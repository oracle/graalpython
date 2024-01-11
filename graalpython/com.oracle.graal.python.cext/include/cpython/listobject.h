/* Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#ifndef Py_CPYTHON_LISTOBJECT_H
#  error "this header file must not be included directly"
#endif

typedef struct {
    PyObject_VAR_HEAD
    /* Vector of pointers to list elements.  list[0] is ob_item[0], etc. */
    PyObject **Py_HIDE_IMPL_FIELD(ob_item);

    /* ob_item contains space for 'allocated' elements.  The number
     * currently in use is ob_size.
     * Invariants:
     *     0 <= ob_size <= allocated
     *     len(list) == ob_size
     *     ob_item == NULL implies ob_size == allocated == 0
     * list.sort() temporarily sets allocated to -1 to detect mutations.
     *
     * Items must normally not be NULL, except during construction when
     * the list is not yet visible outside the function that builds it.
     */
    Py_ssize_t allocated;
} PyListObject;

PyAPI_FUNC(PyObject *) _PyList_Extend(PyListObject *, PyObject *);
PyAPI_FUNC(void) _PyList_DebugMallocStats(FILE *out);

/* Cast argument to PyListObject* type. */
#define _PyList_CAST(op) \
    (assert(PyList_Check(op)), _Py_CAST(PyListObject*, (op)))

// Macros and static inline functions, trading safety for speed

static inline Py_ssize_t PyList_GET_SIZE(PyObject *op) {
    PyListObject *list = _PyList_CAST(op);
    return Py_SIZE(list);
}
#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 < 0x030b0000
#  define PyList_GET_SIZE(op) PyList_GET_SIZE(_PyObject_CAST(op))
#endif

#define PyList_GET_ITEM(op, index) (PyList_GetItem((PyObject*)(op), (index)))

PyAPI_FUNC(void) PyTruffleList_SET_ITEM(PyObject *op, Py_ssize_t index, PyObject *value);
static inline void
PyList_SET_ITEM(PyObject *op, Py_ssize_t index, PyObject *value) {
    PyTruffleList_SET_ITEM(op, index, value);
}
#if !defined(Py_LIMITED_API) || Py_LIMITED_API+0 < 0x030b0000
#define PyList_SET_ITEM(op, index, value) \
    PyList_SET_ITEM(_PyObject_CAST(op), index, _PyObject_CAST(value))
#endif
