/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.deque;

import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE_ITER;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDequeIter)
public final class DequeIterBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DequeIterBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DequeIterBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_DEQUE_ITER, minNumOfPositionalArgs = 2, parameterNames = {"$self", "iterable", "index"})
    @GenerateNodeFactory
    public abstract static class DequeIterNode extends PythonTernaryBuiltinNode {

        @Specialization
        static PDequeIter doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object deque, Object indexObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile dequeProfile,
                        @Cached InlinedConditionProfile indexNoneProfile,
                        @Cached PyNumberIndexNode toIndexNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached DequeIterCommonBuiltins.DequeIterNextNode getNextNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (!dequeProfile.profile(inliningTarget, deque instanceof PDeque)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_OBJ_TYPE_S_GOT_P, BuiltinNames.T_DEQUE, deque);
            }
            PDequeIter dequeIter = PFactory.createDequeIter(language, (PDeque) deque);
            if (indexNoneProfile.profile(inliningTarget, indexObj != PNone.NO_VALUE)) {
                int index = castToJavaIntExactNode.execute(inliningTarget, toIndexNode.execute(frame, inliningTarget, indexObj));
                for (int i = 0; i < index; i++) {
                    getNextNode.execute(dequeIter);
                }
            }
            return dequeIter;
        }
    }
}
