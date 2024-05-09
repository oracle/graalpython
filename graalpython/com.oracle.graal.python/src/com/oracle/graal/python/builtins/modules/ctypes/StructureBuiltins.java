/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.Structure;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.Union;
import static com.oracle.graal.python.builtins.modules.ctypes.StructUnionTypeBuiltins.T__FIELDS_;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL;
import static com.oracle.graal.python.nodes.ErrorMessages.DUPLICATE_VALUES_FOR_FIELD_S;
import static com.oracle.graal.python.nodes.ErrorMessages.TOO_MANY_INITIALIZERS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltinsFactory.PyTypeStgDictNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.Structure, PythonBuiltinClassType.Union})
public final class StructureBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructureBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> {
            PCallCapiFunction.callUncached(FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL, PythonToNativeNode.executeUncached(Structure));
            PCallCapiFunction.callUncached(FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL, PythonToNativeNode.executeUncached(Union));
        });
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static Object GenericPyCDataNew(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CtypesNodes.GenericPyCDataNewNode pyCDataNewNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);
            return pyCDataNewNode.execute(inliningTarget, type, dict);
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class InitNode extends PythonBuiltinNode {
        private static final int RECURSION_LIMIT = 5;

        @Specialization
        static Object Struct_init(VirtualFrame frame, CDataObject self, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") com.oracle.graal.python.runtime.IndirectCallData indirectCallData,
                        @Cached PyObjectSetAttr setAttr,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached CastToTruffleStringNode toString,
                        @Cached HashingStorageGetItem getItem,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (args.length > 0) {
                int res = _init_pos_args(frame, inliningTarget, self, getClassNode.execute(inliningTarget, self), args, kwds, 0,
                                indirectCallData, setAttr, getItemNode, toString, getItem, pyTypeStgDictNode, getBaseClassNode, equalNode, raiseNode, RECURSION_LIMIT);
                if (res < args.length) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, TOO_MANY_INITIALIZERS);
                }
            }

            if (kwds.length > 0) {
                for (PKeyword kw : kwds) {
                    setAttr.execute(frame, inliningTarget, self, kw.getName(), kw.getValue());
                }
            }
            return PNone.NONE;
        }

        /*****************************************************************/
        /*
         * Struct_Type
         */
        /*
         * This function is called to initialize a Structure or Union with positional arguments. It
         * calls itself recursively for all Structure or Union base classes, then retrieves the
         * _fields_ member to associate the argument position with the correct field name.
         *
         * Returns -1 on error, or the index of next argument on success.
         */
        static int _init_pos_args(VirtualFrame frame, Node inliningTarget, Object self, Object type, Object[] args, PKeyword[] kwds, int idx,
                        IndirectCallData indirectCallData,
                        PyObjectSetAttr setAttr,
                        PyObjectGetItem getItemNode,
                        CastToTruffleStringNode toString,
                        HashingStorageGetItem getItem,
                        PyTypeStgDictNode pyTypeStgDictNode,
                        GetBaseClassNode getBaseClassNode,
                        EqualNode equalNode,
                        PRaiseNode.Lazy raiseNode,
                        int recursionLimit) {
            Object fields;
            int index = idx;

            Object base = getBaseClassNode.execute(inliningTarget, type);
            if (pyTypeStgDictNode.execute(inliningTarget, base) != null) {
                if (recursionLimit > 0) {
                    index = _init_pos_args(frame, inliningTarget, self, base, args, kwds, index,
                                    indirectCallData, setAttr, getItemNode, toString, getItem, pyTypeStgDictNode, getBaseClassNode, equalNode,
                                    raiseNode, recursionLimit - 1);
                } else {
                    Object savedState = IndirectCallContext.enter(frame, indirectCallData);
                    try {
                        index = _init_pos_args_boundary(self, base, args, kwds, index, indirectCallData, setAttr, getItemNode);
                    } finally {
                        IndirectCallContext.exit(frame, indirectCallData, savedState);
                    }
                }
            }

            StgDictObject dict = pyTypeStgDictNode.execute(inliningTarget, type);
            fields = getItem.execute(inliningTarget, dict.getDictStorage(), T__FIELDS_);
            if (fields == null) {
                return index;
            }

            for (int i = 0; i < dict.length && (i + index) < args.length; ++i) {
                Object pair = getItemNode.execute(frame, inliningTarget, fields, i);
                TruffleString name = toString.execute(inliningTarget, getItemNode.execute(frame, inliningTarget, pair, 0));
                Object val = args[i + index];
                if (kwds.length > 0) {
                    if (KeywordsStorage.findStringKey(kwds, name, equalNode) != -1) {
                        // using execute() instead of raise() because we need to pass raisingNode
                        // explicitly (raiseNode might be uncached)
                        throw raiseNode.get(inliningTarget).execute(inliningTarget, TypeError, null, PNone.NO_VALUE, DUPLICATE_VALUES_FOR_FIELD_S, new Object[]{name});
                    }
                }

                setAttr.execute(frame, inliningTarget, self, name, val);
            }
            return index + dict.length;
        }

        @TruffleBoundary
        static int _init_pos_args_boundary(Object self, Object type, Object[] args, PKeyword[] kwds, int idx,
                        IndirectCallData indirectCallData,
                        PyObjectSetAttr setAttr,
                        PyObjectGetItem getItemNode) {
            return _init_pos_args(null, null, self, type, args, kwds, idx, indirectCallData, setAttr,
                            getItemNode, CastToTruffleStringNode.getUncached(),
                            HashingStorageGetItemNodeGen.getUncached(), PyTypeStgDictNodeGen.getUncached(),
                            GetBaseClassNode.getUncached(), TruffleString.EqualNode.getUncached(), PRaiseNode.Lazy.getUncached(), 0);
        }
    }

}
