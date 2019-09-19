/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.util.ArrayList;
import java.util.List;

public class FormatStringLiteralNode extends LiteralNode {

    // error messages from parsing
    static final String ERROR_MESSAGE_EMPTY_EXPRESSION = "f-string: empty expression not allowed";
    static final String ERROR_MESSAGE_SINGLE_BRACE = "f-string: single '}' is not allowed";
    static final String ERROR_MESSAGE_INVALID_CONVERSION = "f-string: invalid conversion character: expected 's', 'r', or 'a'";
    static final String ERROR_MESSAGE_UNTERMINATED_STRING = "f-string: unterminated string";
    static final String ERROR_MESSAGE_INVALID_SYNTAX = "f-string: invalid syntax";

    private static final String EMPTY_STRING = "";

    // token types, they are int, because there are part of int[]
    protected static final int TOKEN_TYPE_STRING = 1;
    protected static final int TOKEN_TYPE_EXPRESSION = 2;
    protected static final int TOKEN_TYPE_EXPRESSION_STR = 3;
    protected static final int TOKEN_TYPE_EXPRESSION_REPR = 4;
    protected static final int TOKEN_TYPE_EXPRESSION_ASCII = 5;

    public static class StringPart {
        /**
         * Marks, whether the value is formatted string
         */
        private final boolean isFormatString;
        private final String text;

        public StringPart(String text, boolean isFormatString) {
            this.text = text;
            this.isFormatString = isFormatString;
        }

        public boolean isFormatString() {
            return isFormatString;
        }

        public String getText() {
            return text;
        }
    }

    private final StringPart[] values;

    @Children ExpressionNode[] expressions;

    @CompilerDirectives.CompilationFinal private List<int[]> tokens;
    // @CompilerDirectives.CompilationFinal private Object[] splitValue;
    private boolean parsedCorrectly;

    public FormatStringLiteralNode(StringPart[] values) {
        this.values = values;
        this.parsedCorrectly = true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (values.length == 0) {
            return EMPTY_STRING;
        }
        if (parsedCorrectly && tokens == null) {
            // was not parsed yet
            CompilerDirectives.transferToInterpreterAndInvalidate();
            for (StringPart part : values) {
                System.out.print(part.isFormatString ? "'" + part.text + "' " : "f'" + part.text + "' ");
            }
            System.out.println("");
            // create tokens
            tokens = createTokens(this, this.values, true);
            // create sources from tokens, that marks expressions
            String[] expressionSources = createExpressionSources(values, tokens);
            // and create the expressions
            ExpressionNode[] exprs = new ExpressionNode[expressionSources.length];
            try {
                for (int i = 0; i < expressionSources.length; i++) {
                    exprs[i] = createExpression(expressionSources[i], frame);
                }
                expressions = insert(exprs);
            } catch (Exception e) {
                // we don't need to keep the expressions, because they will not be executed
                parsedCorrectly = false;
                raiseInvalidSyntax(this, ERROR_MESSAGE_INVALID_SYNTAX);
            }
        }
        if (!parsedCorrectly) {
            // there was error during obtaining expressions -> don't execute and raise the same
            // error
            raiseInvalidSyntax(this, ERROR_MESSAGE_INVALID_SYNTAX);
        }
        StringBuilder result = new StringBuilder();
        int exprIndex = 0;
        for (int i = 0; i < tokens.size(); i++) {
            int[] token = tokens.get(i);
            if (token[0] == TOKEN_TYPE_STRING) {
                addToResult(result, values[token[1]].text.substring(token[2], token[3]));
            } else {
                addToResult(result, expressions[exprIndex++].execute(frame));
                i += token[4];
            }
        }
        return result.toString();
    }

    @CompilerDirectives.TruffleBoundary
    private void addToResult(StringBuilder result, Object part) {
        result.append(part);
    }

    public StringPart[] getValues() {
        return values;
    }

    public static FormatStringLiteralNode create(StringPart[] values) {
        return new FormatStringLiteralNode(values);
    }

