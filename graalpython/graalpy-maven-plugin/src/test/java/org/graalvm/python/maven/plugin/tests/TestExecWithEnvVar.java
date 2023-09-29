package org.graalvm.python.maven.plugin.tests;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestExecWithEnvVar extends GraalPyPluginTests {
    @Test
    public void execWithEnvvar() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("exec_test");
        v.addCliArguments("graalpy:exec");
        v.setEnvironmentVariable("GRAAL_PYTHON_ARGS", "\013-c\013print(42, 'from python')");
        v.execute();
        v.verifyTextInLog("42 from python");
    }
}
