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
project(com.oracle.graal.python.cext)

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
require_var(BZ2_LIB_DIR)

set(TARGET_LIBPYTHON "python")
set(LIB_OUTPUT_DIR "${PROJECT_OUTPUT_DIR}/lib")

######################################################################
# common variables and compile/link options (for all build targets)
######################################################################

if(NOT WIN32)
    set(CFLAGS_WARNINGS -Werror -Wno-int-to-pointer-cast -Wno-int-conversion -Wno-void-pointer-to-int-cast
                        -Wno-incompatible-pointer-types-discards-qualifiers -Wno-pointer-type-mismatch
                        -Wno-braced-scalar-init -Wno-deprecated-declarations)
else()
    set(CFLAGS_WARNINGS)
endif()

# preprocessor defines for all platforms
add_compile_definitions(
    NDEBUG
    GRAALVM_PYTHON_LLVM
    Py_BUILD_CORE_MODULE
)

if(WIN32)
    add_compile_options(
        /Z7
        /O2
        /WX
    )
    add_compile_definitions(
        MS_WINDOWS
        Py_ENABLE_SHARED
        HAVE_DECLSPEC_DLL
    )
endif()

if(APPLE)
    add_link_options(-undefined dynamic_lookup)
endif()

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

check_var(GRAALVM_LLVM_LIB_DIR)
check_var(TRUFFLE_H_INC)

require_var(GRAALPY_EXT)

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
set(SRC_FILES src/codecs.c src/setobject.c src/compile.c src/thread.c src/moduleobject.c src/preconfig.c
              src/getbuildinfo.c src/object.c src/dtoa.c src/pystrhex.c src/capi.c src/complexobject.c src/capsule.c
              src/typeobject.c src/obmalloc.c src/descrobject.c src/memoryobject.c src/traceback.c src/unicodeobject.c
              src/pythonrun.c src/funcobject.c src/codeobject.c src/unicodectype.c src/structseq.c src/import.c
              src/pytime.c src/bytearrayobject.c src/listobject.c src/bytesobject.c src/object_shared.c src/longobject.c
              src/sysmodule.c src/pystrtod.c src/tupleobject.c src/iterobject.c src/sliceobject.c src/classobject.c
              src/floatobject.c src/namespaceobject.c src/_warnings.c src/dictobject.c src/pystate.c src/mysnprintf.c
              src/ceval.c src/getcompiler.c src/pyhash.c src/fileutils.c src/descrobject_shared.c src/modsupport.c
              src/context.c src/abstract.c src/frameobject.c src/posixmodule.c src/longobject_shared.c src/exceptions.c
              src/pyctype.c src/typeobject_shared.c src/mystrtoul.c src/weakrefobject.c src/modsupport_shared.c
              src/fileobject.c src/pystrcmp.c src/getversion.c src/genobject.c src/methodobject.c src/boolobject.c
              src/pylifecycle.c src/errors.c src/signals.c src/datetime.c
)

include_directories(
    "src"
    "include"
    "${TRUFFLE_H_INC}"
)

function(native_module name core src_files)
    add_library(${name} SHARED)
    target_compile_options(${name} PRIVATE ${CFLAGS_WARNINGS})
    if(APPLE)
        target_link_options(${name} PRIVATE -undefined dynamic_lookup)
    endif()
    if(${core})
        target_compile_definitions(${name} PRIVATE Py_BUILD_CORE)
        target_include_directories(${name} PRIVATE "include/internal")
    endif()
    set_target_properties(${name} PROPERTIES SUFFIX "${GRAALPY_EXT}")
    set_target_properties(${name} PROPERTIES PREFIX "")
    target_sources(${name} PRIVATE ${src_files})
    install(TARGETS ${name} DESTINATION "bin/modules")
endfunction()

function(simple_native_module name)
    native_module(${name} TRUE "modules/${name}.c")
endfunction()

#file(GLOB_RECURSE GLOBBED_SRC_FILES
#    LIST_DIRECTORIES FALSE
#    "src/*.c")

######################################################################
# BUILD TARGETS
######################################################################

add_library(${TARGET_LIBPYTHON} SHARED)
simple_native_module("_mmap")
simple_native_module("_cpython_sre")
simple_native_module("_cpython_unicodedata")
simple_native_module("_cpython_struct")

if(NOT WIN32)
    native_module("_testcapi" FALSE "modules/_testcapi.c")
    simple_native_module("_testmultiphase")
    simple_native_module("_ctypes_test")

    ###################### BZIP2 ########################
    set(TARGET_BZ2 "_bz2")
    simple_native_module(${TARGET_BZ2})
    set_target_properties(${TARGET_BZ2} PROPERTIES
        # BUILD_WITH_INSTALL_RPATH FALSE
        # BUILD_RPATH_USE_ORIGIN TRUE
        BUILD_RPATH "../lib/${LLVM_MODE}/"
    )
    target_link_directories(${TARGET_BZ2} PRIVATE "${BZ2_LIB_DIR}")
    target_link_libraries(${TARGET_BZ2} bz2)


    ###################### PYEXPAT ######################
    set(TARGET_PYEXPAT "pyexpat")
    simple_native_module(${TARGET_PYEXPAT})
    set(PYEXPAT_HEADERS
        "expat/ascii.h" "expat/asciitab.h" "expat/expat.h" "expat/expat_config.h" "expat/expat_external.h"
        "expat/internal.h" "expat/latin1tab.h" "expat/utf8tab.h" "expat/xmlrole.h" "expat/xmltok.h"
        "expat/xmltok_impl.h"
    )
    target_sources(${TARGET_PYEXPAT} PRIVATE ${PYEXPAT_HEADERS})
    target_sources(${TARGET_PYEXPAT} PRIVATE "expat/xmlparse.c" "expat/xmlrole.c" "expat/xmltok.c")
    target_include_directories(${TARGET_PYEXPAT} PRIVATE "expat")
    # bpo-30947: Python uses best available entropy sources to call XML_SetHashSalt(),
    # expat entropy sources are not needed
    target_compile_definitions(${TARGET_PYEXPAT} PRIVATE
        HAVE_EXPAT_CONFIG_H=1
        XML_POOR_ENTROPY=1
    )
endif()

# set file extension; e.g. "graalpy231-310-native-x86_64-darwin.so"
set_target_properties(${TARGET_LIBPYTHON} PROPERTIES SUFFIX "${GRAALPY_EXT}")

target_sources(${TARGET_LIBPYTHON} PRIVATE ${SRC_FILES})
target_include_directories(${TARGET_LIBPYTHON} PRIVATE
    "include/internal"
)

######################################################################
# target-specific compile and link options
######################################################################

#target_link_directories(${TARGET_LIBPYTHON} PRIVATE ${GRAALVM_LLVM_LIB_DIR})
#target_link_libraries(${TARGET_LIBPYTHON} graalvm-llvm)
target_compile_options(${TARGET_LIBPYTHON} PRIVATE ${CFLAGS_WARNINGS})

if(WIN32)
    target_link_libraries(${TARGET_LIBPYTHON} sulong-native)
endif()

#if(APPLE)
#    target_link_options(${TARGET_LIBPYTHON} PRIVATE -undefined dynamic_lookup)
#endif()

install(TARGETS ${TARGET_LIBPYTHON} DESTINATION bin)
