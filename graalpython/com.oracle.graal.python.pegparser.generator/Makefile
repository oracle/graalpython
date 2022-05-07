QUIETLY$(MX_VERBOSE) = @

PARSER_PATH ?= ../com.oracle.graal.python.pegparser/src/com/oracle/graal/python/pegparser
ifdef MX_PYTHON
PYTHON_EXE ?= ${MX_PYTHON}
else ifdef MX_PYTHON_VERSION
PYTHON_EXE ?= python${MX_PYTHON_VERSION}
else
PYTHON_EXE ?= python3
endif

TARGET=${PARSER_PATH}/Parser.java

GRAMMAR=${PARSER_PATH}/python.gram
TOKENS=${PARSER_PATH}/Tokens

PEGEN_FILES=$(shell find pegen pegjava -name '*.py')

STAMP=${GRAMMAR}.stamp

.PHONY: default clean
default: ${STAMP}

${STAMP}: ${GRAMMAR} ${TOKENS} ${PEGEN_FILES} main_parser_gen.py
	$(QUIETLY) ${PYTHON_EXE} main_parser_gen.py ${GRAMMAR} ${TOKENS} ${TARGET}
	$(QUIETLY) touch $@

clean:
	$(QUIETLY) rm -f ${TARGET}
	$(QUIETLY) rm -f ${STAMP}
