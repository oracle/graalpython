package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.objects.ints.IntBuiltins.MulNode.mul;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;

/**
 * Helper class with shared fast-paths. Must be public so that it is accessible by the Bytecode DSL
 * generated code.
 */
@GenerateCached(false)
@TypeSystemReference(PythonIntegerTypes.class)
public abstract class PyNumberMultiplyFastPaths extends BinaryOpNode {

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins
     */
    @Specialization(rewriteOn = ArithmeticException.class)
    public static int doII(int x, int y) throws ArithmeticException {
        return Math.multiplyExact(x, y);
    }

    @Specialization(replaces = "doII")
    public static long doIIL(int x, int y) {
        return x * (long) y;
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public static long doLL(long x, long y) {
        return Math.multiplyExact(x, y);
    }

    @Specialization(replaces = "doLL")
    public static Object doLongWithOverflow(long x, long y,
                    @Bind("this") Node inliningTarget) {
        /* Inlined version of Math.multiplyExact(x, y) with BigInteger fallback. */
        long r = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 != 0)) {
            // Some bits greater than 2^31 that might cause overflow
            // Check the result using the divide operator
            // and check for the special case of Long.MIN_VALUE * -1
            if (((y != 0) && (r / y != x)) ||
                            (x == Long.MIN_VALUE && y == -1)) {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), mul(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
        }
        return r;
    }

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in FloatBuiltins
     */
    @Specialization
    public static double doDL(double left, long right) {
        return left * right;
    }

    @Specialization
    public static double doLD(long left, double right) {
        return left * right;
    }

    @Specialization
    public static double doDD(double left, double right) {
        return left * right;
    }
}
