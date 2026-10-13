/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNodeGen;
import com.oracle.graal.python.nodes.call.CallDispatchersFactory.FunctionIndirectInvokeNodeGen;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public class CallDispatchers {

    @NeverDefault
    public static DirectCallNode createDirectCallNodeFor(PBuiltinFunction callee) {
        DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callee.getCallTarget());
        if (PythonLanguage.get(null).getEngineOption(PythonOptions.EnableForcedSplits) || callee.forceSplitDirectCalls()) {
            callNode.cloneCallTarget();
        }
        return callNode;
    }

    @NeverDefault
    public static DirectCallNode createDirectCallNodeFor(PFunction callee) {
        return Truffle.getRuntime().createDirectCallNode(callee.getCallTarget());
    }

    /**
     * Node for invoking call targets of builtin functions, modules or generators when the call
     * target is a PE constant. Use {@link #createDirectCallNodeFor(PBuiltinFunction)} to create the
     * direct call node for builtin functions. Takes PArguments
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SimpleDirectInvokeNode extends Node {

        abstract Object executeImpl(VirtualFrame frame, Node inliningTarget, Class<? extends RootNode> rootType, DirectCallNode callNode, Object[] arguments);

        public final Object executeBuiltin(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, Object[] arguments) {
            return executeImpl(frame, inliningTarget, BuiltinFunctionRootNode.class, callNode, arguments);
        }

        public final Object executePFunction(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, Object[] arguments) {
            return executeImpl(frame, inliningTarget, PBytecodeDSLRootNodeGen.class, callNode, arguments);
        }

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, Class<? extends RootNode> rootType, DirectCallNode callNode, Object[] arguments,
                        @Cached InlinedConditionProfile profileIsNullFrame,
                        @Cached ExecutionContext.CallContext callContext) {
            RootCallTarget callTarget = (RootCallTarget) callNode.getCurrentCallTarget();
            if (profileIsNullFrame.profile(inliningTarget, frame == null)) {
                return framelessCall(inliningTarget, callNode, arguments);
            } else {
                callContext.prepareCall(frame, inliningTarget, arguments, callTarget);
                return callNode.call(rootType, -1, arguments);
            }
        }

        @InliningCutoff
        private static Object framelessCall(Node inliningTarget, DirectCallNode callNode, Object[] arguments) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            Object state = IndirectCalleeContext.enter(threadState, arguments);
            try {
                return callNode.call(arguments);
            } finally {
                IndirectCalleeContext.exit(threadState, state);
            }
        }
    }

    /**
     * Node for invoking call targets of builtin functions, modules or generators when the call
     * target is dynamic. Takes PArguments
     */
    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class SimpleIndirectInvokeNode extends Node {

        public abstract Object executeBuiltin(Frame frame, Node inliningTarget, BuiltinFunctionRootNode rootNode, Object[] arguments);

        public abstract Object executePFunction(Frame frame, Node inliningTarget, PBytecodeDSLRootNode rootNode, Object[] arguments);

        public static Object executeUncachedBuiltin(BuiltinFunctionRootNode rootNode, Object[] arguments) {
            return CallDispatchersFactory.SimpleIndirectInvokeNodeGen.getUncached().executeBuiltin(null, null, rootNode, arguments);
        }

        public static Object executeUncachedPFunction(PBytecodeDSLRootNode rootNode, Object[] arguments) {
            return CallDispatchersFactory.SimpleIndirectInvokeNodeGen.getUncached().executePFunction(null, null, rootNode, arguments);
        }

        public static void executeUncachedPCode(PCode code, Object[] arguments) {
            code.initializeCallTarget();
            executeUncachedPFunction(code.getRootNode(), arguments);
        }

        @Specialization
        static Object doIndirect(VirtualFrame frame, Node inliningTarget, PRootNode rootNode, Object[] arguments,
                        @Cached InlinedConditionProfile profileIsNullFrame,
                        @Cached ExecutionContext.CallContext callContext,
                        @Cached IndirectCallNode callNode) {
            if (profileIsNullFrame.profile(inliningTarget, frame == null)) {
                return framelessCall(inliningTarget, rootNode, arguments, callNode);
            } else {
                callContext.prepareCall(frame, inliningTarget, arguments, rootNode);
                return callNode.call(rootNode, -1, arguments);
            }
        }

        @InliningCutoff
        private static Object framelessCall(Node inliningTarget, PRootNode rootNode, Object[] arguments, IndirectCallNode callNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            Object state = IndirectCalleeContext.enter(threadState, arguments);
            try {
                return callNode.call(rootNode, -1, arguments);
            } finally {
                IndirectCalleeContext.exit(threadState, state);
            }
        }
    }

    /**
     * Node for invoking arbitrary type of call target using the same calling conventions as builtins or Python functions
     * invoked via {@link SimpleIndirectInvokeNode}.
     */
    @GenerateInline(false) // used only as cached
    @GenerateUncached
    public abstract static class SimpleGenericIndirectInvokeNode extends Node {

        public abstract Object execute(Frame frame, RootCallTarget callTarget, Object[] arguments);

        public static Object executeUncached(RootCallTarget callTarget, Object[] arguments) {
            return CallDispatchersFactory.SimpleGenericIndirectInvokeNodeGen.getUncached().execute(null, callTarget, arguments);
        }

        @Specialization
        static Object doIndirect(VirtualFrame frame, RootCallTarget callTarget, Object[] arguments,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile profileIsNullFrame,
                        @Cached ExecutionContext.CallContext callContext,
                        @Cached IndirectCallNode callNode) {
            if (profileIsNullFrame.profile(inliningTarget, frame == null)) {
                return framelessCall(inliningTarget, callTarget, arguments, callNode);
            } else {
                callContext.prepareCall(frame, inliningTarget, arguments, callTarget);
                return callNode.call(callTarget, arguments);
            }
        }

        @InliningCutoff
        private static Object framelessCall(Node inliningTarget, RootCallTarget callTarget, Object[] arguments, IndirectCallNode callNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            Object state = IndirectCalleeContext.enter(threadState, arguments);
            try {
                return callNode.call(callTarget, arguments);
            } finally {
                IndirectCalleeContext.exit(threadState, state);
            }
        }

        @NeverDefault
        public static SimpleGenericIndirectInvokeNode create() {
            return CallDispatchersFactory.SimpleGenericIndirectInvokeNodeGen.create();
        }
    }

    /**
     * Node for invoking builtin functions with an inline cache on the function object and a
     * secondary inline cache on the call target. Takes PArguments
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
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            return invoke.executeBuiltin(frame, inliningTarget, callNode, arguments);
        }

        @Specialization(guards = "callee.getFunctionRootNode() == rootNode", limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callBuiltinFunctionCached")
        static Object callBuiltinFunctionCachedCt(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PBuiltinFunction callee, Object[] arguments,
                        @SuppressWarnings("unused") @Cached(value = "callee.getFunctionRootNode()", adopt = false) BuiltinFunctionRootNode rootNode,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            return invoke.executeBuiltin(frame, inliningTarget, callNode, arguments);
        }

        @Specialization(replaces = {"callBuiltinFunctionCached", "callBuiltinFunctionCachedCt"})
        @Megamorphic
        @InliningCutoff
        static Object callBuiltinFunctionMegamorphic(VirtualFrame frame, Node inliningTarget, PBuiltinFunction callee, Object[] arguments,
                        @Exclusive @Cached SimpleIndirectInvokeNode invoke) {
            callee.initCallTarget();
            return invoke.executeBuiltin(frame, inliningTarget, callee.getFunctionRootNode(), arguments);
        }
    }

    /**
     * Node for calling builtin functions with an inline cache on the function object and a
     * secondary inline cache on the call target.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class BuiltinFunctionCachedCallNode extends PNodeWithContext {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PBuiltinFunction callee, Object[] arguments, PKeyword[] keywords);

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()")
        static Object callBuiltinFunctionCached(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PBuiltinFunction callee, Object[] arguments, PKeyword[] keywords,
                        @Cached("callee") PBuiltinFunction cachedCallee,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            Object[] pArguments = createArgs.execute(inliningTarget, cachedCallee, arguments, keywords, cachedCallee.getSignature(), null, null,
                            cachedCallee.getDefaults(), cachedCallee.getKwDefaults(), false);
            return invoke.executeBuiltin(frame, inliningTarget, callNode, pArguments);
        }

        @Specialization(guards = "callee.getFunctionRootNode() == rootNode", limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callBuiltinFunctionCached")
        static Object callBuiltinFunctionCachedCt(VirtualFrame frame, Node inliningTarget, PBuiltinFunction callee, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached(value = "callee.getFunctionRootNode()", adopt = false) BuiltinFunctionRootNode rootNode,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            Signature signature;
            /*
             * The signature is PE-constant for a given callTarget, but getting it from there needs
             * multiple virtual calls in the interpreter.
             */
            if (CompilerDirectives.inCompiledCode()) {
                signature = Signature.fromCallTarget((RootCallTarget) callNode.getCallTarget());
            } else {
                signature = callee.getSignature();
            }
            Object[] pArguments = createArgs.execute(inliningTarget, callee, arguments, keywords, signature, null, null,
                            callee.getDefaults(), callee.getKwDefaults(), false);
            return invoke.executeBuiltin(frame, inliningTarget, callNode, pArguments);
        }

        @Specialization(replaces = {"callBuiltinFunctionCached", "callBuiltinFunctionCachedCt"})
        @Megamorphic
        @InliningCutoff
        static Object callBuiltinFunctionMegamorphic(VirtualFrame frame, Node inliningTarget, PBuiltinFunction callee, Object[] arguments, PKeyword[] keywords,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Exclusive @Cached SimpleIndirectInvokeNode invoke) {
            Object[] pArguments = createArgs.execute(inliningTarget, callee, arguments, keywords, callee.getSignature(), null, null,
                            callee.getDefaults(), callee.getKwDefaults(), false);
            callee.initCallTarget();
            return invoke.executeBuiltin(frame, inliningTarget, callee.getFunctionRootNode(), pArguments);
        }
    }

    /**
     * Node for calling builtin functions with an inline cache on the method object and a secondary
     * inline cache on the call target.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class BuiltinMethodCachedCallNode extends PNodeWithContext {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PBuiltinMethod callee, Object[] arguments, PKeyword[] keywords);

        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()")
        static Object callBuiltinMethodCached(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PBuiltinMethod callee, Object[] arguments, PKeyword[] keywords,
                        @Cached(value = "callee", weak = true) PBuiltinMethod cachedCallee,
                        @Bind("cachedCallee.getBuiltinFunction()") PBuiltinFunction function,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached("createDirectCallNodeFor(function)") DirectCallNode callNode,
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            Object[] pArguments = createArgs.execute(inliningTarget, cachedCallee, arguments, keywords, function.getSignature(), callee.getSelf(), callee.getClassObject(),
                            function.getDefaults(), function.getKwDefaults(), isMethodcall(callee));
            return invoke.executeBuiltin(frame, inliningTarget, callNode, pArguments);
        }

        @Specialization(guards = "function.getFunctionRootNode() == rootNode", limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callBuiltinMethodCached")
        static Object callBuiltinMethodCachedCt(VirtualFrame frame, Node inliningTarget, PBuiltinMethod callee, Object[] arguments, PKeyword[] keywords,
                        @Bind("callee.getBuiltinFunction()") PBuiltinFunction function,
                        @SuppressWarnings("unused") @Cached(value = "function.getFunctionRootNode()", adopt = false) BuiltinFunctionRootNode rootNode,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached("createDirectCallNodeFor(function)") DirectCallNode callNode,
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            Signature signature;
            /*
             * The signature is PE-constant for a given callTarget, but getting it from there needs
             * multiple virtual calls in the interpreter.
             */
            if (CompilerDirectives.inCompiledCode()) {
                signature = Signature.fromCallTarget((RootCallTarget) callNode.getCallTarget());
            } else {
                signature = function.getSignature();
            }
            Object[] pArguments = createArgs.execute(inliningTarget, callee, arguments, keywords, signature, callee.getSelf(), callee.getClassObject(),
                            function.getDefaults(), function.getKwDefaults(), isMethodcall(callee));
            return invoke.executeBuiltin(frame, inliningTarget, callNode, pArguments);
        }

        @Specialization(replaces = {"callBuiltinMethodCached", "callBuiltinMethodCachedCt"})
        @Megamorphic
        @InliningCutoff
        static Object callBuiltinMethodMegamorphic(VirtualFrame frame, Node inliningTarget, PBuiltinMethod callee, Object[] arguments, PKeyword[] keywords,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached SimpleIndirectInvokeNode invoke) {
            PBuiltinFunction function = callee.getBuiltinFunction();
            Object[] pArguments = createArgs.execute(inliningTarget, callee, arguments, keywords, function.getSignature(), callee.getSelf(), callee.getClassObject(),
                            function.getDefaults(), function.getKwDefaults(), isMethodcall(callee));
            function.initCallTarget();
            return invoke.executeBuiltin(frame, inliningTarget, function.getFunctionRootNode(), pArguments);
        }

        private static boolean isMethodcall(PBuiltinMethod callee) {
            return !(callee.getSelf() instanceof PythonModule);
        }
    }

    /**
     * Node for invoking python functions when the call target of the function is PE constant (the
     * function itself doesn't have to be). Use {@link #createDirectCallNodeFor(PFunction)} to
     * create the call node. Takes PArguments
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FunctionDirectInvokeNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, PFunction callee, Object[] arguments);

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, DirectCallNode callNode, PFunction callee, Object[] arguments,
                        @Cached SimpleDirectInvokeNode invoke) {
            assert callee.getCallTarget() == callNode.getCallTarget() : String.format("%s != %s", callee.getCallTarget(), callNode.getCallTarget());
            PArguments.setGlobals(arguments, callee.getGlobals());
            PArguments.setFunctionObject(arguments, callee);
            return invoke.executePFunction(frame, inliningTarget, callNode, arguments);
        }
    }

    /**
     * Node for invoking python functions when the call target is dynamic. Takes PArguments
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class FunctionIndirectInvokeNode extends Node {

        public static FunctionIndirectInvokeNode getUncached() {
            return FunctionIndirectInvokeNodeGen.getUncached();
        }

        public abstract Object execute(Frame frame, Node inliningTarget, PFunction callee, Object[] arguments);

        @Specialization
        static Object doDirect(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments,
                        @Cached SimpleIndirectInvokeNode invoke) {
            PArguments.setGlobals(arguments, callee.getGlobals());
            PArguments.setFunctionObject(arguments, callee);
            callee.initCallTarget();
            return invoke.executePFunction(frame, inliningTarget, callee.getRootNode(), arguments);
        }
    }

    /**
     * Node for invoking python functions with an inline cache on the function object and a
     * secondary inline cache on the call target. Takes PArguments
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
                        @Exclusive @Cached FunctionDirectInvokeNode invoke) {
            return invoke.execute(frame, inliningTarget, callNode, cachedCallee, arguments);
        }

        // We have multiple contexts, don't cache the objects so that contexts can be cleaned up
        @Specialization(guards = {"callee.getCode().getRootNode() == rootNode"}, limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callFunctionCached")
        static Object callFunctionCachedCt(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments,
                        @SuppressWarnings("unused") @Cached(value = "callee.getCode().getRootNode()", adopt = false) PBytecodeDSLRootNode rootNode,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Exclusive @Cached FunctionDirectInvokeNode invoke) {
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
     * Node for calling python functions with an inline cache on the function object and a secondary
     * inline cache on the call target.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class FunctionCachedCallNode extends PNodeWithContext {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments, PKeyword[] keywords);

        // We only have a single context and this function never changed its code
        @Specialization(guards = {"isSingleContext()", "callee == cachedCallee"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "cachedCallee.getCodeStableAssumption()")
        static Object callFunctionCached(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PFunction callee, Object[] arguments, PKeyword[] keywords,
                        @Cached(value = "callee", weak = true) PFunction cachedCallee,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Exclusive @Cached FunctionDirectInvokeNode invoke) {
            Object[] pArguments = createArgs.execute(inliningTarget, cachedCallee, arguments, keywords, cachedCallee.getCode().getSignature(), null, null,
                            cachedCallee.getDefaults(), cachedCallee.getKwDefaults(), false);
            return invoke.execute(frame, inliningTarget, callNode, cachedCallee, pArguments);
        }

        // We have multiple contexts, don't cache the objects so that contexts can be cleaned up
        @Specialization(guards = {"callee.getCode().getRootNode() == rootNode"}, limit = "getCallSiteInlineCacheMaxDepth()", replaces = "callFunctionCached")
        static Object callFunctionCachedCt(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached(value = "callee.getCode().getRootNode()", adopt = false) PBytecodeDSLRootNode rootNode,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached("createDirectCallNodeFor(callee)") DirectCallNode callNode,
                        @Exclusive @Cached FunctionDirectInvokeNode invoke) {
            Signature signature;
            /*
             * The signature is PE-constant for a given callTarget, but getting it from there needs
             * multiple virtual calls in the interpreter.
             */
            if (CompilerDirectives.inCompiledCode()) {
                signature = Signature.fromCallTarget((RootCallTarget) callNode.getCallTarget());
            } else {
                signature = callee.getCode().getSignature();
            }
            Object[] pArguments = createArgs.execute(inliningTarget, callee, arguments, keywords, signature, null, null,
                            callee.getDefaults(), callee.getKwDefaults(), false);
            return invoke.execute(frame, inliningTarget, callNode, callee, pArguments);
        }

        @Specialization(replaces = {"callFunctionCached", "callFunctionCachedCt"})
        @Megamorphic
        @InliningCutoff
        static Object callFunctionMegamorphic(VirtualFrame frame, Node inliningTarget, PFunction callee, Object[] arguments, PKeyword[] keywords,
                        @Exclusive @Cached CreateArgumentsNode createArgs,
                        @Cached FunctionIndirectInvokeNode invoke) {
            Object[] pArguments = createArgs.execute(inliningTarget, callee, arguments, keywords, callee.getCode().getSignature(), null, null,
                            callee.getDefaults(), callee.getKwDefaults(), false);
            return invoke.execute(frame, inliningTarget, callee, pArguments);
        }
    }

    /**
     * Node for invoking a call target with an inline cache. Takes PArguments.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic({CallDispatchers.class, PythonOptions.class})
    public abstract static class PFunctionCallTargetCachedInvokeNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PBytecodeDSLRootNode rootNode, Object[] pythonArguments);

        static RootCallTarget getCallTarget(PBytecodeDSLRootNode rootNode) {
            CompilerAsserts.neverPartOfCompilation();
            return rootNode.getCallTarget();
        }

        static boolean sameRootNode(PBytecodeDSLRootNode rootNode, DirectCallNode callNode) {
            return rootNode == ((RootCallTarget) callNode.getCallTarget()).getRootNode();
        }

        @Specialization(guards = "sameRootNode(rootNode, callNode)", limit = "getCallSiteInlineCacheMaxDepth()", excludeForUncached = true)
        static Object doCallTargetDirectPFunction(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") PBytecodeDSLRootNode rootNode, Object[] args,
                        @Exclusive @Cached(parameters = "getCallTarget(rootNode)") DirectCallNode callNode,
                        @Exclusive @Cached SimpleDirectInvokeNode invoke) {
            return invoke.executePFunction(frame, inliningTarget, callNode, args);
        }

        @Specialization(replaces = "doCallTargetDirectPFunction")
        static Object doCallTargetIndirect(VirtualFrame frame, Node inliningTarget, PBytecodeDSLRootNode rootNode, Object[] args,
                        @Cached SimpleIndirectInvokeNode invoke) {
            return invoke.executePFunction(frame, inliningTarget, rootNode, args);
        }
    }
}
