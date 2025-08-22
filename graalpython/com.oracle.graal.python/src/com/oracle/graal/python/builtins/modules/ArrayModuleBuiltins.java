/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_DECODE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.array.PArray.MachineFormat;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
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

    @Builtin(name = "_array_reconstructor", minNumOfPositionalArgs = 4, numOfPositionalOnlyArgs = 4, parameterNames = {"arrayType", "typeCode", "mformatCode", "items"})
    @ArgumentClinic(name = "typeCode", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "mformatCode", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class ArrayReconstructorNode extends PythonClinicBuiltinNode {
        @Specialization(guards = "mformatCode == cachedCode", limit = "3")
        static Object reconstructCached(VirtualFrame frame, Object arrayType, TruffleString typeCode, @SuppressWarnings("unused") int mformatCode, PBytes bytes,
                        @Bind Node inliningTarget,
                        @Cached("mformatCode") int cachedCode,
                        // Truffle lacks generic inline value profile, but it still warns that this
                        // can be inlined:
                        @Cached(value = "createIdentityProfile()", inline = false) ValueProfile formatProfile,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callDecode,
                        @Exclusive @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                        @Exclusive @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                        @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached ArrayBuiltins.ByteSwapNode byteSwapNode,
                        @Exclusive @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Exclusive @Cached TruffleString.CodePointAtIndexNode atIndexNode,
                        @Exclusive @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            BufferFormat format = BufferFormat.forArray(typeCode, lengthNode, atIndexNode);
            if (format == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.BAD_TYPECODE);
            }
            return doReconstruct(frame, inliningTarget, arrayType, typeCode, cachedCode, bytes, callDecode, fromBytesNode, fromUnicodeNode, isTypeNode, isSubtypeNode, byteSwapNode,
                            formatProfile.profile(format),
                            getInstanceShape, raiseNode);
        }

        @Specialization(replaces = "reconstructCached")
        static Object reconstruct(VirtualFrame frame, Object arrayType, TruffleString typeCode, int mformatCode, PBytes bytes,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callDecode,
                        @Exclusive @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                        @Exclusive @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                        @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                        @Exclusive @Cached ArrayBuiltins.ByteSwapNode byteSwapNode,
                        @Exclusive @Cached TruffleString.CodePointLengthNode lengthNode,
                        @Exclusive @Cached TruffleString.CodePointAtIndexNode atIndexNode,
                        @Exclusive @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            BufferFormat format = BufferFormat.forArray(typeCode, lengthNode, atIndexNode);
            if (format == null) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.BAD_TYPECODE);
            }
            return doReconstruct(frame, inliningTarget, arrayType, typeCode, mformatCode, bytes, callDecode, fromBytesNode, fromUnicodeNode, isTypeNode, isSubtypeNode, byteSwapNode, format,
                            getInstanceShape,
                            raiseNode);
        }

        private static Object doReconstruct(VirtualFrame frame, Node inliningTarget, Object arrayType, TruffleString typeCode, int mformatCode, PBytes bytes, PyObjectCallMethodObjArgs callDecode,
                        ArrayBuiltins.FromBytesNode fromBytesNode, ArrayBuiltins.FromUnicodeNode fromUnicodeNode, TypeNodes.IsTypeNode isTypeNode, IsSubtypeNode isSubtypeNode,
                        ArrayBuiltins.ByteSwapNode byteSwapNode, BufferFormat format,
                        TypeNodes.GetInstanceShape getInstanceShape, PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, arrayType)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.FIRST_ARGUMENT_MUST_BE_A_TYPE_OBJECT_NOT_P, arrayType);
            }
            if (!isSubtypeNode.execute(arrayType, PythonBuiltinClassType.PArray)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_NOT_SUBTYPE_OF_ARRAY, arrayType);
            }
            MachineFormat machineFormat = MachineFormat.fromCode(mformatCode);
            if (machineFormat != null) {
                PArray array;
                if (machineFormat == MachineFormat.forFormat(format)) {
                    array = PFactory.createArray(PythonLanguage.get(inliningTarget), arrayType, getInstanceShape.execute(arrayType), typeCode, machineFormat.format);
                    fromBytesNode.executeWithoutClinic(frame, array, bytes);
                } else {
                    TruffleString newTypeCode = machineFormat.format == format ? typeCode : machineFormat.format.baseTypeCode;
                    array = PFactory.createArray(PythonLanguage.get(inliningTarget), arrayType, getInstanceShape.execute(arrayType), newTypeCode, machineFormat.format);
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
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.THIRD_ARG_MUST_BE_A_VALID_MACHINE_CODE_FMT);
            }
        }

        @Specialization(guards = "!isPBytes(value)")
        @SuppressWarnings("unused")
        static Object error(Object arrayType, TruffleString typeCode, int mformatCode, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FOURTH_ARG_SHOULD_BE_BYTES, value);
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
