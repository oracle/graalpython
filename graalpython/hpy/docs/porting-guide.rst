Porting Guide
=============

Porting ``PyObject *`` to HPy API constructs
--------------------------------------------

While in CPython one always uses ``PyObject *`` to reference to Python objects,
in HPy there are several types of handles that should be used depending on the
life-time of the handle: ``HPy``, ``HPyField``, and ``HPyGlobal``.

- ``HPy`` represents short lived handles that live no longer than the duration of
  one call from Python to HPy extension function. Rule of thumb: use for local
  variables, arguments, and return values.

- ``HPyField`` represents handles that are Python object struct fields, i.e.,
  live in native memory attached to some Python object.

- ``HPyGlobal`` represents handles stored in C global variables. ``HPyGlobal``
  can provide isolation between subinterpreters.

.. warning:: Never use a local variable of type ``HPyField``, for any reason! If
    the GC kicks in, it might become invalid and become a dangling pointer.

.. warning:: Never store `HPy` handles to a long-lived memory, for example: C
    global variables or Python object structs.

The ``HPy``/``HPyField`` dichotomy might seem arbitrary at first, but it is
needed to allow Python implementations to use a moving GC, such as PyPy. It is
easier to explain and understand the rules by thinking about how a moving GC
interacts with the C code inside an HPy extension.

It is worth remembering that during the collection phase, a moving GC might
move an existing object to another memory location, and in that case it needs
to update all the places which store a pointer to it.  In order to do so, it
needs to *know* where the pointers are. If there is a local C variable which is
unknown to the GC but contains a pointer to a GC-managed object, the variable
will point to invalid memory as soon as the object is moved.

Back to ``HPy`` vs ``HPyField`` vs ``HPyGlobal``:

  * ``HPy`` handles must be used for all C local variables, function arguments
    and function return values. They are supposed to be short-lived and closed
    as soon as they are no longer needed. The debug mode will report a
    long-lived ``HPy`` as a potential memory leak.

  * In PyPy and GraalPy, ``HPy`` handles are implemented using an
    indirection: they are indexes inside a big list of GC-managed objects: this
    big list is tracked by the GC, so when an object moves its pointer is
    correctly updated.

  * ``HPyField`` is for long-lived references, and the GC must be aware of
    their location in memory. In PyPy, an ``HPyField`` is implemented as a
    direct pointer to the object, and thus we need a way to inform the GC
    where it is in memory, so that it can update its value upon moving: this
    job is done by ``tp_traverse``, as explained in the next section.

  * ``HPyGlobal`` is for long-lived references that are supposed to be closed
    implicitly when the module is unloaded (once module unloading is actually
    implemented). ``HPyGlobal`` provides indirection to isolate subinterpreters.
    Implementation wise, ``HPyGlobal`` will usually contain an index to a table
    with Python objects stored in the interpreter state.

  * On CPython without subinterpreters support, ``HPy``, ``HPyGlobal``,
    and ``HPyField`` are implemented as ``PyObject *``.

  * On CPython with subinterpreters support, ``HPyGlobal`` will be implemented
    by an indirection through the interpreter state. Note that thanks to the HPy
    design, switching between this and the more efficient implementation without
    subinterpreter support will not require rebuilding of the extension (in HPy
    universal mode), nor rebuilding of CPython.

.. note:: If you write a custom type using ``HPyField``, you **MUST** also write
   a ``tp_traverse`` slot. Note that this is different than the old ``Python.h``
   API, where you need ``tp_traverse`` only under certain conditions. See the
   next section for more details.

.. note:: The contract of ``tp_traverse`` is that it must visit all members of
   type ``HPyField`` contained within given struct, or more precisely *owned* by
   given Python object (in the sense of the *owner* argument to
   ``HPyField_Store``), and nothing more, nothing less. Some Python
   implementations may choose to not call the provided ``tp_traverse`` if they
   know how to visit all members of type ``HPyField`` by other means (for
   example, when they track them internally already). The debug mode will check
   this contract.

``tp_traverse``, ``tp_clear``, ``Py_TPFLAGS_HAVE_GC``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Let's quote the ``Python.h`` documentation about `GC support
<https://docs.python.org/3/c-api/gcsupport.html>`_

  Python's support for detecting and collecting garbage which involves
  circular references requires support from object types which are
  “containers” for other objects which may also be containers. Types which do
  not store references to other objects, or which only store references to
  atomic types (such as numbers or strings), do not need to provide any
  explicit support for garbage collection.

A good rule of thumb is that if your type contains ``PyObject *`` fields, you
need to:

  1. provide a ``tp_traverse`` slot;

  2. provide a ``tp_clear`` slot;

  3. add the ``Py_TPFLAGS_GC`` to the ``tp_flags``.


However, if you know that your ``PyObject *`` fields will contain only
"atomic" types, you can avoid these steps.

