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

def a_function(): pass
codetype = type(a_function.__code__)


def co_repr(self):
    return '<code object %s, file "%s", line %d>' % (self.co_name, self.co_filename, self.co_firstlineno)


def co_replace(self, **kwargs):
    import types
    return types.CodeType(
        kwargs.get('co_argcount', self.co_argcount),
        kwargs.get('co_posonlyargcount', self.co_posonlyargcount),
        kwargs.get('co_kwonlyargcount', self.co_kwonlyargcount),
        kwargs.get('co_nlocals', self.co_nlocals),
        kwargs.get('co_stacksize', self.co_stacksize),
        kwargs.get('co_flags', self.co_flags),
        kwargs.get('co_code', self.co_code),
        kwargs.get('co_consts', self.co_consts),
        kwargs.get('co_names', self.co_names),
        kwargs.get('co_varnames', self.co_varnames),
        kwargs.get('co_filename', self.co_filename),
        kwargs.get('co_name', self.co_name),
        kwargs.get('co_firstlineno', self.co_firstlineno),
        kwargs.get('co_lnotab', self.co_lnotab),
        kwargs.get('co_freevars', self.co_freevars),
        kwargs.get('co_cellvars', self.co_cellvars),
    )


codetype.__repr__ = co_repr
codetype.replace = co_replace
