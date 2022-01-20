/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.compiler;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.CompilationUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.pegparser.FExprParser;
import com.oracle.graal.python.pegparser.NodeFactory;
import com.oracle.graal.python.pegparser.NodeFactoryImp;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.ParserErrorCallback;
import com.oracle.graal.python.pegparser.ParserTokenizer;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.test.PythonTests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class CompilerTests extends PythonTests {
    public CompilerTests() {
    }

    @Rule public TestName name = new TestName();

    @Test
    public void testBinaryOp() {
        doTest("1 + 1", "<module>");
    }

    @Test
    public void testAssignment() {
        doTest("a = 12", "<module>");
    }

    @Test
    public void testAugAssignment() {
        doTest("a += 12.0", "<module>");
    }

    @Test
    public void testCall() {
        doTest("range(num)", "<module>");
    }

    @Test
    public void testFor() {
        doTest("for i in [1,2]:\n pass", "<module>");
    }

    @Test
    public void testWhile() {
        doTest("while False: pass", "<module>");
    }

    // @Test
    // public void testExcept() {
    //     String s;
    //     s = "try:\n" +
    //         "  print(1)\n" +
    //         "except BaseException as e:\n" +
    //         "  print(e)\n" +
    //         "except ValueError:\n" +
    //         "  print('ValueError')\n" +
    //         "except:\n" +
    //         "  pass\n" +
    //         "else:\n" +
    //         "  print('else')\n" +
    //         "finally:\n" +
    //         "  print('finally')\n";
    //     doTest(s, null);
    // }

    @Test
    public void testDefun() {
        String s;
        s = "def docompute(num, num2=5):\n" +
            "   return (num, num2)\n";
        doTest(s, null);
    }

    @Test
    public void testBenchmark() {
        String source = "def docompute(num):\n" +
                        "    for i in range(num):\n" +
                        "        sum_ = 0.0\n" +
                        "        j = 0\n" +
                        "        while j < num:\n" +
                        "            sum_ += 1.0 / (((i + j) * (i + j + 1) >> 1) + i + 1)\n" +
                        "            j += 1\n" +
                        "\n" +
                        "    return sum_\n" +
                        "\n" +
                        "\n" +
                        "def measure(num):\n" +
                       "    for run in range(num):\n" +
                        "        sum_ = docompute(10000)  # 10000\n" +
                        "    print('sum', sum_)\n" +
                        "\n" +
                        "\n" +
                        "def __benchmark__(num=5):\n" +
                        "    measure(num)\n";
        doTest(source, "<module>");
    }

    private void doTest(String src, String moduleName) {
        ParserTokenizer tokenizer = new ParserTokenizer(src);
        NodeFactory factory = new NodeFactoryImp();
        ParserErrorCallback errorCb = new ParserErrorCallback() {
            @Override
            public void onError(ParserErrorCallback.ErrorType type, int start, int end, String message) {
                System.err.println(String.format("TODO: %s[%d:%d]: %s", type.name(), start, end, message));
            }
        };
        FExprParser fexpParser = new FExprParser() {
            @Override
            public ExprTy parse(String code) {
                ParserTokenizer tok = new ParserTokenizer(code);
                return new Parser(tok, factory, this, errorCb).fstring_rule();
            }
        };
        Parser parser = new Parser(tokenizer, factory, fexpParser, errorCb);
        ModTy result = parser.file_rule();
        Compiler compiler = new Compiler();
        CompilationUnit cu = compiler.compile(result, moduleName, EnumSet.noneOf(Compiler.Flags.class), 2);
        CodeUnit co = cu.assemble(moduleName, 0);
        checkCodeUnit(co);
    }

    private void checkCodeUnit(CodeUnit co) {
        String coString = co.toString();
        Path goldenFile = Paths.get(System.getProperty("org.graalvm.language.python.home"),
                        "com.oracle.graal.python.test", "testData", "goldenFiles",
                        this.getClass().getSimpleName(),
                        name.getMethodName() + ".co");
        try {
            if (!Files.exists(goldenFile)) {
                Files.createDirectories(goldenFile.getParent());
                Files.writeString(goldenFile, coString);
            } else {
                Assert.assertEquals(Files.readString(goldenFile), coString);
            }
        } catch (IOException ex) {
            Assert.assertTrue(ex.getMessage(), false);
        }
    }

}
