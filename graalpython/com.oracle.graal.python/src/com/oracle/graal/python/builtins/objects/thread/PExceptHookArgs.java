package com.oracle.graal.python.builtins.objects.thread;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public class PExceptHookArgs extends PythonBuiltinObject  {

    private final Object excType;
    private final Object excValue;
    private final Object excTraceback;
    private final Object thread;

    public PExceptHookArgs(Object cls, Shape instanceShape, Object excType, Object excValue, Object excTraceback, Object thread) {
        super(cls, instanceShape);
        this.excType = excType;
        this.excValue = excValue;
        this.excTraceback = excTraceback;
        this.thread = thread;
    }

    public Object getExcType() { return excType; }
    public Object getExcValue() { return excValue; }
    public Object getExcTraceback() { return excTraceback; }
    public Object getThread() { return thread; }

}
