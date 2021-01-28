package com.oracle.graal.python.builtins.objects.ssl;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.object.Shape;

/**
 * Python wrapper around {@link MemoryBIO} objects which emulate OpenSSL's BIO interface.
 */
public class PMemoryBIO extends PythonObject {
    private final MemoryBIO bio = new MemoryBIO();

    public PMemoryBIO(Object pythonClass, Shape instanceShape) {
        super(pythonClass, instanceShape);
    }

    public MemoryBIO getBio() {
        return bio;
    }
}
