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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.PDate;
import com.oracle.graal.python.builtins.modules.datetime.PTimeDelta;
import com.oracle.graal.python.builtins.modules.datetime.TemporalValueNodes;
import com.oracle.graal.python.builtins.modules.datetime.TemporalValueNodes.DateValue;
import com.oracle.graal.python.builtins.modules.datetime.TemporalValueNodes.TimeDeltaValue;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.lib.PyDateCheckNode;
import com.oracle.graal.python.lib.PyDeltaCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignDate)
public final class ForeignDateBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ForeignDateBuiltinsSlotsGen.SLOTS;

    private static final int MAX_ORDINAL = 3_652_059;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignDateBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object add(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage lang,
                        @Cached PyDateCheckNode dateLikeCheck,
                        @Cached PyDeltaCheckNode deltaCheck,
                        @Cached TemporalValueNodes.GetDateValue readDateValue,
                        @Cached TemporalValueNodes.GetTimeDeltaValue readTimeDeltaValue) {
            Object dateObj;
            Object deltaObj;
            if (dateLikeCheck.execute(inliningTarget, left) && deltaCheck.execute(inliningTarget, right)) {
                dateObj = left;
                deltaObj = right;
            } else if (deltaCheck.execute(inliningTarget, left) && dateLikeCheck.execute(inliningTarget, right)) {
                dateObj = right;
                deltaObj = left;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            DateValue date = readDateValue.execute(inliningTarget, dateObj);
            TimeDeltaValue delta = readTimeDeltaValue.execute(inliningTarget, deltaObj);
            return op(lang, inliningTarget, date, delta);
        }

        @TruffleBoundary
        private static PDate op(PythonLanguage lang, Node inliningTarget, DateValue date, TimeDeltaValue delta) {
            long days = ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), date.toLocalDate()) + 1 + delta.days;
            if (days <= 0 || days > MAX_ORDINAL) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }
            LocalDate ld = ChronoUnit.DAYS.addTo(LocalDate.of(1, 1, 1), days - 1);
            return toPythonDate(lang, ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth());
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object sub(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage lang,
                        @Cached PyDateCheckNode dateLikeCheckNode,
                        @Cached PyDeltaCheckNode deltaCheck,
                        @Cached TemporalValueNodes.GetDateValue readDateValueNode,
                        @Cached TemporalValueNodes.GetTimeDeltaValue getTimeDeltaValue) {
            if (!dateLikeCheckNode.execute(inliningTarget, left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            DateValue leftValue = readDateValueNode.execute(inliningTarget, left);

            if (dateLikeCheckNode.execute(inliningTarget, right)) {
                return op1(right, inliningTarget, lang, readDateValueNode, leftValue);
            }

            if (deltaCheck.execute(inliningTarget, right)) {
                TimeDeltaValue delta = getTimeDeltaValue.execute(inliningTarget, right);
                return op2(inliningTarget, lang, leftValue, delta);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        private static Object op1(Object right, Node inliningTarget, PythonLanguage lang, TemporalValueNodes.GetDateValue readDateValueNode, DateValue leftValue) {
            LocalDate leftDate = leftValue.toLocalDate();
            LocalDate from = LocalDate.of(1, 1, 1);
            long leftDays = ChronoUnit.DAYS.between(from, leftDate) + 1;
            LocalDate rightDate = readDateValueNode.execute(inliningTarget, right).toLocalDate();
            long rightDays = ChronoUnit.DAYS.between(from, rightDate) + 1;
            return new PTimeDelta(PythonBuiltinClassType.PTimeDelta, PythonBuiltinClassType.PTimeDelta.getInstanceShape(lang), (int) (leftDays - rightDays), 0, 0);
        }

        @TruffleBoundary
        private static Object op2(Node inliningTarget, PythonLanguage lang, DateValue leftValue, TimeDeltaValue delta) {
            LocalDate leftDate = leftValue.toLocalDate();
            LocalDate from = LocalDate.of(1, 1, 1);
            long leftDays = ChronoUnit.DAYS.between(from, leftDate) + 1;
            long days = leftDays - delta.days;
            if (days <= 0 || days >= MAX_ORDINAL) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }
            from = ChronoUnit.DAYS.addTo(from, days - 1);
            return toPythonDate(lang, from.getYear(), from.getMonthValue(), from.getDayOfMonth());
        }
    }

    private static PDate toPythonDate(PythonLanguage lang, DateValue date) {
        return toPythonDate(lang, date.year, date.month, date.day);
    }

    private static PDate toPythonDate(PythonLanguage lang, int year, int month, int day) {
        Shape shape = PythonBuiltinClassType.PDate.getInstanceShape(lang);
        return new PDate(PythonBuiltinClassType.PDate, shape, year, month, day);
    }
}
