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

import base64
import gzip
import json
import os
import shutil
import sys
import tarfile
import urllib.parse
import urllib.request

import mx
import mx_urlrewrites
import mx_util


SUITE = mx.suite('graalpython')


def run(*args, **kwargs):
    from mx_graalpython import run
    return run(*args, **kwargs)


def _download_graalos_standalone_artifact(source, target):
    source = mx_urlrewrites.rewriteurl(source)
    if "artifact/latest" in source:
        artifact_base_url = os.environ.get("GRAALPY_GRAALOS_ARTIFACT_BASE_URL")
        if not artifact_base_url:
            mx.abort("GRAALPY_GRAALOS_ARTIFACT_BASE_URL must be set to resolve GraalOS artifact metadata")
        with urllib.request.urlopen(urllib.request.Request(source, headers={"Accept": "application/json"})) as response:
            metadata = json.loads(response.read().decode("utf-8"))
        artifact_name = metadata.get("artifactName")
        if not artifact_name:
            mx.abort(f"GraalOS artifact metadata does not contain artifactName: {source}")
        if script := os.environ.get("ARTIFACT_DOWNLOAD_SCRIPT"):
            run([sys.executable, script, artifact_name, target])
            return
        source = urllib.parse.urljoin(artifact_base_url, artifact_name)

    mx.log(f"Downloading {source} to {target}")
    with urllib.request.urlopen(source) as response, open(target, "wb") as fp:
        shutil.copyfileobj(response, fp)


def _extract_tarball(tarball, destination, strip_components=0):
    if os.path.isdir(destination):
        shutil.rmtree(destination)
    mx_util.ensure_dir_exists(destination)
    if strip_components == 0:
        mx.Extractor.create(tarball).extract(destination)
        return
    if strip_components < 0:
        mx.abort(f"strip_components must not be negative: {strip_components}")

    with tarfile.open(tarball) as archive:
        for member in archive:
            original_name = member.name
            stripped_name = "/".join(original_name.split("/")[strip_components:])
            if not stripped_name:
                continue
            if not mx.Extractor._is_sane_name(stripped_name):  # pylint: disable=protected-access
                mx.abort(f"Refusing to extract unsafe archive entry after stripping: {original_name}")
            member.name = stripped_name
            archive.extract(member, destination)
            member.name = original_name
    os.utime(destination, None)


def _find_graalos_runtime_home(runtime_root):
    default = os.path.join(runtime_root, "opt", "graalos")
    if os.path.isdir(default):
        return default
    for root, dirs, _ in os.walk(runtime_root):
        if os.path.basename(root) == "opt" and "graalos" in dirs:
            return os.path.join(root, "graalos")
    mx.abort(f"Could not find opt/graalos in extracted GraalOS runtime artifact: {runtime_root}")


def _ensure_graalos_runtime_inputs(runtime_home):
    graalhost_dir = os.path.join(runtime_home, "graalhost")
    required = [
        os.path.join(graalhost_dir, "graalhost"),
        os.path.join(graalhost_dir, "libc.so"),
    ]
    libbinsweep = os.path.join(graalhost_dir, "libbinsweep.so")
    if not os.path.exists(libbinsweep):
        optional_libbinsweep = os.path.join(graalhost_dir, "optional", "libbinsweep.so.gz")
        if os.path.exists(optional_libbinsweep):
            with gzip.open(optional_libbinsweep, "rb") as src, open(libbinsweep, "wb") as dst:
                shutil.copyfileobj(src, dst)
    required.append(libbinsweep)
    missing = [path for path in required if not os.path.exists(path)]
    if missing:
        mx.abort("Extracted GraalOS runtime artifact is missing required files:\n" + "\n".join(missing))


def graalpy_graalos_standalone_build_and_test(report=None):
    del report  # This gate executes an in-sandbox smoke test directly instead of using the source-tree test runner.
    toolchain_url = os.environ.get("GRAALPY_GRAALOS_TOOLCHAIN_URL")
    runtime_url = os.environ.get("GRAALPY_GRAALOS_RUNTIME_URL")
    if not toolchain_url or not runtime_url:
        mx.log("Skipping GRAALPY_NATIVE_GRAALOS_STANDALONE build: GraalOS artifact URLs are not configured")
        return

    work_dir = os.path.join(SUITE.dir, "mxbuild", "graalos-standalone-ci")
    if os.path.isdir(work_dir):
        shutil.rmtree(work_dir)
    mx_util.ensure_dir_exists(work_dir)

    graalvm_tarball = os.path.join(work_dir, "graalvm.tar.gz")
    runtime_tarball = os.path.join(work_dir, "graalos-runtime.tar.gz")
    graalvm_home = os.path.join(work_dir, "graalvm")
    runtime_root = os.path.join(work_dir, "runtime")

    _download_graalos_standalone_artifact(toolchain_url, graalvm_tarball)
    _extract_tarball(graalvm_tarball, graalvm_home, strip_components=1)
    musl_toolchain = os.path.join(graalvm_home, "lib", "toolchains", "musl-swcfi")
    if not os.path.exists(os.path.join(graalvm_home, "bin", mx.exe_suffix("java"))):
        mx.abort(f"Extracted GraalOS toolchain artifact does not contain bin/java: {graalvm_home}")
    if not os.path.isdir(musl_toolchain):
        mx.abort(f"Extracted GraalOS toolchain artifact does not contain musl-swcfi toolchain: {musl_toolchain}")

    _download_graalos_standalone_artifact(runtime_url, runtime_tarball)
    _extract_tarball(runtime_tarball, runtime_root)
    graalos_runtime_home = _find_graalos_runtime_home(runtime_root)
    _ensure_graalos_runtime_inputs(graalos_runtime_home)

    from mx_graalpython import extend_os_env, run_mx, _graalpy_launcher
    env = extend_os_env(
        JAVA_HOME=graalvm_home,
        MUSL_TOOLCHAIN=musl_toolchain,
        GRAALOS_TOOLCHAIN_PATH=musl_toolchain,
        GRAALOS_RUNTIME_HOME=graalos_runtime_home,
        NATIVE_IMAGE_EXPERIMENTAL_OPTIONS_ARE_FATAL="false",
    )
    run_mx([
        "--multitarget=linux-amd64-musl-swcfi",
        "build",
        "--target", "GRAALPY_NATIVE_GRAALOS_STANDALONE",
    ], env=env)

    standalone_home = os.path.join(SUITE.dir, "mxbuild", "linux-amd64", "GRAALPY_NATIVE_GRAALOS_STANDALONE")
    launcher = os.path.join(standalone_home, "bin", _graalpy_launcher())
    if not os.path.exists(launcher):
        mx.abort(f"GRAALPY_NATIVE_GRAALOS_STANDALONE launcher was not built: {launcher}")

    test_path = os.path.join(
        SUITE.dir,
        "graalpython",
        "com.oracle.graal.python.test",
        "src",
        "tests",
        "test_graalos_standalone.py",
    )
    with open(test_path, "r", encoding="utf-8") as f:
        smoke_test = f.read()
    smoke_test += """
try:
    test_graalos_sqlite3_native_extension_smoke()
except unittest.SkipTest as e:
    print(f"skipped: {e}")
"""
    smoke_test_arg = base64.b64encode(smoke_test.encode("utf-8")).decode("ascii")
    smoke_test_command = f"import base64; exec(base64.b64decode({smoke_test_arg!r}).decode('utf-8'))"
    run([launcher, "-c", smoke_test_command], env=env)
