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


if sys.graal_python_host_import_enabled:
    class JavaPackageLoader:
        @staticmethod
        def is_java_package(name):
            return any(p.getName().startswith(name) for p in type("java.lang.Package").getPackages())

        @staticmethod
        def _make_getattr(modname):
            modname = modname + "."
            def __getattr__(key, default=None):
                loadname = modname + key
                if JavaPackageLoader.is_java_package(loadname):
                    return JavaPackageLoader._create_module(loadname)
                else:
                    try:
                        return type(modname + key)
                    except KeyError:
                        raise AttributeError(key)
            return __getattr__

        @staticmethod
        def create_module(spec):
            return JavaPackageLoader._create_module(spec.name)

        @staticmethod
        def _create_module(name):
            newmod = _frozen_importlib._new_module(name)
            newmod.__getattr__ = JavaPackageLoader._make_getattr(name)
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
            sys.modules[module.__name__] = type(module.__name__)


    class JavaImportFinder:
        @staticmethod
        def find_spec(fullname, path, target=None):
            if JavaPackageLoader.is_java_package(fullname):
                return _frozen_importlib.ModuleSpec(fullname, JavaPackageLoader, is_package=True)
            else:
                try:
                    type(fullname)
                    return _frozen_importlib.ModuleSpec(fullname, JavaTypeLoader, is_package=False)
                except KeyError:
                    pass


    sys.meta_path.append(JavaImportFinder)
    __getattr__ = JavaPackageLoader._make_getattr("java")
