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

"""
- Create and a skeleton maven polyglot Java - Python application.

- Create and build a skeleton Python launcher to bundle a Python application
into a single-binary executable.

This tool uses GraalVM Native Image to prepare a self-contained binary from a
script, module, and optionally venv.

"""

import abc
import argparse
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

import _frozen_importlib_external

assert sys.pycache_prefix is None

JAVA_LAUNCHER = "Py2BinLauncher"
JAVA_LAUNCHER_FILE = f"{JAVA_LAUNCHER}.java"
JAVA_LAUNCHER_PATH = f"launcher/{JAVA_LAUNCHER_FILE}"

VIRTUAL_FILESYSTEM_TEMPLATE_FILE = "VirtualFileSystem.java"
VIRTUAL_FILESYSTEM_TEMPLATE_PATH = f"shared/{VIRTUAL_FILESYSTEM_TEMPLATE_FILE}"
NATIVE_IMAGE_PROXY_CONF_PATH = f"shared/native-image-proxy-configuration.json"
NATIVE_IMAGE_RESOURCES_FILE = "native-image-resources.json"
NATIVE_IMAGE_RESOURCES_PATH = f"shared/{NATIVE_IMAGE_RESOURCES_FILE}"

CMD_NATIVE_EXECUTABLE = "native"
CMD_JAVA_BINDINGS = "java_bindings"
CMD_JAVA_PYTHON_APP = "polyglot_app"
ATTR_STANDALONE_CMD = "command"

MVN_CODE_PREFIX = "src/main/java"
MVN_RESOURCE_PREFIX = "src/main/resources"

class AbstractStandalone:

    def __init__(self, parsed_args):
        self.parsed_args = parsed_args
        self.virtual_filesystem_template = self.get_file(VIRTUAL_FILESYSTEM_TEMPLATE_PATH)
        vfs_prefix, vfs_fileslist_path = self.parse_vfs_prefix_constant()
        self.vfs_prefix = vfs_prefix
        self.vfs_fileslist_path = vfs_fileslist_path

    @abc.abstractmethod
    def create(self):
        pass    

    @staticmethod
    def get_file(*paths):
        return os.path.join(os.path.dirname(__file__), *paths)

    def create_virtual_filesystem_file(self, target_file, java_pkg=""):
        lines = open(self.virtual_filesystem_template, 'r').readlines()
        with open(target_file, 'w') as f:
            for line in lines:
                if "{java-pkg}" in line:
                    line = line.replace("{java-pkg}", java_pkg)
                f.write(line)

    def parse_vfs_prefix_constant(self):
        """
        Determine the vitual filesystem prefix.
        """

        with open(self.virtual_filesystem_template) as f:
            content = f.read()
        vfs_prefix = re.search(
            'static final String VFS_PREFIX = "/([^"]+)"', content
        ).group(1)
        fileslist_path = re.search(
            'static final String FILES_LIST_PATH = "/([^"]+)"', content
        ).group(1)
        return vfs_prefix, fileslist_path

    def check_output_directory(self):
        if hasattr(self.parsed_args, "module") and os.path.abspath(self.parsed_args.output_directory).startswith(os.path.abspath(self.parsed_args.module)):
            print(
                "Output directory cannot be placed inside of module folder to run.",
                sep="\n",
            )
            exit(1)

        if os.path.exists(os.path.abspath(self.parsed_args.output_directory)):
            print(
                "Output directory already exists.",
                sep="\n",
            )
            exit(1)

class PolyglotJavaPython(AbstractStandalone):

    def create(self):
        self.check_output_directory()

        target_dir = self.parsed_args.output_directory
        
        if self.parsed_args.verbose:
            print(f"Creating polyglot java python application in directory {target_dir}")

        os.makedirs(target_dir, exist_ok=True)

        # java sources
        shutil.copytree(os.path.join(os.path.dirname(__file__), "app/src"), os.path.join(target_dir, "src"))

        virtual_filesystem_java_file = os.path.join(target_dir, MVN_CODE_PREFIX, "com", "mycompany", "javapython", VIRTUAL_FILESYSTEM_TEMPLATE_FILE)
        self.create_virtual_filesystem_file(virtual_filesystem_java_file, "package com.mycompany.javapython;")

        # std lib        
        vfs_home = os.path.join(target_dir, MVN_RESOURCE_PREFIX, self.vfs_prefix, "home")
        os.makedirs(vfs_home, exist_ok=True)
        shutil.copytree(__graalpython__.capi_home, os.path.join(vfs_home, "lib-graalpython"))
        shutil.copytree(__graalpython__.stdlib_home, os.path.join(vfs_home, "lib-python", "3"))

        # misc
        shutil.copy(os.path.join(os.path.dirname(__file__), NATIVE_IMAGE_RESOURCES_PATH), target_dir)
        shutil.copy(os.path.join(os.path.dirname(__file__), NATIVE_IMAGE_PROXY_CONF_PATH), target_dir)
        shutil.copy(os.path.join(os.path.dirname(__file__), "app/pom.xml"), target_dir)

