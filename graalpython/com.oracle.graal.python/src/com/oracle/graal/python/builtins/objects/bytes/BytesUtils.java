/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.oracle.graal.python.nodes.ErrorMessages;
import static com.oracle.graal.python.parser.sst.StringUtils.warnInvalidEscapeSequence;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class BytesUtils {

    static final byte[] hexdigits = "0123456789abcdef".getBytes();

    public static byte[] fromSize(PythonCore core, int size) {
        if (size < 0) {
            throw core.raise(ValueError, ErrorMessages.NEGATIVE_COUNT);
        } else if (size >= Integer.MAX_VALUE) {
            // TODO: fix me, in python the array can take long sizes, we are bound to ints for now
            throw core.raise(OverflowError, ErrorMessages.BYTE_STR_IS_TOO_LARGE);
        }
        return new byte[size];
    }

    public static byte[] fromString(ParserErrorCallback errors, String source) {
        return decodeEscapeToBytes(errors, source);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static byte[] fromStringAndEncoding(PythonCore core, String source, String encoding) {
        try {
            return source.getBytes(encoding);
        } catch (UnsupportedEncodingException e) {
            throw core.raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
        }
    }

    @TruffleBoundary
    public static void byteRepr(StringBuilder sb, byte b) {
        if (b == 9) {
            sb.append("\\t");
        } else if (b == 10) {
            sb.append("\\n");
        } else if (b == 13) {
            sb.append("\\r");
        } else if (b > 31 && b <= 126) {
            Character chr = (char) b;
            if (chr == '\'' || chr == '\\') {
                sb.append('\\');
            }
            sb.append(chr);
        } else {
            sb.append(String.format("\\x%02x", b));
        }
    }

    public static String bytesRepr(byte[] bytes, int length) {
        CompilerAsserts.neverPartOfCompilation();
        int len = length;
        if (len > bytes.length) {
            len = bytes.length;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("b'");
        for (int i = 0; i < len; i++) {
            byteRepr(sb, bytes[i]);
        }
        sb.append("'");
        return sb.toString();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static StringBuilder decodeEscapes(ParserErrorCallback errors, String string, boolean regexMode) {
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
                throw errors.raise(ValueError, ErrorMessages.TRAILING_S_IN_STR, "\\");
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
                    throw errors.raise(ValueError, ErrorMessages.INVALID_ESCAPE_AT, "\\x", i);
                default:
                    if (regexMode) {
                        if (chr == 'g' || (chr >= '0' && chr <= '9')) {
                            // only allow backslashes, named group references and numbered group
                            // references in regex mode
                            charList.append('\\');
                            charList.append(chr);
                        } else {
                            throw errors.raise(ValueError, ErrorMessages.INVALID_ESCAPE_SEQ_AT, chr, i);
                        }
                    } else {
                        charList.append('\\');
                        charList.append(chr);
                        if (!wasDeprecationWarning) {
                            wasDeprecationWarning = true;
                            warnInvalidEscapeSequence(errors, chr);
                        }
                    }
            }
        }

        return charList;
    }

    private static byte[] decodeEscapeToBytes(ParserErrorCallback errors, String string) {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder sb = decodeEscapes(errors, string, false);
        byte[] bytes = new byte[sb.length()];
        for (int i = 0; i < sb.length(); i++) {
            bytes[i] = (byte) sb.charAt(i);
        }
        return bytes;
    }

    @TruffleBoundary
    public static byte[] unicodeEscape(String str) {
        // Initial allocation of bytes for UCS4 strings needs 10 bytes per source character
        // ('\U00xxxxxx')
        byte[] bytes = new byte[str.length() * 10];
        int j = 0;
        for (int i = 0; i < str.length(); i++) {
            int ch = str.codePointAt(i);
            j = unicodeEscape(ch, j, bytes);
        }
        bytes = Arrays.copyOf(bytes, j);
        return bytes;
    }

    @TruffleBoundary
    public static byte[] unicodeNonAsciiEscape(String str) {
        byte[] bytes = new byte[str.length() * 10];
        int j = 0;
        for (int i = 0; i < str.length(); i++) {
            int ch = str.codePointAt(i);
            j = unicodeNonAsciiEscape(ch, j, bytes);
        }
        bytes = Arrays.copyOf(bytes, j);
        return bytes;
    }

    /**
     * Puts the escape sequence for given code point into given byte array starting from given
     * index. Returns index of the last written buffer plus one.
     */
    public static int unicodeEscape(int codePoint, int startIndex, byte[] buffer) {
        int i = startIndex;
        /* U+0000-U+00ff range */
        if (codePoint < 0x100) {
            if (codePoint >= ' ' && codePoint < 127) {
                if (codePoint != '\\') {
                    /* Copy printable US ASCII as-is */
                    buffer[i++] = (byte) codePoint;
                } else {
                    /* Escape backslashes */
                    buffer[i++] = '\\';
                    buffer[i++] = '\\';
                }
            } else if (codePoint == '\t') {
                /* Map special whitespace to '\t', \n', '\r' */
                buffer[i++] = '\\';
                buffer[i++] = 't';
            } else if (codePoint == '\n') {
                buffer[i++] = '\\';
                buffer[i++] = 'n';
            } else if (codePoint == '\r') {
                buffer[i++] = '\\';
                buffer[i++] = 'r';
            } else {
                /* Map non-printable US ASCII and 8-bit characters to '\xHH' */
                buffer[i++] = '\\';
                buffer[i++] = 'x';
                buffer[i++] = hexdigits[(codePoint >> 4) & 0x000F];
                buffer[i++] = hexdigits[codePoint & 0x000F];
            }
        } else {
            i = unicodeNonAsciiEscape(codePoint, i, buffer);
        }
        return i;
    }

    private static int unicodeNonAsciiEscape(int codePoint, int startIndex, byte[] buffer) {
        int i = startIndex;
        if (codePoint < 0x100) {
            buffer[i++] = (byte) codePoint;
        } else if (codePoint < 0x10000) {
            /* U+0100-U+ffff range: Map 16-bit characters to '\\uHHHH' */
            buffer[i++] = '\\';
            buffer[i++] = 'u';
            buffer[i++] = hexdigits[(codePoint >> 12) & 0x000F];
            buffer[i++] = hexdigits[(codePoint >> 8) & 0x000F];
            buffer[i++] = hexdigits[(codePoint >> 4) & 0x000F];
            buffer[i++] = hexdigits[codePoint & 0x000F];
        } else {
            /* U+010000-U+10ffff range: Map 21-bit characters to '\U00HHHHHH' */
            /* Make sure that the first two digits are zero */
            buffer[i++] = '\\';
            buffer[i++] = 'U';
            buffer[i++] = '0';
            buffer[i++] = '0';
            buffer[i++] = hexdigits[(codePoint >> 20) & 0x0000000F];
            buffer[i++] = hexdigits[(codePoint >> 16) & 0x0000000F];
            buffer[i++] = hexdigits[(codePoint >> 12) & 0x0000000F];
            buffer[i++] = hexdigits[(codePoint >> 8) & 0x0000000F];
            buffer[i++] = hexdigits[(codePoint >> 4) & 0x0000000F];
            buffer[i++] = hexdigits[codePoint & 0x0000000F];
        }
        return i;
    }
}
