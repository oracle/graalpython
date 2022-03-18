/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.pegparser;

import org.junit.jupiter.api.Test;


public class GenParserTest {

    public GenParserTest() {
    }

    @Test
    public void testSimple01() {
//        String text = "a:int=1";
//        String text = "a = 1 # type: int";
        String text = "a = 1";
        ParserTokenizer tokenizer = new ParserTokenizer(text);
        Parser parser = new Parser(tokenizer, new NodeFactoryImp(), null, null);
        Object result = parser.parse(AbstractParser.InputType.FILE);
        System.out.println("#Result of the parsing: " + result);
    }

}
