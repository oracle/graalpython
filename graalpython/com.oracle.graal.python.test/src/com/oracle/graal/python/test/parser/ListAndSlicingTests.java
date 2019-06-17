/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

import org.junit.Test;

public class ListAndSlicingTests extends ParserTestBase {
    
    @Test
    public void list01() throws Exception {
        checkTreeResult("[1,2,3,4]");
    }
    
    @Test
    public void list02() throws Exception {
        checkTreeResult("list = [1,2,3,4]");
    }
    
    @Test
    public void list03() throws Exception {
        checkTreeResult("[]");
    }
    
    @Test
    public void list04() throws Exception {
        checkTreeResult("l = []");
    }
    
    @Test
    public void list05() throws Exception {
        checkTreeResult("[*{2}, 3, *[4]]");
    }
    
    @Test
    public void slice01() throws Exception {
        checkTreeResult("a[::]");
    }
    
    @Test
    public void slice02() throws Exception {
        checkTreeResult("a[1::]");
    }
    
    @Test
    public void slice03() throws Exception {
        checkTreeResult("a[:1:]");
    }
    
    @Test
    public void slice04() throws Exception {
        checkTreeResult("a[::1]");
    }
    
    @Test
    public void slice05() throws Exception {
        checkTreeResult("a()[b():c():d()]");
    }
}
