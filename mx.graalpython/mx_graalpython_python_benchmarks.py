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

import mx
import mx_benchmark

import glob
import json
import math
import os
import re
import shutil
import subprocess
import sys

from os.path import join, abspath, exists


SUITE = None
python_vm_registry = None

# By default we disabled some benchmarks, both because some don't run and
# because we want to reduce the total runtime of the suites.

DEFAULT_NUMPY_BENCHMARKS = [
    "bench_app",
    "bench_core",
    # "bench_function_base",
    "bench_indexing",
    # "bench_io",
    "bench_linalg",
    # "bench_ma",
    # "bench_random",
    "bench_reduce",
    # "bench_shape_base",
    # "bench_ufunc",
]

SKIPPED_NUMPY_BENCHMARKS = [
    "bench_core.CountNonzero.time_count_nonzero(2, 1000000, <class 'str'>)",  # Times out
    "bench_core.CountNonzero.time_count_nonzero(3, 1000000, <class 'str'>)",  # Times out
    "bench_core.CountNonzero.time_count_nonzero_axis(2, 1000000, <class 'str'>)",  # Times out
    "bench_core.CountNonzero.time_count_nonzero_axis(3, 1000000, <class 'str'>)",  # Times out
    "bench_core.CountNonzero.time_count_nonzero_multi_axis(2, 1000000, <class 'str'>)",  # Times out
    "bench_core.CountNonzero.time_count_nonzero_multi_axis(3, 1000000, <class 'str'>)",  # Times out
    "bench_linalg.LinalgSmallArrays.time_det_small_array",  # TODO fails with numpy.linalg.LinAlgError
]

DEFAULT_PANDAS_BENCHMARKS = [
    "reshape",
    "replace"
]

SKIPPED_PANDAS_BENCHMARKS = [
    "replace.ReplaceDict.time_replace_series",  # Times out
    "replace.ReplaceList.time_replace_list",  # OOM, WIP msimacek
    "replace.ReplaceList.time_replace_list_one_match",  # OOM, WIP msimacek
    "reshape.Crosstab.time_crosstab_normalize_margins",  # Times out
    "reshape.Cut.peakmem_cut_interval",  # Times out
    "reshape.Cut.time_cut_interval",  # Times out
    "reshape.GetDummies.time_get_dummies_1d_sparse",  # Times out
    "reshape.PivotTable.time_pivot_table_margins",  # Times out
    "reshape.WideToLong.time_wide_to_long_big",  # Times out
]

DEFAULT_PYPERFORMANCE_BENCHMARKS = [
    # "2to3",
    # "chameleon",
    "chaos",
    # "crypto_pyaes",
    # "django_template",
    # "dulwich_log",
    "fannkuch",
    "float",
    "go",
    "hexiom",
    # "html5lib",
    "json_dumps",
    "json_loads",
    # "logging",
    # "mako",
    "meteor_contest",
    "nbody",
    "nqueens",
    "pathlib",
    "pickle",
    "pickle_dict",
    "pickle_list",
    "pickle_pure_python",
    "pidigits",
    "pyflate",
    "regex_compile",
    "regex_dna",
    "regex_effbot",
    "regex_v8",
    "richards",
    "scimark",
    "spectral_norm",
    # "sqlalchemy_declarative",
    # "sqlalchemy_imperative",
    # "sqlite_synth",
    # "sympy",
    "telco",
    # "tornado_http",
    "unpack_sequence",
    "unpickle",
    "unpickle_list",
    # "unpickle_pure_python",
    # "xml_etree",
]

DEFAULT_PYPY_BENCHMARKS = [
    "ai",
    # "bm_chameleon",
    # "bm_dulwich_log",
    "bm_mako",
    "bm_mdp",
    "chaos",
    # "cpython_doc",
    "crypto_pyaes",
    "deltablue",
    "django",
    "eparse",
    "fannkuch",
    "float",
    "genshi_text",
    "genshi_xml",
    "go",
    "hexiom2",
    "html5lib",
    "json_bench",
    "meteor-contest",
    "nbody_modified",
    "nqueens",
    "pidigits",
    "pyflate-fast",
    "pyxl_bench",
    "raytrace-simple",
    "richards",
    "scimark_fft",
    "scimark_lu",
    "scimark_montecarlo",
    "scimark_sor",
    "scimark_sparsematmult",
    "spectral-norm",
    "spitfire2",
    "spitfire_cstringio2",
    # "sqlalchemy_declarative",
    # "sqlalchemy_imperative",
    # "sqlitesynth",
    # "sympy_expand",
    # "sympy_integrate",
    # "sympy_str",
    # "sympy_sum",
    # "telco",
    # "twisted_names",
    # "twisted_pb",
    # "twisted_tcp",
]