In HPy the rules are slightly different:

  1. if you have a field of type ``HPyField``, you always **MUST** provide a
     ``tp_traverse``. This is needed so that a moving GC can track the
     relevant areas of memory. However, you **MUST NOT** rely on
     ``tp_traverse`` to be called;

  2. ``tp_clear`` does not exist. On CPython, ``HPy`` automatically generates
     one for you, by using ``tp_traverse`` to know which are the fields to
     clear. Other implementations are free to ignore it, if it's not needed;

  3. ``HPy_TPFLAGS_GC`` is still needed, especially on CPython. If you don't
     specify it, your type will not be tracked by CPython's GC and thus it
     might cause memory leaks if it's part of a reference cycle.  However,
     other implementations are free to ignore the flag and track the objects
     anyway, if their GC implementation allows it.

``tp_dealloc`` and ``Py_DECREF``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Generally speaking, if you have one or more ``PyObject *`` fields in the old
``Python.h``, you must provide a ``tp_dealloc`` slot where you ``Py_DECREF`` all
of them. In HPy this is not needed and will be handled automatically by the
system.

In particular, when running on top of CPython, HPy will automatically provide
a ``tp_dealloc`` which decrefs all the fields listed by ``tp_traverse``.

See also, :ref:`dealloc`.


Direct C API to HPy mappings
----------------------------

In many cases, migrating to HPy is as easy as just replacing a certain C API
function by the appropriate HPy API function. Table :ref:`table-mapping` gives a
mapping between C API and HPy API functions. This mapping is generated together
with the code for the :term:`CPython ABI` mode, so it is guaranteed to be correct.


