/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NOTNULL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_STRINGS;
import static com.oracle.graal.python.nodes.ErrorMessages.DELIMITER_IS_A_SPACE_AND_SKIPINITIALSPACE_IS_TRUE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.GetNextNode;
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
                        @Cached JoinAppendData joinAppendData,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PyObjectStrAsTruffleStringNode objectStrAsTruffleStringNode,
                        @Cached PyNumberCheckNode pyNumberCheckNode,
                        @Cached GetNextNode getNextNode,
                        @Cached IsBuiltinObjectProfile isBuiltinClassProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object iter;

            try {
                iter = getIter.execute(frame, inliningTarget, seq);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.TypeError, errorProfile);
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.CSVError, ErrorMessages.EXPECTED_ITERABLE_NOT_S, getClass.execute(inliningTarget, seq));
            }

            // Join all fields of passed in sequence in internal buffer.
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            CSVDialect dialect = self.dialect;
            boolean first = true;
            boolean nullField = false;
            while (true) {
                try {
                    Object field = getNextNode.execute(frame, iter);
                    /* If this is not the first field we need a field separator */
                    if (!first) {
                        appendStringNode.execute(sb, dialect.delimiter);
                    } else {
                        first = false;
                    }
                    boolean quoted;
                    TruffleString str = null;

                    switch (dialect.quoting) {
                        case QUOTE_NONNUMERIC:
                            quoted = !pyNumberCheckNode.execute(inliningTarget, field);
                            break;
                        case QUOTE_ALL:
                            quoted = true;
                            break;
                        case QUOTE_STRINGS:
                            str = objectStrAsTruffleStringNode.execute(null, inliningTarget, field);
                            // if field isn't a String then the above statement will throw.
                            quoted = true;
                            break;
                        case QUOTE_NOTNULL:
                            quoted = field != PNone.NONE;
                            break;
                        default:
                            quoted = false;
                            break;
                    }

                    nullField = field == PNone.NONE;
                    if (nullField) {
                        joinAppend(inliningTarget, sb, self, null, quoted,
                                        raiseNode, appendStringNode, codePointLengthNode, joinAppendData);
                    } else {
                        if (str == null) {
                            str = objectStrAsTruffleStringNode.execute(null, inliningTarget, field);
                        }
                        joinAppend(inliningTarget, sb, self, str, quoted,
                                        raiseNode, appendStringNode, codePointLengthNode, joinAppendData);
                    }
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, isBuiltinClassProfile);
                    break;
                }
            }
            if (!first && sb.isEmpty()) {
                if (dialect.quoting == QUOTE_NONE ||
                                (nullField && (dialect.quoting == QUOTE_STRINGS || dialect.quoting == QUOTE_NOTNULL))) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.CSVError, ErrorMessages.EMPTY_FIELD_RECORD_MUST_BE_QUOTED);
                }
                joinAppend(inliningTarget, sb, self, null, true,
                                raiseNode, appendStringNode, codePointLengthNode, joinAppendData);
            }
            /*
             * Add line terminator.
             */
            appendStringNode.execute(sb, dialect.lineTerminator);
            return callNode.executeObject(frame, self.write, toStringNode.execute(sb));
        }

        static void joinAppend(Node inliningTarget, TruffleStringBuilder sb, CSVWriter self, TruffleString field, boolean quotedArg,
                        PRaiseNode.Lazy raiseNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode,
                        TruffleString.CodePointLengthNode codePointLengthNode,
                        JoinAppendData joinAppendData) {
            boolean quoted = quotedArg;
            CSVDialect dialect = self.dialect;
            int fieldLen = 0;
            if (field != null) {
                fieldLen = codePointLengthNode.execute(field, TS_ENCODING);
            }
            if (fieldLen == 0 && dialect.delimiterCodePoint == ' ' && dialect.skipInitialSpace) {
                if (dialect.quoting == QUOTE_NONE ||
                                (field == null &&
                                                (dialect.quoting == QUOTE_STRINGS || dialect.quoting == QUOTE_NOTNULL))) {
                    raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.CSVError,
                                    DELIMITER_IS_A_SPACE_AND_SKIPINITIALSPACE_IS_TRUE);
                }
                quoted = true;
            }
            quoted = joinAppendData.execute(inliningTarget, sb, dialect, field, quoted, false,
                            raiseNode, appendStringNode);

            joinAppendData.execute(inliningTarget, sb, dialect, field, quoted, true,
                            raiseNode, appendStringNode);
        }

    }

    protected static abstract class JoinAppendData extends Node {

        abstract boolean execute(Node inliningTarget, TruffleStringBuilder sb, CSVDialect dialect, TruffleString field, boolean quoted, boolean copyPhase,
                        PRaiseNode.Lazy raiseNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode);

        @Specialization
        static boolean joinAppendData(Node inliningTarget, TruffleStringBuilder sb, CSVDialect dialect, TruffleString field, boolean quotedArg, boolean isCopyPhase,
                        PRaiseNode.Lazy raiseNode,
                        TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode) {

            boolean quoted = quotedArg;

            /* Handle preceding quote */
            if (isCopyPhase && quoted) {
                addChar(sb, dialect.quoteChar, true, appendStringNode);
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
                                    addChar(sb, dialect.quoteChar, isCopyPhase, appendStringNode);
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
                                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.CSVError, ErrorMessages.ESCAPE_WITHOUT_ESCAPECHAR);
                            }
                            addChar(sb, dialect.escapeChar, isCopyPhase, appendStringNode);
                        }
                    }
                    /*
                     * Copy field character into record buffer.
                     */
                    addChar(sb, c, isCopyPhase, appendCodePointNode);
                }
            }
            if (quoted) {
                addChar(sb, dialect.quoteChar, isCopyPhase, appendStringNode);
            }

            return quoted;
        }

        static void addChar(TruffleStringBuilder sb, TruffleString c, boolean isCopyPhase,
                        TruffleStringBuilder.AppendStringNode appendStringNode) {
            if (isCopyPhase) {
                appendStringNode.execute(sb, c);
            }
        }

        static void addChar(TruffleStringBuilder sb, int c, boolean isCopyPhase,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode) {
            if (isCopyPhase) {
                appendCodePointNode.execute(sb, c, 1, true);
            }
        }

        private static boolean needsEscape(CSVDialect dialect, int c, TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
            return c == dialect.delimiterCodePoint ||
                            c == dialect.escapeCharCodePoint ||
                            c == dialect.quoteCharCodePoint ||
                            c == '\n' ||
                            c == '\r' ||
                            byteIndexOfCodePointNode.execute(dialect.lineTerminator, c, 0, dialect.lineTerminator.byteLength(TS_ENCODING), TS_ENCODING) >= 0;
        }
    }

    @Builtin(name = "writerows", parameterNames = {"$self", "seqseq"}, minNumOfPositionalArgs = 2, doc = WRITEROWS_DOC)
    @GenerateNodeFactory
    public abstract static class WriteRowsNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, CSVWriter self, Object seq,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode getNext,
                        @Cached IsBuiltinObjectProfile isBuiltinClassProfile,
                        @Cached WriteRowNode writeRow) {
            Object iter, row;

            iter = getIter.execute(frame, inliningTarget, seq);

            while (true) {
                try {
                    row = getNext.execute(frame, iter);
                    writeRow.execute(frame, self, row);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, isBuiltinClassProfile);
                    break;
                }
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