def create_asv_benchmark_selection(benchmarks, skipped=()):
    regex = '|'.join(benchmarks)
    if not skipped:
        return regex
    negative_lookaheads = [re.escape(skip) + (r'\b' if not skip.endswith(')') else '') for skip in skipped]
    return '^(?!' + '|'.join(negative_lookaheads) + ')(' + regex + ')'


class PyPerfJsonRule(mx_benchmark.Rule):
    """Parses a JSON file produced by PyPerf and creates a measurement result."""

    def __init__(self, filenames: str, suiteName: str):
        self.filenames = filenames.split(",")
        self.suiteName = suiteName

    def parse(self, text: str) -> list:
        r = []
        for filename in self.filenames:
            self._parse_file(r, filename)
        return r

    def _parse_file(self, r: list, filename: str):
        with open(self._prepend_working_dir(filename)) as fp:
            js = json.load(fp)
            benchmarks = js["benchmarks"]
            for benchmark in benchmarks:
                name = benchmark.get("metadata", js["metadata"])["name"]
                unit = benchmark.get("metadata", {}).get("unit") or js["metadata"]["unit"]
                unit = {
                    "second": "s",
                    "byte": "B",
                }[unit]
                metric = {
                    "s": "time",
                    "B": "max-rss",
                }[unit]
                for run in benchmark["runs"]:
                    if values := run.get("values", None):
                        if metric == "time":
                            warmups = run.get("warmups", [])
                            for idx, warmup in enumerate(warmups):
                                r.append(
                                    {
                                        "bench-suite": self.suiteName,
                                        "benchmark": name,
                                        "metric.name": "warmup",
                                        "metric.unit": unit,
                                        "metric.score-function": "id",
                                        "metric.better": "lower",
                                        "metric.type": "numeric",
                                        "metric.iteration": idx,
                                        "metric.value": warmup[1],  # 0 is inner_loop count
                                    }
                                )
                        for value in values:
                            r.append(
                                {
                                    "bench-suite": self.suiteName,
                                    "benchmark": name,
                                    "metric.name": metric,
                                    "metric.unit": unit,
                                    "metric.score-function": "id",
                                    "metric.better": "lower",
                                    "metric.type": "numeric",
                                    "metric.iteration": 0,
                                    "metric.value": value,
                                }
                            )


class AsvJsonRule(mx_benchmark.Rule):
    """Parses a JSON file produced by ASV (airspeed-velocity) and creates a measurement result."""

    def __init__(self, filename: str, suiteName: str):
        self.filename = filename
        self.suiteName = suiteName

    def parse(self, text: str) -> list:
        import itertools

        r = []
        with open(self._prepend_working_dir(self.filename)) as fp:
            js = json.load(fp)

            columns = js["result_columns"]  # type: list[str]
            peak_idx = columns.index("result")
            param_idx = columns.index("params")
            try:
                samples_idx = columns.index("samples")
            except ValueError:
                samples_idx = -1

            for benchmark, result in js["results"].items():
                param_combinations = itertools.product(*result[param_idx])
                for run_idx, params in enumerate(param_combinations):
                    peak_values = result[peak_idx]
                    if not peak_values:
                        continue
                    value = peak_values[run_idx]
                    if not value or math.isnan(value):
                        continue
                    r.append(
                        {
                            "bench-suite": self.suiteName,
                            "benchmark": benchmark,
                            "metric.name": "time",
                            "metric.unit": "s",
                            "metric.score-function": "id",
                            "metric.better": "lower",
                            "metric.type": "numeric",
                            "metric.iteration": 0,
                            "metric.value": value,
                            "config.run-flags": " ".join(params),
                        }
                    )
                    # It may be that the samples are missing; so omit this step
                    if 0 <= samples_idx < len(result):
                        for iteration, value in enumerate(result[samples_idx][run_idx]):
                            r.append(
                                {
                                    "bench-suite": self.suiteName,
                                    "benchmark": benchmark,
                                    "metric.name": "warmup",
                                    "metric.unit": "s",
                                    "metric.score-function": "id",
                                    "metric.better": "lower",
                                    "metric.type": "numeric",
                                    "metric.iteration": iteration,
                                    "metric.value": value,
                                    "config.run-flags": " ".join(params),
                                }
                            )

        return r


