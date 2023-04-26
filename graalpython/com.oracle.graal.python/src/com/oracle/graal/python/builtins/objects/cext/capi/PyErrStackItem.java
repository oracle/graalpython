/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

/**
 * Emulates {@code _PyErr_StackItem}.
 */
@ExportLibrary(InteropLibrary.class)
public final class PyErrStackItem extends PythonNativeWrapper {

    public static final String J_EXC_TYPE = "exc_type";
    public static final String J_EXC_VALUE = "exc_value";
    public static final String J_EXC_TRACEBACK = "exc_traceback";
    public static final String J_PREVIOUS_ITEM = "previous_item";

    private final Object exception;

    public PyErrStackItem(Object exception) {
        this.exception = exception;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(new Object[]{J_EXC_TYPE, J_EXC_VALUE, J_EXC_TRACEBACK, J_PREVIOUS_ITEM});
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String key) {
        return J_EXC_TYPE.equals(key) || J_EXC_VALUE.equals(key) || J_EXC_TRACEBACK.equals(key) || J_PREVIOUS_ITEM.equals(key);
    }

    @ExportMessage
    Object readMember(String key,
                    @Bind("$node") Node inliningTarget,
                    @Cached InlinedGetClassNode getClassNode,
                    @Cached ExceptionNodes.GetTracebackNode getTracebackNode,
                    @Cached ToSulongNode toSulongNode) {
        Object result = null;
        if (exception != null) {
            switch (key) {
                case J_EXC_TYPE -> result = getClassNode.execute(inliningTarget, exception);
                case J_EXC_VALUE -> result = exception;
                case J_EXC_TRACEBACK -> {
                    result = getTracebackNode.execute(inliningTarget, exception);
                    if (result == PNone.NONE) {
                        result = null;
                    }
                }
            }
        }
        if (result == null) {
            result = PythonContext.get(toSulongNode).getNativeNull();
        }
        return toSulongNode.execute(result);
    }

    // TO POINTER / AS POINTER / TO NATIVE

    @ExportMessage
    protected boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    public long asPointer() {
        return getNativePointer();
    }

    @ExportMessage
    protected void toNative(
                    @Bind("$node") Node inliningTarget,
                    @Cached InlinedConditionProfile isNativeProfile) {
        if (!isNative(inliningTarget, isNativeProfile)) {
            CApiTransitions.firstToNative(this);
        }
    }
}
