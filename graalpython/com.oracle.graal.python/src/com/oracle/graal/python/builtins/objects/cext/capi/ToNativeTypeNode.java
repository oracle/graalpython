/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.lookupNativeI64MemberInMRO;
import static com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.lookupNativeMemberInMRO;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_alloc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_buffer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_clear;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_dealloc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_del;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_free;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_is_gc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_traverse;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_vectorcall_offset;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_weaklistoffset;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.WritePointerNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.TpSlotMeta;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBasicSizeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetDictOffsetNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetItemSizeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;

public abstract class ToNativeTypeNode {

    private static Object getSlot(PythonManagedClass obj, SlotMethodDef slot) {
        return LookupNativeSlotNode.executeUncached(obj, slot);
    }

    private static boolean hasAsyncMethods(PythonManagedClass obj) {
        return (obj.getMethodsFlags() & MethodsFlags.ASYNC_METHODS) != 0;
    }

    private static boolean hasSequenceMethods(PythonManagedClass obj) {
        return (obj.getMethodsFlags() & MethodsFlags.SEQUENCE_METHODS) != 0;
    }

    private static boolean hasMappingMethods(PythonManagedClass obj) {
        return (obj.getMethodsFlags() & MethodsFlags.MAPPING_METHODS) != 0;
    }

    private static Object allocatePyAsyncMethods(PythonManagedClass obj, Object nullValue) {
        Object mem = CStructAccess.AllocateNode.allocUncached(CStructs.PyAsyncMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();
        writePointerNode.write(mem, CFields.PyAsyncMethods__am_await, getSlot(obj, SlotMethodDef.AM_AWAIT));
        writePointerNode.write(mem, CFields.PyAsyncMethods__am_aiter, getSlot(obj, SlotMethodDef.AM_AITER));
        writePointerNode.write(mem, CFields.PyAsyncMethods__am_anext, getSlot(obj, SlotMethodDef.AM_ANEXT));
        writePointerNode.write(mem, CFields.PyAsyncMethods__am_send, nullValue /*- getValue(obj, SlotMethodDef.AM_SEND) */);
        return mem;
    }

    private static void writeGroupSlots(CFields groupField, TpSlots slots, WritePointerNode writePointerNode, Object groupPointer, Object nullValue) {
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            if (def.getNativeGroupOrField() == groupField) {
                writePointerNode.write(groupPointer, def.getNativeField(), def.getNativeValue(slots, nullValue));
            }
        }
    }

