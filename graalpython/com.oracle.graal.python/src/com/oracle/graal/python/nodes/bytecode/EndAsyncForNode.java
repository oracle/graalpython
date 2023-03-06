package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;

@GenerateUncached
@ImportStatic(PythonBuiltinClassType.class)
public abstract class EndAsyncForNode extends PNodeWithContext {
    public abstract void execute(Object exception, boolean rootNodeVisible);

    public static EndAsyncForNode getUncached() {
        return EndAsyncForNodeGen.getUncached();
    }

    public static EndAsyncForNode create() {
        return EndAsyncForNodeGen.create();
    }

    @Specialization
    public void doPException(PException exception, boolean rootNodeVisible,
                    @Cached @Cached.Shared("IsStopAsyncIteration") IsBuiltinClassProfile isStopAsyncIteration) {
        if (!isStopAsyncIteration.profileException(exception, PythonBuiltinClassType.StopAsyncIteration)) {
            throw exception.getExceptionForReraise(rootNodeVisible);
        }
    }

    @Specialization
    public void doGeneric(Object exception, boolean rootNodeVisible,
                    @Cached @Cached.Shared("IsStopAsyncIteration") IsBuiltinClassProfile isStopAsyncIteration,
                    @Cached PRaiseNode raiseNode) {
        if (exception == PNone.NONE) {
            return;
        }
        if (!isStopAsyncIteration.profileObject(exception, PythonBuiltinClassType.StopAsyncIteration)) {
            if (exception instanceof PException) {
                throw ((PException) exception).getExceptionForReraise(rootNodeVisible);
            } else if (exception instanceof AbstractTruffleException) {
                throw (AbstractTruffleException) exception;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(SystemError, ErrorMessages.EXPECTED_EXCEPTION_ON_THE_STACK);
            }

        }
    }
}
