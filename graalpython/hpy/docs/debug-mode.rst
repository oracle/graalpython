Debug Mode
==========

HPy includes a debug mode which does useful run-time checks to ensure that C
extensions use the API correctly. Its features include:

    1. No special compilation flags are required: it is enough to compile the
       extension with the Universal ABI.

    2. Debug mode can be activated at *import time*, and it can be activated
       per-extension.

    3. You pay the overhead of debug mode only if you use it. Extensions loaded
       without the debug mode run at full speed.

This is possible because the whole of the HPy API is provided as part of the HPy
context, so debug mode can pass in a special debugging context without affecting
the performance of the regular context at all.

.. note:: The debug mode is only available if the module (you want to use it
    for) was built for :term:`HPy Universal ABI`.

The debugging context can already check for:

* Leaked handles.
* Handles used after they are closed.
* Tuple and list builder used after they were *closed* (i.e. cancelled or the
  tuple/list was built).
* Reading from a memory which is no longer guaranteed to be still valid,
  for example, the buffer returned by :c:func:`HPyUnicode_AsUTF8AndSize`,
  :c:func:`HPyBytes_AsString`, and :c:func:`HPyBytes_AS_STRING`, after the
  corresponding ``HPy`` handle was closed.
* Writing to memory which should be read-only, for example the buffer
  returned by :c:func:`HPyUnicode_AsUTF8AndSize`, :c:func:`HPyBytes_AsString`,
  and :c:func:`HPyBytes_AS_STRING`


Activating Debug Mode
---------------------

Debug mode works *only* for extensions built with HPy universal ABI.

To enable debug mode, use environment variable ``HPY``. If ``HPY=debug``, then
all HPy modules are loaded with the trace context. Alternatively, it is also
possible to specify the mode per module like this:
``HPY=modA:debug,modB:debug``.

In order to verify that your extension is being loaded in debug mode, use
environment variable ``HPY_LOG``. If this variable is set, then all HPy
extensions built in universal ABI mode print a message when loaded, such as:

.. code-block:: console

    > import snippets
    Loading 'snippets' in HPy universal mode with a debug context

.. Note: the output above is tested in test_leak_detector_with_traces_output

If the extension is built in CPython ABI mode, then the ``HPY_LOG`` environment
variable has no effect.

An HPy extension module may be also explicitly loaded in debug mode using:

.. code-block:: python

   from hpy.universal import load, MODE_DEBUG
   mod = load(module_name, so_filename, mode=MODE_DEBUG)

When loading HPy extensions explicitly, environment variables ``HPY_LOG``
and ``HPY`` have no effect for that extension.


Using Debug Mode
----------------

By default, when debug mode detects an error it will either abort the process
(using :c:func:`HPy_FatalError`) or raise a fatal exception. This may sound very
strict but in general, it is not safe to continue the execution.

When testing, aborting the process is unwanted. Module ``hpy.debug`` exposes the
``LeakDetector`` class to detect leaked ``HPy`` handles. For example:

.. literalinclude:: examples/tests.py
  :language: python
  :start-at: def test_leak_detector
  :end-before: # END: test_leak_detector

Additionally, the debug module also provides a pytest fixture, ``hpy_debug``,
that for the time being, enables the ``LeakDetector``. In the future, it
may also enable other useful debugging facilities.

.. literalinclude:: examples/tests.py
  :language: python
  :start-at: from hpy.debug.pytest import hpy_debug
  :end-at: # Run some HPy extension code

.. warning:: The usage of ``LeakDetector`` or ``hpy_debug`` by itself does not
    enable HPy debug mode! If debug mode is not enabled for any extension, then
    those features have no effect.

When dealing with handle leaks, it is useful to get a stack trace of the
allocation of the leaked handle. This feature has large memory requirements
and is therefore opt-in. It can be activated by:

.. literalinclude:: examples/tests.py
  :language: python
  :start-at: hpy.debug.set_handle_stack_trace_limit
  :end-at: hpy.debug.set_handle_stack_trace_limit

and disabled by:

