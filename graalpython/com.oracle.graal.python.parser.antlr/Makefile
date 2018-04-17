TARGETS=${OUTPUT_PATH}/Python3BaseVisitor.java \
	${OUTPUT_PATH}/Python3Listener.java \
	${OUTPUT_PATH}/Python3BaseListener.java \
	${OUTPUT_PATH}/Python3Visitor.java \
	${OUTPUT_PATH}/Python3Parser.java \
	${OUTPUT_PATH}/Python3Lexer.java

SOURCE=${PARSER_PATH}/Python3.g4
STAMP=${PARSER_PATH}/Python3.g4.stamp

.PHONY: default clean
default: ${STAMP}

# postprocessing to make source compile without warnings
define postprocess_file
	python ${POSTPROCESSOR} $(1)
endef

${STAMP}: ${SOURCE}
	touch $@
	${JAVA_HOME}/bin/java -cp ${ANTLR_JAR} org.antlr.v4.Tool -visitor -package ${PARSER_PKG} -o ${OUTPUT_PATH} ${SOURCE}
	$(foreach var,$(TARGETS),$(call postprocess_file,$(var));)

clean:
	rm -f ${TARGETS}
