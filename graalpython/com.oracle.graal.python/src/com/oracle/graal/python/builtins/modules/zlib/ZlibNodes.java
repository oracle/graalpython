/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.DEF_BUF_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_ALLOCATE_MEMORY_FOR_S_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INCOMPLETE_OR_TRUNCATED_STREAM;
import static com.oracle.graal.python.nodes.ErrorMessages.INCONSISTENT_STREAM_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_DICTIONARY;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_INITIALIZATION_OPTION;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_INPUT_DATA;
import static com.oracle.graal.python.nodes.ErrorMessages.LIBRARY_VERSION_MISMATCH;
import static com.oracle.graal.python.nodes.ErrorMessages.OUT_OF_MEMORY_WHILE_S_DATA;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_COPYING_S_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_CREATING_S_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_FINISHING_S;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_FLUSHING;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_PREPARING_TO_S_DATA;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_SETTING_ZDICT;
import static com.oracle.graal.python.nodes.ErrorMessages.WHILE_S_DATA;
import static com.oracle.graal.python.runtime.NFIZlibSupport.OUTPUT_OPTION;
import static com.oracle.graal.python.runtime.NFIZlibSupport.UNCONSUMED_TAIL_OPTION;
import static com.oracle.graal.python.runtime.NFIZlibSupport.UNUSED_DATA_OPTION;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class ZlibNodes {

    /*- Return codes for the compression/decompression functions.
        Negative values are errors, positive values are used for special but normal events. */
    public static final int Z_OK = 0;
    public static final int Z_STREAM_END = 1;
    public static final int Z_NEED_DICT = 2;
    public static final int Z_ERRNO = -1;
    public static final int Z_STREAM_ERROR = -2;
    public static final int Z_DATA_ERROR = -3;
    public static final int Z_MEM_ERROR = -4;
    public static final int Z_BUF_ERROR = -5;
    public static final int Z_VERSION_ERROR = -6;

    protected static void deallocateStream(Object zst, NFIZlibSupport zlibSupport, NativeLibrary.InvokeNativeFunction deallocateStream, boolean deallocate) {
        if (deallocate) {
            zlibSupport.deallocateStream(zst, deallocateStream);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ZlibNativeCompressObj extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, NativeZlibCompObject self, PythonContext context, byte[] bytes, int len);

        @Specialization
        static byte[] nativeCompress(Node inliningTarget, NativeZlibCompObject self, PythonContext context, byte[] bytes, int len,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction compressObj,
                        @Cached GetNativeBufferNode getBuffer,
                        @Cached ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            self.lastInput = context.getEnv().asGuestValue(bytes);
            int err = zlibSupport.compressObj(self.getZst(), self.lastInput, len, DEF_BUF_SIZE, compressObj);
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
            }
            return getBuffer.getOutputBuffer(inliningTarget, self.getZst(), context);
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ZlibNativeCompress extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, byte[] bytes, int len, int level, int wbits);

        @Specialization
        static byte[] nativeCompress(Node inliningTarget, byte[] bytes, int len, int level, int wbits,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction createStream,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction deflateOffHeap,
                        @Cached GetNativeBufferNode getBuffer,
                        @Cached ZlibNativeErrorHandling errorHandling) {
            PythonContext context = PythonContext.get(inliningTarget);
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            Object in = context.getEnv().asGuestValue(bytes);
            Object zst = zlibSupport.createStream(createStream);
            int err = zlibSupport.deflateOffHeap(zst, in, len, DEF_BUF_SIZE, level, wbits, deflateOffHeap);
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, zst, err, zlibSupport, true);
            }
            byte[] resultArray = getBuffer.getOutputBuffer(inliningTarget, zst, context);
            zlibSupport.deallocateStream(zst, deallocateStream);
            return resultArray;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ZlibNativeDecompressObj extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, NativeZlibCompObject self, PythonContext context, byte[] bytes, int len, int maxLength);

        @Specialization
        static byte[] nativeDecompress(Node inliningTarget, NativeZlibCompObject self, PythonContext context, byte[] bytes, int len, int maxLength,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction decompressObj,
                        @Cached GetNativeBufferNode getBuffer,
                        @Cached ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            Object in = context.getEnv().asGuestValue(bytes);
            int err = zlibSupport.decompressObj(self.getZst(), in, len, DEF_BUF_SIZE, maxLength, decompressObj);
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, self.getZst(), err, zlibSupport, false);
            }
            return getBuffer.getOutputBuffer(inliningTarget, self.getZst(), context);
        }

    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ZlibNativeDecompressor extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, ZlibDecompressorObject self, PythonContext context, byte[] bytes, int len, int maxLength);

        @Specialization
        static byte[] nativeDecompressBuf(Node inliningTarget, ZlibDecompressorObject self, PythonContext context, byte[] bytes, int len, int maxLength,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction decompressor,
                        @Cached GetNativeBufferNode getBuffer,
                        @Cached ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            Object in = context.getEnv().asGuestValue(bytes);
            int ret = zlibSupport.decompressor(self.getZst(), in, len, maxLength, decompressor);
            if (ret < 0) {
                errorHandling.execute(inliningTarget, self.getZst(), ret, zlibSupport, false);
            }
            self.setNeedsInput(ret == 1);
            return getBuffer.getOutputBuffer(inliningTarget, self.getZst(), context);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class ZlibNativeDecompress extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, byte[] bytes, int len, int wbits, int bufsize, PythonContext context);

        @Specialization
        static byte[] nativeCompress(Node inliningTarget, byte[] bytes, int len, int wbits, int bufsize, PythonContext context,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction createStream,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction inflateOffHeap,
                        @Cached GetNativeBufferNode getBuffer,
                        @Cached ZlibNativeErrorHandling errorHandling) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            Object zst = zlibSupport.createStream(createStream);
            Object in = context.getEnv().asGuestValue(bytes);
            int err = zlibSupport.inflateOffHeap(zst, in, len, bufsize, wbits, inflateOffHeap);
            if (err != Z_OK) {
                errorHandling.execute(inliningTarget, zst, err, zlibSupport, true);
            }
            byte[] resultArray = getBuffer.getOutputBuffer(inliningTarget, zst, context);
            zlibSupport.deallocateStream(zst, deallocateStream);
            return resultArray;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ZlibNativeErrorHandling extends Node {

        public abstract void execute(Node inliningTarget, Object zst, int err, NFIZlibSupport zlibSupport, boolean deallocate);

        @Specialization
        static void doError(Object zst, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Cached(inline = false) ZlibFunctionNativeErrorHandling errorHandling,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction getErrorFunction) {
            errorHandling.execute(zst, zlibSupport.getErrorFunction(zst, getErrorFunction), err, zlibSupport, deallocate);
        }
    }

    @ImportStatic(ZlibNodes.class)
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ZlibNativeErrorMsg extends Node {

        public abstract void execute(Object zst, int err, TruffleString msg, NFIZlibSupport zlibSupport, boolean deallocate);

        @SuppressWarnings("unused")
        @Specialization(guards = "err == Z_VERSION_ERROR")
        static void doVersionError(Object zst, int err, TruffleString msg, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("r") @Cached PRaiseNode raise) {
            /*
             * In case of a version mismatch, comp.msg won't be initialized. Check for this case
             * first, before looking at comp->zst.msg.
             */
            deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
            throw raise.raise(inliningTarget, ZLibError, ERROR_D_S_S, err, msg, LIBRARY_VERSION_MISMATCH);
        }

        @Specialization(guards = "err != Z_VERSION_ERROR")
        static void doError(Object zst, int err, TruffleString msg, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Exclusive @Cached NativeLibrary.InvokeNativeFunction hasStreamErrorMsg,
                        @Exclusive @Cached NativeLibrary.InvokeNativeFunction getStreamErrorMsg) {
            TruffleString zmsg = null;
            if (zlibSupport.hasStreamErrorMsg(zst, hasStreamErrorMsg) == 1) {
                zmsg = zlibSupport.getStreamErrorMsg(zst, getStreamErrorMsg);
            } else {
                switch (err) {
                    case Z_BUF_ERROR:
                        zmsg = INCOMPLETE_OR_TRUNCATED_STREAM;
                        break;
                    case Z_STREAM_ERROR:
                        zmsg = INCONSISTENT_STREAM_STATE;
                        break;
                    case Z_DATA_ERROR:
                        zmsg = INVALID_INPUT_DATA;
                        break;
                }
            }
            deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
            if (zmsg == null) {
                throw raise.raise(inliningTarget, ZLibError, ERROR_D_S, err, msg);
            } else {
                throw raise.raise(inliningTarget, ZLibError, ERROR_D_S_S, err, msg, zmsg);
            }
        }
    }

    @ImportStatic({NFIZlibSupport.class, ZLibModuleBuiltins.class})
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class ZlibFunctionNativeErrorHandling extends Node {

        public abstract void execute(Object zst, int function, int err, NFIZlibSupport zlibSupport, boolean deallocate);

        @Specialization(guards = "function == DEFLATE_INIT_ERROR")
        static void deflateInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, MemoryError, OUT_OF_MEMORY_WHILE_S_DATA, "compressing");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, ZLibError, ErrorMessages.BAD_COMPRESSION_LEVEL);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_S_DATA, "compressing"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_OBJ_ERROR")
        static void deflateObjInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "compression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, ValueError, INVALID_INITIALIZATION_OPTION);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_CREATING_S_OBJECT, "compression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_COPY_ERROR")
        static void deflateCopyError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "compression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, ValueError, INCONSISTENT_STREAM_STATE);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_COPYING_S_OBJECT, "compression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == INFLATE_COPY_ERROR")
        static void inflateCopyError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "decompression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, ValueError, INCONSISTENT_STREAM_STATE);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_COPYING_S_OBJECT, "compression"), zlibSupport, deallocate);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "function == DEFLATE_DICT_ERROR")
        static void deflateDictError(Object zst, int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("r") @Cached PRaiseNode raise) {
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, ValueError, INVALID_DICTIONARY);
            }
            throw raise.raise(inliningTarget, ValueError, ErrorMessages.DEFLATED_SET_DICT);

        }

        @Specialization(guards = "function == INFLATE_INIT_ERROR")
        static void inflateInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, MemoryError, OUT_OF_MEMORY_WHILE_S_DATA, "decompressing");
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_PREPARING_TO_S_DATA, "decompress"), zlibSupport, deallocate);

        }

        @Specialization(guards = "function == INFLATE_OBJ_ERROR")
        static void inflateObjInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "decompression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(inliningTarget, ValueError, INVALID_INITIALIZATION_OPTION);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_CREATING_S_OBJECT, "decompression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == INFLATE_DICT_ERROR")
        static void inflateDictError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError) {
            zlibError.execute(zst, err, WHILE_SETTING_ZDICT, zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_END_ERROR")
        static void deflateEndError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            zlibError.execute(zst, err, formatNode.format(WHILE_FINISHING_S, "compression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == INFLATE_END_ERROR")
        static void inflateEndError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            zlibError.execute(zst, err, formatNode.format(WHILE_FINISHING_S, "decompression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_ERROR")
        static void deflateError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            zlibError.execute(zst, err, formatNode.format(WHILE_S_DATA, "compressing"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == INFLATE_ERROR")
        static void inflateError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            zlibError.execute(zst, err, formatNode.format(WHILE_S_DATA, "decompressing"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_FLUSH_ERROR")
        static void deflateFlushError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError) {
            zlibError.execute(zst, err, WHILE_FLUSHING, zlibSupport, deallocate);
        }

        @Specialization(guards = "function == INFLATE_FLUSH_ERROR")
        static void inflateFlushError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError) {
            zlibError.execute(zst, err, WHILE_FLUSHING, zlibSupport, deallocate);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "function == MEMORY_ERROR")
        static void memError(Object zst, int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Bind("this") Node inliningTarget,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("r") @Cached PRaiseNode raise) {
            deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
            throw raise.raise(inliningTarget, MemoryError);
        }

        @SuppressWarnings("unused")
        @Fallback
        void fallback(Object zst, int function, int err, NFIZlibSupport zlibSupport, boolean deallocate) {
            throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.UNHANDLED_ERROR);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NativeDeallocation extends PNodeWithContext {

        public abstract void execute(Node inliningTarget, NativeZlibCompObject self, PythonContext context, boolean isCompressObj);

        @Specialization(guards = "isCompressObj")
        static void doCompressObj(NativeZlibCompObject self, PythonContext context,
                        @SuppressWarnings("unused") boolean isCompressObj,
                        @Shared @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream) {
            context.getNFIZlibSupport().deallocateStream(self.getZst(), deallocateStream);
            self.setEof(true);
            self.markReleased();
        }

        @Specialization(guards = "!isCompressObj")
        static void doDecompressObj(Node inliningTarget, NativeZlibCompObject self, PythonContext context,
                        @SuppressWarnings("unused") boolean isCompressObj,
                        @Cached GetNativeBufferNode getUnusedDataBuffer,
                        @Cached GetNativeBufferNode getUnconsumedBuffer,
                        @Shared @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream) {
            byte[] unusedData = getUnusedDataBuffer.getUnusedDataBuffer(inliningTarget, self.getZst(), context);
            PythonLanguage language = context.getLanguage(inliningTarget);
            self.setUnusedData(PFactory.createBytes(language, unusedData));
            byte[] unconsumed = getUnconsumedBuffer.getUnconsumedTailBuffer(inliningTarget, self.getZst(), context);
            self.setUnconsumedTail(PFactory.createBytes(language, unconsumed));
            context.getNFIZlibSupport().deallocateStream(self.getZst(), deallocateStream);
            self.setEof(true);
            self.markReleased();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetNativeBufferNode extends PNodeWithContext {

        public abstract byte[] execute(Node inliningTarget, Object zst, int option, PythonContext context);

        public byte[] getOutputBuffer(Node inliningTarget, Object zst, PythonContext context) {
            return execute(inliningTarget, zst, OUTPUT_OPTION, context);
        }

        public byte[] getUnusedDataBuffer(Node inliningTarget, Object zst, PythonContext context) {
            return execute(inliningTarget, zst, UNUSED_DATA_OPTION, context);
        }

        public byte[] getUnconsumedTailBuffer(Node inliningTarget, Object zst, PythonContext context) {
            return execute(inliningTarget, zst, UNCONSUMED_TAIL_OPTION, context);
        }

        @Specialization
        static byte[] getBuffer(Object zst, int option, PythonContext context,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction getBufferSize,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction getBuffer) {
            NFIZlibSupport zlibSupport = context.getNFIZlibSupport();
            int size = zlibSupport.getBufferSize(zst, option, getBufferSize);
            if (size == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            byte[] resultArray = new byte[size];
            Object out = context.getEnv().asGuestValue(resultArray);
            zlibSupport.getBuffer(zst, option, out, getBuffer);
            return resultArray;
        }
    }

}
