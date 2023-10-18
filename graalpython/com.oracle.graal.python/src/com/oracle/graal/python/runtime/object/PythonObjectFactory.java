/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import java.lang.ref.ReferenceQueue;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
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
import com.oracle.graal.python.builtins.modules.ctypes.CDataObject;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldObject;
import com.oracle.graal.python.builtins.modules.ctypes.CThunkObject;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArgObject;
import com.oracle.graal.python.builtins.modules.ctypes.PyCFuncPtrObject;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.modules.ctypes.StructParamObject;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
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
import com.oracle.graal.python.builtins.modules.lzma.LZMAObject;
import com.oracle.graal.python.builtins.modules.zlib.ZLibCompObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
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
import com.oracle.graal.python.builtins.objects.cext.hpy.PythonHPyObject;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
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
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBigRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PObjectSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.itertools.PAccumulate;
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
import com.oracle.graal.python.builtins.objects.list.PList.ListOrigin;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
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
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.modules.multiprocessing.PGraalPySemLock;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.thread.PThreadLocal;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.PTupleGetter;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence.BuiltinTypeDescriptor;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.types.PGenericAlias;
import com.oracle.graal.python.builtins.objects.types.PUnionType;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PythonObjectFactoryNodeGen.LazyNodeGen;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Factory for Python objects that also reports to Truffle's {@link AllocationReporter}. The
 * reporting needs current context. There are several implementations of this abstract class. Use
 * this rule of thumb when choosing which one to use:
 * <ul>
 * <li>In partially evaluated code: use adopted (@Child/@Cached) {@link PythonObjectFactory} node
 * </li>
 * <li>Behind {@code TruffleBoundary}:
 * <ul>
 * <li>When the current context is already available, use {@link Python3Core#factory()}. This avoids
 * repeated context lookups inside the factory.</li>
 * <li>When the current context is not available, but multiple objects will be created: lookup the
 * context and use {@link Python3Core#factory()}. This executes only one context lookup. Note: first
 * check if the caller could pass the context to avoid looking it up behind {@code TruffleBoundary}.
 * </li>
 * <li>When the current context is not available, and only one object is to be created: use
 * {@link PythonObjectFactory#getUncached()}.</li>
 * </ul>
 * </li>
 * </ul>
 */
@GenerateUncached
@ImportStatic(PythonOptions.class)
@GenerateInline(false) // Footprint reduction 28 -> 9
public abstract class PythonObjectFactory extends Node {
    // Note: we're keeping this not inlined for now because:
    // - overwhelming number of usages of non-execute entry points to refactor
    // - often used lazily and as a @Child, most notably in all the builtins

    @NeverDefault
    public static PythonObjectFactory create() {
        return PythonObjectFactoryNodeGen.create();
    }

    public static PythonObjectFactory getUncached() {
        return PythonObjectFactoryNodeGen.getUncached();
    }

    protected abstract AllocationReporter executeTrace(Object o, long size);

    protected abstract Shape executeGetShape(Object o, boolean flag);

    @Specialization
    static Shape getShape(Object o, @SuppressWarnings("unused") boolean flag,
                    @Cached TypeNodes.GetInstanceShape getShapeNode) {
        return getShapeNode.execute(o);
    }

    @Specialization
    static AllocationReporter doTrace(Object o, long size,
                    @Cached(value = "getAllocationReporter()", allowUncached = true) AllocationReporter reporter) {
        if (reporter.isActive()) {
            doTraceImpl(o, size, reporter);
        }
        return null;
    }

    @InliningCutoff
    private static void doTraceImpl(Object o, long size, AllocationReporter reporter) {
        reporter.onEnter(null, 0, size);
        reporter.onReturnValue(o, 0, size);
    }

    @NeverDefault
    protected AllocationReporter getAllocationReporter() {
        return PythonContext.get(this).getAllocationReporter();
    }

    public PythonLanguage getLanguage() {
        return PythonLanguage.get(this);
    }

    public final Shape getShape(PythonBuiltinClassType cls) {
        return cls.getInstanceShape(getLanguage());
    }

    public final Shape getShape(Object cls) {
        return executeGetShape(cls, true);
    }

    public final <T> T trace(T allocatedObject) {
        executeTrace(allocatedObject, AllocationReporter.SIZE_UNKNOWN);
        return allocatedObject;
    }

    /*
     * Python objects
     */

    /**
     * Creates a PythonObject for the given class. This is potentially slightly slower than if the
     * shape had been cached, due to the additional shape lookup.
     */
    public final PythonObject createPythonObject(Object cls) {
        return createPythonObject(cls, getShape(cls));
    }

    /**
     * Creates a PythonObject for the given class. This is potentially slightly slower than if the
     * shape had been cached, due to the additional shape lookup.
     */
    public final PythonObject createPythonHPyObject(Object cls, Object hpyNativeSpace) {
        return trace(new PythonHPyObject(cls, getShape(cls), hpyNativeSpace));
    }

    /**
     * Creates a Python object with the given shape. Python object shapes store the class in the
     * shape if possible.
     */
    public final PythonObject createPythonObject(Object klass, Shape instanceShape) {
        return trace(new PythonObject(klass, instanceShape));
    }

    public final PythonNativeVoidPtr createNativeVoidPtr(Object obj) {
        return trace(new PythonNativeVoidPtr(obj));
    }

    public final PythonNativeVoidPtr createNativeVoidPtr(Object obj, long nativePtr) {
        return trace(new PythonNativeVoidPtr(obj, nativePtr));
    }

    public final SuperObject createSuperObject(Object self) {
        return trace(new SuperObject(self, getShape(self)));
    }

    /*
     * Primitive types
     */
    public final PInt createInt(int value) {
        Shape shape = getLanguage().getBuiltinTypeInstanceShape(PythonBuiltinClassType.PInt);
        return trace(new PInt(PythonBuiltinClassType.PInt, shape, PInt.longToBigInteger(value)));
    }

    public final PInt createInt(long value) {
        Shape shape = getLanguage().getBuiltinTypeInstanceShape(PythonBuiltinClassType.PInt);
        return trace(new PInt(PythonBuiltinClassType.PInt, shape, PInt.longToBigInteger(value)));
    }

    public final PInt createInt(BigInteger value) {
        Shape shape = getLanguage().getBuiltinTypeInstanceShape(PythonBuiltinClassType.PInt);
        return trace(new PInt(PythonBuiltinClassType.PInt, shape, value));
    }

    public final Object createInt(Object cls, int value) {
        return createInt(cls, PInt.longToBigInteger(value));
    }

    public final Object createInt(Object cls, long value) {
        return createInt(cls, PInt.longToBigInteger(value));
    }

    public final PInt createInt(Object cls, BigInteger value) {
        return trace(new PInt(cls, getShape(cls), value));
    }

    public final PFloat createFloat(double value) {
        Shape shape = getLanguage().getBuiltinTypeInstanceShape(PythonBuiltinClassType.PFloat);
        return trace(new PFloat(PythonBuiltinClassType.PFloat, shape, value));
    }

    public final PFloat createFloat(Object cls, double value) {
        return trace(new PFloat(cls, getShape(cls), value));
    }

    public final PString createString(TruffleString string) {
        Shape shape = getLanguage().getBuiltinTypeInstanceShape(PythonBuiltinClassType.PString);
        return trace(new PString(PythonBuiltinClassType.PString, shape, string));
    }

    public final PString createString(Object cls, TruffleString string) {
        return trace(new PString(cls, getShape(cls), string));
    }

    public final PString createString(NativeCharSequence string) {
        return createString(PythonBuiltinClassType.PString, string);
    }

    public final PString createString(Object cls, NativeCharSequence string) {
        return trace(new PString(cls, getShape(cls), string));
    }

    public final PBytes createBytes(byte[] array) {
        return createBytes(array, array.length);
    }

    public final PBytes createBytes(byte[] array, int offset, int length) {
        if (length != array.length) {
            byte[] buf = new byte[length];
            PythonUtils.arraycopy(array, offset, buf, 0, buf.length);
            return createBytes(buf, length);
        }
        return createBytes(array, length);
    }

    public final PBytes createBytes(Object cls, byte[] array) {
        return createBytes(cls, array, array.length);
    }

    public final PBytes createBytes(byte[] array, int length) {
        return createBytes(new ByteSequenceStorage(array, length));
    }

    public final PBytes createBytes(Object cls, byte[] array, int length) {
        return createBytes(cls, new ByteSequenceStorage(array, length));
    }

    public final PBytes createBytes(SequenceStorage storage) {
        return trace(new PBytes(PythonBuiltinClassType.PBytes, getShape(PythonBuiltinClassType.PBytes), storage));
    }

    public final PBytes createBytes(Object cls, SequenceStorage storage) {
        return trace(new PBytes(cls, getShape(cls), storage));
    }

    public final PTuple createEmptyTuple() {
        return createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PTuple createEmptyTuple(Object cls) {
        return createTuple(cls, EmptySequenceStorage.INSTANCE);
    }

    public final PTuple createTuple(Object[] objects) {
        Shape shape = PythonBuiltinClassType.PTuple.getInstanceShape(getLanguage());
        return trace(new PTuple(PythonBuiltinClassType.PTuple, shape, objects));
    }

    public final PTuple createTuple(int[] ints) {
        return createTuple(new IntSequenceStorage(ints));
    }

    public final PTuple createTuple(SequenceStorage store) {
        Shape shape = PythonBuiltinClassType.PTuple.getInstanceShape(getLanguage());
        return trace(new PTuple(PythonBuiltinClassType.PTuple, shape, store));
    }

    public final PTuple createTuple(Object cls, Shape instanceShape, Object[] objects) {
        return trace(new PTuple(cls, instanceShape, objects));
    }

    public final PTuple createTuple(Object cls, Object[] objects) {
        return trace(new PTuple(cls, getShape(cls), objects));
    }

    public final PTuple createTuple(Object cls, SequenceStorage store) {
        return trace(new PTuple(cls, getShape(cls), store));
    }

    public final PTuple createStructSeq(BuiltinTypeDescriptor desc, Object... values) {
        assert desc.inSequence <= values.length && values.length <= desc.fieldNames.length;
        return createTuple(desc.type, new ObjectSequenceStorage(values, desc.inSequence));
    }

    public final PTupleGetter createTupleGetter(int index, Object doc) {
        return createTupleGetter(PythonBuiltinClassType.PTupleGetter, index, doc);
    }

    public final PTupleGetter createTupleGetter(Object cls, int index, Object doc) {
        return trace(new PTupleGetter(cls, getShape(cls), index, doc));
    }

    public final PComplex createComplex(Object cls, double real, double imag) {
        return trace(new PComplex(cls, getShape(cls), real, imag));
    }

    public final PComplex createComplex(double real, double imag) {
        return createComplex(PythonBuiltinClassType.PComplex, real, imag);
    }

    public final PIntRange createIntRange(int stop) {
        return trace(new PIntRange(getLanguage(), 0, stop, 1, stop));
    }

    public final PIntRange createIntRange(int start, int stop, int step, int len) {
        return trace(new PIntRange(getLanguage(), start, stop, step, len));
    }

    public final PBigRange createBigRange(BigInteger start, BigInteger stop, BigInteger step, BigInteger len) {
        return createBigRange(createInt(start), createInt(stop), createInt(step), createInt(len));
    }

    public final PBigRange createBigRange(PInt start, PInt stop, PInt step, PInt len) {
        return trace(new PBigRange(getLanguage(), start, stop, step, len));
    }

    public final PIntSlice createIntSlice(int start, int stop, int step) {
        return trace(new PIntSlice(getLanguage(), start, stop, step));
    }

    public final PIntSlice createIntSlice(int start, int stop, int step, boolean isStartNone, boolean isStepNone) {
        return trace(new PIntSlice(getLanguage(), start, stop, step, isStartNone, isStepNone));
    }

    public final PObjectSlice createObjectSlice(Object start, Object stop, Object step) {
        return trace(new PObjectSlice(getLanguage(), start, stop, step));
    }

    public final PRandom createRandom(Object cls) {
        return trace(new PRandom(cls, getShape(cls)));
    }

    /*
     * Classes, methods and functions
     */

    /**
     * Only to be used during context creation
     */
    public final PythonModule createPythonModule(TruffleString name) {
        return trace(PythonModule.createInternal(name));
    }

    public final PythonModule createPythonModule(Object cls) {
        return trace(new PythonModule(cls, getShape(cls)));
    }

    public final PythonClass createPythonClassAndFixupSlots(PythonLanguage language, Object metaclass, TruffleString name, Object base, PythonAbstractClass[] bases) {
        PythonClass result = trace(new PythonClass(language, metaclass, getShape(metaclass), name, base, bases));
        SpecialMethodSlot.initializeSpecialMethodSlots(result, GetMroStorageNode.executeUncached(result), language);
        result.initializeMroShape(language);
        return result;
    }

    public final PythonClass createPythonClass(Object metaclass, TruffleString name, boolean invokeMro, Object base, PythonAbstractClass[] bases) {
        // Note: called from type ctor, which itself will invoke setupSpecialMethodSlots at the
        // right point
        return trace(new PythonClass(getLanguage(), metaclass, getShape(metaclass), name, invokeMro, base, bases));
    }

    public final PMemoryView createMemoryView(PythonContext context, BufferLifecycleManager bufferLifecycleManager, Object buffer, Object owner,
                    int len, boolean readonly, int itemsize, BufferFormat format, TruffleString formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.PMemoryView;
        return trace(new PMemoryView(cls, getShape(cls), context, bufferLifecycleManager, buffer, owner, len, readonly, itemsize, format, formatString,
                        ndim, bufPointer, offset, shape, strides, suboffsets, flags));
    }

    private final PMemoryView createMemoryView(PythonContext context, BufferLifecycleManager bufferLifecycleManager, Object buffer, Object owner,
                    int len, boolean readonly, int itemsize, TruffleString formatString, int ndim, Object bufPointer,
                    int offset, int[] shape, int[] strides, int[] suboffsets, int flags, TruffleString.CodePointLengthNode lengthNode, TruffleString.CodePointAtIndexNode atIndexNode) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.PMemoryView;
        return trace(new PMemoryView(cls, getShape(cls), context, bufferLifecycleManager, buffer, owner, len, readonly, itemsize,
                        BufferFormat.forMemoryView(formatString, lengthNode, atIndexNode), formatString, ndim, bufPointer, offset, shape, strides, suboffsets, flags));
    }

    public final PMemoryView createMemoryViewForManagedObject(Object buffer, Object owner, int itemsize, int length, boolean readonly, TruffleString format,
                    TruffleString.CodePointLengthNode lengthNode, TruffleString.CodePointAtIndexNode atIndexNode) {
        return createMemoryView(null, null, buffer, owner, length, readonly, itemsize, format, 1,
                        null, 0, new int[]{length / itemsize}, new int[]{itemsize}, null,
                        PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN, lengthNode, atIndexNode);
    }

    public final PMemoryView createMemoryViewForManagedObject(Object owner, int itemsize, int length, boolean readonly, TruffleString format, TruffleString.CodePointLengthNode lengthNode,
                    TruffleString.CodePointAtIndexNode atIndexNode) {
        return createMemoryViewForManagedObject(owner, owner, itemsize, length, readonly, format, lengthNode, atIndexNode);
    }

    public final PMethod createMethod(Object cls, Object self, Object function) {
        return trace(new PMethod(cls, getShape(cls), self, function));
    }

    public final PMethod createMethod(Object self, Object function) {
        return createMethod(PythonBuiltinClassType.PMethod, self, function);
    }

    public final PMethod createBuiltinMethod(Object self, PFunction function) {
        return createMethod(PythonBuiltinClassType.PBuiltinFunctionOrMethod, self, function);
    }

    public final PBuiltinMethod createBuiltinMethod(Object cls, Object self, PBuiltinFunction function) {
        return trace(new PBuiltinMethod(cls, getShape(cls), self, function, null));
    }

    public final PBuiltinMethod createBuiltinMethod(Object self, PBuiltinFunction function, Object classObject) {
        return trace(new PBuiltinMethod(PythonBuiltinClassType.PBuiltinMethod, getShape(PythonBuiltinClassType.PBuiltinMethod), self, function, classObject));
    }

    public final PBuiltinMethod createBuiltinMethod(Object self, PBuiltinFunction function) {
        return createBuiltinMethod(PythonBuiltinClassType.PBuiltinFunctionOrMethod, self, function);
    }

    public final PFunction createFunction(TruffleString name, PCode code, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(getLanguage(), name, name, code, globals, closure));
    }

    public final PFunction createFunction(TruffleString name, TruffleString qualname, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues, PCell[] closure) {
        return trace(new PFunction(getLanguage(), name, qualname, code, globals, defaultValues, kwDefaultValues, closure));
    }

    public final PFunction createFunction(TruffleString name, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues, PCell[] closure) {
        return trace(new PFunction(getLanguage(), name, name, code, globals, defaultValues, kwDefaultValues, closure));
    }

    public final PFunction createFunction(TruffleString name, TruffleString qualname, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues, PCell[] closure,
                    Assumption codeStableAssumption, Assumption defaultsStableAssumption) {
        return trace(new PFunction(getLanguage(), name, qualname, code, globals, defaultValues, kwDefaultValues, closure,
                        codeStableAssumption, defaultsStableAssumption));
    }

    public final PBuiltinFunction createBuiltinFunction(TruffleString name, Object type, int numDefaults, int flags, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.PBuiltinFunction.getInstanceShape(getLanguage()), name, type,
                        PBuiltinFunction.generateDefaults(numDefaults), null, flags, callTarget));
    }

    public final PBuiltinFunction createBuiltinFunction(TruffleString name, Object type, Object[] defaults, PKeyword[] kw, int flags, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(PythonBuiltinClassType.PBuiltinFunction, PythonBuiltinClassType.PBuiltinFunction.getInstanceShape(getLanguage()), name, type, defaults, kw, flags,
                        callTarget));
    }

    public final PBuiltinFunction createWrapperDescriptor(TruffleString name, Object type, int numDefaults, int flags, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(PythonBuiltinClassType.WrapperDescriptor, PythonBuiltinClassType.WrapperDescriptor.getInstanceShape(getLanguage()), name, type,
                        PBuiltinFunction.generateDefaults(numDefaults), null, flags, callTarget));
    }

    public final PBuiltinFunction createWrapperDescriptor(TruffleString name, Object type, Object[] defaults, PKeyword[] kw, int flags, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(PythonBuiltinClassType.WrapperDescriptor, PythonBuiltinClassType.WrapperDescriptor.getInstanceShape(getLanguage()), name, type, defaults, kw, flags,
                        callTarget));
    }

    public final PBuiltinFunction createBuiltinFunction(PBuiltinFunction function, Object klass) {
        PythonBuiltinClassType type = (PythonBuiltinClassType) function.getInitialPythonClass();
        return trace(new PBuiltinFunction(type, type.getInstanceShape(getLanguage()), function.getName(), klass,
                        function.getDefaults(), function.getKwDefaults(), function.getFlags(), function.getCallTarget()));
    }

    public final GetSetDescriptor createGetSetDescriptor(Object get, Object set, TruffleString name, Object type) {
        return trace(new GetSetDescriptor(getLanguage(), get, set, name, type));
    }

    public final GetSetDescriptor createGetSetDescriptor(Object get, Object set, TruffleString name, Object type, boolean allowsDelete) {
        return trace(new GetSetDescriptor(getLanguage(), get, set, name, type, allowsDelete));
    }

    public final GetSetDescriptor createMemberDescriptor(Object get, Object set, TruffleString name, Object type) {
        return trace(new GetSetDescriptor(PythonBuiltinClassType.MemberDescriptor, PythonBuiltinClassType.MemberDescriptor.getInstanceShape(getLanguage()), get, set, name, type, set != null));
    }

    public final HiddenKeyDescriptor createHiddenKeyDescriptor(HiddenKey key, Object type) {
        return trace(new HiddenKeyDescriptor(getLanguage(), key, type));
    }

    public final PDecoratedMethod createClassmethod(Object cls) {
        return trace(new PDecoratedMethod(cls, getShape(cls)));
    }

    public final PDecoratedMethod createClassmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PClassmethod, PythonBuiltinClassType.PClassmethod.getInstanceShape(getLanguage()), callable));
    }

    public final PDecoratedMethod createBuiltinClassmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PBuiltinClassMethod, PythonBuiltinClassType.PBuiltinClassMethod.getInstanceShape(getLanguage()), callable));
    }

    public final PDecoratedMethod createInstancemethod(Object cls) {
        return trace(new PDecoratedMethod(cls, getShape(cls)));
    }

    public final PDecoratedMethod createStaticmethod(Object cls) {
        return trace(new PDecoratedMethod(cls, getShape(cls)));
    }

    public final PDecoratedMethod createStaticmethodFromCallableObj(Object callable) {
        Object func = callable;
        if (func instanceof PBuiltinFunction) {
            /*
             * CPython's C static methods contain an object of type `builtin_function_or_method`
             * (our PBuiltinMethod). Their self points to their type, but when called they get NULL
             * as the first argument instead.
             */
            func = createBuiltinMethod(((PBuiltinFunction) func).getEnclosingType(), (PBuiltinFunction) func);
        }
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PStaticmethod, PythonBuiltinClassType.PStaticmethod.getInstanceShape(getLanguage()), func));
    }

    /*
     * Lists, sets and dicts
     */

    public final PList createList() {
        return createList(PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PList createList(SequenceStorage storage) {
        return createList(PythonBuiltinClassType.PList, storage);
    }

    public final PList createList(Object cls, Shape instanceShape, SequenceStorage storage) {
        return trace(new PList(cls, instanceShape, storage));
    }

    public final PList createList(SequenceStorage storage, ListOrigin origin) {
        return trace(new PList(PythonBuiltinClassType.PList, PythonBuiltinClassType.PList.getInstanceShape(getLanguage()), storage, origin));
    }

    public final PList createList(Object cls, SequenceStorage storage) {
        return trace(new PList(cls, getShape(cls), storage));
    }

    public final PList createList(Object cls) {
        return createList(cls, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PList createList(Object[] array) {
        return createList(PythonBuiltinClassType.PList, array);
    }

    public final PList createList(Object cls, Object[] array) {
        return trace(new PList(cls, getShape(cls), SequenceStorageFactory.createStorage(array)));
    }

    public final PSet createSet() {
        Shape shape = PythonBuiltinClassType.PSet.getInstanceShape(getLanguage());
        return trace(new PSet(PythonBuiltinClassType.PSet, shape));
    }

    public final PSet createSet(Object cls) {
        return trace(new PSet(cls, getShape(cls)));
    }

    public final PSet createSet(Object cls, Shape instanceShape) {
        return trace(new PSet(cls, instanceShape));
    }

    public final PSet createSet(HashingStorage storage) {
        Shape shape = PythonBuiltinClassType.PSet.getInstanceShape(getLanguage());
        return trace(new PSet(PythonBuiltinClassType.PSet, shape, storage));
    }

    public final PFrozenSet createFrozenSet(Object cls) {
        return trace(new PFrozenSet(cls, getShape(cls)));
    }

    public final PFrozenSet createFrozenSet(Object cls, HashingStorage storage) {
        return trace(new PFrozenSet(cls, getShape(cls), storage));
    }

    public final PFrozenSet createFrozenSet(HashingStorage storage) {
        Shape shape = PythonBuiltinClassType.PFrozenSet.getInstanceShape(getLanguage());
        return trace(new PFrozenSet(PythonBuiltinClassType.PFrozenSet, shape, storage));
    }

    public final PDict createDict() {
        Shape shape = PythonBuiltinClassType.PDict.getInstanceShape(getLanguage());
        return trace(new PDict(PythonBuiltinClassType.PDict, shape));
    }

    public final PDict createDict(PKeyword[] keywords) {
        Shape shape = PythonBuiltinClassType.PDict.getInstanceShape(getLanguage());
        return trace(new PDict(PythonBuiltinClassType.PDict, shape, keywords));
    }

    public final PDict createDict(Object cls) {
        return trace(new PDict(cls, getShape(cls)));
    }

    public final POrderedDict createOrderedDict(Object cls) {
        return trace(new POrderedDict(cls, getShape(cls)));
    }

    public final PDictKeysView createOrderedDictKeys(POrderedDict dict) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictKeys;
        return trace(new PDictKeysView(cls, cls.getInstanceShape(getLanguage()), dict));
    }

    public final PDictValuesView createOrderedDictValues(POrderedDict dict) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictValues;
        return trace(new PDictValuesView(cls, cls.getInstanceShape(getLanguage()), dict));
    }

    public final PDictItemsView createOrderedDictItems(POrderedDict dict) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictItems;
        return trace(new PDictItemsView(cls, cls.getInstanceShape(getLanguage()), dict));
    }

    public POrderedDictIterator createOrderedDictIterator(POrderedDict dict, POrderedDictIterator.IteratorType type, boolean reversed) {
        PythonBuiltinClassType cls = PythonBuiltinClassType.POrderedDictIterator;
        return trace(new POrderedDictIterator(cls, cls.getInstanceShape(getLanguage()), dict, type, reversed));
    }

    @SuppressWarnings("unchecked")
    public final PDict createDictFromMap(LinkedHashMap<String, Object> map) {
        return createDict(EconomicMapStorage.create(map));
    }

    /**
     * Generic version of {@link #createDictFromMap(LinkedHashMap)} that allows any type of keys.
     * Note that this means that unless the keys are known to be side effect free, e.g., builtin
     * types, this may end up calling Python code, so indirect call context should be properly
     * set-up. This helper is meant for Truffle boundary code, in PE code build a storage by setting
     * elements one by one starting from empty storage.
     */
    @SuppressWarnings("unchecked")
    public final PDict createDictFromMapGeneric(LinkedHashMap<Object, Object> map) {
        return createDict(EconomicMapStorage.createGeneric(map));
    }

    public final PDict createDictFixedStorage(PythonObject pythonObject, MroSequenceStorage mroSequenceStorage) {
        return createDict(new DynamicObjectStorage(pythonObject.getStorage(), mroSequenceStorage));
    }

    public final PDict createDictFixedStorage(PythonObject pythonObject) {
        return createDict(new DynamicObjectStorage(pythonObject.getStorage()));
    }

    public final PDict createDict(Object cls, HashingStorage storage) {
        return trace(new PDict(cls, getShape(cls), storage));
    }

    public final PDict createDict(HashingStorage storage) {
        return createDict(PythonBuiltinClassType.PDict, storage);
    }

    public final PDict createDict(Object cls, Shape instanceShape, HashingStorage storage) {
        return trace(new PDict(cls, instanceShape, storage));
    }

    public final PSimpleNamespace createSimpleNamespace() {
        return createSimpleNamespace(PythonBuiltinClassType.PSimpleNamespace);
    }

    public final PSimpleNamespace createSimpleNamespace(Object cls) {
        return createSimpleNamespace(cls, getShape(cls));
    }

    public final PSimpleNamespace createSimpleNamespace(Object cls, Shape instanceShape) {
        return trace(new PSimpleNamespace(cls, instanceShape));
    }

    public final PKeyWrapper createKeyWrapper(Object cmp) {
        return trace(new PKeyWrapper(PythonBuiltinClassType.PKeyWrapper, getShape(PythonBuiltinClassType.PKeyWrapper), cmp));
    }

    public final PPartial createPartial(Object cls, Object function, Object[] args, PDict kwDict) {
        return trace(new PPartial(cls, getShape(cls), function, args, kwDict));
    }

    public LruCacheObject createLruCacheObject(Object cls) {
        return trace(new LruCacheObject(cls, getShape(cls)));
    }

    public final PDefaultDict createDefaultDict(Object cls) {
        return createDefaultDict(cls, PNone.NONE);
    }

    public final PDefaultDict createDefaultDict(Object cls, Object defaultFactory) {
        return trace(new PDefaultDict(cls, getShape(cls), defaultFactory));
    }

    public final PDefaultDict createDefaultDict(Object defaultFactory, HashingStorage storage) {
        return createDefaultDict(PythonBuiltinClassType.PDefaultDict, defaultFactory, storage);
    }

    public final PDefaultDict createDefaultDict(Object cls, Object defaultFactory, HashingStorage storage) {
        return trace(new PDefaultDict(cls, getShape(cls), storage, defaultFactory));
    }

    public final PDictView createDictKeysView(PHashingCollection dict) {
        return trace(new PDictKeysView(PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictKeysView.getInstanceShape(getLanguage()), dict));
    }

    public final PDictView createDictValuesView(PHashingCollection dict) {
        return trace(new PDictValuesView(PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDictValuesView.getInstanceShape(getLanguage()), dict));
    }

    public final PDictView createDictItemsView(PHashingCollection dict) {
        return trace(new PDictItemsView(PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictItemsView.getInstanceShape(getLanguage()), dict));
    }

    /*
     * Special objects: generators, proxies, references, cells
     */

    public final PGenerator createGenerator(TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return trace(PGenerator.create(getLanguage(), name, qualname, rootNode, callTargets, arguments, PythonBuiltinClassType.PGenerator));
    }

    public final PGenerator createIterableCoroutine(TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return trace(PGenerator.create(getLanguage(), name, qualname, rootNode, callTargets, arguments, PythonBuiltinClassType.PGenerator, true));
    }

    public final PGenerator createCoroutine(TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return trace(PGenerator.create(getLanguage(), name, qualname, rootNode, callTargets, arguments, PythonBuiltinClassType.PCoroutine));
    }

    public final PCoroutineWrapper createCoroutineWrapper(PGenerator generator) {
        return trace(new PCoroutineWrapper(getLanguage(), generator));
    }

    public final PAsyncGen createAsyncGenerator(TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        return trace(PAsyncGen.create(getLanguage(), name, qualname, rootNode, callTargets, arguments));
    }

    public final PMappingproxy createMappingproxy(Object object) {
        PythonBuiltinClassType mpClass = PythonBuiltinClassType.PMappingproxy;
        return trace(new PMappingproxy(mpClass, mpClass.getInstanceShape(getLanguage()), object));
    }

    public final PMappingproxy createMappingproxy(Object cls, Object object) {
        return trace(new PMappingproxy(cls, getShape(cls), object));
    }

    public final PReferenceType createReferenceType(Object cls, Object object, Object callback, ReferenceQueue<Object> queue) {
        return trace(new PReferenceType(cls, getShape(cls), object, callback, queue));
    }

    public final PReferenceType createReferenceType(Object object, Object callback, ReferenceQueue<Object> queue) {
        return createReferenceType(PythonBuiltinClassType.PReferenceType, object, callback, queue);
    }

    public final PCell createCell(Assumption effectivelyFinal) {
        return trace(new PCell(effectivelyFinal));
    }

    /*
     * Frames, traces and exceptions
     */

    public final PFrame createPFrame(PFrame.Reference frameInfo, Node location, MaterializedFrame locals) {
        return trace(new PFrame(getLanguage(), frameInfo, location, locals));
    }

    public final PFrame createPFrame(Object threadState, PCode code, PythonObject globals, Object localsDict) {
        return trace(new PFrame(getLanguage(), threadState, code, globals, localsDict));
    }

    public final PTraceback createTraceback(PFrame frame, int lineno, PTraceback next) {
        return trace(new PTraceback(getLanguage(), frame, lineno, next));
    }

    public final PTraceback createTraceback(PFrame frame, int lineno, int lasti, PTraceback next) {
        return trace(new PTraceback(getLanguage(), frame, lineno, lasti, next));
    }

    public final PTraceback createTraceback(LazyTraceback tb) {
        return trace(new PTraceback(getLanguage(), tb));
    }

    public final PBaseException createBaseException(Object cls, PTuple args) {
        return createBaseException(cls, null, args);
    }

    public final PBaseException createBaseException(Object cls, Object[] data, PTuple args) {
        return trace(new PBaseException(cls, getShape(cls), data, args));
    }

    /*
     * Note: we use this method to convert a Java StackOverflowError into a Python RecursionError.
     * At the time when this is done, some Java stack frames were already unwinded but there is no
     * guarantee on how many. Therefore, it is important that this method is simple. In particular,
     * do not add calls if that can be avoided.
     */
    public final PBaseException createBaseException(Object cls, TruffleString format, Object[] args) {
        return createBaseException(cls, null, format, args);
    }

    public final PBaseException createBaseException(Object cls, Object[] data, TruffleString format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(cls, getShape(cls), data, format, args));
    }

    public final PBaseException createBaseException(Object cls) {
        return trace(new PBaseException(cls, getShape(cls), null));
    }

    public final PBaseException createBaseException(Object cls, Object[] data) {
        return trace(new PBaseException(cls, getShape(cls), data));
    }

    /*
     * Arrays
     */

    public final PArray createArray(Object cls, TruffleString formatString, BufferFormat format) {
        assert format != null;
        return trace(new PArray(cls, getShape(cls), formatString, format));
    }

    public final PArray createArray(TruffleString formatString, BufferFormat format, int length) throws OverflowException {
        return createArray(PythonBuiltinClassType.PArray, formatString, format, length);
    }

    public final PArray createArray(Object cls, TruffleString formatString, BufferFormat format, int length) throws OverflowException {
        assert format != null;
        return trace(new PArray(cls, getShape(cls), formatString, format, length));
    }

    public final PByteArray createByteArray(byte[] array) {
        return createByteArray(array, array.length);
    }

    public final PByteArray createByteArray(Object cls, byte[] array) {
        return createByteArray(cls, array, array.length);
    }

    public final PByteArray createByteArray(byte[] array, int length) {
        return createByteArray(new ByteSequenceStorage(array, length));
    }

    public final PByteArray createByteArray(Object cls, byte[] array, int length) {
        return createByteArray(cls, new ByteSequenceStorage(array, length));
    }

    public final PByteArray createByteArray(SequenceStorage storage) {
        return createByteArray(PythonBuiltinClassType.PByteArray, storage);
    }

    public final PByteArray createByteArray(Object cls, SequenceStorage storage) {
        return trace(new PByteArray(cls, getShape(cls), storage));
    }

    /*
     * Iterators
     */

    public final PStringIterator createStringIterator(TruffleString str) {
        return trace(new PStringIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), str));
    }

    public final PStringReverseIterator createStringReverseIterator(Object cls, TruffleString str) {
        return trace(new PStringReverseIterator(cls, getShape(cls), str));
    }

    public final PIntegerSequenceIterator createIntegerSequenceIterator(IntSequenceStorage storage, Object list) {
        return trace(new PIntegerSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public final PLongSequenceIterator createLongSequenceIterator(LongSequenceStorage storage, Object list) {
        return trace(new PLongSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public final PDoubleSequenceIterator createDoubleSequenceIterator(DoubleSequenceStorage storage, Object list) {
        return trace(new PDoubleSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public final PObjectSequenceIterator createObjectSequenceIterator(ObjectSequenceStorage storage, Object list) {
        return trace(new PObjectSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), storage, list));
    }

    public final PSequenceIterator createSequenceIterator(Object sequence) {
        return trace(new PSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), sequence));
    }

    public final PSequenceReverseIterator createSequenceReverseIterator(Object cls, Object sequence, int lengthHint) {
        return trace(new PSequenceReverseIterator(cls, getShape(cls), sequence, lengthHint));
    }

    public final PIntRangeIterator createIntRangeIterator(PIntRange fastRange) {
        return createIntRangeIterator(fastRange.getIntStart(), fastRange.getIntStop(), fastRange.getIntStep(), fastRange.getIntLength());
    }

    public final PIntRangeIterator createIntRangeIterator(int start, int stop, int step, int len) {
        return trace(new PIntRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), start, stop, step, len));
    }

    public final PBigRangeIterator createBigRangeIterator(PInt start, PInt stop, PInt step, PInt len) {
        return trace(new PBigRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), start, stop, step, len));
    }

    public final PBigRangeIterator createBigRangeIterator(PBigRange longRange) {
        return createBigRangeIterator(longRange.getPIntStart(), longRange.getPIntStop(), longRange.getPIntStep(), longRange.getPIntLength());
    }

    public final PBigRangeIterator createBigRangeIterator(BigInteger start, BigInteger stop, BigInteger step, BigInteger len) {
        return createBigRangeIterator(createInt(start), createInt(stop), createInt(step), createInt(len));
    }

    public final PArrayIterator createArrayIterator(PArray array) {
        return trace(new PArrayIterator(PythonBuiltinClassType.PArrayIterator, PythonBuiltinClassType.PArrayIterator.getInstanceShape(getLanguage()), array));
    }

    public final PBaseSetIterator createBaseSetIterator(PBaseSet set, HashingStorageNodes.HashingStorageIterator iterator, int initialSize) {
        return trace(new PBaseSetIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.getInstanceShape(getLanguage()), set, iterator, initialSize));
    }

    public final PDictItemIterator createDictItemIterator(HashingStorageNodes.HashingStorageIterator iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictItemIterator(PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictItemIterator.getInstanceShape(getLanguage()), iterator, hashingStorage, initialSize));
    }

    public final PDictKeyIterator createDictKeyIterator(HashingStorageNodes.HashingStorageIterator iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictKeyIterator(PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictKeyIterator.getInstanceShape(getLanguage()), iterator, hashingStorage, initialSize));
    }

    public final PDictValueIterator createDictValueIterator(HashingStorageNodes.HashingStorageIterator iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictValueIterator(PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictValueIterator.getInstanceShape(getLanguage()), iterator, hashingStorage,
                        initialSize));
    }

    public final Object createSentinelIterator(Object callable, Object sentinel) {
        return trace(new PSentinelIterator(PythonBuiltinClassType.PSentinelIterator, PythonBuiltinClassType.PSentinelIterator.getInstanceShape(getLanguage()), callable, sentinel));
    }

    public final PEnumerate createEnumerate(Object cls, Object iterator, long start) {
        return trace(new PEnumerate(cls, getShape(cls), iterator, start));
    }

    public final PEnumerate createEnumerate(Object cls, Object iterator, PInt start) {
        return trace(new PEnumerate(cls, getShape(cls), iterator, start));
    }

    public final PMap createMap(Object cls) {
        return trace(new PMap(cls, getShape(cls)));
    }

    public final PZip createZip(Object cls, Object[] iterables, boolean strict) {
        return trace(new PZip(cls, getShape(cls), iterables, strict));
    }

    public final PForeignArrayIterator createForeignArrayIterator(Object iterable) {
        return trace(new PForeignArrayIterator(PythonBuiltinClassType.PForeignArrayIterator, PythonBuiltinClassType.PForeignArrayIterator.getInstanceShape(getLanguage()), iterable));
    }

    public final PCode createCode(RootCallTarget ct) {
        return trace(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(getLanguage()), ct));
    }

    public final PCode createCode(RootCallTarget ct, int flags, int firstlineno, byte[] linetable, TruffleString filename) {
        return trace(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.getInstanceShape(getLanguage()), ct, flags, firstlineno, linetable, filename));
    }

    public final PCode createCode(RootCallTarget callTarget, Signature signature, CodeUnit codeUnit) {
        return trace(new PCode(PythonBuiltinClassType.PCode, getShape(PythonBuiltinClassType.PCode), callTarget, signature, codeUnit));
    }

    public final PCode createCode(RootCallTarget callTarget, Signature signature, int nlocals, int stacksize, int flags, Object[] constants, TruffleString[] names, TruffleString[] varnames,
                    TruffleString[] freevars, TruffleString[] cellvars, TruffleString filename, TruffleString name, int firstlineno, byte[] linetable) {
        return trace(new PCode(PythonBuiltinClassType.PCode, getShape(PythonBuiltinClassType.PCode), callTarget, signature, nlocals, stacksize, flags, constants, names, varnames, freevars, cellvars,
                        filename, name, firstlineno, linetable));
    }

    public PCode createCode(Supplier<CallTarget> createCode, int flags, int firstlineno, byte[] lnotab, TruffleString filename) {
        return trace(new PCode(PythonBuiltinClassType.PCode, getShape(PythonBuiltinClassType.PCode), createCode, flags, firstlineno, lnotab, filename));
    }

    /*
     * Socket
     */

    public final PSocket createSocket(Object cls) {
        return trace(new PSocket(cls, getShape(cls)));
    }

    /*
     * Threading
     */

    public PThreadLocal createThreadLocal(Object cls, Object[] args, PKeyword[] kwArgs) {
        return trace(new PThreadLocal(cls, getShape(cls), args, kwArgs));
    }

    public final PLock createLock() {
        return createLock(PythonBuiltinClassType.PLock);
    }

    public final PLock createLock(Object cls) {
        return trace(new PLock(cls, getShape(cls)));
    }

    public final PRLock createRLock() {
        return createRLock(PythonBuiltinClassType.PRLock);
    }

    public final PRLock createRLock(Object cls) {
        return trace(new PRLock(cls, getShape(cls)));
    }

    public final PThread createPythonThread(Thread thread) {
        return trace(new PThread(PythonBuiltinClassType.PThread, PythonBuiltinClassType.PThread.getInstanceShape(getLanguage()), thread));
    }

    public final PThread createPythonThread(Object cls, Thread thread) {
        return trace(new PThread(cls, getShape(cls), thread));
    }

    public final PGraalPySemLock createGraalPySemLock(Object cls, TruffleString name, int kind, Semaphore sharedSemaphore) {
        return trace(new PGraalPySemLock(cls, getShape(cls), name, kind, sharedSemaphore));
    }

    public final PScandirIterator createScandirIterator(PythonContext context, Object dirStream, PosixFileHandle path, boolean needsRewind) {
        return trace(new PScandirIterator(PythonBuiltinClassType.PScandirIterator, PythonBuiltinClassType.PScandirIterator.getInstanceShape(getLanguage()), context, dirStream, path, needsRewind));
    }

    public final PDirEntry createDirEntry(Object dirEntryData, PosixFileHandle path) {
        return trace(new PDirEntry(PythonBuiltinClassType.PDirEntry, PythonBuiltinClassType.PDirEntry.getInstanceShape(getLanguage()), dirEntryData, path));
    }

    public final PEncodingMap createEncodingMap(int count2, int count3, byte[] level1, byte[] level23) {
        return trace(new PEncodingMap(PythonBuiltinClassType.PEncodingMap, PythonBuiltinClassType.PEncodingMap.getInstanceShape(getLanguage()), count2, count3, level1, level23));
    }

    public final PMMap createMMap(PythonContext context, Object clazz, Object mmapHandle, int fd, long length, int access) {
        return trace(new PMMap(clazz, getShape(clazz), context, mmapHandle, fd, length, access));
    }

    public final BZ2Object.BZ2Compressor createBZ2Compressor(Object clazz) {
        return trace(BZ2Object.createCompressor(clazz, getShape(clazz)));
    }

    public final BZ2Object.BZ2Decompressor createBZ2Decompressor(Object clazz) {
        return trace(BZ2Object.createDecompressor(clazz, getShape(clazz)));
    }

    public final ZLibCompObject createJavaZLibCompObject(Object clazz, Object stream, int level, int wbits, int strategy, byte[] zdict) {
        return trace(ZLibCompObject.createJava(clazz, getShape(clazz), stream, level, wbits, strategy, zdict));
    }

    public final ZLibCompObject createJavaZLibCompObject(Object clazz, Object stream, int wbits, byte[] zdict) {
        return trace(ZLibCompObject.createJava(clazz, getShape(clazz), stream, wbits, zdict));
    }

    public final ZLibCompObject createNativeZLibCompObject(Object clazz, Object zst, NFIZlibSupport zlibSupport) {
        return trace(ZLibCompObject.createNative(clazz, getShape(clazz), zst, zlibSupport));
    }

    public final LZMAObject.LZMADecompressor createLZMADecompressor(Object clazz, boolean isNative) {
        return trace(LZMAObject.createDecompressor(clazz, getShape(clazz), isNative));
    }

    public final LZMAObject.LZMACompressor createLZMACompressor(Object clazz, boolean isNative) {
        return trace(LZMAObject.createCompressor(clazz, getShape(clazz), isNative));
    }

    public final CSVReader createCSVReader(Object clazz, Object inputIter, CSVDialect dialect) {
        return trace(new CSVReader(clazz, getShape(clazz), inputIter, dialect));
    }

    public final CSVWriter createCSVWriter(Object clazz, Object write, CSVDialect dialect) {
        return trace(new CSVWriter(clazz, getShape(clazz), write, dialect));
    }

    public final CSVDialect createCSVDialect(Object clazz, TruffleString delimiter, int delimiterCodePoint, boolean doubleQuote, TruffleString escapeChar, int escapeCharCodePoint,
                    TruffleString lineTerminator, TruffleString quoteChar, int quoteCharCodePoint, QuoteStyle quoting, boolean skipInitialSpace, boolean strict) {
        return trace(new CSVDialect(clazz, getShape(clazz), delimiter, delimiterCodePoint, doubleQuote, escapeChar, escapeCharCodePoint, lineTerminator, quoteChar, quoteCharCodePoint, quoting,
                        skipInitialSpace, strict));
    }

    public final PFileIO createFileIO(Object clazz) {
        return trace(new PFileIO(clazz, getShape(clazz)));
    }

    public final PChain createChain(Object cls) {
        return trace(new PChain(cls, getShape(cls)));
    }

    public final PCount createCount(Object cls) {
        return trace(new PCount(cls, getShape(cls)));
    }

    public final PIslice createIslice(Object cls) {
        return trace(new PIslice(cls, getShape(cls)));
    }

    public final PPairwise createPairwise(Object cls) {
        return trace(new PPairwise(cls, getShape(cls)));
    }

    public final PPermutations createPermutations(Object cls) {
        return trace(new PPermutations(cls, getShape(cls)));
    }

    public final PProduct createProduct(Object cls) {
        return trace(new PProduct(cls, getShape(cls)));
    }

    public final PRepeat createRepeat(Object cls) {
        return trace(new PRepeat(cls, getShape(cls)));
    }

    public final PAccumulate createAccumulate(Object cls) {
        return trace(new PAccumulate(cls, getShape(cls)));
    }

    public final PDropwhile createDropwhile(Object cls) {
        return trace(new PDropwhile(cls, getShape(cls)));
    }

    public final PCombinations createCombinations(Object cls) {
        return trace(new PCombinations(cls, getShape(cls)));
    }

    public final PCombinationsWithReplacement createCombinationsWithReplacement(Object cls) {
        return trace(new PCombinationsWithReplacement(cls, getShape(cls)));
    }

    public final PCompress createCompress(Object cls) {
        return trace(new PCompress(cls, getShape(cls)));
    }

    public final PCycle createCycle(Object cls) {
        return trace(new PCycle(cls, getShape(cls)));
    }

    public final PFilterfalse createFilterfalse(Object cls) {
        return trace(new PFilterfalse(cls, getShape(cls)));
    }

    public final PGroupBy createGroupBy(Object cls) {
        return trace(new PGroupBy(cls, getShape(cls)));
    }

    public final PGrouper createGrouper(Object cls) {
        return trace(new PGrouper(cls, getShape(cls)));
    }

    public final PGrouper createGrouper(PGroupBy parent, Object tgtKey) {
        return trace(new PGrouper(parent, tgtKey, PythonBuiltinClassType.PGrouper, PythonBuiltinClassType.PGrouper.getInstanceShape(getLanguage())));
    }

    public final PTee createTee() {
        return trace(new PTee(PythonBuiltinClassType.PTee, PythonBuiltinClassType.PTee.getInstanceShape(getLanguage())));
    }

    public final PTee createTee(PTeeDataObject dataObj, int index) {
        return trace(new PTee(dataObj, index, PythonBuiltinClassType.PTee, PythonBuiltinClassType.PTee.getInstanceShape(getLanguage())));
    }

    public final PStarmap createStarmap(Object cls) {
        return trace(new PStarmap(cls, getShape(cls)));
    }

    public final PTakewhile createTakewhile(Object cls) {
        return trace(new PTakewhile(cls, getShape(cls)));
    }

    public final PTeeDataObject createTeeDataObject() {
        return trace(new PTeeDataObject(PythonBuiltinClassType.PTeeDataObject, PythonBuiltinClassType.PTeeDataObject.getInstanceShape(getLanguage())));
    }

    public final PTeeDataObject createTeeDataObject(Object it) {
        return trace(new PTeeDataObject(it, PythonBuiltinClassType.PTeeDataObject, PythonBuiltinClassType.PTeeDataObject.getInstanceShape(getLanguage())));
    }

    public final PZipLongest createZipLongest(Object cls) {
        return trace(new PZipLongest(cls, getShape(cls)));
    }

    public final PTextIO createTextIO(Object clazz) {
        return trace(new PTextIO(clazz, getShape(clazz)));
    }

    public final PStringIO createStringIO(Object clazz) {
        return trace(new PStringIO(clazz, getShape(clazz)));
    }

    public final PBytesIO createBytesIO(Object clazz) {
        return trace(new PBytesIO(clazz, getShape(clazz)));
    }

    public final PBytesIOBuffer createBytesIOBuf(Object clazz, PBytesIO source) {
        return trace(new PBytesIOBuffer(clazz, getShape(clazz), source));
    }

    public final PNLDecoder createNLDecoder(Object clazz) {
        return trace(new PNLDecoder(clazz, getShape(clazz)));
    }

    public final PBuffered createBufferedReader(Object clazz) {
        return trace(new PBuffered(clazz, getShape(clazz), true, false));
    }

    public final PBuffered createBufferedWriter(Object clazz) {
        return trace(new PBuffered(clazz, getShape(clazz), false, true));
    }

    public final PBuffered createBufferedRandom(Object clazz) {
        return trace(new PBuffered(clazz, getShape(clazz), true, true));
    }

    public final PRWPair createRWPair(Object clazz) {
        return trace(new PRWPair(clazz, getShape(clazz)));
    }

    public final PyCArgObject createCArgObject() {
        return trace(new PyCArgObject(PythonBuiltinClassType.CArgObject, getShape(PythonBuiltinClassType.CArgObject)));
    }

    public final CThunkObject createCThunkObject(Object clazz, int nArgs) {
        return trace(new CThunkObject(clazz, getShape(clazz), nArgs));
    }

    public final StructParamObject createStructParamObject(Object clazz) {
        return trace(new StructParamObject(clazz, getShape(clazz)));
    }

    // Don't use directly, use CtypesNodes.CreateCDataObjectNode
    public final CDataObject createCDataObject(Object clazz, Pointer b_ptr, int b_size, boolean b_needsfree) {
        return trace(new CDataObject(clazz, getShape(clazz), b_ptr, b_size, b_needsfree));
    }

    // Don't use directly, use CtypesNodes.CreateCDataObjectNode
    public final PyCFuncPtrObject createPyCFuncPtrObject(Object clazz, Pointer b_ptr, int b_size, boolean b_needsfree) {
        return trace(new PyCFuncPtrObject(clazz, getShape(clazz), b_ptr, b_size, b_needsfree));
    }

    public final CFieldObject createCFieldObject(Object clazz) {
        return trace(new CFieldObject(clazz, getShape(clazz)));
    }

    public final StgDictObject createStgDictObject(Object clazz) {
        return trace(new StgDictObject(clazz, getShape(clazz)));
    }

    public final PSSLContext createSSLContext(Object clazz, SSLMethod method, int verifyFlags, boolean checkHostname, int verifyMode, SSLContext context) {
        return trace(new PSSLContext(clazz, getShape(clazz), method, verifyFlags, checkHostname, verifyMode, context));
    }

    public final PSSLSocket createSSLSocket(Object clazz, PSSLContext context, SSLEngine engine, PSocket socket) {
        return trace(new PSSLSocket(clazz, getShape(clazz), context, engine, socket, createMemoryBIO(), createMemoryBIO(), createMemoryBIO()));
    }

    public final PSSLSocket createSSLSocket(Object clazz, PSSLContext context, SSLEngine engine, PMemoryBIO inbound, PMemoryBIO outbound) {
        return trace(new PSSLSocket(clazz, getShape(clazz), context, engine, null, inbound, outbound, createMemoryBIO()));
    }

    public final PMemoryBIO createMemoryBIO(Object clazz) {
        return trace(new PMemoryBIO(clazz, getShape(clazz)));
    }

    public final PMemoryBIO createMemoryBIO() {
        return trace(new PMemoryBIO(PythonBuiltinClassType.PMemoryBIO, getShape(PythonBuiltinClassType.PMemoryBIO)));
    }

    public final PProperty createProperty() {
        return trace(new PProperty(PythonBuiltinClassType.PProperty, getShape(PythonBuiltinClassType.PProperty)));
    }

    public final PProperty createProperty(Object cls) {
        return trace(new PProperty(cls, getShape(cls)));
    }

    // JSON
    // (not created on fast path, thus TruffleBoundary)

    @TruffleBoundary
    public final PJSONScanner createJSONScanner(Object clazz, boolean strict, Object objectHook, Object objectPairsHook, Object parseFloat, Object parseInt, Object parseConstant) {
        return trace(new PJSONScanner(clazz, getShape(clazz), strict, objectHook, objectPairsHook, parseFloat, parseInt, parseConstant));
    }

    @TruffleBoundary
    public final PJSONEncoder createJSONEncoder(Object clazz, Object markers, Object defaultFn, Object encoder, Object indent, TruffleString keySeparator, TruffleString itemSeparator,
                    boolean sortKeys, boolean skipKeys, boolean allowNan, FastEncode fastEncode) {
        return trace(new PJSONEncoder(clazz, getShape(clazz), markers, defaultFn, encoder, indent, keySeparator, itemSeparator, sortKeys, skipKeys, allowNan, fastEncode));
    }

    public final PDeque createDeque() {
        return trace(new PDeque(PythonBuiltinClassType.PDeque, getShape(PythonBuiltinClassType.PDeque)));
    }

    public final PDeque createDeque(Object cls) {
        return trace(new PDeque(cls, getShape(cls)));
    }

    public final PDequeIter createDequeIter(PDeque deque) {
        return trace(new PDequeIter(PythonBuiltinClassType.PDequeIter, getShape(PythonBuiltinClassType.PDequeIter), deque, false));
    }

    public final PDequeIter createDequeRevIter(PDeque deque) {
        return trace(new PDequeIter(PythonBuiltinClassType.PDequeRevIter, getShape(PythonBuiltinClassType.PDequeRevIter), deque, true));
    }

    public final PSimpleQueue createSimpleQueue(Object cls) {
        return trace(new PSimpleQueue(cls, getShape(cls)));
    }

    public final PContextVar createContextVar(TruffleString name, Object def) {
        return trace(new PContextVar(PythonBuiltinClassType.ContextVar, getShape(PythonBuiltinClassType.ContextVar), name, def));
    }

    public final PContextVarsContext createContextVarsContext() {
        return trace(new PContextVarsContext(PythonBuiltinClassType.ContextVarsContext, getShape(PythonBuiltinClassType.ContextVarsContext)));
    }

    public final PContextIterator createContextIterator(PContextVarsContext ctx, PContextIterator.ItemKind kind) {
        return trace(new PContextIterator(PythonBuiltinClassType.ContextIterator, getShape(PythonBuiltinClassType.ContextIterator), ctx, kind));
    }

    public final PContextVarsContext copyContextVarsContext(PContextVarsContext original) {
        return trace(new PContextVarsContext(original, PythonBuiltinClassType.ContextVarsContext, getShape(PythonBuiltinClassType.ContextVarsContext)));
    }

    public final PContextVarsToken createContextVarsToken(PContextVar var, Object oldValue) {
        return trace(new PContextVarsToken(var, oldValue, PythonBuiltinClassType.ContextVarsToken, getShape(PythonBuiltinClassType.ContextVarsToken)));
    }

    public final PGenericAlias createGenericAlias(Object cls, Object origin, Object arguments) {
        PTuple argumentsTuple;
        if (arguments instanceof PTuple) {
            argumentsTuple = (PTuple) arguments;
        } else {
            argumentsTuple = createTuple(new Object[]{arguments});
        }
        return trace(new PGenericAlias(cls, getShape(cls), origin, argumentsTuple));
    }

    public final PGenericAlias createGenericAlias(Object origin, Object arguments) {
        return createGenericAlias(PythonBuiltinClassType.PGenericAlias, origin, arguments);
    }

    public final PUnionType createUnionType(Object[] args) {
        return trace(new PUnionType(PythonBuiltinClassType.PUnionType, getShape(PythonBuiltinClassType.PUnionType), createTuple(args)));
    }

    public final DigestObject createDigestObject(PythonBuiltinClassType type, String name, Object digest) {
        return trace(DigestObject.create(type, getShape(type), name, digest));
    }

    public final PyCapsule createCapsule(Object pointer, Object name, Object destructor) {
        return trace(new PyCapsule(getLanguage(), pointer, name, destructor));
    }

    public final MultibyteIncrementalDecoderObject createMultibyteIncrementalDecoderObject(Object type) {
        return trace(new MultibyteIncrementalDecoderObject(type, getShape(type)));
    }

    public final MultibyteIncrementalEncoderObject createMultibyteIncrementalEncoderObject(Object type) {
        return trace(new MultibyteIncrementalEncoderObject(type, getShape(type)));
    }

    public final MultibyteStreamReaderObject createMultibyteStreamReaderObject(Object type) {
        return trace(new MultibyteStreamReaderObject(type, getShape(type)));
    }

    public final MultibyteStreamWriterObject createMultibyteStreamWriterObject(Object type) {
        return trace(new MultibyteStreamWriterObject(type, getShape(type)));
    }

    public final MultibyteCodecObject createMultibyteCodecObject(Object type, MultibyteCodec codec) {
        return trace(new MultibyteCodecObject(type, getShape(type), codec));
    }

    public PAsyncGenASend createAsyncGeneratorASend(PAsyncGen receiver, Object message) {
        return trace(new PAsyncGenASend(getLanguage(), receiver, message));
    }

    public PAsyncGenAThrow createAsyncGeneratorAThrow(PAsyncGen receiver, Object arg1, Object arg2, Object arg3) {
        return trace(new PAsyncGenAThrow(getLanguage(), receiver, arg1, arg2, arg3));
    }

    public PAsyncGenWrappedValue createAsyncGeneratorWrappedValue(Object wrapped) {
        return trace(new PAsyncGenWrappedValue(getLanguage(), wrapped));
    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class Lazy extends Node {
        public static Lazy getUncached() {
            return LazyNodeGen.getUncached();
        }

        public final PythonObjectFactory get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        abstract PythonObjectFactory execute(Node inliningTarget);

        @Specialization
        static PythonObjectFactory doIt(@Cached(inline = false) PythonObjectFactory node) {
            return node;
        }
    }
}
