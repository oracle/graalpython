package com.oracle.graal.python.nodes.bytecode;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class GetAExitCoroNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    int exit(VirtualFrame virtualFrame, int stackTopIn,
                    @Cached CallQuaternaryMethodNode callExit,
                    @Cached GetClassNode getClassNode,
                    @Cached GetExceptionTracebackNode getTracebackNode) {
        int stackTop = stackTopIn;
        Object exception = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object exit = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object contextManager = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        if (exception == PNone.NONE) {
            Object result = callExit.execute(virtualFrame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
            virtualFrame.setObject(++stackTop, PNone.NONE);
            virtualFrame.setObject(++stackTop, result);
        } else {
            Object pythonException = exception;
            if (exception instanceof PException) {
                PArguments.setException(virtualFrame, (PException) exception);
                pythonException = ((PException) exception).getEscapedException();
            }
            Object excType = getClassNode.execute(pythonException);
            Object excTraceback = getTracebackNode.execute(pythonException);
            Object result = callExit.execute(virtualFrame, exit, contextManager, excType, pythonException, excTraceback);
            virtualFrame.setObject(++stackTop, exception);
            virtualFrame.setObject(++stackTop, result);
        }
        return stackTop;
    }

    public static GetAExitCoroNode create() {
        return GetAExitCoroNodeGen.create();
    }

    public static GetAExitCoroNode getUncached() {
        return GetAExitCoroNodeGen.getUncached();
    }
}
