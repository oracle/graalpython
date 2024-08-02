#
# Copyright (c) 2023, Oracle and/or its affiliates.
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
project(python-liblzma)

function(require_var var)
  if (NOT ${var})
    message(FATAL_ERROR "${var} needs to be set")
  endif()
endfunction()

require_var(XZ_SRC)

set(XZ_VERSION "${XZ_VERSION_MAJOR}.${XZ_VERSION_MINOR}.${XZ_VERSION_PATCH}")

message(VERBOSE "Building liblzma version: ${XZ_VERSION}")

set(PACKAGE_NAME "xz")
set(TARGET_LZMA "lzma")
set(TARGET_LZMASUPPORT "lzmasupport")
set(XZ_ROOT "${XZ_SRC}/${PACKAGE_NAME}-${XZ_VERSION}")
set(SRC_DIR "${XZ_ROOT}/src")
set(API_DIR "${XZ_ROOT}/src/liblzma/api")

set(XZ_HEADERS
    "${API_DIR}/lzma.h"
    "${API_DIR}/lzma/version.h"
    "${API_DIR}/lzma/base.h"
    "${API_DIR}/lzma/vli.h"
    "${API_DIR}/lzma/check.h"
    "${API_DIR}/lzma/filter.h"
    "${API_DIR}/lzma/bcj.h"
    "${API_DIR}/lzma/delta.h"
    "${API_DIR}/lzma/lzma12.h"
    "${API_DIR}/lzma/container.h"
    "${API_DIR}/lzma/stream_flags.h"
    "${API_DIR}/lzma/block.h"
    "${API_DIR}/lzma/index.h"
    "${API_DIR}/lzma/index_hash.h"
    "${API_DIR}/lzma/hardware.h"
)

add_library(${TARGET_LZMA} STATIC)
add_library(${TARGET_LZMASUPPORT} SHARED)

# we want '-fPIC' even for the static lib
set_target_properties(${TARGET_LZMA} PROPERTIES POSITION_INDEPENDENT_CODE TRUE)

set_target_properties(${TARGET_LZMASUPPORT} PROPERTIES COMPILE_WARNING_AS_ERROR ON)

# preprocessor defines for all platforms
target_compile_definitions(${TARGET_LZMA} PRIVATE HAVE_CONFIG_H)
target_compile_definitions(${TARGET_LZMASUPPORT} PRIVATE NDEBUG)

if(WIN32)
    target_compile_options(${TARGET_LZMA} PRIVATE /Z7 /O2)
    target_compile_options(${TARGET_LZMASUPPORT} PRIVATE /Z7 /O2 /WX)
else()
    target_compile_options(${TARGET_LZMA} PRIVATE -fPIC -DPIC -g -O2)
    target_compile_definitions(${TARGET_LZMA} PRIVATE PIC)

    target_compile_options(${TARGET_LZMASUPPORT} PRIVATE -g -O3)
endif()

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
target_sources(${TARGET_LZMA} PRIVATE ${XZ_HEADERS})

