/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.function;

import java.util.ArrayList;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadDefaultArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public final class BuiltinFunctionRootNode extends PRootNode {

    private final Builtin builtin;
    private final NodeFactory<? extends PythonBuiltinBaseNode> factory;
    private final boolean declaresExplicitSelf;

    @Child private BuiltinCallNode body;

    private abstract static class BuiltinCallNode extends Node {
        public abstract Object execute(VirtualFrame frame);
    }

    private static final class BuiltinAnyCallNode extends BuiltinCallNode {
        @Child private PythonBuiltinNode node;

        public BuiltinAnyCallNode(PythonBuiltinNode node) {
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(frame);
        }
    }

    private static final class BuiltinUnaryCallNode extends BuiltinCallNode {
        @Child private PythonUnaryBuiltinNode node;
        @Child private PNode arg;

        public BuiltinUnaryCallNode(PythonUnaryBuiltinNode node, PNode arg) {
            this.node = node;
            this.arg = arg;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(arg.execute(frame));
        }
    }

    private static final class BuiltinBinaryCallNode extends BuiltinCallNode {
        @Child private PythonBinaryBuiltinNode node;
        @Child private PNode arg1;
        @Child private PNode arg2;

        public BuiltinBinaryCallNode(PythonBinaryBuiltinNode node, PNode arg1, PNode arg2) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(arg1.execute(frame), arg2.execute(frame));
        }
    }

    private static final class BuiltinTernaryCallNode extends BuiltinCallNode {
        @Child private PythonTernaryBuiltinNode node;
        @Child private PNode arg1;
        @Child private PNode arg2;
        @Child private PNode arg3;

        public BuiltinTernaryCallNode(PythonTernaryBuiltinNode node, PNode arg1, PNode arg2, PNode arg3) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(arg1.execute(frame), arg2.execute(frame), arg3.execute(frame));
        }
    }

    private static final class BuiltinVarArgsCallNode extends BuiltinCallNode {
        @Child private PythonVarargsBuiltinNode node;
        @Child private PNode arg1;
        @Child private PNode arg2;
        @Child private PNode arg3;

        public BuiltinVarArgsCallNode(PythonVarargsBuiltinNode node, PNode arg1, PNode arg2, PNode arg3) {
            this.node = node;
            this.arg1 = arg1;
            this.arg2 = arg2;
            this.arg3 = arg3;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(arg1.execute(frame), (Object[]) arg2.execute(frame), (PKeyword[]) arg3.execute(frame));
        }
    }

    public BuiltinFunctionRootNode(PythonLanguage language, Builtin builtin, NodeFactory<? extends PythonBuiltinBaseNode> factory, boolean declaresExplicitSelf) {
        super(language, null);
        this.builtin = builtin;
        this.factory = factory;
        this.declaresExplicitSelf = declaresExplicitSelf;
    }

    private static PNode[] createArgumentsList(Builtin builtin, boolean needsExplicitSelf) {
        ArrayList<PNode> args = new ArrayList<>();
        int numOfPositionalArgs = Math.max(builtin.minNumOfArguments(), builtin.maxNumOfArguments());

        if (builtin.keywordArguments().length > 0 && builtin.maxNumOfArguments() > builtin.minNumOfArguments()) {
            // (tfel): This is actually a specification error, if there are keyword
            // names, we cannot also have optional positional arguments, but we're
            // being defensive here.
            numOfPositionalArgs = builtin.minNumOfArguments();
        }

        if (builtin.fixedNumOfArguments() > 0) {
            numOfPositionalArgs = builtin.fixedNumOfArguments();
        }

        if (!needsExplicitSelf) {
            // if we don't declare the explicit self, we just read (and ignore) it
            numOfPositionalArgs++;
        }

        // read those arguments that only come positionally
        for (int i = 0; i < numOfPositionalArgs; i++) {
            args.add(ReadIndexedArgumentNode.create(i));
        }

        // read splat args if any
        if (builtin.takesVariableArguments()) {
            args.add(ReadVarArgsNode.create(args.size(), true));
        }

        // read named keyword arguments
        for (int i = 0; i < builtin.keywordArguments().length; i++) {
            String name = builtin.keywordArguments()[i];
            ReadDefaultArgumentNode defaultNode = new ReadDefaultArgumentNode();
            defaultNode.setValue(PNone.NO_VALUE);
            if (!builtin.takesVariableArguments()) {
                // if there's no splat, we also accept the keywords positionally
                args.add(ReadKeywordNode.create(name, i + numOfPositionalArgs, defaultNode));
            } else {
                // if there is a splat, keywords have to be passed by name
                args.add(ReadKeywordNode.create(name, defaultNode));
            }
        }

        if (builtin.takesVariableKeywords()) {
            args.add(ReadVarKeywordsNode.create(builtin.keywordArguments()));
        }

        return args.toArray(new PNode[args.size()]);
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (body == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PNode[] argumentsList = createArgumentsList(builtin, declaresExplicitSelf);
            if (PythonBuiltinNode.class.isAssignableFrom(factory.getNodeClass())) {
                if (!declaresExplicitSelf) {
                    PNode[] argumentsListWithoutSelf = new PNode[argumentsList.length - 1];
                    System.arraycopy(argumentsList, 1, argumentsListWithoutSelf, 0, argumentsListWithoutSelf.length);
                    body = insert(new BuiltinAnyCallNode((PythonBuiltinNode) factory.createNode((Object) argumentsListWithoutSelf)));
                } else {
                    body = insert(new BuiltinAnyCallNode((PythonBuiltinNode) factory.createNode((Object) argumentsList)));
                }
            } else {
                PythonBuiltinBaseNode node = factory.createNode();
                if (node instanceof PythonUnaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 2 : "mismatch in number of arguments for " + node.getClass().getName();
                        body = insert(new BuiltinUnaryCallNode((PythonUnaryBuiltinNode) node, argumentsList[1]));
                    } else {
                        assert argumentsList.length == 1 : "mismatch in number of arguments for " + node.getClass().getName();
                        body = insert(new BuiltinUnaryCallNode((PythonUnaryBuiltinNode) node, argumentsList[0]));
                    }
                } else if (node instanceof PythonBinaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 3 : "mismatch in number of arguments for " + node.getClass().getName();
                        body = insert(new BuiltinBinaryCallNode((PythonBinaryBuiltinNode) node, argumentsList[1], argumentsList[2]));
                    } else {
                        assert argumentsList.length == 2 : "mismatch in number of arguments for " + node.getClass().getName();
                        body = insert(new BuiltinBinaryCallNode((PythonBinaryBuiltinNode) node, argumentsList[0], argumentsList[1]));
                    }
                } else if (node instanceof PythonTernaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 4 : "mismatch in number of arguments for " + node.getClass().getName();
                        body = insert(new BuiltinTernaryCallNode((PythonTernaryBuiltinNode) node, argumentsList[1], argumentsList[2], argumentsList[3]));
                    } else {
                        assert argumentsList.length == 3 : "mismatch in number of arguments for " + node.getClass().getName();
                        body = insert(new BuiltinTernaryCallNode((PythonTernaryBuiltinNode) node, argumentsList[0], argumentsList[1], argumentsList[2]));
                    }
                } else if (node instanceof PythonVarargsBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 4 : "mismatch in number of arguments for " + node.getClass().getName();
                        assert argumentsList[0] != null && argumentsList[1] != null && argumentsList[2] != null && argumentsList[3] != null;
                        body = insert(new BuiltinVarArgsCallNode((PythonVarargsBuiltinNode) node, argumentsList[1], argumentsList[2], argumentsList[3]));
                    } else {
                        assert argumentsList.length == 3 : "mismatch in number of arguments for " + node.getClass().getName();
                        assert argumentsList[0] != null && argumentsList[1] != null && argumentsList[2] != null;
                        body = insert(new BuiltinVarArgsCallNode((PythonVarargsBuiltinNode) node, argumentsList[0], argumentsList[1], argumentsList[2]));
                    }
                } else {
                    throw new RuntimeException("unexpected builtin node type: " + node.getClass());
                }
            }
        }
        return body.execute(frame);
    }

    public String getFunctionName() {
        return builtin.name();
    }

    public NodeFactory<? extends PythonBuiltinBaseNode> getFactory() {
        return factory;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<builtin function " + builtin.name() + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Override
    public String getName() {
        return builtin.name();
    }

    public boolean declaresExplicitSelf() {
        return declaresExplicitSelf;
    }
}
