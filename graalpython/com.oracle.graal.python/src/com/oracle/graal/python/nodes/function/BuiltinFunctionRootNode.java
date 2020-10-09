/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode.BuiltinAnyCallNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode.BuiltinBinaryCallNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode.BuiltinQuaternaryCallNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode.BuiltinTernaryCallNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode.BuiltinUnaryCallNode;
import com.oracle.graal.python.nodes.function.builtins.BuiltinCallNode.BuiltinVarArgsCallNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.WrapBinaryfuncR;
import com.oracle.graal.python.nodes.function.builtins.WrapTpNew;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

/**
 * CPython wraps built-in types' slots so the C can take the direct arguments. The slot wrappers for
 * binary and ternay functions (wrap_unaryfunc, wrap_binaryfunc_r, wrap_ternaryfunc,
 * wrap_ternaryfunc_r) extract the arguments using PyArg_UnpackTuple. For the reverse operations,
 * they also swap arguments appropriately, so that in C they can use one function to implement both
 * the operation and its reverse.
 *
 * This BuiltinFunctionRootNode similarly maps from an argument array to the signature of the Java
 * execute method we want and cares about swapping arguments back if needed.
 */
public final class BuiltinFunctionRootNode extends PRootNode {
    private final Signature signature;
    private final Builtin builtin;
    private final String name;
    private final NodeFactory<? extends PythonBuiltinBaseNode> factory;
    private final boolean declaresExplicitSelf;
    @Child private BuiltinCallNode body;
    @Child private CalleeContext calleeContext = CalleeContext.create();

    public BuiltinFunctionRootNode(PythonLanguage language, Builtin builtin, NodeFactory<? extends PythonBuiltinBaseNode> factory, boolean declaresExplicitSelf) {
        super(language);
        CompilerAsserts.neverPartOfCompilation();
        this.signature = createSignature(factory, builtin, declaresExplicitSelf);
        this.builtin = builtin;
        this.name = builtin.name();
        this.factory = factory;
        this.declaresExplicitSelf = declaresExplicitSelf;
        if (builtin.alwaysNeedsCallerFrame()) {
            setNeedsCallerFrame();
        }
    }

    public static class StandaloneBuiltinFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;
        private final Class<T> nodeClass;

        public StandaloneBuiltinFactory(T node) {
            this.node = node;
            this.nodeClass = determineNodeClass(node);
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return nodeClass;
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> determineNodeClass(T node) {
            CompilerAsserts.neverPartOfCompilation();
            Class<T> nodeClass = (Class<T>) node.getClass();
            GeneratedBy genBy = nodeClass.getAnnotation(GeneratedBy.class);
            if (genBy != null) {
                nodeClass = (Class<T>) genBy.value();
                assert nodeClass.isAssignableFrom(node.getClass());
            }
            return nodeClass;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            throw new IllegalAccessError();
        }
    }

    /**
     * Should return a signature compatible with {@link #createArgumentsList(Builtin, boolean)}
     */
    private static Signature createSignature(NodeFactory<? extends PythonBuiltinBaseNode> factory, Builtin builtin, boolean declaresExplicitSelf) {
        String[] parameterNames = builtin.parameterNames();
        int maxNumPosArgs = Math.max(builtin.minNumOfPositionalArgs(), parameterNames.length);

        if (builtin.maxNumOfPositionalArgs() >= 0) {
            maxNumPosArgs = builtin.maxNumOfPositionalArgs();
            assert parameterNames.length == 0 : "either give all parameter names explicitly, or define the max number: " + builtin.name() + " - " + String.join(",", builtin.parameterNames()) +
                            " vs " + builtin.maxNumOfPositionalArgs() + " - " + factory.toString();
        }

        if (!declaresExplicitSelf) {
            // if we don't take the explicit self, we still need to accept it by signature
            maxNumPosArgs++;
        } else if (builtin.constructsClass() != PythonBuiltinClassType.nil && maxNumPosArgs == 0) {
            // we have this convention to always declare the cls argument without setting the num
            // args
            maxNumPosArgs = 1;
        }

        if (maxNumPosArgs > 0) {
            if (parameterNames.length == 0) {
                // PythonLanguage.getLogger().log(Level.FINEST, "missing parameter names for builtin
                // " + factory);
                parameterNames = new String[maxNumPosArgs];
                parameterNames[0] = builtin.constructsClass() != PythonBuiltinClassType.nil ? "$cls" : "$self";
                for (int i = 1, p = 'a'; i < parameterNames.length; i++, p++) {
                    parameterNames[i] = Character.toString((char) p);
                }
            } else {
                if (declaresExplicitSelf) {
                    assert parameterNames.length == maxNumPosArgs : "not enough parameter ids on " + factory;
                } else {
                    // we don't declare the "self" as a parameter id unless it's explicit
                    assert parameterNames.length + 1 == maxNumPosArgs : "not enough parameter ids on " + factory;
                    parameterNames = Arrays.copyOf(parameterNames, parameterNames.length + 1);
                    PythonUtils.arraycopy(parameterNames, 0, parameterNames, 1, parameterNames.length - 1);
                    parameterNames[0] = builtin.constructsClass() != PythonBuiltinClassType.nil ? "$cls" : "$self";
                }
            }
        }

        return new Signature(builtin.numOfPositionalOnlyArgs(), builtin.takesVarKeywordArgs(), (builtin.takesVarArgs() || builtin.varArgsMarker()) ? parameterNames.length : -1,
                        builtin.varArgsMarker(), parameterNames, builtin.keywordOnlyNames());
    }

