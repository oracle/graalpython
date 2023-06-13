/* MIT License
 *
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
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

#include <string.h>
#include <stdio.h>
#include "debug_internal.h"
#include "autogen_debug_ctx_init.h"
#include "hpy/runtime/ctx_funcs.h"
#if defined(_MSC_VER)
# include <malloc.h>   /* for alloca() */
#endif

_HPy_HIDDEN int hpy_debug_ctx_init(HPyContext *dctx, HPyContext *uctx)
{
    if (dctx->_private != NULL) {
        // already initialized
        assert(get_info(dctx)->uctx == uctx); // sanity check
        return 0;
    }
    // initialize debug_info
    // XXX: currently we never free this malloc
    HPyDebugInfo *info = malloc(sizeof(HPyDebugInfo));
    if (info == NULL) {
        HPyErr_NoMemory(uctx);
        return -1;
    }
    info->magic_number = HPY_DEBUG_MAGIC;
    info->uctx = uctx;
    info->current_generation = 0;
    info->uh_on_invalid_handle = HPy_NULL;
    info->closed_handles_queue_max_size = DEFAULT_CLOSED_HANDLES_QUEUE_MAX_SIZE;
    info->protected_raw_data_max_size = DEFAULT_PROTECTED_RAW_DATA_MAX_SIZE;
    info->handle_alloc_stacktrace_limit = 0;
    info->protected_raw_data_size = 0;
    DHQueue_init(&info->open_handles);
    DHQueue_init(&info->closed_handles);
    dctx->_private = info;
    debug_ctx_init_fields(dctx, uctx);
    return 0;
}

_HPy_HIDDEN void hpy_debug_ctx_free(HPyContext *dctx)
{
    free(dctx->_private);
}

HPy hpy_debug_open_handle(HPyContext *dctx, HPy uh)
{
    return DHPy_open(dctx, uh);
}

HPy hpy_debug_unwrap_handle(HPyContext *dctx, HPy dh)
{
    return DHPy_unwrap(dctx, dh);
}

void hpy_debug_close_handle(HPyContext *dctx, HPy dh)
{
    DHPy_close(dctx, dh);
}

// this function is supposed to be called from gdb: it tries to determine
// whether a handle is universal or debug by looking at the last bit
#ifndef _MSC_VER
__attribute__((unused))
#endif
static void hpy_magic_dump(HPyContext *uctx, HPy h)
{
    int universal = h._i & 1;
    if (universal)
        fprintf(stderr, "\nUniversal handle\n");
    else
        fprintf(stderr, "\nDebug handle\n");

#ifdef _MSC_VER
    fprintf(stderr, "raw value: %Ix (%Id)\n", h._i, h._i);
#else
    fprintf(stderr, "raw value: %lx (%ld)\n", h._i, h._i);
#endif
    if (universal)
        _HPy_Dump(uctx, h);
    else {
        DebugHandle *dh = as_DebugHandle(h);
#ifdef _MSC_VER
        fprintf(stderr, "dh->uh: %Ix\n", dh->uh._i);
#else
        fprintf(stderr, "dh->uh: %lx\n", dh->uh._i);
#endif
        _HPy_Dump(uctx, dh->uh);
    }
}

/* ~~~~~~~~~~ manually written wrappers ~~~~~~~~~~ */

void debug_ctx_Close(HPyContext *dctx, DHPy dh)
{
    UHPy uh = DHPy_unwrap(dctx, dh);
    DHPy_close(dctx, dh);
    HPy_Close(get_info(dctx)->uctx, uh);
}

const char *debug_ctx_Unicode_AsUTF8AndSize(HPyContext *dctx, DHPy h, HPy_ssize_t *size)
{
    const char *ptr = HPyUnicode_AsUTF8AndSize(get_info(dctx)->uctx, DHPy_unwrap(dctx, h), size);
    DebugHandle *handle = as_DebugHandle(h);
    HPy_ssize_t data_size;
    char* new_ptr;
    if (ptr != NULL)
    {
        data_size = size != NULL ? *size + 1 : (HPy_ssize_t) strlen(ptr) + 1;
        new_ptr = (char*) raw_data_copy(ptr, data_size, true);
    }
    else
    {
        data_size = 0;
        new_ptr = NULL;
    }
    handle->associated_data = new_ptr;
    handle->associated_data_size = data_size;
    return new_ptr;
}

DHPy debug_ctx_Tuple_FromArray(HPyContext *dctx, DHPy dh_items[], HPy_ssize_t n)
{
    UHPy *uh_items = (UHPy *)alloca(n * sizeof(UHPy));
    for(int i=0; i<n; i++) {
        uh_items[i] = DHPy_unwrap(dctx, dh_items[i]);
    }
    return DHPy_open(dctx, HPyTuple_FromArray(get_info(dctx)->uctx, uh_items, n));
}

DHPy debug_ctx_Type_GenericNew(HPyContext *dctx, DHPy dh_type, DHPy *dh_args,
                               HPy_ssize_t nargs, DHPy dh_kw)
{
    UHPy uh_type = DHPy_unwrap(dctx, dh_type);
    UHPy uh_kw = DHPy_unwrap(dctx, dh_kw);
    UHPy *uh_args = (UHPy *)alloca(nargs * sizeof(UHPy));
    for(int i=0; i<nargs; i++) {
        uh_args[i] = DHPy_unwrap(dctx, dh_args[i]);
    }
    return DHPy_open(dctx, HPyType_GenericNew(get_info(dctx)->uctx, uh_type, uh_args,
                                              nargs, uh_kw));
}

DHPy debug_ctx_Type_FromSpec(HPyContext *dctx, HPyType_Spec *spec, HPyType_SpecParam *dparams)
{
    // dparams might contain some hidden DHPy: we need to manually unwrap them.
    if (dparams != NULL) {
        // count the params
        HPy_ssize_t n = 1;
        for (HPyType_SpecParam *p = dparams; p->kind != 0; p++) {
            n++;
        }
        HPyType_SpecParam *uparams = (HPyType_SpecParam *)alloca(n * sizeof(HPyType_SpecParam));
        for (HPy_ssize_t i=0; i<n; i++) {
            uparams[i].kind = dparams[i].kind;
            uparams[i].object = DHPy_unwrap(dctx, dparams[i].object);
        }
        return DHPy_open(dctx, HPyType_FromSpec(get_info(dctx)->uctx, spec, uparams));
    }
    return DHPy_open(dctx, HPyType_FromSpec(get_info(dctx)->uctx, spec, NULL));
}

/* ~~~ debug mode implementation of HPyTracker ~~~ */
/* MOVED TO file 'debug_ctx_tracker.c' */


int debug_ctx_ContextVar_Get(HPyContext *dctx, DHPy context_var, DHPy defaul_value, DHPy *result) {
    HPy uresult;
    int ret = HPyContextVar_Get(get_info(dctx)->uctx,
                      DHPy_unwrap(dctx, context_var),
                      DHPy_unwrap(dctx, defaul_value),
                      &uresult);
    if (HPy_IsNull(uresult)) {
        *result = HPy_NULL;
    } else {
        *result = DHPy_open(dctx, uresult);
    }
    return ret;
}
