/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;

/**
 * Wrapper around {@link BytecodeDSLCodeUnit} that can lazily initialize the corresponding root
 * node.
 * <p>
 * We cannot just use {@code @Cached} argument to cache the {@link PBytecodeDSLRootNode} created
 * from the {@link BytecodeDSLCodeUnit}, because Truffle DSL permits races, and it could be
 * initialized multiple times, which we must avoid to 1) not fill inline caches unnecessarily, 2)
 * make use of the fact that comprehensions cannot change, so that we can invoke them using
 * {@code DirectCallNode} without inline cache.
 */
public final class BytecodeDSLCodeUnitAndRoot {
    private final BytecodeDSLCodeUnit codeUnit;
    // updated via VarHandle
    private PBytecodeDSLRootNode rootNode;

    public BytecodeDSLCodeUnitAndRoot(BytecodeDSLCodeUnit codeUnit) {
        this.codeUnit = codeUnit;
    }

    public BytecodeDSLCodeUnit getCodeUnit() {
        return codeUnit;
    }

    public PBytecodeDSLRootNode getRootNode(PBytecodeDSLRootNode outerRootNode) {
        PBytecodeDSLRootNode existing = rootNode;
        if (existing != null) {
            return existing;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PBytecodeDSLRootNode created = codeUnit.createRootNode(PythonContext.get(outerRootNode), outerRootNode.getSource());
        PBytecodeDSLRootNode prev = (PBytecodeDSLRootNode) ROOT_HANDLE.compareAndExchangeRelease(this, null, created);
        return prev != null ? prev : created;
    }

    private static final VarHandle ROOT_HANDLE;
    static {
        try {
            ROOT_HANDLE = MethodHandles.lookup().findVarHandle(BytecodeDSLCodeUnitAndRoot.class, "rootNode", PBytecodeDSLRootNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
