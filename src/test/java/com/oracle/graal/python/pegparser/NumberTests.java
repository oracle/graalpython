/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import org.junit.jupiter.api.Test;


public class NumberTests extends ParserTestBase {

    @Test
    public void int01() throws Exception {
        checkTreeResult("1");
    }
    
    @Test
    public void int01_1() throws Exception {
        checkTreeResult("+1");
    }
    
    @Test
    public void int01_2() throws Exception {
        checkTreeResult("+   1");
    }
    
    @Test
    public void int02() throws Exception {
        checkTreeResult("-1");
    }
    
    @Test
    public void int02_1() throws Exception {
        checkTreeResult("-   1");
    }
    
    @Test
    public void int03() throws Exception {
        checkTreeResult("-0");
    }
    
    @Test
    public void int04() throws Exception {
        checkTreeResult("h == -1");
    }
    
}
