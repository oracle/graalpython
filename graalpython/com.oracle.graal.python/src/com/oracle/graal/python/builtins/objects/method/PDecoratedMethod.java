package com.oracle.graal.python.builtins.objects.method;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;

/**
 * Storage for both classmethods and staticmethods
 */
public class PDecoratedMethod extends PythonBuiltinObject {
    private Object callable;

    public PDecoratedMethod(LazyPythonClass cls) {
        super(cls);
    }

    public PDecoratedMethod(LazyPythonClass cls, Object callable) {
        this(cls);
        this.callable = callable;
    }

    public Object getCallable() {
        return callable;
    }

    public void setCallable(Object callable) {
        this.callable = callable;
    }
}
