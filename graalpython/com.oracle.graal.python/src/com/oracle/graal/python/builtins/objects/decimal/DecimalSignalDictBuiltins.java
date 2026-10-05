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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDecimalSignalDictMixin)
@HashNotImplemented
public final class DecimalSignalDictBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DecimalSignalDictBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecimalSignalDictBuiltinsFactory.getFactories();
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.copy.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "SignalDictMixin", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class TpInitNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.tp_init.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.tp_repr.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpGetattroNode extends GetAttrBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.tp_getattro.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpRichcompareNode extends RichCmpBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.tp_richcompare.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.tp_iter.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.mp_length, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MpLengthNode extends LenBuiltinNode {
        @Specialization
        static int doMock(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.mp_length.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MpSubscriptNode extends MpSubscriptBuiltinNode {
        @Specialization
        static Object doMock(VirtualFrame frame, Object self, Object arg1,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.mp_subscript.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MpAssSubscriptNode extends MpAssSubscriptBuiltinNode {
        @Specialization
        static void doMock(VirtualFrame frame, Object self, Object arg1, Object arg2,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            // TODO implement SignalDictMixin.mp_ass_subscript.
            throw raiseNode.raise(inliningTarget, NotImplementedError);
        }
    }
}
