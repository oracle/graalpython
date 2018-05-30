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

    public static byte[] fromString(PythonCore core, String source, boolean raw) {
        return decodeEscapeToBytes(core, source, raw);
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
                } else {
                    throw core.raise(ValueError, "byte must be in range(0, 256)");
                }
            } else {
                throw core.raise(TypeError, "'%s' object cannot be interpreted as an integer", core.lookupType(item.getClass()));
            }
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

    @TruffleBoundary
    public static byte[] decodeEscapeToBytes(PythonCore core, String string, boolean raw) {
        // see _PyBytes_DecodeEscape from
        // https://github.com/python/cpython/blob/master/Objects/bytesobject.c
        // TODO: for the moment we assume ASCII
        List<Character> byteList = new ArrayList<>();
        int length = string.length();
        for (int i = 0; i < length; i++) {
            char chr = string.charAt(i);
            if (chr != '\\' || raw) {
                byteList.add(chr);
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
                    byteList.add('\\');
                    break;
                case '\'':
                    byteList.add('\'');
                    break;
                case '\"':
                    byteList.add('\"');
                    break;
                case 'b':
                    byteList.add('\b');
                    break;
                case 'f':
                    byteList.add('\014');
                    break; /* FF */
                case 't':
                    byteList.add('\t');
                    break;
                case 'n':
                    byteList.add('\n');
                    break;
                case 'r':
                    byteList.add('\r');
                    break;
                case 'v':
                    byteList.add('\013');
                    break; /* VT */
                case 'a':
                    byteList.add('\007');
                    break; /* BEL */
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
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
                    byteList.add((char) code);
                    break;
                case 'x':
                    if (i + 2 < length) {
                        try {
                            int b = Integer.parseInt(string.substring(i + 1, i + 3), 16);
                            assert b >= 0x00 && b <= 0xFF;
                            byteList.add((char) b);
                            i += 2;
                            break;
                        } catch (NumberFormatException e) {
                            // fall through
                        }
                    }
                    throw core.raise(ValueError, "invalid \\x escape at position %d", i);
            }
        }

        byte[] bytes = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            bytes[i] = (byte) byteList.get(i).charValue();
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
}
