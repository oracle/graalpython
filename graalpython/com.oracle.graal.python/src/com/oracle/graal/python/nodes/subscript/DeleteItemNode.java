/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.util.Supplier;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode.NotImplementedHandler;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

// TODO: (tfel) Duplication here with BinaryOpNode
@NodeInfo(shortName = __DELITEM__)
@NodeChild(value = "leftNode", type = ExpressionNode.class)
@NodeChild(value = "rightNode", type = ExpressionNode.class)
public abstract class DeleteItemNode extends StatementNode {
    public abstract ExpressionNode getLeftNode();

    public abstract ExpressionNode getRightNode();

    public abstract Object executeWith(VirtualFrame frame, PythonObject globals, String attributeId);

    // TODO: (tfel) refactor this method (executeWith) into a separate node. Right now this breaks
    // the lengths we go to to avoid boxing :(
    public abstract Object executeWith(VirtualFrame frame, Object left, Object right);

    public int executeInt(VirtualFrame frame, int left, int right) throws UnexpectedResultException {
        return PGuards.expectInteger(executeWith(frame, left, right));
    }

    public double executeDouble(VirtualFrame frame, double left, double right) throws UnexpectedResultException {
        return PGuards.expectDouble(executeWith(frame, left, right));
    }

    private final Supplier<NotImplementedHandler> notImplementedHandler;

    public DeleteItemNode() {
        this.notImplementedHandler = () -> new NotImplementedHandler() {
            @Child private PRaiseNode raiseNode = PRaiseNode.create();

            @Override
            public Object execute(Object arg, Object arg2) {
                throw raiseNode.raise(TypeError, "'%p' object doesn't support item deletion", arg);
            }
        };
    }

    public ExpressionNode getPrimary() {
        return getLeftNode();
    }

    public ExpressionNode getSlice() {
        return getRightNode();
    }

    @Specialization
    Object doObject(VirtualFrame frame, Object primary, Object slice,
                    @Cached("createDelItemNode()") LookupAndCallBinaryNode callDelitemNode) {
        return callDelitemNode.executeObject(frame, primary, slice);
    }

    protected LookupAndCallBinaryNode createDelItemNode() {
        return LookupAndCallBinaryNode.create(SpecialMethodNames.__DELITEM__, null, notImplementedHandler);

    }

    public static DeleteItemNode create() {
        return DeleteItemNodeGen.create(null, null);
    }

    public static DeleteItemNode create(ExpressionNode primary, ExpressionNode slice) {
        return DeleteItemNodeGen.create(primary, slice);
    }

}
