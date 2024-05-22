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

MAVEN_VERSION = "3.9.6"

def run_cmd(cmd, env, cwd=None, print_out=False):
    out = []
    out.append(f"Executing:\n    {cmd=}\n")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    if print_out:
        print("============== output =============")
    for line in iter(process.stdout.readline, ""):
        out.append(line)
        if print_out:
            print(line, end="")
    if print_out:
        print("\n========== end of output ==========")
    return "".join(out), process.wait()

def check_ouput(txt, out, contains=True):
    if contains and txt not in out:
        print_output(out, f"expected '{txt}' in output")
        assert False
    elif not contains and txt in out:
        print_output(out, f"did not expect '{txt}' in output")
        assert False

def print_output(out, err_msg):
    print("============== output =============")
    for line in out:
        print(line, end="")
    print("\n========== end of output ==========")
    print("", err_msg, "", sep="\n")

def get_mvn_wrapper(project_dir, env):
    if 'win32' != sys.platform:
        cmd = [shutil.which('mvn'), "--batch-mode", "wrapper:wrapper", f"-Dmaven={MAVEN_VERSION}"]
        out, return_code = run_cmd(cmd, env, cwd=project_dir)
        check_ouput("BUILD SUCCESS", out)
        mvn_cmd = [os.path.abspath(os.path.join(project_dir, "mvnw")),  "--batch-mode"]
    else:
        # TODO installing mvn wrapper with the current mvn 3.3.9 on gates does not work
        # we have to provide the mvnw.cmd script
        mvnw_dir = os.path.join(os.path.dirname(__file__), "mvnw")
        mvn_cmd = [os.path.abspath(os.path.join(mvnw_dir, "mvnw.cmd")),  "--batch-mode"]

    cmd = mvn_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    check_ouput(MAVEN_VERSION, out)
    return mvn_cmd

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