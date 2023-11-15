/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.locale.PythonLocale.LC_MONETARY;
import static com.oracle.graal.python.runtime.locale.PythonLocale.LC_NUMERIC;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.locale.LocaleUtils;
import com.oracle.graal.python.runtime.locale.PythonLocale;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;
import com.oracle.truffle.api.strings.TruffleString.ToJavaStringNode;

// See PythonLocale JavaDoc for an overview
@CoreFunctions(defineModule = "_locale")
public final class LocaleModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return LocaleModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("LC_ALL", 6);
        addBuiltinConstant("LC_COLLATE", 3);
        addBuiltinConstant("LC_CTYPE", 0);
        addBuiltinConstant("LC_MESSAGES", 5);
        addBuiltinConstant("LC_MONETARY", 4);
        addBuiltinConstant("LC_NUMERIC", 1);
        addBuiltinConstant("LC_TIME", 2);
        addBuiltinConstant("CHAR_MAX", 127);

        addBuiltinConstant("Error", PythonBuiltinClassType.ValueError);

        super.initialize(core);
    }

    // _locale.localeconv()
    @Builtin(name = "localeconv")
    @GenerateNodeFactory
    public abstract static class LocaleConvNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PDict localeconv() {
            PythonContext ctx = PythonContext.get(null);
            PythonObjectFactory factory = ctx.factory();
            LinkedHashMap<String, Object> dict = new LinkedHashMap<>(20);
            final PythonLocale currentPythonLocale = ctx.getCurrentLocale();

            // LC_NUMERIC
            Locale numericLocale = currentPythonLocale.category(LC_NUMERIC);
            NumberFormat numericLocaleNumFormat = NumberFormat.getInstance(numericLocale);
            DecimalFormatSymbols decimalFormatSymbols = getDecimalFormatSymbols(numericLocale, numericLocaleNumFormat);

            dict.put("decimal_point", TruffleString.fromCodePointUncached(decimalFormatSymbols.getDecimalSeparator(), TS_ENCODING));
            dict.put("thousands_sep", TruffleString.fromCodePointUncached(decimalFormatSymbols.getGroupingSeparator(), TS_ENCODING));
            dict.put("grouping", getDecimalFormatGrouping(factory, numericLocaleNumFormat));

            // LC_MONETARY
            Locale monetaryLocale = currentPythonLocale.category(LC_MONETARY);
            NumberFormat monetaryNumFormat = NumberFormat.getInstance(monetaryLocale);
            Currency currency = monetaryNumFormat.getCurrency();
            decimalFormatSymbols = getDecimalFormatSymbols(monetaryLocale, monetaryNumFormat);

            dict.put("int_curr_symbol", toTruffleStringUncached(decimalFormatSymbols.getInternationalCurrencySymbol()));
            dict.put("currency_symbol", toTruffleStringUncached(decimalFormatSymbols.getCurrencySymbol()));
            dict.put("mon_decimal_point", TruffleString.fromCodePointUncached(decimalFormatSymbols.getMonetaryDecimalSeparator(), TS_ENCODING));
            dict.put("mon_thousands_sep", TruffleString.fromCodePointUncached(decimalFormatSymbols.getGroupingSeparator(), TS_ENCODING));
            dict.put("mon_grouping", getDecimalFormatGrouping(factory, monetaryNumFormat));
            // TODO: reasonable default, but not the current locale setting
            dict.put("positive_sign", "");
            dict.put("negative_sign", TruffleString.fromCodePointUncached(decimalFormatSymbols.getMinusSign(), TS_ENCODING));
            dict.put("int_frac_digits", currency.getDefaultFractionDigits());
            dict.put("frac_digits", currency.getDefaultFractionDigits());
            dict.put("p_cs_precedes", PNone.NONE);
            dict.put("p_sep_by_space", PNone.NONE);
            dict.put("n_cs_precedes", PNone.NONE);
            dict.put("n_sep_by_space", PNone.NONE);
            dict.put("p_sign_posn", PNone.NONE);
            dict.put("n_sign_posn", PNone.NONE);

            return factory.createDictFromMap(dict);
        }

        private static DecimalFormatSymbols getDecimalFormatSymbols(Locale locale, NumberFormat numberFormat) {
            return numberFormat instanceof DecimalFormat decimalFormat ? decimalFormat.getDecimalFormatSymbols() : new DecimalFormatSymbols(locale);
        }

        private static PList getDecimalFormatGrouping(PythonObjectFactory factory, NumberFormat numberFormat) {
            if (numberFormat instanceof DecimalFormat decimalFormat) {
                // TODO: this does not support groupings with variable size groups like in India
                // Possible approach: decimalFormat.toPattern() gives a generic pattern (e.g.,
                // #,#00.0#) that would have to be parsed to extract the group sizes from it
                return factory.createList(new Object[]{decimalFormat.getGroupingSize(), 0});
            } else {
                return factory.createList();
            }
        }
    }

    // _locale.setlocale(category, locale=None)
    @Builtin(name = "setlocale", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetLocaleNode extends PythonBuiltinNode {

        @SuppressWarnings("fallthrough")
        @TruffleBoundary
        private static String getCurrent(PythonContext ctx, int category) {
            return LocaleUtils.toPosix(ctx.getCurrentLocale().category(category));
        }

        @TruffleBoundary
        @SuppressWarnings("fallthrough")
        private static String setCurrent(PythonContext ctx, int category, String posixLocaleID, Node nodeForRaise) {
            PythonLocale current = ctx.getCurrentLocale();
            Locale newLocale = LocaleUtils.fromPosix(posixLocaleID, current.category(category));
            if (newLocale != null) {
                ctx.setCurrentLocale(current.withCategory(category, newLocale));
            } else {
                throw PRaiseNode.raiseUncached(nodeForRaise, PythonErrorType.ValueError, ErrorMessages.UNSUPPORTED_LOCALE_SETTING);
            }
            return LocaleUtils.toPosix(newLocale);
        }

        @Specialization
        static TruffleString doGeneric(VirtualFrame frame, Object category, Object posixLocaleID,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached FromJavaStringNode resultAsTruffleStringNode,
                        @Cached ToJavaStringNode toJavaStringNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            long l = asLongNode.execute(frame, inliningTarget, category);
            if (!isValidCategory(l)) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.INVALID_LOCALE_CATEGORY);
            }

            TruffleString posixLocaleIDStr = null;
            // may be NONE or NO_VALUE
            if (!PGuards.isPNone(posixLocaleID)) {
                try {
                    posixLocaleIDStr = castToStringNode.execute(inliningTarget, posixLocaleID);
                } catch (CannotCastException e) {
                    // fall through
                }
            }

            PythonContext ctx = PythonContext.get(inliningTarget);
            String result;
            if (posixLocaleIDStr != null) {
                result = setCurrent(ctx, (int) l, toJavaStringNode.execute(posixLocaleIDStr), inliningTarget);
            } else {
                result = getCurrent(ctx, (int) l);
            }
            return resultAsTruffleStringNode.execute(result, TS_ENCODING);
        }

        static boolean isValidCategory(long l) {
            return 0 <= l && l <= 6;
        }
    }
}
