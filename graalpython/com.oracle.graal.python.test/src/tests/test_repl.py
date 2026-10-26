# Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import errno
import os
import platform
import re
import select
import subprocess
import sys
import tempfile
import time
import unittest
from dataclasses import dataclass
from textwrap import dedent

if (sys.platform != 'win32' and (sys.platform != 'linux' or platform.machine() != 'aarch64')) and (
        sys.implementation.name != 'graalpy' or __graalpython__.posix_module_backend() != 'java'):

    @dataclass
    class ExpectedInOutItem:
        prompt: str
        input: str
        output: str
        keys: str | None = None
        autoindent: int | None = None


    ANSI_ESCAPE = re.compile(r'\x1b(?:[=>]|\[[0-?]*[ -/]*[@-~])')


    def normalize_terminal_output(output):
        output = ANSI_ESCAPE.sub('', output)
        output = re.sub(r'\r+\n', '\n', output).replace('\r', '')
        return re.sub(r'<python-input-\d+>', '<stdin>', output)


    def validate_repl(stdin, python_args=(), ignore_preamble=True, extra_input_and_output=(),
                      repls=('basic', 'pyrepl'), extra_env=None):
        for repl in repls:
            try:
                _validate_repl(stdin, repl, python_args, ignore_preamble, extra_input_and_output,
                               extra_env)
            except Exception as e:
                e.add_note(f'while testing the {repl} REPL')
                raise


    def _validate_repl(stdin, repl, python_args, ignore_preamble, extra_input_and_output, extra_env):
        env = {
            'TERM': 'ansi',
            'NO_COLOR': '1',
            'PYTHONIOENCODING': 'utf-8',
        }
        if repl == 'basic':
            env['PYTHON_BASIC_REPL'] = '1'
        if extra_env:
            env.update(extra_env)
        pty_parent, pty_child = os.openpty()
        proc = None
        try:
            import termios
            termios.tcsetwinsize(pty_parent, (60, 80))
            proc = subprocess.Popen(
                [sys.executable, '-s', '-P', *python_args],
                env=env,
                stdin=pty_child,
                stdout=pty_child,
                stderr=pty_child,
            )
            os.close(pty_child)
            pty_child = None
            raw_out = ''
            out = ''
            input_and_output: list[ExpectedInOutItem] = []
            expected_preamble = ''
            in_matches = list(re.finditer(r'^(>>>|\.\.\.) ?(.*)', stdin, flags=re.MULTILINE))
            for i, match in enumerate(in_matches):
                if i == 0:
                    expected_preamble = stdin[:match.start() - 1] if match.start() else ''
                prompt = match.group(1)
                expected_input = match.group(2)
                expected_output = stdin[match.end():in_matches[i + 1].start() - 1 if i + 1 < len(in_matches) else -1]
                input_and_output.append(ExpectedInOutItem(prompt, expected_input, expected_output))
            input_and_output.extend(extra_input_and_output)
            index = -1
            whole_out = ''
            while True:
                rlist, _, _ = select.select([pty_parent], [], [], 60)
                assert pty_parent in rlist, f"Timed out waiting for REPL output. Output: {whole_out}{out}"
                raw_out += os.read(pty_parent, 1024).decode('utf-8')
                out = normalize_terminal_output(raw_out)
                prompt_match = re.search(r'(>>> |\.\.\. )( *)$', out)
                if prompt_match and select.select([pty_parent], [], [], 0.05)[0]:
                    # Both line editors can emit cursor-control sequences after the visible prompt.
                    # Wait for the terminal update to settle before sending the next input.
                    continue
                if prompt_match and (index < 0 or prompt_match.start() > 0):
                    prompt = out[:3]
                    autoindent = len(prompt_match.group(2))
                    actual_end = prompt_match.start()
                    if actual_end > 0 and out[actual_end - 1] == '\n':
                        actual_end -= 1
                    actual = out[:actual_end]
                    if index >= 0:
                        current = input_and_output[index]
                        assert prompt == current.prompt, f"Actual prompt: {prompt}\nExpected prompt: {current.prompt}\nOutput: {whole_out}{out}"
                        expected = f'{current.prompt} {current.input}{current.output}'
                    else:
                        expected = expected_preamble
                    if index >= 0 or not ignore_preamble:
                        assert actual == expected, f'Actual:\n{actual!r}\nExpected:\n{expected!r}\nWhole output:\n{whole_out}{out}'
                    index += 1
                    if index < len(input_and_output):
                        expected_autoindent = input_and_output[index].autoindent
                        if expected_autoindent is not None:
                            assert autoindent == expected_autoindent, \
                                f'Actual autoindent: {autoindent}\nExpected autoindent: {expected_autoindent}'
                    whole_out += out[:actual_end]
                    raw_out = prompt_match.group(1)
                    if index >= len(input_and_output):
                        os.write(pty_parent, b'\x04')  # Ctrl-D / EOF
                        # Keep draining the PTY while waiting for the child. In particular, PyREPL
                        # restores the terminal with TCSADRAIN, which can block on macOS until the
                        # master consumes all pending terminal-control output.
                        eof_output = bytearray()
                        deadline = time.monotonic() + 60
                        while True:
                            remaining = deadline - time.monotonic()
                            if remaining <= 0:
                                raise subprocess.TimeoutExpired(proc.args, 60, output=bytes(eof_output))
                            rlist, _, _ = select.select([pty_parent], [], [], remaining)
                            if not rlist:
                                raise subprocess.TimeoutExpired(proc.args, 60, output=bytes(eof_output))
                            try:
                                chunk = os.read(pty_parent, 1024)
                            except OSError as e:
                                if e.errno == errno.EIO:
                                    break
                                raise
                            if not chunk:
                                break
                            eof_output.extend(chunk)
                        proc.wait(timeout=max(0, deadline - time.monotonic()))
                        out = eof_output.decode('utf-8')
                        out = normalize_terminal_output(out)
                        assert not out.strip(), f"Garbage after EOF:\n{out!r}"
                        return
                    else:
                        current = input_and_output[index]
                        next_in = current.keys if current.keys is not None else current.input
                        if repl == 'pyrepl' and autoindent:
                            expected_input = current.input
                            indentation = expected_input[:len(expected_input) - len(expected_input.lstrip())]
                            raw_out += indentation
                            if next_in.strip() and current.keys is None:
                                next_in = next_in[len(indentation):]
                            elif not next_in.strip():
                                # Clear PyREPL's automatic indentation so an empty line terminates
                                # the compound statement.
                                next_in = '\x15'
                        os.write(pty_parent, next_in.encode('utf-8') + b'\n')
        finally:
            os.close(pty_parent)
            if pty_child is not None:
                os.close(pty_child)
            if proc is not None and proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()


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


    def test_repl_selection():
        validate_repl(dedent("""\
            >>> hasattr(__import__('__main__'), '__file__')
            False
        """), repls=('basic',))
        validate_repl(dedent("""\
            >>> __import__('__main__').__file__.endswith('/_pyrepl/__main__.py')
            True
        """), repls=('pyrepl',))


    @unittest.skipIf(sys.platform == 'darwin', "Fails with garbage after EOF")
    def test_basic_repl_no_readline():
        validate_repl(dedent("""\
            >>> 1023 + 1
            1024
            >>> None
            >>> "hello"
            'hello'
            >>> _
            'hello'
        """), python_args=['-S'])


    def test_basic_repl_flushes_std_streams():
        validate_repl("", extra_input_and_output=[
            ExpectedInOutItem('>>>', '40 + 2', '\n42'),
            ExpectedInOutItem('>>>', 'import sys; _ = sys.stderr.write("stderr")', '\nstderr'),
            ExpectedInOutItem('>>>', 'import sys; _ = sys.stdout.write("stdout")', '\nstdout'),
        ], repls=('basic',))


    def test_pyrepl_flushes_stdout():
        # CPython's PyREPL flushes stdout, but not stderr, before reading the next line.
        validate_repl("", extra_input_and_output=[
            ExpectedInOutItem('>>>', '40 + 2', '\n42'),
            ExpectedInOutItem('>>>', 'import sys; _ = sys.stdout.write("stdout")', '\nstdout'),
        ], repls=('pyrepl',))


    def test_pyrepl_autoindent():
        validate_repl("", extra_input_and_output=[
            ExpectedInOutItem('>>>', 'def answer():', ''),
            ExpectedInOutItem('...', '    return 42', '', autoindent=4),
            ExpectedInOutItem('...', '', ''),
            ExpectedInOutItem('>>>', 'answer()', '\n42'),
        ], repls=('pyrepl',))


    def test_repl_tab_completion():
        validate_repl("", extra_input_and_output=[
            ExpectedInOutItem('>>>', "print('completion-marker')", '\ncompletion-marker',
                              keys="prin\t'completion-marker')"),
        ])


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
                1 / 0
                ~~^~~
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
                BrokenRepr()
                ~~~~~~~~~~^^
              File "<stdin>", line 3, in __repr__
                asdf
            NameError: name 'asdf' is not defined
        """))


    def test_syntax_error_does_not_exit_repl():
        validate_repl(dedent("""\
            >>> if
              File "<stdin>", line 1
                if
                  ^
            SyntaxError: invalid syntax
            >>> 6 * 7
            42
        """))


    def test_inspect_flag():
        with tempfile.NamedTemporaryFile('w') as f:
            f.write('a = 1\n')
            f.flush()
            validate_repl(dedent("""\
                >>> a
                1
            """), python_args=['-i', f.name], ignore_preamble=False)


    def test_inspect_flag_repl_selection():
        for initial_value, code, flags, repl in [
            ('', "os.environ['PYTHON_BASIC_REPL'] = '1'", [], 'basic'),
            ('1', "del os.environ['PYTHON_BASIC_REPL']", [], 'pyrepl'),
            ('1', "os.environ['PYTHON_BASIC_REPL'] = ''", [], 'pyrepl'),
            ('', "os.environ['PYTHON_BASIC_REPL'] = '1'", ['-E'], 'pyrepl'),
            ('', "os.environ['PYTHON_BASIC_REPL'] = '1'", ['-I'], 'pyrepl'),
        ]:
            # Autoindent distinguishes the selected REPL even when inspecting an existing __main__.
            validate_repl("", python_args=[*flags, '-i', '-c', f'import os; {code}'],
                          repls=(repl,), extra_env={'PYTHON_BASIC_REPL': initial_value},
                          extra_input_and_output=[
                              ExpectedInOutItem('>>>', 'def answer():', ''),
                              ExpectedInOutItem('...', '    return 42', '',
                                                autoindent=4 if repl == 'pyrepl' else 0),
                              ExpectedInOutItem('...', '', ''),
                              ExpectedInOutItem('>>>', 'answer()', '\n42'),
                          ])


    def test_inspect_flag_exit():
        with tempfile.NamedTemporaryFile('w') as f:
            f.write('a = 1\nimport sys\nsys.exit(1)\n')
            f.flush()
            validate_repl(dedent(f"""\
                Traceback (most recent call last):
                  File "{os.path.realpath(f.name)}", line 3, in <module>
                    sys.exit(1)
                    ~~~~~~~~^^^
                SystemExit: 1
                >>> a
                1
            """), python_args=['-i', f.name], ignore_preamble=False)
