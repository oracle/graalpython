/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PySequenceMethods}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class PySequenceMethodsWrapper extends PythonNativeWrapper {

    public PySequenceMethodsWrapper(PythonManagedClass delegate) {
        super(delegate);
    }

    public PythonManagedClass getPythonClass(PythonNativeWrapperLibrary lib) {
        return (PythonManagedClass) lib.getDelegate(this);
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case NativeMemberNames.SQ_REPEAT:
            case NativeMemberNames.SQ_ITEM:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached LookupAttributeInMRONode.Dynamic getSqItemNode,
                    @Cached LookupAttributeInMRONode.Dynamic getSqRepeatNode,
                    @Cached CExtNodes.ToSulongNode toSulongNode,
                    @Cached BranchProfile errorProfile,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                    @Cached GetNativeNullNode getNativeNullNode) throws UnknownIdentifierException {
        Object result;
        try {
            switch (member) {
                case NativeMemberNames.SQ_REPEAT:
                    result = toSulongNode.execute(getSqRepeatNode.execute(getPythonClass(lib), __MUL__));
                    break;
                case NativeMemberNames.SQ_ITEM:
                    return PyProcsWrapper.createSsizeargfuncWrapper(getSqItemNode.execute(getPythonClass(lib), __GETITEM__));
                default:
                    // TODO extend list
                    throw UnknownIdentifierException.create(member);
            }
        } catch (PException e) {
            errorProfile.enter();
            transformExceptionToNativeNode.execute(null, e);
            result = getNativeNullNode.execute(null);
        }
        return result;
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
