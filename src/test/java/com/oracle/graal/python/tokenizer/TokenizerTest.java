/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.tokenizer;

import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TestName;


public class TokenizerTest {

    @Rule
    public TestName name = new TestName();

    private static HashSet<Token.Kind> opTypes = new HashSet();

    {
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
        opTypes.add(Token.Kind.NOTEQUAL);
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

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Test
    public void testInt() throws Exception {
        checkTokensFromtestDataFile();
    }

   @Test
    public void testLong() throws Exception{
        checkTokensFromtestDataFile();
    }

    @Test
    public void testFloat() throws Exception {
        checkTokensFromtestDataFile();
    }

    @Test
    public void testUnderscoreLiterals() throws Exception {
        checkTokensFromtestDataFile();
    }

    @Test
    public void testInvalidUnderscoreLiterals() throws Exception{
        String[] invalid = new String[]{
            // Trailing underscores:
            "0_", "42_", /*"1.4j_",*/ "0x_", "0b1_", "0xf_", "0o5_", "0 if 1_Else 1",
            // Underscores in the base selector:
            "0_b0", "0_xf", "0_o5",
            // Old-style octal, still disallowed:
            /*"0_7", "09_99",*/
            // Multiple consecutive underscores:
            "4_______2", "0.1__4", "0.1__4j", "0b1001__0100", "0xffff__ffff", "0x___",
            "0o5__77", "1e1__0", "1e1__0j",
            // Underscore right before a dot:
            "1_.4", "1_.4j",
            // Underscore right after a dot:
            //TODO            "1._4", "1._4j", "._5", "._5j",
            // Underscore right after a sign:
            "1.0e+_1", "1.0e+_1j",
            // Underscore right before j:
            "1.4_j", "1.4e5_j",
            // Underscore right before e:
            "1_e1", "1.4_e1", "1.4_e1j",
            // Underscore right after e:
            //TODO            "1e_1", "1.4e_1", "1.4e_1j",
            // Complex cases with parens:
            "(1+1.5_j_)", "(1+1.5_j)",};

        for (String code : invalid) {
            //System.out.println(code);
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
    public void testNumbers() throws Exception{
        checkTokensFromtestDataFile();
    }

    @Test
    public void testString() throws Exception {
        checkTokensFromtestDataFile();
    }

    @Test
    public void testFunction() throws Exception {
        checkTokensFromtestDataFile();
    }

    @Test
    public void testComparison() throws Exception {
        checkTokensFromtestDataFile();
    }

    //Test
    public void testNewLines() {
        checkTokens("a = 1\n"
                + "b = 2", new String[]{
                    "Token NAME [0, 1] (1, 0) (1, 1) 'a'",
                    "Token EQUAL [2, 3] (1, 2) (1, 3) '='",
                    "Token NUMBER [4, 5] (2, -1) (2, 0) '1'",
                    "Token NEWLINE [5, 6] (3, 0) (3, 1) '\n'",
                    "Token NAME [6, 7] (3, 1) (3, 2) 'b'",
                    "Token EQUAL [8, 9] (3, 3) (3, 4) '='",
                    "Token NUMBER [10, 11] (3, 5) (3, 6) '2'"});
    }

    private Token[] getTokens(String code) {
        Tokenizer tokenizer = new Tokenizer(code);
        Token token;

        ArrayList<Token> tokens = new ArrayList();

        do {
            token = tokenizer.next();
            System.out.println(tokenizer.toString(token));
            tokens.add(token);
        } while (token.type != Token.Kind.ENDMARKER);
        return tokens.toArray(new Token[tokens.size()]);
    }

    private void checkTokens(String code, String[] tokens) {
        Tokenizer tokenizer = new Tokenizer(code);
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
            System.out.println(sb.toString());
            assertTrue(false);
        }

    }

    private static int getCPythonValueOfTokenType(Token.Kind kind) {
        switch (kind) {
            case ENDMARKER:
                return 0;
            case NAME:
                return 1;
            case NUMBER:
                return 2;
            case STRING:
                return 3;
            case NEWLINE:
                return 4;
            case INDENT:
                return 5;
            case DEDENT:
                return 6;
            case LPAR:
                return 7;
            case RPAR:
                return 8;
            case LSQB:
                return 9;
            case RSQB:
                return 10;
            case COLON:
                return 11;
            case COMMA:
                return 12;
            case SEMI:
                return 13;
            case PLUS:
                return 14;
            case MINUS:
                return 15;
            case STAR:
                return 16;
            case SLASH:
                return 17;
            case VBAR:
                return 18;
            case AMPER:
                return 19;
            case LESS:
                return 20;
            case GREATER:
                return 21;
            case EQUAL:
                return 22;
            case DOT:
                return 23;
            case PERCENT:
                return 24;
            case LBRACE:
                return 25;
            case RBRACE:
                return 26;
            case EQEQUAL:
                return 27;
            case NOTEQUAL:
                return 28;
            case LESSEQUAL:
                return 29;
            case GREATEREQUAL:
                return 30;
            case TILDE:
                return 31;
            case CIRCUMFLEX:
                return 32;
            case LEFTSHIFT:
                return 33;
            case RIGHTSHIFT:
                return 34;
            case DOUBLESTAR:
                return 35;
            case PLUSEQUAL:
                return 36;
            case MINEQUAL:
                return 37;
            case STAREQUAL:
                return 38;
            case SLASHEQUAL:
                return 39;
            case PERCENTEQUAL:
                return 40;
            case AMPEREQUAL:
                return 41;
            case VBAREQUAL:
                return 42;
            case CIRCUMFLEXEQUAL:
                return 43;
            case LEFTSHIFTEQUAL:
                return 44;
            case RIGHTSHIFTEQUAL:
                return 45;
            case DOUBLESTAREQUAL:
                return 46;
            case DOUBLESLASH:
                return 47;
            case DOUBLESLASHEQUAL:
                return 48;
            case AT:
                return 49;
            case ATEQUAL:
                return 50;
            case RARROW:
                return 51;
            case ELLIPSIS:
                return 52;
            case COLONEQUAL:
                return 53;
            case OP:
                return 54;
            case AWAIT:
                return 55;
            case ASYNC:
                return 56;
            case TYPE_IGNORE:
                return 57;
            case TYPE_COMMENT:
                return 58;
            case ERRORTOKEN:
                return 60;
        }
        return -1;
    }

    private void checkTokensFromtestDataFile() throws Exception {
        String testText = getTestFile();
        String[] testTextLines = testText.split("\n");

        ArrayList<String> testLines = new ArrayList<>();
        for (String line : testTextLines) {
            if (!(line.startsWith("//") || line.startsWith("#") || line.isEmpty())) {
                testLines.add(line);
            }
        }

        String goldenText = getGoldenFile();
        String[] goldenTextLines = goldenText.split("\n");
        HashMap<String, List<String>> goldenTokens = new HashMap();

        ArrayList<String> goldenTextTokens = new ArrayList();

        for (String line : goldenTextLines) {
            boolean isToken = line.startsWith("Token");

            if (isToken) {
                goldenTextTokens.add(line);
            } else if (!line.trim().isEmpty()) {
                goldenTextTokens = new ArrayList();
                goldenTokens.put(line, goldenTextTokens);
            }
        }
        assertEquals("The count of tested lines is different from count of lines  in golden file.", goldenTokens.size(), testLines.size());

        for (String line : testLines) {
            assertTrue("Was not found golden result for line: '" + line + "'", goldenTokens.containsKey(line));
            List<String> goldenResult = goldenTokens.get(line);
            int goldenResultIndex = 0;
            Tokenizer tokenizer = new Tokenizer(line);
            Token token;
            do {
                token  = tokenizer.next();
                boolean isOP = opTypes.contains(token.type);
                StringBuffer sb = new StringBuffer();
                sb.append("Token type:").append(isOP ? getCPythonValueOfTokenType(Token.Kind.OP) : getCPythonValueOfTokenType(token.type));
                sb.append(" (").append(isOP ? "OP" : token.type.name()).append(") ");
                if (isOP) {
                    sb.append("exact_type:").append(getCPythonValueOfTokenType(token.type));
                    sb.append(" (").append(token.type.name()).append(") ");
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

    private String getGoldenFile() {
        InputStream fis = getClass().getClassLoader().getResourceAsStream("tokenizer/goldenFiles/" + name.getMethodName() + ".token");
        return new BufferedReader(new InputStreamReader(fis)).lines().collect(Collectors.joining("\n"));
    }

    private String getTestFile() {
        InputStream fis = getClass().getClassLoader().getResourceAsStream("tokenizer/testFiles/" + name.getMethodName() + ".data");
        return new BufferedReader(new InputStreamReader(fis)).lines().collect(Collectors.joining("\n"));
    }

}
