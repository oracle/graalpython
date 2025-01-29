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
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL;
import static com.oracle.graal.python.nodes.ErrorMessages.ARRAY_DOES_NOT_SUPPORT_ITEM_DELETION;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_ONLY_ASSIGN_SEQUENCE_OF_SAME_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.INDICES_MUST_BE_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.INDICES_MUST_BE_INTEGERS;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_INDEX;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataGetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataSetNode;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.AdjustIndices;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.SliceUnpack;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PyCArray)
public final class PyCArrayBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = PyCArrayBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCArrayBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> PCallCapiFunction.callUncached(FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL, PythonToNativeNode.executeUncached(PyCArray)));
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        static Object newCData(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CtypesNodes.GenericPyCDataNewNode newNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);
            return newNode.execute(inliningTarget, type, dict);
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class InitNode extends PythonBuiltinNode {

        @Specialization
        static Object Array_init(VirtualFrame frame, CDataObject self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSetItem pySequenceSetItem) {
            int n = args.length;
            for (int i = 0; i < n; ++i) {
                pySequenceSetItem.execute(frame, inliningTarget, self, i, args[i]);
            }
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.sq_ass_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class PyCArraySetItemNode extends TpSlotSqAssItem.SqAssItemBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        static void Array_ass_item(VirtualFrame frame, CDataObject self, int index, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PyCDataSetNode pyCDataSetNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for array object instances";
            if (index < 0 || index >= stgdict.length) {
                throw raiseNode.get(inliningTarget).raise(IndexError, INVALID_INDEX);
            }
            int size = stgdict.size / stgdict.length;
            // self.b_ptr.createStorage(stgdict.ffi_type_pointer, stgdict.size, value);
            int offset = index * size;

            pyCDataSetNode.execute(frame, self, stgdict.proto, stgdict.setfunc, value, index, size, self.b_ptr.withOffset(offset));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(value)")
        static void error(CDataObject self, int index, PNone value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ARRAY_DOES_NOT_SUPPORT_ITEM_DELETION);
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class PyCArraySetSubscriptNode extends MpAssSubscriptBuiltinNode {

        @Specialization(guards = {"!isNoValue(value)", "!isPSlice(indexObj)"})
        static void Array_ass_subscript(VirtualFrame frame, CDataObject self, Object indexObj, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSint,
                        @Shared @Cached PyCArraySetItemNode setItemNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (indexCheckNode.execute(inliningTarget, indexObj)) {
                int index = asSint.executeExact(frame, inliningTarget, indexObj, IndexError);
                if (index < 0) {
                    index += self.b_length;
                }
                setItemNode.executeIntKey(frame, self, index, value);
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, INDICES_MUST_BE_INTEGER);
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static void Array_ass_subscript(VirtualFrame frame, CDataObject self, PSlice slice, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode pySequenceLength,
                        @Cached PyObjectGetItem pySequenceGetItem,
                        @Cached SliceUnpack sliceUnpack,
                        @Cached AdjustIndices adjustIndices,
                        @Shared @Cached PyCArraySetItemNode setItemNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, self.b_length, sliceUnpack.execute(inliningTarget, slice));
            int start = sliceInfo.start, step = sliceInfo.step;
            int slicelen = sliceInfo.sliceLength;

            int otherlen = pySequenceLength.execute(frame, inliningTarget, value);
            if (otherlen != slicelen) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CAN_ONLY_ASSIGN_SEQUENCE_OF_SAME_SIZE);
            }
            for (int cur = start, i = 0; i < otherlen; cur += step, i++) {
                Object item = pySequenceGetItem.execute(frame, inliningTarget, value, i);
                setItemNode.executeIntKey(frame, self, cur, item);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(value)")
        static void error(CDataObject self, Object index, PNone value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ARRAY_DOES_NOT_SUPPORT_ITEM_DELETION);
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class PyCArrayGetItemNode extends SqItemBuiltinNode {
        @Specialization
        static Object doIt(CDataObject self, int index,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached PyCDataGetNode pyCDataGetNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            checkIndex(inliningTarget, self, index, raiseNode);
            return getItem(inliningTarget, self, index, pyCDataGetNode, pyObjectStgDictNode);
        }

        static Object getItem(Node inliningTarget, CDataObject self, int index, PyCDataGetNode pyCDataGetNode, PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for array object instances";
            int size = stgdict.size / stgdict.length;
            int offset = index * size;
            return pyCDataGetNode.execute(inliningTarget, stgdict.proto, stgdict.getfunc, self, index, size, self.b_ptr.withOffset(offset));
        }

        private static void checkIndex(Node inliningTarget, CDataObject self, int index, Lazy raiseNode) {
            if (index < 0 || index >= self.b_length) {
                raiseInvalidIndex(inliningTarget, raiseNode);
            }
        }

        @InliningCutoff
        private static void raiseInvalidIndex(Node inliningTarget, Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(IndexError, INVALID_INDEX);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class PyCArraySubscriptNode extends MpSubscriptBuiltinNode {

        protected static boolean isInvalid(CDataObject self, int index) {
            return index < 0 || index >= self.b_length;
        }

        @Specialization(guards = "!isInvalid(self, index)")
        static Object doInt(CDataObject self, int index,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            return PyCArrayGetItemNode.getItem(inliningTarget, self, index, pyCDataGetNode, pyObjectStgDictNode);
        }

        @Specialization(limit = "1")
        static Object doSlice(CDataObject self, PSlice slice,
                        @CachedLibrary("self") PythonBufferAccessLibrary bufferLib,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached SliceUnpack sliceUnpack,
                        @Cached AdjustIndices adjustIndices,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached PythonObjectFactory factory) {
            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for array object instances";
            Object proto = stgdict.proto;

            StgDictObject itemdict = pyTypeStgDictNode.execute(inliningTarget, proto);
            assert itemdict != null : "proto is the item type of the array, a ctypes type, so this cannot be NULL";

            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, self.b_length, sliceUnpack.execute(inliningTarget, slice));
            int slicelen = sliceInfo.sliceLength;
            if (itemdict.getfunc == FieldDesc.c.getfunc) {
                byte[] ptr = bufferLib.getInternalOrCopiedByteArray(self);

                if (slicelen <= 0) {
                    return factory.createEmptyBytes();
                }
                if (sliceInfo.step == 1) {
                    return factory.createBytes(ptr, sliceInfo.start, slicelen);
                }
                byte[] dest = new byte[slicelen];
                for (int cur = sliceInfo.start, i = 0; i < slicelen; cur += sliceInfo.step, i++) {
                    dest[i] = ptr[cur];
                }

                return factory.createBytes(dest);
            }
            if (itemdict.getfunc == FieldDesc.u.getfunc) { // CTYPES_UNICODE
                byte[] ptr = bufferLib.getInternalOrCopiedByteArray(self);

                if (slicelen <= 0) {
                    return T_EMPTY_STRING;
                }
                if (sliceInfo.step == 1) {
                    byte[] bytes = PythonUtils.arrayCopyOfRange(ptr, sliceInfo.start, slicelen);
                    return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, TruffleString.Encoding.UTF_8), TS_ENCODING);
                }

                byte[] dest = new byte[slicelen];
                for (int cur = sliceInfo.start, i = 0; i < slicelen; cur += sliceInfo.step, i++) {
                    dest[i] = ptr[cur];
                }

                return switchEncodingNode.execute(fromByteArrayNode.execute(dest, TruffleString.Encoding.UTF_8), TS_ENCODING);
            }

            Object[] np = new Object[slicelen];

            for (int cur = sliceInfo.start, i = 0; i < slicelen; cur += sliceInfo.step, i++) {
                np[i] = doInt(self, cur, inliningTarget, pyCDataGetNode, pyObjectStgDictNode);
            }
            return factory.createList(np);
        }

        @Specialization(guards = "!isPSlice(item)", replaces = "doInt")
        static Object doGeneric(VirtualFrame frame, CDataObject self, Object item,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached InlinedConditionProfile negativeIndexProfile,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!indexCheckNode.execute(inliningTarget, item)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, INDICES_MUST_BE_INTEGERS);
            }
            Object idx = indexNode.execute(frame, inliningTarget, item);
            int index = asSizeNode.executeExact(frame, inliningTarget, idx);
            if (negativeIndexProfile.profile(inliningTarget, index < 0)) {
                index += self.b_length;
            }
            PyCArrayGetItemNode.checkIndex(inliningTarget, self, index, raiseNode);

            return doInt(self, index, inliningTarget, pyCDataGetNode, pyObjectStgDictNode);
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int Array_length(CDataObject self) {
            return self.b_length;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Cached PythonObjectFactory factory) {
            return factory.createGenericAlias(cls, key);
        }
    }
}
