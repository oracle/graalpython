# Python Command Line Options

Python is run using the `graalpython [option] ... (-c cmd | file) [arg] ...` command
sequence and supports some of the same options as the standard Python
interpreter including, but not limited to:
   * `-c cmd`: pass a program as String and terminate options list
   * `-h`, `--help`: print the help message and exit
   * `-i`, `PYTHONINSPECT=x`: inspect interactively after running a script and force a prompt even if
     `stdin` does not appear to be a terminal
   * `-V`, `--version`: print the Python version number and exit
   * `file`: read a program from the script file
   * `arg ...`: arguments passed to program in `sys.argv[1:]`

The following options are mostly useful for developers of the language or to
provide bug reports:
   * `--python.CoreHome=<String>`: The path to the core library of Python
     that is written in Python. This usually resides in a folder
     `lib-graalpython` in the GraalVM distribution.
   * `--python.StdLibHome=<String>`: The path to the standard library that
     Python will use. Usually this is in a under `lib-python/3` in the
     GraalVM distribution, but any Python 3.7 standard library location may work.
   * `--python.WithJavaStacktrace`: Prints a Java-level stack trace besides the
     normal Python stack when errors occur.

There are a few other debugging options used by the developers of GraalVM,
but these change frequently and may not do anything at any given point in time,
so any observed effects of them should not be relied upon.
