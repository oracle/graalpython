package com.oracle.graal.python.test.interop;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostInteropTest extends PythonTests {
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
    public void testConstantInteropBehavior() {
        Value t = context.eval("python", """
            import polyglot

            class MyType(object):
                pass

            polyglot.register_host_interop_behavior(MyType,
                is_boolean=False,
                is_number=True,
                is_string=False,
                # is_date=False,
                # is_duration=False,
                # is_instant=True,
                # is_iterator=False,
                # is_time=True,
                # is_time_zone=False
            )

            MyType()
            """);
        assertFalse(t.isBoolean());
        assertTrue(t.isNumber());
        assertFalse(t.isString());
        // todo (cbasca): redefining behavior is currently not supported
        // assertFalse(t.isDate());
        // assertFalse(t.isDuration());
        // assertTrue(t.isInstant());
        // assertFalse(t.isIterator());
        // assertTrue(t.isTime());
        // assertFalse(t.isTimeZone());
    }

    @Test
    public void testConstantDefaults() {
        Value t = context.eval("python", """
            import polyglot

            class MyType(object):
                pass

            polyglot.register_host_interop_behavior(MyType, is_number=True)

            MyType()
            """);
        assertFalse(t.isBoolean());
        assertTrue(t.isNumber());
        assertFalse(t.isString());
    }

    @Test
    public void testBoolean() {
        String source = """
            import polyglot

            class MyType(object):
                def __init__(self, data):
                    self._data = data

            def as_boolean(t):
                return t._data == "x"

            polyglot.register_host_interop_behavior(MyType,
                is_boolean=True,
                as_boolean=as_boolean
            )
            """;
        Value t = context.eval("python", source + "\nMyType('x')");
        assertTrue(t.isBoolean());
        assertTrue(t.asBoolean());
        t = context.eval("python", source + "\nMyType('y')");
        assertTrue(t.isBoolean());
        assertFalse(t.asBoolean());
    }

    @Test
    public void testNumbersFitsInBehavior() {
        Value t = context.eval("python", """
            import polyglot

            class MyType(object):
                data = 0x7fffffff + 1

            def fits_in_int(t):
                return t.data < 0x7fffffff

            def fits_in_long(t):
                return t.data < 0xffffffffffffffff
                
            def fits_in_big_integer(t):
                return True

            polyglot.register_host_interop_behavior(MyType,
                is_number=True,
                fits_in_int=fits_in_int,
                fits_in_long=fits_in_long,
                fits_in_big_integer=fits_in_big_integer
            )

            MyType()
            """);
        assertTrue(t.isNumber());
        assertFalse(t.isString());
        assertFalse(t.fitsInInt());
        assertTrue(t.fitsInLong());
        assertTrue(t.fitsInBigInteger());
    }
}
