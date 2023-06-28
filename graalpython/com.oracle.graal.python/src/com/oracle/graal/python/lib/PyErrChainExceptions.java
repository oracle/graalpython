package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {_PyErr_ChainExceptions}. Performs a simple exception chaining without
 * checking for cycles (not suitable to implement try-except).
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyErrChainExceptions extends Node {
    public final PException execute(Node inliningTarget, PException current, PException context) {
        execute(inliningTarget, current.getUnreifiedException(), context.getEscapedException());
        return current;
    }

    abstract void execute(Node inliningTarget, Object current, Object context);

    @Specialization
    static void chain(Node inliningTarget, Object current, Object context,
                    @Cached ExceptionNodes.SetContextNode setContextNode) {
        setContextNode.execute(inliningTarget, current, context);
    }
}
