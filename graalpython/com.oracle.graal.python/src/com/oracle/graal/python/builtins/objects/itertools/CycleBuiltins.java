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
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_ARGUMENT_D_MUST_BE_A_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.LenNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCycle})
public final class CycleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CycleBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PCycle self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(VirtualFrame frame, PCycle self,
                        @Bind("this") Node inliningTarget,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached IsBuiltinObjectProfile isStopIterationProfile,
                        @Cached InlinedBranchProfile iterableProfile,
                        @Cached InlinedBranchProfile firstPassProfile) {
            if (self.getIterable() != null) {
                iterableProfile.enter(inliningTarget);
                try {
                    Object item = nextNode.execute(frame, self.getIterable(), PNone.NO_VALUE);
                    if (!self.isFirstpass()) {
                        firstPassProfile.enter(inliningTarget);
                        add(self.getSaved(), item);
                    }
                    return item;
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, isStopIterationProfile);
                    self.setIterable(null);
                }
            }
            if (isEmpty(self.getSaved())) {
                throw raiseStopIteration();
            }
            Object item = get(self.getSaved(), self.getIndex());
            self.setIndex(self.getIndex() + 1);
            if (self.getIndex() >= size(self.getSaved())) {
                self.setIndex(0);
            }
            return item;
        }

        @TruffleBoundary
        @SuppressWarnings("static-method")
        private boolean isEmpty(List<Object> l) {
            return l.isEmpty();
        }

        @TruffleBoundary
        @SuppressWarnings("static-method")
        private Object add(List<Object> l, Object item) {
            return l.add(item);
        }

        @TruffleBoundary
        @SuppressWarnings("static-method")
        private Object get(List<Object> l, int idx) {
            return l.get(idx);
        }

        @TruffleBoundary
        @SuppressWarnings("static-method")
        private int size(List<Object> l) {
            return l.size();
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "hasIterable(self)")
        Object reduce(PCycle self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Exclusive GetClassNode getClass) {
            Object type = getClass.execute(inliningTarget, self);
            PTuple iterableTuple = factory().createTuple(new Object[]{self.getIterable()});
            PTuple tuple = factory().createTuple(new Object[]{getSavedList(self), self.isFirstpass()});
            return factory().createTuple(new Object[]{type, iterableTuple, tuple});
        }

        @Specialization(guards = "!hasIterable(self)")
        @SuppressWarnings("truffle-static-method")
        Object reduceNoIterable(VirtualFrame frame, PCycle self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Exclusive GetClassNode getClass,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached PyObjectGetIter getIterNode,
                        @Cached InlinedBranchProfile indexProfile) {
            Object type = getClass.execute(inliningTarget, self);
            PList savedList = getSavedList(self);
            Object it = getIterNode.execute(frame, inliningTarget, savedList);
            if (self.getIndex() > 0) {
                indexProfile.enter(inliningTarget);
                Object setStateCallable = lookupAttrNode.execute(frame, inliningTarget, it, T___SETSTATE__);
                callNode.executeObject(frame, setStateCallable, self.getIndex());
            }
            PTuple iteratorTuple = factory().createTuple(new Object[]{it});
            PTuple tuple = factory().createTuple(new Object[]{savedList, true});
            return factory().createTuple(new Object[]{type, iteratorTuple, tuple});
        }

        PList getSavedList(PCycle self) {
            return factory().createList(toArray(self.getSaved()));
        }

        @TruffleBoundary
        private static Object[] toArray(List<Object> l) {
            return l.toArray(new Object[l.size()]);
        }

        protected boolean hasIterable(PCycle self) {
            return self.getIterable() != null;
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        abstract Object execute(VirtualFrame frame, PythonObject self, Object state);

        @Specialization
        Object setState(VirtualFrame frame, PCycle self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached LenNode lenNode,
                        @Cached GetItemNode getItemNode,
                        @Cached IsBuiltinObjectProfile isTypeErrorProfile,
                        @Cached ToArrayNode toArrayNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (!((state instanceof PTuple) && ((int) lenNode.execute(frame, state) == 2))) {
                throw raise(TypeError, IS_NOT_A, "state", "2-tuple");
            }
            Object obj = getItemNode.execute(frame, state, 0);
            if (!(obj instanceof PList)) {
                throw raise(TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 1, "Plist");
            }
            PList saved = (PList) obj;

            boolean firstPass;
            try {
                firstPass = asSizeNode.executeLossy(frame, inliningTarget, getItemNode.execute(frame, state, 1)) != 0;
            } catch (PException e) {
                e.expectTypeError(inliningTarget, isTypeErrorProfile);
                throw raise(TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 2, "int");
            }

            Object[] savedArray = toArrayNode.execute(inliningTarget, saved.getSequenceStorage());
            self.setSaved(toList(savedArray));
            self.setFirstpass(firstPass);
            self.setIndex(0);
            return PNone.NONE;
        }

        @TruffleBoundary
        private static ArrayList<Object> toList(Object[] savedArray) {
            return new ArrayList<>(Arrays.asList(savedArray));
        }
    }

}
