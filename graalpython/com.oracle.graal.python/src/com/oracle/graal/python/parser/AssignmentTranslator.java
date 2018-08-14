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
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssignmentNode;
import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.antlr.Python3Parser.ExprContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Expr_stmtContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.ExprlistContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.NormassignContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Star_exprContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.TestContext;
import com.oracle.graal.python.runtime.PythonCore;

public class AssignmentTranslator extends Python3BaseVisitor<PNode> {

    private final NodeFactory factory;
    private final TranslationEnvironment environment;
    private final PythonTreeTranslator translator;
    private final PythonCore core;

    public AssignmentTranslator(PythonCore core, TranslationEnvironment environment, PythonTreeTranslator translator) {
        this.core = core;
        this.factory = core.getLanguage().getNodeFactory();
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
        PNode mostRhs = getAssignmentValue(normassign);
        PNode mostLhs = ctx.testlist_star_expr().accept(this);
        if (normassign.size() > 1) {
            return createMultiAssignment(normassign, mostRhs, mostLhs);
        } else {
            return createAssignment(mostLhs, mostRhs);
        }
    }

    private PNode createAssignment(PNode lhs, PNode rhs) {
        if (lhs instanceof ObjectLiteralNode) {
            return createDestructuringAssignment((PNode[]) ((ObjectLiteralNode) lhs).getObject(), rhs);
        } else if (lhs instanceof TupleLiteralNode) {
            return createDestructuringAssignment(((TupleLiteralNode) lhs).getValues(), rhs);
        } else if (lhs instanceof ListLiteralNode) {
            return createDestructuringAssignment(((ListLiteralNode) lhs).getValues(), rhs);
        } else {
            return ((ReadNode) lhs).makeWriteNode(rhs);
        }
    }

    private PNode createDestructuringAssignment(PNode[] leftHandSides, PNode rhs) {
        PNode[] statements = new PNode[leftHandSides.length];
        ReadNode[] temps = new ReadNode[leftHandSides.length];
        int starredIndex = -1;
        for (int i = 0; i < leftHandSides.length; i++) {
            ReadNode tempRead = environment.makeTempLocalVariable();
            temps[i] = tempRead;
            if (leftHandSides[i] instanceof StarredExpressionNode) {
                if (starredIndex != -1) {
                    throw core.raise(SyntaxError, "two starred expressions in assignment");
                }
                starredIndex = i;
                statements[i] = createAssignment(((StarredExpressionNode) leftHandSides[i]).getValue(), (PNode) tempRead);
            } else {
                statements[i] = createAssignment(leftHandSides[i], (PNode) tempRead);
            }
        }
        return factory.createDestructuringAssignment(rhs, temps, starredIndex, statements);
    }

    private PNode createMultiAssignment(List<NormassignContext> normassign, PNode mostRhs, PNode mostLhs) {
        ReadNode tmp = environment.makeTempLocalVariable();
        PNode tmpWrite = tmp.makeWriteNode(mostRhs);
        PNode[] assignments = new PNode[normassign.size() + 1];
        assignments[0] = tmpWrite;
        assignments[1] = createAssignment(mostLhs, (PNode) tmp);
        for (int i = 0; i < normassign.size() - 1; i++) {
            NormassignContext normassignContext = normassign.get(i);
            if (normassignContext.yield_expr() != null) {
                throw core.raise(SyntaxError, "assignment to yield expression not possible");
            }
            assignments[i + 2] = createAssignment(normassignContext.accept(this), (PNode) tmp);
        }
        return factory.createBlock(assignments);
    }

    @SuppressWarnings("unchecked")
    private PNode getAssignmentValue(List<NormassignContext> normassign) {
        Object mostRhsParsed = normassign.get(normassign.size() - 1).accept(translator);
        PNode mostRhs;
        if (mostRhsParsed instanceof List) {
            mostRhs = factory.createTupleLiteral((List<PNode>) mostRhsParsed);
        } else {
            mostRhs = (PNode) mostRhsParsed;
        }
        return mostRhs;
    }

    private PNode makeAugmentedAssignment(Expr_stmtContext ctx) {
        PNode rhs;
        if (ctx.yield_expr() != null) {
            rhs = (PNode) ctx.yield_expr().accept(translator);
        } else {
            rhs = (PNode) ctx.testlist().accept(translator);
        }
        return makeAugmentedAssignment((PNode) ctx.testlist_star_expr().accept(translator), ctx.augassign().getText(), rhs);
    }

    private PNode makeAugmentedAssignment(PNode lhs, String text, PNode rhs) {
        if (!(lhs instanceof ReadNode)) {
            throw core.raise(SyntaxError, "illegal expression for augmented assignment");
        }
        PNode binOp;
        binOp = factory.createInplaceOperation(text, lhs, rhs);
        PNode duplicate = factory.duplicate(lhs, PNode.class);
        PNodeUtil.clearSourceSections(duplicate);
        return ((ReadNode) duplicate).makeWriteNode(binOp);
    }

    private PNode visitTargetlist(ParserRuleContext ctx, int starSize) {
        if (starSize > 0) {
            if (starSize > 1) {
                throw core.raise(SyntaxError, "%d starred expressions in assigment", starSize);
            }
        }
        List<PNode> targets = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode) {
                continue;
            } else if (child instanceof Python3Parser.TestContext ||
                            child instanceof Python3Parser.Star_exprContext ||
                            child instanceof Python3Parser.ExprContext) {
                targets.add((PNode) child.accept(translator));
            } else {
                assert false;
            }
        }
        if (targets.size() == 1) {
            PNode pNode = targets.get(0);
            if (pNode instanceof ReadNode) {
                return pNode;
            } else if (pNode instanceof TupleLiteralNode || pNode instanceof ListLiteralNode) {
                return pNode;
            } else if (pNode instanceof StarredExpressionNode) {
                throw core.raise(SyntaxError, "starred assignment target must be in a list or tuple");
            } else {
                String text = ctx.getText();
                if (environment.isNonlocal(text)) {
                    throw core.raise(SyntaxError, "no binding for nonlocal variable \"%s\" found", text);
                }
                throw core.raise(SyntaxError, "Cannot assign to %s", pNode);
            }
        } else {
            return factory.createObjectLiteral(targets.toArray(new PNode[0]));
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

    public PNode translate(ExprlistContext exprlist) {
        return makeWriteNode(exprlist.accept(this));
    }

    public PNode translate(ExprContext expr) {
        return makeWriteNode((PNode) expr.accept(translator));
    }

    public PNode translate(Star_exprContext ctx) {
        return makeWriteNode((PNode) ctx.accept(translator));
    }

    public PNode translate(TestContext ctx) {
        return makeWriteNode((PNode) ctx.accept(translator));
    }

    private PNode makeWriteNode(PNode accept) {
        PNode assignmentNode = createAssignment(accept, EmptyNode.create());
        if (!(assignmentNode instanceof WriteNode)) {
            ReadNode tempLocal = environment.makeTempLocalVariable();
            assignmentNode = createAssignment(accept, (PNode) tempLocal);
            return new AssignmentNode(assignmentNode, tempLocal.makeWriteNode(EmptyNode.create()));
        } else {
            return assignmentNode;
        }
    }
}
