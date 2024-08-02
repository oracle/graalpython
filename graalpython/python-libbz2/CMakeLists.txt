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
project(python-libbz2)

include(libbz2.cmake)

if(NOT DEFINED TARGET_LIBBZ2)
    message(FATAL_ERROR "TARGET_LIBBZ2 needs to be set")
endif()

set(PACKAGE_NAME "bzip2")
set(TARGET_BZIP2SUPPORT "bz2support")

add_library(${TARGET_BZIP2SUPPORT} SHARED)

# preprocessor defines for all platforms
target_compile_definitions(${TARGET_BZIP2SUPPORT} PRIVATE NDEBUG)

if(WIN32)
    target_compile_options(${TARGET_BZIP2SUPPORT} PRIVATE /Z7 /O2 /WX)
else()
    target_compile_options(${TARGET_BZIP2SUPPORT} PRIVATE -Wall -Werror -O3 -g)
endif()

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

target_sources(${TARGET_BZIP2SUPPORT} PRIVATE "src/bz2.c")
set_target_properties(${TARGET_BZIP2SUPPORT} PROPERTIES COMPILE_WARNING_AS_ERROR ON)
# variable 'BZIP2_SRC' is provided by file 'libbz2.cmake'
target_include_directories(${TARGET_BZIP2SUPPORT} PRIVATE ${BZIP2_SRC})
target_link_libraries(${TARGET_BZIP2SUPPORT} PRIVATE ${TARGET_LIBBZ2})

install(TARGETS ${TARGET_BZIP2SUPPORT} DESTINATION bin)
