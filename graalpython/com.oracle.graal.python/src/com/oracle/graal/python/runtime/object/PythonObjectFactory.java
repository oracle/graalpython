/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.array.PCharArray;
import com.oracle.graal.python.builtins.objects.array.PDoubleArray;
import com.oracle.graal.python.builtins.objects.array.PIntArray;
import com.oracle.graal.python.builtins.objects.array.PLongArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.FastDictStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.PythonObjectDictStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.LocalsStorage;
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
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PGeneratorFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PCharArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PLongSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator.PRangeReverseIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSentinelIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.random.PRandom;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator;
import com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorageFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

public abstract class PythonObjectFactory extends Node {

    private static final PythonObjectFactory SINGLETON = new PythonObjectFactory() {

        @Override
        protected PythonCore getCore() {
            // TODO(ls): re-enable assertion
            // CompilerAsserts.neverPartOfCompilation();
            return PythonLanguage.getCore();
        }
    };

    public static PythonObjectFactory get() {
        // CompilerAsserts.neverPartOfCompilation();
        return SINGLETON;
    }

    public static PythonObjectFactory create() {
        return new PythonObjectFactory() {
            @CompilationFinal private PythonCore core;

            @Override
            public NodeCost getCost() {
                return core == null ? NodeCost.UNINITIALIZED : NodeCost.MONOMORPHIC;
            }

            @Override
            protected PythonCore getCore() {
                if (core == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    core = PythonLanguage.getContext().getCore();
                }
                return core;
            }
        };
    }

    protected abstract PythonCore getCore();

    private PythonClass lookupClass(PythonBuiltinClassType type) {
        return getCore().lookupType(type);
    }

    @SuppressWarnings("static-method")
    public final <T> T trace(T allocatedObject) {
        return allocatedObject;
    }

    /*
     * Python objects
     */

    @CompilationFinal private Optional<Shape> cachedInstanceShape = Optional.empty();

    public PythonObject createPythonObject(PythonClass cls) {
        if (cls == null) {
            CompilerDirectives.transferToInterpreter();
            // special case for base type class
            return trace(new PythonObject(null));
        } else {
            Optional<Shape> cached = cachedInstanceShape;
            if (cached != null) {
                if (cached.isPresent()) {
                    if (cached.get() == cls.getInstanceShape()) {
                        return trace(new PythonObject(cls, cached.get()));
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        cachedInstanceShape = null;
                    }
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedInstanceShape = Optional.of(cls.getInstanceShape());
                }
            }
            return trace(new PythonObject(cls, cls.getInstanceShape()));
        }
    }

    public PythonNativeObject createNativeObjectWrapper(Object obj) {
        return trace(new PythonNativeObject(obj));
    }

    /*
     * Primitive types
     */
    @CompilationFinal PInt pyTrue = null;
    @CompilationFinal PInt pyFalse = null;

    public PInt createInt(boolean value) {
        if (value && pyTrue == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pyTrue = new PInt(lookupClass(PythonBuiltinClassType.Boolean), BigInteger.ONE);
        } else if (!value && pyFalse == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pyFalse = new PInt(lookupClass(PythonBuiltinClassType.Boolean), BigInteger.ZERO);
        }
        return value ? pyTrue : pyFalse;
    }

    public PInt createInt(int value) {
        return trace(new PInt(lookupClass(PythonBuiltinClassType.PInt), BigInteger.valueOf(value)));
    }

    public PInt createInt(long value) {
        return trace(new PInt(lookupClass(PythonBuiltinClassType.PInt), BigInteger.valueOf(value)));
    }

    public PInt createInt(BigInteger value) {
        return trace(new PInt(lookupClass(PythonBuiltinClassType.PInt), value));
    }

    public Object createInt(PythonClass cls, int value) {
        return trace(new PInt(cls, BigInteger.valueOf(value)));
    }

    public Object createInt(PythonClass cls, long value) {
        return trace(new PInt(cls, BigInteger.valueOf(value)));
    }

    public PInt createInt(PythonClass cls, BigInteger value) {
        return trace(new PInt(cls, value));
    }

    public PFloat createFloat(double value) {
        return trace(new PFloat(lookupClass(PythonBuiltinClassType.PFloat), value));
    }

    public PFloat createFloat(PythonClass cls, double value) {
        return trace(new PFloat(cls, value));
    }

    public PString createString(String string) {
        return trace(new PString(lookupClass(PythonBuiltinClassType.PString), string));
    }

    public PString createString(PythonClass cls, String string) {
        return trace(new PString(cls, string));
    }

