Porting Example
===============

HPy supports *incrementally* porting an existing C extension from the
original Python C API to the HPy API and to have the extension compile and
run at each step along the way.

Here we walk through porting a small C extension that implements a Point type
with some simple methods (a norm and a dot product). The Point type is minimal,
but does contain additional C attributes (the x and y values of the point)
and an attribute (obj) that contains a Python object (that we will need to
convert from a ``PyObject *`` to an ``HPyField``).

There is a separate C file illustrating each step of the incremental port:

* :doc:`steps/step_00_c_api`: The original C API version that we are going to
  port.

* :doc:`steps/step_01_hpy_legacy`: A possible first step where all methods still
  receive ``PyObject *`` arguments and may still cast them to ``PyPointObject *``
  if they are instances of Point.

* :doc:`steps/step_02_hpy_legacy`: Shows how to transition some methods to HPy
  methods that receive ``HPy`` handles as arguments while still supporting legacy
  methods that receive ``PyObject *`` arguments.

* :doc:`steps/step_03_hpy_final`: The completed port to HPy where all methods
  receive ``HPy`` handles and ``PyObject_HEAD`` has been removed.

Take a moment to read through :doc:`steps/step_00_c_api`. Then, once you're
ready, keep reading.

Each section below corresponds to one of the three porting steps above:

.. contents::
    :local:
    :depth: 2

.. note::
    The steps used here are one approach to porting a module. The specific
    steps are not required. They're just an example approach.


Step 01: Converting the module to a (legacy) HPy module
-------------------------------------------------------

First for the easy bit -- let's include ``hpy.h``:

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: #include <hpy.h>
    :end-at: #include <hpy.h>

We'd like to differentiate between references to ``PyPointObject`` that have
been ported to HPy and those that haven't, so let's rename it to ``PointObject``
and alias ``PyPointObject`` to ``PointObject``. We'll keep ``PyPointObject`` for
the instances that haven't been ported yet (the legacy ones) and use
``PointObject`` where we have ported the references:

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: typedef struct {
    :end-at: } PointObject;

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: typedef PointObject PyPointObject;
    :end-at: typedef PointObject PyPointObject;

For this step, all references will be to ``PyPointObject`` -- we'll only start
porting references in the next step.

Let's also call ``HPyType_LEGACY_HELPERS`` to define some helper functions
for use with the ``PointObject`` struct:

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: HPyType_LEGACY_HELPERS(PointObject)
    :end-at: HPyType_LEGACY_HELPERS(PointObject)

Again, we won't use these helpers in this step -- we're just setting things
up for later.

Now for the big steps.

We need to replace ``PyType_Spec`` for the ``Point`` type with the equivalent
``HPyType_Spec``:

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: // HPy type methods and slots (no methods or slots have been ported yet)
    :end-before: // Legacy module methods (the "dot" method is still a PyCFunction)

Initially the list of ported methods in ``point_defines`` is empty and all of
the methods are still in ``Point_slots`` which we have renamed to
``Point_legacy_slots`` for clarity.

``SHAPE(PointObject)`` is a macro that retrieves the shape of ``PointObject`` as it
was defined by the ``HPyType_LEGACY_HELPERS`` macro and will be set to
``HPyType_BuiltinShape_Legacy`` until we replace the legacy macro with the
``HPyType_HELPERS`` one. Any type with ``legacy_slots`` or that still includes
``PyObject_HEAD`` in its struct should have ``.builtin_shape`` set to
``HPyType_BuiltinShape_Legacy``.

Similarly we replace ``PyModuleDef`` with ``HPyModuleDef``:

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: // Legacy module methods (the "dot" method is still a PyCFunction)
    :end-before: // END-OF: HPyModuleDef

Like the type, the list of ported methods in ``module_defines`` is initially
almost empty: all the regular methods are still in ``PointModuleMethods`` which has
been renamed to ``PointModuleLegacyMethods``. However, because HPy supports only
multiphase module initialization, we must convert our module initialization code
to an "exec" slot on the module and add that slot to ``module_defines``.

Now all that is left is to replace the module initialization function with
one that uses ``HPy_MODINIT``. The first argument is the name of the extension,
i.e., what was ``XXX`` in ``PyInit_XXX``, and the second argument
is the ``HPyModuleDef``.

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: HPy_MODINIT(step_01_hpy_legacy, moduledef)

And we're done!

