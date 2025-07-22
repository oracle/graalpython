/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.SpecialMethodNotFound;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRandom)
public final class RandomBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = RandomBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RandomBuiltinsFactory.getFactories();
    }

    // _random.Random([seed])
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "Random", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class PRandomNode extends PythonBuiltinNode {
        private static final TruffleString T_SEED = tsLiteral("seed");

        @Child LookupAndCallBinaryNode setSeed = LookupAndCallBinaryNode.create(T_SEED);

        @Specialization
        PRandom random(VirtualFrame frame, Object cls, Object seed,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PRandom random = PFactory.createRandom(language, cls, getInstanceShape.execute(cls));
            try {
                setSeed.executeObject(frame, random, seed != PNone.NO_VALUE ? seed : PNone.NONE);
            } catch (SpecialMethodNotFound ignore) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.SystemError);
            }
            return random;
        }
    }

    @Builtin(name = "seed", minNumOfPositionalArgs = 1, parameterNames = {"$self", "seed"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class SeedNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PNone seedNone(PRandom random, @SuppressWarnings("unused") PNone none) {
            SecureRandom secureRandom = getContext().getSecureRandom();
            int[] seed = new int[PRandom.N];
            for (int i = 0; i < seed.length; ++i) {
                seed[i] = secureRandom.nextInt();
            }
            random.seed(seed);
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        static PNone seedLong(PRandom random, long inputSeed) {
            long absSeed = Math.abs(inputSeed);
            // absSeed is negative if inputSeed is Long.MIN_VALUE (-2^63), but its bit pattern
            // 0x8000000000000000 still represents positive 2^63 if interpreted as unsigned long
            int hi = (int) (absSeed >>> 32);
            int lo = (int) absSeed;
            if (hi == 0) {
                random.seed(new int[]{lo});
            } else {
                random.seed(new int[]{lo, hi});
            }
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        static PNone seedBigInteger(PRandom random, PInt inputSeed) {
            byte[] bytes = inputSeed.abs().toByteArray();
            int startPos = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
            int numberOfBytes = bytes.length - startPos;
            int numberOfInts = (numberOfBytes + 3) >> 2;

            ByteBuffer bb = ByteBuffer.allocate(numberOfInts * 4);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.position(bb.capacity() - numberOfBytes);
            bb.put(bytes, startPos, numberOfBytes);
            bb.rewind();

            int[] ints = new int[numberOfInts];
            for (int i = numberOfInts - 1; i >= 0; --i) {
                ints[i] = bb.getInt();
            }
            random.seed(ints);
            return PNone.NONE;
        }

        @Specialization(guards = {"!canBeInteger(inputSeed)", "!isPNone(inputSeed)"})
        static PNone seedGeneric(VirtualFrame frame, PRandom random, Object inputSeed,
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hash) {
            return seedLong(random, hash.execute(frame, inliningTarget, inputSeed));
        }
    }

    @Builtin(name = "setstate", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBuiltinNode {

        @Specialization
        static PNone setstate(PRandom random, PTuple tuple,
                        @Bind Node inliningTarget,
                        @Cached GetObjectArrayNode getObjectArrayNode,
                        @Cached CastToJavaUnsignedLongNode castNode,
                        @Cached PRaiseNode raiseNode) {
            Object[] arr = getObjectArrayNode.execute(inliningTarget, tuple);
            if (arr.length != PRandom.N + 1) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.STATE_VECTOR_INVALID);
            }
            int[] state = new int[PRandom.N];
            for (int i = 0; i < PRandom.N; ++i) {
                long l = castNode.execute(inliningTarget, arr[i]);
                state[i] = (int) l;
            }
            long index = castNode.execute(inliningTarget, arr[PRandom.N]);
            if (index < 0 || index > PRandom.N) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.STATE_VECTOR_INVALID);
            }
            random.restore(state, (int) index);
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object setstate(Object random, Object state,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.STATE_VECTOR_MUST_BE_A_TUPLE);
        }
    }

    @Builtin(name = "getstate", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetStateNode extends PythonBuiltinNode {

        @Specialization
        static PTuple getstate(PRandom random,
                        @Bind PythonLanguage language) {
            return PFactory.createTuple(language, encodeState(random));
        }

        @TruffleBoundary
        private static Object[] encodeState(PRandom random) {
            int[] state = random.getState();
            Object[] encodedState = new Object[PRandom.N + 1];
            for (int i = 0; i < PRandom.N; ++i) {
                if (state[i] < 0) {
                    encodedState[i] = state[i] & 0xFFFFFFFFL;
                } else {
                    encodedState[i] = state[i];
                }
            }
            encodedState[PRandom.N] = random.getIndex();
            return encodedState;
        }
    }

    @Builtin(name = "random", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RandomNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        double random(PRandom random) {
            return random.nextDouble();
        }
    }

    @Builtin(name = "getrandbits", minNumOfPositionalArgs = 2, parameterNames = {"$self", "k"})
    @ArgumentClinic(name = "k", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class GetRandBitsNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return RandomBuiltinsClinicProviders.GetRandBitsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "k < 0")
        @SuppressWarnings("unused")
        static int negative(PRandom random, int k,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.NUMBER_OF_BITS_MUST_BE_NON_NEGATIVE);
        }

        @Specialization(guards = "k == 0")
        @SuppressWarnings("unused")
        static int zero(PRandom random, int k) {
            return 0;
        }

        @Specialization(guards = {"k >= 1", "k <= 31"})
        @TruffleBoundary
        static int genInt(PRandom random, int k) {
            return random.nextInt() >>> (32 - k);
        }

        @Specialization(guards = "k == 32")
        @TruffleBoundary
        static long gen32Bits(PRandom random, @SuppressWarnings("unused") int k) {
            return random.nextInt() & 0xFFFFFFFFL;
        }

        @Specialization(guards = {"k >= 33", "k <= 63"})
        @TruffleBoundary
        static long genLong(PRandom random, int k) {
            long x = random.nextInt() & 0xFFFFFFFFL;
            long y = random.nextInt() >>> (64 - k);
            return (y << 32) | x;
        }

        @Specialization(guards = "k >= 64")
        @TruffleBoundary
        static PInt genBigInteger(PRandom random, int k) {
            int ints = ((k + 31) / 32);
            ByteBuffer bb = ByteBuffer.wrap(new byte[4 * ints]).order(ByteOrder.BIG_ENDIAN);
            for (int i = ints - 1; i > 0; --i) {
                int x = random.nextInt();
                bb.putInt(4 * i, x);
            }
            bb.putInt(0, random.nextInt() >>> (32 - (k % 32)));
            return PFactory.createInt(PythonLanguage.get(null), new BigInteger(1, bb.array()));
        }
    }
}
