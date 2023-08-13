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

/* Internal header for all the files in hpy/debug/src. The public API is in
   include/hpy_debug.h
*/
#ifndef HPY_TRACE_INTERNAL_H
#define HPY_TRACE_INTERNAL_H

#include <assert.h>
#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#endif
#include "hpy.h"
#include "hpy_trace.h"


#define HPY_TRACE_MAGIC 0xF00BAA5

// frequency of nanosecond resolution
#define FREQ_NSEC 1000000000L

/* === HPyTraceInfo === */

#ifdef _WIN32
typedef LARGE_INTEGER _HPyTime_t;
typedef BOOL _HPyClockStatus_t;
#else
typedef struct timespec _HPyTime_t;
typedef int _HPyClockStatus_t;
#endif

typedef struct {
    long magic_number; // used just for sanity checks
    HPyContext *uctx;
    /* frequency of the used performance counter */
    _HPyTime_t counter_freq;
#ifdef _WIN32
    /* to_ns = FREQ_NS / counter_freq  */
    int64_t to_ns;
#endif
    /* call count of the corresponding HPy API function */
    uint64_t *call_counts;
    /* durations spent in the corresponding HPy API function */
    _HPyTime_t *durations;
    HPy on_enter_func;
    HPy on_exit_func;
} HPyTraceInfo;


static inline HPyTraceInfo *get_info(HPyContext *tctx)
{
    HPyTraceInfo *info = (HPyTraceInfo*)tctx->_private;
    assert(info->magic_number == HPY_TRACE_MAGIC); // sanity check
    return info;
}

/* Get the current value of the monotonic clock.  This is a platform-dependent
   operation. */
static inline _HPyClockStatus_t get_monotonic_clock(_HPyTime_t *t)
{
#ifdef _WIN32
    return (int)QueryPerformanceCounter(t);
#else
    return clock_gettime(CLOCK_MONOTONIC_RAW, t);
#endif
}

HPyTraceInfo *hpy_trace_on_enter(HPyContext *tctx, int id);
void hpy_trace_on_exit(HPyTraceInfo *info, int id, _HPyClockStatus_t r0,
        _HPyClockStatus_t r1, _HPyTime_t *_ts_start, _HPyTime_t *_ts_end);

#endif /* HPY_TRACE_INTERNAL_H */
