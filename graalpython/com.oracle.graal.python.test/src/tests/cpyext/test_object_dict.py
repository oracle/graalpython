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
