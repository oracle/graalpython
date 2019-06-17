/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.test.parser;

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

//        self.check_suite("@class_decorator\n"
//                         "class foo():pass")
//        self.check_suite("@class_decorator(arg)\n"
//                         "class foo():pass")
//        self.check_suite("@decorator1\n"
//                         "@decorator2\n"
//                         "class foo():pass")
//    
}
