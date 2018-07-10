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

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PythonOptions.class)
public abstract class CallDispatchNode extends Node {

    protected static InvokeNode createInvokeNode(PythonCallable callee) {
        return InvokeNode.create(callee);
    }

    public static CallDispatchNode create() {
        return CallDispatchNodeGen.create();
    }

    public abstract Object executeCall(Object callee, Object[] arguments, PKeyword[] keywords);

    /**
     * We have to treat PMethods specially, because we want the PIC to be on the function, not on
     * the (transient) bound method.
     */
    @SuppressWarnings("unused")
    @Specialization(guards = "method.getFunction() == cachedCallee", limit = "getCallSiteInlineCacheMaxDepth()")
    protected Object callMethod(PMethod method, Object[] arguments, PKeyword[] keywords,
                    @Cached("method.getFunction()") PFunction cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") InvokeNode invoke) {
        return invoke.invoke(arguments, keywords);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "method.getFunction() == cachedCallee", limit = "getCallSiteInlineCacheMaxDepth()")
    protected Object callBuiltinMethod(PBuiltinMethod method, Object[] arguments, PKeyword[] keywords,
                    @Cached("method.getFunction()") PBuiltinFunction cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") InvokeNode invoke) {
        return invoke.invoke(arguments, keywords);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "callee == cachedCallee", limit = "getCallSiteInlineCacheMaxDepth()")
    protected Object callFunction(PythonCallable callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("callee") PythonCallable cachedCallee,
                    @Cached("createInvokeNode(cachedCallee)") InvokeNode invoke) {
        return invoke.invoke(arguments, keywords);
    }

    @Specialization(replaces = {"callMethod", "callBuiltinMethod", "callFunction"})
    protected Object callGeneric(PythonCallable callee, Object[] arguments, PKeyword[] keywords,
                    @Cached("create()") GenericInvokeNode invoke) {
        return invoke.execute(callee, arguments, keywords);
    }
}
