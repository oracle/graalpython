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
package com.oracle.graal.python.nodes.bytecode_dsl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins.PBytecodeDSLSerializer;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public final class BytecodeDSLCodeUnit extends CodeUnit {
    /*
     * A {@link BytecodeDSLCodeUnit} is a context-independent representation of a root node. It
     * contains the bytes produced from Bytecode DSL serialization.
     *
     * Since it is expensive to serialize every root node, we perform serialization lazily using the
     * {@link BytecodeNodes} produced during parsing.
     *
     * When this code unit is directly instantiated via unmarshaling, there is no {@link
     * BytecodeNodes}; instead, we store the serialized bytes directly.
     */
    private volatile byte[] serialized;
    private final BytecodeRootNodes<PBytecodeDSLRootNode> nodes;
    public final int classcellIndex;
    public final int selfIndex;

    public BytecodeDSLCodeUnit(TruffleString name, TruffleString qualname, int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int flags, TruffleString[] names, TruffleString[] varnames,
                    TruffleString[] cellvars, TruffleString[] freevars, int[] cell2arg, Object[] constants, int startLine, int startColumn, int endLine, int endColumn,
                    int classcellIndex, int selfIndex, byte[] serialized, BytecodeRootNodes<PBytecodeDSLRootNode> nodes) {
        super(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags, names, varnames, cellvars, freevars, cell2arg, constants, startLine, startColumn, endLine, endColumn);
        // Only one of these fields should be set. The other gets computed dynamically.
        assert nodes == null || nodes.count() == 1;
        assert serialized == null ^ nodes == null;
        this.serialized = serialized;
        this.nodes = nodes;
        this.classcellIndex = classcellIndex;
        this.selfIndex = selfIndex;
    }

    public BytecodeDSLCodeUnit withFlags(int flags) {
        return new BytecodeDSLCodeUnit(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags,
                        names, varnames, cellvars, freevars, cell2arg, constants,
                        startLine, startColumn, endLine, endColumn, classcellIndex, selfIndex, serialized, nodes);
    }

    @TruffleBoundary
    public PBytecodeDSLRootNode createRootNode(PythonContext context, Source source) {
        if (nodes != null) {
            return nodes.getNode(0);
        }
        // We must not cache deserialized root, because the code unit may be shared by multiple
        // engines. The caller is responsible for ensuring the caching of the resulting root node if
        // necessary
        byte[] toDeserialize = getSerialized(context);
        BytecodeRootNodes<PBytecodeDSLRootNode> deserialized = MarshalModuleBuiltins.deserializeBytecodeNodes(context, source, toDeserialize);
        assert deserialized.count() == 1;
        PBytecodeDSLRootNode result = deserialized.getNode(0);
        result.setMetadata(this, null);
        return result;
    }

    public byte[] getSerialized(PythonContext context) {
        CompilerAsserts.neverPartOfCompilation();
        byte[] result = serialized;
        if (result == null) {
            synchronized (this) {
                result = serialized;
                if (result == null) {
                    result = serialized = computeSerialized(context);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    private byte[] computeSerialized(PythonContext context) {
        try {
            assert PythonContext.get(null) == context;
            BytecodeSerializer serializer = new PBytecodeDSLSerializer();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            nodes.serialize(new DataOutputStream(bytes), serializer);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
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
        if (nodes == null) {
            if (rootNode instanceof PBytecodeDSLRootNode dslRoot) {
                sb.append(dslRoot.dump());
                sb.append('\n');
            }
            sb.append("bytecode not available\n");
        } else {
            sb.append(nodes.getNode(0).dump());
            sb.append('\n'); // dump does not print newline at the end
        }
    }
}
