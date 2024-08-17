/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ctypes.CDataObject;
import com.oracle.graal.python.builtins.modules.ctypes.PyCArgObject;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.graal.python.builtins.objects.cext.hpy.PythonHPyObject;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.TernaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.UnaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptors;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodeRange;

public abstract class PGuards {
    /**
     * Specialization guards.
     */

    public static boolean stringEquals(String expected, String other, ConditionProfile profile) {
        if (profile.profile(expected == other)) {
            return true;
        }
        return expected.equals(other);
    }

    public static boolean stringEquals(String expected, String other, Node inliningTarget, InlinedConditionProfile profile) {
        if (profile.profile(inliningTarget, expected == other)) {
            return true;
        }
        return expected.equals(other);
    }

    public static boolean stringEquals(TruffleString expected, TruffleString other, TruffleString.EqualNode equalNode, ConditionProfile profile) {
        if (profile.profile(expected == other)) {
            return true;
        }
        return equalNode.execute(expected, other, TS_ENCODING);
    }

    public static boolean stringEquals(TruffleString expected, TruffleString other, TruffleString.EqualNode equalNode, Node inliningTarget, InlinedConditionProfile profile) {
        if (profile.profile(inliningTarget, expected == other)) {
            return true;
        }
        return equalNode.execute(expected, other, TS_ENCODING);
    }

    public static boolean stringEquals(TruffleString key, TruffleString cachedKey, TruffleString.EqualNode equalNode) {
        return equalNode.execute(cachedKey, key, TS_ENCODING);
    }

    public static boolean isSameObject(Object left, Object right) {
        return left == right;
    }

    public static boolean isEmpty(Object[] array) {
        return array.length == 0;
    }

    public static boolean isNone(Object value) {
        return value == PNone.NONE;
    }

    public static boolean isNoValue(Object object) {
        return object == PNone.NO_VALUE;
    }

    public static boolean isEllipsis(Object object) {
        return object == PEllipsis.INSTANCE;
    }

    public static boolean isMemoryView(Object object) {
        return object instanceof PMemoryView;
    }

    public static boolean isMMap(Object object) {
        return object instanceof PMMap;
    }

    public static boolean isDeleteMarker(Object object) {
        return object == DescriptorDeleteMarker.INSTANCE;
    }

    public static boolean isDict(Object object) {
        return object instanceof PDict;
    }

    public static boolean isCode(Object object) {
        return object instanceof PCode;
    }

    public static boolean isStgDict(Object dict) {
        return dict instanceof StgDictObject;
    }

    @Idempotent
    public static boolean isFunction(Object value) {
        return value instanceof PBuiltinFunction || value instanceof PFunction;
    }

    @Idempotent
    public static boolean isPBuiltinFunction(Object value) {
        return value instanceof PBuiltinFunction;
    }

    @Idempotent
    public static boolean isPFunction(Object value) {
        return value instanceof PFunction;
    }

    public static boolean isCallable(Object value) {
        return value instanceof PBuiltinFunction || value instanceof PFunction || value instanceof PBuiltinMethod || value instanceof PMethod;
    }

    /**
     * Use instead of {@link #isCallable(Object)} for objects coming from
     * {@link com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode}. Note that such
     * objects can be then forwarded only to call nodes that support them.
     */
    public static boolean isCallableOrDescriptor(Object value) {
        return isCallable(value) || BuiltinMethodDescriptor.isInstance(value);
    }

    public static boolean isBuiltinDescriptor(Object value) {
        return BuiltinMethodDescriptor.isInstance(value);
    }

    public static boolean isClass(Node inliningTarget, Object obj, TypeNodes.IsTypeNode isTypeNode) {
        return isTypeNode.execute(inliningTarget, obj);
    }

    public static boolean isClassUncached(Object obj) {
        return TypeNodes.IsTypeNode.executeUncached(obj);
    }

