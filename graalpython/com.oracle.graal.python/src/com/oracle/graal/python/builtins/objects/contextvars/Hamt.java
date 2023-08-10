/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.truffle.api.CompilerDirectives;

/**
 * Implements a Hamt following a similar internal structure to the one in cpython <a href=
 * "https://github.com/python/cpython/blob/main/Python/hamt.c#L9-L251">https://github.com/python/cpython/blob/main/Python/hamt.c#L9-L251</a>
 * There is a lot of room for optimization, but since most HAMTs which will occur in a python
 * program are quite small, as they are used exclusively for contextvars, it is probably not worth
 * spending extra effort on until we can benchmark sync web frameworks, and later async frameworks.
 *
 * It may make sense to use a sealed interface here eventually, rather than dispatching manually
 * with instanceof.
 */

public final class Hamt {

    public String dump() {
        return dumpPart(this.root, 0);
    }

    private static String dumpPart(TreePart i, int indent) {
        return i == null ? " ".repeat(indent) + "null\n" : i.dump(indent);
    }

    public int size() {
        return computeSize(this.root);
    }

    private static int sumSize(TreePart[] arr) {
        int i = 0;
        for (TreePart el : arr) {
            i += computeSize(el);
        }
        return i;
    }

    private static int computeSize(TreePart part) {
        if (part == null) {
            return 0;
        } else if (part instanceof Entry) {
            return 1;
        } else if (part instanceof CollisionPart) {
            return sumSize(((CollisionPart) part).elems);
        } else if (part instanceof ArrayPart) {
            return sumSize(((ArrayPart) part).elems);
        } else if (part instanceof BitmapPart) {
            return sumSize(((BitmapPart) part).elems);
        }
        throw CompilerDirectives.shouldNotReachHere("TreePart type is not handled");
    }

    final TreePart root;

    public Hamt() {
        this(null);
    }

    private Hamt(TreePart root) {
        this.root = root;
    }

    private static int hashIdx(int hash, int hashShift) {
        // Since we mask off the high 2 bits of the hash, it is always positive
        return hashTail(hash >> hashShift);
    }

    private static int hashTail(int hash) {
        return hash & 0x1F; // last 5 bits
    }

    private static int idxToBit(int idx) {
        assert idx < 32 && idx >= 0 : "invalid bitmap index: " + idx;
        return 1 << idx;
    }

    private static int popcount(int n) {
        return Integer.bitCount(n);
    }

    private static int bitmapToIdx(int bitmap, int position) {
        int shiftedBitmap = bitmap >>> position;
        if ((shiftedBitmap & 1) == 1) {
            return popcount(shiftedBitmap) - 1;
        } else {
            return -1;
        }
    }

    private static BitmapPart bitmapPartsForPair(TreePart one, int hashOne, TreePart two, int hashTwo, int hashShift) {
        assert hashOne != hashTwo : "cannot work with colliding parts";
        int oneIdx = hashIdx(hashOne, hashShift);
        int twoIdx = hashIdx(hashTwo, hashShift);
        if (oneIdx == twoIdx) {
            return new BitmapPart(new TreePart[]{bitmapPartsForPair(one, hashOne, two, hashTwo, hashShift + 5)}, idxToBit(twoIdx));
        }
        return new BitmapPart(oneIdx > twoIdx ? new TreePart[]{one, two} : new TreePart[]{two, one}, idxToBit(twoIdx) | idxToBit(oneIdx));
    }

