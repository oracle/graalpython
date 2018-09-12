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
import os
import platform
import re
import shutil
import sys
import tempfile
from argparse import ArgumentParser

import mx
import mx_benchmark
import mx_gate
import mx_sdk
import mx_subst
import mx_urlrewrites
from mx_gate import Task
from mx_graalpython_bench_param import PATH_MESO
from mx_graalpython_benchmark import PythonBenchmarkSuite
from mx_unittest import unittest

_suite = mx.suite('graalpython')
_mx_graal = mx.suite("compiler", fatalIfMissing=False)
_sulong = mx.suite("sulong")


def _get_core_home():
    return os.path.join(_suite.dir, "graalpython", "lib-graalpython")


def _get_stdlib_home():
    return os.path.join(_suite.dir, "graalpython", "lib-python", "3")


def _get_svm_binary():
    return os.path.join(_suite.dir, "graalpython-svm")


def __get_svm_binary_from_graalvm():
    vmdir = os.path.join(mx.suite("truffle").dir, "..", "vm")
    return os.path.join(vmdir, "mxbuild", "-".join([mx.get_os(), mx.get_arch()]), "graalpython.image", "graalpython")


def _extract_graalpython_internal_options(args):
    internal = []
    non_internal = []
    additional_dists = []
    for arg in args:
        # Class path extensions
        if arg.startswith('-add-dist='):
            additional_dists += [arg[10:]]

        # Debug flags
        elif arg == '-print-ast':
            internal += ["-Dcom.oracle.graal.python.PrintAST=true"]  # false

        elif arg == '-visualize-ast':
            internal += ["-Dcom.oracle.graal.python.VisualizedAST=true"]  # false

        elif arg.startswith('-print-ast='):
            internal += ["-Dcom.oracle.graal.python.PrintASTFilter=" + arg.replace('-print-ast=')]  # null

        elif arg == '-debug-trace':
            internal += ["-Dcom.oracle.graal.python.TraceJythonRuntime=true"]  # false
            internal += ["-Dcom.oracle.graal.python.TraceImports=true"]  # false
            internal += ["-Dcom.oracle.graal.python.TraceSequenceStorageGeneralization=true"]  # false
            internal += ["-Dcom.oracle.graal.python.TraceObjectLayoutCreation=true"]  # false
            internal += ["-Dcom.oracle.graal.python.TraceNodesWithoutSourceSection=true"]  # false
            internal += ["-Dcom.oracle.graal.python.TraceNodesUsingExistingProbe=true"]  # false

        elif arg == '-debug-junit':
            internal += ["-Dcom.oracle.graal.python.CatchGraalPythonExceptionForUnitTesting=true"]  # false

        # Object storage allocation """
        elif arg == '-instrument-storageAlloc':
            internal += ["-Dcom.oracle.graal.python.InstrumentObjectStorageAllocation=true"]  # false

        # Translation flags """
        elif arg == '-print-function':
            internal += ["-Dcom.oracle.graal.python.UsePrintFunction=true"]  # false

        # Runtime flags
        elif arg == '-no-sequence-unboxing':
            internal += ["-Dcom.oracle.graal.python.disableUnboxSequenceStorage=true"]  # true
            internal += ["-Dcom.oracle.graal.python.disableUnboxSequenceIteration=true"]  # true

        elif arg == '-no-intrinsify-calls':
            internal += ["-Dcom.oracle.graal.python.disableIntrinsifyBuiltinCalls=true"]  # true

        elif arg == '-flexible-object-storage':
            internal += ["-Dcom.oracle.graal.python.FlexibleObjectStorage=true"]  # false

        elif arg == '-flexible-storage-evolution':
            internal += ["-Dcom.oracle.graal.python.FlexibleObjectStorageEvolution=true"]  # false
            internal += ["-Dcom.oracle.graal.python.FlexibleObjectStorage=true"]  # false

        # Generators
        elif arg == '-no-inline-generator':
            internal += ["-Dcom.oracle.graal.python.disableInlineGeneratorCalls=true"]  # true
        elif arg == '-no-optimize-genexp':
            internal += ["-Dcom.oracle.graal.python.disableOptimizeGeneratorExpressions=true"]  # true
        elif arg == '-no-generator-peeling':
            internal += ["-Dcom.oracle.graal.python.disableInlineGeneratorCalls=true"]  # true
            internal += ["-Dcom.oracle.graal.python.disableOptimizeGeneratorExpressions=true"]  # true

        elif arg == '-trace-generator-peeling':
            internal += ["-Dcom.oracle.graal.python.TraceGeneratorInlining=true"]  # false

        # Other
        elif arg == '-force-long':
            internal += ["-Dcom.oracle.graal.python.forceLongType=true"]  # false

        elif arg == '-debug-perf' and _mx_graal:
            # internal += ['-Dgraal.InliningDepthError=500']
            # internal += ['-Dgraal.EscapeAnalysisIterations=3']
            # internal += ['-XX:JVMCINMethodSizeLimit=1000000']
            # internal += ['-Xms10g', '-Xmx16g']
            internal += ['-Dgraal.TraceTruffleCompilation=true']
            internal += ['-Dgraal.Dump=']
            # internal += ['-XX:CompileCommand=print,*OptimizedCallTarget.callRoot',
            #            '-XX:CompileCommand=exclude,*OptimizedCallTarget.callRoot',
            #            '-Dgraal.TruffleBackgroundCompilation=false']
            # internal += ['-Dgraal.TruffleCompileImmediately=true']
            internal += ['-Dgraal.TraceTrufflePerformanceWarnings=true']
            internal += ['-Dgraal.TruffleCompilationExceptionsArePrinted=true']
            # internal += ['-Dgraal.TruffleInliningMaxCallerSize=150']
            # internal += ['-Dgraal.InliningDepthError=10']
            # internal += ['-Dgraal.MaximumLoopExplosionCount=1000']
            # internal += ['-Dgraal.TruffleCompilationThreshold=100000']

        elif arg == '-compile-truffle-immediately' and _mx_graal:
            internal += ['-Dgraal.TruffleCompileImmediately=true']
            internal += ['-Dgraal.TruffleCompilationExceptionsAreThrown=true']

        else:
            non_internal += [arg]

    return internal, non_internal, additional_dists


