/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.parser;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.parser.sst.StringUtils;
import com.oracle.graal.python.runtime.PythonParser.ErrorType;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class StringUtilsTests extends ParserTestBase {
    private static final ParserErrorCallback errorCallback = new ParserErrorCallback() {
        @Override
        public RuntimeException raise(PythonBuiltinClassType type, String message, Object... args) {
            Assert.fail("Unexpected error: " + String.format(message, args));
            return null;
        }

        @Override
        public RuntimeException raiseInvalidSyntax(ErrorType type, Source source, SourceSection section, String message, Object... arguments) {
            Assert.fail("Unexpected error: " + String.format(message, arguments));
            return null;
        }

        @Override
        public RuntimeException raiseInvalidSyntax(ErrorType type, Node location, String message, Object... arguments) {
            Assert.fail("Unexpected error: " + String.format(message, arguments));
            return null;
        }

        @Override
        public void warn(Object type, String format, Object... args) {
            Assert.fail("Unexpected warning: " + String.format(format, args));
        }

        @Override
        public PythonLanguage getLanguage() {
            return null;
        }
    };

    @Test
    public void unicodeCharNameBasic() throws Exception {
        Assert.assertEquals("Δ", StringUtils.unescapeJavaString(errorCallback, "\\N{GREEK CAPITAL LETTER DELTA}"));
        Assert.assertEquals("A", StringUtils.unescapeJavaString(errorCallback, "\\N{LATIN CAPITAL LETTER A}"));
        Assert.assertEquals("A", StringUtils.unescapeJavaString(errorCallback, "\\N{LATIN CAPITAL LETTER a}"));
        Assert.assertEquals("A", StringUtils.unescapeJavaString(errorCallback, "\\N{LATIN CAPITAL LETTEr a}"));
        Assert.assertEquals("A", StringUtils.unescapeJavaString(errorCallback, "\\N{latin capital letter a}"));
        Assert.assertEquals("AHOJ", StringUtils.unescapeJavaString(errorCallback, "A\\N{LATIN CAPITAL LETTER H}OJ"));
        Assert.assertEquals("AHOJ", StringUtils.unescapeJavaString(errorCallback, "\\N{LATIN CAPITAL LETTER A}\\N{LATIN CAPITAL LETTER H}\\N{LATIN CAPITAL LETTER O}\\N{LATIN CAPITAL LETTER J}"));
        checkUnknownChar("ahoj");
    }

    @Test
    public void blockHangulSyllables() throws Exception {
        Assert.assertEquals("가", StringUtils.unescapeJavaString(errorCallback, "\\N{HANGUL SYLLABLE GA}"));
        Assert.assertEquals("돐", StringUtils.unescapeJavaString(errorCallback, "\\N{HANGUL SYLLABLE DOLS}"));
        Assert.assertEquals("똜", StringUtils.unescapeJavaString(errorCallback, "\\N{HANGUL SYLLABLE DDOLS}"));
    }

    @Test
    public void blockCjkUnifiedIdeograph() throws Exception {
        Assert.assertEquals("㐀", StringUtils.unescapeJavaString(errorCallback, "\\N{CJK Unified Ideograph-3400}"));
        Assert.assertEquals("𫝜", StringUtils.unescapeJavaString(errorCallback, "\\N{CJK Unified Ideograph-2B75C}"));
        Assert.assertEquals("丳", StringUtils.unescapeJavaString(errorCallback, "\\N{CJK Unified Ideograph-4E33}"));
    }

    @Test
    public void blockCjkUnifiedIdeographUnknownCharacters() throws Exception {
        checkUnknownChar("CJK Unified Ideograph-33FF");
        checkUnknownChar("CJK Unified Ideograph-4DC0");
        checkUnknownChar("CJK Unified Ideograph-4DFF");
        checkUnknownChar("CJK Unified Ideograph-33FF");
        checkUnknownChar("CJK Unified Ideograph-2A6E0");
    }

    @Test
    public void malformedError() throws Exception {
        checkSyntaxErrorMessage("'\\N'", "SyntaxError: (unicode error) 'unicodeescape' codec can't decode bytes in position 0-1: malformed \\N character escape");
        checkSyntaxErrorMessage("'\\N {LATIN CAPITAL LETTER A}'", "SyntaxError: (unicode error) 'unicodeescape' codec can't decode bytes in position 0-1: malformed \\N character escape");
        checkSyntaxErrorMessage("'\\N LATIN CAPITAL LETTER A}'", "SyntaxError: (unicode error) 'unicodeescape' codec can't decode bytes in position 0-1: malformed \\N character escape");
        checkSyntaxErrorMessage("'\\N{LATIN CAPITAL LETTER A'", "SyntaxError: (unicode error) 'unicodeescape' codec can't decode bytes in position 0-24: malformed \\N character escape");
        checkSyntaxErrorMessage("'\\N{LATIN CAPITAL LETTER A \\N{LATIN CAPITAL LETTER B}'",
                        "SyntaxError: (unicode error) 'unicodeescape' codec can't decode bytes in position 0-51: unknown Unicode character name");
    }

    @Test
    public void doNotTrimNames() throws Exception {
        checkUnknownChar("LATIN CAPITAL LETTER A ");
        checkUnknownChar(" LATIN CAPITAL LETTER A");
        checkUnknownChar(" LATIN CAPITAL LETTER A ");
    }

    private void checkUnknownChar(String charName) throws Exception {
        String code = "'\\N{" + charName + "}'";
        checkSyntaxErrorMessage(code, "SyntaxError: (unicode error) 'unicodeescape' codec can't decode bytes in position 0-" + (charName.length() + 3) + ": unknown Unicode character name");
    }
}
