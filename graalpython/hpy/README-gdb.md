How to debug HPy on CPython
============================

This document describes how to make debugging easier when running HPy on
CPython. At the moment it is structured as a collection of notes, PRs to make
it more structured are welcome.

**NOTE**: this document is **not** about the HPy debug mode, but it's about
debugging HPy itself.


Build a debug version of CPython
---------------------------------

This is highly recommended. It takes only few minutes and helps a lot during
debugging for two reasons:

1. You can easily view CPython's source code inside GDB

2. CPython is compiled with `-Og`, which means it will be easier to follow the
   code step-by-step and to inspect variables

3. The `py-*` GDB commands work out of the box.

```
$ cd /path/to/cpython
$ git checkout v3.8.2
$ ./configure --with-pydebug --prefix=/opt/python-debug/
$ make install
```

Enable `python-gdb.py`
----------------------

`python-gdb.py` is a GDB script to make it easier to inspect the state of a
Python process from whitin GDB. It is documented
[in the CPython's dev guide](https://devguide.python.org/gdb/).

The script add a series of GBD commands such as:

  - `py-bt`: prints the Python-level traceback of the current function

  - `py-up`, `py-down`: navigate up and down the Python function stack by
    going to the previous/next occurrence of `_PyEval_EvalFrameDefault`. They
    are more or less equivalent to `up` and `down` inside `pdb`.

  - `py-print`: print the value of a Python-level variable

**WARNING**: the CPython's dev guide suggests to add `add-auto-load-safe-path`
to your `~/.gdbinit`, but it doesn't work for me. What works for me is the
following:

```
# add this to your ~/.gdbinit
source /path/to/cpython/python-gdb.py
```

To check that the `py-*` commands work as expected, you can do the following:

```
$ gdb --args /opt/python-debug/bin/python3 -c 'for i in range(10000000): pass'
GNU gdb (Ubuntu 9.2-0ubuntu1~20.04) 9.2
...
(gdb) run # start the python process
...
<PRESS CTRL-C TO ENTER GDB>
^C
...
(gdb) py-bt
Traceback (most recent call first):
  File "<string>", line 1, in <module>
(gdb) py-print i
global 'i' = 9657683
```

Inspect `PyObject *` and `HPy` inside GDB
------------------------------------------

**WARNING**: `py-dump` and `hpy-dump` prints to stderr, and this interacts
badly with pytest's capturing. Make sure to run `py.test -s`, else you might
not see the output. The included script `gdb-py.test` automatically pass `-s`.

`python-gdb.py` installs a GDB pretty-printer for `PyObject *` variables,
which sometimes can be confusing: often, it prints the Python repr of the
variable:

```
(gdb) set $x = PyLong_FromLong(42)
(gdb) p $x
$5 = 42
(gdb) p (void*)$x
$7 = (void *) 0x555555903ca0 <small_ints+1504>
(gdb) p *$x
$6 = {ob_refcnt = 10, ob_type = 0x5555558d5560 <PyLong_Type>}
```

For some reason which is unknown to me, sometimes it prints a rather obscure
string: here, `type` is the Python type of the object, and `0x5555558ce88` is
its address:

```
(gdb) p PyExc_ValueError
$12 = <type at remote 0x5555558ce880>
(gdb) p (void*)PyExc_ValueError
$13 = (void *) 0x5555558ce880 <_PyExc_ValueError>
```

Another useful trick is to define these two custom GDB commands in your
`~/.gdbinit`:

```
# put this in your ~/.gdbinit
define py-dump
call _PyObject_Dump($arg0)
end

# NOTE:
#    1. this assumes that you have a variable called "ctx" available
#    2. this assumes that it's a debug version of HPy, which was compiled with
#       -fkeep-inline-functions
#    3. if you don't have _HPy_Dump available, you can call manually
#       ctx->ctx_Dump (but only in universal mode)
define hpy-dump
call _HPy_Dump(ctx, $arg0)
end
```

Example of usage:

```
(gdb) set $x = PyLong_FromLong(42)
(gdb) py-dump $x
object address  : 0x555555903ca0
object refcount : 11
object type     : 0x5555558d5560
object type name: int
object repr     : 42

(gdb) py-dump PyExc_ValueError
object address  : 0x5555558ce880
object refcount : 14
object type     : 0x5555558dd8e0
object type name: type
object repr     : <class 'ValueError'>

(gdb) set $h = ctx->ctx_Long_FromLong(ctx, 1234)
(gdb) p $h
$2 = {_i = 77}
(gdb) hpy-dump $h
object address  : 0x7ffff64b0580
object refcount : 1
object type     : 0x5555558d5560
object type name: int
object repr     : 1234
```



Create a venv for python-debug and install hpy
-----------------------------------------------

The following commands create a `venv` based on the newly built
`python-debug`, and installs `hpy` in "editable mode".

```
$ cd /path/to/hpy
$ /opt/python-debug/bin/python3 -m venv venv/hpy-debug
$ . venv/hpy-debug/bin/activate
$ python setup.py develop
```

Once hpy is installed this way, you can edit the C files and rebuild it easily
by using `make`:

```
$ make          # build normally
$ make debug    # build HPY with -O0 -g
```

Run a specific HPy test under GDB
---------------------------------

To run `py.test` under GDB, use the script `./gdb-py.test`.

**Tip:** most HPy tests export a Python function called `f`. You can easily
put a breakpoint into it by using `b f_impl`:

```
$ ./gdb-py.test -s test/test_00_basic.py -k 'test_float_asdouble and universal'
...
(gdb) b f_impl
Breakpoint 1 at 0x7ffff63a0284: file /tmp/pytest-of-antocuni/pytest-219/test_float_asdouble_universal_0/mytest.c, line 5.
(gdb) run
...
Breakpoint 1, f_impl (ctx=0x7ffff64191d0, self=..., arg=...) at /tmp/pytest-of-antocuni/pytest-220/test_float_asdouble_universal_0/mytest.c:5
5	{
(gdb) next
6	    double a = HPyFloat_AsDouble(ctx, arg);
(gdb) p arg
$1 = {_i = 75}
(gdb) hpy-dump arg
object address  : 0x7ffff7017700
object refcount : 3
object type     : 0x5555558d3400
object type name: float
object repr     : 1.0
```
