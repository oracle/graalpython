/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.thread;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaUnsignedLongNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRLock)
public final class RLockBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = RLockBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RLockBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "RLock", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ConstructRLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        PRLock construct(Object cls,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createRLock(language, cls, getInstanceShape.execute(cls));
        }
    }

    @Builtin(name = "_is_owned", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsOwnedRLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isOwned(PRLock self) {
            return self.isOwned();
        }
    }

    @Builtin(name = "_acquire_restore", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AcquireRestoreRLockNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object acquireRestore(PRLock self, PTuple state,
                        @Bind Node inliningTarget,
                        @Cached GilNode gil,
                        @Cached CastToJavaUnsignedLongNode castLong,
                        @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode) {
            if (!self.acquireNonBlocking()) {
                gil.release(true);
                try {
                    self.acquireBlocking(this);
                } finally {
                    gil.acquire();
                }
            }
            // ignore owner, we use the Java lock and cannot set it
            long count = castLong.execute(inliningTarget, getItemNode.execute(inliningTarget, state.getSequenceStorage(), 0));
            long actualCount = self.getCount();
            while (count > actualCount) {
                self.acquireBlocking(this); // we already own it at this point
                actualCount++;
            }
            while (count < actualCount) {
                self.release();
                actualCount--;
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "_release_save", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReleaseSaveRLockNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object releaseSave(PRLock self,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile countProfile,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            int count = self.getCount();
            if (countProfile.profile(inliningTarget, count == 0)) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.RuntimeError, ErrorMessages.CANNOT_RELEASE_UNAQUIRED_LOCK);
            }
            PTuple retVal = PFactory.createTuple(language, new Object[]{count, self.getOwnerId()});
            self.releaseAll();
            return retVal;
        }
    }

    @Builtin(name = "_recursion_count", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class RecursionCountNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int recursionCount(PRLock self) {
            if (self.isOwned()) {
                return self.getCount();
            } else {
                return 0;
            }
        }
    }
}
