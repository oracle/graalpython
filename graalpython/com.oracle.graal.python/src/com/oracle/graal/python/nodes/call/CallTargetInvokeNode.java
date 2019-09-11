/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class CallTargetInvokeNode extends DirectInvokeNode {
    @Child private DirectCallNode callNode;
    @Child private CallContext callContext;
    protected final boolean isBuiltin;

    protected CallTargetInvokeNode(CallTarget callTarget, boolean isBuiltin, boolean isGenerator) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        if (isBuiltin) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && shouldInlineGenerators()) {
            this.callNode.forceInlining();
        }
        this.callContext = CallContext.create();
        this.isBuiltin = isBuiltin;
    }

    @TruffleBoundary
    public static CallTargetInvokeNode create(PFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return CallTargetInvokeNodeGen.create(callTarget, builtin, callee.isGeneratorFunction());
    }

    @TruffleBoundary
    public static CallTargetInvokeNode create(PBuiltinFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return CallTargetInvokeNodeGen.create(callTarget, builtin, false);
    }

    public static CallTargetInvokeNode create(CallTarget callTarget, boolean isBuiltin, boolean isGenerator) {
        return CallTargetInvokeNodeGen.create(callTarget, isBuiltin, isGenerator);
    }

    public final Object execute(VirtualFrame frame, Object[] arguments) {
        return execute(frame, null, null, arguments);
    }

    public abstract Object execute(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments);

    @Specialization(guards = {"globals == null", "closure == null"})
    protected Object doNoClosure(VirtualFrame frame, @SuppressWarnings("unused") PythonObject globals, @SuppressWarnings("unused") PCell[] closure, Object[] arguments) {
        RootCallTarget ct = (RootCallTarget) callNode.getCurrentCallTarget();
        optionallySetClassBodySpecial(arguments, ct);

        // If the frame is 'null', we expect the execution state (i.e. caller info and exception
        // state) in the context. There are two common reasons for having a 'null' frame:
        // 1. This node is the first invoke node used via interop.
        // 2. This invoke node is (indirectly) used behind a TruffleBoundary.
        // This is preferably prepared using 'IndirectCallContext.enter'.
        if (profileIsNullFrame(frame == null)) {
            PythonContext context = getContextRef().get();
            PFrame.Reference frameInfo = IndirectCalleeContext.enter(context, arguments, ct);
            try {
                return callNode.call(arguments);
            } finally {
                IndirectCalleeContext.exit(context, frameInfo);
            }
        } else {
            callContext.prepareCall(frame, arguments, ct, this);
            return callNode.call(arguments);
        }
    }

    @Specialization(replaces = "doNoClosure")
    protected Object doGeneric(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        return doNoClosure(frame, null, null, arguments);
    }

    public final CallTarget getCallTarget() {
        return callNode.getCallTarget();
    }

    public final RootNode getCurrentRootNode() {
        return callNode.getCurrentRootNode();
    }
}
