# Copyright (c) 2018, Oracle and/or its affiliates.
#
# All rights reserved.
import array
import sys
import _io
from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare, GRAALPYTHON
__dir__ = __file__.rpartition("/")[0]


class TestPyObject(CPyExtTestCase):
    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyObject, self).compile_module(name)

    test_Py_TYPE = CPyExtFunction(
        type,
        lambda: ([], 12, sys.modules)
    )

    # Below are the PyObject_* identifiers that we know are used in numpy

    def is_buffer(x):
        __breakpoint__()
        if (isinstance(x, bytes) or isinstance(x, bytearray) or isinstance(x, array.array)):
            return 1
        else:
            return 0

    # test_PyObject_CheckBuffer = CPyExtFunction(
    #     is_buffer,
    #     (b"abc", bytearray(b"abc")),
    #     resultspec="i",
    # )
    # PyObject_Del
    # PyObject_FREE
    # PyObject_Free
    def forgiving_len(o):
        try:
            return len(o)
        except TypeError:
            return -1

    test_PyObject_Length = CPyExtFunction(
        forgiving_len,
        lambda: ([], [1,2,3,4], (1,), sys.modules),
        resultspec="i",
    )
    test_PyObject_Size = CPyExtFunction(
        forgiving_len,
        lambda: ([], [1,2,3,4], (1,), sys.modules),
        resultspec="i",
    )
    # PyObject_MALLOC
    # PyObject_Malloc
    # PyObject_New
    # PyObject_REALLOC
    # PyObject_Realloc
    test_PyObject_TypeCheck = CPyExtFunction(
        lambda args: 1 if isinstance(*args) else 0,
        lambda: (
            (1, int),
        ),
        argspec="OO",
        arguments=["PyObject* op", "PyTypeObject* type"],
        resultspec="i",
    )
    __PyObject_Call_ARGS = (
            ( len, ((1,2,3),), {} ),
            ( sum, ((0,1,2),), {} ),
            ( format, (object(),), {"format_spec": ""} ),
        )

    test_PyObject_Call = CPyExtFunction(
        lambda args: args[0](*args[1], **args[2]),
        lambda: TestPyObject.__PyObject_Call_ARGS,
        arguments=["PyObject* callable", "PyObject* callargs", "PyObject* kwargs"],
        argspec="OOO",
    )
    test_PyObject_CallObject = CPyExtFunction(
        lambda args: args[0](*args[1]),
        lambda: (
            ( len, ((1,2,3),) ),
            ( sum, ((0,1,2),) ),
        ),
        arguments=["PyObject* callable", "PyObject* callargs"],
        argspec="OO",
    )
    test_PyObject_CallFunction = CPyExtFunction(
        lambda args: args[0](args[2], args[3]),
        lambda: (
            ( sum, "Oi", [], 10 ),
        ),
        arguments=["PyObject* callable", "const char* fmt", "PyObject* list", "int initial"],
        argspec="OsOi",
    )

    class MyObject():
        def foo(self, *args, **kwargs):
            return sum(*args, **kwargs)

        def __hash__(self):
            return 42
        
    __MyObject_SINGLETON = MyObject()

    test_PyObject_CallMethod = CPyExtFunction(
        lambda args: getattr(args[0], args[1])(args[3], args[4]),
        lambda: (
            ( TestPyObject.MyObject(), "foo", "Oi", [], 10 ),
        ),
        arguments=["PyObject* rcvr", "const char* method", "const char* fmt", "PyObject* list", "int initial"],
        argspec="OssOi",
    )
    test_PyObject_Type = CPyExtFunction(
        type,
        lambda: (
            1, 0, [], {}, CPyExtFunction,
        )
    )
    test_PyObject_GetItem = CPyExtFunction(
        lambda args: args[0][args[1]],
        lambda: (
            ( [1, 2, 3], 1 ),
            # ( {"a": 42}, "b" ),
        ),
        arguments=["PyObject* primary", "PyObject* item"],
        argspec="OO",
    )

    def forgiving_set_item(args):
        try:
            args[0][args[1]] = args[2]
            return 0
        except:
            if sys.version_info.minor >= 6:
                raise SystemError
            else:
                return -1

    test_PyObject_SetItem = CPyExtFunction(
        forgiving_set_item,
        lambda: (
            ( [1, 2, 3], 1, 12 ),
            ( {"a": 32}, "b", 42 ),
            ( (1,2), 0, 1 ),
            ( [], 0, 1 ),
            ( [], "a", 1 ),
            ( {}, CPyExtFunction(1,2), "hello" ),
        ),
        arguments=["PyObject* primary", "PyObject* key", "PyObject* value"],
        argspec="OOO",
        resultspec="i",
        cmpfunc=unhandled_error_compare
    )
    # PyObject_AsReadBuffer
    # PyObject_AsWriteBuffer
    # PyObject_GetBuffer
    test_PyObject_Format = CPyExtFunction(
        lambda args: args[0].__format__(args[1]),
        lambda: (
            ( [], "" ),
            ( {}, "" ),
            ( 1, "" ),
        ),
        arguments=["PyObject* object", "PyObject* format_spec"],
        argspec="OO",
    )
    test_PyObject_GetIter = CPyExtFunction(
        iter,
        lambda: ([], {}, (0,)),
        cmpfunc=(lambda x,y: type(x) == type(y))
    )
    test_PyObject_IsInstance = CPyExtFunction(
        lambda args: 1 if isinstance(*args) else 0,
        lambda: (
            ( [1,2], list ),
            ( 1, int ),
            ( 1, list ),
        ),
        arguments=["PyObject* object", "PyObject* type"],
        argspec="OO",
        resultspec="i",
    )
    __PyObject_AsFileDescriptor_FD0 = open(1, buffering=0, mode="wb")
    __PyObject_AsFileDescriptor_FD1 = open("%s/As_FileDescriptor_Testfile" % __dir__, buffering=0, mode="wb")
    test_PyObject_AsFileDescriptor = CPyExtFunction(
        lambda arg: arg if isinstance(arg, int) else arg.fileno(),
        lambda: (
            1,
            TestPyObject.__PyObject_AsFileDescriptor_FD0,
            TestPyObject.__PyObject_AsFileDescriptor_FD1,
        ),
        resultspec="i",
    )
    test_PyObject_Print = CPyExtFunction(
        lambda args: 0,
        lambda: ([], 1, "a"),
        arguments=["PyObject* object", "FILE* fp=fdopen(1,\"w\")", "int flags=0"],
        resultspec="i",
    )
    test_PyObject_Repr = CPyExtFunction(
        repr,
        lambda: ([], {}, 0, 123, b"ello")
    )
    test_PyObject_Str = CPyExtFunction(
        repr,
        lambda: ([], {}, 0, 123, b"ello")
    )

    # richcompare_args = ([ ([], [], i) for i in range(6) ] +
    #                     [ (12, 24, i) for i in range(6) ] +
    #                     [ ("aa", "ba", i) for i in range(6) ])

    # def richcompare(args):
    #     return eval("%r %s %r" % (args[0], ["<", "<=", "==", "!=", ">", ">="][args[2]], args[1]))

    # test_PyObject_RichCompare = CPyExtFunction(
    #     richcompare,
    #     richcompare_args,
    #     arguments=["PyObject* left", "PyObject* right", "int op"],
    #     argspec="OOi",
    # )

    # def richcompare_bool(args):
    #     try:
    #         if eval("%r %s %r" % (args[0], ["<", "<=", "==", "!=", ">", ">="][args[2]], args[1])):
    #             return 1
    #         else:
    #             return 0
    #     except:
    #         return -1

    # test_PyObject_RichCompareBool = CPyExtFunction(
    #     richcompare_bool,
    #     richcompare_args,
    #     arguments=["PyObject* left", "PyObject* right", "int op"],
    #     argspec="OOi",
    #     resultspec="i",
    # )
    __PyObject_GetAttrString_ARGS = (
            ( MyObject(), "foo" ),
            ( [], "__len__" ),
        )
    test_PyObject_GetAttrString = CPyExtFunction(
        lambda args: getattr(*args),
        lambda: TestPyObject.__PyObject_GetAttrString_ARGS,
        arguments=["PyObject* object", "const char* attr"],
        argspec="Os",
    )

    def setattrstring(args):
        try:
            setattr(*args)
            return 0
        except:
            if sys.version_info.minor >= 6:
                raise SystemError
            else:
                return -1

    test_PyObject_SetAttrString = CPyExtFunction(
        setattrstring,
        lambda: (
            ( TestPyObject.MyObject, "x", 42 ),
            ( [], "x", 42 ),
        ),
        arguments=["PyObject* object", "const char* attr", "PyObject* value"],
        argspec="OsO",
        resultspec="i",
        cmpfunc=unhandled_error_compare
    )
    test_PyObject_HasAttrString = CPyExtFunction(
        lambda args: 1 if hasattr(*args) else 0,
        lambda: (
            ( TestPyObject.MyObject, "foo" ),
            ( [], "__len__" ),
            ( [], "foobar" ),
        ),
        arguments=["PyObject* object", "const char* attr"],
        argspec="Os",
        resultspec="i",
    )
    __PyObject_GetAttr_ARGS = (
            ( MyObject(), "foo" ),
            ( [], "__len__" ),
        )
    test_PyObject_GetAttr = CPyExtFunction(
        lambda args: getattr(*args),
        lambda: TestPyObject.__PyObject_GetAttr_ARGS,
        arguments=["PyObject* object", "PyObject* attr"],
        argspec="OO",
    )
    test_PyObject_SelfIter = CPyExtFunction(
        lambda x: x,
        lambda: ([], 1, 42, "abc", {}, type),
    )
    test_PyObject_GenericGetAttr = test_PyObject_GetAttr
    test_PyObject_Hash = CPyExtFunction(
        lambda arg: hash(arg),
        lambda: (42, TestPyObject.__MyObject_SINGLETON),
        resultspec="i"
    )
    # test_PyObject_HashNotImplemented = CPyExtFunction(
    #     SystemError,
    #     (42, MyObject),
    #     resultspec="i",
    #     cmpfunc=lambda x,y: type(x)==type(y) if (isinstance(x,BaseException) and isinstance(y,BaseException)) else x==y
    # )
    test_PyObject_IsTrue = CPyExtFunction(
        lambda arg: 1 if bool(arg) else 0,
        lambda: (1, 0, -1, {}, [], [1]),
        resultspec="i",
    )
    test_PyObject_Not = CPyExtFunction(
        lambda arg: 1 if not(bool(arg)) else 0,
        lambda: (1, 0, -1, {}, [], [1], (1,)),
        resultspec="i",
    )
    # PyObject_ClearWeakRefs
    # test_PyObject_New = CPyExtFunction(
    # )
    # test_PyObject_Init = CPyExtFunction(
    # )
