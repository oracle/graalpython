import sys

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare


class TestMisc(CPyExtTestCase):

    def compile_module(self, name):
        type(self).mro()[1].__dict__["test_%s" % name].create_module(name)
        super().compile_module(name)

    test_PyFrame_GetCode = CPyExtFunction(
        lambda args: args[0].f_code,
        lambda: (
            (sys._getframe(0),),
        ),
        code='''PyObject* wrap_PyFrame_GetCode(PyObject* frame) {
            return (PyObject*)PyFrame_GetCode((PyFrameObject*)frame);
        }
        ''',
        resultspec="O",
        argspec="O",
        arguments=["PyObject* frame"],
        callfunction="wrap_PyFrame_GetCode",
        cmpfunc=unhandled_error_compare,
    )

    test_PyFrame_GetLineNumber = CPyExtFunction(
        lambda args: args[0].f_lineno,
        lambda: (
            (sys._getframe(0),),
        ),
        code='''int wrap_PyFrame_GetLineNumber(PyObject* frame) {
            return PyFrame_GetLineNumber((PyFrameObject*)frame);
        }
        ''',
        resultspec="i",
        argspec="O",
        arguments=["PyObject* frame"],
        callfunction="wrap_PyFrame_GetLineNumber",
        cmpfunc=unhandled_error_compare,
    )

    # GR-46546 These APIs are backported from 3.11
    if sys.version_info >= (3, 11) or sys.implementation.name == 'graalpy':
        test_PyFrame_GetLasti = CPyExtFunction(
            lambda args: args[0].f_lasti,
            lambda: (
                (sys._getframe(0),),
            ),
            code='''int wrap_PyFrame_GetLasti(PyObject* frame) {
                return PyFrame_GetLasti((PyFrameObject*)frame);
            }
            ''',
            resultspec="i",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetLasti",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetBack = CPyExtFunction(
            lambda args: args[0].f_back,
            lambda: (
                (sys._getframe(0),),
            ),
            code='''PyObject* wrap_PyFrame_GetBack(PyObject* frame) {
                return (PyObject*)PyFrame_GetBack((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetBack",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetLocals = CPyExtFunction(
            lambda args: args[0].f_locals,
            lambda: (
                (sys._getframe(0),),
            ),
            code='''PyObject* wrap_PyFrame_GetLocals(PyObject* frame) {
                return PyFrame_GetLocals((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetLocals",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetGlobals = CPyExtFunction(
            lambda args: args[0].f_globals,
            lambda: (
                (sys._getframe(0),),
            ),
            code='''PyObject* wrap_PyFrame_GetGlobals(PyObject* frame) {
                return PyFrame_GetGlobals((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetGlobals",
            cmpfunc=unhandled_error_compare,
        )

        test_PyFrame_GetBuiltins = CPyExtFunction(
            lambda args: args[0].f_builtins,
            lambda: (
                (sys._getframe(0),),
            ),
            code='''PyObject* wrap_PyFrame_GetBuiltins(PyObject* frame) {
                return PyFrame_GetBuiltins((PyFrameObject*)frame);
            }
            ''',
            resultspec="O",
            argspec="O",
            arguments=["PyObject* frame"],
            callfunction="wrap_PyFrame_GetBuiltins",
            cmpfunc=unhandled_error_compare,
        )
