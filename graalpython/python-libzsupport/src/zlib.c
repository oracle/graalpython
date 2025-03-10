/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This implementation emulate CPython's zlib
 * see cpython/Modules/zlibmodule.c
 */

#define DEBUG 500
// #define BENCHMARK

#include <limits.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>

#include "zlib.h"

// Integer.MAX_INT
#define GRAALPYTHON_MAX_SIZE (INT_MAX)

#define DEF_BUF_SIZE (16*1024)
#define DEF_MAX_INITIAL_BUF_SIZE (16 * 1024 * 1024)

/* The following parameters are copied from zutil.h, version 0.95 */
#define DEFLATED   8
#if MAX_MEM_LEVEL >= 8
#  define DEF_MEM_LEVEL 8
#else
#  define DEF_MEM_LEVEL  MAX_MEM_LEVEL
#endif

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
    #define END_TIME(zst) zst->timeElapsed += get_time() - start_time;
#else
    #define START_TIME
    #define END_TIME(zst)
#endif

// The ref_count is important for `copy` as we 
// share off heap storage between native objects.
typedef struct
{
    size_t ref_count;
    size_t size;
    Byte *buf;
} off_heap_buffer;

typedef struct
{
    size_t next_in_index;
    off_heap_buffer *unused_data;
    off_heap_buffer *unconsumed_tail;
    int eof;
    int is_initialised;
    off_heap_buffer *zdict;
    size_t avail_in_real;
} compobject;

#define NOT_INITIALIZED 0
#define DEFLATE_TYPE 1
#define INFLATE_TYPE 2
typedef struct
{
    z_stream zst;
    int zst_type;

    off_heap_buffer *output;
    size_t output_size;

    off_heap_buffer *input_buffer;
    size_t input_buffer_size;

    compobject *comp;

    int error_function;
#ifdef BENCHMARK
    double timeElapsed;
#endif
} zlib_stream;

#define NO_ERROR            0   // nfi_var

#define DEFLATE_INIT_ERROR  101 // nfi_var
#define DEFLATE_END_ERROR   102 // nfi_var
#define DEFLATE_DICT_ERROR  103 // nfi_var
#define DEFLATE_OBJ_ERROR   104 // nfi_var
#define DEFLATE_FLUSH_ERROR 105 // nfi_var
#define DEFLATE_COPY_ERROR  106 // nfi_var
#define DEFLATE_ERROR       107 // nfi_var

#define INFLATE_INIT_ERROR  201 // nfi_var
#define INFLATE_END_ERROR   202 // nfi_var
#define INFLATE_DICT_ERROR  203 // nfi_var
#define INFLATE_OBJ_ERROR   204 // nfi_var
#define INFLATE_FLUSH_ERROR 205 // nfi_var
#define INFLATE_COPY_ERROR  206 // nfi_var
#define INFLATE_ERROR       207 // nfi_var

#define INCOMPLETE_ERROR    99  // nfi_var
#define MEMORY_ERROR        999 // nfi_var

// options to get buffer from the native side
#define OUTPUT_OPTION           0 // nfi_var
#define UNUSED_DATA_OPTION      1 // nfi_var
#define UNCONSUMED_TAIL_OPTION  2 // nfi_var
#define ZDICT_OPTION            3 // nfi_var

// nfi_function: name('zlibVersion') static(true)
const char *zlib_get_version() {
    return ZLIB_VERSION;
}

// nfi_function: name('zlibRuntimeVersion') static(true)
const char *zlib_get_runtime_version() {
    return zlibVersion();
}

// nfi_function: name('crc32')
uLong zlib_crc32(uLong crc, const Byte *buf, uInt len)
{
    return crc32(crc, buf, len);
}

// nfi_function: name('adler32')
uLong zlib_adler32(uLong crc, const Byte *buf, uInt len)
{
    return adler32(crc, buf, len);
}

static void* zlib_allocate(voidpf ctx, uInt items, uInt size)
{
    void *m = malloc((size_t)items * (size_t)size);
    LOG_FINER("malloc(address: %p, items: %u, size: %u)\n", m, items, size);
    return m; 
}

static void zlib_deallocate(voidpf ctx, void *ptr)
{
    LOG_FINER("free(%p)\n", ptr);
    free(ptr);
}

static off_heap_buffer* zlib_allocate_buffer(size_t items)
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

static off_heap_buffer* zlib_create_copy_buffer(Byte *src, size_t len) {
    off_heap_buffer *dest = zlib_allocate_buffer(len);
    if (dest && len > 0) {
        memcpy(dest->buf, src, len);
    }
    return dest; 
}

