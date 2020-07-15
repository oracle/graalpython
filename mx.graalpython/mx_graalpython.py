# Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
import glob
import itertools
import json
import os
import platform
import re
import shutil
import sys

HPY_IMPORT_ORPHAN_BRANCH_NAME = "hpy-import"

PY3 = sys.version_info[0] == 3 # compatibility between Python versions
import tempfile
if PY3:
    import urllib.request as urllib_request
else:
    import urllib2 as urllib_request
from argparse import ArgumentParser

import mx
import mx_benchmark
import mx_gate
import mx_unittest
import mx_sdk
import mx_subst
import mx_urlrewrites
from mx_gate import Task
from mx_graalpython_bench_param import PATH_MESO, BENCHMARKS, JBENCHMARKS
from mx_graalpython_benchmark import PythonBenchmarkSuite, python_vm_registry, CPythonVm, PyPyVm, JythonVm, GraalPythonVm, \
    CONFIGURATION_DEFAULT, CONFIGURATION_SANDBOXED, CONFIGURATION_NATIVE, \
    CONFIGURATION_DEFAULT_MULTI, CONFIGURATION_SANDBOXED_MULTI, CONFIGURATION_NATIVE_MULTI, \
    PythonInteropBenchmarkSuite


if not sys.modules.get("__main__"):
    # workaround for pdb++
    sys.modules["__main__"] = type(sys)("<empty>")


SUITE = mx.suite('graalpython')
SUITE_COMPILER = mx.suite("compiler", fatalIfMissing=False)
SUITE_SULONG = mx.suite("sulong")


if PY3:
    raw_input = input # pylint: disable=redefined-builtin;


def _get_core_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-graalpython")


def _get_stdlib_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-python", "3")


def _get_capi_home():
    return mx.dependency("com.oracle.graal.python.cext").get_output_root()


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


def do_run_python(args, extra_vm_args=None, env=None, jdk=None, extra_dists=None, cp_prefix=None, cp_suffix=None, **kwargs):
    if not any(arg.startswith("--python.CAPI") for arg in args):
        capi_home = _get_capi_home()
        args.insert(0, "--experimental-options")
        args.insert(0, "--python.CAPI=%s" % capi_home)

    if not env:
        env = os.environ.copy()
    env.setdefault("GRAAL_PYTHONHOME", _dev_pythonhome())

    check_vm_env = env.get('GRAALPYTHON_MUST_USE_GRAAL', False)
    if check_vm_env:
        if check_vm_env == '1':
            check_vm(must_be_jvmci=True)
        elif check_vm_env == '0':
            check_vm()

    dists = ['GRAALPYTHON', 'TRUFFLE_NFI', 'SULONG']

    vm_args, graalpython_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
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

    vm_args.append("com.oracle.graal.python.shell.GraalPythonMain")
    return mx.run_java(vm_args + graalpython_args, jdk=jdk, env=env, **kwargs)


def _pythonhome_context():
    return set_env(GRAAL_PYTHONHOME=mx.dependency("GRAALPYTHON_GRAALVM_SUPPORT").get_output())


def _dev_pythonhome_context():
    home = os.environ.get("GRAAL_PYTHONHOME", _dev_pythonhome())
    return set_env(GRAAL_PYTHONHOME=home)


def _dev_pythonhome():
    return os.path.join(SUITE.dir, "graalpython")


def punittest(ars):
    args = []
    if "--regex" not in ars:
        args += ['--regex', r'(graal\.python)|(com\.oracle\.truffle\.tck\.tests)']
    args += ars
    with _pythonhome_context():
        mx_unittest.unittest(args)


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


def run_cpython_test(args):
    import glob
    interp_args = []
    globs = []
    test_args = []
    for arg in args:
        if arg.startswith("-"):
            if not globs:
                interp_args.append(arg)
            else:
                test_args.append(arg)
        else:
            globs.append(arg)
    testfiles = []
    for g in globs:
        testfiles += glob.glob(os.path.join(SUITE.dir, "graalpython/lib-python/3/test", "%s*" % g))
    mx.run([python_gvm()] + interp_args + [
        os.path.join(SUITE.dir, "graalpython/com.oracle.graal.python.test/src/tests/run_cpython_test.py"),
    ] + test_args + testfiles)


def retag_unittests(args):
    """run the cPython stdlib unittests"""
    parser = ArgumentParser('mx python-retag-unittests')
    parser.add_argument('--upload-results-to')
    parser.add_argument('--inspect', action='store_true')
    parser.add_argument('-debug-java', action='store_true')
    parsed_args, remaining_args = parser.parse_known_args(args)
    env = os.environ.copy()
    env.update(
        ENABLE_CPYTHON_TAGGED_UNITTESTS="true",
        PYTHONPATH=os.path.join(_dev_pythonhome(), 'lib-python/3'),
    )
    args = [
        '--experimental-options=true',
        '--python.CatchAllExceptions=true',
        '--python.WithThread=true']
    if parsed_args.inspect:
        args.append('--inspect')
    if parsed_args.debug_java:
        args.append('-debug-java')
    args += [
        'graalpython/com.oracle.graal.python.test/src/tests/test_tagged_unittests.py',
        '--retag'
    ]
    mx.run([python_gvm()] + args + remaining_args, env=env)
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
                if not mx.ask_yes_no('Download failed! please download %s manually to %s and type (y) to continue.' % (url, d), default='y'):
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

    tag_blacklist = {
        # This test times out in the gate even though it succeeds locally and in the retagger. Race condition?
        ('test_cprofile.txt', '*graalpython.lib-python.3.test.test_cprofile.CProfileTest.test_run_profile_as_module'),
        # The following two try to read bytecode and fail randomly as our co_code is changing
        ('test_modulefinder.txt', '*graalpython.lib-python.3.test.test_modulefinder.ModuleFinderTest.test_bytecode'),
        ('test_modulefinder.txt', '*graalpython.lib-python.3.test.test_modulefinder.ModuleFinderTest.test_relative_imports_4'),
        # Temporarily disabled due to object identity or race condition (GR-24863)
        ('test_weakref.txt', '*graalpython.lib-python.3.test.test_weakref.MappingTestCase.test_threaded_weak_key_dict_deepcopy'),
    }

    result_tags = linux_tags & darwin_tags - tag_blacklist
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


