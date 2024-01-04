/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.PicklingError;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class MemoTable {

    private static final int INITIAL_CAPACITY = 8;
    private static final int OCCUPANCY_EXPONENT = 1; // 2^X relation between capacity and size
    private static final int CAPACITY_INC_EXPONENT = 2; // 2^X increase in capacity when resizing

    public final class MemoIterator {

        private int index;
        private Object[] keys;
        private int[] values;

        public MemoIterator(MemoTable table) {
            this.keys = table.keys;
            this.values = table.values;
            this.index = -1;
        }

        public boolean advance() {
            while (true) {
                index++;
                if (index >= keys.length) {
                    return false;
                }
                if (keys[index] != null) {
                    return true;
                }
            }
        }

        public Object key() {
            return keys[index];
        }

        public int value() {
            return values[index];
        }
    }

    private Object[] keys;
    private int[] values;
    private int mask;
    private int size;

    public MemoTable() {
        initArrays(INITIAL_CAPACITY);
    }

    private MemoTable(MemoTable map) {
        this.keys = PythonUtils.arrayCopyOf(map.keys, map.keys.length);
        this.values = PythonUtils.arrayCopyOf(map.values, map.values.length);
        this.size = map.size;
        this.mask = map.mask;
    }

    public MemoTable copy() {
        return new MemoTable(this);
    }

    public int size() {
        return this.size;
    }

    public void clear() {
        initArrays(INITIAL_CAPACITY);
        this.size = 0;
    }

    private void initArrays(int newLength) {
        this.keys = new Object[newLength];
        this.values = new int[newLength];
        assert Integer.bitCount(newLength) == 1; // power-of-two
        this.mask = newLength - 1; // bitmask for power-of-two
    }

    private int getIndex(Object key) {
        return System.identityHashCode(key) & mask;
    }

    public int get(Object key) {
        int index = getIndex(key);
        int start = index;

        while (true) {
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, keys[index] == null)) {
                return -1;
            } else if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, keys[index] == key)) {
                return values[index];
            }

            // hash collision - perform linear scan
            index = (index + 1) & mask;
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, index == start)) {
                return -1;
            }
        }
    }

    private void setInternal(Object key, int value) {
        assert key != null;
        assert value >= 0;
        int index = getIndex(key);

        while (true) {
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, keys[index] == null)) {
                keys[index] = key;
                values[index] = value;
                return;
            }

            // hash collision - perform linear scan
            index = (index + 1) & mask;

            /*
             * This assumes that there is enough space - otherwise, this will be an endless loop.
             */
        }
    }

    @TruffleBoundary
    private void resize() {
        int newLength = keys.length << CAPACITY_INC_EXPONENT;
        if (newLength <= keys.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // overflow
            throw PRaiseNode.raiseUncached(null, PicklingError, ErrorMessages.STRUCT_SIZE_TOO_LONG);
        }

        MemoIterator iterator = iterator(); // captures the current contents
        initArrays(newLength);

        while (iterator.advance()) {
            setInternal(iterator.key(), iterator.value());
        }
    }

    public void set(Object key, int value) {
        // we know that we always have space for at least one element
        setInternal(key, value);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, ++this.size > (keys.length >> OCCUPANCY_EXPONENT))) {
            resize();
        }
    }

    public MemoIterator iterator() {
        return new MemoIterator(this);
    }
}
