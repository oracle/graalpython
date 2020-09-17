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

import com.oracle.graal.python.parser.sst.ArgDefListBuilder.Parameter;
import java.util.LinkedList;

public class SSTCheckOffsetVisitor implements SSTreeVisitor<Boolean> {

    private LinkedList<SSTNode> parentStack = new LinkedList<>();
    private String message;

    public SSTCheckOffsetVisitor(SSTNode parent) {
        parentStack.push(parent);
    }

    public String getMessage() {
        return message;
    }

    private boolean checkParent(SSTNode node) {
        SSTNode outer = parentStack.element();
        boolean result = outer.startOffset <= node.startOffset && node.endOffset <= outer.endOffset && outer.startOffset < outer.endOffset && node.startOffset < node.endOffset;
        if (result) {
            return true;
        }
        message = "Subnode " + nodeInfo(node) + " doesn't fit in parent node " + nodeInfo(outer) + ".";
        return false;
    }

    private boolean checkArrayWithOverlap(SSTNode[] nodes, String kind) {
        SSTNode previous = null;
        for (SSTNode node : nodes) {
            if (node != null) {
                if (previous != null) {
                    if (previous.endOffset > node.startOffset) {
                        message = "In " + nodeInfo(parentStack.element()) + " are overlaping " + kind + ": " + nodeInfo(previous) + " and " + nodeInfo(node);
                        return false;
                    }
                }
                if (!node.accept(this)) {
                    return false;
                }
                previous = node;
            }
        }
        return true;
    }

