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

/*-
 * Generated using:
 * scripts/nfi_gen.py -name Bz2 -cpath graalpython/com.oracle.graal.python.cext/bz2/bz2.c -lib libbz2support
 */
public class NFIBz2Support {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NFIBz2Support.class);

    enum Bz2NativeFunctions implements NativeLibrary.NativeFunction {

        /*-
          nfi_function: name('createStream') map('bzst_stream*', 'POINTER')
          bzst_stream *bz_create_bzst_stream()
        */
        bz_create_bzst_stream("(): POINTER"),

        /*-
          nfi_function: name('getTimeElapsed') map('bzst_stream*', 'POINTER')  static(true)
          double bz_get_timeElapsed(bzst_stream* zst)
        */
        bz_get_timeElapsed("(POINTER): DOUBLE"),

        /*-
          nfi_function: name('deallocateStream') map('bzst_stream*', 'POINTER')
          void bz_free_stream(bzst_stream* bzst)
        */
        bz_free_stream("(POINTER): VOID"),

        /*-
          nfi_function: name('gcReleaseHelper') map('bzst_stream*', 'POINTER') release(true)
          void bz_gc_helper(bzst_stream* bzst)
        */
        bz_gc_helper("(POINTER): VOID"),

        /*-
          nfi_function: name('getNextInIndex') map('bzst_stream*', 'POINTER')
          ssize_t bz_get_next_in_index(bzst_stream *bzst)
        */
        bz_get_next_in_index("(POINTER): SINT64"),

        /*-
          nfi_function: name('getBzsAvailInReal') map('bzst_stream*', 'POINTER')
          ssize_t bz_get_bzs_avail_in_real(bzst_stream *bzst)
        */
        bz_get_bzs_avail_in_real("(POINTER): SINT64"),

        /*-
          nfi_function: name('setBzsAvailInReal') map('bzst_stream*', 'POINTER')
          void bz_set_bzs_avail_in_real(bzst_stream *bzst, ssize_t v)
        */
        bz_set_bzs_avail_in_real("(POINTER, SINT64): VOID"),

        /*-
          nfi_function: name('getOutputBufferSize') map('bzst_stream*', 'POINTER')
          size_t bz_get_output_buffer_size(bzst_stream *bzst)
        */
        bz_get_output_buffer_size("(POINTER): UINT64"),

        /*-
          nfi_function: name('getOutputBuffer') map('bzst_stream*', 'POINTER')
          void bz_get_output_buffer(bzst_stream *bzst, Byte *dest)
        */
        bz_get_output_buffer("(POINTER, [UINT8]): VOID"),

        /*-
          nfi_function: name('compressInit') map('bzst_stream*', 'POINTER')
          int bz_compressor_init(bzst_stream *bzst, int compresslevel)
        */
        bz_compressor_init("(POINTER, SINT32): SINT32"),

        /*-
          nfi_function: name('compress') map('bzst_stream*', 'POINTER')
          int bz_compress(bzst_stream *bzst, Byte *data, ssize_t len, int action, ssize_t bufsize)
        */
        bz_compress("(POINTER, [UINT8], SINT64, SINT32, SINT64): SINT32"),

        /*-
          nfi_function: name('decompressInit') map('bzst_stream*', 'POINTER')
          int bz_decompress_init(bzst_stream *bzst)
        */
        bz_decompress_init("(POINTER): SINT32"),

        /*-
          nfi_function: name('decompress') map('bzst_stream*', 'POINTER')
          int bz_decompress(bzst_stream *bzst, Byte *input_buffer, ssize_t offset,ssize_t max_length,ssize_t bufsize, ssize_t bzs_avail_in_real)
        */
        bz_decompress("(POINTER, [UINT8], SINT64, SINT64, SINT64, SINT64): SINT32");

        private final String signature;

        Bz2NativeFunctions(String signature) {
            this.signature = signature;
        }

        @Override
        public String signature() {
            return signature;
        }
    }

    private static final String SUPPORTING_NATIVE_LIB_NAME = "bz2support";

    private final PythonContext pythonContext;
    private final NativeLibrary.TypedNativeLibrary<Bz2NativeFunctions> typedNativeLib;

    @CompilerDirectives.CompilationFinal private boolean available;

    private NFIBz2Support(PythonContext context, NativeLibrary.NFIBackend backend, String noNativeAccessHelp) {
        if (context.isNativeAccessAllowed()) {
            this.pythonContext = context;
            this.typedNativeLib = NativeLibrary.create(PythonContext.getSupportLibName(SUPPORTING_NATIVE_LIB_NAME), Bz2NativeFunctions.values(),
                            backend, noNativeAccessHelp, false);
            this.available = true;
        } else {
            this.pythonContext = null;
            this.typedNativeLib = null;
            this.available = false;
        }
    }

    public static NFIBz2Support createNative(PythonContext context, String noNativeAccessHelp) {
        return new NFIBz2Support(context, NativeLibrary.NFIBackend.NATIVE, noNativeAccessHelp);
    }

    public static NFIBz2Support createLLVM(PythonContext context, String noNativeAccessHelp) {
        return new NFIBz2Support(context, NativeLibrary.NFIBackend.LLVM, noNativeAccessHelp);
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
                    LOGGER.finest("NFIBz2Support pointer has been freed");
                } catch (Exception e) {
                    LOGGER.severe("Error while trying to free NFIBz2Support pointer: " + e.getMessage());
                }
            }
        }
    }

    public static class Pointer extends AsyncHandler.SharedFinalizer.FinalizableReference {

        private final NFIBz2Support lib;

        public Pointer(Object referent, Object ptr, NFIBz2Support lib) {
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
            CompilerAsserts.neverPartOfCompilation("Checking NFIBz2Support availability should only be done during initialization.");
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     *
     * @param zst bzst_stream* zst
     * @return double
     */
    public Object getTimeElapsed(Object zst) {
        return typedNativeLib.callUncached(pythonContext, Bz2NativeFunctions.bz_get_timeElapsed, zst);
    }

    /**
     *
     * @param bzst bzst_stream* bzst
     *
     */
    public Object gcReleaseHelper(Object bzst) {
        return typedNativeLib.callUncached(pythonContext, Bz2NativeFunctions.bz_gc_helper, bzst);
    }

    /**
     * 
     *
     * @return bzst_stream*
     */
    public Object createStream(
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.call(typedNativeLib, Bz2NativeFunctions.bz_create_bzst_stream);
    }

    /**
     * 
     * @param bzst bzst_stream* bzst
     *
     */
    public void deallocateStream(Object bzst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, Bz2NativeFunctions.bz_free_stream, bzst);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @return ssize_t
     */
    public long getNextInIndex(Object bzst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, Bz2NativeFunctions.bz_get_next_in_index, bzst);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @return ssize_t
     */
    public long getBzsAvailInReal(Object bzst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, Bz2NativeFunctions.bz_get_bzs_avail_in_real, bzst);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @param v ssize_t v
     *
     */
    public void setBzsAvailInReal(Object bzst, long v,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, Bz2NativeFunctions.bz_set_bzs_avail_in_real, bzst, v);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @return size_t
     */
    public long getOutputBufferSize(Object bzst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callLong(typedNativeLib, Bz2NativeFunctions.bz_get_output_buffer_size, bzst);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @param dest Byte *dest
     *
     */
    public void getOutputBuffer(Object bzst, Object dest,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        invokeNode.call(typedNativeLib, Bz2NativeFunctions.bz_get_output_buffer, bzst, dest);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @param compresslevel int compresslevel
     * @return int
     */
    public int compressInit(Object bzst, int compresslevel,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, Bz2NativeFunctions.bz_compressor_init, bzst, compresslevel);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @param data Byte *data
     * @param len ssize_t len
     * @param action int action
     * @param bufsize ssize_t bufsize
     * @return int
     */
    public int compress(Object bzst, Object data, long len, int action, long bufsize,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, Bz2NativeFunctions.bz_compress, bzst, data, len, action, bufsize);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @return int
     */
    public int decompressInit(Object bzst,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, Bz2NativeFunctions.bz_decompress_init, bzst);
    }

    /**
     * 
     * @param bzst bzst_stream *bzst
     * @param input_buffer Byte *input_buffer
     * @param offset ssize_t offset
     * @param max_length ssize_t max_length
     * @param bufsize ssize_t bufsize
     * @param bzs_avail_in_real ssize_t bzs_avail_in_real
     * @return int
     */
    public int decompress(Object bzst, Object input_buffer, long offset, long max_length, long bufsize, long bzs_avail_in_real,
                    NativeLibrary.InvokeNativeFunction invokeNode) {
        return invokeNode.callInt(typedNativeLib, Bz2NativeFunctions.bz_decompress, bzst, input_buffer, offset, max_length, bufsize, bzs_avail_in_real);
    }

}
