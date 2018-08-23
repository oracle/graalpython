# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import _io
import argparse
import os
import sys
from time import time

_HRULE = '-'.join(['' for i in range(80)])
ATTR_BENCHMARK = '__benchmark__'


def _as_int(value):
    if isinstance(value, (list, tuple)):
        value = value[0]

    if not isinstance(value, int):
        return int(value)
    return value


class BenchRunner(object):
    def __init__(self, bench_file, bench_args=None, iterations=1, warmup=0):
        if bench_args is None:
            bench_args = []
        self.bench_module = BenchRunner.get_bench_module(bench_file)
        self.bench_args = bench_args
        self.iterations = _as_int(iterations)
        assert isinstance(self.iterations, int)
        self.warmup = _as_int(warmup)
        assert isinstance(self.warmup, int)

    @staticmethod
    def get_bench_module(bench_file):
        name = bench_file.rpartition("/")[2].partition(".")[0].replace('.py', '')
        directory = bench_file.rpartition("/")[0]
        pkg = []
        while any(f.endswith("__init__.py") for f in os.listdir(directory)):
            directory, slash, postfix = directory.rpartition("/")
            pkg.insert(0, postfix)

        if pkg:
            sys.path.insert(0, directory)
            bench_module = __import__(".".join(pkg + [name]))
            for p in pkg[1:]:
                bench_module = getattr(bench_module, p)
            bench_module = getattr(bench_module, name)
            return bench_module

        else:
            bench_module = type(sys)(name, bench_file)
            with _io.FileIO(bench_file, "r") as f:
                bench_module.__file__ = bench_file
                exec(compile(f.readall(), bench_file, "exec"), bench_module.__dict__)
                return bench_module

    def _get_attr(self, attr_name):
        if hasattr(self.bench_module, attr_name):
            return getattr(self.bench_module, attr_name)

    def _call_attr(self, attr_name):
        attr = self._get_attr(attr_name)
        if attr and hasattr(attr, '__call__'):
            attr()

    def run(self):
        print(_HRULE)
        print("### %s, %s warmup iterations, %s bench iterations " % (self.bench_module.__name__, self.warmup, self.iterations))
        print(_HRULE)

        bench_func = self._get_attr(ATTR_BENCHMARK)
        if bench_func and hasattr(bench_func, '__call__'):
            if self.warmup:
                print("### warming up for %s iterations ... " % self.warmup)
                for _ in range(self.warmup):
                    bench_func(*self.bench_args)

            for iteration in range(self.iterations):
                start = time()
                bench_func(*self.bench_args)
                duration = "%.3f" % (time() - start)
                print("### iteration=%s, name=%s, duration=%s" % (iteration, self.bench_module.__name__, duration))


def run_benchmark(prog, args):
    parser = argparse.ArgumentParser(prog=prog, description="Run specified benchmark.")
    parser.add_argument("-w", "--warmup", help="The number of iterations to skip as warmup.", default=0)
    parser.add_argument("-i", "--iterations", help="The number of iterations top run each benchmark.", default=1)
    parser.add_argument("bench_file", metavar='BENCH', help="Path to the benchmark to execute.", nargs=1)
    parser.add_argument("bench_args", metavar='ARGS', help="Path to the benchmarks to execute.", nargs='*', default=None)

    args = parser.parse_args(args)
    BenchRunner(args.bench_file[0], bench_args=args.bench_args, iterations=args.iterations, warmup=args.warmup).run()


if __name__ == '__main__':
    run_benchmark(sys.argv[0], sys.argv[1:])
