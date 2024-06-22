/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_BE_STR_OR_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_CHR_IN_STRUCT_FMT;
import static com.oracle.graal.python.nodes.ErrorMessages.REPEAT_COUNT_WITHOUT_FMT;
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
import static com.oracle.graal.python.nodes.BuiltinNames.J__STRUCT;
import static com.oracle.graal.python.nodes.BuiltinNames.T__STRUCT;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_CHARACTER;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StructError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.struct.FormatAlignment;
import com.oracle.graal.python.builtins.objects.struct.FormatCode;
import com.oracle.graal.python.builtins.objects.struct.FormatDef;
import com.oracle.graal.python.builtins.objects.struct.PStruct;
import com.oracle.graal.python.builtins.objects.struct.StructBuiltins;
import com.oracle.graal.python.util.LRUCache;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__STRUCT, isEager = true)
public class StructModuleBuiltins extends PythonBuiltins {
    private static final int DEFAULT_CACHE_SIZE = 100;
    private static final TruffleString T_ERROR = tsLiteral("error");
    private final LRUStructCache cache = new LRUStructCache(DEFAULT_CACHE_SIZE);

    static class LRUStructCache extends LRUCache<Object, PStruct> {
        public LRUStructCache(int size) {
            super(size);
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(T_ERROR, StructError);
        super.initialize(core);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule structModule = core.lookupBuiltinModule(T__STRUCT);
        structModule.setModuleState(cache);
    }

