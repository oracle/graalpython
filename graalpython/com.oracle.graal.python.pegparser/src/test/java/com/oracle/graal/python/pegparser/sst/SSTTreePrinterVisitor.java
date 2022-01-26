/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.sst;

import com.oracle.graal.python.pegparser.ExprContext;
import java.math.BigInteger;


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

    private void putOnSameLineIfShort(StringBuilder sb, SSTNode node) {
        String nodeText = node.accept(this);
        if (nodeText.contains("\n")) {
            sb.append("\n").append(indent()).append(INDENTATION);
            sb.append(nodeText.replace("\n", "\n" + INDENTATION));
        } else {
            sb.append(nodeText);
        }
    }

    @Override
    public String visit(AliasTy node) {
        return addHeader(node) + " " + node.name + " as " + node.asName;
    }

    @Override
    public String visit(ArgTy node) {
        return addHeader(node) + " " + node.arg;
    }

    @Override
    public String visit(ArgumentsTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));

        if (node.args.length > 0) {
            level++;
            sb.append('\n').append(indent()).append("Args:");
            level++;
            for(SSTNode child: node.args) {
                sb.append('\n').append(indent()).append(child.accept(this));
            }
            level -= 2;
        }

        if (node.posOnlyArgs.length > 0) {
            level++;
            sb.append('\n').append(indent()).append("PosOnlyArgs:");
            level++;
            for(SSTNode child: node.posOnlyArgs) {
                sb.append('\n').append(indent()).append(child.accept(this));
            }
            level -= 2;
        }

        if (node.varArg != null) {
            level++;
            sb.append('\n').append(indent()).append("VarArg: ").append(node.varArg.accept(this));
            level--;
        }

        if (node.kwOnlyArgs.length > 0) {
            level++;
            sb.append('\n').append(indent()).append("KwOnlyArgs:");
            level++;
            for(SSTNode child: node.kwOnlyArgs) {
                sb.append('\n').append(indent()).append(child.accept(this));
            }
            level -= 2;
        }

        if (node.kwArg != null) {
            level++;
            sb.append('\n').append(indent()).append("Kwarg: ").append(node.kwArg.accept(this));
            level--;
        }

        if (node.defaults.length > 0) {
            level++;
            sb.append('\n').append(indent()).append("Defaults:");
            level++;
            for(SSTNode child: node.defaults) {
                sb.append('\n').append(indent()).append(child.accept(this));
            }
            level -= 2;
        }

        if (node.kwDefaults.length > 0) {
            level++;
            sb.append('\n').append(indent()).append("KwDefaults:");
            level++;
            for(SSTNode child : node.kwDefaults) {
                if (child != null) {
                    sb.append('\n').append(indent()).append(child.accept(this));
                }
            }
            level -= 2;
        }

        return sb.toString();
    }

    @Override
    public String visit(ComprehensionTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Target: ").append(node.target.accept(this)).append('\n');
        sb.append(indent()).append("Iterator: ").append(node.iter.accept(this));
        if (node.ifs != null && node.ifs.length > 0) {
            sb.append('\n').append(indent()).append("Ifs:");
            level++;
            for (SSTNode i : node.ifs) {
                sb.append('\n').append(indent()).append(i.accept(this));
            }
            level--;
        }
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
        sb.append("\n");
        sb.append(indent()).append("Attr: ").append(node.attr);
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
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Op: ").append(node.op).append("\n");
        sb.append(indent()).append("LHS: ").append(node.left.accept(this)).append("\n");
        sb.append(indent()).append("RHS: ").append(node.right.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.BoolOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ").append(node.op);
        level++;
        for (ExprTy e : node.values) {
            sb.append('\n').append(indent()).append(e.accept(this));
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
        if (node.args != null && node.args.length > 0) {
            sb.append('\n').append(indent()).append("Args:");
            level++;
            for(SSTNode arg: node.args) {
                sb.append('\n').append(indent()).append(arg.accept(this));
            }
            level--;
        }
        if (node.keywords != null && node.keywords.length > 0) {
            sb.append('\n').append(indent()).append("KWArgs:");
            level++;
            for(SSTNode arg: node.keywords) {
                sb.append('\n').append(indent()).append(arg.accept(this));
            }
            level--;
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Compare node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("LHS: ").append(node.left.accept(this)).append("\n");
        for (int i = 0; i < node.ops.length; i++) {
            sb.append(indent()).append("Op: ").append(node.ops[i].toString()).append("\n");
            sb.append(indent()).append("RHS: ").append(node.comparators[i].accept(this));
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
            sb.append('\n').append(indent()).append("Key: ").append(node.keys[i].accept(this));
            sb.append('\n').append(indent()).append("Val: ").append(node.values[i].accept(this));
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
            sb.append('\n').append(indent()).append(n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.FormattedValue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        if (node.formatSpec != null) {
            sb.append(indent()).append("Spec: ").append(node.formatSpec.accept(this)).append('\n');
        }
        sb.append(indent()).append("Value: ").append(node.value.accept(this));
        if (node.conversion != null) {
            sb.append('\n').append(indent()).append("Conversion: ").append(node.conversion);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.GeneratorExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Element: ").append(node.element.accept(this));
        for (SSTNode n : node.generators) {
            sb.append('\n').append(indent()).append(n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.IfExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Test: ").append(node.test.accept(this));
        sb.append('\n').append(indent()).append("Then: ").append(node.body.accept(this));
        sb.append('\n').append(indent()).append("Else: ").append(node.orElse.accept(this));
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
            sb.append('\n').append(indent()).append(v.accept(this));
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
            sb.append('\n').append(indent()).append(node.args.accept(this));
        }
        sb.append('\n').append(indent()).append("Body: ").append(node.body.accept(this));
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
                sb.append('\n').append(indent()).append(value.accept(this));
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
            sb.append('\n').append(indent()).append(n.accept(this));
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
            sb.append('\n').append(indent()).append(value.accept(this));
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
            sb.append('\n').append(indent()).append(n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Slice node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Starred node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Expr: ").append(node.value.accept(this));
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
        sb.append('\n').append(indent()).append("Slice: ").append(node.slice.accept(this));
        sb.append('\n').append(indent()).append("Value: ").append(node.value.accept(this));
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
                sb.append('\n').append(indent()).append(value.accept(this));
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
            sb.append('\n').append(indent()).append(node.value.accept(this));
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
            sb.append('\n').append(indent()).append(node.value.accept(this));
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(KeywordTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append(node.arg).append(": ").append(node.value.accept(this));
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
        level++;
        for(SSTNode child: node.body) {
            sb.append('\n').append(indent()).append(child.accept(this));
        }
        level--;
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
            for(SSTNode lh: node.targets) {
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
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Op: ").append(node.op).append("\n");
        sb.append(indent()).append("LHS: ").append(node.target.accept(this)).append("\n");
        sb.append(indent()).append("RHS: ").append(node.value.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.ClassDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" ").append(node.name);
        level++;
        if (node.decoratorList != null) {
            sb.append("\n").append(indent()).append("Decorators:");
            level++;
            for (ExprTy e : node.decoratorList) {
                sb.append("\n").append(indent()).append(e.accept(this));
            }
            level--;
        }
        if (node.bases.length > 0) {
            sb.append("\n").append(indent()).append("Bases:");
            level++;
            for (ExprTy e : node.bases) {
                sb.append("\n").append(indent()).append(e.accept(this));
            }
            level--;
        }
        if (node.keywords.length > 0) {
            sb.append("\n").append(indent()).append("Keywords:");
            level++;
            for (KeywordTy e : node.keywords) {
                sb.append("\n").append(indent()).append(e.accept(this));
            }
            level--;
        }
        sb.append('\n').append(indent()).append("---- Class body of ").append(node.name).append(" ----");
        for(SSTNode stm : node.body) {
            sb.append('\n').append(indent()).append(stm.accept(this));
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
        sb.append('\n').append(indent()).append("Target: ").append(node.target.accept(this));
        sb.append('\n').append(indent()).append("Iter: ").append(node.iter.accept(this));
        sb.append('\n').append(indent()).append("Body:");
        level++;
        for (StmtTy s : node.body) {
            sb.append('\n').append(indent()).append(s.accept(this));
        }
        level--;
        if (node.orElse != null) {
            sb.append('\n').append(indent()).append("Else:");
            level++;
            for (StmtTy s : node.body) {
                sb.append('\n').append(indent()).append(s.accept(this));
            }
            level--;
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.FunctionDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Name:").append(node.name);
        if (node.decoratorList != null) {
            sb.append('\n').append(indent()).append("Decorators:");
            for (SSTNode n : node.decoratorList) {
                sb.append('\n').append(indent()).append(n.accept(this));
            }
        }
        if (node.args != null) {
            sb.append('\n').append(indent()).append(node.args.accept(this));
        }
        if (node.returns != null) {
            sb.append('\n').append(indent()).append("Result Annotation: ").append(node.returns.accept(this));
        }
        if (node.typeComment != null) {
            sb.append('\n').append(indent()).append("Type Comment: ").append(node.typeComment);
        }
        sb.append('\n').append(indent()).append("---- Function body of ").append(node.name).append(" ----");
        for(SSTNode stm : node.body) {
            sb.append('\n').append(indent()).append(stm.accept(this));
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
        sb.append('\n').append(indent()).append("Test: ").append(node.test.accept(this));
        sb.append('\n').append(indent()).append("Then:");
        level++;
        for (StmtTy s : node.body) {
            sb.append('\n').append(indent()).append(s.accept(this));
        }
        level--;
        if (node.orElse != null) {
            sb.append('\n').append(indent()).append("Else:");
            level++;
            for (StmtTy s : node.orElse) {
                sb.append('\n').append(indent()).append(s.accept(this));
            }
            level--;
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Import node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.ImportFrom node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Try.ExceptHandler node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.While node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Condition: ").append(node.test.accept(this));
        sb.append('\n').append(indent()).append("Body:");
        level++;
        for (StmtTy s : node.body) {
            sb.append('\n').append(indent()).append(s.accept(this));
        }
        level--;
        if (node.orElse != null) {
            sb.append('\n').append(indent()).append("Else:");
            level++;
            for (StmtTy s : node.body) {
                sb.append('\n').append(indent()).append(s.accept(this));
            }
            level--;
        }
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
