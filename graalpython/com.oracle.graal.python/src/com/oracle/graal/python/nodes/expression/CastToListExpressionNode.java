/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.expression;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetSequenceStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNodeGen.CastToListNodeGen;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;

public abstract class CastToListExpressionNode extends UnaryOpNode {

    @Child private GetSequenceStorageNode getSequenceStorageNode;

    public final SequenceStorage getStorage(VirtualFrame frame) {
        Object result = execute(frame);
        if (getSequenceStorageNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSequenceStorageNode = insert(GetSequenceStorageNodeGen.create());
        }
        return getSequenceStorageNode.execute(result);
    }

    @Specialization
    PList doObject(VirtualFrame frame, Object value,
                    @Cached CastToListNode castToListNode) {
        return castToListNode.execute(frame, value);
    }

    @ImportStatic(PGuards.class)
    public abstract static class CastToListNode extends Node {
        @Child private GetLazyClassNode getClassNode;
        @Child private SequenceStorageNodes.LenNode lenNode;
        @Child private SequenceStorageNodes.GetItemNode getItemNode;

        public abstract PList execute(VirtualFrame frame, Object list);

        protected final LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        @Specialization(guards = {"cannotBeOverridden(getClass(v))", "cachedLength == getLength(v)", "cachedLength < 32"})
        @ExplodeLoop
        protected PList starredTupleCachedLength(VirtualFrame frame, PTuple v,
                        @Cached PythonObjectFactory factory,
                        @Cached("getLength(v)") int cachedLength) {
            SequenceStorage s = v.getSequenceStorage();
            Object[] array = new Object[cachedLength];
            for (int i = 0; i < cachedLength; i++) {
                array[i] = getGetItemNode().execute(frame, s, i);
            }
            return factory.createList(array);
        }

        @Specialization(replaces = "starredTupleCachedLength", guards = "cannotBeOverridden(getClass(v))")
        protected PList starredTuple(PTuple v,
                        @Cached PythonObjectFactory factory,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            return factory.createList(getObjectArrayNode.execute(v).clone());
        }

        @Specialization(guards = "cannotBeOverridden(getClass(v))")
        protected PList starredList(PList v) {
            return v;
        }

        @Specialization(rewriteOn = PException.class)
        protected PList starredIterable(VirtualFrame frame, PythonObject value,
                        @Cached ConstructListNode constructListNode,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PException caughtException = IndirectCallContext.enter(frame, context, this);
            try {
                return constructListNode.execute(value);
            } finally {
                IndirectCallContext.exit(context, caughtException);
            }
        }

        @Specialization
        protected PList starredGeneric(VirtualFrame frame, Object v,
                        @Cached ConstructListNode constructListNode,
                        @Cached IsBuiltinClassProfile attrProfile,
                        @Cached PRaiseNode raise,
                        @Shared("contextRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) {
            PythonContext context = contextRef.get();
            PException caughtException = IndirectCallContext.enter(frame, context, this);
            try {
                return constructListNode.execute(v);
            } catch (PException e) {
                e.expectAttributeError(attrProfile);
                throw raise.raise(TypeError, "%s is not iterable", v);
            } finally {
                IndirectCallContext.exit(context, caughtException);
            }
        }

        protected int getLength(PTuple t) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(t.getSequenceStorage());
        }

        protected SequenceStorageNodes.GetItemNode getGetItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.createNotNormalized());
            }
            return getItemNode;
        }

        public static CastToListNode create() {
            return CastToListNodeGen.create();
        }

    }

    public abstract static class CastToListInteropNode extends PNodeWithContext {

        public abstract PList executeWithGlobalState(Object list);

        public static CastToListInteropNode create() {
            return new CastToListCachedNode();
        }

        public static CastToListInteropNode getUncached() {
            return CastToListUncachedNode.UNCACHED;
        }
    }

    private static final class CastToListCachedNode extends CastToListInteropNode {

        @Child private CastToListNode castToListNode = CastToListNode.create();

        @Override
        public PList executeWithGlobalState(Object list) {
            // NOTE: it is fine to pass 'null' frame because this is a node with global state that
            // forces the caller to take care of it
            return castToListNode.execute(null, list);
        }

    }

    private static final class CastToListUncachedNode extends CastToListInteropNode {
        private static final CastToListUncachedNode UNCACHED = new CastToListUncachedNode();

        private final ContextReference<PythonContext> contextRef = lookupContextReference(PythonLanguage.class);

        @Override
        public PList executeWithGlobalState(Object list) {
            Object builtins = contextRef.get().getBuiltins();
            Object listType = ReadAttributeFromObjectNode.getUncached().execute(builtins, "list");
            LookupInheritedAttributeNode.Dynamic getCall = LookupInheritedAttributeNode.Dynamic.getUncached();
            return (PList) CallNode.getUncached().execute(null, getCall.execute(listType, SpecialMethodNames.__CALL__), listType, list);
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }
}
