/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.csv;

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET_CODEPOINT;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVWriter)
public final class CSVWriterBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVWriterBuiltinsFactory.getFactories();
    }

    @Builtin(name = "writerow", parameterNames = {"$self", "seq"}, minNumOfPositionalArgs = 2, doc = WRITEROW_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetClassNode getClass,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode stringNextNode,
                        @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PyObjectStrAsTruffleStringNode objectStrAsTruffleStringNode,
                        @Cached PyNumberCheckNode pyNumberCheckNode,
                        @Cached PyIterNextNode nextNode,
                        @Cached PRaiseNode raiseNode) {
            Object iter;

            try {
                iter = getIter.execute(frame, inliningTarget, seq);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.TypeError, errorProfile);
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.EXPECTED_ITERABLE_NOT_S, getClass.execute(inliningTarget, seq));
            }

            // Join all fields of passed in sequence in internal buffer.
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            CSVDialect dialect = self.dialect;
            boolean first = true;
            while (true) {
                Object field;
                try {
                    field = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
                    break;
                }
                /* If this is not the first field we need a field separator */
                if (!first) {
                    appendStringNode.execute(sb, dialect.delimiter);
                } else {
                    first = false;
                }
                joinField(inliningTarget, sb, dialect, field, createCodePointIteratorNode, stringNextNode, byteIndexOfCodePointNode, appendCodePointNode, appendStringNode,
                                objectStrAsTruffleStringNode, pyNumberCheckNode, raiseNode);
            }
            if (!first && sb.isEmpty()) {
                if (dialect.quoting == QUOTE_NONE) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.EMPTY_FIELD_RECORD_MUST_BE_QUOTED);
                }
                joinAppend(inliningTarget, sb, dialect, null, true, createCodePointIteratorNode, stringNextNode, byteIndexOfCodePointNode, appendCodePointNode, appendStringNode, raiseNode);
            }
            appendStringNode.execute(sb, dialect.lineTerminator);
            return callNode.executeObject(frame, self.write, toStringNode.execute(sb));
        }

        private static void joinField(Node inliningTarget, TruffleStringBuilder sb, CSVDialect dialect, Object field, TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        TruffleStringIterator.NextNode nextNode, TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode, TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode, PyObjectStrAsTruffleStringNode objectStrAsTruffleStringNode, PyNumberCheckNode pyNumberCheckNode,
                        PRaiseNode raiseNode) {
            boolean quoted;

            switch (dialect.quoting) {
                case QUOTE_NONNUMERIC:
                    quoted = !pyNumberCheckNode.execute(inliningTarget, field);
                    break;
                case QUOTE_ALL:
                    quoted = true;
                    break;
                default:
                    quoted = false;
                    break;
            }

            if (field == PNone.NONE) {
                joinAppend(inliningTarget, sb, dialect, null, quoted, createCodePointIteratorNode, nextNode, byteIndexOfCodePointNode, appendCodePointNode, appendStringNode, raiseNode);
            } else {
                TruffleString str = objectStrAsTruffleStringNode.execute(null, inliningTarget, field);
                joinAppend(inliningTarget, sb, dialect, str, quoted, createCodePointIteratorNode, nextNode, byteIndexOfCodePointNode, appendCodePointNode, appendStringNode, raiseNode);
            }
        }

        private static void joinAppend(Node inliningTarget, TruffleStringBuilder sb, CSVDialect dialect, TruffleString field, boolean quoted,
                        TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode, TruffleStringIterator.NextNode nextNode, TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode, TruffleStringBuilder.AppendStringNode appendStringNode, PRaiseNode raiseNode) {
            /*
             * If we don't already know that the field must be quoted due to dialect settings, check
             * if the field contains characters due to which it must be quoted.
             */
            if (!quoted) {
                quoted = needsQuotes(dialect, field, createCodePointIteratorNode, nextNode, byteIndexOfCodePointNode);
            }

            /* Handle preceding quote */
            if (quoted) {
                appendStringNode.execute(sb, dialect.quoteChar);
            }

            /* Copy field data and add escape chars as needed */
            /* If field is null just pass over */
            if (field != null) {
                TruffleStringIterator tsi = createCodePointIteratorNode.execute(field, TS_ENCODING);
                while (tsi.hasNext()) {

                    boolean wantEscape = false;

                    final int c = nextNode.execute(tsi);

                    if (needsEscape(dialect, c, byteIndexOfCodePointNode)) {
                        if (dialect.quoting == QUOTE_NONE) {
                            wantEscape = true;
                        } else {
                            if (c == dialect.quoteCharCodePoint) {
                                if (dialect.doubleQuote) {
                                    appendStringNode.execute(sb, dialect.quoteChar);
                                } else {
                                    wantEscape = true;
                                }
                            } else if (c == dialect.escapeCharCodePoint) {
                                wantEscape = true;
                            }
                            if (!wantEscape) {
                                quoted = true;
                            }
                        }
                        if (wantEscape) {
                            if (dialect.escapeCharCodePoint == NOT_SET_CODEPOINT) {
                                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.ESCAPE_WITHOUT_ESCAPECHAR);
                            }
                            appendStringNode.execute(sb, dialect.escapeChar);
                        }
                    }
                    appendCodePointNode.execute(sb, c, 1, true);
                }
            }
            if (quoted) {
                appendStringNode.execute(sb, dialect.quoteChar);
            }
        }

        private static boolean needsQuotes(CSVDialect dialect, TruffleString field, TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode, TruffleStringIterator.NextNode nextNode,
                        TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
            if (field == null) {
                return false;
            }
            TruffleStringIterator tsi = createCodePointIteratorNode.execute(field, TS_ENCODING);
            while (tsi.hasNext()) {
                final int c = nextNode.execute(tsi);
                if (needsEscape(dialect, c, byteIndexOfCodePointNode)) {
                    if (!(dialect.quoting == QUOTE_NONE ||
                                    c == dialect.quoteCharCodePoint && !dialect.doubleQuote ||
                                    c == dialect.escapeCharCodePoint)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean needsEscape(CSVDialect dialect, int codePoint, TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
            return codePoint == dialect.delimiterCodePoint || codePoint == dialect.escapeCharCodePoint || codePoint == dialect.quoteCharCodePoint ||
                            byteIndexOfCodePointNode.execute(dialect.lineTerminator, codePoint, 0, dialect.lineTerminator.byteLength(TS_ENCODING), TS_ENCODING) >= 0;
        }
    }

    @Builtin(name = "writerows", parameterNames = {"$self", "seqseq"}, minNumOfPositionalArgs = 2, doc = WRITEROWS_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowsNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached WriteRowNode writeRow) {
            Object iter = getIter.execute(frame, inliningTarget, seq);
            while (true) {
                Object row;
                try {
                    row = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
                    break;
                }
                writeRow.execute(frame, self, row);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "dialect", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetDialectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static CSVDialect doIt(CSVWriter self) {
            return self.dialect;
        }
    }

    private static final String WRITEROW_DOC = "writerow(iterable)\n" +
                    "\n" +
                    "Construct and write a CSV record from an iterable of fields.  Non-string\n" +
                    "elements will be converted to string.";

    private static final String WRITEROWS_DOC = "writerows(iterable of iterables)\n" +
                    "\n" +
                    "Construct and write a series of iterables to a csv file.  Non-string\n" +
                    "elements will be converted to string.";

}
