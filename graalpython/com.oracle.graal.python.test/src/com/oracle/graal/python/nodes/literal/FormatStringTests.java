/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.nodes.literal;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.parser.sst.FormatStringParser;
import com.oracle.graal.python.parser.sst.FormatStringParser.Token;
import com.oracle.graal.python.runtime.PythonParser.ErrorType;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.test.parser.ParserTestBase;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class FormatStringTests extends ParserTestBase {

    @Test
    public void empty() throws Exception {
        testFormatString("f''", "");
    }

    @Test
    public void justBraces() throws Exception {
        testFormatString("f'{{}}'", "{}");
    }

    @Test
    public void doubleBrace01() throws Exception {
        testFormatString("f'{{name}}'", "{name}");
    }

    @Test
    public void doubleBrace02() throws Exception {
        testFormatString("f'Hi {{name}}'", "Hi {name}");
    }

    @Test
    public void doubleBrace03() throws Exception {
        testFormatString("f'{{name}} first'", "{name} first");
    }

    @Test
    public void doubleBrace04() throws Exception {
        testFormatString("f'Hi {{name}} first'", "Hi {name} first");
    }

    @Test
    public void doubleBrace05() throws Exception {
        testFormatString("f'{{'", "{");
    }

    @Test
    public void doubleBrace06() throws Exception {
        testFormatString("f'a{{'", "a{");
    }

    @Test
    public void doubleBrace07() throws Exception {
        testFormatString("f'{{b'", "{b");
    }

    @Test
    public void doubleBrace08() throws Exception {
        testFormatString("f'a{{b'", "a{b");
    }

    @Test
    public void doubleBrace09() throws Exception {
        testFormatString("f'}}'", "}");
    }

    @Test
    public void doubleBrace10() throws Exception {
        testFormatString("f'a}}'", "a}");
    }

    @Test
    public void doubleBrace11() throws Exception {
        testFormatString("f'}}b'", "}b");
    }

    @Test
    public void doubleBrace12() throws Exception {
        testFormatString("f'a}}b'", "a}b");
    }

    @Test
    public void doubleBrace13() throws Exception {
        testFormatString("f'{{}}'", "{}");
    }

    @Test
    public void doubleBrace14() throws Exception {
        testFormatString("f'a{{}}'", "a{}");
    }

    @Test
    public void doubleBrace15() throws Exception {
        testFormatString("f'{{b}}'", "{b}");
    }

    @Test
    public void doubleBrace16() throws Exception {
        testFormatString("f'{{}}c'", "{}c");
    }

    @Test
    public void doubleBrace17() throws Exception {
        testFormatString("f'a{{b}}'", "a{b}");
    }

    @Test
    public void doubleBrace18() throws Exception {
        testFormatString("f'a{{}}c'", "a{}c");
    }

    @Test
    public void doubleBrace19() throws Exception {
        testFormatString("f'{{b}}'", "{b}");
    }

    @Test
    public void doubleBrace20() throws Exception {
        testFormatString("f'a{{b}}c'", "a{b}c");
    }

    @Test
    public void doubleBrace21() throws Exception {
        testFormatString("f'{{{10}'", "{+format((10))");
    }

    @Test
    public void doubleBrace22() throws Exception {
        testFormatString("f'}}{10}'", "}+format((10))");
    }

    @Test
    public void doubleBrace23() throws Exception {
        testFormatString("f'}}{{{10}'", "}{+format((10))");
    }

    @Test
    public void doubleBrace24() throws Exception {
        testFormatString("f'}}a{{{10}'", "}a{+format((10))");
    }

    @Test
    public void doubleBrace25() throws Exception {
        testFormatString("f'{10}{{'", "format((10))+{");
    }

    @Test
    public void doubleBrace26() throws Exception {
        testFormatString("f'{10}}}'", "format((10))+}");
    }

    @Test
    public void doubleBrace27() throws Exception {
        testFormatString("f'{10}}}{{'", "format((10))+}{");
    }

    @Test
    public void doubleBrace28() throws Exception {
        testFormatString("f'{10}}}a{{'", "format((10))+}a{");
    }

    @Test
    public void quotes01() throws Exception {
        testFormatString("f'{\"{{}}\"}'", "format((\"{{}}\"))");
    }

    @Test
    public void embeddedColon() throws Exception {
        testFormatString("f'{var[:1]}'", "format((var[:1]))");
    }

    @Test
    public void parser01() throws Exception {
        testFormatString("f'{name}'", "format((name))");
    }

    @Test
    public void parser02() throws Exception {
        testFormatString("f'name'", "name");
    }

    @Test
    public void parser03() throws Exception {
        testFormatString("f'First: {name}'", "First: +format((name))");
    }

    @Test
    public void parser04() throws Exception {
        testFormatString("f'{name} was here'", "format((name))+ was here");
    }

    @Test
    public void parser05() throws Exception {
        testFormatString("f'It {name} was'", "It +format((name))+ was");
    }

    @Test
    public void strWithColon() throws Exception {
        testFormatString("f'{myarray[:1]}'", "format((myarray[:1]))");
    }

    @Test
    public void str01() throws Exception {
        testFormatString("f'{name!s}'", "format(str((name)))");
    }

    @Test
    public void repr01() throws Exception {
        testFormatString("f'{name!r}'", "format(repr((name)))");
    }

    @Test
    public void ascii01() throws Exception {
        testFormatString("f'{name!a}'", "format(ascii((name)))");
    }

    @Test
    public void emptyExpression01() throws Exception {
        checkSyntaxError("f'{}'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void emptyExpression02() throws Exception {
        checkSyntaxError("f'start{}end'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void emptyExpression03() throws Exception {
        checkSyntaxError("f'start{}}end'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void emptyExpression04() throws Exception {
        checkSyntaxError("f'start{{{}}}end'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void singleBracket01() throws Exception {
        checkSyntaxError("f'}'", FormatStringParser.ERROR_MESSAGE_SINGLE_BRACE);
    }

    @Test
    public void singleBracket02() throws Exception {
        checkSyntaxError("f'start}end'", FormatStringParser.ERROR_MESSAGE_SINGLE_BRACE);
    }

    @Test
    public void singleBracket03() throws Exception {
        checkSyntaxError("f'start{{}end'", FormatStringParser.ERROR_MESSAGE_SINGLE_BRACE);
    }

    @Test
    public void spaces01() throws Exception {
        testFormatString("f'{     {}}'", "format(({}))");
    }

    @Test
    public void spaces02() throws Exception {
        testFormatString("f'{     {}                 }'", "format(({}))");
    }

    @Test
    public void innerExp01() throws Exception {
        testFormatString("f'result: {value:{width}.{precision}}'", "result: +format((value),(format((width))+\".\"+format((precision))))");
    }

    @Test
    public void missingSpecifier01() throws Exception {
        testFormatString("f'{x:}'", "format((x))");
    }

    @Test
    public void missingSpecifier02() throws Exception {
        testFormatString("f'{x!s:}'", "format(str((x)))");
    }

    @Test
    public void missingExpression01() throws Exception {
        checkSyntaxError("f'{!x}'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression02() throws Exception {
        checkSyntaxError("f'{     !x}'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression03() throws Exception {
        checkSyntaxError("f'{ !xr:a}'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression04() throws Exception {
        checkSyntaxError("f'{:x'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression05() throws Exception {
        checkSyntaxError("f'{!'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression06() throws Exception {
        checkSyntaxError("f'{10:{ }}'", FormatStringParser.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    private static void checkSyntaxError(String text, String expectedMessage) throws Exception {
        try {
            testFormatString(text, "Expected Error: " + expectedMessage);
        } catch (RuntimeException e) {
            Assert.assertEquals("SyntaxError: " + expectedMessage, e.getMessage());
        }
    }

    private static void testFormatString(String fstring, String expected) {
        assert fstring.startsWith("f'") && fstring.endsWith("'");
        // remove the f'...', to extract the text of the f-string
        String text = fstring.substring(2).substring(0, fstring.length() - 3);
        ArrayList<Token> tokens = new ArrayList<>();
        FormatStringParser.createTokens(tokens, new MockErrorCallback(), 0, text, false, 0);
        ArrayList<String> expressions = FormatStringParser.createExpressionSources(text, tokens, 0, tokens.size(), tokens.size());
        int expressionsIndex = 0;
        StringBuilder actual = new StringBuilder();
        boolean first = true;
        boolean wasLastString = true;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (first) {
                first = false;
            } else if (!(wasLastString && token.type == FormatStringParser.TOKEN_TYPE_STRING)) {
                actual.append("+");
            }
            if (token.type == FormatStringParser.TOKEN_TYPE_STRING) {
                actual.append(text, token.startIndex, token.endIndex);
                wasLastString = true;
            } else {
                actual.append(expressions.get(expressionsIndex));
                index += token.formatTokensCount;
                wasLastString = false;
            }
        }
        Assert.assertEquals(expected, actual.toString());
    }

    private static final class MockErrorCallback implements ParserErrorCallback {
        @Override
        public RuntimeException raise(PythonBuiltinClassType type, String message, Object... args) {
            throw new RuntimeException("SyntaxError: " + String.format(message, args));
        }

        @Override
        public RuntimeException raiseInvalidSyntax(ErrorType type, Source source, SourceSection section, String message, Object... arguments) {
            throw new RuntimeException("SyntaxError: " + String.format(message, arguments));
        }

        @Override
        public RuntimeException raiseInvalidSyntax(ErrorType type, Node location, String message, Object... arguments) {
            throw new RuntimeException("SyntaxError: " + String.format(message, arguments));
        }

        @Override
        public void warn(Object type, String format, Object... args) {
            throw new RuntimeException("Warning: " + String.format(format, args));
        }

        @Override
        public PythonLanguage getLanguage() {
            return null;
        }
    }
}
