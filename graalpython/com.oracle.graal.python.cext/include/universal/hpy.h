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
