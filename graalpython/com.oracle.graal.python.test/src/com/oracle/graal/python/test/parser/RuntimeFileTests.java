/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import java.io.File;
import org.junit.Test;
 
public class RuntimeFileTests extends ParserTestBase {
    
    @Test
    public void _collections_abc() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void _descriptor() throws Exception {
        checkScopeAndTree();
    }
    
    
    @Test
    public void _sitebuiltins() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void builtins() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void collections__init__() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void enumt() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functions() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void functools() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void heapq() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void initCollectionsPart1() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void initCollectionsPart2() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void keyword() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void locale() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void operator() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void re() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void reprlib() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void site() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void sre_compile() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void sre_constants() throws Exception {
        checkScopeAndTree();
    }
    
   @Test
    public void sre_parse() throws Exception {
        checkScopeAndTree();
    }
   
    @Test
    public void sys() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void traceback() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void types() throws Exception {
        checkScopeAndTree();
    }
    
    
    private void checkScopeAndTree()  throws Exception{
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
