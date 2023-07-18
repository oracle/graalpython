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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.FIRST_ARG_MUST_BE_CALLABLE_S;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___FUNC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PInstancemethod})
public final class InstancemethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return InstancemethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "checkCallableNode.execute(callable)", limit = "1")
        protected static PNone init(PDecoratedMethod self, Object callable,
                        @Shared("checkCallable") @SuppressWarnings("unused") @Cached PyCallableCheckNode checkCallableNode) {
            self.setCallable(callable);
            return PNone.NONE;
        }

        @Specialization(guards = "!checkCallableNode.execute(callable)", limit = "1")
        protected PNone noCallble(@SuppressWarnings("unused") PDecoratedMethod self, Object callable,
                        @Shared("checkCallable") @SuppressWarnings("unused") @Cached PyCallableCheckNode checkCallableNode) {
            throw raise(TypeError, FIRST_ARG_MUST_BE_CALLABLE_S, callable);
        }
    }

    @Builtin(name = J___FUNC__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected static Object func(PDecoratedMethod self) {
            return self.getCallable();
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetattributeNode extends PythonBuiltinNode {
        @Specialization
        protected static Object doIt(VirtualFrame frame, PDecoratedMethod self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                e.expectAttributeError(inliningTarget, errorProfile);
                return getAttrNode.execute(frame, self.getCallable(), key);
            }
        }
    }

    @Builtin(name = J___DOC__, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DocNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doc(VirtualFrame frame, PDecoratedMethod self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getCallable(), T___DOC__);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {
        @Specialization
        protected static Object doIt(VirtualFrame frame, PDecoratedMethod self, Object[] arguments, PKeyword[] keywords,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode) {
            return callNode.execute(frame, self.getCallable(), arguments, keywords);
        }

        @Override
        public Object varArgExecute(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            Object[] argsWithoutSelf = new Object[arguments.length - 1];
            PythonUtils.arraycopy(arguments, 1, argsWithoutSelf, 0, argsWithoutSelf.length);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }
    }

    @Builtin(name = J___GET__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object doGeneric(PDecoratedMethod self, Object obj, @SuppressWarnings("unused") Object cls) {
            if (obj == null || obj == PNone.NONE) {
                return self.getCallable();
            }
            return factory().createMethod(obj, self.getCallable());
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString reprBuiltinFunction(VirtualFrame frame, PDecoratedMethod self,
                        @Cached PyObjectGetAttr getNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<instancemethod %s at 0x%s>", toStr(getNameNode.execute(frame, self.getCallable(), T___NAME__)),
                            PythonAbstractObject.systemHashCodeAsHexString(self.getCallable()));
        }

        @TruffleBoundary
        private static String toStr(Object o) {
            // TODO GR-37980
            return o.toString();
        }
    }
}
