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

import contextlib
import datetime
import glob
import gzip
import itertools
import os
import pathlib
import re
import shlex
import shutil
import subprocess
import sys
import time
import psutil
from functools import wraps
from pathlib import Path
from textwrap import dedent

from typing import cast, Union, Literal, overload

import downstream_tests
import mx_graalpython_benchmark
import mx_urlrewrites

import tempfile
from argparse import ArgumentParser
from dataclasses import dataclass

import mx
import mx_util
import mx_gate
import mx_native
import mx_unittest
import mx_sdk
import mx_sdk_vm_ng
import mx_subst
import mx_truffle
import mx_graalpython_bisect
import mx_graalpython_import
import mx_graalpython_python_benchmarks

# re-export custom mx project classes so they can be used from suite.py
from mx import MavenProject #pylint: disable=unused-import
from mx_cmake import CMakeNinjaProject #pylint: disable=unused-import

from mx_gate import Task
from mx_graalpython_bench_param import PATH_MESO


# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_vm_ng import StandaloneLicenses, ThinLauncherProject, LanguageLibraryProject, DynamicPOMDistribution, DeliverableStandaloneArchive  # pylint: disable=unused-import

if not sys.modules.get("__main__"):
    # workaround for pdb++
    sys.modules["__main__"] = type(sys)("<empty>")


def get_boolean_env(name, default=False):
    env = os.environ.get(name)
    if env is None:
        return default
    return env.lower() in ('true', '1')


SUITE = cast(mx.SourceSuite, mx.suite('graalpython'))
SUITE_COMPILER = mx.suite("compiler", fatalIfMissing=False)

GRAAL_VERSION = SUITE.suiteDict['version']
IS_RELEASE = SUITE.suiteDict['release']
GRAAL_VERSION_MAJ_MIN = ".".join(GRAAL_VERSION.split(".")[:2])
PYTHON_VERSION = SUITE.suiteDict[f'{SUITE.name}:pythonVersion']
PYTHON_VERSION_MAJ_MIN = ".".join(PYTHON_VERSION.split('.')[:2])

LATEST_JAVA_HOME = {"JAVA_HOME": os.environ.get("LATEST_JAVA_HOME", mx.get_jdk().home)}
RUNNING_ON_LATEST_JAVA = os.environ.get("LATEST_JAVA_HOME", os.environ.get("JAVA_HOME")) == mx.get_jdk().home


# this environment variable is used by some of our maven projects and jbang integration to build against the unreleased master version during development
os.environ["GRAALPY_VERSION"] = GRAAL_VERSION

MAIN_BRANCH = 'master'

GRAALPYTHON_MAIN_CLASS = "com.oracle.graal.python.shell.GraalPythonMain"


SANDBOXED_OPTIONS = [
    '--experimental-options',
    '--python.PosixModuleBackend=java',
    '--python.Sha3ModuleBackend=java',
    '--python.CompressionModulesBackend=java'
]


# Allows disabling rebuild for some mx commands such as graalpytest
DISABLE_REBUILD = get_boolean_env('GRAALPYTHON_MX_DISABLE_REBUILD')

_COLLECTING_COVERAGE = False

CI = get_boolean_env("CI")
GITHUB_CI = get_boolean_env("GITHUB_CI")
WIN32 = sys.platform == "win32"
BUILD_NATIVE_IMAGE_WITH_ASSERTIONS = get_boolean_env('BUILD_WITH_ASSERTIONS', CI)
BYTECODE_DSL_INTERPRETER = get_boolean_env('BYTECODE_DSL_INTERPRETER', False)

mx_gate.add_jacoco_excludes([
    "com.oracle.graal.python.pegparser.sst",
    "com.oracle.graal.python.pegparser.test",
    "com.oracle.truffle.api.staticobject.test",
    "com.oracle.truffle.regex.tregex.test",
    "com.oracle.truffle.tck",
    "com.oracle.truffle.tools.chromeinspector.test",
    "com.oracle.truffle.tools.coverage.test",
    "com.oracle.truffle.tools.dap.test",
    "com.oracle.truffle.tools.profiler.test",
    "org.graalvm.tools.insight.test",
    "org.graalvm.tools.lsp.test",
])


def is_collecting_coverage():
    return bool(mx_gate.get_jacoco_agent_args() or _COLLECTING_COVERAGE)


def wants_debug_build(flags=os.environ.get("CFLAGS", "")):
    return any(x in flags for x in ["-g", "-ggdb", "-ggdb3"])


if wants_debug_build():
    setattr(mx_native.DefaultNativeProject, "_original_cflags", mx_native.DefaultNativeProject.cflags)
    setattr(mx_native.DefaultNativeProject, "cflags", property(
        lambda self: self._original_cflags + (["/Z7"] if WIN32 else ["-fPIC", "-ggdb3"])
    ))


if WIN32:
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
                if not os.environ.get("MSSdk"):
                    os.environ["MSSdk"] = os.environ.get("WindowsSdkDir", "unset")
                break
        else:
            mx.log("cl.exe not on PATH, not a VS shell")


def _get_stdlib_home():
    return os.path.join(SUITE.dir, "graalpython", "lib-python", "3")


def _get_capi_home():
    native_libs_output = mx.distribution("GRAALPYTHON_NATIVE_LIBS").get_output()
    assert native_libs_output
    return os.path.join(native_libs_output, mx.get_os(), mx.get_arch())


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



def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    if register_project and register_distribution:
        isolate_build_options = [
                '-H:+DetectUserDirectoriesInImageHeap',
        ]
        meta_pom = None
        for dist in SUITE.dists:
            if dist.name == 'PYTHON_POM':
                meta_pom = dist
        assert meta_pom, "Cannot find python meta-POM distribution in the graalpython suite"
        mx_truffle.register_polyglot_isolate_distributions(SUITE, register_project, register_distribution, 'python',
                                    'graalpython', meta_pom.name, meta_pom.maven_group_id(), meta_pom.theLicense,
                                    isolate_build_options)


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

        if vm_warning:
            mx.log('** warning ** : graal compiler was not found!! Executing using standard VM..')


def get_jdk():
    return mx.get_jdk()


# Called from suite.py
def graalpy_standalone_deps():
    include_truffle_runtime = not mx.env_var_to_bool("EXCLUDE_TRUFFLE_RUNTIME")
    deps = mx_truffle.resolve_truffle_dist_names(use_optimized_runtime=include_truffle_runtime)
    return deps


def _is_overridden_native_image_arg(prefix):
    extras = mx.get_opts().extra_image_builder_argument
    return any(arg.startswith(prefix) for arg in extras)


def github_ci_build_args():
    total_mem = psutil.virtual_memory().total / (1024 ** 3)
    min_bound = 8
    max_mem = 14*1024
    min_mem = int(1024 * (total_mem if total_mem < min_bound else total_mem * .9))
    os_cpu = os.cpu_count() or int(os.environ.get("NUMBER_OF_PROCESSORS", 1)) or 1
       
    build_mem = min(min_mem, max_mem)
    parallelism = os_cpu if os_cpu >= 4 and build_mem >= min_bound*1024 else 1
    
    return ["-Ob",
            # f"-J-Xms{build_mem}m",
            f"-J-Xms7g",
            f"--parallelism={parallelism}"
        ]

def libpythonvm_build_args():
    build_args = []
    build_args += bytecode_dsl_build_args()

    if os.environ.get("GITHUB_CI"):
        build_args += ["-Ob", "-J-XX:MaxRAMPercentage=90.0"]

    if graalos := ("musl" in mx_subst.path_substitutions.substitute("<multitarget_libc_selection>")):
        build_args += ['-H:+GraalOS']
    else:
        build_args += ["-Dpolyglot.image-build-time.PreinitializeContexts=python"]

    if (
            mx.is_linux()
            and not graalos
            and mx_sdk_vm_ng.is_nativeimage_ee()
            and not os.environ.get('NATIVE_IMAGE_AUXILIARY_ENGINE_CACHE')
            and not _is_overridden_native_image_arg("--gc")
    ):
        build_args += ['--gc=G1', '-H:-ProtectionKeys']

    profile = None
    if (
            "GRAALPY_PGO_PROFILE" not in os.environ
            and mx.suite('graalpython-enterprise', fatalIfMissing=False)
            and mx_sdk_vm_ng.get_bootstrap_graalvm_version() >= mx.VersionSpec("25.0")
            and not _is_overridden_native_image_arg("--pgo")
    ):
        vc = SUITE.vc
        commit = str(vc.tip(SUITE.dir)).strip()
        branch = str(vc.active_branch(SUITE.dir, abortOnError=False) or 'master').strip()
        dsl_suffix = '' if not BYTECODE_DSL_INTERPRETER else '-bytecode-dsl'

        if script := os.environ.get("ARTIFACT_DOWNLOAD_SCRIPT"):
            # This is always available in the GraalPy CI
            profile = f"cached_profile{dsl_suffix}.iprof.gz"
            run(
                [
                    sys.executable,
                    script,
                    f"graalpy/pgo{dsl_suffix}-{commit}",
                    profile,
                ],
                nonZeroIsFatal=False,
            )
        else:
            # Locally, we try to get a reasonable profile
            get_profile = mx.command_function('python-get-latest-profile', fatalIfMissing=False)
            if get_profile:
                for b in [branch, "master"]:
                    if not profile:
                        try:
                            profile = get_profile(["--branch", b])
                            if profile and dsl_suffix not in profile:
                                mx.warn("PGO profile seems mismatched, you need newer graal-enterprise")
                        except BaseException:
                            pass

        if CI and (not profile or not os.path.isfile(profile)):
            mx.log("No profile in CI job")
            # When running on a release branch or attempting to merge into
            # a release branch, make sure we can use a PGO profile, and
            # when running in the CI on a bench runner, ensure a PGO profile
            if (
                    any(b.startswith("release/") for b in [branch, os.environ.get("TO_BRANCH", "")])
                    or ("bench" in os.environ.get('BUILD_NAME', ''))
            ):
                mx.warn("PGO profile must exist for benchmarking and release, creating one now...")
                profile = graalpy_native_pgo_build_and_test()

    if os.path.isfile(profile or ""):
        print(invert(f"Automatically chose PGO profile {profile}. To disable this, set GRAALPY_PGO_PROFILE to an empty string'", blinking=True), file=sys.stderr)
        build_args += [
            f"--pgo={profile}",
            "-H:+UnlockExperimentalVMOptions",
            "-H:+PGOPrintProfileQuality",
            "-H:-UnlockExperimentalVMOptions",
        ]
    else:
        print(invert("Not using an automatically selected PGO profile"), file=sys.stderr)
    print(f"[DEBUG] libpythonvm args: {build_args}")
    return build_args


