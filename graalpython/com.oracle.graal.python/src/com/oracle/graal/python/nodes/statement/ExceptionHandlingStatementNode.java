package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ExceptionHandlingStatementNode extends StatementNode {
    @Child private ExceptionStateNodes.SaveExceptionStateNode saveExceptionStateNode;
    @Child private ExceptionStateNodes.RestoreExceptionStateNode restoreExceptionStateNode;
    @Child private ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;

    protected void tryChainExceptionFromHandler(PException handlerException, TruffleException handledException) {
        // Chain the exception handled by the try block to the exception raised by the handler
        if (handledException != handlerException && handledException instanceof PException) {
            chainExceptions(handlerException.getExceptionObject(), ((PException) handledException).getReifiedException());
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, TruffleException handledException) {
        // Chain a preexisting (before the try started) exception to the handled exception
        if (handledException instanceof PException) {
            PException pException = (PException) handledException;
            PException preexisting = getExceptionForChaining(frame);
            if (preexisting != null && pException.getExceptionObject().getContext() == null) {
                chainExceptions(pException.getExceptionObject(), preexisting.getReifiedException());
            }
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, PBaseException handledException) {
        PException preexisting = getExceptionForChaining(frame);
        if (preexisting != null && handledException.getContext() == null) {
            chainExceptions(handledException, preexisting.getReifiedException());
        }
    }

    public static void chainExceptions(PBaseException currentException, PBaseException context) {
        if (currentException.getContext() == null && currentException != context) {
            PBaseException e = context;
            while (e != null) {
                if (e.getContext() == currentException) {
                    e.setContext(null);
                }
                e = e.getContext();
            }
            if (context != null) {
                context.markAsEscaped();
            }
            currentException.setContext(context);
        }
    }

    protected void restoreExceptionState(VirtualFrame frame, ExceptionState e) {
        if (e != null) {
            if (restoreExceptionStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                restoreExceptionStateNode = insert(ExceptionStateNodes.RestoreExceptionStateNode.create());
            }
            restoreExceptionStateNode.execute(frame, e);
        }
    }

    protected ExceptionState saveExceptionState(VirtualFrame frame) {
        if (saveExceptionStateNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            saveExceptionStateNode = insert(ExceptionStateNodes.SaveExceptionStateNode.create());
        }
        return saveExceptionStateNode.execute(frame);
    }

    private PException getExceptionForChaining(VirtualFrame frame) {
        if (getCaughtExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
        }
        return getCaughtExceptionNode.execute(frame);
    }
}
