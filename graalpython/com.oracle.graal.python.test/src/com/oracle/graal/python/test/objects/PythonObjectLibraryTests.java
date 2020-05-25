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
package com.oracle.graal.python.test.objects;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;

import com.oracle.graal.python.test.PythonTests;
import java.util.concurrent.Callable;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

public class PythonObjectLibraryTests extends PythonTests {

    private Context context;

    @Before
    public void setUpTest() {
        Context.Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testLookupAttribute() {
        lookupAttr(() -> true, "__str__", false);
        lookupAttr(() -> 1.1, "__str__", false);
        lookupAttr(() -> 1, "__str__", false);
        lookupAttr(() -> (long) 1, "__str__", false);
        lookupAttr(() -> "abc", "__str__", false);

        lookupAttr(() -> new Object(), "__str__", true);

        lookupAttr(() -> PythonObjectFactory.getUncached().createInt(1), "__str__", false);

        String noSuchMethod = "__nnoossuuttschmeethod__";
        lookupAttr(() -> true, noSuchMethod, true);
        lookupAttr(() -> 1.1, noSuchMethod, true);
        lookupAttr(() -> 1, noSuchMethod, true);
        lookupAttr(() -> (long) 1, "__strt__", true);
        lookupAttr(() -> "abc", noSuchMethod, true);
    }

    private void lookupAttr(Callable<Object> createValue, String attrName, boolean expectNoValue) {
        PythonObjectLibrary lib = PythonObjectLibrary.getFactory().getUncached();
        execInContext(() -> {
            Object value = createValue.call();
            Object attr = lib.lookupAttribute(value, attrName, false);
            assertAttr(attr, expectNoValue);

            attr = lib.lookupAttribute(value, attrName, true);
            assertAttr(attr, expectNoValue);
            return null;
        });
    }

    private static void assertAttr(Object attr, boolean expectNoValue) {
        assertNotNull(attr);
        if (expectNoValue) {
            assertSame(PNone.NO_VALUE, attr);
        } else {
            assertNotSame(PNone.NO_VALUE, attr);
        }
    }

    public void execInContext(Callable<Object> c) {
        context.initialize("python");
        context.getPolyglotBindings().putMember("testSymbol", (ProxyExecutable) (Value... args) -> {
            try {
                return c.call();
            } catch (Exception ex) {
                ex.printStackTrace();
                fail();
            }
            return null;
        });
        context.getPolyglotBindings().getMember("testSymbol").execute();
    }

}
