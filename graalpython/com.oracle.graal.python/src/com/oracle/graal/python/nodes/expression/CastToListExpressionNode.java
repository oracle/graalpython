/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNodeGen.CastToListNodeGen;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline(false)
@GenerateCached
public abstract class CastToListExpressionNode extends UnaryOpNode {

    @Specialization
    static PList doObject(VirtualFrame frame, Object value,
                    @Cached CastToListNode castToListNode) {
        return castToListNode.execute(frame, value);
    }

    @ImportStatic(PGuards.class)
    @GenerateInline(false) // footprint reduction 40 -> 21
    public abstract static class CastToListNode extends Node {

        public abstract PList execute(VirtualFrame frame, Object list);

        @Specialization(guards = {"isBuiltinTuple(v)", "cachedLength == getLength(v)", "cachedLength < 32"}, limit = "2")
        @ExplodeLoop
        static PList starredTupleCachedLength(PTuple v,
                        @Bind PythonLanguage language,
                        @Cached("getLength(v)") int cachedLength,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage s = v.getSequenceStorage();
            Object[] array = new Object[cachedLength];
            for (int i = 0; i < cachedLength; i++) {
                array[i] = getItemNode.execute(s, i);
            }
            return PFactory.createList(language, array);
        }

        @Specialization(replaces = "starredTupleCachedLength", guards = "isBuiltinTuple(v)")
        static PList starredTuple(PTuple v,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            return PFactory.createList(language, getObjectArrayNode.execute(inliningTarget, v).clone());
        }

        @Specialization(guards = "isBuiltinList(v)")
        protected static PList starredList(PList v) {
            return v;
        }

        @Specialization(rewriteOn = PException.class)
        static PList starredIterable(VirtualFrame frame, PythonObject value,
                        @Bind Node inliningTarget,
                        @Shared @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Shared @Cached ConstructListNode constructListNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return constructListNode.execute(frame, value);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @Specialization
        static PList starredGeneric(VirtualFrame frame, Object v,
                        @Bind Node inliningTarget,
                        @Shared @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Shared @Cached ConstructListNode constructListNode,
                        @Cached IsBuiltinObjectProfile attrProfile,
                        @Cached PRaiseNode raise) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return constructListNode.execute(frame, v);
            } catch (PException e) {
                e.expectAttributeError(inliningTarget, attrProfile);
                throw raise.raise(inliningTarget, TypeError, ErrorMessages.OBJ_NOT_ITERABLE, v);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        protected int getLength(PTuple t) {
            return t.getSequenceStorage().length();
        }

        @NeverDefault
        public static CastToListNode create() {
            return CastToListNodeGen.create();
        }

    }

    public abstract static class CastToListInteropNode extends PNodeWithContext {

        public abstract PList executeWithGlobalState(Object list);

        @NeverDefault
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

        @Override
        public PList executeWithGlobalState(Object list) {
            return (PList) CallNode.executeUncached(PythonBuiltinClassType.PList, list);
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }
    }
}
