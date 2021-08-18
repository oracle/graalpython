/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#define DEBUG 500

#include <stdlib.h>
#include <string.h>

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

// nfi_function: name('malloc')
void* ctypesMalloc(size_t size) {
    void *m = malloc(size);
    LOG_FINER("malloc(address: %p, size: %u)\n", m, size);
    return m; 
}

// nfi_function: name('free')
void ctypesFree(void *ptr) {
    LOG_FINER("free(%p)\n", ptr);
    free(ptr);
}

// nfi_function: name('gcReleaseHelper') release(true)
void CtypesGCHelper(void *ptr) {
    ctypesFree(ptr);
}

// nfi_function: name('memcpy')
void ctypesMemcpy(void *src, Byte *dest, size_t size, int free_src) {
    LOG_INFO("memcpy(%p)\n", src);
    memcpy(dest, src, size);
    if (free_src) {
        ctypesFree(src);
    }
}

// nfi_function: name('toNative')
void* ctypesToNative(Byte *src, size_t size) {
    void *m = malloc(size);
    memcpy(m, src, size);
    LOG_FINER("malloc(address: %p, size: %u)\n", m, size);
    return m; 
}
