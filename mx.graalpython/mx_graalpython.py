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

import contextlib
import datetime
import fnmatch
import glob
import itertools
import json
import os
import pathlib
import re
import shlex
import shutil
import sys
import time
from functools import wraps
from pathlib import Path
from textwrap import dedent

import mx_urlrewrites

if sys.version_info[0] < 3:
    raise RuntimeError("The build scripts are no longer compatible with Python 2")

import tempfile
from argparse import ArgumentParser
from dataclasses import dataclass
import stat
from zipfile import ZipFile

import mx
import mx_util
import mx_benchmark
import mx_gate
import mx_native
import mx_unittest
import mx_sdk
import mx_subst
import mx_graalpython_bisect
import mx_graalpython_import
import mx_graalpython_python_benchmarks

# re-export custom mx project classes so they can be used from suite.py
from mx import MavenProject #pylint: disable=unused-import
from mx_cmake import CMakeNinjaProject #pylint: disable=unused-import

from mx_gate import Task
from mx_graalpython_bench_param import PATH_MESO, BENCHMARKS, WARMUP_BENCHMARKS, JBENCHMARKS, JAVA_DRIVER_BENCHMARKS
from mx_graalpython_benchmark import PythonBenchmarkSuite, python_vm_registry, CPythonVm, PyPyVm, JythonVm, \
    GraalPythonVm, \
    CONFIGURATION_DEFAULT, CONFIGURATION_SANDBOXED, CONFIGURATION_NATIVE, \
    CONFIGURATION_DEFAULT_MULTI, CONFIGURATION_SANDBOXED_MULTI, CONFIGURATION_NATIVE_MULTI, \
    CONFIGURATION_DEFAULT_MULTI_TIER, CONFIGURATION_NATIVE_MULTI_TIER, \
    PythonInteropBenchmarkSuite, PythonVmWarmupBenchmarkSuite, \
    CONFIGURATION_INTERPRETER, CONFIGURATION_INTERPRETER_MULTI, CONFIGURATION_NATIVE_INTERPRETER, \
    CONFIGURATION_NATIVE_INTERPRETER_MULTI, PythonJavaEmbeddingBenchmarkSuite, python_java_embedding_vm_registry, \
    GraalPythonJavaDriverVm, CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI_SHARED, \
    CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI, CONFIGURATION_JAVA_EMBEDDING_MULTI_SHARED, \
    CONFIGURATION_JAVA_EMBEDDING_MULTI, CONFIGURATION_PANAMA

if not sys.modules.get("__main__"):
    # workaround for pdb++
    sys.modules["__main__"] = type(sys)("<empty>")


def get_boolean_env(name, default=False):
    env = os.environ.get(name)
    if env is None:
        return default
    return env.lower() in ('true', '1')


SUITE = mx.suite('graalpython')
SUITE_COMPILER = mx.suite("compiler", fatalIfMissing=False)

GRAAL_VERSION = SUITE.suiteDict['version']
GRAAL_VERSION_MAJ_MIN = ".".join(GRAAL_VERSION.split(".")[:2])
PYTHON_VERSION = SUITE.suiteDict[f'{SUITE.name}:pythonVersion']
PYTHON_VERSION_MAJ_MIN = ".".join(PYTHON_VERSION.split('.')[:2])

# this environment variable is used by some of our maven projects and jbang integration to build against the unreleased master version during development
os.environ["GRAALPY_VERSION"] = GRAAL_VERSION

MAIN_BRANCH = 'master'
HPY_IMPORT_ORPHAN_BRANCH_NAME = "hpy-import"

GRAALPYTHON_MAIN_CLASS = "com.oracle.graal.python.shell.GraalPythonMain"

SANDBOXED_OPTIONS = ['--llvm.managed', '--llvm.deadPointerProtection=MASK', '--llvm.partialPointerConversion=false', '--python.PosixModuleBackend=java', '--python.Sha3ModuleBackend=java']

# Allows disabling rebuild for some mx commands such as graalpytest
DISABLE_REBUILD = get_boolean_env('GRAALPYTHON_MX_DISABLE_REBUILD')

_COLLECTING_COVERAGE = False

CI = get_boolean_env("CI")
WIN32 = sys.platform == "win32"
BUILD_NATIVE_IMAGE_WITH_ASSERTIONS = get_boolean_env('BUILD_WITH_ASSERTIONS', CI)

if CI and not os.environ.get("GRAALPYTEST_FAIL_FAST"):
    os.environ["GRAALPYTEST_FAIL_FAST"] = "true"


def is_collecting_coverage():
    return bool(mx_gate.get_jacoco_agent_args() or _COLLECTING_COVERAGE)


def wants_debug_build(flags=os.environ.get("CFLAGS", "")):
    return any(x in flags for x in ["-g", "-ggdb", "-ggdb3"])


if wants_debug_build():
    mx_native.DefaultNativeProject._original_cflags = mx_native.DefaultNativeProject.cflags
    mx_native.DefaultNativeProject.cflags = property(
        lambda self: self._original_cflags + (["/Z7"] if WIN32 else ["-fPIC", "-ggdb3"])
    )


if WIN32:
    # we need the .lib for pythonjni
    original_DefaultNativeProject_getArchivableResults = mx_native.DefaultNativeProject.getArchivableResults
    def getArchivableResultsWithLib(self, *args, **kwargs):
        for result in original_DefaultNativeProject_getArchivableResults(self, *args, **kwargs):
            if any(r.endswith("pythonjni.dll") for r in result):
                yield tuple(r.replace(".dll", ".lib") for r in result)
            yield result
    mx_native.DefaultNativeProject.getArchivableResults = getArchivableResultsWithLib

    # let's check if VS compilers are on the PATH
    if not os.environ.get("LIB"):
        mx.log("LIB not in environment, not a VS shell")
    elif not os.environ.get("INCLUDE"):
        mx.log("INCLUDE not in environment, not a VS shell")
    else:
        for p in os.environ.get("PATH", "").split(os.pathsep):
            if os.path.isfile(os.path.join(os.path.abspath(p), "cl.exe")):
                mx.log("LIB and INCLUDE set, cl.exe on PATH, assuming this is a VS shell")
                os.environ["DISTUTILS_USE_SDK"] = "1"
                break
        else:
            mx.log("cl.exe not on PATH, not a VS shell")


def _sibling(filename):
    return os.path.join(os.path.dirname(__file__), filename)


def _get_core_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-graalpython")


def _get_stdlib_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-python", "3")


def _get_capi_home(args=None):
    return os.path.join(mx.distribution("GRAALPYTHON_NATIVE_LIBS").get_output(), mx.get_os(), mx.get_arch())


_get_jni_home = _get_capi_home


def _extract_graalpython_internal_options(args):
    non_internal = []
    additional_dists = []
    for arg in args:
        # Class path extensions
        if arg.startswith('-add-dist='):
            additional_dists += [arg[10:]]
        else:
            non_internal += [arg]

    return non_internal, additional_dists


def extend_os_env(**kwargs):
    env = os.environ.copy()
    env.update(**kwargs)
    return env


def delete_bad_env_keys(env):
    for k in ["SSL_CERT_FILE", "REQUESTS_CA_BUNDLE"]:
        if k in env:
            del env[k]


def check_vm(vm_warning=True, must_be_jvmci=False):
    if not SUITE_COMPILER:
        if must_be_jvmci:
            mx.abort('** Error ** : graal compiler was not found!')
            sys.exit(1)

        if vm_warning:
            mx.log('** warning ** : graal compiler was not found!! Executing using standard VM..')


def get_jdk():
    return mx.get_jdk()


def full_python(args, env=None):
    """Run python from standalone build (unless kwargs are given). Does not build GraalPython sources automatically."""

    if not any(arg.startswith('--python.WithJavaStacktrace') for arg in args):
        args.insert(0, '--python.WithJavaStacktrace=1')

    if "--hosted" in args[:2]:
        args.remove("--hosted")
        return python(args)

    if '--vm.da' not in args:
        args.insert(0, '--vm.ea')

    if not any(arg.startswith('--experimental-options') for arg in args):
        args.insert(0, '--experimental-options')

    handle_debug_arg(args)

    for arg in itertools.chain(
            itertools.chain(*map(shlex.split, reversed(mx._opts.java_args_sfx))),
            reversed(shlex.split(mx._opts.java_args if mx._opts.java_args else "")),
            itertools.chain(*map(shlex.split, reversed(mx._opts.java_args_pfx))),
    ):
        if arg.startswith("-"):
            args.insert(0, f"--vm.{arg[1:]}")
        else:
            mx.warn(f"Dropping {arg}, cannot pass it to launcher")

    standalone_home = graalpy_standalone_home('jvm', dev=True, build=False)
    graalpy_path = os.path.join(standalone_home, 'bin', _graalpy_launcher())
    if not os.path.exists(graalpy_path):
        mx.abort("GraalPy standalone doesn't seem to be built.\n" +
                 "To build it: mx python-jvm\n" +
                 "Alternatively use: mx python --hosted")

    mx.run([graalpy_path] + args, env=env)


def handle_debug_arg(args):
    if mx._opts.java_dbg_port:
        args.insert(0,
                    f"--vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:{mx._opts.java_dbg_port}")


def python(args, **kwargs):
    """run a Python program or shell"""
    if not any(arg.startswith('--python.WithJavaStacktrace') for arg in args):
        args.insert(0, '--python.WithJavaStacktrace=1')

    do_run_python(args, **kwargs)


def do_run_python(args, extra_vm_args=None, env=None, jdk=None, extra_dists=None, cp_prefix=None, cp_suffix=None, main_class=GRAALPYTHON_MAIN_CLASS, minimal=False, **kwargs):
    experimental_opt_added = False
    if not any(arg.startswith("--python.CAPI") for arg in args):
        capi_home = _get_capi_home()
        args.insert(0, "--python.CAPI=%s" % capi_home)
        args.insert(0, "--experimental-options")
        experimental_opt_added = True

    if not any(arg.startswith("--python.JNIHome") for arg in args):
        args.insert(0, "--python.JNIHome=" + _get_jni_home())
        if not experimental_opt_added:
            args.insert(0, "--experimental-options")

    if not env:
        env = os.environ.copy()
    env.setdefault("GRAAL_PYTHONHOME", _dev_pythonhome())
    delete_bad_env_keys(env)

    check_vm_env = env.get('GRAALPYTHON_MUST_USE_GRAAL', False)
    if check_vm_env:
        if check_vm_env == '1':
            check_vm(must_be_jvmci=True)
        elif check_vm_env == '0':
            check_vm()

    if minimal:
        x = [x for x in SUITE.dists if x.name == "GRAALPYTHON"][0]
        dists = [dep for dep in x.deps if dep.isJavaProject() or dep.isJARDistribution()]
        # Hack: what we should just do is + ['GRAALPYTHON_VERSIONS_MAIN'] and let MX figure out
        # the class-path and other VM arguments necessary for it. However, due to a bug in MX,
        # LayoutDirDistribution causes an exception if passed to mx.get_runtime_jvm_args,
        # because it does not properly initialize its super class ClasspathDependency, see MX PR: 1665.
        ver_dep = mx.dependency('GRAALPYTHON_VERSIONS_MAIN').get_output()
        cp_prefix = ver_dep if cp_prefix is None else (ver_dep + os.pathsep + cp_prefix)
    else:
        dists = ['GRAALPYTHON']
    dists += ['TRUFFLE_NFI', 'SULONG_NATIVE', 'GRAALPYTHON-LAUNCHER']

    vm_args, graalpython_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    if minimal:
        vm_args.insert(0, f"-Dorg.graalvm.language.python.home={_dev_pythonhome()}")
    graalpython_args, additional_dists = _extract_graalpython_internal_options(graalpython_args)
    dists += additional_dists

    if extra_dists:
        dists += extra_dists

    if not CI:
        # Try eagerly to include tools for convenience when running Python
        if not mx.suite("tools", fatalIfMissing=False):
            SUITE.import_suite("tools", version=None, urlinfos=None, in_subdir=True)
        if mx.suite("tools", fatalIfMissing=False):
            for tool in ["CHROMEINSPECTOR", "TRUFFLE_COVERAGE"]:
                if os.path.exists(mx.suite("tools").dependency(tool).path):
                    dists.append(tool)

    graalpython_args.insert(0, '--experimental-options=true')

    vm_args += mx.get_runtime_jvm_args(dists, jdk=jdk, cp_prefix=cp_prefix, cp_suffix=cp_suffix)

    if not jdk:
        jdk = get_jdk()

    # default: assertion checking is enabled
    if extra_vm_args is None or '-da' not in extra_vm_args:
        vm_args += ['-ea', '-esa']

    if extra_vm_args:
        vm_args += extra_vm_args

    vm_args.append(main_class)
    return mx.run_java(vm_args + graalpython_args, jdk=jdk, env=env, **kwargs)


def node_footprint_analyzer(args, **kwargs):
    main_class = 'com.oracle.graal.python.test.advanced.NodeFootprintAnalyzer'
    vm_args = mx.get_runtime_jvm_args(['GRAALPYTHON_UNIT_TESTS', 'GRAALPYTHON', 'TRUFFLE_NFI', 'SULONG_NATIVE'])
    return mx.run_java(vm_args + [main_class] + args, **kwargs)


def _dev_pythonhome_context():
    home = os.environ.get("GRAAL_PYTHONHOME", _dev_pythonhome())
    return set_env(GRAAL_PYTHONHOME=home)


def _dev_pythonhome():
    return os.path.join(SUITE.dir, "graalpython")


def punittest(ars, report=False):
    """
    Runs GraalPython junit tests, TCK, and memory leak tests, which can be skipped using --no-leak-tests.
    Pass --regex to further filter the junit and TSK tests. GraalPy tests are always run in two configurations:
    with language home on filesystem and with language home served from the Truffle resources.
    """
    args = [] if ars is None else ars
    @dataclass
    class TestConfig:
        args: list
        useResources: bool
        reportConfig: bool = report
        def __str__(self):
            return f"args={self.args!r}, useResources={self.useResources}, report={self.reportConfig}"

    configs = []
    skip_leak_tests = False
    if "--no-leak-tests" in args:
        skip_leak_tests = True
        args.remove("--no-leak-tests")

    vm_args = ['-Dpolyglot.engine.WarnInterpreterOnly=false']

    # Note: we must use filters instead of --regex so that mx correctly processes the unit test configs,
    # but it is OK to apply --regex on top of the filters
    graalpy_tests = ['com.oracle.graal.python.test', 'com.oracle.graal.python.pegparser.test', 'org.graalvm.python.embedding.utils.test']
    configs += [
        TestConfig(vm_args + graalpy_tests + args, True),
        TestConfig(vm_args + graalpy_tests + args, False),
        # TCK suite is not compatible with the PythonMxUnittestConfig,
        # so it must have its own run and the useResources config is ignored
        TestConfig(vm_args + ['com.oracle.truffle.tck.tests'] + args, False),
    ]
    if '--regex' not in args:
        async_regex = ['--regex', r'com\.oracle\.graal\.python\.test\.integration\.advanced\.AsyncActionThreadingTest']
        configs.append(TestConfig(vm_args + ['-Dpython.AutomaticAsyncActions=false', 'com.oracle.graal.python.test', 'org.graalvm.python.embedding.utils.test'] + async_regex + args, True, False))

    for c in configs:
        mx.log(f"Python JUnit tests configuration: {c}")
        PythonMxUnittestConfig.useResources = c.useResources
        mx_unittest.unittest(c.args, test_report_tags=({"task": "punittest"} if c.reportConfig else None))

    if skip_leak_tests:
        return

    # test leaks with Python code only
    run_leak_launcher(["--code", "pass", ])
    run_leak_launcher(["--repeat-and-check-size", "250", "--null-stdout", "--code", "print('hello')"])
    # test leaks when some C module code is involved
    run_leak_launcher(["--code", 'import _testcapi, mmap, bz2; print(memoryview(b"").nbytes)'])
    # test leaks with shared engine Python code only
    run_leak_launcher(["--shared-engine", "--code", "pass"])
    run_leak_launcher(["--shared-engine", "--repeat-and-check-size", "250", "--null-stdout", "--code", "print('hello')"])
    # test leaks with shared engine when some C module code is involved
    run_leak_launcher(["--shared-engine", "--code", 'import _testcapi, mmap, bz2; print(memoryview(b"").nbytes)'])
    run_leak_launcher(["--shared-engine", "--code", '[10, 20]', "--python.UseNativePrimitiveStorageStrategy=true",
                       "--forbidden-class", "com.oracle.graal.python.runtime.sequence.storage.NativePrimitiveSequenceStorage",
                       "--forbidden-class", "com.oracle.graal.python.runtime.native_memory.NativePrimitiveReference"])
    run_leak_launcher(["--code", '[10, 20]', "--python.UseNativePrimitiveStorageStrategy=true",
                       "--forbidden-class", "com.oracle.graal.python.runtime.sequence.storage.NativePrimitiveSequenceStorage",
                       "--forbidden-class", "com.oracle.graal.python.runtime.native_memory.NativePrimitiveReference"])


