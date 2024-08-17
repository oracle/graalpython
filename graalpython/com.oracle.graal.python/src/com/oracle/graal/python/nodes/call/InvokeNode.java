/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetCallTargetNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorFunctionRootNode;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLGeneratorFunctionRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class InvokeNode extends Node {
    protected static boolean shouldInlineGenerators() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.get(null).getEngineOption(PythonOptions.ForceInlineGeneratorCalls);
    }

    protected static boolean forceSplitBuiltins() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonLanguage.get(null).getEngineOption(PythonOptions.EnableForcedSplits);
    }

    @TruffleBoundary
    protected static RootCallTarget getCallTarget(Object callee) {
        Object actualCallee = callee;
        RootCallTarget callTarget = GetCallTargetNode.getUncached().execute(actualCallee);
        if (callTarget == null) {
            throw CompilerDirectives.shouldNotReachHere("Unsupported callee type " + actualCallee);
        }
        return callTarget;
    }

    protected static void optionallySetGeneratorFunction(Node inliningTarget, Object[] arguments, CallTarget callTarget, InlinedConditionProfile isGeneratorFunctionProfile, PFunction callee) {
        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
        if (isGeneratorFunctionProfile.profile(inliningTarget, isGeneratorFunction(rootNode))) {
            assert callee != null : "generator function callee not passed to invoke node";
            PArguments.setGeneratorFunction(arguments, callee);
        }
    }

    private static boolean isGeneratorFunction(RootNode rootNode) {
        return rootNode instanceof PBytecodeGeneratorFunctionRootNode || rootNode instanceof PBytecodeDSLGeneratorFunctionRootNode;
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
}
