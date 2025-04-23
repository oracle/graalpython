Struct Sequences
================

Struct sequences are subclasses of tuple. Similar to the API for creating
tuples, HPy provides an API to create struct sequences. This is a builder API
such that the struct sequence is guaranteed not to be written after it is
created.

.. note::

   There is no specific getter function for struct sequences. Just use one of
   :c:func:`HPy_GetItem`, :c:func:`HPy_GetItem_i`, or :c:func:`HPy_GetItem_s`.

.. autocmodule:: hpy/runtime/structseq.h
   :members: HPyStructSequence_Field,HPyStructSequence_Desc,HPyStructSequence_UnnamedField,HPyStructSequence_NewType,HPyStructSequence_New
