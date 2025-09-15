/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public abstract class NativeSequenceStorage extends SequenceStorage implements TruffleObject {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeSequenceStorage.class);

    /* native pointer object */
    private Object ptr;
    private NativeStorageReference reference;

    /**
     * Replicates the native references of this native sequence storage in Java.
     * <p>
     * Native sequence storages have references (if not empty) to other objects. Whenever the Python
     * GC detects a possible reference cycle, we will replicate those native references in Java to
     * give control to the Java GC when objects may die.
     * </p>
     */
    private Object[] replicatedNativeReferences;

    NativeSequenceStorage(Object ptr, int length, int capacity) {
        super(length, capacity);
        this.ptr = ptr;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(PythonUtils.formatJString("new %s", this));
        }
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
    public final Object getIndicativeValue() {
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(ptr=" + CApiContext.asHex(ptr) + ", length=" + length + ", capacity=" + capacity + ", ownsMemory=" + hasReference() + ")";
    }

    /**
     * For a description, see {@link #replicatedNativeReferences}.
     */
    public void setReplicatedNativeReferences(Object[] replicatedNativeReferences) {
        this.replicatedNativeReferences = replicatedNativeReferences;
    }

    public Object[] getReplicatedNativeReferences() {
        return replicatedNativeReferences;
    }

    @ExportMessage
    boolean isPointer(
                    @Shared @CachedLibrary(limit = "1") InteropLibrary lib) {
        return lib.isPointer(ptr);
    }

    @ExportMessage
    long asPointer(
                    @Shared @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.asPointer(ptr);
    }
}
