/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.objects.code.CodeNodes.GetCodeCallTargetNode;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionCodeNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PythonOptions.class)
@GenerateUncached
@SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 29
public abstract class CallDispatchNode extends PNodeWithContext {

    @NeverDefault
    protected static FunctionInvokeNode createInvokeNode(PFunction callee) {
        return FunctionInvokeNode.create(callee);
    }

    @NeverDefault
    protected static FunctionInvokeNode createInvokeNode(PBuiltinFunction callee) {
        return FunctionInvokeNode.create(callee);
    }

    @NeverDefault
    protected static CallTargetInvokeNode createCtInvokeNode(PFunction callee) {
        return CallTargetInvokeNode.create(callee);
    }

    @NeverDefault
    protected static CallTargetInvokeNode createCtInvokeNode(PBuiltinFunction callee) {
        return CallTargetInvokeNode.create(callee);
    }

    @NeverDefault
    public static CallDispatchNode create() {
        return CallDispatchNodeGen.create();
    }

    public static CallDispatchNode getUncached() {
        return CallDispatchNodeGen.getUncached();
    }

    public final Object executeCall(VirtualFrame frame, PFunction callee, Object[] arguments) {
        return executeInternal(frame, callee, arguments);
    }

    public final Object executeCall(VirtualFrame frame, PBuiltinFunction callee, Object[] arguments) {
        return executeInternal(frame, callee, arguments);
    }

    protected abstract Object executeInternal(Frame frame, PFunction callee, Object[] arguments);

    protected abstract Object executeInternal(Frame frame, PBuiltinFunction callee, Object[] arguments);

    // We only have a single context and this function never changed its code
    @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "cachedCallee.getCodeStableAssumption()")
    protected static Object callFunctionCached(VirtualFrame frame, @SuppressWarnings("unused") PFunction callee, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("callee") PFunction cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
        return invoke.execute(frame, arguments);
    }

    // We only have a single context and this function changed its code before, but now it's
    // constant
    protected PCode getCode(Node inliningTarget, GetFunctionCodeNode getFunctionCodeNode, PFunction function) {
        return getFunctionCodeNode.execute(inliningTarget, function);
    }

    @Specialization(guards = {"isSingleContext()", "callee == cachedCallee", "getCode(inliningTarget, getFunctionCodeNode, callee) == cachedCode"}, //
                    replaces = "callFunctionCached", limit = "getCallSiteInlineCacheMaxDepth()")
    protected static Object callFunctionCachedCode(VirtualFrame frame, @SuppressWarnings("unused") PFunction callee, Object[] arguments,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("callee") PFunction cachedCallee,
                    @SuppressWarnings("unused") @Cached GetFunctionCodeNode getFunctionCodeNode,
                    @SuppressWarnings("unused") @Cached("getCode(inliningTarget, getFunctionCodeNode, callee)") PCode cachedCode,
                    @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
        return invoke.execute(frame, arguments);
    }

    protected static RootCallTarget getCallTargetUncached(PFunction callee) {
        CompilerAsserts.neverPartOfCompilation();
        return GetCallTargetNode.getUncached().execute(callee);
    }

    // We have multiple contexts, don't cache the objects so that contexts can be cleaned up
    @Specialization(guards = {"getCt.execute(inliningTarget, callee.getCode()) == ct"}, limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callFunctionCachedCode")
    protected static Object callFunctionCachedCt(VirtualFrame frame, PFunction callee, Object[] arguments,
                    @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Cached("getCallTargetUncached(callee)") RootCallTarget ct,
                    @SuppressWarnings("unused") @Cached GetCodeCallTargetNode getCt,
                    @Cached("createCtInvokeNode(callee)") CallTargetInvokeNode invoke) {
        return invoke.execute(frame, callee, callee.getGlobals(), callee.getClosure(), arguments);
    }

    @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()")
    protected static Object callBuiltinFunctionCached(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction callee, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("callee") PBuiltinFunction cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") FunctionInvokeNode invoke) {
        return invoke.execute(frame, arguments);
    }

    @Specialization(guards = "callee.getCallTarget() == ct", limit = "getCallSiteInlineCacheMaxDepth()")
    protected static Object callBuiltinFunctionCachedCt(VirtualFrame frame, @SuppressWarnings("unused") PBuiltinFunction callee, Object[] arguments,
                    @SuppressWarnings("unused") @Cached("callee.getCallTarget()") RootCallTarget ct,
                    @Cached("createCtInvokeNode(callee)") CallTargetInvokeNode invoke) {
        return invoke.execute(frame, null, null, null, arguments);
    }

    @Specialization(replaces = {"callFunctionCached", "callFunctionCachedCode", "callFunctionCachedCt"})
    @Megamorphic
    protected static Object callFunctionUncached(Frame frame, PFunction callee, Object[] arguments,
                    @Shared @Cached GenericInvokeNode invoke) {
        return invoke.executeInternal(frame, callee, arguments);
    }

    @Specialization(replaces = {"callBuiltinFunctionCached", "callBuiltinFunctionCachedCt"})
    @Megamorphic
    protected static Object callBuiltinFunctionUncached(Frame frame, PBuiltinFunction callee, Object[] arguments,
                    @Shared @Cached GenericInvokeNode invoke) {
        return invoke.executeInternal(frame, callee, arguments);
    }
}