static void zlib_release_buffer(off_heap_buffer *o) {
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

static off_heap_buffer* zlib_get_ref(off_heap_buffer* o) {
    if (o) {
        LOG_FINEST("off_heap_buffer(ref_count: %zu + 1)\n", o->ref_count);
        o->ref_count++;
    }
    return o;
}

// nfi_function: name('createStream') map('zlib_stream*', 'POINTER')
zlib_stream *zlib_create_zlib_stream() {
    zlib_stream *zst = (zlib_stream *) malloc(sizeof(zlib_stream));
    zst->zst_type = NOT_INITIALIZED;
    zst->error_function = NO_ERROR;
    zst->output = zlib_allocate_buffer(1);
    zst->output->size = 0;
    zst->output_size = 0;
    zst->input_buffer = zlib_allocate_buffer(1);
    zst->input_buffer->size = 0;
    zst->input_buffer_size = 0;
    zst->zst.opaque = Z_NULL;
    zst->zst.zalloc = zlib_allocate;
    zst->zst.zfree = zlib_deallocate;
    zst->zst.next_in = NULL;
    zst->zst.next_out = NULL;
    zst->comp = NULL;
#ifdef BENCHMARK
    zst->timeElapsed = 0;
#endif
    LOG_INFO("zlib_stream(%p)\n", zst);
    return zst;
}

void zlib_release_compobject(compobject *comp) {
    LOG_INFO("zlib_release_compobject(%p)\n", comp);
    if (!comp) {
        return;
    }
    if (comp->unconsumed_tail) {
        zlib_release_buffer(comp->unconsumed_tail);
    }
    if (comp->unused_data) {
        zlib_release_buffer(comp->unused_data);
    }
    if (comp->zdict) {
        zlib_release_buffer(comp->zdict);
    }

    free(comp);
}

// nfi_function: name('getTimeElapsed') map('zlib_stream*', 'POINTER')  static(true)
double zlib_get_timeElapsed(zlib_stream* zst) {
#ifdef BENCHMARK
    double t = zst->timeElapsed;
    LOG_FINEST("time Elapsed: %.2f\n", t);
    zst->timeElapsed = 0;
    return t;
#else
    return -1.0;
#endif
}

// nfi_function: name('deallocateStream') map('zlib_stream*', 'POINTER')
void zlib_free_stream(zlib_stream* zst) {
    if (!zst) {
        return;
    }
    if(zst->zst_type) {
        if (zst->zst_type == DEFLATE_TYPE) {
            deflateEnd(&zst->zst);
        } else {
            inflateEnd(&zst->zst);
        }
    }
    zlib_release_buffer(zst->output);
    if (zst->input_buffer) {
        zlib_release_buffer(zst->input_buffer);
    }
    zlib_release_compobject(zst->comp);
    LOG_INFO("free zlib_stream(%p)\n", zst);
    free(zst);
}

// nfi_function: name('gcReleaseHelper') map('zlib_stream*', 'POINTER') release(true)
void zlib_gc_helper(zlib_stream* zst) {
    zlib_free_stream(zst);
}

// nfi_function: name('getErrorFunction') map('zlib_stream*', 'POINTER')
int zlib_get_error_type(zlib_stream *zst) {
    return zst->error_function;
}

// nfi_function: name('getStreamErrorMsg') map('zlib_stream*', 'POINTER')
const char *zlib_get_stream_msg(zlib_stream *zst) {
    return zst->zst.msg;
}

// nfi_function: name('hasStreamErrorMsg') map('zlib_stream*', 'POINTER')
int zlib_has_stream_msg(zlib_stream *zst) {
    if (zst->zst.msg == Z_NULL) {
        return 0;
    }
    return 1;
}

// nfi_function: name('getEOF') map('zlib_stream*', 'POINTER')
int zlib_get_eof(zlib_stream *zst) {
    return zst->comp->eof;
}

// nfi_function: name('getIsInitialised') map('zlib_stream*', 'POINTER')
int zlib_get_is_initialised(zlib_stream *zst) {
    return zst->comp->is_initialised;
}

// nfi_function: name('getBufferSize') map('zlib_stream*', 'POINTER')
uInt zlib_get_buffer_size(zlib_stream *zst, int option) {
    LOG_INFO("zlib_get_buffer_size(%p)\n", zst);
    size_t size = 0;
    switch(option) {
        case OUTPUT_OPTION:
            size = zst->output_size;
            break;
        case UNUSED_DATA_OPTION:
            assert(zst->comp != NULL);
            size = zst->comp->unused_data->size;
            break;
        case UNCONSUMED_TAIL_OPTION:
            assert(zst->comp != NULL);
            size = zst->comp->unconsumed_tail->size;
            break;
        case ZDICT_OPTION:
            assert(zst->comp != NULL);
            size = zst->comp->zdict->size;
            break;
    }
    if (size > GRAALPYTHON_MAX_SIZE) {
        LOG_SEVERE("buffer size is larger than max: %zd > %zd!!\n", size, (ssize_t) GRAALPYTHON_MAX_SIZE);
        return GRAALPYTHON_MAX_SIZE;
    }
    return size;
}


// nfi_function: name('getBuffer') map('zlib_stream*', 'POINTER')
void zlib_get_off_heap_buffer(zlib_stream *zst, int option, Byte *dest) {
    LOG_INFO("zlib_get_off_heap_buffer(%p)\n", zst);
    off_heap_buffer *buffer = NULL;
    size_t size = 0;
    switch(option) {
        case OUTPUT_OPTION:
            buffer = zst->output;
            size = zst->output_size;
            break;
        case UNUSED_DATA_OPTION:
            assert(zst->comp != NULL);
            buffer = zst->comp->unused_data;
            size = zst->comp->unused_data->size;
            break;
        case UNCONSUMED_TAIL_OPTION:
            assert(zst->comp != NULL);
            buffer = zst->comp->unconsumed_tail;
            size = zst->comp->unconsumed_tail->size;
            break;
        case ZDICT_OPTION:
            assert(zst->comp != NULL);
            buffer = zst->comp->zdict;
            size = zst->comp->zdict->size;
            break;
        default:
            return;
    }
    if (!size) {
        return;
    }
    assert(buffer->buf != NULL);
    memcpy(dest, buffer->buf, size);
}

// nfi_function: name('createCompObject') map('zlib_stream*', 'POINTER')
zlib_stream *zlib_create_compobject() {
    zlib_stream *zst = zlib_create_zlib_stream();
    compobject *comp = (compobject *) malloc(sizeof(compobject));
    LOG_INFO("zlib_create_compobject(%p)\n", comp);
    comp->next_in_index = 0;
    comp->eof = 0;
    comp->is_initialised = 0;
    comp->unconsumed_tail = zlib_allocate_buffer(1);
    comp->unconsumed_tail->size = 0;
    comp->unused_data = zlib_allocate_buffer(1);
    comp->unused_data->size = 0;
    comp->zdict = NULL;
    comp->avail_in_real = 0;
    assert(zst->comp == NULL);
    zst->comp = comp;
    return zst;
}

void zlib_set_zdict(zlib_stream *zst, Byte *zdict, size_t zdict_len) {
    if (!zst->comp) {
        LOG_SEVERE("compobject is NULL!!\n");
        return;
    }

    zlib_release_buffer(zst->comp->zdict);
    zst->comp->zdict = zlib_create_copy_buffer(zdict, zdict_len);
    LOG_INFO("zlib_set_zdict(zdict: %p, len: %zu)\n", zst->comp->zdict, zst->comp->zdict->size);
}

static void clear_output(zlib_stream *zst) {
    zlib_release_buffer(zst->output);
    zst->output = zlib_allocate_buffer(1);
    zst->output->size = 0;
    zst->output_size = 0;
}

static void error_occurred(zlib_stream *zst, int errFunction) {
    zst->error_function = errFunction;
    clear_output(zst);
}

static int resize_off_heap_buffer(zlib_stream *zst, size_t length, int is_output) {
    LOG_INFO("resize_off_heap_buffer(%p)\n", zst);
    off_heap_buffer *current;
    size_t len;
    if (is_output == 1) {
        current = zst->output;
        len = zst->output->size;
    } else {
        current = zst->input_buffer;
        len = zst->input_buffer->size;
    }
    off_heap_buffer *resized = zlib_allocate_buffer(length);
    if (!resized) {
        return -1;
    }
    memcpy(resized->buf, current->buf, len);
    zlib_release_buffer(current);
    if (is_output == 1) {
        zst->output = resized;
    } else {
        zst->input_buffer = resized;
    }
    return 0;
}

/************************************************
 *                   Compress                   *
 ************************************************/

static ssize_t arrange_output_buffer_with_maximum(zlib_stream *zst, ssize_t length, ssize_t max_length) {
    LOG_INFO("arrange_output_buffer_with_maximum(%p, length: %zd, max_length: %zd)\n", zst, length, max_length);
    ssize_t occupied;

    if (!zst->output->size) {
        off_heap_buffer *buf = zlib_allocate_buffer(length);
        if (!buf) {
            return -1;
        }
        zlib_release_buffer(zst->output);
        zst->output = buf;
        occupied = 0;
    } else {
        occupied = zst->zst.next_out - zst->output->buf;

        if (length == occupied) {
            ssize_t new_length;
            assert(length <= max_length);
            /* can not scale the buffer over max_length */
            if (length == max_length) {
                return -2;
            }
            if (length <= (max_length >> 1)) {
                new_length = length << 1;
            } else {
                new_length = max_length;
            }
            if (resize_off_heap_buffer(zst, new_length, 1) < 0) {
                return -1;
            }
            length = new_length;
        }
    }

    zst->zst.avail_out = length - occupied;
    zst->zst.next_out = zst->output->buf + occupied;

    return length;
}

static ssize_t arrange_output_buffer(zlib_stream *zst, ssize_t length)
{
    return arrange_output_buffer_with_maximum(zst, length, GRAALPYTHON_MAX_SIZE);
}

// nfi_function: name('deflateOffHeap') map('zlib_stream*', 'POINTER')
int zlib_deflate_off_heap(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size, int level, int wbits) {
    LOG_INFO("zlib_deflate_off_heap(%p)\n", zst);
    int err, flush;
    ssize_t ibuflen = in_len, obuflen = buf_size;

    zst->zst.next_in = in;
    
    err = deflateInit2(&zst->zst, level, DEFLATED, wbits, DEF_MEM_LEVEL, Z_DEFAULT_STRATEGY);
    if (err != Z_OK) {
        zst->error_function = DEFLATE_INIT_ERROR;
        return err;
    }
    zst->zst_type = DEFLATE_TYPE;

    do {
        if (ibuflen > GRAALPYTHON_MAX_SIZE) {
            zst->zst.avail_in = GRAALPYTHON_MAX_SIZE;
            ibuflen -= GRAALPYTHON_MAX_SIZE;
        } else {
            zst->zst.avail_in = in_len;
            ibuflen = 0;
        }
        flush = ibuflen == 0 ? Z_FINISH : Z_NO_FLUSH;

        do {
            obuflen = arrange_output_buffer(zst, obuflen);
            if (obuflen < 0) {
                deflateEnd(&zst->zst);
                zst->zst_type = NOT_INITIALIZED;
                error_occurred(zst, MEMORY_ERROR);
                return -2;
            }

            err = deflate(&zst->zst, flush);

            if (err == Z_STREAM_ERROR) {
                deflateEnd(&zst->zst);
                zst->zst_type = NOT_INITIALIZED;
                error_occurred(zst, DEFLATE_ERROR);
                return err;
            }

        } while (zst->zst.avail_out == 0);
        assert(zst->zst.avail_in == 0);

    } while (flush != Z_FINISH);
    assert(err == Z_STREAM_END);

    err = deflateEnd(&zst->zst);
    zst->zst_type = NOT_INITIALIZED;
    if (err != Z_OK) {
        error_occurred(zst, DEFLATE_END_ERROR);
        return err;
    }
    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
    }
    return Z_OK;
}

