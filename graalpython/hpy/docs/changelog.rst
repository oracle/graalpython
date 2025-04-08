Changelog
=========

Version 0.9 (April 25th, 2023)
------------------------------

This release adds numerous major features and indicates the end of HPy's *alhpa*
phase. We've migrated several key packages to HPy (for a list, see our website
https://hpyproject.org) and we are now confident that HPy is mature enough for
being used as serious extension API. We also plan that the next major release
will be ``1.0``.

Major new features
~~~~~~~~~~~~~~~~~~

Support subclasses of built-in types
  It is now possible to create pure HPy types that inherit from built-in types
  like ``type`` or ``float``. This was already possible before but in a very
  limited way, i.e., by setting :c:member:`HPyType_Spec.basicsize` to ``0``. In
  this case, the type implicitly inherited the basic size of the supertype but
  that also means that you cannot have a custom C struct. It is now possible
  inherit from a built-in type **AND** have a custom C struct. For further
  reference, see :c:member:`HPyType_Spec.builtin_shape` and
  :c:enum:`HPyType_BuiltinShape`.

Support for metaclasses
  HPy now supports creating types with metaclasses. This can be done by passing
  type specification parameter with kind
  :c:enumerator:`HPyType_SpecParam_Metaclass` when calling
  :c:func:`HPyType_FromSpec`.

:term:`HPy Hybrid ABI`
  In addition to :term:`CPython ABI` and :term:`HPy Universal ABI`, we now
  introduced the Hybrid ABI. The major difference is that whenever you use a
  legacy API like :c:func:`HPy_AsPyObject` or :c:func:`HPy_FromPyObject`, the
  prdouced binary will then be specific to one interpreter. This was necessary
  to ensure that universal binaries are really portable and can be used on any
  HPy-capable interpreter.

:doc:`trace-mode`
  Similar to the :doc:`debug-mode`, HPy now provides the Trace Mode that can be
  enabled at runtime and helps analyzing API usage and identifying performance
  issues.

:ref:`porting-guide:multi-phase module initialization`
  HPy now support multi-phase module initialization which is an important
  feature in particular needed for two important use cases: (1) module state
  support (which is planned to be introduced in the next major release), and (2)
  subinterpreters. We decided to drop support for single-phase module
  initialization since this makes the API cleaner and easier to use.

HPy :ref:`porting-guide:calling protocol`
  This was a big missing piece and is now eventually available. It enables slot
  ``HPy_tp_call``, which can now be used in the HPy type specification. We
  decided to use a calling convention similar to CPython's vectorcall calling
  convention. This is: the arguments are passed in a C array and the keyword
  argument names are provided as a Python tuple. Before this release, the only
  way to create a callable type was to set the special method ``__call__``.
  However, this has several disadvantages. In particlar, poor performance on
  CPython (and maybe other implementations) and it was not possible to have
  specialized call function implementations per object (see
  :c:func:`HPy_SetCallFunction`)

Added APIs
~~~~~~~~~~

Deleting attributes and items
  :c:func:`HPy_DelAttr`, :c:func:`HPy_DelAttr_s`, :c:func:`HPy_DelItem`, :c:func:`HPy_DelItem_i`, :c:func:`HPy_DelItem_s`

Capsule API
  :c:func:`HPyCapsule_New`, :c:func:`HPyCapsule_IsValid`, :c:func:`HPyCapsule_Get`, :c:func:`HPyCapsule_Set`

Eval API
  :c:func:`HPy_Compile_s` and :c:func:`HPy_EvalCode`

Formatting helpers
  :c:func:`HPyUnicode_FromFormat` and :c:func:`HPyErr_Format`

Contextvar API
  :c:func:`HPyContextVar_New`, :c:func:`HPyContextVar_Get`, :c:func:`HPyContextVar_Set`

Unicode API
  :c:func:`HPyUnicode_FromEncodedObject` and :c:func:`HPyUnicode_Substring`

Dict API
  :c:func:`HPyDict_Keys` and :c:func:`HPyDict_Copy`

Type API
  :c:func:`HPyType_GetName` and :c:func:`HPyType_IsSubtype`

Slice API
  :c:func:`HPySlice_Unpack` and :c:func:`HPySlice_AdjustIndices`

Structseq API
  :c:func:`HPyStructSequence_NewType`, :c:func:`HPyStructSequence_New`

Call API
  :c:func:`HPy_Call`, :c:func:`HPy_CallMethod`, :c:func:`HPy_CallMethodTupleDict`, :c:func:`HPy_CallMethodTupleDict_s`

HPy call protocol
  :c:func:`HPy_SetCallFunction`

Debug mode
~~~~~~~~~~

* Detect closing and returning (without dup) of context handles
* Detect invalid usage of stored ``HPyContext *`` pointer
* Detect invalid usage of tuple and list builders
* Added Windows support for checking invalid use of raw data pointers (e.g
  ``HPyUnicode_AsUTF8AndSize``) after handle was closed.
* Added support for backtrace on MacOS

Documentation
~~~~~~~~~~~~~

* Added incremental :doc:`porting-example/index`
* Added :doc:`quickstart` guide
* Extended :doc:`api-reference/index`
* Added :doc:`api-reference/function-index`
* Added possiblity to generate examples from tests with argument ``--dump-dir``
  (see :ref:`api:hpy unit tests`)
