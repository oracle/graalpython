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
package com.oracle.graal.python.builtins.objects.array;

import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;

public final class PDoubleArray extends PArray {

    private final double[] array;

    public PDoubleArray(PythonClass clazz, double[] elements) {
        super(clazz);
        this.array = elements;
    }

    public double[] getSequence() {
        return array;
    }

    @Override
    public Object getItem(int idx) {
        int index = SequenceUtil.normalizeIndex(idx, array.length, "array index out of range");
        return getDoubleItemNormalized(index);
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getDoubleItemNormalized(idx);
    }

    public double getDoubleItemNormalized(int idx) {
        return array[idx];
    }

    public void setDoubleItemNormalized(int idx, double value) {
        array[idx] = value;
    }

    @Override
    public PDoubleArray getSlice(PythonObjectFactory factory, int start, int stop, int step, int length) {
        double[] newArray = new double[length];

        if (step == 1) {
            System.arraycopy(array, start, newArray, 0, stop - start);
            return factory.createDoubleArray(newArray);
        }
        for (int i = start, j = 0; j < length; i += step, j++) {
            newArray[j] = array[i];
        }
        return factory.createDoubleArray(newArray);
    }

    @Override
    public int len() {
        return array.length;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("array('d', [");
        for (int i = 0; i < array.length - 1; i++) {
            buf.append(array[i] + ", ");
        }
        buf.append(array[array.length - 1]);
        buf.append("])");
        return buf.toString();
    }
}
