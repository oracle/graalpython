project(pegparser)
cmake_minimum_required(VERSION 3.0)

set(PEGPARSER_SRC_PATH "${CMAKE_CURRENT_LIST_DIR}/../com.oracle.graal.python.pegparser/src")
set(GRAALPY_SRC_PATH "${CMAKE_CURRENT_LIST_DIR}/../com.oracle.graal.python/src")
set(PARSER_PATH "${PEGPARSER_SRC_PATH}/com/oracle/graal/python/pegparser")
set(INPUT_PATH "${CMAKE_CURRENT_LIST_DIR}/input_files")

if(DEFINED ENV{MX_PYTHON})
    set(PYTHON_EXE "$ENV{MX_PYTHON}")
elseif(DEFINED ENV{MX_PYTHON_VERSION})
    set(PYTHON_EXE "python$ENV{MX_PYTHON_VERSION}")
else()
    set(PYTHON_EXE "python3")
endif()

set(PARSER_TARGET "${PARSER_PATH}/Parser.java")

set(GRAMMAR "${INPUT_PATH}/python.gram")
set(TOKENS "${INPUT_PATH}/Tokens")
set(ASDL "${INPUT_PATH}/Python.asdl")

file(GLOB_RECURSE PEGEN_FILES
    LIST_DIRECTORIES TRUE
    "${CMAKE_CURRENT_LIST_DIR}/pegen/*.py")
file(GLOB_RECURSE PEGJAVA_FILES
    LIST_DIRECTORIES TRUE
    "${CMAKE_CURRENT_LIST_DIR}/pegen/*.py")
file(GLOB_RECURSE ASDL_FILES
    LIST_DIRECTORIES TRUE
    "${CMAKE_CURRENT_LIST_DIR}/asdl/*.py")

set(GRAMMAR_STAMP "${GRAMMAR}.stamp")
set(ASDL_STAMP "${ASDL}.stamp")

add_custom_target(grammar ALL
    ${PYTHON_EXE} "${CMAKE_CURRENT_LIST_DIR}/main_parser_gen.py" "${GRAMMAR}" "${TOKENS}" "${PARSER_TARGET}"
    COMMAND ${CMAKE_COMMAND} -E touch "${GRAMMAR_STAMP}"
    BYPRODUCTS "${GRAMMAR_STAMP}"
    DEPENDS "${CMAKE_CURRENT_LIST_DIR}/main_parser_gen.py" "${GRAMMAR}" "${TOKENS}" "${PARSER_TARGET}" ${PEGEN_FILES} ${PEGJAVA_FILES})

add_custom_target(asdl ALL
    ${PYTHON_EXE} "${CMAKE_CURRENT_LIST_DIR}/main_asdl_gen.py" "${ASDL}" --sst-path "${PEGPARSER_SRC_PATH}" --ast-path "${GRAALPY_SRC_PATH}"
    COMMAND ${CMAKE_COMMAND} -E touch "${ASDL_STAMP}"
    BYPRODUCTS "${ASDL_STAMP}"
    DEPENDS "${CMAKE_CURRENT_LIST_DIR}/main_asdl_gen.py" "${ASDL}" ${ASDL_FILES})
