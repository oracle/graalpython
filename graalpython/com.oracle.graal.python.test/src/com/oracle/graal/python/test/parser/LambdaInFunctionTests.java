/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class LambdaInFunctionTests extends ParserTestBase {
    
     @Test
    public void lambda01() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda: 0");
    }
    
    @Test
    public void lambda02() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda x: 0");
    }
    
    @Test
    public void lambda03() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda *y: 0");
    }
    
    @Test
    public void lambda04() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda *y, **z: 0");
    }

    @Test
    public void lambda05() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda **z: 0");
    }

    @Test
    public void lambda06() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda x, y: 0");
    }

    @Test
    public void lambda07() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda foo=bar: 0");
    }

    @Test
    public void lambda08() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda foo=bar, spaz=nifty+spit: 0");
    }

    @Test
    public void lambda09() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda foo=bar, **z: 0");
    }

    @Test
    public void lambda10() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda foo=bar, blaz=blat+2, **z: 0");
    }

    @Test
    public void lambda11() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda foo=bar, blaz=blat+2, *y, **z: 0");
    }

    @Test
    public void lambda12() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  lambda x, *y, **z: 0");
    }
}
