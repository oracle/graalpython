HPy API Introduction
====================

Handles
-------

The "H" in HPy stands for **handle**, which is a central concept: handles are
used to hold a C reference to Python objects, and they are represented by the
C ``HPy`` type.  They play the same role as ``PyObject *`` in the ``Python.h``
API, albeit with some important differences which are detailed below.

When they are no longer needed, handles must be closed by calling
``HPy_Close``, which plays more or less the same role as ``Py_DECREF``.
Similarly, if you need a new handle for an existing object, you can duplicate
it by calling ``HPy_Dup``, which plays more or less the same role as
``Py_INCREF``.

The HPy API strictly follows these rules:

- ``HPy`` handles returned by a function are **never borrowed**, i.e.,
  the caller must either close or return it.
- ``HPy`` handles passed as function arguments are **never stolen**;
  if you receive a ``HPy`` handle argument from your caller, you should never close it.

These rules makes the code simpler to reason about. Moreover, no reference
borrowing enables the Python implementations to use whatever internal
representation they wish. For example, the object returned by ``HPy_GetItem_i``
may be created on demand from some compact internal representation, which does
not need to convert itself to full blown representation in order to hold onto
the borrowed object.

We strongly encourage the users of HPy to also internally follow these rules
for their own internal APIs and helper functions. For the sake of simplicity
and easier local reasoning and also because in the future, code adhering
to those rules may be suitable target for some scalable and precise static
analysis tool.

The concept of handles is certainly not unique to HPy. Other examples include
Unix file descriptors, where you have ``dup()`` and ``close()``, and Windows'
``HANDLE``, where you have ``DuplicateHandle()`` and ``CloseHandle()``.


Handles vs ``PyObject *``
~~~~~~~~~~~~~~~~~~~~~~~~~

In order to fully understand the way HPy handles work, it is useful to discuss
the ``Pyobject *`` pointer in ``Python.h``. These pointers always
point to the same object, and a python object's identity is completely given
by its address in memory, and two pointers with the same address can
be passed to ``Python.h`` API functions interchangeably. As a result, ``Py_INCREF``
and ``Py_DECREF`` can be called with any reference to an object as long as the
total number of calls of ``incref`` is equal to the number of calls of ``decref``
at the end of the object lifetime.

Whereas using HPy API, each handle must be closed independently.

Thus, the following perfectly valid piece of code using ``Python.h``::

  void foo(void)
  {
      PyObject *x = PyLong_FromLong(42);  // implicit INCREF on x
      PyObject *y = x;
      Py_INCREF(y);                       // INCREF on y
      /* ... */
      Py_DECREF(x);
      Py_DECREF(x);                       // two DECREF on x
  }

Becomes using HPy API:

.. literalinclude:: examples/snippets/snippets.c
  :start-after: // BEGIN: foo
  :end-before: // END: foo

Calling any HPy function on a closed handle is an error. Calling
``HPy_Close()`` on the same handle twice is an error. Forgetting to call
``HPy_Close()`` on a handle results in a memory leak. When running in
:ref:`debug-mode:debug mode`, HPy actively checks that you don't
close a handle twice and that you don't forget to close any.


.. note::
  Debug mode is a good example of how powerful it is to decouple the
  identity and therefore the lifetime of handles and those of objects.
  If you find a memory leak on CPython, you know that you are missing a
  ``Py_DECREF`` somewhere but the only way to find the corresponding
  ``Py_INCREF`` is to manually and carefully study the source code.
  On the other hand, if you forget to call ``HPy_Close()``, debug mode
  is able to identify the precise code location which created the unclosed
  handle. Similarly, if you try to operate on a closed handle, it will
  identify the precise code locations which created and closed it. This is
  possible because handles are associated with a single call to a C/API
  function. As a result, given a handle that is leaked or used after freeing,
  it is possible to identify exactly the C/API function that produced it.


Remember that ``Python.h`` guarantees that multiple references to the same
object results in the very same ``PyObject *`` pointer. Thus, it is
possible to compare the pointer addresses to check whether they refer
to the same object::

    int is_same_object(PyObject *x, PyObject *y)
    {
        return x == y;
    }

On the other hand, in HPy, each handle is independent and it is common to have
two different handles which point to the same underlying object, so comparing
two handles directly is ill-defined.  To prevent this kind of common error
(especially when porting existing code to HPy), the ``HPy`` C type is opaque
and the C compiler actively forbids comparisons between them.  To check for
identity, you can use ``HPy_Is()``:

