# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import sys
import os
import shutil
import sysconfig

from importlib import invalidate_caches
from pathlib import Path


compiled_registry = set()


def find_rootdir():
    cur_dir = Path(__file__).parent
    while cur_dir.name != 'graalpython':
        cur_dir = cur_dir.parent
    rootdir = cur_dir.parent / "mxbuild" / "cpyexts"
    rootdir.mkdir(parents=True, exist_ok=True)
    return rootdir


DIR = find_rootdir()


def get_setuptools(setuptools='setuptools==67.6.1'):
    """
    distutils is not part of std library since python 3.12
    we rely on distutils to pick the toolchain for the underlying system
    and build the c extension tests.
    """
    import site
    setuptools_path = find_rootdir() / ('%s-setuptools-venv' % sys.implementation.name)

    if not os.path.isdir(setuptools_path / 'setuptools'):
        import subprocess
        print('installing setuptools in %s' % setuptools_path)
        system_python = install_venv(setuptools_path)
        if sys.platform.startswith('win32'):
            py_executable = setuptools_path / 'Scripts' / 'python.exe'
        else:
            py_executable = setuptools_path / 'bin' / 'python3'
        subprocess.run([py_executable, "-m", "pip", "install", "--target", str(setuptools_path), setuptools], check=True)
        print('setuptools is installed in %s' % setuptools_path)

    pyvenv_site = str(setuptools_path)
    if pyvenv_site not in site.getsitepackages():
        site.addsitedir(pyvenv_site)


def install_venv(venv_path: Path) -> bool:
    """Installs a virtual environment at the given path."""
    if not sys.executable:
        # When running in a PolyBench benchmark context sys.executable is unset
        # And thus we must defer to the system's python
        # Deferring to the system's python is fine as it will only be used to install setuptools
        import subprocess
        subprocess.run(["python", "-m", "venv", str(venv_path)], check=True)
        return True
    else:
        import venv
        venv.create(venv_path, with_pip=True)
        return False


def compile_module_from_string(c_source: str, name: str):
    source_file = DIR / f'{name}.c'
    with open(source_file, "wb", buffering=0) as f:
        f.write(bytes(c_source, 'utf-8'))
    return compile_module_from_file(name)


def compile_module_from_file(module_name: str, sibling_to=None):
    if sibling_to:
        compile_name = str(Path(sibling_to).absolute().parent / module_name)
    else:
        compile_name = module_name
    install_dir = ccompile(None, compile_name)
    sys.path.insert(0, install_dir)
    try:
        cmodule = __import__(module_name)
    finally:
        sys.path.pop(0)
    return cmodule


def ccompile(self, name, check_duplicate_name=True):
    get_setuptools()
    from setuptools import setup, Extension
    from hashlib import sha256
    EXT_SUFFIX = sysconfig.get_config_var("EXT_SUFFIX")

    source_file = DIR / f'{name}.c'
    filename = Path(name).name
    file_not_empty(source_file)

    # compute checksum of source file
    m = sha256()
    with open(source_file,"rb") as f:
        # read 4K blocks
        for block in iter(lambda: f.read(4096),b""):
            m.update(block)
    cur_checksum = m.hexdigest()

    build_dir = DIR / 'build' / filename

    # see if there is already a checksum file
    checksum_file = build_dir / f'{filename}{EXT_SUFFIX}.sha256'
    available_checksum = ""
    if checksum_file.exists():
        # read checksum file
        with open(checksum_file, "r") as f:
            available_checksum = f.readline()

    # note, the suffix is already a string like '.so'
    lib_file = build_dir / f'{filename}{EXT_SUFFIX}'

    if check_duplicate_name and available_checksum != cur_checksum and name in compiled_registry:
        raise RuntimeError(f"\n\nModule with name '{name}' was already compiled, but with different source code. "
              "Have you accidentally used the same name for two different CPyExtType, CPyExtHeapType, "
              "or similar helper calls? Modules with same name can sometimes confuse the import machinery "
              "and cause all sorts of trouble.\n")

    compiled_registry.add(name)

    # Compare checksums and only re-compile if different.
    # Note: It could be that the C source file's checksum didn't change but someone
    # manually deleted the shared library file.
    if available_checksum != cur_checksum or not lib_file.exists():
        shutil.rmtree(build_dir, ignore_errors=True)
        os.makedirs(build_dir)
        # MSVC linker doesn't like absolute paths in some parameters, so just run from the build dir
        old_cwd = os.getcwd()
        os.chdir(build_dir)
        try:
            shutil.copy(source_file, '.')
            module = Extension(filename, sources=[source_file.name])
            args = [
                '--verbose' if sys.flags.verbose else '--quiet',
                'build', '--build-temp=t', '--build-base=b', '--build-purelib=l', '--build-platlib=l',
                'install_lib', '-f', '--install-dir=.',
            ]
            setup(
                script_name='setup',
                script_args=args,
                name=filename,
                version='1.0',
                description='',
                ext_modules=[module]
            )
        finally:
            os.chdir(old_cwd)

        # write new checksum
        with open(checksum_file, "w") as f:
            f.write(cur_checksum)

        # IMPORTANT:
        # Invalidate caches after creating the native module.
        # FileFinder caches directory contents, and the check for directory
        # changes has whole-second precision, so it can miss quick updates.
        invalidate_caches()

    # ensure file was really written
    file_not_empty(lib_file)

    return str(build_dir)


def file_not_empty(path):
    for i in range(3):
        try:
            stat_result = os.stat(path)
            if stat_result[6] != 0:
                return
        except FileNotFoundError:
            pass
    raise SystemError("file %s not available" % path)
