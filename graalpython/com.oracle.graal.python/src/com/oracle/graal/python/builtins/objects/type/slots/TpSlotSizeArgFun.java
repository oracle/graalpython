/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PyObjectCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonTransferNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.NodeFactoryUtils.WrapperNodeFactory;
import com.oracle.graal.python.builtins.objects.type.slots.PythonDispatchers.BinaryPythonSlotDispatcherNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotNative;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFunFactory.FixNegativeIndexNodeGen;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFunFactory.WrapSqItemBuiltinNodeGen;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
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

public class TpSlotSizeArgFun {
    private TpSlotSizeArgFun() {
    }

    public abstract static class TpSlotSizeArgFunBuiltin<T extends SizeArgFunBuiltinNode> extends TpSlotBuiltin<T> {
        private final int callTargetIndex = TpSlotBuiltinCallTargetRegistry.getNextCallTargetIndex();

        protected TpSlotSizeArgFunBuiltin(NodeFactory<T> nodeFactory) {
            super(nodeFactory);
        }

        final SizeArgFunBuiltinNode createSlotNode() {
            return createNode();
        }

        @Override
        public void initialize(PythonLanguage language) {
            RootCallTarget target = createBuiltinCallTarget(language, BuiltinSlotWrapperSignature.BINARY, getNodeFactory(), J___GETITEM__);
            language.setBuiltinSlotCallTarget(callTargetIndex, target);
        }

        @Override
        public PBuiltinFunction createBuiltin(Python3Core core, Object type, TruffleString tsName, PExternalFunctionWrapper wrapper) {
            return switch (wrapper) {
                case GETITEM -> createBuiltin(core, type, tsName, BuiltinSlotWrapperSignature.BINARY, wrapper,
                                WrapperNodeFactory.wrap(getNodeFactory(), WrapSqItemBuiltinNode.class, WrapSqItemBuiltinNodeGen::create));
                default -> throw new IllegalStateException(Objects.toString(wrapper));
            };
        }
    }

    @GenerateInline(value = false, inherit = true)
    public abstract static class SizeArgFunBuiltinNode extends PythonBinaryBuiltinNode {
        @Override
        public final Object execute(VirtualFrame frame, Object arg, Object arg2) {
            // Since this is just the slot node and the __getitem__ method is wrapped by
            // WrapSqItemBuiltinNode,
            // this "execute" method can be only called from the builtin root node called from the
            // CallSlotSizeArgFun node, which always passes integer
            return execute(frame, arg, (int) arg2);
        }

        public abstract Object execute(VirtualFrame frame, Object self, int index);
    }

    public abstract static class SqItemBuiltinNode extends SizeArgFunBuiltinNode {
    }

    public abstract static class WrapSqItemBuiltinNode extends PythonBinaryBuiltinNode {
        private @Child SizeArgFunBuiltinNode slotNode;
        private @Child FixNegativeIndex fixNegativeIndex;

        protected WrapSqItemBuiltinNode(SizeArgFunBuiltinNode slotNode) {
            this.slotNode = slotNode;
        }

        @Specialization(guards = "index >= 0")
        Object doIntIndex(VirtualFrame frame, Object self, int index) {
            return slotNode.execute(frame, self, index);
        }

        @Specialization(replaces = "doIntIndex")
        @SuppressWarnings("truffle-static-method")
        Object doGeneric(VirtualFrame frame, Object self, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int size = asSizeNode.executeExact(frame, inliningTarget, index, PythonBuiltinClassType.OverflowError);
            if (size < 0) {
                if (fixNegativeIndex == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    fixNegativeIndex = insert(FixNegativeIndexNodeGen.create());
                }
                size = fixNegativeIndex.execute(frame, size, index);
            }
            return slotNode.execute(frame, self, size);
        }
    }

    @GenerateInline(false) // lazy
    public abstract static class FixNegativeIndex extends Node {
        public abstract int execute(VirtualFrame frame, int indexAsSize, Object indexObj);

        @Specialization
        static int doIt(VirtualFrame frame, int indexAsSize, Object indexObj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getIndexSlots,
                        @Cached CallSlotLenNode callSlotLen) {
            assert indexAsSize < 0;
            TpSlots indexSlots = getIndexSlots.execute(inliningTarget, indexObj);
            if (indexSlots.sq_length() != null) {
                int len = callSlotLen.execute(frame, inliningTarget, indexSlots.sq_length(), indexObj);
                return indexAsSize + len;
            }
            return indexAsSize;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallSlotSizeArgFun extends Node {
        private static final CApiTiming C_API_TIMING = CApiTiming.create(true, "ssizeargfun");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TpSlot slot, Object self, int index);

        @Specialization(guards = "cachedSlot == slot", limit = "3")
        static Object callCachedBuiltin(VirtualFrame frame, @SuppressWarnings("unused") TpSlotSizeArgFunBuiltin<?> slot, Object self, int index,
                        @SuppressWarnings("unused") @Cached("slot") TpSlotSizeArgFunBuiltin<?> cachedSlot,
                        @Cached("cachedSlot.createSlotNode()") SizeArgFunBuiltinNode slotNode) {
            return slotNode.execute(frame, self, index);
        }

        @Specialization
        static Object callPython(VirtualFrame frame, Node inliningTarget, TpSlotPythonSingle slot, Object self, int index,
                        @Cached BinaryPythonSlotDispatcherNode dispatcherNode) {
            return dispatcherNode.execute(frame, inliningTarget, slot.getCallable(), slot.getType(), self, index);
        }

        @Specialization
        static Object callNative(VirtualFrame frame, Node inliningTarget, TpSlotNative slot, Object self, int index,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached(inline = false) PythonToNativeNode toNativeNode,
                        @Cached ExternalFunctionInvokeNode externalInvokeNode,
                        @Cached(inline = false) NativeToPythonTransferNode toPythonNode,
                        @Cached(inline = false) PyObjectCheckFunctionResultNode checkResultNode) {
            PythonContext ctx = PythonContext.get(inliningTarget);
            PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, ctx);
            Object result = externalInvokeNode.call(frame, inliningTarget, threadState, C_API_TIMING, T___LEN__, slot.callable, toNativeNode.execute(self), index);
            return checkResultNode.execute(threadState, T___GETATTR__, toPythonNode.execute(result));
        }

        @Specialization(replaces = "callCachedBuiltin")
        @InliningCutoff
        static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, TpSlotSizeArgFunBuiltin<?> slot, Object self, int index,
                        @Cached(inline = false) CallContext callContext,
                        @Cached InlinedConditionProfile isNullFrameProfile,
                        @Cached(inline = false) IndirectCallNode indirectCallNode) {
            Object[] arguments = PArguments.create(2);
            PArguments.setArgument(arguments, 0, self);
            PArguments.setArgument(arguments, 1, index);
            return BuiltinDispatchers.callGenericBuiltin(frame, inliningTarget, slot.callTargetIndex, arguments, callContext, isNullFrameProfile, indirectCallNode);
        }
    }
}
