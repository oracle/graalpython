/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.nodes.call;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class InvokeNode extends Node implements IndirectCallNode {
    protected static boolean shouldInlineGenerators() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.getCurrent().getEngineOption(PythonOptions.ForceInlineGeneratorCalls);
    }

    protected static boolean forceSplitBuiltins() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.getCurrent().getEngineOption(PythonOptions.EnableForcedSplits);
    }

    @TruffleBoundary
    protected static RootCallTarget getCallTarget(Object callee) {
        RootCallTarget callTarget;
        Object actualCallee = callee;
        if (actualCallee instanceof PFunction) {
            callTarget = ((PFunction) actualCallee).getCallTarget();
        } else if (actualCallee instanceof PBuiltinFunction) {
            callTarget = ((PBuiltinFunction) callee).getCallTarget();
        } else {
            throw new UnsupportedOperationException("Unsupported callee type " + actualCallee);
        }
        return callTarget;
    }

    protected static final void optionallySetClassBodySpecial(Object[] arguments, CallTarget callTarget, ConditionProfile isClassBodyProfile) {
        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
        if (isClassBodyProfile.profile(rootNode instanceof ClassBodyRootNode)) {
            assert PArguments.getSpecialArgument(arguments) == null : "there cannot be a special argument in a class body";
            PArguments.setSpecialArgument(arguments, rootNode);
        }
    }

    protected static boolean isBuiltin(Object callee) {
        return callee instanceof PBuiltinFunction || callee instanceof PBuiltinMethod;
    }

    public static Object invokeUncached(PBuiltinFunction callee, Object[] arguments) {
        return GenericInvokeNode.getUncached().execute(callee, arguments);
    }

    public static Object invokeUncached(RootCallTarget ct, Object[] arguments) {
        return GenericInvokeNode.getUncached().execute(ct, arguments);
    }
}

abstract class DirectInvokeNode extends InvokeNode {
    /**
     * Flags indicating if some child node of this root node (or a callee) eventually needs the
     * caller or exception state. Hence, the caller of this root node should provide the exception
     * state in the arguments.
     */
    @CompilationFinal private Assumption dontNeedExceptionState = createExceptionStateAssumption();
    @CompilationFinal private Assumption dontNeedCallerFrame = createCallerFrameAssumption();

    private static Assumption createCallerFrameAssumption() {
        return Truffle.getRuntime().createAssumption("does not need caller frame");
    }

    private static Assumption createExceptionStateAssumption() {
        return Truffle.getRuntime().createAssumption("does not need exception state");
    }

    @Override
    public Assumption needNotPassFrameAssumption() {
        return dontNeedCallerFrame;
    }

    @Override
    public Assumption needNotPassExceptionAssumption() {
        return dontNeedExceptionState;
    }

    @CompilationFinal private int state = 0;

    protected boolean profileIsNullFrame(boolean isNullFrame) {
        if (state == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isNullFrame) {
                state = 0x1;
            } else {
                state = 0x2;
            }
        }

        if (state == 0x1) {
            if (!isNullFrame) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("Invoke node was initialized for a null frame. Cannot use it with non-null frame now.");
            }
            return true;
        }
        assert state == 0x2;
        if (isNullFrame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("Invoke node was initialized for a non-null frame. Cannot use it with null frame now.");
        }
        return false;
    }

    @Override
    public Node copy() {
        DirectInvokeNode copy = (DirectInvokeNode) super.copy();
        copy.dontNeedCallerFrame = createCallerFrameAssumption();
        copy.dontNeedExceptionState = createExceptionStateAssumption();
        return copy;
    }
}
