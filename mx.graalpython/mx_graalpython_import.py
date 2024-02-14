# Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import abc
import argparse
import os
import shutil
import sys
from textwrap import dedent

import mx

SUITE = mx.suite('graalpython')


class AbstractRule(abc.ABC):
    def __init__(self, source_path):
        self.source_path = source_path

    @abc.abstractmethod
    def copy(self, source_dir, graalpy_dir, graalpy_path, overrides):
        pass


def match_overrides(graalpy_path, overrides):
    graalpy_prefix = f"{graalpy_path}/"
    matches = [f for f in overrides if f == graalpy_path or f.startswith(graalpy_prefix)]
    for match in matches:
        overrides.remove(match)
    return matches


class Ignore(AbstractRule):
    """
    A rule that ignores a file a directory.
    """

    def __init__(self):
        super().__init__(None)

    def copy(self, source_dir, graalpy_dir, graalpy_path, overrides):
        match_overrides(graalpy_path, overrides)


class CopyFrom(AbstractRule):
    """
    A rule that copies a file or a whole directory recursively.
    """

    def copy(self, source_dir, graalpy_dir, graalpy_path, overrides):
        match_overrides(graalpy_path, overrides)
        src = os.path.join(source_dir, self.source_path)
        dst = os.path.join(graalpy_dir, graalpy_path)
        if os.path.isfile(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy(src, dst)
        elif os.path.isdir(src):
            shutil.copytree(src, dst, dirs_exist_ok=True)
        else:
            sys.exit(f"Source path {src} not found")


class CopyFromWithOverrides(AbstractRule):
    """
    A rule that copies a directory using a list of files specified in the license header overrides list.
    """

    def copy(self, source_dir, graalpy_dir, graalpy_path, overrides):
        src = os.path.join(source_dir, self.source_path)
        if os.path.isdir(src):
            graalpy_prefix = f"{graalpy_path}/"
            matches = match_overrides(graalpy_path, overrides)
            for f in matches:
                dst = os.path.join(graalpy_dir, f)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                src = os.path.join(source_dir, self.source_path)
                if f.startswith(graalpy_prefix):
                    src = os.path.join(src, f.removeprefix(graalpy_prefix))
                if not os.path.exists(src):
                    sys.exit(f"File {f} from license overrides not found in {self.source_path}")
                shutil.copy(src, dst)
        elif os.path.exists(src):
            sys.exit(f"{type(self)} rule should only be used with directories")
        else:
            sys.exit(f"Source path {src} not found")


#############################
# Source file mapping rules #
#############################
CPYTHON_SOURCES_MAPPING = {
    # Standard library
    "graalpython/lib-python/3": CopyFrom("Lib"),

    # C API
    "graalpython/com.oracle.graal.python.cext/include": CopyFromWithOverrides("Include"),
    # Different copyright
    "graalpython/com.oracle.graal.python.cext/include/dynamic_annotations.h": CopyFrom("Include/dynamic_annotations.h"),
    "graalpython/com.oracle.graal.python.cext/expat": CopyFromWithOverrides("Modules/expat"),
    "graalpython/com.oracle.graal.python.cext/modules/_sqlite": CopyFrom("Modules/_sqlite"),
    "graalpython/com.oracle.graal.python.cext/modules/_sha3": CopyFrom("Modules/_sha3"),
    "graalpython/com.oracle.graal.python.cext/modules/_cpython_sre": CopyFromWithOverrides("Modules/_sre"),
    "graalpython/com.oracle.graal.python.cext/modules/_cpython_unicodedata.c": CopyFrom("Modules/unicodedata.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_bz2.c": CopyFrom("Modules/_bz2module.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_mmap.c": CopyFrom("Modules/mmapmodule.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_cpython_struct.c": CopyFrom("Modules/_struct.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_testcapi.c": CopyFrom("Modules/_testcapimodule.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_ctypes_test.h": CopyFrom("Modules/_ctypes/_ctypes_test.h"),
    "graalpython/com.oracle.graal.python.cext/modules/_ctypes_test.c": CopyFrom("Modules/_ctypes/_ctypes_test.c"),
    "graalpython/com.oracle.graal.python.cext/modules/clinic/memoryobject.c.h": CopyFrom(
        "Objects/clinic/memoryobject.c.h"),
    "graalpython/com.oracle.graal.python.cext/modules": CopyFromWithOverrides("Modules"),

    "graalpython/com.oracle.graal.python.cext/src/getbuildinfo.c": CopyFrom("Modules/getbuildinfo.c"),
    "graalpython/com.oracle.graal.python.cext/src/getcompiler.c": CopyFrom("Python/getcompiler.c"),
    "graalpython/com.oracle.graal.python.cext/src/getversion.c": CopyFrom("Python/getversion.c"),
    "graalpython/com.oracle.graal.python.cext/src/mysnprintf.c": CopyFrom("Python/mysnprintf.c"),
    "graalpython/com.oracle.graal.python.cext/src/mystrtoul.c": CopyFrom("Python/mystrtoul.c"),
    "graalpython/com.oracle.graal.python.cext/src/pystrcmp.c": CopyFrom("Python/pystrcmp.c"),
    "graalpython/com.oracle.graal.python.cext/src/pystrtod.c": CopyFrom("Python/pystrtod.c"),
    "graalpython/com.oracle.graal.python.cext/src/pystrhex.c": CopyFrom("Python/pystrhex.c"),
    "graalpython/com.oracle.graal.python.cext/src/typeslots.inc": CopyFrom("Objects/typeslots.inc"),
    # These files take functions from CPython, but they don't follow the same structure, so they are impossible
    # to meaningfully merge
    "graalpython/com.oracle.graal.python.cext/src/tupleobject.c": Ignore(),
    "graalpython/com.oracle.graal.python.cext/src/typeobject.c": Ignore(),
    "graalpython/com.oracle.graal.python.cext/src/thread.c": Ignore(),

    "graalpython/com.oracle.graal.python.cext/src": CopyFromWithOverrides("Objects"),

    # Just few functions are taken from CPython
    "graalpython/python-libposix/src/fork_exec.c": Ignore(),

    # Largely rewritten
    "graalpython/python-venvlauncher/src/venvlauncher.c": Ignore(),

    # PEG Parser
    "graalpython/com.oracle.graal.python.pegparser.generator/pegen": CopyFrom("Tools/peg_generator/pegen"),
    "graalpython/com.oracle.graal.python.pegparser.generator/asdl/asdl.py": CopyFrom("Parser/asdl.py"),
    "graalpython/com.oracle.graal.python.pegparser.generator/input_files/python.gram": CopyFrom("Grammar/python.gram"),
    "graalpython/com.oracle.graal.python.pegparser.generator/input_files/Tokens": CopyFrom("Grammar/Tokens"),
    "graalpython/com.oracle.graal.python.pegparser.generator/diff_generator.py": Ignore(),
    "graalpython/com.oracle.graal.python.pegparser.generator/pegjava/java_generator.py": Ignore(),

    "graalpython/com.oracle.graal.python.frozen/freeze_modules.py": CopyFrom("Tools/scripts/freeze_modules.py"),

    # Others
    # Test files don't need to be updated, they inline some unittest code only
    "graalpython/com.oracle.graal.python.test/src/tests": Ignore(),
}

PYPY_SOURCES_MAPPING = {
    "graalpython/lib-python/3/_md5.py": CopyFrom("lib_pypy/_md5.py"),
    "graalpython/lib-python/3/_sha1.py": CopyFrom("lib_pypy/_sha1.py"),
    "graalpython/lib-python/3/_sha256.py": CopyFrom("lib_pypy/_sha256.py"),
    "graalpython/lib-python/3/_sha512.py": CopyFrom("lib_pypy/_sha512.py"),
    "graalpython/com.oracle.graal.python.benchmarks": Ignore(),
}


def import_python_sources(args):
    parser = argparse.ArgumentParser(
        prog='mx python-src-import',
        description="Update the inlined files from PyPy and CPython. Refer to the docs on Confluence.",
    )
    parser.add_argument('--cpython', action='store', help='Path to CPython sources', required=True)
    parser.add_argument('--pypy', action='store', help='Path to PyPy sources', required=True)
    parser.add_argument('--python-version', action='store',
                        help='Python version to be updated to (used for commit message)', required=True)
    args = parser.parse_args(args)

    with open(os.path.join(os.path.dirname(__file__), "copyrights", "overrides")) as f:
        entries = [line.strip().split(',') for line in f if ',' in line]
        cpython_files = [file for file, license in entries if
                         license == "python.copyright" and not file.endswith('.java')]
        pypy_files = [file for file, license in entries if license == "pypy.copyright"]

    import_dir = os.path.abspath(os.path.join(SUITE.dir, 'python-import'))
    shutil.rmtree(import_dir, ignore_errors=True)
    # Fetch the python-import branch, would fail when not fast-forwardable
    SUITE.vc.git_command(SUITE.dir, ['fetch', 'origin', 'python-import:python-import'])
    # Checkout the python-import branch into a subdirectory
    SUITE.vc.git_command(SUITE.dir, ['clone', '-b', 'python-import', '.', 'python-import'])
    for file in os.listdir(import_dir):
        if not file.startswith('.'):
            shutil.rmtree(os.path.join(import_dir, file), ignore_errors=True)

    def copy_inlined_files(mapping, source_directory, overrides):
        overrides = list(overrides)
        for path, rule in mapping.items():
            rule.copy(source_directory, import_dir, path, overrides)
        if overrides:
            lines = '\n'.join(overrides)
            sys.exit(f"ERROR: The following files were not matched by any rule:\n{lines}")

    copy_inlined_files(CPYTHON_SOURCES_MAPPING, args.cpython, cpython_files)
    copy_inlined_files(PYPY_SOURCES_MAPPING, args.pypy, pypy_files)

    # Commit and fetch changes back into the main repository
    SUITE.vc.git_command(import_dir, ["add", "."])
    SUITE.vc.commit(import_dir, f"Update Python inlined files: {args.python_version}")
    SUITE.vc.git_command(SUITE.dir, ['fetch', 'python-import', '+python-import:python-import'])
    print(dedent("""\

    The python-import branch has been updated to the specified release.
    It is checked out in the python-import directory, where you can make changes and amend the commit. If you do, you
    need to pull the changes back to this repository using the following (from the main repository directory):
        git fetch python-import +python-import:python-import
    When you're happy with the state, push the changes using:
        git push origin python-import:python-import
    """))