.. literalinclude:: examples/snippets/snippets.c
  :start-after: // BEGIN: is_same_object
  :end-before: // END: is_same_object

.. note::
   The main benefit of opaque handle semantics is that implementations are
   allowed to use very different models of memory management.  On CPython,
   implementing handles is trivial because ``HPy`` is basically ``PyObject *``
   in disguise, and ``HPy_Dup()`` and ``HPy_Close()`` are just aliases for
   ``Py_INCREF`` and ``Py_DECREF``.

   Unlike CPython, PyPy does not use reference counting to manage memory:
   instead, it uses a *moving GC*, which means that the address of an object
   might change during its lifetime, and this makes it hard to implement
   semantics like ``PyObject *``'s where the address *identifies* the object,
   and this is directly exposed to the user.  HPy solves this problem: on
   PyPy, handles are integers which represent indices into a list, which
   is itself managed by the GC. When an address changes, the GC edits the
   list, without having to touch all the handles which have been passed to C.


HPyContext
-----------

All HPy function calls take an ``HPyContext`` as a first argument, which
represents the Python interpreter all the handles belong to.  Strictly
speaking, it would be possible to design the HPy API without using
``HPyContext``: after all, all HPy function calls are ultimately mapped to
``Python.h`` function call, where there is no notion of context.

One of the reasons to include ``HPyContext`` from the day one is to be
future-proof: it is conceivable to use it to hold the interpreter or the
thread state in the future, in particular when there will be support for
sub-interpreters.  Another possible usage could be to embed different versions
or implementations of Python inside the same process. In addition, the
``HPyContext`` may also be extended by adding new functions to the end without
breaking any extensions built against the current ``HPyContext``.

Moreover, ``HPyContext`` is used by the :term:`HPy Universal ABI` to contain a
sort of virtual function table which is used by the C extensions to call back
into the Python interpreter.

.. _simple example:

A simple example
-----------------

In this section, we will see how to write a simple C extension using HPy. It
is assumed that you are already familiar with the existing ``Python.h`` API, so we
will underline the similarities and the differences with it.

We want to create a function named ``myabs`` and ``double`` which takes a
single argument and computes its absolute value:

.. literalinclude:: examples/simple-example/simple.c
  :start-after: // BEGIN: myabs
  :end-before: // END: myabs

There are a couple of points which are worth noting:

  * We use the macro ``HPyDef_METH`` to declare we are going to define a HPy
    function called ``myabs``.

  * The function will be available under the name ``"myabs"`` in our Python
    module.

  * The actual C function which implements ``myabs`` is called ``myabs_impl``
    and is inferred by the macro. The macro takes the name and adds ``_impl``
    to the end of it.

  * It uses the ``HPyFunc_O`` calling convention. Like ``METH_O`` in ``Python.h``,
    ``HPyFunc_O`` means that the function receives a single argument on top of
    ``self``.

  * ``myabs_impl`` takes two arguments of type ``HPy``: handles for ``self``
    and the argument, which are guaranteed to be valid. They are automatically
    closed by the caller, so there is no need to call ``HPy_Close`` on them.

  * ``myabs_impl`` returns a handle, which has to be closed by the caller.

  * ``HPy_Absolute`` is the equivalent of ``PyNumber_Absolute`` and
    computes the absolute value of the given argument.

  * We also do not call ``HPy_Close`` on the result returned to the caller.
    We must return a valid handle.

.. note::
   Among other things,
   the ``HPyDef_METH`` macro is needed to maintain compatibility with CPython.
   In CPython, C functions and methods have a C signature that is different to
   the one used by HPy: they don't receive an ``HPyContext`` and their arguments
   have the type ``PyObject *`` instead of ``HPy``.  The macro automatically
   generates a trampoline function whose signature is appropriate for CPython and
   which calls the ``myabs_impl``. This trampoline is then used from both the
   CPython ABI and the CPython implementation of the universal ABI, but other
   implementations of the universal ABI will usually call directly the HPy
   function itself.

The second function definition is a bit different:

.. literalinclude:: examples/simple-example/simple.c
  :start-after: // BEGIN: double
  :end-before: // END: double

This shows off the other way of creating functions.

  * This example is much the same but the difference is that we use
    ``HPyDef_METH_IMPL`` to define a function named ``double``.

  * The difference between ``HPyDef_METH_IMPL`` and ``HPyDef_METH`` is that
    the former needs to be given a name for a the functions as the third
    argument.