def check_vm(vm_warning=True, must_be_jvmci=False):
    if not _mx_graal:
        if must_be_jvmci:
            print '** Error ** : graal compiler was not found!'
            sys.exit(1)

        if vm_warning:
            print '** warning ** : graal compiler was not found!! Executing using standard VM..'


def get_jdk():
    if _mx_graal:
        tag = 'jvmci'
    else:
        tag = None
    return mx.get_jdk(tag=tag)


def python(args):
    """run a Python program or shell"""
    do_run_python(args)


def do_run_python(args, extra_vm_args=None, env=None, jdk=None, **kwargs):
    if not env:
        env = os.environ

    check_vm_env = env.get('GRAALPYTHON_MUST_USE_GRAAL', False)
    if check_vm_env:
        if check_vm_env == '1':
            check_vm(must_be_jvmci=True)
        elif check_vm_env == '0':
            check_vm()

    dists = ['GRAALPYTHON']

    vm_args, graalpython_args = mx.extract_VM_args(args, useDoubleDash=True, defaultAllVMArgs=False)
    internal_graalpython_args, graalpython_args, additional_dists = _extract_graalpython_internal_options(graalpython_args)
    dists += additional_dists
    if '--python.WithJavaStacktrace' not in graalpython_args:
        graalpython_args.insert(0, '--python.WithJavaStacktrace')

    if _sulong:
        dists.append('SULONG')
        if mx.suite("sulong-managed", fatalIfMissing=False):
            dists.append('SULONG_MANAGED')
            vm_args.append(mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:SULONG_LIBS>:<path:SULONG_MANAGED_LIBS>'))
        else:
            vm_args.append(mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:SULONG_LIBS>'))

    # Try eagerly to include tools on Tim's computer
    if not mx.suite("/tools", fatalIfMissing=False):
        def _is_user(user, home=None):
            if home:
                return os.environ.get("USER") == user and os.environ.get(home)
            return os.environ.get("USER") == user

        if _is_user("tim", "MAGLEV_HOME") or _is_user("cbasca") or _is_user("fa"):
            suite_import = mx.SuiteImport("tools", version=None, urlinfos=None, dynamicImport=True, in_subdir=True)
            imported_suite, _ = mx._find_suite_import(_suite, suite_import, fatalIfMissing=False, load=False)
            if imported_suite:
                imported_suite._preload_suite_dict()
                try:
                    mx._register_suite(imported_suite)
                    imported_suite._load()
                    imported_suite._init_metadata()
                    imported_suite._resolve_dependencies()
                    imported_suite._post_init()
                except AssertionError:
                    pass # already registered
            dists.append('CHROMEINSPECTOR')
            if _sulong:
                vm_args.append("-Dpolyglot.llvm.enableLVI=true")

    vm_args += mx.get_runtime_jvm_args(dists, jdk=jdk)

    vm_args += internal_graalpython_args

    if not jdk:
        jdk = get_jdk()

    # default: assertion checking is enabled
    if extra_vm_args is None or '-da' not in extra_vm_args:
        vm_args += ['-ea', '-esa']

    if extra_vm_args:
        vm_args += extra_vm_args

    vm_args.append("com.oracle.graal.python.shell.GraalPythonMain")
    return mx.run_java(vm_args + graalpython_args, jdk=jdk, **kwargs)


def punittest(args):
    if '--regex' in args:
        mx.abort('--regex is not supported for punittest')
    # IMPORTANT! This must not be --suite graalpython, because a
    # --dynamicimports sulong will otherwise not put sulong.jar on the
    # classpath, which means we cannot run our C extension tests!
    unittest(args + ['--regex', '(graal\.python)|(com\.oracle\.truffle\.tck\.tests)', "-Dgraal.TraceTruffleCompilation=true"])


def nativebuild(args):
    mx.build(["--only", "com.oracle.graal.python.cext,GRAALPYTHON,GRAALPYTHON_GRAALVM_SUPPORT"])


def nativeclean(args):
    mx.run(['find', _suite.dir, '-name', '*.bc', '-delete'])


def python3_unittests(args):
    mx.run(["python3", "graalpython/com.oracle.graal.python.test/src/python_unittests.py", "-v"] + args)


# mx gate --tags pythonbenchmarktest
# mx gate --tags pythontest
# mx gate --tags fulltest


class GraalPythonTags(object):
    junit = 'python-junit'
    unittest = 'python-unittest'
    cpyext = 'python-cpyext'
    cpyext_managed = 'python-cpyext-managed'
    cpyext_sandboxed = 'python-cpyext-sandboxed'
    svmunit = 'python-svm-unittest'
    downstream = 'python-downstream'
    graalvm = 'python-graalvm'
    apptests = 'python-apptests'
    license = 'python-license'


def python_gate(args):
    if not os.environ.get("JDT"):
        find_jdt()
    if not os.environ.get("ECLIPSE_EXE"):
        find_eclipse()
    if "--tags" not in args:
        args += ["--tags", "fullbuild,style,python-junit,python-unittest,python-license,python-downstream"]
    return mx.command_function("gate")(args)


def find_jdt():
    pardir = os.path.abspath(os.path.join(_suite.dir, ".."))
    for f in [os.path.join(_suite.dir, f) for f in os.listdir(_suite.dir)] + [os.path.join(pardir, f) for f in os.listdir(pardir)]:
        if os.path.basename(f).startswith("ecj-") and os.path.basename(f).endswith(".jar"):
            mx.log("Automatically choosing %s for JDT" % f)
            os.environ["JDT"] = f
            return


def find_eclipse():
    pardir = os.path.abspath(os.path.join(_suite.dir, ".."))
    for f in [os.path.join(_suite.dir, f) for f in os.listdir(_suite.dir)] + [os.path.join(pardir, f) for f in os.listdir(pardir)]:
        if os.path.basename(f) == "eclipse" and os.path.isdir(f):
            mx.log("Automatically choosing %s for Eclipse" % f)
            eclipse_exe = os.path.join(f, "eclipse")
            if os.path.exists(eclipse_exe):
                os.environ["ECLIPSE_EXE"] = eclipse_exe
                return


def python_build_svm(args):
    mx.run_mx(
        ["--dynamicimports", "/substratevm,/vm", "build",
         "--force-deprecation-as-warning", "--dependencies",
         "GRAAL_MANAGEMENT,graalpython.image"],
        nonZeroIsFatal=True
    )
    shutil.copy(__get_svm_binary_from_graalvm(), _get_svm_binary())


def python_svm(args):
    python_build_svm(args)
    svm_image = __get_svm_binary_from_graalvm()
    mx.run([svm_image] + args)
    return svm_image


def gate_unittests(args=[], subdir=""):
    _graalpytest_driver = "graalpython/com.oracle.graal.python.test/src/graalpytest.py"
    _test_project = "graalpython/com.oracle.graal.python.test/"
    for idx, arg in enumerate(args):
        if arg.startswith("--subdir="):
            subdir = args.pop(idx).split("=")[1]
            break
    test_args = [_graalpytest_driver, "-v", _test_project + "src/tests/" + subdir]
    if "--" in args:
        idx = args.index("--")
        pre_args = args[:idx]
        post_args = args[idx + 1:]
    else:
        pre_args = []
        post_args = args
    mx.command_function("python")(["--python.CatchAllExceptions=true"] + pre_args + test_args + post_args)
    if platform.system() != 'Darwin':
        # TODO: re-enable when python3 is available on darwin
        mx.log("Running tests with CPython")
        mx.run(["python3"] + test_args, nonZeroIsFatal=True)


def graalpython_gate_runner(args, tasks):
    with Task('GraalPython JUnit', tasks, tags=[GraalPythonTags.junit]) as task:
        if task:
            punittest(['--verbose'])

    with Task('GraalPython Python tests', tasks, tags=[GraalPythonTags.unittest]) as task:
        if task:
            gate_unittests()

    with Task('GraalPython C extension tests', tasks, tags=[GraalPythonTags.cpyext]) as task:
        if task:
            gate_unittests(subdir="cpyext/")

    with Task('GraalPython C extension managed tests', tasks, tags=[GraalPythonTags.cpyext_managed]) as task:
        if task:
            mx.run_mx(["--dynamicimports", "sulong-managed", "python-gate-unittests", "--llvm.configuration=managed", "--subdir=cpyext", "--"])

    with Task('GraalPython C extension sandboxed tests', tasks, tags=[GraalPythonTags.cpyext_sandboxed]) as task:
        if task:
            mx.run_mx(["--dynamicimports", "sulong-managed", "python-gate-unittests", "--llvm.configuration=sandboxed", "--subdir=cpyext", "--"])

    with Task('GraalPython Python tests on SVM', tasks, tags=[GraalPythonTags.svmunit]) as task:
        if task:
            if not os.path.exists("./graalpython-svm"):
                python_svm(["-h"])
            if os.path.exists("./graalpython-svm"):
                langhome = mx_subst.path_substitutions.substitute('--native.Dllvm.home=<path:SULONG_LIBS>')

                # tests root directory
                tests_folder = "graalpython/com.oracle.graal.python.test/src/tests/"

                # list of excluded tests
                excluded = ["test_interop.py"]

                def is_included(path):
                    if path.endswith(".py"):
                        basename = path.rpartition("/")[2]
                        return basename.startswith("test_") and basename not in excluded
                    return False

                # list all 1st-level tests and exclude the SVM-incompatible ones
                testfiles = []
                paths = [tests_folder]
                while paths:
                    path = paths.pop()
                    if is_included(path):
                        testfiles.append(path)
                    else:
                        try:
                            paths += [(path + f if path.endswith("/") else "%s/%s" % (path, f)) for f in
                                      os.listdir(path)]
                        except OSError:
                            pass

                test_args = ["graalpython/com.oracle.graal.python.test/src/graalpytest.py", "-v"] + testfiles
                mx.run(["./graalpython-svm", "--python.CoreHome=graalpython/lib-graalpython", "--python.StdLibHome=graalpython/lib-python/3", langhome] + test_args, nonZeroIsFatal=True)

    with Task('GraalPython apptests', tasks, tags=[GraalPythonTags.apptests]) as task:
        if task:
            apprepo = os.environ["GRAALPYTHON_APPTESTS_REPO_URL"]
            _apptest_suite = _suite.import_suite(
                "graalpython-apptests",
                version="f40fcf3af008d30a67e0dbc325a0d90f1e68f0c0",
                urlinfos=[mx.SuiteImportURLInfo(mx_urlrewrites.rewriteurl(apprepo), "git", mx.vc_system("git"))]
            )
            mx.run_mx(["-p", _apptest_suite.dir, "graalpython-apptests"])

    with Task('GraalPython license header update', tasks, tags=[GraalPythonTags.license]) as task:
        if task:
            python_checkcopyrights([])

    with Task('GraalPython GraalVM shared-library build', tasks, tags=[GraalPythonTags.downstream, GraalPythonTags.graalvm]) as task:
        if task:
            run_shared_lib_test()

    with Task('GraalPython GraalVM build', tasks, tags=[GraalPythonTags.downstream, GraalPythonTags.graalvm]) as task:
        if task:
            svm_image = python_svm(["--version"])
            benchmark = os.path.join(PATH_MESO, "image-magix.py")
            out = mx.OutputCapture()
            mx.run(
                [svm_image, benchmark],
                nonZeroIsFatal=True,
                out=mx.TeeOutputCapture(out)
            )
            success = "\n".join([
                "[0, 0, 0, 0, 0, 0, 10, 10, 10, 0, 0, 10, 3, 10, 0, 0, 10, 10, 10, 0, 0, 0, 0, 0, 0]",
            ])
            if success not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not match success pattern:\n' + success)


mx_gate.add_gate_runner(_suite, graalpython_gate_runner)


def run_shared_lib_test(args=None):
    mx.run_mx(
        ["--dynamicimports", "/substratevm,/vm",
         "build", "--force-deprecation-as-warning", "--dependencies",
         "GRAAL_MANAGEMENT,POLYGLOT_NATIVE_API_HEADERS,libpolyglot.so.image"],
        nonZeroIsFatal=True
    )
    vmdir = os.path.join(mx.suite("truffle").dir, "..", "vm")
    svm_lib_path = os.path.join(vmdir, "mxbuild", "-".join([mx.get_os(), mx.get_arch()]), "libpolyglot.so.image")
    fd = name = progname = None
    try:
        fd, name = tempfile.mkstemp(suffix='.c')
        os.write(fd, """
        #include "stdio.h"
        #include "polyglot_api.h"

        #define assert_ok(msg, f) { if (!(f)) { \\
             const poly_extended_error_info* error_info; \\
             poly_get_last_error_info(isolate_thread, &error_info); \\
             fprintf(stderr, "%s\\n", error_info->error_message); \\
             return fprintf(stderr, "%s\\n", msg); } } while (0)

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
            status = poly_context_builder_allow_io(isolate_thread, builder, true);
            if (status != poly_ok) {
            return status;
            }
            status = poly_context_builder_build(isolate_thread, builder, &context);
            if (status != poly_ok) {
                return status;
            }

            poly_destroy_handle(isolate_thread, engine_builder);
            poly_destroy_handle(isolate_thread, builder);

            return poly_ok;
        }

        static poly_status tear_down_context() {
            poly_status status = poly_context_close(isolate_thread, context, true);
            if (status != poly_ok) {
                return status;
            }

            status = poly_destroy_handle(isolate_thread, context);
            if (status != poly_ok) {
                return status;
            }

            status = poly_engine_close(isolate_thread, engine, true);
            if (status != poly_ok) {
                return status;
            }

            status = poly_destroy_handle(isolate_thread, engine);
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

            assert_ok("primitive free failed", poly_destroy_handle(isolate_thread, primitive_object) == poly_ok);
            assert_ok("value free failed", poly_destroy_handle(isolate_thread, value) == poly_ok);
            assert_ok("value computation was incorrect", result_value == 42 * 42);
            assert_ok("func free failed", poly_destroy_handle(isolate_thread, func) == poly_ok);
            assert_ok("Context tear down failed.", tear_down_context() == poly_ok);
            return 0;
        }

        int32_t main(int32_t argc, char **argv) {
            poly_isolate_params isolate_params = {};
            if (poly_create_isolate(&isolate_params, &global_isolate)) {
                return 1;
            }
            return test_basic_python_function();
        }
        """)
        os.close(fd)
        progname = os.path.join(_suite.dir, "graalpython-embedded-tool")
        mx.log("".join(["Running ", "'clang", "-I%s" % svm_lib_path, "-L%s" % svm_lib_path, name, "-o", progname, "-lpolyglot"]))
        mx.run(["clang", "-I%s" % svm_lib_path, "-L%s" % svm_lib_path, name, "-o%s" % progname, "-lpolyglot"], nonZeroIsFatal=True)
        mx.log("Running " + progname + " with LD_LIBRARY_PATH " + svm_lib_path)
        mx.run(["ls", "-l", progname])
        mx.run(["ls", "-l", svm_lib_path])
        run_env = {"LD_LIBRARY_PATH": svm_lib_path, "GRAAL_PYTHONHOME": os.environ["GRAAL_PYTHONHOME"]}
        print(run_env)
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
    active_branch = mx.VC.get_vc(_suite.dir).active_branch(_suite.dir)
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
    if str(_suite.dir).endswith("testdownstream/graalpython"):
        shutil.rmtree(_suite.dir, ignore_errors=True)


def update_import(name, rev="origin/master", callback=None):
    primary = mx.primary_suite()
    dep_dir = mx.suite(name).vc_dir
    vc = mx.VC.get_vc(dep_dir)
    vc.pull(dep_dir, update=False)
    vc.update(dep_dir, rev=rev)
    tip = str(vc.tip(dep_dir)).strip()
    contents = None
    suitefile = os.path.join(primary.dir, "mx." + primary.name, "suite.py")
    with open(suitefile, 'r') as f:
        contents = f.read()
    dep_re = re.compile("['\"]name['\"]:\s+['\"]%s['\"],\s+['\"]version['\"]:\s+['\"]([a-z0-9]+)['\"]" % name, re.MULTILINE)
    dep_match = dep_re.search(contents)
    if dep_match:
        start = dep_match.start(1)
        end = dep_match.end(1)
        assert end - start == len(tip)
        mx.update_file(suitefile, "".join([contents[:start], tip, contents[end:]]), showDiff=True)
        if callback:
            callback()
    else:
        mx.abort("%s not found in %s" % (name, suitefile))


def update_import_cmd(args):
    for name in args:
        callback = None
        if name == "sulong":
            join = os.path.join
            callback=lambda: shutil.copy(
                join(_sulong.dir, "include", "truffle.h"),
                join(_suite.dir, "graalpython", "com.oracle.graal.python.cext", "include", "truffle.h")
            ) and shutil.copy(
                join(mx.dependency("SULONG_LIBS").output, "polyglot.h"),
                join(_suite.dir, "graalpython", "com.oracle.graal.python.cext", "include", "polyglot.h")
            )
        # make sure that sulong and regex are the same version
        if name == "regex":
            update_import("sulong", callback=callback)
        elif name == "sulong":
            update_import("regex", callback=callback)
        update_import(name, callback=callback)


def python_checkcopyrights(args):
    # we wan't to ignore lib-python/3, because that's just crazy
    listfilename = tempfile.mktemp()
    with open(listfilename, "w") as listfile:
        mx.run(["git", "ls-tree", "-r", "HEAD", "--name-only"], out=listfile)
    with open(listfilename, "r") as listfile:
        content = listfile.read()
    with open(listfilename, "w") as listfile:
        for line in content.split("\n"):
            if "lib-python/3" in line:
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


def import_python_sources(args):
    # mappings for files that are renamed
    mapping = {
        "_memoryview.c": "memoryobject.c",
        "_cpython_sre.c": "_sre.c",
    }
    parser = ArgumentParser(prog='mx python-src-import')
    parser.add_argument('--cpython', action='store', help='Path to CPython sources', required=True)
    parser.add_argument('--pypy', action='store', help='Path to PyPy sources', required=True)
    parser.add_argument('--msg', action='store', help='Message for import update commit', required=True)
    args = parser.parse_args(args)

    python_sources = args.cpython
    pypy_sources = args.pypy
    import_version = args.msg

    print """
    So you think you want to update the inlined sources? Here is how it will go:

    1. We'll first check the copyrights check overrides file to identify the
       files taken from CPython and we'll remember that list. There's a mapping
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
       a common ancestroy, git will try to preserve our patches to files, that
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
    """.format(mapping)
    raw_input("Got it?")

    files = []
    with open(os.path.join(os.path.dirname(__file__), "copyrights", "overrides")) as f:
        files = [line.split(",")[0] for line in f.read().split("\n") if len(line.split(",")) > 1 and line.split(",")[1] == "python.copyright"]

    # move to orphaned branch with sources
    if _suite.vc.isDirty(_suite.dir):
        mx.abort("Working dir must be clean")
    tip = _suite.vc.tip(_suite.dir).strip()
    _suite.vc.git_command(_suite.dir, ["checkout", "python-import"])
    _suite.vc.git_command(_suite.dir, ["clean", "-fdx"])
    shutil.rmtree("graalpython")

    for inlined_file in files:
        # C files are mostly just copied
        original_file = None
        name = os.path.basename(inlined_file)
        name = mapping.get(name, name)
        if inlined_file.endswith(".h") or inlined_file.endswith(".c"):
            for root, dirs, files in os.walk(python_sources):
                if os.path.basename(name) in files:
                    original_file = os.path.join(root, name)
                    try:
                        os.makedirs(os.path.dirname(inlined_file))
                    except:
                        pass
                    shutil.copy(original_file, inlined_file)
                    break
        elif inlined_file.endswith(".py"):
            # these files don't need to be updated, they inline some unittest code only
            if name.startswith("test_") or name.endswith("_tests.py"):
                original_file = inlined_file
        if original_file is None:
            mx.warn("Could not update %s - original file not found" % inlined_file)

    # re-copy lib-python
    libdir = os.path.join(_suite.dir, "graalpython/lib-python/3")
    shutil.copytree(os.path.join(pypy_sources, "lib-python", "3"), libdir)

    # commit and check back
    _suite.vc.git_command(_suite.dir, ["add", "."])
    raw_input("Check that the updated files look as intended, then press RETURN...")
    _suite.vc.commit(_suite.dir, "Update Python inlined files: %s" % import_version)
    answer = raw_input("Should we push python-import (y/N)? ")
    if answer and answer in "Yy":
        _suite.vc.git_command(_suite.dir, ["push", "origin", "python-import:python-import"])
    _suite.vc.update(_suite.dir, rev=tip)
    _suite.vc.git_command(_suite.dir, ["merge", "python-import"])


# ----------------------------------------------------------------------------------------------------------------------
#
# add the defined python benchmark suites
#
# ----------------------------------------------------------------------------------------------------------------------
for py_bench_suite in PythonBenchmarkSuite.get_benchmark_suites():
    mx_benchmark.add_bm_suite(py_bench_suite)


# ----------------------------------------------------------------------------------------------------------------------
#
# register as a GraalVM language
#
# ----------------------------------------------------------------------------------------------------------------------
mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=_suite,
    name='Graal.Python',
    short_name='pyn',
    dir_name='python',
    license_files=['LICENSE_GRAALPYTHON'],
    third_party_license_files=['3rd_party_licenses_graalpython.txt'],
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
            build_args=[
                '--language:python',
                '--language:llvm',
            ]
        )
    ],
))


