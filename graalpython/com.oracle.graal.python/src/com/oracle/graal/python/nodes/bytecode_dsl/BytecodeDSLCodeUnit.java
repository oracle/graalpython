/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode_dsl;

import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public final class BytecodeDSLCodeUnit extends CodeUnit {
    public final int classcellIndex;
    public final int selfIndex;
    public final int yieldFromGeneratorIndex;
    public final int instrumentationDataIndex;
    private final BytecodeSupplier supplier;

    public BytecodeDSLCodeUnit(TruffleString name, TruffleString qualname, int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int flags, TruffleString[] names, TruffleString[] varnames,
                    TruffleString[] cellvars, TruffleString[] freevars, int[] cell2arg, Object[] constants, int startLine, int startColumn, int endLine, int endColumn,
                    int classcellIndex, int selfIndex, int yieldFromGeneratorIndex, int instrumentationDataIndex, BytecodeSupplier supplier) {
        super(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags, names, varnames, cellvars, freevars, cell2arg, constants, startLine, startColumn, endLine, endColumn);
        this.classcellIndex = classcellIndex;
        this.selfIndex = selfIndex;
        this.supplier = supplier;
        this.yieldFromGeneratorIndex = yieldFromGeneratorIndex;
        this.instrumentationDataIndex = instrumentationDataIndex;
    }

    public abstract static class BytecodeSupplier {
        public abstract PBytecodeDSLRootNode createRootNode(PythonContext context, Source source);

        public abstract byte[] createSerializedBytecode(PythonContext context);
    }

    public BytecodeDSLCodeUnit withFlags(int flags) {
        return new BytecodeDSLCodeUnit(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags,
                        names, varnames, cellvars, freevars, cell2arg, constants,
                        startLine, startColumn, endLine, endColumn, classcellIndex, selfIndex, yieldFromGeneratorIndex, instrumentationDataIndex, supplier);
    }

    @TruffleBoundary
    public PBytecodeDSLRootNode createRootNode(PythonContext context, Source source) {
        // We must not cache deserialized root, because the code unit may be shared by multiple
        // engines. The caller is responsible for ensuring the caching of the resulting root node if
        // necessary
        PBytecodeDSLRootNode rootNode = supplier.createRootNode(context, source);
        rootNode.setMetadata(this, null);
        return rootNode;
    }

    public byte[] getSerialized(PythonContext context) {
        CompilerAsserts.neverPartOfCompilation();
        return supplier.createSerializedBytecode(context);
    }

    public TruffleString getDocstring() {
        // The first constant in the code unit is the docstring (if available) or PNone.
        if (constants.length > 0 && constants[0] instanceof TruffleString docstring) {
            return docstring;
        }
        return null;
    }

    @Override
    protected void dumpBytecode(StringBuilder sb, boolean optimized, RootNode rootNode) {
        if (rootNode instanceof PBytecodeDSLRootNode dslRoot) {
            sb.append(dslRoot.dump());
            sb.append('\n');
        } else {
            sb.append("bytecode not available\n");
        }
    }
}
