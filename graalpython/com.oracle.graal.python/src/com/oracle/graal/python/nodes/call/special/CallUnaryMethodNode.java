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

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNodeFactory.CachedNodeGen;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class CallUnaryMethodNode extends CallSpecialMethodNode {
    public static CallUnaryMethodNode create() {
        return CachedNodeGen.create();
    }

    public static CallUnaryMethodNode getUncached() {
        return Uncached.INSTANCE;
    }

    public abstract int executeInt(VirtualFrame frame, Object callable, int receiver) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, Object callable, long receiver) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Object callable, double receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, Object callable, boolean receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, Object callable, int receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, Object callable, long receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, Object callable, double receiver) throws UnexpectedResultException;

    public abstract int executeInt(VirtualFrame frame, Object callable, Object receiver) throws UnexpectedResultException;

    public abstract long executeLong(VirtualFrame frame, Object callable, Object receiver) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Object callable, Object receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(VirtualFrame frame, Object callable, Object receiver) throws UnexpectedResultException;

    public abstract Object executeObject(VirtualFrame frame, Object callable, Object receiver);

    abstract static class CachedNode extends CallUnaryMethodNode {

        @Specialization(guards = {"func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
        int callInt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int receiver,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeInt(frame, receiver);
        }

        @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
        int callInt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int receiver,
                        @SuppressWarnings("unused") @Cached(value = "func.getCallTarget()") RootCallTarget ct,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeInt(frame, receiver);
        }

        @Specialization(guards = {"func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
        long callLong(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long receiver,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeLong(frame, receiver);
        }

        @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
        long callLong(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long receiver,
                        @SuppressWarnings("unused") @Cached(value = "func.getCallTarget()") RootCallTarget ct,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeLong(frame, receiver);
        }

        @Specialization(guards = {"func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
        double callDouble(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double receiver,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeDouble(frame, receiver);
        }

        @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
        double callDouble(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double receiver,
                        @SuppressWarnings("unused") @Cached(value = "func.getCallTarget()") RootCallTarget ct,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeDouble(frame, receiver);
        }

        @Specialization(guards = {"func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
        boolean callBool(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean receiver,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeBool(frame, receiver);
        }

        @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
        boolean callBool(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean receiver,
                        @SuppressWarnings("unused") @Cached(value = "func.getCallTarget()") RootCallTarget ct,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
            return builtinNode.executeBool(frame, receiver);
        }

        @Specialization(guards = {"func == cachedFunc", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callObjectSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object receiver,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, receiver);
        }

        @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "!takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object receiver,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                        @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                        @Cached("getUnary(func.getFunction())") PythonUnaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, receiver);
        }

        @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callSelfMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg,
                        @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                        @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                        @Cached("getBinary(func.getFunction())") PythonBinaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, func.getSelf(), arg);
        }

        @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "!takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
        Object callMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object receiver,
                        @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                        @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                        @Cached("getUnary(func.getFunction())") PythonUnaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, receiver);
        }

        @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
        Object callSelfMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg,
                        @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                        @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                        @Cached("getBinary(func.getFunction())") PythonBinaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, func.getSelf(), arg);
        }

        @Specialization
        static Object call(VirtualFrame frame, Object func, Object receiver,
                        @Cached("create()") CallNode callNode) {
            return callNode.execute(frame, func, new Object[]{receiver}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    private static final class Uncached extends CallUnaryMethodNode {
        private static final Uncached INSTANCE = new Uncached();

        @Override
        public Object executeObject(VirtualFrame frame, Object arg0Value, Object arg1Value) {
            return CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached()));
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object arg0Value, Object arg1Value) throws UnexpectedResultException {
            return expectBooleanResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object arg0Value, boolean arg1Value) throws UnexpectedResultException {
            return expectBooleanResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object arg0Value, double arg1Value) throws UnexpectedResultException {
            return expectBooleanResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object arg0Value, int arg1Value) throws UnexpectedResultException {
            return expectBooleanResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public boolean executeBoolean(VirtualFrame frame, Object arg0Value, long arg1Value) throws UnexpectedResultException {
            return expectBooleanResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public double executeDouble(VirtualFrame frame, Object arg0Value, Object arg1Value) throws UnexpectedResultException {
            return expectDoubleResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public double executeDouble(VirtualFrame frame, Object arg0Value, double arg1Value) throws UnexpectedResultException {
            return expectDoubleResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public int executeInt(VirtualFrame frame, Object arg0Value, Object arg1Value) throws UnexpectedResultException {
            return expectIntegerResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public int executeInt(VirtualFrame frame, Object arg0Value, int arg1Value) throws UnexpectedResultException {
            return expectIntegerResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public long executeLong(VirtualFrame frame, Object arg0Value, Object arg1Value) throws UnexpectedResultException {
            return expectLongResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public long executeLong(VirtualFrame frame, Object arg0Value, long arg1Value) throws UnexpectedResultException {
            return expectLongResult(CachedNode.call(frame, arg0Value, arg1Value, (CallNode.getUncached())));
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }
}
