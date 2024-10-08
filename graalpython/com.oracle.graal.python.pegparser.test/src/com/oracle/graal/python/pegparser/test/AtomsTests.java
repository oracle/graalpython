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
package com.oracle.graal.python.pegparser.test;

import org.junit.Test;

public class AtomsTests extends ParserTestBase {

    @Test
    public void variableName() throws Exception {
        checkTreeResult("foo");
    }

    @Test
    public void atomTrue() throws Exception {
        checkTreeResult("True");
    }

    @Test
    public void atomFalse() throws Exception {
        checkTreeResult("False");
    }

    @Test
    public void atomNone() throws Exception {
        checkTreeResult("None");
    }

    @Test
    public void atomString1() throws Exception {
        checkTreeResult("'a String'");
    }

    @Test
    public void atomString2() throws Exception {
        checkTreeResult("\"a String\"");
    }

    @Test
    public void atomString3() throws Exception {
        checkTreeResult("'''a String'''");
    }

    @Test
    public void atomString4() throws Exception {
        checkTreeResult("\"\"\"a String\"\"\"");
    }

    @Test
    public void atomString5() throws Exception {
        checkTreeResult("'a' ' String'");
    }

    @Test
    public void atomString6() throws Exception {
        checkTreeResult("'''a''' ' String'");
    }

    @Test
    public void atomString7() throws Exception {
        checkTreeResult("\"a\" ' String'");
    }

    @Test
    public void atomFString() throws Exception {
        checkTreeResult("f'a{b!r}'");
        checkError("f'a{b!g}'", "Syntax[1:6-1:7]:f-string: invalid conversion character 'g': expected 's', 'r', or 'a'");
    }

    @Test
    public void atomFStringFormatSpec() throws Exception {
        checkTreeResult("f'a{b!r:05}'");
    }

    @Test
    public void atomFStringDebug() throws Exception {
        checkTreeResult("f'a{b=}'");
    }

    @Test
    public void atomFStringDebugMultiline() throws Exception {
        checkTreeResult("f\"\"\"a{b\n" +
                        "+ # comment\n" +
                        "c=}\"\"\"");
    }

    @Test
    public void atomFStringMultiline() throws Exception {
        checkTreeResult("f\"\"\"First line.{\n" +
                        "   v1\n" +
                        "+\n" +
                        "v2}\n" +
                        "Another line.\"\"\"");
    }

    @Test
    public void atomFStringGreedy() throws Exception {
        checkTreeResult("f\"{1:>3{5}}}}\"");
    }

    @Test
    public void atomByte() throws Exception {
        checkTreeResult("b\"a\"");
    }

    @Test
    public void atomMixedBytesString() throws Exception {
        checkError("b\"a\" f'aa'", "Syntax[1:10-1:11]:cannot mix bytes and nonbytes literals");
    }

    @Test
    public void atomTuple() throws Exception {
        checkTreeResult("(a,)");
    }

    @Test
    public void atomGroup() throws Exception {
        checkTreeResult("(a)");
    }

    @Test
    public void atomList() throws Exception {
        checkTreeResult("[2]");
    }

    @Test
    public void atomListcomp() throws Exception {
        checkTreeResult("[[i] for i in a]");
    }

    @Test
    public void atomListcomp2() throws Exception {
        checkTreeResult("[[i] for i in a for [j] in b if 12]");
    }

    @Test
    public void atomDict() throws Exception {
        checkTreeResult("{1: 2}");
    }

    @Test
    public void atomSet() throws Exception {
        checkTreeResult("{a, 2}");
    }

    @Test
    public void atomDictcomp() throws Exception {
        checkTreeResult("{(a,):(b) for a,b in y}");
    }

    @Test
    public void atomSetcomp() throws Exception {
        checkTreeResult("{(a,) for a in b}");
    }

    @Test
    public void atomGenerator() throws Exception {
        checkTreeResult("((a,) for a in b)");
    }

    @Test
    public void atomEllipsis() throws Exception {
        checkTreeResult("...");
    }

    @Test
    public void atomUnicodeStringPrefix() throws Exception {
        checkTreeResult("(u'abc', u'abc' 'def', 'abc' u'def', f'{u\"abc\"}' 'def')");
    }

    @Test
    public void atomMultilineString() throws Exception {
        checkTreeResult("'''abc\ndef'''");
    }

    @Test
    public void atomMultilineStringCrLf() throws Exception {
        checkTreeResult("'''abc\r\ndef'''");
    }

    @Test
    public void atomMultilineStringCr() throws Exception {
        checkTreeResult("'''abc\rdef'''");
    }
}