class Standalone(AbstractStandalone):
    def __init__(self, parsed_args):
        super().__init__(parsed_args)
        self.parsed_args = parsed_args

        self.java_launcher_template = self.get_file(JAVA_LAUNCHER_PATH)

        (home_prefix, venv_prefix, proj_prefix,) = self.parse_standalone_path_constants(self.java_launcher_template)
        self.home_prefix = home_prefix
        self.venv_prefix = venv_prefix
        self.proj_prefix = proj_prefix

    def parse_standalone_path_constants(self, javafile):
        """
        Determine the constants used by the Java launcher pertaining to the layout
        of the resources file.
        """

        with open(javafile) as f:
            content = f.read()

        home_prefix = re.search(
            r'static final String HOME_PREFIX = VirtualFileSystem\.VFS_PREFIX \+ "/([^"]+)"', content
        ).group(1)
        venv_prefix = re.search(
            r'static final String VENV_PREFIX = VirtualFileSystem\.VFS_PREFIX \+ "/([^"]+)"', content
        ).group(1)
        proj_prefix = re.search(
            r'static final String PROJ_PREFIX = VirtualFileSystem\.VFS_PREFIX \+ "/([^"]+)"', content
        ).group(1)
        return home_prefix, venv_prefix, proj_prefix

    def create_target_directory(self):
        if self.parsed_args.verbose:
            print(f"Bundling Python resources into {self.target_dir}")

        self.bundle_python_resources(
            os.path.join(self.target_dir, self.resource_prefix),
            self.vfs_prefix,
            self.home_prefix,
            self.venv_prefix,
            self.proj_prefix,
            self.parsed_args.module,
            self.parsed_args.venv,
        )

        os.makedirs(os.path.dirname(self.launcher_file), exist_ok=True)
        shutil.copy(self.java_launcher_template, self.launcher_file)
        
        virtual_filesystem_java_file = os.path.join(self.target_dir, self.code_prefix, VIRTUAL_FILESYSTEM_TEMPLATE_FILE)
        self.create_virtual_filesystem_file(virtual_filesystem_java_file)
        
        shutil.copy(self.get_file(NATIVE_IMAGE_RESOURCES_PATH), os.path.join(self.target_dir, NATIVE_IMAGE_RESOURCES_FILE))

    def bundle_python_resources(self, target_dir, vfs_prefix, home_prefix, venv_prefix, proj_prefix, project, venv=None):
        """
        Copy the Python core, stdlib, venv, and module into one folder.
        """

        os.makedirs(os.path.dirname(target_dir), exist_ok=True)

        self.copy_folder_to_target(
            target_dir,
            __graalpython__.capi_home,
            f"{vfs_prefix}/{home_prefix}/lib-graalpython",
            path_filter=lambda file=None, dir=None: file and file.endswith(".py"),
        )

        self.copy_folder_to_target(
            target_dir,
            __graalpython__.stdlib_home,
            f"{vfs_prefix}/{home_prefix}/lib-python/3",
            path_filter=lambda file=None, dir=None: dir
            and dir in ["idlelib", "ensurepip", "tkinter", "turtledemo"],
        )

        if venv:
            self.copy_folder_to_target(target_dir, venv, f"{vfs_prefix}/{venv_prefix}")

        if project and os.path.isdir(project):
            self.copy_folder_to_target(target_dir, project, f"{vfs_prefix}/{proj_prefix}")
        else:
            with tempfile.TemporaryDirectory() as tmpdir:
                name = os.path.join(tmpdir, "__main__.py")
                shutil.copy(project, name)
                self.copy_folder_to_target(target_dir, tmpdir, f"{vfs_prefix}/{proj_prefix}")
                os.unlink(name)

    def copy_folder_to_target(self, resource_root, folder, prefix, path_filter=lambda file=None, dir=None: False):
        """
        Store a folder with Python modules. We do not store source code, instead,
        for each py file we create a pyc entry rightaway. Any other resources in the
        folder are stored as-is. If data_only is given, neither .py nor .pyc files are
        added to the archive.
        """
        folder = folder.rstrip("/\\")
        for root, dirs, files in os.walk(folder):
            dirs[:] = filter(lambda d: not path_filter(dir=d) and d != "__pycache__", dirs)

            for file in files:
                if path_filter(file=file):
                    continue
                fullname = os.path.join(root, file)
                arcname = os.path.join(prefix, fullname[len(folder) + 1 :])

                resource_parent_path = os.path.dirname(os.path.join(resource_root, arcname))
                os.makedirs(resource_parent_path, exist_ok=True)

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
                    with open(os.path.join(resource_root, arcname), "wb") as f:
                        f.write(data)
                else:
                    shutil.copy(fullname, os.path.join(resource_root, arcname))

