package com.oracle.graal.python.test.advance;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class MultiContextTest extends PythonTests {
    @Test
    public void testContextReuse() {
        Engine engine = Engine.newBuilder().build();
        for (int i = 0; i < 10; i++) {
            try (Context context = newContext(engine)) {
                context.eval("python", "memoryview(b'abc')");
            }
        }
    }

    private static Context newContext(Engine engine) {
        return Context.newBuilder().allowAllAccess(true).engine(engine).build();
    }
}
