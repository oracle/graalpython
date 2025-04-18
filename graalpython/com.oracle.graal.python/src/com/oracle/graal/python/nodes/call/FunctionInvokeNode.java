/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class FunctionInvokeNode extends DirectInvokeNode {
    @Child private DirectCallNode callNode;
    @Child private CallContext callContext;

    // Needed only for generator functions, will be null for builtins
    private final PFunction callee;
    private final PythonObject globals;
    private final PCell[] closure;
    protected final boolean isBuiltin;

    protected FunctionInvokeNode(PFunction callee, CallTarget callTarget, PythonObject globals, PCell[] closure, boolean isBuiltin, boolean isGenerator, boolean split) {
        this.callee = callee;
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        if (split) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && shouldInlineGenerators()) {
            this.callNode.forceInlining();
        }
        this.globals = globals;
        this.closure = closure;
        this.isBuiltin = isBuiltin;
        this.callContext = CallContext.create();
    }

    public abstract Object execute(VirtualFrame frame, Object[] arguments);

    @Specialization
    protected Object doDirect(VirtualFrame frame, Object[] arguments,
                    @Bind("this") Node inliningTarget,
                    @Cached InlinedConditionProfile isGeneratorFunctionProfile) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        RootCallTarget ct = (RootCallTarget) callNode.getCurrentCallTarget();
        optionallySetGeneratorFunction(inliningTarget, arguments, ct, isGeneratorFunctionProfile, callee);
        if (profileIsNullFrame(frame == null)) {
            PythonContext context = PythonContext.get(this);
            PythonThreadState threadState = context.getThreadState(context.getLanguage(this));
            Object state = IndirectCalleeContext.enter(threadState, arguments, ct);
            try {
                return callNode.call(arguments);
            } finally {
                IndirectCalleeContext.exit(threadState, state);
            }
        } else {
            callContext.prepareCall(frame, arguments, ct, this);
            return callNode.call(arguments);
        }
    }

    public final RootNode getCurrentRootNode() {
        return callNode.getCurrentRootNode();
    }

    @TruffleBoundary
    public static FunctionInvokeNode create(PFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        return FunctionInvokeNodeGen.create(callee, callTarget, callee.getGlobals(), callee.getClosure(), false, callTarget.getRootNode() instanceof PBytecodeGeneratorFunctionRootNode,
                        callee.forceSplitDirectCalls());
    }

    @TruffleBoundary
    public static FunctionInvokeNode create(PBuiltinFunction callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean split = forceSplitBuiltins() || (callee.getFunctionRootNode() instanceof BuiltinFunctionRootNode root && root.getBuiltin().forceSplitDirectCalls());
        return FunctionInvokeNodeGen.create(null, callTarget, null, null, true, false, split);
    }
}
