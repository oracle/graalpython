/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include "capi.h"

#include <stdio.h>

// export the more functions, because we use them dynamically in contrast to cpython on windows
PyAPI_FUNC(PyObject *) _Py_BuildValue_SizeT(const char *, ...);

#define FLAG_SIZE_T 1
typedef double va_double;

// taken from CPython "Python/modsupport.c"
int PyModule_AddObject(PyObject *mod, const char *name, PyObject *value) {
    int res = PyModule_AddObjectRef(mod, name, value);
    if (res == 0) {
        Py_DECREF(value);
    }
    return res;
}

// taken from CPython "Python/modsupport.c"
int PyModule_AddStringConstant(PyObject *m, const char *name, const char *value) {
    PyObject *obj = PyUnicode_FromString(value);
    if (!obj) {
        return -1;
    }
    int res = PyModule_AddObjectRef(m, name, obj);
    Py_DECREF(obj);
    return res;
}

// taken from CPython "Python/modsupport.c"
int PyModule_AddType(PyObject *module, PyTypeObject *type)
{
    if (PyType_Ready(type) < 0) {
        return -1;
    }

    const char *name = _PyType_Name(type);
    assert(name != NULL);

    return PyModule_AddObjectRef(module, name, (PyObject *)type);
}

// partially taken from CPython 3.6.4 "Python/getargs.c"
int _PyArg_UnpackStack(PyObject *const *args, Py_ssize_t nargs, const char *name, Py_ssize_t min, Py_ssize_t max, ...) {
    Py_ssize_t i;
    PyObject **o;

    assert(min >= 0);
    assert(min <= max);


    if (nargs < min) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at least "), min, nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at least "), min, nargs);
        return 0;
    }

    if (nargs == 0) {
        return 1;
    }

    if (nargs > max) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at most "), max, nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at most "), max, nargs);
        return 0;
    }

    va_list vargs;
    va_start(vargs, max);
    for (i = 0; i < nargs; i++) {
        o = va_arg(vargs, PyObject **);
        *o = args[i];
    }
    va_end(vargs);
    return 1;
}

int PyArg_UnpackTuple(PyObject *args, const char *name, Py_ssize_t min, Py_ssize_t max, ...) {
    Py_ssize_t i, l;
    PyObject **o;

    assert(min >= 0);
    assert(min <= max);
    if (!PyTuple_Check(args)) {
        PyErr_SetString(PyExc_SystemError,
            "PyArg_UnpackTuple() argument list is not a tuple");
        return 0;
    }
    l = PyTuple_GET_SIZE(args);
    if (l < min) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at least "), min, l);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at least "), min, l);
        return 0;
    }
    if (l == 0)
        return 1;
    if (l > max) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%s expected %s%zd arguments, got %zd",
                name, (min == max ? "" : "at most "), max, l);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd elements,"
                " but has %zd",
                (min == max ? "" : "at most "), max, l);
        return 0;
    }

    va_list vargs;
    va_start(vargs, max);
    for (i = 0; i < l; i++) {
        o = va_arg(vargs, PyObject **);
        *o = PyTuple_GET_ITEM(args, i);
    }
    va_end(vargs);
    return 1;
}

#undef _PyArg_NoKeywords
#undef _PyArg_NoPositional

// taken from CPython "Python/getargs.c"
int _PyArg_NoKeywords(const char *funcname, PyObject *kwargs) {
    if (kwargs == NULL) {
        return 1;
    }
    if (!PyDict_CheckExact(kwargs)) {
        PyErr_BadInternalCall();
        return 0;
    }
    if (PyDict_GET_SIZE(kwargs) == 0) {
        return 1;
    }

    PyErr_Format(PyExc_TypeError, "%.200s() takes no keyword arguments",
                    funcname);
    return 0;
}

