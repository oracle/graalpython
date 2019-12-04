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
import posix
import sys
import _frozen_importlib


class JavaPackageLoader:
    if sys.graal_python_jython_emulation_enabled:
        @staticmethod
        def is_java_package(name):
            try:
                package = type("java.lang.Package")
                return any(p.getName().startswith(name) for p in package.getPackages())
            except KeyError:
                if sys.flags.verbose:
                    from _warnings import _warn
                    _warn("Host lookup allowed, but java.lang.Package not available. Importing from Java cannot work.")
                return False

        @staticmethod
        def _make_getattr(modname):
            modname = modname + "."
            def __getattr__(key, default=None):
                if sys.graal_python_host_import_enabled:
                    loadname = modname + key
                    if JavaPackageLoader.is_java_package(loadname):
                        return JavaPackageLoader._create_module(loadname)
                    else:
                        try:
                            return type(modname + key)
                        except KeyError:
                            pass
                raise AttributeError(key)
            return __getattr__
    else:
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

    if sys.graal_python_jython_emulation_enabled:
        @staticmethod
        def exec_module(module):
            sys.modules[module.__name__] = type(module.__name__)
    else:
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
    def is_type(self, name):
        try:
            type(name)
            return True
        except KeyError:
            return False

    if sys.graal_python_jython_emulation_enabled:
        def find_spec(self, fullname, path, target=None):
            # Because of how Jython allows you to import classes that have not
            # been loaded, yet, we need attempt to load types very eagerly.
            if self.is_type(fullname):
                # the fullname is already a type, let's load it
                return _frozen_importlib.ModuleSpec(fullname, JavaTypeLoader, is_package=False)
            else:
                current_import_name = __jython_current_import__()
                if current_import_name and self.is_type(current_import_name):
                    # We are currently handling an import that will lead to a
                    # Java type. The fullname is not a type itself, so it must
                    # be a package, not an enclosing class.
                    return _frozen_importlib.ModuleSpec(fullname, JavaPackageLoader, is_package=True)
                else:
                    # We are not currently handling an import statement, and the
                    # fullname is not a type. Thus we can only check if it is a
                    # known package.
                    if JavaPackageLoader.is_java_package(fullname):
                        return _frozen_importlib.ModuleSpec(fullname, JavaPackageLoader, is_package=True)
    else:
        def find_spec(self, fullname, path, target=None):
            if path and path == __path__:
                if fullname.rpartition('.')[2].islower():
                   return _frozen_importlib.ModuleSpec(fullname, JavaPackageLoader, is_package=True)
                else:
                    return _frozen_importlib.ModuleSpec(fullname, JavaTypeLoader, is_package=False)


if sys.graal_python_jython_emulation_enabled:
    class JarImportLoader:
        def __init__(self, code):
            self.code = code

        def create_module(self, spec):
            newmod = _frozen_importlib._new_module(spec.name)
            newmod.__path__ = self.code.co_filename
            return newmod

        def exec_module(self, module):
            exec(self.code, module.__dict__)


    class JarImportFinder:
        def __init__(self):
            self.zipimport = __import__("zipimport")

        def find_spec(self, fullname, path, target=None):
            for path in sys.path:
                if ".jar!" in path:
                    zipimport_path = path.replace(".jar!/", ".jar/").replace(".jar!", ".jar/")
                    zipimporter = self.zipimport.zipimporter(zipimport_path)
                    if zipimporter.find_module(fullname):
                        if zipimporter.is_package(fullname):
                            return _frozen_importlib.ModuleSpec(fullname, JarImportLoader(zipimporter.get_code(fullname)), is_package=True)
                        else:
                            return _frozen_importlib.ModuleSpec(fullname, JarImportLoader(zipimporter.get_code(fullname)), is_package=False)


    sys.meta_path.append(JarImportFinder())


sys.meta_path.append(JavaImportFinder())
if sys.graal_python_jython_emulation_enabled:
    __getattr__ = JavaPackageLoader._make_getattr("java")
else:
    # remove __jython_current_import__ function from builtins module
    import builtins
    del builtins.__jython_current_import__
    del builtins
