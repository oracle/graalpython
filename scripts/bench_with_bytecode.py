import os, subprocess, time


# benchmarkfile = "graalpython/com.oracle.graal.python.benchmarks/python/meso/richards3.py"
# benchmarkfile = "graalpython/com.oracle.graal.python.benchmarks/python/micro/arith-binop.py"
benchmarkfile = "graalpython/com.oracle.graal.python.benchmarks/python/micro-small/arith-binop.py"
iterations = 1000
benchmark_argument = 1


assert os.path.exists(benchmarkfile)
assert "3.8.5" in subprocess.getoutput("/usr/bin/python3 -V"), "You need Python 3.8.5 in /usr/bin/python (or modify GraalPython's lib-graalpython/__graalpython__.py code to call some 3.8.5 binary and disable this assertion)"


code = __graalpython__.compile("", benchmarkfile, "pyc")
module = {}
exec(code, module, module)
bench_func = module["__benchmark__"]


for i in range(iterations):
    s = time.time()
    print(f"Iterations #{i}: ", end="", flush=True)
    bench_func(benchmark_argument)
    print(time.time() - s)