// taken from CPython "Python/getargs.c"
int _PyArg_NoPositional(const char *funcname, PyObject *args) {
    if (args == NULL) {
        return 1;
    }
    if (!PyTuple_CheckExact(args)) {
        PyErr_BadInternalCall();
        return 0;
    }
    if (PyTuple_GET_SIZE(args) == 0) {
        return 1;
    }

    PyErr_Format(PyExc_TypeError, "%.200s() takes no positional arguments",
                    funcname);
    return 0;
}

// partially taken from CPython "Python/getargs.c", removed format processing as in our code, it should only be called with format == NULL
static int
parser_init(struct _PyArg_Parser *parser)
{
    const char * const *keywords;
    const char *format, *msg;
    int i, len, min, max, nkw;
    PyObject *kwtuple;

    assert(parser->keywords != NULL);
    if (parser->kwtuple != NULL) {
        return 1;
    }

    keywords = parser->keywords;
    /* scan keywords and count the number of positional-only parameters */
    for (i = 0; keywords[i] && !*keywords[i]; i++) {
    }
    parser->pos = i;
    /* scan keywords and get greatest possible nbr of args */
    for (; keywords[i]; i++) {
        if (!*keywords[i]) {
            PyErr_SetString(PyExc_SystemError,
                            "Empty keyword parameter name");
            return 0;
        }
    }
    len = i;

    nkw = len - parser->pos;
    kwtuple = PyTuple_New(nkw);
    if (kwtuple == NULL) {
        return 0;
    }
    keywords = parser->keywords + parser->pos;
    for (i = 0; i < nkw; i++) {
        PyObject *str = PyUnicode_FromString(keywords[i]);
        if (str == NULL) {
            Py_DECREF(kwtuple);
            return 0;
        }
        PyUnicode_InternInPlace(&str);
        PyTuple_SET_ITEM(kwtuple, i, str);
    }
    parser->kwtuple = kwtuple;

    return 1;
}

// taken from CPython "Python/getargs.c", changed comparison function
static PyObject*
find_keyword(PyObject *kwnames, PyObject *const *kwstack, PyObject *key)
{
    Py_ssize_t i, nkwargs;

    nkwargs = PyTuple_GET_SIZE(kwnames);
    for (i=0; i < nkwargs; i++) {
        PyObject *kwname = PyTuple_GET_ITEM(kwnames, i);

        /* ptr==ptr should match in most cases since keyword keys
           should be interned strings */
        if (kwname == key) {
            return kwstack[i];
        }
        if (!PyUnicode_Check(kwname)) {
            /* ignore non-string keyword keys:
               an error will be raised below */
            continue;
        }
        if (PyUnicode_Compare(kwname, key) == 0) {
            return kwstack[i];
        }
    }
    return NULL;
}

