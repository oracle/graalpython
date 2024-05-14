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
import sys
import subprocess
import tempfile
import unittest
import urllib.parse
import shutil

is_enabled = 'ENABLE_STANDALONE_UNITTESTS' in os.environ and os.environ['ENABLE_STANDALONE_UNITTESTS'] == "true"

GLOBAL_MVN_CMD = [shutil.which('mvn'), "--batch-mode"]
MAVEN_VERSION = "3.9.6"
VFS_PREFIX = "org.graalvm.python.vfs"

def run_cmd(cmd, env, cwd=None):
    print(f"Executing:\n    {cmd=}\n")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    out = []
    print("============== output =============")
    for line in iter(process.stdout.readline, ""):
        print(line, end="")
        out.append(line)
    print("\n========== end of output ==========")
    return "".join(out), process.wait()

def get_executable(file):
    if os.path.isfile(file):
        return file
    exe = f"{file}.exe"
    if os.path.isfile(exe):
        return exe
    exe = f"{file}.cmd"
    if os.path.isfile(exe):
        return exe
    return None

def get_gp():
    java_home = os.environ["JAVA_HOME"]
    graalpy_home = os.environ["PYTHON_STANDALONE_HOME"]

    ni = get_executable(os.path.join(java_home, "bin", "native-image"))
    jc = get_executable(os.path.join(java_home, "bin", "javac"))
    graalpy = get_executable(os.path.join(graalpy_home, "bin", "graalpy"))

    if not os.path.isfile(graalpy) or not os.path.isfile(jc) or not os.path.isfile(ni):
        print(
            "Standalone module tests require a GraalVM JDK and a GraalPy standalone.",
            "Please point the JAVA_HOME and PYTHON_STANDALONE_HOME environment variables properly.",
            f"{java_home=}",
            f"{graalpy_home=}",
            "native-image exists: " + str(os.path.exists(ni)),
            "javac exists: " + str(os.path.exists(jc)),
            "graalpy exits: " + str(os.path.exists(graalpy)),
            sep="\n",
        )
        assert False

    print("Running tests for standalone module:")
    print("  graalpy_home:", graalpy_home)
    print("  graalpy     :", graalpy)
    print("  java_home   :", java_home)

    return graalpy


def get_graalvm_version():
    graalvmVersion, _ = run_cmd([get_gp(), "-c", "print(__graalpython__.get_graalvm_version(), end='')"], os.environ.copy())
    # when JLine is cannot detect a terminal, it prints logging info
    graalvmVersion = graalvmVersion.split("\n")[-1]
    # we never test -dev versions here, we always pretend to use release versions
    graalvmVersion = graalvmVersion.split("-dev")[0]
    return graalvmVersion

def get_mvn_wrapper(project_dir, env):
    if 'win32' != sys.platform:
        cmd = [shutil.which('mvn'), "--batch-mode", "wrapper:wrapper", f"-Dmaven={MAVEN_VERSION}"]
        out, return_code = run_cmd(cmd, env, cwd=project_dir)
        assert "BUILD SUCCESS", out
        mvn_cmd = [os.path.abspath(os.path.join(project_dir, "mvnw")),  "--batch-mode"]
    else:
        # TODO installing mvn wrapper with the current mvn 3.3.9 on gates does not work
        # we still have to provide the mvnw.bat script
        mvnw_dir = os.path.join(os.path.dirname(__file__), "mvnw")
        mvn_cmd = [os.path.abspath(os.path.join(mvnw_dir, "mvnw.cmd")),  "--batch-mode"]

    cmd = mvn_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    assert "3.9.6" in out
    return mvn_cmd

