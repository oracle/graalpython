# Copyright (c) 2018, 2025, Oracle and/or its affiliates.
# Copyright (c) 2013, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
from __future__ import print_function

import functools
import shutil
import statistics
import sys
import os
import re
import shlex
import subprocess
from abc import ABC, abstractproperty
from contextlib import contextmanager
from os.path import join
from datetime import datetime
from pathlib import Path

import mx
import mx_benchmark
import mx_polybench
from mx_benchmark import StdOutRule, java_vm_registry, OutputCapturingVm, GuestVm, VmBenchmarkSuite, AveragingBenchmarkMixin, bm_exec_context
from mx_graalpython_bench_param import HARNESS_PATH

# ----------------------------------------------------------------------------------------------------------------------
#
# the graalpython suite
#
# ----------------------------------------------------------------------------------------------------------------------
SUITE = mx.suite("graalpython")
DIR = Path(__file__).parent.resolve()

# ----------------------------------------------------------------------------------------------------------------------
#
# constants
#
# ----------------------------------------------------------------------------------------------------------------------
VM_NAME_GRAALPYTHON = "graalpython"
VM_NAME_GRAALPYTHON_SVM = "graalpython-svm"
GROUP_GRAAL = "Graal"
SUBGROUP_GRAAL_PYTHON = "graalpython"

PYTHON_VM_REGISTRY_NAME = "Python"
CONFIGURATION_DEFAULT = "default"
CONFIGURATION_CUSTOM = "custom"
CONFIGURATION_INTERPRETER = "interpreter"
CONFIGURATION_NATIVE_INTERPRETER = "native-interpreter"
CONFIGURATION_DEFAULT_MULTI = "default-multi"
CONFIGURATION_INTERPRETER_MULTI = "interpreter-multi"
CONFIGURATION_NATIVE_INTERPRETER_MULTI = "native-interpreter-multi"
CONFIGURATION_DEFAULT_MULTI_TIER = "default-multi-tier"
CONFIGURATION_NATIVE = "native"
CONFIGURATION_NATIVE_MULTI = "native-multi"
CONFIGURATION_NATIVE_MULTI_TIER = "native-multi-tier"
CONFIGURATION_SANDBOXED = "sandboxed"
CONFIGURATION_SANDBOXED_MULTI = "sandboxed-multi"

PYTHON_JAVA_EMBEDDING_VM_REGISTRY_NAME = "PythonJavaDriver"
CONFIGURATION_JAVA_EMBEDDING_MULTI = "java-driver-multi-default"
CONFIGURATION_JAVA_EMBEDDING_MULTI_SHARED = "java-driver-multi-shared"
CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI = "java-driver-interpreter-multi"
CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI_SHARED = "java-driver-interpreter-multi-shared"

DEFAULT_ITERATIONS = 10

BENCH_BGV = 'benchmarks-bgv'

# ----------------------------------------------------------------------------------------------------------------------
#
# utils
#
# ----------------------------------------------------------------------------------------------------------------------

def is_sandboxed_configuration(conf):
    return conf in (CONFIGURATION_SANDBOXED, CONFIGURATION_SANDBOXED_MULTI)


@contextmanager
def environ(env):
    def _handle_var(key_value):
        (k, v) = key_value
        if v is None:
            del os.environ[k]
        else:
            os.environ[k] = str(v)

    if env:
        prev_env = {v: os.getenv(v) for v in env}
        list(map(_handle_var, list(env.items())))
    else:
        prev_env = None

    try:
        yield
    finally:
        if prev_env:
            list(map(_handle_var, list(prev_env.items())))


# ----------------------------------------------------------------------------------------------------------------------
#
# the vm definitions
#
# ----------------------------------------------------------------------------------------------------------------------
class AbstractPythonVm(OutputCapturingVm, ABC):
    def __init__(self, name, config_name, options=None):
        super().__init__()
        self._name = name
        self._config_name = config_name
        self._options = options

    @property
    def options(self):
        return self._options

    def name(self):
        return self._name

    def config_name(self):
        return self._config_name

    @abstractproperty
    def interpreter(self):
        return None

    def post_process_command_line_args(self, args):
        return args

    def run_vm(self, args, out=None, err=None, cwd=None, nonZeroIsFatal=False, env=None):
        cmd = [self.interpreter] + args
        cmd = mx.apply_command_mapper_hooks(cmd, self.command_mapper_hooks)
        mx.logv(shlex.join(cmd))
        return mx.run(
            cmd,
            out=out,
            err=err,
            cwd=cwd,
            nonZeroIsFatal=nonZeroIsFatal,
            env=env,
        )


class AbstractPythonIterationsControlVm(AbstractPythonVm):
    def __init__(self, name, config_name, options=None, iterations=None):
        super().__init__(name, config_name, options=options)
        try:
            self._iterations = int(iterations)
        except:
            self._iterations = None

    def override_iterations(self, requested_iterations):
        return self._iterations if self._iterations is not None else requested_iterations

    def _override_iterations_args(self, args):
        _args = []
        i = 0
        while i < len(args):
            arg = args[i]
            _args.append(arg)
            if arg == '-i':
                _args.append(str(self.override_iterations(int(args[i + 1]))))
                i += 1
            i += 1
        return _args

    def run_vm(self, args, *splat, **kwargs):
        args = self._override_iterations_args(args)
        return super().run_vm(args, *splat, **kwargs)


