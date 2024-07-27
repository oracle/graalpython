/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.builtins.modules.ast.AstState.T_C_CONSTANT;
import static com.oracle.graal.python.builtins.modules.ast.AstState.T_F_VALUE;
import static com.oracle.graal.python.nodes.ErrorMessages.AST_IDENTIFIER_MUST_BE_OF_TYPE_STR;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_SOME_SORT_OF_S_BUT_GOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.FIELD_S_IS_REQUIRED_FOR_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_INTEGER_VALUE;
import static com.oracle.graal.python.nodes.ErrorMessages.REQUIRED_FIELD_S_MISSING_FROM_S;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FIELD_S_CHANGED_SIZE_DURING_ITERATION;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FIELD_S_MUST_BE_A_LIST_NOT_P;
import static com.oracle.graal.python.nodes.PGuards.isBuiltinPInt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.function.IntFunction;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyBytesCheckExactNode;
import com.oracle.graal.python.lib.PyComplexCheckExactNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyFrozenSetCheckExactNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyTupleCheckExactNode;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.strings.TruffleString;

abstract class Obj2SstBase {

    final AstState state;

    protected Obj2SstBase(AstState state) {
        this.state = state;
    }

    @FunctionalInterface
    interface Conversion<T> {
        T convert(Object o);
    }

