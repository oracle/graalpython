/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser;

import com.oracle.graal.python.pegparser.sst.SSTNode;
import java.util.Arrays;

final class SymbolList {
    private SSTNode[] nodes;
    private ExprContext[] contexts;
    private int size;

    SymbolList() {
        nodes = new SSTNode[16];
        contexts = new ExprContext[nodes.length];
        size = 0;
    }

    private SymbolList(SSTNode[] nodes, ExprContext[] contexts, int size) {
        this.nodes = nodes;
        this.contexts = contexts;
        this.size = size;
    }

    int size() {
        return size;
    }

    SSTNode getNode(int i) {
        return nodes[i];
    }

    ExprContext getContext(int i) {
        return contexts[i];
    }

    void push(SSTNode node, ExprContext context) {
        if (size == nodes.length) {
            nodes = Arrays.copyOf(nodes, nodes.length << 1);
            contexts = Arrays.copyOf(contexts, nodes.length);
        }
        nodes[size++] = node;
        contexts[size] = context;
    }

    void pop(int cnt) {
        reset(size - cnt);
    }

    void reset(int toSize) {
        // we ignore the contexs, there's no leak there. we also never shrink
        Arrays.fill(nodes, toSize, size, null);
        size = toSize;
    }

    SymbolList consume(int toSize) {
        SSTNode[] consumedNodes = Arrays.copyOfRange(nodes, toSize, size);
        ExprContext[] consumedContexts = Arrays.copyOfRange(contexts, toSize, size);
        SymbolList result = new SymbolList(consumedNodes, consumedContexts, size - toSize);
        reset(toSize);
        return result;
    }
}
