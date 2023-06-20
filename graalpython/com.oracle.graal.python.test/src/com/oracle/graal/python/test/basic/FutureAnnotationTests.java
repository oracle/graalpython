package com.oracle.graal.python.test.basic;

import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class FutureAnnotationTests {
    @Test
    public void withoutEvaluates() {
        assertPrints("hello\n", "def f() -> print('hello'): pass");
    }

    @Test
    public void withDoesNotEvaluate() {
        assertPrints("", "from __future__ import annotations\ndef f() -> print('hello'): pass");
    }

    @Test
    public void worksInExec() {
        assertPrints("hello\n", "exec('def f() -> print(\\'hello\\'): pass')");
        assertPrints("", "exec('from __future__ import annotations\\ndef f() -> print(\\'hello\\'): pass')");
    }

    @Test
    public void execInherits() {
        assertPrints("", "from __future__ import annotations\nexec('def f() -> print(\\'hello\\'): pass')");
    }

}
