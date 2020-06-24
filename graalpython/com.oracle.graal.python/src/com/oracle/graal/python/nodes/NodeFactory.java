/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.cell.ReadLocalCellNode;
import com.oracle.graal.python.nodes.cell.WriteLocalCellNode;
import com.oracle.graal.python.nodes.classes.ReadClassAttributeNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.BreakNode;
import com.oracle.graal.python.nodes.control.BreakTargetNode;
import com.oracle.graal.python.nodes.control.ContinueNode;
import com.oracle.graal.python.nodes.control.ContinueTargetNode;
import com.oracle.graal.python.nodes.control.ElseNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.control.ReturnNode;
import com.oracle.graal.python.nodes.control.WhileNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.IsExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.expression.TernaryIfNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.DeleteNameNode;
import com.oracle.graal.python.nodes.frame.DestructuringAssignmentNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.DictConcatNodeFactory;
import com.oracle.graal.python.nodes.generator.YieldFromNode;
import com.oracle.graal.python.nodes.generator.YieldNode;
import com.oracle.graal.python.nodes.literal.BooleanLiteralNode;
import com.oracle.graal.python.nodes.literal.BuiltinsLiteralNode;
import com.oracle.graal.python.nodes.literal.BytesLiteralNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DictLiteralNodeFactory;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.FormatStringLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.KeywordLiteralNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.SetLiteralNodeFactory;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssertNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.ImportFromNode;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.nodes.statement.ImportStarNode;
import com.oracle.graal.python.nodes.statement.PrintExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.statement.WithNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
import com.oracle.graal.python.parser.GeneratorInfo;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

public class NodeFactory {

    private final PythonLanguage language;

    private NodeFactory(PythonLanguage language) {
        this.language = language;
    }