PYTHON_ARCHIVES = ["GRAALPYTHON_GRAALVM_SUPPORT"]
PYTHON_NATIVE_PROJECTS = ["python-libbz2",
                          "python-liblzma",
                          "python-libzsupport",
                          "python-libposix",
                          "com.oracle.graal.python.hpy.llvm",
                          "com.oracle.graal.python.jni",
                          "com.oracle.graal.python.cext"]


def nativebuild(args):
    "Build the non-Java Python projects and archives"
    mx.build(["--dependencies", ",".join(PYTHON_NATIVE_PROJECTS + PYTHON_ARCHIVES)])


def nativeclean(args):
    "Clean the non-Java Python projects"
    mx.clean(["--dependencies", ",".join(PYTHON_NATIVE_PROJECTS + PYTHON_ARCHIVES)])


def python3_unittests(args):
    """run the cPython stdlib unittests"""
    mx.run([sys.executable, "graalpython/com.oracle.graal.python.test/src/python_unittests.py", "-v"] + args)


def compare_unittests(args):
    """compare the output of two runs of the cPython stdlib unittests"""
    mx.run([sys.executable, "graalpython/com.oracle.graal.python.test/src/compare_unittests.py", "-v"] + args)


def run_cpython_test(raw_args):
    test_args = ['-v']
    parser = ArgumentParser()
    parser.add_argument('--all', action='store_true')
    parser.add_argument('--svm', dest='standalone_type', action='store_const', const='native', default='jvm')
    parser.add_argument('-k', dest='tags', action='append')
    parser.add_argument('globs', nargs='+')
    args, rest_args = parser.parse_known_args(raw_args)

    testfiles = []
    for g in args.globs:
        testfiles += glob.glob(os.path.join(SUITE.dir, "graalpython/lib-python/3/test", f"{g}.py"))
        testfiles += glob.glob(os.path.join(SUITE.dir, "graalpython/lib-python/3/test", g, "__init__.py"))
    if not args.all:
        test_tags = get_test_tags(args.globs)
        if args.tags:
            user_tags = [tag if '*' in tag else f'*{tag}*' for tag in args.tags]
            test_tags = [tag for tag in test_tags if any(fnmatch.fnmatch(tag.replace('*', ''), user_tag) for user_tag in user_tags)]
        if not test_tags:
            sys.exit("No tags matched")
    else:
        test_tags = args.tags
    for tag in test_tags or ():
        test_args += ['-k', tag]

    python_args = [
        '--vm.ea',
        *rest_args,
        os.path.join(SUITE.dir, "graalpython/com.oracle.graal.python.test/src/tests/run_cpython_test.py"),
        *test_args,
        *testfiles,
    ]
    handle_debug_arg(python_args)
    standalone_home = graalpy_standalone_home(args.standalone_type, dev=True, build=False)
    graalpy_path = os.path.join(standalone_home, 'bin', _graalpy_launcher())
    if not os.path.exists(graalpy_path):
        mx.abort("GraalPy standalone is not built")
    env = os.environ.copy()
    delete_bad_env_keys(env)
    env['PYTHONPATH'] = os.path.join(_dev_pythonhome(), 'lib-python/3')
    mx.run([graalpy_path, *python_args], env=env)


def get_test_tags(globs):
    sys.path += ["graalpython/com.oracle.graal.python.test/src/tests", "graalpython/lib-python/3"]
    os.environ['ENABLE_CPYTHON_TAGGED_UNITTESTS'] = 'true'
    try:
        import test_tagged_unittests
        collected = []
        for name, tags in test_tagged_unittests.collect_working_tests():
            if any(fnmatch.fnmatch(name, g) for g in globs):
                collected += tags
        return collected
    finally:
        del os.environ['ENABLE_CPYTHON_TAGGED_UNITTESTS']


def retag_unittests(args):
    """run the cPython stdlib unittests"""
    parser = ArgumentParser('mx python-retag-unittests')
    parser.add_argument('--upload-results-to')
    parser.add_argument('--inspect', action='store_true')
    parser.add_argument('-debug-java', action='store_true')
    parser.add_argument('--jvm', action='store_true')
    parser.add_argument('--timeout')
    parsed_args, remaining_args = parser.parse_known_args(args)
    active_branch = mx.VC.get_vc(SUITE.dir).active_branch(SUITE.dir)
    if parsed_args.upload_results_to and active_branch != MAIN_BRANCH:
        mx.log("Skipping retagger when not on main branch")
        return
    env = extend_os_env(
        ENABLE_CPYTHON_TAGGED_UNITTESTS="true",
        PYTHONPATH=os.path.join(_dev_pythonhome(), 'lib-python/3'),
    )
    delete_bad_env_keys(env)
    args = [
        '--experimental-options=true',
        '--python.CatchAllExceptions=true',
    ]
    if parsed_args.inspect:
        args.append('--inspect')
    if parsed_args.debug_java:
        args.append('-debug-java')
    args += [
        'graalpython/com.oracle.graal.python.test/src/tests/test_tagged_unittests.py',
        '--retag'
    ]
    if parsed_args.timeout:
        args += [f'--timeout={parsed_args.timeout}']
    vm = python_svm() if not parsed_args.jvm else python_jvm()
    if parsed_args.jvm:
        args += ['--vm.ea']
    mx.run([vm] + args + remaining_args, env=env)
    if parsed_args.upload_results_to:
        with tempfile.TemporaryDirectory(prefix='graalpython-retagger-') as d:
            filename = os.path.join(d, 'unittest-tags-{}.tar.bz2'.format(sys.platform))
            mx.run(['tar', 'cJf', filename, 'graalpython/com.oracle.graal.python.test/src/tests/unittest_tags'])
            mx.run(['scp', filename, parsed_args.upload_results_to.rstrip('/') + '/'])


def _read_tags(path='.'):
    tags = set()
    tagfiles = glob.glob(os.path.join(path, 'graalpython/com.oracle.graal.python.test/src/tests/unittest_tags/*.txt'))
    for tagfile in tagfiles:
        with open(tagfile) as f:
            tags |= {(os.path.basename(tagfile), line.strip()) for line in f if line.strip()}
    return tags


def _write_tags(tags, path='.'):
    tagdir = os.path.join(path, 'graalpython/com.oracle.graal.python.test/src/tests/unittest_tags/')
    tagfiles = glob.glob(os.path.join(path, 'graalpython/com.oracle.graal.python.test/src/tests/unittest_tags/*.txt'))
    for file in tagfiles:
        os.unlink(file)
    for file, stags in itertools.groupby(sorted(tags), key=lambda x: x[0]):
        with open(os.path.join(tagdir, file), 'w') as f:
            for tag in stags:
                f.write(tag[1] + '\n')


def _fetch_tags_for_platform(parsed_args, platform):
    oldpwd = os.getcwd()
    with tempfile.TemporaryDirectory(prefix='graalpython-update-tags-') as d:
        os.chdir(d)
        try:
            tarfile = 'unittest-tags-{}.tar.bz2'.format(platform)
            url = '{}/{}'.format(parsed_args.tags_directory_url.rstrip('/'), tarfile)
            print(mx.colorize('Download from %s' % (url), color='magenta', bright=True, stream=sys.stdout))
            mx.run(['curl', '-O', url])
            out = mx.OutputCapture()
            mx.run(['file', tarfile], out=out)
            if 'HTML' in out.data:
                if not mx.ask_yes_no('Download failed! please download %s manually to %s and type (y) '
                                     'to continue.' % (url, d), default='y'):
                    sys.exit(1)
            os.mkdir(platform)
            mx.run(['tar', 'xf', tarfile, '-C', platform])
            return _read_tags(platform)
        finally:
            os.chdir(oldpwd)


def update_unittest_tags(args):
    parser = ArgumentParser('mx python-update-unittest-tags')
    parser.add_argument('tags_directory_url')
    parser.add_argument('--untag', action='store_true', help="Allow untagging existing tests")
    parsed_args = parser.parse_args(args)

    current_tags = _read_tags()
    linux_tags = _fetch_tags_for_platform(parsed_args, 'linux')
    darwin_tags = _fetch_tags_for_platform(parsed_args, 'darwin')

    tag_exclusions = [
        # Tests for bytecode optimizations. We don't have the same bytecode, ignore the whole suite
        'graalpython.lib-python.3.test.test_peepholer.*',
        # This test times out in the gate even though it succeeds locally and in the retagger. Race condition?
        'graalpython.lib-python.3.test.test_cprofile.CProfileTest.test_run_profile_as_module',
        # The following two try to read bytecode and fail randomly as our co_code is changing
        'graalpython.lib-python.3.test.test_modulefinder.ModuleFinderTest.test_bytecode',
        'graalpython.lib-python.3.test.test_modulefinder.ModuleFinderTest.test_relative_imports_4',
        # Temporarily disabled due to object identity or race condition (GR-24863)
        'graalpython.lib-python.3.test.test_weakref.MappingTestCase.test_threaded_weak_key_dict_deepcopy',
        # Temporarily disabled due to transient failures (GR-30641)
        'graalpython.lib-python.3.test.test_import.__init__.ImportTests.test_concurrency',
        # Disabled since this fails on Darwin when run in parallel with many other tests
        'graalpython.lib-python.3.test.test_imaplib.NewIMAPSSLTests.test_login_cram_md5_bytes',
        'graalpython.lib-python.3.test.test_imaplib.NewIMAPSSLTests.test_login_cram_md5_plain_text',
        'graalpython.lib-python.3.test.test_imaplib.NewIMAPSSLTests.test_valid_authentication_plain_text',
        'graalpython.lib-python.3.test.test_imaplib.NewIMAPTests.test_login_cram_md5_bytes',
        'graalpython.lib-python.3.test.test_imaplib.NewIMAPTests.test_login_cram_md5_plain_text',
        'graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_noop',
        'graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_pass_',
        'graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_apop_normal',
        'graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_capa',
        'graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_dele',
        # Disabled because these tests hang on Darwin
        'graalpython.lib-python.3.test.test_logging.ConfigDictTest.test_listen_config_1_ok',
        'graalpython.lib-python.3.test.test_logging.ConfigDictTest.test_listen_config_10_ok',
        'graalpython.lib-python.3.test.test_logging.ConfigDictTest.test_listen_verify',
        # GC-related transients
        'graalpython.lib-python.3.test.test_weakref.MappingTestCase.test_weak_keyed_len_cycles',
        'graalpython.lib-python.3.test.test_weakref.WeakMethodTestCase.test_callback_when_method_dead',
        'graalpython.lib-python.3.test.test_weakref.WeakMethodTestCase.test_callback_when_object_dead',
        'graalpython.lib-python.3.test.test_weakref.FinalizeTestCase.test_all_freed',
        'graalpython.lib-python.3.test.test_weakref.ReferencesTestCase.test_equality',
        'graalpython.lib-python.3.test.test_copy.TestCopy.test_copy_weakkeydict',
        'graalpython.lib-python.3.test.test_copy.TestCopy.test_deepcopy_weakkeydict',
        'graalpython.lib-python.3.test.test_deque.TestBasic.test_container_iterator',
        'graalpython.lib-python.3.test.test_mmap.MmapTests.test_weakref',
        'graalpython.lib-python.3.test.test_ast.AST_Tests.test_AST_garbage_collection',
        'graalpython.lib-python.3.test.test_module.ModuleTests.test_weakref',
        # Disabled since code object comparison is not stable for us
        'graalpython.lib-python.3.test.test_marshal.InstancingTestCase.testModule',
        'graalpython.lib-python.3.test.test_marshal.CodeTestCase.test_code',
        # Disabled since signaling isn't stable during parallel tests
        'graalpython.lib-python.3.test.test_faulthandler.FaultHandlerTests.test_sigbus',
        'graalpython.lib-python.3.test.test_faulthandler.FaultHandlerTests.test_sigill',
        # Disabled due to transient failure
        'graalpython.lib-python.3.test.test_multiprocessing_main_handling.SpawnCmdLineTest.*',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.TestNoForkBomb.test_noforkbomb',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestProcess.test_active_children',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestProcess.test_error_on_stdio_flush_1',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestProcess.test_parent_process',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithThreadsTestProcess.test_error_on_stdio_flush_1',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithThreadsTestProcess.test_error_on_stdio_flush_2',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy._TestImportStar.test_import',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestBarrier.test_default_timeout',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestBarrier.test_timeout',
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestLogging.*',
        'graalpython.lib-python.3.test.test_pty.PtyTest.test_openpty',
        # Disabled due to transient stack overflow that fails to get caught and crashes the VM
        'graalpython.lib-python.3.test.test_exceptions.ExceptionTests.test_badisinstance',
        'graalpython.lib-python.3.test.test_exceptions.ExceptionTests.testInfiniteRecursion',
        'graalpython.lib-python.3.test.test_list.ListTest.test_repr_deep',
        'graalpython.lib-python.3.test.test_functools.TestPartialC.test_recursive_pickle',
        'graalpython.lib-python.3.test.test_functools.TestPartialCSubclass.test_recursive_pickle',
        'graalpython.lib-python.3.test.test_functools.TestPartialPy.test_recursive_pickle',
        'graalpython.lib-python.3.test.test_functools.TestPartialPySubclass.test_recursive_pickle',
        'graalpython.lib-python.3.test.test_plistlib.TestBinaryPlistlib.test_deep_nesting',
        # Transient, GR-41056
        'graalpython.lib-python.3.test.test_subprocess.POSIXProcessTestCase.test_swap_std_fds_with_one_closed',
        # Transient, at least on M1
        'ctypes.test.test_python_api.PythonAPITestCase.test_PyOS_snprintf',
        # Transient hash mismatch
        'lib2to3.tests.test_parser.TestPgen2Caching.test_load_grammar_from_subprocess',
        # Connects to internet, sometimes can't reach
        'graalpython.lib-python.3.test.test_ssl.NetworkedTests.test_timeout_connect_ex',
        # Transiently fails because it's dependent on timings
        'graalpython.lib-python.3.test.test_int.IntStrDigitLimitsTests.test_denial_of_service_prevented_int_to_str',
        'graalpython.lib-python.3.test.test_int.IntSubclassStrDigitLimitsTests.test_denial_of_service_prevented_int_to_str',
        # The whole suite transiently times out (GR-47822)
        'graalpython.lib-python.3.test.test_docxmlrpc.*',
        # The whole suite transiently times out (GR-47822)
        'graalpython.lib-python.3.test.test_xmlrpc.*',
        # The whole suite transiently times out (GR-47822)
        'graalpython.lib-python.3.test.test_httpservers.*',
        # Disabled because of fatal error in Sulong (GR-47592)
        'graalpython.lib-python.3.test.test_compileall.CommandLineTestsNoSourceEpoch.test_workers*',
        'graalpython.lib-python.3.test.test_compileall.CommandLineTestsWithSourceEpoch.test_workers*',
        # GR-48555 race condition when exitting right after join
        'graalpython.lib-python.3.test.test_threading.ThreadingExceptionTests.test_print_exception*',
        # GC-related transients
        'test.test_importlib.test_locks.*_LifetimeTests.test_all_locks',
        # Flaky buffer capi tests
        'graalpython.lib-python.3.test.test_buffer.TestBufferProtocol.test_ndarray_slice_assign_multidim',
        # Too unreliable in the CI
        'graalpython.lib-python.3.test.test_multiprocessing_graalpy.WithProcessesTestProcess.test_many_processes',
        'test.test_multiprocessing_spawn.test_processes.WithProcessesTestProcess.test_many_processes',
        # Transiently ends up with 2 processes
        'test.test_concurrent_futures.test_process_pool.ProcessPoolSpawnProcessPoolExecutorTest.test_idle_process_reuse_one',
        'test.test_concurrent_futures.test_process_pool.ProcessPoolSpawnProcessPoolExecutorTest.test_killed_child',
        'test.test_concurrent_futures.test_thread_pool.ThreadPoolExecutorTest.test_idle_thread_reuse',
        'graalpython.lib-python.3.test.test_threading.ThreadTests.test_join_nondaemon_on_shutdown',
        'graalpython.lib-python.3.test.test_threading.ThreadTests.test_import_from_another_thread',
        'graalpython.lib-python.3.test.test_threading.ThreadTests.test_finalization_shutdown',
        # Transiently times out GR-52666
        'test.test_concurrent_futures.test_shutdown.ProcessPoolSpawnProcessPoolShutdownTest.test_submit_after_interpreter_shutdown',
        'test.test_concurrent_futures.test_shutdown.ProcessPoolSpawnProcessPoolShutdownTest.test_del_shutdown',
        # Transient lists differ error GR-49936
        'graalpython.lib-python.3.test.test_buffer.TestBufferProtocol.test_ndarray_index_getitem_multidim',
        'graalpython.lib-python.3.test.test_buffer.TestBufferProtocol.test_ndarray_slice_redundant_suboffsets',
        'graalpython.lib-python.3.test.test_buffer.TestBufferProtocol.test_ndarray_slice_multidim',
        # Transient failure to delete semaphore on process death
        'test.test_multiprocessing_spawn.test_misc.TestResourceTracker.test_resource_tracker_sigkill',
        # Connecting to external page that sometimes times out
        'graalpython.lib-python.3.test.test_urllib2net.OtherNetworkTests.test_ftp',
    ]

    result_tags = linux_tags & darwin_tags
    result_tags = {
        (file, tag) for file, tag in result_tags
        if not any(fnmatch.fnmatch(tag.lstrip('*'), exclusion) for exclusion in tag_exclusions)
    }

    if not parsed_args.untag:
        result_tags |= current_tags
    _write_tags(result_tags)

    diff = linux_tags - darwin_tags
    if diff:
        mx.warn("The following tests work only on Linux:\n" + '\n'.join(x[1] for x in diff))

    diff = darwin_tags - linux_tags
    if diff:
        mx.warn("The following tests work only on Darwin:\n" + '\n'.join(x[1] for x in diff))

    diff = current_tags - result_tags
    if diff:
        mx.warn("Potential regressions:\n" + '\n'.join(x[1] for x in diff))


