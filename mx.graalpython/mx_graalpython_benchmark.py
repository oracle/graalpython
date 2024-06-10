# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
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
import subprocess
from abc import ABCMeta, abstractproperty, abstractmethod
from contextlib import contextmanager
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
ENV_PYTHON3_HOME = "PYTHON3_HOME"
ENV_VIRTUAL_ENV = "VIRTUAL_ENV"
ENV_JYTHON_JAR = "JYTHON_JAR"
VM_NAME_GRAALPYTHON = "graalpython"
VM_NAME_CPYTHON = "cpython"
VM_NAME_PYPY = "pypy"
VM_NAME_JYTHON = "jython"
VM_NAME_GRAALPYTHON_SVM = "graalpython-svm"
GROUP_GRAAL = "Graal"
SUBGROUP_GRAAL_PYTHON = "graalpython"

PYTHON_VM_REGISTRY_NAME = "Python"
CONFIGURATION_DEFAULT = "default"
CONFIGURATION_DEFAULT_DSL = "default-dsl"
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
CONFIGURATION_PANAMA = "panama"

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
def _check_vm_args(name, args):
    if len(args) < 2:
        mx.abort("Expected at least 2 args (a single benchmark path in addition to the harness), "
                 "got {} instead".format(args))


def is_sandboxed_configuration(conf):
    return conf in (CONFIGURATION_SANDBOXED, CONFIGURATION_SANDBOXED_MULTI)


# from six
def add_metaclass(metaclass):
    """Class decorator for creating a class with a metaclass."""
    def wrapper(cls):
        orig_vars = cls.__dict__.copy()
        slots = orig_vars.get('__slots__')
        if slots is not None:
            if isinstance(slots, str):
                slots = [slots]
            for slots_var in slots:
                orig_vars.pop(slots_var)
        orig_vars.pop('__dict__', None)
        orig_vars.pop('__weakref__', None)
        if hasattr(cls, '__qualname__'):
            orig_vars['__qualname__'] = cls.__qualname__
        return metaclass(cls.__name__, cls.__bases__, orig_vars)
    return wrapper


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
@add_metaclass(ABCMeta)
class AbstractPythonVm(Vm):
    def __init__(self, config_name, options=None, env=None):
        super(AbstractPythonVm, self).__init__()
        self._config_name = config_name
        self._options = options
        self._env = env

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
        out = mx.OutputCapture()
        stdout_capture = mx.TeeOutputCapture(out)
        ret_code = mx.run([self.interpreter] + args, out=stdout_capture, err=stdout_capture, env=self._env)
        return ret_code, out.data


@add_metaclass(ABCMeta)
class AbstractPythonIterationsControlVm(AbstractPythonVm):
    def __init__(self, config_name, options=None, env=None, iterations=None):
        super(AbstractPythonIterationsControlVm, self).__init__(config_name, options=options, env=env)
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

    def run(self, cwd, args):
        args = self._override_iterations_args(args)
        return super(AbstractPythonIterationsControlVm, self).run(cwd, args)


class CPythonVm(AbstractPythonIterationsControlVm):
    PYTHON_INTERPRETER = "python3"

    def __init__(self, config_name, options=None, env=None, virtualenv=None, iterations=0):
        super(CPythonVm, self).__init__(config_name, options=options, env=env, iterations=iterations)
        self._virtualenv = virtualenv

    @property
    def interpreter(self):
        venv = self._virtualenv if self._virtualenv else mx.get_env(ENV_VIRTUAL_ENV)
        if venv:
            mx.log(f"CPythonVM virtualenv={venv}")
            return os.path.join(venv, 'bin', CPythonVm.PYTHON_INTERPRETER)
        home = mx.get_env(ENV_PYTHON3_HOME)
        if home:
            mx.log(f"CPythonVM python3 home={home}")
            return os.path.join(home, CPythonVm.PYTHON_INTERPRETER)
        return CPythonVm.PYTHON_INTERPRETER

    def name(self):
        return VM_NAME_CPYTHON


class PyPyVm(AbstractPythonIterationsControlVm):
    PYPY_INTERPRETER = "pypy3"

    def __init__(self, config_name, options=None, env=None, iterations=None):
        super(PyPyVm, self).__init__(config_name, options=options, env=env, iterations=iterations)

    def override_iterations(self, requested_iterations):
        # PyPy warms up much faster, half should be enough
        return int(requested_iterations / 2)

    @property
    def interpreter(self):
        home = mx.get_env(ENV_PYPY_HOME)
        if not home:
            try:
                return subprocess.check_output("which %s" % PyPyVm.PYPY_INTERPRETER, shell=True).decode().strip()
            except OSError:
                mx.abort("{} is not set!".format(ENV_PYPY_HOME))
        return join(home, 'bin', PyPyVm.PYPY_INTERPRETER)

    def name(self):
        return VM_NAME_PYPY