AOT_INCOMPATIBLE_TESTS = ["test_interop.py"]


class GraalPythonTags(object):
    junit = 'python-junit'
    unittest = 'python-unittest'
    unittest_sandboxed = 'python-unittest-sandboxed'
    unittest_multi = 'python-unittest-multi-context'
    unittest_jython = 'python-unittest-jython'
    unittest_hpy = 'python-unittest-hpy'
    unittest_hpy_sandboxed = 'python-unittest-hpy-sandboxed'
    tagged = 'python-tagged-unittest'
    svmunit = 'python-svm-unittest'
    svmunit_sandboxed = 'python-svm-unittest-sandboxed'
    shared_object = 'python-so'
    shared_object_sandboxed = 'python-so-sandboxed'
    graalvm = 'python-graalvm'
    graalvm_sandboxed = 'python-graalvm-sandboxed'
    svm = 'python-svm'
    native_image_embedder = 'python-native-image-embedder'
    license = 'python-license'


def python_gate(args):
    if not os.environ.get("JDT"):
        find_jdt()
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


python_gate.__doc__ = 'Custom gates are %s' % ", ".join([getattr(GraalPythonTags, t) for t in dir(GraalPythonTags) if not t.startswith("__")])


def find_jdt():
    pardir = os.path.abspath(os.path.join(SUITE.dir, ".."))
    for f in [os.path.join(SUITE.dir, f) for f in os.listdir(SUITE.dir)] + [os.path.join(pardir, f) for f in os.listdir(pardir)]:
        if os.path.basename(f).startswith("ecj-") and os.path.basename(f).endswith(".jar"):
            mx.log("Automatically choosing %s for JDT" % f)
            os.environ["JDT"] = f
            return


def find_eclipse():
    pardir = os.path.abspath(os.path.join(SUITE.dir, ".."))
    for f in [os.path.join(SUITE.dir, f) for f in os.listdir(SUITE.dir)] + [os.path.join(pardir, f) for f in os.listdir(pardir)]:
        if os.path.basename(f) == "eclipse" and os.path.isdir(f):
            mx.log("Automatically choosing %s for Eclipse" % f)
            eclipse_exe = os.path.join(f, "eclipse")
            if os.path.exists(eclipse_exe):
                os.environ["ECLIPSE_EXE"] = eclipse_exe
                return


@contextlib.contextmanager
def set_env(**environ):
    "Temporarily set the process environment variables"
    old_environ = dict(os.environ)
    os.environ.update(environ)
    try:
        yield
    finally:
        os.environ.clear()
        os.environ.update(old_environ)


def python_gvm(args=None, **kwargs):
    "Build and run a GraalVM graalpython launcher"
    return _python_graalvm_launcher(args or [], **kwargs)


def python_svm(args=None, **kwargs):
    "Build and run the native graalpython image"
    with set_env(FORCE_BASH_LAUNCHERS="lli,native-image,gu,graalvm-native-clang,graalvm-native-clang++", DISABLE_LIBPOLYGLOT="true", DISABLE_POLYGLOT="true"):
        return _python_graalvm_launcher((args or []) + ["svm"], **kwargs)


def python_so(args):
    "Build the native shared object that includes graalpython"
    with set_env(FORCE_BASH_LAUNCHERS="true", DISABLE_LIBPOLYGLOT="false", DISABLE_POLYGLOT="true"):
        return _python_graalvm_launcher((args or []) + ["svm"])


def _python_graalvm_launcher(args, extra_dy=None):
    dy = "/vm,/tools"
    if extra_dy:
        dy += "," + extra_dy
    if "sandboxed" in args:
        args.remove("sandboxed")
        dy += ",/sulong-managed,/graalpython-enterprise"
    if "svm" in args:
        args.remove("svm")
        dy += ",/substratevm"
    dy = ["--dynamicimports", dy]
    mx.run_mx(dy + ["build"])
    out = mx.OutputCapture()
    mx.run_mx(dy + ["graalvm-home"], out=mx.TeeOutputCapture(out))
    launcher = os.path.join(out.data.strip(), "bin", "graalpython").split("\n")[-1].strip()
    mx.log(launcher)
    if args:
        mx.run([launcher] + args)
    return launcher


def _graalpytest_driver():
    return os.path.join(SUITE.dir, "graalpython", "com.oracle.graal.python.test", "src", "graalpytest.py")


