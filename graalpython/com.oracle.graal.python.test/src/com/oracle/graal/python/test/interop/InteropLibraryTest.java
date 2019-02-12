package com.oracle.graal.python.test.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class InteropLibraryTest extends PythonTests {
    private Context context;

    @Before
    public void setUpTest() {
        Builder builder = Context.newBuilder();
        builder.allowAllAccess(true);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testStringUnbox() {
        Value somePStr = context.eval("python", "" +
                        "class X(str):\n" +
                        "    pass\n" +
                        "X('123')");
        assertTrue(somePStr.isString());
        assertEquals(somePStr.asString(), "123");
    }

    @Test
    public void testDoubleUnbox() {
        Value somePStr = context.eval("python", "" +
                        "class X(float):\n" +
                        "    pass\n" +
                        "X(123.0)");
        assertTrue(somePStr.fitsInFloat());
        assertTrue(somePStr.fitsInDouble());
        assertEquals(somePStr.asString(), "123");
    }

    @Test
    public void testLongUnbox() {
        Value somePStr = context.eval("python", "2**64");
        assertFalse(somePStr.fitsInLong());
        somePStr = context.eval("python", "2**63");
        assertTrue(somePStr.fitsInLong());
        assertFalse(somePStr.fitsInInt());
    }

    @Test
    public void testBooleanUnbox() {
        Value somePStr = context.eval("python", "True");
        assertTrue(somePStr.isBoolean());
        assertTrue(somePStr.asBoolean());

        somePStr = context.eval("python", "False");
        assertTrue(somePStr.isBoolean());
        assertFalse(somePStr.asBoolean());

        somePStr = context.eval("python", "1");
        assertFalse(somePStr.isBoolean());
    }
}
