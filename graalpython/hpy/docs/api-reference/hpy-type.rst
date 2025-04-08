HPy Types and Modules
=====================

Types, modules and their attributes (i.e. methods, members, slots, get-set
descriptors) are defined in a similar way. Section `HPy Type`_ documents the
type-specific and `HPy Module`_ documents the module-specific part. Section
`HPy Definition`_ documents how to define attributes for both, types and
modules.


HPy Type
--------

Definition
~~~~~~~~~~

.. autocmodule:: hpy/hpytype.h
   :members: HPyType_Spec,HPyType_BuiltinShape,HPyType_SpecParam,HPyType_SpecParam_Kind,HPyType_HELPERS,HPyType_LEGACY_HELPERS,HPy_TPFLAGS_DEFAULT,HPy_TPFLAGS_BASETYPE,HPy_TPFLAGS_HAVE_GC

Construction and More
~~~~~~~~~~~~~~~~~~~~~

.. autocmodule:: autogen/public_api.h
   :members: HPyType_FromSpec, HPyType_GetName, HPyType_IsSubtype

HPy Module
----------

.. c:macro:: HPY_EMBEDDED_MODULES

   If ``HPY_EMBEDDED_MODULES`` is defined, this means that there will be
   several embedded HPy modules (and so, several ``HPy_MODINIT`` usages) in the
   same binary. In this case, some restrictions apply:

   1. all of the module's methods/member/slots/... must be defined in the same
      file
   2. the embedder **MUST** declare the module to be *embeddable* by using macro
      :c:macro:`HPY_MOD_EMBEDDABLE`.

.. autocmodule:: hpy/hpymodule.h
   :members: HPY_MOD_EMBEDDABLE,HPyModuleDef,HPy_MODINIT

HPy Definition
--------------

Defining slots, methods, members, and get-set descriptors for types and modules
is done with HPy definition (represented by C struct :c:struct:`HPyDef`).

.. autocmodule:: hpy/hpydef.h
   :members: HPyDef,HPyDef_Kind,HPySlot,HPyMeth,HPyMember_FieldType,HPyMember,HPyGetSet,HPyDef_SLOT,HPyDef_METH,HPyDef_MEMBER,HPyDef_GET,HPyDef_SET,HPyDef_GETSET,HPyDef_CALL_FUNCTION