class CPythonVm(AbstractPythonIterationsControlVm):
    def __init__(self, config_name, options=None, virtualenv=None, iterations=0):
        super().__init__("cpython", config_name, options=options, iterations=iterations)
        self._virtualenv = virtualenv

    def override_iterations(self, requested_iterations):
        # CPython has no JIT right now, just a quickening interpreter
        return min(requested_iterations, 3)

    @property
    def interpreter(self):
        if venv := self._virtualenv:
            path = os.path.join(venv, 'bin', 'python')
            mx.log(f"Using CPython from virtualenv: {path}")
        elif python3_home := mx.get_env('PYTHON3_HOME'):
            path = os.path.join(python3_home, 'python')
            mx.log(f"Using CPython from PYTHON3_HOME: {path}")
        elif path := shutil.which('python'):
            mx.log(f"Using CPython from PATH: {path}")
        else:
            assert sys.implementation.name == 'cpython', "Cannot find CPython"
            path = sys.executable
            mx.log(f"Using CPython from sys.executable: {path}")
        return path

    def run_vm(self, args, *splat, **kwargs):
        for idx, arg in enumerate(args):
            if "--vm.Xmx" in arg:
                mx.warn(f"Ignoring {arg}, cannot restrict memory on CPython.")
                args = args[:idx] + args[idx + 1 :]
                break
        return super().run_vm(args, *splat, **kwargs)


class PyPyVm(AbstractPythonIterationsControlVm):
    def __init__(self, config_name, options=None, virtualenv=None, iterations=0):
        super().__init__("pypy", config_name, options=options, iterations=iterations)

    def override_iterations(self, requested_iterations):
        # PyPy warms up much faster, half should be enough
        return int(requested_iterations / 2)

    @property
    def interpreter(self):
        if home := mx.get_env("PYPY_HOME"):
            exe = join(home, "bin", "pypy3")
        else:
            try:
                exe = subprocess.check_output("which pypy3", shell=True).decode().strip()
            except OSError:
                mx.abort("PYPY_HOME is not set!")
        mx.log(f"PyPy {exe=}")
        return exe

    def run_vm(self, args, *splat, env=None, **kwargs):
        env = env or os.environ.copy()
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
        return super().run_vm(args, *splat, env=env, **kwargs)


class GraalPythonVm(AbstractPythonIterationsControlVm):
    def __init__(self, config_name, options=None, virtualenv=None, iterations=None, extra_polyglot_args=None):
        super().__init__(VM_NAME_GRAALPYTHON, config_name, options=options, iterations=iterations)
        self._extra_polyglot_args = extra_polyglot_args or []

    def override_iterations(self, requested_iterations):
        # If we are native and without JIT, half as many iterations should be enough
        if self.launcher_type == "native" and "interpreter" in self.config_name():
            return int(requested_iterations / 2)
        else:
            return requested_iterations

    @property
    @functools.lru_cache
    def launcher_type(self):
        if mx.dependency("GRAALPY_NATIVE_STANDALONE", fatalIfMissing=False):
            return "native"
        else:
            return "jvm"

    @property
    @functools.lru_cache
    def interpreter(self):
        if self.config_name().startswith(CONFIGURATION_CUSTOM):
            home = mx.get_env("GRAALPY_HOME")
            if not home:
                mx.abort("The custom benchmark config for graalpy is to run with a custom GRAALPY_HOME locally")
            launcher = join(home, "bin", "graalpy")
            mx.log(f"Using {launcher} based on GRAALPY_HOME environment for custom config.")
            return launcher
        from mx_graalpython import graalpy_standalone
        launcher = graalpy_standalone(self.launcher_type, build=False)
        mx.log(f"Using {launcher} based on enabled/excluded GraalPy standalone build targets.")
        return launcher

    def post_process_command_line_args(self, args):
        return self.get_extra_polyglot_args() + args

    def extract_vm_info(self, args=None):
        out_version = subprocess.check_output([self.interpreter, '--version'], universal_newlines=True)
        # The benchmark data goes back a ways, we modify the reported dims for
        # continuity with the historical queries
        graalvm_version_match = re.search(r"\(([^\)]+ ((?:\d+\.?)+)).*\)", out_version)
        if not graalvm_version_match:
            mx.log(f"Using {out_version} as platform version string input")
            graalvm_version_match = [out_version, out_version, out_version]
        dims = {
            'guest-vm': self.name(),
            'guest-vm-config': self.config_name(),
            'host-vm': 'graalvm-' + ('ee' if 'Oracle GraalVM' in out_version else 'ce'),
            'host-vm-config': self.launcher_type,
            "platform.graalvm-edition": 'EE' if 'Oracle GraalVM' in out_version else 'CE',
            "platform.graalvm-version": graalvm_version_match[2],
            "platform.graalvm-version-string": graalvm_version_match[1],
        }
        if dims['guest-vm-config'].endswith('-3-compiler-threads'):
            dims['guest-vm-config'] = dims['guest-vm-config'].replace('-3-compiler-threads', '')
            dims['host-vm-config'] += '-3-compiler-threads'
        self._dims = dims

    def run(self, *args, **kwargs):
        code, out, dims = super().run(*args, **kwargs)
        dims.update(self._dims)
        return code, out, dims

    def get_extra_polyglot_args(self):
        return ["--experimental-options", "-snapshot-startup", "--python.MaxNativeMemory=%s" % (2**34), *self._extra_polyglot_args]


