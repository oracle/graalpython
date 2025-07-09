/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.nodes.ErrorMessages.ASYNC_FOR_NO_AITER;
import static com.oracle.graal.python.nodes.ErrorMessages.ASYNC_FOR_NO_ANEXT_INITIAL;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.lib.PyAIterCheckNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@GenerateInline(false) // used in bytecode root node
public abstract class GetAIterNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object receiver);

    public static GetAIterNode getUncached() {
        return GetAIterNodeGen.getUncached();
    }

    @NeverDefault
    public static GetAIterNode create() {
        return GetAIterNodeGen.create();
    }

    @Specialization
    Object doGeneric(VirtualFrame frame, Object receiver,
                    @Bind Node inliningTarget,
                    @Cached GetClassNode getAsyncIterType,
                    @Cached GetCachedTpSlotsNode getSlots,
                    @Cached CallSlotUnaryNode callSlot,
                    @Cached PRaiseNode raiseNoAIter,
                    @Cached PRaiseNode raiseNoANext,
                    @Cached PyAIterCheckNode checkNode) {
        Object type = getAsyncIterType.execute(inliningTarget, receiver);
        TpSlots slots = getSlots.execute(inliningTarget, type);
        if (slots.am_aiter() == null) {
            throw raiseNoAIter.raise(inliningTarget, PythonBuiltinClassType.TypeError, ASYNC_FOR_NO_AITER, type);
        }
        Object asyncIterator = callSlot.execute(frame, inliningTarget, slots.am_aiter(), receiver);
        if (!checkNode.execute(inliningTarget, asyncIterator)) {
            throw raiseNoANext.raise(inliningTarget, PythonBuiltinClassType.TypeError, ASYNC_FOR_NO_ANEXT_INITIAL, asyncIterator);
        }
        return asyncIterator;
    }
}
