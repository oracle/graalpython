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

from __future__ import annotations

# pylint: disable=cyclic-import

import gzip
import json
import os
import re
import shutil
import sys
import tarfile
import urllib.parse
import urllib.request
from pathlib import Path

import mx
import mx_urlrewrites


SUITE = mx.suite('graalpython')
GRAALOS_VERSIONS_PATH = Path(__file__).parent / "graalos_versions.json"


def run(*args, **kwargs):
    from mx_graalpython import run
    return run(*args, **kwargs)


def _load_graalos_overlay_urls(ci_overlays_dir: Path):
    overlay_path = ci_overlays_dir / "graalpython.jsonnet"
    if not overlay_path.is_file():
        mx.abort(f"Expected sibling ci-overlays checkout at {overlay_path}")

    content = overlay_path.read_text(encoding="utf-8")
    urls = {}
    for env_name, version_name in (
        ("GRAALPY_GRAALOS_TOOLCHAIN_URL", "toolchain"),
        ("GRAALPY_GRAALOS_RUNTIME_URL", "runtime"),
    ):
        match = re.search(rf'{env_name}:\s*"([^"]+)"', content)
        if not match:
            mx.abort(f"Could not find {env_name} in {overlay_path}")
        urls[version_name] = match.group(1)
    return urls


def update_graalos_versions():
    ci_overlays_dir = Path(SUITE.dir).parent / "ci-overlays"
    overlay_urls = _load_graalos_overlay_urls(ci_overlays_dir)
    versions = {
        name: resolve_latest_graalos_artifact_name(url)
        for name, url in overlay_urls.items()
    }
    content = json.dumps(versions, indent=2, sort_keys=True)
    content += "\n"
    mx.update_file(GRAALOS_VERSIONS_PATH.as_posix(), content, showDiff=True)
    SUITE.vc.git_command(SUITE.dir, ["add", GRAALOS_VERSIONS_PATH.relative_to(SUITE.dir)], abortOnError=True)


def resolve_latest_graalos_artifact_name(source, on_fail=mx.abort):
    source = mx_urlrewrites.rewriteurl(source)
    with urllib.request.urlopen(urllib.request.Request(source, headers={"Accept": "application/json"})) as response:
        metadata = json.loads(response.read().decode("utf-8"))
    artifact_name = metadata.get("artifactName")
    if not artifact_name:
        on_fail(f"GraalOS artifact metadata does not contain artifactName: {source}")
    return artifact_name


def load_graalos_versions(on_fail=mx.abort):
    try:
        with open(GRAALOS_VERSIONS_PATH, "r", encoding="utf-8") as fp:
            versions = json.load(fp)
    except FileNotFoundError:
        on_fail(f"GraalOS versions file not found: {GRAALOS_VERSIONS_PATH}")
    except json.JSONDecodeError as exc:
        on_fail(f"Could not parse GraalOS versions file {GRAALOS_VERSIONS_PATH}: {exc}")

    for key in ("toolchain", "runtime"):
        value = versions.get(key)
        if not isinstance(value, str) or not value:
            on_fail(f"GraalOS versions file must contain a non-empty string for {key!r}: {GRAALOS_VERSIONS_PATH}")
    return versions


def _download_graalos_standalone_artifact(artifact_name, target: Path, on_fail=mx.abort):
    if script := os.environ.get("ARTIFACT_DOWNLOAD_SCRIPT"):
        run([sys.executable, script, artifact_name, str(target)])
        return

    artifact_base_url = os.environ.get("GRAALPY_GRAALOS_ARTIFACT_BASE_URL")
    if not artifact_base_url:
        on_fail("GRAALPY_GRAALOS_ARTIFACT_BASE_URL must be set to download GraalOS artifacts")

    source = urllib.parse.urljoin(mx_urlrewrites.rewriteurl(artifact_base_url), artifact_name)

    mx.log(f"Downloading {source} to {target}")
    with urllib.request.urlopen(source) as response, open(target, "wb") as fp:
        shutil.copyfileobj(response, fp)


def _extract_tarball(tarball: Path, destination: Path, strip_components=0, on_fail=mx.abort):
    if destination.is_dir():
        shutil.rmtree(destination)
    destination.mkdir(parents=True, exist_ok=True)
    if strip_components == 0:
        mx.Extractor.create(str(tarball)).extract(str(destination))
        return
    if strip_components < 0:
        mx.abort(f"strip_components must not be negative: {strip_components}")

    with tarfile.open(str(tarball)) as archive:
        for member in archive:
            original_name = member.name
            stripped_name = "/".join(original_name.split("/")[strip_components:])
            if not stripped_name:
                continue
            if not mx.Extractor._is_sane_name(stripped_name):  # pylint: disable=protected-access
                on_fail(f"Refusing to extract unsafe archive entry after stripping: {original_name}")
            member.name = stripped_name
            archive.extract(member, str(destination))
            member.name = original_name
    os.utime(destination, None)


