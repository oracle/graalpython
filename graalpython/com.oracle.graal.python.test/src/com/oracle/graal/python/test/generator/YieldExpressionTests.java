package com.oracle.graal.python.test.generator;

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
    public void testYieldExprCall() {
        String source = "list((lambda: dict({'a': print('x')}, b=(yield), c=print('y'), d=(yield)))())";
        PythonTests.assertPrints("x\ny\n", source);
    }
}
