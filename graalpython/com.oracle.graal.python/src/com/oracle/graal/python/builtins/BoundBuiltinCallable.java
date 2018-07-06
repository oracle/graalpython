package com.oracle.graal.python.builtins;

import com.oracle.graal.python.runtime.object.PythonObjectFactory;

public interface BoundBuiltinCallable<T> {
    T boundToObject(Object binding, PythonObjectFactory factory);
}
