# Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import re
from pathlib import Path
import argparse
import json
import os
import shutil
import site
import subprocess
import tempfile
import importlib
import time

import sys

WARNING = '\033[93m'
FAIL = '\033[91m'
ENDC = '\033[0m'
BOLD = '\033[1m'


def info(msg, *args, **kwargs):
    print(BOLD + msg.format(*args, **kwargs) + ENDC)


def error(msg, *args, **kwargs):
    message = msg.format(*args, **kwargs) if args or kwargs else msg
    print(FAIL + message + ENDC)


def warn(msg, *args, **kwargs):
    message = msg.format(*args, **kwargs) if args or kwargs else msg
    print(WARNING + message + ENDC)


def get_module_name(package_name):
    non_standard_packages = {
        'pyyaml': 'pyaml',
        'protobuf': 'google.protobuf',
        'python-dateutil': 'dateutil',
        'websocket-client': 'websocket',
        'attrs': 'attr',
        'scikit-learn': 'sklearn',
        'scikit_learn': 'sklearn',
    }
    module_name = non_standard_packages.get(package_name, package_name)
    return module_name.replace('-', '_')


def pip_package(name=None, try_import=False):
    def decorator(func):
        def wrapper(*args, **kwargs):
            _name = name if name else func.__name__
            try:
                module_name = get_module_name(_name)
                importlib.import_module(module_name)
                importlib.invalidate_caches()
            except (ImportError, ModuleNotFoundError):
                info("Installing required dependency: {} ... ", _name)
                func(*args, **kwargs)
                if try_import:
                    import site
                    site.main()
                    importlib.invalidate_caches()
                    importlib.import_module(module_name)
                info("{} installed successfully", _name)
        return wrapper
    return decorator


def run_cmd(args, msg="", failOnError=True, cwd=None, env=None, quiet=False, **kwargs):
    cwd_log = "cd " + cwd + " ;" if cwd else ""
    print("+", cwd_log, ' '.join(args))
    result = subprocess.run(args, cwd=cwd, env=env, capture_output=quiet, **kwargs)
    if failOnError and result.returncode != 0:
        xit_msg = [msg]
        if result.stdout:
            xit_msg.append("stdout:")
            xit_msg.append(result.stdout.decode("utf-8"))
        if result.stderr:
            xit_msg.append("stderr:")
            xit_msg.append(result.stderr.decode("utf-8"))
        xit("{}", os.linesep.join(xit_msg))
    return result.returncode


def known_packages():
    # @pip_package()
    # def lightfm(**kwargs):
    #     install_with_pip('requests')
    #     install_from_pypi("lightfm==1.15", **kwargs)
    return locals()


KNOWN_PACKAGES = known_packages()
_KNOW_PACKAGES_FILES = dict()


def get_file_for_package(package, version):
    return _KNOW_PACKAGES_FILES.get((package, version), None)


def set_file_for_package(pth):
    package, version = package_from_path(pth)
    _KNOW_PACKAGES_FILES[(package, version)] = pth
    return package


def xit(fmt, *args, **kwargs):
    error(fmt, *args, **kwargs)
    exit(-1)


def _download_with_curl_and_extract(dest_dir, url, quiet=False):
    name = url[url.rfind("/")+1:]

    downloaded_path = os.path.join(dest_dir, name)

    # first try direct connection
    if run_cmd(["curl", "-L", "-o", downloaded_path, url], failOnError=False, quiet=quiet) != 0:
        # honor env var 'HTTP_PROXY', 'HTTPS_PROXY', and 'NO_PROXY'
        env = os.environ
        curl_opts = []
        using_proxy = False
        if url.startswith("http://") and "HTTP_PROXY" in env:
            curl_opts += ["--proxy", env["HTTP_PROXY"]]
            using_proxy = True
        elif url.startswith("https://") and "HTTPS_PROXY" in env:
            curl_opts += ["--proxy", env["HTTPS_PROXY"]]
            using_proxy = True
        if using_proxy and "NO_PROXY" in env:
            curl_opts += ["--noproxy", env["NO_PROXY"]]
        run_cmd(["curl", "-L"] + curl_opts + ["-o", downloaded_path, url], msg="Download error", quiet=quiet)

    if name.endswith(".tar.gz"):
        run_cmd(["tar", "xzf", downloaded_path, "-C", dest_dir], msg="Error extracting tar.gz", quiet=quiet)
        bare_name = name[:-len(".tar.gz")]
    elif name.endswith(".tar.bz2"):
        run_cmd(["tar", "xjf", downloaded_path, "-C", dest_dir], msg="Error extracting tar.bz2", quiet=quiet)
        bare_name = name[:-len(".tar.bz2")]
    elif name.endswith(".zip"):
        run_cmd(["unzip", "-u", downloaded_path, "-d", dest_dir], msg="Error extracting zip", quiet=quiet)
        bare_name = name[:-len(".zip")]
    else:
        xit("Unknown file type: {!s}", name)

    return bare_name


