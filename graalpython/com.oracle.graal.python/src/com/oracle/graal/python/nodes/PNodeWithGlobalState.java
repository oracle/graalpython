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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class PNodeWithGlobalState extends PNodeWithContext {
    /**
     * Transfers the execution context to the language context.<br>
     * Use this method to make the execution context available to nodes that cannot take a virtual
     * frame at the last location where the virtual frame is available. The recommended usage for
     * nodes implementing this interface is as follows:
     * <p>
     *
     * <pre>
     * {@literal @}Specialization
     * Object doSomething(VirtualFrame frame,
     *                    {@literal @}Cached SomeNodeWithGlobalState node,
     *                    {@literal @}CachedContext(PythonLanguage.class) ContextReference&lt;PythonContext&gt; contextRef) {
     *     try (SomeContextManager cm = node.withGlobalState(contextRef, frame)) {
     *         node.execute();
     *     }
     * </pre>
     * </p>
     */
    public final NodeContextManager withGlobalState(ContextReference<PythonContext> contextRef, VirtualFrame frame) {
        return new NodeContextManager(contextRef.get(), frame, this);
    }

    /**
     * A convenience method that allows to transfer the execution context from the frame to the
     * context.
     *
     * This is mostly useful when using methods annotated with {@code @TruffleBoundary} that again
     * use nodes that would require a frame. Surround the usage of the callee node by a context
     * manager and then it is allowed to pass a {@code null} frame. For example:
     * <p>
     *
     * <pre>
     * public abstract class SomeNode extends Node {
     *     {@literal @}Child private OtherNode otherNode = OtherNode.create();
     *
     *     public abstract Object execute(VirtualFrame frame, Object arg);
     *
     *     {@literal @}Specialization
     *     Object doSomething(VirtualFrame frame, Object arg,
     *                            {@literal @}CachedContext(PythonLanguage.class) ContextReference&lt;PythonContext&gt; contextRef) {
     *         // ...
     *         try (DefaultContextManager cm = PNodeWithGlobalState.transfertToContext(contextRef, frame, this)) {
     *             truffleBoundaryMethod(arg);
     *         }
     *         // ...
     *     }
     *
     *     {@literal @}TruffleBoundary
     *     private void truffleBoundaryMethod(Object arg) {
     *         otherNode.execute(null, arg);
     *     }
     *
     * </pre>
     * </p>
     */
    public static NodeContextManager transferToContext(ContextReference<PythonContext> contextRef, VirtualFrame frame, Node caller) {
        if (frame != null) {
            return new NodeContextManager(contextRef.get(), frame, caller);
        }
        return new NodeContextManager(null, null, null);
    }

    public static final class NodeContextManager implements AutoCloseable {
        private final PythonContext context;
        private final PException savedExceptionState;

        /**
         * @param context - the current context. Can be {@code null}
         * @param frame - the current Python-level frame. Can be {@code null}
         * @param caller - the node causing the Python call. Can be {@code null} if {@code frame} is null.
         */
        public NodeContextManager(PythonContext context, VirtualFrame frame, Node caller) {
            this.context = context;
            if (context != null) {
                this.savedExceptionState = context.getCaughtException();
            } else {
                this.savedExceptionState = null;
            }
            if (frame != null) {
                IndirectCallContext.enter(frame, context, caller);
            }

        }

        public final void close() {
            IndirectCallContext.exit(context, savedExceptionState);
        }
    }
}
