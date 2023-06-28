/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This implementation emulate CPython's lzma
 * see cpython/Modules/_lzmamodule.c
 */

#define DEBUG 300
// #define BENCHMARK

#include <limits.h>
#include <stdlib.h>
#include <string.h>


#include <lzma.h>

typedef uint8_t  Byte;  /* 8 bits */

// Integer.MAX_INT
#define GRAALPYTHON_MAX_SIZE (INT_MAX)

#define MAX_FILTERS_SIZE (LZMA_FILTERS_MAX + 1)
#define LZMA_CHECK_UNKNOWN (LZMA_CHECK_ID_MAX + 1)

#define FORMAT_AUTO_INDEX 0 // nfi_var
#define FORMAT_XZ_INDEX 1 // nfi_var
#define FORMAT_ALONE_INDEX 2 // nfi_var
#define FORMAT_RAW_INDEX 3 // nfi_var

#define CHECK_NONE_INDEX 0 // nfi_var
#define CHECK_CRC32_INDEX 1 // nfi_var
#define CHECK_CRC64_INDEX 2 // nfi_var
#define CHECK_SHA256_INDEX 3 // nfi_var
#define CHECK_ID_MAX_INDEX 4 // nfi_var
#define CHECK_UNKNOWN_INDEX 5 // nfi_var

#define FILTER_LZMA1_INDEX 0 // nfi_var
#define FILTER_LZMA2_INDEX 1 // nfi_var
#define FILTER_DELTA_INDEX 2 // nfi_var
#define FILTER_X86_INDEX 3 // nfi_var
#define FILTER_POWERPC_INDEX 4 // nfi_var
#define FILTER_IA64_INDEX 5 // nfi_var
#define FILTER_ARM_INDEX 6 // nfi_var
#define FILTER_ARMTHUMB_INDEX 7 // nfi_var
#define FILTER_SPARC_INDEX 8 // nfi_var

#define MF_HC3_INDEX 0 // nfi_var
#define MF_HC4_INDEX 1 // nfi_var
#define MF_BT2_INDEX 2 // nfi_var
#define MF_BT3_INDEX 3 // nfi_var
#define MF_BT4_INDEX 4 // nfi_var

#define MODE_FAST_INDEX 0 // nfi_var
#define MODE_NORMAL_INDEX 1 // nfi_var

#define PRESET_DEFAULT_INDEX 0 // nfi_var
#define PRESET_EXTREME_INDEX 1 // nfi_var


#define ID_INDEX           0 // nfi_var
#define PRESET_INDEX       1 // nfi_var
#define DICT_SIZE_INDEX    2 // nfi_var
#define LC_INDEX           3 // nfi_var
#define LP_INDEX           4 // nfi_var
#define PB_INDEX           5 // nfi_var
#define MODE_INDEX         6 // nfi_var
#define NICE_LEN_INDEX     7 // nfi_var
#define MF_INDEX           8 // nfi_var
#define DEPTH_INDEX        9 // nfi_var

#define DIST_INDEX         1 // nfi_var

#define START_OFFSET_INDEX 1 // nfi_var

#define MAX_OPTS_INDEX     10 // nfi_var


#define LZMA_ID_ERROR      98  // nfi_var
#define LZMA_PRESET_ERROR  99  // nfi_var

