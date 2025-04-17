/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public class CallDispatchers {

    private static boolean isGeneratorFunction(RootCallTarget callTarget) {
        return callTarget.getRootNode() instanceof PBytecodeGeneratorFunctionRootNode || callTarget.getRootNode() instanceof PBytecodeDSLGeneratorFunctionRootNode;
    }

    @NeverDefault
    public static DirectCallNode createDirectCallNodeFor(PBuiltinFunction callee) {
        DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callee.getCallTarget());
        if (PythonLanguage.get(null).getEngineOption(PythonOptions.EnableForcedSplits) ||
                        (callee.getFunctionRootNode() instanceof BuiltinFunctionRootNode root && root.getBuiltin().forceSplitDirectCalls())) {
            callNode.cloneCallTarget();
        }
        return callNode;
    }

    @NeverDefault
    public static DirectCallNode createDirectCallNodeFor(PFunction callee) {
        boolean isGenerator = isGeneratorFunction(callee.getCallTarget());
        DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callee.getCallTarget());
        if (callee.forceSplitDirectCalls()) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && PythonLanguage.get(null).getEngineOption(PythonOptions.ForceInlineGeneratorCalls)) {
            callNode.forceInlining();
        }
        return callNode;
    }

    public static boolean sameCallTarget(RootCallTarget callTarget, DirectCallNode callNode) {
        return callTarget == callNode.getCallTarget();
    }

    /**
     * Node for invoking call targets of builtin functions, modules or generators when the call
     * target is a PE constant. Use {@link #createDirectCallNodeFor(PBuiltinFunction)} to create the
     * direct call node for builtin functions.
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SimpleDirectInvokeNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, Object[] arguments);

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, Object[] arguments,
                        @Cached InlinedConditionProfile profileIsNullFrame,
                        @Cached ExecutionContext.CallContext callContext) {
            RootCallTarget callTarget = (RootCallTarget) callNode.getCurrentCallTarget();
            if (profileIsNullFrame.profile(inliningTarget, frame == null)) {
                PythonContext context = PythonContext.get(inliningTarget);
                PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
                Object state = IndirectCalleeContext.enter(threadState, arguments, callTarget);
                try {
                    return callNode.call(arguments);
                } finally {
                    IndirectCalleeContext.exit(threadState, state);
                }
            } else {
                callContext.prepareCall(frame, arguments, callTarget, callNode);
                return callNode.call(arguments);
            }
        }
    }

    /**
     * Node for invoking call targets of builtin functions, modules or generators when the call
     * target is dynamic.
     */
    @GenerateInline(inlineByDefault = true)
    @GenerateUncached
    public abstract static class SimpleIndirectInvokeNode extends Node {

        public abstract Object execute(Frame frame, Node inliningTarget, RootCallTarget callTarget, Object[] arguments);

        public final Object executeCached(VirtualFrame frame, RootCallTarget callTarget, Object[] arguments) {
            return execute(frame, this, callTarget, arguments);
        }

        public static Object executeUncached(RootCallTarget callTarget, Object[] arguments) {
            return CallDispatchersFactory.SimpleIndirectInvokeNodeGen.getUncached().execute(null, null, callTarget, arguments);
        }

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, RootCallTarget callTarget, Object[] arguments,
                        @Cached InlinedConditionProfile profileIsNullFrame,
                        @Cached ExecutionContext.CallContext callContext,
                        @Cached IndirectCallNode callNode) {
            if (profileIsNullFrame.profile(inliningTarget, frame == null)) {
                PythonContext context = PythonContext.get(inliningTarget);
                PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
                Object state = IndirectCalleeContext.enterIndirect(threadState, arguments);
                try {
                    return callNode.call(callTarget, arguments);
                } finally {
                    IndirectCalleeContext.exit(threadState, state);
                }
            } else {
                callContext.prepareIndirectCall(frame, arguments, callNode);
                return callNode.call(callTarget, arguments);
            }
        }

        @NeverDefault
        public static SimpleIndirectInvokeNode create() {
            return CallDispatchersFactory.SimpleIndirectInvokeNodeGen.create();
        }
    }

    /**
     * Node for invoking builtin functions with an inline cache on the function object and a
     * secondary inline cache on the call target.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class BuiltinFunctionCachedInvokeNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PBuiltinFunction callee, Object[] arguments);

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()")
        static Object callBuiltinFunctionCached(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PBuiltinFunction callee, Object[] arguments,
                        @SuppressWarnings("unused") @Cached("callee") PBuiltinFunction cachedCallee,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Cached SimpleDirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callNode, arguments);
        }

        @Specialization(guards = "sameCallTarget(callee.getCallTarget(), callNode)", limit = "getCallSiteInlineCacheMaxDepth()")
        static Object callBuiltinFunctionCachedCt(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PBuiltinFunction callee, Object[] arguments,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Cached SimpleDirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callNode, arguments);
        }

        @Specialization(replaces = {"callBuiltinFunctionCached", "callBuiltinFunctionCachedCt"})
        @Megamorphic
        @InliningCutoff
        static Object callBuiltinFunctionMegamorphic(VirtualFrame frame, Node inliningTarget, PBuiltinFunction callee, Object[] arguments,
                        @Cached SimpleIndirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callee.getCallTarget(), arguments);
        }
    }

    /**
     * Node for invoking python functions when the call target of the function is PE constant (the
     * function itself doesn't have to be). Use {@link #createDirectCallNodeFor(PFunction)} to
     * create the call node.
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FunctionDirectInvokeNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, PFunction callee, Object[] arguments);

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, PFunction callee, Object[] arguments,
                        @Cached SimpleDirectInvokeNode invoke) {
            assert callee.getCallTarget() == callNode.getCallTarget();
            PArguments.setGlobals(arguments, callee.getGlobals());
            PArguments.setClosure(arguments, callee.getClosure());
            RootCallTarget callTarget = (RootCallTarget) callNode.getCurrentCallTarget();
            if (isGeneratorFunction(callTarget)) {
                PArguments.setGeneratorFunction(arguments, callee);
            }
            return invoke.execute(frame, inliningTarget, callNode, arguments);
        }
    }

    /**
     * Node for invoking python functions when the call target is dynamic.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class FunctionIndirectInvokeNode extends Node {

        public abstract Object execute(Frame frame, Node inliningTarget, PFunction callee, Object[] arguments);

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments,
                        @Cached SimpleIndirectInvokeNode invoke,
                        @Cached InlinedConditionProfile generatorProfile) {
            PArguments.setGlobals(arguments, callee.getGlobals());
            PArguments.setClosure(arguments, callee.getClosure());
            RootCallTarget callTarget = callee.getCallTarget();
            if (generatorProfile.profile(inliningTarget, isGeneratorFunction(callTarget))) {
                PArguments.setGeneratorFunction(arguments, callee);
            }
            return invoke.execute(frame, inliningTarget, callTarget, arguments);
        }
    }

    /**
     * Node for invoking python functions with an inline cache on the function object and a
     * secondary inline cache on the call target.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class FunctionCachedInvokeNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments);

        // We only have a single context and this function never changed its code
        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "cachedCallee.getCodeStableAssumption()")
        static Object callFunctionCached(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PFunction callee, Object[] arguments,
                        @SuppressWarnings("unused") @Cached(value = "callee", weak = true) PFunction cachedCallee,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Cached FunctionDirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callNode, cachedCallee, arguments);
        }

        // We have multiple contexts, don't cache the objects so that contexts can be cleaned up
        @Specialization(guards = {"sameCallTarget(callee.getCallTarget(), callNode)"}, limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callFunctionCached")
        static Object callFunctionCachedCt(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Cached FunctionDirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callNode, callee, arguments);
        }

        @Specialization(replaces = {"callFunctionCached", "callFunctionCachedCt"})
        @Megamorphic
        @InliningCutoff
        static Object callFunctionMegamorphic(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments,
                        @Cached FunctionIndirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callee, arguments);
        }
    }

    /**
     * Node for invoking a call target with an inline cache.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class CallTargetCachedInvokeNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, RootCallTarget callTarget, Object[] pythonArguments);

        @Specialization(guards = "sameCallTarget(callTarget, callNode)", limit = "getCallSiteInlineCacheMaxDepth()")
        static Object doCallTargetDirect(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") RootCallTarget callTarget, Object[] args,
                        @Cached(parameters = "callTarget") DirectCallNode callNode,
                        @Cached SimpleDirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callNode, args);
        }

        @Specialization(replaces = "doCallTargetDirect")
        static Object doCallTargetIndirect(VirtualFrame frame, Node inliningTarget, RootCallTarget callTarget, Object[] args,
                        @Cached SimpleIndirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callTarget, args);
        }
    }
}
