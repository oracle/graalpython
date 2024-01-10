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
import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENTS_MUST_BE_ITERATORS;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.LenNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PChain})
public final class ChainBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ChainBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PChain self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object next(VirtualFrame frame, PChain self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached IsBuiltinObjectProfile isStopIterationProfile,
                        @Cached InlinedBranchProfile nextExceptioProfile,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            while (loopProfile.profile(inliningTarget, self.getSource() != PNone.NONE)) {
                if (self.getActive() == PNone.NONE) {
                    try {
                        Object next = nextNode.execute(frame, self.getSource(), PNone.NO_VALUE);
                        Object iter = getIter.execute(frame, inliningTarget, next);
                        self.setActive(iter);
                    } catch (PException e) {
                        nextExceptioProfile.enter(inliningTarget);
                        self.setSource(PNone.NONE);
                        throw e;
                    }
                }
                try {
                    return nextNode.execute(frame, self.getActive(), PNone.NO_VALUE);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, isStopIterationProfile);
                    self.setActive(PNone.NONE);
                }
            }
            throw raiseNode.get(inliningTarget).raiseStopIteration();
        }
    }

    @Builtin(name = "from_iterable", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromIterNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object fromIter(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PythonObjectFactory factory) {
            PChain instance = factory.createChain(PythonBuiltinClassType.PChain);
            instance.setSource(getIter.execute(frame, inliningTarget, arg));
            instance.setActive(PNone.NONE);
            return instance;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reducePos(PChain self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached InlinedConditionProfile hasSourceProfile,
                        @Cached InlinedConditionProfile hasActiveProfile,
                        @Cached PythonObjectFactory factory) {
            Object type = getClass.execute(inliningTarget, self);
            PTuple empty = factory.createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            if (hasSourceProfile.profile(inliningTarget, self.getSource() != PNone.NONE)) {
                if (hasActiveProfile.profile(inliningTarget, self.getActive() != PNone.NONE)) {
                    PTuple tuple = factory.createTuple(new Object[]{self.getSource(), self.getActive()});
                    return factory.createTuple(new Object[]{type, empty, tuple});
                } else {
                    PTuple tuple = factory.createTuple(new Object[]{self.getSource()});
                    return factory.createTuple(new Object[]{type, empty, tuple});
                }
            } else {
                return factory.createTuple(new Object[]{type, empty});
            }
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(VirtualFrame frame, PChain self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached LenNode lenNode,
                        @Cached GetItemNode getItemNode,
                        @Cached InlinedBranchProfile len2Profile,
                        @Cached PyIterCheckNode iterCheckNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!(state instanceof PTuple)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            int len = (int) lenNode.execute(frame, state);
            if (len < 1 || len > 2) {
                throw raiseNode.get(inliningTarget).raise(TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            Object source = getItemNode.execute(frame, state, 0);
            checkIterator(inliningTarget, iterCheckNode, source, raiseNode);
            self.setSource(source);
            if (len == 2) {
                len2Profile.enter(inliningTarget);
                Object active = getItemNode.execute(frame, state, 1);
                checkIterator(inliningTarget, iterCheckNode, active, raiseNode);
                self.setActive(active);
            }
            return PNone.NONE;
        }

        private static void checkIterator(Node inliningTarget, PyIterCheckNode iterCheckNode, Object obj, PRaiseNode.Lazy raiseNode) throws PException {
            if (!iterCheckNode.execute(inliningTarget, obj)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ARGUMENTS_MUST_BE_ITERATORS);
            }
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
