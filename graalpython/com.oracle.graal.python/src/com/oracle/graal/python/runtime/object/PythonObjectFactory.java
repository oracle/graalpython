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
package com.oracle.graal.python.runtime.object;

import java.io.ByteArrayOutputStream;
import java.lang.ref.ReferenceQueue;
import java.math.BigInteger;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.util.concurrent.Semaphore;

import org.graalvm.collections.EconomicMap;
import org.tukaani.xz.FinishableOutputStream;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
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
import com.oracle.graal.python.builtins.objects.function.PGeneratorFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PRangeReverseIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.lzma.PLZMACompressor;
import com.oracle.graal.python.builtins.objects.lzma.PLZMADecompressor;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.posix.PDirEntry;
import com.oracle.graal.python.builtins.objects.posix.PScandirIterator;
import com.oracle.graal.python.builtins.objects.random.PRandom;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator;
import com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PRLock;
import com.oracle.graal.python.builtins.objects.thread.PSemLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.CharSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
@ImportStatic(PythonOptions.class)
@ReportPolymorphism
public abstract class PythonObjectFactory extends Node {

    public static PythonObjectFactory create() {
        return PythonObjectFactoryNodeGen.create();
    }

    public static PythonObjectFactory getUncached() {
        return PythonObjectFactoryNodeGen.getUncached();
    }

    protected abstract AllocationReporter executeTrace(Object o, long size);

    protected abstract DynamicObject executeMakeStorage(Object o, boolean flag);

    @Specialization(guards = "cls == cachedCls", limit = "getCallSiteInlineCacheMaxDepth()")
    static final DynamicObject makeStorageCachedType(@SuppressWarnings("unused") PythonBuiltinClassType cls, @SuppressWarnings("unused") boolean flag,
                    @Cached("cls") PythonBuiltinClassType cachedCls) {
        return cachedCls.newInstance();
    }

    protected static final Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    @Specialization(guards = "cls == cachedCls", limit = "getCallSiteInlineCacheMaxDepth()", replaces = "makeStorageCachedType", assumptions = "singleContextAssumption()")
    static final DynamicObject makeStorageCachedClass(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") boolean flag,
                    @Cached("cls") Object cachedCls,
                    @Cached TypeNodes.GetInstanceShape getShapeNode) {
        return getShapeNode.execute(cachedCls).newInstance();
    }

    @Specialization(replaces = "makeStorageCachedClass")
    static final DynamicObject makeStorageGeneric(Object o, @SuppressWarnings("unused") boolean flag,
                    @Cached TypeNodes.GetInstanceShape getShapeNode) {
        return newInstance(getShapeNode.execute(o));
    }

    @TruffleBoundary(allowInlining = true)
    private static final DynamicObject newInstance(Shape shape) {
        return shape.newInstance();
    }

    @Specialization
    static final AllocationReporter doTrace(Object o, long size,
                    @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") ContextReference<PythonContext> contextRef,
                    @Cached(value = "getAllocationReporter(contextRef)", allowUncached = true) AllocationReporter reporter) {
        if (reporter.isActive()) {
            reporter.onEnter(null, 0, size);
            reporter.onReturnValue(o, 0, size);
        }
        return null;
    }

    @SuppressWarnings("static-method")
    protected static AllocationReporter getAllocationReporter(ContextReference<PythonContext> contextRef) {
        return contextRef.get().getEnv().lookup(AllocationReporter.class);
    }

