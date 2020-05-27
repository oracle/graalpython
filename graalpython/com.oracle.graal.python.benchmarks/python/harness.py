# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import types
from time import time


_HRULE = '-'.join(['' for i in range(80)])

#: this function is used to pre-process the arguments as expected by the __benchmark__ and __setup__ entry points
ATTR_PROCESS_ARGS = '__process_args__'
#: gets called with the preprocessed arguments before __benchmark__
ATTR_SETUP = '__setup__'
#: gets called with the preprocessed arguments N times
ATTR_BENCHMARK = '__benchmark__'
#: this function is used to clean up benchmark storage and reset for a subsequent run.
ATTR_CLEANUP = '__cleanup__'
#: performs any teardown needed in the benchmark
ATTR_TEARDOWN = '__teardown__'


# ----------------------------------------------------------------------------------------------------------------------
#
# the CUSUM method adapted for warmup detection within a given threshold (initial iterations)
#
# ----------------------------------------------------------------------------------------------------------------------
def zeros(n):
    return [0 for _ in range(n)]


def append(arr, val):
    if isinstance(arr, list):
        return arr + [val]
    else:
        return [val] + arr


def cusum(values, threshold=1.0, drift=0.0):
    csum_pos, csum_neg = zeros(len(values)), zeros(len(values))
    change_points = []
    for i in range(1, len(values)):
        diff = values[i] - values[i - 1]
        csum_pos[i] = csum_pos[i-1] + diff - drift
        csum_neg[i] = csum_neg[i-1] - diff - drift

        if csum_pos[i] < 0:
            csum_pos[i] = 0
        if csum_neg[i] < 0:
            csum_neg[i] = 0

        if csum_pos[i] > threshold or csum_neg[i] > threshold:
            change_points = append(change_points, i)
            csum_pos[i], csum_neg[i] = 0, 0

    return change_points


def avg(values):
    return float(sum(values)) / len(values)


def norm(values):
    _max, _min  = max(values), min(values)
    return [float(v - _min) / (_max - _min) * 100.0 for v in values]


def pairwise_slopes(values, cp):
    return [abs(float(values[i+1] - values[i]) / float(cp[i+1] - cp[i])) for i in range(len(values)-1)]


def last_n_percent_runs(values, n=0.1):
    assert 0.0 < n <= 1.0
    end_runs_idx = len(values) - int(len(values) * n)
    end_runs_idx = len(values) - 1 if end_runs_idx >= len(values) else end_runs_idx
    return values[end_runs_idx:], list(range(end_runs_idx, len(values)))


def first_n_percent_runs(values, n=0.1):
    assert 0.0 < n <= 1.0
    first_run_idx = int(len(values) * n)
    return first_run_idx -1 if first_run_idx == len(values) else first_run_idx


