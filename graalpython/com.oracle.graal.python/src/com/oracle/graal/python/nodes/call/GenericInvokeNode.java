package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;

public final class GenericInvokeNode extends AbstractInvokeNode {
    @Child private IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();

    public static GenericInvokeNode create() {
        return new GenericInvokeNode();
    }

    @SuppressWarnings("try")
    private Object doCall(VirtualFrame frame, RootCallTarget callTarget, Object[] arguments) {
        optionallySetClassBodySpecial(arguments, callTarget);
        try (ExecutionContext ec = ExecutionContext.call(frame, arguments, callTarget, this)) {
            return callNode.call(callTarget, arguments);
        }
    }

    public Object execute(VirtualFrame frame, PFunction callee, Object[] arguments) {
        PArguments.setGlobals(arguments, callee.getGlobals());
        PArguments.setClosure(arguments, callee.getClosure());
        RootCallTarget callTarget = getCallTarget(callee);
        return doCall(frame, callTarget, arguments);
    }

    public Object execute(VirtualFrame frame, PBuiltinFunction callee, Object[] arguments) {
        RootCallTarget callTarget = getCallTarget(callee);
        return doCall(frame, callTarget, arguments);
    }

    public Object execute(VirtualFrame frame, RootCallTarget callTarget, Object[] arguments) {
        return doCall(frame, callTarget, arguments);
    }
}