/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.nodes.subscript;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = __SETITEM__)
@NodeChild(value = "primary", type = ExpressionNode.class)
@NodeChild(value = "slice", type = ExpressionNode.class)
@NodeChild(value = "right", type = ExpressionNode.class)
public abstract class SetItemNode extends StatementNode implements WriteNode {
    @Child private PRaiseNode raiseNode;

    public abstract ExpressionNode getPrimary();

    public abstract ExpressionNode getSlice();

    public abstract ExpressionNode getRight();

    public static SetItemNode create(ExpressionNode primary, ExpressionNode slice, ExpressionNode right) {
        return SetItemNodeGen.create(primary, slice, right);
    }

    public static SetItemNode create() {
        return SetItemNodeGen.create(null, null, null);
    }

    @Override
    public ExpressionNode getRhs() {
        return getRight();
    }

    @Override
    public void doWrite(VirtualFrame frame, boolean value) {
        executeWith(frame, getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public void doWrite(VirtualFrame frame, int value) {
        executeWith(frame, getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public void doWrite(VirtualFrame frame, long value) {
        executeWith(frame, getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public void doWrite(VirtualFrame frame, double value) {
        executeWith(frame, getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public void doWrite(VirtualFrame frame, Object value) {
        executeWith(frame, getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    public void executeWith(VirtualFrame frame, Object value) {
        doWrite(frame, value);
    }

    public abstract void executeWith(VirtualFrame frame, Object primary, Object slice, boolean value);

    public abstract void executeWith(VirtualFrame frame, Object primary, Object slice, int value);

    public abstract void executeWith(VirtualFrame frame, Object primary, Object slice, long value);

    public abstract void executeWith(VirtualFrame frame, Object primary, Object slice, double value);

    public abstract void executeWith(VirtualFrame frame, Object primary, Object slice, Object value);

    @Specialization
    void doIntIndex(VirtualFrame frame, Object primary, int index, Object value,
                    @Cached GetClassNode getClassNode,
                    @Cached("create(__SETITEM__)") LookupSpecialMethodNode lookupSetitem,
                    @Cached CallTernaryMethodNode callSetitem) {
        Object setitem = lookupSetitem.execute(frame, getClassNode.execute(primary), primary);
        if (setitem == PNone.NO_VALUE) {
            getRaiseNode().raise(TypeError, ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, primary);
        }
        callSetitem.execute(frame, setitem, primary, index, value);
    }

    @Specialization(replaces = "doIntIndex")
    void doGeneric(VirtualFrame frame, Object primary, Object index, Object value,
                    @Cached GetClassNode getClassNode,
                    @Cached("create(__SETITEM__)") LookupSpecialMethodNode lookupSetitem,
                    @Cached CallTernaryMethodNode callSetitem) {
        Object setitem = lookupSetitem.execute(frame, getClassNode.execute(primary), primary);
        if (setitem == PNone.NO_VALUE) {
            getRaiseNode().raise(TypeError, ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, primary);
        }
        callSetitem.execute(frame, setitem, primary, index, value);
    }

    private PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }
}
