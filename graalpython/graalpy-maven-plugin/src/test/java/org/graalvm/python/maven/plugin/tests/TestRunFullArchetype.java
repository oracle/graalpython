package org.graalvm.python.maven.plugin.tests;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestRunFullArchetype extends GraalPyPluginTests {
    @Test
    public void runFullArchetype() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        // build
        var v = getLocalVerifier("archetype_test");
        v.addCliArguments("package");
        v.execute();
        v.verifyTextInLog("-m venv");
        v.verifyTextInLog("-m ensurepip");
        v.verifyTextInLog("termcolor");
        v.verifyTextInLog("BUILD SUCCESS");
        // run
        v = getLocalVerifier("archetype_test");
        v.setAutoclean(false);
        v.addCliArguments("exec:java", "-Dexec.mainClass=GraalPy");
        v.execute();
        v.verifyTextInLog("/graalpy_vfs/home/lib/python3.10");
        v.verifyTextInLog("/graalpy_vfs/home/lib/graalpy23.1/modules");
        v.verifyTextInLog("/graalpy_vfs/venv/lib/python3.10/site-packages");
    }
}
