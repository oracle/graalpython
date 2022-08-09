QUIETLY$(MX_VERBOSE) = @

PARSER_PATH ?= ../com.oracle.graal.python.pegparser/src/com/oracle/graal/python/pegparser
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

PEGEN_FILES=$(shell find pegen pegjava -name '*.py')

GRAMMAR_STAMP=${GRAMMAR}.stamp

.PHONY: default clean
default: ${GRAMMAR_STAMP}

${GRAMMAR_STAMP}: ${GRAMMAR} ${TOKENS} ${PEGEN_FILES} main_parser_gen.py
	$(QUIETLY) ${PYTHON_EXE} main_parser_gen.py ${GRAMMAR} ${TOKENS} ${PARSER_TARGET}
	$(QUIETLY) touch $@

clean:
	$(QUIETLY) rm -f ${PARSER_TARGET}
	$(QUIETLY) rm -f ${GRAMMAR_STAMP}
