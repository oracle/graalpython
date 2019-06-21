/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser.sst;


public interface SSTreeVisitor<T> {
    T visit(AndSSTNode node);
    T visit(AnnAssignmentSSTNode node);
    T visit(AssertSSTNode node);
    T visit(AssignmentNode node);
    T visit(AugAssignmentSSTNode node);
    T visit(BinaryArithmeticSSTNode node);
    T visit(BlockSSTNode node);
    T visit(BooleanLiteralNode node);
    T visit(CallSSTNode node);
    T visit(ClassSSTNode node);
    T visit(CollectionSSTNode node);
    T visit(ComparisonNode node);
    T visit(DecoratedSSTNode node);
    T visit(DecoratorSSTNode node);
    T visit(DelSSTNode node);
    T visit(ExceptSSTNode node);
    T visit(ExpressionStatementNode node);
    T visit(FloatLiteralNode node);
    T visit(ForComprehensionSSTNode node);
    T visit(ForSSTNode node);
    T visit(FunctionDefSSTNode node);
    T visit(GetAttributeSSTNode node);
    T visit(IfSSTNode node);
    T visit(ImportFromSSTNode node);
    T visit(ImportSSTNode node);
    T visit(LambdaSSTNode node);
    T visit(NotSSTNode node);
    T visit(NumberLiteralNode node);
    T visit(OrSSTNode node);
    T visit(RaiseSSTNode node);
    T visit(ReturnSSTNode node);
    T visit(SimpleSSTNode node);
    T visit(SliceSSTNode node);
    T visit(StarSSTNode node);
    T visit(StringLiteralNode node);
    T visit(SubscriptSSTNode node);
    T visit(TernaryArithmeticSSTNode node);
    T visit(TrySSTNode node);
    T visit(UnarySSTNode node);
    T visit(VarLookupNode node);
    T visit(WhileSSTNode node);
    T visit(WithSSTNode node);
    T visit(YieldExpressionSSTNode node);
}
