package com.oracle.graal.python.builtins.objects.contextvars;

import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.truffle.api.CompilerDirectives;

public final class Hamt {

    @CompilerDirectives.TruffleBoundary
    public String dump() {
        return dumpPart(this.root, 0);
    }

    private static String dumpPart(TreePart i, int indent) {
        return i == null ? " ".repeat(indent) + "null\n" : i.dump(indent);
    }

    private interface TreePart {
        String dump(int indent);
    }

    @CompilerDirectives.ValueType
    public final static class Entry implements TreePart {
        final Object key;
        final int hash;
        final Object value;

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

    // TODO: track size on the Hamt.
    private final TreePart root;

    public Hamt() {
        this(null);
    }

    private Hamt(TreePart root) {
        this.root = root;
    }

    private static int hashIdx(int hash, int hashShift) {
        // since we never access the bits the shift adds here, it is fine that it is adding ones to
        // the start
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
        if ((bitmap & (1 << position)) != 0) {
            // java cannot do unsigned shifts, so we need to mask off the low bits instead
            return popcount(bitmap & (-1 << position)) - 1;
        } else {
            return -1;
        }
    }

    private static BitmapNode bitmapNodesForPair(TreePart one, int hashOne, TreePart two, int hashTwo, int hashShift) {
        assert hashOne != hashTwo : "bitmapNodesForPair cannot work with colliding nodes";
        int oneIdx = hashIdx(hashOne, hashShift);
        int twoIdx = hashIdx(hashTwo, hashShift);
        if (oneIdx == twoIdx) {
            return new BitmapNode(new TreePart[]{bitmapNodesForPair(one, hashOne, two, hashTwo, hashShift + 5)}, idxToBit(twoIdx));
        }
        return new BitmapNode(oneIdx > twoIdx ? new TreePart[]{one, two} : new TreePart[]{two, one}, idxToBit(twoIdx) | idxToBit(oneIdx));
    }

    private static TreePart nodeWithEntry(TreePart original, Entry newEntry, int hashShift) {
        assert hashShift <= 25;
        if (original == null) {
            return newEntry;
        }
        if (original instanceof Entry) {
            Entry existing = (Entry) original;
            if (newEntry.hash == existing.hash) {
                if (PyObjectRichCompareBool.EqNode.getUncached().execute(null, newEntry.key, existing.key)) {
                    return newEntry;
                } else {
                    return new CollisionNode(existing.hash, existing, newEntry);
                }
            }
            return bitmapNodesForPair(newEntry, newEntry.hash, existing, existing.hash, hashShift);
        }
        if (original instanceof BitmapNode) {
            BitmapNode existing = (BitmapNode) original;
            int position = hashIdx(newEntry.hash, hashShift);
            int sparseIdx = bitmapToIdx(existing.bitmap, position);
            if (sparseIdx < 0) {
                int originalLength = existing.elems.length;
                if (originalLength >= 15) {
                    TreePart[] newElems = new TreePart[32];
                    newElems[position] = newEntry;
                    int elemsI = originalLength - 1;
                    for (int i = 0; i < 32; ++i) {
                        if (((existing.bitmap) & (1 << i)) != 0) {
                            newElems[i] = existing.elems[elemsI--];
                        }
                    }
                    return new ArrayNode(newElems);
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
                    return new BitmapNode(newElems, newBitmap);
                }
            } else {
                TreePart[] toReplaceIn = existing.elems.clone();
                TreePart toReplace = toReplaceIn[sparseIdx];
                TreePart newPart = nodeWithEntry(toReplace, newEntry, hashShift + 5);
                toReplaceIn[sparseIdx] = newPart;
                return new BitmapNode(toReplaceIn, existing.bitmap);
            }
        }
        if (original instanceof ArrayNode) {
            ArrayNode existing = (ArrayNode) original;
            int position = hashIdx(newEntry.hash, hashShift);
            TreePart[] toReplaceIn = existing.elems.clone();
            TreePart toReplace = toReplaceIn[position];
            TreePart newPart = nodeWithEntry(toReplace, newEntry, hashShift + 5);
            toReplaceIn[position] = newPart;
            return new ArrayNode(toReplaceIn);
        }
        if (original instanceof CollisionNode) {
            CollisionNode existing = (CollisionNode) original;
            if (existing.hash == newEntry.hash) {
                int originalLength = existing.elems.length;
                Entry[] newElems = new Entry[originalLength + 1];
                newElems[originalLength] = newEntry;
                System.arraycopy(existing.elems, 0, newElems, 0, originalLength);
                return new CollisionNode(existing.hash, newElems);
            } else {
                return bitmapNodesForPair(existing, existing.hash, newEntry, newEntry.hash, hashShift);
            }
        }
        throw CompilerDirectives.shouldNotReachHere("TreePart type is not handled");
    }