    /**
     * Must return argument reads compatible with
     * {@link #createSignature(NodeFactory, Builtin, boolean)}
     */
    private static ReadArgumentNode[] createArgumentsList(Builtin builtin, boolean needsExplicitSelf) {
        ArrayList<ReadArgumentNode> args = new ArrayList<>();

        String[] parameterNames = builtin.parameterNames();
        int maxNumPosArgs = Math.max(builtin.minNumOfPositionalArgs(), parameterNames.length);

        if (builtin.maxNumOfPositionalArgs() >= 0) {
            maxNumPosArgs = builtin.maxNumOfPositionalArgs();
            assert parameterNames.length == 0 : "either give all parameter names explicitly, or define the max number: " + builtin.name();
        }

        if (!needsExplicitSelf) {
            // if we don't declare the explicit self, we just read (and ignore) it
            maxNumPosArgs++;
        }

        // read those arguments that only come positionally
        for (int i = 0; i < maxNumPosArgs; i++) {
            args.add(ReadIndexedArgumentNode.create(i));
        }

        // read splat args if any
        if (builtin.takesVarArgs()) {
            args.add(ReadVarArgsNode.create(args.size(), true));
        }

        int keywordCount = builtin.keywordOnlyNames().length;
        for (int i = 0; i < keywordCount; i++) {
            args.add(ReadIndexedArgumentNode.create(i + maxNumPosArgs));
        }

        if (builtin.takesVarKeywordArgs()) {
            args.add(ReadVarKeywordsNode.create());
        }

        return args.toArray(new ReadArgumentNode[args.size()]);
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public boolean isCaptureFramesForTrace() {
        return false;
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (body == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            BuiltinCallNode newBody;
            ReadArgumentNode[] argumentsList = createArgumentsList(builtin, declaresExplicitSelf);
            if (PythonBuiltinNode.class.isAssignableFrom(factory.getNodeClass())) {
                if (!declaresExplicitSelf) {
                    ReadArgumentNode[] argumentsListWithoutSelf = new ReadArgumentNode[argumentsList.length - 1];
                    PythonUtils.arraycopy(argumentsList, 1, argumentsListWithoutSelf, 0, argumentsListWithoutSelf.length);
                    newBody = new BuiltinAnyCallNode((PythonBuiltinNode) factory.createNode((Object) argumentsListWithoutSelf));
                } else {
                    newBody = new BuiltinAnyCallNode((PythonBuiltinNode) factory.createNode((Object) argumentsList));
                }
            } else {
                PythonBuiltinBaseNode node = factory.createNode();
                if (node instanceof PythonUnaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 2 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinUnaryCallNode((PythonUnaryBuiltinNode) node, argumentsList[1]);
                    } else {
                        assert argumentsList.length == 1 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinUnaryCallNode((PythonUnaryBuiltinNode) node, argumentsList[0]);
                    }
                } else if (node instanceof PythonBinaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 3 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinBinaryCallNode((PythonBinaryBuiltinNode) node, argumentsList[1], argumentsList[2]);
                    } else {
                        assert argumentsList.length == 2 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinBinaryCallNode((PythonBinaryBuiltinNode) node, argumentsList[0], argumentsList[1]);
                    }
                } else if (node instanceof PythonTernaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 4 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinTernaryCallNode((PythonTernaryBuiltinNode) node, argumentsList[1], argumentsList[2], argumentsList[3]);
                    } else {
                        assert argumentsList.length == 3 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinTernaryCallNode((PythonTernaryBuiltinNode) node, argumentsList[0], argumentsList[1], argumentsList[2]);
                    }
                } else if (node instanceof PythonQuaternaryBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 5 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinQuaternaryCallNode((PythonQuaternaryBuiltinNode) node, argumentsList[1], argumentsList[2], argumentsList[3], argumentsList[4]);
                    } else {
                        assert argumentsList.length == 4 : "mismatch in number of arguments for " + node.getClass().getName();
                        newBody = new BuiltinQuaternaryCallNode((PythonQuaternaryBuiltinNode) node, argumentsList[0], argumentsList[1], argumentsList[2], argumentsList[3]);
                    }
                } else if (node instanceof PythonVarargsBuiltinNode) {
                    if (!declaresExplicitSelf) {
                        assert argumentsList.length == 4 : "mismatch in number of arguments for " + node.getClass().getName();
                        assert argumentsList[0] != null && argumentsList[1] != null && argumentsList[2] != null && argumentsList[3] != null;
                        newBody = new BuiltinVarArgsCallNode((PythonVarargsBuiltinNode) node, argumentsList[1], argumentsList[2], argumentsList[3]);
                    } else {
                        assert argumentsList.length == 3 : "mismatch in number of arguments for " + node.getClass().getName();
                        assert argumentsList[0] != null && argumentsList[1] != null && argumentsList[2] != null;
                        newBody = new BuiltinVarArgsCallNode((PythonVarargsBuiltinNode) node, argumentsList[0], argumentsList[1], argumentsList[2]);
                    }
                } else {
                    throw new RuntimeException("unexpected builtin node type: " + node.getClass());
                }
            }

            if (builtin.reverseOperation()) {
                body = insert(new WrapBinaryfuncR(newBody));
            } else if (builtin.constructsClass() != PythonBuiltinClassType.nil) {
                body = insert(new WrapTpNew(newBody));
            } else {
                body = insert(newBody);
            }
        }
        calleeContext.enter(frame);
        try {
            return body.execute(frame);
        } finally {
            calleeContext.exit(frame, this);
        }
    }

    public String getFunctionName() {
        return name;
    }

    public NodeFactory<? extends PythonBuiltinBaseNode> getFactory() {
        return factory;
    }

    public Builtin getBuiltin() {
        return builtin;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<builtin function " + name + " at " + Integer.toHexString(hashCode()) + ">";
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean declaresExplicitSelf() {
        return declaresExplicitSelf;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public boolean isPythonInternal() {
        return true;
    }
}
