package com.oracle.graal.python.builtins.objects.type.slots;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Python3Core;
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
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class TpSlotInit {
    private TpSlotInit() {
    }

    public abstract static class TpSlotInitBuiltin<T extends PythonBuiltinBaseNode> extends TpSlotBuiltin<T> {
        final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();
        final boolean directOneArg;
        final boolean directTwoArgs;
        Signature signature;

        protected TpSlotInitBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
            Class<T> nodeClass = nodeFactory.getNodeClass();
            directTwoArgs = nodeClass.isAssignableFrom(PythonBinaryBuiltinNode.class);
            /*
             * TODO We also want to allow binary builtins that accept one arg, but we can't access
             * the annotation at this point due to @Builtin <-> PBCT circular dependency
             */
            directOneArg = nodeClass.isAssignableFrom(PythonUnaryBuiltinNode.class);
        }

        final PythonBuiltinBaseNode createSlotNode() {
            return createNode();
        }

        public Signature getSignature() {
            if (signature == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SlotSignature slotSignature = getNodeFactory().getNodeClass().getAnnotation(SlotSignature.class);
                signature = BuiltinFunctionRootNode.createSignature(getNodeFactory(), new Slot2Builtin(slotSignature, J___INIT__, null), true, false);
            }
            return signature;
        }

        @Override
        public final void initialize(PythonLanguage language) {
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
    public abstract static class CallSlotInitNode extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, J___INIT__);

        public abstract void execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, Object[] args, PKeyword[] keywords);

        @Specialization(guards = {"cachedSlot == slot", "directInvocation(slot, args, keywords)"}, limit = "3")
        static void callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotInitBuiltin<?> slot, Object self, Object[] args, PKeyword[] keywords,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotInitBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") PythonBuiltinBaseNode slotNode) {
            if (slotNode instanceof PythonUnaryBuiltinNode unaryBuiltinNode) {
                unaryBuiltinNode.execute(frame, self);
            } else if (slotNode instanceof PythonBinaryBuiltinNode binaryBuiltinNode) {
                binaryBuiltinNode.execute(frame, self, args.length > 0 ? args[0] : PNone.NO_VALUE);
            } else {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        protected static boolean directInvocation(TpSlotInitBuiltin<?> slot, Object[] args, PKeyword[] keywords) {
            return keywords.length == 0 && (slot.directOneArg && args.length == 0 || slot.directTwoArgs && args.length == 1);
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
            Object[] arguments = createArgumentsNode.execute(inliningTarget, T___INIT__, args, keywords, slot.getSignature(), self, null, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS,
                            false);
            BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
