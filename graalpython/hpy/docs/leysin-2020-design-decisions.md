Leysin Sprint 2020 Design Decisions
===================================

Closing and duping HPy_NULL
---------------------------

Question: Should `HPy_Close` silently ignore attempts to close `HPy_NULL`?

Decisions:

* `HPy_Close` should function like `Py_XDECREF` and silently not close `HPy_NULL`.
* `HPy_Dup` should silently not dup `HPy_NULL` for symmetry with `HPy_Close`,
  i.e. `HPy_Dup(ctx, HPy_NULL)` should return `HPy_NULL`.
* Both the API and ABI versions should have this behaviour.
* We should add tests for this behaviour.


Attribute and item access
-------------------------

Question: Should we have separate HPySequence_GetItem and HPyMapping_GetItem
or just one HPy_GetItem? Should we explore the idea of protocols for such
access.

Decisions:

* We should start with `HPy_GetItem` and `HPy_SetItem` and `HPy_GetItem_i` and
  `HPy_SetItem_i` for item access and `HPy_GetAttr`, `HPy_SetAttr`,
  `HPy_GetAttr_s` and `HPy_SetAttr_s` for attribute access..
* We could add an `hpy/compat.h` that supports accessing the old Python mapping
  and sequence get item slots. For example, `HPyDict_GetItem_NotBorrowed`.
  `PyDict_GetItem` returns a borrower reference. `HPyDict_GetItem_NotBorrowed`
  should return a new handle that has to be closed.
* HPy API functions MUST NOT return a borrowed reference and we should add this
  to our official documentation.
* We should add an `HPyBuffer` protocol later (with the design still to be
  decided).

Proposed function signatures:

```C
HPy HPy_GetItem(HPyContext ctx, HPy obj, HPy idx);
HPy HPy_GetItem_i(HPyContext ctx, HPy obj, HPy_ssize_t idx);
HPy HPy_GetItem_s(HPyContext ctx, HPy obj, const char * idx); // UTF8 bytes

int HPy_SetItem(HPyContext ctx, HPy obj, HPy idx, HPy value);
int HPy_SetItem_i(HPyContext ctx, HPy obj, HPy_ssize_t idx, HPy value);
int HPy_SetItem_s(HPyContext ctx, HPy obj, const char * idx, HPy value); // UTF8 bytes

HPy HPy_GetAttr(HPyContext ctx, HPy obj, HPy idx);
HPy HPy_GetAttr_s(HPyContext ctx, HPy obj, const char * idx); // UTF8 bytes

int HPy_SetAttr(HPyContext ctx, HPy obj, HPy idx, HPy value);
int HPy_SetAttr_s(HPyContext ctx, HPy obj, const char * idx, HPy value); // UTF8 bytes
```

Macro for returning None
------------------------

Question: Should we have an HPy_RETURN_NONE macro?

Decisions:

* Yes we should, but it should be `HPy_RETURN_NONE(ctx)`.
* We should also change `HPy_IsNull(x)` to `HPy_IsNull(ctx, x)`.

Proposed macros:

```C
#define HPy_RETURN_NONE(ctx) return HPy_Dup(ctx, ctx->h_None);
#define HPy_IsNull(ctx, x) ...;
```

Passing handles as void pointers
--------------------------------

Question: Should we add `HPy_AsVoidP` and `HPy_FromVoidP` to the API? Should they
be on the ctx or not?

Decisions:

* Yes, they should be part of the API.
* They should take the ctx as an argument in case a future implementation needs
  it (much like `HPy_IsNull`).

Proposed functions:

```C
// universal
static inline HPy HPy_FromVoidP(HPyContext ctx, void *p) { return (HPy){(HPy_ssize_t)p}; }
static inline void* HPy_AsVoidP(HPyContext ctx, HPy h) { return (void*)h._i; }

// cpython (the -/+4 is to avoid people casting it to PyObject)
static inline HPy HPy_FromVoidP(HPyContext ctx, void *p) { return (HPy){(HPy_ssize_t)p - 4}; }
static inline void* HPy_AsVoidP(HPyContext ctx, HPy h) { return (void*)h._o + 4; }
```

/*
 * Should we implement HPy_Dump?
 *
 * Question: What should it print and where should it print it?
 *
 * Decision: It's useful for debugging if all macros are also available as functions
 *           definitions.
 *
 * Decision: It should dump to stderr just like PyObject_Dump.
 */

/*
 * How do we silence warnings from using HPy_METH_KEYWORDS?
 *
 * Decision: Write a cast to HPyMeth.
 */

{"add_ints_kw", (HPyMeth) add_ints_kw, HPy_METH_KEYWORDS, ""}

/*
 * How should HPyErr_Format be implemented? Should we avoid va_args?
 *
 * Decision:
 */

HPyErr_Format(ctx, const char *fmt, ...) {
  const char *msg = HPyStr_Format(ctx, fmt, ...);
  HPyErr_SetString(msg);
}

ctx->ctx_HPyErr_Format(???)

