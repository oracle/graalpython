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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.CreateStorageFromIteratorNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.subscript.GetItemNodeGen;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class DestructuringAssignmentNode extends StatementNode implements WriteNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private PRaiseNode raiseNode;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    @Child private ExpressionNode rhs;
    @Children private final WriteNode[] slots;
    @Children private final StatementNode[] assignments;

    @Child private GetItemNode getItem = GetItemNodeGen.create();
    @Child private GetItemNode getNonExistingItem = GetItemNodeGen.create();
    @Child private BuiltinFunctions.LenNode lenNode;
    @Child private LookupInheritedAttributeNode lookupGetItemNode = LookupInheritedAttributeNode.create(__GETITEM__);
    @Child private TupleNodes.ConstructTupleNode constructTupleNode = TupleNodes.ConstructTupleNode.create();

    @Child private CreateStorageFromIteratorNode storageNode = CreateStorageFromIteratorNode.create();
    @Child private GetIteratorNode getIteratorNode = GetIteratorNode.create();

    private final IsBuiltinClassProfile notEnoughValuesProfile = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile tooManyValuesErrorProfile = IsBuiltinClassProfile.create();
    private final BranchProfile tooManyValuesProfile = BranchProfile.create();

    private final IsBuiltinClassProfile errorProfile1 = IsBuiltinClassProfile.create();
    private final IsBuiltinClassProfile errorProfile2 = IsBuiltinClassProfile.create();
    private final int starredIndex;

    public DestructuringAssignmentNode(ExpressionNode rhs, ReadNode[] slots, int starredIndex, StatementNode[] assignments) {
        this.rhs = rhs;
        this.starredIndex = starredIndex;
        this.assignments = assignments;
        this.slots = new WriteNode[slots.length];
        for (int i = 0; i < slots.length; i++) {
            this.slots[i] = (WriteNode) slots[i].makeWriteNode(null);
        }
        this.lenNode = starredIndex == -1 ? null : BuiltinFunctionsFactory.LenNodeFactory.create();
    }

    public static DestructuringAssignmentNode create(ExpressionNode rhs, ReadNode[] slots, int starredIndex, StatementNode[] assignments) {
        return new DestructuringAssignmentNode(rhs, slots, starredIndex, assignments);
    }

    @ExplodeLoop
    private void fillSlots(VirtualFrame frame, Object rhsValue, int stop) {
        for (int i = 0; i < stop; i++) {
            Object value = getItem.execute(frame, rhsValue, i);
            slots[i].doWrite(frame, value);
        }
    }

    @ExplodeLoop
    private int fillRest(VirtualFrame frame, Object rhsValue, int pos) {
        int current = pos;
        for (int i = starredIndex + 1; i < slots.length; i++) {
            Object value = getItem.execute(frame, rhsValue, current++);
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
            int length = (int) lenNode.executeWith(frame, rhsValue);
            int starredLength = length - (slots.length - 1);
            Object[] array = new Object[starredLength];
            for (int i = 0; i < starredLength; i++) {
                array[i] = getItem.execute(frame, rhsValue, pos++);
            }
            slots[starredIndex].doWrite(frame, factory.createList(array));
            return fillRest(frame, rhsValue, pos);
        } catch (PException e) {
            e.expectAttributeError(errorProfile1);
            // __len__ is not implemented
            Object[] array = new Object[2];
            int length = 0;
            while (true) {
                try {
                    if (length + 1 > array.length) {
                        array = Arrays.copyOf(array, array.length << 1);
                    }
                    array[length] = getItem.execute(frame, rhsValue, pos);
                    length++;
                    pos++;
                } catch (PException e2) {
                    e2.expect(IndexError, errorProfile2);
                    // expected, fall through
                    break;
                }
            }
            int rest = slots.length - starredIndex - 1;
            fillFromArray(frame, array, length - rest);
            slots[starredIndex].doWrite(frame, factory.createList(Arrays.copyOf(array, length - rest)));
        }
        return pos;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object rhsValue = rhs.execute(frame);
        Object getItemAttribute = lookupGetItemNode.execute(rhsValue);
        if (getItemAttribute == NO_VALUE) {
            rhsValue = constructTupleNode.execute(frame, rhsValue);
        }
        doWrite(frame, rhsValue);
    }

    public ExpressionNode getRhs() {
        return rhs;
    }

    public void doWrite(VirtualFrame frame, Object rhsValue) {
        rhsValue = factory.createList(storageNode.execute(frame,getIteratorNode.executeWith(frame,rhsValue)));
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
            if (notEnoughValuesProfile.profileException(e, IndexError)) {
                if (lenNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    lenNode = insert(BuiltinFunctionsFactory.LenNodeFactory.create());
                }
                throw ensureRaiseNode().raise(ValueError, "not enough values to unpack (expected %d, got %d)", slots.length, lenNode.executeWith(frame, rhsValue));
            } else {
                throw e;
            }
        }
        try {
            getNonExistingItem.execute(frame, rhsValue, nonExistingItem);
            tooManyValuesProfile.enter();
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = PythonLanguage.getContextRef();
            }
            throw contextRef.get().getCore().raiseInvalidSyntax(getEncapsulatingSourceSection().getSource(), getEncapsulatingSourceSection(), "too many values to unpack (expected %d)",
                            nonExistingItem);
        } catch (PException e) {
            if (tooManyValuesErrorProfile.profileException(e, IndexError)) {
                // expected, fall through
            } else {
                throw e;
            }
        }

        performAssignments(frame);
    }

    private PRaiseNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    @ExplodeLoop
    private void performAssignments(VirtualFrame frame) {
        for (int i = 0; i < assignments.length; i++) {
            assignments[i].executeVoid(frame);
        }
    }
}
