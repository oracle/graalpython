package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.builtins.objects.exception.ExceptionInfo;
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

    protected void tryChainExceptionFromHandler(TruffleException handledException, PException handlerException) {
        // Chain the exception handled by the try block to the exception raised by the handler
        if (handledException != handlerException && handledException instanceof PException) {
            // It had to be reified by the handler already
            PBaseException handledExceptionObject = ((PException) handledException).getExceptionObject();
            chainExceptions(handlerException.getExceptionObject(), handledExceptionObject);
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, TruffleException handledException) {
        // Chain a preexisting (before the try started) exception to the handled exception
        if (handledException instanceof PException) {
            PException pException = (PException) handledException;
            ExceptionInfo preexisting = getExceptionForChaining(frame);
            if (preexisting != null) {
                if (pException.getExceptionObject().getContext() == null) {
                    chainExceptions(pException.getExceptionObject(), preexisting.exception);
                }
            }
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, PBaseException handledException) {
        if (handledException.getContext() == null) {
            ExceptionInfo preexisting = getExceptionForChaining(frame);
            if (preexisting != null) {
                chainExceptions(handledException, preexisting.exception);
            }
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

    private ExceptionInfo getExceptionForChaining(VirtualFrame frame) {
        if (getCaughtExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
        }
        return getCaughtExceptionNode.execute(frame);
    }
}
