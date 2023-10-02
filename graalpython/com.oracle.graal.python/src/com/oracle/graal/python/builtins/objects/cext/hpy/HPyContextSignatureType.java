/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

/**
 * Describes the type of argument or return type in the HPyContext functions.
 */
public enum HPyContextSignatureType {
    HPyContextPtr("HPyContext*", "POINTER", long.class),
    CVoid("void", "VOID", void.class),
    VoidPtr("void*", "POINTER", long.class),
    VoidPtrPtr("void**", "POINTER", long.class),
    Bool("bool", "UINT8", boolean.class),
    Int("int", null, int.class),
    UnsignedInt("unsigned int", null, int.class),
    Long("long", null, int.class),
    UnsignedLong("unsigned long", "SINT32", int.class),
    Int8_t("int8_t", "SINT8", byte.class),
    Uint8_t("uint8_t", "UINT8", byte.class),
    Int16_t("int16_t", "SINT16", short.class),
    Uint16_t("uint16_t", "UINT16", short.class),
    Int32_t("int32_t", "SINT32", int.class),
    Uint32_t("uint32_t", "UINT32", int.class),
    CFloat("float", "FLOAT", float.class),
    CDouble("double", "DOUBLE", double.class),
    Int64_t("int64_t", "SINT64", int.class),
    Uint64_t("uint64_t", "UINT64", int.class),
    HPy("HPy", "POINTER", long.class),
    HPyPtr("HPy*", "POINTER", long.class),
    ConstHPyPtr("const HPy*", "POINTER", long.class),
    Wchar_tPtr("wchar_t*", "POINTER", long.class),
    ConstWchar_tPtr("const wchar_t*", "POINTER", long.class),
    CharPtr("char*", "POINTER", long.class),
    ConstCharPtr("const char*", "POINTER", long.class),
    DataPtr("void*", "POINTER", long.class),
    DataPtrPtr("void**", "POINTER", long.class),
    HPyTracker("HPyTracker", "POINTER", long.class),
    Size_t("size_t", "UINT64", long.class),
    HPy_ssize_t("HPy_ssize_t", "UINT64", long.class),
    HPy_ssize_tPtr("HPy_ssize_t*", "POINTER", long.class),
    HPy_hash_t("HPy_hash_t", "UINT64", long.class),
    HPy_UCS4("HPy_UCS4", "UINT32", int.class),
    HPyTupleBuilder("HPyTupleBuilder", "POINTER", long.class),
    HPyListBuilder("HPyListBuilder", "POINTER", long.class),
    Cpy_PyObjectPtr("cpy_PyObject*", "POINTER", long.class),
    Cpy_PyMethodDefPtr("cpy_PyMethodDef*", "POINTER", long.class),
    HPyModuleDefPtr("HPyModuleDef*", "POINTER", long.class),
    HPyType_SpecPtr("HPyType_Spec*", "POINTER", long.class),
    HPyType_SpecParam("HPyType_SpecParam", null, null),
    HPyType_SpecParamPtr("HPyType_SpecParam*", "POINTER", long.class),
    HPyDefPtr("HPyDef*", "POINTER", long.class),
    HPyThreadState("HPyThreadState", "POINTER", long.class),
    HPyField("HPyField", "POINTER", long.class),
    HPyFieldPtr("HPyField*", "POINTER", long.class),
    HPyGlobal("HPyGlobal", "POINTER", long.class),
    HPyGlobalPtr("HPyGlobal*", "POINTER", long.class),
    HPyCapsule_DestructorPtr("HPyCapsule_Destructor*", "POINTER", long.class),
    _HPyCapsule_key("_HPyCapsule_key", "SINT32", int.class),
    HPyType_BuiltinShape("HPyType_BuiltinShape", "SINT32", int.class),
    HPy_SourceKind("HPy_SourceKind", "SINT32", int.class),
    HPyCallFunctionPtr("HPyCallFunction*", "POINTER", long.class),
    PyType_Slot("PyType_Slot", null, null),
    PyType_SlotPtr("PyType_Slot*", "POINTER", long.class),
    HPyFunc_Signature("HPyFunc_Signature", null, null),
    HPyMember_FieldType("HPyMember_FieldType", null, null),
    HPySlot_Slot("HPySlot_Slot", null, null),
    PyMemberDef("PyMemberDef", null, null),
    HPy_buffer("HPy_buffer", null, null),
    PyGetSetDef("PyGetSetDef", null, null);

    /**
     * The type definition used in C source code.
     */
    final String cType;
    /**
     * The type definition that is used in NFI signatures.
     */
    final String nfiType;
    /**
     * The type used on the Java side in JNI/CLinker functions.
     */
    final Class<?> jniType;

    HPyContextSignatureType(String cType, String nfiType, Class<?> jniType) {
        this.cType = cType;
        this.nfiType = nfiType;
        this.jniType = jniType;
    }
}