class GraalPythonJavaDriverVm(GuestVm):
    def __init__(self, config_name=CONFIGURATION_DEFAULT, cp_suffix=None, distributions=None, cp_prefix=None,
                 host_vm=None, extra_vm_args=None, extra_polyglot_args=None):
        super().__init__(host_vm=host_vm)
        self._config_name = config_name
        self._distributions = distributions or ['GRAALPYTHON_BENCH']
        self._cp_suffix = cp_suffix
        self._cp_prefix = cp_prefix
        self._extra_vm_args = extra_vm_args
        self._extra_polyglot_args = extra_polyglot_args if isinstance(extra_polyglot_args, list) else []

    def name(self):
        return VM_NAME_GRAALPYTHON

    def config_name(self):
        return self._config_name

    def hosting_registry(self):
        return java_vm_registry

    def get_classpath(self):
        cp = []
        if self._cp_prefix:
            cp.append(self._cp_prefix)
        if self._cp_suffix:
            cp.append(self._cp_suffix)
        return cp

    def with_host_vm(self, host_vm):
        return self.__class__(config_name=self._config_name, distributions=self._distributions,
                              cp_suffix=self._cp_suffix, cp_prefix=self._cp_prefix, host_vm=host_vm,
                              extra_vm_args=self._extra_vm_args, extra_polyglot_args=self._extra_polyglot_args)

    def launcher_class(self):
        return 'com.oracle.graal.python.benchmarks.JavaBenchmarkDriver'

    def run(self, cwd, args):
        extra_polyglot_args = self.get_extra_polyglot_args()
        host_vm = self.host_vm()
        cp = self.get_classpath()
        jhm = mx.dependency("mx:JMH_1_21")
        cp_deps = mx.classpath_entries(filter(None, [
            mx.distribution('GRAALPYTHON_BENCH', fatalIfMissing=True),
            mx.distribution('TRUFFLE_RUNTIME', fatalIfMissing=True),
            mx.distribution('TRUFFLE_ENTERPRISE', fatalIfMissing=False),
            jhm,
            mx.dependency("sdk:LAUNCHER_COMMON")
        ] + jhm.deps))
        cp += [x.classpath_repr() for x in cp_deps]
        java_args = ['-cp', ':'.join(cp)] + [self.launcher_class()]
        out = mx.TeeOutputCapture(mx.OutputCapture())
        code = host_vm.run_java(java_args + extra_polyglot_args + args, cwd=cwd, out=out, err=out)
        out = out.underlying.data
        dims = host_vm.dimensions(cwd, args, code, out)
        return code, out, dims

    def get_extra_polyglot_args(self):
        return ["--experimental-options", "--python.MaxNativeMemory=%s" % (2**34), *self._extra_polyglot_args]


# ----------------------------------------------------------------------------------------------------------------------
#
# the benchmark definition
#
# ----------------------------------------------------------------------------------------------------------------------
python_vm_registry = mx_benchmark.VmRegistry(PYTHON_VM_REGISTRY_NAME, known_host_registries=[java_vm_registry])
python_java_embedding_vm_registry = mx_benchmark.VmRegistry(PYTHON_JAVA_EMBEDDING_VM_REGISTRY_NAME, known_host_registries=[java_vm_registry])


