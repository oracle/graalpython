#!/usr/bin/env python3
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def _find_eclipse():
    for path in [
            Path(os.environ.get('ECLIPSE_EXE', '')),
            Path(os.environ.get('ECLIPSE', '')),
            Path(__file__).resolve().parent.parent,
            Path(shutil.which('eclipse') or ''),
    ]:
        if path.is_dir():
            candidates = [
                path / 'eclipse',
                path / 'eclipse.exe',
                path / 'Eclipse.app' / 'Contents' / 'MacOS' / 'eclipse',
            ]
        else:
            candidates = [path]
            for candidate in candidates:
                if candidate.is_file() and os.access(candidate, os.X_OK):
                    return candidate
    print('Eclipse executable not found; set ECLIPSE_EXE', file=sys.stderr)
    sys.exit(1)


def _run_formatter(eclipse, profile, files):
    with (
            tempfile.TemporaryDirectory(prefix='econf') as configuration_dir,
            tempfile.TemporaryDirectory(prefix='ews-') as workspace
    ):
        command = [
            str(eclipse),
            '--launcher.suppressErrors',
            '-nosplash',
            '-configuration',
            configuration_dir,
            '-application',
            '-consolelog',
            '-data',
            workspace,
            'org.eclipse.jdt.core.JavaCodeFormatter',
            '-config',
            str(profile),
        ] + [str(path) for path in files]
        return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)


def main(argv=None):
    files = [Path(path).absolute() for path in sys.argv[1:] if path.endswith('.java')]
    if not files:
        return 0

    profile = (
        Path(__file__).resolve().parent.parent /
        'mx.graalpython' /
        'eclipse-settings' /
        'org.eclipse.jdt.core.prefs'
    )
    eclipse = _find_eclipse()
    original_contents = [path.read_bytes() for path in files]
    try:
        result = _run_formatter(eclipse, profile, files)
    except OSError as error:
        print(f'error: could not run Eclipse formatter: {error}', file=sys.stderr)
        sys.exit(1)
    if result.returncode != 0:
        if result.stdout:
            print(result.stdout, end='', file=sys.stderr)
        print(f'error: Eclipse formatter exited with status {result.returncode}', file=sys.stderr)
        sys.exit(result.returncode)

    for path, original in zip(files, original_contents):
        if path.read_bytes() != original:
            sys.exit(1)


if __name__ == '__main__':
    main()