def _graalpytest_root():
    return os.path.join(mx.dependency("com.oracle.graal.python.test").get_output_root(), "bin", "tests")


def _hpy_test_root():
    return os.path.join(_get_core_home(), "modules", "hpy", "test")


def run_python_unittests(python_binary, args=None, paths=None, aot_compatible=True, exclude=None, env=None):
    args = args or []
    args = ["--experimental-options=true",
            "--python.CatchAllExceptions=true"] + args
    exclude = exclude or []
    paths = paths or [_graalpytest_root()]
    if env is None:
        env = os.environ.copy()

    # list of excluded tests
    if aot_compatible:
        exclude += AOT_INCOMPATIBLE_TESTS

    def is_included(path):
        if path.endswith(".py"):
            basename = os.path.basename(path)
            return basename.startswith("test_") and basename not in exclude
        return False

    # list all 1st-level tests and exclude the SVM-incompatible ones
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

    args += [_graalpytest_driver(), "-v"]

    agent_args = " ".join(mx_gate.get_jacoco_agent_args() or [])
    if agent_args:
        # if we leave the excludes, the string is too long and it will be ignored by
        # the JVM, which ignores JAVA_TOOL_OPTIONS long than 1024 chars silently on JDK8
        agent_args = re.sub("excludes=[^,]+,", "", agent_args)
        # we know these can be excluded
        agent_args += ",excludes=*NodeGen*:*LibraryGen*:*BuiltinsFactory*"
        assert len(agent_args) < 1024
        # jacoco only dumps the data on exit, and when we run all our unittests
        # at once it generates so much data we run out of heap space
        env['JAVA_TOOL_OPTIONS'] = agent_args
        for testfile in testfiles:
            mx.run([python_binary] + args + [testfile], nonZeroIsFatal=True, env=env)
    else:
        args += testfiles
        mx.logv(" ".join([python_binary] + args))
        return mx.run([python_binary] + args, nonZeroIsFatal=True, env=env)


def graalpython_gate_runner(args, tasks):
    # JUnit tests
    with Task('GraalPython JUnit', tasks, tags=[GraalPythonTags.junit]) as task:
        if task:
            punittest(['--verbose'])

    # Unittests on JVM
    with Task('GraalPython Python unittests', tasks, tags=[GraalPythonTags.unittest]) as task:
        if task:
            if platform.system() != 'Darwin' and not mx_gate.get_jacoco_agent_args():
                # TODO: drop condition when python3 is available on darwin
                mx.log("Running tests with CPython")
                test_args = [_graalpytest_driver(), "-v", _graalpytest_root()]
                mx.run(["python3"] + test_args, nonZeroIsFatal=True)
            mx.run(["env"])
            run_python_unittests(python_gvm())

    with Task('GraalPython sandboxed tests', tasks, tags=[GraalPythonTags.unittest_sandboxed]) as task:
        if task:
            run_python_unittests(python_gvm(["sandboxed"]), args=["--llvm.managed"])

    with Task('GraalPython multi-context unittests', tasks, tags=[GraalPythonTags.unittest_multi]) as task:
        if task:
            run_python_unittests(python_gvm(), args=["-multi-context"])

    with Task('GraalPython Jython emulation tests', tasks, tags=[GraalPythonTags.unittest_jython]) as task:
        if task:
            run_python_unittests(python_gvm(), args=["--python.EmulateJython"], paths=["test_interop.py"])

    with Task('GraalPython HPy tests', tasks, tags=[GraalPythonTags.unittest_hpy]) as task:
        if task:
            run_python_unittests(python_gvm(), paths=[_hpy_test_root()])

    with Task('GraalPython HPy sandboxed tests', tasks, tags=[GraalPythonTags.unittest_hpy_sandboxed]) as task:
        if task:
            run_python_unittests(python_gvm(["sandboxed"]), args=["--llvm.managed"], paths=[_hpy_test_root()])

    with Task('GraalPython Python tests', tasks, tags=[GraalPythonTags.tagged]) as task:
        if task:
            env = os.environ.copy()
            env.update(
                ENABLE_CPYTHON_TAGGED_UNITTESTS="true",
                ENABLE_THREADED_GRAALPYTEST="true",
                PYTHONPATH=os.path.join(_dev_pythonhome(), 'lib-python/3'),
            )
            run_python_unittests(
                python_gvm(),
                args=["-v",
                      "--python.WithThread=true"],
                paths=["test_tagged_unittests.py"],
                env=env,
            )

    # Unittests on SVM
    with Task('GraalPython tests on SVM', tasks, tags=[GraalPythonTags.svmunit]) as task:
        if task:
            run_python_unittests(python_svm())

    with Task('GraalPython sandboxed tests on SVM', tasks, tags=[GraalPythonTags.svmunit_sandboxed]) as task:
        if task:
            run_python_unittests(python_svm(["sandboxed"]), args=["--llvm.managed"])

    with Task('GraalPython license header update', tasks, tags=[GraalPythonTags.license]) as task:
        if task:
            python_checkcopyrights([])

    with Task('GraalPython GraalVM shared-library build', tasks, tags=[GraalPythonTags.shared_object, GraalPythonTags.graalvm]) as task:
        if task:
            run_shared_lib_test()

    with Task('GraalPython GraalVM sandboxed shared-library build', tasks, tags=[GraalPythonTags.shared_object_sandboxed, GraalPythonTags.graalvm_sandboxed]) as task:
        if task:
            run_shared_lib_test(["sandboxed"])

    with Task('GraalPython GraalVM build', tasks, tags=[GraalPythonTags.svm, GraalPythonTags.graalvm]) as task:
        if task:
            svm_image = python_svm(["--version"])
            benchmark = os.path.join(PATH_MESO, "image-magix.py")
            out = mx.OutputCapture()
            mx.run([svm_image, "-v", "-S", "--log.python.level=FINEST", benchmark], nonZeroIsFatal=True, out=mx.TeeOutputCapture(out))
            success = "\n".join([
                "[0, 0, 0, 0, 0, 0, 10, 10, 10, 0, 0, 10, 3, 10, 0, 0, 10, 10, 10, 0, 0, 0, 0, 0, 0]",
            ])
            if success not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not match success pattern:\n' + success)
            # Test that stdlib paths are not cached on packages
            out = mx.OutputCapture()
            mx.run([svm_image, "-v", "-S", "--log.python.level=FINEST", "--python.StdLibHome=/foobar", "-c", "import encodings; print(encodings.__path__)"], out=mx.TeeOutputCapture(out))
            if "/foobar" not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not have patched std lib path "/foobar"')
            # Test that stdlib paths are not cached on modules
            out = mx.OutputCapture()
            mx.run([svm_image, "-v", "-S", "--log.python.level=FINEST", "--python.StdLibHome=/foobar", "-c", "import encodings; print(encodings.__file__)"], out=mx.TeeOutputCapture(out))
            if "/foobar" not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not have patched std lib path "/foobar"')
            # Finally, test that we can start even if the graalvm was moved
            out = mx.OutputCapture()
            graalvm_home = svm_image.replace(os.path.sep.join(["", "bin", "graalpython"]), "")
            new_graalvm_home = graalvm_home + "_new"
            shutil.move(graalvm_home, new_graalvm_home)
            launcher = os.path.join(new_graalvm_home, "bin", "graalpython")
            mx.log(launcher)
            mx.run([launcher, "--log.python.level=FINE", "-S", "-c", "print(b'abc'.decode('ascii'))"], out=mx.TeeOutputCapture(out), err=mx.TeeOutputCapture(out))
            assert "Using preinitialized context." in out.data

    with Task('GraalPython GraalVM native embedding', tasks, tags=[GraalPythonTags.svm, GraalPythonTags.graalvm, GraalPythonTags.native_image_embedder]) as task:
        if task:
            run_embedded_native_python_test()


