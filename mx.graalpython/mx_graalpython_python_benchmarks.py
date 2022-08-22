import mx
import mx_benchmark

import json
import subprocess
import sys

from os.path import join


class PyPerfJsonRule(mx_benchmark.Rule):
    """Parses a JSON file produced by PyPerf and creates a measurement result."""

    def __init__(self, filename: str, suiteName: str):
        self.filename = filename
        self.suiteName = suiteName

    def parse(self, text: str) -> dict:
        import statistics

        r = []
        with open(self._prepend_working_dir(self.filename)) as fp:
            js = json.load(fp)
            benchmarks = js["benchmarks"]
            for benchmark in benchmarks:
                unit = {
                    "second": "s",
                    "millisecond": "ms",
                }.get(unit := js["metadata"]["unit"], unit)
                name = benchmark.get("metadata", js["metadata"])["name"]
                for run in benchmark["runs"]:
                    if values := run.get("values", None):
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
                                    "metric.name": "time",
                                    "metric.unit": unit,
                                    "metric.score-function": "id",
                                    "metric.better": "lower",
                                    "metric.type": "numeric",
                                    "metric.iteration": 0,
                                    "metric.value": value,
                                }
                            )
                        if maxrss := run["metadata"].get(
                            "mem_max_rss", js["metadata"].get("mem_max_rss", 0)
                        ):
                            r.append(
                                {
                                    "bench-suite": self.suiteName,
                                    "benchmark": name,
                                    "metric.name": "max-rss",
                                    "metric.unit": unit,
                                    "metric.score-function": "id",
                                    "metric.better": "lower",
                                    "metric.type": "numeric",
                                    "metric.iteration": 0,
                                    "metric.value": maxrss,
                                }
                            )

        return r


class AsvJsonRule(mx_benchmark.Rule):
    """Parses a JSON file produced by ASV (airspeed-velocity) and creates a measurement result."""

    def __init__(self, filename: str, suiteName: str):
        self.filename = filename
        self.suiteName = suiteName

    def parse(self, text: str) -> dict:
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
                            "metric.value": result[peak_idx][run_idx],
                            "config.run-flags": " ".join(params),
                        }
                    )
                    if samples_idx >= 0:
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


class PypyJsonRule(mx_benchmark.Rule, mx_benchmark.AveragingBenchmarkMixin):
    """Parses a JSON file produced by the Unladen Swallow or PyPy benchmark harness and creates a measurement result."""

    def __init__(self, filename: str, suiteName: str):
        self.filename = filename
        self.suiteName = suiteName

    def parse(self, text: str) -> dict:
        import statistics

        r = []
        with open(self._prepend_working_dir(self.filename)) as fp:
            js = json.load(fp)

            for result in js["results"]:
                name = result[0]
                values = result[2]["base_times"]
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
        return "GraalPy"

    def config_name(self):
        return self._config_name

    def hosting_registry(self):
        return mx_benchmark.java_vm_registry

    def with_host_vm(self, host_vm):
        return self.__class__(self.config_name(), self._options, host_vm)

    def run(self, cwd, args):
        return self.host_vm().run_launcher("graalpy", self._options + args, cwd)


class PyPyVm(mx_benchmark.Vm):
    def config_name(self):
        return "default"

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
        return mx.run([self.interpreter()] + args, cwd=cwd)


class CPythonVm(mx_benchmark.Vm):
    def config_name(self):
        return "default"

    def name(self):
        return "cpython"

    def interpreter(self):
        home = mx.get_env("PYTHON3_HOME")
        if not home:
            return sys.executable
        return join(home, "bin", "python3")

    def run(self, cwd, args):
        return mx.run([self.interpreter()] + args, cwd=cwd)


class PyPerformanceSuite(
    mx_benchmark.TemporaryWorkdirMixin, mx_benchmark.VmBenchmarkSuite
):
    def name(self):
        return "pyperformance"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graalpython"

    def benchmarkList(self, bmSuiteArgs):
        raise

    def rules(self, output, benchmarks, bmSuiteArgs):
        return [PyPerfJsonRule(output, self.name())]

    def createVmCommandLineArgs(self, benchmarks, runArgs):
        return []

    def get_vm_registry(self):
        return py_vm_registry

    def _vmRun(self, vm, workdir, command, benchmarks, bmSuiteArgs):
        vm_venv = f"{self.name()}-{vm.name()}-{vm.config_name()}"
        json_file = f"{vm_venv}.json"

        env = {
            "GRAAL_PYTHON_ARGS": "--experimental-options --DisableFrozenModules"
        }  # temporary workaround
        vm.run(workdir, ["-m", "venv", vm_venv])
        mx.run(
            [join(vm_venv, "bin", "pip"), "install", "pyperformance==1.0.2"],
            cwd=workdir,
        )
        retcode = mx.run(
            [
                join(vm_venv, "bin", "pyperformance"),
                "run",
                "-m",
                "-o",
                json_file,
                "-b",
                "bm_ai,deltablue",
            ],
            cwd=workdir,
        )
        return retcode, json_file


py_vm_registry = mx_benchmark.VmRegistry(
    "Py", "py", known_host_registries=[mx_benchmark.java_vm_registry]
)


def register_python_benchmarks():
    py_vm_registry.add_vm(PyPyVm())
    py_vm_registry.add_vm(CPythonVm())
    for config_name, options, priority in [
        ("default", [], 10),
        ("jvm", ["--jvm"], 20),
        (
            "bytecode",
            [
                "--experimental-options",
                "--EnableBytecodeInterpreter=true",
                "--DisableFrozenModules=true",
            ],
            80,
        ),
        (
            "ast",
            [
                "--experimental-options",
                "--EnableBytecodeInterpreter=false",
                "--DisableFrozenModules=true",
            ],
            100,
        ),
    ]:
        # if mx.suite("py-benchmarks", fatalIfMissing=False):
        #     import mx_py_benchmarks

        #     mx_py_benchmarks.add_vm(GraalPyVm(config_name, options), _suite, priority)
        py_vm_registry.add_vm(
            GraalPyVm(config_name, options), mx.suite("graalpython"), priority
        )

    mx_benchmark.add_bm_suite(PyPerformanceSuite())
