/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.cell.ReadLocalCellNode;
import com.oracle.graal.python.nodes.cell.WriteCellVarNode;
import com.oracle.graal.python.nodes.cell.WriteLocalCellNode;
import com.oracle.graal.python.nodes.classes.ReadClassAttributeNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.BreakNode;
import com.oracle.graal.python.nodes.control.BreakTargetNode;
import com.oracle.graal.python.nodes.control.ContinueNode;
import com.oracle.graal.python.nodes.control.ContinueTargetNode;
import com.oracle.graal.python.nodes.control.ElseNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.control.ReturnNode;
import com.oracle.graal.python.nodes.control.WhileNode;
import com.oracle.graal.python.nodes.datamodel.IsCallableNode;
import com.oracle.graal.python.nodes.datamodel.IsContextManagerNode;
import com.oracle.graal.python.nodes.datamodel.IsIterableNode;
import com.oracle.graal.python.nodes.datamodel.IsMappingNode;
import com.oracle.graal.python.nodes.datamodel.IsSequenceNode;
import com.oracle.graal.python.nodes.datamodel.PDataModelEmulationNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.IsNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.frame.DeleteGlobalNode;
import com.oracle.graal.python.nodes.frame.DestructuringAssignmentNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.generator.DictConcatNode;
import com.oracle.graal.python.nodes.generator.YieldNode;
import com.oracle.graal.python.nodes.generator.YieldResumeNode;
import com.oracle.graal.python.nodes.literal.BooleanLiteralNode;
import com.oracle.graal.python.nodes.literal.BuiltinsLiteralNode;
import com.oracle.graal.python.nodes.literal.BytesLiteralNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DictLiteralNode;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.KeywordLiteralNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.SetLiteralNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.AssertNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.ImportFromNode;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.nodes.statement.ImportStarNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.statement.TryExceptNode;
import com.oracle.graal.python.nodes.statement.TryFinallyNode;
import com.oracle.graal.python.nodes.statement.WithNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.parser.ExecutionCellSlots;
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

    public ModuleRootNode createModuleRoot(String name, PNode body, FrameDescriptor fd) {
        return createModuleRoot(name, body, fd, null);
    }

    public ModuleRootNode createModuleRoot(String name, PNode body, FrameDescriptor fd, FrameSlot[] freeVarSlots) {
        body.markAsRoot();
        return new ModuleRootNode(language, name, body, fd, freeVarSlots);
    }

    public FunctionRootNode createFunctionRoot(SourceSection sourceSection, String functionName, boolean isGenerator, FrameDescriptor frameDescriptor, PNode body, ExecutionCellSlots cellSlots) {
        body.markAsRoot();
        return new FunctionRootNode(language, sourceSection, functionName, isGenerator, frameDescriptor, body, cellSlots);
    }

    public ClassBodyRootNode createClassBodyRoot(SourceSection sourceSection, String functionName, FrameDescriptor frameDescriptor, PNode body, ExecutionCellSlots cellSlots) {
        body.markAsRoot();
        return new ClassBodyRootNode(language, sourceSection, functionName, frameDescriptor, body, cellSlots);
    }

    public PNode createBlock(List<PNode> statements) {
        if (statements.size() == 1) {
            return statements.get(0);
        } else {
            PNode[] array = statements.toArray(new PNode[statements.size()]);
            return createBlock(array);
        }
    }

    public PNode createBlock(PNode... statements) {
        return BlockNode.create(statements);
    }

    public PNode createImport(String importee) {
        return new ImportNode(importee);
    }

    public PNode createImportFrom(String importee, String[] fromlist, WriteNode[] readNodes, int level) {
        return ImportFromNode.create(importee, fromlist, readNodes, level);
    }

    public PNode createImportStar(String fromModuleName, int level) {
        return new ImportStarNode(fromModuleName, level);
    }

    public LoopNode createWhile(CastToBooleanNode condition, PNode body) {
        return new WhileNode(condition, body);
    }

    public StatementNode createIf(CastToBooleanNode condition, PNode thenPart, PNode elsePart) {
        return IfNode.create(condition, thenPart, elsePart);
    }

    public GetIteratorNode createGetIterator(PNode collection) {
        return GetIteratorNode.create(collection);
    }

    public StatementNode createElse(PNode forNode, PNode orelse) {
        return new ElseNode(forNode, orelse);
    }

    public StatementNode createReturn() {
        return new ReturnNode();
    }

    public StatementNode createFrameReturn(PNode value) {
        return new ReturnNode.FrameReturnNode(value);
    }

    public StatementNode createBreak() {
        return new BreakNode();
    }

    public StatementNode createContinue() {
        return new ContinueNode();
    }

    public StatementNode createContinueTarget(PNode child) {
        return new ContinueTargetNode(child);
    }

    public StatementNode createBreakTarget(PNode forNode) {
        return new BreakTargetNode(forNode);
    }

    public PNode createYield(PNode right, FrameSlot returnSlot) {
        return createBlock(new YieldNode(createWriteLocal(right, returnSlot)), new YieldResumeNode());
    }

    public PNode createIntegerLiteral(int value) {
        return new IntegerLiteralNode(value);
    }

    public PNode createLongLiteral(long value) {
        return new LongLiteralNode(value);
    }

    public PNode createPIntLiteral(BigInteger value) {
        return new PIntLiteralNode(value);
    }

    public PNode createDoubleLiteral(double value) {
        return new DoubleLiteralNode(value);
    }

    public PNode createComplexLiteral(PComplex value) {
        return new ComplexLiteralNode(value);
    }

    public PNode createStringLiteral(String value) {
        return new StringLiteralNode(value);
    }

    public PNode createBytesLiteral(byte[] value) {
        return new BytesLiteralNode(value);
    }

    public PNode createDictLiteral() {
        return DictLiteralNode.create(new PNode[0], new PNode[0]);
    }

    public PNode createDictLiteral(List<PNode> keys, List<PNode> values) {
        PNode[] convertedKeys = keys.toArray(new PNode[keys.size()]);
        PNode[] convertedValues = values.toArray(new PNode[values.size()]);
        return DictLiteralNode.create(convertedKeys, convertedValues);
    }

    public TupleLiteralNode createTupleLiteral(List<PNode> values) {
        PNode[] convertedValues = values.toArray(new PNode[values.size()]);
        return new TupleLiteralNode(convertedValues);
    }

    public PNode createListLiteral(String[] values) {
        PNode[] stringNodes = new PNode[values.length];
        for (int i = 0; i < stringNodes.length; i++) {
            stringNodes[i] = this.createStringLiteral(values[i]);
        }
        return createListLiteral(stringNodes);
    }

    public PNode createListLiteral(List<PNode> values) {
        return createListLiteral(values.toArray(new PNode[values.size()]));
    }

    public PNode createListLiteral(PNode[] values) {
        return ListLiteralNode.create(values);
    }

    public PNode createSetLiteral(Set<PNode> values) {
        PNode[] convertedValues = values.toArray(new PNode[values.size()]);
        return new SetLiteralNode(convertedValues);
    }

    public PNode createUnaryOperation(String string, PNode operand) {
        switch (string) {
            case "+":
                return UnaryArithmetic.Pos.create(operand);
            case "-":
                return UnaryArithmetic.Neg.create(operand);
            case "~":
                return UnaryArithmetic.Invert.create(operand);
            case "not":
                return CastToBooleanNode.createIfFalseNode(operand);
            default:
                throw new RuntimeException("unexpected operation: " + string);
        }
    }

    public PNode createInplaceOperation(String string, PNode left, PNode right) {
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

    public PNode createBinaryOperation(String string, PNode left, PNode right) {
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
                return TernaryArithmetic.Pow.create(left, right);
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

    public PNode createComparisonOperation(String operator, PNode left, PNode right) {
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
                return BinaryComparisonNode.create(SpecialMethodNames.__CONTAINS__, null, operator, right, left);
            case "notin":
                return CastToBooleanNode.createIfFalseNode(BinaryComparisonNode.create(SpecialMethodNames.__CONTAINS__, null, operator, right, left));
            case "is":
                return IsNode.create(left, right);
            case "isnot":
                return CastToBooleanNode.createIfFalseNode(IsNode.create(left, right));
            default:
                throw new RuntimeException("unexpected operation: " + operator);
        }
    }

    public PNode createGetAttribute(PNode primary, String name) {
        return GetAttributeNode.create(name, primary);
    }

    public PNode createGetItem(PNode primary, String name) {
        return GetItemNode.create(primary, createStringLiteral(name));
    }

    public PNode createDeleteItem(PNode primary, PNode slice) {
        return DeleteItemNode.create(primary, slice);
    }

    public PNode createDeleteItem(PNode primary, String name) {
        return createDeleteItem(primary, createStringLiteral(name));
    }

    public PNode createDeleteAttribute(PNode object, String attributeId) {
        return createDeleteAttribute(object, createStringLiteral(attributeId));
    }

    public PNode createDeleteAttribute(PNode object, PNode attributeId) {
        return DeleteAttributeNode.create(object, attributeId);
    }

    public PNode createDeleteGlobal(String attributeId) {
        return DeleteGlobalNode.create(attributeId);
    }

    public PNode createSlice(PNode lower, PNode upper, PNode step) {
        return SliceLiteralNode.create(lower, upper, step);
    }

    public PNode createSubscriptLoad(PNode primary, PNode slice) {
        return GetItemNode.create(primary, slice);
    }

    public PNode createReadClassAttributeNode(String identifier, FrameSlot cellSlot, boolean isFreeVar) {
        return ReadClassAttributeNode.create(identifier, cellSlot, isFreeVar);
    }

    public PNode createWriteCellVar(PNode readNode, FunctionRootNode funcRootNode, String identifier) {
        return WriteCellVarNode.create(readNode, funcRootNode, identifier);
    }

    public PNode createReadLocalCell(FrameSlot slot, boolean isFreeVar) {
        assert slot != null;
        return ReadLocalCellNode.create(slot, isFreeVar);
    }

    public PNode createReadLocal(FrameSlot slot) {
        assert slot != null;
        return ReadLocalVariableNode.create(slot);
    }

    public PNode createWriteLocalCell(PNode right, FrameSlot slot) {
        assert slot != null;
        return WriteLocalCellNode.create(slot, right);
    }

    public PNode createWriteLocal(PNode right, FrameSlot slot) {
        assert slot != null;
        return WriteLocalVariableNode.create(slot, right);
    }

    public PNode createReadGlobalOrBuiltinScope(String attributeId) {
        return ReadGlobalOrBuiltinNode.create(attributeId);
    }

    public PNode createBooleanLiteral(boolean value) {
        return new BooleanLiteralNode(value);
    }

    public PNode createObjectLiteral(Object obj) {
        return new ObjectLiteralNode(obj);
    }

    public PNode createNullLiteral() {
        return createObjectLiteral(null);
    }

    public PNode createBuiltinsLiteral() {
        return new BuiltinsLiteralNode();
    }

    public PNode createKeywordLiteral(PNode value, String name) {
        return new KeywordLiteralNode(value, name);
    }

    public PNode getBuiltin(String id) {
        return createReadGlobalOrBuiltinScope(id);
    }

    public CastToBooleanNode toBooleanCastNode(PNode node) {
        if (node instanceof CastToBooleanNode) {
            return (CastToBooleanNode) node;
        } else {
            return createYesNode(node);
        }
    }

    public CastToBooleanNode createYesNode(PNode operand) {
        return CastToBooleanNode.createIfTrueNode(operand);
    }

    public StatementNode createTryFinallyNode(PNode body, PNode finalbody) {
        return new TryFinallyNode(body, finalbody);
    }

    public StatementNode createTryExceptElseFinallyNode(PNode body, ExceptNode[] exceptNodes, PNode elseNode, PNode finalbody) {
        return new TryFinallyNode(new TryExceptNode(body, exceptNodes, elseNode), finalbody);
    }

    public StatementNode createAssert(CastToBooleanNode condition, PNode message) {
        return new AssertNode(condition, message);
    }

    public StatementNode createWithNode(PNode withContext, PNode targetNode, PNode body) {
        return WithNode.create(withContext, targetNode, body);
    }

    public PNode createDictionaryConcat(PNode... dictNodes) {
        return DictConcatNode.create(dictNodes);
    }

    public PNode callBuiltin(String string, PNode argument) {
        return PythonCallNode.create(getBuiltin(string), new PNode[]{argument}, new PNode[0], null, null);
    }

    public PNode createSetAttribute(PNode object, String key, PNode rhs) {
        return SetAttributeNode.create(key, object, rhs);
    }

    public PNode createDestructuringAssignment(PNode rhs, List<ReadNode> slots, int starredIndex, PNode[] assignments) {
        return DestructuringAssignmentNode.create(rhs, slots, starredIndex, assignments);
    }

    public PDataModelEmulationNode createIsMapping() {
        return IsMappingNode.create();
    }

    public PDataModelEmulationNode createIsSequence() {
        return IsSequenceNode.create();
    }

    public PDataModelEmulationNode createIsContextManager() {
        return IsContextManagerNode.create();
    }

    public PDataModelEmulationNode createIsCallable() {
        return IsCallableNode.create();
    }

    public PDataModelEmulationNode createIsIterable() {
        return IsIterableNode.create();
    }
}
