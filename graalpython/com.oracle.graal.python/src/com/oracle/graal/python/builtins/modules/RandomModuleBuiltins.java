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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Random;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "random")
public class RandomModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return RandomModuleBuiltinsFactory.getFactories();
    }

    // TODO: put the RNG into the context
    protected static java.util.Random javaRandom = new java.util.Random();

    @Builtin(name = "seed", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class SeedNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        public PNone seed(PNone none) {
            javaRandom.setSeed(System.currentTimeMillis());
            return PNone.NONE;
        }

        @Specialization
        public PNone seed(int inputSeed) {
            javaRandom.setSeed(inputSeed);
            return PNone.NONE;
        }

        @Specialization
        public PNone seed(PInt inputSeed) {
            javaRandom.setSeed(inputSeed.longValue());
            return PNone.NONE;
        }

        @Specialization
        public PNone seed(double inputSeed) {
            javaRandom.setSeed((long) ((Long.MAX_VALUE - inputSeed) * 412316924));
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        public PNone seed(Object inputSeed) {
            javaRandom.setSeed(System.identityHashCode(inputSeed));
            return PNone.NONE;
        }
    }

    @Builtin(name = "jumpahead", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class JumpAheadNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public PNone jumpahead(int jumps) {
            for (int i = jumps; i > 0; i--) {
                javaRandom.nextInt();
            }
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        public PNone jumpahead(double jumps) {
            for (double i = jumps; i > 0; i--) {
                javaRandom.nextInt();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "setstate", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public PNone setstate(PTuple tuple) {
            Object[] arr = tuple.getArray();
            if (arr.length == 1) {
                Object object = arr[0];
                if (object instanceof Long) {
                    javaRandom = new Random((Long) object);
                    return PNone.NONE;
                }
            }
            throw raise(SystemError, "state vector invalid.");
        }
    }

    // TODO: randrange is not part of _random
    @Builtin(name = "randrange", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class RandRangeNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public int randrange(int stop) {
            double scaled = javaRandom.nextDouble() * stop;

            while (scaled > stop) {
                scaled = javaRandom.nextDouble() * stop;
            }

            assert scaled <= stop;
            return (int) scaled;
        }

        @Specialization
        @TruffleBoundary
        public long randrange(long stop) {
            double scaled = javaRandom.nextDouble() * stop;

            while (scaled > stop) {
                scaled = javaRandom.nextDouble() * stop;
            }

            assert scaled <= stop;
            return (long) scaled;
        }

        @Specialization
        @TruffleBoundary
        public PInt randrange(PInt stop) {
            double stopDouble = stop.getValue().doubleValue();

            double scaled = javaRandom.nextDouble() * stopDouble;

            while (scaled > stopDouble) {
                scaled = javaRandom.nextDouble() * stopDouble;
            }

            assert scaled <= stopDouble;
            return factory().createInt(BigDecimal.valueOf(scaled).toBigInteger());
        }
    }

    @Builtin(name = "getstate", fixedNumOfArguments = 0)
    @GenerateNodeFactory
    public abstract static class GetStateNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        public PTuple getstate(PNone none) {
            return factory().createTuple(new Object[]{javaRandom.nextLong()});
        }
    }

    @Builtin(name = "random", fixedNumOfArguments = 0)
    @GenerateNodeFactory
    public abstract static class RandomNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double random() {
            return javaRandom.nextDouble();
        }
    }

    @Builtin(name = "getrandbits", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class GetRandBitsNode extends PythonBuiltinNode {

        @TruffleBoundary
        private static BigInteger createRandomBits(int k) {
            return new BigInteger(k, javaRandom);
        }

        @Specialization
        public PInt getrandbits(int k) {
            return factory().createInt(createRandomBits(k));
        }
    }

    @Builtin(name = "randint", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RandIntNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public int randint(int a, int b) {
            assert a <= b;
            return javaRandom.nextInt(b - a) + a;
        }
    }

    @Builtin(name = "uniform", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class UniformNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double uniform(double a, double b) {
            assert a <= b;
            return (b - a) * javaRandom.nextDouble() + a;
        }
    }

}
