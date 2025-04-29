API Reference
============= 

HPy's public API consists of three parts:

1. The **Core API** as defined in the :doc:`public-api`
2. **HPy Helper** functions
3. **Inline Helper** functions

Core API
--------

The **Core API** consists of inline functions that call into the Python
interpreter. Those functions will be implemented by each Python interpreter. In
:term:`CPython ABI` mode, many of these inline functions will just delegate to
a C API functions. In :term:`HPy Universal ABI` mode, they will call a function
pointer from the HPy context. This is the source of the performance change
between the modes.

.. toctree::
   :maxdepth: 2

   function-index
   hpy-ctx
   hpy-object
   hpy-type
   hpy-call
   hpy-field
   hpy-global
   hpy-dict
   hpy-sequence
   hpy-gil
   hpy-err
   hpy-eval
   public-api


HPy Helper Functions
--------------------

**HPy Helper** functions are functions (written in C) that will be compiled
together with the HPy extension's sources. The appropriate source files are
automatically added to the extension sources. The helper functions will, of
course, use the core API to interact with the interpreter. The main reason for
having the helper functions in the HPy extension is to avoid compatibility
problems due to different compilers.

.. toctree::
   :maxdepth: 2

   argument-parsing
   build-value
   formatting
   structseq
   helpers


Inline Helper Functions
-----------------------

**Inline Helper** functions are ``static inline`` functions (written in C).
Those functions are usually small convenience functions that everyone could
write but in order to avoid duplicated effort, they are defined by HPy.

.. toctree::
   :maxdepth: 2

   inline-helpers

