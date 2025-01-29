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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.CallSlotLenNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PySequence_GetItem}. For native object it would only call
 * {@code sq_item} and never {@code mp_subscript}.
 */
@ImportStatic({PGuards.class, SpecialMethodSlot.class, ExternalFunctionNodes.PExternalFunctionWrapper.class})
@GenerateInline(false) // One lazy usage, one eager usage => not worth it
@GenerateUncached
public abstract class PySequenceGetItemNode extends Node {
    public abstract Object execute(Frame frame, Object object, int index);

    public final Object execute(Object object, int index) {
        return execute(null, object, index);
    }

    @Specialization
    static Object doGeneric(VirtualFrame frame, Object object, int index,
                    @Bind("this") Node inliningTarget,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Cached IndexForSqSlotInt indexForSqSlot,
                    @Cached CallSlotSizeArgFun callSqItem,
                    @Cached PRaiseNode.Lazy raiseNode) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, object);
        index = indexForSqSlot.execute(frame, inliningTarget, object, slots, index);
        if (slots.sq_item() != null) {
            return callSqItem.execute(frame, inliningTarget, slots.sq_item(), object, index);
        } else {
            throw raiseNotSupported(object, inliningTarget, raiseNode, slots);
        }
    }

    @InliningCutoff
    private static PException raiseNotSupported(Object object, Node inliningTarget, Lazy raiseNode, TpSlots slots) {
        TruffleString message = ErrorMessages.OBJ_DOES_NOT_SUPPORT_INDEXING;
        if (slots.mp_subscript() != null) {
            message = ErrorMessages.IS_NOT_A_SEQUENCE;
        }
        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, message, object);
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class IndexForSqSlotInt extends Node {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, int index);

        @Specialization(guards = "index >= 0")
        static int doInt(@SuppressWarnings("unused") Object object, @SuppressWarnings("unused") TpSlots slots, int index) {
            return index;
        }

        @Fallback
        @InliningCutoff
        static int doNegativeInt(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, int index,
                        @Cached InlinedConditionProfile negativeIndexProfile,
                        @Cached CallSlotLenNode callLenSlot) {
            if (negativeIndexProfile.profile(inliningTarget, index < 0)) {
                if (slots.sq_length() != null) {
                    int len = callLenSlot.execute(frame, inliningTarget, slots.sq_length(), object);
                    index += len;
                }
            }
            return index;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class IndexForSqSlot extends Node {
        public abstract int execute(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, Object index);

        @Specialization
        static int doInt(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, int index,
                        @Exclusive @Cached IndexForSqSlotInt indexForSqSlotInt) {
            return indexForSqSlotInt.execute(frame, inliningTarget, object, slots, index);
        }

        @Specialization(replaces = "doInt")
        @InliningCutoff
        static int doGeneric(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, Object indexObj,
                        @Cached PyIndexCheckNode checkNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Exclusive @Cached IndexForSqSlotInt indexForSqSlotInt,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!checkNode.execute(inliningTarget, indexObj)) {
                raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.SEQUENCE_INDEX_MUST_BE_INT_NOT_P, indexObj);
            }
            int index = asSizeNode.executeExact(frame, inliningTarget, indexObj, PythonBuiltinClassType.IndexError);
            return indexForSqSlotInt.execute(frame, inliningTarget, object, slots, index);
        }
    }
}