    public PBytes createBytes(byte[] array) {
        return trace(new PBytes(lookupClass(PythonBuiltinClassType.PBytes), array));
    }

    public PBytes createBytes(PythonClass cls, byte[] array) {
        return trace(new PBytes(cls, array));
    }

    public Object createBytes(PythonClass cls, ByteSequenceStorage storage) {
        return trace(new PBytes(cls, storage));
    }

    public final PTuple createEmptyTuple() {
        return createTuple(new Object[0]);
    }

    public final PTuple createEmptyTuple(PythonClass cls) {
        return trace(new PTuple(cls, new Object[0]));
    }

    public final PTuple createTuple(Object[] objects) {
        return trace(new PTuple(lookupClass(PythonBuiltinClassType.PTuple), objects));
    }

    public final PTuple createTuple(PythonClass cls, Object[] objects) {
        return trace(new PTuple(cls, objects));
    }

    public final PComplex createComplex(PythonClass cls, double real, double imag) {
        return trace(new PComplex(cls, real, imag));
    }

    public final PComplex createComplex(double real, double imag) {
        return createComplex(lookupClass(PythonBuiltinClassType.PComplex), real, imag);
    }

    public PRange createRange(int stop) {
        return trace(new PRange(lookupClass(PythonBuiltinClassType.PRange), stop));
    }

    public PRange createRange(int start, int stop) {
        return trace(new PRange(lookupClass(PythonBuiltinClassType.PRange), start, stop));
    }

    public PRange createRange(int start, int stop, int step) {
        return trace(new PRange(lookupClass(PythonBuiltinClassType.PRange), start, stop, step));
    }

    public PSlice createSlice(int start, int stop, int step) {
        return trace(new PSlice(lookupClass(PythonBuiltinClassType.PSlice), start, stop, step));
    }

    public PRandom createRandom(PythonClass cls) {
        return trace(new PRandom(cls));
    }

    /*
     * Classes, methods and functions
     */

    public PythonModule createPythonModule(String name) {
        return trace(new PythonModule(lookupClass(PythonBuiltinClassType.PythonModule), name));
    }

    public PythonClass createPythonClass(PythonClass metaclass, String name, PythonClass[] bases) {
        return trace(new PythonClass(metaclass, name, bases));
    }

    public PythonNativeClass createNativeClassWrapper(Object object, PythonClass metaClass, String name, PythonClass[] pythonClasses) {
        return trace(new PythonNativeClass(object, metaClass, name, pythonClasses));
    }

    public PMemoryView createMemoryView(PythonClass metaclass, Object value) {
        return trace(new PMemoryView(metaclass, value));
    }

    public final PMethod createMethod(PythonClass cls, Object self, PFunction function) {
        return trace(new PMethod(cls, self, function));
    }

    public final PMethod createMethod(Object self, PFunction function) {
        return createMethod(lookupClass(PythonBuiltinClassType.PMethod), self, function);
    }

    public final PBuiltinMethod createBuiltinMethod(PythonClass cls, Object instance, PBuiltinFunction self) {
        return trace(new PBuiltinMethod(cls, instance, self));
    }

    public final PBuiltinMethod createBuiltinMethod(Object instance, PBuiltinFunction self) {
        return createBuiltinMethod(lookupClass(PythonBuiltinClassType.PBuiltinMethod), instance, self);
    }

