/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.pegparser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;

import com.oracle.graal.python.pegparser.ErrorCallback.WarningType;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Token.Kind;

final class StringParser {

    private StringParser() {
    }

    /**
     * _PyPegen_parse_string
     */
    static ConstantValue parseString(AbstractParser parser, int[] codePoints, Token token) {
        int s = token.startOffset;
        boolean bytesmode = false;
        boolean rawmode = false;
        int quote = codePoints[s];
        while (!bytesmode || !rawmode) {
            if (quote == 'b' || quote == 'B') {
                quote = codePoints[++s];
                bytesmode = true;
            } else if (quote == 'u' || quote == 'U') {
                quote = codePoints[++s];
            } else if (quote == 'r' || quote == 'R') {
                quote = codePoints[++s];
                rawmode = true;
            } else {
                break;
            }
        }
        assert quote == '\'' || quote == '"';
        s++;
        int len = token.endOffset - s;
        assert len >= 1;
        len--;
        assert codePoints[s + len] == quote : "last quote char must match the first";
        if (len >= 4 && codePoints[s] == quote && codePoints[s + 1] == quote) {
            s += 2;
            len -= 4;
            assert codePoints[s + len] == quote && codePoints[s + len + 1] == quote : "invalid ending triple quote";
        }
        // Avoid invoking escape decoding routines if possible.
        rawmode = rawmode || indexOf(codePoints, s, s + len, '\\') == -1;
        if (bytesmode) {
            if (rawmode) {
                byte[] result = new byte[len];
                // Disallow non-ASCII characters.
                for (int i = 0; i < len; ++i) {
                    if (codePoints[s + i] >= 0x80) {
                        parser.raiseSyntaxErrorKnownLocation(token, BYTES_ONLY_ASCII);
                    }
                    result[i] = (byte) codePoints[s + i];
                }
                return ConstantValue.ofBytes(result);
            }
            return ConstantValue.ofBytes(decodeBytesWithEscapes(parser, codePoints, s, len, token));
        }
        return decodeString(parser, codePoints, rawmode, s, len, token);
    }

    /**
     * _PyPegen_decode_string
     */
    static ConstantValue decodeString(AbstractParser parser, int[] codePoints, boolean raw, int s, int len, Token token) {
        if (raw) {
            return parser.stringFactory.fromCodePoints(codePoints, s, len);
        }
        return decodeUnicodeWithEscapes(parser, codePoints, s, len, token);
    }

    /**
     * decode_bytes_with_escapes with inlined _PyBytes_DecodeEscape and ASCII check from
     * _PyPegen_parse_string
     */
    private static byte[] decodeBytesWithEscapes(AbstractParser parser, int[] codePoints, int sInput, int len, Token token) {
        ByteArrayBuilder writer = new ByteArrayBuilder(len);
        int s = sInput;
        int end = s + len;
        boolean wasInvalidEscapeWarning = false;
        while (s < end) {
            int chr = codePoints[s];
            s++;
            if (chr != '\\') {
                // Disallow non-ASCII characters.
                if (chr >= 0x80) {
                    parser.raiseSyntaxErrorKnownLocation(token, "bytes can only contain ASCII literal characters");
                }
                writer.append((byte) chr);
                continue;
            }

            if (s == end) {
                parser.errorCb.onError(ErrorCallback.ErrorType.Value, token.sourceRange, TRAILING_S_IN_STR, "\\");
                return null;
            }

            chr = codePoints[s++];
            switch (chr) {
                case '\n':
                    break;
                case '\\':
                    writer.append('\\');
                    break;
                case '\'':
                    writer.append('\'');
                    break;
                case '\"':
                    writer.append('\"');
                    break;
                case 'b':
                    writer.append('\b');
                    break;
                case 'f':
                    writer.append('\014');
                    break; /* FF */
                case 't':
                    writer.append('\t');
                    break;
                case 'n':
                    writer.append('\n');
                    break;
                case 'r':
                    writer.append('\r');
                    break;
                case 'v':
                    writer.append('\013');
                    break; /* VT */
                case 'a':
                    writer.append('\007');
                    break; /* BEL */
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                    int c = chr - '0';
                    if (s < end) {
                        int nextChar = codePoints[s];
                        if ('0' <= nextChar && nextChar <= '7') {
                            c = (c << 3) + nextChar - '0';
                            s++;

                            if (s < end) {
                                nextChar = codePoints[s];
                                if ('0' <= nextChar && nextChar <= '7') {
                                    c = (c << 3) + nextChar - '0';
                                    s++;
                                }
                            }
                        }
                    }
                    if (c > 255) {
                        if (!wasInvalidEscapeWarning) {
                            wasInvalidEscapeWarning = true;
                            warnInvalidEscapeSequence(parser, codePoints, s - 3, token);
                        }
                    }
                    writer.append(c);
                    break;
                case 'x':
                    if (s + 1 < end) {
                        int digit1 = Character.digit(codePoints[s], 16);
                        int digit2 = Character.digit(codePoints[s + 1], 16);
                        if (digit1 >= 0 && digit2 >= 0) {
                            writer.append(digit1 << 4 | digit2);
                            s += 2;
                            break;
                        }
                    }
                    /* invalid hexadecimal digits */
                    parser.errorCb.onError(ErrorCallback.ErrorType.Value, token.sourceRange, INVALID_ESCAPE_AT, "\\x", s - 2 - (end - len));
                    return null;
                default:
                    if (!wasInvalidEscapeWarning) {
                        wasInvalidEscapeWarning = true;
                        warnInvalidEscapeSequence(parser, codePoints, s - 1, token);
                    }
                    writer.append('\\');
                    s--;
            }
        }
        return writer.build();
    }

