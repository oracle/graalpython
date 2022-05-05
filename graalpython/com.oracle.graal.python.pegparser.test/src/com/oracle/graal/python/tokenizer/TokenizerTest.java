/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.tokenizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.graal.python.pegparser.ParserTokenizer;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;

public class TokenizerTest {

    @Rule public TestName name = new TestName();

    private static HashSet<Integer> opTypes = new HashSet<>();

    static {
        opTypes.add(Token.Kind.PERCENT);
        opTypes.add(Token.Kind.AMPER);
        opTypes.add(Token.Kind.LPAR);
        opTypes.add(Token.Kind.RPAR);
        opTypes.add(Token.Kind.STAR);
        opTypes.add(Token.Kind.PLUS);
        opTypes.add(Token.Kind.COMMA);
        opTypes.add(Token.Kind.MINUS);
        opTypes.add(Token.Kind.DOT);
        opTypes.add(Token.Kind.SLASH);
        opTypes.add(Token.Kind.COLON);
        opTypes.add(Token.Kind.SEMI);
        opTypes.add(Token.Kind.LESS);
        opTypes.add(Token.Kind.EQUAL);
        opTypes.add(Token.Kind.GREATER);
        opTypes.add(Token.Kind.AT);
        opTypes.add(Token.Kind.LSQB);
        opTypes.add(Token.Kind.RSQB);
        opTypes.add(Token.Kind.CIRCUMFLEX);
        opTypes.add(Token.Kind.LBRACE);
        opTypes.add(Token.Kind.VBAR);
        opTypes.add(Token.Kind.RBRACE);
        opTypes.add(Token.Kind.TILDE);
        opTypes.add(Token.Kind.NOTEQUAL);
        opTypes.add(Token.Kind.PERCENTEQUAL);
        opTypes.add(Token.Kind.AMPEREQUAL);
        opTypes.add(Token.Kind.DOUBLESTAR);
        opTypes.add(Token.Kind.STAREQUAL);
        opTypes.add(Token.Kind.PLUSEQUAL);
        opTypes.add(Token.Kind.MINEQUAL);
        opTypes.add(Token.Kind.RARROW);
        opTypes.add(Token.Kind.DOUBLESLASH);
        opTypes.add(Token.Kind.SLASHEQUAL);
        opTypes.add(Token.Kind.COLONEQUAL);
        opTypes.add(Token.Kind.LEFTSHIFT);
        opTypes.add(Token.Kind.LESSEQUAL);
        opTypes.add(Token.Kind.EQEQUAL);
        opTypes.add(Token.Kind.GREATEREQUAL);
        opTypes.add(Token.Kind.RIGHTSHIFT);
        opTypes.add(Token.Kind.ATEQUAL);
        opTypes.add(Token.Kind.CIRCUMFLEXEQUAL);
        opTypes.add(Token.Kind.VBAREQUAL);
        opTypes.add(Token.Kind.DOUBLESTAREQUAL);
        opTypes.add(Token.Kind.ELLIPSIS);
        opTypes.add(Token.Kind.DOUBLESLASHEQUAL);
        opTypes.add(Token.Kind.LEFTSHIFTEQUAL);
        opTypes.add(Token.Kind.RIGHTSHIFTEQUAL);
    }

    public TokenizerTest() {
    }

    private static void assertToken(String code, int kind) {
        assertEquals(kind, new ParserTokenizer(code).getToken().type);
    }

    @Test
    public void testAsync() {
        assertToken("async", Token.Kind.ASYNC);
    }

    @Test
    public void testAwait() {
        assertToken("await", Token.Kind.AWAIT);
    }

    @Test
    public void testIdentifier() {
        assertToken("hello", Token.Kind.NAME);
    }

    @Test
    public void testUnicodeIdentifier() {
        assertToken("Öllo", Token.Kind.NAME);
    }

    // TODO: fix this test, this identifier should not be accepted
    @Test(expected = AssertionError.class)
    public void testIllegalUnicodeIdentifier() {
        assertToken("€", Token.Kind.ERRORTOKEN);
    }

    @Test
    public void testTypeComment() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testInt() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testLong() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testFloat() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testUnderscoreLiterals() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testInvalidUnderscoreLiterals() {
        String[] invalid = new String[]{
                        // Trailing underscores:
                        "0_", "42_", /* "1.4j_", */ "0x_", "0b1_", "0xf_", "0o5_", "0 if 1_Else 1",
                        // Underscores in the base selector:
                        "0_b0", "0_xf", "0_o5",
                        // Old-style octal, still disallowed:
                        /* "0_7", "09_99", */
                        // Multiple consecutive underscores:
                        "4_______2", "0.1__4", "0.1__4j", "0b1001__0100", "0xffff__ffff", "0x___",
                        "0o5__77", "1e1__0", "1e1__0j",
                        // Underscore right before a dot:
                        "1_.4", "1_.4j",
                        // Underscore right after a dot:
                        // TODO "1._4", "1._4j", "._5", "._5j",
                        // Underscore right after a sign:
                        "1.0e+_1", "1.0e+_1j",
                        // Underscore right before j:
                        "1.4_j", "1.4e5_j",
                        // Underscore right before e:
                        "1_e1", "1.4_e1", "1.4_e1j",
                        // Underscore right after e:
                        // TODO "1e_1", "1.4e_1", "1.4e_1j",
                        // Complex cases with parens:
                        "(1+1.5_j_)", "(1+1.5_j)",};

        for (String code : invalid) {
            Token[] tokens = getTokens(code);

            boolean wasError = false;
            for (Token token : tokens) {
                if (token.type == Token.Kind.ERRORTOKEN) {
                    wasError = true;
                    break;
                }
            }
            assertTrue(wasError);
        }
    }

