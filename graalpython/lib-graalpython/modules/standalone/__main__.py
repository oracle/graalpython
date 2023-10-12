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

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import platform
import socket
import urllib
import urllib.request
import tarfile
import zipfile

assert sys.pycache_prefix is None

VFS_PREFIX = "vfs"
VFS_HOME = "home"
VFS_HOME_PREFIX = f"{VFS_PREFIX}/{VFS_HOME}"
VFS_VENV_PREFIX = VFS_PREFIX + "/venv"
VFS_PROJ_PREFIX = VFS_PREFIX + "/proj"

NATIVE_EXEC_JAVA_PKG = "package standalone;"

VFS_JAVA_FILE = "VirtualFileSystem.java"
VFS_JAVA_FILE_TEMPLATE = f"templates/{VFS_JAVA_FILE}"

NATIVE_EXEC_LAUNCHER = "Py2BinLauncher"
NATIVE_EXEC_LAUNCHER_FILE = f"{NATIVE_EXEC_LAUNCHER}.java"
NATIVE_EXEC_LAUNCHER_TEMPLATE_PATH = f"templates/{NATIVE_EXEC_LAUNCHER_FILE}"

NATIVE_IMAGE_RESOURCES_FILE = "native-image-resources.json"
NATIVE_IMAGE_RESOURCES_PATH = f"shared/{NATIVE_IMAGE_RESOURCES_FILE}"

MODULE_NAME = "standalone"
MODULE_INFO_FILE = "module-info.java"
MODULE_INFO_PATH = f"shared/{MODULE_INFO_FILE}"

GRAALVM_URL_BASE = "https://download.oracle.com/graalvm/"

MVN_REPOSITORY = os.getenv("MVN_REPOSITORY")
MVN_PYTHON_ARTEFACT_ID = "python-community"
MVN_PYTHON_COMMUNITY_VERSION = os.getenv("MVN_GRAALPY_VERSION")

FILES_LIST_NAME = "fileslist.txt"
FILES_LIST_PATH = VFS_PREFIX + "/" + FILES_LIST_NAME

CMD_NATIVE_EXECUTABLE = "native"
CMD_JAVA_PYTHON_APP = "polyglot_app"
ATTR_STANDALONE_CMD = "command"

def get_file(*paths):
    return os.path.join(os.path.dirname(__file__), *paths)

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

def create_polyglot_app(parsed_args):
    if hasattr(parsed_args, "module") and os.path.abspath(parsed_args.output_directory).startswith(os.path.abspath(parsed_args.module)):
        print(
            "Output directory cannot be placed inside of module folder to run.",
            sep="\n",
        )
        exit(1)

    if os.path.exists(os.path.abspath(parsed_args.output_directory)):
        print(
            "Output directory already exists.",
            sep="\n",
        )
        exit(1)

    target_dir = parsed_args.output_directory if parsed_args.output_directory else os.getcwd()
    if parsed_args.verbose:
        print(f"Creating polyglot java python application in directory {target_dir}")

    cmd = ["mvn", "archetype:generate", "-DarchetypeGroupId=org.graalvm.python", "-DarchetypeArtifactId=graalpy-archetype"]
    cmd += [f"-DgroupId={parsed_args.group_id}"]
    cmd += [f"-DartifactId={parsed_args.artifact_id}"]
    cmd += ["-Dversion=1.0-SNAPSHOT" if parsed_args.version else f"-Dversion={parsed_args.version}"]
    cmd += [f"-DoutputDirectory={target_dir}"]

    if parsed_args.verbose:
        p = subprocess.run(cmd)
    else:
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    if p.returncode != 0:
        if not parsed_args.verbose:
            print(p.stdout.decode())
            print(p.stderr.decode())
        exit(1)