    @CompilerDirectives.TruffleBoundary
    private static TreePart partWithEntry(TreePart original, Entry newEntry, int hashShift) {
        assert hashShift <= 25;
        if (original == null) {
            return newEntry;
        }
        if (original instanceof Entry) {
            Entry existing = (Entry) original;
            if (newEntry.hash == existing.hash) {
                if (PyObjectRichCompareBool.EqNode.compareUncached(newEntry.key, existing.key)) {
                    return newEntry;
                } else {
                    return new CollisionPart(existing.hash, existing, newEntry);
                }
            }
            return bitmapPartsForPair(newEntry, newEntry.hash, existing, existing.hash, hashShift);
        }
        if (original instanceof BitmapPart) {
            BitmapPart existing = (BitmapPart) original;
            int position = hashIdx(newEntry.hash, hashShift);
            int sparseIdx = bitmapToIdx(existing.bitmap, position);
            if (sparseIdx < 0) {
                int originalLength = existing.elems.length;
                if (originalLength >= 15) {
                    TreePart[] newElems = new TreePart[32];
                    newElems[position] = newEntry;
                    int elemsI = originalLength - 1;
                    for (int i = 0; i < 32; ++i) {
                        if (((existing.bitmap >>> i) & 1) == 1) {
                            newElems[i] = existing.elems[elemsI--];
                        }
                    }
                    return new ArrayPart(newElems);
                } else {
                    int newBitmap = existing.bitmap | idxToBit(position);
                    TreePart[] newElems = new TreePart[existing.elems.length + 1];
                    int insertAt = bitmapToIdx(newBitmap, position);
                    assert insertAt >= 0;
                    int oldI = 0;
                    for (int i = 0; i <= originalLength; ++i) {
                        if (insertAt == i) {
                            newElems[i] = newEntry;
                        } else {
                            newElems[i] = existing.elems[oldI++];
                        }
                    }
                    return new BitmapPart(newElems, newBitmap);
                }
            } else {
                TreePart[] toReplaceIn = existing.elems.clone();
                TreePart toReplace = toReplaceIn[sparseIdx];
                TreePart newPart = partWithEntry(toReplace, newEntry, hashShift + 5);
                toReplaceIn[sparseIdx] = newPart;
                return new BitmapPart(toReplaceIn, existing.bitmap);
            }
        }
        if (original instanceof ArrayPart) {
            ArrayPart existing = (ArrayPart) original;
            int position = hashIdx(newEntry.hash, hashShift);
            TreePart[] toReplaceIn = existing.elems.clone();
            TreePart toReplace = toReplaceIn[position];
            TreePart newPart = partWithEntry(toReplace, newEntry, hashShift + 5);
            toReplaceIn[position] = newPart;
            return new ArrayPart(toReplaceIn);
        }
        if (original instanceof CollisionPart) {
            CollisionPart existing = (CollisionPart) original;
            if (existing.hash == newEntry.hash) {
                int originalLength = existing.elems.length;
                Entry[] newElems = new Entry[originalLength + 1];
                newElems[originalLength] = newEntry;
                System.arraycopy(existing.elems, 0, newElems, 0, originalLength);
                return new CollisionPart(existing.hash, newElems);
            } else {
                return bitmapPartsForPair(existing, existing.hash, newEntry, newEntry.hash, hashShift);
            }
        }
        throw CompilerDirectives.shouldNotReachHere("TreePart type is not handled");
    }

    public Hamt withEntry(Entry newEntry) {
        return new Hamt(partWithEntry(this.root, newEntry, 0));
    }

    @CompilerDirectives.TruffleBoundary
    private static Object lookupKeyInPart(TreePart part, Object key, int hash, int hashShift) {
        assert hashShift <= 25;
        if (part == null) {
            return null;
        }
        if (part instanceof Entry) {
            Entry existing = (Entry) part;
            if (existing.hash == hash && PyObjectRichCompareBool.EqNode.compareUncached(existing.key, key)) {
                return existing.value;
            }
            return null;
        }
        if (part instanceof BitmapPart) {
            BitmapPart existing = (BitmapPart) part;
            int position = hashIdx(hash, hashShift);
            int sparseIdx = bitmapToIdx(existing.bitmap, position);
            if (sparseIdx < 0) {
                return null;
            }
            TreePart deeper = existing.elems[sparseIdx];
            return lookupKeyInPart(deeper, key, hash, hashShift + 5);
        }
        if (part instanceof ArrayPart) {
            ArrayPart existing = (ArrayPart) part;
            int position = hashIdx(hash, hashShift);
            return lookupKeyInPart(existing.elems[position], key, hash, hashShift + 5);
        }
        if (part instanceof CollisionPart) {
            CollisionPart existing = (CollisionPart) part;
            if (existing.hash != hash) {
                return null;
            }
            for (Entry entry : existing.elems) {
                if (PyObjectRichCompareBool.EqNode.compareUncached(entry.key, key)) {
                    return entry.value;
                }
            }
            return null;
        }
        throw CompilerDirectives.shouldNotReachHere("TreePart type not handled");
    }

