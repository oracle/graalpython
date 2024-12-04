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
project(com.oracle.graal.python.hpy.llvm)

function(require_var var)
  if (NOT DEFINED ${var})
    message(FATAL_ERROR "${var} needs to be set")
  endif()
endfunction()

# default to CMake's source dir if not explicitly provided
if(NOT DEFINED SRC_DIR)
    set(SRC_DIR "${CMAKE_SOURCE_DIR}")
endif()

set(TARGET_LIB "hpy-native")

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

require_var(GRAALPY_PARENT_DIR)
require_var(GRAALVM_HPY_INCLUDE_DIR)
require_var(GRAALVM_PYTHON_INCLUDE_DIR)
require_var(PYCONFIG_INCLUDE_DIR)
require_var(TRUFFLE_H_INC)

set(HPY_SRC "${SRC_DIR}/src")
set(HPY_INC "${SRC_DIR}/include")

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
set(SRC_FILES ${HPY_SRC}/hpy.c
              ${HPY_SRC}/hpynative.h
)

set(HPY_HEADERS ${HPY_INC}/hpy/autogen_hpyslot.h
                ${HPY_INC}/hpy/version.h
                ${HPY_INC}/hpy/hpytype.h
                ${HPY_INC}/hpy/universal/hpyfunc_trampolines.h
                ${HPY_INC}/hpy/universal/autogen_trampolines.h
                ${HPY_INC}/hpy/universal/misc_trampolines.h
                ${HPY_INC}/hpy/universal/autogen_ctx.h
                ${HPY_INC}/hpy/universal/autogen_hpyfunc_trampolines.h
                ${HPY_INC}/hpy/hpydef.h
                ${HPY_INC}/hpy/runtime/argparse.h
                ${HPY_INC}/hpy/runtime/ctx_type.h
                ${HPY_INC}/hpy/runtime/buildvalue.h
                ${HPY_INC}/hpy/runtime/ctx_funcs.h
                ${HPY_INC}/hpy/runtime/structseq.h
                ${HPY_INC}/hpy/runtime/helpers.h
                ${HPY_INC}/hpy/inline_helpers.h
                ${HPY_INC}/hpy/cpython/hpyfunc_trampolines.h
                ${HPY_INC}/hpy/cpython/misc.h
                ${HPY_INC}/hpy/cpython/autogen_api_impl.h
                ${HPY_INC}/hpy/cpython/autogen_hpyfunc_trampolines.h
                ${HPY_INC}/hpy/macros.h
                ${HPY_INC}/hpy/hpymodule.h
                ${HPY_INC}/hpy/autogen_hpyfunc_declare.h
                ${HPY_INC}/hpy/cpy_types.h
                ${HPY_INC}/hpy/hpyfunc.h
                ${HPY_INC}/hpy.h
)

add_library(${TARGET_LIB} SHARED)

target_sources(${TARGET_LIB} PRIVATE ${SRC_FILES} ${HPY_HEADERS})
target_include_directories(${TARGET_LIB} PRIVATE
    "${GRAALVM_HPY_INCLUDE_DIR}"
    "${GRAALVM_PYTHON_INCLUDE_DIR}"
    "${PYCONFIG_INCLUDE_DIR}"
    "${TRUFFLE_H_INC}"
)

add_compile_options(-ffile-prefix-map=${GRAALPY_PARENT_DIR}=.)

target_compile_definitions(${TARGET_LIB} PRIVATE
    HPY_ABI_HYBRID
    NDEBUG
    GRAALVM_PYTHON_LLVM
    Py_BUILD_CORE
)

if(WIN32)
    require_var(GRAALVM_LLVM_LIB_DIR)
    target_link_directories(${TARGET_LIB} PRIVATE ${GRAALVM_LLVM_LIB_DIR})
    target_link_libraries(${TARGET_LIB} PRIVATE graalvm-llvm)
    # Following defines are for 'Python.h'. Since HPy includes it, we need them.
    target_compile_definitions(${TARGET_LIB} PRIVATE
        MS_WINDOWS
        Py_ENABLE_SHARED
        HAVE_DECLSPEC_DLL
    )
endif()

if(MSVC)
    target_compile_options(${TARGET_LIB} PRIVATE /Z7 /O2 /WX)
else()
    if(NOT WIN32)
        target_compile_options(${TARGET_LIB} PRIVATE
            -Werror
        )
    endif()
    target_compile_options(${TARGET_LIB} PRIVATE
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
