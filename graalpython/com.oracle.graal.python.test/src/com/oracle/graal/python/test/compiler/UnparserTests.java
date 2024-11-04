/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.compiler.Unparser;
import com.oracle.graal.python.pegparser.ErrorCallback;
import com.oracle.graal.python.pegparser.InputType;
import com.oracle.graal.python.pegparser.Parser;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.tokenizer.CodePoints;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;
import com.oracle.graal.python.test.PythonTests;

public class UnparserTests extends PythonTests {

    @Test
    public void testUnparse() {
        checkRoundTrip("(1 + a - 4) * 2 / 3 // 5 ** b << 7");
        checkRoundTrip("a and b or not (c or d)");
        checkRoundTrip("[a.attr, b['foo'], foo(1, x=b'asdf')]");
        checkRoundTrip("lambda x: {x: 1.0 for i in range(10)}");
        checkRoundTrip("2j + 1 if True else 2");
        checkRoundTrip("f'a={a},b={b!r},c={c:2.0}'");
    }

    @Test
    public void testUnparseConstant() {
        assertEquals("...", unparseConstant(ConstantValue.ELLIPSIS));
        assertEquals("'abc'", unparseConstant(ConstantValue.ofCodePoints(CodePoints.fromJavaString("abc"))));
        assertEquals("u'abc'", unparseConstant(ConstantValue.ofCodePoints(CodePoints.fromJavaString("abc")), "u"));
        ConstantValue[] empty = new ConstantValue[0];
        ConstantValue[] single = new ConstantValue[]{ConstantValue.ofLong(42)};
        ConstantValue[] multiple = new ConstantValue[]{
                        ConstantValue.ofLong(42),
                        ConstantValue.ofDouble(3.14),
                        ConstantValue.FALSE,
                        ConstantValue.ELLIPSIS,
                        ConstantValue.ofCodePoints(CodePoints.fromJavaString("abc")),
                        ConstantValue.ofBytes("xyz".getBytes())
        };
        assertEquals("()", unparseConstant(ConstantValue.ofTuple(empty)));
        assertEquals("(42,)", unparseConstant(ConstantValue.ofTuple(single)));
        assertEquals("(42, 3.14, False, Ellipsis, 'abc', b'xyz')", unparseConstant(ConstantValue.ofTuple(multiple)));
        assertEquals("frozenset()", unparseConstant(ConstantValue.ofFrozenset(empty)));
        assertEquals("frozenset({42})", unparseConstant(ConstantValue.ofFrozenset(single)));
    }

    private static void checkRoundTrip(String source) {
        ErrorCallback errorCallback = new CompilerTests.TestErrorCallbackImpl();
        Parser parser = Compiler.createParser(source, errorCallback, InputType.EVAL, false);
        ModTy.Expression result = (ModTy.Expression) parser.parse();
        assertEquals(source, unparse(result.body));
    }

    private static String unparse(SSTNode node) {
        return Unparser.unparse(node).toJavaStringUncached();
    }

    private static String unparseConstant(ConstantValue constantValue) {
        return unparseConstant(constantValue, null);
    }

    private static String unparseConstant(ConstantValue constantValue, String kind) {
        return unparse(new ExprTy.Constant(constantValue, kind, SourceRange.ARTIFICIAL_RANGE));
    }
}