/************************************************
 *                  Decompress                  *
 ************************************************/

// nfi_function: name('inflateOffHeap') map('zlib_stream*', 'POINTER')
int zlib_inflate_off_heap(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size, int wbits) {
    LOG_INFO("zlib_inflate_off_heap(%p)\n", zst);
    int err, flush;
    ssize_t ibuflen = in_len, obuflen = buf_size;

    zst->zst.next_in = in;
    
    err = inflateInit2(&zst->zst, wbits);
    if (err != Z_OK) {
        zst->error_function = INFLATE_INIT_ERROR;
        return err;
    }
    zst->zst_type = INFLATE_TYPE;

    do {
        if (ibuflen > GRAALPYTHON_MAX_SIZE) {
            zst->zst.avail_in = GRAALPYTHON_MAX_SIZE;
            ibuflen -= GRAALPYTHON_MAX_SIZE;
        } else {
            zst->zst.avail_in = ibuflen;
            ibuflen = 0;
        }
        flush = ibuflen == 0 ? Z_FINISH : Z_NO_FLUSH;

        do {
            obuflen = arrange_output_buffer(zst, obuflen);
            if (obuflen < 0) {
                inflateEnd(&zst->zst);
                zst->zst_type = NOT_INITIALIZED;
                error_occurred(zst, MEMORY_ERROR);
                return -2;
            }

            err = inflate(&zst->zst, flush);

            switch (err) {
                case Z_OK:           
                case Z_BUF_ERROR:    
                case Z_STREAM_END:
                    break;
                default:
                    inflateEnd(&zst->zst);
                    zst->zst_type = NOT_INITIALIZED;
                    error_occurred(zst, INFLATE_ERROR);
                    return err;
            }
        } while (zst->zst.avail_out == 0);

    } while (err != Z_STREAM_END && ibuflen != 0);


    if (err != Z_STREAM_END) {
        inflateEnd(&zst->zst);
        zst->zst_type = NOT_INITIALIZED;
        error_occurred(zst, INFLATE_ERROR);
        return err;
    }

    err = inflateEnd(&zst->zst);
    zst->zst_type = NOT_INITIALIZED;
    if (err != Z_OK) {
        error_occurred(zst, INFLATE_END_ERROR);
        return err;
    }

    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
    }
    return Z_OK;
}

