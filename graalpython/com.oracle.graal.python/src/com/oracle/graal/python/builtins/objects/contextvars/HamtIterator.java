/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.truffle.api.CompilerDirectives;

public final class HamtIterator {
    // the depth of the tree is limited by the number of 5-bit bitstrings in the 32-bit hash + 1
    // CollisionNode with 1 entry child
    private static final int MAX_DEPTH = 8;

    // stores the indices used to get to the next node in path. nodeIndices[i] is the index in
    // path[i] that gets to path[i+1]. Only meaningful up to level-1.
    private final int[] nodeIndices = new int[MAX_DEPTH];

    // Stores the path to the entry that should be returned by next, including the entry.
    private final Hamt.TreePart[] path = new Hamt.TreePart[MAX_DEPTH];

    // the highest index of path that contains useful values, -1 if the iterator is exhausted
    private int level = 0;

    public HamtIterator(Hamt hamt) {
        path[0] = hamt.root;
        findFirstEntry();
    }

    private void visitLeftmost(Hamt.TreePart parent) {
        ++level;
        if (parent instanceof Hamt.CollisionPart) {
            Hamt.CollisionPart part = (Hamt.CollisionPart) parent;
            path[level] = part.elems[0];
            // at level - 1 is the index we used to get to part.elems[0].
            // At level would be the index used on part.elems[0] to get deeper
            nodeIndices[level - 1] = 0;
        } else if (parent instanceof Hamt.BitmapPart) {
            Hamt.BitmapPart part = (Hamt.BitmapPart) parent;
            path[level] = part.elems[0];
            nodeIndices[level - 1] = 0;
        } else if (parent instanceof Hamt.ArrayPart) {
            Hamt.ArrayPart part = (Hamt.ArrayPart) parent;
            for (int ret = 0; ret < part.elems.length; ++ret) {
                if (part.elems[ret] != null) {
                    path[level] = part.elems[ret];
                    nodeIndices[level - 1] = ret;
                    break;
                }
            }
        } else if (parent instanceof Hamt.Entry) {
            throw CompilerDirectives.shouldNotReachHere("got Entry in method for non-leaf nodes");
        } else {
            throw CompilerDirectives.shouldNotReachHere("unhandled TreePart type");
        }

    }

    private void findFirstEntry() {
        if (path[level] == null) {
            level = -1;
            return;
        }
        while (!(path[level] instanceof Hamt.Entry)) {
            visitLeftmost(path[level]);
        }
        // path[level] is now the first Entry
    }

    private void nextInArr(Hamt.TreePart[] arr, int idx) {
        for (int nextIdx = idx + 1; nextIdx < arr.length; nextIdx++) {
            if (arr[nextIdx] != null) {
                nodeIndices[level] = nextIdx;
                level++;
                path[level] = arr[nextIdx];
                findFirstEntry();
                return; // skip the nextEntry() call
            }
        }
        nextEntry(); // used up entire node
    }

    private void nextEntry() {
        level--;
        if (level < 0) {
            level = -1;
            return; // iterator exhausted
        }
        // the part above the just-yielded entry (or one of its parents)
        Hamt.TreePart toAdvance = path[level];
        int idx = nodeIndices[level];

        if (toAdvance instanceof Hamt.CollisionPart) {
            nextInArr(((Hamt.CollisionPart) toAdvance).elems, idx);
        } else if (toAdvance instanceof Hamt.BitmapPart) {
            nextInArr(((Hamt.BitmapPart) toAdvance).elems, idx);
        } else if (toAdvance instanceof Hamt.ArrayPart) {
            nextInArr(((Hamt.ArrayPart) toAdvance).elems, idx);
        } else if (toAdvance instanceof Hamt.Entry) {
            throw CompilerDirectives.shouldNotReachHere("Entry in non-leaf method");
        } else {
            throw CompilerDirectives.shouldNotReachHere("TreePart type not handled");
        }
    }

    public Hamt.Entry next() {
        if (level == -1) {
            return null;
        }
        Hamt.TreePart result = path[level];
        if (!(result instanceof Hamt.Entry)) {
            throw CompilerDirectives.shouldNotReachHere("Hamt path in invalid state");
        }
        nextEntry();
        return (Hamt.Entry) result;
    }

}