class PythonBaseBenchmarkSuite(VmBenchmarkSuite, AveragingBenchmarkMixin):
    def __init__(self, name, benchmarks):
        super().__init__()
        self._checkup = 'GRAALPYTHON_BENCHMARKS_CHECKUP' in os.environ
        self._name = name
        self._benchmarks = benchmarks
        self._graph_dump_time = None
        self._benchmarks_graphs = {}

    def has_graph_tags(self, benchmark):
        has_tag = False
        self._benchmarks_graphs[benchmark] = set()
        benchmark_path = join(self._bench_path, "{}.py".format(benchmark))
        with open(benchmark_path, 'r') as fp:
            text = fp.read()

        for l in text.split('\n'):
            if '# igv:' in l:
                s = l.replace('# igv:', '').strip()
                if s:
                    self._benchmarks_graphs[benchmark].add(s)
                    has_tag = True

        return benchmark if has_tag else None

    def has_graph_option(self, bmSuiteArgs):
        if "--graph" in bmSuiteArgs:
            import time
            self._graph_dump_time = time.time()
            os.mkdir(join(BENCH_BGV, '%d' % self._graph_dump_time))
            bmSuiteArgs.remove('--graph')
            return True
        return False

    def add_graph_dump_option(self, bmSuiteArgs, benchmark):
        if self._graph_dump_time and self._benchmarks_graphs.get(benchmark):
            dump_opt = '-Dgraal.Dump='
            dump_path = join(BENCH_BGV, '%d' % self._graph_dump_time, 'run')
            dump_path_opt = '-Dgraal.DumpPath=' + dump_path
            if dump_opt not in bmSuiteArgs:
                return [dump_opt, dump_path_opt]
            return [dump_path_opt]

        return []

    def post_run_graph(self, benchmark, host_vm_config, guest_vm_config):
        graph_tags = self._benchmarks_graphs.get(benchmark)
        if self._graph_dump_time and graph_tags:
            repo_hash = SUITE.vc.tip(SUITE.dir, 'master').strip()[:7]
            name_format = '{h}.{g_vm}.{h_vm}.{s}.{b}.{t}.bgv'
            dump_path = join(BENCH_BGV, '%d' % self._graph_dump_time, 'run')
            selected_bgv = {}
            num_tags = len(graph_tags)
            for f in sorted(os.listdir(dump_path), reverse=True):
                selected = False
                if num_tags == 0 or not f.endswith('.bgv'):
                    os.remove(join(dump_path, f))
                    continue
                for t in graph_tags:
                    if t in selected_bgv:
                        continue
                    if t in f:
                        graph_tags.remove(t)
                        selected = True
                        num_tags -= 1
                        selected_bgv[t] = f
                        break
                if not selected:
                    os.remove(join(dump_path, f))
            bench_bgv_dir = join(BENCH_BGV, '%d' % self._graph_dump_time)
            for t in selected_bgv:
                old_name = selected_bgv[t]
                new_name = name_format.format(
                    h=repo_hash,
                    g_vm=guest_vm_config,
                    h_vm=host_vm_config,
                    s=self._name,
                    b=benchmark,
                    t=t)
                print('rename: %s to %s' % (old_name, new_name))
                os.rename(join(dump_path, old_name), join(bench_bgv_dir, new_name))
            if graph_tags:
                print('The following tags did not correspond to any bgv file:')
                print('\n'.join(graph_tags))
                raise FileNotFoundError

    def benchmarkList(self, bmSuiteArgs):
        if self.has_graph_option(bmSuiteArgs):
            return [b for b in self._benchmarks.keys() if self.has_graph_tags(b)]
        return list(self._benchmarks.keys())

    def benchmarks(self):
        raise FutureWarning('the benchmarks method has been deprecated for VmBenchmarkSuite instances, '
                            'use the benchmarkList method instead')

    def successPatterns(self):
        return [
            re.compile(r"^### iteration=(?P<iteration>[0-9]+), name=(?P<benchmark>[a-zA-Z0-9.\-_]+), duration=(?P<time>[0-9]+(\.[0-9]+)?$)", re.MULTILINE),  # pylint: disable=line-too-long
            re.compile(r"^@@@ name=(?P<benchmark>[a-zA-Z0-9.\-_]+), duration=(?P<time>[0-9]+(\.[0-9]+)?$)", re.MULTILINE),  # pylint: disable=line-too-long
        ]

    def failurePatterns(self):
        return [
            # lookahead pattern for when truffle compilation details are enabled in the log
            re.compile(r"^(?!(\[truffle\])).*Exception")
        ]

    def group(self):
        return GROUP_GRAAL

    def name(self):
        return self._name

    def subgroup(self):
        return SUBGROUP_GRAAL_PYTHON

    @staticmethod
    def with_branch_and_commit_dict(d):
        """
        We run our benchmark from the graalpython directories, but with other
        suites as primary suites in the CI, so we potentially want to update
        branch and commit info.
        """
        if mx.primary_suite().dir != os.getcwd():
            if any(os.path.isdir(d) and d.startswith("mx.graalpython") for d in os.listdir()):
                vc = SUITE.vc
                if vc is None:
                    return d
                # We want to report the commit from graalpython repo, but the branch from the current repo. The
                # reason is that apptests benchmarks may be run from a PR and then the branch detection misbehaves in
                # the gates, always reporting master for some reason.
                branch = vc.active_branch(os.getcwd(), abortOnError=False) or "<unknown>"
                info = vc.parent_info(SUITE.dir)
                url = vc.default_pull(SUITE.dir, abortOnError=False) or "unknown"
                d.update({
                    "branch": branch,
                    "commit.rev": vc.parent(SUITE.dir),
                    "commit.repo-url": url,
                    "commit.author": info["author"],
                    "commit.author-ts": info["author-ts"],
                    "commit.committer": info["committer"],
                    "commit.committer-ts": info["committer-ts"],
                })
        return d

    def rules(self, output, benchmarks, bm_suite_args):
        bench_name = self.get_bench_name(benchmarks)
        arg = self.get_arg(self.runArgs(bm_suite_args), bench_name)

        return [
            # warmup curves
            StdOutRule(
                r"^### iteration=(?P<iteration>[0-9]+), name=(?P<benchmark>[a-zA-Z0-9._\-]+), duration=(?P<time>[0-9]+(\.[0-9]+)?$)",  # pylint: disable=line-too-long
                self.with_branch_and_commit_dict({
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "warmup",
                    "metric.iteration": ("<iteration>", int),
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                })
            ),
            # secondary metric(s)
            StdOutRule(
                r"### WARMUP detected at iteration: (?P<endOfWarmup>[0-9]+$)",
                self.with_branch_and_commit_dict({
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "end-of-warmup",
                    "metric.iteration": 0,
                    "metric.type": "numeric",
                    "metric.value": ("<endOfWarmup>", int),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                })
            ),

            # no warmups
            StdOutRule(
                r"^@@@ name=(?P<benchmark>[a-zA-Z0-9._\-]+), duration=(?P<time>[0-9]+(\.[0-9]+)?$)",  # pylint: disable=line-too-long
                self.with_branch_and_commit_dict({
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "time",
                    "metric.iteration": 0,
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                })
            ),
        ]

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        ret_code, out, dims = super().runAndReturnStdOut(benchmarks, bmSuiteArgs)

        self.post_run_graph(benchmarks[0], dims['host-vm-config'], dims['guest-vm-config'])

        if self._checkup:
            self.checkup(out)

        return ret_code, out, dims

    def run(self, benchmarks, bm_suite_args):
        if '--checkup' in bm_suite_args:
            self._checkup = True
            bm_suite_args.remove('--checkup')
        results = super().run(benchmarks, bm_suite_args)
        self.addAverageAcrossLatestResults(results)
        return results

    def defaultIterations(self, bm):
        default_bench_args = self._benchmarks[bm]
        if "-i" in default_bench_args:
            bench_idx = default_bench_args.index("-i")
            if bench_idx + 1 < len(default_bench_args):
                return int(default_bench_args[bench_idx + 1])
        return DEFAULT_ITERATIONS

    def postprocess_run_args(self, run_args):
        vm_options = []
        remaining = []
        i = 0

        while i < len(run_args):
            arg = run_args[i]
            if not arg.startswith("-"):
                remaining = run_args[i:]
                break
            if arg.startswith("-i"):
                if len(run_args) >= i and run_args[i + 1] == "-1":
                    pass
                elif self._checkup and len(run_args) >= i:
                    iterations = int(run_args[i + 1]) * 2
                    remaining = ["-i", str(iterations)] + run_args[i+2:]
                    break
                else:
                    remaining = run_args[i:]
                    break
            else:
                vm_options.append(arg)
            i += 1

        if not (remaining and "-i" in remaining):
            iterations = DEFAULT_ITERATIONS + self.getExtraIterationCount(DEFAULT_ITERATIONS)
            if self._checkup:
                iterations *= 2
            remaining = ["-i", str(iterations)] + (remaining if remaining else [])

        if self._checkup:
            vm_options += ['--engine.TraceCompilation', '--vm.XX:+PrintGC']
            remaining += ['--live-results']

        return vm_options, remaining

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        return self.createVmCommandLineArgs(benchmarks, bmSuiteArgs)

    def checkup(self, out):
        lines = out.split('\n')
        benchmark_name = None
        current_iteration = -1
        iterations_count = -1
        iteration_times = []
        gc_times = []
        late_compilation = -1
        for i in range(len(lines)):
            line = lines[i]

            # this marks the beginning of an output of a benchmark
            benchmark_info = re.search("### (.*), \\d+ warmup iterations, (\\d+) bench iterations", line)
            if benchmark_name is None and benchmark_info:
                benchmark_name = benchmark_info.group(1)
                iterations_count = int(benchmark_info.group(2))
                mx.log("Checking benchmark %s with %d iterations" % (benchmark_name, iterations_count))
                continue

            if benchmark_name is None:
                continue

            # this marks the end of the processing of a single benchmark
            warmup_match = re.search("### WARMUP detected at iteration: (\\d+)", line)
            if warmup_match:
                warmup = int(warmup_match.group(1))
                for i in range(warmup, len(iteration_times)):
                    if gc_times[i] > iteration_times[i] / 10:
                        mx.warn("Benchmark checkup: %s: excessive GC pause of %.8f (on %d iteration)" % (benchmark_name, gc_times[i], i))
                if warmup > iterations_count / 2:
                    mx.warn("Benchmark checkup: %s: warmup detected too late (on %d iteration)" % (benchmark_name, warmup))
                if late_compilation > 0:
                    mx.warn("Benchmark checkup: %s: compilation detected too late (on %d iteration)" % (benchmark_name, late_compilation))
                iteration_times = []
                gc_times = []
                current_iteration = -1
                benchmark_name = None
                late_compilation = False
                continue

            # following is done only when we are inside benchmark output:
            iteration_info = re.search("### iteration=(\\d+), name=.*, duration=([0-9.]*)", line)
            if iteration_info:
                current_iteration += 1
                iteration_times += [float(iteration_info.group(2))]
                gc_times += [0.0]
                continue

            if current_iteration == -1:
                continue

            gc_log = re.search("\\[GC .* ([0-9,]*) secs]", line)
            if gc_log:
                gc_times[len(gc_times) - 1] += float(gc_log.group(1).replace(',', '.'))

            if current_iteration >= iterations_count / 2 and "[engine] opt done" in line:
                late_compilation = current_iteration


