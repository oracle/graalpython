package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

@GenerateUncached
public abstract class ExitAWithNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    int exit(VirtualFrame virtualFrame, int stackTopIn,
                    @Cached PyObjectIsTrueNode isTrueNode,
                    @Cached PRaiseNode raiseNode) {
        int stackTop = stackTopIn;
        Object result = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object exception = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        PException savedExcState = PArguments.getException(virtualFrame);
        try {
            if (!isTrueNode.execute(virtualFrame, result) && exception != PNone.NONE) {
                if (exception instanceof PException) {
                    throw ((PException) exception).getExceptionForReraise();
                } else if (exception instanceof AbstractTruffleException) {
                    throw (AbstractTruffleException) exception;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw raiseNode.raise(SystemError, ErrorMessages.EXPECTED_EXCEPTION_ON_THE_STACK);
                }
            }
        } finally {
            PArguments.setException(virtualFrame, savedExcState);
        }
        return stackTop;
    }

    public static ExitAWithNode create() {
        return ExitAWithNodeGen.create();
    }

    public static ExitAWithNode getUncached() {
        return ExitAWithNodeGen.getUncached();
    }
}
