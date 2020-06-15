# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

# Package context -- the full module name for package imports
_py_package_context = None

@__graalpython__.builtin
def create_dynamic(module_spec, filename=None):
    global _py_package_context
    old_package_context = _py_package_context
    _py_package_context = str(module_spec.name)
    try:
        return __create_dynamic__(module_spec, filename)
    finally:
        _py_package_context = old_package_context


@__graalpython__.builtin
def exec_builtin(mod):
    return None


@__graalpython__.builtin
def init_frozen(name):
    return None


@__graalpython__.builtin
def is_frozen(name):
    return False


@__graalpython__.builtin
def get_frozen_object(name):
    raise ImportError("No such frozen object named %s" % name)


is_frozen_package = get_frozen_object


@__graalpython__.builtin
def cache_all_file_modules():
    """
    Caches all modules loaded during initialization through the normal import
    mechanism on the language, so that any additional contexts created in the
    same engine can re-use the cached CallTargets. See the _imp module for
    details on the module caching.
    """
    import sys
    for k,v in sys.modules.items():
        if hasattr(v, "__file__"):
            if not __graalpython__.has_cached_code(k):
                freeze_module(v, k)


@__graalpython__.builtin
def _patch_package_paths(paths):
    import sys
    return _sub_package_paths(paths, __graalpython__.stdlib_home, "!stdlib!")


@__graalpython__.builtin
def _unpatch_package_paths(paths):
    import sys
    return _sub_package_paths(paths, "!stdlib!", __graalpython__.stdlib_home)


@__graalpython__.builtin
def _sub_package_paths(paths, fro, to):
    if paths is not None:
        return [p.replace(fro, to) for p in paths]


@__graalpython__.builtin
def freeze_module(mod, key=None):
    """
    Freeze a module under the optional key in the language cache so that it can
    be shared across multiple contexts. If the module is a package in the
    standard library path, it's __path__ is substituted to not leak the standard
    library path to other contexts.
    """
    import sys
    path = _patch_package_paths(getattr(mod, "__path__", None))
    name = key or mod.__name__
    __graalpython__.cache_module_code(key, mod.__file__, path)


class CachedImportFinder:
    @staticmethod
    def find_spec(fullname, path, target=None):
        path = _unpatch_package_paths(__graalpython__.get_cached_code_path(fullname))
        if path is not None:
            if len(path) > 0:
                submodule_search_locations = path
                is_package = True
            else:
                submodule_search_locations = None
                is_package = False
            spec = CachedImportFinder.ModuleSpec(fullname, CachedLoader, is_package=is_package)
            # we're not setting origin, so the module won't have a __file__
            # attribute and will show up as built-in
            spec.submodule_search_locations = submodule_search_locations
            return spec


class CachedLoader:
    import sys

    @staticmethod
    def create_module(spec):
        pass

    @staticmethod
    def exec_module(module):
        modulename = module.__name__
        exec(__graalpython__.get_cached_code(modulename), module.__dict__)
        CachedLoader.sys.modules[modulename] = module
