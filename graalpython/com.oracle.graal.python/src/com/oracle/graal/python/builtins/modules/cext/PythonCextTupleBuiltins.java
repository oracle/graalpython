/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = PythonCextTupleBuiltins.PYTHON_CEXT_TUPLE)
@GenerateNodeFactory
public class PythonCextTupleBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT_TUPLE = "python_cext_tuple";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyTuple_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyTuple_New extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple doGeneric(VirtualFrame frame, Object size,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            return factory().createTuple(new Object[asSizeNode.executeExact(frame, size)]);
        }
    }

    @Builtin(name = "PyTuple_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyTuple_SetItem extends PythonTernaryBuiltinNode {
        @Specialization
        static int doManaged(VirtualFrame frame, PythonNativeWrapper selfWrapper, Object position, Object elementWrapper,
                        @Cached AsPythonObjectNode selfAsPythonObjectNode,
                        @Cached AsPythonObjectStealingNode elementAsPythonObjectNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object self = selfAsPythonObjectNode.execute(selfWrapper);
                if (!PGuards.isPTuple(self) || selfWrapper.getRefCount() != 1) {
                    throw raiseNode.raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_P, "PTuple_SetItem");
                }
                PTuple tuple = (PTuple) self;
                Object element = elementAsPythonObjectNode.execute(elementWrapper);
                setItemNode.execute(frame, tuple.getSequenceStorage(), position, element);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = "!isNativeWrapper(tuple)")
        static int doNative(Object tuple, long position, Object element,
                        @Cached PCallCapiFunction callSetItem) {
            // TODO(fa): This path should be avoided since this is called from native code to do a
            // native operation.
            callSetItem.call(NativeCAPISymbol.FUN_PY_TRUFFLE_TUPLE_SET_ITEM, tuple, position, element);
            return 0;
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forTupleAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "PyTuple_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyTuple_GetItem extends PythonBinaryBuiltinNode {

        @Specialization
        Object doPTuple(VirtualFrame frame, PTuple tuple, long key,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage sequenceStorage = tuple.getSequenceStorage();
            // we must do a bounds-check but we must not normalize the index
            if (key < 0 || key >= lenNode.execute(sequenceStorage)) {
                throw raise(IndexError, ErrorMessages.TUPLE_OUT_OF_BOUNDS);
            }
            return getItemNode.execute(frame, sequenceStorage, key);
        }

        @Fallback
        Object doPTuple(Object tuple, @SuppressWarnings("unused") Object key) {
            // TODO(fa) To be absolutely correct, we need to do a 'isinstance' check on the object.
            throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, tuple, tuple);
        }
    }
}
