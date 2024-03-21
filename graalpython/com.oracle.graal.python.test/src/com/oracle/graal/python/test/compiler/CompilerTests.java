/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.compiler;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.CompilationUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.FutureFeature;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.test.GraalPythonEnvVars;
import com.oracle.graal.python.test.PythonTests;

public class CompilerTests extends PythonTests {
    public CompilerTests() {
    }

    @Before
    public void beforeTest() {
        // These tests are coupled to the manual bytecode interpreter. They shouldn't run if we're
        // using the Bytecode DSL interpreter.
        Assume.assumeFalse(PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER);
    }

    @Rule public TestName name = new TestName();

    @Test
    public void testBinaryOp() {
        doTest("1 + 1");
    }

    @Test
    public void testComplexNumber() {
        doTest("-2 + 3j");
    }

    @Test
    public void testMinusFolding() {
        doTest("-1 * -7.0");
    }

    @Test
    public void testAssignment() {
        doTest("a = 12");
    }

    @Test
    public void testAugAssignment() {
        doTest("a += 12.0");
    }

    @Test
    public void testAugAssignmentAttr() {
        doTest("a.b += 12.0");
    }

    @Test
    public void testAugAssignmentSubscr() {
        doTest("a[b] += 12.0");
    }

    @Test
    public void testAnnAssignment() {
        doTest("a: int = 12");
    }

    @Test
    public void testDel() {
        doTest("del a");
    }

    @Test
    public void testGetItem() {
        doTest("a[3]");
    }

    @Test
    public void testSetItem() {
        doTest("a[3] = 1");
    }

    @Test
    public void testDelItem() {
        doTest("del a[3]");
    }

    @Test
    public void testSlice() {
        doTest("a[3:9]");
    }

    @Test
    public void testSliceStep() {
        doTest("a[3:9:2]");
    }

    @Test
    public void testCall() {
        doTest("range(num)");
    }

    @Test
    public void testLogicOperators() {
        doTest("a and b or not c");
    }

    @Test
    public void testManyArgs() {
        // Test collecting more args that a single COLLECT_FROM_STACK instruction can handle
        String source = "print(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36)";
        doTest(source);
    }

    @Test
    public void testCallKeyword() {
        doTest("print('test', end=';')");
    }

    @Test
    public void testCallMultiStarArgs() {
        doTest("foo(1, *a, 2, *b, 3)");
    }

    @Test
    public void testCallMultiStarKwargs() {
        doTest("foo(a=1, **a, b=2, **b, c=3)");
    }

    @Test
    public void testVarArgs() {
        String source = "def foo(*args):\n" +
                        "  print(*args)\n";
        doTest(source);
    }

    @Test
    public void testVarKwargs() {
        String source = "def foo(**kwargs):\n" +
                        "  print(**kwargs)\n";
        doTest(source);
    }

    @Test
    public void testArgsCombination() {
        String source = "def foo(a, /, b, *c, d, **e):\n" +
                        "  print(a, b, c, d, e)\n";
        doTest(source);
    }

    @Test
    public void testArgAnnotations() {
        String source = "def foo(a:1, /, b:2, *c:3, d:4, **e:5):\n" +
                        "  print(a, b, c, d, e)\n";
        doTest(source);
    }

    @Test
    public void testClassAnnotations() {
        String source = "class Foo:\n" +
                        "  attr: a[str]";
        doTest(source);
    }

    @Test
    public void testClassAnnotationsFuture() {
        String source = "from __future__ import annotations\n" +
                        "class Foo:\n" +
                        "  attr: a[str]";
        doTest(source);
    }

    @Test
    public void testFor() {
        doTest("for i in [1,2]:\n pass");
    }

    @Test
    public void testWhile() {
        doTest("while False: pass");
    }

    @Test
    public void testForBreakContinue() {
        String source = "for i in range(10):\n" +
                        "  if i == 3:\n" +
                        "    break\n" +
                        "  else:\n" +
                        "    continue\n" +
                        "else:\n" +
                        "  print('else')";
        doTest(source);
    }

    @Test
    public void testWhileBreakContinue() {
        String source = "i = 0\n" +
                        "while i < 10:\n" +
                        "  if i == 3:\n" +
                        "    break\n" +
                        "  else:\n" +
                        "    i += 1\n" +
                        "    continue\n" +
                        "else:\n" +
                        "  print('else')";
        doTest(source);
    }