// taken from CPython "Python/getargs.c"
#undef _PyArg_UnpackKeywords
PyObject * const *
_PyArg_UnpackKeywords(PyObject *const *args, Py_ssize_t nargs,
                      PyObject *kwargs, PyObject *kwnames,
                      struct _PyArg_Parser *parser,
                      int minpos, int maxpos, int minkw,
                      PyObject **buf)
{
    PyObject *kwtuple;
    PyObject *keyword;
    int i, posonly, minposonly, maxargs;
    int reqlimit = minkw ? maxpos + minkw : minpos;
    Py_ssize_t nkwargs;
    PyObject *current_arg;
    PyObject * const *kwstack = NULL;

    assert(kwargs == NULL || PyDict_Check(kwargs));
    assert(kwargs == NULL || kwnames == NULL);

    if (parser == NULL) {
        PyErr_BadInternalCall();
        return NULL;
    }

    if (kwnames != NULL && !PyTuple_Check(kwnames)) {
        PyErr_BadInternalCall();
        return NULL;
    }

    if (args == NULL && nargs == 0) {
        args = buf;
    }

    if (!parser_init(parser)) {
        return NULL;
    }

    kwtuple = parser->kwtuple;
    posonly = parser->pos;
    minposonly = Py_MIN(posonly, minpos);
    maxargs = posonly + (int)PyTuple_GET_SIZE(kwtuple);

    if (kwargs != NULL) {
        nkwargs = PyDict_GET_SIZE(kwargs);
    }
    else if (kwnames != NULL) {
        nkwargs = PyTuple_GET_SIZE(kwnames);
        kwstack = args + nargs;
    }
    else {
        nkwargs = 0;
    }
    if (nkwargs == 0 && minkw == 0 && minpos <= nargs && nargs <= maxpos) {
        /* Fast path. */
        return args;
    }
    if (nargs + nkwargs > maxargs) {
        /* Adding "keyword" (when nargs == 0) prevents producing wrong error
           messages in some special cases (see bpo-31229). */
        PyErr_Format(PyExc_TypeError,
                     "%.200s%s takes at most %d %sargument%s (%zd given)",
                     (parser->fname == NULL) ? "function" : parser->fname,
                     (parser->fname == NULL) ? "" : "()",
                     maxargs,
                     (nargs == 0) ? "keyword " : "",
                     (maxargs == 1) ? "" : "s",
                     nargs + nkwargs);
        return NULL;
    }
    if (nargs > maxpos) {
        if (maxpos == 0) {
            PyErr_Format(PyExc_TypeError,
                         "%.200s%s takes no positional arguments",
                         (parser->fname == NULL) ? "function" : parser->fname,
                         (parser->fname == NULL) ? "" : "()");
        }
        else {
            PyErr_Format(PyExc_TypeError,
                         "%.200s%s takes %s %d positional argument%s (%zd given)",
                         (parser->fname == NULL) ? "function" : parser->fname,
                         (parser->fname == NULL) ? "" : "()",
                         (minpos < maxpos) ? "at most" : "exactly",
                         maxpos,
                         (maxpos == 1) ? "" : "s",
                         nargs);
        }
        return NULL;
    }
    if (nargs < minposonly) {
        PyErr_Format(PyExc_TypeError,
                     "%.200s%s takes %s %d positional argument%s"
                     " (%zd given)",
                     (parser->fname == NULL) ? "function" : parser->fname,
                     (parser->fname == NULL) ? "" : "()",
                     minposonly < maxpos ? "at least" : "exactly",
                     minposonly,
                     minposonly == 1 ? "" : "s",
                     nargs);
        return NULL;
    }

    /* copy tuple args */
    for (i = 0; i < nargs; i++) {
        buf[i] = args[i];
    }

    /* copy keyword args using kwtuple to drive process */
    for (i = Py_MAX((int)nargs, posonly); i < maxargs; i++) {
        if (nkwargs) {
            keyword = PyTuple_GET_ITEM(kwtuple, i - posonly);
            if (kwargs != NULL) {
                current_arg = PyDict_GetItemWithError(kwargs, keyword);
                if (!current_arg && PyErr_Occurred()) {
                    return NULL;
                }
            }
            else {
                current_arg = find_keyword(kwnames, kwstack, keyword);
            }
        }
        else if (i >= reqlimit) {
            break;
        }
        else {
            current_arg = NULL;
        }

        buf[i] = current_arg;

        if (current_arg) {
            --nkwargs;
        }
        else if (i < minpos || (maxpos <= i && i < reqlimit)) {
            /* Less arguments than required */
            keyword = PyTuple_GET_ITEM(kwtuple, i - posonly);
            PyErr_Format(PyExc_TypeError,  "%.200s%s missing required "
                         "argument '%U' (pos %d)",
                         (parser->fname == NULL) ? "function" : parser->fname,
                         (parser->fname == NULL) ? "" : "()",
                         keyword, i+1);
            return NULL;
        }
    }

    if (nkwargs > 0) {
        Py_ssize_t j;
        /* make sure there are no arguments given by name and position */
        for (i = posonly; i < nargs; i++) {
            keyword = PyTuple_GET_ITEM(kwtuple, i - posonly);
            if (kwargs != NULL) {
                current_arg = PyDict_GetItemWithError(kwargs, keyword);
                if (!current_arg && PyErr_Occurred()) {
                    return NULL;
                }
            }
            else {
                current_arg = find_keyword(kwnames, kwstack, keyword);
            }
            if (current_arg) {
                /* arg present in tuple and in dict */
                PyErr_Format(PyExc_TypeError,
                             "argument for %.200s%s given by name ('%U') "
                             "and position (%d)",
                             (parser->fname == NULL) ? "function" : parser->fname,
                             (parser->fname == NULL) ? "" : "()",
                             keyword, i+1);
                return NULL;
            }
        }
        /* make sure there are no extraneous keyword arguments */
        j = 0;
        while (1) {
            int match;
            if (kwargs != NULL) {
                if (!PyDict_Next(kwargs, &j, &keyword, NULL))
                    break;
            }
            else {
                if (j >= PyTuple_GET_SIZE(kwnames))
                    break;
                keyword = PyTuple_GET_ITEM(kwnames, j);
                j++;
            }

            if (!PyUnicode_Check(keyword)) {
                PyErr_SetString(PyExc_TypeError,
                                "keywords must be strings");
                return NULL;
            }
            match = PySequence_Contains(kwtuple, keyword);
            if (match <= 0) {
                if (!match) {
                    PyErr_Format(PyExc_TypeError,
                                 "'%U' is an invalid keyword "
                                 "argument for %.200s%s",
                                 keyword,
                                 (parser->fname == NULL) ? "this function" : parser->fname,
                                 (parser->fname == NULL) ? "" : "()");
                }
                return NULL;
            }
        }
    }

    return buf;
}

