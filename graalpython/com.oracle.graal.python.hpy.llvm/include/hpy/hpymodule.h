/* MIT License
 *
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPY_UNIVERSAL_HPYMODULE_H
#define HPY_UNIVERSAL_HPYMODULE_H
// Copied from Python's exports.h
#ifndef Py_EXPORTED_SYMBOL
    #if defined(_WIN32) || defined(__CYGWIN__)
        #define Py_EXPORTED_SYMBOL __declspec(dllexport)
    #else
        #define Py_EXPORTED_SYMBOL __attribute__ ((visibility ("default")))
    #endif
#endif


// this is defined by HPy_MODINIT
extern HPyContext *_ctx_for_trampolines;

typedef struct {
    const char* name;
    const char* doc;
    HPy_ssize_t size;
    cpy_PyMethodDef *legacy_methods;
    HPyDef **defines;   /* points to an array of 'HPyDef *' */
    /* array with pointers to statically allocated HPyGlobal,
     * with NULL at the end as a sentinel. */
    HPyGlobal **globals;
} HPyModuleDef;


#if defined(__cplusplus)
#  define HPyMODINIT_FUNC extern "C" Py_EXPORTED_SYMBOL HPy
#else /* __cplusplus */
#  define HPyMODINIT_FUNC Py_EXPORTED_SYMBOL HPy
#endif /* __cplusplus */

#ifdef HPY_UNIVERSAL_ABI

// module initialization in the universal case
#define HPy_MODINIT(modname)                                   \
    _HPy_HIDDEN HPyContext *_ctx_for_trampolines;              \
    static HPy init_##modname##_impl(HPyContext *ctx);         \
    HPyMODINIT_FUNC                                         \
    HPyInit_##modname(HPyContext *ctx)                     \
    {                                                          \
        _ctx_for_trampolines = ctx;                            \
        return init_##modname##_impl(ctx);                     \
    }

#else // HPY_UNIVERSAL_ABI

// module initialization in the CPython case
#define HPy_MODINIT(modname)                                   \
    static HPy init_##modname##_impl(HPyContext *ctx);         \
    PyMODINIT_FUNC                                             \
    PyInit_##modname(void)                                     \
    {                                                          \
        return _h2py(init_##modname##_impl(_HPyGetContext())); \
    }

#endif // HPY_UNIVERSAL_ABI

#endif // HPY_UNIVERSAL_HPYMODULE_H