Instead of the ``PyInit_XXX``, we now have an "exec" slot on the module.
We implement it with a C function that that takes an ``HPyContext *ctx`` and ``HPy mod``
as arguments. The ``ctx`` must be forwarded as the first argument to calls to
HPy API methods. The ``mod`` argument is a handle for the module object. The runtime
creates the module for us from the provided ``HPyModuleDef``. There is no need to
call API like ``PyModule_Create`` explicitly.

Next step is to replace ``PyType_FromSpec`` by ``HPyType_FromSpec``.

``HPy_SetAttr_s`` is used to add the ``Point`` class to the module. HPy requires no
special ``PyModule_AddObject`` method.

.. literalinclude:: steps/step_01_hpy_legacy.c
    :lineno-match:
    :start-at: HPyDef_SLOT(module_exec, HPy_mod_exec)
    :end-at: }


Step 02: Transition some methods to HPy
---------------------------------------

In the previous step we put in place the type and module definitions required
to create an HPy extension module. In this step we will port some individual
methods.

Let us start by migrating ``Point_traverse``. First we need to change
``PyObject *obj`` in the ``PointObject`` struct to ``HPyField obj``:

.. literalinclude:: steps/step_02_hpy_legacy.c
    :lineno-match:
    :start-at: typedef struct {
    :end-at: } PointObject;

``HPy`` handles can only be short-lived -- i.e. local variables, arguments to
functions or return values. ``HPyField`` is the way to store long-lived
references to Python objects. For more information, please refer to the
documentation of :ref:`api-reference/hpy-field:HPyField`.

Now we can update ``Point_traverse``:

.. literalinclude:: steps/step_02_hpy_legacy.c
    :lineno-match:
    :start-at: HPyDef_SLOT(Point_traverse, HPy_tp_traverse)
    :end-before: // this is a method for creating a Point

In the first line we used the ``HPyDef_SLOT`` macro to define a small structure
that describes the slot being implemented. The first argument, ``Point_traverse``,
is the name to assign the structure to. By convention, the ``HPyDef_SLOT`` macro
expects a function called ``Point_traverse_impl`` implementing the slot. The
second argument, ``HPy_tp_traverse``, specifies the kind of slot.

This is a change from how slots are defined in the old C API. In the old API,
the kind of slot is only specified much lower down in ``Point_legacy_slots``. In
HPy the implementation and kind are defined in one place using a syntax
reminiscent of Python decorators.

The implementation of traverse is now a bit simpler than in the old C API.
We no longer need to visit ``Py_TYPE(self)`` and need only ``HPy_VISIT``
``self->obj``. HPy ensures that interpreter knows that the type of the instance
is still referenced.

Only struct members of type ``HPyField`` can be visited with ``HPy_VISIT``, which
is why we needed to convert ``obj`` to an ``HPyField`` before we implemented the
HPy traverse.

Next we must update ``Point_init`` to store the value of ``obj`` as an ``HPyField``:

.. literalinclude:: steps/step_02_hpy_legacy.c
    :lineno-match:
    :start-at: HPyDef_SLOT(Point_init, HPy_tp_init)
    :end-before: // this is the getter for the associated object

There are a few new HPy constructs used here:

- The kind of the slot passed to ``HPyDef_SLOT`` is ``HPy_tp_init``.

- ``PointObject_AsStruct`` is defined by ``HPyType_LEGACY_HELPERS`` and returns
  an instance of the ``PointObject`` struct. Because we still include
  ``PyObject_HEAD`` at the start of the struct this is still a valid ``PyObject *``
  but once we finish the port the struct will no longer contain ``PyObject_HEAD``
  and this will just be an ordinary C struct with no memory overhead!

- We use ``HPyTracker`` when parsing the arguments with ``HPyArg_ParseKeywords``.
  The ``HPyTracker`` keeps track of open handles so that they can be closed
  easily at the end with ``HPyTracker_Close``.

- ``HPyArg_ParseKeywords`` is the equivalent of ``PyArg_ParseTupleAndKeywords``.
  Note that the HPy version does not steal a reference like the Python
  version.

- ``HPyField_Store`` is used to store a reference to ``obj`` in the struct. The
  arguments are the context (``ctx``), a handle to the object that owns the
  reference (``self``), the address of the ``HPyField`` (``&p->obj``), and the
  handle to the object (``obj``).

.. note::

    An ``HPyTracker`` is not strictly needed for ``HPyArg_ParseKeywords``
    in ``Point_init``. The arguments ``x`` and ``y`` are C floats (so there are no
    handles to close) and the handle stored in ``obj`` was passed in to the
    ``Point_init`` as an argument and so should not be closed.

    We showed the tracker here to demonstrate its use. You can read more
    about argument parsing in the
    :doc:`API docs </api-reference/argument-parsing>`.

    If a tracker is needed and one is not provided, ``HPyArg_ParseKeywords``
    will return an error.


