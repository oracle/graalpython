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
import static com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.stringToNativeUtf8BytesUncached;
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
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefRawNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeRawNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.dict.PDict;
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
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;

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

    static void initializeType(PythonClassNativeWrapper obj, long mem, boolean heaptype) {
        CompilerAsserts.neverPartOfCompilation();

        PythonManagedClass clazz = (PythonManagedClass) obj.getDelegate();
        TpSlots slots = GetTpSlotsNode.executeUncached(clazz);
        boolean isType = IsBuiltinClassExactProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.PythonClass);

        PythonToNativeRawNode toNative = PythonToNativeRawNode.getUncached();
        PythonToNativeNewRefRawNode toNativeNewRef = PythonToNativeNewRefRawNode.getUncached();
        GetTypeFlagsNode getTypeFlagsNode = GetTypeFlagsNodeGen.getUncached();

        PythonContext ctx = PythonContext.get(null);
        PythonLanguage language = ctx.getLanguage();

        // make this object immortal
        writeLongField(mem, PyObject__ob_refcnt, PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT);
        if (isType) {
            // self-reference
            writePtrField(mem, PyObject__ob_type, mem);
        } else {
            writePtrField(mem, PyObject__ob_type, toNative.execute(GetClassNode.executeUncached(clazz)));
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

        writeLongField(mem, CFields.PyVarObject__ob_size, 0L);

        writePtrField(mem, CFields.PyTypeObject__tp_name, clazz.getClassNativeWrapper().getNameWrapper());
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
            docPtr = stringToNativeUtf8BytesUncached(CastToTruffleStringNode.executeUncached(docObj));
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
            writePtrField(mem, CFields.PyTypeObject__tp_base, toNative.execute(base));
        }

        // TODO(fa): we could cache the dict instance on the class' native wrapper
        PDict dict = GetOrCreateDictNode.executeUncached(clazz);
        HashingStorage dictStorage = dict.getDictStorage();
        if (!(dictStorage instanceof DynamicObjectStorage)) {
            HashingStorage storage = new DynamicObjectStorage(clazz);
            // copy all mappings to the new storage
            dict.setDictStorage(HashingStorageAddAllToOther.executeUncached(dictStorage, storage));
        }
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
        writePtrField(mem, CFields.PyTypeObject__tp_bases, toNative.execute(clazz.basesTuple));
        if (clazz.mroStore == null) {
            clazz.mroStore = PFactory.createTuple(language, GetMroStorageNode.executeUncached(clazz));
        }
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
}
