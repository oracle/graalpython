/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser.sst;


public class SSTTreePrinterVisitor implements SSTreeVisitor<String>{

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
    
    @Override
    public String visit(AndSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(AnnAssignmentSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Annotation: ").append(node.annotation.accept(this));
        sb.append(indent()).append("RHS: ").append(node.rhs.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(AnnotationSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("LHS: ").append(node.lhs.accept(this)).append("\n");
        sb.append(indent()).append("Type: ").append(node.type.accept(this)).append("\n");
        level--;
        return sb.toString();
    }

    @Override
    public String visit(AssertSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(AssignmentSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;

        level--;
        return sb.toString();
//        level++;
//        indent();
//        sb.append("Left hand sides:\n");
//        level++;
//        for(SSTNode lh: node.lhs) {
//            lh.accept(this);
//        }
//        level--;
//        indent();
//        sb.append("Right hand:\n");
//        level++;
//        node.rhs.accept(this);
//        level--;
//        level--;
//        return null;
    }

    @Override
    public String visit(AugAssignmentSSTNode node) {
        return addHeader(node);
    }

    private String binOp(BinaryArithmeticSSTNode.Type type) {
        switch(type) {
            case EQ: return "==";
            case NOT_EQ: return "!=";
            case LT_EQ: return "<=";
            case LT: return "<";
            case GT_EQ: return ">=";
            case GT: return ">";
            case NOT_IN: return "not in";
            case IN: return "in";
            case IS_NOT: return "is not";
            case IS: return "is";
            case BIT_OR: return "|";
            case BIT_COR: return "^";
            case BIT_AND: return "&";
            case LSHIFT: return "<<";
            case RSHIFT: return ">>";
            case ADD: return "+";
            case SUB: return "-";
            case MULT: return "*";
            case DIV: return "/";
            case FLOOR_DIV: return "//";
            case MOD: return "%";
            case MAT_MULT: return "@";
            case POW: return "**";
        }
        return "UNKNOWN";
    }
    
    @Override
    public String visit(BinaryArithmeticSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Op: ").append(binOp(node.operation)).append("\n");
        sb.append(indent()).append("LHS: ").append(node.left.accept(this)).append("\n");
        sb.append(indent()).append("RHS: ").append(node.right.accept(this)).append("\n");
        level--;
        return sb.toString();
    }

    @Override
    public String visit(BlockSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        for(SSTNode child: node.getStatements()) {
            sb.append(indent()).append(child.accept(this)).append("\n");
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(BooleanLiteralSSTNode node) {
        return addHeader(node) + " Value: " + (node.value ? "True" : "False");
    }

    @Override
    public String visit(CallSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ClassSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(CollectionSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ComparisonSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(DecoratedSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(DecoratorSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(DelSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ExceptSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ExpressionStatementSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(FloatLiteralSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ForComprehensionSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ForSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(FunctionDefSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(GetAttributeSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(IfSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ImportFromSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ImportSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(LambdaSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(NotSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(NumberLiteralSSTNode.IntegerLiteralSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Value: \"").append(node.value).append("\"\n");
        return sb.toString();
    }

    @Override
    public String visit(NumberLiteralSSTNode.BigIntegerLiteralSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Value: \"").append(node.value).append("\"\n");
        return sb.toString();
    }

    @Override
    public String visit(OrSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(RaiseSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(ReturnSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(SimpleSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(SliceSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(StarSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(StringLiteralSSTNode.FormatExpressionSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(SubscriptSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(TernaryIfSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(TrySSTNode node) {
        return addHeader(node);
    }

    private char unaryOp(UnarySSTNode.Type type) {
        switch(type) {
            case ADD: return '+';
            case SUB: return '-';
            case INVERT: return '~';
        }
        return '?';
    }
    
    @Override
    public String visit(UnarySSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Op: ").append(unaryOp(node.arithmetic)).append("\n");
        sb.append(indent()).append("Value: ").append(node.value.accept(this)).append("\n");
        level--;
        return sb.toString();
    }

    @Override
    public String visit(VarLookupSSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Value: \"").append(node.name);
        return sb.toString();
    }

    @Override
    public String visit(WhileSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(WithSSTNode node) {
        return addHeader(node);
    }

    @Override
    public String visit(YieldExpressionSSTNode node) {
        return addHeader(node);
    }

}
