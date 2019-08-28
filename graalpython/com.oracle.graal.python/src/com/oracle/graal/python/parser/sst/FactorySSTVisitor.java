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
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.function.Signature;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILD_CLASS__;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.parser.*;
import com.oracle.graal.python.nodes.PNode;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.classes.ClassDefinitionPrologueNode;
import com.oracle.graal.python.nodes.control.BaseBlockNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.ReturnNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.TEMP_LOCAL_PREFIX;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.nodes.generator.GeneratorBlockNode;
import com.oracle.graal.python.nodes.generator.GeneratorReturnTargetNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.LiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.SetLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssertNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.graal.python.runtime.PythonParser;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FactorySSTVisitor implements SSTreeVisitor<PNode> {

    private static final ExpressionNode EMPTY_DOC = new com.oracle.graal.python.nodes.literal.StringLiteralNode("");

    protected final ScopeEnvironment scopeEnvironment;
    protected final Source source;
    protected final NodeFactory nodeFactory;
    protected final PythonParser.ParserErrorCallback errors;

    protected int comprLevel;

    public FactorySSTVisitor(PythonParser.ParserErrorCallback errors, ScopeEnvironment scopeEnvironment, NodeFactory nodeFactory, Source source) {
        this.scopeEnvironment = scopeEnvironment;
        this.source = source;
        this.nodeFactory = nodeFactory;
        this.errors = errors;
        this.comprLevel = 0;
    }

    public ExpressionNode asExpression(BlockSSTNode block) {
        if (block.statements.length == 0) {
            EmptyNode empty = EmptyNode.create();
            empty.assignSourceSection(createSourceSection(0, 0));
            return empty;
        }

        StatementNode[] statements = new StatementNode[block.statements.length];
        for (int i = 0; i < statements.length; i++) {
            statements[i] = (StatementNode) block.statements[i].accept(this);
        }

        int len = statements.length;
        ExpressionNode result;
        if (len == 1) {
            result = asExpression(statements[0]);
            result.assignSourceSection(createSourceSection(block.startOffset, block.endOffset));
        } else {
            // two and more
            ExpressionNode en = asExpression(statements[len - 1]);
            if (len == 2) {
                result = en.withSideEffect(statements[0]);
            } else {
                result = en.withSideEffect(Arrays.copyOf(statements, len - 1));
            }
        }
        result.assignSourceSection(createSourceSection(block.startOffset, block.endOffset));
        return result;
    }

    public ExpressionNode asExpression(PNode node) {
        if (node instanceof ExpressionNode.ExpressionStatementNode) {
            return ((ExpressionNode.ExpressionStatementNode) node).getExpression();
        }
        if (node instanceof ExpressionNode) {
            return (ExpressionNode) node;
        }
        if (node instanceof StatementNode) {
            ExpressionNode emptyNode = EmptyNode.create().withSideEffect((StatementNode) node);
            return emptyNode;
        }
        if (node == null) {
            return EmptyNode.create();
        } else {
            throw new IllegalArgumentException("unexpected class: " + node.getClass());
        }
    }

    protected StatementNode createAssignmentBlock(@SuppressWarnings("unused") AssignmentSSTNode node, StatementNode... statements) {
        return BlockNode.create(statements);
    }

    protected StatementNode createWriteLocal(ExpressionNode right, FrameSlot slot) {
        assert slot != null;
        return WriteLocalVariableNode.create(slot, right);
    }

    @Override
    public PNode visit(AndSSTNode node) {
        ExpressionNode last = (ExpressionNode) node.values[0].accept(this);
        for (int i = 1; i < node.values.length; i++) {
            last = new AndNode(last, (ExpressionNode) node.values[i].accept(this));
        }
        last.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return last;
    }

    @Override
    public PNode visit(AnnAssignmentSSTNode node) {
        // TODO should be done type checking here?
        return visit((AssignmentSSTNode) node);
    }

    @Override
    public PNode visit(AssertSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        ExpressionNode message = node.message == null ? null : (ExpressionNode) node.message.accept(this);
        PNode result = new AssertNode(nodeFactory.toBooleanCastNode(test), message);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(AssignmentSSTNode node) {
        ExpressionNode[] lhs = new ExpressionNode[node.lhs.length];
        for (int i = 0; i < node.lhs.length; i++) {
            lhs[i] = (ExpressionNode) node.lhs[i].accept(this);
        }
        ExpressionNode rhs = (ExpressionNode) node.rhs.accept(this);

        StatementNode result;
        if (lhs.length == 1) {
            result = createAssignment(lhs[0], rhs);
        } else {
            int len = lhs.length;
            StatementNode[] assignments = new StatementNode[len + 1];
            ReadNode tmp = makeTempLocalVariable();
            StatementNode tmpWrite = tmp.makeWriteNode(rhs);
            assignments[0] = tmpWrite;
            for (int i = 0; i < len; i++) {
                assignments[i + 1] = createAssignment(lhs[i], (ExpressionNode) tmp);
            }
            result = createAssignmentBlock(node, assignments);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(AugAssignmentSSTNode node) {
        ExpressionNode lhs = (ExpressionNode) node.lhs.accept(this);
        if (!(lhs instanceof ReadNode)) {
            throw errors.raise(SyntaxError, "illegal expression for augmented assignment");
        }
        ExpressionNode rhs = (ExpressionNode) node.rhs.accept(this);
        ExpressionNode binOp = nodeFactory.createInplaceOperation(node.operation, lhs, rhs);
        PNode duplicate = nodeFactory.duplicate(lhs, PNode.class);
        PNode result = ((ReadNode) duplicate).makeWriteNode(binOp);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(BinaryArithmeticSSTNode node) {
        ExpressionNode left = (ExpressionNode) node.left.accept(this);
        ExpressionNode right = (ExpressionNode) node.right.accept(this);
        ExpressionNode result = node.operation.create(left, right);
        // TODO the old parser assing ss only for the first. See parser test assignment03
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(BlockSSTNode node) {
        StatementNode[] statements = new StatementNode[node.statements.length];
        for (int i = 0; i < statements.length; i++) {
            statements[i] = (StatementNode) node.statements[i].accept(this);
        }
        return BlockNode.create(statements);
    }

    @Override
    public PNode visit(BooleanLiteralSSTNode node) {
        ExpressionNode result = new com.oracle.graal.python.nodes.literal.BooleanLiteralNode(node.value);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(CallSSTNode node) {
        ExpressionNode target = (ExpressionNode) node.target.accept(this);

        ArgListBuilder argBuilder = node.parameters;
        ExpressionNode callNode = PythonCallNode.create(target, argBuilder.getArgs(this), argBuilder.getNameArgs(this), argBuilder.getStarArgs(this), argBuilder.getKwArgs(this));
        // remove source section for the taget to be comaptiable with old parser behavior
        // TODO check, whether we really need to delete the source sections
        target.assignSourceSection(null);
        callNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return callNode;
    }

    private static String getQualifiedName(ScopeInfo scope, String name) {
        StringBuilder qualifiedName = new StringBuilder(name);
        ScopeInfo tmpScope = scope.getParent();
        while (tmpScope != null) {
            switch (tmpScope.getScopeKind()) {
                case Function:
                case Generator:
                    qualifiedName.insert(0, ".<locals>.");
                    qualifiedName.insert(0, tmpScope.getScopeId());
                    tmpScope = tmpScope.getParent();
                    break;
                case Class:
                    qualifiedName.insert(0, '.');
                    qualifiedName.insert(0, tmpScope.getScopeId());
                    tmpScope = tmpScope.getParent();
                    break;
                case Module:
                    tmpScope = null;
                    break;
            }

        }
        return qualifiedName.toString();
    }

    @Override
    public PNode visit(ClassSSTNode node) {
        ScopeInfo classScope = node.classScope;
        scopeEnvironment.setCurrentScope(classScope);
        String qualifiedName = getQualifiedName(classScope, node.name);

        // 1) create a cellvar in the class body (__class__), the class itself is stored here
        classScope.addCellVar(__CLASS__, true);
        // 2) all class methods receive a __class__ freevar
        ScopeInfo childScope = classScope.getFirstChildScope();
        while (childScope != null) {
            if (childScope.getScopeKind() == ScopeKind.Function || childScope.getScopeKind() == ScopeKind.Generator) {
                childScope.addFreeVar(__CLASS__, true);
            }
            childScope = childScope.getNextChildScope();
        }

        SSTNode[] bodyNodes = ((BlockSSTNode) node.body).statements;

        StatementNode[] bodyStatements = new StatementNode[bodyNodes.length + 1];
        bodyStatements[0] = new ClassDefinitionPrologueNode(qualifiedName);
        for (int i = 0; i < bodyNodes.length; i++) {
            bodyStatements[i + 1] = (StatementNode) bodyNodes[i].accept(this);
        }
        ExpressionNode doc = StringUtils.extractDoc(bodyStatements[1]);
        if (doc != null) {
            scopeEnvironment.createLocal(__DOC__);
            bodyStatements[1] = scopeEnvironment.findVariable(__DOC__).makeWriteNode(doc);
        }

        SourceSection nodeSourceSection = createSourceSection(node.startOffset, node.endOffset);
        StatementNode body = nodeFactory.createBlock(bodyStatements);
        ExpressionNode bodyAsExpr = new ReturnTargetNode(body, nodeFactory.createNullLiteral());
        ClassBodyRootNode classBodyRoot = nodeFactory.createClassBodyRoot(nodeSourceSection, node.name, scopeEnvironment.getCurrentFrame(), bodyAsExpr, scopeEnvironment.getExecutionCellSlots());
        RootCallTarget ct = Truffle.getRuntime().createCallTarget(classBodyRoot);
        FunctionDefinitionNode funcDef = new FunctionDefinitionNode(node.name, null, null, null, null, ct, scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots());
        scopeEnvironment.setCurrentScope(node.classScope.getParent());

        ExpressionNode[] args;
        ExpressionNode[] nameArgs;
        ExpressionNode starArg = null;
        ExpressionNode kwArg = null;
        if (node.baseClasses != null) {
            ExpressionNode[] sstArgs = node.baseClasses.getArgs(this);
            args = new ExpressionNode[sstArgs.length + 2];
            for (int i = 0; i < sstArgs.length; i++) {
                args[i + 2] = sstArgs[i];
            }
            nameArgs = node.baseClasses.getNameArgs(this);
            starArg = node.baseClasses.getStarArgs(this);
            kwArg = node.baseClasses.getKwArgs(this);
        } else {
            args = new ExpressionNode[2];
            nameArgs = new ExpressionNode[0];
        }
        args[0] = funcDef;
        args[1] = nodeFactory.createStringLiteral(node.name);

        ExpressionNode owner = nodeFactory.createGetAttribute(nodeFactory.createBuiltinsLiteral(), __BUILD_CLASS__);
        ExpressionNode classDef = PythonCallNode.create(owner, args, nameArgs, starArg, kwArg);
        classDef.assignSourceSection(nodeSourceSection);

        ReadNode read = scopeEnvironment.findVariable(node.name);

        ReadNode tempLocal = makeTempLocalVariable();
        ExpressionNode newClass = ((ExpressionNode) tempLocal).withSideEffect(
                        nodeFactory.createBlock(tempLocal.makeWriteNode(classDef),
                                        nodeFactory.createWriteCellVar((ExpressionNode) tempLocal, classBodyRoot, __CLASS__)));
        PNode result = read.makeWriteNode(newClass);
        result.assignSourceSection(nodeSourceSection);
        return result;
    }

    @Override
    public PNode visit(CollectionSSTNode node) {
        PNode result = null;
        switch (node.type) {
            case PTuple:
                result = nodeFactory.createTupleLiteral(getCollectionItems(node.values));
                break;
            case PList:
                result = nodeFactory.createListLiteral(getCollectionItems(node.values));
                break;
            case PSet:
                result = new SetLiteralNode(getCollectionItems(node.values));
                break;
            case PDict:
                if (node.values.length == 0) {
                    result = nodeFactory.createDictLiteral();
                } else {
                    int initLen = node.values.length / 2;
                    List<ExpressionNode> keys = new ArrayList<>(initLen);
                    List<ExpressionNode> values = new ArrayList<>(initLen);
                    List<ExpressionNode> dicts = new ArrayList<>();
                    for (int i = 0; i < node.values.length; i++) {
                        if (node.values[i] != null) {
                            keys.add((ExpressionNode) node.values[i].accept(this));
                            values.add((ExpressionNode) node.values[++i].accept(this));
                        } else {
                            if (!keys.isEmpty()) {
                                dicts.add(nodeFactory.createDictLiteral(keys, values));
                                keys.clear();
                                values.clear();
                            } else {
                                if (i == 0) {
                                    // TODO : Do we need to create empty dict?
                                    // see the test DictAndSetTests.dict07 and other test below
                                    dicts.add(nodeFactory.createDictLiteral());
                                }
                            }
                            dicts.add((ExpressionNode) node.values[++i].accept(this));
                        }
                    }
                    if (dicts.isEmpty()) {
                        result = nodeFactory.createDictLiteral(keys, values);
                    } else {
                        if (!keys.isEmpty()) {
                            dicts.add(nodeFactory.createDictLiteral(keys, values));
                        }
                        result = nodeFactory.createDictionaryConcat(dicts.toArray(new ExpressionNode[dicts.size()]));
                    }

                }

        }
        if (node.startOffset >= 0 && node.endOffset >= 0) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }

    private ExpressionNode[] getCollectionItems(SSTNode[] values) {
        ExpressionNode[] expressions = new ExpressionNode[values.length];
        for (int i = 0; i < expressions.length; i++) {
            expressions[i] = (ExpressionNode) values[i].accept(this);
        }
        return expressions;
    }

    @Override
    public PNode visit(ComparisonSSTNode node) {
        String operator;
        ExpressionNode left = (ExpressionNode) node.firstValue.accept(this);
        ExpressionNode right;
        ExpressionNode result = null;
        int opLen = node.operations.length;
        for (int i = 0; i < opLen; i++) {
            operator = node.operations[i];
            right = (ExpressionNode) node.otherValues[i].accept(this);
            ExpressionNode nextComp;
            if (right instanceof LiteralNode || right instanceof ReadNode || i == opLen - 1) {
                nextComp = nodeFactory.createComparisonOperation(operator, left, right);
                left = right;
            } else {
                ReadNode tmpVar = makeTempLocalVariable();
                StatementNode tmpAssignment = tmpVar.makeWriteNode(right);
                nextComp = nodeFactory.createComparisonOperation(operator, left, (ExpressionNode) tmpVar).withSideEffect(tmpAssignment);
                left = (ExpressionNode) tmpVar;
            }
            result = result == null ? nextComp : new AndNode(result, nextComp);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(DecoratedSSTNode node) {
        String definitionName;
        if (node.decorated instanceof ClassSSTNode) {
            definitionName = ((ClassSSTNode) node.decorated).name;
            scopeEnvironment.setCurrentScope(((ClassSSTNode) node.decorated).classScope.getParent());
        } else {
            definitionName = ((FunctionDefSSTNode) node.decorated).name;
            scopeEnvironment.setCurrentScope(((FunctionDefSSTNode) node.decorated).functionScope.getParent());
        }
        PNode[] decorators = new PNode[node.decorators.length];
        for (int i = 0; i < decorators.length; i++) {
            // the list has to be reverted
            decorators[i] = node.decorators[decorators.length - i - 1].accept(this);
        }
        PNode decorated = node.decorated.accept(this);
        ExpressionNode definition = null;
        if (decorated instanceof WriteNode) {
            // TODO: should we split creating FunctionDefinitionNode and WriteNode for the function?
            definition = ((WriteNode) decorated).getRhs();
        } else if (decorated instanceof ExpressionNode) {
            definition = (ExpressionNode) decorated;
        }

        for (PNode decorator : decorators) {
            definition = PythonCallNode.create((ExpressionNode) decorator, new ExpressionNode[]{definition}, new ExpressionNode[]{}, null, null);
            definition.assignSourceSection(decorator.getSourceSection());
        }
        PNode result = scopeEnvironment.findVariable(definitionName).makeWriteNode(definition);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(DecoratorSSTNode node) {
        String dottedName = node.name;
        ExpressionNode decoratorFn;
        if (dottedName.contains(".")) {
            String[] nameParts = dottedName.split("\\.");
            decoratorFn = (ExpressionNode) scopeEnvironment.findVariable(nameParts[0]);
            for (int i = 1; i < nameParts.length; i++) {
                decoratorFn = nodeFactory.createGetAttribute(decoratorFn, nameParts[i]);
            }
        } else {
            decoratorFn = (ExpressionNode) scopeEnvironment.findVariable(dottedName);
        }

        if (node.arg != null) {
            decoratorFn = PythonCallNode.create(decoratorFn, node.arg.getArgs(this), node.arg.getNameArgs(this), node.arg.getStarArgs(this), node.arg.getKwArgs(this));
        }
        decoratorFn.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return decoratorFn;
    }

    @Override
    public PNode visit(DelSSTNode node) {
        List<StatementNode> blockList = new ArrayList<>();
        for (int i = 0; i < node.expressions.length; i++) {
            delTarget(blockList, node.expressions[i].accept(this));
        }
        PNode result = nodeFactory.createBlock(blockList);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    private void delTarget(List<StatementNode> blockList, PNode target) {
        if (target instanceof GetItemNode) {
            GetItemNode getItem = (GetItemNode) target;
            blockList.add(nodeFactory.createDeleteItem(getItem.getPrimary(), getItem.getSlice()));
        } else if (target instanceof GetAttributeNode) {
            GetAttributeNode getAttribute = (GetAttributeNode) target;
            blockList.add(nodeFactory.createDeleteAttribute(getAttribute.getObject(), getAttribute.getKey()));
        } else if (target instanceof ReadLocalNode) {
            // this will raise an error, if the variable is not bound
            blockList.add(((ExpressionNode) target).asStatement());
            blockList.add(((ReadLocalNode) target).makeDeleteNode());
        } else if (target instanceof ReadGlobalOrBuiltinNode) {
            ReadGlobalOrBuiltinNode readGlobalOrBuiltin = (ReadGlobalOrBuiltinNode) target;
            blockList.add(nodeFactory.createDeleteGlobal(readGlobalOrBuiltin.getAttributeId()));
        } else if (target instanceof ReadNameNode) {
            ReadNameNode readName = (ReadNameNode) target;
            blockList.add(nodeFactory.createDeleteName(readName.getAttributeId()));
        } else if (target instanceof TupleLiteralNode) {
            for (PNode targetValue : ((TupleLiteralNode) target).getValues()) {
                delTarget(blockList, targetValue);
            }
        } else {
            throw errors.raiseInvalidSyntax(target.getSourceSection().getSource(), target.getSourceSection(), "can't delete '%s'", target.getSourceSection().getCharacters());
        }
    }

    @Override
    public PNode visit(ExceptSSTNode node) {
        // we shouldn't visit this node. It should be processed in try
        return null;
    }

    @Override
    public PNode visit(ExpressionStatementSSTNode node) {
        ExpressionNode expression = (ExpressionNode) node.expression.accept(this);
        return expression.asStatement();
    }

    @Override
    public PNode visit(FloatLiteralSSTNode node) {
        ExpressionNode result;
        if (node.imaginary) {
            double imag = Double.parseDouble(node.value.substring(0, node.value.length() - 1));
            result = new ComplexLiteralNode(new PComplex(PythonBuiltinClassType.PComplex, 0, imag));
        } else {
            result = new DoubleLiteralNode(Double.parseDouble(node.value));
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(ForComprehensionSSTNode node) {
        ScopeInfo oldScope = scopeEnvironment.getCurrentScope();
        GeneratorFactorySSTVisitor generatorVisitor = new GeneratorFactorySSTVisitor(errors, scopeEnvironment, nodeFactory, source, this);
        PNode result = node.accept(generatorVisitor);
        scopeEnvironment.setCurrentScope(oldScope);
        return result;
    }

    @Override
    public PNode visit(ForSSTNode node) {
        ExpressionNode[] targets = new ExpressionNode[node.targets.length];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = (ExpressionNode) node.targets[i].accept(this);
        }
        PNode target;
        if (targets.length == 1) {
            target = targets[0];
            if (!(target instanceof ReadNode || target instanceof TupleLiteralNode || target instanceof ListLiteralNode)) {
                if (target instanceof StarredExpressionNode) {
                    throw errors.raise(SyntaxError, "starred assignment target must be in a list or tuple");
                } else {
                    // TODO handle this???
                    // String text = ctx.getText();
                    // if (environment.isNonlocal(text)) {
                    // throw errors.raise(SyntaxError, "no binding for nonlocal variable \"%s\"
                    // found", text);
                    // }
                    throw errors.raise(SyntaxError, "Cannot assign to %s", target);
                }
            }
        } else {
            target = nodeFactory.createObjectLiteral(targets);
        }
        StatementNode body = (StatementNode) node.body.accept(this);
        if (node.containsContinue) {
            body = nodeFactory.createContinueTarget(body);
        }
        ExpressionNode iterator = (ExpressionNode) node.iterator.accept(this);
        iterator.assignSourceSection(createSourceSection(node.iterator.startOffset, node.iterator.endOffset));
        GetIteratorExpressionNode getIterator = nodeFactory.createGetIterator(iterator);
        getIterator.assignSourceSection(iterator.getSourceSection());
        StatementNode forNode = new ForNode(body, makeWriteNode((ExpressionNode) target), getIterator);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? nodeFactory.createBlock(new StatementNode[0]) : (StatementNode) node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
            result = nodeFactory.createElse(forNode, elseBranch);
        } else {
            // TODO: this is also strange, that we create don't create ElseNode for break target.
            // At least it seems to be inconsistent.
            result = node.elseStatement == null ?
            // TODO: Do we need to create the empty block here?
                            nodeFactory.createBreakTarget(forNode, nodeFactory.createBlock(new StatementNode[0])) : nodeFactory.createBreakTarget(forNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(FunctionDefSSTNode node) {
        ScopeInfo oldScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.functionScope);
        Signature signature = node.argBuilder.getSignature();
        StatementNode argumentNodes = nodeFactory.createBlock(node.argBuilder.getArgumentNodes());

        StatementNode body;
        GeneratorFactorySSTVisitor generatorFactory = null;
        if (scopeEnvironment.isInGeneratorScope()) {
            generatorFactory = new GeneratorFactorySSTVisitor(errors, scopeEnvironment, nodeFactory, source, this);
            body = (StatementNode) node.body.accept(generatorFactory);
        } else {
            body = (StatementNode) node.body.accept(this instanceof GeneratorFactorySSTVisitor
                            ? ((GeneratorFactorySSTVisitor) this).parentVisitor
                            : this);
        }
        ExpressionNode doc = StringUtils.extractDoc(body);
        if (doc != null) {
            if (body instanceof BaseBlockNode) {
                StatementNode[] st = ((BaseBlockNode) body).getStatements();
                if (st.length == 1) {
                    body = BlockNode.create();
                } else {
                    if (st.length == 2) {
                        body = st[1];
                        if (scopeEnvironment.isInGeneratorScope()) {
                            generatorFactory.decreaseNumOfGeneratorBlockNode();
                        }
                    } else {
                        // TODO this is not nice. We create the block twice. Should be created just
                        // one?
                        body = body instanceof GeneratorBlockNode
                                        ? GeneratorBlockNode.create(Arrays.copyOfRange(st, 1, st.length), ((GeneratorBlockNode) body).getIndexSlot())
                                        : BlockNode.create(Arrays.copyOfRange(st, 1, st.length));
                    }

                }
            } else {
                body = BlockNode.create();
            }
        }
        if (doc == null) {
            doc = EMPTY_DOC;
        }

        ExpressionNode funcDef;
        ExpressionNode returnTarget;

        if (scopeEnvironment.isInGeneratorScope()) {
            returnTarget = new GeneratorReturnTargetNode(argumentNodes, body, ReadGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot()), generatorFactory.getNextNumOfActiveFlags());
        } else {
            body = nodeFactory.createBlock(argumentNodes, body);
            returnTarget = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        }

        SourceSection sourceSection = createSourceSection(node.startOffset, node.endOffset);
        returnTarget.assignSourceSection(sourceSection);

        scopeEnvironment.setCurrentScope(node.functionScope.getParent());
        ExpressionNode[] defaults = node.argBuilder.getDefaultParameterValues(this);
        FunctionDefinitionNode.KwDefaultExpressionNode[] kwDefaults = node.argBuilder.getKwDefaultParameterValues(this);
        scopeEnvironment.setCurrentScope(node.functionScope);

        /**
         * Function root
         */
        FrameDescriptor fd = scopeEnvironment.getCurrentFrame();
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(sourceSection, node.name, scopeEnvironment.isInGeneratorScope(), fd, returnTarget, scopeEnvironment.getExecutionCellSlots(),
                        signature);
        RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);
        if (scopeEnvironment.isInGeneratorScope()) {
            funcDef = GeneratorFunctionDefinitionNode.create(node.name, node.enclosingClassName, doc, defaults, kwDefaults, ct, fd,
                            scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots(),
                            generatorFactory.getNumOfActiveFlags(), generatorFactory.getNumOfGeneratorBlockNode(), generatorFactory.getNumOfGeneratorForNode());
        } else {
            funcDef = new FunctionDefinitionNode(node.name, node.enclosingClassName, doc, defaults, kwDefaults, ct, scopeEnvironment.getDefinitionCellSlots(),
                            scopeEnvironment.getExecutionCellSlots());
        }
        scopeEnvironment.setCurrentScope(node.functionScope.getParent());
        ReadNode funcVar = scopeEnvironment.findVariable(node.name);
        StatementNode writeNode = funcVar.makeWriteNode(funcDef);
        // TODO I'm not sure, whether this assingning of sourcesection is right.
        writeNode.assignSourceSection(((FunctionDefinitionNode) funcDef).getFunctionRoot().getSourceSection());
        scopeEnvironment.setCurrentScope(oldScope);
        return writeNode;
    }

    @Override
    public PNode visit(GetAttributeSSTNode node) {
        ExpressionNode receiver = (ExpressionNode) node.receiver.accept(this);
        PNode result = nodeFactory.createGetAttribute(receiver, node.name);
        // TODO: the old parser doesn't assing source section to the reciever and the node as well.
        // Is this ok?
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(IfSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        StatementNode thenStatement = (StatementNode) node.thenStatement.accept(this);
        // TODO: Do we need to generate empty else block, if doesn't exist? The execution check if
        // the else branch is empty anyway.
        StatementNode elseStatement = node.elseStatement == null ? nodeFactory.createBlock(new StatementNode[0]) : (StatementNode) node.elseStatement.accept(this);
        StatementNode result = nodeFactory.createIf(nodeFactory.toBooleanCastNode(test), thenStatement, elseStatement);
        if (node.startOffset != -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }

    @Override
    public PNode visit(ImportFromSSTNode node) {
        scopeEnvironment.setCurrentScope(node.scope);
        String from = node.from;
        int level = 0;
        while (from.length() > level && from.charAt(level) == '.') {
            level++;
        }
        if (level > 0) {
            from = from.substring(level);
        }
        PNode result;
        if (node.asNames == null) {
            // star import
            if (!scopeEnvironment.atModuleLevel()) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), "import * only allowed at module level");
            }
            result = nodeFactory.createImportStar(from, level);
        } else {
            String[] fromList = new String[node.asNames.length];
            WriteNode[] readNodes = new WriteNode[fromList.length];
            for (int i = 0; i < fromList.length; i++) {
                String[] asName = node.asNames[i];
                fromList[i] = asName[0];
                readNodes[i] = (WriteNode) scopeEnvironment.findVariable(asName[1] == null ? asName[0] : asName[1]).makeWriteNode(EmptyNode.create());
            }
            result = nodeFactory.createImportFrom(from, fromList, readNodes, level);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(ImportSSTNode node) {
        scopeEnvironment.setCurrentScope(node.scope);
        ExpressionNode importNode = nodeFactory.createImport(node.name).asExpression();
        PNode result;
        int dotIndex = node.name.indexOf('.');
        if (node.asName == null) {
            String moduleName = dotIndex == -1 ? node.name : node.name.substring(0, dotIndex);
            result = scopeEnvironment.findVariable(moduleName).makeWriteNode(importNode);
        } else {
            if (dotIndex != -1) {
                String[] parts = node.name.split("\\.");
                for (int i = 1; i < parts.length; i++) {
                    importNode = nodeFactory.createGetAttribute(importNode, parts[i]);
                }
            }
            result = scopeEnvironment.findVariable(node.asName).makeWriteNode(importNode);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(LambdaSSTNode node) {

        String funcname = "anonymous";
        ScopeInfo oldScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.functionScope);
        // environment.setDefaultArgumentNodes(defaultArgs);
        // environment.setDefaultKwArgumentNodes(defaultKwArgs);
        /**
         * Parameters
         */
        // Args args = visitArgs(varargslist, defaultArgs, defaultKwArgs);
        StatementNode argumentLoads = nodeFactory.createBlock(node.args == null ? new StatementNode[0] : node.args.getArgumentNodes());
        Signature signature = node.args == null ? Signature.EMPTY : node.args.getSignature();

        /**
         * Lambda body
         */
        StatementNode frameReturn = nodeFactory.createFrameReturn(nodeFactory.createWriteLocal((ExpressionNode) node.body.accept(this), scopeEnvironment.getReturnSlot()));
        StatementNode body = nodeFactory.createBlock(argumentLoads, frameReturn);
        ReturnTargetNode returnTargetNode = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        returnTargetNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));

        /**
         * Defaults
         */
        scopeEnvironment.setCurrentScope(node.functionScope.getParent());
        ExpressionNode[] defaults = node.args == null ? new ExpressionNode[0] : node.args.getDefaultParameterValues(this);
        FunctionDefinitionNode.KwDefaultExpressionNode[] kwDefaults = node.args == null ? new FunctionDefinitionNode.KwDefaultExpressionNode[0] : node.args.getKwDefaultParameterValues(this);
        scopeEnvironment.setCurrentScope(node.functionScope);

        /**
         * Lambda function root
         */
        FrameDescriptor fd = scopeEnvironment.getCurrentFrame();
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(returnTargetNode.getSourceSection(), funcname, scopeEnvironment.isInGeneratorScope(), fd, returnTargetNode,
                        scopeEnvironment.getExecutionCellSlots(), signature);

        /**
         * Definition
         */
        PNode funcDef;
        if (scopeEnvironment.isInGeneratorScope()) {
            GeneratorTranslator gtran = new GeneratorTranslator(funcRoot, false);
            RootCallTarget ct = gtran.translate();
            funcDef = GeneratorFunctionDefinitionNode.create(funcname, null, null, defaults, kwDefaults, ct, fd,
                            scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots(),
                            gtran.getNumOfActiveFlags(), gtran.getNumOfGeneratorBlockNode(), gtran.getNumOfGeneratorForNode());
        } else {
            RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);
            funcDef = new FunctionDefinitionNode(funcname, null, null, defaults, kwDefaults, ct, scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots());
            funcDef.assignSourceSection(returnTargetNode.getSourceSection());
        }
        scopeEnvironment.setCurrentScope(oldScope);
        return funcDef;
    }

    @Override
    public PNode visit(NotSSTNode node) {
        PNode result = CastToBooleanNode.createIfFalseNode((ExpressionNode) node.value.accept(this));
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(NumberLiteralSSTNode node) {
        final long max = node.isNegative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        final long moltmax = max / node.base;
        int i = node.start;
        long result = 0;
        int lastD;
        boolean overunder = false;
        while (i < node.value.length()) {
            lastD = digitValue(node.value.charAt(i));

            long next = result;
            if (next < moltmax) {
                overunder = true;
            } else {
                next *= node.base;
                if (next < (max + lastD)) {
                    overunder = true;
                } else {
                    next -= lastD;
                }
            }
            if (overunder) {
                // overflow
                BigInteger bigResult = BigInteger.valueOf(result);
                BigInteger bigBase = BigInteger.valueOf(node.base);
                while (i < node.value.length()) {
                    bigResult = bigResult.multiply(bigBase).subtract(BigInteger.valueOf(digitValue(node.value.charAt(i))));
                    i++;
                }
                if (!node.isNegative) {
                    bigResult = bigResult.negate();
                }
                PIntLiteralNode intLiteral = new PIntLiteralNode(bigResult);
                intLiteral.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                return intLiteral;
            }
            result = next;
            i++;
        }

        if (!node.isNegative) {
            result = -1 * result;
        }

        ExpressionNode intLiteral = Integer.MIN_VALUE <= result && result <= Integer.MAX_VALUE ? new IntegerLiteralNode((int) result) : new LongLiteralNode(result);
        intLiteral.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return intLiteral;
    }

    @Override
    public PNode visit(OrSSTNode node) {
        ExpressionNode last = (ExpressionNode) node.values[0].accept(this);
        for (int i = 1; i < node.values.length; i++) {
            last = new OrNode(last, (ExpressionNode) node.values[i].accept(this));
        }
        last.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return last;
    }

    @Override
    public PNode visit(RaiseSSTNode node) {
        // TODO: Do we need to create the empty nodes here?
        ExpressionNode value = node.value != null ? (ExpressionNode) node.value.accept(this) : EmptyNode.create();
        ExpressionNode from = node.from != null ? (ExpressionNode) node.from.accept(this) : EmptyNode.create();
        PNode result = RaiseNode.create(value, from);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(ReturnSSTNode node) {
        if (!scopeEnvironment.isInFunctionScope()) {
            errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), "'return' outside function");
        }
        StatementNode result;
        if (node.value != null) {
            result = new ReturnNode.FrameReturnNode(createWriteLocal((ExpressionNode) node.value.accept(this), scopeEnvironment.getReturnSlot()));
        } else {
            result = nodeFactory.createReturn();
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(SimpleSSTNode node) {
        PNode result = null;
        switch (node.type) {
            case BREAK:
                result = nodeFactory.createBreak();
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                break;
            case CONTINUE:
                result = nodeFactory.createContinue();
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                break;
            case PASS:
                EmptyNode emptyNode = EmptyNode.create();
                emptyNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                result = emptyNode.asStatement();
                break;
            case NONE:
                result = nodeFactory.createObjectLiteral(PNone.NONE);
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                break;
            case ELLIPSIS:
                result = nodeFactory.createObjectLiteral(PEllipsis.INSTANCE);
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                break;
            case EMPTY:
                EmptyNode empty = EmptyNode.create();
                empty.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                result = empty.asStatement();
                break;
        }
        return result;
    }

    @Override
    public PNode visit(SliceSSTNode node) {
        ExpressionNode lower = node.start == null ? EmptyNode.create() : (ExpressionNode) node.start.accept(this);
        ExpressionNode upper = node.stop == null ? EmptyNode.create() : (ExpressionNode) node.stop.accept(this);
        ExpressionNode stepValue = node.step == null ? EmptyNode.create() : (ExpressionNode) node.step.accept(this);
        PNode result = SliceLiteralNode.create(lower, upper, stepValue);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StarSSTNode node) {
        PNode result = StarredExpressionNode.create((ExpressionNode) node.value.accept(this));
        // TODO: find out, whether we really don't need the source section for this.
        // The old parser doesn't generater it.
        // result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode node) {
        PNode result = StringUtils.parseString(node.values, nodeFactory, errors);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(SubscriptSSTNode node) {
        ExpressionNode receiver = (ExpressionNode) node.receiver.accept(this);
        if (receiver instanceof ReadNode) {
            receiver.assignSourceSection(null);
        }
        ExpressionNode subscript = (ExpressionNode) node.subscript.accept(this);
        PNode result = nodeFactory.createSubscriptLoad(receiver, subscript);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(TernaryArithmeticSSTNode node) {
        ExpressionNode left = (ExpressionNode) node.left.accept(this);
        ExpressionNode right = (ExpressionNode) node.right.accept(this);
        ExpressionNode result = TernaryArithmetic.Pow.create(left, right);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(TernaryIfSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        ExpressionNode thenExpr = (ExpressionNode) node.thenStatement.accept(this);
        ExpressionNode elseExpr = (ExpressionNode) node.elseStatement.accept(this);
        PNode result = nodeFactory.createTernaryIf(nodeFactory.toBooleanCastNode(test), thenExpr, elseExpr);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(TrySSTNode node) {
        StatementNode body = (StatementNode) node.body.accept(this);
        StatementNode elseStatement = node.elseStatement == null ? nodeFactory.createBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode finalyStatement = node.finallyStatement == null ? null : (StatementNode) node.finallyStatement.accept(this);
        ExceptNode[] exceptNodes = new ExceptNode[node.exceptNodes.length];
        for (int i = 0; i < exceptNodes.length; i++) {
            ExceptSSTNode exceptNode = node.exceptNodes[i];
            ExpressionNode exceptTest = exceptNode.test != null ? (ExpressionNode) exceptNode.test.accept(this) : null;
            StatementNode exceptBody = (StatementNode) exceptNode.body.accept(this);
            WriteNode exceptName = exceptNode.asName != null ? (WriteNode) scopeEnvironment.findVariable(exceptNode.asName).makeWriteNode(null) : null;
            exceptNodes[i] = new ExceptNode(exceptBody, exceptTest, exceptName);
        }
        PNode result = nodeFactory.createTryExceptElseFinallyNode(body, exceptNodes, elseStatement, finalyStatement);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(UnarySSTNode node) {
        PNode result = node.arithmetic.create((ExpressionNode) node.value.accept(this));
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(VarLookupSSTNode node) {
        // ScopeInfo oldScope = scopeEnvironment.setCurrentScope(node.scope);
        PNode result = (PNode) scopeEnvironment.findVariable(node.name);
        if (result == null) {
            if (scopeEnvironment.isNonlocal(node.name)) {
                throw errors.raise(SyntaxError, "no binding for nonlocal variable \"%s\" found", node.name);
            }
            throw errors.raise(SyntaxError, "Cannot assign to %s", node.name);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        // scopeEnvironment.setCurrentScope(oldScope);
        return result;
    }

    @Override
    public PNode visit(WhileSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        StatementNode body = (StatementNode) node.body.accept(this);
        if (node.containsContinue) {
            body = nodeFactory.createContinueTarget(body);
        }
        StatementNode whileNode = nodeFactory.createWhile(nodeFactory.toBooleanCastNode(test), body);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? nodeFactory.createBlock(new StatementNode[0]) : (StatementNode) node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
            result = nodeFactory.createElse(whileNode, elseBranch);
        } else {
            // TODO: this is also strange, that we create don't create ElseNode for break target.
            // At least it seems to be inconsistent.
            result = node.elseStatement == null ?
            // TODO: Do we need to create the empty block here?
                            nodeFactory.createBreakTarget(whileNode, nodeFactory.createBlock(new StatementNode[0])) : nodeFactory.createBreakTarget(whileNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(WithSSTNode node) {
        StatementNode body = (StatementNode) node.body.accept(this);
        WriteNode asName = node.target == null ? null : (WriteNode) makeWriteNode((ExpressionNode) node.target.accept(this));
        ExpressionNode expression = (ExpressionNode) node.expression.accept(this);
        PNode result = nodeFactory.createWithNode(expression, asName, body);
        if (node.startOffset > -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }

    @Override
    public PNode visit(YieldExpressionSSTNode node) {
        ExpressionNode value = node.value == null ? EmptyNode.create() : (ExpressionNode) node.value.accept(this);
        PNode result = nodeFactory.createYield(value);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    private static int digitValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            assert ch >= 'A' && ch <= 'f';
            return ch - 'A' + 10;
        }
    }

    protected SourceSection createSourceSection(int start, int stop) {
        if (source.getLength() > start && source.getLength() >= stop) {
            return source.createSection(start, stop - start);
        } else {
            return source.createUnavailableSection();
        }
    }

    public ReadNode makeTempLocalVariable() {
        String tempName = TEMP_LOCAL_PREFIX + scopeEnvironment.getCurrentScope().getFrameDescriptor().getSize();
        FrameSlot tempSlot = scopeEnvironment.createAndReturnLocal(tempName);
        return scopeEnvironment.getCurrentScope().getScopeKind() != ScopeKind.Generator
                        ? ReadLocalVariableNode.create(tempSlot)
                        : ReadGeneratorFrameVariableNode.create(tempSlot);
    }

    protected StatementNode makeWriteNode(ExpressionNode accept) {
        StatementNode assignmentNode = createAssignment(accept, null);
        if (!(assignmentNode instanceof WriteNode)) {
            ReadNode tempLocal = makeTempLocalVariable();
            assignmentNode = createAssignment(accept, (ExpressionNode) tempLocal);
            return new com.oracle.graal.python.nodes.statement.AssignmentNode(assignmentNode, tempLocal.makeWriteNode(EmptyNode.create()));
        } else {
            return assignmentNode;
        }
    }

    private StatementNode createAssignment(ExpressionNode lhs, ExpressionNode rhs) {
        if (lhs instanceof ObjectLiteralNode) {
            return createDestructuringAssignment((ExpressionNode[]) ((ObjectLiteralNode) lhs).getObject(), rhs);
        } else if (lhs instanceof TupleLiteralNode) {
            return createDestructuringAssignment(((TupleLiteralNode) lhs).getValues(), rhs);
        } else if (lhs instanceof ListLiteralNode) {
            return createDestructuringAssignment(((ListLiteralNode) lhs).getValues(), rhs);
        } else {
            return ((ReadNode) lhs).makeWriteNode(rhs);
        }
    }

    private StatementNode createDestructuringAssignment(ExpressionNode[] leftHandSides, ExpressionNode rhs) {
        StatementNode[] statements = new StatementNode[leftHandSides.length];
        ReadNode[] temps = new ReadNode[leftHandSides.length];
        int starredIndex = -1;
        for (int i = 0; i < leftHandSides.length; i++) {
            ReadNode tempRead = makeTempLocalVariable();
            temps[i] = tempRead;
            if (leftHandSides[i] instanceof StarredExpressionNode) {
                if (starredIndex != -1) {
                    throw errors.raise(SyntaxError, "two starred expressions in assignment");
                }
                starredIndex = i;
                statements[i] = createAssignment(((StarredExpressionNode) leftHandSides[i]).getValue(), (ExpressionNode) tempRead);
            } else {
                statements[i] = createAssignment(leftHandSides[i], (ExpressionNode) tempRead);
            }
        }
        return nodeFactory.createDestructuringAssignment(rhs, temps, starredIndex, statements);
    }
}
