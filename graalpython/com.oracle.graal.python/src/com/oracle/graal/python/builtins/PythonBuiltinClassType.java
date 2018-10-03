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

import java.util.HashSet;

import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.object.Shape;

public enum PythonBuiltinClassType implements LazyPythonClass {

    TruffleObject("truffle_object"),
    Boolean("bool", "builtins"),
    GetSetDescriptor("get_set_desc"),
    PArray("array", "array"),
    PArrayIterator("arrayiterator"),
    PBaseException("BaseException", "builtins"),
    PIterator("iterator"),
    PBuiltinFunction("method_descriptor"),
    PBuiltinMethod("builtin_function_or_method"),
    PByteArray("bytearray", "builtins"),
    PBytes("bytes", "builtins"),
    PCell("cell"),
    PComplex("complex", "builtins"),
    PDict("dict", "builtins"),
    PDictKeysView("dict_keys"),
    PDictItemsIterator("dict_itemsiterator"),
    PDictItemsView("dict_items"),
    PDictKeysIterator("dict_keysiterator"),
    PDictValuesIterator("dict_valuesiterator"),
    PDictValuesView("dict_values"),
    PEllipsis("ellipsis"),
    PEnumerate("enumerate", "builtins"),
    PFloat("float", "builtins"),
    PFrame("frame"),
    PFrozenSet("frozenset", "builtins"),
    PFunction("function"),
    PGenerator("generator"),
    PInt("int", "builtins"),
    PList("list", "builtins"),
    PMappingproxy("mappingproxy"),
    PMemoryView("memoryview", "builtins"),
    PMethod("method"),
    PNone("NoneType"),
    PNotImplemented("NotImplementedType"),
    PRandom("Random", "_random"),
    PRange("range", "builtins"),
    PReferenceType("ReferenceType", "_weakref"),
    PSentinelIterator("callable_iterator"),
    PForeignArrayIterator("foreign_iterator"),
    PReverseIterator("reversed", "builtins"),
    PSet("set", "builtins"),
    PSlice("slice", "builtins"),
    PString("str", "builtins"),
    PTraceback("traceback"),
    PTuple("tuple", "builtins"),
    PythonClass("type", "builtins"),
    PythonModule("module"),
    PythonObject("object", "builtins"),
    Super("super", "builtins"),
    PCode("code"),
    PZip("zip", "builtins"),
    PBuffer("buffer");

    private final String shortName;
    private final Shape instanceShape;
    private final String publicInModule;

    // initialized in static constructor
    @CompilationFinal private PythonBuiltinClassType base;

    PythonBuiltinClassType(String shortName, String publicInModule) {
        this.shortName = shortName;
        this.publicInModule = publicInModule;
        this.instanceShape = com.oracle.graal.python.builtins.objects.type.PythonClass.freshShape();
    }

    PythonBuiltinClassType(String shortName) {
        this(shortName, null);
    }

    public String getShortName() {
        return shortName;
    }

    public PythonBuiltinClassType getBase() {
        return base;
    }

    public String getPublicInModule() {
        return publicInModule;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return shortName;
    }

    public Shape getInstanceShape() {
        return instanceShape;
    }

    static {
        HashSet<String> set = new HashSet<>();
        for (PythonBuiltinClassType type : values()) {
            assert set.add(type.shortName) : type.name();
            type.base = PythonObject;
        }
        Boolean.base = PInt;
    }
}