    <T> T lookupAndConvert(Object obj, TruffleString attrName, TruffleString nodeName, Conversion<T> conversion, boolean required) {
        Object tmp = lookupAttr(obj, attrName);
        if (tmp instanceof PNone) {
            if (!required) {
                return null;
            }
            if (tmp == PNone.NO_VALUE) {
                throw raiseTypeError(REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
            }
            // In CPython, None values for required attributes are not checked here, but converted
            // to C NULL and later checked in _PyAST_* constructor. This is not convenient for us,
            // since our SST nodes are in the pegparser project which does not have access to Python
            // exceptions. So we handle PNone.NONE here, but there is one exception - None is a
            // valid value for the required field ExprTy.Constant.value.
            if (!(nodeName == T_C_CONSTANT && attrName == T_F_VALUE)) {
                throw raiseValueError(FIELD_S_IS_REQUIRED_FOR_S, attrName, nodeName);
            }
        }
        // Py_EnterRecursiveCall(" while traversing '%s' node")
        return conversion.convert(tmp);
    }

    int lookupAndConvertInt(Object obj, TruffleString attrName, TruffleString nodeName, boolean required) {
        Object tmp = lookupAttr(obj, attrName);
        if (tmp instanceof PNone) {
            if (!required) {
                return 0;
            }
            if (tmp == PNone.NO_VALUE) {
                throw raiseTypeError(REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
            }
            // PNone.NONE is handled by obj2int() (produces a different error message)
        }
        // Py_EnterRecursiveCall(" while traversing '%s' node")
        return obj2int(tmp);
    }

    boolean lookupAndConvertBoolean(Object obj, TruffleString attrName, TruffleString nodeName, boolean required) {
        Object tmp = lookupAttr(obj, attrName);
        if (tmp instanceof PNone) {
            if (!required) {
                return false;
            }
            if (tmp == PNone.NO_VALUE) {
                throw raiseTypeError(REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
            }
            // PNone.NONE is handled by obj2boolean() (produces a different error message)
        }
        // Py_EnterRecursiveCall(" while traversing '%s' node")
        return obj2boolean(tmp);
    }

    <T> T[] lookupAndConvertSequence(Object obj, TruffleString attrName, TruffleString nodeName, Conversion<T> conversion, IntFunction<T[]> arrayFactory) {
        Object tmp = lookupAttr(obj, attrName);
        if (tmp instanceof PNone) {
            throw raiseTypeError(REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
        }
        if (!(tmp instanceof PList)) {
            throw raiseTypeError(S_FIELD_S_MUST_BE_A_LIST_NOT_P, nodeName, attrName, tmp);
        }
        SequenceStorage seq = ((PList) tmp).getSequenceStorage();
        T[] result = arrayFactory.apply(seq.length());
        for (int i = 0; i < result.length; ++i) {
            tmp = SequenceStorageNodes.GetItemScalarNode.executeUncached(seq, i);
            // Py_EnterRecursiveCall(" while traversing '%s' node")
            result[i] = conversion.convert(tmp);
            if (result.length != seq.length()) {
                throw raiseTypeError(S_FIELD_S_CHANGED_SIZE_DURING_ITERATION, nodeName, attrName);
            }
        }
        return result;
    }

    static boolean isInstanceOf(Object o, PythonAbstractClass cls) {
        Object check = lookupAttr(cls, SpecialMethodNames.T___INSTANCECHECK__);
        Object result = CallNode.executeUncached(check, o);
        return CastToJavaBooleanNode.executeUncached(result);
    }

    int obj2int(Object o) {
        if (!PyLongCheckNode.executeUncached(o)) {
            throw raiseValueError(INVALID_INTEGER_VALUE, repr(o));
        }
        return PyLongAsIntNode.executeUncached(o);
    }

    boolean obj2boolean(Object o) {
        return obj2int(o) != 0;
    }

    // Equivalent of obj2ast_string().
    // The ASDL "string" type represents either "str" or "bytes" python types.
    // CPython just checks the type and keeps it as a python object in the AST.
    // We need to convert the value to a Java type: j.l.String or byte[].
    Object obj2string(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        if (PyBytesCheckExactNode.executeUncached(obj)) {
            PythonBufferAcquireLibrary acquireLib = PythonBufferAcquireLibrary.getUncached();
            PythonBufferAccessLibrary accessLib = PythonBufferAccessLibrary.getUncached();
            Object buf = acquireLib.acquireReadonly(obj);
            try {
                return accessLib.getCopiedByteArray(buf);
            } finally {
                accessLib.release(buf);
            }
        }
        if (PyUnicodeCheckExactNode.executeUncached(obj)) {
            return CastToJavaStringNode.getUncached().execute(obj);
        }
        throw raiseTypeError(ErrorMessages.AST_STRING_MUST_BE_OF_TYPE_STR);
    }

    String obj2identifier(Object obj) {
        if (obj == PNone.NONE) {
            return null;
        }
        try {
            return CastToJavaStringNode.getUncached().execute(obj);
        } catch (CannotCastException e) {
            throw raiseTypeError(AST_IDENTIFIER_MUST_BE_OF_TYPE_STR);
        }
    }

    ConstantValue obj2ConstantValue(Object obj) {
        // CPython does not do any checks here - they just store obj directly into the SST and
        // later validate that the value is a constant (validate_constants in ast.c).
        // We don't want arbitrary python object in SST, so we need to convert here, which means
        // that we may report errors in different order. If it turns out to be a problem, we can
        // just detect the error here and report it in Validator#validateConstant.
        if (obj == PNone.NONE) {
            return ConstantValue.NONE;
        }
        if (obj == PEllipsis.INSTANCE) {
            return ConstantValue.ELLIPSIS;
        }
        if (obj instanceof Boolean) {
            return ConstantValue.ofBoolean((Boolean) obj);
        }
        if (obj instanceof Integer) {
            return ConstantValue.ofLong((Integer) obj);
        }
        if (obj instanceof Long) {
            return ConstantValue.ofLong((Long) obj);
        }
        if (obj instanceof PInt && isBuiltinPInt((PInt) obj)) {
            BigInteger v = ((PInt) obj).getValue();
            if (PInt.bigIntegerFitsInLong(v)) {
                return ConstantValue.ofLong(v.longValue());
            }
            return ConstantValue.ofBigInteger(v);
        }
        if (obj instanceof Double) {
            return ConstantValue.ofDouble((Double) obj);
        }
        if (obj instanceof PFloat && PyFloatCheckExactNode.executeUncached(obj)) {
            return ConstantValue.ofDouble(((PFloat) obj).getValue());
        }
        if (obj instanceof PComplex && PyComplexCheckExactNode.executeUncached(obj)) {
            PComplex c = (PComplex) obj;
            return ConstantValue.ofComplex(c.getReal(), c.getImag());
        }
        if (obj instanceof TruffleString) {
            return ConstantValue.ofRaw(obj);
        }
        if (obj instanceof PString && PyUnicodeCheckExactNode.executeUncached(obj)) {
            return ConstantValue.ofRaw(((PString) obj).getValueUncached());
        }
        if (obj instanceof PBytes && PyBytesCheckExactNode.executeUncached(obj)) {
            Object buf = PythonBufferAcquireLibrary.getUncached().acquireReadonly(obj);
            PythonBufferAccessLibrary accessLib = PythonBufferAccessLibrary.getFactory().getUncached(buf);
            try {
                return ConstantValue.ofBytes(accessLib.getCopiedByteArray(buf));
            } finally {
                accessLib.release(buf);
            }
        }
        boolean isTuple = PyTupleCheckExactNode.executeUncached(obj);
        if (isTuple || PyFrozenSetCheckExactNode.executeUncached(obj)) {
            Object iter = PyObjectGetIter.executeUncached(obj);
            GetNextNode nextNode = GetNextNode.getUncached();
            ArrayList<ConstantValue> list = new ArrayList<>();
            while (true) {
                try {
                    list.add(obj2ConstantValue(nextNode.execute(iter)));
                } catch (PException e) {
                    e.expectStopIteration(null, IsBuiltinObjectProfile.getUncached());
                    break;
                }
            }
            if (isTuple) {
                return ConstantValue.ofTuple(list.toArray(ConstantValue[]::new));
            }
            return ConstantValue.ofFrozenset(list.toArray(ConstantValue[]::new));
        }
        throw raiseTypeError(ErrorMessages.GOT_AN_INVALID_TYPE_IN_CONSTANT, obj);
    }

    static PException unexpectedNodeType(TruffleString expected, Object obj) {
        throw raiseTypeError(EXPECTED_SOME_SORT_OF_S_BUT_GOT_S, expected, repr(obj));
    }

    private static Object lookupAttr(Object o, TruffleString attrName) {
        return PyObjectLookupAttr.executeUncached(o, attrName);
    }

    private static TruffleString repr(Object o) {
        return PyObjectReprAsTruffleStringNode.executeUncached(o);
    }

    private static PException raise(PythonBuiltinClassType type, TruffleString format, Object... arguments) {
        throw PRaiseNode.getUncached().raise(type, format, arguments);
    }

    static PException raiseTypeError(TruffleString format, Object... arguments) {
        throw raise(PythonBuiltinClassType.TypeError, format, arguments);
    }

    private static PException raiseValueError(TruffleString format, Object... arguments) {
        throw raise(PythonBuiltinClassType.ValueError, format, arguments);
    }
}