    public Hamt withEntry(Entry newEntry) {
        TreePart root = nodeWithEntry(this.root, newEntry, 0);
        return new Hamt(root);
    }

    private static Object lookupKeyInPart(TreePart part, Object key, int hash, int hashShift) {
        assert hashShift <= 25;
        if (part == null) {
            return null;
        }
        if (part instanceof Entry) {
            Entry existing = (Entry) part;
            if (existing.hash == hash && PyObjectRichCompareBool.EqNode.getUncached().execute(null, existing.key, key)) {
                return existing.value;
            }
            return null;
        }
        if (part instanceof BitmapNode) {
            BitmapNode existing = (BitmapNode) part;
            int position = hashIdx(hash, hashShift);
            int sparseIdx = bitmapToIdx(existing.bitmap, position);
            if (sparseIdx < 0) {
                return null;
            }
            TreePart deeper = existing.elems[sparseIdx];
            return lookupKeyInPart(deeper, key, hash, hashShift + 5);
        }
        if (part instanceof ArrayNode) {
            ArrayNode existing = (ArrayNode) part;
            int position = hashIdx(hash, hashShift);
            return lookupKeyInPart(existing.elems[position], key, hash, hashShift + 5);
        }
        if (part instanceof CollisionNode) {
            CollisionNode existing = (CollisionNode) part;
            if (existing.hash != hash) {
                return null;
            }
            for (Entry entry : existing.elems) {
                if (PyObjectRichCompareBool.EqNode.getUncached().execute(null, entry.key, key)) {
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

    private static TreePart bitmapWithoutKey(BitmapNode existing, Object key, int hash, int hashShift) {
        int position = hashIdx(hash, hashShift);
        int sparseIdx = bitmapToIdx(existing.bitmap, position);
        if (sparseIdx < 0) {
            return existing;
        }
        TreePart replacement = partWithoutKey(existing.elems[sparseIdx], key, hash, hashShift + 5);
        switch (existing.elems.length) {
            case 1: {
                if (replacement == null) {
                    // if we have no elements, we can simply delete the BitmapPart entirely
                    return null;
                } else if (replacement instanceof Entry || replacement instanceof CollisionNode) {
                    // if the only element is an entry, we can simply skip the BitmapPart
                    // we cannot do the same for the other TreeParts, since those rely on
                    // depth to find which part of the hash is relevant to them.
                    return replacement;
                }
                // fall through to default
            }
            case 2: {
                if (replacement == null) {
                    // we have one element left, if it is an element which doesn't rely on its
                    // depth,
                    // return that element, otherwise, run the normal removal logic
                    int otherIdx = sparseIdx == 0 ? 1 : 0;
                    TreePart otherElem = existing.elems[otherIdx];
                    if (otherElem instanceof Entry || otherElem instanceof CollisionNode) {
                        return otherElem;
                    }
                }
                // fall through
            }
            default: {
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
                    return new BitmapNode(newElems, newBitmap);
                }
                TreePart[] newElems = existing.elems.clone();
                newElems[sparseIdx] = replacement;
                return new BitmapNode(newElems, existing.bitmap);
            }
        }

    }

    private static TreePart partWithoutKey(TreePart root, Object key, int hash, int hashShift) {
        if (root == null) {
            return null;
        }
        if (root instanceof Entry) {
            Entry existing = (Entry) root;
            if (existing.hash == hash && PyObjectRichCompareBool.EqNode.getUncached().execute(null, existing.key, key)) {
                return null;
            }
            return root;
        }
        if (root instanceof BitmapNode) {
            BitmapNode existing = (BitmapNode) root;
            return bitmapWithoutKey(existing, key, hash, hashShift);
        }
        if (root instanceof ArrayNode) {
            ArrayNode existing = (ArrayNode) root;
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
                    return new BitmapNode(newElems, bitmap);
                }
                // fall through to normal logic
            }
            TreePart[] newElems = existing.elems.clone();
            newElems[position] = replacement;
            return new ArrayNode(newElems);
        }
        if (root instanceof CollisionNode) {
            CollisionNode existing = (CollisionNode) root;
            if (existing.hash == hash) {
                for (int i = 0; i < existing.elems.length; ++i) {
                    if (PyObjectRichCompareBool.EqNode.getUncached().execute(null, existing.elems[i].key, key)) {
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
                        return new CollisionNode(hash, newElems);
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

    private final static class BitmapNode implements TreePart {
        final int bitmap;
        final TreePart[] elems;

        public BitmapNode(TreePart[] elems, int bitmap) {
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

    private final static class ArrayNode implements TreePart {
        final TreePart[] elems;

        public ArrayNode(TreePart[] elems) {
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

    private final static class CollisionNode implements TreePart {
        final int hash;
        final Entry[] elems;

        public CollisionNode(int hash, Entry... elems) {
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
}