.. mark: BEGIN API MAPPING
.. _table-mapping:
.. table:: Safe API function mapping
    :widths: auto

    ================================================================================================================================== ================================================
    C API function                                                                                                                     HPY API function
    ================================================================================================================================== ================================================
    `PyBool_FromLong <https://docs.python.org/3/c-api/bool.html#c.PyBool_FromLong>`_                                                   :c:func:`HPyBool_FromLong`
    `PyBytes_AS_STRING <https://docs.python.org/3/c-api/bytes.html#c.PyBytes_AS_STRING>`_                                              :c:func:`HPyBytes_AS_STRING`
    `PyBytes_AsString <https://docs.python.org/3/c-api/bytes.html#c.PyBytes_AsString>`_                                                :c:func:`HPyBytes_AsString`
    `PyBytes_Check <https://docs.python.org/3/c-api/bytes.html#c.PyBytes_Check>`_                                                      :c:func:`HPyBytes_Check`
    `PyBytes_FromString <https://docs.python.org/3/c-api/bytes.html#c.PyBytes_FromString>`_                                            :c:func:`HPyBytes_FromString`
    `PyBytes_GET_SIZE <https://docs.python.org/3/c-api/bytes.html#c.PyBytes_GET_SIZE>`_                                                :c:func:`HPyBytes_GET_SIZE`
    `PyBytes_Size <https://docs.python.org/3/c-api/bytes.html#c.PyBytes_Size>`_                                                        :c:func:`HPyBytes_Size`
    `PyCallable_Check <https://docs.python.org/3/c-api/callable.html#c.PyCallable_Check>`_                                             :c:func:`HPyCallable_Check`
    `PyCapsule_IsValid <https://docs.python.org/3/c-api/capsule.html#c.PyCapsule_IsValid>`_                                            :c:func:`HPyCapsule_IsValid`
    `PyContextVar_Get <https://docs.python.org/3/c-api/contextvars.html#c.PyContextVar_Get>`_                                          :c:func:`HPyContextVar_Get`
    `PyContextVar_New <https://docs.python.org/3/c-api/contextvars.html#c.PyContextVar_New>`_                                          :c:func:`HPyContextVar_New`
    `PyContextVar_Set <https://docs.python.org/3/c-api/contextvars.html#c.PyContextVar_Set>`_                                          :c:func:`HPyContextVar_Set`
    `PyDict_Check <https://docs.python.org/3/c-api/dict.html#c.PyDict_Check>`_                                                         :c:func:`HPyDict_Check`
    `PyDict_Copy <https://docs.python.org/3/c-api/dict.html#c.PyDict_Copy>`_                                                           :c:func:`HPyDict_Copy`
    `PyDict_Keys <https://docs.python.org/3/c-api/dict.html#c.PyDict_Keys>`_                                                           :c:func:`HPyDict_Keys`
    `PyDict_New <https://docs.python.org/3/c-api/dict.html#c.PyDict_New>`_                                                             :c:func:`HPyDict_New`
    `PyErr_Clear <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_Clear>`_                                                     :c:func:`HPyErr_Clear`
    `PyErr_ExceptionMatches <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_ExceptionMatches>`_                               :c:func:`HPyErr_ExceptionMatches`
    `PyErr_NewException <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_NewException>`_                                       :c:func:`HPyErr_NewException`
    `PyErr_NewExceptionWithDoc <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_NewExceptionWithDoc>`_                         :c:func:`HPyErr_NewExceptionWithDoc`
    `PyErr_NoMemory <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_NoMemory>`_                                               :c:func:`HPyErr_NoMemory`
    `PyErr_SetFromErrnoWithFilename <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_SetFromErrnoWithFilename>`_               :c:func:`HPyErr_SetFromErrnoWithFilename`
    `PyErr_SetFromErrnoWithFilenameObjects <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_SetFromErrnoWithFilenameObjects>`_ :c:func:`HPyErr_SetFromErrnoWithFilenameObjects`
    `PyErr_SetObject <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_SetObject>`_                                             :c:func:`HPyErr_SetObject`
    `PyErr_SetString <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_SetString>`_                                             :c:func:`HPyErr_SetString`
    `PyErr_WarnEx <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_WarnEx>`_                                                   :c:func:`HPyErr_WarnEx`
    `PyErr_WriteUnraisable <https://docs.python.org/3/c-api/exceptions.html#c.PyErr_WriteUnraisable>`_                                 :c:func:`HPyErr_WriteUnraisable`
    `PyEval_EvalCode <https://docs.python.org/3/c-api/veryhigh.html#c.PyEval_EvalCode>`_                                               :c:func:`HPy_EvalCode`
    `PyEval_RestoreThread <https://docs.python.org/3/c-api/init.html#c.PyEval_RestoreThread>`_                                         :c:func:`HPy_ReenterPythonExecution`
    `PyEval_SaveThread <https://docs.python.org/3/c-api/init.html#c.PyEval_SaveThread>`_                                               :c:func:`HPy_LeavePythonExecution`
    `PyFloat_AsDouble <https://docs.python.org/3/c-api/float.html#c.PyFloat_AsDouble>`_                                                :c:func:`HPyFloat_AsDouble`
    `PyFloat_FromDouble <https://docs.python.org/3/c-api/float.html#c.PyFloat_FromDouble>`_                                            :c:func:`HPyFloat_FromDouble`
    `PyImport_ImportModule <https://docs.python.org/3/c-api/import.html#c.PyImport_ImportModule>`_                                     :c:func:`HPyImport_ImportModule`
    `PyIter_Check <https://docs.python.org/3/c-api/iter.html#c.PyIter_Check>`_                                                         :c:func:`HPyIter_Check`
    `PyIter_Next <https://docs.python.org/3/c-api/iter.html#c.PyIter_Next>`_                                                           :c:func:`HPyIter_Next`
    `PyList_Append <https://docs.python.org/3/c-api/list.html#c.PyList_Append>`_                                                       :c:func:`HPyList_Append`
    `PyList_Check <https://docs.python.org/3/c-api/list.html#c.PyList_Check>`_                                                         :c:func:`HPyList_Check`
    `PyList_Insert <https://docs.python.org/3/c-api/list.html#c.PyList_Insert>`_                                                       :c:func:`HPyList_Insert`
    `PyList_New <https://docs.python.org/3/c-api/list.html#c.PyList_New>`_                                                             :c:func:`HPyList_New`
    `PyLong_AsDouble <https://docs.python.org/3/c-api/long.html#c.PyLong_AsDouble>`_                                                   :c:func:`HPyLong_AsDouble`
    `PyLong_AsLong <https://docs.python.org/3/c-api/long.html#c.PyLong_AsLong>`_                                                       :c:func:`HPyLong_AsLong`
    `PyLong_AsLongLong <https://docs.python.org/3/c-api/long.html#c.PyLong_AsLongLong>`_                                               :c:func:`HPyLong_AsLongLong`
    `PyLong_AsSize_t <https://docs.python.org/3/c-api/long.html#c.PyLong_AsSize_t>`_                                                   :c:func:`HPyLong_AsSize_t`
    `PyLong_AsSsize_t <https://docs.python.org/3/c-api/long.html#c.PyLong_AsSsize_t>`_                                                 :c:func:`HPyLong_AsSsize_t`
    `PyLong_AsUnsignedLong <https://docs.python.org/3/c-api/long.html#c.PyLong_AsUnsignedLong>`_                                       :c:func:`HPyLong_AsUnsignedLong`
    `PyLong_AsUnsignedLongLong <https://docs.python.org/3/c-api/long.html#c.PyLong_AsUnsignedLongLong>`_                               :c:func:`HPyLong_AsUnsignedLongLong`
    `PyLong_AsUnsignedLongLongMask <https://docs.python.org/3/c-api/long.html#c.PyLong_AsUnsignedLongLongMask>`_                       :c:func:`HPyLong_AsUnsignedLongLongMask`
    `PyLong_AsUnsignedLongMask <https://docs.python.org/3/c-api/long.html#c.PyLong_AsUnsignedLongMask>`_                               :c:func:`HPyLong_AsUnsignedLongMask`
    `PyLong_AsVoidPtr <https://docs.python.org/3/c-api/long.html#c.PyLong_AsVoidPtr>`_                                                 :c:func:`HPyLong_AsVoidPtr`
    `PyLong_FromLong <https://docs.python.org/3/c-api/long.html#c.PyLong_FromLong>`_                                                   :c:func:`HPyLong_FromLong`
    `PyLong_FromLongLong <https://docs.python.org/3/c-api/long.html#c.PyLong_FromLongLong>`_                                           :c:func:`HPyLong_FromLongLong`
    `PyLong_FromSize_t <https://docs.python.org/3/c-api/long.html#c.PyLong_FromSize_t>`_                                               :c:func:`HPyLong_FromSize_t`
    `PyLong_FromSsize_t <https://docs.python.org/3/c-api/long.html#c.PyLong_FromSsize_t>`_                                             :c:func:`HPyLong_FromSsize_t`
    `PyLong_FromUnsignedLong <https://docs.python.org/3/c-api/long.html#c.PyLong_FromUnsignedLong>`_                                   :c:func:`HPyLong_FromUnsignedLong`
    `PyLong_FromUnsignedLongLong <https://docs.python.org/3/c-api/long.html#c.PyLong_FromUnsignedLongLong>`_                           :c:func:`HPyLong_FromUnsignedLongLong`
    `PyNumber_Absolute <https://docs.python.org/3/c-api/number.html#c.PyNumber_Absolute>`_                                             :c:func:`HPy_Absolute`
    `PyNumber_Add <https://docs.python.org/3/c-api/number.html#c.PyNumber_Add>`_                                                       :c:func:`HPy_Add`
    `PyNumber_And <https://docs.python.org/3/c-api/number.html#c.PyNumber_And>`_                                                       :c:func:`HPy_And`
    `PyNumber_Check <https://docs.python.org/3/c-api/number.html#c.PyNumber_Check>`_                                                   :c:func:`HPyNumber_Check`
    `PyNumber_Divmod <https://docs.python.org/3/c-api/number.html#c.PyNumber_Divmod>`_                                                 :c:func:`HPy_Divmod`
    `PyNumber_Float <https://docs.python.org/3/c-api/number.html#c.PyNumber_Float>`_                                                   :c:func:`HPy_Float`
    `PyNumber_FloorDivide <https://docs.python.org/3/c-api/number.html#c.PyNumber_FloorDivide>`_                                       :c:func:`HPy_FloorDivide`
    `PyNumber_InPlaceAdd <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceAdd>`_                                         :c:func:`HPy_InPlaceAdd`
    `PyNumber_InPlaceAnd <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceAnd>`_                                         :c:func:`HPy_InPlaceAnd`
    `PyNumber_InPlaceFloorDivide <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceFloorDivide>`_                         :c:func:`HPy_InPlaceFloorDivide`
    `PyNumber_InPlaceLshift <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceLshift>`_                                   :c:func:`HPy_InPlaceLshift`
    `PyNumber_InPlaceMatrixMultiply <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceMatrixMultiply>`_                   :c:func:`HPy_InPlaceMatrixMultiply`
    `PyNumber_InPlaceMultiply <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceMultiply>`_                               :c:func:`HPy_InPlaceMultiply`
    `PyNumber_InPlaceOr <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceOr>`_                                           :c:func:`HPy_InPlaceOr`
    `PyNumber_InPlacePower <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlacePower>`_                                     :c:func:`HPy_InPlacePower`
    `PyNumber_InPlaceRemainder <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceRemainder>`_                             :c:func:`HPy_InPlaceRemainder`
    `PyNumber_InPlaceRshift <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceRshift>`_                                   :c:func:`HPy_InPlaceRshift`
    `PyNumber_InPlaceSubtract <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceSubtract>`_                               :c:func:`HPy_InPlaceSubtract`
    `PyNumber_InPlaceTrueDivide <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceTrueDivide>`_                           :c:func:`HPy_InPlaceTrueDivide`
    `PyNumber_InPlaceXor <https://docs.python.org/3/c-api/number.html#c.PyNumber_InPlaceXor>`_                                         :c:func:`HPy_InPlaceXor`
    `PyNumber_Index <https://docs.python.org/3/c-api/number.html#c.PyNumber_Index>`_                                                   :c:func:`HPy_Index`
    `PyNumber_Invert <https://docs.python.org/3/c-api/number.html#c.PyNumber_Invert>`_                                                 :c:func:`HPy_Invert`
    `PyNumber_Long <https://docs.python.org/3/c-api/number.html#c.PyNumber_Long>`_                                                     :c:func:`HPy_Long`
    `PyNumber_Lshift <https://docs.python.org/3/c-api/number.html#c.PyNumber_Lshift>`_                                                 :c:func:`HPy_Lshift`
    `PyNumber_MatrixMultiply <https://docs.python.org/3/c-api/number.html#c.PyNumber_MatrixMultiply>`_                                 :c:func:`HPy_MatrixMultiply`
    `PyNumber_Multiply <https://docs.python.org/3/c-api/number.html#c.PyNumber_Multiply>`_                                             :c:func:`HPy_Multiply`
    `PyNumber_Negative <https://docs.python.org/3/c-api/number.html#c.PyNumber_Negative>`_                                             :c:func:`HPy_Negative`
    `PyNumber_Or <https://docs.python.org/3/c-api/number.html#c.PyNumber_Or>`_                                                         :c:func:`HPy_Or`
    `PyNumber_Positive <https://docs.python.org/3/c-api/number.html#c.PyNumber_Positive>`_                                             :c:func:`HPy_Positive`
    `PyNumber_Power <https://docs.python.org/3/c-api/number.html#c.PyNumber_Power>`_                                                   :c:func:`HPy_Power`
    `PyNumber_Remainder <https://docs.python.org/3/c-api/number.html#c.PyNumber_Remainder>`_                                           :c:func:`HPy_Remainder`
    `PyNumber_Rshift <https://docs.python.org/3/c-api/number.html#c.PyNumber_Rshift>`_                                                 :c:func:`HPy_Rshift`
    `PyNumber_Subtract <https://docs.python.org/3/c-api/number.html#c.PyNumber_Subtract>`_                                             :c:func:`HPy_Subtract`
    `PyNumber_TrueDivide <https://docs.python.org/3/c-api/number.html#c.PyNumber_TrueDivide>`_                                         :c:func:`HPy_TrueDivide`
    `PyNumber_Xor <https://docs.python.org/3/c-api/number.html#c.PyNumber_Xor>`_                                                       :c:func:`HPy_Xor`
    `PyObject_ASCII <https://docs.python.org/3/c-api/object.html#c.PyObject_ASCII>`_                                                   :c:func:`HPy_ASCII`
    `PyObject_Bytes <https://docs.python.org/3/c-api/object.html#c.PyObject_Bytes>`_                                                   :c:func:`HPy_Bytes`
    `PyObject_Call <https://docs.python.org/3/c-api/call.html#c.PyObject_Call>`_                                                       :c:func:`HPy_CallTupleDict`
    `PyObject_DelItem <https://docs.python.org/3/c-api/object.html#c.PyObject_DelItem>`_                                               :c:func:`HPy_DelItem`
    `PyObject_GetAttr <https://docs.python.org/3/c-api/object.html#c.PyObject_GetAttr>`_                                               :c:func:`HPy_GetAttr`
    `PyObject_GetAttrString <https://docs.python.org/3/c-api/object.html#c.PyObject_GetAttrString>`_                                   :c:func:`HPy_GetAttr_s`
    `PyObject_GetItem <https://docs.python.org/3/c-api/object.html#c.PyObject_GetItem>`_                                               :c:func:`HPy_GetItem`
    `PyObject_GetIter <https://docs.python.org/3/c-api/object.html#c.PyObject_GetIter>`_                                               :c:func:`HPy_GetIter`
    `PyObject_HasAttr <https://docs.python.org/3/c-api/object.html#c.PyObject_HasAttr>`_                                               :c:func:`HPy_HasAttr`
    `PyObject_HasAttrString <https://docs.python.org/3/c-api/object.html#c.PyObject_HasAttrString>`_                                   :c:func:`HPy_HasAttr_s`
    `PyObject_Hash <https://docs.python.org/3/c-api/object.html#c.PyObject_Hash>`_                                                     :c:func:`HPy_Hash`
    `PyObject_IsTrue <https://docs.python.org/3/c-api/object.html#c.PyObject_IsTrue>`_                                                 :c:func:`HPy_IsTrue`
    `PyObject_Length <https://docs.python.org/3/c-api/object.html#c.PyObject_Length>`_                                                 :c:func:`HPy_Length`
    `PyObject_Repr <https://docs.python.org/3/c-api/object.html#c.PyObject_Repr>`_                                                     :c:func:`HPy_Repr`
    `PyObject_RichCompare <https://docs.python.org/3/c-api/object.html#c.PyObject_RichCompare>`_                                       :c:func:`HPy_RichCompare`
    `PyObject_RichCompareBool <https://docs.python.org/3/c-api/object.html#c.PyObject_RichCompareBool>`_                               :c:func:`HPy_RichCompareBool`
    `PyObject_SetAttr <https://docs.python.org/3/c-api/object.html#c.PyObject_SetAttr>`_                                               :c:func:`HPy_SetAttr`
    `PyObject_SetAttrString <https://docs.python.org/3/c-api/object.html#c.PyObject_SetAttrString>`_                                   :c:func:`HPy_SetAttr_s`
    `PyObject_SetItem <https://docs.python.org/3/c-api/object.html#c.PyObject_SetItem>`_                                               :c:func:`HPy_SetItem`
    `PyObject_Str <https://docs.python.org/3/c-api/object.html#c.PyObject_Str>`_                                                       :c:func:`HPy_Str`
    `PyObject_Type <https://docs.python.org/3/c-api/object.html#c.PyObject_Type>`_                                                     :c:func:`HPy_Type`
    `PyObject_TypeCheck <https://docs.python.org/3/c-api/object.html#c.PyObject_TypeCheck>`_                                           :c:func:`HPy_TypeCheck`
    `PyObject_Vectorcall <https://docs.python.org/3/c-api/call.html#c.PyObject_Vectorcall>`_                                           :c:func:`HPy_Call`
    `PyObject_VectorcallMethod <https://docs.python.org/3/c-api/call.html#c.PyObject_VectorcallMethod>`_                               :c:func:`HPy_CallMethod`
    `PySequence_Contains <https://docs.python.org/3/c-api/sequence.html#c.PySequence_Contains>`_                                       :c:func:`HPy_Contains`
    `PySequence_DelSlice <https://docs.python.org/3/c-api/sequence.html#c.PySequence_DelSlice>`_                                       :c:func:`HPy_DelSlice`
    `PySequence_GetSlice <https://docs.python.org/3/c-api/sequence.html#c.PySequence_GetSlice>`_                                       :c:func:`HPy_GetSlice`
    `PySequence_SetSlice <https://docs.python.org/3/c-api/sequence.html#c.PySequence_SetSlice>`_                                       :c:func:`HPy_SetSlice`
    `PySlice_AdjustIndices <https://docs.python.org/3/c-api/slice.html#c.PySlice_AdjustIndices>`_                                      :c:func:`HPySlice_AdjustIndices`
    `PySlice_New <https://docs.python.org/3/c-api/slice.html#c.PySlice_New>`_                                                          :c:func:`HPySlice_New`
    `PySlice_Unpack <https://docs.python.org/3/c-api/slice.html#c.PySlice_Unpack>`_                                                    :c:func:`HPySlice_Unpack`
    `PyTuple_Check <https://docs.python.org/3/c-api/tuple.html#c.PyTuple_Check>`_                                                      :c:func:`HPyTuple_Check`
    `PyType_IsSubtype <https://docs.python.org/3/c-api/type.html#c.PyType_IsSubtype>`_                                                 :c:func:`HPyType_IsSubtype`
    `PyUnicode_AsASCIIString <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_AsASCIIString>`_                                :c:func:`HPyUnicode_AsASCIIString`
    `PyUnicode_AsLatin1String <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_AsLatin1String>`_                              :c:func:`HPyUnicode_AsLatin1String`
    `PyUnicode_AsUTF8AndSize <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_AsUTF8AndSize>`_                                :c:func:`HPyUnicode_AsUTF8AndSize`
    `PyUnicode_AsUTF8String <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_AsUTF8String>`_                                  :c:func:`HPyUnicode_AsUTF8String`
    `PyUnicode_Check <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_Check>`_                                                :c:func:`HPyUnicode_Check`
    `PyUnicode_DecodeASCII <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_DecodeASCII>`_                                    :c:func:`HPyUnicode_DecodeASCII`
    `PyUnicode_DecodeFSDefault <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_DecodeFSDefault>`_                            :c:func:`HPyUnicode_DecodeFSDefault`
    `PyUnicode_DecodeFSDefaultAndSize <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_DecodeFSDefaultAndSize>`_              :c:func:`HPyUnicode_DecodeFSDefaultAndSize`
    `PyUnicode_DecodeLatin1 <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_DecodeLatin1>`_                                  :c:func:`HPyUnicode_DecodeLatin1`
    `PyUnicode_EncodeFSDefault <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_EncodeFSDefault>`_                            :c:func:`HPyUnicode_EncodeFSDefault`
    `PyUnicode_FromEncodedObject <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_FromEncodedObject>`_                        :c:func:`HPyUnicode_FromEncodedObject`
    `PyUnicode_FromString <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_FromString>`_                                      :c:func:`HPyUnicode_FromString`
    `PyUnicode_FromWideChar <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_FromWideChar>`_                                  :c:func:`HPyUnicode_FromWideChar`
    `PyUnicode_ReadChar <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_ReadChar>`_                                          :c:func:`HPyUnicode_ReadChar`
    `PyUnicode_Substring <https://docs.python.org/3/c-api/unicode.html#c.PyUnicode_Substring>`_                                        :c:func:`HPyUnicode_Substring`
    `Py_FatalError <https://docs.python.org/3/c-api/sys.html#c.Py_FatalError>`_                                                        :c:func:`HPy_FatalError`
    ================================================================================================================================== ================================================