def create_native_exec(parsed_args):
    target_dir = tempfile.mkdtemp()
    modules_path = os.path.join(target_dir, "modules")
    launcher_file = os.path.join(target_dir, MODULE_NAME, NATIVE_EXEC_LAUNCHER_FILE)
    
    try:
        ni, jc = get_tools(target_dir, modules_path, parsed_args)
        download_python(modules_path, parsed_args)
        create_target_directory(target_dir, launcher_file, parsed_args)
        index_vfs(target_dir)
        build_binary(target_dir, ni, jc, modules_path, launcher_file, parsed_args)
    finally:
        if not parsed_args.keep_temp:
            shutil.rmtree(target_dir)
   
def index_vfs(target_dir):   
    files_list_path = os.path.join(target_dir, FILES_LIST_PATH)
    dir_to_list = os.path.join(target_dir, VFS_PREFIX)        
    target_dir_len = len(target_dir)
    with open(files_list_path, "w") as files_list:
        def f(dir_path, names, line_end):
            rel_path = dir_path[target_dir_len:]
            for name in names:
                vfs_path = os.path.join(rel_path, name)
                files_list.write(f"{vfs_path}{line_end}")
        w = os.walk(dir_to_list)            
        for (dir_path, dir_names, file_names) in w:
            f(dir_path, dir_names, "/\n")
            f(dir_path, file_names, "\n")
                
def create_virtual_filesystem_file(vfs_file, java_pkg=""):
    lines = open(get_file(VFS_JAVA_FILE_TEMPLATE), 'r').readlines()
    with open(vfs_file, 'w') as f:
        for line in lines:
            if "{java-pkg}" in line:
                line = line.replace("{java-pkg}", java_pkg)
            if "{vfs-prefix}" in line:
                line = line.replace("{vfs-prefix}", VFS_PREFIX)
            if "{files-list-name}" in line:
                line = line.replace("{files-list-name}", FILES_LIST_NAME)
            f.write(line)

def create_launcher_file(template, launcher, java_pkg=""):
    lines = open(template, 'r').readlines()
    with open(launcher, 'w') as f:
        for line in lines:
            if "{java-pkg}" in line:
                line = line.replace("{java-pkg}", java_pkg)
            if "{vfs-home-prefix}" in line:
                line = line.replace("{vfs-home-prefix}", VFS_HOME_PREFIX)
            if "{vfs-venv-prefix}" in line:
                line = line.replace("{vfs-venv-prefix}", VFS_VENV_PREFIX)
            if "{vfs-proj-prefix}" in line:
                line = line.replace("{vfs-proj-prefix}", VFS_PROJ_PREFIX)
            f.write(line)

def create_target_directory(target_dir, launcher_file, parsed_args):
    if parsed_args.verbose:
        print(f"Bundling Python resources into {target_dir}")

    bundle_python_resources(
        target_dir,
        parsed_args.module,
        parsed_args.venv,
    )

    os.makedirs(os.path.dirname(launcher_file), exist_ok=True)
    create_launcher_file(get_file(NATIVE_EXEC_LAUNCHER_TEMPLATE_PATH), launcher_file, NATIVE_EXEC_JAVA_PKG)

    virtual_filesystem_java_file = os.path.join(target_dir, MODULE_NAME, VFS_JAVA_FILE)
    create_virtual_filesystem_file(virtual_filesystem_java_file, NATIVE_EXEC_JAVA_PKG)

    shutil.copy(get_file(NATIVE_IMAGE_RESOURCES_PATH), os.path.join(target_dir, NATIVE_IMAGE_RESOURCES_FILE))
    shutil.copy(get_file(MODULE_INFO_PATH), os.path.join(target_dir, MODULE_INFO_FILE))

