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
    if (NOT DEFINED ${var})
        message(FATAL_ERROR "${var} needs to be set")
    endif()
endfunction()

if(MSVC)
    message(FATAL_ERROR "C API cannot be built with MSVC")
endif()

if(WIN32)
    require_var(GRAALVM_LLVM_LIB_DIR)
  endif()
require_var(CAPI_INC_DIR)
require_var(TRUFFLE_H_INC)
require_var(TRUFFLE_NFI_H_INC)
require_var(LLVM_MODE)
require_var(GRAALPY_EXT)

if(NOT DEFINED SRC_DIR)
    set(SRC_DIR "${CMAKE_SOURCE_DIR}")
endif()

set(TARGET_LIBPYTHON "python-${LLVM_MODE}")

######################################################################
# common variables and compile/link options (for all build targets)
######################################################################

set(CFLAGS_WARNINGS -Wno-int-to-pointer-cast -Wno-int-conversion -Wno-void-pointer-to-int-cast
                    -Wno-incompatible-pointer-types-discards-qualifiers -Wno-pointer-type-mismatch
                    -Wno-braced-scalar-init -Wno-deprecated-declarations)

# preprocessor defines for all platforms
add_compile_definitions(
    NDEBUG
    GRAALVM_PYTHON_LLVM
)

if(${LLVM_MODE} STREQUAL "native")
    add_compile_definitions(GRAALVM_PYTHON_LLVM_NATIVE)
else()
    add_compile_definitions(GRAALVM_PYTHON_LLVM_MANAGED)
endif()


if(WIN32)
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

set(CAPI_SRC "${SRC_DIR}/src")

# using glob patterns is not recommended: https://cmake.org/cmake/help/latest/command/file.html#glob
set(SRC_FILES ${CAPI_SRC}/codecs.c ${CAPI_SRC}/setobject.c ${CAPI_SRC}/compile.c ${CAPI_SRC}/thread.c
              ${CAPI_SRC}/moduleobject.c ${CAPI_SRC}/preconfig.c ${CAPI_SRC}/getbuildinfo.c ${CAPI_SRC}/object.c
              ${CAPI_SRC}/dtoa.c ${CAPI_SRC}/pystrhex.c ${CAPI_SRC}/capi.c ${CAPI_SRC}/complexobject.c
              ${CAPI_SRC}/capsule.c ${CAPI_SRC}/typeobject.c ${CAPI_SRC}/obmalloc.c ${CAPI_SRC}/descrobject.c
              ${CAPI_SRC}/memoryobject.c ${CAPI_SRC}/traceback.c ${CAPI_SRC}/unicodeobject.c ${CAPI_SRC}/pythonrun.c
              ${CAPI_SRC}/funcobject.c ${CAPI_SRC}/codeobject.c ${CAPI_SRC}/unicodectype.c ${CAPI_SRC}/structseq.c
              ${CAPI_SRC}/import.c ${CAPI_SRC}/pytime.c ${CAPI_SRC}/bytearrayobject.c ${CAPI_SRC}/listobject.c
              ${CAPI_SRC}/bytesobject.c ${CAPI_SRC}/longobject.c ${CAPI_SRC}/sysmodule.c
              ${CAPI_SRC}/pystrtod.c ${CAPI_SRC}/tupleobject.c ${CAPI_SRC}/iterobject.c ${CAPI_SRC}/sliceobject.c
              ${CAPI_SRC}/classobject.c ${CAPI_SRC}/floatobject.c ${CAPI_SRC}/namespaceobject.c ${CAPI_SRC}/_warnings.c
              ${CAPI_SRC}/dictobject.c ${CAPI_SRC}/pystate.c ${CAPI_SRC}/mysnprintf.c ${CAPI_SRC}/ceval.c
              ${CAPI_SRC}/getcompiler.c ${CAPI_SRC}/pyhash.c ${CAPI_SRC}/fileutils.c
              ${CAPI_SRC}/modsupport.c ${CAPI_SRC}/context.c ${CAPI_SRC}/abstract.c ${CAPI_SRC}/frameobject.c
              ${CAPI_SRC}/posixmodule.c ${CAPI_SRC}/exceptions.c ${CAPI_SRC}/pyctype.c
              ${CAPI_SRC}/mystrtoul.c ${CAPI_SRC}/weakrefobject.c
              ${CAPI_SRC}/modsupport_shared.c ${CAPI_SRC}/fileobject.c ${CAPI_SRC}/pystrcmp.c ${CAPI_SRC}/getversion.c
              ${CAPI_SRC}/genobject.c ${CAPI_SRC}/methodobject.c ${CAPI_SRC}/boolobject.c ${CAPI_SRC}/pylifecycle.c
              ${CAPI_SRC}/errors.c ${CAPI_SRC}/signals.c ${CAPI_SRC}/datetime.c ${CAPI_SRC}/call.c
)