// Taken from CPython 3.8 getargs.c
void _PyArg_BadArgument(const char *fname, const char *displayname,
                   const char *expected, PyObject *arg) {
    PyErr_Format(PyExc_TypeError,
                 "%.200s() %.200s must be %.50s, not %.50s",
                 fname, displayname, expected,
                 arg == Py_None ? "None" : Py_TYPE(arg)->tp_name);
}

#undef _PyArg_CheckPositional

// Taken from CPython 3.8 getargs.c
int
_PyArg_CheckPositional(const char *name, Py_ssize_t nargs,
                       Py_ssize_t min, Py_ssize_t max)
{
    assert(min >= 0);
    assert(min <= max);

    if (nargs < min) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd argument%s, got %zd",
                name, (min == max ? "" : "at least "), min, min == 1 ? "" : "s", nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd element%s,"
                " but has %zd",
                (min == max ? "" : "at least "), min, min == 1 ? "" : "s", nargs);
        return 0;
    }

    if (nargs == 0) {
        return 1;
    }

    if (nargs > max) {
        if (name != NULL)
            PyErr_Format(
                PyExc_TypeError,
                "%.200s expected %s%zd argument%s, got %zd",
                name, (min == max ? "" : "at most "), max, max == 1 ? "" : "s", nargs);
        else
            PyErr_Format(
                PyExc_TypeError,
                "unpacked tuple should have %s%zd element%s,"
                " but has %zd",
                (min == max ? "" : "at most "), max, max == 1 ? "" : "s", nargs);
        return 0;
    }

    return 1;
}

/* The remainder of this file is Py_BuildValue implementation taken from CPython modsuport.c */
static PyObject *va_build_value(const char *, va_list, int);
static PyObject **va_build_stack(PyObject **small_stack, Py_ssize_t small_stack_len, const char *, va_list, int, Py_ssize_t*);

/* Helper for mkvalue() to scan the length of a format */

