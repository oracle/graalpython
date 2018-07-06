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
package com.oracle.graal.python.builtins;

import java.util.HashMap;

import com.oracle.truffle.api.CompilerAsserts;

public enum PythonBuiltinClassType {

    TruffleObject(com.oracle.truffle.api.interop.TruffleObject.class, "truffle_object"),
    Boolean(java.lang.Boolean.class, "bool"),
    GetSetDescriptor(com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor.class, "get_set_desc"),
    PArray(com.oracle.graal.python.builtins.objects.array.PArray.class, "array"),
    PBaseException(com.oracle.graal.python.builtins.objects.exception.PBaseException.class, "BaseException"),
    PBaseSetIterator(com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator.class, "iterator"),
    PBuiltinFunction(com.oracle.graal.python.builtins.objects.function.PBuiltinFunction.class, "method_descriptor"),
    PBuiltinMethod(com.oracle.graal.python.builtins.objects.method.PBuiltinMethod.class, "builtin_function_or_method"),
    PByteArray(com.oracle.graal.python.builtins.objects.bytes.PByteArray.class, "bytearray"),
    PBytes(com.oracle.graal.python.builtins.objects.bytes.PBytes.class, "bytes"),
    PCell(com.oracle.graal.python.builtins.objects.cell.PCell.class, "cell"),
    PCharArray(com.oracle.graal.python.builtins.objects.array.PCharArray.class, "chars"),
    PCharArrayIterator(com.oracle.graal.python.builtins.objects.iterator.PCharArrayIterator.class, "iterator"),
    PComplex(com.oracle.graal.python.builtins.objects.complex.PComplex.class, "complex"),
    PDict(com.oracle.graal.python.builtins.objects.dict.PDict.class, "dict"),
    PDictKeysView(com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView.class, "dict_keys"),
    PDictItemsIterator(com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsIterator.class, "dict_itemsiterator"),
    PDictItemsView(com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView.class, "dict_items"),
    PDictKeysIterator(com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysIterator.class, "dict_keysiterator"),
    PDictValuesIterator(com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesIterator.class, "dict_valuesiterator"),
    PDictValuesView(com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView.class, "dict_values"),
    PDoubleArray(com.oracle.graal.python.builtins.objects.array.PDoubleArray.class, "doubles"),
    PDoubleArrayIterator(com.oracle.graal.python.builtins.objects.iterator.PDoubleArrayIterator.class, "iterator"),
    PDoubleSequenceIterator(com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator.class, "iterator"),
    PEllipsis(com.oracle.graal.python.builtins.objects.PEllipsis.class, "ellipsis"),
    PEnumerate(com.oracle.graal.python.builtins.objects.enumerate.PEnumerate.class, "enumerate"),
    PFloat(com.oracle.graal.python.builtins.objects.floats.PFloat.class, "float"),
    PFrame(com.oracle.graal.python.builtins.objects.frame.PFrame.class, "frame"),
    PFrozenSet(com.oracle.graal.python.builtins.objects.set.PFrozenSet.class, "frozenset"),
    PFunction(com.oracle.graal.python.builtins.objects.function.PFunction.class, "function"),
    PGenerator(com.oracle.graal.python.builtins.objects.generator.PGenerator.class, "generator"),
    PGeneratorFunction(com.oracle.graal.python.builtins.objects.function.PGeneratorFunction.class, "function"),
    PInt(com.oracle.graal.python.builtins.objects.ints.PInt.class, "int"),
    PIntArray(com.oracle.graal.python.builtins.objects.array.PIntArray.class, "ints"),
    PIntArrayIterator(com.oracle.graal.python.builtins.objects.iterator.PIntArrayIterator.class, "iterator"),
    PIntegerSequenceIterator(com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator.class, "iterator"),
    PList(com.oracle.graal.python.builtins.objects.list.PList.class, "list"),
    PLongArray(com.oracle.graal.python.builtins.objects.array.PLongArray.class, "longs"),
    PLongArrayIterator(com.oracle.graal.python.builtins.objects.iterator.PLongArrayIterator.class, "iterator"),
    PLongSequenceIterator(com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator.class, "iterator"),
    PMappingproxy(com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy.class, "mapping_proxy"),
    PMemoryView(com.oracle.graal.python.builtins.objects.memoryview.PMemoryView.class, "memoryview"),
    PMethod(com.oracle.graal.python.builtins.objects.method.PMethod.class, "method"),
    PNone(com.oracle.graal.python.builtins.objects.PNone.class, "NoneType"),
    PNotImplemented(com.oracle.graal.python.builtins.objects.PNotImplemented.class, "NotImplementedType"),
    PRandom(com.oracle.graal.python.builtins.objects.random.PRandom.class, "random"),
    PRange(com.oracle.graal.python.builtins.objects.range.PRange.class, "range"),
    PRangeIterator(com.oracle.graal.python.builtins.objects.iterator.PRangeIterator.class, "iterator"),
    PRangeReverseIterator(com.oracle.graal.python.builtins.objects.iterator.PRangeIterator.PRangeReverseIterator.class, "iterator"),
    PReferenceType(com.oracle.graal.python.builtins.objects.referencetype.PReferenceType.class, "ReferenceType"),
    PSentinelIterator(com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator.class, "callable_iterator"),
    PSequenceIterator(com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator.class, "iterator"),
    PForeignArrayIterator(com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator.class, "foreign_iterator"),
    PSequenceReverseIterator(com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator.class, "reversed"),
    PSet(com.oracle.graal.python.builtins.objects.set.PSet.class, "set"),
    PSlice(com.oracle.graal.python.builtins.objects.slice.PSlice.class, "slice"),
    PString(com.oracle.graal.python.builtins.objects.str.PString.class, "str"),
    PStringIterator(com.oracle.graal.python.builtins.objects.iterator.PStringIterator.class, "iterator"),
    PStringReverseIterator(com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator.class, "reversed"),
    PTraceback(com.oracle.graal.python.builtins.objects.traceback.PTraceback.class, "traceback"),
    PTuple(com.oracle.graal.python.builtins.objects.tuple.PTuple.class, "tuple"),
    PythonBuiltinClass(com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass.class, "type"),
    PythonClass(com.oracle.graal.python.builtins.objects.type.PythonClass.class, "type"),
    PythonNativeClass(com.oracle.graal.python.builtins.objects.cext.PythonNativeClass.class, "type"),
    PythonModule(com.oracle.graal.python.builtins.objects.module.PythonModule.class, "module"),
    PythonObject(com.oracle.graal.python.builtins.objects.object.PythonObject.class, "object"),
    PythonNativeObject(com.oracle.graal.python.builtins.objects.cext.PythonNativeObject.class, "object"),
    PCode(com.oracle.graal.python.builtins.objects.code.PCode.class, "code"),
    PZip(com.oracle.graal.python.builtins.objects.iterator.PZip.class, "zip"),
    PBuffer(com.oracle.graal.python.builtins.objects.memoryview.PBuffer.class, "buffer");

    private final Class<?> clazz;
    private final String shortName;

    PythonBuiltinClassType(Class<?> clazz, String shortName) {
        this.clazz = clazz;
        this.shortName = shortName;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return shortName;
    }

    private static final HashMap<Class<?>, PythonBuiltinClassType> fromJavaClass = new HashMap<>();

    static {
        for (PythonBuiltinClassType builtinClass : values()) {
            fromJavaClass.put(builtinClass.clazz, builtinClass);
        }
        fromJavaClass.put(String.class, PString);
        fromJavaClass.put(Integer.class, PInt);
        fromJavaClass.put(Long.class, PInt);
        fromJavaClass.put(Double.class, PFloat);
    }

    public static PythonBuiltinClassType fromClass(Class<?> clazz) {
        assert fromJavaClass.containsKey(clazz) : clazz + " is not in list of known classes";
        return fromJavaClass.get(clazz);
    }
}