    public static NodeFactory create(PythonLanguage language) {
        return new NodeFactory(language);
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <T> T duplicate(Node orig, Class<T> clazz) {
        return (T) NodeUtil.cloneNode(orig);
    }

    public ModuleRootNode createModuleRoot(String name, String doc, ExpressionNode file, FrameDescriptor fd, boolean hasAnnotations) {
        return new ModuleRootNode(language, name, doc, file, fd, null, hasAnnotations);
    }

    public FunctionRootNode createFunctionRoot(SourceSection sourceSection, String functionName, boolean isGenerator, FrameDescriptor frameDescriptor, ExpressionNode body,
                    ExecutionCellSlots cellSlots, Signature signature) {
        return new FunctionRootNode(language, sourceSection, functionName, isGenerator, false, frameDescriptor, body, cellSlots, signature);
    }

    public ClassBodyRootNode createClassBodyRoot(SourceSection sourceSection, String functionName, FrameDescriptor frameDescriptor, ExpressionNode body, ExecutionCellSlots cellSlots) {
        return new ClassBodyRootNode(language, sourceSection, functionName, frameDescriptor, body, cellSlots);
    }

    public StatementNode createBlock(List<StatementNode> statements) {
        StatementNode[] array = statements.toArray(new StatementNode[statements.size()]);
        return createBlock(array);
    }

    public StatementNode createBlock(StatementNode... statements) {
        return BlockNode.create(statements);
    }

    public ImportNode createImport(String importee) {
        return new ImportNode(importee);
    }

    public StatementNode createImportFrom(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        return ImportFromNode.create(importee, fromlist, readNodes, level);
    }

    public StatementNode createImportStar(String fromModuleName, int level) {
        return new ImportStarNode(fromModuleName, level);
    }

    public LoopNode createWhile(CoerceToBooleanNode condition, StatementNode body) {
        return new WhileNode(condition, body);
    }

    public StatementNode createIf(CoerceToBooleanNode condition, StatementNode thenPart, StatementNode elsePart) {
        return new IfNode(condition, thenPart, elsePart);
    }

    public ExpressionNode createTernaryIf(CoerceToBooleanNode condition, ExpressionNode thenPart, ExpressionNode elsePart) {
        return new TernaryIfNode(condition, thenPart, elsePart);
    }

    public GetIteratorExpressionNode createGetIterator(ExpressionNode collection) {
        return GetIteratorExpressionNode.create(collection);
    }

    public StatementNode createElse(StatementNode forNode, StatementNode orelse) {
        return new ElseNode(forNode, orelse);
    }

    public StatementNode createReturn() {
        return new ReturnNode();
    }

    public StatementNode createFrameReturn(StatementNode value) {
        return new ReturnNode.FrameReturnNode(value);
    }

    public StatementNode createBreak() {
        return new BreakNode();
    }

    public StatementNode createContinue() {
        return new ContinueNode();
    }

    public StatementNode createContinueTarget(StatementNode child) {
        return new ContinueTargetNode(child);
    }

    public StatementNode createBreakTarget(StatementNode forNode) {
        return new BreakTargetNode(forNode, null);
    }

    public StatementNode createBreakTarget(StatementNode forNode, StatementNode orelse) {
        return new BreakTargetNode(forNode, orelse);
    }

    public YieldNode createYield(ExpressionNode right, GeneratorInfo.Mutable generatorInfo) {
        return new YieldNode(right, generatorInfo);
    }

    public YieldFromNode createYieldFrom(ExpressionNode right, GeneratorInfo.Mutable generatorInfo) {
        return new YieldFromNode(right, generatorInfo);
    }

    public ExpressionNode createIntegerLiteral(int value) {
        return new IntegerLiteralNode(value);
    }

    public ExpressionNode createLongLiteral(long value) {
        return new LongLiteralNode(value);
    }

    public ExpressionNode createPIntLiteral(BigInteger value) {
        return new PIntLiteralNode(value);
    }

    public ExpressionNode createDoubleLiteral(double value) {
        return new DoubleLiteralNode(value);
    }

    public ExpressionNode createComplexLiteral(PComplex value) {
        return new ComplexLiteralNode(value);
    }

    public ExpressionNode createStringLiteral(String value) {
        return new StringLiteralNode(value);
    }

    public ExpressionNode createFormatStringLiteral(FormatStringLiteralNode.StringPart[] values) {
        return new FormatStringLiteralNode(values);
    }

    public ExpressionNode createBytesLiteral(byte[] value) {
        return new BytesLiteralNode(value);
    }

    public ExpressionNode createDictLiteral() {
        return DictLiteralNodeFactory.create(new ExpressionNode[0], new ExpressionNode[0]);
    }

    public ExpressionNode createDictLiteral(List<ExpressionNode> keys, List<ExpressionNode> values) {
        ExpressionNode[] convertedKeys = keys.toArray(new ExpressionNode[keys.size()]);
        ExpressionNode[] convertedValues = values.toArray(new ExpressionNode[values.size()]);
        return DictLiteralNodeFactory.create(convertedKeys, convertedValues);
    }

    public TupleLiteralNode createTupleLiteral(ExpressionNode... values) {
        return new TupleLiteralNode(values);
    }

    public TupleLiteralNode createTupleLiteral(List<ExpressionNode> values) {
        ExpressionNode[] convertedValues = values.toArray(new ExpressionNode[values.size()]);
        return new TupleLiteralNode(convertedValues);
    }

    public ExpressionNode createListLiteral(String[] values) {
        ExpressionNode[] stringNodes = new ExpressionNode[values.length];
        for (int i = 0; i < stringNodes.length; i++) {
            stringNodes[i] = this.createStringLiteral(values[i]);
        }
        return createListLiteral(stringNodes);
    }

    public ExpressionNode createListLiteral(List<ExpressionNode> values) {
        return createListLiteral(values.toArray(new ExpressionNode[values.size()]));
    }

    public ExpressionNode createListLiteral(ExpressionNode[] values) {
        return ListLiteralNode.create(values);
    }

    public ExpressionNode createSetLiteral(List<ExpressionNode> values) {
        ExpressionNode[] convertedValues = values.toArray(new ExpressionNode[values.size()]);
        return SetLiteralNodeFactory.create(convertedValues);
    }

    public ExpressionNode createSetLiteral(ExpressionNode[] values) {
        return SetLiteralNodeFactory.create(values);
    }

    public ExpressionNode createUnaryOperation(String string, ExpressionNode operand) {
        switch (string) {
            case "+":
                return UnaryArithmetic.Pos.create(operand);
            case "-":
                return UnaryArithmetic.Neg.create(operand);
            case "~":
                return UnaryArithmetic.Invert.create(operand);
            case "not":
                return CoerceToBooleanNode.createIfFalseNode(operand);
            default:
                throw new RuntimeException("unexpected operation: " + string);
        }
    }

    public ExpressionNode createInplaceOperation(String string, ExpressionNode left, ExpressionNode right) {
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

    public ExpressionNode createBinaryOperation(String string, ExpressionNode left, ExpressionNode right) {
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

    public ExpressionNode createComparisonOperation(String operator, ExpressionNode left, ExpressionNode right) {
        switch (operator) {
            case "<":
                return BinaryComparisonNode.create(SpecialMethodNames.__LT__, SpecialMethodNames.__GT__, operator, left, right);
            case ">":
                return BinaryComparisonNode.create(SpecialMethodNames.__GT__, SpecialMethodNames.__LT__, operator, left, right);
            case "==":
                return BinaryComparisonNode.create(SpecialMethodNames.__EQ__, SpecialMethodNames.__EQ__, operator, left, right);
            case ">=":
                return BinaryComparisonNode.create(SpecialMethodNames.__GE__, SpecialMethodNames.__LE__, operator, left, right);
            case "<=":
                return BinaryComparisonNode.create(SpecialMethodNames.__LE__, SpecialMethodNames.__GE__, operator, left, right);
            case "<>":
            case "!=":
                return BinaryComparisonNode.create(SpecialMethodNames.__NE__, SpecialMethodNames.__NE__, operator, left, right);
            case "in":
                return ContainsNode.create(right, left);
            case "notin":
                return CoerceToBooleanNode.createIfFalseNode(ContainsNode.create(right, left));
            case "is":
                return IsExpressionNode.create(left, right);
            case "isnot":
                return CoerceToBooleanNode.createIfFalseNode(IsExpressionNode.create(left, right));
            default:
                throw new RuntimeException("unexpected operation: " + operator);
        }
    }

    public ExpressionNode createGetAttribute(ExpressionNode primary, String name) {
        return GetAttributeNode.create(name, primary);
    }

    public ExpressionNode createGetItem(ExpressionNode primary, String name) {
        return GetItemNode.create(primary, createStringLiteral(name));
    }

    public StatementNode createDeleteItem(ExpressionNode primary, ExpressionNode slice) {
        return DeleteItemNode.create(primary, slice);
    }

    public StatementNode createDeleteItem(ExpressionNode primary, String name) {
        return createDeleteItem(primary, createStringLiteral(name));
    }

    public StatementNode createDeleteAttribute(ExpressionNode object, String attributeId) {
        return createDeleteAttribute(object, createStringLiteral(attributeId));
    }

    public StatementNode createDeleteAttribute(ExpressionNode object, ExpressionNode attributeId) {
        return DeleteAttributeNode.create(object, attributeId);
    }

    public StatementNode createDeleteGlobal(String attributeId) {
        return DeleteGlobalNode.create(attributeId);
    }

    public StatementNode createDeleteName(String attributeId) {
        return DeleteNameNode.create(attributeId);
    }

    public ExpressionNode createSlice(ExpressionNode lower, ExpressionNode upper, ExpressionNode step) {
        return SliceLiteralNode.create(lower, upper, step);
    }

    public ExpressionNode createSubscriptLoad(ExpressionNode primary, ExpressionNode slice) {
        return GetItemNode.create(primary, slice);
    }

    public ExpressionNode createReadClassAttributeNode(String identifier, FrameSlot cellSlot, boolean isFreeVar) {
        return ReadClassAttributeNode.create(identifier, cellSlot, isFreeVar);
    }

    public ExpressionNode createReadLocalCell(FrameSlot slot, boolean isFreeVar) {
        assert slot != null;
        return ReadLocalCellNode.create(slot, isFreeVar);
    }

    public ExpressionNode createReadLocal(FrameSlot slot) {
        assert slot != null;
        return ReadLocalVariableNode.create(slot);
    }

    public StatementNode createWriteLocalCell(ExpressionNode right, FrameSlot slot) {
        assert slot != null;
        return WriteLocalCellNode.create(slot, ReadLocalVariableNode.create(slot), right);
    }

    public StatementNode createWriteLocal(ExpressionNode right, FrameSlot slot) {
        assert slot != null;
        return WriteLocalVariableNode.create(slot, right);
    }

    public ExpressionNode createReadGlobalOrBuiltinScope(String attributeId) {
        return ReadGlobalOrBuiltinNode.create(attributeId);
    }

    public ReadNode createLoadName(String name) {
        return ReadNameNode.create(name);
    }

    public ExpressionNode createBooleanLiteral(boolean value) {
        return new BooleanLiteralNode(value);
    }

    public ExpressionNode createObjectLiteral(Object obj) {
        return new ObjectLiteralNode(obj);
    }

    public ExpressionNode createNullLiteral() {
        return createObjectLiteral(null);
    }

    public ExpressionNode createBuiltinsLiteral() {
        return new BuiltinsLiteralNode();
    }

    public ExpressionNode createKeywordLiteral(ExpressionNode value, String name) {
        return new KeywordLiteralNode(value, name);
    }

    public ExpressionNode getBuiltin(String id) {
        return createReadGlobalOrBuiltinScope(id);
    }

    public CoerceToBooleanNode toBooleanCastNode(PNode node) {
        if (node instanceof CoerceToBooleanNode) {
            return (CoerceToBooleanNode) node;
        } else {
            return createYesNode((ExpressionNode) node);
        }
    }

    public CoerceToBooleanNode createYesNode(ExpressionNode operand) {
        return CoerceToBooleanNode.createIfTrueNode(operand);
    }

    public StatementNode createTryExceptElseFinallyNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode elseNode, StatementNode finalbody) {
        return new TryFinallyNode(new TryExceptNode(body, exceptNodes, elseNode), finalbody);
    }

    public StatementNode createAssert(CoerceToBooleanNode condition, ExpressionNode message) {
        return new AssertNode(condition, message);
    }

    public StatementNode createWithNode(ExpressionNode withContext, WriteNode targetNode, StatementNode body) {
        return WithNode.create(withContext, targetNode, body);
    }

    public ExpressionNode createDictionaryConcat(ExpressionNode... dictNodes) {
        return DictConcatNodeFactory.create(dictNodes);
    }

    public ExpressionNode callBuiltin(String string, ExpressionNode argument) {
        return PythonCallNode.create(getBuiltin(string), new ExpressionNode[]{argument}, new ExpressionNode[0], null, null);
    }

    public StatementNode createSetAttribute(ExpressionNode object, String key, ExpressionNode rhs) {
        return SetAttributeNode.create(key, object, rhs);
    }

    public StatementNode createDestructuringAssignment(ExpressionNode rhs, ReadNode[] slots, int starredIndex, StatementNode[] assignments) {
        return DestructuringAssignmentNode.create(rhs, slots, starredIndex, assignments);
    }

    public PrintExpressionNode createPrintExpression(ExpressionNode body) {
        return PrintExpressionNode.create(body);
    }
}
