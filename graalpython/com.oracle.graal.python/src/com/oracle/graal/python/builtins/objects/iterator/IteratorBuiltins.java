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
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.iterator.PRangeIterator.PRangeReverseIterator;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PIterator, PythonBuiltinClassType.PArrayIterator})
public class IteratorBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object next(VirtualFrame frame, PArrayIterator self,
                        @Cached("createClassProfile()") ValueProfile itemTypeProfile,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            SequenceStorage sequenceStorage = self.array.getSequenceStorage();
            if (self.index < lenNode.execute(sequenceStorage)) {
                // TODO avoid boxing by getting the array's typecode and using primitive return
                // types
                return itemTypeProfile.profile(getItemNode.execute(frame, sequenceStorage, self.index++));
            }
            throw raise(StopIteration);
        }

        @Specialization
        int next(PIntegerSequenceIterator self) {
            if (!self.isExhausted() && self.index < self.sequence.length()) {
                return self.sequence.getIntItemNormalized(self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization
        int next(PRangeIterator self) {
            if (self.index < self.stop) {
                int value = self.index;
                self.index += self.step;
                return value;
            }
            throw raise(StopIteration);
        }

        @Specialization
        int next(PRangeReverseIterator self) {
            if (self.index > self.stop) {
                int value = self.index;
                self.index -= self.step;
                return value;
            }
            throw raise(StopIteration);
        }

        @Specialization
        double next(PDoubleSequenceIterator self) {
            if (!self.isExhausted() && self.index < self.sequence.length()) {
                return self.sequence.getDoubleItemNormalized(self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization
        long next(PLongSequenceIterator self) {
            if (!self.isExhausted() && self.index < self.sequence.length()) {
                return self.sequence.getLongItemNormalized(self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization
        public Object next(PBaseSetIterator self) {
            HashingStorageIterator<Object> iterator = self.getIterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
            throw raise(StopIteration);
        }

        @Specialization(guards = {"self.isPSequence()"})
        public Object next(VirtualFrame frame, PSequenceIterator self,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage s = getStorage.execute(self.getPSequence());
            if (!self.isExhausted() && self.index < lenNode.execute(s)) {
                return getItemNode.execute(frame, s, self.index++);
            }
            self.setExhausted();
            throw raise(StopIteration);
        }

        @Specialization
        public Object next(PStringIterator self) {
            if (self.index < self.value.length()) {
                return Character.toString(self.value.charAt(self.index++));
            }
            throw raise(StopIteration);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object next(VirtualFrame frame, PSequenceIterator self,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetItem,
                        @Cached("create()") IsBuiltinClassProfile profile) {
            try {
                return callGetItem.executeObject(frame, self.getObject(), self.index++);
            } catch (PException e) {
                e.expectIndexError(profile);
                throw raise(StopIteration);
            }
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
        public int lengthHint(PArrayIterator self) {
            return self.array.len() - self.index;
        }

        @Specialization
        public int lengthHint(PIntegerSequenceIterator self) {
            return self.sequence.length() - self.index;
        }

        @Specialization
        public int lengthHint(PRangeIterator self) {
            return self.getStop() - self.getStart();
        }

        @Specialization
        public int lengthHint(PRangeReverseIterator self) {
            return self.getStart() - self.getStop();
        }

        @Specialization
        public double lengthHint(PDoubleSequenceIterator self) {
            return self.sequence.length() - self.index;
        }

        @Specialization
        public long lengthHint(PLongSequenceIterator self) {
            return self.sequence.length() - self.index;
        }

        @Specialization
        public long lengthHint(PBaseSetIterator self) {
            return self.getSet().size() - self.getIndex();
        }

        @Specialization(guards = "self.isPSequence()")
        public Object lengthHint(PSequenceIterator self,
                        @Cached("create()") SequenceNodes.LenNode lenNode) {
            return lenNode.execute(self.getPSequence()) - self.index;
        }

        @Specialization
        public Object lengthHint(PStringIterator self) {
            return self.value.length() - self.index;
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object lengthHint(VirtualFrame frame, PSequenceIterator self,
                        @Cached("create(__LEN__)") LookupAndCallUnaryNode callLen,
                        @Cached("create(__SUB__, __RSUB__)") LookupAndCallBinaryNode callSub) {
            return callSub.executeObject(frame, callLen.executeObject(frame, self.getObject()), self.index);
        }
    }
}
