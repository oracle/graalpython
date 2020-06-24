/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.reversed;

import static com.oracle.graal.python.nodes.BuiltinNames.REVERSED;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_HAS_NO_LEN;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PReverseIterator)
public class ReversedBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReversedBuiltinsFactory.getFactories();
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object next(VirtualFrame frame, PSequenceReverseIterator self,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItem,
                        @Cached("create()") IsBuiltinClassProfile profile) {
            if (self.index < 0) {
                throw raise(StopIteration);
            }
            try {
                return callGetItem.executeObject(frame, self.getObject(), self.index--);
            } catch (PException e) {
                e.expectIndexError(profile);
                throw raise(StopIteration);
            }
        }

        @Specialization
        Object next(PStringReverseIterator self) {
            if (self.index >= 0) {
                return Character.toString(self.value.charAt(self.index--));
            }
            throw raise(StopIteration);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object __iter__(PythonBuiltinObject self) {
            return self;
        }
    }

    @Builtin(name = __LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int lengthHint(PStringReverseIterator self) {
            return self.index + 1;
        }

        @Specialization(guards = "self.isPSequence()")
        public Object lengthHint(PSequenceReverseIterator self,
                                 @Cached SequenceNodes.LenNode lenNode) {
            if (lenNode.execute(self.getPSequence()) == -1) {
                throw raise(TypeError, OBJ_HAS_NO_LEN, self);
            }
            return self.index + 1;
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object lengthHint(VirtualFrame frame, PSequenceReverseIterator self,
                                 @Cached("create(__LEN__)") LookupAndCallUnaryNode callLen,
                                 @Cached("create(__ADD__, __RADD__)") LookupAndCallBinaryNode callAdd) {
            Object len = callLen.executeObject(frame, self.getObject());
            if (len == PNone.NO_VALUE || len == PNone.NONE) {
                throw raise(TypeError, OBJ_HAS_NO_LEN, self);
            }
            return callAdd.executeObject(frame, self.index, 1);
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object reduce(PStringReverseIterator self,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(self, self.value, self.index, pol);
        }

        @Specialization(guards = "self.isPSequence()")
        public Object reduce(PSequenceReverseIterator self,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            return reduceInternal(self, self.getPSequence(), self.index, pol);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object reduce(VirtualFrame frame, PSequenceReverseIterator self,
                        @Cached("create(__REDUCE__)") LookupAndCallUnaryNode callUnaryNode,
                        @Cached.Shared("pol") @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            Object reduce = pol.lookupAttribute(self.getPSequence(), __REDUCE__);
            Object content = callUnaryNode.executeObject(frame, reduce);
            return reduceInternal(self, content, self.index, pol);
        }

        private PTuple reduceInternal(Object self, Object arg, Object state, PythonObjectLibrary pol) {
            Object revIter = pol.getLazyPythonClass(self);
            PTuple args = factory().createTuple(new Object[]{arg});
            // callable, args, state (optional)
            if (state != null) {
                return factory().createTuple(new Object[]{revIter, args, state});
            } else {
                return factory().createTuple(new Object[]{revIter, args});
            }
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public Object reduce(PBuiltinIterator self, Object index,
                        @CachedLibrary(value = "index") PythonObjectLibrary pol) {
            int idx = pol.asSize(index);
            if (idx < 0) {
                idx = 0;
            }
            self.index = idx;
            return PNone.NONE;
        }
    }
}
