/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J__TYPING;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TYPING;
import static com.oracle.graal.python.nodes.ErrorMessages.BOUND_MUST_BE_A_TYPE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_GET;
import static com.oracle.graal.python.util.PythonUtils.arraycopy;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.frame.FrameBuiltins.GetGlobalsNode;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.typing.PTypeVarTuple;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__TYPING)
public class TypingModuleBuiltins extends PythonBuiltins {

    public static final TruffleString T_GENERIC_ALIAS = tsLiteral("_GenericAlias");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TypingModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("Generic", PythonBuiltinClassType.PGeneric);
    }

    @Builtin(name = "_idfunc", minNumOfPositionalArgs = 1, parameterNames = {"x"})
    @GenerateNodeFactory
    abstract static class IdFuncNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(Object x) {
            return x;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CheckBoundNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object bound);

        @Specialization
        static Object none(@SuppressWarnings("unused") PNone bound) {
            return PNone.NONE;
        }

        @Fallback
        static Object check(VirtualFrame frame, Node inliningTarget, Object bound,
                        @Cached TypeCheckNode typeCheckNode) {
            return typeCheckNode.execute(frame, inliningTarget, bound, BOUND_MUST_BE_A_TYPE);
        }
    }

    /**
     * Equivalent of {@code type_check} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    abstract static class TypeCheckNode extends Node {
        private static final TruffleString T__TYPE_CHECK = tsLiteral("_type_check");

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object arg, TruffleString msg);

        @Specialization
        static Object none(@SuppressWarnings("unused") PNone arg, @SuppressWarnings("unused") TruffleString msg) {
            // Calling typing.py here leads to bootstrapping problems
            return PNone.NONE;
        }

        @Fallback
        static Object check(VirtualFrame frame, Node inliningTarget, Object arg, TruffleString msg,
                        @Cached CallTypingFuncObjectNode callTypingFuncObjectNode) {
            return callTypingFuncObjectNode.execute(frame, inliningTarget, T__TYPE_CHECK, arg, msg);
        }
    }

    /**
     * Equivalent of {@code call_typing_func_object} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CallTypingFuncObjectNode extends Node {
        abstract Object executeInternal(VirtualFrame frame, Node inliningTarget, TruffleString name, Object[] args);

        public final Object execute(VirtualFrame frame, Node inliningTarget, TruffleString name, Object... args) {
            return executeInternal(frame, inliningTarget, name, args);
        }

        @Specialization
        static Object doCall(VirtualFrame frame, Node inliningTarget, TruffleString name, Object[] args,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached(inline = false) CallNode callNode) {
            PythonModule typing = AbstractImportNode.importModule(T_TYPING);
            Object func = getAttrNode.execute(frame, inliningTarget, typing, name);
            return callNode.execute(frame, func, args);
        }
    }

    /**
     * Equivalent of {@code call_typing_args_kwargs} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CallTypingArgsKwargsNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, TruffleString name, Object cls, Object[] args, PKeyword[] keywords);

        @Specialization
        static Object doCall(VirtualFrame frame, Node inliningTarget, TruffleString name, Object cls, Object[] args, PKeyword[] keywords,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached(inline = false) CallNode callNode) {
            PythonModule typing = AbstractImportNode.importModule(T_TYPING);
            Object func = getAttrNode.execute(frame, inliningTarget, typing, name);
            Object[] args2 = new Object[args.length + 1];
            args2[0] = cls;
            arraycopy(args, 0, args2, 1, args.length);
            return callNode.execute(frame, func, args2, keywords);
        }
    }

    /**
     * Equivalent of {@code caller} in {@code typevarobject.c}
     */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CallerNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget);

        @Specialization
        static Object caller(VirtualFrame frame,
                        @Cached(inline = false) GetGlobalsNode getGlobalsNode,
                        @Cached(inline = false) PyObjectCallMethodObjArgs callMethod,
                        @Cached(inline = false) ReadCallerFrameNode readCallerNode) {
            Reference currentFrameInfo = PArguments.getCurrentFrameInfo(frame);
            PFrame pFrame = readCallerNode.executeWith(currentFrameInfo, 0);
            Object globals = getGlobalsNode.execute(frame, pFrame);

            return callMethod.executeCached(frame, globals, T_GET, T___NAME__, PNone.NONE);
        }

    }

    /**
     * Equivalent of {@code typevartuple_unpack}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class UnpackNode extends Node {
        private static final TruffleString T_UNPACK = tsLiteral("Unpack");

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object tvt);

        @Specialization
        static Object doUnpack(VirtualFrame frame, Node inliningTarget, Object tvt,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached PyObjectGetItem getItemNode) {
            PythonModule typing = AbstractImportNode.importModule(T_TYPING);
            Object unpack = getAttrNode.execute(frame, inliningTarget, typing, T_UNPACK);
            return getItemNode.execute(frame, inliningTarget, unpack, tvt);
        }
    }

    /**
     * Equivalent of {@code unpack_typevartuples}.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class UnpackTypeVarTuplesNode extends Node {
        public abstract PTuple execute(VirtualFrame frame, Node inliningTarget, PTuple params);

        @Specialization
        static PTuple doUnpack(VirtualFrame frame, Node inliningTarget, PTuple params,
                        @Bind PythonLanguage language,
                        @Cached ToArrayNode toArrayNode,
                        @Cached UnpackNode unpackNode) {
            Object[] elements = toArrayNode.execute(inliningTarget, params.getSequenceStorage());
            boolean found = false;
            for (Object element : elements) {
                if (element instanceof PTypeVarTuple) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return params;
            }
            Object[] unpacked = new Object[elements.length];
            for (int i = 0; i < unpacked.length; ++i) {
                if (elements[i] instanceof PTypeVarTuple typeVarTuple) {
                    unpacked[i] = unpackNode.execute(frame, inliningTarget, typeVarTuple);
                } else {
                    unpacked[i] = elements[i];
                }
            }
            return PFactory.createTuple(language, unpacked);
        }
    }
}
