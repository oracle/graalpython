/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.buffer;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;

/**
 * Helper node for using {@link PythonBufferAcquireLibrary} and {@link PythonBufferAccessLibrary} in
 * a node annotated with {@link com.oracle.truffle.api.dsl.GenerateUncached}.<br/>
 * In order to correctly use {@link PythonBufferAcquireLibrary} and
 * {@link PythonBufferAccessLibrary}, one needs to se tup an indirect call. Following methods of the
 * library already do that but the caller needs to provide appropriate nodes.
 * <ul>
 * <li>
 * {@link PythonBufferAcquireLibrary#acquireReadonly(Object, VirtualFrame, PNodeWithRaiseAndIndirectCall)}
 * </li>
 * <li>
 * {@link PythonBufferAcquireLibrary#acquireReadonly(Object, VirtualFrame, PythonContext, PythonLanguage, IndirectCallNode)}
 * </li>
 * <li>
 * {@link PythonBufferAcquireLibrary#acquireWritable(Object, VirtualFrame, PNodeWithRaiseAndIndirectCall)}
 * </li>
 * <li>
 * {@link PythonBufferAcquireLibrary#acquireWritable(Object, VirtualFrame, PythonContext, PythonLanguage, IndirectCallNode)}
 * </li>
 * <li>
 * {@link PythonBufferAccessLibrary#release(Object, VirtualFrame, PNodeWithRaiseAndIndirectCall)}</li>
 * <li>
 * {@link PythonBufferAccessLibrary#release(Object, VirtualFrame, PythonContext, PythonLanguage, IndirectCallNode)}
 * </li>
 * </ul>
 * However, if the caller is a node with annotation
 * {@link com.oracle.truffle.api.dsl.GenerateUncached}, you cannot easily use one of these methods
 * because the nodes you need to pass must (1) implement {@link IndirectCallNode}, and (2) be an
 * ancestor of the library. Since {@link IndirectCallNode} requires the usage of Java fields, you
 * cannot use {@link com.oracle.truffle.api.dsl.GenerateUncached}. This node solves the problem by
 * providing an uncached version that fulfills the required interface.
 */
public abstract class BufferAcquireGenerateUncachedNode extends PNodeWithContext implements IndirectCallNode {

    public abstract boolean hasBuffer(Object receiver);

    public abstract Object acquireReadonly(VirtualFrame frame, Object receiver);

    public abstract Object acquireWritable(VirtualFrame frame, Object receiver);

    public abstract PythonBufferAccessLibrary getAccessLib();

    public abstract void release(VirtualFrame frame, Object receiver);

    public static BufferAcquireGenerateUncachedNode create(int limit) {
        return new IndirectCallHelperCachedNode(limit);
    }

    public static BufferAcquireGenerateUncachedNode getUncached(@SuppressWarnings("unused") int limit) {
        return IndirectCallHelperUncachedNode.INSTANCE;
    }

    static final class IndirectCallHelperCachedNode extends BufferAcquireGenerateUncachedNode {

        @Child private PythonBufferAcquireLibrary lib;
        @Child private PythonBufferAccessLibrary accessLib;

        private final int limit;

        @CompilationFinal private Assumption nativeCodeDoesntNeedExceptionState;
        @CompilationFinal private Assumption nativeCodeDoesntNeedMyFrame;

        IndirectCallHelperCachedNode(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean hasBuffer(Object receiver) {
            return lib.hasBuffer(receiver);
        }

        @Override
        public Object acquireReadonly(VirtualFrame frame, Object receiver) {
            return ensureAcquireLib().acquireReadonly(receiver, frame, getContext(), getLanguage(), this);
        }

        @Override
        public Object acquireWritable(VirtualFrame frame, Object receiver) {
            return ensureAcquireLib().acquireWritable(receiver, frame, getContext(), getLanguage(), this);
        }

        @Override
        public void release(VirtualFrame frame, Object receiver) {
            getAccessLib().release(receiver, frame, getContext(), getLanguage(), this);
        }

        @Override
        public Assumption needNotPassFrameAssumption() {
            if (nativeCodeDoesntNeedMyFrame == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeCodeDoesntNeedMyFrame = Truffle.getRuntime().createAssumption();
            }
            return nativeCodeDoesntNeedMyFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            if (nativeCodeDoesntNeedExceptionState == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeCodeDoesntNeedExceptionState = Truffle.getRuntime().createAssumption();
            }
            return nativeCodeDoesntNeedExceptionState;
        }

        private PythonBufferAcquireLibrary ensureAcquireLib() {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonBufferAcquireLibrary.getFactory().createDispatched(limit));
            }
            return lib;
        }

        @Override
        public PythonBufferAccessLibrary getAccessLib() {
            if (accessLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                accessLib = insert(PythonBufferAccessLibrary.getFactory().createDispatched(limit));
            }
            return accessLib;
        }
    }

    static final class IndirectCallHelperUncachedNode extends BufferAcquireGenerateUncachedNode {
        private static final IndirectCallHelperUncachedNode INSTANCE = new IndirectCallHelperUncachedNode();

        @Override
        public boolean hasBuffer(Object receiver) {
            return PythonBufferAcquireLibrary.getUncached().hasBuffer(receiver);
        }

        @Override
        public Object acquireReadonly(VirtualFrame frame, Object receiver) {
            return PythonBufferAcquireLibrary.getUncached().acquireReadonly(receiver);
        }

        @Override
        public Object acquireWritable(VirtualFrame frame, Object receiver) {
            return PythonBufferAcquireLibrary.getUncached().acquireWritable(receiver);
        }

        @Override
        public void release(VirtualFrame frame, Object receiver) {
            PythonBufferAccessLibrary.getUncached().release(receiver);
        }

        @Override
        public PythonBufferAccessLibrary getAccessLib() {
            return PythonBufferAccessLibrary.getUncached();
        }

        @Override
        public Assumption needNotPassFrameAssumption() {
            return Assumption.NEVER_VALID;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return Assumption.NEVER_VALID;
        }

        @Override
        public boolean calleeNeedsCallerFrame() {
            return true;
        }

        @Override
        public boolean calleeNeedsExceptionState() {
            return true;
        }

        @Override
        public void setCalleeNeedsCallerFrame() {
        }

        @Override
        public void setCalleeNeedsExceptionState() {
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }
    }
}
