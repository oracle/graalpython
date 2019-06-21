/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import java.io.File;
import org.junit.Test;

public class FuncDefTests extends ParserTestBase{
    
    @Test
    public void functionDoc01() throws Exception {
        checkScopeAndTree();
    }  
    
    @Test
    public void functionDef02() throws Exception {
        checkScopeAndTree("def foo(): \n"
                + "  return 10\n");
    }
    
    @Test
    public void functionDef03() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functionDef04() throws Exception {
        checkTreeResult("def foo(a, b): \n"
                + "  return a + b\n");
    }
    
    @Test
    public void functionDef05() throws Exception {
        checkTreeResult("def foo(par1 = 10): \n"
                + "  return par1");
    }
    
    @Test
    public void functionDef06() throws Exception {
        checkTreeResult("def foo(par1, par2 = 22): \n"
                + "  return par1 * par2");
    }
    
    @Test
    public void functionDef07() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functionDef08() throws Exception {
        checkSyntaxError("def foo8(par1='ahoj', par2): \n"
                + "  return par1 * par2");
    }
    
    @Test
    public void functionDef09() throws Exception {
        checkTreeResult("def foo(*args): \n"
                + "  pass");
    }
    
    @Test
    public void functionDef10() throws Exception {
        checkTreeResult("def foo(*args): \n"
                + "  print(args)");
    }
    
    @Test
    public void functionDef11() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef12() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functionDef13() throws Exception {
        checkScopeAndTree();
    }
    
//    @Test
//    public void functionDef14() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void functionDef15() throws Exception {
//        checkScopeAndTree();
//    }
    
//    @Test
//    public void functionDef16() throws Exception {
//        checkScopeAndTree();
//    }
    
    @Test
    public void decorator01() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void decorator02() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void decorator03() throws Exception {
        checkScopeAndTree("@some.path.to.decorator\n"
                + "def fn(): pass");
    }
    
    @Test
    public void decorator04() throws Exception {
        checkScopeAndTree(
                "def outer():\n" +
                "  @decorator1\n" +
                "  def inner(): pass");
    }
    
    @Test
    public void decorator05() throws Exception {
        checkScopeAndTree(
                "def outer():\n" +
                "  def decorator1(fn):\n" +
                "    pass\n" +
                "  @decorator1\n" +
                "  def inner(): pass");
    }
    
    private void checkScopeAndTree()  throws Exception{
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
