# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import argparse
import json
import os
import shutil
import site
import subprocess
import sys
import tempfile

def system(cmd, msg=""):
    print("+", cmd)
    status = os.system(cmd)
    if status != 0:
        xit(msg, status=status)


def known_packages():
    def PyYAML(**kwargs):
        install_from_pypi("PyYAML==3.13", **kwargs)

    def six(**kwargs):
        install_from_pypi("six==1.12.0", **kwargs)

    def Cython(extra_opts=[], **kwargs):
        install_from_pypi("Cython==0.29.2", extra_opts=(['--no-cython-compile'] + extra_opts), **kwargs)

    def setuptools(**kwargs):
        install_from_pypi("setuptools==41.0.1", **kwargs)

    def pkgconfig(**kwargs):
        install_from_pypi("pkgconfig==1.5.1", **kwargs)

    def wheel(**kwargs):
        install_from_pypi("wheel==0.33.4", **kwargs)

    def protobuf(**kwargs):
        install_from_pypi("protobuf==3.8.0", **kwargs)

    def Keras_preprocessing(**kwargs):
        install_from_pypi("Keras-Preprocessing==1.0.5", **kwargs)

    def gast(**kwargs):
        install_from_pypi("gast==0.2.2", **kwargs)

    def astor(**kwargs):
        install_from_pypi("astor==0.8.0", **kwargs)

    def absl_py(**kwargs):
        install_from_pypi("absl-py==0.7.1", **kwargs)

    def mock(**kwargs):
        install_from_pypi("mock==3.0.5", **kwargs)

    def Markdown(**kwargs):
        install_from_pypi("Markdown==3.1.1", **kwargs)

    def Werkzeug(**kwargs):
        install_from_pypi("Werkzeug==0.15.4", **kwargs)

    # Does not yet work
    # def h5py(**kwargs):
    #     try:
    #         import pkgconfig
    #     except ImportError:
    #         print("Installing required dependency: pkgconfig")
    #         pkgconfig(**kwargs)
    #     install_from_pypi("h5py==2.9.0", **kwargs)

    # Does not yet work
    # def keras_applications(**kwargs):
    #     try:
    #         import h5py
    #     except ImportError:
    #         print("Installing required dependency: h5py")
    #         h5py(**kwargs)
    #     install_from_pypi("Keras-Applications==1.0.6", **kwargs)

    def setuptools_scm(**kwargs):
        install_from_pypi("setuptools_scm==1.15.0rc1", **kwargs)

    def numpy(**kwargs):
        try:
            import setuptools as st
        except ImportError:
            print("Installing required dependency: setuptools")
            setuptools(**kwargs)

        patch = """
diff --git a/setup.py 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/setup.py
+++ b/setup.py
@@ -348,6 +348,8 @@
 metadata = dict(
         name = 'numpy',
         maintainer = "NumPy Developers",
+        zip_safe = False, # Truffle: make sure we're not zipped
+        include_package_data = True,
         maintainer_email = "numpy-discussion@python.org",
         description = DOCLINES[0],
         long_description = "\n".join(DOCLINES[2:]),


diff --git a/numpy/ctypeslib.py 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/numpy/ctypeslib.py
+++ b/numpy/ctypeslib.py
@@ -59,6 +59,6 @@
 from numpy.core.multiarray import _flagdict, flagsobj

 try:
-    import ctypes
+    ctypes = None # Truffle: use the mock ctypes
 except ImportError:
     ctypes = None



diff --git a/numpy/core/include/numpy/ndarraytypes.h 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/numpy/core/include/numpy/ndarraytypes.h
+++ b/numpy/core/include/numpy/ndarraytypes.h
@@ -407,6 +407,6 @@
 typedef int (PyArray_FromStrFunc)(char *s, void *dptr, char **endptr,
                                   struct _PyArray_Descr *);

-typedef int (PyArray_FillFunc)(void *, npy_intp, void *);
+typedef void (PyArray_FillFunc)(void *, npy_intp, void *);

 typedef int (PyArray_SortFunc)(void *, npy_intp, void *);
 typedef int (PyArray_ArgSortFunc)(void *, npy_intp *, npy_intp, void *);


diff --git a/numpy/core/src/multiarray/shape.c b/numpy/core/src/multiarray/shape.c
index 30820737e..d8a350f0d 100644
--- a/numpy/core/src/multiarray/shape.c
+++ b/numpy/core/src/multiarray/shape.c
@@ -94,3 +94,4 @@ PyArray_Resize(PyArrayObject *self, PyArray_Dims *newshape, int refcheck,
                     "Use the np.resize function or refcheck=False");
-            return NULL;
+            PyErr_Clear();
+            refcnt = 1;
 #else
             refcnt = PyArray_REFCOUNT(self);
 #endif /* PYPY_VERSION */


diff --git a/numpy/linalg/setup.py 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/numpy/linalg/setup.py
+++ b/numpy/linalg/setup.py
@@ -29,6 +29,7 @@
     lapack_info = get_info('lapack_opt', 0)  # and {}

     def get_lapack_lite_sources(ext, build_dir):
+        return all_sources
         if not lapack_info:
             print("### Warning:  Using unoptimized lapack ###")
             return all_sources


diff --git a/numpy/core/getlimits.py b/numpy/core/getlimits.py
index e450a66..ed538b4 100644
--- a/numpy/core/getlimits.py
+++ b/numpy/core/getlimits.py
@@ -160,70 +160,70 @@ _float64_ma = MachArLike(_f64,
                          huge=(1.0 - _epsneg_f64) / _tiny_f64 * _f64(4),
                          tiny=_tiny_f64)

-# Known parameters for IEEE 754 128-bit binary float
-_ld = ntypes.longdouble
-_epsneg_f128 = exp2(_ld(-113))
-_tiny_f128 = exp2(_ld(-16382))
-# Ignore runtime error when this is not f128
-with numeric.errstate(all='ignore'):
-    _huge_f128 = (_ld(1) - _epsneg_f128) / _tiny_f128 * _ld(4)
-_float128_ma = MachArLike(_ld,
-                         machep=-112,
-                         negep=-113,
-                         minexp=-16382,
-                         maxexp=16384,
-                         it=112,
-                         iexp=15,
-                         ibeta=2,
-                         irnd=5,
-                         ngrd=0,
-                         eps=exp2(_ld(-112)),
-                         epsneg=_epsneg_f128,
-                         huge=_huge_f128,
-                         tiny=_tiny_f128)
-
-# Known parameters for float80 (Intel 80-bit extended precision)
-_epsneg_f80 = exp2(_ld(-64))
-_tiny_f80 = exp2(_ld(-16382))
-# Ignore runtime error when this is not f80
-with numeric.errstate(all='ignore'):
-    _huge_f80 = (_ld(1) - _epsneg_f80) / _tiny_f80 * _ld(4)
-_float80_ma = MachArLike(_ld,
-                         machep=-63,
-                         negep=-64,
-                         minexp=-16382,
-                         maxexp=16384,
-                         it=63,
-                         iexp=15,
-                         ibeta=2,
-                         irnd=5,
-                         ngrd=0,
-                         eps=exp2(_ld(-63)),
-                         epsneg=_epsneg_f80,
-                         huge=_huge_f80,
-                         tiny=_tiny_f80)
-
-# Guessed / known parameters for double double; see:
-# https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format#Double-double_arithmetic
-# These numbers have the same exponent range as float64, but extended number of
-# digits in the significand.
-_huge_dd = (umath.nextafter(_ld(inf), _ld(0))
-            if hasattr(umath, 'nextafter')  # Missing on some platforms?
-            else _float64_ma.huge)
-_float_dd_ma = MachArLike(_ld,
-                          machep=-105,
-                          negep=-106,
-                          minexp=-1022,
-                          maxexp=1024,
-                          it=105,
-                          iexp=11,
-                          ibeta=2,
-                          irnd=5,
-                          ngrd=0,
-                          eps=exp2(_ld(-105)),
-                          epsneg= exp2(_ld(-106)),
-                          huge=_huge_dd,
-                          tiny=exp2(_ld(-1022)))
+# # Known parameters for IEEE 754 128-bit binary float
+# _ld = ntypes.longdouble
+# _epsneg_f128 = exp2(_ld(-113))
+# _tiny_f128 = exp2(_ld(-16382))
+# # Ignore runtime error when this is not f128
+# with numeric.errstate(all='ignore'):
+#     _huge_f128 = (_ld(1) - _epsneg_f128) / _tiny_f128 * _ld(4)
+# _float128_ma = MachArLike(_ld,
+#                          machep=-112,
+#                          negep=-113,
+#                          minexp=-16382,
+#                          maxexp=16384,
+#                          it=112,
+#                          iexp=15,
+#                          ibeta=2,
+#                          irnd=5,
+#                          ngrd=0,
+#                          eps=exp2(_ld(-112)),
+#                          epsneg=_epsneg_f128,
+#                          huge=_huge_f128,
+#                          tiny=_tiny_f128)
+
+# # Known parameters for float80 (Intel 80-bit extended precision)
+# _epsneg_f80 = exp2(_ld(-64))
+# _tiny_f80 = exp2(_ld(-16382))
+# # Ignore runtime error when this is not f80
+# with numeric.errstate(all='ignore'):
+#     _huge_f80 = (_ld(1) - _epsneg_f80) / _tiny_f80 * _ld(4)
+# _float80_ma = MachArLike(_ld,
+#                          machep=-63,
+#                          negep=-64,
+#                          minexp=-16382,
+#                          maxexp=16384,
+#                          it=63,
+#                          iexp=15,
+#                          ibeta=2,
+#                          irnd=5,
+#                          ngrd=0,
+#                          eps=exp2(_ld(-63)),
+#                          epsneg=_epsneg_f80,
+#                          huge=_huge_f80,
+#                          tiny=_tiny_f80)
+
+# # Guessed / known parameters for double double; see:
+# # https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format#Double-double_arithmetic
+# # These numbers have the same exponent range as float64, but extended number of
+# # digits in the significand.
+# _huge_dd = (umath.nextafter(_ld(inf), _ld(0))
+#             if hasattr(umath, 'nextafter')  # Missing on some platforms?
+#             else _float64_ma.huge)
+# _float_dd_ma = MachArLike(_ld,
+#                           machep=-105,
+#                           negep=-106,
+#                           minexp=-1022,
+#                           maxexp=1024,
+#                           it=105,
+#                           iexp=11,
+#                           ibeta=2,
+#                           irnd=5,
+#                           ngrd=0,
+#                           eps=exp2(_ld(-105)),
+#                           epsneg= exp2(_ld(-106)),
+#                           huge=_huge_dd,
+#                           tiny=exp2(_ld(-1022)))


 # Key to identify the floating point type.  Key is result of
@@ -234,17 +234,17 @@ _KNOWN_TYPES = {
     b'\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf' : _float64_ma,
     b'\\xcd\\xcc\\xcc\\xbd' : _float32_ma,
     b'f\\xae' : _float16_ma,
-    # float80, first 10 bytes containing actual storage
-    b'\\xcd\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xfb\\xbf' : _float80_ma,
-    # double double; low, high order (e.g. PPC 64)
-    b'\\x9a\\x99\\x99\\x99\\x99\\x99Y<\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf' :
-    _float_dd_ma,
-    # double double; high, low order (e.g. PPC 64 le)
-    b'\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf\\x9a\\x99\\x99\\x99\\x99\\x99Y<' :
-    _float_dd_ma,
-    # IEEE 754 128-bit binary float
-    b'\\x9a\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\xfb\\xbf' :
-    _float128_ma,
+    # # float80, first 10 bytes containing actual storage
+    # b'\\xcd\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xfb\\xbf' : _float80_ma,
+    # # double double; low, high order (e.g. PPC 64)
+    # b'\\x9a\\x99\\x99\\x99\\x99\\x99Y<\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf' :
+    # _float_dd_ma,
+    # # double double; high, low order (e.g. PPC 64 le)
+    # b'\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf\\x9a\\x99\\x99\\x99\\x99\\x99Y<' :
+    # _float_dd_ma,
+    # # IEEE 754 128-bit binary float
+    # b'\\x9a\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\xfb\\xbf' :
+    # _float128_ma,
 }


--
2.14.1

"""
        install_from_pypi("numpy==1.14.3", patch=patch, **kwargs)


    def dateutil(**kwargs):
        try:
            import setuptools_scm as st_scm
        except ImportError:
            print("Installing required dependency: setuptools_scm")
            setuptools_scm(**kwargs)
        install_from_pypi("python-dateutil==2.7.5", **kwargs)


    def pytz(**kwargs):
        install_from_pypi("pytz==2018.7", **kwargs)


    def pandas(**kwargs):
        try:
            import numpy as np
        except ImportError:
            print("Installing required dependency: numpy")
            numpy(**kwargs)


        try:
            import pytz as _dummy_pytz
        except ImportError:
            print("Installing required dependency: pytz")
            pytz(**kwargs)

        try:
            import six as _dummy_six
        except ImportError:
            print("Installing required dependency: six")
            six(**kwargs)

        try:
            import dateutil as __dummy_dateutil
        except ImportError:
            print("Installing required dependency: dateutil")
            dateutil(**kwargs)

        # download pandas-0.20.3
        patch = """diff --git a/pandas/_libs/src/period_helper.c b/pandas/_libs/src/period_helper.c
index 19f810e..2f01238 100644
--- a/pandas/_libs/src/period_helper.c
+++ b/pandas/_libs/src/period_helper.c
@@ -1105,7 +1105,7 @@ static int dInfoCalc_SetFromAbsDateTime(struct date_info *dinfo,
     /* Bounds check */
     Py_AssertWithArg(abstime >= 0.0 && abstime <= SECONDS_PER_DAY,
                      PyExc_ValueError,
-                     "abstime out of range (0.0 - 86400.0): %f", abstime);
+                     "abstime out of range (0.0 - 86400.0): %f", (long long)abstime);

     /* Calculate the date */
     if (dInfoCalc_SetFromAbsDate(dinfo, absdate, calendar)) goto onError;
diff --git a/pandas/_libs/src/period_helper.c b/pandas/_libs/src/period_helper.c
index 2f01238..6c79eb5 100644
--- a/pandas/_libs/src/period_helper.c
+++ b/pandas/_libs/src/period_helper.c
@@ -157,7 +157,7 @@ static int dInfoCalc_SetFromDateAndTime(struct date_info *dinfo, int year,
                 (second < (double)60.0 ||
                  (hour == 23 && minute == 59 && second < (double)61.0)),
             PyExc_ValueError,
-            "second out of range (0.0 - <60.0; <61.0 for 23:59): %f", second);
+            "second out of range (0.0 - <60.0; <61.0 for 23:59): %f", (long long)second);

         dinfo->abstime = (double)(hour * 3600 + minute * 60) + second;

diff --git a/pandas/io/msgpack/_packer.cpp b/pandas/io/msgpack/_packer.cpp
index 8b5b382..7544707 100644
--- a/pandas/io/msgpack/_packer.cpp
+++ b/pandas/io/msgpack/_packer.cpp
@@ -477,10 +477,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
     (sizeof(type) == sizeof(Py_ssize_t) &&\\
           (is_signed || likely(v < (type)PY_SSIZE_T_MAX ||\\
                                v == (type)PY_SSIZE_T_MAX)))  )
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)
diff --git a/pandas/io/msgpack/_unpacker.cpp b/pandas/io/msgpack/_unpacker.cpp
index fa08f53..49f3bf3 100644
--- a/pandas/io/msgpack/_unpacker.cpp
+++ b/pandas/io/msgpack/_unpacker.cpp
@@ -477,10 +477,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
     (sizeof(type) == sizeof(Py_ssize_t) &&\\
           (is_signed || likely(v < (type)PY_SSIZE_T_MAX ||\\
                                v == (type)PY_SSIZE_T_MAX)))  )
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)

"""
        cflags = "-allowcpp" if sys.implementation.name == "graalpython" else ""
        install_from_pypi("pandas==0.20.3", patch=patch, add_cflags=cflags, **kwargs)

    return locals()


