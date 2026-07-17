/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.asyncio;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotUnaryFunc.CallSlotUnaryNode;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/** Equivalent to CPython's {@code _PyCoro_GetAwaitableIter}. */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class PyCoroGetAwaitableIterNode extends Node {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object object);

    @Specialization(guards = "generator.isCoroutine()")
    static Object doCoroutine(PGenerator generator) {
        return generator;
    }

    @Fallback
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached GetClassNode getObjectType,
                    @Cached GetCachedTpSlotsNode getSlots,
                    @Cached CallSlotUnaryNode callSlot,
                    @Cached PyIterCheckNode iterCheck,
                    @Cached PRaiseNode raiseNoAwait,
                    @Cached PRaiseNode raiseNotIter) {
        Object type = getObjectType.execute(inliningTarget, object);
        TpSlots slots = getSlots.execute(inliningTarget, type);
        if (slots.am_await() == null) {
            throw raiseNoAwait.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_BE_USED_AWAIT, type);
        }
        Object iterator = callSlot.execute(frame, inliningTarget, slots.am_await(), object);
        if (iterator instanceof PGenerator generator && generator.isCoroutine()) {
            throw raiseNotIter.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.AWAIT_RETURN_COROUTINE);
        }
        if (!iterCheck.execute(inliningTarget, iterator)) {
            throw raiseNotIter.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.AWAIT_RETURN_NON_ITER, iterator);
        }
        return iterator;
    }

}