    private static ConstantValue decodeUnicodeWithEscapes(AbstractParser parser, int[] codePoints, int start, int len, Token token) {
        int end = start + len;
        int backslashIndex = indexOf(codePoints, start, end, '\\');
        if (backslashIndex < 0) {
            return parser.stringFactory.fromCodePoints(codePoints, start, end - start);
        }
        PythonStringFactory.PythonStringBuilder sb = parser.stringFactory.createBuilder(end - start);
        boolean emittedDeprecationWarning = false;
        int substringStart = start;
        do {
            if (backslashIndex != substringStart) {
                sb.appendCodePoints(codePoints, substringStart, backslashIndex - substringStart);
            }
            if (backslashIndex + 1 < end) {
                substringStart = processEscapeSequence(token.sourceRange, parser.errorCb, codePoints, backslashIndex + 1, end, sb);
                if (substringStart == backslashIndex + 1) {
                    sb.appendCodePoint('\\');
                    if (!emittedDeprecationWarning) {
                        emittedDeprecationWarning = true;
                        warnInvalidEscapeSequence(parser, codePoints, substringStart, token);
                    }
                }
            } else {
                // Lone backslash at the end, can occur in f-strings
                substringStart = backslashIndex;
                break;
            }
        } while ((backslashIndex = indexOf(codePoints, substringStart, end, '\\')) >= 0);
        if (substringStart < end) {
            sb.appendCodePoints(codePoints, substringStart, end - substringStart);
        }
        return sb.build();
    }

