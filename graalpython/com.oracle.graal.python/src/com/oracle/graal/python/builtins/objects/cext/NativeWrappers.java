/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class NativeWrappers {

    public abstract static class PythonNativeWrapper implements TruffleObject {

        private Object delegate;
        private Object nativePointer;

        public PythonNativeWrapper() {
        }

        public PythonNativeWrapper(Object delegate) {
            this.delegate = delegate;
        }

        public final Object getDelegate() {
            return delegate;
        }

        protected void setDelegate(Object delegate) {
            this.delegate = delegate;
        }

        public Object getNativePointer() {
            return nativePointer;
        }

        public void setNativePointer(Object nativePointer) {
            // we should set the pointer just once
            assert this.nativePointer == null || this.nativePointer.equals(nativePointer) || nativePointer == null;
            this.nativePointer = nativePointer;
        }

        public boolean isNative() {
            return nativePointer != null;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof DynamicObjectNativeWrapper || o instanceof TruffleObjectNativeWrapper;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PythonObjectNativeWrapperMRForeign.ACCESS;
        }
    }

    public abstract static class DynamicObjectNativeWrapper extends PythonNativeWrapper {
        private static final Layout OBJECT_LAYOUT = Layout.newLayout().build();
        private static final Shape SHAPE = OBJECT_LAYOUT.createShape(new ObjectType());

        private PythonObjectDictStorage nativeMemberStore;

        public DynamicObjectNativeWrapper() {
        }

        public DynamicObjectNativeWrapper(Object delegate) {
            super(delegate);
        }

        public PythonObjectDictStorage createNativeMemberStore() {
            return createNativeMemberStore(null);
        }

        public PythonObjectDictStorage createNativeMemberStore(Assumption dictStableAssumption) {
            if (nativeMemberStore == null) {
                nativeMemberStore = new PythonObjectDictStorage(SHAPE.newInstance(), dictStableAssumption);
            }
            return nativeMemberStore;
        }

        public PythonObjectDictStorage getNativeMemberStore() {
            return nativeMemberStore;
        }
    }

    /**
     * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
     * correct shape of the corresponding native type {@code struct _object}.
     */
    public static class PythonObjectNativeWrapper extends DynamicObjectNativeWrapper {

        public PythonObjectNativeWrapper(PythonAbstractObject object) {
            super(object);
        }

        public PythonAbstractObject getPythonObject() {
            return (PythonAbstractObject) getDelegate();
        }

        public static DynamicObjectNativeWrapper wrap(PythonAbstractObject obj, ConditionProfile noWrapperProfile) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
        }
    }

    public static class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

        public static final byte PRIMITIVE_STATE_BOOL = 0b00000001;
        public static final byte PRIMITIVE_STATE_BYTE = 0b00000010;
        public static final byte PRIMITIVE_STATE_INT = 0b00000100;
        public static final byte PRIMITIVE_STATE_LONG = 0b00001000;
        public static final byte PRIMITIVE_STATE_DOUBLE = 0b00001000;

        private final byte state;
        private final long value;
        private final double dvalue;

        private PrimitiveNativeWrapper(byte state, long value) {
            assert state != PRIMITIVE_STATE_DOUBLE;
            this.state = state;
            this.value = value;
            this.dvalue = 0.0;
        }

        private PrimitiveNativeWrapper(double dvalue) {
            this.state = PRIMITIVE_STATE_DOUBLE;
            this.value = 0;
            this.dvalue = dvalue;
        }

        public byte getState() {
            return state;
        }

        public boolean getBool() {
            return value != 0;
        }

        public byte getByte() {
            return (byte) value;
        }

        public int getInt() {
            return (int) value;
        }

        public long getLong() {
            return value;
        }

        public double getDouble() {
            return dvalue;
        }

        public boolean isBool() {
            return state == PRIMITIVE_STATE_BOOL;
        }

        public boolean isByte() {
            return state == PRIMITIVE_STATE_BYTE;
        }

        public boolean isInt() {
            return state == PRIMITIVE_STATE_INT;
        }

        public boolean isLong() {
            return state == PRIMITIVE_STATE_LONG;
        }

        public boolean isDouble() {
            return state == PRIMITIVE_STATE_DOUBLE;
        }

        // this method exists just for readability
        public Object getMaterializedObject() {
            return getDelegate();
        }

        // this method exists just for readability
        public void setMaterializedObject(Object materializedPrimitive) {
            setDelegate(materializedPrimitive);
        }

        public static PrimitiveNativeWrapper createBool(boolean val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BOOL, PInt.intValue(val));
        }

        public static PrimitiveNativeWrapper createByte(byte val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BYTE, val);
        }

        public static PrimitiveNativeWrapper createInt(int val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_INT, val);
        }

        public static PrimitiveNativeWrapper createLong(long val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_LONG, val);
        }

        public static PrimitiveNativeWrapper createDouble(double val) {
            return new PrimitiveNativeWrapper(val);
        }
    }

    /**
     * Used to wrap {@link PythonClass} when used in native code. This wrapper mimics the correct
     * shape of the corresponding native type {@code struct _typeobject}.
     */
    public static class PythonClassNativeWrapper extends PythonObjectNativeWrapper {
        private final CStringWrapper nameWrapper;
        private Object getBufferProc;
        private Object releaseBufferProc;

        public PythonClassNativeWrapper(PythonClass object) {
            super(object);
            this.nameWrapper = new CStringWrapper(object.getName());
        }

        public CStringWrapper getNameWrapper() {
            return nameWrapper;
        }

        public Object getGetBufferProc() {
            return getBufferProc;
        }

        public void setGetBufferProc(Object getBufferProc) {
            this.getBufferProc = getBufferProc;
        }

        public Object getReleaseBufferProc() {
            return releaseBufferProc;
        }

        public void setReleaseBufferProc(Object releaseBufferProc) {
            this.releaseBufferProc = releaseBufferProc;
        }

        public static PythonClassNativeWrapper wrap(PythonClass obj) {
            // important: native wrappers are cached
            PythonClassNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (nativeWrapper == null) {
                nativeWrapper = new PythonClassNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            return String.format("PythonClassNativeWrapper(%s, isNative=%s)", getPythonObject(), isNative());
        }
    }

    /**
     * Used to wrap {@link PythonClass} just for the time when a natively defined type is processed
     * in {@code PyType_Ready} and we need to pass the mirroring managed class to native to marry
     * these two objects.
     */
    public static class PythonClassInitNativeWrapper extends PythonObjectNativeWrapper {

        public PythonClassInitNativeWrapper(PythonClass object) {
            super(object);
        }

        @Override
        public String toString() {
            return String.format("PythonClassNativeInitWrapper(%s, isNative=%s)", getPythonObject(), isNative());
        }
    }

    /**
     * Wraps a sequence object (like a list) such that it behaves like a bare C array.
     */
    public static class PySequenceArrayWrapper extends PythonNativeWrapper {

        /** Number of bytes that constitute a single element. */
        private final int elementAccessSize;

        public PySequenceArrayWrapper(Object delegate, int elementAccessSize) {
            super(delegate);
            this.elementAccessSize = elementAccessSize;
        }

        public int getElementAccessSize() {
            return elementAccessSize;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof PySequenceArrayWrapper;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PySequenceArrayWrapperMRForeign.ACCESS;
        }
    }

    public static class TruffleObjectNativeWrapper extends PythonNativeWrapper {

        public TruffleObjectNativeWrapper(TruffleObject foreignObject) {
            super(foreignObject);
        }

        public static TruffleObjectNativeWrapper wrap(TruffleObject foreignObject) {
            assert !(foreignObject instanceof PythonNativeWrapper) : "attempting to wrap a native wrapper";
            return new TruffleObjectNativeWrapper(foreignObject);
        }
    }

    abstract static class PyUnicodeWrapper extends PythonNativeWrapper {

        public PyUnicodeWrapper(PString delegate) {
            super(delegate);
        }

        public PString getPString() {
            return (PString) getDelegate();
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PyUnicodeWrapperMRForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof PyUnicodeWrapper;
        }
    }

    /**
     * A native wrapper for the {@code data} member of {@code PyUnicodeObject}.
     */
    public static class PyUnicodeData extends PyUnicodeWrapper {
        public PyUnicodeData(PString delegate) {
            super(delegate);
        }
    }

    /**
     * A native wrapper for the {@code state} member of {@code PyASCIIObject}.
     */
    public static class PyUnicodeState extends PyUnicodeWrapper {

        public PyUnicodeState(PString delegate) {
            super(delegate);
        }

    }
}