Now, we can define our module:

.. literalinclude:: examples/simple-example/simple.c
  :start-after: // BEGIN: methodsdef
  :end-before: // END: methodsdef

This part is very similar to the one you would write with ``Python.h``.  Note that
we specify ``myabs`` (and **not** ``myabs_impl``) in the method table. There
is also the ``.legacy_methods`` field, which allows to add methods that use the
``Python.h`` API, i.e., the value should be an array of ``PyMethodDef``. This
feature enables support for hybrid extensions in which some of the methods
are still written using the ``Python.h`` API.

Note that the HPy module does not specify its name. HPy does not support the legacy
single phase module initialization and the only module initialization approach is
the multi-phase initialization (`PEP 489 <https://peps.python.org/pep-0489/>`_).
With multi-phase module initialization,
the name of the module is always taken from the ``ModuleSpec`` (`PEP 451 <https://peps.python.org/pep-0451/>`_)
, i.e., most likely from the name used in the ``import {{name}}`` statement that
imported your module.

This is the only difference stemming from multi-phase module initialization in this
simple example.
As long as there is no need for any further initialization, we can just "register"
our module using the ``HPy_MODINIT`` convenience macro. The first argument is the
name of the extension file and is needed for HPy, among other things, to be able
to generate the entry point for CPython called ``PyInit_{{name}}``. The second argument
is the ``HPyModuleDef`` we just defined.

.. literalinclude:: examples/simple-example/simple.c
  :start-after: // BEGIN: moduledef
  :end-before: // END: moduledef

Building the module
~~~~~~~~~~~~~~~~~~~~

Let's write a ``setup.py`` to build our extension:

.. literalinclude:: examples/simple-example/setup.py
    :language: python

We can now build the extension by running ``python setup.py build_ext -i``. On
CPython, it will target the :term:`CPython ABI` by default, so you will end up with
a file named e.g. ``simple.cpython-37m-x86_64-linux-gnu.so`` which can be
imported directly on CPython with no dependency on HPy.

To target the :term:`HPy Universal ABI` instead, it is possible to pass the
option ``--hpy-abi=universal`` to ``setup.py``. The following command will
produce a file called ``simple.hpy.so`` (note that you need to specify
``--hpy-abi`` **before** ``build_ext``, since it is a global option)::

  python setup.py --hpy-abi=universal build_ext -i

.. note::
   This command will also produce a Python file named ``simple.py``, which
   loads the HPy module using the ``universal.load`` function from
   the ``hpy`` Python package.

VARARGS calling convention
~~~~~~~~~~~~~~~~~~~~~~~~~~~

If we want to receive more than a single arguments, we need the
``HPy_METH_VARARGS`` calling convention. Let's add a function ``add_ints``
which adds two integers:

.. literalinclude:: examples/snippets/hpyvarargs.c
  :start-after: // BEGIN: add_ints
  :end-before: // END: add_ints

There are a few things to note:

  * The C signature is different than the corresponding ``Python.h``
    ``METH_VARARGS``: in particular, instead of taking a tuple ``PyObject *args``,
    we take an array of ``HPy`` and its size. This allows the call to happen
    more efficiently, because you don't need to create a tuple just to pass the
    arguments.

  * We call ``HPyArg_Parse`` to parse the arguments. Contrarily to almost all
    the other HPy functions, this is **not** a thin wrapper around
    ``PyArg_ParseTuple`` because as stated above we don't have a tuple to pass
    to it, although the idea is to mimic its behavior as closely as
    possible. The parsing logic is implemented from scratch inside HPy, and as
    such there might be missing functionality during the early stages of HPy
    development.

  * If an error occurs, we return ``HPy_NULL``: we cannot simply ``return NULL``
    because ``HPy`` is not a pointer type.

Once we have written our function, we can add it to the ``SimpleMethods[]``
table, which now becomes:

.. literalinclude:: examples/snippets/hpyvarargs.c
  :start-after: // BEGIN: methodsdef
  :end-before: // END: methodsdef

Creating types in HPy
---------------------

Creating Python types in an HPy extension is again very similar to the C API
with the difference that HPy only supports creating types from a specification.
This is necessary because there is no such C-level type as ``PyTypeObject``
since that would expose the internal implementation.


