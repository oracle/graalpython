/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;

public class SSTNodeWithScopeFinder implements SSTreeVisitor<SSTNodeWithScope> {

    private final int startOffset;
    private final int endOffset;

    public SSTNodeWithScopeFinder(int startOffset, int endOffset) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    private SSTNodeWithScope visitNodes(SSTNode[] nodes) {
        SSTNodeWithScope result = null;
        if (nodes.length > 0 && nodes[0].startOffset <= startOffset && nodes[nodes.length - 1].endOffset >= endOffset) {
            for (SSTNode node : nodes) {
                if (isSubNode(node)) {
                    if ((result = node.accept(this)) != null) {
                        return result;
                    }
                } else {
                    if (endOffset <= node.startOffset) {
                        break;
                    }
                }

            }
        }
        return null;
    }

    private boolean isSubNode(SSTNode node) {
        return node.startOffset <= startOffset && endOffset <= node.endOffset;
    }

    private boolean isNode(SSTNode node) {
        return node.startOffset == startOffset && node.endOffset == endOffset;
    }

    private SSTNodeWithScope check(SSTNode parent, SSTNode child1, SSTNode child2) {
        if (isSubNode(parent)) {
            SSTNodeWithScope result;
            if ((result = child1.accept(this)) != null) {
                return result;
            }
            return child2.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(AndSSTNode node) {
        if (isSubNode(node)) {
            return visitNodes(node.values);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(AnnAssignmentSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.rhs.accept(this)) != null) {
                return result;
            }
            if ((result = node.type.accept(this)) != null) {
                return result;
            }
            return visitNodes(node.lhs);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(AssertSSTNode node) {
        return check(node, node.test, node.message);
    }

    @Override
    public SSTNodeWithScope visit(AssignmentSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.rhs.accept(this)) != null) {
                return result;
            }
            return visitNodes(node.lhs);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(AugAssignmentSSTNode node) {
        return check(node, node.lhs, node.rhs);
    }

    @Override
    public SSTNodeWithScope visit(BinaryArithmeticSSTNode node) {
        return check(node, node.right, node.left);
    }

    @Override
    public SSTNodeWithScope visit(BlockSSTNode node) {
        return visitNodes(node.statements);
    }

    @Override
    public SSTNodeWithScope visit(BooleanLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(CallSSTNode node) {
        if (isSubNode(node)) {
            return node.target.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ClassSSTNode node) {
        if (isSubNode(node)) {
            if (isNode(node)) {
                return node;
            }
            return node.body.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(CollectionSSTNode node) {
        return visitNodes(node.values);
    }

    @Override
    public SSTNodeWithScope visit(ComparisonSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.firstValue.accept(this)) != null) {
                return result;
            }
            return visitNodes(node.otherValues);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(DecoratedSSTNode node) {
        if (isSubNode(node)) {
            return node.decorated.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(DecoratorSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(DelSSTNode node) {
        if (isSubNode(node)) {
            return visitNodes(node.expressions);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ExceptSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.test.accept(this)) != null) {
                return result;
            }
            return node.body.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ExpressionStatementSSTNode node) {
        if (isSubNode(node)) {
            return node.expression.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(FloatLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ForComprehensionSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.iterator.accept(this)) != null) {
                return result;
            }
            if ((result = node.name.accept(this)) != null) {
                return result;
            }
            if ((result = node.target.accept(this)) != null) {
                return result;
            }
            if ((result = visitNodes(node.variables)) != null) {
                return result;
            }
            return visitNodes(node.conditions);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ForSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.body.accept(this)) != null) {
                return result;
            }
            if ((result = node.iterator.accept(this)) != null) {
                return result;
            }
            if ((result = node.elseStatement.accept(this)) != null) {
                return result;
            }
            return visitNodes(node.targets);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(FunctionDefSSTNode node) {
        if (isSubNode(node)) {
            if (isNode(node)) {
                return node;
            }
            return node.body.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(GetAttributeSSTNode node) {
        if (isSubNode(node)) {
            return node.receiver.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(IfSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.test.accept(this)) != null) {
                return result;
            }
            if ((result = node.thenStatement.accept(this)) != null) {
                return result;
            }
            return node.elseStatement.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ImportFromSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ImportSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(LambdaSSTNode node) {
        if (isSubNode(node)) {
            if (isNode(node)) {
                return node;
            }
            return node.body.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(NotSSTNode node) {
        if (isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(NumberLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(OrSSTNode node) {
        if (isSubNode(node)) {
            return visitNodes(node.values);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(RaiseSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.from.accept(this)) != null) {
                return result;
            }
            return node.value.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ReturnSSTNode node) {
        if (isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(SimpleSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(SliceSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.start.accept(this)) != null) {
                return result;
            }
            if ((result = node.step.accept(this)) != null) {
                return result;
            }
            return node.stop.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(StarSSTNode node) {
        if (isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(StringLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(SubscriptSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.receiver.accept(this)) != null) {
                return result;
            }
            return node.subscript.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(TernaryArithmeticSSTNode node) {
        return check(node, node.left, node.right);
    }

    @Override
    public SSTNodeWithScope visit(TernaryIfSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.test.accept(this)) != null) {
                return result;
            }
            if ((result = node.thenStatement.accept(this)) != null) {
                return result;
            }
            return node.elseStatement.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(TrySSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.body.accept(this)) != null) {
                return result;
            }
            if ((result = node.elseStatement.accept(this)) != null) {
                return result;
            }
            if ((result = node.finallyStatement.accept(this)) != null) {
                return result;
            }
            return visitNodes(node.exceptNodes);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(UnarySSTNode node) {
        if (isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(VarLookupSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(WhileSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.body.accept(this)) != null) {
                return result;
            }
            if ((result = node.test.accept(this)) != null) {
                return result;
            }
            return node.elseStatement.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(WithSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.body.accept(this)) != null) {
                return result;
            }
            if ((result = node.target.accept(this)) != null) {
                return result;
            }
            return node.expression.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(YieldExpressionSSTNode node) {
        if (isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

}
