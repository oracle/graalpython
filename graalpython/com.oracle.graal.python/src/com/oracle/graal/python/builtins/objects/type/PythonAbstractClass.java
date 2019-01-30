package com.oracle.graal.python.builtins.objects.type;

public interface PythonAbstractClass extends LazyPythonClass {

    void lookupChanged();
}