.. mark: END API MAPPING


.. note: There are, of course, also cases where it is not possible to map directly and safely from a C API function (or concept) to an HPy API function (or concept).

Reference Counting ``Py_INCREF`` and ``Py_DECREF``
--------------------------------------------------

The equivalents of ``Py_INCREF`` and ``Py_DECREF`` are essentially
:c:func:`HPy_Dup` and :c:func:`HPy_Close`, respectively. The main difference is
that :c:func:`HPy_Dup` gives you a *new handle* to the same object which means
that the two handles may be different if comparing them with ``memcmp`` but
still reference the same object. As a consequence, you may close a handle only
once, i.e., you cannot call :c:func:`HPy_Close` twice on the same ``HPy``
handle, even if returned from ``HPy_Dup``. For examples, see also sections
:ref:`api:handles` and :ref:`api:handles vs ``pyobject *```

Calling functions ``PyObject_Call`` and ``PyObject_CallObject``
---------------------------------------------------------------

Both ``PyObject_Call`` and ``PyObject_CallObject`` are replaced by
``HPy_CallTupleDict(callable, args, kwargs)`` in which either or both of
``args`` and ``kwargs`` may be null handles.

``PyObject_Call(callable, args, kwargs)`` becomes::

    HPy result = HPy_CallTupleDict(ctx, callable, args, kwargs);

