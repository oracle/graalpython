# Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
_BASE_PATH = os.path.join('graalpython', 'benchmarks', 'src')
HARNESS_PATH = os.path.join(_BASE_PATH, 'harness.py')

PATH_BENCH = os.path.join(_BASE_PATH, 'benchmarks')
PATH_MICRO = os.path.join(_BASE_PATH, 'micro')
PATH_MACRO = os.path.join(_BASE_PATH, 'macro')
PATH_MESO = os.path.join(_BASE_PATH, 'meso')

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

GENERATOR_BENCHMARKS = {
    'euler31-timed': ['200'],
    'euler11-timed': ['10000'],
    'ai-nqueen-timed': ['10'],
    'pads-eratosthenes-timed': ['100000'],
    'pads-integerpartitions': ['700'],
    'pads-lyndon': ['100000000'],
    'python-graph-bench': ['200'],
    'simplejson-bench': ['10000'],
    # 'whoosh-bench'    : '5000',
    # 'pymaging-bench'  : '5000',
    # 'sympy-bench'     : '20000',
}

OBJECT_BENCHMARKS = {
    'richards3-timed': ['200'],
    'bm-float-timed': ['1000'],
    'pypy-chaos-timed': ['1000'],
    'pypy-go-timed': ['50'],
    'pypy-deltablue': ['2000'],
}

PYTHON_BENCHMARKS = {
    'binarytrees3t': ['18'],
    'fannkuchredux3t': ['11'],
    'fasta3t': ['25000000'],
    'mandelbrot3t': ['4000'],
    'meteor3t': ['2098'],
    'nbody3t': ['5000000'],
    'spectralnorm3t': ['3000'],
    'pidigits-timed': ['0'],
    'euler31-timed': ['200'],
    'euler11-timed': ['10000'],
    'ai-nqueen-timed': ['10'],
    'pads-eratosthenes-timed': ['100000'],
    'pads-integerpartitions': ['700'],
    'pads-lyndon': ['100000000'],
    'richards3-timed': ['200'],
    'bm-float-timed': ['1000'],
    'pypy-chaos-timed': ['1000'],
    'pypy-go-timed': ['50'],
    'pypy-deltablue': ['2000'],
    'python-graph-bench': ['200'],
    'simplejson-bench': ['10000'],
    'sieve': ['100000'],
    # 'whoosh-bench'    : '5000',
    # type not supported to adopt to Jython! <scoring.WeightScorer...
    # 'pymaging-bench'  : '5000',
    # Multiple super class is not supported yet! + File "JYTHON.jar/Lib/abc.py", line 32, in abstractmethod
    #   AttributeError: 'str' object has no attribute '__isabstractmethod__'
    # 'sympy-bench'     : '20000',
    # ImportError: No module named core
}

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
    'attribute-bool': ITER_25 + ['3000'],
    'boolean-logic': ITER_15 + ['1000'],
    'builtin-len-tuple': ITER_10 + [],
    'builtin-len': ITER_25 + [],
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
    'math-sqrt': ITER_15 + ['500000000'],
    'object-allocate': ITER_10 + ['5000'],
    'object-layout-change': ITER_15 + ['1000000'],
    'special-add-int': ITER_15 + ['5'],
    'special-add': ITER_15 + ['5'],
    'special-len': ITER_10 + ['5'],
}

MACRO_BENCHMARKS = {
    'gcbench': ITER_10 + ['10'],
}

MESO_BENCHMARKS = {
}

# pythonInteropBenchmarks = {
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
    "meso": [PATH_MESO, MESO_BENCHMARKS],
    "macro": [PATH_MACRO, MACRO_BENCHMARKS],


    "normal": [PATH_BENCH, PYTHON_BENCHMARKS],
    "generator": [PATH_BENCH, GENERATOR_BENCHMARKS],
    "object": [PATH_BENCH, OBJECT_BENCHMARKS],
    # "interop": [pathInterop, pythonInteropBenchmarks],
}
