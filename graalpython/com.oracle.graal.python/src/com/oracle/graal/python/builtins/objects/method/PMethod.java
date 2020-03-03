/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.method;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(PythonObjectLibrary.class)
public final class PMethod extends PythonBuiltinObject {

    private final Object function;
    private final Object self;

    public PMethod(LazyPythonClass cls, Object self, Object function) {
        super(cls);
        this.self = self;
        this.function = function;
    }

    public Object getFunction() {
        return function;
    }

    public Object getSelf() {
        return self;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "<method '" + function + "' of " + self + " object at " + function.hashCode() + ">";
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isCallable() {
        return true;
    }

    @ExportMessage
    protected SourceSection getSourceLocation(@CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.getSourceLocation(function);
    }

    @ExportMessage
    protected boolean hasSourceLocation(@CachedLibrary(limit = "1") InteropLibrary lib) {
        return lib.hasSourceLocation(function);
    }
}
