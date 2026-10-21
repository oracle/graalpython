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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.decimal.DecimalNodes.RaiseSignalNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyListCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/** Context configuration and the status protocol shared by decimal operations. */
public final class DecimalContextUtils {
    public static final DecimalExceptionTypes[] SIGNALS = {
                    DecimalExceptionTypes.InvalidOperation, DecimalExceptionTypes.FloatOperation, DecimalExceptionTypes.DivisionByZero,
                    DecimalExceptionTypes.Overflow, DecimalExceptionTypes.Underflow, DecimalExceptionTypes.Subnormal,
                    DecimalExceptionTypes.Inexact, DecimalExceptionTypes.Rounded, DecimalExceptionTypes.Clamped
    };
    // Context repr follows libmpdec's status-bit ordering, rather than exception priority.
    private static final DecimalExceptionTypes[] REPR_SIGNALS = {
                    DecimalExceptionTypes.Clamped, DecimalExceptionTypes.InvalidOperation, DecimalExceptionTypes.DivisionByZero,
                    DecimalExceptionTypes.Inexact, DecimalExceptionTypes.FloatOperation, DecimalExceptionTypes.Overflow,
                    DecimalExceptionTypes.Rounded, DecimalExceptionTypes.Subnormal, DecimalExceptionTypes.Underflow
    };
    static final DecimalExceptionTypes[] INVALID_CONDITIONS = {
                    DecimalExceptionTypes.InvalidOperation, DecimalExceptionTypes.ConversionSyntax,
                    DecimalExceptionTypes.DivisionImpossible, DecimalExceptionTypes.DivisionUndefined, DecimalExceptionTypes.InvalidContext
    };
    private static final DecimalRounding[] ROUNDING_MODES = DecimalRounding.values();
    public static final int DEFAULT_TRAPS = mask(DecimalExceptionTypes.InvalidOperation) |
                    mask(DecimalExceptionTypes.DivisionByZero) | mask(DecimalExceptionTypes.Overflow);
    public static final TruffleString T_FLAGS = tsLiteral("flags");
    public static final TruffleString T_TRAPS = tsLiteral("traps");

    private DecimalContextUtils() {
    }

    public static int conditionMask(DecimalExceptionTypes condition) {
        return 1 << condition.ordinal();
    }

    public static int mask(DecimalExceptionTypes signal) {
        if (signal == DecimalExceptionTypes.InvalidOperation) {
            int result = 0;
            for (DecimalExceptionTypes condition : INVALID_CONDITIONS) {
                result |= conditionMask(condition);
            }
            return result;
        }
        return conditionMask(signal);
    }

    public static DecimalModuleState getState(Node node) {
        return PythonContext.get(node).getDecimalModuleState();
    }

    public static int signalMask(Node node, PRaiseNode raiseNode, Object key) {
        DecimalModuleState state = getState(node);
        for (DecimalExceptionTypes signal : SIGNALS) {
            if (key == state.getExceptionType(signal)) {
                return mask(signal);
            }
        }
        throw raiseNode.raise(node, KeyError, ErrorMessages.DECIMAL_INVALID_SIGNALS);
    }

    /** Set all sticky flags before selecting a trap; trap precedence is CPython's signal order. */
    public static void addStatus(VirtualFrame frame, PDecimalContext context, int status, RaiseSignalNode raiseSignal) {
        context.setFlags(context.getFlags() | status);
        int trapped = status & context.getTraps();
        if (trapped != 0) {
            raiseSignal.execute(frame, trapped);
        }
    }

