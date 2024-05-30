/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.util.PythonUtils.tsArray;

import java.util.Arrays;
import java.util.Comparator;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.sequence.storage.BoolSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.DoubleSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.LongSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class SortNodes {
    private static class SortingPair {
        final Object key;
        final Object value;

        public SortingPair(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class ObjectComparatorRootNode extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("a", "b"), PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);

        @Child private ExecutionContext.CalleeContext calleeContext = ExecutionContext.CalleeContext.create();
        @Child private PyObjectIsTrueNode isTrueNode = PyObjectIsTrueNode.create();
        @Child private BinaryComparisonNode.LtNode ltNodeA = BinaryComparisonNode.LtNode.create();
        @Child private BinaryComparisonNode.LtNode ltNodeB = BinaryComparisonNode.LtNode.create();

        enum Result {
            LT(-1),
            EQ(0),
            GT(1);

            final int value;

            Result(int i) {
                value = i;
            }

        }

        ObjectComparatorRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                Object[] arguments = frame.getArguments();
                Object a = arguments[PArguments.USER_ARGUMENTS_OFFSET];
                Object b = arguments[PArguments.USER_ARGUMENTS_OFFSET + 1];
                if (isTrueNode.executeCached(frame, ltNodeA.executeObject(frame, a, b))) {
                    return Result.LT;
                } else if (isTrueNode.executeCached(frame, ltNodeB.executeObject(frame, b, a))) {
                    return Result.GT;
                } else {
                    return Result.EQ;
                }
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public String getName() {
            return "sort_comparator";
        }

        @Override
        public boolean setsUpCalleeContext() {
            return true;
        }
    }

    public abstract static class SortSequenceStorageNode extends PNodeWithContext {

        @CompilationFinal private RootCallTarget comparatorCallTarget;

        private final ValueProfile keyClassProfile = ValueProfile.createIdentityProfile();

        public abstract void execute(VirtualFrame frame, SequenceStorage storage, Object keyfunc, boolean reverse);

        @Specialization
        void doEmpty(@SuppressWarnings("unused") EmptySequenceStorage storage, @SuppressWarnings("unused") Object keyfunc, @SuppressWarnings("unused") boolean reverse) {
        }

        @Specialization
        @TruffleBoundary
        void sort(BoolSequenceStorage storage, @SuppressWarnings("unused") PNone keyfunc, boolean reverse) {
            int length = storage.length();
            int trueValues = 0;
            boolean[] array = storage.getInternalBoolArray();
            for (int i = 0; i < length; i++) {
                if (array[i]) {
                    trueValues++;
                }
            }
            if (!reverse) {
                Arrays.fill(array, 0, length - trueValues, false);
                Arrays.fill(array, length - trueValues, length, true);
            } else {
                Arrays.fill(array, 0, trueValues, true);
                Arrays.fill(array, trueValues, length, false);
            }
        }

        @Specialization
        @TruffleBoundary
        void sort(IntSequenceStorage storage, @SuppressWarnings("unused") PNone keyfunc, boolean reverse) {
            int[] array = storage.getInternalIntArray();
            int len = storage.length();
            Arrays.sort(array, 0, len);
            if (reverse) {
                reverseArray(array, len);
            }
        }

        @Specialization
        @TruffleBoundary
        void sort(LongSequenceStorage storage, @SuppressWarnings("unused") PNone keyfunc, boolean reverse) {
            long[] array = storage.getInternalLongArray();
            int len = storage.length();
            Arrays.sort(array, 0, len);
            if (reverse) {
                reverseArray(array, len);
            }
        }

        @Specialization
        @TruffleBoundary
        void sort(DoubleSequenceStorage storage, @SuppressWarnings("unused") PNone keyfunc, boolean reverse) {
            int len = storage.length();
            double[] array = storage.getInternalDoubleArray();
            Arrays.sort(array, 0, len);
            if (reverse) {
                reverseArray(array, len);
            }
        }

        @TruffleBoundary
        private static void sortStrings(ObjectSequenceStorage storage, boolean reverse) {
            Object[] array = storage.getInternalObjectArray();
            int len = storage.length();
            Comparator<Object> comparator;
            if (reverse) {
                comparator = (a, b) -> StringUtils.compareStringsUncached((TruffleString) b, (TruffleString) a);
            } else {
                comparator = (a, b) -> StringUtils.compareStringsUncached((TruffleString) a, (TruffleString) b);
            }
            Arrays.sort(array, 0, len, comparator);
        }

        protected boolean isStringOnly(Node inliningTarget, ObjectSequenceStorage storage, InlinedLoopConditionProfile isStringOnlyLoopProfile,
                        InlinedCountingConditionProfile isStringOnlyBreakProfile) {
            int length = storage.length();
            Object[] array = storage.getInternalObjectArray();
            for (int i = 0; isStringOnlyLoopProfile.profile(inliningTarget, i < length); i++) {
                Object value = array[i];
                if (isStringOnlyBreakProfile.profile(inliningTarget, !(value instanceof TruffleString))) {
                    LoopNode.reportLoopCount(this, i);
                    return false;
                }
            }
            LoopNode.reportLoopCount(this, length);
            return true;
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        void sortObjSeqStorage(VirtualFrame frame, ObjectSequenceStorage storage, @SuppressWarnings("unused") PNone keyfunc, boolean reverse,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile isStringOnlyProfile,
                        @Cached InlinedLoopConditionProfile isStringOnlyLoopProfile,
                        @Cached InlinedCountingConditionProfile isStringOnlyBreakProfile,
                        @Shared @Cached CallContext callContext) {
            if (isStringOnlyProfile.profile(inliningTarget, isStringOnly(inliningTarget, storage, isStringOnlyLoopProfile, isStringOnlyBreakProfile))) {
                // Sorting of strings seems to be so much faster (especially on SVM) that it is
                // worth always checking for string only sequences and not replacing the strings
                // specialized code with generic object storage code
                sortStrings(storage, reverse);
            } else {
                sortWithoutKey(frame, storage.getInternalObjectArray(), storage.length(), reverse, callContext);
            }
        }

        @Specialization(guards = "!isPNone(keyfunc)")
        void sort(VirtualFrame frame, ObjectSequenceStorage storage, Object keyfunc, boolean reverse,
                        @Shared @Cached CallNode callNode,
                        @Shared @Cached CallContext callContext) {
            sortWithKey(frame, storage.getInternalObjectArray(), storage.length(), keyfunc, reverse, callNode, callContext);
        }

        @Fallback
        @SuppressWarnings("truffle-static-method")
        void sort(VirtualFrame frame, SequenceStorage storage, Object keyfunc, boolean reverse,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CallContext callContext,
                        @Shared @Cached CallNode callNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemScalarNode) {
            int len = storage.length();
            Object[] array = new Object[len];
            for (int i = 0; i < len; i++) {
                array[i] = getItemScalarNode.execute(inliningTarget, storage, i);
            }
            if (keyfunc instanceof PNone) {
                sortWithoutKey(frame, array, len, reverse, callContext);
            } else {
                sortWithKey(frame, array, len, keyfunc, reverse, callNode, callContext);
            }
            for (int i = 0; i < len; i++) {
                setItemScalarNode.execute(inliningTarget, storage, i, array[i]);
            }
        }

        private void sortWithoutKey(VirtualFrame frame, Object[] array, int len, boolean reverse, CallContext callContext) {
            if (len <= 1) {
                return;
            }
            if (reverse) {
                reverseArray(array, len);
            }
            PythonLanguage language = PythonLanguage.get(this);
            final Object[] arguments = PArguments.create(2);
            final RootCallTarget callTarget = getComparatorCallTarget(language);
            if (frame == null) {
                PythonThreadState threadState = PythonContext.get(this).getThreadState(language);
                Object state = IndirectCalleeContext.enter(threadState, arguments, callTarget);
                try {
                    callSortWithoutKey(array, len, callTarget, arguments);
                } finally {
                    IndirectCalleeContext.exit(threadState, state);
                }
            } else {
                callContext.prepareCall(frame, arguments, callTarget, this);
                callSortWithoutKey(array, len, callTarget, arguments);
            }
            if (reverse) {
                reverseArray(array, len);
            }
        }

        @TruffleBoundary
        private static void callSortWithoutKey(Object[] array, int len, RootCallTarget callTarget, Object[] arguments) {
            try {
                Arrays.sort(array, 0, len, (a, b) -> {
                    PArguments.setArgument(arguments, 0, a);
                    PArguments.setArgument(arguments, 1, b);
                    ObjectComparatorRootNode.Result result = (ObjectComparatorRootNode.Result) callTarget.call(arguments);
                    return result.value;
                });
            } catch (IllegalArgumentException e) {
                /*
                 * This happens when the __lt__ implementation violates its contract. CPython
                 * doesn't detect it and just outputs a list that's not really sorted. We can just
                 * stop at this point, it should enough that the list stays a permutation of the
                 * original
                 */
            }
        }

        private enum KeySortComparator {
            INT(Integer.class, Comparator.comparing(a -> ((Integer) a.key))),
            LONG(Long.class, Comparator.comparing(a -> ((Long) a.key))),
            DOUBLE(Double.class, Comparator.comparing(a -> ((Double) a.key))),
            BOOLEAN(Boolean.class, Comparator.comparing(a -> ((Boolean) a.key))),
            STRING(TruffleString.class, (a, b) -> StringUtils.compareStringsUncached((TruffleString) a.key, (TruffleString) b.key));

            final Class<?> clazz;
            final Comparator<SortingPair> comparator;

            KeySortComparator(Class<?> clazz, Comparator<SortingPair> comparator) {
                this.clazz = clazz;
                this.comparator = comparator;
            }

            @ExplodeLoop
            public static KeySortComparator forClass(Class<?> clazz) {
                for (KeySortComparator c : KeySortComparator.values()) {
                    if (clazz == c.clazz) {
                        return c;
                    }
                }
                return null;
            }
        }

        private void sortWithKey(VirtualFrame frame, Object[] array, int len, Object keyfunc, boolean reverse, CallNode callNode, CallContext callContext) {
            if (len == 0) {
                return;
            }
            // some packages expect "keyfunc" to be called even for one-element lists
            Object key = callNode.execute(frame, keyfunc, array[0]);
            if (len <= 1) {
                return;
            }
            /*
             * Box the values into (key, value) pairs so that the comparator can compare they keys.
             * We want to avoid calling the key function from the comparator because CPython also
             * computes the keys only once. There is an additional optimization opportunity where we
             * could avoid boxing primitive numbers, but that would result in quite a lot of
             * duplicate code.
             */
            SortingPair[] pairArray = new SortingPair[len];
            /*
             * Look at the first key and determine which comparator we could use to compare if the
             * keys turn all to be the same primitive type
             */
            pairArray[reverse ? len - 1 : 0] = new SortingPair(key, array[0]);
            Class<?> keyClass = keyClassProfile.profile(key.getClass());
            KeySortComparator keySortComparator = KeySortComparator.forClass(keyClass);

            for (int i = 1; i < len; i++) {
                key = callNode.execute(frame, keyfunc, array[i]);
                /* Check if the keys are all of the same type */
                if (keySortComparator != null && key.getClass() != keySortComparator.clazz) {
                    keySortComparator = null;
                }
                pairArray[reverse ? len - i - 1 : i] = new SortingPair(key, array[i]);
            }
            if (keySortComparator != null) {
                callSortWithKey(pairArray, len, keySortComparator);
            } else {
                PythonLanguage language = PythonLanguage.get(this);
                final Object[] arguments = PArguments.create(2);
                final RootCallTarget callTarget = getComparatorCallTarget(language);
                if (frame == null) {
                    PythonThreadState threadState = PythonContext.get(this).getThreadState(language);
                    Object state = IndirectCalleeContext.enter(threadState, arguments, callTarget);
                    try {
                        callSortWithKey(pairArray, len, callTarget, arguments);
                    } finally {
                        IndirectCalleeContext.exit(threadState, state);
                    }
                } else {
                    callContext.prepareCall(frame, arguments, callTarget, this);
                    callSortWithKey(pairArray, len, callTarget, arguments);
                }
            }
            for (int i = 0; i < len; i++) {
                array[reverse ? len - i - 1 : i] = pairArray[i].value;
            }
        }

        @TruffleBoundary
        private static void callSortWithKey(SortingPair[] array, int len, KeySortComparator comparator) {
            Arrays.sort(array, 0, len, comparator.comparator);
        }

        @TruffleBoundary
        private static void callSortWithKey(SortingPair[] array, int len, RootCallTarget callTarget, Object[] arguments) {
            try {
                Arrays.sort(array, 0, len, (a, b) -> {
                    PArguments.setArgument(arguments, 0, a.key);
                    PArguments.setArgument(arguments, 1, b.key);
                    ObjectComparatorRootNode.Result result = (ObjectComparatorRootNode.Result) callTarget.call(arguments);
                    return result.value;
                });
            } catch (IllegalArgumentException e) {
                /*
                 * This happens when the __lt__ implementation violates its contract. CPython
                 * doesn't detect it and just outputs a list that's not really sorted. We can just
                 * stop at this point, it should enough that the list stays a permutation of the
                 * original
                 */
            }
        }

        @TruffleBoundary
        private static void reverseArray(Object[] array, int len) {
            for (int i = 0; i < len / 2; i++) {
                Object tmp = array[len - i - 1];
                array[len - i - 1] = array[i];
                array[i] = tmp;
            }
        }

        @TruffleBoundary
        private static void reverseArray(int[] array, int len) {
            for (int i = 0; i < len / 2; i++) {
                int tmp = array[len - i - 1];
                array[len - i - 1] = array[i];
                array[i] = tmp;
            }
        }

        @TruffleBoundary
        private static void reverseArray(long[] array, int len) {
            for (int i = 0; i < len / 2; i++) {
                long tmp = array[len - i - 1];
                array[len - i - 1] = array[i];
                array[i] = tmp;
            }
        }

        @TruffleBoundary
        private static void reverseArray(double[] array, int len) {
            for (int i = 0; i < len / 2; i++) {
                double tmp = array[len - i - 1];
                array[len - i - 1] = array[i];
                array[i] = tmp;
            }
        }

        private RootCallTarget getComparatorCallTarget(PythonLanguage language) {
            if (comparatorCallTarget == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /*
                 * Every sort node should get its own copy to be able to optimize sorts of different
                 * types. Don't put the call targets to the language cache.
                 */
                comparatorCallTarget = new ObjectComparatorRootNode(language).getCallTarget();
            }
            return comparatorCallTarget;
        }
    }
}
