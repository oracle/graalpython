/* MIT License
 *
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates.
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

#ifndef HPY_TRACE_H
#define HPY_TRACE_H

#include "hpy.h"

/*
  This is the main public API for the trace mode, and it's meant to be used
  by hpy.universal implementations (including but not limited to the
  CPython's version of hpy.universal which is included in this repo).

  The idea is that for every uctx there is a corresponding unique tctx which
  wraps it.

  If you call hpy_trace_get_ctx twice on the same uctx, you get the same
  result.
*/

HPyContext * hpy_trace_get_ctx(HPyContext *uctx);
int hpy_trace_ctx_init(HPyContext *tctx, HPyContext *uctx);
int hpy_trace_ctx_free(HPyContext *tctx);
int hpy_trace_get_nfunc(void);
const char * hpy_trace_get_func_name(int idx);

// this is the HPy init function created by HPy_MODINIT. In CPython's version
// of hpy.universal the code is embedded inside the extension, so we can call
// this function directly instead of dlopen it. This is similar to what
// CPython does for its own built-in modules. But we must use the same
// signature as HPy_MODINIT

#ifdef ___cplusplus
extern "C"
#endif
HPy_EXPORTED_SYMBOL
HPyModuleDef* HPyInit__trace();

#ifdef ___cplusplus
extern "C"
#endif
HPy_EXPORTED_SYMBOL
void HPyInitGlobalContext__trace(HPyContext *ctx);

#endif /* HPY_TRACE_H */
