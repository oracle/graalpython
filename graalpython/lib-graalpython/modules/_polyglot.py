# Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import polyglot


def interop_type(foreign_class, allow_method_overwrites=False):
    """
    @interop_type(foreign_class, allow_method_overwrites=False)

    Registers the annotated python class for the given foreign_class.
    Every instance of foreign_class or its subclasses will be treated as an instance of pythonClass.

    Multiple registrations per foreign_class are allowed.
    If two registered classes for the same foreign_class define the same method, an error will be raised.
    If allow_method_overwrites=True, defining the same method is explicitly allowed.
    In case of method conflicts, the newest registered class "wins".

    Example registering a custom interop type for the java class YourClass

    >>> from polyglot import interop_type
    >>> import java

    >>> YourClass = java.type("fully.qualified.package.path.YourClass")

    >>> @polyglot.interop_type(YourClass)
    >>> class JYourClass:
    ...     def __str__(self):
    ...         return self.getClass().getSimpleName()

    >>> yourClassObject = YourClass()
    >>> print(yourClassObject)
    YourClass

    Per default registering classes with the same methods for one foreign_class raises an error.
    If you want to overwrite methods defined in JYourClass use "allow_method_overwrites=True":

    >>> @polyglot.interop_type(YourClass, allow_method_overwrites=True)
    >>> class NewJYourClass:
    ...     def __str__(self):
    ...         return self.getClass().getName()

    >>> print(yourClassObject)  # Note: yourClassObject is still the same instance
    fully.qualified.package.path.YourClass
    """

    def wrapper(python_class):
        polyglot.register_interop_type(foreign_class, python_class, allow_method_overwrites=allow_method_overwrites)
        # return python class to allow multiple @interop_type
        return python_class

    return wrapper


setattr(polyglot, "interop_type", interop_type)


def interop_behavior(receiver):
    if not isinstance(receiver, type):
        raise ValueError(f"The first argument must be a type, not {receiver}")

    def wrapper(python_class):
        if not isinstance(python_class, type):
            raise ValueError(f"The first argument must be a python class, not {python_class}")

        # extracting the function from the staticmethod with __func__
        messages = {key: value.__func__ for key, value in vars(python_class).items() if isinstance(value, staticmethod)}
        polyglot.register_interop_behavior(receiver, **messages)
        return python_class

    return wrapper


setattr(polyglot, "interop_behavior", interop_behavior)


class CriticalInterop():
    def __enter__(self):
        polyglot.__enter_foreign_critical_region__()

    def __exit__(self, *args):
        polyglot.__leave_foreign_critical_region__()


setattr(polyglot, "gil_locked_during_interop", CriticalInterop)


# loading arrow structures on demand
def __getattr__(name):
    if name == "arrow":
        import _polyglot_arrow
        setattr(polyglot, "arrow", _polyglot_arrow)
        return _polyglot_arrow
    raise AttributeError(f"module 'polyglot' has no attribute '{name}'")

setattr(polyglot, "__getattr__", __getattr__)
setattr(polyglot, "__path__", ".")
