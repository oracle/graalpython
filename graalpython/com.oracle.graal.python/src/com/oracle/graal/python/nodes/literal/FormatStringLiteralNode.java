/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
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
    
    
    public static final String NORMAL_PREFIX = "<n>";
    public static final String FORMAT_STRING_PREFIX = "<f>";
    
    private static final String EMPTY_STRING = "";
    private final String[] values;
    
    static final String ERROR_MESSAGE_EMPTY_EXPRESSION = "f-string: empty expression not allowed";
    static final String ERROR_MESSAGE_SINGLE_BRACE = "f-string: single '}' is not allowed";
    static final String ERROR_MESSAGE_INVALID_CONVERSION = "f-string: invalid conversion character: expected 's', 'r', or 'a'";
    static final String ERROR_MESSAGE_UNTERMINATED_STRING = "f-string: unterminated string";
    static final String ERROR_MESSAGE_INVALID_SYNTAX = "f-string: invalid syntax";

    @Children ExpressionNode[] expressions;
    @CompilerDirectives.CompilationFinal private Object[] splitValue;
    private boolean parsedCorrectly;
    
    public FormatStringLiteralNode(String[] values) {
        this.values = values;
        this.parsedCorrectly = true;
    }
    
    @Override
    public Object execute(VirtualFrame frame) {
        if (values.length == 0) {
            return EMPTY_STRING;
        }
        if (parsedCorrectly && splitValue == null) {
            // was not parsed yet
            CompilerDirectives.transferToInterpreterAndInvalidate();
            List<int[]> parts = new ArrayList<>();
            topParser(this, this.values, parts, frame, true);
            List<String> results = new ArrayList<>();
            int exprCount = processValuesParts(parts, results, 0, parts.size());
            int partsCount = results.size();
            if (partsCount == 0) {
                splitValue = new Object[0];
            } else {
                splitValue = new Object[partsCount];
                if (exprCount > 0) {
                    ExpressionNode[] exprs = new ExpressionNode[exprCount];
                    int exprIndex = 0;
                    for (int i = 0; i < results.size(); i++) {
                        String part = results.get(i);
                        if (part.startsWith(NORMAL_PREFIX)) {
                            splitValue[i] = part.substring(NORMAL_PREFIX.length());
                        } else {
                            try {
                                exprs[exprIndex] = createExpression(part.substring(FORMAT_STRING_PREFIX.length()), frame);
                                splitValue[i] = exprs[exprIndex];
                                exprIndex++;
                            } catch (Exception e) {
                                parsedCorrectly = false;
                                raiseInvalidSyntax(this, ERROR_MESSAGE_INVALID_SYNTAX);
                            }
                            
                        }
                    }
                    expressions = insert(exprs);
                } else {
                    for (int i = 0; i < results.size(); i++) {
                        String part = results.get(i);
                        splitValue[i] = part.substring(NORMAL_PREFIX.length());
                    }
                }
            }
            /*int exprCount = topParser(this, this.values, parts, frame, true);
            int partsCount = parts.size();
            if (partsCount == 0) {
                splitValue = new Object[0];
            } else {
                splitValue = parts.toArray();
                if (exprCount > 0) {
                    ExpressionNode[] exprs = new ExpressionNode[exprCount];
                    int exprIndex = 0;
                    for (Object part : splitValue) {
                        if (part instanceof ExpressionNode) {
                            exprs[exprIndex++] = (ExpressionNode) part;
                        }
                    }
                    expressions = insert(exprs);
                }
            }*/
        }
        if (!parsedCorrectly) {
            raiseInvalidSyntax(this, ERROR_MESSAGE_INVALID_SYNTAX);
        }
        if (splitValue.length == 1) {
            // there is only one string or expression
            if (expressions != null) {
                // there is only one expression
                // expressions[0] == splitValue[0] -> no casting needed
                return expressions[0].execute(frame);
            }
            // there is only one string
            return splitValue[0];
        } else {
            StringBuilder result = new StringBuilder();
            int exprIndex = 0;
            for (int i = 0; i < splitValue.length;  i++) {
                Object part = splitValue[i];
                if (part instanceof String) {
                    addToResult(result, part);
                } else {
                    addToResult(result, expressions[exprIndex++].execute(frame));
                }
            }
            return result.toString();
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void addToResult(StringBuilder result, Object part) {
        result.append(part);
    }
    
    public String[] getValues() {
        return values;
    }

    public static FormatStringLiteralNode create(String[] values) {
        return new FormatStringLiteralNode(values);
    }
    
    // parsing the format string
    private static final int STATE_TEXT = 1;  // in text 
    private static final int STATE_AFTER_OPEN_BRACE = 2; // just after {
    private static final int STATE_AFTER_CLOSE_BRACE = 3; // just after }
    private static final int STATE_AFTER_EXCLAMATION = 4; // just after !
    private static final int STATE_EXPRESSION = 5; // in {}
    private static final int STATE_UNKNOWN = 6;
    private static final int STATE_EXPRESSION_STEING_A = 7;
    private static final int STATE_ESPRESSION_STRING_Q = 8;
    private static final int STATE_FORMAT_SPECIFIER = 9;
    
    private static final int TYPE_STRING = 1;
    private static final int TYPE_EXPRESSION = 2;
    private static final int TYPE_EXPRESSION_STR = 3;
    private static final int TYPE_EXPRESSION_REPR = 4;
    private static final int TYPE_EXPRESSION_ASCII = 5;
    
    
    
    
    static int topParser(FormatStringLiteralNode node, String[] values, List<int[]> resultParts, VirtualFrame frame, boolean topLevel) {
        int index = 0;
        int state = STATE_TEXT;
        int start = 0;

        int expressionType = TYPE_EXPRESSION;
        int numberOfExpressions = 0;
        int braceLevel = 0;
        int braceLevelInExpression = 0;
        int braceLevelInFormatSpecifier = 0;
        String formatValue = null;
        String formatSpecifier = null;
        for (int valueIndex = 0; valueIndex < values.length; valueIndex++) {
            String value = values[valueIndex];
            if (value.startsWith(NORMAL_PREFIX)) {
//                resultParts.add(value.substring(NORMAL_PREFIX.length()));
                resultParts.add(new int[]{TYPE_STRING, valueIndex, 3, value.length()});
            } else {
                value = value.substring(FORMAT_STRING_PREFIX.length());
                int len = value.length();
                index = 0;
                while (index < len) {
                    char ch = value.charAt(index);
                    switch (state){
                        case STATE_TEXT:
                            switch(ch) {
                                case '{':
                                    if (start < index) {
//                                        addString(value, start, index, resultParts);
                                         resultParts.add(new int[]{TYPE_STRING, valueIndex, start + 3, index + 3});
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
                            switch(ch) {
                                case '{': 
                                    state = STATE_TEXT; 
                                    braceLevel--;
                                    break;
                                case '}': raiseInvalidSyntax(node, ERROR_MESSAGE_EMPTY_EXPRESSION); break;
                                default: 
                                    index--; 
                                    state = STATE_EXPRESSION;
                                    expressionType = TYPE_EXPRESSION;
                                    braceLevelInExpression = 0;
                            }
                            break;
                        case STATE_AFTER_CLOSE_BRACE:
                            if (ch == '}') {
                                // after '}' should in this moment follow second '}'
                                if (start < index) {
//                                    addString(value, start, index, resultParts);
                                    resultParts.add(new int[]{TYPE_STRING, valueIndex, start + 3, index + 3});
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
                            if (ch == '{') {
                                braceLevelInExpression++;
                            } else if (ch == '}') {
                                if (braceLevelInExpression == 0){
                                    if (start < index) {
                                        numberOfExpressions++;
//                                        resultParts.add(createExpression("format(" + value.substring(start, index) + ")", frame));
                                        resultParts.add(new int[]{expressionType, valueIndex, start + FORMAT_STRING_PREFIX.length(), index  + FORMAT_STRING_PREFIX.length(), 0});
                                    }
                                    braceLevel--;
                                    state = STATE_TEXT;
                                    start = index + 1;
                                } else {
                                    braceLevelInExpression--;
                                }
                            } else if (ch == '\'' || ch == '"') {
                                char startq = ch;
                                boolean triple = false;
                                boolean inString = true;
                                index++;
                                if (index < len && startq == value.charAt(index)) {
                                    index++;
                                    if(index< len && startq == value.charAt(index)) {
                                        // we are in ''' or """ string
                                        triple = true;
                                        index++;
                                    } else {
                                        // we are in empty string "" or ''
                                        inString = false;
                                    } 
                                }
                                if (inString) {
                                    while (index < len && value.charAt(index) != startq) {
                                      index++;
                                    }
                                    // the end of the string reached
                                    if (triple ) {
                                        if (index + 1 < len && startq == value.charAt(index + 1)
                                                && index + 2 < len && startq == value.charAt(index + 2)) {
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

                            } else if (ch == '!') {
                                state = STATE_AFTER_EXCLAMATION;
                            } else if (ch == ':') {
                                int[] specifierValue;
                                specifierValue = new int[] {expressionType, valueIndex, start + 3, index + 3, 0};
                                index++;
                                start = index;
                                int braceLevelInSpecifier = 0;
                                while (index < len) {
                                    ch = value.charAt(index);
                                    if (ch == '{') {
                                        braceLevelInSpecifier++;
                                    } else if (ch == '}') {
                                        braceLevelInSpecifier--;
                                        if (braceLevelInSpecifier == -1) {
//                                            resultParts.add(createExpression("format(" + formatValue + ")", frame));
                                            List<int[]> specifierParts = new ArrayList<>();
                                            topParser(node, new String[]{FORMAT_STRING_PREFIX+value.substring(start, index)}, specifierParts, frame, false);
                                            specifierValue[4] = specifierParts.size();
                                            resultParts.add(specifierValue);
                                            for (int[]part : specifierParts) {
                                                part[1] = valueIndex;
                                                part[2] += start;
                                                part[3] += start;
                                            }
                                            resultParts.addAll(specifierParts);
                                            start = index + 1;
                                            break;
                                        }
                                    }
                                    index++;
                                }
                            }
                            break;
                        case STATE_AFTER_EXCLAMATION:
                            switch (ch) {
                                case 's':
                                    expressionType = TYPE_EXPRESSION_STR;
                                    break;
                                case 'r':
                                    expressionType = TYPE_EXPRESSION_REPR;
                                    break;
                                case 'a':
                                    expressionType = TYPE_EXPRESSION_ASCII;
                                    break;
                                default:
                                    raiseInvalidSyntax(node, ERROR_MESSAGE_INVALID_CONVERSION);
                            }
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
                            //handle the end of the string
                            resultParts.add(new int[]{TYPE_STRING, valueIndex, start+3, index+3});
//                            addString(value, start, index, resultParts);
                        }
                        break;
                    case STATE_AFTER_CLOSE_BRACE: {
                        raiseInvalidSyntax(node, ERROR_MESSAGE_SINGLE_BRACE);
                        break;
                    }
                }
            }
        }
        return numberOfExpressions;
    }
    
    private int processValuesParts(List<int[]> parts, List<String> result, int from, int to) {
        int numberOfExpressions = 0;
        String text = "";
        String expression = "";
        for (int index = from; index < to; index++ ) {
            int[]part = parts.get(index);
            if (part[0] == TYPE_STRING) {
                text = text + values[part[1]].substring(part[2], part[3]);
            } else {
                if (!text.isEmpty()) {
                    result.add(NORMAL_PREFIX + text);
                    text = "";
                }
                expression = "format(";
                switch(part[0]) {
                    case TYPE_EXPRESSION_ASCII:
                        expression += "asci("; break;
                    case TYPE_EXPRESSION_REPR:
                        expression += "repr("; break;
                    case TYPE_EXPRESSION_STR:
                        expression += "str("; break;
                }
                expression += "(" + values[part[1]].substring(part[2], part[3]) + ")";
                if (part[0] != TYPE_EXPRESSION) {
                    expression += ")";
                }
                if (part[4] == 0) {
                    expression += ")";
                } else {
                    expression += ",(";
                    List<String> specifierParts = new ArrayList<> ();
                    processValuesParts(parts, specifierParts, index + 1, index + 1 + part[4]);
                    boolean first = true;
                    for (String specifierPart : specifierParts) {
                        if (first) {
                            first = false;
                        } else {
                            expression += "+";
                        }
                        if (specifierPart.startsWith(NORMAL_PREFIX)) {
                            expression += "\"" + specifierPart.substring(NORMAL_PREFIX.length()) + "\"";
                        } else {
                            expression += specifierPart.substring(FORMAT_STRING_PREFIX.length());
                        }
                    }
                    index = index + 1 + part[4];
                    expression += "))";
                }
                result.add(FORMAT_STRING_PREFIX + expression);
                numberOfExpressions++;
            }

        }
        if (!text.isEmpty()) {
            result.add(NORMAL_PREFIX + text);
        }
        return numberOfExpressions;
    }
    
//    private static int topParser(StringFormatLiteralNode node, List<Object> resultParts, VirtualFrame frame) {
    private static void addString(String value, int start, int end, List<Object> parts) {
        String text = value.substring(start, end);
        int currentLen = parts.size();
        int lastIndex = currentLen - 1;
        if (currentLen > 0 && parts.get(lastIndex) instanceof String) {
            parts.set(lastIndex, parts.get(lastIndex) + text);
        } else {
            parts.add(text);
        }
    }
    
    private static void raiseInvalidSyntax(FormatStringLiteralNode node, String message) {
        PythonLanguage.getCore().raiseInvalidSyntax(node, message);
    }
    
    private static ExpressionNode createExpression(String src, VirtualFrame frame) {
        PythonParser parser = PythonLanguage.getCore().getParser();
        Source source = Source.newBuilder(PythonLanguage.ID, src, "<fstring>").build();
        Node expression = parser.parse(PythonParser.ParserMode.InlineEvaluation, PythonLanguage.getCore(), source, frame);
        return (ExpressionNode)expression;
    }
}
