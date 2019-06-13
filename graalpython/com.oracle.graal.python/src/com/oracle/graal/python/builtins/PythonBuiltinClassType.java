/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.object.Shape;

public enum PythonBuiltinClassType implements LazyPythonClass {

    ForeignObject(BuiltinNames.FOREIGN),
    Boolean("bool", "builtins"),
    GetSetDescriptor("get_set_desc"),
    PArray("array", "array"),
    PArrayIterator("arrayiterator"),
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
    PMMap("mmap", "mmap"),
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
    PZipImporter("zipimporter", "zipimport"),
    PBuffer("buffer", "builtins"),
    PThread("start_new_thread", "_thread"),
    PLock("LockType", "_thread"),
    PRLock("RLock", "_thread"),
    PSocket("socket", "_socket"),
    PStaticmethod("staticmethod", "builtins"),
    PClassmethod("classmethod", "builtins"),
    PScandirIterator("ScandirIterator", "posix"),
    PDirEntry("DirEntry", "posix"),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", "builtins"),
    SystemExit("SystemExit", "builtins"),
    KeyboardInterrupt("KeyboardInterrupt", "builtins"),
    GeneratorExit("GeneratorExit", "builtins"),
    Exception("Exception", "builtins"),
    StopIteration("StopIteration", "builtins"),
    ArithmeticError("ArithmeticError", "builtins"),
    FloatingPointError("FloatingPointError", "builtins"),
    OverflowError("OverflowError", "builtins"),
    ZeroDivisionError("ZeroDivisionError", "builtins"),
    AssertionError("AssertionError", "builtins"),
    AttributeError("AttributeError", "builtins"),
    BufferError("BufferError", "builtins"),
    EOFError("EOFError", "builtins"),
    ImportError("ImportError", "builtins"),
    ModuleNotFoundError("ModuleNotFoundError", "builtins"),
    LookupError("LookupError", "builtins"),
    IndexError("IndexError", "builtins"),
    KeyError("KeyError", "builtins"),
    MemoryError("MemoryError", "builtins"),
    NameError("NameError", "builtins"),
    UnboundLocalError("UnboundLocalError", "builtins"),
    OSError("OSError", "builtins"),
    BlockingIOError("BlockingIOError", "builtins"),
    ChildProcessError("ChildProcessError", "builtins"),
    ConnectionError("ConnectionError", "builtins"),
    BrokenPipeError("BrokenPipeError", "builtins"),
    ConnectionAbortedError("ConnectionAbortedError", "builtins"),
    ConnectionRefusedError("ConnectionRefusedError", "builtins"),
    ConnectionResetError("ConnectionResetError", "builtins"),
    FileExistsError("FileExistsError", "builtins"),
    FileNotFoundError("FileNotFoundError", "builtins"),
    InterruptedError("InterruptedError", "builtins"),
    IsADirectoryError("IsADirectoryError", "builtins"),
    NotADirectoryError("NotADirectoryError", "builtins"),
    PermissionError("PermissionError", "builtins"),
    ProcessLookupError("ProcessLookupError", "builtins"),
    TimeoutError("TimeoutError", "builtins"),
    ZipImportError("ZipImportError", "zipimport"),
    ZLibError("error", "zlib"),

    // todo: all OS errors

    ReferenceError("ReferenceError", "builtins"),
    RuntimeError("RuntimeError", "builtins"),
    NotImplementedError("NotImplementedError", "builtins"),
    SyntaxError("SyntaxError", "builtins"),
    IndentationError("IndentationError", "builtins"),
    TabError("TabError", "builtins"),
    SystemError("SystemError", "builtins"),
    TypeError("TypeError", "builtins"),
    ValueError("ValueError", "builtins"),
    UnicodeError("UnicodeError", "builtins"),
    UnicodeDecodeError("UnicodeDecodeError", "builtins"),
    UnicodeEncodeError("UnicodeEncodeError", "builtins"),
    UnicodeTranslateError("UnicodeTranslateError", "builtins"),
    RecursionError("RecursionError", "builtins"),

