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
"""Small Rich + asteval demo for the GraalOS standalone sandbox."""

from __future__ import annotations

import argparse
import io
import sysconfig
import textwrap
import time
import unittest
from dataclasses import dataclass

from asteval import Interpreter
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table
from rich.text import Text


console = Console()


@dataclass
class EvalResult:
    mode: str
    ok: bool
    output: str
    elapsed_ms: float


def make_interpreter() -> Interpreter:
    aeval = Interpreter()
    # Keep the demo's "safe" lane expression-oriented. GraalOS is the real
    # containment boundary; this prevents the app-level evaluator from opening files.
    aeval.symtable.pop("open", None)
    return aeval


def render_message(role: str, body: str, style: str) -> None:
    console.print(Panel(Text(body), title=role, title_align="left", border_style=style))


def safe_eval(aeval: Interpreter, expr: str) -> EvalResult:
    start = time.perf_counter()
    aeval.error = []
    try:
        value = aeval(expr)
    except Exception as exc:  # asteval normally records errors instead of raising
        elapsed = (time.perf_counter() - start) * 1000
        return EvalResult("asteval", False, f"{type(exc).__name__}: {exc}", elapsed)

    elapsed = (time.perf_counter() - start) * 1000
    if aeval.error:
        errors = "\n".join(str(err.get_error()) for err in aeval.error)
        return EvalResult("asteval", False, errors, elapsed)
    return EvalResult("asteval", True, repr(value), elapsed)


def unsafe_eval(expr: str) -> EvalResult:
    start = time.perf_counter()
    try:
        value = eval(expr)
        elapsed = (time.perf_counter() - start) * 1000
        if value == -1:
            return EvalResult("python eval", False, "-1 (operation denied by sandbox/runtime)", elapsed)
        return EvalResult("python eval", True, repr(value), elapsed)
    except Exception as exc:
        elapsed = (time.perf_counter() - start) * 1000
        return EvalResult("python eval", False, f"{type(exc).__name__}: {exc}", elapsed)


def render_result(result: EvalResult) -> None:
    table = Table.grid(padding=(0, 1))
    table.add_column(style="bold")
    table.add_column()
    table.add_row("mode", result.mode)
    table.add_row("status", "[green]ok[/green]" if result.ok else "[red]blocked/error[/red]")
    table.add_row("time", f"{result.elapsed_ms:.1f} ms")
    console.print(table)
    render_message("sandbox", result.output, "green" if result.ok else "red")


def evaluate(aeval: Interpreter, line: str) -> None:
    line = line.strip()
    if not line:
        return
    if line.startswith("/unsafe "):
        expr = line[len("/unsafe ") :].strip()
        render_result(unsafe_eval(expr))
    else:
        render_result(safe_eval(aeval, line))


def demo_script() -> list[str]:
    return [
        "sum([i*i for i in range(1000)])",
        "sin(pi / 4) ** 2 + cos(pi / 4) ** 2",
        "open('/etc/passwd').read()",
        "/unsafe open('/etc/passwd').read().splitlines()[:3]",
        "/unsafe open('/etc/shadow').read()",
        "/unsafe __import__('subprocess').run(['/bin/sh', '-c', 'id'], capture_output=True, text=True)",
        "/unsafe __import__('socket').create_connection(('example.com', 80), timeout=2)",
        "/unsafe __import__('ctypes').CDLL('libc.so').system(b'cat /etc/shadow')",
    ]


def print_intro() -> None:
    body = textwrap.dedent(
        """
        Type Python expressions and get chat-style results.

        Normal input uses asteval, a restricted expression evaluator.
        Prefix with /unsafe to bypass asteval and use Python eval directly.
        The process is still inside the GraalOS sandbox, so filesystem,
        subprocess, native library, and network attempts remain contained.

        Commands: /demo, /help, /quit
        """
    ).strip()
    render_message("graalos sandbox chat", body, "cyan")


def print_help() -> None:
    examples = "\n".join(demo_script())
    console.print(Syntax(examples, "python", theme="ansi_dark", word_wrap=True))


def interactive() -> int:
    aeval = make_interpreter()
    print_intro()
    while True:
        try:
            line = console.input("[bold cyan]you>[/bold cyan] ")
        except (EOFError, KeyboardInterrupt):
            console.print()
            return 0
        command = line.strip()
        if command in {"/quit", "/exit"}:
            return 0
        if command == "/help":
            print_help()
            continue
        if command == "/demo":
            run_demo(aeval)
            continue
        evaluate(aeval, line)


def run_demo(aeval: Interpreter | None = None) -> None:
    if aeval is None:
        aeval = make_interpreter()
    for line in demo_script():
        render_message("you", line, "blue")
        evaluate(aeval, line)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--demo", action="store_true", help="run the prepared demo script and exit")
    args = parser.parse_args(argv)

    if args.demo:
        print_intro()
        run_demo()
        return 0
    return interactive()


def skip_unless_graalos():
    soabi = sysconfig.get_config_var("SOABI") or ""
    if "graalos" not in soabi:
        raise unittest.SkipTest(f"requires GraalOS SOABI, got {soabi!r}")


class GraalOSSandboxChatTests(unittest.TestCase):

    def setUp(self):
        skip_unless_graalos()

    def test_demo_packages(self):
        import asteval
        import rich

        self.assertTrue(asteval.__version__)
        self.assertTrue(rich.get_console())

    def test_sandbox_chat_demo(self):
        global console
        old_console = console
        output = io.StringIO()
        console = Console(file=output, force_terminal=False, color_system=None, width=120)
        try:
            self.assertEqual(main(["--demo"]), 0)
        finally:
            console = old_console

        stdout = output.getvalue()
        self.assertIn("sum([i*i for i in range(1000)])", stdout)
        self.assertIn("__import__('socket').create_connection", stdout)
        self.assertIn("gaierror", stdout)
        self.assertIn("FileNotFoundError", stdout)
        self.assertIn("operation denied", stdout)


if __name__ == "__main__":
    raise SystemExit(main())
