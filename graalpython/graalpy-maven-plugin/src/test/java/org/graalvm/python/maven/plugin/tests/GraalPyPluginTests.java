package org.graalvm.python.maven.plugin.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.BeforeAll;

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
}
