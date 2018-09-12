/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class CallUnaryMethodNode extends CallSpecialMethodNode {
    public static CallUnaryMethodNode create() {
        return CallUnaryMethodNodeGen.create();
    }

    public abstract int executeInt(Object callable, int receiver) throws UnexpectedResultException;

    public abstract long executeLong(Object callable, long receiver) throws UnexpectedResultException;

    public abstract double executeDouble(Object callable, double receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(Object callable, boolean receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(Object callable, int receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(Object callable, long receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(Object callable, double receiver) throws UnexpectedResultException;

    public abstract int executeInt(Object callable, Object receiver) throws UnexpectedResultException;

    public abstract long executeLong(Object callable, Object receiver) throws UnexpectedResultException;

    public abstract double executeDouble(Object callable, Object receiver) throws UnexpectedResultException;

    public abstract boolean executeBoolean(Object callable, Object receiver) throws UnexpectedResultException;

    public abstract Object executeObject(Object callable, Object receiver);

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    int callInt(@SuppressWarnings("unused") PBuiltinFunction func, int receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeInt(receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    int callInt(@SuppressWarnings("unused") PBuiltinFunction func, int receiver,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeInt(receiver);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    long callLong(@SuppressWarnings("unused") PBuiltinFunction func, long receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeLong(receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    long callLong(@SuppressWarnings("unused") PBuiltinFunction func, long receiver,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeLong(receiver);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    double callDouble(@SuppressWarnings("unused") PBuiltinFunction func, double receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeDouble(receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    double callDouble(@SuppressWarnings("unused") PBuiltinFunction func, double receiver,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeDouble(receiver);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBool(@SuppressWarnings("unused") PBuiltinFunction func, boolean receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeBool(receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBool(@SuppressWarnings("unused") PBuiltinFunction func, boolean receiver,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) throws UnexpectedResultException {
        return builtinNode.executeBool(receiver);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callObjectSingleContext(@SuppressWarnings("unused") PBuiltinFunction func, Object receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) {
        return builtinNode.execute(receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object call(@SuppressWarnings("unused") PBuiltinFunction func, Object receiver,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) {
        return builtinNode.execute(receiver);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callObjectSingleContext(@SuppressWarnings("unused") PBuiltinMethod func, Object receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @Cached("getUnary(func.getFunction())") PythonUnaryBuiltinNode builtinNode) {
        return builtinNode.execute(receiver);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "isFixed"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callObjectSingleContext(@SuppressWarnings("unused") PBuiltinMethod func, Object arg,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("func.getArity().takesFixedNumOfPositionalArgs()") boolean isFixed,
                    @Cached("getBinary(func.getFunction())") PythonBinaryBuiltinNode builtinNode) {
        return builtinNode.execute(func.getSelf(), arg);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object call(@SuppressWarnings("unused") PBuiltinMethod func, Object receiver,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getUnary(func.getFunction())") PythonUnaryBuiltinNode builtinNode) {
        return builtinNode.execute(receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isFixed"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object call(@SuppressWarnings("unused") PBuiltinMethod func, Object arg,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("func.getArity().takesFixedNumOfPositionalArgs()") boolean isFixed,
                    @Cached("getBinary(func.getFunction())") PythonBinaryBuiltinNode builtinNode) {
        return builtinNode.execute(func.getSelf(), arg);
    }

    @Specialization
    Object call(Object func, Object receiver,
                    @Cached("create()") CallNode callNode) {
        return callNode.execute(null, func, new Object[]{receiver}, PKeyword.EMPTY_KEYWORDS);
    }
}