class PolyglotAppTest(unittest.TestCase):

    def setUpClass(self):
        if not is_enabled:
            return

        self.env = os.environ.copy()
        self.env["PYLAUNCHER_DEBUG"] = "1"

        self.archetypeGroupId = "org.graalvm.python"
        self.archetypeArtifactId = "graalpy-archetype-polyglot-app"
        self.pluginArtifactId = "graalpy-maven-plugin"
        self.graalvmVersion = get_graalvm_version()

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
                out, return_code = run_cmd(cmd, self.env)
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
                out, return_code = run_cmd(cmd, self.env)
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
        out, return_code = run_cmd(cmd, self.env, cwd=str(tmpdir))
        assert "BUILD SUCCESS" in out

        if pom_template:
            self.create_test_pom(pom_template, os.path.join(target_dir, "pom.xml"))

        if custom_repos := os.environ.get("MAVEN_REPO_OVERRIDE"):
            repos = []
            pluginRepos = []
            for idx, custom_repo in enumerate(custom_repos.split(",")):
                repos.append(f"""
                    <repository>
                        <id>myrepo{idx}</id>
                        <url>{custom_repo}</url>
                        <releases>
                            <enabled>true</enabled>
                        </releases>
                        <snapshots>
                            <enabled>true</enabled>
                        </snapshots>
                    </repository>
                """)
                pluginRepos.append(f"""
                    <pluginRepository>
                        <id>myrepo{idx}</id>
                        <url>{custom_repo}</url>
                        <releases>
                            <enabled>true</enabled>
                            <updatePolicy>always</updatePolicy>
                        </releases>
                        <snapshots>
                            <enabled>true</enabled>
                            <updatePolicy>always</updatePolicy>
                        </snapshots>
                    </pluginRepository>
                """)

            with open(os.path.join(target_dir, "pom.xml"), "r") as f:
                contents = f.read()
            with open(os.path.join(target_dir, "pom.xml"), "w") as f:
                f.write(contents.replace("</project>", """
                <repositories>
                """ + '\n'.join(repos) + """
                </repositories>
                <pluginRepositories>
                """ + '\n'.join(pluginRepos) + """
                </pluginRepositories>
                </project>
                """))

    def create_test_pom(self, template, pom):
        lines = open(template, 'r').readlines()
        with open(pom, 'w') as f:
            for line in lines:
                if "{graalpy-maven-plugin-version}" in line:
                    line = line.replace("{graalpy-maven-plugin-version}", self.graalvmVersion)
                f.write(line)

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
    def test_generated_app(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "generated_app_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            self.generate_app(tmpdir, target_dir, target_name)

            mvnw_cmd = get_mvn_wrapper(target_dir, self.env)

            # build
            cmd = mvnw_cmd + ["package", "-Pnative", "-DmainClass=it.pkg.GraalPy"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "BUILD SUCCESS" in out, "unexpected output from " + str(cmd)

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
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "hello java" in out, "unexpected output from " + str(cmd)

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
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "BUILD SUCCESS" in out, "unexpected output from " + str(cmd)
            assert "hello java" in out, "unexpected output from " + str(cmd)

            #GR-51132 - NoClassDefFoundError when running polyglot app in java mode
            assert "java.lang.NoClassDefFoundError" not in out

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
    def test_fail_without_graalpy_dep(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "fail_without_graalpy_dep_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "fail_without_graalpy_dep_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "Missing GraalPy dependency. Please add to your pom either org.graalvm.polyglot:python-community or org.graalvm.polyglot:python" in out

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
    def test_gen_launcher_and_venv(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "gen_launcher_and_venv_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "prepare_venv_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "-m venv" in out, "unexpected output from " + str(cmd)
            assert "-m ensurepip" in out, "unexpected output from " + str(cmd)
            assert "ujson" in out, "unexpected output from " + str(cmd)
            assert "termcolor" in out, "unexpected output from " + str(cmd)

            # run again and assert that we do not regenerate the venv
            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "-m venv" not in out, "unexpected output from " + str(cmd)
            assert "-m ensurepip" not in out, "unexpected output from " + str(cmd)
            assert "ujson" not in out, "unexpected output from " + str(cmd)
            assert "termcolor" not in out, "unexpected output from " + str(cmd)

            # remove ujson pkg from plugin config and check if unistalled
            with open(os.path.join(target_dir, "pom.xml"), "r") as f:
                contents = f.read()

            with open(os.path.join(target_dir, "pom.xml"), "w") as f:
                f.write(contents.replace("<package>ujson</package>", ""))

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            assert "-m venv" not in out, "unexpected output from " + str(cmd)
            assert "-m ensurepip" not in out, "unexpected output from " + str(cmd)
            assert "Uninstalling ujson" in out, "unexpected output from " + str(cmd)
            assert "termcolor" not in out, "unexpected output from " + str(cmd)

    @unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
    def test_check_home(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            target_name = "check_home_test"
            target_dir = os.path.join(str(tmpdir), target_name)
            pom_template = os.path.join(os.path.dirname(__file__), "check_home_pom.xml")
            self.generate_app(tmpdir, target_dir, target_name, pom_template)

            mvnw_cmd = get_mvn_wrapper(target_dir, self.env)

            cmd = mvnw_cmd + ["process-resources"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)

            # check fileslist.txt
            fl_path = os.path.join(target_dir, "target", "classes", VFS_PREFIX, "fileslist.txt")
            with open(fl_path) as f:
                for line in f:
                    line = f.readline()
                    # string \n
                    line = line[:len(line)-1]
                    if line.endswith("/") or line == "/" + VFS_PREFIX + "/home/tagfile" or line == "/" + VFS_PREFIX + "/proj/hello.py":
                        continue
                    assert line.endswith("/__init__.py")
                    assert not line.endswith("html/__init__.py")

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_one_file():
    graalpy = get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = get_graalvm_version()

    with tempfile.TemporaryDirectory() as tmpdir:

        source_file = os.path.join(tmpdir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("import sys\n")
            f.write("print('hello world, argv[1:]:', sys.argv[1:])")

        target_file = os.path.join(tmpdir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-m", source_file, "-o", target_file]

        out, return_code = run_cmd(cmd, env)
        assert "Bundling Python resources into" in out, "unexpected output from " + str(cmd)

        cmd = [target_file, "arg1", "arg2"]
        out, return_code = run_cmd(cmd, env)
        assert "hello world, argv[1:]: " + str(cmd[1:]) in out, "unexpected output from " + str(cmd)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_venv_and_one_file():
    graalpy = get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = get_graalvm_version()

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
        out, return_code = run_cmd(cmd, env)

        venv_python = os.path.join(venv_dir, "Scripts", "python.exe") if os.name == "nt" else os.path.join(venv_dir, "bin", "python")
        cmd = [venv_python, "-m", "pip", "install", "termcolor", "ujson"]
        out, return_code = run_cmd(cmd, env)

        target_file = os.path.join(target_dir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-ce", "-Os", "-m", source_file, "--venv", venv_dir, "-o", target_file]
        out, return_code = run_cmd(cmd, env)
        assert "Bundling Python resources into" in out, "unexpected output from " + str(cmd)

        cmd = [target_file]
        out, return_code = run_cmd(cmd, env)
        assert "hello standalone world" in out, "unexpected output from " + str(cmd)
        assert "key=value" in out, "unexpected output from " + str(cmd)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_module():
    graalpy = get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()
    env["MVN_GRAALPY_VERSION"] = get_graalvm_version()

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
        out, return_code = run_cmd(cmd, env)
        assert "Bundling Python resources into" in out, "unexpected output from " + str(cmd)

        cmd = [target_file]
        out, return_code = run_cmd(cmd, env)
        assert "hello standalone world" in out, "unexpected output from " + str(cmd)


unittest.skip_deselected_test_functions(globals())
