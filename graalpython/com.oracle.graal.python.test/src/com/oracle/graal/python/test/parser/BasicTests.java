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

package com.oracle.graal.python.test.parser;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;

public class BasicTests extends ParserTestBase {

    @Test
    public void moduleDoc01() throws Exception {
        checkTreeFromFile();
    }

    @Test
    public void moduleDoc02() throws Exception {
        checkTreeFromFile();
    }

    @Test
    public void moduleDoc03() throws Exception {
        // testing new lines after the module doc
        checkTreeFromFile();
    }

    @Test
    public void moduleDoc04() throws Exception {
        checkTreeFromFile();
    }

    @Test
    public void moduleWithLincense() throws Exception {
        checkTreeResult(
                        "# Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.\n" +
                                        "# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                                        "\"\"\"MODULE A DOC\"\"\"\n" +
                                        "print(\"module A\")");
    }

    @Test
    public void leadingIndent1() throws Exception {
        checkSyntaxErrorMessage(" 1", "IndentationError: unexpected indent");
    }

    @Test
    public void leadingIndent2() throws Exception {
        checkTreeResult(" # foo\npass");
    }

    @Test
    public void annAssign01() throws Exception {
        checkTreeResult("a: int = 1");
    }

    @Test
    public void assert01() throws Exception {
        checkTreeResult("assert True");
    }

    @Test
    public void assert02() throws Exception {
        checkScopeAndTree(
                        "def avg(marks):\n" +
                                        "    assert len(marks) != 0,\"List is empty.\"");
    }

    @Test
    public void assert03() throws Exception {
        checkTreeResult(
                        "def avg():\n" +
                                        "    assert len != 0, getMessage(len)");
    }

    @Test
    public void assert04() throws Exception {
        checkTreeResult("assert not hascased");
    }

