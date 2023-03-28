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


def parse_path_constants(javafile):
    """
    Determine the constants used by the Java launcher pertaining to the layout
    of the resources file.
    """
    with open(javafile) as f:
        content = f.read()
    resource_zip = re.search('static final String RESOURCE_ZIP = "([^"]+)"', content).group(1)
    vfs_prefix = re.search('static final String VFS_PREFIX = "/([^"]+)"', content).group(1)
    home_prefix = re.search('static final String HOME_PREFIX = VFS_PREFIX \+ "/([^"]+)"', content).group(1)
    venv_prefix = re.search('static final String VENV_PREFIX = VFS_PREFIX \+ "/([^"]+)"', content).group(1)
    proj_prefix = re.search('static final String PROJ_PREFIX = VFS_PREFIX \+ "/([^"]+)"', content).group(1)
    return resource_zip, vfs_prefix, home_prefix, venv_prefix, proj_prefix


def ensure_directories(zf, path):
    """
    Recursively create directory entries in a zip file.
    """
    for prefix in itertools.accumulate(path.split("/"), func=lambda a,b: f"{a}/{b}"):
        dirname = f"{prefix}/"
        try:
            zf.getinfo(dirname)
        except KeyError:
            zf.writestr(zipfile.ZipInfo(dirname), b'')


def bundle_python_resources(zipname, vfs_prefix, home_prefix, venv_prefix, proj_prefix, venv=None, project=None):
    """
    Bundle the Python core, stdlib, venv, and module into a zip file.
    """
    os.makedirs(os.path.dirname(zipname), exist_ok=True)
    with zipfile.ZipFile(zipname, mode="w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        ensure_directories(zf, vfs_prefix)
        ensure_directories(zf, f"{vfs_prefix}/{home_prefix}/lib-graalpython")
        ensure_directories(zf, f"{vfs_prefix}/{home_prefix}/lib-python/3")
        ensure_directories(zf, f"{vfs_prefix}/{venv_prefix}")
        ensure_directories(zf, f"{vfs_prefix}/{proj_prefix}")

        write_folder_to_zipfile(zf, __graalpython__.capi_home, f"{vfs_prefix}/{home_prefix}/lib-graalpython", data_only=True)
        write_folder_to_zipfile(zf, __graalpython__.stdlib_home, f"{vfs_prefix}/{home_prefix}/lib-python/3")

        if venv:
            write_folder_to_zipfile(zf, venv, f"{vfs_prefix}/{venv_prefix}")

        if os.path.isdir(project):
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
            arcname = os.path.join(prefix, fullname[len(folder) + 1:])
            zf.writestr(zipfile.ZipInfo(f"{arcname}/"), b'')
        for file in files:
            fullname = os.path.join(root, file)
            arcname = os.path.join(prefix, fullname[len(folder) + 1:])
            if file.endswith(".py"):
                if data_only:
                    continue
                zf.writestr(arcname, b"")
                pycname = _frozen_importlib_external.cache_from_source(fullname)
                with io.open_code(fullname) as sourcefile:
                    code = sourcefile.read()
                bytecode = compile(code, fullname, "exec", dont_inherit=True)
                data = _frozen_importlib_external._code_to_hash_pyc(bytecode, b"0" * 8, checked=False)
                arcname = os.path.join(prefix, pycname[len(folder) + 1:])
                zf.writestr(arcname, data)
            elif file.endswith(".pyc"):
                pass
            else:
                zf.write(fullname, arcname)


def main(args):
    parser = argparse.ArgumentParser(prog='mx graalpytest')
    parser.add_argument('target', help='The target directory to write the skeleton to.')
    parser.add_argument('module', help='Python file or module folder to run')
    parser.add_argument('venv', nargs='?', default=None, help='Python venv to use')

    parsed_args = parser.parse_args(args)

    java_launcher_template = os.path.join(os.path.dirname(__file__), JAVA_LAUNCHER_FILE)
    resource_zip, vfs_prefix, home_prefix, venv_prefix, proj_prefix = parse_path_constants(java_launcher_template)

    targetdir = parsed_args.target
    os.makedirs(targetdir, exist_ok=True)
    bundle_python_resources(os.path.join(targetdir, "src", "main", "resources", resource_zip), vfs_prefix, home_prefix, venv_prefix, proj_prefix, parsed_args.venv, parsed_args.module)

    java_file = os.path.join(targetdir, "src", "main", "java", JAVA_LAUNCHER_FILE)
    os.makedirs(os.path.dirname(java_file), exist_ok=True)
    shutil.copy(java_launcher_template, java_file)
    shutil.copy(os.path.join(os.path.dirname(__file__), "pom.xml"), targetdir)


if __name__ == "__main__":
    main(sys.argv[1:])