def bundle_python_resources(target_dir, project, venv=None):
    """
    Copy the Python core, stdlib, venv, and module into one folder.
    """

    os.makedirs(os.path.dirname(target_dir), exist_ok=True)

    lib_source = __graalpython__.capi_home
    copy_folder_to_target(
        target_dir,
        lib_source,
        f"{VFS_HOME_PREFIX}/lib-graalpython",
        path_filter=lambda file=None, dir=None: file and file.endswith(".py"),
    )

    lib_source = __graalpython__.stdlib_home
    copy_folder_to_target(
        target_dir,
        lib_source,
        f"{VFS_HOME_PREFIX}/lib-python/3",
        path_filter=lambda file=None, dir=None: (
            (
                dir
                and dir
                in [
                    "idlelib",
                    "ensurepip",
                    "tkinter",
                    "turtledemo",
                    "sulong",
                    "llvm-toolchain",
                ]
            )
            or (file and file.split(".")[-1] in ["so", "dll", "dylib"])
        ),
    )

    if venv:
        copy_folder_to_target(target_dir, venv, VFS_VENV_PREFIX)

    if project and os.path.isdir(project):
        copy_folder_to_target(target_dir, project, VFS_PROJ_PREFIX)
    else:
        with tempfile.TemporaryDirectory() as tmpdir:
            name = os.path.join(tmpdir, "__main__.py")
            shutil.copy(project, name)
            copy_folder_to_target(target_dir, tmpdir, VFS_PROJ_PREFIX)
            os.unlink(name)

