/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.posix;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PScandirIterator)
public class ScandirIteratorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ScandirIteratorBuiltinsFactory.getFactories();
    }

    @Builtin(name = SpecialMethodNames.__ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PScandirIterator iter(PScandirIterator self) {
            return self;
        }
    }

    @Builtin(name = SpecialMethodNames.__NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        PDirEntry next(PScandirIterator self) {
            if (self.hasNext()) {
                TruffleFile next = self.next();
                return factory().createDirEntry(next.getName(), next, self.isProduceBytes());
            } else {
                throw raise(PythonBuiltinClassType.StopIteration);
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__ENTER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EnterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PScandirIterator iter(PScandirIterator self) {
            return self;
        }
    }

    @Builtin(name = SpecialMethodNames.__EXIT__, minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class ExitNode extends PythonBuiltinNode {
        @Specialization
        PNone exit(PScandirIterator self, @SuppressWarnings("unused") Object type, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object traceback) {
            self.close();
            return PNone.NONE;
        }
    }

    @Builtin(name = "path", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PathNode extends PythonUnaryBuiltinNode {
        @Specialization
        String path(PScandirIterator self) {
            return self.getPath();
        }
    }
}
