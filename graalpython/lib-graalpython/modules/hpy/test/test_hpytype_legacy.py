# MIT License
# 
# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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

""" HPyType tests on legacy types. """

import pytest
from .support import HPyTest
from .test_hpytype import PointTemplate, TestType as _TestType


class LegacyPointTemplate(PointTemplate):
    """
    Override PointTemplate to instead define a legacy point type that
    still provides access to PyObject_HEAD.
    """

    _STRUCT_BEGIN_FORMAT = """
        #include <Python.h>
        typedef struct {{
            PyObject_HEAD
    """

    _STRUCT_END_FORMAT = """
        }} {struct_name};
        HPyType_LEGACY_HELPERS({struct_name})
    """

    _TYPE_STRUCT_BEGIN_FORMAT = """
        #include <Python.h>
        typedef struct {{
            PyHeapTypeObject super;
    """

    _TYPE_STRUCT_END_FORMAT = _STRUCT_END_FORMAT

    _IS_LEGACY = ".legacy = true,"


@pytest.mark.usefixtures('skip_nfi')
class TestLegacyType(_TestType):

    ExtensionTemplate = LegacyPointTemplate

    @pytest.mark.syncgc
    def test_legacy_dealloc(self):
        mod = self.make_module("""
            static long dealloc_counter = 0;

            HPyDef_METH(get_counter, "get_counter", get_counter_impl, HPyFunc_NOARGS)
            static HPy get_counter_impl(HPyContext *ctx, HPy self)
            {
                return HPyLong_FromLong(ctx, dealloc_counter);
            }

            @DEFINE_PointObject
            @DEFINE_Point_new
            static void Point_dealloc(PyObject *self)
            {
                dealloc_counter++;
                Py_TYPE(self)->tp_free(self);
            }

            static HPyDef *Point_defines[] = {&Point_new, NULL};
            static PyType_Slot Point_slots[] = {
                {Py_tp_dealloc, Point_dealloc},
                {0, NULL},
            };
            static HPyType_Spec Point_spec = {
                .name = "mytest.Point",
                .basicsize = sizeof(PointObject),
                .defines = Point_defines,
                .legacy = true,
                .legacy_slots = Point_slots,
            };

            @EXPORT(get_counter)
            @EXPORT_TYPE("Point", Point_spec)
            @INIT
        """)
        assert mod.get_counter() == 0
        p = mod.Point(0, 0)
        del p
        import gc; gc.collect()
        assert mod.get_counter() == 1

    @pytest.mark.syncgc
    def test_legacy_dealloc_and_HPy_tp_traverse(self):
        import pytest
        mod_src = """
            @DEFINE_PointObject
            @DEFINE_Point_new
            HPyDef_SLOT(Point_traverse, Point_traverse_impl, HPy_tp_traverse)
            static int Point_traverse_impl(void *self, HPyFunc_visitproc visit, void *arg)
            {
                return 0;
            }
            static void Point_dealloc(PyObject *self)
            {
                return;
            }

            static HPyDef *Point_defines[] = {&Point_new, &Point_traverse, NULL};
            static PyType_Slot Point_slots[] = {
                {Py_tp_dealloc, Point_dealloc},
                {0, NULL},
            };
            static HPyType_Spec Point_spec = {
                .name = "mytest.Point",
                .basicsize = sizeof(PointObject),
                .defines = Point_defines,
                .legacy = true,
                .legacy_slots = Point_slots,
            };
            @EXPORT_TYPE("Point", Point_spec)
            @INIT
        """
        with pytest.raises(TypeError) as err:
            mod = self.make_module(mod_src)
        assert "legacy tp_dealloc" in str(err.value)

    @pytest.mark.syncgc
    def test_legacy_dealloc_and_HPy_tp_destroy(self):
        import pytest
        mod_src = """
            @DEFINE_PointObject
            @DEFINE_Point_new
            HPyDef_SLOT(Point_destroy, Point_destroy_impl, HPy_tp_destroy)
            static void Point_destroy_impl(void *obj)
            {
                return;
            }
            static void Point_dealloc(PyObject *self)
            {
                return;
            }

            static HPyDef *Point_defines[] = {&Point_new, &Point_destroy, NULL};
            static PyType_Slot Point_slots[] = {
                {Py_tp_dealloc, Point_dealloc},
                {0, NULL},
            };
            static HPyType_Spec Point_spec = {
                .name = "mytest.Point",
                .basicsize = sizeof(PointObject),
                .defines = Point_defines,
                .legacy = true,
                .legacy_slots = Point_slots,
            };
            @EXPORT_TYPE("Point", Point_spec)
            @INIT
        """
        with pytest.raises(TypeError) as err:
            mod = self.make_module(mod_src)
        assert "legacy tp_dealloc" in str(err.value)

    # Metaclass tests:
    #
    # Note: both the class (Dummy) and the metaclass (DummyMeta) must be at
    # least legacy HPy types for now.
    #
    # The metaclass must inherit from PyType_Type, so it must embed in it
    # PyHeapTypeObject, but HPyType_FromSpec assumes that the total size
    # is ~ sizeof(PyObject) + spec->basicsize. HPy it could use base size
    # instead of sizeof(PyObject), but that may be problematic w.r.t.
    # the alignment of the result.
    #
    # The class itself must be legacy: The following seems to be CPython
    # limitation/bug:
    #
    #   - (A) if we have custom metatype, then typeobject.c:mro_invoke checks:
    #       assert(base->tp_basicsize < type->tp_basicsize)
    #   - (B) if tp_basicsize == 0, then CPython fixes it by:
    #       type->tp_basicsize = base->tp_basicsize // typeobject.c:inherit_special
    #   - but (B) happens only after (A)
    #
    # Either the contract of PyType_Ready is such that if you have a metaclass,
    # you must provide tp_basicsize (including sizeof(PyObject) for the trivial
    # case), or this is a bug. With HPy pure types this could be also workaround
    # by setting some basicsize, even just one byte.

    def _metaclass_test(self, metatype_setup_code):
        mod = self.make_module("""
            #include <Python.h>
            #include <structmember.h>

            typedef struct {
                PyHeapTypeObject super;
                int meta_magic;
                int meta_member;
                char some_more[64];
            } DummyMeta;
            
            typedef struct {
                PyObject_HEAD
                int member;
            } DummyData;

            static PyMemberDef members[] = {
                    {"member", T_INT, offsetof(DummyData, member), 0, NULL},
                    {NULL, 0, 0, 0, NULL},
            };

            static PyType_Slot DummySlots[] = {
                {Py_tp_members, members},
                {0, NULL},
            };
            
            static HPyType_Spec Dummy_spec = {
                .name = "mytest.Dummy",
                .basicsize = sizeof(DummyData),
                .flags = HPy_TPFLAGS_DEFAULT | HPy_TPFLAGS_BASETYPE,
                .legacy = true,
                .legacy_slots = DummySlots,
            };
            
            // Defined by each test:
            bool setup_metatype(HPyContext *ctx, HPy module, HPy *h_DummyMeta);
            
            void setup_types(HPyContext *ctx, HPy module) {
                HPy h_DummyMeta;
                if (!setup_metatype(ctx, module, &h_DummyMeta))
                    return;
            
                HPyType_SpecParam param[] = {
                    { HPyType_SpecParam_Metaclass, h_DummyMeta },
                    { 0 }
                };
                HPy h_Dummy = HPyType_FromSpec(ctx, &Dummy_spec, param);
                if (!HPy_IsNull(h_Dummy)) {
                    HPy_SetAttr_s(ctx, module, "Dummy", h_Dummy);
                    HPy_SetAttr_s(ctx, module, "DummyMeta", h_DummyMeta);
                }
                
                HPy_Close(ctx, h_Dummy);
                HPy_Close(ctx, h_DummyMeta);
            }
        
            HPyDef_METH(set_meta_data, "set_meta_data", set_meta_data_impl, HPyFunc_O)
            static HPy set_meta_data_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                DummyMeta *data = (DummyMeta*) HPy_AsStructLegacy(ctx, arg);
                data->meta_magic = 42;
                data->meta_member = 11;
                for (size_t i = 0; i < 64; ++i)
                    data->some_more[i] = (char) i;
                return HPy_Dup(ctx, ctx->h_None);
            }

            HPyDef_METH(get_meta_data, "get_meta_data", get_meta_data_impl, HPyFunc_O)
            static HPy get_meta_data_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                DummyMeta *data = (DummyMeta*) HPy_AsStructLegacy(ctx, arg);
                for (size_t i = 0; i < 64; ++i) {
                    if (data->some_more[i] != (char) i) {
                        HPyErr_SetString(ctx, ctx->h_RuntimeError, "some_more got mangled");
                        return HPy_NULL;
                    }
                }
                return HPyLong_FromLong(ctx, data->meta_magic + data->meta_member);
            }

            HPyDef_METH(set_member, "set_member", set_member_impl, HPyFunc_O)
            static HPy set_member_impl(HPyContext *ctx, HPy self, HPy arg)
            {
                DummyData *data = (DummyData*) HPy_AsStructLegacy(ctx, arg);
                data->member = 123614;
                return HPy_Dup(ctx, ctx->h_None);
            }

            @EXPORT(set_meta_data)
            @EXPORT(get_meta_data)
            @EXPORT(set_member)
            @EXTRA_INIT_FUNC(setup_types)
            @INIT
        """ + metatype_setup_code)

        assert isinstance(mod.Dummy, type)
        assert mod.DummyMeta is type(mod.Dummy)
        assert mod.set_meta_data(mod.Dummy) is None
        assert mod.get_meta_data(mod.Dummy) == 42 + 11

        d = mod.Dummy()
        mod.set_member(d)
        assert d.member == 123614

    def test_metatype_as_legacy_static_type(self):
        self._metaclass_test("""
            static PyTypeObject DummyMetaType = {
                PyVarObject_HEAD_INIT(&PyType_Type, 0)
                .tp_base = &PyType_Type,
                .tp_name = "mytest.DummyMeta",
                .tp_basicsize = sizeof(DummyMeta),
                .tp_flags = Py_TPFLAGS_DEFAULT,
            };
        
            bool setup_metatype(HPyContext *ctx, HPy module, HPy *h_DummyMeta) {
                if (PyType_Ready(&DummyMetaType))
                    return false;
                *h_DummyMeta = HPy_FromPyObject(ctx, (PyObject*) &DummyMetaType);
                return true;
            }
        """)

    # Ideally this test should be in the super class and parametrized by
    # @IS_LEGACY for both the metatype and the class itself, but we cannot
    # do pure HPy types in this scenario - see the comment above.
    def test_metatype_as_legacy_hpy_type(self):
        self._metaclass_test("""
            static HPyType_Spec DummyMeta_spec = {
                .name = "mytest.DummyMeta",
                .basicsize = sizeof(DummyMeta),
                .flags = HPy_TPFLAGS_DEFAULT,
                .legacy = true,
            };
            
            bool setup_metatype(HPyContext *ctx, HPy module, HPy *h_DummyMeta)
            {
                HPy h_py_type = HPy_FromPyObject(ctx, (PyObject*) &PyType_Type);                                
                HPyType_SpecParam meta_param[] = {
                    { HPyType_SpecParam_Base, h_py_type },
                    { 0 }
                };
                *h_DummyMeta = HPyType_FromSpec(ctx, &DummyMeta_spec, meta_param);
                HPy_Close(ctx, h_py_type);                
                return !HPy_IsNull(*h_DummyMeta);
            }
        """)

