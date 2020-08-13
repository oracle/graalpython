# Tooling Support for Python

The GraalVM's Python implementation is incomplete and cannot launch the standard Python
debugger `pdb`. However, it can run the tools that GraalVM provides.
The `graalpython --help:tools` command will give you more information
about tools currently supported on Python.

### Debugger
To enable debugging, pass the `--inspect` option to the `graalpython`
launcher. For example:
```
$ graalpython --inspect -c "breakpoint(); import os; os.exit()"
Debugger listening on port 9229.
To start debugging, open the following URL in Chrome:
    chrome-devtools://devtools/bundled/js_app.html?ws=127.0.1.1:9229/76fcb6dd-35267eb09c3
```
As you see, the standard Python built-in `breakpoint()` will work with our
debugger. You can inspect variables, set watch expressions, interactively
evaluate code snippets etc. However, this only works if you pass `--inspect` or
some other inspect option. Otherwise, `pdb` is triggered as on CPython (and
does not currently work).

### Coverage
GraalVM comes with a coverage instrument that can be used with `--coverage`. The
the `graalpython --help:tools` command line help for more details on how to use
it. In order to work better with existing Python code, we also partially support
the standard library `trace` module with this low-overhead GraalVM coverage
instrument. So you can do this:
```
$ graalpython -m trace -m -c -s my_script.py
```
This will work similarly to how it will run on CPython. The programmatic API
also works, with some limitations. For example, it does not currently track calls,
only line counts and called functions.

### Profiling
The `_lsprof` built-in module has been implemented using the GraalVM `cpusampler`
tool. Not all profiling features are currently supported, but basic profiling
works:
```
$ graalpython -m cProfile -s sort -m ginstall --help
```
The interactive exploration of a stats output file also works:
```
$ graalpython -m cProfile -o ginstall.profile -m ginstall --help
$ graalpython -m pstats ginstall.profile
ginstall.profile%
callers
[...]
```
