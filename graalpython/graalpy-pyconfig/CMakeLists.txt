#
# Copyright (c) 2024, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
cmake_minimum_required(VERSION 3.22)
project(graalpy-pyconfig)

include(CheckSymbolExists)
include(CheckIncludeFile)
include (TestBigEndian)

test_big_endian(IS_BIG_ENDIAN)

set(CMAKE_REQUIRED_LINK_OPTIONS "-lm")

check_include_file("dirent.h" HAVE_DIRENT_H)
check_include_file("errno.h" HAVE_ERRNO_H)
check_include_file("utime.h" HAVE_UTIME_H)
check_include_file("signal.h" HAVE_SIGNAL_H)
check_include_file("fcntl.h" HAVE_FCNTL_H)
check_include_file("wchar.h" HAVE_WCHAR_H)
check_include_file("unistd.h" HAVE_UNISTD_H)
check_include_file("pthread.h" HAVE_PTHREAD_H)
check_include_file("sys/stat.h" HAVE_SYS_STAT_H)
check_include_file("sys/wait.h" HAVE_SYS_WAIT_H)
check_include_file("sys/time.h" HAVE_SYS_TIME_H)

check_symbol_exists(acosh "math.h" HAVE_ACOSH)
check_symbol_exists(asinh "math.h" HAVE_ASINH)
check_symbol_exists(atanh "math.h" HAVE_ATANH)
check_symbol_exists(copysign "math.h" HAVE_COPYSIGN)
check_symbol_exists(round "math.h" HAVE_ROUND)
check_symbol_exists(hypot "math.h" HAVE_HYPOT)
check_symbol_exists(clock "time.h" HAVE_CLOCK)
check_symbol_exists(sendfile "sys/sendfile.h" HAVE_SENDFILE)

if(HAVE_SYS_TIME_H)
    set(TIME_WITH_SYS_TIME 1)
endif()
if(WIN32)
    set(NT_THREADS 1)
endif()
if(IS_BIG_ENDIAN)
    set(DOUBLE_IS_BIG_ENDIAN_IEEE754 1)
else()
    set(DOUBLE_IS_LITTLE_ENDIAN_IEEE754 1)
endif()

configure_file("pyconfig_template.h" "pyconfig.h" @ONLY)
