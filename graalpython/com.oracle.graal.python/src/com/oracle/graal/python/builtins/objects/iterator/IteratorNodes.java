/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIterator;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LENGTH_HINT__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodesFactory.GetInternalIteratorSequenceStorageNodeGen;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.util.CastBuiltinStringToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class IteratorNodes {

    /**
     * Implements the logic from {@code PyObject_LengthHint}. This returns a non-negative value from
     * o.__len__() or o.__length_hint__(). If those methods aren't found the defaultvalue is
     * returned. If either has no viable or defaultvalue implementation then this node returns -1.
     */
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class, SpecialMethodNames.class})
    public abstract static class GetLength extends PNodeWithContext {

        public abstract int execute(VirtualFrame frame, Node inliningTarget, Object iterable);

        // Note: these fast-paths are duplicated in PyObjectSizeNode, because there is no simple
        // way to share them effectively without unnecessary indirections and overhead in the
        // interpreter

        @Specialization
        static int doTruffleString(TruffleString str,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode) {
            return codePointLengthNode.execute(str, TS_ENCODING);
        }

        @Specialization(guards = "isBuiltinList(object)")
        static int doList(PList object) {
            return object.getSequenceStorage().length();
        }

        @Specialization(guards = "isBuiltinTuple(object)")
        static int doTuple(PTuple object) {
            return object.getSequenceStorage().length();
        }

        @Specialization(guards = "isBuiltinDict(object)")
        static int doDict(Node inliningTarget, PDict object,
                        @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, object.getDictStorage());
        }

        @Specialization(guards = "isBuiltinSet(object)")
        static int doSet(Node inliningTarget, PSet object,
                        @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, object.getDictStorage());
        }

        @Specialization(guards = "isBuiltinPString(object)")
        static int doPString(PString object,
                        @Cached(inline = false) StringNodes.StringLenNode lenNode) {
            return lenNode.execute(object);
        }

        @Specialization(guards = "isBuiltinBytesLike(object)")
        static int doPBytes(PBytesLike object) {
            return object.getSequenceStorage().length();
        }

        @Specialization(guards = "isNoValue(iterable)")
        static int length(@SuppressWarnings("unused") PNone iterable) {
            return -1;
        }

        @Fallback
        @InliningCutoff
        static int length(VirtualFrame frame, Node inliningTarget, Object iterable,
                        @Cached GetClassNode getClassNode,
                        @Cached GetCachedTpSlotsNode getSlotsNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached LookupSpecialMethodNode.Dynamic lenHintNode,
                        @Cached CallSlotLenNode callSlotLenNode,
                        @Cached(inline = false) CallUnaryMethodNode dispatchLenOrLenHint,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached InlinedConditionProfile hasLenProfile,
                        @Cached InlinedConditionProfile hasLengthHintProfile,
                        @Cached PRaiseNode raiseNode) {
            Object clazz = getClassNode.execute(inliningTarget, iterable);
            TpSlots slots = getSlotsNode.execute(inliningTarget, clazz);
            if (hasLenProfile.profile(inliningTarget, slots.combined_sq_mp_length() != null)) {
                try {
                    return callSlotLenNode.execute(frame, inliningTarget, slots.combined_sq_mp_length(), iterable);
                } catch (PException e) {
                    // This is done (maybe aside other things) because of ProxyType.__len__ calls
                    // len builtin, which may raise this for objects that do not have len slot. This
                    // can be maybe avoided by intrinsifying ProxyType
                    e.expect(inliningTarget, TypeError, errorProfile);
                }
            }
            Object attrLenHintObj = lenHintNode.execute(frame, inliningTarget, clazz, T___LENGTH_HINT__, iterable);
            if (hasLengthHintProfile.profile(inliningTarget, attrLenHintObj != PNone.NO_VALUE)) {
                Object len = null;
                try {
                    len = dispatchLenOrLenHint.executeObject(frame, attrLenHintObj, iterable);
                } catch (PException e) {
                    e.expect(inliningTarget, TypeError, errorProfile);
                }
                if (len != null && len != PNotImplemented.NOT_IMPLEMENTED) {
                    if (indexCheckNode.execute(inliningTarget, len)) {
                        int intLen = asSizeNode.executeExact(frame, inliningTarget, len);
                        if (intLen < 0) {
                            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.LENGTH_HINT_SHOULD_RETURN_MT_ZERO);
                        }
                        return intLen;
                    } else {
                        throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_INTEGER_NOT_P, T___LENGTH_HINT__, len);
                    }
                }
            }
            return -1;
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetInternalIteratorSequenceStorage extends Node {
        public static SequenceStorage executeUncached(PBuiltinIterator iterator) {
            return GetInternalIteratorSequenceStorageNodeGen.getUncached().execute(null, iterator);
        }

        /**
         * The argument must be a builtin iterator, which points to the first element of the
         * internal sequence storage. Returns {@code null} if the sequence storage is not available
         * or if the iterator is not pointing to the first item in the storage.
         */
        public final SequenceStorage execute(Node inliningTarget, PBuiltinIterator iterator) {
            assert GetPythonObjectClassNode.executeUncached(iterator) == PIterator;
            assert iterator.index == 0 && !iterator.isExhausted();
            return executeInternal(inliningTarget, iterator);
        }

        protected abstract SequenceStorage executeInternal(Node inliningTarget, PBuiltinIterator iterator);

        @Specialization(guards = "isList(it.sequence)")
        static SequenceStorage doSequenceList(PSequenceIterator it) {
            return ((PList) it.sequence).getSequenceStorage();
        }

        @Specialization
        static SequenceStorage doSequenceLong(PLongSequenceIterator it) {
            return it.sequence;
        }

        @Specialization
        static SequenceStorage doSequenceDouble(PDoubleSequenceIterator it) {
            return it.sequence;
        }

        @Specialization
        static SequenceStorage doSequenceObj(PObjectSequenceIterator it) {
            return it.sequence;
        }

        @Specialization
        static SequenceStorage doSequenceIntSeq(PIntegerSequenceIterator it) {
            return it.sequence;
        }

        @Specialization(guards = "isPTuple(it.sequence)")
        static SequenceStorage doSequenceTuple(PSequenceIterator it) {
            return ((PTuple) it.sequence).getSequenceStorage();
        }

        @Fallback
        static SequenceStorage doOthers(@SuppressWarnings("unused") PBuiltinIterator it) {
            return null;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    public abstract static class BuiltinIteratorLengthHint extends Node {
        /**
         * The argument must be a builtin iterator. Returns {@code -1} if the length hint is not
         * available and rewrites itself to generic fallback that always returns {@code -1}.
         */
        public final int execute(Node inliningTarget, PBuiltinIterator iterator) {
            assert GetPythonObjectClassNode.executeUncached(iterator) == PIterator;
            return executeInternal(inliningTarget, iterator);
        }

        protected abstract int executeInternal(Node inliningTarget, PBuiltinIterator iterator);

        protected static SequenceStorage getStorage(Node inliningTarget, GetInternalIteratorSequenceStorage getSeqStorage, PBuiltinIterator it) {
            return it.index != 0 || it.isExhausted() ? null : getSeqStorage.execute(inliningTarget, it);
        }

        @Specialization(guards = "storage != null", limit = "3")
        static int doSeqStorage(@SuppressWarnings("unused") Node inliningTarget, @SuppressWarnings("unused") PBuiltinIterator it,
                        @SuppressWarnings("unused") @Cached GetInternalIteratorSequenceStorage getSeqStorage,
                        @Bind("getStorage(inliningTarget, getSeqStorage, it)") SequenceStorage storage) {
            return ensurePositive(storage.length());
        }

        @Specialization
        static int doString(PStringIterator it,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode) {
            return ensurePositive(codePointLengthNode.execute(it.value, TS_ENCODING));
        }

        @Specialization
        static int doSequenceArr(PArrayIterator it) {
            return ensurePositive(it.array.getLength());
        }

        @Specialization
        static int doSequenceIntRange(PIntRangeIterator it) {
            return ensurePositive(it.getRemainingLength());
        }

        @Specialization(replaces = {"doSeqStorage", "doString", "doSequenceArr", "doSequenceIntRange"})
        static int doGeneric(@SuppressWarnings("unused") PBuiltinIterator it) {
            return -1;
        }

        static int ensurePositive(int len) {
            if (len < 0) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return len;
        }
    }

    @ImportStatic(PGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 18
    public abstract static class ToArrayNode extends Node {
        public abstract Object[] execute(VirtualFrame frame, Object iterable);

        @Specialization(guards = "isString(iterableObj)")
        public static Object[] doIt(Object iterableObj,
                        @Bind Node inliningTarget,
                        @Cached CastBuiltinStringToTruffleStringNode castToStringNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            TruffleString iterable = castToStringNode.execute(inliningTarget, iterableObj);
            Object[] result = new Object[codePointLengthNode.execute(iterable, TS_ENCODING)];
            loopProfile.profileCounted(inliningTarget, result.length);
            TruffleStringIterator it = createCodePointIteratorNode.execute(iterable, TS_ENCODING);
            int i = 0;
            while (loopProfile.inject(inliningTarget, it.hasNext())) {
                // TODO: GR-37219: use SubstringNode with lazy=true?
                result[i++] = fromCodePointNode.execute(nextNode.execute(it), TS_ENCODING, true);
            }
            return result;
        }

        @Specialization
        public static Object[] doIt(PSequence iterable,
                        @Bind Node inliningTarget,
                        @Cached GetSequenceStorageNode getStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
            SequenceStorage storage = getStorageNode.execute(inliningTarget, iterable);
            return toArrayNode.execute(inliningTarget, storage);
        }

        @Fallback
        public static Object[] doIt(VirtualFrame frame, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode) {
            Object it = getIter.execute(frame, inliningTarget, iterable);
            List<Object> result = createlist();
            while (true) {
                try {
                    Object next = nextNode.execute(frame, inliningTarget, it);
                    result.add(next);
                } catch (IteratorExhausted e) {
                    break;
                }
            }
            return result.toArray(new Object[result.size()]);
        }

        @TruffleBoundary
        private static List<Object> createlist() {
            return new ArrayList<>();
        }
    }
}
