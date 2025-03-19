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
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins;
import com.oracle.graal.python.builtins.modules.csv.CSVReaderBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeSequenceBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArrayBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCPointerBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PyCStructTypeBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.SimpleCDataBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins;
import com.oracle.graal.python.builtins.modules.functools.LruCacheWrapperBuiltins;
import com.oracle.graal.python.builtins.modules.functools.PartialBuiltins;
import com.oracle.graal.python.builtins.modules.hashlib.HashObjectBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedIOMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BufferedReaderMixinBuiltins;
import com.oracle.graal.python.builtins.modules.io.BytesIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.FileIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.IOBaseBuiltins;
import com.oracle.graal.python.builtins.modules.io.StringIOBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperBuiltins;
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
import com.oracle.graal.python.builtins.objects.exception.SyntaxErrorBuiltins;
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
import com.oracle.graal.python.builtins.objects.itertools.ZipLongestBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.map.MapBuiltins;
import com.oracle.graal.python.builtins.objects.mappingproxy.MappingproxyBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins;
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
import com.oracle.graal.python.builtins.objects.type.TpSlots.Builder;
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

    PythonObject("object", null, J_BUILTINS, ObjectBuiltins.SLOTS),
    PythonClass("type", PythonObject, J_BUILTINS, J_BUILTINS, Flags.PUBLIC_BASE_WDICT, TYPE_M_FLAGS, TypeBuiltins.SLOTS),
    PArray("array", PythonObject, "array", ARRAY_M_FLAGS, ArrayBuiltins.SLOTS),
    PArrayIterator("arrayiterator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PIterator("iterator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    PBuiltinFunction("method_descriptor", PythonObject, Flags.PRIVATE_DERIVED_WODICT, MethodDescriptorBuiltins.SLOTS),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinFunctionOrMethod("builtin_function_or_method", PythonObject, Flags.PRIVATE_DERIVED_WODICT, BuiltinFunctionOrMethodBuiltins.SLOTS),
    /** See {@link com.oracle.graal.python.builtins.objects.function.PBuiltinFunction} */
    WrapperDescriptor(J_WRAPPER_DESCRIPTOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, WrapperDescriptorBuiltins.SLOTS),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    MethodWrapper("method-wrapper", PythonObject, Flags.PRIVATE_DERIVED_WODICT, MethodWrapperBuiltins.SLOTS),
    /** See {@link com.oracle.graal.python.builtins.objects.method.PBuiltinMethod} */
    PBuiltinMethod("builtin_method", PBuiltinFunctionOrMethod, Flags.PRIVATE_DERIVED_WODICT),
    PBuiltinClassMethod("classmethod_descriptor", PythonObject, Flags.PRIVATE_DERIVED_WODICT, TpSlots.merge(ClassmethodCommonBuiltins.SLOTS, BuiltinClassmethodBuiltins.SLOTS)),
    GetSetDescriptor("getset_descriptor", PythonObject, Flags.PRIVATE_DERIVED_WODICT, GetSetDescriptorTypeBuiltins.SLOTS),
    MemberDescriptor(J_MEMBER_DESCRIPTOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, MemberDescriptorBuiltins.SLOTS),
    PByteArray("bytearray", PythonObject, J_BUILTINS, BYTE_ARRAY_M_FLAGS, TpSlots.merge(BytesCommonBuiltins.SLOTS, ByteArrayBuiltins.SLOTS)),
    PBytes("bytes", PythonObject, J_BUILTINS, BYTES_M_FLAGS, TpSlots.merge(BytesCommonBuiltins.SLOTS, BytesBuiltins.SLOTS)),
    PCell("cell", PythonObject, Flags.PRIVATE_DERIVED_WODICT, CellBuiltins.SLOTS),
    PSimpleNamespace("SimpleNamespace", PythonObject, null, "types", Flags.PUBLIC_BASE_WDICT, SimpleNamespaceBuiltins.SLOTS),
    PKeyWrapper("KeyWrapper", PythonObject, "_functools", "functools", Flags.PUBLIC_DERIVED_WODICT),
    PPartial(J_PARTIAL, PythonObject, "_functools", "functools", Flags.PUBLIC_BASE_WDICT, PartialBuiltins.SLOTS),
    PLruListElem("_lru_list_elem", PythonObject, null, "functools", Flags.PUBLIC_DERIVED_WODICT),
    PLruCacheWrapper(J_LRU_CACHE_WRAPPER, PythonObject, "_functools", "functools", Flags.PUBLIC_BASE_WDICT, LruCacheWrapperBuiltins.SLOTS),
    PDeque(J_DEQUE, PythonObject, "_collections", "_collections", Flags.PUBLIC_BASE_WODICT, DEQUE_M_FLAGS, DequeBuiltins.SLOTS),
    PTupleGetter(J_TUPLE_GETTER, PythonObject, "_collections", Flags.PUBLIC_BASE_WODICT, TupleGetterBuiltins.SLOTS),
    PDequeIter(J_DEQUE_ITER, PythonObject, "_collections", Flags.PUBLIC_DERIVED_WODICT, DequeIterBuiltins.SLOTS),
    PDequeRevIter(J_DEQUE_REV_ITER, PythonObject, "_collections", Flags.PUBLIC_DERIVED_WODICT, DequeIterBuiltins.SLOTS),
    PComplex("complex", PythonObject, J_BUILTINS, COMPLEX_M_FLAGS, ComplexBuiltins.SLOTS),
    PDict("dict", PythonObject, J_BUILTINS, DICT_M_FLAGS, TpSlots.merge(DictBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    PDefaultDict(J_DEFAULTDICT, PDict, "_collections", "collections", Flags.PUBLIC_BASE_WODICT, DEFAULTDICT_M_FLAGS, DefaultDictBuiltins.SLOTS),
    POrderedDict(J_ORDERED_DICT, PDict, "_collections", "_collections", Flags.PUBLIC_BASE_WDICT, DICT_M_FLAGS, OrderedDictBuiltins.SLOTS),
    PDictItemIterator(J_DICT_ITEMITERATOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PDictReverseItemIterator(J_DICT_REVERSE_ITEMITERATOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PDictItemsView(J_DICT_ITEMS, PythonObject, Flags.PRIVATE_DERIVED_WODICT, DICTITEMSVIEW_M_FLAGS, TpSlots.merge(DictViewBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    PDictKeyIterator(J_DICT_KEYITERATOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PDictReverseKeyIterator(J_DICT_REVERSE_KEYITERATOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PDictKeysView(J_DICT_KEYS, PythonObject, Flags.PRIVATE_DERIVED_WODICT, DICTKEYSVIEW_M_FLAGS, TpSlots.merge(DictViewBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    PDictValueIterator(J_DICT_VALUEITERATOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PDictReverseValueIterator(J_DICT_REVERSE_VALUEITERATOR, PythonObject, Flags.PRIVATE_DERIVED_WODICT, IteratorBuiltins.SLOTS),
    PDictValuesView(J_DICT_VALUES, PythonObject, Flags.PRIVATE_DERIVED_WODICT, DICTVALUESVIEW_M_FLAGS, TpSlots.merge(DictValuesBuiltins.SLOTS, DictReprBuiltin.SLOTS)),
    POrderedDictKeys("odict_keys", PDictKeysView, Flags.PRIVATE_DERIVED_WODICT, DICTKEYSVIEW_M_FLAGS, OrderedDictKeysBuiltins.SLOTS),
    POrderedDictValues("odict_values", PDictValuesView, Flags.PRIVATE_DERIVED_WODICT, DICTVALUESVIEW_M_FLAGS, OrderedDictValuesBuiltins.SLOTS),
    POrderedDictItems("odict_items", PDictItemsView, Flags.PRIVATE_DERIVED_WODICT, DICTITEMSVIEW_M_FLAGS, OrderedDictItemsBuiltins.SLOTS),
    POrderedDictIterator("odict_iterator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, OrderedDictIteratorBuiltins.SLOTS),
    PEllipsis("ellipsis", PythonObject, Flags.PRIVATE_DERIVED_WODICT, EllipsisBuiltins.SLOTS),
    PEnumerate("enumerate", PythonObject, J_BUILTINS, EnumerateBuiltins.SLOTS),
    PMap("map", PythonObject, J_BUILTINS, MapBuiltins.SLOTS),
    PFloat("float", PythonObject, J_BUILTINS, FLOAT_M_FLAGS, FloatBuiltins.SLOTS),
    PFrame("frame", PythonObject, Flags.PRIVATE_DERIVED_WODICT, FrameBuiltins.SLOTS),
    PFrozenSet("frozenset", PythonObject, J_BUILTINS, FROZENSET_M_FLAGS, BaseSetBuiltins.SLOTS),
    PFunction("function", PythonObject, Flags.PRIVATE_DERIVED_WDICT, FunctionBuiltins.SLOTS),
    PGenerator("generator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, GENERATOR_M_FLAGS, GeneratorBuiltins.SLOTS),
    PCoroutine("coroutine", PythonObject, Flags.PRIVATE_DERIVED_WODICT, COROUTINE_M_FLAGS, CoroutineBuiltins.SLOTS),
    PCoroutineWrapper("coroutine_wrapper", PythonObject, Flags.PRIVATE_DERIVED_WODICT, CoroutineWrapperBuiltins.SLOTS),
    PAsyncGenerator("async_generator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, ASYNC_GENERATOR_M_FLAGS),
    PInt("int", PythonObject, J_BUILTINS, INT_M_FLAGS, IntBuiltins.SLOTS),
    Boolean("bool", PInt, J_BUILTINS, J_BUILTINS, Flags.PUBLIC_DERIVED_WODICT, BOOLEAN_M_FLAGS, BoolBuiltins.SLOTS),
    PList("list", PythonObject, J_BUILTINS, LIST_M_FLAGS, ListBuiltins.SLOTS),
    PMappingproxy("mappingproxy", PythonObject, Flags.PRIVATE_DERIVED_WODICT, MAPPINGPROXY_M_FLAGS, MappingproxyBuiltins.SLOTS),
    PMemoryView("memoryview", PythonObject, J_BUILTINS, J_BUILTINS, Flags.PUBLIC_DERIVED_WODICT, MEMORYVIEW_M_FLAGS, MemoryViewBuiltins.SLOTS),
    PAsyncGenASend("async_generator_asend", PythonObject, Flags.PRIVATE_DERIVED_WODICT, ASYNC_GENERATOR_ASEND_M_FLAGS, AsyncGenSendBuiltins.SLOTS),
    PAsyncGenAThrow("async_generator_athrow", PythonObject, Flags.PRIVATE_DERIVED_WODICT, ASYNC_GENERATOR_ATHROW_M_FLAGS, AsyncGenThrowBuiltins.SLOTS),
    PAsyncGenAWrappedValue("async_generator_wrapped_value", PythonObject, Flags.PRIVATE_DERIVED_WODICT),
    PMethod("method", PythonObject, Flags.PRIVATE_DERIVED_WODICT, MethodBuiltins.SLOTS),
    PMMap("mmap", PythonObject, "mmap", MMAP_M_FLAGS, MMapBuiltins.SLOTS),
    PNone("NoneType", PythonObject, Flags.PRIVATE_DERIVED_WODICT, NONE_M_FLAGS, NoneBuiltins.SLOTS),
    PNotImplemented("NotImplementedType", PythonObject, Flags.PRIVATE_DERIVED_WODICT, NotImplementedBuiltins.SLOTS),
    PProperty(J_PROPERTY, PythonObject, J_BUILTINS, Flags.PUBLIC_BASE_WODICT, PropertyBuiltins.SLOTS),
    PSimpleQueue(J_SIMPLE_QUEUE, PythonObject, "_queue", Flags.PUBLIC_BASE_WODICT),
    PRandom("Random", PythonObject, "_random"),
    PRange("range", PythonObject, J_BUILTINS, J_BUILTINS, Flags.PUBLIC_DERIVED_WODICT, RANGE_M_FLAGS, RangeBuiltins.SLOTS),
    PReferenceType("ReferenceType", PythonObject, "_weakref", ReferenceTypeBuiltins.SLOTS),
    PSentinelIterator("callable_iterator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, SentinelIteratorBuiltins.SLOTS),
    PReverseIterator("reversed", PythonObject, J_BUILTINS, ReversedBuiltins.SLOTS),
    PSet("set", PythonObject, J_BUILTINS, SET_M_FLAGS, TpSlots.merge(BaseSetBuiltins.SLOTS, SetBuiltins.SLOTS)),
    PSlice("slice", PythonObject, J_BUILTINS, SliceBuiltins.SLOTS),
    PString("str", PythonObject, J_BUILTINS, STRING_M_FLAGS, StringBuiltins.SLOTS),
    PTraceback(PythonObject, "traceback"),
    PTuple("tuple", PythonObject, J_BUILTINS, TUPLE_M_FLAGS, TupleBuiltins.SLOTS),
    PythonModule("module", PythonObject, Flags.PRIVATE_BASE_WDICT, ModuleBuiltins.SLOTS),
    PythonModuleDef("moduledef", PythonObject, Flags.PRIVATE_DERIVED_WODICT),
    Super("super", PythonObject, J_BUILTINS, SuperBuiltins.SLOTS),
    PCode("code", PythonObject, Flags.PRIVATE_DERIVED_WODICT, CodeBuiltins.SLOTS),
    PGenericAlias("GenericAlias", PythonObject, J_TYPES, J_TYPES, Flags.PUBLIC_BASE_WODICT, GENERIC_ALIAS_M_FLAGS, GenericAliasBuiltins.SLOTS),
    PGenericAliasIterator("generic_alias_iterator", PythonObject, Flags.PRIVATE_DERIVED_WODICT, GenericAliasIteratorBuiltins.SLOTS),
    PUnionType("UnionType", PythonObject, J_TYPES, J_TYPES, Flags.PUBLIC_DERIVED_WODICT, UNION_TYPE_M_FLAGS, UnionTypeBuiltins.SLOTS),
    PZip("zip", PythonObject, J_BUILTINS, PZipBuiltins.SLOTS),
    PThread("start_new_thread", PythonObject, J__THREAD),
    PThreadLocal("_local", PythonObject, J__THREAD, ThreadLocalBuiltins.SLOTS),
    PLock("LockType", PythonObject, J__THREAD, LockBuiltins.SLOTS),
    PRLock("RLock", PythonObject, J__THREAD, LockBuiltins.SLOTS),
    PSemLock("SemLock", PythonObject, "_multiprocessing"),
    PGraalPySemLock("SemLock", PythonObject, "_multiprocessing_graalpy"),
    PSocket("socket", PythonObject, J__SOCKET, SocketBuiltins.SLOTS),
    PStaticmethod("staticmethod", PythonObject, J_BUILTINS, Flags.PUBLIC_BASE_WDICT, StaticmethodBuiltins.SLOTS),
    PClassmethod("classmethod", PythonObject, J_BUILTINS, Flags.PUBLIC_BASE_WDICT, TpSlots.merge(ClassmethodCommonBuiltins.SLOTS, ClassmethodBuiltins.SLOTS)),
    PInstancemethod("instancemethod", PythonObject, Flags.PUBLIC_BASE_WDICT, InstancemethodBuiltins.SLOTS),
    PScandirIterator("ScandirIterator", PythonObject, J_POSIX, Flags.PRIVATE_DERIVED_WODICT, ScandirIteratorBuiltins.SLOTS),
    PDirEntry("DirEntry", PythonObject, J_POSIX, Flags.PUBLIC_DERIVED_WODICT, DirEntryBuiltins.SLOTS),
    LsprofProfiler("Profiler", PythonObject, "_lsprof"),
    PStruct("Struct", PythonObject, J__STRUCT),
    PStructUnpackIterator("unpack_iterator", PythonObject, J__STRUCT, StructUnpackIteratorBuiltins.SLOTS),
    Pickler("Pickler", PythonObject, "_pickle"),
    PicklerMemoProxy("PicklerMemoProxy", PythonObject, "_pickle"),
    UnpicklerMemoProxy("UnpicklerMemoProxy", PythonObject, "_pickle"),
    Unpickler("Unpickler", PythonObject, "_pickle"),
    PickleBuffer("PickleBuffer", PythonObject, "_pickle"),

    // Errors and exceptions:

    // everything after BaseException is considered to be an exception
    PBaseException("BaseException", PythonObject, J_BUILTINS, Flags.EXCEPTION, BaseExceptionBuiltins.SLOTS),
    PBaseExceptionGroup("BaseExceptionGroup", PBaseException, J_BUILTINS, Flags.EXCEPTION, BaseExceptionGroupBuiltins.SLOTS),
    SystemExit("SystemExit", PBaseException, J_BUILTINS, Flags.EXCEPTION),
    KeyboardInterrupt("KeyboardInterrupt", PBaseException, J_BUILTINS, Flags.EXCEPTION),
    GeneratorExit("GeneratorExit", PBaseException, J_BUILTINS, Flags.EXCEPTION),
    Exception("Exception", PBaseException, J_BUILTINS, Flags.EXCEPTION),
    ReferenceError("ReferenceError", Exception, J_BUILTINS, Flags.EXCEPTION),
    RuntimeError("RuntimeError", Exception, J_BUILTINS, Flags.EXCEPTION),
    NotImplementedError("NotImplementedError", RuntimeError, J_BUILTINS, Flags.EXCEPTION),
    SyntaxError("SyntaxError", Exception, J_BUILTINS, Flags.EXCEPTION, SyntaxErrorBuiltins.SLOTS),
    IndentationError("IndentationError", SyntaxError, J_BUILTINS, Flags.EXCEPTION, SyntaxErrorBuiltins.SLOTS),
    TabError("TabError", IndentationError, J_BUILTINS, Flags.EXCEPTION, SyntaxErrorBuiltins.SLOTS),
    SystemError("SystemError", Exception, J_BUILTINS, Flags.EXCEPTION),
    TypeError("TypeError", Exception, J_BUILTINS, Flags.EXCEPTION),
    ValueError("ValueError", Exception, J_BUILTINS, Flags.EXCEPTION),
    StopIteration("StopIteration", Exception, J_BUILTINS, Flags.EXCEPTION),
    StopAsyncIteration("StopAsyncIteration", Exception, J_BUILTINS, Flags.EXCEPTION),
    ArithmeticError("ArithmeticError", Exception, J_BUILTINS, Flags.EXCEPTION),
    FloatingPointError("FloatingPointError", ArithmeticError, J_BUILTINS, Flags.EXCEPTION),
    OverflowError("OverflowError", ArithmeticError, J_BUILTINS, Flags.EXCEPTION),
    ZeroDivisionError("ZeroDivisionError", ArithmeticError, J_BUILTINS, Flags.EXCEPTION),
    AssertionError("AssertionError", Exception, J_BUILTINS, Flags.EXCEPTION),
    AttributeError("AttributeError", Exception, J_BUILTINS, Flags.EXCEPTION),
    BufferError("BufferError", Exception, J_BUILTINS, Flags.EXCEPTION),
    EOFError("EOFError", Exception, J_BUILTINS, Flags.EXCEPTION),
    ImportError("ImportError", Exception, J_BUILTINS, Flags.EXCEPTION, ImportErrorBuiltins.SLOTS),
    ModuleNotFoundError("ModuleNotFoundError", ImportError, J_BUILTINS, Flags.EXCEPTION),
    LookupError("LookupError", Exception, J_BUILTINS, Flags.EXCEPTION),
    IndexError("IndexError", LookupError, J_BUILTINS, Flags.EXCEPTION),
    KeyError("KeyError", LookupError, J_BUILTINS, Flags.EXCEPTION, KeyErrorBuiltins.SLOTS),
    MemoryError("MemoryError", Exception, J_BUILTINS, Flags.EXCEPTION),
    NameError("NameError", Exception, J_BUILTINS, Flags.EXCEPTION),
    UnboundLocalError("UnboundLocalError", NameError, J_BUILTINS, Flags.EXCEPTION),
    OSError("OSError", Exception, J_BUILTINS, Flags.EXCEPTION, OsErrorBuiltins.SLOTS),
    BlockingIOError("BlockingIOError", OSError, J_BUILTINS, Flags.EXCEPTION),
    ChildProcessError("ChildProcessError", OSError, J_BUILTINS, Flags.EXCEPTION),
    ConnectionError("ConnectionError", OSError, J_BUILTINS, Flags.EXCEPTION),
    BrokenPipeError("BrokenPipeError", ConnectionError, J_BUILTINS, Flags.EXCEPTION),
    ConnectionAbortedError("ConnectionAbortedError", ConnectionError, J_BUILTINS, Flags.EXCEPTION),
    ConnectionRefusedError("ConnectionRefusedError", ConnectionError, J_BUILTINS, Flags.EXCEPTION),
    ConnectionResetError("ConnectionResetError", ConnectionError, J_BUILTINS, Flags.EXCEPTION),
    FileExistsError("FileExistsError", OSError, J_BUILTINS, Flags.EXCEPTION),
    FileNotFoundError("FileNotFoundError", OSError, J_BUILTINS, Flags.EXCEPTION),
    InterruptedError("InterruptedError", OSError, J_BUILTINS, Flags.EXCEPTION),
    IsADirectoryError("IsADirectoryError", OSError, J_BUILTINS, Flags.EXCEPTION),
    NotADirectoryError("NotADirectoryError", OSError, J_BUILTINS, Flags.EXCEPTION),
    PermissionError("PermissionError", OSError, J_BUILTINS, Flags.EXCEPTION),
    ProcessLookupError("ProcessLookupError", OSError, J_BUILTINS, Flags.EXCEPTION),
    TimeoutError("TimeoutError", OSError, J_BUILTINS, Flags.EXCEPTION),
    ZLibError("error", Exception, "zlib", Flags.EXCEPTION),
    CSVError("Error", Exception, "_csv", Flags.EXCEPTION),
    LZMAError("LZMAError", Exception, "_lzma", Flags.EXCEPTION),
    StructError("StructError", Exception, J__STRUCT, Flags.EXCEPTION),
    PickleError("PickleError", Exception, "_pickle", Flags.EXCEPTION),
    PicklingError("PicklingError", PickleError, "_pickle", Flags.EXCEPTION),
    UnpicklingError("UnpicklingError", PickleError, "_pickle", Flags.EXCEPTION),
    SocketGAIError("gaierror", OSError, J__SOCKET, Flags.EXCEPTION),
    SocketHError("herror", OSError, J__SOCKET, Flags.EXCEPTION),
    BinasciiError("Error", ValueError, "binascii", Flags.EXCEPTION),
    BinasciiIncomplete("Incomplete", Exception, "binascii", Flags.EXCEPTION),
    SSLError("SSLError", OSError, J__SSL, Flags.EXCEPTION, SSLErrorBuiltins.SLOTS),
    SSLZeroReturnError("SSLZeroReturnError", SSLError, J__SSL, Flags.EXCEPTION),
    SSLWantReadError("SSLWantReadError", SSLError, J__SSL, Flags.EXCEPTION),
    SSLWantWriteError("SSLWantWriteError", SSLError, J__SSL, Flags.EXCEPTION),
    SSLSyscallError("SSLSyscallError", SSLError, J__SSL, Flags.EXCEPTION),
    SSLEOFError("SSLEOFError", SSLError, J__SSL, Flags.EXCEPTION),
    SSLCertVerificationError("SSLCertVerificationError", SSLError, J__SSL, Flags.EXCEPTION),

    // todo: all OS errors

    UnicodeError("UnicodeError", ValueError, J_BUILTINS, Flags.EXCEPTION),
    UnicodeDecodeError("UnicodeDecodeError", UnicodeError, J_BUILTINS, Flags.EXCEPTION, UnicodeDecodeErrorBuiltins.SLOTS),
    UnicodeEncodeError("UnicodeEncodeError", UnicodeError, J_BUILTINS, Flags.EXCEPTION, UnicodeEncodeErrorBuiltins.SLOTS),
    UnicodeTranslateError("UnicodeTranslateError", UnicodeError, J_BUILTINS, Flags.EXCEPTION, UnicodeTranslateErrorBuiltins.SLOTS),
    RecursionError("RecursionError", RuntimeError, J_BUILTINS, Flags.EXCEPTION),

    /*
     * _io.UnsupportedOperation inherits from ValueError and OSError done currently within
     * IOModuleBuiltins class
     */
    IOUnsupportedOperation("UnsupportedOperation", OSError, "io", Flags.EXCEPTION),

    Empty("Empty", Exception, "_queue", Flags.EXCEPTION),

    UnsupportedMessage("UnsupportedMessage", Exception, J_POLYGLOT, Flags.EXCEPTION),

    // warnings
    Warning("Warning", Exception, J_BUILTINS, Flags.EXCEPTION),
    BytesWarning("BytesWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    DeprecationWarning("DeprecationWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    FutureWarning("FutureWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    ImportWarning("ImportWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    PendingDeprecationWarning("PendingDeprecationWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    ResourceWarning("ResourceWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    RuntimeWarning("RuntimeWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    SyntaxWarning("SyntaxWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    UnicodeWarning("UnicodeWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    UserWarning("UserWarning", Warning, J_BUILTINS, Flags.EXCEPTION),
    EncodingWarning("EncodingWarning", Warning, J_BUILTINS, Flags.EXCEPTION),

    // Foreign
    ForeignObject("ForeignObject", PythonObject, J_POLYGLOT, Flags.PUBLIC_BASE_WDICT, ForeignObjectBuiltins.SLOTS),
    ForeignNumber("ForeignNumber", J_POLYGLOT, ForeignObject, Flags.PUBLIC_BASE_WDICT, FOREIGNNUMBER_M_FLAGS, ForeignNumberBuiltins.SLOTS),
    ForeignBoolean("ForeignBoolean", J_POLYGLOT, ForeignNumber, Flags.PUBLIC_BASE_WDICT, FOREIGNNUMBER_M_FLAGS, ForeignBooleanBuiltins.SLOTS),
    ForeignAbstractClass("ForeignAbstractClass", ForeignObject, J_POLYGLOT, Flags.PUBLIC_BASE_WDICT),
    ForeignExecutable("ForeignExecutable", ForeignObject, J_POLYGLOT, Flags.PUBLIC_BASE_WDICT),
    ForeignInstantiable("ForeignInstantiable", ForeignObject, J_POLYGLOT, Flags.PUBLIC_BASE_WDICT),
    ForeignIterable("ForeignIterable", ForeignObject, J_POLYGLOT, J_POLYGLOT, Flags.PUBLIC_BASE_WDICT, ForeignIterableBuiltins.SLOTS),

    // bz2
    BZ2Compressor("BZ2Compressor", PythonObject, "_bz2"),
    BZ2Decompressor("BZ2Decompressor", PythonObject, "_bz2"),

    // lzma
    PLZMACompressor("LZMACompressor", PythonObject, "_lzma"),
    PLZMADecompressor("LZMADecompressor", PythonObject, "_lzma"),

    // zlib
    ZlibCompress("Compress", PythonObject, "zlib"),
    ZlibDecompress("Decompress", PythonObject, "zlib"),

    // io
    PIOBase("_IOBase", PythonObject, "_io", Flags.PUBLIC_BASE_WDICT, IOBaseBuiltins.SLOTS),
    PRawIOBase("_RawIOBase", PIOBase, "_io", IOBaseBuiltins.SLOTS),
    PTextIOBase("_TextIOBase", PIOBase, "_io", IOBaseBuiltins.SLOTS),
    PBufferedIOBase("_BufferedIOBase", PIOBase, "_io", IOBaseBuiltins.SLOTS),
    PBufferedReader("BufferedReader", PBufferedIOBase, "_io", Flags.PUBLIC_BASE_WDICT, TpSlots.merge(BufferedReaderMixinBuiltins.SLOTS, BufferedIOMixinBuiltins.SLOTS)),
    PBufferedWriter("BufferedWriter", PBufferedIOBase, "_io", Flags.PUBLIC_BASE_WDICT, BufferedIOMixinBuiltins.SLOTS),
    PBufferedRWPair("BufferedRWPair", PBufferedIOBase, "_io", Flags.PUBLIC_BASE_WDICT),
    PBufferedRandom("BufferedRandom", PBufferedIOBase, "_io", Flags.PUBLIC_BASE_WDICT, TpSlots.merge(BufferedReaderMixinBuiltins.SLOTS, BufferedIOMixinBuiltins.SLOTS)),
    PFileIO("FileIO", PRawIOBase, "_io", Flags.PUBLIC_BASE_WDICT, FileIOBuiltins.SLOTS),
    PTextIOWrapper("TextIOWrapper", PTextIOBase, "_io", Flags.PUBLIC_BASE_WDICT, TextIOWrapperBuiltins.SLOTS),
    PIncrementalNewlineDecoder("IncrementalNewlineDecoder", PythonObject, "_io", Flags.PUBLIC_BASE_WODICT),
    PStringIO("StringIO", PTextIOBase, "_io", Flags.PUBLIC_BASE_WDICT, StringIOBuiltins.SLOTS),
    PBytesIO("BytesIO", PBufferedIOBase, "_io", Flags.PUBLIC_BASE_WDICT, BytesIOBuiltins.SLOTS),
    PBytesIOBuf("_BytesIOBuffer", PythonObject, "_io", Flags.PRIVATE_BASE_WODICT),

    PStatResult("stat_result", PTuple, "os", "os", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PStatvfsResult("statvfs_result", PTuple, "os", "os", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PTerminalSize("terminal_size", PTuple, "os", "os", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PUnameResult("uname_result", PTuple, J_POSIX, J_POSIX, Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PStructTime("struct_time", PTuple, "time", "time", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PProfilerEntry("profiler_entry", PTuple, "_lsprof", "_lsprof", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PProfilerSubentry("profiler_subentry", PTuple, "_lsprof", "_lsprof", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PStructPasswd("struct_passwd", PTuple, "pwd", "pwd", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PStructRusage("struct_rusage", PTuple, "resource", "resource", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PVersionInfo("version_info", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PWindowsVersion("windowsversion", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PFlags("flags", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PFloatInfo("float_info", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PIntInfo("int_info", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PHashInfo("hash_info", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PThreadInfo("thread_info", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),
    PUnraisableHookArgs("UnraisableHookArgs", PTuple, "sys", "sys", Flags.PUBLIC_DERIVED_WODICT, TUPLE_M_FLAGS, StructSequenceBuiltins.SLOTS),

    PSSLSession("SSLSession", PythonObject, J__SSL),
    PSSLContext("_SSLContext", PythonObject, J__SSL),
    PSSLSocket("_SSLSocket", PythonObject, J__SSL),
    PMemoryBIO("MemoryBIO", PythonObject, J__SSL),

    // itertools
    PTee("_tee", PythonObject, "itertools", Flags.PUBLIC_DERIVED_WODICT, TeeBuiltins.SLOTS),
    PTeeDataObject("_tee_dataobject", PythonObject, "itertools", Flags.PUBLIC_DERIVED_WODICT),
    PAccumulate("accumulate", PythonObject, "itertools", AccumulateBuiltins.SLOTS),
    PCombinations("combinations", PythonObject, "itertools", CombinationsBuiltins.SLOTS),
    PCombinationsWithReplacement("combinations_with_replacement", PythonObject, "itertools", CombinationsBuiltins.SLOTS),
    PCompress("compress", PythonObject, "itertools", CompressBuiltins.SLOTS),
    PCycle("cycle", PythonObject, "itertools", CycleBuiltins.SLOTS),
    PDropwhile("dropwhile", PythonObject, "itertools", DropwhileBuiltins.SLOTS),
    PFilterfalse("filterfalse", PythonObject, "itertools", FilterfalseBuiltins.SLOTS),
    PGroupBy("groupby", PythonObject, "itertools", GroupByBuiltins.SLOTS),
    PGrouper("grouper", PythonObject, "itertools", Flags.PUBLIC_DERIVED_WODICT, GrouperBuiltins.SLOTS),
    PPairwise("pairwise", PythonObject, "itertools", PairwiseBuiltins.SLOTS),
    PPermutations("permutations", PythonObject, "itertools", PermutationsBuiltins.SLOTS),
    PProduct("product", PythonObject, "itertools", ProductBuiltins.SLOTS),
    PRepeat("repeat", PythonObject, "itertools", RepeatBuiltins.SLOTS),
    PChain("chain", PythonObject, "itertools", ChainBuiltins.SLOTS),
    PCount("count", PythonObject, "itertools", CountBuiltins.SLOTS),
    PIslice("islice", PythonObject, "itertools", IsliceBuiltins.SLOTS),
    PStarmap("starmap", PythonObject, "itertools", StarmapBuiltins.SLOTS),
    PTakewhile("takewhile", PythonObject, "itertools", TakewhileBuiltins.SLOTS),
    PZipLongest("zip_longest", PythonObject, "itertools", ZipLongestBuiltins.SLOTS),

    // json
    JSONScanner("Scanner", PythonObject, "_json", Flags.PUBLIC_BASE_WODICT),
    JSONEncoder("Encoder", PythonObject, "_json", Flags.PUBLIC_BASE_WODICT),

    // csv
    CSVDialect("Dialect", PythonObject, "_csv", Flags.PUBLIC_BASE_WODICT),
    CSVReader("Reader", PythonObject, "_csv", Flags.PUBLIC_BASE_WODICT, CSVReaderBuiltins.SLOTS),
    CSVWriter("Writer", PythonObject, "_csv", Flags.PUBLIC_BASE_WODICT),

    // codecs
    PEncodingMap("EncodingMap", PythonObject, Flags.PRIVATE_DERIVED_WODICT),

    // hashlib
    MD5Type("md5", PythonObject, "_md5", Flags.PUBLIC_BASE_WODICT),
    SHA1Type("sha1", PythonObject, "_sha1", Flags.PUBLIC_BASE_WODICT),
    SHA224Type("sha224", PythonObject, "_sha256", Flags.PUBLIC_BASE_WODICT),
    SHA256Type("sha256", PythonObject, "_sha256", Flags.PUBLIC_BASE_WODICT),
    SHA384Type("sha384", PythonObject, "_sha512", Flags.PUBLIC_BASE_WODICT),
    SHA512Type("sha512", PythonObject, "_sha512", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA224Type("sha3_224", PythonObject, "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA256Type("sha3_256", PythonObject, "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA384Type("sha3_384", PythonObject, "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3SHA512Type("sha3_512", PythonObject, "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3Shake128Type("shake_128", PythonObject, "_sha3", Flags.PUBLIC_BASE_WODICT),
    Sha3Shake256Type("shake_256", PythonObject, "_sha3", Flags.PUBLIC_BASE_WODICT),
    Blake2bType("blake2b", PythonObject, "_blake2", Flags.PUBLIC_BASE_WODICT),
    Blake2sType("blake2s", PythonObject, "_blake2", Flags.PUBLIC_BASE_WODICT),
    HashlibHash("HASH", PythonObject, "_hashlib", Flags.PUBLIC_BASE_WODICT, HashObjectBuiltins.SLOTS),
    HashlibHashXof("HASHXOF", HashlibHash, "_hashlib", Flags.PUBLIC_DERIVED_WODICT),
    HashlibHmac("HMAC", PythonObject, "_hashlib", Flags.PUBLIC_BASE_WODICT, HashObjectBuiltins.SLOTS),
    UnsupportedDigestmodError("UnsupportedDigestmodError", ValueError, "_hashlib", Flags.EXCEPTION),

    // _ast (rest of the classes are not builtin, they are generated in AstModuleBuiltins)
    AST("AST", PythonObject, "_ast", "ast", Flags.PUBLIC_BASE_WDICT),

    // _ctype
    CArgObject("CArgObject", PythonObject, Flags.PUBLIC_BASE_WODICT, CArgObjectBuiltins.SLOTS),
    CThunkObject("CThunkObject", PythonObject, J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    StgDict("StgDict", PDict, Flags.PRIVATE_DERIVED_WODICT, DICT_M_FLAGS, StgDictBuiltins.SLOTS),
    PyCStructType("PyCStructType", PythonClass, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCSTRUCTTYPE_M_FLAGS, TpSlots.merge(CDataTypeSequenceBuiltins.SLOTS, PyCStructTypeBuiltins.SLOTS)),
    UnionType(
                    "UnionType",
                    PythonClass,
                    J__CTYPES,
                    J__CTYPES,
                    Flags.PUBLIC_BASE_WODICT,
                    UNIONTYPE_M_FLAGS,
                    TpSlots.merge(CDataTypeSequenceBuiltins.SLOTS, com.oracle.graal.python.builtins.modules.ctypes.UnionTypeBuiltins.SLOTS)),
    PyCPointerType("PyCPointerType", PythonClass, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCPOINTERTYPE_M_FLAGS, CDataTypeSequenceBuiltins.SLOTS),
    PyCArrayType("PyCArrayType", PythonClass, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCARRAYTYPE_M_FLAGS, CDataTypeSequenceBuiltins.SLOTS),
    PyCSimpleType("PyCSimpleType", PythonClass, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCSIMPLETYPE_M_FLAGS, CDataTypeSequenceBuiltins.SLOTS),
    PyCFuncPtrType("PyCFuncPtrType", PythonClass, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCFUNCPTRTYPE_M_FLAGS, CDataTypeSequenceBuiltins.SLOTS),
    PyCData("_CData", PythonObject, J__CTYPES, Flags.PUBLIC_BASE_WODICT), /*- type = PyCStructType */
    Structure("Structure", PyCData, J__CTYPES, Flags.PUBLIC_BASE_WODICT), /*- type = PyCStructType */
    Union("Union", PyCData, J__CTYPES, Flags.PUBLIC_BASE_WODICT), /*- type = UnionType */
    PyCPointer("_Pointer", PyCData, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCPOINTER_M_FLAGS, PyCPointerBuiltins.SLOTS), /*- type = PyCPointerType */
    PyCArray("Array", PyCData, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCARRAY_M_FLAGS, PyCArrayBuiltins.SLOTS), /*- type = PyCArrayType */
    SimpleCData("_SimpleCData", PyCData, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, SIMPLECDATA_M_FLAGS, SimpleCDataBuiltins.SLOTS), /*- type = PyCStructType */
    PyCFuncPtr("PyCFuncPtr", PyCData, J__CTYPES, J__CTYPES, Flags.PUBLIC_BASE_WODICT, PYCFUNCPTR_M_FLAGS, PyCFuncPtrBuiltins.SLOTS), /*- type = PyCFuncPtrType */
    CField("CField", PythonObject, J__CTYPES, Flags.PUBLIC_BASE_WODICT, CFieldBuiltins.SLOTS),
    DictRemover("DictRemover", PythonObject, J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    StructParam("StructParam_Type", PythonObject, J__CTYPES, Flags.PUBLIC_BASE_WODICT),
    ArgError("ArgumentError", PBaseException, J__CTYPES, Flags.EXCEPTION),

    // _multibytecodec
    MultibyteCodec("MultibyteCodec", PythonObject, "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteIncrementalEncoder("MultibyteIncrementalEncoder", PythonObject, "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteIncrementalDecoder("MultibyteIncrementalDecoder", PythonObject, "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteStreamReader("MultibyteStreamReader", PythonObject, "_multibytecodec", Flags.PUBLIC_BASE_WDICT),
    MultibyteStreamWriter("MultibyteStreamWriter", PythonObject, "_multibytecodec", Flags.PUBLIC_BASE_WDICT),

    // contextvars
    ContextVarsToken("Token", PythonObject, J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT),
    ContextVarsContext("Context", PythonObject, J__CONTEXTVARS, J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT, CONTEXT_M_FLAGS, ContextBuiltins.SLOTS),
    ContextVar("ContextVar", PythonObject, J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT),
    // CPython uses separate keys, values, items python types for the iterators.
    ContextIterator("context_iterator", PythonObject, J__CONTEXTVARS, Flags.PUBLIC_DERIVED_WODICT, ContextIteratorBuiltins.SLOTS),

    Capsule(PythonObject, "PyCapsule"),

    PTokenizerIter("TokenizerIter", PythonObject, "_tokenize", TokenizerIterBuiltins.SLOTS),

    // A marker for @Builtin that is not a class. Must always come last.
    nil(PythonObject, "nil");

    private static class Flags {

        static final Flags EXCEPTION = new Flags(true, true, true);
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
    private final PythonBuiltinClassType base;
    private final TruffleString publishInModule;
    private final TruffleString moduleName;
    // This is the name qualified by module used for printing. But the actual __qualname__ is just
    // plain name without module
    private final TruffleString printName;
    private final boolean basetype;
    private final boolean isBuiltinWithDict;

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

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String module, Flags flags) {
        this(name, base, module, module, flags);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String module, Flags flags, TpSlots slots) {
        this(name, base, module, module, flags, DEFAULT_M_FLAGS, slots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String module, Flags flags, long methodsFlags) {
        this(name, base, module, module, flags, methodsFlags, TpSlots.createEmpty());
    }

    PythonBuiltinClassType(String name, String module, PythonBuiltinClassType base, Flags flags, long methodsFlags, TpSlots slots) {
        this(name, base, module, module, flags, methodsFlags, slots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String publishInModule, String moduleName, Flags flags) {
        this(name, base, publishInModule, moduleName, flags, DEFAULT_M_FLAGS, TpSlots.createEmpty());
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String publishInModule, String moduleName, Flags flags, TpSlots slots) {
        this(name, base, publishInModule, moduleName, flags, DEFAULT_M_FLAGS, slots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String publishInModule, String moduleName, Flags flags, long methodsFlags, TpSlots declaredSlots) {
        this.name = toTruffleStringUncached(name);
        this.base = base;
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
        this.declaredSlots = declaredSlots;
        this.slots = initSlots(base, declaredSlots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String module) {
        this(name, base, module, Flags.PUBLIC_BASE_WODICT);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String module, TpSlots slots) {
        this(name, base, module, Flags.PUBLIC_BASE_WODICT, slots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, String module, long methodsFlags, TpSlots slots) {
        this(name, base, module, module, Flags.PUBLIC_BASE_WODICT, methodsFlags, slots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, Flags flags) {
        this(name, base, null, flags);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, Flags flags, TpSlots slots) {
        this(name, base, null, flags, slots);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, Flags flags, long methodsFlags) {
        this(name, base, null, flags, methodsFlags);
    }

    PythonBuiltinClassType(String name, PythonBuiltinClassType base, Flags flags, long methodsFlags, TpSlots slots) {
        this(name, base, null, null, flags, methodsFlags, slots);
    }

    PythonBuiltinClassType(PythonBuiltinClassType base, String name) {
        this(name, base, Flags.PRIVATE_BASE_WODICT);
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
                Builder builder = TpSlots.newBuilder();
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
