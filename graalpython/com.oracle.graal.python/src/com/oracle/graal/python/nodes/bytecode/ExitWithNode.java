package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class ExitWithNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    int exit(VirtualFrame frame, int stackTopIn,
                    @Cached CallQuaternaryMethodNode callExit,
                    @Cached GetClassNode getClassNode,
                    @Cached GetExceptionTracebackNode getTracebackNode,
                    @Cached PyObjectIsTrueNode isTrueNode,
                    @Cached PRaiseNode raiseNode) {
        int stackTop = stackTopIn;
        Object exception = frame.getObject(stackTop);
        frame.setObject(stackTop--, null);
        Object exit = frame.getObject(stackTop);
        frame.setObject(stackTop--, null);
        Object contextManager = frame.getObject(stackTop);
        frame.setObject(stackTop--, null);
        if (exception == PNone.NONE) {
            callExit.execute(frame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
        } else {
            PException savedExcState = PArguments.getException(frame);
            try {
                Object pythonException = exception;
                if (exception instanceof PException) {
                    PArguments.setException(frame, (PException) exception);
                    pythonException = ((PException) exception).getEscapedException();
                }
                Object excType = getClassNode.execute(pythonException);
                Object excTraceback = getTracebackNode.execute(pythonException);
                Object result = callExit.execute(frame, exit, contextManager, excType, pythonException, excTraceback);
                if (!isTrueNode.execute(frame, result)) {
                    if (exception instanceof AbstractTruffleException) {
                        throw (AbstractTruffleException) exception;
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw raiseNode.raise(SystemError, "expected exception on the stack");
                    }
                }
            } finally {
                PArguments.setException(frame, savedExcState);
            }
        }
        return stackTop;
    }

    public static ExitWithNode create() {
        return ExitWithNodeGen.create();
    }

    public static ExitWithNode getUncached() {
        return ExitWithNodeGen.getUncached();
    }
}
