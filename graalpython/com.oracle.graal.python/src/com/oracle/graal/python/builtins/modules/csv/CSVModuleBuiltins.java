/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
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
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_csv")
public final class CSVModuleBuiltins extends PythonBuiltins {

    static final String WRITE = "write";
    static final String NOT_SET = "NOT_SET";
    static final int NOT_SET_CODEPOINT = -1;

    long fieldLimit = 128 * 1024; // max parsed field size

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, CSV_DOC);
        builtinConstants.put("__version__", "1.0");
        builtinConstants.put("QUOTE_MINIMAL", QUOTE_MINIMAL.ordinal());
        builtinConstants.put("QUOTE_ALL", QUOTE_ALL.ordinal());
        builtinConstants.put("QUOTE_NONNUMERIC", QUOTE_NONNUMERIC.ordinal());
        builtinConstants.put("QUOTE_NONE", QUOTE_NONE.ordinal());
        builtinConstants.put("_dialects", core.factory().createDict());
        super.initialize(core);
    }

    @Builtin(name = "register_dialect", parameterNames = {"$mod", "name",
                    "dialect"}, minNumOfPositionalArgs = 2, takesVarKeywordArgs = true, declaresExplicitSelf = true, doc = "Create a mapping from a string name to a dialect class.\n" +
                                    "dialect = csv.register_dialect(name, dialect)")
    @GenerateNodeFactory
    public abstract static class CSVRegisterDialectNode extends PythonBuiltinNode {

        @Specialization
        PNone register(VirtualFrame frame, PythonModule module, Object nameObj, Object dialectObj, PKeyword[] keywords,
                        @Cached CastToJavaStringNode nameNode,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached CallNode callNode,
                        @Cached PyDictSetItem setItem) {

            String name;
            try {
                name = nameNode.execute(nameObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_STRING, "dialect name");
            }

            Object result = callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, keywords);

            PDict dialects = (PDict) readNode.execute(module, "_dialects");

            setItem.execute(frame, dialects, name, result);

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
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached PyDictDelItem delItem,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hashingStorage) {

            PDict dialects = (PDict) readNode.execute(module, "_dialects");

            if (hashingStorage.hasKey((dialects).getDictStorage(), nameObj)) {
                delItem.execute(frame, dialects, nameObj);
            } else {
                throw raise(PythonBuiltinClassType.CSVError, "unknown dialect");
            }

            return PNone.NONE;
        }
    }

    @Builtin(name = "get_dialect", parameterNames = {"$mod", "name"}, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, doc = "Return the dialect instance associated with name.\n" +
                    "dialect = csv.get_dialect(name)")
    @GenerateNodeFactory
    public abstract static class CSVGetDialectNode extends PythonBuiltinNode {

        public abstract CSVDialect execute(VirtualFrame frame, PythonModule module, Object name);

        protected static CSVGetDialectNode create() {
            return CSVModuleBuiltinsFactory.CSVGetDialectNodeFactory.create(null);
        }

        @Specialization
        CSVDialect get(VirtualFrame frame, PythonModule module, Object nameObj,
                        @Cached PyDictGetItem getItemNode,
                        @Cached ReadAttributeFromObjectNode readNode) {

            PDict dialects = (PDict) readNode.execute(module, "_dialects");

            CSVDialect dialect = (CSVDialect) getItemNode.execute(frame, dialects, nameObj);

            if (dialect == null) {
                throw raise(PythonBuiltinClassType.CSVError, "unknown dialect");
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

            Object dialects = readNode.execute(module, "_dialects");
            return constructListNode.execute(frame, dialects);
        }
    }

    @Builtin(name = "reader", doc = READER_DOC, parameterNames = {"csvfile", "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CSVReaderNode extends PythonBuiltinNode {
        @Specialization
        Object createReader(VirtualFrame frame, Object csvfile, Object dialectObj, PKeyword[] kwargs,
                        @Cached PythonObjectFactory pythonObjectFactory,
                        @Cached PyObjectGetIter getIter,
                        @Cached CallNode callNode) {

            CSVReader reader = pythonObjectFactory.createCSVReader(PythonBuiltinClassType.CSVReader);

            reader.fieldSize = 0;
            reader.lineNum = 0;

            reader.parseReset();

            reader.inputIter = getIter.execute(frame, csvfile);
            reader.dialect = (CSVDialect) callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, kwargs);

            return reader;
        }
    }

    @Builtin(name = "writer", doc = WRITER_DOC, parameterNames = {"outputfile", "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CSVWriterNode extends PythonBuiltinNode {
        @Specialization
        Object createReader(VirtualFrame frame, Object outputFile, Object dialectObj, PKeyword[] kwargs,
                        @Cached PythonObjectFactory pythonObjectFactory,
                        @Cached CallNode callNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyCallableCheckNode checkCallable) {

            CSVWriter writer = pythonObjectFactory.createCSVWriter(PythonBuiltinClassType.CSVWriter);

            writer.recSize = 0;
            writer.joinReset();

            writer.write = lookupAttr.execute(frame, outputFile, WRITE);

            if (writer.write == PNone.NO_VALUE || !checkCallable.execute(writer.write)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_MUST_HAVE_WRITE_METHOD, "argument 1");
            }

            writer.dialect = (CSVDialect) callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, kwargs);

            return writer;
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
                        @Cached PyLongCheckExactNode checkLongNode,
                        @Cached PyLongAsLongNode castToLong) {

            CSVModuleBuiltins csvModuleBuiltins = (CSVModuleBuiltins) self.getBuiltins();
            long oldLimit = csvModuleBuiltins.fieldLimit;

            if (newLimit != PNone.NO_VALUE) {
                if (!checkLongNode.execute(newLimit)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_INTEGER, "limit");
                }
                csvModuleBuiltins.fieldLimit = castToLong.execute(frame, newLimit);
            }
            return oldLimit;
        }
    }

    @Builtin(name = "CSVDialect", constructsClass = PythonBuiltinClassType.CSVDialect, parameterNames = {"class", "dialect", "delimiter", "doublequote", "escapechar", "lineterminator", "quotechar",
                    "quoting", "skipinitialspace", "strict"})
    @GenerateNodeFactory
    public abstract static class DialectNode extends PythonBuiltinNode {

        @Specialization
        Object doCSVDialectWithoutKeywords(PythonBuiltinClassType cls, CSVDialect dialect, PNone delimiter, PNone doublequote, PNone escapechar,
                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace,
                        PNone strict) {
            return dialect;
        }

        @Specialization
        CSVDialect doStringWithoutKeywords(VirtualFrame frame, PythonBuiltinClassType cls, String dialectName, PNone delimiter, PNone doublequote, PNone escapechar,
                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace,
                        PNone strict,
                        @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached PyDictGetItem getItemNode) {
            PythonModule module = getCore().lookupBuiltinModule("_csv");
            return getDialect.get(frame, module, dialectName, getItemNode, readNode);
        }

        @Specialization
        Object doNoDialectObj(VirtualFrame frame, PythonBuiltinClassType cls, PNone dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                        Object strictObj,
                        @Cached GetClassNode getClassNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyLongAsIntNode pyLongAsIntNode) {

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                            getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization
        Object doStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, String dialectName, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PyDictGetItem getItemNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyLongAsIntNode pyLongAsIntNode) {
            PythonModule module = getCore().lookupBuiltinModule("_csv");
            CSVDialect dialectObj = getDialect.get(frame, module, dialectName, getItemNode, readNode);

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

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                            getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization
        Object doDialectClassWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, PythonClass dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj,
                        Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                        Object strictObj,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr getFirstAttributesNode,
                        @Cached PyObjectLookupAttr getSecondAttributesNode,
                        @Cached PyObjectLookupAttr getThirdAttributesNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyLongAsIntNode pyLongAsIntNode) {

            // We use multiple AttributeNodes to be able to cache all attributes as current
            // CACHE_SIZE is 3.
            delimiterObj = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getFirstAttributesNode);
            doublequoteObj = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getFirstAttributesNode);
            escapecharObj = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getFirstAttributesNode);
            lineterminatorObj = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getSecondAttributesNode);
            quotecharObj = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getSecondAttributesNode);
            quotingObj = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getSecondAttributesNode);
            skipinitialspaceObj = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getThirdAttributesNode);
            strictObj = getAttributeValue(frame, dialectObj, strictObj, "strict", getThirdAttributesNode);

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                            getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization
        Object doPStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, PString dialectName, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PyDictGetItem getItemNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyLongAsIntNode pyLongAsIntNode) {

            String dialectNameStr = castToJavaStringNode.execute(dialectName);
            PythonModule module = getCore().lookupBuiltinModule("_csv");
            CSVDialect dialectObj = getDialect.get(frame, module, dialectNameStr, getItemNode, readNode);

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

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                            getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        @Specialization(guards = {"!isCSVDialect(dialectObj)", "!isPythonClass(dialectObj)", "!isString(dialectObj)", "!isPNone(dialectObj)"})
        Object doGeneric(VirtualFrame frame, PythonBuiltinClassType cls, Object dialectObj, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj,
                        Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                        Object strictObj,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr getAttributeNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyLongAsIntNode pyLongAsIntNode) {

            delimiterObj = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
            doublequoteObj = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
            escapecharObj = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
            lineterminatorObj = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
            quotingObj = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
            quotecharObj = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
            skipinitialspaceObj = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
            strictObj = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                            getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode);
        }

        protected static boolean isCSVDialect(Object dialect) {
            return dialect instanceof CSVDialect;
        }

        private Object createCSVDialect(VirtualFrame frame, PythonBuiltinClassType cls, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj, Object quotecharObj,
                        Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                        @Cached GetClassNode getClassNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyLongAsIntNode pyLongAsIntNode) {

            String delimiter = getChar("delimiter", delimiterObj, ",", getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean(frame, "doublequote", doublequoteObj, true, isTrueNode);
            String escapechar = getCharOrNone("escapechar", escapecharObj, NOT_SET, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorObj, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharObj, "\"", getClassNode, castToJavaStringNode);
            QuoteStyle quoting = getQuotingValue(frame, "quoting", quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean(frame, "skipinitalspace", skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame, "strict", strictObj, false, isTrueNode);

            /* validate options */

            if (delimiter.equals(NOT_SET)) {
                throw raise(TypeError, ErrorMessages.DELIMITER_MUST_BE_ONE_CHAR_STRING);
            }

            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }

            if (quoting != QUOTE_NONE && quotechar.equals(NOT_SET)) {
                throw raise(TypeError, ErrorMessages.QUOTECHAR_MUST_BE_SET_IF_QUOTING_ENABLED);
            }

            if (lineterminator == null) {
                throw raise(TypeError, ErrorMessages.LINETERMINATOR_MUST_BE_SET);
            }

            return factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);
        }

        private Object getAttributeValue(VirtualFrame frame, Object dialect, Object inputValue, String attributeName, PyObjectLookupAttr getAttributeNode) {
            if (inputValue != PNone.NO_VALUE) {
                return inputValue;
            }
            return getAttributeValueFromDialect(frame, dialect, attributeName, getAttributeNode);
        }

        private Object getAttributeValueFromDialect(VirtualFrame frame, Object dialect, String attributeName, PyObjectLookupAttr getAttributeNode) {
            return getAttributeNode.execute(frame, dialect, attributeName);
        }

        private String getChar(String name, Object valueObj, String defaultValue,
                        GetClassNode getType,
                        CastToJavaStringNode castToJavaStringNode) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            String charValue;

            try {
                charValue = castToJavaStringNode.execute(valueObj);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.S_MUST_BE_STRING_NOT_S, name, getType.execute(valueObj));
            }

            if (charValue.length() > 1 && charValue.codePointCount(0, charValue.length()) > 1) {
                throw raise(TypeError, ErrorMessages.MUST_BE_ONE_CHARACTER_STRING, name);
            }

            // CPython supports empty quotechars and escapechars until 3.10.
            if (charValue.length() == 0) {
                return NOT_SET;
            }

            return charValue;
        }

        private String getCharOrNone(String attribute, Object valueObj, String defaultValue,
                        GetClassNode getType,
                        CastToJavaStringNode castToJavaStringNode) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }
            if (valueObj == PNone.NONE || PythonUtils.equals(valueObj, NOT_SET)) {
                return NOT_SET;
            }

            return getChar(attribute, valueObj, defaultValue, getType, castToJavaStringNode);
        }

        private boolean getBoolean(VirtualFrame frame, String attributeName, Object valueObj, boolean defaultValue, PyObjectIsTrueNode isTrueNode) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            return isTrueNode.execute(frame, valueObj);
        }

        private String getString(String attribute, Object valueObj, String defaultValue, CastToJavaStringNode castToJavaStringNode) {
            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            if (valueObj == PNone.NONE) {
                return null;
            }

            String value;

            try {
                value = castToJavaStringNode.execute(valueObj);
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRING_QUOTED, attribute);
            }

            return value;
        }

        private QuoteStyle getQuotingValue(VirtualFrame frame, String name, Object valueObj, QuoteStyle defaultValue,
                        PyLongCheckExactNode pyLongCheckExactNode,
                        PyLongAsIntNode pyLongAsIntNode) {

            if (valueObj == PNone.NO_VALUE) {
                return defaultValue;
            }

            if (valueObj instanceof QuoteStyle) {
                return (QuoteStyle) valueObj;
            }

            if (!pyLongCheckExactNode.execute(valueObj)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_INTEGER_QUOTED_ATTR, name);
            }

            int value = pyLongAsIntNode.execute(frame, valueObj);

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
