/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.array;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PArray.class)
public class ArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBuiltinNode {
        @Specialization
        PLongArray doPArray(PLongArray left, PLongArray right) {
            long[] joined = new long[left.len() + right.len()];
            System.arraycopy(left.getSequence(), 0, joined, 0, left.len());
            System.arraycopy(right.getSequence(), 0, joined, left.len(), right.len());
            return factory().createLongArray(joined);
        }

        @Specialization
        PCharArray doPArray(PCharArray left, PCharArray right) {
            char[] joined = new char[left.len() + right.len()];
            System.arraycopy(left.getSequence(), 0, joined, 0, left.len());
            System.arraycopy(right.getSequence(), 0, joined, left.len(), right.len());
            return factory().createCharArray(joined);
        }

        @Specialization
        PDoubleArray doPArray(PDoubleArray left, PDoubleArray right) {
            double[] joined = new double[left.len() + right.len()];
            System.arraycopy(left.getSequence(), 0, joined, 0, left.len());
            System.arraycopy(right.getSequence(), 0, joined, left.len(), right.len());
            return factory().createDoubleArray(joined);
        }

        @Specialization
        PIntArray doPArray(PIntArray left, PIntArray right) {
            int[] joined = new int[left.len() + right.len()];
            System.arraycopy(left.getSequence(), 0, joined, 0, left.len());
            System.arraycopy(right.getSequence(), 0, joined, left.len(), right.len());
            return factory().createIntArray(joined);
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends PythonBuiltinNode {
        @Specialization
        PLongArray doIntPArray(PLongArray right, int left) {
            long[] newArray = new long[left * right.len()];
            int count = 0;
            for (int i = 0; i < left; i++) {
                for (int j = 0; j < right.len(); j++) {
                    newArray[count++] = right.getSequence()[i];
                }
            }
            return factory().createLongArray(newArray);
        }

        @Specialization
        PCharArray doIntPArray(PCharArray right, int left) {
            char[] newArray = new char[left * right.len()];
            int count = 0;
            for (int i = 0; i < left; i++) {
                for (int j = 0; j < right.len(); j++) {
                    newArray[count++] = right.getSequence()[i];
                }
            }
            return factory().createCharArray(newArray);
        }

        @Specialization
        PDoubleArray doIntPArray(PDoubleArray right, int left) {
            double[] newArray = new double[left * right.len()];
            int count = 0;
            for (int i = 0; i < left; i++) {
                for (int j = 0; j < right.len(); j++) {
                    newArray[count++] = right.getSequence()[i];
                }
            }
            return factory().createDoubleArray(newArray);
        }

        @Specialization
        PIntArray doIntPArray(PIntArray right, int left) {
            int[] newArray = new int[left * right.len()];
            int count = 0;
            for (int i = 0; i < left; i++) {
                for (int j = 0; j < right.len(); j++) {
                    newArray[count++] = right.getSequence()[i];
                }
            }
            return factory().createIntArray(newArray);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends RMulNode {
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PSequence self, Object other) {
            return self.index(other) != -1;
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PArray self, PSequence other) {
            return self.lessThan(other);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBuiltinNode {
        protected abstract boolean executeWith(Object left, Object right);

        @Specialization(guards = "areBothIntStorage(left,right)")
        boolean doPArrayInt(PArray left, PArray right) {
            IntSequenceStorage leftStore = (IntSequenceStorage) left.getSequenceStorage();
            IntSequenceStorage rightStore = (IntSequenceStorage) right.getSequenceStorage();
            return leftStore.equals(rightStore);
        }

        @Specialization(guards = "areBothByteStorage(left,right)")
        boolean doPArrayByte(PArray left, PArray right) {
            ByteSequenceStorage leftStore = (ByteSequenceStorage) left.getSequenceStorage();
            ByteSequenceStorage rightStore = (ByteSequenceStorage) right.getSequenceStorage();
            return leftStore.equals(rightStore);
        }

        @Specialization
        boolean doPArray(PArray left, PArray right,
                        @Cached("create(__EQ__, __EQ__)") LookupAndCallBinaryNode equalNode) {
            if (left.len() == right.len()) {
                for (int i = 0; i < left.len(); i++) {
                    if (equalNode.executeObject(left.getItem(i), right.getItem(i)) != Boolean.TRUE) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    @Builtin(name = __STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String str(PArray self) {
            // TODO: this needs to be enhanced, but it is slow path and not critical for now
            // mostly cosmetic
            String typeCode = "?";
            String array = "?";
            SequenceStorage sequenceStorage = self.getSequenceStorage();
            if (sequenceStorage instanceof IntSequenceStorage) {
                typeCode = "i";
                array = Arrays.toString(((IntSequenceStorage) sequenceStorage).getInternalIntArray());
            } else if (sequenceStorage instanceof ByteSequenceStorage) {
                typeCode = "b";
                array = Arrays.toString(((ByteSequenceStorage) sequenceStorage).getInternalByteArray());
            } else if (sequenceStorage instanceof DoubleSequenceStorage) {
                typeCode = "d";
                array = Arrays.toString(((DoubleSequenceStorage) sequenceStorage).getInternalDoubleArray());
            }
            return String.format("array('%s', %s)", typeCode, array);
        }
    }
}
