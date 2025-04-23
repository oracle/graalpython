#ifndef HPY_API_H
#define HPY_API_H

#include "hpy.h"

extern struct _HPyContext_s g_universal_ctx;

/* declare alloca() */
#if defined(_MSC_VER)
# include <malloc.h>   /* for alloca() */
#else
# include <stdint.h>
# if (defined (__SVR4) && defined (__sun)) || defined(_AIX) || defined(__hpux)
#  include <alloca.h>
# endif
#endif

#endif /* HPY_API_H */
