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

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.J_ATTR_DELIMITER;
import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET_CODEPOINT;
import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.T__CSV;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_MINIMAL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;
import static com.oracle.graal.python.nodes.StringLiterals.J_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_CRLF;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOUBLE_QUOTE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVDialect)
public final class CSVDialectBuiltins extends PythonBuiltins {

    private static final String J_ATTR_DOUBLEQUOTE = "doublequote";
    private static final String J_ATTR_ESCAPECHAR = "escapechar";
    private static final String J_ATTR_LINETERMINATOR = "lineterminator";
    private static final String J_ATTR_QUOTING = "quoting";
    private static final String J_ATTR_QUOTECHAR = "quotechar";
    private static final String J_ATTR_SKIPINITIALSPACE = "skipinitialspace";
    private static final String J_ATTR_STRICT = J_STRICT;
    private static final TruffleString T_ATTR_SKIPINITIALSPACE = tsLiteral(J_ATTR_SKIPINITIALSPACE);
    private static final TruffleString T_ATTR_QUOTECHAR = tsLiteral(J_ATTR_QUOTECHAR);
    private static final TruffleString T_ATTR_QUOTING = tsLiteral(J_ATTR_QUOTING);
    private static final TruffleString T_ATTR_LINETERMINATOR = tsLiteral(J_ATTR_LINETERMINATOR);
    private static final TruffleString T_ATTR_ESCAPECHAR = tsLiteral(J_ATTR_ESCAPECHAR);
    private static final TruffleString T_ATTR_DOUBLEQUOTE = tsLiteral(J_ATTR_DOUBLEQUOTE);
    private static final TruffleString T_ATTR_DELIMITER = tsLiteral(J_ATTR_DELIMITER);
    private static final TruffleString T_ATTR_STRICT = T_STRICT;
    private static final TruffleString T_NOT_SET = tsLiteral("NOT_SET");

