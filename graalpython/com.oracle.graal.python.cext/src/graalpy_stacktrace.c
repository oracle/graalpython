/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "capi.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(MS_WINDOWS)
#include <windows.h>
#include <dbghelp.h>
#elif (defined(__linux__) && defined(__GNU_LIBRARY__)) || defined(__APPLE__)
#include <execinfo.h>
#endif

#define GRAALPY_NATIVE_STACK_MAX_NAME 1024
#define GRAALPY_NATIVE_STACK_LINE_BUFFER 2048
#define GRAALPY_NATIVE_STACK_CAPTURE_MAX 128

typedef void (*GraalPyStacktraceWriter)(void *ctx, const char *line);

static void
render_unavailable_stacktrace(GraalPyStacktraceWriter writer, void *ctx)
{
    writer(ctx, "<native stacktrace unavailable>");
}

#if defined(MS_WINDOWS)

static int
ensure_windows_symbols_initialized(void)
{
    static int initialized = 0;
    if (!initialized) {
        HANDLE process = GetCurrentProcess();
        SymSetOptions(SymGetOptions() | SYMOPT_LOAD_LINES | SYMOPT_UNDNAME);
        if (!SymInitialize(process, NULL, TRUE)) {
            return 0;
        }
        initialized = 1;
    }
    return 1;
}

static const char *
windows_basename(const char *path)
{
    const char *slash = strrchr(path, '\\');
    const char *alt = strrchr(path, '/');
    const char *base = slash != NULL ? slash + 1 : path;
    if (alt != NULL && (slash == NULL || alt > slash)) {
        base = alt + 1;
    }
    return base;
}

static void
render_windows_stacktrace(GraalPyStacktraceWriter writer, void *ctx, void *const *frames, size_t depth)
{
    HANDLE process = GetCurrentProcess();
    char line[GRAALPY_NATIVE_STACK_LINE_BUFFER];
    char symbol_buffer[sizeof(SYMBOL_INFO) + GRAALPY_NATIVE_STACK_MAX_NAME];
    PSYMBOL_INFO symbol = (PSYMBOL_INFO) symbol_buffer;

    memset(symbol_buffer, 0, sizeof(symbol_buffer));
    symbol->SizeOfStruct = sizeof(SYMBOL_INFO);
    symbol->MaxNameLen = GRAALPY_NATIVE_STACK_MAX_NAME - 1;

    if (!ensure_windows_symbols_initialized()) {
        for (size_t i = 0; i < depth; i++) {
            snprintf(line, sizeof(line), "frame[%lu]: %p",
                            (unsigned long) i, (void *) frames[i]);
            writer(ctx, line);
        }
        return;
    }

    for (size_t i = 0; i < depth; i++) {
        DWORD64 address = (DWORD64) (uintptr_t) frames[i];
        DWORD64 displacement = 0;
        IMAGEHLP_LINE64 source_line;
        DWORD source_displacement = 0;
        char module_path[MAX_PATH] = {'\0'};
        const char *module_name = NULL;
        HMODULE module = NULL;

        memset(&source_line, 0, sizeof(source_line));
        source_line.SizeOfStruct = sizeof(source_line);

        if (GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                        (LPCSTR) frames[i], &module) && GetModuleFileNameA(module, module_path, MAX_PATH) > 0) {
            module_name = windows_basename(module_path);
        }

        if (SymFromAddr(process, address, &displacement, symbol)) {
            if (SymGetLineFromAddr64(process, address, &source_displacement, &source_line)) {
                if (module_name != NULL) {
                    snprintf(line, sizeof(line), "frame[%lu]: %s!%s+0x%llx (%s:%lu) [%p]",
                                    (unsigned long) i, module_name, symbol->Name, (unsigned long long) displacement,
                                    source_line.FileName, (unsigned long) source_line.LineNumber, (void *) frames[i]);
                } else {
                    snprintf(line, sizeof(line), "frame[%lu]: %s+0x%llx (%s:%lu) [%p]",
                                    (unsigned long) i, symbol->Name, (unsigned long long) displacement,
                                    source_line.FileName, (unsigned long) source_line.LineNumber, (void *) frames[i]);
                }
            } else if (module_name != NULL) {
                snprintf(line, sizeof(line), "frame[%lu]: %s!%s+0x%llx [%p]",
                                (unsigned long) i, module_name, symbol->Name, (unsigned long long) displacement, (void *) frames[i]);
            } else {
                snprintf(line, sizeof(line), "frame[%lu]: %s+0x%llx [%p]",
                                (unsigned long) i, symbol->Name, (unsigned long long) displacement, (void *) frames[i]);
            }
        } else if (module_name != NULL) {
            snprintf(line, sizeof(line), "frame[%lu]: %s [%p]",
                            (unsigned long) i, module_name, (void *) frames[i]);
        } else {
            snprintf(line, sizeof(line), "frame[%lu]: %p",
                            (unsigned long) i, (void *) frames[i]);
        }
        writer(ctx, line);
    }
}

