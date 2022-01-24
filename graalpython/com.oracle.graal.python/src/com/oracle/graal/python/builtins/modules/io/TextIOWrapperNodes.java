/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIncrementalNewlineDecoder;
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.STRICT;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.DECODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.RESET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.ASCII;
import static com.oracle.graal.python.nodes.ErrorMessages.COULD_NOT_DETERMINE_DEFAULT_ENCODING;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_CHARACTER;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_DECODER_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_DECODER_STATE_THE_FIRST;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_NEWLINE_VALUE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_READABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_HAVE_RETURNED_A_BYTES_LIKE_OBJECT_NOT_P;
import static com.oracle.graal.python.nodes.PGuards.isPNone;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class TextIOWrapperNodes {

    /**
     * corresponds to textio.c:textiowrapper_set_decodedChars(textio *self, Object chars)
     */
    protected static void setDecodedChars(PTextIO self, String chars) {
        self.clearDecodedChars();
        self.appendDecodedChars(chars);
    }

    /**
     * corresponds to textio.c:textiowrapper_get_decodedChars(textio *self, int n)
     */
    protected static String getDecodedChars(PTextIO self, int len) {
        if (!self.hasDecodedChars()) {
            return "";
        }
        int usedChars = self.getDecodedCharsUsed();
        int n = len;
        int avail = self.getDecodedChars().length() - usedChars;
        if (n < 0 || n > avail) {
            n = avail;
        }
        String chars = PythonUtils.sbToString(self.getDecodedChars());
        if (usedChars > 0 || n < avail) {
            chars = PString.substring(chars, usedChars, usedChars + n);
        }
        self.setDecodedCharsUsed(usedChars + n);
        return chars;
    }

    protected static void validateNewline(String str, PRaiseNode raise) {
        int len = PString.length(str);
        char c = len == 0 ? '\0' : PString.charAt(str, 0);
        if (c != '\0' &&
                        !(c == '\n' && len == 1) &&
                        !(c == '\r' && len == 1) &&
                        !(c == '\r' && len == 2 && PString.charAt(str, 1) == '\n')) {
            throw raise.raise(ValueError, ILLEGAL_NEWLINE_VALUE_S, str);
        }
    }

    protected static void setNewline(PTextIO self, String newline) {
        int len = 0;
        if (newline == null) {
            self.setReadNewline(null);
        } else {
            self.setReadNewline(newline);
            len = PString.length(newline);
        }

        self.setReadUniversal(newline == null || len == 0);
        self.setReadTranslate(newline == null);
        self.setWriteTranslate(newline == null || len != 0);
        if (!self.isReadUniversal()) {
            // validate_newline() accepts only ASCII newlines.
            if (PString.equals(self.getReadNewline(), "\n")) {
                self.setWriteNewline(null);
            } else {
                self.setWriteNewline(self.getReadNewline());
            }
        } else {
            self.setWriteNewline(null);
        }
    }

    abstract static class CheckClosedNode extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, PTextIO self);

        @Specialization(guards = {"self.isFileIO()", "!self.getFileIO().isClosed()"})
        static void ideal(@SuppressWarnings("unused") PTextIO self) {
            // FileIO is not closed.. carryon
        }

        @Specialization(guards = {"self.isFileIO()", "self.getFileIO().isClosed()"})
        void error(@SuppressWarnings("unused") PTextIO self) {
            throw raise(ValueError, ErrorMessages.IO_CLOSED);
        }

        @Specialization(guards = "!self.isFileIO()")
        void checkGeneric(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached ConditionProfile isError) {
            Object res = getAttr.execute(frame, self.getBuffer(), CLOSED);
            if (isError.profile(isTrueNode.execute(frame, res))) {
                error(self);
            }
        }
    }

    protected abstract static class WriteFlushNode extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, PTextIO self);

        @Specialization(guards = "!self.hasPendingBytes()")
        void nothingTodo(@SuppressWarnings("unused") PTextIO self) {
            // nothing to do. there is no pending bytes to write.
        }

        @Specialization(guards = "self.hasPendingBytes()")
        static void writeflush(VirtualFrame frame, PTextIO self,
                        @Cached PythonObjectFactory factory,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            byte[] pending = self.getAndClearPendingBytes();
            PBytes b = factory.createBytes(pending);
            callMethod.execute(frame, self.getBuffer(), WRITE, b);
            // TODO: check _PyIO_trap_eintr
        }
    }

    /*
     * cpython/Modules/_io/textio.c:textiowrapper_read_chunk
     */
    protected abstract static class ChangeEncodingNode extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, PTextIO self, Object encodingObj, Object errorsObj, boolean newline_changed);

        protected static boolean isNothingTodo(Object encodingObj, Object errorsObj, boolean newline_changed) {
            return isPNone(encodingObj) && isPNone(errorsObj) && !newline_changed;
        }

        @Specialization(guards = "isNothingTodo(encodingObj, errorsObj, newline_changed)")
        void nothing(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object encodingObj, @SuppressWarnings("unused") Object errorsObj,
                        @SuppressWarnings("unused") boolean newline_changed) {
            // no change
        }

        @Specialization(guards = "!isNothingTodo(encodingObj, errorsObj, newline_changed)")
        static void changeEncoding(VirtualFrame frame, PTextIO self, Object encodingObj, Object errorsObj, @SuppressWarnings("unused") boolean newline_changed,
                        @Cached IONodes.ToStringNode asString,
                        @Cached CodecsTruffleModuleBuiltins.LookupTextEncoding lookupTextEncoding,
                        @Cached SetDecoderNode setDecoderNode,
                        @Cached SetEncoderNode setEncoderNode,
                        @Cached FixEncoderStateNode fixEncoderStateNode) {
            /* Use existing settings where new settings are not specified */
            String encoding = isPNone(encodingObj) ? self.getEncoding() : asString.execute(encodingObj);
            String errors;
            if (isPNone(errorsObj)) {
                if (isPNone(encodingObj)) {
                    errors = self.getErrors();
                } else {
                    errors = STRICT;
                }
            } else {
                errors = asString.execute(errorsObj);
            }

            // Create new encoder & decoder
            Object codecInfo = lookupTextEncoding.execute(frame, encoding, "codecs.open()");
            setDecoderNode.execute(frame, self, codecInfo, errors);
            setEncoderNode.execute(frame, self, codecInfo, errors);
            self.setEncoding(encoding);
            self.setErrors(errors);

            fixEncoderStateNode.execute(frame, self);
        }
    }

    public static int findLineEnding(PTextIOBase self, String line, int start) {
        int pos;
        if (self.isReadTranslate()) {
            /* Newlines are already translated, only search for \n */
            pos = PString.indexOf(line, "\n", start);
        } else if (self.isReadUniversal()) {
            /*
             * Universal newline search. Find any of \r, \r\n, \n The decoder ensures that \r\n are
             * not split in two pieces
             */
            int nlpos = PString.indexOf(line, "\n", start);
            int crpos = PString.indexOf(line, "\r", start);
            if (crpos == -1) {
                if (nlpos == -1) {
                    return -1;
                } else {
                    pos = nlpos; // \n
                }
            } else if (nlpos == -1) {
                pos = crpos; // \r
            } else if (nlpos < crpos) {
                pos = nlpos; // \n
            } else if (nlpos == crpos + 1) {
                pos = crpos + 1; // \r\n
            } else {
                pos = crpos; // \r
            }
        } else {
            /* Non-universal mode. */
            pos = PString.indexOf(line, self.getReadNewline(), start);
            if (pos != -1) {
                int nl = PString.length(self.getReadNewline());
                pos += nl - 1;
            }
        }
        if (pos != -1) {
            return pos - start + /*- we need to include the line ending char */ 1;
        }
        return pos;
    }

    protected abstract static class ReadlineNode extends PNodeWithRaise {

        public abstract String execute(VirtualFrame frame, PTextIO self, int limit);

        @Specialization
        static String readline(VirtualFrame frame, PTextIO self, int limit,
                        @Cached ReadChunkNode readChunkNode,
                        @Cached WriteFlushNode writeFlushNode) {
            writeFlushNode.execute(frame, self);

            int chunked = 0;
            int start, endpos, offsetToBuffer;
            StringBuilder line = null;
            StringBuilder chunks = null;
            String remaining = null;

            while (true) {
                /* First, get some data if necessary */
                boolean res = true;
                while (!self.hasDecodedChars() || self.getDecodedChars().length() == 0) {
                    res = readChunkNode.execute(frame, self, 0);
                    /*
                     * if (res < 0) { / * NOTE: PyErr_SetFromErrno() calls PyErr_CheckSignals() when
                     * EINTR occurs so we needn't do it ourselves. / // TODO:_PyIO_trap_eintr() }
                     */
                    if (!res) {
                        break;
                    }
                }
                if (!res) {
                    /* end of file */
                    self.clearDecodedChars();
                    self.clearSnapshot();
                    start = endpos = offsetToBuffer = 0;
                    break;
                }

                if (remaining == null) {
                    line = self.getDecodedChars();
                    start = self.getDecodedCharsUsed();
                    offsetToBuffer = 0;
                } else {
                    assert (self.getDecodedCharsUsed() == 0);
                    line = PythonUtils.newStringBuilder();
                    PythonUtils.append(line, remaining);
                    PythonUtils.append(line, self.getDecodedChars());
                    start = 0;
                    offsetToBuffer = PString.length(remaining);
                    remaining = null;
                    // TODO: PyUnicode_READY(line)?
                }

                String lineStr = PythonUtils.sbToString(line);
                endpos = findLineEnding(self, lineStr, start);
                /*
                 * ptr = PyUnicode_DATA(line); kind = PyUnicode_KIND(line); endpos =
                 * _PyIO_find_line_ending( self.readtranslate, self.readuniversal, self.getReadnl(),
                 * kind, ptr + kind * start, ptr + kind * lineLen, consumed);
                 *
                 */
                int consumed;
                if (endpos >= 0) {
                    endpos += start;
                    if (limit >= 0 && (endpos - start) + chunked >= limit) {
                        endpos = start + limit - chunked;
                    }
                    break;
                } else {
                    consumed = PString.length(lineStr) - start;
                }

                /* We can put aside up to `endpos` */
                endpos = consumed + start;
                if (limit >= 0 && (endpos - start) + chunked >= limit) {
                    /* Didn't find line ending, but reached length limit */
                    endpos = start + limit - chunked;
                    break;
                }

                if (endpos > start) {
                    /* No line ending seen yet - put aside current data */
                    String s;
                    if (chunks == null) {
                        chunks = PythonUtils.newStringBuilder();
                    }
                    s = PString.substring(lineStr, start, endpos);
                    PythonUtils.append(chunks, s);
                    chunked += PString.length(s);
                }
                /*
                 * There may be some remaining bytes we'll have to prepend to the next chunk of data
                 */
                int lineLen = line.length();
                if (endpos < lineLen) {
                    remaining = PythonUtils.substring(line, endpos, lineLen);
                }
                line = null;
                /* We have consumed the buffer */
                self.clearDecodedChars();
            }

            if (line != null) {
                /* Our line ends in the current buffer */
                self.setDecodedCharsUsed(endpos - offsetToBuffer);
                if (start > 0 || endpos < line.length()) {
                    String s = PythonUtils.substring(line, start, endpos);
                    line = PythonUtils.newStringBuilder();
                    PythonUtils.append(line, s);
                }
            }
            if (remaining != null) {
                if (chunks == null) {
                    chunks = PythonUtils.newStringBuilder();
                }
                PythonUtils.append(chunks, remaining);
            }
            if (chunks != null) {
                if (line != null) {
                    PythonUtils.append(chunks, PythonUtils.sbToString(line));
                }
                line = chunks;
            }

            return line == null ? "" : PythonUtils.sbToString(line);
        }
    }

    /*
     * cpython/Modules/_io/textio.c:textiowrapper_read_chunk
     */
    protected abstract static class ReadChunkNode extends PNodeWithRaiseAndIndirectCall {

        public abstract boolean execute(VirtualFrame frame, PTextIO self, int size_hint);

        @Specialization(guards = "self.hasDecoder()")
        boolean readChunk(VirtualFrame frame, PTextIO self, int hint,
                        @Cached SequenceNodes.GetObjectArrayNode getArray,
                        @Cached DecodeNode decodeNode,
                        @Cached PyObjectCallMethodObjArgs callMethodGetState,
                        @Cached PyObjectCallMethodObjArgs callMethodRead,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            /*
             * The return value is True unless EOF was reached. The decoded string is placed in
             * self._decoded_chars (replacing its previous value). The entire input chunk is sent to
             * the decoder, though some of it may remain buffered in the decoder, yet to be
             * converted.
             */
            PBytes decBuffer = null;
            Object decFlags = null;
            if (self.isTelling()) {
                /*
                 * To prepare for tell(), we need to snapshot a point in the file where the
                 * decoder's input buffer is empty.
                 */
                Object state = callMethodGetState.execute(frame, self.getDecoder(), GETSTATE);
                /*
                 * Given this, we know there was a valid snapshot point len(decBuffer) bytes ago
                 * with decoder state (b'', decFlags).
                 */
                if (!(state instanceof PTuple)) {
                    throw raise(TypeError, ILLEGAL_DECODER_STATE);
                }
                Object[] array = getArray.execute(state);
                if (array.length < 2) {
                    throw raise(TypeError, ILLEGAL_DECODER_STATE);
                }

                if (!(array[0] instanceof PBytes)) {
                    throw raise(TypeError, ILLEGAL_DECODER_STATE_THE_FIRST, array[0]);
                }

                decBuffer = (PBytes) array[0];
                decFlags = array[1];
            }

            /* Read a chunk, decode it, and put the result in self._decoded_chars. */
            int sizeHint = hint;
            if (sizeHint > 0) {
                sizeHint = (int) (Math.max(self.getB2cratio(), 1.0) * sizeHint);
            }
            int chunkSize = Math.max(self.getChunkSize(), sizeHint);

            Object inputChunk;
            if (self.isHasRead1()) {
                inputChunk = callMethodRead.execute(frame, self.getBuffer(), READ1, chunkSize);
            } else {
                inputChunk = callMethodRead.execute(frame, self.getBuffer(), READ, chunkSize);
            }

            Object inputChunkBuf;
            try {
                inputChunkBuf = bufferAcquireLib.acquireReadonly(inputChunk, frame, this);
            } catch (PException e) {
                throw raise(TypeError, S_SHOULD_HAVE_RETURNED_A_BYTES_LIKE_OBJECT_NOT_P, (self.isHasRead1() ? READ1 : READ), inputChunk);
            }
            try {
                int nbytes = bufferLib.getBufferLength(inputChunkBuf);
                boolean eof = nbytes == 0;

                String decodedChars = decodeNode.execute(frame, self.getDecoder(), inputChunk, eof);

                self.clearDecodedChars();
                self.appendDecodedChars(decodedChars);
                int nchars = PString.length(decodedChars);
                if (nchars > 0) {
                    self.setB2cratio(((double) nbytes) / nchars);
                } else {
                    self.setB2cratio(0.0);
                }
                if (nchars > 0) {
                    eof = false;
                }

                if (self.isTelling()) {
                    /*
                     * At the snapshot point, len(decBuffer) bytes before the read, the next input
                     * to be decoded is decBuffer + inputChunk.
                     */
                    // decBuffer is PBytes, we don't have to acquire the buffer
                    int decBufferLen = bufferLib.getBufferLength(decBuffer);
                    byte[] nextInput = new byte[decBufferLen + nbytes];
                    bufferLib.readIntoByteArray(decBuffer, 0, nextInput, 0, decBufferLen);
                    bufferLib.readIntoByteArray(inputChunkBuf, 0, nextInput, decBufferLen, nbytes);
                    self.setSnapshotNextInput(nextInput);
                    self.setSnapshotDecFlags(asSizeNode.executeExact(frame, decFlags));
                }

                return !eof;
            } finally {
                bufferLib.release(inputChunkBuf, frame, this);
            }
        }

        @Specialization(guards = "!self.hasDecoder()")
        boolean error(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") int size_hint) {
            throw raise(IOUnsupportedOperation, NOT_READABLE);
        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_decode
     */
    protected abstract static class DecodeNode extends PNodeWithRaise {
        public abstract String execute(VirtualFrame frame, Object decoder, Object bytes, boolean eof);

        /*
         * @Specialization(limit = "2") String decodeIncDec(VirtualFrame frame, Object decoder,
         * Object o, boolean eof, // TODO: chars = _PyIncrementalNewlineDecoder_decode(decoder,
         * bytes, eof); }
         *
         */

        @Specialization
        String decodeGeneric(VirtualFrame frame, Object decoder, Object o, boolean eof,
                        @Cached IONodes.ToStringNode toString,
                        @Cached BranchProfile notString,
                        @Cached PyObjectCallMethodObjArgs callMethodDecode) {
            Object decoded = callMethodDecode.execute(frame, decoder, DECODE, o, eof);
            return checkDecoded(decoded, toString, notString);
        }

        private String checkDecoded(Object decoded, IONodes.ToStringNode toString, BranchProfile notString) {
            try {
                return toString.execute(decoded);
            } catch (CannotCastException e) {
                notString.enter();
                throw raise(TypeError, DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P, decoded);
            }
        }
    }

    protected abstract static class CheckDecodedNode extends PNodeWithRaise {
        public abstract String execute(VirtualFrame frame, PTextIO self, Object o, boolean isFinal);

        @Specialization
        String decode(VirtualFrame frame, PTextIO self, Object o, boolean isFinal,
                        @Cached IONodes.ToStringNode toString,
                        @Cached BranchProfile notString,
                        @Cached PyObjectCallMethodObjArgs callMethodDecode) {
            Object decoded = callMethodDecode.execute(frame, self.getDecoder(), DECODE, o, isFinal);
            try {
                return toString.execute(decoded);
            } catch (CannotCastException e) {
                notString.enter();
                throw raise(TypeError, DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P, decoded);
            }
        }
    }

    protected abstract static class DecoderSetStateNode extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, PTextIO self, PTextIO.CookieType cookie, PythonObjectFactory factory);

        @Specialization(guards = "!self.hasDecoder()")
        void nothing(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") PTextIO.CookieType cookie, @SuppressWarnings("unused") PythonObjectFactory factory) {
            // nothing to do.
        }

        /*
         * When seeking to the start of the stream, we call decoder.reset() rather than
         * decoder.getstate(). This is for a few decoders such as utf-16 for which the state value
         * at start is not (b"", 0) but e.g. (b"", 2) (meaning, in the case of utf-16, that we are
         * expecting a BOM).
         */

        protected static boolean isAtInit(PTextIO.CookieType cookie) {
            return cookie.startPos == 0 && cookie.decFlags == 0;
        }

        @Specialization(guards = {"self.hasDecoder()", "isAtInit(cookie)"})
        static void atInit(VirtualFrame frame, PTextIO self, @SuppressWarnings("unused") PTextIO.CookieType cookie, @SuppressWarnings("unused") PythonObjectFactory factory,
                        @Cached PyObjectCallMethodObjArgs callMethodReset) {
            callMethodReset.execute(frame, self.getDecoder(), RESET);
        }

        @Specialization(guards = {"self.hasDecoder()", "!isAtInit(cookie)"})
        static void decoderSetstate(VirtualFrame frame, PTextIO self, PTextIO.CookieType cookie, PythonObjectFactory factory,
                        @Cached PyObjectCallMethodObjArgs callMethodSetState) {
            PTuple tuple = factory.createTuple(new Object[]{factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY), cookie.decFlags});
            callMethodSetState.execute(frame, self.getDecoder(), SETSTATE, tuple);

        }
    }

    protected abstract static class DecoderResetNode extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, PTextIO self);

        @Specialization(guards = "!self.hasDecoder()")
        void nothing(@SuppressWarnings("unused") PTextIO self) {
            // nothing to do.
        }

        @Specialization(guards = "self.hasDecoder()")
        static void reset(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            callMethod.execute(frame, self.getDecoder(), RESET);
        }
    }

    protected abstract static class EncoderResetNode extends PNodeWithRaise {

        public abstract void execute(VirtualFrame frame, PTextIO self, boolean startOfStream);

        @Specialization(guards = "startOfStream")
        static void encoderResetStart(VirtualFrame frame, PTextIO self, @SuppressWarnings("unused") boolean startOfStream,
                        @Cached PyObjectCallMethodObjArgs callMethodReset) {
            callMethodReset.execute(frame, self.getEncoder(), RESET);
            self.setEncodingStartOfStream(true);
        }

        @Specialization(guards = "!startOfStream")
        static void encoderResetNotStart(VirtualFrame frame, PTextIO self, @SuppressWarnings("unused") boolean startOfStream,
                        @Cached PyObjectCallMethodObjArgs callMethodSetState) {
            callMethodSetState.execute(frame, self.getEncoder(), SETSTATE, 0);
            self.setEncodingStartOfStream(false);

        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_fix_encoder_state
     */
    @GenerateUncached
    protected abstract static class FixEncoderStateNode extends PNodeWithContext {

        public abstract void execute(Frame frame, PTextIO self);

        @Specialization(guards = {"!self.isSeekable() || !self.hasEncoder()"})
        void nothing(@SuppressWarnings("unused") PTextIO self) {
            // nothing to do
        }

        @Specialization(guards = {"self.isSeekable()", "self.hasEncoder()"})
        static void fixEncoderState(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Cached PyObjectCallMethodObjArgs callMethodSetState,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            self.setEncodingStartOfStream(true);
            Object cookieObj = callMethodTell.execute(frame, self.getBuffer(), TELL);
            if (!eqNode.execute(frame, cookieObj, 0)) {
                self.setEncodingStartOfStream(false);
                callMethodSetState.execute(frame, self.getEncoder(), SETSTATE, 0);
            }
        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_set_decoder
     */
    @GenerateUncached
    protected abstract static class SetDecoderNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PTextIO self, Object codecInfo, String errors);

        @Specialization
        static void setDecoder(VirtualFrame frame, PTextIO self, Object codecInfo, String errors,
                        @Cached CodecsTruffleModuleBuiltins.GetIncrementalDecoderNode getIncrementalDecoderNode,
                        @Cached ConditionProfile isTrueProfile,
                        @Cached PyObjectCallMethodObjArgs callMethodReadable,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PythonObjectFactory factory) {
            Object res = callMethodReadable.execute(frame, self.getBuffer(), READABLE);
            if (isTrueProfile.profile(!isTrueNode.execute(frame, res))) {
                return;
            }
            Object decoder = getIncrementalDecoderNode.execute(frame, codecInfo, errors);
            if (self.isReadUniversal()) {
                PNLDecoder incDecoder = factory.createNLDecoder(PIncrementalNewlineDecoder);
                IncrementalNewlineDecoderBuiltins.InitNode.internalInit(incDecoder, decoder, self.isReadTranslate());
                self.setDecoder(incDecoder);
            } else {
                self.setDecoder(decoder);
            }
        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_set_encoder
     */
    @GenerateUncached
    protected abstract static class SetEncoderNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PTextIO self, Object codecInfo, String errors);

        @Specialization
        static void setEncoder(VirtualFrame frame, PTextIO self, Object codecInfo, String errors,
                        @Cached CodecsTruffleModuleBuiltins.GetIncrementalEncoderNode getIncrementalEncoderNode,
                        @Cached ConditionProfile isTrueProfile,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyObjectCallMethodObjArgs callMethodWritable) {
            Object res = callMethodWritable.execute(frame, self.getBuffer(), WRITABLE);
            if (isTrueProfile.profile(!isTrueNode.execute(frame, res))) {
                return;
            }
            self.setEncoder(null);
            self.setEncodefunc(null);
            self.setEncoder(getIncrementalEncoderNode.execute(frame, codecInfo, errors));
            // TODO: find encoder function
            // res = libCodecInfo.lookupAttributeStrict(codecInfo, frame, NAME);
            // self.setEncodefunc(null);
        }
    }

    @GenerateUncached
    public abstract static class TextIOWrapperInitNode extends PNodeWithContext {

        public abstract void execute(Frame frame, PTextIO self, Object buffer, Object encodingArg,
                        String errors, Object newlineArg, boolean lineBuffering, boolean writeThrough);

        @Specialization
        void init(VirtualFrame frame, PTextIO self, Object buffer, Object encodingArg,
                        String errors, Object newlineArg, boolean lineBuffering, boolean writeThrough,
                        @Cached CodecsTruffleModuleBuiltins.GetPreferredEncoding getPreferredEncoding,
                        @Cached CodecsTruffleModuleBuiltins.LookupTextEncoding lookupTextEncoding,
                        @Cached SetEncoderNode setEncoderNode,
                        @Cached SetDecoderNode setDecoderNode,
                        @Cached FixEncoderStateNode fixEncoderStateNode,
                        @Cached PyObjectCallMethodObjArgs callMethodSeekable,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PRaiseNode raiseNode) {
            self.setOK(false);
            self.setDetached(false);
            // encoding and newline are processed through arguments clinic and safe to cast.
            String encoding = encodingArg == PNone.NONE ? null : (String) encodingArg;
            String newline = newlineArg == PNone.NONE ? null : (String) newlineArg;
            if (encoding != null) {
                if (PString.indexOf(encoding, "\0", 0) != -1) {
                    throw raiseNode.raise(ValueError, EMBEDDED_NULL_CHARACTER);
                }
            }

            if (newline != null) {
                if (PString.indexOf(newline, "\0", 0) != -1) {
                    throw raiseNode.raise(ValueError, EMBEDDED_NULL_CHARACTER);
                }
                validateNewline(newline, raiseNode);
            }

            self.clearAll();
            /* Try os.device_encoding(fileno) */
            // TODO: find file encoding using system nl_langinfo
            if (encoding == null && !self.hasEncoding()) {
                try {
                    self.setEncoding(getPreferredEncoding.execute(frame));
                } catch (Exception e) {
                    self.setEncoding(ASCII);
                }
            }
            if (self.hasEncoding()) {
                encoding = self.getEncoding();
            } else if (encoding != null) {
                self.setEncoding(encoding);
            } else {
                throw raiseNode.raise(OSError, COULD_NOT_DETERMINE_DEFAULT_ENCODING);
            }

            /* Check we have been asked for a real text encoding */
            Object codecInfo = lookupTextEncoding.execute(frame, encoding, "codecs.open()");

            self.setErrors(errors);
            self.setChunkSize(8192);
            self.setLineBuffering(lineBuffering);
            self.setWriteThrough(writeThrough);
            setNewline(self, newline);

            self.setBuffer(buffer);

            /* Build the decoder object */
            setDecoderNode.execute(frame, self, codecInfo, errors);

            /* Build the encoder object */
            setEncoderNode.execute(frame, self, codecInfo, errors);

            if (buffer instanceof PBuffered) {
                /* Cache the raw FileIO object to speed up 'closed' checks */
                if (((PBuffered) buffer).isFastClosedChecks()) {
                    PFileIO f = ((PBuffered) buffer).getFileIORaw();
                    self.setFileIO(f);
                    f.setUTF8Write(false);
                }
            }

            Object res = callMethodSeekable.execute(frame, buffer, SEEKABLE);
            self.setTelling(isTrueNode.execute(frame, res));
            self.setSeekable(self.isTelling());

            self.setHasRead1(lookup.execute(frame, buffer, READ1) != PNone.NO_VALUE);

            self.setEncodingStartOfStream(false);
            fixEncoderStateNode.execute(frame, self);

            self.setOK(true);
        }
    }
}
