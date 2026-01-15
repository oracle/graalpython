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

import importlib
import os
import shlex
import shutil
import subprocess
import sys
import tarfile
import zipfile
from argparse import ArgumentParser
from glob import glob
from os.path import abspath, dirname, exists, expanduser, isdir, join, splitext
from urllib.request import urlretrieve

sys.path.append(os.path.dirname(__file__))
from repair_wheels import repair_wheels


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
            return f.infolist()[0].filename.split('/', 1)[0]
    else:
        with tarfile.open(archive) as f:
            f.extractall()
            return f.getmembers()[0].name.split('/', 1)[0]


def create_venv(basedir):
    if sys.platform == "win32":
        exe = ".exe"
        pip = "graalpy/Scripts/pip.exe"
    else:
        exe = ""
        pip = "graalpy/bin/pip"
    binary = os.path.join(basedir, "bin", f"graalpy{exe}")
    print("Creating venv with", binary, flush=True)
    shutil.rmtree("graalpy", ignore_errors=True)
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


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("graalpy_url")
    parser.add_argument("--ignore-failures", action="store_true", default=False)
    args = parser.parse_args()
    ext = splitext(args.graalpy_url)[1]
    outpath = f"graalpy{ext}"

    download(args.graalpy_url, outpath)
    extracted = extract(outpath)
    pip = create_venv(extracted)
    success = build_wheels(pip)
    repair_wheels("wheelhouse")
    if not success and not args.ignore_failures:
        sys.exit(1)