@pytest.mark.usefixtures('skip_nfi')
class TestCustomLegacyFeatures(HPyTest):

    def test_legacy_methods(self):
        mod = self.make_module("""
            #include <Python.h>

            static PyObject *f(PyObject *self, PyObject *args)
            {
                return PyLong_FromLong(1234);
            }
            static PyObject *g(PyObject *self, PyObject *arg)
            {
                long x = PyLong_AsLong(arg);
                return PyLong_FromLong(x * 2);
            }
            static PyObject *h(PyObject *self, PyObject *args)
            {
                long a, b, c;
                if (!PyArg_ParseTuple(args, "lll", &a, &b, &c))
                    return NULL;
                return PyLong_FromLong(100*a + 10*b + c);
            }
            static PyObject *k(PyObject *self, PyObject *args, PyObject *kwargs)
            {
                static char *kwlist[] = { "a", "b", "c", NULL };
                long a, b, c;
                if (!PyArg_ParseTupleAndKeywords(args, kwargs, "lll", kwlist, &a, &b, &c))
                    return NULL;
                return PyLong_FromLong(100*a + 10*b + c);
            }

            static PyMethodDef my_legacy_methods[] = {
                {"f", (PyCFunction)f, METH_NOARGS},
                {"g", (PyCFunction)g, METH_O},
                {"h", (PyCFunction)h, METH_VARARGS},
                {"k", (PyCFunction)k, METH_VARARGS | METH_KEYWORDS},
                {NULL}
            };

            @EXPORT_LEGACY(my_legacy_methods)
            @INIT
        """)
        assert mod.f() == 1234
        assert mod.g(45) == 90
        assert mod.h(4, 5, 6) == 456
        assert mod.k(c=6, b=5, a=4) == 456

    def test_legacy_inherits_from_pure_raises(self):
        import pytest
        mod_src = """
            static HPyType_Spec PureType_spec = {
                .name = "mytest.PureType",
                .flags = HPy_TPFLAGS_DEFAULT | HPy_TPFLAGS_BASETYPE,
            };

            static HPyType_Spec LegacyType_spec = {
                .name = "mytest.LegacyType",
                .legacy = true,
            };

            static void make_Types(HPyContext *ctx, HPy module)
            {
                HPy h_PureType = HPyType_FromSpec(ctx, &PureType_spec, NULL);
                if (HPy_IsNull(h_PureType)) {
                    return;
                }

                HPyType_SpecParam LegacyType_param[] = {
                    { HPyType_SpecParam_Base, h_PureType },
                    { 0 }
                };
                HPy h_LegacyType = HPyType_FromSpec(
                    ctx, &LegacyType_spec, LegacyType_param);
                if (HPy_IsNull(h_LegacyType)) {
                    HPy_Close(ctx, h_PureType);
                    return;
                }
                HPy_Close(ctx, h_LegacyType);
                HPy_Close(ctx, h_PureType);
            }
            @EXTRA_INIT_FUNC(make_Types)
            @INIT
        """
        with pytest.raises(TypeError) as err:
            self.make_module(mod_src)
        assert str(err.value) == (
            "A legacy type should not inherit its memory layout from a"
            " pure type")
