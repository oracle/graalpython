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
import java.util.Iterator;
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
import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
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
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator.PRangeReverseIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.lzma.PLZMACompressor;
import com.oracle.graal.python.builtins.objects.lzma.PLZMADecompressor;
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
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.CharSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
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
public abstract class PythonObjectFactory extends Node {

    public static PythonObjectFactory create() {
        return PythonObjectFactoryNodeGen.create();
    }

    public static PythonObjectFactory getUncached() {
        return PythonObjectFactoryNodeGen.getUncached();
    }

    protected abstract void executeTrace(Object o);

    @Specialization
    static final void doTrace(Object o,
                    @CachedContext(PythonLanguage.class) @SuppressWarnings("unused") ContextReference<PythonContext> contextRef,
                    @Cached(value = "getAllocationReporter(contextRef)", allowUncached = true) AllocationReporter reporter) {
        if (reporter.isActive()) {
            reporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
            reporter.onReturnValue(o, 0, AllocationReporter.SIZE_UNKNOWN);
        }
    }

    @SuppressWarnings("static-method")
    protected static AllocationReporter getAllocationReporter(ContextReference<PythonContext> contextRef) {
        return contextRef.get().getEnv().lookup(AllocationReporter.class);
    }

    public final <T> T trace(T allocatedObject) {
        executeTrace(allocatedObject);
        return allocatedObject;
    }

    /*
     * Python objects
     */

    /**
     * Creates a PythonObject for the given class. This is potentially slightly slower than if the
     * shape had been cached, due to the additional shape lookup.
     */
    public PythonObject createPythonObject(LazyPythonClass cls) {
        return trace(new PythonObject(cls));
    }

    /**
     * Creates a Python object with the given shape. Python object shapes store the class in the
     * ObjectType.
     */
    public PythonObject createPythonObject(LazyPythonClass klass, Shape instanceShape) {
        return trace(new PythonObject(klass, instanceShape));
    }

    public PythonNativeVoidPtr createNativeVoidPtr(TruffleObject obj) {
        return trace(new PythonNativeVoidPtr(obj));
    }

    public SuperObject createSuperObject(LazyPythonClass self) {
        return trace(new SuperObject(self));
    }

    /*
     * Primitive types
     */
    public PInt createInt(int value) {
        return trace(new PInt(PythonBuiltinClassType.PInt, PInt.longToBigInteger(value)));
    }

    public PInt createInt(long value) {
        return trace(new PInt(PythonBuiltinClassType.PInt, PInt.longToBigInteger(value)));
    }

    public PInt createInt(BigInteger value) {
        return trace(new PInt(PythonBuiltinClassType.PInt, value));
    }

    public Object createInt(LazyPythonClass cls, int value) {
        return trace(new PInt(cls, PInt.longToBigInteger(value)));
    }

    public Object createInt(LazyPythonClass cls, long value) {
        return trace(new PInt(cls, PInt.longToBigInteger(value)));
    }

    public PInt createInt(LazyPythonClass cls, BigInteger value) {
        return trace(new PInt(cls, value));
    }

    public PFloat createFloat(double value) {
        return trace(new PFloat(PythonBuiltinClassType.PFloat, value));
    }

    public PFloat createFloat(LazyPythonClass cls, double value) {
        return trace(new PFloat(cls, value));
    }

    public PString createString(String string) {
        return trace(new PString(PythonBuiltinClassType.PString, string));
    }

    public PString createString(LazyPythonClass cls, String string) {
        return trace(new PString(cls, string));
    }

    public PString createString(CharSequence string) {
        return trace(new PString(PythonBuiltinClassType.PString, string));
    }

    public PString createString(LazyPythonClass cls, CharSequence string) {
        return trace(new PString(cls, string));
    }

    public PBytes createBytes(byte[] array) {
        return trace(new PBytes(PythonBuiltinClassType.PBytes, array));
    }

    public PBytes createBytes(LazyPythonClass cls, byte[] array) {
        return trace(new PBytes(cls, array));
    }

    public PBytes createBytes(SequenceStorage storage) {
        return trace(new PBytes(PythonBuiltinClassType.PBytes, storage));
    }

