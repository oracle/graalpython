/* Copyright (c) 2020, 2021, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.csv;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

import java.util.List;

@CoreFunctions(defineModule = "_csv")
public class CSVModuleBuiltins extends PythonBuiltins {

    static long fieldLimit = 128 * 1024; // max parsed field size
    public static final String WRITE = "write";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        builtinConstants.put(SpecialAttributeNames.__DOC__, CSV_DOC);
        builtinConstants.put("_dialects", PythonObjectFactory.getUncached().createDict());
        super.initialize(core);
    }

    @Builtin(name = "register_dialect", parameterNames = {"name",
            "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true, doc = "Create a mapping from a string name to a dialect class.\n" +
            "dialect = csv.register_dialect(name, dialect)")
    @GenerateNodeFactory
    public abstract static class CSVRegisterDialectNode extends PythonBuiltinNode {

        @Specialization
        PNone register(VirtualFrame frame, Object nameObj, Object dialectObj, PKeyword[] keywords,
                              @Cached CastToJavaStringNode nameNode,
                              @Cached ReadAttributeFromObjectNode readNode,
                              @Cached CallNode callNode,
                              @CachedLibrary(limit = "1") HashingStorageLibrary library) {

            String name;
            try {
                name = nameNode.execute(nameObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_STRING, "dialect name");
            }

            Object result = callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, keywords);

            PythonModule module = getCore().lookupBuiltinModule("_csv");
            Object dialects = readNode.execute(module, "_dialects");

            // TODO: Write PyDictSetItem Node?
            HashingStorage storage = library.setItem(((PDict) dialects).getDictStorage(), name, result);
            ((PDict) dialects).setDictStorage(storage);

            return PNone.NONE;
        }
    }

    @Builtin(name = "unregister_dialect", parameterNames = {"name"}, minNumOfPositionalArgs = 1, doc = "Delete the name/dialect mapping associated with a string name.\n" +
            "csv.unregister_dialect(name)")
    @GenerateNodeFactory
    public abstract static class CSVUnregisterDialectNode extends PythonBuiltinNode {
        @Specialization
        PNone unregister(Object nameObj,
                                @Cached ReadAttributeFromObjectNode readNode,
                                @CachedLibrary(limit = "1") HashingStorageLibrary library) {

            PythonModule module = getCore().lookupBuiltinModule("_csv");
            Object dialects = readNode.execute(module, "_dialects");

            //TODO: Should we write a PyDict_DelItem Node?
            if (library.hasKey(((PDict) dialects).getDictStorage(), nameObj)) {
                library.delItem(((PDict) dialects).getDictStorage(), nameObj);
            } else {
                throw raise(PythonBuiltinClassType.CSVError, "unknown dialect");
            }

            return PNone.NONE;
        }
    }

    @Builtin(name = "get_dialect", parameterNames = {"name"}, minNumOfPositionalArgs = 1, doc = "Return the dialect instance associated with name.\n" +
            "dialect = csv.get_dialect(name)")
    @GenerateNodeFactory
    public abstract static class CSVGetDialectNode extends PythonBuiltinNode {

        public abstract CSVDialect execute(VirtualFrame frame, Object name);

        protected static CSVGetDialectNode create() {
            return CSVModuleBuiltinsFactory.CSVGetDialectNodeFactory.create(null);
        }
        @Specialization
        CSVDialect get(VirtualFrame frame, Object nameObj,
                       @Cached PyDictGetItem getItemNode,
                       @Cached ReadAttributeFromObjectNode readNode) {

            PythonModule module = getCore().lookupBuiltinModule("_csv");
            PDict dialects = (PDict) readNode.execute(module, "_dialects");

            CSVDialect dialect = (CSVDialect) getItemNode.execute(frame, dialects, nameObj);

            if (dialect == null) {
                throw raise(PythonBuiltinClassType.CSVError, "unknown dialect");
            }

            return dialect;
        }
    }

    @Builtin(name = "list_dialects", doc = "Return a list of all known dialect names.\n" +
            "names = csv.list_dialects()")
    @GenerateNodeFactory
    public abstract static class CSVListDialectsNode extends PythonBuiltinNode {
        @Specialization
        PList listDialects(VirtualFrame frame,
                         @Cached ReadAttributeFromObjectNode readNode,
                         @CachedLibrary(limit = "1") HashingStorageLibrary library,
                         @Cached ListNodes.ConstructListNode constructListNode) {

            PythonModule module = getCore().lookupBuiltinModule("_csv");
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
                            @Cached CSVModuleBuiltins.CSVCallDialectNode callDialect) {

            CSVReader reader = pythonObjectFactory.createCSVReader(PythonBuiltinClassType.CSVReader);

            reader.fieldSize = 0;
            reader.lineNum = 0;

            reader.parseReset();

            reader.inputIter = getIter.execute(frame, csvfile);
            reader.dialect = (CSVDialect) callDialect.execute(frame, dialectObj, kwargs);

            return reader;
        }
    }

    @Builtin(name = "writer", doc = WRITER_DOC , parameterNames = {"outputfile", "dialect"}, minNumOfPositionalArgs = 1, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CSVWriterNode extends PythonBuiltinNode {
        @Specialization
        Object createReader(VirtualFrame frame, Object outputFile, Object dialectObj, PKeyword[] kwargs,
                            @Cached PythonObjectFactory pythonObjectFactory,
                            @Cached CSVModuleBuiltins.CSVCallDialectNode callDialect,
                            @Cached PyObjectLookupAttr lookupAttr,
                            @Cached PyCallableCheckNode checkCallable) {

            CSVWriter writer = pythonObjectFactory.createCSVWriter(PythonBuiltinClassType.CSVWriter);

            writer.rec = "";
            writer.recSize = 0;
            writer.joinReset();

            writer.write = lookupAttr.execute(frame, outputFile, WRITE);

            if (writer.write == PNone.NO_VALUE || !checkCallable.execute(writer.write)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.S_MUST_HAVE_WRITE_METHOD, "argument 1");
            }

            writer.dialect = (CSVDialect) callDialect.execute(frame, dialectObj, kwargs);

            return writer;
        }
    }

    @Builtin(name = "field_size_limit", parameterNames = {"limit"}, doc = "Sets an upper limit on parsed fields.\n" +
            "csv.field_size_limit([limit])\n\n" +
            "Returns old limit. If limit is not given, no new limit is set and\n" +
            "the old limit is returned")
    @GenerateNodeFactory
    public abstract static class CSVFieldSizeLimitNode extends PythonBuiltinNode {
        @Specialization
        long getOrSetFieldSizeLimit(VirtualFrame frame, Object newLimit,
                                    @Cached PyLongCheckExactNode checkIntNode,
                                    @Cached PyLongAsLongNode castToLong) {

            long oldLimit = fieldLimit;

            if (newLimit != PNone.NO_VALUE) {
                if (!PyLongCheckExactNode.getUncached().execute(newLimit)) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_INTEGER, "limit");
                }

                fieldLimit = castToLong.execute(frame, newLimit);
            }

            return oldLimit;
        }
    }

    @Builtin(name = "_call_dialect", isPublic = false, parameterNames = {"dialect_inst", "kwargs"}, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CSVCallDialectNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object callDialectWithKeywordsPDict(VirtualFrame frame, Object dialectObj, PDict keywords,
                           @Cached CallNode callNode) {
            return callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, ((KeywordsStorage) keywords.getDictStorage()).getStore());
        }

        @Specialization
        Object callDialectWithKeywordsArray(VirtualFrame frame, Object dialectObj, PKeyword[] keywords,
                           @Cached CallNode callNode) {
            return callNode.execute(frame, PythonBuiltinClassType.CSVDialect, new Object[]{dialectObj}, keywords);
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

    private static final String WRITER_DOC =
        "    csv_writer = csv.writer(fileobj [, dialect='excel']\n" +
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