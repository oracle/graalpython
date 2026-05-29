/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.annotations.NativeSimpleType.DOUBLE;
import static com.oracle.graal.python.annotations.NativeSimpleType.POINTER;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT32;
import static com.oracle.graal.python.annotations.NativeSimpleType.SINT64;
import static com.oracle.graal.python.annotations.NativeSimpleType.VOID;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.DowncallSignature;
import com.oracle.graal.python.runtime.nativeaccess.NativeLibrary;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory;
import com.oracle.graal.python.runtime.nativeaccess.NativeMemory.ZeroTerminatedUtf8ToTruffleStringNode;
import com.oracle.truffle.api.ThreadLocalAction.Access;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.strings.TruffleString;

public class NFIZlibSupport extends NativeCompressionSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIZlibSupport.class);
    private static final String SUPPORTING_NATIVE_LIB_NAME = "zsupport";

    public static final int NO_ERROR = 0;
    public static final int DEFLATE_INIT_ERROR = 101;
    public static final int DEFLATE_END_ERROR = 102;
    public static final int DEFLATE_DICT_ERROR = 103;
    public static final int DEFLATE_OBJ_ERROR = 104;
    public static final int DEFLATE_FLUSH_ERROR = 105;
    public static final int DEFLATE_COPY_ERROR = 106;
    public static final int DEFLATE_ERROR = 107;
    public static final int INFLATE_INIT_ERROR = 201;
    public static final int INFLATE_END_ERROR = 202;
    public static final int INFLATE_DICT_ERROR = 203;
    public static final int INFLATE_OBJ_ERROR = 204;
    public static final int INFLATE_FLUSH_ERROR = 205;
    public static final int INFLATE_COPY_ERROR = 206;
    public static final int INFLATE_ERROR = 207;
    public static final int INCOMPLETE_ERROR = 99;
    public static final int MEMORY_ERROR = 999;
    public static final int OUTPUT_OPTION = 0;
    public static final int UNUSED_DATA_OPTION = 1;
    public static final int UNCONSUMED_TAIL_OPTION = 2;
    public static final int ZDICT_OPTION = 3;

    abstract static class ZlibNativeFunctions {
        @DowncallSignature(returnType = POINTER)
        abstract long zlib_get_version();

        @DowncallSignature(returnType = POINTER)
        abstract long zlib_get_runtime_version();

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT64, POINTER, SINT32})
        abstract long zlib_crc32(long crc, long buf, int len);

        @DowncallSignature(returnType = SINT64, argumentTypes = {SINT64, POINTER, SINT32})
        abstract long zlib_adler32(long crc, long buf, int len);

        @DowncallSignature(returnType = POINTER)
        abstract long zlib_create_zlib_stream();

        @DowncallSignature(returnType = DOUBLE, argumentTypes = {POINTER})
        abstract double zlib_get_timeElapsed(long zst);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER})
        abstract void zlib_free_stream(long zst);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER})
        abstract void zlib_gc_helper(long zst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int zlib_get_error_type(long zst);

        @DowncallSignature(returnType = POINTER, argumentTypes = {POINTER})
        abstract long zlib_get_stream_msg(long zst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int zlib_has_stream_msg(long zst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int zlib_get_eof(long zst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER})
        abstract int zlib_get_is_initialised(long zst);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32})
        abstract int zlib_get_buffer_size(long zst, int option);

        @DowncallSignature(returnType = VOID, argumentTypes = {POINTER, SINT32, POINTER})
        abstract void zlib_get_off_heap_buffer(long zst, int option, long dest);

        @DowncallSignature(returnType = POINTER)
        abstract long zlib_create_compobject();

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64, SINT32, SINT32})
        abstract int zlib_deflate_off_heap(long zst, long in, long inLen, long bufSize, int level, int wbits);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64, SINT32})
        abstract int zlib_inflate_off_heap(long zst, long in, long inLen, long bufSize, int wbits);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, SINT32, SINT32, SINT32, SINT32, POINTER, SINT64})
        abstract int zlib_Compress_init(long zst, int level, int method, int wbits, int memLevel, int strategy, long dict, long dictLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, SINT32, SINT32, SINT32, SINT32})
        abstract int zlib_Compress_init_no_dict(long zst, int level, int method, int wbits, int memLevel, int strategy);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64})
        abstract int zlib_Compress_obj(long zst, long in, long inLen, long bufSize);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT32})
        abstract int zlib_Compress_flush(long zst, long in, long bufSize, int mode);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int zlib_Compress_copy(long zst, long newCopy);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32, POINTER, SINT64})
        abstract int zlib_Decompress_init(long zst, int wbits, long dict, long dictLen);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT32})
        abstract int zlib_Decompress_init_no_dict(long zst, int wbits);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64, SINT64})
        abstract int zlib_Decompress_obj(long zst, long in, long inLen, long bufSize, long maxLength);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, SINT64})
        abstract int zlib_Decompress_flush(long zst, long length);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER})
        abstract int zlib_Decompress_copy(long zst, long newCopy);

        @DowncallSignature(returnType = SINT32, argumentTypes = {POINTER, POINTER, SINT64, SINT64})
        abstract int zlib_decompress(long zst, long data, long len, long maxLength);

        static NativeLibrary loadNativeLibrary(PythonContext context) {
            return NativeCompressionSupport.loadNativeLibrary(context, SUPPORTING_NATIVE_LIB_NAME);
        }
    }

    private final ZlibNativeFunctions nativeFunctions;

    private NFIZlibSupport(PythonContext context) {
        super(context);
        this.nativeFunctions = isAvailable() ? new ZlibNativeFunctionsGen(context) : null;
    }

    public static NFIZlibSupport createNative(PythonContext context, String noNativeAccessHelp) {
        return new NFIZlibSupport(context);
    }

    static class PointerReleaseCallback implements AsyncHandler.AsyncAction {
        private final Pointer pointer;

        public PointerReleaseCallback(Pointer pointer) {
            this.pointer = pointer;
        }

        @Override
        public void execute(PythonContext context, Access access) {
            if (!pointer.markReleased()) {
                assert pointer.isReleased();
                return;
            }
            try {
                pointer.doRelease();
                LOGGER.finest("NFIZlibSupport pointer has been freed");
            } catch (Exception e) {
                LOGGER.severe("Error while trying to free NFIZlibSupport pointer: " + e.getMessage());
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NFIZlibSupport lib;
        private final long pointer;

        public Pointer(Object referent, long pointer, NFIZlibSupport lib) {
            super(referent, lib.pythonContext.getSharedFinalizer());
            this.lib = lib;
            this.pointer = pointer;
        }

        public long getPointer() {
            return pointer;
        }

        protected void doRelease() {
            lib.gcReleaseHelper(pointer);
        }

        @Override
        public AsyncHandler.AsyncAction release() {
            if (!isReleased()) {
                return new PointerReleaseCallback(this);
            }
            return null;
        }
    }

    public TruffleString zlibVersion() {
        return ZeroTerminatedUtf8ToTruffleStringNode.executeUncached(nativeFunctions.zlib_get_version());
    }

    public TruffleString zlibRuntimeVersion() {
        return ZeroTerminatedUtf8ToTruffleStringNode.executeUncached(nativeFunctions.zlib_get_runtime_version());
    }

    public Object getTimeElapsed(long zst) {
        return nativeFunctions.zlib_get_timeElapsed(zst);
    }

    public Object gcReleaseHelper(long zst) {
        nativeFunctions.zlib_gc_helper(zst);
        return null;
    }

    public long crc32(long crc, byte[] buf, int len) {
        if (len == 0) {
            return crc;
        }
        long nativeBuf = copyToNativeByteArray(buf, len);
        try {
            return nativeFunctions.zlib_crc32(crc, nativeBuf, len);
        } finally {
            if (nativeBuf != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeBuf);
            }
        }
    }

    public long adler32(long crc, byte[] buf, int len) {
        if (len == 0) {
            return crc;
        }
        long nativeBuf = copyToNativeByteArray(buf, len);
        try {
            return nativeFunctions.zlib_adler32(crc, nativeBuf, len);
        } finally {
            if (nativeBuf != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeBuf);
            }
        }
    }

    public long createStream() {
        return nativeFunctions.zlib_create_zlib_stream();
    }

    public void deallocateStream(long zst) {
        nativeFunctions.zlib_free_stream(zst);
    }

    public int getErrorFunction(long zst) {
        return nativeFunctions.zlib_get_error_type(zst);
    }

    public TruffleString getStreamErrorMsg(long zst) {
        return ZeroTerminatedUtf8ToTruffleStringNode.executeUncached(nativeFunctions.zlib_get_stream_msg(zst));
    }

    public int hasStreamErrorMsg(long zst) {
        return nativeFunctions.zlib_has_stream_msg(zst);
    }

    public int getEOF(long zst) {
        return nativeFunctions.zlib_get_eof(zst);
    }

    public int getIsInitialised(long zst) {
        return nativeFunctions.zlib_get_is_initialised(zst);
    }

    public int getBufferSize(long zst, int option) {
        return nativeFunctions.zlib_get_buffer_size(zst, option);
    }

    public void getBuffer(long zst, int option, byte[] dest) {
        if (dest.length == 0) {
            return;
        }
        long nativeDest = NativeMemory.mallocByteArray(dest.length);
        try {
            nativeFunctions.zlib_get_off_heap_buffer(zst, option, nativeDest);
            NativeMemory.readByteArrayElements(nativeDest, 0, dest, 0, dest.length);
        } finally {
            NativeMemory.free(nativeDest);
        }
    }

    public long createCompObject() {
        return nativeFunctions.zlib_create_compobject();
    }

    public int deflateOffHeap(long zst, byte[] in, long inLen, long bufSize, int level, int wbits) {
        long nativeIn = copyToNativeByteArray(in, (int) inLen);
        try {
            return nativeFunctions.zlib_deflate_off_heap(zst, nativeIn, inLen, bufSize, level, wbits);
        } finally {
            if (nativeIn != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeIn);
            }
        }
    }

    public int inflateOffHeap(long zst, byte[] in, long inLen, long bufSize, int wbits) {
        long nativeIn = copyToNativeByteArray(in, (int) inLen);
        try {
            return nativeFunctions.zlib_inflate_off_heap(zst, nativeIn, inLen, bufSize, wbits);
        } finally {
            if (nativeIn != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeIn);
            }
        }
    }

    public int compressObjInitWithDict(long zst, int level, int method, int wbits, int memLevel, int strategy, byte[] dict, long dictLen) {
        long nativeDict = copyToNativeByteArray(dict, (int) dictLen);
        try {
            return nativeFunctions.zlib_Compress_init(zst, level, method, wbits, memLevel, strategy, nativeDict, dictLen);
        } finally {
            if (nativeDict != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeDict);
            }
        }
    }

    public int compressObjInit(long zst, int level, int method, int wbits, int memLevel, int strategy) {
        return nativeFunctions.zlib_Compress_init_no_dict(zst, level, method, wbits, memLevel, strategy);
    }

    public int compressObj(long zst, Object in, long inLen, long bufSize) {
        long nativeIn = copyToNativeByteArray((byte[]) in, (int) inLen);
        try {
            return nativeFunctions.zlib_Compress_obj(zst, nativeIn, inLen, bufSize);
        } finally {
            if (nativeIn != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeIn);
            }
        }
    }

    public int compressObjFlush(long zst, byte[] in, long bufSize, int mode) {
        long nativeIn = copyToNativeByteArray(in);
        try {
            return nativeFunctions.zlib_Compress_flush(zst, nativeIn, bufSize, mode);
        } finally {
            if (nativeIn != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeIn);
            }
        }
    }

    public int compressObjCopy(long zst, long newCopy) {
        return nativeFunctions.zlib_Compress_copy(zst, newCopy);
    }

    public int decompressObjInitWithDict(long zst, int wbits, byte[] dict, long dictLen) {
        long nativeDict = copyToNativeByteArray(dict, (int) dictLen);
        try {
            return nativeFunctions.zlib_Decompress_init(zst, wbits, nativeDict, dictLen);
        } finally {
            if (nativeDict != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeDict);
            }
        }
    }

    public int decompressObjInit(long zst, int wbits) {
        return nativeFunctions.zlib_Decompress_init_no_dict(zst, wbits);
    }

    public int decompressObj(long zst, byte[] in, long inLen, long bufSize, long maxLength) {
        long nativeIn = copyToNativeByteArray(in, (int) inLen);
        try {
            return nativeFunctions.zlib_Decompress_obj(zst, nativeIn, inLen, bufSize, maxLength);
        } finally {
            if (nativeIn != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeIn);
            }
        }
    }

    public int decompressObjFlush(long zst, long length) {
        return nativeFunctions.zlib_Decompress_flush(zst, length);
    }

    public int decompressObjCopy(long zst, long newCopy) {
        return nativeFunctions.zlib_Decompress_copy(zst, newCopy);
    }

    public int decompressor(long zst, byte[] data, long len, long maxLength) {
        long nativeData = copyToNativeByteArray(data, (int) len);
        try {
            return nativeFunctions.zlib_decompress(zst, nativeData, len, maxLength);
        } finally {
            if (nativeData != NativeMemory.NULLPTR) {
                NativeMemory.free(nativeData);
            }
        }
    }
}
