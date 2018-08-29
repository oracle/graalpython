package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.graal.python.builtins.objects.range.PRange;

public class RangeSequenceStorage extends SequenceStorage {

    private final PRange range;

    public RangeSequenceStorage(PRange range) {
        this.range = range;
    }

    public PRange getRange() {
        return range;
    }

    @Override
    public int length() {
        return range.len();
    }

    @Override
    public void setNewLength(int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SequenceStorage copy() {
        return this;
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new IntSequenceStorage(newCapacity);
    }

    @Override
    public Object getInternalArrayObject() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Int;
    }

    @Override
    public Object[] getInternalArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getItemNormalized(int idx) {
        return range.getItemNormalized(idx);
    }

    public int getIntItemNormalized(int idx) {
        return range.getItemNormalized(idx);
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SequenceStorage getSliceInBound(int start, int stop, int step, int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reverse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(SequenceStorage other) {
        if (other instanceof RangeSequenceStorage) {
            return range.equals(((RangeSequenceStorage) other).range);
        }
        return false;
    }

    @Override
    public SequenceStorage generalizeFor(Object value, SequenceStorage other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getIndicativeValue() {
        return range.getStart();
    }

    @Override
    public void ensureCapacity(int newCapacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        throw new UnsupportedOperationException();
    }

}