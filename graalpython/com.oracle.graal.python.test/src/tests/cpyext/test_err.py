# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import warnings

from . import CPyExtTestCase, CPyExtFunction, CPyExtFunctionVoid, unhandled_error_compare, \
    CPyExtType, is_native_object

__dir__ = __file__.rpartition("/")[0]


def _reference_setstring(args):
    raise args[0](args[1])


def _normalize_exception(exc_type, value):
    if issubclass(exc_type, BaseException):
        if isinstance(value, exc_type):
            return value
        if value is None:
            return exc_type()
        if isinstance(value, tuple):
            return exc_type(*value)
        return exc_type(value)


def _reference_setobject(args):
    exc_type, value = args
    raise _normalize_exception(exc_type, value)


def _reference_restore(args):
    exc_type, value, tb = args
    exc = _normalize_exception(exc_type, value)
    if tb:
        exc.__traceback__ = tb
    raise exc


def compare_restore_result(x, y):
    return (
            isinstance(x, BaseException) and
            type(x) == type(y) and
            # Compare str because different exceptions are not equal
            str(x.args) == str(y.args) and
            (x.__traceback__ is example_traceback) == (y.__traceback__ is example_traceback)
    )


def _new_ex_result_check(x, y):
    name = y[0]
    base = y[1]
    return name in str(x) and issubclass(x, base)


def _new_ex_with_doc_result_check(x, y):
    name = y[0]
    doc = y[1]
    base = y[2]
    return name in str(x) and issubclass(x, base) and x.__doc__ == doc


def _reference_setnone(args):
    raise args[0]()


def _reference_format(args):
    raise args[0](args[1].lower() % args[2:])


def _reference_fetch(args):
    try:
        raise args[0]
    except:
        return sys.exc_info()[0]


def compare_tracebacks(tb1, tb2):
    while tb1 and tb2:
        if tb1.tb_frame.f_code != tb2.tb_frame.f_code:
            print(f"\ntb_next: {tb1.tb_frame.f_code} != {tb2.tb_frame.f_code}\n")
            return False
        tb1 = tb1.tb_next
        tb2 = tb2.tb_next
    return tb1 is None and tb2 is None


def compare_frame_f_back_chain(f1, f2):
    while f1 and f2:
        if f1.f_code != f2.f_code:
            print(f"\nframe: {f1.f_code} != {f2.f_code}\n")
            return False
        f1 = f1.f_back
        f2 = f2.f_back
    return f1 is None and f2 is None


def _reference_fetch_tb_from_python(args):
    try:
        args[0]()
    except:
        tb = sys.exc_info()[2]
        return tb.tb_next  # PyErr_Fetch doesn't contain the current frame


def _reference_fetch_tb_f_back(args):
    try:
        args[0]()
    except:
        return sys.exc_info()[2].tb_frame.f_back


def _raise_exception():
    def inner():
        raise OSError

    def reraise(e):
        raise e

    try:
        inner()
    except Exception as e:
        reraise(e)


def _is_exception_class(exc):
    return isinstance(exc, type) and issubclass(exc, BaseException)


def _reference_givenexceptionmatches(args):
    err = args[0]
    exc = args[1]
    if isinstance(exc, tuple):
        for e in exc:
            if _reference_givenexceptionmatches((err, e)):
                return 1
        return 0
    if isinstance(err, BaseException):
        err = type(err)
    if _is_exception_class(err) and _is_exception_class(exc):
        return issubclass(err, exc)
    return exc is err


def _reference_nomemory(args):
    raise MemoryError


class Dummy:
    pass


def raise_erorr():
    raise NameError


try:
    raise_erorr()
except NameError as e:
    example_traceback = e.__traceback__
else:
    assert False

assert example_traceback

ExceptionSubclass = CPyExtType(
    "ExceptionSubclass",
    '',
    struct_base='PyBaseExceptionObject base;',
    tp_new='0',
    tp_alloc='0',
    tp_free='0',
    ready_code='ExceptionSubclassType.tp_base = (PyTypeObject*)PyExc_Exception;'
)


