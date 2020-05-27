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
package com.oracle.graal.python.nodes.frame;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

public abstract class DestructuringAssignmentNode extends StatementNode implements WriteNode {
    /* Lazily initialized helpers, also acting as branch profiles */
    @Child private PRaiseNode raiseNode;
    @CompilationFinal private ContextReference<PythonContext> contextRef;

    /* Syntactic children */
    @Child private ExpressionNode rhs;
    @Children private final WriteNode[] slots;
    @Children private final StatementNode[] assignments;

    protected final int starredIndex;

    public DestructuringAssignmentNode(ExpressionNode rhs, ReadNode[] slots, int starredIndex, StatementNode[] assignments) {
        this.rhs = rhs;
        this.starredIndex = starredIndex;
        this.assignments = assignments;
        this.slots = new WriteNode[slots.length];
        for (int i = 0; i < slots.length; i++) {
            this.slots[i] = (WriteNode) slots[i].makeWriteNode(null);
        }
    }

    public static DestructuringAssignmentNode create(ExpressionNode rhs, ReadNode[] slots, int starredIndex, StatementNode[] assignments) {
        return DestructuringAssignmentNodeGen.create(rhs, slots, starredIndex, assignments);
    }

    public abstract void executeWith(VirtualFrame frame, Object rhsValue);

    @Override
    public final void executeVoid(VirtualFrame frame) {
        Object rhsValue = rhs.execute(frame);
        executeWith(frame, rhsValue);
    }

    public final void doWrite(VirtualFrame frame, Object rhsValue) {
        executeWith(frame, rhsValue);
    }

    public ExpressionNode getRhs() {
        return rhs;
    }

    protected static boolean isBuiltinList(Object object, IsBuiltinClassProfile profile) {
        return object instanceof PList && profile.profileObject((PList) object, PythonBuiltinClassType.PList);
    }

    protected static boolean isBuiltinTuple(Object object, IsBuiltinClassProfile profile) {
        return object instanceof PTuple && profile.profileObject((PTuple) object, PythonBuiltinClassType.PTuple);
    }

    @Specialization(guards = {"isBuiltinList(rhsVal, isBuiltinClass)", "starredIndex < 0"})
    void writeList(VirtualFrame frame, PList rhsVal,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemNode getItemNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
        SequenceStorage sequenceStorage = rhsVal.getSequenceStorage();
        writeSequenceStorage(frame, sequenceStorage, lenNode, getItemNode);
        performAssignments(frame);
    }

    @Specialization(guards = {"isBuiltinTuple(rhsVal, isBuiltinClass)", "starredIndex < 0"})
    void writeTuple(VirtualFrame frame, PTuple rhsVal,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemNode getItemNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
        SequenceStorage sequenceStorage = rhsVal.getSequenceStorage();
        writeSequenceStorage(frame, sequenceStorage, lenNode, getItemNode);
        performAssignments(frame);
    }

