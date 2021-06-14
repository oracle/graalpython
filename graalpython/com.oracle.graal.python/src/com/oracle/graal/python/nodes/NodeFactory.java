/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNodeFactory;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.IsExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

/**
 * Helper methods for creation of nodes.
 */
public abstract class NodeFactory {
    private NodeFactory() {
    }

    @SuppressWarnings({"unchecked", "unused"})
    public static <T> T duplicate(Node orig, Class<T> clazz) {
        return (T) NodeUtil.cloneNode(orig);
    }

    public static ExpressionNode createInplaceOperation(String string, ExpressionNode left, ExpressionNode right) {
        switch (string) {
            case "+=":
                return InplaceArithmetic.IAdd.create(left, right);
            case "-=":
                return InplaceArithmetic.ISub.create(left, right);
            case "*=":
                return InplaceArithmetic.IMul.create(left, right);
            case "/=":
                return InplaceArithmetic.ITrueDiv.create(left, right);
            case "//=":
                return InplaceArithmetic.IFloorDiv.create(left, right);
            case "%=":
                return InplaceArithmetic.IMod.create(left, right);
            case "**=":
                return InplaceArithmetic.IPow.create(left, right);
            case "<<=":
                return InplaceArithmetic.ILShift.create(left, right);
            case ">>=":
                return InplaceArithmetic.IRShift.create(left, right);
            case "&=":
                return InplaceArithmetic.IAnd.create(left, right);
            case "|=":
                return InplaceArithmetic.IOr.create(left, right);
            case "^=":
                return InplaceArithmetic.IXor.create(left, right);
            case "@=":
                return InplaceArithmetic.IMatMul.create(left, right);
            default:
                throw new RuntimeException("unexpected operation: " + string);
        }
    }

    public static ExpressionNode createBinaryOperation(String string, ExpressionNode left, ExpressionNode right) {
        switch (string) {
            case "+":
                return BinaryArithmetic.Add.create(left, right);
            case "-":
                return BinaryArithmetic.Sub.create(left, right);
            case "*":
                return BinaryArithmetic.Mul.create(left, right);
            case "/":
                return BinaryArithmetic.TrueDiv.create(left, right);
            case "//":
                return BinaryArithmetic.FloorDiv.create(left, right);
            case "%":
                return BinaryArithmetic.Mod.create(left, right);
            case "**":
                return BinaryArithmetic.Pow.create(left, right);
            case "<<":
                return BinaryArithmetic.LShift.create(left, right);
            case ">>":
                return BinaryArithmetic.RShift.create(left, right);
            case "&":
                return BinaryArithmetic.And.create(left, right);
            case "|":
                return BinaryArithmetic.Or.create(left, right);
            case "^":
                return BinaryArithmetic.Xor.create(left, right);
            case "@":
                return BinaryArithmetic.MatMul.create(left, right);
            case "and":
                return new AndNode(left, right);
            case "or":
                return new OrNode(left, right);
            default:
                throw new RuntimeException("unexpected operation: " + string);
        }
    }

    public static ExpressionNode createComparisonOperation(String operator, ExpressionNode left, ExpressionNode right) {
        switch (operator) {
            case "<":
                return BinaryComparisonNodeFactory.LtNodeGen.create(left, right);
            case ">":
                return BinaryComparisonNodeFactory.GtNodeGen.create(left, right);
            case "==":
                return BinaryComparisonNodeFactory.EqNodeGen.create(left, right);
            case ">=":
                return BinaryComparisonNodeFactory.GeNodeGen.create(left, right);
            case "<=":
                return BinaryComparisonNodeFactory.LeNodeGen.create(left, right);
            case "<>":
            case "!=":
                return BinaryComparisonNodeFactory.NeNodeGen.create(left, right);
            case "in":
                return ContainsNode.create(left, right);
            case "notin":
                return CoerceToBooleanNode.createIfFalseNode(ContainsNode.create(left, right));
            case "is":
                return IsExpressionNode.create(left, right);
            case "isnot":
                return CoerceToBooleanNode.createIfFalseNode(IsExpressionNode.create(left, right));
            default:
                throw new RuntimeException("unexpected operation: " + operator);
        }
    }

    public static CoerceToBooleanNode toBooleanCastNode(PNode node) {
        if (node instanceof CoerceToBooleanNode) {
            return (CoerceToBooleanNode) node;
        } else {
            return CoerceToBooleanNode.createIfTrueNode((ExpressionNode) node);
        }
    }
}
