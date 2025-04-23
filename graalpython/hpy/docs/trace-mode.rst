Trace Mode
==========

HPy's trace mode allows you to analyze the usage of the HPy API. The two
fundamental metrics are ``call count`` and ``duration``. As the name already
suggests, ``call count`` tells you how often a certain HPy API function was called
and ``duration`` uses a monotonic clock to measure how much (accumulated) time was
spent in a certain HPy API function. It is further possible to register custom
*on-enter* and *on-exit* Python functions.

As with the debug mode, the trace mode can be activated at *import time*, so no
recompilation is required.


Activating Trace Mode
---------------------

Similar to how the
:ref:`debug mode is activated <debug-mode:Activating Debug Mode>`, use
environment variable ``HPY``. If ``HPY=trace``, then all HPy modules are loaded
with the trace context. Alternatively, it is also possible to specify the mode
per module like this: ``HPY=modA:trace,modB:trace``.
Environment variable ``HPY_LOG`` also works.


Using Trace Mode
----------------

The trace mode can be accessed via the shipped module ``hpy.trace``. It provides
following functions:

* ``get_call_counts()`` returns a dict. The HPy API function names are used as
  keys and the corresponding call count is the value.
* ``get_durations()`` also returns a dict similar to ``get_call_counts`` but
  the value is the accumulated time spent in the corresponding HPy API
  function (in nanoseconds). Note, the used clock does not necessarily have a
  nanosecond resolution which means that the least significant digits may not be
  accurate.
* ``set_trace_functions(on_enter=None, on_exit=None)`` allows the user to
  register custom trace functions. The function provided for ``on_enter`` and
  ``on_exit`` functions will be executed before and after and HPy API function
  is and was executed, respectively. Passing ``None`` to any of the two
  arguments or omitting one will clear the corresponding function.
* ``get_frequency()`` returns the resolution of the used clock to measure the
  time in Hertz. For example, a value of ``10000000`` corresponds to
  ``10 MHz``. In that case, the two least significant digits of the durations
  are inaccurate.


Example
-------

Following HPy function uses ``HPy_Add``:

.. literalinclude:: examples/snippets/snippets.c
  :start-after: // BEGIN: add
  :end-before: // END: add

When this script is executed in trace mode:

.. literalinclude:: examples/trace-example.py
  :language: python

The output is ``get_call_counts()["ctx_Add"] == 1``.
