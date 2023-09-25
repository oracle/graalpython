package org.graalvm.python.maven.plugin.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GraalPyPluginTests {
    static Verifier getLocalVerifier(String projectDir) throws IOException, VerificationException {
        var v = new Verifier(System.getProperty("user.dir") + "/target/test-classes/" + projectDir);
        v.setLocalRepo(System.getProperty("user.dir") + "/target/local-repo/");
        v.setEnvironmentVariable("MVN", "mvn -Dmaven.repo.local=" + v.getLocalRepository());
        Files.createDirectories(Path.of(v.getLocalRepository()));
        return v;
    }

    static boolean CAN_RUN_TESTS = true;

    @BeforeAll
    public static void installLocally() throws IOException, XmlPullParserException, VerificationException {
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        Model model = mavenreader.read(Files.newInputStream(Path.of(System.getProperty("user.dir") + "/pom.xml")));
        String id = model.getArtifactId();
        String gid = model.getGroupId();
        String pkg = model.getPackaging();
        String ver = model.getVersion();
        Path jar = Path.of(System.getProperty("user.dir") + "/target/" + id + "-" + ver + ".jar");
        Path cls = Path.of(System.getProperty("user.dir") + "/target/classes");
        if (Files.exists(jar) &&
                        Files.walk(cls).allMatch(p -> {
                            try {
                                return Files.getLastModifiedTime(jar).compareTo(Files.getLastModifiedTime(p)) > 0;
                            } catch (IOException e) {
                                return false;
                            }
                        })) {
            var v = getLocalVerifier("setup_tests");
            v.addCliArguments("install:install-file",
                            "-Dfile=" + jar.toString(), "-DgroupId=" + gid,
                            "-DartifactId=" + id, "-Dversion=" + ver,
                            "-Dpackaging=" + pkg, "-DgeneratePom=true",
                            "-DcreateChecksum=true");
            v.execute();
        } else {
            CAN_RUN_TESTS = false;
        }
    }

    @Test
    public void testVfsIndex() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("list_files_test");
        var vfsPath = Path.of(v.getBasedir(), "target", "classes", "vfs");
        v.addCliArguments("process-resources");
        v.execute();
        var vfsList = vfsPath.resolve("fileslist.txt");
        assertTrue(Files.exists(vfsList));
        var lines = new HashSet<String>(Files.readAllLines(vfsList));
        var linesStr = String.join("\n", lines);
        assertTrue(lines.contains("/vfs/"), linesStr);
        assertTrue(lines.contains("/vfs/home/"), linesStr);
        assertTrue(lines.contains("/vfs/home/dir_with_file/"), linesStr);
        assertTrue(lines.contains("/vfs/home/dir_with_file/file.txt"), linesStr);
        assertFalse(lines.contains("/vfs/home/dir_with_file/empty_dir/"), linesStr);
        assertFalse(lines.contains("/vfs/home/empty_dir/"), linesStr);
        assertFalse(lines.contains("/vfs/home/empty_dir/empty_dir/"), linesStr);
        assertEquals(4, lines.size(), linesStr);
    }

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

    @Test
    public void execWithEnvvar() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("exec_test");
        v.addCliArguments("graalpy:exec");
        v.setEnvironmentVariable("GRAAL_PYTHON_ARGS", "\013-c\013print(42, 'from python')");
        v.execute();
        v.verifyTextInLog("42 from python");
    }

    @Test
    public void execWithArgs() throws Exception {
        Assumptions.assumeTrue(CAN_RUN_TESTS);
        var v = getLocalVerifier("exec_test");
        v.addCliArguments("graalpy:exec", "-Dexec.argc=2", "-Dexec.arg1=-c", "-Dexec.arg2=print(42, 'from python')");
        v.execute();
        v.verifyTextInLog("42 from python");
    }

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
