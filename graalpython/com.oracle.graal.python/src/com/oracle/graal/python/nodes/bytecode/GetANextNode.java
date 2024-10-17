/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.ErrorMessages.ANEXT_INVALID_OBJECT;
import static com.oracle.graal.python.nodes.ErrorMessages.ASYNC_FOR_NO_ANEXT_ITERATION;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.asyncio.GetAwaitableNode;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
@GenerateInline(false) // used in bytecode root node
public abstract class GetANextNode extends PNodeWithContext {
    public abstract Object execute(VirtualFrame frame, Object receiver);

    public static GetANextNode getUncached() {
        return GetANextNodeGen.getUncached();
    }

    public static GetANextNode create() {
        return GetANextNodeGen.create();
    }

    @Specialization
    Object doGeneric(VirtualFrame frame, Object receiver,
                    @Bind("this") Node inliningTarget,
                    @Cached(parameters = "ANext") LookupSpecialMethodSlotNode getANext,
                    @Cached GetClassNode getAsyncIterType,
                    @Cached PRaiseNode raiseNoANext,
                    @Cached InlinedBranchProfile errorProfile,
                    @Cached CallUnaryMethodNode callANext,
                    @Cached PRaiseNode raiseInvalidObject,
                    @Cached(neverDefault = true) GetAwaitableNode getAwaitable) {
        Object type = getAsyncIterType.execute(inliningTarget, receiver);
        Object getter = getANext.execute(frame, type, receiver);
        if (getter == PNone.NO_VALUE) {
            errorProfile.enter(inliningTarget);
            throw raiseNoANext.raise(PythonBuiltinClassType.TypeError, ASYNC_FOR_NO_ANEXT_ITERATION, receiver);
        }
        Object anext = callANext.executeObject(frame, getter, receiver);
        try {
            return getAwaitable.execute(frame, anext);
        } catch (PException e) {
            errorProfile.enter(inliningTarget);
            throw raiseInvalidObject.raiseWithCause(PythonBuiltinClassType.TypeError, e, ANEXT_INVALID_OBJECT, anext);
        }
    }
}