    @TruffleBoundary
    public static int signalsFromObject(Node node, Object value, boolean allowList) {
        if (allowList && PyListCheckNode.executeUncached(value)) {
            int result = 0;
            SequenceStorage storage = ListNodes.GetListStorageNode.executeUncached(value);
            for (int i = 0; i < storage.length(); i++) {
                result |= signalMask(node, PRaiseNode.getUncached(), GetItemScalarNode.executeUncached(storage, i));
            }
            return result;
        }
        if (value instanceof PDecimalSignalDict valueDict) {
            valueDict.checkValid(node);
            return valueDict.getFlags();
        }
        if (!(value instanceof PDict dict)) {
            throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.DECIMAL_EXPECTED_SIGNAL_DICT);
        }
        if (HashingStorageLen.executeUncached(dict.getDictStorage()) != SIGNALS.length) {
            throw PRaiseNode.raiseStatic(node, KeyError, ErrorMessages.DECIMAL_INVALID_SIGNAL_DICT);
        }
        DecimalModuleState state = getState(node);
        int result = 0;
        for (DecimalExceptionTypes signal : SIGNALS) {
            Object item = dict.getItem(state.getExceptionType(signal));
            if (item == null) {
                throw PRaiseNode.raiseStatic(node, KeyError, ErrorMessages.DECIMAL_INVALID_SIGNAL_DICT);
            }
            if (PyObjectIsTrueNode.executeUncached(item)) {
                result |= mask(signal);
            }
        }
        return result;
    }

    @TruffleBoundary
    public static PDict signalsAsDict(Node node, int flags) {
        PDict result = PFactory.createDict(PythonLanguage.get(node));
        DecimalModuleState state = getState(node);
        for (DecimalExceptionTypes signal : SIGNALS) {
            result.setItem(state.getExceptionType(signal), (flags & mask(signal)) != 0);
        }
        return result;
    }

    @TruffleBoundary
    public static PList signalsAsList(Node node, int flags) {
        List<Object> result = new ArrayList<>();
        DecimalModuleState state = getState(node);
        for (DecimalExceptionTypes signal : SIGNALS) {
            if ((flags & mask(signal)) != 0) {
                result.add(state.getExceptionType(signal));
            }
        }
        return PFactory.createList(PythonLanguage.get(node), result.toArray());
    }

    public static long validateLong(Node node, PRaiseNode raiseNode, long value, long min, long max, TruffleString error) {
        if (value < min || value > max) {
            throw raiseNode.raise(node, ValueError, error);
        }
        return value;
    }

    public static int validateClamp(Node node, PRaiseNode raiseNode, long value) {
        return (int) validateLong(node, raiseNode, value, 0, 1, ErrorMessages.DECIMAL_CLAMP_RANGE);
    }

    @TruffleBoundary
    public static DecimalRounding validateRounding(Node node, Object value) {
        try {
            TruffleString string = CastToTruffleStringNode.executeUncached(value);
            for (DecimalRounding rounding : ROUNDING_MODES) {
                if (rounding.getPythonName().equalsUncached(string, TS_ENCODING)) {
                    return rounding;
                }
            }
        } catch (CannotCastException e) {
            // Report the supported rounding modes for both wrong types and unknown strings.
        }
        throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.DECIMAL_ROUNDING_VALUES);
    }

    /** None and omitted arguments leave existing attributes unchanged, including in __init__. */
    @TruffleBoundary
    public static void setAttributes(Node node, PDecimalContext self, Object prec, Object rounding,
                    Object emin, Object emax, Object capitals, Object clamp, Object flags, Object traps) {
        if (isProvided(prec)) {
            self.setPrecision(validateLong(node, PRaiseNode.getUncached(), asContextLong(node, prec), 1, DecimalUtils.MAX_PREC, ErrorMessages.DECIMAL_PREC_RANGE));
        }
        if (isProvided(rounding)) {
            self.setRounding(validateRounding(node, rounding));
        }
        if (isProvided(emin)) {
            self.setEmin(validateLong(node, PRaiseNode.getUncached(), asContextLong(node, emin), DecimalUtils.MIN_EMIN, 0, ErrorMessages.DECIMAL_EMIN_RANGE));
        }
        if (isProvided(emax)) {
            self.setEmax(validateLong(node, PRaiseNode.getUncached(), asContextLong(node, emax), 0, DecimalUtils.MAX_EMAX, ErrorMessages.DECIMAL_EMAX_RANGE));
        }
        if (isProvided(capitals)) {
            self.setCapitals(validateLong(node, PRaiseNode.getUncached(), asContextLong(node, capitals), 0, 1, ErrorMessages.DECIMAL_CAPITALS_RANGE) != 0);
        }
        if (isProvided(clamp)) {
            self.setClamp(validateClamp(node, PRaiseNode.getUncached(), asContextLong(node, clamp)));
        }
        // Match CPython's validation and mutation order.
        if (isProvided(traps)) {
            self.setTraps(signalsFromObject(node, traps, true));
        }
        if (isProvided(flags)) {
            self.setFlags(signalsFromObject(node, flags, true));
        }
    }

    private static long asContextLong(Node node, Object value) {
        if (!PyLongCheckNode.executeUncached(value)) {
            throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.INTEGER_REQUIRED);
        }
        return PyLongAsLongNode.executeUncached(value);
    }

    private static boolean isProvided(Object value) {
        return !(value instanceof PNone);
    }

    @TruffleBoundary
    public static String contextRepr(PDecimalContext self) {
        return "Context(prec=" + self.getPrecision() + ", rounding=" + self.getRounding().getPythonName().toJavaStringUncached() +
                        ", Emin=" + self.getEmin() + ", Emax=" + self.getEmax() + ", capitals=" + (self.getCapitals() ? 1 : 0) +
                        ", clamp=" + self.getClamp() + ", flags=" + signalNames(self.getFlags()) + ", traps=" + signalNames(self.getTraps()) + ")";
    }

    private static String signalNames(int flags) {
        List<String> names = new ArrayList<>();
        for (DecimalExceptionTypes signal : REPR_SIGNALS) {
            if ((flags & mask(signal)) != 0) {
                names.add(signal.name());
            }
        }
        return "[" + String.join(", ", names) + "]";
    }

    @TruffleBoundary
    public static String signalDictRepr(int flags) {
        List<String> items = new ArrayList<>();
        for (DecimalExceptionTypes signal : SIGNALS) {
            items.add("<class 'decimal." + signal.name() + "'>:" + ((flags & mask(signal)) != 0 ? "True" : "False"));
        }
        return "{" + String.join(", ", items) + "}";
    }
}
