# Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import glob
import logging
import os
import shutil
import sys
from distutils.core import setup as distutils_setup, Extension
from sysconfig import get_config_var, get_config_vars

__dir__ = os.path.dirname(__file__)
cflags_warnings = [ "-Wno-int-to-pointer-cast"
                  , "-Wno-int-conversion"
                  , "-Wno-void-pointer-to-int-cast"
                  , "-Wno-incompatible-pointer-types-discards-qualifiers"
                  , "-Wno-pointer-type-mismatch"
                  , "-Wno-braced-scalar-init"
                  , "-Wno-deprecated-declarations"
                  ]
libpython_name = "libpython"
libhpy_name = "libhpy"
libposix_name = "libposix"

MACOS = sys.platform == "darwin"
WIN32 = sys.platform == "win32"
verbosity = '--verbose' if sys.flags.verbose else '--quiet'
darwin_native = MACOS and __graalpython__.platform_id == "native"
win32_native = WIN32 and __graalpython__.platform_id == "native"
relative_rpath = "@loader_path" if darwin_native else r"$ORIGIN"
so_ext = get_config_var("EXT_SUFFIX")
SOABI = get_config_var("SOABI") or so_ext.split(".")[1]
is_managed = 'managed' in SOABI
lib_ext = 'dylib' if MACOS else ('pyd' if WIN32 else 'so')

# configure logger
logger = logging.getLogger(__name__)
logging.basicConfig(format='%(message)s', level=logging.DEBUG if sys.flags.verbose else logging.ERROR)


def setup(*args, **kwargs):
    # wrap the distutil setup. since we're running in the same process, running
    # a full clean will fail the next build, since distutils thinks it already
    # created the "build" directory
    shutil.rmtree("build", ignore_errors=True)
    os.makedirs("build", exist_ok=False)
    return distutils_setup(*args, **kwargs)


threaded = get_config_var("WITH_THREAD")
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

        n_objects = len(objects)
        if n_objects > 1:
            logger.debug("Compiling {} objects in parallel.".format(n_objects))
            pool = SimpleThreadPool(min(n_objects, os.cpu_count()))
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
            self.package_name = package_spec
            self.version = None
        self.src_var = src_var

    def download(self):
        import tempfile
        tempdir = tempfile.mktemp()
        src_archive = os.environ.get(self.src_var, None)
        if src_archive:
            shutil.copytree(src_archive, tempdir)
        else:
            xit("FATAL: Please set the environment variable %s to the location of the source archive of %s" % (self.src_var, self.lib_name))
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
        makefile_path = os.path.join(lib_src_folder, self.makefile)
        if darwin_native:
            with open(makefile_path, "r") as f:
                content = f.read()
                content = content.replace("-Wl,-soname -Wl,%s" % self.install_name, "-Wl,-install_name -Wl,@rpath/%s" % self.install_name)
            with open(makefile_path, "w") as f:
                f.write(content)

        with open(makefile_path, "r") as f:
            content = f.read()
        with open(makefile_path, "w") as f:
            f.write(content.replace("CFLAGS=", "CFLAGS:=${CFLAGS} ").replace("$(CC) -shared", "$(CC) -shared $(CFLAGS)"))

        parallel_arg =  "-j" + str(min(4, os.cpu_count())) if threaded else ""
        system("make -C '%s' %s -f '%s' CC='%s'" % (lib_src_folder, parallel_arg, self.makefile, get_config_var("CC")), msg="Could not build libbz2")
        return lib_src_folder

    def install(self, build_dir=None):
        lib_path = os.path.join(self.lib_install_dir, "lib%s.so" % self.lib_name)
        if os.path.exists(lib_path):
            # library has been built earlier, so just return the install directory.
            return self.lib_install_dir
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
        os.symlink(self.install_name, lib_path)

        return self.lib_install_dir


class LZMADepedency(CAPIDependency):

    def build(self, extracted_dir=None):
        if not extracted_dir:
            extracted_dir = self.download()

        xz_src_path = os.path.join(extracted_dir, self.package_name + "-" + self.version)
        lzma_support_path = os.path.join(__dir__, 'lzma')
        # not using parallel build for xz
        make_args = ['make', '-C', lzma_support_path]
        make_args += ["CC='%s'" % get_config_var("CC")]
        make_args += ["XZ_ROOT='%s'" % xz_src_path]
        make_args += ["CONFIG_H_DIR='%s'" % lzma_support_path]
        make_args += ["LIB_DIR='%s'" % self.lib_install_dir]
        make_args += ["INC_DIR='%s'" % self.include_install_dir]
        system(' '.join(make_args), msg="Could not build liblzma")
        return self.lib_install_dir

    def install(self, build_dir=None):
        lib_path = os.path.join(self.lib_install_dir, "lib%s.%s" % (self.lib_name, lib_ext))
        if os.path.exists(lib_path):
            # library has been built earlier, so just return the install directory.
            return self.lib_install_dir

        return self.build()


