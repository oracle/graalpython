# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import os
import tempfile
import unittest
import shutil
import sys
from tests.standalone import util

class MavenPluginTest(util.PolyglotAppTestBase):

    def generate_app(self, tmpdir, target_dir, target_name, pom_template=None):
        cmd = util.GLOBAL_MVN_CMD + [
            "archetype:generate",
            "-B",
            f"-DarchetypeGroupId={self.archetypeGroupId}",
            f"-DarchetypeArtifactId={self.archetypeArtifactId}",
            f"-DarchetypeVersion={self.graalvmVersion}",
            f"-DartifactId={target_name}",
            "-DgroupId=archetype.it",
            "-Dpackage=it.pkg",
            "-Dversion=0.1-SNAPSHOT",
        ]
        out, return_code = util.run_cmd(cmd, self.env, cwd=str(tmpdir))
        util.check_ouput("BUILD SUCCESS", out)

        if pom_template:
            shutil.copyfile(pom_template, os.path.join(target_dir, "pom.xml"))

        util.patch_pom_repositories(os.path.join(target_dir, "pom.xml"))

        mvnw_dir = os.path.join(os.path.dirname(__file__), "mvnw")
        shutil.copy(os.path.join(mvnw_dir, "mvnw"), os.path.join(target_dir, "mvnw"))
        shutil.copy(os.path.join(mvnw_dir, "mvnw.cmd"), os.path.join(target_dir, "mvnw.cmd"))
        shutil.copytree(os.path.join(mvnw_dir, ".mvn"), os.path.join(target_dir, ".mvn"))
        util.patch_properties_file(os.path.join(target_dir, ".mvn", "wrapper", "maven-wrapper.properties"), self.env.get("MAVEN_DISTRIBUTION_URL_OVERRIDE"))

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_generated_app(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "generated_app_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # build
            cmd = mvnw_cmd + ["package", "-Pnative", "-DmainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # check fileslist.txt
            fl_path = os.path.join(target_dir, "target", "classes", util.VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                lines = f.readlines()
            assert "/" + util.VFS_PREFIX + "/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + util.VFS_PREFIX + "/home/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + util.VFS_PREFIX + "/home/lib-graalpython/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + util.VFS_PREFIX + "/home/lib-python/\n" in lines, "unexpected output from " + str(cmd)

            # execute and check native image
            cmd = [os.path.join(target_dir, "target", target_name)]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("hello java", out)

            # 2.) check java build and exec
            # run with java asserts on
            if self.env.get("MAVEN_OPTS"):
                self.env["MAVEN_OPTS"] = self.env.get("MAVEN_OPTS") + " -ea -esa"
            else:
                self.env["MAVEN_OPTS"] = "-ea -esa"

            # import struct from python file triggers extract of native extension files in VirtualFileSystem
            hello_src = os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py")
            contents = open(hello_src, 'r').read()
            with open(hello_src, 'w') as f:
                f.write("import struct\n" + contents)

            # rebuild and exec
            cmd = mvnw_cmd + ["package", "exec:java", "-Dexec.mainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("hello java", out)

            #GR-51132 - NoClassDefFoundError when running polyglot app in java mode
            util.check_ouput("java.lang.NoClassDefFoundError", out, False)

            # move app to another folder
            # this will break launcher symlinks, but should be able to recover from that
            target_dir2 = os.path.join(str(tmpdir), target_name + ".2")
            os.rename(target_dir, target_dir2)
            mvnw_cmd2 = util.get_mvn_wrapper(target_dir2, self.env)
            # adding new dep triggers launcher without venv regen
            util.replace_in_file(os.path.join(target_dir2, "pom.xml"), "<packages>", "<packages>\n<package>ujson</package>")
            cmd = mvnw_cmd2 + ["package", "exec:java", "-Dexec.mainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir2)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Deleting GraalPy venv due to changed launcher path", out)
            util.check_ouput("hello java", out)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_generated_app_external_resources(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "generated_app_ext_resources_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            # patch project to use external directory for resources
            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)
            src_dir = os.path.join(resources_dir, "src")
            os.makedirs(src_dir, exist_ok=True)
            # copy hello.py
            shutil.copyfile(os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py"), os.path.join(src_dir, "hello.py"))
            shutil.rmtree(os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs"))
            # patch GraalPy.java
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                "package it.pkg;",
                "package it.pkg;\nimport java.nio.file.Path;")
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                "GraalPyResources.createContext()",
                "GraalPyResources.contextBuilder(Path.of(\"" + (resources_dir if "win32" != sys.platform else resources_dir.replace("\\", "\\\\")) + "\")).build()")

            # patch pom.xml
            util.replace_in_file(os.path.join(target_dir, "pom.xml"),
                "<packages>",
                "<pythonResourcesDirectory>${project.basedir}/python-resources</pythonResourcesDirectory>\n<packages>")

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # build
            cmd = mvnw_cmd + ["package", "-Pnative", "-DmainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # execute and check native image
            cmd = [os.path.join(target_dir, "target", target_name)]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("hello java", out)

            # 2.) check java build and exec
            # run with java asserts on
            if self.env.get("MAVEN_OPTS"):
                self.env["MAVEN_OPTS"] = self.env.get("MAVEN_OPTS") + " -ea -esa"
            else:
                self.env["MAVEN_OPTS"] = "-ea -esa"

            # build and exec
            cmd = mvnw_cmd + ["package", "exec:java", "-Dexec.mainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("hello java", out)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_fail_without_graalpy_dep(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "fail_without_graalpy_dep_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "fail_without_graalpy_dep_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("Missing GraalPy dependency. Please add to your pom either org.graalvm.polyglot:python-community or org.graalvm.polyglot:python", out)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_gen_launcher_and_venv(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "gen_launcher_and_venv_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "prepare_venv_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out)
            util.check_ouput("-m ensurepip",out)
            util.check_ouput("ujson", out)
            util.check_ouput("termcolor", out)

            # run again and assert that we do not regenerate the venv
            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("ujson", out, False)
            util.check_ouput("termcolor", out, False)

            # remove ujson pkg from plugin config and check if unistalled
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "<package>ujson</package>", "")

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("Uninstalling ujson", out)
            util.check_ouput("termcolor", out, False)

    def check_tagfile(self, target_dir, expected):
        with open(os.path.join(target_dir, "target", "classes", util.VFS_PREFIX, "home", "tagfile")) as f:
            lines = f.readlines()
        assert lines == expected, "expected tagfile " + str(expected) + ", but got " + str(lines)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_check_home(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "check_home_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "check_home_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # 1. process-resources with no pythonHome config
            process_resources_cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out)

            home_dir = os.path.join(target_dir, "target", "classes", util.VFS_PREFIX, "home")
            assert os.path.exists(home_dir)
            assert os.path.exists(os.path.join(home_dir, "lib-graalpython"))
            assert os.path.isdir(os.path.join(home_dir, "lib-graalpython"))
            assert os.path.exists(os.path.join(home_dir, "lib-python"))
            assert os.path.isdir(os.path.join(home_dir, "lib-python"))
            assert os.path.exists(os.path.join(home_dir, "tagfile"))
            assert os.path.isfile(os.path.join(home_dir, "tagfile"))
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            # 2. process-resources with empty pythonHome includes and excludes
            shutil.copyfile(pom_template, os.path.join(target_dir, "pom.xml"))
            util.patch_pom_repositories(os.path.join(target_dir, "pom.xml"))
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", "<pythonHome></pythonHome></configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out, False)
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            shutil.copyfile(pom_template, os.path.join(target_dir, "pom.xml"))
            util.patch_pom_repositories(os.path.join(target_dir, "pom.xml"))
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", "<pythonHome><includes></includes><excludes></excludes></pythonHome></configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out, False)
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            shutil.copyfile(pom_template, os.path.join(target_dir, "pom.xml"))
            util.patch_pom_repositories(os.path.join(target_dir, "pom.xml"))
            home_tag = """
                        <pythonHome>
                            <includes>
                                <include></include>
                                <include> </include>
                            </includes>
                            <excludes>
                                <exclude></exclude>
                                <exclude> </exclude>
                            </excludes>
                        </pythonHome>
                    """
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", home_tag + "</configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out, False)
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            # 3. process-resources with pythonHome includes and excludes
            home_tag = """
                <pythonHome>
                    <includes>
                        <include>.*__init__\.py</include>
                    </includes>
                    <excludes>
                        <exclude>.*html/__init__\.py</exclude>
                    </excludes>
                </pythonHome>
            """
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", home_tag + "</configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Deleting GraalPy home due to changed includes or excludes", out)
            util.check_ouput("Copying std lib to ", out)
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*__init__\\.py\n', 'exclude:.*html/__init__\\.py\n'])

            # 4. check fileslist.txt
            fl_path = os.path.join(target_dir, "target", "classes", util.VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                for line in f:
                    line = f.readline()
                    # string \n
                    line = line[:len(line)-1]
                    if not line.startswith("/" + util.VFS_PREFIX + "/home/") or line.endswith("/"):
                        continue
                    assert line.endswith("/__init__.py")
                    assert not line.endswith("html/__init__.py")

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_empty_packages(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "empty_packages_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "check_packages_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</packages>", "<package></package><package> </package></packages>")

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

unittest.skip_deselected_test_functions(globals())
