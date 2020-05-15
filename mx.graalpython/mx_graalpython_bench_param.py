# Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import mx

_graalpython_suite = mx.suite('graalpython')

py = ".py"
_BASE_PATH = os.path.join(_graalpython_suite.dir, 'graalpython', 'com.oracle.graal.python.benchmarks', 'python')
HARNESS_PATH = os.path.join(_BASE_PATH, 'harness.py')

PATH_BENCH = os.path.join(_BASE_PATH, 'com.oracle.graal.python.benchmarks')
PATH_MICRO = os.path.join(_BASE_PATH, 'micro')
PATH_MESO = os.path.join(_BASE_PATH, 'meso')
PATH_MACRO = os.path.join(_BASE_PATH, 'macro')
PATH_INTEROP = os.path.join(_BASE_PATH, 'host_interop')

# ----------------------------------------------------------------------------------------------------------------------
#
# the python micro benchmarks
#
# ----------------------------------------------------------------------------------------------------------------------
# the argument list contains both the harness and benchmark args
ITER_25 = ['-i', '25']
ITER_15 = ['-i', '15']
ITER_10 = ['-i', '10']

MICRO_BENCHMARKS = {
    'arith-binop': ITER_10 + ['5'],
    'arith-modulo-sized': ITER_10 + ['500'],
    'attribute-access-polymorphic': ITER_10 + ['1000'],
    'attribute-access': ITER_10 + ['5000'],
    'attribute-access-super': ITER_10 + ['5_000'],
    'attribute-bool': ITER_10 + ['3000'],
    'boolean-logic-sized': ITER_10 + ['5_000'],
    'builtin-len-tuple-sized': ITER_10 + ['1_000_000_000'],
    'builtin-len': ITER_10 + [],
    'class-access': ITER_15 + ['10_000'],
    'call-method-polymorphic': ITER_10 + ['1000'],
    'for-range': ITER_15 + ['50000'],
    'function-call-sized': ITER_10 + ['2_000_000_000'],
    'generator-expression-sized': ITER_10 + ['30_000'],
    'generator-notaligned-sized': ITER_10 + ['30_000'],
    'generator-sized': ITER_10 + ['30_000'],
    'genexp-builtin-call-sized': ITER_10 + ['50_000'],
    'list-comp': ITER_10 + ['5000'],
    'list-indexing': ITER_10 + ['1000000'],
    'list-indexing-from-constructor': ITER_10 + ['10000000'],
    'list-indexing-from-literal': ITER_10 + ['10000000'],
    'list-iterating-explicit': ITER_10 + ['1000000'],
    'list-iterating': ITER_10 + ['1000000'],
    'list-iterating-obj-sized': ITER_10 + ['100_000_000'],
    'list-constructions-sized': ITER_10 + ['10_000'],
    'dict-getitem-sized': ITER_10 + ['50_000_000'],
    'math-sqrt': ITER_10 + ['500000000'],
    'object-allocate': ITER_10 + ['5000'],
    'object-layout-change': ITER_10 + ['1000000'],
    'special-add-int-sized': ITER_10 + ['20_000'],
    'special-add-sized': ITER_10 + ['20_000'],
    'special-len': ITER_10 + ['5'],
    'member-access': ITER_10 + ['5000'],
    'magic-bool-sized': ITER_10 + ['300_000_000'],
    'magic-iter': ITER_10 + ['50000000'],
    'instantiation': ITER_10 + ['50000000'],
    'call-classmethod-sized': ITER_10 + ['500_000_000'],
    'mmap-anonymous-sized': ITER_10 + ['20_000'],
    'mmap-file': ITER_10 + ['1000'],
    'generate-functions-sized': ITER_15 + ['500_000_000'],
    'try-except-sized': ITER_10 + ['100_000_000'],
    'try-except-simple': ITER_10 + ['500_000_000'],
    'try-except-store-sized': ITER_10 + ['100_000_000'],
    'try-except-store-simple': ITER_10 + ['500_000_000'],
    'try-except-store-two-types': ITER_10 + ['100_000_000'],
    'try-except-two-types': ITER_10 + ['100_000_000'],
    'tuple-indexing-from-constructor': ITER_10 + ['10000000'],
    'tuple-indexing-from-literal': ITER_10 + ['10000000'],
}


def _pickling_benchmarks(module='pickle'):
    return {
        '{}-strings'.format(module): ITER_10,
        '{}-lists'.format(module): ITER_10,
        '{}-dicts'.format(module): ITER_10,
        '{}-objects'.format(module): ITER_10,
        '{}-funcs'.format(module): ITER_10,
    }


MICRO_BENCHMARKS.update(_pickling_benchmarks('pickle'))
# MICRO_BENCHMARKS.update(_pickling_benchmarks('cPickle'))


