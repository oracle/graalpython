/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PClassmethod, PythonBuiltinClassType.PBuiltinClassMethod})
public final class ClassmethodCommonBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ClassmethodCommonBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ClassmethodCommonBuiltinsFactory.getFactories();
    }

    @Slot(SlotKind.tp_descr_get)
    @ReportPolymorphism
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class GetNode extends DescrGetBuiltinNode {
        /*-
        TODO: (GR-53082) this is not handling following code-path added to CPython at some later point:
        if (Py_TYPE(cm->cm_callable)->tp_descr_get != NULL) {
            return Py_TYPE(cm->cm_callable)->tp_descr_get(cm->cm_callable, type, type);
        }
        
        Additionally, in CPython tp_descrget is not shared between classmethod_descriptor and classmethod,
        we should investigate if we can really share the implementation
        */

        // If self.getCallable() is null, let the next @Specialization handle that
        @Specialization(guards = {"isSingleContext()", "isNoValue(type)", "cachedSelf == self", "cachedCallable != null"}, limit = "3")
        static Object getCached(@SuppressWarnings("unused") PDecoratedMethod self, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached(value = "self", weak = true) PDecoratedMethod cachedSelf,
                        @SuppressWarnings("unused") @Cached(value = "self.getCallable()", weak = true) Object cachedCallable,
                        @Shared @Cached GetClassNode getClass,
                        @Shared @Cached MakeMethodNode makeMethod) {
            return makeMethod.execute(inliningTarget, getClass.execute(inliningTarget, obj), cachedCallable);
        }

        @Specialization(guards = "isNoValue(type)", replaces = "getCached")
        static Object get(PDecoratedMethod self, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetClassNode getClass,
                        @Shared @Cached MakeMethodNode makeMethod,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doGet(inliningTarget, self, getClass.execute(inliningTarget, obj), makeMethod, raiseNode);
        }

        // If self.getCallable() is null, let the next @Specialization handle that
        @Specialization(guards = {"isSingleContext()", "!isNoValue(type)", "cachedSelf == self", "cachedCallable != null"}, limit = "3")
        static Object getTypeCached(@SuppressWarnings("unused") PDecoratedMethod self, @SuppressWarnings("unused") Object obj, Object type,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached(value = "self", weak = true) PDecoratedMethod cachedSelf,
                        @SuppressWarnings("unused") @Cached(value = "self.getCallable()", weak = true) Object cachedCallable,
                        @Shared @Cached MakeMethodNode makeMethod) {
            return makeMethod.execute(inliningTarget, type, cachedCallable);
        }

        @Specialization(guards = "!isNoValue(type)", replaces = "getTypeCached")
        static Object getType(PDecoratedMethod self, @SuppressWarnings("unused") Object obj, Object type,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached MakeMethodNode makeMethod,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doGet(inliningTarget, self, type, makeMethod, raiseNode);
        }

        private static Object doGet(Node inliningTarget, PDecoratedMethod self, Object type, MakeMethodNode makeMethod, PRaiseNode raiseNode) {
            Object callable = self.getCallable();
            if (callable == null) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.RuntimeError, ErrorMessages.UNINITIALIZED_S_OBJECT);
            }
            return makeMethod.execute(inliningTarget, type, callable);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    @ImportStatic(PGuards.class)
    @ReportPolymorphism
    abstract static class MakeMethodNode extends PNodeWithContext {
        abstract Object execute(Node inliningTarget, Object self, Object func);

        @Specialization
        Object method(Object self, PFunction func,
                        @Bind PythonLanguage language) {
            return PFactory.createMethod(language, self, func);
        }

        @Specialization(guards = "!func.needsDeclaringType()")
        Object methodBuiltin(Object self, PBuiltinFunction func,
                        @Bind PythonLanguage language) {
            return PFactory.createBuiltinMethod(language, self, func);
        }

        @Specialization(guards = "func.needsDeclaringType()")
        Object methodBuiltinWithDeclaringType(Object self, PBuiltinFunction func,
                        @Bind PythonLanguage language) {
            return PFactory.createBuiltinMethod(language, self, func, func.getEnclosingType());
        }

        @Specialization(guards = "!isFunction(func)")
        Object generic(Object self, Object func,
                        @Bind PythonLanguage language) {
            return PFactory.createMethod(language, self, func);
        }
    }

    @Slot(value = SlotKind.tp_call, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child private com.oracle.graal.python.nodes.call.CallNode callNode = com.oracle.graal.python.nodes.call.CallNode.create();

        @Specialization
        protected Object doIt(VirtualFrame frame, PDecoratedMethod self, Object[] arguments, PKeyword[] keywords) {
            return callNode.execute(frame, self.getCallable(), arguments, keywords);
        }

    }
}
