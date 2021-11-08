package com.oracle.graal.python.builtins.modules.csv;


import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
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

import java.util.List;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVDialect)
public class CSVDialectBuiltins extends PythonBuiltins {

    public static final int QUOTE_MINIMAL = 0;
    public static final int QUOTE_ALL = 1;
    public static final int QUOTE_NONNUMERIC = 2;
    public static final int QUOTE_NONE = 3;

    // TODO: Does it make sense to use shorter / other marker values to make equality checks cheaper?
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
        Object doNoDialectObj(VirtualFrame frame, PythonBuiltinClassType cls, PNone dialectObj, Object delimiterObj, Object doublequoteObj, Object
                escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                         Object strictObj,
                         @Cached GetClassNode getClassNode,
                         @Cached CastToJavaStringNode castToJavaStringNode,
                         @Cached PyObjectIsTrueNode isTrueNode,
                         @Cached PyLongCheckExactNode pyLongCheckExactNode,
                         @Cached PyLongAsIntNode pyLongAsIntNode,
                         @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode) {

            String delimiter = getChar("delimiter", delimiterObj, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean(frame, "doublequote", doublequoteObj, true, isTrueNode);
            String escapechar = getCharOrNone("escapechar", escapecharObj, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorObj, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharObj, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getQuotingValue(frame,"quoting", quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean(frame,"skipinitalspace", skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame, "strict", strictObj, false, isTrueNode);

            if (delimiter.equals(NOT_SET)) {
                throw raise(TypeError, "\"delimiter\" must be a 1-character string");
            }

            // TODO: Does this match cpython version?
            /*if (quotechar == Py_None && quoting == NULL)
                self->quoting = QUOTE_NONE;
            * */
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }

            if (quoting != QUOTE_NONE && quotechar == NOT_SET) {
                throw raise(TypeError, "quotechar must be set if quoting enabled");
            }

            if (lineterminator == null) {
                throw raise(TypeError, "lineterminator must be set");
            }

            Object dialect = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            return dialect;
        }


        @Specialization
        Object doStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, String dialectName, Object delimiterObj, Object
                doublequoteObj, Object escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                                    @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                                    @Cached ReadAttributeFromObjectNode readNode,
                                    @Cached GetClassNode getClassNode,
                                    @Cached PyDictGetItem getItemNode,
                                    @Cached PyObjectLookupAttr getAttributeNode,
                                    @Cached CastToJavaStringNode castToJavaStringNode,
                                    @Cached PyObjectIsTrueNode isTrueNode,
                                    @Cached PyLongCheckExactNode pyLongCheckExactNode,
                                    @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                                   @Cached PyLongAsIntNode pyLongAsIntNode) {

            Object dialectObj = getDialect.get(frame, dialectName, getItemNode, readNode);

            // TODO: As we only store CSVDialects it should be possible to avoid possibly expensive getAttribute Calls.

            Object delimiterInput = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
            Object doublequoteInput = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
            Object escapecharInput = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
            Object lineterminatorInput = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
            Object quotingInput = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
            Object quotecharInput = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
            Object skipinitialspaceInput = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
            Object strictInput = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);

            String delimiter = getChar("delimiter", delimiterInput, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean(frame,"doublequote", doublequoteInput, true, isTrueNode);
            String escapechar = getCharOrNone("escapechar", escapecharInput, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorInput, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharInput, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getQuotingValue(frame, "quoting", quotingInput, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean(frame,"skipinitalspace", skipinitialspaceInput, false, isTrueNode);
            boolean strict = getBoolean(frame,"strict", strictInput, false, isTrueNode);

            if (delimiter.equals(NOT_SET)) {
                throw raise(TypeError, "\"delimiter\" must be a 1-character string");
            }

            // TODO: Does this match cpython version?
            /*if (quotechar == Py_None && quoting == NULL)
                self->quoting = QUOTE_NONE;
            * */
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }

            if (quoting != QUOTE_NONE && quotechar == NOT_SET) {
                throw raise(TypeError, "quotechar must be set if quoting enabled");
            }

            if (lineterminator == null) {
                throw raise(TypeError, "lineterminator must be set");
            }

            dialectObj = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            return dialectObj;
        }

        @Specialization
        Object doDialectClass(VirtualFrame frame, PythonBuiltinClassType cls, PythonClass dialectObj, Object delimiterObj, Object doublequoteObj, Object
                escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                              Object strictObj,
                              @Cached GetClassNode getClassNode,
                              @Cached PyObjectLookupAttr getAttributeNode,
                              @Cached CastToJavaStringNode castToJavaStringNode,
                              @Cached PyObjectIsTrueNode isTrueNode,
                              @Cached PyLongCheckExactNode pyLongCheckExactNode,
                              @Cached PyLongAsIntNode pyLongAsIntNode,
                              @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode) {

            delimiterObj = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
            doublequoteObj = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
            escapecharObj = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
            lineterminatorObj = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
            quotingObj = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
            quotecharObj = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
            skipinitialspaceObj = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
            strictObj = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);

            String delimiter = getChar("delimiter", delimiterObj, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean(frame,"doublequote", doublequoteObj, true, isTrueNode);
            String escapechar = getCharOrNone("escapechar", escapecharObj, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorObj, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharObj, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getQuotingValue(frame,"quoting", quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean(frame,"skipinitalspace", skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame,"strict", strictObj, false, isTrueNode);

            if (delimiter.equals(NOT_SET)) {
                throw raise(TypeError, "\"delimiter\" must be a 1-character string");
            }

            // TODO: Does this match cpython version?
            /*if (quotechar == Py_None && quoting == NULL)
                self->quoting = QUOTE_NONE;
            * */
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }

            if (quoting != QUOTE_NONE && quotechar == NOT_SET) {
                throw raise(TypeError, "quotechar must be set if quoting enabled");
            }

            if (lineterminator == null) {
                throw raise(TypeError, "lineterminator must be set");
            }

            Object dialect = factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);

            return dialect;
        }

        @Specialization(guards = {"!isPythonClass(dialectObj)", "!isString(dialectObj)", "!isPNone(dialectObj)"})
        Object doGeneric(VirtualFrame frame, PythonBuiltinClassType cls, Object dialectObj, Object delimiterObj, Object doublequoteObj, Object
                escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                         Object strictObj,
                         @Cached GetClassNode getClassNode,
                         @Cached PyObjectLookupAttr getAttributeNode,
                         @Cached CastToJavaStringNode castToJavaStringNode,
                         @Cached PyObjectIsTrueNode isTrueNode,
                         @Cached PyLongCheckExactNode pyLongCheckExactNode,
                         @Cached PyLongAsIntNode pyLongAsIntNode,
                         @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode) {

            // TODO: should we check dialectObj before accessing here in any way?

            delimiterObj = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getAttributeNode);
            doublequoteObj = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getAttributeNode);
            escapecharObj = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getAttributeNode);
            lineterminatorObj = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getAttributeNode);
            quotingObj = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getAttributeNode);
            quotecharObj = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getAttributeNode);
            skipinitialspaceObj = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getAttributeNode);
            strictObj = getAttributeValue(frame, dialectObj, strictObj, "strict", getAttributeNode);

            String delimiter = getChar("delimiter", delimiterObj, ",", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            boolean doublequote = getBoolean(frame,"doublequote", doublequoteObj, true, isTrueNode);
            String escapechar = getCharOrNone("escapechar", escapecharObj, NOT_SET, pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            String lineterminator = getString("lineterminator", lineterminatorObj, "\r\n", castToJavaStringNode);
            String quotechar = getCharOrNone("quotechar", quotecharObj, "\"", pyUnicodeCheckExactNode, getClassNode, castToJavaStringNode);
            int quoting = getQuotingValue(frame,"quoting", quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean(frame,"skipinitalspace", skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame,"strict", strictObj, false, isTrueNode);

            /* validate options */

            if (delimiter.equals(NOT_SET)) {
                throw raise(TypeError, "\"delimiter\" must be a 1-character string");
            }

            // TODO: Does this match cpython version?
            /*if (quotechar == Py_None && quoting == NULL)
                self->quoting = QUOTE_NONE;
            * */
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }

            if (quoting != QUOTE_NONE && quotechar == NOT_SET) {
                throw raise(TypeError, "quotechar must be set if quoting enabled");
            }

            if (lineterminator == null) {
                throw raise(TypeError, "lineterminator must be set");
            }

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
            return getAttributeNode.execute(frame, dialect, attributeName) ;
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

        private boolean getBoolean(VirtualFrame frame, String attributeName, Object valueObj, boolean defaultValue, PyObjectIsTrueNode isTrueNode) {
            if (valueObj == PNone.NO_VALUE) return defaultValue;

            return isTrueNode.execute(frame, valueObj);
        }

        private String getString(String attribute, Object valueObj, String defaultValue, CastToJavaStringNode castToJavaStringNode) {
            if (valueObj == PNone.NO_VALUE) return defaultValue;
            if (valueObj == PNone.NONE) return null;

            String value;

            // TODO: Implement PyUnicodeCheck Node instead? (Not PyUnicodeCheckExact!)
            try {
                value = castToJavaStringNode.execute(valueObj);
            } catch(CannotCastException e) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRING, attribute);
            }

            return value;
        }

        private int getQuotingValue(VirtualFrame frame, String name, Object valueObj, int defaultValue,
                           PyLongCheckExactNode pyLongCheckExactNode,
                           PyLongAsIntNode pyLongAsIntNode) {
            // TODO: IS lossy cast ok here?
            if (valueObj == PNone.NO_VALUE) return defaultValue;

            int value;

            if (!pyLongCheckExactNode.execute(valueObj)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_INTEGER, name);
            }

            value = pyLongAsIntNode.execute(frame, valueObj);

            if (value < 0 || value > 3) {
                throw raise(TypeError, "bad \"quoting\" value");
            }
            
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