AOT_INCOMPATIBLE_TESTS = ["test_interop.py", "test_jarray.py", "test_ssl_java_integration.py"]
# These test would work on JVM too, but they are prohibitively slow due to a large amount of subprocesses
AOT_ONLY_TESTS = ["test_patched_pip.py", "test_multiprocessing_spawn.py"]

GINSTALL_GATE_PACKAGES = {
    "numpy": "numpy",
    "scipy": "scipy",
    "scikit_learn": "sklearn",
}


class GraalPythonTags(object):
    junit = 'python-junit'
    junit_maven = 'python-junit-maven'
    unittest = 'python-unittest'
    unittest_cpython = 'python-unittest-cpython'
    unittest_sandboxed = 'python-unittest-sandboxed'
    unittest_multi = 'python-unittest-multi-context'
    unittest_jython = 'python-unittest-jython'
    unittest_arrow = 'python-unittest-arrow-storage'
    unittest_hpy = 'python-unittest-hpy'
    unittest_hpy_sandboxed = 'python-unittest-hpy-sandboxed'
    unittest_posix = 'python-unittest-posix'
    unittest_standalone = 'python-unittest-standalone'
    ginstall = 'python-ginstall'
    tagged = 'python-tagged-unittest'
    tagged_sandboxed = 'python-tagged-unittest-sandboxed'
    svmunit = 'python-svm-unittest'
    svmunit_sandboxed = 'python-svm-unittest-sandboxed'
    graalvm = 'python-graalvm'
    embedding = 'python-standalone-embedding'
    graalvm_sandboxed = 'python-graalvm-sandboxed'
    svm = 'python-svm'
    native_image_embedder = 'python-native-image-embedder'
    license = 'python-license'
    windows = 'python-windows-smoketests'
    language_checker = 'python-language-checker'
    exclusions_checker = 'python-class-exclusion-checker'


def python_gate(args):
    if not os.environ.get("JDT"):
        os.environ["JDT"] = "builtin"
    if not os.environ.get("ECLIPSE_EXE"):
        find_eclipse()
    if "--tags" not in args:
        args += ["--tags"]
        tags = ["style"]
        include_sandboxed = mx.suite("sulong-managed", fatalIfMissing=False) is not None
        for x in dir(GraalPythonTags):
            v = getattr(GraalPythonTags, x)
            if isinstance(v, str) and v.startswith("python-"):
                if include_sandboxed and "sandboxed" in v:
                    tags.append(v)
                elif not include_sandboxed and "sandboxed" not in v:
                    tags.append(v)
        args.append(",".join(tags))
    mx.log("Running mx python-gate " + " ".join(args))
    return mx.command_function("gate")(args)


python_gate.__doc__ = 'Custom gates are %s' % ", ".join([
    getattr(GraalPythonTags, t) for t in dir(GraalPythonTags) if not t.startswith("__")
])


def find_eclipse():
    pardir = os.path.abspath(os.path.join(SUITE.dir, ".."))
    for f in [os.path.join(SUITE.dir, f)
              for f in os.listdir(SUITE.dir)] + [os.path.join(pardir, f)
                                                 for f in os.listdir(pardir)]:
        if os.path.basename(f) == "eclipse" and os.path.isdir(f):
            mx.log("Automatically choosing %s for Eclipse" % f)
            eclipse_exe = os.path.join(f, "eclipse")
            if os.path.exists(eclipse_exe):
                os.environ["ECLIPSE_EXE"] = eclipse_exe
                return


@contextlib.contextmanager
def set_env(**environ):
    """Temporarily set the process environment variables"""
    old_environ = dict(os.environ)
    for k, v in environ.items():
        if v is None:
            if k in os.environ:
                del os.environ[k]
        else:
            os.environ[k] = v
    try:
        yield
    finally:
        os.environ.clear()
        os.environ.update(old_environ)


def _graalpy_launcher(managed=False):
    name = 'graalpy-managed' if managed else 'graalpy'
    return f"{name}.exe" if WIN32 else name


def graalpy_standalone_home(standalone_type, enterprise=False, dev=False, build=True):
    assert standalone_type in ['native', 'jvm']
    assert not (enterprise and dev), "EE dev standalones are not implemented yet"
    jdk_version = mx.get_jdk().version

    # Check if GRAALPY_HOME points to some compatible pre-built GraalPy standalone
    python_home = os.environ.get("GRAALPY_HOME", None)
    if python_home and "*" in python_home:
        python_home = os.path.abspath(glob.glob(python_home)[0])
        mx.log("Using GraalPy standalone from GRAALPY_HOME: " + python_home)
        # Try to verify that we're getting what we expect:
        has_java = os.path.exists(os.path.join(python_home, 'jvm', 'bin', 'java'))
        if has_java != (standalone_type == 'jvm'):
            mx.abort(f"GRAALPY_HOME is not compatible with the requested distribution type.\n"
                     f"jvm/bin/java exists?: {has_java}, requested type={standalone_type}.")

        line = ''
        with open(os.path.join(python_home, 'release'), 'r') as f:
            while 'JAVA_VERSION=' not in line:
                line = f.readline()
        if 'JAVA_VERSION=' not in line:
            mx.abort(f"GRAALPY_HOME does not contain 'release' file. Cannot check Java version.")
        actual_jdk_version = mx.VersionSpec(line.strip('JAVA_VERSION=').strip(' "\n\r'))
        if actual_jdk_version != jdk_version:
            mx.abort(f"GRAALPY_HOME is not compatible with the requested JDK version.\n"
                     f"actual version: '{actual_jdk_version}', version string: {line}, requested version: {jdk_version}.")

        launcher = os.path.join(python_home, 'bin', _graalpy_launcher(enterprise))
        out = mx.OutputCapture()
        import_managed_status = mx.run([launcher, "-c", "import sys; assert 'Oracle GraalVM' in sys.version"], nonZeroIsFatal=False, out=out, err=out)
        if enterprise != (import_managed_status == 0):
            mx.abort(f"GRAALPY_HOME is not compatible with requested distribution kind ({import_managed_status=}, {enterprise=}, {out=}).")
        return python_home

    env_file = 'ce-python'
    vm_suite_path = os.path.join(mx.suite('truffle').dir, '..', 'vm')
    svm_component = '_SVM'
    if enterprise:
        env_file = 'ee-python'
        vm_suite_path = os.path.join(mx.suite('graal-enterprise').dir, '..', 'vm-enterprise')
        svm_component = '_SVM_SVMEE'
    if dev:
        if standalone_type == 'jvm':
            svm_component = ''
            mx_args = ['--dy', '/vm']
        else:
            dev_env_file = os.path.join(os.path.abspath(SUITE.dir), 'mx.graalpython/graalpython-svm-standalone')
            mx_args = ['-p', vm_suite_path, '--env', dev_env_file]
    else:
        mx_args = ['-p', vm_suite_path, '--env', env_file]

    mx_args.append("--extra-image-builder-argument=-g")

    if BUILD_NATIVE_IMAGE_WITH_ASSERTIONS:
        mx_args.append("--extra-image-builder-argument=-ea")

    if mx_gate.get_jacoco_agent_args() or (build and not DISABLE_REBUILD):
        dep_type = 'JAVA' if standalone_type == 'jvm' else 'NATIVE'
        # Example of a string we're building here: PYTHON_JAVA_STANDALONE_SVM_SVMEE_JAVA21
        mx.run_mx(mx_args + ["build", "--dep", f"PYTHON_{dep_type}_STANDALONE{svm_component}_JAVA{jdk_version.parts[0]}"])

    out = mx.OutputCapture()
    # note: 'quiet=True' is important otherwise if the outer MX runs verbose,
    # this might fail because of additional output
    mx.run_mx(mx_args + ["standalone-home", "--type", standalone_type, "python"], out=out, quiet=True)
    python_home = out.data.splitlines()[-1].strip()
    if dev and standalone_type == 'native':
        path = Path(python_home)
        debuginfo = path / '../../libpythonvm.so.image/libpythonvm.so.debug'
        if debuginfo.exists():
            shutil.copy(debuginfo, path / 'lib')
    return python_home


def graalpy_standalone(standalone_type, managed=False, enterprise=None, dev=False, build=True):
    assert standalone_type in ['native', 'jvm']
    ee = managed if not enterprise else enterprise
    if standalone_type == 'native' and mx_gate.get_jacoco_agent_args():
        return graalpy_standalone('jvm', managed=managed, enterprise=enterprise, dev=dev, build=build)

    home = graalpy_standalone_home(standalone_type, enterprise=ee, dev=dev, build=build)
    launcher = os.path.join(home, 'bin', _graalpy_launcher(managed))
    return make_coverage_launcher_if_needed(launcher)

def graalpy_standalone_jvm():
    return graalpy_standalone('jvm')


def graalpy_standalone_native():
    return graalpy_standalone('native')


def graalpy_standalone_jvm_managed():
    return graalpy_standalone('jvm', managed=True)


def graalpy_standalone_jvm_enterprise():
    return os.path.join(graalpy_standalone_home('jvm', enterprise=True), 'bin', _graalpy_launcher(managed=False))


def graalpy_standalone_native_managed():
    return graalpy_standalone('native', managed=True)


def graalvm_jdk():
    jdk_major_version = mx.get_jdk().version.parts[0]
    mx_args = ['-p', os.path.join(mx.suite('truffle').dir, '..', 'vm'), '--env', 'ce']
    if not DISABLE_REBUILD:
        mx.run_mx(mx_args + ["build", "--dep", f"GRAALVM_COMMUNITY_JAVA{jdk_major_version}"])
    out = mx.OutputCapture()
    mx.run_mx(mx_args + ["graalvm-home"], out=out)
    return out.data.splitlines()[-1].strip()

def get_maven_cache():
    buildnr = os.environ.get('BUILD_NUMBER')
    # don't worry about maven.repo.local if not running on gate
    return os.path.join(SUITE.get_mx_output_dir(), 'm2_cache_' + buildnr) if buildnr else None

def deploy_local_maven_repo():
    env = os.environ.copy()
    m2_cache = get_maven_cache()
    if m2_cache:
        mvn_repo_local = f'-Dmaven.repo.local={m2_cache}'
        maven_opts = env.get('MAVEN_OPTS')
        maven_opts = maven_opts + " " + mvn_repo_local if maven_opts else mvn_repo_local
        env['MAVEN_OPTS'] = maven_opts
        mx.log(f'Added {mvn_repo_local} to MAVEN_OPTS={maven_opts}')

    if not DISABLE_REBUILD:
        # build GraalPy and all the necessary dependencies, so that we can deploy them
        mx.run_mx(["build"], env=env)

    # deploy maven artifacts
    version = GRAAL_VERSION
    path = os.path.join(SUITE.get_mx_output_dir(), 'public-maven-repo')
    licenses = ['EPL-2.0', 'PSF-License', 'GPLv2-CPE', 'ICU,GPLv2', 'BSD-simplified', 'BSD-new', 'UPL', 'MIT']
    deploy_args = [
        '--tags=public',
        '--all-suites',
        '--all-distribution-types',
        f'--version-string={version}',
        '--validate=none',
        '--licenses', ','.join(licenses),
        '--suppress-javadoc',
        'local',
        pathlib.Path(path).as_uri(),
    ]

    if not DISABLE_REBUILD:
        mx.rmtree(path, ignore_errors=True)
        os.mkdir(path)
        if m2_cache:
            with set_env(MAVEN_OPTS = maven_opts):
                mx.maven_deploy(deploy_args)
        else:
            mx.maven_deploy(deploy_args)
    return path, version, env


def python_jvm(_=None):
    """Returns the path to GraalPy from 'jvm' standalone dev build. Also builds the standalone."""
    launcher = graalpy_standalone('jvm', dev=True)
    mx.log(launcher)
    return launcher


def python_gvm(_=None):
    """Deprecated, use python-jvm"""
    mx.warn("mx python-gvm and the helper function python_gvm are deprecated, use python-jvm/python_jvm")
    return python_jvm()


def make_coverage_launcher_if_needed(launcher):
    if mx_gate.get_jacoco_agent_args():
        # patch our launchers created under jacoco to also run with jacoco.
        # do not use is_collecting_coverage() here, we only want to patch when
        # jacoco agent is requested.
        def graalvm_vm_arg(java_arg):
            if java_arg.startswith("@") and os.path.exists(java_arg[1:]):
                with open(java_arg[1:], "r") as f:
                    java_arg = f.read()
            assert java_arg[0] == "-", java_arg
            return shlex.quote(f'--vm.{java_arg[1:]}')

        agent_args = ' '.join(graalvm_vm_arg(arg) for arg in mx_gate.get_jacoco_agent_args() or [])

        # We need to make sure the arguments get passed to subprocesses, so we create a temporary launcher
        # with the arguments. We also disable compilation, it hardly helps for this use case
        original_launcher = os.path.abspath(os.path.realpath(launcher))
        bash_launcher = f'{original_launcher}.sh'
        with open(bash_launcher, "w") as f:
            f.write("#!/bin/sh\n")
            exe_arg = shlex.quote(f"--python.Executable={bash_launcher}")
            f.write(f'{original_launcher} --jvm {exe_arg} {agent_args} "$@"\n')
        os.chmod(bash_launcher, 0o775)
        mx.log(f"Replaced {launcher} with {bash_launcher} to collect coverage")
        launcher = bash_launcher
    return launcher


def python_svm(_=None):
    """Returns the path to GraalPy native image from 'native' standalone dev build.
    Also builds the standalone if not built already."""
    if mx_gate.get_jacoco_agent_args():
        return python_jvm()
    launcher = graalpy_standalone('native', dev=True)
    mx.log(launcher)
    return launcher


def native_image(args):
    mx.run_mx([
        "-p", os.path.join(mx.suite("truffle").dir, "..", "substratevm"),
        "--dy", "graalpython",
        "--native-images=",
        "build",
    ])
    mx.run_mx([
        "-p", os.path.join(mx.suite("truffle").dir, "..", "substratevm"),
        "--dy", "graalpython",
        "--native-images=",
        "native-image",
        *args
    ])


def _graalpytest_driver():
    return os.path.join(SUITE.dir, "graalpython", "com.oracle.graal.python.test", "src", "graalpytest.py")


def _graalpytest_root():
    return os.path.join(mx.dependency("com.oracle.graal.python.test").get_output_root(), "bin", "tests")


# name of the project containing the HPy tests
HPY_TEST_PROJECT = "com.oracle.graal.python.hpy.test"

def _hpy_test_root():
    return os.path.join(mx.dependency(HPY_TEST_PROJECT).get_output_root(), "bin", "hpytest")


def graalpytest(args):
    parser = ArgumentParser(prog='mx graalpytest')
    parser.add_argument('--python', type=str, action='store', default="", help='Run tests with custom Python binary.')
    parser.add_argument('-v', "--verbose", action="store_true", help='Verbose output.', default=True)
    parser.add_argument('-k', dest="filter", default='', help='Test pattern.')
    parser.add_argument('test', nargs="*", default=[], help='Test file to run (specify absolute or relative; e.g. "/path/to/test_file.py" or "cpyext/test_object.py") ')
    args, unknown_args = parser.parse_known_args(args)

    # ensure that the test distribution is up-to-date
    if not DISABLE_REBUILD:
        mx.command_function("build")(["--only", "com.oracle.graal.python.test"])

    testfiles = _list_graalpython_unittests(args.test)
    cmd_args = []
    # if we got a binary path it's most likely CPython, so don't add graalpython args
    if not args.python:
        cmd_args += ["--experimental-options=true", "--python.EnableDebuggingBuiltins"]
    elif 'graalpy' in os.path.basename(args.python):
        gp_args = ["--vm.ea", "--vm.esa", "--experimental-options=true", "--python.EnableDebuggingBuiltins"]
        mx.log(f"Executable seems to be GraalPy, prepending arguments: {gp_args}")
        cmd_args += gp_args
    # we assume that unknown args are polyglot arguments and just prepend them to the test driver
    cmd_args += unknown_args + [_graalpytest_driver()]
    if args.verbose:
        cmd_args += ["-v"]
    cmd_args += testfiles
    if args.filter:
        cmd_args += ["-k", args.filter]
    env = extend_os_env(PYTHONHASHSEED='0')
    delete_bad_env_keys(env)
    if args.python:
        return mx.run([args.python] + cmd_args, nonZeroIsFatal=True, env=env)
    else:
        return full_python(cmd_args, env=env)


