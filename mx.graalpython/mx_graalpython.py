# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
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
import fnmatch
import getpass
import glob
import itertools
import json
import os
import re
import shlex
import shutil
import sys
from functools import wraps

HPY_IMPORT_ORPHAN_BRANCH_NAME = "hpy-import"

if sys.version_info[0] < 3:
    raise RuntimeError("The build scripts are no longer compatible with Python 2")

import tempfile
import urllib.request as urllib_request
from argparse import ArgumentParser

import mx
import mx_benchmark
import mx_gate
import mx_unittest
import mx_sdk
import mx_subst
import mx_graalpython_bisect
import mx_graalpython_python_benchmarks

from mx_gate import Task
from mx_graalpython_bench_param import PATH_MESO, BENCHMARKS, WARMUP_BENCHMARKS, JBENCHMARKS, PARSER_BENCHMARKS, \
    JAVA_DRIVER_BENCHMARKS
from mx_graalpython_benchmark import PythonBenchmarkSuite, python_vm_registry, CPythonVm, PyPyVm, JythonVm, \
    GraalPythonVm, \
    CONFIGURATION_DEFAULT, CONFIGURATION_SANDBOXED, CONFIGURATION_NATIVE, \
    CONFIGURATION_DEFAULT_MULTI, CONFIGURATION_SANDBOXED_MULTI, CONFIGURATION_NATIVE_MULTI, \
    CONFIGURATION_DEFAULT_MULTI_TIER, CONFIGURATION_NATIVE_MULTI_TIER, \
    PythonInteropBenchmarkSuite, PythonVmWarmupBenchmarkSuite, PythonParserBenchmarkSuite, \
    CONFIGURATION_INTERPRETER, CONFIGURATION_INTERPRETER_MULTI, CONFIGURATION_NATIVE_INTERPRETER, \
    CONFIGURATION_NATIVE_INTERPRETER_MULTI, PythonJavaEmbeddingBenchmarkSuite, python_java_embedding_vm_registry, \
    GraalPythonJavaDriverVm, CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI_SHARED, \
    CONFIGURATION_JAVA_EMBEDDING_INTERPRETER_MULTI, CONFIGURATION_JAVA_EMBEDDING_MULTI_SHARED, \
    CONFIGURATION_JAVA_EMBEDDING_MULTI, CONFIGURATION_DEFAULT_AST, CONFIGURATION_INTERPRETER_AST

if not sys.modules.get("__main__"):
    # workaround for pdb++
    sys.modules["__main__"] = type(sys)("<empty>")


SUITE = mx.suite('graalpython')
SUITE_COMPILER = mx.suite("compiler", fatalIfMissing=False)
SUITE_SULONG = mx.suite("sulong")

GRAALPYTHON_MAIN_CLASS = "com.oracle.graal.python.shell.GraalPythonMain"

SANDBOXED_OPTIONS = ['--llvm.managed', '--llvm.deadPointerProtection=MASK', '--llvm.partialPointerConversion=false', '--python.PosixModuleBackend=java']


def _sibling(filename):
    return os.path.join(os.path.dirname(__file__), filename)


def _get_core_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-graalpython")


def _get_stdlib_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-python", "3")


def _get_capi_home():
    return mx.dependency("com.oracle.graal.python.cext").get_output_root()


def _get_jni_home():
    return mx.distribution("GRAALPYTHON_JNI").get_output()


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
    if SUITE_COMPILER:
        tag = 'jvmci'
    else:
        tag = None
    return mx.get_jdk(tag=tag)


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
    else:
        dists = ['GRAALPYTHON']
    dists += ['TRUFFLE_NFI', 'SULONG_NATIVE']

    vm_args, graalpython_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    if minimal:
        vm_args.insert(0, f"-Dorg.graalvm.language.python.home={_dev_pythonhome()}")
    graalpython_args, additional_dists = _extract_graalpython_internal_options(graalpython_args)
    dists += additional_dists

    if extra_dists:
        dists += extra_dists

    if not os.environ.get("CI"):
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


def _pythonhome_context():
    return set_env(GRAAL_PYTHONHOME=mx.dependency("GRAALPYTHON_GRAALVM_SUPPORT").get_output())


def _dev_pythonhome_context():
    home = os.environ.get("GRAAL_PYTHONHOME", _dev_pythonhome())
    return set_env(GRAAL_PYTHONHOME=home)


def _dev_pythonhome():
    return os.path.join(SUITE.dir, "graalpython")


def punittest(ars):
    """
    Runs GraalPython junit tests and memory leak tests, which can be skipped using --no-leak-tests.

    Any other arguments are forwarded to mx's unittest function. If there is no explicit test filter
    in the arguments array, then we append filter that includes all GraalPython junit tests.
    """
    args = []
    skip_leak_tests = False
    if "--regex" not in ars:
        args += ['--regex', r'(graal\.python)|(com\.oracle\.truffle\.tck\.tests)']
    if "--no-leak-tests" in ars:
        skip_leak_tests = True
        ars.remove("--no-leak-tests")
    args += ars
    with _pythonhome_context():
        mx_unittest.unittest(args)

        if skip_leak_tests:
            return

        common_args = ["--lang", "python",
                       "--forbidden-class", "com.oracle.graal.python.builtins.objects.object.PythonObject",
                       "--python.ForceImportSite", "--python.TRegexUsesSREFallback=false"]

        if not all([
            # test leaks with Python code only
            run_leak_launcher(common_args + ["--code", "pass", ]),
            # test leaks when some C module code is involved
            run_leak_launcher(common_args + ["--code", "import _testcapi, mmap, bz2; print(memoryview(b'').nbytes)"]),
            # test leaks with shared engine Python code only
            run_leak_launcher(common_args + ["--shared-engine", "--code", "pass"]),
            # test leaks with shared engine when some C module code is involved
            run_leak_launcher(common_args + ["--shared-engine", "--code", "import _testcapi, mmap, bz2; print(memoryview(b'').nbytes)"])
        ]):
            mx.abort(1)


