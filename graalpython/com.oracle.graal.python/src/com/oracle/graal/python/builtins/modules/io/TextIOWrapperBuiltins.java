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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PTextIOWrapper;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_BUFFER;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ENCODING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ERRORS;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_LINE_BUFFERING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_RECONFIGURE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE_THROUGH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__CHUNK_SIZE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J__FINALIZING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_DECODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_ENCODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FILENO;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_MODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NAME;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T__DEALLOC_WARN;
import static com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.setNewline;
import static com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.validateNewline;
import static com.oracle.graal.python.nodes.ErrorMessages.A_STRICTLY_POSITIVE_INTEGER_IS_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_DELETE;
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
import static com.oracle.graal.python.nodes.ErrorMessages.REENTRANT_CALL_INSIDE_P_REPR;
import static com.oracle.graal.python.nodes.ErrorMessages.TELLING_POSITION_DISABLED_BY_NEXT_CALL;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_READ_SHOULD_HAVE_RETURNED_A_BYTES_OBJECT_NOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.UNDERLYING_STREAM_IS_NOT_SEEKABLE;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.PGuards.isPNone;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.WriteFlushNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringReplaceNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyErrChainExceptions;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PTextIOWrapper)
public final class TextIOWrapperBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TextIOWrapperBuiltinsFactory.getFactories();
    }

    abstract static class InitCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isOK()")
        @SuppressWarnings("unused")
        static Object initError(PTextIO self,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class AttachedCheckPythonUnaryBuiltinNode extends InitCheckPythonUnaryBuiltinNode {
        protected static boolean checkAttached(PTextIO self) {
            return self.isOK() && !self.isDetached();
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        @SuppressWarnings("unused")
        static Object attachError(PTextIO self,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, DETACHED_BUFFER);
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
        static Object initError(PTextIO self, Object o,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_UNINIT);
        }
    }

    abstract static class AttachedCheckPythonBinaryClinicBuiltinNode extends InitCheckPythonBinaryClinicBuiltinNode {
        protected static boolean checkAttached(PTextIO self) {
            return self.isOK() && !self.isDetached();
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        @SuppressWarnings("unused")
        static Object attachError(PTextIO self, Object o,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, DETACHED_BUFFER);
        }
    }

    abstract static class ClosedCheckPythonBinaryClinicBuiltinNode extends AttachedCheckPythonBinaryClinicBuiltinNode {
        @Child private TextIOWrapperNodes.CheckClosedNode checkClosedNode = TextIOWrapperNodesFactory.CheckClosedNodeGen.create();

        protected boolean isOpen(VirtualFrame frame, PTextIO self) {
            checkClosedNode.execute(frame, self);
            return true;
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "encoding", "errors", "newline", "line_buffering", "write_through"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "line_buffering", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @ArgumentClinic(name = "write_through", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object init(VirtualFrame frame, PTextIO self, Object buffer, Object encodingArg,
                        TruffleString errors, Object newlineArg, boolean lineBuffering, boolean writeThrough,
                        @Cached TextIOWrapperNodes.TextIOWrapperInitNode initNode) {
            initNode.execute(frame, this, self, buffer, encodingArg, errors, newlineArg, lineBuffering, writeThrough);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object detach(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            callMethod.execute(frame, inliningTarget, self, T_FLUSH);
            Object buffer = self.getBuffer();
            self.setBuffer(null);
            self.setDetached(true);
            return buffer;
        }
    }

    @Builtin(name = J_RECONFIGURE, minNumOfPositionalArgs = 1, keywordOnlyNames = {"encoding", "errors", "newline", "line_buffering", "write_through"})
    @GenerateNodeFactory
    abstract static class ReconfigureNode extends PythonBuiltinNode {

        protected static boolean isValid(PTextIO self, Object encodingObj, Object errorsObj, Object newlineObj) {
            if (self.hasDecodedChars()) {
                return isPNone(encodingObj) && isPNone(errorsObj) && isNoValue(newlineObj);
            }
            return true;
        }

        @Specialization(guards = "isValid(self, encodingObj, errorsObj, newlineObj)")
        static Object reconfigure(VirtualFrame frame, PTextIO self, Object encodingObj,
                        Object errorsObj, Object newlineObj,
                        Object lineBufferingObj, Object writeThroughObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy lazyRaiseNode,
                        @Cached IONodes.ToTruffleStringNode toStringNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TextIOWrapperNodes.ChangeEncodingNode changeEncodingNode) {
            TruffleString newline = null;
            if (!isPNone(newlineObj)) {
                newline = toStringNode.execute(inliningTarget, newlineObj);
                validateNewline(newline, inliningTarget, lazyRaiseNode, codePointLengthNode, codePointAtIndexNode);
            }

            boolean lineBuffering, writeThrough;
            if (isPNone(lineBufferingObj)) {
                lineBuffering = self.isLineBuffering();
            } else {
                lineBuffering = isTrueNode.execute(frame, inliningTarget, lineBufferingObj);
            }
            if (isPNone(writeThroughObj)) {
                writeThrough = self.isWriteThrough();
            } else {
                writeThrough = isTrueNode.execute(frame, inliningTarget, writeThroughObj);
            }
            callMethod.execute(frame, inliningTarget, self, T_FLUSH);
            self.setB2cratio(0);
            if (!isNoValue(newlineObj)) {
                setNewline(self, newline, equalNode);
            }

            changeEncodingNode.execute(frame, inliningTarget, self, encodingObj, errorsObj, !isNoValue(newlineObj));
            self.setLineBuffering(lineBuffering);
            self.setWriteThrough(writeThrough);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isValid(self, encodingObj, errorsObj, newlineObj)")
        static Object error(VirtualFrame frame, PTextIO self, Object encodingObj, Object errorsObj, Object newlineObj, Object lineBufferingObj, Object writeThroughObj,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, NOT_POSSIBLE_TO_SET_THE_ENCODING_OR);
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "str"})
    @ArgumentClinic(name = "str", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class WriteNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "!self.hasEncoder()"})
        static Object write(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") TruffleString data,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, NOT_WRITABLE);
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "self.hasEncoder()"})
        static Object write(VirtualFrame frame, PTextIO self, TruffleString data,
                        @Bind("this") Node inliningTarget,
                        @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached TextIOWrapperNodes.DecoderResetNode decoderResetNode,
                        @Cached PyObjectCallMethodObjArgs callMethodEncode,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached StringReplaceNode replaceNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PRaiseNode.Lazy raiseNode) {
            boolean haslf = false;
            boolean needflush = false;
            TruffleString text = data;
            if ((self.isWriteTranslate() && self.hasWriteNewline()) || self.isLineBuffering()) {
                if (indexOfCodePointNode.execute(text, '\n', 0, codePointLengthNode.execute(text, TS_ENCODING), TS_ENCODING) >= 0) {
                    haslf = true;
                }
            }

            if (haslf && self.isWriteTranslate() && self.hasWriteNewline()) {
                text = replaceNode.execute(text, T_NEWLINE, self.getWriteNewline(), -1);
            }

            if (self.isLineBuffering() && (haslf || indexOfCodePointNode.execute(text, '\r', 0, codePointLengthNode.execute(text, TS_ENCODING), TS_ENCODING) >= 0)) {
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
            Object b = callMethodEncode.execute(frame, inliningTarget, self.getEncoder(), T_ENCODE, text);

            if (b != text && !(b instanceof PBytes)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ENCODER_SHOULD_RETURN_A_BYTES_OBJECT_NOT_P, b);
            }

            byte[] encodedText = bufferLib.getInternalOrCopiedByteArray(b);
            int bytesLen = bufferLib.getBufferLength(b);

            if (self.getPendingBytesCount() + bytesLen > self.getChunkSize()) {
                // Prevent to concatenate more than chunk_size data.
                writeFlushNode.execute(frame, inliningTarget, self);
            }

            self.appendPendingBytes(encodedText, bytesLen);
            if (self.getPendingBytesCount() >= self.getChunkSize() || needflush || self.isWriteThrough()) {
                writeFlushNode.execute(frame, inliningTarget, self);
            }

            if (needflush) {
                callMethodFlush.execute(frame, inliningTarget, self.getBuffer(), T_FLUSH);
            }

            self.clearDecodedChars();
            self.clearSnapshot();
            if (self.hasDecoder()) {
                decoderResetNode.execute(frame, inliningTarget, self);
            }

            return codePointLengthNode.execute(data, TS_ENCODING);
        }
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "self.hasDecoder()", "n < 0"})
        static TruffleString readAll(VirtualFrame frame, PTextIO self, @SuppressWarnings("unused") int n,
                        @Bind("this") Node inliningTarget,
                        @Cached TextIOWrapperNodes.DecodeNode decodeNode,
                        @Exclusive @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Shared @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.ConcatNode concatNode) {
            writeFlushNode.execute(frame, inliningTarget, self);

            /* Read everything */
            Object bytes = callMethod.execute(frame, inliningTarget, self.getBuffer(), T_READ);
            TruffleString decoded = decodeNode.execute(frame, self.getDecoder(), bytes, true);
            TruffleString result = self.consumeAllDecodedChars(substringNode, !decoded.isEmpty());
            result = concatNode.execute(result, decoded, TS_ENCODING, false);
            self.clearDecodedChars();
            self.clearSnapshot();
            return result;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "self.hasDecoder()", "n >= 0"})
        static TruffleString read(VirtualFrame frame, PTextIO self, int n,
                        @Bind("this") Node inliningTarget,
                        @Cached TextIOWrapperNodes.ReadChunkNode readChunkNode,
                        @Exclusive @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            writeFlushNode.execute(frame, inliningTarget, self);
            TruffleString result = self.consumeDecodedChars(n, substringNode, false);
            int remaining = n - codePointLengthNode.execute(result, TS_ENCODING);
            TruffleStringBuilder chunks = null;
            /* Keep reading chunks until we have n characters to return */
            while (remaining > 0) {
                boolean res = readChunkNode.execute(frame, inliningTarget, self, remaining);
                // TODO: _PyIO_trap_eintr()
                if (!res) /* EOF */ {
                    break;
                }
                if (!result.isEmpty()) {
                    if (chunks == null) {
                        chunks = TruffleStringBuilder.create(TS_ENCODING);
                    }
                    appendStringNode.execute(chunks, result);
                }

                result = self.consumeDecodedChars(remaining, substringNode, chunks != null);
                remaining -= codePointLengthNode.execute(result, TS_ENCODING);
            }
            if (chunks != null) {
                appendStringNode.execute(chunks, result);
                return toStringNode.execute(chunks);
            }
            return result;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)", "!self.hasDecoder()"})
        static Object noDecoder(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") int n,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, NOT_READABLE);
        }
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)"})
        static TruffleString readline(VirtualFrame frame, PTextIO self, int limit,
                        @Cached TextIOWrapperNodes.ReadlineNode readlineNode) {
            return readlineNode.execute(frame, self, limit);
        }
    }

    @Builtin(name = J_FLUSH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FlushNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)"})
        static Object flush(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            self.setTelling(self.isSeekable());
            writeFlushNode.execute(frame, inliningTarget, self);
            return callMethod.execute(frame, inliningTarget, self.getBuffer(), T_FLUSH);
        }
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object close(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached ClosedNode closedNode,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodDeallocWarn,
                        @Cached PyObjectCallMethodObjArgs callMethodClose,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyErrChainExceptions chainExceptions) {
            Object res = closedNode.execute(frame, self);
            if (isTrueNode.execute(frame, inliningTarget, res)) {
                return PNone.NONE;
            } else {
                if (self.isFinalizing()) {
                    callMethodDeallocWarn.execute(frame, inliningTarget, self.getBuffer(), T__DEALLOC_WARN);
                }

                try {
                    callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);
                } catch (PException e) {
                    try {
                        callMethodClose.execute(frame, inliningTarget, self.getBuffer(), T_CLOSE);
                        throw e;
                    } catch (PException ee) {
                        throw chainExceptions.execute(inliningTarget, ee, e);
                    }
                }
                return callMethodClose.execute(frame, inliningTarget, self.getBuffer(), T_CLOSE);
            }
        }
    }

    @Builtin(name = J_FILENO, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FilenoNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object fileno(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getBuffer(), T_FILENO);
        }
    }

    @Builtin(name = J_SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object seekable(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getBuffer(), T_SEEKABLE);
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object readable(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getBuffer(), T_READABLE);
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class WritableNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object writable(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getBuffer(), T_WRITABLE);
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAttyNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object isatty(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getBuffer(), T_ISATTY);
        }
    }

    static void encoderSetState(VirtualFrame frame, Node inliningTarget, PTextIO self, PTextIO.CookieType cookie,
                    TextIOWrapperNodes.EncoderResetNode encoderResetNode) {
        encoderResetNode.execute(frame, inliningTarget, self, cookie.startPos == 0 && cookie.decFlags == 0);
    }

    @Builtin(name = J_SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "cookie", "whence"})
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "checkAttached(self)")
        static Object seek(VirtualFrame frame, PTextIO self, Object c, int whence,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile overflow,
                        @Cached CastToJavaLongLossyNode toLong,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached TextIOWrapperNodes.DecoderSetStateNode decoderSetStateNode,
                        @Cached TextIOWrapperNodes.DecoderResetNode decoderResetNode,
                        @Cached TextIOWrapperNodes.EncoderResetNode encoderResetNode,
                        @Cached TextIOWrapperNodes.CheckClosedNode checkClosedNode,
                        @Cached TextIOWrapperNodes.DecodeNode decodeNode,
                        @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodSeek,
                        @Cached PyObjectCallMethodObjArgs callMethodRead,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            checkClosedNode.execute(frame, self);
            if (!self.isSeekable()) {
                throw raiseNode.get(inliningTarget).raise(IOUnsupportedOperation, UNDERLYING_STREAM_IS_NOT_SEEKABLE);
            }

            Object cookieObj = c;

            switch (whence) {
                case SEEK_CUR:
                    /* seek relative to current position */
                    if (!eqNode.compare(frame, inliningTarget, cookieObj, 0)) {
                        throw raiseNode.get(inliningTarget).raise(IOUnsupportedOperation, CAN_T_DO_NONZERO_CUR_RELATIVE_SEEKS);
                    }

                    /*
                     * Seeking to the current position should attempt to sync the underlying buffer
                     * with the current position.
                     */
                    cookieObj = callMethodTell.execute(frame, inliningTarget, self, T_TELL);
                    break;

                case SEEK_END:
                    /* seek relative to end of file */
                    if (!eqNode.compare(frame, inliningTarget, cookieObj, 0)) {
                        throw raiseNode.get(inliningTarget).raise(IOUnsupportedOperation, CAN_T_DO_NONZERO_END_RELATIVE_SEEKS);
                    }

                    callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);

                    self.clearDecodedChars();
                    self.clearSnapshot();
                    if (self.hasDecoder()) {
                        decoderResetNode.execute(frame, inliningTarget, self);
                    }

                    Object res = callMethodSeek.execute(frame, inliningTarget, self.getBuffer(), T_SEEK, 0, 2);
                    if (self.hasEncoder()) {
                        /* If seek() == 0, we are at the start of stream, otherwise not */
                        encoderResetNode.execute(frame, inliningTarget, self, eqNode.compare(frame, inliningTarget, res, 0));
                    }
                    return res;

                case SEEK_SET:
                    break;

                default:
                    throw raiseNode.get(inliningTarget).raise(ValueError, INVALID_WHENCE_D_SHOULD_BE_D_D_OR_D, whence, SEEK_SET, SEEK_CUR, SEEK_END);
            }

            Object cookieLong = indexNode.execute(frame, inliningTarget, cookieObj);
            PTextIO.CookieType cookie;
            if (cookieLong instanceof PInt) {
                if (((PInt) cookieLong).isNegative()) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, NEGATIVE_SEEK_POSITION_D, cookieLong);
                }
                cookie = PTextIO.CookieType.parse((PInt) cookieLong, inliningTarget, overflow, raiseNode);
            } else {
                long l = toLong.execute(inliningTarget, cookieLong);
                if (l < 0) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, NEGATIVE_SEEK_POSITION_D, cookieLong);
                }
                cookie = PTextIO.CookieType.parse(l, inliningTarget, overflow, raiseNode);
            }

            callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);

            /*
             * The strategy of seek() is to go back to the safe start point and replay the effect of
             * read(chars_to_skip) from there.
             */

            /* Seek back to the safe start point. */
            callMethodSeek.execute(frame, inliningTarget, self.getBuffer(), T_SEEK, cookie.startPos);

            self.clearDecodedChars();
            self.clearSnapshot();

            /* Restore the decoder to its state from the safe start point. */
            decoderSetStateNode.execute(frame, inliningTarget, self, cookie, factory);

            if (cookie.charsToSkip != 0) {
                /* Just like _read_chunk, feed the decoder and save a snapshot. */
                Object inputChunk = callMethodRead.execute(frame, inliningTarget, self.getBuffer(), T_READ, cookie.bytesToFeed);

                if (!(inputChunk instanceof PBytes)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, UNDERLYING_READ_SHOULD_HAVE_RETURNED_A_BYTES_OBJECT_NOT_S, inputChunk);
                }

                self.setSnapshotDecFlags(cookie.decFlags);
                // TODO avoid copy?
                self.setSnapshotNextInput(bufferLib.getCopiedByteArray(inputChunk));

                TruffleString decoded = decodeNode.execute(frame, self.getDecoder(), inputChunk, cookie.needEOF != 0);
                int decodedLen = self.setDecodedChars(decoded, codePointLengthNode);

                /* Skip chars_to_skip of the decoded characters. */
                if (decodedLen < cookie.charsToSkip) {
                    throw raiseNode.get(inliningTarget).raise(OSError, CAN_T_RESTORE_LOGICAL_FILE_POSITION);
                }
                self.incDecodedCharsUsed(cookie.charsToSkip);
            } else {
                self.setSnapshotDecFlags(cookie.decFlags);
                self.setSnapshotNextInput(PythonUtils.EMPTY_BYTE_ARRAY);
            }

            /* Finally, reset the encoder (merely useful for proper BOM handling) */
            if (self.hasEncoder()) {
                encoderSetState(frame, inliningTarget, self, cookie, encoderResetNode);
            }
            return cookieObj;
        }

        protected static boolean checkAttached(PTextIO self) {
            return self.isOK() && !self.isDetached();
        }

        @Specialization(guards = "!self.isOK()")
        static Object initError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object o1, @SuppressWarnings("unused") Object o2,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        static Object attachError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object o1, @SuppressWarnings("unused") Object o2,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, DETACHED_BUFFER);
        }
    }

    @Builtin(name = J_TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "!self.isSeekable() || !self.isTelling()")
        static Object error(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PTextIO self,
                        @Cached PRaiseNode raiseNode) {
            if (!self.isSeekable()) {
                throw raiseNode.raise(IOUnsupportedOperation, UNDERLYING_STREAM_IS_NOT_SEEKABLE);
            } else {
                throw raiseNode.raise(OSError, TELLING_POSITION_DISABLED_BY_NEXT_CALL);
            }
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
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodTell) {
            writeFlushNode.execute(frame, inliningTarget, self);
            callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);
            return callMethodTell.execute(frame, inliningTarget, self.getBuffer(), T_TELL);
        }

        protected static boolean hasUsedDecodedChar(PTextIO self) {
            /* How many decoded characters have been used up since the snapshot? */
            return self.getDecodedCharsUsed() > 0;
        }

        private static PTextIO.CookieType getCookie(VirtualFrame frame, Node inliningTarget, PTextIO self,
                        WriteFlushNode writeFlushNode,
                        PyObjectCallMethodObjArgs callMethodFlush,
                        PyObjectCallMethodObjArgs callMethodTell,
                        PyLongAsLongNode asLongNode) {
            Object posobj = getPos(frame, self, inliningTarget, writeFlushNode, callMethodFlush, callMethodTell);
            PTextIO.CookieType cookie = new PTextIO.CookieType();
            cookie.startPos = asLongNode.execute(frame, inliningTarget, posobj);
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
        static Object didntMove(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Exclusive @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PTextIO.CookieType cookie = getCookie(frame, inliningTarget, self, writeFlushNode, callMethodFlush, callMethodTell, asLongNode);
            /* We haven't moved from the snapshot point. */
            return PTextIO.CookieType.build(cookie, factory);
        }

        @Specialization(guards = {
                        "checkAttached(self)", //
                        "isOpen(frame, self)", //
                        "self.isSeekable()", //
                        "self.isTelling()", //
                        "hasDecoderAndSnapshot(self)", //
                        "hasUsedDecodedChar(self)" //
        })
        static Object tell(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached TextIOWrapperNodes.WriteFlushNode writeFlushNode,
                        @Cached TextIOWrapperNodes.DecoderSetStateNode decoderSetStateNode,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached IONodes.ToTruffleStringNode toString,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodTell,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodDecode,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodGetState,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callMethodSetState,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Exclusive @Cached PyLongAsLongNode asLongNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @CachedLibrary(limit = "2") InteropLibrary isString,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PTextIO.CookieType cookie = getCookie(frame, inliningTarget, self, writeFlushNode, callMethodFlush, callMethodTell, asLongNode);
            byte[] snapshotNextInput = self.getSnapshotNextInput();
            int nextInputLength = self.getSnapshotNextInput().length;
            int decodedCharsUsed = self.getDecodedCharsUsed();

            /* Decoder state will be restored at the end */
            Object savedState = callMethodGetState.execute(frame, inliningTarget, self.getDecoder(), T_GETSTATE);
            /* Fast search for an acceptable start point, close to our current pos */
            int skipBytes = (int) (self.getB2cratio() * decodedCharsUsed);
            int skipBack = 1;
            assert (skipBack <= nextInputLength);
            while (skipBytes > 0) {
                /* Decode up to temptative start point */
                decoderSetStateNode.execute(frame, inliningTarget, self, cookie, factory);
                PBytes in = factory.createBytes(snapshotNextInput, skipBytes);
                int charsDecoded = decoderDecode(frame, inliningTarget, self, in, callMethodDecode, toString, codePointLengthNode);
                if (charsDecoded <= decodedCharsUsed) {
                    Object[] state = decoderGetstate(frame, inliningTarget, self, savedState, getObjectArrayNode, callMethodGetState, callMethodSetState, raiseNode);
                    int decFlags = asSizeNode.executeExact(frame, inliningTarget, state[1]);
                    int decBufferLen = sizeNode.execute(frame, inliningTarget, state[0]);
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
                decoderSetStateNode.execute(frame, inliningTarget, self, cookie, factory);
            }

            /* Note our initial start point. */
            cookie.startPos += skipBytes;
            cookie.charsToSkip = decodedCharsUsed;
            if (decodedCharsUsed == 0) {
                callMethodSetState.execute(frame, inliningTarget, self.getDecoder(), T_SETSTATE, savedState);

                /* The returned cookie corresponds to the last safe start point. */
                cookie.charsToSkip = decodedCharsUsed;
                return PTextIO.CookieType.build(cookie, factory);
            }

            int charsDecoded = 0;
            byte[] input = PythonUtils.arrayCopyOfRange(snapshotNextInput, skipBytes, nextInputLength);
            while (input.length > 0) {
                PBytes start = factory.createBytes(input, 1);
                int n = decoderDecode(frame, inliningTarget, self, start, callMethodDecode, toString, codePointLengthNode);
                /* We got n chars for 1 byte */
                charsDecoded += n;
                cookie.bytesToFeed += 1;
                Object[] state = decoderGetstate(frame, inliningTarget, self, savedState, getObjectArrayNode, callMethodGetState, callMethodSetState, raiseNode);
                int decFlags = asSizeNode.executeExact(frame, inliningTarget, state[1]);
                int decBufferLen = sizeNode.execute(frame, inliningTarget, state[0]);

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
                Object decoded = callMethodDecode.execute(frame, inliningTarget, self.getDecoder(), T_DECODE, T_EMPTY_STRING,
                                /* final = */ true);

                if (!isString.isString(decoded)) {
                    fail(frame, inliningTarget, self, savedState, callMethodSetState);
                    throw raiseNode.get(inliningTarget).raise(TypeError, DECODER_SHOULD_RETURN_A_STRING_RESULT_NOT_P, decoded);
                }

                charsDecoded += sizeNode.execute(frame, inliningTarget, decoded);
                cookie.needEOF = 1;

                if (charsDecoded < decodedCharsUsed) {
                    fail(frame, inliningTarget, self, savedState, callMethodSetState);
                    throw raiseNode.get(inliningTarget).raise(OSError, CAN_T_RECONSTRUCT_LOGICAL_FILE_POSITION);
                }
            }
            callMethodSetState.execute(frame, inliningTarget, self.getDecoder(), T_SETSTATE, savedState);

            /* The returned cookie corresponds to the last safe start point. */
            cookie.charsToSkip = decodedCharsUsed;
            return PTextIO.CookieType.build(cookie, factory);
        }

        static void fail(VirtualFrame frame, Node inliningTarget, PTextIO self, Object savedState,
                        PyObjectCallMethodObjArgs callMethodSetState) {
            callMethodSetState.execute(frame, inliningTarget, self.getDecoder(), T_SETSTATE, savedState);
        }

        static Object[] decoderGetstate(VirtualFrame frame, Node inliningTarget, PTextIO self, Object saved_state,
                        SequenceNodes.GetObjectArrayNode getArray,
                        PyObjectCallMethodObjArgs callMethodGetState,
                        PyObjectCallMethodObjArgs callMethodSetState,
                        PRaiseNode.Lazy raiseNode) {
            Object state = callMethodGetState.execute(frame, inliningTarget, self.getDecoder(), T_GETSTATE);
            if (!(state instanceof PTuple)) {
                fail(frame, inliningTarget, self, saved_state, callMethodSetState);
                throw raiseNode.get(inliningTarget).raise(TypeError, ILLEGAL_DECODER_STATE);
            }
            Object[] array = getArray.execute(inliningTarget, state);
            if (array.length < 2) {
                fail(frame, inliningTarget, self, saved_state, callMethodSetState);
                throw raiseNode.get(inliningTarget).raise(TypeError, ILLEGAL_DECODER_STATE);
            }

            if (!(array[0] instanceof PBytes)) {
                fail(frame, inliningTarget, self, saved_state, callMethodSetState);
                throw raiseNode.get(inliningTarget).raise(TypeError, ILLEGAL_DECODER_STATE_THE_FIRST, array[0]);
            }
            return array;
        }

        static int decoderDecode(VirtualFrame frame, Node inliningTarget, PTextIO self, PBytes start,
                        PyObjectCallMethodObjArgs callMethodDecode,
                        IONodes.ToTruffleStringNode toString,
                        TruffleString.CodePointLengthNode codePointLengthNode) {
            Object decoded = callMethodDecode.execute(frame, inliningTarget, self.getDecoder(), T_DECODE, start);
            return codePointLengthNode.execute(toString.execute(inliningTarget, decoded), TS_ENCODING);
        }
    }

    @Builtin(name = J_TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "pos"})
    @ArgumentClinic(name = "pos", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class TruncateNode extends AttachedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TextIOWrapperBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "checkAttached(self)")
        static Object truncate(VirtualFrame frame, PTextIO self, Object pos,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodFlush,
                        @Cached PyObjectCallMethodObjArgs callMethodTruncate) {
            callMethodFlush.execute(frame, inliningTarget, self, T_FLUSH);
            return callMethodTruncate.execute(frame, inliningTarget, self.getBuffer(), T_TRUNCATE, pos);
        }
    }

    @Builtin(name = J_ENCODING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EncodingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString doit(PTextIO self) {
            return self.getEncoding();
        }
    }

    @Builtin(name = J_BUFFER, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class BufferNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object buffer(PTextIO self) {
            return self.getBuffer();
        }
    }

    @Builtin(name = J_LINE_BUFFERING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class LineBufferingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object lineBuffering(PTextIO self) {
            return self.isLineBuffering();
        }
    }

    @Builtin(name = J_WRITE_THROUGH, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class WriteThroughNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object writeThrough(PTextIO self) {
            return self.isWriteThrough();
        }
    }

    @Builtin(name = J__FINALIZING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FinalizingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object finalizing(PTextIO self) {
            return self.isFinalizing();
        }
    }

    @Builtin(name = J_NAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object name(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getBuffer(), T_NAME);
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "checkAttached(self)")
        static Object closed(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr lookupAttr) {
            return lookupAttr.execute(frame, inliningTarget, self.getBuffer(), T_CLOSED);
        }
    }

    @Builtin(name = J_NEWLINES, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NewlinesNode extends AttachedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"checkAttached(self)", "!self.hasDecoder()"})
        @SuppressWarnings("unused")
        static Object none(VirtualFrame frame, PTextIO self) {
            return PNone.NONE;
        }

        @Specialization(guards = {"checkAttached(self)", "self.hasDecoder()"})
        static Object doit(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, inliningTarget, self.getDecoder(), T_NEWLINES);
        }
    }

    @Builtin(name = J_ERRORS, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ErrorsNode extends InitCheckPythonUnaryBuiltinNode {
        @Specialization(guards = "self.isOK()")
        static TruffleString doit(PTextIO self) {
            return self.getErrors();
        }
    }

    @Builtin(name = J__CHUNK_SIZE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class ChunkSizeNode extends PythonBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isDetached()", "isNoValue(none)"})
        static Object none(PTextIO self, @SuppressWarnings("unused") PNone none) {
            return self.getChunkSize();
        }

        @Specialization(guards = {"self.isOK()", "!self.isDetached()", "!isNoValue(arg)", "!isDeleteMarker(arg)"})
        static Object chunkSize(VirtualFrame frame, PTextIO self, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int size = asSizeNode.executeExact(frame, inliningTarget, arg, ValueError);
            if (size <= 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, A_STRICTLY_POSITIVE_INTEGER_IS_REQUIRED);
            }
            self.setChunkSize(size);
            return 0;
        }

        @Specialization(guards = {"self.isOK()", "!self.isDetached()"})
        static Object noDelete(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") DescriptorDeleteMarker marker,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(AttributeError, CANNOT_DELETE);
        }

        @Specialization(guards = "!self.isOK()")
        static Object initError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object arg,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = {"self.isOK()", "self.isDetached()"})
        static Object attachError(@SuppressWarnings("unused") PTextIO self, @SuppressWarnings("unused") Object arg,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, DETACHED_BUFFER);
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IternextNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"checkAttached(self)", "isOpen(frame, self)"})
        static TruffleString doit(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached TextIOWrapperNodes.ReadlineNode readlineNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            self.setTelling(false);
            TruffleString line = readlineNode.execute(frame, self, -1);
            if (line.isEmpty()) {
                self.clearSnapshot();
                self.setTelling(self.isSeekable());
                throw raiseNode.get(inliningTarget).raiseStopIteration();
            }
            return line;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends InitCheckPythonUnaryBuiltinNode {

        @Specialization(guards = "self.isOK()")
        static Object doit(VirtualFrame frame, PTextIO self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached("create(Repr)") LookupAndCallUnaryNode repr,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode,
                        @Cached IONodes.ToTruffleStringNode toString,
                        @Cached IsBuiltinObjectProfile isValueError,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!PythonContext.get(inliningTarget).reprEnter(self)) {
                throw raiseNode.get(inliningTarget).raise(RuntimeError, REENTRANT_CALL_INSIDE_P_REPR, self);
            }
            try {
                Object nameobj = PNone.NO_VALUE;
                try {
                    nameobj = lookup.execute(frame, inliningTarget, self, T_NAME);
                } catch (PException e) {
                    e.expect(inliningTarget, ValueError, isValueError);
                    /* Ignore ValueError raised if the underlying stream was detached */
                }
                Object modeobj = lookup.execute(frame, inliningTarget, self, T_MODE);
                if (nameobj instanceof PNone) {
                    if (modeobj == PNone.NO_VALUE) {
                        return simpleTruffleStringFormatNode.format("<_io.TextIOWrapper encoding='%s'>", self.getEncoding());
                    }
                    return simpleTruffleStringFormatNode.format("<_io.TextIOWrapper mode='%s' encoding='%s'>", toString.execute(inliningTarget, modeobj), self.getEncoding());
                }
                Object name = repr.executeObject(frame, nameobj);
                if (modeobj == PNone.NO_VALUE) {
                    return simpleTruffleStringFormatNode.format("<_io.TextIOWrapper name=%s encoding='%s'>", toString.execute(inliningTarget, name), self.getEncoding());
                }
                return simpleTruffleStringFormatNode.format("<_io.TextIOWrapper name=%s mode='%s' encoding='%s'>", toString.execute(inliningTarget, name), toString.execute(inliningTarget, modeobj),
                                self.getEncoding());
            } finally {
                PythonContext.get(inliningTarget).reprLeave(self);
            }
        }
    }
}
