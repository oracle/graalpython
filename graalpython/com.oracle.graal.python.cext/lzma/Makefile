# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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

SHELL=/bin/sh
QUIETLY$(MX_VERBOSE) = @

XZ_ROOT=.
CONFIG_H_DIR=$(XZ_ROOT)
OBJ_DIR=$(XZ_ROOT)/build

LIB_DIR=$(XZ_ROOT)/dist/lib
INC_DIR=$(XZ_ROOT)/dist/include

INCLUDES=-I$(CONFIG_H_DIR) \
			-I$(XZ_ROOT)/src/liblzma \
			-I$(XZ_ROOT)/src/liblzma/api \
			-I$(XZ_ROOT)/src/liblzma/common \
			-I$(XZ_ROOT)/src/liblzma/check \
			-I$(XZ_ROOT)/src/liblzma/lz \
			-I$(XZ_ROOT)/src/liblzma/rangecoder \
			-I$(XZ_ROOT)/src/liblzma/lzma \
			-I$(XZ_ROOT)/src/liblzma/delta \
			-I$(XZ_ROOT)/src/liblzma/simple \
			-I$(XZ_ROOT)/src/common

CFLAGS:=$(CFLAGS) -fPIC -DPIC -g -O2

ifeq ($(shell uname -s), Darwin)
LIB_NAME=liblzma.5.dylib
LIB_LINK=-Wl,-install_name -Wl,@rpath/$(LIB_NAME)
SYM_LIB1=liblzma.dylib
SYM_LIB2=liblzma.5.2.6.dylib
else
LIB_NAME=liblzma.so.5
LIB_LINK=-Wl,-soname -Wl,$(LIB_NAME)
SYM_LIB1=liblzma.so
SYM_LIB2=liblzma.so.5.2.6
endif

CC=clang
SRC = $(XZ_ROOT)/src