* Added initial :doc:`contributing/index` docs

Incompatible changes to version 0.0.4
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

* Simplified ``HPyDef_*`` macros
* Changed macro :c:macro:`HPy_MODINIT` because of multi-phase module init
  support.
* Replace environment variable ``HPY_DEBUG`` by ``HPY`` (see :doc:`debug-mode`
  or :doc:`trace-mode`).
* Changed signature of ``HPyFunc_VARARGS`` and ``HPyFunc_ KEYWORDS`` to align
  with HPy's call protocol calling convention.

Supported Python versions
~~~~~~~~~~~~~~~~~~~~~~~~~

* Added Python 3.11 support
* Preliminary Python 3.12 support
* Dropped Python 3.6 support (since EOL)
* Dropped Python 3.7 support (since EOL by June 2023)

Misc
~~~~

* Ensure deterministic auto-generation
* Ensure ABI backwards compatibility

  * Explicitly define slot within HPyContext of function pointers and handles
  * Compile HPy ABI version into binary and verify at load time
* Added proper support for object members ``HPyMember_OBJECT``
* Changed :c:func:`HPyBytes_AsString` and :c:func:`HPyBytes_AS_STRING` to return ``const char *``
* Use fixed-width integers in context functions



Version 0.0.4 (May 25th, 2022)
------------------------------

New Features/API:

  - HPy headers are C++ compliant
  - Python 3.10 support
  - `HPyField <https://github.com/hpyproject/hpy/blob/master/hpy/tools/autogen/public_api.h#L323>`_:
    References to Python objects that can be stored in raw native memory owned by Python objects.

    - New API functions: ``HPyField_Load``, ``HPyField_Store``
  - `HPyGlobal <https://github.com/hpyproject/hpy/blob/master/hpy/tools/autogen/public_api.h#L383>`_:
    References to Python objects that can be stored into a C global variable.

    - New API functions: ``HPyGlobal_Load``, ``HPyGlobal_Store``
    - Note: ``HPyGlobal`` does not allow to share Python objects between (sub)interpreters

  - `GIL support <https://github.com/hpyproject/hpy/blob/master/hpy/tools/autogen/public_api.h#L358>`_
    - New API functions: ``HPy_ReenterPythonExecution``, ``HPy_LeavePythonExecution``

  - `Value building support <https://github.com/hpyproject/hpy/blob/master/hpy/devel/src/runtime/buildvalue.c#L4>`_ (``HPy_BuildValue``)

  - New type slots

    - ``HPy_mp_ass_subscript``, ``HPy_mp_length``, ``HPy_mp_subscript``
    - ``HPy_tp_finalize``

  - Other new API functions

    - ``HPyErr_SetFromErrnoWithFilename``, ``HPyErr_SetFromErrnoWithFilenameObjects``
    - ``HPyErr_ExceptionMatches``
    - ``HPyErr_WarnEx``
    - ``HPyErr_WriteUnraisable``
    - ``HPy_Contains``
    - ``HPyLong_AsVoidPtr``
    - ``HPyLong_AsDouble``
    - ``HPyUnicode_AsASCIIString``, ``HPyUnicode_DecodeASCII``
    - ``HPyUnicode_AsLatin1String``, ``HPyUnicode_DecodeLatin1``
    - ``HPyUnicode_DecodeFSDefault``, ``HPyUnicode_DecodeFSDefaultAndSize``
    - ``HPyUnicode_ReadChar``

Debug mode:

  - Support activation of debug mode via environment variable ``HPY_DEBUG``
  - Support capturing stack traces of handle allocations
  - Check for invalid use of raw data pointers (e.g ``HPyUnicode_AsUTF8AndSize``) after handle was closed.
  - Detect invalid handles returned from extension functions
  - Detect incorrect closing of handles passed as arguments

Misc Changes:

  - Removed unnecessary prefix ``"m_"`` from fields of ``HPyModuleDef`` (incompatible change)
  - For HPy implementors: new pytest mark for HPy tests assuming synchronous GC

Version 0.0.3 (September 22nd, 2021)
------------------------------------

This release adds various new API functions (see below) and extends the debug
mode with the ability to track closed handles.
The default ABI mode now is 'universal' for non-CPython implementations.
Also, the type definition of ``HPyContext`` was changed and it's no longer a
pointer type.
The name of the HPy dev package was changed to 'hpy' (formerly: 'hpy.devel').
Macro HPy_CAST was replaced by HPy_AsStruct.

New features:

  - Added helper HPyHelpers_AddType for creating new types
  - Support format specifier 's' in HPyArg_Parse
  - Added API functions: HPy_Is, HPy_AsStructLegacy (for legacy types),
    HPyBytes_FromStringAndSize, HPyErr_NewException, HPyErr_NewExceptionWithDoc,
    HPyUnicode_AsUTF8AndSize, HPyUnicode_DecodeFSDefault, HPyImport_ImportModule
  - Debug mode: Implemented tracking of closed handles
  - Debug mode: Add hook for invalid handle access

Bug fixes:

  - Distinguish between pure and legacy types
  - Fix Sphinx doc errors