    @Test
    public void testNumbers() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testString() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testFunction() throws Exception {
        checkTokensFromTestDataFile();
    }

    @Test
    public void testComparison() throws Exception {
        checkTokensFromTestDataFile();
    }

    // Test
    public void testNewLines() {
        checkTokens("a = 1\n" + "b = 2", new String[]{
                        "Token NAME [0, 1] (1, 0) (1, 1) 'a'",
                        "Token EQUAL [2, 3] (1, 2) (1, 3) '='",
                        "Token NUMBER [4, 5] (2, -1) (2, 0) '1'",
                        "Token NEWLINE [5, 6] (3, 0) (3, 1) '\n'",
                        "Token NAME [6, 7] (3, 1) (3, 2) 'b'",
                        "Token EQUAL [8, 9] (3, 3) (3, 4) '='",
                        "Token NUMBER [10, 11] (3, 5) (3, 6) '2'"});
    }

    private static Token[] getTokens(String code) {
        Tokenizer tokenizer = new Tokenizer(code, EnumSet.of(Tokenizer.Flag.EXECT_INPUT, Tokenizer.Flag.TYPE_COMMENT));
        Token token;

        ArrayList<Token> tokens = new ArrayList<>();

        do {
            token = tokenizer.next();
            tokens.add(token);
        } while (token.type != Token.Kind.ENDMARKER);
        return tokens.toArray(new Token[tokens.size()]);
    }

    private String getFileName() {
        return name.getMethodName();
    }

    private static void checkTokens(String code, String[] tokens) {
        Tokenizer tokenizer = new Tokenizer(code, EnumSet.of(Tokenizer.Flag.EXECT_INPUT, Tokenizer.Flag.TYPE_COMMENT));
        Token token = tokenizer.next();
        int index = 0;

        if (tokens != null) {
            while (token.type != Token.Kind.ENDMARKER) {
                assertEquals(tokens[index], tokenizer.toString(token));
                index++;
                token = tokenizer.next();
            }
            assertEquals(tokens.length, index);
        } else {
            StringBuilder sb = new StringBuilder("new String[]{\n");
            boolean first = true;
            while (token.type != Token.Kind.ENDMARKER) {
                if (first) {
                    first = false;
                } else {
                    sb.append(",\n");
                }
                sb.append("            \"");
                sb.append(tokenizer.toString(token));
                sb.append("\"");
                index++;
                token = tokenizer.next();
            }
            sb.append("}");
            fail(sb.toString());
        }

    }

