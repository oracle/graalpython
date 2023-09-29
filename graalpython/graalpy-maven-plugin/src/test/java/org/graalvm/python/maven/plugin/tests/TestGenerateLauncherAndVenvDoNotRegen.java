package org.graalvm.python.maven.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestGenerateLauncherAndVenvDoNotRegen extends GraalPyPluginTests {
    @Test
    public void generateLauncherAndVenvAndDoNotRegen() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("prepare_venv_test");
        v.addCliArguments("generate-resources");
        v.execute();
        v.verifyTextInLog("-m venv");
        v.verifyTextInLog("-m ensurepip");
        v.verifyTextInLog("ujson");
        v.verifyTextInLog("termcolor");
        // run again and assert that we do not regenerate the venv
        v = getLocalVerifier("prepare_venv_test");
        v.setAutoclean(false);
        v.addCliArguments("generate-resources");
        v.execute();
        List<String> lines = v.loadFile(v.getBasedir(), v.getLogFileName(), false);
        assertTrue(lines.stream().allMatch(l -> !l.contains("-m venv") && !l.contains("-m ensurepip") && !l.contains("termcolor")));
    }
}
