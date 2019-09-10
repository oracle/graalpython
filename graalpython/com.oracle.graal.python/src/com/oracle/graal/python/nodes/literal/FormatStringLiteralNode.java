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
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.List;

public class FormatStringLiteralNode extends LiteralNode {
    
    private static final String EMPTY_STRING = "";
    private final String value;

    @Children ExpressionNode[] expressions;
    @CompilerDirectives.CompilationFinal private Object[] splitValue;
    
    public FormatStringLiteralNode(String value) {
        this.value = value;
    }
    
    @Override
    public Object execute(VirtualFrame frame) {
        if (value.isEmpty()) {
            return EMPTY_STRING;
        }
        if (splitValue == null) {
            // was not parsed yet
            CompilerDirectives.transferToInterpreterAndInvalidate();
            ArrayList<Object> parts = new ArrayList<>();
            int exprCount = topParser(this, parts, frame);
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
            }
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
            for (Object part : splitValue) {
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
    
    public String getValue() {
        return value;
    }

    public static FormatStringLiteralNode create(String string) {
        return new FormatStringLiteralNode(string);
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
    
    static final String EMPTY_EXPRESSION_MESSAGE = "f-string: empty expression not allowed";
    static final String SINGLE_BRACE_MESSAGE = "f-string: single '}' is not allowed";
    static final String INVALID_CONVERSION_MESSAGE = "f-string: invalid conversion character: expected 's', 'r', or 'a'";
    static final String UNTERMINATED_STRING = "f-string: unterminated string";
    
    
    static int topParser(FormatStringLiteralNode node, List<Object> resultParts, VirtualFrame frame) {
        String value = node.value;
        int len = value.length();
        int index = 0;
        int state = STATE_TEXT;
        int start = 0;
        int end = 0;
        int numberOfExpressions = 0;
        int braceLevel = 0;
        while (index < len) {
            char ch = value.charAt(index);
            switch (state){
                case STATE_TEXT:
                    switch(ch) {
                        case '{':
                            if (start < index) {
                                addString(value, start, index, resultParts);
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
                        case '}': PythonLanguage.getCore().raiseInvalidSyntax(node, EMPTY_EXPRESSION_MESSAGE); break;
                        default: index--; state = STATE_EXPRESSION;
                        
                    }
                    break;
                case STATE_AFTER_CLOSE_BRACE:
                    if (ch == '}') {
                        // after '}' should in this moment follow second '}'
                        if (start < index) {
                            addString(value, start, index, resultParts);
                        }
                        braceLevel++;
                        if (braceLevel == 0) {
                            state = STATE_TEXT;
                        }
                        start = index + 1;
                    } else {
                        PythonLanguage.getCore().raiseInvalidSyntax(node, SINGLE_BRACE_MESSAGE);
                    }
                    break;
                case STATE_EXPRESSION:
                    if (ch == '}') {
                        if (start < index) {
                            numberOfExpressions++;
                            resultParts.add(createExpression("format", value.substring(start, index), frame));
                        }
                        braceLevel--;
                        state = STATE_TEXT;
                        start = index + 1;
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
                                PythonLanguage.getCore().raiseInvalidSyntax(node, UNTERMINATED_STRING);
                            }
                        }
                                
                    } else if (ch == '!') {
                        state = STATE_AFTER_EXCLAMATION;
                    }
                    break;
                case STATE_AFTER_EXCLAMATION:
                    switch (ch) {
                        case 's':
                            if (start < index - 1) {
                                numberOfExpressions++;
                                resultParts.add(createExpression("str", value.substring(start, index - 1), frame));
                            }
                            break;
                        case 'r':
                            if (start < index - 1) {
                                numberOfExpressions++;
                                resultParts.add(createExpression("repr", value.substring(start, index - 1), frame));
                            }
                            break;
                        case 'a':
                            if (start < index - 1) {
                                numberOfExpressions++;
                                resultParts.add(createExpression("ascii", value.substring(start, index - 1), frame));
                            }
                            break;
                        default:
                            PythonLanguage.getCore().raiseInvalidSyntax(node, INVALID_CONVERSION_MESSAGE);
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
                    addString(value, start, index, resultParts);
                }
                break;
            case STATE_AFTER_CLOSE_BRACE: {
                PythonLanguage.getCore().raiseInvalidSyntax(node, SINGLE_BRACE_MESSAGE);
                break;
            }
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
    
    private static ExpressionNode createExpression(String functionName, String text, VirtualFrame frame) {
        PythonParser parser = PythonLanguage.getCore().getParser();
        String toEval = functionName + "(" + text + ")";
        Source source = Source.newBuilder(PythonLanguage.ID, toEval, "<fstring>").build();
        Node expression = parser.parse(PythonParser.ParserMode.InlineEvaluation, PythonLanguage.getCore(), source, frame);
        return (ExpressionNode)expression;
    }
}
