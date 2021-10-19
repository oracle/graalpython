/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

// cpython://Objects/abstract.c#binary_op1
// Order operations are tried until either a valid result or error: w.op(v,w)[*], v.op(v,w), w.op(v,w)
//
//       [*] only when v->ob_type != w->ob_type && w->ob_type is a subclass of v->ob_type
//
// The (long, double) and (double, long) specializations are needed since long->double conversion
// is not always correct (it can lose information). See FloatBuiltins.EqNode.compareDoubleToLong().
// The (int, double) and (double, int) specializations are needed to avoid int->long conversion.
// Although it would produce correct results, the special handling of long to double comparison
// is slower than converting int->double, which is always correct.
@ImportStatic(PythonOptions.class)
@ReportPolymorphism
public abstract class LookupAndCallBinaryNode extends Node {

    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Object arg, Object arg2);
    }

    protected final SpecialMethodSlot slot;
    protected final SpecialMethodSlot rslot;
    protected final String name;
    protected final String rname;
    protected final Supplier<NotImplementedHandler> handlerFactory;
    protected final boolean ignoreDescriptorException;
    private final boolean alwaysCheckReverse;

    @Child private PRaiseNode raiseNode;
    @Child private GetNameNode getNameNode;
    @Child private CallBinaryMethodNode dispatchNode;
    @Child private CallBinaryMethodNode reverseDispatchNode;
    @Child private NotImplementedHandler handler;

    public abstract Object executeObject(VirtualFrame frame, Object arg, Object arg2);

    LookupAndCallBinaryNode(String name, String rname, Supplier<NotImplementedHandler> handlerFactory, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        if (SpecialMethodSlot.findSpecialSlot(name) != null) {
            // new RuntimeException().printStackTrace();
            // System.out.println(name);
        }
        this.name = name;
        this.rname = rname;
        this.slot = null;
        this.rslot = null;
        this.handlerFactory = handlerFactory;
        this.alwaysCheckReverse = alwaysCheckReverse;
        this.ignoreDescriptorException = ignoreDescriptorException;
    }

    LookupAndCallBinaryNode(SpecialMethodSlot slot, SpecialMethodSlot rslot, Supplier<NotImplementedHandler> handlerFactory, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        this.name = slot.getName();
        this.rname = rslot.getName();
        this.slot = slot;
        this.rslot = rslot;
        this.handlerFactory = handlerFactory;
        this.alwaysCheckReverse = alwaysCheckReverse;
        this.ignoreDescriptorException = ignoreDescriptorException;
    }

    public static LookupAndCallBinaryNode create(String name) {
        return LookupAndCallBinaryNodeGen.create(name, null, null, false, false);
    }

    public static LookupAndCallBinaryNode create(String name, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallBinaryNodeGen.create(name, null, handlerFactory, false, false);
    }

    public static LookupAndCallBinaryNode create(SpecialMethodSlot slot) {
        return LookupAndCallBinaryNodeGen.create(slot, null, null, false, false);
    }

    public static LookupAndCallBinaryNode createReversible(SpecialMethodSlot slot, SpecialMethodSlot rslot, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallBinaryNodeGen.create(slot, rslot, handlerFactory, false, false);
    }

    public static LookupAndCallBinaryNode create(SpecialMethodSlot slot, SpecialMethodSlot rslot, boolean alwaysCheckReverse, boolean ignoreDescriptorException) {
        return LookupAndCallBinaryNodeGen.create(slot, rslot, null, alwaysCheckReverse, ignoreDescriptorException);
    }

    public static LookupAndCallBinaryNode create(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallBinaryNodeGen.create(slot, null, handlerFactory, false, false);
    }

    protected Object getMethod(Object receiver, String methodName) {
        return LookupSpecialMethodNode.Dynamic.getUncached().execute(null, GetClassNode.getUncached().execute(receiver), methodName, receiver);
    }

    protected final boolean isReversible() {
        return rname != null;
    }

    private CallBinaryMethodNode ensureDispatch() {
        // this also serves as a branch profile
        if (dispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dispatchNode = insert(CallBinaryMethodNode.create());
        }
        return dispatchNode;
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

    protected final PythonBinaryBuiltinNode getBinaryBuiltin(PythonBuiltinClassType clazz) {
        if (slot != null) {
            Object attribute = slot.getValue(clazz);
            if (attribute instanceof BinaryBuiltinDescriptor) {
                return ((BinaryBuiltinDescriptor) attribute).createNode();
            }
            // If the slot does not contain builtin, full lookup wouldn't find a builtin either
            return null;
        }
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(clazz, name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonBinaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonBinaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
            }
        }
        return null;
    }

    protected static final PythonBuiltinClassType getBuiltinClass(Object receiver, GetClassNode getClassNode) {
        Object clazz = getClassNode.execute(receiver);
        return clazz instanceof PythonBuiltinClassType ? (PythonBuiltinClassType) clazz : null;
    }

    protected static final boolean isClazz(PythonBuiltinClassType clazz, Object receiver, GetClassNode getClassNode) {
        return getClassNode.execute(receiver) == clazz;
    }

    // Object, Object

    @Specialization(guards = {"!isReversible()", "clazz != null", "function != null", "isClazz(clazz, left, getClassNode)"}, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callObjectBuiltin(VirtualFrame frame, Object left, Object right,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached("getBuiltinClass(left, getClassNode)") PythonBuiltinClassType clazz,
                    @Cached("getBinaryBuiltin(clazz)") PythonBinaryBuiltinNode function) {
        return function.execute(frame, left, right);
    }

    @Specialization(guards = {"!isReversible()", "left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
    Object callObjectGeneric(VirtualFrame frame, Object left, Object right,
                    @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                    @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                    @Cached GetClassNode getClassNode,
                    @Cached("create(name)") LookupSpecialBaseNode getattr) {
        return doCallObject(frame, left, right, getClassNode, getattr);
    }

    @Specialization(guards = "!isReversible()", replaces = "callObjectGeneric")
    @Megamorphic
    Object callObjectMegamorphic(VirtualFrame frame, Object left, Object right,
                    @Cached GetClassNode getClassNode,
                    @Cached("create(name)") LookupSpecialBaseNode getattr) {
        return doCallObject(frame, left, right, getClassNode, getattr);
    }

    private Object doCallObject(VirtualFrame frame, Object left, Object right, GetClassNode getClassNode, LookupSpecialBaseNode getattr) {
        Object leftClass = getClassNode.execute(left);
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
        if (leftCallable == PNone.NO_VALUE) {
            if (handlerFactory != null) {
                return runErrorHandler(frame, left, right);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
        return ensureDispatch().executeObject(frame, leftCallable, left, right);
    }

    @Specialization(guards = {"isReversible()", "left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
    Object callObjectGenericR(VirtualFrame frame, Object left, Object right,
                    @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                    @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                    @Cached("create(name)") LookupSpecialBaseNode getattr,
                    @Cached("create(rname)") LookupSpecialBaseNode getattrR,
                    @Cached("create(name)") LookupSpecialMethodNode getattrNormal,
                    @Cached("create(rname)") LookupSpecialMethodNode getattrRNormal,
                    @Cached GetClassNode getLeftClassNode,
                    @Cached GetClassNode getRightClassNode,
                    @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached IsSubtypeNode isSubtype,
                    @Cached ConditionProfile hasLeftCallable,
                    @Cached ConditionProfile hasRightCallable,
                    @Cached ConditionProfile notImplementedProfile,
                    @Cached BranchProfile noLeftBuiltinClassType,
                    @Cached BranchProfile noRightBuiltinClassType,
                    @Cached BranchProfile gotResultBranch,
                    @Cached AreSameCallables areSameCallables,
                    @Cached GetEnclosingType getEnclosingType) {
        return doCallObjectR(frame, left, right, getattr, getattrR, getLeftClassNode, getRightClassNode, isSameTypeNode, isSubtype, hasLeftCallable, hasRightCallable, notImplementedProfile,
                        noLeftBuiltinClassType, noRightBuiltinClassType, gotResultBranch, areSameCallables, getEnclosingType);
    }

    @Specialization(guards = "isReversible()", replaces = "callObjectGenericR")
    @Megamorphic
    Object callObjectRMegamorphic(VirtualFrame frame, Object left, Object right,
                    @Cached("create(name)") LookupSpecialMethodNode getattr,
                    @Cached("create(rname)") LookupSpecialMethodNode getattrR,
                    @Cached("create(name)") LookupSpecialMethodNode getattrNormal,
                    @Cached("create(rname)") LookupSpecialMethodNode getattrRNormal,
                    @Cached GetClassNode getLeftClassNode,
                    @Cached GetClassNode getRightClassNode,
                    @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached IsSubtypeNode isSubtype,
                    @Cached ConditionProfile hasLeftCallable,
                    @Cached ConditionProfile hasRightCallable,
                    @Cached ConditionProfile notImplementedProfile,
                    @Cached BranchProfile noLeftBuiltinClassType,
                    @Cached BranchProfile noRightBuiltinClassType,
                    @Cached BranchProfile gotResultBranch,
                    @Cached AreSameCallables areSameCallables,
                    @Cached GetEnclosingType getEnclosingType) {
        return doCallObjectR(frame, left, right, getattr, getattrR, getLeftClassNode, getRightClassNode, isSameTypeNode, isSubtype, hasLeftCallable, hasRightCallable, notImplementedProfile,
                        noLeftBuiltinClassType, noRightBuiltinClassType, gotResultBranch, areSameCallables, getEnclosingType);
    }

    private Object doCallObjectR(VirtualFrame frame, Object left, Object right, LookupSpecialBaseNode getattr, LookupSpecialBaseNode getattrR, GetClassNode getLeftClassNode,
                    GetClassNode getRightClassNode, TypeNodes.IsSameTypeNode isSameTypeNode, IsSubtypeNode isSubtype, ConditionProfile hasLeftCallable, ConditionProfile hasRightCallable,
                    ConditionProfile notImplementedProfile, BranchProfile noLeftBuiltinClassType, BranchProfile noRightBuiltinClassType,
                    BranchProfile gotResultBranch, AreSameCallables areSameCallables, GetEnclosingType getEnclosingType) {
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
        Object leftClass = getLeftClassNode.execute(left);
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
        Object rightClass = getRightClassNode.execute(right);
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

        if (!alwaysCheckReverse && areSameCallables.execute(leftCallable, rightCallable)) {
            rightCallable = PNone.NO_VALUE;
        }

        if (hasLeftCallable.profile(leftCallable != PNone.NO_VALUE)) {
            if (hasRightCallable.profile(rightCallable != PNone.NO_VALUE) &&
                            (!isSameTypeNode.execute(leftClass, rightClass) && isSubtype.execute(frame, rightClass, leftClass) ||
                                            isFlagSequenceCompat(leftClass, rightClass, name, noLeftBuiltinClassType, noRightBuiltinClassType))) {
                result = dispatch(frame, ensureReverseDispatch(), rightCallable, right, left, rightClass, rname, isSubtype, getEnclosingType);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                gotResultBranch.enter();
                rightCallable = PNone.NO_VALUE;
            }
            result = dispatch(frame, ensureDispatch(), leftCallable, left, right, leftClass, name, isSubtype, getEnclosingType);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
            gotResultBranch.enter();
        }
        if (notImplementedProfile.profile(rightCallable != PNone.NO_VALUE)) {
            result = dispatch(frame, ensureReverseDispatch(), rightCallable, right, left, rightClass, rname, isSubtype, getEnclosingType);
        }
        if (handlerFactory != null && result == PNotImplemented.NOT_IMPLEMENTED) {
            return runErrorHandler(frame, left, right);
        }
        return result;
    }

    @ImportStatic(PGuards.class)
    protected static abstract class AreSameCallables extends Node {
        public abstract boolean execute(Object left, Object right);

        @Specialization(guards = "a == b")
        static boolean areIdenticalFastPath(@SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b) {
            return true;
        }

        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doDescrs(BuiltinMethodDescriptor a, BuiltinMethodDescriptor b) {
            return a == b;
        }

        @SuppressWarnings("StringEquality")
        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doDescrFun1(BuiltinMethodDescriptor a, PBuiltinFunction b) {
            return a.getFactory() == b.getBuiltinNodeFactory() && a.getName() == b.getName();
        }

        @SuppressWarnings("StringEquality")
        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doDescrFun2(PBuiltinFunction a, BuiltinMethodDescriptor b) {
            return b.getFactory() == a.getBuiltinNodeFactory() && a.getName() == b.getName();
        }

        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doDescrMeth1(BuiltinMethodDescriptor a, PBuiltinMethod b) {
            return doDescrFun1(a, b.getFunction());
        }

        @Specialization(replaces = "areIdenticalFastPath")
        static boolean doDescrMeth2(PBuiltinMethod a, BuiltinMethodDescriptor b) {
            return doDescrFun2(a.getFunction(), b);
        }

        @Fallback
        static boolean doGenericRuntimeObjects(Object a, Object b) {
            // Note: this handles also situations such as BuiltinMethodDescriptor vs PNone.None
            return a == b;
        }
    }

    @ImportStatic(PGuards.class)
    protected static abstract class GetEnclosingType extends Node {
        public abstract Object execute(Object callable);

        @Specialization
        static Object doDescrs(BuiltinMethodDescriptor descriptor) {
            return descriptor.getEnclosingType();
        }

        @Specialization
        static Object doBuiltinFun(PBuiltinFunction fun) {
            return fun.getEnclosingType();
        }

        @Specialization
        static Object doBuiltinMethod(PBuiltinMethod a) {
            return doBuiltinFun(a.getFunction());
        }

        @Fallback
        static Object doOthers(@SuppressWarnings("unused") Object callable) {
            return null;
        }
    }

    private Object dispatch(VirtualFrame frame, CallBinaryMethodNode dispatch, Object callable, Object leftValue, Object rightValue, Object leftClass, String op, IsSubtypeNode isSubtype,
                    GetEnclosingType getEnclosingType) {
        // see descrobject.c/wrapperdescr_call()
        Object enclosing = getEnclosingType.execute(callable);
        if (enclosing != null && !isSubtype.execute(leftClass, enclosing)) {
            throw ensureRaiseNode().raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, op, ensureGetNameNode().execute(leftClass), leftValue);
        }
        return dispatch.executeObject(frame, callable, leftValue, rightValue);
    }

    private static boolean isFlagSequenceCompat(Object leftClass, Object rightClass, String name, BranchProfile gotLeftBuiltinClassType, BranchProfile gotRightBuiltinClassType) {
        if (PGuards.isNativeClass(leftClass) || PGuards.isNativeClass(rightClass)) {
            return false;
        }
        // see pypy descroperation.py#_make_binop_impl()
        boolean isSeqBugCompatOperation = (name.equals(__ADD__) || name.equals(__MUL__));
        return isSeqBugCompatOperation && isFlagSequenceBugCompat(leftClass, gotLeftBuiltinClassType) && !isFlagSequenceBugCompat(rightClass, gotRightBuiltinClassType);
    }

    private static boolean isFlagSequenceBugCompat(Object clazz, BranchProfile gotBuiltinClassType) {
        PythonBuiltinClassType type = null;
        if (clazz instanceof PythonBuiltinClassType) {
            type = (PythonBuiltinClassType) clazz;
        } else if (clazz instanceof PythonBuiltinClass) {
            type = ((PythonBuiltinClass) clazz).getType();
        } else {
            return false;
        }
        gotBuiltinClassType.enter();
        return type == PythonBuiltinClassType.PString ||
                        type == PythonBuiltinClassType.PByteArray ||
                        type == PythonBuiltinClassType.PBytes ||
                        type == PythonBuiltinClassType.PList ||
                        type == PythonBuiltinClassType.PTuple;
    }

    private Object runErrorHandler(VirtualFrame frame, Object left, Object right) {
        if (handler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handler = insert(handlerFactory.get());
        }
        return handler.execute(frame, left, right);
    }

    public String getName() {
        return name;
    }

    public String getRname() {
        return rname;
    }

}
