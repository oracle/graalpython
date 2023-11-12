# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import glob
import subprocess
import tempfile
import unittest
import urllib.parse
import shutil
import sys

is_enabled = 'ENABLE_STANDALONE_UNITTESTS' in os.environ and os.environ['ENABLE_STANDALONE_UNITTESTS'] == "true"
MVN_CMD = [shutil.which('mvn')]

def run_cmd(cmd, env, cwd=None):
    print(f"Executing:\n    {cmd=}\n")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    out = []
    for line in iter(process.stdout.readline, ""):
        print(line, end="")
        out.append(line)
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
            "java exists: " + str(os.path.exists(java)),
            sep="\n",
        )
        assert False

    print("Running tests for standalone module:")
    print("  graalpy_home:", graalpy_home)
    print("  graalpy     :", graalpy)
    print("  java_home   :", java_home)

    return graalpy

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_polyglot_app():
    env = os.environ.copy()
    env["PYLAUNCHER_DEBUG"] = "1"
    with tempfile.TemporaryDirectory() as tmpdir:
        target_name = "polyglot_app_test"
        target_dir = os.path.join(tmpdir, target_name)

        archetypeGroupId = "org.graalvm.python"
        archetypeArtifactId = "graalpy-archetype-polyglot-app"
        pluginArtifactId = "graalpy-maven-plugin"
        graalvmVersion, _ = run_cmd([get_gp(), "-c", "print(__graalpython__.get_graalvm_version(), end='')"], env)
        # when JLine is cannot detect a terminal, it prints logging info
        graalvmVersion = graalvmVersion.split("\n")[-1]

        for custom_repo in os.environ.get("MAVEN_REPO_OVERRIDE", "").split(","):
            url = urllib.parse.urlparse(custom_repo)
            if url.scheme == "file":
                jar = os.path.join(
                    url.path,
                    archetypeGroupId.replace(".", os.path.sep),
                    archetypeArtifactId,
                    graalvmVersion,
                    f"{archetypeArtifactId}-{graalvmVersion}.jar",
                )
                cmd = MVN_CMD + [
                    "install:install-file",
                    f"-Dfile={jar}",
                    f"-DgroupId={archetypeGroupId}",
                    f"-DartifactId={archetypeArtifactId}",
                    f"-Dversion={graalvmVersion}",
                    "-Dpackaging=jar",
                    "-DgeneratePom=true",
                    "-DcreateChecksum=true",
                ]
                out, return_code = run_cmd(cmd, env)
                assert return_code == 0

                jar = os.path.join(
                    url.path,
                    archetypeGroupId.replace(".", os.path.sep),
                    pluginArtifactId,
                    graalvmVersion,
                    f"{pluginArtifactId}-{graalvmVersion}.jar",
                )
                cmd = MVN_CMD + [
                    "install:install-file",
                    f"-Dfile={jar}",
                    f"-DgroupId={archetypeGroupId}",
                    f"-DartifactId={pluginArtifactId}",
                    f"-Dversion={graalvmVersion}",
                    "-Dpackaging=jar",
                    "-DgeneratePom=true",
                    "-DcreateChecksum=true",
                ]
                out, return_code = run_cmd(cmd, env)
                assert return_code == 0
                break

        cmd = MVN_CMD + [
            "archetype:generate",
            "-B",
            f"-DarchetypeGroupId={archetypeGroupId}",
            f"-DarchetypeArtifactId={archetypeArtifactId}",
            f"-DarchetypeVersion={graalvmVersion}",
            f"-DartifactId={target_name}",
            "-DgroupId=archetype.it",
            "-Dpackage=it.pkg",
            "-Dversion=0.1-SNAPSHOT",
        ]
        out, return_code = run_cmd(cmd, env, cwd=tmpdir)
        assert "BUILD SUCCESS" in out

        if custom_repos := os.environ.get("MAVEN_REPO_OVERRIDE"):
            repos = []
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
            with open(os.path.join(target_dir, "pom.xml"), "r") as f:
                contents = f.read()
            with open(os.path.join(target_dir, "pom.xml"), "w") as f:
                f.write(contents.replace("</project>", """
                <repositories>
                """ + '\n'.join(repos) + """
                </repositories>
                </project>
                """))

        env["MVN"] = " ".join(MVN_CMD + [f"-Dgraalpy.version={graalvmVersion}", "-Dgraalpy.edition=python-community"])
        cmd = MVN_CMD + ["dependency:purge-local-repository", f"-Dgraalpy.version={graalvmVersion}", "-Dgraalpy.edition=python-community"]
        run_cmd(cmd, env, cwd=target_dir)
        try:
            cmd = MVN_CMD + ["package", "-Pnative", "-DmainClass=it.pkg.GraalPy", f"-Dgraalpy.version={graalvmVersion}", "-Dgraalpy.edition=python-community"]
            out, return_code = run_cmd(cmd, env, cwd=target_dir)
            assert "BUILD SUCCESS" in out

            cmd = [os.path.join(target_dir, "target", "polyglot_app_test")]
            out, return_code = run_cmd(cmd, env, cwd=target_dir)
            assert "hello java" in out
        finally:
            cmd = MVN_CMD + ["dependency:purge-local-repository", "-DreResolve=false", f"-Dgraalpy.version={graalvmVersion}", "-Dgraalpy.edition=python-community"]
            run_cmd(cmd, env, cwd=target_dir)

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_one_file():
    graalpy = get_gp()
    if graalpy is None:
        return
    env = os.environ.copy() 

    with tempfile.TemporaryDirectory() as tmpdir:

        source_file = os.path.join(tmpdir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("import sys\n")
            f.write("print('hello world, argv[1:]:', sys.argv[1:])")

        target_file = os.path.join(tmpdir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-m", source_file, "-o", target_file]

        out, return_code = run_cmd(cmd, env)
        assert "Bundling Python resources into" in out

        cmd = [target_file, "arg1", "arg2"]
        out, return_code = run_cmd(cmd, env)
        assert "hello world, argv[1:]: " + str(cmd[1:]) in out

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_venv_and_one_file():
    graalpy = get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()

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
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-Os", "-m", source_file, "--venv", venv_dir, "-o", target_file]
        out, return_code = run_cmd(cmd, env)
        assert "Bundling Python resources into" in out

        cmd = [target_file]
        out, return_code = run_cmd(cmd, env)

        assert "hello standalone world" in out
        assert "key=value" in out

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_module():
    graalpy = get_gp()
    if graalpy is None:
        return
    env = os.environ.copy()

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
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-Os", "-m", module_dir, "-o", target_file]

        out, return_code = run_cmd(cmd, env)
        assert "Bundling Python resources into" in out

        cmd = [target_file]
        out, return_code = run_cmd(cmd, env)
        assert "hello standalone world" in out
