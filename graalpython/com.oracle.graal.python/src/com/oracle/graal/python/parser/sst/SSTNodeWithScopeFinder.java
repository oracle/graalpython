/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.parser.sst.NumberLiteralSSTNode.BigIntegerLiteralSSTNode;
import com.oracle.graal.python.parser.sst.NumberLiteralSSTNode.IntegerLiteralSSTNode;

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
            if (child2 != null) {
                return child2.accept(this);
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(AndSSTNode node) {
        if (node.values != null && isSubNode(node)) {
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
            if (node.lhs != null) {
                return visitNodes(node.lhs);
            }
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
        if (node.statements != null && isSubNode(node)) {
            return visitNodes(node.statements);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(BooleanLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(CallSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            for (SSTNode param : node.parameters.getArgs()) {
                if ((result = param.accept(this)) != null) {
                    return result;
                }
            }
            for (SSTNode param : node.parameters.getNameArgNodes()) {
                if ((result = param.accept(this)) != null) {
                    return result;
                }
            }
            for (SSTNode param : node.parameters.getKwArg()) {
                if ((result = param.accept(this)) != null) {
                    return result;
                }
            }
            if (node.target != null) {
                return node.target.accept(this);
            }
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
        if (node.values != null && isSubNode(node)) {
            return visitNodes(node.values);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ComparisonSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if ((result = node.firstValue.accept(this)) != null) {
                return result;
            }
            if (node.otherValues != null) {
                return visitNodes(node.otherValues);
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(DecoratedSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            for (DecoratorSSTNode decoratorSSTNode : node.decorators) {
                if ((result = decoratorSSTNode.accept(this)) != null) {
                    return result;
                }
            }
            if (node.decorated != null) {
                return node.decorated.accept(this);
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(DecoratorSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if (node.arg != null) {
                for (SSTNode param : node.arg.getArgs()) {
                    if ((result = param.accept(this)) != null) {
                        return result;
                    }
                }
                for (SSTNode param : node.arg.getNameArgNodes()) {
                    if ((result = param.accept(this)) != null) {
                        return result;
                    }
                }
                for (SSTNode param : node.arg.getKwArg()) {
                    if ((result = param.accept(this)) != null) {
                        return result;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(DelSSTNode node) {
        if (node.expressions != null && isSubNode(node)) {
            return visitNodes(node.expressions);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ExceptSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if (node.test != null && (result = node.test.accept(this)) != null) {
                return result;
            }
            return node.body.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ExpressionStatementSSTNode node) {
        if (node.expression != null && isSubNode(node)) {
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
            if (node.name != null && (result = node.name.accept(this)) != null) {
                return result;
            }
            if (node.target != null && (result = node.target.accept(this)) != null) {
                return result;
            }
            if (node.variables != null && (result = visitNodes(node.variables)) != null) {
                return result;
            }
            if (node.conditions != null && (result = visitNodes(node.conditions)) != null) {
                return result;
            }
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
            if (node.iterator != null && (result = node.iterator.accept(this)) != null) {
                return result;
            }
            if (node.elseStatement != null && (result = node.elseStatement.accept(this)) != null) {
                return result;
            }
            if (node.targets != null && (result = visitNodes(node.targets)) != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(FunctionDefSSTNode node) {
        if (isSubNode(node)) {
            if (isNode(node)) {
                return node;
            }
            SSTNodeWithScope result;
            if (node.argBuilder != null && ((result = checkParametersWithDefaultValue(node.argBuilder.getArgsWithDefValue())) != null ||
                            (result = checkParametersWithDefaultValue(node.argBuilder.getKWArgsWithDefValue())) != null)) {
                return result;
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
            if (node.elseStatement != null) {
                return node.elseStatement.accept(this);
            }
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
            SSTNodeWithScope result;
            if (node.args != null && ((result = checkParametersWithDefaultValue(node.args.getArgsWithDefValue())) != null ||
                            (result = checkParametersWithDefaultValue(node.args.getKWArgsWithDefValue())) != null)) {
                return result;
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
    public SSTNodeWithScope visit(IntegerLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(BigIntegerLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(OrSSTNode node) {
        if (node.values != null && isSubNode(node)) {
            return visitNodes(node.values);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(RaiseSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if (node.from != null && (result = node.from.accept(this)) != null) {
                return result;
            }
            if (node.value != null) {
                return node.value.accept(this);
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(ReturnSSTNode node) {
        if (node.value != null && isSubNode(node)) {
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
            if (node.start != null && (result = node.start.accept(this)) != null) {
                return result;
            }
            if (node.step != null && (result = node.step.accept(this)) != null) {
                return result;
            }
            if (node.stop != null && (result = node.stop.accept(this)) != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(StarSSTNode node) {
        if (node.value != null && isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
        return null;
    }

    @Override
    public SSTNodeWithScope visit(SubscriptSSTNode node) {
        if (isSubNode(node)) {
            SSTNodeWithScope result;
            if (node.receiver != null && (result = node.receiver.accept(this)) != null) {
                return result;
            }
            return node.subscript.accept(this);
        }
        return null;
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
            if (node.elseStatement != null && (result = node.elseStatement.accept(this)) != null) {
                return result;
            }
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
            if (node.elseStatement != null && (result = node.elseStatement.accept(this)) != null) {
                return result;
            }
            if (node.finallyStatement != null && (result = node.finallyStatement.accept(this)) != null) {
                return result;
            }
            if (node.exceptNodes != null && (result = visitNodes(node.exceptNodes)) != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(UnarySSTNode node) {
        if (node.value != null && isSubNode(node)) {
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
            if (node.test != null && (result = node.test.accept(this)) != null) {
                return result;
            }
            if (node.elseStatement != null && (result = node.elseStatement.accept(this)) != null) {
                return result;
            }
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
            if (node.expression != null && (result = node.target.accept(this)) != null) {
                return result;
            }
            if (node.target != null && (result = node.target.accept(this)) != null) {
                return result;
            }
            if (node.expression != null && (result = node.expression.accept(this)) != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public SSTNodeWithScope visit(YieldExpressionSSTNode node) {
        if (node.value != null && isSubNode(node)) {
            return node.value.accept(this);
        }
        return null;
    }

    private SSTNodeWithScope checkParametersWithDefaultValue(ArgDefListBuilder.ParameterWithDefValue[] parametersWithDefautValue) {
        if (parametersWithDefautValue != null) {
            SSTNodeWithScope result;
            for (ArgDefListBuilder.ParameterWithDefValue param : parametersWithDefautValue) {
                if ((result = param.value.accept(this)) != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
