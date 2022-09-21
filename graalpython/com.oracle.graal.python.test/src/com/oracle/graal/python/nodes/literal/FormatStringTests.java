/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.nodes.literal;

import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.test.PythonTests;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.parser.sst.*;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.test.parser.ParserTestBase;
import com.oracle.truffle.api.strings.TruffleString;

public class FormatStringTests extends ParserTestBase {

    @Test
    public void empty() throws Exception {
        testFormatString("f''", "");
    }

    @Test
    public void justBraces() {
        testFormatString("f'{{}}'", "{}");
    }

    @Test
    public void doubleBrace01() {
        testFormatString("f'{{name}}'", "{name}");
    }

    @Test
    public void doubleBrace02() {
        testFormatString("f'Hi {{name}}'", "Hi {name}");
    }

    @Test
    public void doubleBrace03() {
        testFormatString("f'{{name}} first'", "{name} first");
    }

    @Test
    public void doubleBrace04() {
        testFormatString("f'Hi {{name}} first'", "Hi {name} first");
    }

    @Test
    public void doubleBrace05() {
        testFormatString("f'{{'", "{");
    }

    @Test
    public void doubleBrace06() {
        testFormatString("f'a{{'", "a{");
    }

    @Test
    public void doubleBrace07() {
        testFormatString("f'{{b'", "{b");
    }

    @Test
    public void doubleBrace08() {
        testFormatString("f'a{{b'", "a{b");
    }

    @Test
    public void doubleBrace09() {
        testFormatString("f'}}'", "}");
    }

    @Test
    public void doubleBrace10() {
        testFormatString("f'a}}'", "a}");
    }

    @Test
    public void doubleBrace11() {
        testFormatString("f'}}b'", "}b");
    }

    @Test
    public void doubleBrace12() {
        testFormatString("f'a}}b'", "a}b");
    }

    @Test
    public void doubleBrace13() {
        testFormatString("f'{{}}'", "{}");
    }

    @Test
    public void doubleBrace14() {
        testFormatString("f'a{{}}'", "a{}");
    }

    @Test
    public void doubleBrace15() {
        testFormatString("f'{{b}}'", "{b}");
    }

    @Test
    public void doubleBrace16() {
        testFormatString("f'{{}}c'", "{}c");
    }

    @Test
    public void doubleBrace17() {
        testFormatString("f'a{{b}}'", "a{b}");
    }

    @Test
    public void doubleBrace18() {
        testFormatString("f'a{{}}c'", "a{}c");
    }

    @Test
    public void doubleBrace19() {
        testFormatString("f'{{b}}'", "{b}");
    }

    @Test
    public void doubleBrace20() {
        testFormatString("f'a{{b}}c'", "a{b}c");
    }

    @Test
    public void doubleBrace21() {
        testFormatString("f'{{{10}'", "{+format((10))");
    }

    @Test
    public void doubleBrace22() {
        testFormatString("f'}}{10}'", "}+format((10))");
    }

    @Test
    public void doubleBrace23() {
        testFormatString("f'}}{{{10}'", "}{+format((10))");
    }

    @Test
    public void doubleBrace24() {
        testFormatString("f'}}a{{{10}'", "}a{+format((10))");
    }

    @Test
    public void doubleBrace25() {
        testFormatString("f'{10}{{'", "format((10))+{");
    }

    @Test
    public void doubleBrace26() {
        testFormatString("f'{10}}}'", "format((10))+}");
    }

    @Test
    public void doubleBrace27() {
        testFormatString("f'{10}}}{{'", "format((10))+}{");
    }

    @Test
    public void doubleBrace28() {
        testFormatString("f'{10}}}a{{'", "format((10))+}a{");
    }

    @Test
    public void quotes01() {
        testFormatString("f'{\"{{}}\"}'", "format((\"{{}}\"))");
    }

    @Test
    public void embeddedColon() {
        testFormatString("f'{var[:1]}'", "format((var[:1]))");
    }

    @Test
    public void parser01() {
        testFormatString("f'{name}'", "format((name))");
    }

    @Test
    public void parser02() {
        testFormatString("f'name'", "name");
    }

    @Test
    public void parser03() {
        testFormatString("f'First: {name}'", "First: +format((name))");
    }

    @Test
    public void parser04() {
        testFormatString("f'{name} was here'", "format((name))+ was here");
    }

    @Test
    public void parser05() {
        testFormatString("f'It {name} was'", "It +format((name))+ was");
    }

    @Test
    public void strWithColon() {
        testFormatString("f'{myarray[:1]}'", "format((myarray[:1]))");
    }

    @Test
    public void str01() {
        testFormatString("f'{name!s}'", "format(str((name)))");
    }

    @Test
    public void repr01() {
        testFormatString("f'{name!r}'", "format(repr((name)))");
    }

    @Test
    public void ascii01() {
        testFormatString("f'{name!a}'", "format(ascii((name)))");
    }