PYTHON_ARCHIVES = ["GRAALPYTHON_GRAALVM_SUPPORT"]
PYTHON_NATIVE_PROJECTS = ["com.oracle.graal.python.cext"]


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
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--all', action='store_true')
    parser.add_argument('--gvm', dest='vm', action='store_const', const='gvm')
    parser.add_argument('--svm', dest='vm', action='store_const', const='svm')
    parser.add_argument('-k', dest='tags', nargs='*')
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

    python_args = rest_args + [
        os.path.join(SUITE.dir, "graalpython/com.oracle.graal.python.test/src/tests/run_cpython_test.py"),
    ] + test_args + testfiles
    if args.vm:
        env = os.environ.copy()
        delete_bad_env_keys(env)
        vm = python_gvm() if args.vm == 'gvm' else python_svm()
        with _dev_pythonhome_context():
            mx.run([vm, '--vm.ea', f'--python.CAPI={_get_capi_home()}'] + python_args, env=env)
    else:
        do_run_python(python_args)


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
    parsed_args, remaining_args = parser.parse_known_args(args)
    env = os.environ.copy()
    env.update(
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
    vm = python_svm() if not parsed_args.jvm else python_gvm()
    if parsed_args.jvm:
        args += ['-ea']
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

    tag_exclusions = {
        # This test times out in the gate even though it succeeds locally and in the retagger. Race condition?
        ('test_cprofile.txt', '*graalpython.lib-python.3.test.test_cprofile.CProfileTest.test_run_profile_as_module'),
        # The following two try to read bytecode and fail randomly as our co_code is changing
        ('test_modulefinder.txt', '*graalpython.lib-python.3.test.test_modulefinder.ModuleFinderTest.test_bytecode'),
        ('test_modulefinder.txt', '*graalpython.lib-python.3.test.test_modulefinder.ModuleFinderTest.test_relative_imports_4'),
        # Temporarily disabled due to object identity or race condition (GR-24863)
        ('test_weakref.txt', '*graalpython.lib-python.3.test.test_weakref.MappingTestCase.test_threaded_weak_key_dict_deepcopy'),
        # These tests are *inconsistently* triggering IllegalStateException("Coverage Tracker is already tracking") in com.oracle.truffle.tools.coverage.CoverageTracker. Race condition?
        ('test_trace.txt', '*graalpython.lib-python.3.test.test_trace.TestCommandLine.test_run_as_module'),
        ('test_trace.txt', '*graalpython.lib-python.3.test.test_trace.TestCommandLine.test_sys_argv_list'),
        # Temporarily disabled due to transient failures (GR-30641)
        ('test_import.txt', '*graalpython.lib-python.3.test.test_import.__init__.ImportTests.test_concurrency'),
        # Disabled since this fails on Darwin when run in parallel with many other tests
        ('test_imaplib.txt', '*graalpython.lib-python.3.test.test_imaplib.NewIMAPSSLTests.test_login_cram_md5_bytes'),
        ('test_imaplib.txt', '*graalpython.lib-python.3.test.test_imaplib.NewIMAPSSLTests.test_login_cram_md5_plain_text'),
        ('test_imaplib.txt', '*graalpython.lib-python.3.test.test_imaplib.NewIMAPSSLTests.test_valid_authentication_plain_text'),
        ('test_imaplib.txt', '*graalpython.lib-python.3.test.test_imaplib.NewIMAPTests.test_login_cram_md5_bytes'),
        ('test_imaplib.txt', '*graalpython.lib-python.3.test.test_imaplib.NewIMAPTests.test_login_cram_md5_plain_text'),
        ('test_poplib.txt', '*graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_noop'),
        ('test_poplib.txt', '*graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_pass_'),
        ('test_poplib.txt', '*graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_apop_normal'),
        ('test_poplib.txt', '*graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_capa'),
        ('test_poplib.txt', '*graalpython.lib-python.3.test.test_poplib.TestPOP3_TLSClass.test_dele'),
        ('test_weakref.txt', '*graalpython.lib-python.3.test.test_weakref.MappingTestCase.test_weak_keyed_len_cycles'),
        ('test_weakref.txt', '*graalpython.lib-python.3.test.test_weakref.WeakMethodTestCase.test_callback_when_method_dead'),
        ('test_weakref.txt', '*graalpython.lib-python.3.test.test_weakref.WeakMethodTestCase.test_callback_when_object_dead'),
        # Disabled since code object comparison is not stable for us
        ('test_marshal.txt', '*graalpython.lib-python.3.test.test_marshal.InstancingTestCase.testModule'),
        ('test_marshal.txt', '*graalpython.lib-python.3.test.test_marshal.CodeTestCase.test_code'),
        # Disabled since signaling isn't stable during parallel tests
        ('test_faulthandler.txt', '*graalpython.lib-python.3.test.test_faulthandler.FaultHandlerTests.test_sigbus'),
        ('test_faulthandler.txt', '*graalpython.lib-python.3.test.test_faulthandler.FaultHandlerTests.test_sigill'),
        # Disabled due to transient failure
        ('test_multiprocessing_main_handling.txt', '*graalpython.lib-python.3.test.test_multiprocessing_main_handling.SpawnCmdLineTest.test_module_in_package'),
        ('test_multiprocessing_main_handling.txt', '*graalpython.lib-python.3.test.test_multiprocessing_main_handling.SpawnCmdLineTest.test_module_in_subpackage_in_zipfile'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.TestNoForkBomb.test_noforkbomb'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.WithProcessesTestProcess.test_active_children'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.WithProcessesTestProcess.test_error_on_stdio_flush_1'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.WithThreadsTestProcess.test_error_on_stdio_flush_1'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.WithThreadsTestProcess.test_error_on_stdio_flush_2'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn._TestImportStar.test_import'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.WithProcessesTestBarrier.test_default_timeout'),
        ('test_multiprocessing_spawn.txt', '*graalpython.lib-python.3.test.test_multiprocessing_spawn.WithProcessesTestBarrier.test_timeout'),
        # Disabled due to transient stack overflow that fails to get caught and crashes the VM
        ('test_exceptions.txt', '*graalpython.lib-python.3.test.test_exceptions.ExceptionTests.test_badisinstance'),
        ('test_exceptions.txt', '*graalpython.lib-python.3.test.test_exceptions.ExceptionTests.testInfiniteRecursion'),
        ('test_list.txt', '*graalpython.lib-python.3.test.test_list.ListTest.test_repr_deep'),
        ('test_functools.txt', '*graalpython.lib-python.3.test.test_functools.TestPartialC.test_recursive_pickle'),
        ('test_functools.txt', '*graalpython.lib-python.3.test.test_functools.TestPartialCSubclass.test_recursive_pickle'),
        ('test_functools.txt', '*graalpython.lib-python.3.test.test_functools.TestPartialPy.test_recursive_pickle'),
        ('test_functools.txt', '*graalpython.lib-python.3.test.test_functools.TestPartialPySubclass.test_recursive_pickle'),
        # Transient, GR-41056
        ('test_subprocess.txt', '*graalpython.lib-python.3.test.test_subprocess.POSIXProcessTestCase.test_swap_std_fds_with_one_closed')
    }

    result_tags = linux_tags & darwin_tags - tag_exclusions
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

GINSTALL_GATE_PACKAGES = {
    "numpy": "numpy",
    "scipy": "scipy",
    "scikit_learn": "sklearn",
}


class GraalPythonTags(object):
    junit = 'python-junit'
    unittest = 'python-unittest'
    unittest_cpython = 'python-unittest-cpython'
    unittest_sandboxed = 'python-unittest-sandboxed'
    unittest_multi = 'python-unittest-multi-context'
    unittest_jython = 'python-unittest-jython'
    unittest_hpy = 'python-unittest-hpy'
    unittest_hpy_sandboxed = 'python-unittest-hpy-sandboxed'
    unittest_posix = 'python-unittest-posix'
    ginstall = 'python-ginstall'
    tagged = 'python-tagged-unittest'
    tagged_sandboxed = 'python-tagged-unittest-sandboxed'
    svmunit = 'python-svm-unittest'
    svmunit_sandboxed = 'python-svm-unittest-sandboxed'
    shared_object = 'python-so'
    shared_object_sandboxed = 'python-so-sandboxed'
    graalvm = 'python-graalvm'
    graalvm_sandboxed = 'python-graalvm-sandboxed'
    svm = 'python-svm'
    native_image_embedder = 'python-native-image-embedder'
    license = 'python-license'
    windows = 'python-windows-smoketests'


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
    os.environ.update(environ)
    try:
        yield
    finally:
        os.environ.clear()
        os.environ.update(old_environ)


def _graalvm_home(*, envfile, extra_dy=""):
    if not os.path.isabs(envfile):
        envfile = _sibling(envfile)
    home = os.environ.get("GRAALVM_HOME", None)
    if not home:
        dy = ",".join(["%s%s" % ("/" if dy[1] else "", dy[0]) for dy in mx.get_dynamic_imports()])
        dy += extra_dy
        mx_args = ["--env", envfile]
        if dy:
            mx_args = ["--dy", dy] + mx_args
        mx.run_mx(mx_args + ["build"])
        out = mx.OutputCapture()
        mx.run_mx(mx_args + ["graalvm-home"], out=out)
        home = out.data.splitlines()[-1].strip()
    elif "*" in home:
        home = os.path.abspath(glob.glob(home)[0])
    mx.log("choosing GRAALVM_HOME=%s" % home)
    return home


def _join_bin(home, name):
    if sys.platform == "darwin" and not re.search("Contents/Home/?$", home):
        return os.path.join(home, "Contents", "Home", "bin", name)
    elif sys.platform == "win32":
        return os.path.join(home, "bin", f"{name}.cmd")
    else:
        return os.path.join(home, "bin", name)


def python_gvm(_=None):
    home = _graalvm_home(envfile="graalpython-bash-launcher")
    launcher = _join_bin(home, "graalpy")
    mx.log(launcher)
    return launcher


def python_managed_gvm(_=None):
    home = _graalvm_home(envfile="graalpython-managed-bash-launcher")
    launcher = _join_bin(home, "graalpy-managed")
    mx.log(launcher)
    return launcher


def python_enterprise_gvm(_=None):
    home = _graalvm_home(envfile="graalpython-managed-bash-launcher")
    launcher = _join_bin(home, "graalpy")
    mx.log(launcher)
    return launcher

def python_svm(_=None):
    home = _graalvm_home(envfile="graalpython-launcher")
    launcher = _join_bin(home, "graalpy")
    mx.log(launcher)
    return launcher


def python_managed_svm():
    home = _graalvm_home(envfile="graalpython-managed-launcher")
    launcher = _join_bin(home, "graalpy-managed")
    mx.log(launcher)
    return launcher


def python_so():
    return _graalvm_home(envfile="graalpython-libpolyglot")


def python_managed_so():
    return _graalvm_home(envfile="graalpython-managed-libpolyglot")


def _graalpytest_driver():
    return os.path.join(SUITE.dir, "graalpython", "com.oracle.graal.python.test", "src", "graalpytest.py")


def _graalpytest_root():
    return os.path.join(mx.dependency("com.oracle.graal.python.test").get_output_root(), "bin", "tests")


def _hpy_test_root():
    return os.path.join(_get_core_home(), "modules", "hpy", "test")


def graalpytest(args):
    parser = ArgumentParser(prog='mx graalpytest')
    parser.add_argument('--python', type=str, action='store', default="", help='Run tests with custom Python binary.')
    parser.add_argument('-v', "--verbose", action="store_true", help='Verbose output.', default=True)
    parser.add_argument('-k', dest="filter", default='', help='Test pattern.')
    parser.add_argument('test', nargs="*", default=[], help='Test file to run (specify absolute or relative; e.g. "/path/to/test_file.py" or "cpyext/test_object.py") ')
    args, unknown_args = parser.parse_known_args(args)

    # ensure that the test distribution is up-to-date
    mx.command_function("build")(["--dep", "com.oracle.graal.python.test"])

    testfiles = _list_graalpython_unittests(args.test)
    cmd_args = []
    # if we got a binary path it's most likely CPython, so don't add graalpython args
    if not args.python:
        cmd_args += ["--experimental-options=true", "--python.EnableDebuggingBuiltins"]
    # we assume that unknown args are polyglot arguments and just prepend them to the test driver
    cmd_args += unknown_args + [_graalpytest_driver()]
    if args.verbose:
        cmd_args += ["-v"]
    cmd_args += testfiles
    if args.filter:
        cmd_args += ["-k", args.filter]
    env = os.environ.copy()
    env['PYTHONHASHSEED'] = '0'
    delete_bad_env_keys(env)
    if args.python:
        return mx.run([args.python] + cmd_args, nonZeroIsFatal=True, env=env)
    else:
        return do_run_python(cmd_args, env=env)


def _list_graalpython_unittests(paths=None, exclude=None):
    exclude = [] if exclude is None else exclude
    paths = paths or [_graalpytest_root()]
    def is_included(path):
        if path.endswith(".py"):
            basename = os.path.basename(path)
            return basename.startswith("test_") and basename not in exclude
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
                    testfiles.append(testfile)
            for testfile in glob.glob(os.path.join(path, "test_*.py")):
                if is_included(testfile):
                    testfiles.append(testfile)
    return testfiles


def run_python_unittests(python_binary, args=None, paths=None, aot_compatible=False, exclude=None, env=None,
                         use_pytest=False, cwd=None, lock=None, out=None, err=None, nonZeroIsFatal=True, javaAsserts=False):
    if lock:
        lock.acquire()
    # ensure that the test distribution is up-to-date
    mx.command_function("build")(["--dep", "com.oracle.graal.python.test"])

    args = args or []
    args = ["--experimental-options=true",
            "--python.EnableDebuggingBuiltins",
            "--python.CatchAllExceptions=true"] + args
    exclude = exclude or []
    if env is None:
        env = os.environ.copy()
    env['PYTHONHASHSEED'] = '0'
    delete_bad_env_keys(env)

    # list of excluded tests
    if aot_compatible:
        exclude += AOT_INCOMPATIBLE_TESTS

    # just to be able to verify, print C ext mode (also works for CPython)
    mx.run([python_binary,
            "-c",
            "import sys; print('C EXT MODE: ' + (__graalpython__.platform_id if sys.implementation.name == 'graalpy' else 'cpython'))"],
            nonZeroIsFatal=True, env=env, out=out, err=err)

    # list all 1st-level tests and exclude the SVM-incompatible ones
    testfiles = _list_graalpython_unittests(paths, exclude)
    if use_pytest:
        args += ["-m", "pytest", "-v", "--assert=plain", "--tb=native"]
    else:
        args += [_graalpytest_driver(), "-v"]

    if mx_gate.get_jacoco_agent_args():
        env['ENABLE_THREADED_GRAALPYTEST'] = "false"
        # If 'python_binary' is a SVM launcher, we need to add '--jvm' and prefix each Java arg with '--vm.'
        def graalvm_vm_arg(java_arg):
            if java_arg.startswith("@") and os.path.exists(java_arg[1:]):
                with open(java_arg[1:], "r") as f:
                    java_arg = f.read()
            assert java_arg[0] == "-", java_arg
            return shlex.quote(f'--vm.{java_arg[1:]}')
        agent_args = ' '.join(graalvm_vm_arg(arg) for arg in mx_gate.get_jacoco_agent_args() or [])

        # We need to make sure the arguments get passed to subprocesses, so we create a temporary launcher
        # with the arguments. We also disable compilation, it hardly helps for this use case
        original_launcher = os.path.abspath(os.path.realpath(python_binary))
        bash_launcher = f'{original_launcher}.sh'
        with open(bash_launcher, "w") as f:
            f.write("#!/bin/sh\n")
            exe_arg = shlex.quote(f"--python.Executable={bash_launcher}")
            f.write(f'exec {original_launcher} --jvm {exe_arg} {agent_args} "$@"\n')
        os.chmod(bash_launcher, 0o775)

        # jacoco only dumps the data on exit, and when we run all our unittests
        # at once it generates so much data we run out of heap space
        for testfile in testfiles:
            mx.run([bash_launcher] + args + [testfile], nonZeroIsFatal=nonZeroIsFatal, env=env, cwd=cwd, out=out, err=err)
    else:
        if javaAsserts:
            args += ['-ea']

        args += testfiles
        mx.logv(" ".join([python_binary] + args))
        if lock:
            lock.release()
        return mx.run([python_binary] + args, nonZeroIsFatal=nonZeroIsFatal, env=env, cwd=cwd, out=out, err=err)


def get_venv_env(env_dir):
    env = os.environ.copy()
    path = os.environ.get("PATH", '')
    env.update(**{
        'VIRTUAL_ENV': env_dir,
        'PATH': ":".join([os.path.join(env_dir, 'bin'), path]),
    })
    if 'PYTHONHOME' in env:
        del env['PYTHONHOME']
    return env


def prepare_graalpy_venv(python_binary, packages=None, env_path=None, args=None):
    if args is None:
        args = []
    if packages is None:
        packages = []
    if isinstance(packages, dict):
        packages = list(packages.keys())
    assert isinstance(packages, (tuple, set, list)), "packages arg must be a tuple, set or list"
    env_dir = os.path.realpath(env_path if env_path else tempfile.mkdtemp())
    mx.log("using graalpython venv: {}".format(env_dir))
    mx.run([python_binary, "-m", "venv", env_dir], nonZeroIsFatal=True)
    mx.log("installing the following packages: {}".format(", ".join(packages)))
    mx.run(["graalpy", "-m", "ginstall"] + args + ["install", ",".join(packages)], nonZeroIsFatal=True,
           env=get_venv_env(env_dir))
    return env_dir


def run_with_venv(cmd, env_dir, **kwargs):
    assert isinstance(cmd, list), "cmd argument must be a list"
    kwargs['env'] = get_venv_env(env_dir)
    mx.run(cmd, **kwargs)


def run_ginstall(python_binary, args=None):
    if args is None:
        args = []
    env_dir = prepare_graalpy_venv(python_binary, packages=GINSTALL_GATE_PACKAGES, args=args)
    import_packages = '"{}"'.format(';'.join(["import {}".format(n) for p, n in GINSTALL_GATE_PACKAGES.items()]))
    run_with_venv(["graalpy", "-c", import_packages], env_dir, nonZeroIsFatal=True)


def is_bash_launcher(launcher_path):
    with open(launcher_path, 'r', encoding='ascii', errors='ignore') as launcher:
        return re.match(r'^#!.*bash', launcher.readline())


def patch_batch_launcher(launcher_path, jvm_args):
    with open(launcher_path, 'r', encoding='ascii', errors='ignore') as launcher:
        lines = launcher.readlines()
    assert re.match(r'^#!.*bash', lines[0]), "expected a bash launcher"
    lines.insert(-1, 'jvm_args+=(%s)\n' % jvm_args)
    with open(launcher_path, 'w') as launcher:
        launcher.writelines(lines)


def run_hpy_unittests(python_binary, args=None, include_native=True):
    args = [] if args is None else args
    with tempfile.TemporaryDirectory(prefix='hpy-test-site-') as d:
        env = os.environ.copy()
        prefix = str(d)
        env.update(PYTHONUSERBASE=prefix)
        delete_bad_env_keys(env)
        mx.run_mx(["build", "--dependencies", "LLVM_TOOLCHAIN"])
        env.update(LLVM_TOOLCHAIN_VANILLA=mx_subst.path_substitutions.substitute('<path:LLVM_TOOLCHAIN>/bin'))
        mx.log("LLVM Toolchain (vanilla): {!s}".format(env["LLVM_TOOLCHAIN_VANILLA"]))
        mx.log("Ensure 'setuptools' is installed")
        mx.run([python_binary] + args + ["-m", "ginstall", "install", "--user", "pytest"], nonZeroIsFatal=True, env=env)
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
                                                  out=self.out, err=self.out)
                except BaseException as e: # pylint: disable=broad-except;
                    self.result = e

        abi_list = ['cpython', 'universal']
        if include_native:
            # modes 'debug' and 'nfi' can only be used if native access is allowed
            abi_list.extend(['debug', 'nfi'])
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

        thread_errors = [t.result for t in threads if t.result != 0]
        if any(thread_errors):
            for t in threads:
                mx.log_error("\n\n### Output of thread %r: \n\n%s" % (t.name, t.out))
            mx.abort("At least one HPy testing thread failed.")


def run_tagged_unittests(python_binary, env=None, cwd=None, javaAsserts=False, nonZeroIsFatal=True):
    if env is None:
        env = os.environ
    sub_env = env.copy()
    sub_env.update(dict(
        ENABLE_CPYTHON_TAGGED_UNITTESTS="true",
        ENABLE_THREADED_GRAALPYTEST="true",
        PYTHONPATH=os.path.join(_dev_pythonhome(), 'lib-python/3'),
    ))
    run_python_unittests(
        python_binary,
        args=["-v"],
        paths=["test_tagged_unittests.py"],
        env=sub_env,
        cwd=cwd,
        javaAsserts=javaAsserts,
        nonZeroIsFatal=nonZeroIsFatal,
    )


def graalpython_gate_runner(args, tasks):
    # JUnit tests
    with Task('GraalPython JUnit', tasks, tags=[GraalPythonTags.junit]) as task:
        if task:
            punittest(['--verbose'])

    # Unittests on JVM
    with Task('GraalPython Python unittests', tasks, tags=[GraalPythonTags.unittest]) as task:
        if task:
            mx.run(["env"])
            run_python_unittests(python_gvm(), javaAsserts=True, nonZeroIsFatal=(not mx_gate.get_jacoco_agent_args()))

    with Task('GraalPython Python unittests with CPython', tasks, tags=[GraalPythonTags.unittest_cpython]) as task:
        if task:
            exe = os.environ.get("PYTHON3_HOME", None)
            if exe:
                exe = os.path.join(exe, "python")
            else:
                exe = "python3"
            env = os.environ.copy()
            env['PYTHONHASHSEED'] = '0'
            test_args = [exe, _graalpytest_driver(), "-v", "graalpython/com.oracle.graal.python.test/src/tests"]
            mx.run(test_args, nonZeroIsFatal=True, env=env)

    with Task('GraalPython sandboxed tests', tasks, tags=[GraalPythonTags.unittest_sandboxed]) as task:
        if task:
            run_python_unittests(python_managed_gvm(), javaAsserts=True)

    with Task('GraalPython multi-context unittests', tasks, tags=[GraalPythonTags.unittest_multi]) as task:
        if task:
            run_python_unittests(python_gvm(), args=["-multi-context"], javaAsserts=True, nonZeroIsFatal=(not mx_gate.get_jacoco_agent_args()))

    with Task('GraalPython Jython emulation tests', tasks, tags=[GraalPythonTags.unittest_jython]) as task:
        if task:
            run_python_unittests(python_gvm(), args=["--python.EmulateJython"], paths=["test_interop.py"], javaAsserts=True)

    with Task('GraalPython ginstall', tasks, tags=[GraalPythonTags.ginstall]) as task:
        if task:
            run_ginstall(python_gvm_with_assertions(), args=["--quiet"])

    with Task('GraalPython HPy tests', tasks, tags=[GraalPythonTags.unittest_hpy]) as task:
        if task:
            run_hpy_unittests(python_svm())

    with Task('GraalPython HPy sandboxed tests', tasks, tags=[GraalPythonTags.unittest_hpy_sandboxed]) as task:
        if task:
            run_hpy_unittests(python_managed_svm(), include_native=False)

    with Task('GraalPython posix module tests', tasks, tags=[GraalPythonTags.unittest_posix]) as task:
        if task:
            run_python_unittests(python_gvm(), args=["--PosixModuleBackend=native"], paths=["test_posix.py", "test_mmap.py"], javaAsserts=True)
            run_python_unittests(python_gvm(), args=["--PosixModuleBackend=java"], paths=["test_posix.py", "test_mmap.py"], javaAsserts=True)

    with Task('GraalPython Python tests', tasks, tags=[GraalPythonTags.tagged]) as task:
        if task:
            # don't fail this task if we're running with the jacoco agent, we know that some tests don't pass with it enabled
            run_tagged_unittests(python_svm(), nonZeroIsFatal=(not mx_gate.get_jacoco_agent_args()))

    # Unittests on SVM
    with Task('GraalPython tests on SVM', tasks, tags=[GraalPythonTags.svmunit]) as task:
        if task:
            run_python_unittests(python_svm(), aot_compatible=True)

    with Task('GraalPython sandboxed tests on SVM', tasks, tags=[GraalPythonTags.svmunit_sandboxed]) as task:
        if task:
            run_python_unittests(python_managed_svm(), aot_compatible=True)

    with Task('GraalPython license header update', tasks, tags=[GraalPythonTags.license]) as task:
        if task:
            python_checkcopyrights([])

    with Task('GraalPython GraalVM shared-library build', tasks, tags=[GraalPythonTags.shared_object, GraalPythonTags.graalvm]) as task:
        if task:
            run_shared_lib_test(python_so())

    with Task('GraalPython GraalVM sandboxed shared-library build', tasks, tags=[GraalPythonTags.shared_object_sandboxed, GraalPythonTags.graalvm_sandboxed]) as task:
        if task:
            run_shared_lib_test(python_managed_so(), ("sandboxed",))

    with Task('GraalPython GraalVM build', tasks, tags=[GraalPythonTags.svm, GraalPythonTags.graalvm]) as task:
        if task:
            svm_image = python_svm()
            benchmark = os.path.join(PATH_MESO, "image-magix.py")
            out = mx.OutputCapture()
            mx.run([svm_image, "-v", "-S", "--log.python.level=FINEST", benchmark], nonZeroIsFatal=True, out=mx.TeeOutputCapture(out), err=mx.TeeOutputCapture(out))
            success = "\n".join([
                "[0, 0, 0, 0, 0, 0, 10, 10, 10, 0, 0, 10, 3, 10, 0, 0, 10, 10, 10, 0, 0, 0, 0, 0, 0]",
            ])
            if success not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not match success pattern:\n' + success)
            assert "Using preinitialized context." in out.data

    with Task('GraalPy win32 smoketests', tasks, tags=[GraalPythonTags.windows]) as task:
        if task:
            punittest(["--no-leak-tests", "--regex", r'(com\.oracle\.truffle\.tck\.tests)|(graal\.python\.test\.(advance\.Benchmark|basic|builtin|decorator|generator|interop|util))'])
            svm_image = python_svm()
            out = mx.OutputCapture()
            mx.run([svm_image, "-v", "-S", "--log.python.level=FINEST", "-c", "import sys; print(sys.platform)"], nonZeroIsFatal=True, out=mx.TeeOutputCapture(out), err=mx.TeeOutputCapture(out))
            success = "\n".join(["win32"])
            if success not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not match success pattern:\n' + success)


mx_gate.add_gate_runner(SUITE, graalpython_gate_runner)


def run_shared_lib_test(home, args=()):
    svm_lib_path = os.path.abspath(os.path.join(home, "lib", "polyglot"))
    fd = name = progname = None
    try:
        fd, name = tempfile.mkstemp(suffix='.c')
        os.write(fd, b"""
        #include "stdio.h"
        #include "polyglot_api.h"

        #define assert_ok(msg, f) { if (!(f)) { \\
             const poly_extended_error_info* error_info; \\
             poly_get_last_error_info(isolate_thread, &error_info); \\
             fprintf(stderr, "%%s\\n", error_info->error_message); \\
             return fprintf(stderr, "%%s\\n", msg); } } while (0)

        poly_isolate global_isolate;
        poly_thread isolate_thread;
        poly_engine engine;
        poly_context context;

        static poly_status create_context() {
            poly_status status;

            if (poly_attach_thread(global_isolate, &isolate_thread)) {
                return poly_generic_failure;
            }

            poly_engine_builder engine_builder;
            status = poly_create_engine_builder(isolate_thread, &engine_builder);
            if (status != poly_ok) {
                return status;
            }
            status = poly_engine_builder_build(isolate_thread, engine_builder, &engine);
            if (status != poly_ok) {
                return status;
            }
            poly_context_builder builder;
            status = poly_create_context_builder(isolate_thread, NULL, 0, &builder);
            if (status != poly_ok) {
                return status;
            }
            status = poly_context_builder_engine(isolate_thread, builder, engine);
            if (status != poly_ok) {
                return status;
            }
            status = poly_context_builder_option(isolate_thread, builder, "python.VerboseFlag", "true");
            if (status != poly_ok) {
                return status;
            }
        #if %s
            status = poly_context_builder_option(isolate_thread, builder, "llvm.managed", "true");
            if (status != poly_ok) {
                return status;
            }
        #endif
            status = poly_context_builder_allow_io(isolate_thread, builder, true);
            if (status != poly_ok) {
                return status;
            }
            status = poly_context_builder_build(isolate_thread, builder, &context);
            if (status != poly_ok) {
                return status;
            }

            return poly_ok;
        }

        static poly_status tear_down_context() {
            poly_status status = poly_context_close(isolate_thread, context, true);
            if (status != poly_ok) {
                return status;
            }

            status = poly_engine_close(isolate_thread, engine, true);
            if (status != poly_ok) {
                return status;
            }

            if (poly_detach_thread(isolate_thread)) {
                return poly_ok;
            }

            return poly_ok;
        }

        static int test_basic_python_function() {
            assert_ok("Context creation failed.", create_context() == poly_ok);

            poly_value func;
            assert_ok("function eval failed", poly_context_eval(isolate_thread, context, "python", "test_func", "def test_func(x):\\n  return x * x\\ntest_func", &func) == poly_ok);
            int32_t arg_value = 42;
            poly_value primitive_object;
            assert_ok("create argument failed", poly_create_int32(isolate_thread, context, arg_value, &primitive_object) == poly_ok);
            poly_value arg[1] = {primitive_object};
            poly_value value;
            assert_ok("invocation was unsuccessful", poly_value_execute(isolate_thread, func, arg, 1, &value) == poly_ok);

            int32_t result_value;
            poly_value_as_int32(isolate_thread, value, &result_value);

            assert_ok("value computation was incorrect", result_value == 42 * 42);
            assert_ok("Context tear down failed.", tear_down_context() == poly_ok);
            return 0;
        }

        int32_t main(int32_t argc, char **argv) {
            poly_isolate_params isolate_params = {};
            if (poly_create_isolate(&isolate_params, &global_isolate, &isolate_thread)) {
                return 1;
            }
            return test_basic_python_function();
        }
        """ % (b"1" if "sandboxed" in args else b"0"))
        os.close(fd)
        progname = os.path.join(SUITE.dir, "graalpython-embedded-tool")
        mx.log("".join(["Running ", "'clang", "-I%s" % svm_lib_path, "-L%s" % svm_lib_path, name, "-o", progname, "-lpolyglot"]))
        mx.run(["clang", "-I%s" % svm_lib_path, "-L%s" % svm_lib_path, name, "-o%s" % progname, "-lpolyglot"], nonZeroIsFatal=True)
        mx.log("Running " + progname + " with LD_LIBRARY_PATH " + svm_lib_path)
        mx.run(["ls", "-l", progname])
        mx.run(["ls", "-l", svm_lib_path])
        run_env = {"LD_LIBRARY_PATH": svm_lib_path, "GRAAL_PYTHONHOME": os.path.join(home, "languages", "python")}
        mx.log(repr(run_env))
        mx.run([progname], env=run_env)
    finally:
        try:
            os.unlink(progname)
        except:
            pass
        try:
            os.close(fd)
        except:
            pass
        try:
            os.unlink(name)
        except:
            pass


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
        return getattr(self, "prefix", "")

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


def deploy_binary_if_main(args):
    """if the active branch is the main branch, deploy binaries for the primary suite to remote maven repository."""
    main_branch = 'master'
    active_branch = mx.VC.get_vc(SUITE.dir).active_branch(SUITE.dir)
    if active_branch == main_branch:
        if sys.platform == "darwin":
            args.insert(0, "--platform-dependent")
        return mx.command_function('deploy-binary')(args)
    else:
        mx.log('The active branch is "%s". Binaries are deployed only if the active branch is "%s".' % (
            active_branch, main_branch))
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


mx_subst.path_substitutions.register_with_arg('suite', _get_suite_dir)
mx_subst.path_substitutions.register_with_arg('src_dir', _get_src_dir)
mx_subst.path_substitutions.register_with_arg('output_root', _get_output_root)


def delete_self_if_testdownstream(args):
    """
    A helper for downstream testing with binary dependencies
    """
    if str(SUITE.dir).endswith("testdownstream/graalpython"):
        shutil.rmtree(SUITE.dir, ignore_errors=True)


def update_import(name, suite_py, rev="origin/master"):
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
    if rev != "HEAD":
        vc.pull(dep_dir, update=False)
    vc.update(dep_dir, rev=rev)
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
    join = os.path.join
    vc = SUITE.vc

    current_branch = vc.active_branch(SUITE.dir)
    if current_branch == "master":
        mx.abort("updating imports should be done on a branch")
    if vc.isDirty(SUITE.dir):
        mx.abort("updating imports should be done on a clean branch")

    suite_py_files = []
    local_names = []
    repos = []

    # find all relevant other repos that may need updating
    for sibling in os.listdir(os.path.join(SUITE.dir, "..")):
        if sibling.startswith("graalpython"):
            dd = os.path.join(SUITE.dir, "..", sibling)
            jsonnetfile = os.path.join(dd, "ci.jsonnet")
            if os.path.exists(jsonnetfile):
                local_names.append(sibling)
                repos.append(dd)
                for dirpath, dirnames, filenames in os.walk(dd):
                    mx_dirs = list(filter(lambda x: x.startswith("mx."), dirnames))
                    if mx_dirs:
                        dirnames[:] = mx_dirs # don't go deeper once we found some mx dirs
                    dirnames[:] = list(filter(lambda x: not (x.startswith(".") or x.startswith("__")), dirnames))
                    if "suite.py" in filenames:
                        suite_py_files.append(join(dirpath, "suite.py"))

    # make sure all other repos are clean and on the same branch
    for d in repos:
        if vc.isDirty(d):
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
    if vc.isDirty(overlaydir):
        mx.abort("overlays repo must be clean")
    overlaybranch = vc.active_branch(overlaydir)
    if overlaybranch == "master":
        if "--no-pull" not in args:
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
        for idx, suite_py in enumerate(suite_py_files):
            revisions[name] = update_import(name, suite_py, rev=("HEAD" if (idx or "--no-pull" in args) else "origin/master"))

    # copy files we inline from our imports
    shutil.copy(
        join(mx.suite("truffle").dir, "..", "common.json"),
        join(overlaydir, "python", "graal-common.json"))
    with open(join(overlaydir, "fastr", "common.libsonnet")) as fastrCommon:
        with open(join(overlaydir, "python", "fastr-common.libsonnet"), "w") as fastrCopy:
            fastrCopy.write(fastrCommon.read().replace("graal_common.json", "../python/graal-common.json"))

    # update the graal-enterprise revision in the overlay (used by benchmarks)
    with open(join(overlaydir, "python", "imported-constants.json"), 'w') as fp:
        d = {'GRAAL_ENTERPRISE_REVISION': revisions['graalpython-enterprise']}
        json.dump(d, fp, indent=2)

    # update vm-tests.json vm version
    with open(join(overlaydir, "python", "graal-common.json"), 'r') as fp:
        d = json.load(fp)
        oraclejdk17_ver = d['jdks']['oraclejdk17']['version']

    with open(join(overlaydir, "python", "vm-tests.json"), 'r') as fp:
        d = json.load(fp)
        for job in d:
            job['downloads']['JAVA_HOME']['version'] = oraclejdk17_ver

    with open(join(overlaydir, "python", "vm-tests.json"), 'w') as fp:
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
    prev_verbosity = mx._opts.very_verbose
    for repo in repos:
        jsonnetfile = os.path.join(repo, "ci.jsonnet")
        with open(jsonnetfile, "w") as f:
            f.write('{ "overlay": "%s" }\n' % overlaytip)
        if vc.isDirty(repo):
            vc.commit(repo, "Update imports")
            repos_updated.append(repo)

    # push all repos
    if "--no-push" not in args:
        for repo in repos_updated:
            try:
                mx._opts.very_verbose = True
                vc.git_command(repo, ["push", "-u", "origin", "HEAD:%s" % current_branch], abortOnError=True)
            finally:
                mx._opts.very_verbose = prev_verbosity

        if repos_updated and input('Use automation tool to create PRs (Y/n)? ').lower() != "n":
            username = input('Username: ')
            password = getpass.getpass('Password: ')
            cmds = []
            for repo in repos_updated:
                reponame = os.path.basename(repo)
                cmd = [
                    "ol-cli", "bitbucket", "--user='%s'" % username, "--password='${SSO_PASSWORD}'",
                    "create-pr", "--project=G", "--repo=%s" % reponame,
                    "--title=[GR-21590] Update Python imports",
                    "--from-branch=%s" % current_branch, "--to-branch=master"
                ]
                cmds.append(cmd)
                print(" ".join(cmd))
            for cmd in cmds:
                cmd[3].replace("${SSO_PASSWORD}", password)
                mx.run(cmd, nonZeroIsFatal=False)

    if repos_updated:
        mx.log("\n  ".join(["These repos were updated:"] + repos_updated))


def python_style_checks(args):
    "Check (and fix where possible) copyrights, eclipse formatting, and spotbugs"
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
    listfilename = tempfile.mktemp()
    # additionally, we want to check if the packages we are patching all have a permissive license
    with open(listfilename, "w") as listfile:
        mx.run(["git", "ls-tree", "-r", "HEAD", "--name-only"], out=listfile)
    try:
        # TODO our mirror doesn't handle the json API
        # pypi_base_url = mx_urlrewrites.rewriteurl("https://pypi.org/packages/").replace("packages/", "")
        pypi_base_url = "https://pypi.org/"
        with open(listfilename, "r") as listfile:
            content = listfile.read()
        patchfile_pattern = re.compile(r"lib-graalpython/patches/([^/]+)/(sdist|whl)/.*\.patch")
        checked = set()
        allowed_licenses = [
            "MIT", "BSD", "BSD-3-Clause", "BSD 3-Clause License", "BSD or Apache License, Version 2.0",
            "MIT license", "PSF", "BSD-3-Clause OR Apache-2.0"
        ]
        for line in content.split("\n"):
            if os.stat(line).st_size == 0:
                # empty files are just markers and do not need to be license checked
                continue
            match = patchfile_pattern.search(line)
            if match:
                package_name = match.group(1)
                if package_name in checked:
                    break
                checked.add(package_name)
                package_url = "/".join([pypi_base_url, "pypi", package_name, "json"])
                mx.log("Checking license of patchfile for " + package_url)
                response = urllib_request.urlopen(package_url)
                try:
                    data = json.loads(response.read())
                    data_license = data["info"]["license"]
                    if data_license not in allowed_licenses:
                        mx.abort(("The license for the original project %r is %r. We cannot include " +
                                  "a patch for it. Allowed licenses are: %r.") % (package_name, data_license, allowed_licenses))
                except Exception as e: # pylint: disable=broad-except;
                    mx.abort("Error getting %r.\n%r" % (package_url, e))
                finally:
                    if response:
                        response.close()
    finally:
        os.unlink(listfilename)

def import_python_sources(args):
    "Update the inlined files from PyPy and CPython"

    # mappings for files that are renamed
    mapping = {
        "_sre.c": "_cpython_sre.c",
        "unicodedata.c": "_cpython_unicodedata.c",
        "_bz2module.c": "_bz2.c",
        "mmapmodule.c": "_mmap.c",
        "_struct.c": "_cpython_struct.c",
        "_testcapimodule.c": "_testcapi.c",
    }
    extra_pypy_files = [
        "graalpython/lib-python/3/_md5.py",
        "graalpython/lib-python/3/_sha1.py",
        "graalpython/lib-python/3/_sha256.py",
        "graalpython/lib-python/3/_sha512.py",
    ]

    parser = ArgumentParser(prog='mx python-src-import')
    parser.add_argument('--cpython', action='store', help='Path to CPython sources', required=True)
    parser.add_argument('--pypy', action='store', help='Path to PyPy sources', required=True)
    parser.add_argument('--python-version', action='store', help='Python version to be updated to (used for commit message)', required=True)
    args = parser.parse_args(args)

    python_sources = args.cpython
    pypy_sources = args.pypy
    import_version = args.python_version

    print("""
    So you think you want to update the inlined sources? Here is how it will go:

    1. We'll first check the copyrights check overrides file to identify the
       files taken from CPython or PyPy and we'll remember that list. There's a mapping
       for files that were renamed, currently this includes:
       \t{0!r}\n

    2. We'll check out the "python-import" branch. This branch has only files
       that were inlined from CPython or PyPy. We'll use the sources given on
       the commandline for that. I hope those are in a state where that makes
       sense.

    3. We'll stop and wait to give you some time to check if the python-import
       branch looks as you expect. Then we'll commit the updated files to the
       python-import branch, push it, and move back to whatever your HEAD is
       now.

    4. We'll merge the python-import branch back into HEAD. Because these share
       a common ancestor, git will try to preserve our patches to files, that
       is, copyright headers and any other source patches.

    5. !IMPORTANT! If files were inlined from CPython during normal development
       that were not first added to the python-import branch, you will get merge
       conflicts and git will tell you that the files was added on both
       branches. You probably should resolve these using:

           git checkout python-import -- path/to/file

        Then check the diff and make sure that any patches that we did to those
        files are re-applied.

    6. After the merge is completed and any direct merge conflicts are resolved,
       run this:

           mx python-checkcopyrights --fix

       This will apply copyrights to files that we're newly added from
       python-import.

    7. Adjust some constants in the source code:

            version information in PythonLanguage (e.g., PythonLanguage#MINOR)

    8. Run the tests and fix any remaining issues.

    9. You should push the python-import branch using:

           git push origin python-import:python-import

    NOTE: Your changes, untracked files and ignored files will be stashed for the
    duration this operation. If you abort this script, you can recover them by
    moving back to your branch and using git stash pop. It is recommended that you
    close your IDE during the operation.
    """.format(mapping))
    input("Got it?")

    with open(os.path.join(os.path.dirname(__file__), "copyrights", "overrides")) as f:
        cpy_files = [line.split(",")[0] for line in f.read().split("\n") if len(line.split(",")) > 1 and line.split(",")[1] == "python.copyright"]
        pypy_files = [line.split(",")[0] for line in f.read().split("\n") if len(line.split(",")) > 1 and line.split(",")[1] == "pypy.copyright"]

    # move to orphaned branch with sources
    SUITE.vc.git_command(SUITE.dir, ["stash", "--all"])
    SUITE.vc.git_command(SUITE.dir, ["checkout", "python-import"])
    assert not SUITE.vc.isDirty(SUITE.dir)
    shutil.rmtree("graalpython")

    # re-copy lib-python
    shutil.copytree(os.path.join(python_sources, "Lib"), _get_stdlib_home())

    def copy_inlined_files(inlined_files, source_directory):
        inlined_files = [
            # test files don't need to be updated, they inline some unittest code only
            f for f in inlined_files if re.search(r'\.(py|c|h)$', f) and not re.search(r'/test_|_tests\.py$', f)
        ]
        for dirpath, _, filenames in os.walk(source_directory):
            for filename in filenames:
                original_file = os.path.join(dirpath, filename)
                comparable_file = os.path.join(dirpath, mapping.get(filename, filename))
                # Find the longest suffix match
                inlined_file = max(inlined_files, key=lambda f: len(os.path.commonprefix([''.join(reversed(f)), ''.join(reversed(comparable_file))])))
                if os.path.basename(inlined_file) != os.path.basename(comparable_file):
                    continue
                try:
                    os.makedirs(os.path.dirname(inlined_file))
                except OSError:
                    pass
                shutil.copy(original_file, inlined_file)
                inlined_files.remove(inlined_file)
                if not inlined_files:
                    return
        for remaining_file in inlined_files:
            mx.warn("Could not update %s - original file not found" % remaining_file)

    copy_inlined_files(pypy_files + extra_pypy_files, pypy_sources)
    copy_inlined_files(cpy_files, python_sources)

    # commit and check back
    SUITE.vc.git_command(SUITE.dir, ["add", "."])
    input("Check that the updated files look as intended, then press RETURN...")
    SUITE.vc.commit(SUITE.dir, "Update Python inlined files: %s" % import_version)
    SUITE.vc.git_command(SUITE.dir, ["checkout", "-"])
    SUITE.vc.git_command(SUITE.dir, ["merge", "python-import"])
    SUITE.vc.git_command(SUITE.dir, ["stash", "pop"])


# ----------------------------------------------------------------------------------------------------------------------
#
# add ci verification util
#
# ----------------------------------------------------------------------------------------------------------------------
def verify_ci(dest_suite, common_ci_dir="ci_common", args=None, ext=('.jsonnet', '.libsonnet')):
    """Verify CI configuration"""
    base_suite = SUITE
    assert isinstance(base_suite, mx.SourceSuite)

    ci_files = mx.suite_ci_files(SUITE, common_ci_dir, extension=ext)
    mx.log("CI setup checking common file(s): \n\t{0}".format('\n\t'.join(map(str, ci_files))))
    mx.verify_ci(args, base_suite, dest_suite, common_file=ci_files)


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


mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=SUITE,
    name='GraalVM Python',
    short_name='pyn',
    installable_id='python',
    dir_name='python',
    standalone_dir_name='graalpy-<version>-<graalvm_os>-<arch>',
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'pynl',
        'Truffle',
        'LLVM Runtime Native',
        'LLVM.org toolchain',
        'TRegex',
        'ICU4J',
    ],
    standalone_dependencies={
        'LLVM Runtime Core': ('lib/sulong', []),
        'LLVM Runtime Native': ('lib/sulong', []),
        'LLVM.org toolchain': ('lib/llvm-toolchain', []),
        'GraalVM Python license files': ('', []),
    },
    truffle_jars=[
        'graalpython:GRAALPYTHON',
        'graalpython:BOUNCYCASTLE-PROVIDER',
        'graalpython:BOUNCYCASTLE-PKIX',
        'graalpython:XZ-1.8',
    ],
    support_distributions=[
        'graalpython:GRAALPYTHON_GRAALVM_SUPPORT',
        'graalpython:GRAALPYTHON_GRAALVM_DOCS',
    ],
    library_configs=[
        mx_sdk.LanguageLibraryConfig(
            launchers=['bin/<exe:graalpy>', 'bin/<exe:python>', 'bin/<exe:python3>'],
            jar_distributions=['graalpython:GRAALPYTHON-LAUNCHER'],
            main_class=GRAALPYTHON_MAIN_CLASS,
            build_args=[
                '-H:+TruffleCheckBlackListedMethods',
                '-H:+DetectUserDirectoriesInImageHeap',
                '-Dpolyglot.python.PosixModuleBackend=native'
            ],
            language='python',
        )
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
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_DEFAULT_AST, extra_polyglot_args=[
        '--experimental-options', '--use-ast-interpreter',
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_INTERPRETER, extra_polyglot_args=[
        '--experimental-options', '--engine.Compilation=false'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_INTERPRETER_AST, extra_polyglot_args=[
        '--experimental-options', '--engine.Compilation=false', '--use-ast-interpreter',
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
        '--experimental-options', '--python.HPyBackend=NFI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_INTERPRETER, extra_polyglot_args=[
        '--experimental-options', '--engine.Compilation=false', '--python.HPyBackend=NFI']), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_SANDBOXED_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context'] + SANDBOXED_OPTIONS), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context', '--python.HPyBackend=NFI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_INTERPRETER_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context', '--engine.Compilation=false', '--python.HPyBackend=NFI'
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_MULTI_TIER, extra_polyglot_args=[
        '--experimental-options', '--engine.MultiTier=true', '--python.HPyBackend=NFI'
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
    for parser_bench_suite in PythonParserBenchmarkSuite.get_benchmark_suites(PARSER_BENCHMARKS):
        mx_benchmark.add_bm_suite(parser_bench_suite)


class CharsetFilteringPariticpant:
    """
    Remove charset providers from the resulting JAR distribution. Done to avoid libraries (icu4j-charset)
    adding their charsets implicitly to native image. We need to add them explicitly in a controlled way.
    """
    def __opened__(self, archive, src_archive, services):
        self.__services = services

    def __closing__(self):
        self.__services.pop('java.nio.charset.spi.CharsetProvider', None)


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
    parser.add_argument('--truffle-upload-url', help='Format is like rsync: user@host:/directory', default=None)
    args = parser.parse_args(args)

    if args.jacoco:
        jacoco_args = [
            '--jacoco-omit-excluded',
            '--jacoco-generic-paths',
            '--jacoco-omit-src-gen',
            '--jacocout', 'coverage',
            '--jacoco-format', 'lcov',
        ]
        jacoco_gates = (
            GraalPythonTags.junit,
            GraalPythonTags.unittest,
            GraalPythonTags.unittest_multi,
            GraalPythonTags.unittest_jython,
            GraalPythonTags.tagged,
        )
        mx.run_mx([
            '--strict-compliance',
            '--primary', 'gate',
            '-B=--force-deprecation-as-warning-for-dependencies',
            '--strict-mode',
            '--tags', ",".join(['%s'] * len(jacoco_gates)) % jacoco_gates,
        ] + jacoco_args)
        mx.run_mx([
            '--strict-compliance',
            '--kill-with-sigquit',
            'jacocoreport',
            '--format', 'lcov',
            '--omit-excluded',
            'coverage',
            '--generic-paths',
            '--exclude-src-gen',
        ])
    if args.truffle:
        executable = python_gvm()
        file_filter = "*lib-python*,*lib-graalpython*,*graalpython/include*,*com.oracle.graal.python.cext*"
        variants = [
            {"args": []},
            {"args": ["--python.EmulateJython"], "paths": ["test_interop.py"]},
            # {"args": ["--llvm.managed"]},
            # {"tagged": True},
        ]
        for kwds in variants:
            variant_str = re.sub(r"[^a-zA-Z]", "_", str(kwds))
            outfile = os.path.join(SUITE.dir, "coverage_%s_$UUID$.lcov" % variant_str)
            if os.path.exists(outfile):
                os.unlink(outfile)
            extra_args = [
                "--experimental-options",
                "--llvm.lazyParsing=false",
                "--coverage",
                "--coverage.TrackInternal",
                f"--coverage.FilterFile={file_filter}",
                "--coverage.Output=lcov",
                f"--coverage.OutputFile={outfile}",
            ]
            env = os.environ.copy()
            env['GRAAL_PYTHON_ARGS'] = " ".join(extra_args)
            env['ENABLE_THREADED_GRAALPYTEST'] = "false"
            if kwds.pop("tagged", False):
                run_tagged_unittests(executable, env=env, javaAsserts=True, nonZeroIsFatal=False)
            else:
                run_python_unittests(executable, env=env, javaAsserts=True, nonZeroIsFatal=False, **kwds)

        # generate a synthetic lcov file that includes all sources with 0
        # coverage. this is to ensure all sources actuall show up - otherwise,
        # only loaded sources will be part of the coverage
        with tempfile.NamedTemporaryFile(mode="w", suffix='.py') as f:
            f.write("""
import os

for dirpath, dirnames, filenames in os.walk('{0}'):
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
            """.format(os.path.join(SUITE.dir, "graalpython", "lib-python")))
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
                    "--experimental-options",
                    "--llvm.lazyParsing=false",
                    "--python.PosixModuleBackend=java",
                    "--coverage",
                    "--coverage.TrackInternal",
                    f"--coverage.FilterFile={file_filter}",
                    "--coverage.Output=lcov",
                    "--coverage.OutputFile=" + lcov_file,
                    f.name
                ])

        home_launcher = os.path.join(os.path.dirname(os.path.dirname(executable)), 'languages/python')
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
                with open(f, 'w') as lcov_file:
                    lcov_file.write(lcov)
                cmdargs += ["-a", f]

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


def python_build_watch(args):
    """
    Watch the suite and on any changes to .class, .jar, .h, or .c files rebuild.
    By default, rebuilds only the archives and non-Java projects.
    """
    parser = ArgumentParser(prog='mx python-build-watch')
    parser.add_argument('--full', action='store_true', help='Run a full mx build', required=False)
    parser.add_argument('--graalvm', action='store_true', help='Build a graalvm', required=False)
    parser.add_argument('--no-java', action='store_true', help='Build only archives and native projects [default]', required=False)
    args = parser.parse_args(args)
    if sum([args.full, args.graalvm, args.no_java]) > 1:
        mx.abort("Only one of --full, --graalvm, --no-java can be specified")
    if args.full:
        # suffixes = [".c", ".h", ".class", ".jar", ".java"]
        excludes = [".*\\.py$"]
    elif args.graalvm:
        # suffixes = [".c", ".h", ".class", ".jar", ".java", ".py"]
        excludes = ["mx_.*\\.py$"]
    else:
        # suffixes = [".c", ".h", ".class", ".jar"]
        excludes = [".*\\.py$", ".*\\.java$"]

    cmd = ["inotifywait", "-q", "-e", "close_write,moved_to", "-r", "--format=%f"]
    for e in excludes:
        cmd += ["--exclude", e]
    cmd += ["@%s" % os.path.join(SUITE.dir, ".git"), SUITE.dir]
    cmd_qq = cmd[:]
    cmd_qq[1] = "-qq"
    was_quiet = mx.get_opts().quiet

    while True:
        out = mx.OutputCapture()
        if mx.run(cmd, out=out, nonZeroIsFatal=False) != 0:
            continue
        changed_file = out.data.strip()
        mx.logv(changed_file)
        if any(changed_file.endswith(ext) for ext in [".c", ".h", ".class", ".jar"]):
            if not mx.get_opts().quiet:
                sys.stdout.write("Build needed ")
                sys.stdout.flush()
            while True:
                # re-run this until it times out, which we'll interpret as quiet
                # time
                if not mx.get_opts().quiet:
                    sys.stdout.write(".")
                    sys.stdout.flush()
                mx.get_opts().quiet = True
                try:
                    retcode = mx.run(cmd_qq, timeout=3, nonZeroIsFatal=False)
                finally:
                    mx.get_opts().quiet = was_quiet
                if retcode == mx.ERROR_TIMEOUT:
                    if not mx.get_opts().quiet:
                        sys.stdout.write("\n")
                    break
            mx.log("Building.")
            if args.full:
                mx.command_function("build")()
            elif args.graalvm:
                mx.log(python_gvm())
            else:
                nativebuild([])
            mx.log("Build done.")


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
            "-B",
            "-S"
        ]
        mx.ensure_dir_exists(cwd)

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


