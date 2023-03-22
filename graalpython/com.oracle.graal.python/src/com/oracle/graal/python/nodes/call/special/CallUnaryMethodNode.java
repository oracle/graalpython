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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.TernaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.UnaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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
public abstract class CallUnaryMethodNode extends AbstractCallMethodNode {

    @NeverDefault
    public static CallUnaryMethodNode create() {
        return CallUnaryMethodNodeGen.create();
    }

    public static CallUnaryMethodNode getUncached() {
        return CallUnaryMethodNodeGen.getUncached();
    }

    public abstract Object executeObject(Frame frame, Object callable, Object receiver);

    public final Object executeObject(Object callable, Object receiver) {
        return executeObject(null, callable, receiver);
    }

    @Specialization(guards = {"cachedInfo == info", "node != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callUnarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") UnaryBuiltinDescriptor info, Object receiver,
                    @SuppressWarnings("unused") @Cached("info") UnaryBuiltinDescriptor cachedInfo,
                    @Cached("getBuiltin(cachedInfo)") PythonUnaryBuiltinNode node) {
        return node.execute(frame, receiver);
    }

    protected static boolean hasAllowedArgsNum(BuiltinMethodDescriptor descr) {
        return descr.minNumOfPositionalArgs() <= 1;
    }

    @Specialization(guards = {"cachedInfo == info", "node != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callBinarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") BinaryBuiltinDescriptor info, Object receiver,
                    @SuppressWarnings("unused") @Cached("info") BinaryBuiltinDescriptor cachedInfo,
                    @Cached("hasAllowedArgsNum(cachedInfo)") boolean hasValidArgsNum,
                    @Cached("getBuiltin(cachedInfo)") PythonBinaryBuiltinNode node) {
        raiseInvalidArgsNumUncached(hasValidArgsNum, cachedInfo);
        return node.execute(frame, receiver, PNone.NO_VALUE);
    }

    @Specialization(guards = {"cachedInfo == info", "node != null"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callTernarySpecialMethodSlotInlined(VirtualFrame frame, @SuppressWarnings("unused") TernaryBuiltinDescriptor info, Object receiver,
                    @SuppressWarnings("unused") @Cached("info") TernaryBuiltinDescriptor cachedInfo,
                    @Cached("hasAllowedArgsNum(cachedInfo)") boolean hasValidArgsNum,
                    @Cached("getBuiltin(cachedInfo)") PythonTernaryBuiltinNode node) {
        raiseInvalidArgsNumUncached(hasValidArgsNum, cachedInfo);
        return node.execute(frame, receiver, PNone.NO_VALUE, PNone.NO_VALUE);
    }

    @Specialization(guards = "isBuiltinDescriptor(info)", replaces = {"callUnarySpecialMethodSlotInlined", "callBinarySpecialMethodSlotInlined", "callTernarySpecialMethodSlotInlined"})
    @InliningCutoff
    Object callSpecialMethodSlotCallTarget(VirtualFrame frame, BuiltinMethodDescriptor info, Object receiver,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached InlinedConditionProfile invalidArgsProfile,
                    @Cached GenericInvokeNode invokeNode) {
        raiseInvalidArgsNumUncached(invalidArgsProfile.profile(inliningTarget, hasAllowedArgsNum(info)), info);
        RootCallTarget callTarget = PythonLanguage.get(this).getDescriptorCallTarget(info);
        Object[] arguments = PArguments.create(1);
        PArguments.setArgument(arguments, 0, receiver);
        return invokeNode.execute(frame, callTarget, arguments);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null"}, //
                    limit = "getCallSiteInlineCacheMaxDepth()")
    Object callObjectSingle(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinFunction cachedFunc,
                    @Cached("getBuiltin(frame, func, 1)") PythonBuiltinBaseNode builtinNode) {
        return callUnaryBuiltin(frame, builtinNode, receiver);
    }

    @Specialization(guards = {"func.getCallTarget() == ct", "builtinNode != null"}, //
                    limit = "getCallSiteInlineCacheMaxDepth()")
    Object callObject(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction func, Object receiver,
                    @SuppressWarnings("unused") @Cached(value = "func.getCallTarget()") RootCallTarget ct,
                    @Cached("getBuiltin(frame, func, 1)") PythonBuiltinBaseNode builtinNode) {
        return callUnaryBuiltin(frame, builtinNode, receiver);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null", "!takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object receiver,
                    @SuppressWarnings("unused") @Cached("func") PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getBuiltinFunction(), 1)") PythonBuiltinBaseNode builtinNode) {
        return callUnaryBuiltin(frame, builtinNode, receiver);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func, getCt) == ct", "!takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object receiver,
                    @SuppressWarnings("unused") @Shared @Cached GetCallTargetNode getCt,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func, getCt)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getBuiltinFunction(), 1)") PythonBuiltinBaseNode builtinNode) {
        return callUnaryBuiltin(frame, builtinNode, receiver);
    }

    @Specialization(guards = {"isSingleContext()", "func == cachedFunc", "builtinNode != null", "takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callSelfMethodSingleContext(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg,
                    @SuppressWarnings("unused") @Cached(value = "func", weak = true) PBuiltinMethod cachedFunc,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getBuiltinFunction(), 2)") PythonBuiltinBaseNode builtinNode) {
        return callBinaryBuiltin(frame, builtinNode, func.getSelf(), arg);
    }

    @Specialization(guards = {"builtinNode != null", "getCallTarget(func, getCt) == ct", "takesSelfArg"}, limit = "getCallSiteInlineCacheMaxDepth()")
    Object callSelfMethod(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinMethod func, Object arg,
                    @SuppressWarnings("unused") @Shared @Cached GetCallTargetNode getCt,
                    @SuppressWarnings("unused") @Cached("getCallTarget(func, getCt)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached("takesSelfArg(func)") boolean takesSelfArg,
                    @Cached("getBuiltin(frame, func.getBuiltinFunction(), 2)") PythonBuiltinBaseNode builtinNode) {
        return callBinaryBuiltin(frame, builtinNode, func.getSelf(), arg);
    }

    @Specialization(guards = "!isBuiltinDescriptor(func)", replaces = {"callObjectSingle", "callObject", "callMethodSingleContext", "callSelfMethodSingleContext", "callMethod", "callSelfMethod"})
    @Megamorphic
    @InliningCutoff
    static Object call(VirtualFrame frame, Object func, Object receiver,
                    @Bind("this") Node inliningTarget,
                    @Cached CallNode callNode,
                    @Exclusive @Cached InlinedConditionProfile isBoundProfile) {
        if (isBoundProfile.profile(inliningTarget, func instanceof BoundDescriptor)) {
            return callNode.execute(frame, ((BoundDescriptor) func).descriptor, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS);
        } else {
            return callNode.execute(frame, func, new Object[]{receiver}, PKeyword.EMPTY_KEYWORDS);
        }
    }
}
