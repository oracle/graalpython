/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.grammar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.attributes.DeleteAttributeNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode.NotNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.frame.DeleteNameNode;
import com.oracle.graal.python.nodes.frame.DestructuringAssignmentNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.WriteLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.function.InnerRootNode;
import com.oracle.graal.python.nodes.generator.DictConcatNode;
import com.oracle.graal.python.nodes.literal.BooleanLiteralNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DictLiteralNode;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.ListLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.SetLiteralNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.ImportNode;
import com.oracle.graal.python.nodes.statement.ImportStarNode;
import com.oracle.graal.python.nodes.subscript.DeleteItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.SetItemNode;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import org.junit.Assume;

import org.junit.Test;

public class TestParserTranslator {
    PythonContext context;

    public TestParserTranslator() {
        PythonTests.enterContext();
        context = PythonLanguage.getContext();
    }

    private static class JUnitRootNode extends PRootNode {
        private final ConditionProfile profile = ConditionProfile.createCountingProfile();
        @Child private ExpressionNode body;
        @Child private CalleeContext calleeContext = CalleeContext.create();

        public JUnitRootNode(PythonLanguage language, ExpressionNode body) {
            super(language);
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, profile);
            try {
                return body.execute(frame);
            } finally {
                calleeContext.exit(frame, this);
            }
        }

        @Override
        public Signature getSignature() {
            return Signature.EMPTY;
        }

