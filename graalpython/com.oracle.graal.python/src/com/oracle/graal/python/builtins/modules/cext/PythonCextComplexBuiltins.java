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
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeDoubleField;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins.ComplexValue;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins.ToComplexValueNode;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins.TryComplexSpecialMethodNode;
import com.oracle.graal.python.lib.PyComplexCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.runtime.object.PFactory;

public final class PythonCextComplexBuiltins {

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer, Pointer}, call = Ignored)
    static int GraalPyPrivate_Complex_AsCComplex(long objPtr, long out) {
        Object obj = NativeToPythonInternalNode.executeUncached(objPtr, false);
        double real, imag;
        if (PyComplexCheckNode.executeUncached(obj)) {
            ComplexValue value = ToComplexValueNode.executeUncached(obj);
            real = value.getReal();
            imag = value.getImag();
        } else {
            Object converted = TryComplexSpecialMethodNode.executeUncached(obj);
            if (converted != PNone.NO_VALUE) {
                ComplexValue value = ToComplexValueNode.executeUncached(converted);
                real = value.getReal();
                imag = value.getImag();
            } else {
                real = PyFloatAsDoubleNode.executeUncached(obj);
                imag = 0.0;
            }
        }
        writeDoubleField(out, CFields.Py_complex__real, real);
        writeDoubleField(out, CFields.Py_complex__imag, imag);
        return 0;
    }

    @CApiBuiltin(ret = ArgDescriptor.Double, args = {PyObjectRawPointer}, call = Ignored)
    static double GraalPyPrivate_Complex_RealAsDouble(long objPtr) {
        Object obj = NativeToPythonInternalNode.executeUncached(objPtr, false);
        if (PyComplexCheckNode.executeUncached(obj)) {
            return ToComplexValueNode.executeUncached(obj).getReal();
        }
        Object converted = TryComplexSpecialMethodNode.executeUncached(obj);
        if (converted != PNone.NO_VALUE) {
            return ToComplexValueNode.executeUncached(converted).getReal();
        } else {
            return PyFloatAsDoubleNode.executeUncached(obj);
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Double, args = {PyObjectRawPointer}, call = Ignored)
    static double GraalPyPrivate_Complex_ImagAsDouble(long objPtr) {
        Object obj = NativeToPythonInternalNode.executeUncached(objPtr, false);
        if (PyComplexCheckNode.executeUncached(obj)) {
            return ToComplexValueNode.executeUncached(obj).getImag();
        }
        Object converted = TryComplexSpecialMethodNode.executeUncached(obj);
        if (converted != PNone.NO_VALUE) {
            return ToComplexValueNode.executeUncached(converted).getImag();
        }
        PyFloatAsDoubleNode.executeUncached(obj);
        return 0.0;
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {ArgDescriptor.Double, ArgDescriptor.Double}, call = Direct)
    static long PyComplex_FromDoubles(double r, double i) {
        return PythonToNativeInternalNode.executeNewRefUncached(PFactory.createComplex(PythonLanguage.get(null), r, i));
    }
}
