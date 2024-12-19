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
import shutil
import sys
import textwrap
import unittest

from tests.standalone import util
from tests.standalone.util import TemporaryTestDirectory, Logger

def append(file, txt):
    with open(file, "a") as f:
        f.write(txt)

def _community_as_property(community):
    return 'community = true' if community else ''

class GradlePluginTestBase(util.BuildToolTestBase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.test_prj_path = os.path.join(os.path.dirname(__file__), "gradle", "gradle-test-project")

    def target_dir_name_sufix(self, target_dir):
        pass

    def copy_build_files(self, target_dir):
        pass

    def empty_plugin(self, community):
        pass

    def packages_termcolor(self, community):
        pass

    def packages_termcolor_ujson(self, community):
        pass

    def packages_termcolor_resource_dir(self, resources_dir):
        pass

    def graalpy_python_home_includes(self, community):
        pass

    def empty_packages(self):
        pass

    def native_image_with_include(self):
        pass

    def native_image_with_bogus_include(self):
        pass

    def native_image_with_exlude_email(self):
        pass

    def generate_app(self, target_dir):
        shutil.copytree(self.test_prj_path, target_dir)
        for root, dirs, files in os.walk(target_dir):
            for file in files:
                if file.endswith(".j"):
                    shutil.move(os.path.join(root, file), os.path.join(root, file[0:len(file)- 1] + "java"))

        util.override_gradle_properties_file(target_dir)
        self.copy_build_files(target_dir)

        # at the moment the gradle demon does not run with jdk <= 22
        gradle_java_home = self.env.get("GRADLE_JAVA_HOME")
        assert gradle_java_home, "in order to run standalone gradle tests, the 'GRADLE_JAVA_HOME' env var has to be set to a jdk <= 22"
        util.replace_in_file(os.path.join(target_dir, "gradle.properties"), "{GRADLE_JAVA_HOME}", gradle_java_home.replace("\\", "\\\\"))

    def check_filelist(self, target_dir, log):
        fl_path = os.path.join(target_dir, "build", "resources", "main", util.VFS_PREFIX, "fileslist.txt")
        with open(fl_path) as f:
            lines = f.readlines()

        log.log_block("filelist.txt", ''.join(lines))
        assert "/" + util.VFS_PREFIX + "/\n" in lines, log

    def check_gradle_generated_app(self, community):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "generated_app_gradle" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)
            append(build_file, self.packages_termcolor(community))

            gradlew_cmd = util.get_gradle_wrapper(target_dir, self.env)
            log = Logger()

            # build
            cmd = gradlew_cmd + ["build"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            self.check_filelist(target_dir, log)

            cmd = gradlew_cmd + ["nativeCompile"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            self.check_filelist(target_dir, log)

            # execute and check native image
            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("hello java", out, logger=log)
            self.check_filelist(target_dir, log)

            # import struct from python file triggers extract of native extension files in VirtualFileSystem
            hello_src = os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py")
            with open(hello_src) as f:
                contents = f.read()
            with open(hello_src, 'w') as f:
                f.write("import struct\n" + contents)

            # rebuild and exec
            cmd = gradlew_cmd + ["build", "run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            util.check_ouput("hello java", out, logger=log)
            self.check_filelist(target_dir, log)

            #GR-51132 - NoClassDefFoundError when running polyglot app in java mode
            util.check_ouput("java.lang.NoClassDefFoundError", out, False, logger=log)

            # move app to another folder
            # this will break launcher symlinks, but should be able to recover from that
            target_dir2 = os.path.join(str(tmpdir), "generated_app_gradle.2" + self.target_dir_name_sufix())
            os.rename(target_dir, target_dir2)
            # adding new dep triggers launcher without venv regen
            self.copy_build_files(target_dir2)
            build_file2 = os.path.join(target_dir2, self.build_file_name)
            append(build_file2, self.packages_termcolor_ujson(community))

            gradlew_cmd2 = util.get_gradle_wrapper(target_dir2, self.env)
            cmd = gradlew_cmd2 + ["build", "run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir2, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            util.check_ouput("hello java", out, logger=log)
            self.check_filelist(target_dir2, log)

    def check_gradle_generated_app_external_resources(self):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "generated_gradle_app_external_resources" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            # patch project to use external directory for resources
            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)
            src_dir = os.path.join(resources_dir, "src")
            os.makedirs(src_dir, exist_ok=True)
            # copy hello.py
            shutil.copyfile(os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py"), os.path.join(src_dir, "hello.py"))
            # remove all resources, we also want to check if the gradle plugin can deal with no user resources
            shutil.rmtree(os.path.join(target_dir, "src", "main", "resources"))
            # patch GraalPy.java
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "org", "example", "GraalPy.java"),
                "package org.example;",
                "package org.example;\nimport java.nio.file.Path;")
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "org", "example", "GraalPy.java"),
                "GraalPyResources.createContext()",
                "GraalPyResources.contextBuilder(Path.of(\"" + (resources_dir if "win32" != sys.platform else resources_dir.replace("\\", "\\\\")) + "\")).build()")

            # patch build.gradle
            append(build_file, self.packages_termcolor_resource_dir(resources_dir))

            # build
            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)

            cmd = gradle_cmd + ["clean", "build"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # check java exec
            cmd = gradle_cmd + ["run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("hello java", out)

            # prepare for native build
            meta_inf = os.path.join(target_dir, "src", "main", "resources", "META-INF", "native-image")
            os.makedirs(meta_inf, exist_ok=True)
            shutil.copyfile(os.path.join(self.test_prj_path, "src", "main", "resources", "META-INF", "native-image", "proxy-config.json"), os.path.join(meta_inf, "proxy-config.json"))

            cmd = gradle_cmd + ["nativeCompile"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # execute and check native image
            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("hello java", out)

    def check_gradle_fail_with_mismatching_graalpy_dep(self):
        pass # TODO: once the CI job builds enterprise

    def check_gradle_gen_launcher_and_venv(self, community):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "gradle_gen_launcher_and_venv" + self.target_dir_name_sufix())
            self.generate_app(target_dir)

            build_file = os.path.join(target_dir, self.build_file_name)

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)

            append(build_file, self.packages_termcolor_ujson(community))

            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out)
            util.check_ouput("-m ensurepip",out)
            util.check_ouput("ujson", out)
            util.check_ouput("termcolor", out)

            # run again and assert that we do not regenerate the venv
            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("ujson", out, False)
            util.check_ouput("termcolor", out, False)

            # remove ujson pkg from plugin config and check if unistalled
            self.copy_build_files(target_dir)
            append(build_file, self.packages_termcolor(community))

            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("Uninstalling ujson", out)
            util.check_ouput("termcolor", out, False)

    def check_tagfile(self, home, expected, log=''):
        with open(os.path.join(home, "tagfile")) as f:
            lines = f.readlines()
        assert lines == expected, "expected tagfile " + str(expected) + ", but got " + str(lines) + log

    def check_gradle_check_home_warning(self, community):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "check_home_test_warning" + self.target_dir_name_sufix())
            self.generate_app(target_dir)

            build_file = os.path.join(target_dir, self.build_file_name)

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)
            process_resources_cmd = gradle_cmd + ["--stacktrace", "graalPyResources"]

            # 1. process-resources with no pythonHome config - no warning printed
            log = Logger()
            append(build_file, self.empty_plugin(community))
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            util.check_ouput("the python language home is always available", out, contains=False, logger=log)

            # 2. process-resources with pythonHome includes and excludes - warning printed
            log = Logger()
            self.copy_build_files(target_dir)
            append(build_file, self.graalpy_python_home_includes(community))
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            util.check_ouput("the python language home is always available", out, contains=True, logger=log)

    def check_gradle_check_home(self, community):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "check_home_test" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)

            # 1. native image with include all
            log = Logger()
            append(build_file, self.packages_termcolor(community))
            append(build_file, self.native_image_with_include())
            cmd = gradle_cmd + ["--stacktrace", "build", "nativeCompile"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)

            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("hello java", out, logger=log)

            # 2. native image with bogus include -> nothing included, graalpy won't even start
            log = Logger()
            self.copy_build_files(target_dir)
            append(build_file, self.packages_termcolor(community))
            append(build_file, self.native_image_with_bogus_include())

            cmd = gradle_cmd + ["--stacktrace", "build", "nativeCompile"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)

            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("could not determine Graal.Python's core path", out, logger=log)

            # 3. native image with excluded email package -> graalpy starts but no module named 'email'
            log = Logger()
            self.copy_build_files(target_dir)
            append(build_file, self.packages_termcolor(community))
            append(build_file, self.native_image_with_bogus_include())
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "org", "example", "GraalPy.java"),
                                 "import hello",
                                 "import email; import hello")

            cmd = gradle_cmd + ["--stacktrace", "build", "nativeCompile"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)

            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("No module named 'email'", out, logger=log)


    def check_gradle_empty_packages(self):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "empty_packages_test" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            append(build_file, self.empty_packages())

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)
            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

