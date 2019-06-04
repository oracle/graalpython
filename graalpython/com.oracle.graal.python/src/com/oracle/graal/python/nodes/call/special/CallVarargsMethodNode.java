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
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNodeFactory.CallCachedVarargsMethodNodeGen;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode.VarargsBuiltinDirectInvocationNotSupported;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

public abstract class CallVarargsMethodNode extends CallSpecialMethodNode {

    private static final CallUncachedVarargsMethodNode UNCACHED = new CallUncachedVarargsMethodNode();

    public abstract Object execute(VirtualFrame frame, Object callable, Object[] arguments, PKeyword[] keywords);

    public static CallVarargsMethodNode create() {
        return CallCachedVarargsMethodNodeGen.create();
    }

    public static CallVarargsMethodNode getUncached() {
        return UNCACHED;
    }

    abstract static class CallCachedVarargsMethodNode extends CallVarargsMethodNode {
        @Specialization(guards = {"func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class, assumptions = "singleContextAssumption()")
        Object call(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, PKeyword[] keywords,
                        @Cached("func") @SuppressWarnings("unused") PBuiltinFunction cachedFunc,
                        @Cached("getVarargs(func)") PythonVarargsBuiltinNode builtinNode) throws VarargsBuiltinDirectInvocationNotSupported {
            return builtinNode.varArgExecute(frame, arguments, keywords);
        }

        @Specialization(guards = {"arguments.length == 1", "keywords.length == 0", "func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callUnary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached("func") @SuppressWarnings("unused") PBuiltinFunction cachedFunc,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0]);
        }

        @Specialization(guards = {"arguments.length == 2", "keywords.length == 0", "func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callBinary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached("func") @SuppressWarnings("unused") PBuiltinFunction cachedFunc,
                        @Cached("getBinary(func)") PythonBinaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0], arguments[1]);
        }

        @Specialization(guards = {"arguments.length == 3", "keywords.length == 0", "func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callTernary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached("func") @SuppressWarnings("unused") PBuiltinFunction cachedFunc,
                        @Cached("getTernary(func)") PythonTernaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0], arguments[1], arguments[2]);
        }

        @Specialization(guards = {"arguments.length == 4", "keywords.length == 0", "func == cachedFunc",
                        "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
        Object callQuaternary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached("func") @SuppressWarnings("unused") PBuiltinFunction cachedFunc,
                        @Cached("getQuaternary(func)") PythonQuaternaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0], arguments[1], arguments[2], arguments[3]);
        }

        @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class)
        Object call(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                        @Cached("getVarargs(func)") PythonVarargsBuiltinNode builtinNode) throws VarargsBuiltinDirectInvocationNotSupported {
            return builtinNode.varArgExecute(frame, arguments, keywords);
        }

        @Specialization(guards = {"arguments.length == 1", "keywords.length == 0", "func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
        Object callUnary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                        @Cached("getUnary(func)") PythonUnaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0]);
        }

        @Specialization(guards = {"arguments.length == 2", "keywords.length == 0", "func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
        Object callBinary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                        @Cached("getBinary(func)") PythonBinaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0], arguments[1]);
        }

        @Specialization(guards = {"arguments.length == 3", "keywords.length == 0", "func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
        Object callTernary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                        @Cached("getTernary(func)") PythonTernaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0], arguments[1], arguments[2]);
        }

        @Specialization(guards = {"arguments.length == 4", "keywords.length == 0", "func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
        Object callQuaternary(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                        @Cached("getQuaternary(func)") PythonQuaternaryBuiltinNode builtinNode) {
            return builtinNode.execute(frame, arguments[0], arguments[1], arguments[2], arguments[3]);
        }

        @Specialization
        Object call(VirtualFrame frame, Object func, Object[] arguments, PKeyword[] keywords,
                        @Cached("create()") CallNode callNode) {
            return callNode.execute(frame, func, arguments, keywords);
        }

    }

    private static final class CallUncachedVarargsMethodNode extends CallVarargsMethodNode {

        @Override
        public Object execute(VirtualFrame frame, Object callable, Object[] arguments, PKeyword[] keywords) {
            return CallNode.getUncached().execute(frame, callable, arguments, keywords);
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
