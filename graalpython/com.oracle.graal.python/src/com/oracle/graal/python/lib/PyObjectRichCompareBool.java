/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBoolFactory.EqNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
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

    public abstract static class Comparison {
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

        protected boolean doDefault(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object a, Object b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }
    }

    @SuppressWarnings("unused")
    @GenerateCached(false)
    @GenerateInline(false)
    @GenerateUncached(false)
    public abstract static class ComparisonBaseNode extends PNodeWithContext {
        // Overridden by the implementors to call execute with the right Comparison strategy
        public boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected abstract boolean execute(Frame frame, Node inliningTarget, Object a, Object b, Comparison cmp);

        @Specialization
        static boolean doBB(boolean a, boolean b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doII(int a, int b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doIL(int a, long b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doID(int a, double b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization(guards = "isBuiltinPInt(b)", rewriteOn = OverflowException.class)
        static boolean doIPNoOVerflow(int a, PInt b, Comparison cmp) throws OverflowException {
            return cmp.op(a, b.intValueExact());
        }

        @Specialization(guards = "isBuiltinPInt(b)", replaces = "doIPNoOVerflow")
        static boolean doIP(int a, PInt b, Comparison cmp) {
            try {
                return cmp.op(a, b.intValueExact());
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization
        static boolean doLL(long a, long b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doLI(long a, int b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doLD(long a, double b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization(guards = "isBuiltinPInt(b)", rewriteOn = OverflowException.class)
        static boolean doLPNoOVerflow(long a, PInt b, Comparison cmp) throws OverflowException {
            return cmp.op(a, b.longValueExact());
        }

        @Specialization(guards = "isBuiltinPInt(b)", replaces = "doLPNoOVerflow")
        static boolean doLP(long a, PInt b, Comparison cmp) {
            try {
                return cmp.op(a, b.longValueExact());
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = "isBuiltinPInt(a)", rewriteOn = OverflowException.class)
        static boolean doPINoOverflow(PInt a, int b, Comparison cmp) throws OverflowException {
            return cmp.op(a.intValueExact(), b);
        }

        @Specialization(guards = "isBuiltinPInt(a)", replaces = "doPINoOverflow")
        static boolean doPI(PInt a, int b, Comparison cmp) {
            try {
                return cmp.op(a.intValueExact(), b);
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = "isBuiltinPInt(a)", rewriteOn = OverflowException.class)
        static boolean doPLNoOverflow(PInt a, long b, Comparison cmp) throws OverflowException {
            return cmp.op(a.longValueExact(), b);
        }

        @Specialization(guards = "isBuiltinPInt(a)", replaces = "doPLNoOverflow")
        static boolean doPL(PInt a, long b, Comparison cmp) {
            try {
                return cmp.op(a.longValueExact(), b);
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = {"isBuiltinPInt(a)", "isBuiltinPInt(b)"})
        @TruffleBoundary
        static boolean doPP(PInt a, PInt b, Comparison cmp) {
            return cmp.op(a.compareTo(b), 0);
        }

        @Specialization
        static boolean doDD(double a, double b, Comparison cmp) {
            // nb: Eq subclass handles NaN identity
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doDI(double a, int b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        static boolean doDL(double a, long b, Comparison cmp) {
            return cmp.op(a, b);
        }

        @Specialization
        @InliningCutoff
        static boolean doGeneric(VirtualFrame frame, Node inliningTarget, Object a, Object b, Comparison cmp,
                        @Cached(inline = false) IsNode isNode,
                        @Cached GetClassNode getClassA,
                        @Cached GetClassNode getClassB,
                        @Cached InlinedConditionProfile reversedFirst,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                        @Cached(parameters = "cmp.getSlot()", inline = false) LookupSpecialMethodSlotNode lookupMethod,
                        @Cached(parameters = "cmp.getReverseSlot()", inline = false) LookupSpecialMethodSlotNode lookupReverseMethod,
                        @Cached(inline = false) CallBinaryMethodNode callMethod,
                        @Cached(inline = false) CallBinaryMethodNode callReverseMethod,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (cmp.needsIdentityComparison()) {
                if (isNode.execute(a, b)) {
                    return cmp.identityComparisonResult();
                }
            }
            boolean checkedReverseOp = false;
            Object aType = getClassA.execute(inliningTarget, a);
            Object bType = getClassB.execute(inliningTarget, b);
            if (reversedFirst.profile(inliningTarget, !isSameTypeNode.execute(inliningTarget, aType, bType) &&
                            isSubtypeNode.execute(bType, aType))) {
                checkedReverseOp = true;
                Object reverseMethod = lookupMethodIgnoreDescriptorError(frame, lookupReverseMethod, bType, b);
                if (reverseMethod != PNone.NO_VALUE) {
                    Object result = callReverseMethod.executeObject(frame, reverseMethod, b, a);
                    if (result != PNotImplemented.NOT_IMPLEMENTED) {
                        return isTrueNode.execute(frame, inliningTarget, result);
                    }
                }
            }
            Object method = lookupMethodIgnoreDescriptorError(frame, lookupMethod, aType, a);
            if (method != PNone.NO_VALUE) {
                Object result = callMethod.executeObject(frame, method, a, b);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return isTrueNode.execute(frame, inliningTarget, result);
                }
            }
            if (!checkedReverseOp) {
                Object reverseMethod = lookupMethodIgnoreDescriptorError(frame, lookupReverseMethod, bType, b);
                if (reverseMethod != PNone.NO_VALUE) {
                    Object result = callReverseMethod.executeObject(frame, reverseMethod, b, a);
                    if (result != PNotImplemented.NOT_IMPLEMENTED) {
                        return isTrueNode.execute(frame, inliningTarget, result);
                    }
                }
            }
            return cmp.doDefault(inliningTarget, raiseNode, a, b);
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
    @GenerateInline(inlineByDefault = true)
    public abstract static class EqNode extends ComparisonBaseNode {
        private static final class EqComparison extends Comparison {
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
            protected boolean doDefault(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object a, Object b) {
                // Already compared for identity
                return false;
            }
        }

        private static final EqComparison CMP = new EqComparison();

        public final boolean compareCached(Frame frame, Object a, Object b) {
            return compare(frame, this, a, b);
        }

        @Override
        public final boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            return execute(frame, inliningTarget, a, b, CMP);
        }

        public static boolean compareUncached(Object a, Object b) {
            return EqNodeGen.getUncached().compare(null, null, a, b);
        }

        @Specialization(insertBefore = "doGeneric")
        @InliningCutoff
        static boolean doSS(TruffleString a, TruffleString b, @SuppressWarnings("unused") Comparison cmp,
                        @Cached(inline = false) TruffleString.EqualNode equalNode) {
            return equalNode.execute(a, b, TS_ENCODING);
        }

        @NeverDefault
        public static EqNode create() {
            return PyObjectRichCompareBoolFactory.EqNodeGen.create();
        }

        public static EqNode getUncached() {
            return PyObjectRichCompareBoolFactory.EqNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NeNode extends ComparisonBaseNode {
        private static final class NeComparison extends Comparison {
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
            protected boolean doDefault(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object a, Object b) {
                // Already compared for identity
                return true;
            }
        }

        private static final NeComparison CMP = new NeComparison();

        @Override
        public final boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            return execute(frame, inliningTarget, a, b, CMP);
        }

        @Specialization(insertBefore = "doGeneric")
        static boolean doSS(TruffleString a, TruffleString b, @SuppressWarnings("unused") Comparison cmp,
                        @Cached(inline = false) TruffleString.EqualNode equalNode) {
            return !equalNode.execute(a, b, TS_ENCODING);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class LtNode extends ComparisonBaseNode {

        private static final class LtComparison extends Comparison {
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
            protected boolean doDefault(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object a, Object b) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, "<", a, b);
            }
        }

        public static final LtComparison CMP = new LtComparison();

        @Override
        public boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            return execute(frame, inliningTarget, a, b, CMP);
        }

        @Specialization(insertBefore = "doGeneric")
        static boolean doSS(TruffleString a, TruffleString b, @SuppressWarnings("unused") Comparison cmp,
                        @Cached(inline = false) TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) < 0;
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class LeNode extends ComparisonBaseNode {

        private static final class LeComparison extends Comparison {
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

            @Override
            protected SpecialMethodSlot getSlot() {
                return SpecialMethodSlot.Le;
            }

            @Override
            protected SpecialMethodSlot getReverseSlot() {
                return SpecialMethodSlot.Ge;
            }

            @Override
            protected boolean doDefault(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object a, Object b) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, "<=", a, b);
            }
        }

        public static final LeComparison CMP = new LeComparison();

        @Override
        public final boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            return execute(frame, inliningTarget, a, b, CMP);
        }

        @Specialization(insertBefore = "doGeneric")
        static boolean doSS(TruffleString a, TruffleString b, @SuppressWarnings("unused") Comparison cmp,
                        @Cached(inline = false) TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) <= 0;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GtNode extends ComparisonBaseNode {

        private static final class GtComparison extends Comparison {
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

            @Override
            protected SpecialMethodSlot getSlot() {
                return SpecialMethodSlot.Gt;
            }

            @Override
            protected SpecialMethodSlot getReverseSlot() {
                return SpecialMethodSlot.Lt;
            }

            @Override
            protected boolean doDefault(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object a, Object b) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, ">", a, b);
            }
        }

        public static final GtComparison CMP = new GtComparison();

        @Override
        public final boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            return execute(frame, inliningTarget, a, b, CMP);
        }

        @Specialization(insertBefore = "doGeneric")
        static boolean doSS(TruffleString a, TruffleString b, @SuppressWarnings("unused") Comparison cmp,
                        @Cached(inline = false) TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) > 0;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GeNode extends ComparisonBaseNode {

        private static final class GeComparison extends Comparison {
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

            @Override
            protected SpecialMethodSlot getSlot() {
                return SpecialMethodSlot.Ge;
            }

            @Override
            protected SpecialMethodSlot getReverseSlot() {
                return SpecialMethodSlot.Le;
            }

            @Override
            protected boolean doDefault(Node inliningTarget, Lazy raiseNode, Object a, Object b) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, ">=", a, b);
            }
        }

        public static final GeComparison CMP = new GeComparison();

        @Override
        public final boolean compare(Frame frame, Node inliningTarget, Object a, Object b) {
            return execute(frame, inliningTarget, a, b, CMP);
        }

        @Specialization(insertBefore = "doGeneric")
        static boolean doSS(TruffleString a, TruffleString b, @SuppressWarnings("unused") Comparison cmp,
                        @Cached(inline = false) TruffleString.CompareIntsUTF32Node compareIntsUTF32Node) {
            return compareStrings(a, b, compareIntsUTF32Node) >= 0;
        }
    }
}
