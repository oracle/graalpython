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
            data_only=True,
        )
        write_folder_to_zipfile(
            zf, __graalpython__.stdlib_home, f"{vfs_prefix}/{home_prefix}/lib-python/3"
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


def write_folder_to_zipfile(zf, folder, prefix, data_only=False):
    """
    Store a folder with Python modules. We do not store source code, instead,
    we for each py file we create a pyc entry rightaway. .py files are created
    as empty files, their hashed bitcode set to not check the file hash. This
    ensures that packages are still imported correctly without storing any
    source code in directly. Any other resources in the folder are stored
    as-is. If data_only is given, neither .py nor .pyc files are added to the
    archive.
    """
    folder = folder.rstrip("/\\")
    for root, dirs, files in os.walk(folder):
        for dir in dirs:
            fullname = os.path.join(root, dir)
            arcname = os.path.join(prefix, fullname[len(folder) + 1 :])
            zf.writestr(zipfile.ZipInfo(f"{arcname}/"), b"")
        for file in files:
            fullname = os.path.join(root, file)
            arcname = os.path.join(prefix, fullname[len(folder) + 1 :])
            if file.endswith(".py"):
                if data_only:
                    continue
                zf.writestr(arcname, b"")
                pycname = _frozen_importlib_external.cache_from_source(fullname)
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
                arcname = os.path.join(prefix, pycname[len(folder) + 1 :])
                zf.writestr(arcname, data)
            elif file.endswith(".pyc"):
                pass
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
            "-o",
            output,
            JAVA_LAUNCHER,
        ]
        if parsed_args.verbose:
            print(f"Building Python standalone binary: {' '.join(cmd)}")
        subprocess.check_call(cmd)
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

    subparsers = parser.add_subparsers(required=True)

    parser_bin = subparsers.add_parser(
        "binary", help="Create a standalone binary from the Python code directly."
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
        "java",
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

    parsed_args = parser.parse_args(args)

    java_launcher_template = os.path.join(os.path.dirname(__file__), JAVA_LAUNCHER_FILE)
    (
        resource_zip,
        vfs_prefix,
        home_prefix,
        venv_prefix,
        proj_prefix,
    ) = parse_path_constants(java_launcher_template)

    preparing_java_project = hasattr(parsed_args, "output_directory")

    if preparing_java_project:
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

        if not preparing_java_project:
            build_binary(targetdir, jc, java_file, ni, parsed_args)
    finally:
        if not preparing_java_project and not parsed_args.keep_temp:
            shutil.rmtree(targetdir)


if __name__ == "__main__":
    main(sys.argv[1:])
