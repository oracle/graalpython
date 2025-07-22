/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.PGuards.isNoValue;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextVarsContext)
@HashNotImplemented
public final class ContextBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ContextBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "Context", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object construct(@SuppressWarnings("unused") Object cls,
                        @Bind PythonLanguage language) {
            return PFactory.createContextVarsContext(language);
        }
    }

    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        int len(@SuppressWarnings("unused") PContextVarsContext self) {
            return self.contextVarValues.size();
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetContextVar extends MpSubscriptBuiltinNode {
        @Specialization
        Object get(PContextVarsContext self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raise) {
            return getContextVar(inliningTarget, self, key, null, raise);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class Iter extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PContextVarsContext self,
                        @Bind PythonLanguage language) {
            return PFactory.createContextIterator(language, self, PContextIterator.ItemKind.KEYS);
        }
    }

    @Builtin(name = "keys", declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Keys extends PythonUnaryBuiltinNode {
        @Specialization
        static Object keys(PContextVarsContext self,
                        @Bind PythonLanguage language) {
            return PFactory.createContextIterator(language, self, PContextIterator.ItemKind.KEYS);
        }
    }

    @Builtin(name = "values", declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Values extends PythonUnaryBuiltinNode {
        @Specialization
        static Object values(PContextVarsContext self,
                        @Bind PythonLanguage language) {
            return PFactory.createContextIterator(language, self, PContextIterator.ItemKind.VALUES);
        }
    }

    @Builtin(name = "items", declaresExplicitSelf = true, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Items extends PythonUnaryBuiltinNode {
        @Specialization
        static Object items(PContextVarsContext self,
                        @Bind PythonLanguage language) {
            return PFactory.createContextIterator(language, self, PContextIterator.ItemKind.ITEMS);
        }
    }

    @Builtin(name = "run", takesVarArgs = true, takesVarKeywordArgs = true, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$callable"})
    @GenerateNodeFactory
    public abstract static class Run extends PythonBuiltinNode {
        @Specialization
        static Object get(VirtualFrame frame, PContextVarsContext self, Object fun, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached CallNode call,
                        @Cached PRaiseNode raise) {
            PythonContext.PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            self.enter(inliningTarget, threadState, raise);
            try {
                return call.execute(frame, fun, args, keywords);
            } finally {
                self.leave(threadState);
            }
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Copy extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doCopy(PContextVarsContext self,
                        @Bind PythonLanguage language) {
            PContextVarsContext ret = PFactory.createContextVarsContext(language);
            ret.contextVarValues = self.contextVarValues;
            return ret;
        }
    }

    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetMethod extends PythonBuiltinNode {

        @Specialization
        Object doGetDefault(PContextVarsContext self, Object key, Object def,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile noValueProfile,
                        @Cached PRaiseNode raise) {
            Object defVal = noValueProfile.profile(inliningTarget, isNoValue(def)) ? PNone.NONE : def;
            return getContextVar(inliningTarget, self, key, defVal, raise);
        }

    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    public abstract static class Contains extends SqContainsBuiltinNode {
        @Specialization
        boolean doIn(PContextVarsContext self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raise) {
            if (key instanceof PContextVar var) {
                return self.contextVarValues.lookup(var, var.getHash()) != null;
            }
            throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CONTEXTVAR_KEY_EXPECTED, key);
        }
    }

    private static Object getContextVar(Node inliningTarget, PContextVarsContext self, Object key, Object def, PRaiseNode raise) {
        if (key instanceof PContextVar ctxVar) {
            Object value = self.contextVarValues.lookup(key, ctxVar.getHash());
            if (value == null) {
                if (def == null) {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.KeyError, new Object[]{key});
                } else {
                    return def;
                }
            } else {
                return value;
            }
        } else {
            throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CONTEXTVAR_KEY_EXPECTED, key);
        }
    }
}
