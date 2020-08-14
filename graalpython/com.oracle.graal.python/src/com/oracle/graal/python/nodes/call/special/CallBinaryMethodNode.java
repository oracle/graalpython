/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode.BoundDescriptor;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
@ReportPolymorphism
public abstract class CallBinaryMethodNode extends CallReversibleMethodNode {
    public static CallBinaryMethodNode create() {
        return CallBinaryMethodNodeGen.create();
    }

    public static CallBinaryMethodNode getUncached() {
        return CallBinaryMethodNodeGen.getUncached();
    }

    public abstract boolean executeBool(Frame frame, Object callable, boolean arg, boolean arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract int executeInt(Frame frame, Object callable, boolean arg, boolean arg2) throws UnexpectedResultException;

    public abstract int executeInt(Frame frame, Object callable, int arg, int arg2) throws UnexpectedResultException;

    public abstract int executeInt(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract long executeLong(Frame frame, Object callable, long arg, long arg2) throws UnexpectedResultException;

    public abstract long executeLong(Frame frame, Object callable, Object arg, Object arg2) throws UnexpectedResultException;

    public abstract double executeDouble(Frame frame, Object callable, double arg, double arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(Frame frame, Object callable, int arg, int arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(Frame frame, Object callable, long arg, long arg2) throws UnexpectedResultException;

    public abstract boolean executeBool(Frame frame, Object callable, double arg, double arg2) throws UnexpectedResultException;

    public abstract Object executeObject(Frame frame, Object callable, Object arg1, Object arg2);

    public final Object executeObject(Object callable, Object arg1, Object arg2) {
        return executeObject(null, callable, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean arg1, boolean arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean arg1, boolean arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBool(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean arg1, boolean arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, boolean arg1, boolean arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    int callIntSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callInt(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    int callIntSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callInt(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    int callInt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callInt(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    int callIntReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callInt(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolIntSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolIntSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolInt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolIntReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, int arg1, int arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    long callLongSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callLong(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    long callLongSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callLong(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    long callLong(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callLong(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    long callLongReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callLong(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolLongSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolLongSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolLong(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolLongReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, long arg1, long arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    double callDoubleSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callDouble(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    double callDoubleSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callDouble(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    double callDouble(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callDouble(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    double callDoubleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callDouble(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolDoubleSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc",
                    "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class, assumptions = "singleContextAssumption()")
    boolean callBoolDoubleSingleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolDouble(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = UnexpectedResultException.class)
    boolean callBoolDoubleReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, double arg1, double arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) throws UnexpectedResultException {
        return builtinNode.callBool(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "!isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callObjectSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "isReverse",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callObjectSingleContextReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg2, arg1);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "!isReverse", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callObject(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "isReverse", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callObjectReverse(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("isForReverseBinaryOperation(func.getCallTarget())") boolean isReverse,
                    @Cached("getBinary(frame, func)") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg2, arg1);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "!takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBinary(frame, func.getFunction())") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "takesSelfArg",
                    "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object callSelfMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getTernary(frame, func.getFunction())") PythonTernaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.execute(frame, func.getSelf(), arg1, arg2);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "!takesSelfArg", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBinary(frame, func.getFunction())") PythonBinaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.call(frame, arg1, arg2);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "takesSelfArg", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callSelfMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getTernary(frame, func.getFunction())") PythonTernaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.execute(frame, func.getSelf(), arg1, arg2);
    }

    @Specialization(replaces = {"callBoolSingle", "callBool", "callIntSingle", "callInt", "callBoolIntSingle", "callBoolInt", "callLongSingle", "callLong", "callBoolLongSingle", "callBoolLong",
                    "callDoubleSingle", "callDouble", "callBoolDoubleSingle", "callBoolDouble", "callObjectSingleContext", "callObject", "callBoolSingleReverse", "callBoolReverse",
                    "callIntSingleReverse", "callIntReverse", "callBoolIntSingleReverse", "callBoolIntReverse", "callLongSingleReverse", "callLongReverse", "callBoolLongSingleReverse",
                    "callBoolLongReverse", "callDoubleSingleReverse", "callDoubleReverse", "callBoolDoubleSingleReverse", "callBoolDoubleReverse", "callObjectSingleContextReverse",
                    "callObjectReverse", "callMethodSingleContext", "callSelfMethodSingleContext", "callMethod", "callSelfMethod"})
    static Object call(VirtualFrame frame, Object func, Object arg1, Object arg2,
                    @Cached CallNode callNode,
                    @Cached ConditionProfile isBoundProfile) {
        if (isBoundProfile.profile(func instanceof BoundDescriptor)) {
            return callNode.execute(frame, ((BoundDescriptor) func).descriptor, new Object[]{arg2}, PKeyword.EMPTY_KEYWORDS);
        } else {
            return callNode.execute(frame, func, new Object[]{arg1, arg2}, PKeyword.EMPTY_KEYWORDS);
        }
    }
}
