/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi;

import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

// TODO(NFI2) do not expose fields
@ExportLibrary(InteropLibrary.class)
public record NfiBoundFunction(long ptr, MethodHandle boundHandle, NfiSignature signature) implements TruffleObject {

    public long getAddress() {
        return ptr;
    }

    // TODO(NFI2) duplicate code with NfiSignature.invokeUncached
    public Object invoke(Object... args) throws UnsupportedTypeException, ArityException {
        try {
            Object r = boundHandle.invokeExact(signature.convertArgs(args));
            if (signature.getResType() == NfiType.POINTER) {
                // TODO(NFI2) migrate to RAWPOINTER and remove this wrapping
                r = new NativePointer((long) r);
            }
            return r;
        } catch (Throwable e) {
            // TODO(NFI2) proper exception handling
            throw new RuntimeException(e);
        }
    }

    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage
    boolean isNull() {
        return false;
    }

    @ExportMessage
    long asPointer() {
        return ptr;
    }

    @ExportMessage
    void toNative() {
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    // TODO(NFI2) do not implement interop? for now we need this in
    // ExternalFunctionNodes.createKwDefaults()
    // Look for usages of ensureExecutableUncached()
    @ExportMessage
    public Object execute(Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        Object r = invoke(arguments);
        if (r == null) {
            assert signature.getResType() == NfiType.VOID;
            return new NativePointer(0);
        }
        return r;
    }
}
