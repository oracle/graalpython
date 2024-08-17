package com.oracle.graal.python.lib;

import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

/**
 * Helper class with shared fast-paths. Must be public so that it is accessible by the Bytecode DSL
 * generated code.
 */
@GenerateCached(false)
@TypeSystemReference(PythonIntegerTypes.class)
public abstract class PyNumberFloorDivideFastPaths extends BinaryOpNode {

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins, FloatBuiltins, ...
     */
    @Specialization(guards = "!specialCase(left, right)")
    public static int doII(int left, int right) {
        return Math.floorDiv(left, right);
    }

    @Specialization(guards = "!specialCase(left, right)")
    public static long doLL(long left, long right) {
        return Math.floorDiv(left, right);
    }

    @Specialization(guards = "!isZero(right)")
    public static double doLD(long left, double right) {
        return doDD(left, right);
    }

    @Specialization(guards = "right != 0")
    public static double doDL(double left, long right) {
        return doDD(left, right);
    }

    @Specialization(guards = "!isZero(right)")
    public static double doDD(double left, double right) {
        return Math.floor(left / right);
    }

    public static boolean specialCase(int left, int right) {
        return right == 0 || left == Integer.MIN_VALUE && right == -1;
    }

    public static boolean specialCase(long left, long right) {
        return right == 0 || left == Long.MIN_VALUE && right == -1;
    }

    public static boolean isZero(double right) {
        return right == 0.0;
    }
}