#ifndef NDEBUG
    #include <stdio.h>
    #include <stdarg.h>
    static void debug_log(int level, char *file, int line, char *format, ...) {
        if (DEBUG > level) {
            return;
        }

        char *type = "ALL";
        switch (level) {
            case 1000: type = "SEVERE"; break;
            case 900: type = "WARNING"; break;
            case 800: type = "INFO"; break;
            case 700: type = "CONFIG"; break;
            case 500: type = "FINE"; break;
            case 400: type = "FINER"; break;
            case 300: type = "FINEST"; break;
        }

        va_list args;
        va_start(args, format);
        fprintf(stderr, "[%s %s:%d] ", type, file, line);
        vfprintf(stderr, format, args);
        // fprintf(stderr, "\n");

        va_end(args);
    }

    #define LOG_SEVERE(format, ...) \
          debug_log(1000, __FILE__, __LINE__, format, ## __VA_ARGS__);
    #define LOG_WARNING(format, ...) \
          debug_log(900, __FILE__, __LINE__, format, ## __VA_ARGS__);
    #define LOG_INFO(format, ...) \
          debug_log(800, __FILE__, __LINE__, format, ## __VA_ARGS__);
    #define LOG_CONFIG(format, ...) \
          debug_log(700, __FILE__, __LINE__, format, ## __VA_ARGS__);
    #define LOG_FINE(format, ...) \
          debug_log(500, __FILE__, __LINE__, format, ## __VA_ARGS__);
    #define LOG_FINER(format, ...) \
          debug_log(400, __FILE__, __LINE__, format, ## __VA_ARGS__);
    #define LOG_FINEST(format, ...) \
          debug_log(300, __FILE__, __LINE__, format, ## __VA_ARGS__);
#else
    #define LOG_SEVERE(format, ...)
    #define LOG_WARNING(format, ...)
    #define LOG_INFO(format, ...)
    #define LOG_CONFIG(format, ...)
    #define LOG_FINE(format, ...)
    #define LOG_FINER(format, ...)
    #define LOG_FINEST(format, ...)
#endif


#ifdef BENCHMARK
    #include <sys/time.h>
    #include <sys/resource.h>
    double get_time()
    {
        struct timeval t;
        struct timezone tzp;
        gettimeofday(&t, &tzp);
        return t.tv_sec + t.tv_usec*1e-6;
    }
    #define START_TIME double start_time = get_time();
    #define END_TIME(lzmast) lzmast->timeElapsed += get_time() - start_time;
#else
    #define START_TIME
    #define END_TIME(lzmast)
#endif

/* Container formats: */
enum {
    FORMAT_AUTO,
    FORMAT_XZ,
    FORMAT_ALONE,
    FORMAT_RAW,
};

// The ref_count is important for `copy` as we 
// share off heap storage between native objects.
typedef struct
{
    size_t ref_count;
    size_t size;
    Byte *buf;
} off_heap_buffer;

#define NOT_INITIALIZED 0
#define INITIALIZED 1

typedef struct
{
    lzma_allocator alloc;
    lzma_stream lzs;
    int lzs_type;

    lzma_filter* filters;
    int check;

    off_heap_buffer *output;
    size_t output_size;

    ssize_t next_in_index;
#ifdef BENCHMARK
    double timeElapsed;
#endif
} lzmast_stream;

// nfi_function: name('getMarcos') static(true)
void get_macros(int* formats, int* checks, uint64_t* filters, int* mfs, int* modes, uint64_t* preset) {
    formats[FORMAT_AUTO_INDEX] = FORMAT_AUTO;
    formats[FORMAT_XZ_INDEX] = FORMAT_XZ;
    formats[FORMAT_ALONE_INDEX] = FORMAT_ALONE;
    formats[FORMAT_RAW_INDEX] = FORMAT_RAW;

    checks[CHECK_NONE_INDEX] = LZMA_CHECK_NONE;
    checks[CHECK_CRC32_INDEX] = LZMA_CHECK_CRC32;
    checks[CHECK_CRC64_INDEX] = LZMA_CHECK_CRC64;
    checks[CHECK_SHA256_INDEX] = LZMA_CHECK_SHA256;
    checks[CHECK_ID_MAX_INDEX] = LZMA_CHECK_ID_MAX;
    checks[CHECK_UNKNOWN_INDEX] = LZMA_CHECK_UNKNOWN;
    
    filters[FILTER_LZMA1_INDEX] = LZMA_FILTER_LZMA1;
    filters[FILTER_LZMA2_INDEX] = LZMA_FILTER_LZMA2;
    filters[FILTER_DELTA_INDEX] = LZMA_FILTER_DELTA;
    filters[FILTER_X86_INDEX] = LZMA_FILTER_X86;
    filters[FILTER_POWERPC_INDEX] = LZMA_FILTER_POWERPC;
    filters[FILTER_IA64_INDEX] = LZMA_FILTER_IA64;
    filters[FILTER_ARM_INDEX] = LZMA_FILTER_ARM;
    filters[FILTER_ARMTHUMB_INDEX] = LZMA_FILTER_ARMTHUMB;
    filters[FILTER_SPARC_INDEX] = LZMA_FILTER_SPARC;
    
    mfs[MF_HC3_INDEX] = LZMA_MF_HC3;
    mfs[MF_HC4_INDEX] = LZMA_MF_HC4;
    mfs[MF_BT2_INDEX] = LZMA_MF_BT2;
    mfs[MF_BT3_INDEX] = LZMA_MF_BT3;
    mfs[MF_BT4_INDEX] = LZMA_MF_BT4;

    modes[MODE_FAST_INDEX] = LZMA_MODE_FAST;
    modes[MODE_NORMAL_INDEX] = LZMA_MODE_NORMAL;
    
    preset[PRESET_DEFAULT_INDEX] = LZMA_PRESET_DEFAULT;
    preset[PRESET_EXTREME_INDEX] = LZMA_PRESET_EXTREME;
}

static void* LZMA_Malloc(void* ctx, size_t items, size_t size)
{
    void *m = malloc((size_t)items * (size_t)size);
    LOG_FINER("malloc(address: %p, items: %u, size: %u)\n", m, items, size);
    return m; 
}

static void LZMA_Free(void* ctx, void *ptr)
{
    LOG_FINER("free(%p)\n", ptr);
    free(ptr);
}

static off_heap_buffer* lzma_allocate_buffer(size_t items)
{
    size_t size = items * sizeof(Byte);
    if (!size) {
        LOG_SEVERE("Creating Empty Buffer!!\n");
        size = sizeof(Byte);
    }
    off_heap_buffer *o = (off_heap_buffer*) malloc(sizeof(off_heap_buffer));
    if (!o) {
        return NULL;
    }
    Byte *buf = (Byte*) malloc(size);
    if (!buf) {
        LOG_SEVERE("Memory Error!!\n");
    }
    o->ref_count = 1;
    o->buf = buf;
    o->size = items;
    LOG_FINER("malloc[off_heap_buffer](address: %p, buf: %p, items: %zu, ref_count: %zu)\n", o, buf, items, o->ref_count);
    return o;
}

static off_heap_buffer* lzma_create_copy_buffer(Byte *src, ssize_t len) {
    off_heap_buffer *dest = lzma_allocate_buffer(len);
    if (dest && len > 0) {
        memcpy(dest->buf, src, len);
    }
    return dest; 
}

static void lzma_release_buffer(off_heap_buffer *o) {
    if (!o) {
        return;
    }

    if (o->ref_count == 0) {
        LOG_SEVERE("trying to double free(%p)!!\n", o);
        return;
    }

    LOG_FINEST("off_heap_buffer(address: %p, ref_count: %zu - 1)\n", o, o->ref_count);

    o->ref_count--;
    if (o->ref_count > 0) {
        return;
    }

    LOG_FINER("free(%p)\n", o);

    free(o->buf);
    free(o);
}

static off_heap_buffer* lzma_get_ref(off_heap_buffer* o) {
    if (o) {
        LOG_FINEST("off_heap_buffer(ref_count: %zu + 1)\n", o->ref_count);
        o->ref_count++;
    }
    return o;
}

// nfi_function: name('createStream') map('lzmast_stream*', 'POINTER')
lzmast_stream *lzma_create_lzmast_stream() {
    lzmast_stream *lzmast = (lzmast_stream *) calloc(1, sizeof(lzmast_stream));
    lzmast->alloc.opaque = NULL;
    lzmast->alloc.alloc = LZMA_Malloc;
    lzmast->alloc.free = LZMA_Free;
    lzmast->lzs.allocator = &lzmast->alloc;
    lzmast->lzs_type = NOT_INITIALIZED;
    lzmast->output = lzma_allocate_buffer(1);
    lzmast->output->size = 0;
    lzmast->output_size = 0;
    lzmast->next_in_index = 0;
    lzmast->filters = NULL;
    lzmast->check = LZMA_CHECK_UNKNOWN;

#ifdef BENCHMARK
    lzmast->timeElapsed = 0;
#endif
    LOG_INFO("lzmast_stream(%p)\n", lzmast);
    return lzmast;
}

// nfi_function: name('getTimeElapsed') map('lzmast_stream*', 'POINTER')  static(true)
double lzma_get_timeElapsed(lzmast_stream* lzmast) {
#ifdef BENCHMARK
    double t = lzmast->timeElapsed;
    LOG_FINEST("time Elapsed: %.2f\n", t);
    lzmast->timeElapsed = 0;
    return t;
#else
    return -1.0;
#endif
}

static void initFilters(lzmast_stream *lzmast) {
    if (lzmast->filters == NULL) {
        lzmast->filters = calloc(1, MAX_FILTERS_SIZE * sizeof(lzma_filter));
        for (int i = 0; i < MAX_FILTERS_SIZE; i++) {
            lzmast->filters[i].id = LZMA_VLI_UNKNOWN;
        }
    }
}

static void
free_filter_chain(lzmast_stream *lzmast) {
    if (lzmast->filters) {
        for (int i = 0; lzmast->filters[i].id != LZMA_VLI_UNKNOWN; i++) {
            if (lzmast->filters[i].options) {
                free(lzmast->filters[i].options);
            }
        }
        free(lzmast->filters);
        lzmast->filters = NULL;
    }
}

// nfi_function: name('deallocateStream') map('lzmast_stream*', 'POINTER')
void lzma_free_stream(lzmast_stream* lzmast) {
    if (!lzmast) {
        return;
    }
    if(lzmast->filters) {
        free_filter_chain(lzmast);
    }
    if(lzmast->lzs_type) {
        if (lzmast->lzs_type == INITIALIZED) {
            lzma_end(&lzmast->lzs);
        }
    }
    lzma_release_buffer(lzmast->output);
    LOG_INFO("free lzmast_stream(%p)\n", lzmast);
    free(lzmast);
}

// nfi_function: name('gcReleaseHelper') map('lzmast_stream*', 'POINTER') release(true)
void lzma_gc_helper(lzmast_stream* lzmast) {
    lzma_free_stream(lzmast);
}

// nfi_function: name('getNextInIndex') map('lzmast_stream*', 'POINTER')
ssize_t lzma_get_next_in_index(lzmast_stream *lzmast) {
    return lzmast->next_in_index;
}

// nfi_function: name('getLzsAvailIn') map('lzmast_stream*', 'POINTER')
size_t lzma_get_lzs_avail_in(lzmast_stream *lzmast) {
    return lzmast->lzs.avail_in;
}

// nfi_function: name('getLzsAvailOut') map('lzmast_stream*', 'POINTER')
size_t lzma_get_lzs_avail_out(lzmast_stream *lzmast) {
    return lzmast->lzs.avail_out;
}

// nfi_function: name('getLzsCheck') map('lzmast_stream*', 'POINTER')
int lzma_lzma_get_check(lzmast_stream *lzmast) {
    return lzmast->check;
}

// nfi_function: name('setLzsAvailIn') map('lzmast_stream*', 'POINTER')
void lzma_set_lzs_avail_in(lzmast_stream *lzmast, size_t v) {
    lzmast->lzs.avail_in = v;
}

// nfi_function: name('getOutputBufferSize') map('lzmast_stream*', 'POINTER')
size_t lzma_get_output_buffer_size(lzmast_stream *lzmast) {
    LOG_INFO("lzma_get_output_buffer_size(%p)\n", lzmast);
    size_t size = lzmast->output_size;
    if (size > GRAALPYTHON_MAX_SIZE) {
        LOG_SEVERE("buffer size is larger than max: %zd > %zd!!\n", size, (ssize_t) GRAALPYTHON_MAX_SIZE);
        return GRAALPYTHON_MAX_SIZE;
    }
    return size;
}

static void clear_output(lzmast_stream *lzmast) {
    lzma_release_buffer(lzmast->output);
    lzmast->output = lzma_allocate_buffer(1);
    lzmast->output->size = 0;
    lzmast->output_size = 0;
}

// nfi_function: name('getOutputBuffer') map('lzmast_stream*', 'POINTER')
void lzma_get_output_buffer(lzmast_stream *lzmast, Byte *dest) {
    LOG_INFO("lzma_get_off_heap_buffer(%p)\n", lzmast);
    off_heap_buffer *buffer = lzmast->output;
    size_t size = lzmast->output_size;
    if (!size) {
        return;
    }
    memcpy(dest, buffer->buf, size);
    clear_output(lzmast);
}

static int resize_output_buffer(lzmast_stream *lzmast, ssize_t length) {
    LOG_INFO("resize_output_buffer(%p, %zd)\n", lzmast, length);
    off_heap_buffer *current = lzmast->output;
    off_heap_buffer *resized = lzma_allocate_buffer(length);
    if (!resized) {
        return -1;
    }
    memcpy(resized->buf, current->buf, lzmast->output->size);
    lzma_release_buffer(current);
    lzmast->output = resized;
    return 0;
}

static int lzma_prepare_output_buffer(lzmast_stream *lzmast, ssize_t len) {
    LOG_INFO("lzma_prepare_output_buffer(%p, %zd)\n", lzmast, len);
    lzma_release_buffer(lzmast->output);
    lzmast->output = lzma_allocate_buffer(len);
    if (!lzmast->output) {
        return -1;
    }
    lzmast->output_size = 0;
    return 0;
}

static int isOK(int lzret) {
    switch(lzret) {
        case LZMA_OK:
        case LZMA_GET_CHECK:
        case LZMA_NO_CHECK:
        case LZMA_STREAM_END:
            return 1;
        default:
            return 0;
    }
}

static int
grow_buffer(lzmast_stream *lzmast, ssize_t max_length) {
    LOG_INFO("grow_buffer(%p, %zd)\n", lzmast, max_length);
    /* Expand the buffer by an amount proportional to the current size,
       giving us amortized linear-time behavior. Use a less-than-double
       growth factor to avoid excessive allocation. */
    size_t size = lzmast->output->size;
    size_t new_size = size + (size >> 3) + 6;

    if (max_length > 0 && new_size > (size_t) max_length)
        new_size = (size_t) max_length;

    if (new_size > size) {
        return resize_output_buffer(lzmast, new_size);
    } else {  /* overflow */
        return -1;
    }
}

// nfi_function: name('checkIsSupported')
int lzma_lzma_check_is_supported(int check_id) {
    return lzma_check_is_supported(check_id);
}

/************************************************
 *               Prepare Filters                *
 ************************************************/

// nfi_function: name('setFilterSpecLZMA') map('lzmast_stream*', 'POINTER')
int lzma_set_filter_spec_lzma(lzmast_stream *lzmast, int fidx, int64_t* opts) {
    initFilters(lzmast);
    lzma_options_lzma *options;
    lzmast->filters[fidx].id = (uint64_t) opts[ID_INDEX];
    
    options = (lzma_options_lzma *)calloc(1, sizeof *options);
    if (options == NULL) {
        return LZMA_MEM_ERROR;
    }
    if (lzma_lzma_preset(options, (uint32_t) opts[PRESET_INDEX])) {
        free(options);
        return LZMA_PRESET_ERROR;
    }
    if (opts[DICT_SIZE_INDEX] != -1) {
        options->dict_size = (uint32_t) opts[DICT_SIZE_INDEX];
    }

    if (opts[LC_INDEX] != -1) {
        options->lc = (uint32_t) opts[LC_INDEX]; 
    }

    if (opts[LP_INDEX] != -1) {
        options->lp = (uint32_t) opts[LP_INDEX]; 
    }

    if (opts[PB_INDEX] != -1) {
        options->pb = (uint32_t) opts[PB_INDEX]; 
    }

    if (opts[MODE_INDEX] != -1) {
        options->mode = (lzma_mode) opts[MODE_INDEX]; 
    }

    if (opts[NICE_LEN_INDEX] != -1) {
        options->nice_len = (uint32_t) opts[NICE_LEN_INDEX]; 
    }

    if (opts[MF_INDEX] != -1) {
        options->mf = (lzma_match_finder) opts[MF_INDEX]; 
    }

    if (opts[DEPTH_INDEX] != -1) {
        options->depth = (uint32_t) opts[DEPTH_INDEX]; 
    }

    lzmast->filters[fidx].options = options;
    return LZMA_OK;
}

// nfi_function: name('setFilterSpecDelta') map('lzmast_stream*', 'POINTER')
int lzma_set_filter_spec_delta(lzmast_stream *lzmast, int fidx, int64_t* opts) {
    initFilters(lzmast);
    lzma_options_delta *options;
    lzmast->filters[fidx].id = (uint64_t) opts[ID_INDEX];
    options = (lzma_options_delta *)calloc(1, sizeof *options);
    if (options == NULL) {
        return LZMA_MEM_ERROR;
    }
    options->type = LZMA_DELTA_TYPE_BYTE;
    if (opts[DIST_INDEX] != -1) {
        options->dist = (uint32_t) opts[DIST_INDEX];
    }

    lzmast->filters[fidx].options = options;
    return LZMA_OK;
}

// nfi_function: name('setFilterSpecBCJ') map('lzmast_stream*', 'POINTER')
int lzma_set_filter_spec_bcj(lzmast_stream *lzmast, int fidx, int64_t* opts) {
    initFilters(lzmast);
    lzma_options_bcj *options;
    lzmast->filters[fidx].id = (uint64_t) opts[ID_INDEX];
    options = (lzma_options_bcj *) calloc(1, sizeof *options);
    if (options == NULL) {
        return LZMA_MEM_ERROR;
    }
    if (opts[START_OFFSET_INDEX] != -1) {
        options->start_offset = (uint32_t) opts[START_OFFSET_INDEX];
    }
    lzmast->filters[fidx].options = options;
    return LZMA_OK;
}

// nfi_function: name('encodeFilter') map('lzmast_stream*', 'POINTER')
int lzma_encode_filter_spec(lzmast_stream *lzmast, int64_t* opts) {
    lzma_ret lzret;
    uint32_t encoded_size;
    lzma_filter filter;

    initFilters(lzmast);
    switch ((uint64_t) opts[0]) {
        case LZMA_FILTER_LZMA1:
        case LZMA_FILTER_LZMA2:
            lzret = lzma_set_filter_spec_lzma(lzmast, 0, opts);
            break;
        case LZMA_FILTER_DELTA:
            lzret = lzma_set_filter_spec_delta(lzmast, 0, opts);
            break;
        case LZMA_FILTER_X86:
        case LZMA_FILTER_POWERPC:
        case LZMA_FILTER_IA64:
        case LZMA_FILTER_ARM:
        case LZMA_FILTER_ARMTHUMB:
        case LZMA_FILTER_SPARC:
            lzret = lzma_set_filter_spec_bcj(lzmast, 0, opts);
            break;
    }
    if (!isOK(lzret)) {
        free_filter_chain(lzmast);
        return lzret;
    }
    filter = lzmast->filters[0];

    lzret = lzma_properties_size(&encoded_size, &filter);
    if (!isOK(lzret)) {
        free_filter_chain(lzmast);
        return lzret;
    }

    if (lzma_prepare_output_buffer(lzmast, encoded_size) < 0) {
        free_filter_chain(lzmast);
        return LZMA_MEM_ERROR;
    }

    lzret = lzma_properties_encode(&filter, lzmast->output->buf);
    free_filter_chain(lzmast);
    if (!isOK(lzret)) {
        return lzret;
    }

    lzmast->output_size = encoded_size;
    return LZMA_OK;
}

// nfi_function: name('decodeFilter') map('lzmast_stream*', 'POINTER')
int lzma_decode_filter_spec(int64_t filter_id, Byte* encoded_props, int len, int64_t *opts) {

    lzma_ret lzret;
    lzma_filter filter;

    filter.id = (lzma_vli) filter_id;
    
    lzret = lzma_properties_decode(&filter, NULL, encoded_props, len);
    if (!isOK(lzret)) {
        return lzret;
    }
    int ret = LZMA_OK;

    opts[ID_INDEX] = (int64_t) filter.id;

    switch (filter.id) {
        /* For LZMA1 filters, lzma_properties_{encode,decode}() only look at the
           lc, lp, pb, and dict_size fields. For LZMA2 filters, only the
           dict_size field is used. */
        case LZMA_FILTER_LZMA1: {
            lzma_options_lzma *options = filter.options;
            opts[LC_INDEX] = options->lc;
            opts[LP_INDEX] = options->lp;
            opts[PB_INDEX] = options->pb;
            opts[DICT_SIZE_INDEX] = options->dict_size;
            break;
        }
        case LZMA_FILTER_LZMA2: {
            lzma_options_lzma *options = filter.options;
            opts[DICT_SIZE_INDEX] = options->dict_size;
            break;
        }
        case LZMA_FILTER_DELTA: {
            lzma_options_delta *options = filter.options;
            opts[DIST_INDEX] = options->dist;
            break;
        }
        case LZMA_FILTER_X86:
        case LZMA_FILTER_POWERPC:
        case LZMA_FILTER_IA64:
        case LZMA_FILTER_ARM:
        case LZMA_FILTER_ARMTHUMB:
        case LZMA_FILTER_SPARC: {
            lzma_options_bcj *options = filter.options;
            opts[START_OFFSET_INDEX] = options->start_offset;
            break;
        }
        default:
            ret = LZMA_ID_ERROR;
    }

    /* We use vanilla free() here instead of PyMem_Free() - filter.options was
       allocated by lzma_properties_decode() using the default allocator. */
    free(filter.options);
    return ret;
}

/************************************************
 *               Compress Object                *
 ************************************************/

// nfi_function: name('lzmaEasyEncoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_easy_encoder(lzmast_stream *lzmast, uint32_t preset, int check) {
    LOG_INFO("lzma_lzma_easy_encoder(%p, %d, %d)[lzmast->lzs (%p)]\n", lzmast, preset, check, lzmast->lzs);
    lzma_ret lzret = lzma_easy_encoder(&lzmast->lzs, preset, check);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaStreamEncoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_stream_encoder(lzmast_stream *lzmast, int check) {
    LOG_INFO("lzma_lzma_stream_encoder(%p, %d)\n", lzmast, check);
    lzma_ret lzret = lzma_stream_encoder(&lzmast->lzs, lzmast->filters, check);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaAloneEncoderPreset') map('lzmast_stream*', 'POINTER')
int lzma_lzma_alone_encoder_preset(lzmast_stream *lzmast, uint32_t preset) {
    LOG_INFO("lzma_lzma_alone_encoder_preset(%p, %d)\n", lzmast, preset);
    lzma_options_lzma options;
    if (lzma_lzma_preset(&options, preset)) {
        return LZMA_PRESET_ERROR;
    }
    lzma_ret lzret = lzma_alone_encoder(&lzmast->lzs, &options);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaAloneEncoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_alone_encoder(lzmast_stream *lzmast) {
    lzma_ret lzret = lzma_alone_encoder(&lzmast->lzs, lzmast->filters[0].options);
    free_filter_chain(lzmast);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaRawEncoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_raw_encoder(lzmast_stream *lzmast) {
    lzma_ret lzret = lzma_raw_encoder(&lzmast->lzs, lzmast->filters);
    free_filter_chain(lzmast);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('compress') map('lzmast_stream*', 'POINTER')
int lzma_compress(lzmast_stream *lzmast, Byte *data, size_t len, int iaction, ssize_t bufsize) {
    lzma_action action = (lzma_action) iaction;
    LOG_INFO("lzma_compress(%p, %p, %zd, %d, %zd)\n", lzmast, data, len, action, bufsize);
    lzma_ret lzret;
    ssize_t data_size = 0;
    if (lzma_prepare_output_buffer(lzmast, bufsize) < 0) {
        return LZMA_MEM_ERROR;
    }

    if (len == 0) {
        lzmast->lzs.next_in = NULL;
    } else {
        lzmast->lzs.next_in = data;
    }
    lzmast->lzs.avail_in = len;
    lzmast->lzs.next_out = lzmast->output->buf;
    lzmast->lzs.avail_out = lzmast->output->size;
    for (;;) {

        lzret = lzma_code(&lzmast->lzs, action);
        data_size = lzmast->lzs.next_out - lzmast->output->buf;
        LOG_INFO("lzma_code(): %d  data_size = %zd\n", (int) lzret, data_size);
        if (lzret == LZMA_BUF_ERROR && len == 0 && lzmast->lzs.avail_out > 0) {
            lzret = LZMA_OK; /* That wasn't a real error */
        }
        if (!isOK(lzret)) {
            clear_output(lzmast);
            return lzret;
        }
        if ((action == LZMA_RUN && lzmast->lzs.avail_in == 0) ||
            (action == LZMA_FINISH && lzret == LZMA_STREAM_END)) {
            break;
        } else if (lzmast->lzs.avail_out == 0) {
            if (grow_buffer(lzmast, -1) < 0) {
                clear_output(lzmast);
                return LZMA_MEM_ERROR;
            }
            lzmast->lzs.next_out = lzmast->output->buf + data_size;
            lzmast->lzs.avail_out = lzmast->output->size - data_size;
        }
    }
    lzmast->output_size = data_size;
    return LZMA_OK;
}

/************************************************
 *              Decompress Object               *
 ************************************************/

// nfi_function: name('lzmaRawDecoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_raw_decoder(lzmast_stream *lzmast) {
    lzmast->check = LZMA_CHECK_NONE;
    lzma_ret lzret = lzma_raw_decoder(&lzmast->lzs, lzmast->filters);
    free_filter_chain(lzmast);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaAutoDecoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_auto_decoder(lzmast_stream *lzmast, uint64_t memlimit, uint32_t decoder_flags) {
    lzma_ret lzret = lzma_auto_decoder(&lzmast->lzs, memlimit, decoder_flags);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaStreamDecoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_stream_decoder(lzmast_stream *lzmast, uint64_t memlimit, uint32_t decoder_flags) {
    lzma_ret lzret = lzma_stream_decoder(&lzmast->lzs, memlimit, decoder_flags);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

// nfi_function: name('lzmaAloneDecoder') map('lzmast_stream*', 'POINTER')
int lzma_lzma_alone_decoder(lzmast_stream *lzmast, uint64_t memlimit) {
    LOG_INFO("lzma_decompress(%p, %ld)\n", lzmast, memlimit);
    lzmast->check = LZMA_CHECK_NONE;
    lzma_ret lzret = lzma_alone_decoder(&lzmast->lzs, memlimit);
    if (!isOK(lzret)) {
        return lzret;
    }
    lzmast->lzs_type = INITIALIZED;
    return LZMA_OK;
}

/* Decompress data of length d->lzs.avail_in in d->lzs.next_in.  The output
   buffer is allocated dynamically and returned.  At most max_length bytes are
   returned, so some of the input may not be consumed. d->lzs.next_in and
   d->lzs.avail_in are updated to reflect the consumed input. */

// nfi_function: name('decompress') map('lzmast_stream*', 'POINTER')
int lzma_decompress(lzmast_stream *lzmast, 
                Byte *input_buffer, ssize_t offset, 
                ssize_t max_length,
                ssize_t bufsize, size_t lzs_avail_in) {
    LOG_INFO("lzma_decompress(%p, %p, %zd, %zd, %zd, %zd)\n", lzmast, input_buffer, offset, bufsize, lzs_avail_in);
    lzma_ret lzret;
    lzma_stream *lzs = &lzmast->lzs;
    ssize_t data_size = 0;
    lzs->avail_in = lzs_avail_in;
    if (offset < 0) {
        lzs->next_in = NULL;
    } else {
        lzs->next_in = input_buffer + offset;
    }

    int check_alloc;
    if (max_length < 0 || max_length >= bufsize) {
        check_alloc = lzma_prepare_output_buffer(lzmast, bufsize);
    } else {
        check_alloc = lzma_prepare_output_buffer(lzmast, max_length);
    }
    if (check_alloc < 0) {
        return LZMA_MEM_ERROR;
    }

    lzs->next_out = lzmast->output->buf;
    lzs->avail_out = lzmast->output->size;

    for (;;) {
        lzret = lzma_code(lzs, LZMA_RUN);
        LOG_INFO("lzma_code(): %d\n", (int) lzret);
        data_size = lzs->next_out - lzmast->output->buf;
        lzmast->next_in_index = lzs->next_in - input_buffer;
        if (lzret == LZMA_BUF_ERROR && lzs->avail_in == 0 && lzs->avail_out > 0) {
            lzret = LZMA_OK; /* That wasn't a real error */
        }

        if (!isOK(lzret)) {
            clear_output(lzmast);
            return lzret;
        } 
        if (lzret == LZMA_GET_CHECK || lzret == LZMA_NO_CHECK) {
            lzmast->check = lzma_get_check(&lzmast->lzs);
            lzret = LZMA_OK;
        } 
        if (lzret == LZMA_STREAM_END) {
            // lzmast->eof = 1;
            // we'll return LZMA_STREAM_END to the java side and process EOF.
            break;
        } else if (lzs->avail_out == 0) {
            lzret = LZMA_OK;
            /* Need to check lzs->avail_out before lzs->avail_in.
               Maybe lzs's internal state still have a few bytes
               can be output, grow the output buffer and continue
               if max_lengh < 0. */
            if (data_size == max_length)
                break;
            if (grow_buffer(lzmast, max_length) < 0) {
                clear_output(lzmast);
                return LZMA_MEM_ERROR;
            }
            lzs->next_out = lzmast->output->buf + data_size;
            lzs->avail_out = lzmast->output->size - data_size;
        } else if (lzs->avail_in == 0) {
            lzret = LZMA_OK;
            break;
        }
    }
    LOG_INFO("lzs->avail_in: %zd,  lzs->avail_out: %zd\n", lzs->avail_in, lzs->avail_out);
    lzmast->output_size = data_size;
    return lzret;
}
