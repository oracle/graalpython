/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.list;

import static com.oracle.graal.python.util.PythonUtils.builtinClassToType;

import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
public final class PList extends PSequence {
    private final ListOrigin origin;
    private SequenceStorage store;

    public PList(Object cls, Shape instanceShape, SequenceStorage store) {
        super(builtinClassToType(cls), instanceShape);
        this.origin = null;
        this.store = store;
    }

    public PList(Object cls, Shape instanceShape, SequenceStorage store, ListOrigin origin) {
        super(builtinClassToType(cls), instanceShape);
        this.origin = origin;
        this.store = store;
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public void setSequenceStorage(SequenceStorage newStorage) {
        this.store = newStorage;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("list(%s)", store);
    }

    public ListOrigin getOrigin() {
        return origin;
    }

    @ExportMessage
    public SourceSection getSourceLocation(@Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            ListOrigin node = getOrigin();
            SourceSection result = null;
            if (node != null) {
                result = node.getSourceSection();
            }
            if (result == null) {
                throw UnsupportedMessageException.create();
            }
            return result;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean hasSourceLocation() {
        return getOrigin() != null && getOrigin().getSourceSection() != null;
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
                    @Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            try {
                normalize.execute(index, len, ErrorMessages.INDEX_OUT_OF_RANGE);
            } catch (PException e) {
                return false;
            }
            return true;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            return index == len;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
                    @Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            try {
                normalize.execute(index, len, ErrorMessages.INDEX_OUT_OF_RANGE);
            } catch (PException e) {
                return false;
            }
            return true;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
                    @Bind Node inliningTarget,
                    @Cached PForeignToPTypeNode convert,
                    @Exclusive @Cached SequenceStorageNodes.SetItemScalarGeneralizingNode setItem,
                    @Cached SequenceStorageNodes.AppendNode appendNode,
                    @Cached InlinedBranchProfile generalizedProfile,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            value = convert.executeConvert(value);
            try {
                SequenceStorage newStorage;
                if (index == len) {
                    newStorage = appendNode.execute(inliningTarget, store, value, SequenceStorageNodes.ListGeneralizationNode.SUPPLIER);
                } else {
                    newStorage = setItem.execute(inliningTarget, store, PInt.intValueExact(index), value, SequenceStorageNodes.ListGeneralizationNode.SUPPLIER);
                }
                if (newStorage != store) {
                    generalizedProfile.enter(inliningTarget);
                    store = newStorage;
                }
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void removeArrayElement(long index,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached SequenceStorageNodes.DeleteItemNode delItem,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            try {
                delItem.execute(inliningTarget, store, PInt.intValueExact(index));
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    public interface ListOrigin {

        /**
         * This class serves the purpose of updating the size estimate for the lists constructed
         * here over time. The estimate is updated slowly, it takes {@link #NUM_DIGITS_POW2} lists
         * to reach a size one larger than the current estimate to increase the estimate for new
         * lists.
         */
        @ValueType
        final class SizeEstimate {
            private static final int NUM_DIGITS = 3;
            private static final int NUM_DIGITS_POW2 = 1 << NUM_DIGITS;

            @CompilationFinal private int shiftedStorageSizeEstimate;

            public SizeEstimate(int storageSizeEstimate) {
                assert storageSizeEstimate >= 0;
                shiftedStorageSizeEstimate = storageSizeEstimate * NUM_DIGITS_POW2;
            }

            public int estimate() {
                return shiftedStorageSizeEstimate >> NUM_DIGITS;
            }

            public int updateFrom(int newSizeEstimate) {
                int newValue = shiftedStorageSizeEstimate + newSizeEstimate - estimate();
                shiftedStorageSizeEstimate = Math.max(newValue, 0);
                return shiftedStorageSizeEstimate;
            }
        }

        void reportUpdatedCapacity(ArrayBasedSequenceStorage newStore);

        SourceSection getSourceSection();
    }
}
