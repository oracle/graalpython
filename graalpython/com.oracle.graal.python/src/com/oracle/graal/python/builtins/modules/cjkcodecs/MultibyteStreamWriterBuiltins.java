/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteStreamWriter;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.MBENC_RESET;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.encodeEmptyInput;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.internalErrorCallback;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBENC_FLUSH;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_BE_A_SEQUENCE_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.CODEC_IS_UNEXPECTED_TYPE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = MultibyteStreamWriter)
public final class MultibyteStreamWriterBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultibyteStreamWriterBuiltinsFactory.getFactories();
    }

    private static final TruffleString WRITE = tsLiteral("write");

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {

        private static final TruffleString CODEC = tsLiteral("codec");

        @Specialization
        Object mbstreamwriterNew(VirtualFrame frame, Object type, Object stream, Object err,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached TruffleString.EqualNode isEqual,
                        @Cached PythonObjectFactory factory) { // "O|s:StreamWriter"
            TruffleString errors = null;
            if (err != PNone.NO_VALUE) {
                errors = castToStringNode.execute(inliningTarget, err);
            }

            MultibyteStreamWriterObject self = factory.createMultibyteStreamWriterObject(type);

            Object codec = getAttr.execute(frame, inliningTarget, type, CODEC);
            if (!(codec instanceof MultibyteCodecObject)) {
                throw raise(TypeError, CODEC_IS_UNEXPECTED_TYPE);
            }

            self.codec = ((MultibyteCodecObject) codec).codec;
            self.stream = stream;
            self.pending = null;
            self.errors = internalErrorCallback(errors, isEqual);
            self.codec.encinit(self.errors);

            return self;
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone init(@SuppressWarnings("unused") MultibyteStreamWriterObject self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, doc = "write($self, strobj, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryBuiltinNode {

        // _multibytecodec_MultibyteStreamWriter_write
        @Specialization
        static Object write(VirtualFrame frame, MultibyteStreamWriterObject self, Object strobj,
                        @Bind("this") Node inliningTarget,
                        @Cached MultibyteIncrementalEncoderBuiltins.EncodeStatefulNode encodeStatefulNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PythonObjectFactory factory) {
            // mbstreamwriter_iwrite
            Object str = encodeStatefulNode.execute(frame, self, strobj, 0, factory);
            callMethod.execute(frame, inliningTarget, self.stream, WRITE, str);
            return PNone.NONE;
        }
    }

    @Builtin(name = "writelines", minNumOfPositionalArgs = 2, doc = "writelines($self, lines, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class WritelinesNode extends PythonBinaryBuiltinNode {

        // _multibytecodec_MultibyteStreamWriter_writelines
        @Specialization
        static Object writelines(VirtualFrame frame, MultibyteStreamWriterObject self, PSequence lines,
                        @Bind("this") Node inliningTarget,
                        @Cached MultibyteIncrementalEncoderBuiltins.EncodeStatefulNode encodeStatefulNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.GetItemNode getItem,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PythonObjectFactory factory) {

            SequenceStorage sq = getStorage.execute(inliningTarget, lines);
            for (int i = 0; i < sq.length(); i++) {
                /* length can be changed even within this loop */
                Object strobj = getItem.execute(sq, i);
                // mbstreamwriter_iwrite
                Object str = encodeStatefulNode.execute(frame, self, strobj, 0, factory);
                callMethod.execute(frame, inliningTarget, self.stream, WRITE, str);
            }
            return PNone.NONE;
        }

        // assuming !pySequenceCheck.execute(lines)
        @Fallback
        Object writelines(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object lines) {
            throw raise(TypeError, ARG_MUST_BE_A_SEQUENCE_OBJECT);
        }
    }

    @Builtin(name = "reset", minNumOfPositionalArgs = 1, doc = "reset($self, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class ResetNode extends PythonUnaryBuiltinNode {

        // _multibytecodec_MultibyteStreamWriter_reset_impl
        @Specialization
        static Object reset(VirtualFrame frame, MultibyteStreamWriterObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached MultibyteCodecUtil.EncodeNode encodeNode,
                        @Cached PythonObjectFactory factory) {
            if (self.pending == null) {
                return PNone.NONE;
            }
            int datalen = codePointLengthNode.execute(self.pending, TS_ENCODING);
            PBytes pwrt = encodeEmptyInput(datalen, MBENC_FLUSH | MBENC_RESET, factory);
            if (pwrt == null) {
                MultibyteEncodeBuffer buf = new MultibyteEncodeBuffer(self.pending);
                pwrt = encodeNode.execute(frame, inliningTarget, self.codec, self.state, buf,
                                self.errors, MBENC_FLUSH | MBENC_RESET,
                                factory);
            }
            /*
             * some pending buffer can be truncated when UnicodeEncodeError is raised on 'strict'
             * mode. but, 'reset' method is designed to reset the pending buffer or states so failed
             * string sequence ought to be missed
             */
            self.pending = null;

            // assert(PyBytes_Check(pwrt));
            if (pwrt.getSequenceStorage().length() > 0) {
                callMethod.execute(frame, inliningTarget, self.stream, WRITE, pwrt);
            }

            return PNone.NONE;
        }
    }

    @Builtin(name = "stream", minNumOfPositionalArgs = 1, isGetter = true) // READONLY member
    @GenerateNodeFactory
    abstract static class StreamMemberNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object stream(MultibyteStreamWriterObject self) {
            return self.stream;
        }
    }
}