class PyPyJsonRule(mx_benchmark.Rule, mx_benchmark.AveragingBenchmarkMixin):
    """Parses a JSON file produced by the Unladen Swallow or PyPy benchmark harness and creates a measurement result."""

    def __init__(self, filename: str, suiteName: str):
        self.filename = filename
        self.suiteName = suiteName

    def parse(self, text: str) -> list:
        r = []
        with open(self._prepend_working_dir(self.filename)) as fp:
            js = json.load(fp)

            for result in js["results"]:
                name = result[0]
                if result[1] == "RawResult":
                    values = result[2]["base_times"]
                elif result[1] == "SimpleComparisonResult":
                    values = [result[2]["base_time"]]
                else:
                    mx.warn(f"No data found for {name} with {result[1]}")
                    continue
                for iteration, value in enumerate(values):
                    r.append(
                        {
                            "bench-suite": self.suiteName,
                            "benchmark": name,
                            "metric.name": "warmup",
                            "metric.unit": "s",
                            "metric.score-function": "id",
                            "metric.better": "lower",
                            "metric.type": "numeric",
                            "metric.iteration": iteration,
                            "metric.value": value,
                        }
                    )
        self.addAverageAcrossLatestResults(r)
        return r


class GraalPyVm(mx_benchmark.GuestVm):
    def __init__(self, config_name, options, host_vm=None):
        super(GraalPyVm, self).__init__(host_vm=host_vm)
        self._config_name = config_name
        self._options = options

    def name(self):
        return "graalpython"

    def config_name(self):
        return self._config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def with_host_vm(self, host_vm):
        return self.__class__(self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        for arg in args:
            if "--vm.Xmx" in arg:
                mx.log(f"Setting Xmx from {arg}")
                break
        else:
            xmxArg = "--vm.Xmx8G"
            mx.log(f"Setting Xmx as {xmxArg}")
            args.insert(0, xmxArg)
        try:
            old_gp_arg = os.environ.get("GRAAL_PYTHON_ARGS")
            if old_gp_arg:
                os.environ["GRAAL_PYTHON_ARGS"] = old_gp_arg + " " + xmxArg
            else:
                os.environ["GRAAL_PYTHON_ARGS"] = xmxArg
            old_java_opts = os.environ.get("JAVA_OPTS")
            if old_java_opts:
                os.environ["JAVA_OPTS"] = old_java_opts + " " + xmxArg.replace("--vm", "-")
            else:
                os.environ["JAVA_OPTS"] = xmxArg.replace("--vm.", "-")
            mx.log("Running with `JAVA_OPTS={JAVA_OPTS}` and `GRAAL_PYTHON_ARGS={GRAAL_PYTHON_ARGS}`".format(**os.environ))
            return self.host_vm().run_launcher("graalpy", self._options + args, cwd)
        finally:
            if old_java_opts:
                os.environ["JAVA_OPTS"] = old_java_opts
            else:
                del os.environ["JAVA_OPTS"]
            if old_gp_arg:
                os.environ["GRAAL_PYTHON_ARGS"] = old_gp_arg
            else:
                del os.environ["GRAAL_PYTHON_ARGS"]


class PyPyVm(mx_benchmark.Vm):
    def config_name(self):
        return "launcher"

    def name(self):
        return "pypy"

    def interpreter(self):
        home = mx.get_env("PYPY_HOME")
        if not home:
            try:
                return (
                    subprocess.check_output("which pypy3", shell=True).decode().strip()
                )
            except OSError:
                mx.abort("{} is not set!".format("PYPY_HOME"))
        return join(home, "bin", "pypy3")

    def run(self, cwd, args):
        env = os.environ.copy()
        xmxArg = re.compile("--vm.Xmx([0-9]+)([kKgGmM])")
        pypyGcMax = "8GB"
        for idx, arg in enumerate(args):
            if m := xmxArg.search(arg):
                args = args[:idx] + args[idx + 1 :]
                pypyGcMax = f"{m.group(1)}{m.group(2).upper()}B"
                mx.log(f"Setting PYPY_GC_MAX={pypyGcMax} via {arg}")
                break
        else:
            mx.log(
                f"Setting PYPY_GC_MAX={pypyGcMax}, use --vm.Xmx argument to override it"
            )
        env["PYPY_GC_MAX"] = pypyGcMax
        return mx.run([self.interpreter()] + args, cwd=cwd, env=env)


class Python3Vm(mx_benchmark.Vm):
    def config_name(self):
        return "launcher"

    def name(self):
        return "cpython"

    def interpreter(self):
        home = mx.get_env("PYTHON3_HOME")
        if not home:
            return sys.executable
        if exists(exe := join(home, "bin", "python3")):
            return exe
        elif exists(exe := join(home, "python3")):
            return exe
        elif exists(exe := join(home, "python")):
            return exe
        return join(home, "bin", "python")

    def run(self, cwd, args):
        for idx, arg in enumerate(args):
            if "--vm.Xmx" in arg:
                mx.warn(f"Ignoring {arg}, cannot restrict memory on CPython.")
                args = args[:idx] + args[idx + 1 :]
                break
        return mx.run([self.interpreter()] + args, cwd=cwd)


class WildcardList:
    """It is not easy to track for external suites which benchmarks are
    available, so we just return a wildcard list and assume the caller knows
    what they want to run"""

    def __init__(self, benchmarks=None):
        self.benchmarks = benchmarks

    def __contains__(self, x):
        return True

    def __iter__(self):
        if not self.benchmarks:
            mx.abort(
                "Cannot iterate over benchmark names in foreign benchmark suites. "
                + "Leave off the benchmark name part to run all, or name the benchmarks yourself."
            )
        else:
            return iter(self.benchmarks)


class PySuite(mx_benchmark.TemporaryWorkdirMixin, mx_benchmark.VmBenchmarkSuite):
    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        ret_code, out, dims = super().runAndReturnStdOut(benchmarks, bmSuiteArgs)

        def _replace_host_vm(old, new):
            host_vm = dims.get("host-vm")
            if host_vm and old in host_vm:
                dims['host-vm'] = host_vm.replace(old, new)
                mx.logv(f"[DEBUG] replace 'host-vm': '{host_vm}' -> '{dims['host-vm']}'")

        _replace_host_vm('graalvm-ce-python', 'graalvm-ce')
        _replace_host_vm('graalvm-ee-python', 'graalvm-ee')

        return ret_code, out, dims


class PyPerformanceSuite(PySuite):
    VERSION = "1.0.6"

    def name(self):
        return "pyperformance-suite"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graalpython"

    def benchmarkList(self, bmSuiteArgs):
        return WildcardList(DEFAULT_PYPERFORMANCE_BENCHMARKS)

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [PyPerfJsonRule(output, self.name())]

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        return []

    def get_vm_registry(self):
        return python_vm_registry

    def _vmRun(self, vm, workdir, command, benchmarks, bmSuiteArgs):
        workdir = abspath(workdir)
        vm_venv = f"{self.name()}-{vm.name()}-{vm.config_name()}"

        if not hasattr(self, "prepared"):
            self.prepared = True
            vm.run(workdir, ["-m", "venv", join(workdir, vm_venv)])
            mx.run(
                [
                    join(vm_venv, "bin", "pip"),
                    "install",
                    f"pyperformance=={self.VERSION}",
                ],
                cwd=workdir,
            )

        if benchmarks:
            bms = ["-b", ",".join(benchmarks)]
        else:
            bms = ["-b", ",".join(DEFAULT_PYPERFORMANCE_BENCHMARKS)]
        json_file = f"{vm_venv}.json"
        retcode = mx.run(
            [
                join(vm_venv, "bin", "pyperformance"),
                "run",
                "--inherit-environ",
                "PIP_INDEX_URL,PIP_EXTRA_INDEX_URL,PIP_TRUSTED_HOST,PIP_TIMEOUT,PIP_RETRIES,LD_LIBRARY_PATH,LIBRARY_PATH,CPATH,PATH,PYPY_GC_MAX,JAVA_OPTS,GRAAL_PYTHON_ARGS",
                "-o",
                json_file,
                *bms,
            ],
            cwd=workdir,
            nonZeroIsFatal=False,
        )
        mx.log(f"Return code of benchmark harness: {retcode}")
        # run again in single shot mode for memory measurements
        json_file_memory = f"{vm_venv}_memory.json"
        retcode = mx.run(
            [
                join(vm_venv, "bin", "pyperformance"),
                "run",
                "--debug-single-value",
                "--track-memory",
                "--inherit-environ",
                "PIP_INDEX_URL,PIP_EXTRA_INDEX_URL,PIP_TRUSTED_HOST,PIP_TIMEOUT,PIP_RETRIES,LD_LIBRARY_PATH,LIBRARY_PATH,CPATH,PATH,PYPY_GC_MAX,JAVA_OPTS,GRAAL_PYTHON_ARGS",
                "-o",
                json_file_memory,
                *bms,
            ],
            cwd=workdir,
            nonZeroIsFatal=False,
        )
        mx.log(f"Return code of benchmark harness: {retcode}")
        shutil.copy(join(workdir, json_file), join(SUITE.dir, "raw_results.json"))
        shutil.copy(join(workdir, json_file_memory), join(SUITE.dir, "raw_results_memory.json"))
        return retcode, ",".join([join(workdir, json_file), join(workdir, json_file_memory)])


class PyPySuite(PySuite):
    VERSION = "0324a252cf1a"

    def name(self):
        return "pypy-suite"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graalpython"

    def benchmarkList(self, bmSuiteArgs):
        return WildcardList(DEFAULT_PYPY_BENCHMARKS)

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [PyPyJsonRule(output, self.name())]

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        return []

    def get_vm_registry(self):
        return python_vm_registry

    def _vmRun(self, vm, workdir, command, benchmarks, bmSuiteArgs):
        workdir = abspath(workdir)
        vm_venv = f"{self.name()}-{vm.name()}-{vm.config_name()}"

        if not hasattr(self, "prepared"):
            self.prepared = True
            if artifact := os.environ.get("PYPY_BENCHMARKS_DIR"):
                shutil.copytree(artifact, join(workdir, "benchmarks"))
            else:
                mx.warn("PYPY_BENCHMARKS_DIR is not set, cloning repository")
                mx.run(
                    ["hg", "clone", "https://foss.heptapod.net/pypy/benchmarks"],
                    cwd=workdir,
                )
                mx.run(
                    ["hg", "up", "-C", self.VERSION], cwd=join(workdir, "benchmarks")
                )

            # workaround for pypy's benchmarks script issues
            with open(join(workdir, "benchmarks", "nullpython.py")) as f:
                content = f.read()
            content = content.replace("/usr/bin/python", "/usr/bin/env python")
            with open(join(workdir, "benchmarks", "nullpython.py"), "w") as f:
                f.write(content)

            with open(join(workdir, "benchmarks", "benchmarks.py")) as f:
                content = f.read()
            content = content.replace(
                'float(line.split(b" ")[0])', "float(line.split()[0])"
            )
            with open(join(workdir, "benchmarks", "benchmarks.py"), "w") as f:
                f.write(content)

            vm.run(workdir, ["-m", "venv", join(workdir, vm_venv)])

        json_file = f"{vm_venv}.json"
        if benchmarks:
            bms = ["-b", ",".join(benchmarks)]
        else:
            bms = ["-b", ",".join(DEFAULT_PYPY_BENCHMARKS)]
        retcode = mx.run(
            [
                sys.executable,
                join(workdir, "benchmarks", "run_local.py"),
                f"{vm_venv}/bin/python",
                "-o",
                join(workdir, json_file),
                *bms,
            ],
            cwd=workdir,
            nonZeroIsFatal=False,
        )
        shutil.copy(join(workdir, json_file), join(SUITE.dir, "raw_results.json"))
        mx.log(f"Return code of benchmark harness: {retcode}")
        return retcode, join(workdir, json_file)


class NumPySuite(PySuite):
    VERSION = "v1.26.4"

    BENCHMARK_REQ = [
        "asv==0.5.1",
        "distlib==0.3.6",
        "filelock==3.8.0",
        "platformdirs==2.5.2",
        "six==1.16.0",
        "virtualenv==20.16.3",
        "packaging==24.0",
        f"numpy=={VERSION}",
    ]

    def name(self):
        return "numpy-suite"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graalpython"

    def benchmarkList(self, bmSuiteArgs):
        return WildcardList(DEFAULT_NUMPY_BENCHMARKS)

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [AsvJsonRule(output, self.name())]

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        return []

    def get_vm_registry(self):
        return python_vm_registry

    def _vmRun(self, vm, workdir, command, benchmarks, bmSuiteArgs):
        workdir = abspath(workdir)
        benchdir = join(workdir, "numpy", "benchmarks")
        vm_venv = f"{self.name()}-{vm.name()}-{vm.config_name()}"

        if not hasattr(self, "prepared"):
            self.prepared = True
            npdir = join(workdir, "numpy")
            if artifact := os.environ.get("NUMPY_BENCHMARKS_DIR"):
                shutil.copytree(artifact, npdir)
            else:
                mx.warn("NUMPY_BENCHMARKS_DIR is not set, cloning numpy repository")
                mx.run(
                    [
                        "git",
                        "clone",
                        "--depth",
                        "1",
                        "https://github.com/numpy/numpy.git",
                        "--branch",
                        self.VERSION,
                        "--single-branch",
                    ],
                    cwd=workdir,
                )
                shutil.rmtree(join(npdir, ".git"))
            mx.run(["git", "init", "."], cwd=npdir)
            mx.run(["git", "config", "user.email", "you@example.com"], cwd=npdir)
            mx.run(["git", "config", "user.name", "YourName"], cwd=npdir)
            mx.run(["git", "commit", "--allow-empty", "-m", "init"], cwd=npdir)
            mx.run(["git", "branch", self.VERSION], cwd=npdir)
            mx.run(["git", "branch", "main"], cwd=npdir, nonZeroIsFatal=False)
            mx.run(["git", "branch", "master"], cwd=npdir, nonZeroIsFatal=False)

            vm.run(workdir, ["-m", "venv", join(workdir, vm_venv)])
            pip = join(workdir, vm_venv, "bin", "pip")
            mx.run([pip, "install", *self.BENCHMARK_REQ], cwd=workdir)
            mx.run(
                [join(workdir, vm_venv, "bin", "asv"), "machine", "--yes"], cwd=benchdir
            )

        if not benchmarks:
            benchmarks = DEFAULT_NUMPY_BENCHMARKS
        retcode = mx.run(
            [
                join(workdir, vm_venv, "bin", "asv"),
                "run",
                "--strict",
                "--record-samples",
                "-e",
                "--python=same",
                "--set-commit-hash",
                self.VERSION,
                "-b", create_asv_benchmark_selection(benchmarks, skipped=SKIPPED_NUMPY_BENCHMARKS),
            ],
            cwd=benchdir,
            nonZeroIsFatal=False,
        )

        json_file = glob.glob(join(benchdir, "results", "*", "*numpy*.json"))
        mx.log(f"Return code of benchmark harness: {retcode}")
        if json_file:
            json_file = json_file[0]
            shutil.copy(json_file, join(SUITE.dir, "raw_results.json"))
            return retcode, json_file
        else:
            return -1, ""


class PandasSuite(PySuite):
    VERSION = "1.5.2"
    VERSION_TAG = "v" + VERSION

    BENCHMARK_REQ = [
        "asv==0.5.1",
        "distlib==0.3.6",
        "filelock==3.8.0",
        "platformdirs==2.5.2",
        "six==1.16.0",
        "virtualenv==20.16.3",
        "jinja2",
        f"numpy=={NumPySuite.VERSION}",
        f"pandas=={VERSION}",
    ]

    def name(self):
        return "pandas-suite"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graalpython"

    def benchmarkList(self, bmSuiteArgs):
        return WildcardList([
            "reshape",
            "replace",
        ])

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [AsvJsonRule(output, self.name())]

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        return []

    def get_vm_registry(self):
        return python_vm_registry

    def _vmRun(self, vm, workdir, command, benchmarks, bmSuiteArgs):
        workdir = abspath(workdir)
        benchdir = join(workdir, "pandas", "asv_bench")
        vm_venv = f"{self.name()}-{vm.name()}-{vm.config_name()}"

        if not hasattr(self, "prepared"):
            self.prepared = True
            npdir = join(workdir, "pandas")
            if artifact := os.environ.get("PANDAS_BENCHMARKS_DIR"):
                shutil.copytree(artifact, npdir)
            else:
                mx.warn("PANDAS_BENCHMARKS_DIR is not set, cloning pandas repository")
                repo_url = os.environ.get("PANDAS_REPO_URL", "https://github.com/pandas-dev/pandas.git")
                mx.log("Cloning Pandas from " + repo_url)
                mx.run(
                    [
                        "git",
                        "clone",
                        "--depth",
                        "1",
                        repo_url,
                        "--branch",
                        self.VERSION_TAG,
                        "--single-branch",
                    ],
                    cwd=workdir,
                )
                shutil.rmtree(join(npdir, ".git"))
                pandas_benchmarks_dir = join(npdir, "asv_bench", "benchmarks")
                accepted = ["__init__", "pandas_vb_common"] + list(self.benchmarkList([]))
                removed_files = []
                for f in os.listdir(pandas_benchmarks_dir):
                    # Remove any file or directory that is not a benchmark suite we want to run.
                    # Keep all files starting with "_"
                    if os.path.splitext(f)[0] not in accepted:
                        removed_files.append(f)
                        f_path = join(pandas_benchmarks_dir, f)
                        if os.path.isdir(f_path):
                            shutil.rmtree(f_path)
                        else:
                            os.remove(f_path)
                mx.log("Removed Pandas benchmark files: " + repr(removed_files))


            mx.run(["git", "init", "."], cwd=npdir)
            mx.run(["git", "config", "user.email", "you@example.com"], cwd=npdir)
            mx.run(["git", "config", "user.name", "YourName"], cwd=npdir)
            mx.run(["git", "commit", "--allow-empty", "-m", "init"], cwd=npdir)
            mx.run(["git", "branch", self.VERSION_TAG], cwd=npdir)
            mx.run(["git", "branch", "main"], cwd=npdir, nonZeroIsFatal=False)
            mx.run(["git", "branch", "master"], cwd=npdir, nonZeroIsFatal=False)

            vm.run(workdir, ["-m", "venv", join(workdir, vm_venv)])
            pip = join(workdir, vm_venv, "bin", "pip")
            mx.run([pip, "install", *self.BENCHMARK_REQ], cwd=workdir)
            mx.run(
                [join(workdir, vm_venv, "bin", "asv"), "machine", "--yes"], cwd=benchdir
            )

        if not benchmarks:
            benchmarks = DEFAULT_PANDAS_BENCHMARKS
        retcode = mx.run(
            [
                join(workdir, vm_venv, "bin", "asv"),
                "run",
                "--strict",
                "--record-samples",
                "-e",
                "--python=same",
                "--set-commit-hash",
                self.VERSION_TAG,
                "-b", create_asv_benchmark_selection(benchmarks, skipped=SKIPPED_PANDAS_BENCHMARKS),
            ],
            cwd=benchdir,
            nonZeroIsFatal=False,
        )

        json_file = glob.glob(join(benchdir, "results", "*", "*pandas*.json"))
        mx.log(f"Return code of benchmark harness: {retcode}")
        if json_file:
            json_file = json_file[0]
            shutil.copy(json_file, join(SUITE.dir, "raw_results.json"))
            return retcode, json_file
        else:
            return -1, ""


def register_python_benchmarks():
    global python_vm_registry, SUITE

    from mx_graalpython_benchmark import python_vm_registry as vm_registry

    python_vm_registry = vm_registry

    SUITE = mx.suite("graalpython")

    python_vm_registry.add_vm(PyPyVm())
    python_vm_registry.add_vm(Python3Vm())
    for config_name, options, priority in [
        ("launcher", [], 5),
    ]:
        python_vm_registry.add_vm(GraalPyVm(config_name, options), SUITE, priority)

    mx_benchmark.add_bm_suite(PyPerformanceSuite())
    mx_benchmark.add_bm_suite(PyPySuite())
    mx_benchmark.add_bm_suite(NumPySuite())
    mx_benchmark.add_bm_suite(PandasSuite())
