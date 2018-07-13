/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class BytesUtils {

    public static byte[] fromSize(PythonCore core, int size) {
        if (size < 0) {
            throw core.raise(ValueError, "negative count");
        } else if (size >= Integer.MAX_VALUE) {
            // TODO: fix me, in python the array can take long sizes, we are bound to ints for now
            throw core.raise(OverflowError, "byte string is too large");
        }
        return new byte[size];
    }

    public static byte[] fromString(PythonCore core, String source) {
        return decodeEscapeToBytes(core, source);
    }

    @TruffleBoundary
    public static byte[] fromStringAndEncoding(PythonCore core, String source, String encoding) {
        try {
            return source.getBytes(encoding);
        } catch (UnsupportedEncodingException e) {
            throw core.raise(LookupError, "unknown encoding: %s", encoding);
        }
    }

    public static byte[] fromList(PythonCore core, PList list) {
        int len = list.len();
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            Object item = list.getItem(i);
            if (item instanceof Integer) {
                Integer integer = (Integer) item;
                if (integer >= 0 && integer < 256) {
                    bytes[i] = integer.byteValue();
                    continue;
                }
            } else if (item instanceof Long) {
                Long integer = (Long) item;
                if (integer >= 0 && integer < 256) {
                    bytes[i] = integer.byteValue();
                    continue;
                }
            } else if (item instanceof PInt) {
                try {
                    long integer = ((PInt) item).intValueExact();
                    if (integer >= 0 && integer < 256) {
                        bytes[i] = (byte) integer;
                        continue;
                    }
                } catch (ArithmeticException e) {
                }
            } else {
                throw core.raise(TypeError, "'%s' object cannot be interpreted as an integer", core.lookupType(item.getClass()));
            }
            throw core.raise(ValueError, "byte must be in range(0, 256)");
        }
        return bytes;
    }

    public static String __repr__(byte[] bytes) {
        return __repr__(bytes, bytes.length);
    }

    @TruffleBoundary
    public static String __repr__(byte[] bytes, int length) {
        int len = length;
        if (len > bytes.length) {
            len = bytes.length;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("b'");
        for (int i = 0; i < len; i++) {
            byte b = bytes[i];
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
        sb.append("'");
        return sb.toString();
    }

    @TruffleBoundary(transferToInterpreterOnException = false, allowInlining = true)
    public static StringBuilder decodeEscapes(PythonCore core, String string, boolean regexMode) {
        // see _PyBytes_DecodeEscape from
        // https://github.com/python/cpython/blob/master/Objects/bytesobject.c
        // TODO: for the moment we assume ASCII
        StringBuilder charList = new StringBuilder();
        int length = string.length();
        for (int i = 0; i < length; i++) {
            char chr = string.charAt(i);
            if (chr != '\\') {
                charList.append(chr);
                continue;
            }

            i++;
            if (i >= length) {
                throw core.raise(ValueError, "Trailing \\ in string");
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
                        if (i < length) {
                            char nextChar = string.charAt(i);
                            if (nextChar < '7' && nextChar > '0') {
                                code = (code << 3) + nextChar - '0';
                                i++;

                                if (i < length) {
                                    nextChar = string.charAt(i);
                                    if (nextChar < '7' && nextChar > '0') {
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
                    throw core.raise(ValueError, "invalid \\x escape at position %d", i);
                default:
                    if (regexMode) {
                        charList.append('\\');
                        charList.append(chr);
                    } else {
                        throw core.raise(ValueError, "invalid escape sequence '\\%s' at position %d", chr, i);
                    }
            }
        }

        return charList;
    }

    @TruffleBoundary
    public static byte[] decodeEscapeToBytes(PythonCore core, String string) {
        StringBuilder sb = decodeEscapes(core, string, false);
        byte[] bytes = new byte[sb.length()];
        for (int i = 0; i < sb.length(); i++) {
            bytes[i] = (byte) sb.charAt(i);
        }
        return bytes;
    }

    public static byte[] join(PythonCore core, byte[] bytes, Object... values) {
        List<byte[]> parts = new ArrayList<>();
        int totalSize = 0;
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) {
                // end of internal storage array
                break;
            } else if (value instanceof PSequence && ((PSequence) value).getSequenceStorage() instanceof ByteSequenceStorage) {
                byte[] internalByteArray = ((ByteSequenceStorage) ((PSequence) value).getSequenceStorage()).getInternalByteArray();
                parts.add(internalByteArray);
                totalSize += internalByteArray.length;
            } else {
                throw core.raise(TypeError, "sequence item %s: expected a bytes-like object, %s found", i, core.lookupType(value.getClass()));
            }

            if (bytes != null && i < values.length - 1) {
                parts.add(bytes);
                totalSize += bytes.length;
            }
        }

        byte[] joinedBytes = new byte[totalSize];
        int offset = 0;
        for (byte[] array : parts) {
            System.arraycopy(array, 0, joinedBytes, offset, array.length);
            offset += array.length;
        }

        return joinedBytes;
    }

    private static int normalizeIndex(int index, int length) {
        int idx = index;
        if (idx < 0) {
            idx += length;
        }
        return idx;
    }

    public static int find(PIBytesLike primary, PIBytesLike sub, int starting, int ending) {
        byte[] haystack = primary.getInternalByteArray();
        int len1 = primary.len();
        byte[] needle = sub.getInternalByteArray();
        int len2 = sub.len();

        int start = normalizeIndex(starting, len1);
        int end = normalizeIndex(ending, len1);

        if (start >= len1 || len1 < len2) {
            return -1;
        } else if (end > len1) {
            end = len1;
        }

        outer: for (int i = start; i < end; i++) {
            for (int j = 0; j < len2; j++) {
                if (needle[j] != haystack[i + j] || i + j >= end) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static int find(PIBytesLike primary, int sub, int starting, @SuppressWarnings("unused") int ending) {
        byte[] haystack = primary.getInternalByteArray();
        int len1 = primary.len();

        int start = normalizeIndex(starting, len1);
        if (start >= len1) {
            return -1;
        }

        for (int i = start; i < len1; i++) {
            if (haystack[i] == sub) {
                return i;
            }
        }
        return -1;
    }

    public static boolean startsWith(PIBytesLike primary, PIBytesLike prefix, int start, int ending) {
        return find(primary, prefix, start, ending) != -1;
    }

    public static boolean startsWith(PIBytesLike primary, PIBytesLike prefix) {
        return find(primary, prefix, 0, primary.len()) != -1;
    }

    public static boolean endsWith(PIBytesLike primary, PIBytesLike suffix) {
        return find(primary, suffix, primary.len() - suffix.len(), primary.len()) != -1;
    }
}
