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
 * Wraps a PythonObject to provide a native view with a shape like {@code PySequenceMethods}.
 */
@ExportLibrary(InteropLibrary.class)
public class PySequenceMethodsWrapper extends PythonNativeWrapper {

    public PySequenceMethodsWrapper(PythonManagedClass delegate) {
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

            writePointerNode.write(mem, CFields.PySequenceMethods__sq_length, getValue(obj, SlotMethodDef.SQ_LENGTH));
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_concat, getValue(obj, SlotMethodDef.SQ_CONCAT));
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_repeat, getValue(obj, SlotMethodDef.SQ_REPEAT));
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_item, getValue(obj, SlotMethodDef.SQ_ITEM));
            writePointerNode.write(mem, CFields.PySequenceMethods__was_sq_slice, nullValue);
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_ass_item, nullValue);
            writePointerNode.write(mem, CFields.PySequenceMethods__was_sq_ass_slice, nullValue);
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_contains, nullValue);
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_inplace_concat, nullValue);
            writePointerNode.write(mem, CFields.PySequenceMethods__sq_inplace_repeat, nullValue);

            return mem;
        }
    }
}
