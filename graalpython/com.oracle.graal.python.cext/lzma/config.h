/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#define ASSUME_RAM 128
#define ENABLE_NLS 1
#define HAVE_CHECK_CRC32 1
#define HAVE_CHECK_CRC64 1
#define HAVE_CHECK_SHA256 1
#define HAVE_DCGETTEXT 1
#define HAVE_DECL_PROGRAM_INVOCATION_NAME 1
#define HAVE_DECODERS 1
#define HAVE_DECODER_ARM 1
#define HAVE_DECODER_ARMTHUMB 1
#define HAVE_DECODER_DELTA 1
#define HAVE_DECODER_IA64 1
#define HAVE_DECODER_LZMA1 1
#define HAVE_DECODER_LZMA2 1
#define HAVE_DECODER_POWERPC 1
#define HAVE_DECODER_SPARC 1
#define HAVE_DECODER_X86 1
#define HAVE_DLFCN_H 1
#define HAVE_ENCODERS 1
#define HAVE_ENCODER_ARM 1
#define HAVE_ENCODER_ARMTHUMB 1
#define HAVE_ENCODER_DELTA 1
#define HAVE_ENCODER_IA64 1
#define HAVE_ENCODER_LZMA1 1
#define HAVE_ENCODER_LZMA2 1
#define HAVE_ENCODER_POWERPC 1
#define HAVE_ENCODER_SPARC 1
#define HAVE_ENCODER_X86 1
#define HAVE_FCNTL_H 1
#define HAVE_FUTIMENS 1
#define HAVE_GETTEXT 1
#ifdef __aarch64__
#undef HAVE_IMMINTRIN_H
#else
#define HAVE_IMMINTRIN_H 1
#endif
#define HAVE_INTTYPES_H 1
#define HAVE_LIMITS_H 1
#define HAVE_MBRTOWC 1
#define HAVE_MEMORY_H 1
#define HAVE_MF_BT2 1
#define HAVE_MF_BT3 1
#define HAVE_MF_BT4 1
#define HAVE_MF_HC3 1
#define HAVE_MF_HC4 1
#define HAVE_POSIX_FADVISE 1
#define HAVE_STDBOOL_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STRING_H 1
#define HAVE_STRUCT_STAT_ST_ATIM_TV_NSEC 1
#define HAVE_SYS_PARAM_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TIME_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UINTPTR_T 1
#define HAVE_UNISTD_H 1
#define HAVE_VISIBILITY 1
#define HAVE_WCWIDTH 1
#define HAVE__BOOL 1
#define HAVE__MM_MOVEMASK_EPI8 1
#define HAVE___BUILTIN_ASSUME_ALIGNED 1
#define HAVE___BUILTIN_BSWAPXX 1
#define NDEBUG 1
#define SIZEOF_SIZE_T 8
#define STDC_HEADERS 1
#define TUKLIB_CPUCORES_SCHED_GETAFFINITY 1
#define TUKLIB_FAST_UNALIGNED_ACCESS 1
#ifdef __APPLE__
#define TUKLIB_PHYSMEM_SYSCTL 1
#else
#define TUKLIB_PHYSMEM_SYSCONF 1
#endif
#define VERSION "5.2.6"
#ifndef _DARWIN_USE_64_BIT_INODE
# define _DARWIN_USE_64_BIT_INODE 1
#endif
