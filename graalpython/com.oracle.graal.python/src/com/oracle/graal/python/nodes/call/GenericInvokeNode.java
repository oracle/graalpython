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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

@GenerateUncached
public abstract class GenericInvokeNode extends AbstractInvokeNode {
    public static GenericInvokeNode create() {
        return GenericInvokeNodeGen.create();
    }

    public static GenericInvokeNode getUncached() {
        return GenericInvokeNodeGen.getUncached();
    }

    protected GenericInvokeNode() {
    }

    /**
     * To be used when this node is called adopted.
     *
     * @param frame - the current frame
     * @param callee - either a {@link PFunction}, {@link PBuiltinFunction}, or
     *            {@link RootCallTarget}.
     * @param arguments - the complete (runtime + user) frame arguments for the call
     */
    public final Object execute(VirtualFrame frame, Object callee, Object[] arguments) {
        assert frame != null;
        return executeInternal(frame, callee, arguments);
    }

    protected abstract Object executeInternal(Frame frame, Object callee, Object[] arguments);

    /**
     * Can be used when this node is called unadopted or from a place where no frame is available.
     * However, it will be slower than passing a frame, because the threadstate is read from the
     * Python context.
     * 
     * @param callee - either a {@link PFunction}, {@link PBuiltinFunction}, or
     *            {@link RootCallTarget}.
     * @param arguments - the complete (runtime + user) frame arguments for the call
     */
    public final Object execute(Object callee, Object[] arguments) {
        return executeInternal(null, callee, arguments);
    }

    private Object doCall(Frame frame, RootCallTarget callTarget, Object[] arguments,
                    PythonContext context, IndirectCallNode callNode, CallContext callContext, ConditionProfile isNullFrameProfile, ConditionProfile isClassBodyProfile) {
        optionallySetClassBodySpecial(arguments, callTarget, isClassBodyProfile);
        if (isNullFrameProfile.profile(frame == null)) {
            PFrame.Reference frameInfo = IndirectCalleeContext.enter(context, arguments, callTarget);
            try {
                return callNode.call(callTarget, arguments);
            } finally {
                IndirectCalleeContext.exit(context, frameInfo);
            }
        } else {
            assert frame instanceof VirtualFrame : "GenericInvokeNode should not be executed with non-virtual frames";
                callContext.prepareCall((VirtualFrame) frame, arguments, callTarget, this);
            return callNode.call(callTarget, arguments);
        }
    }

    @Specialization
    Object invokeFunction(Frame frame, PFunction callee, Object[] arguments,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @Shared("callNode") @Cached IndirectCallNode callNode,
                    @Shared("callContext") @Cached CallContext callContext,
                    @Shared("isNullFrameProfile") @Cached("createBinaryProfile()") ConditionProfile isNullFrameProfile,
                    @Shared("isClassBodyProfile") @Cached("createBinaryProfile()") ConditionProfile isClassBodyProfile) {
        PArguments.setGlobals(arguments, callee.getGlobals());
        PArguments.setClosure(arguments, callee.getClosure());
        RootCallTarget callTarget = getCallTarget(callee);
        return doCall(frame, callTarget, arguments, context, callNode, callContext, isNullFrameProfile, isClassBodyProfile);
    }

    @Specialization
    Object invokeBuiltin(Frame frame, PBuiltinFunction callee, Object[] arguments,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @Shared("callNode") @Cached IndirectCallNode callNode,
                    @Shared("callContext") @Cached CallContext callContext,
                    @Shared("isNullFrameProfile") @Cached("createBinaryProfile()") ConditionProfile isNullFrameProfile,
                    @Shared("isClassBodyProfile") @Cached("createBinaryProfile()") ConditionProfile isClassBodyProfile) {
        RootCallTarget callTarget = getCallTarget(callee);
        return doCall(frame, callTarget, arguments, context, callNode, callContext, isNullFrameProfile, isClassBodyProfile);
    }

    @Specialization
    Object invokeCallTarget(Frame frame, RootCallTarget callTarget, Object[] arguments,
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @Shared("callNode") @Cached IndirectCallNode callNode,
                    @Shared("callContext") @Cached CallContext callContext,
                    @Shared("isNullFrameProfile") @Cached("createBinaryProfile()") ConditionProfile isNullFrameProfile,
                    @Shared("isClassBodyProfile") @Cached("createBinaryProfile()") ConditionProfile isClassBodyProfile) {
        return doCall(frame, callTarget, arguments, context, callNode, callContext, isNullFrameProfile, isClassBodyProfile);
    }
}
