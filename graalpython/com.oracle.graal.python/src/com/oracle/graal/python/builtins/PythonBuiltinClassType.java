/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.HashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonObjectLibrary.class)
public enum PythonBuiltinClassType implements TruffleObject {

    ForeignObject(BuiltinNames.FOREIGN, false),
    Boolean("bool", BuiltinNames.BUILTINS, false),
    GetSetDescriptor("get_set_desc", false),
    PArray("array", "array"),
    PArrayIterator("arrayiterator", false),
    PIterator("iterator", false),
    PBuiltinFunction("method_descriptor", false),
    PBuiltinMethod("builtin_function_or_method", false),
    PByteArray("bytearray", BuiltinNames.BUILTINS),
    PBytes("bytes", BuiltinNames.BUILTINS),
    PCell("cell", false),
    PComplex("complex", BuiltinNames.BUILTINS),
    PDict("dict", BuiltinNames.BUILTINS),
    PDictItemIterator(BuiltinNames.DICT_ITEMITERATOR, false),
    PDictReverseItemIterator(BuiltinNames.DICT_REVERSE_ITEMITERATOR, false),
    PDictItemsView(BuiltinNames.DICT_ITEMS, false),
    PDictKeyIterator(BuiltinNames.DICT_KEYITERATOR, false),
    PDictReverseKeyIterator(BuiltinNames.DICT_REVERSE_KEYITERATOR, false),
    PDictKeysView(BuiltinNames.DICT_KEYS, false),
    PDictValueIterator(BuiltinNames.DICT_VALUEITERATOR, false),
    PDictReverseValueIterator(BuiltinNames.DICT_REVERSE_VALUEITERATOR, false),
    PDictValuesView(BuiltinNames.DICT_VALUES, false),
    PEllipsis("ellipsis", false),
    PEnumerate("enumerate", BuiltinNames.BUILTINS),
    PMap("map", BuiltinNames.BUILTINS),
    PFloat("float", BuiltinNames.BUILTINS),
    PFrame("frame", false),
    PFrozenSet("frozenset", BuiltinNames.BUILTINS),
    PFunction("function", false),
    PGenerator("generator", false),
    PInt("int", BuiltinNames.BUILTINS),
    PList("list", BuiltinNames.BUILTINS),
    PMappingproxy("mappingproxy", false),
    PMemoryView("memoryview", BuiltinNames.BUILTINS),
    PMethod("method", false),
    PMMap("mmap", "mmap"),
    PNone("NoneType", false),
    PNotImplemented("NotImplementedType", false),
    PRandom("Random", "_random"),
    PRange("range", BuiltinNames.BUILTINS, false),
    PReferenceType("ReferenceType", "_weakref"),
    PSentinelIterator("callable_iterator", false),
    PForeignArrayIterator("foreign_iterator"),
    PReverseIterator("reversed", BuiltinNames.BUILTINS),
    PSet("set", BuiltinNames.BUILTINS),
    PSlice("slice", BuiltinNames.BUILTINS),
    PString("str", BuiltinNames.BUILTINS),
    PTraceback("traceback"),
    PTuple("tuple", BuiltinNames.BUILTINS),
    PythonClass("type", BuiltinNames.BUILTINS),
    PythonModule("module"),
    PythonObject("object", BuiltinNames.BUILTINS),
    Super("super", BuiltinNames.BUILTINS),
    PCode("code", false),
    PZip("zip", BuiltinNames.BUILTINS),
    PZipImporter("zipimporter", "zipimport"),
    PBuffer("buffer", BuiltinNames.BUILTINS, false),
    PThread("start_new_thread", "_thread"),
    PLock("LockType", "_thread"),
    PRLock("RLock", "_thread"),
    PSemLock("SemLock", "_multiprocessing"),
    PSocket("socket", "_socket"),
    PStaticmethod("staticmethod", BuiltinNames.BUILTINS),
    PClassmethod("classmethod", BuiltinNames.BUILTINS),
    PScandirIterator("ScandirIterator", "posix", false),
    PDirEntry("DirEntry", "posix"),
    PLZMACompressor("LZMACompressor", "_lzma"),
    PLZMADecompressor("LZMADecompressor", "_lzma"),
    LsprofProfiler("Profiler", "_lsprof"),
    PStruct("Struct", "_struct"),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", BuiltinNames.BUILTINS),
    SystemExit("SystemExit", BuiltinNames.BUILTINS),
    KeyboardInterrupt("KeyboardInterrupt", BuiltinNames.BUILTINS),
    GeneratorExit("GeneratorExit", BuiltinNames.BUILTINS),
    Exception("Exception", BuiltinNames.BUILTINS),
    StopIteration("StopIteration", BuiltinNames.BUILTINS),
    StopAsyncIteration("StopAsyncIteration", BuiltinNames.BUILTINS),
    ArithmeticError("ArithmeticError", BuiltinNames.BUILTINS),
    FloatingPointError("FloatingPointError", BuiltinNames.BUILTINS),
    OverflowError("OverflowError", BuiltinNames.BUILTINS),
    ZeroDivisionError("ZeroDivisionError", BuiltinNames.BUILTINS),
    AssertionError("AssertionError", BuiltinNames.BUILTINS),
    AttributeError("AttributeError", BuiltinNames.BUILTINS),
    BufferError("BufferError", BuiltinNames.BUILTINS),
    EOFError("EOFError", BuiltinNames.BUILTINS),
    ImportError("ImportError", BuiltinNames.BUILTINS),
    ModuleNotFoundError("ModuleNotFoundError", BuiltinNames.BUILTINS),
    LookupError("LookupError", BuiltinNames.BUILTINS),
    IndexError("IndexError", BuiltinNames.BUILTINS),
    KeyError("KeyError", BuiltinNames.BUILTINS),
    MemoryError("MemoryError", BuiltinNames.BUILTINS),
    NameError("NameError", BuiltinNames.BUILTINS),
    UnboundLocalError("UnboundLocalError", BuiltinNames.BUILTINS),
    OSError("OSError", BuiltinNames.BUILTINS),
    BlockingIOError("BlockingIOError", BuiltinNames.BUILTINS),
    ChildProcessError("ChildProcessError", BuiltinNames.BUILTINS),
    ConnectionError("ConnectionError", BuiltinNames.BUILTINS),
    BrokenPipeError("BrokenPipeError", BuiltinNames.BUILTINS),
    ConnectionAbortedError("ConnectionAbortedError", BuiltinNames.BUILTINS),
    ConnectionRefusedError("ConnectionRefusedError", BuiltinNames.BUILTINS),
    ConnectionResetError("ConnectionResetError", BuiltinNames.BUILTINS),
    FileExistsError("FileExistsError", BuiltinNames.BUILTINS),
    FileNotFoundError("FileNotFoundError", BuiltinNames.BUILTINS),
    InterruptedError("InterruptedError", BuiltinNames.BUILTINS),
    IsADirectoryError("IsADirectoryError", BuiltinNames.BUILTINS),
    NotADirectoryError("NotADirectoryError", BuiltinNames.BUILTINS),
    PermissionError("PermissionError", BuiltinNames.BUILTINS),
    ProcessLookupError("ProcessLookupError", BuiltinNames.BUILTINS),
    TimeoutError("TimeoutError", BuiltinNames.BUILTINS),
    ZipImportError("ZipImportError", "zipimport"),
    ZLibError("error", "zlib"),
    LZMAError("LZMAError", "_lzma"),
    StructError("StructError", "_struct"),
    SocketGAIError("gaierror", "_socket"),
    SocketHError("herror", "_socket"),
    SocketTimeout("timeout", "_socket"),

