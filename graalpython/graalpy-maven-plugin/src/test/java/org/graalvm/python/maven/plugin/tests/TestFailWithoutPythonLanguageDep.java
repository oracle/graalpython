package org.graalvm.python.maven.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.shared.verifier.VerificationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestFailWithoutPythonLanguageDep extends GraalPyPluginTests {
    @Test
    public void failWithoutPythonLanguageDep() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("fail_no_language_test");
        v.addCliArguments("generate-resources");
        boolean failed = false;
        try {
            v.execute();
        } catch (VerificationException e) {
            failed = true;
        }
        assertTrue(failed);
        v.verifyTextInLog("Missing GraalPy dependency org.graalvm.python:python-language");
    }
}