``PyObject_CallObject(callable, args)`` becomes::

    HPy result = HPy_CallTupleDict(ctx, callable, args, HPy_NULL);

If ``args`` is not a handle to a tuple or ``kwargs`` is not a handle to a
dictionary, ``HPy_CallTupleDict`` will return ``HPy_NULL`` and raise a
``TypeError``. This is different to ``PyObject_Call`` and
``PyObject_CallObject`` which may segfault instead.

Calling Protocol
----------------

Both the *tp_call* and *vectorcall* calling protocols are replaced by HPy's
calling protocol. This is done by defining slot ``HPy_tp_call``. HPy uses only
one calling convention which is similar to the vectorcall calling convention.
In the following example, we implement a call function for a simple Euclidean
vector type. The function computes the dot product of two vectors.

.. literalinclude:: examples/snippets/hpycall.c
  :start-after: // BEGIN EuclideanVectorObject
  :end-before: // END EuclideanVectorObject

.. literalinclude:: examples/snippets/hpycall.c
  :start-after: // BEGIN HPy_tp_call
  :end-before: // END HPy_tp_call

Positional and keyword arguments are passed as C array ``args``. Argument
``nargs`` specifies the number of positional arguments. Argument ``kwnames`` is
a tuple containing the names of the keyword arguments. The keyword argument
values are appended to positional arguments and start at ``args[nargs]`` (if
there are any).

