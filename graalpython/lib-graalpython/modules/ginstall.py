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
import tempfile
import importlib
import sys


def get_module_name(package_name):
    non_standard_packages = {
        'pyyaml':'pyaml',
        'protobuf':'google.protobuf',
        'python-dateutil':'dateutil',
        'websocket-client':'websocket',
    }
    module_name = non_standard_packages.get(package_name, package_name)
    return  module_name.replace('-', '_')


def pip_package(name=None):
    def decorator(func):
        def wrapper(*args, **kwargs):
            _name = name if name else func.__name__
            try:
                module_name = get_module_name(_name)
                importlib.import_module(module_name)
                del sys.modules[module_name]
            except ImportError:
                print("Installing required dependency: {}".format(_name))
                func(*args, **kwargs)
        return wrapper
    return decorator


def system(cmd, msg=""):
    print("+", cmd)
    status = os.system(cmd)
    if status != 0:
        xit(msg, status=status)
    return status


def known_packages():
    @pip_package()
    def pytest(**kwargs):
        wcwidth(**kwargs)
        importlib_metadata(**kwargs)
        pluggy(**kwargs)
        atomicwrites(**kwargs)
        more_itertools(**kwargs)
        attrs(**kwargs)
        packaging(**kwargs)
        py(**kwargs)
        patch = """
diff --git a/src/_pytest/_code/code.py b/src/_pytest/_code/code.py
index aa4dcff..029e659 100644
--- a/src/_pytest/_code/code.py
+++ b/src/_pytest/_code/code.py
@@ -211,15 +211,17 @@ class TracebackEntry:
             if key is not None:
                 astnode = astcache.get(key, None)
         start = self.getfirstlinesource()
-        try:
-            astnode, _, end = getstatementrange_ast(
-                self.lineno, source, astnode=astnode
-            )
-        except SyntaxError:
-            end = self.lineno + 1
-        else:
-            if key is not None:
-                astcache[key] = astnode
+        end = -1
+        # GraalPython: no support for the ast module so the source cannot be retrieved correctly 
+        # try:
+        #     astnode, _, end = getstatementrange_ast(
+        #         self.lineno, source, astnode=astnode
+        #     )
+        # except SyntaxError:
+        #     end = self.lineno + 1
+        # else:
+        #     if key is not None:
+        #         astcache[key] = astnode
         return source[start:end]
 
     source = property(getsource)
diff --git a/src/_pytest/python.py b/src/_pytest/python.py
index 66d8530..8bb2ab6 100644
--- a/src/_pytest/python.py
+++ b/src/_pytest/python.py
@@ -494,8 +494,10 @@ class Module(nodes.File, PyCollector):
     def _importtestmodule(self):
         # we assume we are only called once per module
         importmode = self.config.getoption("--import-mode")
+        imported = False
         try:
             mod = self.fspath.pyimport(ensuresyspath=importmode)
+            imported = True
         except SyntaxError:
             raise self.CollectError(
                 _pytest._code.ExceptionInfo.from_current().getrepr(style="short")
@@ -538,6 +540,10 @@ class Module(nodes.File, PyCollector):
                 "or @pytest.mark.skipif decorators instead, and to skip a "
                 "module use `pytestmark = pytest.mark.{skip,skipif}."
             )
+        finally:
+            # this is needed for GraalPython: some modules fail with java level exceptions (the finally block still executes)
+            if not imported:
+                raise self.CollectError("Module could not be imported, the test could be unsupported by GraalPython")
         self.config.pluginmanager.consider_module(mod)
         return mod
 
        
        """
        install_from_pypi("pytest==5.0.1", patch=patch, **kwargs)

    @pip_package()
    def py(**kwargs):
        install_from_pypi("py==1.8.0", **kwargs)

    @pip_package()
    def attrs(**kwargs):
        patch = """
--- a/setup.py
+++ b/setup.py
@@ -96,7 +96,6 @@
     ).group(1)
     + "\\n\\n`Full changelog "
     + "<{url}en/stable/changelog.html>`_.\\n\\n".format(url=URL)
-    + read("AUTHORS.rst")
 )


"""
        install_from_pypi("attrs==19.1.0", patch=patch, **kwargs)

    @pip_package()
    def pyparsing(**kwargs):
        install_from_pypi("pyparsing==2.4.2", **kwargs)

    @pip_package()
    def packaging(**kwargs):
        six(**kwargs)
        pyparsing(**kwargs)
        install_from_pypi("packaging==19.0", **kwargs)

    @pip_package()
    def more_itertools(**kwargs):
        install_from_pypi("more-itertools==7.0.0", **kwargs)

    @pip_package()
    def atomicwrites(**kwargs):
        patch = """
--- a/setup.py
+++ b/setup.py
@@ -12,7 +12,6 @@


 with open('atomicwrites/__init__.py', 'rb') as f:
-    version = str(ast.literal_eval(_version_re.search(
-        f.read().decode('utf-8')).group(1)))
+    version = "1.3.0"

 setup(
     name='atomicwrites',
"""
        install_from_pypi("atomicwrites==1.3.0", patch=patch, **kwargs)

    @pip_package()
    def pluggy(**kwargs):
        zipp(**kwargs)
        install_from_pypi("pluggy==0.12.0", **kwargs)

    @pip_package()
    def zipp(**kwargs):
        setuptools_scm(**kwargs)
        install_from_pypi("zipp==0.5.0", **kwargs)

    @pip_package()
    def importlib_metadata(**kwargs):
        zipp(**kwargs)
        install_from_pypi("importlib-metadata==0.19", **kwargs)

    @pip_package()
    def wcwidth(**kwargs):
        six(**kwargs)
        install_from_pypi("wcwidth==0.1.7", **kwargs)

    @pip_package()
    def PyYAML(**kwargs):
        install_from_pypi("PyYAML==3.13", **kwargs)

    @pip_package()
    def six(**kwargs):
        install_from_pypi("six==1.12.0", **kwargs)

    @pip_package()
    def Cython(**kwargs):
        install_from_pypi("Cython==0.29.2", **kwargs)

    @pip_package()
    def setuptools(**kwargs):
        six(**kwargs)
        install_from_pypi("setuptools==41.0.1", **kwargs)

    @pip_package()
    def pkgconfig(**kwargs):
        install_from_pypi("pkgconfig==1.5.1", **kwargs)

    @pip_package()
    def wheel(**kwargs):
        install_from_pypi("wheel==0.33.4", **kwargs)

    @pip_package()
    def protobuf(**kwargs):
        install_from_pypi("protobuf==3.8.0", **kwargs)

    @pip_package()
    def Keras_preprocessing(**kwargs):
        install_from_pypi("Keras-Preprocessing==1.0.5", **kwargs)

    @pip_package()
    def gast(**kwargs):
        install_from_pypi("gast==0.2.2", **kwargs)

    @pip_package()
    def astor(**kwargs):
        install_from_pypi("astor==0.8.0", **kwargs)

    @pip_package()
    def absl_py(**kwargs):
        install_from_pypi("absl-py==0.7.1", **kwargs)

    @pip_package()
    def mock(**kwargs):
        install_from_pypi("mock==3.0.5", **kwargs)

    @pip_package()
    def Markdown(**kwargs):
        install_from_pypi("Markdown==3.1.1", **kwargs)

    @pip_package()
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
    #     try:
    #         import six
    #     except ImportError:
    #         print("Installing required dependency: six")
    #         pkgconfig(**kwargs)
    #     install_from_pypi("six==1.12.0", **kwargs)
    #
    # def keras_applications(**kwargs):
    #     try:
    #         import h5py
    #     except ImportError:
    #         print("Installing required dependency: h5py")
    #         h5py(**kwargs)
    #     install_from_pypi("Keras-Applications==1.0.6", **kwargs)

    @pip_package()
    def setuptools_scm(**kwargs):
        install_from_pypi("setuptools_scm==1.15.0", **kwargs)

    @pip_package()
    def numpy(**kwargs):
        setuptools(**kwargs)
        patch = r'''
diff --git a/numpy/__init__.py b/numpy/__init__.py
index ba88c73..e4db404 100644
--- a/numpy/__init__.py
+++ b/numpy/__init__.py
@@ -206,7 +206,7 @@ else:
         try:
             x = ones(2, dtype=float32)
             if not abs(x.dot(x) - 2.0) < 1e-5:
-                raise AssertionError()
+                pass
         except AssertionError:
             msg = ("The current Numpy installation ({!r}) fails to "
                    "pass simple sanity checks. This can be caused for example "
diff --git a/numpy/conftest.py b/numpy/conftest.py
index 7834dd3..b3630bf 100644
--- a/numpy/conftest.py
+++ b/numpy/conftest.py
@@ -46,9 +46,12 @@ def check_fpu_mode(request):
     """
     Check FPU precision mode was not changed during the test.
     """
-    old_mode = get_fpu_mode()
+    # sulong - FPU precision mode testig is not yet supported 
+    # old_mode = get_fpu_mode()
+    old_mode = None
     yield
-    new_mode = get_fpu_mode()
+    # new_mode = get_fpu_mode()
+    new_mode = None
 
     if old_mode != new_mode:
         raise AssertionError("FPU precision mode changed from {0:#x} to {1:#x}"
diff --git a/numpy/core/_dtype_ctypes.py b/numpy/core/_dtype_ctypes.py
index 0852b1e..cf4dc6f 100644
--- a/numpy/core/_dtype_ctypes.py
+++ b/numpy/core/_dtype_ctypes.py
@@ -22,7 +22,6 @@ Unfortunately, this fails because:
 * PEP3118 cannot represent unions, but both numpy and ctypes can
 * ctypes cannot handle big-endian structs with PEP3118 (bpo-32780)
 """
-import _ctypes
 import ctypes
 
 import numpy as np
diff --git a/numpy/core/_internal.py b/numpy/core/_internal.py
index 1d3bb55..202c63b 100644
--- a/numpy/core/_internal.py
+++ b/numpy/core/_internal.py
@@ -13,6 +13,7 @@ from numpy.compat import unicode
 from numpy.core.overrides import set_module
 from .multiarray import dtype, array, ndarray
 try:
+    import _ctypes
     import ctypes
 except ImportError:
     ctypes = None
diff --git a/numpy/core/getlimits.py b/numpy/core/getlimits.py
index 544b8b3..799f669 100644
--- a/numpy/core/getlimits.py
+++ b/numpy/core/getlimits.py
@@ -154,87 +154,6 @@ def _register_known_types():
     _register_type(float64_ma, b'\x9a\x99\x99\x99\x99\x99\xb9\xbf')
     _float_ma[64] = float64_ma
 
-    # Known parameters for IEEE 754 128-bit binary float
-    ld = ntypes.longdouble
-    epsneg_f128 = exp2(ld(-113))
-    tiny_f128 = exp2(ld(-16382))
-    # Ignore runtime error when this is not f128
-    with numeric.errstate(all='ignore'):
-        huge_f128 = (ld(1) - epsneg_f128) / tiny_f128 * ld(4)
-    float128_ma = MachArLike(ld,
-                             machep=-112,
-                             negep=-113,
-                             minexp=-16382,
-                             maxexp=16384,
-                             it=112,
-                             iexp=15,
-                             ibeta=2,
-                             irnd=5,
-                             ngrd=0,
-                             eps=exp2(ld(-112)),
-                             epsneg=epsneg_f128,
-                             huge=huge_f128,
-                             tiny=tiny_f128)
-    # IEEE 754 128-bit binary float
-    _register_type(float128_ma,
-        b'\x9a\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\xfb\xbf')
-    _register_type(float128_ma,
-        b'\x9a\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\x99\xfb\xbf')
-    _float_ma[128] = float128_ma
-
-    # Known parameters for float80 (Intel 80-bit extended precision)
-    epsneg_f80 = exp2(ld(-64))
-    tiny_f80 = exp2(ld(-16382))
-    # Ignore runtime error when this is not f80
-    with numeric.errstate(all='ignore'):
-        huge_f80 = (ld(1) - epsneg_f80) / tiny_f80 * ld(4)
-    float80_ma = MachArLike(ld,
-                            machep=-63,
-                            negep=-64,
-                            minexp=-16382,
-                            maxexp=16384,
-                            it=63,
-                            iexp=15,
-                            ibeta=2,
-                            irnd=5,
-                            ngrd=0,
-                            eps=exp2(ld(-63)),
-                            epsneg=epsneg_f80,
-                            huge=huge_f80,
-                            tiny=tiny_f80)
-    # float80, first 10 bytes containing actual storage
-    _register_type(float80_ma, b'\xcd\xcc\xcc\xcc\xcc\xcc\xcc\xcc\xfb\xbf')
-    _float_ma[80] = float80_ma
-
-    # Guessed / known parameters for double double; see:
-    # https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format#Double-double_arithmetic
-    # These numbers have the same exponent range as float64, but extended number of
-    # digits in the significand.
-    huge_dd = (umath.nextafter(ld(inf), ld(0))
-                if hasattr(umath, 'nextafter')  # Missing on some platforms?
-                else float64_ma.huge)
-    float_dd_ma = MachArLike(ld,
-                              machep=-105,
-                              negep=-106,
-                              minexp=-1022,
-                              maxexp=1024,
-                              it=105,
-                              iexp=11,
-                              ibeta=2,
-                              irnd=5,
-                              ngrd=0,
-                              eps=exp2(ld(-105)),
-                              epsneg= exp2(ld(-106)),
-                              huge=huge_dd,
-                              tiny=exp2(ld(-1022)))
-    # double double; low, high order (e.g. PPC 64)
-    _register_type(float_dd_ma,
-        b'\x9a\x99\x99\x99\x99\x99Y<\x9a\x99\x99\x99\x99\x99\xb9\xbf')
-    # double double; high, low order (e.g. PPC 64 le)
-    _register_type(float_dd_ma,
-        b'\x9a\x99\x99\x99\x99\x99\xb9\xbf\x9a\x99\x99\x99\x99\x99Y<')
-    _float_ma['dd'] = float_dd_ma
-
 
 def _get_machar(ftype):
     """ Get MachAr instance or MachAr-like instance
diff --git a/numpy/core/include/numpy/ndarraytypes.h b/numpy/core/include/numpy/ndarraytypes.h
index b0b749c..2d8e8c0 100644
--- a/numpy/core/include/numpy/ndarraytypes.h
+++ b/numpy/core/include/numpy/ndarraytypes.h
@@ -412,7 +412,7 @@ typedef int (PyArray_ScanFunc)(FILE *fp, void *dptr,
 typedef int (PyArray_FromStrFunc)(char *s, void *dptr, char **endptr,
                                   struct _PyArray_Descr *);
 
-typedef int (PyArray_FillFunc)(void *, npy_intp, void *);
+typedef void (PyArray_FillFunc)(void *, npy_intp, void *);
 
 typedef int (PyArray_SortFunc)(void *, npy_intp, void *);
 typedef int (PyArray_ArgSortFunc)(void *, npy_intp *, npy_intp, void *);
diff --git a/numpy/core/setup_common.py b/numpy/core/setup_common.py
index f837df1..d3ce70d 100644
--- a/numpy/core/setup_common.py
+++ b/numpy/core/setup_common.py
@@ -243,8 +243,8 @@ def check_long_double_representation(cmd):
     except ValueError:
         # try linking to support CC="gcc -flto" or icc -ipo
         # struct needs to be volatile so it isn't optimized away
-        body = body.replace('struct', 'volatile struct')
-        body += "int main(void) { return 0; }\n"
+        body = "#include <stdio.h>\n" + body.replace('struct', 'volatile struct')
+        body += 'int main(void) { printf("%p", &foo); return 0; }\n'
         src, obj = cmd._compile(body, None, None, 'c')
         cmd.temp_files.append("_configtest")
         cmd.compiler.link_executable([obj], "_configtest")
diff --git a/numpy/core/src/multiarray/alloc.c b/numpy/core/src/multiarray/alloc.c
index 6755095..e2fbae6 100644
--- a/numpy/core/src/multiarray/alloc.c
+++ b/numpy/core/src/multiarray/alloc.c
@@ -74,14 +74,6 @@ _npy_alloc_cache(npy_uintp nelem, npy_uintp esz, npy_uint msz,
 #ifdef _PyPyGC_AddMemoryPressure
         _PyPyPyGC_AddMemoryPressure(nelem * esz);
 #endif
-#ifdef HAVE_MADV_HUGEPAGE
-        /* allow kernel allocating huge pages for large arrays */
-        if (NPY_UNLIKELY(nelem * esz >= ((1u<<22u)))) {
-            npy_uintp offset = 4096u - (npy_uintp)p % (4096u);
-            npy_uintp length = nelem * esz - offset;
-            madvise((void*)((npy_uintp)p + offset), length, MADV_HUGEPAGE);
-        }
-#endif
     }
     return p;
 }
diff --git a/numpy/core/src/multiarray/typeinfo.c b/numpy/core/src/multiarray/typeinfo.c
index 14c4f27..c5a72b1 100644
--- a/numpy/core/src/multiarray/typeinfo.c
+++ b/numpy/core/src/multiarray/typeinfo.c
@@ -105,8 +105,7 @@ PyArray_typeinforanged(
 }
 
 /* Python version only needed for backport to 2.7 */
-#if (PY_VERSION_HEX < 0x03040000) \
-    || (defined(PYPY_VERSION_NUM) && (PYPY_VERSION_NUM < 0x07020000))
+#if (PY_VERSION_HEX < 0x03040000)
 
     static int
     PyStructSequence_InitType2(PyTypeObject *type, PyStructSequence_Desc *desc) {
diff --git a/numpy/core/src/npymath/ieee754.c.src b/numpy/core/src/npymath/ieee754.c.src
index d960838..56a8056 100644
--- a/numpy/core/src/npymath/ieee754.c.src
+++ b/numpy/core/src/npymath/ieee754.c.src
@@ -558,12 +558,10 @@ npy_longdouble npy_nextafterl(npy_longdouble x, npy_longdouble y)
 #endif
 
 int npy_clear_floatstatus() {
-    char x=0;
-    return npy_clear_floatstatus_barrier(&x);
+    return 0;
 }
 int npy_get_floatstatus() {
-    char x=0;
-    return npy_get_floatstatus_barrier(&x);
+    return 0;
 }
 
 /*
@@ -593,45 +591,32 @@ int npy_get_floatstatus() {
 
 int npy_get_floatstatus_barrier(char * param)
 {
-    int fpstatus = fpgetsticky();
-    /*
-     * By using a volatile, the compiler cannot reorder this call
-     */
-    if (param != NULL) {
-        volatile char NPY_UNUSED(c) = *(char*)param;
-    }
-    return ((FP_X_DZ  & fpstatus) ? NPY_FPE_DIVIDEBYZERO : 0) |
-           ((FP_X_OFL & fpstatus) ? NPY_FPE_OVERFLOW : 0) |
-           ((FP_X_UFL & fpstatus) ? NPY_FPE_UNDERFLOW : 0) |
-           ((FP_X_INV & fpstatus) ? NPY_FPE_INVALID : 0);
+    return 0;
 }
 
 int npy_clear_floatstatus_barrier(char * param)
 {
-    int fpstatus = npy_get_floatstatus_barrier(param);
-    fpsetsticky(0);
-
-    return fpstatus;
+    return 0;
 }
 
 void npy_set_floatstatus_divbyzero(void)
 {
-    fpsetsticky(FP_X_DZ);
+    return;
 }
 
 void npy_set_floatstatus_overflow(void)
 {
-    fpsetsticky(FP_X_OFL);
+    return;
 }
 
 void npy_set_floatstatus_underflow(void)
 {
-    fpsetsticky(FP_X_UFL);
+    return;
 }
 
 void npy_set_floatstatus_invalid(void)
 {
-    fpsetsticky(FP_X_INV);
+    return;
 }
 
 #elif defined(_AIX)
@@ -640,45 +625,32 @@ void npy_set_floatstatus_invalid(void)
 
 int npy_get_floatstatus_barrier(char *param)
 {
-    int fpstatus = fp_read_flag();
-    /*
-     * By using a volatile, the compiler cannot reorder this call
-     */
-    if (param != NULL) {
-        volatile char NPY_UNUSED(c) = *(char*)param;
-    }
-    return ((FP_DIV_BY_ZERO & fpstatus) ? NPY_FPE_DIVIDEBYZERO : 0) |
-           ((FP_OVERFLOW & fpstatus) ? NPY_FPE_OVERFLOW : 0) |
-           ((FP_UNDERFLOW & fpstatus) ? NPY_FPE_UNDERFLOW : 0) |
-           ((FP_INVALID & fpstatus) ? NPY_FPE_INVALID : 0);
+    return 0;
 }
 
 int npy_clear_floatstatus_barrier(char * param)
 {
-    int fpstatus = npy_get_floatstatus_barrier(param);
-    fp_swap_flag(0);
-
-    return fpstatus;
+    return 0;
 }
 
 void npy_set_floatstatus_divbyzero(void)
 {
-    fp_raise_xcp(FP_DIV_BY_ZERO);
+    return;
 }
 
 void npy_set_floatstatus_overflow(void)
 {
-    fp_raise_xcp(FP_OVERFLOW);
+    return;
 }
 
 void npy_set_floatstatus_underflow(void)
 {
-    fp_raise_xcp(FP_UNDERFLOW);
+    return;
 }
 
 void npy_set_floatstatus_invalid(void)
 {
-    fp_raise_xcp(FP_INVALID);
+    return;
 }
 
 #elif defined(_MSC_VER) || (defined(__osf__) && defined(__alpha))
@@ -698,23 +670,22 @@ static volatile double _npy_floatstatus_x,
 
 void npy_set_floatstatus_divbyzero(void)
 {
-    _npy_floatstatus_x = 1.0 / _npy_floatstatus_zero;
+    return;
 }
 
 void npy_set_floatstatus_overflow(void)
 {
-    _npy_floatstatus_x = _npy_floatstatus_big * 1e300;
+    return;
 }
 
 void npy_set_floatstatus_underflow(void)
 {
-    _npy_floatstatus_x = _npy_floatstatus_small * 1e-300;
+    return;
 }
 
 void npy_set_floatstatus_invalid(void)
 {
-    _npy_floatstatus_inf = NPY_INFINITY;
-    _npy_floatstatus_x = _npy_floatstatus_inf - NPY_INFINITY;
+    return;
 }
 
 /* MS Windows -----------------------------------------------------*/
@@ -724,32 +695,12 @@ void npy_set_floatstatus_invalid(void)
 
 int npy_get_floatstatus_barrier(char *param)
 {
-    /*
-     * By using a volatile, the compiler cannot reorder this call
-     */
-#if defined(_WIN64)
-    int fpstatus = _statusfp();
-#else
-    /* windows enables sse on 32 bit, so check both flags */
-    int fpstatus, fpstatus2;
-    _statusfp2(&fpstatus, &fpstatus2);
-    fpstatus |= fpstatus2;
-#endif
-    if (param != NULL) {
-        volatile char NPY_UNUSED(c) = *(char*)param;
-    }
-    return ((SW_ZERODIVIDE & fpstatus) ? NPY_FPE_DIVIDEBYZERO : 0) |
-           ((SW_OVERFLOW & fpstatus) ? NPY_FPE_OVERFLOW : 0) |
-           ((SW_UNDERFLOW & fpstatus) ? NPY_FPE_UNDERFLOW : 0) |
-           ((SW_INVALID & fpstatus) ? NPY_FPE_INVALID : 0);
+    return 0;
 }
 
 int npy_clear_floatstatus_barrier(char *param)
 {
-    int fpstatus = npy_get_floatstatus_barrier(param);
-    _clearfp();
-
-    return fpstatus;
+    return 0;
 }
 
 /*  OSF/Alpha (Tru64)  ---------------------------------------------*/
@@ -759,26 +710,12 @@ int npy_clear_floatstatus_barrier(char *param)
 
 int npy_get_floatstatus_barrier(char *param)
 {
-    unsigned long fpstatus = ieee_get_fp_control();
-    /*
-     * By using a volatile, the compiler cannot reorder this call
-     */
-    if (param != NULL) {
-        volatile char NPY_UNUSED(c) = *(char*)param;
-    }
-    return  ((IEEE_STATUS_DZE & fpstatus) ? NPY_FPE_DIVIDEBYZERO : 0) |
-            ((IEEE_STATUS_OVF & fpstatus) ? NPY_FPE_OVERFLOW : 0) |
-            ((IEEE_STATUS_UNF & fpstatus) ? NPY_FPE_UNDERFLOW : 0) |
-            ((IEEE_STATUS_INV & fpstatus) ? NPY_FPE_INVALID : 0);
+    return 0;
 }
 
 int npy_clear_floatstatus_barrier(char *param)
 {
-    int fpstatus = npy_get_floatstatus_barrier(param);
-    /* clear status bits as well as disable exception mode if on */
-    ieee_set_fp_control(0);
-
-    return fpstatus;
+    return 0;
 }
 
 #endif
@@ -790,52 +727,33 @@ int npy_clear_floatstatus_barrier(char *param)
 
 int npy_get_floatstatus_barrier(char* param)
 {
-    int fpstatus = fetestexcept(FE_DIVBYZERO | FE_OVERFLOW |
-                                FE_UNDERFLOW | FE_INVALID);
-    /*
-     * By using a volatile, the compiler cannot reorder this call
-     */
-    if (param != NULL) {
-        volatile char NPY_UNUSED(c) = *(char*)param;
-    }
-
-    return ((FE_DIVBYZERO  & fpstatus) ? NPY_FPE_DIVIDEBYZERO : 0) |
-           ((FE_OVERFLOW   & fpstatus) ? NPY_FPE_OVERFLOW : 0) |
-           ((FE_UNDERFLOW  & fpstatus) ? NPY_FPE_UNDERFLOW : 0) |
-           ((FE_INVALID    & fpstatus) ? NPY_FPE_INVALID : 0);
+    return 0;
 }
 
 int npy_clear_floatstatus_barrier(char * param)
 {
-    /* testing float status is 50-100 times faster than clearing on x86 */
-    int fpstatus = npy_get_floatstatus_barrier(param);
-    if (fpstatus != 0) {
-        feclearexcept(FE_DIVBYZERO | FE_OVERFLOW |
-                      FE_UNDERFLOW | FE_INVALID);
-    }
-
-    return fpstatus;
+    return 0;
 }
 
 
 void npy_set_floatstatus_divbyzero(void)
 {
-    feraiseexcept(FE_DIVBYZERO);
+    return;
 }
 
 void npy_set_floatstatus_overflow(void)
 {
-    feraiseexcept(FE_OVERFLOW);
+    return;
 }
 
 void npy_set_floatstatus_underflow(void)
 {
-    feraiseexcept(FE_UNDERFLOW);
+    return;
 }
 
 void npy_set_floatstatus_invalid(void)
 {
-    feraiseexcept(FE_INVALID);
+    return;
 }
 
 #endif
diff --git a/numpy/core/src/umath/extobj.c b/numpy/core/src/umath/extobj.c
index aea1815..b83fab9 100644
--- a/numpy/core/src/umath/extobj.c
+++ b/numpy/core/src/umath/extobj.c
@@ -282,7 +282,7 @@ _check_ufunc_fperr(int errmask, PyObject *extobj, const char *ufunc_name) {
     if (!errmask) {
         return 0;
     }
-    fperr = npy_get_floatstatus_barrier((char*)extobj);
+    fperr = npy_get_floatstatus_barrier("");
     if (!fperr) {
         return 0;
     }
diff --git a/numpy/core/src/umath/loops.c.src b/numpy/core/src/umath/loops.c.src
index 975a5e6..55f3a46 100644
--- a/numpy/core/src/umath/loops.c.src
+++ b/numpy/core/src/umath/loops.c.src
@@ -1872,7 +1872,7 @@ NPY_NO_EXPORT void
             *((npy_bool *)op1) = @func@(in1) != 0;
         }
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
@@ -1932,7 +1932,7 @@ NPY_NO_EXPORT void
             *((@type@ *)op1) = in1;
         }
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
@@ -1960,7 +1960,7 @@ NPY_NO_EXPORT void
             *((@type@ *)op1) = (in1 @OP@ in2 || npy_isnan(in2)) ? in1 : in2;
         }
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
@@ -2050,7 +2050,7 @@ NPY_NO_EXPORT void
             *((@type@ *)op1) = tmp + 0;
         }
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 
 NPY_NO_EXPORT void
@@ -2236,7 +2236,7 @@ HALF_@kind@(char **args, npy_intp *dimensions, npy_intp *steps, void *NPY_UNUSED
         const npy_half in1 = *(npy_half *)ip1;
         *((npy_bool *)op1) = @func@(in1) != 0;
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat**/
 
@@ -2741,7 +2741,7 @@ NPY_NO_EXPORT void
         const @ftype@ in1i = ((@ftype@ *)ip1)[1];
         *((npy_bool *)op1) = @func@(in1r) @OP@ @func@(in1i);
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
@@ -2848,7 +2848,7 @@ NPY_NO_EXPORT void
         ((@ftype@ *)op1)[0] = in1r;
         ((@ftype@ *)op1)[1] = in1i;
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
@@ -2873,7 +2873,7 @@ NPY_NO_EXPORT void
             ((@ftype@ *)op1)[1] = in2i;
         }
     }
-    npy_clear_floatstatus_barrier((char*)dimensions);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
diff --git a/numpy/core/src/umath/reduction.c b/numpy/core/src/umath/reduction.c
index 791d369..317ee71 100644
--- a/numpy/core/src/umath/reduction.c
+++ b/numpy/core/src/umath/reduction.c
@@ -534,7 +534,7 @@ PyUFunc_ReduceWrapper(PyArrayObject *operand, PyArrayObject *out,
     }
 
     /* Start with the floating-point exception flags cleared */
-    npy_clear_floatstatus_barrier((char*)&iter);
+    npy_clear_floatstatus_barrier("");
 
     if (NpyIter_GetIterSize(iter) != 0) {
         NpyIter_IterNextFunc *iternext;
diff --git a/numpy/core/src/umath/scalarmath.c.src b/numpy/core/src/umath/scalarmath.c.src
index a7987ac..aae7c30 100644
--- a/numpy/core/src/umath/scalarmath.c.src
+++ b/numpy/core/src/umath/scalarmath.c.src
@@ -846,7 +846,7 @@ static PyObject *
     }
 
 #if @fperr@
-    npy_clear_floatstatus_barrier((char*)&out);
+    npy_clear_floatstatus_barrier("");
 #endif
 
     /*
@@ -861,7 +861,7 @@ static PyObject *
 
 #if @fperr@
     /* Check status flag.  If it is set, then look up what to do */
-    retstatus = npy_get_floatstatus_barrier((char*)&out);
+    retstatus = npy_get_floatstatus_barrier("");
     if (retstatus) {
         int bufsize, errmask;
         PyObject *errobj;
@@ -991,7 +991,7 @@ static PyObject *
         return Py_NotImplemented;
     }
 
-    npy_clear_floatstatus_barrier((char*)&out);
+    npy_clear_floatstatus_barrier("");
 
     /*
      * here we do the actual calculation with arg1 and arg2
@@ -1006,7 +1006,7 @@ static PyObject *
     }
 
     /* Check status flag.  If it is set, then look up what to do */
-    retstatus = npy_get_floatstatus_barrier((char*)&out);
+    retstatus = npy_get_floatstatus_barrier("");
     if (retstatus) {
         int bufsize, errmask;
         PyObject *errobj;
@@ -1070,7 +1070,7 @@ static PyObject *
         return Py_NotImplemented;
     }
 
-    npy_clear_floatstatus_barrier((char*)&out);
+    npy_clear_floatstatus_barrier("");
 
     /*
      * here we do the actual calculation with arg1 and arg2
@@ -1134,7 +1134,7 @@ static PyObject *
         return Py_NotImplemented;
     }
 
-    npy_clear_floatstatus_barrier((char*)&out);
+    npy_clear_floatstatus_barrier("");
 
     /*
      * here we do the actual calculation with arg1 and arg2
@@ -1148,7 +1148,7 @@ static PyObject *
     }
 
     /* Check status flag.  If it is set, then look up what to do */
-    retstatus = npy_get_floatstatus_barrier((char*)&out);
+    retstatus = npy_get_floatstatus_barrier("");
     if (retstatus) {
         int bufsize, errmask;
         PyObject *errobj;
diff --git a/numpy/core/src/umath/simd.inc.src b/numpy/core/src/umath/simd.inc.src
index 4bb8569..8b120d7 100644
--- a/numpy/core/src/umath/simd.inc.src
+++ b/numpy/core/src/umath/simd.inc.src
@@ -1047,7 +1047,7 @@ sse2_@kind@_@TYPE@(@type@ * ip, @type@ * op, const npy_intp n)
         i += 2 * stride;
 
         /* minps/minpd will set invalid flag if nan is encountered */
-        npy_clear_floatstatus_barrier((char*)&c1);
+        npy_clear_floatstatus_barrier("");
         LOOP_BLOCKED(@type@, 2 * VECTOR_SIZE_BYTES) {
             @vtype@ v1 = @vpre@_load_@vsuf@((@type@*)&ip[i]);
             @vtype@ v2 = @vpre@_load_@vsuf@((@type@*)&ip[i + stride]);
@@ -1056,7 +1056,7 @@ sse2_@kind@_@TYPE@(@type@ * ip, @type@ * op, const npy_intp n)
         }
         c1 = @vpre@_@VOP@_@vsuf@(c1, c2);
 
-        if (npy_get_floatstatus_barrier((char*)&c1) & NPY_FPE_INVALID) {
+        if (npy_get_floatstatus_barrier("") & NPY_FPE_INVALID) {
             *op = @nan@;
         }
         else {
@@ -1069,7 +1069,7 @@ sse2_@kind@_@TYPE@(@type@ * ip, @type@ * op, const npy_intp n)
         /* Order of operations important for MSVC 2015 */
         *op  = (*op @OP@ ip[i] || npy_isnan(*op)) ? *op : ip[i];
     }
-    npy_clear_floatstatus_barrier((char*)op);
+    npy_clear_floatstatus_barrier("");
 }
 /**end repeat1**/
 
diff --git a/numpy/core/src/umath/ufunc_object.c b/numpy/core/src/umath/ufunc_object.c
index d1b029c..2bdff3d 100644
--- a/numpy/core/src/umath/ufunc_object.c
+++ b/numpy/core/src/umath/ufunc_object.c
@@ -107,7 +107,7 @@ PyUFunc_getfperr(void)
      * keep it so just in case third party code relied on the clearing
      */
     char param = 0;
-    return npy_clear_floatstatus_barrier(&param);
+    return npy_clear_floatstatus_barrier("");
 }
 
 #define HANDLEIT(NAME, str) {if (retstatus & NPY_FPE_##NAME) {          \
@@ -141,7 +141,7 @@ PyUFunc_checkfperr(int errmask, PyObject *errobj, int *first)
 {
     /* clearing is done for backward compatibility */
     int retstatus;
-    retstatus = npy_clear_floatstatus_barrier((char*)&retstatus);
+    retstatus = npy_clear_floatstatus_barrier("");
 
     return PyUFunc_handlefperr(errmask, errobj, retstatus, first);
 }
@@ -153,7 +153,7 @@ NPY_NO_EXPORT void
 PyUFunc_clearfperr()
 {
     char param = 0;
-    npy_clear_floatstatus_barrier(&param);
+    npy_clear_floatstatus_barrier("");
 }
 
 /*
@@ -2979,7 +2979,7 @@ PyUFunc_GeneralizedFunction(PyUFuncObject *ufunc,
 #endif
 
     /* Start with the floating-point exception flags cleared */
-    npy_clear_floatstatus_barrier((char*)&iter);
+    npy_clear_floatstatus_barrier("");
 
     NPY_UF_DBG_PRINT("Executing inner loop\n");
 
@@ -3237,7 +3237,7 @@ PyUFunc_GenericFunction(PyUFuncObject *ufunc,
 
         /* Set up the flags */
 
-        npy_clear_floatstatus_barrier((char*)&ufunc);
+        npy_clear_floatstatus_barrier("");
         retval = execute_fancy_ufunc_loop(ufunc, wheremask,
                             op, dtypes, order,
                             buffersize, arr_prep, full_args, op_flags);
@@ -3257,7 +3257,7 @@ PyUFunc_GenericFunction(PyUFuncObject *ufunc,
         }
 
         /* check_for_trivial_loop on half-floats can overflow */
-        npy_clear_floatstatus_barrier((char*)&ufunc);
+        npy_clear_floatstatus_barrier("");
 
         retval = execute_legacy_ufunc_loop(ufunc, trivial_loop_ok,
                             op, dtypes, order,
diff --git a/numpy/ctypeslib.py b/numpy/ctypeslib.py
index 535ea76..2ecf3a2 100644
--- a/numpy/ctypeslib.py
+++ b/numpy/ctypeslib.py
@@ -61,7 +61,7 @@ from numpy import (
 from numpy.core.multiarray import _flagdict, flagsobj
 
 try:
-    import ctypes
+    ctypes = None # Truffle: use the mock ctypes
 except ImportError:
     ctypes = None
 
diff --git a/numpy/linalg/setup.py b/numpy/linalg/setup.py
index 66c07c9..847116f 100644
--- a/numpy/linalg/setup.py
+++ b/numpy/linalg/setup.py
@@ -29,6 +29,7 @@ def configuration(parent_package='', top_path=None):
     lapack_info = get_info('lapack_opt', 0)  # and {}
 
     def get_lapack_lite_sources(ext, build_dir):
+        return all_sources
         if not lapack_info:
             print("### Warning:  Using unoptimized lapack ###")
             return all_sources
diff --git a/numpy/linalg/umath_linalg.c.src b/numpy/linalg/umath_linalg.c.src
index 9fc68a7..6c04f96 100644
--- a/numpy/linalg/umath_linalg.c.src
+++ b/numpy/linalg/umath_linalg.c.src
@@ -386,7 +386,7 @@ static NPY_INLINE int
 get_fp_invalid_and_clear(void)
 {
     int status;
-    status = npy_clear_floatstatus_barrier((char*)&status);
+    status = npy_clear_floatstatus_barrier("");
     return !!(status & NPY_FPE_INVALID);
 }
 
@@ -397,7 +397,7 @@ set_fp_invalid_or_clear(int error_occurred)
         npy_set_floatstatus_invalid();
     }
     else {
-        npy_clear_floatstatus_barrier((char*)&error_occurred);
+        npy_clear_floatstatus_barrier("");
     }
 }
 
diff --git a/numpy/tests/test_ctypeslib.py b/numpy/tests/test_ctypeslib.py
index 521208c..b9fa4c3 100644
--- a/numpy/tests/test_ctypeslib.py
+++ b/numpy/tests/test_ctypeslib.py
@@ -10,6 +10,7 @@ from numpy.distutils.misc_util import get_shared_lib_extension
 from numpy.testing import assert_, assert_array_equal, assert_raises, assert_equal
 
 try:
+    import _ctypes
     import ctypes
 except ImportError:
     ctypes = None
diff --git a/setup.py b/setup.py
index 8b2ded1..8a9295a 100755
--- a/setup.py
+++ b/setup.py
@@ -364,6 +364,8 @@ def setup_package():
     metadata = dict(
         name = 'numpy',
         maintainer = "NumPy Developers",
+        zip_safe = False, # Truffle: make sure we're not zipped
+        include_package_data = True,
         maintainer_email = "numpy-discussion@python.org",
         description = DOCLINES[0],
         long_description = "\n".join(DOCLINES[2:]),
@@ -376,7 +378,6 @@ def setup_package():
         test_suite='nose.collector',
         cmdclass={"sdist": sdist_checked},
         python_requires='>=2.7,!=3.0.*,!=3.1.*,!=3.2.*,!=3.3.*',
-        zip_safe=False,
         entry_points={
             'console_scripts': f2py_cmds
         },

diff --git a/numpy/distutils/ccompiler.py b/numpy/distutils/ccompiler.py
index 14451fa..85e64cc 100644
--- a/numpy/distutils/ccompiler.py
+++ b/numpy/distutils/ccompiler.py
@@ -682,7 +682,7 @@ def CCompiler_cxx_compiler(self):
         return self
 
     cxx = copy(self)
-    cxx.compiler_so = [cxx.compiler_cxx[0]] + cxx.compiler_so[1:]
+    cxx.compiler_so = cxx.compiler_cxx + cxx.compiler_so[1:]
     if sys.platform.startswith('aix') and 'ld_so_aix' in cxx.linker_so[0]:
         # AIX needs the ld_so_aix script included with Python
         cxx.linker_so = [cxx.linker_so[0], cxx.compiler_cxx[0]] \

'''
        install_from_pypi("numpy==1.16.4", patch=patch, env={"NPY_NUM_BUILD_JOBS": "1"}, **kwargs)

    @pip_package()
    def dateutil(**kwargs):
        setuptools_scm(**kwargs)
        install_from_pypi("python-dateutil==2.7.5", **kwargs)

    @pip_package()
    def certifi(**kwargs):
        install_from_pypi("certifi==2019.9.11", **kwargs)

    @pip_package()
    def idna(**kwargs):
        install_from_pypi("idna==2.8", **kwargs)

    @pip_package()
    def chardet(**kwargs):
        install_from_pypi("chardet==3.0.4", **kwargs)

    @pip_package()
    def urllib3(**kwargs):
        install_from_pypi("urllib3==1.25.6", **kwargs)

    @pip_package()
    def requests(**kwargs):
        idna(**kwargs)
        certifi(**kwargs)
        chardet(**kwargs)
        urllib3(**kwargs)
        install_from_pypi("requests==2.22", **kwargs)

    @pip_package()
    def lightfm(**kwargs):
        # pandas(**kwargs)
        requests(**kwargs)
        patch = r"""
--- a/setup.py
+++ b/setup.py
@@ -147,7 +147,8 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc


 use_openmp = not sys.platform.startswith('darwin') and not sys.platform.startswith('win')
+use_openmp = False

 setup(
     name='lightfm',
"""
        install_from_pypi("lightfm==1.15", patch=patch, **kwargs)

    @pip_package()
    def pytz(**kwargs):
        install_from_pypi("pytz==2018.7", **kwargs)

    @pip_package()
    def pandas(**kwargs):
        pytz(**kwargs)
        six(**kwargs)
        dateutil(**kwargs)
        numpy(**kwargs)

        # download pandas-0.25.0
        patch = r'''diff --git a/pandas/io/msgpack/_packer.cpp b/pandas/io/msgpack/_packer.cpp
index f793920..5b0b28c 100644
--- a/pandas/io/msgpack/_packer.cpp
+++ b/pandas/io/msgpack/_packer.cpp
@@ -680,10 +680,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
 static CYTHON_INLINE int __Pyx_is_valid_index(Py_ssize_t i, Py_ssize_t limit) {
     return (size_t) i < (size_t) limit;
 }
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)
diff --git a/pandas/io/msgpack/_unpacker.cpp b/pandas/io/msgpack/_unpacker.cpp
index d6c871c..5853474 100644
--- a/pandas/io/msgpack/_unpacker.cpp
+++ b/pandas/io/msgpack/_unpacker.cpp
@@ -682,10 +682,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
 static CYTHON_INLINE int __Pyx_is_valid_index(Py_ssize_t i, Py_ssize_t limit) {
     return (size_t) i < (size_t) limit;
 }
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)
diff --git a/pandas/_libs/window.cpp b/pandas/_libs/window.cpp
index d527af6..773cfe0 100644
--- a/pandas/_libs/window.cpp
+++ b/pandas/_libs/window.cpp
@@ -705,10 +705,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
 static CYTHON_INLINE int __Pyx_is_valid_index(Py_ssize_t i, Py_ssize_t limit) {
     return (size_t) i < (size_t) limit;
 }
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)
@@ -881,13 +881,7 @@ static const char *__pyx_filename;
 
 /* Header.proto */
 #if !defined(CYTHON_CCOMPLEX)
-  #if defined(__cplusplus)
-    #define CYTHON_CCOMPLEX 1
-  #elif defined(_Complex_I)
-    #define CYTHON_CCOMPLEX 1
-  #else
     #define CYTHON_CCOMPLEX 0
-  #endif
 #endif
 #if CYTHON_CCOMPLEX
   #ifdef __cplusplus
diff --git a/pandas/core/window.py b/pandas/core/window.py
index 8657420..f7b3f08 100644
--- a/pandas/core/window.py
+++ b/pandas/core/window.py
@@ -10,7 +10,7 @@ import warnings

 import numpy as np

-import pandas._libs.window as libwindow
+libwindow = None
 from pandas.compat._optional import import_optional_dependency
 from pandas.compat.numpy import function as nv
 from pandas.util._decorators import Appender, Substitution, cache_readonly
'''
        # workaround until Sulong toolchain fixes this
        cflags = "-stdlib=libc++ -lm -lc" if sys.implementation.name == "graalpython" else ""
        install_from_pypi("pandas==0.25.0", patch=patch, add_cflags=cflags, **kwargs)

    @pip_package()
    def scipy(**kwargs):
        venv_path = os.environ.get("VIRTUAL_ENV", None)
        if not venv_path:
            xit("SciPy can only be installed within a virtual env.")

        llvm_path = kwargs.get("llvm", None)
        if not llvm_path:
            xit("Please provide path to the LLVM installation to use for building SciPy.")
        else:
            del kwargs["llvm"]

        # currently we need 'ar', 'ranlib', and 'ld.lld'
        llvm_bins = {"llvm-ar": "ar", "llvm-ranlib": "ranlib", "ld.lld": "ld.lld"}
        for binary in llvm_bins.keys():
            llvm_bin = os.path.join(llvm_path, "bin", binary)
            if not os.path.isfile(llvm_bin):
                xit("Could not locate llvm-ar at '{}'".format(llvm_bin))
            else:
                dest = os.path.join(venv_path, "bin", llvm_bins[binary])
                if os.path.exists(dest):
                    os.unlink(dest)
                os.symlink(llvm_bin, dest)

        # locate system's gfortran
        path_without_venv = os.pathsep.join([x for x in os.environ["PATH"].split(os.pathsep) if venv_path not in x])
        system_gfortran = shutil.which("gfortran", path=path_without_venv)
        if not system_gfortran:
            xit("Could not locate gfortran binary.")
            
        # create gfortran wrapper script into venv's bin directory
        gfortran_wrapper = os.path.join(venv_path, "bin", "gfortran")
        assert system_gfortran != gfortran_wrapper
        with open(gfortran_wrapper, "w") as f:
            f.write('#!/bin/bash\nexec "{}" -fuse-ld=lld $@\n'.format(system_gfortran))
        # make it executable
        os.chmod(gfortran_wrapper , 0o775)

        # install dependencies
        numpy(**kwargs)

        patch = '''diff --git a/scipy/__init__.py b/scipy/__init__.py
index 2c7508f..f3352ec 100755
--- a/scipy/__init__.py
+++ b/scipy/__init__.py
@@ -116,8 +116,6 @@ else:
 
     del _NumpyVersion
 
-    from scipy._lib._ccallback import LowLevelCallable
-
     from scipy._lib._testutils import PytestTester
     test = PytestTester(__name__)
     del PytestTester
'''
        install_from_pypi("scipy==1.3.1", patch=patch, env={"NPY_NUM_BUILD_JOBS": "1"}, **kwargs)

    return locals()


