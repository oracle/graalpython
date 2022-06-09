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
