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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PythonOptions.class)
@ReportPolymorphism
public abstract class CallDispatchNode extends Node {

    protected static InvokeNode createInvokeNode(PythonCallable callee) {
        return InvokeNode.create(callee);
    }

    protected static CallTargetInvokeNode createCtInvokeNode(PythonCallable callee) {
        return CallTargetInvokeNode.create(callee);
    }

    public static CallDispatchNode create() {
        return CallDispatchNodeGen.create();
    }

    protected Assumption singleContextAssumption() {
        PythonLanguage language = getRootNode().getLanguage(PythonLanguage.class);
        if (language == null) {
            language = PythonLanguage.getCurrent();
        }
        return language.singleContextAssumption;
    }

    public abstract Object executeCall(VirtualFrame frame, Object callee, Object[] arguments, PKeyword[] keywords);

    @SuppressWarnings("unused")
    @Specialization(guards = "method.getFunction() == cachedCallee", limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    protected Object callBuiltinMethod(VirtualFrame frame, PBuiltinMethod method, Object[] arguments, PKeyword[] keywords,
                    @Cached("method.getFunction()") PBuiltinFunction cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") InvokeNode invoke) {
        return invoke.execute(frame, arguments, keywords);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "method.getFunction().getCallTarget() == ct", limit = "getCallSiteInlineCacheMaxDepth()")
    protected Object callBuiltinMethod(VirtualFrame frame, PBuiltinMethod method, Object[] arguments, PKeyword[] keywords,
                    @Cached("method.getFunction().getCallTarget()") RootCallTarget ct,
                    @Cached("createCtInvokeNode(method)") CallTargetInvokeNode invoke) {
        return invoke.execute(frame, method.getGlobals(), method.getClosure(), arguments, keywords);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "callee == cachedCallee", limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "singleContextAssumption()")
    protected Object callFunction(VirtualFrame frame, PythonCallable callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("callee") PythonCallable cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") InvokeNode invoke) {
        return invoke.execute(frame, arguments, keywords);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "callee.getCallTarget() == ct", limit = "getCallSiteInlineCacheMaxDepth()")
    protected Object callFunction(VirtualFrame frame, PFunction callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("callee.getCallTarget()") RootCallTarget ct,
                    @Cached("createCtInvokeNode(callee)") CallTargetInvokeNode invoke) {
        return invoke.execute(frame, callee.getGlobals(), callee.getClosure(), arguments, keywords);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "callee.getCallTarget() == ct", limit = "getCallSiteInlineCacheMaxDepth()")
    protected Object callFunction(VirtualFrame frame, PBuiltinFunction callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("callee.getCallTarget()") RootCallTarget ct,
                    @Cached("createCtInvokeNode(callee)") CallTargetInvokeNode invoke) {
        return invoke.execute(frame, callee.getGlobals(), callee.getClosure(), arguments, keywords);
    }

    @Specialization(replaces = {"callBuiltinMethod", "callFunction"})
    protected Object callGeneric(VirtualFrame frame, PythonCallable callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("create()") GenericInvokeNode invoke) {
        return invoke.execute(frame, callee, arguments, keywords);
    }
}