static Py_ssize_t
countformat(const char *format, char endchar)
{
    Py_ssize_t count = 0;
    int level = 0;
    while (level > 0 || *format != endchar) {
        switch (*format) {
        case '\0':
            /* Premature end */
            PyErr_SetString(PyExc_SystemError,
                            "unmatched paren in format");
            return -1;
        case '(':
        case '[':
        case '{':
            if (level == 0) {
                count++;
            }
            level++;
            break;
        case ')':
        case ']':
        case '}':
            level--;
            break;
        case '#':
        case '&':
        case ',':
        case ':':
        case ' ':
        case '\t':
            break;
        default:
            if (level == 0) {
                count++;
            }
        }
        format++;
    }
    return count;
}


/* Generic function to create a value -- the inverse of getargs() */
/* After an original idea and first implementation by Steven Miale */

static PyObject *do_mktuple(const char**, va_list *, char, Py_ssize_t, int);
static int do_mkstack(PyObject **, const char**, va_list *, char, Py_ssize_t, int);
static PyObject *do_mklist(const char**, va_list *, char, Py_ssize_t, int);
static PyObject *do_mkdict(const char**, va_list *, char, Py_ssize_t, int);
static PyObject *do_mkvalue(const char**, va_list *, int);


static void
do_ignore(const char **p_format, va_list *p_va, char endchar, Py_ssize_t n, int flags)
{
    PyObject *v;
    Py_ssize_t i;
    assert(PyErr_Occurred());
    v = PyTuple_New(n);
    for (i = 0; i < n; i++) {
        PyObject *exception, *value, *tb, *w;

        PyErr_Fetch(&exception, &value, &tb);
        w = do_mkvalue(p_format, p_va, flags);
        PyErr_Restore(exception, value, tb);
        if (w != NULL) {
            if (v != NULL) {
                PyTuple_SET_ITEM(v, i, w);
            }
            else {
                Py_DECREF(w);
            }
        }
    }
    Py_XDECREF(v);
    if (**p_format != endchar) {
        PyErr_SetString(PyExc_SystemError,
                        "Unmatched paren in format");
        return;
    }
    if (endchar) {
        ++*p_format;
    }
}

static PyObject *
do_mkdict(const char **p_format, va_list *p_va, char endchar, Py_ssize_t n, int flags)
{
    PyObject *d;
    Py_ssize_t i;
    if (n < 0)
        return NULL;
    if (n % 2) {
        PyErr_SetString(PyExc_SystemError,
                        "Bad dict format");
        do_ignore(p_format, p_va, endchar, n, flags);
        return NULL;
    }
    /* Note that we can't bail immediately on error as this will leak
       refcounts on any 'N' arguments. */
    if ((d = PyDict_New()) == NULL) {
        do_ignore(p_format, p_va, endchar, n, flags);
        return NULL;
    }
    for (i = 0; i < n; i+= 2) {
        PyObject *k, *v;

        k = do_mkvalue(p_format, p_va, flags);
        if (k == NULL) {
            do_ignore(p_format, p_va, endchar, n - i - 1, flags);
            Py_DECREF(d);
            return NULL;
        }
        v = do_mkvalue(p_format, p_va, flags);
        if (v == NULL || PyDict_SetItem(d, k, v) < 0) {
            do_ignore(p_format, p_va, endchar, n - i - 2, flags);
            Py_DECREF(k);
            Py_XDECREF(v);
            Py_DECREF(d);
            return NULL;
        }
        Py_DECREF(k);
        Py_DECREF(v);
    }
    if (**p_format != endchar) {
        Py_DECREF(d);
        PyErr_SetString(PyExc_SystemError,
                        "Unmatched paren in format");
        return NULL;
    }
    if (endchar)
        ++*p_format;
    return d;
}

