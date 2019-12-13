/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.random;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRandom)
public class RandomBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return RandomBuiltinsFactory.getFactories();
    }

    @Builtin(name = "seed", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SeedNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        PNone seed(PRandom random, @SuppressWarnings("unused") PNone none) {
            random.setSeed(System.currentTimeMillis());
            return PNone.NONE;
        }

        @Specialization
        PNone seed(PRandom random, long inputSeed) {
            random.setSeed(inputSeed);
            return PNone.NONE;
        }

        @Specialization
        PNone seed(PRandom random, PInt inputSeed) {
            random.setSeed(inputSeed.longValue());
            return PNone.NONE;
        }

        @Specialization
        PNone seed(PRandom random, double inputSeed) {
            random.setSeed((long) ((Long.MAX_VALUE - inputSeed) * 412316924));
            return PNone.NONE;
        }

        @Child PythonObjectLibrary objectLib;

        @Fallback
        PNone seedNonLong(VirtualFrame frame, Object random, Object inputSeed) {
            if (random instanceof PRandom) {
                if (objectLib == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    objectLib = insert(PythonObjectLibrary.getFactory().createDispatched(PythonOptions.getCallSiteInlineCacheMaxDepth()));
                }
                long hash = objectLib.hashWithState(inputSeed, PArguments.getThreadState(frame));
                ((PRandom) random).setSeed(hash);
                return PNone.NONE;
            } else {
                throw raise(PythonErrorType.TypeError, "descriptor 'seed' requires a '_random.Random' object but received a '%p'", random);
            }
        }
    }

    @Builtin(name = "setstate", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBuiltinNode {

        @Specialization
        public PNone setstate(PRandom random, PTuple tuple,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            Object[] arr = getObjectArrayNode.execute(tuple);
            if (arr.length == 1) {
                Object object = arr[0];
                if (object instanceof Long) {
                    random.resetJavaRandom();
                    random.setSeed((Long) object);
                    return PNone.NONE;
                }
            }
            throw raise(PythonErrorType.SystemError, "state vector invalid.");
        }
    }

    @Builtin(name = "getstate", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetStateNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public PTuple getstate(PRandom random) {
            return factory().createTuple(new Object[]{random.getSeed()});
        }
    }

    @Builtin(name = "random", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RandomNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double random(PRandom random) {
            return random.nextDouble();
        }
    }

    @Builtin(name = "getrandbits", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetRandBitsNode extends PythonBuiltinNode {

        @TruffleBoundary
        private static BigInteger createRandomBits(PRandom random, int k) {
            return new BigInteger(k, random.getJavaRandom());
        }

        @Specialization
        public PInt getrandbits(PRandom random, int k) {
            return factory().createInt(createRandomBits(random, k));
        }
    }
}