    public Object lookup(Object key, int hash) {
        return lookupKeyInPart(root, key, hash, 0);
    }

    private static TreePart bitmapWithoutKey(BitmapPart existing, Object key, int hash, int hashShift) {
        int position = hashIdx(hash, hashShift);
        int sparseIdx = bitmapToIdx(existing.bitmap, position);
        if (sparseIdx < 0) {
            return existing;
        }
        TreePart replacement = partWithoutKey(existing.elems[sparseIdx], key, hash, hashShift + 5);
        int currentLen = existing.elems.length;
        if (currentLen == 1) {
            if (replacement == null) {
                // if we have no elements, we can simply delete the BitmapPart entirely
                return null;
            } else if (replacement instanceof Entry || replacement instanceof CollisionPart) {
                // if the only element is an entry, we can simply skip the BitmapPart
                // we cannot do the same for the other TreeParts, since those rely on
                // depth to find which part of the hash is relevant to them.
                return replacement;
            }
            // fall through to default
        }
        if (currentLen == 2 && replacement == null) {
            // we have one element left, if it is an element which doesn't rely on its
            // depth,
            // return that element, otherwise, run the normal removal logic
            int otherIdx = sparseIdx == 0 ? 1 : 0;
            TreePart otherElem = existing.elems[otherIdx];
            if (otherElem instanceof Entry || otherElem instanceof CollisionPart) {
                return otherElem;
            }
            // fall through
        }

        if (replacement == null) {
            int newBitmap = existing.bitmap & ~idxToBit(position);
            TreePart[] newElems = new TreePart[existing.elems.length - 1];
            int newI = 0;
            for (int i = 0; i < existing.elems.length; ++i) {
                if (i != sparseIdx) {
                    newElems[newI++] = existing.elems[i];
                }
            }
            assert newI == newElems.length;
            return new BitmapPart(newElems, newBitmap);
        }
        TreePart[] newElems = existing.elems.clone();
        newElems[sparseIdx] = replacement;
        return new BitmapPart(newElems, existing.bitmap);
    }