class TestPyErr(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super(TestPyErr, self).compile_module(name)

    test_PyErr_SetString = CPyExtFunctionVoid(
        _reference_setstring,
        lambda: (
            (ValueError, "hello"),
            (TypeError, "world"),
            (KeyError, "key"),
            (ExceptionSubclass, "hello")
        ),
        resultspec="O",
        argspec='Os',
        arguments=["PyObject* v", "char* msg"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_NewException = CPyExtFunction(
        lambda args: args,
        lambda: (
            ("main.TestException", TypeError, {}),
        ),
        resultspec="O",
        argspec='sOO',
        arguments=["char* name", "PyObject* base", "PyObject* dict"],
        cmpfunc=_new_ex_result_check
    )

    test_PyErr_NewExceptionWithDoc = CPyExtFunction(
        lambda args: args,
        lambda: (
            ("main.TestException", "new exception doc", TypeError, {}),
        ),
        resultspec="O",
        argspec='ssOO',
        arguments=["char* name", "char* doc", "PyObject* base", "PyObject* dict"],
        cmpfunc=_new_ex_with_doc_result_check
    )

    test_PyErr_SetObject = CPyExtFunctionVoid(
        _reference_setobject,
        lambda: (
            (RuntimeError, None),
            (RuntimeError, RuntimeError("error")),
            (ValueError, "hello"),
            (TypeError, "world"),
            (KeyError, "key"),
            (RuntimeError, ValueError()),
            (OSError, (2, "error")),
            (ExceptionSubclass, None),
            (ExceptionSubclass, "hello"),
            (ExceptionSubclass, (1, 2)),
        ),
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* v", "PyObject* msg"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_SetNone = CPyExtFunctionVoid(
        _reference_setnone,
        lambda: (
            (ValueError,),
            (TypeError,),
            (KeyError,),
            (ExceptionSubclass,),
        ),
        resultspec="O",
        argspec='O',
        arguments=["PyObject* v"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Format = CPyExtFunctionVoid(
        _reference_format,
        lambda: (
            (ValueError, "hello %S %S", "beautiful", "world"),
            (TypeError, "world %S %S", "", ""),
            (KeyError, "key %S %S", "", ""),
            (KeyError, "unknown key: %S %S", "some_key", ""),
            (ExceptionSubclass, "hello %S %S", "beautiful", "world"),
        ),
        resultspec="O",
        argspec='OsOO',
        arguments=["PyObject* v", "char* msg", "PyObject* arg0", "PyObject* arg1"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Format_dS = CPyExtFunctionVoid(
        _reference_format,
        lambda: (
            (ValueError, "hello %d times %S", 10, "world"),
            (ValueError, "hello %c times %R", 95, "world"),
        ),
        resultspec="O",
        argspec='OsiO',
        arguments=["PyObject* v", "char* msg", "int arg0", "PyObject* arg1"],
        resultval="NULL",
        callfunction="PyErr_Format",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_PrintEx = CPyExtFunction(
        lambda args: None,
        lambda: (
            (True,),
        ),
        code="""PyObject* wrap_PyErr_PrintEx(int n) {
             PyErr_SetString(PyExc_KeyError, "unknown key whatsoever");
             PyErr_PrintEx(n);
             return Py_None;
         }
         """,
        resultspec="O",
        argspec='i',
        arguments=["int n"],
        callfunction="wrap_PyErr_PrintEx",
        stderr_validator=lambda args, stderr: 'unknown key whatsoever' in stderr and 'Traceback' not in stderr,
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_GivenExceptionMatches = CPyExtFunction(
        _reference_givenexceptionmatches,
        lambda: (
            (ValueError, ValueError),
            (ValueError, BaseException),
            (ValueError, KeyError),
            (ValueError, (KeyError, SystemError, OverflowError)),
            (ValueError, (KeyError, SystemError, ValueError)),
            (ValueError, Dummy),
            (ValueError, Dummy()),
            (ValueError, ExceptionSubclass),
            (ExceptionSubclass, ExceptionSubclass),
            (ExceptionSubclass, Exception),
            (ExceptionSubclass, ValueError),
        ),
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* err", "PyObject* exc"],
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_GivenExceptionMatchesNative = CPyExtFunction(
        lambda args: args[2],
        lambda: (
            # ValueError = 0
            # KeyError = 1
            (ValueError, 0, True),
            (ValueError, 1, False),
            (KeyError, 0, False),
            (KeyError, 1, True),
            (Dummy, 0, False),
            (Dummy, 1, False),
        ),
        code="""int PyErr_GivenExceptionMatchesNative(PyObject* exc, int selector, int unused) {
            switch(selector) {
            case 0:
                return exc == PyExc_ValueError;
            case 1:
                return exc == PyExc_KeyError;
            }
            return 0;
        }
        """,
        resultspec="i",
        argspec='Oii',
        arguments=["PyObject* exc", "int selector", "int unused"],
        callfunction="PyErr_GivenExceptionMatchesNative",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Occurred = CPyExtFunction(
        lambda args: args[0] if _is_exception_class(args[0]) else SystemError,
        lambda: (
            (ValueError, "hello"),
            (KeyError, "world"),
            (ValueError, Dummy()),
            (Dummy, ""),
        ),
        code="""PyObject* wrap_PyErr_Occurred(PyObject* exc, PyObject* msg) {
            PyObject* result;
            PyErr_SetObject(exc, msg);
            result = PyErr_Occurred();
            PyErr_Clear();
            return result;
        }
        """,
        resultspec="O",
        argspec='OO',
        arguments=["PyObject* err", "PyObject* msg"],
        callfunction="wrap_PyErr_Occurred",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_ExceptionMatches = CPyExtFunction(
        _reference_givenexceptionmatches,
        lambda: (
            (ValueError, ValueError),
            (ValueError, BaseException),
            (ValueError, KeyError),
            (ValueError, (KeyError, SystemError, OverflowError)),
            (ValueError, (KeyError, SystemError, ValueError)),
            (ValueError, Dummy),
            (ValueError, Dummy()),
            (ValueError, ExceptionSubclass),
            (ExceptionSubclass, ExceptionSubclass),
            (ExceptionSubclass, Exception),
            (ExceptionSubclass, ValueError),

        ),
        code="""int wrap_PyErr_ExceptionMatches(PyObject* err, PyObject* exc) {
            int res;
            PyErr_SetNone(err);
            res = PyErr_ExceptionMatches(exc);
            PyErr_Clear();
            return res;
        }
        """,
        resultspec="i",
        argspec='OO',
        arguments=["PyObject* err", "PyObject* exc"],
        callfunction="wrap_PyErr_ExceptionMatches",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WarnEx = CPyExtFunctionVoid(
        lambda args: warnings.warn(args[1], args[0], args[2]),
        lambda: (
            (UserWarning, "custom warning", 1),
        ),
        resultspec="O",
        argspec='Osn',
        arguments=["PyObject* category", "char* msg", "Py_ssize_t level"],
        stderr_validator=lambda args, stderr: "UserWarning: custom warning" in stderr,
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WarnExplicitObject = CPyExtFunctionVoid(
        lambda args: warnings.warn_explicit(args[1], args[0], args[2], args[3], args[4]),
        lambda: (
            (UserWarning, "custom warning", "filename.py", 1, "module", None),
        ),
        resultspec="O",
        argspec='OOOiOO',
        arguments=["PyObject* category", "PyObject* text", "PyObject* filename_str", "int lineno",
                   "PyObject* module_str", "PyObject* registry"],
        stderr_validator=lambda args, stderr: "UserWarning: custom warning" in stderr,
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WarnExplicit = CPyExtFunctionVoid(
        lambda args: warnings.warn_explicit(args[1], args[0], args[2], args[3], args[4]),
        lambda: (
            (UserWarning, "custom warning", "filename.py", 1, "module", None),
        ),
        resultspec="O",
        argspec='OssisO',
        arguments=["PyObject* category", "const char* text", "const char* filename_str", "int lineno",
                   "const char* module_str", "PyObject* registry"],
        stderr_validator=lambda args, stderr: "UserWarning: custom warning" in stderr,
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_NoMemory = CPyExtFunctionVoid(
        _reference_nomemory,
        lambda: (
            tuple(),
        ),
        resultspec="O",
        argspec='',
        argumentnames="",
        arguments=["PyObject* dummy"],
        resultval="NULL",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WriteUnraisable = CPyExtFunctionVoid(
        lambda args: None,
        lambda: (
            (None,),
            ("hello",),
        ),
        argspec='O',
        arguments=["PyObject* obj"],
        code="""void wrap_PyErr_WriteUnraisable(PyObject* object) {
            PyErr_SetString(PyExc_RuntimeError, "unraisable_exception");
            if (object == Py_None)
                object = NULL;
            PyErr_WriteUnraisable(object);
        }""",
        callfunction="wrap_PyErr_WriteUnraisable",
        stderr_validator=lambda args, stderr: "RuntimeError: unraisable_exception" in stderr,
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_WriteUnraisableMsg = CPyExtFunctionVoid(
        lambda args: None,
        lambda: (
            (None,),
            ("hello",),
        ),
        code="""void wrap_PyErr_WriteUnraisableMsg(PyObject* object) {
                PyErr_SetString(PyExc_RuntimeError, "unraisable_exception");
                if (object == Py_None)
                    object = NULL;
                _PyErr_WriteUnraisableMsg("in my function", object);
             }
             """,
        argspec='O',
        arguments=["PyObject* obj"],
        callfunction="wrap_PyErr_WriteUnraisableMsg",
        stderr_validator=lambda args,
                                stderr: "RuntimeError: unraisable_exception" in stderr and "Exception ignored in my function:" in stderr,
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Restore = CPyExtFunctionVoid(
        _reference_restore,
        lambda: (
            (RuntimeError, None, None),
            (RuntimeError, RuntimeError("error"), None),
            (ValueError, "hello", None),
            (TypeError, "world", None),
            (KeyError, "key", None),
            (RuntimeError, ValueError(), None),
            (OSError, (2, "error"), None),
            (NameError, None, example_traceback),
            (ExceptionSubclass, "error", None),
        ),
        # Note on CPython all the exception creation happens not in PyErr_Restore, but when leaving the function and
        # normalizing the exception in the caller. So this really test both of these mechanisms together.
        code="""PyObject* wrap_PyErr_Restore(PyObject* typ, PyObject* val, PyObject* tb) {
            if (typ == Py_None) typ = NULL;
            if (val == Py_None) val = NULL;
            if (tb == Py_None) tb = NULL;
            Py_XINCREF(typ);
            Py_XINCREF(val);
            Py_XINCREF(tb);
            PyErr_Restore(typ, val, tb);
            return NULL;
        }
        """,
        resultspec="O",
        argspec='OOO',
        arguments=["PyObject* typ", "PyObject* val", "PyObject* tb"],
        resultval="NULL",
        callfunction="wrap_PyErr_Restore",
        cmpfunc=compare_restore_result
    )

    test_PyErr_Fetch = CPyExtFunction(
        _reference_fetch,
        lambda: (
            (ValueError,),
            (TypeError,),
            (KeyError,),
        ),
        code="""PyObject* wrap_PyErr_Fetch(PyObject* exception_type) {
            PyObject* typ = NULL;
            PyObject* val = NULL;
            PyObject* tb = NULL;
            PyErr_SetNone(exception_type);
            PyErr_Fetch(&typ, &val, &tb);
            return typ;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* exception_type"],
        callfunction="wrap_PyErr_Fetch",
        cmpfunc=unhandled_error_compare
    )

    test_PyErr_Fetch_tb_from_c = CPyExtFunctionVoid(
        lambda args: None,
        lambda: [(1,)],
        code="""PyObject* wrap_PyErr_Fetch_tb_from_c() {
            PyErr_SetString(PyExc_ArithmeticError, "test");
            PyObject* typ = NULL;
            PyObject* val = NULL;
            PyObject* tb = NULL;
            PyErr_Fetch(&typ, &val, &tb);
            return tb == NULL? Py_None: tb;
        }
        """,
        resultspec="O",
        arguments=[],
        callfunction="wrap_PyErr_Fetch_tb_from_c",
        cmpfunc=compare_tracebacks,
    )

    test_PyErr_Fetch_tb_from_python = CPyExtFunction(
        _reference_fetch_tb_from_python,
        lambda: (
            (lambda: 1 / 0,),
            (_raise_exception,),
        ),
        code="""PyObject* wrap_PyErr_Fetch_tb_from_python(PyObject* fn) {
            PyObject_CallFunction(fn, NULL);
            PyObject* typ = NULL;
            PyObject* val = NULL;
            PyObject* tb = NULL;
            PyErr_Fetch(&typ, &val, &tb);
            return tb;
        }
        """,
        resultspec="O",
        argspec='O',
        arguments=["PyObject* fn"],
        callfunction="wrap_PyErr_Fetch_tb_from_python",
        cmpfunc=compare_tracebacks,
    )

    # GR-22089
    # test_PyErr_Fetch_tb_f_back = CPyExtFunction(
    #     _reference_fetch_tb_f_back,
    #     lambda: (
    #         (lambda: 1 / 0,),
    #         (_raise_exception,),
    #     ),
    #     code="""PyObject* wrap_PyErr_Fetch_tb_f_back(PyObject* fn) {
    #         PyObject_CallFunction(fn, NULL);
    #         PyObject* typ = NULL;
    #         PyObject* val = NULL;
    #         PyObject* tb = NULL;
    #         PyErr_Fetch(&typ, &val, &tb);
    #         return PyObject_GetAttrString(PyObject_GetAttrString(tb, "tb_frame"), "f_back");
    #     }
    #     """,
    #     resultspec="O",
    #     argspec='O',
    #     arguments=["PyObject* fn"],
    #     callfunction="wrap_PyErr_Fetch_tb_f_back",
    #     cmpfunc=compare_frame_f_back_chain,
    # )


def raise_native_exception():
    raise ExceptionSubclass(1)


class TestNativeExceptionSubclass:
    def test_init(self):
        e = ExceptionSubclass(1, 2, 3)
        assert is_native_object(e)
        assert type(e) == ExceptionSubclass
        assert isinstance(e, Exception)

    def test_managed_subtype(self):
        class ManagedSubclass(ExceptionSubclass):
            pass

        assert is_native_object(ManagedSubclass())

    def test_raise_type(self):
        try:
            raise ExceptionSubclass
        except ExceptionSubclass as e:
            assert is_native_object(e)
            assert e.args == ()
        else:
            assert False

    def test_raise_instance(self):
        try:
            raise ExceptionSubclass(1)
        except ExceptionSubclass as e:
            assert is_native_object(e)
            assert e.args == (1,)
        else:
            assert False

    def test_traceback(self):
        try:
            raise_native_exception()
        except ExceptionSubclass as e:
            tb = e.__traceback__
        else:
            assert False
        assert tb
        assert tb.tb_frame.f_code is TestNativeExceptionSubclass.test_traceback.__code__
        assert tb.tb_next
        assert tb.tb_next.tb_frame.f_code is raise_native_exception.__code__
        assert tb.tb_next.tb_lineno == raise_native_exception.__code__.co_firstlineno + 1
        e2 = ExceptionSubclass()
        assert e2.__traceback__ is None
        e2.__traceback__ = tb
        assert e2.__traceback__ is tb
        e2 = ExceptionSubclass()
        e2 = e2.with_traceback(tb)
        assert e2.__traceback__ is tb

    def test_traceback_reraise(self):
        try:
            try:
                raise_native_exception()
            except Exception as e1:
                e1.__traceback__ = None
                raise
        except ExceptionSubclass as e:
            tb = e.__traceback__
        else:
            assert False
        assert tb
        assert tb.tb_frame.f_code is TestNativeExceptionSubclass.test_traceback_reraise.__code__
        assert tb.tb_next
        assert tb.tb_next.tb_frame.f_code is raise_native_exception.__code__

    def test_chaining(self):
        inner_e = ExceptionSubclass()
        outer_e = ExceptionSubclass()
        try:
            try:
                raise inner_e
            except Exception:
                raise outer_e
        except Exception:
            pass
        assert outer_e.__context__ is inner_e
        assert outer_e.__suppress_context__ is False

    def test_raise_from(self):
        inner_e = ExceptionSubclass()
        outer_e = ExceptionSubclass()
        try:
            raise outer_e from inner_e
        except Exception:
            pass
        assert outer_e.__cause__ is inner_e
        assert outer_e.__suppress_context__ is True

    def test_cause(self):
        e = ExceptionSubclass()
        e2 = ExceptionSubclass()
        assert e.__suppress_context__ is False
        e.__cause__ = e2
        assert e.__cause__ is e2
        assert e.__suppress_context__ is True
        e.__suppress_context__ = False
        assert e.__suppress_context__ is False

    def test_context(self):
        e = ExceptionSubclass()
        e2 = ExceptionSubclass()
        e.__context__ = e2
        assert e.__context__ is e2

    def test_args(self):
        e = ExceptionSubclass(1, 2, 3)
        assert e.args == (1, 2, 3)
        e.args = ("foo",)
        assert e.args == ("foo",)

    def test_dict(self):
        e = ExceptionSubclass()
        e.asdf = 1
        assert e.asdf == 1
        assert e.__dict__ == {'asdf': 1}
        e.__dict__ = {'foo': 'bar'}
        assert e.__dict__ == {'foo': 'bar'}

    def test_reduce(self):
        e = ExceptionSubclass(1)
        e.asdf = 1
        assert e.__reduce__() == (ExceptionSubclass, (1,), {'asdf': 1})

    def test_repr_str(self):
        assert repr(ExceptionSubclass()) == "ExceptionSubclass()"
        assert repr(ExceptionSubclass(1)) == "ExceptionSubclass(1)"
        assert repr(ExceptionSubclass(1, 2)) == "ExceptionSubclass(1, 2)"
        assert str(ExceptionSubclass()) == ""
        assert str(ExceptionSubclass(1)) == "1"
        assert str(ExceptionSubclass(1, 2)) == "(1, 2)"

    def test_setstate(self):
        e = ExceptionSubclass()
        e.__setstate__({'foo': 'bar'})
        assert e.foo == 'bar'

    def test_throw(self):
        def gen():
            try:
                yield
            except Exception as e:
                yield e

        g = gen()
        next(g)
        e = g.throw(ExceptionSubclass)
        assert is_native_object(e)
        assert type(e) == ExceptionSubclass

        g = gen()
        next(g)
        e = ExceptionSubclass()
        e1 = g.throw(e)
        assert e1 is e
