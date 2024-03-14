# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

from tests.cpyext import CPyExtType, CPyExtHeapType, assert_raises

NativeTypeWithoutDict = CPyExtType(
    name='NativeTypeWithoutDict',
)

NativeTypeWithDict = CPyExtType(
    name='NativeTypeWithDict',
    cmembers='PyObject* dict;',
    tp_dictoffset='offsetof(NativeTypeWithDictObject, dict)',
    tp_getset='{"__dict__", PyObject_GenericGetDict, PyObject_GenericSetDict}',
)

NativeHeapTypeWithoutDict = CPyExtHeapType(
    name='NativeTypeWithManagedDict',
)

NativeHeapTypeWithDict = CPyExtHeapType(
    name='NativeHeapTypeWithManagedDict',
    cmembers='PyObject* dict;',
    code='''
        static PyMemberDef memberlist[] = {
            {"__dictoffset__", T_PYSSIZET, offsetof(NativeHeapTypeWithManagedDictObject, dict), READONLY},
            {NULL}
        };
        static PyGetSetDef getsetlist[] = {
            {"__dict__", PyObject_GenericGetDict, PyObject_GenericSetDict},
            {NULL}
        };
    ''',
    slots=[
        '{Py_tp_members, memberlist}',
        '{Py_tp_getset, getsetlist}',
    ],
)


# TODO it would be great if we could test creating heap types with Py_TPFLAGS_MANAGED_DICT, because pybind11 does that,
# but there's no way to do that without abusing abusing implementations details


class NativeSubtypeWithDict(NativeTypeWithDict):
    pass


class NativeSubtypeWithAddedDict(NativeTypeWithoutDict):
    pass


class TestObjectDict:
    @staticmethod
    def assert_has_no_dict(obj):
        assert_raises(AttributeError, setattr, obj, 'foo', 1)
        assert_raises(AttributeError, getattr, obj, '__dict__')

    @staticmethod
    def assert_has_working_dict(obj):
        obj.foo = 1
        assert obj.foo == 1
        assert obj.__dict__ == {'foo': 1}

    def test_no_dict(self):
        self.assert_has_no_dict(NativeTypeWithoutDict())
        self.assert_has_no_dict(NativeHeapTypeWithoutDict())

    def test_dict(self):
        self.assert_has_working_dict(NativeTypeWithDict())
        self.assert_has_working_dict(NativeHeapTypeWithDict())
        self.assert_has_working_dict(NativeSubtypeWithDict())
        self.assert_has_working_dict(NativeSubtypeWithAddedDict())
