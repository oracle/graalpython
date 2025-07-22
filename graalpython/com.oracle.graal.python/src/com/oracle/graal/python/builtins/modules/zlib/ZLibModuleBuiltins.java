/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.modules.zlib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_OK;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_BYTESLIKE_GOT_P;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.crc32;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static java.util.zip.Deflater.DEFAULT_COMPRESSION;

import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.CRC32;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.UseDefaultForNone;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = ZLibModuleBuiltins.J_ZLIB)
public final class ZLibModuleBuiltins extends PythonBuiltins {

    protected static final String J_ZLIB = "zlib";
    protected static final TruffleString T_ZLIB = tsLiteral(J_ZLIB);

    /*
     * There isn't currently a dynamic way to ask the jdk about the zlib version. so, we got it
     * manually from: "jdk/blob/master/src/java.base/share/native/libzip/zlib/README". The last time
     * zlib been updated in the JDK was on Sep 12, 2017.
     */
    private static final TruffleString T_JDK_ZLIB_VERSION = tsLiteral("1.2.11");

    // copied from zlib/blob/master/zlib.h
    /*- Allowed flush values; see deflate() and inflate() below for details */
    public static final int Z_NO_FLUSH = 0;
    public static final int Z_PARTIAL_FLUSH = 1; // JDK doesn't support it.
    public static final int Z_SYNC_FLUSH = 2;
    public static final int Z_FULL_FLUSH = 3;
    public static final int Z_FINISH = 4;
    public static final int Z_BLOCK = 5;  // JDK doesn't support it.
    public static final int Z_TREES = 6;  // JDK doesn't support it.

    /*- compression levels */
    public static final int Z_NO_COMPRESSION = 0;
    public static final int Z_BEST_SPEED = 1;
    public static final int Z_BEST_COMPRESSION = 9;
    public static final int Z_DEFAULT_COMPRESSION = -1;

    /*- compression strategy; see deflateInit2() below for details */
    public static final int Z_FILTERED = 1;
    public static final int Z_HUFFMAN_ONLY = 2;
    public static final int Z_RLE = 3;
    public static final int Z_FIXED = 4;
    public static final int Z_DEFAULT_STRATEGY = 0;

    /*- The deflate compression method (the only one supported in this version) */
    public static final int DEFLATED = 8;

    protected static final int MAX_WBITS = 15;
    protected static final int DEF_MEM_LEVEL = 8;
    protected static final int DEF_BUF_SIZE = 16 * 1024;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZLibModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("Z_NO_COMPRESSION", Z_NO_COMPRESSION);
        addBuiltinConstant("Z_BEST_SPEED", Z_BEST_SPEED);
        addBuiltinConstant("Z_BEST_COMPRESSION", Z_BEST_COMPRESSION);
        addBuiltinConstant("Z_DEFAULT_COMPRESSION", Z_DEFAULT_COMPRESSION);

        addBuiltinConstant("Z_FILTERED", Z_FILTERED);
        addBuiltinConstant("Z_HUFFMAN_ONLY", Z_HUFFMAN_ONLY);
        addBuiltinConstant("Z_RLE", Z_RLE);
        addBuiltinConstant("Z_FIXED", Z_FIXED);
        addBuiltinConstant("Z_DEFAULT_STRATEGY", Z_DEFAULT_STRATEGY);

        addBuiltinConstant("Z_NO_FLUSH", Z_NO_FLUSH);
        addBuiltinConstant("Z_SYNC_FLUSH", Z_SYNC_FLUSH);
        addBuiltinConstant("Z_FULL_FLUSH", Z_FULL_FLUSH);
        addBuiltinConstant("Z_FINISH", Z_FINISH);

        addBuiltinConstant("DEFLATED", DEFLATED);

