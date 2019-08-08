/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.classes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = "read_class_member")
public abstract class ReadClassAttributeNode extends ExpressionNode implements ReadLocalNode {
    private final String identifier;
    private final boolean isFreeVar;

    @Child private ExpressionNode getNsItem;
    @Child private ExpressionNode readGlobal;
    @Child private ExpressionNode readCellLocal;

    ReadClassAttributeNode(String identifier, FrameSlot cellSlot, boolean isFreeVar) {
        this.identifier = identifier;
        this.isFreeVar = isFreeVar;

        NodeFactory factory = PythonLanguage.getCurrent().getNodeFactory();
        ReadIndexedArgumentNode namespace = ReadIndexedArgumentNode.create(0);

        if (cellSlot != null) {
            this.readCellLocal = factory.createReadLocalCell(cellSlot, isFreeVar);
        }
        this.getNsItem = factory.createGetItem(namespace.asExpression(), this.identifier);
        this.readGlobal = factory.createReadGlobalOrBuiltinScope(this.identifier);
    }

    public static ReadClassAttributeNode create(String name, FrameSlot cellSlot, boolean isFreeVar) {
        return ReadClassAttributeNodeGen.create(name, cellSlot, isFreeVar);
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    public StatementNode makeDeleteNode() {
        return DeleteClassAttributeNode.create(identifier);
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        // freevars pass through the special Class scope
        if (readCellLocal != null && !isFreeVar) {
            return new WriteClassAttributeCellNode((ReadNode) readCellLocal, (ReadNode) getNsItem, rhs);
        } else {
            // assignments always got to the innermost scope
            return ((ReadNode) getNsItem).makeWriteNode(rhs);
        }
    }

    @Specialization
    Object read(VirtualFrame frame,
                    @Cached("create()") GetItemNode getItemNode) {
        try {
            return getNsItem.execute(frame);
        } catch (PException pe) {
            // class namespace overrides closure
            PFrame pFrame = PArguments.getCurrentFrameInfo(frame).getPyFrame();
            if (pFrame != null) {
                Object localsDict = pFrame.getLocalsDict();
                if (localsDict != null) {
                    try {
                        return getItemNode.execute(frame, localsDict, identifier);
                    } catch (PException e) {
                    }
                }
            }

            // read from closure
            if (readCellLocal != null) {
                return readCellLocal.execute(frame);
            }

            // read global or builtin
            return readGlobal.execute(frame);
        }
    }

    private static class WriteClassAttributeCellNode extends StatementNode implements WriteNode {
        @Child private WriteNode writeCellLocal;
        @Child private WriteNode writeNsItem;
        @Child private ExpressionNode right;

        WriteClassAttributeCellNode(ReadNode readCellLocal, ReadNode getNsItem, ExpressionNode rhs) {
            writeCellLocal = (WriteNode) readCellLocal.makeWriteNode(null);
            writeNsItem = (WriteNode) getNsItem.makeWriteNode(null);
            right = rhs;
        }

        public ExpressionNode getRhs() {
            return right;
        }

        public void doWrite(VirtualFrame frame, Object value) {
            writeCellLocal.doWrite(frame, value);
            writeNsItem.doWrite(frame, value);
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            Object value = right.execute(frame);
            doWrite(frame, value);
        }
    }
}
