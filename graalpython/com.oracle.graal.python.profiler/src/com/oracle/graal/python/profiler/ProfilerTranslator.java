/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.graal.python.profiler;

import java.util.*;

import com.oracle.truffle.api.nodes.*;

import com.oracle.graal.python.nodes.*;
import com.oracle.graal.python.nodes.call.*;
import com.oracle.graal.python.nodes.control.*;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.expression.*;
import com.oracle.graal.python.nodes.frame.*;
import com.oracle.graal.python.nodes.function.*;
import com.oracle.graal.python.nodes.generator.*;
import com.oracle.graal.python.nodes.object.*;
import com.oracle.graal.python.nodes.subscript.*;
import com.oracle.graal.python.runtime.*;


public class ProfilerTranslator implements NodeVisitor {

    private final PythonProfilerNodeProber profilerProber;
    private final ProfilerResultPrinter resultPrinter;
    private final PythonContext context;
    private final PythonParseResult parseResult;

    public ProfilerTranslator(PythonParseResult parseResult, PythonContext context) {
        this.context = context;
        this.parseResult = parseResult;
        this.profilerProber = PythonProfilerNodeProber.getInstance();
        this.resultPrinter = new ProfilerResultPrinter(this.profilerProber, parseResult);
    }

    public void translate() {
        RootNode root = parseResult.getModuleRoot();
        root.accept(this);

        for (RootNode functionRoot : parseResult.getFunctionRoots()) {
            functionRoot.accept(this);
        }
    }

    @Override
    public boolean visit(Node node) {
        if (PythonOptions.ProfileCalls) {
            profileCalls(node);
        }

        if (PythonOptions.ProfileControlFlow) {
            profileControlFlow(node);
        }

        if (PythonOptions.ProfileVariableAccesses) {
            profileVariables(node);
        }

        if (PythonOptions.ProfileOperations) {
            profileOperations(node);
        }

        if (PythonOptions.ProfileCollectionOperations) {
            profileCollectionOperations(node);
        }

        return true;
    }

    private void profileCalls(Node node) {
        if (node instanceof FunctionRootNode) {
            FunctionRootNode rootNode = (FunctionRootNode) node;
            PNode body = rootNode.getBody();
            createMethodBodyWrapper(body);
        } else if (node instanceof PythonCallNode) {
            PythonCallNode callNode = (PythonCallNode) node;
            createCallWrapper(callNode);
        }
    }

    private void profileControlFlow(Node node) {
        profileLoops(node);
        profileIfs(node);
        profileBreakContinues(node);
    }

    private void profileLoops(Node node) {
        /**
         * TODO Currently generator loops are not profiled
         */
        if (node instanceof LoopNode && !(node instanceof GeneratorForNode) && !(node instanceof GeneratorWhileNode)) {
            LoopNode loopNode = (LoopNode) node;
            PNode loopBodyNode = loopNode.getBody();
            createLoopBodyWrapper(loopBodyNode);
        }
    }

