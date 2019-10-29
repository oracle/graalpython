/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.graal.python.parser;

import static com.oracle.graal.python.nodes.PNodeUtil.replace;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PNodeUtil;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.BreakTargetNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.control.WhileNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.function.InnerRootNode;
import com.oracle.graal.python.nodes.generator.AbstractYieldNode;
import com.oracle.graal.python.nodes.generator.GeneratorBlockNode;
import com.oracle.graal.python.nodes.generator.GeneratorControlNode;
import com.oracle.graal.python.nodes.generator.GeneratorForNode;
import com.oracle.graal.python.nodes.generator.GeneratorIfNode;
import com.oracle.graal.python.nodes.generator.GeneratorReturnTargetNode;
import com.oracle.graal.python.nodes.generator.GeneratorTryExceptNode;
import com.oracle.graal.python.nodes.generator.GeneratorTryFinallyNode;
import com.oracle.graal.python.nodes.generator.GeneratorWhileNode;
import com.oracle.graal.python.nodes.generator.GeneratorWithNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.generator.WriteGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.generator.YieldFromNode;
import com.oracle.graal.python.nodes.generator.YieldNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.statement.WithNode;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

public class GeneratorTranslator {

    private final FunctionRootNode root;
    private int numYields = 1;
    private int numOfActiveFlags;
    private int numOfGeneratorBlockNode;
    private int numOfGeneratorForNode;
    private boolean needToHandleComplicatedYieldExpression;
    private ExpressionNode getOuterMostLoopIterator;
    private final boolean inGeneratorExpression;

    public GeneratorTranslator(FunctionRootNode root, boolean inGeneratorExpression) {
        this.root = root;
        this.inGeneratorExpression = inGeneratorExpression;
    }

    public RootCallTarget translate() {
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(root);

        /**
         * Replace {@link ReturnTargetNode}.
         */
        List<ReturnTargetNode> returnTargets = NodeUtil.findAllNodeInstances(root, ReturnTargetNode.class);
        assert returnTargets.size() == 1;
        splitArgumentLoads(returnTargets.get(0));

        ForNode outerMostLoop = NodeUtil.findFirstNodeInstance(root, ForNode.class);
        if (outerMostLoop != null && inGeneratorExpression) {
            replaceOuterMostForNode(outerMostLoop);
        }

        /**
         * Redirect local variable accesses to materialized persistent frame.
         */
        for (WriteLocalVariableNode write : NodeUtil.findAllNodeInstances(root, WriteLocalVariableNode.class)) {
            replace(write, WriteGeneratorFrameVariableNode.create(write.getSlot(), write.getRhs()));
        }

        for (ReadLocalVariableNode read : NodeUtil.findAllNodeInstances(root, ReadLocalVariableNode.class)) {
            replace(read, ReadGeneratorFrameVariableNode.create(read.getSlot()));
        }

        for (YieldFromNode yieldFrom : NodeUtil.findAllNodeInstances(root, YieldFromNode.class)) {
            replaceYieldFrom(yieldFrom);
        }

        /**
         * Rewrite all control flow paths leading to yields.
         */
        for (YieldNode yield : NodeUtil.findAllNodeInstances(root, YieldNode.class)) {
            replaceYield(yield);
        }

        for (GeneratorExpressionNode genexp : NodeUtil.findAllNodeInstances(root, GeneratorExpressionNode.class)) {
            genexp.setEnclosingFrameGenerator(true);
        }

        return callTarget;
    }

    private void replaceYieldFrom(YieldFromNode yield) {
        PNode current = yield;
        yield.setIteratorSlot(nextGeneratorForNodeSlot());
        yield.setFlagSlot(nextActiveFlagSlot());
        yield.setIndex(nextYieldIndex());

        if (yield.getParent() instanceof GeneratorReturnTargetNode) {
            // if this yield...from is the only thing in the body, we introduce a block
            replace(yield, BlockNode.create(yield.asStatement()));
        }

        while (current.getParent() != root) {
            Node parent = current.getParent();
            // skip non-python nodes (e.g., Truffle loop nodes)
            while (!(parent instanceof PNode)) {
                parent = parent.getParent();
            }
            current = (PNode) parent;
            replaceControl(current);
        }

        if (needToHandleComplicatedYieldExpression) {
            needToHandleComplicatedYieldExpression = false;
            // TranslationUtil.notCovered("Yield expression used in a complicated expression");
            handleComplicatedYieldExpression(yield);
        }
    }

