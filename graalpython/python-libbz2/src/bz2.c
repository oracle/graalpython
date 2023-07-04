/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This implementation emulate CPython's bz2
 * see cpython/Modules/_bz2module.c
 */

#define DEBUG 500
// #define BENCHMARK

#include <limits.h>
#include <stdlib.h>
#include <string.h>


#include <bzlib.h>

typedef char  Byte;  /* 8 bits */

// Integer.MAX_INT
#define GRAALPYTHON_MAX_SIZE (INT_MAX)

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
    #define END_TIME(bzst) bzst->timeElapsed += get_time() - start_time;
#else
    #define START_TIME
    #define END_TIME(bzst)
#endif


// The ref_count is important for `copy` as we 
// share off heap storage between native objects.
typedef struct
{
    size_t ref_count;
    size_t size;
    Byte *buf;
} off_heap_buffer;

#define NOT_INITIALIZED 0
#define COMPRESS_TYPE 1
#define DECOMPRESS_TYPE 2
typedef struct
{
    bz_stream bzs;
    int bzs_type;

    off_heap_buffer *output;
    size_t output_size;

    ssize_t next_in_index;
    ssize_t bzs_avail_in_real;
#ifdef BENCHMARK
    double timeElapsed;
#endif
} bzst_stream;

static void* BZ2_Malloc(void* ctx, int items, int size)
{
    void *m = malloc((size_t)items * (size_t)size);
    LOG_FINER("malloc(address: %p, items: %u, size: %u)\n", m, items, size);
    return m; 
}

static void BZ2_Free(void* ctx, void *ptr)
{
    LOG_FINER("free(%p)\n", ptr);
    free(ptr);
}

static off_heap_buffer* bz_allocate_buffer(size_t items)
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

