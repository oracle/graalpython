QUIETLY$(MX_VERBOSE) = @

PARSER_PATH ?= ../com.oracle.graal.python.pegparser/src/com/oracle/graal/python/pegparser
ifdef PYTHON3_HOME
PYTHON ?= ${PYTHON3_HOME}/python
else
PYTHON ?= python3
endif

TARGET=${PARSER_PATH}/Parser.java

GRAMMAR=${PARSER_PATH}/python.gram
TOKENS=${PARSER_PATH}/Tokens

PEGEN_FILES=$(shell find pegen pegjava -name '*.py')

STAMP=${GRAMMAR}.stamp

.PHONY: default clean
default: ${STAMP}

${STAMP}: ${GRAMMAR} ${TOKENS} ${PEGEN_FILES} main_parser_gen.py
	$(QUIETLY) ${PYTHON} main_parser_gen.py ${GRAMMAR} ${TOKENS} ${TARGET}
	$(QUIETLY) touch $@

clean:
	$(QUIETLY) rm -f ${TARGET}
	$(QUIETLY) rm -f ${STAMP}
