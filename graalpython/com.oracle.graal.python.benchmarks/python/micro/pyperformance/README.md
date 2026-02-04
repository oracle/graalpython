# Pyperformance benchmark port process for benchmarking with the PolyBench harness

## Brief explanation

These benchmarks have been copied from the pyperformance repository and manually updated  
so that they conform to the PolyBench contract and they don't use any pyperformance/pyperf interface.  
* Remove the usage of `pyperf.Runner` and other `pyperf` functions/classes.
* Move the benchmark workload into the `run` function.
* Define the PolyBench benchmark configuration functions, such as:  
`setup`, `warmupIterations`, `iterations`, `summary`, and `dependencies`.

## Version information

Version: 1.0.6  
Source repository: Acquired with `pip install` (GitHub repo: https://github.com/python/pyperformance/tree/main)

The version was chosen to align with the one used in `graalpython/mx.graalpython/mx_graalpython_python_benchmarks.py::PyPerformanceSuite`.

## Files sourced from the pyperformance project

The benchmarks from the pyperformance repository have the following characteristics  
which do not align with the requirements of the PolyBench harness:
* The benchmark workload is in a `if __name__ == "__main__":` branch.
  * The PolyBench harness expects the benchmark workload to be in the `run` function.
* The workload is passed to a `pyperf.Runner` instance for execution.
  * The PolyBench harness should execute the workload directly.
* The benchmark has a dependency on `pyperf` for performance measurement purposes. This dependency  
is not required for the workload itself.
  * As these dependencies are not essential for the workload but used for auxiliary purposes of the  
  pyperformance harness, they should be eliminated in facilitating benchmarking with the PolyBench harness.

Additionally, there are special functions that the benchmark may implement to configure the PolyBench execution,  
such as: `setup`, `run`, `warmupIterations`, `iterations`, `summary`, and `dependencies`.

To handle these differences, appropriate changes to the source code of the benchmarks must be made.  
These changes are not easy to automate and have therefore been made manually.  
The differences have been catalogued in this section, on a file-by-file basis.  

### bm_pathlib.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_pathlib/run_benchmark.py  
The file has not been named `pathlib.py` as this name would've hidden the `pathlib` package from the standard library.  
Changes made to the original file, as reported by the `diff` tool:
```
11d10
< import shutil
14,15d12
< import pyperf
< 
29c26,28
< def setup(num_files):
---
> tmp_path = None
> def setup():
>     global tmp_path
31c30
<     for fn in generate_filenames(tmp_path, num_files):
---
>     for fn in generate_filenames(tmp_path, NUM_FILES):
35,36d33
<     return tmp_path
< 
49d45
<     t0 = pyperf.perf_counter()
62c58,68
<     return pyperf.perf_counter() - t0
---
> 
> def run():
>     bench_pathlib(8, tmp_path)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
65,77c71,76
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = ("Test the performance of "
<                                       "pathlib operations.")
< 
<     modname = pathlib.__name__
<     runner.metadata['pathlib_module'] = modname
< 
<     tmp_path = setup(NUM_FILES)
<     try:
<         runner.bench_time_func('pathlib', bench_pathlib, tmp_path)
<     finally:
<         shutil.rmtree(tmp_path)
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### bm_pickle.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_pickle/run_benchmark.py  
The file has not been named `pickle.py` as this name would've hidden the `pickle` package from the standard library.  
Changes made to the original file, as reported by the `diff` tool:
```
18,20c18,19
< 
< import pyperf
< IS_PYPY = (pyperf.python_implementation() == 'pypy')
---
> import argparse
> import pickle
87d85
<     t0 = pyperf.perf_counter()
113,114d110
<     return pyperf.perf_counter() - t0
< 
126d121
<     t0 = pyperf.perf_counter()
151,152d145
<     return pyperf.perf_counter() - t0
< 
163d155
<     t0 = pyperf.perf_counter()
178,179d169
<     return pyperf.perf_counter() - t0
< 
187d176
<     t0 = pyperf.perf_counter()
202,203d190
<     return pyperf.perf_counter() - t0
< 
213d199
<     t0 = pyperf.perf_counter()
223,224d208
<     return pyperf.perf_counter() - t0
< 
250,252c234,236
< if __name__ == "__main__":
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.metadata['description'] = "Test the performance of pickling."
---
> def run():
>     __benchmark__(["pickle"])
> 
254c238,239
<     parser = runner.argparser
---
> def __benchmark__(args):
>     parser = argparse.ArgumentParser(prog="python")
262,263c247,248
<     options = runner.parse_args()
<     benchmark, inner_loops = BENCHMARKS[options.benchmark]
---
>     options = parser.parse_args(args)
>     benchmark, _ = BENCHMARKS[options.benchmark]
269c254
<     if not (options.pure_python or IS_PYPY):
---
>     if not options.pure_python:
282,283d266
<     runner.metadata['pickle_protocol'] = str(options.protocol)
<     runner.metadata['pickle_module'] = pickle.__name__
285,286c268,287
<     runner.bench_time_func(name, benchmark,
<                            pickle, options, inner_loops=inner_loops)
---
>     benchmark(512, pickle, options)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
> 
> if __name__ == "__main__":
>     run()
```

### bm_richards.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_richards/run_benchmark.py  
The file has not been named `richards.py` as there is already a `richards.py` in micro benchmarks.  
Changes made to the original file, as reported by the `diff` tool:
```
12,13d11
< import pyperf
< 
418,421c416
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "The Richards benchmark"
< 
---
> def run():
423c418,434
<     runner.bench_func('richards', richard.run, 1)
---
>     richard.run(1)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### chaos.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_chaos/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
8,9c8
< 
< import pyperf
---
> import argparse
238c237
< def main(runner, args):
---
> def __benchmark__(args):
263,268d261
<     runner.metadata['chaos_thickness'] = args.thickness
<     runner.metadata['chaos_width'] = args.width
<     runner.metadata['chaos_height'] = args.height
<     runner.metadata['chaos_iterations'] = args.iterations
<     runner.metadata['chaos_rng_seed'] = args.rng_seed
< 
270,272c263
<     runner.bench_func('chaos', chaos.create_image_chaos,
<                       args.width, args.height, args.iterations,
<                       args.filename, args.rng_seed)
---
>     chaos.create_image_chaos(args.width, args.height, args.iterations, args.filename, args.rng_seed)
284,287c275,276
< if __name__ == "__main__":
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.metadata['description'] = "Create chaosgame-like fractals"
<     cmd = runner.argparser
---
> def run():
>     cmd = argparse.ArgumentParser(prog="python")
308,309c297,314
<     args = runner.parse_args()
<     main(runner, args)
---
>     args = cmd.parse_args()
>     __benchmark__(args)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### fannkuch.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_fannkuch/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
8,9d7
< import pyperf
< 
51,54c49,66
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     arg = DEFAULT_ARG
<     runner.bench_func('fannkuch', fannkuch, arg)
---
> def run():
>     fannkuch(DEFAULT_ARG)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### float.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_float/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
4,5d3
< import pyperf
< 
55,57c53,63
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Float benchmark"
---
> def run():
>     benchmark(POINTS)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
59,60c65,70
<     points = POINTS
<     runner.bench_func('float', benchmark, points)
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### go.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_go/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
7,8d6
< import pyperf
< 
454,461c452,469
< if __name__ == "__main__":
<     kw = {}
<     if pyperf.python_has_jit():
<         # PyPy needs to compute more warmup values to warmup its JIT
<         kw['warmups'] = 50
<     runner = pyperf.Runner(**kw)
<     runner.metadata['description'] = "Test the performance of the Go benchmark"
<     runner.bench_func('go', versus_cpu)
---
> def run():
>     versus_cpu()
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### hexiom.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_hexiom/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
12,13c12
< 
< import pyperf
---
> import argparse
621c620
< def main(loops, level):
---
> def __benchmark__(loops, level):
631d629
<     t0 = pyperf.perf_counter()
639,640d636
<     dt = pyperf.perf_counter() - t0
< 
646,647d641
<     return dt
< 
653,658c647
< if __name__ == "__main__":
<     kw = {'add_cmdline_args': add_cmdline_args}
<     if pyperf.python_has_jit():
<         # PyPy needs to compute more warmup values to warmup its JIT
<         kw['warmups'] = 15
<     runner = pyperf.Runner(**kw)
---
> def run():
660c649,650
<     runner.argparser.add_argument("--level", type=int,
---
>     parser = argparse.ArgumentParser(prog="python")
>     parser.add_argument("--level", type=int,
666,668c656,666
<     args = runner.parse_args()
<     runner.metadata['description'] = "Solver of Hexiom board game"
<     runner.metadata['hexiom_level'] = args.level
---
>     args = parser.parse_args()
>     __benchmark__(16, args.level)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
670c668,673
<     runner.bench_time_func('hexiom', main, args.level)
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### json_dumps.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_json_dumps/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
3,4c3
< 
< import pyperf
---
> import argparse
30,32c29,31
< def main():
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.argparser.add_argument("--cases",
---
> def run():
>     parser = argparse.ArgumentParser(prog="python")
>     parser.add_argument("--cases",
35d33
<     runner.metadata['description'] = "Benchmark json.dumps()"
37c35
<     args = runner.parse_args()
---
>     args = parser.parse_args()
55c53,61
<     runner.bench_func('json_dumps', bench_json_dumps, data)
---
>     bench_json_dumps(data)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
58,59c64,69
< if __name__ == '__main__':
<     main()
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### json_loads.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_json_loads/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
14,16d13
< # Local imports
< import pyperf
< 
97,100c94
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Benchmark json.loads()"
< 
---
> def run():
104a99,108
>     bench_json_loads(objs)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
106c110,115
<     runner.bench_func('json_loads', bench_json_loads, objs, inner_loops=20)
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### meteor_contest.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_meteor_contest/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
13,14d12
< import pyperf
< 
194d191
<     t0 = pyperf.perf_counter()
204,205d200
<     dt = pyperf.perf_counter() - t0
< 
209,214d203
<     return dt
< 
< 
< def main():
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Solver for Meteor Puzzle board"
215a205
> def __benchmark__():
221,222c211,223
<     runner.bench_time_func('meteor_contest', bench_meteor_contest,
<                            board, pieces, solve_arg, fps, se_nh)
---
>     bench_meteor_contest(1, board, pieces, solve_arg, fps, se_nh)
> 
> 
> def run():
>     __benchmark__()
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
225,226c226,231
< if __name__ == "__main__":
<     main()
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### nbody.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_nbody/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
17c17
< import pyperf
---
> import argparse
128d127
<     t0 = pyperf.perf_counter()
135,136d133
<     return pyperf.perf_counter() - t0
< 
142,145c139,141
< if __name__ == '__main__':
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.metadata['description'] = "n-body benchmark"
<     runner.argparser.add_argument("--iterations",
---
> def run():
>     parser = argparse.ArgumentParser(prog="python")
>     parser.add_argument("--iterations",
149c145
<     runner.argparser.add_argument("--reference",
---
>     parser.add_argument("--reference",
154,156c150,167
<     args = runner.parse_args()
<     runner.bench_time_func('nbody', bench_nbody,
<                            args.reference, args.iterations)
---
>     args = parser.parse_args()
>     bench_nbody(1, args.reference, args.iterations)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### nqueens.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_nqueens/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
3,4d2
< import pyperf
< 
57,59c55,65
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Simple, brute-force N-Queens solver"
---
> def run():
>     bench_n_queens(8)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
61,62c67,72
<     queen_count = 8
<     runner.bench_func('nqueens', bench_n_queens, queen_count)
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### pidigits.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_pidigits/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
12,13c12
< 
< import pyperf
---
> import argparse
59,62c58,59
< if __name__ == "__main__":
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
< 
<     cmd = runner.argparser
---
> def run():
>     cmd = argparse.ArgumentParser(prog="python")
67,70c64,81
<     args = runner.parse_args()
<     runner.metadata['description'] = "Compute digits of pi."
<     runner.metadata['pidigits_ndigit'] = args.digits
<     runner.bench_func('pidigits', calc_ndigits, args.digits)
---
>     args = cmd.parse_args()
>     calc_ndigits(args.digits)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### pyflate.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_pyflate/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
23,24d22
< import pyperf
< 
635d632
<     t0 = pyperf.perf_counter()
650d646
<     dt = pyperf.perf_counter() - t0
656,661d651
<     return dt
< 
< 
< if __name__ == '__main__':
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Pyflate benchmark"
662a653
> def run():
665c656,676
<     runner.bench_time_func('pyflate', bench_pyflake, filename)
---
>     bench_pyflake(1, filename)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
> 
> 
> def dependencies():
>     return ["data/interpreter.tar.bz2"]
```

### regex_compile.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_regex_compile/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
11,13c11,12
< 
< # Local imports
< import pyperf
---
> import sys
> import os
39,40c38,40
<         import bm_regex_effbot
<         bm_regex_effbot.bench_regex_effbot(1)
---
>         sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
>         import regex_effbot
>         regex_effbot.bench_regex_effbot(1)
42,43c42,43
<         import bm_regex_v8
<         bm_regex_v8.bench_regex_v8(1)
---
>         import regex_v8
>         regex_v8.bench_regex_v8(1)
53d52
<     t0 = pyperf.perf_counter()
61d59
<     return pyperf.perf_counter() - t0
62a61,63
> def run():
>     regexes = capture_regexes()
>     bench_regex_compile(1, regexes)
64,66d64
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Test regex compilation performance"
68,69c66,83
<     regexes = capture_regexes()
<     runner.bench_time_func('regex_compile', bench_regex_compile, regexes)
---
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
> 
> 
> def dependencies():
>     return ["regex_effbot.py", "regex_v8.py"]
```

### regex_dna.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_regex_dna/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
20,21c20
< 
< import pyperf
---
> import argparse
189d187
<     t0 = pyperf.perf_counter()
194d191
<     dt = pyperf.perf_counter() - t0
198,199d194
<     return dt
< 
206,212c201,202
< if __name__ == '__main__':
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.metadata['description'] = ("Test the performance of regexps "
<                                       "using benchmarks from "
<                                       "The Computer Language Benchmarks Game.")
< 
<     cmd = runner.argparser
---
> def run():
>     cmd = argparse.ArgumentParser(prog="python")
220c210
<     args = runner.parse_args()
---
>     args = cmd.parse_args()
229,231d218
<     runner.metadata['regex_dna_fasta_len'] = args.fasta_length
<     runner.metadata['regex_dna_rng_seed'] = args.rng_seed
< 
236c223,239
<     runner.bench_time_func('regex_dna', bench_regex_dna, seq, expected_res)
---
>     bench_regex_dna(1, seq, expected_res)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### regex_effbot.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_regex_effbot/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
16,18c16
< 
< # Local imports
< import pyperf
---
> import argparse
135d132
<     t0 = pyperf.perf_counter()
152,153d148
<     return pyperf.perf_counter() - t0
< 
164,168c159,161
< if __name__ == '__main__':
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.metadata['description'] = ("Test the performance of regexps "
<                                       "using Fredik Lundh's benchmarks.")
<     runner.argparser.add_argument("-B", "--force_bytes", action="store_true",
---
> def run():
>     parser = argparse.ArgumentParser(prog="python")
>     parser.add_argument("-B", "--force_bytes", action="store_true",
170c163
<     options = runner.parse_args()
---
>     options = parser.parse_args()
174,175c167,183
<     runner.bench_time_func('regex_effbot', bench_regex_effbot,
<                            inner_loops=10)
---
>     bench_regex_effbot(4)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### regex_v8.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_regex_v8/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
45,47d44
< # Third party imports
< import pyperf
< 
1769d1765
<     t0 = pyperf.perf_counter()
1783d1778
<     return pyperf.perf_counter() - t0
1786,1790c1781,1798
< if __name__ == '__main__':
<     runner = pyperf.Runner()
<     runner.metadata['description'] = ("Test the performance of regexps "
<                                       "using V8's benchmarks")
<     runner.bench_time_func('regex_v8', bench_regex_v8)
---
> def run():
>     bench_regex_v8(8)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### scimark.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_scimark/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
3,4c3
< 
< import pyperf
---
> import argparse
155d153
<     t0 = pyperf.perf_counter()
161,162d158
<     return pyperf.perf_counter() - t0
< 
166d161
<     t0 = pyperf.perf_counter()
175,176d169
<     return pyperf.perf_counter() - t0
< 
214d206
<     t0 = pyperf.perf_counter()
219,220d210
<     return pyperf.perf_counter() - t0
< 
263d252
<     t0 = pyperf.perf_counter()
268,269d256
<     return pyperf.perf_counter() - t0
< 
375d361
<     t0 = pyperf.perf_counter()
383,384d368
<     return pyperf.perf_counter() - t0
< 
393,397c377,381
<     'sor': (bench_SOR, 100, 10, Array2D),
<     'sparse_mat_mult': (bench_SparseMatMult, 1000, 50 * 1000),
<     'monte_carlo': (bench_MonteCarlo, 100 * 1000,),
<     'lu': (bench_LU, 100,),
<     'fft': (bench_FFT, 1024, 50),
---
>     'sor': (bench_SOR, 1, 100, 1, Array2D),
>     'sparse_mat_mult': (bench_SparseMatMult, 4, 1000, 50 * 1000),
>     'monte_carlo': (bench_MonteCarlo, 2, 10 * 1000,),
>     'lu': (bench_LU, 1, 50,),
>     'fft': (bench_FFT, 1, 1024, 5),
401,403c385,387
< if __name__ == "__main__":
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.argparser.add_argument("benchmark", nargs='?',
---
> def run():
>     parser = argparse.ArgumentParser(prog="python")
>     parser.add_argument("benchmark", nargs='?',
406c390
<     args = runner.parse_args()
---
>     args = parser.parse_args()
413,415c397,414
<         name = 'scimark_%s' % bench
<         args = BENCHMARKS[bench]
<         runner.bench_time_func(name, *args)
---
>         func, *func_args = BENCHMARKS[bench]
>         func(*func_args)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### spectral_norm.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_spectral_norm/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
15,16d14
< import pyperf
< 
51d48
<     t0 = pyperf.perf_counter()
66c63,73
<     return pyperf.perf_counter() - t0
---
> 
> def run():
>     bench_spectral_norm(1)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
69,74c76,81
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = (
<         'MathWorld: "Hundred-Dollar, Hundred-Digit Challenge Problems", '
<         'Challenge #3.')
<     runner.bench_time_func('spectral_norm', bench_spectral_norm)
---
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### telco.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_telco/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
23,24d22
< import pyperf
< 
44d41
<     start = pyperf.perf_counter()
81d77
<     return pyperf.perf_counter() - start
82a79,81
> def run():
>     filename = rel_path("data", "telco-bench.b")
>     bench_telco(1, filename)
84,86d82
< if __name__ == "__main__":
<     runner = pyperf.Runner()
<     runner.metadata['description'] = "Telco decimal benchmark"
88,89c84,101
<     filename = rel_path("data", "telco-bench.b")
<     runner.bench_time_func('telco', bench_telco, filename)
---
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
> 
> 
> def dependencies():
>     return ["data/telco-bench.b"]
```

### unpack_sequence.py
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_unpack_sequence/run_benchmark.py
Changes made to the original file, as reported by the `diff` tool:
```
3c3
< import pyperf
---
> import argparse
8d7
<     t0 = pyperf.perf_counter()
413,414d411
<     return pyperf.perf_counter() - t0
< 
418c415
<     return do_unpacking(loops, x)
---
>     do_unpacking(loops, x)
424c421
<     return do_unpacking(loops, x)
---
>     do_unpacking(loops, x)
428,430c425,426
<     dt1 = bench_tuple_unpacking(loops)
<     dt2 = bench_list_unpacking(loops)
<     return dt1 + dt2
---
>     bench_tuple_unpacking(loops)
>     bench_list_unpacking(loops)
438c434
< if __name__ == "__main__":
---
> def run():
442,446c438,439
<     runner = pyperf.Runner(add_cmdline_args=add_cmdline_args)
<     runner.metadata['description'] = ("Microbenchmark for "
<                                       "Python's sequence unpacking.")
< 
<     runner.argparser.add_argument("benchmark", nargs="?",
---
>     parser = argparse.ArgumentParser(prog="python")
>     parser.add_argument("benchmark", nargs="?",
449c442
<     options = runner.parse_args()
---
>     options = parser.parse_args()
457c450,466
<     runner.bench_time_func(name, func, inner_loops=400)
---
>     func(8192)
> 
> 
> def warmupIterations():
>     return 0
> 
> 
> def iterations():
>     return 10
> 
> 
> def summary():
>     return {
>         "name": "OutlierRemovalAverageSummary",
>         "lower-threshold": 0.0,
>         "upper-threshold": 1.0,
>     }
```

### data/interpreter.tar.bz2
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_pyflate/data/interpreter.tar.bz2  
Data file used for the `pyflate.py` benchmark.  
No modifications made.

### data/telco-bench.b
Original file path: pyperformance/pyperformance/data-files/benchmarks/bm_telco/data/telco-bench.b  
Data file used for the `telco.py` benchmark.  
No modifications made.

## Original files necessary for benchmarking with the PolyBench harness

These are new files that do not have a corresponding match in the numpy repository.  
These files are generated to conform to the expectations of the PolyBench harness.  

### pickle_dict.py
Invokes the benchmark implemented in `bm_pickle.py` with the `["pickle_dict"]` argument value.  
This file is the PolyBench equivalent of the pyperformance benchmark variant defined in the  
`pyperformance/pyperformance/data-files/benchmarks/bm_pickle/bm_pickle_dict.toml` file.

### pickle_list.py
Invokes the benchmark implemented in `bm_pickle.py` with the `["pickle_list"]` argument value.  
This file is the PolyBench equivalent of the pyperformance benchmark variant defined in the  
`pyperformance/pyperformance/data-files/benchmarks/bm_pickle/bm_pickle_list.toml` file.

### unpickle_list.py
Invokes the benchmark implemented in `bm_pickle.py` with the `["unpickle_list"]` argument value.  
This file is the PolyBench equivalent of the pyperformance benchmark variant defined in the  
`pyperformance/pyperformance/data-files/benchmarks/bm_pickle/bm_unpickle_list.toml` file.

### unpickle.py
Invokes the benchmark implemented in `bm_pickle.py` with the `["unpickle"]` argument value.  
This file is the PolyBench equivalent of the pyperformance benchmark variant defined in the  
`pyperformance/pyperformance/data-files/benchmarks/bm_pickle/bm_unpickle.toml` file.
