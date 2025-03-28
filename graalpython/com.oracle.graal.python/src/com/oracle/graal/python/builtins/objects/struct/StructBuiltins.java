/* Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_BOOL;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_DOUBLE;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_FLOAT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_HALF_FLOAT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_INT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_PAD_BYTE;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_PASCAL_STRING;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_SHORT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_SIGNED_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_SIZE_T;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_STRING;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_SHORT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_UNSIGNED_SIZE_T;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_VOID_PTR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_BOOL;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_DOUBLE;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_FLOAT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_HALF_FLOAT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_INT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_PAD_BYTE;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_PASCAL_STRING;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_SHORT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_SIGNED_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_SIZE_T;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_STRING;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_UNSIGNED_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_UNSIGNED_LONG_LONG;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_UNSIGNED_SHORT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_UNSIGNED_SIZE_T;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.T_LBL_VOID_PTR;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_BE_STR_OR_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_CHR_IN_STRUCT_FMT;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_CHARACTER;
import static com.oracle.graal.python.nodes.ErrorMessages.REPEAT_COUNT_WITHOUT_FMT;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_ITER_CANNOT_UNPACK_FROM_STRUCT_OF_SIZE_0;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_ITER_UNPACK_REQ_A_BUFFER_OF_A_MUL_OF_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_NOT_ENOUGH_DATA_TO_UNPACK_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_NO_SPACE_TO_PACK_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_OFFSET_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_PACK_EXPECTED_N_ITEMS_GOT_K;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_PACK_INTO_REQ_BUFFER_TO_PACK;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_UNPACK_FROM_REQ_AT_LEAST_N_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.UNPACK_REQ_A_BUFFER_OF_N_BYTES;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StructError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.StructModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PStructUnpackIterator;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PStruct)
public class StructBuiltins extends PythonBuiltins {
    static void packInternal(VirtualFrame frame, PStruct self, StructNodes.PackValueNode packValueNode, Object[] args, byte[] buffer, int offset) {
        assert self.getSize() <= buffer.length - offset;
        FormatCode[] codes = self.getCodes();
        int pos = 0;
        for (FormatCode code : codes) {
            int buffer_offset = offset + code.offset;
            for (int j = 0; j < code.repeat; j++, pos++) {
                packValueNode.execute(frame, code, self.formatAlignment, args[pos], buffer, buffer_offset);
                buffer_offset += code.size;
            }
        }
    }

    public static Object[] unpackInternal(PStruct self, StructNodes.UnpackValueNode unpackValueNode, byte[] bytes, int offset) {
        Object[] values = new Object[self.getLen()];
        FormatCode[] codes = self.getCodes();
        int pos = 0;
        for (FormatCode code : codes) {
            int buffer_offset = offset + code.offset;
            for (int j = 0; j < code.repeat; j++, pos++) {
                Object value = unpackValueNode.execute(code, self.formatAlignment, bytes, buffer_offset);
                values[pos] = value;
                buffer_offset += code.size;
            }
        }
        return values;
    }

    public static final TpSlots SLOTS = StructBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructBuiltinsFactory.getFactories();
    }

    @ImportStatic(PythonUtils.class)
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "Struct", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ConstructStructNode extends PythonBinaryBuiltinNode {
        public static final int NUM_BYTES_LIMIT;

        private static final int SHORT_ALIGN = Short.BYTES;
        private static final int INT_ALIGN = Integer.BYTES;
        private static final int LONG_ALIGN = Long.BYTES;
        private static final int FLOAT_ALIGN = Float.BYTES;
        private static final int DOUBLE_ALIGN = Double.BYTES;

        private static final char ALIGNMENT_NATIVE_NATIVE = '@';
        private static final char ALIGNMENT_NATIVE_STD = '=';
        private static final char ALIGNMENT_LE_STD = '<';
        private static final char ALIGNMENT_BE_STD = '>';
        private static final char ALIGNMENT_NET_BE_STD = '!';
        private static final char DEFAULT_ALIGNMENT = ALIGNMENT_NATIVE_NATIVE;
        // format def tables
        private static final FormatDef[] FMT_TABLE = new FormatDef[128];
        private static final FormatDef[] FMT_TABLE_NATIVE = new FormatDef[128];
        static {
            Set<Integer> numBytes = new HashSet<>();

            setFormatDefEntry(FMT_TABLE, FMT_PAD_BYTE, T_LBL_PAD_BYTE, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_SIGNED_CHAR, T_LBL_SIGNED_CHAR, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_UNSIGNED_CHAR, T_LBL_UNSIGNED_CHAR, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_CHAR, T_LBL_CHAR, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_STRING, T_LBL_STRING, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_PASCAL_STRING, T_LBL_PASCAL_STRING, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_SHORT, T_LBL_SHORT, 2, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_UNSIGNED_SHORT, T_LBL_UNSIGNED_SHORT, 2, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_INT, T_LBL_INT, 4, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_UNSIGNED_INT, T_LBL_UNSIGNED_INT, 4, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_LONG, T_LBL_LONG, 4, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_UNSIGNED_LONG, T_LBL_UNSIGNED_LONG, 4, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_LONG_LONG, T_LBL_LONG_LONG, 8, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_UNSIGNED_LONG_LONG, T_LBL_UNSIGNED_LONG_LONG, 8, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_BOOL, T_LBL_BOOL, 1, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_HALF_FLOAT, T_LBL_HALF_FLOAT, 2, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_FLOAT, T_LBL_FLOAT, 4, numBytes);
            setFormatDefEntry(FMT_TABLE, FMT_DOUBLE, T_LBL_DOUBLE, 8, numBytes);

            // native format table
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_PAD_BYTE, T_LBL_PAD_BYTE, Byte.BYTES, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_SIGNED_CHAR, T_LBL_SIGNED_CHAR, Byte.BYTES, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_UNSIGNED_CHAR, T_LBL_UNSIGNED_CHAR, Byte.BYTES, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_CHAR, T_LBL_CHAR, Byte.BYTES, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_STRING, T_LBL_STRING, Byte.BYTES, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_PASCAL_STRING, T_LBL_PASCAL_STRING, Byte.BYTES, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_SHORT, T_LBL_SHORT, Short.BYTES, SHORT_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_UNSIGNED_SHORT, T_LBL_UNSIGNED_SHORT, Short.BYTES, SHORT_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_INT, T_LBL_INT, Integer.BYTES, INT_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_UNSIGNED_INT, T_LBL_UNSIGNED_INT, Integer.BYTES, INT_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_LONG, T_LBL_LONG,
                            PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? Integer.BYTES : Long.BYTES,
                            PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? INT_ALIGN : LONG_ALIGN,
                            numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_UNSIGNED_LONG, T_LBL_UNSIGNED_LONG,
                            PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? Integer.BYTES : Long.BYTES,
                            PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? INT_ALIGN : LONG_ALIGN,
                            numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_SIZE_T, T_LBL_SIZE_T, Long.BYTES, LONG_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_UNSIGNED_SIZE_T, T_LBL_UNSIGNED_SIZE_T, Long.BYTES, LONG_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_LONG_LONG, T_LBL_LONG_LONG, Long.BYTES, LONG_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_UNSIGNED_LONG_LONG, T_LBL_UNSIGNED_LONG_LONG, Long.BYTES, LONG_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_BOOL, T_LBL_BOOL, Byte.BYTES, 0, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_HALF_FLOAT, T_LBL_HALF_FLOAT, Float.BYTES / 2, SHORT_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_FLOAT, T_LBL_FLOAT, Float.BYTES, FLOAT_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_DOUBLE, T_LBL_DOUBLE, Double.BYTES, DOUBLE_ALIGN, numBytes);
            setFormatDefEntry(FMT_TABLE_NATIVE, FMT_VOID_PTR, T_LBL_VOID_PTR, Long.BYTES, LONG_ALIGN, numBytes);

            NUM_BYTES_LIMIT = numBytes.size();
        }

        static void setFormatDefEntry(FormatDef[] table, char format, TruffleString label, int size, Set<Integer> numBytes) {
            setFormatDefEntry(table, format, label, size, 0, numBytes);
        }

        static void setFormatDefEntry(FormatDef[] table, char format, TruffleString label, int size, int alignment, Set<Integer> numBytes) {
            table[format] = new FormatDef(format, label, size, alignment);
            numBytes.add(size);
        }

        public final PStruct execute(Object format) {
            return execute(PythonBuiltinClassType.PStruct, format);
        }

        public abstract PStruct execute(Object cls, Object format);

        @Specialization(guards = "isAscii(format, getCodeRangeNode)")
        static PStruct struct(@SuppressWarnings("unused") Object cls, TruffleString format,
                        @Bind("this") Node inliningTarget,
                        @Cached.Shared @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached.Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @SuppressWarnings("unused") @Cached.Shared @Cached TruffleString.GetCodeRangeNode getCodeRangeNode) {
            byte[] fmt = PythonUtils.getAsciiBytes(format, copyToByteArrayNode, switchEncodingNode);
            return PFactory.createStruct(PythonLanguage.get(inliningTarget), createStructInternal(inliningTarget, fmt));
        }

        @Specialization
        static PStruct struct(Object cls, PString format,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached.Shared @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached.Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @SuppressWarnings("unused") @Cached.Shared @Cached TruffleString.GetCodeRangeNode getCodeRangeNode) {
            return struct(cls, castToTruffleStringNode.execute(inliningTarget, format), inliningTarget, copyToByteArrayNode, switchEncodingNode, getCodeRangeNode);
        }

        @Specialization(limit = "1")
        static PStruct struct(@SuppressWarnings("unused") Object cls, PBytes format,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("format") PythonBufferAccessLibrary bufferLib) {
            byte[] fmt = bufferLib.getCopiedByteArray(format);
            return PFactory.createStruct(PythonLanguage.get(inliningTarget), createStructInternal(inliningTarget, fmt));
        }

        @Specialization(guards = {"!isPBytes(format)", "!isPString(format)", "!isAsciiTruffleString(format, getCodeRangeNode)"})
        static PStruct fallback(@SuppressWarnings("unused") Object cls, Object format,
                        @SuppressWarnings("unused") @Cached.Shared @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, StructError, ARG_MUST_BE_STR_OR_BYTES, "Struct()", format);
        }

        protected static boolean isAsciiTruffleString(Object o, TruffleString.GetCodeRangeNode getCodeRangeNode) {
            return o instanceof TruffleString && PythonUtils.isAscii((TruffleString) o, getCodeRangeNode);
        }

        @CompilerDirectives.TruffleBoundary
        private static PStruct.StructInfo createStructInternal(Node raisingNode, byte[] format) {
            int size = 0;
            int len = 0;
            int nCodes = 0;
            int num;

            if (StructModuleBuiltins.containsNullCharacter(format)) {
                throw PRaiseNode.raiseStatic(raisingNode, PythonBuiltinClassType.StructError, EMBEDDED_NULL_CHARACTER);
            }

            char alignment = DEFAULT_ALIGNMENT;
            int start = 0;

            if (format.length > 0 && isAlignment((char) format[0])) {
                alignment = (char) format[0];
                start = 1;
            }

            final FormatAlignment formatAlignment = whichAlignment(alignment);
            FormatDef[] formatTable = (formatAlignment.nativeSizing) ? FMT_TABLE_NATIVE : FMT_TABLE;

            // first pass: validation
            for (int i = start; i < format.length; i++) {
                char c = (char) format[i];
                if (c == ' ') {
                    continue;
                } else if ('0' <= c && c <= '9') {
                    num = c - '0';
                    while (++i < format.length && '0' <= (c = (char) format[i]) && c <= '9') {
                        if (num >= Integer.MAX_VALUE / 10 && (num > Integer.MAX_VALUE / 10 || (c - '0') > Integer.MAX_VALUE % 10)) {
                            throw PRaiseNode.raiseStatic(raisingNode, StructError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
                        }
                        num = num * 10 + (c - '0');
                    }
                    if (i == format.length) {
                        throw PRaiseNode.raiseStatic(raisingNode, StructError, REPEAT_COUNT_WITHOUT_FMT);
                    }
                } else {
                    num = 1;
                }

                FormatDef formatDef = getEntry(raisingNode, c, formatTable);

                switch (c) {
                    case 's': // fall through
                    case 'p':
                        len++;
                        nCodes++;
                        break;
                    case 'x':
                        break;
                    default:
                        len += num;
                        if (num != 0) {
                            nCodes++;
                        }
                        break;
                }

                int itemSize = formatDef.size;
                size = align(size, c, formatDef);
                if (size == -1) {
                    throw PRaiseNode.raiseStatic(raisingNode, StructError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
                }

                if (num > (Integer.MAX_VALUE - size) / itemSize) {
                    throw PRaiseNode.raiseStatic(raisingNode, StructError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
                }
                size += num * itemSize;
            }

            // second pass - fill in the codes (no validation needed)
            FormatCode[] codes = new FormatCode[nCodes];
            int structSize = size;
            int structLen = len;

            int j = 0;
            size = 0;
            for (int i = start; i < format.length; i++) {
                char c = (char) format[i];
                if (c == ' ') {
                    continue;
                } else if ('0' <= c && c <= '9') {
                    num = c - '0';
                    while (++i < format.length && '0' <= (c = (char) format[i]) && c <= '9') {
                        num = num * 10 + (c - '0');
                    }
                } else {
                    num = 1;
                }

                FormatDef formatDef = getEntry(raisingNode, c, formatTable);
                size = align(size, c, formatDef);
                if (c == 's' || c == 'p') {
                    codes[j++] = new FormatCode(formatDef, size, num, 1);
                    size += num;
                } else if (c == 'x') {
                    size += num;
                } else if (num != 0) {
                    codes[j++] = new FormatCode(formatDef, size, formatDef.size, num);
                    size += formatDef.size * num;
                }
            }

            return new PStruct.StructInfo(format, structSize, structLen, formatAlignment, codes);
        }

        private static FormatDef getEntry(Node raisingNode, char format, FormatDef[] table) {
            FormatDef formatDef = table[format];
            if (formatDef != null) {
                return formatDef;
            }
            throw PRaiseNode.raiseStatic(raisingNode, StructError, BAD_CHR_IN_STRUCT_FMT, format);
        }

        private static boolean isAlignment(char alignment) {
            return alignment == ALIGNMENT_LE_STD ||
                            alignment == ALIGNMENT_BE_STD ||
                            alignment == ALIGNMENT_NET_BE_STD ||
                            alignment == ALIGNMENT_NATIVE_STD ||
                            alignment == ALIGNMENT_NATIVE_NATIVE;
        }

        private static FormatAlignment whichAlignment(char alignment) {
            switch (alignment) {
                case ALIGNMENT_LE_STD:
                    return new FormatAlignment(false, false);
                case ALIGNMENT_BE_STD:
                case ALIGNMENT_NET_BE_STD:
                    return new FormatAlignment(true, false);
                case ALIGNMENT_NATIVE_STD:
                    return new FormatAlignment(ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN, false);
                case ALIGNMENT_NATIVE_NATIVE:
                default:
                    return new FormatAlignment(ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN, true);
            }
        }

        private static int align(int size, char c, FormatDef formatDef) {
            int extra;
            int alignedSize = size;
            if (formatDef.format == c) {
                if (formatDef.alignment > 0 && alignedSize > 0) {
                    extra = (formatDef.alignment - 1) - (alignedSize - 1) % (formatDef.alignment);
                    if (extra > Integer.MAX_VALUE - alignedSize) {
                        return -1;
                    }
                    alignedSize += extra;
                }
            }
            return alignedSize;
        }
    }

    @Builtin(name = "pack", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, takesVarArgs = true, takesVarKeywordArgs = true, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    public abstract static class StructPackNode extends PythonVarargsBuiltinNode {
        public final Object execute(VirtualFrame frame, PStruct self, Object[] args) {
            return execute(frame, self, args, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization
        static Object pack(VirtualFrame frame, PStruct self, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached StructNodes.PackValueNode packValueNode,
                        @Cached PRaiseNode raiseNode) {
            if (keywords.length != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, S_TAKES_NO_KEYWORD_ARGS, "pack()");
            }
            if (args.length != self.getLen()) {
                throw raiseNode.raise(inliningTarget, StructError, STRUCT_PACK_EXPECTED_N_ITEMS_GOT_K, self.getLen(), args.length);
            }
            byte[] bytes = new byte[self.getSize()];
            packInternal(frame, self, packValueNode, args, bytes, 0);
            return PFactory.createBytes(language, bytes);
        }
    }

    @Builtin(name = "pack_into", minNumOfPositionalArgs = 3, parameterNames = {"$self", "buffer", "offset"}, takesVarArgs = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.WritableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class StructPackIntoNode extends PythonClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer, int offset, Object[] args);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructPackIntoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object packInto(VirtualFrame frame, PStruct self, Object buffer, int offset, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached StructNodes.PackValueNode packValueNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                final long size = self.getUnsignedSize();
                if (args.length != self.getLen()) {
                    throw raiseNode.raise(inliningTarget, StructError, STRUCT_PACK_EXPECTED_N_ITEMS_GOT_K, size, args.length);
                }
                int bufferOffset = offset;
                int bufferLen = bufferLib.getBufferLength(buffer);
                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                byte[] bytes;
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    bytes = new byte[self.getSize()];
                }

                // support negative offsets
                if (bufferOffset < 0) {
                    // Check that negative offset is low enough to fit data
                    if (bufferOffset + size > 0) {
                        throw raiseNode.raise(inliningTarget, StructError, STRUCT_NO_SPACE_TO_PACK_N_BYTES, size, bufferOffset);
                    }

                    // Check that negative offset is not crossing buffer boundary
                    if (bufferOffset + bufferLen < 0) {
                        throw raiseNode.raise(inliningTarget, StructError, STRUCT_OFFSET_OUT_OF_RANGE, bufferOffset, bufferLen);
                    }

                    bufferOffset += bufferLen;
                }

                // Check boundaries
                if ((bufferLen - bufferOffset) < size) {
                    assert bufferOffset >= 0;
                    assert size >= 0;

                    throw raiseNode.raise(inliningTarget, StructError, STRUCT_PACK_INTO_REQ_BUFFER_TO_PACK, size + bufferOffset, size, bufferOffset, bufferLen);
                }

                // TODO: GR-54860 use buffer API in the packing process
                packInternal(frame, self, packValueNode, args, bytes, directWrite ? bufferOffset : 0);
                if (!directWrite) {
                    bufferLib.writeFromByteArray(buffer, bufferOffset, bytes, 0, bytes.length);
                }
                return PNone.NONE;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @NeverDefault
        public static StructPackIntoNode create() {
            return StructBuiltinsFactory.StructPackIntoNodeFactory.create(null);
        }
    }

    @Builtin(name = "unpack", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"}, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class StructUnpackNode extends PythonBinaryClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructUnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object unpack(VirtualFrame frame, PStruct self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached StructNodes.UnpackValueNode unpackValueNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int bytesLen = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                if (bytesLen != self.getSize()) {
                    throw raiseNode.raise(inliningTarget, StructError, UNPACK_REQ_A_BUFFER_OF_N_BYTES, self.getSize());
                }
                return PFactory.createTuple(language, unpackInternal(self, unpackValueNode, bytes, 0));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "iter_unpack", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer"}, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class StructIterUnpackNode extends PythonBinaryClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructIterUnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object iterUnpack(VirtualFrame frame, PStruct self, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (self.getSize() == 0) {
                    throw raiseNode.raise(inliningTarget, StructError, STRUCT_ITER_CANNOT_UNPACK_FROM_STRUCT_OF_SIZE_0);
                }
                int bufferLen = bufferLib.getBufferLength(buffer);
                if (bufferLen % self.getSize() != 0) {
                    throw raiseNode.raise(inliningTarget, StructError, STRUCT_ITER_UNPACK_REQ_A_BUFFER_OF_A_MUL_OF_BYTES, self.getSize());
                }
            } catch (Exception e) {
                bufferLib.release(buffer, frame, indirectCallData);
                throw e;
            }
            // The buffer ownership is transferred to the iterator
            // TODO: GR-54860 release it when iterator is collected
            final PStructUnpackIterator structUnpackIterator = PFactory.createStructUnpackIterator(language, self, buffer);
            structUnpackIterator.index = 0;
            return structUnpackIterator;
        }
    }

    @Builtin(name = "unpack_from", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "offset"}, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    public abstract static class StructUnpackFromNode extends PythonTernaryClinicBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PStruct self, Object buffer, int offset);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructBuiltinsClinicProviders.StructUnpackFromNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object unpackFrom(VirtualFrame frame, PStruct self, Object buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached StructNodes.UnpackValueNode unpackValueNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                int bufferOffset = offset;
                int bytesLen = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                final long size = self.getUnsignedSize();
                if (bufferOffset < 0) {
                    if (bufferOffset + size > 0) {
                        throw raiseNode.raise(inliningTarget, StructError, STRUCT_NOT_ENOUGH_DATA_TO_UNPACK_N_BYTES, size, bufferOffset);
                    }

                    if (bufferOffset + bytesLen < 0) {
                        throw raiseNode.raise(inliningTarget, StructError, STRUCT_OFFSET_OUT_OF_RANGE, bufferOffset, bytesLen);
                    }
                    bufferOffset += bytesLen;
                }

                if ((bytesLen - bufferOffset) < size) {
                    throw raiseNode.raise(inliningTarget, StructError, STRUCT_UNPACK_FROM_REQ_AT_LEAST_N_BYTES, size + bufferOffset, size, bufferOffset, bytesLen);
                }

                return PFactory.createTuple(language, unpackInternal(self, unpackValueNode, bytes, bufferOffset));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = "calcsize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StructCalcSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object calcSize(PStruct self) {
            return self.getSize();
        }
    }

    @Builtin(name = "size", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStructSizeNode extends PythonBuiltinNode {
        @Specialization
        protected Object get(PStruct self) {
            return self.getSize();
        }
    }

    @Builtin(name = "format", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetStructFormat extends PythonBuiltinNode {
        @Specialization
        protected Object get(PStruct self,
                        @Cached TruffleString.FromByteArrayNode fromBytes,
                        @Cached TruffleString.SwitchEncodingNode switchEncoding) {
            return switchEncoding.execute(fromBytes.execute(self.getFormat(), TruffleString.Encoding.US_ASCII), TS_ENCODING);
        }
    }
}
