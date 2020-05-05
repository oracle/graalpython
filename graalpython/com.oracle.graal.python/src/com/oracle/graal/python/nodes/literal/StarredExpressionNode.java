/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.literal;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNodeFactory.AppendToSetNodeGen;
import com.oracle.graal.python.nodes.literal.StarredExpressionNodeFactory.AppendToStorageNodeGen;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public final class StarredExpressionNode extends LiteralNode {
    @Child private ExpressionNode childNode;
    @Child private AppendToStorageNode appendToStorageNode;
    @Child private AppendToSetNode appendToSetNode;

    private StarredExpressionNode(ExpressionNode childNode) {
        this.childNode = childNode;
    }

    public static StarredExpressionNode create(ExpressionNode child) {
        return new StarredExpressionNode(child);
    }

    public ExpressionNode getValue() {
        return childNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return childNode.execute(frame);
    }

    public HashingStorage appendToSet(VirtualFrame frame, HashingStorage storage, HashingStorageLibrary storageLib, ThreadState state, Object values) {
        return ensureAppendToSetNode().execute(frame, storage, storageLib, values, state);
    }

    public SequenceStorage appendToStorage(VirtualFrame frame, SequenceStorage storage, Object values) {
        return ensureAppendToStorageNode().execute(frame, storage, values);
    }

    private AppendToStorageNode ensureAppendToStorageNode() {
        if (appendToStorageNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            appendToStorageNode = insert(AppendToStorageNodeGen.create());
        }
        return appendToStorageNode;
    }

    private AppendToSetNode ensureAppendToSetNode() {
        if (appendToSetNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            appendToSetNode = insert(AppendToSetNodeGen.create());
        }
        return appendToSetNode;
    }

    @ImportStatic(PGuards.class)
    public abstract static class AppendBaseNode extends Node {
        @Child private GetLazyClassNode getClassNode;

        final LazyPythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
            }
            return getClassNode.execute(value);
        }

        final boolean cannotBeOverriddenSequence(Object value) {
            return value instanceof PSequence && PGuards.cannotBeOverridden(getClass(value));
        }
    }

    public abstract static class AppendToSetNode extends AppendBaseNode {
        public abstract HashingStorage execute(VirtualFrame frame, HashingStorage storage, HashingStorageLibrary storageLib, Object values, ThreadState state);

        @Specialization(guards = "cannotBeOverridden(getClass(values))")
        HashingStorage doPSequence(VirtualFrame frame, HashingStorage storageIn, HashingStorageLibrary storageLib, PSequence values, ThreadState state,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {
            HashingStorage storage = storageIn;
            SequenceStorage valuesStorage = values.getSequenceStorage();
            int n = lenNode.execute(valuesStorage);
            for (int i = 0; i < n; i++) {
                Object element = getItemNode.execute(frame, valuesStorage, i);
                storage = storageLib.setItemWithState(storage, element, PNone.NONE, state);
            }
            return storage;
        }

        @Specialization(guards = "!cannotBeOverriddenSequence(values)")
        HashingStorage doIterable(VirtualFrame frame, HashingStorage storageIn, HashingStorageLibrary storageLib, Object values, ThreadState state,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object iterator = getIterator.executeWith(frame, values);
            HashingStorage storage = storageIn;
            while (true) {
                try {
                    Object nextValue = next.execute(frame, iterator);
                    storage = storageLib.setItemWithState(storage, nextValue, PNone.NONE, state);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return storage;
        }
    }

    public abstract static class AppendToStorageNode extends AppendBaseNode {
        public abstract SequenceStorage execute(VirtualFrame frame, SequenceStorage storage, Object values);

        @Specialization(guards = "cannotBeOverridden(getClass(values))")
        SequenceStorage doPSequence(SequenceStorage storage, PSequence values,
                        @Cached("createConcatStorageNode()") SequenceStorageNodes.ConcatNode concatNode) {
            return concatNode.execute(storage, values.getSequenceStorage());
        }

        @Specialization(guards = "!cannotBeOverriddenSequence(values)")
        SequenceStorage doIterable(VirtualFrame frame, SequenceStorage storageIn, Object values,
                        @Cached("create()") GetIteratorNode getIterator,
                        @Cached("create()") GetNextNode next,
                        @Cached IsBuiltinClassProfile errorProfile,
                        @Cached SequenceStorageNodes.AppendNode appendNode) {
            Object iterator = getIterator.executeWith(frame, values);
            SequenceStorage storage = storageIn;
            while (true) {
                try {
                    Object nextValue = next.execute(frame, iterator);
                    storage = appendNode.execute(storage, nextValue, ListGeneralizationNode.SUPPLIER);
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    break;
                }
            }
            return storage;
        }

        static SequenceStorageNodes.ConcatNode createConcatStorageNode() {
            return SequenceStorageNodes.ConcatNode.create(new Supplier<GeneralizationNode>() {
                @Override
                public GeneralizationNode get() {
                    return ListGeneralizationNode.create();
                }
            });
        }
    }
}
