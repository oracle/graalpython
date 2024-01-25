/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.ARRAY_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.ASYNC_GENERATOR_ASEND_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.ASYNC_GENERATOR_ATHROW_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.ASYNC_GENERATOR_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.BOOLEAN_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.BYTES_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.BYTE_ARRAY_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.COMPLEX_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.CONTEXT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.COROUTINE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DEFAULTDICT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DEFAULT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DEQUE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DICTITEMSVIEW_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DICTKEYSVIEW_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DICTVALUESVIEW_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.DICT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.FLOAT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.FOREIGNOBJECT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.FROZENSET_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.GENERATOR_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.GENERIC_ALIAS_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.INT_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.LIST_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.MAPPINGPROXY_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.MEMORYVIEW_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.MMAP_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.NONE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCARRAYTYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCARRAY_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCFUNCPTRTYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCFUNCPTR_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCPOINTERTYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCPOINTER_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCSIMPLETYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.PYCSTRUCTTYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.RANGE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.SET_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.SIMPLECDATA_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.STRING_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.TUPLE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.TYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.UNIONTYPE_M_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.UNION_TYPE_M_FLAGS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEFAULTDICT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE_REV_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_ITEMS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_KEYS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_REVERSE_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_REVERSE_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_REVERSE_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_VALUES;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FOREIGN;
import static com.oracle.graal.python.nodes.BuiltinNames.J_LRU_CACHE_WRAPPER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MEMBER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ORDERED_DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PARTIAL;
import static com.oracle.graal.python.nodes.BuiltinNames.J_POSIX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PROPERTY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SIMPLE_QUEUE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TUPLE_GETTER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPES;
import static com.oracle.graal.python.nodes.BuiltinNames.J_WRAPPER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CONTEXTVARS;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CTYPES;
import static com.oracle.graal.python.nodes.BuiltinNames.J__SOCKET;
import static com.oracle.graal.python.nodes.BuiltinNames.J__SSL;
import static com.oracle.graal.python.nodes.BuiltinNames.J__STRUCT;
import static com.oracle.graal.python.nodes.BuiltinNames.J__THREAD;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.Arrays;
import java.util.HashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.runtime.PythonContext;
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
import com.oracle.truffle.api.strings.TruffleString;

// InteropLibrary is proxied through ReflectionLibrary
@ExportLibrary(ReflectionLibrary.class)
public enum PythonBuiltinClassType implements TruffleObject {