mx_gate.add_gate_runner(SUITE, graalpython_gate_runner)


def run_embedded_native_python_test(args=None):
    """
    Test that embedding an engine where a context was initialized at native image
    build-time is enough to create multiple contexts from that engine without
    those contexts having access to the core files, due to caching in the shared
    engine.
    """
    with mx.TempDirCwd(os.getcwd()) as dirname:
        python_launcher = python_svm()
        graalvm_javac = os.path.join(os.path.dirname(python_launcher), "javac")
        graalvm_native_image = os.path.join(os.path.dirname(python_launcher), "native-image")

        filename = os.path.join(dirname, "HelloWorld.java")
        with open(filename, "w") as f:
            f.write("""
            import org.graalvm.polyglot.*;

            public class HelloWorld {
                static final Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("log.python.level", "FINEST").build();
                static {
                   try (Context contextNull = Context.newBuilder("python").engine(engine).build()) {
                       contextNull.initialize("python");
                   }
                }

                public static void main(String[] args) {
                    try (Context context1 = Context.newBuilder("python").engine(engine).build()) {
                        context1.eval("python", "print(b'abc'.decode('ascii'))");
                        try (Context context2 = Context.newBuilder("python").engine(engine).build()) {
                            context2.eval("python", "print(b'xyz'.decode('ascii'))");
                        }
                    }
                }
            }
            """)
        out = mx.OutputCapture()
        mx.run([graalvm_javac, filename])
        mx.run([graalvm_native_image, "-H:+ReportExceptionStackTraces", "--initialize-at-build-time", "--language:python", "HelloWorld"])
        mx.run(["./helloworld"], out=mx.TeeOutputCapture(out))
        assert "abc" in out.data
        assert "xyz" in out.data


def run_shared_lib_test(args=None):
    if args is None:
        args = []
    launcher = python_so(args)
    svm_lib_path = os.path.abspath(os.path.join(launcher, "..", "..", "jre", "lib", "polyglot"))
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
        run_env = {"LD_LIBRARY_PATH": svm_lib_path, "GRAAL_PYTHONHOME": _dev_pythonhome()}
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


def deploy_binary_if_master(args):
    """if the active branch is 'master', deploy binaries for the primary suite to remote maven repository."""
    master_branch = 'master'
    active_branch = mx.VC.get_vc(SUITE.dir).active_branch(SUITE.dir)
    if active_branch == master_branch:
        if sys.platform == "darwin":
            args.insert(0, "--platform-dependent")
        return mx.command_function('deploy-binary')(args)
    else:
        mx.log('The active branch is "%s". Binaries are deployed only if the active branch is "%s".' % (
            active_branch, master_branch))
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


