/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.pegparser.tokenizer.CodePoints;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import com.oracle.graal.python.pegparser.tokenizer.Token.Kind;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer.StatusCode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTokenizerIter)
public final class TokenizerIterBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TokenizerIterBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTokenizerIter iter(PTokenizerIter self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryBuiltinNode {
        private static final TruffleString T_EOF = tsLiteral("EOF");
        private static final CodePoints CP_LF = CodePoints.fromBuffer(new int[]{'\n'}, 0, 1);
        private static final CodePoints CP_CRLF = CodePoints.fromBuffer(new int[]{'\r', '\n'}, 0, 2);

        @Specialization
        static PTuple next(PTokenizerIter self,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.FromIntArrayUTF32Node fromIntArrayUTF32Node,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.isDone()) {
                throw raiseNode.get(inliningTarget).raiseStopIteration(T_EOF);
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

            return factory.createTuple(new Object[]{
                            type,
                            switchEncodingNode.execute(fromIntArrayUTF32Node.execute(tokenCp.getBuffer(), tokenCp.getOffset(), tokenCp.getLength()), TS_ENCODING),
                            factory.createTuple(new Object[]{startLine, startColumn}),
                            factory.createTuple(new Object[]{endLine, endColumn}),
                            line
            });
        }
    }
}
