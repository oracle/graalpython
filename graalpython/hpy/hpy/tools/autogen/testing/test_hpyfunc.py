from hpy.tools.autogen.hpyfunc import autogen_hpyfunc_declare_h
from hpy.tools.autogen.hpyfunc import autogen_hpyfunc_trampoline_h
from hpy.tools.autogen.hpyfunc import autogen_ctx_call_i
from hpy.tools.autogen.hpyfunc import autogen_cpython_hpyfunc_trampoline_h
from hpy.tools.autogen.testing.test_autogen import BaseTestAutogen, src_equal

class TestHPyFunc(BaseTestAutogen):

    def test_parse(self):
        api = self.parse("""
            typedef int HPyFunc_Signature;
            typedef HPy (*HPyFunc_noargs)(HPyContext *ctx, HPy self);
        """)
        assert len(api.hpyfunc_typedefs) == 1
        hpyfunc = api.get_hpyfunc_typedef('HPyFunc_noargs')
        assert hpyfunc.name == 'HPyFunc_noargs'
        assert hpyfunc.base_name() == 'noargs'

    def test_autogen_hpyfunc_declare_h(self):
        api = self.parse("""
            typedef HPy (*HPyFunc_foo)(HPyContext *ctx, HPy self);
            typedef HPy (*HPyFunc_bar)(HPyContext *ctx, HPy, int);
        """)
        got = autogen_hpyfunc_declare_h(api).generate()
        exp = """
            #define _HPyFunc_DECLARE_HPyFunc_FOO(SYM) static HPy SYM(HPyContext *ctx, HPy self)
            #define _HPyFunc_DECLARE_HPyFunc_BAR(SYM) static HPy SYM(HPyContext *ctx, HPy, int)

            typedef HPy (*HPyFunc_foo)(HPyContext *ctx, HPy self);
            typedef HPy (*HPyFunc_bar)(HPyContext *ctx, HPy, int);
        """
        assert src_equal(got, exp)

    def test_autogen_hpyfunc_trampoline_h(self):
        api = self.parse("""
            typedef HPy (*HPyFunc_foo)(HPyContext *ctx, HPy arg, int xy);
            typedef HPy (*HPyFunc_bar)(HPyContext *ctx, HPy, int);
            typedef void (*HPyFunc_proc)(HPyContext *ctx, int x);
        """)
        got = autogen_hpyfunc_trampoline_h(api).generate()
        exp = r"""
            typedef struct {
                cpy_PyObject *arg;
                int xy;
                cpy_PyObject * result;
            } _HPyFunc_args_FOO;

            #define _HPyFunc_TRAMPOLINE_HPyFunc_FOO(SYM, IMPL) \
                static cpy_PyObject *SYM(cpy_PyObject *arg, int xy) \
                { \
                    _HPyFunc_args_FOO a = { arg, xy }; \
                    _HPy_CallRealFunctionFromTrampoline( \
                       _ctx_for_trampolines, HPyFunc_FOO, (HPyCFunction)IMPL, &a); \
                    return a.result; \
                }

            typedef struct {
                cpy_PyObject *arg0;
                int arg1;
                cpy_PyObject * result;
            } _HPyFunc_args_BAR;

            #define _HPyFunc_TRAMPOLINE_HPyFunc_BAR(SYM, IMPL) \
                static cpy_PyObject *SYM(cpy_PyObject *arg0, int arg1) \
                { \
                    _HPyFunc_args_BAR a = { arg0, arg1 }; \
                    _HPy_CallRealFunctionFromTrampoline( \
                       _ctx_for_trampolines, HPyFunc_BAR, (HPyCFunction)IMPL, &a); \
                    return a.result; \
                }

            typedef struct {
                int x;
            } _HPyFunc_args_PROC;

            #define _HPyFunc_TRAMPOLINE_HPyFunc_PROC(SYM, IMPL) \
                static void SYM(int x) \
                { \
                    _HPyFunc_args_PROC a = { x }; \
                    _HPy_CallRealFunctionFromTrampoline( \
                       _ctx_for_trampolines, HPyFunc_PROC, (HPyCFunction)IMPL, &a); \
                    return; \
                }
        """
        assert src_equal(got, exp)

    def test_autogen_ctx_call_i(self):
        api = self.parse("""
            typedef HPy (*HPyFunc_foo)(HPyContext *ctx, HPy arg, int xy);
            typedef int (*HPyFunc_bar)(HPyContext *ctx);
            typedef int (*HPyFunc_baz)(HPyContext *ctx, HPy, int);
            typedef void (*HPyFunc_proc)(HPyContext *ctx, int x);
        """)
        got = autogen_ctx_call_i(api).generate()
        exp = r"""
            case HPyFunc_FOO: {
                HPyFunc_foo f = (HPyFunc_foo)func;
                _HPyFunc_args_FOO *a = (_HPyFunc_args_FOO*)args;
                a->result = _h2py(f(ctx, _py2h(a->arg), a->xy));
                return;
            }
            case HPyFunc_BAR: {
                HPyFunc_bar f = (HPyFunc_bar)func;
                _HPyFunc_args_BAR *a = (_HPyFunc_args_BAR*)args;
                a->result = f(ctx);
                return;
            }
            case HPyFunc_BAZ: {
                HPyFunc_baz f = (HPyFunc_baz)func;
                _HPyFunc_args_BAZ *a = (_HPyFunc_args_BAZ*)args;
                a->result = f(ctx, _py2h(a->arg0), a->arg1);
                return;
            }
            case HPyFunc_PROC: {
                HPyFunc_proc f = (HPyFunc_proc)func;
                _HPyFunc_args_PROC *a = (_HPyFunc_args_PROC*)args;
                f(ctx, a->x);
                return;
            }
        """
        assert src_equal(got, exp)

    def test_autogen_cpython_hpyfunc_trampoline_h(self):
        api = self.parse("""
            typedef HPy (*HPyFunc_foo)(HPyContext *ctx, HPy arg, int xy);
            typedef HPy (*HPyFunc_bar)(HPyContext *ctx, HPy, int);
        """)
        got = autogen_cpython_hpyfunc_trampoline_h(api).generate()
        exp = r"""
            typedef HPy (*_HPyCFunction_FOO)(HPyContext *, HPy, int);
            #define _HPyFunc_TRAMPOLINE_HPyFunc_FOO(SYM, IMPL) \
                static cpy_PyObject *SYM(cpy_PyObject *arg, int xy) \
                { \
                    _HPyCFunction_FOO func = (_HPyCFunction_FOO)IMPL; \
                    return _h2py(func(_HPyGetContext(), _py2h(arg), xy)); \
                }
            typedef HPy (*_HPyCFunction_BAR)(HPyContext *, HPy, int);
            #define _HPyFunc_TRAMPOLINE_HPyFunc_BAR(SYM, IMPL) \
                static cpy_PyObject *SYM(cpy_PyObject *arg0, int arg1) \
                { \
                    _HPyCFunction_BAR func = (_HPyCFunction_BAR)IMPL; \
                    return _h2py(func(_HPyGetContext(), _py2h(arg0), arg1)); \
                }
        """
        assert src_equal(got, exp)
