/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class ExpressionsFromCPTests extends ParserTestBase {

    @Test
    public void expression01() throws Exception {
        checkTreeResult("def f(): pass");
    }
    
    @Test
    public void expression02() throws Exception {
        checkTreeResult("foo(1)");
    }
    
    @Test
    public void expression03() throws Exception {
        checkTreeResult("[1, 2, 3]");
    }
    
    @Test
    public void expression04() throws Exception {
        checkTreeResult("...");
    }

    @Test
    public void expression05() throws Exception {
        checkTreeResult("a[...]");
    }

    @Test
    public void generator01() throws Exception {
        checkScopeAndTree("[x**3 for x in range(20)]");
    }
    
//        self.check_expr("[x**3 for x in range(20) if x % 3]")
//        self.check_expr("[x**3 for x in range(20) if x % 2 if x % 3]")
    @Test
    public void generator04() throws Exception {
        checkScopeAndTree("list(x**3 for x in range(20))");
    }

//        self.check_expr("list(x**3 for x in range(20) if x % 3)")
//        self.check_expr("list(x**3 for x in range(20) if x % 2 if x % 3)")
    @Test
    public void generator07() throws Exception {
        checkScopeAndTree("(x for x in range(10))");
    }
    
    @Test
    public void generator08() throws Exception {
        checkScopeAndTree("foo(x for x in range(10))");
    }

    @Test
    public void fnCall01() throws Exception {
        checkTreeResult("foo(*args)");
    }

    @Test
    public void fnCall02() throws Exception {
        checkTreeResult("foo(*args, **kw)");
    }

    @Test
    public void fnCall03() throws Exception {
        checkTreeResult("foo(**kw)");
    }

    @Test
    public void fnCall04() throws Exception {
        checkTreeResult("foo(key=value)");
    }
    
    @Test
    public void fnCall05() throws Exception {
        checkTreeResult("foo(key=value, *args)");
    }
    
    @Test
    public void fnCall06() throws Exception {
        checkTreeResult("foo(key=value, *args, **kw)");
    }
    
    @Test
    public void fnCall07() throws Exception {
        checkTreeResult("foo(key=value, **kw)");
    }
    
    @Test
    public void fnCall08() throws Exception {
        checkTreeResult("foo(a, b, c, *args)");
    }
    
    @Test
    public void fnCall09() throws Exception {
        checkTreeResult("foo(a, b, c, *args, **kw)");
    }
    
    @Test
    public void fnCall10() throws Exception {
        checkTreeResult("foo(a, b, c, **kw)");
    }
    
    @Test
    public void fnCall11() throws Exception {
        checkTreeResult("foo(a, *args, keyword=23)");
    }
    
    @Test
    public void binOp01() throws Exception {
        checkTreeResult("foo + bar");
    }

    @Test
    public void binOp02() throws Exception {
        checkTreeResult("foo - bar");
    }

    @Test
    public void binOp03() throws Exception {
        checkTreeResult("foo * bar");
    }

    @Test
    public void binOp04() throws Exception {
        checkTreeResult("foo / bar");
    }

    @Test
    public void binOp05() throws Exception {
        checkTreeResult("foo // bar");
    }

    @Test
    public void lambda01() throws Exception {
        checkScopeAndTree("lambda: 0");
    }
    
    @Test
    public void lambda02() throws Exception {
        checkScopeAndTree("lambda x: 0");
    }
    
    @Test
    public void lambda03() throws Exception {
        checkScopeAndTree("lambda *y: 0");
    }
    
    @Test
    public void lambda04() throws Exception {
        checkScopeAndTree("lambda *y, **z: 0");
    }

    @Test
    public void lambda05() throws Exception {
        checkScopeAndTree("lambda **z: 0");
    }

    @Test
    public void lambda06() throws Exception {
        checkScopeAndTree("lambda x, y: 0");
    }

    @Test
    public void lambda07() throws Exception {
        checkScopeAndTree("lambda foo=bar: 0");
    }

    @Test
    public void lambda08() throws Exception {
        checkScopeAndTree("lambda foo=bar, spaz=nifty+spit: 0");
    }

    @Test
    public void lambda09() throws Exception {
        checkScopeAndTree("lambda foo=bar, **z: 0");
    }

    @Test
    public void lambda10() throws Exception {
        checkScopeAndTree("lambda foo=bar, blaz=blat+2, **z: 0");
    }

    @Test
    public void lambda11() throws Exception {
        checkScopeAndTree("lambda foo=bar, blaz=blat+2, *y, **z: 0");
    }

    @Test
    public void lambda12() throws Exception {
        checkScopeAndTree("lambda x, *y, **z: 0");
    }
}
