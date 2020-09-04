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
package com.oracle.graal.python.nodes.function.builtins;

import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class BuiltinCallNode extends Node {
    public abstract Object execute(VirtualFrame frame);

    protected abstract PythonBuiltinBaseNode getNode();

    public static final class BuiltinAnyCallNode extends BuiltinCallNode {
        @Child PythonBuiltinNode node;

        public BuiltinAnyCallNode(PythonBuiltinNode node) {
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame);
        }

        @Override
        protected PythonBuiltinBaseNode getNode() {
            return node;
        }
    }

    public static final class BuiltinUnaryCallNode extends BuiltinCallNode {
        @Child PythonUnaryBuiltinNode node;
        @Child ReadArgumentNode arg;

        public BuiltinUnaryCallNode(PythonUnaryBuiltinNode node, ReadArgumentNode argument) {
            this.node = node;
            this.arg = argument;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.call(frame, arg.execute(frame));
        }

        @Override
        protected PythonBuiltinBaseNode getNode() {
            return node;
        }
    }

    public static final class BuiltinBinaryCallNode extends BuiltinCallNode {
        @Child PythonBinaryBuiltinNode node;
        @Child ReadArgumentNode arg1;
        @Child ReadArgumentNode arg2;

        public BuiltinBinaryCallNode(PythonBinaryBuiltinNode node, ReadArgumentNode arg1, ReadArgumentNode arg2) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.call(frame, arg1.execute(frame), arg2.execute(frame));
        }

        @Override
        protected PythonBuiltinBaseNode getNode() {
            return node;
        }
    }

    public static final class BuiltinTernaryCallNode extends BuiltinCallNode {
        @Child PythonTernaryBuiltinNode node;
        @Child ReadArgumentNode arg1;
        @Child ReadArgumentNode arg2;
        @Child ReadArgumentNode arg3;

        public BuiltinTernaryCallNode(PythonTernaryBuiltinNode node, ReadArgumentNode arg1, ReadArgumentNode arg2, ReadArgumentNode arg3) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.call(frame, arg1.execute(frame), arg2.execute(frame), arg3.execute(frame));
        }

        @Override
        protected PythonBuiltinBaseNode getNode() {
            return node;
        }
    }

    public static final class BuiltinQuaternaryCallNode extends BuiltinCallNode {
        @Child PythonQuaternaryBuiltinNode node;
        @Child ReadArgumentNode arg1;
        @Child ReadArgumentNode arg2;
        @Child ReadArgumentNode arg3;
        @Child ReadArgumentNode arg4;

        public BuiltinQuaternaryCallNode(PythonQuaternaryBuiltinNode node, ReadArgumentNode arg1, ReadArgumentNode arg2, ReadArgumentNode arg3, ReadArgumentNode arg4) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
            this.arg4 = arg4;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.call(frame, arg1.execute(frame), arg2.execute(frame), arg3.execute(frame), arg4.execute(frame));
        }

        @Override
        protected PythonBuiltinBaseNode getNode() {
            return node;
        }
    }

    public static final class BuiltinVarArgsCallNode extends BuiltinCallNode {
        @Child PythonVarargsBuiltinNode node;
        @Child ReadArgumentNode arg1;
        @Child ReadArgumentNode arg2;
        @Child ReadArgumentNode arg3;

        public BuiltinVarArgsCallNode(PythonVarargsBuiltinNode node, ReadArgumentNode arg1, ReadArgumentNode arg2, ReadArgumentNode arg3) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame, arg1.execute(frame), (Object[]) arg2.execute(frame), (PKeyword[]) arg3.execute(frame));
        }

        @Override
        protected PythonBuiltinBaseNode getNode() {
            return node;
        }
    }
}
