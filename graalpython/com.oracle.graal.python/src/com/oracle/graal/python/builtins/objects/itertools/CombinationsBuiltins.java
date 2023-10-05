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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCombinations, PythonBuiltinClassType.PCombinationsWithReplacement})
public final class CombinationsBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CombinationsBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PAbstractCombinations self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "self.isStopped()")
        Object nextStopped(PAbstractCombinations self) {
            throw raiseStopIteration();
        }

        @Specialization(guards = {"!self.isStopped()", "isLastResultNull(self)"})
        Object nextNoResult(PAbstractCombinations self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared PythonObjectFactory factory,
                        @Cached @Exclusive InlinedLoopConditionProfile loopConditionProfile) {
            // On the first pass, initialize result tuple using the indices
            Object[] result = new Object[self.getR()];
            loopConditionProfile.profileCounted(inliningTarget, self.getR());
            for (int i = 0; loopConditionProfile.inject(inliningTarget, i < self.getR()); i++) {
                int idx = self.getIndices()[i];
                result[i] = self.getPool()[idx];
            }
            self.setLastResult(result);
            return factory.createTuple(result);
        }

        @Specialization(guards = {"!self.isStopped()", "!isLastResultNull(self)"})
        Object next(PCombinations self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared PythonObjectFactory factory,
                        @Cached @Shared InlinedLoopConditionProfile indexLoopProfile,
                        @Cached @Shared InlinedLoopConditionProfile resultLoopProfile) {
            return nextInternal(inliningTarget, self, factory, indexLoopProfile, resultLoopProfile);
        }

        @Specialization(guards = {"!self.isStopped()", "!isLastResultNull(self)"})
        Object next(PCombinationsWithReplacement self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared PythonObjectFactory factory,
                        @Cached @Shared InlinedLoopConditionProfile indexLoopProfile,
                        @Cached @Shared InlinedLoopConditionProfile resultLoopProfile) {
            return nextInternal(inliningTarget, self, factory, indexLoopProfile, resultLoopProfile);
        }

        private Object nextInternal(Node inliningTarget, PAbstractCombinations self, PythonObjectFactory factory, InlinedLoopConditionProfile indexLoopProfile,
                        InlinedLoopConditionProfile resultLoopProfile) throws PException {

            CompilerAsserts.partialEvaluationConstant(self.getClass());

            // Copy the previous result
            Object[] result = PythonUtils.arrayCopyOf(self.getLastResult(), self.getLastResult().length);

            int poolLen = self.getPool().length;
            // Scan indices right-to-left until finding one that is not at its maximum
            int i = self.getR() - 1;
            while (i >= 0 && self.getIndices()[i] == self.getMaximum(poolLen, i)) {
                i -= 1;
            }

            // If i is negative, then the indices are all at their maximum value and we're done
            if (i < 0) {
                self.setStopped(true);
                throw raiseStopIteration();
            }

            // Increment the current index which we know is not at its maximum.
            // Then move back to the right setting each index to its lowest possible value
            self.getIndices()[i] += 1;
            indexLoopProfile.profileCounted(inliningTarget, self.getR() - i + 1);
            for (int j = i + 1; indexLoopProfile.inject(inliningTarget, j < self.getR()); j++) {
                self.getIndices()[j] = self.maxIndex(j);
            }

            // Update the result for the new indices starting with i, the leftmost index that
            // changed
            resultLoopProfile.profileCounted(inliningTarget, self.getR() - i);
            for (int j = i; resultLoopProfile.inject(inliningTarget, j < self.getR()); j++) {
                int index = self.getIndices()[j];
                Object elem = self.getPool()[index];
                result[j] = elem;
            }
            self.setLastResult(result);
            return factory.createTuple(result);
        }

        protected boolean isLastResultNull(PAbstractCombinations self) {
            return self.getLastResult() == null;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object reduce(PAbstractCombinations self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasNoLastResultProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple1 = factory.createTuple(new Object[]{factory.createList(self.getPool()), self.getR()});
            if (hasNoLastResultProfile.profile(inliningTarget, self.getLastResult() == null)) {
                return factory.createTuple(new Object[]{type, tuple1});
            }
            Object lastResult = self.getLastResult() == null ? PNone.NONE : factory.createList(self.getLastResult());
            Object[] obj = new Object[self.getIndices().length];
            for (int i = 0; i < obj.length; i++) {
                obj[i] = self.getIndices()[i];
            }
            PList indices = factory.createList(obj);
            PTuple tuple2 = factory.createTuple(new Object[]{indices, lastResult, self.isStopped()});
            return factory.createTuple(new Object[]{type, tuple1, tuple2});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object setState(VirtualFrame frame, PAbstractCombinations self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached TupleBuiltins.LenNode lenNode,
                        @Cached GetItemNode getItemNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached CastToJavaIntLossyNode catsToIntNode,
                        @Cached InlinedConditionProfile noResultProfile,
                        @Cached InlinedLoopConditionProfile indicesProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!(state instanceof PTuple)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            int len = (int) lenNode.execute(frame, state);
            if (len != 3) {
                throw raiseNode.get(inliningTarget).raise(TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }

            PList indices = (PList) getItemNode.execute(frame, state, 0);
            Object[] obj = toArrayNode.execute(inliningTarget, indices.getSequenceStorage());
            int[] intIndices = new int[obj.length];
            indicesProfile.profileCounted(inliningTarget, obj.length);
            for (int i = 0; indicesProfile.inject(inliningTarget, i < obj.length); i++) {
                intIndices[i] = catsToIntNode.execute(inliningTarget, obj[i]);
            }
            self.setIndices(intIndices);

            Object lastResult = getItemNode.execute(frame, state, 1);
            if (noResultProfile.profile(inliningTarget, lastResult instanceof PNone)) {
                self.setLastResult(null);
            } else {
                obj = toArrayNode.execute(inliningTarget, ((PList) lastResult).getSequenceStorage());
                self.setLastResult(obj);
            }
            Object stopped = getItemNode.execute(frame, state, 2);
            self.setStopped((boolean) stopped);

            return PNone.NONE;
        }
    }

}