Creating a simple type in HPy
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This section assumes that the user wants to define a type that stores some data
in a C-level structure. As an example, we will create a simple C structure
``PointObject`` that represents a two-dimensional point.

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: PointObject
  :end-before: // END: PointObject

The macro call ``HPyType_HELPERS(PointObject)`` generates useful helper
facilities for working with the type. It generates a C enum
``PointObject_SHAPE`` and a helper function ``PointObject_AsStruct``. The enum
is used in the type specification. The helper function is used to efficiently
retrieving the pointer ``PointObject *`` from an HPy handle to be able to access
the C structure. We will use this helper function to implement the methods,
get-set descriptors, and slots.

It makes sense to expose fields ``PointObject.x`` and ``PointObject.y`` as
Python-level members. To do so, we need to define members by specifying their
name, type, and location using HPy's convenience macro ``HPyDef_MEMBER``:

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: members
  :end-before: // END: members

The first argument of the macro is the name for the C glabal variable that will
store the necessary information. We will need that later for registration of
the type. The second, third, and fourth arguments are the Python-level name, the
C type of the member, and the offset in the C structure, respectively.

Similarly, methods and get-set descriptors can be defined. For example, method
``foo`` is an instance method that takes no arguments (the self argument is, of
course, implicit), does some computation with fields ``x`` and ``y`` and
returns a Python ``int``:

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: methods
  :end-before: // END: methods

Get-set descriptors are also defined in a very similar way as methods. The
following example defines a get-set descriptor for attribute ``z`` which is
calculated from the ``x`` and ``y`` fields of the struct.

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: getset
  :end-before: // END: getset

It is also possible to define a get-descriptor or a set-descriptor by using
HPy's macros ``HPyDef_GET`` and ``HPyDef_SET`` in the same way.

HPy also supports type slots. In this example, we will define slot
``HPy_tp_new`` (which corresponds to magic method ``__new__``) to initialize
fields ``x`` and ``y`` when constructing the object:

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: slots
  :end-before: // END: slots

After everything was defined, we need to create a list of all defines such that
we are able to eventually register them to the type:

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: defines
  :end-before: // END: defines

Please note that it is required to terminate the list with ``NULL``.
We can now create the actual type specification by appropriately filling an
``HPyType_Spec`` structure:

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: spec
  :end-before: // END: spec

First, we need to define the name of the type by setting a C string to member
``name``. Since this type has a C structure, we need to define the ``basicsize``
and best practice is to set it to ``sizeof(PointObject)``. Also best practice is
to set ``builtin_shape`` to ``PointObject_SHAPE`` where ``PointObject_SHAPE`` is
generated by the previous usage of macro ``HPyType_HELPERS(PointObject)``. Last
but not least, we need to register the defines by setting field ``defines`` to
the previously defined array ``Point_defines``.

The type specification for the simple type ``simple_type.Point`` represented in
C by structure ``PointObject`` is now complete. All that remains is to create
the type object and add it to the module.

We will define a module execute slot, which is executed by the runtime right
after the module is created. The purpose of the execute slot is to initialize
the newly created module object. We can then add the type by using
:c:func:`HPyHelpers_AddType`:

.. literalinclude:: examples/hpytype-example/simple_type.c
  :start-after: // BEGIN: add_type
  :end-before: // END: add_type

Also look at the full example at: :doc:`examples/hpytype-example/simple_type`.


Legacy types
~~~~~~~~~~~~

A type whose struct starts with ``PyObject_HEAD`` (either directly by
embedding it in the type struct or indirectly by embedding another struct like
``PyLongObject``) is a *legacy type*. A legacy type must set
``.builtin_shape = HPyType_BuiltinShape_Legacy``
in its ``HPyType_Spec``. The counterpart (i.e. a non-legacy type) is called HPy
pure type.

Legacy types are available to allow gradual porting of existing CPython
extensions. It is possible to reuse existing ``PyType_Slot`` entities (i.e.
slots, methods, members, and get/set descriptors). The idea is that you can then
migrate one after each other while still running the tests.

The major restriction when using legacy types is that you cannot build a
universal binary of your HPy extension (i.e. you cannot use :term:`HPy Universal
ABI`). The resulting binary will be specific to the Python interpreter used for
building. Therefore, the goal should always be to fully migrate to HPy pure
types.

A type with ``.legacy_slots != NULL`` is required to have
``HPyType_BuiltinShape_Legacy`` and to include ``PyObject_HEAD`` at the start of
its struct. It would be easy to relax this requirement on CPython (where the
``PyObject_HEAD`` fields are always present) but a large burden on other
implementations (e.g. PyPy, GraalPy) where a struct starting with
``PyObject_HEAD`` might not exist.