KNOWN_PACKAGES = known_packages()


def xit(msg, status=-1):
    print(msg)
    exit(-1)


def _install_from_url(url, patch=None, extra_opts=[], add_cflags="", ignore_errors=False):
    name = url[url.rfind("/")+1:]
    tempdir = tempfile.mkdtemp()

    # honor env var 'CFLAGS' and 'CPPFLAGS'
    cppflags = os.environ.get("CPPFLAGS", "")
    cflags = "-v " + os.environ.get("CFLAGS", "") + ((" " + add_cflags) if add_cflags else "")

    if os.system("curl -o %s/%s %s" % (tempdir, name, url)) != 0:
        # honor env var 'HTTP_PROXY' and 'HTTPS_PROXY'
        env = os.environ
        curl_opts = []
        if url.startswith("http://") and "HTTP_PROXY" in env:
            curl_opts += ["--proxy", env["HTTP_PROXY"]]
        elif url.startswith("https://") and "HTTPS_PROXY" in env:
            curl_opts += ["--proxy", env["HTTPS_PROXY"]]
        system("curl %s -o %s/%s %s" % (" ".join(curl_opts), tempdir, name, url), msg="Download error")

    if name.endswith(".tar.gz"):
        system("tar xzf %s/%s -C %s" % (tempdir, name, tempdir), msg="Error extracting tar.gz")
        bare_name = name[:-len(".tar.gz")]
    elif name.endswith(".tar.bz2"):
        system("tar xjf %s/%s -C %s" % (tempdir, name, tempdir), msg="Error extracting tar.bz2")
        bare_name = name[:-len(".tar.bz2")]
    elif name.endswith(".zip"):
        system("unzip -u %s/%s -d %s" % (tempdir, name, tempdir), msg="Error extracting zip")
        bare_name = name[:-len(".zip")]
    else:
        xit("Unknown file type: %s" % name)

    if patch:
        with open("%s/%s.patch" % (tempdir, bare_name), "w") as f:
            f.write(patch)
        system("patch -d %s/%s/ -p1 < %s/%s.patch" % ((tempdir, bare_name)*2))

    if "--prefix" not in extra_opts and site.ENABLE_USER_SITE:
        user_arg = "--user"
    else:
        user_arg = ""
    status = system("cd %s/%s; %s %s %s setup.py install %s %s" % (tempdir, bare_name, 'CFLAGS="%s"' % cflags if cflags else "", 'CPPFLAGS="%s"' % cppflags if cppflags else "", sys.executable, user_arg, " ".join(extra_opts)))
    if status != 0 and not ignore_errors:
        xit("An error occurred trying to run `setup.py install %s %s'" % (user_arg, " ".join(extra_opts)))


