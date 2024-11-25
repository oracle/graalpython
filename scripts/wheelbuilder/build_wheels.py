# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import re
import shlex
import shutil
import subprocess
import sys
import tarfile
import zipfile

from argparse import ArgumentParser
from glob import glob
from os.path import abspath, basename, dirname, exists, isabs, join, splitext
from tempfile import TemporaryDirectory
from urllib.request import urlretrieve


def ensure_installed(name):
    try:
        return importlib.import_module(name)
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", name])
        return importlib.import_module(name)


def download(url, out):
    print("Downloading", url, flush=True)
    urlretrieve(url, out)


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
    return pip


def build_wheels(pip):
    packages_selected = [s for s in os.environ.get("PACKAGES_TO_BUILD", "").split(",") if s]
    packages_to_build = set()
    with open(join(dirname(__file__), "packages.txt")) as f:
        for line in f.readlines():
            line = line.strip()
            name, version = line.split("==")
            if not packages_selected or name in packages_selected or line in packages_selected:
                packages_to_build.add(line)
    scriptdir = abspath(join(dirname(__file__), sys.platform))
    if sys.platform == "win32":
        script_ext = "bat"
    else:
        script_ext = "sh"
    if exists(scriptdir):
        available_scripts = {s.lower(): s for s in os.listdir(scriptdir)}
    else:
        available_scripts = {}
    for spec in packages_to_build:
        name, version = spec.split("==")
        whl_count = len(glob("*.whl"))
        script = f"{name}.{version}.{script_ext}".lower()
        if script not in available_scripts:
            script = f"{name}.{script_ext}".lower()
        if script in available_scripts:
            script = join(scriptdir, available_scripts[script])
            env = os.environ.copy()
            env["PATH"] = abspath(dirname(pip)) + os.pathsep + env["PATH"]
            env["VIRTUAL_ENV"] = abspath(dirname(dirname(pip)))
            print("Building", name, version, "with", script, flush=True)
            if sys.platform == "win32":
                cmd = [script, version] # Python's subprocess.py does the quoting we need
            else:
                cmd = f"{shlex.quote(script)} {version}"
            subprocess.check_call(cmd, shell=True, env=env)
            if not len(glob("*.whl")) > whl_count:
                print("Building wheel for", name, version, "after", script, "did not", flush=True)
                subprocess.check_call([pip, "wheel", spec])
        else:
            print("Building", name, version, flush=True)
            subprocess.check_call([pip, "wheel", spec])


_warned_dlls = []


