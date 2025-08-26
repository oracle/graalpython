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
package com.oracle.graal.python.builtins.objects.asyncio;

import static com.oracle.graal.python.nodes.BuiltinNames.T_CLOSE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SEND;
import static com.oracle.graal.python.nodes.BuiltinNames.T_THROW;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PAnextAwaitable)
public class ANextAwaitableBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ANextAwaitableBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ANextAwaitableBuiltinsFactory.getFactories();
    }

    // anextawaitable_getiter helper
    @GenerateInline
    @GenerateCached(false)
    abstract static class GetIterNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, PANextAwaitable self);

        @Specialization
        static Object getIter(VirtualFrame frame, Node inliningTarget, PANextAwaitable self,
                        @Cached GetAwaitableNode getAwaitableNode) {
            Object awaitable = getAwaitableNode.execute(frame, self.getWrapped());
            if (awaitable instanceof PGenerator coroutine && coroutine.getPythonClass() == PythonBuiltinClassType.PCoroutine) {
                return PFactory.createCoroutineWrapper(PythonLanguage.get(inliningTarget), coroutine);
            }
            return awaitable;
        }
    }

    // anextawaitable_proxy helper
    @GenerateInline
    @GenerateCached(false)
    abstract static class ProxyNode extends Node {
        abstract Object execute(VirtualFrame frame, Node inliningTarget, PANextAwaitable self, TruffleString method);

        @Specialization
        static Object getIter(VirtualFrame frame, Node inliningTarget, PANextAwaitable self, TruffleString method,
                        @Cached GetIterNode getIterNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached PRaiseNode raiseNode) {
            Object awaitable = getIterNode.execute(frame, inliningTarget, self);
            try {
                return callMethod.execute(frame, inliningTarget, awaitable, method);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.StopAsyncIteration, stopIterationProfile);
                throw raiseNode.raiseStopAsyncIteration(inliningTarget, self.getDefaultValue());
            }
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @Slot(value = SlotKind.am_aiter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PANextAwaitable self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PANextAwaitable self,
                        @Bind Node inliningTarget,
                        @Cached GetIterNode getIterNode,
                        @Cached TpSlots.GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIternext,
                        @Cached IsBuiltinObjectProfile stopIterationProfile,
                        @Cached PRaiseNode raiseNode) {
            Object awaitable = getIterNode.execute(frame, inliningTarget, self);
            TpSlots slots = getSlots.execute(inliningTarget, awaitable);
            try {
                return callIternext.execute(frame, inliningTarget, slots.tp_iternext(), awaitable);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.StopAsyncIteration, stopIterationProfile);
                throw raiseNode.raiseStopAsyncIteration(inliningTarget, self.getDefaultValue());
            }
        }
    }

    @Builtin(name = "send", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SendNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doSend(VirtualFrame frame, PANextAwaitable self,
                        @Bind Node inliningTarget,
                        @Cached ProxyNode proxyNode) {
            return proxyNode.execute(frame, inliningTarget, self, T_SEND);
        }
    }

    @Builtin(name = "throw", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ThrowNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doThrow(VirtualFrame frame, PANextAwaitable self,
                        @Bind Node inliningTarget,
                        @Cached ProxyNode proxyNode) {
            return proxyNode.execute(frame, inliningTarget, self, T_THROW);
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doClose(VirtualFrame frame, PANextAwaitable self,
                        @Bind Node inliningTarget,
                        @Cached ProxyNode proxyNode) {
            return proxyNode.execute(frame, inliningTarget, self, T_CLOSE);
        }
    }
}
