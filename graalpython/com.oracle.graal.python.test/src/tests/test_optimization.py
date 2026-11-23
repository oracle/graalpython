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

import json
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


PROGRAM = textwrap.dedent('''
    "module doc"
    import builtins
    import json
    import sys
    checked = []
    try:
        assert checked.append(1), "assertion"
    except AssertionError:
        pass
    class C:
        "class doc"
        def method(self):
            "method doc"
    def function():
        "function doc"
    def generator():
        "generator doc"
        yield __debug__
    print(json.dumps([sys.flags.optimize, __debug__, builtins.__debug__, checked,
                      [__doc__, C.__doc__, C.method.__doc__, function.__doc__, generator.__doc__]]))
''')


class OptimizationTests(unittest.TestCase):
    def run_python(self, args, optimize=None, **kwargs):
        env = os.environ.copy()
        env.pop("PYTHONOPTIMIZE", None)
        if optimize is not None:
            env["PYTHONOPTIMIZE"] = optimize
        return subprocess.run([sys.executable, *args], env=env, text=True, capture_output=True, check=True, **kwargs)

    def check_optimization(self, args, expected, optimize=None, **kwargs):
        result = self.run_python(args, optimize, **kwargs)
        docs = ["module doc", "class doc", "method doc", "function doc", "generator doc"] if expected < 2 else [None] * 5
        self.assertEqual(json.loads(result.stdout), [expected, expected == 0, expected == 0,
                                                   [1] if expected == 0 else [], docs])

    def test_flags(self):
        for args, expected in [([], 0), (["-O"], 1), (["-OO"], 2), (["-O", "-O"], 2),
                               (["-OOO"], 3), (["-SO"], 1)]:
            with self.subTest(args=args):
                self.check_optimization([*args, "-c", PROGRAM], expected)

    def test_environment(self):
        for value, expected in [("", 0), ("0", 0), ("1", 1), ("2", 2), ("3", 3),
                                ("invalid", 1), ("-1", 1), ("99999999999999999999", 1),
                                (" 2", 2), ("2 ", 1), ("+2", 2)]:
            with self.subTest(value=value):
                self.check_optimization(["-c", PROGRAM], expected, value)

    def test_environment_and_flags_take_maximum(self):
        for args, value, expected in [(["-O"], "2", 2), (["-OO"], "1", 2), (["-O"], "0", 1)]:
            with self.subTest(args=args, value=value):
                self.check_optimization([*args, "-c", PROGRAM], expected, value)

    def test_ignore_environment(self):
        for args, expected in [(["-E"], 0), (["-I"], 0), (["-EO"], 1), (["-IOO"], 2)]:
            with self.subTest(args=args):
                self.check_optimization([*args, "-c", PROGRAM], expected, "2")

    def test_file_stdin_and_module(self):
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / "optimization_probe.py"
            script.write_text(PROGRAM)
            for level in range(3):
                flags = ["-" + "O" * level] if level else []
                for args, kwargs in [([str(script)], {}), (["-"], {"input": PROGRAM}),
                                     (["-m", "optimization_probe"], {"cwd": directory})]:
                    with self.subTest(level=level, args=args):
                        self.check_optimization([*flags, *args], level, **kwargs)

    def test_interactive(self):
        for level in (1, 2):
            with self.subTest(level=level):
                result = self.run_python(["-q", "-" + "O" * level, "-i", "-c", "pass"],
                                         input="print(__debug__)\nassert False\n")
                self.assertIn("False", result.stdout)
                self.assertNotIn("AssertionError", result.stderr)

    def test_dynamic_compilation(self):
        program = textwrap.dedent(r'''
            import ast, json
            source = '"doc"\nresult = __debug__\ntry:\n assert False\nexcept AssertionError:\n caught = True\nelse:\n caught = False'
            def report(code):
                ns = {}
                exec(code, ns)
                return [ns["result"], ns["caught"], ns.get("__doc__")]
            raw = compile(source, "<ast>", "exec", ast.PyCF_ONLY_AST)
            values = [report(source), report(compile(source, "<default>", "exec")),
                      report(compile(source, "<inherit>", "exec", optimize=-1)),
                      report(compile(raw, "<ast>", "exec"))]
            values += [report(compile(source, "<explicit>", "exec", optimize=i)) for i in range(3)]
            values += [eval("__debug__"), eval(compile("__debug__", "<eval>", "eval")),
                       compile("__debug__", "<optimized-ast>", "exec", ast.PyCF_OPTIMIZED_AST).body[0].value.value]
            print(json.dumps(values))
        ''')
        for level in range(3):
            with self.subTest(level=level):
                flags = ["-" + "O" * level] if level else []
                result = self.run_python([*flags, "-c", program])
                default = [level == 0, level == 0, "doc" if level < 2 else None]
                self.assertEqual(json.loads(result.stdout), [default] * 4 +
                                 [[True, True, "doc"], [False, False, "doc"], [False, False, None]] +
                                 [level == 0] * 3)

    def test_import_cache_levels(self):
        with tempfile.TemporaryDirectory() as directory:
            module = Path(directory) / "optimization_cached.py"
            module.write_text('"module doc"\nvalue = __debug__\n')
            program = ("import json, optimization_cached as m; "
                       "print(json.dumps([m.value, m.__doc__, m.__cached__]))")
            for level in [0, 1, 2, 0, 2, 1]:
                with self.subTest(level=level):
                    flags = ["-" + "O" * level] if level else []
                    result = json.loads(self.run_python([*flags, "-c", program], cwd=directory).stdout)
                    self.assertEqual(result[:2], [level == 0, "module doc" if level < 2 else None])
                    self.assertIn(f".opt-{level}." if level else sys.implementation.cache_tag, result[2])

    @unittest.skipUnless(sys.implementation.name == "graalpy", "GraalPy options")
    def test_polyglot_options(self):
        for args, expected in [(["--python.PythonOptimizeFlag=true"], 1),
                               (["--python.OptimizationLevel=2"], 2),
                               (["--python.PythonOptimizeFlag=true", "--python.OptimizationLevel=2"], 2)]:
            with self.subTest(args=args):
                self.check_optimization([*args, "-c", PROGRAM], expected)
