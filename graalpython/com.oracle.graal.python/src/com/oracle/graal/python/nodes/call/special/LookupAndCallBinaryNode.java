/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class LookupAndCallBinaryNode extends Node {

    public abstract static class NotImplementedHandler extends PNodeWithContext {
        public abstract Object execute(Object arg, Object arg2);
    }

    protected final String name;
    protected final String rname;
    protected final Supplier<NotImplementedHandler> handlerFactory;

    @Child private CallBinaryMethodNode dispatchNode;
    @Child private CallBinaryMethodNode reverseDispatchNode;
    @Child private NotImplementedHandler handler;

    public abstract boolean executeBool(VirtualFrame frame, boolean arg, boolean arg2) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, long arg, long arg2) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, double arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, long arg, long arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, double arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(VirtualFrame frame, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract Object executeObject(VirtualFrame frame, Object arg, Object arg2);

    LookupAndCallBinaryNode(String name, String rname, Supplier<NotImplementedHandler> handlerFactory) {
        this.name = name;
        this.rname = rname;
        this.handlerFactory = handlerFactory;
    }

    public static LookupAndCallBinaryNode create(String name) {
        return LookupAndCallBinaryNodeGen.create(name, null, null);
    }

    public static LookupAndCallBinaryNode createReversible(String name, Supplier<NotImplementedHandler> handlerFactory) {
        assert name.startsWith("__");
        return LookupAndCallBinaryNodeGen.create(name, name.replaceFirst("__", "__r"), handlerFactory);
    }

    public static LookupAndCallBinaryNode create(String name, String rname) {
        return LookupAndCallBinaryNodeGen.create(name, rname, null);
    }

    public static LookupAndCallBinaryNode create(String name, String rname, Supplier<NotImplementedHandler> handlerFactory) {
        return LookupAndCallBinaryNodeGen.create(name, rname, handlerFactory);
    }

    protected Object getMethod(Object receiver, String methodName) {
        return LookupAttributeInMRONode.Dynamic.getUncached().execute(GetClassNode.getUncached().execute(receiver), methodName);
    }

    protected boolean isReversible() {
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

    private CallBinaryMethodNode ensureReverseDispatch() {
        // this also serves as a branch profile
        if (reverseDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reverseDispatchNode = insert(CallBinaryMethodNode.create());
        }
        return reverseDispatchNode;
    }

    private UnexpectedResultException handleLeftURE(VirtualFrame frame, Object left, Object right, UnexpectedResultException e) throws UnexpectedResultException {
        if (isReversible() && e.getResult() == PNotImplemented.NOT_IMPLEMENTED) {
            throw new UnexpectedResultException(ensureReverseDispatch().executeObject(frame, getMethod(right, rname), right, left));
        } else {
            throw e;
        }
    }

    protected PythonBinaryBuiltinNode getBuiltin(Object receiver) {
        assert receiver instanceof Boolean || receiver instanceof Integer || receiver instanceof Long || receiver instanceof Double || receiver instanceof String;
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(GetClassNode.getUncached().execute(receiver), name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonBinaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonBinaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
            }
        }
        return null;
    }

    // bool, bool

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, boolean left, boolean right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, boolean left, boolean right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeInt(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // int, int

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, int left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeInt(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, int left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    long callLong(VirtualFrame frame, int left, int right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeLong(frame, left, right); // implicit conversion to long
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // long, long

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    long callLong(VirtualFrame frame, long left, long right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeLong(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, long left, long right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // double, double

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    double callDouble(VirtualFrame frame, double left, double right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeDouble(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBoolean(VirtualFrame frame, double left, double right,
                    @Cached("getBuiltin(left)") PythonBinaryBuiltinNode function) throws UnexpectedResultException {
        try {
            return function.executeBool(frame, left, right);
        } catch (UnexpectedResultException e) {
            throw handleLeftURE(frame, left, right, e);
        }
    }

    // Object, Object

    @Specialization(guards = "!isReversible()")
    Object callObject(VirtualFrame frame, Object left, Object right,
                    @Cached("create(name)") LookupInheritedAttributeNode getattr) {
        Object leftCallable = getattr.execute(left);
        if (leftCallable == PNone.NO_VALUE) {
            if (handlerFactory != null) {
                return runErrorHandler(left, right);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
        return ensureDispatch().executeObject(frame, leftCallable, left, right);
    }

    @Specialization(guards = "isReversible()")
    Object callObject(VirtualFrame frame, Object left, Object right,
                    @Cached("create(name)") LookupAttributeInMRONode getattr,
                    @Cached("create(rname)") LookupAttributeInMRONode getattrR,
                    @Cached("create()") GetLazyClassNode getClass,
                    @Cached("create()") GetLazyClassNode getClassR,
                    @Cached("create()") TypeNodes.IsSameTypeNode isSameTypeNode,
                    @Cached("create()") IsSubtypeNode isSubtype,
                    @Cached("createBinaryProfile()") ConditionProfile notImplementedBranch) {
        Object result = PNotImplemented.NOT_IMPLEMENTED;
        LazyPythonClass leftClass = getClass.execute(left);
        Object leftCallable = getattr.execute(leftClass);
        LazyPythonClass rightClass = getClassR.execute(right);
        Object rightCallable = getattrR.execute(rightClass);
        if (leftCallable == rightCallable) {
            rightCallable = PNone.NO_VALUE;
        }
        if (leftCallable != PNone.NO_VALUE) {
            if (rightCallable != PNone.NO_VALUE && !isSameTypeNode.execute(leftClass, rightClass) && isSubtype.execute(frame, rightClass, leftClass)) {
                result = ensureReverseDispatch().executeObject(frame, rightCallable, right, left);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                rightCallable = PNone.NO_VALUE;
            }
            result = ensureDispatch().executeObject(frame, leftCallable, left, right);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }
        if (notImplementedBranch.profile(rightCallable != PNone.NO_VALUE)) {
            result = ensureReverseDispatch().executeObject(frame, rightCallable, right, left);
        }
        if (handlerFactory != null && result == PNotImplemented.NOT_IMPLEMENTED) {
            return runErrorHandler(left, right);
        }
        return result;
    }

    private Object runErrorHandler(Object left, Object right) {
        if (handler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            handler = insert(handlerFactory.get());
        }
        return handler.execute(left, right);
    }

    public String getName() {
        return name;
    }

    public String getRname() {
        return rname;
    }
    
    
}