def _list_graalpython_unittests(paths=None, exclude=None):
    exclude = [] if exclude is None else exclude
    paths = paths or [_graalpytest_root()]
    def is_included(path):
        if path.endswith(".py"):
            path = path.replace("\\", "/")
            basename = os.path.basename(path)
            return (
                basename.startswith("test_")
                and basename not in exclude
                and not any(fnmatch.fnmatch(path, pat) for pat in exclude)
            )
        return False

    testfiles = []
    for path in paths:
        if not os.path.exists(path):
            # allow paths relative to the test root
            path = os.path.join(_graalpytest_root(), path)
        if os.path.isfile(path):
            testfiles.append(path)
        else:
            for testfile in glob.glob(os.path.join(path, "**/test_*.py")):
                if is_included(testfile):
                    testfiles.append(testfile.replace("\\", "/"))
            for testfile in glob.glob(os.path.join(path, "test_*.py")):
                if is_included(testfile):
                    testfiles.append(testfile.replace("\\", "/"))
    return testfiles


def run_python_unittests(python_binary, args=None, paths=None, aot_compatible=False, exclude=None, env=None,
                         use_pytest=False, cwd=None, lock=None, out=None, err=None, nonZeroIsFatal=True, timeout=None, report=False):
    if lock:
        lock.acquire()
    # ensure that the test distribution is up-to-date
    if not DISABLE_REBUILD:
        mx.command_function("build")(["--dep", "com.oracle.graal.python.test"])

    args = args or []
    args = [
        "--vm.ea",
        "--experimental-options=true",
        "--python.EnableDebuggingBuiltins",
        "--python.CatchAllExceptions=true",
        *args,
    ]
    exclude = exclude or []
    if env is None:
        env = os.environ.copy()
    env['PYTHONHASHSEED'] = '0'
    delete_bad_env_keys(env)

    if mx.primary_suite() != SUITE:
        env.setdefault("GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS", "true")

    # list of excluded tests
    if aot_compatible:
        exclude += AOT_INCOMPATIBLE_TESTS
    else:
        exclude += AOT_ONLY_TESTS

    # just to be able to verify, print C ext mode (also works for CPython)
    mx.run([python_binary,
            "-c",
            "import sys; print('C EXT MODE: ' + (__graalpython__.get_platform_id() if sys.implementation.name == 'graalpy' else 'cpython'))"],
            nonZeroIsFatal=True, env=env, out=out, err=err)

    # list all 1st-level tests and exclude the SVM-incompatible ones
    testfiles = _list_graalpython_unittests(paths, exclude)
    if use_pytest:
        args += ["-m", "pytest", "-v", "--assert=plain", "--tb=native"]
    else:
        args += [_graalpytest_driver(), "-v"]

    result = 0
    if is_collecting_coverage():
        env['ENABLE_THREADED_GRAALPYTEST'] = "false"
        if mx_gate.get_jacoco_agent_args():
            with open(python_binary, "r") as f:
                assert f.read(9) == "#!/bin/sh"

        # jacoco only dumps the data on exit, and when we run all our unittests
        # at once it generates so much data we run out of heap space
        for testfile in testfiles:
            mx.run([python_binary] + args + [testfile], nonZeroIsFatal=nonZeroIsFatal, env=env, cwd=cwd, out=out, err=err, timeout=timeout)
    else:
        if WIN32:
            size = 5
            # Windows has problems with long commandlines and with file locks
            # when running multiple cpyext tests in the same process
            pytests = [t for t in testfiles if "cpyext" not in t]
            testfiles = [pytests[i:i + size] for i in range(0, len(pytests), size)] + [[t] for t in testfiles if "cpyext" in t]
        else:
            testfiles = [testfiles]

        for testset in testfiles:
            next_args = args[:]
            if report:
                reportfile = None
                t0 = time.time()
                if not use_pytest:
                    reportfile = os.path.abspath(tempfile.mktemp(prefix="test-report-", suffix=".json"))
                    next_args += ["--report", reportfile]

            next_args += testset
            mx.logv(" ".join([python_binary] + next_args))
            if lock:
                lock.release()
            result = result or mx.run([python_binary] + next_args, nonZeroIsFatal=nonZeroIsFatal, env=env, cwd=cwd, out=out, err=err, timeout=timeout)
            if lock:
                lock.acquire()

            if report:
                if reportfile:
                    mx_gate.make_test_report(reportfile, report.title)
                else:
                    mx_gate.make_test_report([{
                        "name": report.title,
                        "status": "PASSED" if result == 0 else "FAILED",
                        "duration": int((time.time() - t0) * 1000)
                    }], report.title)
        if lock:
            lock.release()
    return result


def run_hpy_unittests(python_binary, args=None, include_native=True, env=None, nonZeroIsFatal=True, timeout=None, report=False):
    args = [] if args is None else args
    mx.command_function("build")(["--dep", HPY_TEST_PROJECT])
    with tempfile.TemporaryDirectory(prefix='hpy-test-site-') as d:
        env = env or os.environ.copy()
        prefix = str(d)
        env.update(PYTHONUSERBASE=prefix)
        delete_bad_env_keys(env)
        mx.run_mx(["build", "--dependencies", "LLVM_TOOLCHAIN"])
        env.update(LLVM_TOOLCHAIN_VANILLA=mx_subst.path_substitutions.substitute('<path:LLVM_TOOLCHAIN>/bin'))
        mx.log("LLVM Toolchain (vanilla): {!s}".format(env["LLVM_TOOLCHAIN_VANILLA"]))
        mx.run([python_binary] + args + ["-m", "ensurepip", "--user"],
               nonZeroIsFatal=nonZeroIsFatal, env=env, timeout=timeout)
        mx.run([python_binary] + args + ["-m", "pip", "install", "--user", "pytest<=6.2.3", "pytest-xdist", "filelock"],
               nonZeroIsFatal=nonZeroIsFatal, env=env, timeout=timeout)
        if not is_collecting_coverage():
            global DISABLE_REBUILD
            DISABLE_REBUILD = True
            # parallelize
            import threading
            threads = []
            lock = threading.RLock()

            class HPyUnitTestsThread(threading.Thread):
                def __init__(self, **tkwargs):
                    super().__init__(**tkwargs)
                    self.out = mx.LinesOutputCapture()
                    self.result = None

                def run(self):
                    # Note: for some reason catching BaseException is not enough to catch mx.abort,
                    # so we use nonZeroIsFatal=False instead.
                    try:
                        self.result = run_python_unittests(python_binary, args=args, paths=[_hpy_test_root()],
                                                      env=tenv, use_pytest=True, lock=lock, nonZeroIsFatal=False,
                                                      out=self.out, err=self.out, timeout=timeout, report=report)
                        self.out.lines.append(f"Thread {self.name} finished with result {self.result}")
                    except BaseException as e: # pylint: disable=broad-except;
                        self.out.lines.append(f"Thread {self.name} finished with exception {e}")
                        self.result = e
        else:
            threads = []
            class HPyUnitTestsThread:
                def __init__(self, name):
                    self.name = name
                    self.out = mx.LinesOutputCapture()

                def start(self):
                    self.result = run_python_unittests(python_binary, args=args, paths=[_hpy_test_root()],
                                                       env=tenv, use_pytest=True, nonZeroIsFatal=nonZeroIsFatal,
                                                       timeout=timeout, report=report)
                    print(f"Thread {self.name} finished with result {self.result}")

                def join(self, *args, **kwargs):
                    return

                def is_alive(self):
                    return False

        abi_list = ['cpython', 'universal']
        if include_native:
            # modes 'debug' and 'nfi' can only be used if native access is allowed
            abi_list.append('debug')
        for abi in abi_list:
            tenv = env.copy()
            tenv["TEST_HPY_ABI"] = abi
            thread = HPyUnitTestsThread(name=abi)
            threads.append(thread)
            thread.start()

        alive = [True] * len(threads)
        while any(alive):
            for i, t in enumerate(threads):
                t.join(timeout=1.0)
                mx.logv("## Progress (last 5 lines) of thread %r:\n%s\n" % (t.name, os.linesep.join(t.out.lines[-5:])))
                alive[i] = t.is_alive()

        failed_threads = [t for t in threads if t.result != 0]
        for t in threads:
            mx.log("\n\n### Output of thread %r: \n\n%s" % (t.name, t.out))
        if any(failed_threads):
            threads_info = [f"{t.name} (result: {t.result})" for t in failed_threads]
            message = "HPy testing threads failed: " + ', '.join(threads_info)
            if nonZeroIsFatal:
                mx.abort("ERROR: " + message)
            else:
                mx.warn(message)


def run_tagged_unittests(python_binary, env=None, cwd=None, nonZeroIsFatal=True,
                         checkIfWithGraalPythonEE=False, report=False):
    python_path = os.path.join(_dev_pythonhome(), 'lib-python/3')
    sub_env = dict(
        ENABLE_THREADED_GRAALPYTEST="true",
    )
    sub_env.update(env or os.environ)
    sub_env.update(
        PYTHONPATH=python_path,
        ENABLE_CPYTHON_TAGGED_UNITTESTS="true",
    )
    print(f"with PYTHONPATH={python_path}")

    if checkIfWithGraalPythonEE:
        mx.run([python_binary, "-c", "import sys; print(sys.version)"])
    run_python_unittests(
        python_binary,
        args=["-v"],
        paths=["test_tagged_unittests.py"],
        env=sub_env,
        cwd=cwd,
        nonZeroIsFatal=nonZeroIsFatal,
        report=report,
    )


def get_cpython():
    if python3_home := os.environ.get("PYTHON3_HOME"):
        return os.path.join(python3_home, "python")
    else:
        return "python3"

def get_wrapper_urls(wrapper_properties_file, keys):
    ret = dict()
    with(open(wrapper_properties_file)) as f:
        while line := f.readline():
            line = line.strip()
            for key in keys:
                if not line.startswith("#") and key not in ret.keys() and key in line:
                    s = line.split("=")
                    if len(s) > 1:
                        ret.update({key : mx_urlrewrites.rewriteurl(s[1].strip())})
                        break
    for key in keys:
        assert key in ret.keys(), f"Expected key '{key}' to be in {wrapper_properties_file}, but was not."

    return ret

