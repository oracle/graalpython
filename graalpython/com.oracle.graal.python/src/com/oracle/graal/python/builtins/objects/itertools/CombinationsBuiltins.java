/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
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
        static Object nextStopped(PAbstractCombinations self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raiseStopIteration();
        }

        @Specialization(guards = {"!self.isStopped()", "isLastResultNull(self)"})
        static Object nextNoResult(PAbstractCombinations self,
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
        static Object next(PCombinations self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached InlinedLoopConditionProfile indexLoopProfile,
                        @Shared @Cached InlinedLoopConditionProfile resultLoopProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return nextInternal(inliningTarget, self, factory, indexLoopProfile, resultLoopProfile, raiseNode);
        }

        @Specialization(guards = {"!self.isStopped()", "!isLastResultNull(self)"})
        static Object next(PCombinationsWithReplacement self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached InlinedLoopConditionProfile indexLoopProfile,
                        @Shared @Cached InlinedLoopConditionProfile resultLoopProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return nextInternal(inliningTarget, self, factory, indexLoopProfile, resultLoopProfile, raiseNode);
        }

        private static Object nextInternal(Node inliningTarget, PAbstractCombinations self, PythonObjectFactory factory, InlinedLoopConditionProfile indexLoopProfile,
                        InlinedLoopConditionProfile resultLoopProfile, PRaiseNode.Lazy raiseNode) throws PException {

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
                throw raiseNode.get(inliningTarget).raiseStopIteration();
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
        static Object reduce(PAbstractCombinations self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasNoLastResultProfile,
                        @Cached InlinedConditionProfile stoppedProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            Object type = getClassNode.execute(inliningTarget, self);
            if (hasNoLastResultProfile.profile(inliningTarget, self.getLastResult() == null)) {
                PTuple args = factory.createTuple(new Object[]{factory.createTuple(self.getPool()), self.getR()});
                return factory.createTuple(new Object[]{type, args});
            } else if (stoppedProfile.profile(inliningTarget, self.isStopped())) {
                PTuple args = factory.createTuple(new Object[]{factory.createEmptyTuple(), self.getR()});
                return factory.createTuple(new Object[]{type, args});
            }
            PTuple indices = factory.createTuple(PythonUtils.arrayCopyOf(self.getIndices(), self.getR()));
            PTuple args = factory.createTuple(new Object[]{factory.createTuple(self.getPool()), self.getR()});
            return factory.createTuple(new Object[]{type, args, indices});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object setState(PAbstractCombinations self, Object stateObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaIntExactNode cast,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int n = self.getPool().length;
            if (stateObj instanceof PTuple state && state.getSequenceStorage().length() == self.getR()) {
                SequenceStorage storage = state.getSequenceStorage();
                try {
                    for (int i = 0; i < self.getR(); i++) {
                        int index = cast.execute(inliningTarget, getItemNode.execute(inliningTarget, storage, i));
                        int max = i + n - self.getR();
                        if (index > max) {
                            index = max;
                        }
                        if (index < 0) {
                            index = 0;
                        }
                        self.getIndices()[i] = index;
                    }
                } catch (CannotCastException e) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                Object[] result = new Object[self.getR()];
                for (int i = 0; i < self.getR(); i++) {
                    result[i] = self.getPool()[self.getIndices()[i]];
                }
                self.setLastResult(result);
                return PNone.NONE;
            } else {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.INVALID_ARGS, T___SETSTATE__);
            }
        }
    }

}
