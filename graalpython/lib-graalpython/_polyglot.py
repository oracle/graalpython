# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import datetime
import polyglot
import time


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


def _date_time_tz(dt: datetime.datetime):
    if dt.tzinfo is not None:
        utcoffset = dt.tzinfo.utcoffset(dt)
        return utcoffset.days * 3600 * 24 + utcoffset.seconds
    raise polyglot.UnsupportedMessage


def _struct_time_tz(st: time.struct_time):
    if st.tm_gmtoff is not None:
        return st.tm_gmtoff
    return st.tm_zone


polyglot.register_interop_behavior(datetime.time,
                                   is_time=True, as_time=lambda d: (d.hour, d.minute, d.second, d.microsecond),
                                   is_time_zone=lambda t: t.tzinfo is not None, as_time_zone=_date_time_tz)

polyglot.register_interop_behavior(datetime.date,
                                   is_date=True, as_date=lambda d: (d.year, d.month, d.day))

polyglot.register_interop_behavior(datetime.datetime,
                                   is_date=True, as_date=lambda d: (d.year, d.month, d.day),
                                   is_time=True, as_time=lambda d: (d.hour, d.minute, d.second, d.microsecond),
                                   is_time_zone=lambda t: t.tzinfo is not None, as_time_zone=_date_time_tz)

polyglot.register_interop_behavior(time.struct_time,
                                   is_date=True, as_date=lambda t: (t.tm_year, t.tm_mon, t.tm_mday),
                                   is_time=True, as_time=lambda t: (t.tm_hour, t.tm_min, t.tm_sec, 0),
                                   is_time_zone=lambda t: t.tm_zone is not None or t.tm_gmtoff is not None,
                                   as_time_zone=_struct_time_tz)

# loading arrow structures on demand
def __getattr__(name):
    if name == "arrow":
        from modules import _polyglot_arrow
        setattr(polyglot, "arrow", _polyglot_arrow)
        return _polyglot_arrow
    raise AttributeError(f"module 'polyglot' has no attribute '{name}'")

setattr(polyglot, "__getattr__", __getattr__)

# example extending time.struct_time using the decorator wrapper
#
# @polyglot.interop_behavior(time.struct_time)
# class StructTimeInteropBehavior:
#     @staticmethod
#     def is_date(t):
#         return True
#
#     @staticmethod
#     def as_date(t):
#         return t.tm_year, t.tm_mon, t.tm_mday
#
#     @staticmethod
#     def is_time(t):
#         return True
#
#     @staticmethod
#     def as_time(t):
#         return t.tm_hour, t.tm_min, t.tm_sec, 0
#
#     @staticmethod
#     def is_time_zone(t):
#         return t.tm_zone is not None or t.tm_gmtoff is not None
#
#     @staticmethod
#     def as_time_zone(t):
#         if t.tm_gmtoff is not None:
#             return t.tm_gmtoff
#         return t.tm_zone
