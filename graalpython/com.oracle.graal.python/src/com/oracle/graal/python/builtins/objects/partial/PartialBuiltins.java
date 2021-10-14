/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.partial;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_PARTIAL_STATE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectReprAsJavaStringNode;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PPartial)
public class PartialBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PartialBuiltinsFactory.getFactories();
    }

    @Builtin(name = "func", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, doc = "function object to use in future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialFuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGet(PPartial self) {
            return self.getFn();
        }
    }

    @Builtin(name = "args", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, doc = "tuple of arguments to future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGet(PPartial self) {
            return self.getArgsTuple(factory());
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class PartialDictNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected Object getDict(PPartial self, @SuppressWarnings("unused") PNone mapping,
                                 @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(self);
        }

        @Specialization
        protected Object setDict(PPartial self, PDict mapping,
                                 @Cached SetDictNode setDict) {
            setDict.execute(self, mapping);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        protected Object setDict(@SuppressWarnings("unused") PPartial self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }
    }


    @Builtin(name = "keywords", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, doc = "dictionary of keyword arguments to future partial calls")
    @GenerateNodeFactory
    public abstract static class PartialKeywordsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGet(PPartial self) {
            return self.getKwDict(factory());
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PartialReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PPartial self,
                        @Cached GetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictIfExistsNode) {
            final PDict dict = getDictIfExistsNode.execute(self);
            return factory().createTuple(new Object[]{
                            getClassNode.execute(self),
                            factory().createTuple(new Object[]{self.getFn()}),
                            factory().createTuple(new Object[]{self.getFn(), self.getArgsTuple(factory()), self.getKwDict(factory()), (dict != null) ? dict : PNone.NONE})});
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PartialSetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object setState(VirtualFrame frame, PPartial self, PTuple state,
                        @Cached ExpandKeywordStarargsNode starargsNode,
                        @Cached SetDictNode setDictNode,
                        @Cached SequenceNodes.GetSequenceStorageNode storageNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode arrayNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode) {
            final Object function = getItemNode.execute(frame, state, 0);
            final Object fnArgs = getItemNode.execute(frame, state, 1);
            final Object fnKwargs = getItemNode.execute(frame, state, 2);
            final Object dict = getItemNode.execute(frame, state, 3);

            if (!callableCheckNode.execute(function) ||
                            !PGuards.isPTuple(fnArgs) ||
                            (fnKwargs != PNone.NONE && !PGuards.isDict(fnKwargs))) {
                throw raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
            }

            self.setFn(function);

            assert fnArgs instanceof PTuple;
            self.setArgs((PTuple) fnArgs, storageNode, arrayNode);

            assert fnKwargs instanceof PDict;
            self.setKw((PDict) fnKwargs, starargsNode);

            if (dict != PNone.NONE) {
                assert dict instanceof PDict;
                setDictNode.execute(self, (PDict) dict);
            }

            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object self, Object state) {
            throw raise(PythonBuiltinClassType.TypeError, INVALID_PARTIAL_STATE);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PartialCallNode extends PythonVarargsBuiltinNode {
        @Specialization
        Object call(VirtualFrame frame, PPartial self, Object[] args, PKeyword[] keywords,
                        @Cached ConditionProfile hasArgsProfile,
                        @Cached ConditionProfile hasKeywordsProfile,
                        @Cached CallVarargsMethodNode callNode) {
            final Object[] pArgs = self.getArgs();
            Object[] callArgs;
            if (hasArgsProfile.profile(args.length > 0)) {
                callArgs = new Object[pArgs.length + args.length];
                PythonUtils.arraycopy(pArgs, 0, callArgs, 0, pArgs.length);
                PythonUtils.arraycopy(args, 0, callArgs, pArgs.length, args.length);
            } else {
                callArgs = pArgs;
            }

            final PKeyword[] pKeywords = self.getKw();
            PKeyword[] callKeywords;
            if (hasKeywordsProfile.profile(keywords.length > 0)) {
                callKeywords = new PKeyword[pKeywords.length + keywords.length];
                PythonUtils.arraycopy(pKeywords, 0, callKeywords, 0, pKeywords.length);
                PythonUtils.arraycopy(keywords, 0, callKeywords, pKeywords.length, keywords.length);
            } else {
                callKeywords = pKeywords;
            }

            return callNode.execute(frame, self.getFn(), callArgs, callKeywords);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PartialReprNode extends PythonUnaryBuiltinNode {
        private static void reprArgs(VirtualFrame frame, PPartial partial, StringBuilder sb, PyObjectReprAsJavaStringNode reprNode) {
            for (Object arg : partial.getArgs()) {
                PythonUtils.append(sb, ", ");
                PythonUtils.append(sb, reprNode.execute(frame, arg));
            }
        }

        private static void reprKwArgs(VirtualFrame frame, PPartial partial, StringBuilder sb, PyObjectReprAsJavaStringNode reprNode, PyObjectStrAsJavaStringNode strNode) {
            for (PKeyword pKeyword : partial.getKw()) {
                PythonUtils.append(sb, ", ");
                PythonUtils.append(sb, strNode.execute(frame, pKeyword.getName()));
                PythonUtils.append(sb, "=");
                PythonUtils.append(sb, reprNode.execute(frame, pKeyword.getValue()));
            }
        }

        @Specialization
        public static Object repr(VirtualFrame frame, PPartial partial,
                        @Cached PyObjectStrAsJavaStringNode strNode,
                        @Cached PyObjectReprAsJavaStringNode reprNode,
                        @Cached ObjectNodes.GetFullyQualifiedClassNameNode classNameNode) {
            final String name = classNameNode.execute(frame, partial);
            StringBuilder sb = PythonUtils.newStringBuilder(name);
            PythonUtils.append(sb, "(");
            PythonContext ctxt = PythonContext.get(classNameNode);
            if (!ctxt.reprEnter(partial)) {
                return "...";
            }
            try {
                PythonUtils.append(sb, reprNode.execute(frame, partial.getFn()));
                reprArgs(frame, partial, sb, reprNode);
                reprKwArgs(frame, partial, sb, reprNode, strNode);
                PythonUtils.append(sb, ")");
                return PythonUtils.sbToString(sb);
            } finally {
                ctxt.reprLeave(partial);
            }
        }
    }
}
