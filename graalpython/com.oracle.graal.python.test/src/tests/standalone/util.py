# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import subprocess
import sys
import unittest
import urllib.parse
import tempfile
from abc import ABC, abstractmethod
from typing import Optional

MAVEN_VERSION = "3.9.8"
GLOBAL_MVN_CMD = [shutil.which('mvn'), "--batch-mode"]

GRADLE_VERSION = "8.9"

VFS_PREFIX = "org.graalvm.python.vfs"

is_maven_plugin_test_enabled = 'ENABLE_MAVEN_PLUGIN_UNITTESTS' in os.environ and os.environ['ENABLE_MAVEN_PLUGIN_UNITTESTS'] == "true"
is_gradle_plugin_test_enabled = 'ENABLE_GRADLE_PLUGIN_UNITTESTS' in os.environ and os.environ['ENABLE_GRADLE_PLUGIN_UNITTESTS'] == "true"


class TemporaryTestDirectory():
    def __init__(self):
        if 'GRAALPY_UNITTESTS_TMPDIR_NO_CLEAN' in os.environ:
            self.ctx = None
            self.name = tempfile.mkdtemp()
            print(f"Running test in {self.name}")
        else:
            self.ctx = tempfile.TemporaryDirectory()
            self.name = self.ctx.name

    def __enter__(self):
        return self.name

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.ctx:
            self.ctx.__exit__(exc_type, exc_val, exc_tb)

class LoggerBase(ABC):
    def log_block(self, name, text):
        self.log("=" * 80)
        self.log(f"==> {name}:")
        self.log(text)
        self.log("=" * 80)

    @abstractmethod
    def log(self, msg, newline=True):
        pass

class Logger(LoggerBase):
    def __init__(self):
        self.data = ''

    def log(self, msg, newline=True):
        self.data += msg + ('\n' if newline else '')

    def __str__(self):
        two_lines = ("=" * 80 + "\n") * 2
        return two_lines + "Test execution log:\n" + self.data + "\n" + two_lines

class NullLogger(LoggerBase):
    def log(self, msg, newline=True):
        pass

class StdOutLogger(LoggerBase):
    def __init__(self, delegate:LoggerBase):
        self.delegate = delegate

    def log(self, msg, newline=True):
        print(msg)
        self.delegate.log(msg, newline=newline)


class BuildToolTestBase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not is_maven_plugin_test_enabled and not is_gradle_plugin_test_enabled:
            return

        cls.env = os.environ.copy()
        cls.env["PYLAUNCHER_DEBUG"] = "1"

        cls.archetypeGroupId = "org.graalvm.python"
        cls.archetypeArtifactId = "graalpy-archetype-polyglot-app"
        cls.pluginArtifactId = "graalpy-maven-plugin"
        cls.graalvmVersion = get_graalvm_version()

        for custom_repo in os.environ.get("MAVEN_REPO_OVERRIDE", "").split(","):
            url = urllib.parse.urlparse(custom_repo)
            if url.scheme == "file":
                jar = os.path.join(
                    urllib.parse.unquote(url.path),
                    cls.archetypeGroupId.replace(".", os.path.sep),
                    cls.archetypeArtifactId,
                    cls.graalvmVersion,
                    f"{cls.archetypeArtifactId}-{cls.graalvmVersion}.jar",
                )
                pom = os.path.join(
                    urllib.parse.unquote(url.path),
                    cls.archetypeGroupId.replace(".", os.path.sep),
                    cls.archetypeArtifactId,
                    cls.graalvmVersion,
                    f"{cls.archetypeArtifactId}-{cls.graalvmVersion}.pom",
                )
                cmd = GLOBAL_MVN_CMD + [
                    "install:install-file",
                    f"-Dfile={jar}",
                    f"-DgroupId={cls.archetypeGroupId}",
                    f"-DartifactId={cls.archetypeArtifactId}",
                    f"-Dversion={cls.graalvmVersion}",
                    "-Dpackaging=jar",
                    f"-DpomFile={pom}",
                    "-DcreateChecksum=true",
                ]
                out, return_code = run_cmd(cmd, cls.env)
                assert return_code == 0, out

                jar = os.path.join(
                    urllib.parse.unquote(url.path),
                    cls.archetypeGroupId.replace(".", os.path.sep),
                    cls.pluginArtifactId,
                    cls.graalvmVersion,
                    f"{cls.pluginArtifactId}-{cls.graalvmVersion}.jar",
                )

                pom = os.path.join(
                    urllib.parse.unquote(url.path),
                    cls.archetypeGroupId.replace(".", os.path.sep),
                    cls.pluginArtifactId,
                    cls.graalvmVersion,
                    f"{cls.pluginArtifactId}-{cls.graalvmVersion}.pom",
                )

                cmd = GLOBAL_MVN_CMD + [
                    "install:install-file",
                    f"-Dfile={jar}",
                    f"-DgroupId={cls.archetypeGroupId}",
                    f"-DartifactId={cls.pluginArtifactId}",
                    f"-Dversion={cls.graalvmVersion}",
                    "-Dpackaging=jar",
                    f"-DpomFile={pom}",
                    "-DcreateChecksum=true",
                ]
                out, return_code = run_cmd(cmd, cls.env)
                assert return_code == 0, out
                break

