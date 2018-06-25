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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNodeFactory.GetItemNodeGen;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class DestructuringAssignmentNode extends PNode implements WriteNode {

    @Child private PNode rhs;
    @Children private final WriteNode[] slots;
    @Children private final PNode[] assignments;

    @Child private GetItemNode getItem = GetItemNodeGen.create();
    @Child private GetItemNode getNonExistingItem = GetItemNodeGen.create();
    @Child private BuiltinFunctions.LenNode lenNode;
    @Child private LookupInheritedAttributeNode lookupInheritedAttributeNode = LookupInheritedAttributeNode.create();
    @Child private TupleNodes.ConstructTupleNode constructTupleNode = TupleNodes.ConstructTupleNode.create();

    private final BranchProfile notEnoughValuesProfile = BranchProfile.create();
    private final BranchProfile tooManyValuesProfile = BranchProfile.create();
    private final BranchProfile otherErrorsProfile = BranchProfile.create();

    private final ConditionProfile errorProfile1 = ConditionProfile.createBinaryProfile();
    private final ConditionProfile errorProfile2 = ConditionProfile.createBinaryProfile();
    private final int starredIndex;

    public DestructuringAssignmentNode(PNode rhs, List<ReadNode> slots, int starredIndex, PNode[] assignments) {
        this.rhs = rhs;
        this.starredIndex = starredIndex;
        this.assignments = assignments;
        this.slots = new WriteNode[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            this.slots[i] = (WriteNode) slots.get(i).makeWriteNode(null);
        }
        this.lenNode = starredIndex == -1 ? null : BuiltinFunctionsFactory.LenNodeFactory.create();
    }

    public static PNode create(PNode rhs, List<ReadNode> slots, int starredIndex, PNode[] assignments) {
        return new DestructuringAssignmentNode(rhs, slots, starredIndex, assignments);
    }

    @ExplodeLoop
    private void fillSlots(VirtualFrame frame, Object rhsValue, int stop) {
        for (int i = 0; i < stop; i++) {
            Object value = getItem.execute(rhsValue, i);
            slots[i].doWrite(frame, value);
        }
    }

    @ExplodeLoop
    private int fillRest(VirtualFrame frame, Object rhsValue, int pos) {
        int current = pos;
        for (int i = starredIndex + 1; i < slots.length; i++) {
            Object value = getItem.execute(rhsValue, current++);
            slots[i].doWrite(frame, value);
        }
        return current;
    }

    @ExplodeLoop
    private int fillFromArray(VirtualFrame frame, Object[] array, int startIndex) {
        int index = startIndex;
        for (int i = starredIndex + 1; i < slots.length; i++) {
            slots[i].doWrite(frame, array[index++]);
        }
        return index;
    }

    private int fillStarred(VirtualFrame frame, Object rhsValue) {
        int pos = starredIndex;
        try {
            // TODO(ls): proper cast to int
            // TODO(ls): the result of the len call doesn't seem to be used in Python
            int length = (int) lenNode.executeWith(rhsValue);
            int starredLength = length - (slots.length - 1);
            Object[] array = new Object[starredLength];
            for (int i = 0; i < starredLength; i++) {
                array[i] = getItem.execute(rhsValue, pos++);
            }
            slots[starredIndex].doWrite(frame, factory().createList(array));
            return fillRest(frame, rhsValue, pos);
        } catch (PException e) {
            e.expect(AttributeError, getCore(), errorProfile1);
            // __len__ is not implemented
            Object[] array = new Object[2];
            int length = 0;
            while (true) {
                try {
                    if (length + 1 > array.length) {
                        array = Arrays.copyOf(array, array.length << 1);
                    }
                    array[length] = getItem.execute(rhsValue, pos);
                    length++;
                    pos++;
                } catch (PException e2) {
                    e2.expect(IndexError, getCore(), errorProfile2);
                    // expected, fall through
                    break;
                }
            }
            int rest = slots.length - starredIndex - 1;
            fillFromArray(frame, array, length - rest);
            slots[starredIndex].doWrite(frame, factory().createList(Arrays.copyOf(array, length - rest)));
        }
        return pos;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object rhsValue = rhs.execute(frame);
        Object getItemAttribute = lookupInheritedAttributeNode.execute(rhsValue, __GETITEM__);
        if (getItemAttribute == NO_VALUE) {
            rhsValue = constructTupleNode.execute(rhsValue);
        }
        return doWrite(frame, rhsValue);
    }

    public PNode getRhs() {
        return rhs;
    }

    public Object doWrite(VirtualFrame frame, Object rhsValue) {
        int nonExistingItem;
        try {
            if (starredIndex == -1) {
                fillSlots(frame, rhsValue, slots.length);
                nonExistingItem = slots.length;
            } else {
                fillSlots(frame, rhsValue, starredIndex);
                nonExistingItem = fillStarred(frame, rhsValue);
            }
        } catch (PException e) {
            notEnoughValuesProfile.enter();
            if (e.getType() == getCore().getErrorClass(IndexError)) {
                throw raise(SyntaxError, "not enough values to unpack");
            } else {
                otherErrorsProfile.enter();
                throw e;
            }
        }
        try {
            getNonExistingItem.execute(rhsValue, nonExistingItem);
            tooManyValuesProfile.enter();
            throw raise(SyntaxError, "too many values to unpack (expected %d)", nonExistingItem);
        } catch (PException e) {
            if (e.getType() == getCore().getErrorClass(IndexError)) {
                // expected, fall through
            } else {
                otherErrorsProfile.enter();
                throw e;
            }
        }

        performAssignments(frame);

        return PNone.NONE;
    }

    @ExplodeLoop
    private void performAssignments(VirtualFrame frame) {
        for (int i = 0; i < assignments.length; i++) {
            assignments[i].executeVoid(frame);
        }
    }
}
