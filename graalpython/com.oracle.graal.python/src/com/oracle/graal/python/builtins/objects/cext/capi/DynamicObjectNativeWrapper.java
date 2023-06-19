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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__exports;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructs.PyMemoryViewObject;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AllToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.MaterializeDelegateNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(InteropLibrary.class)
public abstract class DynamicObjectNativeWrapper extends PythonNativeWrapper {

    public DynamicObjectNativeWrapper() {
    }

    public DynamicObjectNativeWrapper(Object delegate) {
        super(delegate);
    }

    @ExportMessage
    protected boolean isNull() {
        return getDelegate() == PNone.NO_VALUE;
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    protected Object execute(Object[] arguments,
                    @Cached PythonAbstractObject.PExecuteNode executeNode,
                    @Cached AllToJavaNode allToJavaNode,
                    @Cached NativeToPythonNode selfToJava,
                    @Cached PythonToNativeNewRefNode toNewRefNode,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object[] converted;
            Object function = getDelegate();
            if (function instanceof PBuiltinFunction && CExtContext.isMethNoArgs(((PBuiltinFunction) function).getFlags()) && arguments.length == 2) {
                /*
                 * The C function signature for METH_NOARGS is: methNoArgs(PyObject* self, PyObject*
                 * dummy); So we need to trim away the dummy argument, otherwise we will get an
                 * error.
                 */
                converted = new Object[]{selfToJava.execute(arguments[0])};
            } else if (function instanceof PBuiltinFunction && CExtContext.isMethVarargs(((PBuiltinFunction) function).getFlags()) && arguments.length == 2) {
                converted = allToJavaNode.execute(arguments);
                assert converted[1] instanceof PTuple;
                SequenceStorage argsStorage = ((PTuple) converted[1]).getSequenceStorage();
                Object[] wrapArgs = new Object[argsStorage.length() + 1];
                wrapArgs[0] = converted[0];
                PythonUtils.arraycopy(argsStorage.getInternalArray(), 0, wrapArgs, 1, argsStorage.length());
                converted = wrapArgs;
            } else {
                converted = allToJavaNode.execute(arguments);
            }

            Object result = executeNode.execute(function, converted);

            /*
             * If a native wrapper is executed, we directly wrap some managed function and assume
             * that new references are returned. So, we increase the ref count for each native
             * object here.
             */
            return toNewRefNode.execute(result);
        } catch (PException e) {
            transformExceptionToNativeNode.execute(e);
            return toNewRefNode.execute(PythonContext.get(gil).getNativeNull());
        } finally {
            gil.release(mustRelease);
        }
    }

    @GenerateUncached
    abstract static class IntArrayToNativePySSizeArray extends Node {
        public abstract Object execute(int[] array);

        @Specialization
        static Object getShape(int[] intArray,
                        @Cached CStructAccess.AllocateNode alloc,
                        @Cached CStructAccess.WriteLongNode write) {
            Object mem = alloc.alloc(intArray.length * Long.BYTES);
            for (int i = 0; i < intArray.length; i++) {
                write.write(mem, intArray[i]);
            }
            return mem;
        }
    }

    // TO NATIVE, IS POINTER, AS POINTER
    @GenerateUncached
    abstract static class ToNativeNode extends Node {
        public abstract void execute(PythonNativeWrapper obj);

        static boolean isMemoryView(PythonNativeWrapper w) {
            return w.getDelegate() instanceof PMemoryView;
        }