file(GLOB_RECURSE ACTUAL_SRC_FILES
    LIST_DIRECTORIES FALSE
    "${CAPI_SRC}/*.c")


function(list_equals lst0 lst1)
    list(LENGTH ${lst0} len0)
    list(LENGTH ${lst1} len1)
    if(NOT len0 EQUAL len1)
        message(FATAL_ERROR "The list of source files does not match the current file system. "
                            "Different number of files: given len = ${len0} ;; actual len = ${len1}")
    endif()

    # sort lists
    list(SORT ${lst0})
    list(SORT ${lst1})

    # iterate over both lists in simultaneously
    foreach(item IN ZIP_LISTS ${lst0} ${lst1})
        if(NOT ${item_0} STREQUAL ${item_1})
            message(VERBOSE "given src files = ${lst0}")
            message(VERBOSE "actual src files = ${lst1}")
            message(FATAL_ERROR "The list of source files does not match the current file system. "
                                "Different items: given file = ${item_0} ;; actual file = ${item_1}")
        endif()
    endforeach()
endfunction()

list_equals(SRC_FILES ACTUAL_SRC_FILES)

include_directories(
    ${CAPI_SRC}
    "${SRC_DIR}/include"
    "${CAPI_INC_DIR}"
    "${TRUFFLE_H_INC}"
    "${TRUFFLE_NFI_H_INC}"
)

function(native_module name core src_files)
    add_library(${name} SHARED)
    target_compile_options(${name} PRIVATE ${CFLAGS_WARNINGS})
    if(APPLE)
        target_link_options(${name} PRIVATE -undefined dynamic_lookup)
    endif()
    if(${core})
        target_compile_definitions(${name} PRIVATE Py_BUILD_CORE)
        target_include_directories(${name} PRIVATE "${SRC_DIR}/include/internal")
    endif()
    target_compile_definitions(${name} PRIVATE Py_BUILD_CORE_MODULE)
    set_target_properties(${name} PROPERTIES SUFFIX "${GRAALPY_EXT}"
                                             PREFIX "")
    target_sources(${name} PRIVATE ${src_files})
    if(WIN32)
        target_link_directories(${name} PRIVATE ${CMAKE_BINARY_DIR})
        target_link_libraries(${name} PRIVATE ${TARGET_LIBPYTHON})
    endif()
    install(TARGETS ${name} DESTINATION "bin/modules")
endfunction()

function(simple_native_module name)
    native_module(${name} TRUE "${SRC_DIR}/modules/${name}.c")
endfunction()

######################################################################
# BUILD TARGETS
######################################################################

add_library(${TARGET_LIBPYTHON} SHARED)
native_module("_cpython_sre" TRUE "${SRC_DIR}/modules/_cpython_sre/sre.c")
simple_native_module("_cpython_unicodedata")
simple_native_module("_cpython_struct")
set(SQLITE3_SRC
    "${SRC_DIR}/modules/_sqlite/sqlite/sqlite3.c"
    "${SRC_DIR}/modules/_sqlite/blob.c"
    "${SRC_DIR}/modules/_sqlite/connection.c"
    "${SRC_DIR}/modules/_sqlite/cursor.c"
    "${SRC_DIR}/modules/_sqlite/microprotocols.c"
    "${SRC_DIR}/modules/_sqlite/module.c"
    "${SRC_DIR}/modules/_sqlite/prepare_protocol.c"
    "${SRC_DIR}/modules/_sqlite/row.c"
    "${SRC_DIR}/modules/_sqlite/statement.c"
    "${SRC_DIR}/modules/_sqlite/util.c"
)
native_module("_sqlite3" TRUE "${SQLITE3_SRC}")
target_include_directories("_sqlite3" PUBLIC "${SRC_DIR}/modules/_sqlite/sqlite")
native_module("_sha3" TRUE "${SRC_DIR}/modules/_sha3/sha3module.c")

