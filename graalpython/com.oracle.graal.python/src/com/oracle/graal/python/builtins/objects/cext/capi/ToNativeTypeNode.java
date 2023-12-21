/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_alloc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_buffer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_clear;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_dealloc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_del;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_free;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_vectorcall_offset;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_weaklistoffset;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_ALLOC;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.LookupNativeI64MemberInMRONodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.LookupNativeMemberInMRONodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBasicSizeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetItemSizeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetDictOffsetNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNode;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNodeGen.LookupNativeGetattroSlotNodeGen;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.object.HiddenKey;

public abstract class ToNativeTypeNode {

    private static Object getValue(PythonManagedClass obj, SlotMethodDef slot) {
        return LookupNativeSlotNode.executeUncached(obj, slot);
    }

    private static Object allocatePyMappingMethods(PythonManagedClass obj) {
        Object mem = CStructAccess.AllocateNode.getUncached().alloc(CStructs.PyMappingMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();
        writePointerNode.write(mem, CFields.PyMappingMethods__mp_length, getValue(obj, SlotMethodDef.MP_LENGTH));
        writePointerNode.write(mem, CFields.PyMappingMethods__mp_subscript, getValue(obj, SlotMethodDef.MP_SUBSCRIPT));
        writePointerNode.write(mem, CFields.PyMappingMethods__mp_ass_subscript, getValue(obj, SlotMethodDef.MP_ASS_SUBSCRIPT));
        return mem;
    }

    private static Object allocatePyNumberMethods(PythonManagedClass obj, Object nullValue) {
        Object mem = CStructAccess.AllocateNode.getUncached().alloc(CStructs.PyNumberMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_absolute, getValue(obj, SlotMethodDef.NB_ABSOLUTE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_add, getValue(obj, SlotMethodDef.NB_ADD));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_and, getValue(obj, SlotMethodDef.NB_AND));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_bool, getValue(obj, SlotMethodDef.NB_BOOL));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_divmod, getValue(obj, SlotMethodDef.NB_DIVMOD));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_float, getValue(obj, SlotMethodDef.NB_FLOAT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_floor_divide, getValue(obj, SlotMethodDef.NB_FLOOR_DIVIDE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_index, getValue(obj, SlotMethodDef.NB_INDEX));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_add, getValue(obj, SlotMethodDef.NB_INPLACE_ADD));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_and, getValue(obj, SlotMethodDef.NB_INPLACE_AND));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_floor_divide, getValue(obj, SlotMethodDef.NB_INPLACE_FLOOR_DIVIDE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_lshift, getValue(obj, SlotMethodDef.NB_INPLACE_LSHIFT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_matrix_multiply, nullValue);
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_multiply, getValue(obj, SlotMethodDef.NB_INPLACE_MULTIPLY));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_or, getValue(obj, SlotMethodDef.NB_INPLACE_OR));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_power, getValue(obj, SlotMethodDef.NB_INPLACE_POWER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_remainder, getValue(obj, SlotMethodDef.NB_INPLACE_REMAINDER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_rshift, getValue(obj, SlotMethodDef.NB_INPLACE_RSHIFT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_subtract, getValue(obj, SlotMethodDef.NB_INPLACE_SUBTRACT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_true_divide, getValue(obj, SlotMethodDef.NB_INPLACE_TRUE_DIVIDE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_xor, getValue(obj, SlotMethodDef.NB_INPLACE_XOR));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_int, getValue(obj, SlotMethodDef.NB_INT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_invert, getValue(obj, SlotMethodDef.NB_INVERT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_lshift, getValue(obj, SlotMethodDef.NB_LSHIFT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_matrix_multiply, nullValue);
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_multiply, getValue(obj, SlotMethodDef.NB_MULTIPLY));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_negative, getValue(obj, SlotMethodDef.NB_NEGATIVE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_or, getValue(obj, SlotMethodDef.NB_OR));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_positive, getValue(obj, SlotMethodDef.NB_POSITIVE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_power, getValue(obj, SlotMethodDef.NB_POWER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_remainder, getValue(obj, SlotMethodDef.NB_REMAINDER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_reserved, nullValue);
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_rshift, getValue(obj, SlotMethodDef.NB_RSHIFT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_subtract, getValue(obj, SlotMethodDef.NB_SUBTRACT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_true_divide, getValue(obj, SlotMethodDef.NB_TRUE_DIVIDE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_xor, getValue(obj, SlotMethodDef.NB_XOR));
        return mem;
    }

    private static Object allocatePySequenceMethods(PythonManagedClass obj, Object nullValue) {
        Object mem = CStructAccess.AllocateNode.getUncached().alloc(CStructs.PyNumberMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_length, getValue(obj, SlotMethodDef.SQ_LENGTH));
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_concat, getValue(obj, SlotMethodDef.SQ_CONCAT));
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_repeat, getValue(obj, SlotMethodDef.SQ_REPEAT));
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_item, getValue(obj, SlotMethodDef.SQ_ITEM));
        writePointerNode.write(mem, CFields.PySequenceMethods__was_sq_slice, nullValue);
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_ass_item, nullValue);
        writePointerNode.write(mem, CFields.PySequenceMethods__was_sq_ass_slice, nullValue);
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_contains, nullValue);
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_inplace_concat, nullValue);
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_inplace_repeat, nullValue);
        return mem;
    }

    private static Object lookup(PythonManagedClass clazz, CFields member, HiddenKey hiddenName) {
        Object result = LookupNativeMemberInMRONodeGen.getUncached().execute(clazz, member, hiddenName);
        if (result == PNone.NO_VALUE) {
            return PythonContext.get(null).getNativeNull().getPtr();
        }
        return result;
    }

    private static long lookupSize(PythonManagedClass clazz, CFields member, HiddenKey hiddenName) {
        return LookupNativeI64MemberInMRONodeGen.getUncached().execute(clazz, member, hiddenName);
    }

    private static Object lookup(PythonManagedClass obj, SlotMethodDef slot) {
        return LookupNativeSlotNode.executeUncached(obj, slot);
    }

    private static boolean hasSlot(PythonManagedClass clazz, SlotMethodDef slot) {
        return LookupNativeSlotNode.executeUncached(clazz, slot) != PythonContext.get(null).getNativeNull().getPtr();
    }

    static void initializeType(PythonClassNativeWrapper obj, Object mem) {
        CompilerAsserts.neverPartOfCompilation();

        PythonManagedClass clazz = (PythonManagedClass) obj.getDelegate();
        boolean isType = IsBuiltinClassExactProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.PythonClass);

        PythonToNativeNode toNative = PythonToNativeNodeGen.getUncached();
        PythonToNativeNewRefNode toNativeNewRef = PythonToNativeNewRefNodeGen.getUncached();
        CStructAccess.WritePointerNode writePtrNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
        CStructAccess.WriteLongNode writeI64Node = CStructAccessFactory.WriteLongNodeGen.getUncached();
        CStructAccess.WriteIntNode writeI32Node = CStructAccessFactory.WriteIntNodeGen.getUncached();
        GetTypeFlagsNode getTypeFlagsNode = GetTypeFlagsNodeGen.getUncached();

        PythonContext ctx = PythonContext.get(null);
        PythonObjectFactory factory = ctx.factory();
        Object nullValue = ctx.getNativeNull().getPtr();

        // make this object immortal
        writeI64Node.write(mem, PyObject__ob_refcnt, PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT);
        if (isType) {
            // self-reference
            writePtrNode.write(mem, PyObject__ob_type, mem);
        } else {
            writePtrNode.write(mem, PyObject__ob_type, toNative.execute(GetClassNode.executeUncached(clazz)));
        }

        Object base = GetBaseClassNode.executeUncached(clazz);
        if (base == null) {
            base = ctx.getNativeNull();
        } else if (base instanceof PythonBuiltinClassType builtinClass) {
            base = ctx.lookupType(builtinClass);
        }

        writeI64Node.write(mem, CFields.PyVarObject__ob_size, 0L);

        writePtrNode.write(mem, CFields.PyTypeObject__tp_name, clazz.getClassNativeWrapper().getNameWrapper());
        writeI64Node.write(mem, CFields.PyTypeObject__tp_basicsize, GetBasicSizeNode.executeUncached(clazz));
        writeI64Node.write(mem, CFields.PyTypeObject__tp_itemsize, GetItemSizeNode.executeUncached(clazz));
        // writeI64Node.write(mem, CFields.PyTypeObject__tp_weaklistoffset,
        // GetWeakListOffsetNode.executeUncached(clazz));
        /*
         * TODO msimacek: this should use GetWeakListOffsetNode as in the commented out code above.
         * Unfortunately, it causes memory corruption in several libraries
         */
        long weaklistoffset;
        if (clazz instanceof PythonBuiltinClass builtin) {
            weaklistoffset = builtin.getType().getWeaklistoffset();
        } else {
            weaklistoffset = LookupNativeI64MemberInMRONodeGen.getUncached().execute(clazz, PyTypeObject__tp_weaklistoffset, SpecialAttributeNames.T___WEAKLISTOFFSET__);
        }
        writeI64Node.write(mem, CFields.PyTypeObject__tp_weaklistoffset, weaklistoffset);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_dealloc, lookup(clazz, PyTypeObject__tp_dealloc, TypeBuiltins.TYPE_DEALLOC));
        writeI64Node.write(mem, CFields.PyTypeObject__tp_vectorcall_offset, lookupSize(clazz, PyTypeObject__tp_vectorcall_offset, TypeBuiltins.TYPE_VECTORCALL_OFFSET));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_getattr, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_setattr, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_async, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_repr, lookup(clazz, SlotMethodDef.TP_REPR));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_number,
                        IsBuiltinClassExactProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.PythonObject) ? nullValue : allocatePyNumberMethods(clazz, nullValue));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_sequence, (hasSlot(clazz, SlotMethodDef.SQ_LENGTH) || hasSlot(clazz, SlotMethodDef.SQ_ITEM)) ? allocatePySequenceMethods(clazz, nullValue) : nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_mapping, hasSlot(clazz, SlotMethodDef.MP_LENGTH) ? allocatePyMappingMethods(clazz) : nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_hash, lookup(clazz, SlotMethodDef.TP_HASH));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_call, lookup(clazz, SlotMethodDef.TP_CALL));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_str, lookup(clazz, SlotMethodDef.TP_STR));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_getattro, LookupNativeGetattroSlotNodeGen.getUncached().execute(clazz));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_setattro, lookup(clazz, SlotMethodDef.TP_SETATTRO));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_buffer, lookup(clazz, PyTypeObject__tp_as_buffer, TypeBuiltins.TYPE_AS_BUFFER));
        writeI64Node.write(mem, CFields.PyTypeObject__tp_flags, getTypeFlagsNode.execute(clazz));

        // return a C string wrapper that really allocates 'char*' on TO_NATIVE
        Object docObj = clazz.getAttribute(SpecialAttributeNames.T___DOC__);
        try {
            docObj = new CStringWrapper(CastToTruffleStringNode.executeUncached(docObj));
        } catch (CannotCastException e) {
            // if not directly a string, give up (we don't call descriptors here)
            docObj = ctx.getNativeNull().getPtr();
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_doc, docObj);
        // TODO: return a proper traverse function, or at least a dummy
        writePtrNode.write(mem, CFields.PyTypeObject__tp_traverse, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_richcompare, lookup(clazz, SlotMethodDef.TP_RICHCOMPARE));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_iter, lookup(clazz, SlotMethodDef.TP_ITER));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_iternext, lookup(clazz, SlotMethodDef.TP_ITERNEXT));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_methods, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_members, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_getset, nullValue);
        if (!isType) {
            // "object" base needs to be initialized explicitly in capi.c
            writePtrNode.write(mem, CFields.PyTypeObject__tp_base, toNative.execute(base));
        }

        // TODO(fa): we could cache the dict instance on the class' native wrapper
        PDict dict = GetOrCreateDictNode.executeUncached(clazz);
        if (!(dict instanceof StgDictObject)) {
            HashingStorage dictStorage = dict.getDictStorage();
            if (!(dictStorage instanceof DynamicObjectStorage)) {
                HashingStorage storage = new DynamicObjectStorage(clazz.getStorage());
                dict.setDictStorage(storage);
                if (dictStorage != null) {
                    // copy all mappings to the new storage
                    HashingStorageAddAllToOther.executeUncached(dictStorage, dict);
                }
            }
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_dict, toNative.execute(dict));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_descr_get, lookup(clazz, SlotMethodDef.TP_DESCR_GET));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_descr_set, lookup(clazz, SlotMethodDef.TP_DESCR_SET));

        // TODO properly implement 'tp_dictoffset' for builtin classes
        writeI64Node.write(mem, CFields.PyTypeObject__tp_dictoffset, GetDictOffsetNodeGen.getUncached().execute(null, clazz));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_init, lookup(clazz, SlotMethodDef.TP_INIT));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_alloc, lookup(clazz, PyTypeObject__tp_alloc, TYPE_ALLOC));
        // T___new__ is magically a staticmethod for Python types. The tp_new slot lookup
        // expects to get the function
        Object newFunction = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.New).execute(clazz);
        if (newFunction instanceof PDecoratedMethod) {
            newFunction = ((PDecoratedMethod) newFunction).getCallable();
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_new, ManagedMethodWrappers.createKeywords(newFunction));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_free, lookup(clazz, PyTypeObject__tp_free, TypeBuiltins.TYPE_FREE));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_clear, lookup(clazz, PyTypeObject__tp_clear, TypeBuiltins.TYPE_CLEAR));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_is_gc, nullValue);
        if (clazz.basesTuple == null) {
            clazz.basesTuple = factory.createTuple(GetBaseClassesNode.executeUncached(clazz));
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_bases, toNative.execute(clazz.basesTuple));
        if (clazz.mroStore == null) {
            clazz.mroStore = factory.createTuple(GetMroStorageNode.executeUncached(clazz));
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_mro, toNative.execute(clazz.mroStore));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_cache, nullValue);
        PDict subclasses = GetSubclassesNode.executeUncached(clazz);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_subclasses, toNativeNewRef.execute(subclasses));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_weaklist, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_del, lookup(clazz, PyTypeObject__tp_del, TypeBuiltins.TYPE_DEL));
        writeI32Node.write(mem, CFields.PyTypeObject__tp_version_tag, 0);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_finalize, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_vectorcall, nullValue);
    }
}
