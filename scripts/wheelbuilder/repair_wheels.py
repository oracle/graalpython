# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import os
import platform
import shutil
import subprocess
import sys
from glob import glob
from os.path import abspath, dirname, join


def ensure_installed(name, *extra):
    import importlib
    try:
        return importlib.import_module(name)
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", name, *extra])
        return importlib.import_module(name)


def repair_wheels(wheelhouse):
    whls = glob("*.whl")
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    env["PATH"] = abspath(dirname(sys.executable)) + os.pathsep + env["PATH"]
    os.makedirs(wheelhouse, exist_ok=True)
    for whl in whls:
        if sys.platform == "win32":
            ensure_installed("delvewheel")
            p = subprocess.run(
                [
                    sys.executable,
                    "-m",
                    "delvewheel",
                    "repair",
                    "-v",
                    "--exclude",
                    "python-native.dll",
                    "-w",
                    wheelhouse,
                    whl,
                ],
                env=env,
            )
        elif sys.platform == "linux":
            ensure_installed("auditwheel", "patchelf")
            p = subprocess.run(
                [
                    join(dirname(sys.executable), "auditwheel"),
                    "repair",
                    "-w",
                    wheelhouse,
                    whl,
                ],
                env=env,
            )
        elif sys.platform == "darwin":
            ensure_installed("delocate")
            arch = platform.machine().lower()
            if arch == "arm64":
                require_arch = "arm64"
            elif arch == "x86_64":
                require_arch = "x86_64"
            else:
                raise RuntimeError(f"Unsupported architecture on macOS: {arch}")
            p = subprocess.run(
                [
                    join(dirname(sys.executable), "delocate-wheel"),
                    "-v",
                    "--ignore-missing-dependencies",
                    "--require-archs",
                    require_arch,
                    "-w",
                    wheelhouse,
                    whl,
                ],
                env=env,
            )
        else:
            print(f"Unsupported platform: {sys.platform}", file=sys.stderr)
            continue
        if p.returncode != 0:
            print("Repairing", whl, "failed, copying as is.")
            try:
                shutil.copy(whl, wheelhouse)
            except Exception as e:
                print("Copy failed:", e, file=sys.stderr)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Repair wheels using platform-specific tools.")
    parser.add_argument("--wheelhouse", required=True, help="Output directory for repaired wheels")
    args = parser.parse_args()
    repair_wheels(args.wheelhouse)
