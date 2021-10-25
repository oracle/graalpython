/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


public class GenParserTest {
    
    public GenParserTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testSimple01() {
        String text = "a:int=1";
        ParserTokenizer tokenizer = new ParserTokenizer(text);
        GenParser parser = new GenParser(tokenizer, new NodeFactoryImp());
        Object result = parser.file_rule();
        System.out.println("#Result of the parsing: " + result);
    }
    
}
