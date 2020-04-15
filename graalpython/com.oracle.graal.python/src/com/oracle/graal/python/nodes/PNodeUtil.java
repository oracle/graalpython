/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.nodes;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

public class PNodeUtil {

    @SuppressWarnings("unchecked")
    public static <T> T getParentFor(PNode child, Class<T> parentClass) {
        if (parentClass.isInstance(child)) {
            throw new IllegalArgumentException();
        }

        Node current = child.getParent();
        while (!(current instanceof RootNode)) {
            if (parentClass.isInstance(current)) {
                return (T) current;
            }

            current = current.getParent();
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException();
    }

    public static List<ExpressionNode> getListOfSubExpressionsInOrder(PNode root) {
        List<ExpressionNode> expressions = new ArrayList<>();

        root.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof PNode) {
                    PNode pnode = (PNode) node;

                    for (Node child : pnode.getChildren()) {
                        if (child != null && (child instanceof ExpressionNode)) {
                            expressions.add((ExpressionNode) child);
                        }
                    }
                }
                return true;
            }
        });

        return expressions;
    }

    public static void clearSourceSections(PNode node) {
        node.clearSourceSection();
        for (Node c : node.getChildren()) {
            if (c instanceof PNode) {
                PNode child = (PNode) c;
                child.clearSourceSection();
                clearSourceSections(child);
            }
        }
    }

    public static <T extends PNode> T replace(PNode oldNode, T node) {
        node.assignSourceSection(oldNode.getSourceSection());
        return oldNode.replace(node);
    }
}
