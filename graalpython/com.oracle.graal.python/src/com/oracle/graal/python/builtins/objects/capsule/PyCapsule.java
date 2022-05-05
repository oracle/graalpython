package com.oracle.graal.python.builtins.objects.capsule;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class PyCapsule implements TruffleObject {
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

    @ExportMessage
    @TruffleBoundary
    public String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        String quote, n;
        if (name != null) {
            quote = "\"";
            n = name;
        } else {
            quote = "";
            n = "NULL";
        }
        return String.format("<capsule object %s%s%s at %x>", quote, n, quote, hashCode());
    }
}