def _install_from_url(url, package, extra_opts=None, add_cflags="", ignore_errors=False, env=None, version=None,
                      pre_install_hook=None, build_cmd=None, debug_build=False, with_meson=False):
    if build_cmd is None:
        build_cmd = []
    if env is None:
        env = {}
    if extra_opts is None:
        extra_opts = []
    tempdir = tempfile.mkdtemp()

    quiet = "-q" in extra_opts

    os_env = os.environ

    # honor env var 'CFLAGS' and the explicitly passed env
    setup_env = os_env.copy()
    setup_env.update(env)
    cflags = os_env.get("CFLAGS", "") + ((" " + add_cflags) if add_cflags else "")
    setup_env['CFLAGS'] = cflags if cflags else ""

    user_build_cmd = os_env.get("GINSTALL_BUILD_CMD", "")
    if user_build_cmd:
        build_cmd = user_build_cmd.split(" ")

    bare_name = _download_with_curl_and_extract(tempdir, url, quiet=quiet)
    extracted_dir = os.path.join(tempdir, bare_name, "")

    file_realpath = os.path.dirname(os.path.realpath(__file__))
    patches_dir = os.path.join(Path(file_realpath).parent, 'patches', package)
    # empty match group to have the same groups range as in pip_hook
    # unlike with pip, the version number may not be available at all
    versions = re.search("()(\\d+)?(.\\d+)?(.\\d+)?", "" if version is None else version)

    if "--no-autopatch" not in extra_opts:
        # run autopatch_capi
        spec = importlib.util.spec_from_file_location("autopatch_capi", os.path.join(file_realpath, "autopatch_capi.py"))
        autopatch_capi = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(autopatch_capi)
        info("auto-patching {}", extracted_dir)
        autopatch_capi.auto_patch_tree(extracted_dir)

    patch_file_path = first_existing(package, versions, get_sdist_patch(patches_dir), ".patch")
    if patch_file_path:
        run_cmd(["patch", "-d", extracted_dir, "-p1", "-i", patch_file_path], quiet=quiet)

    whl_patches_dir = os.path.join(patches_dir, "whl")
    patch_file_path = first_existing(package, versions, whl_patches_dir, ".patch")
    subdir = read_first_existing(package, versions, whl_patches_dir, ".dir")
    subdir = "" if subdir is None else subdir
    if patch_file_path:
        os.path.join(tempdir, bare_name, subdir)
        run_cmd(["patch", "-d", os.path.join(tempdir, bare_name, subdir), "-p1", "-i", patch_file_path], quiet=quiet)

    tmp_cwd = os.path.join(tempdir, bare_name)
    if pre_install_hook:
        pre_install_hook(tmp_cwd)

    if "--user" not in extra_opts and "--prefix" not in extra_opts and site.ENABLE_USER_SITE:
        user_arg = ["--user"]
    else:
        user_arg = []

    start = time.time()
    if with_meson:
        for cmd in [
            ['meson', 'setup', f'--prefix={os.environ.get("VIRTUAL_ENV")}', 'build'],
            ['meson', 'compile', '-C', 'build', '--ninja-args=-j4'],  # limit concurrency to 4
            ['meson', 'install', '-C', 'build'],
        ]:
            status = run_cmd(cmd, env=setup_env, cwd=tmp_cwd, quiet=quiet)
            if status != 0 and not ignore_errors:
                xit(f"An error occurred trying to run `{' '.join(cmd)}'")
    else:
        cmd = [sys.executable]
        if debug_build and sys.implementation.name == 'graalpy':
            cmd += ["-debug-java", "--python.ExposeInternalSources", "--python.WithJavaStacktrace=2"]
        cmd += ["setup.py"] + build_cmd + ["install"] + user_arg + extra_opts
        status = run_cmd(cmd, env=setup_env, cwd=tmp_cwd, quiet=quiet)
        if status != 0 and not ignore_errors:
            xit("An error occurred trying to run `setup.py install {!s} {}'", user_arg, " ".join(extra_opts))

    end = time.time()
    if quiet:
        info("{} successfully installed (took {:.2f} s)", package, (end - start))


