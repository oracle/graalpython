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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.BOOL;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTES;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPLEX;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.ENUMERATE;
import static com.oracle.graal.python.nodes.BuiltinNames.FLOAT;
import static com.oracle.graal.python.nodes.BuiltinNames.FROZENSET;
import static com.oracle.graal.python.nodes.BuiltinNames.INT;
import static com.oracle.graal.python.nodes.BuiltinNames.LIST;
import static com.oracle.graal.python.nodes.BuiltinNames.MEMORYVIEW;
import static com.oracle.graal.python.nodes.BuiltinNames.MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.OBJECT;
import static com.oracle.graal.python.nodes.BuiltinNames.RANGE;
import static com.oracle.graal.python.nodes.BuiltinNames.REVERSED;
import static com.oracle.graal.python.nodes.BuiltinNames.SET;
import static com.oracle.graal.python.nodes.BuiltinNames.STR;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.ZIP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PGeneratorFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PBaseSetIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PDoubleSequenceIterator;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.iterator.PIntArrayIterator;
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
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.reversed.PSequenceReverseIterator;
import com.oracle.graal.python.builtins.objects.reversed.PStringReverseIterator;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.JavaTypeConversions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "builtins")
public final class BuiltinConstructors extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinConstructorsFactory.getFactories();
    }

    // bytes([source[, encoding[, errors]]])
    @Builtin(name = BYTES, minNumOfArguments = 1, maxNumOfArguments = 4, constructsClass = PBytes.class)
    @GenerateNodeFactory
    public abstract static class BytesNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(source)")
        public PBytes bytes(PythonClass cls, @SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return factory().createBytes(cls, new byte[0]);
        }

        @Specialization
        public PBytes bytes(PythonClass cls, int source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return factory().createBytes(cls, BytesUtils.fromSize(getCore(), source));
        }

        @Specialization
        public PBytes bytes(PythonClass cls, PInt source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            try {
                return factory().createBytes(cls, BytesUtils.fromSize(PythonLanguage.getCore(), source.intValueExact()));
            } catch (ArithmeticException e) {
                // TODO: fix me, in python the array can take long sizes, we are bound to ints for
                // now
                throw raise(OverflowError, "byte string is too large");
            }
        }

        @Specialization
        public PBytes bytes(PythonClass cls, String source, String encoding, @SuppressWarnings("unused") PNone errors) {
            return factory().createBytes(cls, BytesUtils.fromStringAndEncoding(getCore(), source, encoding));
        }

        @Specialization
        public PBytes bytes(PythonClass cls, PString source, String encoding, @SuppressWarnings("unused") PNone errors) {
            return factory().createBytes(cls, BytesUtils.fromStringAndEncoding(getCore(), source.getValue(), encoding));
        }

        @Specialization(guards = "isString(source)")
        @SuppressWarnings("unused")
        public PBytes bytes(PythonClass cls, Object source, PNone encoding, PNone errors) {
            throw raise(TypeError, "string argument without an encoding");
        }

        @Specialization
        public PBytes bytes(PythonClass cls, PythonObject source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create()") ConstructListNode constructListNode,
                        @Cached("create()") GetClassNode getClassNode) {
            PythonClass sourceClass = getClassNode.execute(source);
            PList list = constructListNode.execute(source, sourceClass);
            return factory().createBytes(cls, BytesUtils.fromList(getCore(), list));
        }

        @Specialization(guards = "!isString(source)")
        @SuppressWarnings("unused")
        public PBytes bytes(Object cls, Object source, Object encoding, Object errors) {
            throw raise(TypeError, "encoding without a string argument");
        }
    }

    // bytearray([source[, encoding[, errors]]])
    @Builtin(name = BYTEARRAY, minNumOfArguments = 1, maxNumOfArguments = 4, constructsClass = PByteArray.class)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class ByteArrayNode extends PythonBuiltinNode {

        @Specialization
        public PByteArray bytearray(PythonClass cls, PNone source, PNone encoding, PNone errors) {
            return factory().createByteArray(cls, new byte[0]);
        }

        @Specialization
        public PByteArray bytearray(PythonClass cls, int source, PNone encoding, PNone errors) {
            return factory().createByteArray(cls, BytesUtils.fromSize(PythonLanguage.getCore(), source));
        }

        @Specialization
        public PByteArray bytearray(PythonClass cls, PInt source, PNone encoding, PNone errors) {
            try {
                return factory().createByteArray(cls, BytesUtils.fromSize(PythonLanguage.getCore(), source.intValueExact()));
            } catch (ArithmeticException e) {
                // TODO: fix me, in python the array can take long sizes, we are bound to ints for
                // now
                throw raise(OverflowError, "byte string is too large");
            }
        }

        @Specialization
        public PByteArray bytearray(PythonClass cls, String source, String encoding, PNone errors) {
            return factory().createByteArray(cls, BytesUtils.fromStringAndEncoding(PythonLanguage.getCore(), source, encoding));
        }

        @Specialization
        public PByteArray bytearray(PythonClass cls, PythonObject source, PNone encoding, PNone errors,
                        @Cached("create()") ConstructListNode constructListNode,
                        @Cached("create()") GetClassNode getClassNode) {
            PythonClass sourceClass = getClassNode.execute(source);
            PList list = constructListNode.execute(source, sourceClass);
            return factory().createByteArray(cls, BytesUtils.fromList(PythonLanguage.getCore(), list));
        }
    }

    // complex([real[, imag]])
    @Builtin(name = COMPLEX, minNumOfArguments = 1, maxNumOfArguments = 3, constructsClass = PComplex.class, doc = "complex(real[, imag]) -> complex number\n\n" +
                    "Create a complex number from a real part and an optional imaginary part.\n" +
                    "This is equivalent to (real + imag*1j) where imag defaults to 0.")
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class ComplexNode extends PythonBuiltinNode {
        @Specialization
        public PComplex complexFromDoubleDouble(Object cls, int real, int imaginary) {
            return factory().createComplex(real, imaginary);
        }

        @Specialization
        public PComplex complexFromDoubleDouble(Object cls, double real, double imaginary) {
            return factory().createComplex(real, imaginary);
        }

        @Specialization
        public PComplex complexFromDouble(Object cls, double real, PNone image) {
            return factory().createComplex(real, 0);
        }

        @Specialization
        public PComplex complexFromDouble(Object cls, int real, PNone image) {
            return factory().createComplex(real, 0);
        }

        @Specialization
        public PComplex complexFromNone(Object cls, PNone real, PNone image) {
            return factory().createComplex(0, 0);
        }

        @Specialization
        public PComplex complexFromObjectObject(Object cls, String real, Object imaginary) {
            if (!(imaginary instanceof PNone)) {
                throw raise(TypeError, "complex() can't take second arg if first is a string");
            }
            return JavaTypeConversions.convertStringToComplex(real, (PythonClass) cls, factory());
        }

        @Fallback
        public PComplex complexFromObjectObject(Object cls, Object real, Object imaginary) {
            throw raise(TypeError, "can't convert real %s imag %s", real, imaginary);
        }
    }

    // dict(**kwarg)
    // dict(mapping, **kwarg)
    // dict(iterable, **kwarg)
    @Builtin(name = DICT, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true, constructsClass = PDict.class)
    @GenerateNodeFactory
    public abstract static class DictionaryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PDict dictEmpty(PythonClass cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createDict(cls);
        }
    }

    // enumerate(iterable, start=0)
    @Builtin(name = ENUMERATE, fixedNumOfArguments = 2, keywordArguments = {"start"}, constructsClass = PEnumerate.class)
    @GenerateNodeFactory
    public abstract static class EnumerateNode extends PythonBuiltinNode {
        /**
         * TODO enumerate can take a keyword argument start, and currently that's not supported.
         */

        @Specialization
        public PEnumerate enumerate(PythonClass cls, Object iterable, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(iterable), 0);
        }

        @Specialization
        public PEnumerate enumerate(PythonClass cls, Object iterable, int start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(iterable), start);
        }

        @Specialization
        public PEnumerate enumerate(PythonClass cls, Object iterable, long start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(iterable), start);
        }

        @Specialization(guards = "!isInteger(start)")
        public void enumerate(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") Object iterable, Object start) {
            raise(TypeError, "%p object cannot be interpreted as an integer", start);
        }
    }

    // reversed(seq)
    @Builtin(name = REVERSED, fixedNumOfArguments = 2, constructsClass = {PStringReverseIterator.class, PSequenceReverseIterator.class})
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonBuiltinNode {

        @Specialization
        public PythonObject reversed(@SuppressWarnings("unused") PythonClass cls, PRange range,
                        @Cached("createBinaryProfile()") ConditionProfile stepOneProfile,
                        @Cached("createBinaryProfile()") ConditionProfile stepMinusOneProfile) {
            int stop;
            int start;
            int step = range.getStep();
            if (stepOneProfile.profile(step == 1)) {
                start = range.getStop() - 1;
                stop = range.getStart() - 1;
                step = -1;
            } else if (stepMinusOneProfile.profile(step == -1)) {
                start = range.getStop() + 1;
                stop = range.getStart() + 1;
                step = 1;
            } else {
                assert step != 0;
                long delta = (range.getStop() - (long) range.getStart() - (step > 0 ? -1 : 1)) / step * step;
                start = (int) (range.getStart() + delta);
                stop = range.getStart() - step;
                step = -step;
            }
            return factory().createRangeIterator(start, stop, step);
        }

        @Specialization
        public PythonObject reversed(PythonClass cls, PString value) {
            return factory().createStringReverseIterator(cls, value.getValue());
        }

        @Specialization
        public PythonObject reversed(PythonClass cls, String value) {
            return factory().createStringReverseIterator(cls, value);
        }

        @Specialization(guards = {"!isString(sequence)", "!isPRange(sequence)"})
        public Object reversed(PythonClass cls, Object sequence,
                        @Cached("create()") GetClassNode getClassNode,
                        @Cached("create(__REVERSED__)") LookupAttributeInMRONode reversedNode,
                        @Cached("create()") CallUnaryMethodNode callReversedNode,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode lenNode,
                        @Cached("create(__GETITEM__)") LookupAttributeInMRONode getItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile noReversedProfile,
                        @Cached("createBinaryProfile()") ConditionProfile noGetItemProfile) {
            PythonClass sequenceKlass = getClassNode.execute(sequence);
            Object reversed = reversedNode.execute(sequenceKlass);
            if (noReversedProfile.profile(reversed == PNone.NO_VALUE)) {
                Object getItem = getItemNode.execute(sequenceKlass);
                if (noGetItemProfile.profile(getItem == PNone.NO_VALUE)) {
                    throw raise(TypeError, "'%p' object is not reversible", sequence);
                } else {
                    try {
                        return factory().createSequenceReverseIterator(cls, sequence, lenNode.executeInt(sequence));
                    } catch (UnexpectedResultException e) {
                        throw raise(TypeError, "%p object cannot be interpreted as an integer", e.getResult());
                    }
                }
            } else {
                return callReversedNode.executeObject(reversed, sequence);
            }
        }
    }

    // float([x])
    @Builtin(name = FLOAT, minNumOfArguments = 1, maxNumOfArguments = 2, constructsClass = PFloat.class)
    @GenerateNodeFactory
    public abstract static class FloatNode extends PythonBuiltinNode {
        private final ConditionProfile isPrimitiveProfile = ConditionProfile.createBinaryProfile();

        protected boolean isPrimitiveFloat(Object cls) {
            return isPrimitiveProfile.profile(cls == getBuiltinFloatClass());
        }

        protected PythonBuiltinClass getBuiltinFloatClass() {
            return getCore().lookupType(PythonBuiltinClassType.PFloat);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromInt(PythonClass cls, int arg) {
            if (isPrimitiveFloat(cls)) {
                return (double) arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromBoolean(PythonClass cls, boolean arg) {
            if (isPrimitiveFloat(cls)) {
                return arg ? 1d : 0d;
            }
            return factory().createFloat(cls, arg ? 1d : 0d);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromLong(PythonClass cls, long arg) {
            if (isPrimitiveFloat(cls)) {
                return (double) arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromPInt(PythonClass cls, PInt arg) {
            if (isPrimitiveFloat(cls)) {
                return arg.doubleValue();
            }
            return factory().createFloat(cls, arg.doubleValue());
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromFloat(PythonClass cls, double arg) {
            if (isPrimitiveFloat(cls)) {
                return arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromString(PythonClass cls, String arg) {
            double value = JavaTypeConversions.convertStringToDouble(arg);
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factory().createFloat(cls, value);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        public Object floatFromNone(PythonClass cls, @SuppressWarnings("unused") PNone arg) {
            if (isPrimitiveFloat(cls)) {
                return 0.0;
            }
            return factory().createFloat(cls, 0.0);
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double doubleFromObject(@SuppressWarnings("unused") PythonClass cls, Object obj,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached("create()") BranchProfile gotException) {
            try {
                return callFloatNode.executeDouble(obj);
            } catch (UnexpectedResultException e) {
                gotException.enter();
                Object result = e.getResult();
                if (result == PNone.NO_VALUE) {
                    throw raise(TypeError, "must be real number, not %p", obj);
                } else if (result instanceof PFloat) {
                    // TODO Issue warning if 'result' is a subclass of Python type 'float'
                    return ((PFloat) result).getValue();
                } else {
                    throw raise(TypeError, "%p.__float__ returned non-float (type %p)", obj, result);
                }
            }
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object doPythonObject(PythonClass cls, Object obj,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached("create()") BranchProfile gotException) {
            return floatFromFloat(cls, doubleFromObject(cls, obj, callFloatNode, gotException));
        }

        protected CExtNodes.SubtypeNew createSubtypeNew() {
            return new CExtNodes.SubtypeNew("float");
        }

        // logic similar to float_subtype_new(PyTypeObject *type, PyObject *x) from CPython
        // floatobject.c we have to first create a temporary float, then fill it into
        // a natively allocated subtype structure
        @Specialization(guards = "isSubtype.execute(cls, floatCls)", limit = "1")
        Object doPythonObject(PythonNativeClass cls, Object obj,
                        @Cached("getBuiltinFloatClass()") PythonBuiltinClass floatCls,
                        @SuppressWarnings("unused") @Cached("create()") IsSubtypeNode isSubtype,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached("create()") BranchProfile gotException,
                        @Cached("createSubtypeNew()") CExtNodes.SubtypeNew subtypeNew) {
            double realFloat = doubleFromObject(floatCls, obj, callFloatNode, gotException);
            return subtypeNew.execute(cls, realFloat);
        }

        @Fallback
        @TruffleBoundary
        public Object floatFromObject(@SuppressWarnings("unused") Object cls, Object arg) {
            throw raise(TypeError, "can't convert %s to float", arg.getClass().getSimpleName());
        }
    }

    // frozenset([iterable])
    @Builtin(name = FROZENSET, minNumOfArguments = 1, maxNumOfArguments = 2, constructsClass = PFrozenSet.class)
    @GenerateNodeFactory
    public abstract static class FrozenSetNode extends PythonBuiltinNode {

        @Child private HashingStorageNodes.SetItemNode setItemNode;

        @Specialization(guards = "isNoValue(arg)")
        public PFrozenSet frozensetEmpty(PythonClass cls, @SuppressWarnings("unused") PNone arg) {
            return factory().createFrozenSet(cls);
        }

        @Specialization
        @TruffleBoundary
        public PFrozenSet frozenset(PythonClass cls, String arg) {
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            for (int i = 0; i < arg.length(); i++) {
                getSetItemNode().execute(frozenSet, frozenSet.getDictStorage(), String.valueOf(arg.charAt(i)), PNone.NO_VALUE);
            }
            return frozenSet;
        }

        @Specialization(guards = "!isNoValue(iterable)")
        @TruffleBoundary
        public PFrozenSet frozensetIterable(PythonClass cls, Object iterable,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {

            Object iterator = getIterator.executeWith(iterable);
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            while (true) {
                try {
                    getSetItemNode().execute(frozenSet, frozenSet.getDictStorage(), next.execute(iterator), PNone.NO_VALUE);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return frozenSet;
                }
            }
        }

        private HashingStorageNodes.SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingStorageNodes.SetItemNode.create());
            }
            return setItemNode;
        }
    }

    // int(x=0)
    // int(x, base=10)
    @Builtin(name = INT, fixedNumOfArguments = 1, keywordArguments = {"x", "base"}, constructsClass = PInt.class)
    @GenerateNodeFactory
    public abstract static class IntNode extends PythonBuiltinNode {

        private final ConditionProfile isPrimitiveProfile = ConditionProfile.createBinaryProfile();

        public abstract Object executeWith(Object cls, Object arg, Object keywordArg);

        protected boolean isPrimitiveInt(Object cls) {
            return isPrimitiveProfile.profile(cls == getCore().lookupType(PythonBuiltinClassType.PInt));
        }

        @Specialization
        Object parseInt(PythonClass cls, boolean arg, @SuppressWarnings("unused") PNone keywordArg) {
            if (isPrimitiveInt(cls)) {
                return arg ? 1 : 0;
            } else {
                return factory().createInt(cls, arg ? 1 : 0);
            }
        }

        @Specialization(guards = "isNoValue(keywordArg)")
        public Object createInt(PythonClass cls, int arg, @SuppressWarnings("unused") PNone keywordArg) {
            if (isPrimitiveInt(cls)) {
                return arg;
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(keywordArg)")
        public Object createInt(PythonClass cls, long arg, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("createBinaryProfile()") ConditionProfile isIntProfile) {
            if (isPrimitiveInt(cls)) {
                int intValue = (int) arg;
                if (isIntProfile.profile(intValue == arg)) {
                    return intValue;
                } else {
                    return arg;
                }
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(keywordArg)")
        public Object createInt(PythonClass cls, double arg, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("createBinaryProfile()") ConditionProfile isIntProfile) {
            if (isPrimitiveInt(cls) && isIntProfile.profile(arg >= Integer.MIN_VALUE && arg <= Integer.MAX_VALUE)) {
                return (int) arg;
            }
            return factory().createInt(cls, (long) arg);
        }

        @Specialization
        public Object createInt(PythonClass cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PNone keywordArg) {
            if (isPrimitiveInt(cls)) {
                return 0;
            }
            return factory().createInt(cls, 0);
        }

        @Specialization(guards = "isNoValue(keywordArg)")
        public Object createInt(PythonClass cls, String arg, @SuppressWarnings("unused") PNone keywordArg) {
            try {
                Object value = JavaTypeConversions.stringToInt(arg, 10);
                if (isPrimitiveInt(cls)) {
                    return value;
                } else {
                    return value instanceof BigInteger ? factory().createInt(cls, (BigInteger) value) : factory().createInt(cls, (int) value);
                }
            } catch (NumberFormatException e) {
                throw raise(ValueError, "invalid literal for int() with base 10: %s", arg);
            }
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        int parseInt(Object cls, PIBytesLike arg, int keywordArg) throws NumberFormatException {
            return parseInt(cls, new String(arg.getInternalByteArray()), keywordArg);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        long parseLong(Object cls, PIBytesLike arg, int keywordArg) throws NumberFormatException {
            return parseLong(cls, new String(arg.getInternalByteArray()), keywordArg);
        }

        @Specialization
        @TruffleBoundary
        Object parsePInt(PythonClass cls, PIBytesLike arg, int keywordArg) {
            return parsePInt(cls, new String(arg.getInternalByteArray()), keywordArg);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        int parseInt(Object cls, PString arg, int keywordArg) throws NumberFormatException {
            return parseInt(cls, arg.getValue(), keywordArg);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        long parseLong(Object cls, PString arg, int keywordArg) throws NumberFormatException {
            return parseLong(cls, arg.getValue(), keywordArg);
        }

        @Specialization
        @TruffleBoundary
        Object parsePInt(PythonClass cls, PString arg, int keywordArg) {
            return parsePInt(cls, arg.getValue(), keywordArg);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        int parseInt(@SuppressWarnings("unused") Object cls, String arg, int keywordArg) throws NumberFormatException {
            return Integer.parseInt(arg, keywordArg);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        long parseLong(@SuppressWarnings("unused") Object cls, String arg, int keywordArg) throws NumberFormatException {
            return Long.parseLong(arg, keywordArg);
        }

        @Specialization
        Object parsePInt(PythonClass cls, String arg, int keywordArg) {
            Object int2 = JavaTypeConversions.toInt(arg, keywordArg);
            if (int2 instanceof BigInteger) {
                return factory().createInt(cls, (BigInteger) int2);
            } else if (isPrimitiveInt(cls)) {
                return int2;
            } else {
                assert int2 instanceof Integer;
                return factory().createInt(cls, (int) int2);
            }
        }

        @Specialization
        public Object createInt(PythonClass cls, String arg, Object keywordArg) {
            if (keywordArg instanceof PNone) {
                Object value = JavaTypeConversions.toInt(arg);
                if (value == null) {
                    throw raise(ValueError, "invalid literal for int() with base 10: %s", arg);
                } else if (value instanceof BigInteger) {
                    return factory().createInt(cls, (BigInteger) value);
                } else if (isPrimitiveInt(cls)) {
                    return value;
                } else {
                    return factory().createInt(cls, (int) value);
                }
            } else {
                throw new RuntimeException("Not implemented integer with base: " + keywordArg);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(arg)", "!isNoValue(keywordArg)"})
        Object fail(PythonClass cls, Object arg, Object keywordArg) {
            throw raise(TypeError, "int() can't convert non-string with explicit base");
        }

        @Specialization(guards = "isNoValue(keywordArg)")
        public Object createInt(PythonClass cls, Object obj, PNone keywordArg,
                        @Cached("create(__INT__)") LookupAndCallUnaryNode callIntNode,
                        @Cached("create(__TRUNC__)") LookupAndCallUnaryNode callTruncNode,
                        @Cached("createBinaryProfile()") ConditionProfile isIntProfile) {
            try {
                // at first try __int__ method
                return createInt(cls, callIntNode.executeLong(obj), keywordArg, isIntProfile);
            } catch (UnexpectedResultException e) {
                Object result = e.getResult();
                if (result == PNone.NO_VALUE) {
                    try {
                        // now try __trunc__ method
                        return createInt(cls, callTruncNode.executeLong(obj), keywordArg, isIntProfile);
                    } catch (UnexpectedResultException ee) {
                        result = ee.getResult();
                    }
                }
                if (result == PNone.NO_VALUE) {
                    throw raise(TypeError, "an integer is required (got type %p)", obj);
                } else if (result instanceof Integer) {
                    return createInt(cls, (int) result, keywordArg);
                } else if (result instanceof Long) {
                    return createInt(cls, (long) result, keywordArg, isIntProfile);
                } else if (result instanceof Boolean) {
                    return createInt(cls, (boolean) result ? 1 : 0, keywordArg, isIntProfile);
                } else if (result instanceof PInt) {
                    // TODO warn if 'result' not of exact Python type 'int'
                    return isPrimitiveInt(cls) ? result : factory().createInt(cls, ((PInt) result).getValue());
                } else {
                    throw raise(TypeError, "__int__ returned non-int (type %p)", result);
                }
            }
        }
    }

    // bool([x])
    @Builtin(name = BOOL, minNumOfArguments = 1, maxNumOfArguments = 2, constructsClass = Boolean.class, base = PInt.class)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization
        public boolean bool(Object cls, boolean arg) {
            return arg;
        }

        @Specialization
        public boolean bool(Object cls, int arg) {
            return arg != 0;
        }

        @Specialization
        public boolean bool(Object cls, double arg) {
            return arg != 0.0;
        }

        @Specialization
        public boolean bool(Object cls, String arg) {
            return !arg.isEmpty();
        }

        @Specialization
        public boolean bool(Object cls, PNone arg) {
            return false;
        }

        @Specialization
        public boolean bool(Object cls, Object obj,
                        @Cached("create(__BOOL__)") LookupAndCallUnaryNode callNode,
                        @Cached("create()") GetClassNode getClass) {
            try {
                return callNode.executeBoolean(obj);
            } catch (UnexpectedResultException ex) {
                throw raise(PythonErrorType.TypeError, "__bool__ should return bool, returned %s", getClass.execute(ex.getResult()));
            }
        }
    }

    // list([iterable])
    @Builtin(name = LIST, minNumOfArguments = 1, maxNumOfArguments = 2, constructsClass = PList.class)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonBuiltinNode {

        @Specialization
        protected PList constructList(PythonClass cls, Object value,
                        @Cached("create()") ConstructListNode constructListNode,
                        @Cached("create()") GetClassNode getClassNode) {
            PythonClass valueClass = getClassNode.execute(value);
            return constructListNode.execute(cls, value, valueClass);
        }

        @Fallback
        public PList listObject(@SuppressWarnings("unused") Object cls, Object arg) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException("list does not support iterable object " + arg);
        }
    }

    // object()
    @Builtin(name = OBJECT, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PythonObject.class})
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {
        @Override
        public final Object varArgExecute(Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(PNone.NO_VALUE, arguments, keywords);
        }

        @Specialization
        Object doDirectConstruct(@SuppressWarnings("unused") PNone ignored, Object[] arguments, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return factory().createPythonObject((PythonClass) arguments[0]);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"})
        Object doObjectDirect(@SuppressWarnings("unused") PythonClass self, Object[] varargs, PKeyword[] kwargs,
                        @Cached("self") PythonClass cachedSelf) {
            return doObjectIndirect(cachedSelf, varargs, kwargs);
        }

        @Specialization(replaces = "doObjectDirect")
        Object doObjectIndirect(PythonClass self, Object[] varargs, PKeyword[] kwargs) {
            if (varargs.length > 0 || kwargs.length > 0) {
                // TODO: tfel: this should throw an error only if init isn't overridden
            }
            return factory().createPythonObject(self);
        }
    }

    // range(stop)
    // range(start, stop[, step])
    @Builtin(name = RANGE, minNumOfArguments = 2, maxNumOfArguments = 4, constructsClass = PRange.class)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class RangeNode extends PythonBuiltinNode {

        @Specialization(guards = "caseStop(start,step)")
        public PSequence rangeStop(Object cls, int stop, Object start, Object step) {
            return factory().createRange(stop);
        }

        @Specialization(guards = "caseStop(start,step)")
        public PSequence rangeStop(Object cls, long stop, Object start, Object step) {
            return factory().createRange(((Long) stop).intValue());
        }

        @Specialization(guards = "caseStop(start,step)")
        public PSequence rangeStop(Object cls, PInt stop, Object start, Object step) {
            return factory().createRange(stop.intValue());
        }

        @Specialization(guards = "caseStartStop(step)")
        public PSequence rangeStartStop(Object cls, int start, int stop, Object step) {
            return factory().createRange(start, stop);
        }

        @Specialization(guards = "caseStartStop(step)")
        public PSequence rangeStartStop(Object cls, int start, long stop, Object step) {
            return factory().createRange(start, ((Long) stop).intValue());
        }

        @Specialization(guards = "caseStartStop(step)")
        public PSequence rangeStartStop(Object cls, long start, int stop, Object step) {
            return factory().createRange(((Long) start).intValue(), stop);
        }

        @Specialization(guards = "caseStartStop(stop,start,step)")
        public PSequence rangeStartStop(Object cls, long start, long stop, Object step) {
            return factory().createRange(((Long) start).intValue(), ((Long) stop).intValue());
        }

        @Specialization
        public PSequence rangeStartStopStep(Object cls, int start, int stop, int step) {
            return factory().createRange(start, stop, step);
        }

        @Specialization
        public PSequence rangeStartStopStep(Object cls, long start, long stop, long step) {
            return factory().createRange((int) start, ((Long) stop).intValue(), ((Long) step).intValue());
        }

        @TruffleBoundary
        @Specialization(guards = "isNumber(stop)")
        public PSequence rangeStartStopStep(Object cls, Object start, Object stop, Object step) {
            if (isNumber(stop)) {
                int intStop = 0;
                if (stop instanceof Integer)
                    intStop = (int) stop;
                else if (stop instanceof Long)
                    intStop = ((Long) (stop)).intValue();
                else
                    intStop = ((PInt) stop).intValue();

                if (start instanceof PNone)
                    return factory().createRange(intStop);

                if (isNumber(start)) {
                    int intStart = 0;
                    if (start instanceof Integer)
                        intStart = (int) start;
                    else if (start instanceof Long)
                        intStart = ((Long) (start)).intValue();
                    else
                        intStart = ((PInt) start).intValue();

                    if (step instanceof PNone)
                        return factory().createRange(intStart, intStop);

                    if (isNumber(step)) {
                        int intStep = 0;
                        if (step instanceof Integer)
                            intStep = (int) step;
                        else if (step instanceof Long)
                            intStep = ((Long) (step)).intValue();
                        else
                            intStep = ((PInt) step).intValue();

                        return factory().createRange(intStart, intStop, intStep);
                    }
                }
            }

            throw raise(TypeError, "range does not support %s, %s, %s", start, stop, step);
        }

        @TruffleBoundary
        @Specialization(guards = "!isNumber(stop)")
        public PSequence rangeError(Object cls, Object start, Object stop, Object step) {
            CompilerDirectives.transferToInterpreter();
            throw raise(TypeError, "range does not support %s, %s, %s", start, stop, step);
        }

        public static boolean isNumber(Object value) {
            return value instanceof Integer || value instanceof Long || value instanceof PInt;
        }

        public static boolean caseStop(Object start, Object step) {
            return start == PNone.NO_VALUE && step == PNone.NO_VALUE;
        }

        public static boolean caseStartStop(Object step) {
            return step == PNone.NO_VALUE;
        }

        public static boolean caseStartStop(long start, long stop, Object step) {
            return step == PNone.NO_VALUE;
        }

    }

    // set([iterable])
    @Builtin(name = SET, minNumOfArguments = 1, maxNumOfArguments = 2, constructsClass = PSet.class)
    @GenerateNodeFactory
    public abstract static class SetNode extends PythonBuiltinNode {

        @Specialization
        protected PSet constructSet(PythonClass cls, Object value,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            return constructSetNode.execute(cls, value);
        }

        @Fallback
        public PSet listObject(@SuppressWarnings("unused") Object cls, Object arg) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException("set does not support iterable object " + arg);
        }
    }

    // str(object='')
    // str(object=b'', encoding='utf-8', errors='strict')
    @Builtin(name = STR, minNumOfArguments = 1, keywordArguments = {"object", "encoding", "errors"}, takesVariableKeywords = true, constructsClass = PString.class)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonBuiltinNode {
        private final ConditionProfile isPrimitiveProfile = ConditionProfile.createBinaryProfile();

        private Object asPString(Object cls, String str) {
            if (isPrimitiveProfile.profile(cls == getCore().lookupType(PythonBuiltinClassType.PString))) {
                return str;
            } else {
                return factory().createString((PythonClass) cls, str);
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object str(Object strClass, PNone arg, PNone encoding, PNone errors) {
            return asPString(strClass, "");
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object str(Object strClass, double arg, PNone encoding, PNone errors) {
            return asPString(strClass, JavaTypeConversions.doubleToString(arg));
        }

        @Specialization
        public Object str(Object strClass, Object obj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode callNode,
                        @Cached("createBinaryProfile()") ConditionProfile isString,
                        @Cached("createBinaryProfile()") ConditionProfile isPString) {
            Object result = callNode.executeObject(obj);
            if (isString.profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (isPString.profile(result instanceof PString)) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "__str__ returned non-string (type %p)", result);
        }

        protected static String DECODE = "decode";

        @Specialization(guards = "!isNoValue(encoding)")
        public Object str(Object strClass, PIBytesLike obj, Object encoding, Object errors,
                        @Cached("createBinaryProfile()") ConditionProfile isString,
                        @Cached("createBinaryProfile()") ConditionProfile isPString,
                        @Cached("create(DECODE)") LookupAndCallTernaryNode callDecode) {
            Object result = callDecode.execute(obj, encoding, errors);
            if (isString.profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (isPString.profile(result instanceof PString)) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "%p.decode returned non-string (type %p)", obj, result);
        }
    }

    // tuple([iterable])
    @Builtin(name = TUPLE, minNumOfArguments = 1, maxNumOfArguments = 2, constructsClass = PTuple.class)
    @GenerateNodeFactory
    public abstract static class TupleNode extends PythonBuiltinNode {

        @Specialization
        protected PTuple constructTuple(PythonClass cls, Object value,
                        @Cached("create()") TupleNodes.ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(cls, value);
        }

        @Fallback
        public PTuple tupleObject(@SuppressWarnings("unused") Object cls, Object arg) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException("tuple does not support iterable object " + arg);
        }
    }

    // zip(*iterables)
    @Builtin(name = ZIP, minNumOfArguments = 1, takesVariableArguments = true, constructsClass = PZip.class)
    @GenerateNodeFactory
    public abstract static class ZipNode extends PythonBuiltinNode {
        @Specialization
        public PZip zip(PythonClass cls, Object[] args,
                        @Cached("create()") GetIteratorNode getIterator) {
            Object[] iterables = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object item = args[i];
                // TODO: check whether the argument supports iteration (has __next__ and __iter__)
                iterables[i] = getIterator.executeWith(item);
            }
            return factory().createZip(cls, iterables);
        }
    }

    // function(code, globals[, name[, argdefs[, closure]]])
    @Builtin(name = "function", minNumOfArguments = 3, maxNumOfArguments = 6, constructsClass = {PFunction.class, PGeneratorFunction.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object code, PDict globals, String name, PTuple defaultArgs, PTuple closure) {
            throw raise(NotImplementedError, "function construction not implemented");
        }
    }

    // builtin-function(method-def, self, module)
    @Builtin(name = "builtin-function", minNumOfArguments = 3, maxNumOfArguments = 6, constructsClass = {PBuiltinFunction.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinFunctionNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object method_def, Object def, Object name, Object module) {
            throw raise(TypeError, "cannot create 'builtin_function' instances");
        }
    }

    // type(object)
    // type(object, bases, dict)
    @Builtin(name = TYPE, minNumOfArguments = 2, maxNumOfArguments = 4, takesVariableKeywords = true, constructsClass = {PythonClass.class, PythonBuiltinClass.class})
    @GenerateNodeFactory
    public abstract static class TypeNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(bases)", "isNoValue(dict)"})
        @SuppressWarnings("unused")
        public Object type(Object cls, Object obj, PNone bases, PNone dict, PKeyword[] kwds,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(obj);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(namespace)"})
        @TruffleBoundary
        public Object type(PythonClass cls, String name, PTuple bases, PDict namespace, PKeyword[] kwds,
                        @Cached("create()") GetClassNode getMetaclassNode,
                        @Cached("create(__NEW__)") LookupInheritedAttributeNode getNewFuncNode,
                        @Cached("create()") CallDispatchNode callNewFuncNode,
                        @Cached("create()") CreateArgumentsNode createArgs) {
            PythonClass metaclass = calculate_metaclass(cls, bases, getMetaclassNode);
            if (metaclass != cls) {
                Object newFunc = getNewFuncNode.execute(metaclass);
                if (newFunc instanceof PBuiltinFunction && (((PBuiltinFunction) newFunc).getFunctionRootNode() == getRootNode())) {
                    // the new metaclass has the same __new__ function as we are in
                } else {
                    return callNewFuncNode.executeCall(newFunc, createArgs.execute(metaclass, name, bases, namespace), kwds);
                }
            }
            if (name.indexOf('\0') != -1) {
                throw raise(ValueError, "type name must not contain null characters");
            }
            Object[] array = bases.getArray();
            PythonClass[] basesArray;
            if (array.length == 0) {
                basesArray = new PythonClass[]{getCore().getObjectClass()};
            } else {
                basesArray = new PythonClass[array.length];
                for (int i = 0; i < array.length; i++) {
                    // TODO: deal with non-class bases
                    if (!(array[i] instanceof PythonClass)) {
                        throw raise(NotImplementedError, "creating a class with non-class bases");
                    } else {
                        basesArray[i] = (PythonClass) array[i];
                    }
                }
            }
            assert metaclass != null;
            PythonClass pythonClass = factory().createPythonClass(metaclass, name, basesArray);
            for (DictEntry entry : namespace.entries()) {
                pythonClass.setAttribute(entry.getKey(), entry.getValue());
            }
            return pythonClass;
        }

        private PythonClass calculate_metaclass(PythonClass cls, PTuple bases, GetClassNode getMetaclassNode) {
            PythonClass winner = cls;
            for (Object base : bases.getArray()) {
                PythonClass typ = getMetaclassNode.execute(base);
                if (isSubType(winner, typ)) {
                    continue;
                } else if (isSubType(typ, winner)) {
                    winner = typ;
                    continue;
                }
                throw raise(TypeError, "metaclass conflict: the metaclass of a derived class must be " +
                                "a (non-strict) subclass of the metaclasses of all its bases");
            }
            return winner;
        }

        private static boolean isSubType(PythonClass subclass, PythonClass superclass) {
            for (PythonClass base : subclass.getMethodResolutionOrder()) {
                if (base == superclass) {
                    return true;
                }
            }
            return false;
        }

        protected abstract Object execute(Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        protected static TypeNode create() {
            return BuiltinConstructorsFactory.TypeNodeFactory.create(null);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        public Object typeGeneric(Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
                        @Cached("create()") TypeNode nextTypeNode) {
            if (PGuards.isNoValue(bases) && !PGuards.isNoValue(dict) || !PGuards.isNoValue(bases) && PGuards.isNoValue(dict)) {
                throw raise(TypeError, "type() takes 1 or 3 arguments");
            } else if (!(name instanceof String || name instanceof PString)) {
                throw raise(TypeError, "type() argument 1 must be string, not %p", name);
            } else if (!(bases instanceof PTuple)) {
                throw raise(TypeError, "type() argument 2 must be tuple, not %p", name);
            } else if (!(dict instanceof PDict)) {
                throw raise(TypeError, "type() argument 3 must be dict, not %p", name);
            } else if (!(cls instanceof PythonClass)) {
                // TODO: this is actually allowed, deal with it
                throw raise(NotImplementedError, "creating a class with non-class metaclass");
            }
            return nextTypeNode.execute(cls, name, bases, dict, kwds);
        }
    }

    @Builtin(name = MODULE, minNumOfArguments = 2, maxNumOfArguments = 3, constructsClass = {PythonModule.class}, isPublic = false)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @SuppressWarnings("unused")
    public abstract static class ModuleNode extends PythonBuiltinNode {
        @Child WriteAttributeToObjectNode writeFile = WriteAttributeToObjectNode.create();

        @Specialization
        public PythonModule module(Object cls, String name, PNone path) {
            return factory().createPythonModule(name);
        }

        @Specialization
        public PythonModule module(Object cls, String name, String path) {
            PythonModule module = factory().createPythonModule(name);
            writeFile.execute(module, __FILE__, path);
            return module;
        }
    }

    @Builtin(name = "NotImplementedType", fixedNumOfArguments = 1, constructsClass = {PNotImplemented.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NotImplementedTypeNode extends PythonBuiltinNode {
        protected PythonClass getNotImplementedClass() {
            return getCore().lookupType(PNotImplemented.class);
        }

        @Specialization
        public PNotImplemented module(Object cls,
                        @Cached("getNotImplementedClass()") PythonClass notImplementedClass) {
            if (cls != notImplementedClass) {
                throw raise(TypeError, "'NotImplementedType' object is not callable");
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = "ellipsis", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PEllipsis.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class EllipsisTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PEllipsis call(Object cls, Object args, Object kwds) {
            return PEllipsis.INSTANCE;
        }
    }

    @Builtin(name = "NoneType", fixedNumOfArguments = 1, constructsClass = {PNone.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NoneTypeNode extends PythonBuiltinNode {
        protected PythonClass getNoneClass() {
            return getCore().lookupType(PNone.class);
        }

        @Specialization
        public PNone module(Object cls,
                        @Cached("getNoneClass()") PythonClass noneClass) {
            if (cls != noneClass) {
                throw raise(TypeError, "NoneType.__new__(%s) is not a subtype of NoneType", cls);
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "dict_keys", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PDictView.PDictKeysView.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_keys' instances");
        }
    }

    @Builtin(name = "dict_keysiterator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PDictView.PDictKeysIterator.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_keysiterator' instances");
        }
    }

    @Builtin(name = "dict_values", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PDictView.PDictValuesView.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_values' instances");
        }
    }

    @Builtin(name = "dict_valuesiterator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PDictView.PDictValuesIterator.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_valuesiterator' instances");
        }
    }

    @Builtin(name = "dict_items", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PDictView.PDictItemsView.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_items' instances");
        }
    }

    @Builtin(name = "dict_itemsiterator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PDictView.PDictItemsIterator.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_itemsiterator' instances");
        }
    }

    @Builtin(name = "iterator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {
                    PRangeIterator.class, PIntegerSequenceIterator.class, PSequenceIterator.class,
                    PBaseSetIterator.class, PRangeIterator.class, PDoubleArrayIterator.class,
                    PDoubleSequenceIterator.class, PLongSequenceIterator.class, PLongArrayIterator.class,
                    PIntArrayIterator.class, PStringIterator.class, PRangeReverseIterator.class,
    }, isPublic = false)
    @GenerateNodeFactory
    public abstract static class IteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'iterator' instances");
        }
    }

    @Builtin(name = "callable_iterator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PSentinelIterator.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CallableIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'callable_iterator' instances");
        }
    }

    @Builtin(name = "foreign_iterator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PForeignArrayIterator.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ForeignIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'foreign_iterator' instances");
        }
    }

    @Builtin(name = "generator", takesVariableArguments = true, takesVariableKeywords = true, constructsClass = {PGenerator.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class GeneratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object generator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'generator' instances");
        }
    }

    @Builtin(name = "method", fixedNumOfArguments = 3, constructsClass = {PMethod.class, PBuiltinMethod.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class MethodTypeNode extends PythonBuiltinNode {
        @Specialization
        Object method(PythonClass cls, Object self, PFunction func) {
            return factory().createMethod(cls, self, func);
        }

        @Specialization
        Object method(PythonClass cls, Object self, PBuiltinFunction func) {
            return factory().createBuiltinMethod(cls, self, func);
        }
    }

    @Builtin(name = "frame", constructsClass = {PFrame.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FrameTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of frame type");
        }
    }

    @Builtin(name = "traceback", constructsClass = {PTraceback.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class TracebackTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of traceback type");
        }
    }

    @Builtin(name = "code", constructsClass = {PCode.class}, isPublic = false, minNumOfArguments = 14, maxNumOfArguments = 16)
    @GenerateNodeFactory
    public abstract static class CodeTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call(PythonClass cls, int argcount, int kwonlyargcount, int nlocals, int stacksize,
                        int flags, String codestring, Object constants, Object names, Object varnames,
                        String filename, String name, int firstlineno, Object lnotab, Object freevars,
                        Object cellvars) {
            return factory().createCode(cls, argcount, kwonlyargcount, nlocals, stacksize,
                            flags, codestring, constants, names, varnames,
                            filename, name, firstlineno, lnotab, freevars,
                            cellvars);
        }

        @Specialization
        @TruffleBoundary
        Object call(PythonClass cls, int argcount, int kwonlyargcount, int nlocals, int stacksize,
                        int flags, PBytes codestring, Object constants, Object names, Object varnames,
                        PString filename, PString name, int firstlineno, Object lnotab, Object freevars,
                        Object cellvars) {
            return factory().createCode(cls, argcount, kwonlyargcount, nlocals, stacksize,
                            flags, new String(codestring.getInternalByteArray()), constants, names, varnames,
                            filename.getValue(), name.getValue(), firstlineno, lnotab, freevars,
                            cellvars);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object call(Object cls, Object argcount, Object kwonlyargcount, Object nlocals, Object stacksize,
                        Object flags, Object codestring, Object constants, Object names, Object varnames,
                        Object filename, Object name, Object firstlineno, Object lnotab, Object freevars,
                        Object cellvars) {
            throw raise(PythonErrorType.NotImplementedError, "code object instance from generic arguments");
        }
    }

    @Builtin(name = "cell", constructsClass = {PCell.class}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CellTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of cell type");
        }
    }

    @Builtin(name = "BaseException", constructsClass = {PBaseException.class}, isPublic = true, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object call(PythonClass cls, Object[] varargs, PKeyword[] kwargs) {
            return factory().createBaseException(cls, factory().createTuple(varargs));
        }
    }

    @Builtin(name = "mappingproxy", constructsClass = {PMappingproxy.class}, isPublic = false, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class MappingproxyNode extends PythonBuiltinNode {
        @Specialization
        Object call(PythonClass klass, PythonObject obj) {
            return factory().createMappingproxy(klass, obj);
        }
    }

    @Builtin(name = "getset_descriptor", constructsClass = {GetSetDescriptor.class}, isPublic = false, fixedNumOfArguments = 1, keywordArguments = {"fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class GetSetDescriptorNode extends PythonBuiltinNode {
        private void denyInstantiationAfterInitialization() {
            if (getCore().isInitialized()) {
                throw raise(TypeError, "cannot create 'getset_descriptor' instances");
            }
        }

        @Specialization
        @TruffleBoundary
        Object call(PythonClass getSetClass, PythonCallable get, PythonCallable set, String name, PythonClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(get, set, name, owner);
        }

        @Specialization
        @TruffleBoundary
        Object call(PythonClass getSetClass, PythonCallable get, PNone set, String name, PythonClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(get, null, name, owner);
        }

        @Specialization
        @TruffleBoundary
        Object call(PythonClass getSetClass, PNone set, PNone get, String name, PythonClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(null, null, name, owner);
        }

        @Fallback
        Object call(Object klsas, Object set, Object get, Object name, Object owner) {
            denyInstantiationAfterInitialization();
            throw new RuntimeException("error in creating getset_descriptor during core initialization");
        }
    }

    // slice(stop)
    // slice(start, stop[, step])
    @Builtin(name = "slice", minNumOfArguments = 2, maxNumOfArguments = 4, constructsClass = PSlice.class)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class CreateSliceNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(second)")
        Object sliceStop(PythonClass cls, int first, PNone second, PNone third) {
            return factory().createSlice(SequenceUtil.MISSING_INDEX, first, SequenceUtil.MISSING_INDEX);
        }

        @Specialization(guards = "isNone(second)")
        Object sliceStart(PythonClass cls, int first, PNone second, PNone third) {
            return factory().createSlice(first, SequenceUtil.MISSING_INDEX, SequenceUtil.MISSING_INDEX);
        }

        @Specialization(guards = "isNoValue(third)")
        Object slice(PythonClass cls, int first, int second, PNone third) {
            return factory().createSlice(first, second, SequenceUtil.MISSING_INDEX);
        }

        @Specialization
        Object slice(PythonClass cls, int first, int second, int third) {
            return factory().createSlice(first, second, third);
        }

        @Specialization
        Object slice(PythonClass cls, Object start, Object stop, Object step,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(start, stop, step);
        }
    }

    // buffer([iterable])
    @Builtin(name = "buffer", fixedNumOfArguments = 2, constructsClass = PBuffer.class)
    @GenerateNodeFactory
    public abstract static class BufferNode extends PythonBuiltinNode {

        @Specialization
        protected PBuffer construct(PythonClass cls, Object value) {
            return factory().createBuffer(cls, value);
        }

        @Fallback
        public PBuffer listObject(@SuppressWarnings("unused") Object cls, Object arg) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException("buffer does not support iterable object " + arg);
        }
    }

    // memoryview(obj)
    @Builtin(name = MEMORYVIEW, fixedNumOfArguments = 2, constructsClass = PMemoryView.class)
    @GenerateNodeFactory
    public abstract static class MemoryViewNode extends PythonBuiltinNode {
        @Specialization
        public PMemoryView doGeneric(PythonClass cls, Object value) {
            return factory().createMemoryView(cls, value);
        }
    }

}