    private void replaceYield(YieldNode yield) {
        PNode current = yield;
        yield.setFlagSlot(nextActiveFlagSlot());
        yield.setIndex(nextYieldIndex());

        if (yield.getParent() instanceof GeneratorReturnTargetNode) {
            // if this yield is the only thing in the body, we introduce a block
            replace(yield, BlockNode.create(yield.asStatement()));
        }

        while (current.getParent() != root) {
            Node parent = current.getParent();
            // skip non-python nodes (e.g., Truffle loop nodes)
            while (!(parent instanceof PNode)) {
                parent = parent.getParent();
            }
            current = (PNode) parent;
            replaceControl(current);
        }

        if (needToHandleComplicatedYieldExpression) {
            needToHandleComplicatedYieldExpression = false;
            // TranslationUtil.notCovered("Yield expression used in a complicated expression");
            handleComplicatedYieldExpression(yield);
        }
    }

    public void handleComplicatedYieldExpression(AbstractYieldNode yield) {
        // Find the dominating StatementNode.
        StatementNode targetingStatement = (StatementNode) PNodeUtil.getParentFor(yield, WriteNode.class);

        List<ExpressionNode> subExpressions = PNodeUtil.getListOfSubExpressionsInOrder(targetingStatement);
        List<PNode> extractedExpressions = new ArrayList<>();
        List<StatementNode> extractedWrites = new ArrayList<>();
        for (ExpressionNode expr : subExpressions) {
            if (expr.equals(yield)) {
                break;
            }

            if (!expr.hasSideEffectAsAnExpression()) {
                continue;
            }

            if (isExtracted(extractedExpressions, expr)) {
                continue;
            }

            FrameSlot slot = TranslationEnvironment.makeTempLocalVariable(root.getFrameDescriptor());
            ReadNode read = ReadGeneratorFrameVariableNode.create(slot);
            replace(expr, (PNode) read);
            extractedWrites.add(read.makeWriteNode(expr));
            extractedExpressions.add(expr);
        }

        GeneratorBlockNode targetingBlock = (GeneratorBlockNode) targetingStatement.getParent();
        GeneratorBlockNode extendedBlock = targetingBlock.insertNodesBefore(targetingStatement, extractedWrites);
        replace(targetingBlock, extendedBlock);
    }

    private static boolean isExtracted(List<PNode> extractedExpressins, PNode expr) {
        for (PNode item : extractedExpressins) {
            if (expressionDominates(expr, item)) {
                return true;
            }
        }

        return false;
    }

    private static boolean expressionDominates(PNode expr, PNode potentialDominator) {
        if (expr.equals(potentialDominator)) {
            return false;
        }

        Node current = expr.getParent();
        while (!(current instanceof GeneratorBlockNode)) {
            if (current.equals(potentialDominator)) {
                return true;
            }

            current.getParent();
        }

        return false;
    }

    private void splitArgumentLoads(ReturnTargetNode returnTarget) {
        if (returnTarget.getBody() instanceof BlockNode) {
            BlockNode body = (BlockNode) returnTarget.getBody();
            assert body.getStatements().length == 2;
            StatementNode argumentLoads = body.getStatements()[0];
            replace(returnTarget, new GeneratorReturnTargetNode(argumentLoads, body.getStatements()[1], returnTarget.getReturn(), nextActiveFlagSlot()));
        } else {
            replace(returnTarget, new GeneratorReturnTargetNode(BlockNode.create(), returnTarget.getBody(), returnTarget.getReturn(), nextActiveFlagSlot()));
        }
    }

    private void replaceForNode(ForNode forNode) {
        WriteNode target = (WriteNode) forNode.getTarget();
        ExpressionNode getIter = forNode.getIterator();
        replace(forNode, GeneratorForNode.create(target, getIter, forNode.getBody(), nextGeneratorForNodeSlot()));
    }

    private void replaceOuterMostForNode(ForNode forNode) {
        WriteNode target = (WriteNode) forNode.getTarget();
        ExpressionNode getIter = forNode.getIterator();
        getOuterMostLoopIterator = getIter;
        getIter = ReadIndexedArgumentNode.create(0).asExpression();
        replace(forNode, GeneratorForNode.create(target, getIter, forNode.getBody(), nextGeneratorForNodeSlot()));
    }

