/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.RootNodeFactory;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.BreakTargetNode;
import com.oracle.graal.python.nodes.control.ContinueTargetNode;
import com.oracle.graal.python.nodes.control.ElseNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.control.ReturnNode.GeneratorFrameReturnNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.control.WhileNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.generator.GeneratorBlockNode;
import com.oracle.graal.python.nodes.generator.GeneratorExpressionWithSideEffects;
import com.oracle.graal.python.nodes.generator.GeneratorForNode;
import com.oracle.graal.python.nodes.generator.GeneratorIfNode;
import com.oracle.graal.python.nodes.generator.GeneratorReturnTargetNode;
import com.oracle.graal.python.nodes.generator.GeneratorTryExceptNode;
import com.oracle.graal.python.nodes.generator.GeneratorTryFinallyNode;
import com.oracle.graal.python.nodes.generator.GeneratorWhileNode;
import com.oracle.graal.python.nodes.generator.GeneratorWithNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.generator.YieldFromNode;
import com.oracle.graal.python.nodes.generator.YieldNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.statement.WithNode;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.graal.python.parser.ScopeEnvironment;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.util.BiFunction;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.source.Source;

public class GeneratorFactorySSTVisitor extends FactorySSTVisitor {

    private GeneratorInfo.Mutable generatorInfo = new GeneratorInfo.Mutable();
    protected FactorySSTVisitor parentVisitor;

    public GeneratorFactorySSTVisitor(PythonParser.ParserErrorCallback errors, ScopeEnvironment scopeEnvironment, RootNodeFactory nodeFactory, Source source, FactorySSTVisitor parentVisitor) {
        super(errors, scopeEnvironment, nodeFactory, source);
        this.parentVisitor = parentVisitor;
    }

    public void decreaseNumOfGeneratorBlockNode() {
        generatorInfo.decreaseNumOfBlockNodes();
    }

    public GeneratorInfo getGeneratorInfo() {
        return generatorInfo.getImmutable();
    }

    public GeneratorInfo.Mutable getMutableGeneratorInfo() {
        return generatorInfo;
    }

    @Override
    protected StatementNode createResumableBlock(boolean canYield, StatementNode... statements) {
        if (canYield) {
            return GeneratorBlockNode.create(statements, generatorInfo);
        } else {
            return BlockNode.create(statements);
        }
    }

    @Override
    @SuppressWarnings("deprecation")    // new Frame API
    protected StatementNode createFrameReturn(ExpressionNode value, com.oracle.truffle.api.frame.FrameSlot slot) {
        return new GeneratorFrameReturnNode(value, slot);
    }

    @Override
    public PNode visit(BlockSSTNode node) {
        int oldNumber = generatorInfo.getNumOfActiveFlags();
        StatementNode[] statements = new StatementNode[node.statements.length];
        for (int i = 0; i < statements.length; i++) {
            statements[i] = (StatementNode) node.statements[i].accept(this);
        }
        if (statements.length == 1) {
            return BlockNode.create(statements);
        } else {
            return oldNumber != generatorInfo.getNumOfActiveFlags()
                            ? GeneratorBlockNode.create(statements, generatorInfo)
                            : BlockNode.create(statements);
        }
    }

    @Override
    public PNode visit(ForComprehensionSSTNode node) {
        GeneratorInfo.Mutable savedInfo = generatorInfo;
        generatorInfo = new GeneratorInfo.Mutable();
        ScopeInfo originScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.scope.getParent());
        parentVisitor.comprLevel++;
        ExpressionNode iterator = (ExpressionNode) node.iterator.accept(this);
        GetIteratorExpressionNode getIterator = GetIteratorExpressionNode.create(iterator);
        getIterator.assignSourceSection(iterator.getSourceSection());
        scopeEnvironment.setCurrentScope(node.scope);

        ExpressionNode targetExpression;
        if (node.resultType == PythonBuiltinClassType.PDict) {
            targetExpression = TupleLiteralNode.create((ExpressionNode) node.name.accept(this), (ExpressionNode) node.target.accept(parentVisitor));
        } else {
            targetExpression = (ExpressionNode) node.target.accept(parentVisitor);
        }
        parentVisitor.comprLevel--;
        YieldNode yieldExpression = new YieldNode(targetExpression, generatorInfo);
        yieldExpression.assignSourceSection(targetExpression.getSourceSection());

        StatementNode body = createGeneratorExpressionBody(node, getIterator, yieldExpression.asStatement());

        ExpressionNode returnTarget;