    // todo: all OS errors

    ReferenceError("ReferenceError", BuiltinNames.BUILTINS),
    RuntimeError("RuntimeError", BuiltinNames.BUILTINS),
    NotImplementedError("NotImplementedError", BuiltinNames.BUILTINS),
    SyntaxError("SyntaxError", BuiltinNames.BUILTINS),
    IndentationError("IndentationError", BuiltinNames.BUILTINS),
    TabError("TabError", BuiltinNames.BUILTINS),
    SystemError("SystemError", BuiltinNames.BUILTINS),
    TypeError("TypeError", BuiltinNames.BUILTINS),
    ValueError("ValueError", BuiltinNames.BUILTINS),
    UnicodeError("UnicodeError", BuiltinNames.BUILTINS),
    UnicodeDecodeError("UnicodeDecodeError", BuiltinNames.BUILTINS),
    UnicodeEncodeError("UnicodeEncodeError", BuiltinNames.BUILTINS),
    UnicodeTranslateError("UnicodeTranslateError", BuiltinNames.BUILTINS),
    RecursionError("RecursionError", BuiltinNames.BUILTINS),

    // warnings
    Warning("Warning", BuiltinNames.BUILTINS),
    BytesWarning("BytesWarning", BuiltinNames.BUILTINS),
    DeprecationWarning("DeprecationWarning", BuiltinNames.BUILTINS),
    FutureWarning("FutureWarning", BuiltinNames.BUILTINS),
    ImportWarning("ImportWarning", BuiltinNames.BUILTINS),
    PendingDeprecationWarning("PendingDeprecationWarning", BuiltinNames.BUILTINS),
    ResourceWarning("ResourceWarning", BuiltinNames.BUILTINS),
    RuntimeWarning("RuntimeWarning", BuiltinNames.BUILTINS),
    SyntaxWarning("SyntaxWarning", BuiltinNames.BUILTINS),
    UnicodeWarning("UnicodeWarning", BuiltinNames.BUILTINS),
    UserWarning("UserWarning", BuiltinNames.BUILTINS),

