package com.oracle.graal.python.builtins.objects.type;

public interface AbstractPythonClass extends LazyPythonClass {

    void lookupChanged();
}
