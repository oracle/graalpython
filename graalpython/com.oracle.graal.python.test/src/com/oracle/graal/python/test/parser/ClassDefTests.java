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
    
    @Test
    public void classDef03() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void classDef04() throws Exception {
        checkScopeAndTree();
    }
    
    @Test
    public void classDef05() throws Exception {
        checkScopeAndTree("def fn():\n"
                + "  class DerivedClassName(modname.BaseClassName): pass");
    }
    
    @Test
    public void classDef06() throws Exception {
        checkScopeAndTree("class DerivedClassName(Base1, Base2, Base3): pass");
    }
    
    @Test
    public void classDef07() throws Exception {
        checkScopeAndTree(
                "class OrderedDict(dict):\n" +
                "  def setup(dict_setitem = dict.__setitem__):\n" +
                "    dict_setitem()\n" +
                "    dict.clear()");
    }
    @Test
    public void classDef08() throws Exception {
        checkScopeAndTree(
                "class Test():\n" +
                "    def fn1(format):\n" +
                "        return (format % args)\n" +
                "    def fn2(*args, **kwds):\n" +
                "        return self(*args, **kwds)\n");
    }
    
    @Test
    public void classDef09() throws Exception {
        checkScopeAndTree("class Enum(metaclass=EnumMeta): pass");
    }

    @Test
    public void classDef10() throws Exception {
        checkScopeAndTree(
                "def test(arg):\n" +
                "  pass\n" +
                "class FalseRec:\n" +
                "  def test(self, arg):\n" +
                "    return test(arg+1)");
    }
    
    @Test
    public void classDef11() throws Exception {
        checkScopeAndTree(
                "def make_named_tuple_class(name, fields):\n" +
                "    class named_tuple(tuple):\n" +
                "        __name__ = name\n" +
                "        n_sequence_fields = len(fields)\n" +
                "        fields = fields\n" +
                "        def __repr__(self):\n" +
                "            sb = name\n" +
                "            for f in fields:\n" +
                "                pass\n" +
                "    return named_tuple");
    }
    
    @Test
    public void decorator01() throws Exception {
        checkScopeAndTree("@class_decorator\n" +
                         "class foo():pass");
    }
    
    @Test
    public void decorator02() throws Exception {
        checkScopeAndTree("@decorator1\n" +
                        "@decorator1\n" +
                        "class foo():pass");
    }

    private void checkScopeAndTree()  throws Exception{
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
