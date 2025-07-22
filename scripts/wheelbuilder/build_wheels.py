# Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

__doc__ = """
A very simple script to build wheels for which we have build scripts.
The steps are:

1. Download GraalPy
2. Make a venv
3. Install some default packages like wheel or auditwheel
4. Go over the build scripts and run them in the venv
"""

import hashlib
import importlib
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
import tarfile
import zipfile

from argparse import ArgumentParser
from glob import glob
from os.path import abspath, basename, dirname, exists, expanduser, isabs, isdir, join, splitext
from tempfile import TemporaryDirectory
from urllib.request import urlretrieve


def ensure_installed(name, *extra):
    try:
        return importlib.import_module(name)
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", name, *extra])
        return importlib.import_module(name)


def download(url, out):
    if not os.path.exists(out):
        print("Downloading", url, flush=True)
        urlretrieve(url, out)
    else:
        print("Using previously downloaded", out, flush=True)


def extract(archive):
    print("Extracting", archive, flush=True)
    if splitext(archive)[1] == ".zip":
        with zipfile.ZipFile(archive) as f:
            f.extractall()
    else:
        with tarfile.open(archive) as f:
            f.extractall()


def create_venv():
    if sys.platform == "win32":
        exe = ".exe"
        pip = "graalpy/Scripts/pip.exe"
    else:
        exe = ""
        pip = "graalpy/bin/pip"
    binary = next(iter(glob(f"graalpy-*/bin/graalpy{exe}")))
    print("Creating venv with", binary, flush=True)
    subprocess.check_call([binary, "-m", "venv", "graalpy"])
    print("Installing wheel with", pip, flush=True)
    subprocess.check_call([pip, "install", "wheel"])
    print("Installing paatch to provide patch.exe", flush=True)
    p = subprocess.run([pip, "install", "paatch"])
    if p.returncode != 0:
        print("Installing paatch failed, assuming a GNU patch compatible binary is on PATH", flush=True)
    return pip


def prepare_environment(pip_exe):
    env = os.environ.copy()
    env["PATH"] = abspath(dirname(pip_exe)) + os.pathsep + env["PATH"]
    env["VIRTUAL_ENV"] = abspath(dirname(dirname(pip_exe)))
    if not shutil.which("cargo"):
        cargo_bin = join(expanduser("~"), ".cargo", "bin")
        if isdir(cargo_bin):
            env["PATH"] += os.pathsep + cargo_bin
    return env


def build_wheels(pip):
    with open(join(dirname(__file__), "packages.txt")) as f:
        packages_from_txt = [tuple(l.strip().split("==")) for l in f]
    packages_to_build = []
    for s in os.environ.get("PACKAGES_TO_BUILD", "").split(","):
        s = s.strip()
        if not s:
            continue
        elif "==" in s:
            name, version = s.split("==")
        else:
            name, version = s, None
            for n, v in packages_from_txt:
                if n == name:
                    version = v
                    break
        if not version:
            print("ERROR: Asked to build", s, "but no version given")
            return False
        packages_to_build.append((name, version))
    if not packages_to_build:
        packages_to_build = packages_from_txt
    print("About to build", packages_to_build)
    scriptdir = abspath(join(dirname(__file__), sys.platform))
    if sys.platform == "win32":
        script_ext = "bat"
    else:
        script_ext = "sh"
    if exists(scriptdir):
        available_scripts = {s.lower(): s for s in os.listdir(scriptdir)}
    else:
        available_scripts = {}
    remaining_packages = 0
    while remaining_packages != len(packages_to_build):
        remaining_packages = len(packages_to_build)
        for name, version in packages_to_build.copy():
            whl_count = len(glob("*.whl"))
            script = f"{name}.{version}.{script_ext}".lower()
            if script not in available_scripts:
                script = f"{name}.{script_ext}".lower()
            if script in available_scripts:
                script = join(scriptdir, available_scripts[script])
                env = prepare_environment(pip)
                print("Building", name, version, "with", script, flush=True)
                if sys.platform == "win32":
                    cmd = [script, version]  # Python's subprocess.py does the quoting we need
                else:
                    cmd = f"{os.environ.get('SHELL', '/bin/sh')} {shlex.quote(script)} {version}"
                p = subprocess.run(cmd, shell=True, env=env)
                if p.returncode != 0:
                    continue
                if len(glob("*.whl")) > whl_count:
                    packages_to_build.remove((name, version))
                    continue
                print(script, "did not build a wheel, we will do so now", flush=True)
            print("Building", name, version, flush=True)
            p = subprocess.run([pip, "wheel", f"{name}=={version}"])
            if p.returncode == 0:
                packages_to_build.remove((name, version))
    if packages_to_build:
        print("Failed to build all packages, the following packages failed")
        print(packages_to_build)
        return False
    else:
        return True


def repair_wheels():
    whls = glob("*.whl")
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"
    env["PATH"] = abspath(dirname(sys.executable)) + os.pathsep + env["PATH"]
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
                    "wheelhouse",
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
                    "--plat",
                    "manylinux_2_28_x86_64" if platform.processor() == "x86_64" else "manylinux_2_28_aarch64",
                    "-w",
                    "wheelhouse",
                    whl,
                ],
                env=env,
            )
        elif sys.platform == "darwin":
            ensure_installed("delocate")
            p = subprocess.run(
                [
                    join(dirname(sys.executable), "delocate-wheel"),
                    "-v",
                    "--ignore-missing-dependencies",
                    "--require-archs",
                    "arm64" if platform.processor() == "arm" else "x86_64",
                    "-w",
                    "wheelhouse",
                    whl,
                ],
                env=env,
            )
        if p.returncode != 0:
            print("Repairing", whl, "failed, copying as is.")
            try:
                shutil.copy(whl, "wheelhouse")
            except:
                pass


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("graalpy_url")
    parser.add_argument("--ignore-failures", action="store_true", default=False)
    args = parser.parse_args()
    ext = splitext(args.graalpy_url)[1]
    outpath = f"graalpy{ext}"

    download(args.graalpy_url, outpath)
    extract(outpath)
    pip = create_venv()
    success = build_wheels(pip)
    repair_wheels()
    if not success and not args.ignore_failures:
        sys.exit(1)