    public PFunction createFunction(String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, FrameDescriptor frameDescriptor, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(lookupClass(PythonBuiltinClassType.PFunction), name, enclosingClassName, arity, callTarget, frameDescriptor, globals, closure));
    }

    public PBuiltinFunction createFunction(String name, Arity arity, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(lookupClass(PythonBuiltinClassType.PFunction), name, arity, callTarget));
    }

    public PFunction createBuiltinFunction(String name, String enclosingClassName, Arity arity, RootCallTarget callTarget, FrameDescriptor frameDescriptor, PythonObject globals, PCell[] closure) {
        return trace(new PFunction(lookupClass(PythonBuiltinClassType.PBuiltinFunction), name, enclosingClassName, arity, callTarget, frameDescriptor, globals, closure));
    }

    public PBuiltinFunction createBuiltinFunction(String name, Arity arity, RootCallTarget callTarget) {
        return trace(new PBuiltinFunction(lookupClass(PythonBuiltinClassType.PBuiltinFunction), name, arity, callTarget));
    }

    public GetSetDescriptor createGetSetDescriptor(PythonCallable get, PythonCallable set, String name, PythonClass type) {
        return trace(new GetSetDescriptor(lookupClass(PythonBuiltinClassType.GetSetDescriptor), get, set, name, type));
    }

    /*
     * Lists, sets and dicts
     */

    @CompilationFinal private SequenceStorageFactory sequenceStorageFactory;

    public PList createList() {
        return createList(new Object[0]);
    }

    public PList createList(SequenceStorage storage) {
        return createList(lookupClass(PythonBuiltinClassType.PList), storage);
    }

    public PList createList(PythonClass cls, SequenceStorage storage) {
        return trace(new PList(cls, storage));
    }

    public PList createList(PythonClass cls) {
        return createList(cls, new Object[0]);
    }

    public PList createList(Object[] array) {
        return createList(lookupClass(PythonBuiltinClassType.PList), array);
    }

    public PList createList(PythonClass cls, Object[] array) {
        if (sequenceStorageFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            sequenceStorageFactory = new SequenceStorageFactory();
        }
        return trace(new PList(cls, sequenceStorageFactory.createStorage(array)));
    }

    public PSet createSet() {
        return trace(new PSet(lookupClass(PythonBuiltinClassType.PSet)));
    }

    public PSet createSet(PythonClass cls) {
        return trace(new PSet(cls));
    }

    public PSet createSet(PythonClass cls, HashingStorage storage) {
        return trace(new PSet(cls, storage));
    }

    public PSet createSet(HashingStorage storage) {
        return trace(new PSet(lookupClass(PythonBuiltinClassType.PSet), storage));
    }

    public PFrozenSet createFrozenSet(PythonClass cls) {
        return trace(new PFrozenSet(cls));
    }

    public PFrozenSet createFrozenSet(PythonClass cls, HashingStorage storage) {
        return trace(new PFrozenSet(cls, storage));
    }

    public PFrozenSet createFrozenSet(HashingStorage storage) {
        return trace(new PFrozenSet(lookupClass(PythonBuiltinClassType.PFrozenSet), storage));
    }

    public PDict createDict() {
        return trace(new PDict(lookupClass(PythonBuiltinClassType.PDict)));
    }

    public PDict createDict(PKeyword[] keywords) {
        return trace(new PDict(lookupClass(PythonBuiltinClassType.PDict), keywords));
    }

    public PDict createDict(PythonClass cls) {
        return trace(new PDict(cls));
    }

    public PDict createDict(Map<? extends Object, ? extends Object> map) {
        return createDict(new HashMapStorage(map));
    }

    public PDict createDictLocals(Frame frame, boolean skipCells) {
        return createDict(new LocalsStorage(frame, skipCells));
    }

    public PDict createDict(DynamicObject dynamicObject) {
        return createDict(new FastDictStorage(dynamicObject));
    }

    public PDict createDictFixedStorage(PythonObject pythonObject) {
        return createDict(new PythonObjectDictStorage(pythonObject.getStorage()));
    }

    public PDict createDict(HashingStorage storage) {
        return trace(new PDict(lookupClass(PythonBuiltinClassType.PDict), storage));
    }

    public PDictView createDictKeysView(PDict dict) {
        return trace(new PDictKeysView(lookupClass(PythonBuiltinClassType.PDictKeysView), dict));
    }

    public PDictView createDictValuesView(PDict dict) {
        return trace(new PDictValuesView(lookupClass(PythonBuiltinClassType.PDictValuesView), dict));
    }

    public PDictView createDictItemsView(PDict dict) {
        return trace(new PDictItemsView(lookupClass(PythonBuiltinClassType.PDictItemsView), dict));
    }

    /*
     * Special objects: generators, proxies, references
     */

    public PGenerator createGenerator(String name, RootCallTarget callTarget,
                    FrameDescriptor frameDescriptor, Object[] arguments, PCell[] closure, ExecutionCellSlots cellSlots,
                    int numOfActiveFlags, int numOfGeneratorBlockNode, int numOfGeneratorForNode) {
        return trace(PGenerator.create(lookupClass(PythonBuiltinClassType.PGenerator), name, callTarget, frameDescriptor, arguments, closure, cellSlots,
                        numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode));
    }

    public PGeneratorFunction createGeneratorFunction(String name, String enclosingClassName, Arity arity, RootCallTarget callTarget,
                    FrameDescriptor frameDescriptor, PythonObject globals, PCell[] closure, ExecutionCellSlots cellSlots,
                    int numOfActiveFlags, int numOfGeneratorBlockNode, int numOfGeneratorForNode) {
        return trace(PGeneratorFunction.create(lookupClass(PythonBuiltinClassType.PGeneratorFunction), getCore(), name, enclosingClassName, arity, callTarget,
                        frameDescriptor, globals, closure, cellSlots, numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode));
    }

    public PMappingproxy createMappingproxy(PythonObject self) {
        return trace(new PMappingproxy(lookupClass(PythonBuiltinClassType.PMappingproxy), self));
    }

    public PMappingproxy createMappingproxy(PythonClass cls, PythonObject object) {
        return trace(new PMappingproxy(cls, object));
    }

    public PReferenceType createReferenceType(PythonClass cls, PythonObject object, PFunction callback) {
        return trace(new PReferenceType(cls, object, callback));
    }

    public PReferenceType createReferenceType(PythonObject object, PFunction callback) {
        return createReferenceType(lookupClass(PythonBuiltinClassType.PReferenceType), object, callback);
    }

    /*
     * Frames, traces and exceptions
     */

    public PFrame createPFrame(PBaseException exception, int index) {
        return trace(new PFrame(lookupClass(PythonBuiltinClassType.PFrame), exception, index));
    }

    public PFrame createPFrame(Object threadState, PCode code, PythonObject globals, Object locals) {
        return trace(new PFrame(lookupClass(PythonBuiltinClassType.PFrame), threadState, code, globals, locals));
    }

    public PTraceback createTraceback(PBaseException exception, int index) {
        return trace(new PTraceback(lookupClass(PythonBuiltinClassType.PTraceback), exception, index));
    }

    public PBaseException createBaseException(PythonClass cls, PTuple args) {
        return trace(new PBaseException(cls, args));
    }

    public PBaseException createBaseException(PythonClass cls, String format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(cls, format, args));
    }

    public PBaseException createBaseException(PythonErrorType typ, String format, Object[] args) {
        assert format != null;
        return trace(new PBaseException(getCore().getErrorClass(typ), format, args));
    }

    public PBaseException createBaseException(PythonClass cls) {
        return trace(new PBaseException(cls, createEmptyTuple()));
    }

    /*
     * Arrays
     */

    public PIntArray createIntArray(PythonClass cls, int[] array) {
        return trace(new PIntArray(cls, array));
    }

    public PDoubleArray createDoubleArray(PythonClass cls, double[] array) {
        return trace(new PDoubleArray(cls, array));
    }

    public PCharArray createCharArray(PythonClass cls, char[] array) {
        return trace(new PCharArray(cls, array));
    }

    public PLongArray createLongArray(PythonClass cls, long[] array) {
        return trace(new PLongArray(cls, array));
    }

    public PByteArray createByteArray(PythonClass cls, byte[] array) {
        return trace(new PByteArray(cls, array));
    }

    public PByteArray createByteArray(SequenceStorage storage) {
        return createByteArray(lookupClass(PythonBuiltinClassType.PByteArray), storage);
    }

    public PByteArray createByteArray(PythonClass cls, SequenceStorage storage) {
        return trace(new PByteArray(cls, storage));
    }

    public PIntArray createIntArray(int[] array) {
        return trace(new PIntArray(lookupClass(PythonBuiltinClassType.PIntArray), array));
    }

    public PDoubleArray createDoubleArray(double[] array) {
        return trace(new PDoubleArray(lookupClass(PythonBuiltinClassType.PDoubleArray), array));
    }

    public PCharArray createCharArray(char[] array) {
        return trace(new PCharArray(lookupClass(PythonBuiltinClassType.PCharArray), array));
    }

    public PLongArray createLongArray(long[] array) {
        return trace(new PLongArray(lookupClass(PythonBuiltinClassType.PLongArray), array));
    }

    public PByteArray createByteArray(byte[] array) {
        return trace(new PByteArray(lookupClass(PythonBuiltinClassType.PByteArray), array));
    }

    /*
     * Iterators
     */

    public PStringIterator createStringIterator(String str) {
        return trace(new PStringIterator(lookupClass(PythonBuiltinClassType.PStringIterator), str));
    }

    public PStringReverseIterator createStringReverseIterator(PythonClass cls, String str) {
        return trace(new PStringReverseIterator(cls, str));
    }

    public PIntegerSequenceIterator createIntegerSequenceIterator(IntSequenceStorage storage) {
        return trace(new PIntegerSequenceIterator(lookupClass(PythonBuiltinClassType.PIntegerSequenceIterator), storage));
    }

    public PLongSequenceIterator createLongSequenceIterator(LongSequenceStorage storage) {
        return trace(new PLongSequenceIterator(lookupClass(PythonBuiltinClassType.PLongSequenceIterator), storage));
    }

    public PDoubleSequenceIterator createDoubleSequenceIterator(DoubleSequenceStorage storage) {
        return trace(new PDoubleSequenceIterator(lookupClass(PythonBuiltinClassType.PDoubleSequenceIterator), storage));
    }

    public PSequenceIterator createSequenceIterator(Object sequence) {
        return trace(new PSequenceIterator(lookupClass(PythonBuiltinClassType.PSequenceIterator), sequence));
    }

    public PSequenceReverseIterator createSequenceReverseIterator(PythonClass cls, Object sequence, int lengthHint) {
        return trace(new PSequenceReverseIterator(cls, sequence, lengthHint));
    }

    public PIntegerIterator createRangeIterator(int start, int stop, int step) {
        PIntegerIterator object;
        if (step > 0) {
            object = new PRangeIterator(lookupClass(PythonBuiltinClassType.PRangeIterator), start, stop, step);
        } else {
            object = new PRangeReverseIterator(lookupClass(PythonBuiltinClassType.PRangeReverseIterator), start, stop, -step);
        }
        return trace(object);
    }

    public PIntArrayIterator createIntArrayIterator(PIntArray array) {
        return trace(new PIntArrayIterator(lookupClass(PythonBuiltinClassType.PIntArrayIterator), array));
    }

    public PDoubleArrayIterator createDoubleArrayIterator(PDoubleArray array) {
        return trace(new PDoubleArrayIterator(lookupClass(PythonBuiltinClassType.PDoubleArrayIterator), array));
    }

    public PLongArrayIterator createLongArrayIterator(PLongArray array) {
        return trace(new PLongArrayIterator(lookupClass(PythonBuiltinClassType.PLongArrayIterator), array));
    }

    public PCharArrayIterator createCharArrayIterator(PCharArray array) {
        return trace(new PCharArrayIterator(lookupClass(PythonBuiltinClassType.PCharArrayIterator), array));
    }

    public PBaseSetIterator createBaseSetIterator(PBaseSet set) {
        return trace(new PBaseSetIterator(lookupClass(PythonBuiltinClassType.PBaseSetIterator), set));
    }

    public PDictView.PDictItemsIterator createDictItemsIterator(PDict dict) {
        return trace(new PDictView.PDictItemsIterator(lookupClass(PythonBuiltinClassType.PDictItemsIterator), dict));
    }

    public PDictView.PDictKeysIterator createDictKeysIterator(PDict dict) {
        return trace(new PDictView.PDictKeysIterator(lookupClass(PythonBuiltinClassType.PDictKeysIterator), dict));
    }

    public PDictView.PDictValuesIterator createDictValuesIterator(PDict dict) {
        return trace(new PDictView.PDictValuesIterator(lookupClass(PythonBuiltinClassType.PDictValuesIterator), dict));
    }

    public Object createSentinelIterator(Object callable, Object sentinel) {
        return trace(new PSentinelIterator(lookupClass(PythonBuiltinClassType.PSentinelIterator), callable, sentinel));
    }

    public PEnumerate createEnumerate(PythonClass cls, Object iterator, long start) {
        return trace(new PEnumerate(cls, iterator, start));
    }

    public PZip createZip(PythonClass cls, Object[] iterables) {
        return trace(new PZip(cls, iterables));
    }

    public PForeignArrayIterator createForeignArrayIterator(TruffleObject iterable, int size) {
        return trace(new PForeignArrayIterator(lookupClass(PythonBuiltinClassType.PForeignArrayIterator), iterable, size));
    }

    public PBuffer createBuffer(PythonClass cls, Object iterable) {
        return trace(new PBuffer(cls, iterable));
    }

    public PBuffer createBuffer(Object iterable) {
        return trace(new PBuffer(lookupClass(PythonBuiltinClassType.PBuffer), iterable));
    }

    public PCode createCode(RootNode result) {
        return trace(new PCode(lookupClass(PythonBuiltinClassType.PCode), result, getCore()));
    }

    public PCode createCode(PythonClass cls, int argcount, int kwonlyargcount,
                    int nlocals, int stacksize, int flags,
                    String codestring, Object constants, Object names,
                    Object[] varnames, Object[] freevars, Object[] cellvars,
                    String filename, String name, int firstlineno,
                    Object lnotab) {
        return trace(new PCode(cls, argcount, kwonlyargcount,
                        nlocals, stacksize, flags,
                        codestring, constants, names,
                        varnames, freevars, cellvars,
                        filename, name, firstlineno, lnotab));
    }
}
