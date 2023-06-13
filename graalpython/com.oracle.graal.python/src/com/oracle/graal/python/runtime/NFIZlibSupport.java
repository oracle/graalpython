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
package com.oracle.graal.python.runtime;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.strings.TruffleString;

/*-
 * Generated using:
 * scripts/nfi_gen.py -name Zlib -cpath graalpython/com.oracle.graal.python.cext/zlib/zlib.c -lib libzsupport
 */
public class NFIZlibSupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIZlibSupport.class);

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

    enum ZlibNativeFunctions implements NativeLibrary.NativeFunction {

        /*-
          nfi_function: name('zlibVersion') static(true)
          char *zlib_get_version()
        */
        zlib_get_version("(): STRING"),

        /*-
          nfi_function: name('zlibRuntimeVersion') static(true)
          char *zlib_get_runtime_version()
        */
        zlib_get_runtime_version("(): STRING"),

        /*-
          nfi_function: name('crc32')
          uLong zlib_crc32(uLong crc, Byte *buf, uInt len)
        */
        zlib_crc32("(UINT64, [UINT8], UINT32): UINT64"),

        /*-
          nfi_function: name('adler32')
          uLong zlib_adler32(uLong crc, Byte *buf, uInt len)
        */
        zlib_adler32("(UINT64, [UINT8], UINT32): UINT64"),

        /*-
          nfi_function: name('createStream') map('zlib_stream*', 'POINTER')
          zlib_stream *zlib_create_zlib_stream()
        */
        zlib_create_zlib_stream("(): POINTER"),

        /*-
          nfi_function: name('getTimeElapsed') map('zlib_stream*', 'POINTER')  static(true)
          double zlib_get_timeElapsed(zlib_stream* zst)
        */
        zlib_get_timeElapsed("(POINTER): DOUBLE"),

        /*-
          nfi_function: name('deallocateStream') map('zlib_stream*', 'POINTER')
          void zlib_free_stream(zlib_stream* zst)
        */
        zlib_free_stream("(POINTER): VOID"),

        /*-
          nfi_function: name('gcReleaseHelper') map('zlib_stream*', 'POINTER') release(true)
          void zlib_gc_helper(zlib_stream* zst)
        */
        zlib_gc_helper("(POINTER): VOID"),

        /*-
          nfi_function: name('getErrorFunction') map('zlib_stream*', 'POINTER')
          int zlib_get_error_type(zlib_stream *zst)
        */
        zlib_get_error_type("(POINTER): SINT32"),

        /*-
          nfi_function: name('getStreamErrorMsg') map('zlib_stream*', 'POINTER')
          char *zlib_get_stream_msg(zlib_stream *zst)
        */
        zlib_get_stream_msg("(POINTER): STRING"),

        /*-
          nfi_function: name('hasStreamErrorMsg') map('zlib_stream*', 'POINTER')
          int zlib_has_stream_msg(zlib_stream *zst)
        */
        zlib_has_stream_msg("(POINTER): SINT32"),

        /*-
          nfi_function: name('getEOF') map('zlib_stream*', 'POINTER')
          int zlib_get_eof(zlib_stream *zst)
        */
        zlib_get_eof("(POINTER): SINT32"),

        /*-
          nfi_function: name('getIsInitialised') map('zlib_stream*', 'POINTER')
          int zlib_get_is_initialised(zlib_stream *zst)
        */
        zlib_get_is_initialised("(POINTER): SINT32"),

        /*-
          nfi_function: name('getBufferSize') map('zlib_stream*', 'POINTER')
          uInt zlib_get_buffer_size(zlib_stream *zst, int option)
        */
        zlib_get_buffer_size("(POINTER, SINT32): UINT32"),

        /*-
          nfi_function: name('getBuffer') map('zlib_stream*', 'POINTER')
          void zlib_get_off_heap_buffer(zlib_stream *zst, int option, Byte *dest)
        */
        zlib_get_off_heap_buffer("(POINTER, SINT32, [UINT8]): VOID"),

        /*-
          nfi_function: name('createCompObject') map('zlib_stream*', 'POINTER')
          zlib_stream *zlib_create_compobject()
        */
        zlib_create_compobject("(): POINTER"),

        /*-
          nfi_function: name('deflateOffHeap') map('zlib_stream*', 'POINTER')
          int zlib_deflate_off_heap(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size, int level)
        */
        zlib_deflate_off_heap("(POINTER, [UINT8], SINT64, SINT64, SINT32): SINT32"),

        /*-
          nfi_function: name('inflateOffHeap') map('zlib_stream*', 'POINTER')
          int zlib_inflate_off_heap(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size, int wbits)
        */
        zlib_inflate_off_heap("(POINTER, [UINT8], SINT64, SINT64, SINT32): SINT32"),

        /*-
          nfi_function: name('compressObjInitWithDict') map('zlib_stream*', 'POINTER')
          int zlib_Compress_init(zlib_stream *zst, int level, int method,int wbits, int memLevel,int strategy, Byte *dict, size_t dict_len)
        */
        zlib_Compress_init("(POINTER, SINT32, SINT32, SINT32, SINT32, SINT32, [UINT8], UINT64): SINT32"),

        /*-
          nfi_function: name('compressObjInit') map('zlib_stream*', 'POINTER')
          int zlib_Compress_init_no_dict(zlib_stream *zst, int level, int method,int wbits, int memLevel,int strategy)
        */
        zlib_Compress_init_no_dict("(POINTER, SINT32, SINT32, SINT32, SINT32, SINT32): SINT32"),

        /*-
          nfi_function: name('compressObj') map('zlib_stream*', 'POINTER')
          int zlib_Compress_obj(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size)
        */
        zlib_Compress_obj("(POINTER, [UINT8], SINT64, SINT64): SINT32"),

        /*-
          nfi_function: name('compressObjFlush') map('zlib_stream*', 'POINTER')
          int zlib_Compress_flush(zlib_stream *zst, Byte *in, ssize_t buf_size, int mode)
        */
        zlib_Compress_flush("(POINTER, [UINT8], SINT64, SINT32): SINT32"),

        /*-
          nfi_function: name('compressObjCopy') map('zlib_stream*', 'POINTER')
          int zlib_Compress_copy(zlib_stream *zst, zlib_stream *new_copy)
        */
        zlib_Compress_copy("(POINTER, POINTER): SINT32"),

        /*-
          nfi_function: name('decompressObjInitWithDict') map('zlib_stream*', 'POINTER')
          int zlib_Decompress_init(zlib_stream *zst, int wbits, Byte *dict, size_t dict_len)
        */
        zlib_Decompress_init("(POINTER, SINT32, [UINT8], UINT64): SINT32"),

        /*-
          nfi_function: name('decompressObjInit') map('zlib_stream*', 'POINTER')
          int zlib_Decompress_init_no_dict(zlib_stream *zst, int wbits)
        */
        zlib_Decompress_init_no_dict("(POINTER, SINT32): SINT32"),

        /*-
          nfi_function: name('decompressObj') map('zlib_stream*', 'POINTER')
          int zlib_Decompress_obj(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size, ssize_t max_length)
        */
        zlib_Decompress_obj("(POINTER, [UINT8], SINT64, SINT64, SINT64): SINT32"),

        /*-
          nfi_function: name('decompressObjFlush') map('zlib_stream*', 'POINTER')
          int zlib_Decompress_flush(zlib_stream *zst, ssize_t length)
        */
        zlib_Decompress_flush("(POINTER, SINT64): SINT32"),

        /*-
          nfi_function: name('decompressObjCopy') map('zlib_stream*', 'POINTER')
          int zlib_Decompress_copy(zlib_stream *zst, zlib_stream *new_copy)
        */
        zlib_Decompress_copy("(POINTER, POINTER): SINT32");

        private final String signature;

        ZlibNativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private static final String SUPPORTING_NATIVE_LIB_NAME = "libzsupport";

    private final PythonContext pythonContext;
    private final NativeLibrary.TypedNativeLibrary<ZlibNativeFunctions> typedNativeLib;

    @CompilerDirectives.CompilationFinal private boolean available;

    private NFIZlibSupport(PythonContext context, NativeLibrary.NFIBackend backend, String noNativeAccessHelp) {
        if (context.isNativeAccessAllowed()) {
            this.pythonContext = context;
            this.typedNativeLib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME + PythonContext.getSupportExt(),
                            ZlibNativeFunctions.values(), backend, noNativeAccessHelp, true);
            this.available = true;
        } else {
            this.pythonContext = null;
            this.typedNativeLib = null;
            this.available = false;
        }
    }

    public static NFIZlibSupport createNative(PythonContext context, String noNativeAccessHelp) {
        return new NFIZlibSupport(context, NativeLibrary.NFIBackend.NATIVE, noNativeAccessHelp);
    }

    public static NFIZlibSupport createLLVM(PythonContext context, String noNativeAccessHelp) {
        return new NFIZlibSupport(context, NativeLibrary.NFIBackend.LLVM, noNativeAccessHelp);
    }

    static class PointerReleaseCallback implements AsyncHandler.AsyncAction {
        private final Pointer pointer;

        public PointerReleaseCallback(Pointer pointer) {
            this.pointer = pointer;
        }

        @Override
        public void execute(PythonContext context) {
            synchronized (pointer) {
                if (pointer.isReleased()) {
                    return;
                }
                try {
                    pointer.doRelease();
                    pointer.markReleased();
                    LOGGER.finest("NFIZlibSupport pointer has been freed");
                } catch (Exception e) {
                    LOGGER.severe("Error while trying to free NFIZlibSupport pointer: " + e.getMessage());
                }
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NFIZlibSupport lib;

        public Pointer(Object referent, Object ptr, NFIZlibSupport lib) {
            super(referent, ptr, lib.pythonContext.getSharedFinalizer());
            this.lib = lib;
        }

        protected void doRelease() {
            lib.gcReleaseHelper(getReference());
        }

        @Override
        public AsyncHandler.AsyncAction release() {
            if (!isReleased()) {
                return new PointerReleaseCallback(this);
            }
            return null;
        }
    }

    public void notAvailable() {
        if (available) {
            CompilerAsserts.neverPartOfCompilation("Checking NFIZlibSupport availability should only be done during initialization.");
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     *
     *
     * @return char*
     */
    public Object zlibVersion() {
        return typedNativeLib.callUncached(pythonContext, ZlibNativeFunctions.zlib_get_version);
    }

    /**
     *
     *
     * @return char*
     */
    public Object zlibRuntimeVersion() {
        return typedNativeLib.callUncached(pythonContext, ZlibNativeFunctions.zlib_get_runtime_version);
    }

    /**
     *
     * @param zst zlib_stream* zst
     * @return double
     */
    public Object getTimeElapsed(Object zst) {
        return typedNativeLib.callUncached(pythonContext, ZlibNativeFunctions.zlib_get_timeElapsed, zst);
    }

    /**
     *
     * @param zst zlib_stream* zst
     *
     */
    public Object gcReleaseHelper(Object zst) {
        return typedNativeLib.callUncached(pythonContext, ZlibNativeFunctions.zlib_gc_helper, zst);
    }

    /**
     *
     * @param crc uLong crc
     * @param buf Byte *buf
     * @param len uInt len
     * @return uLong
     */
    public long crc32(long crc, Object buf, int len,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, ZlibNativeFunctions.zlib_crc32, crc, buf, len);
    }

    /**
     *
     * @param crc uLong crc
     * @param buf Byte *buf
     * @param len uInt len
     * @return uLong
     */
    public long adler32(long crc, Object buf, int len,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, ZlibNativeFunctions.zlib_adler32, crc, buf, len);
    }

    /**
     *
     *
     * @return zlib_stream*
     */
    public Object createStream(
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.call(typedNativeLib, ZlibNativeFunctions.zlib_create_zlib_stream);
    }

    /**
     *
     * @param zst zlib_stream* zst
     *
     */
    public void deallocateStream(Object zst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, ZlibNativeFunctions.zlib_free_stream, zst);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @return int
     */
    public int getErrorFunction(Object zst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_get_error_type, zst);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @return char*
     */
    public TruffleString getStreamErrorMsg(Object zst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callString(typedNativeLib, ZlibNativeFunctions.zlib_get_stream_msg, zst);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @return int
     */
    public int hasStreamErrorMsg(Object zst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_has_stream_msg, zst);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @return int
     */
    public int getEOF(Object zst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_get_eof, zst);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @return int
     */
    public int getIsInitialised(Object zst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_get_is_initialised, zst);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param option int option
     * @return uInt
     */
    public int getBufferSize(Object zst, int option,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_get_buffer_size, zst, option);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param option int option
     * @param dest Byte *dest
     *
     */
    public void getBuffer(Object zst, int option, Object dest,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, ZlibNativeFunctions.zlib_get_off_heap_buffer, zst, option, dest);
    }

    /**
     *
     *
     * @return zlib_stream*
     */
    public Object createCompObject(
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.call(typedNativeLib, ZlibNativeFunctions.zlib_create_compobject);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param in Byte *in
     * @param in_len ssize_t in_len
     * @param buf_size ssize_t buf_size
     * @param level int level
     * @return int
     */
    public int deflateOffHeap(Object zst, Object in, long in_len, long buf_size, int level,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_deflate_off_heap, zst, in, in_len, buf_size, level);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param in Byte *in
     * @param in_len ssize_t in_len
     * @param buf_size ssize_t buf_size
     * @param wbits int wbits
     * @return int
     */
    public int inflateOffHeap(Object zst, Object in, long in_len, long buf_size, int wbits,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_inflate_off_heap, zst, in, in_len, buf_size, wbits);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param level int level
     * @param method int method
     * @param wbits int wbits
     * @param memLevel int memLevel
     * @param strategy int strategy
     * @param dict Byte *dict
     * @param dict_len size_t dict_len
     * @return int
     */
    public int compressObjInitWithDict(Object zst, int level, int method, int wbits, int memLevel, int strategy, Object dict, long dict_len,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Compress_init, zst, level, method, wbits, memLevel, strategy, dict, dict_len);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param level int level
     * @param method int method
     * @param wbits int wbits
     * @param memLevel int memLevel
     * @param strategy int strategy
     * @return int
     */
    public int compressObjInit(Object zst, int level, int method, int wbits, int memLevel, int strategy,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Compress_init_no_dict, zst, level, method, wbits, memLevel, strategy);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param in Byte *in
     * @param in_len ssize_t in_len
     * @param buf_size ssize_t buf_size
     * @return int
     */
    public int compressObj(Object zst, Object in, long in_len, long buf_size,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Compress_obj, zst, in, in_len, buf_size);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param in Byte *in
     * @param buf_size ssize_t buf_size
     * @param mode int mode
     * @return int
     */
    public int compressObjFlush(Object zst, Object in, long buf_size, int mode,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Compress_flush, zst, in, buf_size, mode);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param new_copy zlib_stream *new_copy
     * @return int
     */
    public int compressObjCopy(Object zst, Object new_copy,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Compress_copy, zst, new_copy);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param wbits int wbits
     * @param dict Byte *dict
     * @param dict_len size_t dict_len
     * @return int
     */
    public int decompressObjInitWithDict(Object zst, int wbits, Object dict, long dict_len,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Decompress_init, zst, wbits, dict, dict_len);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param wbits int wbits
     * @return int
     */
    public int decompressObjInit(Object zst, int wbits,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Decompress_init_no_dict, zst, wbits);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param in Byte *in
     * @param in_len ssize_t in_len
     * @param buf_size ssize_t buf_size
     * @param max_length ssize_t max_length
     * @return int
     */
    public int decompressObj(Object zst, Object in, long in_len, long buf_size, long max_length,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Decompress_obj, zst, in, in_len, buf_size, max_length);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param length ssize_t length
     * @return int
     */
    public int decompressObjFlush(Object zst, long length,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Decompress_flush, zst, length);
    }

    /**
     *
     * @param zst zlib_stream *zst
     * @param new_copy zlib_stream *new_copy
     * @return int
     */
    public int decompressObjCopy(Object zst, Object new_copy,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, ZlibNativeFunctions.zlib_Decompress_copy, zst, new_copy);
    }

}
