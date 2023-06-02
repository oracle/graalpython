# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

"""Create and build a skeleton Python launcher to bundle a Python application
into a single-binary executable.

This tool uses GraalVM Native Image to prepare a self-contained binary from a
script, module, and optionally venv.

"""

import argparse
import io
import itertools
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

import _frozen_importlib_external

assert sys.pycache_prefix is None


JAVA_LAUNCHER = "Py2BinLauncher"
JAVA_LAUNCHER_FILE = f"{JAVA_LAUNCHER}.java"

CMD_NATIVE_EXECUTABLE = "native_executable"
CMD_JAVA_BINDINGS = "java_bindings"
CMD_JAVA_PYTHON_APP = "polyglot_java_python_app"
ATTR_STANDALONE_CMD = "command"

def get_tools():
    java_home = os.environ.get("GRAALVM_HOME", os.environ.get("JAVA_HOME", ""))
    if not java_home:
        java_home = os.path.join(__graalpython__.home, "..", "..")
    ni = os.path.join(java_home, "bin", "native-image")
    jc = os.path.join(java_home, "bin", "javac")
    if not (os.path.isfile(ni) and os.path.isfile(jc)):
        print(
            "This tool requires a GraalVM installation including the native-image tool and javac.",
            "Please point the JAVA_HOME environment variable to such a GraalVM root.",
            sep="\n",
        )
        exit(1)
    return ni, jc


def parse_path_constants(javafile):
    """
    Determine the constants used by the Java launcher pertaining to the layout
    of the resources file.
    """
    with open(javafile) as f:
        content = f.read()
    resource_zip = re.search(
        'static final String RESOURCE_ZIP = "([^"]+)"', content
    ).group(1)
    vfs_prefix = re.search(
        'static final String VFS_PREFIX = "/([^"]+)"', content
    ).group(1)
    home_prefix = re.search(
        r'static final String HOME_PREFIX = VFS_PREFIX \+ "/([^"]+)"', content
    ).group(1)
    venv_prefix = re.search(
        r'static final String VENV_PREFIX = VFS_PREFIX \+ "/([^"]+)"', content
    ).group(1)
    proj_prefix = re.search(
        r'static final String PROJ_PREFIX = VFS_PREFIX \+ "/([^"]+)"', content
    ).group(1)
    return resource_zip, vfs_prefix, home_prefix, venv_prefix, proj_prefix


def ensure_directories(zf, path):
    """
    Recursively create directory entries in a zip file.
    """
    for prefix in itertools.accumulate(path.split("/"), func=lambda a, b: f"{a}/{b}"):
        dirname = f"{prefix}/"
        try:
            zf.getinfo(dirname)
        except KeyError:
            zf.writestr(zipfile.ZipInfo(dirname), b"")


