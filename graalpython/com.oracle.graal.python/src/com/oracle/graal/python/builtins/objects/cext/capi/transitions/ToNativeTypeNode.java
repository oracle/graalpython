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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

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
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefRawNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeRawNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
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
import com.oracle.graal.python.builtins.objects.type.TypeNodes.SetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetBasicSizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetItemSizeNodeGen;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleString.SwitchEncodingNode;

public abstract class ToNativeTypeNode {

    private static long allocatePyAsyncMethods(TpSlots slots) {
        long mem = CStructAccess.allocate(CStructs.PyAsyncMethods);
        writeGroupSlots(CFields.PyTypeObject__tp_as_async, slots, mem);
        return mem;
    }

    private static void writeGroupSlots(CFields groupField, TpSlots slots, long groupPointer) {
        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            if (def.getNativeGroupOrField() == groupField) {
                CStructAccess.writePtrField(groupPointer, def.getNativeField(), def.getNativeValue(slots, NULLPTR));
            }
        }
    }

    private static long allocatePyMappingMethods(TpSlots slots) {
        long mem = CStructAccess.allocate(CStructs.PyMappingMethods);
        writeGroupSlots(CFields.PyTypeObject__tp_as_mapping, slots, mem);
        return mem;
    }

    private static long allocatePyNumberMethods(TpSlots slots) {
        long mem = CStructAccess.allocate(CStructs.PyNumberMethods);
        writeGroupSlots(CFields.PyTypeObject__tp_as_number, slots, mem);
        return mem;
    }

    private static long allocatePySequenceMethods(TpSlots slots) {
        long mem = CStructAccess.allocate(CStructs.PyNumberMethods);
        writeGroupSlots(CFields.PyTypeObject__tp_as_sequence, slots, mem);
        return mem;
    }

    private static long lookup(PythonManagedClass clazz, CFields member, HiddenAttr hiddenName) {
        return lookupNativeMemberInMRO(clazz, member, hiddenName);
    }

    private static long lookupSize(PythonManagedClass clazz, CFields member, HiddenAttr hiddenName) {
        return lookupNativeI64MemberInMRO(clazz, member, hiddenName);
    }

    public static void initNative(PythonManagedClass clazz, long pointer) {
        initializeType(clazz, pointer, false);
    }

    static void initializeType(PythonManagedClass clazz, long mem, boolean heaptype) {
        CompilerAsserts.neverPartOfCompilation();

        TpSlots slots = GetTpSlotsNode.executeUncached(clazz);
        boolean isType = IsBuiltinClassExactProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.PythonClass);

        PythonToNativeRawNode toNative = PythonToNativeRawNode.getUncached();
        PythonToNativeNewRefRawNode toNativeNewRef = PythonToNativeNewRefRawNode.getUncached();
        GetTypeFlagsNode getTypeFlagsNode = GetTypeFlagsNodeGen.getUncached();

        PythonContext ctx = PythonContext.get(null);
        PythonLanguage language = ctx.getLanguage();

        // make this object immortal
        writeLongField(mem, PyObject__ob_refcnt, PythonObject.IMMORTAL_REFCNT);
        if (isType) {
            // self-reference
            writePtrField(mem, PyObject__ob_type, mem);
        } else {
            PythonAbstractObject promotedType = EnsurePythonObjectNode.executeUncached(ctx, GetClassNode.executeUncached(clazz));
            writePtrField(mem, PyObject__ob_type, toNative.execute(promotedType));
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
            base = PNone.NO_VALUE;
        }

        writeLongField(mem, CFields.PyVarObject__ob_size, 0L);

        TruffleString nameUtf8 = SwitchEncodingNode.getUncached().execute(clazz.getName(), Encoding.UTF_8);
        // TODO(fa): the allocated 'char *' will be free'd at context finalization. It should be
        // free'd if the type is free'd.
        TruffleString nativeUncached = nameUtf8.asNativeUncached(ctx::allocateContextMemory, Encoding.UTF_8, false, true);
        Object internalNativePointerUncached = nativeUncached.getInternalNativePointerUncached(Encoding.UTF_8);
        long namePointer = PythonUtils.coerceToLong(internalNativePointerUncached, InteropLibrary.getUncached());

        writePtrField(mem, CFields.PyTypeObject__tp_name, namePointer);
        writeLongField(mem, CFields.PyTypeObject__tp_basicsize, GetBasicSizeNode.executeUncached(clazz));
        writeLongField(mem, CFields.PyTypeObject__tp_itemsize, GetItemSizeNode.executeUncached(clazz));
        // writeStructMemberLong(mem, CFields.PyTypeObject__tp_weaklistoffset,
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
        long asAsync = slots.has_as_async() ? allocatePyAsyncMethods(slots) : NULLPTR;
        long asNumber = slots.has_as_number() ? allocatePyNumberMethods(slots) : NULLPTR;
        long asSequence = slots.has_as_sequence() ? allocatePySequenceMethods(slots) : NULLPTR;
        long asMapping = slots.has_as_mapping() ? allocatePyMappingMethods(slots) : NULLPTR;
        long asBuffer = lookup(clazz, PyTypeObject__tp_as_buffer, HiddenAttr.AS_BUFFER);
        writeLongField(mem, CFields.PyTypeObject__tp_weaklistoffset, weaklistoffset);
        writePtrField(mem, CFields.PyTypeObject__tp_dealloc, lookup(clazz, PyTypeObject__tp_dealloc, HiddenAttr.DEALLOC));
        writeLongField(mem, CFields.PyTypeObject__tp_vectorcall_offset, lookupSize(clazz, PyTypeObject__tp_vectorcall_offset, HiddenAttr.VECTORCALL_OFFSET));
        writePtrField(mem, CFields.PyTypeObject__tp_getattr, NULLPTR);
        writePtrField(mem, CFields.PyTypeObject__tp_as_async, asAsync);
        writePtrField(mem, CFields.PyTypeObject__tp_as_number, asNumber);
        writePtrField(mem, CFields.PyTypeObject__tp_as_sequence, asSequence);
        writePtrField(mem, CFields.PyTypeObject__tp_as_mapping, asMapping);
        writePtrField(mem, CFields.PyTypeObject__tp_as_buffer, asBuffer);
        writeLongField(mem, CFields.PyTypeObject__tp_flags, flags);

        // return a C string wrapper that really allocates 'char*' on TO_NATIVE
        Object docObj = clazz.getAttribute(SpecialAttributeNames.T___DOC__);
        long docPtr;
        try {
            docPtr = ctx.stringToNativeUtf8Bytes(CastToTruffleStringNode.executeUncached(docObj), true);
        } catch (CannotCastException e) {
            // if not directly a string, give up (we don't call descriptors here)
            docPtr = NULLPTR;
        }
        writePtrField(mem, CFields.PyTypeObject__tp_doc, docPtr);

        long tpTraverse = NULLPTR;
        long tpIsGc = NULLPTR;
        if ((flags & TypeFlags.HAVE_GC) != 0) {
            tpTraverse = lookup(clazz, PyTypeObject__tp_traverse, HiddenAttr.TRAVERSE);
            tpIsGc = lookup(clazz, PyTypeObject__tp_is_gc, HiddenAttr.IS_GC);
        }
        writePtrField(mem, CFields.PyTypeObject__tp_traverse, tpTraverse);
        writePtrField(mem, CFields.PyTypeObject__tp_is_gc, tpIsGc);

        writePtrField(mem, CFields.PyTypeObject__tp_methods, NULLPTR);
        writePtrField(mem, CFields.PyTypeObject__tp_members, NULLPTR);
        writePtrField(mem, CFields.PyTypeObject__tp_getset, NULLPTR);
        if (!isType) {
            // "object" base needs to be initialized explicitly in capi.c
            PythonAbstractObject promotedBase = EnsurePythonObjectNode.executeUncached(ctx, base);
            writePtrField(mem, CFields.PyTypeObject__tp_base, toNative.execute(promotedBase));
        }

        // TODO(fa): we could cache the dict instance on the class' native wrapper
        PDict dict = GetOrCreateDictNode.executeUncached(clazz);
        HashingStorage dictStorage = dict.getDictStorage();
        if (!(dictStorage instanceof DynamicObjectStorage)) {
            HashingStorage storage = new DynamicObjectStorage(clazz);
            // copy all mappings to the new storage
            dict.setDictStorage(HashingStorageAddAllToOther.executeUncached(dictStorage, storage));
        }
        assert EnsurePythonObjectNode.doesNotNeedPromotion(dict);
        writePtrField(mem, CFields.PyTypeObject__tp_dict, toNative.execute(dict));

        for (TpSlotMeta def : TpSlotMeta.VALUES) {
            if (!def.hasGroup() && def.hasNativeWrapperFactory()) {
                writePtrField(mem, def.getNativeGroupOrField(), def.getNativeValue(slots, NULLPTR));
            }
        }

        // TODO properly implement 'tp_dictoffset' for builtin classes
        writeLongField(mem, CFields.PyTypeObject__tp_dictoffset, GetDictOffsetNode.executeUncached(clazz));
        writePtrField(mem, CFields.PyTypeObject__tp_alloc, lookup(clazz, PyTypeObject__tp_alloc, HiddenAttr.ALLOC));
        writePtrField(mem, CFields.PyTypeObject__tp_free, lookup(clazz, PyTypeObject__tp_free, HiddenAttr.FREE));
        writePtrField(mem, CFields.PyTypeObject__tp_clear, lookup(clazz, PyTypeObject__tp_clear, HiddenAttr.CLEAR));
        if (clazz.basesTuple == null) {
            clazz.basesTuple = PFactory.createTuple(language, GetBaseClassesNode.executeUncached(clazz));
        }
        assert EnsurePythonObjectNode.doesNotNeedPromotion(clazz.basesTuple);
        writePtrField(mem, CFields.PyTypeObject__tp_bases, toNative.execute(clazz.basesTuple));
        if (clazz.mroStore == null) {
            clazz.mroStore = PFactory.createTuple(language, GetMroStorageNode.executeUncached(clazz));
        }
        assert EnsurePythonObjectNode.doesNotNeedPromotion(clazz.mroStore);
        writePtrField(mem, CFields.PyTypeObject__tp_mro, toNative.execute(clazz.mroStore));
        writePtrField(mem, CFields.PyTypeObject__tp_cache, NULLPTR);
        PDict subclasses = GetSubclassesNode.executeUncached(clazz);
        writePtrField(mem, CFields.PyTypeObject__tp_subclasses, toNativeNewRef.execute(subclasses));
        writePtrField(mem, CFields.PyTypeObject__tp_weaklist, NULLPTR);
        writePtrField(mem, CFields.PyTypeObject__tp_del, lookup(clazz, PyTypeObject__tp_del, HiddenAttr.DEL));
        writeIntField(mem, CFields.PyTypeObject__tp_version_tag, 0);
        writePtrField(mem, CFields.PyTypeObject__tp_finalize, NULLPTR);
        writePtrField(mem, CFields.PyTypeObject__tp_vectorcall, NULLPTR);

        if (heaptype) {
            assert (flags & TypeFlags.HEAPTYPE) != 0;
            writePtrField(mem, CFields.PyHeapTypeObject__as_async, asAsync);
            writePtrField(mem, CFields.PyHeapTypeObject__as_number, asNumber);
            writePtrField(mem, CFields.PyHeapTypeObject__as_mapping, asMapping);
            writePtrField(mem, CFields.PyHeapTypeObject__as_sequence, asSequence);
            writePtrField(mem, CFields.PyHeapTypeObject__as_buffer, asBuffer);
            writePtrField(mem, CFields.PyHeapTypeObject__ht_name, toNativeNewRef.execute(clazz.getName()));
            writePtrField(mem, CFields.PyHeapTypeObject__ht_qualname, toNativeNewRef.execute(clazz.getQualName()));
            writePtrField(mem, CFields.PyHeapTypeObject__ht_module, NULLPTR);
            Object dunderSlots = clazz.getAttribute(SpecialAttributeNames.T___SLOTS__);
            writePtrField(mem, CFields.PyHeapTypeObject__ht_slots, dunderSlots != PNone.NO_VALUE ? toNativeNewRef.execute(dunderSlots) : NULLPTR);
        }
    }

    /**
     * Creates a wrapper that uses existing native memory as native replacement object.
     */
    public static void wrapStaticTypeStructForManagedClass(PythonManagedClass clazz, long pointer) {
        /*
         * This *MUST NOT* happen, otherwise we would allocate a fresh native type store and then
         * the native pointer of the wrapper would not be equal to the corresponding native global
         * variable. E.g. 'Py_TYPE(PyBaseObjec_Type) != &PyType_Type'.
         */
        if (clazz.isNative()) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        // some values are retained from the native representation
        long basicsize = readLongField(pointer, CFields.PyTypeObject__tp_basicsize);
        if (basicsize != 0) {
            SetBasicSizeNodeGen.getUncached().execute(null, clazz, basicsize);
        }
        long itemsize = readLongField(pointer, CFields.PyTypeObject__tp_itemsize);
        if (itemsize != 0) {
            SetItemSizeNodeGen.getUncached().execute(null, clazz, itemsize);
        }
        long vectorcall_offset = readLongField(pointer, CFields.PyTypeObject__tp_vectorcall_offset);
        if (vectorcall_offset != 0) {
            HiddenAttr.WriteNode.executeUncached(clazz, HiddenAttr.VECTORCALL_OFFSET, vectorcall_offset);
        }
        long alloc_fun = readPtrField(pointer, CFields.PyTypeObject__tp_alloc);
        if (alloc_fun != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.ALLOC, alloc_fun);
        }
        long dealloc_fun = readPtrField(pointer, CFields.PyTypeObject__tp_dealloc);
        if (dealloc_fun != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.DEALLOC, dealloc_fun);
        }
        long free_fun = readPtrField(pointer, CFields.PyTypeObject__tp_free);
        if (free_fun != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.FREE, free_fun);
        }
        long traverse_fun = readPtrField(pointer, CFields.PyTypeObject__tp_traverse);
        if (traverse_fun != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.TRAVERSE, traverse_fun);
        }
        long is_gc_fun = readPtrField(pointer, CFields.PyTypeObject__tp_is_gc);
        if (is_gc_fun != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.IS_GC, is_gc_fun);
        }
        long clear_fun = readPtrField(pointer, CFields.PyTypeObject__tp_clear);
        if (clear_fun != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.CLEAR, clear_fun);
        }
        long as_buffer = readPtrField(pointer, CFields.PyTypeObject__tp_as_buffer);
        if (as_buffer != NULLPTR) {
            HiddenAttr.WriteLongNode.executeUncached(clazz, HiddenAttr.AS_BUFFER, as_buffer);
        }

        /*
         * Initialize type flags: If the native type, we are wrapping, already defines 'tp_flags',
         * we use it because those must stay consistent with slots. For example, native
         * tp_new/tp_alloc/tp_dealloc/tp_free functions must be consistent with
         * 'Py_TPFLAGS_HAVE_GC'.
         */
        long flags = readLongField(pointer, CFields.PyTypeObject__tp_flags);
        if (flags == 0) {
            flags = GetTypeFlagsNode.executeUncached(clazz) | TypeFlags.READY | TypeFlags.IMMUTABLETYPE;
        }
        SetTypeFlagsNode.executeUncached(clazz, flags);

        // TODO(fa): revisit this: static classes are immortal; we don't need a
        // PythonObjectReference
        CApiTransitions.createReference(clazz, pointer, false);
        assert clazz.isNative();
    }
}
