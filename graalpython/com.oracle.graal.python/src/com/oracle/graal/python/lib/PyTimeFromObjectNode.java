package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_VALUE_NAN;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_LARGE_TO_CONVERT_TO;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of {@code _PyTime_FromObject} from CPython.
 */
@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class PyTimeFromObjectNode extends PNodeWithRaise {
    public static final long SEC_TO_MS = 1000L;
    public static final long MS_TO_US = 1000L;
    public static final long US_TO_NS = 1000L;
    public static final long MS_TO_NS = MS_TO_US * US_TO_NS;
    public static final long SEC_TO_NS = SEC_TO_MS * MS_TO_NS;
    public static final long SEC_TO_US = SEC_TO_MS * MS_TO_US;

    public abstract long execute(VirtualFrame frame, Object obj, long unitToNs);

    @Specialization
    long doDouble(double d, long unitToNs) {
        // Implements _PyTime_FromDouble, rounding mode (HALF_UP) is hard-coded for now
        if (Double.isNaN(d)) {
            throw raise(ValueError, INVALID_VALUE_NAN);
        }
        double value = d * unitToNs;
        value = value >= 0.0 ? Math.ceil(value) : Math.floor(value);
        if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw raiseTimeOverflow();
        }
        return (long) value;
    }

    @Specialization
    long doLong(long l, long unitToNs) {
        try {
            return PythonUtils.multiplyExact(l, unitToNs);
        } catch (OverflowException e) {
            throw raiseTimeOverflow();
        }
    }

    @Specialization
    long doOther(VirtualFrame frame, Object value, long unitToNs,
                    @Cached CastToJavaDoubleNode castToDouble,
                    @Cached PyLongAsLongAndOverflowNode asLongNode) {
        try {
            return doDouble(castToDouble.execute(value), unitToNs);
        } catch (CannotCastException e) {
            try {
                return doLong(asLongNode.execute(frame, value), unitToNs);
            } catch (OverflowException e1) {
                throw raiseTimeOverflow();
            }
        }
    }

    private PException raiseTimeOverflow() {
        throw raise(PythonBuiltinClassType.OverflowError, TOO_LARGE_TO_CONVERT_TO, "timestamp", "long");
    }
}
