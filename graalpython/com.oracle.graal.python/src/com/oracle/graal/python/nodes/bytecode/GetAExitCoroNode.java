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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
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
public abstract class GetAExitCoroNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop);

    @Specialization
    int exit(VirtualFrame virtualFrame, int stackTopIn,
                    @Bind Node inliningTarget,
                    @Cached CallQuaternaryMethodNode callExit,
                    @Cached GetClassNode getClassNode,
                    @Cached ExceptionNodes.GetTracebackNode getTracebackNode) {
        int stackTop = stackTopIn;
        Object exception = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object exit = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        Object contextManager = virtualFrame.getObject(stackTop);
        virtualFrame.setObject(stackTop--, null);
        if (exception == PNone.NONE) {
            Object result = callExit.execute(virtualFrame, exit, contextManager, PNone.NONE, PNone.NONE, PNone.NONE);
            virtualFrame.setObject(++stackTop, PNone.NONE);
            virtualFrame.setObject(++stackTop, result);
        } else {
            Object pythonException = exception;
            if (exception instanceof PException) {
                PArguments.setException(virtualFrame, (PException) exception);
                pythonException = ((PException) exception).getEscapedException();
            }
            Object excType = getClassNode.execute(inliningTarget, pythonException);
            Object excTraceback = getTracebackNode.execute(inliningTarget, pythonException);
            Object result = callExit.execute(virtualFrame, exit, contextManager, excType, pythonException, excTraceback);
            virtualFrame.setObject(++stackTop, exception);
            virtualFrame.setObject(++stackTop, result);
        }
        return stackTop;
    }

    public static GetAExitCoroNode create() {
        return GetAExitCoroNodeGen.create();
    }

    public static GetAExitCoroNode getUncached() {
        return GetAExitCoroNodeGen.getUncached();
    }
}
