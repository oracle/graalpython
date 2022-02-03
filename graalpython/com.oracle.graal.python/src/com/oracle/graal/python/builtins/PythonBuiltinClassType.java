/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.BuiltinNames.BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.DEFAULTDICT;
import static com.oracle.graal.python.nodes.BuiltinNames.DEQUE;
import static com.oracle.graal.python.nodes.BuiltinNames.DEQUE_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.DEQUE_REV_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_ITEMS;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_KEYS;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_REVERSE_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_REVERSE_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_REVERSE_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_VALUES;
import static com.oracle.graal.python.nodes.BuiltinNames.FOREIGN;
import static com.oracle.graal.python.nodes.BuiltinNames.MEMBER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.PARTIAL;
import static com.oracle.graal.python.nodes.BuiltinNames.PROPERTY;
import static com.oracle.graal.python.nodes.BuiltinNames.SIMPLE_QUEUE;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE_GETTER;
import static com.oracle.graal.python.nodes.BuiltinNames.WRAPPER_DESCRIPTOR;

import java.util.Arrays;
import java.util.HashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.GraalHPyDebugModuleBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.object.Shape;

// InteropLibrary is proxied through ReflectionLibrary
@ExportLibrary(ReflectionLibrary.class)
public enum PythonBuiltinClassType implements TruffleObject {

