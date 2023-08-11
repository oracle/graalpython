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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFilterfalse})
public final class FilterfalseBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FilterfalseBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PFilterfalse self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "hasFunc(self)")
        static Object next(VirtualFrame frame, PFilterfalse self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached CallNode callNode,
                        @Shared @Cached PyObjectIsTrueNode isTrue,
                        @Shared @Cached InlinedLoopConditionProfile loopConditionProfile) {
            Object n;
            do {
                n = nextNode.execute(frame, self.getSequence(), PNone.NO_VALUE);
            } while (loopConditionProfile.profile(inliningTarget, isTrue.execute(frame, inliningTarget, callNode.execute(self.getFunc(), n))));
            return n;
        }

        @Specialization(guards = "!hasFunc(self)")
        static Object nextNoFunc(VirtualFrame frame, PFilterfalse self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached BuiltinFunctions.NextNode nextNode,
                        @Shared @Cached PyObjectIsTrueNode isTrue,
                        @Shared @Cached InlinedLoopConditionProfile loopConditionProfile) {
            Object n;
            do {
                n = nextNode.execute(frame, self.getSequence(), PNone.NO_VALUE);
            } while (loopConditionProfile.profile(inliningTarget, isTrue.execute(frame, inliningTarget, n)));
            return n;
        }

        protected boolean hasFunc(PFilterfalse self) {
            return self.getFunc() != null;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "hasFunc(self)")
        Object reduce(PFilterfalse self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared GetClassNode getClassNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple = factory().createTuple(new Object[]{self.getFunc(), self.getSequence()});
            return factory().createTuple(new Object[]{type, tuple});
        }

        @Specialization(guards = "!hasFunc(self)")
        Object reduceNoFunc(PFilterfalse self,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared GetClassNode getClassNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple = factory().createTuple(new Object[]{PNone.NONE, self.getSequence()});
            return factory().createTuple(new Object[]{type, tuple});
        }

        protected boolean hasFunc(PFilterfalse self) {
            return self.getFunc() != null;
        }
    }

}
