/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class AssignmentTests extends ParserTestBase{
    @Test
    public void assignment01() throws Exception {
        checkTreeResult("a = 1");
    }
    
    @Test
    public void assignment02() throws Exception {
        checkTreeResult("a = b = 1");
    }
    
    @Test
    public void assignment03() throws Exception {
        checkTreeResult("a = 0\n"
                + "b = a\n"
                + "c = a + a + b");
    }
    
    @Test
    public void assignment04() throws Exception {
        checkTreeResult("a = b = c = d = e");
    }
    
    @Test
    public void assignment05() throws Exception {
        checkTreeResult("a, b, c = 1, 2, 3");
    }
    
    @Test
    public void assignment06() throws Exception {
        checkScopeAndTree("def fn():\n  a = b = c = d = e");
    }
    
    @Test
    public void assignment07() throws Exception {
        checkScopeAndTree("def fn():\n  a, b, c = 1, 2, 3");
    }
    
    @Test
    public void augassign01() throws Exception {
        checkTreeResult("a += b");
    }
    
    @Test
    public void augassign02() throws Exception {
        checkTreeResult("a -= b");
    }

    @Test
    public void augassign03() throws Exception {
        checkTreeResult("a *= b");
    }

    @Test
    public void augassign04() throws Exception {
        checkTreeResult("a /= b");
    }

    @Test
    public void augassign05() throws Exception {
        checkTreeResult("a //= b");
    }

    @Test
    public void augassign06() throws Exception {
        checkTreeResult("a %= b");
    }

    @Test
    public void augassign07() throws Exception {
        checkTreeResult("a &= b");
    }

    @Test
    public void augassign08() throws Exception {
        checkTreeResult("a |= b");
    }

    @Test
    public void augassign09() throws Exception {
        checkTreeResult("a ^= b");
    }

    @Test
    public void augassign10() throws Exception {
        checkTreeResult("a <<= b");
    }

    @Test
    public void augassign11() throws Exception {
        checkTreeResult("a >>= b");
    }   

    @Test
    public void augassign12() throws Exception {
        checkTreeResult("a **= b");
    }
}
