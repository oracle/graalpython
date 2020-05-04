/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.ClassmethodBuiltinsFactory.MakeMethodNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.WeakASTReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PClassmethod})
public class ClassmethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ClassmethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class GetNode extends PythonBuiltinNode {
        @Child MakeMethodNode makeMethod = MakeMethodNode.create();

        /**
         * N.b.: cachedCallable.notNull is sufficient here, because
         * {@link PDecoratedMethod#setCallable} can only be called when the callable was previously
         * {@code null}. So if it ever was not null and we cached that, it is being held alive by
         * the {@code self} argument now and there cannot be a race.
         */
        @Specialization(guards = {"isNoValue(type)", "cachedSelf.is(self)", "cachedCallable.notNull()"}, assumptions = "singleContextAssumption()")
        Object getCached(@SuppressWarnings("unused") PDecoratedMethod self, Object obj, @SuppressWarnings("unused") Object type,
                        @SuppressWarnings("unused") @Cached("weak(self)") WeakASTReference cachedSelf,
                        @SuppressWarnings("unused") @Cached("weak(self.getCallable())") WeakASTReference cachedCallable,
                        @Cached GetClassNode getClass) {
            return makeMethod.execute(getClass.execute(obj), cachedCallable.get());
        }

        @Specialization(guards = "isNoValue(type)", replaces = "getCached")
        Object get(PDecoratedMethod self, Object obj, @SuppressWarnings("unused") Object type,
                        @Cached GetClassNode getClass,
                        @Cached BranchProfile uninitialized) {
            return doGet(self, getClass.execute(obj), uninitialized);
        }

        /**
         * @see #getCached
         */
        @Specialization(guards = {"!isNoValue(type)", "cachedSelf.is(self)", "cachedCallable.notNull()"}, assumptions = "singleContextAssumption()")
        Object getTypeCached(@SuppressWarnings("unused") PDecoratedMethod self, @SuppressWarnings("unused") Object obj, Object type,
                        @SuppressWarnings("unused") @Cached("weak(self)") WeakASTReference cachedSelf,
                        @SuppressWarnings("unused") @Cached("weak(self.getCallable())") WeakASTReference cachedCallable) {
            return makeMethod.execute(type, cachedCallable.get());
        }

        @Specialization(guards = "!isNoValue(type)", replaces = "getTypeCached")
        Object getType(PDecoratedMethod self, @SuppressWarnings("unused") Object obj, Object type,
                        @Cached("create()") BranchProfile uninitialized) {
            return doGet(self, type, uninitialized);
        }

        private Object doGet(PDecoratedMethod self, Object type, BranchProfile uninitialized) {
            Object callable = self.getCallable();
            if (callable == null) {
                uninitialized.enter();
                throw raise(PythonBuiltinClassType.RuntimeError, "uninitialized classmethod object");
            }
            return makeMethod.execute(type, callable);
        }
    }

    @ImportStatic(PGuards.class)
    @ReportPolymorphism
    abstract static class MakeMethodNode extends PNodeWithContext {
        abstract Object execute(Object self, Object func);

        @Specialization
        Object method(Object self, PFunction func,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createMethod(self, func);
        }

        @Specialization
        Object methodBuiltin(Object self, PBuiltinFunction func,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createBuiltinMethod(self, func);
        }

        @Specialization(guards = "!isFunction(func)")
        Object generic(Object self, Object func,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            return factory.createMethod(self, func);
        }

        static MakeMethodNode create() {
            return MakeMethodNodeGen.create();
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Child private com.oracle.graal.python.nodes.call.CallNode callNode = com.oracle.graal.python.nodes.call.CallNode.create();

        @Specialization
        protected Object doIt(VirtualFrame frame, PDecoratedMethod self, Object[] arguments, PKeyword[] keywords) {
            return callNode.execute(frame, self.getCallable(), arguments, keywords);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            Object[] argsWithoutSelf = new Object[arguments.length - 1];
            PythonUtils.arraycopy(arguments, 1, argsWithoutSelf, 0, argsWithoutSelf.length);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }
    }
}
