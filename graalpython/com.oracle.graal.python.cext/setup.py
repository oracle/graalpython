# Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.


import sys
import os
import shutil
import logging
from distutils.core import setup, Extension
from distutils.sysconfig import get_config_var, get_config_vars
import _sysconfig

__dir__ = __file__.rpartition("/")[0]
cflags_warnings = [ "-Wno-int-to-pointer-cast"
                  , "-Wno-int-conversion"
                  , "-Wno-incompatible-pointer-types-discards-qualifiers"
                  , "-Wno-pointer-type-mismatch"
                  , "-Wno-braced-scalar-init"
                  , "-Wno-deprecated-declarations"
                  ]
libpython_name = "libpython"
libhpy_name = "libhpy"

verbosity = '--verbose' if sys.flags.verbose else '--quiet'
darwin_native = sys.platform == "darwin" and __graalpython__.platform_id == "native"
relative_rpath = "@loader_path" if darwin_native else r"\$ORIGIN"
so_ext = get_config_var("EXT_SUFFIX")
SOABI = get_config_var("SOABI")

# configure logger
logger = logging.getLogger(__name__)
logging.basicConfig(format='%(message)s', level=logging.DEBUG if sys.flags.verbose else logging.ERROR)


threaded = _sysconfig.get_config_var("WITH_THREAD")
if threaded:
    logger.debug("building C API threaded")
    import threading
    import queue

    class SimpleThreadPool:

        def __init__(self, n=None):
            self.n = n if n else os.cpu_count()
            self.running = False
            self.started = False
            self.finished_semaphore = None
            self.task_queue = queue.SimpleQueue()
            self.result_queue = queue.SimpleQueue()

        def worker_fun(self, id, fun):
            while self.running:
                try:
                    item = self.task_queue.get()
                    if item is not None:
                        result = fun(item)
                        self.result_queue.put((id, True, item, result))
                    else:
                        break
                except BaseException as e:
                    self.result_queue.put((id, False, item, e))
            self.finished_semaphore.release()

        def start_thread_pool(self, fun):
            if self.running:
                raise RuntimeException("pool already running")

            logger.debug("Starting thread pool with {} worker threads".format(self.n))
            self.running = True
            self.workers = [None] * self.n
            self.finished_semaphore = threading.Semaphore(0)
            for i in range(self.n):
                worker = threading.Thread(target=self.worker_fun, args=(i, fun))
                worker.daemon = True
                worker.start()
                self.workers[i] = worker

        def stop_thread_pool(self):
            self.running = False
            # drain queue; remove non-None items
            try:
                self.task_queue.get_nowait()
            except queue.Empty:
                pass

            # wake up threads by putting None items into the task queue
            for i in range(self.n):
                self.task_queue.put(None)

            for worker in self.workers:
                worker.join()

        def put_job(self, items):
            for item in items:
                self.task_queue.put(item)
            for i in range(self.n):
                self.task_queue.put(None)

        def wait_until_finished(self):
            for i in range(self.n):
                self.finished_semaphore.acquire(True, None)

            results = []
            try:
                while not self.result_queue.empty():
                    id, success, item, result = self.result_queue.get_nowait()
                    if not success:
                        raise result
                    else:
                        results.append(result)
            except queue.Empty:
                # just to be sure
                pass
            return results


    def pcompiler(self, sources, output_dir=None, macros=None, include_dirs=None, debug=0, extra_preargs=None, extra_postargs=None, depends=None):
        # taken from distutils.ccompiler.CCompiler
        macros, objects, extra_postargs, pp_opts, build = self._setup_compile(output_dir, macros, include_dirs, sources, depends, extra_postargs)
        cc_args = self._get_cc_args(pp_opts, debug, extra_preargs)

        def _single_compile(obj):
            try:
                src, ext = build[obj]
            except KeyError:
                return
            logger.debug("Compiling {!s}".format(src))
            self._compile(obj, src, ext, cc_args, extra_postargs, pp_opts)

        if len(objects) > 1:
            logger.debug("Compiling {} objects in parallel.".format(len(objects)))
            pool = SimpleThreadPool()
            pool.start_thread_pool(_single_compile)
            pool.put_job(objects)
            pool.wait_until_finished()
            pool.stop_thread_pool()
        else:
            logger.debug("Compiling 1 object without thread pool.")
            _single_compile(objects[0])
        return objects


    def build_extensions(self):
        self.check_extensions_list(self.extensions)
        if len(self.extensions) > 1:
            pool = SimpleThreadPool()
            pool.start_thread_pool(self.build_extension)
            pool.put_job(self.extensions)
            pool.wait_until_finished()
            pool.stop_thread_pool()
        else:
            return self.build_extension(self.extensions[0])