def repair_wheels_windows(output_dir, wheels):
    import pefile
    from machomachomangler.pe import redll

    def resolve_dll_src(dll):
        # search for dependencies in system directories
        dll_search_paths = [
            os.environ["WINDIR"],
            join(os.environ["WINDIR"], "System32"),
            *os.environ["PATH"].split(";"),
        ]
        ignored_dlls = [
            # These DLLs are just provided by Windows.
            # This list is probably incomplete.
            r"advapi32\.dll",
            r"advapires32\.dll",
            r"atl.*\.dll",
            r"comctl32\.dll",
            r"comdlg32\.dll",
            r"crtdll\.dll",
            r"gdi32\.dll",
            r"hal.*\.dll",
            r"imm32\.dll",
            r"iphlpapi\.dll",
            r"kernel32\.dll",
            r"kernelbase\.dll",
            r"msvbvm60\.dll",
            r"msvcirt\.dll",
            r"msvcrt?.*\.dll",
            r"netapi32\.dll",
            r"ntdll\.dll",
            r"ole32\.dll",
            r"pdh\.dll",
            r"powrprof\.dll",
            r"psapi\.dll",
            r"rpcrt4\.dll",
            r"sechost\.dll",
            r"shell32\.dll",
            r"shlwapi\.dll",
            r"shscrap\.dll",
            r"ucrtbase\.dll",
            r"user32\.dll",
            r"version\.dll",
            r"winmm\.dll",
            r"ws2_32\.dll",
            # These match DLLs that provide API sets.
            # See https://learn.microsoft.com/en-us/windows/win32/apiindex/windows-apisets
            r"api-ms-win-.*\.dll",
            r"ext-ms-win-.*\.dll",
            # These match DLLs that we provide in GraalPy
            r"python.*\.dll",
            # These are the DLLs typically linked when building with MSVC. See
            # https://learn.microsoft.com/en-us/cpp/windows/determining-which-dlls-to-redistribute
            # When these are included, the user should install the latest
            # redist package from
            # https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist
            # However, https://aka.ms/vs/17/redist.txt lists the libraries
            # which can be included in application distributions.
            r"concrt.*\.dll",
            r"mfc.*\.dll",
            r"msvcp.*\.dll",
            r"vcamp.*\.dll",
            r"vccorlib.*\.dll",
            r"vcomp.*\.dll",
            r"vcruntime.*\.dll",
        ]
        if not dll:
            return
        if any(re.match(pat, basename(dll), re.IGNORECASE) for pat in ignored_dlls):
            if dll not in _warned_dlls:
                print("Not including", dll, flush=True)
                _warned_dlls.append(dll)
            return
        if isabs(dll):
            return dll
        for search_path in dll_search_paths:
            if exists(src := join(search_path, dll)):
                return src

    def resolve_dll_target(dll, dependent, checksum):
        return join(dirname(dependent), f"{checksum}.{basename(dll)}")

    def filehash(files):
        sha1 = hashlib.sha1()
        for file in files:
            with open(file, mode="rb") as f:
                sha1.update(f.read())
        return sha1.hexdigest()[:8]

    for whl in wheels:
        with TemporaryDirectory() as name:
            with zipfile.ZipFile(whl) as f:
                f.extractall(name)

            # find all pyd files and recursively copy dependencies
            dlls = glob(f"{name}/**/*.pyd", recursive=True)
            checksum = filehash(dlls)
            dependents_to_dependencies = {}
            while dlls:
                dll = dlls.pop()
                with pefile.PE(dll) as pe:
                    pe_info = pe.dump_dict()
                for syms in pe_info.get("Imported symbols", []):
                    for sym in syms:
                        if dep_src := resolve_dll_src(sym.get("DLL", b"").decode("utf-8")):
                            if not exists(dep_tgt := resolve_dll_target(dep_src, dll, checksum)):
                                print("Including", dep_src, "as", dep_tgt, flush=True)
                                shutil.copy(dep_src, dep_tgt)
                                dlls.append(dep_tgt)
                            dependents_to_dependencies.setdefault(dll, []).append(dep_src)

            for dll, dependencies in dependents_to_dependencies.items():
                mapping = {}
                for dep_src in dependencies:
                    mapping[basename(dep_src).encode("utf-8")] = basename(
                        resolve_dll_target(dep_src, dll, checksum)
                    ).encode("utf-8")
                with open(dll, mode="rb") as f:
                    data = f.read()
                print(
                    "Rewriting\n\t",
                    "\n\t".join([k.decode("utf-8") for k in mapping.keys()]),
                    "\n\t->\n\t",
                    "\n\t".join([v.decode("utf-8") for v in mapping.values()]),
                    "\nin",
                    dll,
                )
                data = redll(data, mapping)
                with open(dll, mode="wb") as f:
                    f.write(data)

            os.makedirs(output_dir, exist_ok=True)
            if exists(whl_tgt := join(output_dir, whl)):
                os.unlink(whl_tgt)
            shutil.make_archive(whl_tgt, "zip", name)
            os.rename(f"{whl_tgt}.zip", whl_tgt)


def repair_wheels():
    if sys.platform == "win32":
        ensure_installed("machomachomangler")
        ensure_installed("pefile")
        repair_wheels_windows("wheelhouse", glob("*.whl"))
    elif sys.platform == "linux":
        ensure_installed("auditwheel")
        subprocess.check_call(
            [join(dirname(sys.executable), "auditwheel"), "repair", "-w", "wheelhouse", *glob("*.whl")]
        )
    elif sys.platform == "darwin":
        ensure_installed("delocate")
        subprocess.check_call(
            [join(dirname(sys.executable), "delocate-wheel"), "-v", "-w", "wheelhouse", *glob("*.whl")]
        )


if __name__ == "__main__":
    parser = ArgumentParser()
    parser.add_argument("graalpy_url")
    args = parser.parse_args()
    ext = splitext(args.graalpy_url)[1]
    outpath = f"graalpy{ext}"

    download(args.graalpy_url, outpath)
    extract(outpath)
    pip = create_venv()
    build_wheels(pip)
    repair_wheels()