Types created via the old Python C API are automatically legacy types.

This section does not provide a dedicated example for how to create and use
legacy types because the :doc:`porting-example/index` already shows how that
is useful during incremental migration to HPy.

Inherit from a built-in type
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

HPy also supports inheriting from following built-in types:

  * ``type``

  * ``int``

  * ``float``

  * ``unicode``

  * ``tuple``

  * ``list``

Inheriting from built-in types is straight forward if you don't have a C
structure that represents your type. In other words, you can simply inherit
from, e.g., ``str`` if the ``basicsize`` in your type specification is ``0``.
For example:

.. literalinclude:: examples/hpytype-example/builtin_type.c
  :start-after: // BEGIN: spec_Dummy
  :end-before: // END: spec_Dummy

.. literalinclude:: examples/hpytype-example/builtin_type.c
  :start-after: // BEGIN: add_Dummy
  :end-before: // END: add_Dummy

This case is simple because there is no ``Dummy_AsStruct`` since there is no
associated C-level structure.

It is, however, more involved if your type also defines its own C structure
(i.e. ``basicsize > 0`` in the type specification). In this case, it is strictly
necessary to use the right *built-in shape*.

**What is the right built-in shape?**

This question is easy to answer: Each built-in shape (except of
:c:enumerator:`HPyType_BuiltinShape_Legacy`) represents a built-in type. You
need to use the built-in shape that fits to the specified base class. The
mapping is described in :c:enum:`HPyType_BuiltinShape`.

Let's do an example. Assume we want to define a type that stores the natural
language of a unicode string to the unicode object but the object should still
just behave like a Python unicode object. So, we define struct
``LanguageObject``:

.. literalinclude:: examples/hpytype-example/builtin_type.c
  :start-after: // BEGIN: LanguageObject
  :end-before: // END: LanguageObject

As you can see, we already specify the built-in shape here using
``HPyType_HELPERS(LanguageObject, HPyType_BuiltinShape_Unicode)``. Then, in the
type specification, we do:

.. literalinclude:: examples/hpytype-example/builtin_type.c
  :start-after: // BEGIN: spec_Language
  :end-before: // END: spec_Language

In the last step, when actually creating the type from the specification, we
need to define that its base class is ``str`` (aka. ``UnicodeType``):

.. literalinclude:: examples/hpytype-example/builtin_type.c
  :start-after: // BEGIN: add_Language
  :end-before: // END: add_Language

Function ``LanguageObject_AsStruct`` (which is generated by ``HPyType_HELPERS``)
will then return a pointer to ``LanguageObject``.

To summarize this: Specifying a type that inherits from a built-in type needs to
be considered in three places:

1. Pass the appropriate built-in shape to :c:macro:`HPyType_HELPERS`.
2. Assign ``SHAPE(TYPE)`` to :c:member:`HPyType_Spec.builtin_shape`.
3. Specify the desired base class in the type specification parameters.

For more information about the built-in shape and for a technical explanation
for why it is required, see :c:member:`HPyType_Spec.builtin_shape` and
:c:enum:`HPyType_BuiltinShape`.

More Examples
-------------

The :doc:`porting-example/index` shows another complete example
of HPy extension ported from Python/C API.

The `HPy project space <https://github.com/hpyproject/>`_ on GitHub
contains forks of some popular Python extensions ported to HPy as
a proof of concept/feasibility studies, such as the
`Kiwi solver <https://github.com/hpyproject/kiwi-hpy>`_.
Note that those forks may not be up to date with their upstream projects
or with the upstream HPy changes.

HPy unit tests
~~~~~~~~~~~~~~

HPy usually has tests for each API function. This means that there is lots of
examples available by looking at the tests. However, the test source uses
many macros and is hard to read. To overcome this we supply a utility to
export clean C sources for the tests. Since the HPy tests are not shipped by
default, you need to clone the HPy repository from GitHub:

.. code-block:: console

    > git clone https://github.com/hpyproject/hpy.git

After that, install all test requirements and dump the sources:

.. code-block:: console

    > cd hpy
    > python3 -m pip install pytest filelock
    > python3 -m pytest --dump-dir=test_sources test/

This will dump the generated test sources into folder ``test_sources``. Note,
that the tests won't be executed but skipped with an appropriate message.
