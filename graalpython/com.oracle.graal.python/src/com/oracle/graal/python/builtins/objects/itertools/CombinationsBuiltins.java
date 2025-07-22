/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins.warnPickleDeprecated;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_NON_NEGATIVE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.itertools.CombinationsBuiltinsClinicProviders.CombinationsNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.object.PFactory;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCombinations, PythonBuiltinClassType.PCombinationsWithReplacement})
public final class CombinationsBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = CombinationsBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CombinationsBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "combinations", minNumOfPositionalArgs = 3, parameterNames = {"cls", "iterable", "r"})
    @ArgumentClinic(name = "r", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class CombinationsNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CombinationsNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object construct(VirtualFrame frame, Object cls, Object iterable, int r,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached IteratorNodes.ToArrayNode toArrayNode,
                        @Cached LoopConditionProfile indicesLoopProfile,
                        @Cached InlinedConditionProfile wrongTypeProfile,
                        @Cached InlinedConditionProfile negativeProfile,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!wrongTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, cls))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (negativeProfile.profile(inliningTarget, r < 0)) {
                throw raiseNode.raise(inliningTarget, ValueError, MUST_BE_NON_NEGATIVE, "r");
            }

            PCombinations self = PFactory.createCombinations(language, cls, getInstanceShape.execute(cls));
            self.setPool(toArrayNode.execute(frame, iterable));

            int[] indices = new int[r];
            indicesLoopProfile.profileCounted(r);
            for (int i = 0; indicesLoopProfile.inject(i < r); i++) {
                indices[i] = i;
            }
            self.setIndices(indices);
            self.setR(r);
            self.setLastResult(null);
            self.setStopped(r > self.getPool().length);

            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PAbstractCombinations self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @SuppressWarnings("unused")
        @Specialization(guards = "self.isStopped()")
        static Object nextStopped(PAbstractCombinations self,
                        @Bind Node inliningTarget) {
            throw iteratorExhausted();
        }

        @Specialization(guards = {"!self.isStopped()", "isLastResultNull(self)"})
        static Object nextNoResult(PAbstractCombinations self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached @Exclusive InlinedLoopConditionProfile loopConditionProfile) {
            // On the first pass, initialize result tuple using the indices
            Object[] result = new Object[self.getR()];
            loopConditionProfile.profileCounted(inliningTarget, self.getR());
            for (int i = 0; loopConditionProfile.inject(inliningTarget, i < self.getR()); i++) {
                int idx = self.getIndices()[i];
                result[i] = self.getPool()[idx];
            }
            self.setLastResult(result);
            return PFactory.createTuple(language, result);
        }

        @Specialization(guards = {"!self.isStopped()", "!isLastResultNull(self)"})
        static Object next(PCombinations self,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedLoopConditionProfile indexLoopProfile,
                        @Shared @Cached InlinedLoopConditionProfile resultLoopProfile) {
            return nextInternal(inliningTarget, self, indexLoopProfile, resultLoopProfile);
        }

        @Specialization(guards = {"!self.isStopped()", "!isLastResultNull(self)"})
        static Object next(PCombinationsWithReplacement self,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedLoopConditionProfile indexLoopProfile,
                        @Shared @Cached InlinedLoopConditionProfile resultLoopProfile) {
            return nextInternal(inliningTarget, self, indexLoopProfile, resultLoopProfile);
        }

        private static Object nextInternal(Node inliningTarget, PAbstractCombinations self, InlinedLoopConditionProfile indexLoopProfile,
                        InlinedLoopConditionProfile resultLoopProfile) {

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
                throw iteratorExhausted();
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
            return PFactory.createTuple(PythonLanguage.get(inliningTarget), result);
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
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile hasNoLastResultProfile,
                        @Cached InlinedConditionProfile stoppedProfile,
                        @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            Object type = getClassNode.execute(inliningTarget, self);
            if (hasNoLastResultProfile.profile(inliningTarget, self.getLastResult() == null)) {
                PTuple args = PFactory.createTuple(language, new Object[]{PFactory.createTuple(language, self.getPool()), self.getR()});
                return PFactory.createTuple(language, new Object[]{type, args});
            } else if (stoppedProfile.profile(inliningTarget, self.isStopped())) {
                PTuple args = PFactory.createTuple(language, new Object[]{PFactory.createEmptyTuple(language), self.getR()});
                return PFactory.createTuple(language, new Object[]{type, args});
            }
            PTuple indices = PFactory.createTuple(language, PythonUtils.arrayCopyOf(self.getIndices(), self.getR()));
            PTuple args = PFactory.createTuple(language, new Object[]{PFactory.createTuple(language, self.getPool()), self.getR()});
            return PFactory.createTuple(language, new Object[]{type, args, indices});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object setState(PAbstractCombinations self, Object stateObj,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaIntExactNode cast,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached PRaiseNode raiseNode) {
            warnPickleDeprecated();
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
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INTEGER_REQUIRED);
                }
                Object[] result = new Object[self.getR()];
                for (int i = 0; i < self.getR(); i++) {
                    result[i] = self.getPool()[self.getIndices()[i]];
                }
                self.setLastResult(result);
                return PNone.NONE;
            } else {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.INVALID_ARGS, T___SETSTATE__);
            }
        }
    }
}