In the above example, function ``call_impl`` will be used by default to call all
instances of the corresponding type. It is also possible to install (maybe
specialized) call function implementations per instances by using function
:c:func:`HPy_SetCallFunction`. This needs to be done in the constructor of an
object. For example:

.. literalinclude:: examples/snippets/hpycall.c
  :start-after: // BEGIN HPy_SetCallFunction
  :end-before: // END HPy_SetCallFunction

Limitations
~~~~~~~~~~~

  1. It is not possible to use slot ``HPy_tp_call`` for a *var object* (i.e. if
     :c:member:`HPyType_Spec.itemsize` is greater ``0``). Reason: HPy installs
     a hidden field in the object's data to store the call function pointer
     which is appended to everything else. In case of ``EuclideanVectorObject``,
     a field is implicitly appended after member ``y``. This is not possible for
     var objects because the variable part will also start after the fixed
     members.

  2. It is also not possible to use slot ``HPy_tp_call`` with a legacy type that
     inherits the basicsize (i.e. if :c:member:`HPyType_Spec.basicsize` is
     ``0``) for the same reason as above.

To overcome these limitations, it is still possible to manually embed a field
for the call function pointer in a type's C struct and tell HPy where this field
is. In this case, it is always necessary to set the call function pointer using
:c:func:`HPy_SetCallFunction` in the object's constructor. This procedure is
less convenient than just using slot ``HPy_tp_cal`` but still not hard to use.
Consider following example. We define a struct ``FooObject`` and declare field
``HPyCallFunction call_func`` which will be used to store the call function's
pointer. We need to register the offset of that field with member
``__vectorcalloffset__`` and in the constructor ``Foo_new``, we assign the call
function ``Foo_call_func``.

