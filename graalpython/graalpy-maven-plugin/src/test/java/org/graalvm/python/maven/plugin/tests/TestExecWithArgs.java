package org.graalvm.python.maven.plugin.tests;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestExecWithArgs extends GraalPyPluginTests {
    @Test
    public void execWithArgs() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("exec_test");
        v.addCliArguments("graalpy:exec", "-Dexec.argc=2", "-Dexec.arg1=-c", "-Dexec.arg2=print(42, 'from python')");
        v.execute();
        v.verifyTextInLog("42 from python");
    }
}
