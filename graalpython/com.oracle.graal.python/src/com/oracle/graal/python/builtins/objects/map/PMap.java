package com.oracle.graal.python.builtins.objects.map;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class PMap extends PythonBuiltinObject {
    @CompilationFinal private Object function;
    @CompilationFinal(dimensions = 1) private Object[] iterators;

    public PMap(LazyPythonClass clazz) {
        super(clazz);
    }

    public Object getFunction() {
        return function;
    }

    public void setFunction(Object function) {
        this.function = function;
    }

    public Object[] getIterators() {
        return iterators;
    }

    public void setIterators(Object[] iterators) {
        this.iterators = iterators;
    }
}
