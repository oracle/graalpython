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
import shutil
import sys
import textwrap
import unittest

from tests.standalone import util
from tests.standalone.util import TemporaryTestDirectory, Logger

MISSING_FILE_WARNING = "The list of installed Python packages does not match the packages specified in the graalpy-maven-plugin configuration"
PACKAGES_CHANGED_ERROR = "packages and their version constraints in graalpy-maven-plugin configuration are different then previously used to generate the lock file"
VENV_UPTODATE = "Virtual environment is up to date with lock file, skipping install"

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

    def lock_packages_config(self, community, pkgs, lock_file):
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

        meta_inf_native_image_dir = os.path.join(target_dir, "src", "main", "resources", "META-INF", "native-image")
        os.makedirs(meta_inf_native_image_dir, exist_ok=True)
        shutil.copy(os.path.join(os.path.dirname(__file__), "native-image.properties"), os.path.join(meta_inf_native_image_dir, "native-image.properties"))

    def check_filelist(self, target_dir, log):
        fl_path = os.path.join(target_dir, "build", "resources", "main", util.DEFAULT_VFS_PREFIX, "fileslist.txt")
        with open(fl_path) as f:
            lines = f.readlines()

        log.log_block("filelist.txt", ''.join(lines))
        assert "/" + util.DEFAULT_VFS_PREFIX + "/\n" in lines, log

    def check_gradle_generated_app(self, community):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "generated_app_gradle" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)
            append(build_file, self.packages_termcolor(community))

            log = Logger()

            # build
            gradlew_cmd = util.get_gradle_wrapper(target_dir, self.env, verbose=False)

            cmd = gradlew_cmd + ["build"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir, logger=log)
            util.check_ouput("BUILD SUCCESS", out, logger=log)
            util.check_ouput("Virtual filesystem is deployed to default resources directory", out, logger=log)
            util.check_ouput("This can cause conflicts if used with other Java libraries that also deploy GraalPy virtual filesystem", out, logger=log)
            self.check_filelist(target_dir, log)

            gradlew_cmd = util.get_gradle_wrapper(target_dir, self.env)

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

    @unittest.skipUnless(util.is_maven_plugin_test_enabled, "ENABLE_MAVEN_PLUGIN_UNITTESTS is not true")
    def check_lock_packages(self, community):
        with util.TemporaryTestDirectory() as tmpdir:

            target_dir = os.path.join(str(tmpdir), "lock_packages" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            gradlew_cmd = util.get_gradle_wrapper(target_dir, self.env)

            # start with requests package without version
            append(build_file, self.lock_packages_config(pkgs=["requests"], lock_file="test-graalpy.lock", community=True))

            # build
            cmd = gradlew_cmd + ["build"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("pip install", out)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=True)
            assert not os.path.exists(os.path.join(target_dir, "test-graalpy.lock"))

            # lock without version
            cmd = gradlew_cmd + ["graalpyLockPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=True)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-graalpy.lock"))
            os.remove(os.path.join(target_dir, "test-graalpy.lock"))

            # lock with correct version
            log = Logger()
            self.copy_build_files(target_dir)
            append(build_file, self.lock_packages_config(pkgs=["requests==2.32.3"], lock_file="test-graalpy.lock", community=True))
            cmd = gradlew_cmd + ["graalpyLockPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=True)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-graalpy.lock"))

            # add termcolor and build - fails as it is not part of lock file
            log = Logger()
            self.copy_build_files(target_dir)
            append(build_file, self.lock_packages_config(pkgs=["requests==2.32.3", "termcolor==2.2"], lock_file="test-graalpy.lock", community=True))
            cmd = gradlew_cmd + ["build"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput(PACKAGES_CHANGED_ERROR, out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-graalpy.lock"))

            # lock with termcolor
            log = Logger()
            cmd = gradlew_cmd + ["graalpyLockPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=True)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)
            assert os.path.exists(os.path.join(target_dir, "test-graalpy.lock"))

            # should be able to import requests if installed
            util.replace_in_file(os.path.join(target_dir, "src", "main", "java", "org", "example", "GraalPy.java"),
                                 "import hello",
                                 "import requests; import hello")

            # rebuild with lock and exec
            cmd = gradlew_cmd + ["build", "run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput(VENV_UPTODATE, out)
            util.check_ouput("hello java", out)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)

            # run with no packages, only lock file
            self.copy_build_files(target_dir)
            append(build_file, self.empty_packages())

            # stop using lock file field and test with default value {project_root}/graalpy.lock
            shutil.move(os.path.join(target_dir, "test-graalpy.lock"), os.path.join(target_dir, "graalpy.lock"))

            # clean and rebuild fails
            cmd = gradlew_cmd + ["clean", "build", "run"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput(MISSING_FILE_WARNING, out, contains=False)

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
            process_resources_cmd = gradle_cmd + ["--stacktrace", "graalPyInstallPackages"]

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
            cmd = gradle_cmd + ["graalPyInstallPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            assert return_code == 0, out

            cmd = gradle_cmd + ["graalpyLockPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out, contains=False)
            util.check_ouput("In order to run the graalPyLockPackages task there have to be python packages declared in the graalpy-gradle-plugin configuration.", out)
            assert not os.path.exists(os.path.join(target_dir, "graalpy.lock"))


    def check_gradle_python_resources_dir_deprecation(self):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "py_res_dir_deprecation_test" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)

            resources_dir = resources_dir if 'win32' != sys.platform else resources_dir.replace("\\", "\\\\")
            append(build_file, f'''
                graalPy {{
                    community = true
                    pythonResourcesDirectory = file("{resources_dir}")
                }}
            ''')

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)
            cmd = gradle_cmd + ["graalPyInstallPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("Property 'pythonResourcesDirectory' is deprecated and will be removed. Use property 'externalDirectory' instead.", out)
            assert return_code == 0, out


    def check_gradle_python_resources_dir_and_external_dir_error(self):
        with TemporaryTestDirectory() as tmpdir:
            target_dir = os.path.join(str(tmpdir), "py_res_dir_external_dir_err" + self.target_dir_name_sufix())
            self.generate_app(target_dir)
            build_file = os.path.join(target_dir, self.build_file_name)

            resources_dir = os.path.join(target_dir, "python-resources")
            os.makedirs(resources_dir, exist_ok=True)

            resources_dir = resources_dir if 'win32' != sys.platform else resources_dir.replace("\\", "\\\\")
            append(build_file, f'''
                graalPy {{
                    community = true
                    pythonResourcesDirectory = file("{resources_dir}")
                    externalDirectory = file("{resources_dir}")
                }}
            ''')

            gradle_cmd = util.get_gradle_wrapper(target_dir, self.env)
            cmd = gradle_cmd + ["graalPyInstallPackages"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("Cannot set both 'externalDirectory' and 'resourceDirectory' at the same time", out)
            assert return_code != 0, out


    def app1_with_namespaced_vfs(self):
        pass


    def app2_using_app1(self):
        pass


    def check_gradle_namespaced_vfs(self):
        with TemporaryTestDirectory() as tmpdir:
            app1_dir = os.path.join(str(tmpdir), "check_ns_app1" + self.target_dir_name_sufix())
            app2_dir = os.path.join(str(tmpdir), "check_ns_app2" + self.target_dir_name_sufix())
            self.generate_app(app1_dir)
            self.generate_app(app2_dir)
            app1_build_file = os.path.join(app1_dir, self.build_file_name)
            app2_build_file = os.path.join(app2_dir, self.build_file_name)

            # Rename app1 package to "com.example"
            new_package_dir = os.path.join(app1_dir, "src", "main", "java", "com")
            os.mkdir(new_package_dir)
            shutil.move(os.path.join(app1_dir, "src", "main", "java", "org", "example"), new_package_dir)
            for j_file in ['GraalPy.java', 'Hello.java']:
                util.replace_in_file(os.path.join(new_package_dir, "example", j_file), "package org.example;", "package com.example;")

            # Update native image config for app1 package
            meta_inf = os.path.join(app1_dir, "src", "main", "resources", "META-INF", "native-image")
            with open(os.path.join(meta_inf, 'proxy-config.json'), 'w') as f:
                f.write('[ ["com.example.Hello"] ]')

            append(app1_build_file, self.app1_with_namespaced_vfs())
            append(app2_build_file, self.app2_using_app1())

            util.replace_main_body(os.path.join(app2_dir, 'src', 'main', 'java', 'org', 'example', 'GraalPy.java'),
                    '''
                                 org.graalvm.python.embedding.VirtualFileSystem vfs =
                                   org.graalvm.python.embedding.VirtualFileSystem.newBuilder()
                                         .resourceDirectory("GRAALPY-VFS/org.graalvm.python.tests/gradleapp1")
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
                                 ''')

            util.replace_in_file(os.path.join(app2_dir, "src", "main", "resources", "org.graalvm.python.vfs", "src", "hello.py"),
                                         'colored("hello "', 'colored("Hi there "')

            vfs_parent_dir = os.path.join(app1_dir, "src", "main", "resources", "GRAALPY-VFS", "org.graalvm.python.tests")
            os.makedirs(vfs_parent_dir)
            shutil.move(os.path.join(app1_dir, "src", "main", "resources", "org.graalvm.python.vfs"), os.path.join(vfs_parent_dir, "gradleapp1"))

            app1_gradle_cmd = util.get_gradle_wrapper(app1_dir, self.env)
            out, return_code = util.run_cmd(app1_gradle_cmd + ['publishToMavenLocal'], self.env, cwd=app1_dir)
            util.check_ouput("Virtual filesystem is deployed to default resources directory", out, contains=False)
            util.check_ouput("This can cause conflicts if used with other Java libraries that also deploy GraalPy virtual filesystem", out, contains=False)
            assert return_code == 0, out

            app2_gradle_cmd = util.get_gradle_wrapper(app2_dir, self.env)
            out, return_code = util.run_cmd(app2_gradle_cmd + ['run'], self.env, cwd=app2_dir)
            util.check_ouput("0: Hi there java", out)
            util.check_ouput("1: hello java", out)
            assert return_code == 0, out

            out, return_code = util.run_cmd(app2_gradle_cmd + ['nativeCompile'], self.env, cwd=app2_dir)
            assert return_code == 0, out

            out, return_code = util.run_cmd([os.path.join(app2_dir, 'build', 'native', 'nativeCompile', 'graalpy-gradle-test-project')], self.env, cwd=app2_dir)
            util.check_ouput("0: Hi there java", out)
            util.check_ouput("1: hello java", out)
            assert return_code == 0, out


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
    def test_gradle_lock_packages(self):
        self.check_lock_packages(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_generated_app_external_resources(self):
        self.check_gradle_generated_app_external_resources()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home_warning(self):
        self.check_gradle_check_home_warning(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_long_running_test_enabled, "ENABLE_GRADLE_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_gradle_check_home(self):
        self.check_gradle_check_home(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_empty_packages(self):
        self.check_gradle_empty_packages()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_namespaced_vfs(self):
        self.check_gradle_namespaced_vfs()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_python_resources_dir_deprecation(self):
        self.check_gradle_python_resources_dir_deprecation()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_python_resources_dir_and_external_dir_error(self):
        self.check_gradle_python_resources_dir_and_external_dir_error()

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

    def lock_packages_config(self, community, pkgs, lock_file=None):
        lf = f"graalPyLockFile = file(\"{lock_file}\")" if lock_file else ""
        packages = "packages = [\"" + "\",\"".join(pkgs) + "\"]"
        return textwrap.dedent(f"""
            graalPy {{
                {packages}
                {lf}
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
                externalDirectory = file("{resources_dir}")
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

    def app1_with_namespaced_vfs(self):
        return '''
               graalPy {
                   community = true
                   resourceDirectory = "GRAALPY-VFS/org.graalvm.python.tests/gradleapp1"
                   packages = ["termcolor"]
               }
               apply plugin: 'maven-publish'
               publishing {
                   publications {
                        mavenJava(MavenPublication) {
                            from components.java
                            groupId = 'org.graalvm.python.tests'
                            artifactId = 'gradleapp1'
                            version = '1.0.0-SNAPSHOT'
                        }
                   }
                   repositories { mavenLocal() }
               }
            '''


    def app2_using_app1(self):
        return '''
            graalPy {
                 community = true
                 packages = ["termcolor"]
            }
            repositories { mavenLocal() }
            dependencies {
                 implementation 'org.graalvm.python.tests:gradleapp1:1.0.0-SNAPSHOT'
            }
            '''

class GradlePluginKotlinTest(GradlePluginTestBase):

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.build_file_name = "build.gradle.kts"
        cls.settings_file_name = "settings.gradle.kts"

    @unittest.skipUnless(util.is_gradle_plugin_long_running_test_enabled, "ENABLE_GRADLE_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_gradle_generated_app(self):
        self.check_gradle_generated_app(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_lock_packages(self):
        self.check_lock_packages(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_long_running_test_enabled, "ENABLE_GRADLE_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_gradle_generated_app_external_resources(self):
        self.check_gradle_generated_app_external_resources()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_check_home_warning(self):
        self.check_gradle_check_home_warning(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_long_running_test_enabled, "ENABLE_GRADLE_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_gradle_check_home(self):
        self.check_gradle_check_home(community=True)

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_empty_packages(self):
        self.check_gradle_empty_packages()

    @unittest.skipUnless(util.is_gradle_plugin_long_running_test_enabled, "ENABLE_GRADLE_PLUGIN_LONG_RUNNING_UNITTESTS is not true")
    def test_gradle_namespaced_vfs(self):
        self.check_gradle_namespaced_vfs()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_python_resources_dir_deprecation(self):
        self.check_gradle_python_resources_dir_deprecation()

    @unittest.skipUnless(util.is_gradle_plugin_test_enabled, "ENABLE_GRADLE_PLUGIN_UNITTESTS is not true")
    def test_gradle_python_resources_dir_and_external_dir_error(self):
        self.check_gradle_python_resources_dir_and_external_dir_error()

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

    def lock_packages_config(self, community, pkgs, lock_file=None):
        lf = f"graalPyLockFile = file(\"{lock_file}\")" if lock_file else ""
        packages = ""
        for p in pkgs:
            packages += f"    packages.add(\"{p}\")\n"
        return textwrap.dedent(f"""
            graalPy {{
                {packages}
                {lf}
                {_community_as_property(community)}
            }}
            """)

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
                externalDirectory = file("{resources_dir}")
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

    def app1_with_namespaced_vfs(self):
        return '''
            graalPy {
                community = true
                resourceDirectory = "GRAALPY-VFS/org.graalvm.python.tests/gradleapp1"
                packages.add("termcolor")
            }
            group = "org.graalvm.python.tests"
            version = "1.0.0-SNAPSHOT"
            apply(plugin = "maven-publish")
            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        // Use the components of the Java plugin (i.e., the compiled code and resources)
                        from(components["java"])
                        artifactId = "gradleapp1kt"
                    }
                }
                repositories {
                    mavenLocal()
                }
            }
            '''


    def app2_using_app1(self):
        return '''
            graalPy {
                community = true
                packages.add("termcolor")
            }
            repositories { mavenLocal() }
            dependencies {
                implementation("org.graalvm.python.tests:gradleapp1kt:1.0.0-SNAPSHOT")
            }
            '''