    public PBytes createBytes(LazyPythonClass cls, SequenceStorage storage) {
        return trace(new PBytes(cls, storage));
    }

    public final PTuple createEmptyTuple() {
        return createTuple(new Object[0]);
    }

    public final PTuple createEmptyTuple(LazyPythonClass cls) {
        return trace(new PTuple(cls, EmptySequenceStorage.INSTANCE));
    }

    public final PTuple createTuple(Object[] objects) {
        return trace(new PTuple(PythonBuiltinClassType.PTuple, objects));
    }

    public final PTuple createTuple(SequenceStorage store) {
        return trace(new PTuple(PythonBuiltinClassType.PTuple, store));
    }

    public final PTuple createTuple(LazyPythonClass cls, Object[] objects) {
        return trace(new PTuple(cls, objects));
    }

    public final PTuple createTuple(LazyPythonClass cls, SequenceStorage store) {
        return trace(new PTuple(cls, store));
    }

    public final PComplex createComplex(LazyPythonClass cls, double real, double imag) {
        return trace(new PComplex(cls, real, imag));
    }

    public final PComplex createComplex(double real, double imag) {
        return createComplex(PythonBuiltinClassType.PComplex, real, imag);
    }

    public PRange createRange(int stop) {
        return trace(new PRange(PythonBuiltinClassType.PRange, stop));
    }

    public PRange createRange(int start, int stop) {
        return trace(new PRange(PythonBuiltinClassType.PRange, start, stop));
    }

    public PRange createRange(int start, int stop, int step) {
        return trace(new PRange(PythonBuiltinClassType.PRange, start, stop, step));
    }

    public PSlice createSlice(int start, int stop, int step) {
        return trace(new PSlice(PythonBuiltinClassType.PSlice, start, stop, step));
    }

    public PRandom createRandom(LazyPythonClass cls) {
        return trace(new PRandom(cls));
    }

    /*
     * Classes, methods and functions
     */

    public PythonModule createPythonModule(String name) {
        return trace(PythonModule.createInternal(name));
    }

    public PythonModule createPythonModule(LazyPythonClass cls) {
        return trace(new PythonModule(cls));
    }

    public PythonClass createPythonClass(LazyPythonClass metaclass, String name, PythonAbstractClass[] bases) {
        return trace(new PythonClass(metaclass, name, bases));
    }

    public PMemoryView createMemoryView(LazyPythonClass metaclass, Object value) {
        return trace(new PMemoryView(metaclass, value));
    }

    public final PMethod createMethod(LazyPythonClass cls, Object self, Object function) {
        return trace(new PMethod(cls, self, function));
    }

    public final PMethod createMethod(Object self, Object function) {
        return createMethod(PythonBuiltinClassType.PMethod, self, function);
    }

    public final PMethod createBuiltinMethod(Object self, PFunction function) {
        return createMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public final PBuiltinMethod createBuiltinMethod(LazyPythonClass cls, Object self, PBuiltinFunction function) {
        return trace(new PBuiltinMethod(cls, self, function));
    }

    public final PBuiltinMethod createBuiltinMethod(Object self, PBuiltinFunction function) {
        return createBuiltinMethod(PythonBuiltinClassType.PBuiltinMethod, self, function);
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(PythonBuiltinClassType.PFunction, name, enclosingClassName, code, globals, closure));
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure) {
        return trace(new PFunction(PythonBuiltinClassType.PFunction, name, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure));
    }

    public PFunction createFunction(String name, String enclosingClassName, PCode code, PythonObject globals, Object[] defaultValues, PKeyword[] kwDefaultValues,
                    PCell[] closure, WriteAttributeToDynamicObjectNode writeAttrNode, Assumption codeStableAssumption, Assumption defaultsStableAssumption) {
        return trace(new PFunction(PythonBuiltinClassType.PFunction, name, enclosingClassName, code, globals, defaultValues, kwDefaultValues, closure, writeAttrNode, codeStableAssumption,
                        defaultsStableAssumption));
    }

