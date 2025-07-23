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

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.T__CSV;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.AFTER_ESCAPED_CRNL;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.EAT_CRNL;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.ESCAPED_CHAR;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.ESCAPE_IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.IN_QUOTED_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_FIELD;
import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_RECORD;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONNUMERIC;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilder.AppendCodePointNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder.ToStringNode;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVReader)
public final class CSVReaderBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = CSVReaderBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVReaderBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterReaderNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object iter(CSVReader self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextReaderNode extends TpIterNextBuiltin {

        private static final int EOL = -2;
        private static final int NEWLINE_CODEPOINT = '\n';
        private static final int CARRIAGE_RETURN_CODEPOINT = '\r';
        private static final int SPACE_CODEPOINT = ' ';

        @Specialization
        static Object nextPos(VirtualFrame frame, CSVReader self,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode stringNextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PyNumberFloatNode pyNumberFloatNode,
                        @Cached AppendNode appendNode,
                        @Cached PyIterNextNode nextNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            PList fields = PFactory.createList(language);
            CSVModuleBuiltins csvModuleBuiltins = (CSVModuleBuiltins) PythonContext.get(inliningTarget).lookupBuiltinModule(T__CSV).getBuiltins();
            self.parseReset();
            do {
                Object lineObj;
                try {
                    lineObj = nextNode.execute(frame, inliningTarget, self.inputIter);
                } catch (IteratorExhausted e) {
                    self.fieldLimit = csvModuleBuiltins.fieldLimit;
                    if (!self.field.isEmpty() || self.state == IN_QUOTED_FIELD) {
                        if (self.dialect.strict) {
                            throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.UNEXPECTED_END_OF_DATA);
                        } else {
                            try {
                                parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);
                            } catch (AbstractTruffleException ignored) {
                            }
                            break;
                        }
                    }
                    throw iteratorExhausted();
                }
                self.fieldLimit = csvModuleBuiltins.fieldLimit;

                TruffleString line;
                try {
                    line = castToStringNode.execute(inliningTarget, lineObj);
                } catch (CannotCastException e) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.WRONG_ITERATOR_RETURN_TYPE, getClassNode.execute(inliningTarget, lineObj));
                }

                self.lineNum++;
                TruffleStringIterator tsi = createCodePointIteratorNode.execute(line, TS_ENCODING);
                while (tsi.hasNext()) {
                    final int codepoint = stringNextNode.execute(tsi, TS_ENCODING);
                    parseProcessCodePoint(inliningTarget, self, fields, codepoint, appendCodePointNode, toStringNode, pyNumberFloatNode, appendNode, raiseNode);
                }
                parseProcessCodePoint(inliningTarget, self, fields, EOL, appendCodePointNode, toStringNode, pyNumberFloatNode, appendNode, raiseNode);

            } while (self.state != START_RECORD);
            return fields;
        }

        @SuppressWarnings("fallthrough")
        private static void parseProcessCodePoint(Node inliningTarget, CSVReader self, PList fields, int codePoint, AppendCodePointNode appendCodePointNode, ToStringNode toStringNode,
                        PyNumberFloatNode pyNumberFloatNode, AppendNode appendNode, PRaiseNode raiseNode) {
            CSVDialect dialect = self.dialect;

            switch (self.state) {
                case START_RECORD:
                    /* start of record */
                    if (codePoint == EOL) {
                        /* empty line - return [] */
                        break;
                    } else if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                        self.state = EAT_CRNL;
                        break;
                    }
                    /* normal character - handle as START_FIELD */
                    self.state = START_FIELD;
                    /* fallthru */

                case START_FIELD:
                    /* expecting field */
                    if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                        /* save empty field - return [fields] */
                        parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);
                        self.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                    } else if (codePoint == dialect.quoteCharCodePoint &&
                                    dialect.quoting != QUOTE_NONE) {
                        /* start quoted field */
                        self.state = IN_QUOTED_FIELD;
                    } else if (codePoint == dialect.escapeCharCodePoint) {
                        /* possible escaped character */
                        if (dialect.quoting == QUOTE_NONNUMERIC) {
                            self.numericField = true;
                        }
                        self.state = ESCAPED_CHAR;
                    } else if (codePoint == SPACE_CODEPOINT && dialect.skipInitialSpace) {
                        /* ignore space at start of field */
                    } else if (codePoint == dialect.delimiterCodePoint) {
                        /* save empty field */
                        parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);
                    } else {
                        /* begin new unquoted field */
                        if (dialect.quoting == QUOTE_NONNUMERIC) {
                            self.numericField = true;
                        }
                        parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                        self.state = IN_FIELD;
                    }
                    break;

                case ESCAPED_CHAR:
                    if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                        parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                        self.state = AFTER_ESCAPED_CRNL;
                        break;
                    }
                    if (codePoint == EOL) {
                        codePoint = NEWLINE_CODEPOINT;
                    }
                    parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);

                    self.state = IN_FIELD;
                    break;

                case AFTER_ESCAPED_CRNL:
                    if (codePoint == EOL) {
                        break;
                    }
                    /* fallthru */

                case IN_FIELD:
                    /* in unquoted field */
                    if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                        /* end of line - return [fields] */
                        parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);

                        self.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                    } else if (codePoint == dialect.escapeCharCodePoint) {
                        /* possible escaped character */
                        self.state = ESCAPED_CHAR;
                    } else if (codePoint == dialect.delimiterCodePoint) {
                        /* save field - wait for new field */
                        parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);
                        self.state = START_FIELD;
                    } else {
                        /* normal character - save in field */
                        parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                    }
                    break;

                case IN_QUOTED_FIELD:
                    /* in quoted field */
                    if (codePoint == EOL) {
                        /* ignore */
                    } else if (codePoint == dialect.escapeCharCodePoint) {
                        /* Possible escape character */
                        self.state = ESCAPE_IN_QUOTED_FIELD;
                    } else if (codePoint == dialect.quoteCharCodePoint &&
                                    dialect.quoting != QUOTE_NONE) {
                        if (dialect.doubleQuote) {
                            /* doublequote; " represented by "" */
                            self.state = ReaderState.QUOTE_IN_QUOTED_FIELD;
                        } else {
                            /* end of quote part of field */
                            self.state = IN_FIELD;
                        }
                    } else {
                        /* normal character - save in field */
                        parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                    }
                    break;

                case ESCAPE_IN_QUOTED_FIELD:
                    if (codePoint == EOL) {
                        codePoint = NEWLINE_CODEPOINT;
                    }
                    parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                    self.state = IN_QUOTED_FIELD;
                    break;

                case QUOTE_IN_QUOTED_FIELD:
                    /* doublequote - seen a quote in a quoted field */
                    if (dialect.quoting != QUOTE_NONE &&
                                    codePoint == dialect.quoteCharCodePoint) {
                        /* save "" as " */
                        parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                        self.state = IN_QUOTED_FIELD;
                    } else if (codePoint == dialect.delimiterCodePoint) {
                        /* save field - wait for new field */
                        parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);
                        self.state = START_FIELD;
                    } else if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT || codePoint == EOL) {
                        /* end of line - return [fields] */
                        parseSaveField(inliningTarget, self, fields, toStringNode, pyNumberFloatNode, appendNode);
                        self.state = (codePoint == EOL) ? START_RECORD : EAT_CRNL;
                    } else if (!dialect.strict) {
                        parseAddCodePoint(inliningTarget, self, codePoint, appendCodePointNode, raiseNode);
                        self.state = IN_FIELD;
                    } else {
                        /* illegal */
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.S_EXPECTED_AFTER_S, dialect.delimiter, dialect.quoteChar);
                    }
                    break;

                case EAT_CRNL:
                    if (codePoint == NEWLINE_CODEPOINT || codePoint == CARRIAGE_RETURN_CODEPOINT) {
                        /* ignore */
                    } else if (codePoint == EOL) {
                        self.state = START_RECORD;
                    } else {
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.NEWLINE_IN_UNQOUTED_FIELD);
                    }
                    break;
            }
        }

        private static void parseSaveField(Node inliningTarget, CSVReader self, PList fields, ToStringNode toStringNode, PyNumberFloatNode pyNumberFloatNode, AppendNode appendNode) {
            TruffleString field = toStringNode.execute(self.field);
            self.field = TruffleStringBuilder.create(TS_ENCODING);
            if (self.numericField) {
                self.numericField = false;
                appendNode.execute(fields, pyNumberFloatNode.execute(inliningTarget, field));
            } else {
                appendNode.execute(fields, field);
            }
        }

        private static void parseAddCodePoint(Node inliningTarget, CSVReader self, int codePoint, TruffleStringBuilder.AppendCodePointNode appendCodePointNode, PRaiseNode raise) {
            assert TS_ENCODING == TruffleString.Encoding.UTF_32;
            int cpLen = self.field.byteLength() / 4;        // assumes UTF-32
            if (cpLen + 1 > self.fieldLimit) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.LARGER_THAN_FIELD_SIZE_LIMIT, self.fieldLimit);
            }
            appendCodePointNode.execute(self.field, codePoint, 1, true);
        }
    }

    @Builtin(name = "dialect", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetDialectNode extends PythonUnaryBuiltinNode {
        @Specialization
        static CSVDialect doIt(CSVReader self) {
            return self.dialect;
        }
    }

    @Builtin(name = "line_num", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class GetLineNumNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int doIt(CSVReader self) {
            return self.lineNum;
        }
    }
}
