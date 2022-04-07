/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.pegparser;

import org.junit.jupiter.api.Test;

/**
 * Testing invalid rules.
 * If the test method has the same names, but different number, then it tests different
 * alts in the rule. 
 */
public class ErrorTests extends ParserTestBase {
    // TODO due to the bad conversion of strings in java generator, there are not spaces after , and . in some messages
    
    @Test
    public void invalidArguments01() throws Exception {
        checkSyntaxErrorMessage("f(lambda x: x[0] = 3)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(x()=2)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(a or b=1)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(x.y=1)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(True=2)", "expression cannot contain assignment,perhaps you meant \"==\"?");     
    }
    
    @Test
    public void invalidArguments02() throws Exception {
        checkSyntaxErrorMessage("f(x for x in L, 1)", "Generator expression must be parenthesized");
        checkSyntaxErrorMessage("f(x for x in L, *[])", "Generator expression must be parenthesized");
        checkSyntaxErrorMessage("f(x for x in L, **{})", "Generator expression must be parenthesized");
        // TODO
//        checkSyntaxErrorMessage("f(L, x for x in L)", "Generator expression must be parenthesized");
        checkSyntaxErrorMessage("f(x for x in L, y for y in L)", "Generator expression must be parenthesized");
        
    }
    
    @Test
    public void invalidArguments03() throws Exception {
        checkSyntaxErrorMessage("f(x for x in L,)", "Generator expression must be parenthesized");
    }
    
    @Test
    public void invalidAssignment01() throws Exception {
        checkSyntaxErrorMessage("[]: int", "only single target(not list)can be annotated");
        checkSyntaxErrorMessage("([]): int", "only single target(not list)can be annotated");
        checkSyntaxErrorMessage("(): int", "only single target(not tuple)can be annotated");
        checkSyntaxErrorMessage("(()): int", "only single target(not tuple)can be annotated");
    }

    @Test
    public void invalidAssignment02() throws Exception {
        checkSyntaxErrorMessage("yield = 1", "assignment to yield expression not possible");
        checkSyntaxErrorMessage("x = y = yield = 1", "assignment to yield expression not possible");
        checkSyntaxErrorMessage("def f(): x = yield = y", "assignment to yield expression not possible");
    }
    
