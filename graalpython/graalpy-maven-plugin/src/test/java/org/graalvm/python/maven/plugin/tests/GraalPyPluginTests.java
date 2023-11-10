/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
    static Verifier getLocalVerifier(String projectDir) throws IOException, VerificationException, XmlPullParserException {
        Model model = getPluginModel();
        var pp = Path.of(buildDir(model), "test-classes");
        if (!pp.isAbsolute()) {
            pp = Path.of(projectDir(), pp.toString());
        }
        var v = new Verifier(pp.resolve(projectDir).toString());
        v.setLocalRepo(pp.resolve("local-repo").toString());
        v.setEnvironmentVariable("MVN", "mvn -Dmaven.repo.local=" + v.getLocalRepository());
        Files.createDirectories(Path.of(v.getLocalRepository()));
        return v;
    }

    static boolean CAN_RUN_TESTS = true;

    @BeforeAll
    public static void installLocally() throws IOException, XmlPullParserException, VerificationException {
        Model model = getPluginModel();
        String id = model.getArtifactId();
        String gid = model.getGroupId();
        String pkg = model.getPackaging();
        String ver = model.getVersion();
        Path targetPath = Path.of(buildDir(model));
        if (!targetPath.isAbsolute()) {
            targetPath = Path.of(projectDir(), targetPath.toString());
        }
        Path jar = targetPath.resolve(id + "-" + ver + ".jar");
        Path cls = Path.of(buildDir(model), "classes");
        if (!cls.isAbsolute()) {
            cls = Path.of(projectDir(), cls.toString());
        }
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

    private static Model getPluginModel() throws IOException, XmlPullParserException {
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        return mavenreader.read(Files.newInputStream(Path.of(projectDir(), "pom.xml")));
    }

    private static String buildDir(Model model) {
        return Path.of(model.getBuild().getDirectory()
                        .replace("${project.basedir}", projectDir())
                        .replace("${project.artifactId}", model.getArtifactId())).normalize().toString();
    }

    private static String projectDir() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().toString();
    }
}
