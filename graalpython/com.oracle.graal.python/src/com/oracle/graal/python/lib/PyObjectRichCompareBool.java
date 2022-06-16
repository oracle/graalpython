/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.compareStrings;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Performs one of comparison operations. The nodes for all operations are inner classes of this
 * class - {@link EqNode}, {@link NeNode}, {@link LtNode}, {@link LeNode}, {@link GtNode} and
 * {@link GeNode}. Equivalent of CPython's {@code PyObject_RichCompareBool} with the difference that
 * the operation is selected by picking particular inner class. Note there is a small difference in
 * behavior between {@code PyObject_RichCompareBool} and {@code PyObject_RichCompare} followed by
 * {@code PyObject_IsTrue} - the objects are compared for referential equality first (when doing
 * equality comparison) before calling the special method. This makes a difference for objects that
 * report they are unequal to themselves (i.e. {@code NaN}).
 */
public abstract class PyObjectRichCompareBool {
    @SuppressWarnings("unused")
    protected abstract static class ComparisonBaseNode extends PNodeWithContext {
        public abstract boolean execute(Frame frame, Object a, Object b);

        protected boolean op(boolean a, boolean b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected boolean op(int a, int b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected boolean op(long a, long b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected boolean op(double a, double b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected SpecialMethodSlot getSlot() {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected SpecialMethodSlot getReverseSlot() {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected boolean needsIdentityComparison() {
            return false;
        }

        protected boolean identityComparisonResult() {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        @Specialization
        boolean doBB(boolean a, boolean b) {
            return op(a, b);
        }

        @Specialization
        boolean doII(int a, int b) {
            return op(a, b);
        }

        @Specialization
        boolean doIL(int a, long b) {
            return op(a, b);
        }

        @Specialization
        boolean doID(int a, double b) {
            return op(a, b);
        }

        @Specialization(guards = "isBuiltinPInt(b)", rewriteOn = OverflowException.class)
        boolean doIPNoOVerflow(int a, PInt b) throws OverflowException {
            return op(a, b.intValueExact());
        }

        @Specialization(guards = "isBuiltinPInt(b)", replaces = "doIPNoOVerflow")
        boolean doIP(int a, PInt b) {
            try {
                return op(a, b.intValueExact());
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization
        boolean doLL(long a, long b) {
            return op(a, b);
        }

        @Specialization
        boolean doLI(long a, int b) {
            return op(a, b);
        }

        @Specialization
        boolean doLD(long a, double b) {
            return op(a, b);
        }

        @Specialization(guards = "isBuiltinPInt(b)", rewriteOn = OverflowException.class)
        boolean doLPNoOVerflow(long a, PInt b) throws OverflowException {
            return op(a, b.longValueExact());
        }

        @Specialization(guards = "isBuiltinPInt(b)", replaces = "doLPNoOVerflow")
        boolean doLP(long a, PInt b) {
            try {
                return op(a, b.longValueExact());
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = "isBuiltinPInt(a)", rewriteOn = OverflowException.class)
        boolean doPINoOverflow(PInt a, int b) throws OverflowException {
            return op(a.intValueExact(), b);
        }

        @Specialization(guards = "isBuiltinPInt(a)", replaces = "doPINoOverflow")
        boolean doPI(PInt a, int b) {
            try {
                return op(a.intValueExact(), b);
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = "isBuiltinPInt(a)", rewriteOn = OverflowException.class)
        boolean doPLNoOverflow(PInt a, long b) throws OverflowException {
            return op(a.longValueExact(), b);
        }

        @Specialization(guards = "isBuiltinPInt(a)", replaces = "doPLNoOverflow")
        boolean doPL(PInt a, long b) {
            try {
                return op(a.longValueExact(), b);
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = {"isBuiltinPInt(a)", "isBuiltinPInt(b)"})
        @TruffleBoundary
        boolean doPP(PInt a, PInt b) {
            return op(a.compareTo(b), 0);
        }

        @Specialization
        boolean doDD(double a, double b) {
            // nb: Eq subclass handles NaN identity
            return op(a, b);
        }

        @Specialization
        boolean doDI(double a, int b) {
            return op(a, b);
        }

        @Specialization
        boolean doDL(double a, long b) {
            return op(a, b);
        }

        @Specialization
        boolean doGeneric(VirtualFrame frame, Object a, Object b,
                        @Cached IsNode isNode,
                        @Cached GetClassNode getClassA,
                        @Cached GetClassNode getClassB,
                        @Cached ConditionProfile reversedFirst,
                        @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached(parameters = "getSlot()") LookupSpecialMethodSlotNode lookupMethod,
                        @Cached(parameters = "getReverseSlot()") LookupSpecialMethodSlotNode lookupReverseMethod,
                        @Cached CallBinaryMethodNode callMethod,
                        @Cached CallBinaryMethodNode callReverseMethod,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PRaiseNode raiseNode) {
            if (needsIdentityComparison()) {
                if (isNode.execute(a, b)) {
                    return identityComparisonResult();
                }
            }
            boolean checkedReverseOp = false;
            Object aType = getClassA.execute(a);
            Object bType = getClassB.execute(b);
            if (reversedFirst.profile(!isSameTypeNode.execute(aType, bType) && isSubtypeNode.execute(bType, aType))) {
                checkedReverseOp = true;
                Object reverseMethod = lookupMethodIgnoreDescriptorError(frame, lookupReverseMethod, bType, b);
                if (reverseMethod != PNone.NO_VALUE) {
                    Object result = callReverseMethod.executeObject(frame, reverseMethod, b, a);
                    if (result != PNotImplemented.NOT_IMPLEMENTED) {
                        return isTrueNode.execute(frame, result);
                    }
                }
            }
            Object method = lookupMethodIgnoreDescriptorError(frame, lookupMethod, aType, a);
            if (method != PNone.NO_VALUE) {
                Object result = callMethod.executeObject(frame, method, a, b);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return isTrueNode.execute(frame, result);
                }
            }
            if (!checkedReverseOp) {
                Object reverseMethod = lookupMethodIgnoreDescriptorError(frame, lookupReverseMethod, bType, b);
                if (reverseMethod != PNone.NO_VALUE) {
                    Object result = callReverseMethod.executeObject(frame, reverseMethod, b, a);
                    if (result != PNotImplemented.NOT_IMPLEMENTED) {
                        return isTrueNode.execute(frame, result);
                    }
                }
            }
            return doDefault(raiseNode, a, b);
        }

        private static Object lookupMethodIgnoreDescriptorError(VirtualFrame frame, LookupSpecialMethodSlotNode lookupMethod, Object aType, Object a) {
            try {
                return lookupMethod.execute(frame, aType, a);
            } catch (PException e) {
                return PNone.NO_VALUE;
            }
        }
    }

    @GenerateUncached
    public abstract static class EqNode extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return a == b;
        }

        @Override
        protected boolean op(int a, int b) {
            return a == b;
        }

        @Override
        protected boolean op(long a, long b) {
            return a == b;
        }

        @Override
        protected boolean op(double a, double b) {
            return a == b || (Double.isNaN(a) && Double.isNaN(b));
        }

        @Specialization(insertBefore = "doGeneric")
        boolean doSS(TruffleString a, TruffleString b,
                        @Cached TruffleString.EqualNode equalNode) {
            return equalNode.execute(a, b, TS_ENCODING);
        }

        @Override
        protected SpecialMethodSlot getSlot() {
            return SpecialMethodSlot.Eq;
        }

        @Override
        protected SpecialMethodSlot getReverseSlot() {
            return SpecialMethodSlot.Eq;
        }

        @Override
        protected boolean needsIdentityComparison() {
            return true;
        }

        @Override
        protected boolean identityComparisonResult() {
            return true;
        }

        @Override
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            // Already compared for identity
            return false;
        }

        public static EqNode create() {
            return PyObjectRichCompareBoolFactory.EqNodeGen.create();
        }

        public static EqNode getUncached() {
            return PyObjectRichCompareBoolFactory.EqNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class NeNode extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return a != b;
        }

        @Override
        protected boolean op(int a, int b) {
            return a != b;
        }

        @Override
        protected boolean op(long a, long b) {
            return a != b;
        }

        @Override
        protected boolean op(double a, double b) {
            return a != b;
        }

        @Specialization(insertBefore = "doGeneric")
        boolean doSS(TruffleString a, TruffleString b,
                        @Cached TruffleString.EqualNode equalNode) {
            return !equalNode.execute(a, b, TS_ENCODING);
        }

        @Override
        protected SpecialMethodSlot getSlot() {
            return SpecialMethodSlot.Ne;
        }

        @Override
        protected SpecialMethodSlot getReverseSlot() {
            return SpecialMethodSlot.Ne;
        }

        @Override
        protected boolean needsIdentityComparison() {
            return true;
        }

        @Override
        protected boolean identityComparisonResult() {
            return false;
        }

        @Override
        @SuppressWarnings("unused")
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            // Already compared for identity
            return true;
        }

        public static NeNode create() {
            return PyObjectRichCompareBoolFactory.NeNodeGen.create();
        }

        public static NeNode getUncached() {
            return PyObjectRichCompareBoolFactory.NeNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LtNode extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return !a && b;
        }

        @Override
        protected boolean op(int a, int b) {
            return a < b;
        }

        @Override
        protected boolean op(long a, long b) {
            return a < b;
        }

        @Override
        protected boolean op(double a, double b) {
            return a < b;
        }

        @Specialization(insertBefore = "doGeneric")
        boolean doSS(TruffleString a, TruffleString b,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) < 0;
        }

        @Override
        protected SpecialMethodSlot getSlot() {
            return SpecialMethodSlot.Lt;
        }

        @Override
        protected SpecialMethodSlot getReverseSlot() {
            return SpecialMethodSlot.Gt;
        }

        @Override
        @SuppressWarnings("unused")
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, "<", a, b);
        }

        public static LtNode create() {
            return PyObjectRichCompareBoolFactory.LtNodeGen.create();
        }

        public static LtNode getUncached() {
            return PyObjectRichCompareBoolFactory.LtNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class LeNode extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return b || !a;
        }

        @Override
        protected boolean op(int a, int b) {
            return a <= b;
        }

        @Override
        protected boolean op(long a, long b) {
            return a <= b;
        }

        @Override
        protected boolean op(double a, double b) {
            return a <= b;
        }

        @Specialization(insertBefore = "doGeneric")
        boolean doSS(TruffleString a, TruffleString b,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) <= 0;
        }

        @Override
        protected SpecialMethodSlot getSlot() {
            return SpecialMethodSlot.Le;
        }

        @Override
        protected SpecialMethodSlot getReverseSlot() {
            return SpecialMethodSlot.Ge;
        }

        @Override
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, "<=", a, b);
        }

        public static LeNode create() {
            return PyObjectRichCompareBoolFactory.LeNodeGen.create();
        }

        public static LeNode getUncached() {
            return PyObjectRichCompareBoolFactory.LeNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GtNode extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return a && !b;
        }

        @Override
        protected boolean op(int a, int b) {
            return a > b;
        }

        @Override
        protected boolean op(long a, long b) {
            return a > b;
        }

        @Override
        protected boolean op(double a, double b) {
            return a > b;
        }

        @Specialization(insertBefore = "doGeneric")
        boolean doSS(TruffleString a, TruffleString b,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) > 0;
        }

