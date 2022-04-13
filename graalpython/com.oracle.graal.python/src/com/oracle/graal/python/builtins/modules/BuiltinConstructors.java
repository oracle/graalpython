/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_ADD_NATIVE_SLOTS;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_OBJECT_NEW;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.canEncodeUTF8;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.containsNullCharacter;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_ITEMSIZE;
import static com.oracle.graal.python.nodes.BuiltinNames.BOOL;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.BYTES;
import static com.oracle.graal.python.nodes.BuiltinNames.CLASSMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.COMPLEX;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_ITEMITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_ITEMS;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_KEYITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_KEYS;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_VALUEITERATOR;
import static com.oracle.graal.python.nodes.BuiltinNames.DICT_VALUES;
import static com.oracle.graal.python.nodes.BuiltinNames.ENUMERATE;
import static com.oracle.graal.python.nodes.BuiltinNames.FLOAT;
import static com.oracle.graal.python.nodes.BuiltinNames.FROZENSET;
import static com.oracle.graal.python.nodes.BuiltinNames.GETSET_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.INSTANCEMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.INT;
import static com.oracle.graal.python.nodes.BuiltinNames.LIST;
import static com.oracle.graal.python.nodes.BuiltinNames.MAP;
import static com.oracle.graal.python.nodes.BuiltinNames.MEMBER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.MEMORYVIEW;
import static com.oracle.graal.python.nodes.BuiltinNames.MODULE;
import static com.oracle.graal.python.nodes.BuiltinNames.OBJECT;
import static com.oracle.graal.python.nodes.BuiltinNames.PROPERTY;
import static com.oracle.graal.python.nodes.BuiltinNames.RANGE;
import static com.oracle.graal.python.nodes.BuiltinNames.REVERSED;
import static com.oracle.graal.python.nodes.BuiltinNames.SET;
import static com.oracle.graal.python.nodes.BuiltinNames.STATICMETHOD;
import static com.oracle.graal.python.nodes.BuiltinNames.STR;
import static com.oracle.graal.python.nodes.BuiltinNames.SUPER;
import static com.oracle.graal.python.nodes.BuiltinNames.TUPLE;
import static com.oracle.graal.python.nodes.BuiltinNames.TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.WRAPPER_DESCRIPTOR;
import static com.oracle.graal.python.nodes.BuiltinNames.ZIP;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_NOT_BE_ZERO;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_CALLING_SET_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ABSTRACTMETHODS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASSCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__WEAKREF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.DECODE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__COMPLEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUNC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins.GetWeakRefsNode;
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
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.enumerate.PEnumerate;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PZip;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.map.PMap;
import com.oracle.graal.python.builtins.objects.memoryview.PBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.namespace.PSimpleNamespace;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.DictNodeGen;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.property.PProperty;
import com.oracle.graal.python.builtins.objects.range.PBigRange;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.range.RangeNodes;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfIntRangeNodeExact;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.IsIdentifierNode;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBestBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetItemsizeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsAcceptableBaseNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.CanBeDoubleNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatFromString;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetAnyAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedSlotNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode.StandaloneBuiltinFactory;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.parser.PythonSSTNodeFactory;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = BuiltinNames.BUILTINS, isEager = true)
public final class BuiltinConstructors extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinConstructorsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        builtinConstants.put("NotImplemented", PNotImplemented.NOT_IMPLEMENTED);
    }

    // bytes([source[, encoding[, errors]]])
    @Builtin(name = BYTES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "source", "encoding", "errors"}, constructsClass = PythonBuiltinClassType.PBytes)
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
        PBytes doEmpty(VirtualFrame frame, Object cls, PNone source, PNone encoding, PNone errors) {
            return factory().createBytes(cls, PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @Specialization(guards = "!isNoValue(source)")
        PBytes doCallBytes(VirtualFrame frame, Object cls, Object source, PNone encoding, PNone errors,
                        @Cached GetClassNode getClassNode,
                        @Cached ConditionProfile hasBytes,
                        @Cached("create(Bytes)") LookupSpecialMethodSlotNode lookupBytes,
                        @Cached CallUnaryMethodNode callBytes,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached ConditionProfile isBytes,
                        @Cached BytesNodes.BytesInitNode bytesInitNode) {
            Object bytesMethod = lookupBytes.execute(frame, getClassNode.execute(source), source);
            if (hasBytes.profile(bytesMethod != PNone.NO_VALUE)) {
                Object bytes = callBytes.executeObject(frame, bytesMethod, source);
                if (isBytes.profile(bytes instanceof PBytes)) {
                    if (cls == PythonBuiltinClassType.PBytes) {
                        return (PBytes) bytes;
                    } else {
                        return factory().createBytes(cls, toBytesNode.execute(frame, bytes));
                    }
                } else {
                    throw raise(TypeError, ErrorMessages.RETURNED_NONBYTES, __BYTES__, bytes);
                }
            }
            return factory().createBytes(cls, bytesInitNode.execute(frame, source, encoding, errors));
        }

        @Specialization(guards = {"isNoValue(source) || (!isNoValue(encoding) || !isNoValue(errors))"})
        PBytes dontCallBytes(VirtualFrame frame, Object cls, Object source, Object encoding, Object errors,
                        @Cached BytesNodes.BytesInitNode bytesInitNode) {
            return factory().createBytes(cls, bytesInitNode.execute(frame, source, encoding, errors));
        }
    }

    @Builtin(name = BYTEARRAY, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PByteArray)
    @GenerateNodeFactory
    public abstract static class ByteArrayNode extends PythonBuiltinNode {
        @Specialization
        public PByteArray setEmpty(Object cls, @SuppressWarnings("unused") Object arg) {
            // data filled in subsequent __init__ call - see BytesBuiltins.InitNode
            return factory().createByteArray(cls, PythonUtils.EMPTY_BYTE_ARRAY);
        }
    }

    // complex([real[, imag]])
    @Builtin(name = COMPLEX, minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PComplex, parameterNames = {"$cls", "real",
                    "imag"}, doc = "complex(real[, imag]) -> complex number\n\n" +
                                    "Create a complex number from a real part and an optional imaginary part.\n" +
                                    "This is equivalent to (real + imag*1j) where imag defaults to 0.")
    @GenerateNodeFactory
    public abstract static class ComplexNode extends PythonTernaryBuiltinNode {

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();
        @Child private IsBuiltinClassProfile isComplexTypeProfile;
        @Child private IsBuiltinClassProfile isResultComplexTypeProfile;
        @Child private CanBeDoubleNode canBeDoubleNode;
        @Child private PyFloatAsDoubleNode asDoubleNode;
        @Child private LookupAndCallUnaryNode callReprNode;
        @Child private LookupAndCallUnaryNode callComplexNode;
        @Child private WarnNode warnNode;

        private PComplex createComplex(Object cls, double real, double imaginary) {
            if (isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PComplex)) {
                return factory().createComplex(real, imaginary);
            }
            return factory().createComplex(cls, real, imaginary);
        }

        private PComplex createComplex(Object cls, PComplex value) {
            if (isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PComplex)) {
                if (isPrimitiveProfile.profileObject(value, PythonBuiltinClassType.PComplex)) {
                    return value;
                }
                return factory().createComplex(value.getReal(), value.getImag());
            }
            return factory().createComplex(cls, value.getReal(), value.getImag());
        }

        @Specialization(guards = {"isNoValue(real)", "isNoValue(imag)"})
        @SuppressWarnings("unused")
        PComplex complexFromNone(Object cls, PNone real, PNone imag) {
            return createComplex(cls, 0, 0);
        }

        @Specialization
        PComplex complexFromIntInt(Object cls, int real, int imaginary) {
            return createComplex(cls, real, imaginary);
        }

        @Specialization
        PComplex complexFromLongLong(Object cls, long real, long imaginary) {
            return createComplex(cls, real, imaginary);
        }

        @Specialization
        PComplex complexFromLongLong(Object cls, PInt real, PInt imaginary) {
            return createComplex(cls, real.doubleValueWithOverflow(getRaiseNode()), imaginary.doubleValueWithOverflow(getRaiseNode()));
        }

        @Specialization
        PComplex complexFromDoubleDouble(Object cls, double real, double imaginary) {
            return createComplex(cls, real, imaginary);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromDouble(Object cls, double real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real, 0);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromDouble(VirtualFrame frame, Object cls, PFloat real, @SuppressWarnings("unused") PNone imag) {
            return complexFromObject(frame, cls, real, imag);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromInt(Object cls, int real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real, 0);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromLong(Object cls, long real, @SuppressWarnings("unused") PNone imag) {
            return createComplex(cls, real, 0);
        }

        @Specialization(guards = "isNoValue(imag)")
        PComplex complexFromLong(VirtualFrame frame, Object cls, PInt real, @SuppressWarnings("unused") PNone imag) {
            return complexFromObject(frame, cls, real, imag);
        }

        @Specialization(guards = {"isNoValue(imag)", "!isNoValue(number)", "!isString(number)"})
        PComplex complexFromObject(VirtualFrame frame, Object cls, Object number, @SuppressWarnings("unused") PNone imag) {
            PComplex value = getComplexNumberFromObject(frame, number);
            if (value == null) {
                if (canBeDouble(number)) {
                    return createComplex(cls, asDouble(frame, number), 0.0);
                } else {
                    throw raiseFirstArgError(number);
                }
            }
            return createComplex(cls, value);
        }

        @Specialization
        PComplex complexFromLongComplex(Object cls, long one, PComplex two) {
            return createComplex(cls, one - two.getImag(), two.getReal());
        }

        @Specialization
        PComplex complexFromPIntComplex(Object cls, PInt one, PComplex two) {
            return createComplex(cls, one.doubleValueWithOverflow(getRaiseNode()) - two.getImag(), two.getReal());
        }

        @Specialization
        PComplex complexFromDoubleComplex(Object cls, double one, PComplex two) {
            return createComplex(cls, one - two.getImag(), two.getReal());
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexLong(VirtualFrame frame, Object cls, Object one, long two) {
            PComplex value = getComplexNumberFromObject(frame, one);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one), two);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two);
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexDouble(VirtualFrame frame, Object cls, Object one, double two) {
            PComplex value = getComplexNumberFromObject(frame, one);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one), two);
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two);
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexPInt(VirtualFrame frame, Object cls, Object one, PInt two) {
            PComplex value = getComplexNumberFromObject(frame, one);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one), two.doubleValueWithOverflow(getRaiseNode()));
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal(), value.getImag() + two.doubleValueWithOverflow(getRaiseNode()));
        }

        @Specialization(guards = "!isString(one)")
        PComplex complexFromComplexComplex(VirtualFrame frame, Object cls, Object one, PComplex two) {
            PComplex value = getComplexNumberFromObject(frame, one);
            if (value == null) {
                if (canBeDouble(one)) {
                    return createComplex(cls, asDouble(frame, one) - two.getImag(), two.getReal());
                } else {
                    throw raiseFirstArgError(one);
                }
            }
            return createComplex(cls, value.getReal() - two.getImag(), value.getImag() + two.getReal());
        }

        @Specialization(guards = {"!isString(one)", "!isNoValue(two)", "!isPComplex(two)"})
        PComplex complexFromComplexObject(VirtualFrame frame, Object cls, Object one, Object two) {
            PComplex oneValue = getComplexNumberFromObject(frame, one);
            if (canBeDouble(two)) {
                double twoValue = asDouble(frame, two);
                if (oneValue == null) {
                    if (canBeDouble(one)) {
                        return createComplex(cls, asDouble(frame, one), twoValue);
                    } else {
                        throw raiseFirstArgError(one);
                    }
                }
                return createComplex(cls, oneValue.getReal(), oneValue.getImag() + twoValue);
            } else {
                throw raiseSecondArgError(two);
            }
        }

        @Specialization
        PComplex complexFromString(VirtualFrame frame, Object cls, String real, Object imaginary) {
            if (imaginary != PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(frame, real, cls, real);
        }

        @Specialization
        PComplex complexFromString(VirtualFrame frame, Object cls, PString real, Object imaginary) {
            if (imaginary != PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.COMPLEX_CANT_TAKE_ARG);
            }
            return convertStringToComplex(frame, real.getValue(), cls, real);
        }

        private IsBuiltinClassProfile getIsComplexTypeProfile() {
            if (isComplexTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isComplexTypeProfile = insert(IsBuiltinClassProfile.create());
            }
            return isComplexTypeProfile;
        }

        private IsBuiltinClassProfile getIsResultComplexTypeProfile() {
            if (isResultComplexTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isResultComplexTypeProfile = insert(IsBuiltinClassProfile.create());
            }
            return isResultComplexTypeProfile;
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
                callComplexNode = insert(LookupAndCallUnaryNode.create(__COMPLEX__));
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

        private PComplex getComplexNumberFromObject(VirtualFrame frame, Object object) {
            if (getIsComplexTypeProfile().profileObject(object, PythonBuiltinClassType.PComplex)) {
                return (PComplex) object;
            } else {
                Object result = callComplex(frame, object);
                if (result instanceof PComplex) {
                    if (!getIsResultComplexTypeProfile().profileObject(result, PythonBuiltinClassType.PComplex)) {
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
            return createComplex(cls, x, y);
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
        public PDict dictEmpty(Object cls, Object[] args, PKeyword[] keywordArgs) {
            return factory().createDict(cls);
        }
    }

    // enumerate(iterable, start=0)
    @Builtin(name = ENUMERATE, minNumOfPositionalArgs = 2, parameterNames = {"cls", "iterable", "start"}, constructsClass = PythonBuiltinClassType.PEnumerate)
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
    @Builtin(name = REVERSED, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PReverseIterator)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class ReversedNode extends PythonBuiltinNode {

        @Specialization
        public PythonObject reversed(@SuppressWarnings("unused") Object cls, PIntRange range,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            int lstart = range.getIntStart();
            int lstop = range.getIntStop();
            int lstep = range.getIntStep();
            int ulen = lenOfRangeNode.executeInt(lstart, lstop, lstep);
            int new_stop = lstart - lstep;
            int new_start = new_stop + ulen * lstep;

            return factory().createIntRangeIterator(new_start, -lstep, ulen);
        }

        @Specialization
        @TruffleBoundary
        public PythonObject reversed(@SuppressWarnings("unused") Object cls, PBigRange range,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            BigInteger lstart = range.getBigIntegerStart();
            BigInteger lstop = range.getBigIntegerStop();
            BigInteger lstep = range.getBigIntegerStep();
            BigInteger ulen = lenOfRangeNode.execute(lstart, lstop, lstep);

            BigInteger new_stop = lstart.subtract(lstep);
            BigInteger new_start = new_stop.add(ulen.multiply(lstep));

            return factory().createBigRangeIterator(new_start, lstep.negate(), ulen);
        }

        @Specialization
        public PythonObject reversed(Object cls, PString value) {
            return factory().createStringReverseIterator(cls, value.getValue());
        }

        @Specialization
        public PythonObject reversed(Object cls, String value) {
            return factory().createStringReverseIterator(cls, value);
        }

        @Specialization(guards = {"!isString(sequence)", "!isPRange(sequence)"})
        public Object reversed(VirtualFrame frame, Object cls, Object sequence,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(Reversed)") LookupSpecialMethodSlotNode lookupReversed,
                        @Cached CallUnaryMethodNode callReversed,
                        @Cached("create(Len)") LookupAndCallUnaryNode lookupLen,
                        @Cached("create(GetItem)") LookupSpecialMethodSlotNode getItemNode,
                        @Cached ConditionProfile noReversedProfile,
                        @Cached ConditionProfile noGetItemProfile) {
            Object sequenceKlass = getClassNode.execute(sequence);
            Object reversed = lookupReversed.execute(frame, sequenceKlass, sequence);
            if (noReversedProfile.profile(reversed == PNone.NO_VALUE)) {
                Object getItem = getItemNode.execute(frame, sequenceKlass, sequence);
                if (noGetItemProfile.profile(getItem == PNone.NO_VALUE)) {
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
    @Builtin(name = FLOAT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFloat)
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class FloatNode extends PythonBinaryBuiltinNode {
        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        // Used for the recursive call
        protected abstract double executeDouble(VirtualFrame frame, PythonBuiltinClassType cls, Object arg) throws UnexpectedResultException;

        protected final boolean isPrimitiveFloat(Object cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PFloat);
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double floatFromDouble(@SuppressWarnings("unused") Object cls, double arg) {
            return arg;
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double floatFromInt(@SuppressWarnings("unused") Object cls, int arg) {
            return arg;
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double floatFromLong(@SuppressWarnings("unused") Object cls, long arg) {
            return arg;
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double floatFromBoolean(@SuppressWarnings("unused") Object cls, boolean arg) {
            return arg ? 1d : 0d;
        }

        @Specialization(guards = "isPrimitiveFloat(cls)")
        double floatFromString(VirtualFrame frame, @SuppressWarnings("unused") Object cls, String obj,
                        @Shared("fromString") @Cached PyFloatFromString fromString) {
            return fromString.execute(frame, obj);
        }

        @Specialization(guards = {"isPrimitiveFloat(cls)", "isNoValue(obj)"})
        double floatFromNoValue(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") PNone obj) {
            return 0.0;
        }

        @Specialization(guards = {"isPrimitiveFloat(cls)", "!isNoValue(obj)"}, replaces = "floatFromString")
        double floatFromObject(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object obj,
                        @Cached IsBuiltinClassProfile stringProfile,
                        @Shared("fromString") @Cached PyFloatFromString fromString,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            if (stringProfile.profileObject(obj, PythonBuiltinClassType.PString)) {
                return fromString.execute(frame, obj);
            }
            return pyNumberFloat.execute(frame, obj);
        }

        @Specialization(guards = {"!isNativeClass(cls)", "!isPrimitiveFloat(cls)"})
        Object floatFromObjectManagedSubclass(VirtualFrame frame, Object cls, Object obj,
                        @Cached FloatNode recursiveCallNode) {
            try {
                return factory().createFloat(cls, recursiveCallNode.executeDouble(frame, PythonBuiltinClassType.PFloat, obj));
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere("float() returned non-primitive value");
            }
        }

        // logic similar to float_subtype_new(PyTypeObject *type, PyObject *x) from CPython
        // floatobject.c we have to first create a temporary float, then fill it into
        // a natively allocated subtype structure
        @Specialization(guards = "isSubtypeOfFloat(frame, isSubtype, cls)", limit = "1")
        static Object floatFromObjectNativeSubclass(VirtualFrame frame, PythonNativeClass cls, Object obj,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached CExtNodes.FloatSubtypeNew subtypeNew,
                        @Cached FloatNode recursiveCallNode) {
            try {
                return subtypeNew.call(cls, recursiveCallNode.executeDouble(frame, PythonBuiltinClassType.PFloat, obj));
            } catch (UnexpectedResultException e) {
                throw CompilerDirectives.shouldNotReachHere("float() returned non-primitive value");
            }
        }

        protected static boolean isSubtypeOfFloat(VirtualFrame frame, IsSubtypeNode isSubtypeNode, PythonNativeClass cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PFloat);
        }
    }

    // frozenset([iterable])
    @Builtin(name = FROZENSET, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PFrozenSet)
    @GenerateNodeFactory
    public abstract static class FrozenSetNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(arg)")
        public PFrozenSet frozensetEmpty(Object cls, @SuppressWarnings("unused") PNone arg) {
            return factory().createFrozenSet(cls);
        }

        @Specialization(guards = "isBuiltinClass.profileIsAnyBuiltinClass(cls)")
        public static PFrozenSet frozensetIdentity(@SuppressWarnings("unused") Object cls, PFrozenSet arg,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
            return arg;
        }

        @Specialization(guards = "!isBuiltinClass.profileIsAnyBuiltinClass(cls)")
        public PFrozenSet subFrozensetIdentity(Object cls, PFrozenSet arg,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
            return factory().createFrozenSet(cls, arg.getDictStorage());
        }

        @Specialization(guards = {"!isNoValue(iterable)", "!isPFrozenSet(iterable)"})
        public PFrozenSet frozensetIterable(VirtualFrame frame, Object cls, Object iterable,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode) {
            HashingStorage storage = getHashingStorageNode.doNoValue(frame, iterable);
            return factory().createFrozenSet(cls, storage);
        }
    }

    // int(x=0)
    // int(x, base=10)
    @Builtin(name = INT, minNumOfPositionalArgs = 1, parameterNames = {"cls", "x", "base"}, numOfPositionalOnlyArgs = 2, constructsClass = PythonBuiltinClassType.PInt)
    @GenerateNodeFactory
    public abstract static class IntNode extends PythonTernaryBuiltinNode {

        private final ConditionProfile invalidBase = ConditionProfile.createBinaryProfile();
        private final BranchProfile invalidValueProfile = BranchProfile.create();
        private final BranchProfile bigIntegerProfile = BranchProfile.create();
        private final BranchProfile primitiveIntProfile = BranchProfile.create();
        private final BranchProfile fullIntProfile = BranchProfile.create();
        private final BranchProfile notSimpleDecimalLiteralProfile = BranchProfile.create();

        @Child private BytesNodes.ToBytesNode toByteArrayNode;
        @Child private LookupAndCallUnaryNode callIndexNode;
        @Child private LookupAndCallUnaryNode callTruncNode;
        @Child private LookupAndCallUnaryNode callReprNode;
        @Child private LookupAndCallUnaryNode callIntNode;
        @Child private WarnNode warnNode;

        public final Object executeWith(VirtualFrame frame, Object number) {
            return execute(frame, PythonBuiltinClassType.PInt, number, 10);
        }

        public final Object executeWith(VirtualFrame frame, Object number, Object base) {
            return execute(frame, PythonBuiltinClassType.PInt, number, base);
        }

        @TruffleBoundary
        private static Object stringToIntInternal(String num, int base) {
            try {
                BigInteger bi = asciiToBigInteger(num, base);
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

        private Object stringToInt(VirtualFrame frame, Object cls, String number, int base, Object origObj) {
            if (base == 0 || base == 10) {
                Object value = parseSimpleDecimalLiteral(number, 0, number.length());
                if (value != null) {
                    return createInt(cls, value);
                }
            }
            notSimpleDecimalLiteralProfile.enter();
            Object value = stringToIntInternal(number, base);
            if (value == null) {
                invalidValueProfile.enter();
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
            return createInt(cls, value);
        }

        private Object createInt(Object cls, Object value) {
            if (value instanceof BigInteger) {
                bigIntegerProfile.enter();
                return factory().createInt(cls, (BigInteger) value);
            } else if (isPrimitiveInt(cls)) {
                primitiveIntProfile.enter();
                return value;
            } else {
                fullIntProfile.enter();
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

        private void checkBase(int base) {
            if (invalidBase.profile((base < 2 || base > 36) && base != 0)) {
                throw raise(ValueError, ErrorMessages.BASE_OUT_OF_RANGE_FOR_INT);
            }
        }

        // Adapted from Jython
        private static BigInteger asciiToBigInteger(String str, int possibleBase) throws NumberFormatException {
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

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        protected boolean isPrimitiveInt(Object cls) {
            return isPrimitiveProfile.profileClass(cls, PythonBuiltinClassType.PInt);
        }

        @Specialization
        Object parseInt(Object cls, boolean arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg ? 1 : 0;
            } else {
                return factory().createInt(cls, arg ? 1 : 0);
            }
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, int arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg;
            }
            return factory().createInt(cls, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, long arg, @SuppressWarnings("unused") PNone base,
                        @Cached ConditionProfile isIntProfile) {
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
        Object createInt(Object cls, double arg, @SuppressWarnings("unused") PNone base,
                        @Cached("createFloatInt()") FloatBuiltins.IntNode floatToIntNode) {
            Object result = floatToIntNode.executeWithDouble(arg);
            return createInt(cls, result);
        }

        // String

        @Specialization(guards = "isNoValue(base)")
        @Megamorphic
        Object createInt(VirtualFrame frame, Object cls, String arg, @SuppressWarnings("unused") PNone base) {
            return stringToInt(frame, cls, arg, 10, arg);
        }

        @Specialization
        @Megamorphic
        Object parsePIntError(VirtualFrame frame, Object cls, String number, int base) {
            checkBase(base);
            return stringToInt(frame, cls, number, base, number);
        }

        @Specialization(guards = "!isNoValue(base)")
        @Megamorphic
        Object createIntError(VirtualFrame frame, Object cls, String number, Object base,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int intBase = asSizeNode.executeLossy(frame, base);
            checkBase(intBase);
            return stringToInt(frame, cls, number, intBase, number);
        }

        // PIBytesLike
        @Specialization
        @Megamorphic
        Object parseBytesError(VirtualFrame frame, Object cls, PBytesLike arg, int base) {
            checkBase(base);
            return stringToInt(frame, cls, toString(arg), base, arg);
        }

        @Specialization(guards = "isNoValue(base)")
        @Megamorphic
        Object parseBytesError(VirtualFrame frame, Object cls, PBytesLike arg, @SuppressWarnings("unused") PNone base) {
            return parseBytesError(frame, cls, arg, 10);
        }

        // PString
        @Specialization(guards = "isNoValue(base)")
        @Megamorphic
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, @SuppressWarnings("unused") PNone base) {
            Object result = callInt(frame, arg);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            return stringToInt(frame, cls, arg.getValue(), 10, arg);
        }

        @Specialization
        @Megamorphic
        Object parsePInt(VirtualFrame frame, Object cls, PString arg, int base) {
            checkBase(base);
            Object result = callInt(frame, arg);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            return stringToInt(frame, cls, arg.getValue(), base, arg);
        }

        // other

        @Specialization(guards = "isNoValue(base)")
        Object createInt(Object cls, PythonNativeVoidPtr arg, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
                return arg;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("cannot wrap void ptr in int subclass");
            }
        }

        @Specialization(guards = "isNoValue(none)")
        Object createInt(Object cls, @SuppressWarnings("unused") PNone none, @SuppressWarnings("unused") PNone base) {
            if (isPrimitiveInt(cls)) {
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
        @Megamorphic
        Object createIntGeneric(VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") PNone base,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
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
                            String number = PythonUtils.newString(bufferLib.getInternalOrCopiedByteArray(buffer), 0, bufferLib.getBufferLength(buffer));
                            return stringToInt(frame, cls, number, 10, obj);
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
            if (!isPrimitiveProfile.profileObject(result, PythonBuiltinClassType.PInt)) {
                getWarnNode().warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                                ErrorMessages.WARN_P_RETURNED_NON_P,
                                obj, "__int__/__index__", "int", result, "int");
                if (PGuards.isPInt(result)) {
                    result = ((PInt) result).getValue();
                } else if (PGuards.isBoolean(result)) {
                    result = (boolean) result ? 1 : 0;
                }
            }
            return createInt(cls, result);
        }

        protected static boolean isIntegerType(Object obj) {
            return PGuards.isBoolean(obj) || PGuards.isInteger(obj) || PGuards.isPInt(obj);
        }

        protected static boolean isHandledType(Object obj) {
            return PGuards.isInteger(obj) || obj instanceof Double || obj instanceof Boolean || PGuards.isString(obj) || PGuards.isBytes(obj) || obj instanceof PythonNativeVoidPtr;
        }

        protected static FloatBuiltins.IntNode createFloatInt() {
            return FloatBuiltinsFactory.IntNodeFactory.create();
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
                throw raise(TypeError, ErrorMessages.RETURNED_NON_INT, "__index__", result);
            }
            return result;
        }

        private Object callTrunc(VirtualFrame frame, Object obj) {
            if (callTruncNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callTruncNode = insert(LookupAndCallUnaryNode.create(__TRUNC__));
            }
            return callTruncNode.executeObject(frame, obj);
        }

        private Object callInt(VirtualFrame frame, Object object) {
            if (callIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callIntNode = insert(LookupAndCallUnaryNode.create(SpecialMethodSlot.Int));
            }
            Object result = callIntNode.executeObject(frame, object);
            if (result != PNone.NO_VALUE && !isIntegerType(result)) {
                throw raise(TypeError, ErrorMessages.RETURNED_NON_INT, __INT__, result);
            }
            return result;
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
            return PythonUtils.newString(toByteArrayNode.execute(pByteArray));
        }
    }

    // bool([x])
    @Builtin(name = BOOL, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.Boolean, base = PythonBuiltinClassType.PInt)
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
    @Builtin(name = LIST, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PList)
    @GenerateNodeFactory
    public abstract static class ListNode extends PythonVarargsBuiltinNode {
        @Specialization
        protected PList constructList(Object cls, @SuppressWarnings("unused") Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createList(cls);
        }
    }

    // object()
    @Builtin(name = OBJECT, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonObject)
    @GenerateNodeFactory
    public abstract static class ObjectNode extends PythonVarargsBuiltinNode {
        @Child private PCallCapiFunction callCapiFunction;
        @Children private CExtNodes.ToSulongNode[] toSulongNodes;
        @Child private CExtNodes.AsPythonObjectNode asPythonObjectNode;
        @Child private SplitArgsNode splitArgsNode;
        @Child private LookupCallableSlotInMRONode lookupInit;
        @Child private LookupCallableSlotInMRONode lookupNew;
        @Child private ReportAbstractClassNode reportAbstractClassNode;
        @CompilationFinal private ValueProfile profileInit;
        @CompilationFinal private ValueProfile profileNew;
        @CompilationFinal private ValueProfile profileInitFactory;
        @CompilationFinal private ValueProfile profileNewFactory;

        abstract static class ReportAbstractClassNode extends PNodeWithContext {
            public abstract PException execute(VirtualFrame frame, Object type);

            @Specialization
            static PException report(VirtualFrame frame, Object type,
                            @Cached PyObjectCallMethodObjArgs callSort,
                            @Cached PyObjectCallMethodObjArgs callJoin,
                            @Cached PyObjectSizeNode sizeNode,
                            @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                            @Cached CastToJavaStringNode cast,
                            @Cached ListNodes.ConstructListNode constructListNode,
                            @Cached PRaiseNode raiseNode) {
                PList list = constructListNode.execute(frame, readAttributeFromObjectNode.execute(type, __ABSTRACTMETHODS__));
                int methodCount = sizeNode.execute(frame, list);
                callSort.execute(frame, list, "sort");
                String joined = cast.execute(callJoin.execute(frame, ", ", "join", list));
                throw raiseNode.raise(TypeError, "Can't instantiate abstract class %N with abstract method%s %s", type, methodCount > 1 ? "s" : "", joined);
            }

            public static ReportAbstractClassNode create() {
                return BuiltinConstructorsFactory.ObjectNodeFactory.ReportAbstractClassNodeGen.create();
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
        Object doManagedObject(VirtualFrame frame, PythonManagedClass self, Object[] varargs, PKeyword[] kwargs) {
            checkExcessArgs(self, varargs, kwargs);
            if (self.isAbstractClass()) {
                throw getReportAbstractClassNode().execute(frame, self);
            }
            return factory().createPythonObject(self);
        }

        @Specialization
        Object doBuiltinTypeType(PythonBuiltinClassType self, Object[] varargs, PKeyword[] kwargs) {
            checkExcessArgs(self, varargs, kwargs);
            return factory().createPythonObject(self);
        }

        @Specialization(guards = "self.needsNativeAllocation()")
        Object doNativeObjectIndirect(VirtualFrame frame, PythonManagedClass self, Object[] varargs, PKeyword[] kwargs,
                        @Cached GetMroNode getMroNode) {
            checkExcessArgs(self, varargs, kwargs);
            if (self.isAbstractClass()) {
                throw getReportAbstractClassNode().execute(frame, self);
            }
            Object nativeBaseClass = findFirstNativeBaseClass(getMroNode.execute(self));
            return callNativeGenericNewNode(nativeBaseClass, varargs, kwargs);
        }

        @Specialization(guards = "isNativeClass(self)")
        Object doNativeObjectDirect(VirtualFrame frame, Object self, Object[] varargs, PKeyword[] kwargs,
                        @Cached TypeNodes.GetTypeFlagsNode getTypeFlagsNode) {
            checkExcessArgs(self, varargs, kwargs);
            if ((getTypeFlagsNode.execute(self) & TypeFlags.IS_ABSTRACT) != 0) {
                throw getReportAbstractClassNode().execute(frame, self);
            }
            return callNativeGenericNewNode(self, varargs, kwargs);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object fallback(Object o, Object[] varargs, PKeyword[] kwargs) {
            throw raise(TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "object.__new__(X): X", o);
        }

        private static Object findFirstNativeBaseClass(PythonAbstractClass[] methodResolutionOrder) {
            for (Object cls : methodResolutionOrder) {
                if (PGuards.isNativeClass(cls)) {
                    return cls;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("class needs native allocation but has not native base class");
        }

        private Object callNativeGenericNewNode(Object self, Object[] varargs, PKeyword[] kwargs) {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(PCallCapiFunction.create());
            }
            if (toSulongNodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                CExtNodes.ToSulongNode[] newToSulongNodes = new CExtNodes.ToSulongNode[4];
                for (int i = 0; i < newToSulongNodes.length; i++) {
                    newToSulongNodes[i] = CExtNodesFactory.ToSulongNodeGen.create();
                }
                toSulongNodes = insert(newToSulongNodes);
            }
            if (asPythonObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPythonObjectNode = insert(CExtNodesFactory.AsPythonObjectNodeGen.create());
            }
            PKeyword[] kwarr = kwargs.length > 0 ? kwargs : null;
            PTuple targs = factory().createTuple(varargs);
            PDict dkwargs = factory().createDict(kwarr);
            return asPythonObjectNode.execute(
                            callCapiFunction.call(FUN_PY_OBJECT_NEW, toSulongNodes[0].execute(self), toSulongNodes[1].execute(self), toSulongNodes[2].execute(targs),
                                            toSulongNodes[3].execute(dkwargs)));
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

        private ReportAbstractClassNode getReportAbstractClassNode() {
            if (reportAbstractClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reportAbstractClassNode = insert(ReportAbstractClassNode.create());
            }
            return reportAbstractClassNode;
        }
    }

    // range(stop)
    // range(start, stop[, step])
    @Builtin(name = RANGE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PRange)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class RangeNode extends PythonQuaternaryBuiltinNode {
        // stop
        @Specialization(guards = "isStop(start, stop, step)")
        Object doIntStop(Object cls, int stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            return doInt(cls, 0, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        Object doPintStop(Object cls, PInt stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            return doPint(cls, factory().createInt(0), stop, factory().createInt(1), stepZeroProfile, lenOfRangeNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        Object doGenericStop(VirtualFrame frame, Object cls, Object stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinClassProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode) {
            return doGeneric(frame, cls, 0, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, cast, overflowProfile, indexNode);
        }

        // start stop
        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doIntStartStop(Object cls, int start, int stop, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            return doInt(cls, start, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doPintStartStop(Object cls, PInt start, PInt stop, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            return doPint(cls, start, stop, factory().createInt(1), stepZeroProfile, lenOfRangeNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        Object doGenericStartStop(VirtualFrame frame, Object cls, Object start, Object stop, @SuppressWarnings("unused") PNone step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinClassProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode) {
            return doGeneric(frame, cls, start, stop, 1, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, cast, overflowProfile, indexNode);
        }

        // start stop step
        @Specialization
        Object doInt(@SuppressWarnings("unused") Object cls, int start, int stop, int step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode) {
            if (stepZeroProfile.profile(step == 0)) {
                throw raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            try {
                int len = lenOfRangeNode.executeInt(start, stop, step);
                return factory().createIntRange(start, stop, step, len);
            } catch (OverflowException e) {
                exceptionProfile.enter();
                return createBigRangeNode.execute(start, stop, step, factory());
            }
        }

        @Specialization
        Object doPint(@SuppressWarnings("unused") Object cls, PInt start, PInt stop, PInt step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode) {
            if (stepZeroProfile.profile(step.isZero())) {
                throw raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            BigInteger len = lenOfRangeNode.execute(start.getValue(), stop.getValue(), step.getValue());
            return factory().createBigRange(start, stop, step, factory().createInt(len));
        }

        @Specialization(guards = "isStartStopStep(start, stop, step)")
        Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object start, Object stop, Object step,
                        @Shared("stepZeroProfile") @Cached ConditionProfile stepZeroProfile,
                        @Shared("exceptionProfile") @Cached BranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinClassProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode) {
            Object lstart = indexNode.execute(frame, start);
            Object lstop = indexNode.execute(frame, stop);
            Object lstep = indexNode.execute(frame, step);

            try {
                int istart = cast.execute(lstart);
                int istop = cast.execute(lstop);
                int istep = cast.execute(lstep);
                return doInt(cls, istart, istop, istep, stepZeroProfile, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode);
            } catch (PException e) {
                e.expect(OverflowError, overflowProfile);
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
    @Builtin(name = SET, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PSet)
    @GenerateNodeFactory
    public abstract static class SetNode extends PythonBuiltinNode {

        @Specialization
        public PSet setEmpty(Object cls, @SuppressWarnings("unused") Object arg) {
            return factory().createSet(cls);
        }

    }

    // str(object='')
    // str(object=b'', encoding='utf-8', errors='strict')
    @Builtin(name = STR, minNumOfPositionalArgs = 1, parameterNames = {"cls", "object", "encoding", "errors"}, constructsClass = PythonBuiltinClassType.PString)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonBuiltinNode {
        @Child private LookupAndCallTernaryNode callDecodeNode;

        @Child private IsBuiltinClassProfile isPrimitiveProfile = IsBuiltinClassProfile.create();

        @CompilationFinal private ConditionProfile isStringProfile;
        @CompilationFinal private ConditionProfile isPStringProfile;
        @Child private CastToJavaStringNode castToJavaStringNode;

        public final Object executeWith(VirtualFrame frame, Object arg) {
            return executeWith(frame, PythonBuiltinClassType.PString, arg, PNone.NO_VALUE, PNone.NO_VALUE);
        }

        public abstract Object executeWith(VirtualFrame frame, Object strClass, Object arg, Object encoding, Object errors);

        @Specialization(guards = {"!isNativeClass(strClass)", "isNoValue(arg)"})
        @SuppressWarnings("unused")
        Object strNoArgs(Object strClass, PNone arg, Object encoding, Object errors) {
            return asPString(strClass, "");
        }

        @Specialization(guards = {"!isNativeClass(strClass)", "!isNoValue(obj)", "isNoValue(encoding)", "isNoValue(errors)"})
        Object strOneArg(VirtualFrame frame, Object strClass, Object obj, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Cached PyObjectStrAsObjectNode strNode) {
            Object result = strNode.execute(frame, obj);

            // try to return a primitive if possible
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
            }

            if (isPrimitiveProfile.profileClass(strClass, PythonBuiltinClassType.PString)) {
                // PyObjectStrAsObjectNode guarantees that the returned object is an instanceof of
                // 'str'
                return result;
            } else {
                try {
                    return asPString(strClass, getCastToJavaStringNode().execute(result));
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("asPstring result not castable to String");
                }
            }
        }

        @Specialization(guards = {"!isNativeClass(strClass)", "!isNoValue(encoding) || !isNoValue(errors)"}, limit = "3")
        Object doBuffer(VirtualFrame frame, Object strClass, Object obj, Object encoding, Object errors,
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
                Object en = encoding == PNone.NO_VALUE ? "utf-8" : encoding;
                return decodeBytes(frame, strClass, bytesObj, en, errors);
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        private Object decodeBytes(VirtualFrame frame, Object strClass, PBytes obj, Object encoding, Object errors) {
            Object result = getCallDecodeNode().execute(frame, obj, encoding, errors);
            if (getIsStringProfile().profile(result instanceof String)) {
                return asPString(strClass, (String) result);
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
        @Specialization(guards = {"isNativeClass(cls)", "isSubtypeOfString(frame, isSubtype, cls)", "isNoValue(encoding)", "isNoValue(errors)"})
        static Object doNativeSubclass(VirtualFrame frame, Object cls, Object obj, @SuppressWarnings("unused") Object encoding,
                        @SuppressWarnings("unused") Object errors,
                        @Cached @SuppressWarnings("unused") IsSubtypeNode isSubtype,
                        @Cached PyObjectStrAsObjectNode strNode,
                        @Cached CExtNodes.StringSubtypeNew subtypeNew) {
            return subtypeNew.call(cls, strNode.execute(frame, obj));
        }

        protected static boolean isSubtypeOfString(VirtualFrame frame, IsSubtypeNode isSubtypeNode, Object cls) {
            return isSubtypeNode.execute(frame, cls, PythonBuiltinClassType.PString);
        }

        private Object asPString(Object cls, String str) {
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

        private CastToJavaStringNode getCastToJavaStringNode() {
            if (castToJavaStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToJavaStringNode = insert(CastToJavaStringNode.create());
            }
            return castToJavaStringNode;
        }

        public static StrNode create() {
            return BuiltinConstructorsFactory.StrNodeFactory.create(null);
        }
    }

    // tuple([iterable])
    @Builtin(name = TUPLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PTuple)
    @GenerateNodeFactory
    public abstract static class TupleNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isNativeClass(cls)")
        protected static PTuple constructTuple(VirtualFrame frame, Object cls, Object iterable,
                        @Cached TupleNodes.ConstructTupleNode constructTupleNode) {
            return constructTupleNode.execute(frame, cls, iterable);
        }

        // delegate to tuple_subtype_new(PyTypeObject *type, PyObject *x)
        @Specialization(guards = {"isNativeClass(cls)", "isSubtypeOfTuple(frame, isSubtype, cls)"}, limit = "1")
        static Object doNative(@SuppressWarnings("unused") VirtualFrame frame, Object cls, Object iterable,
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
    @Builtin(name = ZIP, minNumOfPositionalArgs = 1, takesVarArgs = true, constructsClass = PythonBuiltinClassType.PZip)
    @GenerateNodeFactory
    public abstract static class ZipNode extends PythonBuiltinNode {
        @Specialization
        PZip zip(VirtualFrame frame, Object cls, Object[] args,
                        @Cached PyObjectGetIter getIter) {
            Object[] iterables = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object item = args[i];
                iterables[i] = getIter.execute(frame, item);
            }
            return factory().createZip(cls, iterables);
        }
    }

    // function(code, globals[, name[, argdefs[, closure]]])
    @Builtin(name = "function", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 6, constructsClass = PythonBuiltinClassType.PFunction, isPublic = false)
    @GenerateNodeFactory
    public abstract static class FunctionNode extends PythonBuiltinNode {
        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure) {
            return factory().createFunction(name, code, globals, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction("<lambda>", code, globals, PCell.toCellArray(getObjectArrayNode.execute(closure)));
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, @SuppressWarnings("unused") PNone name, @SuppressWarnings("unused") PNone defaultArgs,
                        @SuppressWarnings("unused") PNone closure,
                        @SuppressWarnings("unused") @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction("<lambda>", code, globals, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, String name, @SuppressWarnings("unused") PNone defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory().createFunction(name, code, globals, PCell.toCellArray(getObjectArrayNode.execute(closure)));
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, String name, PTuple defaultArgs, @SuppressWarnings("unused") PNone closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, code, globals, getObjectArrayNode.execute(defaultArgs), null, null);
        }

        @Specialization
        public PFunction function(@SuppressWarnings("unused") Object cls, PCode code, PDict globals, String name, PTuple defaultArgs, PTuple closure,
                        @Shared("getObjectArrayNode") @Cached GetObjectArrayNode getObjectArrayNode) {
            // TODO split defaults of positional args from kwDefaults
            return factory().createFunction(name, code, globals, getObjectArrayNode.execute(defaultArgs), null, PCell.toCellArray(getObjectArrayNode.execute(closure)));
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

    // type(object)
    // type(object, bases, dict)
    @Builtin(name = TYPE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonClass)
    @GenerateNodeFactory
    public abstract static class TypeNode extends PythonBuiltinNode {
        private static final long SIZEOF_PY_OBJECT_PTR = Long.BYTES;

        @Child private ReadAttributeFromObjectNode readAttrNode;
        @Child private SetAttributeNode.Dynamic writeAttrNode;
        @Child private GetAnyAttributeNode getAttrNode;
        @Child private CastToJavaIntExactNode castToInt;
        @Child private CastToListNode castToList;
        @Child private CastToJavaStringNode castToStringNode;
        @Child private SequenceStorageNodes.LenNode slotLenNode;
        @Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Child private SequenceStorageNodes.AppendNode appendNode;
        @Child private CExtNodes.PCallCapiFunction callAddNativeSlotsNode;
        @Child private CExtNodes.ToSulongNode toSulongNode;
        @Child private ReadCallerFrameNode readCallerFrameNode;
        @Child private GetMroNode getMroNode;
        @Child private IsSubtypeNode isSubtypeNode;
        @Child private GetObjectArrayNode getObjectArrayNode;
        @Child private IsAcceptableBaseNode isAcceptableBaseNode;

        public abstract Object execute(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds);

        @Specialization(guards = {"isNoValue(bases)", "isNoValue(dict)"})
        @SuppressWarnings("unused")
        Object type(Object cls, Object obj, PNone bases, PNone dict, PKeyword[] kwds,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached GetClassNode getClass) {
            if (profile.profileClass(cls, PythonBuiltinClassType.PythonClass)) {
                return getClass.execute(obj);
            } else {
                throw raise(TypeError, TAKES_EXACTLY_D_ARGUMENTS_D_GIVEN, "type.__new__", 3, 1);
            }
        }

        @Megamorphic
        @Specialization(guards = "isString(wName)")
        Object typeNew(VirtualFrame frame, Object cls, Object wName, PTuple bases, PDict namespaceOrig, PKeyword[] kwds,
                        @Cached GetClassNode getClassNode,
                        @CachedLibrary(limit = "3") HashingStorageLibrary hashingStoragelib,
                        @Cached BranchProfile updatedStorage,
                        @Cached("create(New)") LookupCallableSlotInMRONode getNewFuncNode,
                        @Cached TypeBuiltins.BindNew bindNew,
                        @Cached("create(__INIT_SUBCLASS__)") GetAttributeNode getInitSubclassNode,
                        @Cached("create(SetName)") LookupInheritedSlotNode getSetNameNode,
                        @Cached("create(__MRO_ENTRIES__)") LookupInheritedAttributeNode lookupMroEntriesNode,
                        @Cached CastToJavaStringNode castStr,
                        @Cached CallNode callSetNameNode,
                        @Cached CallNode callInitSubclassNode,
                        @Cached CallNode callNewFuncNode,
                        @Cached("create(__DICT__)") LookupAttributeInMRONode getDictAttrNode,
                        @Cached("create(__WEAKREF__)") LookupAttributeInMRONode getWeakRefAttrNode,
                        @Cached GetItemsizeNode getItemSize,
                        @Cached WriteAttributeToObjectNode writeItemSize,
                        @Cached GetBestBaseClassNode getBestBaseNode,
                        @Cached IsIdentifierNode isIdentifier,
                        @Cached HashingStorage.InitNode initNode,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode) {
            // Determine the proper metatype to deal with this
            String name = castStr.execute(wName);
            Object metaclass = cls;
            Object winner = calculateMetaclass(frame, metaclass, bases, getClassNode, lookupMroEntriesNode);
            if (winner != metaclass) {
                Object newFunc = getNewFuncNode.execute(winner);
                if (newFunc instanceof PBuiltinFunction && (((PBuiltinFunction) newFunc).getFunctionRootNode().getCallTarget() == getRootNode().getCallTarget())) {
                    metaclass = winner;
                    // the new metaclass has the same __new__ function as we are in, continue
                } else {
                    // Pass it to the winner
                    return callNewFuncNode.execute(frame, bindNew.execute(frame, newFunc, winner), new Object[]{winner, name, bases, namespaceOrig}, kwds);
                }
            }

            try {
                assert SpecialMethodSlot.pushInitializedTypePlaceholder();
                PDict namespace = factory().createDict();
                PythonLanguage language = PythonLanguage.get(this);
                namespace.setDictStorage(initNode.execute(frame, namespaceOrig, PKeyword.EMPTY_KEYWORDS));
                PythonClass newType = typeMetaclass(frame, language, name, bases, namespace, metaclass, hashingStoragelib,
                                getDictAttrNode, getWeakRefAttrNode, getBestBaseNode, getItemSize, writeItemSize, isIdentifier, constructAndRaiseNode);

                for (DictEntry entry : hashingStoragelib.entries(namespace.getDictStorage())) {
                    Object setName = getSetNameNode.execute(entry.value);
                    if (setName != PNone.NO_VALUE) {
                        try {
                            callSetNameNode.execute(frame, setName, entry.value, newType, entry.key);
                        } catch (PException e) {
                            throw raise(RuntimeError, e.getEscapedException(), ERROR_CALLING_SET_NAME, entry.value, entry.key, newType);
                        }
                    }
                }

                // Call __init_subclass__ on the parent of a newly generated type
                SuperObject superObject = factory().createSuperObject(PythonBuiltinClassType.Super);
                superObject.init(newType, newType, newType);
                callInitSubclassNode.execute(frame, getInitSubclassNode.executeObject(frame, superObject), PythonUtils.EMPTY_OBJECT_ARRAY, kwds);

                // set '__module__' attribute
                Object moduleAttr = ensureReadAttrNode().execute(newType, __MODULE__);
                if (moduleAttr == PNone.NO_VALUE) {
                    PFrame callerFrame = getReadCallerFrameNode().executeWith(frame, 0);
                    PythonObject globals = callerFrame != null ? callerFrame.getGlobals() : null;
                    if (globals != null) {
                        String moduleName = getModuleNameFromGlobals(globals, hashingStoragelib);
                        if (moduleName != null) {
                            ensureWriteAttrNode().execute(frame, newType, __MODULE__, moduleName);
                        }
                    }
                }

                // delete __qualname__ from namespace
                if (hashingStoragelib.hasKey(namespace.getDictStorage(), __QUALNAME__)) {
                    HashingStorage newStore = hashingStoragelib.delItem(namespace.getDictStorage(), __QUALNAME__);
                    if (newStore != namespace.getDictStorage()) {
                        updatedStorage.enter();
                        namespace.setDictStorage(newStore);
                    }
                }

                // set __class__ cell contents
                Object classcell = hashingStoragelib.getItem(namespace.getDictStorage(), __CLASSCELL__);
                if (classcell != null) {
                    if (classcell instanceof PCell) {
                        ((PCell) classcell).setRef(newType);
                    } else {
                        raise(TypeError, ErrorMessages.MUST_BE_A_CELL, "__classcell__");
                    }
                    if (hashingStoragelib.hasKey(namespace.getDictStorage(), __CLASSCELL__)) {
                        HashingStorage newStore = hashingStoragelib.delItem(namespace.getDictStorage(), __CLASSCELL__);
                        if (newStore != namespace.getDictStorage()) {
                            updatedStorage.enter();
                            namespace.setDictStorage(newStore);
                        }
                    }
                }

                if (newType.getAttribute(SpecialAttributeNames.__DOC__) == PNone.NO_VALUE) {
                    newType.setAttribute(SpecialAttributeNames.__DOC__, PNone.NONE);
                }

                SpecialMethodSlot.initializeSpecialMethodSlots(newType, getMroStorageNode, language);
                newType.initializeMroShape(language);
                return newType;
            } catch (PException e) {
                throw e;
            } finally {
                assert SpecialMethodSlot.popInitializedType();
            }
        }

        @Fallback
        Object generic(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object name, Object bases, Object namespace, @SuppressWarnings("unused") Object kwds) {
            if (!(bases instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 2, "tuple", bases);
            } else if (namespace == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
            } else if (!(namespace instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "type.__new__()", 3, "dict", bases);
            } else {
                throw CompilerDirectives.shouldNotReachHere("type fallback reached incorrectly");
            }
        }

        private String getModuleNameFromGlobals(PythonObject globals, HashingStorageLibrary hlib) {
            Object nameAttr;
            if (globals instanceof PythonModule) {
                nameAttr = ensureReadAttrNode().execute(globals, __NAME__);
            } else if (globals instanceof PDict) {
                nameAttr = hlib.getItem(((PDict) globals).getDictStorage(), __NAME__);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid globals object");
            }
            try {
                return ensureCastToStringNode().execute(nameAttr);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        private PythonClass typeMetaclass(VirtualFrame frame, PythonLanguage language, String name, PTuple bases, PDict namespace, Object metaclass,
                        HashingStorageLibrary hashingStorageLib, LookupAttributeInMRONode getDictAttrNode,
                        LookupAttributeInMRONode getWeakRefAttrNode, GetBestBaseClassNode getBestBaseNode, GetItemsizeNode getItemSize, WriteAttributeToObjectNode writeItemSize,
                        IsIdentifierNode isIdentifier, PConstructAndRaiseNode constructAndRaiseNode) {
            Object[] array = ensureGetObjectArrayNode().execute(bases);

            PythonAbstractClass[] basesArray;
            if (array.length == 0) {
                // Adjust for empty tuple bases
                basesArray = new PythonAbstractClass[]{getCore().lookupType(PythonBuiltinClassType.PythonObject)};
            } else {
                basesArray = new PythonAbstractClass[array.length];
                for (int i = 0; i < array.length; i++) {
                    // TODO: deal with non-class bases
                    if (PythonAbstractClass.isInstance(array[i])) {
                        basesArray[i] = (PythonAbstractClass) array[i];
                    } else if (array[i] instanceof PythonBuiltinClassType) {
                        basesArray[i] = getCore().lookupType((PythonBuiltinClassType) array[i]);
                    } else {
                        throw raise(NotImplementedError, "creating a class with non-class bases");
                    }
                }
            }
            // check for possible layout conflicts
            Object base = getBestBaseNode.execute(basesArray);

            assert metaclass != null;

            if (!canEncodeUTF8(name)) {
                throw constructAndRaiseNode.raiseUnicodeEncodeError(frame, "utf-8", name, 0, name.length(), "can't encode class name");
            }
            if (containsNullCharacter(name)) {
                throw raise(ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
            }

            // 1.) create class, but avoid calling mro method - it might try to access __dict__ so
            // we have to copy dict slots first
            PythonClass pythonClass = factory().createPythonClass(metaclass, name, false, basesArray);
            assert SpecialMethodSlot.replaceInitializedTypeTop(pythonClass);

            // 2.) copy the dictionary slots
            Object[] slots = new Object[1];
            boolean[] qualnameSet = new boolean[]{false};
            copyDictSlots(frame, pythonClass, namespace, hashingStorageLib, slots, qualnameSet, constructAndRaiseNode);
            if (!qualnameSet[0]) {
                pythonClass.setQualName(name);
            }

            // 3.) invoke metaclass mro() method
            pythonClass.invokeMro();

            // CPython masks the __hash__ method with None when __eq__ is overriden, but __hash__ is
            // not
            Object hashMethod = hashingStorageLib.getItem(namespace.getDictStorage(), __HASH__);
            if (hashMethod == null) {
                Object eqMethod = hashingStorageLib.getItem(namespace.getDictStorage(), __EQ__);
                if (eqMethod != null) {
                    pythonClass.setAttribute(__HASH__, PNone.NONE);
                }
            }

            boolean addDict = false;
            boolean addWeakRef = false;
            // may_add_dict = base->tp_dictoffset == 0
            boolean mayAddDict = getDictAttrNode.execute(base) == PNone.NO_VALUE;
            // may_add_weak = base->tp_weaklistoffset == 0 && base->tp_itemsize == 0
            boolean hasItemSize = getItemSize.execute(base) != 0;
            boolean mayAddWeakRef = getWeakRefAttrNode.execute(base) == PNone.NO_VALUE && !hasItemSize;

            if (slots[0] == null) {
                // takes care of checking if we may_add_dict and adds it if needed
                addDictIfNative(frame, pythonClass, getItemSize, writeItemSize);
                addDictDescrAttribute(basesArray, pythonClass);
                addWeakrefDescrAttribute(pythonClass);
            } else {
                // have slots

                // Make it into a list
                SequenceStorage slotsStorage;
                Object slotsObject;
                if (slots[0] instanceof String) {
                    slotsObject = slots[0];
                    slotsStorage = new ObjectSequenceStorage(slots);
                } else if (slots[0] instanceof PTuple) {
                    slotsObject = slots[0];
                    slotsStorage = ((PTuple) slots[0]).getSequenceStorage();
                } else if (slots[0] instanceof PList) {
                    slotsObject = slots[0];
                    slotsStorage = ((PList) slots[0]).getSequenceStorage();
                } else {
                    slotsObject = getCastToListNode().execute(frame, slots[0]);
                    slotsStorage = ((PList) slotsObject).getSequenceStorage();
                }
                int slotlen = getListLenNode().execute(slotsStorage);

                if (slotlen > 0 && hasItemSize) {
                    throw raise(TypeError, ErrorMessages.NONEMPTY_SLOTS_NOT_ALLOWED_FOR_SUBTYPE_OF_S, base);
                }

                for (int i = 0; i < slotlen; i++) {
                    String slotName;
                    Object element = getSlotItemNode().execute(frame, slotsStorage, i);
                    // Check valid slot name
                    if (element instanceof String) {
                        slotName = (String) element;
                        if (!(boolean) isIdentifier.execute(frame, slotName)) {
                            throw raise(TypeError, ErrorMessages.SLOTS_MUST_BE_IDENTIFIERS);
                        }
                    } else {
                        throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "__slots__ items", element);
                    }
                    if (__DICT__.equals(slotName)) {
                        if (!mayAddDict || addDict || addDictIfNative(frame, pythonClass, getItemSize, writeItemSize)) {
                            throw raise(TypeError, ErrorMessages.DICT_SLOT_DISALLOWED_WE_GOT_ONE);
                        }
                        addDict = true;
                        addDictDescrAttribute(basesArray, pythonClass);
                    } else if (__WEAKREF__.equals(slotName)) {
                        if (!mayAddWeakRef || addWeakRef) {
                            throw raise(TypeError, ErrorMessages.WEAKREF_SLOT_DISALLOWED_WE_GOT_ONE);
                        }
                        addWeakRef = true;
                        addWeakrefDescrAttribute(pythonClass);
                    } else {
                        // TODO: check for __weakref__
                        // TODO avoid if native slots are inherited
                        try {
                            String mangledName = PythonSSTNodeFactory.mangleName(name, slotName);

                            HiddenKey hiddenSlotKey = createTypeKey(mangledName);
                            HiddenKeyDescriptor slotDesc = factory().createHiddenKeyDescriptor(hiddenSlotKey, pythonClass);
                            pythonClass.setAttribute(mangledName, slotDesc);
                        } catch (OverflowException e) {
                            throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
                        }
                    }
                    // Make slots into a tuple
                }
                Object state = IndirectCallContext.enter(frame, language, getContext(), this);
                try {
                    pythonClass.setAttribute(__SLOTS__, slotsObject);
                    if (basesArray.length > 1) {
                        // TODO: tfel - check if secondary bases provide weakref or dict when we
                        // don't already have one
                    }

                    // checks for some name errors too
                    PTuple newSlots = copySlots(name, slotsStorage, slotlen, addDict, addWeakRef, namespace, hashingStorageLib);

                    // add native slot descriptors
                    if (pythonClass.needsNativeAllocation()) {
                        addNativeSlots(pythonClass, newSlots);
                    }
                } finally {
                    IndirectCallContext.exit(frame, language, getContext(), state);
                }
                Object dict = LookupAttributeInMRONode.lookupSlowPath(pythonClass, __DICT__);
                if (!addDict && dict == PNone.NO_VALUE) {
                    pythonClass.setHasSlotsButNoDictFlag();
                }
            }

            return pythonClass;
        }

        @TruffleBoundary
        private static HiddenKey createTypeKey(String name) {
            return PythonLanguage.get(null).typeHiddenKeys.computeIfAbsent(name, n -> new HiddenKey(n));
        }

        @TruffleBoundary
        private void addDictDescrAttribute(PythonAbstractClass[] basesArray, PythonClass pythonClass) {
            // Note: we need to avoid MRO lookup of __dict__ using slots because they are not
            // initialized yet
            if ((!hasPythonClassBases(basesArray) && LookupAttributeInMRONode.lookupSlowPath(pythonClass, __DICT__) == PNone.NO_VALUE) || basesHaveSlots(basesArray)) {
                Builtin dictBuiltin = ObjectBuiltins.DictNode.class.getAnnotation(Builtin.class);
                RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(
                                l -> new BuiltinFunctionRootNode(l, dictBuiltin, new StandaloneBuiltinFactory<PythonBinaryBuiltinNode>(DictNodeGen.create()), true), ObjectBuiltins.DictNode.class,
                                StandaloneBuiltinFactory.class);
                setAttribute(__DICT__, dictBuiltin, callTarget, pythonClass);
            }
        }

        @TruffleBoundary
        private void addWeakrefDescrAttribute(PythonClass pythonClass) {
            Builtin builtin = GetWeakRefsNode.class.getAnnotation(Builtin.class);
            RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, WeakRefModuleBuiltinsFactory.GetWeakRefsNodeFactory.getInstance(), true), GetWeakRefsNode.class,
                            WeakRefModuleBuiltinsFactory.class);
            setAttribute(__WEAKREF__, builtin, callTarget, pythonClass);
        }

        private void setAttribute(String name, Builtin builtin, RootCallTarget callTarget, PythonClass pythonClass) {
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            PBuiltinFunction function = factory().createBuiltinFunction(name, pythonClass, 1, flags, callTarget);
            GetSetDescriptor desc = factory().createGetSetDescriptor(function, function, name, pythonClass, true);
            pythonClass.setAttribute(name, desc);
        }

        private static boolean basesHaveSlots(PythonAbstractClass[] basesArray) {
            // this is merely based on empirical observation
            // see also test_type.py#test_dict()
            for (PythonAbstractClass c : basesArray) {
                // TODO: what about native?
                if (c instanceof PythonClass) {
                    if (((PythonClass) c).getAttribute(__SLOTS__) != PNone.NO_VALUE) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean hasPythonClassBases(PythonAbstractClass[] basesArray) {
            for (PythonAbstractClass c : basesArray) {
                if (c instanceof PythonClass) {
                    return true;
                }
            }
            return false;
        }

        private void copyDictSlots(VirtualFrame frame, PythonClass pythonClass, PDict namespace, HashingStorageLibrary hashingStorageLib, Object[] slots, boolean[] qualnameSet,
                        PConstructAndRaiseNode constructAndRaiseNode) {
            // copy the dictionary slots over, as CPython does through PyDict_Copy
            // Also check for a __slots__ sequence variable in dict
            PDict typeDict = null;
            for (DictEntry entry : hashingStorageLib.entries(namespace.getDictStorage())) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (__SLOTS__.equals(key)) {
                    slots[0] = value;
                } else if (SpecialMethodNames.__NEW__.equals(key)) {
                    // see CPython: if it's a plain function, make it a static function
                    if (value instanceof PFunction) {
                        pythonClass.setAttribute(key, factory().createStaticmethodFromCallableObj(value));
                    } else {
                        pythonClass.setAttribute(key, value);
                    }
                } else if (SpecialMethodNames.__INIT_SUBCLASS__.equals(key) ||
                                SpecialMethodNames.__CLASS_GETITEM__.equals(key)) {
                    // see CPython: Special-case __init_subclass__ and
                    // __class_getitem__: if they are plain functions, make them
                    // classmethods
                    if (value instanceof PFunction) {
                        pythonClass.setAttribute(key, factory().createClassmethodFromCallableObj(value));
                    } else {
                        pythonClass.setAttribute(key, value);
                    }
                } else if (SpecialAttributeNames.__DOC__.equals(key)) {
                    // CPython sets tp_doc to a copy of dict['__doc__'], if that is a string. It
                    // forcibly encodes the string as UTF-8, and raises an error if that is not
                    // possible.
                    String doc = null;
                    if (value instanceof String) {
                        doc = (String) value;
                    } else if (value instanceof PString) {
                        doc = ((PString) value).getValue();
                    }
                    if (doc != null) {
                        if (!canEncodeUTF8(doc)) {
                            throw constructAndRaiseNode.raiseUnicodeEncodeError(frame, "utf-8", doc, 0, doc.length(), "can't encode docstring");
                        }
                    }
                    pythonClass.setAttribute(key, value);
                } else if (SpecialAttributeNames.__QUALNAME__.equals(key)) {
                    try {
                        pythonClass.setQualName(ensureCastToStringNode().execute(value));
                        qualnameSet[0] = true;
                    } catch (CannotCastException e) {
                        throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "type __qualname__", "str", value);
                    }
                } else if (SpecialAttributeNames.__CLASSCELL__.equals(key)) {
                    // don't populate this attribute
                } else if (key instanceof String && typeDict == null) {
                    pythonClass.setAttribute(key, value);
                } else {
                    // Creates DynamicObjectStorage which ignores non-string keys
                    typeDict = GetOrCreateDictNode.getUncached().execute(pythonClass);
                    // Writing a non string key converts DynamicObjectStorage to EconomicMapStorage
                    HashingStorage updatedStore = hashingStorageLib.setItem(typeDict.getDictStorage(), key, value);
                    typeDict.setDictStorage(updatedStore);
                }
            }
        }

        @TruffleBoundary
        private PTuple copySlots(String className, SequenceStorage slotList, int slotlen, boolean add_dict, boolean add_weak, PDict namespace, HashingStorageLibrary nslib) {
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

                try {
                    slotName = PythonSSTNodeFactory.mangleName(className, slotName);
                } catch (OverflowException e) {
                    throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
                }
                if (slotName == null) {
                    return null;
                }

                setSlotItemNode().execute(newSlots, slotName, NoGeneralizationNode.DEFAULT);
                // Passing 'null' frame is fine because the caller already transfers the exception
                // state to the context.
                if (!slotName.equals(__CLASSCELL__) && !slotName.equals(__QUALNAME__) && nslib.hasKey(namespace.getDictStorage(), slotName)) {
                    // __qualname__ and __classcell__ will be deleted later
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.S_S_CONFLICTS_WITH_CLASS_VARIABLE, slotName, "__slots__");
                }
                j++;
            }
            assert j == slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak);

            // sort newSlots
            Arrays.sort(newSlots.getInternalArray());

            return factory().createTuple(newSlots);

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
                toSulongNode = insert(CExtNodes.ToSulongNode.create());
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

        /**
         * check that the native base does not already have tp_dictoffset
         */
        private boolean addDictIfNative(VirtualFrame frame, PythonManagedClass pythonClass, GetItemsizeNode getItemSize, WriteAttributeToObjectNode writeItemSize) {
            boolean addedNewDict = false;
            if (pythonClass.needsNativeAllocation()) {
                for (Object cls : getMro(pythonClass)) {
                    if (PGuards.isNativeClass(cls)) {
                        // Use GetAnyAttributeNode since these are get-set-descriptors
                        long dictoffset = ensureCastToIntNode().execute(ensureGetAttributeNode().executeObject(frame, cls, __DICTOFFSET__));
                        long basicsize = ensureCastToIntNode().execute(ensureGetAttributeNode().executeObject(frame, cls, __BASICSIZE__));
                        long itemsize = ensureCastToIntNode().execute(getItemSize.execute(cls));
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
                        writeItemSize.execute(pythonClass, TYPE_ITEMSIZE, itemsize);
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

        private Object calculateMetaclass(VirtualFrame frame, Object cls, PTuple bases, GetClassNode getClassNode, LookupInheritedAttributeNode lookupMroEntries) {
            Object winner = cls;
            for (Object base : ensureGetObjectArrayNode().execute(bases)) {
                if (lookupMroEntries.execute(base) != PNone.NO_VALUE) {
                    throw raise(TypeError, ErrorMessages.TYPE_DOESNT_SUPPORT_MRO_ENTRY_RESOLUTION);
                }
                if (!ensureIsAcceptableBaseNode().execute(base)) {
                    throw raise(TypeError, ErrorMessages.TYPE_IS_NOT_ACCEPTABLE_BASE_TYPE, base);
                }
                Object typ = getClassNode.execute(base);
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

        public static TypeNode create() {
            return BuiltinConstructorsFactory.TypeNodeFactory.create(null);
        }

        @Specialization(guards = {"!isNoValue(bases)", "!isNoValue(dict)"})
        Object typeGeneric(VirtualFrame frame, Object cls, Object name, Object bases, Object dict, PKeyword[] kwds,
                        @Cached TypeNode nextTypeNode,
                        @Cached IsTypeNode isTypeNode) {
            if (PGuards.isNoValue(bases) && !PGuards.isNoValue(dict) || !PGuards.isNoValue(bases) && PGuards.isNoValue(dict)) {
                throw raise(TypeError, ErrorMessages.TAKES_D_OR_D_ARGS, "type()", 1, 3);
            } else if (!(name instanceof String || name instanceof PString)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 1", name);
            } else if (!(bases instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 2", bases);
            } else if (!(dict instanceof PDict)) {
                throw raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "type() argument 3", dict);
            } else if (!isTypeNode.execute(cls)) {
                // TODO: this is actually allowed, deal with it
                throw raise(NotImplementedError, "creating a class with non-class metaclass");
            }
            return nextTypeNode.execute(frame, cls, name, bases, dict, kwds);
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

        private CastToJavaIntExactNode ensureCastToIntNode() {
            if (castToInt == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToInt = insert(CastToJavaIntExactNode.create());
            }
            return castToInt;
        }

        private CastToJavaStringNode ensureCastToStringNode() {
            if (castToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToStringNode = insert(CastToJavaStringNodeGen.create());
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

        private IsAcceptableBaseNode ensureIsAcceptableBaseNode() {
            if (isAcceptableBaseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isAcceptableBaseNode = insert(IsAcceptableBaseNode.create());
            }
            return isAcceptableBaseNode;
        }
    }

    @Builtin(name = MODULE, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PythonModule, isPublic = false, //
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

    @Builtin(name = DICT_KEYS, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeysView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_KEYS));
        }
    }

    @Builtin(name = DICT_KEYITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictKeyIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictKeysIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_KEYITERATOR));
        }
    }

    @Builtin(name = DICT_VALUES, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValuesView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_VALUES));
        }
    }

    @Builtin(name = DICT_VALUEITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictValueIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictValuesIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_VALUEITERATOR));
        }
    }

    @Builtin(name = DICT_ITEMS, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemsView, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_ITEMS));
        }
    }

    @Builtin(name = DICT_ITEMITERATOR, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PDictItemIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class DictItemsIteratorTypeNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public Object dictKeys(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, parentheses(DICT_ITEMITERATOR));
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
            return "'iterator'";
        }
    }

    @Builtin(name = "arrayiterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PArrayIterator, isPublic = false)
    @GenerateNodeFactory
    public abstract static class ArrayIteratorTypeNode extends IteratorTypeNode {
        @Override
        protected String className() {
            return "'arrayiterator'";
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

    @Builtin(name = "builtin_function_or_method", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuiltinMethod, isPublic = false)
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
                        @Cached PythonObjectFactory factory) {
            return factory.createTraceback(pframe, lineno, lasti, next);
        }

        @Specialization
        static Object createTraceback(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") PNone next, PFrame pframe, int lasti, int lineno,
                        @Cached PythonObjectFactory factory) {
            return factory.createTraceback(pframe, lineno, lasti, null);
        }

        @Specialization(guards = {"!isPTraceback(next)", "!isNone(next)"})
        @SuppressWarnings("unused")
        Object errorNext(Object cls, Object next, Object frame, Object lasti, Object lineno) {
            throw raise(TypeError, "expected traceback object or None, got '%p'", next);
        }

        @Specialization(guards = "!isPFrame(frame)")
        @SuppressWarnings("unused")
        Object errorFrame(Object cls, Object next, Object frame, Object lasti, Object lineno) {
            throw raise(TypeError, "TracebackType() argument 'tb_frame' must be frame, not %p", frame);
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
                    "lnotab", "freevars", "cellvars"})
    @ArgumentClinic(name = "argcount", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "posonlyargcount", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "kwonlyargcount", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "nlocals", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "stacksize", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "filename", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "firstlineno", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CodeConstructorNode extends PythonClinicBuiltinNode {
        @Specialization
        PCode call(VirtualFrame frame, @SuppressWarnings("unused") Object cls, int argcount,
                        int posonlyargcount, int kwonlyargcount,
                        int nlocals, int stacksize, int flags,
                        PBytes codestring, PTuple constants, PTuple names,
                        PTuple varnames, String filename, String name,
                        int firstlineno, PBytes lnotab,
                        PTuple freevars, PTuple cellvars,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached CodeNodes.CreateCodeNode createCodeNode,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            byte[] codeBytes = bufferLib.getCopiedByteArray(codestring);
            byte[] lnotabBytes = bufferLib.getCopiedByteArray(lnotab);

            Object[] constantsArr = getObjectArrayNode.execute(constants);
            Object[] namesArr = getObjectArrayNode.execute(names);
            Object[] varnamesArr = getObjectArrayNode.execute(varnames);
            Object[] freevarsArr = getObjectArrayNode.execute(freevars);
            Object[] cellcarsArr = getObjectArrayNode.execute(cellvars);

            return createCodeNode.execute(frame, argcount, posonlyargcount, kwonlyargcount,
                            nlocals, stacksize, flags,
                            codeBytes, constantsArr, namesArr,
                            varnamesArr, freevarsArr, cellcarsArr,
                            filename, name, firstlineno,
                            lnotabBytes);
        }

        @Fallback
        @SuppressWarnings("unused")
        PCode call(Object cls, Object argcount, Object kwonlyargcount, Object posonlyargcount,
                        Object nlocals, Object stacksize, Object flags,
                        Object codestring, Object constants, Object names,
                        Object varnames, Object filename, Object name,
                        Object firstlineno, Object lnotab,
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
                throw new VarargsBuiltinDirectInvocationNotSupported();
            }
            if (arguments.length == 1) {
                return initNoArgs(arguments[0], PythonUtils.EMPTY_OBJECT_ARRAY, keywords);
            }
            Object[] argsWithoutSelf = PythonUtils.arrayCopyOfRange(arguments, 1, arguments.length);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }

        @Specialization(guards = "args.length == 0")
        Object initNoArgs(Object cls, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return factory().createBaseException(cls);
        }

        @Specialization(guards = "args.length != 0")
        Object initArgs(Object cls, Object[] args, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return factory().createBaseException(cls, factory().createTuple(args));
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
            throw raise(TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "mappingproxy()", "mapping", obj);
        }

        @Specialization(guards = "isNoValue(none)")
        @SuppressWarnings("unused")
        Object doMissing(Object klass, PNone none) {
            throw raise(TypeError, ErrorMessages.MISSING_D_REQUIRED_S_ARGUMENT_S_POS, "mappingproxy()", "mapping", 1);
        }
    }

    abstract static class DescriptorNode extends PythonBuiltinNode {
        @TruffleBoundary
        protected final void denyInstantiationAfterInitialization(String name) {
            if (getCore().isCoreInitialized()) {
                throw PRaiseNode.raiseUncached(this, TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, name);
            }
        }

        protected static Object ensure(Object value) {
            return value == PNone.NO_VALUE ? null : value;
        }
    }

    @Builtin(name = GETSET_DESCRIPTOR, constructsClass = PythonBuiltinClassType.GetSetDescriptor, isPublic = false, minNumOfPositionalArgs = 1, //
                    parameterNames = {"cls", "fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class GetSetDescriptorNode extends DescriptorNode {
        @Specialization(guards = "isPythonClass(owner)")
        @TruffleBoundary
        Object call(@SuppressWarnings("unused") Object clazz, Object get, Object set, String name, Object owner) {
            denyInstantiationAfterInitialization(GETSET_DESCRIPTOR);
            return PythonObjectFactory.getUncached().createGetSetDescriptor(ensure(get), ensure(set), name, owner);
        }
    }

    @Builtin(name = MEMBER_DESCRIPTOR, constructsClass = PythonBuiltinClassType.MemberDescriptor, isPublic = false, minNumOfPositionalArgs = 1, //
                    parameterNames = {"cls", "fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class MemberDescriptorNode extends DescriptorNode {
        @Specialization(guards = "isPythonClass(owner)")
        @TruffleBoundary
        Object doGeneric(@SuppressWarnings("unused") Object clazz, Object get, Object set, String name, Object owner) {
            denyInstantiationAfterInitialization(MEMBER_DESCRIPTOR);
            return PythonObjectFactory.getUncached().createGetSetDescriptor(ensure(get), ensure(set), name, owner);
        }
    }

    @Builtin(name = WRAPPER_DESCRIPTOR, constructsClass = PythonBuiltinClassType.WrapperDescriptor, isPublic = false, minNumOfPositionalArgs = 1, //
                    parameterNames = {"cls", "fget", "fset", "name", "owner"})
    @GenerateNodeFactory
    public abstract static class WrapperDescriptorNode extends DescriptorNode {
        @Specialization(guards = "isPythonClass(owner)")
        @TruffleBoundary
        Object doGeneric(@SuppressWarnings("unused") Object clazz, Object get, Object set, String name, Object owner) {
            denyInstantiationAfterInitialization(WRAPPER_DESCRIPTOR);
            return PythonObjectFactory.getUncached().createGetSetDescriptor(ensure(get), ensure(set), name, owner);
        }
    }

    // slice(stop)
    // slice(start, stop[, step])
    @Builtin(name = "slice", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, constructsClass = PythonBuiltinClassType.PSlice)
    @GenerateNodeFactory
    public abstract static class CreateSliceNode extends PythonBuiltinNode {

        @Specialization(guards = {"isNoValue(second)", "isNoValue(third)"})
        @SuppressWarnings("unused")
        static Object stop(VirtualFrame frame, Object cls, Object first, Object second, Object third,
                        @Cached SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, PNone.NONE, first, PNone.NONE);
        }

        @Specialization(guards = {"!isNoValue(second)", "isNoValue(third)"})
        @SuppressWarnings("unused")
        static Object startStop(VirtualFrame frame, Object cls, Object first, Object second, Object third,
                        @Cached SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, first, second, PNone.NONE);
        }

        @Specialization(guards = {"!isNoValue(second)", "!isNoValue(third)"})
        static Object slice(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object first, Object second, Object third,
                        @Cached SliceLiteralNode sliceNode) {
            return sliceNode.execute(frame, first, second, third);
        }
    }

    // buffer([iterable])
    @Builtin(name = "buffer", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PBuffer)
    @GenerateNodeFactory
    public abstract static class BufferNode extends PythonBuiltinNode {
        @Child private LookupInheritedSlotNode getSetItemNode;

        @Specialization(guards = "isNoValue(readOnly)")
        protected PBuffer construct(Object cls, Object delegate, @SuppressWarnings("unused") PNone readOnly) {
            return factory().createBuffer(cls, delegate, !hasSetItem(delegate));
        }

        @Specialization
        protected PBuffer construct(Object cls, Object delegate, boolean readOnly) {
            return factory().createBuffer(cls, delegate, readOnly);
        }

        @Fallback
        public PBuffer doGeneric(@SuppressWarnings("unused") Object cls, Object delegate, @SuppressWarnings("unused") Object readOnly) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_BUFFER_FOR, delegate);
        }

        public boolean hasSetItem(Object object) {
            if (getSetItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getSetItemNode = insert(LookupInheritedSlotNode.create(SpecialMethodSlot.SetItem));
            }
            return getSetItemNode.execute(object) != PNone.NO_VALUE;
        }
    }

    // memoryview(obj)
    @Builtin(name = MEMORYVIEW, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "object"}, constructsClass = PythonBuiltinClassType.PMemoryView)
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

        public static MemoryViewNode create() {
            return BuiltinConstructorsFactory.MemoryViewNodeFactory.create(null);
        }
    }

    // super()
    @Builtin(name = SUPER, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.Super)
    @GenerateNodeFactory
    public abstract static class SuperInitNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object object) {
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
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createClassmethod(self);
        }
    }

    @Builtin(name = INSTANCEMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PInstancemethod, isPublic = false, doc = "instancemethod(function)\n\nBind a function to a class.")
    @GenerateNodeFactory
    public abstract static class InstancemethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createInstancemethod(self);
        }
    }

    @Builtin(name = STATICMETHOD, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PStaticmethod)
    @GenerateNodeFactory
    public abstract static class StaticmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doObjectIndirect(Object self, @SuppressWarnings("unused") Object callable) {
            return factory().createStaticmethod(self);
        }
    }

    @Builtin(name = MAP, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PMap)
    @GenerateNodeFactory
    public abstract static class MapNode extends PythonVarargsBuiltinNode {
        @Specialization
        PMap doit(Object self, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] keywords) {
            return factory().createMap(self);
        }
    }

    @Builtin(name = PROPERTY, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PProperty)
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

    @TruffleBoundary
    private static String parentheses(String str) {
        return new StringBuilder("'").append(str).append("'").toString();
    }
}
