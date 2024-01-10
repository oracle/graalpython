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
package com.oracle.graal.python.builtins.modules.bz2;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EOFError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.bz2.BZ2ModuleBuiltins.INITIAL_BUFFER_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.COMPRESSED_FILE_ENDED_BEFORE_EOS;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_DATA_STREAM;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_PARAMETERS_PASSED_TO_LIBBZIP2;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_SEQUENCE_OF_COMMANDS;
import static com.oracle.graal.python.nodes.ErrorMessages.LIBBZIP2_WAS_NOT_COMPILED_CORRECTLY;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_IO_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.UNRECOGNIZED_ERROR_FROM_LIBBZIP2_D;
import static com.oracle.graal.python.nodes.ErrorMessages.VALUE_TOO_LARGE_TO_FIT_INTO_INDEX;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.NFIBz2Support;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public class Bz2Nodes {

    protected static final int BZ_RUN = 0;
    protected static final int BZ_FLUSH = 1;
    protected static final int BZ_FINISH = 2;

    protected static final int BZ_OK = 0;
    protected static final int BZ_RUN_OK = 1;
    protected static final int BZ_FLUSH_OK = 2;
    protected static final int BZ_FINISH_OK = 3;
    protected static final int BZ_STREAM_END = 4;
    protected static final int BZ_SEQUENCE_ERROR = (-1);
    protected static final int BZ_PARAM_ERROR = (-2);
    protected static final int BZ_MEM_ERROR = (-3);
    protected static final int BZ_DATA_ERROR = (-4);
    protected static final int BZ_DATA_ERROR_MAGIC = (-5);
    protected static final int BZ_IO_ERROR = (-6);
    protected static final int BZ_UNEXPECTED_EOF = (-7);
    protected static final int BZ_OUTBUFF_FULL = (-8);
    protected static final int BZ_CONFIG_ERROR = (-9);

    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 21
    public abstract static class Bz2NativeCompress extends Node {

        public abstract byte[] execute(BZ2Object.BZ2Compressor self, PythonContext context, byte[] bytes, int len, int action);

        public byte[] compress(BZ2Object.BZ2Compressor self, PythonContext context, byte[] bytes, int len) {
            return execute(self, context, bytes, len, BZ_RUN);
        }

        public byte[] flush(BZ2Object.BZ2Compressor self, PythonContext context) {
            return execute(self, context, PythonUtils.EMPTY_BYTE_ARRAY, -1, BZ_FINISH);
        }

        @Specialization
        static byte[] nativeCompress(BZ2Object.BZ2Compressor self, PythonContext context, byte[] bytes, int len, int action,
                        @Bind("this") Node inliningTarget,
                        @Cached NativeLibrary.InvokeNativeFunction compress,
                        @Cached GetOutputNativeBufferNode getBuffer,
                        @Cached PRaiseNode.Lazy raiseNode) {
            NFIBz2Support bz2Support = context.getNFIBz2Support();
            Object inGuest = context.getEnv().asGuestValue(bytes);
            int err = bz2Support.compress(self.getBzs(), inGuest, len, action, INITIAL_BUFFER_SIZE, compress);
            if (err != BZ_OK) {
                errorHandling(err, raiseNode.get(inliningTarget));
            }
            return getBuffer.execute(inliningTarget, self.getBzs(), context);
        }

    }

    @GenerateUncached(false)
    @GenerateCached(false)
    @GenerateInline
    public abstract static class Bz2NativeDecompress extends Node {

        public abstract byte[] execute(Node inliningTarget, BZ2Object.BZ2Decompressor self, byte[] data, int len, int maxLength);

        @Specialization
        static byte[] nativeDecompress(Node inliningTarget, BZ2Object.BZ2Decompressor self, byte[] bytes, int len, int maxLength,
                        @Cached InlinedConditionProfile hasNextIntProfile,
                        @Cached InlinedBranchProfile isEofProfile,
                        @Cached InlinedBranchProfile bzsAvailProfile,
                        @Cached InlinedBranchProfile noInputBufferInUseProfile,
                        @Cached(inline = false) Bz2NativeInternalDecompress decompress) {
            boolean inputBufferInUse;
            /* Prepend unconsumed input if necessary */
            if (hasNextIntProfile.profile(inliningTarget, self.getNextIn() != null)) {
                /* Number of bytes we can append to input buffer */
                int availNow = self.getInputBufferSize() - (self.getNextInIndex() + self.getBzsAvailInReal());

                /*
                 * Number of bytes we can append if we move existing contents to beginning of buffer
                 * (overwriting consumed input)
                 */
                int availTotal = self.getInputBufferSize() - self.getBzsAvailInReal();

                if (availTotal < len) {
                    int newSize = self.getInputBufferSize() + len - availNow;

                    /*
                     * Assign to temporary variable first, so we don't lose address of allocated
                     * buffer if realloc fails
                     */
                    self.resizeInputBuffer(newSize);
                    self.setNextIn(self.getInputBuffer());
                } else if (availNow < len) {
                    PythonUtils.arraycopy(self.getNextIn(), self.getNextInIndex(), self.getInputBuffer(), 0, self.getBzsAvailInReal());
                    self.setNextIn(self.getInputBuffer());
                    self.setNextInIndex(0);
                }
                PythonUtils.arraycopy(bytes, 0, self.getNextIn(), self.getNextInIndex() + self.getBzsAvailInReal(), len);
                // memcpy((void*)(bzs->next_in + self.getBzsAvailInReal()), data, len);
                self.incBzsAvailInReal(len);
                inputBufferInUse = true;
            } else {
                self.setNextIn(bytes);
                self.setBzsAvailInReal(len);
                inputBufferInUse = false;
            }

            byte[] result = decompress.execute(self, maxLength);

            if (self.isEOF()) {
                isEofProfile.enter(inliningTarget);
                self.setNeedsInput(false);
                if (self.getBzsAvailInReal() > 0) {
                    self.setUnusedData();
                }
            } else if (self.getBzsAvailInReal() == 0) {
                bzsAvailProfile.enter(inliningTarget);
                self.clearNextIn();
                self.setNextInIndex(0);
                self.setNeedsInput(true);
            } else {
                self.setNeedsInput(false);

                /*
                 * If we did not use the input buffer, we now have to copy the tail from the
                 * caller's buffer into the input buffer
                 */
                if (!inputBufferInUse) {
                    noInputBufferInUseProfile.enter(inliningTarget);

                    /*
                     * Discard buffer if it's too small (resizing it may needlessly copy the current
                     * contents)
                     */
                    if (self.getInputBuffer() != null && self.getInputBufferSize() < self.getBzsAvailInReal()) {
                        self.discardInputBuffer();
                    }

                    /* Allocate if necessary */
                    if (self.getInputBuffer() == null) {
                        self.createInputBuffer(self.getBzsAvailInReal());
                    }

                    /* Copy tail */
                    // memcpy(d->input_buffer, bzs->next_in, self.getBzsAvailInReal());
                    PythonUtils.arraycopy(self.getNextIn(), self.getNextInIndex(), self.getInputBuffer(), 0, self.getBzsAvailInReal());
                    self.setNextIn(self.getInputBuffer());
                    self.setNextInIndex(0);
                }
            }

            return result;
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 21
    public abstract static class Bz2NativeInternalDecompress extends Node {

        public abstract byte[] execute(BZ2Object.BZ2Decompressor self, int maxLength);

        @Specialization
        static byte[] nativeInternalDecompress(BZ2Object.BZ2Decompressor self, int maxLength,
                        @Bind("this") Node inliningTarget,
                        @Cached NativeLibrary.InvokeNativeFunction decompress,
                        @Cached NativeLibrary.InvokeNativeFunction getBzsAvailInReal,
                        @Cached NativeLibrary.InvokeNativeFunction getNextInIndex,
                        @Cached GetOutputNativeBufferNode getBuffer,
                        @Cached InlinedConditionProfile errProfile,
                        @Cached InlinedBranchProfile ofProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            NFIBz2Support bz2Support = context.getNFIBz2Support();
            Object inGuest = self.getNextInGuest(context);
            int offset = self.getNextInIndex();
            int err = bz2Support.decompress(self.getBzs(), inGuest, offset, maxLength, INITIAL_BUFFER_SIZE, self.getBzsAvailInReal(), decompress);
            long nextInIdx = bz2Support.getNextInIndex(self.getBzs(), getNextInIndex);
            long bzsAvailInReal = bz2Support.getBzsAvailInReal(self.getBzs(), getBzsAvailInReal);
            try {
                self.setNextInIndex(nextInIdx);
                self.setBzsAvailInReal(bzsAvailInReal);
            } catch (OverflowException of) {
                ofProfile.enter(inliningTarget);
                throw raiseNode.get(inliningTarget).raise(SystemError, VALUE_TOO_LARGE_TO_FIT_INTO_INDEX);
            }
            if (err == BZ_STREAM_END) {
                self.setEOF();
            } else if (errProfile.profile(inliningTarget, err != BZ_OK)) {
                errorHandling(err, raiseNode.get(inliningTarget));
            }
            return getBuffer.execute(inliningTarget, self.getBzs(), context);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetOutputNativeBufferNode extends Node {

        public abstract byte[] execute(Node inliningTarget, Object bzst, PythonContext context);

        @Specialization
        static byte[] getBuffer(Node inliningTarget, Object bzst, PythonContext context,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction getBufferSize,
                        @Cached(inline = false) NativeLibrary.InvokeNativeFunction getBuffer,
                        @Cached InlinedBranchProfile ofProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            NFIBz2Support bz2Support = context.getNFIBz2Support();
            int size;
            try {
                size = PInt.intValueExact(bz2Support.getOutputBufferSize(bzst, getBufferSize));
            } catch (OverflowException of) {
                ofProfile.enter(inliningTarget);
                throw raiseNode.get(inliningTarget).raise(SystemError, VALUE_TOO_LARGE_TO_FIT_INTO_INDEX);
            }
            if (size == 0) {
                return PythonUtils.EMPTY_BYTE_ARRAY;
            }
            byte[] resultArray = new byte[size];
            Object out = context.getEnv().asGuestValue(resultArray);
            /* this will clear the native output once retrieved */
            bz2Support.getOutputBuffer(bzst, out, getBuffer);
            return resultArray;
        }
    }

    protected static void errorHandling(int bzerror, PRaiseNode raise) {
        switch (bzerror) {
            case BZ_PARAM_ERROR:
                throw raise.raise(ValueError, INVALID_PARAMETERS_PASSED_TO_LIBBZIP2);
            case BZ_MEM_ERROR:
                throw raise.raise(MemoryError);
            case BZ_DATA_ERROR:
            case BZ_DATA_ERROR_MAGIC:
                throw raise.raise(OSError, INVALID_DATA_STREAM);
            case BZ_IO_ERROR:
                throw raise.raise(OSError, UNKNOWN_IO_ERROR);
            case BZ_UNEXPECTED_EOF:
                throw raise.raise(EOFError, COMPRESSED_FILE_ENDED_BEFORE_EOS);
            case BZ_SEQUENCE_ERROR:
                throw raise.raise(RuntimeError, INVALID_SEQUENCE_OF_COMMANDS);
            case BZ_CONFIG_ERROR:
                throw raise.raise(ValueError, LIBBZIP2_WAS_NOT_COMPILED_CORRECTLY);
            default:
                throw raise.raise(OSError, UNRECOGNIZED_ERROR_FROM_LIBBZIP2_D, bzerror);
        }
    }
}