def graalpython_gate_runner(args, tasks):
    report = lambda: (not is_collecting_coverage()) and task
    nonZeroIsFatal = not is_collecting_coverage()
    if WIN32:
        # Windows support is still experimental, so we exclude some unittests
        # on Windows for now. If you add unittests and cannot get them to work
        # on Windows, yet, add their files here.
        excluded_tests = [
            "test_code.py", # forward slash in path problem
            "test_csv.py",
            "test_imports.py", # import posix
            "test_locale.py",
            "test_math.py",
            "test_memoryview.py",
            "test_mmap.py", # sys.getwindowsversion
            "test_multiprocessing.py", # import _winapi
            "test_multiprocessing_graalpy.py", # import _winapi
            "test_patched_pip.py",
            "test_pathlib.py",
            "test_pdb.py", # Tends to hit GR-41935
            "test_posix.py", # import posix
            "test_pyio.py",
            "test_signal.py",
            "test_struct.py",
            "test_structseq.py", # import posix
            "test_subprocess.py",
            "test_thread.py", # sys.getwindowsversion
            "test_traceback.py",
            "test_zipimport.py", # sys.getwindowsversion
            "test_ssl_java_integration.py",
            "*/cpyext/test_abstract.py",
            "*/cpyext/test_functions.py",
            "*/cpyext/test_long.py",
            "*/cpyext/test_member.py",
            "*/cpyext/test_memoryview.py",
            "*/cpyext/test_misc.py",
            "*/cpyext/test_mmap.py",
            "*/cpyext/test_slice.py",
            "*/cpyext/test_shutdown.py",
            "*/cpyext/test_thread.py",
            "*/cpyext/test_unicode.py",
            "*/cpyext/test_wiki.py",
            "*/cpyext/test_tp_slots.py",  # Temporarily disabled due to GR-54345
        ]
    else:
        excluded_tests = []

    # JUnit tests
    with Task('GraalPython JUnit', tasks, tags=[GraalPythonTags.junit, GraalPythonTags.windows]) as task:
        if task:
            if WIN32:
                punittest(
                    [
                        "--verbose",
                        "--no-leak-tests",
                        "--regex",
                        r'((com\.oracle\.truffle\.tck\.tests)|(graal\.python\.test\.integration)|(graal\.python\.test\.(builtin|interop|util)))'
                    ],
                    report=True
                )
            else:
                punittest(['--verbose'], report=report())
                # Run tests with static exclusion paths
                jdk = mx.get_jdk()
                prev = jdk.java_args_pfx
                try:
                    jdk.java_args_pfx = (mx._opts.java_args or []) + ['-Dpython.WithoutPlatformAccess=true']
                    punittest(['--verbose', '--no-leak-tests', '--regex', 'com.oracle.graal.python.test.advanced.ExclusionsTest'])
                finally:
                    jdk.java_args_pfx = prev

    # JUnit tests with Maven
    with Task('GraalPython integration JUnit with Maven', tasks, tags=[GraalPythonTags.junit_maven]) as task:
        if task:
            mvn_repo_path, artifacts_version, env = deploy_local_maven_repo()
            mvn_repo_path = pathlib.Path(mvn_repo_path).as_uri()
            central_override = mx_urlrewrites.rewriteurl('https://repo1.maven.org/maven2/')
            pom_path = os.path.join(SUITE.dir, 'graalpython/com.oracle.graal.python.test.integration/pom.xml')
            mvn_cmd_base = ['-f', pom_path,
                            f'-Dcom.oracle.graal.python.test.polyglot.version={artifacts_version}',
                            f'-Dcom.oracle.graal.python.test.polyglot_repo={mvn_repo_path}',
                            f'-Dcom.oracle.graal.python.test.central_repo={central_override}',
                            '--batch-mode']

            mx.log("Running integration JUnit tests on GraalVM SDK")
            env['JAVA_HOME'] = graalvm_jdk()
            mx.run_maven(mvn_cmd_base + ['-U', 'clean', 'test'], env=env)

            env['JAVA_HOME'] = os.environ['JAVA_HOME']
            mx.log(f"Running integration JUnit tests on vanilla JDK: {os.environ.get('JAVA_HOME', 'system java')}")
            mx.run_maven(mvn_cmd_base + ['-U', '-Dpolyglot.engine.WarnInterpreterOnly=false', 'clean', 'test'], env=env)

    # Unittests on JVM
    with Task('GraalPython Python unittests', tasks, tags=[GraalPythonTags.unittest]) as task:
        if task:
            if not WIN32:
                mx.run(["env"])
            run_python_unittests(
                graalpy_standalone_jvm(),
                exclude=excluded_tests,
                nonZeroIsFatal=nonZeroIsFatal,
                report=report()
            )

    with Task('GraalPython Python unittests with CPython', tasks, tags=[GraalPythonTags.unittest_cpython]) as task:
        if task:
            env = extend_os_env(PYTHONHASHSEED='0')
            test_args = [get_cpython(), _graalpytest_driver(), "-v", "graalpython/com.oracle.graal.python.test/src/tests"]
            mx.run(test_args, nonZeroIsFatal=True, env=env)

    with Task('GraalPython sandboxed tests', tasks, tags=[GraalPythonTags.unittest_sandboxed]) as task:
        if task:
            run_python_unittests(graalpy_standalone_native_managed(), exclude=excluded_tests, report=report())

    with Task('GraalPython multi-context unittests', tasks, tags=[GraalPythonTags.unittest_multi]) as task:
        if task:
            run_python_unittests(graalpy_standalone_jvm(), args=["-multi-context"], exclude=excluded_tests, nonZeroIsFatal=nonZeroIsFatal, report=report())

    with Task('GraalPython Jython emulation tests', tasks, tags=[GraalPythonTags.unittest_jython]) as task:
        if task:
            run_python_unittests(graalpy_standalone_jvm(), args=["--python.EmulateJython"], paths=["test_interop.py"], report=report(), nonZeroIsFatal=nonZeroIsFatal)

    with Task('GraalPython with Arrow Storage Strategy', tasks, tags=[GraalPythonTags.unittest_arrow]) as task:
        if task:
            run_python_unittests(graalpy_standalone_jvm(), args=["--python.UseNativePrimitiveStorageStrategy"], report=report(), nonZeroIsFatal=nonZeroIsFatal)

    with Task('GraalPython HPy tests', tasks, tags=[GraalPythonTags.unittest_hpy]) as task:
        if task:
            run_hpy_unittests(graalpy_standalone_native(), nonZeroIsFatal=nonZeroIsFatal, report=report())

    with Task('GraalPython HPy sandboxed tests', tasks, tags=[GraalPythonTags.unittest_hpy_sandboxed]) as task:
        if task:
            run_hpy_unittests(graalpy_standalone_native_managed(), include_native=False, report=report())

    with Task('GraalPython posix module tests', tasks, tags=[GraalPythonTags.unittest_posix]) as task:
        if task:
            opt = '--PosixModuleBackend={backend} --Sha3ModuleBackend={backend}'
            tests_list = ["test_posix.py", "test_mmap.py", "test_hashlib.py", "test_resource.py"]
            run_python_unittests(graalpy_standalone_jvm(), args=opt.format(backend='native').split(), paths=tests_list, report=report())
            run_python_unittests(graalpy_standalone_jvm(), args=opt.format(backend='java').split(), paths=tests_list, report=report())

    with Task('GraalPython standalone module tests', tasks, tags=[GraalPythonTags.unittest_standalone]) as task:
        if task:
            gvm_jdk = graalvm_jdk()
            standalone_home = graalpy_standalone_home('jvm')
            mvn_repo_path, version, env = deploy_local_maven_repo()

            env['ENABLE_STANDALONE_UNITTESTS'] = 'true'
            # TODO disabled until gradle available on gate
            # env['ENABLE_ENABLE_GRADLE_STANDALONE_UNITTESTS'] = 'true'
            env['ENABLE_JBANG_INTEGRATION_UNITTESTS'] ='true'
            env['JAVA_HOME'] = gvm_jdk
            env['PYTHON_STANDALONE_HOME'] = standalone_home

            # setup maven downloader overrides
            env['MAVEN_REPO_OVERRIDE'] = ",".join([
                f"{pathlib.Path(mvn_repo_path).as_uri()}/",
                mx_urlrewrites.rewriteurl('https://repo1.maven.org/maven2/'),
            ])

            urls = get_wrapper_urls("graalpython/com.oracle.graal.python.test/src/tests/standalone/mvnw/.mvn/wrapper/maven-wrapper.properties", ["distributionUrl"])
            if "distributionUrl" in urls:
                env["MAVEN_DISTRIBUTION_URL_OVERRIDE"] = mx_urlrewrites.rewriteurl(urls["distributionUrl"])

            env["org.graalvm.maven.downloader.version"] = version
            env["org.graalvm.maven.downloader.repository"] = f"{pathlib.Path(mvn_repo_path).as_uri()}/"

            # setup JBang executable
            env["JBANG_CMD"] = _prepare_jbang()
            m2_cache = get_maven_cache()
            if m2_cache:
                env["JBANG_REPO"] = m2_cache

            # run the test
            mx.logv(f"running with os.environ extended with: {env=}")
            mx.run([sys.executable, _graalpytest_driver(), "-v",
                "graalpython/com.oracle.graal.python.test/src/tests/standalone/test_jbang_integration.py",
                "graalpython/com.oracle.graal.python.test/src/tests/standalone/test_standalone.py"], env=env)

    with Task('GraalPython Python tests', tasks, tags=[GraalPythonTags.tagged]) as task:
        if task:
            # don't fail this task if we're running with the jacoco agent, we know that some tests don't pass with it enabled
            run_tagged_unittests(graalpy_standalone_native(), nonZeroIsFatal=(not is_collecting_coverage()), report=report())

    with Task('GraalPython sandboxed Python tests', tasks, tags=[GraalPythonTags.tagged_sandboxed]) as task:
        if task:
            run_tagged_unittests(graalpy_standalone_native_managed(), checkIfWithGraalPythonEE=True, cwd=SUITE.dir, report=report())

    # Unittests on SVM
    with Task('GraalPython tests on SVM', tasks, tags=[GraalPythonTags.svmunit, GraalPythonTags.windows]) as task:
        if task:
            run_python_unittests(graalpy_standalone_native(), exclude=excluded_tests, aot_compatible=True, report=report())

    with Task('GraalPython sandboxed tests on SVM', tasks, tags=[GraalPythonTags.svmunit_sandboxed]) as task:
        if task:
            run_python_unittests(graalpy_standalone_native_managed(), aot_compatible=True, report=report())

    with Task('GraalPython license header update', tasks, tags=[GraalPythonTags.license]) as task:
        if task:
            python_checkcopyrights([])

    with Task('GraalPython GraalVM build', tasks, tags=[GraalPythonTags.svm, GraalPythonTags.graalvm], report=True) as task:
        if task:
            with set_env(PYTHONIOENCODING=None, MX_CHECK_IOENCODING="0"):
                svm_image = python_svm()
                benchmark = os.path.join(PATH_MESO, "image-magix.py")
                out = mx.OutputCapture()
                mx.run([svm_image, "-S", "--log.python.level=FINE", benchmark], nonZeroIsFatal=True, out=mx.TeeOutputCapture(out), err=mx.TeeOutputCapture(out))
            success = "\n".join([
                "[0, 0, 0, 0, 0, 0, 10, 10, 10, 0, 0, 10, 3, 10, 0, 0, 10, 10, 10, 0, 0, 0, 0, 0, 0]",
            ])
            if success not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not match success pattern:\n' + success)
            if not WIN32:
                assert "Using preinitialized context." in out.data

    with Task('Python SVM Truffle TCK', tasks, tags=[GraalPythonTags.language_checker], report=True) as task:
        if task:
            mx.run_mx([
                "--dy", "graalpython,/substratevm",
                "-p", os.path.join(mx.suite("truffle"), "..", "vm"),
                "--native-images=",
                "build",
            ])
            mx.run_mx([
                "--dy", "graalpython,/substratevm",
                "-p", os.path.join(mx.suite("truffle"), "..", "vm"),
                "--native-images=",
                "gate", "svm-truffle-tck-python",
            ])

    with Task('Python exclusion of security relevant classes', tasks, tags=[GraalPythonTags.exclusions_checker]) as task:
        if task:
            native_image([
                "--language:python",
                "-Dpython.WithoutSSL=true",
                "-Dpython.WithoutPlatformAccess=true",
                "-Dpython.WithoutCompressionLibraries=true",
                "-Dpython.WithoutNativePosix=true",
                "-Dpython.WithoutJavaInet=true",
                "-Dimage-build-time.PreinitializeContexts=",
                "-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime",
                "-H:+ReportExceptionStackTraces",
                *map(
                    lambda s: f"-H:ReportAnalysisForbiddenType={s}",
                    """
                    com.sun.security.auth.UnixNumericGroupPrincipal
                    com.sun.security.auth.module.UnixSystem
                    java.security.KeyManagementException
                    java.security.KeyStore
                    java.security.KeyStoreException
                    java.security.SignatureException
                    java.security.UnrecoverableEntryException
                    java.security.UnrecoverableKeyException
                    java.security.cert.CRL
                    java.security.cert.CRLException
                    java.security.cert.CertPathBuilder
                    java.security.cert.CertPathBuilderSpi
                    java.security.cert.CertPathChecker
                    java.security.cert.CertPathParameters
                    java.security.cert.CertSelector
                    java.security.cert.CertStore
                    java.security.cert.CertStoreParameters
                    java.security.cert.CertStoreSpi
                    java.security.cert.CertificateEncodingException
                    java.security.cert.CertificateParsingException
                    java.security.cert.CollectionCertStoreParameters
                    java.security.cert.Extension
                    java.security.cert.PKIXBuilderParameters
                    java.security.cert.PKIXCertPathChecker
                    java.security.cert.PKIXParameters
                    java.security.cert.PKIXRevocationChecker
                    java.security.cert.PKIXRevocationChecker$Option
                    java.security.cert.TrustAnchor
                    java.security.cert.X509CRL
                    java.security.cert.X509CertSelector
                    java.security.cert.X509Certificate
                    java.security.cert.X509Extension
                    sun.security.x509.AVA
                    sun.security.x509.AVAComparator
                    sun.security.x509.AVAKeyword
                    sun.security.x509.AuthorityInfoAccessExtension
                    sun.security.x509.AuthorityKeyIdentifierExtension
                    sun.security.x509.BasicConstraintsExtension
                    sun.security.x509.CRLDistributionPointsExtension
                    sun.security.x509.CertAttrSet
                    sun.security.x509.CertificatePoliciesExtension
                    sun.security.x509.CertificatePolicySet
                    sun.security.x509.DNSName
                    sun.security.x509.EDIPartyName
                    sun.security.x509.ExtendedKeyUsageExtension
                    sun.security.x509.Extension
                    sun.security.x509.GeneralName
                    sun.security.x509.GeneralNameInterface
                    sun.security.x509.GeneralSubtree
                    sun.security.x509.GeneralSubtrees
                    sun.security.x509.IPAddressName
                    sun.security.x509.IssuerAlternativeNameExtension
                    sun.security.x509.KeyUsageExtension
                    sun.security.x509.NameConstraintsExtension
                    sun.security.x509.NetscapeCertTypeExtension
                    sun.security.x509.OIDMap
                    sun.security.x509.OIDMap$OIDInfo
                    sun.security.x509.OIDName
                    sun.security.x509.OtherName
                    sun.security.x509.PKIXExtensions
                    sun.security.x509.PrivateKeyUsageExtension
                    sun.security.x509.RDN
                    sun.security.x509.RFC822Name
                    sun.security.x509.SubjectAlternativeNameExtension
                    sun.security.x509.SubjectKeyIdentifierExtension
                    sun.security.x509.URIName
                    sun.security.x509.X400Address
                    sun.security.x509.X500Name

                    com.oracle.graal.python.runtime.NFIPosixSupport

                    java.util.zip.Adler32

                    org.graalvm.shadowed.org.tukaani.xz.XZ
                    org.graalvm.shadowed.org.tukaani.xz.XZOutputStream
                    org.graalvm.shadowed.org.tukaani.xz.LZMA2Options
                    org.graalvm.shadowed.org.tukaani.xz.FilterOptions

                    java.util.zip.ZipInputStream

                    java.nio.channels.ServerSocketChannel
                    """.split()
                ),
                "-cp", mx.dependency("com.oracle.graal.python.test").classpath_repr(),
                "com.oracle.graal.python.test.advanced.ExclusionsTest"
            ])


mx_gate.add_gate_runner(SUITE, graalpython_gate_runner)


class ArchiveProject(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, theLicense, **args):
        super(ArchiveProject, self).__init__(suite, name, deps, workingSets, theLicense)

    def output_dir(self):
        if hasattr(self, "outputFile"):
            self.outputFile = mx_subst.path_substitutions.substitute(self.outputFile)
            return os.path.dirname(os.path.join(self.dir, self.outputFile))
        else:
            assert hasattr(self, "outputDir")
            self.outputDir = mx_subst.path_substitutions.substitute(self.outputDir)
            return os.path.join(self.dir, self.outputDir)

    def archive_prefix(self):
        return mx_subst.path_substitutions.substitute(getattr(self, "prefix", ""))

    def getArchivableResults(self, use_relpath=True, single=False):
        for f, arcname in super().getArchivableResults(use_relpath=use_relpath, single=single):
            yield f, arcname.replace(os.sep, "/")

    def getResults(self):
        if hasattr(self, "outputFile"):
            return [os.path.join(self.dir, self.outputFile)]
        else:
            ignore_regexps = [re.compile(s) for s in getattr(self, "ignorePatterns", [])]
            results = []
            for root, _, files in os.walk(self.output_dir()):
                for name in files:
                    path = os.path.join(root, name)
                    if not any(r.search(path) for r in ignore_regexps):
                        results.append(path)
            return results

def _prepare_jbang():
    zip_path = mx.library('JBANG', True).get_path(resolve = True)

    oldpwd = os.getcwd()
    work_dir = os.path.join(tempfile.gettempdir(),tempfile.mkdtemp())
    os.chdir(work_dir)
    try:
        with ZipFile(zip_path, "r") as zip_ref:
            zip_ref.extractall(work_dir)

        folders = os.listdir(work_dir)
        jbang_executable = os.path.join(work_dir, folders[0], "bin", "jbang")
        os.chmod(jbang_executable, stat.S_IRWXU)
        return jbang_executable
    finally:
        os.chdir(oldpwd)

def deploy_binary_if_main(args):
    """if the active branch is the main branch, deploy binaries for the primary suite to remote maven repository."""
    active_branch = mx.VC.get_vc(SUITE.dir).active_branch(SUITE.dir)
    if active_branch == MAIN_BRANCH:
        if sys.platform == "darwin":
            args.insert(0, "--platform-dependent")
        return mx.command_function('deploy-binary')(args)
    else:
        mx.log('The active branch is "%s". Binaries are deployed only if the active branch is "%s".' % (
            active_branch, MAIN_BRANCH))
        return 0


def _get_suite_dir(suitename):
    return mx.suite(suitename).dir


def _get_src_dir(projectname):
    for suite in mx.suites():
        for p in suite.projects:
            if p.name == projectname:
                if len(p.source_dirs()) > 0:
                    return p.source_dirs()[0]
                else:
                    return p.dir
    mx.abort("Could not find src dir for project %s" % projectname)


def _get_output_root(projectname):
    for suite in mx.suites():
        for p in suite.projects:
            if p.name == projectname:
                return p.get_output_root()
    mx.abort("Could not find out dir for project %s" % projectname)

# We use the ordinal value of this character and add it to the version parts to
# ensure that we store ASCII-compatible printable characters into the versions
# file.
#
# IMPORTANT: This needs to be in sync with 'PythonLanguage.VERSION_BASE' and
#            'PythonResource.VERSION_BASE'.
VERSION_BASE = '!'

def py_version_short(variant=None, **kwargs):
    if variant == 'major_minor_nodot':
        return PYTHON_VERSION_MAJ_MIN.replace(".", "")
    elif variant == 'binary':
        return "".join([chr(int(p) + ord(VERSION_BASE)) for p in PYTHON_VERSION.split(".")])
    else:
        return PYTHON_VERSION_MAJ_MIN


def graal_version_short(variant=None, **kwargs):
    if variant == 'major_minor_nodot':
        return GRAAL_VERSION_MAJ_MIN.replace(".", "")
    elif variant == 'binary':
        return "".join([chr(int(p) + ord(VERSION_BASE)) for p in GRAAL_VERSION.split(".")])
    else:
        return GRAAL_VERSION_MAJ_MIN


def graalpy_ext(llvm_mode, **kwargs):
    if not llvm_mode:
        mx.abort("substitution 'graalpy_ext' is missing argument 'llvm_mode'")
    os = mx_subst.path_substitutions.substitute('<os>')
    arch = mx_subst.path_substitutions.substitute('<arch>')
    if arch == 'amd64':
        # be compatible with CPython's designation
        # (see also: 'PythonUtils.getPythonArch')
        arch = 'x86_64'

    # 'pyos' also needs to be compatible with CPython's designation.
    # See class 'com.oracle.graal.python.builtins.PythonOS'
    # In this case, we can just use 'sys.platform' of the Python running MX.
    pyos = sys.platform

    # on Windows we use '.pyd' else '.so' but never '.dylib' (similar to CPython):
    # https://github.com/python/cpython/issues/37510
    ext = 'pyd' if os == 'windows' else 'so'
    return f'.graalpy{GRAAL_VERSION_MAJ_MIN.replace(".", "") + dev_tag()}-{PYTHON_VERSION_MAJ_MIN.replace(".", "")}-{llvm_mode}-{arch}-{pyos}.{ext}'


def dev_tag(arg=None, **kwargs):
    if not get_boolean_env('GRAALPYTHONDEVMODE', True) or 'dev' not in SUITE.release_version():
        mx.logv("GraalPy dev_tag: <0 because not in dev mode>")
        return ''

    rev_list = [
        os.path.join('graalpython', 'lib-graalpython', 'patches'),
        os.path.join('graalpython', 'lib-graalpython', 'modules', 'autopatch_capi.py'),
        os.path.join('graalpython', 'com.oracle.graal.python.cext', 'include'),
        os.path.join('graalpython', 'com.oracle.graal.python.cext', 'src', 'capi.h'),
    ]

    rev = SUITE.vc.git_command(SUITE.dir, ['log',
                                           '-1',
                                           '--format=short',
                                           '--'] + rev_list, abortOnError=True)

    mx.logvv("GraalPy dev_tag: got output: \n" + repr(rev))
    res = 'dev' + rev.split()[1][:10]
    mx.logv("GraalPy dev_tag: " + res)
    return res


mx_subst.path_substitutions.register_with_arg('suite', _get_suite_dir)
mx_subst.path_substitutions.register_with_arg('src_dir', _get_src_dir)
mx_subst.path_substitutions.register_with_arg('output_root', _get_output_root)
mx_subst.path_substitutions.register_with_arg('py_ver', py_version_short)
mx_subst.path_substitutions.register_with_arg('graal_ver', graal_version_short)
mx_subst.results_substitutions.register_with_arg('dev_tag', dev_tag)

mx_subst.path_substitutions.register_with_arg('graalpy_ext', graalpy_ext)
mx_subst.results_substitutions.register_with_arg('graalpy_ext', graalpy_ext)


