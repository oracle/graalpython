# Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
_BASE_PATH = os.path.join(_graalpython_suite.dir, 'graalpython', 'benchmarks', 'src')
HARNESS_PATH = os.path.join(_BASE_PATH, 'harness.py')

PATH_BENCH = os.path.join(_BASE_PATH, 'benchmarks')
PATH_MICRO = os.path.join(_BASE_PATH, 'micro')
PATH_MESO = os.path.join(_BASE_PATH, 'meso')
PATH_MACRO = os.path.join(_BASE_PATH, 'macro')

# TODO: add/enable interop benchmarks
# PATH_INTEROP = os.path.join(_BASE_PATH, 'interop')
#
#
# def _compile_interop():
#     cc = os.path.join(_graalpython_suite.dir, 'graalpython', 'bin', 'sulongcc')
#     fp = os.path.join(_graalpython_suite.dir, PATH_INTEROP)
#     src = "%s/cextmodule.c" % fp
#     bc = "%s/cextmodule.bc" % fp
#     if os.path.exists(cc):
#         if not os.path.exists(bc) or os.stat(src).st_atime > os.stat(bc).st_atime:
#             os.system("%s %s 2>/dev/null >/dev/null" % (cc, src))
#
#
# _compile_interop()

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
    'arith-binop': ITER_25 + ['5'],
    'arith-modulo': ITER_25 + ['50'],
    'attribute-access-polymorphic': ITER_10 + ['1000'],
    'attribute-access': ITER_25 + ['5000'],
    'attribute_access_super': ITER_25 + ['500'],
    'attribute-bool': ITER_25 + ['3000'],
    'boolean-logic': ITER_15 + ['1000'],
    'builtin-len-tuple': ITER_10 + [],
    'builtin-len': ITER_25 + [],
    'class_access': ITER_25 + ['1000'],
    'call-method-polymorphic': ITER_10 + ['1000'],
    'for-range': ITER_25 + ['50000'],
    'function-call': ITER_25 + [],
    'generator-expression': ITER_25 + [],
    'generator-notaligned': ITER_25 + [],
    'generator': ITER_25 + [],
    'genexp-builtin-call': ITER_25 + ['1000'],
    'list-comp': ITER_15 + ['5000'],
    'list-indexing': ITER_15 + ['1000000'],
    'list-iterating-explicit': ITER_25 + ['1000000'],
    'list-iterating': ITER_25 + ['1000000'],
    'list-iterating-obj': ITER_15 + ['50000000'],
    'math-sqrt': ITER_15 + ['500000000'],
    'object-allocate': ITER_10 + ['5000'],
    'object-layout-change': ITER_15 + ['1000000'],
    'special-add-int': ITER_15 + ['5'],
    'special-add': ITER_15 + ['5'],
    'special-len': ITER_10 + ['5'],
    'member_access': ITER_10 + ['5'],
    'magic-bool': ITER_10 + ['100000000'],
    'magic-iter': ITER_10 + ['50000000'],
    'instantiation': ITER_10 + ['50000000'],
    'call-classmethod': ITER_15 + ['50000000'],
    'mmap-anonymous': ITER_15 + ['1000'],
    'mmap-file': ITER_15 + ['1000'],
    'generate-functions': ITER_15 + ['10000000'],
    'try-except': ITER_15 + ['1000000'],
    'try-except-store': ITER_15 + ['1000000'],
}


MICRO_NATIVE_BENCHMARKS = {
    'c_member_access': ITER_25 + ['5'],
    'c-list-iterating-obj': ITER_15 + ['50000000'],
    'c-magic-bool': ITER_10 + ['100000000'],
    'c-magic-iter': ITER_10 + ['50000000'],
    'c-instantiation': ITER_10 + ['50000000'],
    'c_arith-binop': ITER_25 + ['5'],
    'c_arith_binop_2': ITER_25 + ['50'],
    'c-call-classmethod': ITER_15 + ['50000000']
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
    'pads-bipartite': ITER_10 + ['10000'],
    'pads-lyndon': ITER_10 + ['10000000'],
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
    'sieve': ITER_25 + ['100000'],
    'image-magix': ITER_10 + ['10000'],
    'parrot-b2': ITER_10 + ['200'],
    # 'threadring': ITER_10 + ['100'],  # TODO: provide itertools cycle implementation
    # 'regexdna': ITER_10 + [],  #  TODO: provide proper input for this benchmark
    # 'knucleotide': ITER_10 + [],  #  TODO: provide proper input for this benchmark
}


MACRO_BENCHMARKS = {
    'gcbench': ITER_10 + ['10'],
}

# INTEROP_BENCHMARKS = {
#     'cext-modulo': [],
#     'for-range-cext': [],
# }

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
    # "interop": [PATH_INTEROP, INTEROP_BENCHMARKS],
}

