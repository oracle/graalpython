package com.oracle.graal.python.test.advance;

import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class CustomBuiltinsTest extends PythonTests {
    @Test
    public void testCustomBuiltinModule() {
        assertPrints("success", "import CustomModule; print(CustomModule.success)");
    }
}