    @Test
    public void emptyExpression01() {
        checkSyntaxError("f'{}'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void emptyExpression02() {
        checkSyntaxError("f'start{}end'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void emptyExpression03() {
        checkSyntaxError("f'start{}}end'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void emptyExpression04() {
        checkSyntaxError("f'start{{{}}}end'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void singleBracket01() {
        checkSyntaxError("f'}'", ErrorMessages.ERROR_MESSAGE_SINGLE_BRACE);
    }

    @Test
    public void singleBracket02() {
        checkSyntaxError("f'start}end'", ErrorMessages.ERROR_MESSAGE_SINGLE_BRACE);
    }

    @Test
    public void singleBracket03() {
        checkSyntaxError("f'start{{}end'", ErrorMessages.ERROR_MESSAGE_SINGLE_BRACE);
    }

    @Test
    public void spaces01() {
        testFormatString("f'{     {}}'", "format((     {}))");
    }

    @Test
    public void spaces02() {
        testFormatString("f'{     {}                 }'", "format((     {}                 ))");
    }

    @Test
    public void innerExp01() {
        testFormatString("f'result: {value:{width}.{precision}}'", "result: +format((value),(format((width))+.+format((precision))))");
    }

    @Test
    public void missingSpecifier01() {
        testFormatString("f'{x:}'", "format((x))");
    }

    @Test
    public void missingSpecifier02() {
        testFormatString("f'{x!s:}'", "format(str((x)))");
    }

    @Test
    public void missingExpression01() {
        checkSyntaxError("f'{!x}'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression02() {
        checkSyntaxError("f'{     !x}'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression03() {
        checkSyntaxError("f'{ !xr:a}'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression04() {
        checkSyntaxError("f'{:x'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression05() {
        checkSyntaxError("f'{!'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    @Test
    public void missingExpression06() {
        checkSyntaxError("f'{10:{ }}'", ErrorMessages.ERROR_MESSAGE_EMPTY_EXPRESSION);
    }

    private void checkSyntaxError(String text, TruffleString expectedMessage) {
        try {
            testFormatString(text, "Expected Error: " + expectedMessage);
        } catch (PException e) {
            Assert.assertEquals("SyntaxError: " + expectedMessage, PythonTests.getExceptionMessage(e));
        }
    }

    private void testFormatString(String fstring, String expected) {
        assert fstring.startsWith("f'") && fstring.endsWith("'");

        parse(fstring, "fstring-test", PythonParser.ParserMode.Statement);
        SSTNode sstResult = getLastSST();

        String result = (new FStringASTPrinter()).print(sstResult);

        Assert.assertEquals(expected, result);
    }

    private static final class FStringASTPrinter implements SSTreeVisitor<Object> {

        private StringBuilder sb;
        private boolean needPlus;

        public String print(SSTNode node) {
            sb = new StringBuilder();
            needPlus = false;
            node.accept(this);
            return sb.toString();
        }

        @Override
        public Object visit(AndSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(AnnAssignmentSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(AnnotationSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(AssertSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(AssignmentSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(AugAssignmentSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(BinaryArithmeticSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(BlockSSTNode node) {
            for (SSTNode statement : node.getStatements()) {
                statement.accept(this);
            }
            return null;
        }

        @Override
        public Object visit(BooleanLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(CallSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ClassSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(CollectionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ComparisonSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(DecoratedSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(DecoratorSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(DelSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ExceptSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ExpressionStatementSSTNode node) {
            return node.getExpression().accept(this);
        }

        @Override
        public Object visit(FloatLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ForComprehensionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ForSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(FunctionDefSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(GetAttributeSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(IfSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ImportFromSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ImportSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(LambdaSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(NotSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(NumberLiteralSSTNode.IntegerLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(NumberLiteralSSTNode.BigIntegerLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(OrSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(RaiseSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(ReturnSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(SimpleSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(SliceSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(StarSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(StringLiteralSSTNode.RawStringLiteralSSTNode node) {
            if (needPlus) {
                sb.append("+");
                needPlus = false;
            }
            sb.append(node.getValue());
            return null;
        }

        @Override
        public Object visit(StringLiteralSSTNode.BytesLiteralSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(StringLiteralSSTNode.FormatExpressionSSTNode node) {
            if (sb.length() != 0) {
                sb.append("+");
            }
            sb.append("format(");
            switch (node.getConversionType()) {
                case ASCII_CONVERSION:
                    sb.append("ascii(");
                    break;
                case REPR_CONVERSION:
                    sb.append("repr(");
                    break;
                case STR_CONVERTION:
                    sb.append("str(");
                    break;
            }
            sb.append(node.getExpressionCode());
            if (node.getConversionType() != StringLiteralSSTNode.FormatStringConversionType.NO_CONVERSION) {
                sb.append(")");
            }
            if (node.getSpecifier() != null) {
                sb.append(",(");
                StringBuilder sbtmp = sb;
                sb = new StringBuilder();
                node.getSpecifier().accept(this);
                sbtmp.append(sb.toString());
                sb = sbtmp;
                sb.append(")");
            }
            sb.append(")");
            needPlus = true;
            return null;
        }

        @Override
        public Object visit(StringLiteralSSTNode.FormatStringLiteralSSTNode node) {
            for (SSTNode part : node.getParts()) {
                part.accept(this);
            }
            return null;
        }

        @Override
        public Object visit(SubscriptSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(TernaryIfSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(TrySSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(UnarySSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(VarLookupSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(WhileSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(WithSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Object visit(YieldExpressionSSTNode node) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
}
