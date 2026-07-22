/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

// This source contains functions that require POSIX functions without GNU extensions
// These functions are called from NativePosixSupport Java class using native-access downcalls

#include <stdint.h>
#include <string.h>
#include <stdio.h>

#ifdef _WIN32
#define GP_EXPORT __declspec(dllexport)
#else
#define GP_EXPORT
#endif

GP_EXPORT void call_strerror(int32_t error, char *buf, int32_t buflen) {
#ifdef _WIN32
    int err = strerror_s(buf, buflen, error);
#else
    /*
      There are two versions of strerror_r and we need the POSIX one. The following lines double-check
      that we got it. First, we check that _GNU_SOURCE has not been defined by any of the included headers.
      Then we explicitly declare the function with POSIX signature which should force the compiler to
      report an error in case we got the GNU version somehow.
    */
#ifdef _GNU_SOURCE
#error "Someone defined _GNU_SOURCE"
#endif
    int strerror_r(int errnum, char *buf, size_t buflen);
    int err = strerror_r(error, buf, buflen);
#endif
    if (err) {
        snprintf(buf, buflen, "Unknown error %d", error);
    }
}
