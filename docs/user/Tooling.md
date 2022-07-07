---
layout: docs-experimental
toc_group: python
link_title: Tooling Support for Python
permalink: /reference-manual/python/Tooling/
---
# Tooling Support for Python

GraalVM's Python runtime is incomplete and cannot launch the standard Python debugger `pdb`.
However, it can run the tools that GraalVM provides.
The `graalpy --help:tools` command will give you more information about tools currently supported on Python.

## Debugger

To enable debugging, pass the `--inspect` option to the `graalpy` launcher.
For example:
```shell
graalpy --inspect -c "breakpoint(); import os; os.exit()"
Debugger listening on port 9229.
To start debugging, open the following URL in Chrome:
    chrome-devtools://devtools/bundled/js_app.html?ws=127.0.1.1:9229/76fcb6dd-35267eb09c3
```

The standard Python built-in `breakpoint()` will work using the [GraalVM's Chrome Inspector](https://github.com/oracle/graal/blob/master/docs/tools/chrome-debugger.md) implementation.
You can inspect variables, set watch expressions, interactively evaluate code snippets, etc.
However, this only works if you pass `--inspect` or some other inspect option. Otherwise, `pdb` is triggered as on CPython (and does not currently work).

## Code Coverage

GraalVM comes with a coverage instrument that can be used with `--coverage`.
Use the `graalpy --help:tools` command to see details on how to use it.

In order to work better with existing Python code, the standard library `trace` module is partially supported with this low-overhead GraalVM coverage instrument.
So you can do this:
```shell
graalpy -m trace -m -c -s my_script.py
```

This will work similarly to how it would run on CPython.

The programmatic API also works, with some limitations.
For example, it does not currently track calls, only line counts and called functions.

## Profiling

The `_lsprof` built-in module has been implemented using the GraalVM `cpusampler` tool.
Not all profiling features are currently supported, but basic profiling works:
```shell
graalpy -m cProfile -s sort -m ginstall --help
```

The interactive exploration of a stats output file also works:
```shell
graalpy -m cProfile -o ginstall.profile -m ginstall --help
graalpy -m pstats ginstall.profile
ginstall.profile%
callers
[...]
```
