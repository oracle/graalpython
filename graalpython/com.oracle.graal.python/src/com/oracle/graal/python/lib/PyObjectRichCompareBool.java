/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Performs one of comparison operations. The nodes for all operations are inner classes of this
 * class - {@link Eq}, {@link Ne}, {@link Lt}, {@link Le}, {@link Gt} and {@link Ge}. Equivalent of
 * CPython's {@code PyObject_RichCompareBool} with the difference that the operation is selected by
 * picking particular inner class. Note there is a small difference in behavior between
 * {@code PyObject_RichCompareBool} and {@code PyObject_RichCompare} followed by
 * {@code PyObject_IsTrue} - the objects are compared for referential equality first (when doing
 * equality comparison) before calling the special method. This makes a difference for objects that
 * report they are unequal to themselves (i.e. {@code NaN}).
 */
public abstract class PyObjectRichCompareBool {
    protected static abstract class ComparisonBaseNode extends PNodeWithContext {
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

        protected boolean op(String a, String b) {
            return op(StringUtils.compareToUnicodeAware(a, b), 0);
        }

        protected SpecialMethodSlot getSlot() {
            throw CompilerDirectives.shouldNotReachHere("abstract method");
        }

        protected SpecialMethodSlot getReverseSlot() {
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
            return op(a, b.intValueExact());
        }

        @Specialization(guards = "isBuiltinPInt(b)", replaces = "doLPNoOVerflow")
        boolean doLP(long a, PInt b) {
            try {
                return op(a, b.intValueExact());
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
            return op(a.intValueExact(), b);
        }

        @Specialization(guards = "isBuiltinPInt(a)", replaces = "doPLNoOverflow")
        boolean doPL(PInt a, long b) {
            try {
                return op(a.intValueExact(), b);
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
        boolean doSS(String a, String b) {
            return op(a.compareTo(b), 0);
        }

        @Specialization
        boolean doGeneric(VirtualFrame frame, Object a, Object b,
                        @Cached GetClassNode getClassA,
                        @Cached GetClassNode getClassB,
                        @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached(parameters = "getSlot()") LookupSpecialMethodSlotNode lookupMethod,
                        @Cached(parameters = "getReverseSlot()") LookupSpecialMethodSlotNode lookupReverseMethod,
                        @Cached CallBinaryMethodNode callMethod,
                        @Cached CallBinaryMethodNode callReverseMethod,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PRaiseNode raiseNode) {
            boolean checkedReverseOp = false;
            Object aType = getClassA.execute(a);
            Object bType = getClassB.execute(b);
            if (!isSameTypeNode.execute(aType, bType) && isSubtypeNode.execute(bType, aType)) {
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

        private Object lookupMethodIgnoreDescriptorError(VirtualFrame frame, LookupSpecialMethodSlotNode lookupMethod, Object aType, Object a) {
            try {
                return lookupMethod.execute(frame, aType, a);
            } catch (PException e) {
                return PNone.NO_VALUE;
            }
        }
    }

    @GenerateUncached
    public abstract static class Eq extends ComparisonBaseNode {
        @Override
        public boolean execute(Frame frame, Object a, Object b) {
            /*
             * Technically, we have objects that are considered identical even if they are not the
             * same Java objects. But that only happens for certain builtins, so it shouldn't be
             * observable if we don't make proper identity comparison here and make them go through
             * __eq__ for simplicity
             */
            if (a == b) {
                return true;
            }
            return executeInternal(frame, a, b);
        }

        protected abstract boolean executeInternal(Frame frame, Object a, Object b);

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
            return a == b;
        }

        @Override
        protected boolean op(String a, String b) {
            return a.equals(b);
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
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            // The objects were already compared for identity in the beginning
            return false;
        }
    }

    @GenerateUncached
    public abstract static class Ne extends ComparisonBaseNode {
        @Override
        public boolean execute(Frame frame, Object a, Object b) {
            if (a == b) {
                return false;
            }
            return executeInternal(frame, a, b);
        }

        protected abstract boolean executeInternal(Frame frame, Object a, Object b);

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
        protected boolean op(String a, String b) {
            return !a.equals(b);
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
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            // The objects were already compared for identity in the beginning
            return true;
        }
    }

    @GenerateUncached
    public abstract static class Lt extends ComparisonBaseNode {
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
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, "<", a, b);
        }
    }

    @GenerateUncached
    public abstract static class Le extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return b || a == b;
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
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, "<=", a, b);
        }
    }

    @GenerateUncached
    public abstract static class Gt extends ComparisonBaseNode {
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
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, ">", a, b);
        }
    }

    @GenerateUncached
    public abstract static class Ge extends ComparisonBaseNode {
        @Override
        protected boolean op(boolean a, boolean b) {
            return a || a == b;
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
        protected boolean doDefault(PRaiseNode raiseNode, Object a, Object b) {
            throw raiseNode.raise(TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, ">=", a, b);
        }
    }
}