def detect_warmup(values, cp_threshold=0.03, stability_slope_grade=0.01):
    """
    detect the point of warmup point (iteration / run)

    :param values: the durations for each run
    :param cp_threshold:  the percent in value difference for a point to be considered a change point (percentage)
    :param stability_slope_grade: the slope grade (percentage). A grade of 1% corresponds to a slope of 0.5 degrees
    :return: the change point or -1 if not detected
    """
    # normalize all
    stability_slope_grade *= 100.0
    cp_threshold *= 100
    values = norm(values)

    try:
        cp = cusum(values, threshold=cp_threshold)
        rolling_avg = [avg(values[i:]) for i in cp]

        def warmup(cp_index):
            val_idx = cp[cp_index] + 1
            return val_idx if val_idx < len(values) else -1

        # find the point where the duration avg is below the cp threshold
        for i, d in enumerate(rolling_avg):
            if d <= cp_threshold:
                return warmup(i)

        # could not find something below the CP threshold (noise in the data), use the stabilisation of slopes
        last_n_vals, last_n_idx = last_n_percent_runs(values, 0.1)
        slopes = pairwise_slopes(rolling_avg + last_n_vals, cp + last_n_idx)

        for i, d in enumerate(slopes):
            if d <= stability_slope_grade:
                return warmup(i)

        return -1
    except Exception as e:
        print("exception occurred while detecting warmup: %s" % e)
        return -1


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
    def __init__(self, bench_file, bench_args=None, iterations=1, warmup=-1, warmup_runs=0):
        assert isinstance(iterations, int), \
            "BenchRunner iterations argument must be an int, got %s instead" % iterations
        assert isinstance(warmup, int), \
            "BenchRunner warmup argument must be an int, got %s instead" % warmup
        assert isinstance(warmup_runs, int), \
            "BenchRunner warmup_runs argument must be an int, got %s instead" % warmup_runs

        if bench_args is None:
            bench_args = []
        self.bench_module = BenchRunner.get_bench_module(bench_file)
        self.bench_args = bench_args

        _iterations = _as_int(iterations)
        self._run_once = _iterations <= 1
        self.iterations = 1 if self._run_once else _iterations
        self.warmup_runs = warmup_runs if warmup_runs > 0 else 0
        self.warmup = warmup if warmup > 0 else -1

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
            bench_module = types.ModuleType(name, bench_file)
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
        if self._run_once:
            print("### %s, exactly one iteration (no warmup curves)" % self.bench_module.__name__)
        else:
            print("### %s, %s warmup iterations, %s bench iterations " % (self.bench_module.__name__,
                                                                          self.warmup_runs, self.iterations))

        # process the args if the processor function is defined
        args = self._call_attr(ATTR_PROCESS_ARGS, *self.bench_args)
        if args is None:
            # default args processor considers all args as ints
            if sys.version_info.major < 3:
                args = list(map(lambda x: int(x.replace("_", "")), self.bench_args))
            else:
                args = list(map(int, self.bench_args))

        print("### args = ", args)
        print(_HRULE)

        print("### setup ... ")
        self._call_attr(ATTR_SETUP, *args)
        print("### start benchmark ... ")

        bench_func = self._get_attr(ATTR_BENCHMARK)
        durations = []
        if bench_func and hasattr(bench_func, '__call__'):
            if self.warmup_runs:
                print("### (pre)warming up for %s iterations ... " % self.warmup_runs)
                for _ in range(self.warmup_runs):
                    bench_func(*args)
                    self._call_attr(ATTR_CLEANUP, *args)

            for iteration in range(self.iterations):
                start = time()
                bench_func(*args)
                duration = time() - start
                durations.append(duration)
                duration_str = "%.3f" % duration
                self._call_attr(ATTR_CLEANUP, *args)
                if self._run_once:
                    print("@@@ name=%s, duration=%s" % (self.bench_module.__name__, duration_str))
                else:
                    print("### iteration=%s, name=%s, duration=%s" % (iteration, self.bench_module.__name__,
                                                                      duration_str))
                # a bit of fuzzy logic to avoid timing out on configurations
                # that are slow, without having to rework our logic for getting
                # default iterations
                if os.environ.get("CI") and iteration >= 4 and duration > 20:
                    import statistics
                    v = durations[-4:]
                    if statistics.stdev(v) / min(v) < 0.03:
                        # with less than 3 percent variance across ~20s
                        # iterations, we can safely stop here
                        break


        print(_HRULE)
        print("### teardown ... ")
        self._call_attr(ATTR_TEARDOWN)
        print("### benchmark complete")
        print(_HRULE)

        # summary
        if self._run_once:
            print("### SINGLE RUN        duration: %.3f s" % durations[0])
        else:
            print("### BEST                duration: %.3f s" % min(durations))
            print("### WORST               duration: %.3f s" % max(durations))
            print("### AVG (all runs)      duration: %.3f s" % (sum(durations) / len(durations)))
            warmup_iter = self.warmup if self.warmup > 0 else detect_warmup(durations)
            # if we cannot detect a warmup starting point but we performed some pre runs, we take a starting point
            # after the 10% of the first runs ...
            if warmup_iter < 0 and self.warmup_runs > 0:
                print("### warmup could not be detected, but %s pre-runs were executed.\n"
                      "### we assume the benchmark is warmed up and pick an iteration "
                      "in the first 10%% of the runs" % self.warmup_runs)
                warmup_iter = first_n_percent_runs(durations, 0.1)

            if warmup_iter > 0:
                print("### WARMUP %s at iteration: %d" % ("specified" if self.warmup > 0 else "detected", warmup_iter))
                no_warmup_durations = durations[warmup_iter:]
                print("### AVG (no warmup)     duration: %.3f s" % (sum(no_warmup_durations) / len(no_warmup_durations)))
            else:
                print("### WARMUP iteration not specified or could not be detected")

        print(_HRULE)
        print("### RAW DURATIONS: %s" % str(durations))
        print(_HRULE)


def run_benchmark(args):
    warmup = -1
    warmup_runs = 0
    iterations = 1
    bench_file = None
    bench_args = []
    paths = []

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

        elif arg == '-r':
            i += 1
            warmup_runs = _as_int(args[i])
        elif arg.startswith("--warmup-runs"):
            warmup_runs = _as_int(arg.split("=")[1])

        elif arg == '-p':
            i += 1
            paths = args[i].split(",")
        elif arg.startswith("--path"):
            paths = arg.split("=")[1].split(",")

        elif bench_file is None:
            bench_file = arg
        else:
            bench_args.append(arg)
        i += 1

    # set the paths if specified
    print(_HRULE)
    sys.path.append(os.path.split(bench_file)[0])
    if paths:
        for pth in paths:
            print("### adding module path: %s" % pth)
            sys.path.append(pth)
    else:
        print("### no extra module search paths specified")

    BenchRunner(bench_file, bench_args=bench_args, iterations=iterations, warmup=warmup, warmup_runs=warmup_runs).run()


if __name__ == '__main__':
    run_benchmark(sys.argv[1:])
