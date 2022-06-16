/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J_LAMBDA_NAME;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASSCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.NoValueNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.RootNodeFactory;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.classes.ClassDefinitionPrologueNode;
import com.oracle.graal.python.nodes.classes.LoadBuildClassNode;
import com.oracle.graal.python.nodes.control.BaseBlockNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.BreakNode;
import com.oracle.graal.python.nodes.control.BreakTargetNode;
import com.oracle.graal.python.nodes.control.ContinueNode;
import com.oracle.graal.python.nodes.control.ContinueTargetNode;
import com.oracle.graal.python.nodes.control.ElseNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.control.ReturnNode;
import com.oracle.graal.python.nodes.control.ReturnNode.FrameReturnNode;
import com.oracle.graal.python.nodes.control.ReturnNode.GeneratorFrameReturnNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.control.WhileNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.expression.TernaryIfNode;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.DeleteNameNode;
import com.oracle.graal.python.nodes.frame.DestructuringAssignmentNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionBodyNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.LambdaBodyNode;
import com.oracle.graal.python.nodes.generator.DictConcatNodeGen;
import com.oracle.graal.python.nodes.generator.GeneratorBlockNode;
import com.oracle.graal.python.nodes.generator.GeneratorReturnTargetNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.literal.BooleanLiteralNode;
import com.oracle.graal.python.nodes.literal.BytesLiteralNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DictLiteralNode;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.FormatStringExpressionNode;
import com.oracle.graal.python.nodes.literal.FormatStringLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.LiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.SetLiteralNodeGen;
import com.oracle.graal.python.nodes.literal.SimpleLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssertNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.ImportFromNode;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.nodes.statement.ImportStarNode;
import com.oracle.graal.python.nodes.statement.PrintExpressionNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.statement.WithNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.parser.DefinitionCellSlots;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.ScopeEnvironment;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.parser.sst.NumberLiteralSSTNode.BigIntegerLiteralSSTNode;
import com.oracle.graal.python.parser.sst.NumberLiteralSSTNode.IntegerLiteralSSTNode;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.util.BiFunction;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public class FactorySSTVisitor implements SSTreeVisitor<PNode> {

    protected final ScopeEnvironment scopeEnvironment;
    protected Source source;
    protected final RootNodeFactory nodeFactory;
    protected final PythonParser.ParserErrorCallback errors;

    protected int comprLevel;
    protected boolean interactive;

    private static final TruffleString RETURN = tsLiteral("return");

    public FactorySSTVisitor(PythonParser.ParserErrorCallback errors, ScopeEnvironment scopeEnvironment, RootNodeFactory nodeFactory, Source source, boolean interactive) {
        this.scopeEnvironment = scopeEnvironment;
        this.source = source;
        this.nodeFactory = nodeFactory;
        this.errors = errors;
        this.comprLevel = 0;
        this.interactive = interactive;
    }

    /**
     * Create a binary expression using given function such that it can be resumed when the right
     * subexpression yields without reexecuting the side-effects of the left subexpression
     *
     * @param left left expression
     * @param right right expression
     * @param rightCanYield whether the right expression can yield. Should be obtained using
     *            {@link #hadYieldSince(int)}
     * @param create function for creating the expression from left and right
     */
    protected ExpressionNode createResumableExpression(ExpressionNode left, ExpressionNode right, @SuppressWarnings("unused") boolean rightCanYield,
                    BiFunction<ExpressionNode, ExpressionNode, ExpressionNode> create) {
        return create.apply(left, right);
    }

    /**
     * Get current number of yields to be used later by {@link #hadYieldSince(int)}.
     */
    protected int getCurrentNumberOfYields() {
        return 0;
    }

    /**
     * Create an expression using given function such that it can be resumed when the one
     * subexpression yields without reexecuting the side-effects of the preceding subexpressions
     *
     * @param nodes subexpressions
     * @param canYield whether one of the expressions can yield. Should be obtained using
     *            {@link #hadYieldSince(int)}
     * @param create function for creating the expression from the nodes
     */
    protected ExpressionNode createResumableExpression(ExpressionNode[] nodes, @SuppressWarnings("unused") boolean canYield, Function<ExpressionNode[], ExpressionNode> create) {
        return create.apply(nodes);
    }

    /**
     * @see #createResumableExpression(ExpressionNode[], boolean, Function)
     */
    protected ExpressionNode createResumableExpression(ExpressionNode[] nodes1, ExpressionNode[] nodes2, @SuppressWarnings("unused") boolean canYield,
                    BiFunction<ExpressionNode[], ExpressionNode[], ExpressionNode> create) {
        return create.apply(nodes1, nodes2);
    }

    /**
     * Whether the visitor encountered a yield since the given number of yields
     *
     * @param oldNmberOfYields should be obtained using {@link #getCurrentNumberOfYields()} before
     *            visiting the expression which we want to detect yields in
     */
    protected boolean hadYieldSince(int oldNmberOfYields) {
        return getCurrentNumberOfYields() > oldNmberOfYields;
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

    public static ExpressionNode asExpression(PNode node) {
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

    protected StatementNode createResumableBlock(@SuppressWarnings("unused") boolean canYield, StatementNode... statements) {
        return BlockNode.create(statements);
    }

    protected StatementNode createFrameReturn(ExpressionNode right, int slot) {
        return new FrameReturnNode(right, slot);
    }

    @Override
    public PNode visit(AndSSTNode node) {
        ExpressionNode last = (ExpressionNode) node.values[0].accept(this);
        for (int i = 1; i < node.values.length; i++) {
            int numYields = getCurrentNumberOfYields();
            ExpressionNode right = (ExpressionNode) node.values[i].accept(this);
            last = createResumableExpression(last, right, hadYieldSince(numYields), AndNode::new);
        }
        last.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return last;
    }

    @Override
    public PNode visit(AnnAssignmentSSTNode node) {
        PNode assignmentNode = createAssignment(node);
        if (!scopeEnvironment.isInFunctionScope() && node.lhs.length == 1 && node.lhs[0] instanceof VarLookupSSTNode) {
            return BlockNode.create(new StatementNode[]{(StatementNode) assignmentNode, (StatementNode) node.annotation.accept(this)});
        }
        return assignmentNode;
    }

    @Override
    public PNode visit(AnnotationSSTNode node) {
        // annotations in a function we ignore at all. Even there are not evalueated, whether
        // the type is wrong
        if (!scopeEnvironment.isInFunctionScope() && node.type != null && node.lhs instanceof VarLookupSSTNode) {
            // create simple SST tree for : __annotations__['var_name'] = type
            VarLookupSSTNode varLookupNode = (VarLookupSSTNode) node.lhs;
            SubscriptSSTNode getAnnotationSST = new SubscriptSSTNode(new VarLookupSSTNode(J___ANNOTATIONS__, -1, -1), new StringLiteralSSTNode.RawStringLiteralSSTNode(varLookupNode.name, -1, -1), -1,
                            -1);
            AssignmentSSTNode assignAnnotationSST = new AssignmentSSTNode(new SSTNode[]{getAnnotationSST}, node.type, -1, -1);
            PNode assignAnnotationNode = visit(assignAnnotationSST);
            return assignAnnotationNode;
        }
        return EmptyNode.create().asStatement();
    }

    @Override
    public PNode visit(AssertSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        ExpressionNode message = node.message == null ? null : (ExpressionNode) node.message.accept(this);
        PNode result = new AssertNode(toBooleanCastNode(test), message);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(AssignmentSSTNode node) {
        return createAssignment(node);
    }

    private StatementNode createAssignment(AssignmentSSTNode node) {
        ExpressionNode[] lhs = new ExpressionNode[node.lhs.length];
        int numYields = getCurrentNumberOfYields();
        for (int i = 0; i < node.lhs.length; i++) {
            SSTNode sstLhs = node.lhs[i];
            lhs[i] = (ExpressionNode) sstLhs.accept(this);
        }
        ExpressionNode rhs = (ExpressionNode) node.rhs.accept(this);

        StatementNode result;
        if (lhs.length == 1 && !hadYieldSince(numYields)) {
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
            result = createResumableBlock(hadYieldSince(numYields), assignments);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(AugAssignmentSSTNode node) {
        ExpressionNode lhs = (ExpressionNode) node.lhs.accept(this);
        ExpressionNode rhs = (ExpressionNode) node.rhs.accept(this);
        ExpressionNode binOp = InplaceArithmetic.createInplaceOperation(node.operation, lhs, rhs);
        PNode duplicate = duplicate(lhs, PNode.class);
        PNode result = ((ReadNode) duplicate).makeWriteNode(binOp);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(BinaryArithmeticSSTNode node) {
        ExpressionNode left = (ExpressionNode) node.left.accept(this);
        int numYields = getCurrentNumberOfYields();
        ExpressionNode right = (ExpressionNode) node.right.accept(this);
        ExpressionNode result = createResumableExpression(left, right, hadYieldSince(numYields), node.operation::create);
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
        ExpressionNode result = new BooleanLiteralNode(node.value);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(CallSSTNode node) {
        ExpressionNode target = (ExpressionNode) node.target.accept(this);

        ArgListBuilder argBuilder = node.parameters;
        if (argBuilder.hasNakedForComp() && argBuilder.getArgs().length != 1) {
            SSTNode forComp = argBuilder.getNakedForComp();
            throw errors.raiseInvalidSyntax(source, createSourceSection(forComp.getStartOffset(), forComp.getEndOffset()), ErrorMessages.GENERATOR_EXPR_MUST_BE_PARENTHESIZED);
        }
        ExpressionNode callNode = PythonCallNode.create(target, argBuilder.getArgs(this), argBuilder.getNameArgs(this), argBuilder.getStarArgs(this), argBuilder.getKwArgs(this));
        callNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return callNode;
    }

    @Override
    public PNode visit(ClassSSTNode node) {
        ScopeInfo classScope = node.scope;
        scopeEnvironment.setCurrentScope(classScope);
        String qualifiedName = classScope.getQualname();

        int delta = 0;
        SSTNode[] bodyNodes = ((BlockSSTNode) node.body).statements;
        ExpressionNode doc = null;
        FunctionBodyNode classBody;
        int classBodyStart = node.body.startOffset;
        if (bodyNodes.length > 0) {
            // we need to extract documentaion, if there is from the class body
            StatementNode possibleDoc = (StatementNode) bodyNodes[0].accept(this);
            doc = StringUtils.extractDoc(possibleDoc);
            delta = doc != null ? 1 : 0;
            StatementNode[] bodyStatements = new StatementNode[bodyNodes.length - delta];
            if (doc == null) {
                bodyStatements[0] = possibleDoc;
            } else if (bodyNodes.length > 1) {
                classBodyStart = bodyNodes[1].startOffset;
            }
            for (int i = 1; i < bodyNodes.length; i++) {
                bodyStatements[i - delta] = (StatementNode) bodyNodes[i].accept(this);
            }
            classBody = FunctionBodyNode.create(bodyStatements);
        } else {
            classBody = FunctionBodyNode.create();
        }
        classBody.assignSourceSection(createSourceSection(classBodyStart, node.body.endOffset));

        delta = delta + (classScope.hasAnnotations() ? 1 : 0);
        boolean hasImplicitClass = classScope.isCellVar(J___CLASS__);
        StatementNode[] classStatements = new StatementNode[2 + delta + (hasImplicitClass ? 1 : 0)];
        // ClassStatemtns look like:
        // [0] ClassDefinitionPrologueNode
        classStatements[0] = new ClassDefinitionPrologueNode(ts(qualifiedName));
        // [?] if there is documentation -> doc statement
        if (doc != null) {
            scopeEnvironment.createLocal(J___DOC__);
            classStatements[1] = scopeEnvironment.findVariable(J___DOC__).makeWriteNode(doc);
        }
        // [?] if thre are annotations -> annotations
        if (classScope.hasAnnotations()) {
            classStatements[delta] = scopeEnvironment.findVariable(J___ANNOTATIONS__).makeWriteNode(DictLiteralNode.createEmptyDictLiteral());
        }
        // [last - 1] class body statements
        classStatements[1 + delta] = classBody;
        if (hasImplicitClass) {
            // [last] assign __class__ cell to __classcell__
            // Only if __class__ is generated in the class scope. The __class__ is in class scope,
            // when an inner method uses __class__ or super is used.
            classStatements[2 + delta] = scopeEnvironment.findVariable(J___CLASSCELL__).makeWriteNode(
                            ReadLocalVariableNode.create(scopeEnvironment.getCurrentScope().findFrameSlot(J___CLASS__)));
        }

        SourceSection nodeSourceSection = createSourceSection(node.startOffset, node.endOffset);
        StatementNode body = BlockNode.create(classStatements);
        ExpressionNode bodyAsExpr = new ReturnTargetNode(body, new ObjectLiteralNode(null));
        bodyAsExpr.assignSourceSection(nodeSourceSection);
        ExecutionCellSlots executionCellSlots = scopeEnvironment.getExecutionCellSlots();
        DefinitionCellSlots definitionCellSlots = scopeEnvironment.getDefinitionCellSlots();
        FrameDescriptor fd = scopeEnvironment.getCurrentScope().createFrameDescriptor();
        ClassBodyRootNode classBodyRoot = nodeFactory.createClassBodyRoot(nodeSourceSection, node.name, fd, bodyAsExpr, executionCellSlots);
        RootCallTarget ct = PythonUtils.getOrCreateCallTarget(classBodyRoot);
        FunctionDefinitionNode funcDef = new FunctionDefinitionNode(ts(node.name), ts(qualifiedName), null, null, null, null, ct, definitionCellSlots, null);
        scopeEnvironment.setCurrentScope(node.scope.getParent());

        ExpressionNode[] args;
        ExpressionNode[] nameArgs;
        ExpressionNode starArg = null;
        ExpressionNode kwArg = null;
        if (node.baseClasses != null) {
            if (node.baseClasses.hasNakedForComp()) {
                SSTNode forComp = node.baseClasses.getNakedForComp();
                throw errors.raiseInvalidSyntax(source, createSourceSection(forComp.getStartOffset(), forComp.getEndOffset()));
            }
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
            nameArgs = ExpressionNode.EMPTY_ARRAY;
        }
        args[0] = funcDef;
        args[1] = new StringLiteralNode(ts(node.name));

        ExpressionNode buildClass = new LoadBuildClassNode();
        ExpressionNode classDef = PythonCallNode.create(buildClass, args, nameArgs, starArg, kwArg);
        classDef.assignSourceSection(nodeSourceSection);

        ReadNode read = scopeEnvironment.findVariable(node.name);

        PNode result = read.makeWriteNode(classDef);
        result.assignSourceSection(nodeSourceSection);
        return result;
    }

    @Override
    public PNode visit(CollectionSSTNode node) {
        PNode result = null;
        int numYields;
        ExpressionNode[] items;
        switch (node.type) {
            case PTuple:
                numYields = getCurrentNumberOfYields();
                items = getCollectionItems(node.values);
                result = createResumableExpression(items, hadYieldSince(numYields), values1 -> new TupleLiteralNode(values1));
                break;
            case PList:
                numYields = getCurrentNumberOfYields();
                items = getCollectionItems(node.values);
                result = createResumableExpression(items, hadYieldSince(numYields), values1 -> ListLiteralNode.create(values1));
                break;
            case PSet:
                numYields = getCurrentNumberOfYields();
                items = getCollectionItems(node.values);
                result = createResumableExpression(items, hadYieldSince(numYields), values1 -> SetLiteralNodeGen.create(values1));
                break;
            case PDict:
                if (node.values.length == 0) {
                    result = DictLiteralNode.createEmptyDictLiteral();
                } else {
                    int initLen = node.values.length / 2;
                    List<ExpressionNode> keys = new ArrayList<>(initLen);
                    List<ExpressionNode> values = new ArrayList<>(initLen);
                    List<ExpressionNode> dicts = new ArrayList<>();
                    numYields = getCurrentNumberOfYields();
                    for (int i = 0; i < node.values.length; i++) {
                        if (node.values[i] != null) {
                            keys.add((ExpressionNode) node.values[i].accept(this));
                            values.add((ExpressionNode) node.values[++i].accept(this));
                        } else {
                            if (!keys.isEmpty()) {
                                dicts.add(createResumableExpression(keys.toArray(ExpressionNode.EMPTY_ARRAY), values.toArray(ExpressionNode.EMPTY_ARRAY), hadYieldSince(numYields),
                                                DictLiteralNode::create));
                                keys.clear();
                                values.clear();
                            } else {
                                if (i == 0) {
                                    // TODO : Do we need to create empty dict?
                                    // see the test DictAndSetTests.dict07 and other test below
                                    dicts.add(DictLiteralNode.createEmptyDictLiteral());
                                }
                            }
                            dicts.add((ExpressionNode) node.values[++i].accept(this));
                        }
                    }
                    if (dicts.isEmpty()) {
                        result = createResumableExpression(keys.toArray(ExpressionNode.EMPTY_ARRAY), values.toArray(ExpressionNode.EMPTY_ARRAY), hadYieldSince(numYields), DictLiteralNode::create);
                    } else {
                        if (!keys.isEmpty()) {
                            dicts.add(createResumableExpression(keys.toArray(ExpressionNode.EMPTY_ARRAY), values.toArray(ExpressionNode.EMPTY_ARRAY), hadYieldSince(numYields),
                                            DictLiteralNode::create));
                        }
                        result = createResumableExpression(dicts.toArray(ExpressionNode.EMPTY_ARRAY), hadYieldSince(numYields), dictNodes -> DictConcatNodeGen.create(dictNodes));
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
        ExpressionNode left = (ExpressionNode) node.firstValue.accept(this);
        ExpressionNode right;
        ExpressionNode result = null;
        int opLen = node.operations.length;
        for (int i = 0; i < opLen; i++) {
            String operator = node.operations[i];
            int numYields = getCurrentNumberOfYields();
            right = (ExpressionNode) node.otherValues[i].accept(this);
            boolean rightCanYield = hadYieldSince(numYields);
            ExpressionNode nextComp;
            if (right instanceof LiteralNode || right instanceof ReadNode || i == opLen - 1) {
                nextComp = createResumableExpression(left, right, rightCanYield, (l, r) -> ExpressionNode.createComparisonOperation(ts(operator), l, r));
                left = right;
            } else {
                ReadNode tmpVar = makeTempLocalVariable();
                StatementNode tmpAssignment = tmpVar.makeWriteNode(right);
                nextComp = createResumableExpression(left, (ExpressionNode) tmpVar, rightCanYield, (l, r) -> ExpressionNode.createComparisonOperation(ts(operator), l, r)).withSideEffect(
                                tmpAssignment);
                left = (ExpressionNode) tmpVar;
            }
            result = result == null ? nextComp : createResumableExpression(result, nextComp, rightCanYield, AndNode::new);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(DecoratedSSTNode node) {
        String definitionName;
        if (node.decorated instanceof ClassSSTNode) {
            definitionName = ((ClassSSTNode) node.decorated).name;
            scopeEnvironment.setCurrentScope(((ClassSSTNode) node.decorated).scope.getParent());
        } else {
            definitionName = ((FunctionDefSSTNode) node.decorated).name;
            scopeEnvironment.setCurrentScope(((FunctionDefSSTNode) node.decorated).scope.getParent());
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
                decoratorFn = GetAttributeNode.create(ts(nameParts[i]), decoratorFn);
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
        PNode result = BlockNode.create(blockList);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    private void delTarget(List<StatementNode> blockList, PNode target) {
        if (target instanceof GetItemNode) {
            GetItemNode getItem = (GetItemNode) target;
            blockList.add(DeleteItemNode.create(getItem.getPrimary(), getItem.getSlice()));
        } else if (target instanceof GetAttributeNode) {
            GetAttributeNode getAttribute = (GetAttributeNode) target;
            blockList.add(DeleteAttributeNode.create(getAttribute.getObject(), new StringLiteralNode(getAttribute.getKey())));
        } else if (target instanceof ReadLocalNode) {
            // this will raise an error, if the variable is not bound
            blockList.add(((ExpressionNode) target).asStatement());
            blockList.add(((ReadLocalNode) target).makeDeleteNode());
        } else if (target instanceof ReadGlobalOrBuiltinNode) {
            ReadGlobalOrBuiltinNode readGlobalOrBuiltin = (ReadGlobalOrBuiltinNode) target;
            blockList.add(DeleteGlobalNode.create(readGlobalOrBuiltin.getAttributeId()));
        } else if (target instanceof ReadNameNode) {
            ReadNameNode readName = (ReadNameNode) target;
            blockList.add(DeleteNameNode.create(readName.getAttributeId()));
        } else if (target instanceof TupleLiteralNode) {
            for (PNode targetValue : ((TupleLiteralNode) target).getValues()) {
                delTarget(blockList, targetValue);
            }
        } else {
            throw errors.raiseInvalidSyntax(target.getSourceSection().getSource(), target.getSourceSection(), ErrorMessages.CANT_DELETE, target.getSourceSection().getCharacters());
        }
    }

    protected StatementNode unbindVariable(ReadNode target) {
        if (target instanceof ReadLocalNode) {
            return ((ReadLocalNode) target).makeDeleteNode();
        } else if (target instanceof ReadGlobalOrBuiltinNode) {
            return DeleteGlobalNode.create(((ReadGlobalOrBuiltinNode) target).getAttributeId());
        } else if (target instanceof ReadNameNode) {
            return DeleteNameNode.create(((ReadNameNode) target).getAttributeId());
        }
        throw new IllegalStateException("invalid target for unbind");
    }

    @Override
    public PNode visit(ExceptSSTNode node) {
        // we shouldn't visit this node. It should be processed in try
        return null;
    }

    @Override
    public PNode visit(ExpressionStatementSSTNode node) {
        ExpressionNode expression = (ExpressionNode) node.expression.accept(this);
        if (interactive && scopeEnvironment.atModuleLevel()) {
            expression = PrintExpressionNode.create(expression);
        }
        return expression.asStatement();
    }

    @Override
    public PNode visit(FloatLiteralSSTNode node) {
        ExpressionNode result;
        if (node.imaginary) {
            result = new ComplexLiteralNode(0, node.value);
        } else {
            result = new DoubleLiteralNode(node.value);
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
        int iteratorSlot = scopeEnvironment.getCurrentScope().acquireScopedTemp();
        ExpressionNode[] targets = new ExpressionNode[node.targets.length];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = (ExpressionNode) node.targets[i].accept(this);
        }
        PNode target;
        if (targets.length == 1) {
            target = targets[0];
            if (!(target instanceof ReadNode || target instanceof TupleLiteralNode || target instanceof ListLiteralNode)) {
                if (target instanceof StarredExpressionNode) {
                    throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.STARRED_ASSIGMENT_MUST_BE_IN_LIST_OR_TUPLE);
                } else {
                    // TODO handle this???
                    // String text = ctx.getText();
                    // if (environment.isNonlocal(text)) {
                    // throw errors.raise(SyntaxError, "no binding for nonlocal variable \"%s\"
                    // found", text);
                    // }
                    throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.CANNOT_ASSIGN_TO, target);
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
        StatementNode forNode = new ForNode(body, makeWriteNode((ExpressionNode) target), getIterator, iteratorSlot);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? BlockNode.create(StatementNode.EMPTY_STATEMENT_ARRAY) : (StatementNode) node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
            result = new ElseNode(forNode, elseBranch);
        } else {
            // TODO: this is also strange, that we create don't create ElseNode for break target.
            // At least it seems to be inconsistent.
            result = node.elseStatement == null ?
            // TODO: Do we need to create the empty block here?
                            new BreakTargetNode(forNode, BlockNode.create(StatementNode.EMPTY_STATEMENT_ARRAY)) : new BreakTargetNode(forNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        scopeEnvironment.getCurrentScope().releaseScopedTemp();
        return result;
    }

    @Override
    public PNode visit(FunctionDefSSTNode node) {
        ScopeInfo oldScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.scope);
        Signature signature = node.argBuilder.getSignature();
        StatementNode argumentNodes = BlockNode.create(node.argBuilder.getArgumentNodes(scopeEnvironment));

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
        FunctionBodyNode functionBody;
        if (doc != null) {
            if (body instanceof BaseBlockNode) {
                StatementNode[] st = ((BaseBlockNode) body).getStatements();
                if (st.length == 1) {
                    functionBody = FunctionBodyNode.create();
                    functionBody.assignSourceSection(createSourceSection(node.endOffset - 1, node.endOffset));
                } else {
                    if (st.length == 2) {
                        functionBody = FunctionBodyNode.create(st[1]);
                        if (scopeEnvironment.isInGeneratorScope()) {
                            generatorFactory.decreaseNumOfGeneratorBlockNode();
                        }
                    } else {
                        // TODO this is not nice. We create the block twice. Should be created just
                        // one?
                        functionBody = body instanceof GeneratorBlockNode
                                        ? FunctionBodyNode.create(GeneratorBlockNode.create(Arrays.copyOfRange(st, 1, st.length), ((GeneratorBlockNode) body).getIndexSlot()))
                                        : FunctionBodyNode.create(Arrays.copyOfRange(st, 1, st.length));
                    }
                    BlockSSTNode blockSST = (BlockSSTNode) node.body;
                    int start = blockSST.statements[1].startOffset;
                    functionBody.assignSourceSection(createSourceSection(start, node.body.getEndOffset()));
                }
            } else {
                functionBody = FunctionBodyNode.create();
                functionBody.assignSourceSection(createSourceSection(node.endOffset - 1, node.endOffset));
            }
        } else {
            functionBody = createFunctionBody(body);
            functionBody.assignSourceSection(createSourceSection(node.body.startOffset, node.body.endOffset));
        }

        body = functionBody;
        ExpressionNode funcDef;
        ExpressionNode returnTarget;

        if (scopeEnvironment.isInGeneratorScope()) {
            returnTarget = new GeneratorReturnTargetNode(argumentNodes, body, ReadGeneratorFrameVariableNode.create(scopeEnvironment.getCurrentScope().getReturnSlot()),
                            generatorFactory.getMutableGeneratorInfo());
        } else {
            body = BlockNode.create(argumentNodes, body);
            returnTarget = new ReturnTargetNode(body, ReadLocalVariableNode.create(scopeEnvironment.getCurrentScope().getReturnSlot()));
        }

        SourceSection sourceSection = createSourceSection(node.startOffset, node.endOffset);
        returnTarget.assignSourceSection(sourceSection);

        scopeEnvironment.setCurrentScope(node.scope.getParent());
        ExpressionNode[] defaults = node.argBuilder.getDefaultParameterValues(this);
        FunctionDefinitionNode.KwDefaultExpressionNode[] kwDefaults = node.argBuilder.getKwDefaultParameterValues(this);
        Map<String, SSTNode> sstAnnotations = node.argBuilder.getAnnotatedArgs();
        Map<TruffleString, ExpressionNode> annotations = null;
        if (sstAnnotations != null && !sstAnnotations.isEmpty()) {
            annotations = new HashMap<>(sstAnnotations.size());
            for (String argName : sstAnnotations.keySet()) {
                SSTNode sstType = sstAnnotations.get(argName);
                annotations.put(ts(argName), (ExpressionNode) sstType.accept(this));
            }
        }
        if (node.resultAnnotation != null) {
            if (annotations == null) {
                annotations = new HashMap<>(1);
            }
            annotations.put(RETURN, (ExpressionNode) node.resultAnnotation.accept(this));
        }
        scopeEnvironment.setCurrentScope(node.scope);

        /*
         * Function root
         */
        ExecutionCellSlots executionCellSlots = scopeEnvironment.getExecutionCellSlots();
        DefinitionCellSlots definitionCellSlots = scopeEnvironment.getDefinitionCellSlots();
        FrameDescriptor fd = scopeEnvironment.getCurrentScope().createFrameDescriptor();
        String name = node.name;
        String qualname = node.scope.getQualname();
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(sourceSection, name, scopeEnvironment.isInGeneratorScope(), fd, returnTarget, executionCellSlots, signature, doc);
        RootCallTarget ct = PythonUtils.getOrCreateCallTarget(funcRoot);
        if (scopeEnvironment.isInGeneratorScope()) {
            funcDef = GeneratorFunctionDefinitionNode.create(ts(name), ts(qualname), ts(node.enclosingClassName), doc, defaults, kwDefaults, ct, fd, definitionCellSlots,
                            generatorFactory.getGeneratorInfo(),
                            annotations);
        } else {
            funcDef = new FunctionDefinitionNode(ts(name), ts(qualname), ts(node.enclosingClassName), doc, defaults, kwDefaults, ct, definitionCellSlots, annotations);
        }
        scopeEnvironment.setCurrentScope(node.scope.getParent());
        ReadNode funcVar = scopeEnvironment.findVariable(name);
        StatementNode writeNode = funcVar.makeWriteNode(funcDef);
        // TODO I'm not sure, whether this assingning of sourcesection is right.
        writeNode.assignSourceSection(((FunctionDefinitionNode) funcDef).getFunctionRoot().getSourceSection());
        scopeEnvironment.setCurrentScope(oldScope);
        return writeNode;
    }

    @Override
    public PNode visit(GetAttributeSSTNode node) {
        ExpressionNode receiver = (ExpressionNode) node.receiver.accept(this);
        PNode result = GetAttributeNode.create(ts(node.name), receiver);
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
        StatementNode elseStatement = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode result = new IfNode(toBooleanCastNode(test), thenStatement, elseStatement);
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
            result = new ImportStarNode(ts(from), level);
        } else {
            TruffleString[] fromList = new TruffleString[node.asNames.length];
            WriteNode[] readNodes = new WriteNode[fromList.length];
            for (int i = 0; i < fromList.length; i++) {
                String[] asName = node.asNames[i];
                fromList[i] = ts(asName[0]);
                readNodes[i] = (WriteNode) scopeEnvironment.findVariable(asName[1] == null ? asName[0] : asName[1]).makeWriteNode(EmptyNode.create());
            }
            result = ImportFromNode.create(ts(from), fromList, readNodes, level);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(ImportSSTNode node) {
        scopeEnvironment.setCurrentScope(node.scope);
        ExpressionNode importNode = new ImportNode(ts(node.name)).asExpression();
        PNode result;
        int dotIndex = node.name.indexOf('.');
        if (node.asName == null) {
            String moduleName = dotIndex == -1 ? node.name : node.name.substring(0, dotIndex);
            result = scopeEnvironment.findVariable(moduleName).makeWriteNode(importNode);
        } else {
            if (dotIndex != -1) {
                String[] parts = node.name.split("\\.");

                String from = node.name.substring(0, node.name.lastIndexOf("."));
                int level = 0;
                while (from.length() > level && from.charAt(level) == '.') {
                    level++;
                }
                if (level > 0) {
                    from = from.substring(level);
                }

                WriteNode readNode = (WriteNode) scopeEnvironment.findVariable(node.asName).makeWriteNode(EmptyNode.create());
                StatementNode importFrom = ImportFromNode.create(ts(from), new TruffleString[]{ts(parts[parts.length - 1])}, new WriteNode[]{readNode}, level);
                result = BlockNode.create(importNode.asStatement(), importFrom);
            } else {
                result = scopeEnvironment.findVariable(node.asName).makeWriteNode(importNode);
            }
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(LambdaSSTNode node) {
        String funcname = J_LAMBDA_NAME;
        String qualname = node.scope.getQualname();
        ScopeInfo oldScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.scope);
        /**
         * Parameters
         */
        StatementNode argumentNodes = BlockNode.create(node.args == null ? StatementNode.EMPTY_STATEMENT_ARRAY : node.args.getArgumentNodes(scopeEnvironment));
        Signature signature = node.args == null ? Signature.EMPTY : node.args.getSignature();

        /**
         * Lambda body
         */
        ExpressionNode lambdaExpr;
        LambdaBodyNode lambdaBody;
        GeneratorFactorySSTVisitor generatorFactory = null;
        boolean isGenerator = scopeEnvironment.isInGeneratorScope();
        StatementNode frameReturn;
        if (isGenerator) {
            generatorFactory = new GeneratorFactorySSTVisitor(errors, scopeEnvironment, nodeFactory, source, this);
            lambdaExpr = (ExpressionNode) node.body.accept(generatorFactory);
            lambdaBody = new LambdaBodyNode(lambdaExpr);
            frameReturn = new GeneratorFrameReturnNode(lambdaBody, scopeEnvironment.getCurrentScope().getReturnSlot());
        } else {
            lambdaExpr = (ExpressionNode) node.body.accept(this instanceof GeneratorFactorySSTVisitor
                            ? ((GeneratorFactorySSTVisitor) this).parentVisitor
                            : this);

            lambdaBody = new LambdaBodyNode(lambdaExpr);
            frameReturn = new FrameReturnNode(lambdaBody, scopeEnvironment.getCurrentScope().getReturnSlot());
        }
        lambdaBody.assignSourceSection(createSourceSection(node.body.getStartOffset(), node.body.getEndOffset()));

        ExpressionNode returnTargetNode;
        if (scopeEnvironment.isInGeneratorScope()) {
            returnTargetNode = new GeneratorReturnTargetNode(argumentNodes, frameReturn, ReadGeneratorFrameVariableNode.create(scopeEnvironment.getCurrentScope().getReturnSlot()),
                            generatorFactory.getMutableGeneratorInfo());
        } else {
            StatementNode body = BlockNode.create(argumentNodes, frameReturn);
            returnTargetNode = new ReturnTargetNode(body, ReadLocalVariableNode.create(scopeEnvironment.getCurrentScope().getReturnSlot()));
        }
        returnTargetNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));

        /**
         * Defaults
         */
        scopeEnvironment.setCurrentScope(node.scope.getParent());
        ExpressionNode[] defaults = node.args == null ? ExpressionNode.EMPTY_ARRAY : node.args.getDefaultParameterValues(this);
        FunctionDefinitionNode.KwDefaultExpressionNode[] kwDefaults = node.args == null ? new FunctionDefinitionNode.KwDefaultExpressionNode[0] : node.args.getKwDefaultParameterValues(this);
        scopeEnvironment.setCurrentScope(node.scope);

        /**
         * Lambda function root
         */
        ExecutionCellSlots executionCellSlots = scopeEnvironment.getExecutionCellSlots();
        DefinitionCellSlots definitionCellSlots = scopeEnvironment.getDefinitionCellSlots();
        FrameDescriptor fd = scopeEnvironment.getCurrentScope().createFrameDescriptor();
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(returnTargetNode.getSourceSection(), funcname, scopeEnvironment.isInGeneratorScope(), fd, returnTargetNode, executionCellSlots,
                        signature, null);

        /**
         * Definition
         */
        PNode funcDef;
        RootCallTarget ct = PythonUtils.getOrCreateCallTarget(funcRoot);
        if (scopeEnvironment.isInGeneratorScope()) {
            funcDef = GeneratorFunctionDefinitionNode.create(ts(funcname), ts(qualname), null, null, defaults, kwDefaults, ct, fd, definitionCellSlots, generatorFactory.getGeneratorInfo(), null);
        } else {
            funcDef = new FunctionDefinitionNode(ts(funcname), ts(qualname), null, null, defaults, kwDefaults, ct, definitionCellSlots, null);
            funcDef.assignSourceSection(returnTargetNode.getSourceSection());
        }
        scopeEnvironment.setCurrentScope(oldScope);
        return funcDef;
    }

    @Override
    public PNode visit(NotSSTNode node) {
        PNode result = CoerceToBooleanNode.createIfFalseNode((ExpressionNode) node.value.accept(this));
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(IntegerLiteralSSTNode node) {
        long result = node.value;
        ExpressionNode intLiteral = Integer.MIN_VALUE <= result && result <= Integer.MAX_VALUE ? new IntegerLiteralNode((int) result) : new LongLiteralNode(result);
        intLiteral.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return intLiteral;
    }

    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);

    @Override
    public PNode visit(BigIntegerLiteralSSTNode node) {
        SimpleLiteralNode intLiteral;
        if (node.value.equals(MIN_LONG)) {
            // this can happen because the numeric literals have to support negation
            intLiteral = new LongLiteralNode(Long.MIN_VALUE);
        } else {
            intLiteral = new PIntLiteralNode(node.value);
        }
        intLiteral.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return intLiteral;
    }

    @Override
    public PNode visit(OrSSTNode node) {
        ExpressionNode last = (ExpressionNode) node.values[0].accept(this);
        for (int i = 1; i < node.values.length; i++) {
            int numYields = getCurrentNumberOfYields();
            ExpressionNode right = (ExpressionNode) node.values[i].accept(this);
            last = createResumableExpression(last, right, hadYieldSince(numYields), OrNode::new);
        }
        last.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return last;
    }

    @Override
    public PNode visit(RaiseSSTNode node) {
        ExpressionNode value = node.value != null ? (ExpressionNode) node.value.accept(this) : NoValueNode.create();
        ExpressionNode from = node.from != null ? (ExpressionNode) node.from.accept(this) : NoValueNode.create();
        PNode result = RaiseNode.create(value, from);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(ReturnSSTNode node) {
        StatementNode result;
        if (node.value != null) {
            result = createFrameReturn((ExpressionNode) node.value.accept(this), scopeEnvironment.getCurrentScope().getReturnSlot());
        } else {
            result = new ReturnNode();
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(SimpleSSTNode node) {
        PNode result = null;
        switch (node.type) {
            case BREAK:
                result = new BreakNode();
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                break;
            case CONTINUE:
                result = new ContinueNode();
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                break;
            case PASS:
            case EMPTY:
                EmptyNode emptyNode = EmptyNode.create();
                emptyNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                result = emptyNode.asStatement();
                break;
            case NONE:
                result = new ObjectLiteralNode(PNone.NONE);
                if (node.startOffset < node.endOffset) {
                    result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                }
                break;
            case ELLIPSIS:
                result = new ObjectLiteralNode(PEllipsis.INSTANCE);
                result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
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
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
        PNode result = new StringLiteralNode(ts(node.value));
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
        PNode result = new BytesLiteralNode(node.value);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.FormatExpressionSSTNode node) {
        Source prev = this.source;
        this.source = Source.newBuilder(PythonLanguage.ID, node.expressionCode, "<fstring-expr>").build();
        ExpressionNode expression = (ExpressionNode) node.expression.accept(this);
        this.source = prev;
        ExpressionNode specifier = node.specifier == null ? null : (ExpressionNode) node.specifier.accept(this);
        PNode result = new FormatStringExpressionNode(expression, specifier, node.conversionType);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
        ExpressionNode[] exprs = new ExpressionNode[node.parts.length];
        for (int i = 0; i < node.parts.length; i++) {
            exprs[i] = (ExpressionNode) node.parts[i].accept(this);
        }
        PNode result = new FormatStringLiteralNode(exprs);
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
        PNode result = GetItemNode.create(receiver, subscript);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(TernaryIfSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        ExpressionNode thenExpr = (ExpressionNode) node.thenStatement.accept(this);
        ExpressionNode elseExpr = (ExpressionNode) node.elseStatement.accept(this);
        PNode result = new TernaryIfNode(toBooleanCastNode(test), thenExpr, elseExpr);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(TrySSTNode node) {
        StatementNode body = (StatementNode) node.body.accept(this);
        StatementNode elseStatement = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode finalyStatement = node.finallyStatement == null ? null : (StatementNode) node.finallyStatement.accept(this);
        ExceptNode[] exceptNodes = new ExceptNode[node.exceptNodes.length];
        for (int i = 0; i < exceptNodes.length; i++) {
            ExceptSSTNode exceptNode = node.exceptNodes[i];
            if (exceptNode.test == null && i < exceptNodes.length - 1) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(exceptNode.startOffset, exceptNode.endOffset), ErrorMessages.DEFAULT_EXCEPT_MUST_BE_LAST);
            }
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
        PNode result = new TryFinallyNode(new TryExceptNode(body, exceptNodes, elseStatement), finalyStatement);
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
        PNode result = (PNode) scopeEnvironment.findVariable(node.name);
        if (result == null) {
            if (scopeEnvironment.isNonlocal(node.name)) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.NO_BINDING_FOR_NON_LOCAL, node.name);
            }
            throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.CANNOT_ASSIGN_TO, node.name);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(WhileSSTNode node) {
        ExpressionNode test = (ExpressionNode) node.test.accept(this);
        StatementNode body = (StatementNode) node.body.accept(this);
        if (node.containsContinue) {
            body = new ContinueTargetNode(body);
        }
        StatementNode whileNode = new WhileNode(toBooleanCastNode(test), body);
        // TODO: Do we need to create the ElseNode, even if the else branch is empty?
        StatementNode elseBranch = node.elseStatement == null ? BlockNode.createEmptyBlock() : (StatementNode) node.elseStatement.accept(this);
        StatementNode result;
        if (!node.containsBreak) {
            result = new ElseNode(whileNode, elseBranch);
        } else {
            // TODO: this is also strange, that we create don't create ElseNode for break target.
            // At least it seems to be inconsistent.
            result = node.elseStatement == null ?
            // TODO: Do we need to create the empty block here?
                            new BreakTargetNode(whileNode, BlockNode.createEmptyBlock()) : new BreakTargetNode(whileNode, elseBranch);
        }
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(WithSSTNode node) {
        StatementNode body = (StatementNode) node.body.accept(this);
        WriteNode asName = node.target == null ? null : (WriteNode) makeWriteNode((ExpressionNode) node.target.accept(this));
        ExpressionNode expression = (ExpressionNode) node.expression.accept(this);
        PNode result = WithNode.create(expression, asName, body);
        if (node.startOffset > -1) {
            result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        }
        return result;
    }

    @Override
    public PNode visit(YieldExpressionSSTNode node) {
        throw new IllegalStateException("FactorySSTVisitor called on yield");
    }

    protected SourceSection createSourceSection(int start, int stop) {
        if (start < stop && source.getLength() > start && source.getLength() >= stop) {
            return source.createSection(start, stop - start);
        } else {
            return source.createUnavailableSection();
        }
    }

    public ReadNode makeTempLocalVariable() {
        int tempSlot = scopeEnvironment.getCurrentScope().createTemp();
        return !scopeEnvironment.isInGeneratorScope()
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
        if (lhs instanceof TupleLiteralNode) {
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
                    SourceSection section = leftHandSides[0].getSourceSection();
                    if (section == null) {
                        section = ((StarredExpressionNode) leftHandSides[i]).getValue().getSourceSection();
                    }
                    throw errors.raiseInvalidSyntax(source, section, ErrorMessages.STARRED_ASSIGMENT_MUST_BE_IN_LIST_OR_TUPLE);
                }
                starredIndex = i;
                statements[i] = createAssignment(((StarredExpressionNode) leftHandSides[i]).getValue(), (ExpressionNode) tempRead);
            } else {
                statements[i] = createAssignment(leftHandSides[i], (ExpressionNode) tempRead);
            }
        }
        return DestructuringAssignmentNode.create(rhs, temps, starredIndex, statements);
    }

    @SuppressWarnings("unused")
    private SourceSection createSourceSectionFromBlock(StatementNode body) {
        int bodyStart = -1;
        int bodyEnd = -1;
        if (body instanceof BaseBlockNode) {
            StatementNode[] bodyStatements = ((BaseBlockNode) body).getStatements();
            if (bodyStatements.length > 0) {
                bodyStart = ((BaseBlockNode) body).getStatements()[0].getSourceSection().getCharIndex();
                bodyEnd = ((BaseBlockNode) body).getStatements()[bodyStatements.length - 1].getSourceSection().getCharEndIndex();
            }
        } else {
            bodyStart = body.getSourceSection().getCharIndex();
            bodyEnd = body.getSourceSection().getCharEndIndex();
        }
        return createSourceSection(bodyStart, bodyEnd);
    }

    private static FunctionBodyNode createFunctionBody(StatementNode body) {
        FunctionBodyNode functionBody;
        if (body instanceof BlockNode) {
            functionBody = FunctionBodyNode.create(((BlockNode) body).getStatements());
        } else {
            functionBody = FunctionBodyNode.create(body);
        }
        return functionBody;
    }

    @SuppressWarnings({"unchecked", "unused"})
    private static <T> T duplicate(Node orig, Class<T> clazz) {
        return (T) NodeUtil.cloneNode(orig);
    }

    protected static CoerceToBooleanNode toBooleanCastNode(PNode node) {
        if (node instanceof CoerceToBooleanNode) {
            return (CoerceToBooleanNode) node;
        } else {
            return CoerceToBooleanNode.createIfTrueNode((ExpressionNode) node);
        }
    }

    static TruffleString ts(String s) {
        return s == null ? null : toTruffleStringUncached(s);
    }
}
