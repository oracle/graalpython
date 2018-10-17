# Copyright (c) 2018, Oracle and/or its affiliates.
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

import argparse
import os
import re
from abc import ABCMeta, abstractproperty, abstractmethod
from os.path import join

import mx
import mx_benchmark
from mx_benchmark import StdOutRule, java_vm_registry, Vm, GuestVm, VmBenchmarkSuite, AveragingBenchmarkMixin
from mx_graalpython_bench_param import HARNESS_PATH

# ----------------------------------------------------------------------------------------------------------------------
#
# the graalpython suite
#
# ----------------------------------------------------------------------------------------------------------------------
SUITE = mx.suite("graalpython")

# ----------------------------------------------------------------------------------------------------------------------
#
# constants
#
# ----------------------------------------------------------------------------------------------------------------------
ENV_PYPY_HOME = "PYPY_HOME"
VM_NAME_GRAALPYTHON = "graalpython"
VM_NAME_CPYTHON = "cpython"
VM_NAME_PYPY = "pypy"
GROUP_GRAAL = "Graal"
SUBGROUP_GRAAL_PYTHON = "graalpython"
PYTHON_VM_REGISTRY_NAME = "Python"
CONFIGURATION_DEFAULT = "default"

DEFAULT_ITERATIONS = 10


# ----------------------------------------------------------------------------------------------------------------------
#
# utils
#
# ----------------------------------------------------------------------------------------------------------------------
def _check_vm_args(name, args):
    if len(args) < 2:
        mx.abort("Expected at least 2 args (a single benchmark path in addition to the harness), "
                 "got {} instead".format(args))


# ----------------------------------------------------------------------------------------------------------------------
#
# the vm definitions
#
# ----------------------------------------------------------------------------------------------------------------------
class AbstractPythonVm(Vm):
    __metaclass__ = ABCMeta

    def __init__(self, config_name, options=None):
        self._config_name = config_name
        self._options = options

    @property
    def options(self):
        return self._options

    def config_name(self):
        """
        The configuration name

        :return: the configuration name
        :rtype: str or unicode
        """
        return self._config_name

    @abstractmethod
    def name(self):
        """
        The VM name

        :return: the vm name
        :rtype: str or unicode
        """
        return None

    @abstractproperty
    def interpreter(self):
        """
        the python like interpreter

        :return: the interpreter
        :rtype: str or unicode
        """
        return None

    def run(self, cwd, args):
        _check_vm_args(self.name(), args)
        stdout_capture = mx.TeeOutputCapture(mx.OutputCapture())
        ret_code = mx.run([self.interpreter] + args, out=stdout_capture, err=stdout_capture)
        print(stdout_capture.data)
        return ret_code, stdout_capture.data


class CPythonVm(AbstractPythonVm):
    @property
    def interpreter(self):
        return "python3"

    def name(self):
        return VM_NAME_CPYTHON


class PyPyVm(AbstractPythonVm):
    def __init__(self, config_name, options=None):
        super(PyPyVm, self).__init__(config_name, options=options)

    @property
    def interpreter(self):
        home = mx.get_env(ENV_PYPY_HOME)
        if not home:
            mx.abort("{} is not set!".format(ENV_PYPY_HOME))
        return join(home, 'bin', 'pypy3')

    def name(self):
        return VM_NAME_PYPY


class GraalPythonVm(GuestVm):
    def __init__(self, config_name=CONFIGURATION_DEFAULT, distributions=None, cp_suffix=None, cp_prefix=None,
                 host_vm=None):
        super(GraalPythonVm, self).__init__(host_vm=host_vm)
        self._config_name = config_name
        self._distributions = distributions
        self._cp_suffix = cp_suffix
        self._cp_prefix = cp_prefix

    def hosting_registry(self):
        return java_vm_registry

    def run(self, cwd, args):
        _check_vm_args(self.name(), args)

        truffle_options = [
            # '-Dgraal.TruffleCompilationExceptionsAreFatal=true'
        ]

        dists = ["GRAALPYTHON", "GRAALPYTHON-LAUNCHER"]
        # add configuration specified distributions
        if self._distributions:
            assert isinstance(self._distributions, list), "distributions must be either None or a list"
            dists += self._distributions

        if mx.suite("sulong", fatalIfMissing=False):
            dists.append('SULONG')
            if mx.suite("sulong-managed", fatalIfMissing=False):
                dists.append('SULONG_MANAGED')

        vm_args = mx.get_runtime_jvm_args(dists, cp_suffix=self._cp_suffix, cp_prefix=self._cp_prefix)
        vm_args += [
            "-Dpython.home=%s" % join(SUITE.dir, "graalpython"),
            "com.oracle.graal.python.shell.GraalPythonMain"
        ]
        cmd = truffle_options + vm_args + args
        return self.host_vm().run(cwd, cmd)

    def name(self):
        return VM_NAME_GRAALPYTHON

    def config_name(self):
        return self._config_name

    def with_host_vm(self, host_vm):
        return self.__class__(config_name=self._config_name, distributions=self._distributions,
                              cp_suffix=self._cp_suffix, cp_prefix=self._cp_prefix, host_vm=host_vm)


