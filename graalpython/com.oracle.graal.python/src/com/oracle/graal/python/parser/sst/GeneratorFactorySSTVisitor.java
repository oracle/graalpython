/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.generator.GeneratorBlockNode;
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
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.ScopeEnvironment;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.runtime.PythonParser;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.Source;

public class GeneratorFactorySSTVisitor extends FactorySSTVisitor {

    private int numOfActiveFlags;
    private int numOfGeneratorBlockNode;
    private int numOfGeneratorForNode;
    private FactorySSTVisitor parentVisitor;
    
    public GeneratorFactorySSTVisitor(PythonParser.ParserErrorCallback errors, ScopeEnvironment scopeEnvironment, NodeFactory nodeFactory, Source source, FactorySSTVisitor parentVisitor) {
        super(errors, scopeEnvironment, nodeFactory, source);
        this.parentVisitor = parentVisitor;
        init();
    }

    public void init() {
        this.numOfActiveFlags = 0;
        this.numOfGeneratorBlockNode = 0;
        this.numOfGeneratorForNode = 0;
    }

    public int getNumOfActiveFlags() {
        return numOfActiveFlags;
    }

    public int getNumOfGeneratorBlockNode() {
        return numOfGeneratorBlockNode;
    }

    public int getNumOfGeneratorForNode() {
        return numOfGeneratorForNode;
    }
    
    public int getNextNumOfActiveFlags() {
        return numOfActiveFlags++;
    }

    public int getNextNumOfGeneratorBlockNode() {
        return numOfGeneratorBlockNode++;
    }
    
    public void decreaseNumOfGeneratorBlockNode() {
        numOfGeneratorBlockNode--;
    }

    public int getNextNumOfGeneratorForNode() {
        return numOfGeneratorForNode++;
    }
    
    
    @Override
    protected StatementNode createAssignmentBlock(AssignmentSSTNode node, StatementNode... statements) {
        if (node.rhs instanceof YieldExpressionSSTNode) {
            return new GeneratorBlockNode(statements, numOfGeneratorBlockNode++);
        } else {
            return BlockNode.create(statements);
        }
    }

    @Override
    protected StatementNode createWriteLocal(ExpressionNode value, FrameSlot slot) {
        return WriteGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot(), value);
    }
    
    @Override
    public PNode visit(BlockSSTNode node) {
        int oldNumber = numOfActiveFlags;
        StatementNode[] statements = new StatementNode[node.statements.length];
        for (int i =0; i < statements.length; i++) {
            statements[i] = (StatementNode)node.statements[i].accept(this);
        }
        if (statements.length == 1) {
            return BlockNode.create(statements);
        } else {
            return oldNumber != numOfActiveFlags 
                    ? new GeneratorBlockNode(statements, numOfGeneratorBlockNode++)
                    : nodeFactory.createBlock(statements);
        }
    }

    @Override
    public PNode visit(ForComprehensionSSTNode node) {
        int oldNumOfActiveFlags = numOfActiveFlags;
        int oldNumOfGeneratorBlockNode = numOfGeneratorBlockNode;
        int oldNumOfGeneratorForNode = numOfGeneratorForNode;
        init();
        SSTNode sstIterator = node.iterator instanceof ForComprehensionSSTNode ? ((ForComprehensionSSTNode)node.iterator).target : node.iterator;
        ScopeInfo originScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.scope.getParent());
        ExpressionNode iterator = (ExpressionNode)sstIterator.accept(this);
        GetIteratorExpressionNode getIterator = nodeFactory.createGetIterator(iterator);
        getIterator.assignSourceSection(iterator.getSourceSection());
        scopeEnvironment.setCurrentScope(node.scope);
        
        ExpressionNode targetExpression;
        parentVisitor.comprLevel++;
        if (node.resultType == PythonBuiltinClassType.PDict) {
            targetExpression = nodeFactory.createTupleLiteral((ExpressionNode)node.name.accept(this), (ExpressionNode)node.target.accept(parentVisitor));
        } else {
            targetExpression = (ExpressionNode)node.target.accept(parentVisitor);
        }
        parentVisitor.comprLevel--;
        YieldNode yieldExpression = nodeFactory.createYield(targetExpression);
        yieldExpression.setFlagSlot(numOfActiveFlags++);
        yieldExpression.assignSourceSection(targetExpression.getSourceSection());
        
        StatementNode body = createGeneratorExpressionBody(node, getIterator, yieldExpression.asStatement());

        ExpressionNode returnTarget;
        
        if (body instanceof BlockNode) {
            System.out.println(" NOt implemented block node");
            returnTarget = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        } else {
            returnTarget = new GeneratorReturnTargetNode(BlockNode.create(), body,  ReadGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot()), numOfActiveFlags++);
        } 
            
