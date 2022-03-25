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

public class ErrorTests extends ParserTestBase {
    
    @Test
    public void arguments01() throws Exception {
        checkSyntaxErrorMessage("f(lambda x: x[0] = 3)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(x()=2)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(a or b=1)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(x.y=1)", "expression cannot contain assignment,perhaps you meant \"==\"?");
        checkSyntaxErrorMessage("f(True=2)", "expression cannot contain assignment,perhaps you meant \"==\"?");     
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
    public void invalidPrimary01() throws Exception {
        checkSyntaxErrorMessage("f{", "invalid syntax");     
    }
    
    @Test
    public void while01() throws Exception {
        checkSyntaxErrorMessage("while True\n", "expected ':'");
    }
    
}
