#!/usr/bin/env bash
# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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

if [ $# -ne 1 ]; then
    echo "Need numpy extracted source path as only argument"
    exit 1
fi

NPY_PATH="$1"
DIR=`cd $(dirname $0); pwd`
export LD="${DIR}/crossld.py"
export CC="${DIR}/crosscc.py"

pushd "$NPY_PATH"
python3 setup.py build

# npysort and npymath are statically linked
eval "llvm-link -o build/libnpysort.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npysort/quicksort.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npysort/mergesort.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npysort/heapsort.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npysort/selection.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npysort/binsearch.bc"

eval "llvm-link -o build/libnpymath.bc \
          build/temp.linux-x86_64-3.*/numpy/core/src/npymath/npy_math.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npymath/npy_math_complex.bc \
          build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/npymath/ieee754.bc \
          build/temp.linux-x86_64-3.*/numpy/core/src/npymath/halffloat.bc"

# finally, link the multiarray module
eval "llvm-link -o build/multiarray.bc \
   build/libnpymath.bc \
   build/libnpysort.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/alloc.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/arrayobject.bc \
   build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/multiarray/arraytypes.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/array_assign.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/array_assign_scalar.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/array_assign_array.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/buffer.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/calculation.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/compiled_base.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/common.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/convert.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/convert_datatype.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/conversion_utils.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/ctors.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/datetime.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/datetime_strings.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/datetime_busday.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/datetime_busdaycal.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/descriptor.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/dragon4.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/dtype_transfer.bc \
   build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/multiarray/einsum.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/flagsobject.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/getset.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/hashdescr.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/item_selection.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/iterators.bc \
   build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/multiarray/lowlevel_strided_loops.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/mapping.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/methods.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/multiarraymodule.bc \
   build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/multiarray/nditer_templ.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/nditer_api.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/nditer_constr.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/nditer_pywrap.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/number.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/numpyos.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/refcount.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/sequence.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/shape.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/scalarapi.bc \
   build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/multiarray/scalartypes.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/strfuncs.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/temp_elide.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/usertypes.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/ucsnarrow.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/multiarray/vdot.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/private/mem_overlap.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/private/npy_longdouble.bc \
   build/temp.linux-x86_64-3.*/numpy/core/src/private/ufunc_override.bc"

cp build/multiarray.bc numpy/core/

eval "llvm-link -o build/umath.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/umath/umathmodule.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/umath/reduction.bc \
     build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/umath/loops.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/umath/ufunc_object.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/umath/extobj.bc \
     build/temp.linux-x86_64-3.*/build/src.linux-x86_64-3.*/numpy/core/src/umath/scalarmath.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/umath/ufunc_type_resolution.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/umath/override.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/private/mem_overlap.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/private/npy_longdouble.bc \
     build/temp.linux-x86_64-3.*/numpy/core/src/private/ufunc_override.bc"

cp build/umath.bc numpy/core/

popd