/*
 * Should make specifying values for optional arguments & dup / closing them
 * less messy?
 *
 * Decision: Right now, No. In the future, someone should invent
 *           ARGUMENT_CLINIC for HPy.
 */

/*
 * Should we rename "struct _object*" to "PyObject*" and "_HPy_PyCFunction"
 * to "PyCFunction" in universal/hpy.h?
 *
 * Decision: No. This would generate a warning if one imports Python.h.
 *
 * Decision: Comment in the code that this _object* is PyObject* and why we
 *           cannot call it that.
 *
 * Decision: In user documentation, just call ing PyObject *.
 */

/*
 * How should the API pass around exceptions?
 *
 * Decision: Follow CPython for each API call.
 *
 */

// E.g. API call returns HPy:

HPy h = HPyLong_FromLong(ctx, 5);
if (HPy_IsNull(ctx, h)) {
  // handle error
  // python error has been set by the API call
}

// or

HPy h = HPyLong_FromLong(ctx, 5);
if (HPyErr_Occurred(ctx)) {
  // handle error
  // python error has been set by the API call
}

// E.g. API call returns a value that is not an HPy:

long l = HPyLong_AsLong(ctx, h);
if (l == -1 && HPyErr_Occurred(ctx)) {
  // handle error
  // python error has been set by the API call
}

// E.g. API call returns a success or error flag:

// int error = HPyArg_Parse(...);
if (!HPyArg_Parse(...)) {
  // handle error
  // python error has been set by the API call
}

/*
 * How should support for creating custom Python types look?
 *
 * Decisions:
 *
 *
 */

// When using C-API:

typedef struct {
  PyObject_HEAD
     /* Type-specific fields go here. */
     PyObject *x;
     PyObject *y;
     double *data;
  int size;
} PointObject;

// When using HPy:

typedef struct {
    HPyObject_HEAD
    double x;
    double y;
} HPy_Point;

typedef struct {
    HPyObject_HEAD
    HPyField a;
    HPyField b;
} HPy_Rectangle;

/* Possible HPy code */

typedef struct {
    ??? ob_type;
} _HPy_ObjectHeader;

#define HPyObject_HEAD _HPy_ObjectHeader head;

#define HPy_STORE(ctx, obj, field, value) ((ctx)->ctx_HPy_StoreInto((_HPy_ObjectHeader *) obj, &((obj)->field), value))
#define HPy_STORE_INTO(ctx, obj, pointer, value) ((ctx)->ctx_HPy_StoreInto((_HPy_ObjectHeader *) obj, pointer, value))

// Using the debug mode ctx, this should check that obj->ob_type->tp_traverse is not NULL.
void HPy_StoreInto(HPyContext ctx, _HPy_ObjectHeader *obj, HPyField *pointer, HPy value)

// HPy_New:
//
// * Should return a handle.
// * It should be possible to go from the handle to struct, but not
//   from struct to handle. E.g. HPy_CAST(ctx, HPyRectangle, h) -> HPyRectangle,
//   but no inverse.
// * We should not have an HPy_Init at the moment -- HPy_New both allocates
//   the object and initializes it. We will add separate allocation and init
//   later if we encounter a need for it.
// * HPyTypeSpec should follow the CPython type spec.

#define HPy_CAST(ctx, return_type, h) (return_type *) ctx->ctx_HPy_Cast(h)

void* HPy_Cast(ctx, HPy h);

HPy HPy_TypeFromSpec(ctx, HPyTypeSpec type_spec);
HPy HPy_New(ctx, HPy h_type);

/* end of possible HPy code */

HPy new_rect(HPy p1, HPy p2) {
    // HPy_New always initialize the whole object to 0. We can also have
    // HPy_NewUninitialized if we don't want to pay the penalty
    HPy_Rectangle *rect;
    HPy rect_handle = HPy_New(ctx, HPy_Rectangle, h_rectangle_type, &rect);

    // HPy_Store does a write barrier on PyPy, and DECREF the old rect->a on
    // CPython if needed
    HPy_Store(ctx, rect, a, p1);
    HPy_Store(ctx, rect, b, p2);
    return rect_handle;
}

double calc_diagonal(HPy rect_handle) {
    // rect is valid until rect_handle is closed. on PyPy we pin the object, and
    // we unpin it when we close the handle
    HPy_Rectangle *rect = HPy_Cast(HPy_Rectangle, rect_handle);

    // HPy_Load reads a field and turn it into a handle
    HPy p1_handle = HPy_Load(rect->a); // p1 is a handle which must be closed
    HPy_Point *p1 = HPy_Cast(HPy_Point, p1_handle);

    // for C99 compilers, we can also provide a macro which declares p2 and
    // p2_handle automatically and does the equivalent of the two lines above
    HPY_LOAD(HPy_Point, p2, rect->b);

    double diag = sqrt(p1->x - p2->x /* etc. etc. */);

    // close all the handles
    HPy_Close(p1_handle);
    HPy_Close(p2_handle);

    return diag;
}

/*
 * Should HPy support Python without the GIL?
 *
 * Problem left as an exercise for the reader.
 */
