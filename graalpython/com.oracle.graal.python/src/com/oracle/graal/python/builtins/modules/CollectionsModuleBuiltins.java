/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J_DEFAULTDICT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DEQUE_REV_ITER;
import static com.oracle.graal.python.nodes.BuiltinNames.J_TUPLE_GETTER;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.deque.DequeIterBuiltins.DequeIterNextNode;
import com.oracle.graal.python.builtins.objects.deque.PDeque;
import com.oracle.graal.python.builtins.objects.deque.PDequeIter;
import com.oracle.graal.python.builtins.objects.dict.PDefaultDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(defineModule = "_collections")
public final class CollectionsModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CollectionsModuleBuiltinsFactory.getFactories();
    }

    // _collections.deque
    @Builtin(name = J_DEQUE, minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PDeque, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class DequeNode extends PythonVarargsBuiltinNode {

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length >= 1) {
                return doGeneric(arguments[0], null, null);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
        }

        @Specialization
        @SuppressWarnings("unused")
        PDeque doGeneric(Object cls, Object[] args, PKeyword[] kwargs) {
            return factory().createDeque(cls);
        }
    }

    // _collections._deque_iterator
    @Builtin(name = J_DEQUE_ITER, constructsClass = PythonBuiltinClassType.PDequeIter, //
                    minNumOfPositionalArgs = 2, parameterNames = {"$self", "iterable", "index"})
    @GenerateNodeFactory
    abstract static class DequeIterNode extends PythonTernaryBuiltinNode {

        @Specialization
        static PDequeIter doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object deque, Object indexObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile dequeProfile,
                        @Cached InlinedConditionProfile indexNoneProfile,
                        @Cached PyNumberIndexNode toIndexNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached DequeIterNextNode getNextNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!dequeProfile.profile(inliningTarget, deque instanceof PDeque)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_OBJ_TYPE_S_GOT_P, BuiltinNames.T_DEQUE, deque);
            }
            PDequeIter dequeIter = factory.createDequeIter((PDeque) deque);
            if (indexNoneProfile.profile(inliningTarget, indexObj != PNone.NO_VALUE)) {
                int index = castToJavaIntExactNode.execute(inliningTarget, toIndexNode.execute(frame, inliningTarget, indexObj));
                for (int i = 0; i < index; i++) {
                    getNextNode.execute(dequeIter);
                }
            }
            return dequeIter;
        }
    }

    // _collections._deque_reverse_iterator
    @Builtin(name = J_DEQUE_REV_ITER, constructsClass = PythonBuiltinClassType.PDequeRevIter, //
                    minNumOfPositionalArgs = 2, parameterNames = {"$self", "iterable", "index"})
    @GenerateNodeFactory
    abstract static class DequeRevIterNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PDequeIter doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object deque, Object indexObj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile dequeProfile,
                        @Cached InlinedConditionProfile indexNoneProfile,
                        @Cached PyNumberIndexNode toIndexNode,
                        @Cached CastToJavaIntExactNode castToJavaIntExactNode,
                        @Cached DequeIterNextNode getNextNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!dequeProfile.profile(inliningTarget, deque instanceof PDeque)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.EXPECTED_OBJ_TYPE_S_GOT_P, BuiltinNames.T_DEQUE, deque);
            }
            PDequeIter dequeIter = factory.createDequeRevIter((PDeque) deque);
            if (indexNoneProfile.profile(inliningTarget, indexObj != PNone.NO_VALUE)) {
                int index = castToJavaIntExactNode.execute(inliningTarget, toIndexNode.execute(frame, inliningTarget, indexObj));
                for (int i = 0; i < index; i++) {
                    getNextNode.execute(dequeIter);
                }
            }
            return dequeIter;
        }
    }

    // _collections.defaultdict
    @Builtin(name = J_DEFAULTDICT, minNumOfPositionalArgs = 1, constructsClass = PythonBuiltinClassType.PDefaultDict, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class DefaultDictNode extends PythonVarargsBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        PDefaultDict doGeneric(Object cls, Object[] args, PKeyword[] kwargs,
                        @Cached PythonObjectFactory factory) {
            return factory.createDefaultDict(cls);
        }
    }

    // _collections._tuplegetter
    @Builtin(name = J_TUPLE_GETTER, parameterNames = {"cls", "index", "doc"}, constructsClass = PythonBuiltinClassType.PTupleGetter)
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class TupleGetterNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CollectionsModuleBuiltinsClinicProviders.TupleGetterNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object construct(Object cls, int index, Object doc,
                        @Cached PythonObjectFactory factory) {
            return factory.createTupleGetter(cls, index, doc);
        }
    }
}