    private void replaceControl(PNode node) {
        /**
         * Has it been replaced already?
         */
        if (node instanceof GeneratorControlNode) {
            return;
        }

        if (node instanceof WhileNode) {
            WhileNode whileNode = (WhileNode) node;

            if (node.getParent() instanceof BreakTargetNode) {
                node.getParent().replace(new GeneratorWhileNode(whileNode.getCondition(), whileNode.getBody(), nextActiveFlagSlot()));
            } else {
                replace(node, new GeneratorWhileNode(whileNode.getCondition(), whileNode.getBody(), nextActiveFlagSlot()));
            }
        } else if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            int ifFlag = nextActiveFlagSlot();
            int elseFlag = nextActiveFlagSlot();
            replace(node, GeneratorIfNode.create(ifNode.getCondition(), ifNode.getThen(), ifNode.getElse(), ifFlag, elseFlag));

        } else if (node instanceof ForNode) {
            ForNode forNode = (ForNode) node;
            replaceForNode(forNode);

        } else if (node instanceof WithNode) {
            replaceWithNode(((WithNode) node));

        } else if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            int slotOfBlockIndex = nextGeneratorBlockIndexSlot();

            replace(node, new GeneratorBlockNode(block.getStatements(), slotOfBlockIndex));

        } else if (node instanceof TryExceptNode) {
            TryExceptNode tryExceptNode = (TryExceptNode) node;
            int exceptFlag = nextActiveFlagSlot();
            int elseFlag = nextActiveFlagSlot();
            int exceptIndex = nextGeneratorBlockIndexSlot();
            replace(node, new GeneratorTryExceptNode(tryExceptNode.getBody(), tryExceptNode.getExceptNodes(), tryExceptNode.getOrelse(), exceptFlag, elseFlag, exceptIndex));

        } else if (node instanceof TryFinallyNode) {
            TryFinallyNode tryFinally = (TryFinallyNode) node;
            int finallyFlag = nextActiveFlagSlot();
            replace(node, new GeneratorTryFinallyNode(tryFinally.getBody(), tryFinally.getFinalbody(), finallyFlag));

        } else if (node instanceof StatementNode) {
            // do nothing for now

        } else if (node instanceof InnerRootNode) {
            // nothing to do

        } else {
            replaceYieldExpression(node);
        }
    }

    private void replaceWithNode(WithNode withNode) {
        replace(withNode, new GeneratorWithNode(withNode.getTargetNode(), withNode.getBody(), withNode.getWithContext(), nextActiveFlagSlot(), nextGeneratorForNodeSlot(), nextActiveFlagSlot()));
    }

    /**
     * Yield used in an expression.
     * <p>
     * The parent nodes of yield are expressions. If all the other sub-expressions evaluated before
     * yield are side affect free, we simply re-evaluate those sub-expressions when resuming.
     * Otherwise we give up.
     */
    private void replaceYieldExpression(PNode node) {
        /**
         * Search for child expressions for ones that are not side-affect free (does not change any
         * local or non-local state).
         */
        for (Node child : node.getChildren()) {
            if (!(child instanceof PNode)) {
                continue;
            }

            if (NodeUtil.findAllNodeInstances(child, YieldNode.class).size() != 0) {
                continue;
            }

            child.accept(new NodeVisitor() {
                public boolean visit(Node childNode) {
                    assert !(child instanceof StatementNode);

                    if (childNode instanceof ExpressionNode) {
                        ExpressionNode childPNode = (ExpressionNode) childNode;
                        if (childPNode.hasSideEffectAsAnExpression()) {
                            needToHandleComplicatedYieldExpression = true;
                        }
                    }
                    return true;
                }
            });
        }
    }

    private int nextYieldIndex() {
        return numYields++;
    }

    private int nextActiveFlagSlot() {
        return numOfActiveFlags++;
    }

    public int getNumOfActiveFlags() {
        return numOfActiveFlags;
    }

    private int nextGeneratorBlockIndexSlot() {
        return numOfGeneratorBlockNode++;
    }

    public int getNumOfGeneratorBlockNode() {
        return numOfGeneratorBlockNode;
    }

    private int nextGeneratorForNodeSlot() {
        return numOfGeneratorForNode++;
    }

    public int getNumOfGeneratorForNode() {
        return numOfGeneratorForNode;
    }

    public ExpressionNode getGetOuterMostLoopIterator() {
        return getOuterMostLoopIterator;
    }
}
