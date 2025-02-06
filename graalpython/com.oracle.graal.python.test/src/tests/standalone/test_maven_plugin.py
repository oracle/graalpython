# Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import pathlib
import re
import shutil
import sys
import textwrap
import unittest

from tests.standalone import util
from tests.standalone.util import TemporaryTestDirectory, Logger

MISSING_FILE_WARNING = "Some python dependencies were installed in addition to packages declared in graalpy-maven-plugin configuration"
WRONG_PACKAGE_VERSION_FORMAT = "Some python packages in graalpy-maven-plugin configuration have no exact version declared"
PACKAGES_INCONSISTENT_ERROR = "some packages in graalpy-maven-plugin configuration are either missing in requirements file or have a different version"
VENV_UPTODATE = "Virtual environment is up to date with requirements file, skipping install"

class MavenPluginTest(util.BuildToolTestBase):

    def generate_app(self, tmpdir, target_dir, target_name, pom_template=None, group_id="archetype.it", package="it.pkg"):
        cmd = util.GLOBAL_MVN_CMD + [
            "archetype:generate",
            "-B",
            f"-DarchetypeGroupId={self.archetypeGroupId}",
            f"-DarchetypeArtifactId={self.archetypeArtifactId}",
            f"-DarchetypeVersion={self.graalvmVersion}",
            f"-DartifactId={target_name}",
            f"-DgroupId={group_id}",
            f"-Dpackage={package}",
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
        util.override_maven_properties_file(target_dir)

        meta_inf_native_image_dir = os.path.join(target_dir, "src", "main", "resources", "META-INF", "native-image")
        os.makedirs(meta_inf_native_image_dir, exist_ok=True)
        shutil.copy(os.path.join(os.path.dirname(__file__), "native-image.properties"), os.path.join(meta_inf_native_image_dir, "native-image.properties"))

    def check_generated_app(self, use_default_vfs_path, use_utils_pkg=False):
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "generated_app_test"
            if use_default_vfs_path:
                target_name += "_default_vfs_path"

            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            vfs_prefix = os.path.join('GRAALPY-VFS', 'archetype.it', target_name)
            graalpy_main_src = os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java")
            if use_default_vfs_path:
                util.replace_in_file(os.path.join(target_dir, "pom.xml"),
                                     "<resourceDirectory>GRAALPY-VFS/${project.groupId}/${project.artifactId}</resourceDirectory>", "")
                util.replace_in_file(graalpy_main_src,
                                    '.resourceDirectory("GRAALPY-VFS/archetype.it/generated_app_test_default_vfs_path")', "")
                resources_dir = os.path.join(target_dir, 'src', 'main', 'resources')
                shutil.move(os.path.join(resources_dir, vfs_prefix), os.path.join(resources_dir, util.DEFAULT_VFS_PREFIX))
                vfs_prefix = util.DEFAULT_VFS_PREFIX

            if use_utils_pkg:
                assert use_default_vfs_path
                util.replace_in_file(graalpy_main_src,
                                     "import org.graalvm.python.embedding.GraalPyResources;",
                                     "import org.graalvm.python.embedding.utils.GraalPyResources;")
                util.replace_in_file(graalpy_main_src,
                                     "import org.graalvm.python.embedding.VirtualFileSystem;",
                                     "import org.graalvm.python.embedding.utils.VirtualFileSystem;")

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # build
            cmd = mvnw_cmd + ["package", "-Pnative", "-DmainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Virtual filesystem is deployed to default resources directory", out, contains=use_default_vfs_path)
            util.check_ouput("This can cause conflicts if used with other Java libraries that also deploy GraalPy virtual filesystem.", out, contains=use_default_vfs_path)

            # check fileslist.txt
            fl_path = os.path.join(target_dir, "target", "classes", vfs_prefix, "fileslist.txt")
            with open(fl_path) as f:
                lines = f.readlines()
            vfs_prefix_in_res = vfs_prefix.replace("\\", "/")
            assert "/" + vfs_prefix_in_res + "/\n" in lines, "'/" + vfs_prefix_in_res + "/' not found in: \n\n" + ''.join(lines)

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
            hello_src = os.path.join(target_dir, "src", "main", "resources", vfs_prefix, "src", "hello.py")
            with open(hello_src) as f:
                contents = f.read()
            with open(hello_src, 'w') as f:
                f.write("import struct\n" + contents)

            # rebuild and exec
            cmd = mvnw_cmd + ["package", "exec:java", "-Dexec.mainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("hello java", out)

            if use_utils_pkg:
                return # make that test bit less extensive

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
    def test_generated_app(self):
        self.check_generated_app(use_default_vfs_path=False)

    @unittest.skipUnless(util.is_maven_plugin_long_running_test_enabled, "ENABLE_MAVEN_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_generated_app_with_default_vfs_path(self):
        self.check_generated_app(use_default_vfs_path=True)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_generated_app_utils_pkg(self):
        self.check_generated_app(use_default_vfs_path=True, use_utils_pkg=True)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_freeze_requirements(self):
        with util.TemporaryTestDirectory() as tmpdir:

            target_name = "test_freeze_requirements"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)
            # run with java asserts on
            if self.env.get("MAVEN_OPTS"):
                self.env["MAVEN_OPTS"] = self.env.get("MAVEN_OPTS") + " -ea -esa"
            else:
                self.env["MAVEN_OPTS"] = "-ea -esa"

            # start with requests package without version
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "termcolor==2.2", "requests")

            # build
            cmd = mvnw_cmd + ["package", "-DrequirementsFile=test-requirements.txt"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("pip install", out)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert not os.path.exists(os.path.join(target_dir, "test-requirements.txt"))

            # freeze - fails due to no version
            cmd = mvnw_cmd + ["org.graalvm.python:graalpy-maven-plugin:freeze-installed-packages", "-DrequirementsFile=test-requirements.txt"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)            
            util.check_ouput(WRONG_PACKAGE_VERSION_FORMAT, out)
            assert not os.path.exists(os.path.join(target_dir, "test-requirements.txt"))

            # freeze with correct version
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "requests", "requests==2.32.3")
            cmd = mvnw_cmd + ["org.graalvm.python:graalpy-maven-plugin:freeze-installed-packages", "-DrequirementsFile=test-requirements.txt"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=True)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-requirements.txt"))

            # add termcolor and build - fails as it is not part of requirements
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</packages>", "<package>termcolor==2.2</package>\n</packages>")
            cmd = mvnw_cmd + ["package", "-DrequirementsFile=test-requirements.txt"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput(PACKAGES_INCONSISTENT_ERROR, out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-requirements.txt"))

            # freeze with termcolor
            # stop using requirementsFile system property but test also with field in pom.xml
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</packages>", "</packages>\n<requirementsFile>test-requirements.txt</requirementsFile>")
            cmd = mvnw_cmd + ["org.graalvm.python:graalpy-maven-plugin:freeze-installed-packages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=True)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-requirements.txt"))

            # rebuild with requirements and exec
            cmd = mvnw_cmd + ["package", "exec:java", "-Dexec.mainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput(VENV_UPTODATE, out)
            util.check_ouput("hello java", out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)

            # disable packages config in pom - run with no packages, only requirements file
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "<packages>", "<!--<packages>")
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</packages>", "</packages>-->")

            # should be able to import requests if installed
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                "import hello",
                "import requests; import hello")

            # clean and rebuild with requirements and exec
            cmd = mvnw_cmd + ["clean", "package", "exec:java", "-Dexec.mainClass=it.pkg.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("pip install", out)
            util.check_ouput("hello java", out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_generated_app_external_resources(self):
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "generated_app_ext_resources_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            # patch project to use external directory for resources
            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)
            src_dir = os.path.join(resources_dir, "src")
            os.makedirs(src_dir, exist_ok=True)
            # copy hello.py
            gen_vfs_prefix = os.path.join('GRAALPY-VFS', 'archetype.it', target_name)
            shutil.copyfile(os.path.join(target_dir, "src", "main", "resources", gen_vfs_prefix, "src", "hello.py"), os.path.join(src_dir, "hello.py"))
            shutil.rmtree(os.path.join(target_dir, "src", "main", "resources", gen_vfs_prefix))
            # patch GraalPy.java
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                "package it.pkg;",
                "package it.pkg;\nimport java.nio.file.Path;")
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                 f'VirtualFileSystem vfs = VirtualFileSystem.newBuilder().resourceDirectory("GRAALPY-VFS/archetype.it/{target_name}").build();', "")
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                "GraalPyResources.contextBuilder(vfs).build()",
                "GraalPyResources.contextBuilder(Path.of(\"" + (resources_dir if "win32" != sys.platform else resources_dir.replace("\\", "\\\\")) + "\")).build()")

            # patch pom.xml
            util.replace_in_file(os.path.join(target_dir, "pom.xml"),
                "<packages>",
                "<externalDirectory>${project.basedir}/python-resources</externalDirectory>\n<packages>")

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
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "fail_without_graalpy_dep_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "fail_without_graalpy_dep_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("Missing GraalPy dependency. Please add to your pom either org.graalvm.polyglot:python-community or org.graalvm.polyglot:python", out)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_check_home_warning(self):
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "check_home_warning_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "check_home_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # 1. process-resources with no pythonHome config - no warning printed
            process_resources_cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("the python language home is always available", out, contains=False)

            # 2. process-resources with pythonHome - warning printed
            shutil.copyfile(pom_template, os.path.join(target_dir, "pom.xml"))
            util.patch_pom_repositories(os.path.join(target_dir, "pom.xml"))
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", "<pythonHome></pythonHome></configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("the python language home is always available", out, contains=True)

    @unittest.skipUnless(util.is_maven_plugin_long_running_test_enabled, "ENABLE_MAVEN_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_check_home(self):
        with TemporaryTestDirectory() as tmpdir:
            target_name = "check_home_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            # 1. native image with include all
            log = Logger()
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "<fallback>false</fallback>",
                                 textwrap.dedent("""<systemProperties>
                                    <org.graalvm.python.resources.include>.*</org.graalvm.python.resources.include>
                                    <org.graalvm.python.resources.log>true</org.graalvm.python.resources.log>
                                 </systemProperties>
                                 <fallback>false</fallback>"""))

            cmd = mvnw_cmd + ["package", "-Pnative"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = [os.path.join(target_dir, "target", target_name)]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("hello java", out, logger=log)

            # 2. native image with bogus include -> nothing included, graalpy won't even start
            log = Logger()
            bogus_include = "<org.graalvm.python.resources.include>bogus-include</org.graalvm.python.resources.include>"
            util.replace_in_file(os.path.join(target_dir, "pom.xml"),
                                 "<org.graalvm.python.resources.include>.*</org.graalvm.python.resources.include>",
                                 bogus_include)

            cmd = mvnw_cmd + ["package", "-Pnative"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = [os.path.join(target_dir, "target", target_name)]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("could not determine Graal.Python's core path", out, logger=log)

            # 3. native image with excluded email package -> graalpy starts but no module named 'email'
            log = Logger()
            util.replace_in_file(os.path.join(target_dir, "pom.xml"), bogus_include,
                textwrap.dedent(f"""<org.graalvm.python.resources.include>.*</org.graalvm.python.resources.include>
                                    <org.graalvm.python.resources.exclude>.*/email/.*</org.graalvm.python.resources.exclude>
                                    <org.graalvm.python.resources.log>true</org.graalvm.python.resources.log>"""))
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "it", "pkg", "GraalPy.java"),
                "import hello",
                "import email; import hello")

            cmd = mvnw_cmd + ["package", "-Pnative"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = [os.path.join(target_dir, "target", target_name)]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("No module named 'email'", out, logger=log)

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_empty_packages(self):
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "empty_packages_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "check_packages_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = mvnw_cmd + ["-X", "org.graalvm.python:graalpy-maven-plugin:freeze-installed-packages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput("In order to run the freeze-installed-packages goal there have to be python packages declared in the graalpy-maven-plugin configuration", out)
            assert not os.path.exists(os.path.join(target_dir, "requirements.txt"))

            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "</packages>", "<package></package><package> </package></packages>")

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = mvnw_cmd + ["org.graalvm.python:graalpy-maven-plugin:freeze-installed-packages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput("In order to run the freeze-installed-packages goal there have to be python packages declared in the graalpy-maven-plugin configuration", out)
            assert not os.path.exists(os.path.join(target_dir, "requirements.txt"))

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_python_resources_dir_deprecation(self):
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "python_res_dir_deprecation_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)

            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "org.graalvm.polyglot", "org.graalvm.python")

            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "<packages>",
                                 "<pythonResourcesDirectory>${project.basedir}/python-resources</pythonResourcesDirectory>"
                                 "<packages>")

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["package"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            assert return_code == 0
            util.check_ouput("Option <pythonResourcesDirectory> is deprecated and will be removed. Use <externalDirectory> instead", out)
            util.check_ouput("BUILD SUCCESS", out)
            assert os.path.exists(os.path.join(resources_dir, 'venv'))


    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_python_resources_dir_and_external_dir_error(self):
        with util.TemporaryTestDirectory() as tmpdir:
            target_name = "python_res_dir_deprecation_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)

            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "org.graalvm.polyglot", "org.graalvm.python")

            util.replace_in_file(os.path.join(target_dir, "pom.xml"), "<packages>",
                                 "<pythonResourcesDirectory>${project.basedir}/python-resources</pythonResourcesDirectory>"
                                 "<externalDirectory>${project.basedir}/python-resources</externalDirectory>"
                                 "<packages>")

            mvnw_cmd = util.get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["package"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            assert return_code != 0
            util.check_ouput("Cannot use <externalDirectory> and <resourceDirectory> at the same time", out)


    # Creates two Java apps from given pom templates, the dependencies between them
    # must be configured in the pom template(s)
    def _prepare_multi_project(self, tmpdir, app1_pom_template_name, app2_pom_template_name):
        app1_dir = os.path.join(str(tmpdir), "app1")
        pom_template = os.path.join(os.path.dirname(__file__), app1_pom_template_name)
        self.generate_app(tmpdir, app1_dir, "app1", pom_template, group_id="org.graalvm.python.tests", package="app1")

        app2_dir = os.path.join(str(tmpdir), "app2")
        pom_template = os.path.join(os.path.dirname(__file__), app2_pom_template_name)
        self.generate_app(tmpdir, app2_dir, "app2", pom_template, group_id="org.graalvm.python.tests", package="app2")

        app1_mvnw_cmd = util.get_mvn_wrapper(app1_dir, self.env)
        app2_mvnw_cmd = util.get_mvn_wrapper(app2_dir, self.env)

        for (app_dir, pkg) in [(app1_dir, "app1"), (app2_dir, "app2")]:
            graalpy_main_src = os.path.join(app_dir, "src", "main", "java", pkg, "GraalPy.java")
            util.replace_in_file(graalpy_main_src,
                         f'.resourceDirectory("GRAALPY-VFS/org.graalvm.python.tests/{pkg}")', "")
            vfs_prefix = os.path.join('GRAALPY-VFS', 'org.graalvm.python.tests', pkg)
            resources_dir = os.path.join(app_dir, 'src', 'main', 'resources')
            shutil.move(os.path.join(resources_dir, vfs_prefix), os.path.join(resources_dir, util.DEFAULT_VFS_PREFIX))

        return app1_dir, app1_mvnw_cmd, app2_dir, app2_mvnw_cmd

    @unittest.skipUnless(util.is_maven_plugin_long_running_test_enabled, "ENABLE_MAVEN_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_multiple_merged_vfs(self):
        with util.TemporaryTestDirectory() as tmpdir:
            # Setup: app2 depends on app1
            #        app1 depends on termcolor and in it user script hello.py it calls termcolor
            #        app2 depends on ujson, calls app1's hello script and ujson directly
            app1_dir, app1_mvnw_cmd, app2_dir, app2_mvnw_cmd = \
                self._prepare_multi_project(tmpdir,
                                            "prepare_venv_termcolor_pom.xml",
                                            "merged_vfs_ujson_pom.xml")

            out, return_code = util.run_cmd(app1_mvnw_cmd + ['package', 'install'], self.env, cwd=app1_dir)
            util.check_ouput("BUILD SUCCESS", out)

            out, return_code = util.run_cmd(app2_mvnw_cmd + ['package'], self.env, cwd=app2_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # Using app2 "as is" gives error
            cmd = app2_mvnw_cmd + ["package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput("Found multiple embedded virtual filesystem instances", out)
            assert return_code != 0

            app1_jar_url_regex = re.compile(r"jar:file:.*/org/graalvm/python/tests/app1/1\.0-SNAPSHOT/app1-1\.0-SNAPSHOT\.jar!/")
            found = app1_jar_url_regex.findall(out)
            if not found:
                raise AssertionError(f"Cannot find app1 URL in the output")
            app1_jar_url = found.pop()

            # Allow multiple VFS => error: duplicate entries
            cmd = app2_mvnw_cmd + ["-Dorg.graalvm.python.vfs.allow_multiple=true", "clean", "package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput("There are duplicate entries", out)
            assert return_code != 0

            # Select app1 VFS => works
            cmd = app2_mvnw_cmd + [f"-Dorg.graalvm.python.vfs.root_url={app1_jar_url}",
                                   "clean", "package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput("hello java", out)
            assert return_code == 0

            # Select app2 VFS => termcolor not found
            app2_uri = pathlib.Path(app2_dir).resolve().as_uri()
            app2_uri = app2_uri[len("file://"):]
            if not app2_uri.endswith('/'):
                app2_uri += '/'
            cmd = app2_mvnw_cmd + [f"-Dorg.graalvm.python.vfs.root_url=file:{app2_uri}target/classes/org.graalvm.python.vfs/",
                                   "clean", "package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput("No module named 'termcolor'", out)
            assert return_code != 0

            # Remove hello.py from app2 => now it should all work
            # The app calls termcolor (from app1 package), also add call to ujson (from app2 package)
            os.remove(os.path.join(app2_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py"))
            util.replace_in_file(os.path.join(app2_dir, "src", "main", "java", "app2", "GraalPy.java"),
                                 'hello.hello("java");',
                                 '''
                                 hello.hello("java");
                                 context.eval("python", "import ujson; print(ujson.dumps([{'key': 'value'}, 81, True]))");
                                 ''')

            cmd = app2_mvnw_cmd + ["-Dorg.graalvm.python.vfs.allow_multiple=true", "clean", "package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput('[{"key":"value"},81,true]', out)
            util.check_ouput("hello java", out)
            assert return_code == 0

            cmd = app2_mvnw_cmd + ["-Pnative", "package"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            assert return_code == 0, out

            out, return_code = util.run_cmd([os.path.join(app2_dir, "target", "app2"), "-Dorg.graalvm.python.vfs.allow_multiple=true"], self.env, cwd=app2_dir)
            util.check_ouput('[{"key":"value"},81,true]', out)
            util.check_ouput("hello java", out)
            assert return_code == 0, out

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_multiple_vfs_incompat_libs_error(self):
        with util.TemporaryTestDirectory() as tmpdir:
            app1_dir, app1_mvnw_cmd, app2_dir, app2_mvnw_cmd = \
                self._prepare_multi_project(tmpdir,
                                            "prepare_venv_old_ujson_pom.xml",
                                            "merged_vfs_ujson_pom.xml")

            out, return_code = util.run_cmd(app1_mvnw_cmd + ['package', 'install'], self.env, cwd=app1_dir)
            util.check_ouput("BUILD SUCCESS", out)

            out, return_code = util.run_cmd(app2_mvnw_cmd + ['package'], self.env, cwd=app2_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # Allow multiple VFS sources
            os.remove(os.path.join(app2_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py"))

            cmd = app2_mvnw_cmd + ["-Dorg.graalvm.python.vfs.allow_multiple=true", "clean", "package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput("Package 'ujson' is installed in different versions", out)
            util.check_ouput("5.10.0", out)
            util.check_ouput("5.9.0", out)
            assert return_code != 0


    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def test_multiple_namespaced_vfs(self):
        with util.TemporaryTestDirectory() as tmpdir:
            # Setup: app2 with default vfs location depends on app1, which has namespaced vfs
            # We should be able to load and execute the "hello.py" script from both libraries within one Java app
            app1_dir, app1_mvnw_cmd, app2_dir, app2_mvnw_cmd = \
                self._prepare_multi_project(tmpdir,
                                            "namespaced_venv.xml",
                                            "namespaced_venv_user.xml")

            util.replace_main_body(os.path.join(app2_dir, "src", "main", "java", "app2", "GraalPy.java"),
                             """
                            org.graalvm.python.embedding.VirtualFileSystem vfs =
                              org.graalvm.python.embedding.VirtualFileSystem.newBuilder()
                                    .resourceDirectory("GRAALPY-VFS/org.graalvm.python.tests/app1")
                                    .build();
                            try (Context context1 = GraalPyResources.createContext();
                                 Context context2 = GraalPyResources.contextBuilder(vfs).build()) {
                                int index = 0;
                                for (Context ctx: new Context[] {context1, context2}) {
                                    ctx.eval("python", "import hello");
                                    Value pyHelloClass = ctx.getPolyglotBindings().getMember("PyHello");
                                    Value pyHello = pyHelloClass.newInstance();
                                    Hello hello = pyHello.as(Hello.class);
                                    System.out.print("" + index++ + ": ");
                                    hello.hello("java");
                                }
                            }
                            """)

            util.replace_in_file(os.path.join(app2_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py"),
                                 'colored("hello "', 'colored("Hi there "')

            vfs_parent_dir = os.path.join(app1_dir, "src", "main", "resources", "GRAALPY-VFS", "org.graalvm.python.tests")
            os.makedirs(vfs_parent_dir, exist_ok=True)
            shutil.move(os.path.join(app1_dir, "src", "main", "resources", "org.graalvm.python.vfs"), os.path.join(vfs_parent_dir, "app1"))

            out, return_code = util.run_cmd(app1_mvnw_cmd + ['package', 'install'], self.env, cwd=app1_dir)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = app2_mvnw_cmd + ["package", "exec:java", "-Dexec.mainClass=app2.GraalPy"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            util.check_ouput("0: Hi there java", out)
            util.check_ouput("1: hello java", out)
            assert return_code == 0, out

            cmd = app2_mvnw_cmd + ["-Pnative", "package"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=app2_dir)
            assert return_code == 0, out

            out, return_code = util.run_cmd(os.path.join(app2_dir, "target", "app2"), self.env, cwd=app2_dir)
            util.check_ouput("0: Hi there java", out)
            util.check_ouput("1: hello java", out)
            assert return_code == 0, out