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
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class SetSequenceStorageItem extends Node {

    public static SetSequenceStorageItem create() {
        return SetSequenceStorageItemNodeGen.create();
    }

    @Child private SequenceStorageNodes.NormalizeIndexNode normalize = SequenceStorageNodes.NormalizeIndexNode.forListAssign();

    private final BranchProfile normalProfile = BranchProfile.create();
    private final ConditionProfile isEmptyProfile = ConditionProfile.createBinaryProfile();

    public final void setItem(PList list, int index, Object value) {
        SequenceStorage storage = list.getSequenceStorage();
        try {
            execute(storage, index, value);
            normalProfile.enter();
        } catch (SequenceStoreException e) {
            if (isEmptyProfile.profile(storage instanceof EmptySequenceStorage)) {
                SequenceStorage newStorage = ((EmptySequenceStorage) storage).generalizeFor(value, null);
                try {
                    newStorage.setItemNormalized(normalize.execute(index, newStorage.length()), value);
                } catch (SequenceStoreException ex) {
                    throw new IllegalStateException();
                }
                list.setSequenceStorage(newStorage);
            } else {
                assert !(storage instanceof ObjectSequenceStorage);
                ObjectSequenceStorage newStorage = ((TypedSequenceStorage) storage).generalizeFor(value, null);
                newStorage.setItemNormalized(normalize.execute(index, newStorage.length()), value);
                list.setSequenceStorage(newStorage);
            }
        }
    }

    public final void setItem(PList list, long index, Object value) {
        setItem(list, normalize.execute(index, list.getSequenceStorage().length()), value);
    }

    public abstract void execute(SequenceStorage storage, int index, Object value);

    @Specialization
    protected void setItem(BoolSequenceStorage storage, int index, boolean value) {
        storage.setBoolItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(ByteSequenceStorage storage, int index, byte value) {
        storage.setByteItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(DoubleSequenceStorage storage, int index, double value) {
        storage.setDoubleItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(IntSequenceStorage storage, int index, int value) {
        storage.setIntItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(ListSequenceStorage storage, int index, PList value) {
        storage.setListItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(LongSequenceStorage storage, int index, long value) {
        storage.setLongItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(TupleSequenceStorage storage, int index, PTuple value) {
        storage.setPTupleItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @Specialization
    protected void setItem(ObjectSequenceStorage storage, int index, Object value) {
        storage.setItemNormalized(normalize.execute(index, storage.length()), value);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected static void setItem(SequenceStorage storage, int index, Object value) {
        throw SequenceStoreException.INSTANCE;
    }
}
