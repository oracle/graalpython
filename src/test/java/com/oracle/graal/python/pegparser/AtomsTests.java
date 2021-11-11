/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import org.junit.jupiter.api.Test;


public class AtomsTests extends ParserTestBase {

    @Test
    public void variableName() throws Exception {
        checkTreeResult("foo");
    }

    @Test
    public void atomTrue() throws Exception {
        checkTreeResult("True");
    }

    @Test
    public void atomFalse() throws Exception {
        checkTreeResult("False");
    }

    @Test
    public void atomNone() throws Exception {
        checkTreeResult("False");
    }

    @Test
    public void atomString() throws Exception {
        checkTreeResult("'a String'");
        checkTreeResult("\"a String\"");
        checkTreeResult("'''a String'''");
        checkTreeResult("\"\"\"a String\"\"\"");
        checkTreeResult("'a' ' String'");
        checkTreeResult("'''a''' ' String'");
        checkTreeResult("\"a\" ' String'");
    }
}
