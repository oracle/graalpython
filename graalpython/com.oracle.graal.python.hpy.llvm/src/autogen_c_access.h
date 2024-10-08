/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


/*
   DO NOT EDIT THIS FILE!

   This file is automatically generated by hpy.tools.autogen.graalpy.autogen_c_access
   See also hpy.tools.autogen and hpy/tools/public_api.h

   Run this to regenerate:
       make autogen

*/

#ifndef _AUTOGEN_C_ACCESS_H
#define _AUTOGEN_C_ACCESS_H
#include <stddef.h>

#include "Python.h"
#include "structmember.h"

static int fill_c_type_sizes(int32_t *ctype_sizes)
{
    ctype_sizes[0] = (int32_t) sizeof(HPyContext*);
    ctype_sizes[1] = (int32_t) 0;
    ctype_sizes[2] = (int32_t) sizeof(void*);
    ctype_sizes[3] = (int32_t) sizeof(void**);
    ctype_sizes[4] = (int32_t) sizeof(bool);
    ctype_sizes[5] = (int32_t) sizeof(int);
    ctype_sizes[6] = (int32_t) sizeof(unsigned int);
    ctype_sizes[7] = (int32_t) sizeof(long);
    ctype_sizes[8] = (int32_t) sizeof(unsigned long);
    ctype_sizes[9] = (int32_t) sizeof(int8_t);
    ctype_sizes[10] = (int32_t) sizeof(uint8_t);
    ctype_sizes[11] = (int32_t) sizeof(int16_t);
    ctype_sizes[12] = (int32_t) sizeof(uint16_t);
    ctype_sizes[13] = (int32_t) sizeof(int32_t);
    ctype_sizes[14] = (int32_t) sizeof(uint32_t);
    ctype_sizes[15] = (int32_t) sizeof(float);
    ctype_sizes[16] = (int32_t) sizeof(double);
    ctype_sizes[17] = (int32_t) sizeof(int64_t);
    ctype_sizes[18] = (int32_t) sizeof(uint64_t);
    ctype_sizes[19] = (int32_t) sizeof(HPy);
    ctype_sizes[20] = (int32_t) sizeof(HPy*);
    ctype_sizes[21] = (int32_t) sizeof(const HPy*);
    ctype_sizes[22] = (int32_t) sizeof(wchar_t*);
    ctype_sizes[23] = (int32_t) sizeof(const wchar_t*);
    ctype_sizes[24] = (int32_t) sizeof(char*);
    ctype_sizes[25] = (int32_t) sizeof(const char*);
    ctype_sizes[26] = (int32_t) sizeof(void*);
    ctype_sizes[27] = (int32_t) sizeof(void**);
    ctype_sizes[28] = (int32_t) sizeof(HPyTracker);
    ctype_sizes[29] = (int32_t) sizeof(size_t);
    ctype_sizes[30] = (int32_t) sizeof(HPy_ssize_t);
    ctype_sizes[31] = (int32_t) sizeof(HPy_ssize_t*);
    ctype_sizes[32] = (int32_t) sizeof(HPy_hash_t);
    ctype_sizes[33] = (int32_t) sizeof(HPy_UCS4);
    ctype_sizes[34] = (int32_t) sizeof(HPyTupleBuilder);
    ctype_sizes[35] = (int32_t) sizeof(HPyListBuilder);
    ctype_sizes[36] = (int32_t) sizeof(cpy_PyObject*);
    ctype_sizes[37] = (int32_t) sizeof(cpy_PyMethodDef*);
    ctype_sizes[38] = (int32_t) sizeof(HPyModuleDef*);
    ctype_sizes[39] = (int32_t) sizeof(HPyType_Spec*);
    ctype_sizes[40] = (int32_t) sizeof(HPyType_SpecParam);
    ctype_sizes[41] = (int32_t) sizeof(HPyType_SpecParam*);
    ctype_sizes[42] = (int32_t) sizeof(HPyDef*);
    ctype_sizes[43] = (int32_t) sizeof(HPyThreadState);
    ctype_sizes[44] = (int32_t) sizeof(HPyField);
    ctype_sizes[45] = (int32_t) sizeof(HPyField*);
    ctype_sizes[46] = (int32_t) sizeof(HPyGlobal);
    ctype_sizes[47] = (int32_t) sizeof(HPyGlobal*);
    ctype_sizes[48] = (int32_t) sizeof(HPyCapsule_Destructor*);
    ctype_sizes[49] = (int32_t) sizeof(_HPyCapsule_key);
    ctype_sizes[50] = (int32_t) sizeof(HPyType_BuiltinShape);
    ctype_sizes[51] = (int32_t) sizeof(HPy_SourceKind);
    ctype_sizes[52] = (int32_t) sizeof(HPyCallFunction*);
    ctype_sizes[53] = (int32_t) sizeof(PyType_Slot);
    ctype_sizes[54] = (int32_t) sizeof(PyType_Slot*);
    ctype_sizes[55] = (int32_t) sizeof(HPyFunc_Signature);
    ctype_sizes[56] = (int32_t) sizeof(HPyMember_FieldType);
    ctype_sizes[57] = (int32_t) sizeof(HPySlot_Slot);
    ctype_sizes[58] = (int32_t) sizeof(PyMemberDef);
    ctype_sizes[59] = (int32_t) sizeof(HPy_buffer);
    ctype_sizes[60] = (int32_t) sizeof(PyGetSetDef);
    return 0;
};

