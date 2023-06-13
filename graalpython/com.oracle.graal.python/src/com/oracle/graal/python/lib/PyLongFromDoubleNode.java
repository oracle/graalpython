package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyLongFromDoubleNode extends Node {
    public abstract Object execute(Node inliningTarget, double value);

    public static Object executeUncached(double value) {
        return PyLongFromDoubleNodeGen.getUncached().execute(null, value);
    }

    @Specialization(guards = "isFinite(value)")
    static Object doFinite(Node inliningTarget, double value,
                    @Cached InlinedConditionProfile fitsInLong,
                    @Cached InlinedConditionProfile fitsInInt,
                    @Cached PythonObjectFactory factory) {
        BigInteger bigInteger = toBigInteger(value);
        if (fitsInLong.profile(inliningTarget, PInt.bigIntegerFitsInLong(bigInteger))) {
            long longValue = PInt.longValue(bigInteger);
            if (fitsInInt.profile(inliningTarget, PInt.isIntRange(longValue))) {
                return (int) longValue;
            }
            return longValue;
        }
        return factory.createInt(bigInteger);
    }

    @Fallback
    static Object doInfinite(double value,
                    @Cached PRaiseNode raiseNode) {
        if (Double.isNaN(value)) {
            throw raiseNode.raise(ValueError, ErrorMessages.CANNOT_CONVERT_FLOAT_NAN_TO_INTEGER);
        }
        assert Double.isInfinite(value);
        throw raiseNode.raise(OverflowError, ErrorMessages.CANNOT_CONVERT_FLOAT_INFINITY_TO_INTEGER);
    }

    @TruffleBoundary
    private static BigInteger toBigInteger(double self) {
        return new BigDecimal(self, MathContext.UNLIMITED).toBigInteger();
    }

    protected static boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