class PythonBenchmarkSuite(PythonBaseBenchmarkSuite):
    def __init__(self, name, bench_path, benchmarks, python_path=None):
        super().__init__(name, benchmarks)
        self._python_path = python_path
        self._harness_path = HARNESS_PATH
        self._harness_path = join(SUITE.dir, self._harness_path)
        if not self._harness_path:
            mx.abort("python harness path not specified!")

        if isinstance(bench_path, str):
            self._bench_path = {bench: join(SUITE.dir, bench_path) for bench in benchmarks}
        else:
            self._bench_path = bench_path
        assert isinstance(self._bench_path, dict), "bench_path is not a dict, got {}".format(self._bench_path)
        assert self._bench_path.keys() == benchmarks.keys(), "not all benchmarks have a path: {}".format(
            benchmarks.keys().difference(self._bench_path.keys()))

    @staticmethod
    def get_bench_name(benchmarks):
        return os.path.basename(os.path.splitext(benchmarks[0])[0])

    def get_arg(self, bmSuiteArgs, bench_name):
        return " ".join(self._benchmarks[bench_name] + bmSuiteArgs)

    def createVmCommandLineArgs(self, benchmarks, bmSuiteArgs):
        if not benchmarks or len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark {}:<benchmark-name>) or all the benchmarks "
                     "(mx benchmark {}:*)".format(self.name(), self.name()))

        benchmark = benchmarks[0]
        vm_args = self.add_graph_dump_option(bmSuiteArgs, benchmark)
        vm_args += self.vmArgs(bmSuiteArgs)
        run_args = self.runArgs(bmSuiteArgs)

        cmd_args = [self._harness_path]

        # resolve the harness python path (for external python modules, that may be required by the benchmark)
        if self._python_path:
            assert isinstance(self._python_path, list), "python_path must be a list"
            python_path = []
            for pth in self._python_path:
                if hasattr(pth, '__call__'):
                    pth = pth()
                assert isinstance(pth, str)
                python_path.append(pth)
            cmd_args += ['-p', ",".join(python_path)]

        # the benchmark
        cmd_args += [join(self._bench_path[benchmark], "{}.py".format(benchmark))]

        if "-i" not in run_args:
            run_args += self._benchmarks[benchmark]
            num_iterations = self.defaultIterations(benchmark) + self.getExtraIterationCount(self.defaultIterations(benchmark))
            run_args[run_args.index("-i") + 1] = str(num_iterations)
        vm_options, run_args = self.postprocess_run_args(run_args)
        cmd_args.extend(run_args)
        return vm_options + vm_args + cmd_args

    def get_vm_registry(self):
        return python_vm_registry

    @classmethod
    def get_benchmark_suites(cls, benchmarks):
        assert isinstance(benchmarks, dict), "benchmarks must be a dict: {suite: [path, {bench: args, ... }], ...}"
        return [cls(suite_name, suite_info[0], suite_info[1])
                for suite_name, suite_info in benchmarks.items()]


