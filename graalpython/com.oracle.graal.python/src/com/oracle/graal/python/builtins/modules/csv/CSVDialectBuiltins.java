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

    // TODO: Implement as enum? => Checks for getQuotingValue?
    public static final int QUOTE_MINIMAL = 0;
    public static final int QUOTE_ALL = 1;
    public static final int QUOTE_NONNUMERIC = 2;
    public static final int QUOTE_NONE = 3;

    private static final String NOT_SET = "NOT_SET";
    private static final String EOL = "EOL";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVDialectBuiltinsFactory.getFactories();
    }

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

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                    getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, pyUnicodeCheckExactNode);
        }


        @Specialization
        Object doStringWithKeywords(VirtualFrame frame, PythonBuiltinClassType cls, String dialectName, Object delimiterObj, Object
                doublequoteObj, Object escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
                                    @Cached CSVModuleBuiltins.CSVGetDialectNode getDialect,
                                    @Cached ReadAttributeFromObjectNode readNode,
                                    @Cached GetClassNode getClassNode,
                                    @Cached PyDictGetItem getItemNode,
                                    @Cached CastToJavaStringNode castToJavaStringNode,
                                    @Cached PyObjectIsTrueNode isTrueNode,
                                    @Cached PyLongCheckExactNode pyLongCheckExactNode,
                                    @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode,
                                    @Cached PyLongAsIntNode pyLongAsIntNode) {

            CSVDialect dialectObj = getDialect.get(frame, dialectName, getItemNode, readNode);

            if (delimiterObj == PNone.NO_VALUE) delimiterObj = dialectObj.getDelimiter();
            if (doublequoteObj == PNone.NO_VALUE) doublequoteObj = dialectObj.isDoublequote();
            if (escapecharObj == PNone.NO_VALUE) escapecharObj = dialectObj.getEscapechar();
            if (lineterminatorObj == PNone.NO_VALUE) lineterminatorObj = dialectObj.getLineterminator();
            if (quotingObj == PNone.NO_VALUE) quotingObj = dialectObj.getQuoting();
            if (quotecharObj == PNone.NO_VALUE) quotecharObj = dialectObj.getQuotechar();
            if (skipinitialspaceObj == PNone.NO_VALUE) skipinitialspaceObj = dialectObj.isSkipinitialspace();
            if (strictObj == PNone.NO_VALUE) strictObj = dialectObj.isStrict();

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                    getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, pyUnicodeCheckExactNode);
        }

        @Specialization
        Object doDialectClass(VirtualFrame frame, PythonBuiltinClassType cls, PythonClass dialectObj, Object delimiterObj, Object doublequoteObj, Object
                escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj,
                              Object strictObj,
                              @Cached GetClassNode getClassNode,
                              @Cached PyObjectLookupAttr getFirstAttributesNode,
                              @Cached PyObjectLookupAttr getSecondAttributesNode,
                              @Cached PyObjectLookupAttr getThirdAttributesNode,
                              @Cached CastToJavaStringNode castToJavaStringNode,
                              @Cached PyObjectIsTrueNode isTrueNode,
                              @Cached PyLongCheckExactNode pyLongCheckExactNode,
                              @Cached PyLongAsIntNode pyLongAsIntNode,
                              @Cached PyUnicodeCheckExactNode pyUnicodeCheckExactNode) {

            delimiterObj = getAttributeValue(frame, dialectObj, delimiterObj, "delimiter", getFirstAttributesNode);
            doublequoteObj = getAttributeValue(frame, dialectObj, doublequoteObj, "doublequote", getFirstAttributesNode);
            escapecharObj = getAttributeValue(frame, dialectObj, escapecharObj, "escapechar", getFirstAttributesNode);
            lineterminatorObj = getAttributeValue(frame, dialectObj, lineterminatorObj, "lineterminator", getSecondAttributesNode);
            quotecharObj = getAttributeValue(frame, dialectObj, quotecharObj, "quotechar", getSecondAttributesNode);
            quotingObj = getAttributeValue(frame, dialectObj, quotingObj, "quoting", getSecondAttributesNode);
            skipinitialspaceObj = getAttributeValue(frame, dialectObj, skipinitialspaceObj, "skipinitialspace", getThirdAttributesNode);
            strictObj = getAttributeValue(frame, dialectObj, strictObj, "strict", getThirdAttributesNode);

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                    getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, pyUnicodeCheckExactNode);
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

            return createCSVDialect(frame, cls, delimiterObj, doublequoteObj, escapecharObj, lineterminatorObj, quotecharObj, quotingObj, skipinitialspaceObj, strictObj,
                    getClassNode, castToJavaStringNode, isTrueNode, pyLongCheckExactNode, pyLongAsIntNode, pyUnicodeCheckExactNode);
        }

        protected boolean isCSVDialect(Object dialect) {
            return dialect instanceof CSVDialect;
        }

        private Object createCSVDialect(VirtualFrame frame, PythonBuiltinClassType cls, Object delimiterObj, Object doublequoteObj, Object escapecharObj, Object lineterminatorObj, Object quotecharObj, Object quotingObj, Object skipinitialspaceObj, Object strictObj,
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
            int quoting = getQuotingValue(frame, "quoting", quotingObj, QUOTE_MINIMAL, pyLongCheckExactNode, pyLongAsIntNode);
            boolean skipinitialspace = getBoolean(frame, "skipinitalspace", skipinitialspaceObj, false, isTrueNode);
            boolean strict = getBoolean(frame, "strict", strictObj, false, isTrueNode);

            /* validate options */

            if (delimiter == NOT_SET) {
                throw raise(TypeError, ErrorMessages.DELIMITER_MUST_BE_ONE_CHAR_STRING);
            }

            // TODO: Does this match cpython version?
            /*if (quotechar == Py_None && quoting == NULL)
                self->quoting = QUOTE_NONE;
            * */
            if (quotecharObj == PNone.NONE && quotingObj == PNone.NO_VALUE) {
                quoting = QUOTE_NONE;
            }

            if (quoting != QUOTE_NONE && quotechar == NOT_SET) {
                throw raise(TypeError, ErrorMessages.QUOTECHAR_MUST_BE_SET_IF_QUOTING_ENABLED);
            }

            if (lineterminator == null) {
                throw raise(TypeError, ErrorMessages.LINETERMINATOR_MUST_BE_SET);
            }

            return factory().createCSVDialect(cls, delimiter, doublequote, escapechar, lineterminator, quotechar, quoting, skipinitialspace, strict);
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
//                throw raise(TypeError, ErrorMessages.S_MUST_BE_STRING_NOT_S, name, getType.execute(valueObj));
//            }

            try {
                charValue = castToJavaStringNode.execute(valueObj);
            } catch (CannotCastException e) {
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
            if (valueObj == PNone.NONE || valueObj == NOT_SET) return NOT_SET;

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
            } catch (CannotCastException e) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRING, attribute);
            }

            return value;
        }

        private int getQuotingValue(VirtualFrame frame, String name, Object valueObj, int defaultValue,
                                    PyLongCheckExactNode pyLongCheckExactNode,
                                    PyLongAsIntNode pyLongAsIntNode) {

            if (valueObj == PNone.NO_VALUE) return defaultValue;

            if (!pyLongCheckExactNode.execute(valueObj)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_INTEGER, name);
            }

            int value = pyLongAsIntNode.execute(frame, valueObj);

            if (value < 0 || value > 3) {
                throw raise(TypeError, ErrorMessages.BAD_QUOTING_VALUE);
            }

            return value;
        }

    }

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