    @Test
    public void testBreakFromWith() {
        String source = "for i in range(10):\n" +
                        "  with foo() as cm:\n" +
                        "    break\n";
        doTest(source);
    }

    @Test
    public void testBreakFromTry() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    break\n" +
                        "  finally:" +
                        "    print('finally')";
        doTest(source);
    }

    @Test
    public void testBreakFromExcept() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    1 / 0\n" +
                        "  except RuntimeError as e:" +
                        "    break";
        doTest(source);
    }

    @Test
    public void testBreakFromFinally() {
        String source = "for i in range(10):\n" +
                        "  try:\n" +
                        "    if i:\n" +
                        "      break\n" +
                        "    print(i)\n" +
                        "  finally:\n" +
                        "    print('finally')\n" +
                        "    break";
        doTest(source);
    }

    @Test
    public void testReturnFromWith() {
        String source = "def foo():\n" +
                        "  for i in range(10):\n" +
                        "    with foo() as cm:\n" +
                        "      return a\n";
        doTest(source);
    }

    @Test
    public void testReturnFromTry() {
        String source = "def foo():\n" +
                        "  for i in range(10):\n" +
                        "    try:\n" +
                        "      return a\n" +
                        "    finally:" +
                        "      print('finally')";
        doTest(source);
    }

    @Test
    public void testReturnFromExcept() {
        String source = "def foo():\n" +
                        "  for i in range(10):\n" +
                        "    try:\n" +
                        "      1 / 0\n" +
                        "    except RuntimeError as e:" +
                        "      return a";
        doTest(source);
    }

    @Test
    public void testReturnFromFinally() {
        String source = "def foo():\n" +
                        "  for i in range(10):\n" +
                        "    try:\n" +
                        "      if i:\n" +
                        "        return a\n" +
                        "      print(i)\n" +
                        "    finally:\n" +
                        "      print('finally')\n" +
                        "      return b";
        doTest(source);
    }

    @Test
    public void testFinallyCancelReturn() {
        String source = "def foo():\n" +
                        "  for i in range(10):\n" +
                        "    try:\n" +
                        "      return a\n" +
                        "    finally:" +
                        "      continue";
        doTest(source);
    }

    @Test
    public void testTryExcept() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryExceptBare() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except:\n" +
                        "  print('except bare')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryFinally() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "finally:\n" +
                        "  print('finally')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryFinallyNested() {
        String source = "def foo(obj):\n" +
                        "    for x in obj:\n" +
                        "        print(x)\n" +
                        "    try:\n" +
                        "        try:\n" +
                        "            print('try')\n" +
                        "        finally:\n" +
                        "            print('finally1')\n" +
                        "    finally:\n" +
                        "        print('finally2')\n";
        doTest(source);
    }

    @Test
    public void testTryExceptFinally() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "finally:\n" +
                        "  print('finally')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryExceptElse() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "else:\n" +
                        "  print('else')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testTryExceptElseFinally() {
        String s = "print('before')\n" +
                        "try:\n" +
                        "  print('try')\n" +
                        "except TypeError as e:\n" +
                        "  print('except1')\n" +
                        "except ValueError as e:\n" +
                        "  print('except2')\n" +
                        "else:\n" +
                        "  print('else')\n" +
                        "finally:\n" +
                        "  print('finally')\n" +
                        "print('after')\n";
        doTest(s);
    }

    @Test
    public void testWith() {
        String s = "print('before')\n" +
                        "with open('/dev/null') as f:\n" +
                        "  f.write('foo')\n" +
                        "print('after')";
        doTest(s);
    }

    @Test
    public void testWithMultiple() {
        String s = "print('before')\n" +
                        "with open('/dev/null') as f, open('/tmp/foo'):\n" +
                        "  f.write('foo')\n" +
                        "print('after')";
        doTest(s);
    }

    @Test
    public void testDefun() {
        String source = "def docompute(num, num2=5):\n" +
                        "   return (num, num2)\n";
        doTest(source);
    }

    @Test
    public void testReturnPlain() {
        String source = "def foo():\n" +
                        "   return\n";
        doTest(source);
    }

    @Test
    public void testClosure() {
        String s = "def foo():\n" +
                        "    x = 1\n" +
                        "    def bar():\n" +
                        "        nonlocal x\n" +
                        "        print(x)\n" +
                        "        x = 2\n" +
                        "    bar()\n" +
                        "    print(x)\n" +
                        "    x = 3\n";
        doTest(s);
    }

    @Test
    public void testIf() {
        String source = "if False:\n" +
                        "   print(True)\n" +
                        "else:\n" +
                        "   print(False)\n";
        doTest(source);
    }

    @Test
    public void testIfExpression() {
        doTest("t if cond else f\n");
    }

    @Test
    public void testClass() {
        String source = "class Foo:\n" +
                        "    c = 64\n" +
                        "    def __init__(self, arg):\n" +
                        "        self.var = arg\n";
        doTest(source);
    }

    @Test
    public void testSuper() {
        String source = "class Foo:\n" +
                        "    def boo(self):\n" +
                        "        print('boo')\n" +
                        "class Bar(Foo):\n" +
                        "    def boo(self):\n" +
                        "        super().boo()\n";
        doTest(source);
    }

    @Test
    public void testEmptyList() {
        doTest("[]");
    }

    @Test
    public void testEmptyTuple() {
        doTest("()");
    }

    @Test
    public void testEmptyDict() {
        doTest("{}");
    }

    @Test
    public void testTupleLiteralInts() {
        doTest("(1, 2, 3)");
    }

    @Test
    public void testTupleLiteralDoubles() {
        doTest("(1.0, 2.0, 3.0)");
    }

    @Test
    public void testTupleLiteralBooleans() {
        doTest("(False, True)");
    }

    @Test
    public void testTupleLiteralObjects() {
        doTest("('a', 1, None)");
    }

    @Test
    public void testTupleLiteralMixed() {
        doTest("(1, 2, 3.0)");
    }

    @Test
    public void testTupleLiteralNonConstant() {
        doTest("(1, 2, [3])");
    }

    @Test
    public void testTupleLiteralMixedIntegers() {
        doTest("(1, 17179869184, 3)");
    }

    @Test
    public void testTupleLiteralExpand() {
        doTest("(1, 2, 3, *a, 5)");
    }

    @Test
    public void testListLiteral() {
        doTest("[1, 2, 3]");
    }

    @Test
    public void testListLiteralExpand() {
        doTest("[1, 2, 3, *a, 5]");
    }

    @Test
    public void testSetLiteral() {
        doTest("{1, 2, 3}");
    }

    @Test
    public void testSetLiteralExpand() {
        doTest("{1, 2, 3, *a, 5}");
    }

    @Test
    public void testDictLiteral() {
        doTest("{'a': 'b', 1: 2}");
    }

    @Test
    public void testDictLiteralExpand() {
        doTest("{'a': 'b', 1: 2, **a, None: True}");
    }

    @Test
    public void testUnpack() {
        doTest("a, b = 1, 2");
    }

    @Test
    public void testUnpackEx() {
        doTest("a, *b, c = 1, 2, 3, 4, 5");
    }

    @Test
    public void testListComprehension() {
        String source = "[str(x) for y in [[1, 2, 3], [4, 5, 6]] for x in y if x < 5]";
        doTest(source);
    }

    @Test
    public void testFString() {
        doTest("f'before{a}middle{b!r:5}after'");
    }

    @Test
    public void testStringSurrogates() {
        doTest("'\\U00010400' != '\\uD801\\uDC00'");
    }

    @Test
    public void testNestedListComprehension() {
        String source = "[[x for x in range(5)] for y in range(3)]";
        doTest(source);
    }

    @Test
    public void testSetComprehension() {
        String source = "{x * 2 for x in range(10) if x % 2 == 0}";
        doTest(source);
    }

    @Test
    public void testDictComprehension() {
        String source = "{x: str(x) for x in range(10)}";
        doTest(source);
    }

    @Test
    public void testLambda() {
        doTest("lambda x, *args: args[x]");
    }

    @Test
    public void testYieldPlain() {
        String source = "def gen(a):\n" +
                        "    yield\n";
        doTest(source);
    }

    @Test
    public void testYieldValue() {
        String source = "def gen(a):\n" +
                        "    yield a + 1\n";
        doTest(source);
    }

    @Test
    public void testYieldFrom() {
        String source = "def gen(a):\n" +
                        "    yield from a\n";
        doTest(source);
    }

    @Test
    public void testCoroutine() {
        String source = "async def foo(a):\n" +
                        "    await a\n";
        doTest(source);
    }

    @Test
    public void testYieldExpression() {
        String source = "def gen(a):\n" +
                        "    b = yield a\n";
        doTest(source);
    }

    @Test
    public void testGeneratorComprehension() {
        String source = "(str(x) for y in [[1, 2, 3], [4, 5, 6]] for x in y if x < 5)";
        doTest(source);
    }

    @Test
    public void testExtendedArgs() {
        StringBuilder source = new StringBuilder();
        source.append("if a:\n");
        for (int i = 0; i < 260; i++) {
            source.append(String.format("   a.f%d('%d')\n", i, i));
        }
        source.append("else:\n");
        source.append("    print('else')");
        doTest(source.toString());
    }

    @Test
    public void testBenchmark() {
        String source = "def docompute(num):\n" +
                        "    for i in range(num):\n" +
                        "        sum_ = 0.0\n" +
                        "        j = 0\n" +
                        "        while j < num:\n" +
                        "            sum_ += 1.0 / (((i + j) * (i + j + 1) >> 1) + i + 1)\n" +
                        "            j += 1\n" +
                        "\n" +
                        "    return sum_\n" +
                        "\n" +
                        "\n" +
                        "def measure(num):\n" +
                        "    for run in range(num):\n" +
                        "        sum_ = docompute(10000)  # 10000\n" +
                        "    print('sum', sum_)\n" +
                        "\n" +
                        "\n" +
                        "def __benchmark__(num=5):\n" +
                        "    measure(num)\n";
        doTest(source);
    }

    @Test
    public void testBenchmark2() {
        String source = "" +
                        "class HandlerTask(Task):\n" +
                        "    def __init__(self,i,p,w,s,r):\n" +
                        "        global Task\n" +
                        "        x = 0\n" +
                        "        raise ValueError\n" +
                        // " def f():\n" +
                        // " nonlocal x\n" +
                        // " x = 1\n" +
                        "        Task.__init__(self,i,p,w,s,r)\n";
        doTest(source);
    }

    @Test
    public void testImport() {
        String source = "" +
                        "if __name__ == '__main__':\n" +
                        "    import sys\n" +
                        "    if not (len(sys.argv) == 1 and sys.argv[0] == 'java_embedding_bench'):\n" +
                        "        import time\n" +
                        "        start = time.time()\n" +
                        "        if len(sys.argv) >= 2:\n" +
                        "            num = int(sys.argv[1])\n" +
                        "            __benchmark__(num)\n" +
                        "        else:\n" +
                        "            __benchmark__()\n" +
                        "        print(\"%s took %s s\" % (__file__, time.time() - start))\n";
        doTest(source);
    }

    @Test
    public void testImportAs() {
        doTest("import a.b.c as d");
    }

    @Test
    public void testImportFrom() {
        doTest("from math import sqrt, sin as sine");
    }

    @Test
    public void testImportStar() {
        doTest("from a.b import *");
    }

    @Test
    public void testEval() {
        doTest("1", InputType.EVAL);
    }

    @Test
    public void testSingle() {
        doTest("1", InputType.SINGLE);
    }

    @Test
    public void testLoadClassDefRef() {
        String s = "def f(x): \n" +
                        "    class C: y = x\n" +
                        "f(1)";
        doTest(s);
    }

    @Test
    public void testNamedExpr() {
        String s = "if x := g():\n  print(x)\n";
        doTest(s);
    }

    @Test
    public void testMatchValueConst() {
        String source = "" +
                        "match 1:\n" +
                        "   case 1:\n" +
                        "       pass\n";
        doTest(source);
    }

    @Test
    public void testMatchValue() {
        String source = "" +
                        "s = 1\n" +
                        "match s:\n" +
                        "   case 1:\n" +
                        "       pass\n";
        doTest(source);
    }

    @Test
    public void testMatchValueWithDefault() {
        String source = "" +
                        "s = 1\n" +
                        "match s:\n" +
                        "   case 1:\n" +
                        "       pass\n" +
                        "   case _:\n" +
                        "       pass\n";
        doTest(source);
    }

    @Test
    public void testMatchSingletonBoolean() {
        String source = "" +
                        "match 1:\n" +
                        "   case True:\n" +
                        "       pass\n";
        doTest(source);
    }

    @Test
    public void testMatchSingletonNone() {
        String source = "" +
                        "match 1:\n" +
                        "   case None:\n" +
                        "       pass\n";
        doTest(source);
    }

    @Test
    public void testGuard() {
        String source = "" +
                        "x = 1\n" +
                        "match 1:\n" +
                        "   case 1 if x == 1:\n" +
                        "       x\n";
        doTest(source);
    }

    @Test
    public void testMatchAs() {
        String source = "" +
                        "match 1:\n" +
                        "   case 1 as x:\n" +
                        "       x\n";
        doTest(source);
    }

    @Test
    public void testMatchAs2() {
        String source = "" +
                        "match 1:\n" +
                        "   case 1 as x:\n" +
                        "       x\n" +
                        "   case 2 as y:\n" +
                        "       x\n";
        doTest(source);
    }

    @Test
    public void testMatchAsDefault() {
        String source = "" +
                        "match 1:\n" +
                        "   case 1 as x:\n" +
                        "       x\n" +
                        "   case _:\n" +
                        "       x\n";
        doTest(source);
    }

    @Test
    public void testMatchAsGuard() {
        String source = "" +
                        "match 1:\n" +
                        "   case 1 as x if x == 1:\n" +
                        "       x\n";
        doTest(source);
    }

    @Test
    public void testWildcard() {
        String source = "" +
                        "match 1:\n" +
                        "  case _:\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testSeq() {
        String source = "" +
                        "match (1):\n" +
                        "  case [1]:\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testSeqWildcard() {
        String source = "" +
                        "match (1):\n" +
                        "  case [_]:\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testSeqWildcardStar() {
        String source = "" +
                        "match (1):\n" +
                        "  case [*_]:\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testSeqWildcardSubscript() {
        String source = "" +
                        "match (1, 2):\n" +
                        "  case [_, x]:\n" +
                        "    x";
        doTest(source);
    }

    @Test
    public void testSeqWildcardStarSubscript() {
        String source = "" +
                        "match (1, 2, 3):\n" +
                        "  case [*_, y]:\n" +
                        "    y";
        doTest(source);
    }

    @Test
    public void testMatchClass() {
        String source = "" +
                        "match 1:\n" +
                        "  case int(x):\n" +
                        "      pass";
        doTest(source);
    }

    @Test
    public void testMatchOr() {
        String source = "" +
                        "match 0:\n" +
                        "  case 0 | 1:\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testMatchOrRot() {
        String source = "" +
                        "match (0, 1):\n" +
                        "  case ((a, b) | (b, a)):\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testMatchMapping() {
        String source = "" +
                        "match {1:1}:\n" +
                        "  case {1:1}:\n" +
                        "    pass";
        doTest(source);
    }

    @Test
    public void testMatchMappingSubpattern() {
        String source = "" +
                        "match {1:1}:\n" +
                        "  case {1:x}:\n" +
                        "    x";
        doTest(source);
    }

    @Test
    public void testMatchMappingStar() {
        String source = "" +
                        "match {1:1}:\n" +
                        "  case {**z}:\n" +
                        "    z";
        doTest(source);
    }

    @Test
    public void testAssignToDebug() {
        checkSyntaxErrorMessage("obj.__debug__ = 1", "cannot assign to __debug__");
        checkSyntaxErrorMessage("__debug__ = 1", "cannot assign to __debug__");
        checkSyntaxErrorMessage("(a, __debug__, c) = (1, 2, 3)", "cannot assign to __debug__");
        checkSyntaxErrorMessage("(a, *__debug__, c) = (1, 2, 3)", "cannot assign to __debug__");
        checkSyntaxErrorMessage("f(__debug__=1)", "cannot assign to __debug__");
        checkSyntaxErrorMessage("__debug__: int", "cannot assign to __debug__");
        checkSyntaxErrorMessage("__debug__ += 1", "cannot assign to __debug__");
        checkSyntaxErrorMessage("def f(*, x=lambda __debug__:0): pass", "cannot assign to __debug__");
        checkSyntaxErrorMessage("def f(*args:(lambda __debug__:0)): pass", "cannot assign to __debug__");
        checkSyntaxErrorMessage("def f(**kwargs:(lambda __debug__:0)): pass", "cannot assign to __debug__");
        checkSyntaxErrorMessage("def f(**__debug__): pass", "cannot assign to __debug__");
        checkSyntaxErrorMessage("def f(*xx, __debug__): pass", "cannot assign to __debug__");
        checkSyntaxErrorMessage("match 1:\n\tcase 1 as __debug__:\n\t\tpass", "cannot assign to __debug__");
    }

    @Test
    public void testNoStarredExprHere() {
        checkSyntaxErrorMessage("*[1,2,3]", "can't use starred expression here");
        checkSyntaxErrorMessage("*a = range(5)", "starred assignment target must be in a list or tuple");
        checkSyntaxErrorMessage("b = *a", "can't use starred expression here");
    }

    @Test
    public void testRepeatedKwArg() {
        checkSyntaxErrorMessage("f(p, k1=50, *(1,2), k1=100)", "keyword argument repeated: k1");
    }

    @Test
    public void testYieldOutsideFunction() {
        checkSyntaxErrorMessage("yield", "'yield' outside function");
        checkSyntaxErrorMessage("class foo:yield 1", "'yield' outside function");
        checkSyntaxErrorMessage("class foo:yield from ()", "'yield' outside function");
        checkSyntaxErrorMessage("def g(a:(yield)): pass", "'yield' outside function");
        checkSyntaxErrorMessage("yield x", "'yield' outside function");
        checkSyntaxErrorMessage("class C: yield 1", "'yield' outside function");
    }

    @Test
    public void testReturnFromAsyncWith() {
        String source = "async def f():\n" +
                        "  async with a:\n" +
                        "     return";
        doTest(source);
    }

    @Test
    public void testReturnFromAsyncWithT() {
        String source = "async def f():\n" +
                        "  async with a:\n" +
                        "    async with b:\n" +
                        "      return";
        doTest(source);

    }

    private void doTest(String src) {
        doTest(src, InputType.FILE);
    }

    private void doTest(String src, InputType type) {
        checkCodeUnit(assemble(src, type));
    }

    private static void checkSyntaxErrorMessage(String src, String msg) {
        try {
            assemble(src, InputType.FILE);
            fail("Expected SyntaxError: " + msg);
        } catch (SyntaxError e) {
            Assert.assertEquals(ErrorCallback.ErrorType.Syntax, e.errorType);
            Assert.assertThat(e.message, CoreMatchers.containsString(msg));
        }
    }

    private static CodeUnit assemble(String src, InputType type) {
        ErrorCallback errorCallback = new TestErrorCallbackImpl();
        Parser parser = Compiler.createParser(src, errorCallback, type, false);
        ModTy result = (ModTy) parser.parse();
        Compiler compiler = new Compiler(errorCallback);
        CompilationUnit cu = compiler.compile(result, EnumSet.noneOf(Compiler.Flags.class), 2, EnumSet.noneOf(FutureFeature.class));
        return cu.assemble();
    }

    private void checkCodeUnit(CodeUnit co) {
        String coString = co.toString();
        Path goldenFile = Paths.get(GraalPythonEnvVars.graalPythonTestsHome(),
                        "com.oracle.graal.python.test", "testData", "goldenFiles",
                        this.getClass().getSimpleName(),
                        name.getMethodName() + ".co");
        try {
            if (!Files.exists(goldenFile)) {
                Files.createDirectories(goldenFile.getParent());
                Files.writeString(goldenFile, coString);
            } else {
                Assert.assertEquals(Files.readString(goldenFile), coString);
            }
        } catch (IOException ex) {
            fail(ex.getMessage());
        }
    }

    static class TestErrorCallbackImpl implements ErrorCallback {
        @Override
        public void reportIncompleteSource(int line) {
            fail("Unexpected call to reportIncompleteSource");
        }

        @Override
        public void onError(ErrorType errorType, SourceRange sourceRange, String message) {
            throw new SyntaxError(errorType, message);
        }

        @Override
        public void onWarning(WarningType warningType, SourceRange sourceRange, String message) {
            throw new AssertionError("Unexpected " + warningType + " warning: " + message);
        }
    }

    private static final class SyntaxError extends RuntimeException {
        private static final long serialVersionUID = 6182610312044069775L;

        final ErrorCallback.ErrorType errorType;
        final String message;

        SyntaxError(ErrorCallback.ErrorType errorType, String message) {
            this.errorType = errorType;
            this.message = message;
        }
    }
}
