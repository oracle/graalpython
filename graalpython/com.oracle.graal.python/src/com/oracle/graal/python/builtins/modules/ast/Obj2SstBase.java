/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_SOME_SORT_OF_S_BUT_GOT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.REQUIRED_FIELD_S_MISSING_FROM_S;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FIELD_S_CHANGED_SIZE_DURING_ITERATION;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FIELD_S_MUST_BE_A_LIST_NOT_P;

import java.util.function.IntFunction;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallNode;
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
        Object tmp = PyObjectLookupAttr.getUncached().execute(null, obj, attrName);
        if (tmp instanceof PNone) {
            if (required) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
            }
            return null;
        }
        // Py_EnterRecursiveCall(" while traversing '%s' node")
        return conversion.convert(tmp);
    }

    int lookupAndConvertInt(Object obj, TruffleString attrName, TruffleString nodeName, boolean required) {
        Object tmp = PyObjectLookupAttr.getUncached().execute(null, obj, attrName);
        if (tmp instanceof PNone) {
            if (required) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
            } else {
                return 0;
            }
        }
        // Py_EnterRecursiveCall(" while traversing '%s' node")
        return obj2int(tmp);
    }

    boolean lookupAndConvertBoolean(Object obj, TruffleString attrName, TruffleString nodeName, boolean required) {
        Object tmp = PyObjectLookupAttr.getUncached().execute(null, obj, attrName);
        if (tmp instanceof PNone) {
            if (required) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
            } else {
                return false;
            }
        }
        // Py_EnterRecursiveCall(" while traversing '%s' node")
        return obj2boolean(tmp);
    }

    <T> T[] lookupAndConvertSequence(Object obj, TruffleString attrName, TruffleString nodeName, Conversion<T> conversion, IntFunction<T[]> arrayFactory) {
        Object tmp = PyObjectLookupAttr.getUncached().execute(null, obj, attrName);
        if (tmp instanceof PNone) {
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, REQUIRED_FIELD_S_MISSING_FROM_S, attrName, nodeName);
        }
        if (!(tmp instanceof PList)) {
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, S_FIELD_S_MUST_BE_A_LIST_NOT_P, nodeName, attrName, tmp);
        }
        SequenceStorage seq = ((PList) tmp).getSequenceStorage();
        T[] result = arrayFactory.apply(seq.length());
        for (int i = 0; i < result.length; ++i) {
            tmp = seq.getItemNormalized(i);
            // Py_EnterRecursiveCall(" while traversing '%s' node")
            result[i] = conversion.convert(tmp);
            if (result.length != seq.length()) {
                throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, S_FIELD_S_CHANGED_SIZE_DURING_ITERATION, nodeName, attrName);
            }
        }
        return result;
    }

    static boolean isInstanceOf(Object o, PythonAbstractClass cls) {
        Object check = PyObjectLookupAttr.getUncached().execute(null, cls, SpecialMethodNames.T___INSTANCECHECK__);
        Object result = CallNode.getUncached().execute(check, o);
        return CastToJavaBooleanNode.getUncached().execute(result);
    }

    int obj2int(Object o) {
        // TODO PyLong_Check
        return PyLongAsIntNode.getUncached().execute(null, o);
    }

    boolean obj2boolean(Object o) {
        return obj2int(o) != 0;
    }

    String obj2String(Object obj) {     // TODO support str and bytes
        // TODO if (!PyUnicode_CheckExact(obj) && !PyBytes_CheckExact(obj))
        if (obj == PNone.NONE) {
            return null;
        }
        return CastToJavaStringNode.getUncached().execute(obj);
    }

    String obj2identifier(Object obj) {
        // TODO if (!PyUnicode_CheckExact(obj) && obj != Py_None) {
        if (obj == PNone.NONE) {
            return null;
        }
        return CastToJavaStringNode.getUncached().execute(obj);
    }

    ConstantValue obj2ConstantValue(Object obj) {
        // TODO convert to a Java object if possible
        return ConstantValue.ofArbitraryPythonObject(obj);
    }

    static PException unexpectedNodeType(TruffleString expected, Object obj) {
        TruffleString repr = PyObjectReprAsTruffleStringNode.getUncached().execute(null, obj);
        throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.TypeError, EXPECTED_SOME_SORT_OF_S_BUT_GOT_S, expected, repr);
    }
}
