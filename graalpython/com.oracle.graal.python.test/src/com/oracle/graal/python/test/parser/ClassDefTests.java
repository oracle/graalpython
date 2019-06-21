/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import java.io.File;
import org.junit.Test;


public class ClassDefTests extends ParserTestBase {
    
    @Test
    public void classDef01() throws Exception {
        checkScopeAndTree("class foo():pass");
    }
    
    @Test
    public void classDef02() throws Exception {
        checkScopeAndTree("class foo(object):pass");
    }
    
    @Test
    public void classDef03() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void classDef04() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void classDef05() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  class DerivedClassName(modname.BaseClassName): pass");
    }
    
    @Test
    public void classDef06() throws Exception {
        checkScopeAndTree("class DerivedClassName(Base1, Base2, Base3): pass");
    }

    @Test
    public void decorator01() throws Exception {
        checkScopeAndTree("@class_decorator\n" +
                         "class foo():pass");
    }
    
    @Test
    public void decorator02() throws Exception {
        checkScopeAndTree("@decorator1\n" +
                        "@decorator1\n" +
                        "class foo():pass");
    }

    private void checkScopeAndTree()  throws Exception{
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