def graalpy_native_pgo_build_and_test(args=None):
    """
    Builds a PGO-instrumented GraalPy native standalone, runs the unittests to generate a profile,
    then builds a PGO-optimized GraalPy native standalone with the collected profile.
    The profile file will be named 'default.iprof' in native image build directory.
    """
    if mx_sdk_vm_ng.get_bootstrap_graalvm_version() < mx.VersionSpec("25.0"):
        mx.abort("python-native-pgo not supported on GraalVM < 25")

    with set_env(GRAALPY_PGO_PROFILE=""):
        mx.log(mx.colorize("[PGO] Building PGO-instrumented native image", color="yellow"))
        build_home = graalpy_standalone_home('native', enterprise=True, build=True)
        instrumented_home = build_home + "_PGO_INSTRUMENTED"
        shutil.rmtree(instrumented_home, ignore_errors=True)
        shutil.copytree(build_home, instrumented_home, symlinks=True, ignore_dangling_symlinks=True)
        instrumented_launcher = os.path.join(instrumented_home, 'bin', _graalpy_launcher())

    mx.log(mx.colorize(f"[PGO] Instrumented build complete: {instrumented_home}", color="yellow"))

    mx.log(mx.colorize(f"[PGO] Running graalpytest with instrumented binary: {instrumented_launcher}", color="yellow"))
    with tempfile.TemporaryDirectory() as d:
        with set_env(
                GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS="true",
                GRAAL_PYTHON_VM_ARGS="\v".join([
                    f"--vm.XX:ProfilesDumpFile={os.path.join(d, '$UUID$.iprof')}",
                    f"--vm.XX:ProfilesLCOVFile={os.path.join(d, '$UUID$.info')}",
                ]),
                GRAALPY_HOME=instrumented_home,
        ):
            graalpytest(["--python", instrumented_launcher, "test_venv.py"])
            python_vm_config = 'custom'
            if BYTECODE_DSL_INTERPRETER:
                python_vm_config += '-bc-dsl'
            mx.command_function('benchmark')(["meso-small:*", "--", "--python-vm", "graalpython", "--python-vm-config", python_vm_config])
        dsl_suffix = '' if not BYTECODE_DSL_INTERPRETER else '-bytecode-dsl'
        iprof_path = Path(SUITE.dir) / f'default{dsl_suffix}.iprof'
        lcov_path = Path(SUITE.dir) / f'default{dsl_suffix}.lcov'

        run([
            os.path.join(
                graalvm_jdk(enterprise=True),
                "bin",
                f"native-image-configure{'.exe' if mx.is_windows() else ''}",
            ),
            "merge-pgo-profiles",
            f"--input-dir={d}",
            f"--output-file={iprof_path}"
        ])
        run([
            "/usr/bin/env",
            "lcov",
            "-o", str(lcov_path),
            *itertools.chain.from_iterable([
                ["-a", f.absolute().as_posix()] for f in Path(d).glob("*.info")
            ])
        ], nonZeroIsFatal=False)
        run([
            "/usr/bin/env",
            "genhtml",
            "--source-directory", str(Path(SUITE.dir) / "com.oracle.graal.python" / "src"),
            "--source-directory", str(Path(SUITE.dir) / "com.oracle.graal.python.pegparser" / "src"),
            "--source-directory", str(Path(SUITE.get_output_root()) / "com.oracle.graal.python" / "src_gen"),
            "--include", "com/oracle/graal/python",
            "--keep-going",
            "-o", "lcov_html",
            str(lcov_path),
        ], nonZeroIsFatal=False)

    if not os.path.isfile(iprof_path):
        mx.abort(f"[PGO] Could not find profile file at expected location: {iprof_path}")

    with set_env(GRAALPY_PGO_PROFILE=str(iprof_path)):
        mx.log(mx.colorize("[PGO] Building optimized native image with collected profile", color="yellow"))
        native_bin = graalpy_standalone('native', enterprise=True, build=True)

    mx.log(mx.colorize(f"[PGO] Optimized PGO build complete: {native_bin}", color="yellow"))

    iprof_gz_path = str(iprof_path) + '.gz'
    with open(iprof_path, 'rb') as f_in, gzip.open(iprof_gz_path, 'wb') as f_out:
        shutil.copyfileobj(f_in, f_out)
    mx.log(mx.colorize(f"[PGO] Gzipped profile at: {iprof_gz_path}", color="yellow"))

    if script := os.environ.get("ARTIFACT_UPLOADER_SCRIPT"):
        commit = str(SUITE.vc.tip(SUITE.dir)).strip()
        run(
            [
                sys.executable,
                script,
                iprof_gz_path,
                f"pgo{dsl_suffix}-{commit}",
                "graalpy",
                "--lifecycle",
                "cache",
                "--artifact-repo-key",
                os.environ.get("ARTIFACT_REPO_KEY_LOCATION"),
                '--skip-existing',
            ],
        )

    if args is None:
        return iprof_gz_path


def full_python(args, env=None):
    """Run python from standalone build (unless kwargs are given). Does not build GraalPython sources automatically."""

    if not any(arg.startswith('--python.WithJavaStacktrace') for arg in args):
        args.insert(0, '--python.WithJavaStacktrace=1')

    if "--hosted" in args[:2]:
        return do_run_python(args)

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

    run([graalpy_path] + args, env=env)


def handle_debug_arg(args):
    if mx._opts.java_dbg_port:
        args.insert(0,
                    f"--vm.agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:{mx._opts.java_dbg_port}")


def do_run_python(args, extra_vm_args=None, env=None, jdk=None, extra_dists=None, cp_prefix=None, cp_suffix=None, main_class=GRAALPYTHON_MAIN_CLASS, minimal=False, **kwargs):

    if "--hosted" in args[:2]:
        args.remove("--hosted")
        if not any(arg.startswith('--python.WithJavaStacktrace') for arg in args):
            args.insert(0, '--python.WithJavaStacktrace=1')

    if not any(arg.startswith("--python.CAPI") for arg in args):
        capi_home = _get_capi_home()
        args.insert(0, "--python.CAPI=%s" % capi_home)
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
        dists = [dep for dep in x.deps if dep.isJavaProject() or dep.isJARDistribution() and dep.exists()]
        # Hack: what we should just do is + ['GRAALPYTHON_VERSIONS_MAIN'] and let MX figure out
        # the class-path and other VM arguments necessary for it. However, due to a bug in MX,
        # LayoutDirDistribution causes an exception if passed to mx.get_runtime_jvm_args,
        # because it does not properly initialize its super class ClasspathDependency, see MX PR: 1665.
        ver_dep = mx.distribution('GRAALPYTHON_VERSIONS_MAIN').get_output()
        cp_prefix = ver_dep if cp_prefix is None else (str(ver_dep) + os.pathsep + cp_prefix)
    else:
        dists = ['GRAALPYTHON']
    dists += ['GRAALPYTHON-LAUNCHER']

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
                if os.path.exists(mx.distribution(tool).path):
                    dists.append(tool)

    graalpython_args.insert(0, '--experimental-options=true')

    vm_args += mx.get_runtime_jvm_args(dists, jdk=jdk, cp_prefix=cp_prefix, cp_suffix=cp_suffix, force_cp=True)

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
    vm_args = mx.get_runtime_jvm_args(['GRAALPYTHON_UNIT_TESTS', 'GRAALPYTHON'])
    return mx.run_java(vm_args + [main_class] + args, **kwargs)


def _dev_pythonhome_context():
    home = os.environ.get("GRAAL_PYTHONHOME", _dev_pythonhome())
    return set_env(GRAAL_PYTHONHOME=home)


def _dev_pythonhome():
    return os.path.join(SUITE.dir, "graalpython")


def get_path_with_patchelf():
    path = os.environ.get("PATH", "")
    if mx.is_linux() and not shutil.which("patchelf"):
        venv = Path(SUITE.get_output_root()).absolute() / "patchelf-venv"
        path += os.pathsep + str(venv / "bin")
        if not shutil.which("patchelf", path=path):
            mx.log(f"{time.strftime('[%H:%M:%S] ')} Building patchelf-venv with {sys.executable}... [patchelf not found on PATH]")
            t0 = time.time()
            subprocess.check_call([sys.executable, "-m", "venv", str(venv)])
            subprocess.check_call([str(venv / "bin" / "pip"), "install", "patchelf"])
            mx.log(f"{time.strftime('[%H:%M:%S] ')} Building patchelf-venv with {sys.executable}... [duration: {time.time() - t0}]")
    if mx.is_windows() and not shutil.which("delvewheel"):
        venv = Path(SUITE.get_output_root()).absolute() / "delvewheel-venv"
        path += os.pathsep + str(venv / "Scripts")
        if not shutil.which("delvewheel", path=path):
            mx.log(f"{time.strftime('[%H:%M:%S] ')} Building delvewheel-venv with {sys.executable}... [delvewheel not found on PATH]")
            t0 = time.time()
            subprocess.check_call([sys.executable, "-m", "venv", str(venv)])
            subprocess.check_call([str(venv / "Scripts" / "pip.exe"), "install", "delvewheel"])
            mx.log(f"{time.strftime('[%H:%M:%S] ')} Building delvewheel-venv with {sys.executable}... [duration: {time.time() - t0}]")
    return path


def punittest(ars, report: Union[Task, bool, None] = False):
    """
    Runs GraalPython junit tests and memory leak tests, which can be skipped using --no-leak-tests.
    Pass --regex to further filter the junit and TSK tests. GraalPy tests are always run in two configurations:
    with language home on filesystem and with language home served from the Truffle resources.
    """
    path = get_path_with_patchelf()
    args = [] if ars is None else ars
    @dataclass
    class TestConfig:
        identifier: str
        args: list
        useResources: bool
        reportConfig: Union[Task, bool, None] = report
        def __str__(self):
            return f"args={self.args!r}, useResources={self.useResources}, report={self.reportConfig}"
        def __post_init__(self):
            assert ' ' not in self.identifier

    configs = []
    skip_leak_tests = False
    if "--no-leak-tests" in args:
        skip_leak_tests = True
        args.remove("--no-leak-tests")
    if is_collecting_coverage():
        skip_leak_tests = True

    vm_args = ['-Dpolyglot.engine.WarnInterpreterOnly=false']

    if BYTECODE_DSL_INTERPRETER:
        vm_args.append("-Dpython.EnableBytecodeDSLInterpreter=true")

    # Note: we must use filters instead of --regex so that mx correctly processes the unit test configs,
    # but it is OK to apply --regex on top of the filters
    graalpy_tests = ['com.oracle.graal.python.test', 'com.oracle.graal.python.pegparser.test', 'org.graalvm.python.embedding.test']
    configs += [
        TestConfig("junit", vm_args + graalpy_tests + args, True),
        TestConfig("junit", vm_args + graalpy_tests + args, False)]

    if not mx.is_windows():
        configs += [
            # Tests that must run in their own process due to C extensions usage, for now ignored on Windows
            TestConfig("multi-threaded-import-java", vm_args + ['com.oracle.graal.python.cext.test.MultithreadedImportTestNative'] + args, True),
            TestConfig("multi-threaded-import-java", vm_args + ['com.oracle.graal.python.cext.test.MultithreadedImportTestNative'] + args, False),
            TestConfig("multi-threaded-import-native", vm_args + ['com.oracle.graal.python.cext.test.MultithreadedImportTestJava'] + args, True),
            TestConfig("multi-threaded-import-native", vm_args + ['com.oracle.graal.python.cext.test.MultithreadedImportTestJava'] + args, False),
        ]

    if '--regex' not in args:
        async_regex = ['--regex', r'com\.oracle\.graal\.python\.test\.integration\.advanced\.AsyncActionThreadingTest']
        configs.append(TestConfig("async", vm_args + ['-Dpython.AutomaticAsyncActions=false', 'com.oracle.graal.python.test', 'org.graalvm.python.embedding.test'] + async_regex + args, True, False))
    else:
        skip_leak_tests = True

    for c in configs:
        mx.log(f"Python JUnit tests configuration: {c}")
        PythonMxUnittestConfig.useResources = c.useResources
        with set_env(PATH=path):
            mx_unittest.unittest(c.args, test_report_tags=({"task": f"punittest-{c.identifier}-{'w' if c.useResources else 'wo'}-resources"} if c.reportConfig else None))

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
                          "com.oracle.graal.python.cext"]