static PyObject *
do_mklist(const char **p_format, va_list *p_va, char endchar, Py_ssize_t n, int flags)
{
    PyObject *v;
    Py_ssize_t i;
    if (n < 0)
        return NULL;
    /* Note that we can't bail immediately on error as this will leak
       refcounts on any 'N' arguments. */
    v = PyList_New(n);
    if (v == NULL) {
        do_ignore(p_format, p_va, endchar, n, flags);
        return NULL;
    }
    for (i = 0; i < n; i++) {
        PyObject *w = do_mkvalue(p_format, p_va, flags);
        if (w == NULL) {
            do_ignore(p_format, p_va, endchar, n - i - 1, flags);
            Py_DECREF(v);
            return NULL;
        }
        PyList_SET_ITEM(v, i, w);
    }
    if (**p_format != endchar) {
        Py_DECREF(v);
        PyErr_SetString(PyExc_SystemError,
                        "Unmatched paren in format");
        return NULL;
    }
    if (endchar)
        ++*p_format;
    return v;
}

static int
do_mkstack(PyObject **stack, const char **p_format, va_list *p_va,
           char endchar, Py_ssize_t n, int flags)
{
    Py_ssize_t i;

    if (n < 0) {
        return -1;
    }
    /* Note that we can't bail immediately on error as this will leak
       refcounts on any 'N' arguments. */
    for (i = 0; i < n; i++) {
        PyObject *w = do_mkvalue(p_format, p_va, flags);
        if (w == NULL) {
            do_ignore(p_format, p_va, endchar, n - i - 1, flags);
            goto error;
        }
        stack[i] = w;
    }
    if (**p_format != endchar) {
        PyErr_SetString(PyExc_SystemError,
                        "Unmatched paren in format");
        goto error;
    }
    if (endchar) {
        ++*p_format;
    }
    return 0;

error:
    n = i;
    for (i=0; i < n; i++) {
        Py_DECREF(stack[i]);
    }
    return -1;
}

static PyObject *
do_mktuple(const char **p_format, va_list *p_va, char endchar, Py_ssize_t n, int flags)
{
    PyObject *v;
    Py_ssize_t i;
    if (n < 0)
        return NULL;
    /* Note that we can't bail immediately on error as this will leak
       refcounts on any 'N' arguments. */
    if ((v = PyTuple_New(n)) == NULL) {
        do_ignore(p_format, p_va, endchar, n, flags);
        return NULL;
    }
    for (i = 0; i < n; i++) {
        PyObject *w = do_mkvalue(p_format, p_va, flags);
        if (w == NULL) {
            do_ignore(p_format, p_va, endchar, n - i - 1, flags);
            Py_DECREF(v);
            return NULL;
        }
        PyTuple_SET_ITEM(v, i, w);
    }
    if (**p_format != endchar) {
        Py_DECREF(v);
        PyErr_SetString(PyExc_SystemError,
                        "Unmatched paren in format");
        return NULL;
    }
    if (endchar)
        ++*p_format;
    return v;
}