class JythonVm(AbstractPythonIterationsControlVm, GuestVm):
    JYTHON_INTERPRETER = "jython"

    def __init__(self, config_name, options=None, env=None, iterations=None, host_vm=None):
        AbstractPythonIterationsControlVm.__init__(self, config_name, options=options, env=env, iterations=iterations)
        GuestVm.__init__(self, host_vm=host_vm)

    def override_iterations(self, requested_iterations):
        return 3

    def hosting_registry(self):
        return java_vm_registry

    @property
    def interpreter(self):
        try:
            return subprocess.check_output("which %s" % JythonVm.JYTHON_INTERPRETER, shell=True).decode().strip()
        except Exception as e: # pylint: disable=broad-except
            mx.log_error(e)
            mx.abort("`jython` is neither on the path, nor is {} set!\n".format(ENV_JYTHON_JAR))

    def run(self, cwd, args):
        jar = mx.get_env(ENV_JYTHON_JAR)
        if jar:
            _check_vm_args(self.name(), args)
            host_vm = self.host_vm()

            vm_args = mx.get_runtime_jvm_args([])
            vm_args += ["-jar", jar]
            for a in args[:]:
                if a.startswith("-D") or a.startswith("-XX"):
                    vm_args.insert(0, a)
                    args.remove(a)
            args = self._override_iterations_args(args)
            cmd = vm_args + args

            if not self._env:
                self._env = dict()
            with environ(self._env):
                return host_vm.run(cwd, cmd)
        else:
            return AbstractPythonIterationsControlVm.run(self, cwd, args)

    def config_name(self):
        return self._config_name

    def with_host_vm(self, host_vm):
        return self.__class__(config_name=self._config_name, options=self._options, env=self._env,
                              iterations=self._iterations, host_vm=host_vm)

    def name(self):
        return VM_NAME_JYTHON


class GraalPythonVmBase(GuestVm):
    def __init__(self, config_name=CONFIGURATION_DEFAULT, distributions=None, cp_suffix=None, cp_prefix=None,
                 host_vm=None, extra_vm_args=None, extra_polyglot_args=None, env=None):
        super(GraalPythonVmBase, self).__init__(host_vm=host_vm)
        self._config_name = config_name
        self._distributions = distributions
        self._cp_suffix = cp_suffix
        self._cp_prefix = cp_prefix
        self._extra_vm_args = extra_vm_args
        self._extra_polyglot_args = extra_polyglot_args if isinstance(extra_polyglot_args, list) else []
        self._env = env

    def hosting_registry(self):
        return java_vm_registry

    def launcher_class(self):
        raise NotImplementedError()

    def run_in_graalvm(self, cwd, args, extra_polyglot_args, host_vm):
        raise NotImplementedError()

    def get_extra_polyglot_args(self):
        raise NotImplementedError()

    def get_classpath(self):
        cp = []
        if self._cp_prefix:
            cp.append(self._cp_prefix)
        if self._cp_suffix:
            cp.append(self._cp_suffix)
        return cp

    @staticmethod
    def _remove_vm_prefix(argument):
        if argument.startswith('--vm.'):
            return '-' + argument.strip('--vm.')
        else:
            return argument

    def run(self, cwd, args):
        _check_vm_args(self.name(), args)
        extra_polyglot_args = self.get_extra_polyglot_args()

        host_vm = self.host_vm()
        if hasattr(host_vm, 'run_lang'): # this is a full GraalVM build
            return self.run_in_graalvm(cwd, args, extra_polyglot_args, host_vm)

        # Otherwise, we're running from the source tree
        args = [self._remove_vm_prefix(x) for x in args]
        truffle_options = [
            # "-Dpolyglot.engine.CompilationExceptionsAreFatal=true"
        ]

        dists = ["GRAALPYTHON", "TRUFFLE_NFI", "GRAALPYTHON-LAUNCHER"]
        # add configuration specified distributions
        if self._distributions:
            assert isinstance(self._distributions, list), "distributions must be either None or a list"
            dists += self._distributions

        if mx.suite("tools", fatalIfMissing=False):
            dists.extend(('CHROMEINSPECTOR', 'TRUFFLE_PROFILER'))
        if mx.suite("sulong", fatalIfMissing=False):
            dists.append('SULONG_NATIVE')
            if mx.suite("sulong-managed", fatalIfMissing=False):
                dists.append('SULONG_MANAGED')

        extra_polyglot_args += [
            "--python.CAPI=%s" % SUITE.extensions._get_capi_home(),
            "--python.JNIHome=%s" % SUITE.extensions._get_jni_home()
        ]

        vm_args = mx.get_runtime_jvm_args(dists, cp_suffix=self._cp_suffix, cp_prefix=self._cp_prefix)
        if isinstance(self._extra_vm_args, list):
            vm_args += self._extra_vm_args
        vm_args += [
            "-Dorg.graalvm.language.python.home=%s" % join(SUITE.dir, "graalpython"),
            self.launcher_class(),
        ]
        for a in args[:]:
            if a.startswith("-D") or a.startswith("-XX"):
                vm_args.insert(0, a)
                args.remove(a)
        cmd = truffle_options + vm_args + extra_polyglot_args + args

        if not self._env:
            self._env = dict()
        with environ(self._env):
            return host_vm.run(cwd, cmd)

    def name(self):
        return VM_NAME_GRAALPYTHON

    def config_name(self):
        return self._config_name

    def with_host_vm(self, host_vm):
        return self.__class__(config_name=self._config_name, distributions=self._distributions,
                              cp_suffix=self._cp_suffix, cp_prefix=self._cp_prefix, host_vm=host_vm,
                              extra_vm_args=self._extra_vm_args, extra_polyglot_args=self._extra_polyglot_args,
                              env=self._env)


