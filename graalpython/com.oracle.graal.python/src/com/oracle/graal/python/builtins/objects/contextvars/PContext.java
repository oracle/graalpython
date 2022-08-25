package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.object.Shape;

public class PContext extends PythonBuiltinObject {
    public Hamt contextVarValues = new Hamt();
    private PContext previousContext = null;

    public void enter(PythonContext.PythonThreadState threadState) {
        if (previousContext != null) {
            throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CANNOT_ENTER_CONTEXT_ALREADY_ENTERED, this);
        }
        previousContext = threadState.getContext(PythonObjectFactory.getUncached());
        assert previousContext != null : "ThreadState had null Context. This should not happen";
        threadState.setContext(this);
    }

    public void leave(PythonContext.PythonThreadState threadState) {
        assert threadState.getContext(null) == this : "leaving a context which is not currently entered";
        assert previousContext != null : "entered context has no previous context";
        threadState.setContext(previousContext);
        previousContext = null;
    }

    public PContext(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }
}
