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
import subprocess
import tempfile
import unittest
import shutil
import glob

is_enabled = 'ENABLE_STANDALONE_UNITTESTS' in os.environ and os.environ['ENABLE_STANDALONE_UNITTESTS'] == "true"
MVN_CMD = [shutil.which('mvn')]
if 'MAVEN_REPO_OVERRIDE' in os.environ:
    MVN_CMD += ['-Dmaven.repo.remote=' + os.environ['MAVEN_REPO_OVERRIDE']]

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
    java_home = os.path.join(__graalpython__.home, "..", "..")

    ni = get_executable(os.path.join(java_home, "bin", "native-image"))
    jc = get_executable(os.path.join(java_home, "bin", "javac"))
    graalpy = get_executable(os.path.join(java_home, "bin", "graalpy"))
    java = get_executable(os.path.join(java_home, "bin", "java"))

    if not os.path.isfile(graalpy) or not os.path.isfile(java) or not os.path.isfile(jc) or not os.path.isfile(ni):
        print(
            "Standalone module tests require a GraalVM installation including graalpy, java, javac and native-image",
            "Please point the JAVA_HOME environment variable to such a GraalVM root.",
            "__graalpython__.home : " + java_home,
            "native-image exists: " + str(os.path.exists(ni)),
            "javac exists: " + str(os.path.exists(jc)),
            "graalpy exits: " + str(os.path.exists(graalpy)),
            "java exists: " + str(os.path.exists(java)),
            sep="\n",
        )
        assert False

    print("Running tests for standalone module:")
    print("  __graalpython__.home:", __graalpython__.home)
    print("  java_home:", java_home)
    print("  graalpy:", graalpy)
    print("  java:", java)

    return java_home, graalpy, java

def get_env(java_home):
    env = os.environ.copy()
    env.update({"JAVA_HOME" : java_home})

    graalvm_home = os.environ.get("GRAALVM_HOME", java_home)
    if "*" in graalvm_home:
        graalvm_home = os.path.abspath(glob.glob(graalvm_home)[0])
        print("Patching GRAALVM_HOME: ", graalvm_home)
        env.update({"GRAALVM_HOME" : graalvm_home})

    to_be_removed = []
    for k in env:
        # subprocess complaining about key names with "=" in them
        if "=" in k:
            to_be_removed.append(k)
    for k in to_be_removed:
        del env[k]
    if len(to_be_removed) > 0:
        print("\ntest_standalone: removed keys from subprocess environment :", to_be_removed)

    return env

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_polyglot_app():

    java_home, graalpy, java = get_gp()
    env = get_env(java_home)

    with tempfile.TemporaryDirectory() as tmpdir:

        target_dir = os.path.join(tmpdir, "polyglot_app_test")

        cmd = [graalpy, "-m", "standalone", "--verbose", "polyglot_app", "-o", target_dir]
        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(p.stdout.decode(errors='backslashreplace'))
        print(p.stderr.decode(errors='backslashreplace'))
        assert "Creating polyglot java python application in directory " + target_dir in out

        cmd = MVN_CMD + ["package", "-Pnative"]
        p = subprocess.run(cmd, cwd=target_dir, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "BUILD SUCCESS" in out

        cmd = [os.path.join(target_dir, "target", "polyglot_app")]
        p = subprocess.run(cmd, cwd=target_dir, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert out.endswith("hello java\n")

        cmd = MVN_CMD + ["package", "-Pjar"]
        p = subprocess.run(cmd, cwd=target_dir, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "BUILD SUCCESS" in out

        cmd = [java, "-jar", os.path.join(target_dir, "target", "polyglot_app-1.0-SNAPSHOT.jar")]
        p = subprocess.run(cmd, cwd=target_dir, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert out.endswith("hello java\n")

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_one_file():
    java_home, graalpy, java = get_gp()
    if graalpy is None or java is None:
        return

    env = get_env(java_home)

    with tempfile.TemporaryDirectory() as tmpdir:

        source_file = os.path.join(tmpdir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("import sys\n")
            f.write("print('hello world, argv[1:]:', sys.argv[1:])")

        target_file = os.path.join(tmpdir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-m", source_file, "-o", target_file]

        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "Bundling Python resources into" in out

        cmd = [target_file, "arg1", "arg2"]
        p = subprocess.run(" ".join(cmd), env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "hello world, argv[1:]: " + str(cmd[1:]) in out

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_one_file_venv():
    java_home, graalpy, java = get_gp()
    if graalpy is None or java is None:
        return

    env = get_env(java_home)

    with tempfile.TemporaryDirectory() as target_dir:
        source_file = os.path.join(target_dir, "hello.py")
        with open(source_file, 'w') as f:
            f.write("from termcolor import colored, cprint\n")
            f.write("colored_text = colored('hello standalone world', 'red', attrs=['reverse', 'blink'])\n")
            f.write("print(colored_text)\n")

        venv_dir = os.path.join(target_dir, "venv")
        cmd = [graalpy, "-m", "venv", venv_dir]
        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))

        venv_python = os.path.join(venv_dir, "Scripts", "python.cmd") if os.name == "nt" else os.path.join(venv_dir, "bin", "python")
        cmd = [venv_python, "-m", "pip", "--no-cache-dir", "install", "termcolor"]
        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))

        target_file = os.path.join(target_dir, "hello")
        cmd = [graalpy, "-m", "standalone", "--verbose", "native", "-Os", "-m", source_file, "--venv", venv_dir, "-o", target_file]
        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "Bundling Python resources into" in out

        cmd = [target_file]
        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))

        assert "hello standalone world" in out

@unittest.skipUnless(is_enabled, "ENABLE_STANDALONE_UNITTESTS is not true")
def test_native_executable_module():
    java_home, graalpy, java = get_gp()
    if graalpy is None or java is None:
        return

    env = get_env(java_home)

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

        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "Bundling Python resources into" in out

        cmd = [target_file]
        p = subprocess.run(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out = p.stdout.decode(errors='backslashreplace')
        print(out)
        print(p.stderr.decode(errors='backslashreplace'))
        assert "hello standalone world" in out
