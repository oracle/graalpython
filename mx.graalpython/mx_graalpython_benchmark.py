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

import os
import re
from abc import ABCMeta, abstractproperty, abstractmethod
from os.path import join

import mx
from mx_benchmark import StdOutRule, VmRegistry, java_vm_registry, Vm, GuestVm, VmBenchmarkSuite
from mx_graalpython_bench_param import benchmarks_list

# ----------------------------------------------------------------------------------------------------------------------
#
# the graalpython suite
#
# ----------------------------------------------------------------------------------------------------------------------
_truffle_python_suite = mx.suite("graalpython")

# ----------------------------------------------------------------------------------------------------------------------
#
# constants
#
# ----------------------------------------------------------------------------------------------------------------------
ENV_PYPY_HOME = "PYPY_HOME"
VM_NAME_TRUFFLE_PYTHON = "graalpython"
VM_NAME_CPYTHON = "cpython"
VM_NAME_PYPY = "pypy"
GROUP_GRAAL = "Graal"
SUBGROUP_GRAAL_PYTHON = "graalpython"
PYTHON_VM_REGISTRY_NAME = "Python"
CONFIGURATION_DEFAULT = "default"
_HRULE = ''.join(['-' for _ in range(120)])


# ----------------------------------------------------------------------------------------------------------------------
#
# utils
#
# ----------------------------------------------------------------------------------------------------------------------
def _check_vm_args(name, args):
    if len(args) != 1:
        mx.abort("Expected only a single benchmark path, got {} instead".format(args))
    benchmark_name = os.path.basename(os.path.splitext(args[0])[0])
    print(_HRULE)
    print(name, benchmark_name)
    print(_HRULE)


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
        stdout_capture = mx.OutputCapture()
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
    def hosting_registry(self):
        return java_vm_registry

    def run(self, cwd, args):
        _check_vm_args(self.name(), args)

        truffle_options = [
            # '-Dgraal.TruffleCompilationExceptionsAreFatal=true'
        ]

        vm_args = [
            "-Dpython.home=%s" % join(_truffle_python_suite.dir, "graalpython"),
            '-cp',
            mx.classpath(["com.oracle.graal.python", "com.oracle.graal.python.shell"]),
            "com.oracle.graal.python.shell.GraalPythonMain"
        ]

        cmd = truffle_options + vm_args + args
        return self.host_vm().run(cwd, cmd)

    def name(self):
        return VM_NAME_TRUFFLE_PYTHON

    def config_name(self):
        return CONFIGURATION_DEFAULT


# ----------------------------------------------------------------------------------------------------------------------
#
# the benchmark definition
#
# ----------------------------------------------------------------------------------------------------------------------
class PythonBenchmarkSuite(VmBenchmarkSuite):
    def __init__(self, name):
        self._name = name
        self._bench_path, self._benchmarks = benchmarks_list[self._name]
        self._bench_path = join(_truffle_python_suite.dir, self._bench_path)

    def rules(self, output, benchmarks, bm_suite_args):
        bench_name = os.path.basename(os.path.splitext(benchmarks[0])[0])
        arg = " ".join(self._benchmarks[bench_name])
        return [
            StdOutRule(
                r"^(?P<benchmark>[a-zA-Z0-9\.\-]+): (?P<time>[0-9]+(\.[0-9]+)?$)",  # pylint: disable=line-too-long
                {
                    "benchmark": '{}.{}'.format(self._name, bench_name),
                    "metric.name": "time",
                    "metric.type": "numeric",
                    "metric.value": ("<time>", float),
                    "metric.unit": "s",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "config.run-flags": "".join(arg),
                }
            ),
        ]

    def createVmCommandLineArgs(self, benchmarks, run_args):
        if not benchmarks or len(benchmarks) != 1:
            mx.abort("Please run a specific benchmark (mx benchmark {}:<benchmark-name>) or all the benchmarks "
                     "(mx benchmark {}:*)".format(self.name(), self.name()))

        benchmark = benchmarks[0]

        cmd_args = [join(self._bench_path, "{}.py".format(benchmark))]
        if len(run_args) != 0:
            cmd_args.extend(self._benchmarks[benchmark])
        else:
            cmd_args.extend(run_args)

        return cmd_args

    def benchmarkList(self, bm_suite_args):
        return self._benchmarks.keys()

    def benchmarks(self):
        raise FutureWarning('the benchmarks method has been deprecated for VmBenchmarkSuite instances, '
                            'use the benchmarkList method instead')

    def successPatterns(self):
        return [
            re.compile(r"^(?P<benchmark>[a-zA-Z0-9.\-]+): (?P<score>[0-9]+(\.[0-9]+)?$)", re.MULTILINE)
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
    def get_benchmark_suites(cls):
        return [cls(suite_name) for suite_name in benchmarks_list]


# ----------------------------------------------------------------------------------------------------------------------
#
# register locally VMs
#
# ----------------------------------------------------------------------------------------------------------------------
python_vm_registry = VmRegistry(PYTHON_VM_REGISTRY_NAME, known_host_registries=[java_vm_registry])
python_vm_registry.add_vm(CPythonVm(CONFIGURATION_DEFAULT), _truffle_python_suite)
python_vm_registry.add_vm(PyPyVm(CONFIGURATION_DEFAULT), _truffle_python_suite)
python_vm_registry.add_vm(GraalPythonVm(), _truffle_python_suite, 10)