mx_subst.path_substitutions.register_with_arg('suite', _get_suite_dir)
mx_subst.path_substitutions.register_with_arg('src_dir', _get_src_dir)


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
            mx.abort("repo %s is not on master or on %s" % (d, current_branch))

    # make sure we can update the overlays
    overlaydir = join(SUITE.dir, "..", "ci-overlays")
    if not os.path.exists(overlaydir):
        mx.abort("Overlays repo must be next to graalpython repo")
        vc = mx.VC.get_vc(overlaydir)
    if vc.isDirty(overlaydir):
        mx.abort("overlays repo must be clean")
    overlaybranch = vc.active_branch(overlaydir)
    if overlaybranch == "master":
        vc.pull(overlaydir)
        vc.set_branch(overlaydir, current_branch, with_remote=False)
        vc.git_command(overlaydir, ["checkout", current_branch], abortOnError=True)
    elif overlaybranch == current_branch:
        pass
    else:
        mx.abort("overlays repo must be on master or branch %s" % current_branch)

    # find all imports we might update
    imports_to_update = set()
    for suite_py in suite_py_files:
        d = {}
        with open(suite_py) as f:
            exec(f.read(), d, d) # pylint: disable=exec-used;
        for suite in d["suite"].get("imports", {}).get("suites", []):
            import_name = suite["name"]
            if suite.get("version") and import_name not in local_names:
                imports_to_update.add(import_name)

    # now update all imports
    for name in imports_to_update:
        for idx, suite_py in enumerate(suite_py_files):
            update_import(name, suite_py, rev=("HEAD" if idx else "origin/master"))

    # copy files we inline from our imports
    shutil.copy(
        join(mx.suite("truffle").dir, "..", "common.json"),
        join(overlaydir, "python", "graal-common.json"))

    # update vm-tests.json vm version
    with open(join(overlaydir, "python", "graal-common.json"), 'r') as fp:
        d = json.load(fp)
        oraclejdk8_ver = d['jdks']['oraclejdk8']['version']

    with open(join(overlaydir, "python", "vm-tests.json"), 'r') as fp:
        d = json.load(fp)
        d['downloads']['JAVA_HOME']['version'] = oraclejdk8_ver

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
    for repo in repos_updated:
        try:
            mx._opts.very_verbose = True
            vc.git_command(repo, ["push", "-u", "origin", "HEAD:%s" % current_branch], abortOnError=True)
        finally:
            mx._opts.very_verbose = prev_verbosity

    if repos_updated:
        mx.log("\n  ".join(["These repos were updated:"] + repos_updated))


def python_style_checks(args):
    "Check (and fix where possible) copyrights, eclipse formatting, and spotbugs"
    python_checkcopyrights(["--fix"] if "--fix" in args else [])
    if not os.environ.get("ECLIPSE_EXE"):
        find_eclipse()
    if os.environ.get("ECLIPSE_EXE"):
        mx.command_function("eclipseformat")([])
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
            if "lib-python/3" in line or "com.oracle.graal.python.test/testData" in line:
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