target_sources(${TARGET_LZMA} PRIVATE
    "${API_DIR}/lzma.h"
    "${SRC_DIR}/common/tuklib_physmem.c"
    "${SRC_DIR}/liblzma/common/vli_size.c"
    "${SRC_DIR}/liblzma/common/hardware_physmem.c"
    "${SRC_DIR}/liblzma/common/filter_encoder.c"
    "${SRC_DIR}/liblzma/common/block_buffer_decoder.c"
    "${SRC_DIR}/liblzma/common/index.c"
    "${SRC_DIR}/liblzma/common/stream_encoder.c"
    "${SRC_DIR}/liblzma/common/block_encoder.c"
    "${SRC_DIR}/liblzma/common/easy_encoder.c"
    "${SRC_DIR}/liblzma/common/easy_buffer_encoder.c"
    "${SRC_DIR}/liblzma/common/block_util.c"
    "${SRC_DIR}/liblzma/common/filter_buffer_decoder.c"
    "${SRC_DIR}/liblzma/common/stream_flags_encoder.c"
    "${SRC_DIR}/liblzma/common/easy_preset.c"
    "${SRC_DIR}/liblzma/common/index_encoder.c"
    "${SRC_DIR}/liblzma/common/common.c"
    "${SRC_DIR}/liblzma/common/block_header_encoder.c"
    "${SRC_DIR}/liblzma/common/vli_encoder.c"
    "${SRC_DIR}/liblzma/common/block_header_decoder.c"
    "${SRC_DIR}/liblzma/common/stream_buffer_encoder.c"
    "${SRC_DIR}/liblzma/common/block_buffer_encoder.c"
    "${SRC_DIR}/liblzma/common/alone_decoder.c"
    "${SRC_DIR}/liblzma/common/auto_decoder.c"
    "${SRC_DIR}/liblzma/common/stream_flags_common.c"
    "${SRC_DIR}/liblzma/common/easy_decoder_memusage.c"
    "${SRC_DIR}/liblzma/common/easy_encoder_memusage.c"
    "${SRC_DIR}/liblzma/common/filter_buffer_encoder.c"
    "${SRC_DIR}/liblzma/common/filter_decoder.c"
    "${SRC_DIR}/liblzma/common/filter_flags_encoder.c"
    "${SRC_DIR}/liblzma/common/filter_common.c"
    "${SRC_DIR}/liblzma/common/alone_encoder.c"
    "${SRC_DIR}/liblzma/common/block_decoder.c"
    "${SRC_DIR}/liblzma/common/filter_flags_decoder.c"
    "${SRC_DIR}/liblzma/common/index_decoder.c"
    "${SRC_DIR}/liblzma/common/stream_buffer_decoder.c"
    "${SRC_DIR}/liblzma/common/stream_flags_decoder.c"
    "${SRC_DIR}/liblzma/common/index_hash.c"
    "${SRC_DIR}/liblzma/common/stream_decoder.c"
    "${SRC_DIR}/liblzma/common/vli_decoder.c"
    "${SRC_DIR}/liblzma/check/crc64_table.c"
    "${SRC_DIR}/liblzma/check/crc32_table.c"
    "${SRC_DIR}/liblzma/check/crc64_fast.c"
    "${SRC_DIR}/liblzma/check/crc32_fast.c"
    "${SRC_DIR}/liblzma/check/check.c"
    "${SRC_DIR}/liblzma/check/sha256.c"
    "${SRC_DIR}/liblzma/lz/lz_encoder.c"
    "${SRC_DIR}/liblzma/lz/lz_encoder_mf.c"
    "${SRC_DIR}/liblzma/lz/lz_decoder.c"
    "${SRC_DIR}/liblzma/lzma/lzma_encoder_presets.c"
    "${SRC_DIR}/liblzma/lzma/lzma_encoder.c"
    "${SRC_DIR}/liblzma/lzma/lzma_encoder_optimum_fast.c"
    "${SRC_DIR}/liblzma/lzma/lzma_encoder_optimum_normal.c"
    "${SRC_DIR}/liblzma/lzma/lzma2_encoder.c"
    "${SRC_DIR}/liblzma/lzma/lzma2_decoder.c"
    "${SRC_DIR}/liblzma/lzma/lzma_decoder.c"
    "${SRC_DIR}/liblzma/lzma/fastpos_table.c"
    "${SRC_DIR}/liblzma/rangecoder/price_table.c"
    "${SRC_DIR}/liblzma/delta/delta_common.c"
    "${SRC_DIR}/liblzma/delta/delta_encoder.c"
    "${SRC_DIR}/liblzma/delta/delta_decoder.c"
    "${SRC_DIR}/liblzma/simple/simple_coder.c"
    "${SRC_DIR}/liblzma/simple/simple_encoder.c"
    "${SRC_DIR}/liblzma/simple/simple_decoder.c"
    "${SRC_DIR}/liblzma/simple/powerpc.c"
    "${SRC_DIR}/liblzma/simple/x86.c"
    "${SRC_DIR}/liblzma/simple/arm.c"
    "${SRC_DIR}/liblzma/simple/ia64.c"
    "${SRC_DIR}/liblzma/simple/sparc.c"
    "${SRC_DIR}/liblzma/simple/armthumb.c"
)


target_include_directories(${TARGET_LZMA} PRIVATE
    "."
    "${SRC_DIR}/liblzma"
    "${SRC_DIR}/liblzma/api"
    "${SRC_DIR}/liblzma/common"
    "${SRC_DIR}/liblzma/check"
    "${SRC_DIR}/liblzma/lz"
    "${SRC_DIR}/liblzma/rangecoder"
    "${SRC_DIR}/liblzma/lzma"
    "${SRC_DIR}/liblzma/delta"
    "${SRC_DIR}/liblzma/simple"
    "${SRC_DIR}/common"
)

target_include_directories(${TARGET_LZMASUPPORT} PRIVATE ${API_DIR})
target_sources(${TARGET_LZMASUPPORT} PRIVATE "src/lzma.c")
target_link_libraries(${TARGET_LZMASUPPORT} PRIVATE ${TARGET_LZMA})

install(TARGETS ${TARGET_LZMASUPPORT} DESTINATION bin)
