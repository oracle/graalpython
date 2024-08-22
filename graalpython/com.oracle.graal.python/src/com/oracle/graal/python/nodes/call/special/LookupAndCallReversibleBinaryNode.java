/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

// cpython://Objects/abstract.c#binary_op1
// Order operations are tried until either a valid result or error: w.op(v,w)[*], v.op(v,w), w.op(v,w)
//
//       [*] only when v->ob_type != w->ob_type && w->ob_type is a subclass of v->ob_type
@ImportStatic(PythonOptions.class)
abstract class LookupAndCallReversibleBinaryNode extends LookupAndCallBinaryNode {

    protected final SpecialMethodSlot slot;
    protected final SpecialMethodSlot rslot;
    private final boolean alwaysCheckReverse;

    @Child private PRaiseNode raiseNode;
    @Child private GetNameNode getNameNode;
    @Child private CallBinaryMethodNode reverseDispatchNode;

    LookupAndCallReversibleBinaryNode(SpecialMethodSlot slot, SpecialMethodSlot rslot, Supplier<NotImplementedHandler> handlerFactory, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        super(handlerFactory, ignoreDescriptorException);
        assert slot != null;
        assert rslot != null;
        this.slot = slot;
        this.rslot = rslot;
        this.alwaysCheckReverse = alwaysCheckReverse;
    }

    private PRaiseNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    private GetNameNode ensureGetNameNode() {
        if (getNameNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getNameNode = insert(GetNameNode.create());
        }
        return getNameNode;
    }

    private CallBinaryMethodNode ensureReverseDispatch() {
        // this also serves as a branch profile
        if (reverseDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reverseDispatchNode = insert(CallBinaryMethodNode.create());
        }
        return reverseDispatchNode;
    }

