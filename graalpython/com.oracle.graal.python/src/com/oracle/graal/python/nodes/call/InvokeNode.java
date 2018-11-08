/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.argument.ApplyKeywordsNode;
import com.oracle.graal.python.nodes.argument.ArityCheckNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;

abstract class AbstractInvokeNode extends Node {

    private final ConditionProfile needsFrameProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isClassBodyProfile = ConditionProfile.createBinaryProfile();

    protected static boolean shouldInlineGenerators() {
        return PythonOptions.getOption(PythonLanguage.getContextRef().get(), PythonOptions.ForceInlineGeneratorCalls);
    }

    @TruffleBoundary
    protected static RootCallTarget getCallTarget(PythonCallable callee) {
        RootCallTarget callTarget;
        PythonCallable actualCallee = callee;
        if (actualCallee instanceof PFunction) {
            callTarget = actualCallee.getCallTarget();
        } else if (actualCallee instanceof PMethod) {
            PMethod method = (PMethod) actualCallee;
            callTarget = method.getFunction().getCallTarget();
        } else if (actualCallee instanceof PBuiltinFunction) {
            callTarget = callee.getCallTarget();
        } else if (callee instanceof PBuiltinMethod) {
            PBuiltinMethod method = (PBuiltinMethod) callee;
            PBuiltinFunction internalFunc = method.getFunction();
            callTarget = internalFunc.getCallTarget();
        } else if (actualCallee instanceof PythonBuiltinClass) {
            actualCallee = (PythonCallable) ((PythonBuiltinClass) actualCallee).getAttribute(__NEW__);
            callTarget = actualCallee.getCallTarget();
        } else {
            throw new UnsupportedOperationException("Unsupported callee type " + actualCallee);
        }
        return callTarget;
    }

    protected final MaterializedFrame getCallerFrame(VirtualFrame frame, CallTarget callTarget) {
        if (frame == null) {
            return null;
        }

        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
        if (needsFrameProfile.profile(rootNode instanceof PRootNode && ((PRootNode) rootNode).needsCallerFrame())) {
            return frame.materialize();
        }
        return null;
    }

    protected final void optionallySetClassBodySpecial(Object[] arguments, CallTarget callTarget) {
        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
        if (isClassBodyProfile.profile(rootNode instanceof ClassBodyRootNode)) {
            PArguments.setSpecialArgument(arguments, rootNode);
        }
    }

    @TruffleBoundary
    protected static Arity getArity(PythonCallable callee) {
        if (callee instanceof PythonBuiltinClass) {
            return ((PythonCallable) ((PythonBuiltinClass) callee).getAttribute(__NEW__)).getArity();
        } else {
            return callee.getArity();
        }
    }

    protected static boolean isBuiltin(PythonCallable callee) {
        return callee instanceof PBuiltinFunction || callee instanceof PBuiltinMethod || callee instanceof PythonBuiltinClass;
    }
}

final class GenericInvokeNode extends AbstractInvokeNode {
    @Child private IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();
    @Child private ArityCheckNode arityCheck = ArityCheckNode.create();
    @Child private ApplyKeywordsNode applyKeywords = ApplyKeywordsNode.create();

    public static GenericInvokeNode create() {
        return new GenericInvokeNode();
    }

    protected Object execute(VirtualFrame frame, PythonCallable callee, Object[] arguments, PKeyword[] keywords) {
        RootCallTarget callTarget = getCallTarget(callee);
        MaterializedFrame callerFrame = getCallerFrame(frame, callTarget);
        PArguments.setCallerFrame(arguments, callerFrame);
        optionallySetClassBodySpecial(arguments, callTarget);
        Arity arity = getArity(callee);
        if (isBuiltin(callee)) {
            PArguments.setKeywordArguments(arguments, keywords);
            arityCheck.execute(arity, arguments, keywords);
            return callNode.call(callTarget, arguments);
        } else {
            Object[] combined = applyKeywords.execute(arity, arguments, keywords);
            PArguments.setGlobals(combined, callee.getGlobals());
            PArguments.setClosure(combined, callee.getClosure());
            arityCheck.execute(arity, combined, PArguments.getKeywordArguments(combined));
            return callNode.call(callTarget, combined);
        }
    }
}

abstract class CallTargetInvokeNode extends AbstractInvokeNode {
    @Child private DirectCallNode callNode;
    @Child private ArityCheckNode arityCheck = ArityCheckNode.create();
    private final Arity arity;
    protected final boolean isBuiltin;