class GraalpythonCAPIBuildTask(GraalpythonBuildTask):
    def run(self, args, env=None, cwd=None, **kwargs):
        env = env.copy() if env else os.environ.copy()
        # n.b.: we don't want derived projects to also have to depend on our build env vars
        env.update(mx.dependency("com.oracle.graal.python.cext").getBuildEnv())
        env.update(self.subject.getBuildEnv())

        # distutils will honor env variables CC, CFLAGS, LDFLAGS but we won't allow to change them,
        # besides keeping custom sysroot, since our toolchain forwards to the system headers
        for var in ["CC", "CFLAGS", "LDFLAGS"]:
            value = env.pop(var, None)
            if value and "--sysroot" in value:
                seen_sysroot = False
                for element in shlex.split(value):
                    if element == "--sysroot":
                        seen_sysroot = True
                    elif seen_sysroot:
                        env[var] = f"--sysroot {element}"
                        break
        return super().run(args, env=env, cwd=cwd, **kwargs)

    def _dev_headers_dir(self):
        return os.path.join(SUITE.dir, "graalpython", "include")

    def _prepare_headers(self):
        target_dir = self._dev_headers_dir()
        if os.path.exists(target_dir):
            shutil.rmtree(target_dir)
        mx.logv("Preparing header files (dest: {!s})".format(target_dir))
        shutil.copytree(os.path.join(self.src_dir(), "include"), target_dir)
        shutil.copy(os.path.join(mx.dependency("SULONG_LEGACY").get_output(), "include", "truffle.h"), target_dir)

    def build(self):
        self._prepare_headers()
        # n.b.: we do the following to ensure that there's a directory when the
        # importlib PathFinder initializes it's directory finders
        mx.ensure_dir_exists(os.path.join(self.subject.get_output_root(), "modules"))
        # TODO: backwards compat, remove once EE is updated
        if not hasattr(self.subject, "args"):
            self.subject.args = [
                os.path.join(self.src_dir(), "setup.py"),
                self.subject.get_output_root()
            ]
        return super().build()

    def clean(self, forBuild=False):
        result = 0
        if not forBuild:
            try:
                mx.logv('Cleaning {0}...'.format(self._dev_headers_dir()))
                shutil.rmtree(self._dev_headers_dir(), ignore_errors=True)
                mx.logv('Cleaning {0}...'.format(self.subject.get_output_root()))
                shutil.rmtree(self.subject.get_output_root(), ignore_errors=True)
            except BaseException as e:
                mx.logv('Error while cleaning: {0}'.format(e))
                result = 1
        return max(result, super().clean(forBuild=forBuild))


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


