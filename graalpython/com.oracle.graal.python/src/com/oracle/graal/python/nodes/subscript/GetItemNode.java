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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode.NotImplementedHandler;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo(shortName = __GETITEM__)
public abstract class GetItemNode extends BinaryOpNode implements ReadNode {
    private static final String P_OBJECT_IS_NOT_SUBSCRIPTABLE = "'%p' object is not subscriptable";

    public ExpressionNode getPrimary() {
        return getLeftNode();
    }

    public ExpressionNode getSlice() {
        return getRightNode();
    }

    public abstract Object execute(VirtualFrame frame, Object primary, Object slice);

    protected static SequenceStorageNodes.GetItemNode createGetItemNodeForList() {
        return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forList(), (s, f) -> f.createList(s));
    }

    protected static SequenceStorageNodes.GetItemNode createGetItemNodeForTuple() {
        return SequenceStorageNodes.GetItemNode.create(NormalizeIndexNode.forTuple(), (s, f) -> f.createTuple(s));
    }

    public static GetItemNode create() {
        return GetItemNodeGen.create(null, null);
    }

    public static GetItemNode create(ExpressionNode primary, ExpressionNode slice) {
        return GetItemNodeGen.create(primary, slice);
    }

    @Override
    public StatementNode makeWriteNode(ExpressionNode rhs) {
        return SetItemNode.create(getPrimary(), getSlice(), rhs);
    }

    @Specialization(guards = {"lib.canBeIndex(index) || isPSlice(index)", "isBuiltinList.profileIsAnyBuiltinObject(primary)"})
    Object doBuiltinList(VirtualFrame frame, PList primary, Object index,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Cached("createGetItemNodeForList()") SequenceStorageNodes.GetItemNode getItemNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinList) {
        return getItemNode.execute(frame, primary.getSequenceStorage(), index);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!lib.canBeIndex(index)", "!isPSlice(index)"})
    public Object doListError(VirtualFrame frame, PList primary, Object index,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "list", index);
    }

    @Specialization(guards = {"lib.canBeIndex(index) || isPSlice(index)", "isBuiltinTuple.profileIsAnyBuiltinObject(primary)"})
    Object doBuiltinTuple(VirtualFrame frame, PTuple primary, Object index,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                    @Cached("createGetItemNodeForTuple()") SequenceStorageNodes.GetItemNode getItemNode,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinTuple) {
        return getItemNode.execute(frame, primary.getSequenceStorage(), index);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!lib.canBeIndex(index)", "!isPSlice(index)"})
    public Object doTupleError(VirtualFrame frame, PTuple primary, Object index,
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                    @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "tuple", index);
    }

    @Specialization(replaces = {"doBuiltinList", "doBuiltinTuple"})
    Object doAnyObject(VirtualFrame frame, Object primary, Object index,
                    @Cached("createGetItemLookupAndCall()") LookupAndCallBinaryNode callGetitemNode) {
        return callGetitemNode.executeObject(frame, primary, index);
    }

    public static LookupAndCallBinaryNode createGetItemLookupAndCall() {
        return LookupAndCallBinaryNode.create(__GETITEM__, null, GetItemNodeNotImplementedHandler::new);
    }

    private static final class GetItemNodeNotImplementedHandler extends NotImplementedHandler {
        @Child private IsBuiltinClassProfile isBuiltinClassProfile;
        @Child private PRaiseNode raiseNode;
        @Child private CallNode callClassGetItemNode;
        @Child private GetAttributeNode getClassGetItemNode;

        @Override
        public Object execute(VirtualFrame frame, Object arg, Object arg2) {
            if (PGuards.isPythonClass(arg)) {
                if (getClassGetItemNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getClassGetItemNode = insert(GetAttributeNode.create(__CLASS_GETITEM__));
                    isBuiltinClassProfile = insert(IsBuiltinClassProfile.create());
                }
                Object classGetItem = null;
                try {
                    classGetItem = getClassGetItemNode.executeObject(frame, arg);
                } catch (PException e) {
                    e.expect(AttributeError, isBuiltinClassProfile);
                    // fall through to normal error handling
                }
                if (classGetItem != null) {
                    if (callClassGetItemNode == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        callClassGetItemNode = insert(CallNode.create());
                    }
                    return callClassGetItemNode.execute(frame, classGetItem, arg2);
                }
            }
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            throw raiseNode.raise(TypeError, P_OBJECT_IS_NOT_SUBSCRIPTABLE, arg);
        }
    }
}
