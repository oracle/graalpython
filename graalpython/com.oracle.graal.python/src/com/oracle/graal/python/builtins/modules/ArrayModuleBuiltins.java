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

import static com.oracle.graal.python.nodes.BuiltinNames.J_ARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ARRAY;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_AT_LEAST_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_AT_MOST_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_DECODE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.array.PArray.MachineFormat;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringCheckedNode;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J_ARRAY)
public final class ArrayModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule arrayModule = core.lookupBuiltinModule(T_ARRAY);
        arrayModule.setAttribute(tsLiteral("ArrayType"), core.lookupType(PythonBuiltinClassType.PArray));
        arrayModule.setAttribute(tsLiteral("typecodes"), tsLiteral("bBuhHiIlLqQfd"));
    }

    // array.array(typecode[, initializer])
    @Builtin(name = J_ARRAY, minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PArray, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ArrayNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        @Specialization(guards = "args.length == 1 || args.length == 2")
        Object array2(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasInitializerProfile,
                        @Cached InlineIsBuiltinClassProfile isNotSubtypeProfile,
                        @Cached CastToTruffleStringCheckedNode cast,
                        @Cached ArrayNodeInternal arrayNodeInternal) {
            checkKwargs(cls, kwargs, inliningTarget, isNotSubtypeProfile);
            Object initializer = hasInitializerProfile.profile(inliningTarget, args.length == 2) ? args[1] : PNone.NO_VALUE;
            return arrayNodeInternal.execute(frame, cls, cast.cast(inliningTarget, args[0], ErrorMessages.ARG_1_MUST_BE_UNICODE_NOT_P, args[0]), initializer);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object cls, Object[] args, PKeyword[] kwargs) {
            if (args.length < 2) {
                throw raise(TypeError, S_TAKES_AT_LEAST_D_ARGUMENTS_D_GIVEN, T_ARRAY, 2, args.length);
            } else {
                throw raise(TypeError, S_TAKES_AT_MOST_D_ARGUMENTS_D_GIVEN, T_ARRAY, 3, args.length);
            }
        }

        private void checkKwargs(Object cls, PKeyword[] kwargs, Node inliningTarget, InlineIsBuiltinClassProfile isNotSubtypeProfile) {
            if (isNotSubtypeProfile.profileIsBuiltinClass(inliningTarget, cls, PythonBuiltinClassType.PArray)) {
                if (kwargs.length != 0) {
                    throw raise(TypeError, S_TAKES_NO_KEYWORD_ARGS, "array.array()");
                }
            }
        }

        @Override
        public final Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            return execute(frame, arguments[0], splitArgsNode.executeCached(arguments), keywords);
        }

        @ImportStatic(PGuards.class)
        abstract static class ArrayNodeInternal extends Node {
            @Child private PRaiseNode raiseNode;
            @Child private PythonObjectFactory factory;
            @CompilationFinal private ValueProfile formatProfile = ValueProfile.createIdentityProfile();

            public abstract PArray execute(VirtualFrame frame, Object cls, TruffleString typeCode, Object initializer);

            @Specialization(guards = "isNoValue(initializer)")
            PArray array(Object cls, TruffleString typeCode, @SuppressWarnings("unused") PNone initializer,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                BufferFormat format = getFormatChecked(typeCode, lengthNode, atIndexNode);
                return getFactory().createArray(cls, typeCode, format);
            }

            @Specialization
            @InliningCutoff
            PArray arrayWithRangeInitializer(Object cls, TruffleString typeCode, PIntRange range,
                            @Bind("this") Node inliningTarget,
                            @Shared @Cached ArrayNodes.PutValueNode putValueNode,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                BufferFormat format = getFormatChecked(typeCode, lengthNode, atIndexNode);
                PArray array;
                try {
                    array = getFactory().createArray(cls, typeCode, format, range.getIntLength());
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }

                int start = range.getIntStart();
                int step = range.getIntStep();
                int len = range.getIntLength();

                for (int index = 0, value = start; index < len; index++, value += step) {
                    putValueNode.execute(null, inliningTarget, array, index, value);
                }

                return array;
            }

            @Specialization
            PArray arrayWithBytesInitializer(VirtualFrame frame, Object cls, TruffleString typeCode, PBytesLike bytes,
                            @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                PArray array = getFactory().createArray(cls, typeCode, getFormatChecked(typeCode, lengthNode, atIndexNode));
                fromBytesNode.executeWithoutClinic(frame, array, bytes);
                return array;
            }

            @Specialization(guards = "isString(initializer)")
            @InliningCutoff
            PArray arrayWithStringInitializer(VirtualFrame frame, Object cls, TruffleString typeCode, Object initializer,
                            @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                BufferFormat format = getFormatChecked(typeCode, lengthNode, atIndexNode);
                if (format != BufferFormat.UNICODE) {
                    throw raise(TypeError, ErrorMessages.CANNOT_USE_STR_TO_INITIALIZE_ARRAY, typeCode);
                }
                PArray array = getFactory().createArray(cls, typeCode, format);
                fromUnicodeNode.execute(frame, array, initializer);
                return array;
            }

            @Specialization
            @SuppressWarnings("truffle-static-method")
            @InliningCutoff
            PArray arrayArrayInitializer(VirtualFrame frame, Object cls, TruffleString typeCode, PArray initializer,
                            @Bind("this") Node inliningTarget,
                            @Shared @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached ArrayNodes.GetValueNode getValueNode,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                BufferFormat format = getFormatChecked(typeCode, lengthNode, atIndexNode);
                try {
                    PArray array = getFactory().createArray(cls, typeCode, format, initializer.getLength());
                    for (int i = 0; i < initializer.getLength(); i++) {
                        putValueNode.execute(frame, inliningTarget, array, i, getValueNode.execute(inliningTarget, initializer, i));
                    }
                    return array;
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }
            }

            @Specialization(guards = "!isBytes(initializer)")
            @SuppressWarnings("truffle-static-method")
            @InliningCutoff
            PArray arraySequenceInitializer(VirtualFrame frame, Object cls, TruffleString typeCode, PSequence initializer,
                            @Bind("this") Node inliningTarget,
                            @Shared @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                BufferFormat format = getFormatChecked(typeCode, lengthNode, atIndexNode);
                SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, initializer);
                int length = storage.length();
                try {
                    PArray array = getFactory().createArray(cls, typeCode, format, length);
                    for (int i = 0; i < length; i++) {
                        putValueNode.execute(frame, inliningTarget, array, i, getItemNode.execute(inliningTarget, storage, i));
                    }
                    return array;
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }
            }

            @Specialization(guards = {"!isBytes(initializer)", "!isString(initializer)", "!isPSequence(initializer)"})
            @SuppressWarnings("truffle-static-method")
            @InliningCutoff
            PArray arrayIteratorInitializer(VirtualFrame frame, Object cls, TruffleString typeCode, Object initializer,
                            @Bind("this") Node inliningTarget,
                            @Cached PyObjectGetIter getIter,
                            @Shared @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached GetNextNode nextNode,
                            @Cached IsBuiltinObjectProfile errorProfile,
                            @Shared @Cached TruffleString.CodePointLengthNode lengthNode,
                            @Shared @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
                Object iter = getIter.execute(frame, inliningTarget, initializer);

                BufferFormat format = getFormatChecked(typeCode, lengthNode, atIndexNode);
                PArray array = getFactory().createArray(cls, typeCode, format);

                int length = 0;
                while (true) {
                    Object nextValue;
                    try {
                        nextValue = nextNode.execute(frame, iter);
                    } catch (PException e) {
                        e.expectStopIteration(inliningTarget, errorProfile);
                        break;
                    }
                    try {
                        length = PythonUtils.addExact(length, 1);
                        array.resizeStorage(length);
                    } catch (OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raise(MemoryError);
                    }
                    putValueNode.execute(frame, inliningTarget, array, length - 1, nextValue);
                }

                array.setLength(length);
                return array;
            }

            private BufferFormat getFormatChecked(TruffleString typeCode, TruffleString.CodePointLengthNode lengthNode, TruffleString.CodePointAtIndexNode atIndexNode) {
                if (lengthNode.execute(typeCode, TS_ENCODING) != 1) {
                    throw raise(TypeError, ErrorMessages.ARRAY_ARG_1_MUST_BE_UNICODE);
                }
                BufferFormat format = BufferFormat.forArray(typeCode, lengthNode, atIndexNode);
                if (format == null) {
                    throw raise(ValueError, ErrorMessages.BAD_TYPECODE);
                }
                return formatProfile.profile(format);
            }

            private PException raise(PythonBuiltinClassType type, TruffleString message, Object... args) {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                throw raiseNode.raise(type, message, args);
            }

            private PException raise(PythonBuiltinClassType type) {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    raiseNode = insert(PRaiseNode.create());
                }
                throw raiseNode.raise(type);
            }

            private PythonObjectFactory getFactory() {
                if (factory == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    factory = insert(PythonObjectFactory.create());
                }
                return factory;
            }
        }
    }

    @Builtin(name = "_array_reconstructor", minNumOfPositionalArgs = 4, numOfPositionalOnlyArgs = 4, parameterNames = {"arrayType", "typeCode", "mformatCode", "items"})
    @ArgumentClinic(name = "typeCode", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "mformatCode", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class ArrayReconstructorNode extends PythonClinicBuiltinNode {
        @Specialization(guards = "mformatCode == cachedCode", limit = "3")
        @SuppressWarnings("truffle-static-method")
        Object reconstructCached(VirtualFrame frame, Object arrayType, TruffleString typeCode, @SuppressWarnings("unused") int mformatCode, PBytes bytes,
                        @Bind("this") Node inliningTarget,
                        @Cached("mformatCode") int cachedCode,
                        // Truffle lacks generic inline value profile, but it still warns that this
                        // can be inlined:
                        @Cached(value = "createIdentityProfile()", inline = false) ValueProfile formatProfile,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callDecode,
                        @Exclusive @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                        @Exclusive @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                        @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached ArrayBuiltins.ByteSwapNode byteSwapNode,
                        @Exclusive @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Exclusive @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
            BufferFormat format = BufferFormat.forArray(typeCode, lengthNode, atIndexNode);
            if (format == null) {
                throw raise(ValueError, ErrorMessages.BAD_TYPECODE);
            }
            return doReconstruct(frame, inliningTarget, arrayType, typeCode, cachedCode, bytes, callDecode, fromBytesNode, fromUnicodeNode, isSubtypeNode, byteSwapNode, formatProfile.profile(format));
        }

        @Specialization(replaces = "reconstructCached")
        @SuppressWarnings("truffle-static-method")
        Object reconstruct(VirtualFrame frame, Object arrayType, TruffleString typeCode, int mformatCode, PBytes bytes,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callDecode,
                        @Exclusive @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                        @Exclusive @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                        @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached ArrayBuiltins.ByteSwapNode byteSwapNode,
                        @Exclusive @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Exclusive @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
            BufferFormat format = BufferFormat.forArray(typeCode, lengthNode, atIndexNode);
            if (format == null) {
                throw raise(ValueError, ErrorMessages.BAD_TYPECODE);
            }
            return doReconstruct(frame, inliningTarget, arrayType, typeCode, mformatCode, bytes, callDecode, fromBytesNode, fromUnicodeNode, isSubtypeNode, byteSwapNode, format);
        }

        private Object doReconstruct(VirtualFrame frame, Node inliningTarget, Object arrayType, TruffleString typeCode, int mformatCode, PBytes bytes, PyObjectCallMethodObjArgs callDecode,
                        ArrayBuiltins.FromBytesNode fromBytesNode, ArrayBuiltins.FromUnicodeNode fromUnicodeNode, IsSubtypeNode isSubtypeNode,
                        ArrayBuiltins.ByteSwapNode byteSwapNode, BufferFormat format) {
            if (!isSubtypeNode.execute(frame, arrayType, PythonBuiltinClassType.PArray)) {
                throw raise(TypeError, ErrorMessages.N_NOT_SUBTYPE_OF_ARRAY, arrayType);
            }
            MachineFormat machineFormat = MachineFormat.fromCode(mformatCode);
            if (machineFormat != null) {
                PArray array;
                if (machineFormat == MachineFormat.forFormat(format)) {
                    array = factory().createArray(arrayType, typeCode, machineFormat.format);
                    fromBytesNode.executeWithoutClinic(frame, array, bytes);
                } else {
                    TruffleString newTypeCode = machineFormat.format == format ? typeCode : machineFormat.format.baseTypeCode;
                    array = factory().createArray(arrayType, newTypeCode, machineFormat.format);
                    if (machineFormat.unicodeEncoding != null) {
                        Object decoded = callDecode.execute(frame, inliningTarget, bytes, T_DECODE, machineFormat.unicodeEncoding);
                        fromUnicodeNode.execute(frame, array, decoded);
                    } else {
                        fromBytesNode.executeWithoutClinic(frame, array, bytes);
                        if (machineFormat.order != ByteOrder.nativeOrder()) {
                            byteSwapNode.execute(frame, array);
                        }
                    }
                }
                return array;
            } else {
                throw raise(ValueError, ErrorMessages.THIRD_ARG_MUST_BE_A_VALID_MACHINE_CODE_FMT);
            }
        }

        @Specialization(guards = "!isPBytes(value)")
        @SuppressWarnings("unused")
        Object error(Object arrayType, TruffleString typeCode, int mformatCode, Object value) {
            throw raise(TypeError, ErrorMessages.FOURTH_ARG_SHOULD_BE_BYTES, value);
        }

        protected static boolean isPBytes(Object obj) {
            return obj instanceof PBytes;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayModuleBuiltinsClinicProviders.ArrayReconstructorNodeClinicProviderGen.INSTANCE;
        }
    }
}
