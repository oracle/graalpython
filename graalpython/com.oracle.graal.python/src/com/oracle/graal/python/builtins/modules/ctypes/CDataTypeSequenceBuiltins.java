/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArray;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArrayType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtrType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointerType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCSimpleType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCStructType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnionType;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCArrayTypeBuiltins.T__LENGTH_;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T__TYPE_;
import static com.oracle.graal.python.nodes.ErrorMessages.ARRAY_LENGTH_MUST_BE_0_NOT_D;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_A_TYPE_OBJECT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesThreadState;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {
                PyCStructType,
                UnionType,
                PyCArrayType,
                PyCFuncPtrType,
                PyCPointerType,
                PyCSimpleType,
})
public final class CDataTypeSequenceBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = CDataTypeSequenceBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CDataTypeSequenceBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.sq_repeat, isComplex = true)
    @GenerateNodeFactory
    abstract static class RepeatNode extends SqRepeatBuiltinNode {
        // TODO: weakref ctypes.cache values
        @Specialization(guards = "length >= 0")
        static Object PyCArrayType_from_ctype(VirtualFrame frame, Object itemtype, int length,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetItem getItem,
                        @Cached CallNode callNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached GetNameNode getNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleFormatNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            Object key = PFactory.createTuple(language, new Object[]{itemtype, length});
            CtypesThreadState ctypes = CtypesThreadState.get(context, context.getLanguage(inliningTarget));
            Object result = getItem.execute(frame, inliningTarget, ctypes.cache, key);
            if (result != null) {
                return result;
            }

            if (!isTypeNode.execute(inliningTarget, itemtype)) {
                throw raiseNode.raise(inliningTarget, TypeError, EXPECTED_A_TYPE_OBJECT);
            }
            TruffleString name = simpleFormatNode.format("%s_Array_%d", getNameNode.execute(inliningTarget, itemtype), length);
            PDict dict = PFactory.createDict(language, new PKeyword[]{new PKeyword(T__LENGTH_, length), new PKeyword(T__TYPE_, itemtype)});
            PTuple tuple = PFactory.createTuple(language, new Object[]{PyCArray});
            result = callNode.execute(frame, PyCArrayType, name, tuple, dict);
            HashingStorage newStorage = setItem.execute(frame, inliningTarget, ctypes.cache, key, result);
            assert newStorage == ctypes.cache;
            return result;
        }

        @Specialization(guards = "length < 0")
        static Object error(@SuppressWarnings("unused") Object self, int length,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ARRAY_LENGTH_MUST_BE_0_NOT_D, length);
        }
    }
}