KNOWN_PACKAGES = known_packages()


def xit(msg, status=-1):
    print(msg)
    exit(-1)


def _install_from_url(url, patch=None, extra_opts=[], add_cflags="", ignore_errors=False, env={}):
    name = url[url.rfind("/")+1:]
    tempdir = tempfile.mkdtemp()

    # honor env var 'HTTP_PROXY' and 'HTTPS_PROXY'
    os_env = os.environ
    curl_opts = []
    if url.startswith("http://") and "HTTP_PROXY" in os_env:
        curl_opts += ["--proxy", os_env["HTTP_PROXY"]]
    elif url.startswith("https://") and "HTTPS_PROXY" in os_env:
        curl_opts += ["--proxy", os_env["HTTPS_PROXY"]]

    # honor env var 'CFLAGS' and 'CPPFLAGS'
    cppflags = os_env.get("CPPFLAGS", "")
    cflags = "-v " + os_env.get("CFLAGS", "") + ((" " + add_cflags) if add_cflags else "")

    env_str = ('CFLAGS="%s" ' % cflags if cflags else "") + ('CPPFLAGS="%s" ' % cppflags if cppflags else "")
    for key in env.keys():
        env_str = env_str + ('%s="%s" ' % (key, env[key]))

    if os.system("curl -L -o %s/%s %s" % (tempdir, name, url)) != 0:
        # honor env var 'HTTP_PROXY' and 'HTTPS_PROXY'
        env = os.environ
        curl_opts = []
        if url.startswith("http://") and "HTTP_PROXY" in env:
            curl_opts += ["--proxy", env["HTTP_PROXY"]]
        elif url.startswith("https://") and "HTTPS_PROXY" in env:
            curl_opts += ["--proxy", env["HTTPS_PROXY"]]
        system("curl -L %s -o %s/%s %s" % (" ".join(curl_opts), tempdir, name, url), msg="Download error")

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
    status = system("cd %s/%s; %s %s setup.py install %s %s" % (tempdir, bare_name, env_str, sys.executable, user_arg, " ".join(extra_opts)))
    if status != 0 and not ignore_errors:
        xit("An error occurred trying to run `setup.py install %s %s'" % (user_arg, " ".join(extra_opts)))


def install_from_pypi(package, patch=None, extra_opts=[], add_cflags="", ignore_errors=True, env={}):
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
        r = subprocess.check_output("curl -L %s" % url, shell=True).decode("utf8")
        url = None
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
        _install_from_url(url, patch=patch, extra_opts=extra_opts, add_cflags=add_cflags, ignore_errors=ignore_errors, env=env)
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
    install_parser.add_argument(
        "--llvm",
        help="Path to LLVM installation."
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
                    KNOWN_PACKAGES[pkg](llvm=args.llvm)
    elif args.command == "pypi":
        for pkg in args.package.split(","):
            install_from_pypi(pkg, ignore_errors=False)


if __name__ == "__main__":
    main(sys.argv[1:])