/************************************************
 *               Compress Object                *
 ************************************************/

// nfi_function: name('compressObjInitWithDict') map('zlib_stream*', 'POINTER')
int zlib_Compress_init(zlib_stream *zst, 
                        int level, int method, 
                        int wbits, int memLevel, 
                        int strategy, Byte *dict, size_t dict_len) {
    LOG_INFO("zlib_Compress_init(%p)\n", zst);
    int err = deflateInit2(&zst->zst, level, method, wbits, memLevel, strategy);
    if (err != Z_OK) {
        zst->error_function = DEFLATE_OBJ_ERROR;
        return err;
    }
    zst->zst_type = DEFLATE_TYPE;
    zst->comp->is_initialised = 1;
    if (dict != NULL) {
        zlib_set_zdict(zst, dict, dict_len);
        off_heap_buffer* zdict = zst->comp->zdict;
        err = deflateSetDictionary(&zst->zst, zdict->buf, zdict->size);
        if (err != Z_OK) {
            zst->error_function = DEFLATE_DICT_ERROR;
            return err;
        }
    }
    return Z_OK;
}

// nfi_function: name('compressObjInit') map('zlib_stream*', 'POINTER')
int zlib_Compress_init_no_dict(zlib_stream *zst, 
                        int level, int method, 
                        int wbits, int memLevel, 
                        int strategy) {
    return zlib_Compress_init(zst, level, method, wbits, memLevel, strategy, NULL, 0);
}

// nfi_function: name('compressObj') map('zlib_stream*', 'POINTER')
int zlib_Compress_obj(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size) {
    LOG_INFO("zlib_Compress_obj(%p, in_len: %zd, buf_size: %zd)\n", zst, in_len, buf_size);
    clear_output(zst);
    int err;
    ssize_t ibuflen = in_len, obuflen = buf_size;

    zst->zst.next_in = in;

    do {
        if (ibuflen > GRAALPYTHON_MAX_SIZE) {
            zst->zst.avail_in = GRAALPYTHON_MAX_SIZE;
            ibuflen -= GRAALPYTHON_MAX_SIZE;
        } else {
            zst->zst.avail_in = in_len;
            ibuflen = 0;
        }
        do {
            obuflen = arrange_output_buffer(zst, obuflen);
            if (obuflen < 0) {
                error_occurred(zst, MEMORY_ERROR);
                return -2;
            }

            err = deflate(&zst->zst, Z_NO_FLUSH);

            if (err == Z_STREAM_ERROR) {
                error_occurred(zst, DEFLATE_ERROR);
                return err;
            }

        } while (zst->zst.avail_out == 0);
        assert(zst->zst.avail_in == 0);

    } while (ibuflen != 0);

    zst->comp->next_in_index = zst->zst.next_in - in;
    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
    }
    return Z_OK;
}

