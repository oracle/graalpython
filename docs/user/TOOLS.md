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

GraalVM comes with a coverage instrument that can be used with `--coverage`. See
the commandline help for more options on how to use it. In order to work better
with existing Python code, we also partially support the standard library
`trace` module with this low-overhead GraalVM coverage instrument. So you can do
this:

    $ graalpython -m trace -m -c -s my_script.py

And this will work similarly to how it will run on CPython. The programmatic API
also works, with some limitations. For example, we do not currently track calls,
only line counts and called functions.

### Profiling

We are going to support the `cProfile` module API, but using the Truffle
*cpusampler* tool
