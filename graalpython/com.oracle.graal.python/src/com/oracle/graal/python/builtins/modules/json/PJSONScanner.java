/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class PJSONScanner extends PythonBuiltinObject {

    final KeyMemo memo;
    final boolean strict;
    final Object objectHook;
    final Object objectPairsHook;
    final Object parseFloat;
    final Object parseInt;
    final Object parseConstant;

    public PJSONScanner(Object cls, Shape instanceShape, boolean strict, Object objectHook, Object objectPairsHook, Object parseFloat, Object parseInt, Object parseConstant) {
        super(cls, instanceShape);
        CompilerAsserts.neverPartOfCompilation();
        this.memo = new KeyMemo();
        this.strict = strict;
        this.objectHook = objectHook;
        this.objectPairsHook = objectPairsHook;
        this.parseFloat = parseFloat;
        this.parseInt = parseInt;
        this.parseConstant = parseConstant;
    }

    /** Interns JSON property names without calling the uncached Java string equality methods. */
    static final class KeyMemo {
        private TruffleString[] keys = new TruffleString[16];
        private int size;

        TruffleString intern(TruffleString key, int hash, TruffleString.EqualNode equalNode) {
            TruffleString[] table = keys;
            int mask = table.length - 1;
            int index = spread(hash) & mask;
            TruffleString existing;
            while ((existing = table[index]) != null) {
                if (equalNode.execute(existing, key, TS_ENCODING)) {
                    return existing;
                }
                index = (index + 1) & mask;
            }
            if (size < table.length - (table.length >> 2)) {
                table[index] = key;
                size++;
            } else {
                growAndInsert(key, hash);
            }
            return key;
        }

        private static int spread(int hash) {
            return hash ^ (hash >>> 16);
        }

        @TruffleBoundary
        private void growAndInsert(TruffleString key, int hash) {
            TruffleString[] oldKeys = keys;
            if (oldKeys.length == 1 << 30) {
                throw new OutOfMemoryError();
            }
            TruffleString[] newKeys = new TruffleString[oldKeys.length << 1];
            for (TruffleString existing : oldKeys) {
                if (existing != null) {
                    // All entries were hashed by HashCodeNode before being inserted.
                    insert(newKeys, existing, existing.hashCode());
                }
            }
            insert(newKeys, key, hash);
            keys = newKeys;
            size++;
        }

        private static void insert(TruffleString[] table, TruffleString key, int hash) {
            int mask = table.length - 1;
            int index = spread(hash) & mask;
            while (table[index] != null) {
                index = (index + 1) & mask;
            }
            table[index] = key;
        }
    }

}
