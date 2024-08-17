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
public abstract class PyNumberAndFastPaths extends BinaryOpNode {

    @Specialization
    public static boolean op(boolean left, boolean right) {
        return left && right;
    }

    @Specialization
    public static int op(int left, int right) {
        return left & right;
    }

    @Specialization
    public static long op(long left, long right) {
        return left & right;
    }
}