class ExpatDependency(CAPIDependency):

    def build(self, extracted_dir=None):
        if not extracted_dir:
            extracted_dir = self.download()
        src_path = os.path.join(extracted_dir, self.package_name + "-" + self.version)

        cmake_args = [
            "cmake",
            "--log-level=ERROR",
            f"-DCMAKE_C_COMPILER='{get_config_var('CC')}'",
            "-DEXPAT_BUILD_TOOLS=OFF",
            "-DEXPAT_BUILD_EXAMPLES=OFF",
            "-DEXPAT_BUILD_TESTS=OFF",
            f"-S '{src_path}'",
            f"-B '{src_path}'",
        ]
        system(' '.join(cmake_args), msg="Could not configure expat")
        system(f"make -C '{src_path}'", msg="Could not build expat")
        # Install manually to avoid pulling in unnecessary files
        for f in glob.glob(f"{src_path}/*.so*"):
            shutil.copy(f, self.lib_install_dir, follow_symlinks=False)
        for f in [f"{src_path}/lib/expat.h", f"{src_path}/lib/expat_external.h"]:
            shutil.copy(f, self.include_install_dir)
        return self.lib_install_dir

    def install(self, build_dir=None):
        lib_path = os.path.join(self.lib_install_dir, "lib%s.%s" % (self.lib_name, lib_ext))
        if os.path.exists(lib_path):
            # library has been built earlier, so just return the install directory.
            return self.lib_install_dir

        return self.build()


def _build_deps(deps):
    libs = []
    library_dirs = []
    include_dirs = []
    for dep in deps:
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
    return libs, library_dirs, include_dirs


class NativeBuiltinModule:
    def __init__(self, name, deps=(), **kwargs):
        self.name = name
        self.deps = deps
        self.kwargs = kwargs

    def __call__(self):
        kwargs = dict(self.kwargs)
        # common case: just a single file which is the module's name plus the file extension
        sources = kwargs.pop("sources", [os.path.join("modules", self.name + ".c")])

        core = kwargs.pop("core", True)
        core_module = kwargs.pop("core_module", True)

        libs, library_dirs, include_dirs = _build_deps(self.deps)

        libs += kwargs.pop("libs", [])
        library_dirs += kwargs.pop("library_dirs", [])
        include_dirs += kwargs.pop("include_dirs", [])
        include_dirs += [os.path.join(__dir__, "src"), os.path.join(__dir__, "include")]
        if core:
            include_dirs += [os.path.join(__dir__, "include/internal")]
        extra_compile_args = cflags_warnings + kwargs.pop("extra_compile_args", [])

        define_macros = kwargs.pop("define_macros", [])
        if core:
            define_macros.append(("Py_BUILD_CORE", None))
        if core_module:
            define_macros.append(("Py_BUILD_CORE_MODULE", None))

        return Extension(
            self.name,
            sources=[os.path.join(__dir__, f) for f in sources if f.endswith('.c')],
            libraries=libs,
            library_dirs=library_dirs,
            extra_compile_args=extra_compile_args,
            include_dirs=include_dirs,
            define_macros=define_macros,
            **kwargs,
        )


builtin_exts = (
    # the modules included in this list are supported on windows, too
    NativeBuiltinModule("_cpython_sre"),
    NativeBuiltinModule("_cpython_unicodedata"),
    NativeBuiltinModule("_mmap"),
    NativeBuiltinModule("_cpython_struct"),
) + (() if WIN32 else (
    NativeBuiltinModule("_testcapi", core=False),
    NativeBuiltinModule("_testmultiphase"),
    NativeBuiltinModule("_ctypes_test"),
    # the above modules are more core, we need them first to deal with later, more complex modules with dependencies
    NativeBuiltinModule(
        "_bz2",
        deps=[Bzip2Depedency("bz2", "bzip2==1.0.8", "BZIP2")],
        extra_link_args=["-Wl,-rpath,%s/../lib/%s/" % (relative_rpath, SOABI)],
    ),
    NativeBuiltinModule(
        'pyexpat',
        define_macros=[
            ('HAVE_EXPAT_CONFIG_H', '1'),
            # bpo-30947: Python uses best available entropy sources to
            # call XML_SetHashSalt(), expat entropy sources are not needed
            ('XML_POOR_ENTROPY', '1'),
        ],
        include_dirs=[os.path.join(__dir__, 'expat')],
        sources=['modules/pyexpat.c', 'expat/xmlparse.c', 'expat/xmlrole.c', 'expat/xmltok.c'],
        depends=[
            'expat/ascii.h', 'expat/asciitab.h', 'expat/expat.h', 'expat/expat_config.h', 'expat/expat_external.h',
            'expat/internal.h', 'expat/latin1tab.h', 'expat/utf8tab.h', 'expat/xmlrole.h', 'expat/xmltok.h',
            'expat/xmltok_impl.h',
        ],
    ),
))