# public domain licensed sources 
SRC_FILES= $(SRC)/common/tuklib_physmem.c \
			$(SRC)/liblzma/common/vli_size.c \
			$(SRC)/liblzma/common/hardware_physmem.c \
			$(SRC)/liblzma/common/filter_encoder.c \
			$(SRC)/liblzma/common/block_buffer_decoder.c \
			$(SRC)/liblzma/common/index.c \
			$(SRC)/liblzma/common/stream_encoder.c \
			$(SRC)/liblzma/common/block_encoder.c \
			$(SRC)/liblzma/common/easy_encoder.c \
			$(SRC)/liblzma/common/easy_buffer_encoder.c \
			$(SRC)/liblzma/common/block_util.c \
			$(SRC)/liblzma/common/filter_buffer_decoder.c \
			$(SRC)/liblzma/common/stream_flags_encoder.c \
			$(SRC)/liblzma/common/easy_preset.c \
			$(SRC)/liblzma/common/index_encoder.c \
			$(SRC)/liblzma/common/common.c \
			$(SRC)/liblzma/common/block_header_encoder.c \
			$(SRC)/liblzma/common/vli_encoder.c \
			$(SRC)/liblzma/common/block_header_decoder.c \
			$(SRC)/liblzma/common/stream_buffer_encoder.c \
			$(SRC)/liblzma/common/block_buffer_encoder.c \
			$(SRC)/liblzma/common/alone_decoder.c \
			$(SRC)/liblzma/common/auto_decoder.c \
			$(SRC)/liblzma/common/stream_flags_common.c \
			$(SRC)/liblzma/common/easy_decoder_memusage.c \
			$(SRC)/liblzma/common/easy_encoder_memusage.c \
			$(SRC)/liblzma/common/filter_buffer_encoder.c \
			$(SRC)/liblzma/common/filter_decoder.c \
			$(SRC)/liblzma/common/filter_flags_encoder.c \
			$(SRC)/liblzma/common/filter_common.c \
			$(SRC)/liblzma/common/alone_encoder.c \
			$(SRC)/liblzma/common/block_decoder.c \
			$(SRC)/liblzma/common/filter_flags_decoder.c \
			$(SRC)/liblzma/common/index_decoder.c \
			$(SRC)/liblzma/common/stream_buffer_decoder.c \
			$(SRC)/liblzma/common/stream_flags_decoder.c \
			$(SRC)/liblzma/common/index_hash.c \
			$(SRC)/liblzma/common/stream_decoder.c \
			$(SRC)/liblzma/common/vli_decoder.c \
			$(SRC)/liblzma/check/crc64_table.c \
			$(SRC)/liblzma/check/crc32_table.c \
			$(SRC)/liblzma/check/crc64_fast.c \
			$(SRC)/liblzma/check/crc32_fast.c \
			$(SRC)/liblzma/check/check.c \
			$(SRC)/liblzma/check/sha256.c \
			$(SRC)/liblzma/lz/lz_encoder.c \
			$(SRC)/liblzma/lz/lz_encoder_mf.c \
			$(SRC)/liblzma/lz/lz_decoder.c \
			$(SRC)/liblzma/lzma/lzma_encoder_presets.c \
			$(SRC)/liblzma/lzma/lzma_encoder.c \
			$(SRC)/liblzma/lzma/lzma_encoder_optimum_fast.c \
			$(SRC)/liblzma/lzma/lzma_encoder_optimum_normal.c \
			$(SRC)/liblzma/lzma/lzma2_encoder.c \
			$(SRC)/liblzma/lzma/lzma2_decoder.c \
			$(SRC)/liblzma/lzma/lzma_decoder.c \
			$(SRC)/liblzma/lzma/fastpos_table.c \
			$(SRC)/liblzma/rangecoder/price_table.c \
			$(SRC)/liblzma/delta/delta_common.c \
			$(SRC)/liblzma/delta/delta_encoder.c \
			$(SRC)/liblzma/delta/delta_decoder.c \
			$(SRC)/liblzma/simple/simple_coder.c \
			$(SRC)/liblzma/simple/simple_encoder.c \
			$(SRC)/liblzma/simple/simple_decoder.c \
			$(SRC)/liblzma/simple/powerpc.c \
			$(SRC)/liblzma/simple/x86.c \
			$(SRC)/liblzma/simple/arm.c \
			$(SRC)/liblzma/simple/ia64.c \
			$(SRC)/liblzma/simple/sparc.c \
			$(SRC)/liblzma/simple/armthumb.c

OBJS_FILES=$(patsubst %.c,%.o,$(SRC_FILES))
OBJS =$(patsubst %.o,$(OBJ_DIR)/%.o,$(notdir $(OBJS_FILES)))

all: build_dir objects
	$(QUIETLY) $(CC) -shared $(CFLAGS)  $(LIB_LINK) -o $(LIB_DIR)/$(LIB_NAME) $(OBJS)
	$(QUIETLY) cd $(LIB_DIR) && ln -s $(LIB_NAME) $(SYM_LIB1)
	$(QUIETLY) cd $(LIB_DIR) && ln -s $(LIB_NAME) $(SYM_LIB2)
	$(QUIETLY) cp -r $(XZ_ROOT)/src/liblzma/api/lzma $(INC_DIR)/lzma
	$(QUIETLY) cp $(XZ_ROOT)/src/liblzma/api/lzma.h $(INC_DIR)/lzma.h
	$(QUIETLY) rm -f $(OBJS)

objects: build_dir $(OBJS_FILES)

.c.o:
	$(QUIETLY) $(CC) -DHAVE_CONFIG_H $(CFLAGS) $(INCLUDES) -c $< -o $(OBJ_DIR)/$(@F)

build_dir:
	$(QUIETLY) mkdir -p $(OBJ_DIR)
	$(QUIETLY) mkdir -p $(LIB_DIR)
	$(QUIETLY) mkdir -p $(INC_DIR)

clean: 
	$(QUIETLY) rm -f $(OBJS) $(OBJS_FILES)
	$(QUIETLY) cd $(LIB_DIR) && rm -f $(LIB_NAME) $(SYM_LIB1) $(SYM_LIB2)
	$(QUIETLY) cd $(INC_DIR) && rm -rf lzma && rm -f lzma.h 