    @Specialization(guards = {"left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
    @SuppressWarnings("truffle-static-method")
    Object callObjectGenericR(VirtualFrame frame, Object left, Object right,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                    @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                    @Exclusive @Cached("create(slot)") LookupSpecialMethodSlotNode getattr,
                    @Exclusive @Cached("create(rslot)") LookupSpecialMethodSlotNode getattrR,
                    @Exclusive @Cached GetClassNode getLeftClassNode,
                    @Exclusive @Cached GetClassNode getRightClassNode,
                    @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                    @Exclusive @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached InlinedConditionProfile hasLeftCallable,
                    @Exclusive @Cached InlinedConditionProfile hasRightCallable,
                    @Exclusive @Cached InlinedConditionProfile notImplementedProfile,
                    @Exclusive @Cached InlinedBranchProfile noLeftBuiltinClassType,
                    @Exclusive @Cached InlinedBranchProfile noRightBuiltinClassType,
                    @Exclusive @Cached InlinedBranchProfile gotResultBranch,
                    @Exclusive @Cached AreSameCallables areSameCallables,
                    @Exclusive @Cached GetEnclosingType getEnclosingType) {
        return doCallObjectR(frame, inliningTarget, left, right, getattr, getattrR, getLeftClassNode, getRightClassNode, isSameTypeNode, isSubtype, hasLeftCallable, hasRightCallable,
                        notImplementedProfile, noLeftBuiltinClassType, noRightBuiltinClassType, gotResultBranch, areSameCallables, getEnclosingType);
    }

    @Specialization(replaces = "callObjectGenericR")
    @Megamorphic
    @SuppressWarnings("truffle-static-method")
    Object callObjectRMegamorphic(VirtualFrame frame, Object left, Object right,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached("create(slot)") LookupSpecialMethodSlotNode getattr,
                    @Exclusive @Cached("create(rslot)") LookupSpecialMethodSlotNode getattrR,
                    @Exclusive @Cached GetClassNode getLeftClassNode,
                    @Exclusive @Cached GetClassNode getRightClassNode,
                    @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                    @Exclusive @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached InlinedConditionProfile hasLeftCallable,
                    @Exclusive @Cached InlinedConditionProfile hasRightCallable,
                    @Exclusive @Cached InlinedConditionProfile notImplementedProfile,
                    @Exclusive @Cached InlinedBranchProfile noLeftBuiltinClassType,
                    @Exclusive @Cached InlinedBranchProfile noRightBuiltinClassType,
                    @Exclusive @Cached InlinedBranchProfile gotResultBranch,
                    @Exclusive @Cached AreSameCallables areSameCallables,
                    @Exclusive @Cached GetEnclosingType getEnclosingType) {
        return doCallObjectR(frame, inliningTarget, left, right, getattr, getattrR, getLeftClassNode, getRightClassNode, isSameTypeNode, isSubtype, hasLeftCallable, hasRightCallable,
                        notImplementedProfile, noLeftBuiltinClassType, noRightBuiltinClassType, gotResultBranch, areSameCallables, getEnclosingType);
    }

    private Object doCallObjectR(VirtualFrame frame, Node inliningTarget, Object left, Object right, LookupSpecialMethodSlotNode getattr, LookupSpecialMethodSlotNode getattrR,
                    GetClassNode getLeftClassNode, GetClassNode getRightClassNode, IsSameTypeNode isSameTypeNode, IsSubtypeNode isSubtype,
                    InlinedConditionProfile hasLeftCallable, InlinedConditionProfile hasRightCallable, InlinedConditionProfile notImplementedProfile, InlinedBranchProfile noLeftBuiltinClassType,
                    InlinedBranchProfile noRightBuiltinClassType, InlinedBranchProfile gotResultBranch, AreSameCallables areSameCallables, GetEnclosingType getEnclosingType) {
        // This specialization implements the logic from cpython://Objects/abstract.c#binary_op1
        // (the structure is modelled closely on it), as well as the additional logic in
        // cpython://Objects/typeobject.c#SLOT1BINFULL. The latter has the addition that it swaps
        // the arguments around. The swapping of arguments is undone when the call ends up in a
        // builtin function using a wrapper in CPython. We implement this reversal in our
        // BuiltinFunctionRootNode. This is opposite to what CPython does (and more in line with
        // what PyPy does), in that CPython always dispatches with the same argument order and has
        // slot wrappers for heap types __r*__ methods to swap the arguments, but we don't wrap heap
        // types' methods and instead have our swapping for the builtin types.

        Object result = PNotImplemented.NOT_IMPLEMENTED;
        Object leftClass = getLeftClassNode.execute(inliningTarget, left);
        Object leftCallable;
        try {
            leftCallable = getattr.execute(frame, leftClass, left);
        } catch (PException e) {
            if (ignoreDescriptorException) {
                leftCallable = PNone.NO_VALUE;
            } else {
                throw e;
            }
        }
        Object rightClass = getRightClassNode.execute(inliningTarget, right);
        Object rightCallable;
        try {
            rightCallable = getattrR.execute(frame, rightClass, right);
        } catch (PException e) {
            if (ignoreDescriptorException) {
                rightCallable = PNone.NO_VALUE;
            } else {
                throw e;
            }
        }

        if (!alwaysCheckReverse && areSameCallables.execute(inliningTarget, leftCallable, rightCallable)) {
            rightCallable = PNone.NO_VALUE;
        }

        if (hasLeftCallable.profile(inliningTarget, leftCallable != PNone.NO_VALUE)) {
            if (hasRightCallable.profile(inliningTarget, rightCallable != PNone.NO_VALUE) &&
                            (!isSameTypeNode.execute(inliningTarget, leftClass, rightClass) && isSubtype.execute(frame, rightClass, leftClass) ||
                                            isFlagSequenceCompat(inliningTarget, leftClass, rightClass, slot, noLeftBuiltinClassType, noRightBuiltinClassType))) {
                result = dispatch(frame, inliningTarget, ensureReverseDispatch(), rightCallable, right, left, rightClass, rslot, isSubtype, getEnclosingType);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                gotResultBranch.enter(inliningTarget);
                rightCallable = PNone.NO_VALUE;
            }
            result = dispatch(frame, inliningTarget, ensureDispatch(), leftCallable, left, right, leftClass, slot, isSubtype, getEnclosingType);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            gotResultBranch.enter(inliningTarget);
        }
        if (notImplementedProfile.profile(inliningTarget, rightCallable != PNone.NO_VALUE)) {
            result = dispatch(frame, inliningTarget, ensureReverseDispatch(), rightCallable, right, left, rightClass, rslot, isSubtype, getEnclosingType);
        }
        if (handlerFactory != null && result == PNotImplemented.NOT_IMPLEMENTED) {
            return runErrorHandler(frame, left, right);
        }
        return result;
    }

    private Object dispatch(VirtualFrame frame, Node inliningTarget, CallBinaryMethodNode dispatch, Object callable, Object leftValue,
                    Object rightValue, Object leftClass, SpecialMethodSlot op, IsSubtypeNode isSubtype, GetEnclosingType getEnclosingType) {
        // see descrobject.c/wrapperdescr_call()
        Object enclosing = getEnclosingType.execute(inliningTarget, callable);
        if (enclosing != null && !isSubtype.execute(leftClass, enclosing)) {
            throw ensureRaiseNode().raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.getName(), ensureGetNameNode().executeCached(leftClass), leftValue);
        }
        return dispatch.executeObject(frame, callable, leftValue, rightValue);
    }

    @SuppressWarnings("unused")
    private static boolean isFlagSequenceCompat(Node inliningTarget, Object leftClass, Object rightClass, SpecialMethodSlot slot, InlinedBranchProfile gotLeftBuiltinClassType,
                    InlinedBranchProfile gotRightBuiltinClassType) {
        return false;
        // Mul slot was converted to CPython like slots, this flag should never apply here
        /*-
        if (PGuards.isNativeClass(leftClass) || PGuards.isNativeClass(rightClass)) {
            return false;
        }
        // see pypy descroperation.py#_make_binop_impl()
        boolean isSeqBugCompatOperation = (slot == SpecialMethodSlot.Mul);
        return isSeqBugCompatOperation && isFlagSequenceBugCompat(inliningTarget, leftClass, gotLeftBuiltinClassType) && !isFlagSequenceBugCompat(inliningTarget, rightClass, gotRightBuiltinClassType);
        */
    }

    private static boolean isFlagSequenceBugCompat(Node inliningTarget, Object clazz, InlinedBranchProfile gotBuiltinClassType) {
        PythonBuiltinClassType type = null;
        if (clazz instanceof PythonBuiltinClassType) {
            type = (PythonBuiltinClassType) clazz;
        } else if (clazz instanceof PythonBuiltinClass) {
            type = ((PythonBuiltinClass) clazz).getType();
        } else {
            return false;
        }
        gotBuiltinClassType.enter(inliningTarget);
        return type == PythonBuiltinClassType.PString ||
                        type == PythonBuiltinClassType.PByteArray ||
                        type == PythonBuiltinClassType.PBytes ||
                        type == PythonBuiltinClassType.PList ||
                        type == PythonBuiltinClassType.PTuple;
    }

    @Override
    public TruffleString getName() {
        return slot.getName();
    }

    @Override
    public TruffleString getRname() {
        return rslot.getName();
    }

}