# NOTE: Following 3 functions are duplicated in pip_hook.py:
# creates a search list of a versioned file:
# {name}-X.Y.Z.{suffix}, {name}-X.Y.{suffix}, {name}-X.{suffix}, {name}.{suffix}
# 'versions' is a result of re.search
def list_versioned(pkg_name, versions, dir, suffix):
    acc = ""
    res = []
    for i in range(2,5):
        v = versions.group(i)
        if v is not None:
            acc = acc + v
            res.append(acc)
    res.reverse()
    res = [os.path.join(dir, pkg_name + "-" + ver + suffix) for ver in res]
    res.append(os.path.join(dir, pkg_name + suffix))
    return res


def first_existing(pkg_name, versions, dir, suffix):
    for filename in list_versioned(pkg_name, versions, dir, suffix):
        if os.path.exists(filename):
            return filename


def read_first_existing(pkg_name, versions, dir, suffix):
    filename = first_existing(pkg_name, versions, dir, suffix)
    if filename:
        with open(filename, "r") as f:
            return f.read()

# end of code duplicated in pip_hook.py

def get_sdist_patch(patch_dir):
    sdist = os.path.join(patch_dir, "sdist")
    return sdist if os.path.isdir(sdist) else patch_dir


def install_with_pip(package, msg="", failOnError=False, **kwargs):
    for kw in ['extra_opts', 'debug_build']:
        if kw in kwargs:
            del kwargs[kw]
    run_cmd([sys.executable, "-m", "pip", "install", package], msg=msg, failOnError=failOnError, **kwargs)


def package_from_path(pth):
    assert os.path.exists(pth)
    package = os.path.splitext(os.path.split(pth)[-1])[0]
    version = None
    if "-" in package:
        package, _, version = package.rpartition("-")
    return package, version


def install_from_pypi(package, extra_opts=None, add_cflags="", ignore_errors=True, env=None, pre_install_hook=None,
                      build_cmd=None, debug_build=False, with_meson=False):
    if build_cmd is None:
        build_cmd = []
    if extra_opts is None:
        extra_opts = []
    package_pattern = os.environ.get("GINSTALL_PACKAGE_PATTERN", "https://pypi.org/pypi/%s/json")
    package_version_pattern = os.environ.get("GINSTALL_PACKAGE_VERSION_PATTERN", "https://pypi.org/pypi/%s/%s/json")

    version = None
    if "==" in package:
        package, _, version = package.rpartition("==")
        url = package_version_pattern % (package, version)
    else:
        url = package_pattern % package

    # check to see if we want to install the package from a file
    pth = get_file_for_package(package, version)
    if pth:
        url = f"file://{os.path.abspath(pth)}"
        warn(f"[install] installing '{package}' from '{url}'")

    if any(url.endswith(ending) for ending in [".zip", ".tar.bz2", ".tar.gz"]):
        # this is already the url to the actual package
        pass
    else:
        r = subprocess.check_output("curl -L %s" % url, stderr=subprocess.DEVNULL, shell=True).decode("utf8")
        url = None
        try:
            urls = json.loads(r)["urls"]
        except:
            pass
        else:
            for url_info in urls:
                if url_info["python_version"] == "source":
                    url = url_info["url"]
                    break

    # make copy of env
    env = env.copy() if env is not None else os.environ.copy()
    from distutils.sysconfig import get_config_var

    def set_if_exists(env_var, conf_var):
        conf_value = get_config_var(conf_var)
        if conf_value:
            env.setdefault(env_var, conf_value)

    set_if_exists("CC", "CC")
    set_if_exists("CXX", "CXX")
    set_if_exists("AR", "AR")
    set_if_exists("RANLIB", "RANLIB")
    set_if_exists("CFLAGS", "CFLAGS")
    set_if_exists("LDFLAGS", "CCSHARED")

    if url:
        _install_from_url(url, package=package, extra_opts=extra_opts, add_cflags=add_cflags,
                          ignore_errors=ignore_errors, env=env, version=version, pre_install_hook=pre_install_hook,
                          build_cmd=build_cmd, debug_build=debug_build, with_meson=with_meson)
    else:
        xit("Package not found: '{!s}'", package)


