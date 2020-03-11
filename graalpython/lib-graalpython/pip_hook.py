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
import sys
import _frozen_importlib


class PipLoader:
    def __init__(self, real_spec):
        self.real_spec = real_spec

    def create_module(self, spec):
        return self.real_spec.loader.create_module(self.real_spec)

    def exec_module(self, module):
        exec_module_result = self.real_spec.loader.exec_module(module)
        old_unpack = module.unpack_file
        def unpack_file(filename, location, *args, **kwargs):
            import os
            result = old_unpack(filename, location, *args, **kwargs)
            package_name = os.path.basename(location)
            potential_patch = os.path.join(sys.graal_python_core_home, "patches", package_name + ".patch")
            print("Looking for Graal Python patch for " + package_name + " in " + potential_patch)
            if os.path.exists(potential_patch):
                print("Patching package " + package_name)
                os.system("patch -d %s -p1 < %s" % (location, potential_patch))
            return result
        module.unpack_file = unpack_file
        return exec_module_result


class PipImportHook:
    @staticmethod
    def find_spec(fullname, path, target=None):
        if fullname == "pip._internal.utils.misc":
            for finder in sys.meta_path:
                if finder is PipImportHook:
                    continue
                real_spec = finder.find_spec(fullname, path, target=None)
                if real_spec:
                    sys.meta_path.remove(PipImportHook)
                    spec = _frozen_importlib.ModuleSpec(fullname, PipLoader(real_spec), is_package=False, origin=real_spec.origin)
                    spec.has_location = real_spec.has_location
                    return spec


sys.meta_path.insert(0, PipImportHook)
