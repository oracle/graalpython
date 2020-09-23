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

import static com.oracle.graal.python.parser.sst.StringUtils.warnInvalidEscapeSequence;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class BytesUtils {

    static final byte[] HEXDIGITS = "0123456789abcdef".getBytes();

    // tables are copied from CPython/Python/pyctype.c
    static final byte PY_CTF_LOWER = 0x01;
    static final byte PY_CTF_UPPER = 0x02;
    static final byte PY_CTF_ALPHA = (PY_CTF_LOWER | PY_CTF_UPPER);
    static final byte PY_CTF_DIGIT = 0x04;
    static final byte PY_CTF_ALNUM = (PY_CTF_ALPHA | PY_CTF_DIGIT);
    static final byte PY_CTF_SPACE = 0x08;
    static final byte PY_CTF_XDIGIT = 0x10;

    static final byte[] BYTES_TABLE = new byte[]{
                    0, /* 0x0 '\x00' */
                    0, /* 0x1 '\x01' */
                    0, /* 0x2 '\x02' */
                    0, /* 0x3 '\x03' */
                    0, /* 0x4 '\x04' */
                    0, /* 0x5 '\x05' */
                    0, /* 0x6 '\x06' */
                    0, /* 0x7 '\x07' */
                    0, /* 0x8 '\x08' */
                    PY_CTF_SPACE, /* 0x9 '\t' */
                    PY_CTF_SPACE, /* 0xa '\n' */
                    PY_CTF_SPACE, /* 0xb '\v' */
                    PY_CTF_SPACE, /* 0xc '\f' */
                    PY_CTF_SPACE, /* 0xd '\r' */
                    0, /* 0xe '\x0e' */
                    0, /* 0xf '\x0f' */
                    0, /* 0x10 '\x10' */
                    0, /* 0x11 '\x11' */
                    0, /* 0x12 '\x12' */
                    0, /* 0x13 '\x13' */
                    0, /* 0x14 '\x14' */
                    0, /* 0x15 '\x15' */
                    0, /* 0x16 '\x16' */
                    0, /* 0x17 '\x17' */
                    0, /* 0x18 '\x18' */
                    0, /* 0x19 '\x19' */
                    0, /* 0x1a '\x1a' */
                    0, /* 0x1b '\x1b' */
                    0, /* 0x1c '\x1c' */
                    0, /* 0x1d '\x1d' */
                    0, /* 0x1e '\x1e' */
                    0, /* 0x1f '\x1f' */
                    PY_CTF_SPACE, /* 0x20 ' ' */
                    0, /* 0x21 '!' */
                    0, /* 0x22 '"' */
                    0, /* 0x23 '#' */
                    0, /* 0x24 '$' */
                    0, /* 0x25 '%' */
                    0, /* 0x26 '&' */
                    0, /* 0x27 "'" */
                    0, /* 0x28 '(' */
                    0, /* 0x29 ')' */
                    0, /* 0x2a '*' */
                    0, /* 0x2b '+' */
                    0, /* 0x2c ',' */
                    0, /* 0x2d '-' */
                    0, /* 0x2e '.' */
                    0, /* 0x2f '/' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x30 '0' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x31 '1' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x32 '2' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x33 '3' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x34 '4' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x35 '5' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x36 '6' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x37 '7' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x38 '8' */
                    PY_CTF_DIGIT | PY_CTF_XDIGIT, /* 0x39 '9' */
                    0, /* 0x3a ':' */
                    0, /* 0x3b ';' */
                    0, /* 0x3c '<' */
                    0, /* 0x3d '=' */
                    0, /* 0x3e '>' */
                    0, /* 0x3f '?' */
                    0, /* 0x40 '@' */
                    PY_CTF_UPPER | PY_CTF_XDIGIT, /* 0x41 'A' */
                    PY_CTF_UPPER | PY_CTF_XDIGIT, /* 0x42 'B' */
                    PY_CTF_UPPER | PY_CTF_XDIGIT, /* 0x43 'C' */
                    PY_CTF_UPPER | PY_CTF_XDIGIT, /* 0x44 'D' */
                    PY_CTF_UPPER | PY_CTF_XDIGIT, /* 0x45 'E' */
                    PY_CTF_UPPER | PY_CTF_XDIGIT, /* 0x46 'F' */
                    PY_CTF_UPPER, /* 0x47 'G' */
                    PY_CTF_UPPER, /* 0x48 'H' */
                    PY_CTF_UPPER, /* 0x49 'I' */
                    PY_CTF_UPPER, /* 0x4a 'J' */
                    PY_CTF_UPPER, /* 0x4b 'K' */
                    PY_CTF_UPPER, /* 0x4c 'L' */
                    PY_CTF_UPPER, /* 0x4d 'M' */
                    PY_CTF_UPPER, /* 0x4e 'N' */
                    PY_CTF_UPPER, /* 0x4f 'O' */
                    PY_CTF_UPPER, /* 0x50 'P' */
                    PY_CTF_UPPER, /* 0x51 'Q' */
                    PY_CTF_UPPER, /* 0x52 'R' */
                    PY_CTF_UPPER, /* 0x53 'S' */
                    PY_CTF_UPPER, /* 0x54 'T' */
                    PY_CTF_UPPER, /* 0x55 'U' */
                    PY_CTF_UPPER, /* 0x56 'V' */
                    PY_CTF_UPPER, /* 0x57 'W' */
                    PY_CTF_UPPER, /* 0x58 'X' */
                    PY_CTF_UPPER, /* 0x59 'Y' */
                    PY_CTF_UPPER, /* 0x5a 'Z' */
                    0, /* 0x5b '[' */
                    0, /* 0x5c '\\' */
                    0, /* 0x5d ']' */
                    0, /* 0x5e '^' */
                    0, /* 0x5f '_' */
                    0, /* 0x60 '`' */
                    PY_CTF_LOWER | PY_CTF_XDIGIT, /* 0x61 'a' */
                    PY_CTF_LOWER | PY_CTF_XDIGIT, /* 0x62 'b' */
                    PY_CTF_LOWER | PY_CTF_XDIGIT, /* 0x63 'c' */
                    PY_CTF_LOWER | PY_CTF_XDIGIT, /* 0x64 'd' */
                    PY_CTF_LOWER | PY_CTF_XDIGIT, /* 0x65 'e' */
                    PY_CTF_LOWER | PY_CTF_XDIGIT, /* 0x66 'f' */
                    PY_CTF_LOWER, /* 0x67 'g' */
                    PY_CTF_LOWER, /* 0x68 'h' */
                    PY_CTF_LOWER, /* 0x69 'i' */
                    PY_CTF_LOWER, /* 0x6a 'j' */
                    PY_CTF_LOWER, /* 0x6b 'k' */
                    PY_CTF_LOWER, /* 0x6c 'l' */
                    PY_CTF_LOWER, /* 0x6d 'm' */
                    PY_CTF_LOWER, /* 0x6e 'n' */
                    PY_CTF_LOWER, /* 0x6f 'o' */
                    PY_CTF_LOWER, /* 0x70 'p' */
                    PY_CTF_LOWER, /* 0x71 'q' */
                    PY_CTF_LOWER, /* 0x72 'r' */
                    PY_CTF_LOWER, /* 0x73 's' */
                    PY_CTF_LOWER, /* 0x74 't' */
                    PY_CTF_LOWER, /* 0x75 'u' */
                    PY_CTF_LOWER, /* 0x76 'v' */
                    PY_CTF_LOWER, /* 0x77 'w' */
                    PY_CTF_LOWER, /* 0x78 'x' */
                    PY_CTF_LOWER, /* 0x79 'y' */
                    PY_CTF_LOWER, /* 0x7a 'z' */
                    0, /* 0x7b '{' */
                    0, /* 0x7c '|' */
                    0, /* 0x7d '}' */
                    0, /* 0x7e '~' */
                    0, /* 0x7f '\x7f' */
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    };

    static final int[] TO_LOWER = new int[]{
                    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
                    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
                    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                    0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f,
                    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
                    0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f,
                    0x40, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,
                    0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f,
                    0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,
                    0x78, 0x79, 0x7a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f,
                    0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,
                    0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f,
                    0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,
                    0x78, 0x79, 0x7a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f,
                    0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
                    0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f,
                    0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97,
                    0x98, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f,
                    0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
                    0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf,
                    0xb0, 0xb1, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7,
                    0xb8, 0xb9, 0xba, 0xbb, 0xbc, 0xbd, 0xbe, 0xbf,
                    0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7,
                    0xc8, 0xc9, 0xca, 0xcb, 0xcc, 0xcd, 0xce, 0xcf,
                    0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7,
                    0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde, 0xdf,
                    0xe0, 0xe1, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7,
                    0xe8, 0xe9, 0xea, 0xeb, 0xec, 0xed, 0xee, 0xef,
                    0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7,
                    0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe, 0xff,
    };

    static final int[] TO_UPPER = new int[]{
                    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                    0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
                    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
                    0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
                    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                    0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f,
                    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
                    0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f,
                    0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
                    0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f,
                    0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
                    0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f,
                    0x60, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
                    0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f,
                    0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
                    0x58, 0x59, 0x5a, 0x7b, 0x7c, 0x7d, 0x7e, 0x7f,
                    0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
                    0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f,
                    0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97,
                    0x98, 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f,
                    0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
                    0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf,
                    0xb0, 0xb1, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7,
                    0xb8, 0xb9, 0xba, 0xbb, 0xbc, 0xbd, 0xbe, 0xbf,
                    0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7,
                    0xc8, 0xc9, 0xca, 0xcb, 0xcc, 0xcd, 0xce, 0xcf,
                    0xd0, 0xd1, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7,
                    0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd, 0xde, 0xdf,
                    0xe0, 0xe1, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7,
                    0xe8, 0xe9, 0xea, 0xeb, 0xec, 0xed, 0xee, 0xef,
                    0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7,
                    0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe, 0xff,
    };

    public static int mask(byte c) {
        return c & 0xff;
    }

    public static boolean isLower(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_LOWER) != 0;
    }

    public static boolean isUpper(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_UPPER) != 0;
    }

    public static boolean isAlpha(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_ALPHA) != 0;
    }

    public static boolean isDigit(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_DIGIT) != 0;
    }

    public static boolean isXDigit(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_XDIGIT) != 0;
    }

    public static boolean isAlnum(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_ALNUM) != 0;
    }

    public static boolean isSpace(byte c) {
        return (BYTES_TABLE[mask(c)] & PY_CTF_SPACE) != 0;
    }

    public static byte toLower(byte c) {
        return (byte) TO_LOWER[mask(c)];
    }

    public static byte toUpper(byte c) {
        return (byte) TO_UPPER[mask(c)];
    }

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

    public static byte[] fromHex(char[] chars, int len) {
        byte[] out = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            int hn = Character.digit(chars[i], 16) << 4;
            int ln = Character.digit(chars[i + 1], 16);
            out[i / 2] = (byte) (hn + ln);
        }

        return out;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static byte[] fromStringAndEncoding(PythonCore core, String source, String encoding) {
        try {
            String e = encoding.equals("latin-1") ? "ISO-8859-1" : encoding;
            return source.getBytes(e);
        } catch (UnsupportedEncodingException e) {
            throw core.raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
        }
    }

    @TruffleBoundary
    public static StringBuilder newStringBuilder() {
        return new StringBuilder();
    }

    @TruffleBoundary
    public static String sbToString(StringBuilder sb) {
        return sb.toString();
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
            char chr = (char) b;
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
        for (int i = 0; i < str.length();) {
            int ch = str.codePointAt(i);
            j = unicodeEscape(ch, j, bytes);
            i += Character.charCount(ch);
        }
        bytes = Arrays.copyOf(bytes, j);
        return bytes;
    }

    @TruffleBoundary
    public static byte[] unicodeNonAsciiEscape(String str) {
        byte[] bytes = new byte[str.length() * 10];
        int j = 0;
        for (int i = 0; i < str.length();) {
            int ch = str.codePointAt(i);
            j = unicodeNonAsciiEscape(ch, j, bytes);
            i += Character.charCount(ch);
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
                byteEscape(codePoint, i, buffer);
                i += 4;
            }
        } else {
            i = unicodeNonAsciiEscape(codePoint, i, buffer);
        }
        return i;
    }

    public static void byteEscape(int codePoint, int startIndex, byte[] buffer) {
        int i = startIndex;
        buffer[i++] = '\\';
        buffer[i++] = 'x';
        buffer[i++] = HEXDIGITS[(codePoint >> 4) & 0x000F];
        buffer[i] = HEXDIGITS[codePoint & 0x000F];
    }

    public static int unicodeNonAsciiEscape(int codePoint, int startIndex, byte[] buffer) {
        int i = startIndex;
        if (codePoint < 0x100) {
            buffer[i++] = (byte) codePoint;
        } else if (codePoint < 0x10000) {
            /* U+0100-U+ffff range: Map 16-bit characters to '\\uHHHH' */
            buffer[i++] = '\\';
            buffer[i++] = 'u';
            buffer[i++] = HEXDIGITS[(codePoint >> 12) & 0x000F];
            buffer[i++] = HEXDIGITS[(codePoint >> 8) & 0x000F];
            buffer[i++] = HEXDIGITS[(codePoint >> 4) & 0x000F];
            buffer[i++] = HEXDIGITS[codePoint & 0x000F];
        } else {
            /* U+010000-U+10ffff range: Map 21-bit characters to '\U00HHHHHH' */
            /* Make sure that the first two digits are zero */
            buffer[i++] = '\\';
            buffer[i++] = 'U';
            buffer[i++] = '0';
            buffer[i++] = '0';
            buffer[i++] = HEXDIGITS[(codePoint >> 20) & 0x0000000F];
            buffer[i++] = HEXDIGITS[(codePoint >> 16) & 0x0000000F];
            buffer[i++] = HEXDIGITS[(codePoint >> 12) & 0x0000000F];
            buffer[i++] = HEXDIGITS[(codePoint >> 8) & 0x0000000F];
            buffer[i++] = HEXDIGITS[(codePoint >> 4) & 0x0000000F];
            buffer[i++] = HEXDIGITS[codePoint & 0x0000000F];
        }
        return i;
    }

    @TruffleBoundary
    public static byte[] fromHex(String str, PRaiseNode raiseNode) {
        // This overestimates if there are spaces
        byte[] bytes = new byte[str.length() / 2];
        byte[] strchar = str.getBytes();
        int n = 0;
        for (int i = 0; i < str.length(); i++) {
            byte c = strchar[i];
            if (isSpace(c)) {
                continue;
            }
            int top = BytesUtils.digitValue(c);
            if (top >= 16 || top < 0) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, "non-hexadecimal number found in fromhex() arg at position %d", i);
            }

            c = i + 1 < str.length() ? strchar[++i] : 0;
            int bottom = BytesUtils.digitValue(c);
            if (bottom >= 16 || bottom < 0) {
                throw raiseNode.raise(PythonBuiltinClassType.ValueError, "non-hexadecimal number found in fromhex() arg at position %d", i);
            }

            bytes[n++] = (byte) ((top << 4) | bottom);
        }
        if (n != bytes.length) {
            bytes = Arrays.copyOf(bytes, n);
        }
        return bytes;
    }

    /**
     * Convert a hex digit character to it's byte value. E.g. this method returns {@code (byte)10}
     * for character {@code 'A'}. For any other character, value {@code 37} is returned. This is
     * similar to {@code _PyLong_DigitValue}.
     */
    public static int digitValue(byte hexChar) {
        if (isDigit(hexChar)) {
            return hexChar - '0';
        } else if ('a' <= hexChar && hexChar <= 'f') {
            return hexChar - 'a' + 10;
        } else if ('A' <= hexChar && hexChar <= 'F') {
            return hexChar - 'A' + 10;
        }
        return 37;
    }

    @TruffleBoundary
    public static String createASCIIString(byte[] retbuf) {
        return new String(retbuf, StandardCharsets.US_ASCII);
    }
}
