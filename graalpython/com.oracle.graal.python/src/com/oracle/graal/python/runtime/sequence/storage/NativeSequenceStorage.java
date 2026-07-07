/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

import java.util.Arrays;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeStorageReference;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.TruffleLogger;

public abstract class NativeSequenceStorage extends SequenceStorage {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(NativeSequenceStorage.class);
    private static final Object BORROWED_OWNER_MARKER = new Object();

    /* native pointer object */
    private long ptr;
    private NativeStorageReference reference;

    /**
     * State for the two independent lifetime obligations of a native sequence storage:
     * <p>
     * Contract:
     * <ul>
     * <li>If {@link #reference} is set, the storage owns {@link #ptr}. The native memory is
     * released by the {@link NativeStorageReference} when this storage dies, so the storage must not
     * also keep a borrowed owner alive.</li>
     * <li>If the storage only borrows {@link #ptr}, it may keep a managed owner alive here. The
     * owner is the object whose native memory contains the storage pointer. The owner is never an
     * {@code Object[]} so that an unmarked array can keep its old meaning of replicated native
     * references.</li>
     * <li>Independently of memory ownership, native sequence storages can reference other Python
     * objects. When the Python GC detects a possible native reference cycle, those referents are
     * replicated here as strong Java references so the Java GC controls when they may die.</li>
     * </ul>
     * <p>
     * Possible encoded states:
     * <ul>
     * <li>{@code null}: no borrowed owner and no replicated native references. This is the initial
     * state for ordinary storages.</li>
     * <li>{@code Object[] refs}: replicated native references only. This is also the only encoded
     * state left when an owning {@link NativeStorageReference} is installed.</li>
     * <li>{@code owner}: borrowed native memory only. {@code owner} keeps the underlying native
     * object alive for as long as this storage can access {@link #ptr}.</li>
     * <li>{@code Object[] {BORROWED_OWNER_MARKER, owner, refs...}}: borrowed native memory plus
     * replicated native references.</li>
     * </ul>
     * <p>
     * Transitions:
     * <ul>
     * <li>{@link #setBorrowedMemoryOwner(Object)} transitions from no owner to borrowed ownership,
     * preserving any already replicated references by creating the marker array state.</li>
     * <li>{@link #setReplicatedNativeReferences(Object[])} replaces only the replicated-reference
     * part. If a borrowed owner is present, it is preserved and the state is re-encoded around the
     * new references.</li>
     * <li>{@link #setReference(NativeStorageReference)} transitions the storage to owned memory and
     * clears any borrowed owner. Replicated references, if present, are preserved because they
     * describe object graph reachability rather than memory ownership.</li>
     * <li>{@link #clearBorrowedMemoryOwner()} removes only the borrowed-owner part and is therefore
     * the inverse of the owner-preserving marker-array encoding.</li>
     * </ul>
     * </p>
     */
    private Object nativeReferenceState;

    NativeSequenceStorage(long ptr, int length, int capacity) {
        super(length, capacity);
        assert ptr != NULLPTR;
        this.ptr = ptr;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(PythonUtils.formatJString("new %s", this));
        }
    }

    public final long getPtr() {
        return ptr;
    }

    public final void setPtr(long ptr) {
        assert ptr != NULLPTR;
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
        clearBorrowedMemoryOwner();
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
     * For a description, see {@link #nativeReferenceState}.
     */
    public void setReplicatedNativeReferences(Object[] replicatedNativeReferences) {
        if (hasBorrowedMemoryOwner()) {
            Object owner = getBorrowedMemoryOwner();
            if (replicatedNativeReferences == null) {
                nativeReferenceState = owner;
            } else {
                nativeReferenceState = createBorrowedOwnerState(owner, replicatedNativeReferences);
            }
        } else {
            nativeReferenceState = replicatedNativeReferences;
        }
    }

    public Object[] getReplicatedNativeReferences() {
        if (nativeReferenceState instanceof Object[] refs) {
            if (isBorrowedOwnerState(refs)) {
                assert validateBorrowedOwnerState(refs);
                return Arrays.copyOfRange(refs, 2, refs.length);
            }
            return refs;
        }
        return null;
    }

    public void setBorrowedMemoryOwner(Object owner) {
        assert isValidBorrowedMemoryOwner(owner);
        assert !hasReference();
        assert !hasBorrowedMemoryOwner();
        Object[] replicatedNativeReferences = getReplicatedNativeReferences();
        if (replicatedNativeReferences == null) {
            nativeReferenceState = owner;
        } else {
            nativeReferenceState = createBorrowedOwnerState(owner, replicatedNativeReferences);
        }
    }

    private void clearBorrowedMemoryOwner() {
        if (nativeReferenceState instanceof Object[] refs) {
            if (isBorrowedOwnerState(refs)) {
                assert validateBorrowedOwnerState(refs);
                nativeReferenceState = Arrays.copyOfRange(refs, 2, refs.length);
            }
        } else {
            nativeReferenceState = null;
        }
    }

    private boolean hasBorrowedMemoryOwner() {
        if (nativeReferenceState instanceof Object[] refs) {
            return isBorrowedOwnerState(refs);
        }
        return nativeReferenceState != null;
    }

    private Object getBorrowedMemoryOwner() {
        if (nativeReferenceState instanceof Object[] refs) {
            assert isBorrowedOwnerState(refs);
            assert validateBorrowedOwnerState(refs);
            return refs[1];
        }
        assert isValidBorrowedMemoryOwner(nativeReferenceState);
        return nativeReferenceState;
    }

    private static Object[] createBorrowedOwnerState(Object owner, Object[] replicatedNativeReferences) {
        Object[] ownerState = new Object[replicatedNativeReferences.length + 2];
        ownerState[0] = BORROWED_OWNER_MARKER;
        ownerState[1] = owner;
        System.arraycopy(replicatedNativeReferences, 0, ownerState, 2, replicatedNativeReferences.length);
        assert validateBorrowedOwnerState(ownerState);
        return ownerState;
    }

    private static boolean isBorrowedOwnerState(Object[] state) {
        return state.length > 0 && state[0] == BORROWED_OWNER_MARKER;
    }

    private static boolean validateBorrowedOwnerState(Object[] state) {
        assert isBorrowedOwnerState(state);
        assert state.length >= 2;
        assert isValidBorrowedMemoryOwner(state[1]);
        return true;
    }

    private static boolean isValidBorrowedMemoryOwner(Object owner) {
        return owner != null && !(owner instanceof Object[]);
    }

}
