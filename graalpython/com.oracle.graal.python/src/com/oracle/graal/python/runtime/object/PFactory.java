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
package com.oracle.graal.python.runtime.object;

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.lang.ref.ReferenceQueue;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixFileHandle;
import com.oracle.graal.python.builtins.modules.bz2.BZ2Object;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodec;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecObject;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalDecoderObject;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteIncrementalEncoderObject;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamReaderObject;
import com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteStreamWriterObject;
import com.oracle.graal.python.builtins.modules.codecs.PEncodingMap;
import com.oracle.graal.python.builtins.modules.csv.CSVDialect;
import com.oracle.graal.python.builtins.modules.csv.CSVReader;
import com.oracle.graal.python.builtins.modules.csv.CSVWriter;
import com.oracle.graal.python.builtins.modules.csv.QuoteStyle;
import com.oracle.graal.python.builtins.modules.functools.LruCacheObject;
import com.oracle.graal.python.builtins.modules.functools.PKeyWrapper;
import com.oracle.graal.python.builtins.modules.functools.PPartial;
import com.oracle.graal.python.builtins.modules.hashlib.DigestObject;
import com.oracle.graal.python.builtins.modules.io.PBuffered;
import com.oracle.graal.python.builtins.modules.io.PBytesIO;
import com.oracle.graal.python.builtins.modules.io.PBytesIOBuffer;
import com.oracle.graal.python.builtins.modules.io.PFileIO;
import com.oracle.graal.python.builtins.modules.io.PNLDecoder;
import com.oracle.graal.python.builtins.modules.io.PRWPair;
import com.oracle.graal.python.builtins.modules.io.PStringIO;
import com.oracle.graal.python.builtins.modules.io.PTextIO;
import com.oracle.graal.python.builtins.modules.json.PJSONEncoder;
import com.oracle.graal.python.builtins.modules.json.PJSONEncoder.FastEncode;
import com.oracle.graal.python.builtins.modules.json.PJSONScanner;
import com.oracle.graal.python.builtins.modules.lsprof.Profiler;
import com.oracle.graal.python.builtins.modules.lzma.LZMAObject;
import com.oracle.graal.python.builtins.modules.multiprocessing.PGraalPySemLock;
import com.oracle.graal.python.builtins.modules.multiprocessing.PSemLock;
import com.oracle.graal.python.builtins.modules.pickle.PPickleBuffer;
import com.oracle.graal.python.builtins.modules.pickle.PPickler;
import com.oracle.graal.python.builtins.modules.pickle.PPicklerMemoProxy;
import com.oracle.graal.python.builtins.modules.pickle.PUnpickler;
import com.oracle.graal.python.builtins.modules.pickle.PUnpicklerMemoProxy;
import com.oracle.graal.python.builtins.modules.zlib.JavaCompress;
import com.oracle.graal.python.builtins.modules.zlib.JavaDecompress;
import com.oracle.graal.python.builtins.modules.zlib.NativeZlibCompObject;
import com.oracle.graal.python.builtins.modules.zlib.ZLibCompObject;
import com.oracle.graal.python.builtins.modules.zlib.ZlibDecompressorObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.asyncio.PANextAwaitable;
import com.oracle.graal.python.builtins.objects.asyncio.PAsyncGen;
import com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenASend;
import com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenAThrow;
import com.oracle.graal.python.builtins.objects.asyncio.PAsyncGenWrappedValue;
import com.oracle.graal.python.builtins.objects.asyncio.PCoroutineWrapper;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.ForeignHashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.contextvars.PContextIterator;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVarsContext;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVarsToken;
import com.oracle.graal.python.builtins.objects.deque.PDeque;
import com.oracle.graal.python.builtins.objects.deque.PDequeIter;
import com.oracle.graal.python.builtins.objects.dict.PDefaultDict;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemIterator;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeyIterator;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValueIterator;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictValuesView;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.PBaseExceptionGroup;
import com.oracle.graal.python.builtins.objects.filter.PFilter;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.IndexedSlotDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBigRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStructUnpackIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.itertools.PAccumulate;
import com.oracle.graal.python.builtins.objects.itertools.PBatched;
import com.oracle.graal.python.builtins.objects.itertools.PChain;
import com.oracle.graal.python.builtins.objects.itertools.PCombinations;
import com.oracle.graal.python.builtins.objects.itertools.PCombinationsWithReplacement;
import com.oracle.graal.python.builtins.objects.itertools.PCompress;
import com.oracle.graal.python.builtins.objects.itertools.PCount;
import com.oracle.graal.python.builtins.objects.itertools.PCycle;
import com.oracle.graal.python.builtins.objects.itertools.PDropwhile;
import com.oracle.graal.python.builtins.objects.itertools.PFilterfalse;
import com.oracle.graal.python.builtins.objects.itertools.PGroupBy;
import com.oracle.graal.python.builtins.objects.itertools.PGrouper;
import com.oracle.graal.python.builtins.objects.itertools.PIslice;
import com.oracle.graal.python.builtins.objects.itertools.PPairwise;
import com.oracle.graal.python.builtins.objects.itertools.PPermutations;
import com.oracle.graal.python.builtins.objects.itertools.PProduct;
import com.oracle.graal.python.builtins.objects.itertools.PRepeat;
import com.oracle.graal.python.builtins.objects.itertools.PStarmap;
import com.oracle.graal.python.builtins.objects.itertools.PTakewhile;
import com.oracle.graal.python.builtins.objects.itertools.PTee;
import com.oracle.graal.python.builtins.objects.itertools.PTeeDataObject;
import com.oracle.graal.python.builtins.objects.itertools.PZipLongest;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewIterator;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.ordereddict.POrderedDict;
import com.oracle.graal.python.builtins.objects.ordereddict.POrderedDictIterator;
import com.oracle.graal.python.builtins.objects.posix.PDirEntry;
import com.oracle.graal.python.builtins.objects.posix.PScandirIterator;
import com.oracle.graal.python.builtins.objects.property.PProperty;
import com.oracle.graal.python.builtins.objects.queue.PSimpleQueue;
import com.oracle.graal.python.builtins.objects.random.PRandom;
import com.oracle.graal.python.builtins.objects.range.PBigRange;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator;
import com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PIntSlice;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.ssl.PMemoryBIO;
import com.oracle.graal.python.builtins.objects.ssl.PSSLContext;
import com.oracle.graal.python.builtins.objects.ssl.PSSLSocket;
import com.oracle.graal.python.builtins.objects.ssl.SSLMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.struct.PStruct;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PThreadLocal;
import com.oracle.graal.python.builtins.objects.tokenize.PTokenizerIter;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.PTupleGetter;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence.BuiltinTypeDescriptor;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.types.PGenericAlias;
import com.oracle.graal.python.builtins.objects.types.PGenericAliasIterator;
import com.oracle.graal.python.builtins.objects.types.PUnionType;
import com.oracle.graal.python.builtins.objects.typing.PParamSpec;
import com.oracle.graal.python.builtins.objects.typing.PParamSpecArgs;
import com.oracle.graal.python.builtins.objects.typing.PParamSpecKwargs;
import com.oracle.graal.python.builtins.objects.typing.PTypeAliasType;
import com.oracle.graal.python.builtins.objects.typing.PTypeVar;
import com.oracle.graal.python.builtins.objects.typing.PTypeVarTuple;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.tools.profiler.CPUSampler;

public final class PFactory {
    private PFactory() {
    }

    /*
     * Python objects
     */
    public static PythonObject createPythonObject(Object cls, Shape shape) {
        return new PythonObject(cls, shape);
    }

    public static PythonNativeVoidPtr createNativeVoidPtr(Object obj) {
        return new PythonNativeVoidPtr(obj);
    }

    public static PythonNativeVoidPtr createNativeVoidPtr(Object obj, long nativePtr) {
        return new PythonNativeVoidPtr(obj, nativePtr);
    }

    public static SuperObject createSuperObject(PythonLanguage language) {
        return createSuperObject(PythonBuiltinClassType.Super, PythonBuiltinClassType.Super.getInstanceShape(language));
    }

    public static SuperObject createSuperObject(Object self, Shape shape) {
        return new SuperObject(self, shape);
    }

    public static PInt createInt(PythonLanguage language, long value) {
        return createInt(PythonBuiltinClassType.PInt, language.getBuiltinTypeInstanceShape(PythonBuiltinClassType.PInt), asBigInt(value));
    }

    @TruffleBoundary
    private static BigInteger asBigInt(long value) {
        return BigInteger.valueOf(value);
    }

    public static PInt createInt(PythonLanguage language, BigInteger value) {
        return createInt(PythonBuiltinClassType.PInt, language.getBuiltinTypeInstanceShape(PythonBuiltinClassType.PInt), value);
    }

    public static PInt createInt(Object cls, Shape shape, long value) {
        return createInt(cls, shape, asBigInt(value));
    }

    public static PInt createInt(Object cls, Shape shape, BigInteger value) {
        return new PInt(cls, shape, value);
    }

    public static PFloat createFloat(PythonLanguage language, double value) {
        return createFloat(PythonBuiltinClassType.PFloat, language.getBuiltinTypeInstanceShape(PythonBuiltinClassType.PFloat), value);
    }

    public static PFloat createFloat(Object cls, Shape shape, double value) {
        return new PFloat(cls, shape, value);
    }

    public static PString createString(PythonLanguage language, TruffleString string) {
        return createString(PythonBuiltinClassType.PString, language.getBuiltinTypeInstanceShape(PythonBuiltinClassType.PString), string);
    }

    public static PString createString(Object cls, Shape shape, TruffleString string) {
        return new PString(cls, shape, string);
    }

    public static PBytes createEmptyBytes(PythonLanguage language) {
        if (CompilerDirectives.inInterpreter()) {
            return createBytes(language, PythonUtils.EMPTY_BYTE_ARRAY);
        } else {
            return createBytes(language, new byte[0]);
        }
    }

