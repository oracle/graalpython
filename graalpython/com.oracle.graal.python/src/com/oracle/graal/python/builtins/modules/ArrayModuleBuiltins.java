/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_AT_LEAST_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_AT_MOST_D_ARGUMENTS_D_GIVEN;
import static com.oracle.graal.python.nodes.ErrorMessages.S_TAKES_NO_KEYWORD_ARGS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

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
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = "array")
public final class ArrayModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule arrayModule = core.lookupBuiltinModule("array");
        arrayModule.setAttribute("ArrayType", core.lookupType(PythonBuiltinClassType.PArray));
        arrayModule.setAttribute("typecodes", "bBuhHiIlLqQfd");
    }

    // array.array(typecode[, initializer])
    @Builtin(name = "array", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PArray, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class ArrayNode extends PythonVarargsBuiltinNode {
        @Child private SplitArgsNode splitArgsNode;

        @Specialization(guards = "args.length == 1")
        Object array2(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kwargs,
                        @Cached IsBuiltinClassProfile isNotSubtypeProfile,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast,
                        @Cached ArrayNodeInternal arrayNodeInternal) {
            checkKwargs(cls, kwargs, isNotSubtypeProfile);
            return arrayNodeInternal.execute(frame, cls, cast.cast(args[0], "array() argument 1 must be a unicode character, not %p", args[0]), PNone.NO_VALUE);
        }

        @Specialization(guards = "args.length == 2")
        Object array3(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kwargs,
                        @Cached IsBuiltinClassProfile isNotSubtypeProfile,
                        @Cached StringNodes.CastToJavaStringCheckedNode cast,
                        @Cached ArrayNodeInternal arrayNodeInternal) {
            checkKwargs(cls, kwargs, isNotSubtypeProfile);
            return arrayNodeInternal.execute(frame, cls, cast.cast(args[0], "array() argument 1 must be a unicode character, not %p", args[0]), args[1]);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object cls, Object[] args, PKeyword[] kwargs) {
            if (args.length < 2) {
                throw raise(TypeError, S_TAKES_AT_LEAST_D_ARGUMENTS_D_GIVEN, "array", 2, args.length);
            } else {
                throw raise(TypeError, S_TAKES_AT_MOST_D_ARGUMENTS_D_GIVEN, "array", 3, args.length);
            }
        }

        private void checkKwargs(Object cls, PKeyword[] kwargs, IsBuiltinClassProfile isNotSubtypeProfile) {
            if (isNotSubtypeProfile.profileClass(cls, PythonBuiltinClassType.PArray)) {
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
            return execute(frame, arguments[0], splitArgsNode.execute(arguments), keywords);
        }

        @ImportStatic(PGuards.class)
        abstract static class ArrayNodeInternal extends Node {
            @Child private PRaiseNode raiseNode;
            @Child private PythonObjectFactory factory;
            @CompilationFinal private ValueProfile formatProfile = ValueProfile.createIdentityProfile();

            public abstract PArray execute(VirtualFrame frame, Object cls, String typeCode, Object initializer);

            @Specialization(guards = "isNoValue(initializer)")
            PArray array(Object cls, String typeCode, @SuppressWarnings("unused") PNone initializer) {
                BufferFormat format = getFormatChecked(typeCode);
                return getFactory().createArray(cls, typeCode, format);
            }

            @Specialization
            PArray arrayWithRangeInitializer(Object cls, String typeCode, PIntRange range,
                            @Cached ArrayNodes.PutValueNode putValueNode) {
                BufferFormat format = getFormatChecked(typeCode);
                PArray array;
                try {
                    array = getFactory().createArray(cls, typeCode, format, range.getIntLength());
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }

                int start = range.getIntStart();
                int stop = range.getIntStop();
                int step = range.getIntStep();

                for (int index = 0, value = start; value < stop; index++, value += step) {
                    putValueNode.execute(null, array, index, value);
                }

                return array;
            }

            @Specialization
            PArray arrayWithBytesInitializer(VirtualFrame frame, Object cls, String typeCode, PBytesLike bytes,
                            @Cached ArrayBuiltins.FromBytesNode fromBytesNode) {
                PArray array = getFactory().createArray(cls, typeCode, getFormatChecked(typeCode));
                fromBytesNode.executeWithoutClinic(frame, array, bytes);
                return array;
            }

            @Specialization(guards = "isString(initializer)")
            PArray arrayWithStringInitializer(VirtualFrame frame, Object cls, String typeCode, Object initializer,
                            @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode) {
                BufferFormat format = getFormatChecked(typeCode);
                if (format != BufferFormat.UNICODE) {
                    throw raise(TypeError, "cannot use a str to initialize an array with typecode '%s'", typeCode);
                }
                PArray array = getFactory().createArray(cls, typeCode, format);
                fromUnicodeNode.execute(frame, array, initializer);
                return array;
            }

            @Specialization
            PArray arrayArrayInitializer(VirtualFrame frame, Object cls, String typeCode, PArray initializer,
                            @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached ArrayNodes.GetValueNode getValueNode) {
                BufferFormat format = getFormatChecked(typeCode);
                try {
                    PArray array = getFactory().createArray(cls, typeCode, format, initializer.getLength());
                    for (int i = 0; i < initializer.getLength(); i++) {
                        putValueNode.execute(frame, array, i, getValueNode.execute(initializer, i));
                    }
                    return array;
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }
            }

            @Specialization
            PArray arraySequenceInitializer(VirtualFrame frame, Object cls, String typeCode, PSequence initializer,
                            @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                            @Cached SequenceStorageNodes.LenNode lenNode,
                            @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
                BufferFormat format = getFormatChecked(typeCode);
                SequenceStorage storage = getSequenceStorageNode.execute(initializer);
                int length = lenNode.execute(storage);
                try {
                    PArray array = getFactory().createArray(cls, typeCode, format, length);
                    for (int i = 0; i < length; i++) {
                        putValueNode.execute(frame, array, i, getItemNode.execute(storage, i));
                    }
                    return array;
                } catch (OverflowException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raise(MemoryError);
                }
            }

            @Specialization(guards = {"!isBytes(initializer)", "!isString(initializer)"})
            PArray arrayIteratorInitializer(VirtualFrame frame, Object cls, String typeCode, Object initializer,
                            @Cached PyObjectGetIter getIter,
                            @Cached ArrayNodes.PutValueNode putValueNode,
                            @Cached GetNextNode nextNode,
                            @Cached IsBuiltinClassProfile errorProfile) {
                Object iter = getIter.execute(frame, initializer);

                BufferFormat format = getFormatChecked(typeCode);
                PArray array = getFactory().createArray(cls, typeCode, format);

                int length = 0;
                while (true) {
                    Object nextValue;
                    try {
                        nextValue = nextNode.execute(frame, iter);
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile);
                        break;
                    }
                    try {
                        length = PythonUtils.addExact(length, 1);
                        array.resizeStorage(length);
                    } catch (OverflowException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raise(MemoryError);
                    }
                    putValueNode.execute(frame, array, length - 1, nextValue);
                }

                array.setLength(length);
                return array;
            }

            private BufferFormat getFormatChecked(String typeCode) {
                if (typeCode.length() != 1) {
                    throw raise(TypeError, "array() argument 1 must be a unicode character, not str");
                }
                BufferFormat format = BufferFormat.forArray(typeCode);
                if (format == null) {
                    throw raise(ValueError, "bad typecode (must be b, B, u, h, H, i, I, l, L, q, Q, f or d)");
                }
                return formatProfile.profile(format);
            }

            private PException raise(PythonBuiltinClassType type, String message, Object... args) {
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
    @ArgumentClinic(name = "typeCode", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "mformatCode", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class ArrayReconstructorNode extends PythonClinicBuiltinNode {
        @Specialization(guards = "mformatCode == cachedCode")
        Object reconstructCached(VirtualFrame frame, Object arrayType, String typeCode, @SuppressWarnings("unused") int mformatCode, PBytes bytes,
                        @Cached("mformatCode") int cachedCode,
                        @Cached("createIdentityProfile()") ValueProfile formatProfile,
                        @Cached PyObjectCallMethodObjArgs callDecode,
                        @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                        @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached ArrayBuiltins.ByteSwapNode byteSwapNode) {
            BufferFormat format = BufferFormat.forArray(typeCode);
            if (format == null) {
                throw raise(ValueError, "bad typecode (must be b, B, u, h, H, i, I, l, L, q, Q, f or d)");
            }
            return doReconstruct(frame, arrayType, typeCode, cachedCode, bytes, callDecode, fromBytesNode, fromUnicodeNode, isSubtypeNode, byteSwapNode, formatProfile.profile(format));
        }

        @Specialization(replaces = "reconstructCached")
        Object reconstruct(VirtualFrame frame, Object arrayType, String typeCode, int mformatCode, PBytes bytes,
                        @Cached PyObjectCallMethodObjArgs callDecode,
                        @Cached ArrayBuiltins.FromBytesNode fromBytesNode,
                        @Cached ArrayBuiltins.FromUnicodeNode fromUnicodeNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached ArrayBuiltins.ByteSwapNode byteSwapNode) {
            BufferFormat format = BufferFormat.forArray(typeCode);
            if (format == null) {
                throw raise(ValueError, "bad typecode (must be b, B, u, h, H, i, I, l, L, q, Q, f or d)");
            }
            return doReconstruct(frame, arrayType, typeCode, mformatCode, bytes, callDecode, fromBytesNode, fromUnicodeNode, isSubtypeNode, byteSwapNode, format);
        }

        private Object doReconstruct(VirtualFrame frame, Object arrayType, String typeCode, int mformatCode, PBytes bytes, PyObjectCallMethodObjArgs callDecode,
                        ArrayBuiltins.FromBytesNode fromBytesNode, ArrayBuiltins.FromUnicodeNode fromUnicodeNode, IsSubtypeNode isSubtypeNode,
                        ArrayBuiltins.ByteSwapNode byteSwapNode, BufferFormat format) {
            if (!isSubtypeNode.execute(frame, arrayType, PythonBuiltinClassType.PArray)) {
                throw raise(TypeError, "%n is not a subtype of array", arrayType);
            }
            MachineFormat machineFormat = MachineFormat.fromCode(mformatCode);
            if (machineFormat != null) {
                PArray array;
                if (machineFormat == MachineFormat.forFormat(format)) {
                    array = factory().createArray(arrayType, typeCode, machineFormat.format);
                    fromBytesNode.executeWithoutClinic(frame, array, bytes);
                } else {
                    String newTypeCode = machineFormat.format == format ? typeCode : machineFormat.format.baseTypeCode;
                    array = factory().createArray(arrayType, newTypeCode, machineFormat.format);
                    if (machineFormat.unicodeEncoding != null) {
                        Object decoded = callDecode.execute(frame, bytes, "decode", machineFormat.unicodeEncoding);
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
                throw raise(ValueError, "third argument must be a valid machine format code.");
            }
        }

        @Specialization(guards = "!isPBytes(value)")
        @SuppressWarnings("unused")
        Object error(Object arrayType, String typeCode, int mformatCode, Object value) {
            throw raise(TypeError, "fourth argument should be bytes, not %p", value);
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