class GraalpythonCAPIProject(GraalpythonProject):
    def __init__(self, suite, name, subDir, srcDirs, deps, workingSets, d, theLicense=None, **kwargs):
        super().__init__(suite, name, subDir, srcDirs, deps, workingSets, d, theLicense, **kwargs)
        if mx.suite("sulong-managed", fatalIfMissing=False) is not None:
            self.buildDependencies.append('sulong-managed:SULONG_MANAGED_HOME')

    def getBuildTask(self, args):
        return GraalpythonCAPIBuildTask(args, self)


def checkout_find_version_for_graalvm(args):
    """
    A quick'n'dirty way to check out the revision of the project at the given
    path to one that imports the same truffle/graal as we do. The assumption is
    the such a version can be reached by following the HEAD^ links
    """
    path = args[0]
    projectname = os.path.basename(args[0])
    suite = os.path.join(path, "mx.%s" % projectname, "suite.py")
    basedir = os.path.dirname(suite)
    while not os.path.isdir(os.path.join(basedir, ".git")):
        basedir = os.path.dirname(basedir)
    suite = os.path.relpath(suite, start=basedir)
    mx.log("Using %s to find revision" % suite)
    other_version = ""
    for i in SUITE.suite_imports:
        if i.name == "sulong":
            needed_version = i.version
            break
    current_commit = SUITE.vc.tip(path).strip()
    current_revision = "HEAD"
    mx.log("Searching %s commit that imports graal repository at %s" % (projectname, needed_version))
    while needed_version != other_version:
        if other_version:
            parent = SUITE.vc.git_command(path, ["show", "--pretty=format:%P", "-s", current_revision]).split()
            if not parent:
                mx.log("Got to oldest revision before finding appropriate commit, using %s" % current_commit)
                return
            current_revision = parent[0]
        contents = SUITE.vc.git_command(path, ["show", "%s:%s" % (current_revision, suite)])
        d = {}
        try:
            exec(contents, d, d) # pylint: disable=exec-used;
        except:
            mx.log("suite.py no longer parseable, falling back to %s" % current_commit)
            return
        suites = d["suite"]["imports"]["suites"]
        for suitedict in suites:
            if suitedict["name"] in ("compiler", "truffle", "regex", "sulong"):
                other_version = suitedict.get("version", "")
                if other_version:
                    break

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

    # headers go into 'com.oracle.graal.python.cext/include'
    header_dest = join(mx.project("com.oracle.graal.python.cext").dir, "include")

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
    tracker_file_dest = join(mx.project("com.oracle.graal.python.jni").dir, "src", "ctx_tracker.c")
    import_file(tracker_file_src, tracker_file_dest)

    # tests go to 'lib-graalpython/module/hpy/tests'
    test_files_dest = _hpy_test_root()
    import_files(hpy_repo_test_dir, test_files_dest)
    remove_inexistent_files(hpy_repo_test_dir, test_files_dest)

    # debug Python sources go into 'lib-graalpython/module/hpy/debug'
    debug_files_dest = join(_get_core_home(), "modules", "hpy", "debug")
    import_files(hpy_repo_debug_dir, debug_files_dest, exclude_subdir("src"))
    remove_inexistent_files(hpy_repo_debug_dir, debug_files_dest)

    # debug mode goes into 'com.oracle.graal.python.jni/src/debug'
    debugctx_src = join(hpy_repo_debug_dir, "src")
    debugctx_dest = join(mx.project("com.oracle.graal.python.jni").dir, "src", "debug")
    debugctx_hdr = join(debugctx_src, "include", "hpy_debug.h")
    import_files(debugctx_src, debugctx_dest, exclude_files(
        "autogen_debug_ctx_call.i", "debug_ctx_cpython.c", debugctx_hdr))
    import_file(debugctx_hdr, join(debugctx_dest, "hpy_debug.h"))

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


