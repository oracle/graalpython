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

import static com.oracle.graal.python.builtins.objects.cext.capi.ReadSlotByNameNode.getSlot;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_ABSOLUTE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_ADD;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_AND;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_BOOL;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_DIVMOD;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_FLOAT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_FLOOR_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INDEX;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_ADD;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_AND;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_FLOOR_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_LSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_OR;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_POWER;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_REMAINDER;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_RSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_SUBTRACT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_TRUE_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INPLACE_XOR;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_INVERT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_LSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_NEGATIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_OR;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_POSITIVE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_POWER;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_REMAINDER;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_RSHIFT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_SUBTRACT;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_TRUE_DIVIDE;
import static com.oracle.graal.python.builtins.objects.cext.capi.SlotMethodDef.NB_XOR;

import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyNumberMethods}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
@ImportStatic(SpecialMethodNames.class)
public final class PyNumberMethodsWrapper extends PythonNativeWrapper {

    @CompilationFinal(dimensions = 1) private static SlotMethodDef[] SLOTS = new SlotMethodDef[]{
                    NB_ABSOLUTE,
                    NB_ADD,
                    NB_AND,
                    NB_BOOL,
                    NB_DIVMOD,
                    NB_FLOAT,
                    NB_FLOOR_DIVIDE,
                    NB_INDEX,
                    NB_INPLACE_ADD,
                    NB_INPLACE_AND,
                    NB_INPLACE_FLOOR_DIVIDE,
                    NB_INPLACE_LSHIFT,
                    NB_INPLACE_MULTIPLY,
                    NB_INPLACE_OR,
                    NB_INPLACE_POWER,
                    NB_INPLACE_REMAINDER,
                    NB_INPLACE_RSHIFT,
                    NB_INPLACE_SUBTRACT,
                    NB_INPLACE_TRUE_DIVIDE,
                    NB_INPLACE_XOR,
                    NB_INT,
                    NB_INVERT,
                    NB_LSHIFT,
                    NB_MULTIPLY,
                    NB_NEGATIVE,
                    NB_OR,
                    NB_POSITIVE,
                    NB_POWER,
                    NB_REMAINDER,
                    NB_RSHIFT,
                    NB_SUBTRACT,
                    NB_TRUE_DIVIDE,
                    NB_XOR,
    };

    public PyNumberMethodsWrapper(PythonManagedClass delegate) {
        super(delegate);
    }

    public PythonManagedClass getPythonClass() {
        return (PythonManagedClass) getDelegate();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean isMemberReadable(String member) {
        return getSlot(member, SLOTS) != null;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Bind("$node") Node inliningTarget,
                    @Cached ReadSlotByNameNode readSlotByNameNode,
                    @Exclusive @Cached GilNode gil) throws UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            Object result = readSlotByNameNode.execute(inliningTarget, this, member, SLOTS);
            if (result == null) {
                throw UnknownIdentifierException.create(member);
            }
            return result;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // TODO implement native type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        // TODO implement native type
        return null;
    }
}
