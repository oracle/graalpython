#
# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
LIBDIR=${VPATH}/../lib-graalpython
TARGET_LIB=${LIBDIR}/capi.bc


SOURCE_FILES=$(wildcard ${VPATH}/src/*.c)
OBJ_FILES=${SOURCE_FILES:%.c=%.o}
INCLUDE_FILES=$(wildcard ${VPATH}/include/*.h)

MODULE_SOURCES=$(wildcard ${VPATH}/modules/*.c)
MODULE_OBJ_FILES=$(MODULE_SOURCES:%.c=%.o)
MODULE_TARGETS=$(MODULE_SOURCES:${VPATH}/modules/%.c=${LIBDIR}/modules/%.bc)

.PHONY: default clean
default: ${TARGET_LIB} ${MODULE_TARGETS}


CFLAGS=${LLVM_TARGET_FLAGS} -ggdb -emit-llvm
OPT_FLAGS=-mem2reg -globalopt -simplifycfg -constprop -always-inline -instcombine -dse -loop-simplify -reassociate -licm -gvn
WARNINGS=-Wno-int-to-pointer-cast -Wno-int-conversion -Wno-incompatible-pointer-types-discards-qualifiers
INCLUDES=-I${VPATH}/include


rebuild:
	$(MAKE) -C ${VPATH} clean
	$(MAKE) -C ${VPATH} ${TARGET_LIB}

${LIBDIR}/modules/%.bc: ${VPATH}/modules/%.o
	@mkdir -p ${LIBDIR}/modules
	llvm-link -o $@ $+

${TARGET_LIB}: ${OBJ_FILES}
	llvm-link -o $@ $+

${VPATH}/src/%.o: ${VPATH}/src/%.c ${VPATH}/Makefile ${VPATH}/src/capi.h ${INCLUDE_FILES}
	clang ${CFLAGS} ${WARNINGS} ${INCLUDES} -o $@ -c $<
	opt -o $@ $@ ${OPT_FLAGS}

${VPATH}/modules/%.o: ${VPATH}/modules/%.c ${VPATH}/Makefile ${VPATH}/src/capi.h ${INCLUDE_FILES}
	clang ${CFLAGS} ${WARNINGS} ${INCLUDES} -o $@ -c $<
	opt -o $@ $@ ${OPT_FLAGS}

clean:
	@rm -f ${TARGET_LIB}
	@rm -f ${TARGET_LIB}.bc
	@rm -f ${OBJ_FILES}
