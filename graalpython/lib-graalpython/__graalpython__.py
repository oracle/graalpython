# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
    :param attributes: a list of attributes names to be loaded lazily from the delegate module
    :param owner_module: the owner module (where this is called from)
    :param on_import_error: a dict of replacement attributes in case of import error
    :return:
    """

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
            new_globals = { key: new_globals[key] for key in attributes }
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
                def wrapper(module, *args, **kwargs):
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
def build_java_class(module, ns, name, base, new_style=False):
    if new_style:
        return build_new_style_java_class(ns, name, base)
    import warnings
    warnings.warn("Subclassing Java classes is going to change "
                  "to a new instance layout that is hopefully "
                  "more intuitive. Pass the keyword new_style=True "
                  "to your class definition to try the new style. "
                  "The new style will become the default in the next "
                  "release and the old style will be removed soon after.", DeprecationWarning, 1)

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


_CUSTOM_JAVA_SUBCLASS_BACKSTOPS = {}


@builtin
def build_new_style_java_class(module, ns, name, base):
    import polyglot
    import types

    JavaClass = __graalpython__.extend(base)
    if JavaClass not in _CUSTOM_JAVA_SUBCLASS_BACKSTOPS:
        class MroClass:
            def __getattr__(self, name):
                sentinel = object()
                # An attribute access on the Java instance failed, check the
                # delegate and then the static Java members
                result = getattr(self.this, name, sentinel)
                if result is sentinel:
                    return getattr(self.getClass().static, name)
                else:
                    return result

            def __setattr__(self, name, value):
                # An attribute access on the Java instance failed, use the delegate
                setattr(self.this, name, value)

            def __delattr__(self, name):
                # An attribute access on the Java instance failed, use the delegate
                delattr(self.this, name)

        # This may race, so we allow_method_overwrites, at the only danger to
        # insert a few useless classes into the MRO
        polyglot.register_interop_type(JavaClass, MroClass, allow_method_overwrites=True)

    # A class to make sure that the returned Python class can be used for
    # issubclass and isinstance checks with the Java instances
    class JavaSubclassMeta(type):
        @property
        def __bases__(self):
            return (JavaClass,)

        def __instancecheck__(cls, obj):
            return isinstance(obj, JavaClass)

        def __subclasscheck__(cls, derived):
            return cls is derived or issubclass(derived, JavaClass)

        def __new__(mcls, name, bases, namespace):
            if bases:
                new_class = None

                class custom_super():
                    def __init__(self, start_type=None, object_or_type=None):
                        assert start_type is None and object_or_type is None, "super() calls in Python class inheriting from Java must not receive arguments"
                        f = sys._getframe(1)
                        self.self = f.f_locals[f.f_code.co_varnames[0]]

                    def __getattribute__(self, name):
                        if name == "__class__":
                            return __class__
                        if name == "self":
                            return object.__getattribute__(self, "self")
                        for t in new_class.mro()[1:]:
                            if t == DelegateSuperclass:
                                break
                            if name in t.__dict__:
                                value = t.__dict__[name]
                                if get := getattr(value, "__get__", None):
                                    return get(self.self.this)
                                return value
                        return getattr(__graalpython__.super(self.self), name)

                # Wrap all methods so that the `self` inside is always a Java object, and
                # adapt the globals in the functions to provide a custom super() if
                # necessary
                def self_as_java_wrapper(k, value):
                    if type(value) is not types.FunctionType:
                        return value
                    if k in ("__new__", "__class_getitem__"):
                        return value
                    if "super" in value.__code__.co_names:
                        value = types.FunctionType(
                            value.__code__,
                            value.__globals__ | {"super": custom_super},
                            name=value.__name__,
                            argdefs=value.__defaults__,
                            closure=value.__closure__,
                        )
                    return lambda self, *args, **kwds: value(self.__this__, *args, **kwds)
                namespace = {k: self_as_java_wrapper(k, v) for k, v in namespace.items()}
                new_class = type.__new__(mcls, name, bases, namespace)
                return new_class
            return type.__new__(mcls, name, bases, namespace)

        def __getattr__(self, name):
            return getattr(JavaClass, name)

    # A class that defines the required construction for the Java instances, so
    # the Python code can actually override __new__ to affect the construction
    # of the Java object
    class DelegateSuperclass(metaclass=JavaSubclassMeta):
        def __new__(cls, *args, **kwds):
            delegate = object.__new__(cls)
            java_object = polyglot.__new__(JavaClass, *(args + (delegate,)))
            delegate.__this__ = java_object
            return java_object

    return type(name, (DelegateSuperclass,), ns)