        @Specialization(guards = "!isMemoryView(obj)")
        static void doPythonNativeWrapper(PythonNativeWrapper obj) {
            if (!obj.isNative()) {
                CApiTransitions.firstToNative(obj);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "isMemoryView(obj)")
        void doPythonNativeWrapper(PythonNativeWrapper obj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClass,
                        @Cached PythonToNativeNewRefNode toNative,
                        @Cached CStructAccess.AllocateNode allocNode,
                        @Cached CStructAccess.GetElementPtrNode getElementNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.WriteLongNode writeI64Node,
                        @Cached CStructAccess.WriteIntNode writeI32Node,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceNodes.SetSequenceStorageNode setStorage,
                        @Cached CExtNodes.PointerAddNode pointerAddNode,
                        @Cached PySequenceArrayWrapper.ToNativeStorageNode toNativeStorageNode,
                        @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray,
                        @Cached CExtNodes.AsCharPointerNode asCharPointerNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (!obj.isNative()) {
                PMemoryView object = (PMemoryView) obj.getDelegate();
                obj.setRefCount(Long.MAX_VALUE / 2); // make this object immortal

                Object mem = allocNode.alloc(PyMemoryViewObject);
                writeI64Node.write(mem, PyObject__ob_refcnt, 0x1000); // TODO: immortal for now
                writePointerNode.write(mem, PyObject__ob_type, toNative.execute(getClass.execute(inliningTarget, object)));
                writeI32Node.write(mem, PyMemoryViewObject__flags, object.getFlags());
                writeI64Node.write(mem, PyMemoryViewObject__exports, object.getExports().get());
                // TODO: ignoring mbuf, hash and weakreflist for now

                Object view = getElementNode.getElementPtr(mem, CFields.PyMemoryViewObject__view);

                Object buf;
                if (object.getBufferPointer() == null) {
                    // TODO GR-21120: Add support for PArray
                    PSequence owner = (PSequence) object.getOwner();
                    NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(getStorage.execute(owner), owner instanceof PBytesLike);
                    if (nativeStorage == null) {
                        throw CompilerDirectives.shouldNotReachHere("cannot allocate native storage");
                    }
                    setStorage.execute(inliningTarget, owner, nativeStorage);
                    Object pointer = nativeStorage.getPtr();
                    if (object.getOffset() == 0) {
                        buf = pointer;
                    } else {
                        buf = pointerAddNode.execute(pointer, object.getOffset());
                    }
                } else {
                    if (object.getOffset() == 0) {
                        buf = object.getBufferPointer();
                    } else {
                        buf = pointerAddNode.execute(object.getBufferPointer(), object.getOffset());
                    }
                }
                writePointerNode.write(view, CFields.Py_buffer__buf, buf);

                if (object.getOwner() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__obj, toNative.execute(object.getOwner()));
                }
                writeI64Node.write(view, CFields.Py_buffer__len, object.getLength());
                writeI64Node.write(view, CFields.Py_buffer__itemsize, object.getItemSize());
                writeI32Node.write(view, CFields.Py_buffer__readonly, PInt.intValue(object.isReadOnly()));
                writeI32Node.write(view, CFields.Py_buffer__ndim, object.getDimensions());
                if (object.getFormatString() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__format, asCharPointerNode.execute(object.getFormatString()));
                }
                if (object.getBufferShape() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__shape, intArrayToNativePySSizeArray.execute(object.getBufferShape()));
                }
                if (object.getBufferStrides() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__strides, intArrayToNativePySSizeArray.execute(object.getBufferStrides()));
                }
                if (object.getBufferSuboffsets() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__suboffsets, intArrayToNativePySSizeArray.execute(object.getBufferSuboffsets()));
                }

                long ptr = coerceToLong(mem, lib);
                CApiTransitions.firstToNative(obj, ptr);
            }
        }
    }

    /**
     * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
     * correct shape of the corresponding native type {@code struct _object}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class PythonObjectNativeWrapper extends DynamicObjectNativeWrapper {

        public PythonObjectNativeWrapper(PythonAbstractObject object) {
            super(object);
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
            return PythonUtils.formatJString("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

        public static final byte PRIMITIVE_STATE_BOOL = 1;
        public static final byte PRIMITIVE_STATE_INT = 1 << 2;
        public static final byte PRIMITIVE_STATE_LONG = 1 << 3;
        public static final byte PRIMITIVE_STATE_DOUBLE = 1 << 4;

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

        public boolean isInt() {
            return state == PRIMITIVE_STATE_INT;
        }

        public boolean isLong() {
            return state == PRIMITIVE_STATE_LONG;
        }

        public boolean isDouble() {
            return state == PRIMITIVE_STATE_DOUBLE;
        }

        public boolean isIntLike() {
            return (state & (PRIMITIVE_STATE_INT | PRIMITIVE_STATE_LONG)) != 0;
        }

        public boolean isSubtypeOfInt() {
            return !isDouble();
        }

        // this method exists just for readability
        public Object getMaterializedObject() {
            return getDelegate();
        }

        // this method exists just for readability
        public void setMaterializedObject(Object materializedPrimitive) {
            setDelegate(materializedPrimitive);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            CompilerAsserts.neverPartOfCompilation();

            PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
            if (other.state == state && other.value == value && other.dvalue == dvalue) {
                // n.b.: in the equals, we also require the native pointer to be the same. The
                // reason for this is to avoid native pointer sharing. Handles are shared if the
                // objects are equal but in this case we must not share because otherwise we would
                // mess up the reference counts.
                return getNativePointer() == other.getNativePointer();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (Long.hashCode(value) ^ Long.hashCode(Double.doubleToRawLongBits(dvalue)) ^ state);
        }

        @Override
        public String toString() {
            String typeName;
            if (isIntLike()) {
                typeName = "int";
            } else if (isDouble()) {
                typeName = "float";
            } else if (isBool()) {
                typeName = "bool";
            } else {
                typeName = "unknown";
            }
            return "PrimitiveNativeWrapper(" + typeName + "(" + value + ")" + ')';
        }

        public static PrimitiveNativeWrapper createBool(boolean val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BOOL, PInt.intValue(val));
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

        @ExportMessage
        @TruffleBoundary
        int identityHashCode() {
            int val = Byte.hashCode(state) ^ Long.hashCode(value);
            if (Double.isNaN(dvalue)) {
                return val;
            } else {
                return val ^ Double.hashCode(dvalue);
            }
        }

        @ExportMessage
        TriState isIdenticalOrUndefined(Object obj) {
            if (obj instanceof PrimitiveNativeWrapper) {
                /*
                 * This basically emulates singletons for boxed values. However, we need to do so to
                 * preserve the invariant that storing an object into a list and getting it out (in
                 * the same critical region) returns the same object.
                 */
                PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
                if (other.state == state && other.value == value && (other.dvalue == dvalue || Double.isNaN(dvalue) && Double.isNaN(other.dvalue))) {
                    /*
                     * n.b.: in the equals, we also require the native pointer to be the same. The
                     * reason for this is to avoid native pointer sharing. Handles are shared if the
                     * objects are equal but in this case we must not share because otherwise we
                     * would mess up the reference counts.
                     */
                    return TriState.valueOf(this.getNativePointer() == other.getNativePointer());
                }
                return TriState.FALSE;
            } else {
                return TriState.UNDEFINED;
            }
        }

        @ExportMessage
        abstract static class AsPointer {

            @Specialization(guards = {"obj.isBool()", "!obj.isNative()"})
            static long doBoolNotNative(PrimitiveNativeWrapper obj,
                            @Cached MaterializeDelegateNode materializeNode) {
                // special case for True and False singletons
                PInt boxed = (PInt) materializeNode.execute(obj);
                assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
                return obj.getNativePointer();
            }

            @Specialization(guards = {"!obj.isBool() || obj.isNative()"})
            static long doBoolNative(PrimitiveNativeWrapper obj) {
                return obj.getNativePointer();
            }
        }
    }

    @ExportMessage
    protected boolean isPointer() {
        return getDelegate() == PNone.NO_VALUE || isNative();
    }

    @ExportMessage
    protected long asPointer() {
        return getDelegate() == PNone.NO_VALUE ? 0L : getNativePointer();
    }

    @ExportMessage
    protected void toNative(
                    @Cached ToNativeNode toNativeNode) {
        if (getDelegate() != PNone.NO_VALUE) {
            toNativeNode.execute(this);
        }
    }
}
