import textwrap
import difflib
import py
import pytest
from hpy.tools.autogen.parse import HPyAPI
from hpy.tools.autogen.ctx import autogen_ctx_h, autogen_ctx_def_h
from hpy.tools.autogen.trampolines import (autogen_trampolines_h,
                                           cpython_autogen_api_impl_h)
from hpy.tools.autogen.hpyslot import autogen_hpyslot_h

def src_equal(exp, got):
    # try to compare two C sources, ignoring whitespace
    exp = textwrap.dedent(exp).strip()
    got = textwrap.dedent(got).strip()
    if exp.split() != got.split():
        diff = difflib.unified_diff(exp.splitlines(), got.splitlines(),
                                    fromfile='expected',
                                    tofile='got')
        print()
        for line in diff:
            print(line)
        return False
    return True

@pytest.mark.usefixtures('initargs')
class BaseTestAutogen:

    @pytest.fixture
    def initargs(self, tmpdir):
        self.tmpdir = tmpdir

    def parse(self, src):
        fname = self.tmpdir.join('test_api.h')
        # automatically add useful typedefs
        src = """
            #define STRINGIFY(X) #X
            #define HPy_ID(X) _Pragma(STRINGIFY(id=X)) \\

            typedef int HPy;
            typedef int HPyContext;
        """ + src
        fname.write(src)
        return HPyAPI.parse(fname)


class TestHPyAPI(BaseTestAutogen):

    def test_ctx_name(self):
        api = self.parse("""
            HPy_ID(0) HPy h_None;
            HPy_ID(1) HPy HPy_Dup(HPyContext *ctx, HPy h);
            HPy_ID(2) void* _HPy_AsStruct(HPyContext *ctx, HPy h);
        """)
        assert api.get_var('h_None').ctx_name() == 'h_None'
        assert api.get_func('HPy_Dup').ctx_name() == 'ctx_Dup'
        assert api.get_func('_HPy_AsStruct').ctx_name() == 'ctx_AsStruct'

    def test_cpython_name(self):
        api = self.parse("""
            HPy_ID(0) HPy HPy_Dup(HPyContext *ctx, HPy h);
            HPy_ID(1) long HPyLong_AsLong(HPyContext *ctx, HPy h);
            HPy_ID(2) HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2);
        """)
        assert api.get_func('HPy_Dup').cpython_name is None
        assert api.get_func('HPyLong_AsLong').cpython_name == 'PyLong_AsLong'
        assert api.get_func('HPy_Add').cpython_name == 'PyNumber_Add'

    def test_hpyslot(self):
        api = self.parse("""
            typedef enum {
                HPy_nb_add = SLOT(7, HPyFunc_BINARYFUNC),
                HPy_tp_repr = SLOT(66, HPyFunc_REPRFUNC),
            } HPySlot_Slot;
        """)
        nb_add = api.get_slot('HPy_nb_add')
        assert nb_add.value == '7'
        assert nb_add.hpyfunc == 'HPyFunc_BINARYFUNC'
        #
        tp_repr = api.get_slot('HPy_tp_repr')
        assert tp_repr.value == '66'
        assert tp_repr.hpyfunc == 'HPyFunc_REPRFUNC'

    def test_parse_id(self):
        api = self.parse("""
            HPy_ID(0) HPy h_Foo;
            HPy_ID(1)
            long HPyFoo_Bar(HPyContext *ctx, HPy h);
        """)
        assert len(api.variables) == 1
        assert len(api.functions) == 1
        assert api.variables[0].ctx_index == 0
        assert api.functions[0].ctx_index == 1

        # don't allow gaps in the sequence of IDs
        with pytest.raises(AssertionError):
            self.parse("""
            HPy_ID(0) HPy h_Foo;
            HPy_ID(3) long HPyFoo_Bar(HPyContext *ctx, HPy h);
            """)

        # don't allow re-using of IDs
        with pytest.raises(AssertionError):
            self.parse("""
            HPy_ID(0) HPy h_Foo;
            HPy_ID(0) HPy h_Foo;
            """)

        # all context members must have an ID
        with pytest.raises(ValueError):
            self.parse("HPy h_Foo;")

        # pragmas must be of form '#pramga key=value'
        with pytest.raises(ValueError):
            self.parse("#pragma hello\nHPy h_Foo;")

        # pragmas value must be an integer
        with pytest.raises(ValueError):
            self.parse("#pragma hello=world\nHPy h_Foo;")

