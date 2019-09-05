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

public class RuntimeFileTests extends ParserTestBase {

    @Test
    public void _collections_abc() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void _descriptor() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void _sitebuiltins() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void builtins() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void collections__init__() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void enumt() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functions() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functools() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void heapq() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void initCollectionsPart1() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void initCollectionsPart2() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void keyword() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void locale() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void operator() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void re() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void reprlib() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void site() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void sre_compile() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void sre_constants() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void sre_parse() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void sys() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void traceback() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void types() throws Exception {
        checkScopeAndTree();
    }

    private void checkScopeAndTree() throws Exception {
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
