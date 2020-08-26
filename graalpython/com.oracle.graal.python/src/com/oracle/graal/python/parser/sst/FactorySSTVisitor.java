/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.LAMBDA_NAME;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILD_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASSCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.TEMP_LOCAL_PREFIX;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.NoValueNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
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
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionBodyNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.nodes.generator.AbstractYieldNode;
import com.oracle.graal.python.nodes.generator.GeneratorBlockNode;
import com.oracle.graal.python.nodes.generator.GeneratorReturnTargetNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.generator.WriteGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.literal.BooleanLiteralNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DictLiteralNode;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.FormatStringLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.LiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.SetLiteralNode;
import com.oracle.graal.python.nodes.literal.SimpleLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssertNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
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
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class FactorySSTVisitor implements SSTreeVisitor<PNode> {

    protected final ScopeEnvironment scopeEnvironment;
    protected Source source;
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

    protected StatementNode createResumableBlock(@SuppressWarnings("unused") boolean canYield, StatementNode... statements) {
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
            int numYields = getCurrentNumberOfYields();
            ExpressionNode right = (ExpressionNode) node.values[i].accept(this);
            last = createResumableExpression(last, right, hadYieldSince(numYields), AndNode::new);
        }
        last.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return last;
    }

    @Override
    public PNode visit(AnnAssignmentSSTNode node) {
        PNode assignmentNode = createAssignment(node, true);
        if (!scopeEnvironment.isInFunctionScope() && node.type != null && node.lhs.length == 1 && node.lhs[0] instanceof VarLookupSSTNode) {
            // annotations in a function we ignore at all. Even there are not evalueated, whether
            // the type is wrong
            // create simple SST tree for : __annotations__['var_name'] = type
            VarLookupSSTNode varLookupNode = (VarLookupSSTNode) node.lhs[0];
            SubscriptSSTNode getAnnotationSST = new SubscriptSSTNode(new VarLookupSSTNode(__ANNOTATIONS__, -1, -1), new StringLiteralSSTNode.RawStringLiteralSSTNode(varLookupNode.name, -1, -1), -1,
                            -1);
            AssignmentSSTNode assignAnnotationSST = new AssignmentSSTNode(new SSTNode[]{getAnnotationSST}, node.type, -1, -1);
            PNode assignAnnotationNode = visit(assignAnnotationSST);
            // return block with statements[the assignment, add variable name, type to
            // __annotations__]
            return BlockNode.create(new StatementNode[]{(StatementNode) assignmentNode, (StatementNode) assignAnnotationNode});
        }
        return assignmentNode;
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
        return createAssignment(node, false);
    }

    public StatementNode createAssignment(AssignmentSSTNode node, boolean checkAnnotationPermitted) {
        ExpressionNode[] lhs = new ExpressionNode[node.lhs.length];
        int numYields = getCurrentNumberOfYields();
        for (int i = 0; i < node.lhs.length; i++) {
            SSTNode sstLhs = node.lhs[i];
            checkCannotAssignTo(sstLhs);
            lhs[i] = (ExpressionNode) sstLhs.accept(this);
            if (checkAnnotationPermitted) {
                if (lhs[i] instanceof TupleLiteralNode) {
                    throw errors.raiseInvalidSyntax(source, createSourceSection(sstLhs.getStartOffset(), sstLhs.getEndOffset()), ErrorMessages.ONLY_SINGLE_TARGET_CAN_BE_ANNOTATED, "tuple");
                } else if (lhs[i] instanceof ListLiteralNode) {
                    throw errors.raiseInvalidSyntax(source, createSourceSection(sstLhs.getStartOffset(), sstLhs.getEndOffset()), ErrorMessages.ONLY_SINGLE_TARGET_CAN_BE_ANNOTATED, "list");
                } else if (!(lhs[i] instanceof ReadNode)) {
                    throw errors.raiseInvalidSyntax(source, createSourceSection(sstLhs.getStartOffset(), sstLhs.getEndOffset()), ErrorMessages.ILLEGAL_TARGET_FOR_ANNOTATION);
                }
            }
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
        checkCannotAssignTo(node.lhs);
        ExpressionNode lhs = (ExpressionNode) node.lhs.accept(this);
        checkExpressionAssignable(lhs);
        if (!(lhs instanceof ReadNode)) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.ILLEGAL_EXPRESSION_FOR_AUGMENTED_ASSIGNEMNT);
        }
        ExpressionNode rhs = (ExpressionNode) node.rhs.accept(this);
        ExpressionNode binOp = nodeFactory.createInplaceOperation(node.operation, lhs, rhs);
        PNode duplicate = nodeFactory.duplicate(lhs, PNode.class);
        PNode result = ((ReadNode) duplicate).makeWriteNode(binOp);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    private void checkCannotAssignTo(SSTNode lhs) throws RuntimeException {
        if (lhs instanceof ForComprehensionSSTNode) {
            PythonBuiltinClassType resultType = ((ForComprehensionSSTNode) lhs).resultType;
            if (resultType == PythonBuiltinClassType.PGenerator) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.startOffset, lhs.endOffset), "cannot assign to generator expression");
            }
            String calleeName;
            switch (resultType) {
                case PList:
                    calleeName = BuiltinNames.LIST;
                    break;
                case PSet:
                    calleeName = BuiltinNames.SET;
                    break;
                case PDict:
                    calleeName = BuiltinNames.DICT;
                    break;
                default:
                    calleeName = null;
            }
            if (calleeName == null) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.startOffset, lhs.endOffset), "cannot assign to comprehension");
            } else {
                throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.startOffset, lhs.endOffset), "cannot assign to %s comprehension", calleeName);
            }
        } else if (lhs instanceof CallSSTNode) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.startOffset, lhs.endOffset), "cannot assign to function call");
        } else if (lhs instanceof CollectionSSTNode) {
            for (SSTNode n : ((CollectionSSTNode) lhs).getValues()) {
                checkCannotAssignTo(n);
            }
        }
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

    protected static String getQualifiedName(ScopeInfo scope, String name) {
        StringBuilder qualifiedName = new StringBuilder(name);
        ScopeInfo tmpScope = scope.getParent();
        while (tmpScope != null) {
            switch (tmpScope.getScopeKind()) {
                case GenExp:
                case ListComp:
                case DictComp:
                case SetComp:
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
        ScopeInfo classScope = node.scope;
        scopeEnvironment.setCurrentScope(classScope);
        String qualifiedName = getQualifiedName(classScope, node.name);

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
        boolean hasImplicitClass = classScope.isCellVar(__CLASS__);
        StatementNode[] classStatements = new StatementNode[2 + delta + (hasImplicitClass ? 1 : 0)];
        // ClassStatemtns look like:
        // [0] ClassDefinitionPrologueNode
        classStatements[0] = new ClassDefinitionPrologueNode(qualifiedName);
        // [?] if there is documentation -> doc statement
        if (doc != null) {
            scopeEnvironment.createLocal(__DOC__);
            classStatements[1] = scopeEnvironment.findVariable(__DOC__).makeWriteNode(doc);
        }
        // [?] if thre are annotations -> annotations
        if (classScope.hasAnnotations()) {
            classStatements[delta] = scopeEnvironment.findVariable(__ANNOTATIONS__).makeWriteNode(nodeFactory.createDictLiteral());
        }
        // [last - 1] class body statements
        classStatements[1 + delta] = classBody;
        if (hasImplicitClass) {
            // [last] assign __class__ cell to __classcell__
            // Only if __class__ is generated in the class scope. The __class__ is in class scope,
            // when an inner method uses __class__ or super is used.
            classStatements[2 + delta] = scopeEnvironment.findVariable(__CLASSCELL__).makeWriteNode(
                            nodeFactory.createReadLocal(scopeEnvironment.getCurrentScope().getFrameDescriptor().findFrameSlot(__CLASS__)));
        }

        SourceSection nodeSourceSection = createSourceSection(node.startOffset, node.endOffset);
        StatementNode body = nodeFactory.createBlock(classStatements);
        ExpressionNode bodyAsExpr = new ReturnTargetNode(body, nodeFactory.createNullLiteral());
        bodyAsExpr.assignSourceSection(nodeSourceSection);
        ClassBodyRootNode classBodyRoot = nodeFactory.createClassBodyRoot(nodeSourceSection, node.name, scopeEnvironment.getCurrentFrame(), bodyAsExpr, scopeEnvironment.getExecutionCellSlots());
        RootCallTarget ct = PythonUtils.getOrCreateCallTarget(classBodyRoot);
        FunctionDefinitionNode funcDef = new FunctionDefinitionNode(node.name, qualifiedName, null, null, null, null, ct, scopeEnvironment.getDefinitionCellSlots(),
                        scopeEnvironment.getExecutionCellSlots(), null);
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
            nameArgs = new ExpressionNode[0];
        }
        args[0] = funcDef;
        args[1] = nodeFactory.createStringLiteral(node.name);

        ExpressionNode owner = nodeFactory.createGetAttribute(nodeFactory.createBuiltinsLiteral(), __BUILD_CLASS__);
        ExpressionNode classDef = PythonCallNode.create(owner, args, nameArgs, starArg, kwArg);
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
                result = createResumableExpression(items, hadYieldSince(numYields), nodeFactory::createTupleLiteral);
                break;
            case PList:
                numYields = getCurrentNumberOfYields();
                items = getCollectionItems(node.values);
                result = createResumableExpression(items, hadYieldSince(numYields), nodeFactory::createListLiteral);
                break;
            case PSet:
                numYields = getCurrentNumberOfYields();
                items = getCollectionItems(node.values);
                result = createResumableExpression(items, hadYieldSince(numYields), nodeFactory::createSetLiteral);
                break;
            case PDict:
                if (node.values.length == 0) {
                    result = nodeFactory.createDictLiteral();
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
                                dicts.add(createResumableExpression(keys.toArray(new ExpressionNode[0]), values.toArray(new ExpressionNode[0]), hadYieldSince(numYields),
                                                nodeFactory::createDictLiteral));
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
                        result = createResumableExpression(keys.toArray(new ExpressionNode[0]), values.toArray(new ExpressionNode[0]), hadYieldSince(numYields), nodeFactory::createDictLiteral);
                    } else {
                        if (!keys.isEmpty()) {
                            dicts.add(createResumableExpression(keys.toArray(new ExpressionNode[0]), values.toArray(new ExpressionNode[0]), hadYieldSince(numYields), nodeFactory::createDictLiteral));
                        }
                        result = createResumableExpression(dicts.toArray(new ExpressionNode[0]), hadYieldSince(numYields), nodeFactory::createDictionaryConcat);
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
                nextComp = createResumableExpression(left, right, rightCanYield, (l, r) -> nodeFactory.createComparisonOperation(operator, l, r));
                left = right;
            } else {
                ReadNode tmpVar = makeTempLocalVariable();
                StatementNode tmpAssignment = tmpVar.makeWriteNode(right);
                nextComp = createResumableExpression(left, (ExpressionNode) tmpVar, rightCanYield, (l, r) -> nodeFactory.createComparisonOperation(operator, l, r)).withSideEffect(tmpAssignment);
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
            throw errors.raiseInvalidSyntax(target.getSourceSection().getSource(), target.getSourceSection(), ErrorMessages.CANT_DELETE, target.getSourceSection().getCharacters());
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
            result = new ComplexLiteralNode(new PComplex(0, node.value));
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
            target = nodeFactory.createTupleLiteral(targets);
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
        scopeEnvironment.setCurrentScope(node.scope);
        Signature signature = node.argBuilder.getSignature();
        StatementNode argumentNodes = nodeFactory.createBlock(node.argBuilder.getArgumentNodes(scopeEnvironment));

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
            returnTarget = new GeneratorReturnTargetNode(argumentNodes, body, ReadGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot()), generatorFactory.getMutableGeneratorInfo());
        } else {
            body = nodeFactory.createBlock(argumentNodes, body);
            returnTarget = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        }

        SourceSection sourceSection = createSourceSection(node.startOffset, node.endOffset);
        returnTarget.assignSourceSection(sourceSection);

        scopeEnvironment.setCurrentScope(node.scope.getParent());
        ExpressionNode[] defaults = node.argBuilder.getDefaultParameterValues(this);
        FunctionDefinitionNode.KwDefaultExpressionNode[] kwDefaults = node.argBuilder.getKwDefaultParameterValues(this);
        Map<String, SSTNode> sstAnnotations = node.argBuilder.getAnnotatedArgs();
        Map<String, ExpressionNode> annotations = null;
        if (sstAnnotations != null && !sstAnnotations.isEmpty()) {
            annotations = new HashMap<>(sstAnnotations.size());
            for (String argName : sstAnnotations.keySet()) {
                SSTNode sstType = sstAnnotations.get(argName);
                annotations.put(argName, (ExpressionNode) sstType.accept(this));
            }
        }
        scopeEnvironment.setCurrentScope(node.scope);

        /*
         * Function root
         */
        FrameDescriptor fd = scopeEnvironment.getCurrentFrame();
        String name = node.name;
        String qualname = getQualifiedName(node.scope, name);
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(sourceSection, name, scopeEnvironment.isInGeneratorScope(), fd, returnTarget, scopeEnvironment.getExecutionCellSlots(),
                        signature);
        RootCallTarget ct = PythonUtils.getOrCreateCallTarget(funcRoot);
        if (scopeEnvironment.isInGeneratorScope()) {
            funcDef = GeneratorFunctionDefinitionNode.create(name, qualname, node.enclosingClassName, doc, defaults, kwDefaults, ct, fd,
                            scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots(),
                            generatorFactory.getGeneratorInfo(), annotations);
        } else {
            funcDef = new FunctionDefinitionNode(name, qualname, node.enclosingClassName, doc, defaults, kwDefaults, ct, scopeEnvironment.getDefinitionCellSlots(),
                            scopeEnvironment.getExecutionCellSlots(), annotations);
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
                throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.IMPORT_START_ONLY_ALLOWED_AT_MODULE_LEVEL);
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
        String funcname = LAMBDA_NAME;
        String qualname = getQualifiedName(node.scope, funcname);
        ScopeInfo oldScope = scopeEnvironment.getCurrentScope();
        scopeEnvironment.setCurrentScope(node.scope);
        /**
         * Parameters
         */
        StatementNode argumentNodes = nodeFactory.createBlock(node.args == null ? new StatementNode[0] : node.args.getArgumentNodes(scopeEnvironment));
        Signature signature = node.args == null ? Signature.EMPTY : node.args.getSignature();

        /**
         * Lambda body
         */
        ExpressionNode lambdaBody;
        FunctionBodyNode functionBody;
        GeneratorFactorySSTVisitor generatorFactory = null;
        boolean isGenerator = scopeEnvironment.isInGeneratorScope();
        StatementNode frameReturn;
        if (isGenerator) {
            generatorFactory = new GeneratorFactorySSTVisitor(errors, scopeEnvironment, nodeFactory, source, this);
            lambdaBody = (ExpressionNode) node.body.accept(generatorFactory);
            functionBody = FunctionBodyNode.create(WriteGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot(), lambdaBody));
            frameReturn = nodeFactory.createFrameReturn(functionBody);
        } else {
            lambdaBody = (ExpressionNode) node.body.accept(this instanceof GeneratorFactorySSTVisitor
                            ? ((GeneratorFactorySSTVisitor) this).parentVisitor
                            : this);
            functionBody = FunctionBodyNode.create(nodeFactory.createWriteLocal(lambdaBody, scopeEnvironment.getReturnSlot()));
            frameReturn = nodeFactory.createFrameReturn(functionBody);
        }
        functionBody.assignSourceSection(createSourceSection(node.body.getStartOffset(), node.body.getEndOffset()));

        ExpressionNode returnTargetNode;
        if (scopeEnvironment.isInGeneratorScope()) {
            returnTargetNode = new GeneratorReturnTargetNode(argumentNodes, frameReturn, ReadGeneratorFrameVariableNode.create(scopeEnvironment.getReturnSlot()),
                            generatorFactory.getMutableGeneratorInfo());
        } else {
            StatementNode body = nodeFactory.createBlock(argumentNodes, frameReturn);
            returnTargetNode = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        }
        returnTargetNode.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));

        /**
         * Defaults
         */
        scopeEnvironment.setCurrentScope(node.scope.getParent());
        ExpressionNode[] defaults = node.args == null ? new ExpressionNode[0] : node.args.getDefaultParameterValues(this);
        FunctionDefinitionNode.KwDefaultExpressionNode[] kwDefaults = node.args == null ? new FunctionDefinitionNode.KwDefaultExpressionNode[0] : node.args.getKwDefaultParameterValues(this);
        scopeEnvironment.setCurrentScope(node.scope);

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
        RootCallTarget ct = PythonUtils.getOrCreateCallTarget(funcRoot);
        if (scopeEnvironment.isInGeneratorScope()) {
            funcDef = GeneratorFunctionDefinitionNode.create(funcname, qualname, null, null, defaults, kwDefaults, ct, fd,
                            scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots(),
                            generatorFactory.getGeneratorInfo(), null);
        } else {
            funcDef = new FunctionDefinitionNode(funcname, qualname, null, null, defaults, kwDefaults, ct, scopeEnvironment.getDefinitionCellSlots(),
                            scopeEnvironment.getExecutionCellSlots(), null);
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
        if (!scopeEnvironment.isInFunctionScope()) {
            errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.RETURN_OUTSIDE_FUNC);
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
                if (node.startOffset < node.endOffset) {
                    result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
                }
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
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
        PNode result = nodeFactory.createStringLiteral(node.value);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
        PNode result = nodeFactory.createBytesLiteral(node.value);
        result.assignSourceSection(createSourceSection(node.startOffset, node.endOffset));
        return result;
    }

    @Override
    public PNode visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
        ExpressionNode[] exprs = new ExpressionNode[node.expressions.length];
        Source prev = this.source;
        for (int i = 0; i < node.expressions.length; i++) {
            this.source = Source.newBuilder(PythonLanguage.ID, node.expresionsSources[i], "<fstring-expr>").build();
            exprs[i] = (ExpressionNode) node.expressions[i].accept(this);
        }
        this.source = prev;
        PNode result = nodeFactory.createFormatStringLiteral(node.value, exprs, node.literals);
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
            if (exceptNode.test == null && i < exceptNodes.length - 1) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(exceptNode.startOffset, exceptNode.endOffset), ErrorMessages.DEFAULT_EXCEPT_MUST_BE_LAST);
            }
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
                throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.NO_BINDING_FOR_NON_LOCAL, node.name);
            }
            throw errors.raiseInvalidSyntax(source, createSourceSection(node.startOffset, node.endOffset), ErrorMessages.CANNOT_ASSIGN_TO, node.name);
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
        String tempName = TEMP_LOCAL_PREFIX + scopeEnvironment.getCurrentScope().getFrameDescriptor().getSize();
        FrameSlot tempSlot = scopeEnvironment.createAndReturnLocal(tempName);
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

    private void checkExpressionAssignable(ExpressionNode lhs) {
        if (lhs instanceof ObjectLiteralNode) {
            if (((ObjectLiteralNode) lhs).getObject() == PEllipsis.INSTANCE) {
                throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "Ellipsis");
            } else {
                throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "None");
            }
        } else if (lhs instanceof BooleanLiteralNode) {
            if ((boolean) ((BooleanLiteralNode) lhs).getValue()) {
                throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "True");
            } else {
                throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "False");
            }
        } else if (lhs instanceof SimpleLiteralNode) {
            throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "literal");
        } else if (lhs instanceof DictLiteralNode) {
            throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "dict display");
        } else if (lhs instanceof SetLiteralNode) {
            throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "set display");
        } else if (lhs instanceof FormatStringLiteralNode) {
            throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "f-string expression");
        } else if (lhs instanceof AbstractYieldNode) {
            throw errors.raiseInvalidSyntax(source, lhs.getSourceSection(), ErrorMessages.CANNOT_ASSIGN_TO, "yield expression");
        }
    }

    private StatementNode createAssignment(ExpressionNode lhs, ExpressionNode rhs) {
        checkExpressionAssignable(lhs);
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
        return nodeFactory.createDestructuringAssignment(rhs, temps, starredIndex, statements);
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
}