class TestAutoGen(BaseTestAutogen):

    def test_autogen_ctx_h(self):
        api = self.parse("""
            HPy_ID(0) HPy h_None;
            HPy_ID(1) HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2);
        """)
        got = autogen_ctx_h(api).generate()
        exp = """
            struct _HPyContext_s {
                const char *name; // used just to make debugging and testing easier
                void *_private;   // used by implementations to store custom data
                int abi_version;
                HPy h_None;
                HPy (*ctx_Add)(HPyContext *ctx, HPy h1, HPy h2);
            };
        """
        assert src_equal(exp, got)

    def test_autogen_ctx_def_h(self):
        api = self.parse("""
            HPy_ID(0) HPy h_None;
            HPy_ID(1) HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2);
        """)
        got = autogen_ctx_def_h(api).generate()
        exp = """
            struct _HPyContext_s g_universal_ctx = {
                .name = "HPy Universal ABI (CPython backend)",
                ._private = NULL,
                .abi_version = HPY_ABI_VERSION,
                /* h_None & co. are initialized by init_universal_ctx() */
                .ctx_Add = &ctx_Add,
            };
        """
        assert src_equal(exp, got)

    def test_autogen_trampolines_h(self):
        api = self.parse("""
            HPy_ID(0) HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2);
            HPy_ID(1) void HPy_Close(HPyContext *ctx, HPy h);
            HPy_ID(2) void* _HPy_AsStruct(HPyContext *ctx, HPy h);
        """)
        got = autogen_trampolines_h(api).generate()
        exp = """
            HPyAPI_FUNC HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2) {
                return ctx->ctx_Add ( ctx, h1, h2 );
            }

            HPyAPI_FUNC void HPy_Close(HPyContext *ctx, HPy h) {
                ctx->ctx_Close ( ctx, h );
            }

            HPyAPI_FUNC void *_HPy_AsStruct(HPyContext *ctx, HPy h) {
                return ctx->ctx_AsStruct ( ctx, h );
            }
        """
        assert src_equal(got, exp)

    def test_cpython_api_impl_h(self):
        api = self.parse("""
            HPy_ID(0) HPy HPy_Dup(HPyContext *ctx, HPy h);
            HPy_ID(1) HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2);
            HPy_ID(2) HPy HPyLong_FromLong(HPyContext *ctx, long value);
            HPy_ID(3) char* HPyBytes_AsString(HPyContext *ctx, HPy h);
        """)
        got = cpython_autogen_api_impl_h(api).generate()
        exp = """
            HPyAPI_FUNC
            HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2)
            {
                return _py2h(PyNumber_Add(_h2py(h1), _h2py(h2)));
            }

            HPyAPI_FUNC
            HPy HPyLong_FromLong(HPyContext *ctx, long value)
            {
                return _py2h(PyLong_FromLong(value));
            }

            HPyAPI_FUNC
            char *HPyBytes_AsString(HPyContext *ctx, HPy h)
            {
                return PyBytes_AsString(_h2py(h));
            }
        """
        assert src_equal(got, exp)

    def test_autogen_hpyslot_h(self):
        api = self.parse("""
            typedef enum {
                HPy_nb_add = SLOT(7, HPyFunc_BINARYFUNC),
                HPy_tp_repr = SLOT(66, HPyFunc_REPRFUNC),
            } HPySlot_Slot;
        """)
        got = autogen_hpyslot_h(api).generate()
        exp = """
            typedef enum {
                HPy_nb_add = 7,
                HPy_tp_repr = 66,
            } HPySlot_Slot;

        #define _HPySlot_SIG__HPy_nb_add HPyFunc_BINARYFUNC
        #define _HPySlot_SIG__HPy_tp_repr HPyFunc_REPRFUNC
        """
        assert src_equal(got, exp)