static int fill_c_field_offsets(int32_t *cfield_offsets)
{
    cfield_offsets[0] = (int32_t) offsetof(HPyType_SpecParam, kind);
    cfield_offsets[1] = (int32_t) offsetof(HPyType_SpecParam, object);
    cfield_offsets[2] = (int32_t) offsetof(HPyType_Spec, name);
    cfield_offsets[3] = (int32_t) offsetof(HPyType_Spec, basicsize);
    cfield_offsets[4] = (int32_t) offsetof(HPyType_Spec, itemsize);
    cfield_offsets[5] = (int32_t) offsetof(HPyType_Spec, flags);
    cfield_offsets[6] = (int32_t) offsetof(HPyType_Spec, builtin_shape);
    cfield_offsets[7] = (int32_t) offsetof(HPyType_Spec, legacy_slots);
    cfield_offsets[8] = (int32_t) offsetof(HPyType_Spec, defines);
    cfield_offsets[9] = (int32_t) offsetof(HPyType_Spec, doc);
    cfield_offsets[10] = (int32_t) offsetof(HPyDef, kind);
    cfield_offsets[11] = (int32_t) offsetof(HPyDef, meth.name);
    cfield_offsets[12] = (int32_t) offsetof(HPyDef, meth.impl);
    cfield_offsets[13] = (int32_t) offsetof(HPyDef, meth.signature);
    cfield_offsets[14] = (int32_t) offsetof(HPyDef, meth.doc);
    cfield_offsets[15] = (int32_t) offsetof(HPyDef, member.name);
    cfield_offsets[16] = (int32_t) offsetof(HPyDef, member.type);
    cfield_offsets[17] = (int32_t) offsetof(HPyDef, member.offset);
    cfield_offsets[18] = (int32_t) offsetof(HPyDef, member.readonly);
    cfield_offsets[19] = (int32_t) offsetof(HPyDef, member.doc);
    cfield_offsets[20] = (int32_t) offsetof(HPyDef, getset.name);
    cfield_offsets[21] = (int32_t) offsetof(HPyDef, getset.getter_impl);
    cfield_offsets[22] = (int32_t) offsetof(HPyDef, getset.setter_impl);
    cfield_offsets[23] = (int32_t) offsetof(HPyDef, getset.doc);
    cfield_offsets[24] = (int32_t) offsetof(HPyDef, getset.closure);
    cfield_offsets[25] = (int32_t) offsetof(HPyDef, slot.slot);
    cfield_offsets[26] = (int32_t) offsetof(HPyDef, slot.impl);
    cfield_offsets[27] = (int32_t) offsetof(PyType_Slot, slot);
    cfield_offsets[28] = (int32_t) offsetof(PyType_Slot, pfunc);
    cfield_offsets[29] = (int32_t) offsetof(HPyCapsule_Destructor, cpy_trampoline);
    cfield_offsets[30] = (int32_t) offsetof(HPyCapsule_Destructor, impl);
    cfield_offsets[31] = (int32_t) offsetof(HPyCallFunction, impl);
    cfield_offsets[32] = (int32_t) offsetof(HPyModuleDef, doc);
    cfield_offsets[33] = (int32_t) offsetof(HPyModuleDef, size);
    cfield_offsets[34] = (int32_t) offsetof(HPyModuleDef, legacy_methods);
    cfield_offsets[35] = (int32_t) offsetof(HPyModuleDef, defines);
    cfield_offsets[36] = (int32_t) offsetof(HPyModuleDef, globals);
    cfield_offsets[37] = (int32_t) offsetof(PyGetSetDef, name);
    cfield_offsets[38] = (int32_t) offsetof(PyGetSetDef, get);
    cfield_offsets[39] = (int32_t) offsetof(PyGetSetDef, set);
    cfield_offsets[40] = (int32_t) offsetof(PyGetSetDef, doc);
    cfield_offsets[41] = (int32_t) offsetof(PyGetSetDef, closure);
    cfield_offsets[42] = (int32_t) offsetof(PyMemberDef, name);
    cfield_offsets[43] = (int32_t) offsetof(PyMemberDef, type);
    cfield_offsets[44] = (int32_t) offsetof(PyMemberDef, offset);
    cfield_offsets[45] = (int32_t) offsetof(PyMemberDef, flags);
    cfield_offsets[46] = (int32_t) offsetof(PyMemberDef, doc);
    cfield_offsets[47] = (int32_t) offsetof(HPy_buffer, buf);
    cfield_offsets[48] = (int32_t) offsetof(HPy_buffer, obj);
    cfield_offsets[49] = (int32_t) offsetof(HPy_buffer, len);
    cfield_offsets[50] = (int32_t) offsetof(HPy_buffer, itemsize);
    cfield_offsets[51] = (int32_t) offsetof(HPy_buffer, readonly);
    cfield_offsets[52] = (int32_t) offsetof(HPy_buffer, ndim);
    cfield_offsets[53] = (int32_t) offsetof(HPy_buffer, format);
    cfield_offsets[54] = (int32_t) offsetof(HPy_buffer, shape);
    cfield_offsets[55] = (int32_t) offsetof(HPy_buffer, strides);
    cfield_offsets[56] = (int32_t) offsetof(HPy_buffer, suboffsets);
    cfield_offsets[57] = (int32_t) offsetof(HPy_buffer, internal);
    return 0;
};

#endif