    ForeignObject(J_FOREIGN, Flags.PRIVATE_DERIVED_WODICT, FOREIGNOBJECT_M_FLAGS),
    Boolean("bool", J_BUILTINS, Flags.PUBLIC_DERIVED_WODICT, BOOLEAN_M_FLAGS),
    PArray("array", "array", ARRAY_M_FLAGS),
    PArrayIterator("arrayiterator", Flags.PRIVATE_DERIVED_WODICT),
    PIterator("iterator", Flags.PRIVATE_DERIVED_WODICT),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    PBuiltinFunction("method_descriptor", Flags.PRIVATE_DERIVED_WODICT),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinFunctionOrMethod("builtin_function_or_method", Flags.PRIVATE_DERIVED_WODICT),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    WrapperDescriptor(J_WRAPPER_DESCRIPTOR, Flags.PRIVATE_DERIVED_WODICT),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    MethodWrapper("method-wrapper", Flags.PRIVATE_DERIVED_WODICT),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinMethod("builtin_method", Flags.PRIVATE_DERIVED_WODICT),
    PBuiltinClassMethod("classmethod_descriptor", Flags.PRIVATE_DERIVED_WODICT),
    GetSetDescriptor("getset_descriptor", Flags.PRIVATE_DERIVED_WODICT),
    MemberDescriptor(J_MEMBER_DESCRIPTOR, Flags.PRIVATE_DERIVED_WODICT),
    PByteArray("bytearray", J_BUILTINS, BYTE_ARRAY_M_FLAGS),
    PBytes("bytes", J_BUILTINS, BYTES_M_FLAGS),
    PCell("cell", Flags.PRIVATE_DERIVED_WODICT),
    PSimpleNamespace("SimpleNamespace", null, "types", Flags.PUBLIC_BASE_WDICT),
    PKeyWrapper("KeyWrapper", "_functools", "functools", Flags.PUBLIC_DERIVED_WODICT),
    PPartial(J_PARTIAL, "_functools", "functools", Flags.PUBLIC_BASE_WDICT),
    PLruListElem("_lru_list_elem", null, "functools", Flags.PUBLIC_DERIVED_WODICT),
    PLruCacheWrapper(J_LRU_CACHE_WRAPPER, "_functools", "functools", Flags.PUBLIC_BASE_WDICT),
    PDefaultDict(J_DEFAULTDICT, "_collections", "collections", Flags.PUBLIC_BASE_WODICT, DEFAULTDICT_M_FLAGS),
    PDeque(J_DEQUE, "_collections", Flags.PUBLIC_BASE_WODICT, DEQUE_M_FLAGS),
    PTupleGetter(J_TUPLE_GETTER, "_collections", Flags.PUBLIC_BASE_WODICT),
    PDequeIter(J_DEQUE_ITER, "_collections", Flags.PUBLIC_DERIVED_WODICT),
    PDequeRevIter(J_DEQUE_REV_ITER, "_collections", Flags.PUBLIC_DERIVED_WODICT),
    POrderedDict(J_ORDERED_DICT, "_collections", Flags.PUBLIC_BASE_WDICT, DICT_M_FLAGS),
    POrderedDictKeys("odict_keys", Flags.PRIVATE_DERIVED_WODICT, DICTKEYSVIEW_M_FLAGS),
    POrderedDictValues("odict_values", Flags.PRIVATE_DERIVED_WODICT, DICTVALUESVIEW_M_FLAGS),
    POrderedDictItems("odict_items", Flags.PRIVATE_DERIVED_WODICT, DICTITEMSVIEW_M_FLAGS),
    POrderedDictIterator("odict_iterator", Flags.PRIVATE_DERIVED_WODICT),
    PComplex("complex", J_BUILTINS, COMPLEX_M_FLAGS),
    PDict("dict", J_BUILTINS, DICT_M_FLAGS),
    PDictItemIterator(J_DICT_ITEMITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictReverseItemIterator(J_DICT_REVERSE_ITEMITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictItemsView(J_DICT_ITEMS, Flags.PRIVATE_DERIVED_WODICT, DICTITEMSVIEW_M_FLAGS),
    PDictKeyIterator(J_DICT_KEYITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictReverseKeyIterator(J_DICT_REVERSE_KEYITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictKeysView(J_DICT_KEYS, Flags.PRIVATE_DERIVED_WODICT, DICTKEYSVIEW_M_FLAGS),
    PDictValueIterator(J_DICT_VALUEITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictReverseValueIterator(J_DICT_REVERSE_VALUEITERATOR, Flags.PRIVATE_DERIVED_WODICT),
    PDictValuesView(J_DICT_VALUES, Flags.PRIVATE_DERIVED_WODICT, DICTVALUESVIEW_M_FLAGS),
    PEllipsis("ellipsis", Flags.PRIVATE_DERIVED_WODICT),
    PEnumerate("enumerate", J_BUILTINS),
    PMap("map", J_BUILTINS),
    PFloat("float", J_BUILTINS, FLOAT_M_FLAGS),
    PFrame("frame", Flags.PRIVATE_DERIVED_WODICT),
    PFrozenSet("frozenset", J_BUILTINS, FROZENSET_M_FLAGS),
    PFunction("function", Flags.PRIVATE_DERIVED_WDICT),
    PGenerator("generator", Flags.PRIVATE_DERIVED_WODICT, GENERATOR_M_FLAGS),
    PCoroutine("coroutine", Flags.PRIVATE_DERIVED_WODICT, COROUTINE_M_FLAGS),
    PCoroutineWrapper("coroutine_wrapper", Flags.PRIVATE_DERIVED_WODICT),
    PAsyncGenerator("async_generator", Flags.PRIVATE_DERIVED_WODICT, ASYNC_GENERATOR_M_FLAGS),
    PInt("int", J_BUILTINS, INT_M_FLAGS),
    PList("list", J_BUILTINS, LIST_M_FLAGS),
    PMappingproxy("mappingproxy", Flags.PRIVATE_DERIVED_WODICT, MAPPINGPROXY_M_FLAGS),
    PMemoryView("memoryview", J_BUILTINS, Flags.PUBLIC_DERIVED_WODICT, MEMORYVIEW_M_FLAGS),
    PAsyncGenASend("async_generator_asend", Flags.PRIVATE_DERIVED_WODICT, ASYNC_GENERATOR_ASEND_M_FLAGS),
    PAsyncGenAThrow("async_generator_athrow", Flags.PRIVATE_DERIVED_WODICT, ASYNC_GENERATOR_ATHROW_M_FLAGS),
    PAsyncGenAWrappedValue("async_generator_wrapped_value", Flags.PRIVATE_DERIVED_WODICT),
    PMethod("method", Flags.PRIVATE_DERIVED_WODICT),
    PMMap("mmap", "mmap", MMAP_M_FLAGS),
    PNone("NoneType", Flags.PRIVATE_DERIVED_WODICT, NONE_M_FLAGS),
    PNotImplemented("NotImplementedType", Flags.PRIVATE_DERIVED_WODICT),
    PProperty(J_PROPERTY, J_BUILTINS, Flags.PUBLIC_BASE_WODICT),
    PSimpleQueue(J_SIMPLE_QUEUE, "_queue", Flags.PUBLIC_BASE_WODICT),
    PRandom("Random", "_random"),
    PRange("range", J_BUILTINS, Flags.PUBLIC_DERIVED_WODICT, RANGE_M_FLAGS),
    PReferenceType("ReferenceType", "_weakref"),
    PSentinelIterator("callable_iterator", Flags.PRIVATE_DERIVED_WODICT),
    PForeignArrayIterator("foreign_iterator"),
    PReverseIterator("reversed", J_BUILTINS),
    PSet("set", J_BUILTINS, SET_M_FLAGS),
    PSlice("slice", J_BUILTINS),
    PString("str", J_BUILTINS, STRING_M_FLAGS),
    PTraceback("traceback"),
    PTuple("tuple", J_BUILTINS, TUPLE_M_FLAGS),
    PythonClass("type", J_BUILTINS, Flags.PUBLIC_BASE_WDICT, TYPE_M_FLAGS),
    PythonModule("module", Flags.PRIVATE_BASE_WDICT),
    PythonModuleDef("moduledef", Flags.PRIVATE_DERIVED_WODICT),
    PythonObject("object", J_BUILTINS),
    Super("super", J_BUILTINS),
    PCode("code", Flags.PRIVATE_DERIVED_WODICT),
    PGenericAlias("GenericAlias", J_TYPES, Flags.PUBLIC_BASE_WODICT, GENERIC_ALIAS_M_FLAGS),
    PUnionType("UnionType", J_TYPES, Flags.PUBLIC_DERIVED_WODICT, UNION_TYPE_M_FLAGS),
    PZip("zip", J_BUILTINS),
    PThread("start_new_thread", J__THREAD),
    PThreadLocal("_local", J__THREAD),
    PLock("LockType", J__THREAD),
    PRLock("RLock", J__THREAD),
    PSemLock("SemLock", "_multiprocessing"),
    PGraalPySemLock("SemLock", "_multiprocessing_graalpy"),
    PSocket("socket", J__SOCKET),
    PStaticmethod("staticmethod", J_BUILTINS, Flags.PUBLIC_BASE_WDICT),
    PClassmethod("classmethod", J_BUILTINS, Flags.PUBLIC_BASE_WDICT),
    PInstancemethod("instancemethod", Flags.PUBLIC_BASE_WDICT),
    PScandirIterator("ScandirIterator", J_POSIX, Flags.PRIVATE_DERIVED_WODICT),
    PDirEntry("DirEntry", J_POSIX, Flags.PUBLIC_DERIVED_WODICT),
    LsprofProfiler("Profiler", "_lsprof"),
    PStruct("Struct", J__STRUCT),
    PStructUnpackIterator("unpack_iterator", J__STRUCT),
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

    PStatResult("stat_result", "os", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PStatvfsResult("statvfs_result", "os", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PTerminalSize("terminal_size", "os", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PUnameResult("uname_result", J_POSIX, Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PStructTime("struct_time", "time", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PProfilerEntry("profiler_entry", "_lsprof", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PProfilerSubentry("profiler_subentry", "_lsprof", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PStructPasswd("struct_passwd", "pwd", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PStructRusage("struct_rusage", "resource", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PVersionInfo("version_info", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PFlags("flags", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PFloatInfo("float_info", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PIntInfo("int_info", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PHashInfo("hash_info", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PThreadInfo("thread_info", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),
    PUnraisableHookArgs("UnraisableHookArgs", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS),

    PSSLSession("SSLSession", J__SSL),
    PSSLContext("_SSLContext", J__SSL),
    PSSLSocket("_SSLSocket", J__SSL),
    PMemoryBIO("MemoryBIO", J__SSL),

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
    PPairwise("pairwise", "itertools"),
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

    // codecs
    PEncodingMap("EncodingMap", Flags.PRIVATE_DERIVED_WODICT),

    // hashlib
    MD5Type("md5", "_md5", Flags.PUBLIC_BASE_WODICT),
    SHA1Type("sha1", "_sha1", Flags.PUBLIC_BASE_WODICT),
    SHA224Type("sha224", "_sha256", Flags.PUBLIC_BASE_WODICT),
    SHA256Type("sha256", "_sha256", Flags.PUBLIC_BASE_WODICT),
    SHA384Type("sha384", "_sha512", Flags.PUBLIC_BASE_WODICT),
    SHA512Type("sha512", "_sha512", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA224Type("sha3_224", "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA256Type("sha3_256", "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA384Type("sha3_384", "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA512Type("sha3_512", "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3Shake128Type("shake_128", "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3Shake256Type("shake_256", "_sha3", Flags.PUBLIC_BASE_WODICT),
    Blake2bType("blake2b", "_blake2", Flags.PUBLIC_BASE_WODICT),
    Blake2sType("blake2s", "_blake2", Flags.PUBLIC_BASE_WODICT),
    HashlibHash("HASH", "_hashlib", Flags.PUBLIC_BASE_WODICT),
    HashlibHashXof("HASHXOF", "_hashlib", Flags.PUBLIC_DERIVED_WODICT),
    HashlibHmac("HMAC", "_hashlib", Flags.PUBLIC_BASE_WODICT),
    UnsupportedDigestmodError("UnsupportedDigestmodError", "_hashlib", Flags.EXCEPTION),

    // _ast (rest of the classes are not builtin, they are generated in AstModuleBuiltins)
    AST("AST", "_ast", "ast", Flags.PUBLIC_BASE_WDICT),

    // _ctype
    CArgObject("CArgObject", Flags.PUBLIC_BASE_WODICT),
    CThunkObject("CThunkObject", J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    StgDict("StgDict", Flags.PRIVATE_DERIVED_WODICT, DICT_M_FLAGS),
    PyCStructType("PyCStructType", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCSTRUCTTYPE_M_FLAGS),
    UnionType("UnionType", J__CTYPES, Flags.PUBLIC_BASE_WODICT, UNIONTYPE_M_FLAGS),
    PyCPointerType("PyCPointerType", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCPOINTERTYPE_M_FLAGS),
    PyCArrayType("PyCArrayType", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCARRAYTYPE_M_FLAGS),
    PyCSimpleType("PyCSimpleType", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCSIMPLETYPE_M_FLAGS),
    PyCFuncPtrType("PyCFuncPtrType", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCFUNCPTRTYPE_M_FLAGS),
    Structure("Structure", J__CTYPES, Flags.PUBLIC_BASE_WODICT), /*- type = PyCStructType */
    Union("Union", J__CTYPES, Flags.PUBLIC_BASE_WODICT), /*- type = UnionType */
    PyCPointer("_Pointer", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCPOINTER_M_FLAGS), /*- type = PyCPointerType */
    PyCArray("Array", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCARRAY_M_FLAGS), /*- type = PyCArrayType */
    PyCData("_CData", J__CTYPES, Flags.PUBLIC_BASE_WODICT), /*- type = PyCStructType */
    SimpleCData("_SimpleCData", J__CTYPES, Flags.PUBLIC_BASE_WODICT, SIMPLECDATA_M_FLAGS), /*- type = PyCStructType */
    PyCFuncPtr("PyCFuncPtr", J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCFUNCPTR_M_FLAGS), /*- type = PyCFuncPtrType */
    CField("CField", J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    DictRemover("DictRemover", J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    StructParam("StructParam_Type", J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    ArgError("ArgumentError", J__CTYPES, Flags.EXCEPTION),

    // _multibytecodec
    MultibyteCodec("MultibyteCodec", "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteIncrementalEncoder("MultibyteIncrementalEncoder", "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteIncrementalDecoder("MultibyteIncrementalDecoder", "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteStreamReader("MultibyteStreamReader", "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteStreamWriter("MultibyteStreamWriter", "_multibytecodec", Flags.PUBLIC_BASE_WDICT),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", J_BUILTINS, Flags.EXCEPTION),
    PBaseExceptionGroup("BaseExceptionGroup", J_BUILTINS, Flags.EXCEPTION),
    SystemExit("SystemExit", J_BUILTINS, Flags.EXCEPTION),
    KeyboardInterrupt("KeyboardInterrupt", J_BUILTINS, Flags.EXCEPTION),
    GeneratorExit("GeneratorExit", J_BUILTINS, Flags.EXCEPTION),
    Exception("Exception", J_BUILTINS, Flags.EXCEPTION),
    StopIteration("StopIteration", J_BUILTINS, Flags.EXCEPTION),
    StopAsyncIteration("StopAsyncIteration", J_BUILTINS, Flags.EXCEPTION),
    ArithmeticError("ArithmeticError", J_BUILTINS, Flags.EXCEPTION),
    FloatingPointError("FloatingPointError", J_BUILTINS, Flags.EXCEPTION),
    OverflowError("OverflowError", J_BUILTINS, Flags.EXCEPTION),
    ZeroDivisionError("ZeroDivisionError", J_BUILTINS, Flags.EXCEPTION),
    AssertionError("AssertionError", J_BUILTINS, Flags.EXCEPTION),
    AttributeError("AttributeError", J_BUILTINS, Flags.EXCEPTION),
    BufferError("BufferError", J_BUILTINS, Flags.EXCEPTION),
    EOFError("EOFError", J_BUILTINS, Flags.EXCEPTION),
    ImportError("ImportError", J_BUILTINS, Flags.EXCEPTION),
    ModuleNotFoundError("ModuleNotFoundError", J_BUILTINS, Flags.EXCEPTION),
    LookupError("LookupError", J_BUILTINS, Flags.EXCEPTION),
    IndexError("IndexError", J_BUILTINS, Flags.EXCEPTION),
    KeyError("KeyError", J_BUILTINS, Flags.EXCEPTION),
    MemoryError("MemoryError", J_BUILTINS, Flags.EXCEPTION),
    NameError("NameError", J_BUILTINS, Flags.EXCEPTION),
    UnboundLocalError("UnboundLocalError", J_BUILTINS, Flags.EXCEPTION),
    OSError("OSError", J_BUILTINS, Flags.EXCEPTION),
    BlockingIOError("BlockingIOError", J_BUILTINS, Flags.EXCEPTION),
    ChildProcessError("ChildProcessError", J_BUILTINS, Flags.EXCEPTION),
    ConnectionError("ConnectionError", J_BUILTINS, Flags.EXCEPTION),
    BrokenPipeError("BrokenPipeError", J_BUILTINS, Flags.EXCEPTION),
    ConnectionAbortedError("ConnectionAbortedError", J_BUILTINS, Flags.EXCEPTION),
    ConnectionRefusedError("ConnectionRefusedError", J_BUILTINS, Flags.EXCEPTION),
    ConnectionResetError("ConnectionResetError", J_BUILTINS, Flags.EXCEPTION),
    FileExistsError("FileExistsError", J_BUILTINS, Flags.EXCEPTION),
    FileNotFoundError("FileNotFoundError", J_BUILTINS, Flags.EXCEPTION),
    InterruptedError("InterruptedError", J_BUILTINS, Flags.EXCEPTION),
    IsADirectoryError("IsADirectoryError", J_BUILTINS, Flags.EXCEPTION),
    NotADirectoryError("NotADirectoryError", J_BUILTINS, Flags.EXCEPTION),
    PermissionError("PermissionError", J_BUILTINS, Flags.EXCEPTION),
    ProcessLookupError("ProcessLookupError", J_BUILTINS, Flags.EXCEPTION),
    TimeoutError("TimeoutError", J_BUILTINS, Flags.EXCEPTION),
    ZLibError("error", "zlib", Flags.EXCEPTION),
    CSVError("Error", "_csv", Flags.EXCEPTION),
    LZMAError("LZMAError", "_lzma", Flags.EXCEPTION),
    StructError("StructError", J__STRUCT, Flags.EXCEPTION),
    PickleError("PickleError", "_pickle", Flags.EXCEPTION),
    PicklingError("PicklingError", "_pickle", Flags.EXCEPTION),
    UnpicklingError("UnpicklingError", "_pickle", Flags.EXCEPTION),
    SocketGAIError("gaierror", J__SOCKET, Flags.EXCEPTION),
    SocketHError("herror", J__SOCKET, Flags.EXCEPTION),
    BinasciiError("Error", "binascii", Flags.EXCEPTION),
    BinasciiIncomplete("Incomplete", "binascii", Flags.EXCEPTION),
    SSLError("SSLError", J__SSL, Flags.EXCEPTION),
    SSLZeroReturnError("SSLZeroReturnError", J__SSL, Flags.EXCEPTION),
    SSLWantReadError("SSLWantReadError", J__SSL, Flags.EXCEPTION),
    SSLWantWriteError("SSLWantWriteError", J__SSL, Flags.EXCEPTION),
    SSLSyscallError("SSLSyscallError", J__SSL, Flags.EXCEPTION),
    SSLEOFError("SSLEOFError", J__SSL, Flags.EXCEPTION),
    SSLCertVerificationError("SSLCertVerificationError", J__SSL, Flags.EXCEPTION),
    PForeignException("ForeignException", Flags.FOREIGN_EXCEPTION),

    // todo: all OS errors

    ReferenceError("ReferenceError", J_BUILTINS, Flags.EXCEPTION),
    RuntimeError("RuntimeError", J_BUILTINS, Flags.EXCEPTION),
    NotImplementedError("NotImplementedError", J_BUILTINS, Flags.EXCEPTION),
    SyntaxError("SyntaxError", J_BUILTINS, Flags.EXCEPTION),
    IndentationError("IndentationError", J_BUILTINS, Flags.EXCEPTION),
    TabError("TabError", J_BUILTINS, Flags.EXCEPTION),
    SystemError("SystemError", J_BUILTINS, Flags.EXCEPTION),
    TypeError("TypeError", J_BUILTINS, Flags.EXCEPTION),
    ValueError("ValueError", J_BUILTINS, Flags.EXCEPTION),
    UnicodeError("UnicodeError", J_BUILTINS, Flags.EXCEPTION),
    UnicodeDecodeError("UnicodeDecodeError", J_BUILTINS, Flags.EXCEPTION),
    UnicodeEncodeError("UnicodeEncodeError", J_BUILTINS, Flags.EXCEPTION),
    UnicodeTranslateError("UnicodeTranslateError", J_BUILTINS, Flags.EXCEPTION),
    RecursionError("RecursionError", J_BUILTINS, Flags.EXCEPTION),

    IOUnsupportedOperation("UnsupportedOperation", "io", Flags.EXCEPTION),

    Empty("Empty", "_queue", Flags.EXCEPTION),

    // warnings
    Warning("Warning", J_BUILTINS, Flags.EXCEPTION),
    BytesWarning("BytesWarning", J_BUILTINS, Flags.EXCEPTION),
    DeprecationWarning("DeprecationWarning", J_BUILTINS, Flags.EXCEPTION),
    FutureWarning("FutureWarning", J_BUILTINS, Flags.EXCEPTION),
    ImportWarning("ImportWarning", J_BUILTINS, Flags.EXCEPTION),
    PendingDeprecationWarning("PendingDeprecationWarning", J_BUILTINS, Flags.EXCEPTION),
    ResourceWarning("ResourceWarning", J_BUILTINS, Flags.EXCEPTION),
    RuntimeWarning("RuntimeWarning", J_BUILTINS, Flags.EXCEPTION),
    SyntaxWarning("SyntaxWarning", J_BUILTINS, Flags.EXCEPTION),
    UnicodeWarning("UnicodeWarning", J_BUILTINS, Flags.EXCEPTION),
    UserWarning("UserWarning", J_BUILTINS, Flags.EXCEPTION),
    EncodingWarning("EncodingWarning", J_BUILTINS, Flags.EXCEPTION),

    // contextvars
    ContextVarsToken("Token", J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT),
    ContextVarsContext("Context", J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT, CONTEXT_M_FLAGS),
    ContextVar("ContextVar", J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT),
    // CPython uses separate keys, values, items python types for the iterators.
    ContextIterator("context_iterator", J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT),

    Capsule("capsule"),

    // A marker for @Builtin that is not a class. Must always come last.
    nil("nil");

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

    private final TruffleString name;
    private final TruffleString publishInModule;
    private final TruffleString moduleName;
    // This is the name qualified by module used for printing. But the actual __qualname__ is just
    // plain name without module
    private final TruffleString printName;
    private final boolean basetype;
    private final boolean isBuiltinWithDict;

    // initialized in static constructor
    @CompilationFinal private PythonBuiltinClassType type;
    @CompilationFinal private PythonBuiltinClassType base;
    @CompilationFinal private int weaklistoffset;

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
    private final long methodsFlags;

    PythonBuiltinClassType(String name, String module, Flags flags) {
        this(name, module, module, flags);
    }

    PythonBuiltinClassType(String name, String module, Flags flags, long methodsFlags) {
        this(name, module, module, flags, methodsFlags);
    }

    PythonBuiltinClassType(String name, String publishInModule, String moduleName, Flags flags) {
        this(name, publishInModule, moduleName, flags, DEFAULT_M_FLAGS);
    }

    PythonBuiltinClassType(String name, String publishInModule, String moduleName, Flags flags, long methodsFlags) {
        this.name = toTruffleStringUncached(name);
        this.publishInModule = toTruffleStringUncached(publishInModule);
        this.moduleName = flags.isPublic && moduleName != null ? toTruffleStringUncached(moduleName) : null;
        if (moduleName != null && moduleName != J_BUILTINS) {
            printName = toTruffleStringUncached(moduleName + "." + name);
        } else {
            printName = this.name;
        }
        this.basetype = flags.isBaseType;
        this.isBuiltinWithDict = flags.isBuiltinWithDict;
        this.methodsFlags = methodsFlags;
        this.weaklistoffset = -1;
    }

    PythonBuiltinClassType(String name, String module) {
        this(name, module, Flags.PUBLIC_BASE_WODICT);
    }

    PythonBuiltinClassType(String name, String module, long methodsFlags) {
        this(name, module, Flags.PUBLIC_BASE_WODICT, methodsFlags);
    }

    PythonBuiltinClassType(String name, Flags flags) {
        this(name, null, flags);
    }

    PythonBuiltinClassType(String name, Flags flags, long methodsFlags) {
        this(name, null, flags, methodsFlags);
    }

    PythonBuiltinClassType(String name) {
        this(name, null, Flags.PRIVATE_BASE_WODICT);
    }

    public boolean isAcceptableBase() {
        return basetype;
    }

    public TruffleString getName() {
        return name;
    }

    public TruffleString getPrintName() {
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

    public TruffleString getPublishInModule() {
        return publishInModule;
    }

    public TruffleString getModuleName() {
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

    public long getMethodsFlags() {
        return methodsFlags;
    }

    public int getWeaklistoffset() {
        return weaklistoffset;
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
        return name.toJavaStringUncached();
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
        PStructUnpackIterator.redefinedSlots = new SpecialMethodSlot[]{SpecialMethodSlot.Next, SpecialMethodSlot.Iter, SpecialMethodSlot.LengthHint};

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
        PStatvfsResult.redefinedSlots = repr;
        PFloatInfo.redefinedSlots = reprAndNew;
        PVersionInfo.redefinedSlots = repr;
        PFlags.redefinedSlots = repr;
        PTerminalSize.redefinedSlots = reprAndNew;

        PythonObject.type = PythonClass;
        PythonObject.base = null;

        PBuiltinMethod.base = PBuiltinFunctionOrMethod;

        Boolean.base = PInt;

        PBaseExceptionGroup.base = PBaseException;
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
        ZLibError.base = Exception;
        CSVError.base = Exception;
        LZMAError.base = Exception;
        SocketGAIError.base = OSError;
        SocketHError.base = OSError;

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
        EncodingWarning.base = Warning;

        PStatResult.base = PTuple;
        PStatvfsResult.base = PTuple;
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
        POrderedDict.base = PDict;
        POrderedDictKeys.base = PDictKeysView;
        POrderedDictValues.base = PDictValuesView;
        POrderedDictItems.base = PDictItemsView;

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

        // hashlib
        UnsupportedDigestmodError.base = ValueError;
        HashlibHashXof.base = HashlibHash;

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
        ArgError.base = PBaseException;

        Empty.base = Exception;

        boolean assertionsEnabled = false;
        assert (assertionsEnabled = true) == true;
        HashSet<String> set = assertionsEnabled ? new HashSet<>() : null;
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

            type.weaklistoffset = WeakRefModuleBuiltins.getBuiltinTypeWeaklistoffset(type);
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
}
