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
project(com.oracle.graal.python.hpy.llvm)

function(require_var var)
  if (NOT ${var})
    message(FATAL_ERROR "${var} needs to be set")
  endif()
endfunction()

function(check_var var)
  set(${var} PARENT_SCOPE)
  require_var(${var})
endfunction()

# set variable from environement variable if the latter exists
function(setFromEnv varname envname)
  if(DEFINED ENV{${envname}})
    set(${varname} $ENV{${envname}} PARENT_SCOPE)
  endif()
endfunction()

require_var(LLVM_MODE)

set(TARGET_LIB "hpy-${LLVM_MODE}")
# set(LIB_OUTPUT_DIR "${PROJECT_OUTPUT_DIR}/lib")

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

check_var(GRAALVM_HPY_INCLUDE_DIR)
check_var(GRAALVM_PYTHON_INCLUDE_DIR)
check_var(TRUFFLE_H_INC)

file(GLOB_RECURSE SRC_FILES
    LIST_DIRECTORIES TRUE
    "${CMAKE_CURRENT_LIST_DIR}/src/*.c")

# only for Windows but to avoid a CMake warning
check_var(GRAALVM_LLVM_LIB_DIR)

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
add_library(${TARGET_LIB} SHARED)

target_sources(${TARGET_LIB} PRIVATE ${SRC_FILES})
target_include_directories(${TARGET_LIB} PRIVATE
    "${GRAALVM_HPY_INCLUDE_DIR}"
    "${GRAALVM_PYTHON_INCLUDE_DIR}"
    "${TRUFFLE_H_INC}"
)

if(WIN32)
    target_link_directories(${TARGET_LIB} PRIVATE ${GRAALVM_LLVM_LIB_DIR})
    target_link_libraries(${TARGET_LIB} graalvm-llvm)
    target_compile_options(${TARGET_LIB} PRIVATE /Z7 /O2 /WX)
else()
    target_compile_options(${TARGET_LIB} PRIVATE
        -DHPY_UNIVERSAL_ABI
        -DNDEBUG
        -DGRAALVM_PYTHON_LLVM
        -Werror
        -Wno-int-to-pointer-cast
        -Wno-int-conversion
        -Wno-void-pointer-to-int-cast
        -Wno-incompatible-pointer-types-discards-qualifiers
        -Wno-pointer-type-mismatch
        -Wno-braced-scalar-init
        -Wno-deprecated-declarations
    )
endif()

if(APPLE)
    target_link_options(${TARGET_LIB} PRIVATE -undefined dynamic_lookup)
endif()

install(TARGETS ${TARGET_LIB} DESTINATION bin)
