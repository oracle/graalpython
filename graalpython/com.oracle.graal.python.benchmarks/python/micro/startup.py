# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import sys
import tempfile
from pathlib import Path

# Use a C runner to spawn the subprocesses to avoid counting subprocess module overhead into the benchmark
RUNNER_CODE = '''
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>

int main(int argc, char *argv[]) {
    if (argc < 3) {
        return 1;
    }
    int n = atoi(argv[1]);
    if (n <= 0) {
        return 1;
    }
    char **cmd_argv = &argv[2];
    for (int i = 0; i < n; ++i) {
        pid_t pid = fork();
        if (pid < 0) {
            perror("fork");
            return 1;
        } else if (pid == 0) {
            execvp(cmd_argv[0], cmd_argv);
            perror("execvp");
            exit(127);  // If exec fails
        } else {
            int status;
            if (waitpid(pid, &status, 0) < 0) {
                perror("waitpid");
                return 1;
            }
        }
    }
    return 0;
}
'''

TMPDIR = tempfile.TemporaryDirectory()
RUNNER_EXE = None
ORIG_ARGV = None


def __setup__(*args):
    global RUNNER_EXE
    tmpdir = Path(TMPDIR.name)
    runner_c = tmpdir / 'runner.c'
    runner_c.write_text(RUNNER_CODE)
    RUNNER_EXE = tmpdir / 'runner'
    subprocess.check_call([os.environ.get('CC', 'gcc'), runner_c, '-O2', '-o', RUNNER_EXE])

    global ORIG_ARGV
    ORIG_ARGV = sys.orig_argv
    for i, arg in enumerate(ORIG_ARGV):
        if arg.endswith('.py'):
            ORIG_ARGV = ORIG_ARGV[:i]
            break


def __teardown__():
    TMPDIR.cleanup()


def __benchmark__(num=1000000):
    subprocess.check_call([
        str(RUNNER_EXE),
        str(num),
        *ORIG_ARGV,
        "-I",  # isolate from environment
        "-S",  # do not import site
        "-B",  # do not attempt to write pyc files
        "-u",  # do not add buffering wrappers around output streams
        "-c",
        "1"
    ])
