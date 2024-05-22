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
import subprocess
import tempfile
import unittest
import urllib.parse
import shutil
import util

is_enabled = 'ENABLE_STANDALONE_UNITTESTS' in os.environ and os.environ['ENABLE_STANDALONE_UNITTESTS'] == "true"

GLOBAL_MVN_CMD = [shutil.which('mvn'), "--batch-mode"]
VFS_PREFIX = "org.graalvm.python.vfs"

def get_gp():
    graalpy = util.get_gp()

    java_home = os.environ["JAVA_HOME"]
    ni = util.get_executable(os.path.join(java_home, "bin", "native-image"))
    jc = util.get_executable(os.path.join(java_home, "bin", "javac"))

    if not os.path.isfile(jc) or not os.path.isfile(ni):
        print(
            "Standalone module tests require a GraalVM JDK.",
            "Please point the JAVA_HOME environment variables properly.",
            f"JAVA_HOME={java_home}",
            "native-image exists: " + str(os.path.exists(ni)),
            "javac exists: " + str(os.path.exists(jc)),
            sep="\n",
        )
        assert False

    print("Running tests for standalone module:")
    print("  graalpy_home:", graalpy_home)
    print("  graalpy     :", graalpy)
    print("  java_home   :", java_home)

    return graalpy

def replace_in_file(file, str, replace_str):
    with open(file, "r") as f:
        contents = f.read()
    with open(file, "w") as f:
        f.write(contents.replace(str, replace_str))

