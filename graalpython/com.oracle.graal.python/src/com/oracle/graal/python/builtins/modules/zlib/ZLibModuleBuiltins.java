/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibCompress;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibDecompress;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_OK;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_BYTESLIKE_GOT_P;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.UseDefaultForNone;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
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
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = ZLibModuleBuiltins.J_ZLIB)
public class ZLibModuleBuiltins extends PythonBuiltins {

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
    public abstract static class ExpectIntNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
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
        Object doOthers(VirtualFrame frame, Object value,
                        @Cached PyLongAsIntNode asIntNode) {
            return asIntNode.execute(frame, value);
        }

        protected ExpectIntNode createRec() {
            return ZLibModuleBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }

        @ClinicConverterFactory
        public static ExpectIntNode create(@ClinicConverterFactory.DefaultValue Object defaultValue, @UseDefaultForNone boolean useDefaultForNone) {
            assert useDefaultForNone; // the other way around is not supported yet by this convertor
            return ZLibModuleBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }
    }

    public abstract static class ExpectByteLikeNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
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
        byte[] error(@SuppressWarnings("unused") VirtualFrame frame, Object value) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, value);
        }

        @ClinicConverterFactory
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

        private static final int[] CRC32_TABLE = {
                        0x00000000, 0x77073096, 0xee0e612c, 0x990951ba, 0x076dc419,
                        0x706af48f, 0xe963a535, 0x9e6495a3, 0x0edb8832, 0x79dcb8a4,
                        0xe0d5e91e, 0x97d2d988, 0x09b64c2b, 0x7eb17cbd, 0xe7b82d07,
                        0x90bf1d91, 0x1db71064, 0x6ab020f2, 0xf3b97148, 0x84be41de,
                        0x1adad47d, 0x6ddde4eb, 0xf4d4b551, 0x83d385c7, 0x136c9856,
                        0x646ba8c0, 0xfd62f97a, 0x8a65c9ec, 0x14015c4f, 0x63066cd9,
                        0xfa0f3d63, 0x8d080df5, 0x3b6e20c8, 0x4c69105e, 0xd56041e4,
                        0xa2677172, 0x3c03e4d1, 0x4b04d447, 0xd20d85fd, 0xa50ab56b,
                        0x35b5a8fa, 0x42b2986c, 0xdbbbc9d6, 0xacbcf940, 0x32d86ce3,
                        0x45df5c75, 0xdcd60dcf, 0xabd13d59, 0x26d930ac, 0x51de003a,
                        0xc8d75180, 0xbfd06116, 0x21b4f4b5, 0x56b3c423, 0xcfba9599,
                        0xb8bda50f, 0x2802b89e, 0x5f058808, 0xc60cd9b2, 0xb10be924,
                        0x2f6f7c87, 0x58684c11, 0xc1611dab, 0xb6662d3d, 0x76dc4190,
                        0x01db7106, 0x98d220bc, 0xefd5102a, 0x71b18589, 0x06b6b51f,
                        0x9fbfe4a5, 0xe8b8d433, 0x7807c9a2, 0x0f00f934, 0x9609a88e,
                        0xe10e9818, 0x7f6a0dbb, 0x086d3d2d, 0x91646c97, 0xe6635c01,
                        0x6b6b51f4, 0x1c6c6162, 0x856530d8, 0xf262004e, 0x6c0695ed,
                        0x1b01a57b, 0x8208f4c1, 0xf50fc457, 0x65b0d9c6, 0x12b7e950,
                        0x8bbeb8ea, 0xfcb9887c, 0x62dd1ddf, 0x15da2d49, 0x8cd37cf3,
                        0xfbd44c65, 0x4db26158, 0x3ab551ce, 0xa3bc0074, 0xd4bb30e2,
                        0x4adfa541, 0x3dd895d7, 0xa4d1c46d, 0xd3d6f4fb, 0x4369e96a,
                        0x346ed9fc, 0xad678846, 0xda60b8d0, 0x44042d73, 0x33031de5,
                        0xaa0a4c5f, 0xdd0d7cc9, 0x5005713c, 0x270241aa, 0xbe0b1010,
                        0xc90c2086, 0x5768b525, 0x206f85b3, 0xb966d409, 0xce61e49f,
                        0x5edef90e, 0x29d9c998, 0xb0d09822, 0xc7d7a8b4, 0x59b33d17,
                        0x2eb40d81, 0xb7bd5c3b, 0xc0ba6cad, 0xedb88320, 0x9abfb3b6,
                        0x03b6e20c, 0x74b1d29a, 0xead54739, 0x9dd277af, 0x04db2615,
                        0x73dc1683, 0xe3630b12, 0x94643b84, 0x0d6d6a3e, 0x7a6a5aa8,
                        0xe40ecf0b, 0x9309ff9d, 0x0a00ae27, 0x7d079eb1, 0xf00f9344,
                        0x8708a3d2, 0x1e01f268, 0x6906c2fe, 0xf762575d, 0x806567cb,
                        0x196c3671, 0x6e6b06e7, 0xfed41b76, 0x89d32be0, 0x10da7a5a,
                        0x67dd4acc, 0xf9b9df6f, 0x8ebeeff9, 0x17b7be43, 0x60b08ed5,
                        0xd6d6a3e8, 0xa1d1937e, 0x38d8c2c4, 0x4fdff252, 0xd1bb67f1,
                        0xa6bc5767, 0x3fb506dd, 0x48b2364b, 0xd80d2bda, 0xaf0a1b4c,
                        0x36034af6, 0x41047a60, 0xdf60efc3, 0xa867df55, 0x316e8eef,
                        0x4669be79, 0xcb61b38c, 0xbc66831a, 0x256fd2a0, 0x5268e236,
                        0xcc0c7795, 0xbb0b4703, 0x220216b9, 0x5505262f, 0xc5ba3bbe,
                        0xb2bd0b28, 0x2bb45a92, 0x5cb36a04, 0xc2d7ffa7, 0xb5d0cf31,
                        0x2cd99e8b, 0x5bdeae1d, 0x9b64c2b0, 0xec63f226, 0x756aa39c,
                        0x026d930a, 0x9c0906a9, 0xeb0e363f, 0x72076785, 0x05005713,
                        0x95bf4a82, 0xe2b87a14, 0x7bb12bae, 0x0cb61b38, 0x92d28e9b,
                        0xe5d5be0d, 0x7cdcefb7, 0x0bdbdf21, 0x86d3d2d4, 0xf1d4e242,
                        0x68ddb3f8, 0x1fda836e, 0x81be16cd, 0xf6b9265b, 0x6fb077e1,
                        0x18b74777, 0x88085ae6, 0xff0f6a70, 0x66063bca, 0x11010b5c,
                        0x8f659eff, 0xf862ae69, 0x616bffd3, 0x166ccf45, 0xa00ae278,
                        0xd70dd2ee, 0x4e048354, 0x3903b3c2, 0xa7672661, 0xd06016f7,
                        0x4969474d, 0x3e6e77db, 0xaed16a4a, 0xd9d65adc, 0x40df0b66,
                        0x37d83bf0, 0xa9bcae53, 0xdebb9ec5, 0x47b2cf7f, 0x30b5ffe9,
                        0xbdbdf21c, 0xcabac28a, 0x53b39330, 0x24b4a3a6, 0xbad03605,
                        0xcdd70693, 0x54de5729, 0x23d967bf, 0xb3667a2e, 0xc4614ab8,
                        0x5d681b02, 0x2a6f2b94, 0xb40bbe37, 0xc30c8ea1, 0x5a05df1b,
                        0x2d02ef8d
        };

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.Crc32NodeClinicProviderGen.INSTANCE;
        }

        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        @Specialization
        long doitNone(VirtualFrame frame, Object data, @SuppressWarnings("unused") PNone value,
                        @Cached ToBytesNode toBytesNode) {
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
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes,
                        @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytes.execute(data);
            int len = data.getSequenceStorage().length();
            return nativeCrc32(bytes, len, value, invoke);
        }

        @Specialization(guards = {"useNative()", "!isBytes(data)"})
        long doNativeObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return nativeCrc32(bytes, bytes.length, value, invoke);
        }

        @Specialization(guards = "!useNative()")
        static long doJavaBytes(PBytesLike data, int value,
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes) {
            byte[] bytes = toBytes.execute(data);
            int len = data.getSequenceStorage().length();
            return javaCrc32(bytes, len, value);
        }

        @Specialization(guards = {"!useNative()", "!isBytes(data)"})
        static long doJavaObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return javaCrc32(bytes, bytes.length, value);
        }

        @Fallback
        long error(Object data, @SuppressWarnings("unused") Object value) {
            throw raise(TypeError, EXPECTED_BYTESLIKE_GOT_P, data);
        }

        long nativeCrc32(byte[] bytes, int len, int value,
                        NativeLibrary.InvokeNativeFunction invoke) {
            PythonContext ctxt = getContext();
            int signedVal = (int) ctxt.getNFIZlibSupport().crc32(value, ctxt.getEnv().asGuestValue(bytes), len, invoke);
            return signedVal & 0xFFFFFFFFL;
        }

        static long javaCrc32(byte[] bytes, int len, int value) {
            int result = ~value;
            for (int i = 0; i < len; i++) {
                result = (result >>> 8) ^ CRC32_TABLE[(result ^ bytes[i]) & 0xff];
            }
            result ^= 0xffffffff;
            return result & 0xFFFFFFFFL;
        }
    }

    // zlib.adler32(data[, value])
    @Builtin(name = "adler32", minNumOfPositionalArgs = 1, parameterNames = {"", "value"})
    @ArgumentClinic(name = "value", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "PNone.NO_VALUE", useDefaultForNone = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class Adler32Node extends PythonBinaryClinicBuiltinNode {

        private static final int DEFER = 3850;
        private static final int BASE = 65521;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.Adler32NodeClinicProviderGen.INSTANCE;
        }

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
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes,
                        @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytes.execute(data);
            int len = data.getSequenceStorage().length();
            return nativeAdler32(bytes, len, value, PythonContext.get(this), invoke);
        }

        @Specialization(guards = {"useNative()", "!isBytes(data)"})
        long doNativeObject(VirtualFrame frame, Object data, int value,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Cached NativeLibrary.InvokeNativeFunction invoke) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return nativeAdler32(bytes, bytes.length, value, PythonContext.get(this), invoke);
        }

        @Specialization(guards = "!useNative()")
        long doJavaBytes(PBytesLike data, int value,
                        @Shared("b") @Cached SequenceStorageNodes.GetInternalBytesNode toBytes) {
            byte[] bytes = toBytes.execute(data);
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
            int signedVal = (int) ctxt.getNFIZlibSupport().adler32(value, ctxt.getEnv().asGuestValue(bytes), len, invoke);
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
    @Builtin(name = "compress", minNumOfPositionalArgs = 1, parameterNames = {"", "level"})
    @ArgumentClinic(name = "level", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "ZLibModuleBuiltins.Z_DEFAULT_COMPRESSION", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CompressNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.CompressNodeClinicProviderGen.INSTANCE;
        }

        protected boolean useNative() {
            return getContext().getNFIZlibSupport().isAvailable();
        }

        @Specialization(guards = {"useNative()"})
        PBytes doNativeBytes(PBytesLike data, int level,
                        @Cached SequenceStorageNodes.GetInternalBytesNode toByte,
                        @Cached ZlibNodes.ZlibNativeCompress nativeCompress) {
            byte[] bytes = toByte.execute(data);
            int len = data.getSequenceStorage().length();
            byte[] resultArray = nativeCompress.execute(bytes, len, level, getContext());
            return factory().createBytes(resultArray);
        }

        @Specialization(guards = {"useNative()", "!isBytes(data)"})
        PBytes doNativeObject(VirtualFrame frame, Object data, int level,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Cached ZlibNodes.ZlibNativeCompress nativeCompress) {
            byte[] bytes = toBytesNode.execute(frame, data);
            return factory().createBytes(nativeCompress.execute(bytes, bytes.length, level, getContext()));
        }

        @Specialization(guards = {"!useNative()"})
        PBytes doJava(VirtualFrame frame, Object data, int level,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Cached ConditionProfile wrongLevelProfile) {
            if (wrongLevelProfile.profile(level < -1 || 9 < level)) {
                throw raise(ZLibError, ErrorMessages.BAD_COMPRESSION_LEVEL);
            }
            byte[] array = toBytesNode.execute(frame, data);
            return factory().createBytes(javaCompress(array, level));
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object data, Object level) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, data);
        }

        @CompilerDirectives.TruffleBoundary
        byte[] javaCompress(byte[] array, int level) {
            Deflater compresser = new Deflater(level);
            compresser.setInput(array);
            compresser.finish();
            byte[] resultArray = new byte[DEF_BUF_SIZE];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (!compresser.finished()) {
                int howmany = compresser.deflate(resultArray);
                baos.write(resultArray, 0, howmany);
            }
            compresser.end();
            return baos.toByteArray();
        }
    }

    // zlib.decompress(data, wbits=MAX_WBITS, bufsize=DEF_BUF_SIZE)
    @Builtin(name = "decompress", minNumOfPositionalArgs = 1, parameterNames = {"", "wbits", "bufsize"})
    @ArgumentClinic(name = "wbits", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "ZLibModuleBuiltins.MAX_WBITS", useDefaultForNone = true)
    @ArgumentClinic(name = "bufsize", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "ZLibModuleBuiltins.DEF_BUF_SIZE", useDefaultForNone = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZLibModuleBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        @Specialization(guards = {"bufsize >= 0", "useNative()"})
        PBytes doNativeBytes(PBytesLike data, int wbits, int bufsize,
                        @Cached SequenceStorageNodes.GetInternalBytesNode toByte,
                        @Cached ZlibNodes.ZlibNativeDecompress nativeDecompress) {
            byte[] bytes = toByte.execute(data);
            int len = data.getSequenceStorage().length();
            return factory().createBytes(nativeDecompress.execute(bytes, len, wbits, bufsize, PythonContext.get(this)));
        }

        @Specialization(guards = {"bufsize >= 0", "useNative()", "!isBytes(data)"})
        PBytes doNativeObject(VirtualFrame frame, Object data, int wbits, int bufsize,
                        @Shared("bb") @Cached ToBytesNode toBytesNode,
                        @Cached ZlibNodes.ZlibNativeDecompress nativeDecompress) {
            byte[] bytes = toBytesNode.execute(frame, data);
            int len = bytes.length;
            return factory().createBytes(nativeDecompress.execute(bytes, len, wbits, bufsize, PythonContext.get(this)));
        }

        @Specialization(guards = {"bufsize >= 0", "!useNative()"})
        PBytes doJava(VirtualFrame frame, Object data, int wbits, int bufsize,
                        @Shared("bb") @Cached ToBytesNode toBytesNode) {
            byte[] array = toBytesNode.execute(frame, data);
            try {
                return factory().createBytes(javaDecompress(array, wbits, bufsize == 0 ? 1 : bufsize));
            } catch (DataFormatException e) {
                throw raise(ZLibError, ErrorMessages.WHILE_PREPARING_TO_S_DATA, "decompress");
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"bufsize >= 0"})
        PBytes doNative(PBytesLike data, int wbits, int bufsize) {
            throw raise(ZLibError, ErrorMessages.MUST_BE_NON_NEGATIVE, "bufsize");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, Object data, Object wbits, Object bufsize) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, data);
        }

        @TruffleBoundary
        byte[] javaDecompress(byte[] array, @SuppressWarnings("unused") int wbits, int bufsize) throws DataFormatException {
            // We don't use wbits currently. There is no easy way how to map to java Inflater.
            Inflater decompresser = new Inflater();
            decompresser.setInput(array);
            byte[] resultArray = new byte[bufsize];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (!decompresser.finished()) {
                int howmany = decompresser.inflate(resultArray);
                if (howmany == 0 && decompresser.needsInput()) {
                    throw raise(ZLibError, ErrorMessages.ERROR_5_WHILE_DECOMPRESSING);
                }
                baos.write(resultArray, 0, howmany);
            }
            decompresser.end();
            return baos.toByteArray();
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

        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        protected static boolean isValidWBitRange(int wbits) {
            return wbits < -7 || (wbits > 7 && wbits <= MAX_WBITS) || (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16));
        }

        @Specialization(guards = {"method == DEFLATED", "useNative()"})
        Object doNative(int level, int method, int wbits, int memLevel, int strategy, byte[] zdict,
                        @Cached NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached NativeLibrary.InvokeNativeFunction compressObjInit,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = PythonContext.get(this).getNFIZlibSupport();
            Object zst = zlibSupport.createCompObject(createCompObject);

            int err;
            if (zdict.length > 0) {
                err = zlibSupport.compressObjInitWithDict(zst, level, method, wbits, memLevel, strategy,
                                PythonContext.get(this).getEnv().asGuestValue(zdict), zdict.length, compressObjInit);
            } else {
                err = zlibSupport.compressObjInit(zst, level, method, wbits, memLevel, strategy, compressObjInit);
            }
            if (err != Z_OK) {
                errorHandling.execute(zst, err, zlibSupport, true);
            }
            return factory().createNativeZLibCompObject(ZlibCompress, zst, zlibSupport);
        }

        /**
         * @param memLevel is ignored - it mostly affects performance and compression rate, we trust
         *            that the Deflater implementation will work well
         */
        @CompilerDirectives.TruffleBoundary
        @Specialization(guards = {"method == DEFLATED", "!useNative()", "isValidWBitRange(wbits)"})
        Object doJava(int level, @SuppressWarnings("unused") int method, int wbits, @SuppressWarnings("unused") int memLevel, int strategy, byte[] zdict) {
            // wbits < 0: generate a RAW stream, i.e., no wrapping
            // wbits 25..31: gzip container, i.e., no wrapping
            // Otherwise: wrap stream with zlib header and trailer
            Deflater deflater = new Deflater(level, wbits < 0 || wbits > (MAX_WBITS + 9));

            deflater.setStrategy(strategy);
            if (zdict.length > 0) {
                deflater.setDictionary(zdict);
            }
            return factory().createJavaZLibCompObject(ZlibCompress, deflater, level, wbits, strategy, zdict);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"method == DEFLATED", "!useNative()", "!isValidWBitRange(wbits)"})
        Object invalid(int level, int method, int wbits, int memLevel, int strategy, byte[] zdict) {
            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_INITIALIZATION_OPTION);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"method != DEFLATED"})
        Object methodErr(int level, int method, int wbits, int memLevel, int strategy, byte[] zdict) {
            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.ONLY_DEFLATED_ALLOWED_AS_METHOD, DEFLATED, method);
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

        protected boolean useNative() {
            return PythonContext.get(this).getNFIZlibSupport().isAvailable();
        }

        protected static boolean isValidWBitRange(int wbits) {
            return wbits < -7 || (wbits > 7 && wbits <= MAX_WBITS) || (wbits > (MAX_WBITS + 9) && wbits <= (MAX_WBITS + 16));
        }

        @Specialization(guards = {"useNative()"})
        Object doNative(int wbits, byte[] zdict,
                        @Cached NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached NativeLibrary.InvokeNativeFunction decompressObjInit,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = PythonContext.get(this).getNFIZlibSupport();
            Object zst = zlibSupport.createCompObject(createCompObject);

            int err;
            if (zdict.length > 0) {
                err = zlibSupport.decompressObjInitWithDict(zst, wbits, PythonContext.get(this).getEnv().asGuestValue(zdict), zdict.length, decompressObjInit);
            } else {
                err = zlibSupport.decompressObjInit(zst, wbits, decompressObjInit);
            }
            if (err != Z_OK) {
                errorHandling.execute(zst, err, zlibSupport, true);
            }
            return factory().createNativeZLibCompObject(ZlibDecompress, zst, zlibSupport);
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization(guards = {"!useNative()", "isValidWBitRange(wbits)"})
        Object doJava(int wbits, byte[] zdict) {
            // wbits < 0: generate a RAW stream, i.e., no wrapping
            // wbits 25..31: gzip container, i.e., no wrapping
            // Otherwise: wrap stream with zlib header and trailer
            boolean isRAW = wbits < 0;
            Inflater inflater = new Inflater(isRAW || wbits > (MAX_WBITS + 9));
            if (isRAW && zdict.length > 0) {
                inflater.setDictionary(zdict);
            }
            ZLibCompObject obj = factory().createJavaZLibCompObject(ZlibDecompress, inflater, wbits, zdict);
            obj.setUnusedData(factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY));
            obj.setUnconsumedTail(factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY));
            return obj;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!useNative()", "!isValidWBitRange(wbits)"})
        Object invalid(int wbits, byte[] zdict) {
            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INVALID_INITIALIZATION_OPTION);
        }
    }
}
