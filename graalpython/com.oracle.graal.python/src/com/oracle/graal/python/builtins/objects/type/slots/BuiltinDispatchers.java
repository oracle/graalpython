/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type.slots;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltinCallTargetRegistry;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public class BuiltinDispatchers {
    static Object callGenericBuiltin(VirtualFrame frame, Node inliningTarget, int callTargetIndex, Object[] arguments,
                    CallContext callContext, InlinedConditionProfile isNullFrameProfile, IndirectCallNode indirectCallNode) {
        PythonLanguage language = PythonLanguage.get(inliningTarget);
        CallTarget callTarget = TpSlotBuiltinCallTargetRegistry.getCallTarget(language, callTargetIndex);
        if (isNullFrameProfile.profile(inliningTarget, frame != null)) {
            callContext.prepareIndirectCall(frame, arguments, inliningTarget);
            return indirectCallNode.call(callTarget, arguments);
        } else {
            return callWithNullFrame(inliningTarget, indirectCallNode, language, arguments, callTarget);
        }
    }

    private static Object callWithNullFrame(Node inliningTarget, IndirectCallNode indirectCallNode, PythonLanguage language, Object[] arguments, CallTarget callTarget) {
        PythonContext context = PythonContext.get(inliningTarget);
        PythonThreadState threadState = context.getThreadState(language);
        Object state = IndirectCalleeContext.enterIndirect(threadState, arguments);
        try {
            return indirectCallNode.call(callTarget, arguments);
        } finally {
            IndirectCalleeContext.exit(threadState, state);
        }
    }
}
