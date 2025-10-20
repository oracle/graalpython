#
# Copyright (c) 2023, 2025, Oracle and/or its affiliates.
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

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
add_custom_target(compile_commands_target ALL
  COMMAND ${CMAKE_COMMAND} -E copy "${CMAKE_BINARY_DIR}/compile_commands.json" "${CMAKE_SOURCE_DIR}")

require_var(GRAALPY_PARENT_DIR)
require_var(CAPI_INC_DIR)
require_var(PYCONFIG_INCLUDE_DIR)
require_var(TRUFFLE_NFI_H_INC)
require_var(GRAALPY_EXT)

if(NOT DEFINED SRC_DIR)
    set(SRC_DIR "${CMAKE_SOURCE_DIR}")
endif()

set(TARGET_LIBPYTHON "python-native")

######################################################################
# common variables and compile/link options (for all build targets)
######################################################################

if (MSVC)
  SET(CMAKE_BUILD_TYPE "RelWithDebInfo" CACHE STRING "" FORCE)
  SET(CFLAGS_WARNINGS /Wall /WX
    # Many warnings that are just MSVC being a bit pedantic
    /wd4255 # no function prototype given: converting '()' to '(void)'
    /wd4820 # padding added after data member
    /wd4100 # unreferenced formal parameter
    /wd4200 # nonstandard extension used: zero-sized array in struct/union
    /wd4996 # This function or variable may be unsafe ... see _CRT_SECURE_NO_WARNINGS
    /wd4668 # ... is not defined as a preprocessor macro, replacing with '0' for '#if/#elif'
    /wd4115 # named type definition in parentheses
    /wd4152 # nonstandard extension, function/data pointer conversion in expression
    /wd5045 # Compiler will insert Spectre mitigation for memory load if /Qspectre switch specified
    /wd4047 # 'int64_t' differs in levels of indirection from 'char []' / 'void *'
    /wd4242 # conversion from 'int' to 'char', possible loss of data
    /wd4244 # conversion from 'long' to 'char', possible loss of data
    /wd4267 # conversion from 'size_t' to 'int', possible loss of data
    /wd4127 # conditional expression is constant
    /wd4702 # unreachable code
    /wd4101 # unreferenced local variable
    /wd4456 # declaration hides previous local declaration
    /wd4459 # declaration hides global declaration
    /wd4061 # enumerator X in switch of enum Y is not explicitly handled by a case label
    /wd4464 # relative include path contains '..'
    /wd4710 # 'fprintf': function not inlined
    /wd4706 # assignment within conditional expression
    /wd4774 # 'sprintf': format string expected in argument 2 is not a string literal
    /wd4191 # unsafe conversion from 'PyObject *(__cdecl *)(MatchObject *,PyObject *const *,Py_ssize_t)' to 'void (__cdecl *)(void)'
    /wd4574 # sqlite, expat: 'SQLITE_ATOMIC_INTRINSICS' is defined to be '0': did you mean to use '#if SQLITE_ATOMIC_INTRINSICS'?
    /wd4701 # potentially uninitialized local variable used

    # Some that I'm not so happy about
    /wd4232 # sre: nonstandard extension used: 'ml_meth': address of dllimport 'Py_GenericAlias' is not static, identity not guaranteed
    /wd4918 # sre: invalid character in pragma optimization list
    /wd4703 # unicodeobject.c:potentially uninitialized local pointer variable used
    /wd4310 # xmlparse: cast truncates constant value
    /wd4777 # format string '%zd' requires an argument of type 'unsigned __int64'
  )
else()
  set(CFLAGS_WARNINGS -Wall -Werror -Wno-unused-function -Wno-unused-variable -Wno-unused-const-variable
                    -Wno-unknown-warning-option
                    -Wno-discarded-qualifiers                         # _testbuffer.c
                    -Wno-tautological-constant-out-of-range-compare   # fileutils.c: wchar_t > MAX_UNICODE is always false on Windows
                    -Wno-unused-but-set-variable                      # sqlite.c: BOOL bRc
                    -Wno-ignored-pragmas                              # sre.c: #pragma optimize("agtw", on)
                    -Wno-int-to-pointer-cast -Wno-int-conversion -Wno-void-pointer-to-int-cast
                    -Wno-incompatible-pointer-types-discards-qualifiers
                    -Wno-braced-scalar-init -Wno-deprecated-declarations)
  add_compile_options(-ffile-prefix-map=${GRAALPY_PARENT_DIR}=.)
endif()


# preprocessor defines for all platforms
add_compile_definitions(
    NDEBUG
    WITH_FREELISTS=1
)