        @Override
        public boolean isPythonInternal() {
            return false;
        }

    }

    private Object runInRoot(ExpressionNode expr) {
        JUnitRootNode jUnitRootNode = new JUnitRootNode(context.getLanguage(), expr);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(jUnitRootNode);
        Object[] arguments = PArguments.create();
        PFrame.Reference frameInfo = IndirectCalleeContext.enter(context, arguments, callTarget);
        try {
            return callTarget.call(arguments);
        } finally {
            IndirectCalleeContext.exit(context, frameInfo);
        }
    }

    RootNode parse(String src) {
        Source source = Source.newBuilder(PythonLanguage.ID, src, "foo").build();
        return (RootNode) context.getCore().getParser().parse(ParserMode.File, context.getCore(), source, null);
    }

    String parseToString(String src) {
        RootNode result = parse(src);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NodeUtil.printCompactTree(out, result);
        return out.toString();
    }

    <T> T getChild(Node result, int num, Class<? extends T> klass) {
        int i = 0;
        for (Node n : result.getChildren()) {
            if (++i <= num) {
                continue;
            }
            n = unpackModuleBodyWrappers(n);
            assertTrue("Expected an instance of " + klass + ", got " + n.getClass(), klass.isInstance(n));
            return klass.cast(n);
        }
        assertFalse("Expected an instance of " + klass + ", got null", true);
        return null;
    }

    private Node unpackModuleBodyWrappers(Node n) {
        Node actual = n;
        if (n instanceof ExpressionNode.ExpressionStatementNode) {
            actual = n.getChildren().iterator().next();
        } else if (n instanceof ExpressionNode.ExpressionWithSideEffects || n instanceof ExpressionNode.ExpressionWithSideEffect) {
            actual = n.getChildren().iterator().next();
        } else if (n instanceof WriteLocalVariableNode) {
            if (((WriteLocalVariableNode) n).getIdentifier().equals(FrameSlotIDs.RETURN_SLOT_ID)) {
                actual = ((WriteLocalVariableNode) n).getRhs();
            }
        } else if (n instanceof InnerRootNode) {
            actual = n.getChildren().iterator().next();
        }
        if (actual == n) {
            return n;
        } else {
            return unpackModuleBodyWrappers(actual);
        }
    }

    <T> T getFirstChild(Node result, Class<? extends T> klass) {
        return getChild(result, 0, klass);
    }

    <T> T parseAs(String src, Class<? extends T> klass) {
        return getFirstChild(parse(src), klass);
    }

    Object literalAs(String src, Class<? extends PNode> klass) {
        return runInRoot((ExpressionNode) parseAs(src, klass));
    }

    <T> T literalAs(String src, Class<? extends PNode> klass, Class<? extends T> rklass) {
        Object literal = literalAs(src, klass);
        assertTrue("Expected an instance of " + rklass + ", got " + literal.getClass(), rklass.isInstance(literal));
        return rklass.cast(literal);
    }

    <T> T assertInstanceOf(PNode value, Class<? extends T> klass) {
        assertTrue("Expected an instance of " + klass + ", got " + value.getClass(), klass.isInstance(value));
        return klass.cast(value);
    }

    @Test
    public void parseNumbers() {
        assertEquals(12345, literalAs("12345", IntegerLiteralNode.class));
        assertEquals("123456789012345678901234567890", literalAs("123456789012345678901234567890", PIntLiteralNode.class).toString());
        assertEquals(1.23, literalAs("1.23", DoubleLiteralNode.class));
        PComplex pComplex = literalAs("1.32j", ComplexLiteralNode.class, PComplex.class);
        assertEquals(pComplex.getReal(), 0.0, 0.0);
        assertEquals(pComplex.getImag(), 1.32, 0.0);
        assertEquals(0x123, literalAs("0x123", IntegerLiteralNode.class));
        assertEquals(0b11, literalAs("0b11", IntegerLiteralNode.class));
        assertEquals(077, literalAs("0o77", IntegerLiteralNode.class));
    }

    @Test
    public void parseLiteralList() {
        PList list = literalAs("[1, 2]", ListLiteralNode.class, PList.class);
        assertEquals(2, list.getSequenceStorage().length());
        assertEquals(1, list.getSequenceStorage().getItemNormalized(0));
        assertEquals(2, list.getSequenceStorage().getItemNormalized(1));
        list = literalAs("[1]", ListLiteralNode.class, PList.class);
        assertEquals(1, list.getSequenceStorage().length());
        assertEquals(1, list.getSequenceStorage().getItemNormalized(0));
        list = literalAs("[]", ListLiteralNode.class, PList.class);
        assertEquals(0, list.getSequenceStorage().length());
    }

    @Test
    public void parseLiteralDict() {
        PDict dict = literalAs("{1: 2}", DictLiteralNode.class, PDict.class);
        assertEquals(1, dict.size());
        assertEquals(2, dict.getItem(1));

        dict = literalAs("{}", DictLiteralNode.class, PDict.class);
        assertEquals(0, dict.size());

        parseAs("{**{1: 2}}", DictConcatNode.class);
        parseAs("{**a}", DictConcatNode.class);
        parseAs("{1:2, **a}", DictConcatNode.class);
        parseAs("{i:i%2 for i in range(20)}", PythonCallNode.class);
    }

    @Test
    public void parseLiteralSet() {
        PSet dict = literalAs("{1, 2}", SetLiteralNode.class, PSet.class);
        Set<Object> s = new HashSet<>();
        dict.getDictStorage().keys().forEach(s::add);
        assertEquals(s, Arrays.stream(new Integer[]{1, 2}).collect(Collectors.toSet()));

        dict = literalAs("{1}", SetLiteralNode.class, PSet.class);
        s = new HashSet<>();
        dict.getDictStorage().keys().forEach(s::add);
        assertEquals(s, Arrays.stream(new Integer[]{1}).collect(Collectors.toSet()));
    }

    @Test
    public void parseLiteralTuple() {
        PTuple tuple = literalAs("(1, 2)", TupleLiteralNode.class, PTuple.class);
        assertArrayEquals(new Object[]{1, 2}, tuple.getArray());
        literalAs("(1)", IntegerLiteralNode.class, Integer.class);
        tuple = literalAs("(1,)", TupleLiteralNode.class, PTuple.class);
        assertArrayEquals(new Object[]{1}, tuple.getArray());
        tuple = literalAs("()", TupleLiteralNode.class, PTuple.class);
        assertArrayEquals(new Object[0], tuple.getArray());
    }

    @Test
    public void parseLiteralString() {
        assertEquals("a string", literalAs("'a string'", StringLiteralNode.class));
        assertEquals("another string", literalAs("\"another string\"", StringLiteralNode.class));
        assertEquals("\na long string\n", literalAs("\"\"\"\n" +
                        "a long string\n" +
                        "\"\"\"", StringLiteralNode.class));
        assertEquals("a\tstring\n", literalAs("'a\\tstring\\n'", StringLiteralNode.class));
        assertEquals("\t\n\u000B\r\f\u0007\b", literalAs("'\\t\\n\\v\\r\\f\\a\\b'", StringLiteralNode.class));
        assertEquals("\\t\\n\\v\\r\\f\\a\\b", literalAs("r'\\t\\n\\v\\r\\f\\a\\b'", StringLiteralNode.class));
    }

    @Test
    public void parseGlobalVariable() {
        parseAs("foobar13_ddsA", ReadNameNode.class);
    }

    @Test
    public void parsePropertyAccess() {
        parseAs("foobar13_ddsA.property", GetAttributeNode.class);
        GetAttributeNode anotherProperty = parseAs("foobar13_ddsA.property.anotherProperty", GetAttributeNode.class);
        GetAttributeNode property = assertInstanceOf(anotherProperty.getObject(), GetAttributeNode.class);
        assertInstanceOf(property.getObject(), ReadNameNode.class);
    }

    @Test
    public void parseSubscript() {
        GetItemNode node = parseAs("foobar[1]", GetItemNode.class);
        assertInstanceOf(node.getLeftNode(), ReadNameNode.class);
        parseAs("foobar[:]", GetItemNode.class);
        parseAs("foobar[::]", GetItemNode.class);
        parseAs("foobar[1:2:3]", GetItemNode.class);
        GetItemNode parseAs = parseAs("foobar[1,2]", GetItemNode.class);
        assertInstanceOf(parseAs.getSlice(), TupleLiteralNode.class);
    }

    @Test
    public void parseCall() {
        parseAs("foo()", PythonCallNode.class);
        parseAs("foo(1)", PythonCallNode.class);
        parseAs("foo(1,2)", PythonCallNode.class);
        parseAs("foo(1,a=1,b=2)", PythonCallNode.class);
        parseAs("foo(1,a=1,*[1,2])", PythonCallNode.class);
        parseAs("foo(1,a=1,*[1,2],b=2,**kwargs)", PythonCallNode.class);
        parseAs("foo(1,a=1,**kwargs)", PythonCallNode.class);
        parseAs("foo(a=1,*[1,2],**kwargs)", PythonCallNode.class);
        parseAs("foo(a=1,**kwargs)", PythonCallNode.class);
        parseAs("foo(a=1,*rest)", PythonCallNode.class);
        parseAs("foo(*rest)", PythonCallNode.class);
        parseAs("foo(**kwargs)", PythonCallNode.class);
    }

    @Test
    public void parseLiteralSpecial() {
        assertSame(true, literalAs("True", BooleanLiteralNode.class));
        assertSame(false, literalAs("False", BooleanLiteralNode.class));
        assertSame(PNone.NONE, literalAs("None", ObjectLiteralNode.class));
    }

    @Test
    public void parseDelStatement() {
        parseAs("del world", DeleteNameNode.class);
        parseAs("del world[0]", DeleteItemNode.class);
        parseAs("del world.field", DeleteAttributeNode.class);
    }

    @Test
    public void parseAssignments() {
        WriteNameNode parseAs = parseAs("a = 1", WriteNameNode.class);
        assertEquals("a", parseAs.getAttributeId());

        SetAttributeNode parseAsField = parseAs("a.b = 1", SetAttributeNode.class);
        assertEquals("b", parseAsField.getAttributeId());

        parseAs("a[1] = 1", SetItemNode.class);

        parseAs = parseAs("a = 1,2", WriteNameNode.class);
        assert parseAs.getRhs() instanceof TupleLiteralNode;

        parseAs("a, = 1,", DestructuringAssignmentNode.class);
        parseAs("a,b = 1,2", DestructuringAssignmentNode.class);
        parseAs("a,*b,c = 1,2", DestructuringAssignmentNode.class);
        parseAs("[[a],*b],c = 1,2", DestructuringAssignmentNode.class);
    }

    @Test
    public void parseImport() {
        WriteNameNode importSet = parseAs("import foo", WriteNameNode.class);
        assertEquals("foo", importSet.getAttributeId());
        assert importSet.getRhs() instanceof ImportNode.ImportExpression;

        importSet = parseAs("import foo as bar", WriteNameNode.class);
        assertEquals("bar", importSet.getAttributeId());
        assert importSet.getRhs() instanceof ImportNode.ImportExpression;

        parseAs("from os import *", ImportStarNode.class);
    }

    @Test
    public void parseComparisons() {
        parseAs("x < y", BinaryComparisonNode.class);
        parseAs("x > y", BinaryComparisonNode.class);
        parseAs("x == y", BinaryComparisonNode.class);
        parseAs("x >= y", BinaryComparisonNode.class);
        parseAs("x <= y", BinaryComparisonNode.class);
        parseAs("x <> y", BinaryComparisonNode.class);
        parseAs("x != y", BinaryComparisonNode.class);
        parseAs("x in y", ContainsNode.class);
        CastToBooleanNode notNode = parseAs("x not in y", CastToBooleanNode.NotNode.class);
        getChild(notNode, 0, ContainsNode.class);
        parseAs("x is y", IsExpressionNode.class);
        notNode = parseAs("x is not y", CastToBooleanNode.NotNode.class);
        getChild(notNode, 0, IsExpressionNode.class);

        AndNode parseAs = parseAs("x < y() <= z", AndNode.class);
        PNode leftNode = parseAs.getLeftNode();
        assert leftNode instanceof ExpressionNode.ExpressionWithSideEffect;
        WriteNode tmpWrite = getChild(leftNode, 0, WriteNode.class);
        assert tmpWrite.getRhs() instanceof PythonCallNode;
        PythonCallNode rhs = (PythonCallNode) tmpWrite.getRhs();
        assertEquals("y", rhs.getCalleeName());
        getChild(leftNode, 1, BinaryComparisonNode.class);
    }

    @Test
    public void parseUnaryOps() {
        Assume.assumeFalse(PythonOptions.getOption(context.getEnv(), PythonOptions.UseExperimentalParser));
        parseAs("-1", UnaryArithmetic.UnaryArithmeticExpression.class);
        parseAs("+1", UnaryArithmetic.UnaryArithmeticExpression.class);
        parseAs("~1", UnaryArithmetic.UnaryArithmeticExpression.class);
        parseAs("not 1", NotNode.class);
    }

    @Test
    public void parseBooleanOps() {
        parseAs("1 and 2", AndNode.class);
        parseAs("1 or 2", OrNode.class);
        OrNode parseAs = parseAs("1 or 2 and 3", OrNode.class);
        assert parseAs.getRightNode() instanceof AndNode;
    }

    @Test
    public void parseBinaryOp() {
        parseAs("1 | 1", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 ^ 1", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 & 1", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 << 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 >> 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 >> 2 << 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 | 1 & 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 + 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 - 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 * 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 / 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 % 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 // 2", BinaryArithmetic.BinaryArithmeticExpression.class);
        parseAs("1 ** 2", TernaryArithmetic.TernaryArithmeticExpression.class);
    }

    @Test
    public void parseFuncdef() {
        WriteNameNode parseAs = parseAs("def fun(): pass", WriteNameNode.class);
        assertEquals("fun", parseAs.getAttributeId());
        assert parseAs.getRhs() instanceof FunctionDefinitionNode;

        parseAs = parseAs("def fun(a): pass", WriteNameNode.class);
        assertEquals("fun", parseAs.getAttributeId());
        assert parseAs.getRhs() instanceof FunctionDefinitionNode;

        parseAs = parseAs("def fun(a,b=1): pass", WriteNameNode.class);
        assertEquals("fun", parseAs.getAttributeId());
        assert parseAs.getRhs() instanceof FunctionDefinitionNode;

        parseAs = parseAs("def fun(a,b=1,*c): pass", WriteNameNode.class);
        assertEquals("fun", parseAs.getAttributeId());
        assert parseAs.getRhs() instanceof FunctionDefinitionNode;

        parseAs = parseAs("def fun(a,b=1,*c,d=2): pass", WriteNameNode.class);
        assertEquals("fun", parseAs.getAttributeId());
        assert parseAs.getRhs() instanceof FunctionDefinitionNode;

        parseAs = parseAs("def fun(a,b=1,*c,d=2,**kwargs): pass", WriteNameNode.class);
        assertEquals("fun", parseAs.getAttributeId());
        assert parseAs.getRhs() instanceof FunctionDefinitionNode;
    }

    @Test
    public void parseLambdef() {
        parseAs("lambda x: x", FunctionDefinitionNode.class);
        parseAs("lambda y,x=1: x", FunctionDefinitionNode.class);
        parseAs("lambda: 5", FunctionDefinitionNode.class);
        parseAs("lambda y,x=1,*ar,**kwar: x", FunctionDefinitionNode.class);
    }

    @Test
    public void parseGenerator() {
        GeneratorExpressionNode parseAs = parseAs("(y for x in range(10) if x > 2 if x < 10 for y in range(x))", GeneratorExpressionNode.class);
        NodeUtil.printCompactTreeToString(parseAs);
        parseAs("list(y for x in range(10) if x > 2 if x < 10 for y in range(x))", PythonCallNode.class);
        parseAs("[y for x in range(10) if x > 2 if x < 10 for y in range(x)]", PythonCallNode.class);
        PythonCallNode parseAs2 = parseAs("[[0 for x in range(i)] for i in range(size)]", PythonCallNode.class);
        NodeUtil.printCompactTreeToString(parseAs2);
    }
}