def run_cmd(cmd, env, cwd=None, print_out=False, logger:LoggerBase=NullLogger()):
    if print_out:
        logger = StdOutLogger(logger)
    out = []
    out.append(f"Executing:\n    {cmd=}\n")
    
    logger.log(f"Executing command: {' '.join(cmd)}")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    for line in iter(process.stdout.readline, ""):
        out.append(line)
    out_str = "".join(out)
    logger.log_block("output", out_str)
    return out_str, process.wait()

def check_ouput(txt, out, contains=True, logger: Optional[LoggerBase] =None):
    # if logger is passed, we assume that it already contains the output
    if contains and txt not in out:
        if not logger:
            print_output(out, f"expected '{txt}' in output")
        assert False, f"expected '{txt}' in output. \n{logger}"
    elif not contains and txt in out:
        if not logger:
            print_output(out, f"did not expect '{txt}' in output")
        assert False, f"did not expect '{txt}' in output. {logger}"

def print_output(out, err_msg=None):
    print("============== output =============")
    for line in out:
        print(line, end="")
    print("\n========== end of output ==========")
    if err_msg:
        print("", err_msg, "", sep="\n")

def get_mvn_wrapper(project_dir, env):
    cmd = "mvnw" if 'win32' != sys.platform else "mvnw.cmd"
    mvn_cmd = [os.path.join(project_dir, cmd),  "--batch-mode"]
    cmd = mvn_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    check_ouput(MAVEN_VERSION, out)
    return mvn_cmd

def get_gradle_wrapper(project_dir, env):
    gradle_cmd = [os.path.abspath(os.path.join(project_dir, "gradlew" if 'win32' != sys.platform else "gradlew.bat"))]
    cmd = gradle_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    check_ouput(GRADLE_VERSION, out)
    return gradle_cmd

def get_gp():
    if "PYTHON_STANDALONE_HOME" not in os.environ:
        print_missing_graalpy_msg()
        assert False

    graalpy_home = os.environ["PYTHON_STANDALONE_HOME"]
    graalpy = get_executable(os.path.join(graalpy_home, "bin", "graalpy"))

    if not os.path.isfile(graalpy):
        print_missing_graalpy_msg()
        assert False

    return graalpy

def print_missing_graalpy_msg(graalpy_home=None):
    print("\nThis test requires a GraalPy standalone.",
            "Please point the PYTHON_STANDALONE_HOME environment variables properly.",
            f"PYTHON_STANDALONE_HOME={graalpy_home}",
            sep="\n")

def get_graalvm_version():
    graalvmVersion, _ = run_cmd([get_gp(), "-c", "print(__graalpython__.get_graalvm_version(), end='')"], os.environ.copy())
    # when JLine is cannot detect a terminal, it prints logging info
    graalvmVersion = graalvmVersion.split("\n")[-1]
    # we never test -dev versions here, we always pretend to use release versions
    graalvmVersion = graalvmVersion.split("-dev")[0]
    return graalvmVersion

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

def patch_pom_repositories(pom):
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
                            <updatePolicy>never</updatePolicy>
                        </releases>
                        <snapshots>
                            <enabled>true</enabled>
                            <updatePolicy>never</updatePolicy>
                        </snapshots>
                    </repository>
                """)
            pluginRepos.append(f"""
                    <pluginRepository>
                        <id>myrepo{idx}</id>
                        <url>{custom_repo}</url>
                        <releases>
                            <enabled>true</enabled>
                        </releases>
                        <snapshots>
                            <enabled>true</enabled>
                        </snapshots>
                    </pluginRepository>
                """)

        with open(pom, "r") as f:
            contents = f.read()
        with open(pom, "w") as f:
            f.write(contents.replace("</project>", """
                <repositories>
                """ + '\n'.join(repos) + """
                </repositories>
                <pluginRepositories>
                """ + '\n'.join(pluginRepos) + """
                </pluginRepositories>
                </project>
                """))

def replace_in_file(file, str, replace_str):
    with open(file, "r") as f:
        contents = f.read()
    assert str in contents
    with open(file, "w") as f:
        f.write(contents.replace(str, replace_str))


def override_gradle_properties_file(gradle_project_root):
    if override_file:=os.environ.get('GRADLE_PROPERTIES_OVERRIDE'):
        shutil.copy(override_file, os.path.join(gradle_project_root, "gradle", "wrapper", "gradle-wrapper.properties"))


def override_maven_properties_file(maven_project_root):
    if override_file:=os.environ.get('MAVEN_PROPERTIES_OVERRIDE'):
        shutil.copy(override_file, os.path.join(maven_project_root, ".mvn", "wrapper", "maven-wrapper.properties"))
