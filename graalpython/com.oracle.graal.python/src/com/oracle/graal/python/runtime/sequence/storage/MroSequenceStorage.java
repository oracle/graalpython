/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class MroSequenceStorage extends TypedSequenceStorage {

    /**
     * This assumption will be invalidated whenever the mro changes.
     */
    private final CyclicAssumption lookupStableAssumption;

    /**
     * These assumptions will be invalidated whenever the value of the given slot changes. All
     * assumptions will be invalidated if the mro changes.
     */
    private final Map<String, List<Assumption>> attributesInMROFinalAssumptions = new HashMap<>();

    @CompilationFinal(dimensions = 1) private PythonAbstractClass[] values;

    public MroSequenceStorage(String className, PythonAbstractClass[] elements) {
        this.values = elements;
        this.capacity = elements.length;
        this.length = elements.length;
        this.lookupStableAssumption = new CyclicAssumption(className);
    }

    public MroSequenceStorage(String className, int capacity) {
        this.values = new PythonAbstractClass[capacity];
        this.capacity = capacity;
        this.length = 0;
        this.lookupStableAssumption = new CyclicAssumption(className);
    }

    @Override
    public final PythonAbstractClass getItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    public void setItemNormalized(int idx, Object value) {
        if (value instanceof PythonAbstractClass) {
            setClassItemNormalized(idx, (PythonAbstractClass) value);
        } else {
            throw new SequenceStoreException(value);
        }
    }

    public void setClassItemNormalized(int idx, PythonAbstractClass value) {
        if (values[idx] != value) {
            lookupChanged("direct assignment to MRO");
        }
        values[idx] = value;
    }

    @Override
    public void insertItem(int idx, Object value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        setItemNormalized(idx, value);
        length++;
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        if (idxTo != idxFrom) {
            lookupChanged("item move within MRO");
        }
        values[idxTo] = values[idxFrom];
    }

    @Override
    public MroSequenceStorage getSliceInBound(int start, int stop, int step, int sliceLength) {
        PythonAbstractClass[] newArray = new PythonAbstractClass[sliceLength];

        if (step == 1) {
            System.arraycopy(values, start, newArray, 0, sliceLength);
            return new MroSequenceStorage(getClassName(), newArray);
        }

        for (int i = start, j = 0; j < sliceLength; i += step, j++) {
            newArray[j] = values[i];
        }

        return new MroSequenceStorage(getClassName(), newArray);
    }

    public void setObjectSliceInBound(int start, int stop, int step, MroSequenceStorage sequence, ConditionProfile sameLengthProfile) {
        int otherLength = sequence.length();

        // range is the whole sequence?
        if (sameLengthProfile.profile(start == 0 && stop == length && step == 1)) {
            values = Arrays.copyOf(sequence.values, otherLength);
            length = otherLength;
            minimizeCapacity();
            return;
        }

        ensureCapacity(stop);

        for (int i = start, j = 0; i < stop; i += step, j++) {
            values[i] = sequence.values[j];
        }

        length = length > stop ? length : stop;
        lookupChanged("slice assignment to MRO");
    }

    public String getClassName() {
        return lookupStableAssumption.getAssumption().getName();
    }

    @Override
    public SequenceStorage copy() {
        return new MroSequenceStorage(getClassName(), Arrays.copyOf(values, length));
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new MroSequenceStorage(getClassName(), newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        return values;
    }

    public final PythonAbstractClass[] getInternalClassArray() {
        return values;
    }

    @Override
    public void increaseCapacityExactWithCopy(int newCapacity) {
        values = Arrays.copyOf(values, newCapacity);
        capacity = values.length;
    }

    @Override
    public void increaseCapacityExact(int newCapacity) {
        values = new PythonAbstractClass[newCapacity];
        capacity = values.length;
    }

    public Object popObject() {
        Object pop = values[length - 1];
        length--;
        return pop;
    }

    @Override
    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                PythonAbstractClass temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
            lookupChanged("MRO reversed");
        }
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public boolean equals(SequenceStorage other) {
        Object[] otherArray = other.getInternalArray();
        return Arrays.equals(values, otherArray);
    }

    @Override
    public Object getInternalArrayObject() {
        return values;
    }

    @Override
    public Object getCopyOfInternalArrayObject() {
        return Arrays.copyOf(values, length);
    }

    @Override
    public void setInternalArrayObject(Object arrayObject) {
        PythonAbstractClass[] classArray = (PythonAbstractClass[]) arrayObject;
        this.values = classArray;
        this.length = classArray.length;
        this.capacity = classArray.length;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Generic;
    }

    public Assumption getLookupStableAssumption() {
        return lookupStableAssumption.getAssumption();
    }

    public Assumption createAttributeInMROFinalAssumption(String name) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        Assumption assumption = Truffle.getRuntime().createAssumption(name.toString());
        attrAssumptions.add(assumption);
        return assumption;
    }

    public void addAttributeInMROFinalAssumption(String name, Assumption assumption) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        attrAssumptions.add(assumption);
    }

    @TruffleBoundary
    public void invalidateAttributeInMROFinalAssumptions(String name) {
        List<Assumption> assumptions = attributesInMROFinalAssumptions.getOrDefault(name, new ArrayList<>());
        if (!assumptions.isEmpty()) {
            String message = getClassName() + "." + name;
            for (Assumption assumption : assumptions) {
                assumption.invalidate(message);
            }
        }
    }

    public void lookupChanged() {
        CompilerAsserts.neverPartOfCompilation();
        for (List<Assumption> list : attributesInMROFinalAssumptions.values()) {
            for (Assumption assumption : list) {
                assumption.invalidate();
            }
        }
        lookupStableAssumption.invalidate();
    }

    public void lookupChanged(String msg) {
        CompilerAsserts.neverPartOfCompilation();
        for (List<Assumption> list : attributesInMROFinalAssumptions.values()) {
            for (Assumption assumption : list) {
                assumption.invalidate();
            }
        }
        lookupStableAssumption.invalidate(msg);
    }

}