# ----------------------------------------------------------------------------------------------------------------------
#
# the benchmark definition
#
# ----------------------------------------------------------------------------------------------------------------------
python_vm_registry = mx_benchmark.VmRegistry(PYTHON_VM_REGISTRY_NAME, known_host_registries=[java_vm_registry])


class PythonBenchmarkSuite(VmBenchmarkSuite, AveragingBenchmarkMixin):
    def __init__(self, name, bench_path, benchmarks, python_path=None):
        self._name = name
        self._python_path = python_path
        self._harness_path = HARNESS_PATH
        self._harness_path = join(SUITE.dir, self._harness_path)
        if not self._harness_path:
            mx.abort("python harness path not specified!")

        self._bench_path, self._benchmarks = bench_path, benchmarks
        self._bench_path = join(SUITE.dir, self._bench_path)

    def rules(self, output, benchmarks, bm_suite_args):
        bench_name = os.path.basename(os.path.splitext(benchmarks[0])[0])
        arg = " ".join(self._benchmarks[bench_name])
        return [
            # warmup curves
            StdOutRule(
                r"^### iteration=(?P<iteration>[0-9]+), name=(?P<benchmark>[a-zA-Z0-9.\-]+), duration=(?P<time>[0-9]+(\.[0-9]+)?$)",  # pylint: disable=line-too-long
                {
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "warmup",
                    "metric.iteration": ("<iteration>", int),
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                }
            ),
            # no warmups
            StdOutRule(
                r"^@@@ name=(?P<benchmark>[a-zA-Z0-9.\-]+), duration=(?P<time>[0-9]+(\.[0-9]+)?$)",  # pylint: disable=line-too-long
                {
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "time",
                    "metric.iteration": 0,
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                }
            ),
        ]

    def run(self, benchmarks, bm_suite_args):
        results = super(PythonBenchmarkSuite, self).run(benchmarks, bm_suite_args)
        self.addAverageAcrossLatestResults(results)
        return results

    def postprocess_run_args(self, run_args):
        parser = argparse.ArgumentParser(add_help=False)
        parser.add_argument("-i", default=None)
        args, remaining = parser.parse_known_args(run_args)
        if args.i:
            if args.i.isdigit():
                return ["-i", args.i] + remaining
            if args.i == "-1":
                return remaining
        else:
            iterations = DEFAULT_ITERATIONS + self.getExtraIterationCount(DEFAULT_ITERATIONS)
            return ["-i", str(iterations)] + remaining

    def createVmCommandLineArgs(self, benchmarks, run_args):
        if not benchmarks or len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark {}:<benchmark-name>) or all the benchmarks "
                     "(mx benchmark {}:*)".format(self.name(), self.name()))

        benchmark = benchmarks[0]

        cmd_args = [self._harness_path]

        # resolve the harness python path (for external python modules, that may be required by the benchmark)
        if self._python_path:
            assert isinstance(self._python_path, list), "python_path must be a list"
            python_path = []
            for pth in self._python_path:
                if hasattr(pth, '__call__'):
                    pth = pth()
                assert isinstance(pth, (str, unicode))
                python_path.append(pth)
            cmd_args += ['-p', ",".join(python_path)]

        # the benchmark
        cmd_args += [join(self._bench_path, "{}.py".format(benchmark))]

        if len(run_args) == 0:
            run_args = self._benchmarks[benchmark]
        run_args = self.postprocess_run_args(run_args)
        cmd_args.extend(run_args)
        return cmd_args

    def benchmarkList(self, bm_suite_args):
        return self._benchmarks.keys()

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
            re.compile(r"Exception")
        ]

    def group(self):
        return GROUP_GRAAL

    def name(self):
        return self._name

    def subgroup(self):
        return SUBGROUP_GRAAL_PYTHON

    def get_vm_registry(self):
        return python_vm_registry

    @classmethod
    def get_benchmark_suites(cls, benchmarks):
        assert isinstance(benchmarks, dict), "benchmarks must be a dict: {suite: [path, {bench: args, ... }], ...}"
        return [cls(suite_name, suite_info[0], suite_info[1])
                for suite_name, suite_info in benchmarks.items()]
