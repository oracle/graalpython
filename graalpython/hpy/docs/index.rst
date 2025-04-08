.. HPy documentation master file, created by
   sphinx-quickstart on Thu Apr  2 23:01:08 2020.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

HPy: a better API for Python
===============================

HPy provides a new API for extending Python in C.

There are several advantages to writing C extensions in HPy:

  - **Speed**: it runs much faster on PyPy, GraalPy, and at native speed on CPython

  - **Deployment**: it is possible to compile a single binary which runs unmodified on all
    supported Python implementations and versions -- think "stable ABI" on steroids

  - **Simplicity**: it is simpler and more manageable than the ``Python.h`` API, both for
    the users and the Pythons implementing it

  - **Debugging**: it provides an improved debugging experience. Debug mode can be turned
    on at runtime without the need to recompile the extension or the Python running it.
    HPy design is more suitable for automated checks.

The official `Python/C API <https://docs.python.org/3/c-api/index.html>`_,
also informally known as ``#include <Python.h>``, is
specific to the current implementation of CPython: it exposes a lot of
internal details which makes it hard to:

  - implement it for other Python implementations (e.g. PyPy, GraalPy,
    Jython, ...)

  - experiment with new approaches inside CPython itself, for example:

    - use a tracing garbage collection instead of reference counting
    - remove the global interpreter lock (GIL) to take full advantage of multicore architectures
    - use tagged pointers to reduce memory footprint

Where to go next:
-----------------

  - Show me the code:

      - :doc:`Quickstart<quickstart>`
      - :ref:`Simple documented HPy extension example<simple example>`
      - :doc:`Tutorial: porting Python/C API extension to HPy<porting-example/index>`

  - Details:

      - :doc:`HPy overview: motivation, goals, current status<overview>`
      - :doc:`HPy API concepts introduction<api>`
      - :doc:`Python/C API to HPy Porting guide<porting-guide>`
      - :doc:`HPy API reference<api-reference/index>`


Full table of contents:
-----------------------

.. toctree::
   :maxdepth: 2

   quickstart
   overview
   api
   porting-guide
   porting-example/index
   debug-mode
   trace-mode
   api-reference/index
   contributing/index
   misc/index
   changelog


Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
