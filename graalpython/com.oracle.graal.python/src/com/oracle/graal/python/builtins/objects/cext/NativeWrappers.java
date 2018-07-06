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
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;

public abstract class NativeWrappers {

    public abstract static class PythonNativeWrapper implements TruffleObject {

        private Object nativePointer;

        public abstract Object getDelegate();

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
            return o instanceof PythonNativeWrapper;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return PythonObjectNativeWrapperMRForeign.ACCESS;
        }
    }

    /**
     * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
     * correct shape of the corresponding native type {@code struct _object}.
     */
    public static class PythonObjectNativeWrapper extends PythonNativeWrapper {
        private static final Layout OBJECT_LAYOUT = Layout.newLayout().build();
        private static final Shape SHAPE = OBJECT_LAYOUT.createShape(new ObjectType());

        private final PythonAbstractObject pythonObject;
        private PythonObjectDictStorage nativeMemberStore;

        public PythonObjectNativeWrapper(PythonAbstractObject object) {
            this.pythonObject = object;
        }

        public PythonAbstractObject getPythonObject() {
            return pythonObject;
        }

        public static boolean isInstance(TruffleObject o) {
            return o instanceof PythonObjectNativeWrapper;
        }

        public static PythonObjectNativeWrapper wrap(PythonAbstractObject obj) {
            // important: native wrappers are cached
            PythonObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (nativeWrapper == null) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        @Override
        public Object getDelegate() {
            return pythonObject;
        }

        public PythonObjectDictStorage createNativeMemberStore() {
            if (nativeMemberStore == null) {
                nativeMemberStore = new PythonObjectDictStorage(SHAPE.newInstance());
            }
            return nativeMemberStore;
        }

        public PythonObjectDictStorage getNativeMemberStore() {
            return nativeMemberStore;
        }

        @Override
        public String toString() {
            return String.format("PythonObjectNativeWrapper(%s, isNative=%s)", pythonObject, isNative());
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
     * Wraps a sequence object (like a list) such that it behaves like a bare C array.
     */
    public static class PySequenceArrayWrapper extends PythonNativeWrapper {

        private final Object delegate;

        /** Number of bytes that constitute a single element. */
        private final int elementAccessSize;

        public PySequenceArrayWrapper(Object delegate, int elementAccessSize) {
            this.delegate = delegate;
            this.elementAccessSize = elementAccessSize;
        }

        @Override
        public Object getDelegate() {
            return delegate;
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
        private final TruffleObject foreignObject;

        public TruffleObjectNativeWrapper(TruffleObject foreignObject) {
            this.foreignObject = foreignObject;
        }

        public TruffleObject getForeignObject() {
            return foreignObject;
        }

        public static TruffleObjectNativeWrapper wrap(TruffleObject foreignObject) {
            assert !(foreignObject instanceof PythonNativeWrapper) : "attempting to wrap a native wrapper";
            return new TruffleObjectNativeWrapper(foreignObject);
        }

        @Override
        public Object getDelegate() {
            return foreignObject;
        }
    }

    abstract static class PyUnicodeWrapper extends PythonNativeWrapper {
        private final PString delegate;

        public PyUnicodeWrapper(PString delegate) {
            this.delegate = delegate;
        }

        @Override
        public PString getDelegate() {
            return delegate;
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