    private static Object allocatePyMappingMethods(TpSlots slots, Object nullValue) {
        Object mem = CStructAccess.AllocateNode.allocUncached(CStructs.PyMappingMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();

        writeGroupSlots(CFields.PyTypeObject__tp_as_mapping, slots, writePointerNode, mem, nullValue);

        return mem;
    }

    private static Object allocatePyNumberMethods(PythonManagedClass obj, TpSlots slots, Object nullValue) {
        Object mem = CStructAccess.AllocateNode.allocUncached(CStructs.PyNumberMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();

        writeGroupSlots(CFields.PyTypeObject__tp_as_number, slots, writePointerNode, mem, nullValue);

        writePointerNode.write(mem, CFields.PyNumberMethods__nb_absolute, getSlot(obj, SlotMethodDef.NB_ABSOLUTE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_float, getSlot(obj, SlotMethodDef.NB_FLOAT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_index, getSlot(obj, SlotMethodDef.NB_INDEX));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_add, getSlot(obj, SlotMethodDef.NB_INPLACE_ADD));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_and, getSlot(obj, SlotMethodDef.NB_INPLACE_AND));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_floor_divide, getSlot(obj, SlotMethodDef.NB_INPLACE_FLOOR_DIVIDE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_lshift, getSlot(obj, SlotMethodDef.NB_INPLACE_LSHIFT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_matrix_multiply, nullValue);
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_multiply, getSlot(obj, SlotMethodDef.NB_INPLACE_MULTIPLY));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_or, getSlot(obj, SlotMethodDef.NB_INPLACE_OR));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_power, getSlot(obj, SlotMethodDef.NB_INPLACE_POWER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_remainder, getSlot(obj, SlotMethodDef.NB_INPLACE_REMAINDER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_rshift, getSlot(obj, SlotMethodDef.NB_INPLACE_RSHIFT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_subtract, getSlot(obj, SlotMethodDef.NB_INPLACE_SUBTRACT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_true_divide, getSlot(obj, SlotMethodDef.NB_INPLACE_TRUE_DIVIDE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_xor, getSlot(obj, SlotMethodDef.NB_INPLACE_XOR));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_int, getSlot(obj, SlotMethodDef.NB_INT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_invert, getSlot(obj, SlotMethodDef.NB_INVERT));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_negative, getSlot(obj, SlotMethodDef.NB_NEGATIVE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_positive, getSlot(obj, SlotMethodDef.NB_POSITIVE));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_power, getSlot(obj, SlotMethodDef.NB_POWER));
        writePointerNode.write(mem, CFields.PyNumberMethods__nb_reserved, nullValue);
        return mem;
    }

    private static Object allocatePySequenceMethods(PythonManagedClass obj, TpSlots slots, Object nullValue) {
        Object mem = CStructAccess.AllocateNode.allocUncached(CStructs.PyNumberMethods);
        CStructAccess.WritePointerNode writePointerNode = CStructAccess.WritePointerNode.getUncached();

        writeGroupSlots(CFields.PyTypeObject__tp_as_sequence, slots, writePointerNode, mem, nullValue);

        writePointerNode.write(mem, CFields.PySequenceMethods__was_sq_slice, nullValue);
        writePointerNode.write(mem, CFields.PySequenceMethods__was_sq_ass_slice, nullValue);
        // TODO populate sq_contains
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_contains, nullValue);
        // TODO populate sq_inplace_concat
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_inplace_concat, nullValue);
        // TODO populate sq_inplace_repeat
        writePointerNode.write(mem, CFields.PySequenceMethods__sq_inplace_repeat, nullValue);
        return mem;
    }

    private static Object lookup(PythonManagedClass clazz, CFields member, HiddenAttr hiddenName) {
        Object result = lookupNativeMemberInMRO(clazz, member, hiddenName);
        if (result == PNone.NO_VALUE) {
            return PythonContext.get(null).getNativeNull();
        }
        return result;
    }

    private static long lookupSize(PythonManagedClass clazz, CFields member, HiddenAttr hiddenName) {
        return lookupNativeI64MemberInMRO(clazz, member, hiddenName);
    }

    private static Object lookup(PythonManagedClass obj, SlotMethodDef slot) {
        return LookupNativeSlotNode.executeUncached(obj, slot);
    }

    static void initializeType(PythonClassNativeWrapper obj, Object mem, boolean heaptype) {
        CompilerAsserts.neverPartOfCompilation();

        PythonManagedClass clazz = (PythonManagedClass) obj.getDelegate();
        TpSlots slots = GetTpSlotsNode.executeUncached(clazz);
        boolean isType = IsBuiltinClassExactProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.PythonClass);

        PythonToNativeNode toNative = PythonToNativeNodeGen.getUncached();
        PythonToNativeNewRefNode toNativeNewRef = PythonToNativeNewRefNodeGen.getUncached();
        CStructAccess.WritePointerNode writePtrNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
        CStructAccess.WriteLongNode writeI64Node = CStructAccessFactory.WriteLongNodeGen.getUncached();
        CStructAccess.WriteIntNode writeI32Node = CStructAccessFactory.WriteIntNodeGen.getUncached();
        GetTypeFlagsNode getTypeFlagsNode = GetTypeFlagsNodeGen.getUncached();

        PythonContext ctx = PythonContext.get(null);
        PythonObjectFactory factory = ctx.factory();
        Object nullValue = ctx.getNativeNull();

        // make this object immortal
        writeI64Node.write(mem, PyObject__ob_refcnt, PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT);
        if (isType) {
            // self-reference
            writePtrNode.write(mem, PyObject__ob_type, mem);
        } else {
            writePtrNode.write(mem, PyObject__ob_type, toNative.execute(GetClassNode.executeUncached(clazz)));
        }

        long flags = getTypeFlagsNode.execute(clazz);
        /*
         * Our datetime classes are declared as static types in C, but are implemented as
         * pure-python heaptypes. Make them into static types on the C-side.
         */
        if (!heaptype) {
            flags &= ~TypeFlags.HEAPTYPE;
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
            weaklistoffset = lookupNativeI64MemberInMRO(clazz, PyTypeObject__tp_weaklistoffset, SpecialAttributeNames.T___WEAKLISTOFFSET__);
        }
        Object asAsync = hasAsyncMethods(clazz) ? allocatePyAsyncMethods(clazz, nullValue) : nullValue;
        Object asNumber = IsBuiltinClassExactProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.PythonObject) ? nullValue : allocatePyNumberMethods(clazz, slots, nullValue);
        Object asSequence = (slots.has_as_sequence() || hasSequenceMethods(clazz)) ? allocatePySequenceMethods(clazz, slots, nullValue) : nullValue;
        Object asMapping = slots.has_as_mapping() ? allocatePyMappingMethods(slots, nullValue) : nullValue;
        Object asBuffer = lookup(clazz, PyTypeObject__tp_as_buffer, HiddenAttr.AS_BUFFER);
        writeI64Node.write(mem, CFields.PyTypeObject__tp_weaklistoffset, weaklistoffset);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_dealloc, lookup(clazz, PyTypeObject__tp_dealloc, HiddenAttr.DEALLOC));
        writeI64Node.write(mem, CFields.PyTypeObject__tp_vectorcall_offset, lookupSize(clazz, PyTypeObject__tp_vectorcall_offset, HiddenAttr.VECTORCALL_OFFSET));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_getattr, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_async, asAsync);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_repr, lookup(clazz, SlotMethodDef.TP_REPR));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_number, asNumber);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_sequence, asSequence);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_mapping, asMapping);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_hash, lookup(clazz, SlotMethodDef.TP_HASH));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_call, lookup(clazz, SlotMethodDef.TP_CALL));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_str, lookup(clazz, SlotMethodDef.TP_STR));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_as_buffer, asBuffer);
        writeI64Node.write(mem, CFields.PyTypeObject__tp_flags, flags);

        // return a C string wrapper that really allocates 'char*' on TO_NATIVE
        Object docObj = clazz.getAttribute(SpecialAttributeNames.T___DOC__);
        try {
            docObj = new CStringWrapper(CastToTruffleStringNode.executeUncached(docObj));
        } catch (CannotCastException e) {
            // if not directly a string, give up (we don't call descriptors here)
            docObj = ctx.getNativeNull();
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_doc, docObj);

        Object tpTraverse = nullValue;
        Object tpIsGc = nullValue;
        if ((flags & TypeFlags.HAVE_GC) != 0) {
            tpTraverse = lookup(clazz, PyTypeObject__tp_traverse, HiddenAttr.TRAVERSE);
            tpIsGc = lookup(clazz, PyTypeObject__tp_is_gc, HiddenAttr.IS_GC);
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_traverse, tpTraverse);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_is_gc, tpIsGc);

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
                HashingStorage storage = new DynamicObjectStorage(clazz);
                // copy all mappings to the new storage
                dict.setDictStorage(HashingStorageAddAllToOther.executeUncached(dictStorage, storage));
            }
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_dict, toNative.execute(dict));

        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            if (!def.hasGroup() && def.hasNativeWrapperFactory()) {
                writePtrNode.write(mem, def.getNativeGroupOrField(), def.getNativeValue(slots, nullValue));
            }
        }

        // TODO properly implement 'tp_dictoffset' for builtin classes
        writeI64Node.write(mem, CFields.PyTypeObject__tp_dictoffset, GetDictOffsetNode.executeUncached(clazz));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_init, lookup(clazz, SlotMethodDef.TP_INIT));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_alloc, lookup(clazz, PyTypeObject__tp_alloc, HiddenAttr.ALLOC));
        // T___new__ is magically a staticmethod for Python types. The tp_new slot lookup
        // expects to get the function
        Object newFunction = LookupCallableSlotInMRONode.getUncached(SpecialMethodSlot.New).execute(clazz);
        if (newFunction instanceof PDecoratedMethod) {
            newFunction = ((PDecoratedMethod) newFunction).getCallable();
        }
        writePtrNode.write(mem, CFields.PyTypeObject__tp_new, ManagedMethodWrappers.createKeywords(newFunction));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_free, lookup(clazz, PyTypeObject__tp_free, HiddenAttr.FREE));
        writePtrNode.write(mem, CFields.PyTypeObject__tp_clear, lookup(clazz, PyTypeObject__tp_clear, HiddenAttr.CLEAR));
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
        writePtrNode.write(mem, CFields.PyTypeObject__tp_del, lookup(clazz, PyTypeObject__tp_del, HiddenAttr.DEL));
        writeI32Node.write(mem, CFields.PyTypeObject__tp_version_tag, 0);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_finalize, nullValue);
        writePtrNode.write(mem, CFields.PyTypeObject__tp_vectorcall, nullValue);

        if (heaptype) {
            assert (flags & TypeFlags.HEAPTYPE) != 0;
            writePtrNode.write(mem, CFields.PyHeapTypeObject__as_async, asAsync);
            writePtrNode.write(mem, CFields.PyHeapTypeObject__as_number, asNumber);
            writePtrNode.write(mem, CFields.PyHeapTypeObject__as_mapping, asMapping);
            writePtrNode.write(mem, CFields.PyHeapTypeObject__as_sequence, asSequence);
            writePtrNode.write(mem, CFields.PyHeapTypeObject__as_buffer, asBuffer);
            writePtrNode.write(mem, CFields.PyHeapTypeObject__ht_name, toNativeNewRef.execute(clazz.getName()));
            writePtrNode.write(mem, CFields.PyHeapTypeObject__ht_qualname, toNativeNewRef.execute(clazz.getQualName()));
            writePtrNode.write(mem, CFields.PyHeapTypeObject__ht_module, nullValue);
            Object dunderSlots = clazz.getAttribute(SpecialAttributeNames.T___SLOTS__);
            writePtrNode.write(mem, CFields.PyHeapTypeObject__ht_slots, dunderSlots != PNone.NO_VALUE ? toNativeNewRef.execute(dunderSlots) : nullValue);
        }
    }
}
