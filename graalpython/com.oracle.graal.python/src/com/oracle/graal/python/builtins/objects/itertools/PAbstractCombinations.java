/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.itertools;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

public abstract class PAbstractCombinations extends PythonBuiltinObject {

    private Object[] pool;
    private int r;
    private int[] indices;
    private Object[] lastResult;
    private boolean stopped;

    public PAbstractCombinations(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public final Object[] getPool() {
        return pool;
    }

    public final void setPool(Object[] pool) {
        this.pool = pool;
    }

    public final int getR() {
        return r;
    }

    public final void setR(int r) {
        this.r = r;
    }

    public final int[] getIndices() {
        return indices;
    }

    public final void setIndices(int[] indices) {
        this.indices = indices;
    }

    public final Object[] getLastResult() {
        return lastResult;
    }

    public final void setLastResult(Object[] lastResult) {
        this.lastResult = lastResult;
    }

    public final boolean isStopped() {
        return stopped;
    }

    public final void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    abstract int getMaximum(int poolLen, int i);

    abstract int maxIndex(int j);
}
