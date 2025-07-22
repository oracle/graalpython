/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AEXIT__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@GenerateInline(false) // used in BCI root node
public abstract class SetupAwithNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    static int setup(VirtualFrame frame, int stackTopIn,
                    @Bind Node inliningTarget,
                    @Cached GetClassNode getClassNode,
                    @Cached LookupSpecialMethodNode.Dynamic lookupAEnter,
                    @Cached LookupSpecialMethodNode.Dynamic lookupAExit,
                    @Cached CallUnaryMethodNode callEnter,
                    @Cached PRaiseNode raiseNode) {
        int stackTop = stackTopIn;
        Object contextManager = frame.getObject(stackTop);
        Object type = getClassNode.execute(inliningTarget, contextManager);
        Object enter = lookupAEnter.execute(frame, inliningTarget, type, T___AENTER__, contextManager);
        if (enter == PNone.NO_VALUE) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_THE_ASYNC_CONTEXT_MANAGER_PROTOCOL, type);
        }
        Object exit = lookupAExit.execute(frame, inliningTarget, type, T___AEXIT__, contextManager);
        if (exit == PNone.NO_VALUE) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.N_OBJECT_DOES_NOT_SUPPORT_THE_ASYNC_CONTEXT_MANAGER_PROTOCOL_AEXIT, type);
        }
        Object res = callEnter.executeObject(frame, enter, contextManager);
        frame.setObject(++stackTop, exit);
        frame.setObject(++stackTop, res);
        return stackTop;
    }

    public static SetupAwithNode create() {
        return SetupAwithNodeGen.create();
    }

    public static SetupAwithNode getUncached() {
        return SetupAwithNodeGen.getUncached();
    }
}
