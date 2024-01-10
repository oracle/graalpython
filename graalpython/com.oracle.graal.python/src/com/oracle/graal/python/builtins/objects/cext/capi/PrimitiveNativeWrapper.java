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

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.MaterializeDelegateNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(InteropLibrary.class)
public final class PrimitiveNativeWrapper extends PythonAbstractObjectNativeWrapper {

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
             * preserve the invariant that storing an object into a list and getting it out (in the
             * same critical region) returns the same object.
             */
            PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
            if (other.state == state && other.value == value && (other.dvalue == dvalue || Double.isNaN(dvalue) && Double.isNaN(other.dvalue))) {
                /*
                 * n.b.: in the equals, we also require the native pointer to be the same. The
                 * reason for this is to avoid native pointer sharing. Handles are shared if the
                 * objects are equal but in this case we must not share because otherwise we would
                 * mess up the reference counts.
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
                        @Bind("this") Node inliningTarget,
                        @Cached MaterializeDelegateNode materializeNode) {
            // special case for True and False singletons
            PInt boxed = (PInt) materializeNode.execute(inliningTarget, obj);
            assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
            return obj.getNativePointer();
        }

        @Specialization(guards = {"!obj.isBool() || obj.isNative()"})
        static long doBoolNative(PrimitiveNativeWrapper obj) {
            return obj.getNativePointer();
        }
    }

    @ExportMessage
    boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    void toNative(
                    @Bind("$node") Node inliningTarget,
                    @Cached CApiTransitions.FirstToNativeNode firstToNativeNode) {
        if (!isNative()) {
            // small int values are cached and will be immortal
            boolean immortal = isIntLike() && CApiGuards.isSmallLong(value);
            // if this wrapper wraps a small int value, this wrapper is one of the cached primitive
            // native wrappers
            assert !immortal || (PythonContext.get(inliningTarget).getCApiContext().getCachedPrimitiveNativeWrapper(value) == this);
            setNativePointer(firstToNativeNode.execute(inliningTarget, this, immortal));
        }
    }
}