    @ImportStatic(PythonUtils.class)
    @Builtin(name = "Struct", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PStruct)
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
                        @Shared @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @SuppressWarnings("unused") @Shared @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] fmt = PythonUtils.getAsciiBytes(format, copyToByteArrayNode, switchEncodingNode);
            return factory.createStruct(createStructInternal(inliningTarget, fmt));
        }

        @Specialization
        static PStruct struct(Object cls, PString format,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Shared @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @SuppressWarnings("unused") @Shared @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return struct(cls, castToTruffleStringNode.execute(inliningTarget, format), inliningTarget, copyToByteArrayNode, switchEncodingNode, getCodeRangeNode, factory);
        }

        @Specialization(limit = "1")
        static PStruct struct(@SuppressWarnings("unused") Object cls, PBytes format,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("format") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] fmt = bufferLib.getCopiedByteArray(format);
            return factory.createStruct(createStructInternal(inliningTarget, fmt));
        }

        @Specialization(guards = {"!isPBytes(format)", "!isPString(format)", "!isAsciiTruffleString(format, getCodeRangeNode)"})
        static PStruct fallback(@SuppressWarnings("unused") Object cls, Object format,
                        @SuppressWarnings("unused") @Shared @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(StructError, ARG_MUST_BE_STR_OR_BYTES, "Struct()", format);
        }

        protected static boolean isAsciiTruffleString(Object o, TruffleString.GetCodeRangeNode getCodeRangeNode) {
            return o instanceof TruffleString && PythonUtils.isAscii((TruffleString) o, getCodeRangeNode);
        }

        @TruffleBoundary
        private static PStruct.StructInfo createStructInternal(Node raisingNode, byte[] format) {
            int size = 0;
            int len = 0;
            int nCodes = 0;
            int num;

            if (containsNullCharacter(format)) {
                throw PRaiseNode.raiseUncached(raisingNode, PythonBuiltinClassType.StructError, EMBEDDED_NULL_CHARACTER);
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
                            throw PRaiseNode.raiseUncached(raisingNode, StructError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
                        }
                        num = num * 10 + (c - '0');
                    }
                    if (i == format.length) {
                        throw PRaiseNode.raiseUncached(raisingNode, StructError, REPEAT_COUNT_WITHOUT_FMT);
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
                    throw PRaiseNode.raiseUncached(raisingNode, StructError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
                }

                if (num > (Integer.MAX_VALUE - size) / itemSize) {
                    throw PRaiseNode.raiseUncached(raisingNode, StructError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
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
            throw PRaiseNode.raiseUncached(raisingNode, StructError, BAD_CHR_IN_STRUCT_FMT, format);
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

    protected static PStruct getStruct(PythonModule structModule, Object format, ConstructStructNode constructStructNode) {
        LRUStructCache cache = structModule.getModuleState(LRUStructCache.class);
        PStruct pStruct = cache.get(format);
        if (pStruct == null) {
            pStruct = constructStructNode.execute(format);
            cache.put(format, pStruct);
        }
        return pStruct;
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({Arrays.class})
    abstract static class GetStructNode extends PNodeWithContext {
        abstract PStruct execute(Node inliningTarget, PythonModule module, Object format, ConstructStructNode constructStructNode);

        protected PStruct getStructInternal(PythonModule module, Object format, ConstructStructNode constructStructNode) {
            return getStruct(module, format, constructStructNode);
        }

        protected boolean eq(TruffleString s1, TruffleString s2, TruffleString.EqualNode eqNode) {
            return eqNode.execute(s1, s2, TS_ENCODING);
        }

        @Specialization(guards = {"isSingleContext()", "eq(format, cachedFormat, eqNode)"}, limit = "1")
        @SuppressWarnings("unused")
        static PStruct doCachedString(PythonModule module, TruffleString format, ConstructStructNode constructStructNode,
                        @Cached("format") TruffleString cachedFormat,
                        @Cached(inline = false) TruffleString.EqualNode eqNode,
                        @Cached(value = "getStructInternal(module, format, constructStructNode)", weak = true) PStruct cachedStruct) {
            return cachedStruct;
        }

        @Specialization(guards = {"isSingleContext()", "equals(bufferLib.getCopiedByteArray(format), cachedFormat)"}, limit = "1")
        @SuppressWarnings("unused")
        static PStruct doCachedBytes(PythonModule module, PBytes format, ConstructStructNode constructStructNode,
                        @CachedLibrary("format") PythonBufferAccessLibrary bufferLib,
                        @Cached(value = "bufferLib.getCopiedByteArray(format)", dimensions = 1) byte[] cachedFormat,
                        @Cached(value = "getStructInternal(module, format, constructStructNode)", weak = true) PStruct cachedStruct) {
            return cachedStruct;
        }

        @Specialization(replaces = {"doCachedString", "doCachedBytes"})
        static PStruct doGeneric(PythonModule module, Object format, ConstructStructNode constructStructNode) {
            return getStruct(module, format, constructStructNode);
        }
    }

    @Builtin(name = "pack", minNumOfPositionalArgs = 2, parameterNames = {"$self", "format"}, takesVarArgs = true, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    abstract static class PackNode extends PythonBuiltinNode {
        @Specialization
        static Object pack(VirtualFrame frame, PythonModule self, Object format, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructPackNode structPackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structPackNode.execute(frame, struct, args);
        }
    }

    @Builtin(name = "pack_into", minNumOfPositionalArgs = 4, parameterNames = {"$self", "format", "buffer", "offset"}, declaresExplicitSelf = true, takesVarArgs = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class PackIntoNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.PackIntoNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object packInto(VirtualFrame frame, PythonModule self, Object format, Object buffer, int offset, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Cached ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructPackIntoNode structPackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structPackNode.execute(frame, struct, buffer, offset, args);
        }
    }

    @Builtin(name = "unpack", minNumOfPositionalArgs = 3, parameterNames = {"$self", "format", "buffer"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class UnpackNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.UnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object unpack(VirtualFrame frame, PythonModule self, Object format, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached GetStructNode getStructNode,
                        @Cached ConstructStructNode constructStructNode,
                        @Cached StructBuiltins.StructUnpackNode structUnpackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structUnpackNode.execute(frame, struct, buffer);
        }
    }

    @Builtin(name = "iter_unpack", minNumOfPositionalArgs = 3, parameterNames = {"$self", "format", "buffer"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IterUnpackNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.IterUnpackNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object iterUnpack(VirtualFrame frame, PythonModule self, Object format, Object buffer,
                        @Bind("this") Node inliningTarget,
                        @Cached ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructIterUnpackNode iterUnpackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return iterUnpackNode.execute(frame, struct, buffer);
        }
    }

    @Builtin(name = "unpack_from", minNumOfPositionalArgs = 3, parameterNames = {"$self", "format", "buffer", "offset"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @ArgumentClinic(name = "buffer", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "offset", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class UnpackFromNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StructModuleBuiltinsClinicProviders.UnpackFromNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object unpackFrom(VirtualFrame frame, PythonModule self, Object format, Object buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode,
                        @Cached StructBuiltins.StructUnpackFromNode structUnpackNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return structUnpackNode.execute(frame, struct, buffer, offset);
        }
    }

    @Builtin(name = "calcsize", minNumOfPositionalArgs = 2, parameterNames = {"$self", "format"}, declaresExplicitSelf = true, forceSplitDirectCalls = true)
    @GenerateNodeFactory
    abstract static class CalcSizeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object calcSize(PythonModule self, Object format,
                        @Bind("this") Node inliningTarget,
                        @Cached ConstructStructNode constructStructNode,
                        @Cached GetStructNode getStructNode) {
            PStruct struct = getStructNode.execute(inliningTarget, self, format, constructStructNode);
            return struct.getSize();
        }
    }

    @Builtin(name = "_clearcache", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ClearCacheNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object clearCache(PythonModule self) {
            LRUStructCache cache = self.getModuleState(LRUStructCache.class);
            cache.clear();
            return PNone.NONE;
        }
    }

    public static boolean containsNullCharacter(byte[] value) {
        for (byte b : value) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }
}
