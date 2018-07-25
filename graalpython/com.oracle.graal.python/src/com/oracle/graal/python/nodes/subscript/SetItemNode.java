/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.array.PCharArray;
import com.oracle.graal.python.builtins.objects.array.PDoubleArray;
import com.oracle.graal.python.builtins.objects.array.PIntArray;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins.GetattributeNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.sequence.SequenceUtil.NormalizeIndexNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = __SETITEM__)
@NodeChildren({@NodeChild(value = "primary", type = PNode.class), @NodeChild(value = "slice", type = PNode.class), @NodeChild(value = "right", type = PNode.class)})
public abstract class SetItemNode extends StatementNode implements WriteNode {

    @Child private NormalizeIndexNode normalize;

    public abstract PNode getPrimary();

    public abstract PNode getSlice();

    public abstract PNode getRight();

    public static SetItemNode create(PNode primary, PNode slice, PNode right) {
        return SetItemNodeGen.create(primary, slice, right);
    }

    public static SetItemNode create() {
        return SetItemNodeGen.create(null, null, null);
    }

    @Override
    public PNode getRhs() {
        return getRight();
    }

    private NormalizeIndexNode ensureNormalize() {
        if (normalize == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            normalize = insert(NormalizeIndexNode.create());
        }
        return normalize;
    }

    @Override
    public Object doWrite(VirtualFrame frame, boolean value) {
        return executeWith(getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public Object doWrite(VirtualFrame frame, int value) {
        return executeWith(getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public Object doWrite(VirtualFrame frame, long value) {
        return executeWith(getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public Object doWrite(VirtualFrame frame, double value) {
        return executeWith(getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    @Override
    public Object doWrite(VirtualFrame frame, Object value) {
        return executeWith(getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    public Object executeWith(VirtualFrame frame, Object value) {
        return executeWith(getPrimary().execute(frame), getSlice().execute(frame), value);
    }

    public abstract Object executeWith(Object primary, Object slice, boolean value);

    public abstract Object executeWith(Object primary, Object slice, int value);

    public abstract Object executeWith(Object primary, Object slice, long value);

    public abstract Object executeWith(Object primary, Object slice, double value);

    public abstract Object executeWith(Object primary, Object slice, Object value);

    @Specialization
    public Object doPArray(PArray primary, PSlice slice, PArray value) {
        primary.setSlice(slice, value);
        return PNone.NONE;
    }

    /**
     * Unboxed array stores.
     */
    @Specialization
    public Object doPArrayInt(PIntArray primary, int index, int value) {
        primary.setIntItemNormalized(ensureNormalize().forArrayAssign(index, primary.len()), value);
        return PNone.NONE;
    }

    @Specialization
    public Object doPArrayDouble(PDoubleArray primary, int index, double value) {
        primary.setDoubleItemNormalized(ensureNormalize().forArrayAssign(index, primary.len()), value);
        return PNone.NONE;
    }

    @Specialization
    public Object doPArrayChar(PCharArray primary, int index, char value) {
        primary.setCharItemNormalized(ensureNormalize().forArrayAssign(index, primary.len()), value);
        return PNone.NONE;
    }

    @Specialization
    public Object doSpecialObject(PythonObject primary, int index, Object value,
                    @Cached("create()") GetattributeNode getSetitemNode,
                    @Cached("create()") GetClassNode getClassNode,
                    @Cached("create()") CallTernaryMethodNode callNode) {
        PythonClass primaryClass = getClassNode.execute(primary);
        Object setItemMethod = getSetitemNode.execute(primaryClass, __SETITEM__);
        return callNode.execute(setItemMethod, primary, index, value);
    }

    @Specialization
    public Object doSpecialObject1(Object primary, Object index, Object value,
                    @Cached("create()") GetattributeNode getSetitemNode,
                    @Cached("create()") GetClassNode getClassNode,
                    @Cached("create()") CallTernaryMethodNode callNode) {
        PythonClass primaryClass = getClassNode.execute(primary);
        Object setItemMethod = getSetitemNode.execute(primaryClass, __SETITEM__);
        return callNode.execute(setItemMethod, primary, index, value);
    }
}