    @CompilerDirectives.TruffleBoundary
    private static TreePart partWithoutKey(TreePart root, Object key, int hash, int hashShift) {
        if (root == null) {
            return null;
        }
        if (root instanceof Entry) {
            Entry existing = (Entry) root;
            if (existing.hash == hash && PyObjectRichCompareBool.EqNode.compareUncached(existing.key, key)) {
                return null;
            }
            return root;
        }
        if (root instanceof BitmapPart) {
            BitmapPart existing = (BitmapPart) root;
            return bitmapWithoutKey(existing, key, hash, hashShift);
        }
        if (root instanceof ArrayPart) {
            ArrayPart existing = (ArrayPart) root;
            int position = hashIdx(hash, hashShift);
            TreePart replacement = partWithoutKey(existing.elems[position], key, hash, hashShift + 5);
            if (replacement == null) {
                // replace this part with a BitmapPart if the array were to store fewer than 16
                // parts
                int bitmap = 0;
                for (int i = 0; i < existing.elems.length; ++i) {
                    if (existing.elems[i] != null && position != i) {
                        bitmap |= idxToBit(i);
                    }
                }
                if (popcount(bitmap) < 16) {
                    assert popcount(bitmap) == 15;
                    TreePart[] newElems = new TreePart[15];
                    int newElemsI = 15;
                    for (int i = 0; i < existing.elems.length; ++i) {
                        if (i != position && existing.elems[i] != null) {
                            newElems[--newElemsI] = existing.elems[i];
                        }
                    }
                    assert newElemsI == 0;
                    return new BitmapPart(newElems, bitmap);
                }
                // fall through to normal logic
            }
            TreePart[] newElems = existing.elems.clone();
            newElems[position] = replacement;
            return new ArrayPart(newElems);
        }
        if (root instanceof CollisionPart) {
            CollisionPart existing = (CollisionPart) root;
            if (existing.hash == hash) {
                for (int i = 0; i < existing.elems.length; ++i) {
                    if (PyObjectRichCompareBool.EqNode.compareUncached(existing.elems[i].key, key)) {
                        if (existing.elems.length == 1) {
                            return null;
                        }
                        int newI = 0;
                        Entry[] newElems = new Entry[existing.elems.length - 1];
                        for (int j = 0; j < existing.elems.length; ++j) {
                            if (j != i) {
                                newElems[newI++] = existing.elems[j];
                            }
                        }
                        if (newElems.length == 1) {
                            return newElems[0];
                        }
                        return new CollisionPart(hash, newElems);
                    }
                }
            }
            return root;
        }
        throw CompilerDirectives.shouldNotReachHere("TreePart type not handled");
    }

    public Hamt without(Object key, int hash) {
        return new Hamt(partWithoutKey(root, key, hash, 0));
    }

    interface TreePart {
        String dump(int indent);
    }

    static final class BitmapPart implements TreePart {
        final int bitmap;
        final TreePart[] elems;

        public BitmapPart(TreePart[] elems, int bitmap) {
            for (TreePart e : elems) {
                assert e != null;
            }
            this.elems = elems;
            this.bitmap = bitmap;
        }

        @Override
        public String dump(int indent) {
            StringBuilder result = new StringBuilder();
            result.append(" ".repeat(indent));
            result.append("Bitmap (");
            result.append(Integer.toBinaryString(this.bitmap));
            result.append(")\n");
            for (TreePart i : elems) {
                result.append(dumpPart(i, indent + 2));
            }
            return result.toString();
        }
    }

    static final class ArrayPart implements TreePart {
        final TreePart[] elems;

        public ArrayPart(TreePart[] elems) {
            assert elems.length == 32;
            this.elems = elems;
        }

        @Override
        public String dump(int indent) {
            StringBuilder result = new StringBuilder();
            result.append(" ".repeat(indent));
            result.append("Array\n");
            for (TreePart i : elems) {
                result.append(dumpPart(i, indent + 2));
            }
            return result.toString();
        }
    }

    static final class CollisionPart implements TreePart {
        final int hash;
        final Entry[] elems;

        public CollisionPart(int hash, Entry... elems) {
            this.hash = hash;
            this.elems = elems;
        }

        @Override
        public String dump(int indent) {
            StringBuilder result = new StringBuilder();
            result.append(" ".repeat(indent));
            result.append("Collision ");
            result.append(hash);
            result.append('\n');
            for (Entry i : elems) {
                result.append(dumpPart(i, indent + 2));
            }
            return result.toString();
        }
    }

    @CompilerDirectives.ValueType
    public static final class Entry implements TreePart {
        public final Object key;
        final int hash;
        public final Object value;

        public Entry(Object key, int hash, Object value) {
            this.key = key;
            this.hash = hash & 0x3FFFFFFF; // 30 bits
            this.value = value;
        }

        @Override
        public String dump(int indent) {
            return " ".repeat(indent) + String.format("%s : %s (%d)\n", key, value, hash);
        }
    }
}
