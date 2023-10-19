/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_COMPILER_FLAGS;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.nodes.BuiltinNames.T_COMPILE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXEC;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.nodes.ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT;
import static com.oracle.graal.python.nodes.PGuards.isDict;
import static com.oracle.graal.python.nodes.PGuards.isString;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextPythonRunBuiltins {
    // from compile.h
    private static final int Py_single_input = 256;
    private static final int Py_file_input = 257;
    private static final int Py_eval_input = 258;

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, Int, PyObject, PyObject, PY_COMPILER_FLAGS}, call = Direct)
    abstract static class PyRun_StringFlags extends CApi5BuiltinNode {

        @Specialization(guards = "checkArgs(source, globals, locals, inliningTarget, isMapping)")
        static Object run(Object source, int type, Object globals, Object locals, @SuppressWarnings("unused") Object flags,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyMappingCheckNode isMapping,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonModule builtins = PythonContext.get(inliningTarget).getBuiltins();
            Object compileCallable = lookupNode.execute(null, inliningTarget, builtins, T_COMPILE);
            TruffleString stype;
            if (type == Py_single_input) {
                stype = StringLiterals.T_SINGLE;
            } else if (type == Py_file_input) {
                stype = StringLiterals.T_EXEC;
            } else if (type == Py_eval_input) {
                stype = StringLiterals.T_EVAL;
            } else {
                throw raiseNode.get(inliningTarget).raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
            }
            Object code = callNode.execute(compileCallable, source, stype, stype);
            Object execCallable = lookupNode.execute(null, inliningTarget, builtins, T_EXEC);
            return callNode.execute(execCallable, code, globals, locals);
        }

        @Specialization(guards = "!isString(source) || !isDict(globals)")
        static Object run(@SuppressWarnings("unused") Object source, @SuppressWarnings("unused") int type, @SuppressWarnings("unused") Object globals,
                        @SuppressWarnings("unused") Object locals, @SuppressWarnings("unused") Object flags,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
        }

        @Specialization(guards = {"isString(source)", "isDict(globals)", "!isMapping.execute(inliningTarget, locals)"})
        static Object run(@SuppressWarnings("unused") Object source, @SuppressWarnings("unused") int type, @SuppressWarnings("unused") Object globals, Object locals,
                        @SuppressWarnings("unused") Object flags,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyMappingCheckNode isMapping,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, locals);
        }

        protected static boolean checkArgs(Object source, Object globals, Object locals, Node inliningTarget, PyMappingCheckNode isMapping) {
            return isString(source) && isDict(globals) && isMapping.execute(inliningTarget, locals);
        }
    }

    @CApiBuiltin(ret = PyObject, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int}, call = Direct)
    abstract static class Py_CompileString extends CApiTernaryBuiltinNode {
        @Specialization(guards = {"isString(source)", "isString(filename)"})
        static Object compile(Object source, Object filename, int type,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode) {
            return Py_CompileStringExFlags.compile(source, filename, type, null, -1, inliningTarget, raiseNode, lookupNode, callNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(source) || !isString(filename)")
        static Object fail(Object source, Object filename, Object type,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @CApiBuiltin(ret = PyObject, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Int, PY_COMPILER_FLAGS, Int}, call = Direct)
    abstract static class Py_CompileStringExFlags extends CApi5BuiltinNode {
        @Specialization(guards = {"isString(source)", "isString(filename)"})
        static Object compile(Object source, Object filename, int type,
                        @SuppressWarnings("unused") Object flags, int optimizationLevel, @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode) {
            PythonModule builtins = PythonContext.get(lookupNode).getCore().getBuiltins();
            Object compileCallable = lookupNode.execute(null, inliningTarget, builtins, T_COMPILE);
            TruffleString stype;
            if (type == Py_single_input) {
                stype = StringLiterals.T_SINGLE;
            } else if (type == Py_file_input) {
                stype = StringLiterals.T_EXEC;
            } else if (type == Py_eval_input) {
                stype = StringLiterals.T_EVAL;
            } else {
                throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
            }
            int defaultFlags = 0;
            boolean dontInherit = false;
            return callNode.execute(compileCallable, source, filename, stype, defaultFlags, dontInherit, optimizationLevel);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(source) || !isString(filename)")
        static Object fail(Object source, Object filename, Object type, Object flags, Object optimizationLevel,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @CApiBuiltin(ret = PyObject, args = {ConstCharPtrAsTruffleString, PyObject, Int, PY_COMPILER_FLAGS, Int}, call = Direct)
    abstract static class Py_CompileStringObject extends CApi5BuiltinNode {
        @Specialization(guards = "isString(source)")
        static Object compile(Object source, Object filename, int type,
                        @SuppressWarnings("unused") Object flags,
                        int optimizationLevel,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode) {
            return Py_CompileStringExFlags.compile(source, filename, type, null, optimizationLevel, inliningTarget, raiseNode, lookupNode, callNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(source)")
        static Object fail(Object source, Object filename, Object type, Object flags, Object optimizationLevel,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(SystemError, BAD_ARG_TO_INTERNAL_FUNC);
        }
    }
}
