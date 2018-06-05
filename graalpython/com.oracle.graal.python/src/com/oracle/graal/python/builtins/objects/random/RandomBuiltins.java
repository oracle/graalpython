package com.oracle.graal.python.builtins.objects.random;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PRandom.class)
public class RandomBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return RandomBuiltinsFactory.getFactories();
    }

    @Builtin(name = "seed", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class SeedNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        public PNone seed(PRandom random, PNone none) {
            random.setSeed(System.currentTimeMillis());
            return PNone.NONE;
        }

        @Specialization
        public PNone seed(PRandom random, int inputSeed) {
            random.setSeed(inputSeed);
            return PNone.NONE;
        }

        @Specialization
        public PNone seed(PRandom random, PInt inputSeed) {
            random.setSeed(inputSeed.longValue());
            return PNone.NONE;
        }

        @Specialization
        public PNone seed(PRandom random, double inputSeed) {
            random.setSeed((long) ((Long.MAX_VALUE - inputSeed) * 412316924));
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        public PNone seed(PRandom random, Object inputSeed) {
            random.setSeed(System.identityHashCode(inputSeed));
            return PNone.NONE;
        }
    }

    @Builtin(name = "setstate", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public PNone setstate(PRandom random, PTuple tuple) {
            Object[] arr = tuple.getArray();
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

    @Builtin(name = "getstate", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class GetStateNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public PTuple getstate(PRandom random) {
            return factory().createTuple(new Object[]{random.getSeed()});
        }
    }

    @Builtin(name = "random", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class RandomNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double random(PRandom random) {
            return random.nextDouble();
        }
    }

    @Builtin(name = "getrandbits", fixedNumOfArguments = 2)
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
