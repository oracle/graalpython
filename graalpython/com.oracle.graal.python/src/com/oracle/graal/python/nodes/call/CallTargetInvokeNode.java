package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

public abstract class CallTargetInvokeNode extends AbstractInvokeNode {
    @Child private DirectCallNode callNode;
    protected final boolean isBuiltin;

    protected CallTargetInvokeNode(CallTarget callTarget, boolean isBuiltin, boolean isGenerator) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        if (isBuiltin) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && shouldInlineGenerators()) {
            this.callNode.forceInlining();
        }
        this.isBuiltin = isBuiltin;
    }

    @TruffleBoundary
    public static CallTargetInvokeNode create(PFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return CallTargetInvokeNodeGen.create(callTarget, builtin, callee.isGeneratorFunction());
    }

    @TruffleBoundary
    public static CallTargetInvokeNode create(PBuiltinFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return CallTargetInvokeNodeGen.create(callTarget, builtin, false);
    }

    public static CallTargetInvokeNode create(CallTarget callTarget, boolean isBuiltin, boolean isGenerator) {
        return CallTargetInvokeNodeGen.create(callTarget, isBuiltin, isGenerator);
    }

    public abstract Object execute(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments);

    @Specialization(guards = {"globals == null", "closure == null"})
    @SuppressWarnings("try")
    protected Object doSimple(VirtualFrame frame, @SuppressWarnings("unused") PythonObject globals, @SuppressWarnings("unused") PCell[] closure, Object[] arguments) {
        RootCallTarget ct = (RootCallTarget) callNode.getCallTarget();
        optionallySetClassBodySpecial(arguments, ct);
        try (ExecutionContext ec = ExecutionContext.call(frame, arguments, ct, this)) {
            return callNode.call(arguments);
        }
    }

    @Specialization(replaces = "doSimple")
    protected Object doNoKeywords(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        return doSimple(frame, null, null, arguments);
    }

    public final CallTarget getCallTarget() {
        return callNode.getCallTarget();
    }
}