/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyEnsureHandleNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * A simple interop-capable object that is used to initialize the HPy C API.
 */
@ExportLibrary(InteropLibrary.class)
public class GraalHPyInitObject implements TruffleObject {

    public static final String SET_HPY_NATIVE_TYPE = "setHPyNativeType";
    public static final String SET_HPY_ARRAY_NATIVE_TYPE = "setHPyArrayNativeType";
    public static final String SET_HPY_NULL_HANDLE = "setHPyNullHandle";
    public static final String SET_WCHAR_SIZE = "setWcharSize";
    private final GraalHPyContext hpyContext;

    public GraalHPyInitObject(GraalHPyContext hpyContext) {
        this.hpyContext = hpyContext;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(new String[]{SET_HPY_NATIVE_TYPE, SET_HPY_ARRAY_NATIVE_TYPE, SET_HPY_NULL_HANDLE});
    }

    @ExportMessage
    boolean isMemberInvocable(String key) {
        switch (key) {
            case SET_HPY_NATIVE_TYPE:
            case SET_HPY_ARRAY_NATIVE_TYPE:
            case SET_HPY_NULL_HANDLE:
            case SET_WCHAR_SIZE:
                return true;
        }
        return false;
    }

    @ExportMessage
    Object invokeMember(String key, Object[] arguments,
                    @Cached HPyEnsureHandleNode ensureHandleNode) throws UnsupportedMessageException, ArityException {

        if (arguments.length != 1) {
            throw ArityException.create(1, arguments.length);
        }

        switch (key) {
            case SET_HPY_NATIVE_TYPE:
                hpyContext.setHPyNativeType(arguments[0]);
                return 0;
            case SET_HPY_ARRAY_NATIVE_TYPE:
                hpyContext.setHPyArrayNativeType(arguments[0]);
                return 0;
            case SET_HPY_NULL_HANDLE:
                hpyContext.setNullHandle(ensureHandleNode.execute(hpyContext, arguments[0]));
                return 0;
            case SET_WCHAR_SIZE:
                hpyContext.setWcharSize(((Number) arguments[0]).longValue());
                return 0;
        }
        throw UnsupportedMessageException.create();
    }

}