        @Override
        protected SpecialMethodSlot getSlot() {
            return SpecialMethodSlot.Gt;
        }

        @Override
        protected SpecialMethodSlot getReverseSlot() {
            return SpecialMethodSlot.Lt;
        }

        @Override
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, ">", a, b);
        }

        public static GtNode create() {
            return PyObjectRichCompareBoolFactory.GtNodeGen.create();
        }

        public static GtNode getUncached() {
            return PyObjectRichCompareBoolFactory.GtNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GeNode extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return a || !b;
        }

        @Override
        protected boolean op(int a, int b) {
            return a >= b;
        }

        @Override
        protected boolean op(long a, long b) {
            return a >= b;
        }

        @Override
        protected boolean op(double a, double b) {
            return a >= b;
        }

        @Specialization(insertBefore = "doGeneric")
        boolean doSS(TruffleString a, TruffleString b,
                        @Cached TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) >= 0;
        }

        @Override
        protected SpecialMethodSlot getSlot() {
            return SpecialMethodSlot.Ge;
        }

        @Override
        protected SpecialMethodSlot getReverseSlot() {
            return SpecialMethodSlot.Le;
        }

        @Override
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, ">=", a, b);
        }

        public static GeNode create() {
            return PyObjectRichCompareBoolFactory.GeNodeGen.create();
        }

        public static GeNode getUncached() {
            return PyObjectRichCompareBoolFactory.GeNodeGen.getUncached();
        }
    }
}
