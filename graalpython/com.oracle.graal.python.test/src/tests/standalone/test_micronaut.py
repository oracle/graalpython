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
import unittest
import shutil
import difflib
import tempfile
import sys

is_enabled = 'ENABLE_MICRONAUT_UNITTESTS' in os.environ and os.environ['ENABLE_MICRONAUT_UNITTESTS'] == "true"

MAVEN_VERSION = "3.9.6"

def run_cmd(cmd, env, cwd=None):
    out = []
    out.append(f"Executing:\n    {cmd=}\n")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    for line in iter(process.stdout.readline, ""):
        out.append(line)
    return "".join(out), process.wait()

def patch_pom(pom):
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

def get_gp():
    graalpy_home = os.environ["PYTHON_STANDALONE_HOME"]
    graalpy = get_executable(os.path.join(graalpy_home, "bin", "graalpy"))

    if not os.path.isfile(graalpy):
        print(
            "Micronaut extension tests require a GraalPy standalone.",
            "Please point the JAVA_HOME and PYTHON_STANDALONE_HOME environment variables properly.",
            f"{graalpy_home=}",
            "graalpy exits: " + str(os.path.exists(graalpy)),
            sep="\n",
            )
        assert False
    return graalpy

def get_graalvm_version():
    graalvmVersion, _ = run_cmd([get_gp(), "-c", "print(__graalpython__.get_graalvm_version(), end='')"], os.environ.copy())
    # when JLine is cannot detect a terminal, it prints logging info
    graalvmVersion = graalvmVersion.split("\n")[-1]
    # we never test -dev versions here, we always pretend to use release versions
    graalvmVersion = graalvmVersion.split("-dev")[0]
    return graalvmVersion

def create_hello_app(hello_app_dir, target_dir):
    for root, dirs, files in os.walk(hello_app_dir):
        for file in files:

            if file.endswith("ContextFactory.j"):
                # will copy later, when needed
                continue

            source_file = os.path.join(root, file)
            if file.endswith(".j"):
                file = file[0:len(file)- 1] + "java"
            elif file.endswith(".p"):
                file = file[0:len(file)- 1] + "py"
            target_root = os.path.join(target_dir, root[len(hello_app_dir) + 1:])
            target_file = os.path.join(target_root, file)
            os.makedirs(os.path.dirname(target_file), exist_ok=True)
            shutil.copyfile(source_file, target_file)

    pom = os.path.join(target_dir, "pom.xml")
    with open(pom, 'r') as f:
        lines = f.readlines()
    with open(pom, 'w') as f:
        for line in lines:
            if "{graalpy-maven-plugin-version}" in line:
                line = line.replace("{graalpy-maven-plugin-version}", get_graalvm_version())
            f.write(line)

def diff_texts(a, b, a_filename, b_filename):
    a = a.splitlines()
    b = b.splitlines()
    return difflib.unified_diff(a, b, a_filename, b_filename, "(generated)", "(expexted)", lineterm="")

def check_golden_file(file, golden):
    if not os.path.exists(golden):
        shutil.copyfile(file, golden)
        return

    found_diff = False
    with open(file) as f, open(golden) as fg:
        f_contents = f.read()
        fg_contets = fg.read()

        diff = diff_texts(f_contents, fg_contets, file, golden)
        for s in diff:
            found_diff = True
            print(s)

    return found_diff

def print_output(out):
    print("============== output =============")
    for line in out:
        print(line, end="")
    print("\n========== end of output ==========")

def check_ouput(txt, out, contains=True):
    if contains and txt not in out:
        print_output(out)
        assert False, f"expected '{txt}' in output"
    elif not contains and txt in out:
        print_output(out)
        assert False, f"did not expect '{txt}' in output"

def get_mvn_wrapper(project_dir, env):
    if 'win32' != sys.platform:
        cmd = [shutil.which('mvn'), "--batch-mode", "wrapper:wrapper", f"-Dmaven={MAVEN_VERSION}"]
        out, return_code = run_cmd(cmd, env, cwd=project_dir)
        check_ouput("BUILD SUCCESS", out)
        mvn_cmd = [os.path.abspath(os.path.join(project_dir, "mvnw")),  "--batch-mode"]
    else:
        # TODO installing mvn wrapper with the current mvn 3.3.9 on gates does not work
        # we have to provide the mvnw.bat script
        mvnw_dir = os.path.join(os.path.dirname(__file__), "mvnw")
        mvn_cmd = [os.path.abspath(os.path.join(mvnw_dir, "mvnw.cmd")),  "--batch-mode"]

    print("mvn --version ...")
    cmd = mvn_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    check_ouput("3.9.6", out)
    print_output(out)
    return mvn_cmd

class MicronautAppTest(unittest.TestCase):
    def setUpClass(self):
        if not is_enabled:
            return

        self.env = os.environ.copy()
        self.env["PYLAUNCHER_DEBUG"] = "1"

    @unittest.skipUnless(is_enabled, "ENABLE_MICRONAUT_UNITTESTS is not true")
    def test_hello_app(self):
        hello_app_dir = os.path.join(os.path.dirname(__file__), "micronaut/hello")
        with tempfile.TemporaryDirectory() as target_dir:
            create_hello_app(hello_app_dir, target_dir)

            pom_file = os.path.join(target_dir, "pom.xml")
            patch_pom(pom_file)

            mvn_cmd = get_mvn_wrapper(target_dir, self.env)

            # clean
            print("clean micronaut hello app ...")
            cmd = mvn_cmd + ["clean"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            check_ouput("BUILD SUCCESS", out)

            # build
            # java unittests are executed during the build
            print("build micronaut hello app ...")
            cmd = mvn_cmd + ["package"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            check_ouput("BUILD SUCCESS", out)
            check_ouput("=== CREATED REPLACE CONTEXT ===", out, False)

            # build and execute tests with a custom graalpy context factory
            source_file = os.path.join(hello_app_dir, "src/main/java/example/micronaut/ContextFactory.j")
            target_file = os.path.join(target_dir, "src/main/java/example/micronaut/ContextFactory.java")
            shutil.copyfile(source_file, target_file)

            print("build micronaut hello app with custom context factory ...")
            cmd = mvn_cmd + ["package"]
            out, return_code = run_cmd(cmd, self.env, cwd=target_dir)
            check_ouput("BUILD SUCCESS", out)
            check_ouput("=== CREATED REPLACE CONTEXT ===", out)

unittest.skip_deselected_test_functions(globals())