    public final DynamicObject makeStorage(Object cls) {
        return executeMakeStorage(cls, true);
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
    public PythonObject createPythonObject(Object cls) {
        return createPythonObject(cls, makeStorage(cls));
    }

    /**
     * Creates a Python object with the given shape. Python object shapes store the class in the
     * ObjectType.
     */
    public PythonObject createPythonObject(Object klass, DynamicObject storage) {
        return trace(new PythonObject(klass, storage));
    }

    public PythonNativeVoidPtr createNativeVoidPtr(TruffleObject obj) {
        return trace(new PythonNativeVoidPtr(obj));
    }

    public SuperObject createSuperObject(Object self) {
        return trace(new SuperObject(self, makeStorage(self)));
    }

    /*
     * Primitive types
     */
    public PInt createInt(int value) {
        return createInt(PInt.longToBigInteger(value));
    }

    public PInt createInt(long value) {
        return createInt(PInt.longToBigInteger(value));
    }

    public PInt createInt(BigInteger value) {
        return createInt(PythonBuiltinClassType.PInt, value);
    }

    public Object createInt(Object cls, int value) {
        return createInt(cls, PInt.longToBigInteger(value));
    }

    public Object createInt(Object cls, long value) {
        return createInt(cls, PInt.longToBigInteger(value));
    }

    public PInt createInt(Object cls, BigInteger value) {
        return trace(new PInt(cls, makeStorage(cls), value));
    }

    public PFloat createFloat(double value) {
        return createFloat(PythonBuiltinClassType.PFloat, value);
    }

    public PFloat createFloat(Object cls, double value) {
        return trace(new PFloat(cls, makeStorage(cls), value));
    }

    public PString createString(String string) {
        return createString(PythonBuiltinClassType.PString, string);
    }

    public PString createString(Object cls, String string) {
        return trace(new PString(cls, makeStorage(cls), string));
    }

    public PString createString(CharSequence string) {
        return createString(PythonBuiltinClassType.PString, string);
    }

    public PString createString(Object cls, CharSequence string) {
        return trace(new PString(cls, makeStorage(cls), string));
    }

    public PBytes createBytes(byte[] array) {
        return createBytes(PythonBuiltinClassType.PBytes, array);
    }

    public PBytes createBytes(Object cls, byte[] array) {
        return trace(new PBytes(cls, makeStorage(cls), array));
    }

    public PBytes createBytes(SequenceStorage storage) {
        return createBytes(PythonBuiltinClassType.PBytes, storage);
    }

    public PBytes createBytes(Object cls, SequenceStorage storage) {
        return trace(new PBytes(cls, makeStorage(cls), storage));
    }

    public final PTuple createEmptyTuple() {
        return createTuple(new Object[0]);
    }

    public final PTuple createEmptyTuple(Object cls) {
        return createTuple(cls, EmptySequenceStorage.INSTANCE);
    }

    public final PTuple createTuple(Object[] objects) {
        return createTuple(PythonBuiltinClassType.PTuple, objects);
    }

    public final PTuple createTuple(SequenceStorage store) {
        return createTuple(PythonBuiltinClassType.PTuple, store);
    }

    public final PTuple createTuple(Object cls, Object[] objects) {
        return trace(new PTuple(cls, makeStorage(cls), objects));
    }

    public final PTuple createTuple(Object cls, SequenceStorage store) {
        return trace(new PTuple(cls, makeStorage(cls), store));
    }

    public final PComplex createComplex(Object cls, double real, double imag) {
        return trace(new PComplex(cls, makeStorage(cls), real, imag));
    }

    public final PComplex createComplex(double real, double imag) {
        return createComplex(PythonBuiltinClassType.PComplex, real, imag);
    }

    public PRange createRange(int stop) {
        return trace(new PRange(stop));
    }

    public PRange createRange(int start, int stop) {
        return trace(new PRange(start, stop));
    }

    public PRange createRange(int start, int stop, int step) {
        return trace(new PRange(start, stop, step));
    }

    public PRange createRange(PRangeIterator rangeIterator) {
        return createRange(rangeIterator.start, rangeIterator.stop, rangeIterator.step);
    }

    public PRange createRange(PRangeReverseIterator rangeIterator) {
        return createRange(rangeIterator.start, rangeIterator.stop, rangeIterator.step);
    }

    public PSlice createSlice(int start, int stop, int step) {
        return trace(new PSlice(start, stop, step));
    }

    public PRandom createRandom(Object cls) {
        return trace(new PRandom(cls, makeStorage(cls)));
    }

    /*
     * Classes, methods and functions
     */

    public PythonModule createPythonModule(String name) {
        return trace(PythonModule.createInternal(name));
    }

    public PythonModule createPythonModule(Object cls) {
        return trace(new PythonModule(cls, makeStorage(cls)));
    }

    public PythonClass createPythonClass(Object metaclass, String name, PythonAbstractClass[] bases) {
        return trace(new PythonClass(metaclass, makeStorage(metaclass), name, bases));
    }

    public PMemoryView createMemoryView(Object cls, Object value) {
        return trace(new PMemoryView(cls, makeStorage(cls), value));
    }

    public final PMethod createMethod(Object cls, Object self, Object function) {
        return trace(new PMethod(cls, makeStorage(cls), self, function));
    }

    public final PMethod createMethod(Object self, Object function) {
        return createMethod(PythonBuiltinClassType.PMethod, self, function);
    }

    public final PMethod createBuiltinMethod(Object self, PFunction function) {
        return createMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public final PBuiltinMethod createBuiltinMethod(Object cls, Object self, PBuiltinFunction function) {
        return trace(new PBuiltinMethod(cls, makeStorage(cls), self, function));
    }

    public final PBuiltinMethod createBuiltinMethod(Object self, PBuiltinFunction function) {
        return createBuiltinMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(name, name, enclosingClassName, code, globals, closure));
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure) {
        return trace(new PFunction(name, name, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure));
    }

    public PFunction createFunction(String name, String qualname, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure, Assumption codeStableAssumption, Assumption defaultsStableAssumption) {
        return trace(new PFunction(name, qualname, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure, codeStableAssumption,
                        defaultsStableAssumption));
    }

    public PBuiltinFunction createBuiltinFunction(String name, Object type, int numDefaults, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(name, type, numDefaults, callTarget));
    }

