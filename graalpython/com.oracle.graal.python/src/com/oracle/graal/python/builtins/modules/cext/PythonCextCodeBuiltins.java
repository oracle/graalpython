/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi18BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextCodeBuiltins {

    @CApiBuiltin(ret = PyCodeObjectTransfer, args = {Int, Int, Int, Int, Int, Int, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, PyObject, Int, PyObject,
                    PyObject}, call = Direct)
    abstract static class PyCode_NewWithPosOnlyArgs extends CApi18BuiltinNode {
        @Specialization
        @TruffleBoundary
        public static Object codeNew(int argcount, int posonlyargcount, int kwonlyargcount, int nlocals, int stacksize, int flags, Object code, Object consts,
                        Object names, Object varnames, Object freevars, Object cellvars,
                        Object filename, Object name, Object qualname,
                        int firstlineno, Object lnotab,
                        @SuppressWarnings("unused") Object exceptionTable,
                        @Cached CallNode callNode) {
            /*
             * This rearranges the arguments (freevars, cellvars).
             */
            Object[] args = new Object[]{
                            argcount,
                            posonlyargcount,
                            kwonlyargcount, nlocals, stacksize, flags,
                            code, consts, names, varnames,
                            filename, name, qualname,
                            firstlineno, lnotab, exceptionTable,
                            freevars, cellvars
            };
            return callNode.execute(PythonBuiltinClassType.PCode, args);
        }
    }

    @CApiBuiltin(ret = PyCodeObjectTransfer, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, call = Direct)
    abstract static class PyCode_NewEmpty extends CApiTernaryBuiltinNode {
        public abstract PCode execute(TruffleString filename, TruffleString funcname, int lineno);

        @Specialization
        @TruffleBoundary
        static PCode newEmpty(TruffleString filename, TruffleString funcname, int lineno,
                        @Cached CodeNodes.CreateCodeNode createCodeNode) {
            return createCodeNode.execute(null, 0, 0, 0, 0, 0, 0,
                            EMPTY_BYTE_ARRAY, EMPTY_OBJECT_ARRAY, EMPTY_TRUFFLESTRING_ARRAY,
                            EMPTY_TRUFFLESTRING_ARRAY, EMPTY_TRUFFLESTRING_ARRAY, EMPTY_TRUFFLESTRING_ARRAY,
                            filename, funcname, funcname,
                            lineno, EMPTY_BYTE_ARRAY);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyCodeObject, Int}, call = Direct)
    abstract static class PyCode_Addr2Line extends CApiBinaryBuiltinNode {
        @Specialization
        static int addr2line(PCode code, int lasti) {
            if (lasti < 0) {
                return code.co_firstlineno();
            }
            return code.lastiToLine(lasti);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyCodeObject}, call = Direct)
    abstract static class PyCode_GetName extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PCode code) {
            return code.getName();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyCodeObject}, call = Direct)
    abstract static class PyCode_GetFileName extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PCode code) {
            return code.getFilename();
        }
    }
}
