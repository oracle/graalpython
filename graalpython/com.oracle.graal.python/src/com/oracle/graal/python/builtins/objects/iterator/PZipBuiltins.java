/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.iterator;

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
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PZip)
public final class PZipBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PZipBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isEmpty(self.getIterators())")
        Object doEmpty(@SuppressWarnings("unused") PZip self) {
            throw raiseStopIteration();
        }

        @Specialization(guards = {"!isEmpty(self.getIterators())", "!self.isStrict()"})
        Object doNext(VirtualFrame frame, PZip self,
                        @Shared @Cached GetNextNode next) {
            Object[] iterators = self.getIterators();
            Object[] tupleElements = new Object[iterators.length];
            for (int i = 0; i < iterators.length; i++) {
                tupleElements[i] = next.execute(frame, iterators[i]);
            }
            return factory().createTuple(tupleElements);
        }

        @Specialization(guards = {"!isEmpty(self.getIterators())", "self.isStrict()"})
        @SuppressWarnings("truffle-static-method")
        Object doNext(VirtualFrame frame, PZip self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetNextNode next,
                        @Cached IsBuiltinObjectProfile classProfile) {
            Object[] iterators = self.getIterators();
            Object[] tupleElements = new Object[iterators.length];
            int i = 0;
            try {
                for (; i < iterators.length; i++) {
                    tupleElements[i] = next.execute(frame, iterators[i]);
                }
                return factory().createTuple(tupleElements);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, classProfile);
                if (i > 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.ZIP_ARG_D_IS_SHORTER_THEN_ARG_SD, i + 1, i == 1 ? " " : "s 1-", i);
                }
                for (i = 1; i < iterators.length; i++) {
                    try {
                        next.execute(frame, iterators[i]);
                        throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.ZIP_ARG_D_IS_LONGER_THEN_ARG_SD, i + 1, i == 1 ? " " : "s 1-", i);
                    } catch (PException e2) {
                        e2.expectStopIteration(inliningTarget, classProfile);
                    }
                }
                throw e;
            }
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doPZip(PZip self) {
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isStrict()")
        Object reduce(PZip self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClass) {
            Object type = getClass.execute(inliningTarget, self);
            PTuple tuple = factory().createTuple(self.getIterators());
            return factory().createTuple(new Object[]{type, tuple});
        }

        @Specialization(guards = "self.isStrict()")
        Object reduceStrict(PZip self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClass) {
            Object type = getClass.execute(inliningTarget, self);
            PTuple tuple = factory().createTuple(self.getIterators());
            return factory().createTuple(new Object[]{type, tuple, true});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doit(VirtualFrame frame, PZip self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setStrict(isTrueNode.execute(frame, inliningTarget, state));
            return PNone.NONE;
        }
    }
}
