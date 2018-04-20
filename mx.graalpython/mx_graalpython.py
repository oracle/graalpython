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
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile

import mx
import mx_sdk
import mx_benchmark
import mx_gate
import mx_subst
from mx_downstream import testdownstream
from mx_gate import Task
from mx_graalpython_benchmark import PythonBenchmarkSuite
from mx_unittest import unittest
from mx_urlrewrites import _urlrewrites

_suite = mx.suite('graalpython')
_mx_graal = mx.suite("compiler", fatalIfMissing=False)
_sulong = mx.suite("sulong")


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
        graalpython_args = ['--python.WithJavaStacktrace'] + graalpython_args

    if _sulong:
        vm_args.append(mx_subst.path_substitutions.substitute('-Dpolyglot.llvm.libraryPath=<path:SULONG_LIBS>'))
        dists.append('SULONG')

    if not any("-Dpython.home" in arg for arg in vm_args):
        if not any("--python.SysPrefix" in arg for arg in graalpython_args):
            graalpython_args.append("--python.SysPrefix=%s" % os.path.join(_suite.dir, "graalpython", "com.oracle.graal.python.cext"))
        if not any("--python.CoreHome" in arg for arg in graalpython_args):
            graalpython_args.append("--python.CoreHome=%s" % os.path.join(_suite.dir, "graalpython", "lib-graalpython"))
        if not any("--python.StdLibHome" in arg for arg in graalpython_args):
            graalpython_args.append("--python.StdLibHome=%s" % os.path.join(_suite.dir, "graalpython", "lib-python", "3"))

    # Try eagerly to include tools on Tim's computer
    if not mx.suite("/tools", fatalIfMissing=False):
        def _is_user(user, home=None):
            if home:
                return os.environ.get("USER") == user and os.environ.get(home)
            return os.environ.get("USER") == user

        if _is_user("tim", "MAGLEV_HOME") or _is_user("cbasca"):
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
    unittest(args + ['--regex', '(graal\.python)|(com\.oracle\.truffle\.tck\.tests)'])


def nativebuild(args):
    mx.build(["--only", "com.oracle.graal.python.cext"])

# mx gate --tags pythonbenchmarktest
# mx gate --tags pythontest
# mx gate --tags fulltest


class GraalPythonTags(object):
    junit = 'python-junit'
    unittest = 'python-unittest'
    benchmarks = 'python-benchmarks'
    downstream = 'python-downstream'
    svmbinary = 'python-svm-binary'
    svmsource = 'python-svm-source'
    R = 'python-R'
    license = 'python-license'


python_test_benchmarks = {
    'binarytrees3': '12',
    'fannkuchredux3': '9',
    'fasta3': '250000',
    'mandelbrot3': '600',
    'meteor3': '2098',
    'nbody3': '100000',
    'spectralnorm3': '500',
    'richards3': '3',
    'bm-ai': '0',
    'pidigits': '0',
    'pypy-go': '1',
}


def _gate_python_benchmarks_tests(name, iterations):
    run_java = mx.run_java
    vmargs += ['-cp', mx.classpath(["com.oracle.graal.python"]), "com.oracle.graal.python.shell.GraalPythonMain", name, str(iterations)]
    success_pattern = re.compile(r"^(?P<benchmark>[a-zA-Z0-9.\-]+): (?P<score>[0-9]+(\.[0-9]+)?$)")
    out = mx.OutputCapture()
    run_java(vmargs, out=mx.TeeOutputCapture(out), err=subprocess.STDOUT)
    if not re.search(success_pattern, out.data, re.MULTILINE):
        mx.abort('Benchmark "' + name + '" doesn\'t match success pattern: ' + str(success_pattern))


def python_gate(args):
    if not os.environ.get("JDT"):
        find_jdt()
    if not os.environ.get("ECLIPSE_EXE"):
        find_eclipse()
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


def graalpython_gate_runner(args, tasks):
    with Task('GraalPython JUnit', tasks, tags=[GraalPythonTags.junit]) as task:
        if task:
            punittest(['--verbose'])

    with Task('GraalPython Python tests', tasks, tags=[GraalPythonTags.unittest]) as task:
        if task:
            test_args = ["graalpython/com.oracle.graal.python.test/src/graalpytest.py", "-v",
                         "graalpython/com.oracle.graal.python.test/src/tests/"]
            mx.command_function("python")(test_args)
            if platform.system() != 'Darwin':
                # TODO: re-enable when python3 is available on darwin
                mx.run(["python3"] + test_args, nonZeroIsFatal=True)

    with Task('GraalPython downstream R tests', tasks, tags=[GraalPythonTags.downstream, GraalPythonTags.R]) as task:
        script_r2p = os.path.join(_suite.dir, "graalpython", "benchmarks", "src", "benchmarks", "interop", "r_python_image_demo.r")
        script_p2r = os.path.join(_suite.dir, "graalpython", "benchmarks", "src", "benchmarks", "interop", "python_r_image_demo.py")
        pythonjars = os.pathsep.join([
            os.path.join(_suite.dir, "mxbuild", "dists", "graalpython.jar"),
            os.path.join(_suite.dir, "mxbuild", "dists", "graalpython-env.jar")
        ])
        if task:
            rrepo = os.environ["FASTR_REPO_URL"]
            testdownstream(
                _suite,
                [rrepo, mx.suite("truffle").vc._remote_url(mx.suite("truffle").dir, "origin")],
                ".",
                [["--dynamicimports", "graalpython", "--version-conflict-resolution", "latest_all", "build", "--force-deprecation-as-warning"],
                 ["--cp-sfx", pythonjars, "r", "--polyglot", "--file=%s" % script_r2p]
                 ])
            testdownstream(
                _suite,
                [rrepo, mx.suite("truffle").vc._remote_url(mx.suite("truffle").dir, "origin")],
                ".",
                [["--dynamicimports", "graalpython", "--version-conflict-resolution", "latest_all", "build", "--force-deprecation-as-warning"],
                 ["-v", "--cp-sfx", pythonjars, "r", "--jvm", "--polyglot", "-e", "eval.polyglot('python', path='%s')" % str(script_p2r)]
                 ])

    with Task('GraalPython license header update', tasks, tags=[GraalPythonTags.license]) as task:
        if task:
            python_checkcopyrights([])

    with Task('GraalPython downstream svm binary tests', tasks, tags=[GraalPythonTags.downstream, GraalPythonTags.svmbinary]) as task:
        if task:
            _run_downstream_svm(
                [["--dynamicimports", "graalpython", "delete-graalpython-if-testdownstream"],
                 ["gate", '-B--force-deprecation-as-warning', "--tags", "build,python"]],
                binary=True
            )

    with Task('GraalPython downstream svm source tests', tasks, tags=[GraalPythonTags.downstream, GraalPythonTags.svmsource]) as task:
        if task:
            _run_downstream_svm([[
                "--dynamicimports", "graalpython", "--strict-compliance", "gate", '-B--force-deprecation-as-warning', "--strict-mode", "--tags", "build,python"
            ]])

    for name, iterations in sorted(python_test_benchmarks.iteritems()):
        with Task('PythonBenchmarksTest:' + name, tasks, tags=[GraalPythonTags.benchmarks]) as task:
            if task:
                _gate_python_benchmarks_tests("graalpython/benchmarks/src/benchmarks/" + name + ".py", iterations)


