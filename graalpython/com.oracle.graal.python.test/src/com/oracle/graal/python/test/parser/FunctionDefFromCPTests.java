/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class FunctionDefFromCPTests extends ParserTestBase {
    
    @Test
    public void functionDefCPT01() throws Exception {
        checkScopeAndTree("def f(): pass");
    }
    
    @Test
    public void functionDefCPT02() throws Exception {
        checkScopeAndTree("def f(*args): pass");
    }
    
    @Test
    public void functionDefCPT03() throws Exception {
        checkScopeAndTree("def f(*args, **kw): pass");
    }
    
    @Test
    public void functionDefCPT04() throws Exception {
        checkScopeAndTree("def f(**kw): pass");
    }
    
    @Test
    public void functionDefCPT05() throws Exception {
        checkScopeAndTree("def f(foo=bar): pass");
    }
    
    @Test
    public void functionDefCPT06() throws Exception {
        checkScopeAndTree("def f(foo=bar, *args): pass");
    }
    
    @Test
    public void functionDefCPT07() throws Exception {
        checkScopeAndTree("def f(foo=bar, *args, **kw): pass");
    }
    
    @Test
    public void functionDefCPT08() throws Exception {
        checkScopeAndTree("def f(foo=bar, **kw): pass");
    }
    
    @Test
    public void functionDefCPT09() throws Exception {
        checkScopeAndTree("def f(a, b): pass");
    }
    
    @Test
    public void functionDefCPT10() throws Exception {
        checkScopeAndTree("def f(a, b, *args): pass");
    }
    
    @Test
    public void functionDefCPT11() throws Exception {
        checkScopeAndTree("def f(a, b, *args, **kw): pass");
    }
    
    @Test
    public void functionDefCPT12() throws Exception {
        checkScopeAndTree("def f(a, b, **kw): pass");
    }
    
    @Test
    public void functionDefCPT13() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar): pass");
    }
    
    @Test
    public void functionDefCPT14() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, *args, **kw): pass");
    }
    
    @Test
    public void functionDefCPT15() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, **kw): pass");
    }
    
    // keyword-only arguments
    @Test
    public void functionDefCPT16() throws Exception {
        checkScopeAndTree("def f(*, a): pass");
    }

    @Test
    public void functionDefCPT17() throws Exception {
        checkScopeAndTree("def f(*, a = 5): pass");
    }

    @Test
    public void functionDefCPT18() throws Exception {
        checkScopeAndTree("def f(*, a = 5, b): pass");
    }

    @Test
    public void functionDefCPT19() throws Exception {
        checkScopeAndTree("def f(*, a, b = 5): pass");
    }

    @Test
    public void functionDefCPT20() throws Exception {
        checkScopeAndTree("def f(*, a, b = 5, **kwds): pass");
    }

    @Test
    public void functionDefCPT21() throws Exception {
        checkScopeAndTree("def f(*args, a): pass");
    }

    @Test
    public void functionDefCPT22() throws Exception {
        checkScopeAndTree("def f(*args, a = 5): pass");
    }

    @Test
    public void functionDefCPT23() throws Exception {
        checkScopeAndTree("def f(*args, a = 5, b): pass");
    }

    @Test
    public void functionDefCPT24() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, **kw): pass");
    }

    @Test
    public void functionDefCPT25() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, **kw): pass");
    }
 
    @Test
    public void decoratedFn01() throws Exception {
        checkScopeAndTree(
                "@staticmethod\n"  +
                "def f(): pass");
    }
    
    @Test
    public void decoratedFn02() throws Exception {
        checkScopeAndTree(
                "@staticmethod\n" +
                "@funcattrs(x, y)\n" +
                "def f(): pass");
    }
    
    @Test
    public void decoratedFn03() throws Exception {
        checkScopeAndTree(
                "@funcattrs()\n" +
                "def f(): pass");
    }
}
