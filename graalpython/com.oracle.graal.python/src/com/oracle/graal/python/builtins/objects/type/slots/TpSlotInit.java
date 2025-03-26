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
package com.oracle.graal.python.builtins.objects.type.slots;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CreateArgsTupleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.InitCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode.CreateAndCheckArgumentsNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class TpSlotInit {
    private TpSlotInit() {
    }

    public abstract static class TpSlotInitBuiltin<T extends PythonBuiltinBaseNode> extends TpSlotBuiltin<T> {
        final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();
        /*
         * TODO these should be just final, but we currently can't initialize them in the
         * constructor because of circular dependency between @Builtin and PBCT.nil
         */
        @CompilationFinal boolean directInvocation;
        @CompilationFinal Signature signature;
        @CompilationFinal Object[] defaults;

        protected TpSlotInitBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        final PythonBuiltinBaseNode createSlotNodeIfDirect() {
            return directInvocation ? createNode() : null;
        }

        public Signature getSignature() {
            return signature;
        }

        public Object[] getDefaults() {
            return defaults;
        }

        @Override
        public final void initialize(PythonLanguage language) {
            Class<T> nodeClass = getNodeFactory().getNodeClass();
            SlotSignature slotSignature = nodeClass.getAnnotation(SlotSignature.class);
            Slot2Builtin builtin = new Slot2Builtin(slotSignature, J___INIT__, null);
            signature = BuiltinFunctionRootNode.createSignature(getNodeFactory(), builtin, true, false);
            defaults = new Object[PythonBuiltins.numDefaults(builtin)];
            Arrays.fill(defaults, PNone.NO_VALUE);
            directInvocation = PythonUnaryBuiltinNode.class.isAssignableFrom(nodeClass) || PythonBinaryBuiltinNode.class.isAssignableFrom(nodeClass) || //
                            PythonTernaryBuiltinNode.class.isAssignableFrom(nodeClass) || PythonVarargsBuiltinNode.class.isAssignableFrom(nodeClass);
            RootCallTarget callTarget = createSlotCallTarget(language, null, getNodeFactory(), J___INIT__);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return createBuiltin(core, type, tsName, null, wrapper, getNodeFactory());
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ReportPolymorphism
    public abstract static class CallSlotTpInitNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, J___INIT__);

        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object[] args, PKeyword[] keywords);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static void callCachedBuiltin(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") TpSlotInitBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotInitBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNodeIfDirect()") PythonBuiltinBaseNode slotNode,
                        @Cached DispatchSlotFullDirectNode dispatchFullNode) {
            if (slotNode != null) {
                if (slotNode instanceof PythonUnaryBuiltinNode unaryBuiltinNode && keywords.length == 0 && args.length == 0) {
                    unaryBuiltinNode.execute(frame, self);
                    return;
                } else if (slotNode instanceof PythonBinaryBuiltinNode binaryBuiltinNode && keywords.length == 0) {
                    if (args.length == 1) {
                        binaryBuiltinNode.execute(frame, self, args[0]);
                        return;
                    }
                    int numDefaults = cachedSlot.getDefaults().length;
                    if (args.length == 0 && numDefaults >= 1) {
                        binaryBuiltinNode.execute(frame, self, PNone.NO_VALUE);
                        return;
                    }
                } else if (slotNode instanceof PythonTernaryBuiltinNode ternaryBuiltinNode && keywords.length == 0) {
                    if (args.length == 2) {
                        ternaryBuiltinNode.execute(frame, self, args[0], args[1]);
                        return;
                    }
                    int numDefaults = cachedSlot.getDefaults().length;
                    if (args.length == 1 && numDefaults >= 1) {
                        ternaryBuiltinNode.execute(frame, self, args[0], PNone.NO_VALUE);
                        return;
                    }
                    if (args.length == 0 && numDefaults >= 2) {
                        ternaryBuiltinNode.execute(frame, self, PNone.NO_VALUE, PNone.NO_VALUE);
                        return;
                    }
                } else if (slotNode instanceof PythonVarargsBuiltinNode varargsBuiltinNode) {
                    varargsBuiltinNode.execute(frame, self, args, keywords);
                    return;
                }
            }
            dispatchFullNode.execute(frame, inliningTarget, cachedSlot, self, args, keywords);
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class DispatchSlotFullDirectNode extends Node {

            public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlotInitBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords);

            @Specialization
            static Object call(VirtualFrame frame, Node inliningTarget, TpSlotInitBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                            @Bind PythonLanguage language,
                            @Bind("language.getBuiltinSlotCallTarget(slot.callTargetIndex)") RootCallTarget callTarget,
                            @Cached CreateAndCheckArgumentsNode createArgumentsNode,
                            @Cached CallContext callContext,
                            @Cached InlinedConditionProfile frameProfile,
                            @Cached(parameters = "callTarget") DirectCallNode callNode) {
                CompilerAsserts.partialEvaluationConstant(slot);
                Object[] arguments = createArgumentsNode.execute(inliningTarget, T___INIT__, args, keywords, slot.getSignature(), self, null, slot.getDefaults(), PKeyword.EMPTY_KEYWORDS, false);
                if (frameProfile.profile(inliningTarget, frame != null)) {
                    callContext.prepareCall(frame, arguments, callTarget, inliningTarget);
                    return callNode.call(arguments);
                } else {
                    PythonContext context = PythonContext.get(inliningTarget);
                    PythonThreadState threadState = context.getThreadState(language);
                    Object state = IndirectCalleeContext.enter(threadState, arguments, callTarget);
                    try {
                        return callNode.call(arguments);
                    } finally {
                        IndirectCalleeContext.exit(threadState, state);
                    }
                }
            }
        }

        @Specialization
        static void callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached MaybeBindDescriptorNode bindDescriptorNode,
                        @Cached(inline = false) CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object bound = bindDescriptorNode.execute(frame, inliningTarget, slot.getCallable(), self, slot.getType());
            Object callable;
            Object[] callArgs;
            if (bound instanceof BoundDescriptor boundDescriptor) {
                callable = boundDescriptor.descriptor;
                callArgs = args;
            } else {
                callable = slot.getCallable();
                callArgs = new Object[args.length + 1];
                callArgs[0] = self;
                PythonUtils.arraycopy(args, 0, callArgs, 1, args.length);
            }
            Object result = callNode.execute(frame, callable, callArgs, keywords);
            if (result != PNone.NONE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.SHOULD_RETURN_NONE, "__init__()");
            }
        }

        @Specialization
        static void callNative(VirtualFrame frame, Node inliningTarget, TpSlot.TpSlotCExtNative slot, Object self, Object[] args, PKeyword[] keywords,
                        @Bind PythonContext context,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Cached CreateArgsTupleNode createArgsTupleNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached InitCheckFunctionResultNode checkResult) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, context);
            // TODO eager native tuple
            Object argsTuple = createArgsTupleNode.execute(language, args, false);
            Object kwargsDict = PFactory.createDict(language, keywords);
            Object nativeResult = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, T___INIT__, slot.callable,
                            toNativeNode.execute(self), toNativeNode.execute(argsTuple), toNativeNode.execute(kwargsDict));
            checkResult.execute(state, T___INIT__, nativeResult);
        }

        @Specialization(replaces = "callCachedBuiltin")
        static void callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotInitBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached CreateAndCheckArgumentsNode createArgumentsNode,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = createArgumentsNode.execute(inliningTarget, T___INIT__, args, keywords, slot.getSignature(), self, null, slot.getDefaults(), PKeyword.EMPTY_KEYWORDS,
                            false);
            BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
