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

import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_ALL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_MINIMAL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONNUMERIC;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.StringLiterals.J_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_CRLF;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOUBLE_QUOTE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = CSVModuleBuiltins.J__CSV)
public final class CSVModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T__DIALECTS = tsLiteral("_dialects");
    static final String J_ATTR_DELIMITER = "delimiter";
    private static final TruffleString T_ATTR_DELIMITER = tsLiteral(J_ATTR_DELIMITER);
    static final String J_ATTR_DOUBLEQUOTE = "doublequote";
    private static final TruffleString T_ATTR_DOUBLEQUOTE = tsLiteral(J_ATTR_DOUBLEQUOTE);
    static final String J_ATTR_ESCAPECHAR = "escapechar";
    private static final TruffleString T_ATTR_ESCAPECHAR = tsLiteral(J_ATTR_ESCAPECHAR);
    static final String J_ATTR_LINETERMINATOR = "lineterminator";
    private static final TruffleString T_ATTR_LINETERMINATOR = tsLiteral(J_ATTR_LINETERMINATOR);
    static final String J_ATTR_QUOTING = "quoting";
    private static final TruffleString T_ATTR_QUOTING = tsLiteral(J_ATTR_QUOTING);
    static final String J_ATTR_QUOTECHAR = "quotechar";
    private static final TruffleString T_ATTR_QUOTECHAR = tsLiteral(J_ATTR_QUOTECHAR);
    static final String J_ATTR_SKIPINITIALSPACE = "skipinitialspace";
    private static final TruffleString T_ATTR_SKIPINITIALSPACE = tsLiteral(J_ATTR_SKIPINITIALSPACE);
    static final String J_ATTR_STRICT = J_STRICT;
    private static final TruffleString T_ATTR_STRICT = T_STRICT;

    static final String J__CSV = "_csv";
    static final TruffleString T__CSV = tsLiteral(J__CSV);

    static {
        // See comment about Python 3.10 in #getChar() and graalpython/test/test_csv.py
        assert PythonLanguage.MINOR <= 10;
    }

    private static final TruffleString T_NOT_SET = tsLiteral("NOT_SET");
    static final int NOT_SET_CODEPOINT = -1;

    long fieldLimit = 128 * 1024; // max parsed field size

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant(SpecialAttributeNames.T___DOC__, CSV_DOC);
        addBuiltinConstant("__version__", "1.0");
        addBuiltinConstant("QUOTE_MINIMAL", QUOTE_MINIMAL.ordinal());
        addBuiltinConstant("QUOTE_ALL", QUOTE_ALL.ordinal());
        addBuiltinConstant("QUOTE_NONNUMERIC", QUOTE_NONNUMERIC.ordinal());
        addBuiltinConstant("QUOTE_NONE", QUOTE_NONE.ordinal());
        addBuiltinConstant(T__DIALECTS, core.factory().createDict());
        super.initialize(core);
    }

    @Builtin(name = "register_dialect", parameterNames = {"$mod", "name",
                    "dialect"}, minNumOfPositionalArgs = 2, takesVarKeywordArgs = true, declaresExplicitSelf = true, doc = "Create a mapping from a string name to a dialect class.\n" +
                                    "dialect = csv.register_dialect(name, dialect)")
    @GenerateNodeFactory
    public abstract static class CSVRegisterDialectNode extends PythonBuiltinNode {

        @Specialization
        PNone register(VirtualFrame frame, PythonModule module, Object nameObj, Object dialectObj, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode nameNode,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached CallNode callNode,
                        @Cached PyDictSetItem setItem) {

            TruffleString name;
            try {
                name = nameNode.execute(inliningTarget, nameObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_STRING, "dialect name");
            }

            Object result = callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, keywords);

            // TODO GR-38165: unchecked cast to PDict
            PDict dialects = (PDict) readNode.execute(module, T__DIALECTS);

            setItem.execute(frame, inliningTarget, dialects, name, result);

            return PNone.NONE;
        }
    }

    @Builtin(name = "unregister_dialect", parameterNames = {"$mod",
                    "name"}, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "Delete the name/dialect mapping associated with a string name.\n" +
                                    "csv.unregister_dialect(name)")
    @GenerateNodeFactory
    public abstract static class CSVUnregisterDialectNode extends PythonBuiltinNode {
        @Specialization
        PNone unregister(VirtualFrame frame, PythonModule module, Object nameObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached PyDictDelItem delItem,
                        @Cached HashingStorageGetItem getItem) {

            // TODO GR-38165: unchecked cast to PDict
            PDict dialects = (PDict) readNode.execute(module, T__DIALECTS);

            if (getItem.hasKey(frame, inliningTarget, (dialects).getDictStorage(), nameObj)) {
                delItem.execute(frame, inliningTarget, dialects, nameObj);
            } else {
                throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.UNKNOWN_DIALECT);
            }

            return PNone.NONE;
        }
    }

    @Builtin(name = "get_dialect", parameterNames = {"$mod", "name"}, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "Return the dialect instance associated with name.\n" +
                    "dialect = csv.get_dialect(name)")
    @GenerateNodeFactory
    public abstract static class CSVGetDialectNode extends PythonBuiltinNode {

        public abstract CSVDialect execute(VirtualFrame frame, PythonModule module, Object name);

        @NeverDefault
        protected static CSVGetDialectNode create() {
            return CSVModuleBuiltinsFactory.CSVGetDialectNodeFactory.create(null);
        }

        @Specialization
        CSVDialect get(VirtualFrame frame, PythonModule module, Object nameObj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyDictGetItem getItemNode,
                        @Cached ReadAttributeFromObjectNode readNode) {

            // TODO GR-38165: unchecked cast to PDict
            PDict dialects = (PDict) readNode.execute(module, T__DIALECTS);

            CSVDialect dialect = (CSVDialect) getItemNode.execute(frame, inliningTarget, dialects, nameObj);

            if (dialect == null) {
                throw raise(PythonBuiltinClassType.CSVError, ErrorMessages.UNKNOWN_DIALECT);
            }

            return dialect;
        }
    }

    @Builtin(name = "list_dialects", parameterNames = {"$mod"}, declaresExplicitSelf = true, doc = "Return a list of all known dialect names.\n" +
                    "names = csv.list_dialects()")
    @GenerateNodeFactory
    public abstract static class CSVListDialectsNode extends PythonBuiltinNode {
        @Specialization
        PList listDialects(VirtualFrame frame, PythonModule module,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached ListNodes.ConstructListNode constructListNode) {

            Object dialects = readNode.execute(module, T__DIALECTS);
            return constructListNode.execute(frame, dialects);
        }
    }

    @Builtin(name = "reader", doc = READER_DOC, parameterNames = {"csvfile", "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CSVReaderNode extends PythonBuiltinNode {
        @Specialization
        Object createReader(VirtualFrame frame, Object csvfile, Object dialectObj, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached CallNode callNode) {
            Object inputIter = getIter.execute(frame, inliningTarget, csvfile);
            CSVDialect dialect = (CSVDialect) callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, kwargs);
            return factory().createCSVReader(PythonBuiltinClassType.CSVReader, inputIter, dialect);
        }
    }

    @Builtin(name = "writer", doc = WRITER_DOC, parameterNames = {"outputfile", "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CSVWriterNode extends PythonBuiltinNode {
        @Specialization
        Object createReader(VirtualFrame frame, Object outputFile, Object dialectObj, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyCallableCheckNode checkCallable) {
            Object write = lookupAttr.execute(frame, inliningTarget, outputFile, T_WRITE);
            if (write == PNone.NO_VALUE || !checkCallable.execute(inliningTarget, write)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_MUST_HAVE_WRITE_METHOD, "argument 1");
            }
            CSVDialect dialect = (CSVDialect) callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, kwargs);
            return factory().createCSVWriter(PythonBuiltinClassType.CSVWriter, write, dialect);
        }
    }

    @Builtin(name = "field_size_limit", parameterNames = {"$mod", "limit"}, declaresExplicitSelf = true, doc = "Sets an upper limit on parsed fields.\n" +
                    "csv.field_size_limit([limit])\n\n" +
                    "Returns old limit. If limit is not given, no new limit is set and\n" +
                    "the old limit is returned")
    @GenerateNodeFactory
    public abstract static class CSVFieldSizeLimitNode extends PythonBuiltinNode {

        @Specialization
        long getOrSetFieldSizeLimit(VirtualFrame frame, PythonModule self, Object newLimit,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongCheckExactNode checkLongNode,
                        @Cached PyLongAsLongNode castToLong) {

            CSVModuleBuiltins csvModuleBuiltins = (CSVModuleBuiltins) self.getBuiltins();
            long oldLimit = csvModuleBuiltins.fieldLimit;

            if (newLimit != PNone.NO_VALUE) {
                if (!checkLongNode.execute(inliningTarget, newLimit)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_INTEGER, "limit");
                }
                csvModuleBuiltins.fieldLimit = castToLong.execute(frame, inliningTarget, newLimit);
            }
            return oldLimit;
        }
    }

    @Builtin(name = "CSVDialect", constructsClass = PythonBuiltinClassType.CSVDialect, parameterNames = {"class", "dialect", "delimiter", "doublequote", "escapechar", "lineterminator", "quotechar",
                    "quoting", "skipinitialspace", "strict"})
    @GenerateNodeFactory
    public abstract static class DialectNode extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doCSVDialectWithoutKeywords(PythonBuiltinClassType cls, CSVDialect dialect, PNone delimiter, PNone doublequote, PNone escapechar,
                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace, PNone strict) {
            return dialect;
        }

        @Specialization
        @SuppressWarnings("unused")
        CSVDialect doStringWithoutKeywords(VirtualFrame frame, PythonBuiltinClassType cls, TruffleString dialectName, PNone delimiter, PNone doublequote, PNone escapechar,
                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace,
                        PNone strict,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect) {
            PythonModule module = getCore().lookupBuiltinModule(T__CSV);
            return getDialect.execute(frame, module, dialectName);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doNoDialectObj(VirtualFrame frame, PythonBuiltinClassType cls, @SuppressWarnings("unused") PNone dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode) {
            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, TruffleString dialectName, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode) {
            PythonModule module = getCore().lookupBuiltinModule(T__CSV);
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
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doDialectClassWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, PythonClass dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                        Object strictObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectLookupAttr getFirstAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getSecondAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getThirdAttributesNode,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode) {

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
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doPStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, PString dialectName, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode) {

            TruffleString dialectNameStr = castToStringNode.execute(inliningTarget, dialectName);
            PythonModule module = getCore().lookupBuiltinModule(T__CSV);
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
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization(guards = {"!isCSVDialect(dialectObj)", "!isPythonClass(dialectObj)", "!isString(dialectObj)", "!isPNone(dialectObj)"})
        @SuppressWarnings("truffle-static-method")
        Object doGeneric(VirtualFrame frame, PythonBuiltinClassType cls, Object dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectLookupAttr getFirstAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getSecondAttributesNode,
                        @Exclusive @Cached PyObjectLookupAttr getThirdAttributesNode,
                        @Exclusive @Cached PyObjectIsTrueNode isTrueNode,
                        @Exclusive @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Exclusive @Cached PyLongAsIntNode pyLongAsIntNode) {

            delimiterObj = getAttributeValue(frame, inliningTarget, dialectObj, delimiterObj, T_ATTR_DELIMITER, getFirstAttributesNode);
            doublequoteObj = getAttributeValue(frame, inliningTarget, dialectObj, doublequoteObj, T_ATTR_DOUBLEQUOTE, getFirstAttributesNode);
            escapecharObj = getAttributeValue(frame, inliningTarget, dialectObj, escapecharObj, T_ATTR_ESCAPECHAR, getFirstAttributesNode);
            lineterminatorObj = getAttributeValue(frame, inliningTarget, dialectObj, lineterminatorObj, T_ATTR_LINETERMINATOR, getSecondAttributesNode);
            quotingObj = getAttributeValue(frame, inliningTarget, dialectObj, quotingObj, T_ATTR_QUOTING, getSecondAttributesNode);
            quotecharObj = getAttributeValue(frame, inliningTarget, dialectObj, quotecharObj, T_ATTR_QUOTECHAR, getSecondAttributesNode);
            skipinitialspaceObj = getAttributeValue(frame, inliningTarget, dialectObj, skipinitialspaceObj, T_ATTR_SKIPINITIALSPACE, getThirdAttributesNode);
            strictObj = getAttributeValue(frame, inliningTarget, dialectObj, strictObj, T_ATTR_STRICT, getThirdAttributesNode);

            return createCSVDialect(frame, inliningTarget, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj,
                            quotecharObj, quotingObj, skipinitialspaceObj, strictObj, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        protected static boolean isCSVDialect(Object dialect) {
            return dialect instanceof CSVDialect;
        }

        private Object createCSVDialect(VirtualFrame frame, Node inliningTarget, PythonBuiltinClassType cls, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj,
                        Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        PyObjectIsTrueNode isTrueNode, PyLongCheckExactNode pyLongCheckExactNode, PyLongAsIntNode pyLongAsIntNode) {
            TruffleString delimiter = getChar(T_ATTR_DELIMITER, delimiterObj, T_COMMA, false);
            boolean doubleQuote = getBoolean(frame, inliningTarget, doublequoteObj, true, isTrueNode);
            TruffleString escapeChar = getChar(T_ATTR_ESCAPECHAR, escapecharObj, T_NOT_SET, true);
            TruffleString lineTerminator = getString(T_ATTR_LINETERMINATOR, lineterminatorObj, T_CRLF);
            TruffleString quoteChar = getChar(T_ATTR_QUOTECHAR, quotecharObj, T_DOUBLE_QUOTE, true);
            QuoteStyle quoting = getQuotingValue(frame, inliningTarget, T_ATTR_QUOTING, quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipInitialSpace = getBoolean(frame, inliningTarget, skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame, inliningTarget, strictObj, false, isTrueNode);
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }
            return createCSVDialect(cls, delimiter, doubleQuote, escapeChar, lineTerminator, quoteChar, quoting, skipInitialSpace, strict);
        }

        @TruffleBoundary
        private Object createCSVDialect(PythonBuiltinClassType cls, TruffleString delimiter, boolean doubleQuote, TruffleString escapeChar, TruffleString lineTerminator, TruffleString quoteChar,
                        QuoteStyle quoting, boolean skipInitialSpace, boolean strict) {
            if (TruffleString.EqualNode.getUncached().execute(delimiter, T_NOT_SET, TS_ENCODING)) {
                throw raise(TypeError, ErrorMessages.DELIMITER_MUST_BE_ONE_CHAR_STRING);
            }

            if (quoting != QUOTE_NONE && TruffleString.EqualNode.getUncached().execute(quoteChar, T_NOT_SET, TS_ENCODING)) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.QUOTECHAR_MUST_BE_SET_IF_QUOTING_ENABLED);
            }

            if (lineTerminator == null) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.LINETERMINATOR_MUST_BE_SET);
            }

            // delimiter cannot be NOT_SET
            int delimiterCodePoint = TruffleString.CodePointAtIndexNode.getUncached().execute(delimiter, 0, TS_ENCODING);
            int escapeCharCodePoint = TruffleString.EqualNode.getUncached().execute(escapeChar, T_NOT_SET, TS_ENCODING) ? NOT_SET_CODEPOINT
                            : TruffleString.CodePointAtIndexNode.getUncached().execute(escapeChar, 0, TS_ENCODING);
            int quoteCharCodePoint = TruffleString.EqualNode.getUncached().execute(quoteChar, T_NOT_SET, TS_ENCODING) ? NOT_SET_CODEPOINT
                            : TruffleString.CodePointAtIndexNode.getUncached().execute(quoteChar, 0, TS_ENCODING);

            return PythonObjectFactory.getUncached().createCSVDialect(cls, delimiter, delimiterCodePoint, doubleQuote,
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
        private TruffleString getChar(TruffleString name, Object valueObj, TruffleString defaultValue, boolean optional) {
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
                throw raise(TypeError, optional ? ErrorMessages.S_MUST_BE_STRING_OR_NONE_NOT_S : ErrorMessages.S_MUST_BE_STRING_NOT_S, name, GetClassNode.executeUncached(valueObj));
            }

            if (optional && TruffleString.EqualNode.getUncached().execute(charValue, T_NOT_SET, TS_ENCODING)) {
                return T_NOT_SET;
            }

            if (TruffleString.CodePointLengthNode.getUncached().execute(charValue, TS_ENCODING) > 1) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.MUST_BE_ONE_CHARACTER_STRING, name);
            }

            // CPython supports empty quotechars and escapechars until inclusive 3.10.
            if (charValue.isEmpty()) {
                return T_NOT_SET;
            }

            return charValue;
        }

        private static boolean getBoolean(VirtualFrame frame, Node inliningTarget, Object valueObj, boolean defaultValue, PyObjectIsTrueNode isTrueNode) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            return isTrueNode.execute(frame, inliningTarget, valueObj);
        }

        @TruffleBoundary
        private TruffleString getString(TruffleString attribute, Object valueObj, TruffleString defaultValue) {
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
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.MUST_BE_STRING_QUOTED, attribute);
            }

            return value;
        }

        private QuoteStyle getQuotingValue(VirtualFrame frame, Node inliningTarget, TruffleString name, Object valueObj, QuoteStyle defaultValue,
                        PyLongCheckExactNode pyLongCheckExactNode,
                        PyLongAsIntNode pyLongAsIntNode) {

            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            if (valueObj instanceof QuoteStyle) {
                return (QuoteStyle) valueObj;
            }

            if (!pyLongCheckExactNode.execute(inliningTarget, valueObj)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_INTEGER_QUOTED_ATTR, name);
            }

            int value = pyLongAsIntNode.execute(frame, inliningTarget, valueObj);

            if (!QuoteStyle.containsOrdinalValue(value)) {
                throw raise(TypeError, ErrorMessages.BAD_QUOTING_VALUE);
            }

            return QuoteStyle.getQuoteStyle(value);
        }

    }

    private static final String CSV_DOC = "CSV parsing and writing.\n" +
                    "\n" +
                    "This module provides classes that assist in the reading and writing\n" +
                    "of Comma Separated Value (CSV) files, and implements the interface\n" +
                    "described by PEP 305.  Although many CSV files are simple to parse,\n" +
                    "the format is not formally defined by a stable specification and\n" +
                    "is subtle enough that parsing lines of a CSV file with something\n" +
                    "like line.split(\",\") is bound to fail.  The module supports three\n" +
                    "basic APIs: reading, writing, and registration of dialects.\n" +
                    "\n" +
                    "\n" +
                    "DIALECT REGISTRATION:\n" +
                    "\n" +
                    "Readers and writers support a dialect argument, which is a convenient\n" +
                    "handle on a group of settings.  When the dialect argument is a string,\n" +
                    "it identifies one of the dialects previously registered with the module.\n" +
                    "If it is a class or instance, the attributes of the argument are used as\n" +
                    "the settings for the reader or writer:\n" +
                    "\n" +
                    "    class excel:\n" +
                    "        delimiter = ','\n" +
                    "        quotechar = '\"'\n" +
                    "        escapechar = None\n" +
                    "        doublequote = True\n" +
                    "        skipinitialspace = False\n" +
                    "        lineterminator = '\\r\\n'\n" +
                    "        quoting = QUOTE_MINIMAL\n" +
                    "\n" +
                    "SETTINGS:\n" +
                    "\n" +
                    "    * quotechar - specifies a one-character string to use as the\n" +
                    "        quoting character.  It defaults to '\"'.\n" +
                    "    * delimiter - specifies a one-character string to use as the\n" +
                    "        field separator.  It defaults to ','.\n" +
                    "    * skipinitialspace - specifies how to interpret whitespace which\n" +
                    "        immediately follows a delimiter.  It defaults to False, which\n" +
                    "        means that whitespace immediately following a delimiter is part\n" +
                    "        of the following field.\n" +
                    "    * lineterminator -  specifies the character sequence which should\n" +
                    "        terminate rows.\n" +
                    "    * quoting - controls when quotes should be generated by the writer.\n" +
                    "        It can take on any of the following module constants:\n" +
                    "\n" +
                    "        csv.QUOTE_MINIMAL means only when required, for example, when a\n" +
                    "            field contains either the quotechar or the delimiter\n" +
                    "        csv.QUOTE_ALL means that quotes are always placed around fields.\n" +
                    "        csv.QUOTE_NONNUMERIC means that quotes are always placed around\n" +
                    "            fields which do not parse as integers or floating point\n" +
                    "            numbers.\n" +
                    "        csv.QUOTE_NONE means that quotes are never placed around fields.\n" +
                    "    * escapechar - specifies a one-character string used to escape\n" +
                    "        the delimiter when quoting is set to QUOTE_NONE.\n" +
                    "    * doublequote - controls the handling of quotes inside fields.  When\n" +
                    "        True, two consecutive quotes are interpreted as one during read,\n" +
                    "        and when writing, each quote character embedded in the data is\n" +
                    "        written as two quotes\n";

    private static final String READER_DOC = "\n" +
                    "csv_reader = reader(iterable [, dialect='excel']\n" +
                    "                    [optional keyword args])\n" +
                    "for row in csv_reader:\n" +
                    "process(row)\n" +
                    "\n" +
                    "The \"iterable\" argument can be any object that returns a line\n" +
                    "of input for each iteration, such as a file object or a list.  The\n" +
                    "optional \"dialect\" parameter is discussed below.  The function\n" +
                    "also accepts optional keyword arguments which override settings\n" +
                    "provided by the dialect.\n" +
                    "\n" +
                    "The returned object is an iterator.  Each iteration returns a row\n" +
                    "of the CSV file (which can span multiple input lines)";

    private static final String WRITER_DOC = "    csv_writer = csv.writer(fileobj [, dialect='excel']\n" +
                    "                            [optional keyword args])\n" +
                    "    for row in sequence:\n" +
                    "        csv_writer.writerow(row)\n" +
                    "\n" +
                    "    [or]\n" +
                    "\n" +
                    "    csv_writer = csv.writer(fileobj [, dialect='excel']\n" +
                    "                            [optional keyword args])\n" +
                    "    csv_writer.writerows(rows)\n" +
                    "\n" +
                    "The \"fileobj\" argument can be any object that supports the file API.\n";
}
