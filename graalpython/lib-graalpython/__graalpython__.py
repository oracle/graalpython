# Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

@builtin
def import_current_as_named_module(module, name, owner_globals=None):
    """
    load a builtin anonymous module which does not have a Truffle land builtin module counter part

    :param name: the module name to load as
    :param owner_globals: the module globals (to update)
    :return: the loaded module
    """
    if owner_globals is None:
        owner_globals = {}

    module = sys.modules.get(name, None)
    if not module:
        module = type(sys)(name)
        sys.modules[name] = module
    new_globals = dict(**owner_globals)
    new_globals.update(**module.__dict__)
    module.__dict__.update(**new_globals)
    return module


@builtin
def lazy_attributes_from_delegate(module, delegate_name, attributes, owner_module, on_import_error):
    """
    used to lazily load attributes defined in another module via the __getattr__ mechanism.
    This will only cache the attributes in the caller module.

    :param delegate_name: the delegate module
    :param attributes: a list of attributes names to be loaded lazily from the delagate module
    :param owner_module: the owner module (where this is called from)
    :param on_import_error: a dict of replacement attributes in case of import error
    :return:
    """
    attributes.append('__all__')

    def __getattr__(attr):
        if attr in attributes:
            try:
                delegate_module = __import__(delegate_name)
            except ImportError:
                if on_import_error and (attr in on_import_error):
                    return on_import_error[attr]
                else:
                    raise

            new_globals = dict(**delegate_module.__dict__)
            new_globals.update(**owner_module.__dict__)
            owner_module.__dict__.update(**new_globals)

            if '__getattr__' in owner_module.__dict__:
                del owner_module.__dict__['__getattr__']

            return getattr(delegate_module, attr)
        raise AttributeError("module '{}' does not have '{}' attribute".format(delegate_name, attr))

    owner_module.__dict__['__getattr__'] = __getattr__


@builtin
def auto_wrap_methods(module, delegate_name, delegate_attributes, owner_globals):
    func_type = type(import_current_as_named_module)

    new_globals = dict(**owner_globals)

    for attr in owner_globals:
        if attr.startswith("__"):
            continue
        elif not isinstance(owner_globals[attr], func_type):
            continue
        elif attr not in delegate_attributes:
            raise AttributeError("attribute '{}' not allowed in module '{}', permitted values are: '{}'".format(
                attr, __name__, delegate_attributes
            ))

        if attr in delegate_attributes:
            def make_wrapper(attribute, method):
                @__graalpython__.builtin
                def wrapper(*args, **kwargs):
                    try:
                        return method(*args, **kwargs)
                    except NotImplementedError:
                        delegate_module = __import__(delegate_name)
                        return getattr(delegate_module, attribute)(*args, **kwargs)
                return wrapper

            new_globals[attr] = make_wrapper(attr, owner_globals[attr])

    return new_globals


@builtin
def import_current_as_named_module_with_delegate(module, module_name, delegate_name, delegate_attributes=None,
                                                 owner_globals=None, wrap_methods=True, on_import_error=None):
    owner_module = import_current_as_named_module(module_name, owner_globals=owner_globals)
    if wrap_methods and owner_globals:
        wrapped_globals = auto_wrap_methods(delegate_name, delegate_attributes, owner_globals)
        owner_module.__dict__.update(**wrapped_globals)
    if delegate_attributes:
        lazy_attributes_from_delegate(delegate_name, delegate_attributes, owner_module, on_import_error)


@builtin
def build_java_class(module, ns, name, base):
    ns['__super__'] = None  # place where store the original java class when instance is created
    ExtenderClass = type("PythonJavaExtenderClass", (object, ), ns)
    HostAdapter = __graalpython__.extend(base)
    resultClass = type(name, (object, ), {})

    def factory (cls, *args):
        # creates extender object and store the super java class
        extenderInstance = ExtenderClass()
        args = args[1:] + (extenderInstance, ) # remove the class and add the extender instance object
        hostObject = HostAdapter(*args)   # create new adapter
        extenderInstance.__super__ = __graalpython__.super(hostObject)   #set the super java object
        return hostObject

    resultClass.__new__ = classmethod(factory)
    return resultClass


# @builtin
# def compile(codestr, path, mode="pyc"):
#     bltin_compile = __builtins__["compile"]
#     if mode in ["pyc", "pyc-nocompile"]:
#         import os, subprocess, marshal
#         if isinstance(codestr, bytes):
#             codestr = codestr.decode()
#         if not os.path.exists(path):
#             content = f"'''{codestr}'''"
#         else:
#             content = f"open('{path}').read()"
#         code = f"""if True:
#         import sys, marshal
#         sys.stdout.buffer.write(marshal.dumps(compile({content}, '{path}', 'exec')))
#         """
#         proc = subprocess.run(["/usr/bin/python3", "-S", "-c", code], capture_output=True)
#         if proc.returncode == 0:
#             if mode == "pyc":
#                 print(f"Loaded {path} via CPython")
#                 return bltin_compile(marshal.loads(proc.stdout), path, "exec")
#             else:
#                 return proc.stdout
#         raise RuntimeError("CPython bytecode compilation error")
#     else:
#         return bltin_compile(codestr, path, mode)
