cmake_minimum_required(VERSION 3.22)
project(com.oracle.graal.python.c_embed LANGUAGES C DESCRIPTION "GraalPython C Embedding API")

set(CMAKE_C_STANDARD 11)
set(LIB_NAME "graalpython-embed")

# don't install into the system but into the MX project's output dir
set(CMAKE_INSTALL_PREFIX ${CMAKE_BINARY_DIR})

function(require_var var)
    if (NOT DEFINED ${var})
        message(FATAL_ERROR "${var} needs to be set")
    endif()
endfunction()

require_var(CEXT_H_INC)
#
# The statically linked graal native library for graalpy
# e.g. libpythonvm.dylib
# and the header files for graal_isolate etc.
require_var(RUNTIME_LIB_DIR)

#
# e.g. libpython-native.dylib
# should be statically linked too to expose static symbols
require_var(PYTHON_NATIVE_LIB)

message("Using graalpython runtime lib dir: ${RUNTIME_LIB_DIR}")
message("Using graalpython native lib: ${PYTHON_NATIVE_LIB}")

add_library(pythonvm STATIC IMPORTED)
set_target_properties(pythonvm PROPERTIES IMPORTED_LOCATION "${RUNTIME_LIB_DIR}/libpythonvm.dylib")

#
# depend on libpython-native
add_library(python-native STATIC IMPORTED)
set_target_properties(python-native PROPERTIES IMPORTED_LOCATION "${PYTHON_NATIVE_LIB}")

set(SRC_FILES src/graalpython-embed.c)

add_library(${LIB_NAME} SHARED ${SRC_FILES})

target_include_directories(${LIB_NAME} PUBLIC include)
target_include_directories(${LIB_NAME} PRIVATE "${RUNTIME_LIB_DIR}")

target_link_libraries(${LIB_NAME} pythonvm)
target_link_libraries(${LIB_NAME} python-native)

#
# for pyport.h and exports.h
target_include_directories(${LIB_NAME} PRIVATE "${CEXT_H_INC}")

install(TARGETS ${LIB_NAME} DESTINATION bin)

message("Created lib in: ${CMAKE_INSTALL_PREFIX}")