    ForeignObject(FOREIGN, Flags.PRIVATE_DERIVED_WODICT),
    Boolean("bool", BUILTINS, Flags.PUBLIC_DERIVED_WODICT),
    GetSetDescriptor("getset_descriptor", Flags.PRIVATE_DERIVED_WODICT),
    MemberDescriptor(MEMBER_DESCRIPTOR, Flags.PRIVATE_DERIVED_WODICT),
    WrapperDescriptor(WRAPPER_DESCRIPTOR, Flags.PRIVATE_DERIVED_WODICT),
    PArray("array", "array"),
    PArrayIterator("arrayiterator", Flags.PRIVATE_DERIVED_WODICT),
    PIterator("iterator", Flags.PRIVATE_DERIVED_WODICT),
    PBuiltinFunction("method_descriptor", Flags.PRIVATE_DERIVED_WODICT),
    PBuiltinMethod("builtin_function_or_method", Flags.PRIVATE_DERIVED_WODICT),
    PBuiltinClassMethod("classmethod_descriptor", Flags.PRIVATE_DERIVED_WODICT),
    PByteArray("bytearray", BUILTINS),
    PBytes("bytes", BUILTINS),
    PCell("cell", Flags.PRIVATE_DERIVED_WODICT),
    PSimpleNamespace("SimpleNamespace", null, "types", Flags.PUBLIC_BASE_WDICT),
    PKeyWrapper("KeyWrapper", "_functools", "functools", Flags.PUBLIC_DERIVED_WODICT),
    PPartial(PARTIAL, "_functools", "functools", Flags.PUBLIC_BASE_WDICT),
    PDefaultDict(DEFAULTDICT, "_collections", "collections", Flags.PUBLIC_BASE_WODICT),
    PDeque(DEQUE, "_collections", Flags.PUBLIC_BASE_WODICT),
    PTupleGetter(TUPLE_GETTER, "_collections", Flags.PUBLIC_BASE_WODICT),
    PDequeIter(DEQUE_ITER, "_collections", Flags.PUBLIC_DERIVED_WODICT),
    PDequeRevIter(DEQUE_REV_ITER, "_collections", Flags.PUBLIC_DERIVED_WODICT),
    PComplex("complex", BUILTINS),
    PDict("dict", BUILTINS),
    PDictItemIterator(DICT_ITEMITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictReverseItemIterator(DICT_REVERSE_ITEMITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictItemsView(DICT_ITEMS, Flags.PRIVATE_DERIVED_WODICT),
    PDictKeyIterator(DICT_KEYITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictReverseKeyIterator(DICT_REVERSE_KEYITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictKeysView(DICT_KEYS, Flags.PRIVATE_DERIVED_WODICT),
    PDictValueIterator(DICT_VALUEITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictReverseValueIterator(DICT_REVERSE_VALUEITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictValuesView(DICT_VALUES, Flags.PRIVATE_DERIVED_WODICT),
    PEllipsis("ellipsis", BUILTINS, Flags.PRIVATE_DERIVED_WODICT),
    PEnumerate("enumerate", BUILTINS),
    PMap("map", BUILTINS),
    PFloat("float", BUILTINS),
    PFrame("frame", Flags.PRIVATE_DERIVED_WODICT),
    PFrozenSet("frozenset", BUILTINS),
    PFunction("function", Flags.PRIVATE_DERIVED_WDICT),
    PGenerator("generator", Flags.PRIVATE_DERIVED_WODICT),
    PInt("int", BUILTINS),
    PList("list", BUILTINS),
    PMappingproxy("mappingproxy", Flags.PRIVATE_DERIVED_WODICT),
    PMemoryView("memoryview", BUILTINS, Flags.PUBLIC_DERIVED_WODICT),
    PMethod("method", Flags.PRIVATE_DERIVED_WODICT),
    PMMap("mmap", "mmap"),
    PNone("NoneType", Flags.PRIVATE_DERIVED_WODICT),
    PNotImplemented("NotImplementedType", Flags.PRIVATE_DERIVED_WODICT),
    PProperty(PROPERTY, BUILTINS, Flags.PUBLIC_BASE_WODICT),
    PSimpleQueue(SIMPLE_QUEUE, "_queue", Flags.PUBLIC_BASE_WODICT),
    PRandom("Random", "_random"),
    PRange("range", BUILTINS, Flags.PUBLIC_DERIVED_WODICT),
    PReferenceType("ReferenceType", "_weakref"),
    PSentinelIterator("callable_iterator", Flags.PRIVATE_DERIVED_WODICT),
    PForeignArrayIterator("foreign_iterator"),
    PReverseIterator("reversed", BUILTINS),
    PSet("set", BUILTINS),
    PSlice("slice", BUILTINS),
    PString("str", BUILTINS),
    PTraceback("traceback"),
    PTuple("tuple", BUILTINS),
    PythonClass("type", BUILTINS, Flags.PUBLIC_BASE_WDICT),
    PythonModule("module", Flags.PRIVATE_BASE_WDICT),
    PythonModuleDef("moduledef", Flags.PRIVATE_DERIVED_WODICT),
    PythonObject("object", BUILTINS),
    Super("super", BUILTINS),
    PCode("code", Flags.PRIVATE_DERIVED_WODICT),
    PZip("zip", BUILTINS),
    PZipImporter("zipimporter", "zipimport"),
    PBuffer("buffer", BUILTINS, Flags.PUBLIC_DERIVED_WODICT),
    PThread("start_new_thread", "_thread"),
    PThreadLocal("_local", "_thread"),
    PLock("LockType", "_thread"),
    PRLock("RLock", "_thread"),
    PSemLock("SemLock", "_multiprocessing"),
    PSocket("socket", "_socket"),
    PStaticmethod("staticmethod", BUILTINS, Flags.PUBLIC_BASE_WDICT),
    PClassmethod("classmethod", BUILTINS, Flags.PUBLIC_BASE_WDICT),
    PInstancemethod("instancemethod", BUILTINS, Flags.PUBLIC_BASE_WDICT),
    PScandirIterator("ScandirIterator", "posix", Flags.PRIVATE_DERIVED_WODICT),
    PDirEntry("DirEntry", "posix", Flags.PUBLIC_DERIVED_WODICT),
    LsprofProfiler("Profiler", "_lsprof"),
    PStruct("Struct", "_struct"),
    PStructUnpackIterator("unpack_iterator", "_struct"),
    Pickler("Pickler", "_pickle"),
    PicklerMemoProxy("PicklerMemoProxy", "_pickle"),
    UnpicklerMemoProxy("UnpicklerMemoProxy", "_pickle"),
    Unpickler("Unpickler", "_pickle"),
    PickleBuffer("PickleBuffer", "_pickle"),

    // bz2
    BZ2Compressor("BZ2Compressor", "_bz2"),
    BZ2Decompressor("BZ2Decompressor", "_bz2"),

    // lzma
    PLZMACompressor("LZMACompressor", "_lzma"),
    PLZMADecompressor("LZMADecompressor", "_lzma"),

    // zlib
    ZlibCompress("Compress", "zlib"),
    ZlibDecompress("Decompress", "zlib"),

    // io
    PIOBase("_IOBase", "_io", Flags.PUBLIC_BASE_WDICT),
    PRawIOBase("_RawIOBase", "_io"),
    PTextIOBase("_TextIOBase", "_io"),
    PBufferedIOBase("_BufferedIOBase", "_io"),
    PBufferedReader("BufferedReader", "_io", Flags.PUBLIC_BASE_WDICT),
    PBufferedWriter("BufferedWriter", "_io", Flags.PUBLIC_BASE_WDICT),
    PBufferedRWPair("BufferedRWPair", "_io", Flags.PUBLIC_BASE_WDICT),
    PBufferedRandom("BufferedRandom", "_io", Flags.PUBLIC_BASE_WDICT),
    PFileIO("FileIO", "_io", Flags.PUBLIC_BASE_WDICT),
    PTextIOWrapper("TextIOWrapper", "_io", Flags.PUBLIC_BASE_WDICT),
    PIncrementalNewlineDecoder("IncrementalNewlineDecoder", "_io", Flags.PUBLIC_BASE_WODICT),
    PStringIO("StringIO", "_io", Flags.PUBLIC_BASE_WDICT),
    PBytesIO("BytesIO", "_io", Flags.PUBLIC_BASE_WDICT),
    PBytesIOBuf("_BytesIOBuffer", "_io", Flags.PRIVATE_BASE_WODICT),

    PStatResult("stat_result", "os", Flags.PUBLIC_DERIVED_WODICT),
    PTerminalSize("terminal_size", "os", Flags.PUBLIC_DERIVED_WODICT),
    PUnameResult("uname_result", "posix", Flags.PUBLIC_DERIVED_WODICT),
    PStructTime("struct_time", "time", Flags.PUBLIC_DERIVED_WODICT),
    PProfilerEntry("profiler_entry", "_lsprof", Flags.PUBLIC_DERIVED_WODICT),
    PProfilerSubentry("profiler_subentry", "_lsprof", Flags.PUBLIC_DERIVED_WODICT),
    PStructPasswd("struct_passwd", "pwd", Flags.PUBLIC_DERIVED_WODICT),
    PStructRusage("struct_rusage", "resource", Flags.PUBLIC_DERIVED_WODICT),
    PVersionInfo("version_info", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PFlags("flags", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PFloatInfo("float_info", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PIntInfo("int_info", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PHashInfo("hash_info", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PThreadInfo("thread_info", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PUnraisableHookArgs("UnraisableHookArgs", "sys", Flags.PUBLIC_DERIVED_WODICT),
    PSSLSession("SSLSession", "_ssl"),
    PSSLContext("_SSLContext", "_ssl"),
    PSSLSocket("_SSLSocket", "_ssl"),
    PMemoryBIO("MemoryBIO", "_ssl"),

    // itertools
    PTee("_tee", "itertools", Flags.PUBLIC_DERIVED_WODICT),
    PTeeDataObject("_tee_dataobject", "itertools", Flags.PUBLIC_DERIVED_WODICT),
    PAccumulate("accumulate", "itertools"),
    PCombinations("combinations", "itertools"),
    PCombinationsWithReplacement("combinations_with_replacement", "itertools"),
    PCompress("compress", "itertools"),
    PCycle("cycle", "itertools"),
    PDropwhile("dropwhile", "itertools"),
    PFilterfalse("filterfalse", "itertools"),
    PGroupBy("groupby", "itertools"),
    PGrouper("grouper", "itertools", Flags.PUBLIC_DERIVED_WODICT),
    PPermutations("permutations", "itertools"),
    PProduct("product", "itertools"),
    PRepeat("repeat", "itertools"),
    PChain("chain", "itertools"),
    PCount("count", "itertools"),
    PIslice("islice", "itertools"),
    PStarmap("starmap", "itertools"),
    PTakewhile("takewhile", "itertools"),
    PZipLongest("zip_longest", "itertools"),

    // json
    JSONScanner("Scanner", "_json", Flags.PUBLIC_BASE_WODICT),
    JSONEncoder("Encoder", "_json", Flags.PUBLIC_BASE_WODICT),

    // csv
    CSVDialect("Dialect", "_csv", Flags.PUBLIC_BASE_WODICT),
    CSVReader("Reader", "_csv", Flags.PUBLIC_BASE_WODICT),
    CSVWriter("Writer", "_csv", Flags.PUBLIC_BASE_WODICT),

    // _ast (rest of the classes are not builtin, they are generated in AstModuleBuiltins)
    AST("AST", "_ast", Flags.PUBLIC_BASE_WDICT),

    // HPy
    DebugHandle("DebugHandle", GraalHPyDebugModuleBuiltins.HPY_DEBUG, Flags.PUBLIC_DERIVED_WODICT),

    // _ctype
    CArgObject("CArgObject", Flags.PUBLIC_BASE_WDICT),
    CThunkObject("CThunkObject", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    StgDict("StgDict", Flags.PRIVATE_DERIVED_WODICT),
    PyCStructType("PyCStructType", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    UnionType("UnionType", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    PyCPointerType("PyCPointerType", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    PyCArrayType("PyCArrayType", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    PyCSimpleType("PyCSimpleType", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    PyCFuncPtrType("PyCFuncPtrType", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    Structure("Structure", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = PyCStructType
    Union("Union", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = UnionType
    PyCPointer("_Pointer", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = PyCPointerType
    PyCArray("Array", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = PyCArrayType
    PyCData("_CData", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = PyCStructType
    SimpleCData("_SimpleCData", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = PyCStructType
    PyCFuncPtr("PyCFuncPtr", "_ctypes", Flags.PUBLIC_BASE_WDICT), // type = PyCFuncPtrType
    CField("CField", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    DictRemover("DictRemover", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    StructParam("StructParam_Type", "_ctypes", Flags.PUBLIC_BASE_WDICT),
    ArgError("ArgumentError", "ctypes", Flags.EXCEPTION),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", BUILTINS, Flags.EXCEPTION),
    SystemExit("SystemExit", BUILTINS, Flags.EXCEPTION),
    KeyboardInterrupt("KeyboardInterrupt", BUILTINS, Flags.EXCEPTION),
    GeneratorExit("GeneratorExit", BUILTINS, Flags.EXCEPTION),
    Exception("Exception", BUILTINS, Flags.EXCEPTION),
    StopIteration("StopIteration", BUILTINS, Flags.EXCEPTION),
    StopAsyncIteration("StopAsyncIteration", BUILTINS, Flags.EXCEPTION),
    ArithmeticError("ArithmeticError", BUILTINS, Flags.EXCEPTION),
    FloatingPointError("FloatingPointError", BUILTINS, Flags.EXCEPTION),
    OverflowError("OverflowError", BUILTINS, Flags.EXCEPTION),
    ZeroDivisionError("ZeroDivisionError", BUILTINS, Flags.EXCEPTION),
    AssertionError("AssertionError", BUILTINS, Flags.EXCEPTION),
    AttributeError("AttributeError", BUILTINS, Flags.EXCEPTION),
    BufferError("BufferError", BUILTINS, Flags.EXCEPTION),
    EOFError("EOFError", BUILTINS, Flags.EXCEPTION),
    ImportError("ImportError", BUILTINS, Flags.EXCEPTION),
    ModuleNotFoundError("ModuleNotFoundError", BUILTINS, Flags.EXCEPTION),
    LookupError("LookupError", BUILTINS, Flags.EXCEPTION),
    IndexError("IndexError", BUILTINS, Flags.EXCEPTION),
    KeyError("KeyError", BUILTINS, Flags.EXCEPTION),
    MemoryError("MemoryError", BUILTINS, Flags.EXCEPTION),
    NameError("NameError", BUILTINS, Flags.EXCEPTION),
    UnboundLocalError("UnboundLocalError", BUILTINS, Flags.EXCEPTION),
    OSError("OSError", BUILTINS, Flags.EXCEPTION),
    BlockingIOError("BlockingIOError", BUILTINS, Flags.EXCEPTION),
    ChildProcessError("ChildProcessError", BUILTINS, Flags.EXCEPTION),
    ConnectionError("ConnectionError", BUILTINS, Flags.EXCEPTION),
    BrokenPipeError("BrokenPipeError", BUILTINS, Flags.EXCEPTION),
    ConnectionAbortedError("ConnectionAbortedError", BUILTINS, Flags.EXCEPTION),
    ConnectionRefusedError("ConnectionRefusedError", BUILTINS, Flags.EXCEPTION),
    ConnectionResetError("ConnectionResetError", BUILTINS, Flags.EXCEPTION),
    FileExistsError("FileExistsError", BUILTINS, Flags.EXCEPTION),
    FileNotFoundError("FileNotFoundError", BUILTINS, Flags.EXCEPTION),
    InterruptedError("InterruptedError", BUILTINS, Flags.EXCEPTION),
    IsADirectoryError("IsADirectoryError", BUILTINS, Flags.EXCEPTION),
    NotADirectoryError("NotADirectoryError", BUILTINS, Flags.EXCEPTION),
    PermissionError("PermissionError", BUILTINS, Flags.EXCEPTION),
    ProcessLookupError("ProcessLookupError", BUILTINS, Flags.EXCEPTION),
    TimeoutError("TimeoutError", BUILTINS, Flags.EXCEPTION),
    ZipImportError("ZipImportError", "zipimport", Flags.EXCEPTION),
    ZLibError("error", "zlib", Flags.EXCEPTION),
    CSVError("Error", "_csv", Flags.EXCEPTION),
    LZMAError("LZMAError", "_lzma", Flags.EXCEPTION),
    StructError("StructError", "_struct", Flags.EXCEPTION),
    PickleError("PickleError", "_pickle", Flags.EXCEPTION),
    PicklingError("PicklingError", "_pickle", Flags.EXCEPTION),
    UnpicklingError("UnpicklingError", "_pickle", Flags.EXCEPTION),
    SocketGAIError("gaierror", "_socket", Flags.EXCEPTION),
    SocketHError("herror", "_socket", Flags.EXCEPTION),
    SocketTimeout("timeout", "_socket", Flags.EXCEPTION),
    BinasciiError("Error", "binascii", Flags.EXCEPTION),
    BinasciiIncomplete("Incomplete", "binascii", Flags.EXCEPTION),
    SSLError("SSLError", "_ssl", Flags.EXCEPTION),
    SSLZeroReturnError("SSLZeroReturnError", "_ssl", Flags.EXCEPTION),
    SSLWantReadError("SSLWantReadError", "_ssl", Flags.EXCEPTION),
    SSLWantWriteError("SSLWantWriteError", "_ssl", Flags.EXCEPTION),
    SSLSyscallError("SSLSyscallError", "_ssl", Flags.EXCEPTION),
    SSLEOFError("SSLEOFError", "_ssl", Flags.EXCEPTION),
    SSLCertVerificationError("SSLCertVerificationError", "_ssl", Flags.EXCEPTION),
    PForeignException("ForeignException", Flags.FOREIGN_EXCEPTION),

    // todo: all OS errors

    ReferenceError("ReferenceError", BUILTINS, Flags.EXCEPTION),
    RuntimeError("RuntimeError", BUILTINS, Flags.EXCEPTION),
    NotImplementedError("NotImplementedError", BUILTINS, Flags.EXCEPTION),
    SyntaxError("SyntaxError", BUILTINS, Flags.EXCEPTION),
    IndentationError("IndentationError", BUILTINS, Flags.EXCEPTION),
    TabError("TabError", BUILTINS, Flags.EXCEPTION),
    SystemError("SystemError", BUILTINS, Flags.EXCEPTION),
    TypeError("TypeError", BUILTINS, Flags.EXCEPTION),
    ValueError("ValueError", BUILTINS, Flags.EXCEPTION),
    UnicodeError("UnicodeError", BUILTINS, Flags.EXCEPTION),
    UnicodeDecodeError("UnicodeDecodeError", BUILTINS, Flags.EXCEPTION),
    UnicodeEncodeError("UnicodeEncodeError", BUILTINS, Flags.EXCEPTION),
    UnicodeTranslateError("UnicodeTranslateError", BUILTINS, Flags.EXCEPTION),
    RecursionError("RecursionError", BUILTINS, Flags.EXCEPTION),

    IOUnsupportedOperation("UnsupportedOperation", "io", Flags.EXCEPTION),

    Empty("Empty", "_queue", Flags.EXCEPTION),

    // warnings
    Warning("Warning", BUILTINS, Flags.EXCEPTION),
    BytesWarning("BytesWarning", BUILTINS, Flags.EXCEPTION),
    DeprecationWarning("DeprecationWarning", BUILTINS, Flags.EXCEPTION),
    FutureWarning("FutureWarning", BUILTINS, Flags.EXCEPTION),
    ImportWarning("ImportWarning", BUILTINS, Flags.EXCEPTION),
    PendingDeprecationWarning("PendingDeprecationWarning", BUILTINS, Flags.EXCEPTION),
    ResourceWarning("ResourceWarning", BUILTINS, Flags.EXCEPTION),
    RuntimeWarning("RuntimeWarning", BUILTINS, Flags.EXCEPTION),
    SyntaxWarning("SyntaxWarning", BUILTINS, Flags.EXCEPTION),
    UnicodeWarning("UnicodeWarning", BUILTINS, Flags.EXCEPTION),
    UserWarning("UserWarning", BUILTINS, Flags.EXCEPTION),

    // A marker for @Builtin that is not a class. Must always come last.
    nil(null);

    private static class Flags {

        static final Flags EXCEPTION = new Flags(true, true, true);
        static final Flags FOREIGN_EXCEPTION = new Flags(false, false, true);
        static final Flags PRIVATE_DERIVED_WDICT = new Flags(false, false, true);
        static final Flags PRIVATE_BASE_WDICT = new Flags(false, true, true);
        static final Flags PRIVATE_BASE_WODICT = new Flags(false, true, false);
        static final Flags PUBLIC_BASE_WDICT = new Flags(true, true, true);
        static final Flags PUBLIC_BASE_WODICT = new Flags(true, true, false);
        static final Flags PUBLIC_DERIVED_WODICT = new Flags(true, false, false);
        static final Flags PRIVATE_DERIVED_WODICT = new Flags(false, false, false);

        final boolean isPublic;
        final boolean isBaseType;
        final boolean isBuiltinWithDict;

        Flags(boolean isPublic, boolean isBaseType, boolean isBuiltinWithDict) {
            this.isPublic = isPublic;
            this.isBaseType = isBaseType;
            this.isBuiltinWithDict = isBuiltinWithDict;
        }
    }

    private final String name;
    private final String publishInModule;
    private final String moduleName;
    // This is the name qualified by module used for printing. But the actual __qualname__ is just
    // plain name without module
    private final String printName;
    private final boolean basetype;
    private final boolean isBuiltinWithDict;
    private final boolean isException;

    // initialized in static constructor
    @CompilationFinal private PythonBuiltinClassType type;
    @CompilationFinal private PythonBuiltinClassType base;

    /**
     * @see #redefinesSlot(SpecialMethodSlot)
     */
    private SpecialMethodSlot[] redefinedSlots;

    /**
     * Lookup cache for special slots defined in {@link SpecialMethodSlot}. Use
     * {@link SpecialMethodSlot} to access the values. Unlike the cache in
     * {@link com.oracle.graal.python.builtins.objects.type.PythonManagedClass}, this caches only
     * builtin context independent values, most notably instances of {@link BuiltinMethodDescriptor}
     * .
     */
    private Object[] specialMethodSlots;

    PythonBuiltinClassType(String name, String module, Flags flags) {
        this(name, module, module, flags);
    }

    PythonBuiltinClassType(String name, String publishInModule, String moduleName, Flags flags) {
        this.name = name;
        this.publishInModule = publishInModule;
        this.moduleName = flags.isPublic ? moduleName : null;
        if (moduleName != null && moduleName != BUILTINS) {
            printName = moduleName + "." + name;
        } else {
            printName = name;
        }
        this.basetype = flags.isBaseType;
        this.isBuiltinWithDict = flags.isBuiltinWithDict;
        this.isException = flags == Flags.EXCEPTION;
    }

    PythonBuiltinClassType(String name, String module) {
        this(name, module, Flags.PUBLIC_BASE_WODICT);
    }

    PythonBuiltinClassType(String name, Flags flags) {
        this(name, null, flags);
    }

    PythonBuiltinClassType(String name) {
        this(name, null, Flags.PRIVATE_BASE_WODICT);
    }

    public boolean isAcceptableBase() {
        return basetype;
    }

    public String getName() {
        return name;
    }

    public String getPrintName() {
        return printName;
    }

    public PythonBuiltinClassType getType() {
        return type;
    }

    public PythonBuiltinClassType getBase() {
        return base;
    }

    public boolean isBuiltinWithDict() {
        return isBuiltinWithDict;
    }

    public String getPublishInModule() {
        return publishInModule;
    }

    public String getModuleName() {
        return moduleName;
    }

    /**
     * Access the values using methods in {@link SpecialMethodSlot}.
     */
    public Object[] getSpecialMethodSlots() {
        return specialMethodSlots;
    }

    public void setSpecialMethodSlots(Object[] slots) {
        assert specialMethodSlots == null; // should be assigned only once per VM
        specialMethodSlots = slots;
    }

    /**
     * Returns {@code true} if this method slot is redefined in Python code during initialization.
     * Values of such slots cannot be cached in {@link #specialMethodSlots}, because they are not
     * context independent.
     */
    public boolean redefinesSlot(SpecialMethodSlot slot) {
        if (redefinedSlots != null) {
            for (SpecialMethodSlot redefSlot : redefinedSlots) {
                if (redefSlot == slot) {
                    return true;
                }
            }
        }
        if (base != null) {
            return base.redefinesSlot(slot);
        }
        return false;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return name;
    }

    public final Shape getInstanceShape(PythonLanguage lang) {
        if (name == null) {
            throw CompilerDirectives.shouldNotReachHere("incorrect use of Python builtin type marker");
        }
        return lang.getBuiltinTypeInstanceShape(this);
    }

    @CompilationFinal(dimensions = 1) public static final PythonBuiltinClassType[] VALUES = Arrays.copyOf(values(), values().length - 1);

    static {
        // fill the overridden slots
        SpecialMethodSlot[] repr = new SpecialMethodSlot[]{SpecialMethodSlot.Repr};
        SpecialMethodSlot[] reprAndNew = new SpecialMethodSlot[]{SpecialMethodSlot.Repr, SpecialMethodSlot.New};

        Boolean.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.And};
        PythonModule.redefinedSlots = Super.redefinedSlots = repr;
        SyntaxError.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.Str};
        UnicodeEncodeError.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.Str};
        UnicodeDecodeError.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.Str};
        UnicodeTranslateError.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.Str};
        OSError.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.Str};

        // These slots actually contain context independent values, but they are initialized in
        // StructSequence to artificial PBuiltinFunctions with artificial builtin node factories,
        // which are different for each context. We'd have to turn those factories into singletons
        // to guarantee their identity across contexts. For the sake of simplicity, we just ignore
        // those slots for now.
        PStruct.type = PythonClass;
        PStructRusage.redefinedSlots = reprAndNew;
        PStructPasswd.redefinedSlots = reprAndNew;
        PUnameResult.redefinedSlots = reprAndNew;
        PUnraisableHookArgs.redefinedSlots = reprAndNew;
        PIntInfo.redefinedSlots = reprAndNew;
        PHashInfo.redefinedSlots = reprAndNew;
        PStructTime.redefinedSlots = reprAndNew;
        PProfilerEntry.redefinedSlots = reprAndNew;
        PProfilerSubentry.redefinedSlots = reprAndNew;
        PThreadInfo.redefinedSlots = reprAndNew;
        PStatResult.redefinedSlots = repr;
        PFloatInfo.redefinedSlots = reprAndNew;
        PVersionInfo.redefinedSlots = repr;
        PFlags.redefinedSlots = repr;
        PTerminalSize.redefinedSlots = reprAndNew;

        PythonObject.type = PythonClass;
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
        CSVError.base = Exception;
        LZMAError.base = Exception;
        SocketGAIError.base = OSError;
        SocketHError.base = OSError;
        SocketTimeout.base = OSError;

        SSLError.base = OSError;
        SSLZeroReturnError.base = SSLError;
        SSLWantReadError.base = SSLError;
        SSLWantWriteError.base = SSLError;
        SSLSyscallError.base = SSLError;
        SSLCertVerificationError.base = SSLError;
        SSLEOFError.base = SSLError;

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
        StructError.base = Exception;
        BinasciiError.base = ValueError;
        BinasciiIncomplete.base = Exception;
        PickleError.base = Exception;
        PicklingError.base = PickleError;
        UnpicklingError.base = PickleError;

        PForeignException.base = PBaseException;

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

        PStatResult.base = PTuple;
        PTerminalSize.base = PTuple;
        PUnameResult.base = PTuple;
        PStructTime.base = PTuple;
        PProfilerEntry.base = PTuple;
        PProfilerSubentry.base = PTuple;
        PStructPasswd.base = PTuple;
        PStructRusage.base = PTuple;
        PVersionInfo.base = PTuple;
        PFlags.base = PTuple;
        PFloatInfo.base = PTuple;
        PIntInfo.base = PTuple;
        PHashInfo.base = PTuple;
        PThreadInfo.base = PTuple;
        PUnraisableHookArgs.base = PTuple;
        PDefaultDict.base = PDict;

        PArrayIterator.type = PythonClass;
        PSocket.type = PythonClass;

        // _io.UnsupportedOperation inherits from ValueError and OSError
        // done currently within IOModuleBuiltins class
        IOUnsupportedOperation.base = OSError;

        PRawIOBase.base = PIOBase;
        PTextIOBase.base = PIOBase;
        PBufferedIOBase.base = PIOBase;
        PBufferedReader.base = PBufferedIOBase;
        PBufferedWriter.base = PBufferedIOBase;
        PBufferedRWPair.base = PBufferedIOBase;
        PBufferedRandom.base = PBufferedIOBase;
        PBytesIO.base = PBufferedIOBase;
        PFileIO.base = PRawIOBase;
        PTextIOWrapper.base = PTextIOBase;
        PStringIO.base = PTextIOBase;

        // _ctypes
        StgDict.base = PDict;
        PyCStructType.base = PythonClass;
        UnionType.base = PythonClass;
        PyCPointerType.base = PythonClass;
        PyCArrayType.base = PythonClass;
        PyCSimpleType.base = PythonClass;
        PyCFuncPtrType.base = PythonClass;
        Structure.type = PyCStructType;
        Structure.base = PyCData;
        Union.type = UnionType;
        Union.base = PyCData;
        PyCPointer.type = PyCPointerType;
        PyCPointer.base = PyCData;
        PyCArray.type = PyCArrayType;
        PyCArray.base = PyCData;
        SimpleCData.type = PyCSimpleType;
        SimpleCData.base = PyCData;
        PyCFuncPtr.type = PyCFuncPtrType;
        PyCFuncPtr.base = PyCData;

        Empty.base = Exception;

        HashSet<String> set = PythonUtils.ASSERTIONS_ENABLED ? new HashSet<>() : null;
        for (PythonBuiltinClassType type : VALUES) {
            // check uniqueness
            assert set.add("" + type.moduleName + "." + type.name) : type.name();

            /* Initialize type.base (defaults to PythonObject unless that's us) */
            if (type.base == null && type != PythonObject) {
                type.base = PythonObject;
            }

            /*
             * Now the only way base can still be null is if type is PythonObject.
             */
            if (type.type == null && type.base != null) {
                type.type = type.base.type;
            }
        }

        // Finally, we set all remaining types to PythonClass.
        for (PythonBuiltinClassType type : VALUES) {
            if (type.type == null) {
                type.type = PythonClass;
            }
        }
    }

    // Proxy InteropLibrary messages to the PythonBuiltinClass
    @ExportMessage
    public Object send(Message message, Object[] args,
                    @CachedLibrary(limit = "1") ReflectionLibrary lib) throws Exception {
        return lib.send(PythonContext.get(lib).lookupType(this), message, args);
    }

    public static boolean isExceptionType(PythonBuiltinClassType type) {
        return type.isException;
    }
}
