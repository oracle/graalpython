package com.oracle.graal.python.runtime;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.strings.TruffleString;

public class PAsyncGen extends PGenerator {
    private boolean closed = false;
    private boolean hooksCalled = false;
    private boolean runningAsync = false;

    public static PAsyncGen create(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        rootNode.createGeneratorFrame(arguments);
        return new PAsyncGen(lang, name, qualname, rootNode, callTargets, arguments);
    }

    protected PAsyncGen(PythonLanguage lang, TruffleString name, TruffleString qualname, PBytecodeRootNode rootNode, RootCallTarget[] callTargets, Object[] arguments) {
        super(lang, name, qualname, rootNode, callTargets, arguments, PythonBuiltinClassType.PAsyncGenerator, false);
    }

    public boolean isClosed() {
        return closed;
    }

    public void markClosed() {
        this.closed = true;
    }

    public boolean isHooksCalled() {
        return hooksCalled;
    }

    public void setHooksCalled(boolean hooksCalled) {
        this.hooksCalled = hooksCalled;
    }

    public boolean isRunningAsync() {
        return runningAsync;
    }

    public void setRunningAsync(boolean runningAsync) {
        this.runningAsync = runningAsync;
    }
}
