/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyNumberMethods}.
 */
@ExportLibrary(InteropLibrary.class)
public final class PyNumberMethodsWrapper extends PythonNativeWrapper {

    public PyNumberMethodsWrapper(PythonManagedClass delegate) {
        super(delegate);
    }

    @GenerateUncached
    public abstract static class AllocateNode extends PNodeWithContext {

        public abstract Object execute(PythonManagedClass clazz);

        static Object getValue(PythonManagedClass obj, SlotMethodDef slot) {
            return LookupNativeSlotNode.executeUncached(obj, slot);
        }

        @Specialization
        Object alloc(PythonManagedClass obj,
                        @Cached CStructAccess.AllocateNode allocNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode) {

            Object mem = allocNode.alloc(CStructs.PyNumberMethods);

            Object nullValue = getContext().getNativeNull().getPtr();

            writePointerNode.write(mem, CFields.PyNumberMethods__nb_absolute, getValue(obj, SlotMethodDef.NB_ABSOLUTE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_add, getValue(obj, SlotMethodDef.NB_ADD));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_and, getValue(obj, SlotMethodDef.NB_AND));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_bool, getValue(obj, SlotMethodDef.NB_BOOL));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_divmod, getValue(obj, SlotMethodDef.NB_DIVMOD));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_float, getValue(obj, SlotMethodDef.NB_FLOAT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_floor_divide, getValue(obj, SlotMethodDef.NB_FLOOR_DIVIDE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_index, getValue(obj, SlotMethodDef.NB_INDEX));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_add, getValue(obj, SlotMethodDef.NB_INPLACE_ADD));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_and, getValue(obj, SlotMethodDef.NB_INPLACE_AND));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_floor_divide, getValue(obj, SlotMethodDef.NB_INPLACE_FLOOR_DIVIDE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_lshift, getValue(obj, SlotMethodDef.NB_INPLACE_LSHIFT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_matrix_multiply, nullValue);
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_multiply, getValue(obj, SlotMethodDef.NB_INPLACE_MULTIPLY));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_or, getValue(obj, SlotMethodDef.NB_INPLACE_OR));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_power, getValue(obj, SlotMethodDef.NB_INPLACE_POWER));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_remainder, getValue(obj, SlotMethodDef.NB_INPLACE_REMAINDER));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_rshift, getValue(obj, SlotMethodDef.NB_INPLACE_RSHIFT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_subtract, getValue(obj, SlotMethodDef.NB_INPLACE_SUBTRACT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_true_divide, getValue(obj, SlotMethodDef.NB_INPLACE_TRUE_DIVIDE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_inplace_xor, getValue(obj, SlotMethodDef.NB_INPLACE_XOR));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_int, getValue(obj, SlotMethodDef.NB_INT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_invert, getValue(obj, SlotMethodDef.NB_INVERT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_lshift, getValue(obj, SlotMethodDef.NB_LSHIFT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_matrix_multiply, nullValue);
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_multiply, getValue(obj, SlotMethodDef.NB_MULTIPLY));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_negative, getValue(obj, SlotMethodDef.NB_NEGATIVE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_or, getValue(obj, SlotMethodDef.NB_OR));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_positive, getValue(obj, SlotMethodDef.NB_POSITIVE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_power, getValue(obj, SlotMethodDef.NB_POWER));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_remainder, getValue(obj, SlotMethodDef.NB_REMAINDER));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_reserved, nullValue);
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_rshift, getValue(obj, SlotMethodDef.NB_RSHIFT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_subtract, getValue(obj, SlotMethodDef.NB_SUBTRACT));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_true_divide, getValue(obj, SlotMethodDef.NB_TRUE_DIVIDE));
            writePointerNode.write(mem, CFields.PyNumberMethods__nb_xor, getValue(obj, SlotMethodDef.NB_XOR));

            return mem;
        }
    }
}