// nfi_function: name('compressObjFlush') map('zlib_stream*', 'POINTER')
int zlib_Compress_flush(zlib_stream *zst, Byte *in, ssize_t buf_size, int mode) {
    LOG_INFO("zlib_Compress_flush(%p, buf_size: %zd)\n", zst, buf_size);
    clear_output(zst);
    int err;
    ssize_t length = buf_size;

    /* Flushing with Z_NO_FLUSH is a no-op, so there's no point in
       doing any work at all; just return an empty string. */
    // (mq) this should be handled in the java side
    assert (mode != Z_NO_FLUSH); 

    if(zst->zst.next_in) {
        zst->zst.next_in = in + zst->comp->next_in_index;
    }
    zst->zst.avail_in = 0;

    do {
        length = arrange_output_buffer(zst, length);
        if (length < 0) {
            error_occurred(zst, MEMORY_ERROR);
            return -2;
        }

        err = deflate(&zst->zst, mode);

        if (err == Z_STREAM_ERROR) {
            error_occurred(zst, DEFLATE_ERROR);
            return err;
        }
    } while (zst->zst.avail_out == 0);
    assert(zst->zst.avail_in == 0);

    /* If mode is Z_FINISH, we also have to call deflateEnd() to free
       various data structures. Note we should only get Z_STREAM_END when
       mode is Z_FINISH, but checking both for safety*/
    if (err == Z_STREAM_END && mode == Z_FINISH) {
        err = deflateEnd(&zst->zst);
        zst->zst_type = NOT_INITIALIZED;
        if (err != Z_OK) {
            error_occurred(zst, DEFLATE_END_ERROR);
            return err;
        } else {
            // notify the java-end to release memory.
            zst->comp->is_initialised = 0;
        }
        /* We will only get Z_BUF_ERROR if the output buffer was full
           but there wasn't more output when we tried again, so it is
           not an error condition.
        */
    } else if (err != Z_OK && err != Z_BUF_ERROR) {
        error_occurred(zst, DEFLATE_FLUSH_ERROR);
        return err;
    }

    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
    }
    return Z_OK;
}


// nfi_function: name('compressObjCopy') map('zlib_stream*', 'POINTER')
int zlib_Compress_copy(zlib_stream *zst, zlib_stream *new_copy) {
    LOG_INFO("zlib_Compress_copy(%p)\n", zst);
    int err = deflateCopy(&new_copy->zst, &zst->zst);
    if (err != Z_OK) {
        zst->error_function = DEFLATE_COPY_ERROR;
        return err;
    }
    new_copy->comp->next_in_index = zst->comp->next_in_index;
    new_copy->comp->unused_data = zlib_get_ref(zst->comp->unused_data);
    new_copy->comp->unconsumed_tail = zlib_get_ref(zst->comp->unconsumed_tail);
    new_copy->comp->zdict = zlib_get_ref(zst->comp->zdict);
    new_copy->comp->eof = zst->comp->eof;

    /* Mark it as being initialized */
    new_copy->comp->is_initialised = 1;
    return Z_OK;
}


/************************************************
 *              Decompress Object               *
 ************************************************/

// nfi_function: name('decompressObjInitWithDict') map('zlib_stream*', 'POINTER')
int zlib_Decompress_init(zlib_stream *zst, int wbits, Byte *dict, size_t dict_len) {
    LOG_INFO("zlib_Decompress_init(%p)\n", zst);
    
    int err = inflateInit2(&zst->zst, wbits);
    if (err != Z_OK) {
        zst->error_function = INFLATE_OBJ_ERROR;
        return err;
    }
    zst->zst_type = INFLATE_TYPE;
    zst->comp->is_initialised = 1;
    if (dict != NULL) {
        zlib_set_zdict(zst, dict, dict_len);
    }
    off_heap_buffer* zdict = zst->comp->zdict;
    if (dict != NULL && wbits < 0) {
        err = inflateSetDictionary(&zst->zst, zdict->buf, zdict->size);
        if (err != Z_OK) {
            zst->error_function = INFLATE_DICT_ERROR;
            return err;
        }
    }
    return Z_OK;
}

// nfi_function: name('decompressObjInit') map('zlib_stream*', 'POINTER')
int zlib_Decompress_init_no_dict(zlib_stream *zst, int wbits) {
    return zlib_Decompress_init(zst, wbits, NULL, 0);
}

