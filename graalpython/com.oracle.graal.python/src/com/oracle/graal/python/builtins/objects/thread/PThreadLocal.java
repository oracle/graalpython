/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.thread;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public final class PThreadLocal extends PythonBuiltinObject {
    private final ThreadLocal<PDict> threadLocalDict;
    private final Object[] args;
    private final PKeyword[] keywords;

    public PThreadLocal(Object cls, Shape instanceShape, Object[] args, PKeyword[] keywords) {
        super(cls, instanceShape);
        threadLocalDict = new ThreadLocal<>();
        this.args = args;
        this.keywords = keywords;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasDict() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    PDict getDict(@CachedLibrary("this") PythonObjectLibrary lib,
                    @Cached CallNode callNode,
                    @Cached PythonObjectFactory factory,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            PDict dict = threadLocalDict.get();
            if (dict == null) {
                dict = factory.createDict();
                threadLocalDict.set(dict);
                Object initMethod = lib.lookupAttribute(this, null, SpecialMethodNames.__INIT__);
                callNode.execute(initMethod, args, keywords);
            }
            return dict;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    void setDict(PDict value, @Shared("raise") @Cached PRaiseNode raise) {
        throw raise.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.ATTR_S_READONLY, "__dict__");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    void deleteDict(@Shared("raise") @Cached PRaiseNode raise) {
        throw raise.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.ATTR_S_READONLY, "__dict__");
    }
}