    public static PBytes createBytes(PythonLanguage language, byte[] array) {
        return createBytes(language, array, array.length);
    }

    public static PBytes createBytes(PythonLanguage language, byte[] array, int length) {
        return createBytes(language, new ByteSequenceStorage(array, length));
    }

    public static PBytes createBytes(PythonLanguage language, SequenceStorage storage) {
        return createBytes(PythonBuiltinClassType.PBytes, PythonBuiltinClassType.PBytes.getInstanceShape(language), storage);
    }

    public static PBytes createBytes(Object cls, Shape shape, byte[] bytes) {
        return createBytes(cls, shape, new ByteSequenceStorage(bytes));
    }

    public static PBytes createBytes(Object cls, Shape shape, SequenceStorage storage) {
        return new PBytes(cls, shape, storage);
    }

    public static PTuple createEmptyTuple(PythonLanguage language) {
        return createTuple(language, EmptySequenceStorage.INSTANCE);
    }

    public static PTuple createEmptyTuple(Object cls, Shape shape) {
        return createTuple(cls, shape, EmptySequenceStorage.INSTANCE);
    }

    public static PTuple createTuple(PythonLanguage language, Object[] objects) {
        return createTuple(language, new ObjectSequenceStorage(objects));
    }

    public static PTuple createTuple(PythonLanguage language, int[] ints) {
        return createTuple(language, new IntSequenceStorage(ints));
    }

    public static PTuple createTuple(PythonLanguage language, SequenceStorage store) {
        return createTuple(PythonBuiltinClassType.PTuple, PythonBuiltinClassType.PTuple.getInstanceShape(language), store);
    }

    public static PTuple createTuple(Object cls, Shape shape, SequenceStorage store) {
        return new PTuple(cls, shape, store);
    }

    public static PTuple createStructSeq(PythonLanguage language, BuiltinTypeDescriptor desc, Object... values) {
        assert desc.inSequence <= values.length && values.length <= desc.fieldNames.length;
        return createTuple(desc.type, desc.type.getInstanceShape(language), new ObjectSequenceStorage(values, desc.inSequence));
    }

    public static PTupleGetter createTupleGetter(PythonLanguage language, int index, Object doc) {
        return createTupleGetter(PythonBuiltinClassType.PTupleGetter, PythonBuiltinClassType.PTupleGetter.getInstanceShape(language), index, doc);
    }

    public static PTupleGetter createTupleGetter(Object cls, Shape shape, int index, Object doc) {
        return new PTupleGetter(cls, shape, index, doc);
    }

    public static PComplex createComplex(Object cls, Shape shape, double real, double imag) {
        return new PComplex(cls, shape, real, imag);
    }

    public static PComplex createComplex(PythonLanguage language, double real, double imag) {
        return createComplex(PythonBuiltinClassType.PComplex, PythonBuiltinClassType.PComplex.getInstanceShape(language), real, imag);
    }

    public static PIntRange createIntRange(PythonLanguage language, int stop) {
        return new PIntRange(language, 0, stop, 1, stop);
    }

    public static PIntRange createIntRange(PythonLanguage language, int start, int stop, int step, int len) {
        return new PIntRange(language, start, stop, step, len);
    }

    public static PBigRange createBigRange(PythonLanguage language, PInt start, PInt stop, PInt step, PInt len) {
        return new PBigRange(language, start, stop, step, len);
    }

    public static PIntSlice createIntSlice(PythonLanguage language, int start, int stop, int step) {
        return new PIntSlice(language, start, stop, step);
    }

    public static PIntSlice createIntSlice(PythonLanguage language, int start, int stop, int step, boolean isStartNone, boolean isStepNone) {
        return new PIntSlice(language, start, stop, step, isStartNone, isStepNone);
    }

    public static PObjectSlice createObjectSlice(PythonLanguage language, Object start, Object stop, Object step) {
        return new PObjectSlice(language, start, stop, step);
    }

    public static PRandom createRandom(Object cls, Shape shape) {
        return new PRandom(cls, shape);
    }

    /*
     * Classes, methods and functions
     */

    /**
     * Only to be used during context creation
     */
    public static PythonModule createPythonModule(TruffleString name) {
        return PythonModule.createInternal(name);
    }

    public static PythonModule createPythonModule(Object cls, Shape shape) {
        return new PythonModule(cls, shape);
    }

    public static PythonClass createPythonClassAndFixupSlots(Node location, PythonLanguage language, TruffleString name, Object base, PythonAbstractClass[] bases) {
        return createPythonClassAndFixupSlots(location, language, PythonBuiltinClassType.PythonClass, PythonBuiltinClassType.PythonClass.getInstanceShape(language), name, base, bases);
    }

    public static PythonClass createPythonClassAndFixupSlots(Node location, PythonLanguage language, Object metaclass, Shape metaclassShape, TruffleString name, Object base,
                    PythonAbstractClass[] bases) {
        PythonClass result = new PythonClass(location, language, metaclass, metaclassShape, name, base, bases);
        // Fixup tp slots
        MroSequenceStorage mro = GetMroStorageNode.executeUncached(result);
        TpSlots.inherit(result, null, mro, true);
        TpSlots.fixupSlotDispatchers(result);
        result.initializeMroShape(language);
        return result;
    }

    public static PythonClass createPythonClass(Node location, PythonLanguage language, Object metaclass, Shape metaclassShape, TruffleString name, boolean invokeMro, Object base,
                    PythonAbstractClass[] bases) {
        return new PythonClass(location, language, metaclass, metaclassShape, name, invokeMro, base, bases);
    }

    public static PMemoryView createMemoryView(PythonLanguage language, PythonContext context, BufferLifecycleManager bufferLifecycleManager, Object buffer, Object owner,
                    int len, boolean readonly, int itemsize, BufferFormat format, TruffleString formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.PMemoryView;
        return new PMemoryView(cls, cls.getInstanceShape(language), context, bufferLifecycleManager, buffer, owner, len, readonly, itemsize, format, formatString,
                        ndim, bufPointer, offset, shape, strides, suboffsets, flags);
    }

