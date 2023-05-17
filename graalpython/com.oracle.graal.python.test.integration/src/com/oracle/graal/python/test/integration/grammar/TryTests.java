/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertLastLineErrorContains;
import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class TryTests {

    @Test
    public void tryNoFinallyZeroDivide() {
        String source = "try:\n" + //
                        "    result = 1 / 0\n" + //
                        "except ZeroDivisionError:\n" + //
                        "    print(\"division by zero!\")\n" + //
                        "else:\n" + //
                        "    print(\"result is \", result)\n";

        assertPrints("division by zero!\n", source);
    }

    @Test
    public void tryDivide() {
        String source = "try:\n" + //
                        "    result = 1 / 1\n" + //
                        "except ZeroDivisionError:\n" + //
                        "    print(\"division by zero!\")\n" + //
                        "else:\n" + //
                        "    print(\"result is \", result)\n" + //
                        "finally:\n" + //
                        "    print(\"executing finally clause\")\n";

        assertPrints("result is  1.0\n" + "executing finally clause\n", source);
    }

    @Test
    public void tryZeroDivide() {
        String source = "try:\n" + //
                        "    result = 1 / 0\n" + //
                        "except ZeroDivisionError:\n" + //
                        "    print(\"division by zero!\")\n" + //
                        "else:\n" + //
                        "    print(\"result is \", result)\n" + //
                        "finally:\n" + //
                        "    print(\"executing finally clause\")\n";

        assertPrints("division by zero!\n" + "executing finally clause\n", source);
    }

    @Test
    public void tryZeroDivideInsideFunction() {
        String source = "def foo():\n" + //
                        "    result = 1 / 0\n" + //
                        "\n" + //
                        "try:\n" + //
                        "    foo()\n" + //
                        "except ZeroDivisionError:\n" + //
                        "    print(\"division by zero!\")\n" + //
                        "else:\n" + //
                        "    print(\"result is \", result)\n" + //
                        "finally:\n" + //
                        "    print(\"executing finally clause\")\n";

        assertPrints("division by zero!\n" + "executing finally clause\n", source);
    }

    @Test
    public void raiseWithoutArg() {
        String source = "def divide(x, y):\n" + //
                        "    try:\n" + //
                        "        result = x / y\n" + //
                        "        raise KeyboardInterrupt\n" + //
                        "    except KeyboardInterrupt as err:\n" + //
                        "        try:\n" + //
                        "            result = x / (y+1)\n" + //
                        "            foo()\n" + //
                        "        except KeyboardInterrupt as exp:\n" + //
                        "            print(\"last KeyboardInterrupt!\",exp)\n" + //
                        "        else:\n" + //
                        "            print(\"exception result is \", result)\n" + //
                        "        finally:\n" + //
                        "            print(\"executing finally clause raised without argument\")\n" + //
                        "    except ZeroDivisionError as z:\n" + //
                        "        print(\"ZeroDivisionError!\", z)\n" + //
                        "    else:\n" + //
                        "        print(\"result is \", result)\n" + //
                        "    finally:\n" + //
                        "       print(\"executing finally clause\")\n" + //
                        "\n" + //
                        "def foo():\n" + //
                        "    raise\n" + //
                        "\n" + //
                        "divide(1,1)\n";

        assertPrints("last KeyboardInterrupt! \n" + "executing finally clause raised without argument\n" + "executing finally clause\n", source);
    }

    @Test
    public void exceptWithoutArg() {
        String source = "def foo():\n" + //
                        "    result = 1 / 0\n" + //
                        "\n" + //
                        "try:\n" + //
                        "    foo()\n" + //
                        "except:\n" + //
                        "    print(\"division by zero!\")\n" + //
                        "else:\n" + //
                        "    print(\"result is \", result)\n" + //
                        "finally:\n" + //
                        "    print(\"executing finally clause\")\n";

        assertPrints("division by zero!\n" + "executing finally clause\n", source);
    }

    @Test
    public void exceptWithoutArg2() {
        String source = "def foo():\n" + //
                        "    raise KeyboardInterrupt\n" + //

                        "def bar():\n" + //
                        "    try:\n" + //
                        "        foo()\n" + //
                        "    except AssertionError:\n" + //
                        "        print(\"EXCEPT ASSERTION ERROR\")\n" + //
                        "    except:\n" + //
                        "        print(\"EXCEPT WITHOUT AN EXPRESSION\")\n" + //

                        "bar()\n";

        assertPrints("EXCEPT WITHOUT AN EXPRESSION\n", source);
    }

    @Test
    public void raiseAssertion() {
        String source = "\n" + //
                        "def foo():\n" + //
                        "    raise AssertionError(\"Problem\")\n" + //

                        "def bar():\n" + //
                        "   try:\n" + //
                        "        foo()\n" + //
                        "   except AssertionError:\n" + //
                        "        print(\"EXCEPTED ASSERTION ERROR\")\n" + //

                        "bar()\n" + //
                        "bar()\n" + //
                        "print(\"CONTINUING\")\n";

        assertPrints("EXCEPTED ASSERTION ERROR\nEXCEPTED ASSERTION ERROR\nCONTINUING\n", source);
    }

    @Test
    public void tupleExceptTypes() {
        String source = "\n" + //
                        "try: 1/0\n" + //
                        "except (EOFError, TypeError, ZeroDivisionError): pass\n" + //
                        "try: 1/0\n" + //
                        "except (EOFError, TypeError, ZeroDivisionError) as msg: pass\n" + //
                        "try: pass\n" + //
                        "finally: pass\n";

        assertPrints("", source);
    }

    @Test
    public void tryGrammarError() {
        assertLastLineErrorContains("SyntaxError", "try: 1+1\n");
        assertLastLineErrorContains("SyntaxError", "try:\n 1+1\n");
    }

    @Test
    public void tryZeroArgLen() {
        assertPrints("0\n", "try:\n" +
                        "   assert False\n" +
                        "except AssertionError as e:\n" +
                        "   print(len(e.args))\n");

        assertPrints("1\n", "try:\n" +
                        "   assert False, \"\"\n" +
                        "except AssertionError as e:\n" +
                        "   print(len(e.args))\n");
    }

    @Test
    public void testExceptionState1() {
        String source = "import sys\n" +
                        "try:\n" +
                        "    raise NameError\n" +
                        "except BaseException:\n" +
                        "    print(repr(sys.exc_info()[1]))\n" +
                        "print(repr(sys.exc_info()[1]))\n";
        assertPrints("NameError()\nNone\n", source);
    }

    @Test
    public void testExceptionState2() {
        String source = "import sys\n" +
                        "try:\n" +
                        "    try:\n" +
                        "        raise NameError\n" +
                        "    finally:\n" +
                        "        print(repr(sys.exc_info()[1]))\n" +
                        "except:\n" +
                        "    pass\n" +
                        "print(repr(sys.exc_info()[1]))\n";
        assertPrints("NameError()\nNone\n", source);
    }

    @Test
    public void testExceptionState3() {
        String source = "import sys\n" +
                        "try:\n" +
                        "    raise NameError\n" +
                        "except BaseException:\n" +
                        "    print(repr(sys.exc_info()[1]))\n" +
                        "finally:\n" +
                        "    print(repr(sys.exc_info()[1]))\n" +
                        "print(repr(sys.exc_info()[1]))\n";
        assertPrints("NameError()\nNone\nNone\n", source);
    }

    @Test
    public void testExceptionState4() {
        String source = "import sys\n" +
                        "try:\n" +
                        "    try:\n" +
                        "        raise NameError\n" +
                        "    except BaseException:\n" +
                        "        raise TypeError\n" +
                        "    finally:\n" +
                        "        print(repr(sys.exc_info()[1]))\n" +
                        "except:\n" +
                        "    pass\n" +
                        "print(repr(sys.exc_info()[1]))\n";
        assertPrints("TypeError()\nNone\n", source);
    }

    @Test
    public void testExceptionState5() {
        String source = "import sys\n" +
                        "try:\n" +
                        "    try:\n" +
                        "        pass\n" +
                        "    except BaseException:\n" +
                        "        pass\n" +
                        "    else:\n" +
                        "        raise TypeError\n" +
                        "    finally:\n" +
                        "        print(repr(sys.exc_info()[1]))\n" +
                        "except:\n" +
                        "    pass\n" +
                        "print(repr(sys.exc_info()[1]))\n";
        assertPrints("TypeError()\nNone\n", source);
    }

    @Test
    public void testExceptionState6() {
        String source = "import sys\n" +
                        "try:\n" +
                        "    raise NameError\n" +
                        "except BaseException:\n" +
                        "    pass\n" +
                        "finally:\n" +
                        "    print(repr(sys.exc_info()[1]))\n" +
                        "print(repr(sys.exc_info()[1]))\n";
        assertPrints("None\nNone\n", source);
    }

    @Test
    public void testNamedExceptionDeleted() {
        String source = "ex = 42\n" +
                        "try:\n" +
                        "    raise NameError\n" +
                        "except BaseException as ex:\n" +
                        "    pass\n" +
                        "try:\n" +
                        "    print(ex)\n" +
                        "    print(\"expected NameError\")\n" +
                        "except NameError:\n" +
                        "    print(\"hit NameError\")\n";
        assertPrints("hit NameError\n", source);
    }

    @Test
    public void testNamedExceptionNotDeleted() {
        String source = "ex = 42\n" +
                        "try:\n" +
                        "    print(\"nothing thrown\")\n" +
                        "except BaseException as ex:\n" +
                        "    pass\n" +
                        "try:\n" +
                        "    print(ex)\n" +
                        "except NameError:\n" +
                        "    print(\"hit unexpected NameError\")\n";
        assertPrints("nothing thrown\n42\n", source);
    }

    @Test
    public void testNamedExceptionDeletedByHandler() {
        String source = "ex = 42\n" +
                        "try:\n" +
                        "    raise NameError\n" +
                        "except BaseException as ex:\n" +
                        "    print(\"deleting exception\")\n" +
                        "    del ex\n" +
                        "try:\n" +
                        "    print(ex)\n" +
                        "    print(\"expected NameError\")\n" +
                        "except NameError:\n" +
                        "    print(\"hit NameError\")\n";
        assertPrints("deleting exception\nhit NameError\n", source);
    }
}
