/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_locale")
public class LocaleModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return LocaleModuleBuiltinsFactory.getFactories();
    }

    // locale.localeconv()
    @Builtin(name = "localeconv")
    @GenerateNodeFactory
    public abstract static class NormalizeNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public PDict localeconv() {
            Locale locale = Locale.getDefault();
            DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols(locale);
            NumberFormat numberFormat = NumberFormat.getCurrencyInstance(locale);
            Currency currency = numberFormat.getCurrency();

            Map<String, Object> dict = new HashMap<>();

            // LC_NUMERIC
            dict.put("decimal_point", String.valueOf(decimalFormatSymbols.getDecimalSeparator()));
            dict.put("thousands_sep", String.valueOf(decimalFormatSymbols.getGroupingSeparator()));
            dict.put("grouping", factory().createList()); // TODO: set the proper grouping

            // LC_MONETARY
            dict.put("int_curr_symbol", decimalFormatSymbols.getInternationalCurrencySymbol());
            dict.put("currency_symbol", decimalFormatSymbols.getCurrencySymbol());
            dict.put("mon_decimal_point", String.valueOf(decimalFormatSymbols.getMonetaryDecimalSeparator()));
            dict.put("mon_thousands_sep", "");
            dict.put("mon_grouping", factory().createList()); // TODO: set the proper grouping
            dict.put("positive_sign", "");
            dict.put("negative_sign", String.valueOf(decimalFormatSymbols.getMinusSign()));
            dict.put("int_frac_digits", "");
            dict.put("frac_digits", currency.getDefaultFractionDigits());
            dict.put("p_cs_precedes", "");
            dict.put("p_sep_by_space", "");
            dict.put("n_cs_precedes", "");
            dict.put("n_sep_by_space", "");
            dict.put("p_sign_posn", "");
            dict.put("n_sign_posn", "");

            return factory().createDict(dict);
        }
    }
}
