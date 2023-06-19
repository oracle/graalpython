/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.pegparser.sst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.ErrorCallback.WarningType;
import com.oracle.graal.python.pegparser.FExprParser;
import com.oracle.graal.python.pegparser.PythonStringFactory;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public abstract class StringLiteralUtils {
    private static final ExprTy[] EMPTY_SST_ARRAY = new ExprTy[0];

    private static final String CANNOT_MIX_MESSAGE = "cannot mix bytes and nonbytes literals";

    private static class BytesBuilder {
        List<byte[]> bytes = new ArrayList<>();
        int len = 0;

        void append(byte[] b) {
            len += b.length;
            bytes.add(b);
        }

        byte[] build() {
            byte[] output = new byte[len];
            int offset = 0;
            for (byte[] bs : bytes) {
                System.arraycopy(bs, 0, output, offset, bs.length);
                offset += bs.length;
            }
            return output;
        }
    }

    public static <T> ExprTy createStringLiteral(String[] values, SourceRange[] sourceRanges, FExprParser exprParser, ErrorCallback errorCallback, PythonStringFactory<T> stringFactory,
                    int featureVersion) {
        assert values.length == sourceRanges.length && values.length > 0;
        PythonStringFactory.PythonStringBuilder<T> sb = null;
        BytesBuilder bb = null;
        boolean isFormatString = false;
        ArrayList<ExprTy> formatStringParts = null;
        SourceRange sourceRange = sourceRanges[0].withEnd(sourceRanges[sourceRanges.length - 1]);
        SourceRange sbSourceRange = null;
        boolean hasUPrefix = false;
        for (int index = 0; index < values.length; ++index) {
            String text = values[index];
            boolean isRaw = false;
            boolean isBytes = false;
            boolean isFormat = false;

            int strStartIndex = 1;
            int strEndIndex = text.length() - 1;

            OUTER: for (int i = 0; i < 3; i++) {
                char chr = Character.toLowerCase(text.charAt(i));
                switch (chr) {
                    case 'r':
                        isRaw = true;
                        break;
                    // unicode case (default)
                    case 'u':
                        hasUPrefix = index == 0;
                        break;
                    case 'b':
                        isBytes = true;
                        break;
                    case 'f':
                        isFormat = true;
                        break;
                    case '\'':
                    case '"':
                        strStartIndex = i + 1;
                        break OUTER;
                    default:
                        break;
                }
            }

            if (isFormat && featureVersion < 6) {
                errorCallback.onError(ErrorCallback.ErrorType.Syntax, sourceRange, "Format strings are only supported in Python 3.6 and greater");
            }

            if (text.endsWith("'''") || text.endsWith("\"\"\"")) {
                strStartIndex += 2;
                strEndIndex -= 2;
            }

            text = text.substring(strStartIndex, strEndIndex);
            if (isBytes) {
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) >= 0x80) {
                        errorCallback.onError(ErrorCallback.ErrorType.Syntax, sourceRange, "bytes can only contain ASCII literal characters");
                    }
                }
                if (sb != null || isFormatString) {
                    errorCallback.onError(sourceRange, CANNOT_MIX_MESSAGE);
                    if (sb != null) {
                        return new ExprTy.Constant(ConstantValue.ofRaw(sb.build()), hasUPrefix ? "u" : null, sourceRange);
                    } else if (bb != null) {
                        return new ExprTy.Constant(ConstantValue.ofBytes(bb.build()), null, sourceRange);
                    } else {
                        return new ExprTy.Constant(ConstantValue.ofBytes(text.getBytes()), null, sourceRange);
                    }
                }
                if (bb == null) {
                    bb = new BytesBuilder();
                }
                if (isRaw) {
                    bb.append(text.getBytes());
                } else {
                    bb.append(decodeEscapeToBytes(errorCallback, text, sourceRange));
                }
            } else {
                if (bb != null) {
                    errorCallback.onError(sourceRange, CANNOT_MIX_MESSAGE);
                    return new ExprTy.Constant(ConstantValue.ofBytes(bb.build()), null, sourceRange);
                }
                if (isFormat) {
                    isFormatString = true;
                    if (formatStringParts == null) {
                        formatStringParts = new ArrayList<>();
                    }
                    if (sb != null && !sb.isEmpty()) {
                        formatStringParts.add(new ExprTy.Constant(ConstantValue.ofRaw(sb.build()), hasUPrefix ? "u" : null, sbSourceRange));
                        sb = null;
                    }

                    FormatStringParser.parse(formatStringParts, text, sourceRanges[index].shiftStartRight(strStartIndex), isRaw, exprParser, errorCallback, stringFactory, featureVersion, hasUPrefix);
                } else {
                    if (sb == null) {
                        sb = stringFactory.createBuilder(text.length());
                        sbSourceRange = sourceRanges[index];
                    }
                    if (!isRaw) {
                        sb.appendPythonString(unescapeString(sourceRanges[index], errorCallback, text, stringFactory));
                    } else {
                        sb.appendString(text);
                    }
                    sbSourceRange = sbSourceRange.withEnd(sourceRanges[index]);
                }
            }
        }

        if (bb != null) {
            return new ExprTy.Constant(ConstantValue.ofBytes(bb.build()), null, sourceRange);
        } else if (isFormatString) {
            assert formatStringParts != null; // guaranteed due to how isFormatString is set
            if (sb != null && !sb.isEmpty()) {
                formatStringParts.add(new ExprTy.Constant(ConstantValue.ofRaw(sb.build()), hasUPrefix ? "u" : null, sbSourceRange));
            }
            ExprTy[] formatParts = formatStringParts.toArray(EMPTY_SST_ARRAY);
            return new ExprTy.JoinedStr(formatParts, sourceRange);
        }
        return new ExprTy.Constant(ConstantValue.ofRaw(sb == null ? stringFactory.emptyString() : sb.build()), hasUPrefix ? "u" : null, sourceRange);
    }

    private static final class FormatStringParser {
        // error messages from parsing
        public static final String ERROR_MESSAGE_EMPTY_EXPRESSION = "f-string: empty expression not allowed";
        public static final String ERROR_MESSAGE_NESTED_TOO_DEEPLY = "f-string: expressions nested too deeply";
        public static final String ERROR_MESSAGE_SINGLE_BRACE = "f-string: single '}' is not allowed";
        public static final String ERROR_MESSAGE_INVALID_CONVERSION = "f-string: invalid conversion character: expected 's', 'r', or 'a'";
        public static final String ERROR_MESSAGE_UNTERMINATED_STRING = "f-string: unterminated string";
        public static final String ERROR_MESSAGE_BACKSLASH_IN_EXPRESSION = "f-string expression part cannot include a backslash";
        public static final String ERROR_MESSAGE_HASH_IN_EXPRESSION = "f-string expression part cannot include '#'";
        public static final String ERROR_MESSAGE_CLOSING_PAR_DOES_NOT_MATCH = "f-string: closing parenthesis '%c' does not match opening parenthesis '%c'";
        public static final String ERROR_MESSAGE_UNMATCHED_PAR = "f-string: unmatched '%c'";
        public static final String ERROR_MESSAGE_TOO_MANY_NESTED_PARS = "f-string: too many nested parenthesis";
        public static final String ERROR_MESSAGE_EXPECTING_CLOSING_BRACE = "f-string: expecting '}'";
        public static final String ERROR_MESSAGE_SELF_DOCUMENTING_EXPRESSION = "f-string: self documenting expressions are only supported in Python 3.8 and greater";

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
             * string is not the next token, but the next token + the value under this index. Value
             * is useful/defined only for expression tokens.
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

            SourceRange getSourceRange(String text, SourceRange textSourceRange) {
                int column = textSourceRange.startColumn;
                int line = textSourceRange.startLine;
                for (int i = 0; i < startIndex; ++i) {
                    if (text.charAt(i) == '\n') {
                        line++;
                        column = 0;
                    } else {
                        column++;
                    }
                }
                int startColumn = column;
                int startLine = line;
                for (int i = startIndex; i < endIndex; ++i) {
                    if (text.charAt(i) == '\n') {
                        line++;
                        column = 0;
                    } else {
                        column++;
                    }
                }
                return new SourceRange(startLine, startColumn, line, column);
            }
        }

        private static ExprTy createFormatStringLiteralSSTNodeFromToken(ArrayList<Token> tokens, int tokenIndex, String text, SourceRange textSourceRange, boolean isRawString,
                        ErrorCallback errorCallback, FExprParser exprParser, PythonStringFactory<?> stringFactory, boolean hasUPrefix) {
            Token token = tokens.get(tokenIndex);
            String code = text.substring(token.startIndex, token.endIndex);
            if (token.type == TOKEN_TYPE_STRING) {
                Object o;
                if (!isRawString) {
                    o = unescapeString(token.getSourceRange(text, textSourceRange), errorCallback, code, stringFactory);
                } else {
                    o = stringFactory.fromJavaString(code);
                }
                return new ExprTy.Constant(ConstantValue.ofRaw(o), hasUPrefix ? "u" : null, token.getSourceRange(text, textSourceRange));
            }
            int specTokensCount = token.formatTokensCount;
            // the expression has to be wrapped in ()
            code = "(" + code + ")";
            // TODO: pass isInteractive flag
            ExprTy expression = exprParser.parse(code, token.getSourceRange(text, textSourceRange));
            if (expression == null) {
                // TODO handle and raise appropriate error msg
                errorCallback.onError(ErrorCallback.ErrorType.Syntax, textSourceRange, "invalid syntax");
                return null;
            }
            ExprTy specifier = null;
            SourceRange specifierSourceRange = null;
            if (specTokensCount > 0) {
                ExprTy[] specifierParts = new ExprTy[specTokensCount];
                int specifierTokenStartIndex = tokenIndex + 1;
                Token specToken = tokens.get(specifierTokenStartIndex);
                specifierSourceRange = specToken.getSourceRange(text, textSourceRange);
                int realCount = 0;
                int i;

                for (i = 0; i < specTokensCount; i++) {
                    specToken = tokens.get(specifierTokenStartIndex + i);
                    specifierParts[realCount++] = createFormatStringLiteralSSTNodeFromToken(tokens, specifierTokenStartIndex + i, text, textSourceRange, isRawString, errorCallback, exprParser,
                                    stringFactory, hasUPrefix);
                    i = i + specToken.formatTokensCount;
                }

                if (realCount < i) {
                    specifierParts = Arrays.copyOf(specifierParts, realCount);
                }

                specifierSourceRange = specifierSourceRange.withEnd(specToken.getSourceRange(text, textSourceRange));
                specifier = new ExprTy.JoinedStr(specifierParts, specifierSourceRange);
            }
            int conversionType;
            switch (token.type) {
                case TOKEN_TYPE_EXPRESSION_STR:
                    conversionType = 's';
                    break;
                case TOKEN_TYPE_EXPRESSION_REPR:
                    conversionType = 'r';
                    break;
                case TOKEN_TYPE_EXPRESSION_ASCII:
                    conversionType = 'a';
                    break;
                default:
                    conversionType = -1;
            }
            SourceRange range = token.getSourceRange(text, textSourceRange);
            if (specifierSourceRange != null) {
                range = range.withEnd(specifierSourceRange);
            }
            if (conversionType != -1) {
                range = range.shiftEndRight(2);
            }
            return new ExprTy.FormattedValue(expression, conversionType, specifier, range);
        }

        /**
         * Parses f-string into an array of {@link ExprTy}. The nodes can end up being
         * {@link ExprTy.Constant} or {@link ExprTy.FormattedValue}.
         */
        public static void parse(ArrayList<ExprTy> formatStringParts, String text, SourceRange textSourceRange, boolean isRawString, FExprParser exprParser, ErrorCallback errorCallback,
                        PythonStringFactory<?> stringFactory, int featureVersion, boolean hasUPrefix) {
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
            if (createTokens(tokens, errorCallback, 0, text, textSourceRange, isRawString, 0, featureVersion) < 0) {
                return;
            }

            // create nodes from the tokens
            int tokenIndex = 0;
            Token token;

            while (tokenIndex < tokens.size()) {
                token = tokens.get(tokenIndex);
                ExprTy part = createFormatStringLiteralSSTNodeFromToken(tokens, tokenIndex, text, textSourceRange, isRawString, errorCallback, exprParser, stringFactory, hasUPrefix);
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
         * @param isRawString whether the String is raw, i.e., escape sequences should be
         *            interpreted as a verbatim text
         * @param recursionLevel recursive calls are used for parsing the formatting string, which
         *            may contain other expressions. Depending on the recursive level some rules
         *            apply differently.
         * @return the index of the last processed character or {@code -1} on error
         */
        public static int createTokens(ArrayList<Token> tokens, ErrorCallback errorCallback, int startIndex, String text, SourceRange textSourceRange, boolean isRawString, int recursionLevel,
                        int featureVersion) {
            int state = STATE_TEXT;

            int braceLevel = 0;
            int braceLevelInExpression = 0;
            char[] bracesInExpression = new char[MAX_PAR_NESTING];
            int len = text.length();
            int index = startIndex;
            int start = startIndex;
            boolean toplevel = recursionLevel == 0;
            // currentExpression is set by '=' or '!' handlers, which create the expression token,
            // and
            // is read by the ':', which either needs to create the expression token itself or
            // should
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
                                    warnInvalidEscapeSequence(errorCallback, textSourceRange, text.charAt(index + 1));
                                } else if (lookahead(text, index, len, 'N', '{')) {
                                    // skip escape sequence \N{...}, it should not be treated as an
                                    // expression inside f-string, but \\N{...} should be left
                                    // intact
                                    index += 2;
                                    while (index < len && text.charAt(index) != '}') {
                                        index++;
                                    }
                                    if (index >= len) {
                                        // Missing the closing brace. The escape sequence is
                                        // malformed,
                                        // which will be reported by the String escaping code later,
                                        // here we just end the parsing
                                        index = len - 1;
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
                                        // We are parsing a format specifier (nested f-string) and
                                        // here
                                        // we reached the closing brace of the top-level f-string,
                                        // i.e.,
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
                            errorCallback.onError(textSourceRange, ERROR_MESSAGE_EMPTY_EXPRESSION);
                            return -1;
                        }
                        if (ch == '{' && toplevel) {
                            // '{' escaping works only when parsing the expression, not when parsing
                            // the
                            // format (i.e., when we are in the recursive call)
                            state = STATE_TEXT;
                            braceLevel--;
                        } else if (recursionLevel == 2) {
                            // we are inside formatting expression of another formatting expression,
                            // example: f'{42:{42:{42}}}'. This level of nesting is not allowed.
                            errorCallback.onError(textSourceRange, ERROR_MESSAGE_NESTED_TOO_DEEPLY);
                            return -1;
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
                            errorCallback.onError(textSourceRange, ERROR_MESSAGE_SINGLE_BRACE);
                            return -1;
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
                                    errorCallback.onError(textSourceRange, ERROR_MESSAGE_TOO_MANY_NESTED_PARS);
                                    return -1;
                                }
                                break;
                            case ')':
                            case ']':
                                if (braceLevelInExpression == 0) {
                                    errorCallback.onError(textSourceRange, ERROR_MESSAGE_UNMATCHED_PAR, ch);
                                    return -1;
                                }
                                braceLevelInExpression--;
                                char expected = ch == ')' ? '(' : '[';
                                if (bracesInExpression[braceLevelInExpression] != expected) {
                                    errorCallback.onError(textSourceRange, ERROR_MESSAGE_CLOSING_PAR_DOES_NOT_MATCH, ch, bracesInExpression[braceLevelInExpression]);
                                    return -1;
                                }
                                break;
                            case '}':
                                if (braceLevelInExpression == 0) {
                                    Token t = createExpressionToken(errorCallback, text, textSourceRange, start, index);
                                    if (t == null) {
                                        return -1;
                                    }
                                    tokens.add(t);
                                    braceLevel--;
                                    state = STATE_TEXT;
                                    start = index + 1;
                                } else {
                                    braceLevelInExpression--;
                                    if (bracesInExpression[braceLevelInExpression] != '{') {
                                        errorCallback.onError(textSourceRange, ERROR_MESSAGE_CLOSING_PAR_DOES_NOT_MATCH, '}', bracesInExpression[braceLevelInExpression]);
                                        return -1;
                                    }
                                }
                                break;
                            case '=':
                                if (braceLevelInExpression == 0) {
                                    if (featureVersion < 8) {
                                        errorCallback.onError(textSourceRange, ERROR_MESSAGE_SELF_DOCUMENTING_EXPRESSION);
                                        return -1;
                                    }
                                    // The "=" mode, e.g., f'{1+1=}' produces "1+1=2"
                                    // Python allows '=' to be followed by whitespace, but nothing
                                    // else
                                    // "=" inside format specification
                                    int expressionEndIndex = index;
                                    index++;
                                    while (index < len && Character.isWhitespace(text.charAt(index))) {
                                        index++;
                                    }

                                    // Have we reached a legal end character of an expression?
                                    if (index >= len) {
                                        errorCallback.onError(textSourceRange, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                                        return -1;
                                    }
                                    char endChar = text.charAt(index);
                                    if (endChar != '}' && endChar != ':' && endChar != '!') {
                                        errorCallback.onError(textSourceRange, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                                        return -1;
                                    }

                                    // add verbatim text of the expression (including the "=" and
                                    // any
                                    // spaces after it) and the expression itself
                                    tokens.add(new Token(TOKEN_TYPE_STRING, start, index));
                                    currentExpression = createExpressionToken(errorCallback, text, textSourceRange, start, expressionEndIndex);
                                    if (currentExpression == null) {
                                        return -1;
                                    }
                                    tokens.add(currentExpression);
                                    if (endChar == '}') {
                                        // "debug" expressions are by default converted using
                                        // "repr",
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
                                        // parse ':' again, the ':' handler checks the
                                        // currentExpression
                                        start = index;
                                        state = STATE_AFTER_COLON;
                                    }
                                }
                                break;
                            case '\'':
                            case '"':
                                index = skipString(errorCallback, text, textSourceRange, index, len, ch);
                                if (index < 0) {
                                    return index;
                                }
                                break;
                            case '!':
                                state = STATE_AFTER_EXCLAMATION;
                                currentExpression = createExpressionToken(errorCallback, text, textSourceRange, start, index);
                                if (currentExpression == null) {
                                    return -1;
                                }
                                tokens.add(currentExpression);
                                break;
                            case ':':
                                if (braceLevelInExpression == 0) {
                                    currentExpression = createExpressionToken(errorCallback, text, textSourceRange, start, index);
                                    if (currentExpression == null) {
                                        return -1;
                                    }
                                    tokens.add(currentExpression);
                                    state = STATE_AFTER_COLON;
                                }
                                break;
                            case '#':
                                errorCallback.onError(textSourceRange, ERROR_MESSAGE_HASH_IN_EXPRESSION);
                                return -1;
                            case '\\':
                                errorCallback.onError(textSourceRange, ERROR_MESSAGE_BACKSLASH_IN_EXPRESSION);
                                return -1;
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
                                errorCallback.onError(textSourceRange, ERROR_MESSAGE_INVALID_CONVERSION);
                                return -1;
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
                                errorCallback.onError(textSourceRange, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                                return -1;
                        }
                        break;
                    case STATE_AFTER_COLON:
                        assert currentExpression != null;
                        int tokensSizeBefore = tokens.size();
                        index = createTokens(tokens, errorCallback, index, text, textSourceRange, isRawString, recursionLevel + 1, featureVersion);
                        if (index < 0) {
                            return -1;
                        }
                        currentExpression.formatTokensCount = tokens.size() - tokensSizeBefore;
                        if (index >= len || text.charAt(index) != '}') {
                            errorCallback.onError(textSourceRange, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                            return -1;
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
                    errorCallback.onError(textSourceRange, ERROR_MESSAGE_SINGLE_BRACE);
                    return -1;
                case STATE_AFTER_EXCLAMATION:
                case STATE_AFTER_OPEN_BRACE:
                case STATE_AFTER_COLON:
                    errorCallback.onError(textSourceRange, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                    return -1;
                case STATE_EXPRESSION:
                    // expression is not allowed to span multiple f-strings: f'{3+' f'1}' is not
                    // the same as f'{3+1}'
                    errorCallback.onError(textSourceRange, ERROR_MESSAGE_EXPECTING_CLOSING_BRACE);
                    return -1;
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
        private static int skipString(ErrorCallback errorCallback, String text, SourceRange sourceRange, int startIndex, int len, char startq) {
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
                        errorCallback.onError(sourceRange, ERROR_MESSAGE_BACKSLASH_IN_EXPRESSION);
                        return -1;
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
                    errorCallback.onError(sourceRange, ERROR_MESSAGE_UNTERMINATED_STRING);
                    return -1;
                }
            }
            return index;
        }

        /**
         * Return an expression Token or {@code null} on error.
         */
        private static Token createExpressionToken(ErrorCallback errorCallback, String text, SourceRange sourceRange, int start, int end) {
            if (start >= end) {
                errorCallback.onError(sourceRange, ERROR_MESSAGE_EMPTY_EXPRESSION);
                return null;
            }
            boolean onlyWhiteSpaces = true;
            for (int index = start; index < end; index++) {
                if (!Character.isWhitespace(text.charAt(index))) {
                    onlyWhiteSpaces = false;
                    break;
                }
            }
            if (onlyWhiteSpaces) {
                errorCallback.onError(sourceRange, ERROR_MESSAGE_EMPTY_EXPRESSION);
                return null;
            }
            return new Token(TOKEN_TYPE_EXPRESSION, start, end);
        }
    }

    private static byte[] decodeEscapeToBytes(ErrorCallback errors, String string, SourceRange sourceRange) {
        StringBuilder sb = decodeEscapes(errors, string, sourceRange, false);
        byte[] bytes = new byte[sb.length()];
        for (int i = 0; i < sb.length(); i++) {
            bytes[i] = (byte) sb.charAt(i);
        }
        return bytes;
    }

    public static StringBuilder decodeEscapes(ErrorCallback errors, String string, SourceRange sourceRange, boolean regexMode) {
        // see _PyBytes_DecodeEscape from
        // https://github.com/python/cpython/blob/master/Objects/bytesobject.c
        // TODO: for the moment we assume ASCII
        StringBuilder charList = new StringBuilder();
        int length = string.length();
        boolean wasDeprecationWarning = false;
        for (int i = 0; i < length; i++) {
            char chr = string.charAt(i);
            if (chr != '\\') {
                charList.append(chr);
                continue;
            }

            i++;
            if (i >= length) {
                errors.onError(ErrorCallback.ErrorType.Value, sourceRange, TRAILING_S_IN_STR, "\\");
                return charList;
            }

            chr = string.charAt(i);
            switch (chr) {
                case '\n':
                    break;
                case '\\':
                    if (regexMode) {
                        charList.append('\\');
                    }
                    charList.append('\\');
                    break;
                case '\'':
                    charList.append('\'');
                    break;
                case '\"':
                    charList.append('\"');
                    break;
                case 'b':
                    charList.append('\b');
                    break;
                case 'f':
                    charList.append('\014');
                    break; /* FF */
                case 't':
                    charList.append('\t');
                    break;
                case 'n':
                    charList.append('\n');
                    break;
                case 'r':
                    charList.append('\r');
                    break;
                case 'v':
                    charList.append('\013');
                    break; /* VT */
                case 'a':
                    charList.append('\007');
                    break; /* BEL */
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    if (!regexMode) {
                        int code = chr - '0';
                        if (i + 1 < length) {
                            char nextChar = string.charAt(i + 1);
                            if ('0' <= nextChar && nextChar <= '7') {
                                code = (code << 3) + nextChar - '0';
                                i++;

                                if (i + 1 < length) {
                                    nextChar = string.charAt(i + 1);
                                    if ('0' <= nextChar && nextChar <= '7') {
                                        code = (code << 3) + nextChar - '0';
                                        i++;
                                    }
                                }
                            }
                        }
                        charList.append((char) code);
                    } else {
                        // this mode is required for regex substitute to disambiguate from
                        // backreferences
                        charList.append('\\');
                        charList.append(chr);
                    }
                    break;
                case 'x':
                    if (i + 2 < length) {
                        try {
                            int b = Integer.parseInt(string.substring(i + 1, i + 3), 16);
                            assert b >= 0x00 && b <= 0xFF;
                            charList.append((char) b);
                            i += 2;
                            break;
                        } catch (NumberFormatException e) {
                            // fall through
                        }
                    }
                    errors.onError(ErrorCallback.ErrorType.Value, sourceRange, INVALID_ESCAPE_AT, "\\x", i);
                    return charList;
                default:
                    if (regexMode) {
                        if (chr == 'g' || (chr >= '0' && chr <= '9')) {
                            // only allow backslashes, named group references and numbered group
                            // references in regex mode
                            charList.append('\\');
                            charList.append(chr);
                        } else {
                            errors.onError(ErrorCallback.ErrorType.Value, sourceRange, INVALID_ESCAPE_AT, "\\x", i);
                            return charList;
                        }
                    } else {
                        charList.append('\\');
                        charList.append(chr);
                        if (!wasDeprecationWarning) {
                            wasDeprecationWarning = true;
                            warnInvalidEscapeSequence(errors, sourceRange, chr);
                        }
                    }
            }
        }

        return charList;
    }

    private static <T> T unescapeString(SourceRange sourceRange, ErrorCallback errorCallback, String st, PythonStringFactory<T> stringFactory) {
        if (!st.contains("\\")) {
            return stringFactory.fromJavaString(st);
        }
        PythonStringFactory.PythonStringBuilder<T> sb = stringFactory.createBuilder(st.length());
        boolean wasDeprecationWarning = false;
        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.appendCodePoint(Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'a':
                        ch = '\u0007';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case 'v':
                        ch = '\u000b';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    case '\r':
                        nextChar = (i == st.length() - 2) ? '\\' : st.charAt(i + 2);
                        if (nextChar == '\n') {
                            i++;
                        }
                        i++;
                        continue;
                    case '\n':
                        i++;
                        continue;
                    // Hex Unicode: u????
                    case 'u':
                        int code = getHexValue(st, sourceRange, i + 2, 4, errorCallback);
                        if (code < 0) {
                            return stringFactory.fromJavaString(st);
                        }
                        sb.appendCodePoint(code);
                        i += 5;
                        continue;
                    // Hex Unicode: U????????
                    case 'U':
                        code = getHexValue(st, sourceRange, i + 2, 8, errorCallback);
                        if (Character.isValidCodePoint(code)) {
                            sb.appendCodePoint(code);
                        } else {
                            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, String.format(UNICODE_ERROR + ILLEGAL_CHARACTER, i, i + 9));
                            return stringFactory.fromJavaString(st);
                        }
                        i += 9;
                        continue;
                    // Hex Unicode: x??
                    case 'x':
                        code = getHexValue(st, sourceRange, i + 2, 2, errorCallback);
                        if (code < 0) {
                            return stringFactory.fromJavaString(st);
                        }
                        sb.appendCodePoint(code);
                        i += 3;
                        continue;
                    case 'N':
                        // a character from Unicode Data Database
                        i = doCharacterName(st, sourceRange, sb, i + 2, errorCallback);
                        if (i < 0) {
                            return stringFactory.fromJavaString(st);
                        }
                        continue;
                    default:
                        if (!wasDeprecationWarning) {
                            wasDeprecationWarning = true;
                            warnInvalidEscapeSequence(errorCallback, sourceRange, nextChar);
                        }
                        sb.appendCodePoint(ch);
                        sb.appendCodePoint(nextChar);
                        i++;
                        continue;
                }
                i++;
            }
            sb.appendCodePoint(ch);
        }
        return sb.build();
    }

    private static int getHexValue(String text, SourceRange sourceRange, int start, int len, ErrorCallback errorCb) {
        int digit;
        int result = 0;
        for (int index = start; index < (start + len); index++) {
            if (index < text.length()) {
                digit = Character.digit(text.charAt(index), 16);
                if (digit == -1) {
                    // Like cpython, raise error with the wrong character first,
                    // even if there are not enough characters
                    return createTruncatedError(sourceRange, start - 2, index - 1, len, errorCb);
                }
                result = result * 16 + digit;
            } else {
                return createTruncatedError(sourceRange, start - 2, index - 1, len, errorCb);
            }
        }
        return result;
    }

    private static int createTruncatedError(SourceRange sourceRange, int startIndex, int endIndex, int len, ErrorCallback errorCb) {
        String truncatedMessage = null;
        switch (len) {
            case 2:
                truncatedMessage = TRUNCATED_XXX_ERROR;
                break;
            case 4:
                truncatedMessage = TRUNCATED_UXXXX_ERROR;
                break;
            case 8:
                truncatedMessage = TRUNCATED_UXXXXXXXX_ERROR;
                break;
        }
        errorCb.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + truncatedMessage, startIndex, endIndex);
        return -1;
    }

    /**
     * Replace '/N{Unicode Character Name}' with the code point of the character.
     *
     * @param text a text that contains /N{...} escape sequence
     * @param sb string builder where the result code point will be written
     * @param offset this is offset of the open brace
     * @return offset of the close brace or {@code -1} if an error was signaled
     */
    private static int doCharacterName(String text, SourceRange sourceRange, PythonStringFactory.PythonStringBuilder<?> sb, int offset, ErrorCallback errorCallback) {
        if (offset >= text.length()) {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + MALFORMED_ERROR, offset - 2, offset - 1);
            return -1;
        }
        char ch = text.charAt(offset);
        if (ch != '{') {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + MALFORMED_ERROR, offset - 2, offset - 1);
            return -1;
        }
        int closeIndex = text.indexOf("}", offset + 1);
        if (closeIndex == -1) {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + MALFORMED_ERROR, offset - 2, offset - 1);
            return -1;
        }
        String charName = text.substring(offset + 1, closeIndex).toUpperCase();
        int cp = getCodePoint(charName);
        if (cp >= 0) {
            sb.appendCodePoint(cp);
        } else {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + UNKNOWN_UNICODE_ERROR, offset - 2, closeIndex);
            return -1;
        }
        return closeIndex;
    }

    // Names for most control characters that mean 0-31, not some symbol
    private static final Map<String, Integer> CONTROL_CHAR_NAMES = new HashMap<>(32);
    static {
        CONTROL_CHAR_NAMES.put("NULL", 0x0000);
        CONTROL_CHAR_NAMES.put("START OF HEADING", 0x0001);
        CONTROL_CHAR_NAMES.put("START OF TEXT", 0x0002);
        CONTROL_CHAR_NAMES.put("END OF TEXT", 0x0003);
        CONTROL_CHAR_NAMES.put("END OF TRANSMISSION", 0x0004);
        CONTROL_CHAR_NAMES.put("ENQUIRY", 0x0005);
        CONTROL_CHAR_NAMES.put("ACKNOWLEDGE", 0x0006);
        CONTROL_CHAR_NAMES.put("BELL", 0x0007);
        CONTROL_CHAR_NAMES.put("BACKSPACE", 0x0008);
        CONTROL_CHAR_NAMES.put("CHARACTER TABULATION", 0x0009);
        CONTROL_CHAR_NAMES.put("LINE FEED", 0x000A);
        CONTROL_CHAR_NAMES.put("LINE TABULATION", 0x000B);
        CONTROL_CHAR_NAMES.put("FORM FEED", 0x000C);
        CONTROL_CHAR_NAMES.put("CARRIAGE RETURN", 0x000D);
        CONTROL_CHAR_NAMES.put("SHIFT OUT", 0x000E);
        CONTROL_CHAR_NAMES.put("SHIFT IN", 0x000F);
        CONTROL_CHAR_NAMES.put("DATA LINK ESCAPE", 0x0010);
        CONTROL_CHAR_NAMES.put("DEVICE CONTROL ONE", 0x0011);
        CONTROL_CHAR_NAMES.put("DEVICE CONTROL TWO", 0x0012);
        CONTROL_CHAR_NAMES.put("DEVICE CONTROL THREE", 0x0013);
        CONTROL_CHAR_NAMES.put("DEVICE CONTROL FOUR", 0x0014);
        CONTROL_CHAR_NAMES.put("NEGATIVE ACKNOWLEDGE", 0x0015);
        CONTROL_CHAR_NAMES.put("SYNCHRONOUS IDLE", 0x0016);
        CONTROL_CHAR_NAMES.put("END OF TRANSMISSION BLOCK", 0x0017);
        CONTROL_CHAR_NAMES.put("CANCEL", 0x0018);
        CONTROL_CHAR_NAMES.put("END OF MEDIUM", 0x0019);
        CONTROL_CHAR_NAMES.put("SUBSTITUTE", 0x001A);
        CONTROL_CHAR_NAMES.put("ESCAPE", 0x001B);
        CONTROL_CHAR_NAMES.put("INFORMATION SEPARATOR FOUR", 0x001C);
        CONTROL_CHAR_NAMES.put("INFORMATION SEPARATOR THREE", 0x001D);
        CONTROL_CHAR_NAMES.put("INFORMATION SEPARATOR TWO", 0x001E);
        CONTROL_CHAR_NAMES.put("INFORMATION SEPARATOR ONE", 0x001F);
        CONTROL_CHAR_NAMES.put("BYTE ORDER MARK", 0xFEFF);
    }

    public static int getCodePoint(String charName) {
        int possibleChar = CONTROL_CHAR_NAMES.getOrDefault(charName.toUpperCase(Locale.ROOT), -1);
        if (possibleChar > -1) {
            return possibleChar;
        }
        possibleChar = UCharacter.getCharFromName(charName);
        if (possibleChar > -1) {
            return possibleChar;
        }
        possibleChar = UCharacter.getCharFromExtendedName(charName);
        if (possibleChar > -1) {
            return possibleChar;
        }
        possibleChar = UCharacter.getCharFromNameAlias(charName);
        if (possibleChar > -1) {
            return possibleChar;
        }
        return -1;
    }

    public static void warnInvalidEscapeSequence(ErrorCallback errorCallback, SourceRange sourceRange, char nextChar) {
        errorCallback.onWarning(WarningType.Deprecation, sourceRange, "invalid escape sequence '\\%c'", nextChar);
    }

    private static final String UNICODE_ERROR = "(unicode error) 'unicodeescape' codec can't decode bytes in position %d-%d:";
    private static final String ILLEGAL_CHARACTER = "illegal Unicode character";
    private static final String TRAILING_S_IN_STR = "Trailing %s in string";
    private static final String MALFORMED_ERROR = " malformed \\N character escape";
    private static final String TRUNCATED_XXX_ERROR = "truncated \\xXX escape";
    private static final String TRUNCATED_UXXXX_ERROR = "truncated \\uXXXX escape";
    private static final String TRUNCATED_UXXXXXXXX_ERROR = "truncated \\UXXXXXXXX escape";
    private static final String UNKNOWN_UNICODE_ERROR = " unknown Unicode character name";
    private static final String INVALID_ESCAPE_AT = "invalid %s escape at position %d";
}
