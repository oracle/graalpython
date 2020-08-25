# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import _frozen_importlib
import sys


class PipLoader:
    def __init__(self, real_spec):
        self.real_spec = real_spec

    def create_module(self, spec):
        return self.real_spec.loader.create_module(self.real_spec)

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
            name_ver_match = re.search("^([^-]+)-(\\d+)(.\\d+)?(.\\d+)?.*\\.(tar\\.gz|whl)$", archive_name)
            if not name_ver_match:
                print("Warning: could not parse package name, version, or format from '{}'.\n"
                      "Could not determine if any GraalPython specific patches need to be applied.".format(archive_name))
                return result

            package_name = name_ver_match.group(1)
            is_sdist = name_ver_match.group(5) == "tar.gz"
            patches_base_dir = os.path.join(__graalpython__.core_home, "patches")

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
                        print("Applying Graal Python patch failed for %s. The package may still work." % package_name)

            print("Looking for Graal Python patches for " + package_name)

            # patches intended for binary distribution:
            # we may need to change wd if we are actually patching the source distribution
            bdist_dir = os.path.join(patches_base_dir, package_name, "whl")
            bdist_patch_wd = read_first_existing(package_name, name_ver_match, bdist_dir, ".dir") if is_sdist else None
            apply_first_existing(bdist_dir, ".patch", bdist_patch_wd)

            # patches intended for source distribution if applicable
            if is_sdist:
                sdist_dir = os.path.join(patches_base_dir, package_name, "sdist")
                apply_first_existing(sdist_dir, ".patch")

            return result

        module.unpack_file = unpack_file
        return exec_module_result


class PipImportHook:
    @staticmethod
    def find_spec(fullname, path, target=None):
        # We are patching function 'unpack_file',
        # which may be in a different module depending on the PIP version.
        # Older versions have it pip._internal.utils.misc, newer versions
        # still have module pip._internal.utils.misc, but they moved
        # 'unpack_file' to pip._internal.utils.unpacking
        is_unpacking = fullname == "pip._internal.utils.unpacking"
        is_misc_or_unpacking = is_unpacking or fullname == "pip._internal.utils.misc"
        if is_misc_or_unpacking:
            for finder in sys.meta_path:
                if finder is PipImportHook:
                    continue
                real_spec = finder.find_spec(fullname, path, target=None)
                if real_spec:
                    if is_unpacking:
                        # We cannot remove ourselves if the module was pip._internal.utils.misc,
                        # because we still need to watch out for pip._internal.utils.unpacking
                        sys.meta_path.remove(PipImportHook)
                    spec = _frozen_importlib.ModuleSpec(fullname, PipLoader(real_spec), is_package=False, origin=real_spec.origin)
                    spec.has_location = real_spec.has_location
                    return spec


sys.meta_path.insert(0, PipImportHook)