.. literalinclude:: examples/snippets/hpycall.c
  :start-after: // BEGIN FooObject
  :end-before: // END FooObject

.. literalinclude:: examples/snippets/hpycall.c
  :start-after: // BEGIN vectorcalloffset
  :end-before: // END vectorcalloffset

.. note::

    In contrast to CPython's vectorcall protocol, ``nargs`` will never have flag
    ``PY_VECTORCALL_ARGUMENTS_OFFSET`` set. It will **only** be the positional
    argument count.

.. _call-migration:

Incremental Migration to HPy's Calling Protocol
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In order to support incremental migration, HPy provides helper function
:c:func:`HPyHelpers_PackArgsAndKeywords` that converts from HPy's calling
convention to CPython's *tp_call*  calling convention. Consider following
example:

.. literalinclude:: examples/snippets/hpycall.c
  :start-after: // BEGIN pack_args
  :end-before: // END pack_args

In this example, ``args``, ``nargs``, and ``kwnames`` are used to create a tuple
of positional arguments ``args_tuple`` and a keyword arguments dictionary
``kwd``.

PyModule_AddObject
------------------

``PyModule_AddObject`` is replaced with a regular :c:func:`HPy_SetAttr_s`. There
is no ``HPyModule_AddObject`` function because it has an unusual refcount
behavior (stealing a reference but only when it returns ``0``).


.. _dealloc:

Deallocator slot ``Py_tp_dealloc``
----------------------------------

``Py_tp_dealloc`` essentially becomes ``HPy_tp_destroy``. The name intentionally
differs because there are major differences: while the slot function of
``Py_tp_dealloc`` receives the full object (which makes it possible to resurrect
it) and while there are no restrictions on what you may call in the C API
deallocator, you must not do that in HPy's deallocator.