def _python_checkpatchfiles():
    listfilename = tempfile.mktemp()
    # additionally, we want to check if the packages we are patching all have a permissive license
    with open(listfilename, "w") as listfile:
        mx.run(["git", "ls-tree", "-r", "HEAD", "--name-only"], out=listfile)
    try:
        pypi_base_url = mx_urlrewrites.rewriteurl("https://pypi.org/packages/").replace("packages/", "")
        with open(listfilename, "r") as listfile:
            content = listfile.read()
        patchfile_pattern = re.compile(r"lib-graalpython/patches/([^/]+)/(sdist|whl)/.*\.patch")
        checked = set()
        allowed_licenses = ["MIT", "BSD", "MIT license"]
        for line in content.split("\n"):
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
        "memoryobject.c": "_memoryview.c",
        "_sre.c": "_cpython_sre.c",
        "unicodedata.c": "_cpython_unicodedata.c",
        "_bz2module.c": "_bz2.c",
        "mmapmodule.c": "_mmap.c",
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

    7. Run the tests and fix any remaining issues.
    8. You should push the python-import branch using:

           git push origin python-import:python-import

    NOTE: Your changes, untracked files and ignored files will be stashed for the
    duration this operation. If you abort this script, you can recover them by
    moving back to your branch and using git stash pop. It is recommended that you
    close your IDE during the operation.
    """.format(mapping))
    raw_input("Got it?")

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
    raw_input("Check that the updated files look as intended, then press RETURN...")
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
    name='Graal.Python license files',
    short_name='pynl',
    dir_name='python',
    dependencies=[],
    license_files=['LICENSE_GRAALPYTHON.txt'],
    third_party_license_files=['THIRD_PARTY_LICENSE_GRAALPYTHON.txt'],
    truffle_jars=[],
    support_distributions=[
        'graalpython:GRAALPYTHON_GRAALVM_LICENSES',
    ],
    priority=5
))


mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=SUITE,
    name='Graal.Python',
    short_name='pyn',
    dir_name='python',
    standalone_dir_name='graalpython-<version>-<graalvm_os>-<arch>',
    license_files=[],
    third_party_license_files=[],
    dependencies=['pynl', 'Truffle', 'Sulong', 'LLVM.org toolchain', 'TRegex'],
    standalone_dependencies={
        'Sulong': ('lib/sulong', ['bin/<exe:lli>']),
        'LLVM.org toolchain': ('lib/llvm-toolchain', []),
        'Graal.Python license files': ('', []),
    },
    truffle_jars=[
        'graalpython:GRAALPYTHON',
    ],
    support_distributions=[
        'graalpython:GRAALPYTHON_GRAALVM_SUPPORT',
        'graalpython:GRAALPYTHON_GRAALVM_DOCS',
    ],
    launcher_configs=[
        mx_sdk.LanguageLauncherConfig(
            destination='bin/<exe:graalpython>',
            jar_distributions=['graalpython:GRAALPYTHON-LAUNCHER'],
            main_class='com.oracle.graal.python.shell.GraalPythonMain',
            # build_args=['-H:+RemoveSaturatedTypeFlows'],
            build_args=[
                '-H:+TruffleCheckBlackListedMethods',
                '-H:+DetectUserDirectoriesInImageHeap',
            ],
            language='python',
        )
    ],
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
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_DEFAULT_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context',
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_SANDBOXED, extra_polyglot_args=[
        '--llvm.managed',
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE, extra_polyglot_args=[
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_SANDBOXED_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context', '--llvm.managed',
    ]), SUITE, 10)
    python_vm_registry.add_vm(GraalPythonVm(config_name=CONFIGURATION_NATIVE_MULTI, extra_polyglot_args=[
        '--experimental-options', '-multi-context',
    ]), SUITE, 10)


def _register_bench_suites(namespace):
    for py_bench_suite in PythonBenchmarkSuite.get_benchmark_suites(BENCHMARKS):
        mx_benchmark.add_bm_suite(py_bench_suite)
    for java_bench_suite in PythonInteropBenchmarkSuite.get_benchmark_suites(JBENCHMARKS):
        mx_benchmark.add_bm_suite(java_bench_suite)


def mx_post_parse_cmd_line(namespace):
    # all projects are now available at this time
    _register_vms(namespace)
    _register_bench_suites(namespace)


def python_coverage(args):
    "Generate coverage report for our unittests"
    parser = ArgumentParser(prog='mx python-coverage')
    parser.add_argument('--jacoco', action='store_true', help='do generate Jacoco coverage')
    parser.add_argument('--truffle', action='store_true', help='do generate Truffle coverage')
    parser.add_argument('--truffle-upload-url', help='Format is like rsync: user@host:/directory', default=None)
    args = parser.parse_args(args)

    if args.jacoco:
        jacoco_args = [
            '--jacoco-whitelist-package', 'com.oracle.graal.python',
        ]
        jacoco_gates = (
            GraalPythonTags.junit,
            GraalPythonTags.unittest,
            GraalPythonTags.unittest_multi,
            GraalPythonTags.unittest_jython,
            GraalPythonTags.tagged,
        )
        mx.run_mx(jacoco_args + [
            '--strict-compliance',
            '--primary', 'gate',
            '-B=--force-deprecation-as-warning-for-dependencies',
            '--strict-mode',
            '--tags', ",".join(['%s'] * len(jacoco_gates)) % jacoco_gates,
            '--jacocout', 'html',
        ])
        if mx.get_env("SONAR_HOST_URL", None):
            mx.run_mx(jacoco_args + [
                'sonarqube-upload',
                '-Dsonar.host.url=%s' % mx.get_env("SONAR_HOST_URL"),
                '-Dsonar.projectKey=com.oracle.graalvm.python',
                '-Dsonar.projectName=GraalVM - Python',
                '--exclude-generated',
            ])
        mx.run_mx(jacoco_args + [
            'coverage-upload',
        ])
    if args.truffle:
        executable = python_gvm()
        variants = [
            {"args": []},
            {"args": ["--python.EmulateJython"], "paths": ["test_interop.py"]},
            # {"args": ["--llvm.managed"]},
            {
                "args": ["-v", "--python.WithThread=true"],
                "paths": ["test_tagged_unittests.py"],
                "tagged": True
            },
        ]
        outputlcov = "coverage.lcov"
        if os.path.exists(outputlcov):
            os.unlink(outputlcov)
        cmdargs = ["/usr/bin/env", "lcov", "-o", outputlcov]
        prefix = os.path.join(SUITE.dir, "graalpython")
        for kwds in variants:
            variant_str = re.sub(r"[^a-zA-Z]", "_", str(kwds))
            for pattern in ["py"]:
                outfile = os.path.join(SUITE.dir, "coverage_%s_%s_$$.lcov" % (variant_str, pattern))
                if os.path.exists(outfile):
                    os.unlink(outfile)
                extra_args = [
                    "--coverage",
                    "--coverage.TrackInternal",
                    "--coverage.FilterFile=%s/*.%s" % (prefix, pattern),
                    "--coverage.Output=lcov",
                    "--coverage.OutputFile=%s" % outfile,
                ]
                with set_env(GRAAL_PYTHON_ARGS=" ".join(extra_args)):
                    with _dev_pythonhome_context(): # run all our tests in the dev-home, so that lcov has consistent paths
                        kwds["args"].append("--python.CAPI=" + _get_capi_home())
                        if kwds.pop("tagged", False):
                            with set_env(ENABLE_CPYTHON_TAGGED_UNITTESTS="true", ENABLE_THREADED_GRAALPYTEST="true"):
                                run_python_unittests(executable, **kwds)
                        else:
                            run_python_unittests(executable, **kwds)

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
            with open(fullname, 'r') as f:
                try:
                    compile(f.read(), fullname, 'exec')
                except BaseException as e:
                    print('Could not compile', fullname, e)
            """.format(os.path.join(prefix, "lib-python")))
            f.flush()
            with _dev_pythonhome_context():
                mx.run([
                    executable,
                    "-S",
                    "--experimental-options",
                    "--coverage",
                    "--coverage.TrackInternal",
                    "--coverage.FilterFile=%s/*.py" % prefix,
                    "--coverage.Output=lcov",
                    "--coverage.OutputFile=zero.lcov",
                    f.name
                ])

        # merge all generated lcov files
        for f in os.listdir(SUITE.dir):
            if f.endswith(".lcov"):
                cmdargs += ["-a", f]

        mx.run(cmdargs)
        primary = mx.primary_suite()
        info = primary.vc.parent_info(primary.dir)
        rev = primary.vc.parent(primary.dir)
        coverage_dir = '{}-truffle-coverage_{}_{}'.format(
            primary.name,
            datetime.datetime.fromtimestamp(info['author-ts']).strftime('%Y-%m-%d_%H_%M'),
            rev[:7],
        )
        mx.run(["/usr/bin/env", "genhtml", "--prefix", prefix, "--ignore-errors", "source", "-o", coverage_dir, outputlcov])
        if args.truffle_upload_url:
            if not args.truffle_upload_url.endswith("/"):
                args.truffle_upload_url = args.truffle_upload_url + "/"
            mx.run(["scp", "-r", coverage_dir, args.truffle_upload_url])


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