    @Test
    public void inline01() throws Exception {
        FrameDescriptor fd = new FrameDescriptor(44);
        fd.addFrameSlot("a");
        fd.addFrameSlot("b");
        Frame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{2, 3, null}, fd);
        checkTreeResult("a + b", PythonParser.ParserMode.InlineEvaluation, frame);
    }

    @Test
    public void simpleExpression01() throws Exception {
        checkTreeResult("'ahoj'");
    }

    @Test
    public void simpleExpression02() throws Exception {
        checkTreeResult("'ahoj'; 2");
    }

    @Test
    public void simpleExpression03() throws Exception {
        checkTreeResult("'ahoj'; 2; 1.0");
    }

    @Test
    public void simpleExpression04() throws Exception {
        checkTreeResult("None");
    }

    @Test
    public void simpleExpression05() throws Exception {
        checkTreeResult("...");
    }

    @Test
    public void simpleExpression06() throws Exception {
        checkTreeResult("a[1]");
    }

    @Test
    public void simpleExpression07() throws Exception {
        checkTreeResult("");
    }

    @Test
    public void simpleExpression08() throws Exception {
        checkTreeResult("\"\\uD800\"", PythonParser.ParserMode.Eval);
    }

    @Test
    public void longString01() throws Exception {
        checkTreeResult("'''ahoj'''");
    }

    @Test
    public void longString02() throws Exception {
        checkTreeResult("'''\n" + "ahoj\n" + "hello\n" + "good bye\n" + "'''");
    }

    @Test
    public void binaryOp01() throws Exception {
        checkTreeResult("1 + 10");
    }

    @Test
    public void binaryOp02() throws Exception {
        checkTreeResult("'ahoj' + 10");
    }

    @Test
    public void binaryOp03() throws Exception {
        checkTreeResult("3 ** 2");
    }

    @Test
    public void binaryOp04() throws Exception {
        checkTreeResult("3 ** 2 ** 2");
    }

    @Test
    public void comparision01() throws Exception {
        checkTreeResult("3 < 10");
    }

    @Test
    public void comparision02() throws Exception {
        checkTreeResult("1 < '10' > True");
    }

    @Test
    public void comparision03() throws Exception {
        checkTreeResult("1 < '10' > True != 1.0");
    }

    @Test
    public void comparision04() throws Exception {
        checkScopeAndTree("x < y() <= z");
    }

    @Test
    public void comparision05() throws Exception {
        checkScopeAndTree("x() < y() <= z()");
    }

    @Test
    public void comparision06() throws Exception {
        checkScopeAndTree("x() < y() < y() <= z()");
    }

    @Test
    public void if01() throws Exception {
        checkTreeResult(
                        "if False: \n" + "  10");
    }

    @Test
    public void if02() throws Exception {
        checkTreeResult(
                        "if False: \n" + "  10\n" + "else :\n" + "  a");
    }

    @Test
    public void if03() throws Exception {
        checkTreeResult("10 if False else 11");
    }

    @Test
    public void if04() throws Exception {
        checkTreeResult("predicate = func if func is not None else lambda a: a");
    }

    @Test
    public void if05() throws Exception {
        checkTreeResult("if not i: pass");
    }

    @Test
    public void elif01() throws Exception {
        checkTreeResult(
                        "var = 100\n" + "if var == 200:\n" + "  print (2)\n" + "elif var == 150:\n" + "  print (1.5)\n" + "elif var == 100:\n" + "  print (1)");
    }

    @Test
    public void call01() throws Exception {
        checkTreeResult("foo()");
    }

    @Test
    public void call02() throws Exception {
        checkTreeResult("foo(1)");
    }

    @Test
    public void call03() throws Exception {
        checkScopeAndTree("foo(arg = 1)");
    }

    @Test
    public void call04() throws Exception {
        checkSyntaxError("foo(1+arg = 1)");
    }

    @Test
    public void call05() throws Exception {
        checkSyntaxError("foo(arg + 1 = 1)");
    }

    @Test
    public void call06() throws Exception {
        checkTreeResult("foo(arg1 = 1, arg2 = 2)");
    }

    @Test
    public void call07() throws Exception {
        checkTreeResult("foo('ahoj', arg1 = 1, arg2 = 2)");
    }

    @Test
    public void call08() throws Exception {
        checkTreeResult("foo('ahoj', arg1 = 1, arg2 = 2)");
    }

    @Test
    public void call09() throws Exception {
        checkTreeResult("foo(*mylist)");
    }

    @Test
    public void call10() throws Exception {
        checkTreeResult("foo(*mylist1, *mylist2)");
    }

    @Test
    public void call11() throws Exception {
        checkTreeResult("foo(**mydict)");
    }

    @Test
    public void call12() throws Exception {
        checkTreeResult("foo(**mydict1, **mydict2)");
    }

    @Test
    public void call13() throws Exception {
        checkSyntaxError("foo(**mydict1, *mylist)");
    }

    @Test
    public void call14() throws Exception {
        checkSyntaxError("foo(**mydict1, 1)");
    }

    @Test
    public void call15() throws Exception {
        checkSyntaxError("foo(arg1=1, 1)");
    }

    @Test
    public void call16() throws Exception {
        checkTreeResult("a.b.c.foo()");
    }

    @Test
    public void call17() throws Exception {
        checkScopeAndTree("def fn(): foo(arg = 1)");
    }

    @Test
    public void call18() throws Exception {
        checkScopeAndTree("def fn(arg = [1,2]): foo(arg = [1])");
    }

    @Test
    public void call19() throws Exception {
        checkScopeAndTree("def fn(): \"in\".format(name=\"Baf\")");
    }

    @Test
    public void call20() throws Exception {
        checkScopeAndTree("def fn(name): \"in\".format(name=name)");
    }

    @Test
    public void del01() throws Exception {
        checkTreeResult("del x");
    }

    @Test
    public void del02() throws Exception {
        checkTreeResult("del x, y, z");
    }

    @Test
    public void del03() throws Exception {
        checkTreeResult("del (x, y, z)");
    }

    @Test
    public void for01() throws Exception {
        checkTreeResult("for i in 'ahoj':\n" + "  pass");
    }

    @Test
    public void for02() throws Exception {
        checkTreeResult("for i in range(210):\n" + "  print(i)");
    }

    @Test
    public void for03() throws Exception {
        checkScopeAndTree(
                        "for x in xrange(3):\n" +
                                        "  if x == 1:\n" +
                                        "    break\n");
    }

    @Test
    public void for04() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  for x in xrange(3):\n" +
                                        "    if x == 1:\n" +
                                        "      break\n");
    }

    @Test
    public void for05() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void for06() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void for07() throws Exception {
        // TODO: the line 25 in for07.tast should have SourceSection None according the old parser
        // result.
        checkTreeResult(
                        "for x in range(10):\n" +
                                        "    if x % 2 == 0:\n" +
                                        "        continue\n" +
                                        "    print(x)");
    }

    @Test
    public void for08() throws Exception {
        checkTreeResult(
                        "for x in range(10):\n" +
                                        "    if call01() == 0:\n" +
                                        "        continue\n" +
                                        "    print(x)");
    }

    @Test
    public void for09() throws Exception {
        checkTreeResult(
                        "for i in range(1, 10):\n" +
                                        "    if call01(i)==0:\n" +
                                        "        break\n" +
                                        "    print(i)\n" +
                                        "else:\n" +
                                        "    print(\"test\")");
    }

    @Test
    public void for10() throws Exception {
        checkScopeAndTree(
                        "for num in range(10,20):\n" +
                                        "   for i in range(2,num):\n" +
                                        "      if True:\n" +
                                        "         break\n" +
                                        "   else:\n" +
                                        "      pass");
    }

    @Test
    public void for11() throws Exception {
        checkTreeResult("for i, b in (): pass");
    }

    @Test
    public void for12() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  for a, b in ((1,2), (3,4)):\n" +
                                        "    print(a)");
    }

    @Test
    public void for13() throws Exception {
        checkScopeAndTree(
                        "def format(self):\n" +
                                        "    for frame in self:\n" +
                                        "        count += 1\n" +
                                        "        if count >= 3:\n" +
                                        "            continue\n" +
                                        "        if count == 4:\n" +
                                        "            for name, value in a:\n" +
                                        "                count = 1");
    }

    @Test
    public void for14() throws Exception {
        checkScopeAndTree(
                        "def merge(sequences):\n" +
                                        "    for s1 in sequences:\n" +
                                        "        for s2 in sequences:\n" +
                                        "            if candidate in s2[1:]:\n" +
                                        "                break\n" +
                                        "        else:\n" +
                                        "            break");
    }

    @Test
    public void for15() throws Exception {
        checkScopeAndTree(
                        "def formatyear():\n" +
                                        "        for (i, row) in something:\n" +
                                        "            pass\n" +
                                        "        return 10");
    }

    @Test
    public void for16() throws Exception {
        checkSyntaxError(
                        "for i in range(10):\n" +
                                        "    def foo():\n" +
                                        "        continue\n");
    }

    @Test
    public void for17() throws Exception {
        checkSyntaxError(
                        "for i in range(10):\n" +
                                        "    class foo:\n" +
                                        "        break\n");
    }

    @Test
    public void global01() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void global02() throws Exception {
        checkTreeResult("global x");
    }

    @Test
    public void nonlocal01() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void or01() throws Exception {
        checkTreeResult("a or b");
    }

    @Test
    public void and01() throws Exception {
        checkTreeResult("a and b");
    }

    @Test
    public void not01() throws Exception {
        checkTreeResult("not a");
    }

    @Test
    public void nonlocal02() throws Exception {
        checkSyntaxError("nonlocal x");
    }

    @Test
    public void raise01() throws Exception {
        checkTreeResult("raise");
    }

    @Test
    public void raise02() throws Exception {
        checkTreeResult("raise NameError");
    }

    @Test
    public void raise03() throws Exception {
        checkTreeResult("raise NameError('Pavel')");
    }

    @Test
    public void raise04() throws Exception {
        checkTreeResult("raise NameError('Pavel') from exc");
    }

    @Test
    public void try01() throws Exception {
        checkScopeAndTree(
                        "try:\n" +
                                        "  pass\n" +
                                        "except ValueError:\n" +
                                        "  pass");
    }

    @Test
    public void try02() throws Exception {
        checkScopeAndTree(
                        "try:\n" +
                                        "  pass\n" +
                                        "except ValueError as va:\n" +
                                        "  pass");
    }

    @Test
    public void try03() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  try:\n" +
                                        "    pass\n" +
                                        "  except ValueError as va:\n" +
                                        "    pass");
    }

    @Test
    public void try04() throws Exception {
        checkTreeResult(
                        "try:\n" +
                                        "  pass\n" +
                                        "except (RuntimeError, TypeError, NameError):\n" +
                                        "  pass");
    }

    @Test
    public void try05() throws Exception {
        checkTreeResult(
                        "try:\n" +
                                        "  pass\n" +
                                        "except:\n" +
                                        "  pass");
    }

    @Test
    public void try06() throws Exception {
        checkTreeResult(
                        "for cls in (B, C, D):\n" +
                                        "    try:\n" +
                                        "        pass\n" +
                                        "    except D:\n" +
                                        "        pass\n" +
                                        "    except C:\n" +
                                        "        pass\n" +
                                        "    except B:\n" +
                                        "        pass");
    }

    @Test
    public void try07() throws Exception {
        checkTreeResult(
                        "try:\n" +
                                        "    pass\n" +
                                        "except OSError as err:\n" +
                                        "    pass\n" +
                                        "except ValueError:\n" +
                                        "    pass\n" +
                                        "except:\n" +
                                        "    raise");
    }

    @Test
    public void try08() throws Exception {
        checkTreeResult(
                        "try:\n" +
                                        "    pass\n" +
                                        "except OSError:\n" +
                                        "    pass\n" +
                                        "else:\n" +
                                        "    pass");
    }

    @Test
    public void try09() throws Exception {
        checkTreeResult(
                        "try:\n" +
                                        "   raise KeyboardInterrupt\n" +
                                        "finally:\n" +
                                        "   print('Goodbye, world!')");
    }

    @Test
    public void try10() throws Exception {
        checkScopeAndTree(
                        "def divide(x, y):\n" +
                                        "    try:\n" +
                                        "        result = x / y\n" +
                                        "    except ZeroDivisionError:\n" +
                                        "        print(\"division by zero!\")\n" +
                                        "    else:\n" +
                                        "        print(\"result is\", result)\n" +
                                        "    finally:\n" +
                                        "        print(\"executing finally clause\")");
    }

    @Test
    public void try11() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "    try:\n" +
                                        "        pass\n" +
                                        "    except Exception as err:\n" +
                                        "        print(err)");
    }

    @Test
    public void tuple01() throws Exception {
        checkTreeResult("(1, 2, 3)");
    }

    @Test
    public void tuple02() throws Exception {
        checkTreeResult("(1, call01((1,2,)), 'ahoj')");
    }

    @Test
    public void tuple03() throws Exception {
        checkTreeResult("t = ()");
    }

    @Test
    public void tuple04() throws Exception {
        checkTreeResult("t = (2)");
    }

    @Test
    public void tuple05() throws Exception {
        checkTreeResult("t = (2,)");
    }

    @Test
    public void tuple06() throws Exception {
        checkTreeResult("t = ('strange,')");
    }

    @Test
    public void tuple07() throws Exception {
        checkTreeResult("1,2,3");
    }

    @Test
    public void tuple08() throws Exception {
        checkTreeResult("1,");
    }

    @Test
    public void tuple09() throws Exception {
        checkTreeResult("1, call1()");
    }

    @Test
    public void tuple10() throws Exception {
        checkTreeResult("t = 1, call1()");
    }

    @Test
    public void tuple11() throws Exception {
        checkTreeResult("a += 1,2,3");
    }

    @Test
    public void tuple12() throws Exception {
        checkTreeResult("a[1,3,4]");
    }

    @Test
    public void tuple13() throws Exception {
        checkTreeResult("b = (\n" +
                        "  (0x69, 0x131), # iı\n" +
                        ")");
    }

    @Test
    public void unary01() throws Exception {
        checkTreeResult("+u");
    }

    @Test
    public void unary02() throws Exception {
        checkTreeResult("-u");
    }

    @Test
    public void unary03() throws Exception {
        checkTreeResult("~u");
    }

    @Test
    public void getAttr01() throws Exception {
        // TODO: old parser doesn't generate source section for a
        checkTreeResult("a.b");
    }

    @Test
    public void getAttr02() throws Exception {
        // TODO: old parser doesn't generate source section for a()
        checkTreeResult("a().b");
    }

    @Test
    public void getAttr03() throws Exception {
        // TODO: old parser generates only source section for all expression
        checkTreeResult("a().b.c(x).d.f()");
    }

    @Test
    public void while01() throws Exception {
        checkTreeResult("while True:\n" + "  pass");
    }

    @Test
    public void while02() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    continue\n" + "  pass");
    }

    @Test
    public void while03() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    break\n" + "  pass");
    }

    @Test
    public void while04() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    break\n" + "  pass");
    }

    @Test
    public void while05() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    continue\n" + "  pass");
    }

    @Test
    public void while06() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    break\n" + "  pass\n" + "else:\n" + "  pass");
    }

    @Test
    public void while07() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    break\n" + "  pass\n" + "else:\n" + "  pass");
    }

    @Test
    public void while08() throws Exception {
        checkTreeResult("while True:\n" + "  if False:\n" + "    break\n" + "  if True:\n" + "    continue\n" + "  pass\n" + "else:\n" + "  print('done')");
    }

    @Test
    public void while09() throws Exception {
        checkTreeResult("while True:\n" + "  pass\n" + "else:\n" + "  pass");
    }

    @Test
    public void while10() throws Exception {
        checkTreeResult("while tb is not None: pass");
    }

    @Test
    public void while11() throws Exception {
        checkTreeResult(
                        "iters = 0\n" +
                                        "while iters < 40:\n" +
                                        "    while iters < 10:\n" +
                                        "        if False:\n" +
                                        "            break\n" +
                                        "        iters += 1\n" +
                                        "    else:\n" +
                                        "        iters += 1\n" +
                                        "        break");
    }

    @Test
    public void while12() throws Exception {
        checkSyntaxError(
                        "while False:\n" +
                                        "    def foo():\n" +
                                        "        break");
    }

    @Test
    public void with01() throws Exception {
        checkTreeResult(
                        "with A() as a:\n" +
                                        "  pass");
    }

    @Test
    public void with02() throws Exception {
        checkTreeResult(
                        "with A() as a, B() as b:\n" +
                                        "  pass");
    }

    @Test
    public void with03() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  with A() as a:\n" +
                                        "    pass");
    }

    @Test
    public void with04() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  with A() as a, B() as b:\n" +
                                        "    pass");
    }

    @Test
    public void with05() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  with open('x'):\n" +
                                        "    pass");
    }

    @Test
    public void with06() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "  with A() as a:\n" +
                                        "    with B() as b:\n" +
                                        "      pass");
    }

    @Test
    public void spaceEnd() throws Exception {
        checkTreeResult("x=5 ");
    }

    @Test
    public void isIdentifier() throws Exception {
        PythonCore core = context.getCore();
        PythonParser parser = core.getParser();
        Assert.assertTrue(parser.isIdentifier(core, "hello"));
        Assert.assertTrue(parser.isIdentifier(core, "_"));
        Assert.assertTrue(parser.isIdentifier(core, "b0"));
        Assert.assertTrue(parser.isIdentifier(core, "bc"));
        Assert.assertTrue(parser.isIdentifier(core, "b_"));
        Assert.assertTrue(parser.isIdentifier(core, "µ"));

        Assert.assertFalse(parser.isIdentifier(core, " hello"));
        Assert.assertFalse(parser.isIdentifier(core, "hello "));
        Assert.assertFalse(parser.isIdentifier(core, "hel lo"));
        Assert.assertFalse(parser.isIdentifier(core, "hel?o"));
        Assert.assertFalse(parser.isIdentifier(core, "hel!o"));

        Assert.assertFalse(parser.isIdentifier(core, " "));
        Assert.assertFalse(parser.isIdentifier(core, "["));
        Assert.assertFalse(parser.isIdentifier(core, "©"));
        Assert.assertFalse(parser.isIdentifier(core, "0"));
    }

    private void checkScopeAndTree() throws Exception {
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }

    private void checkTreeFromFile() throws Exception {
        File testFile = getTestFileFromTestAndTestMethod();
        checkTreeFromFile(testFile, true);
    }

}
