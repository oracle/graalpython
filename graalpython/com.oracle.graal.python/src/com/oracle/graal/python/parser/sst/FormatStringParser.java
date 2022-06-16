/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser.sst;

import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_BACKSLASH_IN_EXPRESSION;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_CLOSING_PAR_DOES_NOT_MATCH;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_EXPECTING_CLOSING_BRACE;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_HASH_IN_EXPRESSION;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_INVALID_CONVERSION;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_SINGLE_BRACE;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_TOO_MANY_NESTED_PARS;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_UNMATCHED_PAR;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_UNTERMINATED_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_MESSAGE_NESTED_TOO_DEEPLY;

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.parser.PythonSSTNodeFactory;
import com.oracle.graal.python.parser.PythonSSTNodeFactory.FStringExprParser;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.truffle.api.source.Source;
import java.util.Arrays;
import com.oracle.truffle.api.strings.TruffleString;

public final class FormatStringParser {

    // token types and Token data holder (public for testing purposes)
    public static final byte TOKEN_TYPE_STRING = 1;
    public static final byte TOKEN_TYPE_EXPRESSION = 2;
    public static final byte TOKEN_TYPE_EXPRESSION_STR = 3;
    public static final byte TOKEN_TYPE_EXPRESSION_REPR = 4;
    public static final byte TOKEN_TYPE_EXPRESSION_ASCII = 5;

    public static final class Token {
        public byte type;
        /**
         * Start and end index within the parsed f-string.
         */
        public final int startIndex;
        public final int endIndex;
        /**
         * Count how many tokens follow as tokens of format specifier. So the next expression or
         * string is not the next token, but the next token + the value under this index. Value is
         * useful/defined only for expression tokens.
         */
        public int formatTokensCount;

        public Token(byte type, int startIndex, int endIndex) {
            this.type = type;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Token(");
            sb.append("Type: ").append(type);
            sb.append(", [").append(startIndex).append(", ").append(endIndex).append("], Inner tokens: ");
            sb.append(formatTokensCount).append(')');
            return sb.toString();
        }
    }

    private static SSTNode createFormatStringLiteralSSTNodeFromToken(ArrayList<Token> tokens, int tokenIndex, String text, boolean isRawString, int partOffsetInSource, int textOffsetInSource,
                    Source source, PythonSSTNodeFactory nodeFactory, ParserErrorCallback errorCallback, FStringExprParser exprParser) {
        Token token = tokens.get(tokenIndex);
        String code = text.substring(token.startIndex, token.endIndex);
        if (token.type == TOKEN_TYPE_STRING) {
            if (!isRawString) {
                code = StringUtils.unescapeString(textOffsetInSource, textOffsetInSource + code.length(), source, errorCallback, code);
            }
            return new StringLiteralSSTNode.RawStringLiteralSSTNode(code, textOffsetInSource + token.startIndex, textOffsetInSource + token.endIndex);
        }
        int specTokensCount = token.formatTokensCount;
        // the expression has to be wrapped in ()
        code = "(" + code + ")";
        SSTNode expression = exprParser.parseExpression(errorCallback, code, nodeFactory, source.isInteractive());
        SSTNode specifier = null;
        if (specTokensCount > 0) {
            SSTNode[] specifierParts = new SSTNode[specTokensCount];
            int specifierTokenStartIndex = tokenIndex + 1;
            Token specToken = tokens.get(specifierTokenStartIndex);
            int specifierStartOffset = textOffsetInSource + tokens.get(specifierTokenStartIndex).startIndex - ((specToken != null && specToken.type != TOKEN_TYPE_STRING) ? 1 : 0);
            int realCount = 0;
            int i;

            for (i = 0; i < specTokensCount; i++) {
                specToken = tokens.get(specifierTokenStartIndex + i);
                specifierParts[realCount++] = createFormatStringLiteralSSTNodeFromToken(tokens, specifierTokenStartIndex + i, text, isRawString, partOffsetInSource, textOffsetInSource, source,
                                nodeFactory, errorCallback, exprParser);
                i = i + specToken.formatTokensCount;
            }

            if (realCount < i) {
                specifierParts = Arrays.copyOf(specifierParts, realCount);
            }

            int specifierEndOffset = textOffsetInSource;
            if (specToken != null) {
                specifierEndOffset = textOffsetInSource + specToken.endIndex + (specToken.type != TOKEN_TYPE_STRING ? 1 : 0);
            }
            specifier = new StringLiteralSSTNode.FormatStringLiteralSSTNode(specifierParts, specifierStartOffset, specifierEndOffset);
        }
        StringLiteralSSTNode.FormatStringConversionType conversionType;
        switch (token.type) {
            case TOKEN_TYPE_EXPRESSION_STR:
                conversionType = StringLiteralSSTNode.FormatStringConversionType.STR_CONVERTION;
                break;
            case TOKEN_TYPE_EXPRESSION_REPR:
                conversionType = StringLiteralSSTNode.FormatStringConversionType.REPR_CONVERSION;
                break;
            case TOKEN_TYPE_EXPRESSION_ASCII:
                conversionType = StringLiteralSSTNode.FormatStringConversionType.ASCII_CONVERSION;
                break;
            default:
                conversionType = StringLiteralSSTNode.FormatStringConversionType.NO_CONVERSION;
        }
        int endOffset = specifier == null ? textOffsetInSource + token.endIndex : specifier.endOffset;
        if (conversionType != StringLiteralSSTNode.FormatStringConversionType.NO_CONVERSION) {
            endOffset = endOffset + 2;
        }
        return new StringLiteralSSTNode.FormatExpressionSSTNode(code, expression, specifier, conversionType, textOffsetInSource + token.startIndex, endOffset);
    }

