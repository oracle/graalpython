/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class MroSequenceStorage extends ArrayBasedSequenceStorage {

    private final TruffleString className;
    /**
     * This assumption will be invalidated whenever the mro changes.
     */
    private final CyclicAssumption lookupStableAssumption;

    /**
     * These assumptions will be invalidated whenever the value of the given slot changes. All
     * assumptions will be invalidated if the mro changes.
     */
    private final Map<TruffleString, List<Assumption>> attributesInMROFinalAssumptions;
    private boolean hasAttributesInMROFinalAssumptions;

    @CompilationFinal(dimensions = 1) private final PythonAbstractClass[] values;

    /**
     * We cannot simply replace an {@code MroSequenceStorage} with a {@link NativeSequenceStorage}
     * because we still need the <emph>managed</emph> one due to the assumptions. Therefore, if an
     * {@code MroSequenceStorage} goes to native, we will create an additional
     * {@link NativeSequenceStorage} and link to it.
     */
    private NativeSequenceStorage nativeMirror;

    @TruffleBoundary
    public MroSequenceStorage(TruffleString className, PythonAbstractClass[] elements) {
        this.className = className;
        this.values = elements;
        this.capacity = elements.length;
        this.length = elements.length;
        this.lookupStableAssumption = new CyclicAssumption(className.toJavaStringUncached());
        this.attributesInMROFinalAssumptions = new HashMap<>();
    }

    @TruffleBoundary
    public MroSequenceStorage(TruffleString className, int capacity) {
        this.className = className;
        this.values = new PythonAbstractClass[capacity];
        this.capacity = capacity;
        this.length = 0;
        this.lookupStableAssumption = new CyclicAssumption(className.toJavaStringUncached());
        this.attributesInMROFinalAssumptions = new HashMap<>();
    }

    @Override
    public PythonAbstractClass getItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    @SuppressWarnings("unused")
    public void setItemNormalized(int idx, Object value) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    public TruffleString getClassName() {
        return className;
    }

    @Override
    public MroSequenceStorage createEmpty(int newCapacity) {
        return new MroSequenceStorage(getClassName(), newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        return values;
    }

    public PythonAbstractClass[] getInternalClassArray() {
        return values;
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public Object getInternalArrayObject() {
        return values;
    }

    @Override
    public Object getCopyOfInternalArrayObject() {
        return Arrays.copyOf(values, length);
    }

    @SuppressWarnings("unused")
    @Override
    public void setInternalArrayObject(Object arrayObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not be reached");
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Generic;
    }

    public Assumption getLookupStableAssumption() {
        return lookupStableAssumption.getAssumption();
    }

    public Assumption createAttributeInMROFinalAssumption(TruffleString name) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            hasAttributesInMROFinalAssumptions = true;
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        Assumption assumption = Truffle.getRuntime().createAssumption(name.toString());
        attrAssumptions.add(assumption);
        return assumption;
    }

    public void addAttributeInMROFinalAssumption(TruffleString name, Assumption assumption) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            hasAttributesInMROFinalAssumptions = true;
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        attrAssumptions.add(assumption);
    }

    /**
     * Returns {@code true} if some assumption was actually invalidated.
     */
    @TruffleBoundary
    public boolean invalidateAttributeInMROFinalAssumptions(TruffleString name) {
        List<Assumption> assumptions = attributesInMROFinalAssumptions.getOrDefault(name, Collections.emptyList());
        // the empty check is just to avoid the StringBuilder allocation
        if (!assumptions.isEmpty()) {
            if (invalidateAttributesInMROFinalAssumptions(assumptions, getClassName() + "." + name)) {
                // remove list
                attributesInMROFinalAssumptions.remove(name);
            }
            return true;
        }
        return false;
    }

    public void lookupChanged() {
        CompilerAsserts.neverPartOfCompilation();
        attributesInMROFinalAssumptions.values().removeIf(REMOVE_IF_LARGE);
        lookupStableAssumption.invalidate();
    }

    private static final Predicate<List<Assumption>> REMOVE_IF_LARGE = new Predicate<>() {

        @Override
        public boolean test(List<Assumption> assumptions) {
            return invalidateAttributesInMROFinalAssumptions(assumptions, "");
        }
    };

    @TruffleBoundary
    private static boolean invalidateAttributesInMROFinalAssumptions(List<Assumption> list, String reason) {
        int n = list.size();
        if (n > 0) {
            for (Assumption assumption : list) {
                assumption.invalidate(reason);
            }
        }

        // clear assumptions to avoid memory leak; they are all invalidated, so we don't need
        // them any longer
        if (n < 16) {
            // keep small lists; they don't hurt too much and we save allocations as well as GC
            // pressure
            list.clear();

            // indicate to keep the list instance
            return false;
        }
        // indicate that the list should completely be removed
        return true;
    }

    public Object[] getCopyOfInternalArray() {
        return getInternalArray();
    }

    public boolean hasAttributeInMROFinalAssumptions() {
        return hasAttributesInMROFinalAssumptions;
    }

    public NativeSequenceStorage getNativeMirror() {
        return nativeMirror;
    }

    public void setNativeMirror(NativeSequenceStorage nativeMirror) {
        this.nativeMirror = nativeMirror;
    }

    public boolean isNative() {
        return nativeMirror != null;
    }
}
