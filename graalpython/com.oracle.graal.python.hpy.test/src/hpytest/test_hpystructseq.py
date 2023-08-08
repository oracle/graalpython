# MIT License
#
# Copyright (c) 2023, 2023, Oracle and/or its affiliates.
# Copyright (c) 2019 pyhandle
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

"""
NOTE: this tests are also meant to be run as PyPy "applevel" tests.

This means that global imports will NOT be visible inside the test
functions. In particular, you have to "import pytest" inside the test in order
to be able to use e.g. pytest.raises (which on PyPy will be implemented by a
"fake pytest module")
"""
from .support import HPyTest


class TestHPyStructSequence(HPyTest):
    def test_structseq(self):
        import pytest
        mod = self.make_module("""
            static HPyStructSequence_Field structseq_fields[] = {
                { "field0", "doc0" },
                { "field1", NULL },
                { NULL, NULL },
                { NULL, NULL },
            };

            static HPyStructSequence_Field structseq_empty_fields[] = {
                { NULL, NULL },
            };

            static HPyStructSequence_Desc withfields_desc = {
                .name = "mytest.WithFields",
                .doc = "some doc",
                .fields = structseq_fields
            };

            static HPyStructSequence_Desc nofields_desc = {
                .name = "mytest.NoFields",
                .fields = structseq_empty_fields
            };

            #define N 3

            HPyDef_METH(build, "build", HPyFunc_O)
            static HPy build_impl(HPyContext *ctx, HPy self, HPy type)
            {
                int i;
                HPy elements[N];
                elements[0] = HPyLong_FromLong(ctx, 1);
                elements[1] = HPyFloat_FromDouble(ctx, 2.0);
                elements[2] = HPyUnicode_FromString(ctx, "3");
                HPy structseq = HPyStructSequence_New(ctx, type, N, elements);
                for (i = 0; i < N; i++)
                    HPy_Close(ctx, elements[i]);
                if (HPy_IsNull(structseq))
                    return HPy_NULL;
                return structseq;
            }

            static void make_types(HPyContext *ctx, HPy module)
            {
                // cannot be done in the static initializer
                structseq_fields[2].name = HPyStructSequence_UnnamedField;

                HPy h_withfields_type = HPyStructSequence_NewType(ctx, &withfields_desc);
                if (HPy_IsNull(h_withfields_type))
                    return;
                HPy_SetAttr_s(ctx, module, "WithFields", h_withfields_type);
                HPy_Close(ctx, h_withfields_type);

                HPy h_nofields_type = HPyStructSequence_NewType(ctx, &nofields_desc);
                if (HPy_IsNull(h_nofields_type)) {
                    return;
                }
                HPy_SetAttr_s(ctx, module, "NoFields", h_nofields_type);
                HPy_Close(ctx, h_nofields_type);
            }

            @EXPORT(build)
            @EXTRA_INIT_FUNC(make_types)
            @INIT
        """)
        assert mod.WithFields.__name__ == "WithFields"
        assert mod.WithFields.__doc__ == "some doc"
        assert mod.WithFields.__module__ == "mytest"
        assert mod.NoFields.__name__ == "NoFields"
        # In Python 3.8 or earlier, we cannot pass a NULL pointer as docstring,
        # so we pass the empty string. Therefore, we are testing for None or
        # empty docstring.
        assert not mod.NoFields.__doc__
        assert mod.NoFields.__module__ == "mytest"

        assert mod.WithFields.n_fields == 3
        assert mod.NoFields.n_fields == 0

        s0 = mod.build(mod.WithFields)
        assert s0.field0 == 1, s0.field0
        assert s0.field1 == 2.0, s0.field1
        assert s0[2] == "3", s0[2]

        with pytest.raises(TypeError):
            mod.build(mod.NoFields)

        with pytest.raises(TypeError):
            mod.build(str)


    def test_invalid_descriptor(self):
        import pytest
        mod = self.make_module("""
            static HPyStructSequence_Field sentinel = { NULL, NULL };
            
            static HPyStructSequence_Desc nofields_desc = {
                .fields = &sentinel
            };
            
            HPyDef_METH(build, "build", HPyFunc_NOARGS)
            static HPy build_impl(HPyContext *ctx, HPy self)
            {
                return HPyStructSequence_NewType(ctx, &nofields_desc);
            }

            @EXPORT(build)
            @INIT
        """)
        with pytest.raises(SystemError):
            mod.build()