mx_gate.add_gate_runner(_suite, graalpython_gate_runner)


def _run_downstream_svm(commands, binary=False):
    new_rewrites = None
    if binary:
        localmvn = "/tmp/graalpythonsnapshots"
        localmvnrepl = "file://%s" % localmvn
        publicmvn = mx.repository("python-public-snapshots").url
        publicmvnpattern = re.compile(publicmvn)
        git = mx.GitConfig()

        new_rewrites = [{publicmvnpattern.pattern: {"replacement": localmvnrepl}}]
        for rewrite in _urlrewrites:
            if rewrite.pattern.match(publicmvn):
                # we replace rewrites of our public repo
                pass
            elif publicmvnpattern.match(rewrite.replacement):
                # we rewrite to what we want
                new_rewrites.append({rewrite.pattern.pattern: {"replacement": localmvnrepl}})
            else:
                new_rewrites.append({rewrite.pattern.pattern: {"replacement": rewrite.replacement}})
        os.environ["TRUFFLE_PYTHON_VERSION"] = git.tip(_suite.dir).strip()
        os.environ["TRUFFLE_SULONG_VERSION"] = git.tip(_sulong.dir).strip()
        prev_urlrewrites = os.environ.get("MX_URLREWRITES")
        os.environ["MX_URLREWRITES"] = json.dumps(new_rewrites)

        mx.command_function("deploy-binary")(["--all-suites", "python-local-snapshots", localmvnrepl])

    try:
        mx.log(str(dict(os.environ)))
        testdownstream(
            _suite,
            [mx.suite("truffle").vc._remote_url(mx.suite("truffle").dir, "origin")],
            "substratevm",
            commands)
    finally:
        if binary:
            os.environ.pop("TRUFFLE_PYTHON_VERSION")
            os.environ.pop("TRUFFLE_SULONG_VERSION")
            if prev_urlrewrites:
                os.environ["MX_URLREWRITES"] = prev_urlrewrites
            else:
                os.environ.pop("MX_URLREWRITES")
            shutil.rmtree(localmvn, ignore_errors=True)


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
            )
        # make sure that truffle and regex are the same version
        elif name == "regex":
            update_import("truffle", callback=callback)
        elif name == "truffle":
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
        mx.command_function("checkcopyrights")(["--primary", "--", "--file-list", listfilename] + args)
    finally:
        os.unlink(listfilename)


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
    name='Graal.Python',
    short_name='pyn',
    dir_name='python',
    documentation_files=['link:<support>/README_GRAALPYTHON.md'],
    license_files=['link:<support>/GraalCE_Python_license_3rd_party_license.txt'],
    third_party_license_files=[],
    truffle_jars=[
        'dependency:graalpython:GRAALPYTHON',
        'dependency:graalpython:GRAALPYTHON-ENV',
    ],
    support_distributions=[
        'extracted-dependency:graalpython:GRAALPYTHON_GRAALVM_SUPPORT',
        'extracted-dependency:graalpython:GRAALPYTHON_GRAALVM_DOCS',
    ],
    launcher_configs=[
        mx_sdk.LanguageLauncherConfig(
            destination='bin/<exe:graalpython>',
            jar_distributions=['dependency:graalpython:GRAALPYTHON-LAUNCHER'],
            main_class='com.oracle.graal.python.shell.GraalPythonMain',
            build_args=[
                '--language:python',
                '--language:llvm',
            ]
        )
    ],
), _suite)


# ----------------------------------------------------------------------------------------------------------------------
#
# set our GRAAL_PYTHONHOME if not already set
#
# ----------------------------------------------------------------------------------------------------------------------
if not os.getenv("GRAAL_PYTHONHOME"):
    os.environ["GRAAL_PYTHONHOME"] = os.path.join(_suite.dir, "graalpython")


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
    'punittest': [punittest, ''],
    'nativebuild': [nativebuild, '']
})
