package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class AsyncTests {
    @Test
    public void nativeCoroutine() {
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
