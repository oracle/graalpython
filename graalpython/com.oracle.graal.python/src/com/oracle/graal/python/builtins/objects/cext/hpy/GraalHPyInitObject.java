/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.GilNode.UncachedAcquire;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A simple interop-capable object that is used to initialize the HPy C API.
 */
@ExportLibrary(InteropLibrary.class)
public final class GraalHPyInitObject implements TruffleObject {

    public static final TruffleString T_SET_HPY_CONTEXT_NATIVE_TYPE = tsLiteral("setHPyContextNativeType");
    public static final TruffleString T_SET_HPY_NATIVE_TYPE = tsLiteral("setHPyNativeType");
    public static final TruffleString T_SET_HPYFIELD_NATIVE_TYPE = tsLiteral("setHPyFieldNativeType");
    public static final TruffleString T_SET_HPY_ARRAY_NATIVE_TYPE = tsLiteral("setHPyArrayNativeType");
    public static final TruffleString T_SET_WCHAR_SIZE = tsLiteral("setWcharSize");
    private final GraalHPyContext hpyContext;

    private static final TruffleString[] MEMBERS = new TruffleString[]{T_SET_HPY_CONTEXT_NATIVE_TYPE, T_SET_HPY_NATIVE_TYPE, T_SET_HPYFIELD_NATIVE_TYPE, T_SET_HPY_ARRAY_NATIVE_TYPE, T_SET_WCHAR_SIZE};

    public GraalHPyInitObject(GraalHPyContext hpyContext) {
        this.hpyContext = hpyContext;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(MEMBERS);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberInvocable(String key,
                    @Cached.Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Cached.Shared("eq") @Cached TruffleString.EqualNode eqNode) {
        TruffleString tmember = fromJavaStringNode.execute(key, TS_ENCODING);
        for (TruffleString m : MEMBERS) {
            if (eqNode.execute(tmember, m, TS_ENCODING)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings({"try", "unused"})
    Object invokeMember(String key, Object[] arguments,
                    @Cached.Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                    @Cached.Shared("eq") @Cached TruffleString.EqualNode eqNode) throws UnsupportedMessageException, ArityException {
        try (UncachedAcquire gil = GilNode.uncachedAcquire()) {
            if (arguments.length != 1) {
                throw ArityException.create(1, 1, arguments.length);
            }

            TruffleString tmember = fromJavaStringNode.execute(key, TS_ENCODING);
            if (eqNode.execute(T_SET_HPY_CONTEXT_NATIVE_TYPE, tmember, TS_ENCODING)) {
                hpyContext.setHPyContextNativeType(arguments[0]);
                return 0;
            } else if (eqNode.execute(T_SET_HPY_NATIVE_TYPE, tmember, TS_ENCODING)) {
                hpyContext.setHPyNativeType(arguments[0]);
                return 0;
            } else if (eqNode.execute(T_SET_HPYFIELD_NATIVE_TYPE, tmember, TS_ENCODING)) {
                hpyContext.setHPyFieldNativeType(arguments[0]);
                return 0;
            } else if (eqNode.execute(T_SET_HPY_ARRAY_NATIVE_TYPE, tmember, TS_ENCODING)) {
                hpyContext.setHPyArrayNativeType(arguments[0]);
                return 0;
            } else if (eqNode.execute(T_SET_WCHAR_SIZE, tmember, TS_ENCODING)) {
                hpyContext.setWcharSize(((Number) arguments[0]).longValue());
                return 0;
            }
            throw UnsupportedMessageException.create();
        }
    }
}
