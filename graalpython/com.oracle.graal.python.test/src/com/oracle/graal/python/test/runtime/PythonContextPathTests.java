/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import org.junit.After;
import org.junit.Test;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.TruffleFile;

public class PythonContextPathTests {

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    @Test
    public void isPyFileInLanguageHomeNormalizesPathBeforeContainmentCheck() {
        PythonTests.enterContext();
        PythonContext context = PythonContext.get(null);
        String languageHomePath = context.getLanguageHome().toJavaStringUncached();
        TruffleFile languageHome = context.getEnv().getInternalTruffleFile(languageHomePath).getAbsoluteFile().normalize();
        assumeFalse("resource-backed language home is rooted at /", "/".equals(languageHome.getPath()));
        TruffleFile insideLanguageHome = context.getEnv().getInternalTruffleFile(languageHomePath + "/lib-python");
        TruffleFile escapedLanguageHome = context.getEnv().getInternalTruffleFile(languageHomePath + "/../outside.py");

        assertTrue(context.isPyFileInLanguageHome(insideLanguageHome));
        assertFalse("escaped path " + escapedLanguageHome.getAbsoluteFile().normalize() +
                        " should not be contained in language home " + languageHome,
                        context.isPyFileInLanguageHome(escapedLanguageHome));
    }
}
