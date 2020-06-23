/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.parser.sst.FunctionDefSSTNode;
import com.oracle.graal.python.parser.sst.LambdaSSTNode;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.SSTNodeWithScopeFinder;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.source.Source;
import org.junit.Assert;
import org.junit.Test;

public class SSTNodeWithScopeFinderTest extends ParserTestBase {

    @Test
    public void calllArgTest() throws Exception {
        String code = "class Tests():\n" +
                        "    def test1(self):\n" +
                        "        rdd = self.parallelize(range(100))\n" +
                        "        assert rdd.reduce(lambda a, b: a+b) == 4950";
        checkFinder(code, code.indexOf("def test1"), code.indexOf("950") + 3, false);
        checkFinder(code, code.indexOf("lambda a"), code.indexOf("a+b") + 3, true);
    }

    @Test
    public void tryTest() throws Exception {
        String code = "try:\n" +
                        "    import sys\n" +
                        "    process = None\n" +
                        "\n" +
                        "    def fn1():\n" +
                        "        return 20\n" +
                        "\n" +
                        "except ImportError:\n" +
                        "\n" +
                        "    def fn2():\n" +
                        "        return 30" +
                        "\n";
        checkFinder(code, code.indexOf("def fn1"), code.indexOf("except"), false);
        checkFinder(code, code.indexOf("def fn2"), code.indexOf("30") + 3, false);
    }

    @Test
    public void ifTest() throws Exception {
        String code = "if True:\n" +
                        "    a = 1\n" +
                        "    def fn1():\n" +
                        "        return 10\n" +
                        "elif False:\n" +
                        "    b = 2\n" +
                        "    def fn2():\n" +
                        "        return 20\n" +
                        "else:\n" +
                        "    def fn3():\n" +
                        "        return 30\n";
        checkFinder(code, code.indexOf("def fn1"), code.indexOf("elif"), false);
        checkFinder(code, code.indexOf("def fn2"), code.indexOf("else"), false);
        checkFinder(code, code.indexOf("def fn3"), code.indexOf("30") + 3, false);
    }

    private void checkFinder(String code, int startOffset, int endOffset, boolean isLambda) {
        SSTNode result = findNodeWithScope(code, startOffset, endOffset);
        Assert.assertNotNull("No node with scope was found ", result);
        if (isLambda) {
            Assert.assertTrue("Was expected LambdaSSTNode, but " + result.getClass().getSimpleName() + " found.", result instanceof LambdaSSTNode);
        } else {
            Assert.assertTrue("Was expected FunctionDefSSTNode, but " + result.getClass().getSimpleName() + " found.", result instanceof FunctionDefSSTNode);
        }
        Assert.assertTrue("Start or end offset is not the expected one.", result.getStartOffset() == startOffset && result.getEndOffset() == endOffset);
    }

    private SSTNode findNodeWithScope(String code, int startOffset, int endOffset) {
        Source source = Source.newBuilder(PythonLanguage.ID, code, "NodeWithScopeFinderTest").build();
        parse(source, PythonParser.ParserMode.File);
        SSTNode lastSST = getLastSST();
        SSTNodeWithScopeFinder finder = new SSTNodeWithScopeFinder(startOffset, endOffset);
        return lastSST.accept(finder);
    }

}
