/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.ArrayBuiltins;
import com.oracle.graal.python.builtins.objects.array.ArrayNodes;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "array")
public final class ArrayModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayModuleBuiltinsFactory.getFactories();
    }

    // array.array(typecode[, initializer])
    @Builtin(name = "array", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, constructsClass = PythonBuiltinClassType.PArray, parameterNames = {"$cls", "typeCode", "initializer"})
    @ArgumentClinic(name = "typeCode", conversion = ArgumentClinic.ClinicConversion.String)
    @GenerateNodeFactory
    abstract static class ArrayNode extends PythonTernaryClinicBuiltinNode {

        @Specialization(guards = "isNoValue(initializer)")
        PArray array(Object cls, String typeCode, @SuppressWarnings("unused") PNone initializer) {
            BufferFormat format = getFormatChecked(typeCode);
            return factory().createArray(cls, typeCode, format);
        }

        @Specialization
        PArray arrayWithRangeInitializer(Object cls, String typeCode, PIntRange range,
                        @Cached ArrayNodes.PutValueNode putValueNode) {
            BufferFormat format = getFormatChecked(typeCode);
            PArray array = factory().createArray(cls, typeCode, format, range.getIntLength());

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
            PArray array = factory().createArray(cls, typeCode, getFormatChecked(typeCode));
            fromBytesNode.execute(frame, array, bytes);
            return array;
        }

        // TODO impl for PSequence and PArray or use lenght_hint

        @Specialization(limit = "3")
        PArray arrayIteratorInitializer(VirtualFrame frame, Object cls, String typeCode, Object initializer,
                        @CachedLibrary("initializer") PythonObjectLibrary lib,
                        @Cached ArrayNodes.PutValueNode putValueNode,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object iter = lib.getIteratorWithFrame(initializer, frame);

            BufferFormat format = BufferFormat.forArray(typeCode);
            PArray array = factory().createArray(cls, typeCode, format);

            int lenght = 0;
            while (true) {
                Object nextValue;
                try {
                    nextValue = nextNode.execute(frame, iter);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
                array.ensureCapacity(++lenght);
                putValueNode.execute(frame, array, lenght - 1, nextValue);
            }

            array.setLenght(lenght);
            return array;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object cls, Object typeCode, Object initializer) {
            throw raise(TypeError, "array() argument 1 must be a unicode character, not int");
        }

        private BufferFormat getFormatChecked(String typeCode) {
            BufferFormat format = BufferFormat.forArray(typeCode);
            if (format == null) {
                throw raise(ValueError, "bad typecode (must be b, B, u, h, H, i, I, l, L, q, Q, f or d)");
            }
            return format;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayModuleBuiltinsClinicProviders.ArrayNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "_array_reconstructor", minNumOfPositionalArgs = 4, numOfPositionalOnlyArgs = 4, parameterNames = {"arrayType", "typeCode", "mformatCode", "items"})
    @ArgumentClinic(name = "typeCode", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "mformatCode", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class ArrayReconstructorNode extends PythonClinicBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object reconstruct(Object arrayType, String typeCode, int mformatCode, Object items) {
            // TODO
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ArrayModuleBuiltinsClinicProviders.ArrayReconstructorNodeClinicProviderGen.INSTANCE;
        }
    }
}
