package org.graalvm.python.maven.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.file.Path;

import org.apache.maven.shared.verifier.VerificationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class TestArchetypeWithNativeImage extends GraalPyPluginTests {
    @Test
    public void archetypeWithNativeImage() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        // build
        var v = getLocalVerifier("archetype_ni_test");
        v.addCliArguments("package");
        v.execute();
        v.verifyTextInLog("-m venv");
        v.verifyTextInLog("-m ensurepip");
        v.verifyTextInLog("termcolor");
        v.verifyTextInLog("BUILD SUCCESS");

        // run
        v = getLocalVerifier("archetype_ni_test");
        v.setAutoclean(false);
        v.addCliArguments("-Pnative", "-DmainClass=GraalPy", "-DimageName=graalpy", "package");
        try {
            v.execute();
        } catch (VerificationException e) {
            v.verifyTextInLog("is not a GraalVM distribution");
            Assumptions.assumeTrue(false);
        }

        Process p = new ProcessBuilder(Path.of(v.getBasedir(), "target", "graalpy").toAbsolutePath().toString()).start();
        var buffer = new char[8192];
        var sb = new StringBuilder();
        try (var in = new InputStreamReader(p.getInputStream(), "UTF-8")) {
            while (true) {
                int size = in.read(buffer, 0, buffer.length);
                if (size < 0) {
                    break;
                }
                sb.append(buffer, 0, size);
            }
        }
        p.waitFor();
        var output = sb.toString();
        assertTrue(output.contains("/graalpy_vfs/home/lib/python3.10"));
        assertTrue(output.contains("/graalpy_vfs/home/lib/graalpy23.1/modules"));
        assertTrue(output.contains("/graalpy_vfs/venv/lib/python3.10/site-packages"));
    }
}