        addBuiltinConstant("MAX_WBITS", MAX_WBITS);
        addBuiltinConstant("DEF_MEM_LEVEL", DEF_MEM_LEVEL);
        addBuiltinConstant("DEF_BUF_SIZE", DEF_BUF_SIZE);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        NFIZlibSupport zlibSupport = core.getContext().getNFIZlibSupport();
        PythonModule zlibModule = core.lookupBuiltinModule(T_ZLIB);
        // isAvailable() checked already if native access is allowed
        TruffleString ver = T_JDK_ZLIB_VERSION;
        TruffleString rtver = T_JDK_ZLIB_VERSION;
        if (zlibSupport.isAvailable()) {
            try {
                ver = asString(zlibSupport.zlibVersion());
                rtver = asString(zlibSupport.zlibRuntimeVersion());
                zlibModule.setAttribute(tsLiteral("Z_PARTIAL_FLUSH"), Z_PARTIAL_FLUSH);
                zlibModule.setAttribute(tsLiteral("Z_BLOCK"), Z_BLOCK);
                zlibModule.setAttribute(tsLiteral("Z_TREES"), Z_TREES);
            } catch (NativeLibrary.NativeLibraryCannotBeLoaded e) {
                zlibSupport.notAvailable();
                // ignore and proceed without native zlib support and use jdk's.
            }
        }
        zlibModule.setAttribute(tsLiteral("ZLIB_VERSION"), ver);
        zlibModule.setAttribute(tsLiteral("ZLIB_RUNTIME_VERSION"), rtver);
    }

    private static TruffleString asString(Object o) {
        if (o != null) {
            try {
                return InteropLibrary.getUncached().asTruffleString(o).switchEncodingUncached(TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                // pass through
            }
        }
        return null;
    }

    @ImportStatic(MathGuards.class)
    public abstract static class ExpectIntNode extends ArgumentCastNode {
        private final Object defaultValue;

        protected ExpectIntNode(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization
        Object none(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization
        static int doInt(int i) {
            // fast-path for the most common case
            return i;
        }

        @Specialization
        static int doBool(boolean b) {
            return PInt.intValue(b);
        }

        @Specialization
        public int toInt(long x) {
            // lost magnitude is ok here.
            return (int) x;
        }

        @Specialization
        public int toInt(PInt x) {
            // lost magnitude is ok here.
            return x.intValue();
        }

        @Specialization(guards = {"!isPNone(value)", "!isInteger(value)"})
        static Object doOthers(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsIntNode asIntNode) {
            return asIntNode.execute(frame, inliningTarget, value);
        }

        protected ExpectIntNode createRec() {
            return ZLibModuleBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static ExpectIntNode create(@ClinicConverterFactory.DefaultValue Object defaultValue, @UseDefaultForNone boolean useDefaultForNone) {
            assert useDefaultForNone; // the other way around is not supported yet by this convertor
            return ZLibModuleBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }
    }

    public abstract static class ExpectByteLikeNode extends ArgumentCastNode {
        private final byte[] defaultValue;

        protected ExpectByteLikeNode(byte[] defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization
        byte[] handleNone(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization
        static byte[] doBytes(PBytesLike bytesLike,
                        @Shared("b") @Cached BytesNodes.ToBytesNode toBytesNode) {
            return toBytesNode.execute(bytesLike);
        }

        @Specialization
        static byte[] doMemView(VirtualFrame frame, PMemoryView bytesLike,
                        @Shared("b") @Cached BytesNodes.ToBytesNode toBytesNode) {
            return toBytesNode.execute(frame, bytesLike);
        }

        @Fallback
        static byte[] error(@SuppressWarnings("unused") VirtualFrame frame, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, value);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static ExpectByteLikeNode create(@ClinicConverterFactory.DefaultValue byte[] defaultValue, @UseDefaultForNone boolean useDefaultForNone) {
            assert useDefaultForNone; // the other way around is not supported yet by this convertor
            return ZLibModuleBuiltinsFactory.ExpectByteLikeNodeGen.create(defaultValue);
        }
    }

    // zlib.crc32(data[, value])
    @Builtin(name = "crc32", minNumOfPositionalArgs = 1, parameterNames = {"data", "value"})
    @ArgumentClinic(name = "value", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "PNone.NO_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class Crc32Node extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.Crc32NodeClinicProviderGen.INSTANCE;
        }

        @NonIdempotent
        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        @Specialization
        long doitNone(VirtualFrame frame, Object data, @SuppressWarnings("unused") PNone value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode) {
            return doCRC32(toBytesNode.execute(frame, data));
        }

        @TruffleBoundary
        static long doCRC32(byte[] data) {
            CRC32 crc32 = new CRC32();
            crc32.update(data);
            return crc32.getValue();
        }

        @Specialization(guards = "useNative()")
        long doNativeBytes(PBytesLike data, int value,
                        @Bind Node inliningTarget,
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes,
                        @Shared @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytes.execute(inliningTarget, data);
            int len = data.getSequenceStorage().length();
            return nativeCrc32(bytes, len, value, invoke);
        }

        @Specialization(guards = {"useNative()", "!isBytes(data)"})
        long doNativeObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Shared @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return nativeCrc32(bytes, bytes.length, value, invoke);
        }

        @Specialization(guards = "!useNative()")
        static long doJavaBytes(PBytesLike data, int value,
                        @Bind Node inliningTarget,
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes) {
            byte[] bytes = toBytes.execute(inliningTarget, data);
            int len = data.getSequenceStorage().length();
            return crc32(value, bytes, 0, len);
        }

        @Specialization(guards = {"!useNative()", "!isBytes(data)"})
        static long doJavaObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return crc32(value, bytes, 0, bytes.length);
        }

        @Fallback
        static long error(Object data, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, EXPECTED_BYTESLIKE_GOT_P, data);
        }

        long nativeCrc32(byte[] bytes, int len, int value,
                        NativeLibrary.InvokeNativeFunction invoke) {
            PythonContext ctxt = getContext();
            int signedVal = (int) ctxt.getNFIZlibSupport().crc32(value, bytes, len, invoke);
            return signedVal & 0xFFFFFFFFL;
        }
    }

    // zlib.adler32(data[, value])
    @Builtin(name = "adler32", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"data", "value"})
    @ArgumentClinic(name = "value", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "PNone.NO_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class Adler32Node extends PythonBinaryClinicBuiltinNode {

        private static final int DEFER = 3850;
        private static final int BASE = 65521;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.Adler32NodeClinicProviderGen.INSTANCE;
        }

        @NonIdempotent
        protected boolean useNative() {
            return getContext().getNFIZlibSupport().isAvailable();
        }

        @Specialization
        long doitNone(VirtualFrame frame, Object data, @SuppressWarnings("unused") PNone value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode) {
            return doAdler32(toBytesNode.execute(frame, data));
        }

        @TruffleBoundary
        private static long doAdler32(byte[] bytes) {
            Adler32 adler32 = new Adler32();
            adler32.update(bytes);
            return adler32.getValue();
        }

        @Specialization(guards = "useNative()")
        long doNativeBytes(PBytesLike data, int value,
                        @Bind Node inliningTarget,
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes,
                        @Shared @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytes.execute(inliningTarget, data);
            int len = data.getSequenceStorage().length();
            return nativeAdler32(bytes, len, value, PythonContext.get(this), invoke);
        }

        @Specialization(guards = {"useNative()", "!isBytes(data)"})
        long doNativeObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Shared @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return nativeAdler32(bytes, bytes.length, value, PythonContext.get(this), invoke);
        }

        @Specialization(guards = "!useNative()")
        long doJavaBytes(PBytesLike data, int value,
                        @Bind Node inliningTarget,
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes) {
            byte[] bytes = toBytes.execute(inliningTarget, data);
            int len = data.getSequenceStorage().length();
            return javaAdler32(bytes, len, value);
        }

        @Specialization(guards = {"!useNative()", "!isBytes(data)"})
        long doJavaObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return javaAdler32(bytes, bytes.length, value);
        }

        long nativeAdler32(byte[] bytes, int len, int value,
                        PythonContext ctxt,
                        NativeLibrary.InvokeNativeFunction invoke) {
            int signedVal = (int) ctxt.getNFIZlibSupport().adler32(value, bytes, len, invoke);
            return signedVal & 0xFFFFFFFFL;
        }

        long javaAdler32(byte[] bytes, int len, int value) {
            int index = 0;
            int result = value;
            int s1 = result & 0xffff;
            int s2 = result >>> 16;
            while (index < len) {
                int max = Math.min(index + DEFER, index + len);
                while (index < max) {
                    s1 = (bytes[index++] & 0xff) + s1;
                    s2 += s1;
                }
                s1 %= BASE;
                s2 %= BASE;
            }
            result = (s2 << 16) | s1;
            return result & 0xFFFFFFFFL;
        }
    }

    // zlib.compress(data, level=-1)
    @Builtin(name = "compress", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data", "level", "wbits"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "level", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.Z_DEFAULT_COMPRESSION", useDefaultForNone = true)
    @ArgumentClinic(name = "wbits", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.MAX_WBITS", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.CompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PBytes compress(VirtualFrame frame, Object buffer, int level, int wbits,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached CompressInnerNode innerNode) {
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                byte[] resultArray = innerNode.execute(inliningTarget, bytes, len, level, wbits);
                return PFactory.createBytes(language, resultArray);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class CompressInnerNode extends Node {
            abstract byte[] execute(Node inliningTarget, byte[] bytes, int length, int level, int wbits);

            @NonIdempotent
            protected boolean useNative() {
                return PythonContext.get(this).getNFIZlibSupport().isAvailable();
            }

            protected static boolean isValidLevel(int level) {
                return !((level < 0 || level > 9) && level != DEFAULT_COMPRESSION);
            }

            @Specialization(guards = "useNative()")
            static byte[] doNative(Node inliningTarget, byte[] bytes, int length, int level, int wbits,
                            @Cached ZlibNodes.ZlibNativeCompress nativeCompress) {
                return nativeCompress.execute(inliningTarget, bytes, length, level, wbits);
            }

            @Specialization(guards = {"!useNative()", "isValidLevel(level)"})
            static byte[] doJava(byte[] bytes, int length, int level, int wbits,
                            @Bind Node inliningTarget) {
                return JavaCompress.compressFinish(bytes, length, level, wbits, inliningTarget);
            }

            @Specialization(guards = {"!useNative()", "!isValidLevel(level)"})
            static byte[] doJavaLevelError(byte[] bytes, int length, int level, int wbits,
                            @Bind Node inliningTarget) {
                throw PRaiseNode.raiseStatic(inliningTarget, ZLibError, ErrorMessages.BAD_COMPRESSION_LEVEL);
            }
        }
    }

    // zlib.decompress(data, wbits=MAX_WBITS, bufsize=DEF_BUF_SIZE)
    @Builtin(name = "decompress", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data", "wbits", "bufsize"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "wbits", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.MAX_WBITS", useDefaultForNone = true)
    @ArgumentClinic(name = "bufsize", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "ZLibModuleBuiltins.DEF_BUF_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PBytes decompress(VirtualFrame frame, Object buffer, int wbits, int bufsize,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached DecompressInnerNode innerNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                if (bufsize < 0) {
                    throw raiseNode.raise(inliningTarget, ZLibError, ErrorMessages.MUST_BE_NON_NEGATIVE, "bufsize");
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                byte[] resultArray = innerNode.execute(inliningTarget, bytes, len, wbits, bufsize);
                return PFactory.createBytes(language, resultArray);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class DecompressInnerNode extends Node {
            abstract byte[] execute(Node inliningTarget, byte[] bytes, int length, int wbits, int bufsize);

            @NonIdempotent
            protected boolean useNative() {
                return PythonContext.get(this).getNFIZlibSupport().isAvailable();
            }

            @Specialization(guards = "useNative()")
            static byte[] doNative(Node inliningTarget, byte[] bytes, int length, int wbits, int bufsize,
                            @Cached ZlibNodes.ZlibNativeDecompress nativeDecompress) {
                return nativeDecompress.execute(inliningTarget, bytes, length, wbits, bufsize, PythonContext.get(inliningTarget));
            }

            @Specialization(guards = "!useNative()")
            @TruffleBoundary
            static byte[] doJava(Node inliningTarget, byte[] bytes, int length, int wbits, int bufsize) {
                return JavaDecompress.decompress(bytes, length, wbits, bufsize == 0 ? 1 : bufsize, inliningTarget);
            }
        }
    }

    protected static final byte[] EMPTY_BYTE_ARRAY = PythonUtils.EMPTY_BYTE_ARRAY;

    @ImportStatic(ZLibModuleBuiltins.class)
    @Builtin(name = "compressobj", parameterNames = {"level", "method", "wbits", "memLevel", "strategy", "zdict"})
    @ArgumentClinic(name = "level", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "method", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.DEFLATED", useDefaultForNone = true)
    @ArgumentClinic(name = "wbits", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.MAX_WBITS", useDefaultForNone = true)
    @ArgumentClinic(name = "memLevel", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.DEF_MEM_LEVEL", useDefaultForNone = true)
    @ArgumentClinic(name = "strategy", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.Z_DEFAULT_STRATEGY", useDefaultForNone = true)
    @ArgumentClinic(name = "zdict", conversionClass = ExpectByteLikeNode.class, defaultValue = "ZLibModuleBuiltins.EMPTY_BYTE_ARRAY", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class CompressObjNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.CompressObjNodeClinicProviderGen.INSTANCE;
        }

        @NonIdempotent
        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        protected static boolean isValidWBitRange(int wbits) {
            return wbits < -7 || (wbits > 7 && wbits <= MAX_WBITS) || (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16));
        }

        @Specialization(guards = {"method == DEFLATED", "useNative()"})
        static Object doNative(int level, int method, int wbits, int memLevel, int strategy, byte[] zdict,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached NativeLibrary.InvokeNativeFunction compressObjInit,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = PythonContext.get(inliningTarget).getNFIZlibSupport();
            Object zst = zlibSupport.createCompObject(createCompObject);

            int err;
            if (zdict.length > 0) {
                err = zlibSupport.compressObjInitWithDict(zst, level, method, wbits, memLevel, strategy,
                                zdict, zdict.length, compressObjInit);
            } else {
                err = zlibSupport.compressObjInit(zst, level, method, wbits, memLevel, strategy, compressObjInit);
            }
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, zst, err, zlibSupport, true);
            }
            return PFactory.createNativeZLibCompObjectCompress(language, zst, zlibSupport);
        }

        /**
         * @param memLevel is ignored - it mostly affects performance and compression rate, we trust
         *            that the Deflater implementation will work well
         */
        @TruffleBoundary
        @Specialization(guards = {"method == DEFLATED", "!useNative()", "isValidWBitRange(wbits)"})
        static Object doJava(int level, @SuppressWarnings("unused") int method, int wbits, @SuppressWarnings("unused") int memLevel, int strategy, byte[] zdict,
                        @Bind PythonLanguage language) {
            JavaCompress compress = PFactory.createJavaZLibCompObjectCompress(language, level, wbits, strategy, zdict);
            compress.setStrategy();
            compress.setDictionary();
            return compress;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"method == DEFLATED", "!useNative()", "!isValidWBitRange(wbits)"})
        static Object invalid(int level, int method, int wbits, int memLevel, int strategy, byte[] zdict,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_INITIALIZATION_OPTION);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"method != DEFLATED"})
        static Object methodErr(int level, int method, int wbits, int memLevel, int strategy, byte[] zdict,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.ONLY_DEFLATED_ALLOWED_AS_METHOD, DEFLATED, method);
        }
    }

    @Builtin(name = "decompressobj", parameterNames = {"wbits", "zdict"})
    @ArgumentClinic(name = "wbits", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "ZLibModuleBuiltins.MAX_WBITS", useDefaultForNone = true)
    @ArgumentClinic(name = "zdict", conversionClass = ExpectByteLikeNode.class, defaultValue = "ZLibModuleBuiltins.EMPTY_BYTE_ARRAY", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecompressObjNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.DecompressObjNodeClinicProviderGen.INSTANCE;
        }

        @NonIdempotent
        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        protected static boolean isValidWBitRange(int wbits) {
            return wbits < -7 || (wbits > 7 && wbits <= MAX_WBITS) || (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16));
        }

        @Specialization(guards = {"useNative()"})
        static Object doNative(int wbits, byte[] zdict,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached NativeLibrary.InvokeNativeFunction decompressObjInit,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            Object zst = zlibSupport.createCompObject(createCompObject);

            int err;
            if (zdict.length > 0) {
                err = zlibSupport.decompressObjInitWithDict(zst, wbits, zdict, zdict.length, decompressObjInit);
            } else {
                err = zlibSupport.decompressObjInit(zst, wbits, decompressObjInit);
            }
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, zst, err, zlibSupport, true);
            }
            return PFactory.createNativeZLibCompObjectDecompress(context.getLanguage(inliningTarget), zst, zlibSupport);
        }

        @TruffleBoundary
        @Specialization(guards = {"!useNative()", "isValidWBitRange(wbits)"})
        static Object doJava(int wbits, byte[] zdict) {
            // wbits < 0: generate a RAW stream, i.e., no wrapping
            // wbits 25..31: gzip container, i.e., no wrapping
            // Otherwise: wrap stream with zlib header and trailer
            boolean isRAW = wbits < 0;
            PythonLanguage language = PythonLanguage.get(null);
            JavaDecompress obj = PFactory.createJavaZLibCompObjectDecompress(language, wbits, zdict);
            if (isRAW) {
                obj.setDictionary();
            }
            obj.setUnusedData(PFactory.createEmptyBytes(language));
            obj.setUnconsumedTail(PFactory.createEmptyBytes(language));
            return obj;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!useNative()", "!isValidWBitRange(wbits)"})
        static Object invalid(int wbits, byte[] zdict,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_INITIALIZATION_OPTION);
        }
    }
}