    public PBuiltinFunction createBuiltinFunction(String name, LazyPythonClass type, int numDefaults, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(PythonBuiltinClassType.PBuiltinFunction, name, type, numDefaults, callTarget));
    }

    public GetSetDescriptor createGetSetDescriptor(Object get, Object set, String name, LazyPythonClass type) {
        return trace(new GetSetDescriptor(PythonBuiltinClassType.GetSetDescriptor, get, set, name, type));
    }

    public HiddenKeyDescriptor createHiddenKeyDescriptor(HiddenKey key, LazyPythonClass type) {
        return trace(new HiddenKeyDescriptor(PythonBuiltinClassType.GetSetDescriptor, key, type));
    }

    public PDecoratedMethod createClassmethod(LazyPythonClass cls) {
        return trace(new PDecoratedMethod(cls));
    }

    public PDecoratedMethod createClassmethod(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PClassmethod, callable));
    }

    public PDecoratedMethod createStaticmethod(LazyPythonClass cls) {
        return trace(new PDecoratedMethod(cls));
    }

    public PDecoratedMethod createStaticmethod(Object callable) {
        return trace(new PDecoratedMethod(PythonBuiltinClassType.PStaticmethod, callable));
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
        return trace(new PList(PythonBuiltinClassType.PList, storage, origin));
    }

    public PList createList(LazyPythonClass cls, SequenceStorage storage) {
        return trace(new PList(cls, storage));
    }

    public PList createList(LazyPythonClass cls) {
        return createList(cls, new Object[0]);
    }

    public PList createList(Object[] array) {
        return createList(PythonBuiltinClassType.PList, array);
    }

    public PList createList(LazyPythonClass cls, Object[] array) {
        return trace(new PList(cls, SequenceStorageFactory.createStorage(array)));
    }

    public PSet createSet(LazyPythonClass cls) {
        return trace(new PSet(cls));
    }

    public PSet createSet(PythonClass cls, HashingStorage storage) {
        return trace(new PSet(cls, storage));
    }

    public PSet createSet(HashingStorage storage) {
        return trace(new PSet(PythonBuiltinClassType.PSet, storage));
    }

    public PFrozenSet createFrozenSet(LazyPythonClass cls) {
        return trace(new PFrozenSet(cls));
    }

    public PFrozenSet createFrozenSet(PythonClass cls, HashingStorage storage) {
        return trace(new PFrozenSet(cls, storage));
    }

    public PFrozenSet createFrozenSet(HashingStorage storage) {
        return trace(new PFrozenSet(PythonBuiltinClassType.PFrozenSet, storage));
    }

    public PDict createDict() {
        return trace(new PDict(PythonBuiltinClassType.PDict));
    }

    public PDict createDict(PKeyword[] keywords) {
        return trace(new PDict(PythonBuiltinClassType.PDict, keywords));
    }

    public PDict createDict(LazyPythonClass cls) {
        return trace(new PDict(cls));
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
        return trace(new PDict(PythonBuiltinClassType.PDict, storage));
    }

    public PDictView createDictKeysView(PHashingCollection dict) {
        return trace(new PDictKeysView(PythonBuiltinClassType.PDictKeysView, dict));
    }

    public PDictView createDictValuesView(PHashingCollection dict) {
        return trace(new PDictValuesView(PythonBuiltinClassType.PDictValuesView, dict));
    }

    public PDictView createDictItemsView(PHashingCollection dict) {
        return trace(new PDictItemsView(PythonBuiltinClassType.PDictItemsView, dict));
    }

    /*
     * Special objects: generators, proxies, references
     */

    public PGenerator createGenerator(String name, RootCallTarget[] callTargets, FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure, ExecutionCellSlots cellSlots,
                    int numOfActiveFlags, int numOfGeneratorBlockNode, int numOfGeneratorForNode, int numOfGeneratorTryNode, Object iterator) {
        return trace(PGenerator.create(PythonBuiltinClassType.PGenerator, name, callTargets, frameDescriptor, arguments, closure, cellSlots, numOfActiveFlags, numOfGeneratorBlockNode,
                        numOfGeneratorForNode, numOfGeneratorTryNode, this, iterator));
    }

    public PGeneratorFunction createGeneratorFunction(String name, String enclosingClassName, PCode code, PythonObject globals, PCell[] closure, Object[] defaultValues,
                    PKeyword[] kwDefaultValues) {
        return trace(PGeneratorFunction.create(PythonBuiltinClassType.PFunction, name, enclosingClassName, code, globals, closure, defaultValues, kwDefaultValues));
    }

    public PMappingproxy createMappingproxy(PythonObject object) {
        return trace(new PMappingproxy(PythonBuiltinClassType.PMappingproxy, new DynamicObjectStorage(object.getStorage())));
    }

    public PMappingproxy createMappingproxy(HashingStorage storage) {
        return trace(new PMappingproxy(PythonBuiltinClassType.PMappingproxy, storage));
    }

    public PMappingproxy createMappingproxy(PythonClass cls, PythonObject object) {
        return trace(new PMappingproxy(cls, new DynamicObjectStorage(object.getStorage())));
    }

    public PMappingproxy createMappingproxy(LazyPythonClass cls, HashingStorage storage) {
        return trace(new PMappingproxy(cls, storage));
    }

    public PReferenceType createReferenceType(LazyPythonClass cls, Object object, Object callback, ReferenceQueue<Object> queue) {
        return trace(new PReferenceType(cls, object, callback, queue));
    }

    public PReferenceType createReferenceType(Object object, Object callback, ReferenceQueue<Object> queue) {
        return createReferenceType(PythonBuiltinClassType.PReferenceType, object, callback, queue);
    }

    /*
     * Frames, traces and exceptions
     */

    public PFrame createPFrame(PFrame.Reference frameInfo, Node location, boolean inClassBody) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, frameInfo, location, inClassBody));
    }

    public PFrame createPFrame(PFrame.Reference frameInfo, Node location, Object locals, boolean inClassBody) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, frameInfo, location, locals, inClassBody));
    }

    public PFrame createPFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
        return trace(new PFrame(PythonBuiltinClassType.PFrame, threadState, code, globals, locals));
    }

    public PTraceback createTraceback(PFrame frame, int lineno, PTraceback next) {
        return trace(new PTraceback(PythonBuiltinClassType.PTraceback, frame, lineno, next));
    }

    public PTraceback createTraceback(LazyTraceback tb) {
        return trace(new PTraceback(PythonBuiltinClassType.PTraceback, tb));
    }

    public PBaseException createBaseException(LazyPythonClass cls, PTuple args) {
        return trace(new PBaseException(cls, args));
    }

    public PBaseException createBaseException(LazyPythonClass cls, String format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(cls, format, args));
    }

    public PBaseException createBaseException(LazyPythonClass cls) {
        return trace(new PBaseException(cls, createEmptyTuple()));
    }

    /*
     * Arrays
     */

    public PArray createArray(LazyPythonClass cls, byte[] array) {
        return trace(new PArray(cls, new ByteSequenceStorage(array)));
    }

    public PArray createArray(LazyPythonClass cls, int[] array) {
        return trace(new PArray(cls, new IntSequenceStorage(array)));
    }

    public PArray createArray(LazyPythonClass cls, double[] array) {
        return trace(new PArray(cls, new DoubleSequenceStorage(array)));
    }

    public PArray createArray(LazyPythonClass cls, char[] array) {
        return trace(new PArray(cls, new CharSequenceStorage(array)));
    }

    public PArray createArray(LazyPythonClass cls, long[] array) {
        return trace(new PArray(cls, new LongSequenceStorage(array)));
    }

    public PArray createArray(LazyPythonClass cls, SequenceStorage store) {
        return trace(new PArray(cls, store));
    }

    public PByteArray createByteArray(LazyPythonClass cls, byte[] array) {
        return trace(new PByteArray(cls, array));
    }

    public PByteArray createByteArray(SequenceStorage storage) {
        return createByteArray(PythonBuiltinClassType.PByteArray, storage);
    }

    public PByteArray createByteArray(LazyPythonClass cls, SequenceStorage storage) {
        return trace(new PByteArray(cls, storage));
    }

    public PArray createArray(byte[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new ByteSequenceStorage(array)));
    }

    public PArray createArray(int[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new IntSequenceStorage(array)));
    }

    public PArray createArray(double[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new DoubleSequenceStorage(array)));
    }

    public PArray createArray(char[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new CharSequenceStorage(array)));
    }

    public PArray createArray(long[] array) {
        return trace(new PArray(PythonBuiltinClassType.PArray, new LongSequenceStorage(array)));
    }

    public PArray createArray(SequenceStorage store) {
        return trace(new PArray(PythonBuiltinClassType.PArray, store));
    }

    public PByteArray createByteArray(byte[] array) {
        return trace(new PByteArray(PythonBuiltinClassType.PByteArray, array));
    }

    /*
     * Iterators
     */

    public PStringIterator createStringIterator(String str) {
        return trace(new PStringIterator(PythonBuiltinClassType.PIterator, str));
    }

    public PStringReverseIterator createStringReverseIterator(LazyPythonClass cls, String str) {
        return trace(new PStringReverseIterator(cls, str));
    }

    public PIntegerSequenceIterator createIntegerSequenceIterator(IntSequenceStorage storage) {
        return trace(new PIntegerSequenceIterator(PythonBuiltinClassType.PIterator, storage));
    }

    public PLongSequenceIterator createLongSequenceIterator(LongSequenceStorage storage) {
        return trace(new PLongSequenceIterator(PythonBuiltinClassType.PIterator, storage));
    }

    public PDoubleSequenceIterator createDoubleSequenceIterator(DoubleSequenceStorage storage) {
        return trace(new PDoubleSequenceIterator(PythonBuiltinClassType.PIterator, storage));
    }

    public PSequenceIterator createSequenceIterator(Object sequence) {
        return trace(new PSequenceIterator(PythonBuiltinClassType.PIterator, sequence));
    }

    public PSequenceReverseIterator createSequenceReverseIterator(LazyPythonClass cls, Object sequence, int lengthHint) {
        return trace(new PSequenceReverseIterator(cls, sequence, lengthHint));
    }

    public PIntegerIterator createRangeIterator(int start, int stop, int step, ConditionProfile stepPositiveProfile) {
        PIntegerIterator object;
        if (stepPositiveProfile.profile(step > 0)) {
            object = new PRangeIterator(PythonBuiltinClassType.PIterator, start, stop, step);
        } else {
            object = new PRangeReverseIterator(PythonBuiltinClassType.PIterator, start, stop, -step);
        }
        return trace(object);
    }

    public PArrayIterator createArrayIterator(PArray array) {
        return trace(new PArrayIterator(PythonBuiltinClassType.PArrayIterator, array));
    }

    public PBaseSetIterator createBaseSetIterator(PBaseSet set, Iterator<Object> iterator) {
        return trace(new PBaseSetIterator(PythonBuiltinClassType.PIterator, set, iterator));
    }

    public PDictView.PDictItemsIterator createDictItemsIterator(Iterator<DictEntry> iterator) {
        return trace(new PDictView.PDictItemsIterator(PythonBuiltinClassType.PDictItemsIterator, iterator));
    }

    public PDictView.PDictKeysIterator createDictKeysIterator(PHashingCollection dict) {
        return trace(new PDictView.PDictKeysIterator(PythonBuiltinClassType.PDictKeysIterator, dict));
    }

    public PDictView.PDictValuesIterator createDictValuesIterator(Iterator<Object> iterator) {
        return trace(new PDictView.PDictValuesIterator(PythonBuiltinClassType.PDictValuesIterator, iterator));
    }

    public Object createSentinelIterator(Object callable, Object sentinel) {
        return trace(new PSentinelIterator(PythonBuiltinClassType.PSentinelIterator, callable, sentinel));
    }

    public PEnumerate createEnumerate(LazyPythonClass cls, Object iterator, long start) {
        return trace(new PEnumerate(cls, iterator, start));
    }

    public PZip createZip(LazyPythonClass cls, Object[] iterables) {
        return trace(new PZip(cls, iterables));
    }

    public PForeignArrayIterator createForeignArrayIterator(Object iterable, int size) {
        return trace(new PForeignArrayIterator(PythonBuiltinClassType.PForeignArrayIterator, iterable, size));
    }

    public PBuffer createBuffer(LazyPythonClass cls, Object iterable, boolean readonly) {
        return trace(new PBuffer(cls, iterable, readonly));
    }

    public PBuffer createBuffer(Object iterable, boolean readonly) {
        return trace(new PBuffer(PythonBuiltinClassType.PBuffer, iterable, readonly));
    }

    public PCode createCode(RootCallTarget ct) {
        return trace(new PCode(PythonBuiltinClassType.PCode, ct));
    }

    public PCode createCode(LazyPythonClass cls, RootCallTarget callTarget, Signature signature,
                    int nlocals, int stacksize, int flags,
                    byte[] codestring, Object[] constants, Object[] names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    byte[] lnotab) {
        return trace(new PCode(cls, callTarget, signature,
                        nlocals, stacksize, flags,
                        codestring, constants, names,
                        varnames, freevars, cellvars,
                        filename, name, firstlineno, lnotab));
    }

    public PZipImporter createZipImporter(LazyPythonClass cls, PDict zipDirectoryCache, String separator) {
        return trace(new PZipImporter(cls, zipDirectoryCache, separator));
    }

    /*
     * Socket
     */

    public PSocket createSocket(int family, int type, int proto) {
        return trace(new PSocket(PythonBuiltinClassType.PSocket, family, type, proto));
    }

    public PSocket createSocket(LazyPythonClass cls, int family, int type, int proto) {
        return trace(new PSocket(cls, family, type, proto));
    }

    public PSocket createSocket(LazyPythonClass cls, int family, int type, int proto, int fileno) {
        return trace(new PSocket(cls, family, type, proto, fileno));
    }

    /*
     * Threading
     */

    public PLock createLock() {
        return trace(new PLock(PythonBuiltinClassType.PLock));
    }

    public PLock createLock(LazyPythonClass cls) {
        return trace(new PLock(cls));
    }

    public PRLock createRLock() {
        return trace(new PRLock(PythonBuiltinClassType.PRLock));
    }

    public PRLock createRLock(LazyPythonClass cls) {
        return trace(new PRLock(cls));
    }

    public PThread createPythonThread(Thread thread) {
        return trace(new PThread(PythonBuiltinClassType.PThread, thread));
    }

    public PThread createPythonThread(LazyPythonClass cls, Thread thread) {
        return trace(new PThread(cls, thread));
    }

    public PSemLock createSemLock(LazyPythonClass cls, String name, int kind, Semaphore sharedSemaphore) {
        return trace(new PSemLock(cls, name, kind, sharedSemaphore));
    }

    public PScandirIterator createScandirIterator(LazyPythonClass cls, String path, DirectoryStream<TruffleFile> next) {
        return trace(new PScandirIterator(cls, path, next));
    }

    public PDirEntry createDirEntry(String name, TruffleFile file) {
        return trace(new PDirEntry(PythonBuiltinClassType.PDirEntry, name, file));
    }

    public Object createDirEntry(LazyPythonClass cls, String name, TruffleFile file) {
        return trace(new PDirEntry(cls, name, file));
    }

    public PMMap createMMap(SeekableByteChannel channel, long length, long offset) {
        return trace(new PMMap(PythonBuiltinClassType.PMMap, channel, length, offset));
    }

    public PMMap createMMap(LazyPythonClass clazz, SeekableByteChannel channel, long length, long offset) {
        return trace(new PMMap(clazz, channel, length, offset));
    }

    public PLZMACompressor createLZMACompressor(LazyPythonClass clazz, FinishableOutputStream lzmaStream, ByteArrayOutputStream bos) {
        return trace(new PLZMACompressor(clazz, lzmaStream, bos));
    }

    public PLZMADecompressor createLZMADecompressor(LazyPythonClass clazz, int format, int memlimit) {
        return trace(new PLZMADecompressor(clazz, format, memlimit));
    }
}
