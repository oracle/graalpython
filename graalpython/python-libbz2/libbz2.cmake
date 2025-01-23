#
# Copyright (c) 2023, 2024, Oracle and/or its affiliates.
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
project(libbz2)

function(require_var var)
    if (NOT DEFINED ${var})
        message(FATAL_ERROR "${var} needs to be set; was '${var}'")
    endif()
endfunction()

require_var(BZIP2_ROOT)
require_var(BZIP2_VERSION_MAJOR)
require_var(BZIP2_VERSION_MINOR)
require_var(BZIP2_VERSION_PATCH)

set(BZIP2_VERSION "${BZIP2_VERSION_MAJOR}.${BZIP2_VERSION_MINOR}.${BZIP2_VERSION_PATCH}")

message(VERBOSE "Building libbz2 version: ${BZIP2_VERSION}")

set(PACKAGE_NAME "bzip2")
set(TARGET_LIBBZ2 "bz2")
set(BZIP2_SRC "${BZIP2_ROOT}/${PACKAGE_NAME}-${BZIP2_VERSION}")

add_library(${TARGET_LIBBZ2} STATIC)

# we want '-fPIC' even for the static lib
set_target_properties(${TARGET_LIBBZ2} PROPERTIES POSITION_INDEPENDENT_CODE TRUE)

# preprocessor defines for all platforms
target_compile_definitions(${TARGET_LIBBZ2} PRIVATE _FILE_OFFSET_BITS=64)

if(WIN32)
    target_compile_options(${TARGET_LIBBZ2} PRIVATE /O2 /Wall)
else()
    target_compile_options(${TARGET_LIBBZ2} PRIVATE -Wall -Winline -O2)
endif()

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
target_sources(${TARGET_LIBBZ2} PRIVATE
    "${BZIP2_SRC}/bzlib.h"
    "${BZIP2_SRC}/blocksort.c"
    "${BZIP2_SRC}/huffman.c"
    "${BZIP2_SRC}/crctable.c"
    "${BZIP2_SRC}/randtable.c"
    "${BZIP2_SRC}/compress.c"
    "${BZIP2_SRC}/decompress.c"
    "${BZIP2_SRC}/bzlib.c"
)
