/* MIT License
 *  
 * Copyright (c) 2020, Oracle and/or its affiliates. 
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

#ifndef HPY_UNIVERSAL_H
#define HPY_UNIVERSAL_H

#include <stdlib.h>
#include <stdint.h>
#include <stdarg.h>

typedef intptr_t HPy_ssize_t;

struct _HPy_s { void* _i; };
typedef struct _HPy_s HPy;

typedef struct _HPyContext_s *HPyContext;
struct _object;  /* that's PyObject inside CPython */
typedef struct _object *(*_HPy_CPyCFunction)(struct _object *self,
                                             struct _object *args);

#ifdef __GNUC__
#define _HPy_HIDDEN  __attribute__((visibility("hidden")))
#else
#define _HPy_HIDDEN
#endif /* __GNUC__ */

#define HPyAPI_RUNTIME_FUNC(restype) _HPy_HIDDEN restype

#define HPy_NULL ((HPy){NULL})
#define HPy_IsNull(x) ((x)._i == NULL)

// XXX: we need to decide whether these are part of the official API or not,
// and maybe introduce a better naming convetion. For now, they are needed for
// ujson
static inline HPy HPy_FromVoidP(void *p) { return (HPy){p}; }
static inline void* HPy_AsVoidP(HPy h) { return h._i; }

// include runtime functions
#include "common/runtime.h"

#include "meth.h"
#include "module.h"

#include "autogen_ctx.h"
#include "autogen_trampolines.h"

#endif /* HPY_UNIVERSAL_H */