The last update we need to make for the change to ``HPyField`` is to migrate
``Point_obj_get`` which retrieves ``obj`` from the stored ``HPyField``:

.. literalinclude:: steps/step_02_hpy_legacy.c
    :lineno-match:
    :start-at: HPyDef_GET(Point_obj, "obj", .doc="Associated object.")
    :end-before: // an HPy method of Point

Above we have used ``PointObject_AsStruct`` again, and then ``HPyField_Load`` to
retrieve the value of ``obj`` from the ``HPyField``.

We've now finished all of the changes needed by introducing ``HPyField``. We
could stop here, but let's migrate one ordinary method, ``Point_norm``, to end
off this stage of the port:

.. literalinclude:: steps/step_02_hpy_legacy.c
    :lineno-match:
    :start-at: HPyDef_METH(Point_norm, "norm", HPyFunc_NOARGS, .doc="Distance from origin.")
    :end-before: // this is an LEGACY function which casts a PyObject* into a PyPointObject*

To define a method we use ``HPyDef_METH`` instead of ``HPyDef_SLOT``. ``HPyDef_METH``
creates a small structure defining the method. The first argument is the name
to assign to the structure (``Point_norm``). The second is the Python name of
the method (``norm``). The third specifies the method signature (``HPyFunc_NOARGS``
-- i.e. no additional arguments in this case). The last provides the docstring.
The macro then expects a function named ``Point_norm_impl`` implementing the
method.

The rest of the implementation remains similar, except that we use
``HPyFloat_FromDouble`` to create a handle to a Python float containing the
result (i.e. the distance of the point from the origin).

Now we are done and just have to remove the old implementations from
``Point_legacy_slots`` and add them to ``point_defines``:

.. literalinclude:: steps/step_02_hpy_legacy.c
    :lineno-match:
    :start-at: static HPyDef *point_defines[] = {
    :end-before: static HPyType_Spec Point_Type_spec = {


Step 03: Complete the port to HPy
---------------------------------

In this step we'll complete the port. We'll no longer include Python, remove
``PyObject_HEAD`` from the ``PointObject`` struct, and port the remaining methods.

First, let's remove the import of ``Python.h``:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: // #include <Python.h>  // disallow use of the old C API
    :end-at: // #include <Python.h>  // disallow use of the old C API

And ``PyObject_HEAD`` from the struct:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: typedef struct {
    :end-at: } PointObject;

And the typedef of ``PointObject`` to ``PyPointObject``:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: // typedef PointObject PyPointObject;
    :end-at: // typedef PointObject PyPointObject;

Now any code that has not been ported should result in a compilation error.

We must also change the type helpers from ``HPyType_LEGACY_HELPERS`` to
``HPyType_HELPERS`` so that ``PointObject_AsStruct`` knows that ``PyObject_HEAD``
has been removed:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: HPyType_HELPERS(PointObject)
    :end-at: HPyType_HELPERS(PointObject)

There is one more method to port, the ``dot`` method which is a module method
that implements the dot product between two points:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: HPyDef_METH(dot, "dot", HPyFunc_VARARGS, .doc="Dot product.")
    :end-before: // Method, type and module definitions. In this porting step all

The changes are similar to those used in porting the ``norm`` method, except:

- We use ``HPyArg_Parse`` instead of ``HPyArg_ParseKeywordsDict``.

- We opted not to use an ``HPyTracker`` by passing ``NULL`` as the pointer to the
  tracker when calling ``HPyArg_Parse``. There is no reason not to use a
  tracker here, but the handles to the two points are passed in as arguments
  to ``dot_impl`` and thus there is no need to close them (and they should not
  be closed).

We use ``PointObject_AsStruct`` and ``HPyFloat_FromDouble`` as before.

Now that we have ported everything we can remove ``PointMethods``,
``Point_legacy_slots`` and ``PointModuleLegacyMethods``. The resulting
type definition is much cleaner:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: static HPyDef *point_defines[] = {
    :end-before: // HPy module methods

and the module definition is simpler too:

.. literalinclude:: steps/step_03_hpy_final.c
    :lineno-match:
    :start-at: static HPyDef *module_defines[] = {
    :end-before: HPy_MODINIT(step_03_hpy_final, moduledef)

Now that the port is complete, when we compile our extension in HPy
universal mode, we obtain a built extension that depends only on the HPy ABI
and not on the CPython ABI at all!