static int save_unconsumed_input(zlib_stream *zst, Byte *in, size_t in_len, int err) {
    if (err == Z_STREAM_END) {
        /* The end of the compressed data has been reached. Store the leftover
           input data in self->unused_data. */
        if (zst->zst.avail_in > 0) {
            LOG_INFO("save_unconsumed_input(%p) unused_data\n", zst);
            off_heap_buffer *new_data;
            size_t old_size, new_size, left_size;
            old_size = zst->comp->unused_data->size;
            left_size = in + in_len - zst->zst.next_in;
            if (left_size > (GRAALPYTHON_MAX_SIZE - old_size)) {
                error_occurred(zst, MEMORY_ERROR);
                return -2;
            }
            new_size = old_size + left_size;
            new_data = zlib_allocate_buffer(new_size);
            if (new_data == NULL) {
                error_occurred(zst, MEMORY_ERROR);
                return -1;
            }
            memcpy(new_data->buf, zst->comp->unused_data->buf, old_size);
            memcpy(new_data->buf + old_size, zst->zst.next_in, left_size);
            zlib_release_buffer(zst->comp->unused_data);
            zst->comp->unused_data = new_data;
            zst->zst.avail_in = 0;
        }
    }

    if (zst->zst.avail_in > 0 || zst->comp->unconsumed_tail->size) {
        /* This code handles two distinct cases:
           1. Output limit was reached. Save leftover input in unconsumed_tail.
           2. All input data was consumed. Clear unconsumed_tail. */
        LOG_INFO("save_unconsumed_input(%p) unconsumed_tail\n", zst);
        size_t left_size = in + in_len - zst->zst.next_in;
        off_heap_buffer *new_data = zlib_create_copy_buffer(zst->zst.next_in, left_size);
        if (new_data == NULL) {
            zst->error_function = MEMORY_ERROR;
            return -1;
        }
        zlib_release_buffer(zst->comp->unconsumed_tail);
        zst->comp->unconsumed_tail = new_data;
    }

    return Z_OK;
}

// nfi_function: name('decompressObj') map('zlib_stream*', 'POINTER')
int zlib_Decompress_obj(zlib_stream *zst, Byte *in, ssize_t in_len, ssize_t buf_size, ssize_t max_length) {
    LOG_INFO("zlib_Decompress_obj(%p, in_len: %zd, buf_size: %zd, max_length: %zd)\n", zst, in_len, buf_size, max_length);
    clear_output(zst);
    
    int err = Z_OK, serr;
    ssize_t ibuflen = in_len, obuflen = buf_size, hard_limit;
    zst->zst.next_in = in;
    off_heap_buffer* zdict = zst->comp->zdict;

    if (max_length == 0)
        hard_limit = GRAALPYTHON_MAX_SIZE;
    else
        hard_limit = max_length;

    /* limit amount of data allocated to max_length */
    if (max_length && obuflen > max_length) {
        obuflen = max_length;
    }

    do {
        if (ibuflen > GRAALPYTHON_MAX_SIZE) {
            zst->zst.avail_in = GRAALPYTHON_MAX_SIZE;
            ibuflen -= GRAALPYTHON_MAX_SIZE;
        } else {
            zst->zst.avail_in = ibuflen;
            ibuflen = 0;
        }

        do {
            obuflen = arrange_output_buffer_with_maximum(zst, obuflen, hard_limit);
            if (obuflen == -2) {
                if (max_length > 0) {
                    LOG_INFO("max_length > 0 -> goto save\n");
                    goto save;
                }
            }
            if (obuflen < 0) {
                error_occurred(zst, MEMORY_ERROR);
                return -2;
            }
            START_TIME
            err = inflate(&zst->zst, Z_SYNC_FLUSH);
            END_TIME(zst)
            LOG_INFO("inflate(&zst->zst, Z_SYNC_FLUSH) -> %d\n", err);

            switch (err) {
                case Z_OK:           
                case Z_BUF_ERROR:    
                case Z_STREAM_END:
                    break;
                default:
                    if (err == Z_NEED_DICT && zdict != NULL) {
                        LOG_INFO("setting zdict len: %d\n", zdict->size);
                        int err2 = inflateSetDictionary(&zst->zst, zdict->buf, zdict->size);
                        if (err2 < 0) {
                            error_occurred(zst, INFLATE_DICT_ERROR);
                            return err2;
                        }
                        break;
                    }
                    LOG_INFO("zdict != NULL -> goto save\n");
                    goto save;
            }

        LOG_INFO("zst->zst.avail_out(%u) == 0 || err(%d) == Z_NEED_DICT(2)\n", zst->zst.avail_out, err);
        } while (zst->zst.avail_out == 0 || err == Z_NEED_DICT);

    LOG_INFO("%d != Z_STREAM_END(1) && ibuflen != %zd\n", err, ibuflen);
    } while (err != Z_STREAM_END && ibuflen != 0);

 save:
    serr = save_unconsumed_input(zst, in, in_len, err);
    if (serr < 0) {
        return serr;
    }

    if (err == Z_STREAM_END) {
        /* This is the logical place to call inflateEnd, but the old behaviour
           of only calling it on flush() is preserved. */
        zst->comp->eof = 1;
    } else if (err != Z_OK && err != Z_BUF_ERROR) {
        /* We will only get Z_BUF_ERROR if the output buffer was full
           but there wasn't more output when we tried again, so it is
           not an error condition.
        */
        error_occurred(zst, INFLATE_ERROR);
        return err;
    }

    zst->comp->next_in_index = zst->zst.next_in - in;
    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
        LOG_INFO("Output size: %d, zst->zst.next_out: %p, zst->output(buf: %p, %zu)\n", zst->output_size, zst->zst.next_out, zst->output->buf, zst->output->size);
    }
    return Z_OK;
}

