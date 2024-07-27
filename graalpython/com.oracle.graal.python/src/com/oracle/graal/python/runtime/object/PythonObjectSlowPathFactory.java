/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.object;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.object.Shape;

/**
 * A subclass of {@link PythonObjectFactory} which is basically an uncached version of it but
 * directly stores a reference to the {@link AllocationReporter} instead of doing a context lookup
 * and getting it from the context. This class is meant to be used on slow path where the context is
 * explicitly available.
 *
 * Objects of this class should not be created directly, but retrieved from
 * {@link com.oracle.graal.python.builtins.Python3Core}. Note that
 * {@link PythonObjectSlowPathFactory} is context dependent object. It must not be stored in AST or
 * in {@link com.oracle.graal.python.PythonLanguage}, for example.
 */
public final class PythonObjectSlowPathFactory extends PythonObjectFactory {

    private final AllocationReporter reporter;
    private final PythonLanguage language;
    private final SlowPathGetInstanceShapeNode getInstanceShapeNode;

    public PythonObjectSlowPathFactory(AllocationReporter reporter, PythonLanguage language) {
        this.reporter = Objects.requireNonNull(reporter);
        this.language = language;
        this.getInstanceShapeNode = new SlowPathGetInstanceShapeNode(language);
    }

    @Override
    public PythonLanguage getLanguage() {
        return language;
    }

    @TruffleBoundary
    @Override
    protected AllocationReporter executeTrace(Object arg0Value, long arg1Value) {
        assert PythonContext.get(null).getAllocationReporter() == reporter;
        return PythonObjectFactory.doTrace(arg0Value, arg1Value, reporter);
    }

    @TruffleBoundary
    @Override
    protected Shape executeGetShape(Object arg0Value, boolean arg1Value) {
        return getInstanceShapeNode.execute(arg0Value);
    }

    @Override
    public boolean isAdoptable() {
        return false;
    }

}
