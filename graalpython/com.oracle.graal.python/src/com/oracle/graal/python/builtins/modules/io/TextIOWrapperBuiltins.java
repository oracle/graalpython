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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PTextIOWrapper;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.BUFFER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.DECODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ENCODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ENCODING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ERRORS;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.LINE_BUFFERING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.RECONFIGURE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.WRITE_THROUGH;
import static com.oracle.graal.python.builtins.modules.io.IONodes._CHUNK_SIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes._DEALLOC_WARN;
import static com.oracle.graal.python.builtins.modules.io.IONodes._FINALIZING;
import static com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.setNewline;
import static com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.validateNewline;
import static com.oracle.graal.python.nodes.ErrorMessages.A_STRICTLY_POSITIVE_INTEGER_IS_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_DO_NONZERO_CUR_RELATIVE_SEEKS;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_DO_NONZERO_END_RELATIVE_SEEKS;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_RECONSTRUCT_LOGICAL_FILE_POSITION;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_RESTORE_LOGICAL_FILE_POSITION;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.DETACHED_BUFFER;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODER_SHOULD_RETURN_A_BYTES_OBJECT_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_DECODER_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_DECODER_STATE_THE_FIRST;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_WHENCE_D_SHOULD_BE_D_D_OR_D;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SEEK_POSITION_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_POSSIBLE_TO_SET_THE_ENCODING_OR;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_READABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_WRITABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.TELLING_POSITION_DISABLED_BY_NEXT_CALL;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_READ_SHOULD_HAVE_RETURNED_A_BYTES_OBJECT_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_STREAM_IS_NOT_SEEKABLE;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.PGuards.isPNone;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode.chainExceptions;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PTextIOWrapper)
public final class TextIOWrapperBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TextIOWrapperBuiltinsFactory.getFactories();
    }

    abstract static class InitCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isOK()")
        @SuppressWarnings("unused")
        Object initError(PTextIO self) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class AttachedCheckPythonUnaryBuiltinNode extends InitCheckPythonUnaryBuiltinNode {
        protected static boolean checkAttached(PTextIO self) {
            return self.isOK() && !self.isDetached();
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        @SuppressWarnings("unused")
        Object attachError(PTextIO self) {
            throw raise(ValueError, DETACHED_BUFFER);
        }
    }

    abstract static class ClosedCheckPythonUnaryBuiltinNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Child private TextIOWrapperNodes.CheckClosedNode checkClosedNode = TextIOWrapperNodesFactory.CheckClosedNodeGen.create();

        protected boolean isOpen(VirtualFrame frame, PTextIO self) {
            checkClosedNode.execute(frame, self);
            return true;
        }
    }

    abstract static class InitCheckPythonBinaryClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere("abstract TextIOWrapper init checks");
        }

        @Specialization(guards = "!self.isOK()")
        @SuppressWarnings("unused")
        Object initError(PTextIO self, Object o) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class AttachedCheckPythonBinaryClinicBuiltinNode extends InitCheckPythonBinaryClinicBuiltinNode {
        protected static boolean checkAttached(PTextIO self) {
            return self.isOK() && !self.isDetached();
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        @SuppressWarnings("unused")
        Object attachError(PTextIO self, Object o) {
            throw raise(ValueError, DETACHED_BUFFER);
        }
    }

    abstract static class ClosedCheckPythonBinaryClinicBuiltinNode extends AttachedCheckPythonBinaryClinicBuiltinNode {
        @Child private TextIOWrapperNodes.CheckClosedNode checkClosedNode = TextIOWrapperNodesFactory.CheckClosedNodeGen.create();

        protected boolean isOpen(VirtualFrame frame, PTextIO self) {
            checkClosedNode.execute(frame, self);
            return true;
        }
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "encoding", "errors", "newline", "line_buffering", "write_through"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "line_buffering", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @ArgumentClinic(name = "write_through", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object init(VirtualFrame frame, PTextIO self, Object buffer, Object encodingArg,
                        String errors, Object newlineArg, boolean lineBuffering, boolean writeThrough,
                        @Cached TextIOWrapperNodes.TextIOWrapperInitNode initNode) {
            initNode.execute(frame, self, buffer, encodingArg, errors, newlineArg, lineBuffering, writeThrough);
            return PNone.NONE;
        }
    }

    @Builtin(name = DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object detach(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            callMethod.execute(frame, self, FLUSH);
            Object buffer = self.getBuffer();
            self.setBuffer(null);
            self.setDetached(true);
            return buffer;
        }
    }

    @Builtin(name = RECONFIGURE, minNumOfPositionalArgs = 1, keywordOnlyNames = {"encoding", "errors", "newline", "line_buffering", "write_through"})
    @GenerateNodeFactory
    abstract static class ReconfigureNode extends PythonBuiltinNode {

        protected static boolean isValid(PTextIO self, Object encodingObj, Object errorsObj, Object newlineObj) {
            if (self.getDecodedChars() != null) {
                return isPNone(encodingObj) && isPNone(errorsObj) && isNoValue(newlineObj);
            }
            return true;
        }

        @Specialization(guards = "isValid(self, encodingObj, errorsObj, newlineObj)")
        Object reconfigure(VirtualFrame frame, PTextIO self, Object encodingObj,
                        Object errorsObj, Object newlineObj,
                        Object lineBufferingObj, Object writeThroughObj,
                        @Cached IONodes.ToStringNode toStringNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached TextIOWrapperNodes.ChangeEncodingNode changeEncodingNode) {
            String newline = null;
            if (!isPNone(newlineObj)) {
                newline = toStringNode.execute(newlineObj);
                validateNewline(newline, getRaiseNode());
            }

            boolean lineBuffering, writeThrough;
            if (isPNone(lineBufferingObj)) {
                lineBuffering = self.isLineBuffering();
            } else {
                lineBuffering = isTrueNode.execute(frame, lineBufferingObj);
            }
            if (isPNone(writeThroughObj)) {
                writeThrough = self.isWriteThrough();
            } else {
                writeThrough = isTrueNode.execute(frame, writeThroughObj);
            }
            callMethod.execute(frame, self, FLUSH);
            self.setB2cratio(0);
            if (!isNoValue(newlineObj)) {
                setNewline(self, newline);
            }

            changeEncodingNode.execute(frame, self, encodingObj, errorsObj, !isNoValue(newlineObj));
            self.setLineBuffering(lineBuffering);
            self.setWriteThrough(writeThrough);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isValid(self, encodingObj, errorsObj, newlineObj)")
        Object error(VirtualFrame frame, PTextIO self, Object encodingObj, Object errorsObj, Object newlineObj, Object lineBufferingObj, Object writeThroughObj) {
            throw raise(IOUnsupportedOperation, NOT_POSSIBLE_TO_SET_THE_ENCODING_OR);
        }
    }

    @Builtin(name = WRITE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "str"})
    @ArgumentClinic(name = "str", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class WriteNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "!self.hasEncoder()"})
        Object write(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") String data) {
            throw raise(IOUnsupportedOperation, NOT_WRITABLE);
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "self.hasEncoder()"})
        Object write(VirtualFrame frame, PTextIO self, String data,
                        @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached TextIOWrapperNodes.DecoderResetNode decoderResetNode,
                        @Cached PyObjectCallMethodObjArgs callMethodEncode,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            boolean haslf = false;
            boolean needflush = false;
            String text = data;
            int textlen = PString.length(text);

            if ((self.isWriteTranslate() && self.hasWriteNewline()) || self.isLineBuffering()) {
                if (PString.indexOf(text, "\n", 0) != -1) {
                    haslf = true;
                }
            }

            if (haslf && self.isWriteTranslate() && self.hasWriteNewline()) {
                text = PythonUtils.replace(text, "\n", self.getWriteNewline());
            }

            if (self.isLineBuffering() && (haslf || PString.indexOf(text, "\r", 0) != -1)) {
                needflush = true;
            }

            /*-
            // TODO: check if this is needed.
            if (self.encodefunc != null) {
            if (PyUnicode_IS_ASCII(text) && is_asciicompat_encoding(self.encodefunc)) {
                b = text;
            }
            else {
                b = (*self.encodefunc)((PyObject *) self, text);
            }
            self.encodingStartOfStream = false;
            }
            else
             */
            Object b = callMethodEncode.execute(frame, self.getEncoder(), ENCODE, text);

            if (b != text && !(b instanceof PBytes)) {
                throw raise(TypeError, ENCODER_SHOULD_RETURN_A_BYTES_OBJECT_NOT_P, b);
            }

            byte[] encodedText = bufferLib.getInternalOrCopiedByteArray(b);
            int bytes_len = bufferLib.getBufferLength(b);

            self.appendPendingBytes(encodedText, bytes_len);
            if (self.getPendingBytesCount() > self.getChunkSize() || needflush || self.isWriteThrough()) {
                writeFlushNode.execute(frame, self);
            }

            if (needflush) {
                callMethodFlush.execute(frame, self.getBuffer(), FLUSH);
            }

            self.clearDecodedChars();
            self.clearSnapshot();
            if (self.hasDecoder()) {
                decoderResetNode.execute(frame, self);
            }

            return textlen;
        }
    }

    @Builtin(name = READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "self.hasDecoder()", "n < 0"})
        static Object readAll(VirtualFrame frame, PTextIO self, @SuppressWarnings("unused") int n,
                        @Cached TextIOWrapperNodes.DecodeNode decodeNode,
                        @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            writeFlushNode.execute(frame, self);

            /* Read everything */
            Object bytes = callMethod.execute(frame, self.getBuffer(), READ);
            String decoded = decodeNode.execute(frame, self.getDecoder(), bytes, true);
            StringBuilder result = getDecodedChars(self, -1);
            PythonUtils.append(result, decoded);
            self.clearDecodedChars();
            self.clearSnapshot();
            return PythonUtils.sbToString(result);
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "self.hasDecoder()", "n >= 0"})
        static Object read(VirtualFrame frame, PTextIO self, int n,
                        @Cached TextIOWrapperNodes.ReadChunkNode readChunkNode,
                        @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode) {
            writeFlushNode.execute(frame, self);
            StringBuilder result = getDecodedChars(self, n);
            int remaining = n - result.length();
            StringBuilder chunks = null;
            /* Keep reading chunks until we have n characters to return */
            while (remaining > 0) {
                boolean res = readChunkNode.execute(frame, self, remaining);
                // TODO: _PyIO_trap_eintr()
                if (!res) /* EOF */ {
                    break;
                }
                if (chunks == null) {
                    chunks = PythonUtils.newStringBuilder();
                }
                if (result.length() > 0) {
                    PythonUtils.append(chunks, result);
                }

                result = getDecodedChars(self, remaining);
                remaining -= result.length();
            }
            if (chunks != null) {
                PythonUtils.append(chunks, result);
                return PythonUtils.sbToString(chunks);
            }

            return PythonUtils.sbToString(result);
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "!self.hasDecoder()"})
        Object noDecoder(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") int n) {
            throw raise(IOUnsupportedOperation, NOT_READABLE);
        }

        private static StringBuilder getDecodedChars(PTextIO self, int len) {
            StringBuilder chars = PythonUtils.newStringBuilder();
            int avail;
            int n = len;

            if (!self.hasDecodedChars()) {
                return chars;
            }

            /* decoded_chars is guaranteed to be "ready". */
            avail = self.getDecodedChars().length() - self.getDecodedCharsUsed();

            assert (avail >= 0);

            if (n < 0 || n > avail) {
                n = avail;
            }

            if (self.getDecodedCharsUsed() > 0 || n < avail) {
                PythonUtils.append(chars, PythonUtils.substring(self.getDecodedChars(), self.getDecodedCharsUsed(), self.getDecodedCharsUsed() + n));
            } else {
                chars = self.getDecodedChars();
            }

            self.incDecodedCharsUsed(n);
            return chars;
        }
    }

    @Builtin(name = READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)"})
        static Object readline(VirtualFrame frame, PTextIO self, int limit,
                        @Cached TextIOWrapperNodes.ReadlineNode readlineNode) {
            return readlineNode.execute(frame, self, limit);
        }
    }

    @Builtin(name = FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)"})
        static Object flush(VirtualFrame frame, PTextIO self,
                        @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            self.setTelling(self.isSeekable());
            writeFlushNode.execute(frame, self);
            return callMethod.execute(frame, self.getBuffer(), FLUSH);
        }
    }

    @Builtin(name = CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object close(VirtualFrame frame, PTextIO self,
                        @Cached ClosedNode closedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodDeallocWarn,
                        @Cached PyObjectCallMethodObjArgs callMethodClose,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object res = closedNode.execute(frame, self);
            if (isTrueNode.execute(frame, res)) {
                return PNone.NONE;
            } else {
                if (self.isFinalizing()) {
                    callMethodDeallocWarn.execute(frame, self.getBuffer(), _DEALLOC_WARN);
                }

                try {
                    callMethodFlush.execute(frame, self, FLUSH);
                } catch (PException e) {
                    try {
                        callMethodClose.execute(frame, self.getBuffer(), CLOSE);
                        throw e;
                    } catch (PException ee) {
                        chainExceptions(ee.getEscapedException(), e);
                        throw ee.getExceptionForReraise();
                    }
                }
                return callMethodClose.execute(frame, self.getBuffer(), CLOSE);
            }
        }
    }

    @Builtin(name = FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object fileno(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getBuffer(), FILENO);
        }
    }

    @Builtin(name = SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object seekable(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getBuffer(), SEEKABLE);
        }
    }

    @Builtin(name = READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object readable(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getBuffer(), READABLE);
        }
    }

    @Builtin(name = WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class WritableNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object writable(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getBuffer(), WRITABLE);
        }
    }

    @Builtin(name = ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object isatty(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, self.getBuffer(), ISATTY);
        }
    }

    protected static void encoderSetState(VirtualFrame frame, PTextIO self, PTextIO.CookieType cookie,
                    TextIOWrapperNodes.EncoderResetNode encoderResetNode) {
        encoderResetNode.execute(frame, self, cookie.startPos == 0 && cookie.decFlags == 0);
    }

    @Builtin(name = SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "cookie", "whence"})
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "checkAttached(self)")
        Object seek(VirtualFrame frame, PTextIO self, Object c, int whence,
                        @Cached ConditionProfile overflow,
                        @Cached CastToJavaLongLossyNode toLong,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached TextIOWrapperNodes.DecoderSetStateNode decoderSetStateNode,
                        @Cached TextIOWrapperNodes.DecoderResetNode decoderResetNode,
                        @Cached TextIOWrapperNodes.EncoderResetNode encoderResetNode,
                        @Cached TextIOWrapperNodes.CheckClosedNode checkClosedNode,
                        @Cached TextIOWrapperNodes.CheckDecodedNode checkDecodedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodSeek,
                        @Cached PyObjectCallMethodObjArgs callMethodRead,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            checkClosedNode.execute(frame, self);
            if (!self.isSeekable()) {
                throw raise(IOUnsupportedOperation, UNDERLYING_STREAM_IS_NOT_SEEKABLE);
            }

            Object cookieObj = c;

            switch (whence) {
                case SEEK_CUR:
                    /* seek relative to current position */
                    if (!eqNode.execute(frame, cookieObj, 0)) {
                        throw raise(IOUnsupportedOperation, CAN_T_DO_NONZERO_CUR_RELATIVE_SEEKS);
                    }

                    /*
                     * Seeking to the current position should attempt to sync the underlying buffer
                     * with the current position.
                     */
                    cookieObj = callMethodTell.execute(frame, self, TELL);
                    break;

                case SEEK_END:
                    /* seek relative to end of file */
                    if (!eqNode.execute(frame, cookieObj, 0)) {
                        throw raise(IOUnsupportedOperation, CAN_T_DO_NONZERO_END_RELATIVE_SEEKS);
                    }

                    callMethodFlush.execute(frame, self, FLUSH);

                    self.clearDecodedChars();
                    self.clearSnapshot();
                    if (self.hasDecoder()) {
                        decoderResetNode.execute(frame, self);
                    }

                    Object res = callMethodSeek.execute(frame, self.getBuffer(), SEEK, 0, 2);
                    if (self.hasEncoder()) {
                        /* If seek() == 0, we are at the start of stream, otherwise not */
                        encoderResetNode.execute(frame, self, eqNode.execute(frame, res, 0));
                    }
                    return res;

                case SEEK_SET:
                    break;

                default:
                    throw raise(ValueError, INVALID_WHENCE_D_SHOULD_BE_D_D_OR_D, whence, SEEK_SET, SEEK_CUR, SEEK_END);
            }

            Object cookieLong = indexNode.execute(frame, cookieObj);
            PTextIO.CookieType cookie;
            if (cookieLong instanceof PInt) {
                if (((PInt) cookieLong).isNegative()) {
                    throw raise(ValueError, NEGATIVE_SEEK_POSITION_D, cookieLong);
                }
                cookie = PTextIO.CookieType.parse((PInt) cookieLong, overflow, getRaiseNode());
            } else {
                long l = toLong.execute(cookieLong);
                if (l < 0) {
                    throw raise(ValueError, NEGATIVE_SEEK_POSITION_D, cookieLong);
                }
                cookie = PTextIO.CookieType.parse(l, overflow, getRaiseNode());
            }

            callMethodFlush.execute(frame, self, FLUSH);

            /*
             * The strategy of seek() is to go back to the safe start point and replay the effect of
             * read(chars_to_skip) from there.
             */

            /* Seek back to the safe start point. */
            callMethodSeek.execute(frame, self.getBuffer(), SEEK, cookie.startPos);

            self.clearDecodedChars();
            self.clearSnapshot();

            /* Restore the decoder to its state from the safe start point. */
            decoderSetStateNode.execute(frame, self, cookie, factory());

            if (cookie.charsToSkip != 0) {
                /* Just like _read_chunk, feed the decoder and save a snapshot. */
                Object inputChunk = callMethodRead.execute(frame, self.getBuffer(), READ, cookie.bytesToFeed);

                if (!(inputChunk instanceof PBytes)) {
                    throw raise(TypeError, UNDERLYING_READ_SHOULD_HAVE_RETURNED_A_BYTES_OBJECT_NOT_S, inputChunk);
                }

                self.setSnapshotDecFlags(cookie.decFlags);
                // TODO avoid copy?
                self.setSnapshotNextInput(bufferLib.getCopiedByteArray(inputChunk));

                String decoded = checkDecodedNode.execute(frame, self, inputChunk, cookie.needEOF != 0);
                self.appendDecodedChars(decoded);
                self.setDecodedCharsUsed(0);

                /* Skip chars_to_skip of the decoded characters. */
                if (self.getDecodedChars().length() < cookie.charsToSkip) {
                    throw raise(OSError, CAN_T_RESTORE_LOGICAL_FILE_POSITION);
                }
                self.setDecodedCharsUsed(cookie.charsToSkip);
            } else {
                self.setSnapshotDecFlags(cookie.decFlags);
                self.setSnapshotNextInput(PythonUtils.EMPTY_BYTE_ARRAY);
            }

            /* Finally, reset the encoder (merely useful for proper BOM handling) */
            if (self.hasEncoder()) {
                encoderSetState(frame, self, cookie, encoderResetNode);
            }
            return cookieObj;
        }

        protected static boolean checkAttached(PTextIO self) {
            return self.isOK() && !self.isDetached();
        }

        @Specialization(guards = "!self.isOK()")
        Object initError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object o1, @SuppressWarnings("unused") Object o2) {
            throw raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        Object attachError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object o1, @SuppressWarnings("unused") Object o2) {
            throw raise(ValueError, DETACHED_BUFFER);
        }
    }

    @Builtin(name = TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "!self.isSeekable()"})
        Object notSeekable(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self) {
            throw raise(IOUnsupportedOperation, UNDERLYING_STREAM_IS_NOT_SEEKABLE);
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "!self.isTelling()"})
        Object notTelling(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self) {
            throw raise(OSError, TELLING_POSITION_DISABLED_BY_NEXT_CALL);
        }

        protected static boolean hasDecoderAndSnapshot(PTextIO self) {
            return self.hasDecoder() && self.hasSnapshotNextInput();
        }

        @Specialization(guards = {
                        "checkAttached(self)", //
                        "isOpen(frame, self)", //
                        "self.isSeekable()", //
                        "self.isTelling()", //
                        "!hasDecoderAndSnapshot(self)", //
        })
        static Object getPos(VirtualFrame frame, PTextIO self,
                        @Shared("writeFlush") @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Shared("callTell") @Cached PyObjectCallMethodObjArgs callMethodTell) {
            writeFlushNode.execute(frame, self);
            callMethodFlush.execute(frame, self, FLUSH);
            return callMethodTell.execute(frame, self.getBuffer(), TELL);
        }

        protected static boolean hasUsedDecodedChar(PTextIO self) {
            /* How many decoded characters have been used up since the snapshot? */
            return self.getDecodedCharsUsed() > 0;
        }

        private static PTextIO.CookieType getCookie(VirtualFrame frame, PTextIO self,
                        TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        PyObjectCallMethodObjArgs callMethodFlush,
                        PyObjectCallMethodObjArgs callMethodTell,
                        PyLongAsLongNode asLongNode) {
            Object posobj = getPos(frame, self, writeFlushNode, callMethodFlush, callMethodTell);
            PTextIO.CookieType cookie = new PTextIO.CookieType();
            cookie.startPos = asLongNode.execute(frame, posobj);
            /* Skip backward to the snapshot point (see _read_chunk). */
            cookie.decFlags = self.getSnapshotDecFlags();
            cookie.startPos -= self.getSnapshotNextInput().length;
            return cookie;
        }

        @Specialization(guards = {
                        "checkAttached(self)", //
                        "isOpen(frame, self)", //
                        "self.isSeekable()", //
                        "self.isTelling()", //
                        "hasDecoderAndSnapshot(self)", //
                        "!hasUsedDecodedChar(self)" //
        })
        Object didntMove(VirtualFrame frame, PTextIO self,
                        @Shared("writeFlush") @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Shared("callTell") @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Cached PyLongAsLongNode asLongNode) {
            PTextIO.CookieType cookie = getCookie(frame, self, writeFlushNode, callMethodFlush, callMethodTell, asLongNode);
            /* We haven't moved from the snapshot point. */
            return PTextIO.CookieType.build(cookie, factory());
        }

        @Specialization(guards = {
                        "checkAttached(self)", //
                        "isOpen(frame, self)", //
                        "self.isSeekable()", //
                        "self.isTelling()", //
                        "hasDecoderAndSnapshot(self)", //
                        "hasUsedDecodedChar(self)" //
        })
        Object tell(VirtualFrame frame, PTextIO self,
                        @Shared("writeFlush") @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached TextIOWrapperNodes.DecoderSetStateNode decoderSetStateNode,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached IONodes.ToStringNode toString,
                        @Shared("callFlush") @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Shared("callTell") @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Cached PyObjectCallMethodObjArgs callMethodDecode,
                        @Cached PyObjectCallMethodObjArgs callMethodGetState,
                        @Cached PyObjectCallMethodObjArgs callMethodSetState,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @CachedLibrary(limit = "2") InteropLibrary isString) {
            PTextIO.CookieType cookie = getCookie(frame, self, writeFlushNode, callMethodFlush, callMethodTell, asLongNode);
            byte[] snapshotNextInput = self.getSnapshotNextInput();
            int nextInputLength = self.getSnapshotNextInput().length;
            int decodedCharsUsed = self.getDecodedCharsUsed();

            /* Decoder state will be restored at the end */
            Object savedState = callMethodGetState.execute(frame, self.getDecoder(), GETSTATE);
            /* Fast search for an acceptable start point, close to our current pos */
            int skipBytes = (int) (self.getB2cratio() * decodedCharsUsed);
            int skipBack = 1;
            assert (skipBack <= nextInputLength);
            while (skipBytes > 0) {
                /* Decode up to temptative start point */
                decoderSetStateNode.execute(frame, self, cookie, factory());
                PBytes in = factory().createBytes(snapshotNextInput, skipBytes);
                int charsDecoded = decoderDecode(frame, self, in, callMethodDecode, toString);
                if (charsDecoded <= decodedCharsUsed) {
                    Object[] state = decoderGetstate(frame, self, savedState, getObjectArrayNode, callMethodGetState, callMethodSetState);
                    int decFlags = asSizeNode.executeExact(frame, state[1]);
                    int decBufferLen = sizeNode.execute(frame, state[0]);
                    if (decBufferLen == 0) {
                        /* Before pos and no bytes buffered in decoder => OK */
                        cookie.decFlags = decFlags;
                        decodedCharsUsed -= charsDecoded;
                        break;
                    }
                    /* Skip back by buffered amount and reset heuristic */
                    skipBytes -= decBufferLen;
                    skipBack = 1;
                } else {
                    /* We're too far ahead, skip back a bit */
                    skipBytes -= skipBack;
                    skipBack *= 2;
                }
            }
            if (skipBytes <= 0) {
                skipBytes = 0;
                decoderSetStateNode.execute(frame, self, cookie, factory());
            }

            /* Note our initial start point. */
            cookie.startPos += skipBytes;
            cookie.charsToSkip = decodedCharsUsed;
            if (decodedCharsUsed == 0) {
                callMethodSetState.execute(frame, self.getDecoder(), SETSTATE, savedState);

                /* The returned cookie corresponds to the last safe start point. */
                cookie.charsToSkip = decodedCharsUsed;
                return PTextIO.CookieType.build(cookie, factory());
            }

            int charsDecoded = 0;
            byte[] input = PythonUtils.arrayCopyOfRange(snapshotNextInput, skipBytes, nextInputLength);
            while (input.length > 0) {
                PBytes start = factory().createBytes(input, 1);
                int n = decoderDecode(frame, self, start, callMethodDecode, toString);
                /* We got n chars for 1 byte */
                charsDecoded += n;
                cookie.bytesToFeed += 1;
                Object[] state = decoderGetstate(frame, self, savedState, getObjectArrayNode, callMethodGetState, callMethodSetState);
                int decFlags = asSizeNode.executeExact(frame, state[1]);
                int decBufferLen = sizeNode.execute(frame, state[0]);

                if (decBufferLen == 0 && charsDecoded <= decodedCharsUsed) {
                    /* Decoder buffer is empty, so this is a safe start point. */
                    cookie.startPos += cookie.bytesToFeed;
                    decodedCharsUsed -= charsDecoded;
                    cookie.decFlags = decFlags;
                    cookie.bytesToFeed = 0;
                    charsDecoded = 0;
                }
                if (charsDecoded >= decodedCharsUsed) {
                    break;
                }
                input = PythonUtils.arrayCopyOfRange(input, 1, input.length);
            }
            if (input.length == 0) {
                /* We didn't get enough decoded data; signal EOF to get more. */
                Object decoded = callMethodDecode.execute(frame, self.getDecoder(), DECODE, "",
                                /* final = */ true);

                if (!isString.isString(decoded)) {
                    fail(frame, self, savedState, callMethodSetState);
                    throw raise(TypeError, DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P, decoded);
                }

                charsDecoded += sizeNode.execute(frame, decoded);
                cookie.needEOF = 1;

                if (charsDecoded < decodedCharsUsed) {
                    fail(frame, self, savedState, callMethodSetState);
                    throw raise(OSError, CAN_T_RECONSTRUCT_LOGICAL_FILE_POSITION);
                }
            }
            callMethodSetState.execute(frame, self.getDecoder(), SETSTATE, savedState);

            /* The returned cookie corresponds to the last safe start point. */
            cookie.charsToSkip = decodedCharsUsed;
            return PTextIO.CookieType.build(cookie, factory());
        }

        static void fail(VirtualFrame frame, PTextIO self, Object savedState,
                        PyObjectCallMethodObjArgs callMethodSetState) {
            callMethodSetState.execute(frame, self.getDecoder(), SETSTATE, savedState);
        }

        Object[] decoderGetstate(VirtualFrame frame, PTextIO self, Object saved_state,
                        SequenceNodes.GetObjectArrayNode getArray,
                        PyObjectCallMethodObjArgs callMethodGetState,
                        PyObjectCallMethodObjArgs callMethodSetState) {
            Object state = callMethodGetState.execute(frame, self.getDecoder(), GETSTATE);
            if (!(state instanceof PTuple)) {
                fail(frame, self, saved_state, callMethodSetState);
                throw raise(TypeError, ILLEGAL_DECODER_STATE);
            }
            Object[] array = getArray.execute(state);
            if (array.length < 2) {
                fail(frame, self, saved_state, callMethodSetState);
                throw raise(TypeError, ILLEGAL_DECODER_STATE);
            }

            if (!(array[0] instanceof PBytes)) {
                fail(frame, self, saved_state, callMethodSetState);
                throw raise(TypeError, ILLEGAL_DECODER_STATE_THE_FIRST, array[0]);
            }
            return array;
        }

        static int decoderDecode(VirtualFrame frame, PTextIO self, PBytes start,
                        PyObjectCallMethodObjArgs callMethodDecode,
                        IONodes.ToStringNode toString) {
            Object decoded = callMethodDecode.execute(frame, self.getDecoder(), DECODE, start);
            return PString.length(toString.execute(decoded));
        }
    }

    @Builtin(name = TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "pos"})
    @ArgumentClinic(name = "pos", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class TruncateNode extends AttachedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "checkAttached(self)")
        static Object truncate(VirtualFrame frame, PTextIO self, Object pos,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodTruncate) {
            callMethodFlush.execute(frame, self, FLUSH);
            return callMethodTruncate.execute(frame, self.getBuffer(), TRUNCATE, pos);
        }
    }

    @Builtin(name = ENCODING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EncodingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(PTextIO self) {
            return self.getEncoding();
        }
    }

    @Builtin(name = BUFFER, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BufferNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object buffer(PTextIO self) {
            return self.getBuffer();
        }
    }

    @Builtin(name = LINE_BUFFERING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class LineBufferingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object lineBuffering(PTextIO self) {
            return self.isLineBuffering();
        }
    }

    @Builtin(name = WRITE_THROUGH, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class WriteThroughNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object writeThrough(PTextIO self) {
            return self.isWriteThrough();
        }
    }

    @Builtin(name = _FINALIZING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object finalizing(PTextIO self) {
            return self.isFinalizing();
        }
    }

    @Builtin(name = NAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object name(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getBuffer(), NAME);
        }
    }

    @Builtin(name = CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object closed(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectGetAttr lookupAttr) {
            return lookupAttr.execute(frame, self.getBuffer(), CLOSED);
        }
    }

    @Builtin(name = NEWLINES, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NewlinesNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"checkAttached(self)", "!self.hasDecoder()"})
        @SuppressWarnings("unused")
        static Object none(VirtualFrame frame, PTextIO self) {
            return PNone.NONE;
        }

        @Specialization(guards = {"checkAttached(self)", "self.hasDecoder()"})
        static Object doit(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getDecoder(), NEWLINES);
        }
    }

    @Builtin(name = ERRORS, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ErrorsNode extends InitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static Object doit(PTextIO self) {
            return self.getErrors();
        }
    }

    @Builtin(name = _CHUNK_SIZE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class ChunkSizeNode extends PythonBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isDetached()", "isNoValue(none)"})
        static Object none(PTextIO self, @SuppressWarnings("unused") PNone none) {
            return self.getChunkSize();
        }

        @Specialization(guards = {"self.isOK()", "!self.isDetached()", "!isNoValue(arg)"})
        Object chunkSize(VirtualFrame frame, PTextIO self, Object arg,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int size = asSizeNode.executeExact(frame, arg, ValueError);
            if (size <= 0) {
                throw raise(ValueError, A_STRICTLY_POSITIVE_INTEGER_IS_REQUIRED);
            }
            self.setChunkSize(size);
            return 0;
        }

        @Specialization(guards = "!self.isOK()")
        Object initError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object arg) {
            throw raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        Object attachError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object arg) {
            throw raise(ValueError, DETACHED_BUFFER);
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IternextNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)"})
        Object doit(VirtualFrame frame, PTextIO self,
                        @Cached TextIOWrapperNodes.ReadlineNode readlineNode) {
            self.setTelling(false);
            String line = readlineNode.execute(frame, self, -1);
            if (PString.length(line) == 0) {
                self.clearSnapshot();
                self.setTelling(self.isSeekable());
                throw raiseStopIteration();
            }
            return line;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends InitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.isOK()")
        Object doit(VirtualFrame frame, PTextIO self,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached("create(Repr)") LookupAndCallUnaryNode repr,
                        @Cached IONodes.ToStringNode toString,
                        @Cached IsBuiltinClassProfile isValueError) {
            if (!getContext().reprEnter(self)) {
                throw raise(RuntimeError, "reentrant call inside %p.__repr__", self);
            } else {
                try {
                    StringBuilder sb = PythonUtils.newStringBuilder();
                    PythonUtils.append(sb, "<_io.TextIOWrapper");
                    Object nameobj = PNone.NO_VALUE;
                    try {
                        nameobj = lookup.execute(frame, self, NAME);
                    } catch (PException e) {
                        e.expect(ValueError, isValueError);
                        /* Ignore ValueError raised if the underlying stream was detached */
                    }
                    if (!(nameobj instanceof PNone)) {
                        Object name = repr.executeObject(frame, nameobj);
                        PythonUtils.append(sb, PythonUtils.format(" name=%s", toString.execute(name)));
                    }
                    Object modeobj = lookup.execute(frame, self, MODE);
                    if (modeobj != PNone.NO_VALUE) {
                        PythonUtils.append(sb, PythonUtils.format(" mode='%s'", toString.execute(modeobj)));
                    }
                    PythonUtils.append(sb, PythonUtils.format(" encoding='%s'>", self.getEncoding()));
                    return PythonUtils.sbToString(sb);
                } finally {
                    getContext().reprLeave(self);
                }
            }
        }
    }
}