    public static final TpSlots SLOTS = CSVDialectBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVDialectBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "CSVDialect", parameterNames = {"class", "dialect", "delimiter", "doublequote", "escapechar",
                    "lineterminator", "quotechar",
                    "quoting", "skipinitialspace", "strict"})
    @GenerateNodeFactory
    public abstract static class DialectNode extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static Object doCSVDialectWithoutKeywords(PythonBuiltinClassType cls, CSVDialect dialect, PNone delimiter, PNone doublequote, PNone escapechar,
                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace, PNone strict) {
            return dialect;
        }

        @Specialization
        @SuppressWarnings("unused")
        static CSVDialect doStringWithoutKeywords(VirtualFrame frame, PythonBuiltinClassType cls, TruffleString dialectName, PNone delimiter, PNone doublequote, PNone escapechar,
                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace, PNone strict,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect) {
            PythonModule module = PythonContext.get(inliningTarget).lookupBuiltinModule(T__CSV);
            return getDialect.execute(frame, module, dialectName);
        }

        @Specialization
        static Object doNoDialectObj(VirtualFrame frame, PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, raiseNode);
        }

        @Specialization
        static Object doStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, TruffleString dialectName, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            PythonModule module = PythonContext.get(inliningTarget).lookupBuiltinModule(T__CSV);
            CSVDialect dialectObj = getDialect.execute(frame, module, dialectName);

            if (delimiterObj == PNone.NO_VALUE) {
                delimiterObj = dialectObj.delimiter;
            }
            if (doublequoteObj == PNone.NO_VALUE) {
                doublequoteObj = dialectObj.doubleQuote;
            }
            if (escapecharObj == PNone.NO_VALUE) {
                escapecharObj = dialectObj.escapeChar;
            }
            if (lineterminatorObj == PNone.NO_VALUE) {
                lineterminatorObj = dialectObj.lineTerminator;
            }
            if (quotingObj == PNone.NO_VALUE) {
                quotingObj = dialectObj.quoting;
            }
            if (quotecharObj == PNone.NO_VALUE) {
                quotecharObj = dialectObj.quoteChar;
            }
            if (skipinitialspaceObj == PNone.NO_VALUE) {
                skipinitialspaceObj = dialectObj.skipInitialSpace;
            }
            if (strictObj == PNone.NO_VALUE) {
                strictObj = dialectObj.strict;
            }

            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, raiseNode);
        }

        @Specialization
        static Object doDialectClassWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, PythonClass dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectLookupAttr getFirstAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getSecondAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getThirdAttributesNode,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {

            // We use multiple AttributeNodes to be able to cache all attributes as current
            // CACHE_SIZE is 3.
            delimiterObj = getAttributeValue(frame, inliningTarget, dialectObj, delimiterObj, T_ATTR_DELIMITER, getFirstAttributesNode);
            doublequoteObj = getAttributeValue(frame, inliningTarget, dialectObj, doublequoteObj, T_ATTR_DOUBLEQUOTE, getFirstAttributesNode);
            escapecharObj = getAttributeValue(frame, inliningTarget, dialectObj, escapecharObj, T_ATTR_ESCAPECHAR, getFirstAttributesNode);
            lineterminatorObj = getAttributeValue(frame, inliningTarget, dialectObj, lineterminatorObj, T_ATTR_LINETERMINATOR, getSecondAttributesNode);
            quotecharObj = getAttributeValue(frame, inliningTarget, dialectObj, quotecharObj, T_ATTR_QUOTECHAR, getSecondAttributesNode);
            quotingObj = getAttributeValue(frame, inliningTarget, dialectObj, quotingObj, T_ATTR_QUOTING, getSecondAttributesNode);
            skipinitialspaceObj = getAttributeValue(frame, inliningTarget, dialectObj, skipinitialspaceObj, T_ATTR_SKIPINITIALSPACE, getThirdAttributesNode);
            strictObj = getAttributeValue(frame, inliningTarget, dialectObj, strictObj, T_ATTR_STRICT, getThirdAttributesNode);

            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, raiseNode);
        }

        @Specialization
        static Object doPStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, PString dialectName, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {

            TruffleString dialectNameStr = castToStringNode.execute(inliningTarget, dialectName);
            PythonModule module = PythonContext.get(inliningTarget).lookupBuiltinModule(T__CSV);
            CSVDialect dialectObj = getDialect.execute(frame, module, dialectNameStr);

            if (delimiterObj == PNone.NO_VALUE) {
                delimiterObj = dialectObj.delimiter;
            }
            if (doublequoteObj == PNone.NO_VALUE) {
                doublequoteObj = dialectObj.doubleQuote;
            }
            if (escapecharObj == PNone.NO_VALUE) {
                escapecharObj = dialectObj.escapeChar;
            }
            if (lineterminatorObj == PNone.NO_VALUE) {
                lineterminatorObj = dialectObj.lineTerminator;
            }
            if (quotingObj == PNone.NO_VALUE) {
                quotingObj = dialectObj.quoting;
            }
            if (quotecharObj == PNone.NO_VALUE) {
                quotecharObj = dialectObj.quoteChar;
            }
            if (skipinitialspaceObj == PNone.NO_VALUE) {
                skipinitialspaceObj = dialectObj.skipInitialSpace;
            }
            if (strictObj == PNone.NO_VALUE) {
                strictObj = dialectObj.strict;
            }

            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, raiseNode);
        }

        @Specialization(guards = {"!isCSVDialect(dialectObj)", "!isPythonClass(dialectObj)", "!isString(dialectObj)", "!isPNone(dialectObj)"})
        static Object doGeneric(VirtualFrame frame, Object cls, Object dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectLookupAttr getFirstAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getSecondAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getThirdAttributesNode,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {

            delimiterObj = getAttributeValue(frame, inliningTarget, dialectObj, delimiterObj, T_ATTR_DELIMITER, getFirstAttributesNode);
            doublequoteObj = getAttributeValue(frame, inliningTarget, dialectObj, doublequoteObj, T_ATTR_DOUBLEQUOTE, getFirstAttributesNode);
            escapecharObj = getAttributeValue(frame, inliningTarget, dialectObj, escapecharObj, T_ATTR_ESCAPECHAR, getFirstAttributesNode);
            lineterminatorObj = getAttributeValue(frame, inliningTarget, dialectObj, lineterminatorObj, T_ATTR_LINETERMINATOR, getSecondAttributesNode);
            quotingObj = getAttributeValue(frame, inliningTarget, dialectObj, quotingObj, T_ATTR_QUOTING, getSecondAttributesNode);
            quotecharObj = getAttributeValue(frame, inliningTarget, dialectObj, quotecharObj, T_ATTR_QUOTECHAR, getSecondAttributesNode);
            skipinitialspaceObj = getAttributeValue(frame, inliningTarget, dialectObj, skipinitialspaceObj, T_ATTR_SKIPINITIALSPACE, getThirdAttributesNode);
            strictObj = getAttributeValue(frame, inliningTarget, dialectObj, strictObj, T_ATTR_STRICT, getThirdAttributesNode);

            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, raiseNode);
        }

        protected static boolean isCSVDialect(Object dialect) {
            return dialect instanceof CSVDialect;
        }

        private static Object createCSVDialect(VirtualFrame frame, Node inliningTarget, Object cls, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        PyObjectIsTrueNode isTrueNode, PyLongCheckExactNode pyLongCheckExactNode, PyLongAsIntNode pyLongAsIntNode, PRaiseNode raiseNode) {
            TruffleString delimiter = getChar(inliningTarget, T_ATTR_DELIMITER, delimiterObj, T_COMMA, false);
            boolean doubleQuote = getBoolean(frame, doublequoteObj, true, isTrueNode);
            TruffleString escapeChar = getChar(inliningTarget, T_ATTR_ESCAPECHAR, escapecharObj, T_NOT_SET, true);
            TruffleString lineTerminator = getString(inliningTarget, T_ATTR_LINETERMINATOR, lineterminatorObj, T_CRLF);
            TruffleString quoteChar = getChar(inliningTarget, T_ATTR_QUOTECHAR, quotecharObj, T_DOUBLE_QUOTE, true);
            QuoteStyle quoting = getQuotingValue(frame, inliningTarget, T_ATTR_QUOTING, quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode, raiseNode);
            boolean skipInitialSpace = getBoolean(frame, skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame, strictObj, false, isTrueNode);
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }
            return createCSVDialect(inliningTarget, cls, delimiter, doubleQuote, escapeChar, lineTerminator, quoteChar, quoting, skipInitialSpace, strict);
        }

        @TruffleBoundary
        private static Object createCSVDialect(Node raisingNode, Object cls, TruffleString delimiter, boolean doubleQuote, TruffleString escapeChar, TruffleString lineTerminator,
                        TruffleString quoteChar, QuoteStyle quoting, boolean skipInitialSpace, boolean strict) {
            if (TruffleString.EqualNode.getUncached().execute(delimiter, T_NOT_SET, TS_ENCODING)) {
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.DELIMITER_MUST_BE_ONE_CHAR_STRING);
            }

            if (quoting != QUOTE_NONE && TruffleString.EqualNode.getUncached().execute(quoteChar, T_NOT_SET, TS_ENCODING)) {
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.QUOTECHAR_MUST_BE_SET_IF_QUOTING_ENABLED);
            }

            if (lineTerminator == null) {
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.LINETERMINATOR_MUST_BE_SET);
            }

            // delimiter cannot be NOT_SET
            int delimiterCodePoint = TruffleString.CodePointAtIndexUTF32Node.getUncached().execute(delimiter, 0);
            int escapeCharCodePoint = TruffleString.EqualNode.getUncached().execute(escapeChar, T_NOT_SET, TS_ENCODING) ? NOT_SET_CODEPOINT
                            : TruffleString.CodePointAtIndexUTF32Node.getUncached().execute(escapeChar, 0);
            int quoteCharCodePoint = TruffleString.EqualNode.getUncached().execute(quoteChar, T_NOT_SET, TS_ENCODING) ? NOT_SET_CODEPOINT
                            : TruffleString.CodePointAtIndexUTF32Node.getUncached().execute(quoteChar, 0);

            return PFactory.createCSVDialect(cls, TypeNodes.GetInstanceShape.executeUncached(cls), delimiter, delimiterCodePoint, doubleQuote,
                            escapeChar, escapeCharCodePoint, lineTerminator, quoteChar, quoteCharCodePoint, quoting,
                            skipInitialSpace, strict);
        }

        private static Object getAttributeValue(VirtualFrame frame, Node inliningTarget, Object dialect, Object inputValue, TruffleString attributeName, PyObjectLookupAttr getAttributeNode) {
            if (inputValue != PNone.NO_VALUE) {
                return inputValue;
            }
            return getAttributeNode.execute(frame, inliningTarget, dialect, attributeName);
        }

        @TruffleBoundary
        private static TruffleString getChar(Node raisingNode, TruffleString name, Object valueObj, TruffleString defaultValue, boolean optional) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }
            if (optional && valueObj == PNone.NONE) {
                return T_NOT_SET;
            }

            TruffleString charValue;

            try {
                charValue = CastToTruffleStringNode.executeUncached(valueObj);
            } catch (CannotCastException e) {
                TruffleString format = optional ? ErrorMessages.S_MUST_BE_STRING_OR_NONE_NOT_S : ErrorMessages.S_MUST_BE_STRING_NOT_S;
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, format, name, GetClassNode.executeUncached(valueObj));
            }

            if (optional && TruffleString.EqualNode.getUncached().execute(charValue, T_NOT_SET, TS_ENCODING)) {
                return T_NOT_SET;
            }

            if (TruffleString.CodePointLengthNode.getUncached().execute(charValue, TS_ENCODING) != 1) {
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.MUST_BE_ONE_CHARACTER_STRING, name);
            }

            return charValue;
        }

        private static boolean getBoolean(VirtualFrame frame, Object valueObj, boolean defaultValue, PyObjectIsTrueNode isTrueNode) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            return isTrueNode.execute(frame, valueObj);
        }

        @TruffleBoundary
        private static TruffleString getString(Node raisingNode, TruffleString attribute, Object valueObj, TruffleString defaultValue) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            if (valueObj == PNone.NONE) {
                return null;
            }

            TruffleString value;

            try {
                value = CastToTruffleStringNode.executeUncached(valueObj);
            } catch (CannotCastException e) {
                throw PRaiseNode.raiseStatic(raisingNode, TypeError, ErrorMessages.MUST_BE_STRING_QUOTED, attribute);
            }

            return value;
        }

        private static QuoteStyle getQuotingValue(VirtualFrame frame, Node inliningTarget, TruffleString name, Object valueObj, QuoteStyle defaultValue,
                        PyLongCheckExactNode pyLongCheckExactNode, PyLongAsIntNode pyLongAsIntNode, PRaiseNode raiseNode) {

            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            if (valueObj instanceof QuoteStyle) {
                return (QuoteStyle) valueObj;
            }

            if (!pyLongCheckExactNode.execute(inliningTarget, valueObj)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_INTEGER_QUOTED_ATTR, name);
            }

            int value = pyLongAsIntNode.execute(frame, inliningTarget, valueObj);

            if (!QuoteStyle.containsOrdinalValue(value)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.BAD_QUOTING_VALUE);
            }

            return QuoteStyle.getQuoteStyle(value);
        }
    }

    @Builtin(name = J_ATTR_DELIMITER, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DelimiterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.delimiterCodePoint == NOT_SET_CODEPOINT ? PNone.NONE : self.delimiter;
        }
    }

    @Builtin(name = J_ATTR_DOUBLEQUOTE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DoubleQuoteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.doubleQuote;
        }
    }

    @Builtin(name = J_ATTR_ESCAPECHAR, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EscapeCharNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.escapeCharCodePoint == NOT_SET_CODEPOINT ? PNone.NONE : self.escapeChar;
        }
    }

    @Builtin(name = J_ATTR_LINETERMINATOR, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class LineTerminatorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString doIt(CSVDialect self) {
            return self.lineTerminator;
        }
    }

    @Builtin(name = J_ATTR_QUOTECHAR, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QuoteCharNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.quoteCharCodePoint == NOT_SET_CODEPOINT ? PNone.NONE : self.quoteChar;
        }
    }

    @Builtin(name = J_ATTR_QUOTING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QuotingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int doIt(CSVDialect self) {
            return self.quoting.ordinal();
        }
    }

    @Builtin(name = J_ATTR_SKIPINITIALSPACE, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SkipInitialSpaceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.skipInitialSpace;
        }
    }

    @Builtin(name = J_ATTR_STRICT, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StrictNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.strict;
        }
    }
}
