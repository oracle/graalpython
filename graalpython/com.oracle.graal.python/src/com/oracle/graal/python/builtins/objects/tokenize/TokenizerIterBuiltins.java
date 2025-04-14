/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.tokenize;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_BYTESLIKE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_NONBYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_NON_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_ENCODING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.tokenize.TokenizerIterBuiltinsClinicProviders.TokenizerIterNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.pegparser.tokenizer.CodePoints;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Token.Kind;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.StatusCode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.graal.python.builtins.modules.TokenizeModuleBuiltinsClinicProviders.TokenizerIterNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.tokenize.PTokenizerIter;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTokenizerIter)
public final class TokenizerIterBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = TokenizerIterBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TokenizerIterBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "TokenizerIter", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "readline"}, keywordOnlyNames = {"extra_tokens",
                    "encoding"})
    @ArgumentClinic(name = "extra_tokens", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @ArgumentClinic(name = "encoding", conversion = ClinicConversion.TString, useDefaultForNone = true, defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    abstract static class TokenizerIterNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TokenizerIterNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTokenizerIter tokenizerIterStr(Object cls, Object readline, boolean extraTokens, @SuppressWarnings("unused") PNone encoding,
                        @Bind PythonLanguage language,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            Supplier<int[]> inputSupplier = () -> {
                Object o;
                try {
                    o = CallNode.executeUncached(readline);
                } catch (PException e) {
                    e.expectUncached(PythonBuiltinClassType.StopIteration);
                    return null;
                }
                TruffleString line;
                try {
                    line = CastToTruffleStringNode.executeUncached(o);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.TypeError, RETURNED_NON_STRING, "readline()", o);
                }
                return getCodePoints(line);
            };
            return PFactory.createTokenizerIter(language, cls, getInstanceShape.execute(cls), inputSupplier, extraTokens);
        }

        @Specialization
        static PTokenizerIter tokenizerIterBytes(Object cls, Object readline, boolean extraTokens, TruffleString encoding,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            Charset charset;
            try {
                charset = getCharset(Tokenizer.getNormalName(encoding.toJavaStringUncached()));
            } catch (Exception e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.LookupError, UNKNOWN_ENCODING, encoding);
            }

            Supplier<int[]> inputSupplier = () -> {
                Object o;
                try {
                    o = CallNode.executeUncached(readline);
                } catch (PException e) {
                    e.expectUncached(PythonBuiltinClassType.StopIteration);
                    return null;
                }
                if (!PyBytesCheckNode.executeUncached(o)) {
                    throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.TypeError, RETURNED_NONBYTES, "readline()", o);
                }

                Object buffer;
                try {
                    buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(o);
                } catch (PException e) {
                    throw PRaiseNode.raiseStatic(null, TypeError, EXPECTED_BYTESLIKE_GOT_P, o);
                }
                PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getUncached();
                byte[] bytes;
                int len;
                try {
                    bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    len = bufferLib.getBufferLength(buffer);
                } finally {
                    bufferLib.release(buffer);
                }
                String line = charset.decode(ByteBuffer.wrap(bytes, 0, len)).toString();
                return getCodePoints(TruffleString.fromJavaStringUncached(line, TS_ENCODING));
            };
            return PFactory.createTokenizerIter(language, cls, getInstanceShape.execute(cls), inputSupplier, extraTokens);
        }

        @TruffleBoundary
        private static Charset getCharset(String s) {
            return Charset.forName(s);
        }

        private static int[] getCodePoints(TruffleString ts) {
            int len = ts.codePointLengthUncached(TS_ENCODING);
            if (len == 0) {
                return null;
            }
            int[] res = new int[len];
            int i = 0;
            TruffleStringIterator it = ts.createCodePointIteratorUncached(TS_ENCODING);
            while (it.hasNext()) {
                res[i++] = it.nextUncached();
            }
            return res;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTokenizerIter iter(PTokenizerIter self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    abstract static class NextNode extends TpIterNextBuiltin {
        private static final TruffleString T_EOF = tsLiteral("EOF");
        private static final CodePoints CP_LF = CodePoints.fromBuffer(new int[]{'\n'}, 0, 1);
        private static final CodePoints CP_CRLF = CodePoints.fromBuffer(new int[]{'\r', '\n'}, 0, 2);

        @Specialization
        static PTuple next(PTokenizerIter self,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.FromIntArrayUTF32Node fromIntArrayUTF32Node,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (self.isDone()) {
                throw raiseNode.raiseStopIteration(inliningTarget, T_EOF);
            }
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            Token token;
            try {
                token = self.getNextToken();
            } finally {
                encapsulating.set(encapsulatingNode);
            }
            CodePoints tokenCp = self.getTokenCodePoints(token);
            int type = token.type;
            boolean isTrailingToken = false;
            if (type == Kind.ENDMARKER || (type == Kind.DEDENT && self.tokenizer.getDone() == StatusCode.EOF)) {
                isTrailingToken = true;
            }
            TruffleString line = self.tokenizer.isExtraTokens() && isTrailingToken ? TS_ENCODING.getEmpty() : self.getLine(token, fromIntArrayUTF32Node, switchEncodingNode);

            int startLine = token.sourceRange.startLine;
            int endLine = token.sourceRange.endLine;
            int startColumn = token.sourceRange.startColumn;
            int endColumn = token.sourceRange.endColumn;
            if (token.type == Kind.NEWLINE) {
                endColumn--;
            }
            if (self.tokenizer.isExtraTokens()) {
                if (isTrailingToken) {
                    startLine += 1;
                    endLine += 1;
                    startColumn = 0;
                    endColumn = 0;
                }

                if (type > Kind.DEDENT && type < Kind.OP) {
                    type = Kind.OP;
                } else if (type == Kind.ASYNC || type == Kind.AWAIT) {
                    type = Kind.NAME;
                } else if (type == Kind.NEWLINE) {
                    if (!self.tokenizer.isImplicitNewline()) {
                        if (!tokenCp.isEmpty() && tokenCp.get(0) == '\r') {
                            tokenCp = CP_CRLF;
                        } else {
                            tokenCp = CP_LF;
                        }
                    } else {
                        tokenCp = CodePoints.EMPTY;
                    }
                    endColumn++;
                } else if (type == Kind.NL) {
                    if (self.tokenizer.isImplicitNewline()) {
                        tokenCp = CodePoints.EMPTY;
                    }
                }
            } else if (type == Kind.INDENT || type == Kind.DEDENT) {
                startColumn = -1;
                endColumn = -1;
            } else if (type == Kind.NEWLINE) {
                tokenCp = CodePoints.EMPTY;
            }

            return PFactory.createTuple(language, new Object[]{
                            type,
                            switchEncodingNode.execute(fromIntArrayUTF32Node.execute(tokenCp.getBuffer(), tokenCp.getOffset(), tokenCp.getLength()), TS_ENCODING),
                            PFactory.createTuple(language, new Object[]{startLine, startColumn}),
                            PFactory.createTuple(language, new Object[]{endLine, endColumn}),
                            line
            });
        }
    }
}
