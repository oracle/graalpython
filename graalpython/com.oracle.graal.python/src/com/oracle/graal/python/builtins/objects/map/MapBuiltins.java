/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMap)
public final class MapBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MapBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 3, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization(guards = "args.length == 0")
        static PNone doOne(VirtualFrame frame, PMap self, Object func, Object iterable, @SuppressWarnings("unused") Object[] args,
                        @Cached GetIteratorNode getIter) {
            self.setFunction(func);
            self.setIterators(new Object[]{getIter.executeWith(frame, iterable)});
            return PNone.NONE;
        }

        @Specialization(replaces = "doOne")
        static PNone doit(VirtualFrame frame, PMap self, Object func, Object iterable, Object[] args,
                        @Cached GetIteratorNode getIter) {
            self.setFunction(func);
            Object[] iterators = new Object[args.length + 1];
            iterators[0] = getIter.executeWith(frame, iterable);
            for (int i = 0; i < args.length; i++) {
                iterators[i + 1] = getIter.executeWith(frame, args[i]);
            }
            self.setIterators(iterators);
            return PNone.NONE;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getIterators().length == 1")
        Object doOne(VirtualFrame frame, PMap self,
                        @Cached CallNode callNode,
                        @Cached GetNextNode next) {
            return callNode.execute(self.getFunction(), next.execute(frame, self.getIterators()[0]));
        }

        @Specialization(replaces = "doOne")
        Object doNext(VirtualFrame frame, PMap self,
                        @Cached CallNode callNode,
                        @Cached("create()") GetNextNode next) {
            Object[] iterators = self.getIterators();
            Object[] arguments = new Object[iterators.length];
            for (int i = 0; i < iterators.length; i++) {
                arguments[i] = next.execute(frame, iterators[i]);
            }
            return callNode.execute(self.getFunction(), arguments);
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        PMap iter(PMap self) {
            return self;
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doit(VirtualFrame frame, PMap self, Object x,
                        @Cached BranchProfile moreThanOne,
                        @Cached GetIteratorNode getIter,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached GetNextNode next,
                        @Cached IsBuiltinClassProfile profile) {
            PMap iterMap = factory().createMap(PythonBuiltinClassType.PMap);
            Object[] iterators = self.getIterators();
            Object iterator = iterators[0];
            Object[] args;
            if (iterators.length > 1) {
                moreThanOne.enter();
                args = new Object[iterators.length - 1];
                System.arraycopy(iterators, 1, args, 0, args.length);
            } else {
                args = PArguments.EMPTY_VARARGS;
            }
            InitNode.doit(frame, iterMap, self.getFunction(), iterator, args, getIter);
            ThreadState state = PArguments.getThreadState(frame);
            while (true) {
                try {
                    Object n = next.execute(frame, iterMap);
                    if (lib.equalsWithState(n, x, lib, state)) {
                        return true;
                    }
                } catch (PException e) {
                    e.expectStopIteration(profile);
                    break;
                }
            }
            return false;
        }
    }
}