#elif (defined(__linux__) && defined(__GNU_LIBRARY__)) || defined(__APPLE__)

static void
render_execinfo_stacktrace(GraalPyStacktraceWriter writer, void *ctx, void *const *frames, size_t depth)
{
    char **symbols = backtrace_symbols((void *const *) frames, (int) depth);
    char line[GRAALPY_NATIVE_STACK_LINE_BUFFER];
    if (symbols == NULL) {
        for (size_t i = 0; i < depth; i++) {
            snprintf(line, sizeof(line), "frame[%lu]: %p",
                            (unsigned long) i, (void *) frames[i]);
            writer(ctx, line);
        }
        return;
    }

    for (size_t i = 0; i < depth; i++) {
        snprintf(line, sizeof(line), "frame[%lu]: %s", (unsigned long) i, symbols[i]);
        writer(ctx, line);
    }
    free(symbols);
}

#endif

size_t
GraalPyPrivate_CaptureStacktrace(void **frames, size_t max_depth, size_t skip)
{
    if (frames == NULL || max_depth == 0) {
        return 0;
    }
#if defined(MS_WINDOWS)
    return (size_t) CaptureStackBackTrace((ULONG) (skip + 1), (ULONG) max_depth, frames, NULL);
#elif (defined(__linux__) && defined(__GNU_LIBRARY__)) || defined(__APPLE__)
    void *captured_frames[GRAALPY_NATIVE_STACK_CAPTURE_MAX];
    size_t capture_depth = max_depth + skip + 1;
    if (capture_depth > (sizeof(captured_frames) / sizeof(captured_frames[0]))) {
        capture_depth = sizeof(captured_frames) / sizeof(captured_frames[0]);
    }
    int raw_depth = backtrace(captured_frames, (int) capture_depth);
    size_t depth = raw_depth > 0 ? (size_t) raw_depth : 0;
    size_t start = depth > (skip + 1) ? (skip + 1) : depth;
    size_t usable_depth = depth - start;
    if (usable_depth > 0) {
        memmove(frames, captured_frames + start, usable_depth * sizeof(void *));
    }
    return usable_depth;
#else
    return 0;
#endif
}

static void
render_stacktrace(GraalPyStacktraceWriter writer, void *ctx, void *const *frames, size_t depth)
{
    if (depth == 0) {
        render_unavailable_stacktrace(writer, ctx);
        return;
    }
#if defined(MS_WINDOWS)
    render_windows_stacktrace(writer, ctx, frames, depth);
#elif (defined(__linux__) && defined(__GNU_LIBRARY__)) || defined(__APPLE__)
    render_execinfo_stacktrace(writer, ctx, frames, depth);
#else
    (void) frames;
    render_unavailable_stacktrace(writer, ctx);
#endif
}

static void
file_writer(void *ctx, const char *line)
{
    fprintf((FILE *) ctx, "%s\n", line);
}

void
GraalPyPrivate_PrintCapturedStacktrace(FILE *file, const char *header, void *const *frames, size_t depth)
{
    if (header != NULL) {
        fputs(header, file);
    }
    render_stacktrace(file_writer, file, frames, depth);
    fflush(file);
}

void
GraalPyPrivate_PrintCurrentStacktrace(FILE *file, const char *header, size_t max_depth, size_t skip)
{
    void *frames[64];
    size_t depth = max_depth;
    if (depth > (sizeof(frames) / sizeof(frames[0]))) {
        depth = sizeof(frames) / sizeof(frames[0]);
    }
    depth = GraalPyPrivate_CaptureStacktrace(frames, depth, skip + 1);
    GraalPyPrivate_PrintCapturedStacktrace(file, header, frames, depth);
}

typedef struct {
    int level;
    const char *prefix;
} LogWriterCtx;

static void
log_writer(void *ctx, const char *line)
{
    LogWriterCtx *log_ctx = (LogWriterCtx *) ctx;
    if (log_ctx->prefix != NULL) {
        GraalPyPrivate_Log(log_ctx->level, "%s%s\n", log_ctx->prefix, line);
    } else {
        GraalPyPrivate_Log(log_ctx->level, "%s\n", line);
    }
}

void
GraalPyPrivate_LogCapturedStacktrace(int level, const char *prefix, void *const *frames, size_t depth)
{
    if ((Py_Truffle_Options & level) == 0) {
        return;
    }
    LogWriterCtx log_ctx = {level, prefix};
    render_stacktrace(log_writer, &log_ctx, frames, depth);
}
