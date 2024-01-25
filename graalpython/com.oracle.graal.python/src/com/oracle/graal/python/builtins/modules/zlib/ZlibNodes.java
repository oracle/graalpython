/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.Z_FINISH;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.Z_SYNC_FLUSH;
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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
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

        public abstract byte[] execute(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext context, byte[] bytes, int len);

        @Specialization
        static byte[] nativeCompress(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext context, byte[] bytes, int len,
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

        public abstract byte[] execute(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext context, byte[] bytes, int len, int maxLength);

        @Specialization
        static byte[] nativeDecompress(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext context, byte[] bytes, int len, int maxLength,
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
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("r") @Cached PRaiseNode raise) {
            /*
             * In case of a version mismatch, comp.msg won't be initialized. Check for this case
             * first, before looking at comp->zst.msg.
             */
            deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
            throw raise.raise(ZLibError, ERROR_D_S_S, err, msg, LIBRARY_VERSION_MISMATCH);
        }

        @Specialization(guards = "err != Z_VERSION_ERROR")
        static void doError(Object zst, int err, TruffleString msg, NFIZlibSupport zlibSupport, boolean deallocate,
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
                throw raise.raise(ZLibError, ERROR_D_S, err, msg);
            } else {
                throw raise.raise(ZLibError, ERROR_D_S_S, err, msg, zmsg);
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
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(MemoryError, OUT_OF_MEMORY_WHILE_S_DATA, "compressing");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(ZLibError, ErrorMessages.BAD_COMPRESSION_LEVEL);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_S_DATA, "compressing"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_OBJ_ERROR")
        static void deflateObjInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "compression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(ValueError, INVALID_INITIALIZATION_OPTION);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_CREATING_S_OBJECT, "compression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == DEFLATE_COPY_ERROR")
        static void deflateCopyError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "compression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(ValueError, INCONSISTENT_STREAM_STATE);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_COPYING_S_OBJECT, "compression"), zlibSupport, deallocate);
        }

        @Specialization(guards = "function == INFLATE_COPY_ERROR")
        static void inflateCopyError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "decompression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(ValueError, INCONSISTENT_STREAM_STATE);
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_COPYING_S_OBJECT, "compression"), zlibSupport, deallocate);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "function == DEFLATE_DICT_ERROR")
        static void deflateDictError(Object zst, int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("r") @Cached PRaiseNode raise) {
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(ValueError, INVALID_DICTIONARY);
            }
            throw raise.raise(ValueError, ErrorMessages.DEFLATED_SET_DICT);

        }

        @Specialization(guards = "function == INFLATE_INIT_ERROR")
        static void inflateInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(MemoryError, OUT_OF_MEMORY_WHILE_S_DATA, "decompressing");
            }
            zlibError.execute(zst, err, formatNode.format(WHILE_PREPARING_TO_S_DATA, "decompress"), zlibSupport, deallocate);

        }

        @Specialization(guards = "function == INFLATE_OBJ_ERROR")
        static void inflateObjInitError(Object zst, @SuppressWarnings("unused") int function, int err, NFIZlibSupport zlibSupport, boolean deallocate,
                        @Shared("r") @Cached PRaiseNode raise,
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("err") @Cached ZlibNativeErrorMsg zlibError,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode formatNode) {
            if (err == Z_MEM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(MemoryError, CANT_ALLOCATE_MEMORY_FOR_S_OBJECT, "decompression");
            }
            if (err == Z_STREAM_ERROR) {
                deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
                throw raise.raise(ValueError, INVALID_INITIALIZATION_OPTION);
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
                        @Shared("d") @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Shared("r") @Cached PRaiseNode raise) {
            deallocateStream(zst, zlibSupport, deallocateStream, deallocate);
            throw raise.raise(MemoryError);
        }

        @SuppressWarnings("unused")
        @Fallback
        void fallback(Object zst, int function, int err, NFIZlibSupport zlibSupport, boolean deallocate) {
            throw PRaiseNode.raiseUncached(this, SystemError, ErrorMessages.UNHANDLED_ERROR);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NativeDeallocation extends PNodeWithContext {

        public abstract void execute(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext context, PythonObjectFactory factory, boolean isCompressObj);

        @Specialization(guards = "isCompressObj")
        static void doCompressObj(ZLibCompObject.NativeZlibCompObject self, PythonContext context, @SuppressWarnings("unused") PythonObjectFactory factory,
                        @SuppressWarnings("unused") boolean isCompressObj,
                        @Shared @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream) {
            context.getNFIZlibSupport().deallocateStream(self.getZst(), deallocateStream);
            self.setEof(true);
            self.markReleased();
        }

        @Specialization(guards = "!isCompressObj")
        static void doDecompressObj(Node inliningTarget, ZLibCompObject.NativeZlibCompObject self, PythonContext context, PythonObjectFactory factory,
                        @SuppressWarnings("unused") boolean isCompressObj,
                        @Cached GetNativeBufferNode getUnusedDataBuffer,
                        @Cached GetNativeBufferNode getUnconsumedBuffer,
                        @Shared @Cached(inline = false) NativeLibrary.InvokeNativeFunction deallocateStream) {
            byte[] unusedData = getUnusedDataBuffer.getUnusedDataBuffer(inliningTarget, self.getZst(), context);
            self.setUnusedData(factory.createBytes(unusedData));
            byte[] unconsumed = getUnconsumedBuffer.getUnconsumedTailBuffer(inliningTarget, self.getZst(), context);
            self.setUnconsumedTail(factory.createBytes(unconsumed));
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

    abstract static class JavaCompressNode {
        private JavaCompressNode() {
        }

        @TruffleBoundary
        public static byte[] execute(ZLibCompObject.JavaZlibCompObject self, int mode) {
            byte[] result = new byte[DEF_BUF_SIZE];
            Deflater deflater = (Deflater) self.stream;
            int deflateMode = mode;
            if (mode == Z_FINISH) {
                deflateMode = Z_SYNC_FLUSH;
                deflater.finish();
            }

            int bytesWritten = result.length;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (bytesWritten == result.length) {
                bytesWritten = deflater.deflate(result, 0, result.length, deflateMode);
                baos.write(result, 0, bytesWritten);
            }

            if (mode == Z_FINISH) {
                deflater.end();
                self.setUninitialized();
            }
            return baos.toByteArray();
        }
    }

    static final class JavaDecompressor {
        @TruffleBoundary
        private static byte[] createByteArray(ZLibCompObject.JavaZlibCompObject self, Inflater inflater, byte[] bytes, int length, int maxLength, int bufSize, Node nodeForRaise) {
            int maxLen = maxLength == 0 ? Integer.MAX_VALUE : maxLength;
            byte[] result = new byte[Math.min(maxLen, bufSize)];
            boolean zdictIsSet = false;

            self.setInflaterInput(bytes, length, nodeForRaise);

            int bytesWritten = result.length;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (baos.size() < maxLen && bytesWritten == result.length) {
                try {
                    int len = Math.min(maxLen - baos.size(), result.length);
                    bytesWritten = inflater.inflate(result, 0, len);
                    if (bytesWritten == 0 && !zdictIsSet && inflater.needsDictionary()) {
                        if (self.getZdict().length > 0) {
                            inflater.setDictionary(self.getZdict());
                            zdictIsSet = true;
                            // we inflate again with a dictionary
                            bytesWritten = inflater.inflate(result, 0, len);
                        } else {
                            throw PRaiseNode.raiseUncached(nodeForRaise, ZLibError, WHILE_SETTING_ZDICT);
                        }
                    }
                } catch (DataFormatException e) {
                    throw PRaiseNode.raiseUncached(nodeForRaise, ZLibError, e);
                }
                baos.write(result, 0, bytesWritten);
            }
            return baos.toByteArray();
        }

        public static byte[] execute(VirtualFrame frame, ZLibCompObject.JavaZlibCompObject self, byte[] bytes, int length, int maxLength, int bufSize,
                        Node nodeForRaise, PythonObjectFactory factory, BytesNodes.ToBytesNode toBytesNode) {
            Inflater inflater = (Inflater) self.stream;
            byte[] result = createByteArray(self, inflater, bytes, length, maxLength, bufSize, nodeForRaise);
            self.setEof(isFinished(inflater));
            byte[] unusedDataBytes = toBytesNode.execute(frame, self.getUnusedData());
            int unconsumedTailLen = self.getUnconsumedTail().getSequenceStorage().length();
            saveUnconsumedInput(self, bytes, length, unusedDataBytes, unconsumedTailLen, factory);
            return result;
        }

        private static void saveUnconsumedInput(ZLibCompObject.JavaZlibCompObject self, byte[] data, int length,
                        byte[] unusedDataBytes, int unconsumedTailLen, PythonObjectFactory factory) {
            Inflater inflater = (Inflater) self.stream;
            int unusedLen = getRemaining(inflater);
            byte[] tail = PythonUtils.arrayCopyOfRange(data, length - unusedLen, length);
            if (self.isEof()) {
                if (unconsumedTailLen > 0) {
                    self.setUnconsumedTail(factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY));
                }
                if (unusedDataBytes.length > 0 && tail.length > 0) {
                    byte[] newUnusedData = new byte[unusedDataBytes.length + tail.length];
                    PythonUtils.arraycopy(unusedDataBytes, 0, newUnusedData, 0, unusedDataBytes.length);
                    PythonUtils.arraycopy(tail, 0, newUnusedData, unusedDataBytes.length, tail.length);
                    self.setUnusedData(factory.createBytes(newUnusedData));
                } else if (tail.length > 0) {
                    self.setUnusedData(factory.createBytes(tail));
                }
            } else {
                self.setUnconsumedTail(factory.createBytes(tail));
            }
        }

        @TruffleBoundary
        public static int getRemaining(Inflater inflater) {
            return inflater.getRemaining();
        }

        @TruffleBoundary
        public static int getBytesRead(Inflater inflater) {
            try {
                return PInt.intValueExact(inflater.getBytesRead());
            } catch (OverflowException e) {
                throw CompilerDirectives.shouldNotReachHere("input size is larger than an int!");
            }
        }

        @TruffleBoundary
        public static boolean isFinished(Inflater inflater) {
            return inflater.finished();
        }
    }

}
