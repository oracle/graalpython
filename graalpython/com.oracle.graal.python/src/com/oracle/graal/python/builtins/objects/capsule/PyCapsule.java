package com.oracle.graal.python.builtins.objects.capsule;

public final class PyCapsule {
    private Object pointer;
    private String name;
    private Object context;
    private Object destructor;

    public Object getPointer() {
        return pointer;
    }

    public void setPointer(Object pointer) {
        this.pointer = pointer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    public Object getDestructor() {
        return destructor;
    }

    public void setDestructor(Object destructor) {
        this.destructor = destructor;
    }
}
