/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CharPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Cpy_PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyFunc_Signature;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPySlot_Slot;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPyType_BuiltinShape;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.HPy_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int32_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.PyType_SlotPtr;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Uint32_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.VoidPtr;

public enum GraalHPyCField {
    HPyType_SpecParam__kind(Int32_t),
    HPyType_SpecParam__object(HPy),
    HPyType_Spec__name(ConstCharPtr),
    HPyType_Spec__basicsize(Int32_t),
    HPyType_Spec__itemsize(Int32_t),
    HPyType_Spec__flags(Uint32_t),
    HPyType_Spec__builtin_shape(HPyType_BuiltinShape),
    HPyType_Spec__legacy_slots(PyType_SlotPtr),
    HPyType_Spec__defines(VoidPtr),
    HPyType_Spec__doc(ConstCharPtr),
    HPyDef__kind(Int32_t),
    HPyDef__meth__name(ConstCharPtr),
    HPyDef__meth__impl(VoidPtr),
    HPyDef__meth__signature(HPyFunc_Signature),
    HPyDef__meth__doc(ConstCharPtr),
    HPyDef__member__name(ConstCharPtr),
    HPyDef__member__type(Int),
    HPyDef__member__offset(HPy_ssize_t),
    HPyDef__member__readonly(Int),
    HPyDef__member__doc(ConstCharPtr),
    HPyDef__getset__name(ConstCharPtr),
    HPyDef__getset__getter_impl(VoidPtr),
    HPyDef__getset__setter_impl(VoidPtr),
    HPyDef__getset__doc(ConstCharPtr),
    HPyDef__getset__closure(VoidPtr),
    HPyDef__slot__slot(HPySlot_Slot),
    HPyDef__slot__impl(VoidPtr),
    PyType_Slot__slot(Int),
    PyType_Slot__pfunc(VoidPtr),
    HPyCapsule_Destructor__cpy_trampoline(VoidPtr),
    HPyCapsule_Destructor__impl(VoidPtr),
    HPyCallFunction__impl(VoidPtr),
    HPyModuleDef__doc(ConstCharPtr),
    HPyModuleDef__size(HPy_ssize_t),
    HPyModuleDef__legacy_methods(Cpy_PyObjectPtr),
    HPyModuleDef__defines(VoidPtr),
    HPyModuleDef__globals(VoidPtr),
    PyGetSetDef__name(ConstCharPtr),
    PyGetSetDef__get(VoidPtr),
    PyGetSetDef__set(VoidPtr),
    PyGetSetDef__doc(ConstCharPtr),
    PyGetSetDef__closure(VoidPtr),
    PyMemberDef__name(ConstCharPtr),
    PyMemberDef__type(Int),
    PyMemberDef__offset(HPy_ssize_t),
    PyMemberDef__flags(Int),
    PyMemberDef__doc(ConstCharPtr),
    HPy_buffer__buf(VoidPtr),
    HPy_buffer__obj(HPy),
    HPy_buffer__len(HPy_ssize_t),
    HPy_buffer__itemsize(HPy_ssize_t),
    HPy_buffer__readonly(Int),
    HPy_buffer__ndim(Int),
    HPy_buffer__format(CharPtr),
    HPy_buffer__shape(HPy_ssize_t),
    HPy_buffer__strides(HPy_ssize_t),
    HPy_buffer__suboffsets(HPy_ssize_t),
    HPy_buffer__internal(VoidPtr);

    private final HPyContextSignatureType type;

    GraalHPyCField(HPyContextSignatureType type) {
        this.type = type;
    }

    public HPyContextSignatureType getType() {
        return type;
    }
}