    public static boolean isEmptyStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof EmptySequenceStorage;
    }

    public static boolean isIntStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof IntSequenceStorage;
    }

    public static boolean isByteStorage(PSequence array) {
        return array.getSequenceStorage() instanceof ByteSequenceStorage;
    }

    public static boolean areBothIntStorage(PSequence first, PSequence second) {
        return first.getSequenceStorage() instanceof IntSequenceStorage && second.getSequenceStorage() instanceof IntSequenceStorage;
    }

    public static boolean areBothByteStorage(PSequence first, PSequence second) {
        return first.getSequenceStorage() instanceof ByteSequenceStorage && second.getSequenceStorage() instanceof ByteSequenceStorage;
    }

    public static boolean isLongStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof LongSequenceStorage;
    }

    public static boolean areBothLongStorage(PList first, PList second) {
        return first.getSequenceStorage() instanceof LongSequenceStorage && second.getSequenceStorage() instanceof LongSequenceStorage;
    }

    public static boolean isDoubleStorage(PSequence sequence) {
        return sequence.getSequenceStorage() instanceof DoubleSequenceStorage;
    }

    public static boolean areBothDoubleStorage(PList first, PList second) {
        return first.getSequenceStorage() instanceof DoubleSequenceStorage && second.getSequenceStorage() instanceof DoubleSequenceStorage;
    }

    public static boolean isObjectStorage(PSequence list) {
        return list.getSequenceStorage() instanceof ObjectSequenceStorage;
    }

    public static boolean areBothObjectStorage(PList first, PList second) {
        return first.getSequenceStorage() instanceof ObjectSequenceStorage && second.getSequenceStorage() instanceof ObjectSequenceStorage;
    }

    public static boolean isList(Object o) {
        return o instanceof PList;
    }

    public static boolean isEconomicMapOrEmpty(HashingStorage self) {
        return self instanceof EconomicMapStorage || self instanceof EmptyStorage;
    }

    public static boolean isObjectStorageIterator(PSequenceIterator iterator) {
        if (!iterator.isPSequence()) {
            return false;
        }

        PSequence sequence = iterator.getPSequence();

        if (sequence instanceof PList) {
            PList list = (PList) sequence;
            return list.getSequenceStorage() instanceof ObjectSequenceStorage;
        }

        return false;
    }

    public static boolean isPythonObject(Object obj) {
        return obj instanceof PythonObject;
    }

    public static boolean isPythonModule(Object obj) {
        return obj instanceof PythonModule;
    }

    /**
     * Argument guards.
     */
    public static boolean emptyArguments(VirtualFrame frame) {
        return PArguments.getUserArgumentLength(frame) == 0;
    }

    public static boolean argGiven(Object object) {
        return object == PNone.NO_VALUE;
    }

    public static boolean emptyArguments(PNone none) {
        return none == PNone.NO_VALUE;
    }

    public static boolean isIndexPositive(int idx) {
        return idx >= 0;
    }

    public static boolean isIndexNegative(int idx) {
        return idx < 0;
    }

    public static boolean isIndexPositive(long idx) {
        return idx >= 0;
    }

    public static boolean isIndexNegative(long idx) {
        return idx < 0;
    }

    public static boolean isPythonUserClass(Object klass) {
        return klass instanceof PythonClass || PythonNativeClass.isInstance(klass);
    }

    public static boolean isPythonBuiltinClassType(Object klass) {
        return klass instanceof PythonBuiltinClassType;
    }

    public static boolean isPythonBuiltinClass(Object klass) {
        return klass instanceof PythonBuiltinClass;
    }

    public static boolean isNativeObject(Object object) {
        return PythonNativeObject.isInstance(object);
    }

    public static boolean isManagedClass(Object klass) {
        return PythonManagedClass.isInstance(klass);
    }

    public static boolean isNativeClass(Object klass) {
        return PythonNativeClass.isInstance(klass);
    }

    public static boolean isPythonClass(Object klass) {
        return PythonAbstractClass.isInstance(klass) || klass instanceof PythonBuiltinClassType;
    }

    public static boolean isPRange(Object obj) {
        return obj instanceof PRange;
    }

    public static boolean isString(Object obj) {
        return isJavaString(obj) || obj instanceof TruffleString || obj instanceof PString;
    }

    public static boolean isTruffleString(Object obj) {
        return obj instanceof TruffleString;
    }

    public static boolean isBuiltinFunction(Object obj) {
        return obj instanceof PBuiltinFunction;
    }

    public static boolean isMethod(Object value) {
        return value instanceof PMethod || value instanceof PBuiltinMethod;
    }

    public static boolean isPMethod(Object value) {
        return value instanceof PMethod;
    }

    public static boolean isBuiltinMethod(Object obj) {
        return obj instanceof PBuiltinMethod;
    }

    public static boolean isBuiltinObject(Object obj) {
        return obj instanceof PythonBuiltinObject;
    }

    public static boolean isAnyPythonObject(Object obj) {
        return obj instanceof PythonAbstractObject;
    }

    public static boolean canBeInteger(Object idx) {
        return isBoolean(idx) || isInteger(idx) || isPInt(idx);
    }

    public static boolean isNumber(Object num) {
        return isPFloat(num) || isDouble(num) || canBeInteger(num);
    }

    public static boolean isPInt(Object obj) {
        return obj instanceof PInt;
    }

    public static boolean isBuiltinPInt(PInt obj) {
        /*
         * int's __class__ cannot be reassigned and other objects cannot have their class assigned
         * to builtin int, so it is enough to look at the initial class. PInt constructor ensures
         * that it cannot be PythonBuiltinClass.
         */
        return obj.getInitialPythonClass() == PythonBuiltinClassType.PInt;
    }

    public static boolean isPString(Object obj) {
        return obj instanceof PString;
    }

    public static boolean isPFloat(Object obj) {
        return obj instanceof PFloat;
    }

    public static boolean isPNone(Object obj) {
        return obj instanceof PNone;
    }

    public static boolean isPComplex(Object obj) {
        return obj instanceof PComplex;
    }

    public static boolean isPTuple(Object obj) {
        return obj instanceof PTuple;
    }

    public static boolean isPSequence(Object obj) {
        return obj instanceof PSequence;
    }

    public static boolean isPCode(Object obj) {
        return obj instanceof PCode;
    }

    public static boolean isPException(AbstractTruffleException exception) {
        return exception instanceof PException;
    }

    public static boolean isPBaseException(Object obj) {
        return obj instanceof PBaseException;
    }

    public static boolean isPTraceback(Object obj) {
        return obj instanceof PTraceback;
    }

    public static boolean isInt(Object obj) {
        return obj instanceof Integer;
    }

    public static boolean isLong(Object obj) {
        return obj instanceof Long;
    }

    public static boolean isInteger(Object obj) {
        return obj instanceof Long || obj instanceof Integer;
    }

    public static boolean isDouble(Object obj) {
        return obj instanceof Double;
    }

    public static boolean isBoolean(Object obj) {
        return obj instanceof Boolean;
    }

    public static boolean isBytes(Object obj) {
        return obj instanceof PBytesLike;
    }

    public static boolean isPBytes(Object obj) {
        return obj instanceof PBytes;
    }

    public static boolean isPByteArray(Object obj) {
        return obj instanceof PByteArray;
    }

    public static boolean isArray(Object obj) {
        return obj instanceof PArray;
    }

    public static boolean isAnySet(Object obj) {
        return obj instanceof PBaseSet;
    }

    public static boolean isDictView(Object obj) {
        return obj instanceof PDictView;
    }

    public static boolean isDictKeysView(Object obj) {
        return obj instanceof PDictView.PDictKeysView;
    }

    public static boolean isDictItemsView(Object obj) {
        return obj instanceof PDictView.PDictItemsView;
    }

    public static boolean isPHashingCollection(Object o) {
        return o instanceof PHashingCollection;
    }

    public static boolean isPSet(Object o) {
        return o instanceof PSet;
    }

    public static boolean isPFrozenSet(Object o) {
        return o instanceof PFrozenSet;
    }

    public static boolean canDoSetBinOp(Object o) {
        return isAnySet(o) || isDictView(o);
    }

    public static boolean isPSlice(Object obj) {
        return obj instanceof PSlice;
    }

    public static boolean isPyCArg(Object obj) {
        return obj instanceof PyCArgObject;
    }

    public static boolean isCDataObject(Object obj) {
        return obj instanceof CDataObject;
    }

    public static boolean isHPyHandle(Object obj) {
        return obj instanceof GraalHPyHandle;
    }

    public static boolean isHPyObject(Object obj) {
        return obj instanceof PythonHPyObject;
    }

    public static boolean expectBoolean(Object result) throws UnexpectedResultException {
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        throw new UnexpectedResultException(result);
    }

    public static int expectInteger(Object result) throws UnexpectedResultException {
        if (result instanceof Integer) {
            return (Integer) result;
        }
        throw new UnexpectedResultException(result);
    }

    public static int expectInt(Object result) throws UnexpectedResultException, OverflowException {
        if (result instanceof Integer) {
            return (Integer) result;
        }
        if (result instanceof Long) {
            return PInt.intValueExact((Long) result);
        }
        throw new UnexpectedResultException(result);
    }

    public static long expectLong(Object result) throws UnexpectedResultException {
        if (result instanceof Long) {
            return (Long) result;
        }
        throw new UnexpectedResultException(result);
    }

    public static double expectDouble(Object result) throws UnexpectedResultException {
        if (result instanceof Double) {
            return (Double) result;
        }
        throw new UnexpectedResultException(result);
    }

    private static boolean isBuiltinImmutableTypeInstance(PythonObject dict, PythonBuiltinClassType type) {
        /*
         * Immutable types' __class__ cannot be reassigned and other objects cannot have their class
         * assigned to immutable types, so it is enough to look at the initial class. The Java
         * constructor of the object must ensure that it cannot be PythonBuiltinClass, see PDict for
         * an example.
         */
        assert !(dict.getInitialPythonClass() instanceof PythonBuiltinClass pbc) || pbc.getType() != type;
        return dict.getInitialPythonClass() == type;
    }

    public static boolean isBuiltinDict(PythonObject dict) {
        return isBuiltinImmutableTypeInstance(dict, PythonBuiltinClassType.PDict);
    }

    public static boolean isBuiltinTuple(PythonObject tuple) {
        return isBuiltinImmutableTypeInstance(tuple, PythonBuiltinClassType.PTuple);
    }

    public static boolean isBuiltinList(PythonObject list) {
        return isBuiltinImmutableTypeInstance(list, PythonBuiltinClassType.PList);
    }

    public static boolean isBuiltinSet(PythonObject set) {
        return isBuiltinImmutableTypeInstance(set, PythonBuiltinClassType.PSet);
    }

    public static boolean isBuiltinFrozenSet(PythonObject frozenSet) {
        return isBuiltinImmutableTypeInstance(frozenSet, PythonBuiltinClassType.PFrozenSet);
    }

    public static boolean isBuiltinAnySet(PythonObject set) {
        return isBuiltinSet(set) || isBuiltinFrozenSet(set);
    }

    public static boolean isBuiltinHashingCollection(PythonObject hashingCollection) {
        return isBuiltinDict(hashingCollection) || isBuiltinSet(hashingCollection) || isBuiltinFrozenSet(hashingCollection);
    }

    public static boolean isBuiltinPString(PString string) {
        return isBuiltinImmutableTypeInstance(string, PythonBuiltinClassType.PString);
    }

    public static boolean isBuiltinBytes(PythonObject bytes) {
        return isBuiltinImmutableTypeInstance(bytes, PythonBuiltinClassType.PBytes);
    }

    public static boolean isBuiltinByteArray(PythonObject byteArray) {
        return isBuiltinImmutableTypeInstance(byteArray, PythonBuiltinClassType.PByteArray);
    }

    public static boolean isBuiltinBytesLike(PythonObject object) {
        return isBuiltinBytes(object) || isBuiltinByteArray(object);
    }

    public static boolean isBuiltinSequence(PythonObject sequence) {
        return isBuiltinList(sequence) || isBuiltinTuple(sequence) || isBuiltinBytesLike(sequence);
    }

    public static boolean isKindOfBuiltinClass(Object clazz) {
        return clazz instanceof PythonBuiltinClassType || clazz instanceof PythonBuiltinClass;
    }

    public static boolean isUnaryBuiltinDescriptor(Object value) {
        return value instanceof UnaryBuiltinDescriptor;
    }

    public static boolean isBinaryBuiltinDescriptor(Object value) {
        return value instanceof BinaryBuiltinDescriptor;
    }

    public static boolean isTernaryBuiltinDescriptor(Object value) {
        return value instanceof TernaryBuiltinDescriptor;
    }

    public static boolean isMinusOne(long l) {
        return l == -1;
    }

    public static boolean isAscii(TruffleString str, TruffleString.GetCodeRangeNode getCodeRangeNode) {
        return getCodeRangeNode.execute(str, TS_ENCODING) == CodeRange.ASCII;
    }

    @InliningCutoff
    public static boolean isIndexOrSlice(Node inliningTarget, PyIndexCheckNode indexCheckNode, Object key) {
        return indexCheckNode.execute(inliningTarget, key) || isPSlice(key);
    }

    @InliningCutoff
    public static boolean isNativeWrapper(PythonAbstractObject object) {
        PythonNativeWrapper wrapper = object.getNativeWrapper();
        return wrapper != null && wrapper.isNative();
    }

    public static boolean isNullOrZero(Object value, InteropLibrary lib) {
        if (value instanceof Long) {
            return ((long) value) == 0;
        } else if (value instanceof NativePointer nativePointer) {
            return nativePointer.isNull();
        } else {
            return lib.isNull(value);
        }
    }

    /* CPython tests that tp_iter is dict_iter */
    public static boolean hasBuiltinDictIter(Node inliningTarget, PDict dict, GetPythonObjectClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
        return isBuiltinDict(dict) || lookupIter.execute(getClassNode.execute(inliningTarget, dict)) == BuiltinMethodDescriptors.DICT_ITER;
    }

    @Idempotent
    public static boolean isBytecodeDSLInterpreter() {
        return PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER;
    }
}
