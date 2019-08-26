/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.nodes.NodeContextManager;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithGlobalState;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class LookupAndCallUnaryNode extends Node {

    public abstract static class NoAttributeHandler extends PNodeWithContext {
        public abstract Object execute(Object receiver);
    }

    protected final String name;
    protected final Supplier<NoAttributeHandler> handlerFactory;
    @Child private NoAttributeHandler handler;

    public abstract int executeInt(VirtualFrame frame, int receiver) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, Object receiver) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, long receiver) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, Object receiver) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, double receiver) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Object receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, boolean receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, Object receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, int receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, long receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, double receiver) throws UnexpectedResultException;

    public abstract Object executeObject(VirtualFrame frame, Object receiver);

    public static LookupAndCallUnaryNode create(String name) {
        return LookupAndCallUnaryNodeGen.create(name, null);
    }

    public static LookupAndCallUnaryNode create(String name, Supplier<NoAttributeHandler> handlerFactory) {
        return LookupAndCallUnaryNodeGen.create(name, handlerFactory);
    }

    LookupAndCallUnaryNode(String name, Supplier<NoAttributeHandler> handlerFactory) {
        this.name = name;
        this.handlerFactory = handlerFactory;
    }

    public String getMethodName() {
        return name;
    }

    protected PythonUnaryBuiltinNode getBuiltin(Object receiver) {
        assert receiver instanceof Boolean || receiver instanceof Integer || receiver instanceof Long || receiver instanceof Double || receiver instanceof String || receiver instanceof PNone;
        Object attribute = LookupAttributeInMRONode.Dynamic.getUncached().execute(GetClassNode.getUncached().execute(receiver), name);
        if (attribute instanceof PBuiltinFunction) {
            PBuiltinFunction builtinFunction = (PBuiltinFunction) attribute;
            if (PythonUnaryBuiltinNode.class.isAssignableFrom(builtinFunction.getBuiltinNodeFactory().getNodeClass())) {
                return (PythonUnaryBuiltinNode) builtinFunction.getBuiltinNodeFactory().createNode();
            }
        }
        return null;
    }

    // int

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, int receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeInt(frame, receiver);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBool(VirtualFrame frame, int receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeBool(frame, receiver);
    }

    @Specialization(guards = "function != null")
    Object callObject(VirtualFrame frame, int receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) {
        return function.execute(frame, receiver);
    }

    // long

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    long callInt(VirtualFrame frame, long receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeLong(frame, receiver);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBool(VirtualFrame frame, long receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeBool(frame, receiver);
    }

    @Specialization(guards = "function != null")
    Object callObject(VirtualFrame frame, long receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) {
        return function.execute(frame, receiver);
    }

    // double

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    double callInt(VirtualFrame frame, double receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeDouble(frame, receiver);
    }

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBool(VirtualFrame frame, double receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeBool(frame, receiver);
    }

    @Specialization(guards = "function != null")
    Object callObject(VirtualFrame frame, double receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) {
        return function.execute(frame, receiver);
    }

    // bool

    @Specialization(guards = "function != null", rewriteOn = UnexpectedResultException.class)
    boolean callBool(VirtualFrame frame, boolean receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) throws UnexpectedResultException {
        return function.executeBool(frame, receiver);
    }

    @Specialization(guards = "function != null")
    Object callObject(VirtualFrame frame, boolean receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) {
        return function.execute(frame, receiver);
    }

    // PNone

    @Specialization(guards = "function != null")
    Object callObject(VirtualFrame frame, PNone receiver,
                    @Cached("getBuiltin(receiver)") PythonUnaryBuiltinNode function) {
        return function.execute(frame, receiver);
    }

    // Object

    @Specialization
    Object callObject(VirtualFrame frame, Object receiver,
                    @Cached("create(name)") LookupInheritedAttributeNode getattr,
                    @Cached("create()") CallUnaryMethodNode dispatchNode) {
        Object attr = getattr.execute(receiver);
        if (attr == PNone.NO_VALUE) {
            if (handlerFactory != null) {
                if (handler == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    handler = insert(handlerFactory.get());
                }
                return handler.execute(receiver);
            }
            return PNone.NO_VALUE;
        } else {
            return dispatchNode.executeObject(frame, attr, receiver);
        }
    }

    @GenerateUncached
    public abstract static class LookupAndCallUnaryDynamicNode extends PNodeWithGlobalState<NodeContextManager> {

        protected abstract Object executeObject(Object receiver, String name);

        @Specialization
        static Object doObject(Object receiver, String name,
                        @Cached LookupInheritedAttributeNode.Dynamic getattr,
                        @Cached CallUnaryMethodNode dispatchNode,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {

            Object attr = getattr.execute(receiver, name);
            if (profile.profile(attr != PNone.NO_VALUE)) {
                // NOTE: it's safe to pass a 'null' frame since this node can only be used via a
                // global state context manager
                return dispatchNode.executeObject(null, attr, receiver);
            }
            return PNone.NO_VALUE;
        }

        @Override
        public CallUnaryContextManager withGlobalState(ContextReference<PythonContext> contextRef, VirtualFrame frame) {
            return new CallUnaryContextManager(this, contextRef.get(), frame);
        }

        @Override
        public CallUnaryContextManager passState() {
            return new CallUnaryContextManager(this, null, null);
        }

        public static LookupAndCallUnaryDynamicNode create() {
            return LookupAndCallUnaryNodeGen.LookupAndCallUnaryDynamicNodeGen.create();
        }

        public static LookupAndCallUnaryDynamicNode getUncached() {
            return LookupAndCallUnaryNodeGen.LookupAndCallUnaryDynamicNodeGen.getUncached();
        }
    }

    public static final class CallUnaryContextManager extends NodeContextManager {

        private final LookupAndCallUnaryDynamicNode delegate;

        private CallUnaryContextManager(LookupAndCallUnaryDynamicNode delegate, PythonContext context, VirtualFrame frame) {
            super(context, frame, delegate);
            this.delegate = delegate;
        }

        public Object executeObject(Object receiver, String name) {
            return delegate.executeObject(receiver, name);
        }
    }
}