    @Test
    public void invalidAssignment03() throws Exception {
        checkSyntaxErrorMessage("a, b += 1, 2", "'tuple' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("(a, b) += 1, 2", "'tuple' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("[a, b] += 1, 2", "'list' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("(x for x in x) += 1", "'generator expression' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("None += 1", "'None' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("f() += 1", "'function call' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("def f(): (yield bar) += y", "'yield expression' is an illegal expression for augmented assignment");
        checkSyntaxErrorMessage("(y for y in (1,2)) += 10", "'generator expression' is an illegal expression for augmented assignment");
    }
    
    @Test
    public void invalidComprehension01() throws Exception {
        checkSyntaxErrorMessage("[*item for item in l]", "iterable unpacking cannot be used in comprehension");
        checkSyntaxErrorMessage("[*[0, 1] for i in range(10)]", "iterable unpacking cannot be used in comprehension");
        checkSyntaxErrorMessage("[*'a' for i in range(10)]", "iterable unpacking cannot be used in comprehension");
        checkSyntaxErrorMessage("[*[] for i in range(10)]", "iterable unpacking cannot be used in comprehension");
    }
    
    @Test
    public void invalidComprehension02() throws Exception {
        checkSyntaxErrorMessage("[x,y for x,y in range(100)]", "did you forget parentheses around the comprehension target?");
        checkSyntaxErrorMessage("{x,y for x,y in range(100)}", "did you forget parentheses around the comprehension target?");
        // TODO
//        checkSyntaxErrorMessage("[i := 0, j := 1 for i, j in [(1, 2), (3, 4)]]", "did you forget parentheses around the comprehension target?");        
    }
    
    @Test
    public void invalidDictComprehension01() throws Exception {
        checkSyntaxErrorMessage("{**{} for a in [1]}", "dict unpacking cannot be used in dict comprehension");     
    }
    
    @Test
    public void invalidDoubleStarredKvPairs01() throws Exception {
        checkSyntaxErrorMessage("{1: *12+1}", "cannot use a starred expression in a dictionary value");     
        checkSyntaxErrorMessage("{1: *12+1, 23: 1}", "cannot use a starred expression in a dictionary value");
        checkSyntaxErrorMessage("{1:}", "expression expected after dictionary key and ':'");
    }
    
    @Test
    public void invalidExceptionBlock01() throws Exception {
        checkSyntaxErrorMessage("try:\n" +
                "  pass\n" +
                "except A, B:\n" +
                "   pass", "exception group must be parenthesized");
        // TODO uncomment, when it will be possible. 
//        checkSyntaxErrorMessage("try:\n" +
//                "  pass\n" +
//                "except A, B, C:\n" +
//                "   pass", "exception group must be parenthesized");
//        checkSyntaxErrorMessage("try:\n" +
//                "  pass\n" +
//                "except A, B, C as blech:\n" +
//                "   pass", "exception group must be parenthesized");
//        checkSyntaxErrorMessage("try:\n" +
//                "  pass\n" +
//                "except A, B, C as blech:\n" +
//                "   pass\n" +
//                "finally:\n" +
//                "   pass", "exception group must be parenthesized");
    }
    
    @Test
    public void invalidGroup01() throws Exception {
        checkSyntaxErrorMessage("(*x),y = 1, 2 # doctest:+ELLIPSIS", "cannot use starred expression here");
        checkSyntaxErrorMessage("(((*x))),y = 1, 2 # doctest:+ELLIPSIS", "cannot use starred expression here");
        checkSyntaxErrorMessage("z,(*x),y = 1, 2, 4 # doctest:+ELLIPSIS", "cannot use starred expression here");
        checkSyntaxErrorMessage("z,(*x) = 1, 2 # doctest:+ELLIPSIS", "cannot use starred expression here");
        checkSyntaxErrorMessage("((*x),y) = 1, 2 # doctest:+ELLIPSIS", "cannot use starred expression here");
    }
        
    @Test
    public void invalidKvPairs01() throws Exception {
        checkSyntaxErrorMessage("{ 23: 1, 1: *12+1}", "cannot use a starred expression in a dictionary value");     
        checkSyntaxErrorMessage("{1:2, 3:4, 5:}", "expression expected after dictionary key and ':'");
    }
    
    @Test
    public void invalidNameExpression01() throws Exception {
        checkSyntaxErrorMessage("if x = 3:\n" +
            "   pass", "invalid syntax.Maybe you meant '==' or ':=' instead of '='?");     
        checkSyntaxErrorMessage("while x = 3:\n" +
            "   pass", "invalid syntax.Maybe you meant '==' or ':=' instead of '='?");     
    }
    
    @Test
    public void invalidNameExpression02() throws Exception {
        checkSyntaxErrorMessage("if x.a = 3:\n" +
            "   pass", "cannot assign to attribute here.Maybe you meant '==' instead of '='?");     
        checkSyntaxErrorMessage("while x.a = 3:\n" +
            "   pass", "cannot assign to attribute here.Maybe you meant '==' instead of '='?");     
    }
    
    @Test
    public void invalidNameExpression03() throws Exception {
        checkSyntaxErrorMessage("(True := 1)", "cannot use assignment expressions with True");     
        checkSyntaxErrorMessage("((a, b) := (1, 2))", "cannot use assignment expressions with tuple");
        checkSyntaxErrorMessage("(lambda: x := 1)", "cannot use assignment expressions with lambda");
    }
    
    @Test
    public void invalidPrimary01() throws Exception {
        checkSyntaxErrorMessage("f{", "invalid syntax");     
    }
    
    @Test
    public void while01() throws Exception {
        checkSyntaxErrorMessage("while True\n", "expected ':'");
    }
    
}
