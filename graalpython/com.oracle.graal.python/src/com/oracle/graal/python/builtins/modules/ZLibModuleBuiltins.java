/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = ZLibModuleBuiltins.MODULE_NAME)
public class ZLibModuleBuiltins extends PythonBuiltins {

    protected static final int MAX_WBITS = 15;
    protected static final int DEFLATED = 8;
    protected static final int DEF_MEM_LEVEL = 8;
    protected static final int DEF_BUF_SIZE = 16 * 1024;
    // compression levels
    protected static final int Z_NO_COMPRESSION = 0;
    protected static final int Z_BEST_SPEED = 1;
    protected static final int Z_BEST_COMPRESSION = 9;
    protected static final int Z_DEFAULT_COMPRESSION = -1;
    // compression strategies
    protected static final int Z_FILTERED = 1;
    protected static final int Z_HUFFMAN_ONLY = 2;
    protected static final int Z_RLE = 3;
    protected static final int Z_FIXED = 4;
    protected static final int Z_DEFAULT_STRATEGY = 0;
    // allowed flush values
    protected static final int Z_NO_FLUSH = 0;
    protected static final int Z_PARTIAL_FLUSH = 1;
    protected static final int Z_SYNC_FLUSH = 2;
    protected static final int Z_FULL_FLUSH = 3;
    protected static final int Z_FINISH = 4;
    protected static final int Z_BLOCK = 5;
    protected static final int Z_TREES = 6;

    // errors
    protected static final int Z_BUF_ERROR = -5;

    protected static final String MODULE_NAME = "zlib";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZLibModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("MAX_WBITS", MAX_WBITS);
        builtinConstants.put("DEFLATED", DEFLATED);
        builtinConstants.put("DEF_MEM_LEVEL", DEF_MEM_LEVEL);
        builtinConstants.put("DEF_BUF_SIZE", DEF_BUF_SIZE);

        builtinConstants.put("Z_NO_COMPRESSION", Z_NO_COMPRESSION);
        builtinConstants.put("Z_BEST_SPEED", Z_BEST_SPEED);
        builtinConstants.put("Z_BEST_COMPRESSION", Z_BEST_COMPRESSION);
        builtinConstants.put("Z_DEFAULT_COMPRESSION", Z_DEFAULT_COMPRESSION);

        builtinConstants.put("Z_FILTERED", Z_FILTERED);
        builtinConstants.put("Z_HUFFMAN_ONLY", Z_HUFFMAN_ONLY);
        builtinConstants.put("Z_RLE", Z_RLE);
        builtinConstants.put("Z_FIXED", Z_FIXED);
        builtinConstants.put("Z_DEFAULT_STRATEGY", Z_DEFAULT_STRATEGY);

