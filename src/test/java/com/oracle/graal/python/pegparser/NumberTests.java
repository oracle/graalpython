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
    
    @Test
    public void int05() throws Exception {
        checkTreeResult("--2");
    }
    
    @Test
    public void int06() throws Exception {
        checkTreeResult("---2");
    }
    
    @Test
    public void int07() throws Exception {
        checkTreeResult("----2");
    }

    @Test
    public void maxint() throws Exception {
        checkTreeResult("2147483647");
    }
    
    @Test
    public void minint() throws Exception {
        checkTreeResult("-2147483648");
    }
    
    @Test
    public void minlong() throws Exception {
        checkTreeResult("-9223372036854775808");
    }

    @Test
    public void maxNegLong() throws Exception {
        checkTreeResult("-2147483649");
    }

    @Test
    public void minPosLong() throws Exception {
        checkTreeResult("2147483648");
    }

    @Test
    public void maxlong() throws Exception {
        checkTreeResult("9223372036854775807");
    }

    @Test
    public void minPosPInt() throws Exception {
        checkTreeResult("9223372036854775808");
    }

    @Test
    public void maxNegPInt() throws Exception {
        checkTreeResult("-9223372036854775809");
    }
   
}