    public GetSetDescriptor createGetSetDescriptor(Object get, Object set, String name, Object type) {
        return trace(new GetSetDescriptor(get, set, name, type));
    }

    public GetSetDescriptor createGetSetDescriptor(Object get, Object set, String name, Object type, boolean allowsDelete) {
        return trace(new GetSetDescriptor(get, set, name, type, allowsDelete));
    }

    public HiddenKeyDescriptor createHiddenKeyDescriptor(HiddenKey key, Object type) {
        return trace(new HiddenKeyDescriptor(key, type));
    }

    public PDecoratedMethod createClassmethod(Object cls) {
        return trace(new PDecoratedMethod(cls, makeStorage(cls)));
    }

    public PDecoratedMethod createClassmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PClassmethod, PythonBuiltinClassType.PClassmethod.newInstance(), callable));
    }

    public PDecoratedMethod createStaticmethod(Object cls) {
        return trace(new PDecoratedMethod(cls, makeStorage(cls)));
    }

    public PDecoratedMethod createStaticmethodFromCallableObj(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PStaticmethod, PythonBuiltinClassType.PStaticmethod.newInstance(), callable));
    }

    /*
     * Lists, sets and dicts
     */

    public PList createList() {
        return createList(new Object[0]);
    }

    public PList createList(SequenceStorage storage) {
        return createList(PythonBuiltinClassType.PList, storage);
    }

    public PList createList(SequenceStorage storage, ListLiteralNode origin) {
        return trace(new PList(PythonBuiltinClassType.PList, PythonBuiltinClassType.PList.newInstance(), storage, origin));
    }

    public PList createList(Object cls, SequenceStorage storage) {
        return trace(new PList(cls, makeStorage(cls), storage));
    }

    public PList createList(Object cls) {
        return createList(cls, new Object[0]);
    }

    public PList createList(Object[] array) {
        return createList(PythonBuiltinClassType.PList, array);
    }

    public PList createList(Object cls, Object[] array) {
        return trace(new PList(cls, makeStorage(cls), SequenceStorageFactory.createStorage(array)));
    }

    public PSet createSet(Object cls) {
        return trace(new PSet(cls, makeStorage(cls)));
    }

    public PSet createSet(PythonClass cls, HashingStorage storage) {
        return trace(new PSet(cls, makeStorage(cls), storage));
    }

    public PSet createSet(HashingStorage storage) {
        return trace(new PSet(PythonBuiltinClassType.PSet, PythonBuiltinClassType.PSet.newInstance(), storage));
    }

    public PFrozenSet createFrozenSet(Object cls) {
        return trace(new PFrozenSet(cls, makeStorage(cls)));
    }

    public PFrozenSet createFrozenSet(Object cls, HashingStorage storage) {
        return trace(new PFrozenSet(cls, makeStorage(cls), storage));
    }

    public PFrozenSet createFrozenSet(HashingStorage storage) {
        return createFrozenSet(PythonBuiltinClassType.PFrozenSet, storage);
    }

    public PDict createDict() {
        return createDict(PythonBuiltinClassType.PDict);
    }

    public PDict createDict(PKeyword[] keywords) {
        return trace(new PDict(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.newInstance(), keywords));
    }

    public PDict createDict(Object cls) {
        return trace(new PDict(cls, makeStorage(cls)));
    }

    public PDict createDict(EconomicMap<? extends Object, Object> map) {
        return createDict(EconomicMapStorage.create(map));
    }

    public PDict createDictLocals(MaterializedFrame frame) {
        return createDict(new LocalsStorage(frame));
    }

    public PDict createDictLocals(FrameDescriptor fd) {
        return createDict(new LocalsStorage(fd));
    }

    public PDict createDict(DynamicObject dynamicObject) {
        return createDict(new DynamicObjectStorage(dynamicObject));
    }

    public PDict createDictFixedStorage(PythonObject pythonObject) {
        return createDict(new DynamicObjectStorage(pythonObject.getStorage()));
    }

    public PDict createDict(HashingStorage storage) {
        return trace(new PDict(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.newInstance(), storage));
    }

    public PDictView createDictKeysView(PHashingCollection dict) {
        return trace(new PDictKeysView(PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictKeysView.newInstance(), dict));
    }

    public PDictView createDictValuesView(PHashingCollection dict) {
        return trace(new PDictValuesView(PythonBuiltinClassType.PDictValuesView, PythonBuiltinClassType.PDictValuesView.newInstance(), dict));
    }

    public PDictView createDictItemsView(PHashingCollection dict) {
        return trace(new PDictItemsView(PythonBuiltinClassType.PDictItemsView, PythonBuiltinClassType.PDictItemsView.newInstance(), dict));
    }

    /*
     * Special objects: generators, proxies, references
     */

    public PGenerator createGenerator(String name, String qualname, RootCallTarget[] callTargets, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure, ExecutionCellSlots cellSlots,
                    GeneratorInfo generatorInfo, Object iterator) {
        return trace(PGenerator.create(name, qualname, callTargets, frameDescriptor, arguments, closure, cellSlots, generatorInfo, this, iterator));
    }

    public PGeneratorFunction createGeneratorFunction(String name, String qualname, String enclosingClassName, PCode code, PythonObject globals, PCell[] closure, Object[] defaultValues,
                    PKeyword[] kwDefaultValues) {
        return trace(PGeneratorFunction.create(name, qualname, enclosingClassName, code, globals, closure, defaultValues, kwDefaultValues));
    }

    public PMappingproxy createMappingproxy(PythonObject object) {
        return trace(new PMappingproxy(PythonBuiltinClassType.PMappingproxy, PythonBuiltinClassType.PMappingproxy.newInstance(), new DynamicObjectStorage(object.getStorage())));
    }

    public PMappingproxy createMappingproxy(HashingStorage storage) {
        return trace(new PMappingproxy(PythonBuiltinClassType.PMappingproxy, PythonBuiltinClassType.PMappingproxy.newInstance(), storage));
    }

    public PMappingproxy createMappingproxy(PythonClass cls, PythonObject object) {
        return trace(new PMappingproxy(cls, makeStorage(cls), new DynamicObjectStorage(object.getStorage())));
    }

    public PMappingproxy createMappingproxy(Object cls, HashingStorage storage) {
        return trace(new PMappingproxy(cls, makeStorage(cls), storage));
    }

    public PReferenceType createReferenceType(Object cls, Object object, Object callback, ReferenceQueue<Object> queue) {
        return trace(new PReferenceType(cls, makeStorage(cls), object, callback, queue));
    }

    public PReferenceType createReferenceType(Object object, Object callback, ReferenceQueue<Object> queue) {
        return createReferenceType(PythonBuiltinClassType.PReferenceType, object, callback, queue);
    }

    /*
     * Frames, traces and exceptions
     */

    public PFrame createPFrame(PFrame.Reference frameInfo, Node location, boolean inClassBody) {
        return trace(new PFrame(frameInfo, location, inClassBody));
    }

    public PFrame createPFrame(PFrame.Reference frameInfo, Node location, Object locals, boolean inClassBody) {
        return trace(new PFrame(frameInfo, location, locals, inClassBody));
    }

    public PFrame createPFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
        return trace(new PFrame(threadState, code, globals, locals));
    }

    public PTraceback createTraceback(PFrame frame, int lineno, PTraceback next) {
        return trace(new PTraceback(frame, lineno, next));
    }

    public PTraceback createTraceback(PFrame frame, int lineno, int lasti, PTraceback next) {
        return trace(new PTraceback(frame, lineno, lasti, next));
    }

    public PTraceback createTraceback(LazyTraceback tb) {
        return trace(new PTraceback(tb));
    }

    public PBaseException createBaseException(Object cls, PTuple args) {
        return trace(new PBaseException(cls, makeStorage(cls), args));
    }

    public PBaseException createBaseException(Object cls, String format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(cls, makeStorage(cls), format, args));
    }

    public PBaseException createBaseException(Object cls) {
        return trace(new PBaseException(cls, makeStorage(cls)));
    }

    /*
     * Arrays
     */

    public PArray createArray(Object cls, byte[] array) {
        return trace(new PArray(cls, makeStorage(cls), new ByteSequenceStorage(array)));
    }

    public PArray createArray(Object cls, int[] array) {
        return trace(new PArray(cls, makeStorage(cls), new IntSequenceStorage(array)));
    }

    public PArray createArray(Object cls, double[] array) {
        return trace(new PArray(cls, makeStorage(cls), new DoubleSequenceStorage(array)));
    }

    public PArray createArray(Object cls, char[] array) {
        return trace(new PArray(cls, makeStorage(cls), new CharSequenceStorage(array)));
    }

    public PArray createArray(Object cls, long[] array) {
        return trace(new PArray(cls, makeStorage(cls), new LongSequenceStorage(array)));
    }

    public PArray createArray(Object cls, SequenceStorage store) {
        return trace(new PArray(cls, makeStorage(cls), store));
    }

    public PByteArray createByteArray(Object cls, byte[] array) {
        return trace(new PByteArray(cls, makeStorage(cls), array));
    }

    public PByteArray createByteArray(SequenceStorage storage) {
        return createByteArray(PythonBuiltinClassType.PByteArray, storage);
    }

    public PByteArray createByteArray(Object cls, SequenceStorage storage) {
        return trace(new PByteArray(cls, makeStorage(cls), storage));
    }

    public PArray createArray(byte[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.newInstance(), new ByteSequenceStorage(array)));
    }

    public PArray createArray(int[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.newInstance(), new IntSequenceStorage(array)));
    }

    public PArray createArray(double[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.newInstance(), new DoubleSequenceStorage(array)));
    }

    public PArray createArray(char[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.newInstance(), new CharSequenceStorage(array)));
    }

    public PArray createArray(long[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.newInstance(), new LongSequenceStorage(array)));
    }

    public PArray createArray(SequenceStorage store) {
        return trace(new PArray(PythonBuiltinClassType.PArray, PythonBuiltinClassType.PArray.newInstance(), store));
    }

    public PByteArray createByteArray(byte[] array) {
        return trace(new PByteArray(PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PByteArray.newInstance(), array));
    }

    /*
     * Iterators
     */

    public PStringIterator createStringIterator(String str) {
        return trace(new PStringIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), str));
    }

    public PStringReverseIterator createStringReverseIterator(Object cls, String str) {
        return trace(new PStringReverseIterator(cls, makeStorage(cls), str));
    }

    public PIntegerSequenceIterator createIntegerSequenceIterator(IntSequenceStorage storage) {
        return trace(new PIntegerSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), storage));
    }

    public PLongSequenceIterator createLongSequenceIterator(LongSequenceStorage storage) {
        return trace(new PLongSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), storage));
    }

    public PDoubleSequenceIterator createDoubleSequenceIterator(DoubleSequenceStorage storage) {
        return trace(new PDoubleSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), storage));
    }

    public PSequenceIterator createSequenceIterator(Object sequence) {
        return trace(new PSequenceIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), sequence));
    }

    public PSequenceReverseIterator createSequenceReverseIterator(Object cls, Object sequence, int lengthHint) {
        return trace(new PSequenceReverseIterator(cls, makeStorage(cls), sequence, lengthHint));
    }

    public PIntegerIterator createRangeIterator(int start, int stop, int step, ConditionProfile stepPositiveProfile) {
        PIntegerIterator object;
        if (stepPositiveProfile.profile(step > 0)) {
            object = new PRangeIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), start, stop, step);
        } else {
            object = new PRangeReverseIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), start, stop, -step);
        }
        return trace(object);
    }

    public PArrayIterator createArrayIterator(PArray array) {
        return trace(new PArrayIterator(PythonBuiltinClassType.PArrayIterator, PythonBuiltinClassType.PArrayIterator.newInstance(), array));
    }

    public PBaseSetIterator createBaseSetIterator(PBaseSet set, HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PBaseSetIterator(PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PIterator.newInstance(), set, iterator, hashingStorage, initialSize));
    }

    public PDictItemIterator createDictItemIterator(HashingStorageIterator<DictEntry> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictItemIterator(PythonBuiltinClassType.PDictItemIterator, PythonBuiltinClassType.PDictItemIterator.newInstance(), iterator, hashingStorage, initialSize));
    }

    public PDictItemIterator createDictReverseItemIterator(HashingStorageIterator<DictEntry> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictItemIterator(PythonBuiltinClassType.PDictReverseItemIterator, PythonBuiltinClassType.PDictReverseItemIterator.newInstance(), iterator, hashingStorage,
                        initialSize));
    }

    public PDictKeyIterator createDictKeyIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictKeyIterator(PythonBuiltinClassType.PDictKeyIterator, PythonBuiltinClassType.PDictKeyIterator.newInstance(), iterator, hashingStorage, initialSize));
    }

    public PDictKeyIterator createDictReverseKeyIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictKeyIterator(PythonBuiltinClassType.PDictReverseKeyIterator, PythonBuiltinClassType.PDictReverseKeyIterator.newInstance(), iterator, hashingStorage, initialSize));
    }

    public PDictValueIterator createDictValueIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictValueIterator(PythonBuiltinClassType.PDictValueIterator, PythonBuiltinClassType.PDictValueIterator.newInstance(), iterator, hashingStorage, initialSize));
    }

    public PDictValueIterator createDictReverseValueIterator(HashingStorageIterator<Object> iterator, HashingStorage hashingStorage, int initialSize) {
        return trace(new PDictValueIterator(PythonBuiltinClassType.PDictReverseValueIterator, PythonBuiltinClassType.PDictReverseValueIterator.newInstance(), iterator, hashingStorage,
                        initialSize));
    }

    public Object createSentinelIterator(Object callable, Object sentinel) {
        return trace(new PSentinelIterator(PythonBuiltinClassType.PSentinelIterator, PythonBuiltinClassType.PSentinelIterator.newInstance(), callable, sentinel));
    }

    public PEnumerate createEnumerate(Object cls, Object iterator, long start) {
        return trace(new PEnumerate(cls, makeStorage(cls), iterator, start));
    }

    public PMap createMap(Object cls) {
        return trace(new PMap(cls, makeStorage(cls)));
    }

    public PZip createZip(Object cls, Object[] iterables) {
        return trace(new PZip(cls, makeStorage(cls), iterables));
    }

    public PForeignArrayIterator createForeignArrayIterator(Object iterable) {
        return trace(new PForeignArrayIterator(PythonBuiltinClassType.PForeignArrayIterator, PythonBuiltinClassType.PForeignArrayIterator.newInstance(), iterable));
    }

    public PBuffer createBuffer(Object cls, Object iterable, boolean readonly) {
        return trace(new PBuffer(cls, makeStorage(cls), iterable, readonly));
    }

    public PBuffer createBuffer(Object iterable, boolean readonly) {
        return trace(new PBuffer(PythonBuiltinClassType.PBuffer, PythonBuiltinClassType.PBuffer.newInstance(), iterable, readonly));
    }

    public PCode createCode(RootCallTarget ct) {
        return trace(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.newInstance(), ct));
    }

    public PCode createCode(RootCallTarget ct, byte[] codestring, int flags, int firstlineno, byte[] lnotab) {
        return trace(new PCode(PythonBuiltinClassType.PCode, PythonBuiltinClassType.PCode.newInstance(), ct, codestring, flags, firstlineno, lnotab));
    }

    public PCode createCode(Object cls, RootCallTarget callTarget, Signature signature,
                    int nlocals, int stacksize, int flags,
                    byte[] codestring, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    byte[] lnotab) {
        return trace(new PCode(cls, makeStorage(cls), callTarget, signature,
                        nlocals, stacksize, flags,
                        codestring, constants, names,
                        varnames, freevars, cellvars,
                        filename, name, firstlineno, lnotab));
    }

    public PZipImporter createZipImporter(Object cls, PDict zipDirectoryCache, String separator) {
        return trace(new PZipImporter(cls, makeStorage(cls), zipDirectoryCache, separator));
    }

    /*
     * Socket
     */

    public PSocket createSocket(int family, int type, int proto) {
        return trace(new PSocket(PythonBuiltinClassType.PSocket, PythonBuiltinClassType.PSocket.newInstance(), family, type, proto));
    }

    public PSocket createSocket(Object cls, int family, int type, int proto) {
        return trace(new PSocket(cls, makeStorage(cls), family, type, proto));
    }

    public PSocket createSocket(Object cls, int family, int type, int proto, int fileno) {
        return trace(new PSocket(cls, makeStorage(cls), family, type, proto, fileno));
    }

    /*
     * Threading
     */

    public PLock createLock() {
        return createLock(PythonBuiltinClassType.PLock);
    }

    public PLock createLock(Object cls) {
        return trace(new PLock(cls, makeStorage(cls)));
    }

    public PRLock createRLock() {
        return createRLock(PythonBuiltinClassType.PRLock);
    }

    public PRLock createRLock(Object cls) {
        return trace(new PRLock(cls, makeStorage(cls)));
    }

    public PThread createPythonThread(Thread thread) {
        return trace(new PThread(PythonBuiltinClassType.PThread, PythonBuiltinClassType.PThread.newInstance(), thread));
    }

    public PThread createPythonThread(Object cls, Thread thread) {
        return trace(new PThread(cls, makeStorage(cls), thread));
    }

    public PSemLock createSemLock(Object cls, String name, int kind, Semaphore sharedSemaphore) {
        return trace(new PSemLock(cls, makeStorage(cls), name, kind, sharedSemaphore));
    }

    public PScandirIterator createScandirIterator(Object cls, String path, DirectoryStream<TruffleFile> next) {
        return trace(new PScandirIterator(cls, makeStorage(cls), path, next));
    }

    public PDirEntry createDirEntry(String name, TruffleFile file) {
        return trace(new PDirEntry(PythonBuiltinClassType.PDirEntry, PythonBuiltinClassType.PDirEntry.newInstance(), name, file));
    }

    public Object createDirEntry(Object cls, String name, TruffleFile file) {
        return trace(new PDirEntry(cls, makeStorage(cls), name, file));
    }

    public PMMap createMMap(SeekableByteChannel channel, long length, long offset) {
        return trace(new PMMap(PythonBuiltinClassType.PMMap, PythonBuiltinClassType.PMMap.newInstance(), channel, length, offset));
    }

    public PMMap createMMap(Object clazz, SeekableByteChannel channel, long length, long offset) {
        return trace(new PMMap(clazz, makeStorage(clazz), channel, length, offset));
    }

    public PLZMACompressor createLZMACompressor(Object clazz, FinishableOutputStream lzmaStream, ByteArrayOutputStream bos) {
        return trace(new PLZMACompressor(clazz, makeStorage(clazz), lzmaStream, bos));
    }

    public PLZMADecompressor createLZMADecompressor(Object clazz, int format, int memlimit) {
        return trace(new PLZMADecompressor(clazz, makeStorage(clazz), format, memlimit));
    }
}