static PyObject *
do_mkvalue(const char **p_format, va_list *p_va, int flags)
{
#define ERROR_NEED_PY_SSIZE_T_CLEAN \
    { \
        PyErr_SetString(PyExc_SystemError, \
                        "PY_SSIZE_T_CLEAN macro must be defined for '#' formats"); \
        return NULL; \
    }

    for (;;) {
        switch (*(*p_format)++) {
        case '(':
            return do_mktuple(p_format, p_va, ')',
                              countformat(*p_format, ')'), flags);

        case '[':
            return do_mklist(p_format, p_va, ']',
                             countformat(*p_format, ']'), flags);

        case '{':
            return do_mkdict(p_format, p_va, '}',
                             countformat(*p_format, '}'), flags);

        case 'b':
        case 'B':
        case 'h':
        case 'i':
            return PyLong_FromLong((long)va_arg(*p_va, int));

        case 'H':
            return PyLong_FromLong((long)va_arg(*p_va, unsigned int));

        case 'I':
        {
            unsigned int n;
            n = va_arg(*p_va, unsigned int);
            return PyLong_FromUnsignedLong(n);
        }

        case 'n':
#if SIZEOF_SIZE_T!=SIZEOF_LONG
            return PyLong_FromSsize_t(va_arg(*p_va, Py_ssize_t));
#endif
            /* Fall through from 'n' to 'l' if Py_ssize_t is long */
        case 'l':
            return PyLong_FromLong(va_arg(*p_va, long));

        case 'k':
        {
            unsigned long n;
            n = va_arg(*p_va, unsigned long);
            return PyLong_FromUnsignedLong(n);
        }

        case 'L':
            return PyLong_FromLongLong((long long)va_arg(*p_va, long long));

        case 'K':
            return PyLong_FromUnsignedLongLong((long long)va_arg(*p_va, unsigned long long));

        case 'u':
        {
            PyObject *v;
            Py_UNICODE *u = va_arg(*p_va, Py_UNICODE *);
            Py_ssize_t n;
            if (**p_format == '#') {
                ++*p_format;
                if (flags & FLAG_SIZE_T) {
                    n = va_arg(*p_va, Py_ssize_t);
                }
                else {
                    n = va_arg(*p_va, int);
                    ERROR_NEED_PY_SSIZE_T_CLEAN;
                }
            }
            else
                n = -1;
            if (u == NULL) {
                v = Py_None;
                Py_INCREF(v);
            }
            else {
                if (n < 0)
                    n = wcslen(u);
                v = PyUnicode_FromWideChar(u, n);
            }
            return v;
        }
        case 'f':
        case 'd':
            return PyFloat_FromDouble(
                (double)va_arg(*p_va, va_double));

        case 'D':
            return PyComplex_FromCComplex(
                *((Py_complex *)va_arg(*p_va, Py_complex *)));

        case 'c':
        {
            char p[1];
            p[0] = (char)va_arg(*p_va, int);
            return PyBytes_FromStringAndSize(p, 1);
        }
        case 'C':
        {
            int i = va_arg(*p_va, int);
            return PyUnicode_FromOrdinal(i);
        }

        case 's':
        case 'z':
        case 'U':   /* XXX deprecated alias */
        {
            PyObject *v;
            const char *str = va_arg(*p_va, const char *);
            Py_ssize_t n;
            if (**p_format == '#') {
                ++*p_format;
                if (flags & FLAG_SIZE_T) {
                    n = va_arg(*p_va, Py_ssize_t);
                }
                else {
                    n = va_arg(*p_va, int);
                    ERROR_NEED_PY_SSIZE_T_CLEAN;
                }
            }
            else
                n = -1;
            if (str == NULL) {
                v = Py_None;
                Py_INCREF(v);
            }
            else {
                if (n < 0) {
                    size_t m = strlen(str);
                    if (m > PY_SSIZE_T_MAX) {
                        PyErr_SetString(PyExc_OverflowError,
                            "string too long for Python string");
                        return NULL;
                    }
                    n = (Py_ssize_t)m;
                }
                v = PyUnicode_FromStringAndSize(str, n);
            }
            return v;
        }

        case 'y':
        {
            PyObject *v;
            const char *str = va_arg(*p_va, const char *);
            Py_ssize_t n;
            if (**p_format == '#') {
                ++*p_format;
                if (flags & FLAG_SIZE_T) {
                    n = va_arg(*p_va, Py_ssize_t);
                }
                else {
                    n = va_arg(*p_va, int);
                    ERROR_NEED_PY_SSIZE_T_CLEAN;
                }
            }
            else
                n = -1;
            if (str == NULL) {
                v = Py_None;
                Py_INCREF(v);
            }
            else {
                if (n < 0) {
                    size_t m = strlen(str);
                    if (m > PY_SSIZE_T_MAX) {
                        PyErr_SetString(PyExc_OverflowError,
                            "string too long for Python bytes");
                        return NULL;
                    }
                    n = (Py_ssize_t)m;
                }
                v = PyBytes_FromStringAndSize(str, n);
            }
            return v;
        }

        case 'N':
        case 'S':
        case 'O':
        if (**p_format == '&') {
            typedef PyObject *(*converter)(void *);
            converter func = va_arg(*p_va, converter);
            void *arg = va_arg(*p_va, void *);
            ++*p_format;
            return (*func)(arg);
        }
        else {
            PyObject *v;
            v = va_arg(*p_va, PyObject *);
            if (v != NULL) {
                if (*(*p_format - 1) != 'N')
                    Py_INCREF(v);
            }
            else if (!PyErr_Occurred())
                /* If a NULL was passed
                 * because a call that should
                 * have constructed a value
                 * failed, that's OK, and we
                 * pass the error on; but if
                 * no error occurred it's not
                 * clear that the caller knew
                 * what she was doing. */
                PyErr_SetString(PyExc_SystemError,
                    "NULL object passed to Py_BuildValue");
            return v;
        }

        case ':':
        case ',':
        case ' ':
        case '\t':
            break;

        default:
            PyErr_SetString(PyExc_SystemError,
                "bad format char passed to Py_BuildValue");
            return NULL;

        }
    }

#undef ERROR_NEED_PY_SSIZE_T_CLEAN
}


