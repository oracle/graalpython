/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteStreamReader;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.internalErrorCallback;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalDecoderBuiltins.DecodeNode.decoderAppendPending;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalDecoderBuiltins.DecodeNode.decoderFeedBuffer;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBERR_TOOFEW;
import static com.oracle.graal.python.nodes.ErrorMessages.CODEC_IS_UNEXPECTED_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.STREAM_FUNCTION_RETURNED_A_NON_BYTES_OBJECT_P;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = MultibyteStreamReader)
public final class MultibyteStreamReaderBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = MultibyteStreamReaderBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultibyteStreamReaderBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 2, parameterNames = {"$cls", "stream", "errors"})
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object mbstreamreaderNew(VirtualFrame frame, Object type, Object stream, Object err,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached TruffleString.EqualNode isEqual,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) { // "O|s:StreamReader"

            TruffleString errors = null;
            if (err != PNone.NO_VALUE) {
                errors = castToStringNode.execute(inliningTarget, err);
            }

            MultibyteStreamReaderObject self = PFactory.createMultibyteStreamReaderObject(language, type, getInstanceShape.execute(type));
            Object codec = getAttr.execute(frame, inliningTarget, type, StringLiterals.T_CODEC);
            if (!(codec instanceof MultibyteCodecObject)) {
                throw raiseNode.raise(inliningTarget, TypeError, CODEC_IS_UNEXPECTED_TYPE);
            }

            self.codec = ((MultibyteCodecObject) codec).codec;
            self.stream = stream;
            self.pendingsize = 0;
            self.errors = internalErrorCallback(errors, isEqual);
            self.state = self.codec.decinit(self.errors);
            return self;
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "MultibyteStreamReader", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonUnaryBuiltinNode {

        @Specialization
        PNone init(@SuppressWarnings("unused") MultibyteStreamReaderObject self) {
            return PNone.NONE;
        }
    }

    @GenerateInline(false)
    abstract static class IReadNode extends PNodeWithContext {

        abstract TruffleString execute(VirtualFrame frame, MultibyteStreamReaderObject self, TruffleString method, long sizehint);

        // mbstreamreader_iread
        @Specialization
        static TruffleString iread(VirtualFrame frame, MultibyteStreamReaderObject self, TruffleString method, long sizehint,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PyBytesCheckNode bytesCheckNode,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached MultibyteCodecUtil.DecodeErrorNode decodeErrorNode,
                        @Cached PRaiseNode raiseNode) {

            if (sizehint == 0) {
                return T_EMPTY_STRING;
            }
            MultibyteDecodeBuffer buf = null;
            for (;;) {
                Object cres;
                if (sizehint < 0) {
                    cres = callMethod.execute(frame, inliningTarget, self.stream, method);
                } else {
                    cres = callMethod.execute(frame, inliningTarget, self.stream, method, sizehint);
                }

                if (!(cres instanceof PBytes)) {
                    if (!bytesCheckNode.execute(inliningTarget, cres)) {
                        throw raiseNode.raise(inliningTarget, TypeError, STREAM_FUNCTION_RETURNED_A_NON_BYTES_OBJECT_P, cres);
                    }
                }

                byte[] cresBytes = toBytesNode.execute(frame, cres);
                int cresBytesLen = cresBytes.length;
                boolean endoffile = cresBytesLen == 0;

                int rsize = cresBytesLen;
                if (self.pendingsize > 0) {
                    // (mq): this cannot happen
                    // if (cresBytesLen > MAXSIZE - self.pendingsize) {
                    // throw raise(MemoryError);
                    // }

                    rsize += self.pendingsize;
                    byte[] ctr = new byte[rsize];
                    PythonUtils.arraycopy(self.pending, 0, ctr, 0, self.pendingsize);
                    PythonUtils.arraycopy(cresBytes, 0, ctr, self.pendingsize, cresBytesLen);
                    cresBytes = ctr;
                    self.pendingsize = 0;
                }
                if (buf == null) {
                    buf = new MultibyteDecodeBuffer(cresBytes);
                } else {
                    buf.replaceInbuf(cresBytes);
                }

                if (rsize > 0) {
                    decoderFeedBuffer(frame, self, buf, decodeErrorNode, inliningTarget);
                }

                if (endoffile || sizehint < 0) {
                    if (!buf.isFull()) {
                        decodeErrorNode.execute(frame, self.codec,
                                        buf, self.errors, MBERR_TOOFEW);
                    }
                }

                if (!buf.isFull()) { /* pending sequence exists */
                    decoderAppendPending(inliningTarget, self, buf, raiseNode);
                }

                if (sizehint < 0 || buf.getOutpos() != 0 || rsize == 0) {
                    break;
                }

                sizehint = 1; /* read 1 more byte and retry */
            }

            return buf.toTString();
        }

    }

    @Builtin(name = "read", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sizeobj"}, doc = "read($self, sizeobj=None, /)\n--\n\n")
    @ArgumentClinic(name = "sizeobj", conversion = ArgumentClinic.ClinicConversion.Long, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteStreamReaderBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        // _multibytecodec_MultibyteStreamReader_read_impl
        @Specialization
        static Object read(VirtualFrame frame, MultibyteStreamReaderObject self, long size,
                        @Cached IReadNode iReadNode) {
            return iReadNode.execute(frame, self, StringLiterals.T_READ, size);
        }
    }

    @Builtin(name = "readline", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sizeobj"}, doc = "readline($self, sizeobj=None, /)\n--\n\n")
    @ArgumentClinic(name = "sizeobj", conversion = ArgumentClinic.ClinicConversion.Long, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteStreamReaderBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        // _multibytecodec_MultibyteStreamReader_readline_impl
        @Specialization
        static Object readline(VirtualFrame frame, MultibyteStreamReaderObject self, long size,
                        @Cached IReadNode iReadNode) {
            return iReadNode.execute(frame, self, StringLiterals.T_READLINE, size);
        }
    }

    @Builtin(name = "readlines", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sizehintobj"}, doc = "readlines($self, sizehintobj=None, /)\n--\n\n")
    @ArgumentClinic(name = "sizehintobj", conversion = ArgumentClinic.ClinicConversion.Long, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlinesNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteStreamReaderBuiltinsClinicProviders.ReadlinesNodeClinicProviderGen.INSTANCE;
        }

        // _multibytecodec_MultibyteStreamReader_readlines_impl
        @Specialization
        static Object readlines(VirtualFrame frame, MultibyteStreamReaderObject self, long sizehint,
                        @Cached StringBuiltins.SplitLinesNode splitLinesNode,
                        @Cached IReadNode iReadNode) {
            TruffleString r = iReadNode.execute(frame, self, StringLiterals.T_READ, sizehint);
            return splitLinesNode.execute(frame, r, true);
        }
    }

    @Builtin(name = "reset", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "reset($self, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class ResetNode extends PythonUnaryBuiltinNode {

        // _multibytecodec_MultibyteStreamReader_reset_impl
        @Specialization
        static Object reset(MultibyteStreamReaderObject self) {
            self.codec.decreset(self.state);
            self.pendingsize = 0;
            return PNone.NONE;
        }

    }

    @Builtin(name = "stream", minNumOfPositionalArgs = 1, isGetter = true) // READONLY member
    @GenerateNodeFactory
    abstract static class StreamMemberNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object stream(MultibyteStreamReaderObject self) {
            return self.stream;
        }
    }

}