.. literalinclude:: examples/tests.py
  :language: python
  :start-at: hpy.debug.disable_handle_stack_traces
  :end-at: hpy.debug.disable_handle_stack_traces


Example
-------

.. note: The following output is tested in test_leak_detector_with_traces_output

Following HPy function leaks a handle:

.. literalinclude:: examples/snippets/snippets.c
  :start-after: // BEGIN: test_leak_stacktrace
  :end-before: // END: test_leak_stacktrace

When this script is executed in debug mode:

.. literalinclude:: examples/debug-example.py
  :language: python
  :end-before: print("SUCCESS")

The output is::

    Traceback (most recent call last):
      File "/path/to/hpy/docs/examples/debug-example.py", line 7, in <module>
        snippets.test_leak_stacktrace()
      File "/path/to/hpy/debug/leakdetector.py", line 43, in __exit__
        self.stop()
      File "/path/to/hpy/debug/leakdetector.py", line 36, in stop
        raise HPyLeakError(leaks)
    hpy.debug.leakdetector.HPyLeakError: 1 unclosed handle:
        <DebugHandle 0x556bbcf907c0 for 42>
    Allocation stacktrace:
    /path/to/site-packages/hpy-0.0.4.dev227+gd7eeec6.d20220510-py3.8-linux-x86_64.egg/hpy/universal.cpython-38d-x86_64-linux-gnu.so(debug_ctx_Long_FromLong+0x45) [0x7f1d928c48c4]
    /path/to/site-packages/hpy_snippets-0.0.0-py3.8-linux-x86_64.egg/snippets.hpy.so(+0x122c) [0x7f1d921a622c]
    /path/to/site-packages/hpy_snippets-0.0.0-py3.8-linux-x86_64.egg/snippets.hpy.so(+0x14b1) [0x7f1d921a64b1]
    /path/to/site-packages/hpy-0.0.4.dev227+gd7eeec6.d20220510-py3.8-linux-x86_64.egg/hpy/universal.cpython-38d-x86_64-linux-gnu.so(debug_ctx_CallRealFunctionFromTrampoline+0xca) [0x7f1d928bde1e]
    /path/to/site-packages/hpy_snippets-0.0.0-py3.8-linux-x86_64.egg/snippets.hpy.so(+0x129b) [0x7f1d921a629b]
    /path/to/site-packages/hpy_snippets-0.0.0-py3.8-linux-x86_64.egg/snippets.hpy.so(+0x1472) [0x7f1d921a6472]
    /path/to/libpython3.8d.so.1.0(+0x10a022) [0x7f1d93807022]
    /path/to/libpython3.8d.so.1.0(+0x1e986b) [0x7f1d938e686b]
    /path/to/libpython3.8d.so.1.0(+0x2015e9) [0x7f1d938fe5e9]
    /path/to/libpython3.8d.so.1.0(_PyEval_EvalFrameDefault+0x1008c) [0x7f1d938f875a]
    /path/to/libpython3.8d.so.1.0(PyEval_EvalFrameEx+0x64) [0x7f1d938e86b8]
    /path/to/libpython3.8d.so.1.0(_PyEval_EvalCodeWithName+0xfaa) [0x7f1d938fc8af]
    /path/to/libpython3.8d.so.1.0(PyEval_EvalCodeEx+0x86) [0x7f1d938fca25]
    /path/to/libpython3.8d.so.1.0(PyEval_EvalCode+0x4b) [0x7f1d938e862b]

For the time being, HPy uses the glibc ``backtrace`` and ``backtrace_symbols``
`functions <https://www.gnu.org/software/libc/manual/html_node/Backtraces.html>`_.
Therefore all their caveats and limitations apply. Usual recommendations to get
more symbols in the traces and not only addresses, such as ``snippets.hpy.so(+0x122c)``, are:

* link your native code with ``-rdynamic`` flag (``LDFLAGS="-rdynamic"``)
* build your code without optimizations and with debug symbols (``CFLAGS="-O0 -g"``)
* use ``addr2line`` command line utility, e.g.: ``addr2line -e /path/to/snippets.hpy.so -C -f +0x122c``
