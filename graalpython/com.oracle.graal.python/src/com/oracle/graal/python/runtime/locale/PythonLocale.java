/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.locale;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Locale;
import java.util.Locale.Category;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Abstraction of the POSIX locale settings.
 * <p>
 * How is current/default locale handled in CPython/GraalPy:
 * <p>
 * Python stdlib defines pure Python function locale.getdefaultlocale, which tries to infer the
 * locale from environment variables. The idea is that this is system independent way of determining
 * the default locale. This is problematic, because for locale sensitive operations CPython and
 * Python stdlib use:
 *
 * <li>
 * <ul>
 * localeconv builtin, which is thin wrapped around libC's localeconv
 * </ul>
 * <ul>
 * from native code: directly locale sensitive libC functions
 * </ul>
 * <ul>
 * to get the current locale: _setlocale(category), which should return current locale and which
 * retrieves it from libC
 * </ul>
 * </li>
 *
 * How libC determines initial current/default locale does not have to align with
 * locale.getdefaultlocale. In fact, if getdefaultlocale is supposed to narrow differences between
 * systems, it shouldn't by design. For that reason locale.getdefaultlocale is
 * <a href="https://bugs.python.org/issue46659">deprecated in 11 marked for removal in 15</a>.
 * <p>
 * For GraalPy we do the same: ignore potential inconsistency locale.getdefaultlocale and use a
 * library (for us Java instead of lib C) to get the default locale and to do locale sensitive
 * operations. The current locale is maintained in Python context and not globally, but when run
 * from launcher we also set the current locale for whole JVM, and we may need to set the current
 * locale also in the native code if native access is allowed. In embedding mode we do not change
 * the global JVM's current locale. All GraalPy locale sensitive builtins should not rely on JVM's
 * current locale, but get the locale from Python context.
 */
public final class PythonLocale {
    public static final TruffleString T_GETDEFAULTLOCALE = tsLiteral("__getdefaultlocale");
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PythonLocale.class);

    public static final int LC_ALL = 6;
    public static final int LC_COLLATE = 3;
    public static final int LC_CTYPE = 0;
    public static final int LC_MESSAGES = 5;
    public static final int LC_MONETARY = 4;
    public static final int LC_NUMERIC = 1;
    public static final int LC_TIME = 2;

    // We should have separate fields for each POSIX category and use them in the right places in
    // GraalPython codebase. JVM does not recognize this many categories, but the interface of
    // PythonLocale pretends that we do.

    private final Locale displayLocale;
    private final Locale formatLocale;

    public PythonLocale(Locale displayLocale, Locale formatLocale) {
        assert displayLocale != null;
        assert formatLocale != null;
        this.displayLocale = displayLocale;
        this.formatLocale = formatLocale;
    }

    public Locale category(int category) {
        return switch (category) {
            case LC_COLLATE, LC_CTYPE, LC_MESSAGES -> formatLocale;
            default -> displayLocale;
        };
    }

    public PythonLocale withCategory(int category, Locale locale) {
        return switch (category) {
            case LC_COLLATE, LC_CTYPE, LC_MESSAGES -> new PythonLocale(displayLocale, locale);
            case LC_MONETARY, LC_NUMERIC, LC_TIME -> new PythonLocale(locale, formatLocale);
            default -> new PythonLocale(locale, locale);
        };
    }

    public static PythonLocale initializeFromTruffleEnv(Env env) {
        String defaultLangOption = env.getOptions().get(PythonOptions.InitialLocale);
        if (defaultLangOption == null || defaultLangOption.isEmpty()) {
            // per Python docs: empty string -> default locale
            return fromJavaDefault();
        }

        Locale displayLocale = LocaleUtils.fromPosix(defaultLangOption, null);
        if (displayLocale == null) {
            LOGGER.warning(() -> "Could not parse the value of --python.InitialLocale='" + defaultLangOption + '\'');
            return fromJavaDefault();
        } else {
            LOGGER.fine(() -> "Selected locale: " + displayLocale);
            return new PythonLocale(displayLocale, displayLocale);
        }
    }

    public static PythonLocale fromJavaDefault() {
        Locale display = Locale.getDefault(Category.DISPLAY);
        Locale format = Locale.getDefault(Category.FORMAT);
        PythonLocale result = new PythonLocale(display, format);
        LOGGER.fine(() -> "Selected locale from Java default: " + display + ", " + format);
        return result;
    }

    public void setAsJavaDefault() {
        CompilerAsserts.neverPartOfCompilation();
        Locale.setDefault(Locale.Category.DISPLAY, displayLocale);
        Locale.setDefault(Category.FORMAT, formatLocale);
    }
}
