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
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class CallQuaternaryMethodNode extends CallSpecialMethodNode {
    public static CallQuaternaryMethodNode create() {
        return CallQuaternaryMethodNodeGen.create();
    }

    public abstract Object execute(VirtualFrame frame, Object callable, Object arg1, Object arg2, Object arg3, Object arg4);

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object call(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2, Object arg3, Object arg4,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getQuaternary(frame, func)") PythonQuaternaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.execute(frame, arg1, arg2, arg3, arg4);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null", "frame != null || unusedFrame"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object call(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object arg1, Object arg2, Object arg3, Object arg4,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getQuaternary(frame, func)") PythonQuaternaryBuiltinNode builtinNode,
                    @SuppressWarnings("unused") @Cached("frameIsUnused(builtinNode)") boolean unusedFrame) {
        return builtinNode.execute(frame, arg1, arg2, arg3, arg4);
    }

    @Specialization(guards = {"func == cachedFunc", "builtinNode != null", "!takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    Object call(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2, Object arg3, Object arg4,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getQuaternary(frame, func.getFunction())") PythonQuaternaryBuiltinNode builtinNode) {
        return builtinNode.execute(frame, arg1, arg2, arg3, arg4);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func) == ct", "!takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object call(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg1, Object arg2, Object arg3, Object arg4,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getQuaternary(frame, func.getFunction())") PythonQuaternaryBuiltinNode builtinNode) {
        return builtinNode.execute(frame, arg1, arg2, arg3, arg4);
    }

    @Specialization
    Object call(VirtualFrame frame, Object func, Object arg1, Object arg2, Object arg3, Object arg4,
                    @Cached CallNode callNode) {
        return callNode.execute(frame, func, new Object[]{arg1, arg2, arg3, arg4}, PKeyword.EMPTY_KEYWORDS);
    }
}