def run_leak_launcher(input_args, out=None):
    print("mx python-leak-test " + " ".join(input_args))

    args = input_args
    capi_home = _get_capi_home()
    args.insert(0, "--experimental-options")
    args.insert(0, "--python.CAPI=%s" % capi_home)

    env = os.environ.copy()
    env.setdefault("GRAAL_PYTHONHOME", _dev_pythonhome())

    dists = ['GRAALPYTHON', 'TRUFFLE_NFI', 'SULONG_NATIVE', 'GRAALPYTHON_UNIT_TESTS']

    vm_args, graalpython_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    vm_args += mx.get_runtime_jvm_args(dists)
    jdk = get_jdk()
    vm_args.append("com.oracle.graal.python.test.advance.LeakTest")
    retval = mx.run_java(vm_args + graalpython_args, jdk=jdk, env=env, nonZeroIsFatal=False, out=out)
    if retval == 0:
        print("PASSED")
        return True
    elif os.environ.get("CI") and "--keep-dump" not in input_args:
        # rerun once with heap dumping enabled
        out = mx.OutputCapture()
        run_leak_launcher(["--keep-dump"] + input_args, out=out)
        path = out.data.strip().partition("Dump file:")[2].strip()
        if path:
            save_path = os.path.join(SUITE.dir, "dumps", "leak_test")
            try:
                os.makedirs(save_path)
            except OSError:
                pass
            dest = shutil.copy(path, save_path)
            print("Heapdump file kept in " + dest)
        return False
    else:
        print("FAILED")
        return False


