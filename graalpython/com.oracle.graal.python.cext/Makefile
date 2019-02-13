# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

QUIETLY$(MX_VERBOSE) = @

ifndef PLATFORM
  $(error PLATFORM is undefined)
endif

LIBDIR=${VPATH}/../lib-graalpython
TARGET_LIB=${LIBDIR}/${PLATFORM}/capi.bc

SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)
OBJ_FILES=${SOURCE_FILES:${VPATH}/src/%.c=${PLATFORM}/%.bc}
INCLUDE_FILES=$(wildcard ${VPATH}/include/*.h)

MODULE_SOURCES=$(wildcard ${VPATH}/modules/*.c)
MODULE_OBJ_FILES=$(MODULE_SOURCES:%.c=%.o)
MODULE_TARGETS=$(MODULE_SOURCES:${VPATH}/modules/%.c=${LIBDIR}/modules/${PLATFORM}/%.bc)

HEADER_TARGETS=$(INCLUDE_FILES:${VPATH}/include/%.h=${VPATH}/../include/%.h)

.PHONY: default clean
default: ${TARGET_LIB} ${MODULE_TARGETS} ${HEADER_TARGETS}


CFLAGS=${LLVM_TARGET_FLAGS} -O1 -ggdb -emit-llvm
OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -always-inline -instcombine -dse -loop-simplify -reassociate -licm -gvn
WARNINGS=-Wno-int-to-pointer-cast -Wno-int-conversion -Wno-incompatible-pointer-types-discards-qualifiers -Wno-pointer-type-mismatch
INCLUDES=-I${POLYGLOT_INC} -I${VPATH}/include


rebuild:
	$(MAKE) -C ${VPATH} clean
	$(MAKE) -C ${VPATH} ${TARGET_LIB} ${MODULE_TARGETS} ${HEADER_TARGETS}

${TARGET_LIB}: ${OBJ_FILES}
	$(QUIETLY) llvm-link -o $@ $+

${PLATFORM}/%.bc: ${VPATH}/src/%.c ${VPATH}/Makefile ${VPATH}/src/capi.h ${INCLUDE_FILES}
	$(QUIETLY) mkdir -p $(dir $@)
	$(QUIETLY) clang ${CFLAGS} ${WARNINGS} ${INCLUDES} -o $@ -c $<
	$(QUIETLY) opt -o $@ $@ ${OPT_FLAGS}

${LIBDIR}/modules/${PLATFORM}/%.bc: ${VPATH}/modules/%.c ${VPATH}/Makefile ${VPATH}/src/capi.h ${INCLUDE_FILES}
	$(QUIETLY) mkdir -p $(dir $@)
	$(QUIETLY) clang ${CFLAGS} ${WARNINGS} ${INCLUDES} -o $@ -c $<
	$(QUIETLY) opt -o $@ $@ ${OPT_FLAGS}

${VPATH}/../include/%.h: ${INCLUDE_FILES}
	$(QUIETLY) mkdir -p ${VPATH}/../include/
	$(QUIETLY) cp $(@:${VPATH}/../include/%.h=${VPATH}/include/%.h) $@

clean:
	$(QUIETLY) rm -f ${TARGET_LIB}
	$(QUIETLY) rm -f ${TARGET_LIB}.bc
	$(QUIETLY) rm -f ${OBJ_FILES}