        if (body instanceof BlockNode) {
            returnTarget = new ReturnTargetNode(body, ReadLocalVariableNode.create(scopeEnvironment.getReturnSlot()));
        } else {
            returnTarget = new GeneratorReturnTargetNode(BlockNode.create(), body, ReadGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot()), generatorInfo);
        }

        // ExpressionNode returnTarget = new ReturnTargetNode(body,
        // nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        returnTarget.assignSourceSection(body.getSourceSection());

        // creating generator expression
        FrameDescriptor fd = node.scope.getFrameDescriptor();
        String name = node.scope.getScopeId();
        String qualname = node.scope.getQualname();
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(returnTarget.getSourceSection(), name, true, fd, returnTarget, scopeEnvironment.getExecutionCellSlots(), Signature.EMPTY, null);
        RootCallTarget callTarget = PythonUtils.getOrCreateCallTarget(funcRoot);
        GeneratorExpressionNode genExprDef = new GeneratorExpressionNode(name, qualname, callTarget, getIterator, fd, scopeEnvironment.getDefinitionCellSlots(),
                        scopeEnvironment.getExecutionCellSlots(), generatorInfo.getImmutable());
        genExprDef.setEnclosingFrameDescriptor(node.scope.getParent().getFrameDescriptor());
        genExprDef.assignSourceSection(funcRoot.getSourceSection());
        genExprDef.setEnclosingFrameGenerator(node.level != 0 || parentVisitor.comprLevel != 0 || node.scope.getParent().getScopeKind() == ScopeInfo.ScopeKind.Generator);
        PNode result;
        switch (node.resultType) {
            case PList:
                result = PythonCallNode.create(ReadGlobalOrBuiltinNode.create(BuiltinNames.LIST), new ExpressionNode[]{genExprDef}, ExpressionNode.EMPTY_ARRAY, null, null);
                result.assignSourceSection(createSourceSection(node.target.startOffset, node.endOffset));
                break;
            case PSet:
                result = PythonCallNode.create(ReadGlobalOrBuiltinNode.create(BuiltinNames.SET), new ExpressionNode[]{genExprDef}, ExpressionNode.EMPTY_ARRAY, null, null);
                result.assignSourceSection(createSourceSection(node.target.startOffset, node.endOffset));
                break;
            case PDict:
                result = PythonCallNode.create(ReadGlobalOrBuiltinNode.create(BuiltinNames.DICT), new ExpressionNode[]{genExprDef}, ExpressionNode.EMPTY_ARRAY, null, null);
                result.assignSourceSection(createSourceSection(node.name.startOffset, node.endOffset));
                break;
            default:
                result = genExprDef;
                break;
        }
        scopeEnvironment.setCurrentScope(originScope);
        generatorInfo = savedInfo;
        return result;
    }

    private StatementNode createGeneratorExpressionBody(ForComprehensionSSTNode node, GetIteratorExpressionNode iterator, StatementNode yield) {
        ExpressionNode condition = null;
        if (node.conditions != null && node.conditions.length > 0) {
            condition = (ExpressionNode) node.conditions[0].accept(this);
            for (int i = 1; i < node.conditions.length; i++) {
                condition = new AndNode(condition, (ExpressionNode) node.conditions[i].accept(this));
            }
        }
        StatementNode body = yield;
        if (node.innerFor != null) {
            // example: [(e,s) for e in (1,2,3) for s in (4, 5, 6))]
            ExpressionNode exprIterator = (ExpressionNode) node.innerFor.iterator.accept(this);
            GetIteratorExpressionNode getIterator = GetIteratorExpressionNode.create(exprIterator);
            getIterator.assignSourceSection(exprIterator.getSourceSection());
            body = createGeneratorExpressionBody(node.innerFor, getIterator, yield);
        }
        if (condition != null) {
            // TODO: Do we have to create empty block in the else branch?
            body = GeneratorIfNode.create(CoerceToBooleanNode.createIfTrueNode(condition), body, BlockNode.createEmptyBlock(), generatorInfo);
        }

        StatementNode variable;
        if (node.variables.length == 1) {
            // if (node.variables[0] instanceof VarLookupSSTNode) {
            // VarLookupSSTNode vln = (VarLookupSSTNode)node.variables[0];
            // FrameSlot slot = scopeEnvironment.getCurrentScope().findFrameSlot(vln.name);
            // variable = makeWriteNode(ReadGeneratorFrameVariableNode.create(slot));
            // } else {
            variable = makeWriteNode((ExpressionNode) node.variables[0].accept(this));
            // }
        } else {
            ExpressionNode[] variables = new ExpressionNode[node.variables.length];
            for (int i = 0; i < node.variables.length; i++) {
                variables[i] = (ExpressionNode) node.variables[i].accept(this);
            }
            variable = makeWriteNode(new TupleLiteralNode(variables));
        }
        body = GeneratorForNode.create((WriteNode) variable,
                        node.level == 0 ? ReadIndexedArgumentNode.create(0).asExpression() : iterator,
                        body, generatorInfo);
        body.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return body;
    }

    @Override
    public PNode visit(ForSSTNode node) {
        int oldNumOfActiveFlags = generatorInfo.getNumOfActiveFlags();
        ExpressionNode[] targets = new ExpressionNode[node.targets.length];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = (ExpressionNode) node.targets[i].accept(this);
        }
        PNode target;
        if (targets.length == 1) {
            target = targets[0];
            if (!(target instanceof ReadNode || target instanceof TupleLiteralNode || target instanceof ListLiteralNode)) {
                if (target instanceof StarredExpressionNode) {
                    throw PRaiseNode.raiseUncached(null, SyntaxError, ErrorMessages.STARRED_ASSIGMENT_MUST_BE_IN_LIST_OR_TUPLE);
                } else {
                    // TODO handle this???
                    // String text = ctx.getText();
                    // if (environment.isNonlocal(text)) {
                    // throw errors.raise(SyntaxError, "no binding for nonlocal variable \"%s\"
                    // found", text);
                    // }
                    throw PRaiseNode.raiseUncached(null, SyntaxError, ErrorMessages.CANNOT_ASSIGN_TO, target);
                }
            }
        } else {
            target = new TupleLiteralNode(targets);
        }
        StatementNode body = (StatementNode) node.body.accept(this);
        if (node.containsContinue) {
            body = new ContinueTargetNode(body);
        }
        ExpressionNode iterator = (ExpressionNode) node.iterator.accept(this);
        iterator.assignSourceSection(createSourceSection(node.iterator.startOffset, node.iterator.endOffset));
        GetIteratorExpressionNode getIterator = GetIteratorExpressionNode.create(iterator);
        getIterator.assignSourceSection(iterator.getSourceSection());
        StatementNode forNode = oldNumOfActiveFlags == generatorInfo.getNumOfActiveFlags()
                        ? new ForNode(body, makeWriteNode((ExpressionNode) target), getIterator)
                        : GeneratorForNode.create((WriteNode) makeWriteNode((ExpressionNode) target), getIterator, body, generatorInfo);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
            result = new ElseNode(forNode, elseBranch);
        } else {
            // TODO: this is also strange, that we create don't create ElseNode for break target.
            // At least it seems to be inconsistent.
            result = node.elseStatement == null ?
            // TODO: Do we need to create the empty block here?
                            new BreakTargetNode(forNode, BlockNode.createEmptyBlock()) : new BreakTargetNode(forNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(IfSSTNode node) {
        int oldNum = generatorInfo.getNumOfActiveFlags();
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        StatementNode thenStatement = (StatementNode) node.thenStatement.accept(this);
        // TODO: Do we need to generate empty else block, if doesn't exist? The execution check if
        // the else branch is empty anyway.
        StatementNode elseStatement = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode result = oldNum != generatorInfo.getNumOfActiveFlags()
                        ? GeneratorIfNode.create(FactorySSTVisitor.toBooleanCastNode(test), thenStatement, elseStatement, generatorInfo)
                        : new IfNode(FactorySSTVisitor.toBooleanCastNode(test), thenStatement, elseStatement);
        if (node.startOffset != -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }

    @Override
    public PNode visit(TrySSTNode node) {
        int oldNumber = generatorInfo.getNumOfActiveFlags();
        StatementNode body = (StatementNode) node.body.accept(this);
        StatementNode elseStatement = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode finalyStatement = node.finallyStatement == null ? null : (StatementNode) node.finallyStatement.accept(this);
        ExceptNode[] exceptNodes = new ExceptNode[node.exceptNodes.length];
        for (int i = 0; i < exceptNodes.length; i++) {
            ExceptSSTNode exceptNode = node.exceptNodes[i];
            ExpressionNode exceptTest = exceptNode.test != null ? (ExpressionNode) exceptNode.test.accept(this) : null;
            StatementNode exceptBody = (StatementNode) exceptNode.body.accept(this);
            WriteNode exceptName = null;
            StatementNode exceptNameDelete = null;
            if (exceptNode.asName != null) {
                ReadNode readAsNode = scopeEnvironment.findVariable(exceptNode.asName);
                exceptName = (WriteNode) readAsNode.makeWriteNode(null);
                exceptNameDelete = unbindVariable(readAsNode);
            }
            exceptNodes[i] = new ExceptNode(exceptBody, exceptTest, exceptName, exceptNameDelete);
        }

        StatementNode result;
        if (oldNumber == generatorInfo.getNumOfActiveFlags()) {
            result = new TryFinallyNode(new TryExceptNode(body, exceptNodes, elseStatement), finalyStatement);
        } else {
            result = new GeneratorTryExceptNode(body, exceptNodes, elseStatement, generatorInfo);
            result = new GeneratorTryFinallyNode(result, finalyStatement, generatorInfo);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(WithSSTNode node) {
        int oldNumOfActiveFlags = generatorInfo.getNumOfActiveFlags();
        StatementNode body = (StatementNode) node.body.accept(this);
        WriteNode asName = node.target == null ? null : (WriteNode) makeWriteNode((ExpressionNode) node.target.accept(this));
        ExpressionNode expression = (ExpressionNode) node.expression.accept(this);
        PNode result = oldNumOfActiveFlags != generatorInfo.getNumOfActiveFlags()
                        // if the body contains yield -> create Generator control node.
                        ? new GeneratorWithNode(asName, body, expression, generatorInfo)
                        : WithNode.create(expression, asName, body);
        if (node.startOffset > -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }

    @Override
    public PNode visit(WhileSSTNode node) {
        int oldNumber = generatorInfo.getNumOfActiveFlags();
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        StatementNode body = (StatementNode) node.body.accept(this);
        if (node.containsContinue) {
            body = new ContinueTargetNode(body);
        }
        StatementNode whileNode = oldNumber != generatorInfo.getNumOfActiveFlags()
                        ? new GeneratorWhileNode(FactorySSTVisitor.toBooleanCastNode(test), body, generatorInfo)
                        : new WhileNode(FactorySSTVisitor.toBooleanCastNode(test), body);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
            result = new ElseNode(whileNode, elseBranch);
        } else if (oldNumber == generatorInfo.getNumOfActiveFlags()) {
            result = node.elseStatement == null ?
            // TODO: Do we need to create the empty block here?
                            new BreakTargetNode(whileNode, BlockNode.createEmptyBlock()) : new BreakTargetNode(whileNode, elseBranch);
        } else {
            result = node.elseStatement == null ? whileNode : new ElseNode(whileNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(YieldExpressionSSTNode node) {
        ExpressionNode value = node.value != null ? (ExpressionNode) node.value.accept(this) : EmptyNode.create();
        ExpressionNode result;
        if (node.isFrom) {
            result = new YieldFromNode(value, generatorInfo);
        } else {
            result = new YieldNode(value, generatorInfo);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    protected int getCurrentNumberOfYields() {
        return generatorInfo.getYieldNodes().size();
    }

    @Override
    protected ExpressionNode createResumableExpression(ExpressionNode left, ExpressionNode right, boolean rightCanYield, BiFunction<ExpressionNode, ExpressionNode, ExpressionNode> create) {
        if (rightCanYield) {
            // Make sure that left is computed only once even if right yields
            ReadNode readLeft = makeTempLocalVariable();
            StatementNode writeLeft = readLeft.makeWriteNode(left);
            ExpressionNode operation = create.apply((ExpressionNode) readLeft, right);
            return GeneratorExpressionWithSideEffects.create(operation, new StatementNode[]{writeLeft}, generatorInfo);
        } else {
            return create.apply(left, right);
        }
    }

    @Override
    protected ExpressionNode createResumableExpression(ExpressionNode[] nodes, boolean canYield, Function<ExpressionNode[], ExpressionNode> create) {
        if (canYield) {
            StatementNode[] sideEffects = new StatementNode[nodes.length];
            ExpressionNode[] exprs = new ExpressionNode[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                ReadNode read = makeTempLocalVariable();
                StatementNode write = read.makeWriteNode(nodes[i]);
                sideEffects[i] = write;
                exprs[i] = (ExpressionNode) read;
            }
            return GeneratorExpressionWithSideEffects.create(create.apply(exprs), sideEffects, generatorInfo);
        } else {
            return create.apply(nodes);
        }
    }

    @Override
    protected ExpressionNode createResumableExpression(ExpressionNode[] nodes1, ExpressionNode[] nodes2, boolean canYield, BiFunction<ExpressionNode[], ExpressionNode[], ExpressionNode> create) {
        if (canYield) {
            StatementNode[] sideEffects = new StatementNode[nodes1.length + nodes2.length];
            ExpressionNode[] exprs1 = new ExpressionNode[nodes1.length];
            for (int i = 0; i < nodes1.length; i++) {
                ReadNode read = makeTempLocalVariable();
                StatementNode write = read.makeWriteNode(nodes1[i]);
                sideEffects[i] = write;
                exprs1[i] = (ExpressionNode) read;
            }
            ExpressionNode[] exprs2 = new ExpressionNode[nodes2.length];
            for (int i = 0; i < nodes2.length; i++) {
                ReadNode read = makeTempLocalVariable();
                StatementNode write = read.makeWriteNode(nodes2[i]);
                sideEffects[nodes1.length + i] = write;
                exprs2[i] = (ExpressionNode) read;
            }
            return GeneratorExpressionWithSideEffects.create(create.apply(exprs1, exprs2), sideEffects, generatorInfo);
        } else {
            return create.apply(nodes1, nodes2);
        }
    }
}