The two major restrictions apply to the slot function of ``HPy_tp_destroy``:

1. The function must be **thread-safe**.
2. The function **must not** call into the interpreter.

The idea is, that ``HPy_tp_destroy`` just releases native resources (e.g. by
using C lib's ``free`` function). Therefore, it only receives a pointer to the
object's native data (and not a handle to the object) and it does not receive an
``HPyContext`` pointer argument.

For the time being, HPy will support the ``HPy_tp_finalize`` slot where those
tight restrictions do not apply at the (significant) cost of performance.

Special slots ``Py_tp_methods``, ``Py_tp_members``, and ``Py_tp_getset``
------------------------------------------------------------------------

There is no direct replacement for C API slots ``Py_tp_methods``,
``Py_tp_members``, and ``Py_tp_getset`` because they are no longer needed.
Methods, members, and get/set descriptors are specified *flatly* together with
the other slots, using the standard mechanisms of :c:macro:`HPyDef_METH`,
:c:macro:`HPyDef_MEMBER`, and :c:macro:`HPyDef_GETSET`. The resulting ``HPyDef``
structures are then accumulated in :c:member:`HPyType_Spec.defines`.

Creating lists and tuples
-------------------------

The C API way of creating lists and tuples is to create an empty list or tuple
object using ``PyList_New(n)`` or ``PyTuple_New(n)``, respectively, and then to
fill the empty object using ``PyList_SetItem / PyList_SET_ITEM`` or
``PyTuple_SetItem / PyTuple_SET_ITEM``, respectively.

This is in particular problematic for tuples because they are actually
immutable. HPy goes a different way and provides a dedicated *builder* API to
avoid the (temporary) inconsistent state during object initialization.

Long story short, doing the same in HPy with builders is still very simple and
straight forward. Following an example for creating a list:

.. code-block:: c

    PyObject *list = PyList_New(5);
    if (list == NULL)
        return NULL; /* error */
    PyList_SET_ITEM(list, 0, item0);
    PyList_SET_ITEM(list, 1, item0);
    ...
    PyList_SET_ITEM(list, 4, item0);
    /* now 'list' is ready to use */

becomes

.. code-block:: c

    HPyListBuilder builder = HPyListBuilder_New(ctx, 5);
    HPyListBuilder_Set(ctx, builder, 0, h_item0);
    HPyListBuilder_Set(ctx, builder, 1, h_item1);
    ...
    HPyListBuilder_Set(ctx, builder, 4, h_item4);
    HPy h_list = HPyListBuilder_Build(ctx, builder);
    if (HPy_IsNull(h_list))
        return HPy_NULL; /* error */

.. note:: In contrast to ``PyList_SetItem``, ``PyList_SET_ITEM``,
   ``PyTuple_SetItem``, and ``PyTuple_SET_ITEM``, the builder functions
   :c:func:`HPyListBuilder_Set` and :c:func:`HPyTupleBuilder_Set` are **NOT**
   stealing references. It is necessary to close the passed item handles (e.g.
   ``h_item0`` in the above example) if they are no longer needed.

If an error occurs during building the list or tuple, it is necessary to call
:c:func:`HPyListBuilder_Cancel` or :c:func:`HPyTupleBuilder_Cancel`,
respectively, to avoid memory leaks.

For details, see the API reference documentation
:doc:`api-reference/hpy-sequence`.

Buffers
-------

The buffer API in HPy is implemented using the ``HPy_buffer`` struct, which looks
very similar to ``Py_buffer`` (refer to the `CPython documentation
<https://docs.python.org/3.6/c-api/buffer.html#buffer-structure>`_ for the
meaning of the fields)::

    typedef struct {
        void *buf;
        HPy obj;
        HPy_ssize_t len;
        HPy_ssize_t itemsize;
        int readonly;
        int ndim;
        char *format;
        HPy_ssize_t *shape;
        HPy_ssize_t *strides;
        HPy_ssize_t *suboffsets;
        void *internal;
    } HPy_buffer;

Buffer slots for HPy types are specified using slots ``HPy_bf_getbuffer`` and
``HPy_bf_releasebuffer`` on all supported Python versions, even though the
matching PyType_Spec slots, ``Py_bf_getbuffer`` and ``Py_bf_releasebuffer``, are
only available starting from CPython 3.9.

Multi-phase Module Initialization
---------------------------------

HPy supports only multi-phase module initialization (PEP 451). This means that
the module object is typically created by interpreter from the ``HPyModuleDef``
specification and there is no "init" function. However, the module can define
one or more ``HPy_mod_exec`` slots, which will be executed just after the module
object is created. Inside the code of those slots, one can usually perform the same
initialization as before.

Example of legacy single phase module initialization that uses Python/C API:

.. literalinclude:: examples/snippets/legacyinit.c
  :start-after: // BEGIN
  :end-before: // END

The same code structure ported to HPy and multi-phase module initialization:

.. literalinclude:: examples/snippets/hpyinit.c
  :start-after: // BEGIN
  :end-before: // END