    // protected for testing
    protected static String[] createExpressionSources(StringPart[] values, List<int[]> tokens) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < tokens.size(); index++) {
            int[] token = tokens.get(index);
            if (token[0] != TOKEN_TYPE_STRING) {
                // processing only expressions
                StringBuilder expression = new StringBuilder("format(");
                switch (token[0]) {
                    case TOKEN_TYPE_EXPRESSION_ASCII:
                        expression.append("ascii(");
                        break;
                    case TOKEN_TYPE_EXPRESSION_REPR:
                        expression.append("repr(");
                        break;
                    case TOKEN_TYPE_EXPRESSION_STR:
                        expression.append("str(");
                        break;
                }
                expression.append("(").append(values[token[1]].text.substring(token[2], token[3])).append(")");
                if (token[0] != TOKEN_TYPE_EXPRESSION) {
                    expression.append(")");
                }
                if (token[4] == 0) {
                    // there is no format specifier
                    expression.append(")");
                } else {
                    // the expression has token[4] specifiers parts
                    // obtains expressions in the format specifier
                    int indexPlusOne = index + 1;
                    String[] specifierExpressions = createExpressionSources(values, tokens.subList(indexPlusOne, indexPlusOne + token[4]));
                    expression.append(",(");
                    boolean first = true;
                    int expressionIndex = 0;
                    // move the index after the format specifier
                    index = indexPlusOne + token[4];
                    for (int sindex = indexPlusOne; sindex < index; sindex++) {
                        int[] stoken = tokens.get(sindex);
                        if (first) {
                            first = false;
                        } else {
                            // we have to concat the string in the sources here
                            // for example f'{10.123}:{3}.{4}' has simplified source: format(10.123,
                            // 3+"."+4)
                            expression.append("+");
                        }
                        if (stoken[0] == TOKEN_TYPE_STRING) {
                            // add the string
                            expression.append("\"").append(values[stoken[1]].text.substring(stoken[2], stoken[3])).append("\"");
                        } else {
                            // add the expression sorce
                            expression.append(specifierExpressions[expressionIndex]);
                            expressionIndex++;
                        }
                    }
                    index--;
                    expression.append("))");
                }
                result.add(expression.toString());
            }
        }
        return result.toArray(new String[result.size()]);
    }

    // parsing the format string
    private static final int STATE_TEXT = 1;  // in text
    private static final int STATE_AFTER_OPEN_BRACE = 2; // just after {
    private static final int STATE_AFTER_CLOSE_BRACE = 3; // just after }
    private static final int STATE_AFTER_EXCLAMATION = 4; // just after !
    private static final int STATE_EXPRESSION = 5; // in {}
    private static final int STATE_UNKNOWN = 6;

    // protected for testing
    /**
     * This is the parser of the fstring. As result is a list of tokens, when a token is int array
     * of leng 4 (if the token is string) or 5 (if the token is an expression. Meaning of the token
     * items: token[0] - this is token type. Can be string or expression (expression can (but
     * doesn't have to be) wrapped with str or repr or ascii function. token[1] - it's the index to
     * the node.values, from which was the token created token[2] - start of the text in the
     * node.value[token[1]] token[3] - end of the text in the node.value[token[1]] token[4] - only
     * for expressions. It's count how many tokens follow as tokens of format specifier. So the next
     * expression or string is not the next token, but the next token + token[4]
     * 
     * @param node it's needed for raising syntax errors
     * @param values this part of text will be parsed
     * @param topLevel if there is called recursion on topLevel = false, then the syntax error is
     *            raised
     * @return a list of tokens
     */
    protected static List<int[]> createTokens(FormatStringLiteralNode node, StringPart[] values, boolean topLevel) {
        int index;
        int state = STATE_TEXT;
        int start = 0;

        int expressionType = TOKEN_TYPE_EXPRESSION;
        int braceLevel = 0;
        int braceLevelInExpression = 0;
        List<int[]> resultParts = new ArrayList<>(values.length);
        for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
            StringPart value = values[valueIndex];
            if (!value.isFormatString) {
                resultParts.add(new int[]{TOKEN_TYPE_STRING, valueIndex, 0, value.text.length()});
            } else {
                String text = value.text;
                int len = text.length();
                index = 0;
                while (index < len) {
                    char ch = text.charAt(index);
                    switch (state) {
                        case STATE_TEXT:
                            switch (ch) {
                                case '{':
                                    if (start < index) {
                                        resultParts.add(new int[]{TOKEN_TYPE_STRING, valueIndex, start, index});
                                    }
                                    state = STATE_AFTER_OPEN_BRACE;
                                    start = index + 1;
                                    braceLevel++;
                                    break;
                                case '}':
                                    braceLevel--;
                                    state = STATE_AFTER_CLOSE_BRACE;
                                    break;
                            }
                            break;
                        case STATE_AFTER_OPEN_BRACE:
                            switch (ch) {
                                case '{':
                                    state = STATE_TEXT;
                                    braceLevel--;
                                    break;
                                case '}':
                                    raiseInvalidSyntax(node, ERROR_MESSAGE_EMPTY_EXPRESSION);
                                    break;
                                default:
                                    index--;
                                    state = STATE_EXPRESSION;
                                    expressionType = TOKEN_TYPE_EXPRESSION;
                                    braceLevelInExpression = 0;
                            }
                            break;
                        case STATE_AFTER_CLOSE_BRACE:
                            if (ch == '}') {
                                // after '}' should in this moment follow second '}'
                                if (start < index) {
                                    resultParts.add(new int[]{TOKEN_TYPE_STRING, valueIndex, start, index});
                                }
                                braceLevel++;
                                if (braceLevel == 0) {
                                    state = STATE_TEXT;
                                }
                                start = index + 1;
                            } else {
                                raiseInvalidSyntax(node, ERROR_MESSAGE_SINGLE_BRACE);
                            }
                            break;
                        case STATE_EXPRESSION:
                            switch (ch) {
                                case '{':

                                    braceLevelInExpression++;
                                    break;
                                case '}':
                                    if (braceLevelInExpression == 0) {
                                        if (start < index) {
                                            resultParts.add(new int[]{expressionType, valueIndex, start, index, 0});
                                        }
                                        braceLevel--;
                                        state = STATE_TEXT;
                                        start = index + 1;
                                    } else {
                                        braceLevelInExpression--;
                                    }
                                    break;
                                case '\'':
                                case '"':
                                    char startq = ch;
                                    boolean triple = false;
                                    boolean inString = true;
                                    index++;
                                    if (index < len && startq == text.charAt(index)) {
                                        index++;
                                        if (index < len && startq == text.charAt(index)) {
                                            // we are in ''' or """ string
                                            triple = true;
                                            index++;
                                        } else {
                                            // we are in empty string "" or ''
                                            inString = false;
                                        }
                                    }
                                    if (inString) {
                                        while (index < len && text.charAt(index) != startq) {
                                            index++;
                                        }
                                        // the end of the string reached
                                        if (triple) {
                                            if (index + 1 < len && startq == text.charAt(index + 1) && index + 2 < len && startq == text.charAt(index + 2)) {
                                                index += 2;
                                                inString = false;
                                            }
                                        } else if (index < len) {
                                            inString = false;
                                        }
                                        if (inString) {
                                            raiseInvalidSyntax(node, ERROR_MESSAGE_UNTERMINATED_STRING);
                                        }
                                    }
                                    break;
                                case '!':
                                    state = STATE_AFTER_EXCLAMATION;
                                    break;
                                case ':':
                                    int[] specifierValue;
                                    specifierValue = new int[]{expressionType, valueIndex, start, index, 0};
                                    index++;
                                    start = index;
                                    int braceLevelInSpecifier = 0;
                                    while (index < len) {
                                        ch = text.charAt(index);
                                        if (ch == '{') {
                                            braceLevelInSpecifier++;
                                        } else if (ch == '}') {
                                            braceLevelInSpecifier--;
                                            if (braceLevelInSpecifier == -1) {
                                                List<int[]> specifierParts = createTokens(node, new StringPart[]{new StringPart(text.substring(start, index), true)}, false);
                                                specifierValue[4] = specifierParts.size();
                                                resultParts.add(specifierValue);
                                                for (int[] part : specifierParts) {
                                                    part[1] = valueIndex;
                                                    part[2] += start;
                                                    part[3] += start;
                                                }
                                                resultParts.addAll(specifierParts);
                                                start = index + 1;
                                                state = STATE_TEXT;
                                                break;
                                            }
                                        }
                                        index++;
                                    }
                                    break;
                                default:
                                    break;
                            }
                            break;
                        case STATE_AFTER_EXCLAMATION:
                            switch (ch) {
                                case 's':
                                    expressionType = TOKEN_TYPE_EXPRESSION_STR;
                                    break;
                                case 'r':
                                    expressionType = TOKEN_TYPE_EXPRESSION_REPR;
                                    break;
                                case 'a':
                                    expressionType = TOKEN_TYPE_EXPRESSION_ASCII;
                                    break;
                                default:
                                    raiseInvalidSyntax(node, ERROR_MESSAGE_INVALID_CONVERSION);
                            }
                            if (start < index) {
                                resultParts.add(new int[]{expressionType, valueIndex, start, index - 1, 0});
                            }
                            expressionType = TOKEN_TYPE_EXPRESSION;
                            state = STATE_EXPRESSION;
                            start = index + 1;
                            break;
                        case STATE_UNKNOWN:
                            if (ch == '}') {
                                state = STATE_TEXT;
                                start = index + 1;
                            }
                            break;
                    }
                    index++;
                }
                switch (state) {
                    case STATE_TEXT:
                        if (start < index) {
                            // handle the end of the string
                            resultParts.add(new int[]{TOKEN_TYPE_STRING, valueIndex, start, index});
                        }
                        break;
                    case STATE_AFTER_CLOSE_BRACE: {
                        raiseInvalidSyntax(node, ERROR_MESSAGE_SINGLE_BRACE);
                        break;
                    }
                }
            }
        }
        return resultParts;
    }

    private static void raiseInvalidSyntax(FormatStringLiteralNode node, String message) {
        PythonLanguage.getCore().raiseInvalidSyntax(node, message);
    }

    private static ExpressionNode createExpression(String src, VirtualFrame frame) {
        PythonParser parser = PythonLanguage.getCore().getParser();
        Source source = Source.newBuilder(PythonLanguage.ID, src, "<fstring>").build();
        Node expression = parser.parse(PythonParser.ParserMode.InlineEvaluation, PythonLanguage.getCore(), source, frame);
        return (ExpressionNode) expression;
    }
}