if(WIN32)
    add_compile_definitions(
        MS_WINDOWS
        Py_ENABLE_SHARED
        HAVE_DECLSPEC_DLL
        NTDDI_VERSION=NTDDI_WINBLUE
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
              ${CAPI_SRC}/mystrtoul.c ${CAPI_SRC}/weakrefobject.c ${CAPI_SRC}/gcmodule.c
              ${CAPI_SRC}/fileobject.c ${CAPI_SRC}/pystrcmp.c ${CAPI_SRC}/getversion.c
              ${CAPI_SRC}/genobject.c ${CAPI_SRC}/methodobject.c ${CAPI_SRC}/boolobject.c ${CAPI_SRC}/pylifecycle.c
              ${CAPI_SRC}/errors.c ${CAPI_SRC}/signals.c ${CAPI_SRC}/datetime.c ${CAPI_SRC}/call.c
              ${CAPI_SRC}/getargs.c ${CAPI_SRC}/tracemalloc.c ${CAPI_SRC}/initconfig.c
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
    "${PYCONFIG_INCLUDE_DIR}"
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
if(NOT WIN32)
    simple_native_module("termios")
endif()
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
# This combines the flags CPython uses on macOS and Windows, on Linux systems
# it usually links against the system sqlite3 so we do not really know. See
# https://github.com/python/cpython/issues/88017 for some reasons
target_include_directories("_sqlite3" PUBLIC "${SRC_DIR}/modules/_sqlite/sqlite")
target_compile_definitions("_sqlite3" PRIVATE
    SQLITE_ENABLE_MATH_FUNCTIONS
    SQLITE_ENABLE_FTS5
    SQLITE_ENABLE_FTS4
    SQLITE_ENABLE_FTS3_PARENTHESIS
    SQLITE_ENABLE_RTREE
    SQLITE_OMIT_AUTOINIT
    SQLITE_TCL=0
)

set(LIBHACL_HEADERS
    # "${SRC_DIR}/modules/_hacl/include/krml/FStar_UInt128_Verified.h"
    # "${SRC_DIR}/modules/_hacl/include/krml/FStar_UInt_8_16_32_64.h"
    # "${SRC_DIR}/modules/_hacl/include/krml/fstar_uint128_struct_endianness.h"
    # "${SRC_DIR}/modules/_hacl/include/krml/internal/target.h"
    # "${SRC_DIR}/modules/_hacl/include/krml/lowstar_endianness.h"
    # "${SRC_DIR}/modules/_hacl/include/krml/types.h"
    # "${SRC_DIR}/modules/_hacl/Hacl_Streaming_Types.h"
    # "${SRC_DIR}/modules/_hacl/python_hacl_namespaces.h"
    "${SRC_DIR}/modules/_hacl/include"
    "${SRC_DIR}/modules/_hacl"
)
set(SHA3_SRC
    "${SRC_DIR}/modules/sha3module.c"
    "${SRC_DIR}/modules/_hacl/Hacl_Hash_SHA3.c"
)
native_module("_sha3" TRUE "${SHA3_SRC}")
target_include_directories("_sha3" PUBLIC "${LIBHACL_HEADERS}")

set(TESTCAPI_SRC
    "${SRC_DIR}/modules/_testcapi/abstract.c"
    "${SRC_DIR}/modules/_testcapi/buffer.c"
    "${SRC_DIR}/modules/_testcapi/bytearray.c"
    "${SRC_DIR}/modules/_testcapi/bytes.c"
    # "${SRC_DIR}/modules/_testcapi/code.c"
    "${SRC_DIR}/modules/_testcapi/codec.c"
    "${SRC_DIR}/modules/_testcapi/complex.c"
    "${SRC_DIR}/modules/_testcapi/datetime.c"
    "${SRC_DIR}/modules/_testcapi/dict.c"
    "${SRC_DIR}/modules/_testcapi/docstring.c"
    "${SRC_DIR}/modules/_testcapi/exceptions.c"
    "${SRC_DIR}/modules/_testcapi/file.c"
    "${SRC_DIR}/modules/_testcapi/float.c"
    "${SRC_DIR}/modules/_testcapi/gc.c"
    "${SRC_DIR}/modules/_testcapi/getargs.c"
    "${SRC_DIR}/modules/_testcapi/heaptype.c"
    # "${SRC_DIR}/modules/_testcapi/heaptype_relative.c"
    "${SRC_DIR}/modules/_testcapi/immortal.c"
    "${SRC_DIR}/modules/_testcapi/list.c"
    "${SRC_DIR}/modules/_testcapi/long.c"
    "${SRC_DIR}/modules/_testcapi/mem.c"
    "${SRC_DIR}/modules/_testcapi/numbers.c"
    "${SRC_DIR}/modules/_testcapi/parts.h"
    "${SRC_DIR}/modules/_testcapi/pyos.c"
    "${SRC_DIR}/modules/_testcapi/pytime.c"
    "${SRC_DIR}/modules/_testcapi/run.c"
    "${SRC_DIR}/modules/_testcapi/set.c"
    "${SRC_DIR}/modules/_testcapi/structmember.c"
    "${SRC_DIR}/modules/_testcapi/sys.c"
    "${SRC_DIR}/modules/_testcapi/testcapi_long.h"
    "${SRC_DIR}/modules/_testcapi/tuple.c"
    "${SRC_DIR}/modules/_testcapi/unicode.c"
    "${SRC_DIR}/modules/_testcapi/util.h"
    # "${SRC_DIR}/modules/_testcapi/vectorcall.c"
    # "${SRC_DIR}/modules/_testcapi/vectorcall_limited.c"
    # "${SRC_DIR}/modules/_testcapi/watchers.c"
    "${SRC_DIR}/modules/_testcapi.c"
)

native_module("_testcapi" FALSE "${TESTCAPI_SRC}")
if(WIN32)
    target_compile_options("_testcapi" PRIVATE /wd4296 /wd4130)
endif()
simple_native_module("_testbuffer")
if(WIN32)
    target_compile_options("_testbuffer" PRIVATE /wd4090)
endif()
simple_native_module("_testmultiphase")
simple_native_module("_testsinglephase")

if(NOT WIN32)
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
    XML_DTD=1
)

