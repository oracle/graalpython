/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.lib.PyObjectRichCompare.GenericRichCompare;
import com.oracle.graal.python.lib.PyObjectRichCompareBoolNodeGen.CachedPyObjectRichCompareBoolNodeGen;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;

/**
 * Performs one of comparison operations. Equivalent of CPython's {@code PyObject_RichCompareBool}.
 * Note there is a small difference in behavior between {@code PyObject_RichCompareBool} and
 * {@code PyObject_RichCompare} followed by {@code PyObject_IsTrue} (see
 * <a href="https://bugs.python.org/issue4296">bpo4296</a>) - the objects are compared for
 * referential equality first (when doing equality comparison) before calling the special method.
 * This makes a difference for objects that report they are unequal to themselves (i.e.
 * {@code NaN}). Since we do not maintain identity for unboxed float objects, we cannot fully match
 * the CPython behavior - we treat all NaNs with exactly the same bits as equal.
 */
@GenerateInline
@GenerateCached(false)
@GenerateUncached
@TypeSystemReference(PythonIntegerTypes.class)
public abstract class PyObjectRichCompareBool extends Node {
    public abstract boolean execute(Frame frame, Node inliningTarget, Object a, Object b, RichCmpOp op);

    public final boolean executeEq(Frame frame, Node inliningTarget, Object a, Object b) {
        return execute(frame, inliningTarget, a, b, RichCmpOp.Py_EQ);
    }

    public final boolean executeCached(Frame frame, Object a, Object b, RichCmpOp op) {
        return execute(frame, null, a, b, op);
    }

    public static boolean executeUncached(Object a, Object b, RichCmpOp op) {
        return getUncached().execute(null, null, a, b, op);
    }

    public static boolean executeEqUncached(Object a, Object b) {
        return executeUncached(a, b, RichCmpOp.Py_EQ);
    }

    @Specialization
    static boolean doInts(int a, int b, RichCmpOp op) {
        return op.compare(a, b);
    }

    public static boolean compareWithFakeIdentity(RichCmpOp op, double a, double b) {
        // CPython checks identity of the float objects first, we cannot do that so we chose to
        // report the same NaN bit patterns as identical
        return switch (op) {
            case Py_LT -> a < b;
            case Py_LE -> a <= b;
            case Py_EQ, Py_NE -> PFloat.areIdentical(a, b) == op.isEq();
            case Py_GT -> a > b;
            case Py_GE -> a >= b;
        };
    }

    @Specialization
    static boolean doDoubles(double a, double b, RichCmpOp op) {
        return compareWithFakeIdentity(op, a, b);
    }

    @Specialization
    static boolean doLongs(long a, long b, RichCmpOp op) {
        return op.compare(a, b);
    }

    @Specialization(guards = "op.isEqOrNe()")
    static boolean doStrings(TruffleString a, TruffleString b, RichCmpOp op,
                    @Cached EqualNode equalNode) {
        return equalNode.execute(a, b, PythonUtils.TS_ENCODING) == op.isEq();
    }

    @Fallback
    static boolean doIt(VirtualFrame frame, Object a, Object b, RichCmpOp op,
                    @Cached IsNode isNode,
                    @Cached(inline = false) GenericRichCompare richCompare,
                    @Cached PyObjectIsTrueNode isTrueNode) {
        // CPython fast-path: Quick result when objects are the same. Guarantees that identity
        // implies equality.
        if (op.isEqOrNe()) {
            if (isNode.execute(a, b)) {
                return op.isEq();
            }
        }
        Object result = richCompare.execute(frame, a, b, op);
        return isTrueNode.execute(frame, result);
    }

    public static PyObjectRichCompareBool getUncached() {
        return PyObjectRichCompareBoolNodeGen.getUncached();
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class CachedPyObjectRichCompareBool extends Node {
        public abstract boolean execute(Frame frame, Object a, Object b, RichCmpOp op);

        public final boolean executeEq(Frame frame, Object a, Object b) {
            return execute(frame, a, b, RichCmpOp.Py_EQ);
        }

        @Specialization
        static boolean doIt(Frame frame, Object a, Object b, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool delegate) {
            return delegate.execute(frame, inliningTarget, a, b, op);
        }

        public static CachedPyObjectRichCompareBool create() {
            return CachedPyObjectRichCompareBoolNodeGen.create();
        }

        public static CachedPyObjectRichCompareBool getUncached() {
            return CachedPyObjectRichCompareBoolNodeGen.getUncached();
        }
    }
}