//        ExpressionNode returnTarget = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        returnTarget.assignSourceSection(body.getSourceSection());
//            int lineNum = ctx.getStart().getLine();

        // createing generator expression
        FrameDescriptor fd = node.scope.getFrameDescriptor();
        String name = node.scope.getParent().getScopeId() + ".<locals>.<genexp>:" + source.getName() + ":" + node.line;
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(returnTarget.getSourceSection(), name, true, fd, returnTarget, scopeEnvironment.getExecutionCellSlots(), Signature.EMPTY);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(funcRoot);
        ExpressionNode loopIterator = getIterator;
        GeneratorExpressionNode genExprDef = new GeneratorExpressionNode(name, callTarget, loopIterator, fd, scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots(), 
                numOfActiveFlags, numOfGeneratorBlockNode, numOfGeneratorForNode);
        genExprDef.setEnclosingFrameDescriptor(node.scope.getParent().getFrameDescriptor());
        genExprDef.assignSourceSection(funcRoot.getSourceSection());
        genExprDef.setEnclosingFrameGenerator(node.level != 0 || parentVisitor.comprLevel != 0 || node.scope.getParent().getScopeKind() == ScopeInfo.ScopeKind.Generator);
        PNode result;
        switch (node.resultType) {
            case PList:
                result = nodeFactory.callBuiltin(BuiltinNames.LIST, genExprDef);
                result.assignSourceSection(createSourceSection(node.target.startOffset, node.endOffset));
                break;
            case PSet:
                result = nodeFactory.callBuiltin(BuiltinNames.SET, genExprDef);
                result.assignSourceSection(createSourceSection(node.target.startOffset, node.endOffset));
                break;
            case PDict:
                result = nodeFactory.callBuiltin(BuiltinNames.DICT, genExprDef);
                result.assignSourceSection(createSourceSection(node.name != null ? node.name.startOffset : node.target.startOffset, node.endOffset));
                break;
            default:
                result = genExprDef;
                break;
        }
        scopeEnvironment.setCurrentScope(originScope);
        numOfActiveFlags = oldNumOfActiveFlags;
        numOfGeneratorBlockNode = oldNumOfGeneratorBlockNode;
        numOfGeneratorForNode = oldNumOfGeneratorForNode;
        return result;
    }
    
    private StatementNode createGeneratorExpressionBody(ForComprehensionSSTNode node, GetIteratorExpressionNode iterator, StatementNode yield) {
        ExpressionNode condition = null;
        if (node.conditions != null && node.conditions.length > 0) {
            condition = (ExpressionNode)node.conditions[0].accept(this);
            for(int i = 1; i < node.conditions.length; i++) {
                condition = nodeFactory.createBinaryOperation("and", condition, (ExpressionNode)node.conditions[i].accept(this));
            }
        }
        StatementNode body = yield;
        if (condition != null) {
            // TODO: Do we have to create empty block in the else branch?
            body = GeneratorIfNode.create(nodeFactory.createYesNode(condition), body, nodeFactory.createBlock(), numOfActiveFlags++, numOfActiveFlags++);
        } else if (node.iterator instanceof ForComprehensionSSTNode) {
            ForComprehensionSSTNode forComp = (ForComprehensionSSTNode)node.iterator;
            SSTNode sstIterator = forComp.iterator instanceof ForComprehensionSSTNode ? ((ForComprehensionSSTNode)forComp.iterator).target : forComp.iterator;
            ExpressionNode exprIterator = (ExpressionNode)sstIterator.accept(this);
            GetIteratorExpressionNode getIterator = nodeFactory.createGetIterator(exprIterator);
            getIterator.assignSourceSection(exprIterator.getSourceSection());
            body = createGeneratorExpressionBody(forComp, getIterator, yield);
        }
        
        StatementNode variable;
        if (node.variables.length == 1 ) {
//            if (node.variables[0] instanceof VarLookupSSTNode) {
//                VarLookupSSTNode vln = (VarLookupSSTNode)node.variables[0];
//                FrameSlot slot = scopeEnvironment.getCurrentScope().findFrameSlot(vln.name);
//                variable = makeWriteNode(ReadGeneratorFrameVariableNode.create(slot));
//            } else {
                variable = makeWriteNode((ExpressionNode)node.variables[0].accept(this));
//            }
        } else {
            ExpressionNode[] variables = new ExpressionNode[node.variables.length];
            for (int i = 0; i < node.variables.length; i++) {
                variables[i] = (ExpressionNode)node.variables[i].accept(this);
            }
            variable = makeWriteNode(nodeFactory.createObjectLiteral(variables));
        }
        body = GeneratorForNode.create((WriteNode)variable, 
                node.level == 0 ? ReadIndexedArgumentNode.create(0).asExpression() : iterator,
                body, numOfGeneratorForNode++);
        body.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return body;
    }
    
    @Override
    public PNode visit(ForSSTNode node) {
        int oldNumOfActiveFlags = numOfActiveFlags;
        ExpressionNode[] targets = new ExpressionNode[node.targets.length];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = (ExpressionNode)node.targets[i].accept(this);
        }
        PNode target;
        if (targets.length == 1) {
            target = targets[0];
            if (!(target instanceof ReadNode || target instanceof TupleLiteralNode || target instanceof ListLiteralNode)) {
                if (target instanceof StarredExpressionNode) {
                    throw errors.raise(SyntaxError, "starred assignment target must be in a list or tuple");
                } else {
                // TODO handle this???
//                String text = ctx.getText();
//                if (environment.isNonlocal(text)) {
//                    throw errors.raise(SyntaxError, "no binding for nonlocal variable \"%s\" found", text);
//                }
                    throw errors.raise(SyntaxError, "Cannot assign to %s", target);
                }
            }
        } else {
            target = nodeFactory.createObjectLiteral(targets);
        }
        StatementNode body = (StatementNode)node.body.accept(this);
        if (node.containsContinue) {
            body = nodeFactory.createContinueTarget(body);
        }
        ExpressionNode iterator = (ExpressionNode)node.iterator.accept(this);
        iterator.assignSourceSection(createSourceSection(node.iterator.startOffset, node.iterator.endOffset));
        GetIteratorExpressionNode getIterator = nodeFactory.createGetIterator(iterator);
        getIterator.assignSourceSection(iterator.getSourceSection());
        StatementNode forNode = oldNumOfActiveFlags == numOfActiveFlags
                ? new ForNode(body, makeWriteNode((ExpressionNode)target), getIterator)
                : GeneratorForNode.create((WriteNode)makeWriteNode((ExpressionNode)target), getIterator, body, numOfGeneratorForNode++);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? nodeFactory.createBlock(new StatementNode[0]) : (StatementNode)node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
             result = nodeFactory.createElse(forNode, elseBranch);
        } else {
            // TODO: this is also strange, that we create don't create ElseNode for break target. 
            // At least it seems to be inconsistent. 
            result = node.elseStatement == null ? 
                    //TODO: Do we need to create the empty block here?
                    nodeFactory.createBreakTarget(forNode, nodeFactory.createBlock(new StatementNode[0])) : 
                    nodeFactory.createBreakTarget(forNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }
    
    @Override
    public PNode visit(IfSSTNode node) {
        int oldNum = numOfActiveFlags;
        ExpressionNode test = (ExpressionNode)node.test.accept(this);
        StatementNode thenStatement = (StatementNode)node.thenStatement.accept(this);
        // TODO: Do we need to generate empty else block, if doesn't exist? The execution check if the else branch is empty anyway.
        StatementNode elseStatement = node.elseStatement == null ? nodeFactory.createBlock(new StatementNode[0]) : (StatementNode)node.elseStatement.accept(this);
        StatementNode result = oldNum != numOfActiveFlags
                ? GeneratorIfNode.create(nodeFactory.toBooleanCastNode(test), thenStatement, elseStatement, numOfActiveFlags++, numOfActiveFlags++)
                : nodeFactory.createIf(nodeFactory.toBooleanCastNode(test), thenStatement, elseStatement);
        if (node.startOffset != -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }
    
    @Override
    public PNode visit(TrySSTNode node) {
        int oldNumber = numOfActiveFlags;
        StatementNode body = (StatementNode)node.body.accept(this);
        StatementNode elseStatement = node.elseStatement == null ? nodeFactory.createBlock(): (StatementNode)node.elseStatement.accept(this);
        StatementNode finalyStatement = node.finallyStatement == null ? null: (StatementNode)node.finallyStatement.accept(this);
        ExceptNode[] exceptNodes = new ExceptNode[node.exceptNodes.length];
        for (int i = 0; i < exceptNodes.length; i++) {
            ExceptSSTNode exceptNode = node.exceptNodes[i];
            ExpressionNode exceptTest = exceptNode.test != null ? (ExpressionNode)exceptNode.test.accept(this) : null;
            StatementNode exceptBody = (StatementNode)exceptNode.body.accept(this);
            WriteNode exceptName = exceptNode.asName != null ? (WriteNode)scopeEnvironment.findVariable(exceptNode.asName).makeWriteNode(null) : null;
            exceptNodes[i] = new ExceptNode(exceptBody, exceptTest, exceptName);
        }
        
        StatementNode result;
        if (oldNumber == numOfActiveFlags) {
            result = nodeFactory.createTryExceptElseFinallyNode(body, exceptNodes, elseStatement, finalyStatement);
        } else {
            result = new GeneratorTryExceptNode(body, exceptNodes, elseStatement, numOfActiveFlags++, numOfActiveFlags++, numOfGeneratorBlockNode++);
            result = new GeneratorTryFinallyNode(result, finalyStatement, numOfActiveFlags++);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }
    
    @Override
    public PNode visit(WithSSTNode node) {
        int oldNumOfActiveFlags = numOfActiveFlags;
        StatementNode body = (StatementNode)node.body.accept(this);
        WriteNode asName = node.target == null ? null : (WriteNode)makeWriteNode((ExpressionNode)node.target.accept(this));
        ExpressionNode expression = (ExpressionNode)node.expression.accept(this);
        PNode result = oldNumOfActiveFlags != numOfActiveFlags 
                // if the body contains yield -> create Generator control node. 
                ? new GeneratorWithNode(asName, body, expression, numOfActiveFlags++, numOfGeneratorForNode++, numOfActiveFlags++)
                : nodeFactory.createWithNode(expression, asName, body);
        if (node.startOffset > -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }
    
    @Override
    public PNode visit(WhileSSTNode node) {
        int oldNumber = numOfActiveFlags;
        ExpressionNode test = (ExpressionNode)node.test.accept(this);
        StatementNode body = (StatementNode)node.body.accept(this);
        if (node.containsContinue) {
            body = nodeFactory.createContinueTarget(body);
        }
        StatementNode whileNode = oldNumber != numOfActiveFlags
                ? new GeneratorWhileNode(nodeFactory.toBooleanCastNode(test), body, numOfActiveFlags++)
                : nodeFactory.createWhile(nodeFactory.toBooleanCastNode(test), body);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? nodeFactory.createBlock(new StatementNode[0]) : (StatementNode)node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
             result = nodeFactory.createElse(whileNode, elseBranch);
        } else if (oldNumber == numOfActiveFlags ) {
            // TODO: The old parser doesn't enclude the else branch to the tree. See issue GR-16991
            // TODO: this is also strange, that we create don't create ElseNode for break target. 
            // At least it seems to be inconsistent.
            result = node.elseStatement == null ? 
                    //TODO: Do we need to create the empty block here?
                    nodeFactory.createBreakTarget(whileNode, nodeFactory.createBlock(new StatementNode[0])) : 
                    nodeFactory.createBreakTarget(whileNode, elseBranch);
        } else {
            result = whileNode;
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }
    
    @Override
    public PNode visit(YieldExpressionSSTNode node) {
        ExpressionNode value = node.value != null ? (ExpressionNode)node.value.accept(this) : EmptyNode.create();
        ExpressionNode result;
        if (node.isFrom) {
            YieldFromNode yieldNode = nodeFactory.createYieldFrom(value);
            yieldNode.setFlagSlot(numOfActiveFlags++);
            yieldNode.setIteratorSlot(numOfGeneratorForNode++);
            result = yieldNode;
        } else {
            YieldNode yieldNode = nodeFactory.createYield(value);
            yieldNode.setFlagSlot(numOfActiveFlags++);
            result = yieldNode;
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }
    
}
