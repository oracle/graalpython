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

import static com.oracle.graal.python.builtins.objects.slice.PSlice.MISSING_INDEX;
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
import static com.oracle.graal.python.nodes.BuiltinNames.SUPER;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.ZIP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.CastToByteNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
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
import com.oracle.graal.python.nodes.datamodel.IsIndexNode;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "builtins")
public final class BuiltinConstructors extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinConstructorsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("NotImplemented", PNotImplemented.NOT_IMPLEMENTED);
    }

    protected abstract static class CreateByteOrByteArrayNode extends PythonBuiltinNode {
        @Child private IsIndexNode isIndexNode;
        @Child private CastToIndexNode castToIndexNode;

        @SuppressWarnings("unused")
        protected Object create(PythonClass cls, byte[] barr) {
            throw new AssertionError("should not reach");
        }

        @Specialization(guards = {"isNoValue(source)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(PythonClass cls, @SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, new byte[0]);
        }

        @Specialization(guards = {"isInt(capObj)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(PythonClass cls, Object capObj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            int cap = getCastToIndexNode().execute(capObj);
            return create(cls, BytesUtils.fromSize(getCore(), cap));
        }

        @Specialization(guards = "isNoValue(errors)")
        public Object fromString(PythonClass cls, String source, String encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, BytesUtils.fromStringAndEncoding(getCore(), source, encoding));
        }

        @Specialization(guards = {"isNoValue(encoding)", "isNoValue(errors)"})
        @SuppressWarnings("unused")
        public Object fromString(PythonClass cls, String source, PNone encoding, PNone errors) {
            throw raise(PythonErrorType.TypeError, "string argument without an encoding");
        }

        @Specialization(guards = {"!isInt(iterable)", "!isNoValue(iterable)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(PythonClass cls, Object iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("createBinaryProfile()") ConditionProfile stopIterationProfile,
                        @Cached("create()") CastToByteNode castToByteNode) {

            Object it = getIteratorNode.executeWith(iterable);
            byte[] arr = new byte[16];
            int i = 0;
            while (true) {
                try {
                    byte item = castToByteNode.execute(getNextNode.execute(it));
                    if (i >= arr.length) {
                        arr = resize(arr, arr.length * 2);
                    }
                    arr[i++] = item;
                } catch (PException e) {
                    e.expect(StopIteration, getCore(), stopIterationProfile);
                    return create(cls, resize(arr, i));
                }
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] resize(byte[] arr, int len) {
            return Arrays.copyOf(arr, len);
        }

        protected boolean isInt(Object o) {
            if (isIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isIndexNode = insert(IsIndexNode.create());
            }
            return isIndexNode.execute(o);
        }

        protected CastToIndexNode getCastToIndexNode() {
            if (castToIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIndexNode = insert(CastToIndexNode.createOverflow());
            }
            return castToIndexNode;
        }
    }

    // bytes([source[, encoding[, errors]]])
    @Builtin(name = BYTES, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PBytes)
    @GenerateNodeFactory
    public abstract static class BytesNode extends CreateByteOrByteArrayNode {
        @Override
        protected Object create(PythonClass cls, byte[] barr) {
            return factory().createBytes(cls, barr);
        }
    }

    // bytearray([source[, encoding[, errors]]])
    @Builtin(name = BYTEARRAY, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PByteArray)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ByteArrayNode extends CreateByteOrByteArrayNode {
        @Override
        protected Object create(PythonClass cls, byte[] barr) {
            return factory().createByteArray(cls, barr);
        }
    }

    // complex([real[, imag]])
    @Builtin(name = COMPLEX, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PComplex, doc = "complex(real[, imag]) -> complex number\n\n" +
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
            return convertStringToComplex(real, (PythonClass) cls);
        }

        // Taken from Jython PyString's __complex__() method
        @TruffleBoundary(transferToInterpreterOnException = false)
        private PComplex convertStringToComplex(String str, PythonClass cls) {
            boolean gotRe = false;
            boolean gotIm = false;
            boolean done = false;
            boolean swError = false;

            int s = 0;
            int n = str.length();
            while (s < n && Character.isSpaceChar(str.charAt(s))) {
                s++;
            }

            if (s == n) {
                throw getCore().raise(ValueError, "empty string for complex()");
            }

            double z = -1.0;
            double x = 0.0;
            double y = 0.0;

            int sign = 1;
            do {
                char c = str.charAt(s);

                switch (c) {
                    case '-':
                    case '+':
                        if (c == '-') {
                            sign = -1;
                        }
                        if (done || s + 1 == n) {
                            swError = true;
                            break;
                        }
                        // a character is guaranteed, but it better be a digit
                        // or J or j
                        c = str.charAt(++s);  // eat the sign character
                        // and check the next
                        if (!Character.isDigit(c) && c != 'J' && c != 'j') {
                            swError = true;
                        }
                        break;

                    case 'J':
                    case 'j':
                        if (gotIm || done) {
                            swError = true;
                            break;
                        }
                        if (z < 0.0) {
                            y = sign;
                        } else {
                            y = sign * z;
                        }
                        gotIm = true;
                        done = gotRe;
                        sign = 1;
                        s++; // eat the J or j
                        break;

                    case ' ':
                        while (s < n && Character.isSpaceChar(str.charAt(s))) {
                            s++;
                        }
                        if (s != n) {
                            swError = true;
                        }
                        break;

                    default:
                        boolean digitOrDot = (c == '.' || Character.isDigit(c));
                        if (!digitOrDot) {
                            swError = true;
                            break;
                        }
                        int end = endDouble(str, s);
                        z = Double.valueOf(str.substring(s, end)).doubleValue();
                        if (z == Double.POSITIVE_INFINITY) {
                            throw getCore().raise(ValueError, String.format("float() out of range: %.150s", str));
                        }

                        s = end;
                        if (s < n) {
                            c = str.charAt(s);
                            if (c == 'J' || c == 'j') {
                                break;
                            }
                        }
                        if (gotRe) {
                            swError = true;
                            break;
                        }

                        /* accept a real part */
                        x = sign * z;
                        gotRe = true;
                        done = gotIm;
                        z = -1.0;
                        sign = 1;
                        break;

                } /* end of switch */

            } while (s < n && !swError);

            if (swError) {
                throw getCore().raise(ValueError, "malformed string for complex() %s", str.substring(s));
            }

            return factory().createComplex(cls, x, y);
        }

        // Taken from Jython PyString directly
        public static int endDouble(String string, int s) {
            int end = s;
            int n = string.length();
            while (end < n) {
                char c = string.charAt(end++);
                if (Character.isDigit(c)) {
                    continue;
                }
                if (c == '.') {
                    continue;
                }
                if (c == 'e' || c == 'E') {
                    if (end < n) {
                        c = string.charAt(end);
                        if (c == '+' || c == '-') {
                            end++;
                        }
                        continue;
                    }
                }
                return end - 1;
            }
            return end;
        }

        @Fallback
        public PComplex complexFromObjectObject(Object cls, Object real, Object imaginary) {
            throw raise(TypeError, "can't convert real %s imag %s", real, imaginary);
        }
    }

    // dict(**kwarg)
    // dict(mapping, **kwarg)
    // dict(iterable, **kwarg)
    @Builtin(name = DICT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDict)
    @GenerateNodeFactory
    public abstract static class DictionaryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PDict dictEmpty(PythonClass cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createDict(cls);
        }
    }

    // enumerate(iterable, start=0)
    @Builtin(name = ENUMERATE, fixedNumOfPositionalArgs = 2, keywordArguments = {"start"}, constructsClass = PythonBuiltinClassType.PEnumerate)
    @GenerateNodeFactory
    public abstract static class EnumerateNode extends PythonBuiltinNode {

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
    @Builtin(name = REVERSED, fixedNumOfPositionalArgs = 2, constructsClass = {PythonBuiltinClassType.PStringReverseIterator, PythonBuiltinClassType.PSequenceReverseIterator})
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
    @Builtin(name = FLOAT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFloat)
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
            double value = convertStringToDouble(arg);
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factory().createFloat(cls, value);
        }

        // Taken from Jython PyString's atof() method
        // The last statement throw Py.ValueError is modified
        @TruffleBoundary
        private double convertStringToDouble(String str) {
            StringBuilder s = null;
            int n = str.length();

            for (int i = 0; i < n; i++) {
                char ch = str.charAt(i);
                if (ch == '\u0000') {
                    throw getCore().raise(ValueError, "empty string for complex()");
                }
                if (Character.isDigit(ch)) {
                    if (s == null) {
                        s = new StringBuilder(str);
                    }
                    int val = Character.digit(ch, 10);
                    s.setCharAt(i, Character.forDigit(val, 10));
                }
            }
            String sval = str.trim();
            if (s != null) {
                sval = s.toString();
            }
            try {
                // Double.valueOf allows format specifier ("d" or "f") at the end
                String lowSval = sval.toLowerCase();
                if (lowSval.equals("nan") || lowSval.equals("+nan") || lowSval.equals("-nan")) {
                    return Double.NaN;
                } else if (lowSval.equals("inf") || lowSval.equals("+inf") || lowSval.equals("infinity") || lowSval.equals("+infinity")) {
                    return Double.POSITIVE_INFINITY;
                } else if (lowSval.equals("-inf") || lowSval.equals("-infinity")) {
                    return Double.NEGATIVE_INFINITY;
                }
                return Double.valueOf(sval).doubleValue();
            } catch (NumberFormatException exc) {
                // throw Py.ValueError("invalid literal for __float__: " + str);
                throw getCore().raise(ValueError, "could not convert string to float: %s", str);
            }
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
            if (obj instanceof String) {
                return convertStringToDouble((String) obj);
            } else if (obj instanceof PString) {
                return convertStringToDouble(((PString) obj).getValue());
            } else if (obj instanceof PNone) {
                return 0.0;
            }
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
        @Specialization(guards = "isSubtype.execute(cls, getBuiltinFloatClass())", limit = "1")
        Object doPythonObject(PythonNativeClass cls, Object obj,
                        @SuppressWarnings("unused") @Cached("create()") IsSubtypeNode isSubtype,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached("create()") BranchProfile gotException,
                        @Cached("createSubtypeNew()") CExtNodes.SubtypeNew subtypeNew) {
            double realFloat = doubleFromObject(getBuiltinFloatClass(), obj, callFloatNode, gotException);
            return subtypeNew.execute(cls, realFloat);
        }

        @Fallback
        @TruffleBoundary
        public Object floatFromObject(@SuppressWarnings("unused") Object cls, Object arg) {
            throw raise(TypeError, "can't convert %s to float", arg.getClass().getSimpleName());
        }
    }

    // frozenset([iterable])
    @Builtin(name = FROZENSET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFrozenSet)
    @GenerateNodeFactory
    public abstract static class FrozenSetNode extends PythonBuiltinNode {

        @Child private HashingCollectionNodes.SetItemNode setItemNode;

        @Specialization(guards = "isNoValue(arg)")
        public PFrozenSet frozensetEmpty(PythonClass cls, @SuppressWarnings("unused") PNone arg) {
            return factory().createFrozenSet(cls);
        }

        @Specialization
        @TruffleBoundary
        public PFrozenSet frozenset(PythonClass cls, String arg) {
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            for (int i = 0; i < arg.length(); i++) {
                getSetItemNode().execute(frozenSet, String.valueOf(arg.charAt(i)), PNone.NO_VALUE);
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
                    getSetItemNode().execute(frozenSet, next.execute(iterator), PNone.NO_VALUE);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return frozenSet;
                }
            }
        }

        private HashingCollectionNodes.SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingCollectionNodes.SetItemNode.create());
            }
            return setItemNode;
        }
    }

    // int(x=0)
    // int(x, base=10)
    @Builtin(name = INT, fixedNumOfPositionalArgs = 1, keywordArguments = {"x", "base"}, constructsClass = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    public abstract static class IntNode extends PythonBuiltinNode {

        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;

        @TruffleBoundary(transferToInterpreterOnException = false)
        private Object stringToInt(String num, int base) {
            String s = num.replace("_", "");
            if ((base >= 2 && base <= 32) || base == 0) {
                BigInteger bi = asciiToBigInteger(s, base, false);
                if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    return bi;
                } else {
                    return bi.intValue();
                }
            } else {
                throw getCore().raise(ValueError, "base is out of range for int()");
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private Object toInt(Object arg) {
            if (arg instanceof Integer || arg instanceof BigInteger) {
                return arg;
            } else if (arg instanceof Boolean) {
                return (boolean) arg ? 1 : 0;
            } else if (arg instanceof Double) {
                return doubleToInt((Double) arg);
            } else if (arg instanceof String) {
                return stringToInt((String) arg, 10);
            }
            return null;
        }

        private Object toInt(Object arg1, Object arg2) {
            if (arg1 instanceof String && arg2 instanceof Integer) {
                return stringToInt((String) arg1, (Integer) arg2);
            } else {
                throw getCore().raise(ValueError, "invalid base or val for int()");
            }
        }

        private static Object doubleToInt(double num) {
            if (num > Integer.MAX_VALUE || num < Integer.MIN_VALUE) {
                return BigInteger.valueOf((long) num);
            } else {
                return (int) num;
            }
        }

        // Copied directly from Jython
        @TruffleBoundary(transferToInterpreterOnException = false)
        private static BigInteger asciiToBigInteger(String str, int possibleBase, boolean isLong) {
            int base = possibleBase;
            int b = 0;
            int e = str.length();

            while (b < e && Character.isWhitespace(str.charAt(b))) {
                b++;
            }

            while (e > b && Character.isWhitespace(str.charAt(e - 1))) {
                e--;
            }

            char sign = 0;
            if (b < e) {
                sign = str.charAt(b);
                if (sign == '-' || sign == '+') {
                    b++;
                    while (b < e && Character.isWhitespace(str.charAt(b))) {
                        b++;
                    }
                }

                if (base == 16) {
                    if (str.charAt(b) == '0') {
                        if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                            b += 2;
                        }
                    }
                } else if (base == 0) {
                    if (str.charAt(b) == '0') {
                        if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                            base = 16;
                            b += 2;
                        } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                            base = 8;
                            b += 2;
                        } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                            base = 2;
                            b += 2;
                        } else {
                            base = 8;
                        }
                    }
                } else if (base == 8) {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                        b += 2;
                    }
                } else if (base == 2) {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                        b += 2;
                    }
                }
            }

            if (base == 0) {
                base = 10;
            }

            // if the base >= 22, then an 'l' or 'L' is a digit!
            if (isLong && base < 22 && e > b && (str.charAt(e - 1) == 'L' || str.charAt(e - 1) == 'l')) {
                e--;
            }

            String s = str;
            if (b > 0 || e < str.length()) {
                s = str.substring(b, e);
            }

            BigInteger bi;
            if (sign == '-') {
                bi = new BigInteger("-" + s, base);
            } else {
                bi = new BigInteger(s, base);
            }
            return bi;
        }

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
                Object value = stringToInt(arg, 10);
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
            return parseInt(cls, new String(getByteArray(arg)), keywordArg);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        long parseLong(Object cls, PIBytesLike arg, int keywordArg) throws NumberFormatException {
            return parseLong(cls, new String(getByteArray(arg)), keywordArg);
        }

        @Specialization(rewriteOn = NumberFormatException.class)
        @TruffleBoundary
        Object parseBytes(PythonClass cls, PIBytesLike arg, int base) {
            return parsePInt(cls, new String(getByteArray(arg)), base);
        }

        @Specialization(replaces = "parseBytes")
        Object parseBytesError(PythonClass cls, PIBytesLike arg, int base) {
            try {
                return parseBytes(cls, arg, base);
            } catch (NumberFormatException e) {
                throw raise(ValueError, "invalid literal for int() with base %s: %s", base, arg);
            }
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

        @Specialization(rewriteOn = NumberFormatException.class)
        Object parsePInt(PythonClass cls, String arg, int base) {
            Object int2 = toInt(arg, base);
            if (int2 instanceof BigInteger) {
                return factory().createInt(cls, (BigInteger) int2);
            } else if (isPrimitiveInt(cls)) {
                return int2;
            } else {
                assert int2 instanceof Integer;
                return factory().createInt(cls, (int) int2);
            }
        }

        @Specialization(replaces = "parsePInt")
        Object parsePIntError(PythonClass cls, String arg, int base) {
            try {
                return parsePInt(cls, arg, base);
            } catch (NumberFormatException e) {
                throw raise(ValueError, "invalid literal for int() with base %s: %s", base, arg);
            }
        }

        @Specialization
        public Object createInt(PythonClass cls, String arg, Object keywordArg) {
            if (keywordArg instanceof PNone) {
                Object value = toInt(arg);
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

        @Specialization(guards = {"isNoValue(keywordArg)", "!isNoValue(obj)"})
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

        private byte[] getByteArray(PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(SequenceStorageNodes.ToByteArrayNode.create());
            }
            return toByteArrayNode.execute(pByteArray.getSequenceStorage());
        }

    }

    // bool([x])
    @Builtin(name = BOOL, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.Boolean, base = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class BoolNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean boolB(Object cls, boolean arg) {
            return arg;
        }

        @Specialization
        public boolean boolI(Object cls, int arg) {
            return arg != 0;
        }

        @Specialization
        public boolean boolD(Object cls, double arg) {
            return arg != 0.0;
        }

        @Specialization
        public boolean boolS(Object cls, String arg) {
            return !arg.isEmpty();
        }

        @Specialization
        public boolean boolN(Object cls, PNone arg) {
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
    @Builtin(name = LIST, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PList)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonBinaryBuiltinNode {

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
    @Builtin(name = OBJECT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonObject)
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {
        @Override
        public final Object varArgExecute(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, PNone.NO_VALUE, arguments, keywords);
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
    @Builtin(name = RANGE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PRange)
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
    @Builtin(name = SET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PSet)
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
    @Builtin(name = STR, minNumOfPositionalArgs = 1, keywordArguments = {"object", "encoding", "errors"}, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PString)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonBuiltinNode {
        @Child private LookupAndCallTernaryNode callDecodeNode;

        private final ConditionProfile isPrimitiveProfile = ConditionProfile.createBinaryProfile();

        @CompilationFinal private ConditionProfile isStringProfile;
        @CompilationFinal private ConditionProfile isPStringProfile;

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
            return asPString(strClass, PFloat.doubleToString(arg));
        }

        @Specialization(guards = {"!isNoValue(obj)", "!isNone(obj)"})
        public Object str(Object strClass, Object obj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode callNode) {
            Object result = callNode.executeObject(obj);
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (getIsPStringProfile().profile(result instanceof PString)) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "__str__ returned non-string (type %p)", result);
        }

        @Specialization(guards = "!isNoValue(encoding)")
        public Object doBytesLike(Object strClass, PIBytesLike obj, Object encoding, Object errors) {
            Object result = getCallDecodeNode().execute(obj, encoding, errors);
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (getIsPStringProfile().profile(result instanceof PString)) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "%p.decode returned non-string (type %p)", obj, result);
        }

        @Specialization(guards = "!isNoValue(encoding)")
        public Object doMemoryView(Object strClass, PMemoryView obj, Object encoding, Object errors,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytes) {
            Object result = callToBytes.executeObject(obj);
            if (isBytesProfile.profile(result instanceof PBytes)) {
                return doBytesLike(strClass, (PBytes) result, encoding, errors);
            }
            throw raise(PythonErrorType.TypeError, "%p.tobytes returned non-bytes object (type %p)", obj, result);
        }

        private LookupAndCallTernaryNode getCallDecodeNode() {
            if (callDecodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callDecodeNode = insert(LookupAndCallTernaryNode.create(DECODE));
            }
            return callDecodeNode;
        }

        private ConditionProfile getIsStringProfile() {
            if (isStringProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isStringProfile = ConditionProfile.createBinaryProfile();
            }
            return isStringProfile;
        }

        private ConditionProfile getIsPStringProfile() {
            if (isPStringProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPStringProfile = ConditionProfile.createBinaryProfile();
            }
            return isPStringProfile;
        }
    }

    // tuple([iterable])
    @Builtin(name = TUPLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PTuple)
    @GenerateNodeFactory
    public abstract static class TupleNode extends PythonBinaryBuiltinNode {

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
    @Builtin(name = ZIP, minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PZip)
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
    @Builtin(name = "function", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = {PythonBuiltinClassType.PFunction,
                    PythonBuiltinClassType.PGeneratorFunction}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {
        @Specialization
        public PFunction function(PythonClass cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, @SuppressWarnings("unused") PNone closure) {
            return factory().createFunction(name, cls.getName(), code.getArity(), code.getRootCallTarget(), code.getFrameDescriptor(), globals, null);
        }

        @Specialization
        public PFunction function(PythonClass cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure) {
            return factory().createFunction(name, cls.getName(), code.getArity(), code.getRootCallTarget(), code.getFrameDescriptor(), globals, (PCell[]) closure.getArray());
        }

        @Specialization
        public PFunction function(PythonClass cls, PCode code, PDict globals, String name, PTuple defaultArgs, @SuppressWarnings("unused") PNone closure) {
            return factory().createFunction(name, cls.getName(), code.getArity(), code.getRootCallTarget(), code.getFrameDescriptor(), globals, defaultArgs.getArray(), null);
        }

        @Specialization
        public PFunction function(PythonClass cls, PCode code, PDict globals, String name, PTuple defaultArgs, PTuple closure) {
            return factory().createFunction(name, cls.getName(), code.getArity(), code.getRootCallTarget(), code.getFrameDescriptor(), globals, defaultArgs.getArray(), (PCell[]) closure.getArray());
        }

        @Fallback
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object code, Object globals, Object name, Object defaultArgs, Object closure) {
            throw raise(TypeError, "function construction not supported for (%p, %p, %p, %p, %p, %p)", cls, code, globals, name, defaultArgs, closure);
        }
    }

    // builtin-function(method-def, self, module)
    @Builtin(name = "method_descriptor", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = {PythonBuiltinClassType.PBuiltinFunction}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinFunctionNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object method_def, Object def, Object name, Object module) {
            throw raise(TypeError, "cannot create 'method_descriptor' instances");
        }
    }

    // type(object)
    // type(object, bases, dict)
    @Builtin(name = TYPE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PythonClass,
                    PythonBuiltinClassType.PythonBuiltinClass})
    @GenerateNodeFactory
    public abstract static class TypeNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(bases)", "isNoValue(dict)"})
        @SuppressWarnings("unused")
        public Object type(Object cls, Object obj, PNone bases, PNone dict, PKeyword[] kwds,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(obj);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(namespace)"})
        public Object type(VirtualFrame frame, PythonClass cls, String name, PTuple bases, PDict namespace, PKeyword[] kwds,
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
                    return callNewFuncNode.executeCall(frame, newFunc, createArgs.execute(metaclass, name, bases, namespace), kwds);
                }
            }
            return typeMetaclass(name, bases, namespace, metaclass);
        }

        @TruffleBoundary
        private Object typeMetaclass(String name, PTuple bases, PDict namespace, PythonClass metaclass) {
            if (name.indexOf('\0') != -1) {
                throw raise(ValueError, "type name must not contain null characters");
            }
            Object[] array = bases.getArray();
            PythonClass[] basesArray;
            if (array.length == 0) {
                basesArray = new PythonClass[]{getCore().lookupType(PythonBuiltinClassType.PythonObject)};
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

        protected abstract Object execute(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        protected static TypeNode create() {
            return BuiltinConstructorsFactory.TypeNodeFactory.create(null);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        public Object typeGeneric(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
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
            return nextTypeNode.execute(frame, cls, name, bases, dict, kwds);
        }
    }

    @Builtin(name = MODULE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = {PythonBuiltinClassType.PythonModule}, isPublic = false)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @SuppressWarnings("unused")
    public abstract static class ModuleNode extends PythonBuiltinNode {
        @Child WriteAttributeToObjectNode writeFile = WriteAttributeToObjectNode.create();

        @Specialization
        public PythonModule module(PythonClass cls, String name, PNone path) {
            return factory().createPythonModule(cls, name);
        }

        @Specialization
        public PythonModule module(PythonClass cls, String name, String path) {
            PythonModule module = factory().createPythonModule(cls, name);
            writeFile.execute(module, __FILE__, path);
            return module;
        }
    }

    @Builtin(name = "NotImplementedType", fixedNumOfPositionalArgs = 1, constructsClass = {PythonBuiltinClassType.PNotImplemented}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NotImplementedTypeNode extends PythonBuiltinNode {
        protected PythonClass getNotImplementedClass() {
            return getCore().lookupType(PythonBuiltinClassType.PNotImplemented);
        }

        @Specialization
        public PNotImplemented module(Object cls) {
            if (cls != getNotImplementedClass()) {
                throw raise(TypeError, "'NotImplementedType' object is not callable");
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = "ellipsis", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PEllipsis}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class EllipsisTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PEllipsis call(Object cls, Object args, Object kwds) {
            return PEllipsis.INSTANCE;
        }
    }

    @Builtin(name = "NoneType", fixedNumOfPositionalArgs = 1, constructsClass = {PythonBuiltinClassType.PNone}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NoneTypeNode extends PythonBuiltinNode {
        protected PythonClass getNoneClass() {
            return getCore().lookupType(PythonBuiltinClassType.PNone);
        }

        @Specialization
        public PNone module(Object cls) {
            if (cls != getNoneClass()) {
                throw raise(TypeError, "NoneType.__new__(%s) is not a subtype of NoneType", cls);
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "dict_keys", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PDictKeysView}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_keys' instances");
        }
    }

    @Builtin(name = "dict_keysiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PDictKeysIterator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_keysiterator' instances");
        }
    }

    @Builtin(name = "dict_values", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PDictValuesView}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_values' instances");
        }
    }

    @Builtin(name = "dict_valuesiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PDictValuesIterator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_valuesiterator' instances");
        }
    }

    @Builtin(name = "dict_items", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PDictItemsView}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_items' instances");
        }
    }

    @Builtin(name = "dict_itemsiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PDictItemsIterator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_itemsiterator' instances");
        }
    }

    @Builtin(name = "iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {
                    PythonBuiltinClassType.PRangeIterator, PythonBuiltinClassType.PIntegerSequenceIterator, PythonBuiltinClassType.PSequenceIterator,
                    PythonBuiltinClassType.PBaseSetIterator, PythonBuiltinClassType.PRangeIterator, PythonBuiltinClassType.PArrayIterator,
                    PythonBuiltinClassType.PDoubleSequenceIterator, PythonBuiltinClassType.PLongSequenceIterator,
                    PythonBuiltinClassType.PStringIterator, PythonBuiltinClassType.PRangeReverseIterator,
    }, isPublic = false)
    @GenerateNodeFactory
    public abstract static class IteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'iterator' instances");
        }
    }

    @Builtin(name = "callable_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PSentinelIterator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CallableIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'callable_iterator' instances");
        }
    }

    @Builtin(name = "foreign_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PForeignArrayIterator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ForeignIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'foreign_iterator' instances");
        }
    }

    @Builtin(name = "generator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PGenerator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class GeneratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object generator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'generator' instances");
        }
    }

    @Builtin(name = "method", fixedNumOfPositionalArgs = 3, constructsClass = {PythonBuiltinClassType.PMethod}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class MethodTypeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object method(PythonClass cls, Object self, PFunction func) {
            return factory().createMethod(cls, self, func);
        }

        @Specialization(guards = "isPythonBuiltinClass(cls)")
        Object builtinMethod(@SuppressWarnings("unused") PythonClass cls, Object self, PBuiltinFunction func) {
            return factory().createBuiltinMethod(self, func);
        }
    }

    @Builtin(name = "builtin_function_or_method", fixedNumOfPositionalArgs = 3, constructsClass = {PythonBuiltinClassType.PBuiltinMethod}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodTypeNode extends PythonBuiltinNode {
        @Specialization
        Object method(PythonClass cls, Object self, PBuiltinFunction func) {
            return factory().createBuiltinMethod(cls, self, func);
        }
    }

    @Builtin(name = "frame", constructsClass = {PythonBuiltinClassType.PFrame}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FrameTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of frame type");
        }
    }

    @Builtin(name = "traceback", constructsClass = {PythonBuiltinClassType.PTraceback}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class TracebackTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of traceback type");
        }
    }

    @Builtin(name = "code", constructsClass = {PythonBuiltinClassType.PCode}, isPublic = false, minNumOfPositionalArgs = 14, maxNumOfPositionalArgs = 16)
    @GenerateNodeFactory
    public abstract static class CodeTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call(PythonClass cls, int argcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        String codestring, PTuple constants, PTuple names,
                        PTuple varnames, PTuple freevars, PTuple cellvars,
                        Object filename, Object name, int firstlineno,
                        String lnotab) {
            return factory().createCode(cls, argcount, kwonlyargcount,
                            nlocals, stacksize, flags,
                            toBytes(codestring), constants.getArray(), names.getArray(),
                            varnames.getArray(), freevars.getArray(), cellvars.getArray(),
                            getStringArg(filename), getStringArg(name), firstlineno,
                            toBytes(lnotab));
        }

        @Specialization
        Object call(PythonClass cls, int argcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        PBytes codestring, PTuple constants, PTuple names,
                        PTuple varnames, PTuple freevars, PTuple cellvars,
                        Object filename, Object name, int firstlineno,
                        PBytes lnotab,
                        @Cached("create()") SequenceStorageNodes.ToByteArrayNode toByteArrayNode) {
            byte[] codeBytes = toByteArrayNode.execute(codestring.getSequenceStorage());
            byte[] lnotabBytes = toByteArrayNode.execute(lnotab.getSequenceStorage());

            return factory().createCode(cls, argcount, kwonlyargcount,
                            nlocals, stacksize, flags,
                            codeBytes, constants.getArray(), names.getArray(),
                            varnames.getArray(), freevars.getArray(), cellvars.getArray(),
                            getStringArg(filename), getStringArg(name), firstlineno,
                            lnotabBytes);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object call(Object cls, Object argcount, Object kwonlyargcount,
                        Object nlocals, Object stacksize, Object flags,
                        Object codestring, Object constants, Object names,
                        Object varnames, Object freevars, Object cellvars,
                        Object filename, Object name, Object firstlineno,
                        Object lnotab) {
            throw raise(SystemError, "bad argument to internal function");
        }

        private String getStringArg(Object arg) {
            if (arg instanceof String) {
                return (String) arg;
            } else if (arg instanceof PString) {
                return ((PString) arg).toString();
            } else {
                throw raise(SystemError, "bad argument to internal function");
            }
        }

        @TruffleBoundary
        private static byte[] toBytes(String data) {
            return data.getBytes();
        }
    }

    @Builtin(name = "cell", constructsClass = {PythonBuiltinClassType.PCell}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CellTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of cell type");
        }
    }

    @Builtin(name = "BaseException", constructsClass = {PythonBuiltinClassType.PBaseException}, isPublic = true, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object call(PythonClass cls, Object[] varargs, PKeyword[] kwargs) {
            return factory().createBaseException(cls, factory().createTuple(varargs));
        }
    }

    @Builtin(name = "mappingproxy", constructsClass = {PythonBuiltinClassType.PMappingproxy}, isPublic = false, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MappingproxyNode extends PythonBuiltinNode {
        @Child private IsSequenceNode isMappingNode;

        @Specialization
        Object doMapping(PythonClass klass, PHashingCollection obj) {
            return factory().createMappingproxy(klass, obj.getDictStorage());
        }

        @Specialization(guards = {"isMapping(obj)", "!isBuiltinMapping(obj)"})
        Object doMapping(PythonClass klass, PythonObject obj,
                        @Cached("create()") HashingStorageNodes.InitNode initNode) {
            return factory().createMappingproxy(klass, initNode.execute(obj, PKeyword.EMPTY_KEYWORDS));
        }

        @Specialization(guards = "isNoValue(none)")
        @SuppressWarnings("unused")
        Object doMissing(PythonClass klass, PNone none) {
            throw raise(TypeError, "mappingproxy() missing required argument 'mapping' (pos 1)");
        }

        @Specialization(guards = {"!isMapping(obj)", "!isNoValue(obj)"})
        @SuppressWarnings("unused")
        Object doInvalid(PythonClass klass, Object obj) {
            throw raise(TypeError, "mappingproxy() argument must be a mapping, not %p", obj);
        }

        protected boolean isBuiltinMapping(Object o) {
            return o instanceof PHashingCollection;
        }

        protected boolean isMapping(Object o) {
            if (isMappingNode == null) {
                CompilerDirectives.transferToInterpreter();
                isMappingNode = insert(IsSequenceNode.create());
            }
            return isMappingNode.execute(o);
        }
    }

    @Builtin(name = "getset_descriptor", constructsClass = {PythonBuiltinClassType.GetSetDescriptor}, isPublic = false, fixedNumOfPositionalArgs = 1, keywordArguments = {"fget", "fset", "name",
                    "owner"})
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
    @Builtin(name = "slice", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PSlice)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class CreateSliceNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(second)")
        Object sliceStop(PythonClass cls, int first, PNone second, PNone third) {
            return factory().createSlice(MISSING_INDEX, first, MISSING_INDEX);
        }

        @Specialization(guards = "isNone(second)")
        Object sliceStart(PythonClass cls, int first, PNone second, PNone third) {
            return factory().createSlice(first, MISSING_INDEX, MISSING_INDEX);
        }

        @Specialization(guards = "isNoValue(third)")
        Object slice(PythonClass cls, int first, int second, PNone third) {
            return factory().createSlice(first, second, MISSING_INDEX);
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
    @Builtin(name = "buffer", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuffer)
    @GenerateNodeFactory
    public abstract static class BufferNode extends PythonBuiltinNode {
        @Child private LookupInheritedAttributeNode getSetItemNode;

        @Specialization(guards = "isNoValue(readOnly)")
        protected PBuffer construct(PythonClass cls, Object delegate, @SuppressWarnings("unused") PNone readOnly) {
            return factory().createBuffer(cls, delegate, !hasSetItem(delegate));
        }

        @Specialization
        protected PBuffer construct(PythonClass cls, Object delegate, boolean readOnly) {
            return factory().createBuffer(cls, delegate, readOnly);
        }

        @Fallback
        public PBuffer doGeneric(@SuppressWarnings("unused") Object cls, Object delegate, @SuppressWarnings("unused") Object readOnly) {
            throw raise(TypeError, "cannot create buffer for object %s", delegate);
        }

        public boolean hasSetItem(Object object) {
            if (getSetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSetItemNode = insert(LookupInheritedAttributeNode.create(__SETITEM__));
            }
            return getSetItemNode.execute(object) != PNone.NO_VALUE;
        }
    }

    // memoryview(obj)
    @Builtin(name = MEMORYVIEW, fixedNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PMemoryView)
    @GenerateNodeFactory
    public abstract static class MemoryViewNode extends PythonBuiltinNode {
        @Specialization
        public PMemoryView doGeneric(PythonClass cls, Object value) {
            return factory().createMemoryView(cls, value);
        }
    }

    // super()
    @Builtin(name = SUPER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.Super)
    @GenerateNodeFactory
    public abstract static class SuperInitNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(PythonClass self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object object) {
            return factory().createSuperObject(self);
        }
    }
}
