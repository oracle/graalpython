/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ImportCExtSymbolNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Writes to an output variable in the varargs by doing the necessary native typing and
 * dereferencing. This is mostly like
 *
 * <pre>
 *     SomeType *outVar = va_arg(valist, SomeType *);
 *     *outVar = value;
 * </pre>
 *
 * It is important to use the appropriate {@code write*} functions!
 */
@GenerateUncached
@ImportStatic(NativeCAPISymbol.class)
public abstract class WriteNextVaArgNode extends Node {

    public final void writeUInt8(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_UINT8, value);
    }

    public final void writeInt8(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_INT8, value);
    }

    public final void writeUInt16(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_UINT16, value);
    }

    public final void writeInt16(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_INT16, value);
    }

    public final void writeUInt32(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_INT32, value);
    }

    public final void writeInt32(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_INT32, value);
    }

    public final void writeUInt64(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_UINT64, value);
    }

    public final void writeInt64(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_INT64, value);
    }

    public final void writePySsizeT(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_PYSSIZE_T, value);
    }

    public final void writeFloat(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_FLOAT_T, value);
    }

    public final void writeDouble(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_DOUBLE_T, value);
    }

    public final void writePyObject(Object valist, Object value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_PYOBJECT_PTR_T, value);
    }

    public final void writeComplex(Object valist, PComplex value) throws InteropException {
        execute(valist, NativeCAPISymbol.VALIST_SET_NEXT_PYCOMPLEX, value);
    }

    protected abstract void execute(Object valist, NativeCAPISymbol accessType, Object value) throws InteropException;

    @Specialization(guards = "name != VALIST_SET_NEXT_PYCOMPLEX")
    static void doPointer(Object valist, @SuppressWarnings("unused") NativeCAPISymbol name, Object value,
                    @Shared("importCSym") @Cached ImportCExtSymbolNode importCExtSymbolNode,
                    @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary interopLibrary) {
        try {
            interopLibrary.execute(importCExtSymbolNode.execute(PythonContext.get(importCExtSymbolNode).getCApiContext(), name), valist, value);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @Specialization(guards = "name == VALIST_SET_NEXT_PYCOMPLEX")
    static void doComplex(Object valist, @SuppressWarnings("unused") NativeCAPISymbol name, PComplex value,
                    @Shared("importCSym") @Cached ImportCExtSymbolNode importCExtSymbolNode,
                    @Shared("lib") @CachedLibrary(limit = "3") InteropLibrary interopLibrary) {
        try {
            interopLibrary.execute(importCExtSymbolNode.execute(PythonContext.get(importCExtSymbolNode).getCApiContext(), name), valist, value.getReal(), value.getImag());
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
