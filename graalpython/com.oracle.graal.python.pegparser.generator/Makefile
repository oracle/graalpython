QUIETLY$(MX_VERBOSE) = @

PEGPARSER_SRC_PATH ?= ../com.oracle.graal.python.pegparser/src
GRAALPY_SRC_PATH ?= ../com.oracle.graal.python/src
PARSER_PATH ?= ${PEGPARSER_SRC_PATH}/com/oracle/graal/python/pegparser
INPUT_PATH ?= input_files
ifdef MX_PYTHON
PYTHON_EXE ?= ${MX_PYTHON}
else ifdef MX_PYTHON_VERSION
PYTHON_EXE ?= python${MX_PYTHON_VERSION}
else
PYTHON_EXE ?= python3
endif

PARSER_TARGET=${PARSER_PATH}/Parser.java

GRAMMAR=${INPUT_PATH}/python.gram
TOKENS=${INPUT_PATH}/Tokens
ASDL=${INPUT_PATH}/Python.asdl

PEGEN_FILES=$(shell find pegen pegjava -name '*.py')
ASDL_FILES=$(shell find asdl -name '*.py')

GRAMMAR_STAMP=${GRAMMAR}.stamp
ASDL_STAMP=${ASDL}.stamp

.PHONY: default clean
default: ${GRAMMAR_STAMP} ${ASDL_STAMP}

${GRAMMAR_STAMP}: ${GRAMMAR} ${TOKENS} ${PEGEN_FILES} main_parser_gen.py
	$(QUIETLY) ${PYTHON_EXE} main_parser_gen.py ${GRAMMAR} ${TOKENS} ${PARSER_TARGET}
	$(QUIETLY) touch $@

${ASDL_STAMP}: ${ASDL} ${ASDL_FILES} main_asdl_gen.py
	$(QUIETLY) ${PYTHON_EXE} main_asdl_gen.py ${ASDL} --sst-path ${PEGPARSER_SRC_PATH} --ast-path ${GRAALPY_SRC_PATH}
	$(QUIETLY) touch $@

clean:
	$(QUIETLY) rm -f ${PARSER_TARGET}
	$(QUIETLY) rm -f ${GRAMMAR_STAMP}
	$(QUIETLY) rm -f ${ASDL_STAMP}
