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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_BYTES_SUBTYPE_NEW;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_NEW;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BOOL;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTES;
import static com.oracle.graal.python.nodes.BuiltinNames.J_CLASSMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.J_COMPLEX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_ITEMS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_KEYS;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DICT_VALUES;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ENUMERATE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FLOAT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FROZENSET;
import static com.oracle.graal.python.nodes.BuiltinNames.J_GETSET_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_INSTANCEMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.J_INT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_LIST;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MAP;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MEMBER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MEMORYVIEW;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_OBJECT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_PROPERTY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_RANGE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REVERSED;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SET;
import static com.oracle.graal.python.nodes.BuiltinNames.J_STATICMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.J_STR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_SUPER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_WRAPPER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ZIP;
import static com.oracle.graal.python.nodes.BuiltinNames.T_GETSET_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAMBDA_NAME;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MEMBER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.BuiltinNames.T_WRAPPER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ZIP;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_NOT_BE_ZERO;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUNC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_JOIN;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_SORT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COMPLEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MRO_ENTRIES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUNC__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.DeprecationWarning;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.addExact;
import static com.oracle.graal.python.util.PythonUtils.multiplyExact;
import static com.oracle.graal.python.util.PythonUtils.negateExact;
import static com.oracle.graal.python.util.PythonUtils.objectArrayToTruffleStringArray;
import static com.oracle.graal.python.util.PythonUtils.subtractExact;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CByteArrayWrapper;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PBigRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.property.PProperty;
import com.oracle.graal.python.builtins.objects.range.PBigRange;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.range.RangeNodes;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfIntRangeNodeExact;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.CreateTypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsAcceptableBaseNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.types.PGenericAlias;
import com.oracle.graal.python.lib.CanBeDoubleNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatFromString;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsAnyBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = BuiltinNames.J_BUILTINS, isEager = true)
public final class BuiltinConstructors extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinConstructorsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(T_NOT_IMPLEMENTED, PNotImplemented.NOT_IMPLEMENTED);
    }

    // bytes([source[, encoding[, errors]]])
    @Builtin(name = J_BYTES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "source", "encoding", "errors"}, constructsClass = PythonBuiltinClassType.PBytes)
    @ArgumentClinic(name = "encoding", conversionClass = BytesNodes.ExpectStringNode.class, args = "\"bytes()\"")
    @ArgumentClinic(name = "errors", conversionClass = BytesNodes.ExpectStringNode.class, args = "\"bytes()\"")
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class BytesNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinConstructorsClinicProviders.BytesNodeClinicProviderGen.INSTANCE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(source)")
        static Object doEmpty(Object cls, PNone source, PNone encoding, PNone errors,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CreateBytes createBytes) {
            return createBytes.execute(inliningTarget, cls, PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @Specialization(guards = "!isNoValue(source)")
        @SuppressWarnings("truffle-static-method")
        Object doCallBytes(VirtualFrame frame, Object cls, Object source, PNone encoding, PNone errors,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached InlinedConditionProfile hasBytes,
                        @Cached("create(Bytes)") LookupSpecialMethodSlotNode lookupBytes,
                        @Cached CallUnaryMethodNode callBytes,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached PyBytesCheckNode check,
                        @Shared @Cached BytesNodes.BytesInitNode bytesInitNode,
                        @Shared @Cached CreateBytes createBytes) {
            Object bytesMethod = lookupBytes.execute(frame, getClassNode.execute(inliningTarget, source), source);
            if (hasBytes.profile(inliningTarget, bytesMethod != PNone.NO_VALUE)) {
                Object bytes = callBytes.executeObject(frame, bytesMethod, source);
                if (check.execute(inliningTarget, bytes)) {
                    if (cls == PythonBuiltinClassType.PBytes) {
                        return bytes;
                    } else {
                        return createBytes.execute(inliningTarget, cls, toBytesNode.execute(frame, bytes));
                    }
                } else {
                    throw raise(TypeError, ErrorMessages.RETURNED_NONBYTES, T___BYTES__, bytes);
                }
            }
            return createBytes.execute(inliningTarget, cls, bytesInitNode.execute(frame, inliningTarget, source, encoding, errors));
        }

        @Specialization(guards = {"isNoValue(source) || (!isNoValue(encoding) || !isNoValue(errors))"})
        Object dontCallBytes(VirtualFrame frame, Object cls, Object source, Object encoding, Object errors,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached BytesNodes.BytesInitNode bytesInitNode,
                        @Shared @Cached CreateBytes createBytes) {
            return createBytes.execute(inliningTarget, cls, bytesInitNode.execute(frame, inliningTarget, source, encoding, errors));
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class CreateBytes extends PNodeWithContext {
            abstract Object execute(Node inliningTarget, Object cls, byte[] bytes);

            @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
            static PBytes doManaged(@SuppressWarnings("unused") Node inliningTarget, Object cls, byte[] bytes,
                            @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                            @Cached PythonObjectFactory factory) {
                return factory.createBytes(cls, bytes);
            }

            @Specialization(guards = "needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
            static Object doNative(@SuppressWarnings("unused") Node inliningTarget, Object cls, byte[] bytes,
                            @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                            @Cached PythonToNativeNode toNative,
                            @Cached NativeToPythonNode toPython,
                            @Cached PCallCapiFunction call) {
                CByteArrayWrapper wrapper = new CByteArrayWrapper(bytes);
                try {
                    return toPython.execute(call.call(FUN_BYTES_SUBTYPE_NEW, toNative.execute(cls), wrapper, bytes.length));
                } finally {
                    wrapper.free();
                }
            }
        }
    }

    @Builtin(name = J_BYTEARRAY, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PByteArray)
    @GenerateNodeFactory
    public abstract static class ByteArrayNode extends PythonBuiltinNode {
        @Specialization
        public PByteArray setEmpty(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BytesBuiltins.InitNode
            return factory().createByteArray(cls, PythonUtils.EMPTY_BYTE_ARRAY);
        }
    }

    // complex([real[, imag]])
    @Builtin(name = J_COMPLEX, minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PComplex, parameterNames = {"$cls", "real",
                    "imag"}, doc = "complex(real[, imag]) -> complex number\n\n" +
                                    "Create a complex number from a real part and an optional imaginary part.\n" +
                                    "This is equivalent to (real + imag*1j) where imag defaults to 0.")
    @GenerateNodeFactory
    public abstract static class ComplexNode extends PythonTernaryBuiltinNode {

        @Child private CanBeDoubleNode canBeDoubleNode;
        @Child private PyFloatAsDoubleNode asDoubleNode;
        @Child private LookupAndCallUnaryNode callReprNode;
        @Child private LookupAndCallUnaryNode callComplexNode;
        @Child private WarnNode warnNode;

        private PComplex createComplex(Object cls, double real, double imaginary, Node inliningTarget, InlineIsBuiltinClassProfile isPrimitiveProfile) {
            if (isPrimitiveProfile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PComplex)) {
                return factory().createComplex(real, imaginary);
            }
            return factory().createComplex(cls, real, imaginary);
        }

        private PComplex createComplex(Object cls, PComplex value, Node inliningTarget, InlineIsBuiltinClassProfile isPrimitiveProfile, IsBuiltinObjectProfile isBuiltinObjectProfile) {
            if (isPrimitiveProfile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PComplex)) {
                if (isBuiltinObjectProfile.profileObject(inliningTarget, value, PythonBuiltinClassType.PComplex)) {
                    return value;
                }
                return factory().createComplex(value.getReal(), value.getImag());
            }
            return factory().createComplex(cls, value.getReal(), value.getImag());
        }

        @Specialization(guards = {"isNoValue(real)", "isNoValue(imag)"})
        @SuppressWarnings("unused")
        PComplex complexFromNone(Object cls, PNone real, PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, 0, 0, inliningTarget, isPrimitiveProfile);
        }

        @Specialization
        PComplex complexFromIntInt(Object cls, int real, int imaginary,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real, imaginary, inliningTarget, isPrimitiveProfile);
        }

        @Specialization
        PComplex complexFromLongLong(Object cls, long real, long imaginary,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real, imaginary, inliningTarget, isPrimitiveProfile);
        }

        @Specialization
        PComplex complexFromLongLong(Object cls, PInt real, PInt imaginary,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real.doubleValueWithOverflow(getRaiseNode()),
                            imaginary.doubleValueWithOverflow(getRaiseNode()), inliningTarget, isPrimitiveProfile);
        }

        @Specialization
        PComplex complexFromDoubleDouble(Object cls, double real, double imaginary,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real, imaginary, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromDouble(Object cls, double real, @SuppressWarnings("unused") PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real, 0, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromDouble(VirtualFrame frame, Object cls, PFloat real, @SuppressWarnings("unused") PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile,
                        @Shared("isBuiltinObj") @Cached IsBuiltinObjectProfile isBuiltinObjectProfile) {
            return complexFromObject(frame, cls, real, imag, inliningTarget, isComplexType, isResultComplexType, isPrimitiveProfile, isBuiltinObjectProfile);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromInt(Object cls, int real, @SuppressWarnings("unused") PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real, 0, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromLong(Object cls, long real, @SuppressWarnings("unused") PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, real, 0, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromLong(VirtualFrame frame, Object cls, PInt real, @SuppressWarnings("unused") PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile,
                        @Shared("isBuiltinObj") @Cached IsBuiltinObjectProfile isBuiltinObjectProfile) {
            return complexFromObject(frame, cls, real, imag, inliningTarget, isComplexType, isResultComplexType, isPrimitiveProfile, isBuiltinObjectProfile);
        }

        @Specialization(guards = {"isNoValue(imag)", "!isNoValue(number)", "!isString(number)"})
        PComplex complexFromObject(VirtualFrame frame, Object cls, Object number, @SuppressWarnings("unused") PNone imag,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile,
                        @Shared("isBuiltinObj") @Cached IsBuiltinObjectProfile isBuiltinObjectProfile) {
            PComplex value = getComplexNumberFromObject(frame, number, inliningTarget, isComplexType, isResultComplexType);
            if (value == null) {
                if (canBeDouble(number)) {
                    return createComplex(cls, asDouble(frame, number), 0.0, inliningTarget, isPrimitiveProfile);
                } else {
                    throw raiseFirstArgError(number);
                }
            }
            return createComplex(cls, value, inliningTarget, isPrimitiveProfile, isBuiltinObjectProfile);
        }

        @Specialization
        PComplex complexFromLongComplex(Object cls, long one, PComplex two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, one - two.getImag(), two.getReal(), inliningTarget, isPrimitiveProfile);
        }

        @Specialization
        PComplex complexFromPIntComplex(Object cls, PInt one, PComplex two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, one.doubleValueWithOverflow(getRaiseNode()) - two.getImag(), two.getReal(), inliningTarget, isPrimitiveProfile);
        }

        @Specialization
        PComplex complexFromDoubleComplex(Object cls, double one, PComplex two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return createComplex(cls, one - two.getImag(), two.getReal(), inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexLong(VirtualFrame frame, Object cls, Object one, long two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one), two, inliningTarget, isPrimitiveProfile);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexDouble(VirtualFrame frame, Object cls, Object one, double two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one), two, inliningTarget, isPrimitiveProfile);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexPInt(VirtualFrame frame, Object cls, Object one, PInt two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one), two.doubleValueWithOverflow(getRaiseNode()), inliningTarget, isPrimitiveProfile);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two.doubleValueWithOverflow(getRaiseNode()), inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexComplex(VirtualFrame frame, Object cls, Object one, PComplex two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            PComplex value = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one) - two.getImag(), two.getReal(), inliningTarget, isPrimitiveProfile);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal() - two.getImag(), value.getImag() + two.getReal(), inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(two)", "!isPComplex(two)"})
        PComplex complexFromComplexObject(VirtualFrame frame, Object cls, Object one, Object two,
                        @Bind("this") Node inliningTarget,
                        @Shared("isComplex") @Cached IsBuiltinObjectProfile isComplexType,
                        @Shared("isComplexResult") @Cached IsBuiltinObjectProfile isResultComplexType,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            PComplex oneValue = getComplexNumberFromObject(frame, one, inliningTarget, isComplexType, isResultComplexType);
            if (canBeDouble(two)) {
                double twoValue = asDouble(frame, two);
                if (oneValue == null) {
                    if (canBeDouble(one)) {
                        return createComplex(cls, asDouble(frame, one), twoValue, inliningTarget, isPrimitiveProfile);
                    } else {
                        throw raiseFirstArgError(one);
                    }
                }
                return createComplex(cls, oneValue.getReal(), oneValue.getImag() + twoValue, inliningTarget, isPrimitiveProfile);
            } else {
                throw raiseSecondArgError(two);
            }
        }

        @Specialization
        PComplex complexFromString(VirtualFrame frame, Object cls, TruffleString real, Object imaginary,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            if (imaginary != PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(frame, toJavaStringNode.execute(real), cls, real);
        }

        @Specialization
        PComplex complexFromString(VirtualFrame frame, Object cls, PString real, Object imaginary,
                        @Cached CastToJavaStringNode castToStringNode) {
            if (imaginary != PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(frame, castToStringNode.execute(real), cls, real);
        }

        private boolean canBeDouble(Object object) {
            if (canBeDoubleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                canBeDoubleNode = insert(CanBeDoubleNode.create());
            }
            return canBeDoubleNode.execute(object);
        }

        private double asDouble(VirtualFrame frame, Object object) {
            if (asDoubleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asDoubleNode = insert(PyFloatAsDoubleNode.create());
            }
            return asDoubleNode.execute(frame, object);
        }

        private Object callComplex(VirtualFrame frame, Object object) {
            if (callComplexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callComplexNode = insert(LookupAndCallUnaryNode.create(T___COMPLEX__));
            }
            return callComplexNode.executeObject(frame, object);
        }

        private WarnNode getWarnNode() {
            if (warnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                warnNode = insert(WarnNode.create());
            }
            return warnNode;
        }

        private PException raiseFirstArgError(Object x) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_NUMBER, "complex() first", x);
        }

        private PException raiseSecondArgError(Object x) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_NUMBER, "complex() second", x);
        }

        private PComplex getComplexNumberFromObject(VirtualFrame frame, Object object, Node inliningTarget,
                        IsBuiltinObjectProfile isComplexType, IsBuiltinObjectProfile isResultComplexType) {
            if (isComplexType.profileObject(inliningTarget, object, PythonBuiltinClassType.PComplex)) {
                return (PComplex) object;
            } else {
                Object result = callComplex(frame, object);
                if (result instanceof PComplex) {
                    if (!isResultComplexType.profileObject(inliningTarget, result, PythonBuiltinClassType.PComplex)) {
                        getWarnNode().warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                                        ErrorMessages.WARN_P_RETURNED_NON_P,
                                        object, "__complex__", "complex", result, "complex");
                    }
                    return (PComplex) result;
                } else if (result != PNone.NO_VALUE) {
                    throw raise(TypeError, ErrorMessages.COMPLEX_RETURNED_NON_COMPLEX, result);
                }
                if (object instanceof PComplex) {
                    // the class extending PComplex but doesn't have __complex__ method
                    return (PComplex) object;
                }
                return null;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object complexGeneric(Object cls, Object realObj, Object imaginaryObj) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "complex.__new__(X): X", cls);
        }

        // Adapted from CPython's complex_subtype_from_string
        private PComplex convertStringToComplex(VirtualFrame frame, String src, Object cls, Object origObj) {
            String str = FloatUtils.removeUnicodeAndUnderscores(src);
            if (str == null) {
                if (callReprNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callReprNode = insert(LookupAndCallUnaryNode.create(SpecialMethodSlot.Repr));
                }
                Object strStr = callReprNode.executeObject(frame, origObj);
                if (PGuards.isString(strStr)) {
                    throw raise(ValueError, ErrorMessages.COULD_NOT_CONVERT_STRING_TO_COMPLEX, strStr);
                } else {
                    // During the formatting of "ValueError: invalid literal ..." exception,
                    // CPython attempts to raise "TypeError: __repr__ returned non-string",
                    // which gets later overwitten with the original "ValueError",
                    // but without any message (since the message formatting failed)
                    throw raise(ValueError);
                }
            }
            PComplex c = convertStringToComplexOrNull(str, cls);
            if (c == null) {
                throw raise(ValueError, ErrorMessages.COMPLEX_ARG_IS_MALFORMED_STR);
            }
            return c;
        }

        // Adapted from CPython's complex_from_string_inner
        @TruffleBoundary
        private PComplex convertStringToComplexOrNull(String str, Object cls) {
            int len = str.length();

            // position on first nonblank
            int i = FloatUtils.skipAsciiWhitespace(str, 0, len);

            boolean gotBracket;
            if (i < len && str.charAt(i) == '(') {
                // Skip over possible bracket from repr().
                gotBracket = true;
                i = FloatUtils.skipAsciiWhitespace(str, i + 1, len);
            } else {
                gotBracket = false;
            }

            double x, y;
            boolean expectJ;

            // first look for forms starting with <float>
            FloatUtils.StringToDoubleResult res1 = FloatUtils.stringToDouble(str, i, len);
            if (res1 != null) {
                // all 4 forms starting with <float> land here
                i = res1.position;
                char ch = i < len ? str.charAt(i) : '\0';
                if (ch == '+' || ch == '-') {
                    // <float><signed-float>j | <float><sign>j
                    x = res1.value;
                    FloatUtils.StringToDoubleResult res2 = FloatUtils.stringToDouble(str, i, len);
                    if (res2 != null) {
                        // <float><signed-float>j
                        y = res2.value;
                        i = res2.position;
                    } else {
                        // <float><sign>j
                        y = ch == '+' ? 1.0 : -1.0;
                        i++;
                    }
                    expectJ = true;
                } else if (ch == 'j' || ch == 'J') {
                    // <float>j
                    i++;
                    y = res1.value;
                    x = 0;
                    expectJ = false;
                } else {
                    // <float>
                    x = res1.value;
                    y = 0;
                    expectJ = false;
                }
            } else {
                // not starting with <float>; must be <sign>j or j
                char ch = i < len ? str.charAt(i) : '\0';
                if (ch == '+' || ch == '-') {
                    // <sign>j
                    y = ch == '+' ? 1.0 : -1.0;
                    i++;
                } else {
                    // j
                    y = 1.0;
                }
                x = 0;
                expectJ = true;
            }

            if (expectJ) {
                char ch = i < len ? str.charAt(i) : '\0';
                if (!(ch == 'j' || ch == 'J')) {
                    return null;
                }
                i++;
            }

            // trailing whitespace and closing bracket
            i = FloatUtils.skipAsciiWhitespace(str, i, len);
            if (gotBracket) {
                // if there was an opening parenthesis, then the corresponding
                // closing parenthesis should be right here
                if (i >= len || str.charAt(i) != ')') {
                    return null;
                }
                i = FloatUtils.skipAsciiWhitespace(str, i + 1, len);
            }

            // we should now be at the end of the string
            if (i != len) {
                return null;
            }
            return createComplex(cls, x, y, null, InlineIsBuiltinClassProfile.getUncached());
        }
    }

    // dict(**kwarg)
    // dict(mapping, **kwarg)
    // dict(iterable, **kwarg)
    @Builtin(name = J_DICT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDict)
    @GenerateNodeFactory
    public abstract static class DictionaryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PDict dictEmpty(Object cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createDict(cls);
        }
    }

    // enumerate(iterable, start=0)
    @Builtin(name = J_ENUMERATE, minNumOfPositionalArgs = 2, parameterNames = {"cls", "iterable", "start"}, constructsClass = PythonBuiltinClassType.PEnumerate)
    @GenerateNodeFactory
    public abstract static class EnumerateNode extends PythonBuiltinNode {

        @Specialization
        PEnumerate doNone(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone keywordArg,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) {
            return factory().createEnumerate(cls, getIter.execute(frame, iterable), 0);
        }

        @Specialization
        PEnumerate doInt(VirtualFrame frame, Object cls, Object iterable, int start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) {
            return factory().createEnumerate(cls, getIter.execute(frame, iterable), start);
        }

        @Specialization
        PEnumerate doLong(VirtualFrame frame, Object cls, Object iterable, long start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) {
            return factory().createEnumerate(cls, getIter.execute(frame, iterable), start);
        }

        @Specialization
        PEnumerate doPInt(VirtualFrame frame, Object cls, Object iterable, PInt start,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) {
            return factory().createEnumerate(cls, getIter.execute(frame, iterable), start);
        }

        static boolean isIntegerIndex(Object idx) {
            return isInteger(idx) || idx instanceof PInt;
        }

        @Specialization(guards = "!isIntegerIndex(start)")
        void enumerate(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object iterable, Object start) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, start);
        }
    }

    // reversed(seq)
    @Builtin(name = J_REVERSED, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PReverseIterator)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class ReversedNode extends PythonBuiltinNode {

        @Specialization
        public PythonObject reversed(@SuppressWarnings("unused") Object cls, PIntRange range,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedBranchProfile overflowProfile) {
            int lstart = range.getIntStart();
            int lstep = range.getIntStep();
            int ulen = range.getIntLength();
            try {
                int new_stop = subtractExact(lstart, lstep);
                int new_start = addExact(new_stop, multiplyExact(ulen, lstep));
                return factory().createIntRangeIterator(new_start, new_stop, negateExact(lstep), ulen);
            } catch (OverflowException e) {
                overflowProfile.enter(inliningTarget);
                return handleOverflow(lstart, lstep, ulen);
            }
        }

        @TruffleBoundary
        private PBigRangeIterator handleOverflow(int lstart, int lstep, int ulen) {
            BigInteger bstart = BigInteger.valueOf(lstart);
            BigInteger bstep = BigInteger.valueOf(lstep);
            BigInteger blen = BigInteger.valueOf(ulen);
            BigInteger new_stop = bstart.subtract(bstep);
            BigInteger new_start = new_stop.add(blen.multiply(bstep));

            return factory().createBigRangeIterator(new_start, new_stop, bstep.negate(), blen);
        }

        @Specialization
        @TruffleBoundary
        public PythonObject reversed(@SuppressWarnings("unused") Object cls, PBigRange range) {
            BigInteger lstart = range.getBigIntegerStart();
            BigInteger lstep = range.getBigIntegerStep();
            BigInteger ulen = range.getBigIntegerLength();

            BigInteger new_stop = lstart.subtract(lstep);
            BigInteger new_start = new_stop.add(ulen.multiply(lstep));

            return factory().createBigRangeIterator(new_start, new_stop, lstep.negate(), ulen);
        }

        @Specialization
        public PythonObject reversed(Object cls, PString value,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return factory().createStringReverseIterator(cls, castToStringNode.execute(value));
        }

        @Specialization
        public PythonObject reversed(Object cls, TruffleString value) {
            return factory().createStringReverseIterator(cls, value);
        }

        @Specialization(guards = {"!isString(sequence)", "!isPRange(sequence)"})
        @SuppressWarnings("truffle-static-method")
        public Object reversed(VirtualFrame frame, Object cls, Object sequence,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached("create(Reversed)") LookupSpecialMethodSlotNode lookupReversed,
                        @Cached CallUnaryMethodNode callReversed,
                        @Cached("create(Len)") LookupAndCallUnaryNode lookupLen,
                        @Cached("create(GetItem)") LookupSpecialMethodSlotNode getItemNode,
                        @Cached InlinedConditionProfile noReversedProfile,
                        @Cached InlinedConditionProfile noGetItemProfile) {
            Object sequenceKlass = getClassNode.execute(inliningTarget, sequence);
            Object reversed = lookupReversed.execute(frame, sequenceKlass, sequence);
            if (noReversedProfile.profile(inliningTarget, reversed == PNone.NO_VALUE)) {
                Object getItem = getItemNode.execute(frame, sequenceKlass, sequence);
                if (noGetItemProfile.profile(inliningTarget, getItem == PNone.NO_VALUE)) {
                    throw raise(TypeError, ErrorMessages.OBJ_ISNT_REVERSIBLE, sequence);
                } else {
                    Object h = lookupLen.executeObject(frame, sequence);
                    int lengthHint;
                    try {
                        lengthHint = PGuards.expectInt(h);
                    } catch (UnexpectedResultException | OverflowException e) {
                        throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, h);
                    }
                    return factory().createSequenceReverseIterator(cls, sequence, lengthHint);
                }
            } else {
                return callReversed.executeObject(frame, reversed, sequence);
            }
        }
    }

    // float([x])
    @Builtin(name = J_FLOAT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFloat)
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class FloatNode extends PythonBinaryBuiltinNode {
        // Used for the recursive call
        protected abstract double executeDouble(VirtualFrame frame, PythonBuiltinClassType cls, Object arg) throws UnexpectedResultException;

        @Specialization(guards = "isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", limit = "1")
        static double floatFromDouble(@SuppressWarnings("unused") Object cls, double arg,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile) {
            return arg;
        }

        @Specialization(guards = "isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", limit = "1")
        static double floatFromInt(@SuppressWarnings("unused") Object cls, int arg,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile) {
            return arg;
        }

        @Specialization(guards = "isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", limit = "1")
        static double floatFromLong(@SuppressWarnings("unused") Object cls, long arg,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile) {
            return arg;
        }

        @Specialization(guards = "isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", limit = "1")
        static double floatFromBoolean(@SuppressWarnings("unused") Object cls, boolean arg,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile) {
            return arg ? 1d : 0d;
        }

        @Specialization(guards = "isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", limit = "1")
        static double floatFromString(VirtualFrame frame, @SuppressWarnings("unused") Object cls, TruffleString obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile,
                        @Shared("fromString") @Cached PyFloatFromString fromString) {
            return fromString.execute(frame, obj);
        }

        @Specialization(guards = {"isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", "isNoValue(obj)"}, limit = "1")
        static double floatFromNoValue(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") PNone obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile) {
            return 0.0;
        }

        @Specialization(guards = {"isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", "!isNoValue(obj)"}, //
                        replaces = "floatFromString", limit = "1")
        static double floatFromObject(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object obj,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile,
                        @Cached IsBuiltinObjectProfile stringProfile,
                        @Shared("fromString") @Cached PyFloatFromString fromString,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            if (stringProfile.profileObject(inliningTarget, obj, PythonBuiltinClassType.PString)) {
                return fromString.execute(frame, obj);
            }
            return pyNumberFloat.execute(frame, obj);
        }

        @Specialization(guards = {
                        "!needsNativeAllocationNode.execute(inliningTarget, cls)", //
                        "!isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)", //
                        "isNoValue(obj)"}, //
                        limit = "1")
        Object floatFromNoneManagedSubclass(Object cls, PNone obj,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile) {
            return factory().createFloat(cls, floatFromNoValue(cls, obj, inliningTarget, isPrimitiveFloatProfile));
        }

        @Specialization(guards = {
                        "!needsNativeAllocationNode.execute(inliningTarget, cls)", //
                        "!isPrimitiveFloat(this, cls, isPrimitiveFloatProfile)"}, //
                        limit = "1")
        Object floatFromObjectManagedSubclass(VirtualFrame frame, Object cls, Object obj,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @SuppressWarnings("unused") @Shared("isFloat") @Cached InlineIsBuiltinClassProfile isPrimitiveFloatProfile,
                        @Shared @Cached FloatNode recursiveCallNode) {
            try {
                return factory().createFloat(cls, recursiveCallNode.executeDouble(frame, PythonBuiltinClassType.PFloat, obj));
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere("float() returned non-primitive value");
            }
        }

        // logic similar to float_subtype_new(PyTypeObject *type, PyObject *x) from CPython
        // floatobject.c we have to first create a temporary float, then fill it into
        // a natively allocated subtype structure
        @Specialization(guards = { //
                        "needsNativeAllocationNode.execute(inliningTarget, cls)", //
                        "isSubtypeOfFloat(frame, isSubtype, cls)"}, limit = "1")
        static Object floatFromObjectNativeSubclass(VirtualFrame frame, Object cls, Object obj,
                        @Bind("this") @SuppressWarnings("unused") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.FloatSubtypeNew subtypeNew,
                        @Shared @Cached FloatNode recursiveCallNode) {
            try {
                return subtypeNew.call(cls, recursiveCallNode.executeDouble(frame, PythonBuiltinClassType.PFloat, obj));
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere("float() returned non-primitive value");
            }
        }

        protected final boolean isPrimitiveFloat(Node inliningTarget, Object cls, InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return isPrimitiveProfile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PFloat);
        }

        protected static boolean isSubtypeOfFloat(VirtualFrame frame, IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PFloat);
        }
    }

    // frozenset([iterable])
    @Builtin(name = J_FROZENSET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFrozenSet)
    @GenerateNodeFactory
    public abstract static class FrozenSetNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(arg)")
        public PFrozenSet frozensetEmpty(Object cls, @SuppressWarnings("unused") PNone arg) {
            return factory().createFrozenSet(cls);
        }

        @Specialization(guards = "isBuiltinClass.profileIsAnyBuiltinClass(inliningTarget, cls)", limit = "1")
        public static PFrozenSet frozensetIdentity(@SuppressWarnings("unused") Object cls, PFrozenSet arg,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltinProfile") @SuppressWarnings("unused") @Cached IsAnyBuiltinClassProfile isBuiltinClass) {
            return arg;
        }

        @Specialization(guards = "!isBuiltinClass.profileIsAnyBuiltinClass(inliningTarget, cls)", limit = "1")
        public PFrozenSet subFrozensetIdentity(Object cls, PFrozenSet arg,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltinProfile") @SuppressWarnings("unused") @Cached IsAnyBuiltinClassProfile isBuiltinClass) {
            return factory().createFrozenSet(cls, arg.getDictStorage());
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPFrozenSet(iterable)"})
        public PFrozenSet frozensetIterable(VirtualFrame frame, Object cls, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage storage = getHashingStorageNode.doNoValue(frame, inliningTarget, iterable);
            return factory().createFrozenSet(cls, storage);
        }
    }

    // int(x=0)
    // int(x, base=10)
    @Builtin(name = J_INT, minNumOfPositionalArgs = 1, parameterNames = {"cls", "x", "base"}, numOfPositionalOnlyArgs = 2, constructsClass = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    public abstract static class IntNode extends PythonTernaryBuiltinNode {
        @Child private BytesNodes.ToBytesNode toByteArrayNode;
        @Child private LookupAndCallUnaryNode callIndexNode;
        @Child private LookupAndCallUnaryNode callTruncNode;
        @Child private LookupAndCallUnaryNode callReprNode;
        @Child private LookupAndCallUnaryNode callIntNode;
        @Child private PyIndexCheckNode indexCheckNode;
        @Child private WarnNode warnNode;

        public final Object executeWith(VirtualFrame frame, Object number) {
            return execute(frame, PythonBuiltinClassType.PInt, number, 10);
        }

        public final Object executeWith(VirtualFrame frame, Object number, Object base) {
            return execute(frame, PythonBuiltinClassType.PInt, number, base);
        }

        @TruffleBoundary
        private static Object stringToIntInternal(String num, int base, PythonContext context) {
            try {
                BigInteger bi = asciiToBigInteger(num, base, context);
                if (bi == null) {
                    return null;
                }
                if (bi.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || bi.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                    return bi;
                } else {
                    return bi.intValue();
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private Object stringToInt(VirtualFrame frame, Object cls, String number, int base, Object origObj,
                        Node inliningTarget, InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        InlinedBranchProfile notSimpleDecimalLiteralProfile, InlinedBranchProfile invalidValueProfile,
                        InlinedBranchProfile bigIntegerProfile, InlinedBranchProfile primitiveIntProfile, InlinedBranchProfile fullIntProfile) {
            if (base == 0 || base == 10) {
                Object value = parseSimpleDecimalLiteral(number, 0, number.length());
                if (value != null) {
                    return createInt(cls, value, inliningTarget, isPrimitiveIntProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
                }
            }
            notSimpleDecimalLiteralProfile.enter(inliningTarget);
            Object value = stringToIntInternal(number, base, getContext());
            if (value == null) {
                invalidValueProfile.enter(inliningTarget);
                if (callReprNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callReprNode = insert(LookupAndCallUnaryNode.create(SpecialMethodSlot.Repr));
                }
                Object str = callReprNode.executeObject(frame, origObj);
                if (PGuards.isString(str)) {
                    throw raise(ValueError, ErrorMessages.INVALID_LITERAL_FOR_INT_WITH_BASE, base, str);
                } else {
                    // During the formatting of "ValueError: invalid literal ..." exception,
                    // CPython attempts to raise "TypeError: __repr__ returned non-string",
                    // which gets later overwitten with the original "ValueError",
                    // but without any message (since the message formatting failed)
                    throw raise(ValueError);
                }
            }
            return createInt(cls, value, inliningTarget, isPrimitiveIntProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        private Object createInt(Object cls, Object value, Node inliningTarget, InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        InlinedBranchProfile bigIntegerProfile, InlinedBranchProfile primitiveIntProfile, InlinedBranchProfile fullIntProfile) {
            if (value instanceof BigInteger) {
                bigIntegerProfile.enter(inliningTarget);
                return factory().createInt(cls, (BigInteger) value);
            } else if (isPrimitiveInt(inliningTarget, cls, isPrimitiveIntProfile)) {
                primitiveIntProfile.enter(inliningTarget);
                return value;
            } else {
                fullIntProfile.enter(inliningTarget);
                if (value instanceof Integer) {
                    return factory().createInt(cls, (Integer) value);
                } else if (value instanceof Long) {
                    return factory().createInt(cls, (Long) value);
                } else if (value instanceof Boolean) {
                    return factory().createInt(cls, (Boolean) value ? 1 : 0);
                } else if (value instanceof PInt) {
                    return factory().createInt(cls, ((PInt) value).getValue());
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Unexpected type");
        }

        private void checkBase(int base, Node inliningTarget, InlinedConditionProfile invalidBase) {
            if (invalidBase.profile(inliningTarget, (base < 2 || base > 36) && base != 0)) {
                throw raise(ValueError, ErrorMessages.BASE_OUT_OF_RANGE_FOR_INT);
            }
        }

        private void checkBase(PInt base, Node inliningTarget, InlinedConditionProfile invalidBase) {
            int ibase;
            try {
                ibase = base.intValueExact();
            } catch (OverflowException e) {
                // this should just trigger the error
                ibase = 1;
            }
            checkBase(ibase, inliningTarget, invalidBase);
        }

        // Adapted from Jython
        private static BigInteger asciiToBigInteger(String str, int possibleBase, PythonContext context) throws NumberFormatException {
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

            boolean acceptUnderscore = false;
            boolean raiseIfNotZero = false;
            char sign = 0;
            if (b < e) {
                sign = str.charAt(b);
                if (sign == '-' || sign == '+') {
                    b++;
                }

                if (base == 16) {
                    if (str.charAt(b) == '0') {
                        if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                            b += 2;
                            acceptUnderscore = true;
                        }
                    }
                } else if (base == 0) {
                    if (str.charAt(b) == '0') {
                        if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'X') {
                            base = 16;
                            b += 2;
                            acceptUnderscore = true;
                        } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                            base = 8;
                            b += 2;
                            acceptUnderscore = true;
                        } else if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                            base = 2;
                            b += 2;
                            acceptUnderscore = true;
                        } else {
                            raiseIfNotZero = true;
                        }
                    }
                } else if (base == 8) {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'O') {
                        b += 2;
                        acceptUnderscore = true;
                    }
                } else if (base == 2) {
                    if (b < e - 1 && Character.toUpperCase(str.charAt(b + 1)) == 'B') {
                        b += 2;
                        acceptUnderscore = true;
                    }
                }
            }

            if (base == 0) {
                base = 10;
            }

            // reject invalid characters without going to BigInteger
            for (int i = b; i < e; i++) {
                char c = str.charAt(i);
                if (c == '_') {
                    if (!acceptUnderscore || i == e - 1) {
                        throw new NumberFormatException("Illegal underscore in int literal");
                    } else {
                        acceptUnderscore = false;
                    }
                } else {
                    acceptUnderscore = true;
                    if (Character.digit(c, base) == -1) {
                        // invalid char
                        return null;
                    }
                }
            }

            String s = str;
            if (b > 0 || e < str.length()) {
                s = str.substring(b, e);
            }
            s = s.replace("_", "");

            checkMaxDigits(context, s.length(), base);

            BigInteger bi;
            if (sign == '-') {
                bi = new BigInteger("-" + s, base);
            } else {
                bi = new BigInteger(s, base);
            }

            if (raiseIfNotZero && !bi.equals(BigInteger.ZERO)) {
                throw new NumberFormatException("Obsolete octal int literal");
            }
            return bi;
        }

        private static void checkMaxDigits(PythonContext context, int digits, int base) {
            if (digits > SysModuleBuiltins.INT_MAX_STR_DIGITS_THRESHOLD && Integer.bitCount(base) != 1) {
                Integer maxDigits = context.getIntMaxStrDigits();
                if (maxDigits > 0 && digits > maxDigits) {
                    throw PRaiseNode.getUncached().raise(ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION_D, maxDigits, digits);
                }
            }
        }

        /**
         * Fast path parser of integer literals. Accepts only a subset of allowed literals - no
         * underscores, no leading zeros, no plus sign, no spaces, only ascii digits and the result
         * must be small enough to fit into long.
         *
         * @param arg the string to parse
         * @return parsed integer, long or null if the literal is not simple enough
         */
        public static Object parseSimpleDecimalLiteral(String arg, int offset, int remaining) {
            if (remaining <= 0) {
                return null;
            }
            int start = arg.charAt(offset) == '-' ? 1 : 0;
            if (remaining <= start || remaining > 18 + start) {
                return null;
            }
            if (arg.charAt(start + offset) == '0') {
                if (remaining > start + 1) {
                    return null;
                }
                return 0;
            }
            long value = 0;
            for (int i = start; i < remaining; i++) {
                char c = arg.charAt(i + offset);
                if (c < '0' || c > '9') {
                    return null;
                }
                value = value * 10 + (c - '0');
            }
            if (start != 0) {
                value = -value;
            }
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        }

        protected boolean isPrimitiveInt(Node inliningTarget, Object cls, InlineIsBuiltinClassProfile profile) {
            return profile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PInt);
        }

        @Specialization
        Object parseInt(Object cls, boolean arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile) {
            if (isPrimitiveInt(inliningTarget, cls, isPrimitiveIntProfile)) {
                return arg ? 1 : 0;
            } else {
                return factory().createInt(cls, arg ? 1 : 0);
            }
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, int arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile) {
            if (isPrimitiveInt(inliningTarget, cls, isPrimitiveIntProfile)) {
                return arg;
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, long arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Exclusive @Cached InlinedConditionProfile isIntProfile) {
            if (isPrimitiveInt(inliningTarget, cls, isPrimitiveIntProfile)) {
                int intValue = (int) arg;
                if (isIntProfile.profile(inliningTarget, intValue == arg)) {
                    return intValue;
                } else {
                    return arg;
                }
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        @SuppressWarnings("truffle-static-method")
        Object createInt(Object cls, double arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Cached PyLongFromDoubleNode pyLongFromDoubleNode,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            Object result = pyLongFromDoubleNode.execute(inliningTarget, arg);
            return createInt(cls, result, inliningTarget, isPrimitiveIntProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        // String

        @Specialization(guards = "isNoValue(base)")
        @Megamorphic
        Object createInt(VirtualFrame frame, Object cls, TruffleString arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            return stringToInt(frame, cls, toJavaStringNode.execute(arg), 10, arg, inliningTarget, isPrimitiveIntProfile,
                            notSimpleDecimalLiteralProfile, invalidValueProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        @Specialization
        @Megamorphic
        Object parsePIntError(VirtualFrame frame, Object cls, TruffleString number, int base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared @Cached InlinedConditionProfile invalidBase,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            checkBase(base, inliningTarget, invalidBase);
            return stringToInt(frame, cls, toJavaStringNode.execute(number), base, number,
                            inliningTarget, isPrimitiveIntProfile, notSimpleDecimalLiteralProfile, invalidValueProfile,
                            bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        @Specialization(guards = "!isNoValue(base)")
        @Megamorphic
        Object createIntError(VirtualFrame frame, Object cls, TruffleString number, Object base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared @Cached InlinedConditionProfile invalidBase,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            int intBase = asSizeNode.executeLossy(frame, base);
            checkBase(intBase, inliningTarget, invalidBase);
            return stringToInt(frame, cls, toJavaStringNode.execute(number), intBase, number,
                            inliningTarget, isPrimitiveIntProfile, notSimpleDecimalLiteralProfile, invalidValueProfile,
                            bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        // PIBytesLike
        @Specialization
        @Megamorphic
        Object parseBytesError(VirtualFrame frame, Object cls, PBytesLike arg, int base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared @Cached InlinedConditionProfile invalidBase,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            checkBase(base, inliningTarget, invalidBase);
            return stringToInt(frame, cls, toString(arg), base, arg, inliningTarget,
                            isPrimitiveIntProfile, notSimpleDecimalLiteralProfile, invalidValueProfile, bigIntegerProfile,
                            primitiveIntProfile, fullIntProfile);
        }

        @Specialization(guards = "isNoValue(base)")
        @Megamorphic
        Object parseBytesError(VirtualFrame frame, Object cls, PBytesLike arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared @Cached InlinedConditionProfile invalidBase,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            return parseBytesError(frame, cls, arg, 10, inliningTarget, isPrimitiveIntProfile, invalidBase,
                            notSimpleDecimalLiteralProfile, invalidValueProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        // PString
        @Specialization(guards = "isNoValue(base)")
        @Megamorphic
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToStringNode,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            Object result = callInt(frame, arg);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            return stringToInt(frame, cls, castToStringNode.execute(arg), 10, arg, inliningTarget, isPrimitiveIntProfile,
                            notSimpleDecimalLiteralProfile, invalidValueProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        @Specialization
        @Megamorphic
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, int base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToStringNode,
                        @Shared @Cached InlinedConditionProfile invalidBase,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            checkBase(base, inliningTarget, invalidBase);
            Object result = callInt(frame, arg);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            return stringToInt(frame, cls, castToStringNode.execute(arg), base, arg, inliningTarget,
                            isPrimitiveIntProfile, notSimpleDecimalLiteralProfile, invalidValueProfile,
                            bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        @Specialization
        @Megamorphic
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, PInt base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToStringNode,
                        @Shared @Cached InlinedConditionProfile invalidBase,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            checkBase(base, inliningTarget, invalidBase);
            Object result = callInt(frame, arg);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            return stringToInt(frame, cls, castToStringNode.execute(arg), base.intValue(), arg, inliningTarget,
                            isPrimitiveIntProfile, notSimpleDecimalLiteralProfile, invalidValueProfile, bigIntegerProfile,
                            primitiveIntProfile, fullIntProfile);
        }

        // other

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, PythonNativeVoidPtr arg, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile) {
            if (isPrimitiveInt(inliningTarget, cls, isPrimitiveIntProfile)) {
                return arg;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("cannot wrap void ptr in int subclass");
            }
        }

        @Specialization(guards = "isNoValue(none)")
        Object createInt(Object cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile) {
            if (isPrimitiveInt(inliningTarget, cls, isPrimitiveIntProfile)) {
                return 0;
            }
            return factory().createInt(cls, 0);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isString(arg)", "!isBytes(arg)", "!isNoValue(base)"})
        Object fail(Object cls, Object arg, Object base) {
            throw raise(TypeError, ErrorMessages.INT_CANT_CONVERT_STRING_WITH_EXPL_BASE);
        }

        @Specialization(guards = {"isNoValue(base)", "!isNoValue(obj)", "!isHandledType(obj)"})
        @SuppressWarnings("truffle-static-method")
        @Megamorphic
        Object createIntGeneric(VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") PNone base,
                        @Bind("this") Node inliningTarget,
                        @Cached IsBuiltinObjectProfile isPrimitiveIntObjectProfile,
                        @Shared("primitiveInt") @Cached InlineIsBuiltinClassProfile isPrimitiveIntProfile,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached InlinedBranchProfile notSimpleDecimalLiteralProfile,
                        @Shared @Cached InlinedBranchProfile invalidValueProfile,
                        @Shared @Cached InlinedBranchProfile bigIntegerProfile,
                        @Shared @Cached InlinedBranchProfile primitiveIntProfile,
                        @Shared @Cached InlinedBranchProfile fullIntProfile) {
            /*
             * This method (together with callInt and callIndex) reflects the logic of PyNumber_Long
             * in CPython. We don't use PythonObjectLibrary here since the original CPython function
             * does not use any of the conversion functions (such as _PyLong_AsInt or
             * PyNumber_Index) either, but it reimplements the logic in a slightly different way
             * (e.g. trying __int__ before __index__ whereas _PyLong_AsInt does it the other way)
             * and also with specific exception messages which are expected by Python unittests.
             * This unfortunately means that this method relies on the internal logic of NO_VALUE
             * return values representing missing magic methods which should be ideally hidden by
             * PythonObjectLibrary.
             */
            Object result = callInt(frame, obj);
            if (result == PNone.NO_VALUE) {
                result = callIndex(frame, obj);
                if (result == PNone.NO_VALUE) {
                    Object truncResult = callTrunc(frame, obj);
                    if (truncResult == PNone.NO_VALUE) {
                        Object buffer;
                        try {
                            buffer = bufferAcquireLib.acquireReadonly(obj, frame, this);
                        } catch (PException e) {
                            throw raise(TypeError, ErrorMessages.ARG_MUST_BE_STRING_OR_BYTELIKE_OR_NUMBER, "int()", obj);
                        }
                        try {
                            String number = newString(bufferLib.getInternalOrCopiedByteArray(buffer), 0, bufferLib.getBufferLength(buffer));
                            return stringToInt(frame, cls, number, 10, obj, inliningTarget, isPrimitiveIntProfile,
                                            notSimpleDecimalLiteralProfile, invalidValueProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
                        } finally {
                            bufferLib.release(buffer, frame, this);
                        }
                    }
                    if (isIntegerType(truncResult)) {
                        result = truncResult;
                    } else {
                        result = callIndex(frame, truncResult);
                        if (result == PNone.NO_VALUE) {
                            result = callInt(frame, truncResult);
                            if (result == PNone.NO_VALUE) {
                                throw raise(TypeError, ErrorMessages.RETURNED_NON_INTEGRAL, "__trunc__", truncResult);
                            }
                        }
                    }
                }
            }

            // If a subclass of int is returned by __int__ or __index__, a conversion to int is
            // performed and a DeprecationWarning should be triggered (see PyNumber_Long).
            if (!isPrimitiveIntObjectProfile.profileObject(inliningTarget, result, PythonBuiltinClassType.PInt)) {
                getWarnNode().warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                                ErrorMessages.WARN_P_RETURNED_NON_P,
                                obj, "__int__/__index__", "int", result, "int");
                if (PGuards.isPInt(result)) {
                    result = ((PInt) result).getValue();
                } else if (PGuards.isBoolean(result)) {
                    result = (boolean) result ? 1 : 0;
                }
            }
            return createInt(cls, result, inliningTarget, isPrimitiveIntProfile, bigIntegerProfile, primitiveIntProfile, fullIntProfile);
        }

        protected static boolean isIntegerType(Object obj) {
            return PGuards.isBoolean(obj) || PGuards.isInteger(obj) || PGuards.isPInt(obj);
        }

        protected static boolean isHandledType(Object obj) {
            return PGuards.isInteger(obj) || obj instanceof Double || obj instanceof Boolean || PGuards.isString(obj) || PGuards.isBytes(obj) || obj instanceof PythonNativeVoidPtr;
        }

        private Object callIndex(VirtualFrame frame, Object obj) {
            if (callIndexNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callIndexNode = insert(LookupAndCallUnaryNode.create(SpecialMethodSlot.Index));
            }
            Object result = callIndexNode.executeObject(frame, obj);
            // the case when the result is NO_VALUE (i.e. the object does not provide __index__)
            // is handled in createIntGeneric
            if (result != PNone.NO_VALUE && !isIntegerType(result)) {
                throw raise(TypeError, ErrorMessages.RETURNED_NON_INT, J___INDEX__, result);
            }
            return result;
        }

        private Object callTrunc(VirtualFrame frame, Object obj) {
            if (callTruncNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callTruncNode = insert(LookupAndCallUnaryNode.create(T___TRUNC__));
            }
            Object result = callTruncNode.executeObject(frame, obj);
            if (result != PNone.NO_VALUE) {
                getWarnNode().warnEx(frame, DeprecationWarning, ErrorMessages.WARN_DELEGATION_OF_INT_TO_TRUNC_IS_DEPRECATED, 1);
                if (getIndexCheckNode().execute(result)) {
                    return callIndex(frame, result);
                } else {
                    throw raise(TypeError, ErrorMessages.RETURNED_NON_INTEGRAL, J___TRUNC__, result);
                }
            }
            return result;
        }

        private Object callInt(VirtualFrame frame, Object object) {
            if (callIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callIntNode = insert(LookupAndCallUnaryNode.create(SpecialMethodSlot.Int));
            }
            Object result = callIntNode.executeObject(frame, object);
            if (result != PNone.NO_VALUE && !isIntegerType(result)) {
                throw raise(TypeError, ErrorMessages.RETURNED_NON_INT, T___INT__, result);
            }
            return result;
        }

        private PyIndexCheckNode getIndexCheckNode() {
            if (indexCheckNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexCheckNode = insert(PyIndexCheckNode.create());
            }
            return indexCheckNode;
        }

        private WarnNode getWarnNode() {
            if (warnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                warnNode = insert(WarnNode.create());
            }
            return warnNode;
        }

        private String toString(PBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(BytesNodes.ToBytesNode.create());
            }
            return newString(toByteArrayNode.execute(pByteArray));
        }

        @TruffleBoundary(allowInlining = true)
        private static String newString(byte[] bytes) {
            return new String(bytes);
        }

        @TruffleBoundary(allowInlining = true)
        private static String newString(byte[] bytes, int offset, int length) {
            return new String(bytes, offset, length);
        }
    }

    // bool([x])
    @Builtin(name = J_BOOL, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.Boolean, base = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class BoolNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static boolean bool(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object obj,
                        @Cached PyObjectIsTrueNode isTrue) {
            return isTrue.execute(frame, obj);
        }
    }

    // list([iterable])
    @Builtin(name = J_LIST, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PList)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonVarargsBuiltinNode {
        @Specialization
        protected PList constructList(Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createList(cls);
        }
    }

    // object()
    @Builtin(name = J_OBJECT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonObject)
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {

        @Child private SplitArgsNode splitArgsNode;
        @Child private LookupCallableSlotInMRONode lookupInit;
        @Child private LookupCallableSlotInMRONode lookupNew;
        @CompilationFinal private ValueProfile profileInit;
        @CompilationFinal private ValueProfile profileNew;
        @CompilationFinal private ValueProfile profileInitFactory;
        @CompilationFinal private ValueProfile profileNewFactory;

        @GenerateCached(false)
        @GenerateInline
        abstract static class ReportAbstractClassNode extends PNodeWithContext {
            public abstract PException execute(VirtualFrame frame, Node inliningTarget, Object type);

            @Specialization
            static PException report(VirtualFrame frame, Object type,
                            @Cached(inline = false) PyObjectCallMethodObjArgs callSort,
                            @Cached(inline = false) PyObjectCallMethodObjArgs callJoin,
                            @Cached(inline = false) PyObjectSizeNode sizeNode,
                            @Cached(inline = false) ReadAttributeFromObjectNode readAttributeFromObjectNode,
                            @Cached(inline = false) CastToTruffleStringNode cast,
                            @Cached(inline = false) ListNodes.ConstructListNode constructListNode,
                            @Cached(inline = false) PRaiseNode raiseNode) {
                PList list = constructListNode.execute(frame, readAttributeFromObjectNode.execute(type, T___ABSTRACTMETHODS__));
                int methodCount = sizeNode.execute(frame, list);
                callSort.execute(frame, list, T_SORT);
                TruffleString joined = cast.execute(callJoin.execute(frame, T_COMMA_SPACE, T_JOIN, list));
                throw raiseNode.raise(TypeError, ErrorMessages.CANT_INSTANTIATE_ABSTRACT_CLASS_WITH_ABSTRACT_METHODS, type, methodCount > 1 ? "s" : "", joined);
            }
        }

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @Specialization(guards = {"!self.needsNativeAllocation()"})
        Object doManagedObject(VirtualFrame frame, PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ReportAbstractClassNode reportAbstractClassNode) {
            checkExcessArgs(self, varargs, kwargs);
            if (self.isAbstractClass()) {
                throw reportAbstractClassNode.execute(frame, inliningTarget, self);
            }
            return factory().createPythonObject(self);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        Object doBuiltinTypeType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs) {
            checkExcessArgs(self, varargs, kwargs);
            return factory().createPythonObject(self);
        }

        @Specialization(guards = "self.needsNativeAllocation()")
        Object doNativeObjectIndirect(VirtualFrame frame, PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ReportAbstractClassNode reportAbstractClassNode,
                        @Shared @Cached CallNativeGenericNewNode callNativeGenericNewNode) {
            checkExcessArgs(self, varargs, kwargs);
            if (self.isAbstractClass()) {
                throw reportAbstractClassNode.execute(frame, inliningTarget, self);
            }
            return callNativeGenericNewNode.execute(inliningTarget, self);
        }

        @Specialization(guards = "isNativeClass(self)")
        @SuppressWarnings("truffle-static-method")
        Object doNativeObjectDirect(VirtualFrame frame, Object self, Object[] varargs, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached TypeNodes.GetTypeFlagsNode getTypeFlagsNode,
                        @Shared @Cached ReportAbstractClassNode reportAbstractClassNode,
                        @Shared @Cached CallNativeGenericNewNode callNativeGenericNewNode) {
            checkExcessArgs(self, varargs, kwargs);
            if ((getTypeFlagsNode.execute(self) & TypeFlags.IS_ABSTRACT) != 0) {
                throw reportAbstractClassNode.execute(frame, inliningTarget, self);
            }
            return callNativeGenericNewNode.execute(inliningTarget, self);
        }

        @GenerateInline
        @GenerateCached(false)
        protected abstract static class CallNativeGenericNewNode extends Node {
            abstract Object execute(Node inliningTarget, Object cls);

            @Specialization
            static Object call(Object cls,
                            @Cached PythonToNativeNode toNativeNode,
                            @Cached NativeToPythonNode toPythonNode,
                            @Cached PCallCapiFunction callCapiFunction) {
                return toPythonNode.execute(callCapiFunction.call(FUN_PY_OBJECT_NEW, toNativeNode.execute(cls)));
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fallback(Object o, Object[] varargs, PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "object.__new__(X): X", o);
        }

        private void checkExcessArgs(Object type, Object[] varargs, PKeyword[] kwargs) {
            if (varargs.length != 0 || kwargs.length != 0) {
                if (lookupNew == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lookupNew = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.New));
                }
                if (lookupInit == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lookupInit = insert(LookupCallableSlotInMRONode.create(SpecialMethodSlot.Init));
                }
                if (profileNew == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileNew = createValueIdentityProfile();
                }
                if (profileInit == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileInit = createValueIdentityProfile();
                }
                if (profileNewFactory == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileNewFactory = ValueProfile.createClassProfile();
                }
                if (profileInitFactory == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profileInitFactory = ValueProfile.createClassProfile();
                }
                if (ObjectBuiltins.InitNode.overridesBuiltinMethod(type, profileNew, lookupNew, profileNewFactory, BuiltinConstructorsFactory.ObjectNodeFactory.class)) {
                    throw raise(TypeError, ErrorMessages.NEW_TAKES_ONE_ARG);
                }
                if (!ObjectBuiltins.InitNode.overridesBuiltinMethod(type, profileInit, lookupInit, profileInitFactory, ObjectBuiltinsFactory.InitNodeFactory.class)) {
                    throw raise(TypeError, ErrorMessages.NEW_TAKES_NO_ARGS, type);
                }
            }
        }
    }

    // range(stop)
    // range(start, stop[, step])
    @Builtin(name = J_RANGE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PRange)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class RangeNode extends PythonQuaternaryBuiltinNode {
        // stop
        @Specialization(guards = "isStop(start, stop, step)")
        Object doIntStop(Object cls, int stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            return doInt(cls, 0, stop, 1, inliningTarget, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        Object doPintStop(Object cls, PInt stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            return doPint(cls, factory().createInt(0), stop, factory().createInt(1), inliningTarget, stepZeroProfile, lenOfRangeNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        Object doGenericStop(VirtualFrame frame, Object cls, Object stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinObjectProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode) {
            return doGeneric(frame, cls, 0, stop, 1, inliningTarget, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, cast, overflowProfile, indexNode);
        }

        // start stop
        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doIntStartStop(Object cls, int start, int stop, @SuppressWarnings("unused") PNone step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            return doInt(cls, start, stop, 1, inliningTarget, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doPintStartStop(Object cls, PInt start, PInt stop, @SuppressWarnings("unused") PNone step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            return doPint(cls, start, stop, factory().createInt(1), inliningTarget, stepZeroProfile, lenOfRangeNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doGenericStartStop(VirtualFrame frame, Object cls, Object start, Object stop, @SuppressWarnings("unused") PNone step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinObjectProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode) {
            return doGeneric(frame, cls, start, stop, 1, inliningTarget, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, cast, overflowProfile, indexNode);
        }

        // start stop step
        @Specialization
        Object doInt(@SuppressWarnings("unused") Object cls, int start, int stop, int step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            if (stepZeroProfile.profile(inliningTarget, step == 0)) {
                throw raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            try {
                int len = lenOfRangeNode.executeInt(start, stop, step);
                return factory().createIntRange(start, stop, step, len);
            } catch (OverflowException e) {
                exceptionProfile.enter(this);
                return createBigRangeNode.execute(start, stop, step, factory());
            }
        }

        @Specialization
        Object doPint(@SuppressWarnings("unused") Object cls, PInt start, PInt stop, PInt step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            if (stepZeroProfile.profile(inliningTarget, step.isZero())) {
                throw raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            BigInteger len = lenOfRangeNode.execute(start.getValue(), stop.getValue(), step.getValue());
            return factory().createBigRange(start, stop, step, factory().createInt(len));
        }

        @Specialization(guards = "isStartStopStep(start, stop, step)")
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object start, Object stop, Object step,
                        @Bind("this") Node inliningTarget,
                        @Shared("stepZeroProfile") @Cached InlinedConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinObjectProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode) {
            Object lstart = indexNode.execute(frame, start);
            Object lstop = indexNode.execute(frame, stop);
            Object lstep = indexNode.execute(frame, step);

            try {
                int istart = cast.execute(lstart);
                int istop = cast.execute(lstop);
                int istep = cast.execute(lstep);
                return doInt(cls, istart, istop, istep, inliningTarget, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode);
            } catch (PException e) {
                e.expect(inliningTarget, OverflowError, overflowProfile);
                return createBigRangeNode.execute(lstart, lstop, lstep, factory());
            }
        }

        protected static boolean isStop(Object start, Object stop, Object step) {
            return isNoValue(start) && !isNoValue(stop) && isNoValue(step);
        }

        protected static boolean isStartStop(Object start, Object stop, Object step) {
            return !isNoValue(start) && !isNoValue(stop) && isNoValue(step);
        }

        protected static boolean isStartStopStep(Object start, Object stop, Object step) {
            return !isNoValue(start) && !isNoValue(stop) && !isNoValue(step);
        }
    }

    // set([iterable])
    @Builtin(name = J_SET, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PSet)
    @GenerateNodeFactory
    public abstract static class SetNode extends PythonBuiltinNode {

        @Specialization
        public PSet setEmpty(Object cls, @SuppressWarnings("unused") Object arg) {
            return factory().createSet(cls);
        }

    }

    // str(object='')
    // str(object=b'', encoding='utf-8', errors='strict')
    @Builtin(name = J_STR, minNumOfPositionalArgs = 1, parameterNames = {"cls", "object", "encoding", "errors"}, constructsClass = PythonBuiltinClassType.PString)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonBuiltinNode {
        @Child private LookupAndCallTernaryNode callDecodeNode;

        @CompilationFinal private ConditionProfile isStringProfile;
        @CompilationFinal private ConditionProfile isPStringProfile;
        @Child private CastToTruffleStringNode castToTruffleStringNode;

        public final Object executeWith(Object arg) {
            return executeWith(null, PythonBuiltinClassType.PString, arg, PNone.NO_VALUE, PNone.NO_VALUE);
        }

        public final Object executeWith(VirtualFrame frame, Object arg) {
            return executeWith(frame, PythonBuiltinClassType.PString, arg, PNone.NO_VALUE, PNone.NO_VALUE);
        }

        public abstract Object executeWith(VirtualFrame frame, Object cls, Object arg, Object encoding, Object errors);

        @Specialization(guards = {"!needsNativeAllocationNode.execute(inliningTarget, cls)", "isNoValue(arg)"}, limit = "1")
        @SuppressWarnings("unused")
        Object strNoArgs(Object cls, PNone arg, Object encoding, Object errors,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile) {
            return asPString(cls, T_EMPTY_STRING, inliningTarget, isPrimitiveProfile);
        }

        @Specialization(guards = {"!needsNativeAllocationNode.execute(inliningTarget, cls)", "!isNoValue(obj)", "isNoValue(encoding)", "isNoValue(errors)"}, limit = "1")
        Object strOneArg(VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile,
                        @Shared @Cached PyObjectStrAsObjectNode strNode) {
            Object result = strNode.execute(frame, obj);

            // try to return a primitive if possible
            result = assertNoJavaString(result);
            if (getIsStringProfile().profile(result instanceof TruffleString)) {
                return asPString(cls, (TruffleString) result, inliningTarget, isPrimitiveProfile);
            }

            if (isPrimitiveProfile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PString)) {
                // PyObjectStrAsObjectNode guarantees that the returned object is an instanceof of
                // 'str'
                return result;
            } else {
                try {
                    return asPString(cls, getCastToTruffleStringNode().execute(result), inliningTarget, isPrimitiveProfile);
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("asPstring result not castable to String");
                }
            }
        }

        @Specialization(guards = {"!needsNativeAllocationNode.execute(inliningTarget, cls)", "!isNoValue(encoding) || !isNoValue(errors)"}, limit = "3")
        @SuppressWarnings("truffle-static-method")
        Object doBuffer(VirtualFrame frame, Object cls, Object obj, Object encoding, Object errors,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared("isPrimitive") @Cached InlineIsBuiltinClassProfile isPrimitiveProfile,
                        @CachedLibrary("obj") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            Object buffer;
            try {
                buffer = acquireLib.acquireReadonly(obj, frame, this);
            } catch (PException e) {
                throw raise(TypeError, ErrorMessages.NEED_BYTELIKE_OBJ, obj);
            }
            try {
                // TODO(fa): we should directly call '_codecs.decode'
                // TODO don't copy, CPython creates a memoryview
                PBytes bytesObj = factory().createBytes(bufferLib.getCopiedByteArray(buffer));
                Object en = encoding == PNone.NO_VALUE ? T_UTF8 : encoding;
                return decodeBytes(frame, cls, bytesObj, en, errors, inliningTarget, isPrimitiveProfile);
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        private Object decodeBytes(VirtualFrame frame, Object cls, PBytes obj, Object encoding, Object errors, Node inliningTarget, InlineIsBuiltinClassProfile isPrimitiveProfile) {
            Object result = getCallDecodeNode().execute(frame, obj, encoding, errors);
            result = assertNoJavaString(result);
            if (getIsStringProfile().profile(result instanceof TruffleString)) {
                return asPString(cls, (TruffleString) result, inliningTarget, isPrimitiveProfile);
            } else if (getIsPStringProfile().profile(result instanceof PString)) {
                return result;
            }
            throw raise(TypeError, ErrorMessages.P_S_RETURNED_NON_STRING, obj, "decode", result);
        }

        /**
         * logic similar to
         * {@code unicode_subtype_new(PyTypeObject *type, PyObject *args, PyObject *kwds)} from
         * CPython {@code unicodeobject.c} we have to first create a temporary string, then fill it
         * into a natively allocated subtype structure
         */
        @Specialization(guards = {"needsNativeAllocationNode.execute(inliningTarget, cls)", "isSubtypeOfString(frame, isSubtype, cls)", //
                        "isNoValue(encoding)", "isNoValue(errors)"}, limit = "1")
        static Object doNativeSubclass(VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Shared @Cached PyObjectStrAsObjectNode strNode,
                        @Cached CExtNodes.StringSubtypeNew subtypeNew) {
            if (obj == PNone.NO_VALUE) {
                return subtypeNew.call(cls, T_EMPTY_STRING);
            } else {
                return subtypeNew.call(cls, strNode.execute(frame, obj));
            }
        }

        protected static boolean isSubtypeOfString(VirtualFrame frame, IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PString);
        }

        private Object asPString(Object cls, TruffleString str, Node inliningTarget, InlineIsBuiltinClassProfile isPrimitiveProfile) {
            if (isPrimitiveProfile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PString)) {
                return str;
            } else {
                return factory().createString(cls, str);
            }
        }

        private LookupAndCallTernaryNode getCallDecodeNode() {
            if (callDecodeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callDecodeNode = insert(LookupAndCallTernaryNode.create(T_DECODE));
            }
            return callDecodeNode;
        }

        private ConditionProfile getIsStringProfile() {
            if (isStringProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isStringProfile = ConditionProfile.create();
            }
            return isStringProfile;
        }

        private ConditionProfile getIsPStringProfile() {
            if (isPStringProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isPStringProfile = ConditionProfile.create();
            }
            return isPStringProfile;
        }

        private CastToTruffleStringNode getCastToTruffleStringNode() {
            if (castToTruffleStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToTruffleStringNode = insert(CastToTruffleStringNode.create());
            }
            return castToTruffleStringNode;
        }

        public static StrNode create() {
            return BuiltinConstructorsFactory.StrNodeFactory.create(null);
        }
    }

    // tuple([iterable])
    @Builtin(name = J_TUPLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PTuple)
    @GenerateNodeFactory
    public abstract static class TupleNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
        static PTuple constructTuple(VirtualFrame frame, Object cls, Object iterable,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(frame, cls, iterable);
        }

        // delegate to tuple_subtype_new(PyTypeObject *type, PyObject *x)
        @Specialization(guards = {"needsNativeAllocationNode.execute(inliningTarget, cls)", "isSubtypeOfTuple(frame, isSubtype, cls)"}, limit = "1")
        static Object doNative(@SuppressWarnings("unused") VirtualFrame frame, Object cls, Object iterable,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.TupleSubtypeNew subtypeNew) {
            return subtypeNew.call(cls, iterable);
        }

        protected static boolean isSubtypeOfTuple(VirtualFrame frame, IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PTuple);
        }

        @Fallback
        public PTuple tupleObject(Object cls, @SuppressWarnings("unused") Object arg) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    // zip(*iterables)
    @Builtin(name = J_ZIP, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PZip)
    @GenerateNodeFactory
    public abstract static class ZipNode extends PythonBuiltinNode {
        @Specialization
        PZip zip(VirtualFrame frame, Object cls, Object[] args, @SuppressWarnings("unused") PNone kw,
                        @Shared @Cached PyObjectGetIter getIter) {
            return zip(frame, cls, args, false, getIter);
        }

        @Specialization(guards = "kw.length == 0")
        PZip zip(VirtualFrame frame, Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] kw,
                        @Shared @Cached PyObjectGetIter getIter) {
            return zip(frame, cls, args, false, getIter);
        }

        @Specialization(guards = "kw.length == 1")
        PZip zip(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kw,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.EqualNode eqNode,
                        @Shared @Cached PyObjectGetIter getIter,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached InlinedConditionProfile profile) {
            if (profile.profile(inliningTarget, eqNode.execute(kw[0].getName(), T_STRICT, TS_ENCODING))) {
                return zip(frame, cls, args, isTrueNode.execute(frame, kw[0].getValue()), getIter);
            }
            throw raise(TypeError, ErrorMessages.S_IS_AN_INVALID_ARG_FOR_S, kw[0].getName(), T_ZIP);
        }

        @Specialization(guards = "kw.length != 1")
        Object zip(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object[] args, PKeyword[] kw) {
            throw raise(TypeError, ErrorMessages.S_TAKES_AT_MOST_ONE_KEYWORD_ARGUMENT_D_GIVEN, T_ZIP, kw.length);
        }

        private PZip zip(VirtualFrame frame, Object cls, Object[] args, boolean strict, PyObjectGetIter getIter) {
            Object[] iterables = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object item = args[i];
                iterables[i] = getIter.execute(frame, item);
            }
            return factory().createZip(cls, iterables, strict);
        }
    }

    // function(code, globals[, name[, argdefs[, closure]]])
    @Builtin(name = "function", minNumOfPositionalArgs = 3, parameterNames = {"$cls", "code", "globals", "name", "argdefs",
                    "closure"}, constructsClass = PythonBuiltinClassType.PFunction, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure) {
            return factory().createFunction(name, code, globals, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        PTuple closure,
                        @Bind("this") Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction(T_LAMBDA_NAME, code, globals, PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closure)));
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @SuppressWarnings("unused") @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction(T_LAMBDA_NAME, code, globals, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure,
                        @Bind("this") Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction(name, code, globals, PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closure)));
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, PTuple defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @Bind("this") Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(code.getName(), code, globals, getObjectArrayNode.execute(inliningTarget, defaultArgs), null, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, PTuple defaultArgs, @SuppressWarnings("unused") PNone closure,
                        @Bind("this") Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, code, globals, getObjectArrayNode.execute(inliningTarget, defaultArgs), null, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, TruffleString name, PTuple defaultArgs, PTuple closure,
                        @Bind("this") Node inliningTarget,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, code, globals, getObjectArrayNode.execute(inliningTarget, defaultArgs), null, PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closure)));
        }

        @Fallback
        @SuppressWarnings("unused")
        public PFunction function(@SuppressWarnings("unused") Object cls, Object code, Object globals, Object name, Object defaultArgs, Object closure) {
            throw raise(TypeError, ErrorMessages.FUNC_CONSTRUCTION_NOT_SUPPORTED, cls, code, globals, name, defaultArgs, closure);
        }
    }

    // builtin-function(method-def, self, module)
    @Builtin(name = "method_descriptor", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = PythonBuiltinClassType.PBuiltinFunction, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinFunctionNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        public PFunction function(Object cls, Object method_def, Object def, Object name, Object module) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'method_descriptor'");
        }
    }

    // type(object, bases, dict)
    @Builtin(name = J_TYPE, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, needsFrame = true, constructsClass = PythonBuiltinClassType.PythonClass)
    @GenerateNodeFactory
    public abstract static class TypeNode extends PythonVarargsBuiltinNode {
        @Child private IsSubtypeNode isSubtypeNode;
        @Child private IsAcceptableBaseNode isAcceptableBaseNode;

        public abstract Object execute(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        @Override
        public final Object execute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            if (arguments.length == 3) {
                return execute(frame, self, arguments[0], arguments[1], arguments[2], keywords);
            } else {
                throw raise(TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "type.__new__", 3, arguments.length);
            }
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            if (arguments.length == 4) {
                return execute(frame, arguments[0], arguments[1], arguments[2], arguments[3], keywords);
            } else if (arguments.length == 3) {
                return execute(frame, self, arguments[0], arguments[1], arguments[2], keywords);
            } else {
                throw raise(TypeError, ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "type.__new__", 3, arguments.length);
            }
        }

        @Specialization(guards = "isString(wName)")
        @SuppressWarnings("truffle-static-method")
        Object typeNew(VirtualFrame frame, Object cls, Object wName, PTuple bases, PDict namespaceOrig, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached("create(New)") LookupCallableSlotInMRONode getNewFuncNode,
                        @Cached TypeBuiltins.BindNew bindNew,
                        @Shared @Cached IsTypeNode isTypeNode,
                        @Cached PyObjectLookupAttr lookupMroEntriesNode,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached CallNode callNewFuncNode,
                        @Cached CreateTypeNode createType,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            // Determine the proper metatype to deal with this
            TruffleString name = castStr.execute(wName);
            Object metaclass = cls;
            Object winner = calculateMetaclass(frame, inliningTarget, metaclass, bases, getClassNode, isTypeNode, lookupMroEntriesNode, getObjectArrayNode);
            if (winner != metaclass) {
                Object newFunc = getNewFuncNode.execute(winner);
                if (newFunc instanceof PBuiltinMethod && (((PBuiltinMethod) newFunc).getBuiltinFunction().getFunctionRootNode().getCallTarget() == getRootNode().getCallTarget())) {
                    metaclass = winner;
                    // the new metaclass has the same __new__ function as we are in, continue
                } else {
                    // Pass it to the winner
                    return callNewFuncNode.execute(frame, bindNew.execute(frame, newFunc, winner), new Object[]{winner, name, bases, namespaceOrig}, kwds);
                }
            }

            return createType.execute(frame, namespaceOrig, name, bases, metaclass, kwds);
        }

        @Fallback
        Object generic(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object name, Object bases, Object namespace, @SuppressWarnings("unused") PKeyword[] kwds) {
            if (!(bases instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 2, "tuple", bases);
            } else if (!(namespace instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 3, "dict", bases);
            } else {
                throw CompilerDirectives.shouldNotReachHere("type fallback reached incorrectly");
            }
        }

        private Object calculateMetaclass(VirtualFrame frame, Node inliningTarget, Object cls, PTuple bases, InlinedGetClassNode getClassNode, IsTypeNode isTypeNode,
                        PyObjectLookupAttr lookupMroEntries, GetObjectArrayNode getObjectArrayNode) {
            Object winner = cls;
            for (Object base : getObjectArrayNode.execute(inliningTarget, bases)) {
                if (!isTypeNode.execute(base) && lookupMroEntries.execute(frame, base, T___MRO_ENTRIES__) != PNone.NO_VALUE) {
                    throw raise(TypeError, ErrorMessages.TYPE_DOESNT_SUPPORT_MRO_ENTRY_RESOLUTION);
                }
                if (!ensureIsAcceptableBaseNode().execute(base)) {
                    throw raise(TypeError, ErrorMessages.TYPE_IS_NOT_ACCEPTABLE_BASE_TYPE, base);
                }
                Object typ = getClassNode.execute(inliningTarget, base);
                if (isSubType(frame, winner, typ)) {
                    continue;
                } else if (isSubType(frame, typ, winner)) {
                    winner = typ;
                    continue;
                }
                throw raise(TypeError, ErrorMessages.METACLASS_CONFLICT);
            }
            return winner;
        }

        protected boolean isSubType(VirtualFrame frame, Object subclass, Object superclass) {
            if (isSubtypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubtypeNode = insert(IsSubtypeNode.create());
            }
            return isSubtypeNode.execute(frame, subclass, superclass);
        }

        @NeverDefault
        public static TypeNode create() {
            return BuiltinConstructorsFactory.TypeNodeFactory.create();
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        Object typeGeneric(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
                        @Cached TypeNode nextTypeNode,
                        @Shared @Cached IsTypeNode isTypeNode) {
            if (!(name instanceof TruffleString || name instanceof PString)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 1", name);
            } else if (!(bases instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 2", bases);
            } else if (!(dict instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 3", dict);
            } else if (!isTypeNode.execute(cls)) {
                // TODO: this is actually allowed, deal with it
                throw raise(NotImplementedError, ErrorMessages.CREATING_CLASS_NON_CLS_META_CLS);
            }
            return nextTypeNode.execute(frame, cls, name, bases, dict, kwds);
        }

        private IsAcceptableBaseNode ensureIsAcceptableBaseNode() {
            if (isAcceptableBaseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isAcceptableBaseNode = insert(IsAcceptableBaseNode.create());
            }
            return isAcceptableBaseNode;
        }
    }

    @Builtin(name = J_MODULE, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonModule, isPublic = false, //
                    doc = "module(name, doc=None)\n" +
                                    "--\n" +
                                    "\n" +
                                    "Create a module object.\n" +
                                    "\n" +
                                    "The name must be a string; the optional doc argument can have any type.")
    @GenerateNodeFactory
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

        @Specialization(guards = "isTypeNode.execute(self)", limit = "1")
        @SuppressWarnings("unused")
        Object doNative(PythonAbstractNativeObject self, Object[] varargs, PKeyword[] kwargs,
                        @Cached IsTypeNode isTypeNode) {
            return factory().createPythonModule(self);
        }
    }

    @Builtin(name = "NotImplementedType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PNotImplemented, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NotImplementedTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public static PNotImplemented module(Object cls) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "ellipsis", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PEllipsis, isPublic = false)
    @GenerateNodeFactory
    public abstract static class EllipsisTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public static PEllipsis call(Object cls) {
            return PEllipsis.INSTANCE;
        }
    }

    @Builtin(name = "NoneType", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PNone, isPublic = false)
    @GenerateNodeFactory
    public abstract static class NoneTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public static PNone module(Object cls) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J_DICT_KEYS, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeysView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, J_DICT_KEYS);
        }
    }

    @Builtin(name = J_DICT_KEYITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeyIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, J_DICT_KEYITERATOR);
        }
    }

    @Builtin(name = J_DICT_VALUES, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValuesView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, J_DICT_VALUES);
        }
    }

    @Builtin(name = J_DICT_VALUEITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValueIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, J_DICT_VALUEITERATOR);
        }
    }

    @Builtin(name = J_DICT_ITEMS, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemsView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, J_DICT_ITEMS);
        }
    }

    @Builtin(name = J_DICT_ITEMITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, J_DICT_ITEMITERATOR);
        }
    }

    @Builtin(name = "iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class IteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, className());
        }

        protected String className() {
            return "iterator";
        }
    }

    @Builtin(name = "arrayiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PArrayIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ArrayIteratorTypeNode extends IteratorTypeNode {
        @Override
        protected String className() {
            return "arrayiterator";
        }
    }

    @Builtin(name = "callable_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PSentinelIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CallableIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'callable_iterator'");
        }
    }

    @Builtin(name = "foreign_iterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PForeignArrayIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ForeignIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object iterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'foreign_iterator'");
        }
    }

    @Builtin(name = "generator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PGenerator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class GeneratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object generator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "'generator'");
        }
    }

    @Builtin(name = "method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PMethod, isPublic = false)
    @GenerateNodeFactory
    public abstract static class MethodTypeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object method(Object cls, PFunction func, Object self) {
            return factory().createMethod(cls, self, func);
        }

        @Specialization
        Object methodBuiltin(@SuppressWarnings("unused") Object cls, PBuiltinFunction func, Object self) {
            return factory().createMethod(self, func);
        }

        @Specialization
        Object methodGeneric(@SuppressWarnings("unused") Object cls, Object func, Object self,
                        @Cached PyCallableCheckNode callableCheck) {
            if (callableCheck.execute(func)) {
                return factory().createMethod(self, func);
            } else {
                throw raise(TypeError, ErrorMessages.FIRST_ARG_MUST_BE_CALLABLE_S, "");
            }
        }
    }

    @Builtin(name = "builtin_function_or_method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuiltinFunctionOrMethod, isPublic = false)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodTypeNode extends PythonBuiltinNode {
        @Specialization
        Object method(Object cls, Object self, PBuiltinFunction func) {
            return factory().createBuiltinMethod(cls, self, func);
        }
    }

    @Builtin(name = "frame", constructsClass = PythonBuiltinClassType.PFrame, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FrameTypeNode extends PythonBuiltinNode {
        @Specialization
        Object call() {
            throw raise(RuntimeError, ErrorMessages.CANNOT_CALL_CTOR_OF, "frame type");
        }
    }

    @Builtin(name = "TracebackType", constructsClass = PythonBuiltinClassType.PTraceback, isPublic = false, minNumOfPositionalArgs = 5, parameterNames = {"$cls", "tb_next", "tb_frame", "tb_lasti",
                    "tb_lineno"})
    @ArgumentClinic(name = "tb_lasti", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "tb_lineno", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    public abstract static class TracebackTypeNode extends PythonClinicBuiltinNode {
        @Specialization
        static Object createTraceback(@SuppressWarnings("unused") Object cls, PTraceback next, PFrame pframe, int lasti, int lineno,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTraceback(pframe, lineno, lasti, next);
        }

        @Specialization
        static Object createTraceback(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") PNone next, PFrame pframe, int lasti, int lineno,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTraceback(pframe, lineno, lasti, null);
        }

        @Specialization(guards = {"!isPTraceback(next)", "!isNone(next)"})
        @SuppressWarnings("unused")
        Object errorNext(Object cls, Object next, Object frame, Object lasti, Object lineno) {
            throw raise(TypeError, ErrorMessages.EXPECTED_TRACEBACK_OBJ_OR_NONE, next);
        }

        @Specialization(guards = "!isPFrame(frame)")
        @SuppressWarnings("unused")
        Object errorFrame(Object cls, Object next, Object frame, Object lasti, Object lineno) {
            throw raise(TypeError, ErrorMessages.TRACEBACK_TYPE_ARG_MUST_BE_FRAME, frame);
        }

        protected static boolean isPFrame(Object obj) {
            return obj instanceof PFrame;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinConstructorsClinicProviders.TracebackTypeNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "code", constructsClass = PythonBuiltinClassType.PCode, isPublic = false, minNumOfPositionalArgs = 15, numOfPositionalOnlyArgs = 17, parameterNames = {
                    "$cls", "argcount", "posonlyargcount", "kwonlyargcount", "nlocals", "stacksize", "flags", "codestring", "constants", "names", "varnames", "filename", "name", "firstlineno",
                    "linetable", "freevars", "cellvars"})
    @ArgumentClinic(name = "argcount", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "posonlyargcount", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "kwonlyargcount", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "nlocals", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "stacksize", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "filename", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "firstlineno", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CodeConstructorNode extends PythonClinicBuiltinNode {
        @Specialization
        PCode call(VirtualFrame frame, @SuppressWarnings("unused") Object cls, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        PBytes codestring, PTuple constants, PTuple names,
                        PTuple varnames, TruffleString filename, TruffleString name,
                        int firstlineno, PBytes linetable,
                        PTuple freevars, PTuple cellvars,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached GetObjectArrayNode getObjectArrayNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            byte[] codeBytes = bufferLib.getCopiedByteArray(codestring);
            byte[] linetableBytes = bufferLib.getCopiedByteArray(linetable);

            Object[] constantsArr = getObjectArrayNode.execute(inliningTarget, constants);
            TruffleString[] namesArr = objectArrayToTruffleStringArray(getObjectArrayNode.execute(inliningTarget, names), castToTruffleStringNode);
            TruffleString[] varnamesArr = objectArrayToTruffleStringArray(getObjectArrayNode.execute(inliningTarget, varnames), castToTruffleStringNode);
            TruffleString[] freevarsArr = objectArrayToTruffleStringArray(getObjectArrayNode.execute(inliningTarget, freevars), castToTruffleStringNode);
            TruffleString[] cellcarsArr = objectArrayToTruffleStringArray(getObjectArrayNode.execute(inliningTarget, cellvars), castToTruffleStringNode);

            return createCodeNode.execute(frame, argcount, posonlyargcount, kwonlyargcount,
                            nlocals, stacksize, flags,
                            codeBytes, constantsArr, namesArr,
                            varnamesArr, freevarsArr, cellcarsArr,
                            filename, name, firstlineno,
                            linetableBytes);
        }

        @Fallback
        @SuppressWarnings("unused")
        PCode call(Object cls, Object argcount, Object kwonlyargcount, Object posonlyargcount,
                        Object nlocals, Object stacksize, Object flags,
                        Object codestring, Object constants, Object names,
                        Object varnames, Object filename, Object name,
                        Object firstlineno, Object linetable,
                        Object freevars, Object cellvars) {
            throw raise(TypeError, ErrorMessages.INVALID_ARGS, "code");
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BuiltinConstructorsClinicProviders.CodeConstructorNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "cell", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PCell, isPublic = false)
    @GenerateNodeFactory
    public abstract static class CellTypeNode extends PythonBinaryBuiltinNode {
        @CompilationFinal private Assumption sharedAssumption;

        private Assumption getAssumption() {
            if (sharedAssumption == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sharedAssumption = Truffle.getRuntime().createAssumption("cell is effectively final");
            }
            if (CompilerDirectives.inCompiledCode()) {
                return sharedAssumption;
            } else {
                return Truffle.getRuntime().createAssumption("cell is effectively final");
            }
        }

        @Specialization(guards = "isNoValue(contents)")
        Object newCellEmpty(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object contents) {
            return factory().createCell(getAssumption());
        }

        @Specialization(guards = "!isNoValue(contents)")
        Object newCell(@SuppressWarnings("unused") Object cls, Object contents) {
            Assumption assumption = getAssumption();
            PCell cell = factory().createCell(assumption);
            cell.setRef(contents, assumption);
            return cell;
        }
    }

    @Builtin(name = "BaseException", constructsClass = PythonBuiltinClassType.PBaseException, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class BaseExceptionNode extends PythonVarargsBuiltinNode {
        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
            }
            if (arguments.length == 1) {
                return execute(frame, arguments[0], PythonUtils.EMPTY_OBJECT_ARRAY, keywords);
            }
            Object[] argsWithoutSelf = PythonUtils.arrayCopyOfRange(arguments, 1, arguments.length);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }

        @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
        static Object doManaged(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached InlinedConditionProfile argsProfile) {
            if (argsProfile.profile(inliningTarget, args.length == 0)) {
                return factory.createBaseException(cls);
            } else {
                return factory.createBaseException(cls, factory.createTuple(args));
            }
        }

        @Specialization(guards = "needsNativeAllocationNode.execute(inliningTarget, cls)", limit = "1")
        static Object doNativeSubtype(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached PythonToNativeNode toNativeNode,
                        @Cached NativeToPythonNode toPythonNode,
                        @Cached ExternalFunctionNodes.DefaultCheckFunctionResultNode checkFunctionResultNode) {
            Object argsTuple = args.length > 0 ? factory.createTuple(args) : factory.createEmptyTuple();
            Object nativeResult = callCapiFunction.call(NativeCAPISymbol.FUN_EXCEPTION_SUBTYPE_NEW, toNativeNode.execute(cls), toNativeNode.execute(argsTuple));
            return toPythonNode.execute(checkFunctionResultNode.execute(PythonContext.get(inliningTarget), NativeCAPISymbol.FUN_EXCEPTION_SUBTYPE_NEW.getTsName(), nativeResult));
        }
    }

    @Builtin(name = "mappingproxy", constructsClass = PythonBuiltinClassType.PMappingproxy, isPublic = false, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MappingproxyNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!isNoValue(obj)")
        Object doMapping(Object klass, Object obj,
                        @Cached PyMappingCheckNode mappingCheckNode) {
            // descrobject.c mappingproxy_check_mapping()
            if (!(obj instanceof PList || obj instanceof PTuple) && mappingCheckNode.execute(obj)) {
                return factory().createMappingproxy(klass, obj);
            }
            throw raise(TypeError, ErrorMessages.S_ARG_MUST_BE_S_NOT_P, "mappingproxy()", "mapping", obj);
        }

        @Specialization(guards = "isNoValue(none)")
        @SuppressWarnings("unused")
        Object doMissing(Object klass, PNone none) {
            throw raise(TypeError, ErrorMessages.MISSING_D_REQUIRED_S_ARGUMENT_S_POS, "mappingproxy()", "mapping", 1);
        }
    }

    abstract static class DescriptorNode extends PythonBuiltinNode {
        @TruffleBoundary
        protected final void denyInstantiationAfterInitialization(TruffleString name) {
            if (getCore().isCoreInitialized()) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, name);
            }
        }

        protected static Object ensure(Object value) {
            return value == PNone.NO_VALUE ? null : value;
        }
    }

    @Builtin(name = J_GETSET_DESCRIPTOR, constructsClass = PythonBuiltinClassType.GetSetDescriptor, isPublic = false, minNumOfPositionalArgs = 1, //
                    parameterNames = {"cls", "fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class GetSetDescriptorNode extends DescriptorNode {
        @Specialization(guards = "isPythonClass(owner)")
        @TruffleBoundary
        Object call(@SuppressWarnings("unused") Object clazz, Object get, Object set, TruffleString name, Object owner) {
            denyInstantiationAfterInitialization(T_GETSET_DESCRIPTOR);
            return PythonObjectFactory.getUncached().createGetSetDescriptor(ensure(get), ensure(set), name, owner);
        }
    }

    @Builtin(name = J_MEMBER_DESCRIPTOR, constructsClass = PythonBuiltinClassType.MemberDescriptor, isPublic = false, minNumOfPositionalArgs = 1, //
                    parameterNames = {"cls", "fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class MemberDescriptorNode extends DescriptorNode {
        @Specialization(guards = "isPythonClass(owner)")
        @TruffleBoundary
        Object doGeneric(@SuppressWarnings("unused") Object clazz, Object get, Object set, TruffleString name, Object owner) {
            denyInstantiationAfterInitialization(T_MEMBER_DESCRIPTOR);
            return PythonObjectFactory.getUncached().createGetSetDescriptor(ensure(get), ensure(set), name, owner);
        }
    }

    @Builtin(name = J_WRAPPER_DESCRIPTOR, constructsClass = PythonBuiltinClassType.WrapperDescriptor, isPublic = false, minNumOfPositionalArgs = 1, //
                    parameterNames = {"cls", "fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class WrapperDescriptorNode extends DescriptorNode {
        @Specialization(guards = "isPythonClass(owner)")
        @TruffleBoundary
        Object doGeneric(@SuppressWarnings("unused") Object clazz, Object get, Object set, TruffleString name, Object owner) {
            denyInstantiationAfterInitialization(T_WRAPPER_DESCRIPTOR);
            return PythonObjectFactory.getUncached().createGetSetDescriptor(ensure(get), ensure(set), name, owner);
        }
    }

    // slice(stop)
    // slice(start, stop[, step])
    @Builtin(name = "slice", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PSlice)
    @GenerateNodeFactory
    abstract static class SliceNode extends PythonQuaternaryBuiltinNode {
        @Specialization(guards = {"isNoValue(second)"})
        @SuppressWarnings("unused")
        static Object singleArg(Object cls, Object first, Object second, Object third,
                        @Shared @Cached PySliceNew sliceNode) {
            return sliceNode.execute(PNone.NONE, first, PNone.NONE);
        }

        @Specialization(guards = {"!isNoValue(stop)", "isNoValue(step)"})
        @SuppressWarnings("unused")
        static Object twoArgs(Object cls, Object start, Object stop, Object step,
                        @Shared @Cached PySliceNew sliceNode) {
            return sliceNode.execute(start, stop, PNone.NONE);
        }

        @Fallback
        static Object threeArgs(@SuppressWarnings("unused") Object cls, Object start, Object stop, Object step,
                        @Shared @Cached PySliceNew sliceNode) {
            return sliceNode.execute(start, stop, step);
        }
    }

    // memoryview(obj)
    @Builtin(name = J_MEMORYVIEW, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "object"}, constructsClass = PythonBuiltinClassType.PMemoryView)
    @GenerateNodeFactory
    public abstract static class MemoryViewNode extends PythonBuiltinNode {

        public abstract PMemoryView execute(VirtualFrame frame, Object cls, Object object);

        public final PMemoryView execute(VirtualFrame frame, Object object) {
            return execute(frame, PythonBuiltinClassType.PMemoryView, object);
        }

        @Specialization
        PMemoryView fromObject(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object object,
                        @Cached PyMemoryViewFromObject memoryViewFromObject) {
            return memoryViewFromObject.execute(frame, object);
        }

        @NeverDefault
        public static MemoryViewNode create() {
            return BuiltinConstructorsFactory.MemoryViewNodeFactory.create(null);
        }
    }

    // super()
    @Builtin(name = J_SUPER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.Super)
    @GenerateNodeFactory
    public abstract static class SuperInitNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object object) {
            return factory().createSuperObject(self);
        }
    }

    @Builtin(name = J_CLASSMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PClassmethod, doc = "classmethod(function) -> method\n" +
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
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createClassmethod(self);
        }
    }

    @Builtin(name = J_INSTANCEMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PInstancemethod, isPublic = false, doc = "instancemethod(function)\n\nBind a function to a class.")
    @GenerateNodeFactory
    public abstract static class InstancemethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createInstancemethod(self);
        }
    }

    @Builtin(name = J_STATICMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PStaticmethod)
    @GenerateNodeFactory
    public abstract static class StaticmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createStaticmethod(self);
        }
    }

    @Builtin(name = J_MAP, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PMap)
    @GenerateNodeFactory
    public abstract static class MapNode extends PythonVarargsBuiltinNode {
        @Specialization
        PMap doit(Object self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createMap(self);
        }
    }

    @Builtin(name = J_PROPERTY, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PProperty)
    @GenerateNodeFactory
    public abstract static class PropertyNode extends PythonVarargsBuiltinNode {
        @Specialization
        PProperty doit(Object self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createProperty(self);
        }
    }

    @Builtin(name = "SimpleNamespace", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, isPublic = false, constructsClass = PythonBuiltinClassType.PSimpleNamespace, doc = "A simple attribute-based namespace.\n" +
                    "\n" +
                    "SimpleNamespace(**kwargs)")
    @GenerateNodeFactory
    public abstract static class SimpleNamespaceNode extends PythonVarargsBuiltinNode {
        @Specialization
        PSimpleNamespace doit(Object self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createSimpleNamespace(self);
        }
    }

    @Builtin(name = "GenericAlias", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PGenericAlias)
    @GenerateNodeFactory
    abstract static class GenericAliasNode extends PythonTernaryBuiltinNode {
        @Specialization
        PGenericAlias doit(Object cls, Object origin, Object arguments) {
            return factory().createGenericAlias(cls, origin, arguments);
        }
    }
}