def build_libpython(capi_home):
    src_dir = os.path.join(__dir__, "src")
    files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]
    module = NativeBuiltinModule(
        libpython_name,
        sources=files,
        extra_compile_args=cflags_warnings,
        core_module=False,
    )()
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % capi_home, "clean", "--all"]
    if WIN32:
        # need to link with sulongs libs instead of libpython for this one
        llvm_libs = " ".join([
            f" -L{lpath}" for lpath in __graalpython__.get_toolchain_paths("LD_LIBRARY_PATH")
        ]) + f" -lgraalvm-llvm -lsulong-native"
        get_config_vars()["LDSHARED"] = get_config_vars()["LDSHARED_LINUX"] + llvm_libs
    try:
        setup(
            script_name='setup' + libpython_name,
            script_args=args,
            name=libpython_name,
            version='1.0',
            description="Graal Python's C API",
            ext_modules=[module],
        )
    finally:
        if WIN32:
            # reset LDSHARED, but keep linking with sulong libs so we can use internal
            # APIs in shipped extensions
            get_config_vars()["LDSHARED"] = get_config_vars()["LDSHARED_WINDOWS"] + llvm_libs


def build_libhpy(capi_home):
    src_dir = os.path.join(__dir__, "hpy")
    files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]
    module = Extension(libhpy_name,
                       sources=files,
                       define_macros=[("HPY_UNIVERSAL_ABI", 1)],
                       extra_compile_args=cflags_warnings,
    )
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % capi_home, "clean", "--all"]
    setup(
        script_name='setup' + libhpy_name,
        script_args=args,
        name=libhpy_name,
        version='1.0',
        description="Graal Python's HPy C API",
        ext_modules=[module],
    )

def build_nativelibsupport(capi_home, subdir, libname, deps=[], **kwargs):
    if not is_managed:
        src_dir = os.path.join(__dir__, subdir)
        files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]

        libs, library_dirs, include_dirs = _build_deps(deps)
        libs += kwargs.get("libs", [])
        library_dirs += kwargs.get("library_dirs", [])
        runtime_library_dirs = kwargs.get("runtime_library_dirs", [])
        include_dirs += kwargs.get("include_dirs", [])
        extra_link_args = kwargs.get("extra_link_args", [])
        module = Extension(libname,
                        sources=files,
                        libraries=libs,
                        library_dirs=library_dirs,
                        extra_compile_args=cflags_warnings + kwargs.get("cflags_warnings", []),
                        runtime_library_dirs=runtime_library_dirs,
                        include_dirs=include_dirs,
                        extra_link_args=extra_link_args,

        )
        args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % capi_home, "clean", "--all"]
        setup(
            script_name='setup' + libname,
            script_args=args,
            name=libname,
            version='1.0',
            description="Graal Python's native %s support" % subdir,
            ext_modules=[module],
        )

def build_libposix(capi_home):
    src_dir = os.path.join(__dir__, "posix")
    files = [os.path.abspath(os.path.join(src_dir, f)) for f in os.listdir(src_dir) if f.endswith(".c")]
    no_gnu_source = get_config_var("USE_GNU_SOURCE")
    if no_gnu_source:
        get_config_vars()["CFLAGS"] = get_config_var("CFLAGS_DEFAULT")
    module = Extension(libposix_name,
                       sources=files,
                       libraries=['crypt'] if not darwin_native else [],
                       extra_compile_args=cflags_warnings + ['-Wall', '-Werror'])
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s' % capi_home, "clean", "--all"]
    setup(
        script_name='setup' + libposix_name,
        script_args=args,
        name=libposix_name,
        version='1.0',
        description="Graal Python's Native support for the POSIX library",
        ext_modules=[module],
    )
    if no_gnu_source:
        get_config_vars()["CFLAGS"] = get_config_var("CFLAGS_DEFAULT") + " " + get_config_var("USE_GNU_SOURCE")


def build_builtin_exts(capi_home):
    args = [verbosity, 'build', 'install_lib', '-f', '--install-dir=%s/modules' % capi_home, "clean", "--all"]
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
    if WIN32:
        # avoid long paths on win32
        original_object_filenames = distutils.ccompiler.CCompiler.object_filenames
        def stripped_object_filenames(*args, **kwargs):
            kwargs.update(dict(strip_dir=1))
            return original_object_filenames(*args, **kwargs)
        distutils.ccompiler.CCompiler.object_filenames = stripped_object_filenames

    try:
        build_libpython(capi_home)
        build_builtin_exts(capi_home)
        build_libhpy(capi_home)
        if WIN32:
            return # TODO: ...
        build_libposix(capi_home)
        build_nativelibsupport(capi_home,
                                subdir="bz2",
                                libname="libbz2support",
                                deps=[Bzip2Depedency("bz2", "bzip2==1.0.8", "BZIP2")],
                                extra_link_args=["-Wl,-rpath,%s/lib/%s/" % (relative_rpath, SOABI)])
        build_nativelibsupport(capi_home,
                                subdir="lzma",
                                libname="liblzmasupport",
                                deps=[LZMADepedency("lzma", "xz==5.2.6", "XZ-5.2.6")],
                                extra_link_args=["-Wl,-rpath,%s/lib/%s/" % (relative_rpath, SOABI)])
    finally:
        if threaded:
            distutils.ccompiler.CCompiler.compile = original_compile
            distutils.command.build_ext.build_ext.build_extensions = original_build_extensions
        if WIN32:
            distutils.ccompiler.CCompiler.object_filenames = original_object_filenames


if __name__ == "__main__":
    build(sys.argv[1])
