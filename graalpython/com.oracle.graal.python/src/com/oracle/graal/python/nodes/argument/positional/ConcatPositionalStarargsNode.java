/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.argument.positional;

import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class ConcatPositionalStarargsNode extends ExpressionNode {
    @Children final ExpressionNode[] iterables;

    protected ConcatPositionalStarargsNode(ExpressionNode... iterables) {
        assert iterables.length > 1;
        this.iterables = iterables;
    }

    @Specialization
    @ExplodeLoop
    Object concat(VirtualFrame frame,
                    @Cached ListNodes.ConstructListNode constructListNode,
                    @Cached("createExtends()") ListBuiltins.ListExtendNode[] extendNode) {
        PList result = constructListNode.execute(iterables[0].execute(frame));
        for (int i = 1; i < iterables.length; i++) {
            extendNode[i - 1].execute(frame, result, iterables[i].execute(frame));
        }
        return result;
    }

    protected ListBuiltins.ListExtendNode[] createExtends() {
        ListBuiltins.ListExtendNode[] nodes = new ListBuiltins.ListExtendNode[iterables.length - 1];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = ListBuiltins.ListExtendNode.create();
        }
        return nodes;
    }

    public static ConcatPositionalStarargsNode create(ExpressionNode... iterables) {
        return ConcatPositionalStarargsNodeGen.create(iterables);
    }
}
