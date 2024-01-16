package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertLastLineErrorContains;

import org.junit.Test;

public class SubscriptTests {

    @Test
    // Regression test for GR-51403
    public void testNoQuickeningForCollection() {
        String source = "1[1]\n";
        assertLastLineErrorContains("TypeError: 'int' object is not subscriptable", source);
    }
}
