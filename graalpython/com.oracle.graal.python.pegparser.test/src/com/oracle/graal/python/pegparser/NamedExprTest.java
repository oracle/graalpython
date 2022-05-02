/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import org.junit.Test;

public class NamedExprTest extends ParserTestBase {

    @Test
    public void testAssignment01() throws Exception {
        checkTreeResult("(a := 10)");
    }

    @Test
    public void testAssignment02() throws Exception {
        checkTreeResult("a = 20\n(a := a)");
    }

    @Test
    public void testAssignment03() throws Exception {
        checkTreeResult("(total := 1 + 2)");
    }

    @Test
    public void testAssignment04() throws Exception {
        checkTreeResult("(info := (1, 2, 3))");
    }

    @Test
    public void testAssignment05() throws Exception {
        checkTreeResult("(x := 1, 2)");
    }

    @Test
    public void testAssignment06() throws Exception {
        checkTreeResult("(z := (y := (x := 0)))");
    }

    @Test
    public void testAssignment07() throws Exception {
        checkTreeResult("(loc := (1, 2))");
    }

    @Test
    public void testAssignment08() throws Exception {
        checkTreeResult("if spam := \"eggs\": pass\n");
    }

    @Test
    public void testAssignment09() throws Exception {
        checkTreeResult("if True and (spam := True): pass");
    }

    @Test
    public void testAssignment10() throws Exception {
        checkTreeResult("if (match := 10) == 10: pass");
    }

    @Test
    public void testAssignment11() throws Exception {
        checkTreeResult("res = [(x, y, x/y) for x in input_data if (y := spam(x)) > 0]");
    }

    @Test
    public void testAssignment12() throws Exception {
        checkTreeResult("res = [[y := spam(x), x/y] for x in range(1, 5)]");
    }

    @Test
    public void testAssignment13() throws Exception {
        checkTreeResult("length = len(lines := [1, 2])");
    }

    @Test
    public void testAssignment14() throws Exception {
        checkTreeResult(
                        "while a > (d := x // a**(n-1)):\n" +
                                        "   a = ((n-1)*a + d) // n");
    }

    @Test
    public void testAssignment15() throws Exception {
        checkTreeResult("while a := False: pass");
    }

    @Test
    public void testAssignment16() throws Exception {
        checkTreeResult("fib = {(c := a): (a := b) + (b := a + c) - b for __ in range(6)}");
    }

    @Test
    public void testAssignment17() throws Exception {
        checkTreeResult("element = a[b:=0]");
    }

    @Test
    public void testAssignment18() throws Exception {
        checkTreeResult("element = a[b:=0, c:=0]");
    }

}
