import os
import mx

py = ".py"
pathBench = "graalpython/benchmarks/src/benchmarks/"
pathMicro = "graalpython/benchmarks/src/micro/"
pathInterop = "graalpython/benchmarks/src/interop/"


def _compile_interop():
    cc = os.path.join(mx.suite('graalpython').dir, 'graalpython', 'bin', 'sulongcc')
    fp = os.path.join(mx.suite('graalpython').dir, pathInterop)
    src = "%s/cextmodule.c" % fp
    bc = "%s/cextmodule.bc" % fp
    if os.path.exists(cc):
        if not os.path.exists(bc) or os.stat(src).st_atime > os.stat(bc).st_atime:
            os.system("%s %s 2>/dev/null >/dev/null" % (cc, src))


_compile_interop()

pythonGeneratorBenchmarks = {
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

pythonObjectBenchmarks = {
    'richards3-timed': ['200'],
    'bm-float-timed': ['1000'],
    'pypy-chaos-timed': ['1000'],
    'pypy-go-timed': ['50'],
    'pypy-deltablue': ['2000'],
}

pythonBenchmarks = {
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
# TODO: commented out benchmarks are probably too slow atm, revisit this at a later time once performance picks up
pythonMicroBenchmarks = {
    'arith-binop': [],
    'attribute-access': [],
    'attribute-access-polymorphic': [],
    # 'attribute-bool': [],
    # 'boolean-logic': [],
    'builtin-len': [],
    'builtin-len-tuple': [],
    'call-method-polymorphic': [],
    'for-range': [],
    'function-call': [],
    'generator': [],
    'generator-notaligned': [],
    'generator-expression': [],
    'genexp-builtin-call': [],
    'list-comp': [],
    'list-indexing': [],
    'list-iterating': [],
    'math-sqrt': [],
    # 'object-allocate': [],
    # 'object-layout-change': [],
    # 'special-add': [],
    # 'special-add-int': [],
    # 'special-len': [],
}

# XXX: testing
# pythonBenchmarks = {
#     'binarytrees3t'                 : ['8'],
#     'mandelbrot3t'                  : ['300'],
#     'ai-nqueen-timed'               : ['5'],
# }
# pythonMicroBenchmarks = {
#     'arith-binop'                   : [],
#     'for-range'                     : [],
# }

pythonInteropBenchmarks = {
    'cext-modulo': [],
    'for-range-cext': [],
}

# helper list

benchmarks_list = {
    "normal": [pathBench, pythonBenchmarks],
    "micro": [pathMicro, pythonMicroBenchmarks],
    "generator": [pathBench, pythonGeneratorBenchmarks],
    "object": [pathBench, pythonObjectBenchmarks],
    "interop": [pathInterop, pythonInteropBenchmarks],
}
