/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.contextvars;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.LookupError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextVar)
public final class ContextVarBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextVarBuiltinsFactory.getFactories();
    }

    @Builtin(name = "get", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object get(PContextVar self, Object def,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile defIsNoValueProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object defValue = defIsNoValueProfile.profile(inliningTarget, isNoValue(def)) ? PContextVar.NO_DEFAULT : def;
            PythonContext.PythonThreadState threadState = PythonContext.get(inliningTarget).getThreadState(PythonLanguage.get(inliningTarget));
            Object value = self.get(threadState, defValue);
            if (value != null) {
                return value;
            }
            throw raiseNode.get(inliningTarget).raise(LookupError);
        }
    }

    @Builtin(name = "set", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object set(PContextVar self, Object value,
                        @Cached PythonObjectFactory factory) {
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            Object oldValue = self.getValue(threadState);
            self.setValue(threadState, value);
            return factory.createContextVarsToken(self, oldValue);
        }
    }

    @Builtin(name = "reset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ResetNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object reset(PContextVar self, PContextVarsToken token,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raise) {
            if (self == token.getVar()) {
                token.use(inliningTarget, raise);
                PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
                if (token.getOldValue() == null) {
                    PContextVarsContext context = threadState.getContextVarsContext();
                    context.contextVarValues = context.contextVarValues.without(self, self.getHash());
                } else {
                    self.setValue(threadState, token.getOldValue());
                }
            } else {
                throw raise.get(inliningTarget).raise(ValueError, ErrorMessages.TOKEN_FOR_DIFFERENT_CONTEXTVAR, token);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isToken(token)")
        Object doError(@SuppressWarnings("unused") PContextVar self, Object token,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raise) {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.INSTANCE_OF_TOKEN_EXPECTED, token);
        }

        static boolean isToken(Object obj) {
            return obj instanceof PContextVarsToken;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
