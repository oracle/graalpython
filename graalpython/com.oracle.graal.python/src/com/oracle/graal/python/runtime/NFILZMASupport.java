/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*-
 * Generated using:
 * scripts/nfi_gen.py -name LZMA -cpath graalpython/com.oracle.graal.python.cext/lzma/lzma.c -lib liblzmasupport
 */
public class NFILZMASupport {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFILZMASupport.class);

    public static final int FORMAT_AUTO_INDEX = 0;
    public static final int FORMAT_XZ_INDEX = 1;
    public static final int FORMAT_ALONE_INDEX = 2;
    public static final int FORMAT_RAW_INDEX = 3;
    public static final int CHECK_NONE_INDEX = 0;
    public static final int CHECK_CRC32_INDEX = 1;
    public static final int CHECK_CRC64_INDEX = 2;
    public static final int CHECK_SHA256_INDEX = 3;
    public static final int CHECK_ID_MAX_INDEX = 4;
    public static final int CHECK_UNKNOWN_INDEX = 5;
    public static final int FILTER_LZMA1_INDEX = 0;
    public static final int FILTER_LZMA2_INDEX = 1;
    public static final int FILTER_DELTA_INDEX = 2;
    public static final int FILTER_X86_INDEX = 3;
    public static final int FILTER_POWERPC_INDEX = 4;
    public static final int FILTER_IA64_INDEX = 5;
    public static final int FILTER_ARM_INDEX = 6;
    public static final int FILTER_ARMTHUMB_INDEX = 7;
    public static final int FILTER_SPARC_INDEX = 8;
    public static final int MF_HC3_INDEX = 0;
    public static final int MF_HC4_INDEX = 1;
    public static final int MF_BT2_INDEX = 2;
    public static final int MF_BT3_INDEX = 3;
    public static final int MF_BT4_INDEX = 4;
    public static final int MODE_FAST_INDEX = 0;
    public static final int MODE_NORMAL_INDEX = 1;
    public static final int PRESET_DEFAULT_INDEX = 0;
    public static final int PRESET_EXTREME_INDEX = 1;
    public static final int ID_INDEX = 0;
    public static final int PRESET_INDEX = 1;
    public static final int DICT_SIZE_INDEX = 2;
    public static final int LC_INDEX = 3;
    public static final int LP_INDEX = 4;
    public static final int PB_INDEX = 5;
    public static final int MODE_INDEX = 6;
    public static final int NICE_LEN_INDEX = 7;
    public static final int MF_INDEX = 8;
    public static final int DEPTH_INDEX = 9;
    public static final int DIST_INDEX = 1;
    public static final int START_OFFSET_INDEX = 1;
    public static final int MAX_OPTS_INDEX = 10;
    public static final int LZMA_ID_ERROR = 98;
    public static final int LZMA_PRESET_ERROR = 99;

    enum LZMANativeFunctions implements NativeLibrary.NativeFunction {

        /*-
          nfi_function: name('getMarcos') static(true)
          void get_macros(int* formats, int* checks, uint64_t* filters, int* mfs, int* modes, uint64_t* preset)
        */
        get_macros("([SINT32], [SINT32], [UINT64], [SINT32], [SINT32], [UINT64]): VOID"),

        /*-
          nfi_function: name('createStream') map('lzmast_stream*', 'POINTER')
          lzmast_stream *lzma_create_lzmast_stream()
        */
        lzma_create_lzmast_stream("(): POINTER"),

        /*-
          nfi_function: name('getTimeElapsed') map('lzmast_stream*', 'POINTER')  static(true)
          double lzma_get_timeElapsed(lzmast_stream* lzmast)
        */
        lzma_get_timeElapsed("(POINTER): DOUBLE"),

        /*-
          nfi_function: name('deallocateStream') map('lzmast_stream*', 'POINTER')
          void lzma_free_stream(lzmast_stream* lzmast)
        */
        lzma_free_stream("(POINTER): VOID"),

        /*-
          nfi_function: name('gcReleaseHelper') map('lzmast_stream*', 'POINTER') release(true)
          void lzma_gc_helper(lzmast_stream* lzmast)
        */
        lzma_gc_helper("(POINTER): VOID"),

        /*-
          nfi_function: name('getNextInIndex') map('lzmast_stream*', 'POINTER')
          ssize_t lzma_get_next_in_index(lzmast_stream *lzmast)
        */
        lzma_get_next_in_index("(POINTER): SINT64"),

        /*-
          nfi_function: name('getLzsAvailIn') map('lzmast_stream*', 'POINTER')
          size_t lzma_get_lzs_avail_in(lzmast_stream *lzmast)
        */
        lzma_get_lzs_avail_in("(POINTER): UINT64"),

        /*-
          nfi_function: name('getLzsAvailOut') map('lzmast_stream*', 'POINTER')
          size_t lzma_get_lzs_avail_out(lzmast_stream *lzmast)
        */
        lzma_get_lzs_avail_out("(POINTER): UINT64"),

        /*-
          nfi_function: name('getLzsCheck') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_get_check(lzmast_stream *lzmast)
        */
        lzma_lzma_get_check("(POINTER): SINT32"),

        /*-
          nfi_function: name('setLzsAvailIn') map('lzmast_stream*', 'POINTER')
          void lzma_set_lzs_avail_in(lzmast_stream *lzmast, size_t v)
        */
        lzma_set_lzs_avail_in("(POINTER, UINT64): VOID"),

        /*-
          nfi_function: name('getOutputBufferSize') map('lzmast_stream*', 'POINTER')
          size_t lzma_get_output_buffer_size(lzmast_stream *lzmast)
        */
        lzma_get_output_buffer_size("(POINTER): UINT64"),

        /*-
          nfi_function: name('getOutputBuffer') map('lzmast_stream*', 'POINTER')
          void lzma_get_output_buffer(lzmast_stream *lzmast, Byte *dest)
        */
        lzma_get_output_buffer("(POINTER, [UINT8]): VOID"),

        /*-
          nfi_function: name('checkIsSupported')
          int lzma_lzma_check_is_supported(int check_id)
        */
        lzma_lzma_check_is_supported("(SINT32): SINT32"),

        /*-
          nfi_function: name('setFilterSpecLZMA') map('lzmast_stream*', 'POINTER')
          int lzma_set_filter_spec_lzma(lzmast_stream *lzmast, int fidx, int64_t* opts)
        */
        lzma_set_filter_spec_lzma("(POINTER, SINT32, [SINT64]): SINT32"),

        /*-
          nfi_function: name('setFilterSpecDelta') map('lzmast_stream*', 'POINTER')
          int lzma_set_filter_spec_delta(lzmast_stream *lzmast, int fidx, int64_t* opts)
        */
        lzma_set_filter_spec_delta("(POINTER, SINT32, [SINT64]): SINT32"),

        /*-
          nfi_function: name('setFilterSpecBCJ') map('lzmast_stream*', 'POINTER')
          int lzma_set_filter_spec_bcj(lzmast_stream *lzmast, int fidx, int64_t* opts)
        */
        lzma_set_filter_spec_bcj("(POINTER, SINT32, [SINT64]): SINT32"),

        /*-
          nfi_function: name('encodeFilter') map('lzmast_stream*', 'POINTER')
          int lzma_encode_filter_spec(lzmast_stream *lzmast, int64_t* opts)
        */
        lzma_encode_filter_spec("(POINTER, [SINT64]): SINT32"),

        /*-
          nfi_function: name('decodeFilter') map('lzmast_stream*', 'POINTER')
          int lzma_decode_filter_spec(int64_t filter_id, Byte* encoded_props, int len, int64_t *opts)
        */
        lzma_decode_filter_spec("(SINT64, [UINT8], SINT32, [SINT64]): SINT32"),

        /*-
          nfi_function: name('lzmaEasyEncoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_easy_encoder(lzmast_stream *lzmast, uint32_t preset, int check)
        */
        lzma_lzma_easy_encoder("(POINTER, UINT32, SINT32): SINT32"),

        /*-
          nfi_function: name('lzmaStreamEncoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_stream_encoder(lzmast_stream *lzmast, int check)
        */
        lzma_lzma_stream_encoder("(POINTER, SINT32): SINT32"),

        /*-
          nfi_function: name('lzmaAloneEncoderPreset') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_alone_encoder_preset(lzmast_stream *lzmast, uint32_t preset)
        */
        lzma_lzma_alone_encoder_preset("(POINTER, UINT32): SINT32"),

        /*-
          nfi_function: name('lzmaAloneEncoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_alone_encoder(lzmast_stream *lzmast)
        */
        lzma_lzma_alone_encoder("(POINTER): SINT32"),

        /*-
          nfi_function: name('lzmaRawEncoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_raw_encoder(lzmast_stream *lzmast)
        */
        lzma_lzma_raw_encoder("(POINTER): SINT32"),

        /*-
          nfi_function: name('compress') map('lzmast_stream*', 'POINTER')
          int lzma_compress(lzmast_stream *lzmast, Byte *data, size_t len, int iaction, ssize_t bufsize)
        */
        lzma_compress("(POINTER, [UINT8], UINT64, SINT32, SINT64): SINT32"),

        /*-
          nfi_function: name('lzmaRawDecoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_raw_decoder(lzmast_stream *lzmast)
        */
        lzma_lzma_raw_decoder("(POINTER): SINT32"),

        /*-
          nfi_function: name('lzmaAutoDecoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_auto_decoder(lzmast_stream *lzmast, uint64_t memlimit, uint32_t decoder_flags)
        */
        lzma_lzma_auto_decoder("(POINTER, UINT64, UINT32): SINT32"),

        /*-
          nfi_function: name('lzmaStreamDecoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_stream_decoder(lzmast_stream *lzmast, uint64_t memlimit, uint32_t decoder_flags)
        */
        lzma_lzma_stream_decoder("(POINTER, UINT64, UINT32): SINT32"),

        /*-
          nfi_function: name('lzmaAloneDecoder') map('lzmast_stream*', 'POINTER')
          int lzma_lzma_alone_decoder(lzmast_stream *lzmast, uint64_t memlimit)
        */
        lzma_lzma_alone_decoder("(POINTER, UINT64): SINT32"),

        /*-
          nfi_function: name('decompress') map('lzmast_stream*', 'POINTER')
          int lzma_decompress(lzmast_stream *lzmast, Byte *input_buffer, ssize_t offset,ssize_t max_length,ssize_t bufsize, size_t lzs_avail_in)
        */
        lzma_decompress("(POINTER, [UINT8], SINT64, SINT64, SINT64, UINT64): SINT32");

        private final String signature;

        LZMANativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private static final String SUPPORTING_NATIVE_LIB_NAME = "liblzmasupport";

    private final PythonContext pythonContext;
    private final NativeLibrary.TypedNativeLibrary<LZMANativeFunctions> typedNativeLib;

    @CompilerDirectives.CompilationFinal private boolean available;

    private NFILZMASupport(PythonContext context, NativeLibrary.NFIBackend backend, String noNativeAccessHelp) {
        if (context.isNativeAccessAllowed()) {
            this.pythonContext = context;
            this.typedNativeLib = NativeLibrary.create(SUPPORTING_NATIVE_LIB_NAME + context.getSoAbi().toJavaStringUncached(), LZMANativeFunctions.values(),
                            backend, noNativeAccessHelp, true);
            this.available = true;
        } else {
            this.pythonContext = null;
            this.typedNativeLib = null;
            this.available = false;
        }
    }

    public static NFILZMASupport createNative(PythonContext context, String noNativeAccessHelp) {
        return new NFILZMASupport(context, NativeLibrary.NFIBackend.NATIVE, noNativeAccessHelp);
    }

    public static NFILZMASupport createLLVM(PythonContext context, String noNativeAccessHelp) {
        return new NFILZMASupport(context, NativeLibrary.NFIBackend.LLVM, noNativeAccessHelp);
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
                    LOGGER.finest("NFILZMASupport pointer has been freed");
                } catch (Exception e) {
                    LOGGER.severe("Error while trying to free NFILZMASupport pointer: " + e.getMessage());
                }
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NFILZMASupport lib;

        public Pointer(Object referent, Object ptr, NFILZMASupport lib) {
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
            CompilerAsserts.neverPartOfCompilation("Checking NFILZMASupport availability should only be done during initialization.");
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     *
     * @param formats int* formats
     * @param checks int* checks
     * @param filters uint64_t* filters
     * @param mfs int* mfs
     * @param modes int* modes
     * @param preset uint64_t* preset
     *
     */
    public Object getMacros(Object formats, Object checks, Object filters, Object mfs, Object modes, Object preset) {
        return typedNativeLib.callUncached(pythonContext, LZMANativeFunctions.get_macros, formats, checks, filters, mfs, modes, preset);
    }

    /**
     *
     * @param lzmast lzmast_stream* lzmast
     * @return double
     */
    public Object getTimeElapsed(Object lzmast) {
        return typedNativeLib.callUncached(pythonContext, LZMANativeFunctions.lzma_get_timeElapsed, lzmast);
    }

    /**
     *
     * @param lzmast lzmast_stream* lzmast
     *
     */
    public Object gcReleaseHelper(Object lzmast) {
        return typedNativeLib.callUncached(pythonContext, LZMANativeFunctions.lzma_gc_helper, lzmast);
    }

    /**
     * 
     *
     * @return lzmast_stream*
     */
    public Object createStream(
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.call(typedNativeLib, LZMANativeFunctions.lzma_create_lzmast_stream);
    }

    /**
     * 
     * @param lzmast lzmast_stream* lzmast
     *
     */
    public void deallocateStream(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, LZMANativeFunctions.lzma_free_stream, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return ssize_t
     */
    public long getNextInIndex(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, LZMANativeFunctions.lzma_get_next_in_index, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return size_t
     */
    public long getLzsAvailIn(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, LZMANativeFunctions.lzma_get_lzs_avail_in, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return size_t
     */
    public long getLzsAvailOut(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, LZMANativeFunctions.lzma_get_lzs_avail_out, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return int
     */
    public int getLzsCheck(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_get_check, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param v size_t v
     *
     */
    public void setLzsAvailIn(Object lzmast, long v,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, LZMANativeFunctions.lzma_set_lzs_avail_in, lzmast, v);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return size_t
     */
    public long getOutputBufferSize(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, LZMANativeFunctions.lzma_get_output_buffer_size, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param dest Byte *dest
     *
     */
    public void getOutputBuffer(Object lzmast, Object dest,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, LZMANativeFunctions.lzma_get_output_buffer, lzmast, dest);
    }

    /**
     * 
     * @param check_id int check_id
     * @return int
     */
    public int checkIsSupported(int check_id,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_check_is_supported, check_id);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param fidx int fidx
     * @param opts int64_t* opts
     * @return int
     */
    public int setFilterSpecLZMA(Object lzmast, int fidx, Object opts,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_set_filter_spec_lzma, lzmast, fidx, opts);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param fidx int fidx
     * @param opts int64_t* opts
     * @return int
     */
    public int setFilterSpecDelta(Object lzmast, int fidx, Object opts,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_set_filter_spec_delta, lzmast, fidx, opts);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param fidx int fidx
     * @param opts int64_t* opts
     * @return int
     */
    public int setFilterSpecBCJ(Object lzmast, int fidx, Object opts,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_set_filter_spec_bcj, lzmast, fidx, opts);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param opts int64_t* opts
     * @return int
     */
    public int encodeFilter(Object lzmast, Object opts,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_encode_filter_spec, lzmast, opts);
    }

    /**
     * 
     * @param filter_id int64_t filter_id
     * @param encoded_props Byte* encoded_props
     * @param len int len
     * @param opts int64_t *opts
     * @return int
     */
    public int decodeFilter(long filter_id, Object encoded_props, int len, Object opts,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_decode_filter_spec, filter_id, encoded_props, len, opts);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param preset uint32_t preset
     * @param check int check
     * @return int
     */
    public int lzmaEasyEncoder(Object lzmast, long preset, int check,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_easy_encoder, lzmast, preset, check);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param check int check
     * @return int
     */
    public int lzmaStreamEncoder(Object lzmast, int check,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_stream_encoder, lzmast, check);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param preset uint32_t preset
     * @return int
     */
    public int lzmaAloneEncoderPreset(Object lzmast, long preset,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_alone_encoder_preset, lzmast, preset);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return int
     */
    public int lzmaAloneEncoder(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_alone_encoder, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return int
     */
    public int lzmaRawEncoder(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_raw_encoder, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param data Byte *data
     * @param len size_t len
     * @param iaction int iaction
     * @param bufsize ssize_t bufsize
     * @return int
     */
    public int compress(Object lzmast, Object data, long len, int iaction, long bufsize,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_compress, lzmast, data, len, iaction, bufsize);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @return int
     */
    public int lzmaRawDecoder(Object lzmast,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_raw_decoder, lzmast);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param memlimit uint64_t memlimit
     * @param decoder_flags uint32_t decoder_flags
     * @return int
     */
    public int lzmaAutoDecoder(Object lzmast, long memlimit, long decoder_flags,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_auto_decoder, lzmast, memlimit, decoder_flags);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param memlimit uint64_t memlimit
     * @param decoder_flags uint32_t decoder_flags
     * @return int
     */
    public int lzmaStreamDecoder(Object lzmast, long memlimit, long decoder_flags,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_stream_decoder, lzmast, memlimit, decoder_flags);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param memlimit uint64_t memlimit
     * @return int
     */
    public int lzmaAloneDecoder(Object lzmast, long memlimit,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_lzma_alone_decoder, lzmast, memlimit);
    }

    /**
     * 
     * @param lzmast lzmast_stream *lzmast
     * @param input_buffer Byte *input_buffer
     * @param offset ssize_t offset
     * @param max_length ssize_t max_length
     * @param bufsize ssize_t bufsize
     * @param lzs_avail_in size_t lzs_avail_in
     * @return int
     */
    public int decompress(Object lzmast, Object input_buffer, long offset, long max_length, long bufsize, long lzs_avail_in,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, LZMANativeFunctions.lzma_decompress, lzmast, input_buffer, offset, max_length, bufsize, lzs_avail_in);
    }

}
