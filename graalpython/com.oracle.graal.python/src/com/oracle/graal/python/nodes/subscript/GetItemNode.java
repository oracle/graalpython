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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;

import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.array.PCharArray;
import com.oracle.graal.python.builtins.objects.array.PDoubleArray;
import com.oracle.graal.python.builtins.objects.array.PIntArray;
import com.oracle.graal.python.builtins.objects.array.PLongArray;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = __GETITEM__)
public abstract class GetItemNode extends BinaryOpNode implements ReadNode {

    @Child private NormalizeIndexNode normalize;

    public abstract Object execute(Object primary, Object slice);

    public PNode getPrimary() {
        return getLeftNode();
    }

    public PNode getSlice() {
        return getRightNode();
    }

    public abstract Object execute(VirtualFrame frame, Object primary, Object slice);

    public static GetItemNode create() {
        return GetItemNodeGen.create(null, null);
    }

    public static GetItemNode create(PNode primary, PNode slice) {
        return GetItemNodeGen.create(primary, slice);
    }

    private SequenceStorageNodes.NormalizeIndexNode ensureNormalize() {
        if (normalize == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            normalize = insert(SequenceStorageNodes.NormalizeIndexNode.forArray());
        }
        return normalize;
    }

    @Override
    public PNode makeWriteNode(PNode rhs) {
        return SetItemNode.create(getPrimary(), getSlice(), rhs);
    }

    @Specialization
    public Object doPArray(PArray primary, PSlice slice) {
        return primary.getSlice(factory(), slice);
    }

    @Specialization
    public int doPIntArray(PIntArray primary, int idx) {
        return primary.getIntItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public int doPIntArray(PIntArray primary, long idx) {
        return primary.getIntItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public long doPLongArray(PLongArray primary, int idx) {
        return primary.getLongItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public long doPLongArray(PLongArray primary, long idx) {
        return primary.getLongItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public double doPDoubleArray(PDoubleArray primary, int idx) {
        return primary.getDoubleItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public double doPDoubleArray(PDoubleArray primary, long idx) {
        return primary.getDoubleItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public char doPCharArray(PCharArray primary, int idx) {
        return primary.getCharItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public char doPCharArray(PCharArray primary, long idx) {
        return primary.getCharItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public Object doPArray(PArray primary, long idx) {
        return primary.getItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public Object doPArray(PArray primary, PInt idx) {
        return primary.getItemNormalized(ensureNormalize().execute(idx, primary.len()));
    }

    @Specialization
    public Object doSpecialObject(Object primary, Object index,
                    @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetitemNode) {
        return callGetitemNode.executeObject(primary, index);
    }

}