    private static int getCPythonValueOfTokenType(int kind) {
        switch (kind) {
            case Token.Kind.ENDMARKER:
                return 0;
            case Token.Kind.NAME:
                return 1;
            case Token.Kind.NUMBER:
                return 2;
            case Token.Kind.STRING:
                return 3;
            case Token.Kind.NEWLINE:
                return 4;
            case Token.Kind.INDENT:
                return 5;
            case Token.Kind.DEDENT:
                return 6;
            case Token.Kind.LPAR:
                return 7;
            case Token.Kind.RPAR:
                return 8;
            case Token.Kind.LSQB:
                return 9;
            case Token.Kind.RSQB:
                return 10;
            case Token.Kind.COLON:
                return 11;
            case Token.Kind.COMMA:
                return 12;
            case Token.Kind.SEMI:
                return 13;
            case Token.Kind.PLUS:
                return 14;
            case Token.Kind.MINUS:
                return 15;
            case Token.Kind.STAR:
                return 16;
            case Token.Kind.SLASH:
                return 17;
            case Token.Kind.VBAR:
                return 18;
            case Token.Kind.AMPER:
                return 19;
            case Token.Kind.LESS:
                return 20;
            case Token.Kind.GREATER:
                return 21;
            case Token.Kind.EQUAL:
                return 22;
            case Token.Kind.DOT:
                return 23;
            case Token.Kind.PERCENT:
                return 24;
            case Token.Kind.LBRACE:
                return 25;
            case Token.Kind.RBRACE:
                return 26;
            case Token.Kind.EQEQUAL:
                return 27;
            case Token.Kind.NOTEQUAL:
                return 28;
            case Token.Kind.LESSEQUAL:
                return 29;
            case Token.Kind.GREATEREQUAL:
                return 30;
            case Token.Kind.TILDE:
                return 31;
            case Token.Kind.CIRCUMFLEX:
                return 32;
            case Token.Kind.LEFTSHIFT:
                return 33;
            case Token.Kind.RIGHTSHIFT:
                return 34;
            case Token.Kind.DOUBLESTAR:
                return 35;
            case Token.Kind.PLUSEQUAL:
                return 36;
            case Token.Kind.MINEQUAL:
                return 37;
            case Token.Kind.STAREQUAL:
                return 38;
            case Token.Kind.SLASHEQUAL:
                return 39;
            case Token.Kind.PERCENTEQUAL:
                return 40;
            case Token.Kind.AMPEREQUAL:
                return 41;
            case Token.Kind.VBAREQUAL:
                return 42;
            case Token.Kind.CIRCUMFLEXEQUAL:
                return 43;
            case Token.Kind.LEFTSHIFTEQUAL:
                return 44;
            case Token.Kind.RIGHTSHIFTEQUAL:
                return 45;
            case Token.Kind.DOUBLESTAREQUAL:
                return 46;
            case Token.Kind.DOUBLESLASH:
                return 47;
            case Token.Kind.DOUBLESLASHEQUAL:
                return 48;
            case Token.Kind.AT:
                return 49;
            case Token.Kind.ATEQUAL:
                return 50;
            case Token.Kind.RARROW:
                return 51;
            case Token.Kind.ELLIPSIS:
                return 52;
            case Token.Kind.COLONEQUAL:
                return 53;
            case Token.Kind.OP:
                return 54;
            case Token.Kind.AWAIT:
                return 55;
            case Token.Kind.ASYNC:
                return 56;
            case Token.Kind.TYPE_IGNORE:
                return 57;
            case Token.Kind.TYPE_COMMENT:
                return 58;
            case Token.Kind.ERRORTOKEN:
                return 60;
        }
        return -1;
    }

    private void checkTokensFromTestDataFile() throws Exception {
        String testText = getTestFile();
        String[] testTextLines = testText.split("\n");

        // obtaining test lines from the test file. Some lines can be commented out
        ArrayList<String> testLines = new ArrayList<>();
        for (String line : testTextLines) {
            if (!(line.startsWith("//") || line.startsWith("#") || line.isEmpty())) {
                testLines.add(line);
            }
        }

        String goldenText = getGoldenFile();
        String[] goldenTextLines = goldenText.split("\n");
        HashMap<String, List<String>> goldenTokens = new HashMap<>();

        ArrayList<String> goldenTextTokens = new ArrayList<>();

        for (String line : goldenTextLines) {
            boolean isToken = line.startsWith("Token");

            if (isToken) {
                goldenTextTokens.add(line);
            } else if (!line.trim().isEmpty()) {
                goldenTextTokens = new ArrayList<>();
                goldenTokens.put(line, goldenTextTokens);
            }
        }
        assertEquals("The count of tested lines is different from count of lines  in golden file.", goldenTokens.size(), testLines.size());

        for (String line : testLines) {
            assertTrue("Was not found golden result for line: '" + line + "'", goldenTokens.containsKey(line));
            List<String> goldenResult = goldenTokens.get(line);
            int goldenResultIndex = 0;
            Tokenizer tokenizer = new Tokenizer(line, EnumSet.of(Tokenizer.Flag.EXECT_INPUT, Tokenizer.Flag.TYPE_COMMENT));
            Token token;
            do {
                token = tokenizer.next();
                boolean isOP = opTypes.contains(token.type);
                StringBuilder sb = new StringBuilder();
                sb.append("Token type:").append(isOP ? getCPythonValueOfTokenType(Token.Kind.OP) : getCPythonValueOfTokenType(token.type));
                sb.append(" (").append(isOP ? "OP" : token.typeName()).append(") ");
                if (isOP) {
                    sb.append("exact_type:").append(getCPythonValueOfTokenType(token.type));
                    sb.append(" (").append(token.typeName()).append(") ");
                }
                sb.append("start:[").append(token.startLine).append(", ").append(token.startColumn).append("] ");
                sb.append("end:[").append(token.endLine).append(", ").append(token.endColumn).append("] ");
                sb.append("string:'").append(tokenizer.getTokenString(token)).append("'");
                String goldenToken = goldenResult.get(goldenResultIndex);
                assertEquals("Code: '" + line + "'", goldenToken, sb.toString());
                goldenResultIndex++;
            } while (token.type != Token.Kind.ENDMARKER);
            assertEquals(goldenResultIndex, goldenResult.size());
        }
    }

    private String getGoldenFile() throws IOException {
        return Files.readString(Path.of("graalpython/com.oracle.graal.python.pegparser.test/testData/tokenizer/goldenFiles", getFileName() + ".token"));
    }

    private String getTestFile() throws IOException {
        return Files.readString(Path.of("graalpython/com.oracle.graal.python.pegparser.test/testData/tokenizer/testFiles", getFileName() + ".data"));
    }
}
