# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

# __all__ = ['Struct', '_clearcache', 'calcsize', 'error', 'iter_unpack', 'pack', 'pack_into', 'unpack', 'unpack_from']


@__graalpython__.builtin
def _clearcache(*args):
    import _cpython__struct
    return _cpython__struct.clearcache(*args)


@__graalpython__.builtin
def calcsize(fmt):
    import _cpython__struct
    return _cpython__struct.calcsize(fmt)


@__graalpython__.builtin
def iter_unpack(fmt, buffer):
    import _cpython__struct
    return _cpython__struct.calcsize(fmt, buffer)


@__graalpython__.builtin
def pack(fmt, *vals):
    import _cpython__struct
    return _cpython__struct.pack(fmt, *vals)


@__graalpython__.builtin
def pack_into(fmt, buffer, offset, *vals):
    import _cpython__struct
    return _cpython__struct.pack_into(fmt, buffer, offset, *vals)


@__graalpython__.builtin
def unpack(fmt, *vals):
    import _cpython__struct
    return _cpython__struct.unpack(fmt, *vals)


@__graalpython__.builtin
def unpack_from(fmt, buffer, offset=0):
    import _cpython__struct
    return _cpython__struct.unpack_from(fmt, buffer, offset=offset)


# error and Struct
def __getattr__(name):
    if name in ['error', 'Struct']:
        import _cpython__struct
        return __getattr__(_cpython__struct, name)
    raise AttributeError("module {} has no attribute {}".format(__name__, name))


import sys
_struct = sys.modules.get("_struct", None)
if not _struct:
    _struct = type(sys)("_struct")
    sys.modules["_struct"] = _struct
new_globals = dict(**globals())
new_globals.update(**_struct.__dict__)
_struct.__dict__.update(**new_globals)