class GraalpythonCAPIBuildTask(mx.ProjectBuildTask):
    class PrefixingOutput():
        def __init__(self, prefix, printfunc):
            self.prefix = "[" + prefix + "] "
            self.printfunc = printfunc

        def __call__(self, line):
            # n.b.: mx already sends us the output line-by-line
            self.printfunc(self.prefix + line.rstrip())

    def __init__(self, args, project):
        jobs = min(mx.cpu_count(), 8)
        super(GraalpythonCAPIBuildTask, self).__init__(args, jobs, project)

    def __str__(self):
        return 'Building C API project {} with setuptools'.format(self.subject.name)

    def run(self, args, env=None, cwd=None, **kwargs):
        env = env.copy() if env else os.environ.copy()
        # n.b.: we don't want derived projects to also have to depend on our build env vars
        env.update(mx.dependency("com.oracle.graal.python.cext").getBuildEnv())
        env.update(self.subject.getBuildEnv())

        # distutils will honor env variables CC, CFLAGS, LDFLAGS but we won't allow to change them
        for var in ["CC", "CFLAGS", "LDFLAGS"]:
            env.pop(var, None)

        return do_run_python(args, env=env, cwd=cwd, out=self.PrefixingOutput(self.subject.name, mx.log), err=self.PrefixingOutput(self.subject.name, mx.log_error), **kwargs)

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

        cwd = os.path.join(self.subject.get_output_root(), "mxbuild_temp")
        args = []
        if mx._opts.verbose:
            args.append("-v")
        else:
            # always add "-q" if not verbose to suppress hello message
            args.append("-q")

        args += ["--python.WithThread", "-S", os.path.join(self.src_dir(), "setup.py"), self.subject.get_output_root()]
        mx.ensure_dir_exists(cwd)
        rc = self.run(args, cwd=cwd)
        shutil.rmtree(cwd) # remove the temporary build files
        return min(rc, 1)

    def src_dir(self):
        return self.subject.dir

    def needsBuild(self, newestInput):
        tsNewest = 0
        newestFile = None
        for root, _, files in os.walk(self.src_dir()):
            for f in files:
                ts = os.path.getmtime(os.path.join(root, f))
                if tsNewest < ts:
                    tsNewest = ts
                    newestFile = f
        tsOldest = sys.maxsize
        oldestFile = None
        for root, _, files in os.walk(self.subject.get_output_root()):
            for f in files:
                ts = os.path.getmtime(os.path.join(root, f))
                if tsOldest > ts:
                    tsOldest = ts
                    oldestFile = f
        if tsOldest == sys.maxsize:
            tsOldest = 0
        if tsOldest < tsNewest:
            self.clean() # we clean here, because setuptools doesn't check timestamps
            if newestFile and oldestFile:
                return (True, "rebuild needed, %s newer than %s" % (newestFile, oldestFile))
            else:
                return (True, "build needed")
        else:
            return (False, "up to date")

    def newestOutput(self):
        return None

    def clean(self, forBuild=False):
        result = 0
        try:
            shutil.rmtree(self._dev_headers_dir())
        except BaseException:
            result = 1
        try:
            shutil.rmtree(self.subject.get_output_root())
        except BaseException:
            result = 1
        return result


class GraalpythonCAPIProject(mx.Project):
    def __init__(self, suite, name, subDir, srcDirs, deps, workingSets, d, theLicense=None, **kwargs):
        context = 'project ' + name
        self.buildDependencies = mx.Suite._pop_list(kwargs, 'buildDependencies', context)
        if mx.suite("sulong-managed", fatalIfMissing=False) is not None:
            self.buildDependencies.append('sulong-managed:SULONG_MANAGED_HOME')
        super(GraalpythonCAPIProject, self).__init__(suite, name, subDir, srcDirs, deps, workingSets, d, theLicense, **kwargs)

    def getOutput(self, replaceVar=mx_subst.results_substitutions):
        return self.get_output_root()

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
        return GraalpythonCAPIBuildTask(args, self)

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        ret = {}
        if hasattr(self, 'buildEnv'):
            for key, value in self.buildEnv.items():
                ret[key] = replaceVar.substitute(value, dependency=self)
        return ret


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
    count = 0;
    for path in os.walk(SUITE.dir):
        for file in glob.iglob(os.path.join(path[0], '*.pyc')):
            count += 1
            os.remove(file)

    if count > 0:
        print ('Cleaning', count, "`*.pyc` files...")

