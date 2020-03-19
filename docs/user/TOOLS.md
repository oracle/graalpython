# Tooling for Python on GraalVM

We will try to support all the Truffle tools in a way the user would expect.

### Debugger

To enable debugging, pass the `--inspect` option to the `graalpython`
launcher. For example:

    $ graalpython --inspect -c "breakpoint(); import os; os.exit()"
    Debugger listening on port 9229.
    To start debugging, open the following URL in Chrome:
        chrome-devtools://devtools/bundled/js_app.html?ws=127.0.1.1:9229/76fcb6dd-35267eb09c3

As you see, the standard Python built-in `breakpoint()` will work with our
debugger. You can inspect variables, set watch expressions, interactively
evaluate code snippets etc. However, this only works if you pass `--inspect` or
some other inspect option. Otherwise, `pdb` is triggered as on CPython (and
doesn't currently work).

### Coverage

We are going to support the `trace` module API, but using the Truffle *coverage*
tool.

### Profiling

We are going to support the `cProfile` module API, but using the Truffle
*cpusampler* tool
