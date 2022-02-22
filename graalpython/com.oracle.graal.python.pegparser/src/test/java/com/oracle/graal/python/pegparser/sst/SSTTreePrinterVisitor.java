/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.sst;

import java.math.BigInteger;

import com.oracle.graal.python.pegparser.ExprContext;

public class SSTTreePrinterVisitor implements SSTreeVisitor<String> {

    private static final String INDENTATION = "    ";
    private int level = 0;

    private String indent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(INDENTATION);
        }
        return sb.toString();
    }

    private String addHeader(SSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getClass().getSimpleName()).append("[").append(node.getStartOffset());
        sb.append(", ").append(node.getEndOffset()).append("]");
        return sb.toString();
    }

    private StringBuilder appendNewLineIndented(StringBuilder sb, String str) {
        return sb.append('\n').append(indent()).append(str);
    }

    private void putOnSameLineIfShort(StringBuilder sb, SSTNode node) {
        String nodeText = node.accept(this);
        if (nodeText.contains("\n")) {
            sb.append("\n").append(indent()).append(INDENTATION);
            sb.append(nodeText.replace("\n", "\n" + INDENTATION));
        } else {
            sb.append(nodeText);
        }
    }

    private void appendNode(StringBuilder sb, String name, SSTNode node) {
        if (node != null) {
            appendNewLineIndented(sb, name + ": ").append(node.accept(this));
        }
    }

    private void appendString(StringBuilder sb, String name, Object value) {
        if (value != null) {
            appendNewLineIndented(sb, name).append(value);
        }
    }

    private void appendNodes(StringBuilder sb, String header, SSTNode[] nodes) {
        if (nodes != null && nodes.length > 0) {
            appendNewLineIndented(sb, header + ':');
            level++;
            for (SSTNode s : nodes) {
                if (s != null) {
                    appendNewLineIndented(sb, s.accept(this));
                }
            }
            level--;
        }
    }

    @Override
    public String visit(AliasTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.name);
        if (node.asName != null) {
            sb.append(" as ").append(node.asName);
        }
        return sb.toString();
    }

    @Override
    public String visit(ArgTy node) {
        return addHeader(node) + " " + node.arg;
    }

    @Override
    public String visit(ArgumentsTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Args", node.args);
        appendNodes(sb, "PosOnlyArgs", node.posOnlyArgs);
        appendNode(sb, "VarArg", node.varArg);
        appendNodes(sb, "KwOnlyArgs", node.kwOnlyArgs);
        appendNode(sb, "Kwarg", node.kwArg);
        appendNodes(sb, "Defaults", node.defaults);
        appendNodes(sb, "KwDefaults", node.kwDefaults);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ComprehensionTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Target", node.target);
        appendNode(sb, "Iterator", node.iter);
        appendNodes(sb, "Ifs", node.ifs);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Attribute node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(' ').append('\n');
        level++;
        sb.append(indent()).append("Receiver: ");
        putOnSameLineIfShort(sb, node.value);
        appendString(sb, "Attr: ", node.attr);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Await node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.BinOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Op: ", node.op);
        appendNode(sb, "LHS", node.left);
        appendNode(sb, "RHS", node.right);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.BoolOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ").append(node.op);
        level++;
        for (ExprTy e : node.values) {
            appendNewLineIndented(sb, e.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Call node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Target: ");
        putOnSameLineIfShort(sb, node.func);
        appendNodes(sb, "Args", node.args);
        appendNodes(sb, "KWArgs", node.keywords);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Compare node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("LHS: ");
        putOnSameLineIfShort(sb, node.left);
        for (int i = 0; i < node.ops.length; i++) {
            sb.append("\n").append(indent()).append("Op: ").append(node.ops[i].toString()).append("\n");
            sb.append(indent()).append("RHS: ");
            putOnSameLineIfShort(sb, node.comparators[i]);
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Constant node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.kind).append("[").append(node.getStartOffset());
        sb.append(", ").append(node.getEndOffset()).append("]");
        sb.append(" Value: ");
        switch (node.kind) {
            case LONG:
                sb.append(node.longValue);
                break;
            case DOUBLE:
                sb.append(Double.longBitsToDouble(node.longValue));
                break;
            case COMPLEX:
                sb.append(Double.longBitsToDouble(node.longValue)).append('j');
                break;
            default:
                if (node.value == null || node.value instanceof Boolean || node.value instanceof BigInteger || node.value instanceof String) {
                    sb.append(node.value);
                } else {
                    sb.append("<unprintable value>");
                }
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Dict node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Values:");
        level++;
        for (int i = 0; i < node.keys.length; i++) {
            appendNode(sb, "Key", node.keys[i]);
            appendNode(sb, "Val", node.values[i]);
        }
        level--;
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.DictComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Key: ").append(node.key.accept(this)).append('\n');
        sb.append(indent()).append("Value: ").append(node.value.accept(this));
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.FormattedValue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Spec", node.formatSpec);
        appendNode(sb, "Value", node.value);
        appendString(sb, "Conversion: ", node.conversion);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.GeneratorExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Element", node.element);
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.IfExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Test", node.test);
        appendNode(sb, "Then", node.body);
        appendNode(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.JoinedStr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append(" Values:");
        level++;
        for (ExprTy v : node.values) {
            appendNewLineIndented(sb, v.accept(this));
        }
        level -= 2;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Lambda node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        if (node.args != null) {
            appendNewLineIndented(sb, node.args.accept(this));
        }
        appendNode(sb, "Body", node.body);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.List node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.context != null && node.context != ExprContext.Load) {
            sb.append(" Context: ").append(node.context);
        }
        if (node.elements != null) {
            sb.append('\n');
            level++;
            sb.append(indent()).append("Values:");
            level++;
            for (SSTNode value : node.elements) {
                appendNewLineIndented(sb, value.accept(this));
            }
            level--;
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.ListComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Element: ").append(node.element.accept(this));
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Name node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Value: \"").append(node.id).append('"');
        if (node.context != ExprContext.Load) {
            sb.append(' ').append(node.context);
        }
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.NamedExpr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Set node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Values:");
        level++;
        for (SSTNode value : node.elements) {
            appendNewLineIndented(sb, value.accept(this));
        }
        level--;
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.SetComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Element: ").append(node.element.accept(this));
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Slice node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Start: ");
        if (node.lower != null) {
            putOnSameLineIfShort(sb, node.lower);
        }
        sb.append('\n').append(indent()).append("Stop: ");
        if (node.upper != null) {
            putOnSameLineIfShort(sb, node.upper);
        }
        sb.append('\n').append(indent()).append("Step: ");
        if (node.step != null) {
            putOnSameLineIfShort(sb, node.step);
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Starred node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        if (node.context != null) {
            sb.append('\n').append(indent()).append(" Context: ").append(node.context);
        }
        sb.append('\n').append(indent()).append(" Expr: ");
        putOnSameLineIfShort(sb, node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Subscript node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.context != null && node.context != ExprContext.Load) {
            sb.append(" Context: ").append(node.context);
        }
        level++;
        appendNode(sb, "Slice", node.slice);
        appendNode(sb, "Value", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Tuple node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.context != null && node.context != ExprContext.Load) {
            sb.append(" Context: ").append(node.context);
        }
        if (node.elements != null) {
            sb.append('\n');
            level++;
            sb.append(indent()).append("Values:");
            level++;
            for (SSTNode value : node.elements) {
                appendNewLineIndented(sb, value.accept(this));
            }
            level--;
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.UnaryOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Op: ").append(node.op.toString()).append("\n");
        sb.append(indent()).append("Value: ").append(node.operand.accept(this));
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Yield node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.value != null) {
            level++;
            appendNewLineIndented(sb, node.value.accept(this));
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.YieldFrom node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.value != null) {
            level++;
            appendNewLineIndented(sb, node.value.accept(this));
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(KeywordTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNewLineIndented(sb, node.arg).append(": ").append(node.value.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ModTy.Expression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ModTy.FunctionType node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ModTy.Interactive node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ModTy.Module node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.body != null) {
            level++;
            for (SSTNode child : node.body) {
                appendNewLineIndented(sb, child.accept(this));
            }
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ModTy.TypeIgnore node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.AnnAssign node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(node.isSimple ? "simple" : "not simple").append("\n");
        level++;
        sb.append(indent()).append("Annotation: ").append(node.annotation.accept(this)).append('\n');
        sb.append(indent()).append("LHS: ").append(node.target.accept(this)).append('\n');
        sb.append(indent()).append("RHS: ").append(node.value.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Assert node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Test", node.test);
        appendNode(sb, "Msg", node.msg);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Assign node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("LHS: ");
        if (node.targets.length == 1) {
            putOnSameLineIfShort(sb, node.targets[0]);
            sb.append("\n");
        } else {
            sb.append("\n");
            level++;
            for (SSTNode lh : node.targets) {
                sb.append(indent()).append(lh.accept(this)).append("\n");
            }
            level--;
        }
        sb.append(indent()).append("RHS: ");
        putOnSameLineIfShort(sb, node.value);
        if (node.typeComment != null) {
            sb.append('\n');
            sb.append(indent()).append("TypeComment: ").append(node.typeComment);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.AsyncFor node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.AsyncFunctionDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.AsyncWith node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.AugAssign node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Op: ", node.op);
        appendNode(sb, "LHS", node.target);
        appendNode(sb, "RHS", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.ClassDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" ").append(node.name);
        level++;
        appendNodes(sb, "Decorators", node.decoratorList);
        appendNodes(sb, "Bases", node.bases);
        appendNodes(sb, "Keywords", node.keywords);
        appendNewLineIndented(sb, "---- Class body of ").append(node.name).append(" ----");
        for (SSTNode stm : node.body) {
            appendNewLineIndented(sb, stm.accept(this));
        }
        if (sb.lastIndexOf("\n") != sb.length() - 1) {
            sb.append('\n');
        }
        sb.append(indent()).append("---- End of ").append(node.name).append(" class ----");
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Delete node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Targets:");
        level++;
        for (SSTNode n : node.targets) {
            sb.append('\n').append(indent()).append(n.accept(this));
        }
        level--;
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Expr node) {
        return node.value.accept(this);
    }

    @Override
    public String visit(StmtTy.For node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Target", node.target);
        appendNode(sb, "Iter", node.iter);
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.FunctionDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNewLineIndented(sb, "Name:").append(node.name);
        if (node.decoratorList != null) {
            appendNewLineIndented(sb, "Decorators:");
            for (SSTNode n : node.decoratorList) {
                appendNewLineIndented(sb, n.accept(this));
            }
        }
        if (node.args != null) {
            appendNewLineIndented(sb, node.args.accept(this));
        }
        if (node.returns != null) {
            appendNode(sb, "Result Annotation", node.returns);
        }
        if (node.typeComment != null) {
            appendNewLineIndented(sb, "Type Comment: ").append(node.typeComment);
        }
        appendNewLineIndented(sb, "---- Function body of ").append(node.name).append(" ----");
        for (SSTNode stm : node.body) {
            appendNewLineIndented(sb, stm.accept(this));
        }
        if (sb.lastIndexOf("\n") != sb.length() - 1) {
            sb.append('\n');
        }
        sb.append(indent()).append("---- End of ").append(node.name).append(" function ----");
        level--;

        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Global node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ");
        for (String id : node.names) {
            sb.append(id).append(' ');
        }
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.If node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Test", node.test);
        appendNodes(sb, "Then", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Import node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        for (AliasTy a : node.names) {
            appendNewLineIndented(sb, a.accept(this));
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.ImportFrom node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Level ").append(node.level).append(' ').append(node.module);
        level++;
        for (AliasTy a : node.names) {
            appendNewLineIndented(sb, a.accept(this));
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Case node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchAs node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchClass node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchMapping node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchOr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchSequence node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchSingleton node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchStar node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match.Pattern.MatchValue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.NonLocal node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ");
        for (String id : node.names) {
            sb.append(id).append(' ');
        }
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Raise node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Exc", node.exc);
        appendNode(sb, "Cause", node.cause);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Return node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.value != null) {
            sb.append(" ");
            putOnSameLineIfShort(sb, node.value);
        }
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Try node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Except", node.handlers);
        appendNodes(sb, "Finally", node.finalBody);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Try.ExceptHandler node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Type", node.type);
        appendString(sb, "Var: ", node.name);
        appendNodes(sb, "Body", node.body);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.While node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Condition", node.test);
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.With node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.With.Item node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Break node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Continue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Pass node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }
}
