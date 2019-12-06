/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;

public interface IndirectCallNode extends NodeInterface {
    abstract Assumption needNotPassFrameAssumption();

    abstract Assumption needNotPassExceptionAssumption();

    default boolean calleeNeedsCallerFrame() {
        return !needNotPassFrameAssumption().isValid();
    }

    default boolean calleeNeedsExceptionState() {
        return !needNotPassExceptionAssumption().isValid();
    }

    default void setCalleeNeedsCallerFrame() {
        needNotPassFrameAssumption().invalidate();
    }

    default void setCalleeNeedsExceptionState() {
        needNotPassExceptionAssumption().invalidate();
    }

    /**
     * Finds the parent of {@code callNode} that is an {@link IndirectCallNode}
     * and marks it so it will pass the state via the context the next time.
     *
     * @return {@code true} if the marking was successful, {@code false} otherwise
     */
    public static boolean setEncapsulatingNeedsToPassCallerFrame(final Node callNode) {
        Node pythonCallNode = callNode;
        while (pythonCallNode != null) {
            if (pythonCallNode instanceof IndirectCallNode) {
                final IndirectCallNode indirectCallNode = (IndirectCallNode) pythonCallNode;
                indirectCallNode.setCalleeNeedsCallerFrame();
                return true;
            }
            pythonCallNode = pythonCallNode.getParent();
        }
        return false;
    }

    public static void setEncapsulatingNeedsToPassExceptionState(Node callNode) {
        Node pythonCallNode = callNode;
        while (pythonCallNode != null) {
            if (pythonCallNode instanceof IndirectCallNode) {
                ((IndirectCallNode) pythonCallNode).setCalleeNeedsExceptionState();
                break;
            }
            pythonCallNode = pythonCallNode.getParent();
        }
    }
}
