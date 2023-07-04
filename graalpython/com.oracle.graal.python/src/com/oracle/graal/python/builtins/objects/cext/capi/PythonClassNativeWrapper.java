/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.ToNativeTypeNode.InitializeTypeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ToNativeTypeNodeGen.InitializeTypeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.SetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetBasicSizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetItemSizeNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Used to wrap {@link PythonClass} when used in native code. This wrapper mimics the correct shape
 * of the corresponding native type {@code struct _typeobject}.
 */
public final class PythonClassNativeWrapper extends PythonReplacingNativeWrapper {
    private final CStringWrapper nameWrapper;

    private PythonClassNativeWrapper(PythonManagedClass object, TruffleString name) {
        super(object);
        this.nameWrapper = new CStringWrapper(name);
    }

    public CStringWrapper getNameWrapper() {
        return nameWrapper;
    }

    public static PythonClassNativeWrapper wrap(PythonManagedClass obj, TruffleString name) {
        // important: native wrappers are cached
        PythonClassNativeWrapper nativeWrapper = obj.getClassNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PythonClassNativeWrapper(obj, name);
            obj.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    /**
     * Creates a wrapper that uses an existing native object as native replacement object.
     */
    public static void wrapNative(PythonManagedClass clazz, TruffleString name, Object pointer) {
        InitializeTypeNode initializeNode = InitializeTypeNodeGen.getUncached();
        // important: native wrappers are cached
        assert clazz.getClassNativeWrapper() == null;
        PythonClassNativeWrapper wrapper = new PythonClassNativeWrapper(clazz, name);
        clazz.setNativeWrapper(wrapper);

        CStructAccess.ReadI64Node readI64 = CStructAccess.ReadI64Node.getUncached();
        CStructAccess.ReadPointerNode readPointer = CStructAccess.ReadPointerNode.getUncached();
        WriteAttributeToObjectNode writeAttr = WriteAttributeToObjectNode.getUncached();
        InteropLibrary lib = InteropLibrary.getUncached();

        // some values are retained from the native representation
        long basicsize = readI64.read(pointer, CFields.PyTypeObject__tp_basicsize);
        if (basicsize != 0) {
            SetBasicSizeNodeGen.getUncached().execute(null, clazz, basicsize);
        }
        long itemsize = readI64.read(pointer, CFields.PyTypeObject__tp_itemsize);
        if (itemsize != 0) {
            SetItemSizeNodeGen.getUncached().execute(null, clazz, itemsize);
        }
        long vectorcall_offset = readI64.read(pointer, CFields.PyTypeObject__tp_vectorcall_offset);
        if (vectorcall_offset != 0) {
            writeAttr.execute(clazz, TypeBuiltins.TYPE_VECTORCALL_OFFSET, vectorcall_offset);
        }
        Object alloc_fun = readPointer.read(pointer, CFields.PyTypeObject__tp_alloc);
        if (!PGuards.isNullOrZero(alloc_fun, lib)) {
            writeAttr.execute(clazz, TypeBuiltins.TYPE_ALLOC, alloc_fun);
        }
        Object dealloc_fun = readPointer.read(pointer, CFields.PyTypeObject__tp_dealloc);
        if (!PGuards.isNullOrZero(dealloc_fun, lib)) {
            writeAttr.execute(clazz, TypeBuiltins.TYPE_DEALLOC, dealloc_fun);
        }
        Object free_fun = readPointer.read(pointer, CFields.PyTypeObject__tp_free);
        if (!PGuards.isNullOrZero(free_fun, lib)) {
            writeAttr.execute(clazz, TypeBuiltins.TYPE_FREE, free_fun);
        }
        Object as_buffer = readPointer.read(pointer, CFields.PyTypeObject__tp_as_buffer);
        if (!PGuards.isNullOrZero(as_buffer, lib)) {
            writeAttr.execute(clazz, TypeBuiltins.TYPE_AS_BUFFER, as_buffer);
        }

        // initialize flags:
        long flags = GetTypeFlagsNode.getUncached().execute(clazz);
        flags |= TypeFlags.READY | TypeFlags.IMMUTABLETYPE;
        SetTypeFlagsNode.getUncached().execute(clazz, flags);

        initializeNode.execute(wrapper, pointer);
        wrapper.setReplacement(pointer, lib);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonUtils.formatJString("PythonClassNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
    }

    @Override
    protected Object allocateReplacememtObject() {
        return ToNativeTypeNodeGen.getUncached().execute(this);
    }
}
