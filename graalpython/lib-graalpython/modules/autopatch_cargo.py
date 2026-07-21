# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import argparse
import hashlib
import json
import logging
import os
import re
import shutil
import subprocess
import tarfile
import tempfile
import tomllib
import urllib.parse
import urllib.request
from contextlib import contextmanager
from pathlib import Path


logger = logging.getLogger(__name__)

CRATES_IO_SOURCE = "registry+https://github.com/rust-lang/crates.io-index"
AUTOPATCH_DIR = ".graalpy"
IGNORED_DIRECTORIES = {AUTOPATCH_DIR, ".git", ".hg", ".svn", "target"}
PATCH_TABLE_RE = re.compile(
    r'^\s*\[(?:patch|"patch")\.(?:crates-io|"crates-io")\]\s*(?:#.*)?$',
    re.MULTILINE,
)


def _specifier_contains(specifier, version):
    try:
        from pip._vendor.packaging.specifiers import SpecifierSet
    except ImportError:
        from packaging.specifiers import SpecifierSet
    return SpecifierSet(specifier).contains(version)


class DirectoryPatchRepository:
    def __init__(self, path):
        self.path = Path(path)
        with open(self.path / "metadata.toml", "rb") as metadata_file:
            self.metadata = tomllib.load(metadata_file)

    def get_rules(self, name):
        if metadata := self.metadata.get(name):
            return metadata.get("rules")

    @contextmanager
    def resolve_patch(self, patch_name):
        yield self.path / patch_name


def _matching_rule(repository, name, version):
    for rule in repository.get_rules(name) or ():
        if not rule.get("version") or _specifier_contains(rule["version"], version):
            return rule


def _cargo_home():
    if cargo_home := os.environ.get("CARGO_HOME"):
        return Path(cargo_home)
    return Path.home() / ".cargo"


def _find_cached_crate(name, version):
    registry_sources = _cargo_home() / "registry" / "src"
    if not registry_sources.is_dir():
        return None
    crate_dir_name = f"{name}-{version}"
    for registry in registry_sources.iterdir():
        candidate = registry / crate_dir_name
        if candidate.is_dir():
            return candidate
    return None


def _safe_extract(archive, target):
    with tarfile.open(archive, "r:gz") as tar:
        tar.extractall(target, filter="data")


def _download_crate(name, version, checksum, destination):
    quoted_name = urllib.parse.quote(name, safe="")
    quoted_version = urllib.parse.quote(version, safe="")
    url = f"https://crates.io/api/v1/crates/{quoted_name}/{quoted_version}/download"
    request = urllib.request.Request(url, headers={"User-Agent": "GraalPy autopatch_cargo"})
    with tempfile.TemporaryDirectory(prefix="graalpy-crate-") as tempdir:
        archive = Path(tempdir) / f"{name}-{version}.crate"
        with urllib.request.urlopen(request) as response, open(archive, "wb") as output:
            shutil.copyfileobj(response, output)
        actual_checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
        if actual_checksum != checksum:
            raise RuntimeError(
                f"Checksum mismatch for {name} {version}: Cargo.lock has {checksum}, downloaded {actual_checksum}"
            )
        extracted = Path(tempdir) / "extracted"
        extracted.mkdir()
        _safe_extract(archive, extracted)
        crate_root = extracted / f"{name}-{version}"
        if not (crate_root / "Cargo.toml").is_file():
            raise RuntimeError(f"Downloaded archive for {name} {version} has an unexpected layout")
        shutil.copytree(crate_root, destination)


def _copy_crate(name, version, checksum, destination):
    if cached := _find_cached_crate(name, version):
        shutil.copytree(cached, destination)
    else:
        if not checksum:
            raise RuntimeError(f"Cargo.lock does not contain a checksum for {name} {version}")
        _download_crate(name, version, checksum, destination)


