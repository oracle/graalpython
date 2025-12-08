/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;
import static com.oracle.graal.python.test.integration.Utils.IS_WINDOWS;
import static org.junit.Assume.assumeFalse;

import org.junit.Test;

public class AsyncTests {
    @Test
    public void nativeCoroutine() {
        assumeFalse(IS_WINDOWS);
        String source = "import asyncio\n" +
                        "async def foo():\n" +
                        "  return 42\n" +
                        "async def main():\n" +
                        "  print(await foo())\n" +
                        "asyncio.run(main())";
        assertPrints("42\n", source);
    }

    @Test
    public void asyncWith() {
        assumeFalse(IS_WINDOWS);
        String source = "import asyncio\n" +
                        "class AsyncContextManager:\n" +
                        "  async def __aenter__(self):\n" +
                        "    await asyncio.sleep(0.01)\n" +
                        "    print(\"entered\")\n" +
                        "  async def __aexit__(self, exc_type, exc_value, traceback):\n" +
                        "    await asyncio.sleep(0.01)\n" +
                        "    if exc_type:\n" +
                        "      print(\"exited exceptionally\")\n" +
                        "    else:\n" +
                        "      print(\"exited normally\")\n" +
                        "    return True\n" +
                        "async def main(shouldRaise):\n" +
                        "  async with AsyncContextManager():\n" +
                        "    print(\"inside\")\n" +
                        "    if shouldRaise:\n" +
                        "      raise ValueError\n" +
                        "asyncio.run(main(%s))";
        assertPrints("entered\ninside\nexited normally\n", String.format(source, "False"));
        assertPrints("entered\ninside\nexited exceptionally\n", String.format(source, "True"));
    }

    @Test
    public void asyncWithExceptional() {
        assumeFalse(IS_WINDOWS);
        String source = "import asyncio\n" +
                        "class AsyncContextManager:\n" +
                        "  async def __aenter__(self):\n" +
                        "    await asyncio.sleep(0.01)\n" +
                        "    print(\"entered\")\n" +
                        "  async def __aexit__(self, exc_type, exc_value, traceback):\n" +
                        "    await asyncio.sleep(0.01)\n" +
                        "    print(\"exited\")\n" +
                        "    return False\n" + // don't handle exception
                        "async def main(shouldRaise):\n" +
                        "  async with AsyncContextManager():\n" +
                        "    print(\"inside\")\n" +
                        "    if shouldRaise:\n" +
                        "      raise ValueError\n" +
                        "try:\n" +
                        "  asyncio.run(main(%s))\n" +
                        "except ValueError:\n" +
                        "  print(\"rethrew\")\n";
        assertPrints("entered\ninside\nexited\n", String.format(source, "False"));
        assertPrints("entered\ninside\nexited\nrethrew\n", String.format(source, "True"));
    }
}