def _find_graalos_runtime_home(runtime_root: Path, on_fail=mx.abort):
    default = runtime_root / "opt" / "graalos"
    if default.is_dir():
        return default
    for root, dirs, _ in os.walk(runtime_root):
        root_path = Path(root)
        if root_path.name == "opt" and "graalos" in dirs:
            return root_path / "graalos"
    on_fail(f"Could not find opt/graalos in extracted GraalOS runtime artifact: {runtime_root}")


def _ensure_graalos_runtime_inputs(runtime_home: Path, on_fail=mx.abort):
    graalhost_dir = runtime_home / "graalhost"
    required = [
        graalhost_dir / "graalhost",
        graalhost_dir / "libc.so",
    ]
    libbinsweep = graalhost_dir / "libbinsweep.so"
    if not libbinsweep.exists():
        optional_libbinsweep = graalhost_dir / "optional" / "libbinsweep.so.gz"
        if optional_libbinsweep.exists():
            with gzip.open(optional_libbinsweep, "rb") as src, open(libbinsweep, "wb") as dst:
                shutil.copyfileobj(src, dst)
    required.append(libbinsweep)
    missing = [path for path in required if not path.exists()]
    if missing:
        on_fail("Extracted GraalOS runtime artifact is missing required files:\n" + "\n".join([str(p) for p in missing]))


def _prepare_graalos_demo(standalone_home: Path, env):
    demo_wheels = standalone_home / "demo-wheels"
    site_packages = standalone_home / "lib" / "python3.12" / "site-packages"
    demo_wheels.mkdir(parents=True, exist_ok=True)
    site_packages.mkdir(parents=True, exist_ok=True)

    run([
        sys.executable, "-m", "pip", "download",
        "--only-binary=:all:",
        "--implementation", "py",
        "--python-version", "3.12",
        "--abi", "none",
        "--platform", "any",
        "--dest", str(demo_wheels),
        "rich",
        "asteval",
    ], env=env)
    # Work around GRAALOS-8260 by installing pure-Python demo wheels from the
    # host. Remove this once in-sandbox ensurepip/pip subprocesses work there.
    run([
        sys.executable, "-m", "pip", "install",
        "--target", str(site_packages),
        "--no-index",
        "--find-links", str(demo_wheels),
        "--ignore-requires-python",
        "--no-compile",
        "--upgrade",
        "rich",
        "asteval",
    ], env=env)

    from mx_graalpython import _python_unittest_root
    graalos_tests = Path(_python_unittest_root()) / "graalos"
    shutil.copy2(graalos_tests / "test_graalos_sandbox_chat.py", standalone_home / "test_graalos_sandbox_chat.py")
    shutil.copy2(graalos_tests / "GRAALOS_DEMO.md", standalone_home / "GRAALOS_DEMO.md")


def _stage_graalos_test_harness(standalone_home: Path):
    from mx_graalpython import _python_test_runner, _python_unittest_root
    graalos_tests = Path(_python_unittest_root()) / "graalos"
    harness_dir = standalone_home / "test-harness"
    tests_dir = harness_dir / "tests"
    tests_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(
        _python_test_runner(),
        harness_dir / "runner.py",
    )
    shutil.copy2(
        graalos_tests / "test_graalos_standalone.py",
        tests_dir / "test_graalos_standalone.py",
    )
    shutil.copy2(
        graalos_tests / "test_graalos_sandbox_chat.py",
        tests_dir / "test_graalos_sandbox_chat.py",
    )


def _set_graalos_standalone_env(standalone_home: Path, key, value, on_fail=mx.abort):
    config_path = standalone_home / "config.json"
    original_config = config_path.read_text(encoding="utf-8")
    _ = json.dumps({key: value})  # Validate that both values can be represented in JSON.
    if re.search(rf'^\s*"{re.escape(key)}"\s*:', original_config, flags=re.MULTILINE):
        return config_path, original_config
    env_match = re.search(r'("env"\s*:\s*\{\n)(.*?)(\n\s*\})', original_config, flags=re.DOTALL)
    if not env_match:
        on_fail(f"Could not find env object in GraalOS standalone config: {config_path}")
    env_body = env_match.group(2)
    indent_match = re.search(r'^(\s*)"', env_body, flags=re.MULTILINE)
    indent = indent_match.group(1) if indent_match else "    "
    separator = "," if env_body.strip() else ""
    entry = f'{separator}\n{indent}{json.dumps(key)}: {json.dumps(value)}'
    config = original_config[:env_match.end(2)] + entry + original_config[env_match.end(2):]
    config_path.write_text(config, encoding="utf-8")
    return config_path, original_config


