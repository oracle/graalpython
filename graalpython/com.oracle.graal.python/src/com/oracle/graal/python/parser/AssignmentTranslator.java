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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PNodeUtil;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssignmentNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.antlr.Python3Parser.ExprContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Expr_stmtContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.ExprlistContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.NormassignContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Star_exprContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.TestContext;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;

public class AssignmentTranslator extends Python3BaseVisitor<PNode> {

    private final NodeFactory factory;
    private final TranslationEnvironment environment;
    private final PythonTreeTranslator translator;
    private final ParserErrorCallback errors;

    public AssignmentTranslator(ParserErrorCallback errors, TranslationEnvironment environment, PythonTreeTranslator translator) {
        this.errors = errors;
        this.factory = errors.getLanguage().getNodeFactory();
        this.environment = environment;
        this.translator = translator;
    }

    public PNode translate(Expr_stmtContext ctx) {
        if (ctx.annassign() != null) {
            throw new RuntimeException("not implemented");
        } else if (ctx.augassign() != null) {
            return makeAugmentedAssignment(ctx);
        } else {
            return makeNormalAssignment(ctx);
        }
    }

    private PNode makeNormalAssignment(Expr_stmtContext ctx) {
        List<NormassignContext> normassign = ctx.normassign();
        ExpressionNode mostRhs = getAssignmentValue(normassign);
        ExpressionNode mostLhs = (ExpressionNode) ctx.testlist_star_expr().accept(this);
        if (normassign.size() > 1) {
            return createMultiAssignment(normassign, mostRhs, mostLhs);
        } else {
            return createAssignment(mostLhs, mostRhs);
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
            ReadNode tempRead = environment.makeTempLocalVariable();
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
        return factory.createDestructuringAssignment(rhs, temps, starredIndex, statements);
    }

    private PNode createMultiAssignment(List<NormassignContext> normassign, ExpressionNode mostRhs, ExpressionNode mostLhs) {
        ReadNode tmp = environment.makeTempLocalVariable();
        StatementNode tmpWrite = tmp.makeWriteNode(mostRhs);
        StatementNode[] assignments = new StatementNode[normassign.size() + 1];
        assignments[0] = tmpWrite;
        assignments[1] = createAssignment(mostLhs, (ExpressionNode) tmp);
        for (int i = 0; i < normassign.size() - 1; i++) {
            NormassignContext normassignContext = normassign.get(i);
            if (normassignContext.yield_expr() != null) {
                throw errors.raise(SyntaxError, "assignment to yield expression not possible");
            }
            assignments[i + 2] = createAssignment((ExpressionNode) normassignContext.accept(this), (ExpressionNode) tmp);
        }
        return factory.createBlock(assignments);
    }

    @SuppressWarnings("unchecked")
    private ExpressionNode getAssignmentValue(List<NormassignContext> normassign) {
        Object mostRhsParsed = normassign.get(normassign.size() - 1).accept(translator);
        ExpressionNode mostRhs;
        if (mostRhsParsed instanceof List) {
            mostRhs = factory.createTupleLiteral((List<ExpressionNode>) mostRhsParsed);
        } else {
            mostRhs = (ExpressionNode) mostRhsParsed;
        }
        return mostRhs;
    }

    private PNode makeAugmentedAssignment(Expr_stmtContext ctx) {
        ExpressionNode rhs;
        if (ctx.yield_expr() != null) {
            rhs = (ExpressionNode) ctx.yield_expr().accept(translator);
        } else {
            rhs = (ExpressionNode) ctx.testlist().accept(translator);
        }
        return makeAugmentedAssignment((ExpressionNode) ctx.testlist_star_expr().accept(translator), ctx.augassign().getText(), rhs);
    }

    private PNode makeAugmentedAssignment(ExpressionNode lhs, String text, ExpressionNode rhs) {
        if (!(lhs instanceof ReadNode)) {
            throw errors.raise(SyntaxError, "illegal expression for augmented assignment");
        }
        ExpressionNode binOp = factory.createInplaceOperation(text, lhs, rhs);
        PNode duplicate = factory.duplicate(lhs, PNode.class);
        PNodeUtil.clearSourceSections(duplicate);
        return ((ReadNode) duplicate).makeWriteNode(binOp);
    }

    private PNode visitTargetlist(ParserRuleContext ctx, int starSize) {
        boolean endsWithComma = false;
        if (starSize > 0) {
            if (starSize > 1) {
                throw errors.raise(SyntaxError, "%d starred expressions in assigment", starSize);
            }
        }
        List<ExpressionNode> targets = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            endsWithComma = false;
            if (child instanceof TerminalNode) {
                if (child.getText().equals(",")) {
                    endsWithComma = true;
                }
                continue;
            } else if (child instanceof Python3Parser.TestContext ||
                            child instanceof Python3Parser.Star_exprContext ||
                            child instanceof Python3Parser.ExprContext) {
                targets.add((ExpressionNode) child.accept(translator));
            } else {
                assert false;
            }
        }
        if (targets.size() == 1 && !endsWithComma) {
            PNode pNode = targets.get(0);
            if (pNode instanceof ReadNode) {
                return pNode;
            } else if (pNode instanceof TupleLiteralNode || pNode instanceof ListLiteralNode) {
                return pNode;
            } else if (pNode instanceof StarredExpressionNode) {
                throw errors.raise(SyntaxError, "starred assignment target must be in a list or tuple");
            } else {
                String text = ctx.getText();
                if (environment.isNonlocal(text)) {
                    throw errors.raise(SyntaxError, "no binding for nonlocal variable \"%s\" found", text);
                }
                throw errors.raise(SyntaxError, "Cannot assign to %s", pNode);
            }
        } else {
            return factory.createObjectLiteral(targets.toArray(new ExpressionNode[0]));
        }
    }

    @Override
    public PNode visitExprlist(Python3Parser.ExprlistContext ctx) {
        int starSize = ctx.star_expr().size();
        return visitTargetlist(ctx, starSize);
    }

    @Override
    public PNode visitTestlist_star_expr(Python3Parser.Testlist_star_exprContext ctx) {
        int starSize = ctx.star_expr().size();
        return visitTargetlist(ctx, starSize);
    }

    public StatementNode translate(ExprlistContext exprlist) {
        return makeWriteNode((ExpressionNode) exprlist.accept(this));
    }

    public StatementNode translate(ExprContext expr) {
        return makeWriteNode((ExpressionNode) expr.accept(translator));
    }

    public StatementNode translate(Star_exprContext ctx) {
        return makeWriteNode((ExpressionNode) ctx.accept(translator));
    }

    public StatementNode translate(TestContext ctx) {
        return makeWriteNode((ExpressionNode) ctx.accept(translator));
    }

    private StatementNode makeWriteNode(ExpressionNode accept) {
        StatementNode assignmentNode = createAssignment(accept, null);
        if (!(assignmentNode instanceof WriteNode)) {
            ReadNode tempLocal = environment.makeTempLocalVariable();
            assignmentNode = createAssignment(accept, (ExpressionNode) tempLocal);
            return new AssignmentNode(assignmentNode, tempLocal.makeWriteNode(EmptyNode.create()));
        } else {
            return assignmentNode;
        }
    }
}
