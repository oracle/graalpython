/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ensurePointerUncached;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readPtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.calloc;

import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.FirstToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.SetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetBasicSizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetItemSizeNodeGen;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Used to wrap {@link PythonClass} when used in native code. This wrapper is replacing and the
 * replacement mimics the correct shape of the corresponding native type {@code struct _typeobject}.
 */
public final class PythonClassNativeWrapper extends PythonAbstractObjectNativeWrapper {
    private final CStringWrapper nameWrapper;
    private long replacement;

    private PythonClassNativeWrapper(PythonManagedClass object, TruffleString name, TruffleString.SwitchEncodingNode switchEncoding) {
        super(object);
        this.nameWrapper = new CStringWrapper(switchEncoding.execute(name, TruffleString.Encoding.UTF_8), TruffleString.Encoding.UTF_8);
    }

    @TruffleBoundary
    public long getNameWrapper() {
        return ensurePointerUncached(nameWrapper);
    }

    public static PythonClassNativeWrapper wrap(PythonManagedClass obj, TruffleString name, TruffleString.SwitchEncodingNode switchEncoding) {
        // important: native wrappers are cached
        PythonClassNativeWrapper nativeWrapper = obj.getClassNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PythonClassNativeWrapper(obj, name, switchEncoding);
            obj.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    /**
     * Creates a wrapper that uses existing native memory as native replacement object.
     */
    public static void wrapStaticTypeStructForManagedClass(PythonManagedClass clazz, TruffleString name, long pointer) {
        /*
         * This *MUST NOT* happen, otherwise we would allocate a fresh native type store and then
         * the native pointer of the wrapper would not be equal to the corresponding native global
         * variable. E.g. 'Py_TYPE(PyBaseObjec_Type) != &PyType_Type'.
         */
        if (clazz.getNativeWrapper() != null) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        PythonClassNativeWrapper wrapper = new PythonClassNativeWrapper(clazz, name, TruffleString.SwitchEncodingNode.getUncached());
        clazz.setNativeWrapper(wrapper);

        InteropLibrary lib = InteropLibrary.getUncached();

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

        /*
         * It's important that we first register the pointer before initializing the type (see
         * 'getReplacement' for more explanation).
         */
        wrapper.replacement = pointer;
        CApiTransitions.createReference(wrapper, pointer, false);
    }

    public static void initNative(PythonManagedClass clazz, long pointer) {
        PythonClassNativeWrapper classNativeWrapper = clazz.getClassNativeWrapper();
        if (classNativeWrapper == null) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        ToNativeTypeNode.initializeType(classNativeWrapper, pointer, false);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonUtils.formatJString("PythonClassNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
    }

    public long getReplacement() {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, replacement == NULLPTR)) {
            initializeReplacement();
        }
        return replacement;
    }

    @TruffleBoundary
    private void initializeReplacement() {
        /*
         * Note: it's important that we first allocate the empty 'PyTypeStruct' and register it to
         * the wrapper before we do the type's initialization. Otherwise, we will run into an
         * infinite recursion because, e.g., some type uses 'None', so the 'NoneType' will be
         * transformed to native but 'NoneType' may have some field that is initialized with 'None'
         * and so on.
         *
         * If we first set the empty struct and initialize it afterward, everything is fine.
         */
        PythonManagedClass clazz = (PythonManagedClass) getDelegate();
        boolean heaptype = (GetTypeFlagsNode.executeUncached(clazz) & TypeFlags.HEAPTYPE) != 0;
        long size = CStructs.PyTypeObject.size();
        if (heaptype) {
            size = CStructs.PyHeapTypeObject.size();
            if (GetClassNode.executeUncached(clazz) instanceof PythonAbstractNativeObject nativeMetatype) {
                // TODO should call the metatype's tp_alloc
                size = TypeNodes.GetBasicSizeNode.executeUncached(nativeMetatype);
            }
        }
        /*
         * For built-in classes, we can always create a strong reference. Those classes are always
         * reachable and a weak reference is not necessary. We will release the native memory in the
         * C API finalization.
         */
        boolean isBuiltinClass = clazz instanceof PythonBuiltinClass;

        long ptr = calloc(size);
        replacement = ptr;
        CApiTransitions.createReference(this, ptr, true);
        ToNativeTypeNode.initializeType(this, ptr, heaptype);
        assert !isBuiltinClass || getRefCount() == IMMORTAL_REFCNT;
    }

    /**
     * Does not initialize the replacement.
     */
    public long getReplacementIfInitialized() {
        return replacement;
    }

    @Ignore
    @Override
    public void toNative(boolean newRef, Node inliningTarget, FirstToNativeNode firstToNativeNode) {
        if (!isNative()) {
            /*
             * This is a wrapper that is eagerly transformed to its C layout in the Python-to-native
             * transition. Therefore, the wrapper is expected to be native already.
             */
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
