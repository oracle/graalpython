/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.JSONScanner)
public final class JSONScannerBuiltins extends PythonBuiltins {

    public static final TruffleString T_JSON_DECODE_ERROR = tsLiteral("JSONDecodeError");

    static final class IntRef {
        int value;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JSONScannerBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "string", "idx"})
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "idx", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CallScannerNode extends PythonTernaryClinicBuiltinNode {

        // TODO: do we need these nodes, they are all used behind TruffleBoundary
        @Child private CallUnaryMethodNode callParseFloat = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callParseInt = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callParseConstant = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callObjectHook = CallUnaryMethodNode.create();
        @Child private CallUnaryMethodNode callObjectPairsHook = CallUnaryMethodNode.create();
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @CompilationFinal private Shape tupleInstanceShape;
        @CompilationFinal private Shape listInstanceShape;
        @CompilationFinal private Shape dictInstanceShape;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JSONScannerBuiltinsClinicProviders.CallScannerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        protected PTuple call(PJSONScanner self, TruffleString string, int idx,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            if (tupleInstanceShape == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tupleInstanceShape = PythonLanguage.get(this).getBuiltinTypeInstanceShape(PythonBuiltinClassType.PTuple);
            }
            if (listInstanceShape == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                listInstanceShape = PythonLanguage.get(this).getBuiltinTypeInstanceShape(PythonBuiltinClassType.PList);
            }
            if (dictInstanceShape == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dictInstanceShape = PythonLanguage.get(this).getBuiltinTypeInstanceShape(PythonBuiltinClassType.PDict);
            }
            IntRef nextIdx = new IntRef();
            Object result = scanOnceUnicode(self, toJavaStringNode.execute(string), idx, nextIdx);
            return factory.createTuple(new Object[]{result, nextIdx.value});
        }

        @TruffleBoundary
        private Object parseObjectUnicode(PJSONScanner scanner, String string, int start, IntRef nextIdx) {
            /*
             * Read a JSON object from PyUnicode pystr. idx is the index of the first character
             * after the opening curly brace. nextIdx is a return-by-reference index to the first
             * character after the closing curly brace.
             *
             * Returns a new PyObject (usually a dict, but object_hook can change that)
             */
            boolean hasPairsHook = scanner.objectPairsHook != PNone.NONE;

            int idx = start;
            int length = string.length();

            ObjectSequenceStorage listStorage = null;
            EconomicMapStorage mapStorage = null;
            if (hasPairsHook) {
                listStorage = new ObjectSequenceStorage(4);
            } else {
                mapStorage = EconomicMapStorage.create();
            }

            /* skip whitespace after { */
            idx = skipWhitespace(string, idx, length);

            /* only loop if the object is non-empty */
            if (idx >= length || string.charAt(idx) != '}') {
                while (true) {

                    /* read key */
                    if (idx >= length || string.charAt(idx) != '"') {
                        throw decodeError(this, string, idx, ErrorMessages.EXPECTING_PROP_NAME_ECLOSED_IN_DBL_QUOTES);
                    }
                    TruffleString newKey = scanStringUnicode(string, idx + 1, scanner.strict, nextIdx, this);
                    TruffleString key = scanner.memo.putIfAbsent(newKey, newKey);
                    if (key == null) {
                        key = newKey;
                    }
                    idx = nextIdx.value;

                    /* skip whitespace between key and : delimiter, read :, skip whitespace */
                    idx = skipWhitespace(string, idx, length);
                    if (idx >= length || string.charAt(idx) != ':') {
                        throw decodeError(this, string, idx, ErrorMessages.EXPECTING_COLON_DELIMITER);
                    }
                    idx = skipWhitespace(string, idx + 1, length);

                    /* read any JSON term */
                    Object val = scanOnceUnicode(scanner, string, idx, nextIdx);
                    idx = nextIdx.value;

                    if (hasPairsHook) {
                        listStorage.insertItem(listStorage.length(), factory.createTuple(PythonBuiltinClassType.PTuple, tupleInstanceShape, new Object[]{key, val}));
                    } else {
                        HashingStorage newStorage = HashingStorageSetItem.executeUncached(mapStorage, key, val);
                        assert newStorage == mapStorage;
                    }

                    /* skip whitespace before } or , */
                    idx = skipWhitespace(string, idx, length);

                    /* bail if the object is closed or we didn't get the , delimiter */
                    if (idx < length && string.charAt(idx) == '}') {
                        break;
                    }
                    if (idx >= length || string.charAt(idx) != ',') {
                        throw decodeError(this, string, idx, ErrorMessages.EXPECTING_COMMA_DELIMITER);
                    }

                    /* skip whitespace after , delimiter */
                    idx = skipWhitespace(string, idx + 1, length);
                }
            }

            nextIdx.value = idx + 1;

            if (hasPairsHook) {
                return callObjectPairsHook.executeObject(scanner.objectPairsHook, factory.createList(PythonBuiltinClassType.PList, listInstanceShape, listStorage));
            }

            /* if object_hook is not None: rval = object_hook(rval) */
            PDict rval = factory.createDict(PythonBuiltinClassType.PDict, dictInstanceShape, mapStorage);
            if (scanner.objectHook != PNone.NONE) {
                return callObjectHook.executeObject(scanner.objectHook, rval);
            }
            return rval;
        }

        @TruffleBoundary
        private Object parseArrayUnicode(PJSONScanner scanner, String string, int start, IntRef nextIdx) {
            /*
             * Read a JSON array from PyUnicode pystr. idx is the index of the first character after
             * the opening brace. nextIdx is a return-by-reference index to the first character
             * after the closing brace.
             *
             * Returns a new PyList
             */
            int idx = start;
            ObjectSequenceStorage storage = new ObjectSequenceStorage(4);
            int length = string.length();

            idx = skipWhitespace(string, idx, length);

            /* only loop if the array is non-empty */
            if (idx >= length || string.charAt(idx) != ']') {
                while (true) {

                    /* read any JSON term */
                    Object val = scanOnceUnicode(scanner, string, idx, nextIdx);
                    storage.insertItem(storage.length(), val);
                    idx = nextIdx.value;

                    /* skip whitespace between term and , */
                    idx = skipWhitespace(string, idx, length);

                    /* bail if the array is closed or we didn't get the , delimiter */
                    if (idx < length && string.charAt(idx) == ']') {
                        break;
                    }
                    if (idx >= length || string.charAt(idx) != ',') {
                        throw decodeError(this, string, idx, ErrorMessages.EXPECTING_COMMA_DELIMITER);
                    }
                    idx++;

                    idx = skipWhitespace(string, idx, length);
                }
            }

            /* verify that idx < (length-1), string.charAt( idx) should be ']' */
            if (idx >= length || string.charAt(idx) != ']') {
                throw decodeError(this, string, length - 1, ErrorMessages.EXPECTING_VALUE);
            }
            nextIdx.value = idx + 1;
            return factory.createList(PythonBuiltinClassType.PList, listInstanceShape, storage);
        }

        private static int skipWhitespace(String string, int start, int length) {
            int idx = start;
            while (idx < length && JSONModuleBuiltins.isWhitespace(string.charAt(idx))) {
                idx++;
            }
            return idx;
        }

        private Object parseConstant(PJSONScanner scanner, String constant, int idx, IntRef nextIdx) {
            /*
             * Read a JSON constant. constant is the constant string that was found ("NaN",
             * "Infinity", "-Infinity"). idx is the index of the first character of the constant
             * nextIdx is a return-by-reference index to the first character after the constant.
             *
             * Returns the result of parse_constant
             */

            nextIdx.value = idx + constant.length();
            return callParseConstant.executeObject(scanner.parseConstant, toTruffleStringUncached(constant));
        }

        @TruffleBoundary
        private Object matchNumberUnicode(PJSONScanner scanner, String string, int start, IntRef nextIdx) {
            /*
             * Read a JSON number from PyUnicode pystr. idx is the index of the first character of
             * the number nextIdx is a return-by-reference index to the first character after the
             * number.
             *
             * Returns a new PyObject representation of that number: PyLong, or PyFloat. May return
             * other types if parse_int or parse_float are set
             */

            int idx = start;
            int length = string.length();

            /* read a sign if it's there, make sure it's not the end of the string */
            if (string.charAt(idx) == '-') {
                idx++;
                if (idx >= length) {
                    throw stopIteration(this, start);
                }
            }

            /* read as many integer digits as we find as long as it doesn't start with 0 */
            if (string.charAt(idx) >= '1' && string.charAt(idx) <= '9') {
                idx++;
                while (idx < length && string.charAt(idx) >= '0' && string.charAt(idx) <= '9') {
                    idx++;
                }
                /* if it starts with 0 we only expect one integer digit */
            } else if (string.charAt(idx) == '0') {
                idx++;
                /* no integer digits, error */
            } else {
                throw stopIteration(this, start);
            }
            boolean isFloat = false;

            /* if the next char is '.' followed by a digit then read all float digits */
            if (idx < (length - 1) && string.charAt(idx) == '.' && string.charAt(idx + 1) >= '0' && string.charAt(idx + 1) <= '9') {
                isFloat = true;
                idx += 2;
                while (idx < length && string.charAt(idx) >= '0' && string.charAt(idx) <= '9') {
                    idx++;
                }
            }

            /* if the next char is 'e' or 'E' then maybe read the exponent (or backtrack) */
            if (idx < (length - 1) && (string.charAt(idx) == 'e' || string.charAt(idx) == 'E')) {
                int e_start = idx;
                idx++;

                /* read an exponent sign if present */
                if (idx < (length - 1) && (string.charAt(idx) == '-' || string.charAt(idx) == '+')) {
                    idx++;
                }

                /* read all digits */
                while (idx < length && string.charAt(idx) >= '0' && string.charAt(idx) <= '9') {
                    idx++;
                }

                /* if we got a digit, then parse as float. if not, backtrack */
                if (string.charAt(idx - 1) >= '0' && string.charAt(idx - 1) <= '9') {
                    isFloat = true;
                } else {
                    idx = e_start;
                }
            }

            nextIdx.value = idx;
            if (isFloat) {
                if (PyFloatCheckExactNode.executeUncached(scanner.parseFloat)) {
                    String numStr = string.substring(start, idx);
                    return FloatUtils.parseValidString(numStr);
                } else {
                    /* copy the section we determined to be a number */
                    TruffleString numStr = toTruffleStringUncached(string.substring(start, idx));
                    return callParseFloat.executeObject(scanner.parseFloat, numStr);
                }
            } else {
                if (PyLongCheckExactNode.executeUncached(scanner.parseInt)) {
                    Object rval = BuiltinConstructors.IntNode.parseSimpleDecimalLiteral(string, start, idx - start);
                    if (rval != null) {
                        return rval;
                    }
                    String numStr = string.substring(start, idx);
                    BigInteger bi = new BigInteger(numStr);
                    try {
                        return bi.intValueExact();
                    } catch (ArithmeticException e) {
                        // fall through
                    }
                    try {
                        return bi.longValueExact();
                    } catch (ArithmeticException e) {
                        // fall through
                    }
                    return factory.createInt(bi);
                } else {
                    /* copy the section we determined to be a number */
                    TruffleString numStr = toTruffleStringUncached(string.substring(start, idx));
                    return callParseInt.executeObject(scanner.parseInt, numStr);
                }
            }
        }

        @TruffleBoundary
        private Object scanOnceUnicode(PJSONScanner scanner, String string, int idx, IntRef nextIdx) {
            /*
             * Read one JSON term (of any kind) from PyUnicode pystr. idx is the index of the first
             * character of the term nextIdx is a return-by-reference index to the first character
             * after the number.
             *
             * Returns a new PyObject representation of the term.
             */
            if (idx < 0) {
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.ValueError, ErrorMessages.IDX_CANNOT_BE_NEG);
            }
            int length = string.length();
            if (idx >= length) {
                throw stopIteration(this, idx);
            }

            switch (string.charAt(idx)) {
                case '"':
                    /* string */
                    return scanStringUnicode(string, idx + 1, scanner.strict, nextIdx, this);
                case '{':
                    /* object */
                    return parseObjectUnicode(scanner, string, idx + 1, nextIdx);
                case '[':
                    /* array */
                    return parseArrayUnicode(scanner, string, idx + 1, nextIdx);
                case 'n':
                    /* null */
                    if ((idx + 3 < length) && string.charAt(idx + 1) == 'u' && string.charAt(idx + 2) == 'l' && string.charAt(idx + 3) == 'l') {
                        nextIdx.value = idx + 4;
                        return PNone.NONE;
                    }
                    break;
                case 't':
                    /* true */
                    if ((idx + 3 < length) && string.charAt(idx + 1) == 'r' && string.charAt(idx + 2) == 'u' && string.charAt(idx + 3) == 'e') {
                        nextIdx.value = idx + 4;
                        return true;
                    }
                    break;
                case 'f':
                    /* false */
                    if ((idx + 4 < length) && string.charAt(idx + 1) == 'a' && string.charAt(idx + 2) == 'l' && string.charAt(idx + 3) == 's' && string.charAt(idx + 4) == 'e') {
                        nextIdx.value = idx + 5;
                        return false;
                    }
                    break;
                case 'N':
                    /* NaN */
                    if ((idx + 2 < length) && string.charAt(idx + 1) == 'a' && string.charAt(idx + 2) == 'N') {
                        return parseConstant(scanner, "NaN", idx, nextIdx);
                    }
                    break;
                case 'I':
                    /* Infinity */
                    if ((idx + 7 < length) && string.charAt(idx + 1) == 'n' &&
                                    string.charAt(idx + 2) == 'f' &&
                                    string.charAt(idx + 3) == 'i' &&
                                    string.charAt(idx + 4) == 'n' &&
                                    string.charAt(idx + 5) == 'i' &&
                                    string.charAt(idx + 6) == 't' &&
                                    string.charAt(idx + 7) == 'y') {
                        return parseConstant(scanner, "Infinity", idx, nextIdx);
                    }
                    break;
                case '-':
                    /* -Infinity */
                    if ((idx + 8 < length) && string.charAt(idx + 1) == 'I' &&
                                    string.charAt(idx + 2) == 'n' &&
                                    string.charAt(idx + 3) == 'f' &&
                                    string.charAt(idx + 4) == 'i' &&
                                    string.charAt(idx + 5) == 'n' &&
                                    string.charAt(idx + 6) == 'i' &&
                                    string.charAt(idx + 7) == 't' &&
                                    string.charAt(idx + 8) == 'y') {
                        return parseConstant(scanner, "-Infinity", idx, nextIdx);
                    }
                    break;
            }
            /* Didn't find a string, object, array, or named constant. Look for a number. */
            return matchNumberUnicode(scanner, string, idx, nextIdx);
        }

    }

    @TruffleBoundary
    static TruffleString scanStringUnicode(String string, int start, boolean strict, IntRef nextIdx, Node raisingNode) {
        String result;
        StringBuilder builder = null;

        if (start < 0 || start > string.length()) {
            throw PRaiseNode.raiseUncached(raisingNode, PythonBuiltinClassType.ValueError, ErrorMessages.END_IS_OUT_OF_BOUNDS);
        }
        int idx = start;
        while (idx < string.length()) {
            char c = string.charAt(idx++);
            if (c == '"') {
                // we reached the end of the string literal
                result = builder == null ? string.substring(start, idx - 1) : builder.toString();
                nextIdx.value = idx;
                return toTruffleStringUncached(result);
            } else if (c == '\\') {
                // escape sequence, switch to StringBuilder
                if (builder == null) {
                    builder = new StringBuilder().append(string, start, idx - 1);
                }
                if (idx >= string.length()) {
                    throw decodeError(raisingNode, string, start - 1, ErrorMessages.UTERMINATED_STR_STARTING);
                }
                c = string.charAt(idx++);
                if (c == 'u') {
                    if (idx + 3 >= string.length()) {
                        throw decodeError(raisingNode, string, idx - 1, ErrorMessages.INVALID_UXXXX_ESCAPE);
                    }
                    c = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = Character.digit(string.charAt(idx++), 16);
                        if (digit == -1) {
                            throw decodeError(raisingNode, string, idx - 1, ErrorMessages.INVALID_UXXXX_ESCAPE);
                        }
                        c = (char) ((c << 4) + digit);
                    }
                } else {
                    switch (c) {
                        case '"':
                        case '\\':
                        case '/':
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = '\f';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        default:
                            throw decodeError(raisingNode, string, idx - 1, ErrorMessages.INVALID_ESCAPE);
                    }
                }
                builder.append(c);
            } else {
                // any other character: check if in strict mode
                if (strict && c < 0x20) {
                    throw decodeError(raisingNode, string, idx - 1, ErrorMessages.INVALID_CTRL_CHARACTER_AT);
                }
                if (builder != null) {
                    builder.append(c);
                }
            }
        }
        throw decodeError(raisingNode, string, start - 1, ErrorMessages.UNTERMINATED_STR_STARTING_AT);
    }

    private static RuntimeException decodeError(Node raisingNode, String jsonString, int pos, TruffleString format) {
        CompilerAsserts.neverPartOfCompilation();
        Object module = AbstractImportNode.importModule(toTruffleStringUncached("json.decoder"));
        Object errorClass = PyObjectLookupAttr.executeUncached(module, T_JSON_DECODE_ERROR);
        Object exception = CallNode.executeUncached(errorClass, format, toTruffleStringUncached(jsonString), pos);
        throw PRaiseNode.raiseExceptionObject(raisingNode, exception, false);
    }

    private static RuntimeException stopIteration(Node raisingNode, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        Object exception = CallNode.executeUncached(PythonContext.get(raisingNode).lookupType(PythonBuiltinClassType.StopIteration), value);
        throw PRaiseNode.raiseExceptionObject(raisingNode, exception, false);
    }
}