class PythonJavaEmbeddingBenchmarkSuite(PythonBaseBenchmarkSuite):
    def __init__(self, name, bench_path, benchmarks):
        super().__init__(name, benchmarks)
        self._bench_path = bench_path

    def get_vm_registry(self):
        return python_java_embedding_vm_registry

    def get_bench_name(self, benchmarks):
        return os.path.basename(os.path.splitext(benchmarks[0])[0])

    def get_arg(self, bmSuiteArgs, bench_name):
        # returns arguments with which the benchmark was running for the results reporting
        return " ".join(self._benchmarks[bench_name][1:] + bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchmark = benchmarks[0]

        vm_args = self.add_graph_dump_option(bmSuiteArgs, benchmark)
        vm_args += self.vmArgs(bmSuiteArgs)

        run_args = self.runArgs(bmSuiteArgs)
        if "-i" not in run_args:
            # if not passed explicitly by the user, use the default args for this benchmark
            run_args += self._benchmarks[benchmark]

        # adds default -i value if missing and splits to VM options and args for the harness
        vm_options, run_args = self.postprocess_run_args(run_args)

        return vm_options + vm_args + ['-path', self._bench_path] + [benchmark] + run_args

    @classmethod
    def get_benchmark_suites(cls, benchmarks):
        assert isinstance(benchmarks, dict), "benchmarks must be a dict: {suite: [path, {bench: args, ... }], ...}"
        return [cls(suite_name, suite_info[0], suite_info[1])
                for suite_name, suite_info in benchmarks.items()]


class PythonVmWarmupBenchmarkSuite(PythonBenchmarkSuite):
    def rules(self, output, benchmarks, bm_suite_args):
        bench_name = self.get_bench_name(benchmarks)
        arg = self.get_arg(bm_suite_args, bench_name)

        return [
            # startup (difference between start of VM to end of first iteration)
            StdOutRule(
                r"### STARTUP +at iteration: (?P<iteration>[0-9]+), +duration: (?P<time>[0-9]+(\.[0-9]+)?$)",
                self.with_branch_and_commit_dict({
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "startup",
                    "metric.iteration": ("<iteration>", int),
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                })
            ),

            StdOutRule(
                r"### EARLY WARMUP +at iteration: (?P<iteration>[0-9]+), +duration: (?P<time>[0-9]+(\.[0-9]+)?$)",
                self.with_branch_and_commit_dict({
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "early-warmup",
                    "metric.iteration": ("<iteration>", int),
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                })
            ),

            StdOutRule(
                r"### LATE WARMUP +at iteration: (?P<iteration>[0-9]+), +duration: (?P<time>[0-9]+(\.[0-9]+)?$)",
                self.with_branch_and_commit_dict({
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "late-warmup",
                    "metric.iteration": ("<iteration>", int),
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                })
            ),
        ]


class PythonParserBenchmarkSuite(PythonBaseBenchmarkSuite): # pylint: disable=too-many-ancestors
    def get_vm_registry(self):
        return java_vm_registry

    def get_bench_name(self, benchmarks):
        return benchmarks[0]

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        vmArgs = self.vmArgs(bmSuiteArgs)
        dists = ["GRAALPYTHON", "GRAALPYTHON-LAUNCHER"]
        if mx.suite("tools", fatalIfMissing=False):
            dists.extend(('CHROMEINSPECTOR', 'TRUFFLE_PROFILER'))

        vmArgs += [
            "-Dorg.graalvm.language.python.home=%s" % mx.dependency("GRAALPYTHON_GRAALVM_SUPPORT").get_output(),
        ]
        vmArgs += mx.get_runtime_jvm_args(dists + ['com.oracle.graal.python.benchmarks'], jdk=mx.get_jdk())
        jmh_entry = ["com.oracle.graal.python.benchmarks.parser.ParserBenchRunner"]
        runArgs = self.runArgs(bmSuiteArgs)

        bench_name = benchmarks[0]
        bench_args = self._benchmarks[bench_name]
        return vmArgs + jmh_entry + runArgs + [bench_name] + bench_args

    def get_arg(self, bmSuiteArgs, bench_name):
        return " ".join(self._benchmarks[bench_name][1:] + bmSuiteArgs)

    @classmethod
    def get_benchmark_suites(cls, benchmarks):
        assert isinstance(benchmarks, dict), "benchmarks must be a dict: {suite: {bench: args, ... }, ...}"
        return [cls(suite_name, suite_info[0]) for suite_name, suite_info in benchmarks.items()]

class PythonJMHDistMxBenchmarkSuite(mx_benchmark.JMHDistBenchmarkSuite):
    def name(self):
        return "python-jmh"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "graalpython"

    def filter_distribution(self, dist):
        # Note: for some reason the GRAALPYTHON_BENCH is filtered out by the base class,
        # but by overriding this method we fix that and also get the nice property
        # that one cannot accidentally run some other JMH benchmarks via this class
        return dist.name == 'GRAALPYTHON_BENCH'


class LiveHeapTracker(mx_benchmark.Tracker):
    def __init__(self, bmSuite):
        super().__init__(bmSuite)
        self.out_file = None

    def map_command(self, cmd):
        bench_name = self.bmSuite.currently_running_benchmark() if self.bmSuite else "benchmark"
        if self.bmSuite:
            bench_name = f"{self.bmSuite.name()}-{bench_name}"
        ts = datetime.now().strftime("%Y%m%d-%H%M%S")
        vm = bm_exec_context().get('vm')
        if isinstance(vm, GraalPythonVm) and vm.launcher_type == "jvm":
            jmap_command = mx.get_jdk().exe_path('jmap')
        else:
            jmap_command = ""
        self.out_file = os.path.join(os.getcwd(), f"heap_tracker_{bench_name}_{ts}.txt")
        iterations = 3
        if "-i" in cmd:
            cmd[cmd.index("-i") + 1] = "1"
        return [sys.executable, str(DIR / 'live_heap_tracker.py'), self.out_file, str(iterations), jmap_command, *cmd]

    def get_rules(self, bmSuiteArgs):
        return [self.LiveHeapRule(self, bmSuiteArgs)]

    class LiveHeapRule(mx_benchmark.Rule):
        def __init__(self, tracker, bmSuiteArgs):
            self.tracker = tracker
            self.bmSuiteArgs = bmSuiteArgs

        def parse(self, text):
            with open(self.tracker.out_file) as f:
                heap_mb, uss_mb = zip(*(map(lambda i: int(i) / (1024 ** 2), line.split()) for line in f if line))
            os.unlink(self.tracker.out_file)
            heap_deciles = statistics.quantiles(heap_mb, n=10)
            uss_deciles = statistics.quantiles(uss_mb, n=10)
            print(f"Heap size deciles (MiB): {heap_deciles}")
            print(f"USS size deciles (MiB): {uss_deciles}")
            # The heap benchmarks are a separate suite, because they are run
            # very differently, but we want to be able to conveniently query
            # all data about the same suites that we have. So, if this suite
            # name ends with "-heap", we drop that so it gets attributed to the
            # base suite.
            suite = self.tracker.bmSuite.benchSuiteName(self.bmSuiteArgs)
            if suite.endswith("-heap"):
                suite = suite[:-len("-heap")]
            benchmark = f"{suite}.{self.tracker.bmSuite.currently_running_benchmark()}"
            vm_flags = ' '.join(self.tracker.bmSuite.vmArgs(self.bmSuiteArgs))
            return (
                [
                    PythonBaseBenchmarkSuite.with_branch_and_commit_dict({
                        "benchmark": benchmark,
                        "bench-suite": suite,
                        "config.vm-flags": vm_flags,
                        "metric.name": "allocated-memory",
                        "metric.value": heap_deciles[-1],
                        "metric.unit": "MB",
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.better": "lower",
                        "metric.iteration": 0
                    })
                ] if heap_deciles[-1] != 0 else []
            ) + [PythonBaseBenchmarkSuite.with_branch_and_commit_dict({
                "benchmark": benchmark,
                "bench-suite": suite,
                "config.vm-flags": vm_flags,
                "metric.name": "memory",
                "metric.value": uss_deciles[-1],
                "metric.unit": "MB",
                "metric.type": "numeric",
                "metric.score-function": "id",
                "metric.better": "lower",
                "metric.iteration": 0
            })]


class PythonHeapBenchmarkSuite(PythonBaseBenchmarkSuite):
    def __init__(self, name, bench_path, benchmarks):
        super().__init__(name, benchmarks)
        super().register_tracker('live-heap', LiveHeapTracker)
        self._bench_path = bench_path

    def get_vm_registry(self):
        return python_vm_registry

    def rules(self, output, benchmarks, bm_suite_args):
        return []  # Tracker will add a rule

    def register_tracker(self, name, tracker_type):
        # We don't want any other trackers
        pass

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        benchmark = benchmarks[0]
        bench_path = os.path.join(self._bench_path, f'{benchmark}.py')
        bench_args = self._benchmarks[benchmark]
        run_args = self.runArgs(bmSuiteArgs)
        cmd_args = []
        if "-i" in bench_args:
            # Need to use the harness to parse
            cmd_args.append(HARNESS_PATH)
        if "-i" not in run_args:
            # Explicit iteration count overrides default
            run_args += bench_args
        cmd_args.append(bench_path)
        return [*self.vmArgs(bmSuiteArgs), *cmd_args, *run_args]

    def successPatterns(self):
        return []

    def failurePatterns(self):
        return []

    @classmethod
    def get_benchmark_suites(cls, benchmarks):
        assert isinstance(benchmarks, dict), "benchmarks must be a dict: {suite: [path, {bench: args, ... }], ...}"
        return [cls(suite_name, suite_info[0], suite_info[1])
                for suite_name, suite_info in benchmarks.items()]


def register_vms(suite, sandboxed_options):
    # Other Python VMs:
    python_vm_registry.add_vm(CPythonVm(config_name=CONFIGURATION_DEFAULT), suite)
    python_vm_registry.add_vm(PyPyVm(config_name=CONFIGURATION_DEFAULT), suite)
    # For continuity with old datapoints, provide CPython and PyPy with launcher config_name
    python_vm_registry.add_vm(CPythonVm(config_name="launcher"), suite)
    python_vm_registry.add_vm(PyPyVm(config_name="launcher"), suite)

    graalpy_vms = []

    def add_graalpy_vm(name, *extra_polyglot_args):
        graalpy_vms.append((name, extra_polyglot_args))
        python_vm_registry.add_vm(GraalPythonVm(config_name=name, extra_polyglot_args=extra_polyglot_args), suite, 10)

    # GraalPy VMs:
    add_graalpy_vm(CONFIGURATION_DEFAULT)
    add_graalpy_vm(CONFIGURATION_CUSTOM)
    add_graalpy_vm(CONFIGURATION_INTERPRETER, '--experimental-options', '--engine.Compilation=false')
    add_graalpy_vm(CONFIGURATION_DEFAULT_MULTI, '--experimental-options', '-multi-context')
    add_graalpy_vm(CONFIGURATION_INTERPRETER_MULTI, '--experimental-options', '-multi-context', '--engine.Compilation=false')
    add_graalpy_vm(CONFIGURATION_DEFAULT_MULTI_TIER, '--experimental-options', '--engine.MultiTier=true')
    add_graalpy_vm(CONFIGURATION_SANDBOXED, *sandboxed_options)
    add_graalpy_vm(CONFIGURATION_NATIVE)
    add_graalpy_vm(CONFIGURATION_NATIVE_INTERPRETER, '--experimental-options', '--engine.Compilation=false')
    add_graalpy_vm(CONFIGURATION_SANDBOXED_MULTI, '--experimental-options', '-multi-context', *sandboxed_options)
    add_graalpy_vm(CONFIGURATION_NATIVE_MULTI, '--experimental-options', '-multi-context')
    add_graalpy_vm(CONFIGURATION_NATIVE_INTERPRETER_MULTI, '--experimental-options', '-multi-context', '--engine.Compilation=false')
    add_graalpy_vm(CONFIGURATION_NATIVE_MULTI_TIER, '--experimental-options', '--engine.MultiTier=true')

    # all of the graalpy vms, but with different numbers of compiler threads
    for name, extra_polyglot_args in graalpy_vms[:]:
        add_graalpy_vm(f'{name}-1-compiler-threads', *['--engine.CompilerThreads=1', *extra_polyglot_args])
        add_graalpy_vm(f'{name}-3-compiler-threads', *['--engine.CompilerThreads=3', *extra_polyglot_args])

    # java embedding driver
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_MULTI,
                                extra_polyglot_args=['-multi-context']), suite, 10)
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_MULTI_SHARED,
                                extra_polyglot_args=['-multi-context', '-shared-engine']), suite, 10)
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI,
                                extra_polyglot_args=['-multi-context', '-interpreter']), suite, 10)
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI_SHARED,
                                extra_polyglot_args=['-multi-context', '-interpreter', '-shared-engine']), suite, 10)