    private void profileIfs(Node node) {
        if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            if (hasSourceSection(ifNode)) {
                PNode thenNode = ifNode.getThen();
                PNode elseNode = ifNode.getElse();
                /**
                 * Only create a wrapper node for the else part if the else part exists.
                 */
                if (elseNode instanceof EmptyNode) {
                    if (checkSourceSection(thenNode)) {
                        createIfWithoutElseWrappers(ifNode, thenNode);
                    }
                } else {
                    if (checkSourceSection(thenNode) && checkSourceSection(elseNode)) {
                        createIfWrappers(ifNode, thenNode, elseNode);
                    }
                }
            }
        }
    }

    private void profileBreakContinues(Node node) {
        if (node instanceof BreakNode || node instanceof ContinueNode) {
            createBreakContinueWrapper((PNode) node);
        }
    }

    private void profileVariables(Node node) {
        if (!(node.getParent() instanceof PythonCallNode)) {
            if (node instanceof WriteLocalVariableNode) {
                createReadWriteWrapper((PNode) node);
            } else if (node instanceof ReadLocalVariableNode) {
                createReadWriteWrapper((PNode) node);
            } else if (node instanceof ReadLevelVariableNode) {
                createReadWriteWrapper((PNode) node);
            } else if (node instanceof ReadGlobalNode) {
                createReadWriteWrapper((PNode) node);
            } else if (node instanceof SetAttributeNode) {
                createCollectionOperationWrapper((PNode) node);
            } else if (node instanceof GetAttributeNode) {
                createCollectionOperationWrapper((PNode) node);
            }
        }
    }

    private void profileOperations(Node node) {
        if (!(node.getParent() instanceof PythonCallNode)) {
            if (node instanceof BinaryArithmeticNode) {
                createOperationWrapper((PNode) node);
            } else if (node instanceof BinaryBitwiseNode) {
                createOperationWrapper((PNode) node);
            } else if (node instanceof BinaryBooleanNode) {
                createOperationWrapper((PNode) node);
            } else if (node instanceof BinaryComparisonNode) {
                createOperationWrapper((PNode) node);
            } else if (node instanceof UnaryArithmeticNode) {
                createOperationWrapper((PNode) node);
            } else if (node instanceof BreakNode) {
                createOperationWrapper((PNode) node);
            } else if (node instanceof ContinueNode) {
                createOperationWrapper((PNode) node);
            }
        }
    }

    private void profileCollectionOperations(Node node) {
        if (!(node.getParent() instanceof PythonCallNode)) {
            if (node instanceof SubscriptLoadIndexNode) {
                createCollectionOperationWrapper((PNode) node);
            } else if (node instanceof SubscriptLoadSliceNode) {
                createCollectionOperationWrapper((PNode) node);
            } else if (node instanceof SubscriptDeleteNode) {
                createCollectionOperationWrapper((PNode) node);
            } else if (node instanceof SubscriptStoreIndexNode) {
                createCollectionOperationWrapper((PNode) node);
            } else if (node instanceof SubscriptStoreSliceNode) {
                createCollectionOperationWrapper((PNode) node);
            }
        }
    }

    private PythonWrapperNode createMethodBodyWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsMethodBody(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private PythonWrapperNode createCallWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsCall(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private PythonWrapperNode createLoopBodyWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsLoop(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private void createIfWrappers(IfNode ifNode, PNode thenNode, PNode elseNode) {
        List<PythonWrapperNode> wrappers = profilerProber.probeAsIf(ifNode, thenNode, elseNode, context);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(thenNode, wrappers.get(1));
        replaceNodeWithWrapper(elseNode, wrappers.get(2));
    }

    private void createIfWithoutElseWrappers(PNode ifNode, PNode thenNode) {
        List<PythonWrapperNode> wrappers = profilerProber.probeAsIfWithoutElse(ifNode, thenNode, context);
        replaceNodeWithWrapper(ifNode, wrappers.get(0));
        replaceNodeWithWrapper(thenNode, wrappers.get(1));
    }

    private PythonWrapperNode createBreakContinueWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsBreakContinue(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private PythonWrapperNode createReadWriteWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsVariableAccess(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private PythonWrapperNode createOperationWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsOperation(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private PythonWrapperNode createCollectionOperationWrapper(PNode node) {
        if (checkSourceSection(node)) {
            PythonWrapperNode wrapperNode = profilerProber.probeAsCollectionOperation(node, context);
            replaceNodeWithWrapper(node, wrapperNode);
            return wrapperNode;
        }

        return null;
    }

    private static void replaceNodeWithWrapper(PNode node, PythonWrapperNode wrapperNode) {
        /**
         * If a node is already wrapped, then another wrapper node is not created, and existing
         * wrapper node is used. If a wrapper node is not created, do not replace the node,
         * otherwise replace the node with the new created wrapper node
         */
        if (!wrapperNode.equals(node.getParent())) {
            node.replace(wrapperNode);
            wrapperNode.adoptChildren();
        }
    }

    private boolean checkSourceSection(PNode node) {
        if (hasSourceSection(node)) {
            return true;
        }

        return false;
    }

    private boolean hasSourceSection(PNode node) {
        if (node.getSourceSection() == null) {
            resultPrinter.addNodeEmptySourceSection(node);
            return false;
        }

        return true;
    }

    public ProfilerResultPrinter getProfilerResultPrinter() {
        return resultPrinter;
    }

    public PythonParseResult getParseResult() {
        return parseResult;
    }
}