// nfi_function: name('decompressObjFlush') map('zlib_stream*', 'POINTER')
int zlib_Decompress_flush(zlib_stream *zst, ssize_t length) {
    LOG_INFO("zlib_Decompress_flush(%p, length: %zd)\n", zst, length);
    clear_output(zst);
    int err, serr, flush;
    off_heap_buffer* zdict = zst->comp->zdict;
    off_heap_buffer* data;
    ssize_t ibuflen = zst->comp->unconsumed_tail->size;
    data = zlib_create_copy_buffer(zst->comp->unconsumed_tail->buf, ibuflen);
    zst->zst.next_in = data->buf;

    do {
        if (ibuflen > GRAALPYTHON_MAX_SIZE) {
            zst->zst.avail_in = GRAALPYTHON_MAX_SIZE;
            ibuflen -= GRAALPYTHON_MAX_SIZE;
        } else {
            zst->zst.avail_in = ibuflen;
            ibuflen = 0;
        }
        flush = ibuflen == 0 ? Z_FINISH : Z_NO_FLUSH;

        do {
            length = arrange_output_buffer(zst, length);
            if (length < 0) {
                error_occurred(zst, MEMORY_ERROR);
                zlib_release_buffer(data);
                return -2;
            }

            LOG_INFO("inflate(%p, %d)\n", zst, flush);
            err = inflate(&zst->zst, flush);

            switch (err) {
                case Z_OK:
                case Z_BUF_ERROR:    
                case Z_STREAM_END:
                    break;
                default:
                    if (err == Z_NEED_DICT && zdict != NULL) {
                        LOG_INFO("setting zdict len: %d\n", zdict->size);
                        int err2 = inflateSetDictionary(&zst->zst, zdict->buf, zdict->size);
                        if (err2 < 0) {
                            error_occurred(zst, INFLATE_DICT_ERROR);
                            zlib_release_buffer(data);
                            return err2;
                        } else {
                            break;
                        }
                    }
                    goto save;
            }

        } while (zst->zst.avail_out == 0 || err == Z_NEED_DICT);

    } while (err != Z_STREAM_END && ibuflen != 0);

 save:
    serr = save_unconsumed_input(zst, data->buf, data->size, err);
    zlib_release_buffer(data);
    if (serr < 0) {
        return serr;
    }

    /* If at end of stream, clean up any memory allocated by zlib. */
    if (err == Z_STREAM_END) {
        zst->comp->eof = 1;
        // notify the java-end to release memory.
        zst->comp->is_initialised = 0;
        err = inflateEnd(&zst->zst);
        zst->zst_type = NOT_INITIALIZED;
        if (err != Z_OK) {
            error_occurred(zst, INFLATE_END_ERROR);
            return err;
        }
    }
    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
    }
    return Z_OK;
}

// nfi_function: name('decompressObjCopy') map('zlib_stream*', 'POINTER')
int zlib_Decompress_copy(zlib_stream *zst, zlib_stream *new_copy) {
    LOG_INFO("zlib_Decompress_copy(%p)\n", zst);
    int err = inflateCopy(&new_copy->zst, &zst->zst);
    if (err != Z_OK) {
        zst->error_function = INFLATE_COPY_ERROR;
        return err;
    }
    new_copy->comp->unused_data = zlib_get_ref(zst->comp->unused_data);
    new_copy->comp->unconsumed_tail = zlib_get_ref(zst->comp->unconsumed_tail);
    new_copy->comp->zdict = zlib_get_ref(zst->comp->zdict);
    new_copy->comp->eof = zst->comp->eof;

    /* Mark it as being initialized */
    new_copy->comp->is_initialised = 1;

    return Z_OK;
}

static void
arrange_input_buffer(z_stream *zst, size_t *remains)
{
    zst->avail_in = (uInt)(((size_t)*remains) < UINT_MAX ? UINT_MAX : ((size_t)*remains));
    *remains -= zst->avail_in;
}

/* Decompress data of length zst->comp->avail_in_real in self->state.next_in. The
   output buffer is allocated dynamically and returned. If the max_length is
   of sufficiently low size, max_length is allocated immediately. At most
   max_length bytes are returned, so some of the input may not be consumed.
   self->state.next_in and zst->comp->avail_in_real are updated to reflect the
   consumed input. */