        builtinConstants.put("Z_NO_FLUSH", Z_NO_FLUSH);
        builtinConstants.put("Z_PARTIAL_FLUSH", Z_PARTIAL_FLUSH);
        builtinConstants.put("Z_SYNC_FLUSH", Z_SYNC_FLUSH);
        builtinConstants.put("Z_FULL_FLUSH", Z_FULL_FLUSH);
        builtinConstants.put("Z_FINISH", Z_FINISH);
        builtinConstants.put("Z_BLOCK", Z_BLOCK);
        builtinConstants.put("Z_TREES", Z_TREES);

    }

    // zlib.crc32(data[, value])
    @Builtin(name = "crc32", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class Crc32Node extends PythonBinaryBuiltinNode {

        @Child private CastToIntegerFromIntNode castToIntNode;

        // we can't use jdk Crc32 class, if there is done init value of crc
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

        private static int computeCRC32(byte[] bytes, int value) {
            int result = value ^ 0xffffffff;
            for (byte bite : bytes) {
                result = (result >>> 8) ^ CRC32_TABLE[(result ^ bite) & 0xff];
            }

            result = result ^ 0xffffffff;
            return result;
        }

        private CastToIntegerFromIntNode getCastToIntNode() {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CastToIntegerFromIntNode.create(val -> {
                    throw raise(PythonBuiltinClassType.TypeError, "an integer is required (got type %p)", val);
                }));
            }
            return castToIntNode;
        }

        @Specialization
        public long doit(VirtualFrame frame, Object data, @SuppressWarnings("unused") PNone value,
                        @Cached ToBytesNode toBytesNode) {
            return doCRC32(toBytesNode.execute(frame, data));
        }

        @TruffleBoundary
        private long doCRC32(byte[] data) {
            CRC32 crc32 = new CRC32();
            crc32.update(data);
            return crc32.getValue();
        }

        @Specialization
        public long doit(VirtualFrame frame, Object data, long value,
                        @Cached ToBytesNode toBytesNode) {
            // lost magnitude is ok here.
            int initValue = (int) value;
            byte[] array = toBytesNode.execute(frame, data);
            return computeCRC32(array, initValue) & 0xFFFFFFFFL;
        }

        @Specialization
        public long doPInt(VirtualFrame frame, Object data, PInt value,
                        @Cached ToBytesNode toBytesNode) {
            // lost magnitude is ok here.
            int initValue = value.intValue();
            byte[] array = toBytesNode.execute(frame, data);
            return computeCRC32(array, initValue) & 0xFFFFFFFFL;
        }

        @Specialization
        public long doObject(VirtualFrame frame, Object data, Object value,
                        @Cached("create()") Crc32Node recursiveNode) {
            return (long) recursiveNode.execute(frame, data, getCastToIntNode().execute(value));
        }

        protected static Crc32Node create() {
            return ZLibModuleBuiltinsFactory.Crc32NodeFactory.create();
        }
    }

    // zlib.adler32(data[, value])
    @Builtin(name = "adler32", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class Adler32Node extends PythonBinaryBuiltinNode {

        @Child private CastToIntegerFromIntNode castToIntNode;

        private static final int DEFER = 3850;
        private static final int BASE = 65521;

        private static int computeAdler32(byte[] bytes, int value) {
            int index = 0;
            int len = bytes.length;
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
            return result;
        }

        private CastToIntegerFromIntNode getCastToIntNode() {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CastToIntegerFromIntNode.create(val -> {
                    throw raise(PythonBuiltinClassType.TypeError, "an integer is required (got type %p)", val);
                }));
            }
            return castToIntNode;
        }

        @TruffleBoundary
        private long doAdler32(byte[] data) {
            Adler32 adler32 = new Adler32();
            adler32.update(data);
            return adler32.getValue();
        }

        @Specialization
        public long doit(VirtualFrame frame, Object data, @SuppressWarnings("unused") PNone value,
                        @Cached ToBytesNode toBytesNode) {
            return doAdler32(toBytesNode.execute(frame, data));
        }

        @Specialization
        public long doit(VirtualFrame frame, Object data, long value,
                        @Cached ToBytesNode toBytesNode) {
            // lost magnitude is ok here.
            int initValue = (int) value;
            byte[] array = toBytesNode.execute(frame, data);
            return computeAdler32(array, initValue) & 0xFFFFFFFFL;
        }

        @Specialization
        public long doPInt(VirtualFrame frame, Object data, PInt value,
                        @Cached ToBytesNode toBytesNode) {
            // lost magnitude is ok here.
            int initValue = value.intValue();
            byte[] array = toBytesNode.execute(frame, data);
            return computeAdler32(array, initValue) & 0xFFFFFFFFL;
        }

        @Specialization
        public long doObject(VirtualFrame frame, Object data, Object value,
                        @Cached("create()") Adler32Node recursiveNode) {
            return (long) recursiveNode.execute(frame, data, getCastToIntNode().execute(value));
        }

        protected static Adler32Node create() {
            return ZLibModuleBuiltinsFactory.Adler32NodeFactory.create();
        }
    }

    @Builtin(name = "zlib_deflateInit", minNumOfPositionalArgs = 6)
    @GenerateNodeFactory
    abstract static class DeflateInitNode extends PythonBuiltinNode {
        /**
         * @param memLevel is ignored - it mostly affects performance and compression rate, we trust
         *            that the Deflater implementation will work well
         */
        @Specialization
        @TruffleBoundary
        Object deflateInit(int level, int method, int wbits, int memLevel, int strategy, Object zdict) {
            Deflater deflater;
            if (wbits < 0) {
                // generate a RAW stream, i.e., no wrapping
                deflater = new Deflater(level, true);
            } else if (wbits >= 25) {
                // include gzip container
                throw raise(PythonBuiltinClassType.NotImplementedError, "gzip containers");
            } else {
                // wrap stream with zlib header and trailer
                deflater = new Deflater(level, false);
            }

            if (method != DEFLATED) {
                throw raise(PythonBuiltinClassType.ValueError, "only DEFLATED (%d) allowed as method, got %d", DEFLATED, method);
            }
            deflater.setStrategy(strategy);
            if (zdict instanceof String) {
                deflater.setDictionary(((String) zdict).getBytes());
            } else if (!(zdict instanceof PNone)) {
                throw raise(PythonBuiltinClassType.ValueError, "zdict must be a str, not %p", zdict);
            }
            return new DeflaterWrapper(deflater);
        }
    }

    static class DeflaterWrapper implements TruffleObject {
        private final Deflater deflater;

        public DeflaterWrapper(Deflater deflater) {
            this.deflater = deflater;
        }
    }

    @Builtin(name = "zlib_deflateCompress", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class DeflateCompress extends PythonTernaryBuiltinNode {
        @Child private ToBytesNode toBytes = ToBytesNode.create();

        @Specialization
        Object deflateCompress(VirtualFrame frame, DeflaterWrapper stream, Object pb, int mode) {
            byte[] data = toBytes.execute(frame, pb);
            byte[] result = new byte[DEF_BUF_SIZE];

            ByteArrayOutputStream baos = deflate(stream, mode, data, result);

            return factory().createBytes(baos.toByteArray());
        }

        @TruffleBoundary
        private static ByteArrayOutputStream deflate(DeflaterWrapper stream, int mode, byte[] data, byte[] result) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            stream.deflater.setInput(data);
            int deflateMode = mode;
            if (mode == Z_FINISH) {
                deflateMode = Z_SYNC_FLUSH;
                stream.deflater.finish();
            }

            int bytesWritten = result.length;
            while (bytesWritten == result.length) {
                bytesWritten = stream.deflater.deflate(result, 0, result.length, deflateMode);
                baos.write(result, 0, bytesWritten);
            }

            if (mode == Z_FINISH) {
                stream.deflater.end();
            }
            return baos;
        }
    }

    @Builtin(name = "zlib_inflateInit", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InflateInitNode extends PythonBinaryBuiltinNode {
        @Child private ToBytesNode toBytes = ToBytesNode.create();

        @Specialization
        Object init(VirtualFrame frame, int wbits, PBytes zdict) {
            byte[] bytes = toBytes.execute(frame, zdict);
            return new InflaterWrapper(inflate(wbits, bytes));
        }

        @TruffleBoundary
        private Inflater inflate(int wbits, byte[] bytes) {
            Inflater inflater;
            if (wbits < 0) {
                // generate a RAW stream, i.e., no wrapping
                inflater = new Inflater(true);
            } else if (wbits >= 25) {
                // include gzip container
                throw raise(PythonBuiltinClassType.NotImplementedError, "gzip containers");
            } else {
                // wrap stream with zlib header and trailer
                inflater = new Inflater(false);
            }

            inflater.setDictionary(bytes);
            return inflater;
        }
    }

    static class InflaterWrapper implements TruffleObject {
        private final Inflater inflater;

        public InflaterWrapper(Inflater inflater) {
            this.inflater = inflater;
        }

        @TruffleBoundary(allowInlining = true)
        private boolean needsInput() {
            return inflater.needsInput();
        }

        @TruffleBoundary(allowInlining = true)
        private int getRemaining() {
            return inflater.getRemaining();
        }
    }

    @Builtin(name = "zlib_inflateDecompress", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class InflaterDecompress extends PythonTernaryBuiltinNode {
        @Child private ToBytesNode toBytes = ToBytesNode.create();

        @Specialization
        Object decompress(VirtualFrame frame, InflaterWrapper stream, PIBytesLike pb, int maxLen) {
            int maxLength = maxLen == 0 ? Integer.MAX_VALUE : maxLen;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] data = toBytes.execute(frame, pb);
            byte[] result = new byte[DEF_BUF_SIZE];
            byte[] decompressed = decompress(stream, maxLength, baos, data, result);

            return factory().createTuple(new Object[]{
                            factory().createBytes(decompressed),
                            stream.inflater.finished(),
                            stream.getRemaining()
            });
        }

        @TruffleBoundary
        private byte[] decompress(InflaterWrapper stream, int maxLength, ByteArrayOutputStream baos, byte[] data, byte[] result) {
            stream.inflater.setInput(data);
            int bytesWritten = result.length;
            while (baos.size() < maxLength && bytesWritten == result.length) {
                try {
                    bytesWritten = stream.inflater.inflate(result, 0, result.length);
                } catch (DataFormatException e) {
                    throw raise(ZLibError, e);
                }
                baos.write(result, 0, bytesWritten);
            }
            byte[] decompressed = baos.toByteArray();
            return decompressed;
        }
    }

    // zlib.compress(data, level=-1)
    @Builtin(name = "compress", minNumOfPositionalArgs = 1, parameterNames = {"", "level"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class CompressNode extends PythonBinaryBuiltinNode {

        @Child private SequenceStorageNodes.ToByteArrayNode toArrayNode;

        private SequenceStorageNodes.ToByteArrayNode getToArrayNode() {
            if (toArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create());
            }
            return toArrayNode;
        }

        @TruffleBoundary
        private static byte[] compress(byte[] input, int level) {
            Deflater compresser = new Deflater(level);
            compresser.setInput(input);
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

        @Specialization
        public PBytes doit(VirtualFrame frame, PIBytesLike data, @SuppressWarnings("unused") PNone level) {
            byte[] array = getToArrayNode().execute(frame, data.getSequenceStorage());
            return factory().createBytes(compress(array, -1));
        }

        @Specialization
        public PBytes doit(VirtualFrame frame, PIBytesLike data, long level,
                        @Cached("createBinaryProfile()") ConditionProfile wrongLevelProfile) {
            if (wrongLevelProfile.profile(level < -1 || 9 < level)) {
                throw raise(ZLibError, "Bad compression level");
            }
            byte[] array = getToArrayNode().execute(frame, data.getSequenceStorage());
            return factory().createBytes(compress(array, (int) level));
        }

    }

    // zlib.decompress(data, wbits=MAX_WBITS, bufsize=DEF_BUF_SIZE)
    @Builtin(name = "decompress", minNumOfPositionalArgs = 1, parameterNames = {"data", "wbits", "bufsize"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DecompressNode extends PythonTernaryBuiltinNode {

        @Child private CastToIntegerFromIntNode castToIntNode;
        @Child private SequenceStorageNodes.ToByteArrayNode toArrayNode;

        private final ConditionProfile bufSizeProfile = ConditionProfile.createBinaryProfile();

        private SequenceStorageNodes.ToByteArrayNode getToArrayNode() {
            if (toArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create());
            }
            return toArrayNode;
        }

        private CastToIntegerFromIntNode getCastToIntNode() {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CastToIntegerFromIntNode.create(val -> {
                    throw raise(PythonBuiltinClassType.TypeError, "an integer is required (got type %p)", val);
                }));
            }
            return castToIntNode;
        }

        @TruffleBoundary
        private byte[] decompress(byte[] data, @SuppressWarnings("unused") long wbits, int bufsize) {
            // decompress
            // We don't use wbits currently. There is no easy way how to map to java Inflater.
            Inflater decompresser = new Inflater();
            decompresser.setInput(data);
            byte[] resultArray = new byte[bufsize];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                while (!decompresser.finished()) {
                    int howmany = decompresser.inflate(resultArray);
                    if (howmany == 0 && decompresser.needsInput()) {
                        throw raise(ZLibError, "Error -5 while decompressing data: incomplete or truncated stream");
                    }
                    baos.write(resultArray, 0, howmany);
                }
            } catch (DataFormatException e) {
                throw raise(ZLibError, "while preparing to decompress data");
            }
            decompresser.end();
            return baos.toByteArray();
        }

        @Specialization
        public PBytes doit(VirtualFrame frame, PIBytesLike data, @SuppressWarnings("unused") PNone wbits, @SuppressWarnings("unused") PNone bufsize) {
            byte[] array = getToArrayNode().execute(frame, data.getSequenceStorage());
            return factory().createBytes(decompress(array, MAX_WBITS, DEF_BUF_SIZE));
        }

        @Specialization
        public PBytes decompress(VirtualFrame frame, PIBytesLike data, byte wbits, int bufsize) {
            return decompress(frame, data, (long) wbits, bufsize);
        }

        @Specialization
        public PBytes decompress(VirtualFrame frame, PIBytesLike data, long wbits, int bufsize) {
            // checking bufsize
            if (bufSizeProfile.profile(bufsize < 0)) {
                throw raise(ZLibError, "bufsize must be non-negative");
            }
            byte[] array = getToArrayNode().execute(frame, data.getSequenceStorage());
            return factory().createBytes(decompress(array, (int) wbits, bufsize == 0 ? 1 : bufsize));
        }

        @Specialization
        public PBytes decompress(VirtualFrame frame, PIBytesLike data, long wbits, Object bufsize,
                        @Cached("create()") DecompressNode recursiveNode) {
            Object bufferLen = getCastToIntNode().execute(bufsize);
            return (PBytes) recursiveNode.execute(frame, data, wbits, bufferLen);
        }

        protected static DecompressNode create() {
            return ZLibModuleBuiltinsFactory.DecompressNodeFactory.create();
        }
    }
}
