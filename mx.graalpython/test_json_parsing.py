# Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

# pylint: skip-file
import math
import os
import shutil
import sys
import unittest

from unittest import mock

from test_json_parsing_data import *

sys.path.append(os.path.join(os.path.dirname(__file__)))
try:
    import mx
except ImportError:
    if mx_exe := shutil.which("mx"):
        mx_path = os.path.dirname(os.path.realpath(mx_exe))
        sys.path.append(os.path.join(mx_path, "src"))


class TestJsonBenchmarkParsers(unittest.TestCase):
    def test_pyperf_parsing_multi(self):
        import mx
        import mx_graalpython_python_benchmarks
        from mx_graalpython_python_benchmarks import PyPerfJsonRule

        rule = PyPerfJsonRule("", "pyperformance")

        with mock.patch('mx_graalpython_python_benchmarks.open', mock.mock_open(read_data=PYPERF_INPUT)):
            results = rule.parse("ignored")

        benchmarks = ["deltablue", "regex_dna"]
        benchmarks_found = set()

        for result in results:
            self.assertEqual(result["bench-suite"], "pyperformance")
            self.assertIn(result["benchmark"], benchmarks)
            benchmarks_found.add(result["benchmark"])
            self.assertIn(result["metric.name"], ["time", "max-rss", "warmup"])
            self.assertIn(result["metric.unit"], ["B", "ms", "s"])
            self.assertEqual(result["metric.score-function"], "id")
            self.assertEqual(result["metric.type"], "numeric")
            self.assertIsInstance(result["metric.value"], (float, int))
            self.assertIsInstance(result["metric.iteration"], int)

        self.assertSetEqual(benchmarks_found, set(benchmarks))

    def test_pyperf_parsing_single(self):
        import mx
        import mx_graalpython_python_benchmarks
        from mx_graalpython_python_benchmarks import PyPerfJsonRule

        rule = PyPerfJsonRule("", "pyperformance")

        with mock.patch('mx_graalpython_python_benchmarks.open', mock.mock_open(read_data=PYPERF_INPUT_SINGLE)):
            results = rule.parse("ignored")

        for result in results:
            self.assertEqual(result["bench-suite"], "pyperformance")
            self.assertEqual(result["benchmark"], "deltablue")
            self.assertIn(result["metric.name"], ["time", "max-rss", "warmup"])
            self.assertIn(result["metric.unit"], ["B", "ms", "s"])
            self.assertEqual(result["metric.score-function"], "id")
            self.assertEqual(result["metric.type"], "numeric")
            self.assertIsInstance(result["metric.value"], (float, int))
            self.assertIsInstance(result["metric.iteration"], int)

    def test_asv_parsing(self):
        import mx
        import mx_graalpython_python_benchmarks
        from mx_graalpython_python_benchmarks import AsvJsonRule

        rule = AsvJsonRule("", "asv")

        with mock.patch('mx_graalpython_python_benchmarks.open', mock.mock_open(read_data=ASV_JSON)):
            results = rule.parse("ignored")

        benchmarks = ["bench_app.LaplaceInplace.time_it", "bench_app.MaxesOfDots.time_it"]
        benchmarks_found = set()

        for result in results:
            self.assertEqual(result["bench-suite"], "asv")
            self.assertIn(result["benchmark"], benchmarks)
            benchmarks_found.add(result["benchmark"])
            self.assertIn(result["metric.name"], ["time", "warmup"])
            self.assertIn(result["metric.unit"], ["s"])
            self.assertEqual(result["metric.score-function"], "id")
            self.assertEqual(result["metric.type"], "numeric")
            self.assertIsInstance(result["metric.value"], float)
            self.assertIsInstance(result["metric.iteration"], int)
            self.assertIn(result["config.run-flags"], ["", "'inplace'", "'normal'"])

        self.assertSetEqual(benchmarks_found, set(benchmarks))

        # 3 results, LaplaceInplace with 2 params, and no params for MaxesOfDots
        self.assertEqual(len([result for result in results if result["metric.name"] == "time"]), 3)
        # 30 warmups, 10 each for each result
        self.assertEqual(len([result for result in results if result["metric.name"] == "warmup"]), 30)

    def test_asv_parsing2(self):
        import mx
        import mx_graalpython_python_benchmarks
        from mx_graalpython_python_benchmarks import AsvJsonRule

        rule = AsvJsonRule("", "asv")

        with mock.patch('mx_graalpython_python_benchmarks.open', mock.mock_open(read_data=ASV_JSON2)):
            results = rule.parse("ignored")

        for result in results:
            self.assertFalse(math.isnan(result["metric.value"]), "nan-results are not reported")

    def test_pypy_results_parsing(self):
        import mx
        import mx_graalpython_python_benchmarks
        from mx_graalpython_python_benchmarks import PyPyJsonRule

        rule = PyPyJsonRule("", "pypy")

        with mock.patch('mx_graalpython_python_benchmarks.open', mock.mock_open(read_data=PYPY_JSON)):
            results = rule.parse("ignored")

        for result in results:
            self.assertEqual(result["bench-suite"], "pypy")
            self.assertIn(result["benchmark"], ["ai", "deltablue"])
            self.assertIn(result["metric.name"], ["time", "warmup"])
            self.assertIn(result["metric.unit"], ["s"])
            self.assertEqual(result["metric.score-function"], "id")
            self.assertEqual(result["metric.type"], "numeric")
            self.assertIsInstance(result["metric.value"], float)
            self.assertIsInstance(result["metric.iteration"], int)

        self.assertEqual(len(results), 102, "should have 2*50 warmup values and 2*1 averages")

    def test_pypy_results_parsing2(self):
        import mx
        import mx_graalpython_python_benchmarks
        from mx_graalpython_python_benchmarks import PyPyJsonRule

        rule = PyPyJsonRule("", "pypy")

        with mock.patch('mx_graalpython_python_benchmarks.open', mock.mock_open(read_data=PYPY_JSON2)):
            results = rule.parse("ignored")

        for result in results:
            self.assertEqual(result["bench-suite"], "pypy")
            self.assertIn(result["benchmark"], ["scimark_fft"])
            self.assertIn(result["metric.name"], ["time", "warmup"])
            self.assertIn(result["metric.unit"], ["s"])
            self.assertEqual(result["metric.score-function"], "id")
            self.assertEqual(result["metric.type"], "numeric")
            self.assertIsInstance(result["metric.value"], float)
            self.assertIsInstance(result["metric.iteration"], int)

        self.assertEqual(len(results), 2, "2 values expected")


if __name__ == '__main__':
    unittest.main()