    // A marker for @Builtin that is not a class. Must always come last.
    nil(null);

    private final String name;
    private final String publicInModule;
    private final String qualifiedName;
    private final boolean basetype;

    // initialized in static constructor
    @CompilationFinal private PythonBuiltinClassType base;

    PythonBuiltinClassType(String name, String publicInModule, boolean basetype) {
        this.name = name;
        this.publicInModule = publicInModule;
        if (publicInModule != null && publicInModule != BuiltinNames.BUILTINS) {
            qualifiedName = publicInModule + "." + name;
        } else {
            qualifiedName = name;
        }
        this.basetype = basetype;
    }

    PythonBuiltinClassType(String name, String publicInModule) {
        this(name, publicInModule, true);
    }

    PythonBuiltinClassType(String name, boolean basetype) {
        this(name, null, basetype);
    }

    PythonBuiltinClassType(String name) {
        this(name, null, true);
    }

    public boolean isAcceptableBase() {
        return basetype;
    }

    public String getName() {
        return name;
    }

    public String getQualifiedName() {
        return qualifiedName;
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

    public final Shape getInstanceShape(PythonLanguage lang) {
        if (name == null) {
            CompilerDirectives.shouldNotReachHere("incorrect use of Python builtin type marker");
        }
        return lang.getBuiltinTypeInstanceShape(this);
    }

    @CompilationFinal(dimensions = 1) public static final PythonBuiltinClassType[] VALUES = Arrays.copyOf(values(), values().length - 1);
    @CompilationFinal(dimensions = 1) public static final PythonBuiltinClassType[] EXCEPTIONS;

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

        PythonObject.base = null;

        Boolean.base = PInt;

        SystemExit.base = PBaseException;
        KeyboardInterrupt.base = PBaseException;
        GeneratorExit.base = PBaseException;
        Exception.base = PBaseException;
        StopIteration.base = Exception;
        StopAsyncIteration.base = Exception;
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
        BrokenPipeError.base = ConnectionError;
        ConnectionAbortedError.base = ConnectionError;
        ConnectionRefusedError.base = ConnectionError;
        ConnectionResetError.base = ConnectionError;
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
        LZMAError.base = Exception;
        SocketGAIError.base = OSError;
        SocketHError.base = OSError;
        SocketTimeout.base = OSError;

        ReferenceError.base = Exception;
        RuntimeError.base = Exception;
        NotImplementedError.base = RuntimeError;
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

    /* InteropLibrary messages */
    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    public Object execute(Object[] arguments,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return lib.execute(context.getCore().lookupType(this), arguments);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isInstantiable() {
        return true;
    }

    @ExportMessage
    public Object instantiate(Object[] arguments,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return lib.instantiate(context.getCore().lookupType(this), arguments);
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    public Object getMembers(boolean includeInternal,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedMessageException {
        return lib.getMembers(context.getCore().lookupType(this), includeInternal);
    }

    @ExportMessage
    public boolean isMemberReadable(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.isMemberReadable(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public Object readMember(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedMessageException, UnknownIdentifierException {
        return lib.readMember(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public boolean isMemberModifiable(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.isMemberModifiable(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public boolean isMemberInsertable(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.isMemberInsertable(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public void writeMember(String key, Object value,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        lib.writeMember(context.getCore().lookupType(this), key, value);
    }

    @ExportMessage
    public boolean isMemberRemovable(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.isMemberRemovable(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public void removeMember(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedMessageException, UnknownIdentifierException {
        lib.removeMember(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public boolean isMemberInvocable(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.isMemberInvocable(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public Object invokeMember(String key, Object[] arguments,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        return lib.invokeMember(context.getCore().lookupType(this), key, arguments);
    }

    @ExportMessage
    public boolean isMemberInternal(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.isMemberInternal(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public boolean hasMemberReadSideEffects(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.hasMemberReadSideEffects(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    public boolean hasMemberWriteSideEffects(String key,
                    @CachedLibrary(limit = "1") InteropLibrary lib,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return lib.hasMemberWriteSideEffects(context.getCore().lookupType(this), key);
    }

    @ExportMessage
    static boolean isSequenceType(PythonBuiltinClassType type,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
        return lib.isSequenceType(context.getCore().lookupType(type));
    }

    @ExportMessage
    static boolean isMappingType(PythonBuiltinClassType type,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
        return lib.isMappingType(context.getCore().lookupType(type));
    }

    @ExportMessage
    static long hashWithState(PythonBuiltinClassType type, ThreadState state,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
        return lib.hashWithState(context.getCore().lookupType(type), state);
    }

    @ExportMessage
    static Object getLazyPythonClass(@SuppressWarnings("unused") PythonBuiltinClassType type) {
        return PythonClass;
    }

    @ExportMessage
    static class IsSame {
        @Specialization
        static boolean tt(PythonBuiltinClassType receiver, PythonBuiltinClassType other) {
            return receiver == other;
        }

        @Specialization
        static boolean tc(PythonBuiltinClassType receiver, PythonBuiltinClass other) {
            return receiver == other.getType();
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean tO(PythonBuiltinClassType receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    static int equalsInternal(PythonBuiltinClassType self, Object other, @SuppressWarnings("unused") ThreadState state,
                    @CachedLibrary("self") PythonObjectLibrary selfLib) {
        return selfLib.isSame(self, other) ? 1 : 0;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isCallable() {
        return true;
    }

    @ExportMessage
    public Object callObjectWithState(ThreadState state, Object[] arguments,
                    @CachedContext(PythonLanguage.class) PythonContext ctx,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
        return lib.callObjectWithState(ctx.getCore().lookupType(this), state, arguments);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isLazyPythonClass() {
        return true;
    }

    @ExportMessage
    static boolean isMetaObject(@SuppressWarnings("unused") PythonBuiltinClassType self) {
        return true;
    }

    @ExportMessage
    static boolean isMetaInstance(PythonBuiltinClassType self, Object instance,
                    @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Cached IsSubtypeNode isSubtype) {
        return isSubtype.execute(lib.getLazyPythonClass(instance), self);
    }

    @ExportMessage
    static String getMetaSimpleName(PythonBuiltinClassType self) {
        return self.getName();
    }

    @ExportMessage
    static String getMetaQualifiedName(PythonBuiltinClassType self) {
        return self.getQualifiedName();
    }

    @ExplodeLoop
    public static boolean isExceptionType(PythonBuiltinClassType type) {
        for (int i = 0; i < EXCEPTIONS.length; i++) {
            if (EXCEPTIONS[i] == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * Must be kept in sync with
     * {@link com.oracle.graal.python.builtins.objects.type.TypeBuiltins.ReprNode
     * TypeBuiltins.ReprNode}
     */
    @ExportMessage
    String asPString() {
        return getQualifiedName();
    }

    @ExportMessage
    String asPStringWithState(@SuppressWarnings("unused") ThreadState state) {
        return asPString();
    }
}
