/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.thread;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PThreadHandle)
public final class ThreadHandleBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ThreadHandleBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadHandleBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "_ThreadHandle", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ConstructThreadHandleNode extends PythonUnaryBuiltinNode {
        @Specialization
        PThreadHandle construct(Object cls,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createThreadHandle(cls, getInstanceShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PThreadHandle self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<_thread._ThreadHandle object: ident=%d>", self.getIdent());
        }
    }

    @Builtin(name = "ident", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class IdentNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long ident(PThreadHandle self) {
            return self.getIdent();
        }
    }

    @Builtin(name = "join", minNumOfPositionalArgs = 1, parameterNames = {"$self", "timeout"})
    @GenerateNodeFactory
    abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object join(VirtualFrame frame, PThreadHandle self, Object timeout,
                        @Bind Node inliningTarget,
                        @Cached PyTimeFromObjectNode pyTimeFromObjectNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode raiseNode) {
            long timeoutMillis = -1;
            if (timeout != PNone.NO_VALUE && timeout != PNone.NONE) {
                long timeoutNs = pyTimeFromObjectNode.fromSeconds(frame, inliningTarget, timeout, RoundType.TIMEOUT);
                timeoutMillis = timeoutNs <= 0 ? 0 : (timeoutNs + TimeUtils.MS_TO_NS - 1) / TimeUtils.MS_TO_NS;
            }
            gil.release(true);
            try {
                self.join(inliningTarget, timeoutMillis);
            } catch (IllegalStateException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.THREAD_NOT_STARTED);
            } catch (IllegalThreadStateException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_JOIN_CURRENT_THREAD);
            } finally {
                gil.acquire();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "is_done", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsDoneNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isDone(PThreadHandle self) {
            return self.isDone();
        }
    }

    @Builtin(name = "_set_done", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SetDoneNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object setDone(PThreadHandle self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            try {
                self.setDone();
            } catch (IllegalStateException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.THREAD_NOT_STARTED);
            }
            return PNone.NONE;
        }
    }
}
