/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.sst;

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

    private void putOnSameLineIfShort(StringBuilder sb, SSTNode node) {
        String nodeText = node.accept(this);
        if (nodeText.contains("\n")) {
            sb.append("\n").append(indent()).append(INDENTATION);
            sb.append(nodeText.replace("\n", "\n" + INDENTATION));
        } else {
            sb.append(nodeText);
        }
    }

    // @Override
    // public String visit(AndSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(AnnAssignmentSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("Annotation: ").append(node.annotation.accept(this)).append('\n');
    //     sb.append(indent()).append("RHS: ").append(node.rhs.accept(this));
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(AnnotationSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("LHS: ").append(node.lhs.accept(this)).append("\n");
    //     sb.append(indent()).append("Type: ").append(node.type.accept(this));
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(AssertSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(AssignmentSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("LHS: ");
    //     if (node.lhs.length == 1) {
    //         putOnSameLineIfShort(sb, node.lhs[0]);
    //         sb.append("\n");
    //     } else {
    //         sb.append("\n");
    //         level++;
    //         for(SSTNode lh: node.lhs) {
    //             sb.append(indent()).append(lh.accept(this)).append("\n");
    //         }
    //         level--;
    //     }
    //     sb.append(indent()).append("RHS: ");
    //     putOnSameLineIfShort(sb, node.rhs);
    //     if (node.typeComment != null) {
    //         sb.append('\n');
    //         sb.append(indent()).append("TypeComment: ").append(node.typeComment.accept(this));
    //     }
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(AugAssignmentSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("Op: ").append(binOp(node.operation)).append("\n");
    //     sb.append(indent()).append("LHS: ").append(node.lhs.accept(this)).append("\n");
    //     sb.append(indent()).append("RHS: ").append(node.rhs.accept(this));
    //     level--;
    //     return sb.toString();
    // }

    // private String binOp(BinaryArithmeticSSTNode.Type type) {
    //     switch(type) {
    //         case EQ: return "==";
    //         case NOT_EQ: return "!=";
    //         case LT_EQ: return "<=";
    //         case LT: return "<";
    //         case GT_EQ: return ">=";
    //         case GT: return ">";
    //         case NOT_IN: return "not in";
    //         case IN: return "in";
    //         case IS_NOT: return "is not";
    //         case IS: return "is";
    //         case BIT_OR: return "|";
    //         case BIT_XOR: return "^";
    //         case BIT_AND: return "&";
    //         case LSHIFT: return "<<";
    //         case RSHIFT: return ">>";
    //         case ADD: return "+";
    //         case SUB: return "-";
    //         case MULT: return "*";
    //         case DIV: return "/";
    //         case FLOOR_DIV: return "//";
    //         case MOD: return "%";
    //         case MAT_MULT: return "@";
    //         case POW: return "**";
    //     }
    //     return "UNKNOWN";
    // }

    // @Override
    // public String visit(BinaryArithmeticSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("Op: ").append(binOp(node.operation)).append("\n");
    //     sb.append(indent()).append("LHS: ").append(node.left.accept(this)).append("\n");
    //     sb.append(indent()).append("RHS: ").append(node.right.accept(this));
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(CallSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("Target: ");
    //     putOnSameLineIfShort(sb, node.target);
    //     if ((node.args != null && node.args.length > 0)
    //             || (node.kwargs != null && node.kwargs.length > 0)) {
    //         sb.append("\n");
    //     }
    //     if (node.args != null && node.args.length > 0) {
    //         sb.append(indent()).append("Args: ");
    //         level++;
    //         for(SSTNode arg: node.args) {
    //             sb.append('\n').append(indent()).append(arg.accept(this));
    //         }
    //         sb.append('\n');
    //         level--;
    //     }
    //     if (node.kwargs != null && node.kwargs.length > 0) {
    //         sb.append(indent()).append("KWArgs: ");
    //         level++;
    //         for(SSTNode arg: node.kwargs) {
    //             sb.append('\n').append(indent()).append(arg.accept(this));
    //         }
    //         level--;
    //     }
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(ClassSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(CollectionSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(' ').append(node.type.name()).append("\n");
    //     level++;
    //     sb.append(indent()).append("Values:");
    //     level++;
    //     for (SSTNode value : node.values) {
    //         sb.append('\n').append(indent()).append(value.accept(this));
    //     }
    //     level--;
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(ComparisonSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(DecoratedSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(DecoratorSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(DelSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(ExceptSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(ExpressionStatementSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(FloatLiteralSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(ForComprehensionSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append('\n');
    //     level++;
    //     sb.append(indent()).append("Target: ").append(node.target.accept(this)).append('\n');
    //     sb.append(indent()).append("Iterator: ").append(node.iterator.accept(this)).append('\n');
    //     sb.append(indent()).append("Ifs:");
    //     level++;
    //     for (SSTNode i : node.ifs) {
    //         sb.append('\n').append(indent()).append(i.accept(this));
    //     }
    //     level--;
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(ComprehensionSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(' ').append(node.resultType.name()).append('\n');
    //     level++;
    //     sb.append(indent()).append("Element: ").append(node.element.accept(this));
    //     for (SSTNode n : node.generators) {
    //         sb.append('\n').append(indent()).append(n.accept(this));
    //     }
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(ForSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(FunctionDefSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node));
    //     level++;
    //     sb.append('\n').append(indent()).append("Name:").append(node.name);
    //     if (node.decorators != null) {
    //         sb.append('\n').append(indent()).append("Decorators:");
    //         for (SSTNode n : node.decorators) {
    //             sb.append('\n').append(indent()).append(n.accept(this));
    //         }
    //     }
    //     if (node.resultAnnotation != null) {
    //         sb.append('\n').append(indent()).append("Result Annotation: ").append(node.resultAnnotation.accept(this));
    //     }
    //     if (node.typeComment != null) {
    //         sb.append('\n').append(indent()).append("Type Comment: ").append(node.typeComment.accept(this));
    //     }
    //     sb.append('\n').append(indent()).append("---- Function body of ").append(node.name).append(" ----");
    //     for(SSTNode stm : node.body) {
    //         sb.append('\n').append(indent()).append(stm.accept(this));
    //     }
    //     if (sb.lastIndexOf("\n") != sb.length() - 1) {
    //         sb.append('\n');
    //     }
    //     sb.append(indent()).append("---- End of ").append(node.name).append(" function ----");
    //     level--;

    //     return sb.toString();
    // }

    // @Override
    // public String visit(GetAttributeSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(' ').append('\n');
    //     level++;
    //     sb.append(indent()).append("Receiver: ");
    //     putOnSameLineIfShort(sb, node.receiver);
    //     sb.append("\n");
    //     sb.append(indent()).append("Attr: ").append(node.name);
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(IfSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(ImportFromSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(ImportSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(LambdaSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(NotSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(NumberLiteralSSTNode.IntegerLiteralSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(" Value: \"").append(node.value).append("\"");
    //     return sb.toString();
    // }

    // @Override
    // public String visit(NumberLiteralSSTNode.BigIntegerLiteralSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(" Value: \"").append(node.value).append("\"");
    //     return sb.toString();
    // }

    // @Override
    // public String visit(OrSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(RaiseSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(ReturnSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(SimpleSSTNode node) {
    //     return addHeader(node) + " Type: " + (node.type.name());
    // }

    // @Override
    // public String visit(SliceSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(StarSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(" Expr: ").append(node.value.accept(this));
    //     return sb.toString();
    // }

    // @Override
    // public String visit(StringLiteralUtils.RawStringLiteralSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(StringLiteralUtils.BytesLiteralSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(StringLiteralUtils.FormatExpressionSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(StringLiteralUtils.FormatStringLiteralSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(SubscriptSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(TernaryIfSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(TrySSTNode node) {
    //     return addHeader(node);
    // }

    // private char unaryOp(UnarySSTNode.Type type) {
    //     switch(type) {
    //         case ADD: return '+';
    //         case SUB: return '-';
    //         case INVERT: return '~';
    //     }
    //     return '?';
    // }

    // @Override
    // public String visit(UnarySSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append("\n");
    //     level++;
    //     sb.append(indent()).append("Op: ").append(unaryOp(node.arithmetic)).append("\n");
    //     sb.append(indent()).append("Value: ").append(node.value.accept(this));
    //     level--;
    //     return sb.toString();
    // }

    // @Override
    // public String visit(VarLookupSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node)).append(" Value: \"").append(node.name).append('"');
    //     if (node.getContext() != ExprContext.Load) {
    //         sb.append(' ').append(node.getContext());
    //     }
    //     return sb.toString();
    // }

    // @Override
    // public String visit(WhileSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(WithSSTNode node) {
    //     return addHeader(node);
    // }

    // @Override
    // public String visit(YieldExpressionSSTNode node) {
    //     StringBuilder sb = new StringBuilder();
    //     sb.append(addHeader(node));
    //     if (node.isFrom) {
    //         sb.append(" from");
    //     }
    //     if (node.value != null) {
    //         level++;
    //         sb.append('\n').append(indent()).append(node.value.accept(this));
    //         level--;
    //     }
    //     return sb.toString();
    // }

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
        level++;
        sb.append("\nArgs:");
        level++;
        for(SSTNode child: node.args) {
            sb.append('\n').append(indent()).append(child.accept(this));
        }
        level -= 2;

        level++;
        sb.append("\nPosOnlyArgs:");
        level++;
        for(SSTNode child: node.posOnlyArgs) {
            sb.append('\n').append(indent()).append(child.accept(this));
        }
        level -= 2;

        level++;
        sb.append("\nKwOnlyArgs:");
        level++;
        for(SSTNode child: node.kwOnlyArgs) {
            sb.append('\n').append(indent()).append(child.accept(this));
        }
        level -= 2;

        level++;
        sb.append("\nKwarg:");
        level++;
        sb.append('\n').append(indent()).append(node.kwArg.accept(this));
        level -= 2;

        level++;
        sb.append("\nDefaults:");
        level++;
        for(SSTNode child: node.defaults) {
            sb.append('\n').append(indent()).append(child.accept(this));
        }
        level -= 2;

        level++;
        sb.append("\nKwDefaults:");
        level++;
        for(SSTNode child: node.kwDefaults) {
            sb.append('\n').append(indent()).append(child.accept(this));
        }
        level -= 2;

        return sb.toString();
    }

    @Override
    public String visit(ComprehensionTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Attribute node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.BoolOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Call node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Compare node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Constant node) {
        return addHeader(node) + " Value: " + (node.value == null ? node.longValue : node.value) + " Type: " + node.kind.toString();
    }

    @Override
    public String visit(ExprTy.Dict node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.DictComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.FormattedValue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.GeneratorExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.IfExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.JoinedStr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Lambda node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.List node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.ListComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Name node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.SetComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Subscript node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Tuple node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.UnaryOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Yield node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.YieldFrom node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(KeywordTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        sb.append(addHeader(node));
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
        sb.append(addHeader(node));
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
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.ClassDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.For node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.FunctionDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Global node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.If node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
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
        sb.append(addHeader(node));
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