    /**
     * Parses f-string into an array of SSTNodes. The nodes can be RawStringLiteralSSTNode or
     * FormatExpressionSSTNode.
     */
    public static void parse(ArrayList<SSTNode> formatStringParts, String text, boolean isRawString, int partOffsetInSource, int textOffsetInSource, Source source, FStringExprParser exprParser,
                    ParserErrorCallback errorCallback, PythonSSTNodeFactory nodeFactory) {
        int estimatedTokensCount = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '}' || c == ':') {
                estimatedTokensCount++;
                if (estimatedTokensCount > 32) {
                    // don't get too crazy...
                    break;
                }
            }
        }

        // create tokens
        ArrayList<Token> tokens = new ArrayList<>(estimatedTokensCount);
        createTokens(tokens, errorCallback, 0, text, isRawString, 0);

        // create nodes from the tokens
        int tokenIndex = 0;
        Token token;

        while (tokenIndex < tokens.size()) {
            token = tokens.get(tokenIndex);
            SSTNode part = createFormatStringLiteralSSTNodeFromToken(tokens, tokenIndex, text, isRawString, partOffsetInSource, textOffsetInSource, source, nodeFactory, errorCallback, exprParser);
            formatStringParts.add(part);
            tokenIndex = tokenIndex + 1 + token.formatTokensCount;
        }

    }

    // parsing the format string
    private static final int STATE_TEXT = 1;  // in text
    private static final int STATE_AFTER_OPEN_BRACE = 2; // just after {
    private static final int STATE_AFTER_CLOSE_BRACE = 3; // just after }
    private static final int STATE_AFTER_EXCLAMATION = 4; // just after !
    private static final int STATE_AFTER_COLON = 5; // just after :
    private static final int STATE_EXPRESSION = 6; // in {}
    private static final int STATE_UNKNOWN = 7;

    private static final int MAX_PAR_NESTING = 200;

    /**
     * This is the parser of the fstring.
     *
     * @param tokens list where the parsed tokens will be stored
     * @param errorCallback it's needed for raising syntax errors
     * @param startIndex start parsing from this index
     * @param text text to be parsed
     * @param isRawString whether the String is raw, i.e., escape sequences should be interpreted as
     *            a verbatim text
     * @param recursionLevel recursive calls are used for parsing the formatting string, which may
     *            contain other expressions. Depending on the recursive level some rules apply
     *            differently.
     * @return the index of the last processed character
     */
    public static int createTokens(ArrayList<Token> tokens, ParserErrorCallback errorCallback, int startIndex, String text, boolean isRawString, int recursionLevel) {
        int index;
        int state = STATE_TEXT;
        int start = 0;

        int braceLevel = 0;
        int braceLevelInExpression = 0;
        char[] bracesInExpression = new char[MAX_PAR_NESTING];
        int len = text.length();
        index = startIndex;
        start = startIndex;
        boolean toplevel = recursionLevel == 0;
        // currentExpression is set by '=' or '!' handlers, which create the expression token, and
        // is read by the ':', which either needs to create the expression token itself or should
        // reuse the created by '=' or '!' if preceded by '=' or '!', e.g., f'{expr!s:10<}'
        Token currentExpression = null;
        parserLoop: while (index < len) {
            char ch = text.charAt(index);
            switch (state) {
                case STATE_TEXT:
                    switch (ch) {
                        case '\\':
                            if (isRawString) {
                                break;
                            }
                            if (lookahead(text, index, len, '\\')) {
                                // double "\\" is skipped, note that "\\\N{...}" should still be
                                // treated as \N escape sequence
                                index++;
                            } else if (lookahead(text, index, len, '{')) {
                                StringUtils.warnInvalidEscapeSequence(errorCallback, text.charAt(index + 1));
                            } else if (lookahead(text, index, len, 'N', '{')) {
                                // skip escape sequence \N{...}, it should not be treated as an
                                // expression inside f-string, but \\N{...} should be left intact
                                index += 2;
                                while (index < len && text.charAt(index) != '}') {
                                    index++;
                                }
                                if (index >= len) {
                                    // Missing the closing brace. The escape sequence is malformed,
                                    // which will be reported by the String escaping code later,
                                    // here we just end the parsing
                                    index = len;
                                    break parserLoop;
                                }
                            }
                            break;
                        case '{':
                            if (start < index) {
                                tokens.add(new Token(TOKEN_TYPE_STRING, start, index));
                            }
                            state = STATE_AFTER_OPEN_BRACE;
                            start = index + 1;
                            braceLevel++;
                            break;
                        case '}':
                            braceLevel--;
                            if (braceLevel == -1) {
                                if (!toplevel) {
                                    // We are parsing a format specifier (nested f-string) and here
                                    // we reached the closing brace of the top-level f-string, i.e.,
                                    // the end of the nested f-string too
                                    break parserLoop;
                                }
                            }
                            state = STATE_AFTER_CLOSE_BRACE;
                            break;
                    }
                    break;
                case STATE_AFTER_OPEN_BRACE:
                    if (ch == '}' || ch == '=') {
                        throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EMPTY_EXPRESSION);
                    }
                    if (ch == '{' && toplevel) {
                        // '{' escaping works only when parsing the expression, not when parsing the
                        // format (i.e., when we are in the recursive call)
                        state = STATE_TEXT;
                        braceLevel--;
                    } else if (recursionLevel == 2) {
                        // we are inside formatting expression of another formatting expression,
                        // example: f'{42:{42:{42}}}'. This level of nesting is not allowed.
                        throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_NESTED_TOO_DEEPLY);
                    } else {
                        index--;
                        state = STATE_EXPRESSION;
                        braceLevelInExpression = 0;
                        currentExpression = null;
                    }
                    break;
                case STATE_AFTER_CLOSE_BRACE:
                    if (toplevel && ch == '}') {
                        // after '}' should in this moment follow second '}', only allowed when
                        // parsing the expression, not when parsing the format
                        if (start < index) {
                            tokens.add(new Token(TOKEN_TYPE_STRING, start, index));
                        }
                        braceLevel++;
                        if (braceLevel == 0) {
                            state = STATE_TEXT;
                        }
                        start = index + 1;
                    } else {
                        throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_SINGLE_BRACE);
                    }
                    break;
                case STATE_EXPRESSION:
                    if (index + 1 < len) {
                        // Some patterns of two characters, such as '!=', should be skipped
                        if ((ch == '!' || ch == '<' || ch == '>' || ch == '=') && (text.charAt(index + 1) == '=')) {
                            index += 2;
                            continue;
                        }
                    }
                    switch (ch) {
                        case '{':
                        case '(':
                        case '[':
                            bracesInExpression[braceLevelInExpression] = ch;
                            braceLevelInExpression++;
                            if (braceLevelInExpression >= MAX_PAR_NESTING) {
                                throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_TOO_MANY_NESTED_PARS);
                            }
                            break;
                        case ')':
                        case ']':
                            if (braceLevelInExpression == 0) {
                                throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_UNMATCHED_PAR, ch);
                            }
                            braceLevelInExpression--;
                            char expected = ch == ')' ? '(' : '[';
                            if (bracesInExpression[braceLevelInExpression] != expected) {
                                throw raiseUnmatchingClosingPar(errorCallback, bracesInExpression[braceLevelInExpression], ch);
                            }
                            break;
                        case '}':
                            if (braceLevelInExpression == 0) {
                                tokens.add(createExpressionToken(errorCallback, text, start, index));
                                braceLevel--;
                                state = STATE_TEXT;
                                start = index + 1;
                            } else {
                                braceLevelInExpression--;
                                if (bracesInExpression[braceLevelInExpression] != '{') {
                                    throw raiseUnmatchingClosingPar(errorCallback, bracesInExpression[braceLevelInExpression], '}');
                                }
                            }
                            break;
                        case '=':
                            if (braceLevelInExpression == 0) {
                                // The "=" mode, e.g., f'{1+1=}' produces "1+1=2"
                                // Python allows '=' to be followed by whitespace, but nothing else
                                // "=" inside format specification
                                int expressionEndIndex = index;
                                index++;
                                while (index < len && Character.isWhitespace(text.charAt(index))) {
                                    index++;
                                }

                                // Have we reached a legal end character of an expression?
                                if (index >= len) {
                                    throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                                }
                                char endChar = text.charAt(index);
                                if (endChar != '}' && endChar != ':' && endChar != '!') {
                                    throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                                }

                                // add verbatim text of the expression (including the "=" and any
                                // spaces after it) and the expression itself
                                tokens.add(new Token(TOKEN_TYPE_STRING, start, index));
                                currentExpression = createExpressionToken(errorCallback, text, start, expressionEndIndex);
                                tokens.add(currentExpression);
                                if (endChar == '}') {
                                    // "debug" expressions are by default converted using "repr",
                                    // but as long as there is no format
                                    currentExpression.type = TOKEN_TYPE_EXPRESSION_REPR;
                                    // we're done with the expression
                                    braceLevel--;
                                    state = STATE_TEXT;
                                    start = index + 1;
                                    currentExpression = null;
                                } else if (endChar == '!') {
                                    // parse the format specifier, this state expects to see the
                                    // expression token in currentExpression
                                    state = STATE_AFTER_EXCLAMATION;
                                } else {
                                    // endChar must be ':'
                                    // parse ':' again, the ':' handler checks the currentExpression
                                    start = index;
                                    state = STATE_AFTER_COLON;
                                }
                            }
                            break;
                        case '\'':
                        case '"':
                            index = skipString(errorCallback, text, index, len, ch);
                            break;
                        case '!':
                            state = STATE_AFTER_EXCLAMATION;
                            currentExpression = createExpressionToken(errorCallback, text, start, index);
                            tokens.add(currentExpression);
                            break;
                        case ':':
                            if (braceLevelInExpression == 0) {
                                currentExpression = createExpressionToken(errorCallback, text, start, index);
                                tokens.add(currentExpression);
                                state = STATE_AFTER_COLON;
                            }
                            break;
                        case '#':
                            throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_HASH_IN_EXPRESSION);
                        case '\\':
                            throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_BACKSLASH_IN_EXPRESSION);
                        default:
                            break;
                    }
                    break;
                case STATE_AFTER_EXCLAMATION:
                    assert currentExpression != null;
                    switch (ch) {
                        case 's':
                            currentExpression.type = TOKEN_TYPE_EXPRESSION_STR;
                            break;
                        case 'r':
                            currentExpression.type = TOKEN_TYPE_EXPRESSION_REPR;
                            break;
                        case 'a':
                            currentExpression.type = TOKEN_TYPE_EXPRESSION_ASCII;
                            break;
                        default:
                            throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_INVALID_CONVERSION);
                    }
                    start = index + 2;
                    index++;
                    char next = index < len ? text.charAt(index) : Character.MAX_VALUE;
                    switch (next) {
                        case ':':
                            state = STATE_AFTER_COLON;
                            break;
                        case '}':
                            // We're done with the expression
                            state = STATE_TEXT;
                            braceLevel--;
                            break;
                        default:
                            throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                    }
                    break;
                case STATE_AFTER_COLON:
                    assert currentExpression != null;
                    int tokensSizeBefore = tokens.size();
                    index = createTokens(tokens, errorCallback, index, text, isRawString, recursionLevel + 1);
                    currentExpression.formatTokensCount = tokens.size() - tokensSizeBefore;
                    if (index >= len || text.charAt(index) != '}') {
                        throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                    }
                    braceLevel--;
                    state = STATE_TEXT;
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
                    tokens.add(new Token(TOKEN_TYPE_STRING, start, index));
                }
                break;
            case STATE_AFTER_CLOSE_BRACE:
                throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_SINGLE_BRACE);
            case STATE_AFTER_EXCLAMATION:
            case STATE_AFTER_OPEN_BRACE:
            case STATE_AFTER_COLON:
                throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
            case STATE_EXPRESSION:
                // expression is not allowed to span multiple f-strings: f'{3+' f'1}' is not
                // the same as f'{3+1}'
                throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
        }
        return index;
    }

    private static boolean lookahead(String text, int index, int len, char c1) {
        return index + 1 < len && text.charAt(index + 1) == c1;
    }

    private static boolean lookahead(String text, int index, int len, char c1, char c2) {
        return index + 2 < len && text.charAt(index + 1) == c1 && text.charAt(index + 2) == c2;
    }

    /**
     * Skips a string literal. Checks for all the valid quotation styles.
     */
    private static int skipString(ParserErrorCallback errorCallback, String text, int startIndex, int len, char startq) {
        boolean triple = false;
        boolean inString = true;
        int index = startIndex + 1;
        if (index < len && startq == text.charAt(index)) {
            if (lookahead(text, index, len, startq)) {
                // we are in ''' or """ string, fully consume the quotes
                triple = true;
                index += 2;
            } else {
                // we are in empty string "" or ''
                inString = false;
            }
        }
        if (inString) {
            while (index < len) {
                char ch = text.charAt(index);
                if (ch == '\\') {
                    throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_BACKSLASH_IN_EXPRESSION);
                }
                if (ch == startq) {
                    if (triple) {
                        // single quote should be ignored in a triple quoted string
                        if (lookahead(text, index, len, startq, startq)) {
                            inString = false;
                            break;
                        }
                    } else {
                        inString = false;
                        break;
                    }
                }
                index++;
            }
            if (inString) {
                throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_UNTERMINATED_STRING);
            }
        }
        return index;
    }

    private static Token createExpressionToken(ParserErrorCallback errorCallback, String text, int start, int end) {
        if (start >= end) {
            throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EMPTY_EXPRESSION);
        }
        boolean onlyWhiteSpaces = true;
        for (int index = start; index < end; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                onlyWhiteSpaces = false;
                break;
            }
        }
        if (onlyWhiteSpaces) {
            throw raiseInvalidSyntax(errorCallback, ERROR_MESSAGE_EMPTY_EXPRESSION);
        }
        return new Token(TOKEN_TYPE_EXPRESSION, start, end);
    }

    private static RuntimeException raiseUnmatchingClosingPar(ParserErrorCallback errorCallback, char opening, char closing) {
        Source source = Source.newBuilder(PythonLanguage.ID, "unknown", "<fstring>").build();
        throw errorCallback.raiseInvalidSyntax(source, source.createUnavailableSection(), ERROR_MESSAGE_CLOSING_PAR_DOES_NOT_MATCH, closing, opening);
    }

    private static RuntimeException raiseInvalidSyntax(ParserErrorCallback errorCallback, TruffleString message) {
        Source source = Source.newBuilder(PythonLanguage.ID, "unknown", "<fstring>").build();
        throw errorCallback.raiseInvalidSyntax(source, source.createUnavailableSection(), message);
    }

    private static RuntimeException raiseInvalidSyntax(ParserErrorCallback errorCallback, TruffleString message, Object... args) {
        Source source = Source.newBuilder(PythonLanguage.ID, "unknown", "<fstring>").build();
        throw errorCallback.raiseInvalidSyntax(source, source.createUnavailableSection(), message, args);
    }
}
