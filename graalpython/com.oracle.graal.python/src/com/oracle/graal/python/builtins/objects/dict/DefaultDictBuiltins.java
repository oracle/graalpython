/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.FIRST_ARG_MUST_BE_CALLABLE_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MISSING__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDefaultDict)
public final class DefaultDictBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DefaultDictBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reprFunction(VirtualFrame frame, PDefaultDict self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached DictReprBuiltin.ReprNode dictReprNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            final Object klass = getClassNode.execute(inliningTarget, self);
            final TruffleString name = getNameNode.execute(inliningTarget, klass);
            final TruffleString factoryRepr = reprNode.execute(frame, inliningTarget, self.getDefaultFactory());
            final TruffleString dictRepr = dictReprNode.execute(frame, self);
            return simpleTruffleStringFormatNode.format("%s(%s, %s)", name, factoryRepr, dictRepr);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(VirtualFrame frame, PDefaultDict self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PythonObjectFactory factory) {
            final Object defaultFactory = self.getDefaultFactory();
            PTuple args = (defaultFactory == PNone.NONE) ? factory.createEmptyTuple() : factory.createTuple(new Object[]{defaultFactory});
            Object iter = getIter.execute(frame, inliningTarget, factory.createDictItemsView(self));
            return factory.createTuple(new Object[]{getClassNode.execute(inliningTarget, self), args, PNone.NONE, PNone.NONE, iter});
        }
    }

    // copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PDefaultDict copy(@SuppressWarnings("unused") VirtualFrame frame, PDefaultDict self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageCopy copyNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createDefaultDict(self.getDefaultFactory(), copyNode.execute(inliningTarget, self.getDictStorage()));
        }
    }

    @Builtin(name = J___MISSING__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MissingNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNone(self.getDefaultFactory())")
        static Object doNoFactory(@SuppressWarnings("unused") PDefaultDict self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.KeyError, new Object[]{key});
        }

        @Specialization(guards = "!isNone(self.getDefaultFactory())")
        static Object doMissing(VirtualFrame frame, PDefaultDict self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached PyDictSetItem setItem) {
            final Object value = callNode.execute(frame, self.getDefaultFactory());
            setItem.execute(frame, inliningTarget, self, key, value);
            return value;
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        Object doInit(VirtualFrame frame, PDefaultDict self, Object[] args, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached DictBuiltins.InitNode dictInitNode,
                        @Cached PyCallableCheckNode callableCheckNode) {
            Object[] newArgs = args;
            Object newDefault = PNone.NONE;
            if (newArgs.length > 0) {
                newDefault = newArgs[0];
                if (newDefault != PNone.NONE && !callableCheckNode.execute(inliningTarget, newDefault)) {
                    throw raise(TypeError, FIRST_ARG_MUST_BE_CALLABLE_S, " or None");
                }
                newArgs = PythonUtils.arrayCopyOfRange(args, 1, args.length);
            }
            self.setDefaultFactory(newDefault);
            return dictInitNode.execute(frame, self, newArgs, kwargs);
        }
    }

    @Builtin(name = "default_factory", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "Factory for default value called by __missing__().")
    @GenerateNodeFactory
    abstract static class DefaultFactoryNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        @SuppressWarnings("unused")
        Object set(PDefaultDict self, Object value) {
            self.setDefaultFactory(value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        @SuppressWarnings("unused")
        Object get(PDefaultDict self, @SuppressWarnings("unused") PNone value) {
            return self.getDefaultFactory();
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object or(VirtualFrame frame, PDict self, PDict other,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached CallNode callNode,
                        @Cached DictNodes.UpdateNode updateNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PDefaultDict dd = (PDefaultDict) (self instanceof PDefaultDict ? self : other);
            Object type = getClassNode.execute(inliningTarget, dd);
            Object result = callNode.execute(frame, type, dd.getDefaultFactory(), self);
            if (result instanceof PDefaultDict) {
                updateNode.execute(frame, (PDefaultDict) result, other);
                return result;
            } else {
                /* Cpython doesn't check for this and ends up with SystemError */
                throw raiseNode.get(inliningTarget).raise(TypeError);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object or(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
