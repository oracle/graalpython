/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCodeObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.CharPtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextCodeBuiltins {

    @CApiBuiltin(ret = PyCodeObjectRawPointer, args = {Int, Int, Int, Int, Int, Int, PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer,
                    PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer, Int, PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PyUnstable_Code_NewWithPosOnlyArgs(int argcount, int posonlyargcount, int kwonlyargcount, int nlocals, int stacksize, int flags, long codePtr, long constsPtr,
                    long namesPtr, long varnamesPtr, long freevarsPtr, long cellvarsPtr,
                    long filenamePtr, long namePtr, long qualnamePtr,
                    int firstlineno, long lnotabPtr, long exceptionTablePtr) {
        Object code = NativeToPythonNode.executeRawUncached(codePtr);
        Object consts = NativeToPythonNode.executeRawUncached(constsPtr);
        Object names = NativeToPythonNode.executeRawUncached(namesPtr);
        Object varnames = NativeToPythonNode.executeRawUncached(varnamesPtr);
        Object freevars = NativeToPythonNode.executeRawUncached(freevarsPtr);
        Object cellvars = NativeToPythonNode.executeRawUncached(cellvarsPtr);
        Object filename = NativeToPythonNode.executeRawUncached(filenamePtr);
        Object name = NativeToPythonNode.executeRawUncached(namePtr);
        Object qualname = NativeToPythonNode.executeRawUncached(qualnamePtr);
        Object lnotab = NativeToPythonNode.executeRawUncached(lnotabPtr);
        Object exceptionTable = NativeToPythonNode.executeRawUncached(exceptionTablePtr);
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
        return PythonToNativeNewRefNode.executeLongUncached(CallNode.executeUncached(PythonBuiltinClassType.PCode, args));
    }

    @CApiBuiltin(ret = PyCodeObjectRawPointer, args = {ConstCharPtr, ConstCharPtr, Int}, call = Direct)
    static long PyCode_NewEmpty(long filenamePtr, long funcnamePtr, int lineno) {
        TruffleString filename = (TruffleString) CharPtrToPythonNode.getUncached().execute(filenamePtr);
        TruffleString funcname = (TruffleString) CharPtrToPythonNode.getUncached().execute(funcnamePtr);
        return PythonToNativeNewRefNode.executeLongUncached(createCodeNewEmpty(filename, funcname, lineno));
    }

    @CApiBuiltin(ret = Int, args = {PyCodeObjectRawPointer, Int}, call = Direct)
    static int PyCode_Addr2Line(long codePtr, int lasti) {
        PCode code = (PCode) NativeToPythonNode.executeRawUncached(codePtr);
        if (lasti < 0) {
            return code.co_firstlineno();
        }
        return code.lastiToLine(lasti);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyCodeObjectRawPointer}, call = Direct)
    static long GraalPyCode_GetName(long codePtr) {
        PCode code = (PCode) NativeToPythonNode.executeRawUncached(codePtr);
        return PythonToNativeNewRefNode.executeLongUncached(code.getName());
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyCodeObjectRawPointer}, call = Direct)
    static long GraalPyCode_GetFileName(long codePtr) {
        PCode code = (PCode) NativeToPythonNode.executeRawUncached(codePtr);
        return PythonToNativeNewRefNode.executeLongUncached(code.getFilename());
    }

    static PCode createCodeNewEmpty(TruffleString filename, TruffleString funcname, int lineno) {
        return CodeNodes.CreateCodeNode.executeUncached(0, 0, 0, 0, 0, 0,
                        EMPTY_BYTE_ARRAY, EMPTY_OBJECT_ARRAY, EMPTY_TRUFFLESTRING_ARRAY,
                        EMPTY_TRUFFLESTRING_ARRAY, EMPTY_TRUFFLESTRING_ARRAY, EMPTY_TRUFFLESTRING_ARRAY,
                        filename, funcname, funcname,
                        lineno, EMPTY_BYTE_ARRAY);
    }
}