def _upload_graalos_standalone_artifact(standalone_home: Path, work_dir: Path):
    script = os.environ.get("ARTIFACT_UPLOADER_SCRIPT")
    if not script:
        mx.log("Skipping GRAALPY_NATIVE_GRAALOS_STANDALONE artifact upload: ARTIFACT_UPLOADER_SCRIPT is not set")
        return

    revision = str(SUITE.vc.tip(SUITE.dir)).strip()
    short_revision = revision[:10]
    archive_base = work_dir / f"graalpy-native-graalos-standalone-linux-amd64-dev-g{short_revision}"
    archive_path = shutil.make_archive(
        str(archive_base),
        "gztar",
        root_dir=str(standalone_home.parent),
        base_dir=standalone_home.name,
    )
    artifact_name = Path(archive_path).name
    upload_cmd = [
        sys.executable,
        script,
        archive_path,
        f"graalpy/{artifact_name}",
        "graalpy",
        "--artifact-type", "graalpy-native-graalos-standalone",
        "--version", f"dev-g{short_revision}",
        "--revision", revision,
        "--edition", "ee",
        "--lifecycle", "snapshot",
        "--platform", "linux-amd64",
    ]
    if repo_key := os.environ.get("ARTIFACT_REPO_KEY_LOCATION"):
        upload_cmd += ["--artifact-repo-key", repo_key]
    run(upload_cmd)


def graalpy_graalos_standalone_build_and_test(report=None, on_fail=mx.abort):
    artifact_base_url = os.environ.get("GRAALPY_GRAALOS_ARTIFACT_BASE_URL")
    if not artifact_base_url:
        mx.log("Skipping GRAALPY_NATIVE_GRAALOS_STANDALONE build: GRAALPY_GRAALOS_ARTIFACT_BASE_URL is not configured")
        return
    versions = load_graalos_versions(on_fail=on_fail)

    work_dir = Path(SUITE.dir) / "mxbuild" / "graalos-standalone-ci"
    if work_dir.is_dir():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True, exist_ok=True)

    graalvm_tarball = work_dir / "graalvm.tar.gz"
    runtime_tarball = work_dir / "graalos-runtime.tar.gz"
    graalvm_home = work_dir / "graalvm"
    runtime_root = work_dir / "runtime"

    _download_graalos_standalone_artifact(versions["toolchain"], graalvm_tarball, on_fail=on_fail)
    _extract_tarball(graalvm_tarball, graalvm_home, strip_components=1, on_fail=on_fail)
    musl_toolchain = graalvm_home / "lib" / "toolchains" / "musl-swcfi"
    if not (graalvm_home / "bin" / mx.exe_suffix("java")).is_file():
        on_fail(f"Extracted GraalOS toolchain artifact does not contain bin/java: {graalvm_home}")
    if not musl_toolchain.is_dir():
        on_fail(f"Extracted GraalOS toolchain artifact does not contain musl-swcfi toolchain: {musl_toolchain}")

    _download_graalos_standalone_artifact(versions["runtime"], runtime_tarball, on_fail=on_fail)
    _extract_tarball(runtime_tarball, runtime_root, on_fail=on_fail)
    graalos_runtime_home = _find_graalos_runtime_home(runtime_root, on_fail=on_fail)
    _ensure_graalos_runtime_inputs(graalos_runtime_home, on_fail=on_fail)

    from mx_graalpython import extend_os_env, run_mx, run_python_unittests, _graalpy_launcher
    env = extend_os_env(
        JAVA_HOME=str(graalvm_home),
        MUSL_TOOLCHAIN=str(musl_toolchain),
        GRAALOS_TOOLCHAIN_PATH=str(musl_toolchain),
        GRAALOS_RUNTIME_HOME=str(graalos_runtime_home),
        NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL="false",
    )
    result = run_mx([
        "--multitarget=linux-amd64-musl-swcfi",
        "build",
        "--target", "GRAALPY_NATIVE_GRAALOS_STANDALONE",
    ], env=env, nonZeroIsFatal=(on_fail == mx.abort))  # pylint: disable=comparison-with-callable
    if result != 0:
        on_fail("Building GRAALPY_NATIVE_GRAALOS_STANDALONE failed")

    standalone_home = Path(SUITE.dir) / "mxbuild" / "linux-amd64" / "GRAALPY_NATIVE_GRAALOS_STANDALONE"
    launcher = standalone_home / "bin" / _graalpy_launcher()
    if not launcher.exists():
        on_fail(f"GRAALPY_NATIVE_GRAALOS_STANDALONE launcher was not built: {launcher}")

    _prepare_graalos_demo(standalone_home, env)
    _stage_graalos_test_harness(standalone_home)
    config_path, original_config = _set_graalos_standalone_env(
        standalone_home,
        "GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS",
        "true",
        on_fail=on_fail,
    )
    try:
        run_python_unittests(
            str(launcher),
            paths=[
                "/test-harness/tests/test_graalos_standalone.py",
                "/test-harness/tests/test_graalos_sandbox_chat.py",
            ],
            env=env,
            report=report,
            parallel=0,
            test_runner="/test-harness/runner.py",
        )
    finally:
        config_path.write_text(original_config, encoding="utf-8")
    _upload_graalos_standalone_artifact(standalone_home, work_dir)
