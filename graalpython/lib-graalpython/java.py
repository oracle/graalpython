# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


class JavaPackageLoader:
    @staticmethod
    def _make_getattr(modname):
        if modname.startswith("java."):
            modname_wo = modname[len("java."):] + "."
        else:
            modname_wo = None
        modname = modname + "."
        def __getattr__(key, default=None):
            try:
                return type(modname + key)
            except KeyError:
                pass
            if modname_wo:
                try:
                    return type(modname_wo + key)
                except KeyError:
                    pass
            raise AttributeError(key)
        return __getattr__

    @staticmethod
    def create_module(spec):
        newmod = _frozen_importlib._new_module(spec.name)
        newmod.__getattr__ = JavaPackageLoader._make_getattr(spec.name)
        newmod.__path__ = __path__
        return newmod

    @staticmethod
    def exec_module(module):
        pass


class JavaTypeLoader:
    @staticmethod
    def create_module(spec):
        pass

    @staticmethod
    def exec_module(module):
        try:
            sys.modules[module.__name__] = type(module.__name__)
        except KeyError:
            if module.__name__.startswith("java."):
                sys.modules[module.__name__] = type(module.__name__[len("java."):])
            else:
                raise


class JavaImportFinder:
    @staticmethod
    def find_spec(fullname, path, target=None):
        if path and path == __path__:
            if fullname.rpartition('.')[2].islower():
                return _frozen_importlib.ModuleSpec(fullname, JavaPackageLoader, is_package=True)
            else:
                return _frozen_importlib.ModuleSpec(fullname, JavaTypeLoader, is_package=False)


sys.meta_path.append(JavaImportFinder)