if(NOT WIN32)
    native_module("_testcapi" FALSE "${SRC_DIR}/modules/_testcapi.c")
    simple_native_module("_testbuffer")
    simple_native_module("_testmultiphase")
    simple_native_module("_ctypes_test")

    ###################### BZIP2 ########################
    if(DEFINED LIBBZ2_BUILD_FILE)
        include("${LIBBZ2_BUILD_FILE}")
        set(TARGET_BZ2 "_bz2")
        simple_native_module(${TARGET_BZ2})
        # variable 'BZIP2_SRC' is defined in file 'LIBBZ2_BUILD_FILE'
        target_include_directories(${TARGET_BZ2} PRIVATE ${BZIP2_SRC})
        # variable 'TARGET_LIBBZ2' is defined in file 'LIBBZ2_BUILD_FILE'
        target_link_libraries(${TARGET_BZ2} ${TARGET_LIBBZ2})
    endif()


    ###################### PYEXPAT ######################
    set(TARGET_PYEXPAT "pyexpat")
    simple_native_module(${TARGET_PYEXPAT})
    set(EXPAT_SRC "${SRC_DIR}/expat")
    set(PYEXPAT_HEADERS
        ${EXPAT_SRC}/ascii.h ${EXPAT_SRC}/asciitab.h ${EXPAT_SRC}/expat.h ${EXPAT_SRC}/expat_config.h
        ${EXPAT_SRC}/expat_external.h ${EXPAT_SRC}/internal.h ${EXPAT_SRC}/latin1tab.h ${EXPAT_SRC}/utf8tab.h
        ${EXPAT_SRC}/xmlrole.h ${EXPAT_SRC}/xmltok.h ${EXPAT_SRC}/xmltok_impl.h
    )
    target_sources(${TARGET_PYEXPAT} PRIVATE ${PYEXPAT_HEADERS})
    target_sources(${TARGET_PYEXPAT} PRIVATE ${EXPAT_SRC}/xmlparse.c ${EXPAT_SRC}/xmlrole.c ${EXPAT_SRC}/xmltok.c)
    target_include_directories(${TARGET_PYEXPAT} PRIVATE ${EXPAT_SRC})
    # bpo-30947: Python uses best available entropy sources to call XML_SetHashSalt(),
    # expat entropy sources are not needed
    target_compile_definitions(${TARGET_PYEXPAT} PRIVATE
        HAVE_EXPAT_CONFIG_H=1
        XML_POOR_ENTROPY=1
    )
endif()

target_sources(${TARGET_LIBPYTHON} PRIVATE ${SRC_FILES})
target_include_directories(${TARGET_LIBPYTHON} PRIVATE
    "${SRC_DIR}/include/internal"
)

######################################################################
# target-specific compile and link options
######################################################################

target_compile_definitions(${TARGET_LIBPYTHON} PRIVATE Py_BUILD_CORE)
target_compile_options(${TARGET_LIBPYTHON} PRIVATE ${CFLAGS_WARNINGS})

if(WIN32)
    target_link_directories(${TARGET_LIBPYTHON} PRIVATE ${GRAALVM_LLVM_LIB_DIR})
    target_link_libraries(${TARGET_LIBPYTHON} sulong-native graalvm-llvm)
else()
    # Link to math library; required for functions like 'hypot' or similar
    target_link_libraries(${TARGET_LIBPYTHON} m)
endif()

install(TARGETS ${TARGET_LIBPYTHON} DESTINATION bin)