    // warnings
    Warning("Warning", "builtins"),
    BytesWarning("BytesWarning", "builtins"),
    DeprecationWarning("DeprecationWarning", "builtins"),
    FutureWarning("FutureWarning", "builtins"),
    ImportWarning("ImportWarning", "builtins"),
    PendingDeprecationWarning("PendingDeprecationWarning", "builtins"),
    ResourceWarning("ResourceWarning", "builtins"),
    RuntimeWarning("RuntimeWarning", "builtins"),
    SyntaxWarning("SyntaxWarning", "builtins"),
    UnicodeWarning("UnicodeWarning", "builtins"),
    UserWarning("UserWarning", "builtins");

    private final String name;
    private final Shape instanceShape;
    private final String publicInModule;

    // initialized in static constructor
    @CompilationFinal private PythonBuiltinClassType base;

    PythonBuiltinClassType(String name, String publicInModule) {
        this.name = name;
        this.publicInModule = publicInModule;
        this.instanceShape = com.oracle.graal.python.builtins.objects.object.PythonObject.freshShape(this);
    }

    PythonBuiltinClassType(String name) {
        this(name, null);
    }

    public String getName() {
        return name;
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
        return name;
    }

    public Shape getInstanceShape() {
        return instanceShape;
    }

    public static final PythonBuiltinClassType[] VALUES = values();
    public static final PythonBuiltinClassType[] EXCEPTIONS;

    static {
        // fill the EXCEPTIONS array

        EXCEPTIONS = new PythonBuiltinClassType[VALUES.length - PBaseException.ordinal()];
        for (int i = 0; i < EXCEPTIONS.length; i++) {
            EXCEPTIONS[i] = VALUES[i + PBaseException.ordinal()];
        }

        // set the base classes (and check uniqueness):

        HashSet<String> set = new HashSet<>();
        for (PythonBuiltinClassType type : VALUES) {
            assert set.add(type.name) : type.name();
            type.base = PythonObject;
        }

        Boolean.base = PInt;

        SystemExit.base = PBaseException;
        KeyboardInterrupt.base = PBaseException;
        GeneratorExit.base = PBaseException;
        Exception.base = PBaseException;
        StopIteration.base = Exception;
        ArithmeticError.base = Exception;
        FloatingPointError.base = ArithmeticError;
        OverflowError.base = ArithmeticError;
        ZeroDivisionError.base = ArithmeticError;
        AssertionError.base = Exception;
        AttributeError.base = Exception;
        BufferError.base = Exception;
        EOFError.base = Exception;
        ImportError.base = Exception;
        ModuleNotFoundError.base = ImportError;
        LookupError.base = Exception;
        IndexError.base = LookupError;
        KeyError.base = LookupError;
        MemoryError.base = Exception;
        NameError.base = Exception;
        UnboundLocalError.base = NameError;
        OSError.base = Exception;
        BlockingIOError.base = OSError;
        ChildProcessError.base = OSError;
        ConnectionError.base = OSError;
        BrokenPipeError.base = OSError;
        ConnectionAbortedError.base = OSError;
        ConnectionRefusedError.base = OSError;
        ConnectionResetError.base = OSError;
        FileExistsError.base = OSError;
        FileNotFoundError.base = OSError;
        InterruptedError.base = OSError;
        IsADirectoryError.base = OSError;
        NotADirectoryError.base = OSError;
        PermissionError.base = OSError;
        ProcessLookupError.base = OSError;
        TimeoutError.base = OSError;
        ZipImportError.base = ImportError;
        ZLibError.base = Exception;

        ReferenceError.base = Exception;
        RuntimeError.base = Exception;
        NotImplementedError.base = Exception;
        SyntaxError.base = Exception;
        IndentationError.base = SyntaxError;
        TabError.base = IndentationError;
        SystemError.base = Exception;
        TypeError.base = Exception;
        ValueError.base = Exception;
        UnicodeError.base = ValueError;
        UnicodeDecodeError.base = UnicodeError;
        UnicodeEncodeError.base = UnicodeError;
        UnicodeTranslateError.base = UnicodeError;
        RecursionError.base = RuntimeError;

        // warnings
        Warning.base = Exception;
        BytesWarning.base = Warning;
        DeprecationWarning.base = Warning;
        FutureWarning.base = Warning;
        ImportWarning.base = Warning;
        PendingDeprecationWarning.base = Warning;
        ResourceWarning.base = Warning;
        RuntimeWarning.base = Warning;
        SyntaxWarning.base = Warning;
        UnicodeWarning.base = Warning;
        UserWarning.base = Warning;
    }
}