def no_return(fn):
    @wraps(fn)
    def inner(*args, **kwargs):
        fn(*args, **kwargs)
    return inner


# ----------------------------------------------------------------------------------------------------------------------
#
# register the suite commands (if any)
#
# ----------------------------------------------------------------------------------------------------------------------
mx.update_commands(SUITE, {
    'python-build-watch': [python_build_watch, ''],
    'python': [python, '[Python args|@VM options]'],
    'python3': [python, '[Python args|@VM options]'],
    'deploy-binary-if-master': [deploy_binary_if_main, ''],
    'python-gate': [python_gate, '--tags [gates]'],
    'python-update-import': [update_import_cmd, '[--no-pull] [--no-push] [import-name, default: truffle]'],
    'python-style': [python_style_checks, '[--fix] [--no-spotbugs]'],
    'python-svm': [no_return(python_svm), ''],
    'python-gvm': [no_return(python_gvm), ''],
    'python-unittests': [python3_unittests, ''],
    'python-compare-unittests': [compare_unittests, ''],
    'python-retag-unittests': [retag_unittests, ''],
    'python-run-cpython-unittest': [run_cpython_test, '[-k TEST_PATTERN] [--gvm] [--svm] [--all] TESTS'],
    'python-update-unittest-tags': [update_unittest_tags, ''],
    'python-import-for-graal': [checkout_find_version_for_graalvm, ''],
    'nativebuild': [nativebuild, ''],
    'nativeclean': [nativeclean, ''],
    'python-src-import': [import_python_sources, ''],
    'python-coverage': [python_coverage, ''],
    'punittest': [punittest, ''],
    'graalpytest': [graalpytest, '[-h] [-v] [--python PYTHON] [-k TEST_PATTERN] [TESTS]'],
    'clean': [python_clean, ''],
    'python-update-hpy-import': [update_hpy_import_cmd, '[--no-pull] PATH_TO_HPY'],
    'bisect-benchmark': [mx_graalpython_bisect.bisect_benchmark, ''],
    'python-leak-test': [run_leak_launcher, ''],
    'python-checkcopyrights': [python_checkcopyrights, '[--fix]'],
})