class JavaBinding(Standalone):
    def __init__(self, parsed_args):
        super().__init__(parsed_args)

        self.target_dir = parsed_args.output_directory
        self.code_prefix = MVN_CODE_PREFIX
        self.resource_prefix = MVN_RESOURCE_PREFIX
        self.launcher_file = os.path.join(self.target_dir, self.code_prefix, JAVA_LAUNCHER_FILE)
        
    def create(self):
        self.check_output_directory()

        os.makedirs(self.target_dir, exist_ok=True)    
        self.create_target_directory()
        shutil.copy(os.path.join(os.path.dirname(__file__), "launcher", "pom.xml"), self.target_dir)

class NativeExecutable(Standalone):
    
    def __init__(self, parsed_args):
        super().__init__(parsed_args)
        
        self.target_dir = tempfile.mkdtemp()
        self.code_prefix = ""
        self.resource_prefix = ""
        self.launcher_file = os.path.join(self.target_dir, self.code_prefix, JAVA_LAUNCHER_FILE)
                
    def create(self):
        try:
            self.create_target_directory()
            files_list_path = os.path.join(self.target_dir, self.vfs_fileslist_path)
            dir_to_list = os.path.join(self.target_dir, self.vfs_prefix)
            __graalpython__.list_files(dir_to_list, files_list_path)
            self.build_binary()
        finally:
            if not self.parsed_args.keep_temp:
                shutil.rmtree(self.target_dir)

    @staticmethod
    def get_executable(file):
        if os.path.isfile(file):
            return file
        exe = f"{file}.exe"
        if os.path.isfile(exe):
            return exe
        exe = f"{file}.cmd"
        if os.path.isfile(exe):
            return exe
        return None
    
    @staticmethod
    def get_tools(verbose):
        java_home = os.environ.get("GRAALVM_HOME", os.environ.get("JAVA_HOME", ""))
        if java_home:
            ni = NativeExecutable.get_executable(os.path.join(java_home, "bin", "native-image"))
            jc = NativeExecutable.get_executable(os.path.join(java_home, "bin", "javac"))
            if verbose:                
                print(f"found JAVA_HOME: {java_home}")
                print(f"  native_image: {ni}")
                print(f"  javac: {jc}")

        if not java_home or not ni or not jc or not (os.path.isfile(ni) and os.path.isfile(jc)):
            java_home = os.path.join(__graalpython__.home, "..", "..")
            ni = NativeExecutable.get_executable(os.path.join(java_home, "bin", "native-image"))
            jc = NativeExecutable.get_executable(os.path.join(java_home, "bin", "javac"))
            if verbose:                
                print(f"falled back on __graalpython__.home: {java_home}")
                print(f"  native_image: {ni}")
                print(f"  javac: {jc}")

        if not ni or not jc or not os.path.isfile(ni) or not os.path.isfile(jc):
            print(
                "This tool requires a GraalVM installation including the native-image tool and javac.",
                "Please point the JAVA_HOME environment variable to such a GraalVM root.",
                sep="\n",
            )
            exit(1)
        return ni, jc

    def build_binary(self):
        cwd = os.getcwd()
        output = os.path.abspath(self.parsed_args.output)
        os.chdir(self.target_dir)
        ni, jc = self.get_tools(self.parsed_args.verbose)
        try:
            # it would seem it is enough to compile the launcher file, but on some linux setups it isn't
            cmd = [jc, os.path.join(self.target_dir, "VirtualFileSystem.java"), self.launcher_file]
            if self.parsed_args.verbose:
                print(f"Compiling code for Python standalone entry point: {' '.join(cmd)}")
            p = subprocess.run(cmd, cwd=self.target_dir, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if p.returncode != 0:
                print(p.stdout.decode())
                print(p.stderr.decode())
                exit(1)

            cmd = [ni] + self.parsed_args.ni_args[:]
            if self.parsed_args.Os:
                cmd +=[
                    "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                    "-Dpolyglot.engine.WarnInterpreterOnly=false",
                ]
            cmd += [
                "--language:python",
                "-H:-CopyLanguageResources",
                "-H:ResourceConfigurationFiles=native-image-resources.json",
                "-o",
                output,
                JAVA_LAUNCHER,
            ]
            if self.parsed_args.verbose:
                print(f"Building Python standalone binary: {' '.join(cmd)}")
            subprocess.check_call(cmd, cwd=self.target_dir)
        finally:
            os.chdir(cwd)

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

    if parsed_args.command == CMD_JAVA_PYTHON_APP:
        standalone = PolyglotJavaPython(parsed_args)
    elif parsed_args.command == CMD_JAVA_BINDINGS:
        standalone = JavaBinding(parsed_args)
    else :
        standalone = NativeExecutable(parsed_args)

    standalone.create()

if __name__ == "__main__":
    main(sys.argv[1:])