class GradlePluginGroovyTest(GradlePluginTestBase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.build_file_name = "build.gradle"
        cls.settings_file_name = "settings.gradle"

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app(self):
        self.check_gradle_generated_app(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app_external_resources(self):
        self.check_gradle_generated_app_external_resources()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_gen_launcher_and_venv(self):
        self.check_gradle_gen_launcher_and_venv(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home_warning(self):
        self.check_gradle_check_home_warning(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home(self):
        self.check_gradle_check_home(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_empty_packages(self):
        self.check_gradle_empty_packages()

    def target_dir_name_sufix(self):
        return "_groovy"

    def copy_build_files(self, target_dir):
        build_file = os.path.join(target_dir, self.build_file_name)
        shutil.copyfile(os.path.join(os.path.dirname(__file__), "gradle", "build", self.build_file_name), build_file)
        settings_file = os.path.join(target_dir, self.settings_file_name)
        shutil.copyfile(os.path.join(os.path.dirname(__file__), "gradle", "build", self.settings_file_name), settings_file)
        if custom_repos := os.environ.get("MAVEN_REPO_OVERRIDE"):
            mvn_repos = ""
            for idx, custom_repo in enumerate(custom_repos.split(",")):
                mvn_repos += f"maven {{ url \"{custom_repo}\" }}\n    "
            util.replace_in_file(build_file,
                "repositories {", f"repositories {{\n    mavenLocal()\n    {mvn_repos}")
            util.replace_in_file(settings_file,
                "repositories {", f"repositories {{\n        {mvn_repos}")

    def empty_plugin(self, community):
        return f"graalPy {{ {_community_as_property(community)} }}"

    def packages_termcolor(self, community):
        return textwrap.dedent(f"""
            graalPy {{
                packages = ["termcolor"]
                {_community_as_property(community)}
            }}
            """)

    def packages_termcolor_ujson(self, community):
        return textwrap.dedent(f"""
            graalPy {{
                packages = ["termcolor", "ujson"]
                {_community_as_property(community)}
            }}
            """)

    def packages_termcolor_resource_dir(self, resources_dir):
        resources_dir = resources_dir if 'win32' != sys.platform else resources_dir.replace("\\", "\\\\")
        return textwrap.dedent(f"""
            graalPy {{
                packages = ["termcolor"]
                pythonResourcesDirectory = file("{resources_dir}")
                community = true
            }}
            """)

    def graalpy_python_home_includes(self, community):
        return textwrap.dedent(f"""
            graalPy {{
                pythonHome {{
                    includes = [".*"]
                }}
                {_community_as_property(community)}
            }}
            """)

    def empty_packages(self):
        return textwrap.dedent("""
            graalPy {
                packages = []
                community = true
            }
            """)

    def native_image_with_include(self):
        return textwrap.dedent("""
            graalvmNative {
                binaries {
                    main {
                        systemProperties = ['org.graalvm.python.resources.include': '.*', 'org.graalvm.python.resources.log': 'true']
                    }
                }
            }
            """)
    def native_image_with_bogus_include(self):
        return textwrap.dedent("""
            graalvmNative {
                binaries {
                    main {
                        systemProperties = ['org.graalvm.python.resources.include': 'bogus-include', 'org.graalvm.python.resources.log': 'true']
                    }
                }
            }
            """)

    def native_image_with_exclude_email(self):
        return textwrap.dedent("""
            graalvmNative {
                binaries {
                    main {
                        systemProperties = ['org.graalvm.python.resources.include': '.*', 'org.graalvm.python.resources.exclude': '.*/email/.*', 'org.graalvm.python.resources.log': 'true']
                    }
                }
            }
            """)

class GradlePluginKotlinTest(GradlePluginTestBase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.build_file_name = "build.gradle.kts"
        cls.settings_file_name = "settings.gradle.kts"

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app(self):
        self.check_gradle_generated_app(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app_external_resources(self):
        self.check_gradle_generated_app_external_resources()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_gen_launcher_and_venv(self):
        self.check_gradle_gen_launcher_and_venv(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home_warning(self):
        self.check_gradle_check_home_warning(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home(self):
        self.check_gradle_check_home(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_empty_packages(self):
        self.check_gradle_empty_packages()

    def target_dir_name_sufix(self):
        return "_kotlin"

    def copy_build_files(self, target_dir):
        build_file = os.path.join(target_dir, self.build_file_name)
        shutil.copyfile(os.path.join(os.path.dirname(__file__), "gradle", "build", self.build_file_name), build_file)
        settings_file = os.path.join(target_dir, self.settings_file_name)
        shutil.copyfile(os.path.join(os.path.dirname(__file__), "gradle", "build", self.settings_file_name), settings_file)
        if custom_repos := os.environ.get("MAVEN_REPO_OVERRIDE"):
            mvn_repos = ""
            for idx, custom_repo in enumerate(custom_repos.split(",")):
                mvn_repos += f"maven(url=\"{custom_repo}\")\n    "

            util.replace_in_file(build_file, "repositories {", f"repositories {{\n    mavenLocal()\n    {mvn_repos}")
            util.replace_in_file(settings_file, "repositories {", f"repositories {{\n        {mvn_repos}")

    def empty_plugin(self, community):
        return f"graalPy {{ {_community_as_property(community) } }}"

    def packages_termcolor(self, community):
       return textwrap.dedent(f"""
            graalPy {{
               packages.add("termcolor")
               {_community_as_property(community)}
            }}
            """)

    def packages_termcolor_ujson(self, community):
        return textwrap.dedent(f"""
            graalPy {{
                packages.add("termcolor")
                packages.add("ujson")
                {_community_as_property(community)}
            }}
            """)

    def packages_termcolor_resource_dir(self, resources_dir):
        resources_dir = resources_dir if 'win32' != sys.platform else resources_dir.replace("\\", "\\\\")
        return textwrap.dedent(f"""
            graalPy {{
                packages.add("termcolor")
                pythonResourcesDirectory = file("{resources_dir}")
                community = true
            }}
            """)

    def graalpy_python_home_includes(self, community):
        return textwrap.dedent(f"""
            graalPy {{
                pythonHome {{
                    includes.add(".*__init__.py")
                }}
                {_community_as_property(community)}
            }}
            """)

    def empty_packages(self):
        return textwrap.dedent("""
            graalPy {
                packages
                community = true
            }
            """)

    def native_image_with_include(self):
        return textwrap.dedent("""
            graalvmNative {
                binaries {
                    named("main") {
                        systemProperties.putAll(mapOf("org.graalvm.python.resources.include" to ".*", "org.graalvm.python.resources.log" to "true"))
                    }
                }
            }
            """)
    def native_image_with_bogus_include(self):
        return textwrap.dedent("""
            graalvmNative {
                binaries {
                    named("main") {
                        systemProperties.putAll(mapOf("org.graalvm.python.resources.include" to "bogus-include", "org.graalvm.python.resources.log" to "true"))
                    }
                }
            }
            """)

    def native_image_with_exclude_email(self):
        return textwrap.dedent("""
            graalvmNative {
                binaries {
                    named("main") {
                        systemProperties.putAll(mapOf("org.graalvm.python.resources.include" to ".*", "org.graalvm.python.resources.include" to ".*/email/.*", "org.graalvm.python.resources.log" to "true"))
                    }
                }
            }
            """)

