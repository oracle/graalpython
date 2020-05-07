package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;

public abstract class PNodeWithState extends PNodeWithContext {
    @Child private PythonObjectFactory objectFactory;
    @Child private PRaiseNode raiseNode;

    protected final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode.raise(type, format, arguments);
    }

    protected final PythonObjectFactory factory() {
        if (objectFactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            objectFactory = insert(PythonObjectFactory.create());
        }
        return objectFactory;
    }
}