# ----------------------------------------------------------------------------------------------------------------------
#
# set our GRAAL_PYTHONHOME if not already set
#
# ----------------------------------------------------------------------------------------------------------------------
if not os.getenv("GRAAL_PYTHONHOME"):
    home = os.path.join(_suite.dir, "graalpython")
    if not os.path.exists(home):
        home = [d for d in _suite.dists if d.name == "GRAALPYTHON_GRAALVM_SUPPORT"][0].output
    os.environ["GRAAL_PYTHONHOME"] = home


# ----------------------------------------------------------------------------------------------------------------------
#
# register the suite commands (if any)
#
# ----------------------------------------------------------------------------------------------------------------------
mx.update_commands(_suite, {
    'python': [python, '[Python args|@VM options]'],
    'python3': [python, '[Python args|@VM options]'],
    'deploy-binary-if-master': [deploy_binary_if_master, ''],
    'python-gate': [python_gate, ''],
    'python-update-import': [update_import_cmd, 'import name'],
    'delete-graalpython-if-testdownstream': [delete_self_if_testdownstream, ''],
    'python-checkcopyrights': [python_checkcopyrights, 'Make sure code files have copyright notices'],
    'python-build-svm': [python_build_svm, 'build svm image if it is outdated'],
    'python-svm': [python_svm, 'run python svm image (building it if it is outdated'],
    'punittest': [punittest, ''],
    'python3-unittests': [python3_unittests, 'run the cPython stdlib unittests'],
    'python-unittests': [python3_unittests, 'run the cPython stdlib unittests'],
    'python-gate-unittests': [gate_unittests, ''],
    'nativebuild': [nativebuild, ''],
    'nativeclean': [nativeclean, ''],
    'python-so-test': [run_shared_lib_test, ''],
    'python-src-import': [import_python_sources, ''],
})
