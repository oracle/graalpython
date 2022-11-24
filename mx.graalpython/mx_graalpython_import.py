import os
import shutil
import sys
import argparse
from textwrap import dedent

import mx

SUITE = mx.suite('graalpython')


class CopyFrom:
    def __init__(self, source_path, copy_all=False):
        self.source_path = source_path
        self.copy_all = copy_all

    def copy(self, basedir, graalpy_path, overrides):
        src = os.path.join(basedir, self.source_path)
        if os.path.isfile(src):
            os.makedirs(os.path.dirname(graalpy_path), exist_ok=True)
            shutil.copy(src, graalpy_path)
        elif os.path.isdir(src):
            if self.copy_all:
                shutil.copytree(src, graalpy_path, dirs_exist_ok=True)
            else:
                graalpy_prefix = f"{graalpy_path}/"
                matches = [f for f in overrides if f == graalpy_path or f.startswith(graalpy_prefix)]
                for f in matches:
                    os.makedirs(os.path.dirname(f), exist_ok=True)
                    src = os.path.join(basedir, self.source_path)
                    if f.startswith(graalpy_prefix):
                        src = os.path.join(src, f.removeprefix(graalpy_prefix))
                    shutil.copy(src, f)
        else:
            sys.exit(f"Source path {src} not found")


class Ignore:
    def copy(self, basedir, graalpy_path, overrides):
        pass