def update_hpy_import_cmd(args):
    """Update our import of HPy sources."""
    parser = ArgumentParser('mx python-update-hpy-import')
    parser.add_argument('--pull', action='store_true', help='Perform a pull of the HPy repo first.', required=False)
    parser.add_argument('hpy_repo', metavar='HPY_REPO', help='Path to the HPy repo to import from.')
    parsed_args, remaining_args = parser.parse_known_args(args)

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
    hpy_repo_runtime_dir = join(hpy_repo_path, "hpy", "devel", "src")
    hpy_repo_test_dir = join(hpy_repo_path, "test")
    for d in [hpy_repo_path, hpy_repo_include_dir, hpy_repo_runtime_dir, hpy_repo_test_dir]:
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
    import_version = vc_git.git_command(hpy_repo_path, ["rev-parse", "--short", "HEAD"])
    mx.log("Determined HPy revision {}".format(import_version))

    if vc_git.isDirty(hpy_repo_path):
        res = raw_input("WARNING: your HPy repo is not clean. Do you want to proceed? (n/y) ")
        if str(res).strip().lower() != "y":
            return

    # switch to the HPy import orphan branch
    vc.git_command(SUITE.dir, ["checkout", HPY_IMPORT_ORPHAN_BRANCH_NAME])
    assert not SUITE.vc.isDirty(SUITE.dir)

    def import_files(from_dir, to_dir):
        mx.log("Importing HPy files from {}".format(from_dir))
        for dirpath, dirnames, filenames in os.walk(from_dir):
            relative_dir_path = os.path.relpath(dirpath, start=from_dir)
            # ignore dir 'cpython' and all its subdirs
            for filename in filenames:
                src_file = join(dirpath, filename)
                dest_file = join(to_dir, relative_dir_path, filename)
                mx.logv("Importing HPy file {} to {}".format(src_file, dest_file))

                # ensure that relative parent directories already exist (ignore existing)
                os.makedirs(os.path.dirname(dest_file), exist_ok=True)

                # copy file (overwrite existing)
                mx.copyfile(src_file, dest_file)
                # we may copy ignored files
                vc.add(SUITE.dir, dest_file, abortOnError=False)


    # headers go into 'com.oracle.graal.python.cext/include'
    header_dest = join(mx.dependency("com.oracle.graal.python.cext").dir, "include")

    # copy headers from .../hpy/hpy/devel/include' to 'header_dest'
    # but exclude subdir 'cpython' (since that's only for CPython)
    import_files(hpy_repo_include_dir, header_dest)

    # runtime sources go into 'lib-graalpython/module/hpy/src
    runtime_files_dest = join(_get_core_home(), "modules", "hpy", "src")
    import_files(hpy_repo_runtime_dir, runtime_files_dest)

    # tests go to 'lib-graalpython/module/hpy/tests
    test_files_dest = _hpy_test_root()
    import_files(hpy_repo_test_dir, test_files_dest)

    SUITE.vc.git_command(SUITE.dir, ["add", header_dest, runtime_files_dest, test_files_dest])
    raw_input("Check that the updated files look as intended, then press RETURN...")
    SUITE.vc.commit(SUITE.dir, "Update HPy inlined files: %s" % import_version)
    SUITE.vc.git_command(SUITE.dir, ["checkout", "-"])
    SUITE.vc.git_command(SUITE.dir, ["merge", HPY_IMPORT_ORPHAN_BRANCH_NAME])



# ----------------------------------------------------------------------------------------------------------------------
#
# register the suite commands (if any)
#
# ----------------------------------------------------------------------------------------------------------------------
mx.update_commands(SUITE, {
    'python-build-watch': [python_build_watch, ''],
    'python': [python, '[Python args|@VM options]'],
    'python3': [python, '[Python args|@VM options]'],
    'deploy-binary-if-master': [deploy_binary_if_master, ''],
    'python-gate': [python_gate, '--tags [gates]'],
    'python-update-import': [update_import_cmd, '[--no-pull] [import-name, default: truffle]'],
    'python-style': [python_style_checks, '[--fix]'],
    'python-svm': [python_svm, ''],
    'python-gvm': [python_gvm, ''],
    'python-unittests': [python3_unittests, ''],
    'python-compare-unittests': [compare_unittests, ''],
    'python-retag-unittests': [retag_unittests, ''],
    'python-run-cpython-unittest': [run_cpython_test, 'test-name'],
    'python-update-unittest-tags': [update_unittest_tags, ''],
    'python-import-for-graal': [checkout_find_version_for_graalvm, ''],
    'nativebuild': [nativebuild, ''],
    'nativeclean': [nativeclean, ''],
    'python-src-import': [import_python_sources, ''],
    'python-coverage': [python_coverage, ''],
    'punittest': [punittest, ''],
    'clean': [python_clean, ''],
    'python-update-hpy-import': [update_hpy_import_cmd, '[--no-pull] PATH_TO_HPY'],
})
