/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EncodingWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIncrementalNewlineDecoder;
import static com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins.T_INCREMENTALDECODER;
import static com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins.T_INCREMENTALENCODER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ1;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_RESET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ASCII;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LOCALE;
import static com.oracle.graal.python.nodes.ErrorMessages.COULD_NOT_DETERMINE_DEFAULT_ENCODING;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EMBEDDED_NULL_CHARACTER;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_DECODER_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_DECODER_STATE_THE_FIRST;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_NEWLINE_VALUE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_READABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_HAVE_RETURNED_A_BYTES_LIKE_OBJECT_NOT_P;
import static com.oracle.graal.python.nodes.PGuards.isPNone;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_DECODE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsTruffleModuleBuiltins.MakeIncrementalcodecNode;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringCheckedNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public abstract class TextIOWrapperNodes {

    public static final TruffleString T_CODECS_OPEN = tsLiteral("codecs.open()");

    protected static void validateNewline(TruffleString str, Node inliningTarget, PRaiseNode.Lazy raise, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        int len = codePointLengthNode.execute(str, TS_ENCODING);
        int c = len == 0 ? '\0' : codePointAtIndexNode.execute(str, 0, TS_ENCODING);
        if (c != '\0' &&
                        !(c == '\n' && len == 1) &&
                        !(c == '\r' && len == 1) &&
                        !(c == '\r' && len == 2 && codePointAtIndexNode.execute(str, 1, TS_ENCODING) == '\n')) {
            throw raise.get(inliningTarget).raise(ValueError, ILLEGAL_NEWLINE_VALUE_S, str);
        }
    }

    protected static void setNewline(PTextIO self, TruffleString newline, TruffleString.EqualNode equalNode) {
        boolean empty = true;
        if (newline == null) {
            self.setReadNewline(null);
        } else {
            self.setReadNewline(newline);
            empty = newline.isEmpty();
        }

        self.setReadUniversal(newline == null || empty);
        self.setReadTranslate(newline == null);
        self.setWriteTranslate(newline == null || !empty);
        if (!self.isReadUniversal()) {
            // validate_newline() accepts only ASCII newlines.
            if (equalNode.execute(T_NEWLINE, self.getReadNewline(), TS_ENCODING)) {
                self.setWriteNewline(null);
            } else {
                self.setWriteNewline(self.getReadNewline());
            }
        } else {
            self.setWriteNewline(null);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 28 -> 9
    abstract static class CheckClosedNode extends Node {

        public abstract void execute(VirtualFrame frame, PTextIO self);

        @Specialization(guards = {"self.isFileIO()", "!self.getFileIO().isClosed()"})
        static void ideal(@SuppressWarnings("unused") PTextIO self) {
            // FileIO is not closed.. carryon
        }

        @Specialization(guards = {"self.isFileIO()", "self.getFileIO().isClosed()"})
        static void error(@SuppressWarnings("unused") PTextIO self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.IO_CLOSED);
        }

        @Specialization(guards = "!self.isFileIO()")
        static void checkGeneric(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object res = getAttr.execute(frame, inliningTarget, self.getBuffer(), T_CLOSED);
            if (isTrueNode.execute(frame, inliningTarget, res)) {
                error(self, raiseNode.get(inliningTarget));
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    protected abstract static class WriteFlushNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PTextIO self);

        @Specialization(guards = "!self.hasPendingBytes()")
        static void nothingTodo(@SuppressWarnings("unused") PTextIO self) {
            // nothing to do. there is no pending bytes to write.
        }

        @Specialization(guards = "self.hasPendingBytes()")
        static void writeflush(VirtualFrame frame, Node inliningTarget, PTextIO self,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            byte[] pending = self.getAndClearPendingBytes();
            PBytes b = factory.createBytes(pending);
            callMethod.execute(frame, inliningTarget, self.getBuffer(), T_WRITE, b);
            // TODO: check _PyIO_trap_eintr
        }
    }

    /*
     * cpython/Modules/_io/textio.c:textiowrapper_read_chunk
     */
    @GenerateInline
    @GenerateCached(false)
    protected abstract static class ChangeEncodingNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PTextIO self, Object encodingObj, Object errorsObj, boolean newline_changed);

        protected static boolean isNothingTodo(Object encodingObj, Object errorsObj, boolean newline_changed) {
            return isPNone(encodingObj) && isPNone(errorsObj) && !newline_changed;
        }

        @Specialization(guards = "isNothingTodo(encodingObj, errorsObj, newline_changed)")
        static void nothing(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object encodingObj, @SuppressWarnings("unused") Object errorsObj,
                        @SuppressWarnings("unused") boolean newline_changed) {
            // no change
        }

        @Specialization(guards = "!isNothingTodo(encodingObj, errorsObj, newline_changed)")
        static void changeEncoding(VirtualFrame frame, Node inliningTarget, PTextIO self, Object encodingObj, Object errorsObj, @SuppressWarnings("unused") boolean newline_changed,
                        @Cached IONodes.ToTruffleStringNode asString,
                        @Cached(inline = false) CodecsTruffleModuleBuiltins.LookupTextEncoding lookupTextEncoding,
                        @Cached SetDecoderNode setDecoderNode,
                        @Cached SetEncoderNode setEncoderNode,
                        @Cached FixEncoderStateNode fixEncoderStateNode) {
            /* Use existing settings where new settings are not specified */
            TruffleString encoding = isPNone(encodingObj) ? self.getEncoding() : asString.execute(inliningTarget, encodingObj);
            TruffleString errors;
            if (isPNone(errorsObj)) {
                if (isPNone(encodingObj)) {
                    errors = self.getErrors();
                } else {
                    errors = T_STRICT;
                }
            } else {
                errors = asString.execute(inliningTarget, errorsObj);
            }

            // Create new encoder & decoder
            Object codecInfo = lookupTextEncoding.execute(frame, encoding, T_CODECS_OPEN);
            setDecoderNode.execute(frame, inliningTarget, self, codecInfo, errors);
            setEncoderNode.execute(frame, inliningTarget, self, codecInfo, errors);
            self.setEncoding(encoding);
            self.setErrors(errors);

            fixEncoderStateNode.execute(frame, inliningTarget, self);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FindLineEndingNode extends Node {
        /**
         * Finds the line ending in {@code line}, starting at code-point index {@code start}. If it
         * is found, returns the length of the line, i.e. the number of code points between
         * {@code start} and the end of the line ending. If the line ending is not found, returns -1
         * and sets consumed[0] to the index of the code point (relative to {@code start}) that
         * needs to be examined next time when more characters are available. This usually points to
         * the end of the string. However, if non-universal line endings are involved, {@code line}
         * could end with a partial line ending, in which case {@code consumed[0]} will be set to
         * the code point index of the beginning of the incomplete line ending, relative to
         * {@code start}).
         */
        public abstract int execute(Node inliningTarget, PTextIOBase self, TruffleString line, int start, int[] consumed);

        @Specialization(guards = "self.isReadTranslate()")
        static int doTranslated(@SuppressWarnings("unused") PTextIOBase self, TruffleString line, int start, int[] consumed,
                        @Shared @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached(inline = false) TruffleString.IndexOfCodePointNode indexOfCodePointNode) {
            /* Newlines are already translated, only search for \n */
            int len = codePointLengthNode.execute(line, TS_ENCODING);
            int pos = indexOfCodePointNode.execute(line, '\n', start, len, TS_ENCODING);
            if (pos < 0) {
                consumed[0] = len - start;
                return -1;
            }
            return pos - start + 1;
        }

        @Specialization(guards = {"!self.isReadTranslate()", "self.isReadUniversal()"})
        static int doUniversal(@SuppressWarnings("unused") PTextIOBase self, TruffleString line, int start, int[] consumed,
                        @Shared @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached(inline = false) TruffleString.IndexOfCodePointNode indexOfCodePointNode) {
            /*
             * Universal newline search. Find any of \r, \r\n, \n The decoder ensures that \r\n are
             * not split in two pieces
             */
            int len = codePointLengthNode.execute(line, TS_ENCODING);
            int nlpos = indexOfCodePointNode.execute(line, '\n', start, len, TS_ENCODING);
            int crpos = indexOfCodePointNode.execute(line, '\r', start, len, TS_ENCODING);
            if (crpos < 0) {
                if (nlpos < 0) {
                    consumed[0] = len - start;
                    return -1;
                }
                return nlpos - start + 1; // \n
            }
            if (nlpos < 0) {
                return crpos - start + 1; // \r
            }
            if (nlpos < crpos) {
                return nlpos - start + 1; // \n
            }
            if (nlpos == crpos + 1) {
                return nlpos - start + 1; // \r\n
            }
            return crpos - start + 1;   // \r
        }

        @Specialization(guards = {"!self.isReadTranslate()", "!self.isReadUniversal()"})
        static int doNonUniversal(@SuppressWarnings("unused") PTextIOBase self, TruffleString line, int start, int[] consumed,
                        @Shared @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.IndexOfStringNode indexOfStringNode,
                        @Shared @Cached(inline = false) TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            int len = codePointLengthNode.execute(line, TS_ENCODING);
            TruffleString readNl = self.getReadNewline();
            int nlLen = codePointLengthNode.execute(readNl, TS_ENCODING);
            if (nlLen == 1) {
                int cp = codePointAtIndexNode.execute(readNl, 0, TS_ENCODING);
                int pos = indexOfCodePointNode.execute(line, cp, start, len, TS_ENCODING);
                if (pos >= 0) {
                    return pos - start + 1;
                }
                consumed[0] = len - start;
                return -1;
            }
            int pos = indexOfStringNode.execute(line, readNl, start, len, TS_ENCODING);
            if (pos >= 0) {
                return pos - start + nlLen;
            }
            int firstCp = codePointAtIndexNode.execute(readNl, 0, TS_ENCODING);
            int i = len - (nlLen - 1);
            if (i < start) {
                i = start;
            }
            pos = indexOfCodePointNode.execute(line, firstCp, i, len, TS_ENCODING);
            if (pos < 0) {
                consumed[0] = len - start;
            } else {
                consumed[0] = pos - start;
            }
            return -1;
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 64 -> 45
    protected abstract static class ReadlineNode extends Node {

        public abstract TruffleString execute(VirtualFrame frame, PTextIO self, int limit);

        @Specialization
        static TruffleString readline(VirtualFrame frame, PTextIO self, int limit,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadChunkNode readChunkNode,
                        @Cached WriteFlushNode writeFlushNode,
                        @Cached FindLineEndingNode findLineEndingNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.ConcatNode concatNode) {
            writeFlushNode.execute(frame, inliningTarget, self);

            int chunked = 0;
            int start, endpos, offsetToBuffer;
            TruffleString line = null;
            TruffleStringBuilder chunks = null;
            TruffleString remaining = null;
            int[] consumed = new int[1];

            while (true) {
                /* First, get some data if necessary */
                boolean res = true;
                while (!self.hasDecodedCharsAvailable()) {
                    res = readChunkNode.execute(frame, inliningTarget, self, 0);
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
                    line = concatNode.execute(remaining, self.getDecodedChars(), TS_ENCODING, false);
                    start = 0;
                    offsetToBuffer = codePointLengthNode.execute(remaining, TS_ENCODING);
                    remaining = null;
                    // TODO: PyUnicode_READY(line)?
                }

                endpos = findLineEndingNode.execute(inliningTarget, self, line, start, consumed);
                /*
                 * ptr = PyUnicode_DATA(line); kind = PyUnicode_KIND(line); endpos =
                 * _PyIO_find_line_ending( self.readtranslate, self.readuniversal, self.getReadnl(),
                 * kind, ptr + kind * start, ptr + kind * lineLen, consumed);
                 *
                 */
                if (endpos >= 0) {
                    endpos += start;
                    if (limit >= 0 && (endpos - start) + chunked >= limit) {
                        endpos = start + limit - chunked;
                    }
                    break;
                }

                /* We can put aside up to `endpos` */
                endpos = consumed[0] + start;
                if (limit >= 0 && (endpos - start) + chunked >= limit) {
                    /* Didn't find line ending, but reached length limit */
                    endpos = start + limit - chunked;
                    break;
                }

                if (endpos > start) {
                    /* No line ending seen yet - put aside current data */
                    if (chunks == null) {
                        chunks = TruffleStringBuilder.create(TS_ENCODING);
                    }
                    TruffleString s = substringNode.execute(line, start, endpos - start, TS_ENCODING, true);
                    appendStringNode.execute(chunks, s);
                    chunked += endpos - start;
                }
                /*
                 * There may be some remaining bytes we'll have to prepend to the next chunk of data
                 */
                int lineLen = codePointLengthNode.execute(line, TS_ENCODING);
                if (endpos < lineLen) {
                    remaining = substringNode.execute(line, endpos, lineLen - endpos, TS_ENCODING, true);
                }
                line = null;
                /* We have consumed the buffer */
                self.clearDecodedChars();
            }

            if (line != null) {
                /* Our line ends in the current buffer */
                self.incDecodedCharsUsed(endpos - offsetToBuffer - self.getDecodedCharsUsed());
                int lineLen = codePointLengthNode.execute(line, TS_ENCODING);
                if (start > 0 || endpos < lineLen) {
                    line = substringNode.execute(line, start, endpos - start, TS_ENCODING, remaining != null || chunks != null);
                }
            }
            if (remaining != null) {
                if (chunks == null) {
                    chunks = TruffleStringBuilder.create(TS_ENCODING);
                }
                appendStringNode.execute(chunks, remaining);
            }
            if (chunks != null) {
                if (line != null) {
                    appendStringNode.execute(chunks, line);
                }
                line = toStringNode.execute(chunks);
            }

            return line == null ? T_EMPTY_STRING : line;
        }
    }

    /*
     * cpython/Modules/_io/textio.c:textiowrapper_read_chunk
     */
    @GenerateInline
    @GenerateCached(false)
    protected abstract static class ReadChunkNode extends Node {

        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, PTextIO self, int size_hint);

        @Specialization(guards = "self.hasDecoder()")
        static boolean readChunk(VirtualFrame frame, Node inliningTarget, PTextIO self, int hint,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached SequenceNodes.GetObjectArrayNode getArray,
                        @Cached(inline = false) DecodeNode decodeNode,
                        @Cached PyObjectCallMethodObjArgs callMethodGetState,
                        @Cached PyObjectCallMethodObjArgs callMethodRead,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
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
                Object state = callMethodGetState.execute(frame, inliningTarget, self.getDecoder(), T_GETSTATE);
                /*
                 * Given this, we know there was a valid snapshot point len(decBuffer) bytes ago
                 * with decoder state (b'', decFlags).
                 */
                if (!(state instanceof PTuple)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ILLEGAL_DECODER_STATE);
                }
                Object[] array = getArray.execute(inliningTarget, state);
                if (array.length < 2) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ILLEGAL_DECODER_STATE);
                }

                if (!(array[0] instanceof PBytes)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ILLEGAL_DECODER_STATE_THE_FIRST, array[0]);
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
                inputChunk = callMethodRead.execute(frame, inliningTarget, self.getBuffer(), T_READ1, chunkSize);
            } else {
                inputChunk = callMethodRead.execute(frame, inliningTarget, self.getBuffer(), T_READ, chunkSize);
            }

            Object inputChunkBuf;
            try {
                inputChunkBuf = bufferAcquireLib.acquireReadonly(inputChunk, frame, indirectCallData);
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, S_SHOULD_HAVE_RETURNED_A_BYTES_LIKE_OBJECT_NOT_P, (self.isHasRead1() ? T_READ1 : T_READ), inputChunk);
            }
            try {
                int nbytes = bufferLib.getBufferLength(inputChunkBuf);
                boolean eof = nbytes == 0;

                TruffleString decodedChars = decodeNode.execute(frame, self.getDecoder(), inputChunk, eof);

                self.clearDecodedChars();
                int nchars = self.setDecodedChars(decodedChars, codePointLengthNode);
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
                    self.setSnapshotDecFlags(asSizeNode.executeExact(frame, inliningTarget, decFlags));
                }

                return !eof;
            } finally {
                bufferLib.release(inputChunkBuf, frame, indirectCallData);
            }
        }

        @Specialization(guards = "!self.hasDecoder()")
        static boolean error(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") int size_hint,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, NOT_READABLE);
        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_decode
     */
    @SuppressWarnings("truffle-inlining")       // footprint reduction 80 -> 62
    protected abstract static class DecodeNode extends Node {
        public abstract TruffleString execute(VirtualFrame frame, Object decoder, Object bytes, boolean eof);

        /*
         * @Specialization(limit = "2") String decodeIncDec(VirtualFrame frame, Object decoder,
         * Object o, boolean eof, // TODO: chars = _PyIncrementalNewlineDecoder_decode(decoder,
         * bytes, eof); }
         *
         */

        @Specialization
        static TruffleString decodeGeneric(VirtualFrame frame, Object decoder, Object o, boolean eof,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castNode,
                        @Cached PyObjectCallMethodObjArgs callMethodDecode) {
            Object decoded = callMethodDecode.execute(frame, inliningTarget, decoder, T_DECODE, o, eof);
            return castNode.cast(inliningTarget, decoded, DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P, decoded);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    protected abstract static class DecoderSetStateNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PTextIO self, PTextIO.CookieType cookie, PythonObjectFactory factory);

        @Specialization(guards = "!self.hasDecoder()")
        static void nothing(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") PTextIO.CookieType cookie, @SuppressWarnings("unused") PythonObjectFactory factory) {
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
        static void atInit(VirtualFrame frame, Node inliningTarget, PTextIO self, @SuppressWarnings("unused") PTextIO.CookieType cookie, @SuppressWarnings("unused") PythonObjectFactory factory,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodReset) {
            callMethodReset.execute(frame, inliningTarget, self.getDecoder(), T_RESET);
        }

        @Specialization(guards = {"self.hasDecoder()", "!isAtInit(cookie)"})
        static void decoderSetstate(VirtualFrame frame, Node inliningTarget, PTextIO self, PTextIO.CookieType cookie, PythonObjectFactory factory,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodSetState) {
            PTuple tuple = factory.createTuple(new Object[]{factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY), cookie.decFlags});
            callMethodSetState.execute(frame, inliningTarget, self.getDecoder(), T_SETSTATE, tuple);

        }
    }

    @GenerateInline
    @GenerateCached(false)
    protected abstract static class DecoderResetNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PTextIO self);

        @Specialization(guards = "!self.hasDecoder()")
        static void nothing(@SuppressWarnings("unused") PTextIO self) {
            // nothing to do.
        }

        @Specialization(guards = "self.hasDecoder()")
        static void reset(VirtualFrame frame, Node inliningTarget, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            callMethod.execute(frame, inliningTarget, self.getDecoder(), T_RESET);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    protected abstract static class EncoderResetNode extends Node {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PTextIO self, boolean startOfStream);

        @Specialization(guards = "startOfStream")
        static void encoderResetStart(VirtualFrame frame, Node inliningTarget, PTextIO self, @SuppressWarnings("unused") boolean startOfStream,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodReset) {
            callMethodReset.execute(frame, inliningTarget, self.getEncoder(), T_RESET);
            self.setEncodingStartOfStream(true);
        }

        @Specialization(guards = "!startOfStream")
        static void encoderResetNotStart(VirtualFrame frame, Node inliningTarget, PTextIO self, @SuppressWarnings("unused") boolean startOfStream,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodSetState) {
            callMethodSetState.execute(frame, inliningTarget, self.getEncoder(), T_SETSTATE, 0);
            self.setEncodingStartOfStream(false);

        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_fix_encoder_state
     */
    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    protected abstract static class FixEncoderStateNode extends PNodeWithContext {

        public abstract void execute(VirtualFrame frame, Node inliningTarget, PTextIO self);

        @Specialization(guards = {"!self.isSeekable() || !self.hasEncoder()"})
        void nothing(@SuppressWarnings("unused") PTextIO self) {
            // nothing to do
        }

        @Specialization(guards = {"self.isSeekable()", "self.hasEncoder()"})
        static void fixEncoderState(VirtualFrame frame, @SuppressWarnings("unused") Node inliningTarget, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Cached PyObjectCallMethodObjArgs callMethodSetState,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            self.setEncodingStartOfStream(true);
            Object cookieObj = callMethodTell.execute(frame, inliningTarget, self.getBuffer(), T_TELL);
            if (!eqNode.compare(frame, inliningTarget, cookieObj, 0)) {
                self.setEncodingStartOfStream(false);
                callMethodSetState.execute(frame, inliningTarget, self.getEncoder(), T_SETSTATE, 0);
            }
        }
    }

    /*
     * cpython/Modules/_io/textio.c:_textiowrapper_set_decoder
     */
    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    protected abstract static class SetDecoderNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Node inliningTarget, PTextIO self, Object codecInfo, TruffleString errors);

        @Specialization
        static void setDecoder(VirtualFrame frame, Node inliningTarget, PTextIO self, Object codecInfo, TruffleString errors,
                        @Cached(inline = false) MakeIncrementalcodecNode makeIncrementalcodecNode,
                        @Cached InlinedConditionProfile isTrueProfile,
                        @Cached PyObjectCallMethodObjArgs callMethodReadable,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached(inline = false) PythonObjectFactory factory) {
            Object res = callMethodReadable.execute(frame, inliningTarget, self.getBuffer(), T_READABLE);
            if (isTrueProfile.profile(inliningTarget, !isTrueNode.execute(frame, inliningTarget, res))) {
                return;
            }
            Object decoder = makeIncrementalcodecNode.execute(frame, codecInfo, errors, T_INCREMENTALDECODER);
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
    @GenerateCached(false)
    @GenerateInline
    protected abstract static class SetEncoderNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Node inliningTarget, PTextIO self, Object codecInfo, TruffleString errors);

        @Specialization
        static void setEncoder(VirtualFrame frame, Node inliningTarget, PTextIO self, Object codecInfo, TruffleString errors,
                        @Cached(inline = false) MakeIncrementalcodecNode makeIncrementalcodecNode,
                        @Cached InlinedConditionProfile isTrueProfile,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyObjectCallMethodObjArgs callMethodWritable) {
            Object res = callMethodWritable.execute(frame, inliningTarget, self.getBuffer(), T_WRITABLE);
            if (isTrueProfile.profile(inliningTarget, !isTrueNode.execute(frame, inliningTarget, res))) {
                return;
            }
            self.setEncoder(null);
            self.setEncodefunc(null);
            self.setEncoder(makeIncrementalcodecNode.execute(frame, codecInfo, errors, T_INCREMENTALENCODER));
            // TODO: find encoder function
            // res = libCodecInfo.lookupAttributeStrict(codecInfo, frame, NAME);
            // self.setEncodefunc(null);
        }
    }

    @GenerateUncached
    @GenerateCached(false)
    @GenerateInline
    public abstract static class TextIOWrapperInitNode extends PNodeWithContext {

        public abstract void execute(Frame frame, Node inliningTarget, PTextIO self, Object buffer, Object encodingArg,
                        TruffleString errors, Object newlineArg, boolean lineBuffering, boolean writeThrough);

        @Specialization
        static void init(VirtualFrame frame, Node inliningTarget, PTextIO self, Object buffer, Object encodingArg,
                        TruffleString errors, Object newlineArg, boolean lineBuffering, boolean writeThrough,
                        @Cached(inline = false) CodecsTruffleModuleBuiltins.GetEncodingNode getEncodingNode,
                        @Cached(inline = false) CodecsTruffleModuleBuiltins.LookupTextEncoding lookupTextEncoding,
                        @Cached SetEncoderNode setEncoderNode,
                        @Cached SetDecoderNode setDecoderNode,
                        @Cached FixEncoderStateNode fixEncoderStateNode,
                        @Cached PyObjectCallMethodObjArgs callMethodSeekable,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached(inline = false) TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached(inline = false) TruffleString.EqualNode equalNode,
                        @Cached(inline = false) WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            self.setOK(false);
            self.setDetached(false);
            // encoding and newline are processed through arguments clinic and safe to cast.
            TruffleString encoding = encodingArg == PNone.NONE ? null : (TruffleString) encodingArg;
            TruffleString newline = newlineArg == PNone.NONE ? null : (TruffleString) newlineArg;
            if (encoding == null) {
                if (PythonContext.get(inliningTarget).getOption(PythonOptions.WarnDefaultEncodingFlag)) {
                    warnNode.warnEx(frame, EncodingWarning, ErrorMessages.WARN_ENCODING_ARGUMENT_NOT_SPECIFIED, 1);
                }
            } else if (equalNode.execute(T_LOCALE, encoding, TS_ENCODING)) {
                encoding = null;
            } else {
                if (indexOfCodePointNode.execute(encoding, 0, 0, codePointLengthNode.execute(encoding, TS_ENCODING), TS_ENCODING) != -1) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, EMBEDDED_NULL_CHARACTER);
                }
            }

            if (newline != null) {
                if (indexOfCodePointNode.execute(newline, 0, 0, codePointLengthNode.execute(newline, TS_ENCODING), TS_ENCODING) != -1) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, EMBEDDED_NULL_CHARACTER);
                }
                validateNewline(newline, inliningTarget, raiseNode, codePointLengthNode, codePointAtIndexNode);
            }

            self.clearAll();
            /* Try os.device_encoding(fileno) */
            // TODO: find file encoding using system nl_langinfo
            if (encoding == null && !self.hasEncoding()) {
                try {
                    self.setEncoding(getEncodingNode.execute(frame));
                } catch (Exception e) {
                    self.setEncoding(T_ASCII);
                }
            }
            if (self.hasEncoding()) {
                encoding = self.getEncoding();
            } else if (encoding != null) {
                self.setEncoding(encoding);
            } else {
                throw raiseNode.get(inliningTarget).raise(OSError, COULD_NOT_DETERMINE_DEFAULT_ENCODING);
            }

            /* Check we have been asked for a real text encoding */
            Object codecInfo = lookupTextEncoding.execute(frame, encoding, T_CODECS_OPEN);

            self.setErrors(errors);
            self.setChunkSize(8192);
            self.setLineBuffering(lineBuffering);
            self.setWriteThrough(writeThrough);
            setNewline(self, newline, equalNode);

            self.setBuffer(buffer);

            /* Build the decoder object */
            setDecoderNode.execute(frame, inliningTarget, self, codecInfo, errors);

            /* Build the encoder object */
            setEncoderNode.execute(frame, inliningTarget, self, codecInfo, errors);

            if (buffer instanceof PBuffered) {
                /* Cache the raw FileIO object to speed up 'closed' checks */
                if (((PBuffered) buffer).isFastClosedChecks()) {
                    PFileIO f = ((PBuffered) buffer).getFileIORaw();
                    self.setFileIO(f);
                    f.setUTF8Write(false);
                }
            }

            Object res = callMethodSeekable.execute(frame, inliningTarget, buffer, T_SEEKABLE);
            self.setTelling(isTrueNode.execute(frame, inliningTarget, res));
            self.setSeekable(self.isTelling());

            self.setHasRead1(lookup.execute(frame, inliningTarget, buffer, T_READ1) != PNone.NO_VALUE);

            self.setEncodingStartOfStream(false);
            fixEncoderStateNode.execute(frame, inliningTarget, self);

            self.setOK(true);
        }
    }
}