def bundle_python_resources(
    zipname, vfs_prefix, home_prefix, venv_prefix, proj_prefix, project, venv=None
):
    """
    Bundle the Python core, stdlib, venv, and module into a zip file.
    """
    os.makedirs(os.path.dirname(zipname), exist_ok=True)
    with zipfile.ZipFile(
        zipname, mode="w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as zf:
        ensure_directories(zf, vfs_prefix)
        ensure_directories(zf, f"{vfs_prefix}/{home_prefix}/lib-graalpython")
        ensure_directories(zf, f"{vfs_prefix}/{home_prefix}/lib-python/3")
        ensure_directories(zf, f"{vfs_prefix}/{venv_prefix}")
        ensure_directories(zf, f"{vfs_prefix}/{proj_prefix}")

        write_folder_to_zipfile(
            zf,
            __graalpython__.capi_home,
            f"{vfs_prefix}/{home_prefix}/lib-graalpython",
            path_filter=lambda file=None, dir=None: file and file.endswith(".py"),
        )
        write_folder_to_zipfile(
            zf,
            __graalpython__.stdlib_home,
            f"{vfs_prefix}/{home_prefix}/lib-python/3",
            path_filter=lambda file=None, dir=None: dir
            and dir in ["idlelib", "ensurepip", "tkinter", "turtledemo"],
        )

        if venv:
            write_folder_to_zipfile(zf, venv, f"{vfs_prefix}/{venv_prefix}")

        if project and os.path.isdir(project):
            write_folder_to_zipfile(zf, project, f"{vfs_prefix}/{proj_prefix}")
        else:
            with tempfile.TemporaryDirectory() as tmpdir:
                name = os.path.join(tmpdir, "__main__.py")
                shutil.copy(project, name)
                write_folder_to_zipfile(zf, tmpdir, f"{vfs_prefix}/{proj_prefix}")
                os.unlink(name)


def write_folder_to_zipfile(zf, folder, prefix, path_filter=lambda file=None, dir=None: False):
    """
    Store a folder with Python modules. We do not store source code, instead,
    for each py file we create a pyc entry rightaway. Any other resources in the
    folder are stored as-is. If data_only is given, neither .py nor .pyc files are
    added to the archive.
    """
    folder = folder.rstrip("/\\")
    for root, dirs, files in os.walk(folder):
        dirs[:] = filter(lambda d: not path_filter(dir=d) and d != "__pycache__", dirs)
        for dir in dirs:
            fullname = os.path.join(root, dir)
            arcname = os.path.join(prefix, fullname[len(folder) + 1 :])
            zf.writestr(zipfile.ZipInfo(f"{arcname}/"), b"")
        for file in files:
            if path_filter(file=file):
                continue
            fullname = os.path.join(root, file)
            arcname = os.path.join(prefix, fullname[len(folder) + 1 :])
            if file.endswith(".py"):
                arcname = os.path.splitext(arcname)[0] + ".pyc"
                with io.open_code(fullname) as sourcefile:
                    code = sourcefile.read()
                try:
                    bytecode = compile(code, fullname, "exec", dont_inherit=True)
                except:
                    print(f"Warning: Not including {fullname}")
                    bytecode = compile("None", fullname, "exec", dont_inherit=True)
                data = _frozen_importlib_external._code_to_hash_pyc(
                    bytecode, b"0" * 8, checked=False
                )
                zf.writestr(arcname, data)
            else:
                zf.write(fullname, arcname)


def build_binary(targetdir, jc, java_file, ni, parsed_args):
    cwd = os.getcwd()
    output = os.path.abspath(parsed_args.output)
    os.chdir(targetdir)
    try:
        cmd = [jc, java_file]
        if parsed_args.verbose:
            print(f"Compiling code for Python standalone entry point: {' '.join(cmd)}")
        subprocess.check_call(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        cmd = [ni] + parsed_args.ni_args[:]
        if parsed_args.Os:
            cmd += [
                "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
            ]
        cmd += [
            "--language:python",
            "-H:-CopyLanguageResources",
            "-o",
            output,
            JAVA_LAUNCHER,
        ]
        if parsed_args.verbose:
            print(f"Building Python standalone binary: {' '.join(cmd)}")
        subprocess.check_call(cmd)
    finally:
        os.chdir(cwd)

def check_output_directory(parsed_args):
    if os.path.abspath(parsed_args.output_directory).startswith(os.path.abspath(parsed_args.module)):
        print(
            "Output directory cannot be placed inside of module folder to run.",
            sep="\n",
        )
        exit(1)

def main(args):
    parser = argparse.ArgumentParser(prog=f"{sys.executable} -m standalone")
    parser.add_argument(
        "--verbose", action="store_true", help="Print actions as they are performed"
    )
    parser.add_argument(
        "--keep-temp", action="store_true", help="Keep temporary files for debugging."
    )

    subparsers = parser.add_subparsers(required=True, dest=ATTR_STANDALONE_CMD)

    parser_bin = subparsers.add_parser(
        CMD_NATIVE_EXECUTABLE, help="Create a standalone binary from the Python code directly."
    )
    parser_bin.add_argument(
        "-m", "--module", help="Python file or module folder to run", required=True
    )
    parser_bin.add_argument("--venv", help="Python venv to bundle")
    parser_bin.add_argument(
        "-o", "--output", help="Output filename for the binary", required=True
    )
    parser_bin.add_argument(
        "-Os", action="store_true", help="Optimize the binary for size, not speed"
    )
    parser_bin.add_argument(
        "-N",
        action="append",
        dest="ni_args",
        help="extra arguments to pass to the GraalVM Native Image build command",
        metavar="<arg>",
        default=[],
    )

    parser_jar = subparsers.add_parser(
        CMD_JAVA_BINDINGS,
        help="Create a Java project from the Python code. This gives the most flexibility, as the project can be used to build both standalone Jar files or native binaries using Maven.",
    )
    parser_jar.add_argument(
        "-m", "--module", help="Python file or module folder to run", required=True
    )
    parser_jar.add_argument("--venv", help="Python venv to bundle")
    parser_jar.add_argument(
        "-o",
        "--output-directory",
        help="The directory to write the Java project to.",
        required=True,
    )

    parser_app = subparsers.add_parser(
        CMD_JAVA_PYTHON_APP,
        help="Create a skeleton Java project. This gives the most flexibility, as the project can be used to build both standalone Jar files or native binaries using Maven.",
    )
    
    parser_app.add_argument(
        "-o",
        "--output-directory",
        help="The directory to write the Java project to.",
        required=True,
    )
    
    parsed_args = parser.parse_args(args)

    if(parsed_args.command == CMD_JAVA_PYTHON_APP):        
        target_dir = parsed_args.output_directory
        os.makedirs(target_dir, exist_ok=True)

        shutil.copytree(os.path.join(os.path.dirname(__file__), "app/src"), os.path.join(target_dir, "src"))

        vfs_home = os.path.join(target_dir, "src", "main", "resources", "vfs", "home")
        os.makedirs(vfs_home, exist_ok=True)
        shutil.copytree(__graalpython__.capi_home, os.path.join(vfs_home, "lib-graalpython"))
        shutil.copytree(__graalpython__.stdlib_home, os.path.join(vfs_home, "lib-python", "3"))

        shutil.copy(os.path.join(os.path.dirname(__file__), "app/native-image-resources.json"), target_dir )
        shutil.copy(os.path.join(os.path.dirname(__file__), "app/pom.xml"), target_dir)
        
    else:    
        preparing_java_binding = parsed_args.command == CMD_JAVA_BINDINGS

        if preparing_java_binding:
            check_output_directory(parsed_args)

        java_launcher_template = os.path.join(os.path.dirname(__file__), JAVA_LAUNCHER_FILE)
        (
            resource_zip,
            vfs_prefix,
            home_prefix,
            venv_prefix,
            proj_prefix,
        ) = parse_path_constants(java_launcher_template)

        if preparing_java_binding:
            ni, jc = "", ""
            resource_prefix = os.path.join("src", "main", "resources")
            code_prefix = os.path.join("src", "main", "java")
            targetdir = parsed_args.output_directory
        else:
            ni, jc = get_tools()
            resource_prefix = ""
            code_prefix = ""
            targetdir = tempfile.mkdtemp()

        if parsed_args.verbose:
            print(f"Creating target directory {targetdir}")
        os.makedirs(targetdir, exist_ok=True)
        try:
            if parsed_args.verbose:
                print("Bundling Python resources")
            bundle_python_resources(
                os.path.join(targetdir, resource_prefix, resource_zip),
                vfs_prefix,
                home_prefix,
                venv_prefix,
                proj_prefix,
                parsed_args.module,
                parsed_args.venv,
            )

            java_file = os.path.join(targetdir, code_prefix, JAVA_LAUNCHER_FILE)
            os.makedirs(os.path.dirname(java_file), exist_ok=True)
            shutil.copy(java_launcher_template, java_file)
            shutil.copy(os.path.join(os.path.dirname(__file__), "pom.xml"), targetdir)

            if not preparing_java_binding:
                build_binary(targetdir, jc, java_file, ni, parsed_args)
        finally:
            if not preparing_java_binding and not parsed_args.keep_temp:
                shutil.rmtree(targetdir)


if __name__ == "__main__":
    main(sys.argv[1:])
