/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.frame;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFrameLocalsProxy)
public final class FrameLocalsProxyBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = FrameLocalsProxyBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrameLocalsProxyBuiltinsFactory.getFactories();
    }

    private static int findSlot(VirtualFrame frame, Node inliningTarget, PFrameLocalsProxy self, Object key, PyObjectRichCompareBool equals) {
        BytecodeFrame bytecodeFrame = self.getBytecodeFrame();
        BytecodeDSLFrameInfo info = (BytecodeDSLFrameInfo) bytecodeFrame.getFrameDescriptorInfo();
        // Cell parameters occur in both varnames and cellvars. The function prologue moves their
        // value to the cell slot and clears the regular slot, so prefer the later cell slot.
        for (int i = info.getVariableCount() - 1; i >= 0; i--) {
            if (equals.executeEq(frame, inliningTarget, info.getVariableName(i), key)) {
                return i;
            }
        }
        return -1;
    }

    private static Object getSlotValue(PFrameLocalsProxy self, int slot) {
        Object value = getLocalValue(self.getBytecodeFrame(), slot);
        if (value instanceof PCell cell) {
            value = cell.getRef();
        }
        return value;
    }

    // FIXME: Truffle has a PE-constant assertion for the BytecodeFrame BCI which isn't even used for us
    @TruffleBoundary
    private static Object getLocalValue(BytecodeFrame bytecodeFrame, int slot) {
        return bytecodeFrame.getLocalValue(slot);
    }

    @TruffleBoundary
    private static void setLocalValue(BytecodeFrame bytecodeFrame, int slot, Object value) {
        bytecodeFrame.setLocalValue(slot, value);
    }

    private static Object lookup(VirtualFrame frame, Node inliningTarget, PFrameLocalsProxy self, Object key, PyObjectRichCompareBool equals, HashingStorageGetItem getItem) {
        int slot = findSlot(frame, inliningTarget, self, key, equals);
        if (slot >= 0) {
            Object value = getSlotValue(self, slot);
            if (value != null) {
                return value;
            }
        }
        PDict extra = self.getFrame().getExtraLocals();
        return extra == null ? null : getItem.execute(frame, inliningTarget, extra.getDictStorage(), key);
    }

    private static PDict extras(PFrameLocalsProxy self, PythonLanguage language) {
        PFrame pFrame = self.getFrame();
        PDict result = pFrame.getExtraLocals();
        if (result == null) {
            result = PFactory.createDict(language);
            pFrame.setExtraLocals(result);
        }
        return result;
    }

    private static PDict snapshot(PFrameLocalsProxy self, PythonLanguage language, Node inliningTarget, HashingStorageSetItem setItem) {
        PDict result = PFactory.createDict(language);
        BytecodeDSLFrameInfo info = (BytecodeDSLFrameInfo) self.getBytecodeFrame().getFrameDescriptorInfo();
        for (int i = 0; i < info.getVariableCount(); i++) {
            Object value = getSlotValue(self, i);
            if (value != null) {
                result.setDictStorage(setItem.execute(inliningTarget, result.getDictStorage(), info.getVariableName(i), value));
            }
        }
        PDict extra = self.getFrame().getExtraLocals();
        if (extra != null) {
            result.update(extra);
        }
        return result;
    }

    private enum SnapshotEntryKind {
        KEYS,
        VALUES,
        ITEMS
    }

    private static Object[] snapshotEntries(PFrameLocalsProxy self, PythonLanguage language, Node inliningTarget, HashingStorageSetItem setItem, SnapshotEntryKind kind,
                    HashingStorageGetIterator getIterator,
                    HashingStorageIteratorNext iteratorNext,
                    HashingStorageIteratorKey iteratorKey,
                    HashingStorageIteratorValue iteratorValue) {
        PDict snapshot = snapshot(self, language, inliningTarget, setItem);
        HashingStorage storage = snapshot.getDictStorage();
        HashingStorageIterator iterator = getIterator.execute(inliningTarget, storage);
        List<Object> result = new ArrayList<>();
        while (iteratorNext.execute(inliningTarget, storage, iterator)) {
            Object key = iteratorKey.execute(inliningTarget, storage, iterator);
            Object value = iteratorValue.execute(inliningTarget, storage, iterator);
            result.add(switch (kind) {
                case KEYS -> key;
                case VALUES -> value;
                case ITEMS -> PFactory.createTuple(language, new Object[]{key, value});
            });
        }
        return result.toArray();
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetItemNode extends MpSubscriptBuiltinNode {
        @Specialization
        static Object get(VirtualFrame frame, PFrameLocalsProxy self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool equals,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PRaiseNode raise) {
            Object value = lookup(frame, inliningTarget, self, key, equals, getItem);
            if (value != null) {
                return value;
            }
            throw raise.raise(inliningTarget, PythonBuiltinClassType.KeyError, new Object[]{key});
        }
    }

    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetNode extends PythonBuiltinNode {

        @Specialization
        static Object get(VirtualFrame frame, PFrameLocalsProxy self, Object key, Object defaultValue,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool equals,
                        @Cached HashingStorageGetItem getItem) {
            Object value = lookup(frame, inliningTarget, self, key, equals, getItem);
            if (value == null) {
                return defaultValue == PNone.NO_VALUE ? PNone.NONE : defaultValue;
            }
            return value;
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetItemNode extends MpAssSubscriptBuiltinNode {
        @Specialization
        static void set(VirtualFrame frame, PFrameLocalsProxy self, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectRichCompareBool equals,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageDelItem delItem,
                        @Cached PRaiseNode raise) {
            int slot = findSlot(frame, inliningTarget, self, key, equals);
            if (slot >= 0) {
                if (value == PNone.NO_VALUE) {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.CANNOT_REMOVE_LOCAL_VARIABLES_FROM_FRAME_LOCALS_PROXY);
                }
                Object current = getLocalValue(self.getBytecodeFrame(), slot);
                if (current instanceof PCell cell) {
                    cell.setRef(value);
                } else {
                    setLocalValue(self.getBytecodeFrame(), slot, value);
                }
            } else if (value == PNone.NO_VALUE) {
                PDict extra = self.getFrame().getExtraLocals();
                if (extra == null || getItem.execute(frame, inliningTarget, extra.getDictStorage(), key) == null) {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.KeyError, new Object[]{key});
                }
                delItem.execute(frame, inliningTarget, extra.getDictStorage(), key, extra);
            } else {
                PDict extra = extras(self, language);
                extra.setDictStorage(setItem.execute(frame, inliningTarget, extra.getDictStorage(), key, value));
            }
        }
    }

    @Slot(value = SlotKind.mp_length, isComplex = true)
    @GenerateNodeFactory
    abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen storageLen) {
            return count(self, inliningTarget, storageLen);
        }

        private static int count(PFrameLocalsProxy self, Node inliningTarget, HashingStorageLen storageLen) {
            int count = 0;
            BytecodeDSLFrameInfo info = (BytecodeDSLFrameInfo) self.getBytecodeFrame().getFrameDescriptorInfo();
            for (int i = 0; i < info.getVariableCount(); i++) {
                if (getSlotValue(self, i) != null) {
                    count++;
                }
            }
            PDict extra = self.getFrame().getExtraLocals();
            return count + (extra == null ? 0 : storageLen.execute(inliningTarget, extra.getDictStorage()));
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(VirtualFrame frame, PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectGetIter getIter,
                        @Cached HashingStorageSetItem setItem) {
            return getIter.execute(frame, inliningTarget, snapshot(self, language, inliningTarget, setItem));
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDict copy(PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached HashingStorageSetItem setItem) {
            return snapshot(self, language, inliningTarget, setItem);
        }
    }

    @Builtin(name = "keys", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class KeysNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PList keys(PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext iteratorNext,
                        @Cached HashingStorageIteratorKey iteratorKey,
                        @Cached HashingStorageIteratorValue iteratorValue) {
            return PFactory.createList(language, snapshotEntries(self, language, inliningTarget, setItem, SnapshotEntryKind.KEYS, getIterator, iteratorNext, iteratorKey, iteratorValue));
        }
    }

    @Builtin(name = "values", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PList values(PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext iteratorNext,
                        @Cached HashingStorageIteratorKey iteratorKey,
                        @Cached HashingStorageIteratorValue iteratorValue) {
            return PFactory.createList(language, snapshotEntries(self, language, inliningTarget, setItem, SnapshotEntryKind.VALUES, getIterator, iteratorNext, iteratorKey, iteratorValue));
        }
    }

    @Builtin(name = "items", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ItemsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PList items(PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext iteratorNext,
                        @Cached HashingStorageIteratorKey iteratorKey,
                        @Cached HashingStorageIteratorValue iteratorValue) {
            return PFactory.createList(language, snapshotEntries(self, language, inliningTarget, setItem, SnapshotEntryKind.ITEMS, getIterator, iteratorNext, iteratorKey, iteratorValue));
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    abstract static class ContainsNode extends SqContainsBuiltinNode {
        @Specialization
        static boolean contains(VirtualFrame frame, PFrameLocalsProxy self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompareBool equals,
                        @Cached HashingStorageGetItem getItem) {
            return lookup(frame, inliningTarget, self, key, equals, getItem) != null;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(PFrameLocalsProxy self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached HashingStorageSetItem setItem) {
            return repr.execute(null, inliningTarget, snapshot(self, language, inliningTarget, setItem));
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends RichCmpBuiltinNode {
        @Specialization
        static Object compare(VirtualFrame frame, PFrameLocalsProxy self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectRichCompare compare,
                        @Cached HashingStorageSetItem setItem) {
            return compare.execute(frame, inliningTarget, snapshot(self, language, inliningTarget, setItem), other, op);
        }
    }
}