class GraalPythonVm(GraalPythonVmBase):
    def __init__(self, config_name=CONFIGURATION_DEFAULT, distributions=None, cp_suffix=None, cp_prefix=None,
                 host_vm=None, extra_vm_args=None, extra_polyglot_args=None, env=None):
        super(GraalPythonVm, self).__init__(config_name=config_name, cp_suffix=cp_suffix, distributions=distributions,
                                            cp_prefix=cp_prefix, host_vm=host_vm, extra_vm_args=extra_vm_args,
                                            extra_polyglot_args=extra_polyglot_args, env=env)

    def launcher_class(self):
        # We need to do it lazily because 'mx_graalpython' is importing this module
        from mx_graalpython import GRAALPYTHON_MAIN_CLASS
        return GRAALPYTHON_MAIN_CLASS

    def run_in_graalvm(self, cwd, args, extra_polyglot_args, host_vm):
        with environ(self._env or {}):
            cp = self.get_classpath()
            if len(cp) > 0:
                extra_polyglot_args.append("--vm.classpath=" + ":".join(cp))
            managed = '--llvm.managed' in extra_polyglot_args or '--llvm.managed=true' in extra_polyglot_args
            launcher_name = 'graalpy-managed' if managed else 'graalpy'
            return host_vm.run_launcher(launcher_name, extra_polyglot_args + args, cwd)

    def get_extra_polyglot_args(self):
        return ["--experimental-options", "-snapshot-startup", "--python.MaxNativeMemory=%s" % (2**34)] + self._extra_polyglot_args


class GraalPythonJavaDriverVm(GraalPythonVmBase):
    def __init__(self, config_name=CONFIGURATION_DEFAULT, cp_suffix=None, distributions=None, cp_prefix=None,
                 host_vm=None, extra_vm_args=None, extra_polyglot_args=None, env=None):
        super(GraalPythonJavaDriverVm, self).__init__(config_name=config_name, cp_suffix=cp_suffix,
                                                      distributions=['GRAALPYTHON_BENCH'] if not distributions else distributions,
                                                      cp_prefix=cp_prefix, host_vm=host_vm, extra_vm_args=extra_vm_args,
                                                      extra_polyglot_args=extra_polyglot_args, env=env)

    def launcher_class(self):
        return 'com.oracle.graal.python.benchmarks.JavaBenchmarkDriver'

    def run_in_graalvm(self, cwd, args, extra_polyglot_args, host_vm):
        # In GraalVM we run the Java benchmarks driver like one would run any other Java application
        # that embeds GraalPython on GraalVM. We need to add the dependencies on class path, and since
        # we use run_java, we need to do some output postprocessing that normally run_launcher would do
        with environ(self._env or {}):
            cp = self.get_classpath()
            jhm = mx.dependency("mx:JMH_1_21")
            cp_deps = [
                mx.distribution('GRAALPYTHON_BENCH', fatalIfMissing=True),
                jhm,
                mx.dependency("sdk:LAUNCHER_COMMON")
            ] + jhm.deps
            cp += [x.classpath_repr() for x in cp_deps]
            java_args = ['-cp', ':'.join(cp)] + [self.launcher_class()]
            out = mx.TeeOutputCapture(mx.OutputCapture())
            code = host_vm.run_java(java_args + extra_polyglot_args + args, cwd=cwd, out=out, err=out)
            out = out.underlying.data
            dims = host_vm.dimensions(cwd, args, code, out)
            return code, out, dims

    def get_extra_polyglot_args(self):
        return ["--experimental-options", "--python.MaxNativeMemory=%s" % (2**34)] + self._extra_polyglot_args


