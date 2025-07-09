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

import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_ALL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_MINIMAL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONE;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NONNUMERIC;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_NOTNULL;
import static com.oracle.graal.python.builtins.modules.csv.QuoteStyle.QUOTE_STRINGS;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
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
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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

    static final String J__CSV = "_csv";
    static final TruffleString T__CSV = tsLiteral(J__CSV);

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
        addBuiltinConstant("QUOTE_STRINGS", QUOTE_STRINGS.ordinal());
        addBuiltinConstant("QUOTE_NOTNULL", QUOTE_NOTNULL.ordinal());
        addBuiltinConstant(T__DIALECTS, PFactory.createDict(core.getLanguage()));
        super.initialize(core);
    }

    @Builtin(name = "register_dialect", parameterNames = {"$mod", "name",
                    "dialect"}, minNumOfPositionalArgs = 2, takesVarKeywordArgs = true, declaresExplicitSelf = true, doc = "Create a mapping from a string name to a dialect class.\n" +
                                    "dialect = csv.register_dialect(name, dialect)")
    @GenerateNodeFactory
    public abstract static class CSVRegisterDialectNode extends PythonBuiltinNode {

        @Specialization
        static PNone register(VirtualFrame frame, PythonModule module, Object nameObj, Object dialectObj, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode nameNode,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached CallNode callNode,
                        @Cached PyDictSetItem setItem,
                        @Cached PRaiseNode raiseNode) {
            TruffleString name;
            try {
                name = nameNode.execute(inliningTarget, nameObj);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_STRING, "dialect name");
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
        static PNone unregister(VirtualFrame frame, PythonModule module, Object nameObj,
                        @Bind Node inliningTarget,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached PyDictDelItem delItem,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PRaiseNode raiseNode) {

            // TODO GR-38165: unchecked cast to PDict
            PDict dialects = (PDict) readNode.execute(module, T__DIALECTS);

            if (getItem.hasKey(frame, inliningTarget, (dialects).getDictStorage(), nameObj)) {
                delItem.execute(frame, inliningTarget, dialects, nameObj);
            } else {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.UNKNOWN_DIALECT);
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
        static CSVDialect get(VirtualFrame frame, PythonModule module, Object nameObj,
                        @Bind Node inliningTarget,
                        @Cached PyDictGetItem getItemNode,
                        @Cached ReadAttributeFromObjectNode readNode,
                        @Cached PRaiseNode raiseNode) {

            // TODO GR-38165: unchecked cast to PDict
            PDict dialects = (PDict) readNode.execute(module, T__DIALECTS);

            CSVDialect dialect = (CSVDialect) getItemNode.execute(frame, inliningTarget, dialects, nameObj);

            if (dialect == null) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.CSVError, ErrorMessages.UNKNOWN_DIALECT);
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
        static Object createReader(VirtualFrame frame, Object csvfile, Object dialectObj, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached CallNode callNode,
                        @Bind PythonLanguage language) {
            Object inputIter = getIter.execute(frame, inliningTarget, csvfile);
            CSVDialect dialect = (CSVDialect) callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, kwargs);
            return PFactory.createCSVReader(language, inputIter, dialect);
        }
    }

    @Builtin(name = "writer", doc = WRITER_DOC, parameterNames = {"outputfile", "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CSVWriterNode extends PythonBuiltinNode {
        @Specialization
        static Object createReader(VirtualFrame frame, Object outputFile, Object dialectObj, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyCallableCheckNode checkCallable,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            Object write = lookupAttr.execute(frame, inliningTarget, outputFile, T_WRITE);
            if (write == PNone.NO_VALUE || !checkCallable.execute(inliningTarget, write)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_MUST_HAVE_WRITE_METHOD, "argument 1");
            }
            CSVDialect dialect = (CSVDialect) callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, kwargs);
            return PFactory.createCSVWriter(language, write, dialect);
        }
    }

    @Builtin(name = "field_size_limit", parameterNames = {"$mod", "limit"}, declaresExplicitSelf = true, doc = "Sets an upper limit on parsed fields.\n" +
                    "csv.field_size_limit([limit])\n\n" +
                    "Returns old limit. If limit is not given, no new limit is set and\n" +
                    "the old limit is returned")
    @GenerateNodeFactory
    public abstract static class CSVFieldSizeLimitNode extends PythonBuiltinNode {

        @Specialization
        static long getOrSetFieldSizeLimit(VirtualFrame frame, PythonModule self, Object newLimit,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckExactNode checkLongNode,
                        @Cached PyLongAsLongNode castToLong,
                        @Cached PRaiseNode raiseNode) {
            CSVModuleBuiltins csvModuleBuiltins = (CSVModuleBuiltins) self.getBuiltins();
            long oldLimit = csvModuleBuiltins.fieldLimit;

            if (newLimit != PNone.NO_VALUE) {
                if (!checkLongNode.execute(inliningTarget, newLimit)) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_INTEGER, "limit");
                }
                csvModuleBuiltins.fieldLimit = castToLong.execute(frame, inliningTarget, newLimit);
            }
            return oldLimit;
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
                    "        csv.QUOTE_STRINGS means that quotes are always placed around\n" +
                    "            fields which are strings.  Note that the Python value None\n" +
                    "            is not a string.\n" +
                    "        csv.QUOTE_NOTNULL means that quotes are only placed around fields\n" +
                    "            that are not the Python value None.\n" +
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
