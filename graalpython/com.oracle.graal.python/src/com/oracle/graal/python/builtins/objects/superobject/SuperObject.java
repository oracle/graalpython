package com.oracle.graal.python.builtins.objects.superobject;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class SuperObject extends PythonBuiltinObject {
    @CompilationFinal private Object type;
    @CompilationFinal private PythonClass objecttype;
    @CompilationFinal private Object object;

    public SuperObject(PythonClass cls) {
        super(cls);
    }

    public void init(Object newType, PythonClass newObjecttype, Object newObject) {
        if (this.type != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        this.type = newType;
        this.objecttype = newObjecttype;
        this.object = newObject;
    }

    public PythonClass getObjectType() {
        return objecttype;
    }

    public Object getType() {
        return type;
    }

    public Object getObject() {
        return object;
    }
}
