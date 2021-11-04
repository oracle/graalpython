package com.oracle.graal.python.builtins.modules.csv;


import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

import java.util.List;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVDialect)
public class CSVDialectBuiltins extends PythonBuiltins {

    public static final int QUOTE_MINIMAL = 0;
    public static final int QUOTE_ALL = 1;
    public static final int QUOTE_NONNUMERIC = 2;
    public static final int QUOTE_NONE = 3;

    private static final String NOT_SET = "NOT_SET";
    private static final String EOL = "EOL";

    // Todo: enum + isDefined?

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVDialectBuiltinsFactory.getFactories();
    }

    // Todo: Does a non fitting keyword argument automatically throw a TypeError?
    // # TypeError if we have an unknown Keyword Argument
    @Builtin(name = "CSVDialect", constructsClass = PythonBuiltinClassType.CSVDialect,
            parameterNames = {"class", "dialect", "delimiter", "doublequote", "escapechar", "lineterminator", "quotechar",
                    "quoting", "skipinitialspace", "strict"})
    @GenerateNodeFactory
    public abstract static class DialectNode extends PythonBuiltinNode {

        @Specialization
        Object doDialectWithoutKeywords(PythonBuiltinClassType cls, CSVDialect dialect, PNone delimiter, PNone doublequote, PNone escapechar,
                                        PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace,
                                        PNone strict) {
            return dialect;
        }

        @Specialization
        Object doStringWithoutKeywords(PythonBuiltinClassType cls, String dialectName, PNone delimiter, PNone doublequote, PNone escapechar,
                                       PNone lineterminator, PNone quotechar, PNone quoting, PNone skipinitialspace,
                                       PNone strict,
                                       @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                                       @Cached CastToJavaStringNode nameNode,
                                       @Cached ReadAttributeFromObjectNode readNode,
                                       @CachedLibrary(limit = "1") HashingStorageLibrary library) {
            return getDialect.get(dialectName, nameNode, readNode, library);
        }

        @Specialization(guards = {"!isCSVDialect(dialectObj)", "!isString(dialectObj)"})
        Object doGenericDialectWithoutKeywords(VirtualFrame frame, PythonBuiltinClassType cls, Object dialectObj, PNone delimiterObj, PNone doublequoteObj, PNone escapecharObj,
                                               PNone lineterminatorObj, PNone quotecharObj, PNone quotingObj, PNone skipinitialspaceObj,
                                               PNone strictObj,
                                               @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                                               @Cached GetClassNode getClassNode,
                                               @Cached IsSubtypeNode isSubtypeNode,
                                               @Cached PyObjectLookupAttr getAttributeNode,
                                               @Cached CastToJavaStringNode castToJavaStringNode,
                                               @Cached ReadAttributeFromObjectNode readNode,
                                               @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                                               @Cached PyLongCheckExactNode pyLongCheckExactNode,
                                               @Cached PyLongAsIntNode pyLongAsIntNode,
                                               @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                                               @CachedLibrary(limit = "1") HashingStorageLibrary library) {

            // TODO: What if dialect itself is a class ?
            Object clazz = getClassNode.execute(dialectObj);
            CSVDialect dialect;
            // TODO: Can Subtype Check be moved to guard?P
            if (isSubtypeNode.execute(clazz, PythonBuiltinClassType.CSVDialect)) { //isSubTypeNode + getClassNode
                dialect = (CSVDialect) dialectObj;
            } else {
                Object delimiterInput = getAttributeValueFromDialect(frame, dialectObj, "delimiter", getAttributeNode);
                Object doublequoteInput = getAttributeValueFromDialect(frame, dialectObj, "doublequote", getAttributeNode);
                Object escapecharInput = getAttributeValueFromDialect(frame, dialectObj, "escapechar", getAttributeNode);
                Object lineterminatorInput = getAttributeValueFromDialect(frame, dialectObj, "lineterminator", getAttributeNode);
                Object quotingInput = getAttributeValueFromDialect(frame, dialectObj, "quoting", getAttributeNode);
                Object quotecharInput = getAttributeValueFromDialect(frame, dialectObj, "quotechar", getAttributeNode);
                Object skipinitialspaceInput = getAttributeValueFromDialect(frame, dialectObj, "skipinitialspace", getAttributeNode);
                Object strictInput = getAttributeValueFromDialect(frame, dialectObj, "strict", getAttributeNode);

                String delimiter = getChar("delimiter", delimiterInput, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
                boolean doublequote = getBoolean("doublequote", doublequoteInput, true, castToJavaBooleanNode);
                String escapechar = getCharOrNone("escapechar", escapecharInput, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
                String lineterminator = getString("lineterminator", lineterminatorInput, "\r\n", castToJavaStringNode);
                String quotechar = getCharOrNone("quotechar", quotecharInput, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
                int quoting = getInt(frame,"quoting", quotingInput, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
                boolean skipinitialspace = getBoolean("skipinitalspace", skipinitialspaceInput, false, castToJavaBooleanNode);
                boolean strict = getBoolean("strict", strictInput, false, castToJavaBooleanNode);

                dialect = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            }

            return dialect;
        }

        @Specialization
        CSVDialect doDialectWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, CSVDialect dialectObj, Object delimiterObj, Object
                doublequoteObj, Object escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj,
                                         Object skipinitialspaceObj, Object strictObj,
                                         @Cached PyObjectLookupAttr getAttributeNode,
                                         @Cached GetClassNode getClassNode,
                                         @Cached CastToJavaStringNode castToJavaStringNode,
                                         @Cached ReadAttributeFromObjectNode readNode,
                                         @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                                         @Cached PyLongCheckExactNode pyLongCheckExactNode,
                                         @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                                         @Cached PyLongAsIntNode pyLongAsIntNode) {

            Object delimiterInput = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
            Object doublequoteInput = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
            Object escapecharInput = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
            Object lineterminatorInput = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
            Object quotingInput = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
            Object quotecharInput = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
            Object skipinitialspaceInput = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
            Object strictInput = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);

            String delimiter = getChar("delimiter", delimiterInput, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean("doublequote", doublequoteInput, true, castToJavaBooleanNode);
            String escapechar = getCharOrNone("escapechar", escapecharInput, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorInput, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharInput, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getInt(frame,"quoting", quotingInput, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean("skipinitalspace", skipinitialspaceInput, false, castToJavaBooleanNode);
            boolean strict = getBoolean("strict", strictInput, false, castToJavaBooleanNode);

            CSVDialect dialect = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            return dialect;
        }

        @Specialization
        Object doStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, String dialectName, Object delimiterObj, Object
                doublequoteObj, Object escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                                    @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                                    @Cached ReadAttributeFromObjectNode readNode,
                                    @Cached GetClassNode getClassNode,
                                    @CachedLibrary(limit = "1") HashingStorageLibrary library,
                                    @Cached PyObjectLookupAttr getAttributeNode,
                                    @Cached CastToJavaStringNode castToJavaStringNode,
                                    @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                                    @Cached PyLongCheckExactNode pyLongCheckExactNode,
                                    @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                                    @Cached PyLongAsIntNode pyLongAsIntNode) {

            Object dialectObj = getDialect.get(dialectName, castToJavaStringNode, readNode, library);

            Object delimiterInput = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
            Object doublequoteInput = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
            Object escapecharInput = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
            Object lineterminatorInput = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
            Object quotingInput = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
            Object quotecharInput = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
            Object skipinitialspaceInput = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
            Object strictInput = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);

            String delimiter = getChar("delimiter", delimiterInput, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean("doublequote", doublequoteInput, true, castToJavaBooleanNode);
            String escapechar = getCharOrNone("escapechar", escapecharInput, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorInput, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharInput, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getInt(frame,"quoting", quotingInput, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean("skipinitalspace", skipinitialspaceInput, false, castToJavaBooleanNode);
            boolean strict = getBoolean("strict", strictInput, false, castToJavaBooleanNode);

            dialectObj = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            return dialectObj;
        }

        @Specialization(guards = {"!isCSVDialect(dialectObj)", "!isString(dialectObj)"})
        Object doGeneric(VirtualFrame frame, PythonBuiltinClassType cls, Object dialectObj, Object delimiterObj, Object doublequoteObj, Object
                escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                         Object strictObj,
                         @Cached GetClassNode getClassNode,
                         @Cached PyObjectLookupAttr getAttributeNode,
                         @Cached CastToJavaStringNode castToJavaStringNode,
                         @Cached CastToJavaBooleanNode castToJavaBooleanNode,
                         @Cached PyLongCheckExactNode pyLongCheckExactNode,
                         @Cached PyLongAsIntNode pyLongAsIntNode,
                         @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode) {

            // TODO: should we check dialectObj before accessing here in any way?
            if (!(dialectObj instanceof PNone)) {
                delimiterObj = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
                doublequoteObj = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
                escapecharObj = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
                lineterminatorObj = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
                quotingObj = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
                quotecharObj = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
                skipinitialspaceObj = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
                strictObj = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);
            }

            String delimiter = getChar("delimiter", delimiterObj, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean("doublequote", doublequoteObj, true, castToJavaBooleanNode);
            String escapechar = getCharOrNone("escapechar", escapecharObj, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorObj, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharObj, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getInt(frame,"quoting", quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean("skipinitalspace", skipinitialspaceObj, false, castToJavaBooleanNode);
            boolean strict = getBoolean("strict", strictObj, false, castToJavaBooleanNode);

            Object dialect = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            return dialect;
        }

        protected boolean isCSVDialect(Object dialect) {
            return dialect instanceof CSVDialect;
        }

        private Object getAttributeValue(VirtualFrame frame, Object dialect, Object inputValue, String attributeName, PyObjectLookupAttr getAttributeNode) {
            if (inputValue != PNone.NO_VALUE) return inputValue;
            return getAttributeValueFromDialect(frame, dialect, attributeName, getAttributeNode);
        }

        private Object getAttributeValueFromDialect(VirtualFrame frame, Object dialect, String attributeName, PyObjectLookupAttr getAttributeNode) {
            return getAttributeNode.execute(frame, dialect, attributeName);
        }

        private String getChar(String name, Object valueObj, String defaultValue,
                               PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                               GetClassNode getType,
                               CastToJavaStringNode castToJavaStringNode) {
            if (valueObj == PNone.NO_VALUE) return defaultValue;

            String charValue;

            // TODO: Add implementation for PyUnicodeCheck?
            // #define PyUnicode_Check(op) \
            //                 PyType_FastSubclass(Py_TYPE(op), Py_TPFLAGS_UNICODE_SUBCLASS)
//            if (pyUnicodeCheckExactNode.execute(valueObj)) {
//                // TODO: Get actual class name?
//                throw raise(TypeError, ErrorMessages.S_MUST_BE_STRING_NOT_S, name, getType.execute(valueObj));
//            }

            try {
                charValue = castToJavaStringNode.execute(valueObj);
            } catch ( CannotCastException e) {
                throw raise(TypeError, ErrorMessages.S_MUST_BE_STRING_NOT_S, name, getType.execute(valueObj));
            }

            if (charValue.length() != 1) {
                throw raise(TypeError, ErrorMessages.MUST_BE_ONE_CHARACTER_STRING, name);
            }

            return charValue;
        }

        private String getCharOrNone(String attribute, Object valueObj, String defaultValue,
                                     PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                                     GetClassNode getType,
                                     CastToJavaStringNode castToJavaStringNode) {
            if (valueObj == PNone.NO_VALUE) return defaultValue;
            if (valueObj == PNone.NONE) return NOT_SET;

            return getChar(attribute, valueObj, defaultValue, pyUnicodeCheckExactNode, getType, castToJavaStringNode);
        }

        private boolean getBoolean(String attribute, Object value, boolean defaultValue, CastToJavaBooleanNode castToJavaBooleanNode) {
            if (value == PNone.NO_VALUE) return defaultValue;

            return castToJavaBooleanNode.execute(value);
        }

        private String getString(String attribute, Object value, String defaultValue, CastToJavaStringNode castToJavaStringNode) {
            if (value == PNone.NO_VALUE) return defaultValue;

            return castToJavaStringNode.execute(value);
        }

        private int getInt(VirtualFrame frame, String name, Object valueObj, int defaultValue,
                           PyLongCheckExactNode pyLongCheckExactNode,
                           PyLongAsIntNode pyLongAsIntNode) {
            // TODO: IS lossy cast ok here?
            if (valueObj == PNone.NO_VALUE) return defaultValue;

            int value;

            if (!pyLongCheckExactNode.execute(valueObj)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_INTEGER, name);
            }

            value = pyLongAsIntNode.execute(frame, valueObj);
            return value;
        }

    }

    // TODO: Add doc information ?

    @Builtin(name = "delimiter", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DelimiterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.getDelimiter().equals(NOT_SET) ? PNone.NONE : self.getDelimiter();
        }
    }

    @Builtin(name = "doublequote", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DoublequoteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.isDoublequote();
        }
    }

    @Builtin(name = "escapechar", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EscapecharNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.getEscapechar().equals(NOT_SET) ? PNone.NONE : self.getEscapechar();
        }
    }

    @Builtin(name = "lineterminator", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class LineterminatorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static String doIt(CSVDialect self) {
            return self.getLineterminator();
        }
    }

    @Builtin(name = "quotechar", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QuotecharNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.getDelimiter().equals(NOT_SET) ? PNone.NONE : self.getQuotechar();
        }
    }

    @Builtin(name = "quoting", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QuotingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int doIt(CSVDialect self) {
            return self.getQuoting();
        }
    }

    @Builtin(name = "skipinitialspace", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SkipinitialspaceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.isSkipinitialspace();
        }
    }

    @Builtin(name = "strict", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StrictNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.isStrict();
        }
    }

}