def install_from_pypi(package, patch=None, extra_opts=[], add_cflags="", ignore_errors=True):
    package_pattern = os.environ.get("GINSTALL_PACKAGE_PATTERN", "https://pypi.org/pypi/%s/json")
    package_version_pattern = os.environ.get("GINSTALL_PACKAGE_VERSION_PATTERN", "https://pypi.org/pypi/%s/%s/json")

    if "==" in package:
        package, _, version = package.rpartition("==")
        url = package_version_pattern % (package, version)
    else:
        url = package_pattern % package

    if any(url.endswith(ending) for ending in [".zip", ".tar.bz2", ".tar.gz"]):
        # this is already the url to the actual package
        pass
    else:
        r = subprocess.check_output("curl %s" % url, shell=True).decode("utf8")
        try:
            urls = json.loads(r)["urls"]
        except:
            pass
        else:
            for url_info in urls:
                if url_info["python_version"] == "source":
                    url = url_info["url"]
                    break

    if url:
        _install_from_url(url, patch=patch, extra_opts=extra_opts, add_cflags=add_cflags, ignore_errors=ignore_errors)
    else:
        xit("Package not found: '%s'" % package)


def main(argv):
    parser = argparse.ArgumentParser(description="The simple Python package installer for GraalVM")

    subparsers = parser.add_subparsers(title="Commands", dest="command", metavar="Use COMMAND --help for further help.")

    subparsers.add_parser(
        "list",
        help="list locally installed packages"
    )

    install_parser = subparsers.add_parser(
        "install",
        help="install a known package",
        description="Install a known package. Known packages are " + ", ".join(KNOWN_PACKAGES.keys())
    )
    install_parser.add_argument(
        "package",
        help="comma-separated list"
    )
    install_parser.add_argument(
        "--prefix",
        help="user-site path prefix"
    )

    subparsers.add_parser(
        "uninstall",
        help="remove installation folder of a local package",
    ).add_argument(
        "package",
        help="comma-separated list"
    )

    subparsers.add_parser(
        "pypi",
        help="attempt to install a package from PyPI (untested, likely won't work, and it won't install dependencies for you)",
        description="Attempt to install a package from PyPI"
    ).add_argument(
        "package",
        help="comma-separated list, can use `==` at the end of a package name to specify an exact version"
    )

    args = parser.parse_args(argv)

    if args.command == "list":
        if site.ENABLE_USER_SITE:
            user_site = site.getusersitepackages()
        else:
            for s in site.getsitepackages():
                if s.endswith("site-packages"):
                    user_site = s
                    break
        print("Installed packages:")
        for p in sys.path:
            if p.startswith(user_site):
                print(p[len(user_site) + 1:])
    elif args.command == "uninstall":
        print("WARNING: I will only delete the package folder, proper uninstallation is not supported at this time.")
        user_site = site.getusersitepackages()
        for pkg in args.package.split(","):
            deleted = False
            for p in sys.path:
                if p.startswith(user_site):
                    # +1 due to the path separator
                    pkg_name = p[len(user_site)+1:]
                    if pkg_name.startswith(pkg):
                        if os.path.isdir(p):
                            shutil.rmtree(p)
                        else:
                            os.unlink(p)
                        deleted = True
                        break
            if deleted:
                print("Deleted %s" % p)
            else:
                xit("Unknown package: '%s'" % pkg)
    elif args.command == "install":
        for pkg in args.package.split(","):
            if pkg not in KNOWN_PACKAGES:
                xit("Unknown package: '%s'" % pkg)
            else:
                if args.prefix:
                    KNOWN_PACKAGES[pkg](extra_opts=["--prefix", args.prefix])
                else:
                    KNOWN_PACKAGES[pkg]()
    elif args.command == "pypi":
        for pkg in args.package.split(","):
            install_from_pypi(pkg, ignore_errors=False)



if __name__ == "__main__":
    main(sys.argv[1:])