#############################
# Source file mapping rules #
#############################
CPYTHON_SOURCES_MAPPING = {
    # Standard library
    "graalpython/lib-python/3": CopyFrom("Lib", copy_all=True),

    # C API
    "graalpython/com.oracle.graal.python.cext/include": CopyFrom("Include"),
    "graalpython/com.oracle.graal.python.cext/expat": CopyFrom("Modules/expat"),
    "graalpython/com.oracle.graal.python.cext/modules/_cpython_sre.c": CopyFrom("Modules/_sre.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_cpython_unicodedata.c": CopyFrom("Modules/unicodedata.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_bz2.c": CopyFrom("Modules/_bz2module.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_mmap.c": CopyFrom("Modules/mmapmodule.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_cpython_struct.c": CopyFrom("Modules/_struct.c"),
    "graalpython/com.oracle.graal.python.cext/modules/_testcapi.c": CopyFrom("Modules/_testcapimodule.c"),
    "graalpython/com.oracle.graal.python.cext/modules": CopyFrom("Modules"),
    "graalpython/com.oracle.graal.python.cext/src/getbuildinfo.c": CopyFrom("Modules/getbuildinfo.c"),
    "graalpython/com.oracle.graal.python.cext/src/capsule.c": CopyFrom("Objects/capsule.c"),
    "graalpython/com.oracle.graal.python.cext/src/complexobject.c": CopyFrom("Objects/complexobject.c"),
    "graalpython/com.oracle.graal.python.cext/src/floatobject.c": CopyFrom("Objects/floatobject.c"),
    "graalpython/com.oracle.graal.python.cext/src/sliceobject.c": CopyFrom("Objects/sliceobject.c"),
    "graalpython/com.oracle.graal.python.cext/src/unicodectype.c": CopyFrom("Objects/unicodectype.c"),
    "graalpython/com.oracle.graal.python.cext/src/unicodeobject.c": CopyFrom("Objects/unicodeobject.c"),
    "graalpython/com.oracle.graal.python.cext/src/unicodetype_db.h": CopyFrom("Objects/unicodetype_db.h"),
    "graalpython/com.oracle.graal.python.cext/src/typeslots.inc": CopyFrom("Objects/typeslots.inc"),
    "graalpython/com.oracle.graal.python.cext/src/getcompiler.c": CopyFrom("Python/getcompiler.c"),
    "graalpython/com.oracle.graal.python.cext/src/getversion.c": CopyFrom("Python/getversion.c"),
    "graalpython/com.oracle.graal.python.cext/src/mysnprintf.c": CopyFrom("Python/mysnprintf.c"),
    "graalpython/com.oracle.graal.python.cext/src/mystrtoul.c": CopyFrom("Python/mystrtoul.c"),
    "graalpython/com.oracle.graal.python.cext/src/pystrhex.c": CopyFrom("Python/pystrhex.c"),
    # Just few functions are taken from CPython
    "graalpython/com.oracle.graal.python.cext/posix/fork_exec.c": Ignore(),

    # PEG Parser
    "graalpython/com.oracle.graal.python.pegparser.generator/pegen": CopyFrom("Tools/peg_generator/pegen",
                                                                              copy_all=True),
    "graalpython/com.oracle.graal.python.pegparser.generator/asdl/asdl.py": CopyFrom("Parser/asdl.py"),
    "graalpython/com.oracle.graal.python.pegparser.generator/input_files/python.gram": CopyFrom("Grammar/python.gram"),
    "graalpython/com.oracle.graal.python.pegparser.generator/input_files/Tokens": CopyFrom("Grammar/Tokens"),
    "graalpython/com.oracle.graal.python.pegparser.generator/diff_generator.py": Ignore(),
    "graalpython/com.oracle.graal.python.pegparser.generator/pegjava/java_generator.py": Ignore(),

    # Others
    # Test files don't need to be updated, they inline some unittest code only
    "graalpython/com.oracle.graal.python.test/src/tests": Ignore(),
    # The following files are not copies, they just contain parts
    "graalpython/lib-graalpython/zipimport.py": Ignore(),
    "graalpython/com.oracle.graal.python.frozen/freeze_modules.py": Ignore(),
}

PYPY_SOURCES_MAPPING = {
    "graalpython/lib-python/3": CopyFrom("lib_pypy"),
}


def import_python_sources(args):
    """Update the inlined files from PyPy and CPython"""
    parser = argparse.ArgumentParser(prog='mx python-src-import')
    parser.add_argument('--cpython', action='store', help='Path to CPython sources', required=True)
    parser.add_argument('--pypy', action='store', help='Path to PyPy sources', required=True)
    parser.add_argument('--python-version', action='store',
                        help='Python version to be updated to (used for commit message)', required=True)
    args = parser.parse_args(args)

    # TODO
    """
    So you think you want to update the inlined sources? Here is how it will go:

    1. We'll first check the copyrights check overrides file to identify the
       files taken from CPython or PyPy and we'll remember that list. There's a mapping
       for files that were renamed, currently this includes:
       \t{0!r}\n

    2. We'll check out the "python-import" branch. This branch has only files
       that were inlined from CPython or PyPy. We'll use the sources given on
       the commandline for that. I hope those are in a state where that makes
       sense.

    3. We'll stop and wait to give you some time to check if the python-import
       branch looks as you expect. Then we'll commit the updated files to the
       python-import branch, push it, and move back to whatever your HEAD is
       now.

    4. We'll merge the python-import branch back into HEAD. Because these share
       a common ancestor, git will try to preserve our patches to files, that
       is, copyright headers and any other source patches.

    5. !IMPORTANT! If files were inlined from CPython during normal development
       that were not first added to the python-import branch, you will get merge
       conflicts and git will tell you that the files was added on both
       branches. You probably should resolve these using:

           git checkout python-import -- path/to/file

        Then check the diff and make sure that any patches that we did to those
        files are re-applied.

    6. After the merge is completed and any direct merge conflicts are resolved,
       run this:

           mx python-checkcopyrights --fix

       This will apply copyrights to files that we're newly added from
       python-import.

    7. Adjust some constants in the source code:

            version information in PythonLanguage (e.g., PythonLanguage#MINOR)

    8. Run the tests and fix any remaining issues.

    9. You should push the python-import branch using:

           git push origin python-import:python-import

    """

    with open(os.path.join(os.path.dirname(__file__), "copyrights", "overrides")) as f:
        entries = [line.split(',') for line in f if ',' in line]
        cpython_files = [file for file, license in entries if license == "python.copyright"]
        pypy_files = [file for file, license in entries if license == "pypy.copyright"]

    import_dir = os.path.abspath(os.path.join(SUITE.dir, 'python-import'))
    shutil.rmtree(import_dir, ignore_errors=True)
    # Fetch the python-import branch, would fail when not fast-forwardable
    SUITE.vc.git_command(SUITE.dir, ['fetch', 'origin', 'python-import:python-import'])
    # Checkout the python-import branch into a subdirectory
    SUITE.vc.git_command(SUITE.dir, ['clone', '-b', 'python-import', '.', 'python-import'])

    def copy_inlined_files(mapping, source_directory, overrides):
        overrides = list(overrides)
        for path, rule in mapping.items():
            rule.copy(source_directory, os.path.join(import_dir, path), overrides)
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
    need to pull the changes back to this repository using (from the main repository directory):
        git fetch python-import +python-import:python-import
    When you're happy with the state, push the changes using:
        git push origin python-import:python-import
    Then you can merge the imported files into the main repository using:
        git merge python-import
    Because the branches share a common ancestor, git will try to preserve our patches to files, that is,
    copyright headers and any other source patches.
    """))
