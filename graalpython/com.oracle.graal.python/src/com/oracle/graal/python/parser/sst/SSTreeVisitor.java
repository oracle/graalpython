/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

public interface SSTreeVisitor<T> {
    T visit(AndSSTNode node);

    T visit(AnnAssignmentSSTNode node);

    T visit(AssertSSTNode node);

    T visit(AssignmentSSTNode node);

    T visit(AugAssignmentSSTNode node);

    T visit(BinaryArithmeticSSTNode node);

    T visit(BlockSSTNode node);

    T visit(BooleanLiteralSSTNode node);

    T visit(CallSSTNode node);

    T visit(ClassSSTNode node);

    T visit(CollectionSSTNode node);

    T visit(ComparisonSSTNode node);

    T visit(DecoratedSSTNode node);

    T visit(DecoratorSSTNode node);

    T visit(DelSSTNode node);

    T visit(ExceptSSTNode node);

    T visit(ExpressionStatementSSTNode node);

    T visit(FloatLiteralSSTNode node);

    T visit(ForComprehensionSSTNode node);

    T visit(ForSSTNode node);

    T visit(FunctionDefSSTNode node);

    T visit(GetAttributeSSTNode node);

    T visit(IfSSTNode node);

    T visit(ImportFromSSTNode node);

    T visit(ImportSSTNode node);

    T visit(LambdaSSTNode node);

    T visit(NotSSTNode node);

    T visit(NumberLiteralSSTNode.IntegerLiteralSSTNode node);

    T visit(NumberLiteralSSTNode.BigIntegerLiteralSSTNode node);

    T visit(OrSSTNode node);

    T visit(RaiseSSTNode node);

    T visit(ReturnSSTNode node);

    T visit(SimpleSSTNode node);

    T visit(SliceSSTNode node);

    T visit(StarSSTNode node);

    T visit(StringLiteralSSTNode.RawStringLiteralSSTNode node);

    T visit(StringLiteralSSTNode.BytesLiteralSSTNode node);

    T visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node);

    T visit(SubscriptSSTNode node);

    T visit(TernaryIfSSTNode node);

    T visit(TrySSTNode node);

    T visit(UnarySSTNode node);

    T visit(VarLookupSSTNode node);

    T visit(WhileSSTNode node);

    T visit(WithSSTNode node);

    T visit(YieldExpressionSSTNode node);
}
