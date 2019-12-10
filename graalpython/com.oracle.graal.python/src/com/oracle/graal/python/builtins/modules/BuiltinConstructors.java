/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_ADD_NATIVE_SLOTS;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_OBJECT_GENERIC_NEW;
import static com.oracle.graal.python.builtins.objects.slice.PSlice.MISSING_INDEX;
import static com.oracle.graal.python.nodes.BuiltinNames.BOOL;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTES;
import static com.oracle.graal.python.nodes.BuiltinNames.CLASSMETHOD;
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
import static com.oracle.graal.python.nodes.BuiltinNames.STATICMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.STR;
import static com.oracle.graal.python.nodes.BuiltinNames.SUPER;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.ZIP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__WEAKREF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToDoubleNode;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.nodes.util.CastToStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.ExecutionContext.ForeignCallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = BuiltinNames.BUILTINS)
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

    @TypeSystemReference(PythonArithmeticTypes.class)
    protected abstract static class CreateByteOrByteArrayNode extends PythonBuiltinNode {
        @Child private CastToIndexNode castToIndexNode;

        private final IsBuiltinClassProfile isClassProfile = IsBuiltinClassProfile.create();

        @SuppressWarnings("unused")
        protected Object create(LazyPythonClass cls, byte[] barr) {
            throw new AssertionError("should not reach");
        }

        @Specialization(guards = {"isNoValue(source)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(LazyPythonClass cls, @SuppressWarnings("unused") PNone source, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, new byte[0]);
        }

        @Specialization(guards = {"lib.canBeIndex(capObj)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(VirtualFrame frame, LazyPythonClass cls, Object capObj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            int cap = getCastToIndexNode().execute(frame, capObj);
            return create(cls, BytesUtils.fromSize(getCore(), cap));
        }

        @Specialization(guards = "isNoValue(errors)")
        public Object fromString(LazyPythonClass cls, String source, String encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, BytesUtils.fromStringAndEncoding(getCore(), source, encoding));
        }

        @Specialization(guards = {"isNoValue(encoding)", "isNoValue(errors)"})
        @SuppressWarnings("unused")
        public Object fromString(LazyPythonClass cls, String source, PNone encoding, PNone errors) {
            throw raise(PythonErrorType.TypeError, "string argument without an encoding");
        }

        protected boolean isSimpleBytes(PBytes iterable) {
            return isClassProfile.profileObject(iterable, PythonBuiltinClassType.PBytes) && iterable.getSequenceStorage() instanceof ByteSequenceStorage;
        }

        @Specialization(guards = {"isSimpleBytes(iterable)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(LazyPythonClass cls, PBytes iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, (byte[]) ((ByteSequenceStorage) iterable.getSequenceStorage()).getCopyOfInternalArrayObject());
        }

        protected boolean isSimpleBytes(PByteArray iterable) {
            return isClassProfile.profileObject(iterable, PythonBuiltinClassType.PByteArray) && iterable.getSequenceStorage() instanceof ByteSequenceStorage;
        }

        @Specialization(guards = {"isSimpleBytes(iterable)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(LazyPythonClass cls, PByteArray iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return create(cls, (byte[]) ((ByteSequenceStorage) iterable.getSequenceStorage()).getCopyOfInternalArrayObject());
        }

        @Specialization(guards = {"!lib.canBeIndex(iterable)", "!isNoValue(iterable)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object bytearray(VirtualFrame frame, LazyPythonClass cls, Object iterable, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") IsBuiltinClassProfile stopIterationProfile,
                        @Cached("create()") CastToByteNode castToByteNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {

            Object it = getIteratorNode.executeWith(frame, iterable);
            byte[] arr = new byte[16];
            int i = 0;
            while (true) {
                try {
                    byte item = castToByteNode.execute(frame, getNextNode.execute(frame, it));
                    if (i >= arr.length) {
                        arr = resize(arr, arr.length * 2);
                    }
                    arr[i++] = item;
                } catch (PException e) {
                    e.expectStopIteration(stopIterationProfile);
                    return create(cls, resize(arr, i));
                }
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte[] resize(byte[] arr, int len) {
            return Arrays.copyOf(arr, len);
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
        protected Object create(LazyPythonClass cls, byte[] barr) {
            return factory().createBytes(cls, barr);
        }
    }

    // bytearray([source[, encoding[, errors]]])
    @Builtin(name = BYTEARRAY, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PByteArray)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ByteArrayNode extends CreateByteOrByteArrayNode {
        @Override
        protected Object create(LazyPythonClass cls, byte[] barr) {
            return factory().createByteArray(cls, barr);
        }
    }

    // complex([real[, imag]])
    @Builtin(name = COMPLEX, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PComplex, doc = "complex(real[, imag]) -> complex number\n\n" +
                    "Create a complex number from a real part and an optional imaginary part.\n" +
                    "This is equivalent to (real + imag*1j) where imag defaults to 0.")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ComplexNode extends PythonBuiltinNode {
        @Specialization
        PComplex complexFromIntInt(@SuppressWarnings("unused") Object cls, int real, int imaginary) {
            return factory().createComplex(real, imaginary);
        }

        @Specialization
        PComplex complexFromLongLong(@SuppressWarnings("unused") Object cls, long real, long imaginary) {
            return factory().createComplex(real, imaginary);
        }

        @Specialization
        PComplex complexFromLongLong(@SuppressWarnings("unused") Object cls, PInt real, PInt imaginary) {
            return factory().createComplex(real.doubleValue(), imaginary.doubleValue());
        }

        @Specialization
        PComplex complexFromDoubleDouble(@SuppressWarnings("unused") Object cls, double real, double imaginary) {
            return factory().createComplex(real, imaginary);
        }

        @Specialization
        PComplex complexFromDouble(@SuppressWarnings("unused") Object cls, double real, @SuppressWarnings("unused") PNone image) {
            return factory().createComplex(real, 0);
        }

        @Specialization
        PComplex complexFromInt(@SuppressWarnings("unused") Object cls, int real, @SuppressWarnings("unused") PNone image) {
            return factory().createComplex(real, 0);
        }

        @Specialization
        PComplex complexFromLong(@SuppressWarnings("unused") Object cls, long real, @SuppressWarnings("unused") PNone image) {
            return factory().createComplex(real, 0);
        }

        @Specialization
        PComplex complexFromLong(@SuppressWarnings("unused") Object cls, PInt real, @SuppressWarnings("unused") PNone image) {
            return factory().createComplex(real.doubleValue(), 0);
        }

        @Specialization
        @SuppressWarnings("unused")
        PComplex complexFromNone(Object cls, PNone real, PNone image) {
            return factory().createComplex(0, 0);
        }

        @Specialization
        PComplex complexFromObjectObject(Object cls, String real, Object imaginary) {
            if (!(imaginary instanceof PNone)) {
                throw raise(TypeError, "complex() can't take second arg if first is a string");
            }
            return convertStringToComplex(real, (LazyPythonClass) cls);
        }

        @Specialization
        Object complexGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object realObj, Object imaginaryObj,
                        @Cached("create()") GetLazyClassNode getClassNode,
                        @Cached("create()") IsBuiltinClassProfile isComplexTypeProfile,
                        @Cached("create()") BranchProfile errorProfile,
                        @Cached("create()") CastToDoubleNode castRealNode,
                        @Cached("create()") CastToDoubleNode castImagNode,
                        @Cached("create()") IsBuiltinClassProfile profile) {
            boolean noImag = PGuards.isNoValue(imaginaryObj);
            if (noImag && isComplexTypeProfile.profileClass(getClassNode.execute(realObj), PythonBuiltinClassType.PComplex)) {
                return realObj;
            }
            try {
                double real = castRealNode.execute(frame, realObj);
                double imag = !noImag ? castImagNode.execute(frame, imaginaryObj) : 0.0;
                return factory().createComplex(real, imag);
            } catch (PException e) {
                errorProfile.enter();
                e.expect(PythonBuiltinClassType.TypeError, profile);
                throw raise(TypeError, "can't convert real %s imag %s", realObj, imaginaryObj);
            }
        }

        protected static boolean isExactComplexType(GetLazyClassNode getClassNode, PComplex obj) {
            return getClassNode.execute(obj) == PythonBuiltinClassType.PComplex;
        }

        // Taken from Jython PyString's __complex__() method
        @TruffleBoundary(transferToInterpreterOnException = false)
        private PComplex convertStringToComplex(String str, LazyPythonClass cls) {
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
                throw raise(ValueError, "empty string for complex()");
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
                            throw raise(ValueError, String.format("float() out of range: %.150s", str));
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
                throw raise(ValueError, "malformed string for complex() %s", str.substring(s));
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
    }

    // dict(**kwarg)
    // dict(mapping, **kwarg)
    // dict(iterable, **kwarg)
    @Builtin(name = DICT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDict)
    @GenerateNodeFactory
    public abstract static class DictionaryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PDict dictEmpty(LazyPythonClass cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createDict(cls);
        }
    }

    // enumerate(iterable, start=0)
    @Builtin(name = ENUMERATE, minNumOfPositionalArgs = 2, parameterNames = {"cls", "iterable", "start"}, constructsClass = PythonBuiltinClassType.PEnumerate)
    @GenerateNodeFactory
    public abstract static class EnumerateNode extends PythonBuiltinNode {

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, LazyPythonClass cls, Object iterable, @SuppressWarnings("unused") PNone keywordArg,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), 0);
        }

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, LazyPythonClass cls, Object iterable, int start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), start);
        }

        @Specialization
        public PEnumerate enumerate(VirtualFrame frame, LazyPythonClass cls, Object iterable, long start,
                        @Cached("create()") GetIteratorNode getIterator) {
            return factory().createEnumerate(cls, getIterator.executeWith(frame, iterable), start);
        }

        @Specialization(guards = "!isInteger(start)")
        public void enumerate(@SuppressWarnings("unused") LazyPythonClass cls, @SuppressWarnings("unused") Object iterable, Object start) {
            raise(TypeError, "%p object cannot be interpreted as an integer", start);
        }
    }

    // reversed(seq)
    @Builtin(name = REVERSED, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PReverseIterator)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonBuiltinNode {

        @Specialization
        public PythonObject reversed(@SuppressWarnings("unused") LazyPythonClass cls, PRange range,
                        @Cached("createBinaryProfile()") ConditionProfile stepPositiveProfile,
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
            return factory().createRangeIterator(start, stop, step, stepPositiveProfile);
        }

        @Specialization
        public PythonObject reversed(LazyPythonClass cls, PString value) {
            return factory().createStringReverseIterator(cls, value.getValue());
        }

        @Specialization
        public PythonObject reversed(LazyPythonClass cls, String value) {
            return factory().createStringReverseIterator(cls, value);
        }

        @Specialization(guards = {"!isString(sequence)", "!isPRange(sequence)"})
        public Object reversed(VirtualFrame frame, LazyPythonClass cls, Object sequence,
                        @Cached("create()") GetLazyClassNode getClassNode,
                        @Cached("create(__REVERSED__)") LookupAttributeInMRONode reversedNode,
                        @Cached("create()") CallUnaryMethodNode callReversedNode,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode lenNode,
                        @Cached("create(__GETITEM__)") LookupAttributeInMRONode getItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile noReversedProfile,
                        @Cached("createBinaryProfile()") ConditionProfile noGetItemProfile) {
            LazyPythonClass sequenceKlass = getClassNode.execute(sequence);
            Object reversed = reversedNode.execute(sequenceKlass);
            if (noReversedProfile.profile(reversed == PNone.NO_VALUE)) {
                Object getItem = getItemNode.execute(sequenceKlass);
                if (noGetItemProfile.profile(getItem == PNone.NO_VALUE)) {
                    throw raise(TypeError, "'%p' object is not reversible", sequence);
                } else {
                    try {
                        return factory().createSequenceReverseIterator(cls, sequence, lenNode.executeInt(frame, sequence));
                    } catch (UnexpectedResultException e) {
                        throw raise(TypeError, "%p object cannot be interpreted as an integer", e.getResult());
                    }
                }
            } else {
                return callReversedNode.executeObject(frame, reversed, sequence);
            }
        }
    }

    // float([x])
    @Builtin(name = FLOAT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFloat)
    @GenerateNodeFactory
    public abstract static class FloatNode extends PythonBuiltinNode {
        @Child private BytesNodes.ToBytesNode toByteArrayNode;

        private final IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();
        @CompilationFinal private ConditionProfile isNanProfile;

        public abstract Object executeWith(VirtualFrame frame, Object cls, Object arg);

        protected final boolean isPrimitiveFloat(LazyPythonClass cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PFloat);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromInt(LazyPythonClass cls, int arg) {
            if (isPrimitiveFloat(cls)) {
                return (double) arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromBoolean(LazyPythonClass cls, boolean arg) {
            if (isPrimitiveFloat(cls)) {
                return arg ? 1d : 0d;
            }
            return factory().createFloat(cls, arg ? 1d : 0d);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromLong(LazyPythonClass cls, long arg) {
            if (isPrimitiveFloat(cls)) {
                return (double) arg;
            }
            return factory().createFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromPInt(LazyPythonClass cls, PInt arg) {
            if (isPrimitiveFloat(cls)) {
                return arg.doubleValue();
            }
            return factory().createFloat(cls, arg.doubleValue());
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromDouble(LazyPythonClass cls, double arg) {
            if (isPrimitiveFloat(cls)) {
                return arg;
            }
            return factoryCreateFloat(cls, arg);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromString(LazyPythonClass cls, String arg) {
            double value = convertStringToDouble(arg);
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factoryCreateFloat(cls, value);
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromBytes(VirtualFrame frame, LazyPythonClass cls, PIBytesLike arg) {
            double value = convertBytesToDouble(frame, arg);
            if (isPrimitiveFloat(cls)) {
                return value;
            }
            return factoryCreateFloat(cls, value);
        }

        private double convertBytesToDouble(VirtualFrame frame, PIBytesLike arg) {
            return convertStringToDouble(createString(getByteArray(frame, arg)));
        }

        @TruffleBoundary
        private static String createString(byte[] bytes) {
            return new String(bytes);
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
                    throw raise(ValueError, "empty string for complex()");
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
                String lowSval = sval.toLowerCase(Locale.ENGLISH);
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
                throw raise(ValueError, "could not convert string to float: %s", str);
            }
        }

        @Specialization(guards = "!isNativeClass(cls)")
        Object floatFromNone(LazyPythonClass cls, @SuppressWarnings("unused") PNone arg) {
            if (isPrimitiveFloat(cls)) {
                return 0.0;
            }
            return factory().createFloat(cls, 0.0);
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double doubleFromObject(VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass cls, Object obj,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached("create()") BranchProfile gotException) {
            if (obj instanceof String) {
                return convertStringToDouble((String) obj);
            } else if (obj instanceof PString) {
                return convertStringToDouble(((PString) obj).getValue());
            } else if (obj instanceof PNone) {
                return 0.0;
            } else if (obj instanceof PIBytesLike) {
                return convertBytesToDouble(frame, (PIBytesLike) obj);
            }
            try {
                return callFloatNode.executeDouble(frame, obj);
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
        Object doPythonObject(VirtualFrame frame, LazyPythonClass cls, Object obj,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached("create()") BranchProfile gotException) {
            return floatFromDouble(cls, doubleFromObject(frame, cls, obj, callFloatNode, gotException));
        }

        // logic similar to float_subtype_new(PyTypeObject *type, PyObject *x) from CPython
        // floatobject.c we have to first create a temporary float, then fill it into
        // a natively allocated subtype structure
        @Specialization(guards = "isSubtypeOfFloat(frame, isSubtype, cls)", limit = "1")
        Object doPythonObject(VirtualFrame frame, PythonNativeClass cls, Object obj,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode callFloatNode,
                        @Cached BranchProfile gotException,
                        @Cached CExtNodes.FloatSubtypeNew subtypeNew) {
            double realFloat = doubleFromObject(frame, PythonBuiltinClassType.PFloat, obj, callFloatNode, gotException);
            return subtypeNew.call(cls, realFloat);
        }

        @Fallback
        @TruffleBoundary
        Object floatFromObject(@SuppressWarnings("unused") Object cls, Object arg) {
            throw raise(TypeError, "can't convert %s to float", arg.getClass().getSimpleName());
        }

        protected static boolean isSubtypeOfFloat(VirtualFrame frame, IsSubtypeNode isSubtypeNode, PythonNativeClass cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PFloat);
        }

        private byte[] getByteArray(VirtualFrame frame, PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toByteArrayNode.execute(frame, pByteArray);
        }

        private PFloat factoryCreateFloat(LazyPythonClass cls, double arg) {
            if (isNaN(arg)) {
                return getCore().getNaN();
            }
            return factory().createFloat(cls, arg);
        }

        private boolean isNaN(double d) {
            if (isNanProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNanProfile = ConditionProfile.createBinaryProfile();
            }
            return isNanProfile.profile(Double.isNaN(d));
        }
    }

    // frozenset([iterable])
    @Builtin(name = FROZENSET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFrozenSet)
    @GenerateNodeFactory
    public abstract static class FrozenSetNode extends PythonBuiltinNode {

        @Child private HashingCollectionNodes.SetItemNode setItemNode;

        @Specialization(guards = "isNoValue(arg)")
        public PFrozenSet frozensetEmpty(LazyPythonClass cls, @SuppressWarnings("unused") PNone arg) {
            return factory().createFrozenSet(cls);
        }

        @Specialization
        public PFrozenSet frozenset(VirtualFrame frame, LazyPythonClass cls, String arg) {
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            for (int i = 0; i < PString.length(arg); i++) {
                getSetItemNode().execute(frame, frozenSet, PString.valueOf(PString.charAt(arg, i)), PNone.NO_VALUE);
            }
            return frozenSet;
        }

        @Specialization(guards = "!isNoValue(iterable)")
        public PFrozenSet frozensetIterable(VirtualFrame frame, LazyPythonClass cls, Object iterable,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("create()") IsBuiltinClassProfile errorProfile) {

            Object iterator = getIterator.executeWith(frame, iterable);
            PFrozenSet frozenSet = factory().createFrozenSet(cls);
            while (true) {
                try {
                    getSetItemNode().execute(frame, frozenSet, next.execute(frame, iterator), PNone.NO_VALUE);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
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
    @Builtin(name = INT, minNumOfPositionalArgs = 1, parameterNames = {"cls", "x", "base"}, constructsClass = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    public abstract static class IntNode extends PythonBuiltinNode {

        private final ConditionProfile invalidBase = ConditionProfile.createBinaryProfile();
        private final BranchProfile invalidValueProfile = BranchProfile.create();
        private final BranchProfile bigIntegerProfile = BranchProfile.create();
        private final BranchProfile primitiveIntProfile = BranchProfile.create();
        private final BranchProfile fullIntProfile = BranchProfile.create();

        @Child private BytesNodes.ToBytesNode toByteArrayNode;

        public abstract Object executeWith(VirtualFrame frame, Object cls, Object arg, Object base);

        @TruffleBoundary
        private static Object stringToIntInternal(String num, int base) {
            String s = num.replace("_", "");
            try {
                BigInteger bi = asciiToBigInteger(s, base, false);
                if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    return bi;
                } else {
                    return bi.intValue();
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Object stringToInt(LazyPythonClass cls, String number, int base) {
            Object value = stringToIntInternal(number, base);
            if (value == null) {
                invalidValueProfile.enter();
                throw raise(ValueError, "invalid literal for int() with base %s: %s", base, number);
            } else if (value instanceof BigInteger) {
                bigIntegerProfile.enter();
                return factory().createInt(cls, (BigInteger) value);
            } else if (isPrimitiveInt(cls)) {
                primitiveIntProfile.enter();
                return value;
            } else {
                fullIntProfile.enter();
                return factory().createInt(cls, (int) value);
            }
        }

        private void checkBase(int base) {
            if (invalidBase.profile((base < 2 || base > 32) && base != 0)) {
                throw raise(ValueError, "base is out of range for int()");
            }
        }

        // Copied directly from Jython
        private static BigInteger asciiToBigInteger(String str, int possibleBase, boolean isLong) throws NumberFormatException {
            CompilerAsserts.neverPartOfCompilation();
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

        @TruffleBoundary
        private static int parseInt(String arg, int base) {
            if (arg.isEmpty() || base == 0) {
                throw new NumberFormatException();
            }
            boolean negative = arg.charAt(0) == '-';
            int start = negative ? 1 : 0;
            if (arg.length() <= start || arg.charAt(start) == '_') {
                throw new NumberFormatException();
            }
            long value = 0;
            for (int i = start; i < arg.length(); i++) {
                char c = arg.charAt(i);
                if (c == '_') {
                    continue;
                }
                if (c < '0' || c > '9') {
                    throw new NumberFormatException();
                }
                value = value * base + (c - '0');
                if (value > Integer.MAX_VALUE) {
                    throw new NumberFormatException();
                }
            }
            return (int) (negative ? -value : value);
        }

        private static final long MAX_VALUE = (Long.MAX_VALUE - 10) / 10;

        @TruffleBoundary
        private static long parseLong(String arg, int base) {
            if (arg.isEmpty() || base == 0) {
                throw new NumberFormatException();
            }
            boolean negative = arg.charAt(0) == '-';
            int start = negative ? 1 : 0;
            if (arg.length() <= start || arg.charAt(start) == '_') {
                throw new NumberFormatException();
            }
            long value = 0;
            for (int i = start; i < arg.length(); i++) {
                char c = arg.charAt(i);
                if (c == '_') {
                    continue;
                }
                if (c < '0' || c > '9') {
                    throw new NumberFormatException();
                }
                if (value >= MAX_VALUE) {
                    // overflow, this will not allow Long.MIN_VALUE to be parsed
                    throw new NumberFormatException();
                }
                value = value * base + (c - '0');
            }
            return negative ? -value : value;
        }

        private final IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        protected boolean isPrimitiveInt(LazyPythonClass cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PInt);
        }

        @Specialization
        Object parseInt(LazyPythonClass cls, boolean arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg ? 1 : 0;
            } else {
                return factory().createInt(cls, arg ? 1 : 0);
            }
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(LazyPythonClass cls, int arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg;
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(LazyPythonClass cls, long arg, @SuppressWarnings("unused") PNone base,
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

        @Specialization(guards = "isNoValue(base)")
        Object createInt(VirtualFrame frame, LazyPythonClass cls, double arg, @SuppressWarnings("unused") PNone base,
                        @Cached("createFloatInt()") FloatBuiltins.IntNode intNode,
                        @Cached("createGeneric()") CreateIntFromObjectNode createIntFromObjectNode) {
            Object result = intNode.executeWithDouble(arg);
            return createIntFromObjectNode.execute(frame, cls, result);
        }

        // String

        @Specialization(guards = {"isNoValue(base)", "isPrimitiveInt(cls)"}, rewriteOn = NumberFormatException.class)
        int createIntBase10(@SuppressWarnings("unused") LazyPythonClass cls, String arg, @SuppressWarnings("unused") PNone base) throws NumberFormatException {
            return parseInt(arg, 10);
        }

        @Specialization(guards = {"isNoValue(base)", "isPrimitiveInt(cls)"}, rewriteOn = NumberFormatException.class)
        long createLongBase10(@SuppressWarnings("unused") LazyPythonClass cls, String arg, @SuppressWarnings("unused") PNone base) throws NumberFormatException {
            return parseLong(arg, 10);
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(LazyPythonClass cls, String arg, @SuppressWarnings("unused") PNone base) {
            return stringToInt(cls, arg, 10);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        int parseInt(@SuppressWarnings("unused") LazyPythonClass cls, String arg, int base) throws NumberFormatException {
            checkBase(base);
            return parseInt(arg, base);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        long parseLong(@SuppressWarnings("unused") LazyPythonClass cls, String arg, int base) throws NumberFormatException {
            checkBase(base);
            return parseLong(arg, base);
        }

        @Specialization
        Object parsePIntError(LazyPythonClass cls, String number, int base) {
            checkBase(base);
            return stringToInt(cls, number, base);
        }

        @Specialization(guards = "!isNoValue(base)")
        Object createIntError(VirtualFrame frame, LazyPythonClass cls, String number, Object base,
                        @Cached CastToIndexNode castToIndexNode) {
            int intBase = castToIndexNode.execute(frame, base);
            checkBase(intBase);
            return stringToInt(cls, number, intBase);
        }

        // PIBytesLike

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        int parseInt(VirtualFrame frame, LazyPythonClass cls, PIBytesLike arg, int base) throws NumberFormatException {
            return parseInt(cls, toString(frame, arg), base);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        long parseLong(VirtualFrame frame, LazyPythonClass cls, PIBytesLike arg, int base) throws NumberFormatException {
            return parseLong(cls, toString(frame, arg), base);
        }

        @Specialization
        Object parseBytesError(VirtualFrame frame, LazyPythonClass cls, PIBytesLike arg, int base) {
            checkBase(base);
            return stringToInt(cls, toString(frame, arg), base);
        }

        @Specialization(guards = "isNoValue(base)")
        Object parseBytesError(VirtualFrame frame, LazyPythonClass cls, PIBytesLike arg, @SuppressWarnings("unused") PNone base) {
            return parseBytesError(frame, cls, arg, 10);
        }

        // PString

        @Specialization(guards = {"isNoValue(base)", "isPrimitiveInt(cls)"}, rewriteOn = NumberFormatException.class)
        int createInt(@SuppressWarnings("unused") LazyPythonClass cls, PString arg, @SuppressWarnings("unused") PNone base) throws NumberFormatException {
            return parseInt(arg.getValue(), 10);
        }

        @Specialization(guards = {"isNoValue(base)", "isPrimitiveInt(cls)"}, rewriteOn = NumberFormatException.class)
        long createLong(@SuppressWarnings("unused") LazyPythonClass cls, PString arg, @SuppressWarnings("unused") PNone base) throws NumberFormatException {
            return parseLong(arg.getValue(), 10);
        }

        @Specialization(guards = "isNoValue(base)")
        Object parsePInt(LazyPythonClass cls, PString arg, @SuppressWarnings("unused") PNone base) {
            return stringToInt(cls, arg.getValue(), 10);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        int parseInt(@SuppressWarnings("unused") LazyPythonClass cls, PString arg, int base) throws NumberFormatException {
            checkBase(base);
            return parseInt(arg.getValue(), base);
        }

        @Specialization(guards = "isPrimitiveInt(cls)", rewriteOn = NumberFormatException.class)
        long parseLong(@SuppressWarnings("unused") LazyPythonClass cls, PString arg, int base) throws NumberFormatException {
            checkBase(base);
            return parseLong(arg.getValue(), base);
        }

        @Specialization
        Object parsePInt(LazyPythonClass cls, PString arg, int base) {
            checkBase(base);
            return stringToInt(cls, arg.getValue(), base);
        }

        // other

        @Specialization(guards = "isNoValue(base)")
        Object createInt(LazyPythonClass cls, PythonNativeVoidPtr arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("cannot wrap void ptr in int subclass");
            }
        }

        @Specialization
        Object createInt(LazyPythonClass cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return 0;
            }
            return factory().createInt(cls, 0);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(arg)", "!isNoValue(base)"})
        Object fail(LazyPythonClass cls, Object arg, Object base) {
            throw raise(TypeError, "int() can't convert non-string with explicit base");
        }

        @Specialization(guards = {"isNoValue(base)", "!isNoValue(obj)", "!isHandledType(obj)"})
        Object createInt(VirtualFrame frame, LazyPythonClass cls, Object obj, @SuppressWarnings("unused") PNone base,
                        @Cached("createGeneric()") CreateIntFromObjectNode createIntFromObjectNode) {
            return createIntFromObjectNode.execute(frame, cls, obj);
        }

        protected static boolean isHandledType(Object obj) {
            return PGuards.isInteger(obj) || obj instanceof Double || obj instanceof Boolean || PGuards.isString(obj) || PGuards.isBytes(obj) || obj instanceof PythonNativeVoidPtr;
        }

        protected static CreateIntFromObjectNode createGeneric() {
            return CreateIntFromObjectNode.create(true, () -> LookupAndCallUnaryNode.create(SpecialMethodNames.__INT__));
        }

        protected static FloatBuiltins.IntNode createFloatInt() {
            return FloatBuiltinsFactory.IntNodeFactory.create();
        }

        private String toString(VirtualFrame frame, PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toString(toByteArrayNode.execute(frame, pByteArray));
        }

        @TruffleBoundary
        private static String toString(byte[] barr) {
            return new String(barr);
        }

    }

    protected static final class CreateIntFromObjectNode extends Node {

        private static final int STATE_INT = 0;
        private static final int STATE_LONG = 1;
        private static final int STATE_GENERIC = 2;

        @Child private PythonObjectFactory objectFactory;
        @Child private LookupAndCallUnaryNode callNode;
        @Child private CreateIntFromObjectNode recursive;
        @Child private PRaiseNode raiseNode;

        @CompilationFinal private int state;
        private final IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();
        private final boolean allowRecursive;
        private final Supplier<LookupAndCallUnaryNode> callNodeSupplier;

        public CreateIntFromObjectNode(boolean allowRecursive, Supplier<LookupAndCallUnaryNode> callNodeSupplier) {
            this.allowRecursive = allowRecursive;
            this.callNodeSupplier = callNodeSupplier;
        }

        public Object execute(VirtualFrame frame, LazyPythonClass cls, Object obj) {
            try {
                switch (state) {
                    case STATE_INT:
                        return createInt(cls, ensureCallNode().executeInt(frame, obj));
                    case STATE_LONG:
                        return createLong(cls, ensureCallNode().executeLong(frame, obj));
                    case STATE_GENERIC:
                        return createGeneric(frame, cls, obj, ensureCallNode().executeObject(frame, obj));
                }
            } catch (UnexpectedResultException e) {
                return executeAndSpecialize(frame, cls, obj, e.getResult());
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }

        private Object executeAndSpecialize(VirtualFrame frame, LazyPythonClass cls, Object obj, Object result) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            switch (state) {
                case STATE_INT:
                    state = STATE_LONG;
                    return createGeneric(frame, cls, obj, result);
                case STATE_LONG:
                    state = STATE_GENERIC;
                    return createGeneric(frame, cls, obj, result);
                case STATE_GENERIC:
                    assert false;
            }
            throw new IllegalStateException();
        }

        private Object createInt(LazyPythonClass cls, int ival) {
            if (isPrimitiveInt(cls)) {
                return ival;
            }
            return factory().createInt(cls, ival);
        }

        private Object createLong(LazyPythonClass cls, long lval) {
            if (isPrimitiveInt(cls)) {
                return lval;
            }
            return factory().createInt(cls, lval);
        }

        private Object createGeneric(VirtualFrame frame, LazyPythonClass cls, Object obj, Object result) {
            if (result == PNone.NO_VALUE && ensureRecursive()) {
                return recursive.execute(frame, cls, obj);
            }
            if (result == PNone.NO_VALUE) {
                throw ensureRaiseNode().raise(TypeError, "an integer is required (got type %p)", obj);
            } else if (result instanceof Integer) {
                return createInt(cls, (int) result);
            } else if (result instanceof Long) {
                return createLong(cls, (long) result);
            } else if (result instanceof Boolean) {
                return createInt(cls, (boolean) result ? 1 : 0);
            } else if (result instanceof PInt) {
                // TODO warn if 'result' not of exact Python type 'int'
                return isPrimitiveInt(cls) ? result : factory().createInt(cls, ((PInt) result).getValue());
            } else {
                throw ensureRaiseNode().raise(TypeError, "__int__ returned non-int (type %p)", result);
            }
        }

        private LookupAndCallUnaryNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(callNodeSupplier.get());
            }
            return callNode;
        }

        private boolean ensureRecursive() {
            if (allowRecursive && recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(CreateIntFromObjectNode.create(false, () -> LookupAndCallUnaryNode.create(SpecialMethodNames.__TRUNC__)));
            }
            return allowRecursive;
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        private static CreateIntFromObjectNode create(boolean allowRecursive, Supplier<LookupAndCallUnaryNode> callNode) {
            return new CreateIntFromObjectNode(allowRecursive, callNode);
        }

        protected boolean isPrimitiveInt(LazyPythonClass cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PInt);
        }

        protected final PythonObjectFactory factory() {
            if (objectFactory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectFactory = insert(PythonObjectFactory.create());
            }
            return objectFactory;
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
        public boolean bool(VirtualFrame frame, Object cls, Object obj,
                        @Cached("create(__BOOL__)") LookupAndCallUnaryNode callNode) {
            try {
                return callNode.executeBoolean(frame, obj);
            } catch (UnexpectedResultException ex) {
                throw raise(PythonErrorType.TypeError, "__bool__ should return bool, returned %p", ex.getResult());
            }
        }

    }

    // list([iterable])
    @Builtin(name = LIST, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PList)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonVarargsBuiltinNode {
        @Specialization
        protected PList constructList(LazyPythonClass cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createList(cls);
        }

        @Fallback
        @SuppressWarnings("unused")
        public PList listObject(Object cls, Object[] arguments, PKeyword[] keywords) {
            throw raise(PythonBuiltinClassType.TypeError, "'cls' is not a type object (%p)", cls);
        }
    }

    // object()
    @Builtin(name = OBJECT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonObject)
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {
        @Child private PCallCapiFunction callCapiFunction;
        @Children private CExtNodes.ToSulongNode[] toSulongNodes;
        @Child private CExtNodes.AsPythonObjectNode asPythonObjectNode;
        @Child private TypeNodes.GetInstanceShape getInstanceShapeNode;
        @Child private SplitArgsNode splitArgsNode;

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf", "!self.needsNativeAllocation()"}, assumptions = "singleContextAssumption()")
        Object doObjectDirect(@SuppressWarnings("unused") PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Cached("self") PythonManagedClass cachedSelf) {
            return doObjectIndirect(cachedSelf, varargs, kwargs);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", guards = {"self == cachedSelf"})
        Object doObjectDirectType(@SuppressWarnings("unused") PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs,
                        @Cached("self") PythonBuiltinClassType cachedSelf) {
            return doObjectIndirectType(cachedSelf, varargs, kwargs);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()", //
                        guards = {"getInstanceShape(self) == cachedInstanceShape", "!self.needsNativeAllocation()"}, //
                        replaces = "doObjectDirect")
        Object doObjectCachedInstanceShape(@SuppressWarnings("unused") PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Cached("getInstanceShape(self)") Shape cachedInstanceShape) {
            if (varargs.length > 0 || kwargs.length > 0) {
                // TODO: tfel: this should throw an error only if init isn't overridden
            }
            return factory().createPythonObject(self, cachedInstanceShape);
        }

        @Specialization(guards = "!self.needsNativeAllocation()", replaces = "doObjectCachedInstanceShape")
        Object doObjectIndirect(PythonManagedClass self, Object[] varargs, PKeyword[] kwargs) {
            return doObjectCachedInstanceShape(self, varargs, kwargs, getInstanceShape(self));
        }

        @Specialization(replaces = "doObjectDirectType")
        Object doObjectIndirectType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs) {
            if (varargs.length > 0 || kwargs.length > 0) {
                // TODO: tfel: this should throw an error only if init isn't overridden
            }
            return factory().createPythonObject(self, self.getInstanceShape());
        }

        @Specialization(guards = "self.needsNativeAllocation()")
        Object doNativeObjectIndirect(PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Cached("create()") GetMroNode getMroNode) {
            if (varargs.length > 0 || kwargs.length > 0) {
                // TODO: tfel: this should throw an error only if init isn't overridden
            }
            PythonNativeClass nativeBaseClass = findFirstNativeBaseClass(getMroNode.execute(self));
            return callNativeGenericNewNode(nativeBaseClass, varargs, kwargs);
        }

        @Specialization
        Object doNativeObjectIndirect(PythonNativeClass self, Object[] varargs, PKeyword[] kwargs) {
            if (varargs.length > 0 || kwargs.length > 0) {
                // TODO: tfel: this should throw an error only if init isn't overridden
            }
            return callNativeGenericNewNode(self, varargs, kwargs);
        }

        private static PythonNativeClass findFirstNativeBaseClass(PythonAbstractClass[] methodResolutionOrder) {
            for (PythonAbstractClass cls : methodResolutionOrder) {
                if (PGuards.isNativeClass(cls)) {
                    return PythonNativeClass.cast(cls);
                }
            }
            throw new IllegalStateException("class needs native allocation but has not native base class");
        }

        private Object callNativeGenericNewNode(PythonNativeClass self, Object[] varargs, PKeyword[] kwargs) {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(PCallCapiFunction.create());
            }
            if (toSulongNodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNodes = new CExtNodes.ToSulongNode[4];
                for (int i = 0; i < toSulongNodes.length; i++) {
                    toSulongNodes[i] = insert(CExtNodesFactory.ToSulongNodeGen.create());
                }
            }
            if (asPythonObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPythonObjectNode = insert(CExtNodesFactory.AsPythonObjectNodeGen.create());
            }
            PKeyword[] kwarr = kwargs.length > 0 ? kwargs : null;
            PTuple targs = factory().createTuple(varargs);
            PDict dkwargs = factory().createDict(kwarr);
            return asPythonObjectNode.execute(
                            callCapiFunction.call(FUN_PY_OBJECT_GENERIC_NEW, toSulongNodes[0].execute(self), toSulongNodes[1].execute(self), toSulongNodes[2].execute(targs),
                                            toSulongNodes[3].execute(dkwargs)));
        }

        protected Shape getInstanceShape(LazyPythonClass clazz) {
            if (getInstanceShapeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getInstanceShapeNode = insert(TypeNodes.GetInstanceShape.create());
            }
            return getInstanceShapeNode.execute(clazz);
        }

        protected static Class<? extends LazyPythonClass> getJavaClass(Object arg) {
            return ((LazyPythonClass) arg).getClass();
        }
    }

    // range(stop)
    // range(start, stop[, step])
    @Builtin(name = RANGE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PRange)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RangeNode extends PythonQuaternaryBuiltinNode {

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
                if (stop instanceof Integer) {
                    intStop = (int) stop;
                } else if (stop instanceof Long) {
                    intStop = ((Long) (stop)).intValue();
                } else {
                    intStop = ((PInt) stop).intValue();
                }

                if (start instanceof PNone) {
                    return factory().createRange(intStop);
                }

                if (isNumber(start)) {
                    int intStart = 0;
                    if (start instanceof Integer) {
                        intStart = (int) start;
                    } else if (start instanceof Long) {
                        intStart = ((Long) (start)).intValue();
                    } else {
                        intStart = ((PInt) start).intValue();
                    }

                    if (step instanceof PNone) {
                        return factory().createRange(intStart, intStop);
                    }

                    if (isNumber(step)) {
                        int intStep = 0;
                        if (step instanceof Integer) {
                            intStep = (int) step;
                        } else if (step instanceof Long) {
                            intStep = ((Long) (step)).intValue();
                        } else {
                            intStep = ((PInt) step).intValue();
                        }

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
        protected PSet constructSet(VirtualFrame frame, LazyPythonClass cls, Object value,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            return constructSetNode.execute(frame, cls, value);
        }

        @Fallback
        public PSet listObject(@SuppressWarnings("unused") Object cls, Object arg) {
            throw raise(PythonBuiltinClassType.TypeError, "set does not support iterable object %s", arg);
        }
    }

    // str(object='')
    // str(object=b'', encoding='utf-8', errors='strict')
    @Builtin(name = STR, minNumOfPositionalArgs = 1, parameterNames = {"cls", "object", "encoding", "errors"}, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PString)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonBuiltinNode {
        @Child private LookupAndCallTernaryNode callDecodeNode;

        private final IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ConditionProfile isStringProfile;
        @CompilationFinal private ConditionProfile isPStringProfile;

        public abstract Object executeWith(VirtualFrame frame, Object strClass, Object arg, Object encoding, Object errors);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isNoValue(arg)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object strNoArgs(LazyPythonClass strClass, PNone arg, PNone encoding, PNone errors) {
            return asPString(strClass, "");
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object str(LazyPythonClass strClass, double arg, PNone encoding, PNone errors) {
            return asPString(strClass, PFloat.doubleToString(arg));
        }

        @Specialization(guards = {"!isNoValue(obj)", "isNoValue(encoding)", "isNoValue(errors)"})
        public Object strOneArg(VirtualFrame frame, LazyPythonClass strClass, Object obj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode callNode) {
            Object result = callNode.executeObject(frame, obj);
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (getIsPStringProfile().profile(result instanceof PString)) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "__str__ returned non-string (type %p)", result);
        }

        @Specialization(guards = {"!isBytes(obj)", "!isMemoryView(obj)", "!isNoValue(encoding)"})
        public Object strNonBytesArgAndEncodingArg(@SuppressWarnings("unused") LazyPythonClass strClass, Object obj, @SuppressWarnings("unused") Object encoding,
                        @SuppressWarnings("unused") Object errors) {
            throw raise(PythonErrorType.TypeError, "decoding to str: need a bytes-like object, %p found", obj);
        }

        @Specialization(guards = "!isNoValue(encoding)")
        public Object doBytesLike(VirtualFrame frame, LazyPythonClass strClass, PIBytesLike obj, Object encoding, Object errors) {
            Object result = getCallDecodeNode().execute(frame, obj, encoding, errors);
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            } else if (getIsPStringProfile().profile(result instanceof PString)) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "%p.decode returned non-string (type %p)", obj, result);
        }

        @Specialization(guards = "!isNoValue(encoding)")
        public Object doMemoryView(VirtualFrame frame, LazyPythonClass strClass, PMemoryView obj, Object encoding, Object errors,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytes) {
            Object result = callToBytes.executeObject(frame, obj);
            if (isBytesProfile.profile(result instanceof PBytes)) {
                return doBytesLike(frame, strClass, (PBytes) result, encoding, errors);
            }
            throw raise(PythonErrorType.TypeError, "%p.tobytes returned non-bytes object (type %p)", obj, result);
        }

        private Object asPString(LazyPythonClass cls, String str) {
            if (isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PString)) {
                return str;
            } else {
                return factory().createString(cls, str);
            }
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

        @Specialization(guards = "!isNativeClass(cls)")
        protected PTuple constructTuple(VirtualFrame frame, LazyPythonClass cls, Object iterable,
                        @Cached("create()") TupleNodes.ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(frame, cls, iterable);
        }

        // delegate to tuple_subtype_new(PyTypeObject *type, PyObject *x)
        @Specialization(guards = "isSubtypeOfTuple(frame, isSubtype, cls)", limit = "1")
        Object doNative(@SuppressWarnings("unused") VirtualFrame frame, PythonNativeClass cls, Object iterable,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.TupleSubtypeNew subtypeNew) {
            return subtypeNew.call(cls, iterable);
        }

        protected static boolean isSubtypeOfTuple(VirtualFrame frame, IsSubtypeNode isSubtypeNode, PythonNativeClass cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PTuple);
        }

        @Fallback
        public PTuple tupleObject(Object cls, @SuppressWarnings("unused") Object arg) {
            throw raise(PythonBuiltinClassType.TypeError, "'cls' is not a type object (%p)", cls);
        }
    }

    // zip(*iterables)
    @Builtin(name = ZIP, minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PZip)
    @GenerateNodeFactory
    public abstract static class ZipNode extends PythonBuiltinNode {
        @Specialization
        PZip zip(VirtualFrame frame, LazyPythonClass cls, Object[] args,
                        @Cached("create()") GetIteratorNode getIterator) {
            Object[] iterables = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object item = args[i];
                // TODO: check whether the argument supports iteration (has __next__ and __iter__)
                iterables[i] = getIterator.executeWith(frame, item);
            }
            return factory().createZip(cls, iterables);
        }
    }

    // function(code, globals[, name[, argdefs[, closure]]])
    @Builtin(name = "function", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = PythonBuiltinClassType.PFunction, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {
        @Child private GetNameNode getNameNode;

        @Specialization
        public PFunction function(LazyPythonClass cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, @SuppressWarnings("unused") PNone closure) {
            return factory().createFunction(name, getTypeName(cls), code.getRootCallTarget(), globals, null);
        }

        @Specialization
        public PFunction function(LazyPythonClass cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction(name, getTypeName(cls), code.getRootCallTarget(), globals, (PCell[]) getObjectArrayNode.execute(closure));
        }

        @Specialization
        public PFunction function(LazyPythonClass cls, PCode code, PDict globals, String name, PTuple defaultArgs, @SuppressWarnings("unused") PNone closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, getTypeName(cls), code.getRootCallTarget(), globals, getObjectArrayNode.execute(defaultArgs), null, null);
        }

        @Specialization
        public PFunction function(LazyPythonClass cls, PCode code, PDict globals, String name, PTuple defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, getTypeName(cls), code.getRootCallTarget(), globals, getObjectArrayNode.execute(defaultArgs), null, (PCell[]) getObjectArrayNode.execute(closure));
        }

        @Fallback
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object code, Object globals, Object name, Object defaultArgs, Object closure) {
            throw raise(TypeError, "function construction not supported for (%p, %p, %p, %p, %p, %p)", cls, code, globals, name, defaultArgs, closure);
        }

        private String getTypeName(Object typeObj) {
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getNameNode.execute(typeObj);
        }
    }

    // builtin-function(method-def, self, module)
    @Builtin(name = "method_descriptor", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = PythonBuiltinClassType.PBuiltinFunction, isPublic = false)
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
    @Builtin(name = TYPE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonClass)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ReportPolymorphism
    public abstract static class TypeNode extends PythonBuiltinNode {
        private static final long SIZEOF_PY_OBJECT_PTR = Long.BYTES;

        @Child private ReadAttributeFromObjectNode readAttrNode;
        @Child private SetAttributeNode.Dynamic writeAttrNode;
        @Child private GetAnyAttributeNode getAttrNode;
        @Child private CastToIndexNode castToInt;
        @Child private CastToListNode castToList;
        @Child private CastToStringNode castToStringNode;
        @Child private SequenceStorageNodes.LenNode slotLenNode;
        @Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Child private SequenceStorageNodes.AppendNode appendNode;
        @Child private HashingStorageNodes.ContainsKeyNode containsKeyNode;
        @Child private HashingStorageNodes.GetItemNode getDictItemNode;
        @Child private CExtNodes.PCallCapiFunction callAddNativeSlotsNode;
        @Child private CExtNodes.ToSulongNode toSulongNode;
        @Child private ReadCallerFrameNode readCallerFrameNode;
        @Child private GetMroNode getMroNode;
        @Child private IsSubtypeNode isSubtypeNode;
        @Child private GetObjectArrayNode getObjectArrayNode;

        protected abstract Object execute(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        @Specialization(guards = {"isNoValue(bases)", "isNoValue(dict)"})
        @SuppressWarnings("unused")
        Object type(Object cls, Object obj, PNone bases, PNone dict, PKeyword[] kwds,
                        @Cached("create()") GetClassNode getClass) {
            return getClass.execute(obj);
        }

        @Specialization
        Object type(VirtualFrame frame, LazyPythonClass cls, String name, PTuple bases, PDict namespace, PKeyword[] kwds,
                        @Cached GetLazyClassNode getMetaclassNode,
                        @Cached("create(__NEW__)") LookupInheritedAttributeNode getNewFuncNode,
                        @Cached("create(__INIT_SUBCLASS__)") GetAttributeNode getInitSubclassNode,
                        @Cached CallNode callInitSubclassNode,
                        @Cached CallNode callNewFuncNode) {
            // Determine the proper metatype to deal with this
            LazyPythonClass metaclass = calculate_metaclass(frame, cls, bases, getMetaclassNode);
            if (metaclass != cls) {
                Object newFunc = getNewFuncNode.execute(metaclass);
                if (newFunc instanceof PBuiltinFunction && (((PBuiltinFunction) newFunc).getFunctionRootNode() == getRootNode())) {
                    // the new metaclass has the same __new__ function as we are in, continue
                } else {
                    // Pass it to the winner
                    callNewFuncNode.execute(frame, newFunc, new Object[]{metaclass, name, bases, namespace}, kwds);
                }
            }

            try {
                PythonClass newType = typeMetaclass(frame, name, bases, namespace, metaclass);

                // TODO: Call __set_name__ on all descriptors in a newly generated type

                // Call __init_subclass__ on the parent of a newly generated type
                SuperObject superObject = factory().createSuperObject(PythonBuiltinClassType.Super);
                superObject.init(newType, newType, newType);
                callInitSubclassNode.execute(frame, getInitSubclassNode.executeObject(frame, superObject), new Object[0], kwds);

                // set '__module__' attribute
                Object moduleAttr = ensureReadAttrNode().execute(newType, __MODULE__);
                if (moduleAttr == PNone.NO_VALUE) {
                    PFrame callerFrame = getReadCallerFrameNode().executeWith(frame, 0);
                    PythonObject globals = callerFrame.getGlobals();
                    if (globals != null) {
                        String moduleName = getModuleNameFromGlobals(frame, globals);
                        if (moduleName != null) {
                            ensureWriteAttrNode().execute(frame, newType, __MODULE__, moduleName);
                        }
                    }
                }

                return newType;
            } catch (PException e) {
                throw e;
            }
        }

        private String getModuleNameFromGlobals(VirtualFrame frame, PythonObject globals) {
            Object nameAttr;
            if (globals instanceof PythonModule) {
                nameAttr = ensureReadAttrNode().execute(globals, __NAME__);
            } else if (globals instanceof PDict) {
                nameAttr = ensureGetDictItemNode().execute(frame, ((PDict) globals).getDictStorage(), __NAME__);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("invalid globals object");
            }
            return ensureCastToStringNode().execute(frame, nameAttr);
        }

        @SuppressWarnings("try")
        private PythonClass typeMetaclass(VirtualFrame frame, String name, PTuple bases, PDict namespace, LazyPythonClass metaclass) {

            Object[] array = ensureGetObjectArrayNode().execute(bases);

            PythonAbstractClass[] basesArray;
            if (array.length == 0) {
                // Adjust for empty tuple bases
                basesArray = new PythonAbstractClass[]{getCore().lookupType(PythonBuiltinClassType.PythonObject)};
            } else {
                basesArray = new PythonAbstractClass[array.length];
                for (int i = 0; i < array.length; i++) {
                    // TODO: deal with non-class bases
                    if (!(array[i] instanceof PythonAbstractClass)) {
                        throw raise(NotImplementedError, "creating a class with non-class bases");
                    } else {
                        basesArray[i] = (PythonAbstractClass) array[i];
                    }
                }
            }
            assert metaclass != null;

            if (name.indexOf('\0') != -1) {
                throw raise(ValueError, "type name must not contain null characters");
            }
            PythonClass pythonClass = factory().createPythonClass(metaclass, name, basesArray);

            // copy the dictionary slots over, as CPython does through PyDict_Copy
            // Also check for a __slots__ sequence variable in dict
            Object slots = null;
            for (DictEntry entry : namespace.entries()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (__SLOTS__.equals(key)) {
                    slots = value;
                } else if (SpecialMethodNames.__NEW__.equals(key)) {
                    // TODO: see CPython: if it's a plain function, make it a
                    // static function

                    // tfel: this requires a little bit of refactoring on our
                    // side that I don't want to do now
                    pythonClass.setAttribute(key, value);
                } else if (SpecialMethodNames.__INIT_SUBCLASS__.equals(key) ||
                                SpecialMethodNames.__CLASS_GETITEM__.equals(key)) {
                    // see CPython: Special-case __init_subclass__ and
                    // __class_getitem__: if they are plain functions, make them
                    // classmethods
                    if (value instanceof PFunction) {
                        pythonClass.setAttribute(key, factory().createClassmethod(value));
                    } else {
                        pythonClass.setAttribute(key, value);
                    }
                } else {
                    pythonClass.setAttribute(key, value);
                }
            }

            boolean addDict = false;
            if (slots == null) {
                // takes care of checking if we may_add_dict and adds it if needed
                addDictIfNative(frame, pythonClass);
                // TODO: tfel - also deal with weaklistoffset
            } else {
                // have slots

                // Make it into a list
                SequenceStorage slotList;
                if (slots instanceof String) {
                    slotList = factory().createList(new Object[]{slots}).getSequenceStorage();
                } else {
                    slotList = getCastToListNode().execute(frame, slots).getSequenceStorage();
                }
                int slotlen = getListLenNode().execute(slotList);
                // TODO: tfel - check if slots are allowed. They are not if the base class is var
                // sized

                for (int i = 0; i < slotlen; i++) {
                    String slotName;
                    Object element = getSlotItemNode().execute(frame, slotList, i);
                    // Check valid slot name
                    if (element instanceof String) {
                        slotName = (String) element;
                    } else {
                        throw raise(TypeError, "__slots__ items must be strings, not '%p'", element);
                    }
                    if (__DICT__.equals(slotName)) {
                        // check that the native base does not already have tp_dictoffset
                        if (addDictIfNative(frame, pythonClass)) {
                            throw raise(TypeError, "__dict__ slot disallowed: we already got one");
                        }
                        addDict = true;
                    } else {
                        // TODO: check for __weakref__
                        // TODO avoid if native slots are inherited
                        HiddenKey hiddenSlotKey = new HiddenKey(slotName);
                        HiddenKeyDescriptor slotDesc = factory().createHiddenKeyDescriptor(hiddenSlotKey, pythonClass);
                        pythonClass.setAttribute(slotName, slotDesc);
                    }
                    // Make slots into a tuple
                }
                PythonContext context = getContextRef().get();
                PException caughtException = ForeignCallContext.enter(frame, context, this);
                try {
                    PTuple newSlots = copySlots(name, slotList, slotlen, addDict, false, namespace);
                    pythonClass.setAttribute(__SLOTS__, newSlots);
                    if (basesArray.length > 1) {
                        // TODO: tfel - check if secondary bases provide weakref or dict when we
                        // don't already have one
                    }

                    // add native slot descriptors
                    if (pythonClass.needsNativeAllocation()) {
                        addNativeSlots(pythonClass, newSlots);
                    }
                } finally {
                    ForeignCallContext.exit(frame, context, caughtException);
                }
            }

            return pythonClass;
        }

        @TruffleBoundary
        private PTuple copySlots(String className, SequenceStorage slotList, int slotlen, boolean add_dict, boolean add_weak, PDict namespace) {
            SequenceStorage newSlots = new ObjectSequenceStorage(slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak));
            int j = 0;
            for (int i = 0; i < slotlen; i++) {
                // the cast is ensured by the previous loop
                // n.b.: passing the null frame here is fine, since the storage and index are known
                // types
                String slotName = (String) getSlotItemNode().execute(null, slotList, i);
                if ((add_dict && __DICT__.equals(slotName)) || (add_weak && __WEAKREF__.equals(slotName))) {
                    continue;
                }

                slotName = mangle(className, slotName);
                if (slotName == null) {
                    return null;
                }

                setSlotItemNode().execute(newSlots, slotName, NoGeneralizationNode.DEFAULT);
                // Passing 'null' frame is fine because the caller already transfers the exception
                // state to the context.
                if (getContainsKeyNode().execute(null, namespace.getDictStorage(), slotName)) {
                    throw raise(PythonBuiltinClassType.ValueError, "%s in __slots__ conflicts with class variable", slotName);
                }
                j++;
            }
            assert j == slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak);

            // sort newSlots
            Arrays.sort(newSlots.getInternalArray());

            return factory().createTuple(newSlots);

        }

        private String mangle(String privateobj, String ident) {
            // Name mangling: __private becomes _classname__private. This is independent from how
            // the name is used.
            int nlen, plen, ipriv;
            if (privateobj == null || ident.charAt(0) != '_' || ident.charAt(1) != '_') {
                return ident;
            }
            nlen = ident.length();
            plen = privateobj.length();

            // Don't mangle __whatever__ or names with dots.
            if ((ident.charAt(nlen - 1) == '_' && ident.charAt(nlen - 2) == '_') || ident.indexOf('.') != -1) {
                return ident;
            }

            // Strip leading underscores from class name
            ipriv = 0;
            while (privateobj.charAt(ipriv) == '_') {
                ipriv++;
            }

            // Don't mangle if class is just underscores
            if (ipriv == plen) {
                return ident;
            }
            plen -= ipriv;

            if ((long) plen + nlen >= Integer.MAX_VALUE) {
                throw raise(PythonBuiltinClassType.OverflowError, "private identifier too large to be mangled");
            }

            /* ident = "_" + priv[ipriv:] + ident # i.e. 1+plen+nlen bytes */
            return "_" + privateobj.substring(ipriv) + ident;
        }

        private HashingStorageNodes.ContainsKeyNode getContainsKeyNode() {
            if (containsKeyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                containsKeyNode = insert(HashingStorageNodes.ContainsKeyNode.create());
            }
            return containsKeyNode;
        }

        private SequenceStorageNodes.GetItemNode getSlotItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getItemNode;
        }

        private SequenceStorageNodes.AppendNode setSlotItemNode() {
            if (appendNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendNode = insert(SequenceStorageNodes.AppendNode.create());
            }
            return appendNode;
        }

        private SequenceStorageNodes.LenNode getListLenNode() {
            if (slotLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slotLenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return slotLenNode;
        }

        private ReadCallerFrameNode getReadCallerFrameNode() {
            if (readCallerFrameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readCallerFrameNode = insert(ReadCallerFrameNode.create());
            }
            return readCallerFrameNode;
        }

        private void addNativeSlots(PythonManagedClass pythonClass, PTuple slots) {
            if (callAddNativeSlotsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callAddNativeSlotsNode = insert(CExtNodes.PCallCapiFunction.create());
            }
            callAddNativeSlotsNode.call(FUN_ADD_NATIVE_SLOTS, toSulongNode.execute(pythonClass), toSulongNode.execute(slots));
        }

        private CastToListNode getCastToListNode() {
            if (castToList == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToList = insert(CastToListNode.create());
            }
            return castToList;
        }

        private boolean addDictIfNative(VirtualFrame frame, PythonManagedClass pythonClass) {
            boolean addedNewDict = false;
            if (pythonClass.needsNativeAllocation()) {
                for (Object cls : getMro(pythonClass)) {
                    if (PGuards.isNativeClass(cls)) {
                        // Use GetAnyAttributeNode since these are get-set-descriptors
                        long dictoffset = ensureCastToIntNode().execute(frame, ensureGetAttributeNode().executeObject(frame, cls, __DICTOFFSET__));
                        long basicsize = ensureCastToIntNode().execute(frame, ensureGetAttributeNode().executeObject(frame, cls, __BASICSIZE__));
                        long itemsize = ensureCastToIntNode().execute(frame, ensureGetAttributeNode().executeObject(frame, cls, __ITEMSIZE__));
                        if (dictoffset == 0) {
                            addedNewDict = true;
                            // add_dict
                            if (itemsize != 0) {
                                dictoffset = -SIZEOF_PY_OBJECT_PTR;
                            } else {
                                dictoffset = basicsize;
                                basicsize += SIZEOF_PY_OBJECT_PTR;
                            }
                        }
                        ensureWriteAttrNode().execute(frame, pythonClass, __DICTOFFSET__, dictoffset);
                        ensureWriteAttrNode().execute(frame, pythonClass, __BASICSIZE__, basicsize);
                        ensureWriteAttrNode().execute(frame, pythonClass, __ITEMSIZE__, itemsize);
                        break;
                    }
                }
            }
            return addedNewDict;
        }

        private PythonAbstractClass[] getMro(PythonAbstractClass pythonClass) {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroNode.create());
            }
            return getMroNode.execute(pythonClass);
        }

        private LazyPythonClass calculate_metaclass(VirtualFrame frame, LazyPythonClass cls, PTuple bases, GetLazyClassNode getMetaclassNode) {
            LazyPythonClass winner = cls;
            for (Object base : ensureGetObjectArrayNode().execute(bases)) {
                LazyPythonClass typ = getMetaclassNode.execute(base);
                if (isSubType(frame, winner, typ)) {
                    continue;
                } else if (isSubType(frame, typ, winner)) {
                    winner = typ;
                    continue;
                }
                throw raise(TypeError, "metaclass conflict: the metaclass of a derived class must be " +
                                "a (non-strict) subclass of the metaclasses of all its bases");
            }
            return winner;
        }

        protected boolean isSubType(VirtualFrame frame, LazyPythonClass subclass, LazyPythonClass superclass) {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode.execute(frame, subclass, superclass);
        }

        protected static TypeNode create() {
            return BuiltinConstructorsFactory.TypeNodeFactory.create(null);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        Object typeGeneric(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
                        @Cached("create()") TypeNode nextTypeNode) {
            if (PGuards.isNoValue(bases) && !PGuards.isNoValue(dict) || !PGuards.isNoValue(bases) && PGuards.isNoValue(dict)) {
                throw raise(TypeError, "type() takes 1 or 3 arguments");
            } else if (!(name instanceof String || name instanceof PString)) {
                throw raise(TypeError, "type() argument 1 must be string, not %p", name);
            } else if (!(bases instanceof PTuple)) {
                throw raise(TypeError, "type() argument 2 must be tuple, not %p", name);
            } else if (!(dict instanceof PDict)) {
                throw raise(TypeError, "type() argument 3 must be dict, not %p", name);
            } else if (!(cls instanceof LazyPythonClass)) {
                // TODO: this is actually allowed, deal with it
                throw raise(NotImplementedError, "creating a class with non-class metaclass");
            }
            return nextTypeNode.execute(frame, cls, name, bases, dict, kwds);
        }

        private HashingStorageNodes.GetItemNode ensureGetDictItemNode() {
            if (getDictItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDictItemNode = insert(HashingStorageNodes.GetItemNode.create());
            }
            return getDictItemNode;
        }

        private ReadAttributeFromObjectNode ensureReadAttrNode() {
            if (readAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttrNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttrNode;
        }

        private GetAnyAttributeNode ensureGetAttributeNode() {
            if (getAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getAttrNode = insert(GetAnyAttributeNode.create());
            }
            return getAttrNode;
        }

        private SetAttributeNode.Dynamic ensureWriteAttrNode() {
            if (writeAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeAttrNode = insert(SetAttributeNode.Dynamic.create());
            }
            return writeAttrNode;
        }

        private CastToIndexNode ensureCastToIntNode() {
            if (castToInt == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToInt = insert(CastToIndexNode.create());
            }
            return castToInt;
        }

        private CastToStringNode ensureCastToStringNode() {
            if (castToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToStringNode = insert(CastToStringNode.create());
            }
            return castToStringNode;
        }

        private GetObjectArrayNode ensureGetObjectArrayNode() {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode;
        }
    }

    @Builtin(name = MODULE, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonModule, isPublic = false)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ModuleNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doType(PythonBuiltinClass self, Object[] varargs, PKeyword[] kwargs) {
            return factory().createPythonModule(self.getType());
        }

        @Specialization(guards = "!isPythonBuiltinClass(self)")
        @SuppressWarnings("unused")
        Object doManaged(PythonManagedClass self, Object[] varargs, PKeyword[] kwargs) {
            return factory().createPythonModule(self);
        }

        @Specialization
        @SuppressWarnings("unused")
        Object doType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs) {
            return factory().createPythonModule(self);
        }

        @Specialization(guards = "isTypeNode.execute(self)")
        @SuppressWarnings("unused")
        Object doNative(PythonAbstractNativeObject self, Object[] varargs, PKeyword[] kwargs,
                        @Cached IsTypeNode isTypeNode) {
            return factory().createPythonModule(self);
        }
    }

    @Builtin(name = "NotImplementedType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PNotImplemented, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NotImplementedTypeNode extends PythonBuiltinNode {
        protected PythonBuiltinClass getNotImplementedClass() {
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

    @Builtin(name = "ellipsis", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PEllipsis, isPublic = false)
    @GenerateNodeFactory
    public abstract static class EllipsisTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PEllipsis call(Object cls, Object args, Object kwds) {
            return PEllipsis.INSTANCE;
        }
    }

    @Builtin(name = "NoneType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PNone, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NoneTypeNode extends PythonBuiltinNode {
        protected PythonBuiltinClass getNoneClass() {
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

    @Builtin(name = "dict_keys", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeysView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_keys' instances");
        }
    }

    @Builtin(name = "dict_keysiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeysIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_keysiterator' instances");
        }
    }

    @Builtin(name = "dict_values", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValuesView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_values' instances");
        }
    }

    @Builtin(name = "dict_valuesiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValuesIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_valuesiterator' instances");
        }
    }

    @Builtin(name = "dict_items", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemsView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_items' instances");
        }
    }

    @Builtin(name = "dict_itemsiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemsIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'dict_itemsiterator' instances");
        }
    }

    @Builtin(name = "iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = {PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PArrayIterator}, isPublic = false)
    @GenerateNodeFactory
    public abstract static class IteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'iterator' instances");
        }
    }

    @Builtin(name = "callable_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PSentinelIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CallableIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'callable_iterator' instances");
        }
    }

    @Builtin(name = "foreign_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PForeignArrayIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ForeignIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'foreign_iterator' instances");
        }
    }

    @Builtin(name = "generator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PGenerator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class GeneratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object generator(Object args, Object kwargs) {
            throw raise(TypeError, "cannot create 'generator' instances");
        }
    }

    @Builtin(name = "method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PMethod, isPublic = false)
    @GenerateNodeFactory
    public abstract static class MethodTypeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object method(LazyPythonClass cls, PFunction func, Object self) {
            return factory().createMethod(cls, self, func);
        }

        @Specialization
        Object methodBuiltin(@SuppressWarnings("unused") LazyPythonClass cls, PBuiltinFunction func, Object self) {
            return factory().createMethod(self, func);
        }

        @Specialization
        Object methodGeneric(VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass cls, Object func, Object self,
                        @CachedLibrary(limit = "3") PythonObjectLibrary dataModelLibrary) {
            PythonContext context = getContextRef().get();
            PException caughtException = IndirectCallContext.enter(frame, context, this);
            try {
                if (dataModelLibrary.isCallable(func)) {
                    return factory().createMethod(self, func);
                } else {
                    throw raise(TypeError, "first argument must be callable");
                }
            } finally {
                IndirectCallContext.exit(frame, context, caughtException);
            }
        }
    }

    @Builtin(name = "builtin_function_or_method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuiltinMethod, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodTypeNode extends PythonBuiltinNode {
        @Specialization
        Object method(LazyPythonClass cls, Object self, PBuiltinFunction func) {
            return factory().createBuiltinMethod(cls, self, func);
        }
    }

    @Builtin(name = "frame", constructsClass = PythonBuiltinClassType.PFrame, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FrameTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of frame type");
        }
    }

    @Builtin(name = "traceback", constructsClass = PythonBuiltinClassType.PTraceback, isPublic = false)
    @GenerateNodeFactory
    public abstract static class TracebackTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of traceback type");
        }
    }

    @Builtin(name = "code", constructsClass = PythonBuiltinClassType.PCode, isPublic = false, minNumOfPositionalArgs = 14, maxNumOfPositionalArgs = 16)
    @GenerateNodeFactory
    public abstract static class CodeTypeNode extends PythonBuiltinNode {

        // limit is 2 because we expect PBytes or String
        @Specialization(guards = {"codestringBufferLib.isBuffer(codestring)", "lnotabBufferLib.isBuffer(lnotab)"}, limit = "2", rewriteOn = UnsupportedMessageException.class)
        Object call(VirtualFrame frame, LazyPythonClass cls, int argcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        Object codestring, PTuple constants, PTuple names,
                        PTuple varnames, Object filename, Object name,
                        int firstlineno, Object lnotab,
                        PTuple freevars, PTuple cellvars,
                        @CachedLibrary("codestring") PythonObjectLibrary codestringBufferLib,
                        @CachedLibrary("lnotab") PythonObjectLibrary lnotabBufferLib,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached GetObjectArrayNode getObjectArrayNode) throws UnsupportedMessageException {
            byte[] codeBytes = codestringBufferLib.getBufferBytes(codestring);
            byte[] lnotabBytes = lnotabBufferLib.getBufferBytes(lnotab);

            Object[] constantsArr = getObjectArrayNode.execute(constants);
            Object[] namesArr = getObjectArrayNode.execute(names);
            Object[] varnamesArr = getObjectArrayNode.execute(varnames);
            Object[] freevarsArr = getObjectArrayNode.execute(freevars);
            Object[] cellcarsArr = getObjectArrayNode.execute(cellvars);

            return createCodeNode.execute(frame, cls, argcount, kwonlyargcount,
                            nlocals, stacksize, flags,
                            codeBytes, constantsArr, namesArr,
                            varnamesArr, freevarsArr, cellcarsArr,
                            getStringArg(filename), getStringArg(name), firstlineno,
                            lnotabBytes);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object call(Object cls, Object argcount, Object kwonlyargcount,
                        Object nlocals, Object stacksize, Object flags,
                        Object codestring, Object constants, Object names,
                        Object varnames, Object filename, Object name,
                        Object firstlineno, Object lnotab,
                        Object freevars, Object cellvars) {
            throw raise(SystemError, "bad argument to internal function");
        }

        private String getStringArg(Object arg) {
            if (arg instanceof String) {
                return (String) arg;
            } else if (arg instanceof PString) {
                return ((PString) arg).getValue();
            } else {
                throw raise(SystemError, "bad argument to internal function");
            }
        }

        @TruffleBoundary
        private static byte[] toBytes(String data) {
            return data.getBytes();
        }
    }

    @Builtin(name = "cell", constructsClass = PythonBuiltinClassType.PCell, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CellTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw new RuntimeException("cannot call constructor of cell type");
        }
    }

    @Builtin(name = "BaseException", constructsClass = PythonBuiltinClassType.PBaseException, isPublic = true, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object call(LazyPythonClass cls, Object[] varargs, PKeyword[] kwargs) {
            return factory().createBaseException(cls, factory().createTuple(varargs));
        }
    }

    @Builtin(name = "mappingproxy", constructsClass = PythonBuiltinClassType.PMappingproxy, isPublic = false, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MappingproxyNode extends PythonBuiltinNode {
        @Specialization
        Object doMapping(LazyPythonClass klass, PHashingCollection obj) {
            return factory().createMappingproxy(klass, obj.getDictStorage());
        }

        @Specialization(guards = {"isSequence(frame, obj, lib)", "!isBuiltinMapping(obj)"})
        Object doMapping(VirtualFrame frame, LazyPythonClass klass, PythonObject obj,
                        @Cached("create()") HashingStorageNodes.InitNode initNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            return factory().createMappingproxy(klass, initNode.execute(frame, obj, PKeyword.EMPTY_KEYWORDS));
        }

        @Specialization(guards = "isNoValue(none)")
        @SuppressWarnings("unused")
        Object doMissing(LazyPythonClass klass, PNone none) {
            throw raise(TypeError, "mappingproxy() missing required argument 'mapping' (pos 1)");
        }

        @Specialization(guards = {"!isSequence(frame, obj, lib)", "!isNoValue(obj)"})
        Object doInvalid(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass klass, Object obj,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            throw raise(TypeError, "mappingproxy() argument must be a mapping, not %p", obj);
        }

        protected boolean isBuiltinMapping(Object o) {
            return o instanceof PHashingCollection;
        }

        protected boolean isSequence(VirtualFrame frame, Object o, PythonObjectLibrary library) {
            PythonContext context = getContextRef().get();
            PException caughtException = IndirectCallContext.enter(frame, context, this);
            try {
                return library.isSequence(o);
            } finally {
                IndirectCallContext.exit(frame, context, caughtException);
            }
        }
    }

    @Builtin(name = "getset_descriptor", constructsClass = PythonBuiltinClassType.GetSetDescriptor, isPublic = false, minNumOfPositionalArgs = 1, parameterNames = {"cls", "fget", "fset", "name",
                    "owner"})
    @GenerateNodeFactory
    public abstract static class GetSetDescriptorNode extends PythonBuiltinNode {
        private void denyInstantiationAfterInitialization() {
            if (getCore().isInitialized()) {
                throw raise(TypeError, "cannot create 'getset_descriptor' instances");
            }
        }

        @Specialization(guards = {"!isNoValue(get)", "!isNoValue(set)"})
        @TruffleBoundary
        Object call(@SuppressWarnings("unused") LazyPythonClass getSetClass, Object get, Object set, String name, PythonAbstractClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(get, set, name, owner);
        }

        @Specialization(guards = {"!isNoValue(get)", "isNoValue(set)"})
        @TruffleBoundary
        Object call(@SuppressWarnings("unused") LazyPythonClass getSetClass, Object get, @SuppressWarnings("unused") PNone set, String name, PythonAbstractClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(get, null, name, owner);
        }

        @Specialization(guards = {"isNoValue(get)", "isNoValue(set)"})
        @TruffleBoundary
        @SuppressWarnings("unused")
        Object call(LazyPythonClass getSetClass, PNone get, PNone set, String name, PythonAbstractClass owner) {
            denyInstantiationAfterInitialization();
            return factory().createGetSetDescriptor(null, null, name, owner);
        }
    }

    // slice(stop)
    // slice(start, stop[, step])
    @Builtin(name = "slice", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PSlice)
    @GenerateNodeFactory
    public abstract static class CreateSliceNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(second)", "isNoValue(third)"})
        @SuppressWarnings("unused")
        Object sliceStop(LazyPythonClass cls, int first, PNone second, PNone third) {
            return factory().createSlice(MISSING_INDEX, first, MISSING_INDEX);
        }

        @Specialization(guards = "isNoValue(third)")
        Object sliceStart(@SuppressWarnings("unused") LazyPythonClass cls, int first, int second, @SuppressWarnings("unused") PNone third) {
            return factory().createSlice(first, second, MISSING_INDEX);
        }

        @Specialization
        Object slice(@SuppressWarnings("unused") LazyPythonClass cls, int first, int second, int third) {
            return factory().createSlice(first, second, third);
        }

        @Specialization(guards = "isNoValue(third)")
        Object slice(VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass cls, Object first, Object second, @SuppressWarnings("unused") PNone third,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, first, second, MISSING_INDEX);
        }

        @Specialization(guards = {"isNoValue(second)", "isNoValue(third)"})
        @SuppressWarnings("unused")
        Object slice(VirtualFrame frame, LazyPythonClass cls, Object first, PNone second, PNone third,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, MISSING_INDEX, first, MISSING_INDEX);
        }

        @Specialization(guards = {"!isNoValue(stop)", "!isNoValue(step)"})
        Object slice(VirtualFrame frame, @SuppressWarnings("unused") LazyPythonClass cls, Object start, Object stop, Object step,
                        @Cached("create()") SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, start, stop, step);
        }
    }

    // buffer([iterable])
    @Builtin(name = "buffer", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuffer)
    @GenerateNodeFactory
    public abstract static class BufferNode extends PythonBuiltinNode {
        @Child private LookupInheritedAttributeNode getSetItemNode;

        @Specialization(guards = "isNoValue(readOnly)")
        protected PBuffer construct(LazyPythonClass cls, Object delegate, @SuppressWarnings("unused") PNone readOnly) {
            return factory().createBuffer(cls, delegate, !hasSetItem(delegate));
        }

        @Specialization
        protected PBuffer construct(LazyPythonClass cls, Object delegate, boolean readOnly) {
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
    @Builtin(name = MEMORYVIEW, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PMemoryView)
    @GenerateNodeFactory
    public abstract static class MemoryViewNode extends PythonBuiltinNode {
        @Specialization
        public PMemoryView doGeneric(LazyPythonClass cls, Object value) {
            return factory().createMemoryView(cls, value);
        }
    }

    // super()
    @Builtin(name = SUPER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.Super)
    @GenerateNodeFactory
    public abstract static class SuperInitNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(LazyPythonClass self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object object) {
            return factory().createSuperObject(self);
        }
    }

    @Builtin(name = CLASSMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PClassmethod, doc = "classmethod(function) -> method\n" +
                    "\n" +
                    "Convert a function to be a class method.\n" +
                    "\n" +
                    "A class method receives the class as implicit first argument,\n" +
                    "just like an instance method receives the instance.\n" +
                    "To declare a class method, use this idiom:\n" +
                    "\n" +
                    "  class C:\n" +
                    "      @classmethod\n" +
                    "      def f(cls, arg1, arg2, ...):\n" +
                    "          ...\n" +
                    "\n" +
                    "It can be called either on the class (e.g. C.f()) or on an instance\n" +
                    "(e.g. C().f()).  The instance is ignored except for its class.\n" +
                    "If a class method is called for a derived class, the derived class\n" +
                    "object is passed as the implied first argument.\n" +
                    "\n" +
                    "Class methods are different than C++ or Java static methods.\n" +
                    "If you want those, see the staticmethod builtin.")
    @GenerateNodeFactory
    public abstract static class ClassmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(LazyPythonClass self, @SuppressWarnings("unused") Object callable) {
            return factory().createClassmethod(self);
        }
    }

    @Builtin(name = STATICMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PStaticmethod)
    @GenerateNodeFactory
    public abstract static class StaticmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(LazyPythonClass self, @SuppressWarnings("unused") Object callable) {
            return factory().createStaticmethod(self);
        }
    }
}
