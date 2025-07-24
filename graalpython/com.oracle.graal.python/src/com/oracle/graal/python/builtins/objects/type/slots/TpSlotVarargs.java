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

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CreateArgsTupleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.DefaultCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.EagerTupleState;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.InitCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotCExtNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.call.BoundDescriptor;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class TpSlotVarargs {
    private TpSlotVarargs() {
    }

    public abstract static class TpSlotVarargsBuiltin<T extends PythonBuiltinBaseNode> extends TpSlotBuiltin<T> {
        final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();
        private final String name;
        private final TruffleString tsName;
        protected final boolean directInvocation;
        protected final Signature signature;
        protected final Object[] defaults;
        protected final PKeyword[] kwDefaults;

        protected TpSlotVarargsBuiltin(NodeFactory<T> nodeFactory, String name) {
            this(nodeFactory, name, false);
        }

        protected TpSlotVarargsBuiltin(NodeFactory<T> nodeFactory, String name, boolean takesClass) {
            super(nodeFactory);
            this.name = name;
            this.tsName = PythonUtils.tsLiteral(name);
            Class<T> nodeClass = nodeFactory.getNodeClass();
            SlotSignature slotSignature = nodeClass.getAnnotation(SlotSignature.class);
            Slot2Builtin builtin = new Slot2Builtin(slotSignature, name, null);
            signature = BuiltinFunctionRootNode.createSignature(nodeFactory, builtin, true, takesClass);
            defaults = PBuiltinFunction.generateDefaults(PythonBuiltins.numDefaults(builtin));
            kwDefaults = PBuiltinFunction.generateKwDefaults(signature);
            directInvocation = PythonUnaryBuiltinNode.class.isAssignableFrom(nodeClass) || PythonBinaryBuiltinNode.class.isAssignableFrom(nodeClass) || //
                            PythonTernaryBuiltinNode.class.isAssignableFrom(nodeClass) || PythonVarargsBuiltinNode.class.isAssignableFrom(nodeClass) || //
                            PythonQuaternaryBuiltinNode.class.isAssignableFrom(nodeClass);
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

        public PKeyword[] getKwDefaults() {
            return kwDefaults;
        }

        public TruffleString getName() {
            return tsName;
        }

        @Override
        public final void initialize(PythonLanguage language) {
            RootCallTarget callTarget = createSlotCallTarget(language, null, getNodeFactory(), name);
            language.setBuiltinSlotCallTarget(callTargetIndex, callTarget);
        }

        @Override
        public PythonObject createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return createBuiltin(core, type, tsName, null, wrapper, getNodeFactory());
        }
    }

    public abstract static class TpSlotNewBuiltin<T extends PythonBuiltinBaseNode> extends TpSlotVarargsBuiltin<T> {

        protected TpSlotNewBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory, J___NEW__, true);
        }

        @Override
        public PBuiltinMethod createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            PythonLanguage language = core.getLanguage();
            NodeFactory<T> factory = getNodeFactory();
            Class<T> nodeClass = factory.getNodeClass();
            SlotSignature slotSignature = nodeClass.getAnnotation(SlotSignature.class);
            Slot2Builtin builtin = new Slot2Builtin(slotSignature, J___NEW__, null);
            PythonBuiltinClassType builtinType = type instanceof PythonBuiltinClassType bt ? bt : null;
            /*
             * Note: '__new__' is not a 'wrapper_descriptor', but a 'builtin_function_or_method'.
             * The method holds the declaring type as self and the actual 'cls' argument comes after
             * it. The underlying slot doesn't take the first self. In CPython 'tp_new_wrapper' uses
             * the self to check the call safety and then drops it for the slot call. Our
             * 'WrapTpNew' holds the type in a field and uses that to do the check. The dropping is
             * achieved by using `declaresExplicitSelf = false`.
             */
            RootCallTarget callTarget = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, factory, false, builtinType),
                            nodeClass, nodeClass, builtinType, J___NEW__);
            return PFactory.createNewWrapper(language, type, defaults, kwDefaults, callTarget, this);
        }
    }

    @GenerateCached(false)
    abstract static class CallVarargsTpSlotBaseNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object[] args, PKeyword[] keywords);

        @Specialization(guards = {"frame != null", "cachedSlot == slot"}, limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, Node inliningTarget, @SuppressWarnings("unused") TpSlotVarargsBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotVarargsBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNodeIfDirect()") PythonBuiltinBaseNode slotNode,
                        @Cached DispatchVarargsBuiltinFullDirectNode dispatchFullNode) {
            if (slotNode != null) {
                if (slotNode instanceof PythonUnaryBuiltinNode unaryBuiltinNode) {
                    if (keywords.length == 0 && args.length == 0) {
                        return unaryBuiltinNode.execute(frame, self);
                    }
                } else if (slotNode instanceof PythonBinaryBuiltinNode binaryBuiltinNode) {
                    if (keywords.length == 0 && (args.length == 1 || args.length + cachedSlot.getDefaults().length >= 1 && args.length <= 1)) {
                        return binaryBuiltinNode.execute(frame, self, args.length == 1 ? args[0] : PNone.NO_VALUE);
                    }
                } else if (slotNode instanceof PythonTernaryBuiltinNode ternaryBuiltinNode) {
                    if (keywords.length == 0 && (args.length == 2 || args.length + cachedSlot.getDefaults().length >= 2 && args.length <= 2)) {
                        return ternaryBuiltinNode.execute(frame, self, args.length >= 1 ? args[0] : PNone.NO_VALUE, args.length == 2 ? args[1] : PNone.NO_VALUE);
                    }
                } else if (slotNode instanceof PythonQuaternaryBuiltinNode quaternaryBuiltinNode) {
                    if (keywords.length == 0 && (args.length == 3 || args.length + cachedSlot.getDefaults().length >= 3 && args.length <= 3)) {
                        return quaternaryBuiltinNode.execute(frame, self,
                                        args.length >= 1 ? args[0] : PNone.NO_VALUE,
                                        args.length >= 2 ? args[1] : PNone.NO_VALUE,
                                        args.length == 3 ? args[2] : PNone.NO_VALUE);
                    }
                } else if (slotNode instanceof PythonVarargsBuiltinNode varargsBuiltinNode) {
                    return varargsBuiltinNode.execute(frame, self, args, keywords);
                }
            }
            return dispatchFullNode.execute(frame, inliningTarget, cachedSlot, self, args, keywords);
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotVarargsBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached CreateArgumentsNode createArgumentsNode,
                        @Cached CallDispatchers.SimpleIndirectInvokeNode invoke) {
            Object[] arguments = createArgumentsNode.execute(inliningTarget, slot.getName(), args, keywords, slot.getSignature(), self, null, slot.getDefaults(), slot.getKwDefaults(),
                            false);
            RootCallTarget callTarget = PythonLanguage.get(inliningTarget).getBuiltinSlotCallTarget(slot.callTargetIndex);
            return invoke.execute(frame, inliningTarget, callTarget, arguments);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class DispatchVarargsBuiltinFullDirectNode extends Node {

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlotVarargsBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords);

        @Specialization
        static Object call(VirtualFrame frame, Node inliningTarget, TpSlotVarargsBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached CreateArgumentsNode createArgumentsNode,
                        @Cached("createDirectCallNode(slot)") DirectCallNode callNode,
                        @Cached CallDispatchers.SimpleDirectInvokeNode invoke) {
            CompilerAsserts.partialEvaluationConstant(slot);
            Object[] arguments = createArgumentsNode.execute(inliningTarget, slot.getName(), args, keywords, slot.getSignature(), self, null, slot.getDefaults(), slot.getKwDefaults(), false);
            return invoke.execute(frame, inliningTarget, callNode, arguments);
        }

        @NeverDefault
        protected static DirectCallNode createDirectCallNode(TpSlotVarargsBuiltin<?> slot) {
            return Truffle.getRuntime().createDirectCallNode(PythonLanguage.get(null).getBuiltinSlotCallTarget(slot.callTargetIndex));
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class CallSlotVarargsPythonNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object[] args, PKeyword[] keywords);

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached MaybeBindDescriptorNode bindDescriptorNode,
                        @Cached(inline = false) CallNode callNode) {
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
            return callNode.execute(frame, callable, callArgs, keywords);
        }
    }

    private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "<varargs slot>");

    @GenerateInline(false)
    @GenerateUncached
    abstract static class CallSlotVarargsNativeNode extends Node {

        abstract Object execute(VirtualFrame frame, TpSlotCExtNative slot, Object self, Object[] args, PKeyword[] keywords, TruffleString name, CheckFunctionResultNode checkResultNode,
                        NativeToPythonTransferNode toPythonNode);

        @Specialization
        static Object callNative(VirtualFrame frame, TpSlotCExtNative slot, Object self, Object[] args, PKeyword[] keywords, TruffleString name, CheckFunctionResultNode checkResultNode,
                        NativeToPythonTransferNode toPythonNode,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached PythonToNativeNode toNativeNode,
                        @Cached CreateArgsTupleNode createArgsTupleNode,
                        @Cached EagerTupleState eagerTupleState,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            PythonThreadState state = getThreadStateNode.execute(inliningTarget, context);
            PTuple argsTuple = createArgsTupleNode.execute(inliningTarget, language, args, eagerTupleState);
            Object kwargsDict = keywords.length > 0 ? PFactory.createDict(language, keywords) : NO_VALUE;
            Object nativeResult = externalInvokeNode.call(frame, inliningTarget, state, C_API_TIMING, name, slot.callable,
                            toNativeNode.execute(self), toNativeNode.execute(argsTuple), toNativeNode.execute(kwargsDict));
            eagerTupleState.report(inliningTarget, argsTuple);
            checkResultNode.execute(state, name, nativeResult);
            if (toPythonNode != null) {
                return toPythonNode.execute(nativeResult);
            }
            return NO_VALUE;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ReportPolymorphism
    public abstract static class CallSlotTpInitNode extends CallVarargsTpSlotBaseNode {

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached CallSlotVarargsPythonNode callNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = callNode.execute(frame, inliningTarget, slot, self, args, keywords);
            if (result != PNone.NONE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.SHOULD_RETURN_NONE, "__init__()");
            }
            return PNone.NO_VALUE;
        }

        @Specialization
        @InliningCutoff
        static Object callNative(VirtualFrame frame, TpSlotCExtNative slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached InitCheckFunctionResultNode checkResult,
                        @Cached CallSlotVarargsNativeNode callNode) {
            return callNode.execute(frame, slot, self, args, keywords, T___INIT__, checkResult, null);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class BindNewMethodNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object descriptor, Object type);

        @Specialization(guards = "isStaticmethod(descriptor)")
        static Object doStaticmethod(PDecoratedMethod descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor.getCallable();
        }

        protected static boolean isStaticmethod(PDecoratedMethod descriptor) {
            return descriptor.getInitialPythonClass() == PythonBuiltinClassType.PStaticmethod;
        }

        @Specialization
        static Object doBuiltinMethod(PBuiltinMethod descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Specialization
        static Object doFunction(PFunction descriptor, @SuppressWarnings("unused") Object type) {
            return descriptor;
        }

        @Fallback
        static Object doBind(VirtualFrame frame, Node inliningTarget, Object descriptor, Object type,
                        @Cached GetObjectSlotsNode getSlotsNode,
                        @Cached CallSlotDescrGet callGetSlot) {
            var getMethod = getSlotsNode.execute(inliningTarget, descriptor).tp_descr_get();
            if (getMethod != null) {
                return callGetSlot.execute(frame, inliningTarget, getMethod, descriptor, NO_VALUE, type);
            }
            return descriptor;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ReportPolymorphism
    public abstract static class CallSlotTpNewNode extends CallVarargsTpSlotBaseNode {

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached BindNewMethodNode bindNew,
                        @Cached(inline = false) CallNode callNode) {
            Object callable = bindNew.execute(frame, inliningTarget, slot.getCallable(), self);
            return callNode.execute(frame, callable, PythonUtils.prependArgument(self, args), keywords);
        }

        @Specialization
        @InliningCutoff
        static Object callNative(VirtualFrame frame, TpSlotCExtNative slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached DefaultCheckFunctionResultNode checkResult,
                        @Cached NativeToPythonTransferNode toPythonNode,
                        @Cached CallSlotVarargsNativeNode callNode) {
            return callNode.execute(frame, slot, self, args, keywords, T___NEW__, checkResult, toPythonNode);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ReportPolymorphism
    public abstract static class CallSlotTpCallNode extends CallVarargsTpSlotBaseNode {

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached CallSlotVarargsPythonNode callNode) {
            return callNode.execute(frame, inliningTarget, slot, self, args, keywords);
        }

        @Specialization
        @InliningCutoff
        static Object callNative(VirtualFrame frame, TpSlotCExtNative slot, Object self, Object[] args, PKeyword[] keywords,
                        @Cached DefaultCheckFunctionResultNode checkResult,
                        @Cached NativeToPythonTransferNode toPythonNode,
                        @Cached CallSlotVarargsNativeNode callNode) {
            return callNode.execute(frame, slot, self, args, keywords, T___CALL__, checkResult, toPythonNode);
        }
    }
}