def copy_folder_to_target(resource_root, folder, prefix, path_filter=lambda file=None, dir=None: False):
    """
    Store a folder with Python modules.
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
            shutil.copy(fullname, os.path.join(resource_root, arcname))

def get_graalvm_url():
    jdk_version = __graalpython__.get_jdk_version()
    if "." in jdk_version:
        major_version = jdk_version[:jdk_version.index(".")]
    else:
        major_version = jdk_version

    system = platform.system()
    sufix = 'tar.gz'
    if system == 'Darwin':
        system = 'macos'
    elif system == 'Linux':
        system = 'linux'
    elif system == 'win32':
        sufix = 'zip'
        system = 'windows'
    else:
        raise RuntimeError("Unknown platform system", system)

    machine = platform.machine()
    if machine == 'x86_64':
        machine = 'x64'
    elif machine == 'arm64' or machine == 'aarch64':
        machine = 'aarch64'
    else:
        raise RuntimeError("Unknown platform machine", machine)

    return f"{GRAALVM_URL_BASE}{major_version}/archive/graalvm-jdk-{jdk_version}_{system}-{machine}_bin.{sufix}"

def get_tools(target_dir, modules_path, parsed_args):
    if os.getenv("JAVA_HOME"):
        graalvm_home = os.getenv("JAVA_HOME")
    else:
        modules_path = os.path.join(target_dir, "lib")
        graalvm_url = get_graalvm_url()
        os.makedirs(modules_path, exist_ok=True)
        graalvm_file = os.path.join(modules_path, graalvm_url[graalvm_url.rindex("/") + 1:])
        if parsed_args.verbose:
            print(f"downloading {graalvm_url} to {graalvm_file}")
        try:
            urllib.request.urlretrieve(graalvm_url, graalvm_file)
        except (socket.gaierror, urllib.error.URLError) as err:
            raise ConnectionError(f"failed to download from {graalvm_url}: {err}")

        if platform.system() == 'Darwin' or platform.system() == 'Linux':
            with tarfile.open(graalvm_file) as tar_file:
                first_member = tar_file.next().path
                tar_file.extractall(modules_path)
        else:
            with zipfile.ZipFile(graalvm_file) as zip_file:
                first_member = zip_file.namelist()[0]
                zip_file.extractall(modules_path)

        graalvm_dir = os.path.join(modules_path, first_member[:first_member.index("/")])
        if platform.system() == 'Darwin':
            graalvm_home = os.path.join(graalvm_dir, "Contents", "Home")
        else:
            graalvm_home = graalvm_dir

    ni = get_executable(os.path.join(graalvm_home, "bin", "native-image"))
    jc = get_executable(os.path.join(graalvm_home, "bin", "javac"))
    if parsed_args.verbose:
        print(f"using GRAALVM: {graalvm_home}")
        print(f"  native_image: {ni}")
        print(f"  javac: {jc}")

    if not ni or not os.path.exists(ni):
        if not parsed_args.verbose:
            print(f"using GRAALVM: {graalvm_home}")
            print(f"  native_image: {ni}")
            print(f"  javac: {jc}")
        if os.getenv("JAVA_HOME"):
            print("If using JAVA_HOME env variable, please point it to a GraalVM installation with native image and javac")
        else:
            graalvm_url = get_graalvm_url()
            print(f"GraalVM downloaded from {graalvm_url} has no native image or javac")
        sys.exit(1)

    return ni, jc

def download_python(modules_path, parsed_args):
    graalvm_version = MVN_PYTHON_COMMUNITY_VERSION if MVN_PYTHON_COMMUNITY_VERSION else __graalpython__.get_graalvm_version()
    
    mvnd = get_executable(os.path.join(__graalpython__.home, "libexec", "graalpy-polyglot-get"))
    cmd = [mvnd]

    if MVN_REPOSITORY:
        cmd += ["-r", MVN_REPOSITORY]
    cmd += ["-a", MVN_PYTHON_ARTEFACT_ID]
    cmd += ["-v", graalvm_version]
    cmd += ["-o", modules_path]
    if parsed_args.verbose:
        print(f"downloading graalpython maven artefacts: {' '.join(cmd)}")

    if parsed_args.verbose:
        p = subprocess.run(cmd)
    else:
        p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    if p.returncode != 0:
        if not parsed_args.verbose:
            print(p.stdout.decode())
            print(p.stderr.decode())
        exit(1)

def build_binary(target_dir, ni, jc, modules_path, launcher_file, parsed_args):
    cwd = os.getcwd()
    output = os.path.abspath(parsed_args.output)
    os.chdir(target_dir)

    try:
        cmd = [jc, "--module-path", modules_path, os.path.join(target_dir,MODULE_INFO_FILE), os.path.join(target_dir, MODULE_NAME, "VirtualFileSystem.java"), launcher_file]
        if parsed_args.verbose:
            print(f"Compiling code for Python standalone entry point: {' '.join(cmd)}")
        p = subprocess.run(cmd, cwd=target_dir, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if p.returncode != 0:
            print(p.stdout.decode())
            print(p.stderr.decode())
            exit(1)

        ni_modules = os.pathsep.join([os.path.join(modules_path, f) for f in os.listdir(modules_path) if f.endswith(".jar")] + [target_dir])
        cmd = [ni, "--module-path", ni_modules] + parsed_args.ni_args[:]

        if parsed_args.Os:
            cmd +=[
                "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
            ]
            # Remove once GR-48563 is fixed
            if "Oracle GraalVM" in subprocess.check_output([ni, "--version"], text=True):
                cmd.append("-H:-OptConditionalMoves")
        cmd += [
            "--no-fallback",
            "-H:-CopyLanguageResources",
            f"-H:ResourceConfigurationFiles={target_dir}/{NATIVE_IMAGE_RESOURCES_FILE}",
            "-o",
            output,
            f"{MODULE_NAME}.{NATIVE_EXEC_LAUNCHER}",
        ]
        if parsed_args.verbose:
            print(f"Building Python standalone binary: {' '.join(cmd)}")
        subprocess.check_call(cmd, cwd=target_dir)
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

    parser_app = subparsers.add_parser(
        CMD_JAVA_PYTHON_APP,
        help="Create a polyglot Java-Python maven project skeleton preconfigured to build native binaries.",
    )    
    
    parser_app.add_argument(
        "-g",
        "--group-id",
        help="The created maven project group id.",
        required=True,
    )
    
    parser_app.add_argument(
        "-a",
        "--artifact-id",
        help="The created maven project artifact id.",
        required=True,
    )
    
    parser_app.add_argument(
        "-v",
        "--version",
        help="The created maven project version.",
    )
    
    parser_app.add_argument(
        "-o",
        "--output-directory",
        help="The directory to write the maven project to.",
    )

    parsed_args = parser.parse_args(args)

    if parsed_args.command == CMD_JAVA_PYTHON_APP:
        create_polyglot_app(parsed_args)
    else :
        create_native_exec(parsed_args)

if __name__ == "__main__":
    main(sys.argv[1:])
