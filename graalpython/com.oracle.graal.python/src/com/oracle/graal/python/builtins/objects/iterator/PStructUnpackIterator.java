/* Copyright (c) 2020, 2024, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
package com.oracle.graal.python.builtins.objects.iterator;

import com.oracle.graal.python.builtins.objects.struct.PStruct;
import com.oracle.truffle.api.object.Shape;

public final class PStructUnpackIterator extends PBuiltinIterator {
    final PStruct struct;
    final Object buffer;

    public PStructUnpackIterator(Object clazz, Shape instanceShape, PStruct struct, Object buffer) {
        super(clazz, instanceShape);
        this.struct = struct;
        this.buffer = buffer;
    }

    public PStruct getStruct() {
        return struct;
    }

    public Object getBuffer() {
        return buffer;
    }
}
