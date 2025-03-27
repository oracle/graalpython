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
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.FOREIGNNUMBER_M_FLAGS;
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
import com.oracle.graal.python.builtins.modules.lsprof.ProfilerBuiltins;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.modules.ast.AstBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2CompressorBuiltins;
import com.oracle.graal.python.builtins.modules.bz2.BZ2DecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalDecoderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalEncoderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamReaderBuiltins;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamWriterBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVReaderBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeSequenceBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCStructTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.SimpleCDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructureBuiltins;
import com.oracle.graal.python.builtins.modules.functools.KeyWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.LruCacheWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.PartialBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.HashObjectBuiltins;
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
import com.oracle.graal.python.builtins.modules.lzma.LZMACompressorBuiltins;
import com.oracle.graal.python.builtins.modules.lzma.LZMADecompressorBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.PicklerBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.UnpicklerBuiltins;
import com.oracle.graal.python.builtins.objects.NoneBuiltins;
import com.oracle.graal.python.builtins.objects.NotImplementedBuiltins;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGenSendBuiltins;
import com.oracle.graal.python.builtins.objects.asyncio.AsyncGenThrowBuiltins;
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
import com.oracle.graal.python.builtins.objects.contextvars.TokenBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeBuiltins;
import com.oracle.graal.python.builtins.objects.deque.DequeIterBuiltins;
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
import com.oracle.graal.python.builtins.objects.foreign.ForeignIterableBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignNumberBuiltins;
import com.oracle.graal.python.builtins.objects.foreign.ForeignObjectBuiltins;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins;
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
import com.oracle.graal.python.builtins.objects.iterator.PZipBuiltins;
import com.oracle.graal.python.builtins.objects.iterator.SentinelIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.AccumulateBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.ChainBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CombinationsBuiltins;
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
import com.oracle.graal.python.builtins.objects.range.RangeBuiltins;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.reversed.ReversedBuiltins;
import com.oracle.graal.python.builtins.objects.set.BaseSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltins;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins;
import com.oracle.graal.python.builtins.objects.slice.SliceBuiltins;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.struct.StructUnpackIteratorBuiltins;
import com.oracle.graal.python.builtins.objects.superobject.SuperBuiltins;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.tokenize.TokenizerIterBuiltins;
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

    PythonObject("object", null, new Builder().publishInModule(J_BUILTINS).basetype().slots(ObjectBuiltins.SLOTS)),
    PythonClass("type", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(TypeBuiltins.SLOTS).methodsFlags(TYPE_M_FLAGS)),
    PArray("array", PythonObject, new Builder().publishInModule("array").basetype().slots(ArrayBuiltins.SLOTS).methodsFlags(ARRAY_M_FLAGS)),
    PArrayIterator("arrayiterator", PythonObject, new Builder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PIterator("iterator", PythonObject, new Builder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    PBuiltinFunction("method_descriptor", PythonObject, new Builder().disallowInstantiation().slots(MethodDescriptorBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinFunctionOrMethod("builtin_function_or_method", PythonObject, new Builder().slots(TpSlots.merge(AbstractMethodBuiltins.SLOTS, BuiltinFunctionOrMethodBuiltins.SLOTS))),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    WrapperDescriptor(J_WRAPPER_DESCRIPTOR, PythonObject, new Builder().disallowInstantiation().slots(WrapperDescriptorBuiltins.SLOTS)),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    MethodWrapper("method-wrapper", PythonObject, new Builder().slots(TpSlots.merge(AbstractMethodBuiltins.SLOTS, MethodWrapperBuiltins.SLOTS))),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinMethod("builtin_method", PBuiltinFunctionOrMethod, new Builder()),
    PBuiltinClassMethod("classmethod_descriptor", PythonObject, new Builder().slots(TpSlots.merge(ClassmethodCommonBuiltins.SLOTS, BuiltinClassmethodBuiltins.SLOTS))),
    GetSetDescriptor("getset_descriptor", PythonObject, new Builder().disallowInstantiation().slots(GetSetDescriptorTypeBuiltins.SLOTS)),
    MemberDescriptor(J_MEMBER_DESCRIPTOR, PythonObject, new Builder().disallowInstantiation().slots(MemberDescriptorBuiltins.SLOTS)),
    PByteArray(
                    "bytearray",
                    PythonObject,
                    new Builder().publishInModule(J_BUILTINS).basetype().slots(TpSlots.merge(BytesCommonBuiltins.SLOTS, ByteArrayBuiltins.SLOTS)).methodsFlags(BYTE_ARRAY_M_FLAGS)),
    PBytes("bytes", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(TpSlots.merge(BytesCommonBuiltins.SLOTS, BytesBuiltins.SLOTS)).methodsFlags(BYTES_M_FLAGS)),
    PCell("cell", PythonObject, new Builder().slots(CellBuiltins.SLOTS)),
    PSimpleNamespace("SimpleNamespace", PythonObject, new Builder().publishInModule("types").basetype().addDict().slots(SimpleNamespaceBuiltins.SLOTS)),
    PKeyWrapper("KeyWrapper", PythonObject, new Builder().moduleName("functools").publishInModule("_functools").disallowInstantiation().slots(KeyWrapperBuiltins.SLOTS)),
    PPartial(J_PARTIAL, PythonObject, new Builder().moduleName("functools").publishInModule("_functools").basetype().addDict().slots(PartialBuiltins.SLOTS)),
    PLruListElem("_lru_list_elem", PythonObject, new Builder().publishInModule("functools").disallowInstantiation()),
    PLruCacheWrapper(J_LRU_CACHE_WRAPPER, PythonObject, new Builder().moduleName("functools").publishInModule("_functools").basetype().addDict().slots(LruCacheWrapperBuiltins.SLOTS)),
    PDeque(J_DEQUE, PythonObject, new Builder().publishInModule("_collections").basetype().slots(DequeBuiltins.SLOTS).methodsFlags(DEQUE_M_FLAGS)),
    PTupleGetter(J_TUPLE_GETTER, PythonObject, new Builder().publishInModule("_collections").basetype().slots(TupleGetterBuiltins.SLOTS)),
    PDequeIter(J_DEQUE_ITER, PythonObject, new Builder().publishInModule("_collections").slots(DequeIterBuiltins.SLOTS)),
    PDequeRevIter(J_DEQUE_REV_ITER, PythonObject, new Builder().publishInModule("_collections").slots(DequeIterBuiltins.SLOTS)),
    PComplex("complex", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(ComplexBuiltins.SLOTS).methodsFlags(COMPLEX_M_FLAGS)),
    PDict("dict", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(TpSlots.merge(DictBuiltins.SLOTS, DictReprBuiltin.SLOTS)).methodsFlags(DICT_M_FLAGS)),
    PDefaultDict(J_DEFAULTDICT, PDict, new Builder().moduleName("collections").publishInModule("_collections").basetype().slots(DefaultDictBuiltins.SLOTS).methodsFlags(DEFAULTDICT_M_FLAGS)),
    POrderedDict(J_ORDERED_DICT, PDict, new Builder().publishInModule("_collections").basetype().addDict().slots(OrderedDictBuiltins.SLOTS).methodsFlags(DICT_M_FLAGS)),
    PDictItemIterator(J_DICT_ITEMITERATOR, PythonObject, new Builder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PDictReverseItemIterator(J_DICT_REVERSE_ITEMITERATOR, PythonObject, new Builder().slots(IteratorBuiltins.SLOTS)),
    PDictItemsView(J_DICT_ITEMS, PythonObject, new Builder().disallowInstantiation().slots(TpSlots.merge(DictViewBuiltins.SLOTS, DictReprBuiltin.SLOTS)).methodsFlags(DICTITEMSVIEW_M_FLAGS)),
    PDictKeyIterator(J_DICT_KEYITERATOR, PythonObject, new Builder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PDictReverseKeyIterator(J_DICT_REVERSE_KEYITERATOR, PythonObject, new Builder().slots(IteratorBuiltins.SLOTS)),
    PDictKeysView(J_DICT_KEYS, PythonObject, new Builder().disallowInstantiation().slots(TpSlots.merge(DictViewBuiltins.SLOTS, DictReprBuiltin.SLOTS)).methodsFlags(DICTKEYSVIEW_M_FLAGS)),
    PDictValueIterator(J_DICT_VALUEITERATOR, PythonObject, new Builder().disallowInstantiation().slots(IteratorBuiltins.SLOTS)),
    PDictReverseValueIterator(J_DICT_REVERSE_VALUEITERATOR, PythonObject, new Builder().slots(IteratorBuiltins.SLOTS)),
    PDictValuesView(J_DICT_VALUES, PythonObject, new Builder().disallowInstantiation().slots(TpSlots.merge(DictValuesBuiltins.SLOTS, DictReprBuiltin.SLOTS)).methodsFlags(DICTVALUESVIEW_M_FLAGS)),
    POrderedDictKeys("odict_keys", PDictKeysView, new Builder().slots(OrderedDictKeysBuiltins.SLOTS).methodsFlags(DICTKEYSVIEW_M_FLAGS)),
    POrderedDictValues("odict_values", PDictValuesView, new Builder().slots(OrderedDictValuesBuiltins.SLOTS).methodsFlags(DICTVALUESVIEW_M_FLAGS)),
    POrderedDictItems("odict_items", PDictItemsView, new Builder().slots(OrderedDictItemsBuiltins.SLOTS).methodsFlags(DICTITEMSVIEW_M_FLAGS)),
    POrderedDictIterator("odict_iterator", PythonObject, new Builder().slots(OrderedDictIteratorBuiltins.SLOTS)),
    PEllipsis("ellipsis", PythonObject, new Builder().slots(EllipsisBuiltins.SLOTS)),
    PEnumerate("enumerate", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(EnumerateBuiltins.SLOTS)),
    PMap("map", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(MapBuiltins.SLOTS)),
    PFloat("float", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(FloatBuiltins.SLOTS).methodsFlags(FLOAT_M_FLAGS)),
    PFrame("frame", PythonObject, new Builder().slots(FrameBuiltins.SLOTS)),
    PFrozenSet("frozenset", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(TpSlots.merge(BaseSetBuiltins.SLOTS, FrozenSetBuiltins.SLOTS)).methodsFlags(FROZENSET_M_FLAGS)),
    PFunction("function", PythonObject, new Builder().addDict().slots(FunctionBuiltins.SLOTS)),
    PGenerator("generator", PythonObject, new Builder().disallowInstantiation().slots(GeneratorBuiltins.SLOTS).methodsFlags(GENERATOR_M_FLAGS)),
    PCoroutine("coroutine", PythonObject, new Builder().slots(CoroutineBuiltins.SLOTS).methodsFlags(COROUTINE_M_FLAGS)),
    PCoroutineWrapper("coroutine_wrapper", PythonObject, new Builder().slots(CoroutineWrapperBuiltins.SLOTS)),
    PAsyncGenerator("async_generator", PythonObject, new Builder().methodsFlags(ASYNC_GENERATOR_M_FLAGS)),
    PInt("int", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(IntBuiltins.SLOTS).methodsFlags(INT_M_FLAGS)),
    Boolean("bool", PInt, new Builder().publishInModule(J_BUILTINS).slots(BoolBuiltins.SLOTS).methodsFlags(BOOLEAN_M_FLAGS)),
    PList("list", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(ListBuiltins.SLOTS).methodsFlags(LIST_M_FLAGS)),
    PMappingproxy("mappingproxy", PythonObject, new Builder().slots(MappingproxyBuiltins.SLOTS).methodsFlags(MAPPINGPROXY_M_FLAGS)),
    PMemoryView("memoryview", PythonObject, new Builder().publishInModule(J_BUILTINS).slots(MemoryViewBuiltins.SLOTS).methodsFlags(MEMORYVIEW_M_FLAGS)),
    PAsyncGenASend("async_generator_asend", PythonObject, new Builder().slots(AsyncGenSendBuiltins.SLOTS).methodsFlags(ASYNC_GENERATOR_ASEND_M_FLAGS)),
    PAsyncGenAThrow("async_generator_athrow", PythonObject, new Builder().slots(AsyncGenThrowBuiltins.SLOTS).methodsFlags(ASYNC_GENERATOR_ATHROW_M_FLAGS)),
    PAsyncGenAWrappedValue("async_generator_wrapped_value", PythonObject, new Builder()),
    PMethod("method", PythonObject, new Builder().slots(TpSlots.merge(AbstractMethodBuiltins.SLOTS, MethodBuiltins.SLOTS))),
    PMMap("mmap", PythonObject, new Builder().publishInModule("mmap").basetype().slots(MMapBuiltins.SLOTS).methodsFlags(MMAP_M_FLAGS)),
    PNone("NoneType", PythonObject, new Builder().slots(NoneBuiltins.SLOTS).methodsFlags(NONE_M_FLAGS)),
    PNotImplemented("NotImplementedType", PythonObject, new Builder().slots(NotImplementedBuiltins.SLOTS)),
    PProperty(J_PROPERTY, PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(PropertyBuiltins.SLOTS)),
    PSimpleQueue(J_SIMPLE_QUEUE, PythonObject, new Builder().publishInModule("_queue").basetype()),
    PRandom("Random", PythonObject, new Builder().publishInModule("_random").basetype()),
    PRange("range", PythonObject, new Builder().publishInModule(J_BUILTINS).slots(RangeBuiltins.SLOTS).methodsFlags(RANGE_M_FLAGS)),
    PReferenceType("ReferenceType", PythonObject, new Builder().publishInModule("_weakref").basetype().slots(ReferenceTypeBuiltins.SLOTS)),
    PSentinelIterator("callable_iterator", PythonObject, new Builder().disallowInstantiation().slots(SentinelIteratorBuiltins.SLOTS)),
    PReverseIterator("reversed", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(ReversedBuiltins.SLOTS)),
    PSet("set", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(TpSlots.merge(BaseSetBuiltins.SLOTS, SetBuiltins.SLOTS)).methodsFlags(SET_M_FLAGS)),
    PSlice("slice", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(SliceBuiltins.SLOTS)),
    PString("str", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(StringBuiltins.SLOTS).methodsFlags(STRING_M_FLAGS)),
    PTraceback("traceback", PythonObject, new Builder().basetype()),
    PTuple("tuple", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(TupleBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PythonModule("module", PythonObject, new Builder().basetype().addDict().slots(ModuleBuiltins.SLOTS)),
    PythonModuleDef("moduledef", PythonObject, new Builder()),
    Super("super", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(SuperBuiltins.SLOTS)),
    PCode("code", PythonObject, new Builder().slots(CodeBuiltins.SLOTS)),
    PGenericAlias("GenericAlias", PythonObject, new Builder().publishInModule(J_TYPES).basetype().slots(GenericAliasBuiltins.SLOTS).methodsFlags(GENERIC_ALIAS_M_FLAGS)),
    PGenericAliasIterator("generic_alias_iterator", PythonObject, new Builder().slots(GenericAliasIteratorBuiltins.SLOTS)),
    PUnionType("UnionType", PythonObject, new Builder().publishInModule(J_TYPES).slots(UnionTypeBuiltins.SLOTS).methodsFlags(UNION_TYPE_M_FLAGS)),
    PZip("zip", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().slots(PZipBuiltins.SLOTS)),
    PThread("start_new_thread", PythonObject, new Builder().publishInModule(J__THREAD).basetype()),
    PThreadLocal("_local", PythonObject, new Builder().publishInModule(J__THREAD).basetype().slots(ThreadLocalBuiltins.SLOTS)),
    PLock("LockType", PythonObject, new Builder().publishInModule(J__THREAD).disallowInstantiation().slots(LockBuiltins.SLOTS)),
    PRLock("RLock", PythonObject, new Builder().publishInModule(J__THREAD).basetype().slots(LockBuiltins.SLOTS)),
    PSemLock("SemLock", PythonObject, new Builder().publishInModule("_multiprocessing").basetype()),
    PGraalPySemLock("SemLock", PythonObject, new Builder().publishInModule("_multiprocessing_graalpy").basetype()),
    PSocket("socket", PythonObject, new Builder().publishInModule(J__SOCKET).basetype().slots(SocketBuiltins.SLOTS)),
    PStaticmethod("staticmethod", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(StaticmethodBuiltins.SLOTS)),
    PClassmethod("classmethod", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(TpSlots.merge(ClassmethodCommonBuiltins.SLOTS, ClassmethodBuiltins.SLOTS))),
    PInstancemethod("instancemethod", PythonObject, new Builder().basetype().addDict().slots(InstancemethodBuiltins.SLOTS)),
    PScandirIterator("ScandirIterator", PythonObject, new Builder().moduleName(J_POSIX).disallowInstantiation().slots(ScandirIteratorBuiltins.SLOTS)),
    PDirEntry("DirEntry", PythonObject, new Builder().publishInModule(J_POSIX).disallowInstantiation().slots(DirEntryBuiltins.SLOTS)),
    LsprofProfiler("Profiler", PythonObject, new Builder().publishInModule("_lsprof").basetype().slots(ProfilerBuiltins.SLOTS)),
    PStruct("Struct", PythonObject, new Builder().publishInModule(J__STRUCT).basetype()),
    PStructUnpackIterator("unpack_iterator", PythonObject, new Builder().publishInModule(J__STRUCT).basetype().slots(StructUnpackIteratorBuiltins.SLOTS)),
    Pickler("Pickler", PythonObject, new Builder().publishInModule("_pickle").basetype().slots(PicklerBuiltins.SLOTS)),
    PicklerMemoProxy("PicklerMemoProxy", PythonObject, new Builder().publishInModule("_pickle").basetype()),
    UnpicklerMemoProxy("UnpicklerMemoProxy", PythonObject, new Builder().publishInModule("_pickle").basetype()),
    Unpickler("Unpickler", PythonObject, new Builder().publishInModule("_pickle").basetype().slots(UnpicklerBuiltins.SLOTS)),
    PickleBuffer("PickleBuffer", PythonObject, new Builder().publishInModule("_pickle").basetype()),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", PythonObject, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(BaseExceptionBuiltins.SLOTS)),
    PBaseExceptionGroup("BaseExceptionGroup", PBaseException, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(BaseExceptionGroupBuiltins.SLOTS)),
    SystemExit("SystemExit", PBaseException, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(SystemExitBuiltins.SLOTS)),
    KeyboardInterrupt("KeyboardInterrupt", PBaseException, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    GeneratorExit("GeneratorExit", PBaseException, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    Exception("Exception", PBaseException, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ReferenceError("ReferenceError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    RuntimeError("RuntimeError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    NotImplementedError("NotImplementedError", RuntimeError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    SyntaxError("SyntaxError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(SyntaxErrorBuiltins.SLOTS)),
    IndentationError("IndentationError", SyntaxError, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(SyntaxErrorBuiltins.SLOTS)),
    TabError("TabError", IndentationError, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(SyntaxErrorBuiltins.SLOTS)),
    SystemError("SystemError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    TypeError("TypeError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ValueError("ValueError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    StopIteration("StopIteration", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(StopIterationBuiltins.SLOTS)),
    StopAsyncIteration("StopAsyncIteration", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ArithmeticError("ArithmeticError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    FloatingPointError("FloatingPointError", ArithmeticError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    OverflowError("OverflowError", ArithmeticError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ZeroDivisionError("ZeroDivisionError", ArithmeticError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    AssertionError("AssertionError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    AttributeError("AttributeError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    BufferError("BufferError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    EOFError("EOFError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ImportError("ImportError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(ImportErrorBuiltins.SLOTS)),
    ModuleNotFoundError("ModuleNotFoundError", ImportError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    LookupError("LookupError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    IndexError("IndexError", LookupError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    KeyError("KeyError", LookupError, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(KeyErrorBuiltins.SLOTS)),
    MemoryError("MemoryError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    NameError("NameError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    UnboundLocalError("UnboundLocalError", NameError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    OSError("OSError", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(OsErrorBuiltins.SLOTS)),
    BlockingIOError("BlockingIOError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ChildProcessError("ChildProcessError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionError("ConnectionError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    BrokenPipeError("BrokenPipeError", ConnectionError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionAbortedError("ConnectionAbortedError", ConnectionError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionRefusedError("ConnectionRefusedError", ConnectionError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ConnectionResetError("ConnectionResetError", ConnectionError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    FileExistsError("FileExistsError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    FileNotFoundError("FileNotFoundError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    InterruptedError("InterruptedError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    IsADirectoryError("IsADirectoryError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    NotADirectoryError("NotADirectoryError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    PermissionError("PermissionError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ProcessLookupError("ProcessLookupError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    TimeoutError("TimeoutError", OSError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ZLibError("error", Exception, new Builder().publishInModule("zlib").basetype().addDict()),
    CSVError("Error", Exception, new Builder().publishInModule("_csv").basetype().addDict()),
    LZMAError("LZMAError", Exception, new Builder().publishInModule("_lzma").basetype().addDict()),
    StructError("StructError", Exception, new Builder().publishInModule(J__STRUCT).basetype().addDict()),
    PickleError("PickleError", Exception, new Builder().publishInModule("_pickle").basetype().addDict()),
    PicklingError("PicklingError", PickleError, new Builder().publishInModule("_pickle").basetype().addDict()),
    UnpicklingError("UnpicklingError", PickleError, new Builder().publishInModule("_pickle").basetype().addDict()),
    SocketGAIError("gaierror", OSError, new Builder().publishInModule(J__SOCKET).basetype().addDict()),
    SocketHError("herror", OSError, new Builder().publishInModule(J__SOCKET).basetype().addDict()),
    BinasciiError("Error", ValueError, new Builder().publishInModule("binascii").basetype().addDict()),
    BinasciiIncomplete("Incomplete", Exception, new Builder().publishInModule("binascii").basetype().addDict()),
    SSLError("SSLError", OSError, new Builder().publishInModule(J__SSL).basetype().addDict().slots(SSLErrorBuiltins.SLOTS)),
    SSLZeroReturnError("SSLZeroReturnError", SSLError, new Builder().publishInModule(J__SSL).basetype().addDict()),
    SSLWantReadError("SSLWantReadError", SSLError, new Builder().publishInModule(J__SSL).basetype().addDict()),
    SSLWantWriteError("SSLWantWriteError", SSLError, new Builder().publishInModule(J__SSL).basetype().addDict()),
    SSLSyscallError("SSLSyscallError", SSLError, new Builder().publishInModule(J__SSL).basetype().addDict()),
    SSLEOFError("SSLEOFError", SSLError, new Builder().publishInModule(J__SSL).basetype().addDict()),
    SSLCertVerificationError("SSLCertVerificationError", SSLError, new Builder().publishInModule(J__SSL).basetype().addDict()),

    // todo: all OS errors

    UnicodeError("UnicodeError", ValueError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    UnicodeDecodeError("UnicodeDecodeError", UnicodeError, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(UnicodeDecodeErrorBuiltins.SLOTS)),
    UnicodeEncodeError("UnicodeEncodeError", UnicodeError, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(UnicodeEncodeErrorBuiltins.SLOTS)),
    UnicodeTranslateError("UnicodeTranslateError", UnicodeError, new Builder().publishInModule(J_BUILTINS).basetype().addDict().slots(UnicodeTranslateErrorBuiltins.SLOTS)),
    RecursionError("RecursionError", RuntimeError, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),

    /*
     * _io.UnsupportedOperation inherits from ValueError and OSError done currently within
     * IOModuleBuiltins class
     */
    IOUnsupportedOperation("UnsupportedOperation", OSError, new Builder().publishInModule("io").basetype().addDict()),

    Empty("Empty", Exception, new Builder().publishInModule("_queue").basetype().addDict()),

    UnsupportedMessage("UnsupportedMessage", Exception, new Builder().publishInModule(J_POLYGLOT).basetype().addDict()),

    // warnings
    Warning("Warning", Exception, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    BytesWarning("BytesWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    DeprecationWarning("DeprecationWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    FutureWarning("FutureWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ImportWarning("ImportWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    PendingDeprecationWarning("PendingDeprecationWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    ResourceWarning("ResourceWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    RuntimeWarning("RuntimeWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    SyntaxWarning("SyntaxWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    UnicodeWarning("UnicodeWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    UserWarning("UserWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),
    EncodingWarning("EncodingWarning", Warning, new Builder().publishInModule(J_BUILTINS).basetype().addDict()),

    // Foreign
    ForeignObject("ForeignObject", PythonObject, new Builder().publishInModule(J_POLYGLOT).basetype().addDict().slots(ForeignObjectBuiltins.SLOTS)),
    ForeignNumber("ForeignNumber", ForeignObject, new Builder().publishInModule(J_POLYGLOT).basetype().addDict().slots(ForeignNumberBuiltins.SLOTS).methodsFlags(FOREIGNNUMBER_M_FLAGS)),
    ForeignBoolean("ForeignBoolean", ForeignNumber, new Builder().publishInModule(J_POLYGLOT).basetype().addDict().slots(ForeignBooleanBuiltins.SLOTS).methodsFlags(FOREIGNNUMBER_M_FLAGS)),
    ForeignAbstractClass("ForeignAbstractClass", ForeignObject, new Builder().publishInModule(J_POLYGLOT).basetype().addDict()),
    ForeignExecutable("ForeignExecutable", ForeignObject, new Builder().publishInModule(J_POLYGLOT).basetype().addDict()),
    ForeignInstantiable("ForeignInstantiable", ForeignObject, new Builder().publishInModule(J_POLYGLOT).basetype().addDict()),
    ForeignIterable("ForeignIterable", ForeignObject, new Builder().publishInModule(J_POLYGLOT).basetype().addDict().slots(ForeignIterableBuiltins.SLOTS)),

    // bz2
    BZ2Compressor("BZ2Compressor", PythonObject, new Builder().publishInModule("_bz2").basetype().slots(BZ2CompressorBuiltins.SLOTS)),
    BZ2Decompressor("BZ2Decompressor", PythonObject, new Builder().publishInModule("_bz2").basetype().slots(BZ2DecompressorBuiltins.SLOTS)),

    // lzma
    PLZMACompressor("LZMACompressor", PythonObject, new Builder().publishInModule("_lzma").basetype().slots(LZMACompressorBuiltins.SLOTS)),
    PLZMADecompressor("LZMADecompressor", PythonObject, new Builder().publishInModule("_lzma").basetype().slots(LZMADecompressorBuiltins.SLOTS)),

    // zlib
    ZlibCompress("Compress", PythonObject, new Builder().publishInModule("zlib").disallowInstantiation()),
    ZlibDecompress("Decompress", PythonObject, new Builder().publishInModule("zlib").disallowInstantiation()),

    // io
    PIOBase("_IOBase", PythonObject, new Builder().publishInModule("_io").basetype().addDict().slots(IOBaseBuiltins.SLOTS)),
    PRawIOBase("_RawIOBase", PIOBase, new Builder().publishInModule("_io").basetype().slots(IOBaseBuiltins.SLOTS)),
    PTextIOBase("_TextIOBase", PIOBase, new Builder().publishInModule("_io").basetype().slots(IOBaseBuiltins.SLOTS)),
    PBufferedIOBase("_BufferedIOBase", PIOBase, new Builder().publishInModule("_io").basetype().slots(IOBaseBuiltins.SLOTS)),
    PBufferedReader(
                    "BufferedReader",
                    PBufferedIOBase,
                    new Builder().publishInModule("_io").basetype().addDict().slots(TpSlots.merge(BufferedReaderMixinBuiltins.SLOTS, BufferedIOMixinBuiltins.SLOTS, BufferedReaderBuiltins.SLOTS))),
    PBufferedWriter("BufferedWriter", PBufferedIOBase, new Builder().publishInModule("_io").basetype().addDict().slots(TpSlots.merge(BufferedIOMixinBuiltins.SLOTS, BufferedWriterBuiltins.SLOTS))),
    PBufferedRWPair("BufferedRWPair", PBufferedIOBase, new Builder().publishInModule("_io").basetype().addDict().slots(BufferedRWPairBuiltins.SLOTS)),
    PBufferedRandom(
                    "BufferedRandom",
                    PBufferedIOBase,
                    new Builder().publishInModule("_io").basetype().addDict().slots(TpSlots.merge(BufferedReaderMixinBuiltins.SLOTS, BufferedIOMixinBuiltins.SLOTS, BufferedRandomBuiltins.SLOTS))),
    PFileIO("FileIO", PRawIOBase, new Builder().publishInModule("_io").basetype().addDict().slots(FileIOBuiltins.SLOTS)),
    PTextIOWrapper("TextIOWrapper", PTextIOBase, new Builder().publishInModule("_io").basetype().addDict().slots(TextIOWrapperBuiltins.SLOTS)),
    PIncrementalNewlineDecoder("IncrementalNewlineDecoder", PythonObject, new Builder().publishInModule("_io").basetype().slots(IncrementalNewlineDecoderBuiltins.SLOTS)),
    PStringIO("StringIO", PTextIOBase, new Builder().publishInModule("_io").basetype().addDict().slots(StringIOBuiltins.SLOTS)),
    PBytesIO("BytesIO", PBufferedIOBase, new Builder().publishInModule("_io").basetype().addDict().slots(BytesIOBuiltins.SLOTS)),
    PBytesIOBuf("_BytesIOBuffer", PythonObject, new Builder().moduleName("_io").basetype()),

    PStatResult("stat_result", PTuple, new Builder().publishInModule("os").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PStatvfsResult("statvfs_result", PTuple, new Builder().publishInModule("os").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PTerminalSize("terminal_size", PTuple, new Builder().publishInModule("os").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PUnameResult("uname_result", PTuple, new Builder().publishInModule(J_POSIX).slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PStructTime("struct_time", PTuple, new Builder().publishInModule("time").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PProfilerEntry("profiler_entry", PTuple, new Builder().publishInModule("_lsprof").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PProfilerSubentry("profiler_subentry", PTuple, new Builder().publishInModule("_lsprof").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PStructPasswd("struct_passwd", PTuple, new Builder().publishInModule("pwd").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PStructRusage("struct_rusage", PTuple, new Builder().publishInModule("resource").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PVersionInfo("version_info", PTuple, new Builder().publishInModule("sys").disallowInstantiation().slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PWindowsVersion("windowsversion", PTuple, new Builder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PFlags("flags", PTuple, new Builder().publishInModule("sys").disallowInstantiation().slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PFloatInfo("float_info", PTuple, new Builder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PIntInfo("int_info", PTuple, new Builder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PHashInfo("hash_info", PTuple, new Builder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PThreadInfo("thread_info", PTuple, new Builder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),
    PUnraisableHookArgs("UnraisableHookArgs", PTuple, new Builder().publishInModule("sys").slots(StructSequenceBuiltins.SLOTS).methodsFlags(TUPLE_M_FLAGS)),

    PSSLSession("SSLSession", PythonObject, new Builder().publishInModule(J__SSL).disallowInstantiation()),
    PSSLContext("_SSLContext", PythonObject, new Builder().publishInModule(J__SSL).basetype()),
    PSSLSocket("_SSLSocket", PythonObject, new Builder().publishInModule(J__SSL).basetype()),
    PMemoryBIO("MemoryBIO", PythonObject, new Builder().publishInModule(J__SSL).basetype()),

    // itertools
    PTee("_tee", PythonObject, new Builder().publishInModule("itertools").slots(TeeBuiltins.SLOTS)),
    PTeeDataObject("_tee_dataobject", PythonObject, new Builder().publishInModule("itertools").slots(TeeDataObjectBuiltins.SLOTS)),
    PAccumulate("accumulate", PythonObject, new Builder().publishInModule("itertools").basetype().slots(AccumulateBuiltins.SLOTS)),
    PCombinations("combinations", PythonObject, new Builder().publishInModule("itertools").basetype().slots(CombinationsBuiltins.SLOTS)),
    PCombinationsWithReplacement("combinations_with_replacement", PythonObject, new Builder().publishInModule("itertools").basetype().slots(CombinationsBuiltins.SLOTS)),
    PCompress("compress", PythonObject, new Builder().publishInModule("itertools").basetype().slots(CompressBuiltins.SLOTS)),
    PCycle("cycle", PythonObject, new Builder().publishInModule("itertools").basetype().slots(CycleBuiltins.SLOTS)),
    PDropwhile("dropwhile", PythonObject, new Builder().publishInModule("itertools").basetype().slots(DropwhileBuiltins.SLOTS)),
    PFilterfalse("filterfalse", PythonObject, new Builder().publishInModule("itertools").basetype().slots(FilterfalseBuiltins.SLOTS)),
    PGroupBy("groupby", PythonObject, new Builder().publishInModule("itertools").basetype().slots(GroupByBuiltins.SLOTS)),
    PGrouper("grouper", PythonObject, new Builder().publishInModule("itertools").slots(GrouperBuiltins.SLOTS)),
    PPairwise("pairwise", PythonObject, new Builder().publishInModule("itertools").basetype().slots(PairwiseBuiltins.SLOTS)),
    PPermutations("permutations", PythonObject, new Builder().publishInModule("itertools").basetype().slots(PermutationsBuiltins.SLOTS)),
    PProduct("product", PythonObject, new Builder().publishInModule("itertools").basetype().slots(ProductBuiltins.SLOTS)),
    PRepeat("repeat", PythonObject, new Builder().publishInModule("itertools").basetype().slots(RepeatBuiltins.SLOTS)),
    PChain("chain", PythonObject, new Builder().publishInModule("itertools").basetype().slots(ChainBuiltins.SLOTS)),
    PCount("count", PythonObject, new Builder().publishInModule("itertools").basetype().slots(CountBuiltins.SLOTS)),
    PIslice("islice", PythonObject, new Builder().publishInModule("itertools").basetype().slots(IsliceBuiltins.SLOTS)),
    PStarmap("starmap", PythonObject, new Builder().publishInModule("itertools").basetype().slots(StarmapBuiltins.SLOTS)),
    PTakewhile("takewhile", PythonObject, new Builder().publishInModule("itertools").basetype().slots(TakewhileBuiltins.SLOTS)),
    PZipLongest("zip_longest", PythonObject, new Builder().publishInModule("itertools").basetype().slots(ZipLongestBuiltins.SLOTS)),

    // json
    JSONScanner("Scanner", PythonObject, new Builder().publishInModule("_json").basetype()),
    JSONEncoder("Encoder", PythonObject, new Builder().publishInModule("_json").basetype()),

    // csv
    CSVDialect("Dialect", PythonObject, new Builder().publishInModule("_csv").basetype()),
    CSVReader("Reader", PythonObject, new Builder().publishInModule("_csv").basetype().disallowInstantiation().slots(CSVReaderBuiltins.SLOTS)),
    CSVWriter("Writer", PythonObject, new Builder().publishInModule("_csv").basetype().disallowInstantiation()),

    // codecs
    PEncodingMap("EncodingMap", PythonObject, new Builder().disallowInstantiation()),

    // hashlib
    MD5Type("md5", PythonObject, new Builder().publishInModule("_md5").basetype().disallowInstantiation()),
    SHA1Type("sha1", PythonObject, new Builder().publishInModule("_sha1").basetype().disallowInstantiation()),
    SHA224Type("sha224", PythonObject, new Builder().publishInModule("_sha256").basetype().disallowInstantiation()),
    SHA256Type("sha256", PythonObject, new Builder().publishInModule("_sha256").basetype().disallowInstantiation()),
    SHA384Type("sha384", PythonObject, new Builder().publishInModule("_sha512").basetype().disallowInstantiation()),
    SHA512Type("sha512", PythonObject, new Builder().publishInModule("_sha512").basetype().disallowInstantiation()),
    Sha3SHA224Type("sha3_224", PythonObject, new Builder().publishInModule("_sha3").basetype()),
    Sha3SHA256Type("sha3_256", PythonObject, new Builder().publishInModule("_sha3").basetype()),
    Sha3SHA384Type("sha3_384", PythonObject, new Builder().publishInModule("_sha3").basetype()),
    Sha3SHA512Type("sha3_512", PythonObject, new Builder().publishInModule("_sha3").basetype()),
    Sha3Shake128Type("shake_128", PythonObject, new Builder().publishInModule("_sha3").basetype()),
    Sha3Shake256Type("shake_256", PythonObject, new Builder().publishInModule("_sha3").basetype()),
    Blake2bType("blake2b", PythonObject, new Builder().publishInModule("_blake2").basetype()),
    Blake2sType("blake2s", PythonObject, new Builder().publishInModule("_blake2").basetype()),
    HashlibHash("HASH", PythonObject, new Builder().publishInModule("_hashlib").basetype().disallowInstantiation().slots(HashObjectBuiltins.SLOTS)),
    HashlibHashXof("HASHXOF", HashlibHash, new Builder().publishInModule("_hashlib").disallowInstantiation()),
    HashlibHmac("HMAC", PythonObject, new Builder().publishInModule("_hashlib").basetype().disallowInstantiation().slots(HashObjectBuiltins.SLOTS)),
    UnsupportedDigestmodError("UnsupportedDigestmodError", ValueError, new Builder().publishInModule("_hashlib").basetype().addDict()),

    // _ast (rest of the classes are not builtin, they are generated in AstModuleBuiltins)
    AST("AST", PythonObject, new Builder().moduleName("ast").publishInModule("_ast").basetype().addDict().slots(AstBuiltins.SLOTS)),

    // _ctype
    CArgObject("CArgObject", PythonObject, new Builder().basetype().slots(CArgObjectBuiltins.SLOTS)),
    CThunkObject("CThunkObject", PythonObject, new Builder().publishInModule(J__CTYPES).basetype()),
    StgDict("StgDict", PDict, new Builder().slots(StgDictBuiltins.SLOTS).methodsFlags(DICT_M_FLAGS)),
    PyCStructType(
                    "PyCStructType",
                    PythonClass,
                    new Builder().publishInModule(J__CTYPES).basetype().slots(TpSlots.merge(CDataTypeSequenceBuiltins.SLOTS, PyCStructTypeBuiltins.SLOTS)).methodsFlags(PYCSTRUCTTYPE_M_FLAGS)),
    UnionType(
                    "UnionType",
                    PythonClass,
                    new Builder().publishInModule(J__CTYPES).basetype().slots(
                                    TpSlots.merge(CDataTypeSequenceBuiltins.SLOTS, com.oracle.graal.python.builtins.modules.ctypes.UnionTypeBuiltins.SLOTS)).methodsFlags(UNIONTYPE_M_FLAGS)),
    PyCPointerType("PyCPointerType", PythonClass, new Builder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS).methodsFlags(PYCPOINTERTYPE_M_FLAGS)),
    PyCArrayType("PyCArrayType", PythonClass, new Builder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS).methodsFlags(PYCARRAYTYPE_M_FLAGS)),
    PyCSimpleType("PyCSimpleType", PythonClass, new Builder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS).methodsFlags(PYCSIMPLETYPE_M_FLAGS)),
    PyCFuncPtrType("PyCFuncPtrType", PythonClass, new Builder().publishInModule(J__CTYPES).basetype().slots(CDataTypeSequenceBuiltins.SLOTS).methodsFlags(PYCFUNCPTRTYPE_M_FLAGS)),
    PyCData("_CData", PythonObject, new Builder().publishInModule(J__CTYPES).basetype().slots(CDataBuiltins.SLOTS)), /*- type = PyCStructType */
    Structure("Structure", PyCData, new Builder().publishInModule(J__CTYPES).basetype().slots(StructureBuiltins.SLOTS)), /*- type = PyCStructType */
    Union("Union", PyCData, new Builder().publishInModule(J__CTYPES).basetype().slots(StructureBuiltins.SLOTS)), /*- type = UnionType */
    PyCPointer("_Pointer", PyCData, new Builder().publishInModule(J__CTYPES).basetype().slots(PyCPointerBuiltins.SLOTS).methodsFlags(PYCPOINTER_M_FLAGS)), /*- type = PyCPointerType */
    PyCArray("Array", PyCData, new Builder().publishInModule(J__CTYPES).basetype().slots(PyCArrayBuiltins.SLOTS).methodsFlags(PYCARRAY_M_FLAGS)), /*- type = PyCArrayType */
    SimpleCData("_SimpleCData", PyCData, new Builder().publishInModule(J__CTYPES).basetype().slots(SimpleCDataBuiltins.SLOTS).methodsFlags(SIMPLECDATA_M_FLAGS)), /*- type = PyCStructType */
    PyCFuncPtr("PyCFuncPtr", PyCData, new Builder().publishInModule(J__CTYPES).basetype().slots(PyCFuncPtrBuiltins.SLOTS).methodsFlags(PYCFUNCPTR_M_FLAGS)), /*- type = PyCFuncPtrType */
    CField("CField", PythonObject, new Builder().publishInModule(J__CTYPES).basetype().slots(CFieldBuiltins.SLOTS)),
    DictRemover("DictRemover", PythonObject, new Builder().publishInModule(J__CTYPES).basetype()),
    StructParam("StructParam_Type", PythonObject, new Builder().publishInModule(J__CTYPES).basetype()),
    ArgError("ArgumentError", PBaseException, new Builder().publishInModule(J__CTYPES).basetype().addDict()),

    // _multibytecodec
    MultibyteCodec("MultibyteCodec", PythonObject, new Builder().publishInModule("_multibytecodec").basetype().addDict().disallowInstantiation()),
    MultibyteIncrementalEncoder("MultibyteIncrementalEncoder", PythonObject, new Builder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteIncrementalEncoderBuiltins.SLOTS)),
    MultibyteIncrementalDecoder("MultibyteIncrementalDecoder", PythonObject, new Builder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteIncrementalDecoderBuiltins.SLOTS)),
    MultibyteStreamReader("MultibyteStreamReader", PythonObject, new Builder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteStreamReaderBuiltins.SLOTS)),
    MultibyteStreamWriter("MultibyteStreamWriter", PythonObject, new Builder().publishInModule("_multibytecodec").basetype().addDict().slots(MultibyteStreamWriterBuiltins.SLOTS)),

    // contextvars
    ContextVarsToken("Token", PythonObject, new Builder().publishInModule(J__CONTEXTVARS).slots(TokenBuiltins.SLOTS)),
    ContextVarsContext("Context", PythonObject, new Builder().publishInModule(J__CONTEXTVARS).slots(ContextBuiltins.SLOTS).methodsFlags(CONTEXT_M_FLAGS)),
    ContextVar("ContextVar", PythonObject, new Builder().publishInModule(J__CONTEXTVARS)),
    // CPython uses separate keys, values, items python types for the iterators.
    ContextIterator("context_iterator", PythonObject, new Builder().publishInModule(J__CONTEXTVARS).slots(ContextIteratorBuiltins.SLOTS)),

    Capsule("PyCapsule", PythonObject, new Builder().basetype()),

    PTokenizerIter("TokenizerIter", PythonObject, new Builder().publishInModule("_tokenize").basetype().slots(TokenizerIterBuiltins.SLOTS)),

    // A marker for @Builtin that is not a class. Must always come last.
    nil("nil", PythonObject, new Builder());

    private static final class Builder {
        private String publishInModule;
        private String moduleName;
        private boolean basetype;
        private boolean addDict;
        private boolean disallowInstantiation;
        private TpSlots slots;
        private long methodsFlags = DEFAULT_M_FLAGS;

        public Builder publishInModule(String publishInModule) {
            this.publishInModule = publishInModule;
            if (moduleName == null) {
                this.moduleName = publishInModule;
            }
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder basetype() {
            this.basetype = true;
            return this;
        }

        public Builder addDict() {
            this.addDict = true;
            return this;
        }

        public Builder disallowInstantiation() {
            this.disallowInstantiation = true;
            return this;
        }

        public Builder slots(TpSlots slots) {
            this.slots = slots;
            return this;
        }

        public Builder methodsFlags(long methodsFlags) {
            this.methodsFlags = methodsFlags;
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

    private final long methodsFlags;

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, Builder builder) {
        this.name = toTruffleStringUncached(name);
        this.base = base;
        this.publishInModule = toTruffleStringUncached(builder.publishInModule);
        this.moduleName = builder.moduleName != null ? toTruffleStringUncached(builder.moduleName) : null;
        if (builder.moduleName != null && builder.moduleName != J_BUILTINS) {
            printName = toTruffleStringUncached(builder.moduleName + "." + name);
        } else {
            printName = this.name;
        }
        this.basetype = builder.basetype;
        this.isBuiltinWithDict = builder.addDict;
        this.disallowInstantiation = builder.disallowInstantiation;
        this.methodsFlags = builder.methodsFlags;
        this.weaklistoffset = -1;
        this.declaredSlots = builder.slots != null ? builder.slots : TpSlots.createEmpty();
        this.slots = initSlots(base, declaredSlots);
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

    public long getMethodsFlags() {
        return methodsFlags;
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

    @CompilationFinal(dimensions = 1) public static final PythonBuiltinClassType[] VALUES = Arrays.copyOf(values(), values().length - 1);

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

    private static TpSlots initSlots(PythonBuiltinClassType base, TpSlots declaredSlots) {
        if (base == null) {
            return declaredSlots;
        }
        var slots = base.slots.copy();
        slots.overrideIgnoreGroups(declaredSlots);
        return slots.build();
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
     * If a {@link PythonBuiltinClassType#declaredSlots} should be initialized to a merge of slots
     * from multiple {@link CoreFunctions}, use the helper methods, such as
     * {@link TpSlots#merge(TpSlots, TpSlots)}.
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
                        builder.merge(slots);
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
