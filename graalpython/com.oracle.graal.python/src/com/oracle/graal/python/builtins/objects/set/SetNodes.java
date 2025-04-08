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
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class SetNodes {

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 116 -> 98
    public abstract static class ConstructSetNode extends PNodeWithContext {
        public abstract PSet execute(Frame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        static PSet set(@SuppressWarnings("unused") PNone none,
                        @Bind PythonLanguage language) {
            return PFactory.createSet(language);
        }

        @Specialization(guards = "!isNoValue(iterable)")
        static PSet setIterable(VirtualFrame frame, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode) {
            PSet set = PFactory.createSet(language);
            Object iterator = getIter.execute(frame, inliningTarget, iterable);
            while (true) {
                Object next;
                try {
                    next = nextNode.execute(frame, inliningTarget, iterator);
                } catch (IteratorExhausted e) {
                    return set;
                }
                setItemNode.execute(frame, inliningTarget, set, next, PNone.NONE);
            }
        }

        @Fallback
        static PSet setObject(Object value,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_NOT_ITERABLE, value);
        }

        @NeverDefault
        public static ConstructSetNode create() {
            return SetNodesFactory.ConstructSetNodeGen.create();
        }

        public static ConstructSetNode getUncached() {
            return SetNodesFactory.ConstructSetNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @OperationProxy.Proxyable
    @SuppressWarnings("truffle-inlining")       // footprint reduction 92 -> 73
    public abstract static class AddNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PSet self, Object o);

        @Specialization
        public static void add(VirtualFrame frame, PSet self, Object o,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, inliningTarget, self, o, PNone.NONE);
        }

        @NeverDefault
        public static AddNode create() {
            return SetNodesFactory.AddNodeGen.create();
        }

        public static AddNode getUncached() {
            return SetNodesFactory.AddNodeGen.getUncached();
        }
    }

    public abstract static class DiscardNode extends PythonBinaryBuiltinNode {

        public abstract boolean execute(VirtualFrame frame, PSet self, Object key);

        @Specialization
        boolean discard(VirtualFrame frame, PSet self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseSetBuiltins.ConvertKeyNode conv,
                        @Cached HashingStorageDelItem delItem) {
            Object checkedKey = conv.execute(inliningTarget, key);
            return delItem.execute(frame, inliningTarget, self.getDictStorage(), checkedKey, self);
        }
    }
}
