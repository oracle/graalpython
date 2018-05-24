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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;

import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.array.PCharArray;
import com.oracle.graal.python.builtins.objects.array.PDoubleArray;
import com.oracle.graal.python.builtins.objects.array.PIntArray;
import com.oracle.graal.python.builtins.objects.array.PLongArray;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.runtime.sequence.SequenceUtil.NormalizeIndexNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = __GETITEM__)
@GenerateNodeFactory
public abstract class GetItemNode extends BinaryOpNode implements ReadNode {

    @Child private NormalizeIndexNode normalize = NormalizeIndexNode.create();

    public abstract Object execute(Object primary, Object slice);

    public PNode getPrimary() {
        return getLeftNode();
    }

    public PNode getSlice() {
        return getRightNode();
    }

    public abstract Object execute(VirtualFrame frame, Object primary, Object slice);

    public static GetItemNode create() {
        return GetItemNodeFactory.create(null, null);
    }

    public static GetItemNode create(PNode primary, PNode slice) {
        return GetItemNodeFactory.create(primary, slice);
    }

    private int toInt(PInt index) {
        try {
            return index.intValueExact();
        } catch (ArithmeticException e) {
            // anything outside the int range is considered to be "out of range"
            throw raise(IndexError, "index out of range");
        }
    }

    @Override
    public PNode makeWriteNode(PNode rhs) {
        return SetItemNodeFactory.create(getPrimary(), getSlice(), rhs);
    }

    @Specialization
    public String doString(String primary, PSlice slice) {
        SliceInfo info = slice.computeActualIndices(primary.length());
        final int start = info.start;
        int stop = info.stop;
        int step = info.step;

        if (step > 0 && stop < start) {
            stop = start;
        }
        if (step == 1) {
            return getSubString(primary, start, stop);
        } else {
            char[] newChars = new char[info.length];
            int j = 0;
            for (int i = start; j < info.length; i += step) {
                newChars[j++] = primary.charAt(i);
            }

            return new String(newChars);
        }
    }

    @Specialization
    public Object doPRange(PRange range, PSlice slice) {
        return range.getSlice(factory(), slice);
    }

    @Specialization
    public Object doPArray(PArray primary, PSlice slice) {
        return primary.getSlice(factory(), slice);
    }

    @Specialization
    public String doString(String primary, int idx) {
        try {
            int index = idx;

            if (idx < 0) {
                index += primary.length();
            }

            return charAtToString(primary, index);
        } catch (StringIndexOutOfBoundsException | ArithmeticException e) {
            throw raise(IndexError, "IndexError: string index out of range");
        }
    }

    @Specialization
    public String doString(String primary, PInt idx) {
        return doString(primary, toInt(idx));
    }

    @Specialization
    public Object doPBytes(PBytes primary, int idx) {
        return primary.getItemNormalized(normalize.forRange(idx, primary.len()));
    }

    @Specialization
    public Object doPBytes(PBytes bytes, PInt idx) {
        return doPBytes(bytes, toInt(idx));
    }

    @Specialization
    public Object doPByteArray(PByteArray primary, int idx) {
        return primary.getItemNormalized(normalize.forRange(idx, primary.len()));
    }

    @Specialization
    public Object doPByteArray(PByteArray bytearray, PInt idx) {
        return doPByteArray(bytearray, toInt(idx));
    }

    @Specialization
    public Object doPRange(PRange primary, int idx) {
        return primary.getItemNormalized(normalize.forRange(idx, primary.len()));
    }

    @Specialization
    public Object doPRange(PRange primary, long idx) {
        return primary.getItemNormalized(normalize.forRange(idx, primary.len()));
    }

    @Specialization
    public int doPIntArray(PIntArray primary, int idx) {
        return primary.getIntItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public int doPIntArray(PIntArray primary, long idx) {
        return primary.getIntItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public long doPLongArray(PLongArray primary, int idx) {
        return primary.getLongItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public long doPLongArray(PLongArray primary, long idx) {
        return primary.getLongItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public double doPDoubleArray(PDoubleArray primary, int idx) {
        return primary.getDoubleItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public double doPDoubleArray(PDoubleArray primary, long idx) {
        return primary.getDoubleItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public char doPCharArray(PCharArray primary, int idx) {
        return primary.getCharItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public char doPCharArray(PCharArray primary, long idx) {
        return primary.getCharItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public Object doPArray(PArray primary, long idx) {
        return primary.getItemNormalized(normalize.forArray(idx, primary.len()));
    }

    @Specialization
    public Object doPArray(PArray primary, PInt idx) {
        return primary.getItemNormalized(normalize.forArray(toInt(idx), primary.len()));
    }

    @Specialization
    public Object doSpecialObject(Object primary, Object index,
                    @Cached("create(__GETITEM__)") LookupAndCallBinaryNode callGetitemNode) {
        return callGetitemNode.executeObject(primary, index);
    }

    private static String getSubString(String origin, int start, int stop) {
        char[] chars = new char[stop - start];
        origin.getChars(start, stop, chars, 0);
        return new String(chars);
    }

    private static String charAtToString(String primary, int index) {
        char charactor = primary.charAt(index);
        return new String(new char[]{charactor});
    }
}