    private boolean checkArgListBuilder(ArgListBuilder builder) {
        if (builder != null) {
            SSTNode[] args = builder.getArgs();
            if (args != null) {
                if (!checkArrayWithOverlap(args, "arguments")) {
                    return false;
                }
            }
            args = builder.getNameArgNodes();
            if (args != null) {
                if (!checkArrayWithOverlap(args, "name arguments")) {
                    return false;
                }
            }
            args = builder.getKwArg();
            if (args != null) {
                if (!checkArrayWithOverlap(args, "keyword arguments")) {
                    return false;
                }
            }
            args = builder.getStarArg();
            if (args != null) {
                if (!checkArrayWithOverlap(args, "star arguments")) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkArgDefListBuider(ArgDefListBuilder builder) {
        if (builder != null) {
            Parameter[] params = builder.getArgs();
            SSTNode previousType = null;
            SSTNode previousValue = null;
            if (params != null) {
                for (Parameter param : params) {
                    if (param.type != null) {
                        if (previousType != null && param.type.startOffset <= previousType.endOffset) {
                            message = "Parameter types in " + nodeInfo(parentStack.element()) + " are overlaping: " + nodeInfo(previousType) + " and " + nodeInfo(param.type);
                            return false;
                        }
                        if (previousValue != null && param.type.startOffset <= previousType.endOffset) {
                            message = "Parameter type and default value in " + nodeInfo(parentStack.element()) + " are overlaping: " + nodeInfo(previousValue) + " and " + nodeInfo(param.type);
                            return false;
                        }
                        if (!param.type.accept(this)) {
                            return false;
                        }
                        previousType = param.type;
                    }
                    if (param instanceof ArgDefListBuilder.ParameterWithDefValue) {
                        SSTNode paramValue = ((ArgDefListBuilder.ParameterWithDefValue) param).value;
                        if (paramValue != null) {
                            if (previousValue != null && paramValue.startOffset <= previousValue.endOffset) {
                                message = "Parameter default values in " + nodeInfo(parentStack.element()) + " are overlaping: " + nodeInfo(previousValue) + " and " + nodeInfo(paramValue);
                                return false;
                            }
                            if (previousType != null && paramValue.startOffset <= previousType.endOffset) {
                                message = "Parameter default values and type in " + nodeInfo(parentStack.element()) + " are overlaping: " + nodeInfo(previousValue) + " and " + nodeInfo(paramValue);
                                return false;
                            }
                            if (!paramValue.accept(this)) {
                                return false;
                            }
                            previousValue = paramValue;
                        }
                    }

                }
            }

        }
        return true;
    }

    private static String nodeInfo(SSTNode node) {
        String result = node.getClass().getSimpleName() + "[" + node.startOffset + ", " + node.endOffset + "]";
        return result;
    }

    @Override
    public Boolean visit(AndSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArrayWithOverlap(node.values, "values")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(AnnAssignmentSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArrayWithOverlap(node.lhs, "left hand side items") || !node.rhs.accept(this) || (node.type != null && !node.type.accept(this))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(AssertSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.test.accept(this) || (node.message != null && !node.message.accept(this))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(AssignmentSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.rhs.accept(this) || !checkArrayWithOverlap(node.lhs, "left hand side")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(AugAssignmentSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.lhs.accept(this) || !node.rhs.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(BinaryArithmeticSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.right.accept(this) || !node.left.accept(this) || !checkArrayWithOverlap(new SSTNode[]{node.left, node.right}, "left and right side")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(BlockSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArrayWithOverlap(node.statements, "statements")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(BooleanLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(CallSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArgListBuilder(node.parameters) || !node.target.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ClassSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArgListBuilder(node.baseClasses) || !node.body.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(CollectionSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (node.values != null && !checkArrayWithOverlap(node.values, "items")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ComparisonSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.firstValue.accept(this) || !checkArrayWithOverlap(node.otherValues, "onther values")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(DecoratedSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArrayWithOverlap(node.decorators, "decorators") || !node.decorated.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(DecoratorSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArgListBuilder(node.arg)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(DelSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArrayWithOverlap(node.expressions, "expressions")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ExceptSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if ((node.test != null && !node.test.accept(this)) || !node.body.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ExpressionStatementSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.expression.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(FloatLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(ForComprehensionSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.target.accept(this) || !checkArrayWithOverlap(node.conditions, "conditions") || !checkArrayWithOverlap(node.variables, "variables") || !node.iterator.accept(this) ||
                            (node.name != null && !node.name.accept(this))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ForSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (node.targets != null && !checkArrayWithOverlap(node.targets, "targets")) {
                return false;
            }
            if (!node.iterator.accept(this) || !node.body.accept(this)) {
                return false;
            }
            if (node.elseStatement != null && !node.elseStatement.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(FunctionDefSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArgDefListBuider(node.argBuilder) || !node.body.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(GetAttributeSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.receiver.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(IfSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.test.accept(this) || !node.thenStatement.accept(this) || !checkArrayWithOverlap(new SSTNode[]{node.test, node.thenStatement}, "condition and then branch")) {
                return false;
            }
            if (node.elseStatement != null && (!node.elseStatement.accept(this) || !checkArrayWithOverlap(new SSTNode[]{node.thenStatement, node.elseStatement}, "then and else branch"))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ImportFromSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(ImportSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(LambdaSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArgDefListBuider(node.args) || !node.body.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(NotSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.value.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(NumberLiteralSSTNode.IntegerLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(NumberLiteralSSTNode.BigIntegerLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(OrSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!checkArrayWithOverlap(node.values, "values")) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(RaiseSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if ((node.value != null && !node.value.accept(this)) || (node.from != null && !node.from.accept(this))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(ReturnSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (node.value != null && !node.value.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(SimpleSSTNode node) {
        if (node.startOffset == -1 && node.startOffset == -1) {
            // non existing node
            return true;
        }
        return checkParent(node);
    }

    @Override
    public Boolean visit(SliceSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if ((node.start != null && !node.start.accept(this)) || (node.step != null && !node.step.accept(this)) || (node.stop != null && !node.stop.accept(this))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(StarSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.value.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(SubscriptSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.receiver.accept(this) || !node.subscript.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(TernaryIfSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.test.accept(this) || !node.thenStatement.accept(this) || !node.elseStatement.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(TrySSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.body.accept(this) || (node.elseStatement != null && !node.elseStatement.accept(this)) || (node.finallyStatement != null && !node.finallyStatement.accept(this)) ||
                            (node.exceptNodes != null && !checkArrayWithOverlap(node.exceptNodes, "exception nodes"))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(UnarySSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.value.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(VarLookupSSTNode node) {
        return checkParent(node);
    }

    @Override
    public Boolean visit(WhileSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (!node.test.accept(this) || !node.body.accept(this) || (node.elseStatement != null && !node.elseStatement.accept(this))) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(WithSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if ((node.target != null && !node.target.accept(this)) || (node.expression != null && !node.expression.accept(this)) || !node.body.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public Boolean visit(YieldExpressionSSTNode node) {
        if (checkParent(node)) {
            parentStack.push(node);
            if (node.value != null && !node.value.accept(this)) {
                return false;
            }
            parentStack.pop();
        } else {
            return false;
        }
        return true;
    }
}
