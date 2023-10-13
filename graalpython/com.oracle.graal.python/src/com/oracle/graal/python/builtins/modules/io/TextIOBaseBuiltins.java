/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ENCODING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ERRORS;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_DETACH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTextIOBase)
public final class TextIOBaseBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TextIOBaseBuiltinsFactory.getFactories();
    }

    @Builtin(name = J_DETACH, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DetachNode extends PythonBuiltinNode {
        @Specialization
        static Object detach(@SuppressWarnings("unused") Object self,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_DETACH);
        }
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends PythonBuiltinNode {
        @Specialization
        static Object read(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object args,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_READ);
        }
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends PythonBuiltinNode {
        @Specialization
        static Object read(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object args,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_READLINE);
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBuiltinNode {
        @Specialization
        static Object write(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object args,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(IOUnsupportedOperation, T_WRITE);
        }
    }

    @Builtin(name = J_ENCODING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EncodingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J_NEWLINES, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NewlinesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ERRORS, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ErrorsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doit(@SuppressWarnings("unused") Object self) {
            return PNone.NONE;
        }
    }
}
