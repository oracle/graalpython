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
import re
import select
import subprocess
import sys
import tempfile
import termios
from textwrap import dedent


def validate_repl(stdin, python_args=(), ignore_preamble=True):
    env = os.environ.copy()
    env['TERM'] = 'ansi'
    env['PYTHONIOENCODING'] = 'utf-8'
    pty_parent, pty_child = os.openpty()
    try:
        termios.tcsetwinsize(pty_parent, (60, 80))
        proc = subprocess.Popen(
            [sys.executable, '-I', *python_args],
            env=env,
            stdin=pty_child,
            stdout=pty_child,
            stderr=pty_child,
        )
        out = ''
        input_and_output = []
        expected_preamble = ''
        in_matches = list(re.finditer(r'^(>>>|\.\.\.) (.*)', stdin, flags=re.MULTILINE))
        for i, match in enumerate(in_matches):
            if i == 0:
                expected_preamble = stdin[:match.start() - 1] if match.start() else ''
            input_and_output.append((
                match.group(1),
                match.group(2),
                stdin[match.end():in_matches[i + 1].start() - 1 if i + 1 < len(in_matches) else -1],
            ))
        index = -1
        whole_out = ''
        while True:
            rlist, _, _ = select.select([pty_parent], [], [], 30)
            assert pty_parent in rlist, f"Timed out waiting for REPL output. Output: {whole_out}{out}"
            out += os.read(pty_parent, 1024).decode('utf-8')
            out = out.replace('\r\n', '\n')
            out = re.sub(r'\x1b\[(?:\?2004[hl]|\d+[A-G])', '', out)
            if out == '>>> ' or out.endswith(('\n>>> ', '\n... ')):
                prompt = out[:3]
                actual = out[:-5]
                if index >= 0:
                    expected_prompt, current_in, expected_out = input_and_output[index]
                    assert prompt == expected_prompt
                    expected = f'{expected_prompt} {current_in}{expected_out}'
                else:
                    expected = expected_preamble
                if index >= 0 or not ignore_preamble:
                    assert actual == expected, f'Actual:\n{actual!r}\nExpected:\n{expected!r}'
                index += 1
                whole_out += out[:-4]
                out = out[-4:]
                if index >= len(input_and_output):
                    os.write(pty_parent, b'\x04')  # CTRL-D
                    proc.wait(timeout=30)
                    out = os.read(pty_parent, 1024).decode('utf-8')
                    out = re.sub(r'\x1b\[\?2004[hl]', '', out)
                    assert not out.strip(), f"Garbage after EOF:\n{out!r}"
                    return
                else:
                    _, next_in, _ = input_and_output[index]
                    os.write(pty_parent, next_in.encode('utf-8') + b'\r')
    finally:
        os.close(pty_child)
        os.close(pty_parent)


def test_basic_repl():
    validate_repl(dedent("""\
        >>> 1023 + 1
        1024
        >>> None
        >>> "hello"
        'hello'
        >>> _
        'hello'
    """))


def test_continuation():
    validate_repl(dedent(r'''\
        >>> def foo():
        ...   a = 1
        ...   return a
        ... 
        >>> class Foo:
        ...   def meth(self):
        ...     return 1
        ... 
        >>> from functools import wraps
        >>> @wraps
        ... def foo(fn):
        ...   return fn
        ... 
        >>> from contextlib import contextmanager
        >>> @contextmanager
        ... class Foo:
        ...   pass
        ... 
        >>> """
        ... asdf
        ... """
        '\nasdf\n'
    '''))


def test_exceptions():
    validate_repl(dedent("""\
        >>> 1 / 0
        Traceback (most recent call last):
          File "<stdin>", line 1, in <module>
        ZeroDivisionError: division by zero
        >>> import sys
        >>> sys.last_value
        ZeroDivisionError('division by zero')
        >>> class BrokenRepr:
        ...   def __repr__(self):
        ...     asdf
        ... 
        >>> BrokenRepr()
        Traceback (most recent call last):
          File "<stdin>", line 1, in <module>
          File "<stdin>", line 3, in __repr__
        NameError: name 'asdf' is not defined
    """))


def test_inspect_flag():
    with tempfile.NamedTemporaryFile('w') as f:
        f.write('a = 1\n')
        f.flush()
        validate_repl(dedent("""\
            >>> a
            1
        """), python_args=['-i', f.name], ignore_preamble=False)


def test_inspect_flag_exit():
    with tempfile.NamedTemporaryFile('w') as f:
        f.write('a = 1\nimport sys\nsys.exit(1)\n')
        f.flush()
        validate_repl(dedent(f"""\
            Traceback (most recent call last):
              File "{f.name}", line 3, in <module>
                sys.exit(1)
            SystemExit: 1
            >>> a
            1
        """), python_args=['-i', f.name], ignore_preamble=False)
