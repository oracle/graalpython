/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class GeneratorAndCompForTests extends ParserTestBase {
    
    @Test
    public void generator01() throws Exception {
        checkScopeAndTree("(x*x for x in range(10))");
    }
    
    @Test
    public void list01() throws Exception {
        checkScopeAndTree("[x**y for x in range(20)]");
    }
    
    @Test
    public void argument01() throws Exception {
        checkScopeAndTree("foo(x+2 for x in range(10))");
    }
    
    @Test
    public void set01() throws Exception {
        checkScopeAndTree("{x**y for x in range(20)}");
    }
    
    @Test
    public void dict01() throws Exception {
        checkScopeAndTree("{x:x*x for x in range(20)}");
    }
    
    @Test
    public void dict02() throws Exception {
        checkScopeAndTree(
                "dict1 = {'a': 1, 'b': 2, 'c': 3, 'd': 4, 'e': 5}\n" +
                "double_dict1 = {k:v*2 for (k,v) in dict1.items()}"
        );
    }
    
//    @Test
//    public void generator02() throws Exception {
//        checkScopeAndTree(
//                "def fn():\n" + 
//                "  (x*x for x in range(10))");
//    }
    
//    @Test
//    public void generator03() throws Exception {
//        checkTreeResult("(x + c for x in range(10))");
//    }
//    
//    @Test
//    public void generator04() throws Exception {
//        checkTreeResult(
//                "def fn():\n" +
//                "  c = 10\n" +
//                "  (x + c for x in range(10))");
//    }
}
