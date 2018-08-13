/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.runtime.sequence;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class PSequence extends PythonBuiltinObject implements PLenSupplier {

    public PSequence(PythonClass cls) {
        super(cls);
    }

    @Deprecated
    public final Object getSlice(PythonObjectFactory factory, PSlice slice) {
        SliceInfo info = slice.computeActualIndices(len());
        return getSlice(factory, info.start, info.stop, info.step, info.length);
    }

    @Deprecated
    protected abstract Object getSlice(PythonObjectFactory factory, int start, int stop, int step, int length);

    @Deprecated
    public abstract void setSlice(int start, int stop, int step, PSequence value);

    @Deprecated
    public abstract void setSlice(PSlice slice, PSequence value);

    @Deprecated
    public abstract void delItem(int idx);

    public abstract int index(Object value);

    public abstract SequenceStorage getSequenceStorage();

    /**
     * Note: Sequences are never immutable for us because they can go <it>to native</it>, i.e., the
     * storage will be exchanged and also, native code often allows to modify <it>immutable</it>
     * objects (like {@code _PyTuple_Resize}).
     */
    public abstract void setSequenceStorage(SequenceStorage newStorage);

    public static PSequence require(Object value) {
        if (value instanceof PSequence) {
            return (PSequence) value;
        }
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError("PSequence required.");
    }

    public static PSequence expect(Object value) throws UnexpectedResultException {
        if (value instanceof PSequence) {
            return (PSequence) value;
        }
        throw new UnexpectedResultException(value);
    }
}
