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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "array")
public final class ArrayModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayModuleBuiltinsFactory.getFactories();
    }

    // array.array(typecode[, initializer])
    @Builtin(name = "array", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PArray)
    @GenerateNodeFactory
    abstract static class PythonArrayNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(initializer)")
        PArray array(PythonClass cls, String typeCode, @SuppressWarnings("unused") PNone initializer) {
            /**
             * TODO @param typeCode should be a char, not a string
             */
            return makeEmptyArray(cls, typeCode.charAt(0));
        }

        @Specialization
        PArray arrayWithRangeInitializer(PythonClass cls, String typeCode, PRange range) {
            if (!typeCode.equals("i")) {
                typeError(typeCode, range);
            }

            int[] intArray = new int[range.len()];

            int start = range.getStart();
            int stop = range.getStop();
            int step = range.getStep();

            int index = 0;
            for (int i = start; i < stop; i += step) {
                intArray[index++] = i;
            }

            return factory().createArray(cls, intArray);
        }

        @Specialization
        PArray arrayWithSequenceInitializer(PythonClass cls, String typeCode, String str) {
            if (!typeCode.equals("c")) {
                typeError(typeCode, str);
            }

            return factory().createArray(cls, str.toCharArray());
        }

        /**
         * @param cls
         */
        @Specialization
        PArray arrayWithSequenceInitializer(PythonClass cls, String typeCode, PSequence initializer,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            SequenceStorage store;
            switch (typeCode.charAt(0)) {
                case 'i':
                    Object iter = getIterator.executeWith(initializer);
                    int[] intArray = new int[initializer.len()];
                    int i = 0;

                    while (true) {
                        Object nextValue;
                        try {
                            nextValue = next.execute(iter);
                        } catch (PException e) {
                            e.expectStopIteration(getCore(), errorProfile);
                            break;
                        }
                        if (nextValue instanceof Integer) {
                            intArray[i++] = (int) nextValue;
                        } else {
                            CompilerDirectives.transferToInterpreter();
                            operandTypeError();
                        }
                    }

                    return factory().createArray(cls, intArray);
                case 'd':
                    store = initializer.getSequenceStorage();
                    double[] doubleArray = new double[store.length()];

                    for (i = 0; i < doubleArray.length; i++) {
                        Object val = store.getItemNormalized(i);
                        doubleArray[i] = (double) val;
                    }

                    return factory().createArray(cls, doubleArray);
                case 'b':
                    store = initializer.getSequenceStorage();
                    byte[] byteArray = new byte[store.length()];

                    for (i = 0; i < byteArray.length; i++) {
                        Object val = store.getItemNormalized(i);
                        if (val instanceof Number) {
                            byteArray[i] = ((Number) val).byteValue();
                        } else {
                            throw raise(ValueError, "byte value expected");
                        }
                    }

                    return factory().createArray(cls, byteArray);
                default:
                    return null;
            }
        }

        @Specialization
        @TruffleBoundary
        PArray arrayWithObjectInitializer(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") String typeCode, Object initializer) {
            throw new RuntimeException("Unsupported initializer " + initializer);
        }

        private PArray makeEmptyArray(PythonClass cls, char type) {
            switch (type) {
                case 'c':
                case 'b':
                case 'B':
                    return factory().createArray(cls, new char[0]);
                case 'i':
                    return factory().createArray(cls, new int[0]);
                case 'd':
                    return factory().createArray(cls, new double[0]);
                default:
                    return null;
            }
        }

        @TruffleBoundary
        private void typeError(String typeCode, Object initializer) {
            throw raise(TypeError, "unsupported operand type: %s %s and 'array.array'", typeCode, initializer);
        }

        @TruffleBoundary
        private static void operandTypeError() {
            throw new RuntimeException("Unexpected argument type for array() ");
        }
    }
}
