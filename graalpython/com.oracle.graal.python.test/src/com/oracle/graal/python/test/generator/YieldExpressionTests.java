/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.generator;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class YieldExpressionTests {
    @Test
    public void testYieldExprAnd1() {
        String source = "list((lambda: str(print('x')) and str((yield)) and str(print('y')))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprAnd2() {
        String source = "list((lambda: str(print('x')) and (yield) and str(print('y')))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprOr1() {
        String source = "list((lambda: print('x') or (yield) or print('y'))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprOr2() {
        String source = "list((lambda: print('x') or str((yield)) or print('y'))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprAdd() {
        String source = "list((lambda: str(print('x')) + str((yield)) + str(print('y')))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprSub() {
        String source = "list((lambda: int(print('x') or 5) - int((yield) or 5) - int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprMul() {
        String source = "list((lambda: int(print('x') or 5) * int((yield) or 5) * int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprFloordiv() {
        String source = "list((lambda: int(print('x') or 5) // int((yield) or 5) // int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprTruediv() {
        String source = "list((lambda: int(print('x') or 5) / int((yield) or 5) / int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprMod() {
        String source = "list((lambda: int(print('x') or 5) % int((yield) or 5) % int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprPow() {
        String source = "list((lambda: int(print('x') or 5) ** int((yield) or 5) ** int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprBitAnd() {
        String source = "list((lambda: int(print('x') or 5) & int((yield) or 5) & int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprBitOr() {
        String source = "list((lambda: int(print('x') or 5) | int((yield) or 5) | int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprBitXor() {
        String source = "list((lambda: int(print('x') or 5) ^ int((yield) or 5) ^ int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprBitLShift() {
        String source = "list((lambda: int(print('x') or 5) << int((yield) or 5) << int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprBitRShift() {
        String source = "list((lambda: int(print('x') or 5) >> int((yield) or 5) >> int(print('y') or 5))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprEq() {
        String source = "list((lambda: int(print('x') or 5) == int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprNe() {
        String source = "list((lambda: int(print('x') or 5) != int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprGt() {
        String source = "list((lambda: int(print('x') or 5) > int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprGe() {
        String source = "list((lambda: int(print('x') or 5) >= int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprLt() {
        String source = "list((lambda: int(print('x') or 5) < int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprLe() {
        String source = "list((lambda: int(print('x') or 5) <= int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprIs() {
        String source = "list((lambda: int(print('x') or 5) is int((yield) or 5))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprIn() {
        String source = "list((lambda: int(print('x') or 5) in [(yield) or 5])())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprTuple() {
        String source = "list((lambda: (print('x'), (yield), print('y')))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprList() {
        String source = "list((lambda: [print('x'), (yield), print('y')])())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprSet() {
        String source = "list((lambda: {print('x'), (yield), print('y')})())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    public void testYieldExprDict() {
        String source = "list((lambda: {'a': print('x'), 'b': (yield), 'c': print('y')})())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    @Ignore // TODO
    public void testYieldExprCall() {
        String source = "list((lambda: dict({'a': print('x')}, b=(yield), c=print('y'), d=(yield)))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    @Ignore // TODO
    public void testYieldExprDef() {
        String source = "def gen():\n" +
                        "  def inner(a=print('x'), b=(yield), c=print('y')):\n" +
                        "    pass\n" +
                        "list(gen())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    @Ignore // TODO
    public void testYieldExprSlice() {
        String source = "list((lambda: [][print('x') or 1:(yield) or 1:print('y') or 1])())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    @Ignore // TODO
    public void testYieldExprTernaryIf1() {
        String source = "list((lambda: (yield) if print('x') else print('y'))())";
        PythonTests.assertPrints("x\ny\n", source);
    }

    @Test
    @Ignore // TODO
    public void testYieldExprTernaryIf2() {
        String source = "list((lambda: print('y') if print('x') else (yield))())";
        PythonTests.assertPrints("x\n", source);
    }

    @Test
    public void testYieldExprWith() {
        String source = "class cm:\n" +
                        "  def __init__(self):\n" +
                        "    print('init')\n" +
                        "  def __enter__(self, *args):\n" +
                        "    print('enter')\n" +
                        "  def __exit__(self, *args):\n" +
                        "    print('exit')\n" +
                        "def gen():\n" +
                        "  with cm() as x, ((yield) or cm()) as y:\n" +
                        "    pass\n" +
                        "list(gen())";
        PythonTests.assertPrints("init\nenter\ninit\nenter\nexit\nexit\n", source);
    }

    @Test
    @Ignore // TODO
    public void testYieldExprFString() {
        String source = "list((lambda: f\"{print('x')}{(yield)}{print('y')}\")())";
        PythonTests.assertPrints("x\ny\n", source);
    }
}