    public static PMemoryView createMemoryViewForManagedObject(PythonLanguage language, Object buffer, Object owner, int itemsize, int length, boolean readonly, TruffleString format,
                    TruffleString.CodePointLengthNode lengthNode, TruffleString.CodePointAtIndexNode atIndexNode) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.PMemoryView;
        return new PMemoryView(cls, cls.getInstanceShape(language), null, null, buffer, owner, length, readonly, itemsize,
                        BufferFormat.forMemoryView(format, lengthNode, atIndexNode), format, 1, null, 0, new int[]{length / itemsize}, new int[]{itemsize}, null,
                        PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN);
    }

    public static MemoryViewIterator createMemoryViewIterator(PythonLanguage language, PMemoryView seq, int index, int length, BufferFormat fmt) {
        return new MemoryViewIterator(PythonBuiltinClassType.PMemoryViewIterator, PythonBuiltinClassType.PMemoryViewIterator.getInstanceShape(language), seq, index, length, fmt);
    }

    public static PMethod createMethod(PythonLanguage language, Object cls, Shape shape, Object self, Object function) {
        return new PMethod(cls, shape, self, function);
    }

    public static PMethod createMethod(PythonLanguage language, Object self, Object function) {
        return createMethod(language, PythonBuiltinClassType.PMethod, PythonBuiltinClassType.PMethod.getInstanceShape(language), self, function);
    }

    public static PMethod createBuiltinMethod(PythonLanguage language, Object self, PFunction function) {
        return createMethod(language, PythonBuiltinClassType.PBuiltinFunctionOrMethod, PythonBuiltinClassType.PBuiltinFunctionOrMethod.getInstanceShape(language), self, function);
    }

    public static PBuiltinMethod createBuiltinMethod(Object cls, Shape shape, Object self, PBuiltinFunction function) {
        return new PBuiltinMethod(cls, shape, self, function, null);
    }

    public static PBuiltinMethod createBuiltinMethod(PythonLanguage language, Object self, PBuiltinFunction function, Object classObject) {
        return new PBuiltinMethod(PythonBuiltinClassType.PBuiltinMethod, PythonBuiltinClassType.PBuiltinMethod.getInstanceShape(language), self, function, classObject);
    }

    public static PBuiltinMethod createBuiltinMethod(PythonLanguage language, Object self, PBuiltinFunction function) {
        return createBuiltinMethod(PythonBuiltinClassType.PBuiltinFunctionOrMethod, PythonBuiltinClassType.PBuiltinFunctionOrMethod.getInstanceShape(language), self, function);
    }

    public static PFunction createFunction(PythonLanguage language, TruffleString name, PCode code, PythonObject globals, PCell[] closure) {
        return new PFunction(language, name, name, code, globals, closure);
    }

    public static PFunction createFunction(PythonLanguage language, TruffleString name, TruffleString qualname, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure) {
        return new PFunction(language, name, qualname, code, globals, defaultValues, kwDefaultValues, closure);
    }

    public static PFunction createFunction(PythonLanguage language, TruffleString name, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues, PCell[] closure) {
        return new PFunction(language, name, name, code, globals, defaultValues, kwDefaultValues, closure);
    }

    public static PFunction createFunction(PythonLanguage language, TruffleString name, TruffleString qualname, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure, Assumption codeStableAssumption) {
        return new PFunction(language, name, qualname, code, globals, defaultValues, kwDefaultValues, closure, codeStableAssumption);
    }

    public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString name, Object type, int numDefaults, int flags, RootCallTarget callTarget) {
        return new PBuiltinFunction(PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.PBuiltinFunction.getInstanceShape(language), name, type,
                        PBuiltinFunction.generateDefaults(numDefaults), null, flags, callTarget);
    }

    public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString name, Object type, Object[] defaults, PKeyword[] kw, int flags, RootCallTarget callTarget) {
        return new PBuiltinFunction(PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.PBuiltinFunction.getInstanceShape(language), name, type, defaults, kw, flags, callTarget);
    }

    public static PBuiltinFunction createWrapperDescriptor(PythonLanguage language, TruffleString name, Object type, int numDefaults, int flags, RootCallTarget callTarget, TpSlot slot,
                    PExternalFunctionWrapper slotWrapper) {
        return new PBuiltinFunction(PythonBuiltinClassType.WrapperDescriptor, PythonBuiltinClassType.WrapperDescriptor.getInstanceShape(language), name, type,
                        PBuiltinFunction.generateDefaults(numDefaults), null, flags, callTarget, slot, slotWrapper);
    }

    public static PBuiltinFunction createWrapperDescriptor(PythonLanguage language, TruffleString name, Object type, Object[] defaults, PKeyword[] kw, int flags, RootCallTarget callTarget,
                    TpSlot slot, PExternalFunctionWrapper slotWrapper) {
        return new PBuiltinFunction(PythonBuiltinClassType.WrapperDescriptor, PythonBuiltinClassType.WrapperDescriptor.getInstanceShape(language), name, type, defaults, kw, flags,
                        callTarget, slot, slotWrapper);
    }

    public static PBuiltinMethod createNewWrapper(PythonLanguage language, Object type, Object[] defaults, PKeyword[] kwdefaults, RootCallTarget callTarget, TpSlot slot) {
        PBuiltinFunction func = createWrapperDescriptor(language, T___NEW__, type, defaults, kwdefaults, CExtContext.METH_VARARGS | CExtContext.METH_KEYWORDS, callTarget, slot,
                        PExternalFunctionWrapper.NEW);
        return createBuiltinMethod(language, type, func);
    }

    public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, PBuiltinFunction function, Object klass) {
        PythonBuiltinClassType type = (PythonBuiltinClassType) function.getPythonClass();
        return new PBuiltinFunction(type, type.getInstanceShape(language), function.getName(), klass,
                        function.getDefaults(), function.getKwDefaults(), function.getFlags(), function.getCallTarget(),
                        function.getSlot(), function.getSlotWrapper());
    }

    public static GetSetDescriptor createGetSetDescriptor(PythonLanguage language, Object get, Object set, TruffleString name, Object type, boolean allowsDelete) {
        return new GetSetDescriptor(language, get, set, name, type, allowsDelete);
    }

    public static GetSetDescriptor createMemberDescriptor(PythonLanguage language, Object get, Object set, TruffleString name, Object type) {
        return new GetSetDescriptor(PythonBuiltinClassType.MemberDescriptor, PythonBuiltinClassType.MemberDescriptor.getInstanceShape(language), get, set, name, type, set != null);
    }

    public static IndexedSlotDescriptor createIndexedSlotDescriptor(PythonLanguage language, TruffleString name, int index, Object type) {
        return new IndexedSlotDescriptor(language, name, index, type);
    }

    public static PDecoratedMethod createClassmethod(Object cls, Shape shape) {
        return new PDecoratedMethod(cls, shape);
    }

    public static PDecoratedMethod createClassmethodFromCallableObj(PythonLanguage language, Object callable) {
        return new PDecoratedMethod(PythonBuiltinClassType.PClassmethod, PythonBuiltinClassType.PClassmethod.getInstanceShape(language), callable);
    }

    public static PDecoratedMethod createBuiltinClassmethodFromCallableObj(PythonLanguage language, Object callable) {
        return new PDecoratedMethod(PythonBuiltinClassType.PBuiltinClassMethod, PythonBuiltinClassType.PBuiltinClassMethod.getInstanceShape(language), callable);
    }

    public static PDecoratedMethod createInstancemethod(PythonLanguage language) {
        return createInstancemethod(PythonBuiltinClassType.PInstancemethod, PythonBuiltinClassType.PInstancemethod.getInstanceShape(language));
    }

    public static PDecoratedMethod createInstancemethod(Object cls, Shape shape) {
        return new PDecoratedMethod(cls, shape);
    }

    public static PDecoratedMethod createStaticmethod(PythonLanguage language) {
        return createStaticmethod(PythonBuiltinClassType.PStaticmethod, PythonBuiltinClassType.PStaticmethod.getInstanceShape(language));
    }

    public static PDecoratedMethod createStaticmethod(Object cls, Shape shape) {
        return new PDecoratedMethod(cls, shape);
    }

    public static PDecoratedMethod createStaticmethodFromCallableObj(PythonLanguage language, Object callable) {
        Object func;
        if (callable instanceof PBuiltinFunction builtinFunction) {
            /*
             * CPython's C static methods contain an object of type `builtin_function_or_method`
             * (our PBuiltinMethod). Their self points to their type, but when called they get NULL
             * as the first argument instead.
             */
            func = createBuiltinMethod(language, builtinFunction.getEnclosingType(), builtinFunction);
        } else {
            func = callable;
        }
        return new PDecoratedMethod(PythonBuiltinClassType.PStaticmethod, PythonBuiltinClassType.PStaticmethod.getInstanceShape(language), func);
    }

    /*
     * Lists, sets and dicts
     */

    public static PList createList(PythonLanguage language) {
        return createList(language, EmptySequenceStorage.INSTANCE);
    }

    public static PList createList(PythonLanguage language, Object[] array) {
        return createList(language, new ObjectSequenceStorage(array));
    }

    public static PList createList(PythonLanguage language, SequenceStorage storage) {
        return createList(language, storage, (PList.ListOrigin) null);
    }

    public static PList createList(PythonLanguage language, SequenceStorage storage, PList.ListOrigin origin) {
        return createList(PythonBuiltinClassType.PList, PythonBuiltinClassType.PList.getInstanceShape(language), storage, origin);
    }

    public static PList createList(Object cls, Shape shape) {
        return createList(cls, shape, EmptySequenceStorage.INSTANCE);
    }

    public static PList createList(Object cls, Shape shape, SequenceStorage storage) {
        return createList(cls, shape, storage, null);
    }

    public static PList createList(Object cls, Shape shape, SequenceStorage storage, PList.ListOrigin origin) {
        return new PList(cls, shape, storage, origin);
    }

    public static PSet createSet(PythonLanguage language) {
        return createSet(language, EmptyStorage.INSTANCE);
    }

    public static PSet createSet(PythonLanguage language, HashingStorage storage) {
        return createSet(PythonBuiltinClassType.PSet, PythonBuiltinClassType.PSet.getInstanceShape(language), storage);
    }

    public static PSet createSet(Object cls, Shape shape) {
        return createSet(cls, shape, EmptyStorage.INSTANCE);
    }

    public static PSet createSet(Object cls, Shape shape, HashingStorage storage) {
        return new PSet(cls, shape, storage);
    }

    public static PFrozenSet createFrozenSet(PythonLanguage language) {
        return createFrozenSet(language, EmptyStorage.INSTANCE);
    }

    public static PFrozenSet createFrozenSet(PythonLanguage language, HashingStorage storage) {
        return createFrozenSet(PythonBuiltinClassType.PFrozenSet, PythonBuiltinClassType.PFrozenSet.getInstanceShape(language), storage);
    }

    public static PFrozenSet createFrozenSet(Object cls, Shape shape, HashingStorage storage) {
        return new PFrozenSet(cls, shape, storage);
    }

    public static PDict createDict(PythonLanguage language) {
        return createDict(language, EmptyStorage.INSTANCE);
    }

    public static PDict createDict(PythonLanguage language, PKeyword[] keywords) {
        return createDict(language, new KeywordsStorage(keywords));
    }

    public static PDict createDict(PythonLanguage language, HashingStorage storage) {
        return createDict(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.getInstanceShape(language), storage);
    }

    public static PDict createDict(Object cls, Shape shape, HashingStorage storage) {
        return new PDict(cls, shape, storage);
    }

    public static POrderedDict createOrderedDict(Object cls, Shape shape) {
        return new POrderedDict(cls, shape);
    }

    public static PDictKeysView createOrderedDictKeys(PythonLanguage language, POrderedDict dict) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictKeys;
        return new PDictKeysView(cls, cls.getInstanceShape(language), dict);
    }

    public static PDictValuesView createOrderedDictValues(PythonLanguage language, POrderedDict dict) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictValues;
        return new PDictValuesView(cls, cls.getInstanceShape(language), dict);
    }

    public static PDictItemsView createOrderedDictItems(PythonLanguage language, POrderedDict dict) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictItems;
        return new PDictItemsView(cls, cls.getInstanceShape(language), dict);
    }

    public static POrderedDictIterator createOrderedDictIterator(PythonLanguage language, POrderedDict dict, POrderedDictIterator.IteratorType type, boolean reversed) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictIterator;
        return new POrderedDictIterator(cls, cls.getInstanceShape(language), dict, type, reversed);
    }

    public static PDict createDictFromMap(PythonLanguage language, LinkedHashMap<String, Object> map) {
        return createDict(language, EconomicMapStorage.create(map));
    }

    /**
     * Generic version of {@link #createDictFromMap(PythonLanguage, LinkedHashMap)} that allows any
     * type of keys. Note that this means that unless the keys are known to be side effect free,
     * e.g., builtin types, this may end up calling Python code, so indirect call context should be
     * properly set-up. This helper is meant for Truffle boundary code, in PE code build a storage
     * by setting elements one by one starting from empty storage.
     */
    public static PDict createDictFromMapGeneric(PythonLanguage language, LinkedHashMap<Object, Object> map) {
        return createDict(language, EconomicMapStorage.createGeneric(map));
    }

    public static PDict createDictFixedStorage(PythonLanguage language, PythonObject pythonObject, MroSequenceStorage mroSequenceStorage) {
        return createDict(language, new DynamicObjectStorage(pythonObject, mroSequenceStorage));
    }

    public static PDict createDictFixedStorage(PythonLanguage language, PythonObject pythonObject) {
        return createDict(language, new DynamicObjectStorage(pythonObject));
    }

    public static PSimpleNamespace createSimpleNamespace(PythonLanguage language) {
        return createSimpleNamespace(PythonBuiltinClassType.PSimpleNamespace, PythonBuiltinClassType.PSimpleNamespace.getInstanceShape(language));
    }

    public static PSimpleNamespace createSimpleNamespace(Object cls, Shape shape) {
        return new PSimpleNamespace(cls, shape);
    }

    public static PKeyWrapper createKeyWrapper(PythonLanguage language, Object cmp) {
        return new PKeyWrapper(PythonBuiltinClassType.PKeyWrapper, PythonBuiltinClassType.PKeyWrapper.getInstanceShape(language), cmp);
    }

    public static PPartial createPartial(Object cls, Shape shape, Object function, Object[] args, PDict kwDict) {
        return new PPartial(cls, shape, function, args, kwDict);
    }

    public static LruCacheObject createLruCacheObject(PythonLanguage language, Object cls, Shape shape) {
        return new LruCacheObject(cls, shape);
    }

    public static PDefaultDict createDefaultDict(Object cls, Shape shape) {
        return createDefaultDict(cls, shape, PNone.NONE);
    }

    public static PDefaultDict createDefaultDict(Object cls, Shape shape, Object defaultFactory) {
        return new PDefaultDict(cls, shape, defaultFactory);
    }

    public static PDefaultDict createDefaultDict(PythonLanguage language, Object defaultFactory, HashingStorage storage) {
        return createDefaultDict(PythonBuiltinClassType.PDefaultDict, PythonBuiltinClassType.PDefaultDict.getInstanceShape(language), defaultFactory, storage);
    }

    public static PDefaultDict createDefaultDict(Object cls, Shape shape, Object defaultFactory, HashingStorage storage) {
        return new PDefaultDict(cls, shape, storage, defaultFactory);
    }

    public static PDictView createDictKeysView(PythonLanguage language, PHashingCollection dict) {
        return new PDictKeysView(PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictKeysView.getInstanceShape(language), dict);
    }

    public static PDictView createDictKeysView(PythonLanguage language, Object dict, ForeignHashingStorage foreignHashingStorage) {
        return new PDictKeysView(PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictKeysView.getInstanceShape(language), dict, foreignHashingStorage);
    }

    public static PDictView createDictValuesView(PythonLanguage language, PHashingCollection dict) {
        return new PDictValuesView(PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDictValuesView.getInstanceShape(language), dict);
    }

    public static PDictView createDictValuesView(PythonLanguage language, Object dict, ForeignHashingStorage foreignHashingStorage) {
        return new PDictValuesView(PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDictValuesView.getInstanceShape(language), dict, foreignHashingStorage);
    }

    public static PDictView createDictItemsView(PythonLanguage language, PHashingCollection dict) {
        return new PDictItemsView(PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictItemsView.getInstanceShape(language), dict);
    }

    public static PDictView createDictItemsView(PythonLanguage language, Object dict, ForeignHashingStorage foreignHashingStorage) {
        return new PDictItemsView(PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictItemsView.getInstanceShape(language), dict, foreignHashingStorage);
    }

    /*
     * Special objects: generators, proxies, references, cells
     */

    public static PGenerator createGenerator(PythonLanguage language, PFunction function, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return PGenerator.create(language, function, rootNode, callTargets, arguments, PythonBuiltinClassType.PGenerator);
    }

    public static PGenerator createGenerator(PythonLanguage language, PFunction function, PBytecodeDSLRootNode rootNode, Object[] arguments, ContinuationRootNode continuationRootNode,
                    MaterializedFrame continuationFrame) {
        return PGenerator.create(language, function, rootNode, arguments, PythonBuiltinClassType.PGenerator, continuationRootNode, continuationFrame);
    }

    public static PGenerator createIterableCoroutine(PythonLanguage language, PFunction function, PBytecodeRootNode rootNode, RootCallTarget[] callTargets,
                    Object[] arguments) {
        return PGenerator.create(language, function, rootNode, callTargets, arguments, PythonBuiltinClassType.PGenerator, true);
    }

    public static PGenerator createIterableCoroutine(PythonLanguage language, PFunction function, PBytecodeDSLRootNode rootNode,
                    Object[] arguments, ContinuationRootNode continuationRootNode, MaterializedFrame continuationFrame) {
        return PGenerator.create(language, function, rootNode, arguments, PythonBuiltinClassType.PGenerator, true, continuationRootNode, continuationFrame);
    }

    public static PGenerator createCoroutine(PythonLanguage language, PFunction function, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return PGenerator.create(language, function, rootNode, callTargets, arguments, PythonBuiltinClassType.PCoroutine);
    }

    public static PGenerator createCoroutine(PythonLanguage language, PFunction function, PBytecodeDSLRootNode rootNode, Object[] arguments, ContinuationRootNode continuationRootNode,
                    MaterializedFrame continuationFrame) {
        return PGenerator.create(language, function, rootNode, arguments, PythonBuiltinClassType.PCoroutine, continuationRootNode, continuationFrame);
    }

    public static PCoroutineWrapper createCoroutineWrapper(PythonLanguage language, PGenerator generator) {
        return new PCoroutineWrapper(language, generator);
    }

    public static PAsyncGen createAsyncGenerator(PythonLanguage language, PFunction function, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return PAsyncGen.create(language, function, rootNode, callTargets, arguments);
    }

    public static PANextAwaitable createANextAwaitable(PythonLanguage language, Object wrapped, Object defaultValue) {
        return new PANextAwaitable(PythonBuiltinClassType.PAnextAwaitable, PythonBuiltinClassType.PAnextAwaitable.getInstanceShape(language), wrapped, defaultValue);
    }

    public static PMappingproxy createMappingproxy(PythonLanguage language, Object object) {
        return createMappingproxy(PythonBuiltinClassType.PMappingproxy, PythonBuiltinClassType.PMappingproxy.getInstanceShape(language), object);
    }

    public static PMappingproxy createMappingproxy(Object cls, Shape shape, Object object) {
        return new PMappingproxy(cls, shape, object);
    }

    public static PReferenceType createReferenceType(PythonLanguage language, Object object) {
        return createReferenceType(language, PythonBuiltinClassType.PReferenceType, PythonBuiltinClassType.PReferenceType.getInstanceShape(language), object);
    }

    public static PReferenceType createReferenceType(PythonLanguage language, Object cls, Shape shape, Object object) {
        return createReferenceType(language, cls, shape, object, null, null);
    }

    public static PReferenceType createReferenceType(PythonLanguage language, Object cls, Shape shape, Object object, Object callback, ReferenceQueue<Object> queue) {
        return new PReferenceType(cls, shape, object, callback, queue);
    }

    public static PCell createCell(Assumption effectivelyFinal) {
        return new PCell(effectivelyFinal);
    }

    /*
     * Frames, traces and exceptions
     */

    public static PFrame createPFrame(PythonLanguage language, PFrame.Reference frameInfo, Node location, boolean hasCustomLocals) {
        return new PFrame(language, frameInfo, location, hasCustomLocals);
    }

    public static PFrame createPFrame(PythonLanguage language, Object threadState, PCode code, PythonObject globals, Object localsDict) {
        return new PFrame(language, threadState, code, globals, localsDict);
    }

    public static PTraceback createTraceback(PythonLanguage language, PFrame frame, int lineno, PTraceback next) {
        return new PTraceback(language, frame, lineno, -1, next);
    }

    public static PTraceback createTracebackWithLasti(PythonLanguage language, PFrame frame, int lineno, int lasti, PTraceback next) {
        return new PTraceback(language, frame, lineno, lasti, next);
    }

    public static PTraceback createTraceback(PythonLanguage language, LazyTraceback tb) {
        return new PTraceback(language, tb);
    }

    public static PBaseException createBaseException(Object cls, Shape shape, PTuple args) {
        return createBaseException(cls, shape, null, args);
    }

    public static PBaseException createBaseException(PythonLanguage language, PythonBuiltinClassType cls, PTuple args) {
        return createBaseException(cls, cls.getInstanceShape(language), null, args);
    }

    public static PBaseException createBaseException(PythonLanguage language, PythonBuiltinClassType type, Object[] data, PTuple args) {
        return createBaseException(type, type.getInstanceShape(language), data, args);
    }

    public static PBaseException createBaseException(Object cls, Shape shape, Object[] data, PTuple args) {
        return new PBaseException(cls, shape, data, args);
    }

    /*
     * Note: we use this method to convert a Java StackOverflowError into a Python RecursionError.
     * At the time when this is done, some Java stack frames were already unwinded but there is no
     * guarantee on how many. Therefore, it is important that this method is simple. In particular,
     * do not add calls if that can be avoided.
     */
    public static PBaseException createBaseException(Object cls, Shape shape, TruffleString format, Object[] formatArgs) {
        return createBaseException(cls, shape, null, format, formatArgs);
    }

    public static PBaseException createBaseException(PythonLanguage language, PythonBuiltinClassType type, TruffleString format, Object[] formatArgs) {
        return createBaseException(type, type.getInstanceShape(language), null, format, formatArgs);
    }

    public static PBaseException createBaseException(PythonLanguage language, PythonBuiltinClassType type, Object[] data, TruffleString format, Object[] formatArgs) {
        return createBaseException(type, type.getInstanceShape(language), data, format, formatArgs);
    }

    public static PBaseException createBaseException(Object cls, Shape shape, Object[] data, TruffleString format, Object[] formatArgs) {
        assert format != null;
        return new PBaseException(cls, shape, data, format, formatArgs);
    }

    public static PBaseException createBaseException(Object cls, Shape shape) {
        return new PBaseException(cls, shape, null);
    }

    public static PBaseException createBaseException(PythonLanguage language, PythonBuiltinClassType type) {
        return new PBaseException(type, type.getInstanceShape(language), null);
    }

    public static PBaseException createBaseException(PythonLanguage language, PythonBuiltinClassType type, TruffleString format) {
        return new PBaseException(type, type.getInstanceShape(language), null, format, EMPTY_OBJECT_ARRAY);
    }

    public static PBaseExceptionGroup createBaseExceptionGroup(PythonLanguage language, Object cls, Shape shape, TruffleString message, Object[] exceptions, Object[] args) {
        return new PBaseExceptionGroup(cls, shape, message, exceptions, createTuple(language, args));
    }

    /*
     * Arrays
     */

    public static PArray createArray(Object cls, Shape shape, TruffleString formatString, BufferFormat format) {
        assert format != null;
        return new PArray(cls, shape, formatString, format);
    }

    public static PArray createArray(PythonLanguage language, TruffleString formatString, BufferFormat format, int length) throws OverflowException {
        return createArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.getInstanceShape(language), formatString, format, length);
    }

    public static PArray createArray(Object cls, Shape shape, TruffleString formatString, BufferFormat format, int length) throws OverflowException {
        assert format != null;
        int byteSize = PythonUtils.multiplyExact(length, format.bytesize);
        return new PArray(cls, shape, formatString, format, byteSize);
    }

    public static PByteArray createByteArray(PythonLanguage language, byte[] array) {
        return createByteArray(language, array, array.length);
    }

    public static PByteArray createByteArray(Object cls, Shape shape, byte[] array) {
        return createByteArray(cls, shape, array, array.length);
    }

    public static PByteArray createByteArray(PythonLanguage language, byte[] array, int length) {
        return createByteArray(language, new ByteSequenceStorage(array, length));
    }

    public static PByteArray createByteArray(Object cls, Shape shape, byte[] array, int length) {
        return createByteArray(cls, shape, new ByteSequenceStorage(array, length));
    }

    public static PByteArray createByteArray(PythonLanguage language, SequenceStorage storage) {
        return createByteArray(PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PByteArray.getInstanceShape(language), storage);
    }

    public static PByteArray createByteArray(Object cls, Shape shape, SequenceStorage storage) {
        return new PByteArray(cls, shape, storage);
    }

    /*
     * Iterators
     */

    public static PStringIterator createStringIterator(PythonLanguage language, TruffleString str) {
        return new PStringIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), str);
    }

    public static PStringReverseIterator createStringReverseIterator(Object cls, Shape shape, TruffleString str) {
        return new PStringReverseIterator(cls, shape, str);
    }

    public static PIntegerSequenceIterator createIntegerSequenceIterator(PythonLanguage language, IntSequenceStorage storage, Object list) {
        return new PIntegerSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), storage, list);
    }

    public static PLongSequenceIterator createLongSequenceIterator(PythonLanguage language, LongSequenceStorage storage, Object list) {
        return new PLongSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), storage, list);
    }

    public static PDoubleSequenceIterator createDoubleSequenceIterator(PythonLanguage language, DoubleSequenceStorage storage, Object list) {
        return new PDoubleSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), storage, list);
    }

    public static PObjectSequenceIterator createObjectSequenceIterator(PythonLanguage language, ObjectSequenceStorage storage, Object list) {
        return new PObjectSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), storage, list);
    }

    public static PSequenceIterator createSequenceIterator(PythonLanguage language, Object sequence) {
        return new PSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), sequence);
    }

    public static PSequenceReverseIterator createSequenceReverseIterator(PythonLanguage language, Object sequence, int lengthHint) {
        return createSequenceReverseIterator(PythonBuiltinClassType.PReverseIterator, PythonBuiltinClassType.PReverseIterator.getInstanceShape(language), sequence, lengthHint);
    }

    public static PSequenceReverseIterator createSequenceReverseIterator(Object cls, Shape shape, Object sequence, int lengthHint) {
        return new PSequenceReverseIterator(cls, shape, sequence, lengthHint);
    }

    public static PIntRangeIterator createIntRangeIterator(PythonLanguage language, PIntRange fastRange) {
        return createIntRangeIterator(language, fastRange.getIntStart(), fastRange.getIntStop(), fastRange.getIntStep(), fastRange.getIntLength());
    }

    public static PIntRangeIterator createIntRangeIterator(PythonLanguage language, int start, int stop, int step, int len) {
        return new PIntRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), start, stop, step, len);
    }

    public static PBigRangeIterator createBigRangeIterator(PythonLanguage language, PInt start, PInt stop, PInt step, PInt len) {
        return new PBigRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), start, stop, step, len);
    }

    public static PBigRangeIterator createBigRangeIterator(PythonLanguage language, PBigRange longRange) {
        return createBigRangeIterator(language, longRange.getPIntStart(), longRange.getPIntStop(), longRange.getPIntStep(), longRange.getPIntLength());
    }

    public static PBigRangeIterator createBigRangeIterator(PythonLanguage language, BigInteger start, BigInteger stop, BigInteger step, BigInteger len) {
        return createBigRangeIterator(language, createInt(language, start), createInt(language, stop), createInt(language, step), createInt(language, len));
    }

    public static PArrayIterator createArrayIterator(PythonLanguage language, PArray array) {
        return new PArrayIterator(PythonBuiltinClassType.PArrayIterator, PythonBuiltinClassType.PArrayIterator.getInstanceShape(language), array);
    }

    public static PBaseSetIterator createBaseSetIterator(PythonLanguage language, PBaseSet set, HashingStorageNodes.HashingStorageIterator iterator, int initialSize) {
        return new PBaseSetIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(language), set, iterator, initialSize);
    }

    public static PDictItemIterator createDictItemIterator(PythonLanguage language, HashingStorageNodes.HashingStorageIterator iterator, HashingStorage hashingStorage, int initialSize) {
        return new PDictItemIterator(PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictItemIterator.getInstanceShape(language), iterator, hashingStorage,
                        initialSize);
    }

    public static PDictKeyIterator createDictKeyIterator(PythonLanguage language, HashingStorageNodes.HashingStorageIterator iterator, HashingStorage hashingStorage, int initialSize) {
        return new PDictKeyIterator(PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictKeyIterator.getInstanceShape(language), iterator, hashingStorage, initialSize);
    }

    public static PDictValueIterator createDictValueIterator(PythonLanguage language, HashingStorageNodes.HashingStorageIterator iterator, HashingStorage hashingStorage, int initialSize) {
        return new PDictValueIterator(PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictValueIterator.getInstanceShape(language), iterator, hashingStorage, initialSize);
    }

    public static Object createSentinelIterator(PythonLanguage language, Object callable, Object sentinel) {
        return new PSentinelIterator(PythonBuiltinClassType.PSentinelIterator, PythonBuiltinClassType.PSentinelIterator.getInstanceShape(language), callable, sentinel);
    }

    public static PEnumerate createEnumerate(Object cls, Shape shape, Object iterator, long start) {
        return new PEnumerate(cls, shape, iterator, start);
    }

    public static PEnumerate createEnumerate(Object cls, Shape shape, Object iterator, PInt start) {
        return new PEnumerate(cls, shape, iterator, start);
    }

    public static PMap createMap(Object cls, Shape shape) {
        return new PMap(cls, shape);
    }

    public static PFilter createFilter(Object cls, Shape shape) {
        return new PFilter(cls, shape);
    }

    public static PZip createZip(Object cls, Shape shape, Object[] iterables, boolean strict) {
        return new PZip(cls, shape, iterables, strict);
    }

    public static PCode createCode(PythonLanguage language, RootCallTarget ct) {
        return new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(language), ct);
    }

    public static PCode createCode(PythonLanguage language, RootCallTarget ct, int flags, int firstlineno, byte[] linetable, TruffleString filename) {
        return new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(language), ct, flags, firstlineno, linetable, filename);
    }

    public static PCode createCode(PythonLanguage language, RootCallTarget callTarget, Signature signature, BytecodeCodeUnit codeUnit) {
        return new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(language), callTarget, signature, codeUnit);
    }

    public static PCode createCode(PythonLanguage language, RootCallTarget callTarget, Signature signature, BytecodeDSLCodeUnit codeUnit) {
        return new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(language), callTarget, signature, codeUnit);
    }

    public static PCode createCode(PythonLanguage language, RootCallTarget callTarget, Signature signature, int nlocals,
                    int stacksize, int flags, Object[] constants,
                    TruffleString[] names, TruffleString[] varnames,
                    TruffleString[] freevars, TruffleString[] cellvars,
                    TruffleString filename, TruffleString name, TruffleString qualname,
                    int firstlineno, byte[] linetable) {
        return new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(language), callTarget, signature,
                        nlocals, stacksize, flags, constants, names, varnames, freevars, cellvars,
                        filename, name, qualname, firstlineno, linetable);
    }

    /*
     * Socket
     */

    public static PSocket createSocket(Object cls, Shape shape) {
        return new PSocket(cls, shape);
    }

    /*
     * Threading
     */

    public static PThreadLocal createThreadLocal(Object cls, Shape shape, Object[] args, PKeyword[] kwArgs) {
        return new PThreadLocal(cls, shape, args, kwArgs);
    }

    public static PLock createLock(PythonLanguage language) {
        return createLock(PythonBuiltinClassType.PLock, PythonBuiltinClassType.PLock.getInstanceShape(language));
    }

    public static PLock createLock(Object cls, Shape shape) {
        return new PLock(cls, shape);
    }

    public static PRLock createRLock(Object cls, Shape shape) {
        return new PRLock(cls, shape);
    }

    public static PSemLock createSemLock(Object cls, Shape shape, long handle, int kind, int maxValue, TruffleString name) {
        return new PSemLock(cls, shape, handle, kind, maxValue, name);
    }

    public static PGraalPySemLock createGraalPySemLock(PythonLanguage language, TruffleString name, int kind, Semaphore sharedSemaphore) {
        return createGraalPySemLock(PythonBuiltinClassType.PGraalPySemLock, PythonBuiltinClassType.PGraalPySemLock.getInstanceShape(language), name, kind, sharedSemaphore);
    }

    public static PGraalPySemLock createGraalPySemLock(Object cls, Shape shape, TruffleString name, int kind, Semaphore sharedSemaphore) {
        return new PGraalPySemLock(cls, shape, name, kind, sharedSemaphore);
    }

    public static PScandirIterator createScandirIterator(PythonLanguage language, PythonContext context, Object dirStream, PosixFileHandle path, boolean needsRewind) {
        return new PScandirIterator(PythonBuiltinClassType.PScandirIterator, PythonBuiltinClassType.PScandirIterator.getInstanceShape(language), context, dirStream, path, needsRewind);
    }

    public static PDirEntry createDirEntry(PythonLanguage language, Object dirEntryData, PosixFileHandle path) {
        return new PDirEntry(PythonBuiltinClassType.PDirEntry, PythonBuiltinClassType.PDirEntry.getInstanceShape(language), dirEntryData, path);
    }

    public static PEncodingMap createEncodingMap(PythonLanguage language, int count2, int count3, byte[] level1, byte[] level23) {
        return new PEncodingMap(PythonBuiltinClassType.PEncodingMap, PythonBuiltinClassType.PEncodingMap.getInstanceShape(language), count2, count3, level1, level23);
    }

    public static PMMap createMMap(PythonContext context, Object cls, Shape shape, Object mmapHandle, int fd, long length, int access) {
        return new PMMap(cls, shape, context, mmapHandle, fd, length, access);
    }

    public static BZ2Object.BZ2Compressor createBZ2Compressor(PythonLanguage language) {
        return createBZ2Compressor(PythonBuiltinClassType.BZ2Compressor, PythonBuiltinClassType.BZ2Compressor.getInstanceShape(language));
    }

    public static BZ2Object.BZ2Compressor createBZ2Compressor(Object cls, Shape shape) {
        return BZ2Object.createCompressor(cls, shape);
    }

    public static BZ2Object.BZ2Decompressor createBZ2Decompressor(PythonLanguage language) {
        return createBZ2Decompressor(PythonBuiltinClassType.BZ2Decompressor, PythonBuiltinClassType.BZ2Decompressor.getInstanceShape(language));
    }

    public static BZ2Object.BZ2Decompressor createBZ2Decompressor(Object cls, Shape shape) {
        return BZ2Object.createDecompressor(cls, shape);
    }

    public static JavaCompress createJavaZLibCompObjectCompress(PythonLanguage language, int level, int wbits, int strategy, byte[] zdict) {
        return new JavaCompress(PythonBuiltinClassType.ZlibCompress, PythonBuiltinClassType.ZlibCompress.getInstanceShape(language), level, wbits, strategy, zdict);
    }

    public static JavaDecompress createJavaZLibCompObjectDecompress(PythonLanguage language, int wbits, byte[] zdict) {
        return new JavaDecompress(PythonBuiltinClassType.ZlibDecompress, PythonBuiltinClassType.ZlibDecompress.getInstanceShape(language), wbits, zdict);
    }

    public static ZLibCompObject createNativeZLibCompObjectCompress(PythonLanguage language, Object zst, NFIZlibSupport zlibSupport) {
        return createNativeZLibCompObject(PythonBuiltinClassType.ZlibCompress, PythonBuiltinClassType.ZlibCompress.getInstanceShape(language), zst, zlibSupport);
    }

    public static ZLibCompObject createNativeZLibCompObjectDecompress(PythonLanguage language, Object zst, NFIZlibSupport zlibSupport) {
        return createNativeZLibCompObject(PythonBuiltinClassType.ZlibDecompress, PythonBuiltinClassType.ZlibDecompress.getInstanceShape(language), zst, zlibSupport);
    }

    public static ZLibCompObject createNativeZLibCompObject(Object cls, Shape shape, Object zst, NFIZlibSupport zlibSupport) {
        return new NativeZlibCompObject(cls, shape, zst, zlibSupport);
    }

    public static ZlibDecompressorObject createJavaZlibDecompressorObject(PythonLanguage language, int wbits, byte[] zdict) {
        return ZlibDecompressorObject.createJava(PythonBuiltinClassType.ZlibDecompressor, PythonBuiltinClassType.ZlibDecompressor.getInstanceShape(language), wbits, zdict);
    }

    public static ZlibDecompressorObject createNativeZlibDecompressorObject(PythonLanguage language, Object zst, NFIZlibSupport zlibSupport) {
        return ZlibDecompressorObject.createNative(PythonBuiltinClassType.ZlibDecompressor, PythonBuiltinClassType.ZlibDecompressor.getInstanceShape(language), zst, zlibSupport);
    }

    public static LZMAObject.LZMADecompressor createLZMADecompressor(Object cls, Shape shape, boolean isNative) {
        return LZMAObject.createDecompressor(cls, shape, isNative);
    }

    public static LZMAObject.LZMACompressor createLZMACompressor(Object cls, Shape shape, boolean isNative) {
        return LZMAObject.createCompressor(cls, shape, isNative);
    }

    public static CSVReader createCSVReader(PythonLanguage language, Object inputIter, CSVDialect dialect) {
        return createCSVReader(PythonBuiltinClassType.CSVReader, PythonBuiltinClassType.CSVReader.getInstanceShape(language), inputIter, dialect);
    }

    public static CSVReader createCSVReader(Object cls, Shape shape, Object inputIter, CSVDialect dialect) {
        return new CSVReader(cls, shape, inputIter, dialect);
    }

    public static CSVWriter createCSVWriter(PythonLanguage language, Object write, CSVDialect dialect) {
        return createCSVWriter(PythonBuiltinClassType.CSVWriter, PythonBuiltinClassType.CSVWriter.getInstanceShape(language), write, dialect);
    }

    public static CSVWriter createCSVWriter(Object cls, Shape shape, Object write, CSVDialect dialect) {
        return new CSVWriter(cls, shape, write, dialect);
    }

    public static CSVDialect createCSVDialect(Object cls, Shape shape, TruffleString delimiter, int delimiterCodePoint, boolean doubleQuote, TruffleString escapeChar,
                    int escapeCharCodePoint,
                    TruffleString lineTerminator, TruffleString quoteChar, int quoteCharCodePoint, QuoteStyle quoting, boolean skipInitialSpace, boolean strict) {
        return new CSVDialect(cls, shape, delimiter, delimiterCodePoint, doubleQuote, escapeChar, escapeCharCodePoint, lineTerminator, quoteChar, quoteCharCodePoint, quoting,
                        skipInitialSpace, strict);
    }

    public static PFileIO createFileIO(PythonLanguage language) {
        return createFileIO(PythonBuiltinClassType.PFileIO, PythonBuiltinClassType.PFileIO.getInstanceShape(language));
    }

    public static PFileIO createFileIO(Object cls, Shape shape) {
        return new PFileIO(cls, shape);
    }

    public static PChain createChain(PythonLanguage language) {
        return createChain(PythonBuiltinClassType.PChain, PythonBuiltinClassType.PChain.getInstanceShape(language));
    }

    public static PChain createChain(Object cls, Shape shape) {
        return new PChain(cls, shape);
    }

    public static PCount createCount(Object cls, Shape shape) {
        return new PCount(cls, shape);
    }

    public static PIslice createIslice(Object cls, Shape shape) {
        return new PIslice(cls, shape);
    }

    public static PPairwise createPairwise(Object cls, Shape shape) {
        return new PPairwise(cls, shape);
    }

    public static PPermutations createPermutations(Object cls, Shape shape) {
        return new PPermutations(cls, shape);
    }

    public static PProduct createProduct(Object cls, Shape shape) {
        return new PProduct(cls, shape);
    }

    public static PRepeat createRepeat(Object cls, Shape shape) {
        return new PRepeat(cls, shape);
    }

    public static PAccumulate createAccumulate(PythonLanguage language) {
        return createAccumulate(PythonBuiltinClassType.PAccumulate, PythonBuiltinClassType.PAccumulate.getInstanceShape(language));
    }

    public static PAccumulate createAccumulate(Object cls, Shape shape) {
        return new PAccumulate(cls, shape);
    }

    public static PDropwhile createDropwhile(Object cls, Shape shape) {
        return new PDropwhile(cls, shape);
    }

    public static PCombinations createCombinations(Object cls, Shape shape) {
        return new PCombinations(cls, shape);
    }

    public static PCombinationsWithReplacement createCombinationsWithReplacement(Object cls, Shape shape) {
        return new PCombinationsWithReplacement(cls, shape);
    }

    public static PCompress createCompress(Object cls, Shape shape) {
        return new PCompress(cls, shape);
    }

    public static PCycle createCycle(Object cls, Shape shape) {
        return new PCycle(cls, shape);
    }

    public static PFilterfalse createFilterfalse(Object cls, Shape shape) {
        return new PFilterfalse(cls, shape);
    }

    public static PGroupBy createGroupBy(Object cls, Shape shape) {
        return new PGroupBy(cls, shape);
    }

    public static PGrouper createGrouper(PythonLanguage language, PGroupBy parent, Object tgtKey) {
        return new PGrouper(parent, tgtKey, PythonBuiltinClassType.PGrouper, PythonBuiltinClassType.PGrouper.getInstanceShape(language));
    }

    public static PTee createTee(PythonLanguage language, PTeeDataObject dataObj, int index) {
        return new PTee(dataObj, index, PythonBuiltinClassType.PTee, PythonBuiltinClassType.PTee.getInstanceShape(language));
    }

    public static PStarmap createStarmap(Object cls, Shape shape) {
        return new PStarmap(cls, shape);
    }

    public static PTakewhile createTakewhile(Object cls, Shape shape) {
        return new PTakewhile(cls, shape);
    }

    public static PTeeDataObject createTeeDataObject(PythonLanguage language) {
        return new PTeeDataObject(PythonBuiltinClassType.PTeeDataObject, PythonBuiltinClassType.PTeeDataObject.getInstanceShape(language));
    }

    public static PTeeDataObject createTeeDataObject(PythonLanguage language, Object it) {
        return new PTeeDataObject(it, PythonBuiltinClassType.PTeeDataObject, PythonBuiltinClassType.PTeeDataObject.getInstanceShape(language));
    }

    public static PZipLongest createZipLongest(Object cls, Shape shape) {
        return new PZipLongest(cls, shape);
    }

    public static PBatched createBatched(Object cls, Shape shape) {
        return new PBatched(cls, shape);
    }

    public static PTextIO createTextIO(PythonLanguage language) {
        return createTextIO(PythonBuiltinClassType.PTextIOWrapper, PythonBuiltinClassType.PTextIOWrapper.getInstanceShape(language));
    }

    public static PTextIO createTextIO(Object cls, Shape shape) {
        return new PTextIO(cls, shape);
    }

    public static PStringIO createStringIO(Object cls, Shape shape) {
        return new PStringIO(cls, shape);
    }

    public static PBytesIO createBytesIO(Object cls, Shape shape) {
        return new PBytesIO(cls, shape);
    }

    public static PBytesIOBuffer createBytesIOBuf(PythonLanguage language, PBytesIO source) {
        return createBytesIOBuf(PythonBuiltinClassType.PBytesIOBuf, PythonBuiltinClassType.PBytesIOBuf.getInstanceShape(language), source);
    }

    public static PBytesIOBuffer createBytesIOBuf(Object cls, Shape shape, PBytesIO source) {
        return new PBytesIOBuffer(cls, shape, source);
    }

    public static PNLDecoder createNLDecoder(PythonLanguage language) {
        return createNLDecoder(PythonBuiltinClassType.PIncrementalNewlineDecoder, PythonBuiltinClassType.PIncrementalNewlineDecoder.getInstanceShape(language));
    }

    public static PNLDecoder createNLDecoder(Object cls, Shape shape) {
        return new PNLDecoder(cls, shape);
    }

    public static PBuffered createBufferedReader(PythonLanguage language) {
        return createBufferedReader(PythonBuiltinClassType.PBufferedReader, PythonBuiltinClassType.PBufferedReader.getInstanceShape(language));
    }

    public static PBuffered createBufferedReader(Object cls, Shape shape) {
        return new PBuffered(cls, shape, true, false);
    }

    public static PBuffered createBufferedWriter(PythonLanguage language) {
        return createBufferedWriter(PythonBuiltinClassType.PBufferedWriter, PythonBuiltinClassType.PBufferedWriter.getInstanceShape(language));
    }

    public static PBuffered createBufferedWriter(Object cls, Shape shape) {
        return new PBuffered(cls, shape, false, true);
    }

    public static PBuffered createBufferedRandom(PythonLanguage language) {
        return createBufferedRandom(PythonBuiltinClassType.PBufferedRandom, PythonBuiltinClassType.PBufferedRandom.getInstanceShape(language));
    }

    public static PBuffered createBufferedRandom(Object cls, Shape shape) {
        return new PBuffered(cls, shape, true, true);
    }

    public static PRWPair createRWPair(Object cls, Shape shape) {
        return new PRWPair(cls, shape);
    }

    public static PSSLContext createSSLContext(Object cls, Shape shape, SSLMethod method, int verifyFlags, boolean checkHostname, int verifyMode, SSLContext context) {
        return new PSSLContext(cls, shape, method, verifyFlags, checkHostname, verifyMode, context);
    }

    public static PSSLSocket createSSLSocket(PythonLanguage language, PSSLContext context, SSLEngine engine, PSocket socket) {
        return createSSLSocket(language, PythonBuiltinClassType.PSSLSocket, PythonBuiltinClassType.PSSLSocket.getInstanceShape(language), context, engine, socket);
    }

    public static PSSLSocket createSSLSocket(PythonLanguage language, Object cls, Shape shape, PSSLContext context, SSLEngine engine, PSocket socket) {
        return new PSSLSocket(cls, shape, context, engine, socket, createMemoryBIO(language), createMemoryBIO(language), createMemoryBIO(language));
    }

    public static PSSLSocket createSSLSocket(PythonLanguage language, PSSLContext context, SSLEngine engine, PMemoryBIO inbound, PMemoryBIO outbound) {
        return createSSLSocket(language, PythonBuiltinClassType.PSSLSocket, PythonBuiltinClassType.PSSLSocket.getInstanceShape(language), context, engine, inbound, outbound);
    }

    public static PSSLSocket createSSLSocket(PythonLanguage language, Object cls, Shape shape, PSSLContext context, SSLEngine engine, PMemoryBIO inbound, PMemoryBIO outbound) {
        return new PSSLSocket(cls, shape, context, engine, null, inbound, outbound, createMemoryBIO(language));
    }

    public static PMemoryBIO createMemoryBIO(Object cls, Shape shape) {
        return new PMemoryBIO(cls, shape);
    }

    public static PMemoryBIO createMemoryBIO(PythonLanguage language) {
        return new PMemoryBIO(PythonBuiltinClassType.PMemoryBIO, PythonBuiltinClassType.PMemoryBIO.getInstanceShape(language));
    }

    public static PProperty createProperty(PythonLanguage language) {
        return new PProperty(PythonBuiltinClassType.PProperty, PythonBuiltinClassType.PProperty.getInstanceShape(language));
    }

    public static PProperty createProperty(Object cls, Shape shape) {
        return new PProperty(cls, shape);
    }

    // JSON
    // (not created on fast path, thus TruffleBoundary)

    @TruffleBoundary
    public static PJSONScanner createJSONScanner(Object cls, Shape shape, boolean strict, Object objectHook, Object objectPairsHook, Object parseFloat, Object parseInt,
                    Object parseConstant) {
        return new PJSONScanner(cls, shape, strict, objectHook, objectPairsHook, parseFloat, parseInt, parseConstant);
    }

    @TruffleBoundary
    public static PJSONEncoder createJSONEncoder(Object cls, Shape shape, Object markers, Object defaultFn, Object encoder, Object indent, TruffleString keySeparator,
                    TruffleString itemSeparator,
                    boolean sortKeys, boolean skipKeys, boolean allowNan, FastEncode fastEncode) {
        return new PJSONEncoder(cls, shape, markers, defaultFn, encoder, indent, keySeparator, itemSeparator, sortKeys, skipKeys, allowNan, fastEncode);
    }

    public static PDeque createDeque(PythonLanguage language) {
        return new PDeque(PythonBuiltinClassType.PDeque, PythonBuiltinClassType.PDeque.getInstanceShape(language));
    }

    public static PDeque createDeque(Object cls, Shape shape) {
        return new PDeque(cls, shape);
    }

    public static PDequeIter createDequeIter(PythonLanguage language, PDeque deque) {
        return new PDequeIter(PythonBuiltinClassType.PDequeIter, PythonBuiltinClassType.PDequeIter.getInstanceShape(language), deque, false);
    }

    public static PDequeIter createDequeRevIter(PythonLanguage language, PDeque deque) {
        return new PDequeIter(PythonBuiltinClassType.PDequeRevIter, PythonBuiltinClassType.PDequeRevIter.getInstanceShape(language), deque, true);
    }

    public static PSimpleQueue createSimpleQueue(Object cls, Shape shape) {
        return new PSimpleQueue(cls, shape);
    }

    public static PContextVar createContextVar(PythonLanguage language, TruffleString name, Object def) {
        return new PContextVar(PythonBuiltinClassType.ContextVar, PythonBuiltinClassType.ContextVar.getInstanceShape(language), name, def);
    }

    public static PContextVarsContext createContextVarsContext(PythonLanguage language) {
        return new PContextVarsContext(PythonBuiltinClassType.ContextVarsContext, PythonBuiltinClassType.ContextVarsContext.getInstanceShape(language));
    }

    public static PContextIterator createContextIterator(PythonLanguage language, PContextVarsContext ctx, PContextIterator.ItemKind kind) {
        return new PContextIterator(PythonBuiltinClassType.ContextIterator, PythonBuiltinClassType.ContextIterator.getInstanceShape(language), ctx, kind);
    }

    public static PContextVarsContext copyContextVarsContext(PythonLanguage language, PContextVarsContext original) {
        return new PContextVarsContext(original, PythonBuiltinClassType.ContextVarsContext, PythonBuiltinClassType.ContextVarsContext.getInstanceShape(language));
    }

    public static PContextVarsToken createContextVarsToken(PythonLanguage language, PContextVar var, Object oldValue) {
        return new PContextVarsToken(var, oldValue, PythonBuiltinClassType.ContextVarsToken, PythonBuiltinClassType.ContextVarsToken.getInstanceShape(language));
    }

    public static PGenericAlias createGenericAlias(PythonLanguage language, Object cls, Shape shape, Object origin, Object arguments, boolean starred) {
        PTuple argumentsTuple;
        if (arguments instanceof PTuple) {
            argumentsTuple = (PTuple) arguments;
        } else {
            argumentsTuple = createTuple(language, new Object[]{arguments});
        }
        return new PGenericAlias(cls, shape, origin, argumentsTuple, starred);
    }

    public static PGenericAlias createGenericAlias(PythonLanguage language, Object origin, Object arguments, boolean starred) {
        return createGenericAlias(language, PythonBuiltinClassType.PGenericAlias, PythonBuiltinClassType.PGenericAlias.getInstanceShape(language), origin, arguments, starred);
    }

    public static PGenericAlias createGenericAlias(PythonLanguage language, Object origin, Object arguments) {
        return createGenericAlias(language, origin, arguments, false);
    }

    public static PGenericAliasIterator createGenericAliasIterator(PythonLanguage language, PGenericAlias object) {
        return new PGenericAliasIterator(PythonBuiltinClassType.PGenericAliasIterator, PythonBuiltinClassType.PGenericAliasIterator.getInstanceShape(language), object);
    }

    public static PUnionType createUnionType(PythonLanguage language, Object[] args) {
        return new PUnionType(PythonBuiltinClassType.PUnionType, PythonBuiltinClassType.PUnionType.getInstanceShape(language), createTuple(language, args));
    }

    public static DigestObject createDigestObject(PythonLanguage language, PythonBuiltinClassType type, String name, Object digest) {
        return DigestObject.create(type, type.getInstanceShape(language), name, digest);
    }

    public static PyCapsule createCapsuleNativeName(PythonLanguage language, Object pointer, Object name) {
        return createCapsule(language, new PyCapsule.CapsuleData(pointer, name));
    }

    public static PyCapsule createCapsuleJavaName(PythonLanguage language, Object pointer, byte[] name) {
        return createCapsule(language, new PyCapsule.CapsuleData(pointer, new CArrayWrappers.CByteArrayWrapper(name)));
    }

    public static PyCapsule createCapsule(PythonLanguage language, PyCapsule.CapsuleData data) {
        return new PyCapsule(language, data);
    }

    public static MultibyteIncrementalDecoderObject createMultibyteIncrementalDecoderObject(Object cls, Shape shape) {
        return new MultibyteIncrementalDecoderObject(cls, shape);
    }

    public static MultibyteIncrementalEncoderObject createMultibyteIncrementalEncoderObject(Object cls, Shape shape) {
        return new MultibyteIncrementalEncoderObject(cls, shape);
    }

    public static MultibyteStreamReaderObject createMultibyteStreamReaderObject(Object cls, Shape shape) {
        return new MultibyteStreamReaderObject(cls, shape);
    }

    public static MultibyteStreamWriterObject createMultibyteStreamWriterObject(Object cls, Shape shape) {
        return new MultibyteStreamWriterObject(cls, shape);
    }

    public static MultibyteCodecObject createMultibyteCodecObject(PythonLanguage language, MultibyteCodec codec) {
        return createMultibyteCodecObject(PythonBuiltinClassType.MultibyteCodec, PythonBuiltinClassType.MultibyteCodec.getInstanceShape(language), codec);
    }

    public static MultibyteCodecObject createMultibyteCodecObject(Object cls, Shape shape, MultibyteCodec codec) {
        return new MultibyteCodecObject(cls, shape, codec);
    }

    public static PAsyncGenASend createAsyncGeneratorASend(PythonLanguage language, PAsyncGen receiver, Object message) {
        return new PAsyncGenASend(language, receiver, message);
    }

    public static PAsyncGenAThrow createAsyncGeneratorAThrow(PythonLanguage language, PAsyncGen receiver, Object arg1, Object arg2, Object arg3) {
        return new PAsyncGenAThrow(language, receiver, arg1, arg2, arg3);
    }

    public static PAsyncGenWrappedValue createAsyncGeneratorWrappedValue(PythonLanguage language, Object wrapped) {
        return new PAsyncGenWrappedValue(language, wrapped);
    }

    // pickle

    public static PPickleBuffer createPickleBuffer(PythonLanguage language, Object view) {
        return createPickleBuffer(view, PythonBuiltinClassType.PickleBuffer, PythonBuiltinClassType.PickleBuffer.getInstanceShape(language));
    }

    public static PPickleBuffer createPickleBuffer(Object view, Object cls, Shape shape) {
        return new PPickleBuffer(cls, shape, view);
    }

    public static PPickler createPickler(PythonLanguage language) {
        return createPickler(PythonBuiltinClassType.Pickler, PythonBuiltinClassType.Pickler.getInstanceShape(language));
    }

    public static PPickler createPickler(Object cls, Shape shape) {
        return new PPickler(cls, shape);
    }

    public static PUnpickler createUnpickler(PythonLanguage language) {
        return createUnpickler(PythonBuiltinClassType.Unpickler, PythonBuiltinClassType.Unpickler.getInstanceShape(language));
    }

    public static PUnpickler createUnpickler(Object cls, Shape shape) {
        return new PUnpickler(cls, shape);
    }

    public static PPicklerMemoProxy createPicklerMemoProxy(PythonLanguage language, PPickler pickler) {
        return createPicklerMemoProxy(pickler, PythonBuiltinClassType.PicklerMemoProxy, PythonBuiltinClassType.PicklerMemoProxy.getInstanceShape(language));
    }

    public static PPicklerMemoProxy createPicklerMemoProxy(PPickler pickler, Object cls, Shape shape) {
        return new PPicklerMemoProxy(cls, shape, pickler);
    }

    public static PUnpicklerMemoProxy createUnpicklerMemoProxy(PythonLanguage language, PUnpickler unpickler) {
        return createUnpicklerMemoProxy(unpickler, PythonBuiltinClassType.UnpicklerMemoProxy, PythonBuiltinClassType.UnpicklerMemoProxy.getInstanceShape(language));
    }

    public static PUnpicklerMemoProxy createUnpicklerMemoProxy(PUnpickler unpickler, Object cls, Shape shape) {
        return new PUnpicklerMemoProxy(cls, shape, unpickler);
    }

    public static PStruct createStruct(PythonLanguage language, PStruct.StructInfo structInfo) {
        return new PStruct(PythonBuiltinClassType.PStruct, PythonBuiltinClassType.PStruct.getInstanceShape(language), structInfo);
    }

    public static PStructUnpackIterator createStructUnpackIterator(PythonLanguage language, PStruct struct, Object buffer) {
        return new PStructUnpackIterator(PythonBuiltinClassType.PStructUnpackIterator, PythonBuiltinClassType.PStructUnpackIterator.getInstanceShape(language), struct, buffer);
    }

    public static PTokenizerIter createTokenizerIter(Object cls, Shape shape, Supplier<int[]> inputSupplier, boolean extraTokens) {
        return new PTokenizerIter(cls, shape, inputSupplier, extraTokens);
    }

    public static PTypeVar createTypeVar(PythonLanguage language, TruffleString name, Object bound, Object evaluateBound, Object constraints, Object evaluateConstraints,
                    boolean covariant, boolean contravariant, boolean inferVariance) {
        return createTypeVar(PythonBuiltinClassType.PTypeVar, PythonBuiltinClassType.PTypeVar.getInstanceShape(language), name, bound, evaluateBound, constraints, evaluateConstraints,
                        covariant, contravariant, inferVariance);
    }

    public static PTypeVar createTypeVar(Object cls, Shape shape, TruffleString name, Object bound, Object evaluateBound, Object constraints, Object evaluateConstraints,
                    boolean covariant, boolean contravariant, boolean inferVariance) {
        return new PTypeVar(cls, shape, name, bound, evaluateBound, constraints, evaluateConstraints, covariant, contravariant, inferVariance);
    }

    public static PTypeVarTuple createTypeVarTuple(PythonLanguage language, TruffleString name) {
        return createTypeVarTuple(PythonBuiltinClassType.PTypeVarTuple, PythonBuiltinClassType.PTypeVarTuple.getInstanceShape(language), name);
    }

    public static PTypeVarTuple createTypeVarTuple(Object cls, Shape shape, TruffleString name) {
        return new PTypeVarTuple(cls, shape, name);
    }

    public static PParamSpec createParamSpec(PythonLanguage language, TruffleString name, Object bound, boolean covariant, boolean contravariant, boolean inferVariance) {
        return createParamSpec(PythonBuiltinClassType.PParamSpec, PythonBuiltinClassType.PParamSpec.getInstanceShape(language), name, bound, covariant, contravariant, inferVariance);
    }

    public static PParamSpec createParamSpec(Object cls, Shape shape, TruffleString name, Object bound, boolean covariant, boolean contravariant, boolean inferVariance) {
        return new PParamSpec(cls, shape, name, bound, covariant, contravariant, inferVariance);
    }

    public static PParamSpecArgs createParamSpecArgs(PythonLanguage language, Object origin) {
        return createParamSpecArgs(PythonBuiltinClassType.PParamSpecArgs, PythonBuiltinClassType.PParamSpecArgs.getInstanceShape(language), origin);
    }

    public static PParamSpecArgs createParamSpecArgs(Object cls, Shape shape, Object origin) {
        return new PParamSpecArgs(cls, shape, origin);
    }

    public static PParamSpecKwargs createParamSpecKwargs(PythonLanguage language, Object origin) {
        return createParamSpecKwargs(PythonBuiltinClassType.PParamSpecKwargs, PythonBuiltinClassType.PParamSpecKwargs.getInstanceShape(language), origin);
    }

    public static PParamSpecKwargs createParamSpecKwargs(Object cls, Shape shape, Object origin) {
        return new PParamSpecKwargs(cls, shape, origin);
    }

    public static PTypeAliasType createTypeAliasType(PythonLanguage language, TruffleString name, PTuple typeParams, Object computeValue, Object value, Object module) {
        return createTypeAliasType(PythonBuiltinClassType.PTypeAliasType, PythonBuiltinClassType.PTypeAliasType.getInstanceShape(language), name, typeParams, computeValue, value, module);
    }

    public static PTypeAliasType createTypeAliasType(Object cls, Shape shape, TruffleString name, PTuple typeParams, Object computeValue, Object value, Object module) {
        return new PTypeAliasType(cls, shape, name, typeParams, computeValue, value, module);
    }

    public static Profiler createProfiler(Object cls, Shape shape, CPUSampler sampler) {
        return new Profiler(cls, shape, sampler);
    }
}