def register_suites():
    from mx_graalpython_bench_param import BENCHMARKS, JAVA_DRIVER_BENCHMARKS, WARMUP_BENCHMARKS, HEAP_BENCHMARKS

    for py_bench_suite in PythonBenchmarkSuite.get_benchmark_suites(BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    for py_bench_suite in PythonJavaEmbeddingBenchmarkSuite.get_benchmark_suites(JAVA_DRIVER_BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    for py_bench_suite in PythonVmWarmupBenchmarkSuite.get_benchmark_suites(WARMUP_BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    mx_benchmark.add_bm_suite(PythonJMHDistMxBenchmarkSuite())
    for py_bench_suite in PythonHeapBenchmarkSuite.get_benchmark_suites(HEAP_BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)


mx_polybench.register_polybench_language(mx_suite=SUITE, language="python", distributions=["GRAALPYTHON", "GRAALPYTHON_RESOURCES"])


def graalpython_polybench_runner(polybench_run: mx_polybench.PolybenchRunFunction, tags) -> None:
    fork_count_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "polybench-fork-counts.json")
    if "gate" in tags:
        polybench_run(["--jvm", "interpreter/*.py", "--experimental-options", "--engine.Compilation=false", "-w", "1", "-i", "1"])
        polybench_run(["--native", "interpreter/*.py", "--experimental-options", "--engine.Compilation=false", "-w", "1", "-i", "1"])
        polybench_run(["--native", "warmup/*.py", "-w", "1", "-i", "1", "--metric=one-shot", "--mx-benchmark-args", "--fork-count-file", fork_count_file])
    if "benchmark" in tags:
        polybench_run(["--jvm", "interpreter/*.py", "--experimental-options", "--engine.Compilation=false"])
        polybench_run(["--native", "interpreter/*.py", "--experimental-options", "--engine.Compilation=false"])
        polybench_run(["--jvm", "interpreter/*.py"])
        polybench_run(["--native", "interpreter/*.py"])
        polybench_run(["--native", "warmup/*.py", "--metric=one-shot", "--mx-benchmark-args", "--fork-count-file", fork_count_file])
        polybench_run(
            ["--jvm", "interpreter/pyinit.py", "-w", "0", "-i", "0", "--metric=none", "--mx-benchmark-args", "--fork-count-file", fork_count_file])
        polybench_run(
            ["--native", "interpreter/pyinit.py", "-w", "0", "-i", "0", "--metric=none", "--mx-benchmark-args", "--fork-count-file", fork_count_file])
        polybench_run(["--jvm", "interpreter/*.py", "--metric=metaspace-memory"])
        polybench_run(["--jvm", "interpreter/*.py", "--metric=application-memory"])
        polybench_run(["--jvm", "interpreter/*.py", "--metric=allocated-bytes", "-w", "40", "-i", "10", "--experimental-options", "--engine.Compilation=false"])
        polybench_run(["--native", "interpreter/*.py", "--metric=allocated-bytes", "-w", "40", "-i", "10", "--experimental-options", "--engine.Compilation=false"])
        polybench_run(["--jvm", "interpreter/*.py", "--metric=allocated-bytes", "-w", "40", "-i", "10"])
        polybench_run(["--native", "interpreter/*.py", "--metric=allocated-bytes", "-w", "40", "-i", "10"])
    if "instructions" in tags:
        assert mx_polybench.is_enterprise()
        polybench_run(["--native", "interpreter/*.py", "--metric=instructions", "--experimental-options", "--engine.Compilation=false",
                       "--mx-benchmark-args", "--fork-count-file", fork_count_file])


mx_polybench.register_polybench_benchmark_suite(mx_suite=SUITE, name="python", languages=["python"], benchmark_distribution="GRAALPYTHON_POLYBENCH_BENCHMARKS",
                                                # Match all python files, except for:
                                                #  * harness.py
                                                #  * tests/__init__.py
                                                # These two are necessary for running the C-extension-module benchmarks,
                                                # but are not benchmarks themselves.
                                                benchmark_file_filter=r"^(?!.*(harness\.py|tests/__init__\.py)$).*\.py$",
                                                runner=graalpython_polybench_runner, tags={"gate", "benchmark", "instructions"})
