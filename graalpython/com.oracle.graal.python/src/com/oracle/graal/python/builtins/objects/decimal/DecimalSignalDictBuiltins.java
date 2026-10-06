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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDecimalSignalDictMixin)
@HashNotImplemented
public final class DecimalSignalDictBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DecimalSignalDictBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DecimalSignalDictBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "SignalDictMixin", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class TpNewNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PDecimalSignalDict construct(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached TypeNodes.GetInstanceShape getShape) {
            return PFactory.createDecimalSignalDict(cls, getShape.execute(cls), null, false);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "SignalDictMixin", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class TpInitNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object initialize(PDecimalSignalDict self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            self.detach();
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PDecimalSignalDict self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaString) {
            self.checkValid(raiseNode, inliningTarget);
            return fromJavaString.execute(DecimalContextUtils.signalDictRepr(self.getFlags()), TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpRichcompareNode extends RichCmpBuiltinNode {
        @Specialization
        static Object compare(VirtualFrame frame, PDecimalSignalDict self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached HashingStorageLen lenNode,
                        @Cached PyDictGetItem getItem,
                        @Cached PyObjectIsTrueNode isTrue) {
            self.checkValid(raiseNode, inliningTarget);
            if (other instanceof PDecimalSignalDict otherSignals) {
                otherSignals.checkValid(raiseNode, inliningTarget);
                if (op == RichCmpOp.Py_EQ || op == RichCmpOp.Py_NE) {
                    return (self.getFlags() == otherSignals.getFlags()) != (op == RichCmpOp.Py_NE);
                }
            } else if (other instanceof PDict otherDict && (op == RichCmpOp.Py_EQ || op == RichCmpOp.Py_NE)) {
                if (lenNode.execute(inliningTarget, otherDict.getDictStorage()) != DecimalContextUtils.SIGNALS.length) {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
                DecimalModuleState state = DecimalContextUtils.getState(inliningTarget);
                int flags = 0;
                for (DecimalExceptionTypes signal : DecimalContextUtils.SIGNALS) {
                    Object value = getItem.execute(frame, inliningTarget, otherDict, state.getExceptionType(signal));
                    if (value == null) {
                        return PNotImplemented.NOT_IMPLEMENTED;
                    }
                    if (isTrue.execute(frame, value)) {
                        flags |= DecimalContextUtils.mask(signal);
                    }
                }
                return (self.getFlags() == flags) ^ (op == RichCmpOp.Py_NE);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class TpIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iterator(VirtualFrame frame, PDecimalSignalDict self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectGetIter getIter) {
            self.checkValid(raiseNode, inliningTarget);
            DecimalModuleState state = DecimalContextUtils.getState(inliningTarget);
            Object[] keys = new Object[DecimalContextUtils.SIGNALS.length];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = state.getExceptionType(DecimalContextUtils.SIGNALS[i]);
            }
            return getIter.execute(frame, inliningTarget, PFactory.createTuple(language, keys));
        }
    }

    @Slot(value = SlotKind.mp_length, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MpLengthNode extends LenBuiltinNode {
        @Specialization
        static int length(PDecimalSignalDict self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkValid(raiseNode, inliningTarget);
            return DecimalContextUtils.SIGNALS.length;
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MpSubscriptNode extends MpSubscriptBuiltinNode {
        @Specialization
        static boolean get(PDecimalSignalDict self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            self.checkValid(raiseNode, inliningTarget);
            return (self.getFlags() & DecimalContextUtils.signalMask(inliningTarget, raiseNode, key)) != 0;
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MpAssSubscriptNode extends MpAssSubscriptBuiltinNode {
        @Specialization
        static void set(VirtualFrame frame, PDecimalSignalDict self, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached PRaiseNode raiseNode) {
            self.checkValid(raiseNode, inliningTarget);
            if (value == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DECIMAL_SIGNAL_DELETE);
            }
            int mask = DecimalContextUtils.signalMask(inliningTarget, raiseNode, key);
            boolean enabled = isTrue.execute(frame, value);
            self.setFlags(enabled ? self.getFlags() | mask : self.getFlags() & ~mask);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PDict copy(PDecimalSignalDict self,
                        @Bind Node inliningTarget) {
            self.checkValid(inliningTarget);
            return DecimalContextUtils.signalsAsDict(inliningTarget, self.getFlags());
        }
    }
}
