/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode.VarargsBuiltinDirectInvocationNotSupported;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateUncached
public abstract class CallVarargsMethodNode extends AbstractCallMethodNode {

    public abstract Object execute(Frame frame, Object callable, Object[] arguments, PKeyword[] keywords);

    @NeverDefault
    public static CallVarargsMethodNode create() {
        return CallVarargsMethodNodeGen.create();
    }

    public static CallVarargsMethodNode getUncached() {
        return CallVarargsMethodNodeGen.getUncached();
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null"}, //
                    limit = "getCallSiteInlineCacheMaxDepth()",  //
                    rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class)
    static Object callVarargsDirect(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, PKeyword[] keywords,
                    @Cached("func") @SuppressWarnings("unused") PBuiltinFunction cachedFunc,
                    @Cached("getVarargs(frame, func)") PythonVarargsBuiltinNode builtinNode) throws VarargsBuiltinDirectInvocationNotSupported {
        return builtinNode.varArgExecute(frame, PNone.NO_VALUE, arguments, keywords);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class)
    static Object callVarargs(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object[] arguments, PKeyword[] keywords,
                    @SuppressWarnings("unused") @Cached("func.getCallTarget()") RootCallTarget ct,
                    @Cached("getVarargs(frame, func)") PythonVarargsBuiltinNode builtinNode) throws VarargsBuiltinDirectInvocationNotSupported {
        return builtinNode.varArgExecute(frame, PNone.NO_VALUE, arguments, keywords);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null",
                    "takesSelfArg"}, rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callSelfMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object[] arguments, PKeyword[] keywords,
                    @SuppressWarnings("unused") @Cached(value = "func", weak = true) PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getVarargs(frame, func.getBuiltinFunction())") PythonVarargsBuiltinNode builtinNode) {
        return builtinNode.varArgExecute(frame, func.getSelf(), arguments, keywords);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func, getCt) == ct", "takesSelfArg"}, //
                    limit = "getCallSiteInlineCacheMaxDepth()", rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class)
    static Object callSelfMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object[] arguments, PKeyword[] keywords,
                    @SuppressWarnings("unused") @Shared @Cached GetCallTargetNode getCt,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func, getCt)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getVarargs(frame, func.getBuiltinFunction())") PythonVarargsBuiltinNode builtinNode) {
        return builtinNode.varArgExecute(frame, func.getSelf(), arguments, keywords);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null",
                    "!takesSelfArg"}, rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callSelfMethodSingleContextNoSelf(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object[] arguments, PKeyword[] keywords,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getVarargs(frame, func.getBuiltinFunction())") PythonVarargsBuiltinNode builtinNode) {
        return builtinNode.varArgExecute(frame, PNone.NO_VALUE, arguments, keywords);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func, getCt) == ct", "!takesSelfArg"}, //
                    rewriteOn = VarargsBuiltinDirectInvocationNotSupported.class, limit = "getCallSiteInlineCacheMaxDepth()")
    static Object callSelfMethodNoSelf(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object[] arguments, PKeyword[] keywords,
                    @SuppressWarnings("unused") @Shared @Cached GetCallTargetNode getCt,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func, getCt)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getVarargs(frame, func.getBuiltinFunction())") PythonVarargsBuiltinNode builtinNode) {
        return builtinNode.varArgExecute(frame, PNone.NO_VALUE, arguments, keywords);
    }

    @Specialization(guards = {"arguments.length == 1", "keywords.length == 0"})
    static Object callUnary(VirtualFrame frame, Object callable, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                    @Cached CallUnaryMethodNode callUnaryMethodNode) {
        return callUnaryMethodNode.executeObject(frame, callable, arguments[0]);
    }

    @Specialization(guards = {"arguments.length == 2", "keywords.length == 0"})
    static Object callBinary(VirtualFrame frame, Object callable, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                    @Cached CallBinaryMethodNode callBinaryMethodNode) {
        return callBinaryMethodNode.executeObject(frame, callable, arguments[0], arguments[1]);
    }

    @Specialization(guards = {"arguments.length == 3", "keywords.length == 0"})
    static Object callTernary(VirtualFrame frame, Object callable, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                    @Cached CallTernaryMethodNode callTernaryMethodNode) {
        return callTernaryMethodNode.execute(frame, callable, arguments[0], arguments[1], arguments[2]);
    }

    @Specialization(guards = {"arguments.length == 4", "keywords.length == 0"})
    static Object callQuaternary(VirtualFrame frame, Object callable, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                    @Cached CallQuaternaryMethodNode callQuaternaryMethodNode) {
        return callQuaternaryMethodNode.execute(frame, callable, arguments[0], arguments[1], arguments[2], arguments[3]);
    }

    @Specialization(replaces = {"callVarargsDirect", "callVarargs", "callSelfMethodSingleContext", "callSelfMethod", "callSelfMethodSingleContextNoSelf", "callSelfMethodNoSelf", "callUnary",
                    "callBinary", "callTernary", "callQuaternary"})
    @Megamorphic
    @InliningCutoff
    static Object call(VirtualFrame frame, Object func, Object[] arguments, PKeyword[] keywords,
                    @Bind("this") Node inliningTarget,
                    @Cached CallNode callNode,
                    @Cached InlinedConditionProfile isBoundProfile) {
        if (isBoundProfile.profile(inliningTarget, func instanceof BoundDescriptor)) {
            Object[] boundArguments = new Object[arguments.length - 1];
            PythonUtils.arraycopy(arguments, 1, boundArguments, 0, boundArguments.length);
            return callNode.execute(frame, ((BoundDescriptor) func).descriptor, boundArguments, keywords);
        } else {
            return callNode.execute(frame, func, arguments, keywords);
        }
    }
}