class PolyglotAppTest(unittest.TestCase):

    def setUpClass(self):
        if not is_enabled:
            return

        self.env = os.environ.copy()
        self.env["PYLAUNCHER_DEBUG"] = "1"

        self.archetypeGroupId = "org.graalvm.python"
        self.archetypeArtifactId = "graalpy-archetype-polyglot-app"
        self.pluginArtifactId = "graalpy-maven-plugin"
        self.graalvmVersion = util.get_graalvm_version()

        for custom_repo in os.environ.get("MAVEN_REPO_OVERRIDE", "").split(","):
            url = urllib.parse.urlparse(custom_repo)
            if url.scheme == "file":
                jar = os.path.join(
                    url.path,
                    self.archetypeGroupId.replace(".", os.path.sep),
                    self.archetypeArtifactId,
                    self.graalvmVersion,
                    f"{self.archetypeArtifactId}-{self.graalvmVersion}.jar",
                )
                pom = os.path.join(
                    url.path,
                    self.archetypeGroupId.replace(".", os.path.sep),
                    self.archetypeArtifactId,
                    self.graalvmVersion,
                    f"{self.archetypeArtifactId}-{self.graalvmVersion}.pom",
                )
                cmd = GLOBAL_MVN_CMD + [
                    "install:install-file",
                    f"-Dfile={jar}",
                    f"-DgroupId={self.archetypeGroupId}",
                    f"-DartifactId={self.archetypeArtifactId}",
                    f"-Dversion={self.graalvmVersion}",
                    "-Dpackaging=jar",
                    f"-DpomFile={pom}",
                    "-DcreateChecksum=true",
                ]
                out, return_code = util.run_cmd(cmd, self.env)
                assert return_code == 0

                jar = os.path.join(
                    url.path,
                    self.archetypeGroupId.replace(".", os.path.sep),
                    self.pluginArtifactId,
                    self.graalvmVersion,
                    f"{self.pluginArtifactId}-{self.graalvmVersion}.jar",
                )

                pom = os.path.join(
                    url.path,
                    self.archetypeGroupId.replace(".", os.path.sep),
                    self.pluginArtifactId,
                    self.graalvmVersion,
                    f"{self.pluginArtifactId}-{self.graalvmVersion}.pom",
                )

                cmd = GLOBAL_MVN_CMD + [
                    "install:install-file",
                    f"-Dfile={jar}",
                    f"-DgroupId={self.archetypeGroupId}",
                    f"-DartifactId={self.pluginArtifactId}",
                    f"-Dversion={self.graalvmVersion}",
                    "-Dpackaging=jar",
                    f"-DpomFile={pom}",
                    "-DcreateChecksum=true",
                ]
                out, return_code = util.run_cmd(cmd, self.env)
                assert return_code == 0
                break

    def generate_app(self, tmpdir, target_dir, target_name, pom_template=None):
        cmd = GLOBAL_MVN_CMD + [
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

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
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
            fl_path = os.path.join(target_dir, "target", "classes", VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                lines = f.readlines()
            assert "/" + VFS_PREFIX + "/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + VFS_PREFIX + "/home/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + VFS_PREFIX + "/home/lib-graalpython/\n" in lines, "unexpected output from " + str(cmd)
            assert "/" + VFS_PREFIX + "/home/lib-python/\n" in lines, "unexpected output from " + str(cmd)

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
            hello_src = os.path.join(target_dir, "src", "main", "resources", "org.graalvm.python.vfs", "proj", "hello.py")
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

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
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

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
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
            replace_in_file(os.path.join(target_dir, "pom.xml"), "<package>ujson</package>", "")

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("-m venv", out, False)
            util.check_ouput("-m ensurepip", out, False)
            util.check_ouput("Uninstalling ujson", out)
            util.check_ouput("termcolor", out, False)

    def check_tagfile(self, target_dir, expected):
        with open(os.path.join(target_dir, "target", "classes", VFS_PREFIX, "home", "tagfile")) as f:
            lines = f.readlines()
        assert lines == expected, "expected tagfile " + str(expected) + ", but got " + str(lines)

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
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

            home_dir = os.path.join(target_dir, "target", "classes", VFS_PREFIX, "home")
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
            replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", "<pythonHome></pythonHome></configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Copying std lib to ", out, False)
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*\n'])

            shutil.copyfile(pom_template, os.path.join(target_dir, "pom.xml"))
            util.patch_pom_repositories(os.path.join(target_dir, "pom.xml"))
            replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", "<pythonHome><includes></includes><excludes></excludes></pythonHome></configuration>")
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
            replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", home_tag + "</configuration>")
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
            replace_in_file(os.path.join(target_dir, "pom.xml"), "</configuration>", home_tag + "</configuration>")
            out, return_code = util.run_cmd(process_resources_cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)
            util.check_ouput("Deleting GraalPy home due to changed includes or excludes", out)
            util.check_ouput("Copying std lib to ", out)
            self.check_tagfile(target_dir, [f'{self.graalvmVersion}\n', 'include:.*__init__\\.py\n', 'exclude:.*html/__init__\\.py\n'])

            # 4. check fileslist.txt
            fl_path = os.path.join(target_dir, "target", "classes", VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                for line in f:
                    line = f.readline()
                    # string \n
                    line = line[:len(line)-1]
                    if not line.startswith("/" + VFS_PREFIX + "/home/") or line.endswith("/"):
                        continue
                    assert line.endswith("/__init__.py")
                    assert not line.endswith("html/__init__.py")

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
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

            replace_in_file(os.path.join(target_dir, "pom.xml"), "</packages>", "<package></package><package> </package></packages>")

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = util.run_cmd(cmd, self.env, cwd=target_dir)
            util.check_ouput("BUILD SUCCESS", out)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_one_file():
    graalpy = util.get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = util.get_graalvm_version()

    with tempfile.TemporaryDirectory() as tmpdir:

        source_file = os.path.join(tmpdir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("import sys\n")
            f.write("print('hello world, argv[1:]:', sys.argv[1:])")

        target_file = os.path.join(tmpdir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-m", source_file, "-o", target_file]

        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("Bundling Python resources into", out)

        cmd = [target_file, "arg1", "arg2"]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("hello world, argv[1:]: " + str(cmd[1:]), out)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_venv_and_one_file():
    graalpy = util.get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = util.get_graalvm_version()

    with tempfile.TemporaryDirectory() as target_dir:
        source_file = os.path.join(target_dir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("from termcolor import colored, cprint\n")
            f.write("colored_text = colored('hello standalone world', 'red', attrs=['reverse', 'blink'])\n")
            f.write("print(colored_text)\n")
            f.write("import ujson\n")
            f.write('d = ujson.loads("""{"key": "value"}""")\n')
            f.write("print('key=' + d['key'])\n")

        venv_dir = os.path.join(target_dir, "venv")
        cmd = [graalpy, "-m", "venv", venv_dir]
        out, return_code = util.run_cmd(cmd, env)

        venv_python = os.path.join(venv_dir, "Scripts", "python.exe") if os.name == "nt" else os.path.join(venv_dir, "bin", "python")
        cmd = [venv_python, "-m", "pip", "install", "termcolor", "ujson"]
        out, return_code = util.run_cmd(cmd, env)

        target_file = os.path.join(target_dir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-Os", "-m", source_file, "--venv", venv_dir, "-o", target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("Bundling Python resources into", out)

        cmd = [target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("hello standalone world", out)
        util.check_ouput("key=value", out)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_module():
    graalpy = util.get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = util.get_graalvm_version()

    with tempfile.TemporaryDirectory() as tmp_dir:

        module_dir = os.path.join(tmp_dir, "hello_app")
        os.makedirs(module_dir, exist_ok=True)

        source_file = os.path.join(module_dir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("def print_hello():\n")
            f.write("    print('hello standalone world')\n")

        source_file = os.path.join(module_dir, "__main__.py")
        with open(source_file, 'w') as f:
            f.write("import hello\n")
            f.write("hello.print_hello()\n")

        target_file = os.path.join(tmp_dir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-Os", "-m", module_dir, "-o", target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("Bundling Python resources into", out)

        cmd = [target_file]
        out, return_code = util.run_cmd(cmd, env)
        util.check_ouput("hello standalone world", out)

unittest.skip_deselected_test_functions(globals())
