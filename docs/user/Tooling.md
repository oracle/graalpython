---
layout: docs-experimental
toc_group: python
link_title: Tooling Support for Python
permalink: /reference-manual/python/Tooling/
---
# Tooling Support for Python
GraalPy can run many standard Python tools as well as tools from the GraalVM ecosystem.
The `graalpy --help:tools` command will give you more information about GraalVM tools currently supported on Python.

## Debugger
The built-in `breakpoint()` function uses `pdb` by default.

### PDB
The standard python debugger `pdb` is supported on GraalPy. Refer to the official [PDB documentation](https://docs.python.org/3/library/pdb.html) for usage.

### Chrome Inspector
To enable [GraalVM's Chrome Inspector](https://github.com/oracle/graal/blob/master/docs/tools/chrome-debugger.md) debugger, pass the `--inspect` option to the `graalpy` launcher.
The built-in `breakpoint()` function will work using the Chrome Inspector implementation.

## Code Coverage

GraalPy comes with a coverage instrument that can be used with the `--coverage` option.
Use the `graalpy --help:tools` command to see usage details.

In order to work better with existing Python code, the standard library `trace` module is partially supported with this low-overhead coverage instrument.
So, for example, you trace a script as follows:

```bash
graalpy -m trace -m -c -s my_script.py
```

This works in the same way as CPython.

The programmatic API also works, with some limitations.
For example, it does not currently track calls, only line counts and called functions.

## Profiling

The `_lsprof` built-in module is implemented using the GraalVM `cpusampler` tool.
Not all profiling features are currently supported, but basic profiling works. For example:

```bash
graalpy -m cProfile -s calls -m ginstall --help
```

The interactive exploration of a stats output file also works:

```bash
graalpy -m cProfile -o ginstall.profile -m ginstall --help
graalpy -m pstats ginstall.profile
ginstall.profile%
callers
[...]
```

The `profile` module works as well:

```bash
graalpy -m profile -s calls -m ginstall --help
```

or

```bash
>>> import profile
>>> profile.run('l = []; l.append(1)')
         5 function calls in 0.002 seconds

   Ordered by: standard name

   ncalls  tottime  percall  cumtime  percall filename:lineno(function)
        1    0.000    0.000    0.000    0.000 :0(_setprofile)
        1    0.000    0.000    0.000    0.000 :0(append)
        1    0.000    0.000    0.001    0.001 :0(exec)
        1    0.000    0.000    0.000    0.000 <string>:1(<module>)
        1    0.001    0.001    0.002    0.002 profile:0(l = []; l.append(1))
        0    0.000             0.000          profile:0(profiler)
```
