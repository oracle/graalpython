HPy Quickstart
==============

This section shows how to quickly get started with HPy by creating
a simple HPy extension from scratch.

Install latest HPy release:

.. code-block:: console

    python3 -m pip install hpy

Alternatively, you can also install HPy from the development repository:

.. code-block:: console

    python3 -m pip install git+https://github.com/hpyproject/hpy.git#egg=hpy

Create a new directory for the new HPy extension. Location and name of the directory
do not matter. Add the following two files:

.. literalinclude:: examples/quickstart/quickstart.c

.. literalinclude:: examples/quickstart/setup.py
    :language: python

Build the extension:

.. code-block:: console

    python3 setup.py --hpy-abi=universal develop

Try it out -- start Python console in the same directory and type:

.. literalinclude:: examples/tests.py
  :start-after: test_quickstart
  :end-before: # END: test_quickstart

Notice the shared library that was created by running ``setup.py``:

.. code-block:: console

    > ls *.so
    quickstart.hpy0.so

It does not have Python version encoded in it. If you happen to have
GraalPy or PyPy installation that supports given HPy version, you can
try running the same extension on it. For example, start
``$GRAALVM_HOME/bin/graalpy`` in the same directory and type the same
Python code: the extension should load and work just fine.

Where to go next?

  - :ref:`Simple documented HPy extension example<simple example>`
  - :doc:`Tutorial: porting Python/C API extension to HPy<porting-example/index>`