def update_import(name, suite_py, args):
    parent = os.path.join(SUITE.dir, "..")
    dep_dir = None
    for dirpath, dirnames, _ in os.walk(parent):
        if os.path.sep in os.path.relpath(dirpath, parent):
            dirnames.clear() # we're looking for siblings or sibling-subdirs
        elif name in dirnames and os.path.isdir(os.path.join(dirpath, name, "mx.%s" % name)):
            dep_dir = dirpath
            break
    if not dep_dir:
        mx.warn("could not find suite %s to update" % name)
        return
    vc = mx.VC.get_vc(dep_dir)
    repo_name = os.path.basename(dep_dir)
    if repo_name == "graal" and args.graal_rev:
        rev = args.graal_rev
    elif repo_name == "graal-enterprise" and args.graal_enterprise_rev:
        rev = args.graal_enterprise_rev
    elif args.no_pull:
        rev = "HEAD"
    else:
        vc.pull(dep_dir)
        rev = "origin/master"
    if rev != "HEAD":
        vc.update(dep_dir, rev=rev, mayPull=True)
    tip = str(vc.tip(dep_dir)).strip()
    contents = None
    with open(suite_py, 'r') as f:
        contents = f.read()
    dep_re = re.compile(r"['\"]name['\"]:\s+['\"]%s['\"],\s+['\"]version['\"]:\s+['\"]([a-z0-9]+)['\"]" % name, re.MULTILINE)
    dep_match = dep_re.search(contents)
    if dep_match:
        start = dep_match.start(1)
        end = dep_match.end(1)
        assert end - start == len(tip)
        mx.update_file(suite_py, "".join([contents[:start], tip, contents[end:]]), showDiff=True)
    return tip


def update_import_cmd(args):
    """Update our imports"""

    parser = ArgumentParser()
    parser.add_argument('--graal-rev', default='')
    parser.add_argument('--graal-enterprise-rev', default='')
    parser.add_argument('--no-pull', action='store_true')
    parser.add_argument('--no-push', action='store_true')
    parser.add_argument('--allow-dirty', action='store_true')
    args = parser.parse_args(args)

    join = os.path.join
    vc = SUITE.vc

    current_branch = vc.active_branch(SUITE.dir)
    if vc.isDirty(SUITE.dir) and not args.allow_dirty:
        mx.abort("updating imports should be done on a clean branch")
    if current_branch == "master":
        vc.git_command(SUITE.dir, ["checkout", "-b", f"update/GR-21590/{datetime.datetime.now().strftime('%d%m%y')}"])
        current_branch = vc.active_branch(SUITE.dir)

    local_names = ["graalpython", "graalpython-apptests"]
    repos = [os.path.join(SUITE.dir, "..", name) for name in local_names]
    suite_py_files = [os.path.join(SUITE.dir, "..", name, f"mx.{name}", "suite.py") for name in local_names]
    for suite_py in suite_py_files:
        assert os.path.isfile(suite_py), f"Cannot find {suite_py}"

    # make sure all other repos are clean and on the same branch
    for d in repos:
        if vc.isDirty(d) and not args.allow_dirty:
            mx.abort("repo %s is not clean" % d)
        d_branch = vc.active_branch(d)
        if d_branch == current_branch:
            pass
        elif d_branch == "master":
            vc.set_branch(d, current_branch, with_remote=False)
            vc.git_command(d, ["checkout", current_branch], abortOnError=True)
        else:
            mx.abort("repo %s is not on the main branch or on %s" % (d, current_branch))

    # make sure we can update the overlays
    overlaydir = join(SUITE.dir, "..", "ci-overlays")
    if not os.path.exists(overlaydir):
        mx.abort("Overlays repo must be next to graalpython repo")
        vc = mx.VC.get_vc(overlaydir)
    if vc.isDirty(overlaydir) and not args.allow_dirty:
        mx.abort("overlays repo must be clean")
    overlaybranch = vc.active_branch(overlaydir)
    if overlaybranch == "master":
        if not args.no_pull:
            vc.pull(overlaydir)
        vc.set_branch(overlaydir, current_branch, with_remote=False)
        vc.git_command(overlaydir, ["checkout", current_branch], abortOnError=True)
    elif overlaybranch == current_branch:
        pass
    else:
        mx.abort("overlays repo must be on the main branch or branch %s" % current_branch)

    # find all imports we might update
    imports_to_update = set()
    for suite_py in suite_py_files:
        d = {}
        with open(suite_py) as f:
            exec(f.read(), d, d) # pylint: disable=exec-used;
        for suite in d["suite"].get("imports", {}).get("suites", []):
            import_name = suite["name"]
            if suite.get("version") and import_name not in local_names and import_name != 'library-tester':
                imports_to_update.add(import_name)

    revisions = {}
    # now update all imports
    for name in imports_to_update:
        for _, suite_py in enumerate(suite_py_files):
            revisions[name] = update_import(name, suite_py, args)

    # copy files we inline from our imports
    shutil.copy(
        join(mx.suite("truffle").dir, "..", "common.json"),
        join(overlaydir, "python", "graal", "common.json"))
    shutil.copytree(
        join(mx.suite("truffle").dir, "..", "ci"),
        join(overlaydir, "python", "graal", "ci"),
        dirs_exist_ok=True)

    enterprisedir = join(SUITE.dir, "..", "graal-enterprise")
    shutil.copy(
        join(enterprisedir, "common.json"),
        join(overlaydir, "python", "graal-enterprise", "common.json"))
    shutil.copytree(
        join(enterprisedir, "ci"),
        join(overlaydir, "python", "graal-enterprise", "ci"),
        dirs_exist_ok=True)

    # update the graal-enterprise revision in the overlay (used by benchmarks)
    with open(join(overlaydir, "python", "imported-constants.json"), 'w') as fp:
        d = {'GRAAL_ENTERPRISE_REVISION': revisions['graalpython-enterprise']}
        json.dump(d, fp, indent=2)

    repos_updated = []

    # now allow dependent repos to hook into update
    output = mx.OutputCapture()
    for repo in repos:
        basename = os.path.basename(repo)
        cmdname = "%s-update-import" % basename
        is_mx_command = mx.run_mx(["-p", repo, "help", cmdname], out=output, err=output, nonZeroIsFatal=False, quiet=True) == 0
        if is_mx_command:
            mx.run_mx(["-p", repo, cmdname, "--overlaydir=%s" % overlaydir], suite=repo, nonZeroIsFatal=True)
        else:
            print(mx.colorize('%s command for %s.. skipped!' % (cmdname, basename), color='magenta', bright=True, stream=sys.stdout))

    # commit ci-overlays if dirty
    if vc.isDirty(overlaydir):
        vc.commit(overlaydir, "Update Python imports")
        repos_updated.append(overlaydir)

    overlaytip = str(vc.tip(overlaydir)).strip()

    # update ci import in all our repos, commit the full update
    prev_verbosity = mx.get_opts().very_verbose
    for repo in repos:
        jsonnetfile = os.path.join(repo, "ci.jsonnet")
        with open(jsonnetfile, "w") as f:
            f.write('{ "overlay": "%s" }\n' % overlaytip)
        if vc.isDirty(repo):
            vc.commit(repo, "Update imports")
            repos_updated.append(repo)

    # push all repos
    if not args.no_push:
        for repo in repos_updated:
            try:
                mx.get_opts().very_verbose = True
                vc.git_command(repo, ["push", "-u", "origin", "HEAD:%s" % current_branch], abortOnError=True)
            finally:
                mx.get_opts().very_verbose = prev_verbosity

    if repos_updated:
        mx.log("\n  ".join(["These repos were updated:"] + repos_updated))


def python_style_checks(args):
    "Check (and fix where possible) copyrights, eclipse formatting, and spotbugs"
    warn_about_old_hardcoded_version()
    python_run_mx_filetests(args)
    python_checkcopyrights(["--fix"] if "--fix" in args else [])
    if not os.environ.get("ECLIPSE_EXE"):
        find_eclipse()
    if os.environ.get("ECLIPSE_EXE"):
        mx.command_function("eclipseformat")(["--primary"])
    if "--no-spotbugs" not in args:
        mx.command_function("spotbugs")([])


def python_checkcopyrights(args):
    # we wan't to ignore lib-python/3, because that's just crazy
    listfilename = tempfile.mktemp()
    with open(listfilename, "w") as listfile:
        mx.run(["git", "ls-tree", "-r", "HEAD", "--name-only"], out=listfile)
    with open(listfilename, "r") as listfile:
        content = listfile.read()
    with open(listfilename, "w") as listfile:
        for line in content.split("\n"):
            if "lib-python/3" in line or "com.oracle.graal.python.test/testData" in line or "com.oracle.graal.python.pegparser.test/testData" in line:
                pass
            elif os.path.splitext(line)[1] in [".py", ".java", ".c", ".h", ".sh"]:
                listfile.write(line)
                listfile.write("\n")
    try:
        r = mx.command_function("checkcopyrights")(["--primary", "--", "--file-list", listfilename] + args)
        if r != 0:
            mx.abort("copyrights check failed")
    finally:
        os.unlink(listfilename)

    _python_checkpatchfiles()


def python_run_mx_filetests(args):
    for test in glob.glob(os.path.join(os.path.dirname(__file__), "test_*.py")):
        if not test.endswith("data.py"):
            mx.log(test)
            mx.run([sys.executable, test, "-v"])


def _python_checkpatchfiles():
    env = os.environ.copy()
    mx_dir = Path(__file__).parent
    root_dir = mx_dir.parent
    [pip_wheel] = (root_dir / 'graalpython' / 'lib-python' / '3' / 'ensurepip' / '_bundled').glob('pip-*.whl')
    env['PYTHONPATH'] = str(pip_wheel)
    # We use the CPython that is used for running our unittests, not the one mx is running with.
    # This is done to make sure it can import the pip wheel.
    mx.run(
        [get_cpython(), str(mx_dir / 'verify_patches.py'), str(root_dir / 'graalpython' / 'lib-graalpython' / 'patches')],
        env=env,
        nonZeroIsFatal=True,
    )


# ----------------------------------------------------------------------------------------------------------------------
#
# register as a GraalVM language
#
# ----------------------------------------------------------------------------------------------------------------------
mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=SUITE,
    name='GraalVM Python license files',
    short_name='pynl',
    dir_name='python',
    dependencies=[],
    license_files=['LICENSE_GRAALPY.txt'],
    third_party_license_files=['THIRD_PARTY_LICENSE_GRAALPY.txt'],
    truffle_jars=[],
    support_distributions=[
        'graalpython:GRAALPYTHON_GRAALVM_LICENSES',
    ],
    priority=6,  # Higher than 'GraalVM Python' to help defining the main
    stability="experimental",
))


standalone_dependencies_common = {
    'LLVM Runtime Core': ('lib/sulong', []),
    'LLVM Runtime Native': ('lib/sulong', []),
    'LLVM.org toolchain': ('lib/llvm-toolchain', []),
}

mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=SUITE,
    name='GraalVM Python',
    short_name='pyn',
    installable_id='python',
    dir_name='python',
    standalone_dir_name='graalpy-community-<version>-<graalvm_os>-<arch>',
    standalone_dir_name_enterprise='graalpy-<version>-<graalvm_os>-<arch>',
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'pynl',
        'Truffle',
        'LLVM Runtime Native',
        'LLVM.org toolchain',
        'TRegex',
        'ICU4J',
        'XZ',
    ],
    standalone_dependencies={**standalone_dependencies_common, **{
        'GraalVM Python license files': ('', []),
    }},
    standalone_dependencies_enterprise={**standalone_dependencies_common, **{
        **({} if mx.is_windows() else {
            'LLVM Runtime Managed': ('lib/sulong', []),
            'LLVM Runtime Enterprise': ('lib/sulong', []),
            'LLVM Runtime Native Enterprise': ('lib/sulong', []),
            'GraalVM Python EE managed libraries': ('', []),
        }),
        'GraalVM Python EE': ('', []),
        'GraalVM Python license files EE': ('', []),
        'GraalVM enterprise license files': ('', ['LICENSE.txt', 'GRAALVM-README.md']),
    }},
    truffle_jars=[
        'graalpython:GRAALPYTHON',
        'graalpython:BOUNCYCASTLE-PROVIDER',
        'graalpython:BOUNCYCASTLE-PKIX',
        'graalpython:BOUNCYCASTLE-UTIL',
    ],
    support_distributions=[
        'graalpython:GRAALPYTHON_GRAALVM_SUPPORT',
        'graalpython:GRAALPYTHON_GRAALVM_DOCS',
        'graalpython:GRAALPY_VIRTUALENV',
    ],
    library_configs=[
        mx_sdk.LanguageLibraryConfig(
            launchers=['bin/<exe:graalpy>', 'bin/<exe:graalpy-lt>', 'bin/<exe:python>', 'bin/<exe:python3>', 'libexec/<exe:graalpy-polyglot-get>'],
            jar_distributions=['graalpython:GRAALPYTHON-LAUNCHER', 'sdk:MAVEN_DOWNLOADER'],
            main_class=GRAALPYTHON_MAIN_CLASS,
            build_args=[
                '-J-Xms14g', # GR-46399: libpythonvm needs more than the default minimum of 8 GB to be built
                '-H:+DetectUserDirectoriesInImageHeap',
                '-H:-CopyLanguageResources',
                '-Dpolyglot.python.PosixModuleBackend=native',
                '-Dpolyglot.python.Sha3ModuleBackend=native',
            ],
            language='python',
            default_vm_args=[
                '--vm.Xss16777216', # request 16M of stack
            ],
        ),
    ],
    priority=5,
    stability="experimental",
))


# ----------------------------------------------------------------------------------------------------------------------
#
# post init
#
# ----------------------------------------------------------------------------------------------------------------------
def _register_vms(namespace):
    # cpython
    python_vm_registry.add_vm(CPythonVm(config_name=CONFIGURATION_DEFAULT), SUITE)

    # pypy
    python_vm_registry.add_vm(PyPyVm(config_name=CONFIGURATION_DEFAULT), SUITE)

    # jython
    python_vm_registry.add_vm(JythonVm(config_name=CONFIGURATION_DEFAULT), SUITE)

    # graalpython
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_DEFAULT), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_INTERPRETER, extra_polyglot_args=[
        '--experimental-options', '--engine.Compilation=false'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_DEFAULT_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context',
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_INTERPRETER_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context', '--engine.Compilation=false'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_DEFAULT_MULTI_TIER, extra_polyglot_args=[
        '--experimental-options', '--engine.MultiTier=true',
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_SANDBOXED, extra_polyglot_args=SANDBOXED_OPTIONS), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE, extra_polyglot_args=[
        '--experimental-options', '--python.HPyBackend=JNI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_INTERPRETER, extra_polyglot_args=[
        '--experimental-options', '--engine.Compilation=false', '--python.HPyBackend=JNI']), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_SANDBOXED_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context'] + SANDBOXED_OPTIONS), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context', '--python.HPyBackend=JNI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_INTERPRETER_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context', '--engine.Compilation=false', '--python.HPyBackend=JNI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_MULTI_TIER, extra_polyglot_args=[
        '--experimental-options', '--engine.MultiTier=true', '--python.HPyBackend=JNI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_PANAMA, extra_polyglot_args=[
        '--experimental-options', '--python.UsePanama=true'
    ]), SUITE, 10)

    # java embedding driver
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_MULTI,
                                extra_polyglot_args=['-multi-context']), SUITE, 10)
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_MULTI_SHARED,
                                extra_polyglot_args=['-multi-context', '-shared-engine']), SUITE, 10)
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI,
                                extra_polyglot_args=['-multi-context', '-interpreter']), SUITE, 10)
    python_java_embedding_vm_registry.add_vm(
        GraalPythonJavaDriverVm(config_name=CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI_SHARED,
                                extra_polyglot_args=['-multi-context', '-interpreter', '-shared-engine']), SUITE, 10)