MICRO_NATIVE_BENCHMARKS = {
    'c_member_access': ITER_10 + ['5'],
    'c-list-iterating-obj': ITER_10 + ['50000000'],
    'c-magic-bool': ITER_10 + ['100000000'],
    'c-magic-iter': ITER_10 + ['50000000'],
    'c_arith-binop': ITER_10 + ['5'],
    'c_arith_binop_2': ITER_10 + ['50'],
    'c-call-classmethod': ITER_10 + ['50000000'],
    'c-issubtype-polymorphic-forced-to-native': ITER_10 + ['50000000'],
    'c-issubtype-polymorphic': ITER_10 + ['50000000'],
    'c-issubtype-monorphic': ITER_10 + ['50000000'],
    'c-call-method': ITER_15 + ['5000000'],
    'c-instantiate-large': ITER_15 + ['1000'],
}


MESO_BENCHMARKS = {
    # -------------------------------------------------------
    # generator benchmarks
    # -------------------------------------------------------
    'euler31': ITER_10 + ['200'],
    'euler11': ITER_10 + ['10000'],
    'ai-nqueen': ITER_10 + ['10'],
    'pads-eratosthenes': ITER_10 + ['100000'],
    'pads-integerpartitions': ITER_10 + ['700'],
    'pads-bipartite-sized': ITER_10 + ['100_000'],
    'pads-lyndon': ITER_15 + ['10000000'],
    # -------------------------------------------------------
    # object benchmarks
    # -------------------------------------------------------
    'richards3': ITER_10 + ['200'],
    'bm-float': ITER_10 + ['1000'],
    # -------------------------------------------------------
    # normal benchmarks
    # -------------------------------------------------------
    'binarytrees3': ITER_25 + ['18'],
    'fannkuchredux3': ITER_10 + ['11'],
    'fasta3': ITER_10 + ['25000000'],
    'mandelbrot3': ITER_10 + ['4000'],
    'meteor3': ITER_15 + ['2098'],
    'nbody3': ITER_10 + ['5000000'],
    'spectralnorm3': ITER_10 + ['3000'],
    'pidigits': ITER_10 + [],
    'sieve-sized': ITER_15 + ['500_000'],
    'image-magix': ITER_10 + ['10000'],
    'parrot-b2': ITER_10 + ['200'],
    'threadring': ITER_10 + ['100_000_000'],
    'regexdna': ITER_25 + [],
    'knucleotide': ITER_25 + [],
    'chaos': ITER_10 + ['10'],
    'go': ITER_10 + [],
    'raytrace-simple': ITER_10 + [],
    'lud': ITER_10 + ['512'],
    'mm': ITER_10 + ['10'],
    # Rodinia
    'backprop_rodinia': ITER_10 + ['1048576'],
    'lavaMD_rodinia': ITER_10 + ['32'],
    'pathfinder_rodinia': ITER_10 + ['10'],
    'particlefilter_rodinia': ITER_10 + ['2048'],
    'srad_rodinia': ITER_10 + ['100'],
}


MACRO_BENCHMARKS = {
    'gcbench': ITER_10 + ['10'],
}


INTEROP_BENCHMARKS = {
    'euler_java': ITER_10 + ['200'],
    'image-magix': ITER_10 + ['10000'],
    'image-magix-java': ITER_10 + ['10000'],
}


_INTEROP_JAVA_PACKAGE = 'com.oracle.graal.python.benchmarks.interop.'
INTEROP_JAVA_BENCHMARKS = {
    'richards3': [_INTEROP_JAVA_PACKAGE + 'PyRichards'] + MESO_BENCHMARKS['richards3'],
    'euler31': [_INTEROP_JAVA_PACKAGE + 'PyEuler31'] + MESO_BENCHMARKS['euler31'],
    'euler11': [_INTEROP_JAVA_PACKAGE + 'PyEuler11'] + MESO_BENCHMARKS['euler11'],
    'nbody3': [_INTEROP_JAVA_PACKAGE + 'PyNbody'] + MESO_BENCHMARKS['nbody3'],
    'fannkuchredux3': [_INTEROP_JAVA_PACKAGE + 'PyFannkuchredux'] + MESO_BENCHMARKS['fannkuchredux3'],
}


# ----------------------------------------------------------------------------------------------------------------------
#
# the benchmarks
#
# ----------------------------------------------------------------------------------------------------------------------
BENCHMARKS = {
    "micro": [PATH_MICRO, MICRO_BENCHMARKS],
    "micro-native": [PATH_MICRO, MICRO_NATIVE_BENCHMARKS],
    "meso": [PATH_MESO, MESO_BENCHMARKS],
    "macro": [PATH_MACRO, MACRO_BENCHMARKS],
    "interop": [PATH_INTEROP, INTEROP_BENCHMARKS],
}

JBENCHMARKS = {
    "pyjava": [INTEROP_JAVA_BENCHMARKS],
}
