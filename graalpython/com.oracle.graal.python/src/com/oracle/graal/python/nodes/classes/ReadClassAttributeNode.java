/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.cell.ReadLocalCellNode;
import com.oracle.graal.python.nodes.cell.WriteLocalCellNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.AccessNameNode;
import com.oracle.graal.python.nodes.frame.DeleteNameNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.frame.WriteNameNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * This node implements all {@code LOAD_*} bytecodes that can occur in CPython in class bodies. They
 * are not separated for us due to the way that our parser constructs
 * {@link com.oracle.graal.python.nodes.frame.ReadNode ReadNodes} first and then converts them into
 * {@link com.oracle.graal.python.nodes.frame.WriteNode WriteNodes} later. Since writes in the class
 * body go to the class scope, never to an outer cell, these are different than just using a
 * {@link ReadLocalCellNode} or {@link com.oracle.graal.python.nodes.frame.ReadNameNode
 * ReadNameNode} and using their transformation. The specializations of this node then implement the
 * equivalent of the bytecodes {@code LOAD_DEREF}, {@code LOAD_GLOBAL}, {@code LOAD_NAME}, and
 * {@code
 * LOAD_CLASSDEREF} from CPython.
 */
@NodeInfo(shortName = "read_class_member")
public abstract class ReadClassAttributeNode extends ExpressionNode implements ReadLocalNode, AccessNameNode {
    protected final TruffleString identifier;
    protected final boolean isFreeVar;
    protected final Integer cellSlot;

    ReadClassAttributeNode(TruffleString identifier, Integer cellSlot, boolean isFreeVar) {
        this.identifier = identifier;
        this.isFreeVar = isFreeVar;
        this.cellSlot = cellSlot;
    }

    public static ReadClassAttributeNode create(TruffleString name, Integer cellSlot, boolean isFreeVar) {
        return ReadClassAttributeNodeGen.create(name, cellSlot, isFreeVar);
    }

    @Override
    public TruffleString getAttributeId() {
        return identifier;
    }

    @Override
    public StatementNode makeDeleteNode() {
        return DeleteNameNode.create(identifier);
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        // freevars pass through the special Class scope
        if (cellSlot != null && !isFreeVar) {
            return new WriteClassAttributeCellNode(identifier, cellSlot, rhs);
        } else {
            // assignments always got to the innermost scope
            return WriteNameNode.create(identifier, rhs);
        }
    }

    // TODO: (tfel) LOAD_DEREF doesn't really occur in CPython in classes, they use LOAD_CLASSDEREF
    // which asserts that there are custom locals, so I'm not sure this can happen. Keeping it for
    // now, since we had it before, need to investigate further.
    @Specialization(guards = {"!hasLocals(frame)", "cellSlot != null"})
    protected Object loadDeref(VirtualFrame frame,
                    @Shared("readCell") @Cached("create(cellSlot, isFreeVar)") ReadLocalCellNode readCell) {
        return readCell.execute(frame);
    }

    @Specialization(guards = {"!hasLocals(frame)", "cellSlot == null"})
    protected Object loadGlobal(VirtualFrame frame,
                    @Shared("readGlobal") @Cached("create(identifier)") ReadGlobalOrBuiltinNode readGlobal) {
        return readGlobal.execute(frame);
    }

    protected static HashingStorage getLocalsStorage(VirtualFrame frame) {
        return ((PDict) PArguments.getSpecialArgument(frame)).getDictStorage();
    }

    @Specialization(guards = {"hasLocalsDict(frame)", "cellSlot != null"})
    protected Object loadClassDerefFast(VirtualFrame frame,
                    @Shared("dictGetItem") @Cached HashingStorageGetItem getItem,
                    @Shared("readCell") @Cached("create(cellSlot, isFreeVar)") ReadLocalCellNode readCell) {
        Object result = getItem.execute(frame, getLocalsStorage(frame), identifier);
        if (result == null) {
            return readCell.execute(frame);
        } else {
            return result;
        }
    }

    @Specialization(guards = {"hasLocalsDict(frame)", "cellSlot == null"})
    protected Object loadNameFast(VirtualFrame frame,
                    @Shared("dictGetItem") @Cached HashingStorageGetItem getItem,
                    @Shared("readGlobal") @Cached("create(identifier)") ReadGlobalOrBuiltinNode readGlobal) {
        Object result = getItem.execute(frame, getLocalsStorage(frame), identifier);
        if (result == null) {
            return readGlobal.execute(frame);
        } else {
            return result;
        }
    }

    @Specialization(guards = {"hasLocals(frame)", "cellSlot != null"}, replaces = "loadClassDerefFast")
    protected Object loadClassDeref(VirtualFrame frame,
                    @Shared("profile") @Cached IsBuiltinClassProfile keyErrorProfile,
                    @Shared("getItem") @Cached GetItemNode getItem,
                    @Shared("readCell") @Cached("create(cellSlot, isFreeVar)") ReadLocalCellNode readCell) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        try {
            return getItem.execute(frame, frameLocals, identifier);
        } catch (PException e) {
            e.expect(KeyError, keyErrorProfile);
            return readCell.execute(frame);
        }
    }

    @Specialization(guards = {"hasLocals(frame)", "cellSlot == null"}, replaces = "loadNameFast")
    protected Object loadName(VirtualFrame frame,
                    @Shared("profile") @Cached IsBuiltinClassProfile keyErrorProfile,
                    @Shared("getItem") @Cached GetItemNode getItem,
                    @Shared("readGlobal") @Cached("create(identifier)") ReadGlobalOrBuiltinNode readGlobal) {
        Object frameLocals = PArguments.getSpecialArgument(frame);
        try {
            return getItem.execute(frame, frameLocals, identifier);
        } catch (PException e) {
            e.expect(KeyError, keyErrorProfile);
            return readGlobal.execute(frame);
        }
    }

    // (tfel) There is no equivalent bytecode for this behaviour in CPython, they determine in the
    // compiler if something should use STORE_NAME or STORE_DEREF. It may be good to make this more
    // similar in the future.
    private static final class WriteClassAttributeCellNode extends StatementNode implements WriteNode {
        @Child WriteNameNode writeName;
        @Child WriteLocalCellNode writeCell;
        @Child ExpressionNode rhs;

        WriteClassAttributeCellNode(TruffleString identifier, int cellSlot, ExpressionNode rhs) {
            writeName = WriteNameNode.create(identifier, rhs);
            writeCell = WriteLocalCellNode.create(cellSlot, ReadLocalVariableNode.create(cellSlot), rhs);
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            writeName.executeVoid(frame);
            writeCell.executeVoid(frame);
        }

        @Override
        public ExpressionNode getRhs() {
            return rhs;
        }

        @Override
        public void executeBoolean(VirtualFrame frame, boolean value) {
            writeName.executeBoolean(frame, value);
            writeCell.executeBoolean(frame, value);
        }

        @Override
        public void executeInt(VirtualFrame frame, int value) {
            writeName.executeInt(frame, value);
            writeCell.executeInt(frame, value);
        }

        @Override
        public void executeLong(VirtualFrame frame, long value) {
            writeName.executeLong(frame, value);
            writeCell.executeLong(frame, value);
        }

        @Override
        public void executeDouble(VirtualFrame frame, double value) {
            writeName.executeDouble(frame, value);
            writeCell.executeDouble(frame, value);
        }

        @Override
        public void executeObject(VirtualFrame frame, Object value) {
            writeName.executeObject(frame, value);
            writeCell.executeObject(frame, value);
        }
    }
}