    @ExplodeLoop
    private void writeSequenceStorage(VirtualFrame frame, SequenceStorage sequenceStorage, SequenceStorageNodes.LenNode lenNode, SequenceStorageNodes.GetItemNode getItemNode) {
        int len = lenNode.execute(sequenceStorage);
        if (len > slots.length) {
            CompilerDirectives.transferToInterpreter();
            throw getCore().raiseInvalidSyntax(getEncapsulatingSourceSection().getSource(), getEncapsulatingSourceSection(), ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, slots.length);
        } else if (len < slots.length) {
            throw ensureRaiseNode().raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, slots.length, len);
        } else {
            for (int i = 0; i < slots.length; i++) {
                Object value = getItemNode.execute(frame, sequenceStorage, i);
                slots[i].doWrite(frame, value);
            }
        }
    }

    @Specialization(guards = {"isBuiltinList(rhsVal, isBuiltinClass)", "starredIndex >= 0"}, limit = "1")
    void writeListStarred(VirtualFrame frame, PList rhsVal,
                    @Shared("writeStarred") @Cached WriteSequenceStorageStarredNode writeSequenceStorageStarredNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
        SequenceStorage sequenceStorage = rhsVal.getSequenceStorage();
        writeSequenceStorageStarredNode.execute(frame, sequenceStorage, slots, starredIndex);
        performAssignments(frame);
    }

    @Specialization(guards = {"isBuiltinTuple(rhsVal, isBuiltinClass)", "starredIndex >= 0"}, limit = "1")
    void writeTupleStarred(VirtualFrame frame, PTuple rhsVal,
                    @Shared("writeStarred") @Cached WriteSequenceStorageStarredNode writeSequenceStorageStarredNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinClass) {
        SequenceStorage sequenceStorage = rhsVal.getSequenceStorage();
        writeSequenceStorageStarredNode.execute(frame, sequenceStorage, slots, starredIndex);
        performAssignments(frame);
    }

    @ExplodeLoop
    private void performAssignments(VirtualFrame frame) {
        for (int i = 0; i < assignments.length; i++) {
            assignments[i].executeVoid(frame);
        }
    }

    @Specialization(guards = {"!isBuiltinTuple(iterable, tupleProfile)", "!isBuiltinList(iterable, listProfile)", "starredIndex < 0"})
    void writeIterable(VirtualFrame frame, Object iterable,
                    @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemNode getItemNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile tupleProfile,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile listProfile) {
        PTuple rhsValue = constructTupleNode.execute(frame, iterable);
        writeSequenceStorage(frame, rhsValue.getSequenceStorage(), lenNode, getItemNode);
        performAssignments(frame);
    }

    @Specialization(guards = {"!isBuiltinTuple(iterable, tupleProfile)", "!isBuiltinList(iterable, listProfile)", "starredIndex >= 0"}, limit = "1")
    void writeIterableStarred(VirtualFrame frame, Object iterable,
                    @Cached TupleNodes.ConstructTupleNode constructTupleNode,
                    @Shared("writeStarred") @Cached WriteSequenceStorageStarredNode writeSequenceStorageStarredNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile tupleProfile,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile listProfile) {
        PTuple rhsValue = constructTupleNode.execute(frame, iterable);
        writeSequenceStorageStarredNode.execute(frame, rhsValue.getSequenceStorage(), slots, starredIndex);
        performAssignments(frame);
    }

    private PythonCore getCore() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(PythonLanguage.class);
        }
        return contextRef.get().getCore();
    }

    private PRaiseNode ensureRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    /**
     * This node performs assignments in form of
     * {@code pre_0, pre_1, ..., pre_i, *starred, post_0, post_1, ..., post_k = sequenceObject}.
     * Note that the parameters {@code slots} and {@code starredIndex} must be PE constant!
     */
    abstract static class WriteSequenceStorageStarredNode extends Node {

        @Child private PythonObjectFactory factory;
        @Child private PRaiseNode raiseNode;

        abstract void execute(VirtualFrame frame, SequenceStorage storage, WriteNode[] slots, int starredIndex);

        @Specialization(guards = {"getLength(lenNode, storage) == cachedLength"}, limit = "1")
        void doExploded(VirtualFrame frame, SequenceStorage storage, WriteNode[] slots, int starredIndex,
                        @Shared("getItemNode") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("lenNode") @Cached @SuppressWarnings("unused") SequenceStorageNodes.LenNode lenNode,
                        @Cached("getLength(lenNode, storage)") int cachedLength) {

            CompilerAsserts.partialEvaluationConstant(slots);
            CompilerAsserts.partialEvaluationConstant(starredIndex);
            if (cachedLength < slots.length - 1) {
                throw ensureRaiseNode().raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, slots.length, cachedLength);
            } else {
                writeSlots(frame, storage, getItemNode, slots, starredIndex);
                final int starredLength = cachedLength - (slots.length - 1);
                CompilerAsserts.partialEvaluationConstant(starredLength);
                Object[] array = consumeStarredItems(frame, storage, starredLength, getItemNode, starredIndex);
                assert starredLength == array.length;
                slots[starredIndex].doWrite(frame, factory().createList(array));
                performAssignmentsAfterStar(frame, storage, starredIndex + starredLength, getItemNode, slots, starredIndex);
            }
        }

        @Specialization(replaces = "doExploded")
        void doGeneric(VirtualFrame frame, SequenceStorage storage, WriteNode[] slots, int starredIndex,
                        @Shared("getItemNode") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("lenNode") @Cached SequenceStorageNodes.LenNode lenNode) {
            CompilerAsserts.partialEvaluationConstant(slots);
            CompilerAsserts.partialEvaluationConstant(starredIndex);
            int len = lenNode.execute(storage);
            if (len < slots.length - 1) {
                throw ensureRaiseNode().raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, slots.length, len);
            } else {
                writeSlots(frame, storage, getItemNode, slots, starredIndex);
                final int starredLength = len - (slots.length - 1);
                Object[] array = new Object[starredLength];
                int pos = starredIndex;
                for (int i = 0; i < starredLength; i++) {
                    array[i] = getItemNode.execute(frame, storage, pos++);
                }
                slots[starredIndex].doWrite(frame, factory().createList(array));
                for (int i = starredIndex + 1; i < slots.length; i++) {
                    Object value = getItemNode.execute(frame, storage, pos++);
                    slots[i].doWrite(frame, value);
                }
            }
        }

        @ExplodeLoop
        private static void writeSlots(VirtualFrame frame, SequenceStorage storage, SequenceStorageNodes.GetItemNode getItemNode, WriteNode[] slots, int starredIndex) {
            for (int i = 0; i < starredIndex; i++) {
                Object value = getItemNode.execute(frame, storage, i);
                slots[i].doWrite(frame, value);
            }
        }

        @ExplodeLoop
        private static Object[] consumeStarredItems(VirtualFrame frame, SequenceStorage sequenceStorage, int starredLength, SequenceStorageNodes.GetItemNode getItemNode, int starredIndex) {
            Object[] array = new Object[starredLength];
            CompilerAsserts.partialEvaluationConstant(starredLength);
            for (int i = 0; i < starredLength; i++) {
                array[i] = getItemNode.execute(frame, sequenceStorage, starredIndex + i);
            }
            return array;
        }

        @ExplodeLoop
        private static void performAssignmentsAfterStar(VirtualFrame frame, SequenceStorage sequenceStorage, int startPos, SequenceStorageNodes.GetItemNode getItemNode, WriteNode[] slots,
                        int starredIndex) {
            for (int i = starredIndex + 1, pos = startPos; i < slots.length; i++, pos++) {
                Object value = getItemNode.execute(frame, sequenceStorage, pos);
                slots[i].doWrite(frame, value);
            }
        }

        static int getLength(SequenceStorageNodes.LenNode lenNode, SequenceStorage storage) {
            return lenNode.execute(storage);
        }

        private PythonObjectFactory factory() {
            if (factory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                factory = insert(PythonObjectFactory.create());
            }
            return factory;
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

    }
}
