# Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

"""The purpose of this import hook is two-fold. We have patches for certain
packages to make them work on GraalPy. These patches need to be applied when the
relevant packages are unpacked. Additionally, we want certain packages to always
prefer known versions if that agrees with the version spec the user of pip
install or the package has specified in its requirements.

The PipInstallLoader takes care of the latter - when packages are installed
through the "install" or "wheel" commands, the argument version specs are
parsed, we look up which patches we have for the package (if any). If we have
only patches for a few specific versions for that packages, and one or more of
these match the version-range that was requested, we narrow the version spec to
request the best specific package version that we have a patch for. If we have a
generic patch file for a package in addition to specific, versioned patches, we
do not narrow. The assumption is that a generic patch file means we can work
with any version of the package, and the specific ones just fix issues we have
with specific versions.

The PipUnpackLoader takes care of the former - once we are unpacking a package,
we need to apply the most specific patch we have for it.
"""

import _frozen_importlib
import sys


NAME_VER_PATTERN = "([^-]+)-(\\d+)(.\\d+)?(.\\d+)?"
WARNED = False

class PipLoader:
    def __init__(self, real_spec):
        self.real_spec = real_spec
        import os
        self._patches_base_dirs = [os.path.join(__graalpython__.core_home, "patches")]
        if hasattr(__graalpython__, "tdebug"):
            self._patches_base_dirs += os.environ.get('PIPLOADER_PATCHES_BASE_DIRS', "").split(",")

    def create_module(self, spec):
        return self.real_spec.loader.create_module(self.real_spec)

    def print_version_warning(self):
        global WARNED
        if not WARNED:
            print("WARNING: You are using an untested version of pip. GraalPy",
                  "provides patches and workarounds for a number of packages when used with",
                  "compatible pip versions. We recommend to stick with the pip version that",
                  "ships with this version of GraalPy.")
            WARNED = True


class PipInstallLoader(PipLoader):
    def exec_module(self, module):
        exec_module_result = self.real_spec.loader.exec_module(module)
        cmd = getattr(module, 'InstallCommand', getattr(module, 'WheelCommand', None))
        if cmd is None:
            return exec_module_result

        try:
            from pip._vendor.packaging.requirements import Requirement
        except:
            self.print_version_warning()
            return exec_module_result

        infos_printed = set()

        original_init = Requirement.__init__
        def new_init(self, req_string, *args, **kwargs):
            req_string = narrow_requirement_to_supported_version(req_string)
            return original_init(self, req_string, *args, **kwargs)

        def narrow_requirement_to_supported_version(req_string):
            # we may find a patch directory and then we should prefer a
            # version with a patch from that directory
            import os
            import re

            Requirement.__init__ = original_init
            try:
                req = Requirement(req_string)
            except:
                pass
            else:
                patchfiles = []
                for pbd in self._patches_base_dirs:
                    for sfx in ["whl", "sdist"]:
                        dir = os.path.join(pbd, req.name, sfx)
                        if os.path.isdir(dir):
                            for f in os.listdir(dir):
                                if f == f"{req.name}.patch":
                                    # generic patch available, don't care about
                                    # the version
                                    return req_string
                                m = re.match(f"{NAME_VER_PATTERN}\\.patch", f)
                                if m:
                                    version = "".join([g for g in m.group(2, 3, 4) if g])
                                    if version in req.specifier:
                                        patchfiles.append(version)

                if patchfiles:
                    version = sorted(patchfiles)[-1]
                    if req.name not in infos_printed:
                        print(f"INFO: Choosing GraalPy tested version {version} for {req}")
                        infos_printed.add(req.name)
                    req.specifier = Requirement(f"{req.name}=={version}").specifier
                    req_string = str(req)
            finally:
                Requirement.__init__ = new_init

            return req_string

        original_run = cmd.run

        def run(self, options, args, *splat, **kwargs):
            args = [narrow_requirement_to_supported_version(a) for a in args]

            Requirement.__init__ = new_init
            try:
                return original_run(self, options=options, args=args, *splat, **kwargs)
            finally:
                Requirement.__init__ = original_init

        cmd.run = run

        return exec_module_result


