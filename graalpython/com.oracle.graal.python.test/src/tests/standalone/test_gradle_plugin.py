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
import util
import sys
import textwrap

def append(file, txt):
    with open(file, "a") as f:
        f.write(txt)

class GradlePluginTestBase(util.PolyglotAppTestBase):
    def setUpClass(self):
        super().setUpClass()
        self.test_prj_path = os.path.join(os.path.dirname(__file__), "gradle", "gradle-test-project")

    def target_dir_name_sufix(self, target_dir):
        pass

    def copy_build_files(self, target_dir):
        pass

    def packages_termcolor(self, build_file):
        pass

    def packages_termcolor_ujson(self):
        pass

    def packages_termcolor_resource_dir(self, resources_dir):
        pass

    def empty_home_includes(self):
        pass

    def home_includes(self):
        pass

    def empty_packages(self):
        pass

    def generate_app(self, target_dir):
        shutil.copytree(self.test_prj_path, target_dir)
        for root, dirs, files in os.walk(target_dir):
            for file in files:
                if file.endswith(".j"):
                    shutil.move(os.path.join(root, file), os.path.join(root, file[0:len(file)- 1] + "java"))

        util.patch_properties_file(os.path.join(target_dir, "gradle", "wrapper", "gradle-wrapper.properties"), self.env.get("GRADLE_DISTRIBUTION_URL_OVERRIDE"))

        self.copy_build_files(target_dir)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "generated_app_gradle" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)
            append(build_file, self.packages_termcolor())

            gradlew_cmd = util.get_gradle_wrapper(target_dir, self.env)

            # build
            cmd = gradlew_cmd + ["build"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)

            cmd = gradlew_cmd + ["nativeCompile"]
            # gradle needs jdk <= 22, but it looks like the 'gradle nativeCompile' cmd does not complain if higher,
            # which is fine, because we need to build the native binary with a graalvm build
            # and the one we have set in JAVA_HOME is at least jdk24
            # => run without gradle = True
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # check fileslist.txt
            fl_path = os.path.join(target_dir, "build", "resources", "main", util.VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                lines = f.readlines()
            assert "/" + util.VFS_PREFIX + "/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + util.VFS_PREFIX + "/home/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + util.VFS_PREFIX + "/home/lib-graalpython/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + util.VFS_PREFIX + "/home/lib-python/\n" in lines, "unexpected output from " + str(cmd)

            # execute and check native image
            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("hello java", out)

            # import struct from python file triggers extract of native extension files in VirtualFileSystem
            hello_src = os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py")
            contents = open(hello_src, 'r').read()
            with open(hello_src, 'w') as f:
                f.write("import struct\n" + contents)

            # rebuild and exec
            cmd = gradlew_cmd + ["build", "run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("hello java", out)

            #GR-51132 - NoClassDefFoundError when running polyglot app in java mode
            util.check_ouput("java.lang.NoClassDefFoundError", out, False)

            # move app to another folder
            # this will break launcher symlinks, but should be able to recover from that
            target_dir2 = os.path.join(str(tmpdir), "generated_app_gradle.2" + self.target_dir_name_sufix())
            os.rename(target_dir, target_dir2)
            # adding new dep triggers launcher without venv regen
            self.copy_build_files(target_dir2)
            build_file2 = os.path.join(target_dir2, self.build_file_name)
            append(build_file2, self.packages_termcolor_ujson())

            gradlew_cmd2 = util.get_gradle_wrapper(target_dir2, self.env)
            cmd = gradlew_cmd2 + ["build", "run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir2, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Deleting GraalPy venv due to broken launcher symlinks", out)
            util.check_ouput("hello java", out)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app_external_resources(self):
        with tempfile.TemporaryDirectory() as tmpdir:
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
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)

            # check java exec
            cmd = gradle_cmd + ["run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("hello java", out)

            # prepare for native build
            meta_inf = os.path.join(target_dir, "src", "main", "resources", "META-INF", "native-image")
            os.makedirs(meta_inf, exist_ok=True)
            shutil.copyfile(os.path.join(self.test_prj_path, "src", "main", "resources", "META-INF", "native-image", "proxy-config.json"), os.path.join(meta_inf, "proxy-config.json"))

            # gradle needs jdk <= 22, but it looks like the 'gradle nativeCompile' cmd does not complain if higher,
            # which is fine, because we need to build the native binary with a graalvm build
            # and the one we have set in JAVA_HOME is at least jdk24
            # => run without gradle = True
            cmd = gradle_cmd + ["nativeCompile"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

            # execute and check native image
            cmd = [os.path.join(target_dir, "build", "native", "nativeCompile", "graalpy-gradle-test-project")]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("hello java", out)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_fail_without_graalpy_dep(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "gradle_fail_without_graalpy_dep" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)
            append(build_file, "graalPy {}")

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)

            util.replace_in_file(build_file,
                "implementation(\"org.graalvm.python:python-community:24.2.0\")",
                "// implementation(\"org.graalvm.python:python-community:24.2.0\")")

            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("Missing GraalPy dependency. Please add to your build.gradle either org.graalvm.polyglot:python-community or org.graalvm.polyglot:python", out)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_gen_launcher_and_venv(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "gradle_gen_launcher_and_venv" + self.target_dir_name_sufix())
            self.generate_app(target_dir)

            build_file = os.path.join(target_dir, self.build_file_name)

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)

            append(build_file, self.packages_termcolor_ujson())

            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("-m venv", out)
            util.check_ouput("-m ensurepip",out)
            util.check_ouput("ujson", out)
            util.check_ouput("termcolor", out)

            # run again and assert that we do not regenerate the venv
            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("ujson", out, False)
            util.check_ouput("termcolor", out, False)

            # remove ujson pkg from plugin config and check if unistalled
            self.copy_build_files(target_dir)
            append(build_file, self.packages_termcolor())

            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("Uninstalling ujson", out)
            util.check_ouput("termcolor", out, False)

    def check_tagfile(self, home, expected):
        with open(os.path.join(home, "tagfile")) as f:
            lines = f.readlines()
        assert lines == expected, "expected tagfile " + str(expected) + ", but got " + str(lines)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "check_home_test" + self.target_dir_name_sufix())
            self.generate_app(target_dir)

            build_file_template = os.path.join(os.path.dirname(__file__), "gradle", "build", self.build_file_name)
            build_file = os.path.join(target_dir, self.build_file_name)

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)
            process_resources_cmd = gradle_cmd + ["graalPyResources"]

            # 1. process-resources with no pythonHome config
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out)

            home_dir = os.path.join(target_dir, "build", "generated", "graalpy", "resources", util.VFS_PREFIX, "home")
            assert os.path.exists(home_dir)
            assert os.path.exists(os.path.join(home_dir, "lib-graalpython"))
            assert os.path.isdir(os.path.join(home_dir, "lib-graalpython"))
            assert os.path.exists(os.path.join(home_dir, "lib-python"))
            assert os.path.isdir(os.path.join(home_dir, "lib-python"))
            assert os.path.exists(os.path.join(home_dir, "tagfile"))
            assert os.path.isfile(os.path.join(home_dir, "tagfile"))
            self.check_tagfile(home_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            # 2. process-resources with empty pythonHome
            self.copy_build_files(target_dir)
            append(build_file, "graalPy {\n    pythonHome { }\n}")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out, False)
            self.check_tagfile(home_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            # 3. process-resources with empty pythonHome includes and excludes
            self.copy_build_files(target_dir)
            append(build_file, self.empty_home_includes())
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out, False)
            self.check_tagfile(home_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            # 4. process-resources with pythonHome includes and excludes
            self.copy_build_files(target_dir)
            append(build_file, self.home_includes())
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Deleting GraalPy home due to changed includes or excludes", out)
            util.check_ouput("Copying std lib to ", out)
            self.check_tagfile(home_dir, [f'{self.graalvmVersion}\n', 'include:.*__init__.py\n', 'exclude:.*html/__init__.py\n'])

            # 5. check fileslist.txt
            # XXX build vs graalPyVFSFilesList task?
            out, return_code = util.run_cmd(gradle_cmd + ["build"], self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)
            fl_path = os.path.join(target_dir, "build", "resources", "main", util.VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                for line in f:
                    line = f.readline()
                    # string \n
                    line = line[:len(line)-1]
                    if line.endswith("tagfile"):
                        continue
                    if not line.startswith("/" + util.VFS_PREFIX + "/home/") or line.endswith("/"):
                        continue
                    assert line.endswith("/__init__.py"), f"expected line to end with /__init__.py, but was '{line}'"
                    assert not line.endswith("html/__init__.py"), f"expected line to end with html/__init__.py, but was '{line}''"

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_empty_packages(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "empty_packages_test" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            append(build_file, self.empty_packages())

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)
            cmd = gradle_cmd + ["graalPyResources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, gradle = True)
            util.check_ouput("BUILD SUCCESS", out)

class GradlePluginGroovyTest(GradlePluginTestBase):

    def setUpClass(self):
        super().setUpClass()
        self.build_file_name = "build.gradle"
        self.settings_file_name = "settings.gradle"

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

    def packages_termcolor(self):
        return textwrap.dedent("""
            graalPy {
                packages = ["termcolor"]
            }
            """)

    def packages_termcolor_ujson(self):
        return textwrap.dedent("""
            graalPy {
                packages = ["termcolor", "ujson"]
            }
            """)

    def packages_termcolor_resource_dir(self, resources_dir):
        resources_dir = resources_dir if 'win32' != sys.platform else resources_dir.replace("\\", "\\\\")
        return textwrap.dedent(f"""
            graalPy {{
                packages = ["termcolor"]
                pythonResourcesDirectory = file("{resources_dir}")
            }}
            """)

    def home_includes(self):
        return textwrap.dedent("""
            graalPy {
                pythonHome {
                    includes = [".*__init__.py"]
                    excludes = [".*html/__init__.py"]
                }
            }
            """)

    def empty_home_includes(self):
        return textwrap.dedent("""
            graalPy {
               pythonHome {
                   includes = []
                   excludes = []
               }
            }
            """)

    def empty_packages(self):
        return textwrap.dedent("""
            graalPy {
                packages = []
            }
            """)

class GradlePluginKotlinTest(GradlePluginTestBase):

    def setUpClass(self):
        super().setUpClass()
        self.build_file_name = "build.gradle.kts"
        self.settings_file_name = "settings.gradle.kts"

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

    def packages_termcolor(self):
       return textwrap.dedent("""
            graalPy {
               packages.add("termcolor")
            }
            """)

    def packages_termcolor_ujson(self):
        return textwrap.dedent("""
            graalPy {
                packages.add("termcolor")
                packages.add("ujson")
            }
            """)

    def packages_termcolor_resource_dir(self, resources_dir):
        resources_dir = resources_dir if 'win32' != sys.platform else resources_dir.replace("\\", "\\\\")
        return textwrap.dedent(f"""
            graalPy {{
                packages.add("termcolor")
                pythonResourcesDirectory = file("{resources_dir}")
            }}
            """)

    def home_includes(self):
        return textwrap.dedent("""
            graalPy {
                pythonHome {
                    includes.add(".*__init__.py")
                    excludes.add(".*html/__init__.py")
                }
            }
            """)

    def empty_home_includes(self):
        return textwrap.dedent("""
            graalPy {
               pythonHome {
                   includes
                   excludes
               }
            }
            """)

    def empty_packages(self):
        return textwrap.dedent("""
            graalPy {
                packages
            }
            """)

unittest.skip_deselected_test_functions(globals())
