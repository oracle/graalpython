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

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.runtime.ExecutionContext.CallContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class GenericInvokeNode extends AbstractInvokeNode {
    private static final GenericInvokeUncachedNode UNCACHED = new GenericInvokeUncachedNode();

    @Child private IndirectCallNode callNode;
    @Child private CallContext callContext;

    private final ConditionProfile isNullFrameProfile;

    public static GenericInvokeNode create() {
        return new GenericInvokeCachedNode();
    }

    public static GenericInvokeNode getUncached() {
        return UNCACHED;
    }

    public GenericInvokeNode(IndirectCallNode callNode, CallContext callContext, ConditionProfile isNullFrameProfile) {
        this.callNode = callNode;
        this.callContext = callContext;
        this.isNullFrameProfile = isNullFrameProfile;
    }

    private Object doCall(VirtualFrame frame, RootCallTarget callTarget, Object[] arguments) {
        optionallySetClassBodySpecial(arguments, callTarget);
        if (isNullFrameProfile.profile(frame == null)) {
            PythonContext context = getContextRef().get();
            PFrame.Reference frameInfo = IndirectCalleeContext.enter(context, arguments, callTarget);
            try {
                return callNode.call(callTarget, arguments);
            } finally {
                IndirectCalleeContext.exit(context, frameInfo);
            }
        } else {
            callContext.prepareCall(frame, arguments, callTarget, this);
            return callNode.call(callTarget, arguments);
        }
    }

    public Object execute(VirtualFrame frame, PFunction callee, Object[] arguments) {
        PArguments.setGlobals(arguments, callee.getGlobals());
        PArguments.setClosure(arguments, callee.getClosure());
        RootCallTarget callTarget = getCallTarget(callee);
        return doCall(frame, callTarget, arguments);
    }

    public Object execute(VirtualFrame frame, PBuiltinFunction callee, Object[] arguments) {
        RootCallTarget callTarget = getCallTarget(callee);
        return doCall(frame, callTarget, arguments);
    }

    public Object execute(VirtualFrame frame, RootCallTarget callTarget, Object[] arguments) {
        return doCall(frame, callTarget, arguments);
    }

    private static final class GenericInvokeCachedNode extends GenericInvokeNode {

        public GenericInvokeCachedNode() {
            super(IndirectCallNode.create(), CallContext.create(), ConditionProfile.createBinaryProfile());
        }

    }

    private static final class GenericInvokeUncachedNode extends GenericInvokeNode {
        public GenericInvokeUncachedNode() {
            super(IndirectCallNode.getUncached(), CallContext.getUncached(), ConditionProfile.getUncached());
        }

        @Override
        public boolean isAdoptable() {
            return false;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.MEGAMORPHIC;
        }
    }
}
