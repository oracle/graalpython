#
# Copyright (c) 2026, Oracle and/or its affiliates.
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
project(graalpy-graalos-standalone-payload LANGUAGES NONE)

foreach(required_var
        GRAALPY_LAUNCHER
        PYTHON_LAUNCHER
        PYTHON3_LAUNCHER
        GRAALPY_CONFIG_LAUNCHER
        GRAALPY_POLYGLOT_GET_LAUNCHER
        GRAALPY_SUITE_PARENT)
    if(NOT DEFINED ${required_var})
        message(FATAL_ERROR "${required_var} needs to be set")
    endif()
endforeach()

set(PAYLOAD_DIR "${CMAKE_BINARY_DIR}")
set(GRAALOS_DIR "${PAYLOAD_DIR}/lib/graalos")
set(SYSROOT_LIB_DIR "${GRAALOS_DIR}/sysroot/lib")

file(REMOVE_RECURSE
    "${PAYLOAD_DIR}/bin"
    "${PAYLOAD_DIR}/libexec"
    "${PAYLOAD_DIR}/lib"
    "${PAYLOAD_DIR}/config.json")
file(MAKE_DIRECTORY
    "${PAYLOAD_DIR}/bin"
    "${PAYLOAD_DIR}/libexec"
    "${GRAALOS_DIR}"
    "${SYSROOT_LIB_DIR}")

function(_append_env_path out_var env_name)
    if(DEFINED ENV{${env_name}} AND NOT "$ENV{${env_name}}" STREQUAL "")
        list(APPEND ${out_var} "$ENV{${env_name}}")
        set(${out_var} "${${out_var}}" PARENT_SCOPE)
    endif()
endfunction()

function(_find_graalos_runtime out_var)
    set(candidates)
    _append_env_path(candidates GRAALOS_RUNTIME_HOME)
    _append_env_path(candidates GRAALOS_HOME)
    _append_env_path(candidates GRAALOS_BUILD_HOME)
    list(APPEND candidates
        "${GRAALPY_SUITE_PARENT}/graalos/cmake-build-clang-pkeysoff"
        "${GRAALPY_SUITE_PARENT}/graalos/build")

    foreach(candidate IN LISTS candidates)
        if(EXISTS "${candidate}/graalhost/graalhost"
                AND EXISTS "${candidate}/graalhost/libc.so"
                AND EXISTS "${candidate}/graalhost/libbinsweep.so")
            set(${out_var} "${candidate}" PARENT_SCOPE)
            return()
        endif()
    endforeach()

    string(REPLACE ";" "\n" candidate_text "${candidates}")
    message(FATAL_ERROR
        "Could not find GraalOS graalhost, safe libc.so, and libbinsweep.so. Checked:\n${candidate_text}")
endfunction()

function(_find_graalos_sysroot_lib_dir out_var)
    set(candidates)
    if(DEFINED ENV{MUSL_TOOLCHAIN} AND NOT "$ENV{MUSL_TOOLCHAIN}" STREQUAL "")
        list(APPEND candidates "$ENV{MUSL_TOOLCHAIN}/lib")
    endif()
    if(DEFINED ENV{GRAALOS_TOOLCHAIN_PATH} AND NOT "$ENV{GRAALOS_TOOLCHAIN_PATH}" STREQUAL "")
        list(APPEND candidates "$ENV{GRAALOS_TOOLCHAIN_PATH}/lib")
    endif()
    if(DEFINED ENV{JAVA_HOME} AND NOT "$ENV{JAVA_HOME}" STREQUAL "")
        list(APPEND candidates
            "$ENV{JAVA_HOME}/lib/toolchains/musl-swcfi/lib"
            "$ENV{JAVA_HOME}/lib/toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib")
    endif()
    file(GLOB graalos_prebuilt_musl_lib_dirs
        LIST_DIRECTORIES TRUE
        "${GRAALPY_SUITE_PARENT}/graalos/build-tools/prebuilt/graalvm-graalos-*/lib/toolchains/musl-swcfi/lib")
    file(GLOB graalos_prebuilt_sysroot_lib_dirs
        LIST_DIRECTORIES TRUE
        "${GRAALPY_SUITE_PARENT}/graalos/build-tools/prebuilt/graalvm-graalos-*/lib/toolchains/sysroot/x86_64-unknown-linux-musl_swcfi/lib")
    list(APPEND candidates
        ${graalos_prebuilt_musl_lib_dirs}
        ${graalos_prebuilt_sysroot_lib_dirs})

    set(required_libs
        libc++.so
        libc++.so.1
        libc++.so.1.0
        libc++abi.so
        libc++abi.so.1
        libc++abi.so.1.0
        libunwind.so
        libunwind.so.1
        libunwind.so.1.0)

    foreach(candidate IN LISTS candidates)
        set(found TRUE)
        foreach(lib IN LISTS required_libs)
            if(NOT EXISTS "${candidate}/${lib}")
                set(found FALSE)
                break()
            endif()
        endforeach()
        if(found)
            set(${out_var} "${candidate}" PARENT_SCOPE)
            return()
        endif()
    endforeach()

    string(REPLACE ";" "\n" candidate_text "${candidates}")
    message(FATAL_ERROR "Could not find required GraalOS musl-swcfi runtime libraries. Checked:\n${candidate_text}")
