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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

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
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

abstract class AbstractInvokeNode extends Node {
    protected static RootCallTarget getCallTarget(PythonCallable callee) {
        RootCallTarget callTarget;
        PythonCallable actualCallee = callee;
        if (actualCallee instanceof PFunction) {
            callTarget = actualCallee.getCallTarget();
        } else if (actualCallee instanceof PMethod) {
            PMethod method = (PMethod) actualCallee;
            callTarget = method.__func__().getCallTarget();
        } else if (actualCallee instanceof PBuiltinFunction) {
            callTarget = callee.getCallTarget();
        } else if (callee instanceof PBuiltinMethod) {
            PBuiltinMethod method = (PBuiltinMethod) callee;
            PBuiltinFunction internalFunc = method.__func__();
            callTarget = internalFunc.getCallTarget();
        } else if (actualCallee instanceof PythonBuiltinClass) {
            actualCallee = (PythonCallable) ((PythonBuiltinClass) actualCallee).getAttribute(__NEW__);
            callTarget = actualCallee.getCallTarget();
        } else {
            throw new UnsupportedOperationException("Unsupported callee type " + actualCallee);
        }
        return callTarget;
    }

    protected static MaterializedFrame getCallerFrame(RootCallTarget callTarget) {
        RootNode rootNode = callTarget.getRootNode();
        MaterializedFrame callerFrame = null;
        if (rootNode instanceof PRootNode && ((PRootNode) rootNode).isWithCallerFrame()) {
            callerFrame = Truffle.getRuntime().getCurrentFrame().getFrame(FrameInstance.FrameAccess.MATERIALIZE).materialize();
        }
        return callerFrame;
    }

    protected static Arity getArity(PythonCallable callee) {
        if (callee instanceof PythonBuiltinClass) {
            return ((PythonCallable) ((PythonBuiltinClass) callee).getAttribute(__NEW__)).getArity();
        } else {
            return callee.getArity();
        }
    }

    protected static boolean isBuiltin(PythonCallable callee) {
        if (callee instanceof PBuiltinFunction || callee instanceof PBuiltinMethod || callee instanceof PythonBuiltinClass) {
            return true;
        } else {
            return false;
        }
    }
}

final class GenericInvokeNode extends AbstractInvokeNode {
    @Child private IndirectCallNode callNode = Truffle.getRuntime().createIndirectCallNode();
    @Child private ArityCheckNode arityCheck = ArityCheckNode.create();
    @Child private ApplyKeywordsNode applyKeywords = ApplyKeywordsNode.create();

    public static GenericInvokeNode create() {
        return new GenericInvokeNode();
    }

    @TruffleBoundary
    protected Object execute(PythonCallable callee, Object[] arguments, PKeyword[] keywords) {
        RootCallTarget callTarget = getCallTarget(callee);
        PArguments.setCallerFrame(arguments, getCallerFrame(callTarget));

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

public abstract class InvokeNode extends AbstractInvokeNode {
    @Child private DirectCallNode callNode;
    @Child private ArityCheckNode arityCheck = ArityCheckNode.create();
    private final Arity arity;
    private final PythonObject globals;
    private final PCell[] closure;
    protected final boolean isBuiltin;

    protected InvokeNode(CallTarget callTarget, Arity calleeArity, PythonObject globals, PCell[] closure, boolean isBuiltin) {
        this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        this.arity = calleeArity;
        this.globals = globals;
        this.closure = closure;
        this.isBuiltin = isBuiltin;
    }

    protected abstract Object execute(VirtualFrame frame, Object[] arguments, PKeyword[] keywords);

    public Object invoke(Object[] arguments, PKeyword[] keywords) {
        return execute(null, arguments, keywords);
    }

    public Object invoke(Object[] arguments) {
        return execute(null, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    @TruffleBoundary
    public static InvokeNode create(PythonCallable callee) {
        RootCallTarget callTarget = getCallTarget(callee);

        boolean builtin = isBuiltin(callee);
        if (builtin && shouldSplit(callee)) {
            callTarget = split(callTarget);
        }
        return InvokeNodeGen.create(callTarget, getArity(callee), callee.getGlobals(), callee.getClosure(), builtin);
    }

    /**
     * Replicate the CallTarget to let each builtin call site executes its own AST.
     */
    protected static RootCallTarget split(RootCallTarget callTarget) {
        CompilerAsserts.neverPartOfCompilation();
        RootNode rootNode = callTarget.getRootNode();
        return Truffle.getRuntime().createCallTarget(NodeUtil.cloneNode(rootNode));
    }

    protected static boolean shouldSplit(PythonCallable callee) {
        return callee.getName().equals(__INIT__) ||
                        callee.getName().equals(__GET__) ||
                        callee.getName().equals(__NEW__) ||
                        callee.getName().equals(__CALL__);
    }

    private MaterializedFrame getCallerFrame(VirtualFrame frame) {
        if (frame == null) {
            return null;
        }

        RootNode rootNode = this.callNode.getRootNode();
        if (rootNode instanceof PRootNode && ((PRootNode) rootNode).isWithCallerFrame()) {
            return frame.materialize();
        }
        return null;
    }

    @Specialization(guards = {"keywords.length == 0"})
    protected Object doNoKeywords(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) {
        PArguments.setGlobals(arguments, globals);
        PArguments.setClosure(arguments, closure);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame));
        arityCheck.execute(arity, arguments, keywords);
        return callNode.call(arguments);
    }

    @Specialization(guards = {"!isBuiltin"})
    protected Object doWithKeywords(VirtualFrame frame, Object[] arguments, PKeyword[] keywords,
                    @Cached("create()") ApplyKeywordsNode applyKeywords) {
        Object[] combined = applyKeywords.execute(arity, arguments, keywords);
        PArguments.setGlobals(combined, globals);
        PArguments.setClosure(combined, closure);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame));
        arityCheck.execute(arity, combined, PArguments.getKeywordArguments(combined));
        return callNode.call(combined);
    }

    @Specialization(guards = "isBuiltin")
    protected Object doBuiltinWithKeywords(VirtualFrame frame, Object[] arguments, PKeyword[] keywords) {
        PArguments.setKeywordArguments(arguments, keywords);
        PArguments.setCallerFrame(arguments, getCallerFrame(frame));
        arityCheck.execute(arity, arguments, keywords);
        return callNode.call(arguments);
    }
}
