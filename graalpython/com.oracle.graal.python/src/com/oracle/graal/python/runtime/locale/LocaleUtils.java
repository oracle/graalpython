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

import java.nio.charset.Charset;
import java.util.Locale;

import com.oracle.truffle.api.CompilerAsserts;

public final class LocaleUtils {
    private LocaleUtils() {
    }

    public static Locale fromPosix(String posixLocaleId, Locale defaultLocale) {
        CompilerAsserts.neverPartOfCompilation();
        // format: [language[_territory][.variant][@modifier]]
        // 2 lower _ 2 UPPER .
        if (posixLocaleId == null) {
            return null;
        }
        if (posixLocaleId.isEmpty()) {
            // per Python docs: empty string -> default locale
            return defaultLocale;
        }

        String language;
        String country = "";
        String variant = "";

        int len = posixLocaleId.length();

        // get the language
        int posCountrySep = posixLocaleId.indexOf('_');
        if (posCountrySep < 0) {
            language = posixLocaleId;
        } else {
            language = posixLocaleId.substring(0, posCountrySep);

            int posVariantSep = posixLocaleId.indexOf('.');
            if (posVariantSep < 0) {
                country = posixLocaleId.substring(posCountrySep + 1, len);
            } else {
                country = posixLocaleId.substring(posCountrySep + 1, posVariantSep);
                variant = posixLocaleId.substring(posVariantSep + 1, len);
            }
        }

        if (!language.isEmpty() && language.length() != 2) {
            return null;
        }

        if (!country.isEmpty() && country.length() != 2) {
            return null;
        }

        return Locale.of(language, country, variant);
    }

    public static String toPosix(Locale locale) {
        CompilerAsserts.neverPartOfCompilation();
        if (locale == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        String language = locale.getLanguage();
        if (language.isEmpty()) {
            language = locale.getISO3Language();
        }

        if (!language.isEmpty()) {
            builder.append(language);

            String country = locale.getCountry();
            if (country.isEmpty()) {
                country = locale.getISO3Country();
            }

            if (!country.isEmpty()) {
                builder.append('_');
                builder.append(country.toUpperCase());

                Charset charset = Charset.defaultCharset();
                builder.append('.');
                builder.append(charset.name());
            }
        } else {
            return null;
        }

        return builder.toString();
    }
}