def system(cmd, msg=""):
    logger.debug("Running command: " + cmd)
    status = os.system(cmd)
    if status != 0:
        xit(msg, status=status)


def xit(msg, status=-1):
    print(msg)
    sys.exit(-1)


class CAPIDependency:
    def __init__(self, lib_name, package_spec, src_var):
        self.lib_name = lib_name
        if "==" in package_spec:
            self.package_name, _, self.version = package_spec.rpartition("==")
        else:
            self.package_name = self.package_spec
            self.version = None
        self.src_var = src_var

    def download(self):
        import tempfile
        tempdir = tempfile.mktemp()
        src_archive = os.environ.get(self.src_var, None)
        if src_archive:
            shutil.copytree(src_archive, tempdir)
        else:
            xit("FATAL: Please set the environment variable %s to the location of the source archive of %s" % (src_archive_var, lib_name))
        return tempdir

    def conftest(self):
        return False

    @staticmethod
    def _ensure_dir(dir):
        if not (os.path.isdir(dir) and os.path.exists(dir)):
            os.makedirs(dir)

    @classmethod
    def set_lib_install_base(cls, value):
        cls._lib_install_dir = os.path.join(value, "lib", SOABI)
        cls._include_install_dir = os.path.join(value, "include")

    @property
    def lib_install_dir(self):
        CAPIDependency._ensure_dir(self._lib_install_dir)
        return self._lib_install_dir

    @property
    def include_install_dir(self):
        CAPIDependency._ensure_dir(self._include_install_dir)
        return self._include_install_dir


class Bzip2Depedency(CAPIDependency):
    makefile = "Makefile-libbz2_so"
    header_name = "bzlib.h"
    install_name = "libbz2.so.1.0"

    def build(self, extracted_dir=None):
        if not extracted_dir:
            extracted_dir = self.download()
        lib_src_folder = os.path.join(extracted_dir, self.package_name + "-" + self.version)
        logger.info("Building dependency %s in %s using Makefile %s" % (self.package_name, lib_src_folder, self.makefile))

        # On Darwin, we need to use -install_name for the native linker
        if darwin_native:
            makefile_path = os.path.join(lib_src_folder, self.makefile)
            with open(makefile_path, "r") as f:
                content = f.read()
                content = content.replace("-Wl,-soname -Wl,%s" % self.install_name, "-Wl,-install_name -Wl,@rpath/%s" % self.install_name)
            with open(makefile_path, "w") as f:
                f.write(content)

        parallel_arg =  "-j" + str(os.cpu_count()) if threaded else ""
        system("make -C '%s' %s -f '%s' CC='%s'" % (lib_src_folder, parallel_arg, self.makefile, get_config_var("CC")), msg="Could not build libbz2")
        return lib_src_folder

    def install(self, build_dir=None):
        if not build_dir:
            build_dir = self.build()

        # bzip2's Makefile will name the output file 'libb2.so.<version>'
        lib_filename = "libbz2.so." + self.version
        #lib_filename = "bzip2-shared"

        # The destination file name will be 'libbz2.so'. The Makefile also creates a symbolic link called
        # 'libbz2.so.1.0' which we could use but to be more platform-independent, we just rename the file.
        dest_lib_filename = os.path.join(self.lib_install_dir, self.install_name)

        logger.info("Installing dependency %s to %s" % (self.package_name, dest_lib_filename))
        shutil.copy(os.path.join(build_dir, lib_filename), dest_lib_filename)

        # also install the include file 'bzlib.h'
        dest_include_filename = os.path.join(self.include_install_dir, self.header_name)

        logger.info("Installing header file %s to %s" % (self.header_name, dest_include_filename))
        shutil.copy(os.path.join(build_dir, self.header_name), dest_include_filename)

        # create symlink 'libbz2.so' for linking
        os.symlink(self.install_name, os.path.join(self.lib_install_dir, "lib%s.so" % self.lib_name))

        return self.lib_install_dir


