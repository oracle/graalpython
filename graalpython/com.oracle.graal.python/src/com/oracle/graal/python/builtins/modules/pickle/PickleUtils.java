/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.modules.pickle.PPickler.BasePickleWriteNode.NEW_LINE_BYTE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.StandardCharsets;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuffer;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PickleUtils {
    public static final TruffleString T_MOD_COPYREG = tsLiteral("copyreg");
    public static final TruffleString T_MOD_FUNCTOOLS = tsLiteral("functools");
    public static final TruffleString T_MOD_CODECS = tsLiteral("codecs");
    public static final TruffleString T_MOD_COMPAT_PICKLE = tsLiteral("_compat_pickle");
    public static final TruffleString T_ATTR_EXT_REGISTRY = tsLiteral("_extension_registry");
    public static final TruffleString T_ATTR_INV_REGISTRY = tsLiteral("_inverted_registry");
    public static final TruffleString T_ATTR_EXT_CACHE = tsLiteral("_extension_cache");
    public static final TruffleString T_ATTR_NAME_MAPPING = tsLiteral("NAME_MAPPING");
    public static final TruffleString T_ATTR_IMPORT_MAPPING = tsLiteral("IMPORT_MAPPING");
    public static final TruffleString T_ATTR_REVERSE_NAME_MAPPING = tsLiteral("REVERSE_NAME_MAPPING");
    public static final TruffleString T_ATTR_REVERSE_IMPORT_MAPPING = tsLiteral("REVERSE_IMPORT_MAPPING");
    public static final TruffleString T_CP_NAME_MAPPING = StringUtils.cat(T_MOD_COMPAT_PICKLE, T_DOT, T_ATTR_NAME_MAPPING);
    public static final TruffleString T_CP_IMPORT_MAPPING = StringUtils.cat(T_MOD_COMPAT_PICKLE, T_DOT, T_ATTR_IMPORT_MAPPING);
    public static final TruffleString T_CP_REVERSE_NAME_MAPPING = StringUtils.cat(T_MOD_COMPAT_PICKLE, T_DOT, T_ATTR_REVERSE_NAME_MAPPING);
    public static final TruffleString T_CP_REVERSE_IMPORT_MAPPING = StringUtils.cat(T_MOD_COMPAT_PICKLE, T_DOT, T_ATTR_REVERSE_IMPORT_MAPPING);
    public static final TruffleString T_METHOD_ENCODE = tsLiteral("encode");
    public static final TruffleString T_METHOD_PARTIAL = tsLiteral("partial");
    public static final TruffleString T_METHOD_READ = StringLiterals.T_READ;
    public static final TruffleString T_METHOD_READLINE = StringLiterals.T_READLINE;
    public static final TruffleString T_METHOD_READINTO = tsLiteral("readinto");
    public static final TruffleString T_METHOD_PEEK = tsLiteral("peek");
    public static final TruffleString T_METHOD_WRITE = tsLiteral("write");

    public static final String J_METHOD_LOAD = "load";
    public static final String J_METHOD_DUMP = "dump";

    public static final String J_METHOD_PERSISTENT_ID = "persistent_id";
    public static final TruffleString T_METHOD_PERSISTENT_ID = tsLiteral(J_METHOD_PERSISTENT_ID);

    public static final String J_METHOD_PERSISTENT_LOAD = "persistent_load";
    public static final TruffleString T_METHOD_PERSISTENT_LOAD = tsLiteral(J_METHOD_PERSISTENT_LOAD);

    public static final String J_METHOD_FIND_CLASS = "find_class";
    public static final String J_METHOD_CLEAR_MEMO = "clear_memo";

    public static final String J_ATTR_DISPATCH_TABLE = "dispatch_table";
    public static final TruffleString T_ATTR_DISPATCH_TABLE = tsLiteral(J_ATTR_DISPATCH_TABLE);

    public static final String J_ATTR_MEMO = "memo";
    public static final String J_ATTR_FAST = "fast";
    public static final String J_ATTR_BIN = "bin";

    public static final int READ_WHOLE_LINE = -1;

    public static final int PICKLE_PROTOCOL_HIGHEST = 5;
    public static final String J_DEFAULT_PICKLE_PROTOCOL = "4";

    // Keep in sync with pickle.Pickler._BATCHSIZE. This is how many elements
    // batch_list/dict() pumps out before doing APPENDS/SETITEMS. Nothing will
    // break if this gets out of sync with pickle.py, but it's unclear that would
    // help anything either.
    public static final int BATCHSIZE = 1000;
    // Nesting limit until Pickler, when running in "fast mode", starts
    // checking for self-referential data-structures.
    public static final int FAST_NESTING_LIMIT = 50;

    // Initial size of the write buffer of Pickler.
    public static final int WRITE_BUF_SIZE = 4096;
    // Prefetch size when unpickling (disabled on unpeekable streams)
    public static final int PREFETCH = 8192 * 16;

    public static final int FRAME_SIZE_MIN = 4;
    public static final int FRAME_SIZE_TARGET = 64 * 1024;
    public static final int FRAME_HEADER_SIZE = 9;

    public static final byte NO_OPCODE = 0;

    // Pickle opcodes. These must be kept updated with pickle.py
    public static final byte OPCODE_MARK = '(';
    public static final byte OPCODE_STOP = '.';
    public static final byte OPCODE_POP = '0';
    public static final byte OPCODE_POP_MARK = '1';
    public static final byte OPCODE_DUP = '2';
    public static final byte OPCODE_FLOAT = 'F';
    public static final byte OPCODE_INT = 'I';
    public static final byte OPCODE_BININT = 'J';
    public static final byte OPCODE_BININT1 = 'K';
    public static final byte OPCODE_LONG = 'L';
    public static final byte OPCODE_BININT2 = 'M';
    public static final byte OPCODE_NONE = 'N';
    public static final byte OPCODE_PERSID = 'P';
    public static final byte OPCODE_BINPERSID = 'Q';
    public static final byte OPCODE_REDUCE = 'R';
    public static final byte OPCODE_STRING = 'S';
    public static final byte OPCODE_BINSTRING = 'T';
    public static final byte OPCODE_SHORT_BINSTRING = 'U';
    public static final byte OPCODE_UNICODE = 'V';
    public static final byte OPCODE_BINUNICODE = 'X';
    public static final byte OPCODE_APPEND = 'a';
    public static final byte OPCODE_BUILD = 'b';
    public static final byte OPCODE_GLOBAL = 'c';
    public static final byte OPCODE_DICT = 'd';
    public static final byte OPCODE_EMPTY_DICT = '}';
    public static final byte OPCODE_APPENDS = 'e';
    public static final byte OPCODE_GET = 'g';
    public static final byte OPCODE_BINGET = 'h';
    public static final byte OPCODE_INST = 'i';
    public static final byte OPCODE_LONG_BINGET = 'j';
    public static final byte OPCODE_LIST = 'l';
    public static final byte OPCODE_EMPTY_LIST = ']';
    public static final byte OPCODE_OBJ = 'o';
    public static final byte OPCODE_PUT = 'p';
    public static final byte OPCODE_BINPUT = 'q';
    public static final byte OPCODE_LONG_BINPUT = 'r';
    public static final byte OPCODE_SETITEM = 's';
    public static final byte OPCODE_TUPLE = 't';
    public static final byte OPCODE_EMPTY_TUPLE = ')';
    public static final byte OPCODE_SETITEMS = 'u';
    public static final byte OPCODE_BINFLOAT = 'G';

    // = Protocol;
    public static final byte OPCODE_PROTO = (byte) 0x80;
    public static final byte OPCODE_NEWOBJ = (byte) 0x81;
    public static final byte OPCODE_EXT1 = (byte) 0x82;
    public static final byte OPCODE_EXT2 = (byte) 0x83;
    public static final byte OPCODE_EXT4 = (byte) 0x84;
    public static final byte OPCODE_TUPLE1 = (byte) 0x85;
    public static final byte OPCODE_TUPLE2 = (byte) 0x86;
    public static final byte OPCODE_TUPLE3 = (byte) 0x87;
    public static final byte OPCODE_NEWTRUE = (byte) 0x88;
    public static final byte OPCODE_NEWFALSE = (byte) 0x89;
    public static final byte OPCODE_LONG1 = (byte) 0x8a;
    public static final byte OPCODE_LONG4 = (byte) 0x8b;

    // = Protocol 3 (Python 3.;
    public static final byte OPCODE_BINBYTES = 'B';
    public static final byte OPCODE_SHORT_BINBYTES = 'C';

    // = Protocol;
    public static final byte OPCODE_SHORT_BINUNICODE = (byte) 0x8c;
    public static final byte OPCODE_BINUNICODE8 = (byte) 0x8d;
    public static final byte OPCODE_BINBYTES8 = (byte) 0x8e;
    public static final byte OPCODE_EMPTY_SET = (byte) 0x8f;
    public static final byte OPCODE_ADDITEMS = (byte) 0x90;
    public static final byte OPCODE_FROZENSET = (byte) 0x91;
    public static final byte OPCODE_NEWOBJ_EX = (byte) 0x92;
    public static final byte OPCODE_STACK_GLOBAL = (byte) 0x93;
    public static final byte OPCODE_MEMOIZE = (byte) 0x94;
    public static final byte OPCODE_FRAME = (byte) 0x95;

    // = Protocol;
    public static final byte OPCODE_BYTEARRAY8 = (byte) 0x96;
    public static final byte OPCODE_NEXT_BUFFER = (byte) 0x97;
    public static final byte OPCODE_READONLY_BUFFER = (byte) 0x98;

    public static final TruffleString T_PROTO_LE2_TRUE = tsLiteral("I01\n");
    public static final TruffleString T_PROTO_LE2_FALSE = tsLiteral("I00\n");

    @ExplodeLoop
    public static void writeSize64(byte[] out, int start, int value) {
        final int sizeofSizeT = Integer.BYTES;
        for (int i = 0; i < sizeofSizeT; i++) {
            out[start + i] = (byte) ((value >> (8 * i)) & 0xff);
        }
        for (int i = sizeofSizeT; i < 8; i++) {
            out[start + i] = 0;
        }
    }

    public static byte[] resize(byte[] src, int newSize) {
        byte[] newBytes = new byte[newSize];
        if (src != null) {
            PythonUtils.arraycopy(src, 0, newBytes, 0, Math.min(newSize, src.length));
        }
        return newBytes;
    }

    private static int arrayCopyWithNewLine(byte[] dst, int offset, byte[] src) {
        PythonUtils.arraycopy(src, 0, dst, offset, src.length);
        int len = src.length + 2;
        dst[len - 1] = NEW_LINE_BYTE;
        return len;
    }

    public static int toAsciiBytesWithNewLine(byte[] dst, int offset, long value, TruffleString.FromLongNode fromLongNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        TruffleString s = fromLongNode.execute(value, TruffleString.Encoding.US_ASCII, true);
        byte[] buf = new byte[s.byteLength(TruffleString.Encoding.US_ASCII)];
        copyToByteArrayNode.execute(s, 0, buf, 0, buf.length, TruffleString.Encoding.US_ASCII);
        return arrayCopyWithNewLine(dst, offset, buf);
    }

    public static int getStringSize(byte[] bytes) {
        int len = bytes.length > 0 && bytes[bytes.length - 1] == '\n' ? bytes.length - 1 : bytes.length;
        return len > 0 && bytes[len - 1] == 0 ? len - 1 : len;
    }

    public static TruffleString getValidIntString(byte[] bytes) {
        return getValidIntASCIIString(bytes, TruffleString.FromByteArrayNode.getUncached());
    }

    public static int asciiBytesToInt(byte[] bytes, TruffleString.ParseIntNode parseIntNode, TruffleString.FromByteArrayNode fromByteArrayNode)
                    throws TruffleString.NumberFormatException {
        return parseIntNode.execute(getValidIntASCIIString(bytes, fromByteArrayNode), 10);
    }

    public static long asciiBytesToLong(byte[] bytes, TruffleString.ParseLongNode parseLongNode, TruffleString.FromByteArrayNode fromByteArrayNode)
                    throws TruffleString.NumberFormatException {
        return parseLongNode.execute(getValidIntASCIIString(bytes, fromByteArrayNode), 10);
    }

    private static TruffleString getValidIntASCIIString(byte[] bytes, TruffleString.FromByteArrayNode fromByteArray) {
        return fromByteArray.execute(bytes, 0, getStringSize(bytes), TruffleString.Encoding.US_ASCII, true);
    }

    @TruffleBoundary
    public static TruffleString doubleToAsciiString(double value) {
        // TODO isn't Double.toString(value) enough? [GR-38109]
        return toTruffleStringUncached(new String(Double.toString(value).getBytes(StandardCharsets.US_ASCII), StandardCharsets.US_ASCII));
    }

    @TruffleBoundary
    public static double asciiBytesToDouble(byte[] bytes) {
        int len = bytes[bytes.length - 1] == '\n' ? bytes.length - 1 : bytes.length;
        return Double.parseDouble(new String(bytes, 0, len, StandardCharsets.US_ASCII));
    }

    @TruffleBoundary
    public static double asciiBytesToDouble(byte[] bytes, PRaiseNode raiseNode, PythonBuiltinClassType errorType) {
        try {
            return asciiBytesToDouble(bytes);
        } catch (NumberFormatException nfe) {
            throw raiseNode.raise(errorType);
        }
    }

    public static byte[] encodeUTF8Strict(TruffleString str, TruffleString.SwitchEncodingNode switchEncodingNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                    TruffleString.GetCodeRangeNode getCodeRangeNode) {
        return encodeStrict(str, switchEncodingNode, TruffleString.Encoding.UTF_8, copyToByteArrayNode, getCodeRangeNode);
    }

    public static byte[] encodeStrict(TruffleString ts, TruffleString.SwitchEncodingNode switchEncodingNode, TruffleString.Encoding encoding, TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                    TruffleString.GetCodeRangeNode getCodeRangeNode) {
        // TODO: [GR-39571] TruffleStrings: allow preservation of UTF-16 surrogate
        if (getCodeRangeNode.execute(ts, TS_ENCODING) == TruffleString.CodeRange.BROKEN) {
            return null;
        }
        TruffleString s = switchEncodingNode.execute(ts, encoding);
        byte[] buf = new byte[s.byteLength(encoding)];
        copyToByteArrayNode.execute(s, 0, buf, 0, buf.length, encoding);
        return buf;
    }

    public static TruffleString decodeUTF8Strict(byte[] data, int len, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
        return decodeStrict(data, len, fromByteArrayNode, TruffleString.Encoding.UTF_8, switchEncodingNode);
    }

    public static TruffleString decodeLatin1Strict(byte[] data, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
        return decodeStrict(data, data.length, fromByteArrayNode, TruffleString.Encoding.ISO_8859_1, switchEncodingNode);
    }

    private static TruffleString decodeStrict(byte[] data, int len, TruffleString.FromByteArrayNode fromByteArrayNode, TruffleString.Encoding encoding,
                    TruffleString.SwitchEncodingNode switchEncodingNode) {
        TruffleString ret = fromByteArrayNode.execute(data, 0, len, encoding, true);
        return switchEncodingNode.execute(ret, TS_ENCODING);
    }

    public static byte[] rawUnicodeEscape(TruffleString unicode, TruffleString.CodePointLengthNode codePointLengthNode, TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        int len = codePointLengthNode.execute(unicode, TS_ENCODING);
        ByteArrayBuffer buffer = new ByteArrayBuffer(len);
        for (int i = 0; i < len; i++) {
            final int ch = codePointAtIndexNode.execute(unicode, i, TS_ENCODING);
            if (ch >= 0x10000) {
                // Map 32-bit characters to \Uxxxxxxxx
                buffer.append('\\');
                buffer.append('U');
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 28) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 24) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 20) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 16) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 12) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 8) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 4) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[ch & 15]);
            } else if (ch >= 256 || ch == '\\' || ch == 0 || ch == '\n' || ch == '\r' || ch == 0x1a) {
                // Map 16-bit characters, \\ and \n to \Uxxxx
                buffer.append('\\');
                buffer.append('u');
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 12) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 8) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[(ch >> 4) & 0xf]);
                buffer.append((char) BytesUtils.HEXDIGITS[ch & 15]);
            } else {
                // Copy everything else as-is
                buffer.append((char) ch);
            }
        }
        return buffer.getByteArray();
    }

    public static Pair<Object, Object> initMethodRef(VirtualFrame frame, Node inliningTarget, PyObjectLookupAttr lookup, Object receiver, TruffleString identifier) {
        final Object func = lookup.execute(frame, inliningTarget, receiver, identifier);
        if (func == PNone.NO_VALUE) {
            return Pair.create(null, null);
        }

        if (func instanceof PMethod && ((PMethod) func).getSelf() == receiver) {
            return Pair.create(((PMethod) func).getFunction(), receiver);
        } else if (func instanceof PBuiltinMethod && ((PBuiltinMethod) func).getSelf() == receiver) {
            return Pair.create(((PBuiltinMethod) func).getFunction(), receiver);
        } else {
            return Pair.create(func, null);
        }
    }

    public static Object reconstructMethod(PythonObjectFactory factory, Object func, Object self) {
        if (self != null) {
            return factory.createMethod(self, func);
        }
        return func;
    }
}