###################### CTYPES ######################
native_module("_ctypes_test" TRUE "${SRC_DIR}/modules/_ctypes/_ctypes_test.c")
set(CTYPES_SRC
    "${SRC_DIR}/modules/_ctypes/_ctypes.c"
    "${SRC_DIR}/modules/_ctypes/callbacks.c"
    "${SRC_DIR}/modules/_ctypes/callproc.c"
    "${SRC_DIR}/modules/_ctypes/cfield.c"
    "${SRC_DIR}/modules/_ctypes/ctypes.h"
    "${SRC_DIR}/modules/_ctypes/stgdict.c"
)
native_module("_ctypes" TRUE "${CTYPES_SRC}")
target_include_directories("_ctypes" PUBLIC "${SRC_DIR}/modules/_ctypes")
find_library(FFI_LIBRARY
  NAMES "ffi.lib" "libffi.a"
  PATHS "${LIBFFI_DIST}"
  REQUIRED
  NO_DEFAULT_PATH
  NO_PACKAGE_ROOT_PATH
  NO_CMAKE_PATH
  NO_CMAKE_ENVIRONMENT_PATH
  NO_SYSTEM_ENVIRONMENT_PATH
  NO_CMAKE_SYSTEM_PATH
  NO_CMAKE_INSTALL_PREFIX
)
find_path(FFI_INCLUDE
  NAMES "ffi.h"
  PATHS "${LIBFFI_DIST}"
  PATH_SUFFIXES "include"
  REQUIRED
  NO_DEFAULT_PATH
  NO_PACKAGE_ROOT_PATH
  NO_CMAKE_PATH
  NO_CMAKE_ENVIRONMENT_PATH
  NO_SYSTEM_ENVIRONMENT_PATH
  NO_CMAKE_SYSTEM_PATH
  NO_CMAKE_INSTALL_PREFIX
)
target_link_libraries("_ctypes" PRIVATE ${FFI_LIBRARY})

target_include_directories("_ctypes" BEFORE PRIVATE ${FFI_INCLUDE})

if(WIN32)
    target_compile_options("_ctypes" PRIVATE
        /wd4201 # ffi.h(332): warning C4201: nonstandard extension used: nameless struct/union
        /wd4457 # _ctypes.c(4783): warning C4457: declaration of 'item' hides function parameter
    )
    target_compile_definitions("_ctypes" PRIVATE
        MS_WIN32
        MS_WIN64
        _WIN64
        FFI_STATIC_BUILD
    )
elseif(APPLE)
    target_compile_definitions("_ctypes" PRIVATE
        HAVE_DECL_RTLD_LOCAL
        HAVE_DECL_RTLD_GLOBAL
        HAVE_DYLD_SHARED_CACHE_CONTAINS_PATH
        HAVE_BUILTIN_AVAILABLE
    )
else()
    target_compile_definitions("_ctypes" PRIVATE
        HAVE_DECL_RTLD_LOCAL
        HAVE_DECL_RTLD_GLOBAL
    )
endif()

###################### LIBPYTHON ######################
target_sources(${TARGET_LIBPYTHON} PRIVATE ${SRC_FILES})
target_include_directories(${TARGET_LIBPYTHON} PRIVATE
    "${SRC_DIR}/include/internal"
)

######################################################################
# target-specific compile and link options
######################################################################

target_compile_definitions(${TARGET_LIBPYTHON} PRIVATE Py_BUILD_CORE Py_BUILD_CORE_BUILTIN)
target_compile_options(${TARGET_LIBPYTHON} PRIVATE ${CFLAGS_WARNINGS})

if(WIN32)
    if (NOT MSVC)
        target_compile_options(${TARGET_LIBPYTHON} PRIVATE "-fmsc-version=1920")
    endif()
else()
    # Link to math library; required for functions like 'hypot' or similar
    target_link_libraries(${TARGET_LIBPYTHON} m)
endif()

install(TARGETS ${TARGET_LIBPYTHON} DESTINATION bin)