def get_site_packages_path():
    if site.ENABLE_USER_SITE:
        return site.getusersitepackages()
    else:
        for s in site.getsitepackages():
            if s.endswith("site-packages"):
                return s
    return None


def main(argv):
    parser = argparse.ArgumentParser(description="The simple Python package installer for GraalVM")
    parser.add_argument("--quiet", "-q", action="store_true", help="Do not show build output")

    subparsers = parser.add_subparsers(title="Commands", dest="command", metavar="Use COMMAND --help for further help.")

    subparsers.add_parser(
        "list",
        help="list locally installed packages"
    )

    install_parser = subparsers.add_parser(
        "install", help="install a known package",
        description="Install a known package. Known packages are:\n" + "\n".join(sorted(KNOWN_PACKAGES.keys())),
        formatter_class=argparse.RawDescriptionHelpFormatter)
    install_parser.add_argument("package", help="comma-separated list")
    install_parser.add_argument("--prefix", help="user-site path prefix")
    install_parser.add_argument("--user", action='store_true', help="install into user site")
    install_parser.add_argument("--debug-build", action="store_true", help="Enable debug options when building")
    install_parser.add_argument("--no-autopatch", action="store_true", help="Do not autopatch C extensions.")

    uninstall_parser = subparsers.add_parser(
        "uninstall", help="remove installation folder of a local package", )
    uninstall_parser.add_argument("package", help="comma-separated list")

    pypi_parser = subparsers.add_parser(
        "pypi", help="attempt to install a package from PyPI (untested, likely won't work, and it won't install "
                     "dependencies for you)",
        description="Attempt to install a package from PyPI")
    pypi_parser.add_argument("package", help="comma-separated list, can use `==` at the end of a package name to "
                                             "specify an exact version")

    args = parser.parse_args(argv)

    quiet_flag = ["-q"] if args.quiet else []

    if args.command == "list":
        user_site = get_site_packages_path()
        info("Installed packages:")
        for p in sys.path:
            if p.startswith(user_site):
                info(p[len(user_site) + 1:])
    elif args.command == "uninstall":
        warn("WARNING: I will only delete the package folder, proper uninstallation is not supported at this time.")
        user_site = get_site_packages_path()
        for pkg in args.package.split(","):
            deleted = False
            p = None
            for p in sys.path:
                if p.startswith(user_site):
                    # +1 due to the path separator
                    pkg_name = p[len(user_site)+1:]
                    if pkg_name.startswith(pkg):
                        if os.path.isdir(p):
                            shutil.rmtree(p)
                        else:
                            os.unlink(p)
                        deleted = True
                        break
            if deleted:
                info("Deleted {}", p)
            else:
                xit("Unknown package: '{!s}'", pkg)
    elif args.command == "install":
        extra_opts = [] + quiet_flag
        if args.prefix:
            extra_opts += ["--prefix", args.prefix]
        if args.user:
            extra_opts += ["--user"]
        if args.no_autopatch:
            extra_opts += ["--no-autopatch"]

        for pkg in args.package.split(","):
            if os.path.isfile(pkg):
                warn("installing from file: {}".format(pkg))
                pkg = set_file_for_package(pkg)

            if pkg not in KNOWN_PACKAGES:
                warn("package: '%s' not found in known packages, installing with pip" % pkg)
                install_with_pip(pkg, msg="Unknown package: '{!s}'".format(pkg), failOnError=True)
            else:
                KNOWN_PACKAGES[pkg](extra_opts=extra_opts, debug_build=args.debug_build)
    elif args.command == "pypi":
        for pkg in args.package.split(","):
            install_from_pypi(pkg, extra_opts=quiet_flag, ignore_errors=False)


if __name__ == "__main__":
    main(sys.argv[1:])
