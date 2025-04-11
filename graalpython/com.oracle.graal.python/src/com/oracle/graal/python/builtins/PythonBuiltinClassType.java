/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.BuiltinNames.J_LRU_CACHE_WRAPPER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MEMBER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ORDERED_DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PARTIAL;
import static com.oracle.graal.python.nodes.BuiltinNames.J_POLYGLOT;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.modules.StatResultBuiltins;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ast.AstBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2CompressorBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2DecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalDecoderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalEncoderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamReaderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamWriterBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVDialectBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVReaderBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeSequenceBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCSimpleTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCStructTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.SimpleCDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructUnionTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructureBuiltins;
import com.oracle.graal.python.builtins.modules.functools.KeyWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.LruCacheWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.PartialBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Blake2bObjectBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.HashObjectBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.Sha3Builtins;
import com.oracle.graal.python.builtins.modules.io.BufferedIOMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedRWPairBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedRandomBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedWriterBuiltins;
import com.oracle.graal.python.builtins.modules.io.BytesIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.FileIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.IncrementalNewlineDecoderBuiltins;
import com.oracle.graal.python.builtins.modules.io.StringIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONEncoderBuiltins;
import com.oracle.graal.python.builtins.modules.json.JSONScannerBuiltins;
import com.oracle.graal.python.builtins.modules.lsprof.ProfilerBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMACompressorBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMADecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.GraalPySemLockBuiltins;
import com.oracle.graal.python.builtins.modules.multiprocessing.SemLockBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.PickleBufferBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.PicklerBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.PicklerMemoProxyBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.UnpicklerBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.UnpicklerMemoProxyBuiltins;
import com.oracle.graal.python.builtins.objects.NoneBuiltins;
import com.oracle.graal.python.builtins.objects.NotImplementedBuiltins;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGenSendBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGenThrowBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.CoroutineWrapperBuiltins;
import com.oracle.graal.python.builtins.objects.bool.BoolBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.code.CodeBuiltins;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.ContextBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.ContextIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.ContextVarBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.TokenBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeIterBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeIterCommonBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeRevIterBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DefaultDictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictReprBuiltin;
import com.oracle.graal.python.builtins.objects.dict.DictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictViewBuiltins;
import com.oracle.graal.python.builtins.objects.ellipsis.EllipsisBuiltins;
import com.oracle.graal.python.builtins.objects.enumerate.EnumerateBuiltins;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionBuiltins;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionGroupBuiltins;
import com.oracle.graal.python.builtins.objects.exception.ImportErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.KeyErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.StopIterationBuiltins;
import com.oracle.graal.python.builtins.objects.exception.SyntaxErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.SystemExitBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeDecodeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeEncodeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.UnicodeTranslateErrorBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignBooleanBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignExecutableBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignInstantiableBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignIterableBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignNumberBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignObjectBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
import com.oracle.graal.python.builtins.objects.function.AbstractFunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.FunctionBuiltins;
import com.oracle.graal.python.builtins.objects.function.MethodDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.function.WrapperDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.generator.CoroutineBuiltins;
import com.oracle.graal.python.builtins.objects.generator.GeneratorBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptorTypeBuiltins;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.MemberDescriptorBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.IteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.SentinelIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.ZipBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.AccumulateBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ChainBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CombinationsBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CombinationsWithReplacementBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CompressBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CountBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CycleBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.DropwhileBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.FilterfalseBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.GroupByBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.GrouperBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.IsliceBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.PairwiseBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.PermutationsBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ProductBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.RepeatBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.StarmapBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.TakewhileBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.TeeBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.TeeDataObjectBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ZipLongestBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.map.MapBuiltins;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins;
import com.oracle.graal.python.builtins.objects.method.AbstractMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.BuiltinFunctionOrMethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.ClassmethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.ClassmethodCommonBuiltins;
import com.oracle.graal.python.builtins.objects.method.InstancemethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodBuiltins;
import com.oracle.graal.python.builtins.objects.method.MethodWrapperBuiltins;
import com.oracle.graal.python.builtins.objects.method.StaticmethodBuiltins;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltins;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.namespace.SimpleNamespaceBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictItemsBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictKeysBuiltins;
import com.oracle.graal.python.builtins.objects.ordereddict.OrderedDictValuesBuiltins;
import com.oracle.graal.python.builtins.objects.posix.DirEntryBuiltins;
import com.oracle.graal.python.builtins.objects.posix.ScandirIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.property.PropertyBuiltins;
import com.oracle.graal.python.builtins.objects.queue.SimpleQueueBuiltins;
import com.oracle.graal.python.builtins.objects.random.RandomBuiltins;
import com.oracle.graal.python.builtins.objects.range.RangeBuiltins;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.reversed.ReversedBuiltins;
import com.oracle.graal.python.builtins.objects.set.BaseSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltins;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.MemoryBIOBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLContextBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.struct.StructBuiltins;
import com.oracle.graal.python.builtins.objects.struct.StructUnpackIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltins;
import com.oracle.graal.python.builtins.objects.thread.CommonLockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.LockTypeBuiltins;
import com.oracle.graal.python.builtins.objects.thread.RLockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.tokenize.TokenizerIterBuiltins;
import com.oracle.graal.python.builtins.objects.traceback.TracebackBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.InstantiableStructSequenceBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.StructSequenceBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleGetterBuiltins;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.types.GenericAliasBuiltins;
import com.oracle.graal.python.builtins.objects.types.GenericAliasIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.types.UnionTypeBuiltins;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleOptions;
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

    PythonObject("object", null, newBuilder().publishInModule(J_BUILTINS).basetype().slots(ObjectBuiltins.SLOTS).doc("""
                    The base class of the class hierarchy.

                    When called, it accepts no arguments and returns a new featureless
                    instance that has no instance attributes and cannot be given any.
                    """)),
    PythonClass("type", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(TypeBuiltins.SLOTS).doc("""
                    type(object) -> the object's type
                    type(name, bases, dict, **kwds) -> a new type""")),
    PArray("array", PythonObject, newBuilder().publishInModule("array").basetype().slots(ArrayBuiltins.SLOTS)),
    PArrayIterator("arrayiterator", PythonObject, newBuilder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PIterator("iterator", PythonObject, newBuilder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    PBuiltinFunction("method_descriptor", PythonObject, newBuilder().disallowInstantiation().slots(AbstractFunctionBuiltins.SLOTS, MethodDescriptorBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinFunctionOrMethod(
                    "builtin_function_or_method",
                    PythonObject,
                    newBuilder().disallowInstantiation().slots(AbstractMethodBuiltins.SLOTS, BuiltinFunctionOrMethodBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    WrapperDescriptor(J_WRAPPER_DESCRIPTOR, PythonObject, newBuilder().disallowInstantiation().slots(AbstractFunctionBuiltins.SLOTS, WrapperDescriptorBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    MethodWrapper("method-wrapper", PythonObject, newBuilder().slots(AbstractMethodBuiltins.SLOTS, MethodWrapperBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinMethod("builtin_method", PBuiltinFunctionOrMethod, newBuilder()),
    PBuiltinClassMethod("classmethod_descriptor", PythonObject, newBuilder().slots(ClassmethodCommonBuiltins.SLOTS, BuiltinClassmethodBuiltins.SLOTS)),
    GetSetDescriptor("getset_descriptor", PythonObject, newBuilder().disallowInstantiation().slots(GetSetDescriptorTypeBuiltins.SLOTS)),
    MemberDescriptor(J_MEMBER_DESCRIPTOR, PythonObject, newBuilder().disallowInstantiation().slots(MemberDescriptorBuiltins.SLOTS)),
    PByteArray(
                    "bytearray",
                    PythonObject,
                    newBuilder().publishInModule(J_BUILTINS).basetype().slots(BytesCommonBuiltins.SLOTS, ByteArrayBuiltins.SLOTS).doc("""
                                    bytearray(iterable_of_ints) -> bytearray
                                    bytearray(string, encoding[, errors]) -> bytearray
                                    bytearray(bytes_or_buffer) -> mutable copy of bytes_or_buffer
                                    bytearray(int) -> bytes array of size given by the parameter initialized with null bytes
                                    bytearray() -> empty bytes array

                                    Construct a mutable bytearray object from:
                                      - an iterable yielding integers in range(256)
                                      - a text string encoded using the specified encoding
                                      - a bytes or a buffer object
                                      - any object implementing the buffer API.
                                      - an integer""")),
    PBytes("bytes", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(BytesCommonBuiltins.SLOTS, BytesBuiltins.SLOTS).doc("""
                    bytes(iterable_of_ints) -> bytes
                    bytes(string, encoding[, errors]) -> bytes
                    bytes(bytes_or_buffer) -> immutable copy of bytes_or_buffer
                    bytes(int) -> bytes object of size given by the parameter initialized with null bytes
                    bytes() -> empty bytes object

                    Construct an immutable array of bytes from:
                      - an iterable yielding integers in range(256)
                      - a text string encoded using the specified encoding
                      - any object implementing the buffer API.
                      - an integer""")),
    PCell("cell", PythonObject, newBuilder().slots(CellBuiltins.SLOTS)),
    PSimpleNamespace("SimpleNamespace", PythonObject, newBuilder().publishInModule("types").basetype().addDict().slots(SimpleNamespaceBuiltins.SLOTS).doc("""
                    A simple attribute-based namespace.

                    SimpleNamespace(**kwargs)""")),
    PKeyWrapper("KeyWrapper", PythonObject, newBuilder().moduleName("functools").publishInModule("_functools").disallowInstantiation().slots(KeyWrapperBuiltins.SLOTS)),
    PPartial(J_PARTIAL, PythonObject, newBuilder().moduleName("functools").publishInModule("_functools").basetype().addDict().slots(PartialBuiltins.SLOTS).doc("""
                    partial(func, *args, **keywords) - new function with partial application
                    of the given arguments and keywords.
                    """)),
    PLruListElem("_lru_list_elem", PythonObject, newBuilder().publishInModule("functools").disallowInstantiation()),
    PLruCacheWrapper(J_LRU_CACHE_WRAPPER, PythonObject, newBuilder().moduleName("functools").publishInModule("_functools").basetype().addDict().slots(LruCacheWrapperBuiltins.SLOTS).doc("""
                    Create a cached callable that wraps another function.

                    user_function:      the function being cached

                    maxsize:  0         for no caching
                              None      for unlimited cache size
                              n         for a bounded cache

                    typed:    False     cache f(3) and f(3.0) as identical calls
                              True      cache f(3) and f(3.0) as distinct calls

                    cache_info_type:    namedtuple class with the fields:
                                           hits misses currsize maxsize
                    """)),
    PDeque(J_DEQUE, PythonObject, newBuilder().publishInModule("_collections").basetype().slots(DequeBuiltins.SLOTS)),
    PTupleGetter(J_TUPLE_GETTER, PythonObject, newBuilder().publishInModule("_collections").basetype().slots(TupleGetterBuiltins.SLOTS)),
    PDequeIter(J_DEQUE_ITER, PythonObject, newBuilder().publishInModule("_collections").slots(DequeIterCommonBuiltins.SLOTS, DequeIterBuiltins.SLOTS)),
    PDequeRevIter(J_DEQUE_REV_ITER, PythonObject, newBuilder().publishInModule("_collections").slots(DequeIterCommonBuiltins.SLOTS, DequeRevIterBuiltins.SLOTS)),
    PComplex("complex", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(ComplexBuiltins.SLOTS).doc("""
                    Create a complex number from a real part and an optional imaginary part.

                    This is equivalent to (real + imag*1j) where imag defaults to 0.""")),
    PDict("dict", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(DictBuiltins.SLOTS, DictReprBuiltin.SLOTS).doc("""
                    dict() -> new empty dictionary
                    dict(mapping) -> new dictionary initialized from a mapping object's
                        (key, value) pairs
                    dict(iterable) -> new dictionary initialized as if via:
                        d = {}
                        for k, v in iterable:
                            d[k] = v
                    dict(**kwargs) -> new dictionary initialized with the name=value pairs
                        in the keyword argument list.  For example:  dict(one=1, two=2)""")),
    PDefaultDict(J_DEFAULTDICT, PDict, newBuilder().moduleName("collections").publishInModule("_collections").basetype().slots(DefaultDictBuiltins.SLOTS)),
    POrderedDict(J_ORDERED_DICT, PDict, newBuilder().publishInModule("_collections").basetype().addDict().slots(OrderedDictBuiltins.SLOTS)),
    PDictItemIterator(J_DICT_ITEMITERATOR, PythonObject, newBuilder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PDictReverseItemIterator(J_DICT_REVERSE_ITEMITERATOR, PythonObject, newBuilder().slots(IteratorBuiltins.SLOTS)),
    PDictItemsView(J_DICT_ITEMS, PythonObject, newBuilder().disallowInstantiation().slots(DictViewBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    PDictKeyIterator(J_DICT_KEYITERATOR, PythonObject, newBuilder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PDictReverseKeyIterator(J_DICT_REVERSE_KEYITERATOR, PythonObject, newBuilder().slots(IteratorBuiltins.SLOTS)),
    PDictKeysView(J_DICT_KEYS, PythonObject, newBuilder().disallowInstantiation().slots(DictViewBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    PDictValueIterator(J_DICT_VALUEITERATOR, PythonObject, newBuilder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PDictReverseValueIterator(J_DICT_REVERSE_VALUEITERATOR, PythonObject, newBuilder().slots(IteratorBuiltins.SLOTS)),
    PDictValuesView(J_DICT_VALUES, PythonObject, newBuilder().disallowInstantiation().slots(DictValuesBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    POrderedDictKeys("odict_keys", PDictKeysView, newBuilder().slots(OrderedDictKeysBuiltins.SLOTS)),
    POrderedDictValues("odict_values", PDictValuesView, newBuilder().slots(OrderedDictValuesBuiltins.SLOTS)),
    POrderedDictItems("odict_items", PDictItemsView, newBuilder().slots(OrderedDictItemsBuiltins.SLOTS)),
    POrderedDictIterator("odict_iterator", PythonObject, newBuilder().slots(OrderedDictIteratorBuiltins.SLOTS)),
    PEllipsis("ellipsis", PythonObject, newBuilder().slots(EllipsisBuiltins.SLOTS)),
    PEnumerate("enumerate", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(EnumerateBuiltins.SLOTS).doc("""
                    Return an enumerate object.

                      iterable
                        an object supporting iteration

                    The enumerate object yields pairs containing a count (from start, which
                    defaults to zero) and a value yielded by the iterable argument.

                    enumerate is useful for obtaining an indexed list:
                        (0, seq[0]), (1, seq[1]), (2, seq[2]), ...""")),
    PMap("map", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(MapBuiltins.SLOTS).doc("""
                    map(func, *iterables) --> map object

                    Make an iterator that computes the function using arguments from
                    each of the iterables.  Stops when the shortest iterable is exhausted.""")),
    PFloat(
                    "float",
                    PythonObject,
                    newBuilder().publishInModule(J_BUILTINS).basetype().slots(FloatBuiltins.SLOTS).doc("""
                                    Convert a string or number to a floating point number, if possible.""")),
    PFrame("frame", PythonObject, newBuilder().disallowInstantiation().slots(FrameBuiltins.SLOTS)),
    PFrozenSet(
                    "frozenset",
                    PythonObject,
                    newBuilder().publishInModule(J_BUILTINS).basetype().slots(BaseSetBuiltins.SLOTS, FrozenSetBuiltins.SLOTS).doc("""
                                    frozenset() -> empty frozenset object
                                    frozenset(iterable) -> frozenset object

                                    Build an immutable unordered collection of unique elements.""")),
    PFunction("function", PythonObject, newBuilder().addDict().slots(AbstractFunctionBuiltins.SLOTS, FunctionBuiltins.SLOTS)),
    PGenerator("generator", PythonObject, newBuilder().disallowInstantiation().slots(GeneratorBuiltins.SLOTS)),
    PCoroutine("coroutine", PythonObject, newBuilder().slots(CoroutineBuiltins.SLOTS)),
    PCoroutineWrapper("coroutine_wrapper", PythonObject, newBuilder().slots(CoroutineWrapperBuiltins.SLOTS)),
    PAsyncGenerator("async_generator", PythonObject, newBuilder().slots(AsyncGeneratorBuiltins.SLOTS)),
    PInt("int", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(IntBuiltins.SLOTS).doc("""
                    int([x]) -> integer
                    int(x, base=10) -> integer

                    Convert a number or string to an integer, or return 0 if no arguments
                    are given.  If x is a number, return x.__int__().  For floating point
                    numbers, this truncates towards zero.

                    If x is not a number or if base is given, then x must be a string,
                    bytes, or bytearray instance representing an integer literal in the
                    given base.  The literal can be preceded by '+' or '-' and be surrounded
                    by whitespace.  The base defaults to 10.  Valid bases are 0 and 2-36.
                    Base 0 means to interpret the base from the string as an integer literal.""")),
    Boolean("bool", PInt, newBuilder().publishInModule(J_BUILTINS).slots(BoolBuiltins.SLOTS).doc("""
                    bool(x) -> bool

                    Returns True when the argument x is true, False otherwise.
                    The builtins True and False are the only two instances of the class bool.
                    The class bool is a subclass of the class int, and cannot be subclassed.""")),
    PList("list", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(ListBuiltins.SLOTS).doc("""
                    Built-in mutable sequence.

                    If no argument is given, the constructor creates a new empty list.
                    The argument must be an iterable if specified.""")),
    PMappingproxy("mappingproxy", PythonObject, newBuilder().slots(MappingproxyBuiltins.SLOTS)),
    PMemoryView(
                    "memoryview",
                    PythonObject,
                    newBuilder().publishInModule(J_BUILTINS).slots(MemoryViewBuiltins.SLOTS).doc("""
                                    Create a new memoryview object which references the given object.""")),
    PAsyncGenASend("async_generator_asend", PythonObject, newBuilder().slots(AsyncGenSendBuiltins.SLOTS)),
    PAsyncGenAThrow("async_generator_athrow", PythonObject, newBuilder().slots(AsyncGenThrowBuiltins.SLOTS)),
    PAsyncGenAWrappedValue("async_generator_wrapped_value", PythonObject, newBuilder()),
    PMethod("method", PythonObject, newBuilder().slots(AbstractMethodBuiltins.SLOTS, MethodBuiltins.SLOTS).doc("""
                    Create a bound instance method object.""")),
    PMMap("mmap", PythonObject, newBuilder().publishInModule("mmap").basetype().slots(MMapBuiltins.SLOTS)),
    PNone("NoneType", PythonObject, newBuilder().slots(NoneBuiltins.SLOTS)),
    PNotImplemented("NotImplementedType", PythonObject, newBuilder().slots(NotImplementedBuiltins.SLOTS)),
    PProperty(J_PROPERTY, PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(PropertyBuiltins.SLOTS).doc("""
                    Property attribute.

                      fget
                        function to be used for getting an attribute value
                      fset
                        function to be used for setting an attribute value
                      fdel
                        function to be used for del'ing an attribute
                      doc
                        docstring

                    Typical use is to define a managed attribute x:

                    class C(object):
                        def getx(self): return self._x
                        def setx(self, value): self._x = value
                        def delx(self): del self._x
                        x = property(getx, setx, delx, "I'm the 'x' property.")

                    Decorators make defining new properties or modifying existing ones easy:

                    class C(object):
                        @property
                        def x(self):
                            "I am the 'x' property."
                            return self._x
                        @x.setter
                        def x(self, value):
                            self._x = value
                        @x.deleter
                        def x(self):
                            del self._x""")),
    PSimpleQueue(
                    J_SIMPLE_QUEUE,
                    PythonObject,
                    newBuilder().publishInModule("_queue").basetype().slots(SimpleQueueBuiltins.SLOTS).doc("""
                                    SimpleQueue()
                                    --

                                    Simple, unbounded, reentrant FIFO queue.""")),
    PRandom("Random", PythonObject, newBuilder().publishInModule("_random").basetype().slots(RandomBuiltins.SLOTS)),
    PRange("range", PythonObject, newBuilder().publishInModule(J_BUILTINS).slots(RangeBuiltins.SLOTS).doc("""
                    range(stop) -> range object
                    range(start, stop[, step]) -> range object

                    Return an object that produces a sequence of integers from start (inclusive)
                    to stop (exclusive) by step.  range(i, j) produces i, i+1, i+2, ..., j-1.
                    start defaults to 0, and stop is omitted!  range(4) produces 0, 1, 2, 3.
                    These are exactly the valid indices for a list of 4 elements.
                    When step is given, it specifies the increment (or decrement).""")),
    PReferenceType("ReferenceType", PythonObject, newBuilder().publishInModule("_weakref").basetype().slots(ReferenceTypeBuiltins.SLOTS)),
    PSentinelIterator("callable_iterator", PythonObject, newBuilder().disallowInstantiation().slots(SentinelIteratorBuiltins.SLOTS)),
    PReverseIterator("reversed", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(ReversedBuiltins.SLOTS).doc("""
                    Return a reverse iterator over the values of the given sequence.""")),
    PSet("set", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(BaseSetBuiltins.SLOTS, SetBuiltins.SLOTS).doc("""
                    set() -> new empty set object
                    set(iterable) -> new set object

                    Build an unordered collection of unique elements.""")),
    PSlice("slice", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(SliceBuiltins.SLOTS).doc("""
                    slice(stop)
                    slice(start, stop[, step])

                    Create a slice object.  This is used for extended slicing (e.g. a[0:10:2]).""")),
    PString("str", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(StringBuiltins.SLOTS).doc("""
                    str(object='') -> str
                    str(bytes_or_buffer[, encoding[, errors]]) -> str

                    Create a new string object from the given object. If encoding or
                    errors is specified, then the object must expose a data buffer
                    that will be decoded using the given encoding and error handler.
                    Otherwise, returns the result of object.__str__() (if defined)
                    or repr(object).
                    encoding defaults to sys.getdefaultencoding().
                    errors defaults to 'strict'.""")),
    PTraceback("traceback", PythonObject, newBuilder().basetype().slots(TracebackBuiltins.SLOTS)),
    PTuple("tuple", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(TupleBuiltins.SLOTS).doc("""
                    Built-in immutable sequence.

                    If no argument is given, the constructor returns an empty tuple.
                    If iterable is specified the tuple is initialized from iterable's items.

                    If the argument is a tuple, the return value is the same object.""")),
    PythonModule("module", PythonObject, newBuilder().basetype().addDict().slots(ModuleBuiltins.SLOTS).doc("""
                    Create a module object.

                    The name must be a string; the optional doc argument can have any type.""")),
    PythonModuleDef("moduledef", PythonObject, newBuilder()),
    Super("super", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().slots(SuperBuiltins.SLOTS).doc("""
                    super() -> same as super(__class__, <first argument>)
                    super(type) -> unbound super object
                    super(type, obj) -> bound super object; requires isinstance(obj, type)
                    super(type, type2) -> bound super object; requires issubclass(type2, type)
                    Typical use to call a cooperative superclass method:
                    class C(B):
                        def meth(self, arg):
                            super().meth(arg)
                    This works for class methods too:
                    class C(B):
                        @classmethod
                        def cmeth(cls, arg):
                            super().cmeth(arg)""")),
    PCode("code", PythonObject, newBuilder().slots(CodeBuiltins.SLOTS)),
    PGenericAlias("GenericAlias", PythonObject, newBuilder().publishInModule(J_TYPES).basetype().slots(GenericAliasBuiltins.SLOTS)),
    PGenericAliasIterator("generic_alias_iterator", PythonObject, newBuilder().slots(GenericAliasIteratorBuiltins.SLOTS)),
    PUnionType("UnionType", PythonObject, newBuilder().publishInModule(J_TYPES).slots(UnionTypeBuiltins.SLOTS)),
    PZip(
                    "zip",
                    PythonObject,
                    newBuilder().publishInModule(J_BUILTINS).basetype().slots(ZipBuiltins.SLOTS).doc("""
                                    zip(*iterables, strict=False) --> Yield tuples until an input is exhausted.

                                       >>> list(zip('abcdefg', range(3), range(4)))
                                       [('a', 0, 0), ('b', 1, 1), ('c', 2, 2)]

                                    The zip object yields n-length tuples, where n is the number of iterables
                                    passed as positional arguments to zip().  The i-th element in every tuple
                                    comes from the i-th iterable argument to zip().  This continues until the
                                    shortest argument is exhausted.

                                    If strict is true and one of the arguments is exhausted before the others,
                                    raise a ValueError.""")),
    PThreadLocal("_local", PythonObject, newBuilder().publishInModule(J__THREAD).basetype().slots(ThreadLocalBuiltins.SLOTS)),
    PLock("LockType", PythonObject, newBuilder().publishInModule(J__THREAD).disallowInstantiation().slots(CommonLockBuiltins.SLOTS, LockTypeBuiltins.SLOTS)),
    PRLock("RLock", PythonObject, newBuilder().publishInModule(J__THREAD).basetype().slots(CommonLockBuiltins.SLOTS, RLockBuiltins.SLOTS)),
    PSemLock("SemLock", PythonObject, newBuilder().publishInModule("_multiprocessing").basetype().slots(SemLockBuiltins.SLOTS)),
    PGraalPySemLock("SemLock", PythonObject, newBuilder().publishInModule("_multiprocessing_graalpy").basetype().slots(GraalPySemLockBuiltins.SLOTS)),
    PSocket("socket", PythonObject, newBuilder().publishInModule(J__SOCKET).basetype().slots(SocketBuiltins.SLOTS)),
    PStaticmethod("staticmethod", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(StaticmethodBuiltins.SLOTS).doc("""
                    staticmethod(function) -> method

                    Convert a function to be a static method.

                    A static method does not receive an implicit first argument.
                    To declare a static method, use this idiom:

                         class C:
                             @staticmethod
                             def f(arg1, arg2, argN):
                                 ...

                    It can be called either on the class (e.g. C.f()) or on an instance
                    (e.g. C().f()). Both the class and the instance are ignored, and
                    neither is passed implicitly as the first argument to the method.

                    Static methods in Python are similar to those found in Java or C++.
                    For a more advanced concept, see the classmethod builtin.""")),
    PClassmethod("classmethod", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(ClassmethodCommonBuiltins.SLOTS, ClassmethodBuiltins.SLOTS).doc("""
                    classmethod(function) -> method

                    Convert a function to be a class method.

                    A class method receives the class as implicit first argument,
                    just like an instance method receives the instance.
                    To declare a class method, use this idiom:

                      class C:
                          @classmethod
                          def f(cls, arg1, arg2, argN):
                              ...

                    It can be called either on the class (e.g. C.f()) or on an instance
                    (e.g. C().f()).  The instance is ignored except for its class.
                    If a class method is called for a derived class, the derived class
                    object is passed as the implied first argument.

                    Class methods are different than C++ or Java static methods.
                    If you want those, see the staticmethod builtin.""")),
    PInstancemethod("instancemethod", PythonObject, newBuilder().basetype().addDict().slots(InstancemethodBuiltins.SLOTS).doc("""
                    instancemethod(function)

                    Bind a function to a class.""")),
    PScandirIterator("ScandirIterator", PythonObject, newBuilder().moduleName(J_POSIX).disallowInstantiation().slots(ScandirIteratorBuiltins.SLOTS)),
    PDirEntry("DirEntry", PythonObject, newBuilder().publishInModule(J_POSIX).disallowInstantiation().slots(DirEntryBuiltins.SLOTS)),
    LsprofProfiler("Profiler", PythonObject, newBuilder().publishInModule("_lsprof").basetype().slots(ProfilerBuiltins.SLOTS)),
    PStruct("Struct", PythonObject, newBuilder().publishInModule(J__STRUCT).basetype().slots(StructBuiltins.SLOTS)),
    PStructUnpackIterator("unpack_iterator", PythonObject, newBuilder().publishInModule(J__STRUCT).basetype().slots(StructUnpackIteratorBuiltins.SLOTS)),
    Pickler("Pickler", PythonObject, newBuilder().publishInModule("_pickle").basetype().slots(PicklerBuiltins.SLOTS)),
    PicklerMemoProxy("PicklerMemoProxy", PythonObject, newBuilder().publishInModule("_pickle").basetype().slots(PicklerMemoProxyBuiltins.SLOTS)),
    UnpicklerMemoProxy("UnpicklerMemoProxy", PythonObject, newBuilder().publishInModule("_pickle").basetype().slots(UnpicklerMemoProxyBuiltins.SLOTS)),
    Unpickler("Unpickler", PythonObject, newBuilder().publishInModule("_pickle").basetype().slots(UnpicklerBuiltins.SLOTS)),
    PickleBuffer("PickleBuffer", PythonObject, newBuilder().publishInModule("_pickle").basetype().slots(PickleBufferBuiltins.SLOTS)),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", PythonObject, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(BaseExceptionBuiltins.SLOTS).doc("""
                    Common base class for all exceptions""")),
    PBaseExceptionGroup("BaseExceptionGroup", PBaseException, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(BaseExceptionGroupBuiltins.SLOTS).doc("""
                    A combination of multiple unrelated exceptions.""")),
    SystemExit("SystemExit", PBaseException, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(SystemExitBuiltins.SLOTS)),
    KeyboardInterrupt("KeyboardInterrupt", PBaseException, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    GeneratorExit("GeneratorExit", PBaseException, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    Exception("Exception", PBaseException, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ReferenceError("ReferenceError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    RuntimeError("RuntimeError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    NotImplementedError("NotImplementedError", RuntimeError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    SyntaxError("SyntaxError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(SyntaxErrorBuiltins.SLOTS)),
    IndentationError("IndentationError", SyntaxError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(SyntaxErrorBuiltins.SLOTS)),
    TabError("TabError", IndentationError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(SyntaxErrorBuiltins.SLOTS)),
    SystemError("SystemError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    TypeError("TypeError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ValueError("ValueError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    StopIteration("StopIteration", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(StopIterationBuiltins.SLOTS)),
    StopAsyncIteration("StopAsyncIteration", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ArithmeticError("ArithmeticError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    FloatingPointError("FloatingPointError", ArithmeticError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    OverflowError("OverflowError", ArithmeticError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ZeroDivisionError("ZeroDivisionError", ArithmeticError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    AssertionError("AssertionError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    AttributeError("AttributeError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    BufferError("BufferError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    EOFError("EOFError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ImportError("ImportError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(ImportErrorBuiltins.SLOTS)),
    ModuleNotFoundError("ModuleNotFoundError", ImportError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    LookupError("LookupError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    IndexError("IndexError", LookupError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    KeyError("KeyError", LookupError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(KeyErrorBuiltins.SLOTS)),
    MemoryError("MemoryError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    NameError("NameError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    UnboundLocalError("UnboundLocalError", NameError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    OSError("OSError", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(OsErrorBuiltins.SLOTS)),
    BlockingIOError("BlockingIOError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ChildProcessError("ChildProcessError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionError("ConnectionError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    BrokenPipeError("BrokenPipeError", ConnectionError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionAbortedError("ConnectionAbortedError", ConnectionError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionRefusedError("ConnectionRefusedError", ConnectionError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionResetError("ConnectionResetError", ConnectionError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    FileExistsError("FileExistsError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    FileNotFoundError("FileNotFoundError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    InterruptedError("InterruptedError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    IsADirectoryError("IsADirectoryError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    NotADirectoryError("NotADirectoryError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    PermissionError("PermissionError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ProcessLookupError("ProcessLookupError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    TimeoutError("TimeoutError", OSError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ZLibError("error", Exception, newBuilder().publishInModule("zlib").basetype().addDict()),
    CSVError("Error", Exception, newBuilder().publishInModule("_csv").basetype().addDict()),
    LZMAError("LZMAError", Exception, newBuilder().publishInModule("_lzma").basetype().addDict()),
    StructError("StructError", Exception, newBuilder().publishInModule(J__STRUCT).basetype().addDict()),
    PickleError("PickleError", Exception, newBuilder().publishInModule("_pickle").basetype().addDict()),
    PicklingError("PicklingError", PickleError, newBuilder().publishInModule("_pickle").basetype().addDict()),
    UnpicklingError("UnpicklingError", PickleError, newBuilder().publishInModule("_pickle").basetype().addDict()),
    SocketGAIError("gaierror", OSError, newBuilder().publishInModule(J__SOCKET).basetype().addDict()),
    SocketHError("herror", OSError, newBuilder().publishInModule(J__SOCKET).basetype().addDict()),
    BinasciiError("Error", ValueError, newBuilder().publishInModule("binascii").basetype().addDict()),
    BinasciiIncomplete("Incomplete", Exception, newBuilder().publishInModule("binascii").basetype().addDict()),
    SSLError("SSLError", OSError, newBuilder().publishInModule(J__SSL).basetype().addDict().slots(SSLErrorBuiltins.SLOTS)),
    SSLZeroReturnError("SSLZeroReturnError", SSLError, newBuilder().publishInModule(J__SSL).basetype().addDict()),
    SSLWantReadError("SSLWantReadError", SSLError, newBuilder().publishInModule(J__SSL).basetype().addDict()),
    SSLWantWriteError("SSLWantWriteError", SSLError, newBuilder().publishInModule(J__SSL).basetype().addDict()),
    SSLSyscallError("SSLSyscallError", SSLError, newBuilder().publishInModule(J__SSL).basetype().addDict()),
    SSLEOFError("SSLEOFError", SSLError, newBuilder().publishInModule(J__SSL).basetype().addDict()),
    SSLCertVerificationError("SSLCertVerificationError", SSLError, newBuilder().publishInModule(J__SSL).basetype().addDict()),

    // todo: all OS errors

    UnicodeError("UnicodeError", ValueError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    UnicodeDecodeError("UnicodeDecodeError", UnicodeError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(UnicodeDecodeErrorBuiltins.SLOTS)),
    UnicodeEncodeError("UnicodeEncodeError", UnicodeError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(UnicodeEncodeErrorBuiltins.SLOTS)),
    UnicodeTranslateError("UnicodeTranslateError", UnicodeError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict().slots(UnicodeTranslateErrorBuiltins.SLOTS)),
    RecursionError("RecursionError", RuntimeError, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),

    /*
     * _io.UnsupportedOperation inherits from ValueError and OSError done currently within
     * IOModuleBuiltins class
     */
    IOUnsupportedOperation("UnsupportedOperation", OSError, newBuilder().publishInModule("io").basetype().addDict()),

    Empty("Empty", Exception, newBuilder().publishInModule("_queue").basetype().addDict()),

    UnsupportedMessage("UnsupportedMessage", Exception, newBuilder().publishInModule(J_POLYGLOT).basetype().addDict()),

    // warnings
    Warning("Warning", Exception, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    BytesWarning("BytesWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    DeprecationWarning("DeprecationWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    FutureWarning("FutureWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ImportWarning("ImportWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    PendingDeprecationWarning("PendingDeprecationWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    ResourceWarning("ResourceWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    RuntimeWarning("RuntimeWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    SyntaxWarning("SyntaxWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    UnicodeWarning("UnicodeWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    UserWarning("UserWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),
    EncodingWarning("EncodingWarning", Warning, newBuilder().publishInModule(J_BUILTINS).basetype().addDict()),

    // Foreign
    ForeignObject("ForeignObject", PythonObject, newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().disallowInstantiation().slots(ForeignObjectBuiltins.SLOTS)),
    ForeignNumber(
                    "ForeignNumber",
                    ForeignObject,
                    newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().disallowInstantiation().slots(ForeignNumberBuiltins.SLOTS)),
    ForeignBoolean(
                    "ForeignBoolean",
                    ForeignNumber,
                    newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().disallowInstantiation().slots(ForeignBooleanBuiltins.SLOTS)),
    ForeignAbstractClass("ForeignAbstractClass", ForeignObject, newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().disallowInstantiation()),
    ForeignExecutable("ForeignExecutable", ForeignObject, newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().disallowInstantiation().slots(ForeignExecutableBuiltins.SLOTS)),
    ForeignInstantiable("ForeignInstantiable", ForeignObject, newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().slots(ForeignInstantiableBuiltins.SLOTS)),
    ForeignIterable("ForeignIterable", ForeignObject, newBuilder().publishInModule(J_POLYGLOT).basetype().addDict().disallowInstantiation().slots(ForeignIterableBuiltins.SLOTS)),

    // bz2
    BZ2Compressor("BZ2Compressor", PythonObject, newBuilder().publishInModule("_bz2").basetype().slots(BZ2CompressorBuiltins.SLOTS)),
    BZ2Decompressor("BZ2Decompressor", PythonObject, newBuilder().publishInModule("_bz2").basetype().slots(BZ2DecompressorBuiltins.SLOTS)),

    // lzma
    PLZMACompressor("LZMACompressor", PythonObject, newBuilder().publishInModule("_lzma").basetype().slots(LZMACompressorBuiltins.SLOTS)),
    PLZMADecompressor("LZMADecompressor", PythonObject, newBuilder().publishInModule("_lzma").basetype().slots(LZMADecompressorBuiltins.SLOTS)),

    // zlib
    ZlibCompress("Compress", PythonObject, newBuilder().publishInModule("zlib").disallowInstantiation()),
    ZlibDecompress("Decompress", PythonObject, newBuilder().publishInModule("zlib").disallowInstantiation()),

    // io
    PIOBase("_IOBase", PythonObject, newBuilder().publishInModule("_io").basetype().addDict().slots(IOBaseBuiltins.SLOTS)),
    PRawIOBase("_RawIOBase", PIOBase, newBuilder().publishInModule("_io").basetype().slots(IOBaseBuiltins.SLOTS)),
    PTextIOBase("_TextIOBase", PIOBase, newBuilder().publishInModule("_io").basetype().slots(IOBaseBuiltins.SLOTS)),
    PBufferedIOBase("_BufferedIOBase", PIOBase, newBuilder().publishInModule("_io").basetype().slots(IOBaseBuiltins.SLOTS)),
    PBufferedReader(
                    "BufferedReader",
                    PBufferedIOBase,
                    newBuilder().publishInModule("_io").basetype().addDict().slots(BufferedReaderMixinBuiltins.SLOTS, BufferedIOMixinBuiltins.SLOTS, BufferedReaderBuiltins.SLOTS)),
    PBufferedWriter("BufferedWriter", PBufferedIOBase, newBuilder().publishInModule("_io").basetype().addDict().slots(BufferedIOMixinBuiltins.SLOTS, BufferedWriterBuiltins.SLOTS)),
    PBufferedRWPair("BufferedRWPair", PBufferedIOBase, newBuilder().publishInModule("_io").basetype().addDict().slots(BufferedRWPairBuiltins.SLOTS)),
    PBufferedRandom(
                    "BufferedRandom",
                    PBufferedIOBase,
                    newBuilder().publishInModule("_io").basetype().addDict().slots(BufferedReaderMixinBuiltins.SLOTS, BufferedIOMixinBuiltins.SLOTS, BufferedRandomBuiltins.SLOTS)),
    PWindowsConsoleIO("_WindowsConsoleIO", PRawIOBase, newBuilder().moduleName("_io").basetype()),
    PFileIO("FileIO", PRawIOBase, newBuilder().publishInModule("_io").basetype().addDict().slots(FileIOBuiltins.SLOTS)),
    PTextIOWrapper("TextIOWrapper", PTextIOBase, newBuilder().publishInModule("_io").basetype().addDict().slots(TextIOWrapperBuiltins.SLOTS)),
    PIncrementalNewlineDecoder("IncrementalNewlineDecoder", PythonObject, newBuilder().publishInModule("_io").basetype().slots(IncrementalNewlineDecoderBuiltins.SLOTS)),
    PStringIO("StringIO", PTextIOBase, newBuilder().publishInModule("_io").basetype().addDict().slots(StringIOBuiltins.SLOTS)),
    PBytesIO("BytesIO", PBufferedIOBase, newBuilder().publishInModule("_io").basetype().addDict().slots(BytesIOBuiltins.SLOTS)),
    PBytesIOBuf("_BytesIOBuffer", PythonObject, newBuilder().moduleName("_io").basetype()),

    PStatResult(
                    "stat_result",
                    PTuple,
                    newBuilder().publishInModule("os").slots(StructSequenceBuiltins.SLOTS, StatResultBuiltins.SLOTS).doc("""
                                    stat_result: Result from stat, fstat, or lstat.

                                    This object may be accessed either as a tuple of
                                      (mode, ino, dev, nlink, uid, gid, size, atime, mtime, ctime)
                                    or via the attributes st_mode, st_ino, st_dev, st_nlink, st_uid, and so on.

                                    Posix/windows: If your platform supports st_blksize, st_blocks, st_rdev,
                                    or st_flags, they are available as attributes only.

                                    See os.stat for more information.""")),
    PStatvfsResult("statvfs_result", PTuple, newBuilder().publishInModule("os").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    statvfs_result: Result from statvfs or fstatvfs.

                    This object may be accessed either as a tuple of
                      (bsize, frsize, blocks, bfree, bavail, files, ffree, favail, flag, namemax),
                    or via the attributes f_bsize, f_frsize, f_blocks, f_bfree, and so on.

                    See os.statvfs for more information.""")),
    PTerminalSize("terminal_size", PTuple, newBuilder().publishInModule("os").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    A tuple of (columns, lines) for holding terminal window size""")),
    PUnameResult("uname_result", PTuple, newBuilder().publishInModule(J_POSIX).slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    uname_result: Result from os.uname().

                    This object may be accessed either as a tuple of
                      (sysname, nodename, release, version, machine),
                    or via the attributes sysname, nodename, release, version, and machine.

                    See os.uname for more information.""")),
    PStructTime("struct_time", PTuple, newBuilder().publishInModule("time").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    The time value as returned by gmtime(), localtime(), and strptime(), and
                     accepted by asctime(), mktime() and strftime().  May be considered as a
                     sequence of 9 integers.

                     Note that several fields' values are not the same as those defined by
                     the C language standard for struct tm.  For example, the value of the
                     field tm_year is the actual year, not year - 1900.  See individual
                     fields' descriptions for details.""")),
    PProfilerEntry("profiler_entry", PTuple, newBuilder().publishInModule("_lsprof").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS)),
    PProfilerSubentry("profiler_subentry", PTuple, newBuilder().publishInModule("_lsprof").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS)),
    PStructPasswd("struct_passwd", PTuple, newBuilder().publishInModule("pwd").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    pwd.struct_passwd: Results from getpw*() routines.

                    This object may be accessed either as a tuple of
                      (pw_name,pw_passwd,pw_uid,pw_gid,pw_gecos,pw_dir,pw_shell)
                    or via the object attributes as named in the above tuple.""")),
    PStructRusage("struct_rusage", PTuple, newBuilder().publishInModule("resource").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    struct_rusage: Result from getrusage.

                    This object may be accessed either as a tuple of
                        (utime,stime,maxrss,ixrss,idrss,isrss,minflt,majflt,
                        nswap,inblock,oublock,msgsnd,msgrcv,nsignals,nvcsw,nivcsw)
                    or via the attributes ru_utime, ru_stime, ru_maxrss, and so on.""")),
    PVersionInfo("version_info", PTuple, newBuilder().publishInModule("sys").disallowInstantiation().slots(StructSequenceBuiltins.SLOTS).doc("""
                    sys.version_info

                    Version information as a named tuple.""")),
    PWindowsVersion("windowsversion", PTuple, newBuilder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    sys.getwindowsversion

                    Return info about the running version of Windows as a named tuple.""")),
    PFlags("flags", PTuple, newBuilder().publishInModule("sys").disallowInstantiation().slots(StructSequenceBuiltins.SLOTS).doc("""
                    sys.flags

                    Flags provided through command line arguments or environment vars.""")),
    PFloatInfo("float_info", PTuple, newBuilder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    sys.float_info

                    A named tuple holding information about the float type. It contains low level
                    information about the precision and internal representation. Please study
                    your system's :file:`float.h` for more information.""")),
    PIntInfo("int_info", PTuple, newBuilder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    sys.int_info

                    A named tuple that holds information about Python's
                    internal representation of integers.  The attributes are read only.""")),
    PHashInfo("hash_info", PTuple, newBuilder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    hash_info

                    A named tuple providing parameters used for computing
                    hashes. The attributes are read only.""")),
    PThreadInfo("thread_info", PTuple, newBuilder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                    sys.thread_info

                    A named tuple holding information about the thread implementation.""")),
    PUnraisableHookArgs(
                    "UnraisableHookArgs",
                    PTuple,
                    newBuilder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS, InstantiableStructSequenceBuiltins.SLOTS).doc("""
                                    UnraisableHookArgs

                                    Type used to pass arguments to sys.unraisablehook.""")),

    PSSLSession("SSLSession", PythonObject, newBuilder().publishInModule(J__SSL).disallowInstantiation()),
    PSSLContext("_SSLContext", PythonObject, newBuilder().publishInModule(J__SSL).basetype().slots(SSLContextBuiltins.SLOTS)),
    PSSLSocket("_SSLSocket", PythonObject, newBuilder().publishInModule(J__SSL).basetype()),
    PMemoryBIO("MemoryBIO", PythonObject, newBuilder().publishInModule(J__SSL).basetype().slots(MemoryBIOBuiltins.SLOTS)),

    // itertools
    PTee("_tee", PythonObject, newBuilder().publishInModule("itertools").slots(TeeBuiltins.SLOTS)),
    PTeeDataObject("_tee_dataobject", PythonObject, newBuilder().publishInModule("itertools").slots(TeeDataObjectBuiltins.SLOTS)),
    PAccumulate("accumulate", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(AccumulateBuiltins.SLOTS).doc("""
                    accumulate(iterable) --> accumulate object

                    Return series of accumulated sums.""")),
    PCombinations("combinations", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(CombinationsBuiltins.SLOTS).doc("""
                    combinations(iterable, r) --> combinations object

                    Return successive r-length combinations of elements in the iterable.

                    combinations(range(4), 3) --> (0,1,2), (0,1,3), (0,2,3), (1,2,3)""")),
    PCombinationsWithReplacement(
                    "combinations_with_replacement",
                    PythonObject,
                    newBuilder().publishInModule("itertools").basetype().slots(CombinationsBuiltins.SLOTS, CombinationsWithReplacementBuiltins.SLOTS).doc("""
                                    combinations_with_replacement(iterable, r) --> combinations_with_replacement object

                                    Return successive r-length combinations of elements in the iterable
                                    allowing individual elements to have successive repeats.
                                        combinations_with_replacement('ABC', 2) --> AA AB AC BB BC CC""")),
    PCompress("compress", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(CompressBuiltins.SLOTS).doc("""
                    Make an iterator that filters elements from *data* returning
                    only those that have a corresponding element in *selectors* that evaluates to
                    ``True``.  Stops when either the *data* or *selectors* iterables has been
                    exhausted.
                    Equivalent to::

                    \tdef compress(data, selectors):
                    \t\t# compress('ABCDEF', [1,0,1,0,1,1]) --> A C E F
                    \t\treturn (d for d, s in zip(data, selectors) if s)""")),
    PCycle("cycle", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(CycleBuiltins.SLOTS).doc("""
                    Make an iterator returning elements from the iterable and
                        saving a copy of each. When the iterable is exhausted, return
                        elements from the saved copy. Repeats indefinitely.

                        Equivalent to :

                        def cycle(iterable):
                        \tsaved = []
                        \tfor element in iterable:
                        \t\tyield element
                        \t\tsaved.append(element)
                        \twhile saved:
                        \t\tfor element in saved:
                        \t\t\tyield element""")),
    PDropwhile("dropwhile", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(DropwhileBuiltins.SLOTS).doc("""
                    dropwhile(predicate, iterable) --> dropwhile object

                    Drop items from the iterable while predicate(item) is true.
                    Afterwards, return every element until the iterable is exhausted.""")),
    PFilterfalse("filterfalse", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(FilterfalseBuiltins.SLOTS).doc("""
                    filterfalse(function or None, sequence) --> filterfalse object

                    Return those items of sequence for which function(item) is false.
                    If function is None, return the items that are false.""")),
    PGroupBy("groupby", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(GroupByBuiltins.SLOTS).doc("""
                    Make an iterator that returns consecutive keys and groups from the
                    iterable. The key is a function computing a key value for each
                    element. If not specified or is None, key defaults to an identity
                    function and returns the element unchanged. Generally, the
                    iterable needs to already be sorted on the same key function.

                    The returned group is itself an iterator that shares the
                    underlying iterable with groupby(). Because the source is shared,
                    when the groupby object is advanced, the previous group is no
                    longer visible. So, if that data is needed later, it should be
                    stored as a list:

                    \tgroups = []
                    \tuniquekeys = []
                    \tfor k, g in groupby(data, keyfunc):
                    \t\tgroups.append(list(g))      # Store group iterator as a list
                    \t\tuniquekeys.append(k)""")),
    PGrouper("grouper", PythonObject, newBuilder().publishInModule("itertools").slots(GrouperBuiltins.SLOTS)),
    PPairwise("pairwise", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(PairwiseBuiltins.SLOTS).doc("""
                    Return an iterator of overlapping pairs taken from the input iterator.

                        s -> (s0,s1), (s1,s2), (s2, s3), ...""")),
    PPermutations("permutations", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(PermutationsBuiltins.SLOTS).doc("""
                    permutations(iterable[, r]) --> permutations object

                    Return successive r-length permutations of elements in the iterable.

                    permutations(range(3), 2) --> (0,1), (0,2), (1,0), (1,2), (2,0), (2,1)""")),
    PProduct("product", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(ProductBuiltins.SLOTS).doc("""
                    Cartesian product of input iterables.

                    Equivalent to nested for-loops in a generator expression. For example,
                     ``product(A, B)`` returns the same as ``((x,y) for x in A for y in B)``.

                    The nested loops cycle like an odometer with the rightmost element advancing
                     on every iteration.  This pattern creates a lexicographic ordering so that if
                     the input's iterables are sorted, the product tuples are emitted in sorted
                     order.

                    To compute the product of an iterable with itself, specify the number of
                     repetitions with the optional *repeat* keyword argument.  For example,
                     ``product(A, repeat=4)`` means the same as ``product(A, A, A, A)``.

                    This function is equivalent to the following code, except that the
                     actual implementation does not build up intermediate results in memory::

                    def product(*args, **kwds):
                    \t# product('ABCD', 'xy') --> Ax Ay Bx By Cx Cy Dx Dy
                    \t# product(range(2), repeat=3) --> 000 001 010 011 100 101 110 111
                    \tpools = map(tuple, args) * kwds.get('repeat', 1)
                    \tresult = [[]]
                    \tfor pool in pools:
                    \t\tresult = [x+[y] for x in result for y in pool]
                    \tfor prod in result:
                    \t\tyield tuple(prod)""")),
    PRepeat("repeat", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(RepeatBuiltins.SLOTS).doc("""
                    repeat(object [,times]) -> create an iterator which returns the object
                    for the specified number of times.  If not specified, returns the object
                    endlessly.""")),
    PChain("chain", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(ChainBuiltins.SLOTS).doc("""
                    Return a chain object whose .__next__() method returns elements from the
                    first iterable until it is exhausted, then elements from the next
                    iterable, until all of the iterables are exhausted.""")),
    PCount("count", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(CountBuiltins.SLOTS)),
    PIslice("islice", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(IsliceBuiltins.SLOTS)),
    PStarmap("starmap", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(StarmapBuiltins.SLOTS).doc("""
                    starmap(function, sequence) --> starmap object

                    Return an iterator whose values are returned from the function evaluated
                    with an argument tuple taken from the given sequence.""")),
    PTakewhile("takewhile", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(TakewhileBuiltins.SLOTS).doc("""
                    Make an iterator that returns elements from the iterable as
                    long as the predicate is true.

                    Equivalent to :

                    def takewhile(predicate, iterable):
                    \tfor x in iterable:
                    \t\tif predicate(x):
                    \t\t\tyield x
                    \t\telse:
                    \t\t\tbreak""")),
    PZipLongest("zip_longest", PythonObject, newBuilder().publishInModule("itertools").basetype().slots(ZipLongestBuiltins.SLOTS).doc("""
                    zip_longest(iter1 [,iter2 [...]], [fillvalue=None]) --> zip_longest object

                    Return a zip_longest object whose .next() method returns a tuple where
                    the i-th element comes from the i-th iterable argument.  The .next()
                    method continues until the longest iterable in the argument sequence
                    is exhausted and then it raises StopIteration.  When the shorter iterables
                    are exhausted, the fillvalue is substituted in their place.  The fillvalue
                    defaults to None or can be specified by a keyword argument.""")),

    // json
    JSONScanner(
                    "Scanner",
                    PythonObject,
                    newBuilder().publishInModule("_json").basetype().slots(JSONScannerBuiltins.SLOTS).doc("""
                                    JSON scanner object""")),
    JSONEncoder(
                    "Encoder",
                    PythonObject,
                    newBuilder().publishInModule("_json").basetype().slots(JSONEncoderBuiltins.SLOTS).doc("""
                                    _iterencode(obj, _current_indent_level) -> iterable""")),

    // csv
    CSVDialect("Dialect", PythonObject, newBuilder().publishInModule("_csv").basetype().slots(CSVDialectBuiltins.SLOTS)),
    CSVReader("Reader", PythonObject, newBuilder().publishInModule("_csv").basetype().disallowInstantiation().slots(CSVReaderBuiltins.SLOTS)),
    CSVWriter("Writer", PythonObject, newBuilder().publishInModule("_csv").basetype().disallowInstantiation()),

    // codecs
    PEncodingMap("EncodingMap", PythonObject, newBuilder().disallowInstantiation()),

    // hashlib
    MD5Type("md5", PythonObject, newBuilder().publishInModule("_md5").basetype().disallowInstantiation()),
    SHA1Type("sha1", PythonObject, newBuilder().publishInModule("_sha1").basetype().disallowInstantiation()),
    SHA224Type("sha224", PythonObject, newBuilder().publishInModule("_sha256").basetype().disallowInstantiation()),
    SHA256Type("sha256", PythonObject, newBuilder().publishInModule("_sha256").basetype().disallowInstantiation()),
    SHA384Type("sha384", PythonObject, newBuilder().publishInModule("_sha512").basetype().disallowInstantiation()),
    SHA512Type("sha512", PythonObject, newBuilder().publishInModule("_sha512").basetype().disallowInstantiation()),
    Sha3SHA224Type("sha3_224", PythonObject, newBuilder().publishInModule("_sha3").basetype().slots(Sha3Builtins.SLOTS)),
    Sha3SHA256Type("sha3_256", PythonObject, newBuilder().publishInModule("_sha3").basetype().slots(Sha3Builtins.SLOTS)),
    Sha3SHA384Type("sha3_384", PythonObject, newBuilder().publishInModule("_sha3").basetype().slots(Sha3Builtins.SLOTS)),
    Sha3SHA512Type("sha3_512", PythonObject, newBuilder().publishInModule("_sha3").basetype().slots(Sha3Builtins.SLOTS)),
    Sha3Shake128Type("shake_128", PythonObject, newBuilder().publishInModule("_sha3").basetype().slots(Sha3Builtins.SLOTS)),
    Sha3Shake256Type("shake_256", PythonObject, newBuilder().publishInModule("_sha3").basetype().slots(Sha3Builtins.SLOTS)),
    Blake2bType("blake2b", PythonObject, newBuilder().publishInModule("_blake2").basetype().slots(Blake2bObjectBuiltins.SLOTS)),
    /* Note we reuse the blake2b slots */
    Blake2sType("blake2s", PythonObject, newBuilder().publishInModule("_blake2").basetype().slots(Blake2bObjectBuiltins.SLOTS)),
    HashlibHash("HASH", PythonObject, newBuilder().publishInModule("_hashlib").basetype().disallowInstantiation().slots(HashObjectBuiltins.SLOTS)),
    HashlibHashXof("HASHXOF", HashlibHash, newBuilder().publishInModule("_hashlib").disallowInstantiation()),
    HashlibHmac("HMAC", PythonObject, newBuilder().publishInModule("_hashlib").basetype().disallowInstantiation().slots(HashObjectBuiltins.SLOTS)),
    UnsupportedDigestmodError("UnsupportedDigestmodError", ValueError, newBuilder().publishInModule("_hashlib").basetype().addDict()),

    // _ast (rest of the classes are not builtin, they are generated in AstModuleBuiltins)
    AST("AST", PythonObject, newBuilder().moduleName("ast").publishInModule("_ast").basetype().addDict().slots(AstBuiltins.SLOTS)),

    // _ctype
    CArgObject("CArgObject", PythonObject, newBuilder().basetype().slots(CArgObjectBuiltins.SLOTS)),
    CThunkObject("CThunkObject", PythonObject, newBuilder().publishInModule(J__CTYPES).basetype()),
    StgDict("StgDict", PDict, newBuilder().slots(StgDictBuiltins.SLOTS)),
    PyCStructType(
                    "PyCStructType",
                    PythonClass,
                    newBuilder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS, StructUnionTypeBuiltins.SLOTS, PyCStructTypeBuiltins.SLOTS)),
    UnionType(
                    "UnionType",
                    PythonClass,
                    newBuilder().publishInModule(J__CTYPES).basetype().slots(
                                    CDataTypeSequenceBuiltins.SLOTS, StructUnionTypeBuiltins.SLOTS,
                                    com.oracle.graal.python.builtins.modules.ctypes.UnionTypeBuiltins.SLOTS)),
    PyCPointerType(
                    "PyCPointerType",
                    PythonClass,
                    newBuilder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS, PyCPointerTypeBuiltins.SLOTS)),
    PyCArrayType(
                    "PyCArrayType",
                    PythonClass,
                    newBuilder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS, PyCArrayTypeBuiltins.SLOTS)),
    PyCSimpleType(
                    "PyCSimpleType",
                    PythonClass,
                    newBuilder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS, PyCPointerTypeBuiltins.SLOTS, PyCSimpleTypeBuiltins.SLOTS)),
    PyCFuncPtrType(
                    "PyCFuncPtrType",
                    PythonClass,
                    newBuilder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS, PyCFuncPtrTypeBuiltins.SLOTS)),
    PyCData("_CData", PythonObject, newBuilder().publishInModule(J__CTYPES).basetype().slots(CDataBuiltins.SLOTS)), /*- type = PyCStructType */
    Structure("Structure", PyCData, newBuilder().publishInModule(J__CTYPES).basetype().slots(StructureBuiltins.SLOTS)), /*- type = PyCStructType */
    Union("Union", PyCData, newBuilder().publishInModule(J__CTYPES).basetype().slots(StructureBuiltins.SLOTS)), /*- type = UnionType */
    PyCPointer("_Pointer", PyCData, newBuilder().publishInModule(J__CTYPES).basetype().slots(PyCPointerBuiltins.SLOTS)), /*- type = PyCPointerType */
    PyCArray("Array", PyCData, newBuilder().publishInModule(J__CTYPES).basetype().slots(PyCArrayBuiltins.SLOTS)), /*- type = PyCArrayType */
    SimpleCData("_SimpleCData", PyCData, newBuilder().publishInModule(J__CTYPES).basetype().slots(SimpleCDataBuiltins.SLOTS)), /*- type = PyCStructType */
    PyCFuncPtr("PyCFuncPtr", PyCData, newBuilder().publishInModule(J__CTYPES).basetype().slots(PyCFuncPtrBuiltins.SLOTS)), /*- type = PyCFuncPtrType */
    CField("CField", PythonObject, newBuilder().publishInModule(J__CTYPES).basetype().slots(CFieldBuiltins.SLOTS)),
    DictRemover("DictRemover", PythonObject, newBuilder().publishInModule(J__CTYPES).basetype()),
    StructParam("StructParam_Type", PythonObject, newBuilder().publishInModule(J__CTYPES).basetype()),
    ArgError("ArgumentError", PBaseException, newBuilder().publishInModule(J__CTYPES).basetype().addDict()),

    // _multibytecodec
    MultibyteCodec("MultibyteCodec", PythonObject, newBuilder().publishInModule("_multibytecodec").basetype().addDict().disallowInstantiation()),
    MultibyteIncrementalEncoder("MultibyteIncrementalEncoder", PythonObject, newBuilder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteIncrementalEncoderBuiltins.SLOTS)),
    MultibyteIncrementalDecoder("MultibyteIncrementalDecoder", PythonObject, newBuilder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteIncrementalDecoderBuiltins.SLOTS)),
    MultibyteStreamReader("MultibyteStreamReader", PythonObject, newBuilder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteStreamReaderBuiltins.SLOTS)),
    MultibyteStreamWriter("MultibyteStreamWriter", PythonObject, newBuilder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteStreamWriterBuiltins.SLOTS)),

    // contextvars
    ContextVarsToken("Token", PythonObject, newBuilder().publishInModule(J__CONTEXTVARS).slots(TokenBuiltins.SLOTS)),
    ContextVarsContext("Context", PythonObject, newBuilder().publishInModule(J__CONTEXTVARS).slots(ContextBuiltins.SLOTS)),
    ContextVar("ContextVar", PythonObject, newBuilder().publishInModule(J__CONTEXTVARS).slots(ContextVarBuiltins.SLOTS)),
    // CPython uses separate keys, values, items python types for the iterators.
    ContextIterator("context_iterator", PythonObject, newBuilder().publishInModule(J__CONTEXTVARS).slots(ContextIteratorBuiltins.SLOTS)),

    Capsule("PyCapsule", PythonObject, newBuilder().basetype()),

    PTokenizerIter("TokenizerIter", PythonObject, newBuilder().publishInModule("_tokenize").basetype().slots(TokenizerIterBuiltins.SLOTS));

    private static TypeBuilder newBuilder() {
        return new TypeBuilder();
    }

    private static final class TypeBuilder {
        private String publishInModule;
        private String moduleName;
        private boolean basetype;
        private boolean addDict;
        private boolean disallowInstantiation;
        private TpSlots slots;
        private String doc;

        public TypeBuilder publishInModule(String publishInModule) {
            this.publishInModule = publishInModule;
            if (moduleName == null) {
                this.moduleName = publishInModule;
            }
            return this;
        }

        public TypeBuilder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public TypeBuilder basetype() {
            this.basetype = true;
            return this;
        }

        public TypeBuilder addDict() {
            this.addDict = true;
            return this;
        }

        public TypeBuilder disallowInstantiation() {
            this.disallowInstantiation = true;
            return this;
        }

        public TypeBuilder slots(TpSlots slots) {
            this.slots = slots;
            return this;
        }

        public TypeBuilder slots(TpSlots slots1, TpSlots slots2) {
            this.slots = slots1.copy().overrideIgnoreGroups(slots2).build();
            return this;
        }

        public TypeBuilder slots(TpSlots slots1, TpSlots slots2, TpSlots slots3) {
            this.slots = slots1.copy().overrideIgnoreGroups(slots2).overrideIgnoreGroups(slots3).build();
            return this;
        }

        public TypeBuilder doc(String doc) {
            this.doc = doc;
            return this;
        }
    }

    private final TruffleString name;
    private final PythonBuiltinClassType base;
    private final TruffleString publishInModule;
    private final TruffleString moduleName;
    // This is the name qualified by module used for printing. But the actual __qualname__ is just
    // plain name without module
    private final TruffleString printName;
    private final boolean basetype;
    private final boolean isBuiltinWithDict;
    private final boolean disallowInstantiation;
    private final TruffleString doc;

    // initialized in static constructor
    @CompilationFinal private PythonBuiltinClassType type;
    @CompilationFinal private int weaklistoffset;

    /**
     * Lookup cache for special slots defined in {@link SpecialMethodSlot}. Use
     * {@link SpecialMethodSlot} to access the values. Unlike the cache in
     * {@link com.oracle.graal.python.builtins.objects.type.PythonManagedClass}, this caches only
     * builtin context independent values, most notably instances of {@link BuiltinMethodDescriptor}
     * .
     */
    private Object[] specialMethodSlots;

    /**
     * The slots defined directly on the builtin class.
     */
    private final TpSlots declaredSlots;

    /**
     * The actual slots including slots inherited from base classes
     */
    private final TpSlots slots;

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, TypeBuilder builder) {
        this.name = toTruffleStringUncached(name);
        this.base = base;
        this.publishInModule = toTruffleStringUncached(builder.publishInModule);
        this.moduleName = builder.moduleName != null ? toTruffleStringUncached(builder.moduleName) : null;
        if (builder.moduleName != null && !J_BUILTINS.equals(builder.moduleName)) {
            printName = toTruffleStringUncached(builder.moduleName + "." + name);
        } else {
            printName = this.name;
        }
        this.basetype = builder.basetype;
        this.isBuiltinWithDict = builder.addDict;
        this.weaklistoffset = -1;
        this.declaredSlots = builder.slots != null ? builder.slots : TpSlots.createEmpty();
        boolean disallowInstantiation = builder.disallowInstantiation;
        // logic from type_ready_set_new
        // base.base == null is a roundabout way to check for base == object
        if (declaredSlots.tp_new() == null && base.base == null) {
            disallowInstantiation = true;
        }
        if (base == null) {
            this.slots = declaredSlots;
        } else {
            var slotBuilder = base.slots.copy();
            slotBuilder.overrideIgnoreGroups(declaredSlots);
            if (disallowInstantiation) {
                slotBuilder.set(TpSlots.TpSlotMeta.TP_NEW, null);
            }
            this.slots = slotBuilder.build();
        }
        this.disallowInstantiation = disallowInstantiation;
        this.doc = toTruffleStringUncached(builder.doc);
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

    public boolean disallowInstantiation() {
        return disallowInstantiation;
    }

    public TruffleString getPublishInModule() {
        return publishInModule;
    }

    public TruffleString getModuleName() {
        return moduleName;
    }

    public TruffleString getDoc() {
        return doc;
    }

    /**
     * Access the values using methods in {@link SpecialMethodSlot}.
     */
    public Object[] getSpecialMethodSlots() {
        return specialMethodSlots;
    }

    public TpSlots getSlots() {
        return slots;
    }

    public TpSlots getDeclaredSlots() {
        return declaredSlots;
    }

    public void setSpecialMethodSlots(Object[] slots) {
        assert specialMethodSlots == null; // should be assigned only once per VM
        specialMethodSlots = slots;
    }

    public int getWeaklistoffset() {
        return weaklistoffset;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return name.toJavaStringUncached();
    }

    public final Shape getInstanceShape(PythonLanguage lang) {
        return lang.getBuiltinTypeInstanceShape(this);
    }

    @CompilationFinal(dimensions = 1) public static final PythonBuiltinClassType[] VALUES = Arrays.copyOf(values(), values().length);

    static {
        PythonObject.type = PythonClass;
        Structure.type = PyCStructType;
        Union.type = UnionType;
        PyCPointer.type = PyCPointerType;
        PyCArray.type = PyCArrayType;
        SimpleCData.type = PyCSimpleType;
        PyCFuncPtr.type = PyCFuncPtrType;

        boolean assertionsEnabled = false;
        assert (assertionsEnabled = true) == true;
        HashSet<String> set = assertionsEnabled ? new HashSet<>() : null;
        for (PythonBuiltinClassType type : VALUES) {
            // check uniqueness
            assert set.add("" + type.moduleName + "." + type.name) : type.name();

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

    /**
     * Checks (called only with assertions enabled) that:
     * <p>
     * {@link PythonBuiltins} classes with some {@code @Slot} annotated nodes declare static final
     * field {@code SLOTS}, which is manually assigned to the generated {@code SLOTS} field from the
     * corresponding generated class.
     * <p>
     * {@link PythonBuiltinClassType#declaredSlots} contains merge of all the slots defined by
     * {@link PythonBuiltins} classes annotated with {@link CoreFunctions#extendClasses()} that
     * contains the {@link PythonBuiltinClassType}.
     * <p>
     * Note: this is all done so that the generated slots code is referenced only from the class
     * that contains the {@link Slot} annotation and that we can initialize the
     * {@link PythonBuiltinClassType#slots} in static ctor and bake the values into native-image
     * even without pre-initialized context.
     */
    static boolean verifySlotsConventions(PythonBuiltins[] allBuiltins) {
        if (TruffleOptions.AOT) {
            return true;
        }
        var typeToBuiltins = new HashMap<PythonBuiltinClassType, List<PythonBuiltins>>();
        for (PythonBuiltins builtin : allBuiltins) {
            boolean hasSlots = hasSlotNodes(builtin);
            Field slotsField = getSlotsField(builtin);
            String slotsGenClassName = builtin.getClass().getName() + "SlotsGen";
            if (!hasSlots) {
                if (slotsField != null && !getSlotsFieldValue(builtin).areEqualTo(TpSlots.createEmpty())) {
                    throw new AssertionError(builtin.getClass().getSimpleName() +
                                    " has SLOTS field, but does not have any @Slot annotated inner classes.");
                }
                continue;
            } else if (slotsField == null) {
                throw new AssertionError(String.format("%s does not have SLOTS field, but contains @Slot annotated inner classes. " +
                                "By convention it should have SLOTS field and other code should use that " +
                                "field to avoid directly referencing the generated class %s.",
                                builtin.getClass().getSimpleName(), slotsGenClassName));
            }
            try {
                Field genSlotsField = Class.forName(slotsGenClassName).getDeclaredField("SLOTS");
                genSlotsField.setAccessible(true);
                assert genSlotsField.get(null) == getSlotsFieldValue(builtin) : String.format(
                                "By convention %s.SLOTS field should be initialized to %s.SLOTS field",
                                builtin.getClass().getSimpleName(), slotsGenClassName);
            } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
                throw new AssertionError("Cannot access " + slotsGenClassName);
            }
            CoreFunctions coreFunctions = builtin.getClass().getAnnotation(CoreFunctions.class);
            for (PythonBuiltinClassType extendedClass : coreFunctions.extendClasses()) {
                typeToBuiltins.computeIfAbsent(extendedClass, key -> new ArrayList<>()).add(builtin);
            }
        }
        for (var typeAndBuiltins : typeToBuiltins.entrySet()) {
            PythonBuiltinClassType type = typeAndBuiltins.getKey();
            List<PythonBuiltins> builtins = typeAndBuiltins.getValue();
            assert !builtins.isEmpty() : "No PythonBuiltins for type " + type;
            // One @CoreFunction for type => SLOTS must be identical to PBCT.declaredSlots
            if (builtins.size() == 1) {
                assert getSlotsFieldValue(builtins.get(0)) == type.declaredSlots;
            } else {
                // Multiple @CoreFunctions => PBCT.declaredSlots must be equal to their merge
                TpSlots.Builder builder = TpSlots.newBuilder();
                for (PythonBuiltins builtin : builtins) {
                    TpSlots slots = getSlotsFieldValue(builtin);
                    if (slots != null) {
                        builder.overrideIgnoreGroups(slots);
                    }
                }
                assert type.declaredSlots.areEqualTo(builder.build()) : String.format("%s.declaredSlots are not equal to the merge of SLOTS " +
                                "fields of all @CoreFunction(extendsClasses = ...%s...) annotated PythonBuiltins: %s",
                                type.name(), type.name(), builtins.stream().map(x -> x.getClass().getSimpleName()).collect(Collectors.joining(", ")));
            }
        }
        return true;
    }

    private static boolean hasSlotNodes(PythonBuiltins builtin) {
        if (builtin.getClass().getAnnotation(HashNotImplemented.class) != null) {
            return true;
        }
        for (Class<?> innerClass : builtin.getClass().getDeclaredClasses()) {
            if (innerClass.getDeclaredAnnotationsByType(Slot.class).length > 0) {
                return true;
            }
        }
        return false;
    }

    private static Field getSlotsField(PythonBuiltins builtin) {
        try {
            return builtin.getClass().getDeclaredField("SLOTS");
        } catch (NoSuchFieldException ignore) {
            return null;
        }
    }

    private static TpSlots getSlotsFieldValue(PythonBuiltins builtin) {
        try {
            Field slotsField = getSlotsField(builtin);
            return slotsField == null ? null : (TpSlots) slotsField.get(builtin);
        } catch (IllegalAccessException ignore) {
            throw new AssertionError("Cannot access SLOTS field of " + builtin.getClass().getSimpleName());
        }
    }

    // Proxy InteropLibrary messages to the PythonBuiltinClass
    @ExportMessage
    public Object send(Message message, Object[] args,
                    @CachedLibrary(limit = "1") ReflectionLibrary lib) throws Exception {
        return lib.send(PythonContext.get(lib).lookupType(this), message, args);
    }
}
