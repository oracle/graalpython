/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@CoreFunctions(defineModule = "atexit")
public class AtexitModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AtexitModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "register", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class RegisterNode extends PythonVarargsBuiltinNode {
        private static class AtExitCallTarget extends RootNode {
            @Child private CallNode callNode = CallNode.create();

            private final ContextReference<PythonContext> contextRef = lookupContextReference(PythonLanguage.class);

            private Object callable;
            private Object[] arguments;
            private PKeyword[] keywords;

            protected AtExitCallTarget(TruffleLanguage<?> language, Object callable, Object[] arguments, PKeyword[] keywords) {
                super(language);
                this.callable = callable;
                this.arguments = arguments;
                this.keywords = keywords;
            }

            @Override
            @SuppressWarnings("try")
            public Object execute(VirtualFrame frame) {
                PythonContext context = contextRef.get();
                context.setTopFrameInfo(PFrame.Reference.EMPTY);
                context.setCaughtException(PException.NO_EXCEPTION);

                // We deliberately pass 'null' frame here, the execution state will then be taken
                // from the context.
                try {
                    return callNode.execute(null, callable, arguments, keywords);
                } finally {
                    context.popTopFrameInfo();
                    context.setCaughtException(null);
                }
            }
        }

        @Specialization
        Object register(Object callable, Object[] arguments, PKeyword[] keywords) {
            CompilerDirectives.transferToInterpreter();
            AtExitCallTarget atExitCallTarget = new AtExitCallTarget(getContext().getLanguage(), callable, arguments, keywords);
            getContext().registerShutdownHook(callable, Truffle.getRuntime().createCallTarget(atExitCallTarget));
            return callable;
        }
    }

    @Builtin(name = "unregister", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UnregisterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object register(Object callable) {
            getContext().deregisterShutdownHook(callable);
            return PNone.NONE;
        }
    }
}
