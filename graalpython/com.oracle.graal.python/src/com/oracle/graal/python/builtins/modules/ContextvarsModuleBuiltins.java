/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J__CONTEXTVARS;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__CONTEXTVARS)
public final class ContextvarsModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextvarsModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "copy_context", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetDefaultEncodingNode extends PythonBuiltinNode {
        @Specialization
        protected Object copyCtx(
                        @Cached PythonObjectFactory factory) {
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            return factory.copyContextVarsContext(threadState.getContextVarsContext());
        }
    }

    @Builtin(name = "ContextVar", minNumOfPositionalArgs = 2, parameterNames = {"cls", "name", "default"}, constructsClass = PythonBuiltinClassType.ContextVar)
    @GenerateNodeFactory
    public abstract static class ContextVarNode extends PythonTernaryBuiltinNode {

        @Specialization
        protected static Object constructDef(@SuppressWarnings("unused") Object cls, TruffleString name, Object def,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile noValueProfile,
                        @Cached PythonObjectFactory factory) {
            if (noValueProfile.profile(inliningTarget, isNoValue(def))) {
                def = PContextVar.NO_DEFAULT;
            }
            return factory.createContextVar(name, def);
        }
    }

    @Builtin(name = "Context", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.ContextVarsContext)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object construct(@SuppressWarnings("unused") Object cls,
                        @Cached PythonObjectFactory factory) {
            return factory.createContextVarsContext();
        }
    }

    @Builtin(name = "Token", minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.ContextVarsToken)
    @GenerateNodeFactory
    public abstract static class TokenNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object construct(@SuppressWarnings("unused") Object cls,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.TOKEN_ONLY_BY_CONTEXTVAR);
        }
    }
}