def nativebuild(_):
    "Build the non-Java Python projects and archives"
    mx.build(["--dependencies", ",".join(PYTHON_NATIVE_PROJECTS + PYTHON_ARCHIVES)])


def nativeclean(_):
    "Clean the non-Java Python projects"
    mx.clean(["--dependencies", ",".join(PYTHON_NATIVE_PROJECTS + PYTHON_ARCHIVES)])


class GraalPythonTags(object):
    junit = 'python-junit'
    junit_maven = 'python-junit-maven'
    junit_maven_isolates = 'python-junit-polyglot-isolates'
    jvmbuild = 'python-jvm-build'
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
    tagged = 'python-tagged-unittest'
    svmbuild = 'python-svm-build'
    svmunit = 'python-svm-unittest'
    svmunit_sandboxed = 'python-svm-unittest-sandboxed'
    graalvm = 'python-graalvm'
    embedding = 'python-standalone-embedding'
    graalvm_sandboxed = 'python-graalvm-sandboxed'
    svm = 'python-svm'
    native_image_embedder = 'python-native-image-embedder'
    license = 'python-license'
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
        for x in dir(GraalPythonTags):
            v = getattr(GraalPythonTags, x)
            if isinstance(v, str) and v.startswith("python-"):
                if "sandboxed" not in v:
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
            eclipse_exe = os.path.join(f, f"eclipse{'.exe' if mx.is_windows() else ''}")
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


def _graalpy_launcher():
    name = 'graalpy'
    return f"{name}.exe" if WIN32 else name


# dev only has effect if standalone_type is 'jvm' and means minimal, Default TruffleRuntime (no JIT)
def graalpy_standalone_home(standalone_type, enterprise=False, dev=False, build=True):
    assert standalone_type in ['native', 'jvm']
    jdk_version = mx.get_jdk().version

    # Check if GRAALPY_HOME points to some compatible pre-built GraalPy standalone
    python_home = os.environ.get("GRAALPY_HOME", None)
    if python_home:
        python_home = os.path.abspath(glob.glob(python_home)[0])
        mx.log("Using GraalPy standalone from GRAALPY_HOME: " + python_home)
        # Try to verify that we're getting what we expect:
        has_java = os.path.exists(os.path.join(python_home, 'jvm', 'bin', mx.exe_suffix('java')))
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

        launcher = os.path.join(python_home, 'bin', _graalpy_launcher())
        out = mx.OutputCapture()
        mx.run([launcher, "-c", "print(__graalpython__.is_bytecode_dsl_interpreter)"], nonZeroIsFatal=False, out=out, err=out)
        is_bytecode_dsl_interpreter = out.data.strip() == "True"
        if is_bytecode_dsl_interpreter != BYTECODE_DSL_INTERPRETER:
            requested = "Bytecode DSL" if BYTECODE_DSL_INTERPRETER else "Manual"
            actual = "Bytecode DSL" if is_bytecode_dsl_interpreter else "Manual"
            mx.abort(f"GRAALPY_HOME is not compatible with requested interpreter kind ({requested=}, {actual=})")

        return python_home

    # Build
    if standalone_type == 'jvm':
        if dev:
            env_file = 'jvm'
        else:
            env_file = 'jvm-ee-libgraal' if enterprise else 'jvm-ce-libgraal'
        standalone_dist = 'GRAALPY_JVM_STANDALONE'
        if "GraalVM" in subprocess.check_output([get_jdk().java, '-version'], stderr=subprocess.STDOUT, universal_newlines=True):
            env_file = ""
    else:
        env_file = 'native-ee' if enterprise else 'native-ce'
        standalone_dist = 'GRAALPY_NATIVE_STANDALONE'

    mx_args = ['-p', SUITE.dir, *(['--env', env_file] if env_file else [])]
    
    print(f"[DEBUG] GITHUB_CI env: {os.environ.get('GITHUB_CI')}")
    if GITHUB_CI:
        print("[DEBUG] Running in GitHub Ci")
        mx_args.append("--extra-image-builder-argument=-Ob")
    else:
        mx_args.append("--extra-image-builder-argument=-g")

    pgo_profile = os.environ.get("GRAALPY_PGO_PROFILE")
    if pgo_profile is not None:
        if not enterprise or standalone_type != "native":
            mx.abort("PGO is only supported on enterprise NI")
        if pgo_profile:
            mx_args.append(f"--extra-image-builder-argument=--pgo={pgo_profile}")
            mx_args.append(f"--extra-image-builder-argument=-H:+UnlockExperimentalVMOptions")
            mx_args.append(f"--extra-image-builder-argument=-H:+PGOPrintProfileQuality")
        else:
            mx_args.append(f"--extra-image-builder-argument=--pgo-instrument")
            mx_args.append(f"--extra-image-builder-argument=-H:+UnlockExperimentalVMOptions")
            mx_args.append(f"--extra-image-builder-argument=-H:+ProfilingLCOV")
    elif BUILD_NATIVE_IMAGE_WITH_ASSERTIONS:
        mx_args.append("--extra-image-builder-argument=-ea")

    if mx_gate.get_jacoco_agent_args() or (build and not DISABLE_REBUILD):
        mx_build_args = mx_args
        if BYTECODE_DSL_INTERPRETER:
            mx_build_args = mx_args + ["--extra-image-builder-argument=-Dpython.EnableBytecodeDSLInterpreter=true"]
        # This build is purposefully done without the LATEST_JAVA_HOME in the
        # environment, so we can build JVM standalones on an older Graal JDK
        run_mx(mx_build_args + ["build", "--target", standalone_dist])

    python_home = os.path.join(SUITE.dir, 'mxbuild', f"{mx.get_os()}-{mx.get_arch()}", standalone_dist)

    if standalone_type == 'native':
        debuginfo = os.path.join(SUITE.dir, 'mxbuild', f"{mx.get_os()}-{mx.get_arch()}", "libpythonvm", "libpythonvm.so.debug")
        if os.path.exists(debuginfo):
            shutil.copy(debuginfo, os.path.join(python_home, 'lib'))
    return python_home


def graalpy_standalone(standalone_type, enterprise=False, dev=False, build=True):
    assert standalone_type in ['native', 'jvm']
    if standalone_type == 'native' and mx_gate.get_jacoco_agent_args():
        return graalpy_standalone('jvm', enterprise=enterprise, dev=dev, build=build)

    home = graalpy_standalone_home(standalone_type, enterprise=enterprise, dev=dev, build=build)
    launcher = os.path.join(home, 'bin', _graalpy_launcher())
    return make_coverage_launcher_if_needed(launcher)

def graalpy_standalone_jvm():
    return graalpy_standalone('jvm')


def graalpy_standalone_native():
    return graalpy_standalone('native')


def graalpy_standalone_jvm_enterprise():
    return os.path.join(graalpy_standalone_home('jvm', enterprise=True), 'bin', _graalpy_launcher())


def graalpy_standalone_native_enterprise():
    return os.path.join(graalpy_standalone_home('native', enterprise=True), 'bin', _graalpy_launcher())


def graalvm_jdk(enterprise=False):
    jdk_version = mx.get_jdk().version

    # Check if GRAAL_JDK_HOME points to some compatible pre-built gvm
    graal_jdk_home = os.environ.get("GRAAL_JDK_HOME", None)
    if graal_jdk_home:
        graal_jdk_home = os.path.abspath(glob.glob(graal_jdk_home)[0])
        if sys.platform == "darwin":
            jdk_home_subdir = os.path.join(graal_jdk_home, 'Contents', 'Home')
            if os.path.exists(jdk_home_subdir):
                graal_jdk_home = jdk_home_subdir
        mx.log("Using Graal from GRAAL_JDK_HOME: " + graal_jdk_home)

        # Try to verify that we're getting what we expect:
        has_java = os.path.exists(os.path.join(graal_jdk_home, 'bin', mx.exe_suffix('java')))
        if not has_java:
            mx.abort(f"GRAAL_JDK_HOME does not contain java executable.")

        release = os.path.join(graal_jdk_home, 'release')
        if not os.path.exists(release):
            mx.abort(f"No 'release' file in GRAAL_JDK_HOME.")

        java_version = None
        with open(release, 'r') as f:
            while not java_version:
                line = f.readline()
                if 'JAVA_VERSION=' in line:
                    java_version = line

        if not java_version:
            mx.abort(f"Could not check Java version in GRAAL_JDK_HOME 'release' file.")
        actual_jdk_version = mx.VersionSpec(java_version.strip('JAVA_VERSION=').strip(' "\n\r'))
        if actual_jdk_version != jdk_version:
            mx.abort(f"GRAAL_JDK_HOME is not compatible with the requested JDK version.\n"
             f"actual version: '{actual_jdk_version}', version string: {java_version}, requested version: {jdk_version}.")

        return graal_jdk_home

    jdk_major_version = mx.get_jdk().version.parts[0]
    if enterprise:
        mx_args = ['-p', os.path.join(mx.suite('truffle').dir, '..', '..', 'graal-enterprise', 'vm-enterprise'), '--env', 'ee']
        edition = ""
    else:
        mx_args = ['-p', os.path.join(mx.suite('truffle').dir, '..', 'vm'), '--env', 'ce']
        edition = "COMMUNITY_"
    if not DISABLE_REBUILD:
        run_mx(mx_args + ["build", "--dep", f"GRAALVM_{edition}JAVA{jdk_major_version}"], env={**os.environ, **LATEST_JAVA_HOME})
    out = mx.OutputCapture()
    run_mx(mx_args + ["graalvm-home"], out=out)
    return out.data.splitlines()[-1].strip()

def get_maven_cache():
    buildnr = os.environ.get('BUILD_NUMBER')
    # don't worry about maven.repo.local if not running on gate
    return os.path.join(SUITE.get_mx_output_dir(), 'm2_cache_' + buildnr) if buildnr else None

