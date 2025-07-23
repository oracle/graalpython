# Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

from . import CPyExtTestCase, CPyExtFunction, unhandled_error_compare


def _reference_get_slice(attr):
    def inner(args):
        if isinstance(args[0], slice):
            return getattr(args[0], attr)
        raise TypeError
    return inner


class TestPySlice(CPyExtTestCase):

    def reference_get_indices(slize, length):
        r = slize.indices(length)
        slicelength = 0
        for i in range(r[0], r[1], r[2]):
            slicelength += 1
        retval = [0]
        retval += list(r)
        retval.append(slicelength)
        return tuple(retval)


    test_PySlice_GetIndicesEx = CPyExtFunction(
        lambda args: TestPySlice.reference_get_indices(*args),
        lambda: (
            (slice(0,1,2), 10),
            (slice(None,-1), 10),
            (slice(None,-1), 1),
            (slice(1), 3),
            (slice(None, -1), 3),
        ),
        code='''PyObject* wrapper_SliceIndices(PyObject* slice, int length) {
            Py_ssize_t start, stop, step, slicelength;
            int result = PySlice_GetIndicesEx(slice, length, &start, &stop, &step, &slicelength);
            return Py_BuildValue("iiiii", result, start, stop, step, slicelength);
        }
        ''',
        resultspec="O",
        argspec='Oi',
        callfunction="wrapper_SliceIndices",
        arguments=["PyObject* slice", "int length"],
        cmpfunc=unhandled_error_compare
    )

    def reference_unpack(slize):
        start = 0 if slize.start is None else slize.start
        stop = -1 if slize.stop is None else slize.stop
        step = 1 if slize.step is None else slize.step
        if step == 0:
            raise ValueError("slice step cannot be zero")
        return (0, start, stop, step)

    test_PySlice_Unpack = CPyExtFunction(
        lambda arg: TestPySlice.reference_unpack(*arg),
        lambda: (
            (slice(0,1,2),),
            (slice(0,-1),),
            (slice(None,-1,0),),
            (slice(0,1,-1),),
            (slice(1,-1),),
            (slice(1),),
        ),
        code='''PyObject* wrapper_SliceUnpack(PyObject* slice) {
        Py_ssize_t start, stop, step;
        int result = PySlice_Unpack(slice, &start, &stop, &step);
        if (result < 0) {
             return NULL;
        }
        return Py_BuildValue("iiii", result, start, stop, step);
    }
    ''',
        resultspec="O",
        argspec='O',
        callfunction="wrapper_SliceUnpack",
        arguments=["PyObject* slice"],
        cmpfunc=unhandled_error_compare
    )

    def reference_adjust(length, start, stop, step):
        slicelength = -1

        if start < 0:
            start += length
            if (start < 0):
                start = -1 if step < 0 else 0
        elif start >= length:
            start = length - 1 if step < 0 else length

        if stop < 0:
            stop += length
            if (stop < 0):
                stop = -1 if step < 0 else 0
        elif stop >= length:
            stop = length - 1 if step < 0 else length

        if step < 0:
            if stop < start:
                slicelength = (start - stop - 1) / (-step) + 1
                return slicelength, start, stop
        else:
            if start < stop:
                slicelength = (stop - start - 1) / step + 1
                return slicelength, start, stop

        return 0, start, stop


    test_PySlice_AdjustIndices = CPyExtFunction(
        lambda arg: TestPySlice.reference_adjust(*arg),
        lambda: (
            (3,0,-1,1),
            (3,1,-1,1),
            (12,-1,-1,-1),
            (12,-1,0,-1),
            (12,-1,0,-2),
            (3,1,-1,2),
        ),
        code='''PyObject* Slice_AdjustIndices(int length, int sta, int sto, int step) {
        Py_ssize_t start = sta;
        Py_ssize_t stop = sto;
        int slicelength = PySlice_AdjustIndices(length, &start, &stop, step);
        return Py_BuildValue("iii", slicelength, start, stop);
    }
    ''',
        resultspec="O",
        argspec='iiii',
        callfunction="Slice_AdjustIndices",
        arguments=["int length", "int start", "int stop", "int step"],
        cmpfunc=unhandled_error_compare
    )

    def reference_new_slice(args):
        return slice(args[0], args[1], args[2])

    test_PySlice_New = CPyExtFunction(
        reference_new_slice,
        lambda: (
            (1, 2, 3,),
            (1, 2, "a",),
            (1, 2, None,),
            (1, None, None,),
            (-1, -1, -1,),
            ("a", "b", "c"),
            ("a", "b", None),
            ("a", None, None),
            (None, None, None),
        ),
        resultspec="O",
        argspec='OOO',
        arguments=["PyObject* start", "PyObject* stop", "PyObject* step"],
        cmpfunc=unhandled_error_compare
    )

    if sys.implementation.name == 'graalpy':
        test_GraalPySlice_Start = CPyExtFunction(
            _reference_get_slice('start'),
            lambda: (
                (slice(1),),
                (slice(1, 2, 3),),
                (slice(None, 2, 3),),
                (slice("a"),),
                (tuple(),),
            ),
            resultspec='O',
            argspec='O',
            arguments=["PyObject* slice"],
            cmpfunc=unhandled_error_compare,
        )

        test_GraalPySlice_Stop = CPyExtFunction(
            _reference_get_slice('stop'),
            lambda: (
                (slice(1),),
                (slice(1, 2, 3),),
                (slice(None, 2, 3),),
                (slice("a"),),
                (tuple(),),
            ),
            resultspec='O',
            argspec='O',
            arguments=["PyObject* slice"],
            cmpfunc=unhandled_error_compare,
        )

        test_GraalPySlice_Step = CPyExtFunction(
            _reference_get_slice('step'),
            lambda: (
                (slice(1),),
                (slice(1, 2, 3),),
                (slice(None, 2, 3),),
                (slice("a"),),
                (tuple(),),
            ),
            resultspec='O',
            argspec='O',
            arguments=["PyObject* slice"],
            cmpfunc=unhandled_error_compare,
        )