# ----------------------------------------------------------------------------------------------------------------------
#
# the benchmark definition
#
# ----------------------------------------------------------------------------------------------------------------------
python_vm_registry = mx_benchmark.VmRegistry(PYTHON_VM_REGISTRY_NAME, known_host_registries=[java_vm_registry])
python_java_embedding_vm_registry = mx_benchmark.VmRegistry(PYTHON_JAVA_EMBEDDING_VM_REGISTRY_NAME, known_host_registries=[java_vm_registry])


class PythonBaseBenchmarkSuite(VmBenchmarkSuite, AveragingBenchmarkMixin):
    def __init__(self, name, benchmarks):
        super(PythonBaseBenchmarkSuite, self).__init__()
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

    def with_branch_and_commit_dict(self, d):
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
        ret_code, out, dims = super(PythonBaseBenchmarkSuite, self).runAndReturnStdOut(benchmarks, bmSuiteArgs)

        # host-vm rewrite rules
        def _replace_host_vm(key):
            host_vm = dims.get("host-vm")
            if host_vm and host_vm.startswith(key):
                dims['host-vm'] = key
                mx.logv("[DEBUG] replace 'host-vm': '{key}-python' -> '{key}'".format(key=key))

        _replace_host_vm('graalvm-ce')
        _replace_host_vm('graalvm-ee')
        self.post_run_graph(benchmarks[0], dims['host-vm-config'], dims['guest-vm-config'])

        if self._checkup:
            self.checkup(out)

        return ret_code, out, dims

    def run(self, benchmarks, bm_suite_args):
        if '--checkup' in bm_suite_args:
            self._checkup = True
            bm_suite_args.remove('--checkup')
        results = super(PythonBaseBenchmarkSuite, self).run(benchmarks, bm_suite_args)
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
        super(PythonBenchmarkSuite, self).__init__(name, benchmarks)
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
        super(PythonJavaEmbeddingBenchmarkSuite, self).__init__(name, benchmarks)
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


class PythonInteropBenchmarkSuite(PythonBaseBenchmarkSuite): # pylint: disable=too-many-ancestors
    def get_vm_registry(self):
        return java_vm_registry

    def get_bench_name(self, benchmarks):
        return benchmarks[0]

    def get_arg(self, bmSuiteArgs, bench_name):
        return " ".join(self._benchmarks[bench_name][1:] + bmSuiteArgs)

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        vmArgs = self.vmArgs(bmSuiteArgs)
        dists = ["GRAALPYTHON", "TRUFFLE_NFI", "GRAALPYTHON-LAUNCHER"]
        if mx.suite("tools", fatalIfMissing=False):
            dists.extend(('CHROMEINSPECTOR', 'TRUFFLE_PROFILER'))
        if mx.suite("sulong", fatalIfMissing=False):
            dists.append('SULONG_NATIVE')
            if mx.suite("sulong-managed", fatalIfMissing=False):
                dists.append('SULONG_MANAGED')

        vmArgs += [
            "-Dorg.graalvm.language.python.home=%s" % join(SUITE.dir, "graalpython"),
        ]
        vmArgs += mx.get_runtime_jvm_args(dists + ['com.oracle.graal.python.benchmarks'], jdk=mx.get_jdk())
        jmh_entry = ["com.oracle.graal.python.benchmarks.interop.BenchRunner"]
        runArgs = self.runArgs(bmSuiteArgs)

        bench_name = benchmarks[0]
        bench_args = self._benchmarks[bench_name]
        return vmArgs + jmh_entry + runArgs + [bench_name] + bench_args

    @classmethod
    def get_benchmark_suites(cls, benchmarks):
        assert isinstance(benchmarks, dict), "benchmarks must be a dict: {suite: {bench: args, ... }, ...}"
        return [cls(suite_name, suite_info[0]) for suite_name, suite_info in benchmarks.items()]


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
        if mx.suite("sulong", fatalIfMissing=False):
            dists.append('SULONG_NATIVE')
            if mx.suite("sulong-managed", fatalIfMissing=False):
                dists.append('SULONG_MANAGED')

        vmArgs += [
            "-Dorg.graalvm.language.python.home=%s" % join(SUITE.dir, "graalpython"),
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
