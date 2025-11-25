/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NonIdempotent;
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
    private Map<TruffleString, FinalAttributeAssumptionNode> attributesInMROFinalAssumptions;

    public static final class FinalAttributeAssumptionNode {
        @CompilationFinal private Assumption assumption;
        @CompilationFinal private Object value;

        public FinalAttributeAssumptionNode() {
            this.assumption = Truffle.getRuntime().createAssumption("attribute in MRO final");
        }

        public void invalidate() {
            if (assumption != null) {
                assumption.invalidate("MRO entry not final");
            }
            assumption = null;
            value = null;
        }

        @NonIdempotent
        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        @NonIdempotent
        public Assumption getAssumption() {
            return assumption;
        }
    }

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

    public PythonAbstractClass getPythonClassItemNormalized(int idx) {
        return values[idx];
    }

    public TruffleString getClassName() {
        return className;
    }

    @Override
    public MroSequenceStorage createEmpty(int newCapacity) {
        return new MroSequenceStorage(getClassName(), newCapacity);
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
        return getCopyOfInternalArray();
    }

    public Object[] getCopyOfInternalArray() {
        return PythonUtils.arrayCopyOf(values, length);
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

    public FinalAttributeAssumptionNode getFinalAttributeAssumption(TruffleString name) {
        CompilerAsserts.neverPartOfCompilation();
        if (attributesInMROFinalAssumptions != null) {
            return attributesInMROFinalAssumptions.get(name);
        }
        return null;
    }

    public void putFinalAttributeAssumption(TruffleString name, FinalAttributeAssumptionNode node) {
        CompilerAsserts.neverPartOfCompilation();
        if (attributesInMROFinalAssumptions == null) {
            attributesInMROFinalAssumptions = new HashMap<>();
        }
        assert attributesInMROFinalAssumptions.get(name) == null;
        attributesInMROFinalAssumptions.put(name, node);
    }

    public void invalidateFinalAttributeAssumption(TruffleString name) {
        CompilerAsserts.neverPartOfCompilation();
        FinalAttributeAssumptionNode node = getFinalAttributeAssumption(name);
        if (node != null) {
            node.invalidate();
        }
    }

    public void lookupChanged() {
        CompilerAsserts.neverPartOfCompilation();
        if (attributesInMROFinalAssumptions != null) {
            for (FinalAttributeAssumptionNode node : attributesInMROFinalAssumptions.values()) {
                node.invalidate();
            }
        }
        lookupStableAssumption.invalidate();
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
