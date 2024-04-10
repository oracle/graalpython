/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.capsule;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.Capsule;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(InteropLibrary.class)
public final class PyCapsule extends PythonBuiltinObject {
    /*
     * This class provides indirection to all the data members. Capsule destructors take the
     * capsule, so we use this to recreate a temporary "resurrected" capsule for the destructor
     * call.
     */
    public static class CapsuleData {
        private Object pointer;
        private Object name;
        private Object context;
        private Object destructor;

        public CapsuleData(Object pointer, Object name) {
            this.pointer = pointer;
            this.name = name;
        }

        public Object getDestructor() {
            return destructor;
        }
    }

    private final CapsuleData data;

    /**
     * (mq) We are forcing all PyCapsule objects to be of a builtin type
     * PythonBuiltinClassType.Capsule. There are, currently, no possible way to extend PyCapsule, so
     * we can relax few checks elsewhere.
     */
    public PyCapsule(PythonLanguage lang, CapsuleData data) {
        super(Capsule, Capsule.getInstanceShape(lang));
        this.data = data;
    }

    public CapsuleData getData() {
        return data;
    }

    public Object getPointer() {
        return data.pointer;
    }

    public void setPointer(Object pointer) {
        data.pointer = pointer;
    }

    public Object getName() {
        return data.name;
    }

    public void setName(Object name) {
        data.name = name;
    }

    public Object getContext() {
        return data.context;
    }

    public void setContext(Object context) {
        data.context = context;
    }

    public Object getDestructor() {
        return data.destructor;
    }

    public void registerDestructor(Object destructor) {
        assert destructor == null || !InteropLibrary.getUncached().isNull(destructor);
        if (data.destructor == null && destructor != null) {
            CApiTransitions.registerPyCapsuleDestructor(this);
        }
        data.destructor = destructor;
    }

    @ExportMessage
    @TruffleBoundary
    public String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        String quote, n;
        if (data.name != null) {
            quote = "\"";
            if (data.name instanceof TruffleString) {
                n = ((TruffleString) getName()).toJavaStringUncached();
            } else {
                n = CastToJavaStringNode.getUncached().execute(FromCharPointerNodeGen.getUncached().execute(data.name, false));
            }
        } else {
            quote = "";
            n = "NULL";
        }
        return String.format("<capsule object %s%s%s at %x>", quote, n, quote, hashCode());
    }
}