class NativeBuiltinModule:
    def __init__(self, name, subdir="modules", files=None, deps=[], **kwargs):
        self.name = name
        self.subdir = subdir
        self.deps = deps
        # common case: just a single file which is the module's name plus the file extension
        if not files:
            self.files = [name + ".c"]
        self.kwargs = kwargs

    def __call__(self):
        kwargs = self.kwargs
        libs = []
        library_dirs = []
        include_dirs = []
        runtime_library_dirs = []
        extra_link_args = []
        for dep in self.deps:
            if not dep.conftest():
                # this will download, build and install the library
                install_dir = dep.install()
                assert install_dir == dep.lib_install_dir
            else:
                logger.info("conftest for %s passed; not installing", dep.package_name)

            # Whether or not the dependency is already available, we need to link against it.
            if dep.lib_install_dir:
                libs.append(dep.lib_name)
                library_dirs.append(dep.lib_install_dir)

            # If the dependency provides a header file, add the include path
            if dep.include_install_dir:
                include_dirs.append(dep.include_install_dir)

        libs += kwargs.get("libs", [])
        library_dirs += kwargs.get("library_dirs", [])
        runtime_library_dirs += kwargs.get("runtime_library_dirs", [])
        include_dirs += kwargs.get("include_dirs", [])
        extra_link_args += kwargs.get("extra_link_args", [])

        return Extension(self.name,
                         sources=[os.path.join(__dir__, self.subdir, f) for f in self.files],
                         libraries=libs,
                         library_dirs=library_dirs,
                         extra_compile_args=cflags_warnings + kwargs.get("cflags_warnings", []),
                         runtime_library_dirs=runtime_library_dirs,
                         include_dirs=include_dirs,
                         extra_link_args=extra_link_args,
                         )


builtin_exts = (
    NativeBuiltinModule("_cpython_sre"),
    NativeBuiltinModule("_cpython_unicodedata"),
    NativeBuiltinModule("_memoryview"),
    NativeBuiltinModule("_mmap"),
    NativeBuiltinModule("_cpython_struct"),
    NativeBuiltinModule("_testcapi"),
    NativeBuiltinModule("_testmultiphase"),
    # the above modules are more core, we need them first to deal with later, more complex modules with dependencies
    NativeBuiltinModule("_bz2", deps=[Bzip2Depedency("bz2", "bzip2==1.0.8", "BZIP2")], extra_link_args=["-Wl,-rpath,%s/../lib/%s/" % (relative_rpath, SOABI)]),
)


def build_libpython(capi_home):
    src_dir = os.path.join(__dir__, "src")
    files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]
    module = Extension(libpython_name,
                       sources=files,
                       extra_compile_args=cflags_warnings,
                       )
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % capi_home, "clean"]
    setup(
        script_name='setup' + libpython_name,
        script_args=args,
        name=libpython_name,
        version='1.0',
        description="Graal Python's C API",
        ext_modules=[module],
    )


def build_libhpy(capi_home):
    src_dir = os.path.join(__dir__, "hpy")
    files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]
    module = Extension(libhpy_name,
                       sources=files,
                       define_macros=[("HPY_UNIVERSAL_ABI", None)],
                       extra_compile_args=cflags_warnings,
    )
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % capi_home, "clean"]
    setup(
        script_name='setup' + libhpy_name,
        script_args=args,
        name=libhpy_name,
        version='1.0',
        description="Graal Python's HPy C API",
        ext_modules=[module],
    )


def build_builtin_exts(capi_home):
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s/modules' % capi_home, "clean"]
    distutil_exts = [(ext, ext()) for ext in builtin_exts]
    def build_builtin_ext(item):
        ext, distutil_ext = item
        setup(
            script_name='setup_' + ext.name,
            script_args=args,
            name=ext.name,
            version='1.0',
            description="Graal Python builtin native module '%s'" % ext.name,
            ext_modules=[distutil_ext]
        )
        logger.debug("Successfully built and installed module %s", ext.name)

    for item in distutil_exts:
        build_builtin_ext(item)


def build(capi_home):
    CAPIDependency.set_lib_install_base(capi_home)

    if threaded:
        import distutils.ccompiler
        import distutils.command.build_ext
        original_compile = distutils.ccompiler.CCompiler.compile
        original_build_extensions = distutils.command.build_ext.build_ext.build_extensions
        distutils.ccompiler.CCompiler.compile = pcompiler
        distutils.command.build_ext.build_ext.build_extensions = build_extensions

    try:
        build_libhpy(capi_home)
        build_libpython(capi_home)
        build_builtin_exts(capi_home)
    finally:
        if threaded:
            distutils.ccompiler.CCompiler.compile = original_compile
            distutils.command.build_ext.build_ext.build_extensions = original_build_extensions


if __name__ == "__main__":
    build(sys.argv[1])
