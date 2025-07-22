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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCData;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_HASPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.T_UNPICKLE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CTYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.CTYPES_OBJECTS_CONTAINING_POINTERS_CANNOT_BE_PICKLED;
import static com.oracle.graal.python.nodes.ErrorMessages.P_DICT_MUST_BE_A_DICTIONARY_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.UNHASHABLE_TYPE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PyCData)
public final class CDataBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = CDataBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CDataBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> PCallCapiFunction.callUncached(FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL, PythonToNativeNode.executeUncached(PyCData)));
    }

    @Builtin(name = "_b_base_", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, isGetter = true, doc = "the base object")
    @GenerateNodeFactory
    protected abstract static class BBaseNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getBBase(CDataObject self) {
            return self.b_base == null ? PNone.NONE : self.b_base;
        }
    }

    @Builtin(name = "_b_needsfree_", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, isGetter = true, doc = "whether the object owns the memory or not")
    @GenerateNodeFactory
    protected abstract static class BNeedsFreeNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getBNeedsFree(CDataObject self) {
            return self.b_needsfree;
        }
    }

    @Builtin(name = "_objects", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, isGetter = true, doc = "internal objects tree (NEVER CHANGE THIS OBJECT!)")
    @GenerateNodeFactory
    protected abstract static class ObjectsNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object getObjects(CDataObject self) {
            return self.b_objects == null ? PNone.NONE : self.b_objects;
        }
    }

    @Builtin(name = "__ctypes_from_outparam__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class CtypesFromOutparamNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object PyCData_from_outparam(CDataObject self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    protected abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(@SuppressWarnings("unused") CDataObject self,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, UNHASHABLE_TYPE);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(VirtualFrame frame, CDataObject self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached("create(T___DICT__)") GetAttributeNode getAttributeNode,
                        @Cached ReadAttributeFromPythonObjectNode readAttrNode,
                        @Cached PointerNodes.ReadBytesNode readBytesNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PRaiseNode raiseNode) {
            StgDictObject stgDict = pyObjectStgDictNode.execute(inliningTarget, self);
            if ((stgDict.flags & (TYPEFLAG_ISPOINTER | TYPEFLAG_HASPOINTER)) != 0) {
                throw raiseNode.raise(inliningTarget, ValueError, CTYPES_OBJECTS_CONTAINING_POINTERS_CANNOT_BE_PICKLED);
            }
            Object dict = getAttributeNode.executeObject(frame, self);
            Object[] t1 = new Object[]{dict, null};
            t1[1] = PFactory.createBytes(language, readBytesNode.execute(inliningTarget, self.b_ptr, self.b_size));
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object[] t2 = new Object[]{clazz, PFactory.createTuple(language, t1)};
            PythonModule ctypes = PythonContext.get(inliningTarget).lookupBuiltinModule(T__CTYPES);
            Object unpickle = readAttrNode.execute(ctypes, T_UNPICKLE, null);
            if (unpickle == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("unpickle isn't supported yet."));
            }
            Object[] t3 = new Object[]{unpickle, PFactory.createTuple(language, t2)};
            return PFactory.createTuple(language, t3); // "O(O(NN))"
        }
    }

    @ImportStatic(SpecialAttributeNames.class)
    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object PyCData_setstate(VirtualFrame frame, CDataObject self, PTuple args,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached("create(T___DICT__)") GetAttributeNode getAttributeNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = args.getSequenceStorage();
            Object[] array = getArray.execute(inliningTarget, storage);
            if (storage.length() < 3 || !PGuards.isDict(array[0]) || !PGuards.isInteger(array[2])) {
                throw raiseNode.raise(inliningTarget, TypeError);
            }
            PDict dict = (PDict) array[0];
            Object data = array[1];
            int len = asSizeNode.executeExact(frame, inliningTarget, array[2]);
            // PyArg_ParseTuple(args, "O!s#",&PyDict_Type, &dict, &data, &len))

            if (len > self.b_size) {
                len = self.b_size;
            }
            memmove(inliningTarget, self.b_ptr, data, len);
            Object mydict = getAttributeNode.executeObject(frame, self);
            if (!PGuards.isDict(mydict)) {
                throw raiseNode.raise(inliningTarget, TypeError, P_DICT_MUST_BE_A_DICTIONARY_NOT_P, self, mydict);
            }
            PDict selfDict = (PDict) mydict;
            addAllToOtherNode.execute(frame, inliningTarget, dict.getDictStorage(), selfDict);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        private static void memmove(Node raisingNode, Object dest, Object src, int len) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseStatic(raisingNode, NotImplementedError, toTruffleStringUncached("memmove is partially supported.")); // TODO
        }
    }
}
