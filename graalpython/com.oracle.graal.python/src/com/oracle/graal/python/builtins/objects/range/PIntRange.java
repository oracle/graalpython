/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.range;

import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.iterator.PIntRangeIterator;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonObjectLibrary.class)
public final class PIntRange extends PRange {

    private final int start;
    private final int stop;
    private final int step;
    private final int length;

    public PIntRange(int start, int stop, int step, int length) {
        this.start = start;
        this.stop = stop;
        this.step = step;

        this.length = length;
    }

    @Override
    public Object getStart() {
        return getIntStart();
    }

    @Override
    public Object getStep() {
        return getIntStep();
    }

    @Override
    public Object getStop() {
        return getIntStop();
    }

    @Override
    public Object getLength() {
        return getIntLength();
    }

    public int getIntStart() {
        return start;
    }

    public int getIntStep() {
        return step;
    }

    public int getIntStop() {
        return stop;
    }

    public int getIntLength() {
        return length;
    }

    public int getIntItemNormalized(int index) {
        assert index < length;
        return index * step + start;
    }

    @Override
    protected boolean withStep() {
        return step != 1;
    }

    @ExportMessage
    public int length() {
        return length;
    }

    @ExportMessage
    public boolean isTrue() {
        return length != 0;
    }

    @ExportMessage
    PIntRangeIterator getIteratorWithState(@SuppressWarnings("unused") ThreadState threadState,
                    @Cached PythonObjectFactory factory) {
        return factory.createIntRangeIterator(this);
    }
}
