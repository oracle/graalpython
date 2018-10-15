/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.truffle;

import com.oracle.truffle.api.CompilerAsserts;

import static com.oracle.graal.python.nodes.BuiltinNames.COMPLEX;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.FLOAT;
import static com.oracle.graal.python.nodes.BuiltinNames.INT;
import static com.oracle.graal.python.nodes.BuiltinNames.LIST;
import static com.oracle.graal.python.nodes.BuiltinNames.RANGE;
import static com.oracle.graal.python.nodes.BuiltinNames.SET;
import static com.oracle.graal.python.nodes.BuiltinNames.STR;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;

public class PythonTypesUtil {

    /**
     * TODO This method should be extended to support the remaining types.
     */
    public static String getPythonTypeName(Object object) {
        CompilerAsserts.neverPartOfCompilation();

        if (object instanceof Integer || object instanceof PInt) {
            return INT;
        } else if (object instanceof Double) {
            return FLOAT;
        } else if (object instanceof PComplex) {
            return COMPLEX;
        } else if (object instanceof String) {
            return STR;
        } else if (object instanceof PList) {
            return LIST;
        } else if (object instanceof PTuple) {
            return TUPLE;
        } else if (object instanceof PRange) {
            return RANGE;
        } else if (object instanceof PArray) {
            return "array";
        } else if (object instanceof PSet) {
            return SET;
        } else if (object instanceof PDict) {
            return DICT;
        } else if (object instanceof PNone) {
            return "None";
        } else if (object instanceof PythonClass) {
            return object.toString();
        } else if (object instanceof PythonObject) {
            return ((PythonObject) object).getLazyPythonClass().getName();
        }

        throw new RuntimeException("Unsupported type name " + object.getClass());
    }

}