def _register_bench_suites(namespace):
    for py_bench_suite in PythonBenchmarkSuite.get_benchmark_suites(BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    for py_bench_suite in PythonJavaEmbeddingBenchmarkSuite.get_benchmark_suites(JAVA_DRIVER_BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    for py_bench_suite in PythonVmWarmupBenchmarkSuite.get_benchmark_suites(WARMUP_BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    for java_bench_suite in PythonInteropBenchmarkSuite.get_benchmark_suites(JBENCHMARKS):
        mx_benchmark.add_bm_suite(java_bench_suite)


class CharsetFilteringPariticpant:
    """
    Remove charset providers from the resulting JAR distribution. Done to avoid libraries (icu4j-charset)
    adding their charsets implicitly to native image. We need to add them explicitly in a controlled way.
    """
    def __opened__(self, archive, src_archive, services):
        self.__services = services

    def __closing__(self):
        self.__services.pop('java.nio.charset.spi.CharsetProvider', None)


def warn_about_old_hardcoded_version():
    """
    Ensure hardcoded versions everywhere are what we expect, either matching the master version
    or one of the latest releases.
    """
    graal_major = int(GRAAL_VERSION.split(".")[0])
    graal_minor = int(GRAAL_VERSION.split(".")[1])

    def hardcoded_ver_is_too_far_behind_master(m):
        hardcoded_major = int(m.group(1).split(".")[0])
        if hardcoded_major < graal_major:
            if graal_minor > 0 or hardcoded_major < graal_minor - 1:
                return f"Hardcoded version in `{m.group().strip()}` is too far behind {graal_major}.{graal_minor}. Update it to the latest released version."

    def hardcoded_ver_is_behind_major_minor(m):
        if m.group(1) != GRAAL_VERSION_MAJ_MIN:
            return f"Hardcoded version in `{m.group().strip()}` should have {GRAAL_VERSION_MAJ_MIN} as <major>.<minor> version."

    files_with_versions = {
        "graalpython/graalpy-maven-plugin/pom.xml": {
            r"^  <version>(\d+\.\d+)(?:\.\d+)*</version>" : hardcoded_ver_is_behind_major_minor,
            r'<graalpy.version>(\d+\.\d+)(?:\.\d+)*</graalpy.version>' : hardcoded_ver_is_behind_major_minor
        },
        "graalpython/com.oracle.graal.python.test.integration/pom.xml": {
            r'<com.oracle.graal.python.test.polyglot.version>(\d+\.\d+)(?:\.\d+)*' : hardcoded_ver_is_behind_major_minor,
        },
        "graalpython/graalpy-archetype-polyglot-app/pom.xml": {
            r"^  <version>(\d+\.\d+)(?:\.\d+)*</version>" : hardcoded_ver_is_behind_major_minor,
        },
        "graalpython/graalpy-jbang/examples/hello.java": {
            r"//DEPS org.graalvm.python:python[^:]*:\${env.GRAALPY_VERSION:(\d+\.\d+)(?:\.\d+)*" : hardcoded_ver_is_too_far_behind_master,
        },
        "graalpython/graalpy-jbang/templates/graalpy-template_local_repo.java.qute": {
            r"//DEPS org.graalvm.python:python[^:]*:\${env.GRAALPY_VERSION:(\d+\.\d+)(?:\.\d+)*" : hardcoded_ver_is_too_far_behind_master,
        },
        "graalpython/graalpy-jbang/templates/graalpy-template.java.qute": {
            r"//DEPS org.graalvm.python:python[^:]*:\${env.GRAALPY_VERSION:(\d+\.\d+)(?:\.\d+)*" : hardcoded_ver_is_too_far_behind_master,
        },
        "graalpython/graalpy-archetype-polyglot-app/src/main/resources/archetype-resources/pom.xml": {
            r'<graalpy.version>(\d+\.\d+)(?:\.\d+)*</graalpy.version>' : hardcoded_ver_is_behind_major_minor,
        },
    }
    replacements = set()
    for path, patterns in files_with_versions.items():
        full_path = os.path.join(SUITE.dir, path)
        with open(full_path, "r", encoding="utf-8") as f:
            content = f.read()
        for pattern, test in patterns.items():
            pattern = re.compile(pattern, flags=re.M)
            start = 0
            while m := pattern.search(content, start):
                mx.logvv(f"[{SUITE.name}] {path} with hardcoded version `{m.group()}'")
                if msg := test(m):
                    replacements.add((path, msg))
                start = m.end()
    if replacements:
        mx.abort("\n".join([
            ": ".join(r) for r in replacements
        ]))


def mx_post_parse_cmd_line(namespace):
    # all projects are now available at this time
    _register_vms(namespace)
    _register_bench_suites(namespace)
    mx_graalpython_python_benchmarks.register_python_benchmarks()

    for dist in mx.suite('graalpython').dists:
        if hasattr(dist, 'set_archiveparticipant'):
            dist.set_archiveparticipant(CharsetFilteringPariticpant())


def python_coverage(args):
    "Generate coverage report for our unittests"
    parser = ArgumentParser(prog='mx python-coverage')
    parser.add_argument('--jacoco', action='store_true', help='do generate Jacoco coverage')
    parser.add_argument('--truffle', action='store_true', help='do generate Truffle coverage')
    args = parser.parse_args(args)

    # do not endlessly rebuild tests
    mx.command_function("build")(["--dep", "com.oracle.graal.python.test"])
    env = extend_os_env(
        GRAALPYTHON_MX_DISABLE_REBUILD="True",
        GRAALPYTEST_FAIL_FAST="False",
    )

    global _COLLECTING_COVERAGE
    _COLLECTING_COVERAGE = True

    if args.jacoco:
        jacoco_args = [
            '--jacoco-omit-excluded',
            '--jacoco-generic-paths',
            '--jacoco-omit-src-gen',
            '--jacocout', 'coverage',
            '--jacoco-format', 'lcov',
        ]
        if os.environ.get("TAGGED_UNITTEST_PARTIAL"):
            jacoco_gates = (
                GraalPythonTags.tagged,
            )
        else:
            jacoco_gates = (
                GraalPythonTags.junit,
                GraalPythonTags.unittest,
                GraalPythonTags.unittest_multi,
                GraalPythonTags.unittest_jython,
                GraalPythonTags.unittest_hpy,
            )

        mx.run_mx([
            '--strict-compliance',
            '--primary', 'gate',
            '-B=--force-deprecation-as-warning-for-dependencies',
            '--strict-mode',
            '--tags', ",".join(['%s'] * len(jacoco_gates)) % jacoco_gates,
        ] + jacoco_args, env=env)
        mx.run_mx([
            '--strict-compliance',
            '--kill-with-sigquit',
            'jacocoreport',
            '--format', 'lcov',
            '--omit-excluded',
            'coverage',
            '--generic-paths',
            '--exclude-src-gen',
        ], env=env)
    if args.truffle:
        executable = graalpy_standalone_jvm()
        file_filter = f"*lib-graalpython*,*graalpython/include*,*com.oracle.graal.python.cext*,*lib/graalpy{graal_version_short()}*,*include/python{py_version_short()}*"
        if os.environ.get("TAGGED_UNITTEST_PARTIAL"):
            variants = [
                {"tagged": True},
            ]
        else:
            variants = [
                {"args": []},
                {"args": ["--python.EmulateJython"], "paths": ["test_interop.py"]},
                {"hpy": True},
                # {"args": ["--llvm.managed"]},
            ]

        common_coverage_args = [
            "--experimental-options",
            "--llvm.lazyParsing=false",
            "--python.DisableFrozenModules",  # To have proper source information about lib-graalpython
            "--coverage",
            "--coverage.TrackInternal",
            f"--coverage.FilterFile={file_filter}",
            "--coverage.Output=lcov",
        ]
        for kwds in variants:
            variant_str = re.sub(r"[^a-zA-Z]", "_", str(kwds))
            outfile = os.path.join(SUITE.dir, "coverage_%s_$UUID$.lcov" % variant_str)
            if os.path.exists(outfile):
                os.unlink(outfile)
            extra_args = [
                *common_coverage_args,
                f"--coverage.OutputFile={outfile}",
            ]
            env['GRAAL_PYTHON_ARGS'] = " ".join(extra_args)
            env['ENABLE_THREADED_GRAALPYTEST'] = "false"
            # deselect some tagged unittests that hang with coverage enabled
            env['TAGGED_UNITTEST_SELECTION'] = "~test_multiprocessing_spawn,test_multiprocessing_main_handling,test_multiprocessing_graalpy"
            if kwds.pop("tagged", False):
                run_tagged_unittests(executable, env=env, nonZeroIsFatal=False)
            elif kwds.pop("hpy", False):
                run_hpy_unittests(executable, env=env, nonZeroIsFatal=False, timeout=5*60*60) # hpy unittests are really slow under coverage
            else:
                run_python_unittests(executable, env=env, nonZeroIsFatal=False, timeout=3600, **kwds) # pylint: disable=unexpected-keyword-arg;

        # generate a synthetic lcov file that includes all sources with 0
        # coverage. this is to ensure all sources actuall show up - otherwise,
        # only loaded sources will be part of the coverage
        with tempfile.NamedTemporaryFile(mode="w", suffix='.py') as f:
            f.write(dedent(f"""
                import os

                for dirpath, dirnames, filenames in os.walk({os.path.join(SUITE.dir, "graalpython", "lib-graalpython")!r}):
                    if "test" in dirnames:
                        dirnames.remove("test")
                    if "tests" in dirnames:
                        dirnames.remove("tests")
                    for filename in filenames:
                        if filename.endswith(".py"):
                            fullname = os.path.join(dirpath, filename)
                            with open(fullname, 'rb') as f:
                                try:
                                    compile(f.read(), fullname, 'exec')
                                except BaseException as e:
                                    print('Could not compile', fullname, e)
            """))
            f.flush()
            lcov_file = 'zero.lcov'
            try:
                # the coverage instrument complains if the file already exists
                os.unlink(lcov_file)
            except OSError:
                pass
            # We use "java" POSIX backend, because the supporting so library is missing in the dev build
            with _dev_pythonhome_context():
                mx.run([
                    executable,
                    "-S",
                    *common_coverage_args,
                    "--python.PosixModuleBackend=java",
                    f"--coverage.OutputFile={lcov_file}",
                    f.name
                ], env=None)

        home_launcher = os.path.dirname(os.path.dirname(executable))
        suite_dir = SUITE.dir
        if suite_dir.endswith("/"):
            suite_dir = suite_dir[:-1]
        suite_parent = os.path.dirname(SUITE.dir)
        if not suite_parent.endswith("/"):
            suite_parent = f"{suite_parent}/"
        # merge all generated lcov files
        outputlcov = "lcov.info"
        if os.path.exists(outputlcov):
            os.unlink(outputlcov)
        cmdargs = ["/usr/bin/env", "lcov", "-o", outputlcov]
        for f in os.listdir(SUITE.dir):
            if f.endswith(".lcov") and os.path.getsize(f):
                with open(f) as lcov_file:
                    lcov = lcov_file.read()
                # Normalize graalpython paths to be relative to a graalpython checkout
                lcov = lcov.replace(home_launcher, "graalpython/graalpython").replace(suite_dir, "graalpython").replace(suite_parent, "")
                # link our generated include paths back to the sources
                lcov = lcov.replace("graalpython/graalpython/include/", "graalpython/graalpython/com.oracle.graal.python.cext/include/")
                # Map distribution paths to source paths
                lcov = lcov.replace(f"graalpython/graalpython/lib/graalpy{graal_version_short()}", "graalpython/graalpython/lib-graalpython")
                lcov = lcov.replace(f"graalpython/graalpython/lib/python{py_version_short()}", "graalpython/graalpython/lib-python/3")
                lcov = lcov.replace(f"graalpython/graalpython/include/python{py_version_short()}", "graalpython/graalpython/com.oracle.graal.python.cext/include/")
                with open(f, 'w') as lcov_file:
                    lcov_file.write(lcov)

                # check the file contains valid records
                if mx.run(["/usr/bin/env", "lcov", "--summary", f], nonZeroIsFatal=False) == 0:
                    cmdargs += ["-a", f]
                else:
                    mx.warn(f"Skipping {f}")

        # actually run the merge command
        mx.run(cmdargs)

    # upload coverage data
    out = mx.OutputCapture()
    mx.run_mx(['sversions', '--print-repositories', '--json'], out=out)
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8') as f:
        f.write(out.data)
        f.flush()
        print(f"Associated data", out.data, sep="\n")
        mx.run(['coverage-uploader.py', '--associated-repos', f.name])


class GraalpythonBuildTask(mx.ProjectBuildTask):
    class PrefixingOutput():
        def __init__(self, prefix, printfunc):
            self.prefix = "[" + prefix + "] "
            self.printfunc = printfunc

        def __call__(self, line):
            # n.b.: mx already sends us the output line-by-line
            self.printfunc(self.prefix + line.rstrip())

    def __init__(self, args, project):
        jobs = min(mx.cpu_count(), 8)
        super().__init__(args, jobs, project)

    def __str__(self):
        return 'Building project {}'.format(self.subject.name)

    def build(self):
        args = [mx_subst.path_substitutions.substitute(a, dependency=self) for a in self.subject.args]
        return self.run(args)

    def run(self, args, env=None, cwd=None, **kwargs):
        cwd = cwd or os.path.join(self.subject.get_output_root(), "mxbuild_temp")

        if os.path.exists("/dev/null"):
            pycache_dir = "/dev/null"
        else:
            pycache_dir = os.path.join(self.subject.get_output_root(), "__pycache__")

        if mx._opts.verbose:
            args.insert(0, "-v")
        else:
            # always add "-q" if not verbose to suppress hello message
            args.insert(0, "-q")

        args[:0] = [
            f"--python.PyCachePrefix={pycache_dir}",
            "--python.DisableFrozenModules",
            "-B",
            "-S"
        ]
        mx_util.ensure_dir_exists(cwd)

        env = env.copy() if env else os.environ.copy()
        env.update(self.subject.getBuildEnv())
        args.insert(0, '--PosixModuleBackend=java')
        jdk = mx.get_jdk()  # Don't get JVMCI, it might not have finished building by this point
        rc = do_run_python(args, jdk=jdk, env=env, cwd=cwd, minimal=True, out=self.PrefixingOutput(self.subject.name, mx.log), err=self.PrefixingOutput(self.subject.name, mx.log_error), **kwargs)

        shutil.rmtree(cwd) # remove the temporary build files
        # if we're just running style tests, this is allowed to fail
        if os.environ.get("BUILD_NAME") == "python-style":
            return 0
        return min(rc, 1)

    def src_dir(self):
        return self.subject.dir

    def newestOutput(self):
        return None

    def needsBuild(self, newestInput):
        if self.args.force:
            return True, 'forced build'
        if not os.path.exists(self.subject.get_output_root()):
            return True, 'inexisting output dir'
        return False, 'unimplemented'

    def clean(self, forBuild=False):
        if forBuild == "reallyForBuild":
            try:
                shutil.rmtree(self.subject.get_output_root())
            except BaseException:
                return 1
        return 0


class GraalpythonProject(mx.ArchivableProject):
    def __init__(self, suite, name, subDir, srcDirs, deps, workingSets, d, theLicense=None, **kwargs): # pylint: disable=super-init-not-called
        context = 'project ' + name
        self.buildDependencies = mx.Suite._pop_list(kwargs, 'buildDependencies', context)
        mx.Project.__init__(self, suite, name, subDir, srcDirs, deps, workingSets, d, theLicense, **kwargs)

    def getOutput(self, replaceVar=mx_subst.results_substitutions):
        return self.get_output_root()

    def output_dir(self):
        return self.getOutput()

    def archive_prefix(self):
        return ""

    def getResults(self):
        return [x[0] for x in self.getArchivableResults(use_relpath=False)]

    def getArchivableResults(self, use_relpath=True, single=False):
        if single:
            raise ValueError("single not supported")
        output = self.getOutput()
        for root, _, files in os.walk(output):
            for name in files:
                fullname = os.path.join(root, name)
                if use_relpath:
                    yield fullname, os.path.relpath(fullname, output)
                else:
                    yield fullname, name

    def getBuildTask(self, args):
        return GraalpythonBuildTask(args, self)

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        ret = {}
        if hasattr(self, 'buildEnv'):
            for key, value in self.buildEnv.items():
                ret[key] = replaceVar.substitute(value, dependency=self)
        return ret


orig_clean = mx.command_function("clean")
def python_clean(args):
    orig_clean(args)
    count = 0
    for path in os.walk(SUITE.dir):
        for file in glob.iglob(os.path.join(path[0], '*.pyc')):
            count += 1
            os.remove(file)

    if count > 0:
        print('Cleaning', count, "`*.pyc` files...")

def update_hpy_import_cmd(args):
    """Update our import of HPy sources."""
    parser = ArgumentParser('mx python-update-hpy-import')
    parser.add_argument('--pull', action='store_true', help='Perform a pull of the HPy repo first.', required=False)
    parser.add_argument('hpy_repo', metavar='HPY_REPO', help='Path to the HPy repo to import from.')
    parsed_args, _ = parser.parse_known_args(args)

    join = os.path.join
    vc = SUITE.vc

    current_branch = vc.active_branch(SUITE.dir)
    if current_branch == "master":
        mx.abort("updating imports should be done on a branch")
    if vc.isDirty(SUITE.dir):
        mx.abort("updating imports should be done on a clean branch")

    hpy_repo_path = parsed_args.hpy_repo

    # do sanity check of the HPy repo
    hpy_repo_include_dir = join(hpy_repo_path, "hpy", "devel", "include")
    hpy_repo_src_dir = join(hpy_repo_path, "hpy", "devel", "src")
    hpy_repo_debug_dir = join(hpy_repo_path, "hpy", "debug")
    hpy_repo_trace_dir = join(hpy_repo_path, "hpy", "trace")
    hpy_repo_test_dir = join(hpy_repo_path, "test")
    for d in [hpy_repo_path, hpy_repo_include_dir, hpy_repo_src_dir, hpy_repo_test_dir]:
        if not os.path.isdir(d):
            mx.abort("HPy import repo is missing directory {}".format(d))

    # We should use 'SUITE.vc' here because HPy always uses Git and this may be different from 'SUITE.vc'.
    vc_git = mx.vc_system("git")

    # Now that we know the 'hpy_repo_path' looks sane, do a pull if requested.
    if parsed_args.pull:
        if not vc_git.is_this_vc(hpy_repo_path):
            mx.abort("Cannot perform pull for HPy repo because {} is not a valid Git repo.".format(hpy_repo_path))
        vc_git.pull(hpy_repo_path, update=True)

    # determine short revision of HPy
    import_version = vc_git.git_command(hpy_repo_path, ["rev-parse", "--short", "HEAD"]).strip()
    mx.log("Determined HPy revision {}".format(import_version))

    if vc_git.isDirty(hpy_repo_path):
        res = input("WARNING: your HPy repo is not clean. Do you want to proceed? (n/y) ")
        if str(res).strip().lower() != "y":
            return

    # switch to the HPy import orphan branch
    vc.git_command(SUITE.dir, ["checkout", HPY_IMPORT_ORPHAN_BRANCH_NAME])
    assert not SUITE.vc.isDirty(SUITE.dir)

    def import_file(src_file, dest_file):
        mx.logv("Importing HPy file {} to {}".format(src_file, dest_file))

        # ensure that relative parent directories already exist (ignore existing)
        os.makedirs(os.path.dirname(dest_file), exist_ok=True)

        # copy file (overwrite existing)
        mx.copyfile(src_file, dest_file)
        # we may copy ignored files
        vc.add(SUITE.dir, dest_file, abortOnError=False)

    def import_files(from_dir, to_dir, exclude=lambda x: False):
        mx.log("Importing HPy files from {}".format(from_dir))
        for dirpath, _, filenames in os.walk(from_dir):
            relative_dir_path = os.path.relpath(dirpath, start=from_dir)
            for filename in filenames:
                src_file = join(dirpath, filename)
                relative_src_file = join(relative_dir_path, filename)
                if not exclude(relative_src_file):
                    dest_file = join(to_dir, relative_src_file)
                    import_file(src_file, dest_file)

    def remove_inexistent_file(src_file, dest_file):
        if not os.path.exists(dest_file):
            mx.logv("Removing file {} since {} does not exist".format(src_file, dest_file))
            vc.git_command(SUITE.dir, ["rm", src_file])

    def remove_inexistent_files(hpy_dir, our_dir):
        mx.log("Looking for removed files in {} (HPy reference dir {})".format(our_dir, hpy_dir))
        for dirpath, _, filenames in os.walk(our_dir):
            relative_dir_path = os.path.relpath(dirpath, start=our_dir)
            for filename in filenames:
                src_file = join(dirpath, filename)
                dest_file = join(hpy_dir, relative_dir_path, filename)
                remove_inexistent_file(src_file, dest_file)

    def exclude_subdir(subdir):
        return lambda relpath: relpath.startswith(subdir)

    def exclude_files(*files):
        return lambda relpath: str(os.path.normpath(relpath)) in files

    # headers go into 'com.oracle.graal.python.hpy.llvm/include'
    header_dest = join(mx.project("com.oracle.graal.python.hpy.llvm").dir, "include")

    # copy 'hpy/devel/__init__.py' to 'lib-graalpython/module/hpy/devel/__init__.py'
    dest_devel_file = join(_get_core_home(), "modules", "hpy", "devel", "__init__.py")
    src_devel_file = join(hpy_repo_path, "hpy", "devel", "__init__.py")
    if not os.path.exists(src_devel_file):
        SUITE.vc.git_command(SUITE.dir, ["reset", "--hard"])
        SUITE.vc.git_command(SUITE.dir, ["checkout", "-"])
        mx.abort("File '{}' is missing but required.".format(src_devel_file))
    import_file(src_devel_file, dest_devel_file)

    # 'version.py' goes to 'lib-graalpython/module/hpy/devel/'
    dest_version_file = join(_get_core_home(), "modules", "hpy", "devel", "version.py")
    src_version_file = join(hpy_repo_path, "hpy", "devel", "version.py")
    if not os.path.exists(src_version_file):
        SUITE.vc.git_command(SUITE.dir, ["reset", "--hard"])
        SUITE.vc.git_command(SUITE.dir, ["checkout", "-"])
        mx.abort("File 'version.py' is not available. Did you forget to run 'setup.py build' ?")
    import_file(src_version_file, dest_version_file)

    # 'abitag.py' goes to 'lib-graalpython/module/hpy/devel/'
    dest_abitag_file = join(_get_core_home(), "modules", "hpy", "devel", "abitag.py")
    src_abitag_file = join(hpy_repo_path, "hpy", "devel", "abitag.py")
    if not os.path.exists(src_abitag_file):
        SUITE.vc.git_command(SUITE.dir, ["reset", "--hard"])
        SUITE.vc.git_command(SUITE.dir, ["checkout", "-"])
        mx.abort("File 'abitag.py' is not available. Did you forget to run 'setup.py build' ?")
    import_file(src_abitag_file, dest_abitag_file)

    # copy headers from .../hpy/hpy/devel/include' to 'header_dest'
    # but exclude subdir 'cpython' (since that's only for CPython)
    import_files(hpy_repo_include_dir, header_dest)
    remove_inexistent_files(hpy_repo_include_dir, header_dest)


    # runtime sources go into 'lib-graalpython/module/hpy/devel/src'
    runtime_files_dest = join(_get_core_home(), "modules", "hpy", "devel", "src")
    import_files(hpy_repo_src_dir, runtime_files_dest)
    remove_inexistent_files(hpy_repo_src_dir, runtime_files_dest)

    # 'ctx_tracker.c' also goes to 'com.oracle.graal.python.jni/src/ctx_tracker.c'
    tracker_file_src = join(hpy_repo_src_dir, "runtime", "ctx_tracker.c")
    if not os.path.exists(tracker_file_src):
        mx.abort("File '{}' is missing but required.".format(tracker_file_src))
    jni_project_dir = mx.project("com.oracle.graal.python.jni").dir
    tracker_file_dest = join(jni_project_dir, "src", "ctx_tracker.c")
    import_file(tracker_file_src, tracker_file_dest)

    # tests go to 'com.oracle.graal.python.hpy.test/src/hpytest'
    test_files_dest = join(mx.dependency(HPY_TEST_PROJECT).dir, "src", "hpytest")
    import_files(hpy_repo_test_dir, test_files_dest)
    remove_inexistent_files(hpy_repo_test_dir, test_files_dest)

    # debug Python sources go into 'lib-graalpython/module/hpy/debug'
    debug_files_dest = join(_get_core_home(), "modules", "hpy", "debug")
    import_files(hpy_repo_debug_dir, debug_files_dest, exclude_subdir("src"))
    remove_inexistent_files(hpy_repo_debug_dir, debug_files_dest)

    # debug mode goes into 'com.oracle.graal.python.jni/src/debug'
    debugctx_src = join(hpy_repo_debug_dir, "src")
    debugctx_dest = join(jni_project_dir, "src", "debug")
    debugctx_hdr = join(debugctx_src, "include", "hpy_debug.h")
    import_files(debugctx_src, debugctx_dest, exclude_files(
        "autogen_debug_ctx_call.i", "debug_ctx_cpython.c", debugctx_hdr))
    import_file(debugctx_hdr, join(debugctx_dest, "hpy_debug.h"))

    # trace Python sources go into 'lib-graalpython/module/hpy/trace'
    trace_files_dest = join(_get_core_home(), "modules", "hpy", "trace")
    import_files(hpy_repo_debug_dir, trace_files_dest, exclude_subdir("src"))
    remove_inexistent_files(hpy_repo_trace_dir, trace_files_dest)

    # trace mode goes into 'com.oracle.graal.python.jni/src/trace'
    tracectx_src = join(hpy_repo_trace_dir, "src")
    tracectx_dest = join(jni_project_dir, "src", "trace")
    tracectx_hdr = join(tracectx_src, "include", "hpy_trace.h")
    import_files(tracectx_src, tracectx_dest, exclude_files(tracectx_hdr))
    import_file(tracectx_hdr, join(tracectx_dest, "hpy_trace.h"))

    # import 'version.py' by path and read '__version__'
    from importlib import util
    spec = util.spec_from_file_location("version", dest_version_file)
    version_module = util.module_from_spec(spec)
    spec.loader.exec_module(version_module)
    imported_version = version_module.__version__

    SUITE.vc.git_command(SUITE.dir, ["add", header_dest, test_files_dest, runtime_files_dest, tracker_file_dest])
    input("Check that the updated files look as intended, then press RETURN...")
    SUITE.vc.commit(SUITE.dir, "Update HPy inlined files: %s" % import_version)
    SUITE.vc.git_command(SUITE.dir, ["checkout", "-"])
    SUITE.vc.git_command(SUITE.dir, ["merge", HPY_IMPORT_ORPHAN_BRANCH_NAME])

    # update PKG-INFO version
    pkg_info_file = join(_get_core_home(), "modules", "hpy.egg-info", "PKG-INFO")
    with open(pkg_info_file, "w") as f:
        f.write("Metadata-Version: 2.1\n"
                "Name: hpy\n"
                "Version: {}\n"
                "Summary: UNKNOWN\n"
                "Home-page: UNKNOWN\n"
                "License: UNKNOWN\n"
                "Description: UNKNOWN\n"
                "Platform: UNKNOWN\n"
                "Provides-Extra: dev\n".format(imported_version).strip())


def run_leak_launcher(input_args):
    args = input_args
    keep_dumps_on_success = False
    if '--keep-dump-on-success' in input_args:
        args.remove('--keep-dump-on-success')
        args.append('--keep-dump')
        keep_dumps_on_success = True
    print(shlex.join(["mx", "python-leak-test", *input_args]))

    args = ["--lang", "python",
            "--forbidden-class", "com.oracle.graal.python.builtins.objects.object.PythonObject",
            "--python.ForceImportSite", "--python.TRegexUsesSREFallback=false"]
    args += input_args
    args = [
        "--keep-dump",
        "--experimental-options",
        *args,
    ]

    env = os.environ.copy()

    dists = ['GRAALPYTHON', 'GRAALPYTHON_RESOURCES', 'TRUFFLE_NFI', 'SULONG_NATIVE', 'GRAALPYTHON_UNIT_TESTS']

    vm_args, graalpython_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    vm_args += mx.get_runtime_jvm_args(dists)
    vm_args += ['--add-exports', 'org.graalvm.py/com.oracle.graal.python.builtins=ALL-UNNAMED']
    vm_args.append('-Dpolyglot.engine.WarnInterpreterOnly=false')
    jdk = get_jdk()
    vm_args.append("com.oracle.graal.python.test.advanced.LeakTest")
    out = mx.OutputCapture()
    retval = mx.run_java(vm_args + graalpython_args, jdk=jdk, env=env, nonZeroIsFatal=False, out=mx.TeeOutputCapture(out))
    dump_paths = re.findall(r'Dump file: (\S+)', out.data.strip())
    if retval == 0:
        print("PASSED")
        if dump_paths and not keep_dumps_on_success:
            print("Removing heapdump for passed test")
            for p in dump_paths:
                os.unlink(p)
    else:
        print("FAILED")
        if 'CI' in os.environ and dump_paths:
            for i, dump_path in enumerate(dump_paths):
                save_path = os.path.join(SUITE.dir, "dumps", f"leak_test{i}")
                try:
                    os.makedirs(save_path)
                except OSError:
                    pass
                dest = shutil.copy(dump_path, save_path)
                print(f"Heapdump file {dump_path} kept in {dest}")
        mx.abort(1)


def no_return(fn):
    @wraps(fn)
    def inner(*args, **kwargs):
        fn(*args, **kwargs)
    return inner


def host_inlining_log_extract_method(args_in):
    parser = ArgumentParser(description="Extracts single method from host inlining log file. "
                                 "Result, when saved to file, can be visualized with: java scripts/HostInliningVisualizer.java filename")
    parser.add_argument("filename", help="file with host inlining log")
    parser.add_argument("method", help="name of a method to extract")
    parser.add_argument("-f", "--fields",
                        help="fields to select from the list with details, use Python subscript syntax, "
                             "default: '-1:' (i.e., the last field: reason for the [non-]inlining decision)", default="-1:")
    args = parser.parse_args(args_in)

    start = 'Context: HostedMethod<' + args.method + ' '
    result = []
    inside = False
    remove = [
        'com.oracle.truffle.api.impl.',
        'com.oracle.graal.python.nodes.bytecode.',
        'com.oracle.graal.python.nodes.',
        'com.oracle.graal.python.']
    with open(args.filename) as file:
        while line := file.readline():
            if inside:
                if line.strip() == '':
                    print('\n'.join(result))
                    return 0
                match = re.search(r'\[inlined.*\]', line)
                if match:
                    details = match.group().split(',')
                    details = ', '.join(eval(f"details[{args.fields}]"))  #pylint: disable=eval-used
                    line = line[:match.start()].rstrip() + f' [{details}]'
                for x in remove:
                    line = line.replace(x, '')
                result.append(line)
            elif start in line:
                inside = True
    print("Method not found in the log")
    return 1


class PythonMxUnittestConfig(mx_unittest.MxUnittestConfig):
    # We use global state, which influences what this unit-test config is going to do
    # The global state can be adjusted before a test run to achieve a different tests configuration
    useResources = True # Whether to use resources, or language home of filesystem
    # Possible future extensions: useSulong = True

    def apply(self, config):
        (vmArgs, mainClass, mainClassArgs) = config
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/com.oracle.truffle.api.impl=ALL-UNNAMED'])  # for TruffleRunner/TCK
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.py/*=ALL-UNNAMED'])  # for Python internals
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.py.launcher/*=ALL-UNNAMED'])  # for Python launcher internals
        if not PythonMxUnittestConfig.useResources:
            vmArgs.append('-Dorg.graalvm.language.python.home=' + mx.dependency("GRAALPYTHON_GRAALVM_SUPPORT").get_output())
        return (vmArgs, mainClass, mainClassArgs)

    def processDeps(self, deps):
        if PythonMxUnittestConfig.useResources:
            deps.add(mx.distribution('GRAALPYTHON_RESOURCES', fatalIfMissing=True))


mx_unittest.register_unittest_config(PythonMxUnittestConfig('python-internal'))

def graalpy_standalone_wrapper(args_in):
    parser = ArgumentParser(description='Builds GraalPy standalone of give configuration and prints path to its launcher.')
    parser.add_argument('type', nargs='?', default='jvm', choices=['jvm', 'native'])
    parser.add_argument('edition', nargs='?', default='ce', choices=['ce', 'ee'])
    parser.add_argument('--no-build', action='store_true',
                        help="Doesn't build the standalone, only prints the patch to its launcher")
    args = parser.parse_args(args_in)
    if args.edition == 'ee':
        if not mx.suite('graalpython-enterprise', fatalIfMissing=False):
            mx.abort("You must add --dynamicimports graalpython-enterprise for EE edition")
    print(graalpy_standalone(args.type, enterprise=args.edition == 'ee', build=not args.no_build))


# ----------------------------------------------------------------------------------------------------------------------
#
# register the suite commands (if any)
#
# ----------------------------------------------------------------------------------------------------------------------
full_python_cmd = [full_python, '[--hosted, run on the currently executing JVM from source tree, default is to run from GraalVM] [Python args|@VM options]']
mx.update_commands(SUITE, {
    'python': full_python_cmd,
    'python3': full_python_cmd,
    'deploy-binary-if-master': [deploy_binary_if_main, ''],
    'python-gate': [python_gate, '--tags [gates]'],
    'python-update-import': [update_import_cmd, '[--no-pull] [--no-push] [import-name, default: truffle]'],
    'python-style': [python_style_checks, '[--fix] [--no-spotbugs]'],
    'python-svm': [no_return(python_svm), ''],
    'python-jvm': [no_return(python_jvm), ''],
    'graalpy-standalone': [graalpy_standalone_wrapper, '[jvm|native] [ce|ee] [--no-build]'],
    'python-gvm': [no_return(python_gvm), ''],
    'python-unittests': [python3_unittests, ''],
    'python-compare-unittests': [compare_unittests, ''],
    'python-retag-unittests': [retag_unittests, ''],
    'python-run-cpython-unittest': [run_cpython_test, '[-k TEST_PATTERN] [--svm] [--all] TESTS'],
    'python-update-unittest-tags': [update_unittest_tags, ''],
    'nativebuild': [nativebuild, ''],
    'nativeclean': [nativeclean, ''],
    'python-src-import': [mx_graalpython_import.import_python_sources, ''],
    'python-coverage': [python_coverage, ''],
    'punittest': [punittest, ''],
    'graalpytest': [graalpytest, '[-h] [-v] [--python PYTHON] [-k TEST_PATTERN] [TESTS]'],
    'clean': [python_clean, ''],
    'python-update-hpy-import': [update_hpy_import_cmd, '[--no-pull] PATH_TO_HPY'],
    'bisect-benchmark': [mx_graalpython_bisect.bisect_benchmark, ''],
    'python-leak-test': [run_leak_launcher, ''],
    'python-nodes-footprint': [node_footprint_analyzer, ''],
    'python-checkcopyrights': [python_checkcopyrights, '[--fix]'],
    'host-inlining-log-extract': [host_inlining_log_extract_method, ''],
})