endfunction()

function(_copy_executable source target)
    configure_file("${source}" "${target}" COPYONLY)
    file(CHMOD "${target}"
        PERMISSIONS
            OWNER_READ OWNER_WRITE OWNER_EXECUTE
            GROUP_READ GROUP_EXECUTE
            WORLD_READ WORLD_EXECUTE)
endfunction()

function(_copy_file source target)
    configure_file("${source}" "${target}" COPYONLY)
endfunction()

function(_write_launcher target virtual_executable)
    file(READ "${CMAKE_CURRENT_LIST_DIR}/launcher-wrapper.sh" wrapper_content)
    string(REPLACE "@GRAALPY_SANDBOX_VIRTUAL_EXECUTABLE@" "${virtual_executable}" wrapper_content "${wrapper_content}")
    file(WRITE "${target}" "${wrapper_content}")
    file(CHMOD "${target}"
        PERMISSIONS
            OWNER_READ OWNER_WRITE OWNER_EXECUTE
            GROUP_READ GROUP_EXECUTE
            WORLD_READ WORLD_EXECUTE)
endfunction()

_write_launcher("${PAYLOAD_DIR}/bin/${GRAALPY_LAUNCHER}" "/bin/graalpy")
_write_launcher("${PAYLOAD_DIR}/bin/${PYTHON_LAUNCHER}" "/bin/python")
_write_launcher("${PAYLOAD_DIR}/bin/${PYTHON3_LAUNCHER}" "/bin/python3")
_write_launcher("${PAYLOAD_DIR}/bin/${GRAALPY_CONFIG_LAUNCHER}" "/bin/graalpy-config")
_write_launcher("${PAYLOAD_DIR}/libexec/${GRAALPY_POLYGLOT_GET_LAUNCHER}" "/libexec/graalpy-polyglot-get")

_copy_file("${CMAKE_CURRENT_LIST_DIR}/config.json" "${PAYLOAD_DIR}/config.json")
_copy_executable("${CMAKE_CURRENT_LIST_DIR}/graalpy-sandbox-launcher.sh" "${GRAALOS_DIR}/graalpy-sandbox-launcher")
_copy_executable(
    "${CMAKE_CURRENT_LIST_DIR}/graalpy-sandbox-expand-config.sh"
    "${GRAALOS_DIR}/graalpy-sandbox-expand-config")
_copy_executable("${CMAKE_CURRENT_LIST_DIR}/graalpy-sandbox-fsmappings.sh" "${GRAALOS_DIR}/graalpy-sandbox-fsmappings")

_find_graalos_runtime(graalos_runtime)
_copy_executable("${graalos_runtime}/graalhost/graalhost" "${GRAALOS_DIR}/graalhost")
_copy_file("${graalos_runtime}/graalhost/libc.so" "${GRAALOS_DIR}/libc.so")
_copy_file("${graalos_runtime}/graalhost/libbinsweep.so" "${GRAALOS_DIR}/libbinsweep.so")

_find_graalos_sysroot_lib_dir(graalos_sysroot_lib_dir)
foreach(lib
        libc++.so
        libc++.so.1
        libc++.so.1.0
        libc++abi.so
        libc++abi.so.1
        libc++abi.so.1.0
        libunwind.so
        libunwind.so.1
        libunwind.so.1.0)
    file(COPY "${graalos_sysroot_lib_dir}/${lib}" DESTINATION "${SYSROOT_LIB_DIR}")
endforeach()
