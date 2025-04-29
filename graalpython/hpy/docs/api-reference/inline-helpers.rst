Inline Helpers
==============

**Inline Helper** functions are ``static inline`` functions (written in C).
Those functions are usually small convenience functions that everyone could
write but in order to avoid duplicated effort, they are defined by HPy.

One category of inline helpers are functions that convert the commonly used
but not fixed width C types, such as ``int``, or ``long long``, to HPy API.
The HPy API always uses well-defined fixed width types like ``int32`` or
``unsigned int8``.

.. autocmodule:: hpy/inline_helpers.h
   :members:
