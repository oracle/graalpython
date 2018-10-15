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
import os
import sys
from time import time


_HRULE = '-'.join(['' for i in range(80)])
ATTR_BENCHMARK = '__benchmark__'
ATTR_PROCESS_ARGS = '__process_args__'
ATTR_TEARDOWN = '__teardown__'


def ccompile(name, code):
    from importlib import invalidate_caches
    from distutils.core import setup, Extension
    __dir__ = __file__.rpartition("/")[0]
    source_file = '%s/%s.c' % (__dir__, name)
    with open(source_file, "w") as f:
        f.write(code)
    module = Extension(name, sources=[source_file])
    args = ['--quiet', 'build', 'install_lib', '-f', '--install-dir=%s' % __dir__]
    setup(
        script_name='setup',
        script_args=args,
        name=name,
        version='1.0',
        description='',
        ext_modules=[module]
    )
    # IMPORTANT:
    # Invalidate caches after creating the native module.
    # FileFinder caches directory contents, and the check for directory
    # changes has whole-second precision, so it can miss quick updates.
    invalidate_caches()


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

        _iterations = _as_int(iterations)
        self._run_once = _iterations <= 1
        self.iterations = 1 if self._run_once else _iterations

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
                bench_module.ccompile = ccompile
                exec(compile(f.readall(), bench_file, "exec"), bench_module.__dict__)
                return bench_module

    def _get_attr(self, attr_name):
        if hasattr(self.bench_module, attr_name):
            return getattr(self.bench_module, attr_name)

    def _call_attr(self, attr_name, *args):
        attr = self._get_attr(attr_name)
        if attr and hasattr(attr, '__call__'):
            return attr(*args)

    def run(self):
        print(_HRULE)
        if self._run_once:
            print("### %s, exactly one iteration (no warmup curves)" % (self.bench_module.__name__))
        else:
            print("### %s, %s warmup iterations, %s bench iterations " % (self.bench_module.__name__, self.warmup, self.iterations))

        # process the args if the processor function is defined
        args = self._call_attr(ATTR_PROCESS_ARGS, *self.bench_args)
        if args is None:
            # default args processor considers all args as ints
            args = list(map(int, self.bench_args))

        print("### args = %s" % args)
        print(_HRULE)

        bench_func = self._get_attr(ATTR_BENCHMARK)
        if bench_func and hasattr(bench_func, '__call__'):
            if self.warmup:
                print("### warming up for %s iterations ... " % self.warmup)
                for _ in range(self.warmup):
                    bench_func(*args)

            for iteration in range(self.iterations):
                start = time()
                bench_func(*args)
                duration = "%.3f" % (time() - start)
                if self._run_once:
                    print("@@@ name=%s, duration=%s" % (self.bench_module.__name__, duration))
                else:
                    print("### iteration=%s, name=%s, duration=%s" % (iteration, self.bench_module.__name__, duration))

        print("teardown ... ")
        self._call_attr(ATTR_TEARDOWN)
        print("benchmark complete")


def run_benchmark(prog, args):
    warmup = 0
    iterations = 1
    bench_file = None
    bench_args = []

    i = 0
    while i < len(args):
        arg = args[i]
        if arg == '-i':
            i += 1
            iterations = _as_int(args[i])
        elif arg.startswith("--iterations"):
            iterations = _as_int(arg.split("=")[1])
        elif arg == '-w':
            i += 1
            warmup = _as_int(args[i])
        elif arg.startswith("--warmup"):
            warmup = _as_int(arg.split("=")[1])
        elif bench_file is None:
            bench_file = arg
        else:
            bench_args.append(arg)
        i += 1

    BenchRunner(bench_file, bench_args=bench_args, iterations=iterations, warmup=warmup).run()


if __name__ == '__main__':
    run_benchmark(sys.argv[0], sys.argv[1:])