def update_maven_opts(env):
    m2_cache = get_maven_cache()
    if m2_cache:
        mvn_repo_local = f'-Dmaven.repo.local={m2_cache}'
        maven_opts = env.get('MAVEN_OPTS')
        maven_opts = maven_opts + " " + mvn_repo_local if maven_opts else mvn_repo_local
        if mx.is_windows():
            maven_opts = maven_opts.replace("|", "^|")
        env['MAVEN_OPTS'] = maven_opts
        mx.log(f"Added '{mvn_repo_local}' to MAVEN_OPTS={maven_opts}")
    return env

def deploy_local_maven_repo(env=None):
    env = update_maven_opts({**os.environ.copy(), **(env or {})})
    run_mx_args = [
        '-p',
        os.path.join(mx.suite('truffle').dir, '..', 'vm'),
        '--dy',
        'graalpython',
    ]

    if not DISABLE_REBUILD:
        # build GraalPy and all the necessary dependencies, so that we can deploy them
        run_mx(run_mx_args + ["build"], env={**env, **LATEST_JAVA_HOME})

    # deploy maven artifacts
    version = GRAAL_VERSION
    path = os.path.join(SUITE.get_mx_output_dir(), 'public-maven-repo')
    licenses = ['EPL-2.0', 'PSF-License', 'GPLv2-CPE', 'ICU,GPLv2', 'BSD-simplified', 'BSD-new', 'UPL', 'MIT', 'GFTC']
    deploy_args = run_mx_args + [
        'maven-deploy',
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
        run_mx(deploy_args, env={**env, **LATEST_JAVA_HOME})
    return path, version, env


def deploy_graalpy_extensions_to_local_maven_repo(env=None):
    env = update_maven_opts({**os.environ.copy(), **(env or {})})
    env["MVNW_REPOURL"] = mx_urlrewrites.rewriteurl("https://repo.maven.apache.org/maven2/").rstrip('/')
    env["MVNW_VERBOSE"] = "true"

    gradle_java_home = os.environ.get('GRADLE_JAVA_HOME')
    if not gradle_java_home:
        def abortCallback(msg):
            mx.abort("Could not find a JDK of version between 17 and 21 to build a Gradle plugin from graalpy-extensions.\n"
                     "Export GRADLE_JAVA_HOME pointing to a suitable JDK or use the generic MX mechanism explained below:\n" + msg)
        gradle_java_home = mx.get_tools_jdk('17..21', abortCallback=abortCallback).home

    graalpy_extensions_path = os.environ.get('GRAALPY_EXTENSIONS_PATH')
    if not graalpy_extensions_path:
        mx.log("Cloning graalpy-extensions. If you want to use custom local clone, set env variable GRAALPY_EXTENSIONS_PATH")
        graalpy_extensions_path = os.path.join(SUITE.get_mx_output_dir(), 'graalpy-extensions')
        if os.path.exists(graalpy_extensions_path):
            shutil.rmtree(graalpy_extensions_path)
        mx.run(['git', 'clone', '--depth=1', 'https://github.com/oracle/graalpy-extensions.git', graalpy_extensions_path])

    local_repo_path = os.path.join(SUITE.get_mx_output_dir(), 'public-maven-repo')
    version = GRAAL_VERSION
    mx.run([os.path.join(graalpy_extensions_path, mx.cmd_suffix('mvnw')),
            '-Pmxurlrewrite', '-DskipJavainterfacegen', '-DskipTests', '-DdeployAtEnd=true',
            f'-Drevision={version}',
            f'-Dlocal.repo.url=' + pathlib.Path(local_repo_path).as_uri(),
            f'-DaltDeploymentRepository=local::default::file:{local_repo_path}',
            f"-Dgradle.java.home={gradle_java_home}",
            'deploy'], env=env, cwd=graalpy_extensions_path)

    return local_repo_path, version, env


def deploy_local_maven_repo_wrapper(*_):
    p, _, _ = deploy_local_maven_repo()
    print(f"local Maven repo path: {p}")


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
        quote = shlex.quote if sys.platform != 'win32' else lambda x: x
        def graalvm_vm_arg(java_arg):
            return quote(f'--vm.{java_arg[1:] if java_arg.startswith("-") else java_arg}')

        agent_args = ' '.join(graalvm_vm_arg(arg) for arg in mx_gate.get_jacoco_agent_args() or [])

        # We need to make sure the arguments get passed to subprocesses, so we create a temporary launcher
        # with the arguments.
        original_launcher = os.path.abspath(os.path.realpath(launcher))
        if sys.platform != 'win32':
            coverage_launcher = original_launcher + "_cov"
            c_launcher_source = coverage_launcher + ".c"
            agent_args_list = shlex.split(agent_args)
            extra_args_c = []
            for arg in agent_args_list:
                extra_args_c.append('new_args[arg_index++] = "' + arg.replace("\"", r"\"") + '";')
            extra_args_c = ' '.join(extra_args_c)
            c_code = dedent(f"""\
                    #include <stdio.h>
                    #include <stdlib.h>
                    #include <unistd.h>

                    int main(int argc, char **argv) {{
                        char *new_args[argc + 3 + {len(agent_args_list)}];
                        int arg_index = 0;
                        new_args[arg_index++] = argv[0];
                        new_args[arg_index++] = "--jvm";
                        {extra_args_c}
                        for (int i = 1; i < argc; i++) {{
                            new_args[arg_index++] = argv[i];
                        }}
                        new_args[arg_index] = NULL;
                        execvp("{original_launcher}", new_args);
                        perror("execvp failed");
                        return 1;
                    }}
            """)
            with open(c_launcher_source, "w") as f:
                f.write(c_code)
            compile_cmd = ["cc", c_launcher_source, "-o", coverage_launcher]
            subprocess.check_call(compile_cmd)
            os.chmod(coverage_launcher, 0o775)
        else:
            coverage_launcher = original_launcher.replace('.exe', '.cmd')
            # Windows looks for libraries on PATH, we need to add the jvm bin dir there or it won't find the instrumentation dlls
            jvm_bindir = os.path.join(os.path.dirname(os.path.dirname(original_launcher)), 'jvm', 'bin')
            with open(coverage_launcher, "w") as f:
                f.write(f'@echo off\nset PATH=%PATH%;{jvm_bindir}\n')
                exe_arg = quote(f"--python.Executable={coverage_launcher}")
                f.write(f'{original_launcher} --jvm {exe_arg} {agent_args} %*\n')
        mx.log(f"Replaced {launcher} with {coverage_launcher} to collect coverage")
        launcher = coverage_launcher
    return launcher


def python_svm(_=None):
    """Returns the path to GraalPy native image from 'native' standalone dev build.
    Also builds the standalone if not built already."""
    if mx_gate.get_jacoco_agent_args():
        return python_jvm()
    launcher = graalpy_standalone('native')
    mx.log(launcher)
    return launcher


def _python_test_runner():
    return os.path.join(SUITE.dir, "graalpython", "com.oracle.graal.python.test", "src", "runner.py")

def _python_unittest_root():
    return os.path.join(SUITE.dir, "graalpython", "com.oracle.graal.python.test", "src", "tests")


def graalpytest(args):
    # help is delegated to the runner, it will fake the mx-specific options as well
    parser = ArgumentParser(prog='mx graalpytest', add_help=False)
    parser.add_argument('--python')
    parser.add_argument('--svm', action='store_true')
    args, unknown_args = parser.parse_known_args(args)

    env = extend_os_env(
        MX_GRAALPYTEST='1',
        PYTHONHASHSEED='0',
    )

    python_args = []
    runner_args = []
    for arg in unknown_args:
        if arg.startswith(('--python.', '--engine.', '--vm.', '--inspect', '--log.', '--experimental-options')):
            python_args.append(arg)
        else:
            runner_args.append(arg)
    # if we got a binary path it's most likely CPython, so don't add graalpython args
    is_graalpy = False
    python_binary = args.python
    if not python_binary:
        is_graalpy = True
        python_args += ["--experimental-options=true", "--python.EnableDebuggingBuiltins"]
        if args.svm:
            python_binary = graalpy_standalone_native()
    elif 'graalpy' in os.path.basename(python_binary) or 'mxbuild' in python_binary:
        is_graalpy = True
        gp_args = ["--experimental-options=true", "--python.EnableDebuggingBuiltins"]
        if env.get("GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS") != "true":
            gp_args += ["--vm.ea", "--vm.esa"]
        mx.log(f"Executable seems to be GraalPy, prepending arguments: {gp_args}")
        python_args += gp_args
    if is_graalpy and BYTECODE_DSL_INTERPRETER:
        python_args.insert(0, "--vm.Dpython.EnableBytecodeDSLInterpreter=true")

    runner_args.append(f'--subprocess-args={shlex.join(python_args)}')
    if is_graalpy:
        runner_args.append(f'--append-path={os.path.join(_dev_pythonhome(), "lib-python", "3")}')
    cmd_args = [*python_args, _python_test_runner(), 'run', *runner_args]
    delete_bad_env_keys(env)
    if python_binary:
        try:
            result = run([python_binary, *cmd_args], nonZeroIsFatal=True, env=env)
            print(f"back from mx.run, returning {result}")
            return result
        except BaseException as e:
            print(f"Exception raised: {e}")
    else:
        return full_python(cmd_args, env=env)


def run_python_unittests(python_binary, args=None, paths=None, exclude=None, env=None,
                         cwd=None, lock=None, out=None, err=None, nonZeroIsFatal=True, timeout=None,
                         report: Union[Task, bool, None] = False, parallel=None, runner_args=None):
    if lock:
        lock.acquire()

    if parallel is None:
        parallel = 4 if paths is None else 1

    if sys.platform == 'win32':
        # Windows machines don't seem to have much memory
        parallel = min(parallel, 2)

    parallelism = str(min(os.cpu_count() or 1, parallel))

    args = args or []
    args = [
        "--vm.ea",
        "--experimental-options=true",
        "--python.EnableDebuggingBuiltins",
        *args,
    ]

    if env is None:
        env = os.environ.copy()
    env['PYTHONHASHSEED'] = '0'
    delete_bad_env_keys(env)

    if mx.primary_suite() != SUITE:
        env.setdefault("GRAALPYTEST_ALLOW_NO_JAVA_ASSERTIONS", "true")

    if (pip_index := env.get("PIP_INDEX_URL")) and "PIP_EXTRA_INDEX_URL" not in env:
        # the user was overriding the index, don't sneak our default extra
        # index in in that case
        env["PIP_EXTRA_INDEX_URL"] = pip_index

    if BYTECODE_DSL_INTERPRETER:
        args += ['--vm.Dpython.EnableBytecodeDSLInterpreter=true']
    args += [_python_test_runner(), "run", "--durations", "10", "-n", parallelism, f"--subprocess-args={shlex.join(args)}"]

    if runner_args:
        args += runner_args

    if exclude:
        for file in exclude:
            args += ['--ignore', file]

    if is_collecting_coverage() and mx_gate.get_jacoco_agent_args():
        # jacoco only dumps the data on exit, and when we run all our unittests
        # at once it generates so much data we run out of heap space
        args.append('--separate-workers')

    reportfile = None
    t0 = time.time()
    if report:
        reportfile = os.path.abspath(tempfile.mktemp(prefix="test-report-", suffix=".json"))
        args += ["--mx-report", reportfile]

    if paths is not None:
        args += paths
    else:
        args.append(os.path.relpath(_python_unittest_root()))

    print(f"[DEBUG] args: {args}")

    mx.logv(shlex.join([python_binary] + args))
    if lock:
        lock.release()
    result = run([python_binary] + args, nonZeroIsFatal=nonZeroIsFatal, env=env, cwd=cwd, out=out, err=err, timeout=timeout)
    if lock:
        lock.acquire()

    if isinstance(report, mx.Task):
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


def run_hpy_unittests(python_binary, args=None, env=None, nonZeroIsFatal=True, timeout=None, report: Union[Task, bool, None] = False):
    t0 = time.time()
    result = downstream_tests.downstream_test_hpy(python_binary, args=args, env=env, check=nonZeroIsFatal, timeout=timeout)
    if isinstance(report, mx.Task):
        mx_gate.make_test_report([{
            "name": report.title,
            "status": "PASSED" if result == 0 else "FAILED",
            "duration": int((time.time() - t0) * 1000)
        }], report.title)


def run_tagged_unittests(python_binary, env=None, cwd=None, nonZeroIsFatal=True, checkIfWithGraalPythonEE=False,
                         report: Union[Task, bool, None] = False, parallel=8, exclude=None, paths=()):

    if checkIfWithGraalPythonEE:
        mx.run([python_binary, "-c", "import sys; print(sys.version)"])
    run_python_unittests(
        python_binary,
        runner_args=[f'--append-path={os.path.join(_dev_pythonhome(), "lib-python", "3")}', '--tagged'],
        paths=paths or [os.path.relpath(os.path.join(_get_stdlib_home(), 'test'))],
        env=env,
        cwd=cwd,
        nonZeroIsFatal=nonZeroIsFatal,
        report=report,
        parallel=parallel,
        exclude=exclude,
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

def graalpython_gate_runner(_, tasks):
    report = lambda: (not is_collecting_coverage()) and task
    nonZeroIsFatal = not is_collecting_coverage()

    # JUnit tests
    with Task('GraalPython JUnit', tasks, tags=[GraalPythonTags.junit]) as task:
        if task:
            run_mx(["build"], env={**os.environ, **LATEST_JAVA_HOME})
            if WIN32:
                punittest(
                    [
                        "--verbose",
                        "--no-leak-tests",
                        "--regex",
                        r'((graal\.python\.test\.integration)|(graal\.python\.test\.(builtin|interop|util))|(graal\.python\.cext\.test))'
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
            if report():
                tmpfile = tempfile.NamedTemporaryFile(delete=False, suffix='.json.gz')
                try:
                    # Cannot use context manager because windows doesn't allow
                    # make_test_report to read the file while it is open for
                    # writing
                    mx.command_function('tck')([f'--json-results={tmpfile.name}'])
                    mx_gate.make_test_report(tmpfile.name, GraalPythonTags.junit + "-TCK")
                finally:
                    tmpfile.close()
                    try:
                        os.unlink(tmpfile.name)
                    except:
                        pass # Sometimes this fails on windows
            else:
                mx.command_function('tck')([])

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

            env['PATH'] = get_path_with_patchelf()

            mx.log("Running integration JUnit tests on GraalVM SDK")
            env['JAVA_HOME'] = graalvm_jdk()
            mx.run_maven(mvn_cmd_base + ['-U', 'clean', 'test'], env=env)

            env['JAVA_HOME'] = os.environ['JAVA_HOME']
            mx.log(f"Running integration JUnit tests on vanilla JDK: {os.environ.get('JAVA_HOME', 'system java')}")
            mx.run_maven(mvn_cmd_base + ['-U', '-Dpolyglot.engine.WarnInterpreterOnly=false', 'clean', 'test'], env=env)

    # JUnit tests with Maven and polyglot isolates
    with Task('GraalPython integration JUnit with Maven and Polyglot Isolates', tasks, tags=[GraalPythonTags.junit_maven_isolates]) as task:
        if task:
            if mx.is_windows():
                mx.log(mx.colorize('Polyglot isolate tests do not work on Windows', color='magenta'))
                return

            mvn_repo_path, artifacts_version, env = deploy_local_maven_repo(env={
                "DYNAMIC_IMPORTS": "/truffle-enterprise,/substratevm-enterprise",
                "NATIVE_IMAGES": "",
                "POLYGLOT_ISOLATES": "python",
            })
            mvn_repo_path = pathlib.Path(mvn_repo_path).as_uri()
            central_override = mx_urlrewrites.rewriteurl('https://repo1.maven.org/maven2/')
            pom_path = os.path.join(SUITE.dir, 'graalpython/com.oracle.graal.python.test.integration/pom.xml')
            mvn_cmd_base = ['-f', pom_path,
                            f'-Dcom.oracle.graal.python.test.polyglot.version={artifacts_version}',
                            f'-Dcom.oracle.graal.python.test.polyglot_repo={mvn_repo_path}',
                            f'-Dcom.oracle.graal.python.test.central_repo={central_override}',
                            '--batch-mode']

            env['PATH'] = get_path_with_patchelf()

            mx.log("Running integration JUnit tests on GraalVM SDK with external polyglot isolates")
            env['JAVA_HOME'] = graalvm_jdk(enterprise=True)
            mx.run_maven(mvn_cmd_base + [
                '-U',
                '-Pisolate',
                '-Dpolyglot.engine.AllowExperimentalOptions=true',
                '-Dpolyglot.engine.SpawnIsolate=true',
                '-Dpolyglot.engine.IsolateMode=external',
                'clean',
                'test',
            ], env=env)

            mx.log("Running integration JUnit tests on GraalVM SDK with untrusted sandbox policy")
            mx.run_maven(mvn_cmd_base + [
                '-Pisolate',
                '-Dtest=SandboxPolicyUntrustedTest',
                'test',
            ], env=env)

    # Unittests on JVM
    with Task('GraalPython JVM build', tasks, tags=[GraalPythonTags.jvmbuild]) as task:
        if task:
            graalpy_standalone_jvm()

    with Task('GraalPython Python unittests', tasks, tags=[GraalPythonTags.unittest]) as task:
        if task:
            run_python_unittests(
                graalpy_standalone_jvm(),
                nonZeroIsFatal=nonZeroIsFatal,
                report=report(),
                parallel=6,
            )

    with Task('GraalPython Python unittests with CPython', tasks, tags=[GraalPythonTags.unittest_cpython]) as task:
        if task:
            env = extend_os_env(PYTHONHASHSEED='0')
            test_args = [get_cpython(), _python_test_runner(), "run", "-n", "6", "graalpython/com.oracle.graal.python.test/src/tests"]
            run(test_args, nonZeroIsFatal=True, env=env)

    with Task('GraalPython sandboxed tests', tasks, tags=[GraalPythonTags.unittest_sandboxed]) as task:
        if task:
            run_python_unittests(graalpy_standalone_jvm_enterprise(), args=SANDBOXED_OPTIONS, report=report())

    with Task('GraalPython multi-context unittests', tasks, tags=[GraalPythonTags.unittest_multi]) as task:
        if task:
            run_python_unittests(graalpy_standalone_jvm(), args=["-multi-context"], nonZeroIsFatal=nonZeroIsFatal, report=report())

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
            run_hpy_unittests(graalpy_standalone_native_enterprise(), args=SANDBOXED_OPTIONS, report=report())

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
            deploy_graalpy_extensions_to_local_maven_repo()

            if RUNNING_ON_LATEST_JAVA:
                # our standalone python binary is meant for standalone graalpy
                # releases which are only for latest
                env['ENABLE_STANDALONE_UNITTESTS'] = 'true'
            env['JAVA_HOME'] = gvm_jdk
            env['PYTHON_STANDALONE_HOME'] = standalone_home

            # setup maven downloader overrides
            env['MAVEN_REPO_OVERRIDE'] = ",".join([
                f"{pathlib.Path(mvn_repo_path).as_uri()}/",
                mx_urlrewrites.rewriteurl('https://repo1.maven.org/maven2/'),
            ])

            env["org.graalvm.maven.downloader.version"] = version
            env["org.graalvm.maven.downloader.repository"] = f"{pathlib.Path(mvn_repo_path).as_uri()}/"

            # run the test
            mx.logv(f"running with os.environ extended with: {env=}")
            run_python_unittests(
                os.path.join(standalone_home, 'bin', _graalpy_launcher()),
                paths=["graalpython/com.oracle.graal.python.test/src/tests/standalone/test_standalone.py"],
                env=env,
                parallel=3,
            )

    with Task('GraalPython Python tests', tasks, tags=[GraalPythonTags.tagged]) as task:
        if task:
            # don't fail this task if we're running with the jacoco agent, we know that some tests don't pass with it enabled
            run_tagged_unittests(graalpy_standalone_native(), nonZeroIsFatal=(not is_collecting_coverage()), report=report())

    # Unittests on SVM
    with Task('GraalPython build on SVM', tasks, tags=[GraalPythonTags.svmbuild]) as task:
        if task:
            graalpy_standalone_native()

    with Task('GraalPython tests on SVM', tasks, tags=[GraalPythonTags.svmunit]) as task:
        if task:
            run_python_unittests(graalpy_standalone_native(), parallel=8, report=report())

    with Task('GraalPython sandboxed tests on SVM', tasks, tags=[GraalPythonTags.svmunit_sandboxed]) as task:
        if task:
            run_python_unittests(graalpy_standalone_native_enterprise(), parallel=8, args=SANDBOXED_OPTIONS, report=report())

    with Task('GraalPython license header update', tasks, tags=[GraalPythonTags.license]) as task:
        if task:
            python_checkcopyrights([])

    with Task('GraalPython GraalVM build', tasks, tags=[GraalPythonTags.svm, GraalPythonTags.graalvm], report=True) as task:
        if task:
            with set_env(PYTHONIOENCODING=None, MX_CHECK_IOENCODING="0"):
                svm_image = python_svm()
                benchmark = os.path.join(PATH_MESO, "image-magix.py")
                out = mx.OutputCapture()
                run([svm_image, "-S", "--log.python.level=FINE", benchmark], nonZeroIsFatal=True, out=mx.TeeOutputCapture(out), err=mx.TeeOutputCapture(out))
            success = "\n".join([
                "[0, 0, 0, 0, 0, 0, 10, 10, 10, 0, 0, 10, 3, 10, 0, 0, 10, 10, 10, 0, 0, 0, 0, 0, 0]",
            ])
            if success not in out.data:
                mx.abort('Output from generated SVM image "' + svm_image + '" did not match success pattern:\n' + success)
            if not WIN32:
                assert "Using preinitialized context." in out.data

    with Task('Python SVM Truffle TCK', tasks, tags=[GraalPythonTags.language_checker], report=True) as task:
        if task:
            run_mx([
                "--dy", "graalpython,/substratevm",
                "-p", os.path.join(mx.suite("truffle").dir, "..", "vm"),
                "--native-images=",
                "build",
            ], env={**os.environ, **LATEST_JAVA_HOME})
            run_mx([
                "--dy", "graalpython,/substratevm",
                "-p", os.path.join(mx.suite("truffle").dir, "..", "vm"),
                "--native-images=",
                "gate", "svm-truffle-tck-python",
            ])

    with Task("Graalpython tox example", tasks, tags=["tox-example"]) as task:
        if task:
            try:
                tox_example([])
            except:
                mx.log("TIP: run 'mx help tox-example' to learn more about reproducing this test locally")
                raise

    if WIN32 and is_collecting_coverage():
        mx.log("Ask for shutdown of any remaining graalpy.exe processes")
        # On windows, the jacoco command can fail if the file is still locked
        # by lingering test processes, so we try to give it a bit of a cleanup
        mx.run([
            'taskkill.exe',
            '/T', # with children
            '/IM',
            'graalpy.exe',
        ], nonZeroIsFatal=False)
        time.sleep(2)
        mx.log("Forcefully terminate any remaining graalpy.exe processes")
        mx.run([
            'taskkill.exe',
            '/F', # force
            '/T', # with children
            '/IM',
            'graalpy.exe',
        ], nonZeroIsFatal=False)
        # Forcefully killing processes on Windows does not release file
        # locks immediately, so we still need to sleep for a bit in the
        # hopes that the OS will release
        time.sleep(8)


mx_gate.add_gate_runner(SUITE, graalpython_gate_runner)


def tox_example(args=None):
    """
    Runs the tox example: executing tox in a CPython venv, which then executes
    pytest tests of an example package 'leftpad' on GraalPython.

    To pass additional arguments to GraalPython, set the GRAAL_PYTHON_ARGS
    environment variable, tox will forward it to GraalPython.

    Run with '--help' to learn about supported options.
    """
    import argparse
    parser = argparse.ArgumentParser(prog='mx tox-example')
    parser.add_argument("--reuse-venv", action="store_true",
                        help="Whether to reuse existing venv created by previous invocations of this command.")
    opts = parser.parse_args(args)

    graalpy = graalpy_standalone_native_enterprise()

    tox_project_dir = os.path.join(
        cast(mx.Project, mx.project("com.oracle.graal.python.test", fatalIfMissing=True)).dir,
        "src",
        "tox"
    )

    mx.log("Setting up CPython venv to run tox itself")
    libs = [
        "distlib==0.3.9",
        "filelock==3.18.0",
        "packaging==25.0",
        "platformdirs==4.3.8",
        "pluggy==1.5.0",
        "py==1.11.0",
        "pyparsing==3.2.3",
        "six==1.17.0",
        "toml==0.10.2",
        "tox==4.25.0",
        "virtualenv==20.31.2",
        os.path.join(os.path.dirname(graalpy), "..", "graalpy_virtualenv_seeder"),
    ]

    def get_new_vm(project_name, install_libs=None, reuse_existing=False):
        if install_libs is None:
            install_libs = []
        import platform
        mx.log("[platform] {}".format(platform.uname()))
        path = os.path.join(mx.dependency("com.oracle.graal.python.test").get_output_root(), "tox_venv")
        reuse = os.path.exists(path) and reuse_existing
        action_name = "Reusing existing" if reuse else "Creating"
        mx.logv("{} venv for {} in {}".format(action_name, project_name, path))

        # remove any pre-existing venv to ensure that launchers are freshly created
        if not reuse and os.path.isdir(path):
            mx.log("Deleting pre-existing venv in {}".format(path))
            from shutil import rmtree
            rmtree(path)

        quiet_opt = ["-q"] if not mx._opts.verbose else []
        env_py3_home = os.environ.get("PYTHON3_HOME")
        if env_py3_home:
            python = os.path.join(env_py3_home, "python3")
            mx.logv("Overriding 'python3' using environment variable PYTHON3_HOME to '{}'".format(python))
        else:
            python = "python3"
        mx.log("{} CPython venv for {} (bin: {})".format(action_name, project_name, python))
        if not reuse:
            mx.run([python, "-m", "venv", "--clear", path])
        vm = os.path.join(path, "bin", "python")

        os.environ['VIRTUAL_ENV'] = path
        os.environ['PATH'] = "{}:{}".format(os.path.join(path, "bin"), os.environ.get("PATH", ""))

        if not reuse:
            for lib in install_libs:
                try:
                    cmd = [vm, "-m", "pip"] + quiet_opt + ["install", lib]
                    mx.log("running: {}".format(' '.join(cmd)))
                    mx.run(cmd)
                except:
                    mx.abort("Could not install dependency %s" % install_libs)
        os.environ['PYTHON'] = vm
        os.environ["PYTHON_VM"] = vm
        return vm

    python3 = get_new_vm("tox", install_libs=libs, reuse_existing=opts.reuse_venv)

    new_env = os.environ.copy()
    new_env['PATH'] = new_env['PATH'] + os.pathsep + os.path.dirname(graalpy)
    mx.log(f"Added {graalpy} to the PATH")

    def check_output(expected, lines):
        for e in expected:
            if not any(e in l for l in lines):
                mx.abort("Could not find expected {} in the output".format(e))

    # Passing tests:
    mx.log("Running {} -m tox -e graalpy".format(python3))
    wd = os.path.join(tox_project_dir, "leftpad")
    output = mx.LinesOutputCapture()
    mx.log("Running {} -m tox -e graalpy".format(python3))
    output_capture = mx.TeeOutputCapture(output)
    mx.run([python3, "-m", "tox"], env=new_env, cwd=wd, out=output_capture, err=output_capture)
    check_output(["4 passed", "graalpy: OK"], output.lines)

    # Failing tests:
    mx.log("Running {} -m tox -e graalpy with intentionally failing tests".format(python3))
    output = mx.LinesOutputCapture()
    new_env['GRAALPY_LEFTPAD_FAIL'] = '1'
    output_capture = mx.TeeOutputCapture(output)
    exit_code = mx.run([python3, "-m", "tox"], env=new_env, cwd=wd, out=output_capture, err=output_capture, nonZeroIsFatal=False)
    check_output(["test_leftpad.py::test_leftpad_failing - AssertionError", "1 failed, 3 passed"], output.lines)
    if exit_code == 0:
        mx.abort("Expected the tests to fail")


class ArchiveProject(mx.ArchivableProject):
    def __init__(self, suite, name, deps, workingSets, theLicense, **_):
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


def deploy_binary_if_main(args):
    """if the active branch is the main branch, deploy binaries for the primary suite to remote maven repository."""
    active_branch = SUITE.vc.active_branch(SUITE.dir)
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


def _get_suite_parent_dir(suitename):
    return os.path.dirname(mx.suite(suitename).dir)


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
    prefix, _, suffix = projectname.rpartition(":")
    for suite in mx.suites():
        if prefix and suite.name != prefix:
            continue
        for p in itertools.chain(suite.projects, suite.dists):
            if p.name == suffix:
                try:
                    return p.get_output_root()
                except:
                    return p.get_output()
    mx.abort("Could not find out dir for project %s" % projectname)

# We use the ordinal value of this character and add it to the version parts to
# ensure that we store ASCII-compatible printable characters into the versions
# file.
#
# IMPORTANT: This needs to be in sync with 'PythonLanguage.VERSION_BASE' and
#            'PythonResource.VERSION_BASE'.
VERSION_BASE = '!'

def py_version_short(variant=None, **_):
    if variant == 'major_minor_nodot':
        return PYTHON_VERSION_MAJ_MIN.replace(".", "")
    elif variant == 'binary':
        return "".join([chr(int(p) + ord(VERSION_BASE)) for p in PYTHON_VERSION.split(".")])
    else:
        return PYTHON_VERSION_MAJ_MIN


def graal_version_short(variant=None, **_):
    if variant == 'major_minor_nodot':
        return GRAAL_VERSION_MAJ_MIN.replace(".", "")
    elif variant == 'major_minor':
        return GRAAL_VERSION_MAJ_MIN
    elif variant == 'binary':
        # PythonLanguage and PythonResource consume this data, and they assume 3 components, so we cap the list size
        # to 3 although the version may have even more components
        return "".join([chr(int(p) + ord(VERSION_BASE)) for p in GRAAL_VERSION.split(".")[:3]])
    elif variant == 'hex':
        parts = GRAAL_VERSION.split(".")
        num = 0
        for i in range(3):
            num <<= 8
            num |= int(parts[i]) if i < len(parts) else 0
        num <<= 8
        num |= release_level('int') << 4
        return hex(num)
    else:
        return '.'.join(GRAAL_VERSION.split('.')[:3])


@overload
def release_level(variant: Literal['int']) -> int: ...


@overload
def release_level(variant: Union[Literal['binary'], None]) -> str: ...


def release_level(variant=None):
    # CPython has alpha, beta, candidate and final. We distinguish just two at the moment
    level = 'alpha'
    if SUITE.suiteDict['release']:
        level = 'final'
    if variant in ('binary', 'int'):
        level_num = {
            'alpha': 0xA,
            'beta': 0xB,
            'candidate': 0xC,
            'final': 0xF,
        }[level]
        if variant == 'binary':
            return chr(level_num + ord(VERSION_BASE))
        return level_num
    return level


def graalpy_ext(*_):
    os = mx_subst.path_substitutions.substitute('<os>')
    arch = mx_subst.path_substitutions.substitute('<arch>')
    if arch == 'amd64':
        # be compatible with CPython's designation
        # (see also: 'PythonUtils.getPythonArch')
        arch = 'x86_64'

    # 'pyos' also needs to be compatible with CPython's designation.
    # See class 'com.oracle.graal.python.annotations.PythonOS'
    # In this case, we can just use 'sys.platform' of the Python running MX.
    pyos = sys.platform

    # on Windows we use '.pyd' else '.so' but never '.dylib' (similar to CPython):
    # https://github.com/python/cpython/issues/37510
    ext = 'pyd' if os == 'windows' else 'so'
    return f'.graalpy{GRAAL_VERSION_MAJ_MIN.replace(".", "") + dev_tag()}-{PYTHON_VERSION_MAJ_MIN.replace(".", "")}-native-{arch}-{pyos}.{ext}'


def dev_tag(_=None):
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
mx_subst.path_substitutions.register_with_arg('suite_parent', _get_suite_parent_dir)
mx_subst.path_substitutions.register_with_arg('src_dir', _get_src_dir)
mx_subst.path_substitutions.register_with_arg('output_root', _get_output_root)
mx_subst.path_substitutions.register_with_arg('py_ver', py_version_short)
mx_subst.path_substitutions.register_with_arg('graal_ver', graal_version_short)
mx_subst.path_substitutions.register_with_arg('release_level', release_level)
mx_subst.results_substitutions.register_with_arg('dev_tag', dev_tag)

mx_subst.path_substitutions.register_no_arg('graalpy_ext', graalpy_ext)
mx_subst.results_substitutions.register_no_arg('graalpy_ext', graalpy_ext)


def update_import(name, suite_py: Path, args):
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
    vc = cast(mx.VC, mx.VC.get_vc(dep_dir))
    repo_name = os.path.basename(dep_dir)
    if repo_name == "graal" and args.graal_rev:
        rev = args.graal_rev
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
        mx.update_file(suite_py.resolve().as_posix(), "".join([contents[:start], tip, contents[end:]]), showDiff=True)
    return tip


def update_import_cmd(args):
    """Update our imports"""

    parser = ArgumentParser()
    parser.add_argument('--graal-rev', default='')
    parser.add_argument('--no-pull', action='store_true')
    parser.add_argument('--no-push', action='store_true')
    parser.add_argument('--allow-dirty', action='store_true')
    parser.add_argument('--no-master-check', action='store_true', help="do not check if repos are on master branch (e.g., when detached)")
    args = parser.parse_args(args)

    vc = SUITE.vc

    current_branch = vc.active_branch(SUITE.dir, abortOnError=not args.no_master_check)
    if vc.isDirty(SUITE.dir) and not args.allow_dirty:
        mx.abort(f"updating imports should be done on a clean branch, not clean: {SUITE.dir}")
    if current_branch == "master" or args.no_master_check:
        vc.git_command(SUITE.dir, ["checkout", "-b", f"update/GR-21590/{datetime.datetime.now().strftime('%d%m%y')}"])
        current_branch = vc.active_branch(SUITE.dir)

    repo = Path(SUITE.dir)
    truffle_repo = Path(cast(mx.SourceSuite, mx.suite("truffle")).dir).parent
    suite_py = Path(__file__).parent / "suite.py"

    # find all imports we might update
    imports_to_update = set()
    d = {}
    with open(suite_py) as f:
        exec(f.read(), d, d) # pylint: disable=exec-used;
    for suite in d["suite"].get("imports", {}).get("suites", []):
        imports_to_update.add(suite["name"])

    revisions = {}
    # now update all imports
    for name in imports_to_update:
        revisions[name] = update_import(name, suite_py, args)

    shutil.copy(truffle_repo / "common.json", repo / "ci" / "graal" / "common.json")
    shutil.copytree(truffle_repo / "ci", repo / "ci" / "graal" / "ci", dirs_exist_ok=True)

    if vc.isDirty(repo):
        prev_verbosity = mx.get_opts().very_verbose
        mx.get_opts().very_verbose = True
        try:
            vc.commit(repo, "Update imports")
            if not args.no_push:
                vc.git_command(repo, ["push", "-u", "origin", "HEAD:%s" % current_branch], abortOnError=True)
                mx.log("Import update was pushed")
            else:
                mx.log("Import update was committed")
        finally:
            mx.get_opts().very_verbose = prev_verbosity


def python_style_checks(args):
    "Check (and fix where possible) copyrights, eclipse formatting, and spotbugs"
    warn_about_old_hardcoded_version()
    python_run_mx_filetests(args)
    check_unused_operations()
    python_checkcopyrights(["--fix"] if "--fix" in args else [])
    if not os.environ.get("ECLIPSE_EXE"):
        find_eclipse()
    if os.environ.get("ECLIPSE_EXE"):
        mx.command_function("eclipseformat")(["--primary"])
    if "--no-spotbugs" not in args:
        mx.command_function("spotbugs")([])
    if "--no-sigcheck" not in args:
        mx.command_function("sigtest")([])


def python_checkcopyrights(args):
    if mx.is_windows():
        # skip, broken with crlf stuff
        return
    files = None
    if '--files' in args:
        i = args.index('--files')
        files = args[i + 1:]
        args = args[:i]
    # we wan't to ignore lib-python/3, because that's just crazy
    listfilename = tempfile.mktemp()
    with open(listfilename, "w") as listfile:
        if files is None:
            mx.run(["git", "ls-tree", "-r", "HEAD", "--name-only"], out=listfile)
        else:
            for x in files:
                listfile.write(x)
                listfile.write('\n')
    with open(listfilename, "r") as listfile:
        content = listfile.read()
    with open(listfilename, "w") as listfile:
        for line in content.split("\n"):
            if any(x in line for x in [
                "lib-python/3",
                ".test/testData",
                "/hpy/",
                "com.oracle.graal.python.test.integration/src/org.graalvm.python.vfs/",
                "com.oracle.graal.python.test.integration/src/GRAALPY-VFS/",
            ]):
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


def python_run_mx_filetests(_):
    for test in glob.glob(os.path.join(os.path.dirname(__file__), "test_*.py")):
        if not test.endswith("data.py"):
            mx.log(test)
            mx.run([sys.executable, test, "-v"])


def check_unused_operations():
    root_node = "graalpython/com.oracle.graal.python/src/com/oracle/graal/python/nodes/bytecode_dsl/PBytecodeDSLRootNode.java"
    in_operation = False
    operations = []
    with open(os.path.join(SUITE.dir, root_node)) as f:
        while line := f.readline():
            if not in_operation and ('@Operation\n' in line or '@Operation(' in line):
                in_operation = True
            elif in_operation:
                if names := re.findall(r'public static final class (\S+)', line):
                    in_operation = False
                    operations += names
    compiler = "graalpython/com.oracle.graal.python/src/com/oracle/graal/python/compiler/bytecode_dsl/RootNodeCompiler.java"
    with open(os.path.join(SUITE.dir, compiler)) as f:
        contents = f.read().lower()
    result = True
    for n in operations:
        if not n.lower() in contents:
            mx.log_error(f"ERROR: unused @Operation {n}")
            result = False
    if not result:
        mx.abort("Found unused @Operation, see above")



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


def bytecode_dsl_build_args():
    return ['-Dpython.EnableBytecodeDSLInterpreter=true'] if BYTECODE_DSL_INTERPRETER else []

mx_sdk.register_graalvm_component(mx_sdk.GraalVmLanguage(
    suite=SUITE,
    name='GraalVM Python',
    short_name='pyn',
    dir_name='python',
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'pynl',
        'Truffle',
        'TRegex',
        'ICU4J',
        'XZ',
    ],
    truffle_jars=[
        'graalpython:GRAALPYTHON',
        'graalpython:BOUNCYCASTLE-PROVIDER',
        'graalpython:BOUNCYCASTLE-PKIX',
        'graalpython:BOUNCYCASTLE-UTIL',
    ],
    support_distributions=[
        'graalpython:GRAALPYTHON_GRAALVM_SUPPORT',
        'graalpython:GRAALPYTHON_GRAALVM_DOCS',
        'graalpython:GRAALPY_VIRTUALENV_SEEDER',
    ],
    library_configs=[
        mx_sdk.LanguageLibraryConfig(
            launchers=['bin/<exe:graalpy>', 'bin/<exe:python>', 'bin/<exe:python3>', 'libexec/<exe:graalpy-polyglot-get>'],
            jar_distributions=['graalpython:GRAALPYTHON-LAUNCHER', 'sdk:MAVEN_DOWNLOADER'],
            main_class=GRAALPYTHON_MAIN_CLASS,
            build_args=[
                '-H:+DetectUserDirectoriesInImageHeap',
                '-H:-CopyLanguageResources',
                # Uncomment to disable JLine FFM provider at native image build time
                # '-Dorg.graalvm.shadowed.org.jline.terminal.ffm.disable=true',
                '--enable-native-access=org.graalvm.shadowed.jline',
                '-Dpolyglot.python.PosixModuleBackend=native',
                '-Dpolyglot.python.Sha3ModuleBackend=native',
                '-Dpolyglot.python.CompressionModulesBackend=native',
            ] + bytecode_dsl_build_args(),
            language='python',
            default_vm_args=[
                '--vm.Xss16777216', # request 16M of stack
                '--vm.-enable-native-access=org.graalvm.shadowed.jline',
            ],
            # Force launcher and jline on ImageModulePath (needed for --enable-native-access=org.graalvm.shadowed.jline)
            use_modules='image',
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

class CharsetFilteringPariticpant:
    """
    Remove charset providers from the resulting JAR distribution. Done to avoid libraries (icu4j-charset)
    adding their charsets implicitly to native image. We need to add them explicitly in a controlled way.
    """
    def __opened__(self, __archive__, __src_archive__, services):
        self.__services = services

    def __closing__(self):
        self.__services.pop('java.nio.charset.spi.CharsetProvider', None)


def warn_about_old_hardcoded_version():
    """
    Ensure hardcoded versions everywhere are what we expect, either matching the master version
    or one of the latest releases.
    """

    def hardcoded_ver_is_behind_major_minor(m):
        if m.group(1) != GRAAL_VERSION_MAJ_MIN:
            return f"Hardcoded version in `{m.group().strip()}` should have {GRAAL_VERSION_MAJ_MIN} as <major>.<minor> version."

    files_with_versions = {
        "graalpython/com.oracle.graal.python.test.integration/pom.xml": {
            r'<com.oracle.graal.python.test.polyglot.version>(\d+\.\d+)(?:\.\d+)*' : hardcoded_ver_is_behind_major_minor,
        }
    }
    replacements = set()
    for path, patterns in files_with_versions.items():
        full_path = os.path.join(SUITE.dir, path)
        with open(full_path, "r", encoding="utf-8") as f:
            content = f.read()
        for pattern, test in patterns.items():
            patternString = pattern
            pattern = re.compile(pattern, flags=re.M)
            if not pattern.search(content, 0):
                replacements.add((path, f"Found no occurrence of pattern '${patternString}'"))
            else:
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


def mx_post_parse_cmd_line(_):
    # all projects are now available at this time
    mx_graalpython_benchmark.register_vms(SUITE, SANDBOXED_OPTIONS)
    mx_graalpython_benchmark.register_suites()
    mx_graalpython_python_benchmarks.register_python_benchmarks()

    for dist in mx.suite('graalpython').dists:
        if hasattr(dist, 'set_archiveparticipant'):
            dist.set_archiveparticipant(CharsetFilteringPariticpant())


def python_coverage(args):
    "Generate coverage report for our unittests"
    parser = ArgumentParser(prog='mx python-coverage')
    subparsers = parser.add_subparsers()
    jacoco_parser = subparsers.add_parser('jacoco', help="Generate Jacoco (Java) coverage")
    jacoco_parser.set_defaults(mode='jacoco')
    jacoco_parser.add_argument('--tags', required=True, help="Tags for mx gate")
    truffle_parser = subparsers.add_parser('truffle', help="Generate Truffle (Python) coverage")
    truffle_parser.set_defaults(mode='truffle')
    args = parser.parse_args(args)

    # do not endlessly rebuild tests, build once for all
    run_mx(["build"], env={**os.environ, **LATEST_JAVA_HOME})
    run_mx(["build", "--dep", "com.oracle.graal.python.test"], env={**os.environ, **LATEST_JAVA_HOME})
    env = extend_os_env(
        GRAALPYTHON_MX_DISABLE_REBUILD="True",
        GRAALPYTEST_FAIL_FAST="False",
    )

    global _COLLECTING_COVERAGE
    _COLLECTING_COVERAGE = True

    if args.mode == 'jacoco':
        jacoco_args = [
            '--jacoco-omit-excluded',
            '--jacoco-generic-paths',
            '--jacoco-omit-src-gen',
            '--jacocout', 'coverage',
            '--jacoco-format', 'lcov',
        ]
        run_mx([
            '--strict-compliance',
            '--primary', 'gate',
            '-B=--force-deprecation-as-warning-for-dependencies',
            '--strict-mode',
            '--tags', args.tags,
        ] + jacoco_args, env=env)
        run_mx([
            '--strict-compliance',
            '--kill-with-sigquit',
            'jacocoreport',
            '--format', 'lcov',
            '--omit-excluded',
            'coverage',
            '--generic-paths',
            '--exclude-src-gen',
        ], env=env)

    if args.mode == 'truffle':
        executable = graalpy_standalone_jvm()
        file_filter = f"*lib-graalpython*,*graalpython/include*,*com.oracle.graal.python.cext*,*lib/graalpy{graal_version_short()}*,*include/python{py_version_short()}*"
        variants = [
            {"args": []},
            # Run only a few tagged tests that are relevant to the files in lib-graalpython
            {"tagged": True, "paths": ["test_re.py", "test_unicodedata.py"]},
            {"args": ["--python.EmulateJython"], "paths": ["test_interop.py"]},
            {"hpy": True},
        ]

        common_coverage_args = [
            "--experimental-options",
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
            env['GRAAL_PYTHON_VM_ARGS'] = " ".join(extra_args)
            if kwds.pop("tagged", False):
                run_tagged_unittests(executable, env=env, nonZeroIsFatal=False, **kwds) # pylint: disable=unexpected-keyword-arg;
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
        args = [mx_subst.path_substitutions.substitute(a, dependency=self) for a in cast(GraalpythonProject, self.subject).args]
        return bool(self.run(args))

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
            "--PosixModuleBackend=java",
            "--CompressionModulesBackend=java",
            f"--PyCachePrefix={pycache_dir}",
            "--DisableFrozenModules",
            "-B",
            "-S"
        ]
        mx_util.ensure_dir_exists(cwd)

        env = env.copy() if env else os.environ.copy()
        env.update(cast(GraalpythonProject, self.subject).getBuildEnv())
        jdk = mx.get_jdk()  # Don't get JVMCI, it might not have finished building by this point
        rc = do_run_python(args, jdk=jdk, env=env, cwd=cwd, minimal=True, out=self.PrefixingOutput(self.subject.name, mx.log), err=self.PrefixingOutput(self.subject.name, mx.log_error), **kwargs)

        shutil.rmtree(cwd) # remove the temporary build files
        # if we're just running style tests, this is allowed to fail
        if os.environ.get("BUILD_NAME") == "python-style":
            return 0
        return min(rc, 1)

    def src_dir(self):
        return cast(GraalpythonProject, self.subject).dir

    def newestOutput(self):
        return None

    def needsBuild(self, newestInput):
        if self.args.force:
            return True, 'forced build'
        root = self.subject.get_output_root()
        if not os.path.exists(root):
            return True, 'inexisting output dir'
        if not newestInput:
            return False, "no input"
        ts = None
        for dirpath, _, filenames in os.walk(root):
            for f in filenames:
                file_path = os.path.join(dirpath, f)
                t = mx.TimeStampFile(file_path)
                if not ts:
                    ts = t
                elif ts.isNewerThan(t):
                    ts = t
        if not ts:
            return True, "no output files"
        else:
            return ts.isOlderThan(newestInput), str(ts)

    def clean(self, forBuild=False):
        if forBuild == "reallyForBuild":
            try:
                shutil.rmtree(self.subject.get_output_root())
            except BaseException:
                return True
        return False


class GraalpythonProject(mx.ArchivableProject):
    args: str

    def __init__(self, suite, name, subDir, srcDirs, deps, workingSets, d, theLicense=None, **kwargs): # pylint: disable=super-init-not-called
        context = 'project ' + name
        self.buildDependencies = mx.Suite._pop_list(kwargs, 'buildDependencies', context)
        mx.Project.__init__(self, suite, name, subDir, srcDirs, deps, workingSets, d, theLicense, **kwargs)

    def getOutput(self, replaceVar=mx_subst.results_substitutions):
        return replaceVar.substitute(self.get_output_root())

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

    def getBuildTask(self, args): # pyright: ignore
        return GraalpythonBuildTask(args, self)

    def getBuildEnv(self, replaceVar=mx_subst.path_substitutions):
        ret = {}
        if buildEnv := getattr(self, 'buildEnv', {}):
            for key, value in buildEnv.items():
                ret[key] = replaceVar.substitute(value, dependency=self)
        return ret


class GraalpythonFrozenModuleBuildTask(GraalpythonBuildTask):
    def build(self):
        # We freeze modules twice: once for the manual Bytecode interpreter and once for the DSL interpreter.
        args = [mx_subst.path_substitutions.substitute(a, dependency=self) for a in cast(GraalpythonProject, self.subject).args]

        manual_vm_args = ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000"] if 'DEBUG_FROZEN' in os.environ else []
        dsl_vm_args = ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000"] if 'DEBUG_FROZEN_BCI' in os.environ else []

        return bool(
            self.run_for(args, "manual bytecode", extra_vm_args=manual_vm_args) or
            self.run_for(args, "dsl", extra_vm_args=dsl_vm_args + ["-Dpython.EnableBytecodeDSLInterpreter=true"])
        )

    def run_for(self, args, interpreter_kind, extra_vm_args=None):
        mx.log(f"Building frozen modules for {interpreter_kind} interpreter.")
        return super().run(args, extra_vm_args=extra_vm_args)


class GraalpythonFrozenProject(GraalpythonProject):
    def getBuildTask(self, args):
        return GraalpythonFrozenModuleBuildTask(args, self)


orig_clean = mx.command_function("clean")
def python_clean(args):
    if '--just-pyc' not in args:
        orig_clean(args)
    if not args:
        count = 0
        for path in os.walk(SUITE.dir):
            for file in glob.iglob(os.path.join(path[0], '*.pyc')):
                count += 1
                os.remove(file)

        if count > 0:
            print('Cleaning', count, "`*.pyc` files...")


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

    dists = ['GRAALPYTHON', 'GRAALPYTHON_RESOURCES', 'GRAALPYTHON_UNIT_TESTS']

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


def invert(msg, blinking=False, file=sys.stderr):
    if getattr(file, "isatty", lambda: False)():
        if blinking:
            extra = "\033[5;7m"
        else:
            extra = "\033[7m"
        return f"{extra}{msg}\033[0m"
    return msg


def run(args, *splat, **kwargs):
    if not mx.get_opts().quiet:
        msg = "Running: "
        env = kwargs.get("env")
        if env:
            extra_env = shlex.join([f"{k}={v}" for k, v in env.items() if os.environ.get(k) != v])
            msg += f'{extra_env} '
        msg += shlex.join(args)
        mx.log(mx.colorize(msg, color="green"))
    return mx.run(args, *splat, **kwargs)


def run_mx(args, *splat, **kwargs):
    env = kwargs.get("env", os.environ)
    extra_env = {k: v for k, v in env.items() if os.environ.get(k) != v}

    # Sigh. mx.run_mx forcibly overrides the environment JAVA_HOME by passing
    # --java-home to the subprocess...
    if jh := extra_env.get("JAVA_HOME"):
        args = [f"--java-home={jh}"] + args

    if "-p" not in args and "--dy" not in args and "--dynamicimports" not in args:
        if dy := mx.get_dynamic_imports():
            args = [
                "--dy",
                ",".join(f"{'/' if subdir else ''}{name}" for name, subdir in dy)
            ] + args

    msg = "Running: "
    if extra_env:
        msg += shlex.join([f"{k}={v}" for k, v in extra_env.items()])
    msg += f" mx {shlex.join(args)}"
    if not mx.get_opts().quiet:
        mx.log(mx.colorize(msg, color="green"))
    return mx.run_mx(args, *splat, **kwargs)


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

    def apply(self, config):
        (vmArgs, mainClass, mainClassArgs) = config
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/com.oracle.truffle.api.impl=ALL-UNNAMED'])  # for TruffleRunner/TCK
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/com.oracle.truffle.polyglot=ALL-UNNAMED'])  # for TruffleRunner/TCK
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.py/*=ALL-UNNAMED'])  # for Python internals
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.py.launcher/*=ALL-UNNAMED'])  # for Python launcher internals
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.python.embedding/*=ALL-UNNAMED'])
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.python.embedding.tools/*=ALL-UNNAMED'])
        if not PythonMxUnittestConfig.useResources:
            vmArgs.append(f'-Dorg.graalvm.language.python.home={mx.distribution("GRAALPYTHON_GRAALVM_SUPPORT").get_output()}')
        if mx._opts.verbose:
            vmArgs.append('-Dcom.oracle.graal.python.test.verbose=true')
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
    print(graalpy_standalone(args.type, enterprise=args.edition == 'ee', build=not args.no_build))

def graalpy_jmh(args):
    """
    JMH benchmarks launcher for manual benchmark execution during development.
    The real benchmark runs are drive by "mx benchmark python-jmh:GRAALPYTHON_BENCH".
    All arguments are forwarded to the JMH launcher entry point. Run with "-help" to
    get more info. Example arguments: "-f 0 -i 5 -wi 5 -w 5 -r 5 initCtx".
    """
    vm_args = mx.get_runtime_jvm_args(['GRAALPYTHON_BENCH'])
    mx.run_java(vm_args + ['org.openjdk.jmh.Main'] + args)


def run_downstream_test(args):
    parser = ArgumentParser(description="Runs important upstream packages tests using their main branch")
    parser.add_argument('project')
    parser.add_argument('--dev', action='store_true', help="Use JVM dev standalone")
    args = parser.parse_args(args)
    if args.dev:
        graalpy = graalpy_standalone('jvm', dev=True)
    else:
        graalpy = graalpy_standalone_native()
    downstream_tests.run_downstream_test(graalpy, args.project)


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
    'nativebuild': [nativebuild, ''],
    'nativeclean': [nativeclean, ''],
    'python-src-import': [mx_graalpython_import.import_python_sources, ''],
    'python-coverage': [python_coverage, ''],
    'punittest': [punittest, ''],
    'graalpytest': [graalpytest, '[-h] [--python PYTHON] [TESTS]'],
    'clean': [python_clean, '[--just-pyc]'],
    'bisect-benchmark': [mx_graalpython_bisect.bisect_benchmark, ''],
    'python-leak-test': [run_leak_launcher, ''],
    'python-nodes-footprint': [node_footprint_analyzer, ''],
    'python-checkcopyrights': [python_checkcopyrights, '[--fix]'],
    'host-inlining-log-extract': [host_inlining_log_extract_method, ''],
    'tox-example': [tox_example, ''],
    'graalpy-jmh': [graalpy_jmh, ''],
    'deploy-local-maven-repo': [deploy_local_maven_repo_wrapper, ''],
    'downstream-test': [run_downstream_test, ''],
    'python-native-pgo': [graalpy_native_pgo_build_and_test, 'Build PGO-instrumented native image, run tests, then build PGO-optimized native image'],
})