static int
decompress_buf(zlib_stream *zst, ssize_t max_length)
{
    LOG_INFO("decompress_buf(%p, max_length: %zd)\n", zst, max_length);
    clear_output(zst);
    /* data_size is strictly positive, but because we repeatedly have to
       compare against max_length and PyBytes_GET_SIZE we declare it as
       signed */
    ssize_t hard_limit;
    ssize_t obuflen;

    int err = Z_OK;

    /* When sys.maxsize is passed as default use DEF_BUF_SIZE as start buffer.
       In this particular case the data may not necessarily be very big, so
       it is better to grow dynamically.*/
    if ((max_length < 0) || max_length >= GRAALPYTHON_MAX_SIZE) {
        hard_limit = GRAALPYTHON_MAX_SIZE;
        obuflen = DEF_BUF_SIZE;
    } else {
        /* Assume that decompressor is used in file decompression with a fixed
           block size of max_length. In that case we will reach max_length almost
           always (except at the end of the file). So it makes sense to allocate
           max_length. */
        hard_limit = max_length;
        obuflen = max_length;
        if (obuflen > DEF_MAX_INITIAL_BUF_SIZE){
            // Safeguard against memory overflow.
            obuflen = DEF_MAX_INITIAL_BUF_SIZE;
        }
    }

    do {
        arrange_input_buffer(&(zst->zst), &(zst->comp->avail_in_real));

        do {
            obuflen = arrange_output_buffer_with_maximum(zst,
                                                        obuflen,
                                                        hard_limit);
            if (obuflen == -1){
                error_occurred(zst, MEMORY_ERROR);
                // PyErr_SetString(PyExc_MemoryError,
                //                 "Insufficient memory for buffer allocation");
                return -1;
            }
            else if (obuflen == -2) {
                break;
            }
            // Py_BEGIN_ALLOW_THREADS
            err = inflate(&zst->zst, Z_SYNC_FLUSH);
            // Py_END_ALLOW_THREADS
            switch (err) {
            case Z_OK:            /* fall through */
            case Z_BUF_ERROR:     /* fall through */
            case Z_STREAM_END:
                break;
            default:
                if (err == Z_NEED_DICT) {
                    error_occurred(zst, INFLATE_DICT_ERROR);
                    return -1;
                }
                else {
                    break;
                }
            }
        } while (zst->zst.avail_out == 0);
    } while(err != Z_STREAM_END && zst->comp->avail_in_real != 0);

    if (err == Z_STREAM_END) {
        zst->comp->eof = 1;
        zst->comp->is_initialised = 0;
        /* Unlike the Decompress object we call inflateEnd here as there are no
           backwards compatibility issues */
        err = inflateEnd(&zst->zst);
        if (err != Z_OK) {
            error_occurred(zst, INFLATE_END_ERROR);
            return err;
        }
    } else if (err != Z_OK && err != Z_BUF_ERROR) {
        error_occurred(zst, INFLATE_ERROR);
        return err;
    }

    zst->comp->avail_in_real += zst->zst.avail_in;

    if (zst->output->size) {
        zst->output_size = zst->zst.next_out - zst->output->buf;
    }

    return Z_OK;
}


// nfi_function: name('decompressor') map('zlib_stream*', 'POINTER')
int zlib_decompress(zlib_stream *zst, Byte *data,
            size_t len, ssize_t max_length)
{
    LOG_INFO("zlib_decompress(%p, data: %p, len: %zd, max_length: %zd)\n", zst, data, len, max_length);
    int needs_input = 0;
    int input_buffer_in_use = 0;

    /* Prepend unconsumed input if necessary */
    if (zst->zst.next_in != NULL) {

        /* Number of bytes we can append to input buffer */
        size_t avail_now = (zst->input_buffer->buf + zst->input_buffer_size) - (zst->zst.next_in + zst->comp->avail_in_real);

        /* Number of bytes we can append if we move existing
            contents to beginning of buffer (overwriting
            consumed input) */
            size_t avail_total = zst->input_buffer_size - zst->comp->avail_in_real;

        if (avail_total < len) {
            size_t offset = zst->zst.next_in - zst->input_buffer->buf;
            size_t new_size = zst->input_buffer_size + len - avail_now;

            /* Assign to temporary variable first, so we don't
                lose address of allocated buffer if realloc fails */
            int tmp = resize_off_heap_buffer(zst, new_size, /* input_buffer */ 0);
            if (tmp < 0) {
                error_occurred(zst, MEMORY_ERROR);
                return -1;
            }
            zst->input_buffer_size = new_size;

            zst->zst.next_in = zst->input_buffer->buf + offset;
        }
        else if (avail_now < len) {
            memmove(zst->input_buffer->buf, zst->zst.next_in,
                    zst->comp->avail_in_real);
                    zst->zst.next_in = zst->input_buffer->buf;
        }
        memcpy((void*)(zst->zst.next_in + zst->comp->avail_in_real), data, len);
        zst->comp->avail_in_real += len;
        input_buffer_in_use = 1;
    }
    else {
        zst->zst.next_in = data;
        zst->comp->avail_in_real = len;
        input_buffer_in_use = 0;
    }

    int result = decompress_buf(zst, max_length);
    if(result != Z_OK) {
        zst->zst.next_in = NULL;
        return -1;
    }

    if (zst->comp->eof) {
        needs_input = 0;

        if (zst->comp->avail_in_real > 0) {
            zlib_release_buffer(zst->comp->unused_data);
            zst->comp->unused_data = zlib_allocate_buffer(zst->comp->avail_in_real);
            memcpy(zst->comp->unused_data, (char *)zst->zst.next_in, zst->comp->avail_in_real);
        }
    }
    else if (zst->comp->avail_in_real == 0) {
        zst->zst.next_in = NULL;
        needs_input = 1;
    }
    else {
        needs_input = 0;

        /* If we did not use the input buffer, we now have
            to copy the tail from the caller's buffer into the
            input buffer */
        if (!input_buffer_in_use) {

            /* Discard buffer if it's too small
                (resizing it may needlessly copy the current contents) */
            if (zst->input_buffer != NULL &&
                zst->input_buffer_size < zst->comp->avail_in_real) {
                zlib_release_buffer(zst->input_buffer);
                zst->input_buffer = NULL;
            }

            /* Allocate if necessary */
            if (zst->input_buffer == NULL) {
                zst->input_buffer = zlib_allocate_buffer(zst->comp->avail_in_real);
                if (zst->input_buffer == NULL) {
                    error_occurred(zst, MEMORY_ERROR);
                    return -1;
                }
                zst->input_buffer_size = zst->comp->avail_in_real;
            }

            /* Copy tail */
            memcpy(zst->input_buffer->buf, zst->zst.next_in, zst->comp->avail_in_real);
            zst->zst.next_in = zst->input_buffer->buf;
        }
    }
    return needs_input;
}
