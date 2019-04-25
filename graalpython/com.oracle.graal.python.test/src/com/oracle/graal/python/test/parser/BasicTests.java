/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.graal.python.test.parser;

import java.io.File;
import org.junit.*;

public class BasicTests extends ParserTestBase {
    
    @Test
    public void moduleDoc01() throws Exception {
        checkFile();
    }
    
    @Test
    public void moduleDoc02() throws Exception {
        checkFile();
    }
    
    @Test
    public void moduleDoc03() throws Exception {
        // testing new lines after the module doc
        checkFile();
    }
    
    @Test
    public void moduleDoc04() throws Exception {
        checkFile();
    }
    
    
    @Test
    public void simpleExpression01() throws Exception {
        checkTreeResult("'ahoj'");
    }
    
    @Test
    public void simpleExpression02() throws Exception {
        checkTreeResult("'ahoj'; 2");
    }
    
    @Test
    public void simpleExpression03() throws Exception {
        checkTreeResult("'ahoj'; 2; 1.0");
    }
    
    @Test
    public void longString01() throws Exception {
        checkTreeResult("'''ahoj'''");
    }
    
    @Test
    public void longString02() throws Exception {
        checkTreeResult("'''\n"
                + "ahoj\n"
                + "hello\n"
                + "good bye\n"
                + "'''");
    }
    
    @Test
    public void binaryOp01() throws Exception {
        checkTreeResult("1 + 10");
    }
    
    @Test
    public void binaryOp02() throws Exception {
        checkTreeResult("'ahoj' + 10");
    }
    
    @Test
    public void binaryOp03() throws Exception {
        checkTreeResult("3 ** 2");
    }
    
    @Test
    public void binaryOp04() throws Exception {
        checkTreeResult("3 ** 2 ** 2");
    }
    
    @Test
    public void comparision01() throws Exception {
        checkTreeResult("3 < 10");
    }
    
    @Test
    public void comparision02() throws Exception {
        checkTreeResult("1 < '10' > True");
    }
    
    @Test
    public void comparision03() throws Exception {
        checkTreeResult("1 < '10' > True != 1.0");
    }
    
    @Test
    public void if01() throws Exception {
        checkTreeResult(
                "if False: \n"
              + "  10");
    }
    
    @Test
    public void assignment01() throws Exception {
        checkTreeResult("a = 1");
    }
    
    @Test
    public void assignment02() throws Exception {
        checkTreeResult("a = b = 1");
    }
    
    @Test
    public void call01() throws Exception {
        checkTreeResult("foo()");
    }
    
    @Test
    public void call02() throws Exception {
        checkTreeResult("foo(1)");
    }
    
    @Test
    public void call03() throws Exception {
        checkTreeResult("foo(arg = 1)");
    }
    
    @Test
    public void call04() throws Exception {
        checkSyntaxError("foo(1+arg = 1)");
    }
    
    @Test
    public void call05() throws Exception {
        checkSyntaxError("foo(arg + 1 = 1)");
    }
    
    @Test
    public void call06() throws Exception {
        checkTreeResult("foo(arg1 = 1, arg2 = 2)");
    }
 
    @Test
    public void call07() throws Exception {
        checkTreeResult("foo('ahoj', arg1 = 1, arg2 = 2)");
    }
    
    @Test
    public void call08() throws Exception {
        checkTreeResult("foo('ahoj', arg1 = 1, arg2 = 2)");
    }
    
    @Test
    public void call09() throws Exception {
        checkTreeResult("foo(*mylist)");
    }
    
    @Test
    public void call10() throws Exception {
        checkTreeResult("foo(*mylist1, *mylist2)");
    }
    
    @Test
    public void call11() throws Exception {
        checkTreeResult("foo(**mydict)");
    }
    
    @Test
    public void call12() throws Exception {
        checkTreeResult("foo(**mydict1, **mydict2)");
    }
    
    @Test
    public void call13() throws Exception {
        checkSyntaxError("foo(**mydict1, *mylist)");
    }
    
    @Test
    public void call14() throws Exception {
        checkSyntaxError("foo(**mydict1, 1)");
    }
    
    @Test
    public void call15() throws Exception {
        checkSyntaxError("foo(arg1=1, 1)");
    }
    
    @Test
    public void functionDef01() throws Exception {
        checkTreeResult("def foo(): pass");
    }
    
    @Test
    public void functionDef02() throws Exception {
        checkTreeResult("def foo(): \n"
                + "  return 10\n");
    }
    
    @Test
    public void functionDef03() throws Exception {
        checkTreeResult("def foo(): \n"
                + "  a = 10\n"
                + "  return a\n");
    }
    
//    @Test
//    public void scope01() throws Exception {
//        checkTreeResult("a = 1\n"
//                + "def foo(): \n"
//                + "  a = 10\n"
//                + "  return a\n");
//    }
    
//    @Test
//    public void SimpleTest() throws Exception{
//        String src = "def foo(): \n"
//                + "  a = 10\n"
//                + "  return a\n";
////        String src = 
////                  "if true: \n"
////                + "  'ahoj'\n"
////                + "  10";
//        System.out.println("old parser");
//        
//        
//        System.out.println("Result");
////      System.out.println("----------old truffle tree-----------");
//        RootNode result = parse(src);
//        System.out.println(NodeUtil.printCompactTreeToString(result)); 
//        
////        System.out.println("-----------Antlr AST-------------");
////        StringBuilder sb = new StringBuilder();
////        (new OldParserVisitor(sb)).visit(lastAntrlTree);
////        System.out.println(sb.toString());
//        
//        System.out.println("------new------");
//        Node resultNew = parseNew(src);
//        System.out.println("----new truffle tree---");
//        System.out.println(NodeUtil.printCompactTreeToString(resultNew));
//        System.out.println("----new our truffle tree---");
//        ParserTreePrinter visitor = new ParserTreePrinter();
//        result.accept(visitor);
//        System.out.println(visitor.getTree());
//    }
 
    private void checkFile() throws Exception  {
        File testFile = getTestFileFromTestAndTestMethod();
        checkTreeFromFile(testFile, true);
    }
}
