/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.arrow;

import com.oracle.graal.python.nodes.arrow.vector.VectorArrowArrayReleaseCallback;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public class ArrowVectorSupport {

    private final String FIXED_WIDTH_VECTOR_PATH = "org.apache.arrow.vector.BaseFixedWidthVector";

    public final boolean isJavaArrowImplOnClassPath;
    public final Class<?> baseFixedWidthVectorClass;
    private final PythonContext ctx;

    private Object vectorArrowArrayNFIClosure;
    @CompilationFinal private long vectorArrowArrayReleaseCallback;

    public ArrowVectorSupport(PythonContext ctx) {
        this.ctx = ctx;
        Class<?> baseFixedWidthVectorClass;
        try {
            baseFixedWidthVectorClass = Class.forName(FIXED_WIDTH_VECTOR_PATH);
        } catch (ClassNotFoundException e) {
            baseFixedWidthVectorClass = null;
        }
        this.baseFixedWidthVectorClass = baseFixedWidthVectorClass;
        this.isJavaArrowImplOnClassPath = baseFixedWidthVectorClass != null;
    }

    public boolean isFixedWidthVector(Object vector) {
        if (!isJavaArrowImplOnClassPath) {
            return false;
        }
        var env = ctx.getEnv();
        if (env.isHostObject(vector)) {
            Object hostVector = env.asHostObject(vector);
            return baseFixedWidthVectorClass.isInstance(hostVector);
        }

        return false;
    }

    @TruffleBoundary
    private void initVectorArrowArrayReleaseCallback() {
        CompilerAsserts.neverPartOfCompilation();
        var signature = ArrowUtil.createNfiSignature("(UINT64):VOID", ctx);
        var executable = new VectorArrowArrayReleaseCallback();
        this.vectorArrowArrayNFIClosure = SignatureLibrary.getUncached().createClosure(signature, executable);
        this.vectorArrowArrayReleaseCallback = PythonUtils.coerceToLong(vectorArrowArrayNFIClosure, InteropLibrary.getUncached());
    }

    public long getVectorArrowArrayReleaseCallback() {
        if (vectorArrowArrayReleaseCallback == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initVectorArrowArrayReleaseCallback();
        }
        return vectorArrowArrayReleaseCallback;
    }
}
