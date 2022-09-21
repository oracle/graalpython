/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.test.PythonTests;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.source.Source;

/**
 * This test checks throwing IncompleteSourceException that is used in Python shell, to find out if
 * the shell should ask for an additional input.
 */
public class IncompleteCodeTests extends ParserTestBase {

    @Test
    public void testCheckSyntaxErrors() {
        // these are the codes, where the syntax error was not raised in interactive mode
        checkSyntaxErrorMessage("f(a+b=1)", "SyntaxError: keyword can't be an expression");
    }

    @Test
    public void testFunctionDef() throws Exception {
        checkSyntaxErrorMessage("def\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("def \n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("def test\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("def test:\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("def test(:\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("def test:\n", "SyntaxError: invalid syntax");
        checkIncompleteException("def test():\n");
        checkIncompleteException("def test():    \n");
        checkIncompleteException("def test(\n");
        checkIncompleteException("def test(   \n");
        checkSyntaxErrorMessage("def test(\n    \n\n)", "SyntaxError: invalid syntax");
        checkIncompleteException("def test(   \n\n):\n");
    }

    @Test
    public void testIf() throws Exception {
        checkSyntaxErrorMessage("if True\n", "SyntaxError: invalid syntax");
        checkIncompleteException("if True:\n");
        checkSyntaxErrorMessage("if True:\n\n", "IndentationError: expected an indented block");
        checkSyntaxErrorMessage("if True:\nelse\n", "IndentationError: expected an indented block");
        checkSyntaxErrorMessage("if True:\nelse:\n", "IndentationError: expected an indented block");
        checkSyntaxErrorMessage("if True:\n  else:\n", "SyntaxError: invalid syntax");
        checkNoError("if True:\n  print(1)\n");
        checkNoError("if True:\n  \n    \n   print(1)\n");
        checkSyntaxErrorMessage("if True:\n  \n    \n   print(1)\n    print(2)\n", "IndentationError: unexpected indent");
        checkSyntaxErrorMessage("if True:\n   print(1)\n  print(2)\n", "IndentationError: unindent does not match any outer indentation level");

    }

    @Test
    public void testTripleQuotedString() throws Exception {
        checkIncompleteException("'''\n");
        checkIncompleteException("'''\n\n");
        checkIncompleteException("'''\n\n\n");
        checkIncompleteException("'''ahoj\n");
        checkIncompleteException("'''ahoj\ncau\n");
        checkIncompleteException("'''ahoj\ncau\nhi\n");
        checkIncompleteException("'''ahoj\ncau\nhi\n\n");
        checkIncompleteException("'''ahoj\ncau\nhi\n\n\n");
        checkIncompleteException("'''\n''\n");

    }

    @Test
    public void testTryBlock() throws Exception {
        checkSyntaxErrorMessage("try\n", "SyntaxError: invalid syntax");
        checkIncompleteException("try    :\n");
        checkIncompleteException("try    :     \n");
        checkIncompleteException("try    :#should be incomplete\n");
        checkSyntaxErrorMessage("try:\n  print(1)\n    print(2)\n", "IndentationError: unexpected indent");
        checkSyntaxErrorMessage("try:\n    print(1)\n  print(2)\n", "IndentationError: unindent does not match any outer indentation level");
        checkSyntaxErrorMessage("try:\n  print(1)\nprint(2)\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("try:\nexcept\n", "IndentationError: expected an indented block");
        checkSyntaxErrorMessage("try:\n  pass\nexcept\n", "SyntaxError: invalid syntax");
        checkIncompleteException("try:\n  pass\nexcept:\n");
        checkSyntaxErrorMessage("try:\n  pass\nexcept ValueError\n", "SyntaxError: invalid syntax");
        checkIncompleteException("try:\n  pass\nexcept ValueError:\n");
        checkSyntaxErrorMessage("try:\n  pass\nexcept ValueError:\nfinally", "IndentationError: expected an indented block");
        checkSyntaxErrorMessage("try:\n  pass\nexcept:\n  print(1)\n    print(2)\n", "IndentationError: unexpected indent");
        checkSyntaxErrorMessage("try:\n  pass\nexcept:\n    print(1)\n  print(2)\n", "IndentationError: unindent does not match any outer indentation level");
        checkSyntaxErrorMessage("try:\n  pass\nexcept:\nprint(1)\n", "IndentationError: expected an indented block");
        checkSyntaxErrorMessage("try:\nfinally\n", "IndentationError: expected an indented block");
        checkIncompleteException("try:\n  pass\nfinally:\n");
        checkSyntaxErrorMessage("try:\n  pass\nfinally:\n  print(1)\n    print(2)\n", "IndentationError: unexpected indent");
        checkSyntaxErrorMessage("try:\n  pass\nfinally:\n    print(1)\n  print(2)\n", "IndentationError: unindent does not match any outer indentation level");
        checkSyntaxErrorMessage("try:\n  pass\nfinally:\nprint(1)\n", "IndentationError: expected an indented block");
    }

    @Test
    public void testWhile() throws Exception {
        checkSyntaxErrorMessage("while\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("while:\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("while  : \n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("while i < 12\n", "SyntaxError: invalid syntax");
        checkIncompleteException("while i < 12:\n");
        checkIncompleteException("while i < 12      :\n");
        checkIncompleteException("while i < 12 :     \n");
        checkSyntaxErrorMessage("while i < 12:\n  print(1)\n    print(2)\n", "IndentationError: unexpected indent");
        checkSyntaxErrorMessage("while i < 12:\n    print(1)\n  print(2)\n", "IndentationError: unindent does not match any outer indentation level");
    }

    @Test
    public void testClass() throws Exception {
        checkSyntaxErrorMessage("class\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("class A\n", "SyntaxError: invalid syntax");
        checkSyntaxErrorMessage("class A()\n", "SyntaxError: invalid syntax");
        checkIncompleteException("class A:\n");
        checkSyntaxErrorMessage("class A:\npass\n", "IndentationError: expected an indented block");
        checkNoError("class A:\n  pass\n");
        checkIncompleteException("class A:\n  def a(self):\n");
    }

    private static Source createInteractiveSource(String code) {
        return Source.newBuilder(PythonLanguage.ID, code, "<stdin>").interactive(true).build();
    }

    public void checkNoError(String source) {
        try {
            parse(createInteractiveSource(source), PythonParser.ParserMode.Statement);
        } catch (PException e) {
            Assert.assertTrue("Source:'" + source + "' should not throw any error, but '" + PythonTests.getExceptionMessage(e) + "' was thrown.", false);
        }
    }

    @Override
    public void checkSyntaxErrorMessage(String source, String expectedMessage) {
        try {
            parse(createInteractiveSource(source), PythonParser.ParserMode.Statement);
        } catch (PException e) {
            String exceptionMessage = PythonTests.getExceptionMessage(e);
            Assert.assertEquals("Source:'" + source + "' should throw '" + expectedMessage + "', but '" + exceptionMessage + "' was thrown.", expectedMessage, exceptionMessage);
            return;
        } catch (Exception e) {
            Assert.fail("Unexpected exception " + e);
        }

        Assert.assertTrue("Source:'" + source + "' should throw '" + expectedMessage + "', but was not thrown.", false);
    }

    public void checkIncompleteException(String source) throws Exception {
        try {
            parse(createInteractiveSource(source), PythonParser.ParserMode.Statement);
        } catch (Exception e) {
            if (!(e instanceof PythonParser.PIncompleteSourceException)) {
                Assert.assertTrue("Source:'" + source + "' should throw PIncompleteSourceException, but this exception was thrown: '" + e.getMessage() + "'", false);
            }
            return;
        }

        Assert.assertTrue("Source:'" + source + "' should throw PIncompleteSourceException, but no error was thrown.", false);
    }

}