def _apply_patch(crate_dir, patch_path):
    executable = "patch.exe" if os.name == "nt" else "patch"
    subprocess.run(
        [executable, "-f", "-d", str(crate_dir), "-p1", "-i", str(patch_path)],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def _find_lockfiles(location):
    location = Path(location)
    if location.is_file():
        return [location] if location.name == "Cargo.lock" else []
    lockfiles = []
    for root, directories, files in os.walk(location):
        directories[:] = [directory for directory in directories if directory not in IGNORED_DIRECTORIES]
        if "Cargo.lock" in files:
            lockfiles.append(Path(root) / "Cargo.lock")
    return lockfiles


def _load_locked_crates(lockfile, repository):
    with open(lockfile, "rb") as cargo_lock:
        lock_data = tomllib.load(cargo_lock)
    result = []
    for package in lock_data.get("package", ()):
        name = package.get("name")
        version = package.get("version")
        if not name or not version or package.get("source") != CRATES_IO_SOURCE:
            continue
        if rule := _matching_rule(repository, name, version):
            if rule.get("patch"):
                result.append((name, version, package.get("checksum"), rule))
    return result


def _existing_crates_io_patches(manifest_data):
    result = set()
    for key, value in manifest_data.get("patch", {}).get("crates-io", {}).items():
        if isinstance(value, dict):
            result.add(value.get("package", key))
        else:
            result.add(key)
    return result


def _patch_entry(name, version, relative_path):
    alias = re.sub(r"[^A-Za-z0-9_-]", "_", f"graalpy-{name}-{version}")
    return (
        f'{json.dumps(alias)} = {{ package = {json.dumps(name)}, '
        f'path = {json.dumps(relative_path)} }}\n'
    )


def _add_manifest_overrides(manifest, overrides):
    contents = manifest.read_text()
    manifest_data = tomllib.loads(contents)
    existing_patches = _existing_crates_io_patches(manifest_data)
    overrides = [override for override in overrides if override[0] not in existing_patches]
    if not overrides:
        return 0

    entries = "".join(_patch_entry(*override) for override in overrides)
    if match := PATCH_TABLE_RE.search(contents):
        insertion_point = match.end()
        contents = contents[:insertion_point] + "\n" + entries + contents[insertion_point:]
    elif manifest_data.get("patch", {}).get("crates-io"):
        raise RuntimeError("Cargo.toml defines patch.crates-io without a table header that autopatch_cargo can extend")
    else:
        if contents and not contents.endswith("\n"):
            contents += "\n"
        contents += "\n# Added by GraalPy autopatch_cargo\n[patch.crates-io]\n" + entries
    manifest.write_text(contents)
    return len(overrides)


def _use_path_packages_in_lockfile(lockfile, overrides):
    contents = lockfile.read_text()
    targets = {(name, version) for name, version, _ in overrides}
    replaced = set()

    def replace_package(match):
        block = match.group()
        package = tomllib.loads(block)["package"][0]
        target = (package.get("name"), package.get("version"))
        if target not in targets or package.get("source") != CRATES_IO_SOURCE:
            return block
        block = re.sub(r'^source\s*=.*\n', '', block, flags=re.MULTILINE)
        block = re.sub(r'^checksum\s*=.*\n', '', block, flags=re.MULTILINE)
        replaced.add(target)
        return block

    contents = re.sub(
        r'^\[\[package\]\]\n.*?(?=^\[\[package\]\]\n|\Z)',
        replace_package,
        contents,
        flags=re.MULTILINE | re.DOTALL,
    )
    if missing := targets - replaced:
        raise RuntimeError(f"Cannot find patched crates in Cargo.lock: {sorted(missing)}")
    lockfile.write_text(contents)


def _autopatch_workspace(lockfile, repository):
    manifest = lockfile.with_name("Cargo.toml")
    if not manifest.is_file():
        logger.warning("Skipping Cargo.lock without a Cargo.toml in the same directory: %s", lockfile)
        return 0

    try:
        locked_crates = _load_locked_crates(lockfile, repository)
        manifest_data = tomllib.loads(manifest.read_text())
    except (OSError, tomllib.TOMLDecodeError) as error:
        logger.warning("Cannot inspect Cargo workspace at %s: %s", lockfile.parent, error)
        return 0

    existing_patches = _existing_crates_io_patches(manifest_data)
    locked_crates = [crate for crate in locked_crates if crate[0] not in existing_patches]
    overrides = []
    for name, version, checksum, rule in locked_crates:
        crate_dir = lockfile.parent / AUTOPATCH_DIR / "crates" / f"{name}-{version}"
        if crate_dir.exists():
            logger.warning("Refusing to overwrite existing autopatch directory %s", crate_dir)
            continue
        try:
            crate_dir.parent.mkdir(parents=True, exist_ok=True)
            _copy_crate(name, version, checksum, crate_dir)
            with repository.resolve_patch(rule["patch"]) as patch_path:
                if not patch_path:
                    raise RuntimeError(f"Cannot resolve crate patch {rule['patch']}")
                _apply_patch(crate_dir, patch_path)
        except (OSError, RuntimeError, subprocess.SubprocessError) as error:
            shutil.rmtree(crate_dir, ignore_errors=True)
            logger.warning("Could not autopatch crate %s %s: %s", name, version, error)
            continue
        relative_path = Path(os.path.relpath(crate_dir, manifest.parent)).as_posix()
        overrides.append((name, version, relative_path))
        logger.info("Autopatched Rust crate %s %s in %s", name, version, crate_dir)

    original_manifest = manifest.read_text()
    original_lockfile = lockfile.read_text()
    try:
        added = _add_manifest_overrides(manifest, overrides)
        _use_path_packages_in_lockfile(lockfile, overrides)
    except (OSError, RuntimeError, tomllib.TOMLDecodeError) as error:
        manifest.write_text(original_manifest)
        lockfile.write_text(original_lockfile)
        for name, version, _ in overrides:
            shutil.rmtree(lockfile.parent / AUTOPATCH_DIR / "crates" / f"{name}-{version}", ignore_errors=True)
        logger.warning("Could not add Cargo path overrides to %s: %s", manifest, error)
        return 0
    return added


def auto_patch_tree(location, repository):
    """Patch registered crates used by Cargo workspaces under location. Returns the number of overrides added."""
    patched = 0
    for lockfile in _find_lockfiles(location):
        patched += _autopatch_workspace(lockfile, repository)
    return patched


def main(argv=None):
    parser = argparse.ArgumentParser(description="Auto-patch registered Rust crates used under the given directory.")
    parser.add_argument("path", help="source tree containing one or more Cargo.lock files")
    parser.add_argument(
        "--patches-dir",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "patches" / "crates",
        help="directory containing crate metadata.toml and patches",
    )
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO)
    auto_patch_tree(args.path, DirectoryPatchRepository(args.patches_dir))


if __name__ == "__main__":
    main()
