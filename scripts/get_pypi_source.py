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

#!/usr/bin/python

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
try:
    import tomllib
except ModuleNotFoundError:
    tomllib = None


PYPI_URL = "https://pypi.org/pypi"
METADATA_TOML = Path(__file__).resolve().parents[1] / "graalpython" / "lib-graalpython" / "patches" / "metadata.toml"


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def parse_pkg(pkg_arg):
    if "==" in pkg_arg:
        name, version = pkg_arg.split("==", 1)
    else:
        name, version = pkg_arg, None
    if not name:
        raise ValueError("missing package name")
    return re.sub(r"[-_.]+", "-", name).lower(), version


def pypi_json(name, version=None):
    url = f"{PYPI_URL}/{name}/json"
    with urllib.request.urlopen(url) as resp:
        data = json.load(resp)
    if version is None:
        version = data["info"]["version"]
    if version not in data["releases"]:
        return version, []
    return version, data["releases"][version]


def choose_artifact(files):
    # Match ~/scripts/get-source.py: prefer a universal wheel, then source archives.
    for file_info in files:
        filename = file_info["filename"]
        if filename.endswith(".whl") and "-none-any.whl" in filename:
            return file_info, "wheel"
    for file_info in files:
        filename = file_info["filename"]
        if filename.endswith((".tar.gz", ".zip")):
            return file_info, "sdist"
    return None, None


def find_add_source(name, version):
    if not version or not METADATA_TOML.is_file():
        return None
    if tomllib is None:
        raise RuntimeError("Reading metadata.toml requires Python 3.11 or newer")
    with open(METADATA_TOML, "rb") as metadata_file:
        metadata = tomllib.load(metadata_file)
    for add_source in metadata.get(name, {}).get("add-sources", []):
        if add_source.get("version") == version:
            return add_source["url"]
    return None


def artifact_from_add_source(name, version):
    url = find_add_source(name, version)
    if url is None:
        return None, None
    filename = urllib.parse.urlparse(url).path.rsplit("/", 1)[-1] or f"{name}-{version}.tar.gz"
    return {"filename": filename, "url": url}, "sdist"


def no_source_error(name, version):
    sys.exit(
        f"No suitable source found for {name}=={version}. "
        f"Hint: If upstream doesn't publish sources on PyPI, you can add a [[{name}.add-sources]] entry to {METADATA_TOML} pointing to a release tarball and try again."
    )


def download_file(url, dest):
    with urllib.request.urlopen(url) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)


def safe_extract_zip(zip_path, target_dir):
    target = Path(target_dir).resolve()
    with zipfile.ZipFile(zip_path, "r") as zip_file:
        for member in zip_file.infolist():
            destination = (target / member.filename).resolve()
            if target != destination and target not in destination.parents:
                raise RuntimeError(f"Refusing to extract {member.filename!r} outside {target}")
        zip_file.extractall(target)


def safe_extract_tar(tar_path, target_dir):
    with tarfile.open(tar_path, "r:*") as tar:
        try:
            tar.extractall(path=target_dir, filter="data")
        except TypeError:
            target = Path(target_dir).resolve()
            for member in tar.getmembers():
                destination = (target / member.name).resolve()
                if target != destination and target not in destination.parents:
                    raise RuntimeError(f"Refusing to extract {member.name!r} outside {target}")
            tar.extractall(path=target_dir)


def flatten_single_directory(target_dir):
    entries = os.listdir(target_dir)
    if len(entries) != 1:
        return
    inner_dir = os.path.join(target_dir, entries[0])
    if not os.path.isdir(inner_dir):
        return
    staging_dir = tempfile.mkdtemp(prefix=".get-pypi-source-", dir=target_dir)
    try:
        for entry in os.listdir(inner_dir):
            shutil.move(os.path.join(inner_dir, entry), staging_dir)
        os.rmdir(inner_dir)
        for entry in os.listdir(staging_dir):
            shutil.move(os.path.join(staging_dir, entry), target_dir)
    finally:
        if os.path.isdir(staging_dir):
            os.rmdir(staging_dir)


def unpack(archive_path, artifact_type, target_dir):
    if artifact_type == "wheel":
        safe_extract_zip(archive_path, target_dir)
    elif str(archive_path).endswith(".zip"):
        safe_extract_zip(archive_path, target_dir)
        flatten_single_directory(target_dir)
    else:
        safe_extract_tar(archive_path, target_dir)
        flatten_single_directory(target_dir)


def run(cmd, cwd, **kwargs):
    subprocess.run(cmd, cwd=cwd, check=True, **kwargs)


def init_git_repo(target_dir):
    run(["git", "init"], cwd=target_dir)
    run(["git", "add", "-A", "-f"], cwd=target_dir)
    run(["git", "commit", "--quiet", "-m", "Initial commit"], cwd=target_dir)


def has_git_changes(target_dir):
    result = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=target_dir,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return bool(result.stdout.strip())


def autopatch_capi(target_dir):
    repo_root = Path(__file__).resolve().parents[1]
    autopatch_script = repo_root / "graalpython" / "lib-graalpython" / "modules" / "autopatch_capi.py"
    run([sys.executable, str(autopatch_script), target_dir], cwd=repo_root)
    if has_git_changes(target_dir):
        run(["git", "add", "-A", "-f"], cwd=target_dir)
        run(["git", "commit", "--quiet", "-m", "Autopatched"], cwd=target_dir)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Download and extract a PyPI package artifact into a temporary directory."
    )
    parser.add_argument("package", help="Package specifier, optionally with an exact version: name==version")
    args = parser.parse_args(argv)

    name, requested_version = parse_pkg(args.package)
    try:
        version, files = pypi_json(name, requested_version)
    except urllib.error.HTTPError as exc:
        if requested_version is None or exc.code != 404:
            raise
        version = requested_version
        files = []
    artifact, artifact_type = choose_artifact(files)
    if artifact is None:
        artifact, artifact_type = artifact_from_add_source(name, version)
    if artifact is None:
        raise no_source_error(name, version)

    target_dir = tempfile.mkdtemp(prefix=f"{name}-{version}-")
    try:
        with tempfile.TemporaryDirectory() as download_dir:
            archive_path = os.path.join(download_dir, artifact["filename"])
            eprint(f"Downloading {artifact['filename']}")
            download_file(artifact["url"], archive_path)
            unpack(archive_path, artifact_type, target_dir)
        init_git_repo(target_dir)
    except Exception:
        shutil.rmtree(target_dir, ignore_errors=True)
        raise
    autopatch_capi(target_dir)

    print(f"Prepared source at: {target_dir}")


if __name__ == "__main__":
    main()
