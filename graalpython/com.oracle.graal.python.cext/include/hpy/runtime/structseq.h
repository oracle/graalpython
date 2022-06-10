/* MIT License
 *
 * Copyright (c) 2022, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef HPY_COMMON_RUNTIME_STRUCTSEQ_H
#define HPY_COMMON_RUNTIME_STRUCTSEQ_H

#include "hpy.h"

/*
 * Struct sequences are relatively simple subclasses of tuple, so we provide the
 * simplified API to create them here. This maps closely to the CPython limited
 * API for creating struct sequences. However, in universal mode we use the
 * collections.namedtuple type to implement this, which behaves a bit
 * differently w.r.t. hidden elements. Thus, the n_in_sequence field available
 * in CPython's PyStructSequence_Desc is not available. Also, we use a builder
 * API like for tuples and lists so that the struct sequence is guaranteed not
 * to be written after it is created.
 */
typedef struct {
    const char *name;
    const char *doc;
} HPyStructSequence_Field;

typedef struct {
    const char *name;
    const char *doc;
    HPyStructSequence_Field *fields;
} HPyStructSequence_Desc;

extern const char * const HPyStructSequence_UnnamedField;

typedef HPyTupleBuilder HPyStructSequenceBuilder;

HPyAPI_HELPER HPy HPyStructSequence_NewType(HPyContext*, HPyStructSequence_Desc*);

HPyAPI_HELPER HPyStructSequenceBuilder HPyStructSequenceBuilder_New(HPyContext*, HPy);

HPyAPI_HELPER void HPyStructSequenceBuilder_Set(HPyContext*, HPyStructSequenceBuilder, HPy_ssize_t, HPy);

HPyAPI_HELPER void HPyStructSequenceBuilder_Set_i(HPyContext*, HPyStructSequenceBuilder, HPy_ssize_t, long);

HPyAPI_HELPER HPy HPyStructSequenceBuilder_Build(HPyContext*, HPyStructSequenceBuilder, HPy);

HPyAPI_HELPER void HPyStructSequenceBuilder_Cancel(HPyContext*, HPyStructSequenceBuilder);

HPyAPI_HELPER HPy HPyStructSequence_GetItem(HPyContext*, HPy, HPy_ssize_t);

#endif /* HPY_COMMON_RUNTIME_STRUCTSEQ_H */