    private static int processEscapeSequence(SourceRange sourceRange, ErrorCallback errorCallback, int[] codePoints, int startIndex, int end, PythonStringFactory.PythonStringBuilder sb) {
        int cp = codePoints[startIndex];
        int i = startIndex + 1;
        return switch (cp) {
            case '\\' -> {
                sb.appendCodePoint('\\');
                yield i;
            }
            case 'a' -> {
                sb.appendCodePoint('\u0007');
                yield i;
            }
            case 'b' -> {
                sb.appendCodePoint('\b');
                yield i;
            }
            case 'f' -> {
                sb.appendCodePoint('\f');
                yield i;
            }
            case 'n' -> {
                sb.appendCodePoint('\n');
                yield i;
            }
            case 'r' -> {
                sb.appendCodePoint('\r');
                yield i;
            }
            case 't' -> {
                sb.appendCodePoint('\t');
                yield i;
            }
            case 'v' -> {
                sb.appendCodePoint('\u000b');
                yield i;
            }
            case '\"' -> {
                sb.appendCodePoint('\"');
                yield i;
            }
            case '\'' -> {
                sb.appendCodePoint('\'');
                yield i;
            }
            case '\r', '\n' -> i;
            // Octal code point
            case '0', '1', '2', '3', '4', '5', '6', '7' -> {
                int octalValue = cp - '0';
                cp = i < end ? codePoints[i] : 0;
                if (cp >= '0' && cp <= '7') {
                    i++;
                    octalValue = octalValue * 8 + cp - '0';
                    cp = i < end ? codePoints[i] : 0;
                    if (cp >= '0' && cp <= '7') {
                        i++;
                        octalValue = octalValue * 8 + cp - '0';
                    }
                }
                sb.appendCodePoint(octalValue);
                yield i;
            }
            // Hex Unicode: u????
            case 'u' -> {
                int code = getHexValue(codePoints, sourceRange, i, end, 4, errorCallback);
                if (code < 0) {
                    yield startIndex;
                }
                sb.appendCodePoint(code);
                yield i + 4;
            }
            // Hex Unicode: U????????
            case 'U' -> {
                int code = getHexValue(codePoints, sourceRange, i, end, 8, errorCallback);
                if (Character.isValidCodePoint(code)) {
                    sb.appendCodePoint(code);
                } else {
                    errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, String.format(UNICODE_ERROR + ILLEGAL_CHARACTER, i, i + 9));
                    yield startIndex;
                }
                yield i + 8;
            }
            // Hex Unicode: x??
            case 'x' -> {
                int code = getHexValue(codePoints, sourceRange, i, end, 2, errorCallback);
                if (code < 0) {
                    yield startIndex;
                }
                sb.appendCodePoint(code);
                yield i + 2;
            }
            case 'N' -> {
                i = doCharacterName(codePoints, sourceRange, sb, i, end, errorCallback);
                if (i < 0) {
                    yield startIndex;
                }
                yield i;
                // a character from Unicode Data Database
            }
            default -> startIndex;
        };
    }

    private static int getHexValue(int[] codePoints, SourceRange sourceRange, int start, int end, int len, ErrorCallback errorCb) {
        int digit;
        int result = 0;
        for (int index = start; index < (start + len); index++) {
            if (index < end) {
                digit = Character.digit(codePoints[index], 16);
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
     * @param codePoints a text that contains /N{...} escape sequence
     * @param sb string builder where the result code point will be written
     * @param offset this is offset of the open brace
     * @param end end of the input
     * @return offset after the close brace or {@code -1} if an error was signaled
     */
    private static int doCharacterName(int[] codePoints, SourceRange sourceRange, PythonStringFactory.PythonStringBuilder sb, int offset, int end, ErrorCallback errorCallback) {
        if (offset >= end) {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + MALFORMED_ERROR, offset - 2, offset - 1);
            return -1;
        }
        int ch = codePoints[offset];
        if (ch != '{') {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + MALFORMED_ERROR, offset - 2, offset - 1);
            return -1;
        }
        int closeIndex = indexOf(codePoints, offset + 1, end, '}');
        if (closeIndex == -1) {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + MALFORMED_ERROR, offset - 2, offset - 1);
            return -1;
        }
        String charName = new String(codePoints, offset + 1, closeIndex - offset - 1).toUpperCase();
        int cp = getCodePoint(charName);
        if (cp >= 0) {
            sb.appendCodePoint(cp);
        } else {
            errorCallback.onError(ErrorCallback.ErrorType.Encoding, sourceRange, UNICODE_ERROR + UNKNOWN_UNICODE_ERROR, offset - 2, closeIndex);
            return -1;
        }
        return closeIndex + 1;
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

    private static int getCodePoint(String charName) {
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

    private static void warnInvalidEscapeSequence(AbstractParser parser, int[] codePoints, int firstInvalidEscape, Token token) {
        if (parser.callInvalidRules) {
            // Do not report warnings if we are in the second pass of the parser
            // to avoid showing the warning twice.
            return;
        }
        int c = codePoints[firstInvalidEscape];
        if ((token.type == Kind.FSTRING_MIDDLE || token.type == Kind.FSTRING_END) && (c == '{' || c == '}')) {
            // in this case the tokenizer has already emitted a warning
            return;
        }

        boolean octal = '4' <= c && c <= '7';
        WarningType category;
        if (parser.featureVersion >= 12) {
            category = WarningType.Syntax;
        } else {
            category = WarningType.Deprecation;
        }
        if (octal) {
            parser.errorCb.onWarning(category, token.sourceRange, INVALID_OCTAL_ESCAPE, c, codePoints[firstInvalidEscape + 1], codePoints[firstInvalidEscape + 2]);
        } else {
            parser.errorCb.onWarning(category, token.sourceRange, INVALID_ESCAPE, c);
        }
    }

    private static final String UNICODE_ERROR = "(unicode error) 'unicodeescape' codec can't decode bytes in position %d-%d:";
    private static final String ILLEGAL_CHARACTER = "illegal Unicode character";
    private static final String TRAILING_S_IN_STR = "Trailing %s in string";
    private static final String MALFORMED_ERROR = " malformed \\N character escape";
    private static final String TRUNCATED_XXX_ERROR = "truncated \\xXX escape";
    private static final String TRUNCATED_UXXXX_ERROR = "truncated \\uXXXX escape";
    private static final String TRUNCATED_UXXXXXXXX_ERROR = "truncated \\UXXXXXXXX escape";
    private static final String UNKNOWN_UNICODE_ERROR = " unknown Unicode character name";
    private static final String INVALID_ESCAPE = "invalid escape sequence '\\%c'";
    private static final String INVALID_OCTAL_ESCAPE = "invalid octal escape sequence '\\%c%c%c'";
    private static final String INVALID_ESCAPE_AT = "invalid %s escape at position %d";
    private static final String BYTES_ONLY_ASCII = "bytes can only contain ASCII literal characters";

    /**
     * Returned value is relative to `codepoints[0]`, either -1 or in the range [start, end - 1]
     * inclusive.
     */
    public static int indexOf(int[] codePoints, int start, int end, int cp) {
        int i = start;
        while (i < end) {
            if (codePoints[i] == cp) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    private static final class ByteArrayBuilder {
        private byte[] data;
        private int size;

        ByteArrayBuilder(int capacity) {
            this.data = new byte[capacity];
        }

        void append(int item) {
            assert item >= 0 && item <= 0xff;
            if (size + 1 > data.length) {
                data = Arrays.copyOf(data, Math.max(size + 1, size * 2));
            }
            this.data[size++] = (byte) item;
        }

        byte[] build() {
            return Arrays.copyOf(data, size);
        }
    }
}
