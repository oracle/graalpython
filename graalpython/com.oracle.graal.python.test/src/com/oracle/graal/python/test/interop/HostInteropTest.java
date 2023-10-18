package com.oracle.graal.python.test.interop;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

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
    public void testCustomTypeRegistryInterop() {
        Value t = context.eval("python", """
                        import polyglot

                        class MyType(object):
                            data = 10

                        def is_number(t):
                            return True

                        def fits_in_int(t):
                            return t.data < 0x7fffffff

                        polyglot.register_host_interop_behavior(MyType,
                            is_number=is_number,
                            fits_in_int=fits_in_int)

                        MyType()
                        """);
        System.out.printf("\n\n>>>> Is number: %s\n\n\n", t.isNumber());
        System.out.printf("\n\n>>>> FITS In int: %s\n\n\n", t.fitsInInt());
    }
}