class PipUnpackLoader(PipLoader):
    def exec_module(self, module):
        exec_module_result = self.real_spec.loader.exec_module(module)
        if not hasattr(module, 'unpack_file'):
            return exec_module_result

        old_unpack = module.unpack_file

        def unpack_file(filename, location, *args, **kwargs):
            result = old_unpack(filename, location, *args, **kwargs)

            import os
            import re
            import subprocess
            # we expect filename to be something like "pytest-5.4.2-py3-none-any.whl"
            # some packages may have only major.minor or just major version
            archive_name = os.path.basename(filename)
            name_ver_match = re.search("^{0}.*\\.(tar\\.gz|whl|zip)$".format(NAME_VER_PATTERN), archive_name)
            if not name_ver_match:
                print("Warning: could not parse package name, version, or format from '{}'.\n"
                      "Could not determine if any GraalPy specific patches need to be applied.".format(archive_name))
                return result

            package_name = name_ver_match.group(1)
            is_sdist = name_ver_match.group(5) in ("tar.gz", "zip")

            # NOTE: Following 3 functions are duplicated in ginstall.py:
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

            # end of code duplicated in ginstall.py

            def apply_first_existing(dir, suffix, wd=None):
                filename = first_existing(package_name, name_ver_match, dir, suffix)
                if filename:
                    print("Patching package " + package_name + " using " + filename)
                    patch_res = subprocess.run(["patch", "-f", "-d", location, "-p1", "-i", filename], cwd=wd)
                    if patch_res.returncode != 0:
                        print("Applying GraalPy patch failed for %s. The package may still work." % package_name)
                elif os.path.isdir(dir):
                    patchfiles = [f for f in os.listdir(dir) if re.match("{0}{1}$".format(NAME_VER_PATTERN, suffix), f)]
                    if patchfiles:
                        print("We have patches to make this package work on GraalVM for some version(s).")
                        print("If installing or running fails, consider using one of the versions that we have patches for:\n\t", "\n\t".join(patchfiles), sep="")

            print("Looking for GraalPy patches for " + package_name)

            for pbd in self._patches_base_dirs:
                # patches intended for binary distribution:
                # we may need to change wd if we are actually patching the source distribution
                bdist_dir = os.path.join(pbd, package_name, "whl")
                bdist_patch_wd = read_first_existing(package_name, name_ver_match, bdist_dir, ".dir") if is_sdist else None
                apply_first_existing(bdist_dir, ".patch", bdist_patch_wd)

                # patches intended for source distribution if applicable
                if is_sdist:
                    sdist_dir = os.path.join(pbd, package_name, "sdist")
                    apply_first_existing(sdist_dir, ".patch")

            return result

        module.unpack_file = unpack_file
        return exec_module_result


class PipImportHook:
    @staticmethod
    def _wrap_real_spec(wrapper, fullname, path, target=None):
        for finder in sys.meta_path:
            if finder is PipImportHook:
                continue
            real_spec = finder.find_spec(fullname, path, target=None)
            if real_spec:
                spec = _frozen_importlib.ModuleSpec(fullname, wrapper(real_spec), is_package=False, origin=real_spec.origin)
                spec.has_location = real_spec.has_location
                return spec

    @staticmethod
    def find_spec(fullname, path, target=None):
        if fullname in ["pip._internal.commands.install", "pip._internal.commands.wheel"]:
            return PipImportHook._wrap_real_spec(PipInstallLoader, fullname, path, target=None)
        elif fullname in ["pip._internal.utils.unpacking", "pip._internal.utils.misc"]:
            return PipImportHook._wrap_real_spec(PipUnpackLoader, fullname, path, target=None)


sys.meta_path.insert(0, PipImportHook)