PyObject *
Py_BuildValue(const char *format, ...)
{
    va_list va;
    PyObject* retval;
    va_start(va, format);
    retval = va_build_value(format, va, 0);
    va_end(va);
    return retval;
}

PyObject *
_Py_BuildValue_SizeT(const char *format, ...)
{
    va_list va;
    PyObject* retval;
    va_start(va, format);
    retval = va_build_value(format, va, FLAG_SIZE_T);
    va_end(va);
    return retval;
}

PyObject *
Py_VaBuildValue(const char *format, va_list va)
{
    return va_build_value(format, va, 0);
}

PyObject *
_Py_VaBuildValue_SizeT(const char *format, va_list va)
{
    return va_build_value(format, va, FLAG_SIZE_T);
}

static PyObject *
va_build_value(const char *format, va_list va, int flags)
{
    const char *f = format;
    Py_ssize_t n = countformat(f, '\0');
    va_list lva;
    PyObject *retval;

    if (n < 0)
        return NULL;
    if (n == 0) {
        Py_RETURN_NONE;
    }
    va_copy(lva, va);
    if (n == 1) {
        retval = do_mkvalue(&f, &lva, flags);
    } else {
        retval = do_mktuple(&f, &lva, '\0', n, flags);
    }
    va_end(lva);
    return retval;
}

PyObject **
_Py_VaBuildStack(PyObject **small_stack, Py_ssize_t small_stack_len,
                const char *format, va_list va, Py_ssize_t *p_nargs)
{
    return va_build_stack(small_stack, small_stack_len, format, va, 0, p_nargs);
}

PyObject **
_Py_VaBuildStack_SizeT(PyObject **small_stack, Py_ssize_t small_stack_len,
                       const char *format, va_list va, Py_ssize_t *p_nargs)
{
    return va_build_stack(small_stack, small_stack_len, format, va, FLAG_SIZE_T, p_nargs);
}

static PyObject **
va_build_stack(PyObject **small_stack, Py_ssize_t small_stack_len,
               const char *format, va_list va, int flags, Py_ssize_t *p_nargs)
{
    const char *f;
    Py_ssize_t n;
    va_list lva;
    PyObject **stack;
    int res;

    n = countformat(format, '\0');
    if (n < 0) {
        *p_nargs = 0;
        return NULL;
    }

    if (n == 0) {
        *p_nargs = 0;
        return small_stack;
    }

    if (n <= small_stack_len) {
        stack = small_stack;
    }
    else {
        stack = PyMem_Malloc(n * sizeof(stack[0]));
        if (stack == NULL) {
            PyErr_NoMemory();
            return NULL;
        }
    }

    va_copy(lva, va);
    f = format;
    res = do_mkstack(stack, &f, &lva, '\0', n, flags);
    va_end(lva);

    if (res < 0) {
        if (stack != small_stack) {
            PyMem_Free(stack);
        }
        return NULL;
    }

    *p_nargs = n;
    return stack;
}
