/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.map;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMap)
public final class MapBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MapBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 3, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization(guards = "args.length == 0")
        static PNone doOne(VirtualFrame frame, PMap self, Object func, Object iterable, @SuppressWarnings("unused") Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) {
            self.setFunction(func);
            self.setIterators(new Object[]{getIter.execute(frame, inliningTarget, iterable)});
            return PNone.NONE;
        }

        @Specialization(replaces = "doOne")
        static PNone doGeneric(VirtualFrame frame, PMap self, Object func, Object iterable, Object[] args,
                        @Bind("this") Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter) {
            self.setFunction(func);
            Object[] iterators = new Object[args.length + 1];
            iterators[0] = getIter.execute(frame, inliningTarget, iterable);
            for (int i = 0; i < args.length; i++) {
                iterators[i + 1] = getIter.execute(frame, inliningTarget, args[i]);
            }
            self.setIterators(iterators);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getIterators().length == 1")
        Object doOne(VirtualFrame frame, PMap self,
                        @Shared @Cached CallVarargsMethodNode callNode,
                        @Shared @Cached GetNextNode next) {
            return callNode.execute(frame, self.getFunction(), new Object[]{next.execute(frame, self.getIterators()[0])}, PKeyword.EMPTY_KEYWORDS);
        }

        @Specialization(replaces = "doOne")
        Object doNext(VirtualFrame frame, PMap self,
                        @Shared @Cached CallVarargsMethodNode callNode,
                        @Shared @Cached GetNextNode next) {
            Object[] iterators = self.getIterators();
            Object[] arguments = new Object[iterators.length];
            for (int i = 0; i < iterators.length; i++) {
                arguments[i] = next.execute(frame, iterators[i]);
            }
            return callNode.execute(frame, self.getFunction(), arguments, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PMap iter(PMap self) {
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonBuiltinNode {
        @Specialization
        static PTuple doit(PMap self, @SuppressWarnings("unused") Object ignored,
                        @Cached PythonObjectFactory factory) {
            Object[] iterators = self.getIterators();
            Object[] args = new Object[iterators.length + 1];
            args[0] = self.getFunction();
            System.arraycopy(iterators, 0, args, 1, iterators.length);
            return factory.createTuple(new Object[]{PythonBuiltinClassType.PMap, factory.createTuple(args)});
        }
    }
}
