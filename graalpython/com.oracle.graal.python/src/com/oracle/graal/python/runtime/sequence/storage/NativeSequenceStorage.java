/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.runtime.sequence.storage;

import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeStorageReference;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;

public abstract class NativeSequenceStorage extends SequenceStorage {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeSequenceStorage.class);

    /* native pointer object */
    private Object ptr;
    private NativeStorageReference reference;

    NativeSequenceStorage(Object ptr, int length, int capacity) {
        super(length, capacity);
        this.ptr = ptr;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(PythonUtils.formatJString("new %s", toJString()));
        }
    }

    @TruffleBoundary
    private String toJString() {
        return toString();
    }

    public final Object getPtr() {
        return ptr;
    }

    public final void setPtr(Object ptr) {
        if (reference != null) {
            reference.setPtr(ptr);
        }
        this.ptr = ptr;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public final void setReference(NativeStorageReference reference) {
        assert this.reference == null : "attempting to set another NativeStorageReference";
        this.reference = reference;
    }

    public final boolean hasReference() {
        return reference != null;
    }

    public final void setNewLength(int length) {
        assert length <= capacity;
        this.length = length;
        if (reference != null) {
            reference.setSize(length);
        }
    }

    @Override
    public final void ensureCapacity(int newCapacity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final SequenceStorage copy() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final Object[] getInternalArray() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final Object[] getCopyOfInternalArray() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final SequenceStorage getSliceInBound(int start, int stop, int step, int len) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final void reverse() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final boolean equals(SequenceStorage other) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final SequenceStorage generalizeFor(Object value, SequenceStorage other) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final Object getIndicativeValue() {
        return null;
    }

    @Override
    public final void copyItem(int idxTo, int idxFrom) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public final Object getInternalArrayObject() {
        return ptr;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(ptr=" + CApiContext.asHex(ptr) + ", length=" + length + ", capacity=" + capacity + ", ownsMemory=" + hasReference() + ")";
    }
}
