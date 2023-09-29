package org.graalvm.python.maven.plugin.tests;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestGenerateLauncherAndVenv extends GraalPyPluginTests {
    @Test
    public void generateLauncherAndVenv() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("prepare_venv_test");
        v.addCliArguments("generate-resources");
        v.execute();
        v.verifyTextInLog("-m venv");
        v.verifyTextInLog("-m ensurepip");
        v.verifyTextInLog("ujson");
        v.verifyTextInLog("termcolor");
    }
}
