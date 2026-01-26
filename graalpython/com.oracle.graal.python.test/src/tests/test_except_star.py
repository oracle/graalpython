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

import unittest
import subprocess
import sys
import textwrap

from tests.util import skipIfBytecodeDSL


class ExceptStarPrintTest(unittest.TestCase):
    def test_01_eg_simple(self):
        script = textwrap.dedent("""
            raise ExceptionGroup("eg", [
                    ValueError(1),
                    TypeError(2)
                ])
        """)
        p = subprocess.run([sys.executable, "-c", script], capture_output=True)
        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 2, in <module>',
                    b'  | ExceptionGroup: eg (2 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 1',
                    b'    +---------------- 2 ----------------',
                    b'    | TypeError: 2',
                    b'    +------------------------------------']
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_02_eg_nested(self):
        script = textwrap.dedent("""
            raise ExceptionGroup("EG", [
                TypeError("1"),
                ExceptionGroup("2", [
                    IndexError("2.1"),
                    ValueError("2.2"),
                    ExceptionGroup("2.3", [
                        IndexError("2.3.1"),
                        ExceptionGroup("2.3.2", [
                            TypeError("2.3.2.1"),
                            IndexError("2.3.2.2"),
                            ExceptionGroup("2.3.2.3", [
                                ImportError("2.3.2.3.1"),
                                ValueError("2.3.2.3.2")
                            ])
                        ]),
                        IndexError("2.3.3"),
                        IndexError("2.3.4"),
                        IndexError("2.3.5"),
                        IndexError("2.3.6"),
                    ]),
                    ExceptionGroup("2.4", [
                        IndexError("2.4.1"),
                        ExceptionGroup("2.4.2", [
                            TypeError("2.4.2.1"),
                            IndexError("2.4.2.2"),
                            ExceptionGroup("2.4.3", [
                                ImportError("2.4.3.1"),
                                ValueError("2.4.3.2")
                            ])
                        ])
                    ])
                ]),
                ValueError("3"),
                ValueError("4"),
                ValueError("5"),
            ])
        """)
        p = subprocess.run([sys.executable, "-c", script], capture_output=True)
        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 2, in <module>',
                    b'  | ExceptionGroup: EG (5 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | TypeError: 1',
                    b'    +---------------- 2 ----------------',
                    b'    | ExceptionGroup: 2 (4 sub-exceptions)',
                    b'    +-+---------------- 1 ----------------',
                    b'      | IndexError: 2.1',
                    b'      +---------------- 2 ----------------',
                    b'      | ValueError: 2.2',
                    b'      +---------------- 3 ----------------',
                    b'      | ExceptionGroup: 2.3 (6 sub-exceptions)',
                    b'      +-+---------------- 1 ----------------',
                    b'        | IndexError: 2.3.1',
                    b'        +---------------- 2 ----------------',
                    b'        | ExceptionGroup: 2.3.2 (3 sub-exceptions)',
                    b'        +-+---------------- 1 ----------------',
                    b'          | TypeError: 2.3.2.1',
                    b'          +---------------- 2 ----------------',
                    b'          | IndexError: 2.3.2.2',
                    b'          +---------------- 3 ----------------',
                    b'          | ExceptionGroup: 2.3.2.3 (2 sub-exceptions)',
                    b'          +-+---------------- 1 ----------------',
                    b'            | ImportError: 2.3.2.3.1',
                    b'            +---------------- 2 ----------------',
                    b'            | ValueError: 2.3.2.3.2',
                    b'            +------------------------------------',
                    b'        +---------------- 3 ----------------',
                    b'        | IndexError: 2.3.3',
                    b'        +---------------- 4 ----------------',
                    b'        | IndexError: 2.3.4',
                    b'        +---------------- 5 ----------------',
                    b'        | IndexError: 2.3.5',
                    b'        +---------------- 6 ----------------',
                    b'        | IndexError: 2.3.6',
                    b'        +------------------------------------',
                    b'      +---------------- 4 ----------------',
                    b'      | ExceptionGroup: 2.4 (2 sub-exceptions)',
                    b'      +-+---------------- 1 ----------------',
                    b'        | IndexError: 2.4.1',
                    b'        +---------------- 2 ----------------',
                    b'        | ExceptionGroup: 2.4.2 (3 sub-exceptions)',
                    b'        +-+---------------- 1 ----------------',
                    b'          | TypeError: 2.4.2.1',
                    b'          +---------------- 2 ----------------',
                    b'          | IndexError: 2.4.2.2',
                    b'          +---------------- 3 ----------------',
                    b'          | ExceptionGroup: 2.4.3 (2 sub-exceptions)',
                    b'          +-+---------------- 1 ----------------',
                    b'            | ImportError: 2.4.3.1',
                    b'            +---------------- 2 ----------------',
                    b'            | ValueError: 2.4.3.2',
                    b'            +------------------------------------',
                    b'    +---------------- 3 ----------------',
                    b'    | ValueError: 3',
                    b'    +---------------- 4 ----------------',
                    b'    | ValueError: 4',
                    b'    +---------------- 5 ----------------',
                    b'    | ValueError: 5',
                    b'    +------------------------------------']
        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_03_eg_nested_truncated(self):
        script = textwrap.dedent("""
            raise ExceptionGroup("EG", [
                TypeError("1"),
                ExceptionGroup("2", [
                    IndexError("2.1"),
                    ValueError("2.2"),
                    ExceptionGroup("2.3", [
                        IndexError("2.3.1"),
                        ExceptionGroup("2.3.2", [
                            TypeError("2.3.2.1"),
                            IndexError("2.3.2.2"),
                            ExceptionGroup("2.3.2.3", [
                                ExceptionGroup("2.3.2.3.1", [
                                    ExceptionGroup("2.3.2.3.1.1", [
                                        ExceptionGroup("2.3.2.3.1.1.1", [
                                            ExceptionGroup("2.3.2.3.1.1.1.1", [
                                                ExceptionGroup("2.3.2.3.1.1.1.1.1", [
                                                    ExceptionGroup("2.3.2.3.1.1.1.1.1.1", [
                                                        ExceptionGroup("2.3.2.3.1.1.1.1.1.1.1", [
                                                            ExceptionGroup("2.3.2.3.1.1.1.1.1.1.1.1", [
                                                                ExceptionGroup("2.3.2.3.1.1.1.1.1.1.1.1.1", [
                                                                    IndexError(1)
                                                                ]),
                                                            ]),
                                                        ]),
                                                    ]),
                                                    ImportError("2.3.2.3.1.1.1.1.1.2"),
                                                    IndexError("2.3.2.3.1.1.1.1.1.3"),
                                                ]),
                                            ]),
                                        ]),
                                    ]),
                                ]),
                                ImportError("2.3.2.3.1"),
                                ValueError("2.3.2.3.2")
                            ])
                        ]),
                        IndexError("2.3.3"),
                        IndexError("2.3.4"),
                        IndexError("2.3.5"),
                        IndexError("2.3.6"),
                        IndexError("2.3.7"),
                        IndexError("2.3.8"),
                        IndexError("2.3.9"),
                        IndexError("2.3.10"),
                        IndexError("2.3.11"),
                        IndexError("2.3.12"),
                        IndexError("2.3.13"),
                        IndexError("2.3.14"),
                        IndexError("2.3.15"),
                        IndexError("2.3.16"),
                        IndexError("2.3.17"),
                        IndexError("2.3.18"),
                    ]),
                    ExceptionGroup("2.4", [
                        IndexError("2.4.1"),
                        ExceptionGroup("2.4.2", [
                            TypeError("2.4.2.1"),
                            IndexError("2.4.2.2"),
                            ExceptionGroup("2.4.3", [
                                ImportError("2.4.3.1"),
                                ValueError("2.4.3.2")
                            ])
                        ])
                    ])
                ]),
                ValueError("3"),
                ValueError("4"),
                ValueError("5"),
                ValueError("6"),
                ValueError("7"),
                ValueError("8"),
                ValueError("9"),
                ValueError("10"),
                ValueError("11"),
                ValueError("12"),
                ValueError("13"),
                ValueError("14"),
                ValueError("15"),
                ValueError("16"),
                ValueError("17"),
                ValueError("18"),
                ValueError("19"),
                ValueError("20"),
                ValueError("21"),
                ValueError("22"),
                ValueError("23"),
            ])
        """)
        p = subprocess.run([sys.executable, "-c", script], capture_output=True)
        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 2, in <module>',
                    b'  | ExceptionGroup: EG (23 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | TypeError: 1',
                    b'    +---------------- 2 ----------------',
                    b'    | ExceptionGroup: 2 (4 sub-exceptions)',
                    b'    +-+---------------- 1 ----------------',
                    b'      | IndexError: 2.1',
                    b'      +---------------- 2 ----------------',
                    b'      | ValueError: 2.2',
                    b'      +---------------- 3 ----------------',
                    b'      | ExceptionGroup: 2.3 (18 sub-exceptions)',
                    b'      +-+---------------- 1 ----------------',
                    b'        | IndexError: 2.3.1',
                    b'        +---------------- 2 ----------------',
                    b'        | ExceptionGroup: 2.3.2 (3 sub-exceptions)',
                    b'        +-+---------------- 1 ----------------',
                    b'          | TypeError: 2.3.2.1',
                    b'          +---------------- 2 ----------------',
                    b'          | IndexError: 2.3.2.2',
                    b'          +---------------- 3 ----------------',
                    b'          | ExceptionGroup: 2.3.2.3 (3 sub-exceptions)',
                    b'          +-+---------------- 1 ----------------',
                    b'            | ExceptionGroup: 2.3.2.3.1 (1 sub-exception)',
                    b'            +-+---------------- 1 ----------------',
                    b'              | ExceptionGroup: 2.3.2.3.1.1 (1 sub-exception)',
                    b'              +-+---------------- 1 ----------------',
                    b'                | ExceptionGroup: 2.3.2.3.1.1.1 (1 sub-exception)',
                    b'                +-+---------------- 1 ----------------',
                    b'                  | ExceptionGroup: 2.3.2.3.1.1.1.1 (1 sub-exception)',
                    b'                  +-+---------------- 1 ----------------',
                    b'                    | ExceptionGroup: 2.3.2.3.1.1.1.1.1 (3 sub-exceptions)',
                    b'                    +-+---------------- 1 ----------------',
                    b'                      | ... (max_group_depth is 10)',
                    b'                      +---------------- 2 ----------------',
                    b'                      | ImportError: 2.3.2.3.1.1.1.1.1.2',
                    b'                      +---------------- 3 ----------------',
                    b'                      | IndexError: 2.3.2.3.1.1.1.1.1.3',
                    b'                      +------------------------------------',
                    b'            +---------------- 2 ----------------',
                    b'            | ImportError: 2.3.2.3.1',
                    b'            +---------------- 3 ----------------',
                    b'            | ValueError: 2.3.2.3.2',
                    b'            +------------------------------------',
                    b'        +---------------- 3 ----------------',
                    b'        | IndexError: 2.3.3',
                    b'        +---------------- 4 ----------------',
                    b'        | IndexError: 2.3.4',
                    b'        +---------------- 5 ----------------',
                    b'        | IndexError: 2.3.5',
                    b'        +---------------- 6 ----------------',
                    b'        | IndexError: 2.3.6',
                    b'        +---------------- 7 ----------------',
                    b'        | IndexError: 2.3.7',
                    b'        +---------------- 8 ----------------',
                    b'        | IndexError: 2.3.8',
                    b'        +---------------- 9 ----------------',
                    b'        | IndexError: 2.3.9',
                    b'        +---------------- 10 ----------------',
                    b'        | IndexError: 2.3.10',
                    b'        +---------------- 11 ----------------',
                    b'        | IndexError: 2.3.11',
                    b'        +---------------- 12 ----------------',
                    b'        | IndexError: 2.3.12',
                    b'        +---------------- 13 ----------------',
                    b'        | IndexError: 2.3.13',
                    b'        +---------------- 14 ----------------',
                    b'        | IndexError: 2.3.14',
                    b'        +---------------- 15 ----------------',
                    b'        | IndexError: 2.3.15',
                    b'        +---------------- ... ----------------',
                    b'        | and 3 more exceptions',
                    b'        +------------------------------------',
                    b'      +---------------- 4 ----------------',
                    b'      | ExceptionGroup: 2.4 (2 sub-exceptions)',
                    b'      +-+---------------- 1 ----------------',
                    b'        | IndexError: 2.4.1',
                    b'        +---------------- 2 ----------------',
                    b'        | ExceptionGroup: 2.4.2 (3 sub-exceptions)',
                    b'        +-+---------------- 1 ----------------',
                    b'          | TypeError: 2.4.2.1',
                    b'          +---------------- 2 ----------------',
                    b'          | IndexError: 2.4.2.2',
                    b'          +---------------- 3 ----------------',
                    b'          | ExceptionGroup: 2.4.3 (2 sub-exceptions)',
                    b'          +-+---------------- 1 ----------------',
                    b'            | ImportError: 2.4.3.1',
                    b'            +---------------- 2 ----------------',
                    b'            | ValueError: 2.4.3.2',
                    b'            +------------------------------------',
                    b'    +---------------- 3 ----------------',
                    b'    | ValueError: 3',
                    b'    +---------------- 4 ----------------',
                    b'    | ValueError: 4',
                    b'    +---------------- 5 ----------------',
                    b'    | ValueError: 5',
                    b'    +---------------- 6 ----------------',
                    b'    | ValueError: 6',
                    b'    +---------------- 7 ----------------',
                    b'    | ValueError: 7',
                    b'    +---------------- 8 ----------------',
                    b'    | ValueError: 8',
                    b'    +---------------- 9 ----------------',
                    b'    | ValueError: 9',
                    b'    +---------------- 10 ----------------',
                    b'    | ValueError: 10',
                    b'    +---------------- 11 ----------------',
                    b'    | ValueError: 11',
                    b'    +---------------- 12 ----------------',
                    b'    | ValueError: 12',
                    b'    +---------------- 13 ----------------',
                    b'    | ValueError: 13',
                    b'    +---------------- 14 ----------------',
                    b'    | ValueError: 14',
                    b'    +---------------- 15 ----------------',
                    b'    | ValueError: 15',
                    b'    +---------------- ... ----------------',
                    b'    | and 8 more exceptions',
                    b'    +------------------------------------']
        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_04_eg_cause(self):
        script = textwrap.dedent("""
            EG = ExceptionGroup
            try:
                raise EG("eg1", [ValueError(1), TypeError(2)])
            except Exception as e:
                raise EG("eg2", [ValueError(3), TypeError(4)]) from e
        """)

        p = subprocess.run([sys.executable, "-c", script], capture_output=True)

        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 4, in <module>',
                    b'  | ExceptionGroup: eg1 (2 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 1',
                    b'    +---------------- 2 ----------------',
                    b'    | TypeError: 2',
                    b'    +------------------------------------',
                    b'',
                    b'The above exception was the direct cause of the following exception:',
                    b'',
                    b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 6, in <module>',
                    b'  | ExceptionGroup: eg2 (2 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 3',
                    b'    +---------------- 2 ----------------',
                    b'    | TypeError: 4',
                    b'    +------------------------------------']

        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_05_eg_context_with_context(self):
        script = textwrap.dedent("""
            EG = ExceptionGroup
            try:
                try:
                    raise EG("eg1", [ValueError(1), TypeError(2)])
                except EG:
                    raise EG("eg2", [ValueError(3), TypeError(4)])
            except EG:
                raise ImportError(5)
        """)

        p = subprocess.run([sys.executable, "-c", script], capture_output=True)

        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 5, in <module>',
                    b'  | ExceptionGroup: eg1 (2 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 1',
                    b'    +---------------- 2 ----------------',
                    b'    | TypeError: 2',
                    b'    +------------------------------------',
                    b'',
                    b'During handling of the above exception, another exception occurred:',
                    b'',
                    b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 7, in <module>',
                    b'  | ExceptionGroup: eg2 (2 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 3',
                    b'    +---------------- 2 ----------------',
                    b'    | TypeError: 4',
                    b'    +------------------------------------',
                    b'',
                    b'During handling of the above exception, another exception occurred:',
                    b'',
                    b'Traceback (most recent call last):',
                    b'  File "<string>", line 9, in <module>',
                    b'ImportError: 5',]

        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_06_eg_nested_with_context(self):
        script = textwrap.dedent("""
            EG = ExceptionGroup
            VE = ValueError
            TE = TypeError
            try:
                try:
                    raise EG("nested", [TE(2), TE(3)])
                except Exception as e:
                    exc = e
                raise EG("eg", [VE(1), exc, VE(4)])
            except EG:
                raise EG("top", [VE(5)])
        """)

        p = subprocess.run([sys.executable, "-c", script], capture_output=True)

        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 10, in <module>',
                    b'  | ExceptionGroup: eg (3 sub-exceptions)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 1',
                    b'    +---------------- 2 ----------------',
                    b'    | Exception Group Traceback (most recent call last):',
                    b'    |   File "<string>", line 7, in <module>',
                    b'    | ExceptionGroup: nested (2 sub-exceptions)',
                    b'    +-+---------------- 1 ----------------',
                    b'      | TypeError: 2',
                    b'      +---------------- 2 ----------------',
                    b'      | TypeError: 3',
                    b'      +------------------------------------',
                    b'    +---------------- 3 ----------------',
                    b'    | ValueError: 4',
                    b'    +------------------------------------',
                    b'',
                    b'During handling of the above exception, another exception occurred:',
                    b'',
                    b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 12, in <module>',
                    b'  | ExceptionGroup: top (1 sub-exception)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | ValueError: 5',
                    b'    +------------------------------------',]

        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_07_eg_with_notes(self):
        script = textwrap.dedent("""
            try:
                excs = []
                for msg in ['bad value', 'terrible value']:
                    try:
                        raise ValueError(msg)
                    except ValueError as e:
                        e.add_note(f'the {msg}')
                        excs.append(e)
                raise ExceptionGroup("nested", excs)
            except ExceptionGroup as e:
                e.add_note(('>> Multi line note\\n'
                            '>> Because I am such\\n'
                            '>> an important exception.\\n'
                            '>> empty lines work too\\n'
                            '\\n'
                            '(that was an empty line)'))
                raise
        """)

        p = subprocess.run([sys.executable, "-c", script], capture_output=True)

        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 10, in <module>',
                    b'  | ExceptionGroup: nested (2 sub-exceptions)',
                    b'  | >> Multi line note',
                    b'  | >> Because I am such',
                    b'  | >> an important exception.',
                    b'  | >> empty lines work too',
                    b'  | ',
                    b'  | (that was an empty line)',
                    b'  +-+---------------- 1 ----------------',
                    b'    | Traceback (most recent call last):',
                    b'    |   File "<string>", line 6, in <module>',
                    b'    | ValueError: bad value',
                    b'    | the bad value',
                    b'    +---------------- 2 ----------------',
                    b'    | Traceback (most recent call last):',
                    b'    |   File "<string>", line 6, in <module>',
                    b'    | ValueError: terrible value',
                    b'    | the terrible value',
                    b'    +------------------------------------',]

        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)

    def test_08_eg_with_multiple_notes(self):
        script = textwrap.dedent("""
            try:
                excs = []
                for msg in ['bad value', 'terrible value']:
                    try:
                        raise ValueError(msg)
                    except ValueError as e:
                        e.add_note(f'the {msg}')
                        e.add_note(f'Goodbye {msg}')
                        excs.append(e)
                raise ExceptionGroup("nested", excs)
            except ExceptionGroup as e:
                e.add_note(('>> Multi line note\\n'
                            '>> Because I am such\\n'
                            '>> an important exception.\\n'
                            '>> empty lines work too\\n'
                            '\\n'
                            '(that was an empty line)'))
                e.add_note('Goodbye!')
                raise
        """)

        p = subprocess.run([sys.executable, "-c", script], capture_output=True)

        expected = [b'  + Exception Group Traceback (most recent call last):',
                    b'  |   File "<string>", line 11, in <module>',
                    b'  | ExceptionGroup: nested (2 sub-exceptions)',
                    b'  | >> Multi line note',
                    b'  | >> Because I am such',
                    b'  | >> an important exception.',
                    b'  | >> empty lines work too',
                    b'  | ',
                    b'  | (that was an empty line)',
                    b'  | Goodbye!',
                    b'  +-+---------------- 1 ----------------',
                    b'    | Traceback (most recent call last):',
                    b'    |   File "<string>", line 6, in <module>',
                    b'    | ValueError: bad value',
                    b'    | the bad value',
                    b'    | Goodbye bad value',
                    b'    +---------------- 2 ----------------',
                    b'    | Traceback (most recent call last):',
                    b'    |   File "<string>", line 6, in <module>',
                    b'    | ValueError: terrible value',
                    b'    | the terrible value',
                    b'    | Goodbye terrible value',
                    b'    +------------------------------------',]

        self.maxDiff = None
        self.assertEqual(p.stderr.splitlines(), expected)