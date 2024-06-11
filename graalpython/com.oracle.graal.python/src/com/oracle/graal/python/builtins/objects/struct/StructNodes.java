/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.struct;

import static com.oracle.graal.python.builtins.modules.StructModuleBuiltins.ConstructStructNode.NUM_BYTES_LIMIT;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_FOR_N_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_NOT_T;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_O_O_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.FMT_REQ_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_CHR_FMT_BYTES_1;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCT_FMT_NOT_YET_SUPPORTED;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_BOOL;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_CHAR;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_DOUBLE;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_FLOAT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_HALF_FLOAT;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_PASCAL_STRING;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_STRING;
import static com.oracle.graal.python.builtins.objects.struct.FormatCode.FMT_VOID_PTR;
import static com.oracle.graal.python.nodes.PGuards.isBytes;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StructError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.CanBeDoubleNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.NumericSupport;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public final class StructNodes {
    // ------------------------------------------------------------------------------------------------------------
    //
    // utility nodes
    //
    // ------------------------------------------------------------------------------------------------------------
    // static int get_long(PyObject *v, long *p)
    @GenerateInline(false) // footprint reduction 68 -> 50
    public abstract static class GetLongNode extends PNodeWithContext {
        public abstract long execute(VirtualFrame frame, Object value, boolean unsigned);

        @Specialization
        static long get(VirtualFrame frame, Object value, boolean unsigned,
                        @Bind("this") Node inliningTarget,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyLongCheckNode pyLongCheckNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyLongAsLongNode pyLongAsLongNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object longValue;
            if (!pyLongCheckNode.execute(inliningTarget, value)) {
                if (indexCheckNode.execute(inliningTarget, value)) {
                    longValue = indexNode.execute(frame, inliningTarget, value);
                } else {
                    throw raiseNode.get(inliningTarget).raise(StructError, ARG_NOT_T, "an integer");
                }
            } else {
                longValue = value;
            }

            long x;
            try {
                x = pyLongAsLongNode.execute(frame, inliningTarget, longValue);
            } catch (PException pe) {
                pe.expect(inliningTarget, PythonBuiltinClassType.OverflowError, errorProfile);
                throw raiseNode.get(inliningTarget).raise(StructError, ARG_O_O_RANGE);
            }
            if (unsigned && x < 0) {
                throw raiseNode.get(inliningTarget).raise(StructError, ARG_O_O_RANGE);
            }
            return x;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    //
    // struct nodes
    //
    // ------------------------------------------------------------------------------------------------------------
    @ImportStatic({PGuards.class})
    public abstract static class StructBaseNode extends PNodeWithContext {
        public static final BigInteger UBYTE_MASK = BigInteger.valueOf(0xff);
        public static final BigInteger ULONG_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

        public static int getNumBytesLimit() {
            return NUM_BYTES_LIMIT;
        }

        public static boolean isFormat(FormatCode formatCode, char fmt) {
            return formatCode.formatDef.format == fmt;
        }

        public static boolean isNumberSize8Unsigned(FormatCode formatCode) {
            return formatCode.formatDef.integer && (formatCode.numBytes() == 8 && formatCode.isUnsigned());
        }

        public static boolean isNumberUpToSize8Unsigned(FormatCode formatCode) {
            return formatCode.formatDef.integer && !(formatCode.numBytes() == 8 && formatCode.isUnsigned());
        }

        public static boolean isFmtFloat(FormatCode formatCode) {
            return isFormat(formatCode, FMT_HALF_FLOAT) || isFormat(formatCode, FMT_FLOAT) || isFormat(formatCode, FMT_DOUBLE);
        }

        public static boolean isFmtInteger(FormatCode formatCode) {
            return formatCode.formatDef.integer;
        }

        public static boolean isFmtVoidPtr(FormatCode formatCode) {
            return isFormat(formatCode, FMT_VOID_PTR);
        }

        public static boolean isFmtBoolean(FormatCode formatCode) {
            return isFormat(formatCode, FMT_BOOL);
        }

        public static boolean isFmtBytes(FormatCode formatCode) {
            return isFormat(formatCode, FMT_CHAR) || isFormat(formatCode, FMT_STRING) || isFormat(formatCode, FMT_PASCAL_STRING);
        }

        public static boolean isSupportedFormat(FormatCode formatCode) {
            return isFmtBoolean(formatCode) || isFmtBytes(formatCode) || isFmtFloat(formatCode) || isFmtInteger(formatCode) || isFmtVoidPtr(formatCode);
        }

        // checks
        private static PException raiseNumberError(FormatCode formatCode, PRaiseNode raiseNode) {
            if (formatCode.formatDef.name == null) {
                return raiseNode.raise(StructError, ARG_O_O_RANGE);
            }
            return raiseNode.raise(StructError, FMT_REQ_RANGE, formatCode.formatDef.name, formatCode.formatDef.min, formatCode.formatDef.max);
        }

        private static PException raiseNumberErrorUncached(Node raisingNode, FormatCode formatCode) {
            if (formatCode.formatDef.name == null) {
                return PRaiseNode.raiseUncached(raisingNode, StructError, ARG_O_O_RANGE);
            }
            return PRaiseNode.raiseUncached(raisingNode, StructError, FMT_REQ_RANGE, formatCode.formatDef.name, formatCode.formatDef.min, formatCode.formatDef.max);
        }

        public static long checkLong(Node inliningTarget, FormatCode formatCode, long value, PRaiseNode.Lazy raiseNode) {
            if (value < formatCode.formatDef.min || value > formatCode.formatDef.max) {
                throw raiseNumberError(formatCode, raiseNode.get(inliningTarget));
            }
            return value;
        }

        public static long checkLongUnsigned(Node inliningTarget, FormatCode formatCode, long value, PRaiseNode.Lazy raiseNode) {
            if (value < formatCode.formatDef.min || Long.compareUnsigned(value, formatCode.formatDef.max) > 0) {
                throw raiseNumberError(formatCode, raiseNode.get(inliningTarget));
            }
            return value;
        }

        @TruffleBoundary
        public static BigInteger checkBigInt(Node raisingNode, FormatCode formatCode, BigInteger value) {
            if (value.compareTo(BigInteger.valueOf(formatCode.formatDef.min)) < 0 ||
                            value.compareTo(BigInteger.valueOf(formatCode.formatDef.max).and(ULONG_MASK)) > 0) {
                throw raiseNumberErrorUncached(raisingNode, formatCode);
            }
            return value;
        }

        // helpers
        @TruffleBoundary
        public static BigInteger getAsUnsignedBigInt(long value) {
            return BigInteger.valueOf(value).and(ULONG_MASK);
        }

        public static long handleSign(FormatCode code, long num) {
            // handle the sign extension (2's complement)
            long value = num;
            if ((value >>> (code.numBytes() * 8 - 1) & 1) == 1) {
                value |= -(value & (1L << ((8 * code.numBytes()) - 1)));
            }
            return value;
        }

        public static NumericSupport getNumericSupport(FormatAlignment formatAlignment) {
            return formatAlignment.bigEndian ? NumericSupport.bigEndian() : NumericSupport.littleEndian();
        }
    }

    @ImportStatic(FormatCode.class)
    @GenerateInline(false) // footprint reduction 104 -> 85
    public abstract static class PackValueNode extends StructBaseNode {
        public abstract void execute(VirtualFrame frame, FormatCode formatCode, FormatAlignment formatAlignment, Object value, byte[] buffer, int offset);

        private static void packLongInternal(Node inliningTarget, FormatCode formatCode, long value, byte[] buffer, int offset, NumericSupport numericSupport, int numBytes,
                        InlinedConditionProfile profileSigned, PRaiseNode.Lazy raiseNode) {
            final long num;
            if (profileSigned.profile(inliningTarget, formatCode.isUnsigned())) {
                num = checkLongUnsigned(inliningTarget, formatCode, value, raiseNode);
            } else {
                num = checkLong(inliningTarget, formatCode, value, raiseNode);
            }
            numericSupport.putLong(buffer, offset, num, numBytes);
        }

        @Specialization(guards = {"isFmtInteger(formatCode)", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "3")
        static void packLong(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, long value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached InlinedConditionProfile profileSigned,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            packLongInternal(inliningTarget, formatCode, value, buffer, offset, numericSupport, formatCode.numBytes(), profileSigned, raiseNode);
        }

        @Specialization(guards = {"isFmtInteger(formatCode)", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "3")
        static void packInt(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, int value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached InlinedConditionProfile profileSigned,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            packLongInternal(inliningTarget, formatCode, value, buffer, offset, numericSupport, formatCode.numBytes(), profileSigned, raiseNode);
        }

        @Specialization(guards = {"isFmtInteger(formatCode)", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "3")
        static void packPInt(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, PInt value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached CastToJavaBigIntegerNode toJavaBigIntegerNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            try {
                BigInteger num = checkBigInt(inliningTarget, formatCode, toJavaBigIntegerNode.execute(inliningTarget, value));
                numericSupport.putBigInteger(buffer, offset, num, formatCode.numBytes());
            } catch (OverflowException oe) {
                throw raiseNode.get(inliningTarget).raise(StructError, ARG_O_O_RANGE);
            }
        }

        @Specialization(guards = {"isFmtFloat(formatCode)", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "3")
        static void packFloat(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, double value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            numericSupport.putDouble(inliningTarget, buffer, offset, value, formatCode.numBytes(), raiseNode);
        }

        @Specialization(guards = {"isFmtFloat(formatCode)", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "3")
        static void packPFloat(FormatCode formatCode, FormatAlignment formatAlignment, PFloat value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            packFloat(formatCode, formatAlignment, value.getValue(), buffer, offset, inliningTarget, numericSupport, raiseNode);
        }

        @Specialization(guards = "isFmtBoolean(formatCode)")
        static void packBoolean(@SuppressWarnings("unused") FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, boolean value, byte[] buffer, int offset) {
            assert offset < buffer.length;
            buffer[offset] = (byte) (value ? 1 : 0);
        }

        @Specialization(guards = "isFmtBytes(formatCode)", limit = "getCallSiteInlineCacheMaxDepth()")
        static void packBytes(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, PBytesLike value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached("createEqualityProfile()") PrimitiveValueProfile formatProfile,
                        @CachedLibrary("value") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int n = bufferLib.getBufferLength(value);

            switch (formatProfile.profile(formatCode.formatDef.format)) {
                case FMT_CHAR:
                    if (n != 1) {
                        throw raiseNode.get(inliningTarget).raise(StructError, STRUCT_CHR_FMT_BYTES_1);
                    }
                    assert n <= buffer.length - offset;
                    bufferLib.readIntoByteArray(value, 0, buffer, offset, n);
                    break;
                case FMT_STRING:
                    if (n > formatCode.size) {
                        n = formatCode.size;
                    }
                    assert n <= buffer.length - offset;
                    if (n > 0) {
                        bufferLib.readIntoByteArray(value, 0, buffer, offset, n);
                    }
                    break;
                case FMT_PASCAL_STRING:
                default:
                    if (n > (formatCode.size - 1)) {
                        n = formatCode.size - 1;
                    }
                    assert n + 1 <= buffer.length - offset;
                    if (n > 0) {
                        bufferLib.readIntoByteArray(value, 0, buffer, offset + 1, n);
                    }
                    if (n > 255) {
                        n = 255;
                    }
                    buffer[offset] = (byte) n;
            }
        }

        @Specialization(guards = {"numericSupport == getNumericSupport(formatAlignment)"}, limit = "1")
        static void packObjectCached(VirtualFrame frame, FormatCode formatCode, FormatAlignment formatAlignment, Object value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetLongNode getLongNode,
                        @Shared @Cached CastToJavaBigIntegerNode toJavaBigIntegerNode,
                        @Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Shared @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached("createEqualityProfile()") PrimitiveValueProfile formatProfile,
                        @Shared @Cached InlinedConditionProfile profileSigned,
                        @Shared @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (isNumberUpToSize8Unsigned(formatCode)) {
                packLong(formatCode, formatAlignment, getLongNode.execute(frame, value, formatCode.isUnsigned()), buffer, offset, inliningTarget, numericSupport, profileSigned, raiseNode);
            } else if (isNumberSize8Unsigned(formatCode)) {
                if (value instanceof Integer || value instanceof Long) {
                    packLong(formatCode, formatAlignment, getLongNode.execute(frame, value, formatCode.isUnsigned()), buffer, offset, inliningTarget, numericSupport, profileSigned, raiseNode);
                } else {
                    try {
                        BigInteger num = checkBigInt(inliningTarget, formatCode, toJavaBigIntegerNode.execute(inliningTarget, value));
                        getNumericSupport(formatAlignment).putBigInteger(buffer, offset, num, formatCode.numBytes());
                    } catch (OverflowException oe) {
                        throw raiseNode.get(inliningTarget).raise(StructError, ARG_O_O_RANGE);
                    } catch (PException pe) {
                        pe.expect(inliningTarget, PythonBuiltinClassType.TypeError, errorProfile);
                        throw raiseNode.get(inliningTarget).raise(StructError, ARG_NOT_T, "an integer");
                    }
                }
            } else if (isFmtFloat(formatCode)) {
                if (!canBeDoubleNode.execute(inliningTarget, value)) {
                    throw raiseNode.get(inliningTarget).raise(StructError, ARG_NOT_T, "a float");
                }
                packFloat(formatCode, formatAlignment, asDoubleNode.execute(frame, inliningTarget, value), buffer, offset, inliningTarget, numericSupport, raiseNode);
            } else if (isFmtVoidPtr(formatCode)) {
                getLongNode.execute(frame, value, formatCode.isUnsigned());
                packLong(formatCode, formatAlignment, getLongNode.execute(frame, value, formatCode.isUnsigned()), buffer, offset, inliningTarget, numericSupport, profileSigned, raiseNode);
            } else if (isFmtBoolean(formatCode)) {
                packBoolean(formatCode, formatAlignment, isTrueNode.execute(frame, inliningTarget, value), buffer, offset);
            } else if (isFmtBytes(formatCode)) {
                if (!isBytes(value)) {
                    throw raiseNode.get(inliningTarget).raise(StructError, ARG_FOR_N_MUST_BE, formatCode.formatDef.format, "bytes");
                }
                packBytes(formatCode, formatAlignment, (PBytesLike) value, buffer, offset, inliningTarget, formatProfile, bufferLib, raiseNode);
            } else {
                throw raiseNode.get(inliningTarget).raise(NotImplementedError, STRUCT_FMT_NOT_YET_SUPPORTED, formatCode);
            }
        }

        @Specialization(replaces = "packObjectCached")
        static void packObject(VirtualFrame frame, FormatCode formatCode, FormatAlignment formatAlignment, Object value, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetLongNode getLongNode,
                        @Shared @Cached CastToJavaBigIntegerNode toJavaBigIntegerNode,
                        @Shared @Cached CanBeDoubleNode canBeDoubleNode,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Shared @Cached PyObjectIsTrueNode isTrueNode,
                        @Shared @Cached("createEqualityProfile()") PrimitiveValueProfile formatProfile,
                        @Shared @Cached InlinedConditionProfile profileSigned,
                        @Shared @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            packObjectCached(frame, formatCode, formatAlignment, value, buffer, offset, inliningTarget, getLongNode, toJavaBigIntegerNode,
                            canBeDoubleNode, asDoubleNode, isTrueNode,
                            getNumericSupport(formatAlignment), formatProfile, profileSigned, errorProfile, bufferLib, raiseNode);
        }
    }

    @ImportStatic(FormatCode.class)
    @GenerateInline(false) // footprint reduction 40 -> 22
    public abstract static class UnpackValueNode extends StructBaseNode {
        public abstract Object execute(FormatCode formatCode, FormatAlignment formatAlignment, byte[] buffer, int offset);

        @Specialization(guards = {"isFmtInteger(formatCode)", "numBytes == formatCode.numBytes()", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "getNumBytesLimit()")
        static Object unpack8Cached(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached("formatCode.numBytes()") int numBytes,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport,
                        @Shared @Cached InlinedConditionProfile profilePIntResult,
                        @Shared @Cached InlinedConditionProfile profileSigned,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            long num;
            if (profileSigned.profile(inliningTarget, formatCode.isUnsigned())) {
                num = numericSupport.getLongUnsigned(buffer, offset, numBytes);
            } else {
                num = numericSupport.getLong(buffer, offset, numBytes);
                num = handleSign(formatCode, num);
            }
            if (profilePIntResult.profile(inliningTarget, formatCode.isUnsigned() && num < 0)) {
                return factory.get(inliningTarget).createInt(getAsUnsignedBigInt(num));
            }
            return num;
        }

        @Specialization(guards = "isFmtInteger(formatCode)", replaces = "unpack8Cached")
        static Object unpack8(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profilePIntResult,
                        @Shared @Cached InlinedConditionProfile profileSigned,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            return unpack8Cached(formatCode, formatAlignment, buffer, offset, inliningTarget, formatCode.numBytes(),
                            getNumericSupport(formatAlignment), profilePIntResult, profileSigned, factory);
        }

        @Specialization(guards = {"isFmtFloat(formatCode)", "numericSupport == getNumericSupport(formatAlignment)"}, limit = "3")
        static Object unpackFloat8(FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, byte[] buffer, int offset,
                        @Cached("getNumericSupport(formatAlignment)") NumericSupport numericSupport) {
            return numericSupport.getDouble(buffer, offset, formatCode.numBytes());
        }

        @Specialization(guards = {"isFmtVoidPtr(formatCode)", "formatAlignment.isNative()"})
        @SuppressWarnings("unused")
        static Object unpackVoidPtr(FormatCode formatCode, FormatAlignment formatAlignment, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile profilePIntResult,
                        @Shared @Cached PythonObjectFactory.Lazy factory,
                        @Shared @Cached PRaiseNode raiseNode) {
            long num = getNumericSupport(formatAlignment).getLongUnsigned(buffer, offset, formatCode.numBytes());
            if (profilePIntResult.profile(inliningTarget, num < 0)) {
                return factory.get(inliningTarget).createInt(getAsUnsignedBigInt(num));
            }
            return num;
        }

        @Specialization(guards = "isFmtBoolean(formatCode)")
        static Object unpackBool(@SuppressWarnings("unused") FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, byte[] buffer, int offset) {
            return buffer[offset] != 0;
        }

        @Specialization(guards = "isFmtBytes(formatCode)")
        static Object unpackBytes(@SuppressWarnings("unused") FormatCode formatCode, @SuppressWarnings("unused") FormatAlignment formatAlignment, byte[] buffer, int offset,
                        @Bind("this") Node inliningTarget,
                        @Cached(value = "createIdentityProfile()", inline = false) ValueProfile formatProfile,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            byte[] bytes;
            switch (formatProfile.profile(formatCode.formatDef.format)) {
                case FMT_CHAR:
                    bytes = new byte[]{buffer[offset]};
                    break;
                case FMT_STRING:
                    bytes = new byte[formatCode.size];
                    PythonUtils.arraycopy(buffer, offset, bytes, 0, formatCode.size);
                    break;
                case FMT_PASCAL_STRING:
                default:
                    int n = buffer[offset] & 0xFF;
                    if (n >= formatCode.size) {
                        n = formatCode.size - 1;
                    }
                    bytes = new byte[n];
                    PythonUtils.arraycopy(buffer, offset + 1, bytes, 0, n);
            }
            return factory.get(inliningTarget).createBytes(bytes);
        }

        @Specialization(guards = "!isSupportedFormat(formatCode)")
        @SuppressWarnings("unused")
        static byte[] unpackUnsupported(FormatCode formatCode, FormatAlignment formatAlignment, byte[] buffer, int offset,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(NotImplementedError, STRUCT_FMT_NOT_YET_SUPPORTED, formatCode);
        }
    }

}