static void bz_release_buffer(off_heap_buffer *o) {
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

// nfi_function: name('createStream') map('bzst_stream*', 'POINTER')
bzst_stream *bz_create_bzst_stream() {
    bzst_stream *bzst = (bzst_stream *) malloc(sizeof(bzst_stream));
    bzst->bzs.opaque = NULL;
    bzst->bzs.bzalloc = BZ2_Malloc;
    bzst->bzs.bzfree = BZ2_Free;
    bzst->bzs_type = NOT_INITIALIZED;
    bzst->output = bz_allocate_buffer(1);
    bzst->output->size = 0;
    bzst->output_size = 0;
    bzst->next_in_index = 0;
    bzst->bzs_avail_in_real = 0;

#ifdef BENCHMARK
    bzst->timeElapsed = 0;
#endif
    LOG_INFO("bzst_stream(%p)\n", bzst);
    return bzst;
}

// nfi_function: name('getTimeElapsed') map('bzst_stream*', 'POINTER')  static(true)
double bz_get_timeElapsed(bzst_stream* zst) {
#ifdef BENCHMARK
    double t = bzst->timeElapsed;
    LOG_FINEST("time Elapsed: %.2f\n", t);
    bzst->timeElapsed = 0;
    return t;
#else
    return -1.0;
#endif
}

// nfi_function: name('deallocateStream') map('bzst_stream*', 'POINTER')
void bz_free_stream(bzst_stream* bzst) {
    if (!bzst) {
        return;
    }
    if(bzst->bzs_type) {
        if (bzst->bzs_type == COMPRESS_TYPE) {
            BZ2_bzCompressEnd(&bzst->bzs);
        } else {
            BZ2_bzDecompressEnd(&bzst->bzs);
        }
    }
    bz_release_buffer(bzst->output);
    LOG_INFO("free bzst_stream(%p)\n", bzst);
    free(bzst);
}

// nfi_function: name('gcReleaseHelper') map('bzst_stream*', 'POINTER') release(true)
void bz_gc_helper(bzst_stream* bzst) {
    bz_free_stream(bzst);
}

// nfi_function: name('getNextInIndex') map('bzst_stream*', 'POINTER')
ssize_t bz_get_next_in_index(bzst_stream *bzst) {
    return bzst->next_in_index;
}

// nfi_function: name('getBzsAvailInReal') map('bzst_stream*', 'POINTER')
ssize_t bz_get_bzs_avail_in_real(bzst_stream *bzst) {
    return bzst->bzs_avail_in_real;
}

// nfi_function: name('setBzsAvailInReal') map('bzst_stream*', 'POINTER')
void bz_set_bzs_avail_in_real(bzst_stream *bzst, ssize_t v) {
    bzst->bzs_avail_in_real = v;
}

// nfi_function: name('getOutputBufferSize') map('bzst_stream*', 'POINTER')
size_t bz_get_output_buffer_size(bzst_stream *bzst) {
    LOG_INFO("bz_get_output_buffer_size(%p)\n", bzst);
    size_t size = bzst->output_size;
    if (size > GRAALPYTHON_MAX_SIZE) {
        LOG_SEVERE("buffer size is larger than max: %zd > %zd!!\n", size, (ssize_t) GRAALPYTHON_MAX_SIZE);
        return GRAALPYTHON_MAX_SIZE;
    }
    return size;
}

static void clear_output(bzst_stream *bzst) {
    bz_release_buffer(bzst->output);
    bzst->output = bz_allocate_buffer(1);
    bzst->output->size = 0;
    bzst->output_size = 0;
}

// nfi_function: name('getOutputBuffer') map('bzst_stream*', 'POINTER')
void bz_get_output_buffer(bzst_stream *bzst, Byte *dest) {
    LOG_INFO("bz_get_off_heap_buffer(%p)\n", bzst);
    off_heap_buffer *buffer = bzst->output;
    size_t size = bzst->output_size;
    if (!size) {
        return;
    }
    memcpy(dest, buffer->buf, size);
    clear_output(bzst);
}

static int resize_output_buffer(bzst_stream *bzst, ssize_t length) {
    LOG_INFO("resize_output_buffer(%p, %zd)\n", bzst, length);
    off_heap_buffer *current = bzst->output;
    off_heap_buffer *resized = bz_allocate_buffer(length);
    if (!resized) {
        return -1;
    }
    memcpy(resized->buf, current->buf, bzst->output->size);
    bz_release_buffer(current);
    bzst->output = resized;
    return 0;
}

static int bz_prepare_output_buffer(bzst_stream *bzst, ssize_t len) {
    LOG_INFO("bz_prepare_output_buffer(%p, %zd)\n", bzst, len);
    bz_release_buffer(bzst->output);
    bzst->output = bz_allocate_buffer(len);
    if (!bzst->output) {
        return -1;
    }
    bzst->output_size = 0;
    return 0;
}

static int isOK(int bzerror) {
    switch(bzerror) {
        case BZ_OK:
        case BZ_RUN_OK:
        case BZ_FLUSH_OK:
        case BZ_FINISH_OK:
        case BZ_STREAM_END:
            return 1;
        default:
            return 0;
    }
}

static int
grow_buffer(bzst_stream *bzst, ssize_t max_length) {
    LOG_INFO("grow_buffer(%p, %zd)\n", bzst, max_length);
    /* Expand the buffer by an amount proportional to the current size,
       giving us amortized linear-time behavior. Use a less-than-double
       growth factor to avoid excessive allocation. */
    size_t size = bzst->output->size;
    size_t new_size = size + (size >> 3) + 6;

    if (max_length > 0 && new_size > (size_t) max_length)
        new_size = (size_t) max_length;

    if (new_size > size) {
        return resize_output_buffer(bzst, new_size);
    } else {  /* overflow */
        return -1;
    }
}

/************************************************
 *               Compress Object                *
 ************************************************/


// nfi_function: name('compressInit') map('bzst_stream*', 'POINTER')
int bz_compressor_init(bzst_stream *bzst, int compresslevel) {
    LOG_INFO("bz_compressor_init(%p, %d)\n", bzst, compresslevel);
    int bzerror = BZ2_bzCompressInit(&bzst->bzs, compresslevel, 0, 0);
    if (!isOK(bzerror)) {
        return bzerror;
    }
    bzst->bzs_type = COMPRESS_TYPE;
    return BZ_OK;
}

// nfi_function: name('compress') map('bzst_stream*', 'POINTER')
int bz_compress(bzst_stream *bzst, Byte *data, ssize_t len, int action, ssize_t bufsize) {
    LOG_INFO("bz_compress(%p, %zd, %d, %zd)\n", bzst, len, action, bufsize);
    size_t data_size = 0;

    if (bz_prepare_output_buffer(bzst, bufsize) < 0) {
        return BZ_MEM_ERROR;
    }

    if (len == 0) {
        bzst->bzs.next_in = NULL;
    } else {
        bzst->bzs.next_in = data;
    }
    
    bzst->bzs.avail_in = 0;
    bzst->bzs.next_out = bzst->output->buf;
    bzst->bzs.avail_out = bzst->output->size;
    for (;;) {
        char *this_out;
        int bzerror;

        /* On a 64-bit system, len might not fit in avail_in (an unsigned int).
           Do compression in chunks of no more than UINT_MAX bytes each. */
        if (bzst->bzs.avail_in == 0 && len > 0) {
            if (len > GRAALPYTHON_MAX_SIZE) {
                bzst->bzs.avail_in = GRAALPYTHON_MAX_SIZE;
            } else {
                bzst->bzs.avail_in = len;
            }
            len -= bzst->bzs.avail_in;
        }

        /* In regular compression mode, stop when input data is exhausted. */
        if (action == BZ_RUN && bzst->bzs.avail_in == 0)
            break;

        if (bzst->bzs.avail_out == 0) {
            size_t buffer_left = bzst->output->size - data_size;
            if (buffer_left == 0) {
                if (grow_buffer(bzst, -1) < 0) {
                    clear_output(bzst);
                    return BZ_MEM_ERROR;
                }
                bzst->bzs.next_out = bzst->output->buf + data_size;
                buffer_left = bzst->output->size - data_size;
            }
            if (buffer_left > GRAALPYTHON_MAX_SIZE) {
                bzst->bzs.avail_out = GRAALPYTHON_MAX_SIZE;
            } else {
                bzst->bzs.avail_out = buffer_left;
            }
        }

        this_out = bzst->bzs.next_out;
        bzerror = BZ2_bzCompress(&bzst->bzs, action);
        data_size += bzst->bzs.next_out - this_out;
        if (!isOK(bzerror)) {
            clear_output(bzst);
            return bzerror;
        }

        /* In flushing mode, stop when all buffered data has been flushed. */
        if (action == BZ_FINISH && bzerror == BZ_STREAM_END)
            break;
    }
    bzst->output_size = data_size;
    return BZ_OK;
}


/************************************************
 *              Decompress Object               *
 ************************************************/

// nfi_function: name('decompressInit') map('bzst_stream*', 'POINTER')
int bz_decompress_init(bzst_stream *bzst) {
    LOG_INFO("bz_decompress_init(%p)\n", bzst);
    int bzerror = BZ2_bzDecompressInit(&bzst->bzs, 0, 0);
    if (!isOK(bzerror)) {
        return bzerror;
    }

    bzst->bzs_type = DECOMPRESS_TYPE;
    return BZ_OK;
}


/* Decompress data of length d->bzs_avail_in_real in d->bzs.next_in.  The output
   buffer is allocated dynamically and returned.  At most max_length bytes are
   returned, so some of the input may not be consumed. d->bzs.next_in and
   d->bzs_avail_in_real are updated to reflect the consumed input. */
// nfi_function: name('decompress') map('bzst_stream*', 'POINTER')
int bz_decompress(bzst_stream *bzst, 
                Byte *input_buffer, ssize_t offset, 
                ssize_t max_length,
                ssize_t bufsize, ssize_t bzs_avail_in_real) {
    LOG_INFO("bz_decompress(%p, %p, %zd, %zd, %zd)\n", bzst, input_buffer, offset, max_length, bufsize);
    /* data_size is strictly positive, but because we repeatedly have to
       compare against max_length and PyBytes_GET_SIZE we declare it as
       signed */

    ssize_t data_size = 0;
    bz_stream *bzs = &bzst->bzs;
    bzst->bzs_avail_in_real = bzs_avail_in_real;
    if (offset < 0) {
        bzs->next_in = NULL;
    } else {
        bzs->next_in = input_buffer + offset;
    }

    int check_alloc;
    if (max_length < 0 || max_length >= bufsize) {
        check_alloc = bz_prepare_output_buffer(bzst, bufsize);
    } else {
        check_alloc = bz_prepare_output_buffer(bzst, max_length);
    }
    if (check_alloc < 0) {
        return BZ_MEM_ERROR;
    }

    bzs->next_out = bzst->output->buf;
    int ret = BZ_OK;
    for (;;) {
        int bzret;
        size_t avail;

        /* On a 64-bit system, buffer length might not fit in avail_out, so we
           do decompression in chunks of no more than UINT_MAX bytes
           each. Note that the expression for `avail` is guaranteed to be
           positive, so the cast is safe. */
        avail = (size_t) (bzst->output->size - data_size);
        if (avail > GRAALPYTHON_MAX_SIZE) {
            bzs->avail_out = (unsigned int) GRAALPYTHON_MAX_SIZE;
        } else {
            bzs->avail_out = (unsigned int) avail;
        }

        if (bzst->bzs_avail_in_real > GRAALPYTHON_MAX_SIZE) {
            bzs->avail_in = (unsigned int) GRAALPYTHON_MAX_SIZE;
        } else {
            bzs->avail_in = (unsigned int) bzst->bzs_avail_in_real;
        }
        bzst->bzs_avail_in_real -= bzs->avail_in;

        bzret = BZ2_bzDecompress(bzs);
        data_size = bzs->next_out - bzst->output->buf;
        bzst->next_in_index = bzs->next_in - input_buffer;
        bzst->bzs_avail_in_real += bzs->avail_in;
        if (!isOK(bzret)) {
            clear_output(bzst);
            return bzret;
        }
        if (bzret == BZ_STREAM_END) {
            // d->eof = 1; // it will be set on the java-side
            ret = bzret;
            break;
        } else if (bzst->bzs_avail_in_real == 0) {
            break;
        } else if (bzs->avail_out == 0) {
            if (data_size == max_length) {
                break;
            }
            if (data_size == bzst->output->size && grow_buffer(bzst, max_length) < 0) {
                clear_output(bzst);
                return BZ_MEM_ERROR;
            }
            bzs->next_out = bzst->output->buf + data_size;
        }
    }
    bzst->output_size = data_size;
    return ret;
}