    protected CallTargetInvokeNode(CallTarget callTarget, Arity arity, boolean isBuiltin, boolean isGenerator) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        this.arity = arity;
        if (isBuiltin) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && shouldInlineGenerators()) {
            this.callNode.forceInlining();
        }
        this.isBuiltin = isBuiltin;
    }

    @TruffleBoundary
    public static CallTargetInvokeNode create(PythonCallable callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return CallTargetInvokeNodeGen.create(callTarget, callee.getArity(), builtin, callee.isGeneratorFunction());
    }

    public abstract Object execute(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments, PKeyword[] keywords);

    @Specialization(guards = {"keywords.length == 0"})
    protected Object doNoKeywords(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments, PKeyword[] keywords) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame, callNode.getCallTarget()));
        optionallySetClassBodySpecial(arguments, callNode.getCallTarget());
        arityCheck.execute(arity, arguments, keywords);
        return callNode.call(arguments);
    }

    @Specialization(guards = {"!isBuiltin"})
    protected Object doWithKeywords(VirtualFrame frame, PythonObject globals, PCell[] closure, Object[] arguments, PKeyword[] keywords,
                    @Cached("create()") ApplyKeywordsNode applyKeywords) {
        Object[] combined = applyKeywords.execute(arity, arguments, keywords);
        PArguments.setGlobals(combined, globals);
        PArguments.setClosure(combined, closure);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame, callNode.getCallTarget()));
        optionallySetClassBodySpecial(arguments, callNode.getCallTarget());
        arityCheck.execute(arity, combined, PArguments.getKeywordArguments(combined));
        return callNode.call(combined);
    }

    @Specialization(guards = "isBuiltin")
    protected Object doBuiltinWithKeywords(VirtualFrame frame, @SuppressWarnings("unused") PythonObject globals, @SuppressWarnings("unused") PCell[] closure, Object[] arguments,
                    PKeyword[] keywords) {
        PArguments.setKeywordArguments(arguments, keywords);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame, callNode.getCallTarget()));
        optionallySetClassBodySpecial(arguments, callNode.getCallTarget());
        arityCheck.execute(arity, arguments, keywords);
        return callNode.call(arguments);
    }
}

public abstract class InvokeNode extends AbstractInvokeNode {
    @Child private DirectCallNode callNode;
    @Child private ArityCheckNode arityCheck = ArityCheckNode.create();
    private final Arity arity;
    private final PythonObject globals;
    private final PCell[] closure;
    protected final boolean isBuiltin;

    protected InvokeNode(CallTarget callTarget, Arity calleeArity, PythonObject globals, PCell[] closure, boolean isBuiltin, boolean isGenerator) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        if (isBuiltin) {
            callNode.cloneCallTarget();
        }
        if (isGenerator && shouldInlineGenerators()) {
            this.callNode.forceInlining();
        }
        this.arity = calleeArity;
        this.globals = globals;
        this.closure = closure;
        this.isBuiltin = isBuiltin;
    }

    public abstract Object execute(VirtualFrame frame, Object[] arguments, PKeyword[] keywords);

    @TruffleBoundary
    public static InvokeNode create(PythonCallable callee) {
        RootCallTarget callTarget = getCallTarget(callee);
        boolean builtin = isBuiltin(callee);
        return InvokeNodeGen.create(callTarget, getArity(callee), callee.getGlobals(), callee.getClosure(), builtin, callee.isGeneratorFunction());
    }

    @Specialization(guards = {"keywords.length == 0"})
    protected Object doNoKeywords(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame, callNode.getCallTarget()));
        optionallySetClassBodySpecial(arguments, callNode.getCallTarget());
        arityCheck.execute(arity, arguments, keywords);
        return callNode.call(arguments);
    }

    @Specialization(guards = {"!isBuiltin"})
    protected Object doWithKeywords(VirtualFrame frame, Object[] arguments, PKeyword[] keywords,
                    @Cached("create()") ApplyKeywordsNode applyKeywords) {
        Object[] combined = applyKeywords.execute(arity, arguments, keywords);
        PArguments.setGlobals(combined, globals);
        PArguments.setClosure(combined, closure);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame, callNode.getCallTarget()));
        optionallySetClassBodySpecial(arguments, callNode.getCallTarget());
        arityCheck.execute(arity, combined, PArguments.getKeywordArguments(combined));
        return callNode.call(combined);
    }

    @Specialization(guards = "isBuiltin")
    protected Object doBuiltinWithKeywords(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) {
        PArguments.setKeywordArguments(arguments, keywords);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame, callNode.getCallTarget()));
        optionallySetClassBodySpecial(arguments, callNode.getCallTarget());
        arityCheck.execute(arity, arguments, keywords);
        return callNode.call(arguments);
    }
}
