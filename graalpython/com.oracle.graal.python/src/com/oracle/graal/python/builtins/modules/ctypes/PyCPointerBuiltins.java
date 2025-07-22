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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointer;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.GetKeepedObjects;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CREATE_INSTANCE_HAS_NO_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_N_INSTEAD_OF_P;
import static com.oracle.graal.python.nodes.ErrorMessages.NULL_POINTER_ACCESS;
import static com.oracle.graal.python.nodes.ErrorMessages.POINTER_DOES_NOT_SUPPORT_ITEM_DELETION;
import static com.oracle.graal.python.nodes.ErrorMessages.POINTER_INDICES_MUST_BE_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.SLICE_START_IS_REQUIRED_FOR_STEP_0;
import static com.oracle.graal.python.nodes.ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO;
import static com.oracle.graal.python.nodes.ErrorMessages.SLICE_STOP_IS_REQUIRED;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.KeepRefNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataGetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataSetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.SqAssItemBuiltinNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PyCPointer)
public final class PyCPointerBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = PyCPointerBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCPointerBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> PCallCapiFunction.callUncached(FUN_PY_TRUFFLE_CDATA_INIT_BUFFER_PROTOCOL, PythonToNativeNode.executeUncached(PyCPointer)));
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class PointerSetContentsNode extends Node {
        abstract void execute(VirtualFrame frame, Node inliningTarget, CDataObject self, Object value);

        @Specialization
        static void set(VirtualFrame frame, Node inliningTarget, CDataObject self, Object value,
                        @Bind PythonLanguage language,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached(inline = false) IsInstanceNode isInstanceNode,
                        @Cached KeepRefNode keepRefNode,
                        @Cached PointerNodes.WritePointerNode writePointerNode) {
            if (value == null) {
                throw raiseNode.raise(inliningTarget, TypeError, POINTER_DOES_NOT_SUPPORT_ITEM_DELETION);
            }
            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for pointer instances";
            assert stgdict.proto != null;
            if (!pyTypeCheck.isCDataObject(inliningTarget, value)) {
                boolean res = isInstanceNode.executeWith(frame, value, stgdict.proto);
                if (!res) {
                    raiseNode.raise(inliningTarget, TypeError, EXPECTED_N_INSTEAD_OF_P, stgdict.proto, value);
                }
            }

            CDataObject dst = (CDataObject) value;
            writePointerNode.execute(inliningTarget, self.b_ptr, dst.b_ptr);

            /*
             * A Pointer instance must keep the value it points to alive. So, a pointer instance has
             * b_length set to 2 instead of 1, and we set 'value' itself as the second item of the
             * b_objects list, additionally.
             */
            keepRefNode.execute(frame, inliningTarget, self, 1, value);

            Object keep = GetKeepedObjects(dst, language);
            keepRefNode.execute(frame, inliningTarget, self, 0, keep);
        }
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        static Object Pointer_new(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CtypesNodes.GenericPyCDataNewNode pyCDataNewNode,
                        @Cached PRaiseNode raiseNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);
            if (dict.proto == null) {
                throw raiseNode.raise(inliningTarget, TypeError, CANNOT_CREATE_INSTANCE_HAS_NO_TYPE);
            }
            return pyCDataNewNode.execute(inliningTarget, type, dict);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object Pointer_init(VirtualFrame frame, CDataObject self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached PointerSetContentsNode setContentsNode) {
            if (args.length > 0) {
                setContentsNode.execute(frame, inliningTarget, self, args[0]);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "contents", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "the object this pointer points to (read-write)")
    @GenerateNodeFactory
    protected abstract static class PointerContentSNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static Object get_contents(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CtypesNodes.PyCDataFromBaseObjNode fromBaseObjNode,
                        @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Cached PRaiseNode raiseNode) {
            if (self.b_ptr.isNull()) {
                throw raiseNode.raise(inliningTarget, ValueError, NULL_POINTER_ACCESS);
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for pointer instances";
            return fromBaseObjNode.execute(inliningTarget, stgdict.proto,
                            self, 0, readPointerNode.execute(inliningTarget, self.b_ptr));
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object set_contents(VirtualFrame frame, CDataObject self, Object value,
                        @Bind Node inliningTarget,
                        @Cached PointerSetContentsNode setContentsNode) {
            setContentsNode.execute(frame, inliningTarget, self, value);
            return PNone.NONE;
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    protected abstract static class PointerBoolNode extends NbBoolBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        boolean Pointer_bool(CDataObject self) {
            return !self.b_ptr.isNull();
        }
    }

    @Slot(value = SlotKind.sq_ass_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class PointerSetItemNode extends SqAssItemBuiltinNode {

        @Specialization
        static void Pointer_ass_item(VirtualFrame frame, CDataObject self, int index, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyCDataSetNode pyCDataSetNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Cached PRaiseNode raiseNode) {
            if (value == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, POINTER_DOES_NOT_SUPPORT_ITEM_DELETION);
            }

            if (self.b_ptr.isNull()) {
                throw raiseNode.raise(inliningTarget, ValueError, NULL_POINTER_ACCESS);
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for pointer instances";

            Object proto = stgdict.proto;
            assert proto != null;

            StgDictObject itemdict = pyTypeStgDictNode.execute(inliningTarget, proto);
            assert itemdict != null : "Cannot be NULL because the itemtype of a pointer is always a ctypes type";

            int size = itemdict.size;
            int offset = index * itemdict.size;

            pyCDataSetNode.execute(frame, self, proto, stgdict.setfunc, value, index, size, readPointerNode.execute(inliningTarget, self.b_ptr).withOffset(offset));
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class PointerGetItemNode extends SqItemBuiltinNode {

        @Specialization
        static Object Pointer_item(CDataObject self, int index,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Exclusive @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (self.b_ptr.isNull()) {
                raiseNullPtr(inliningTarget, raiseNode);
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for pointer object instances";

            Object proto = stgdict.proto;
            assert proto != null;
            StgDictObject itemdict = pyTypeStgDictNode.execute(inliningTarget, proto);
            assert itemdict != null : "proto is the item type of the pointer, a ctypes type, so this cannot be NULL";

            int size = itemdict.size;
            int offset = index * itemdict.size;

            return pyCDataGetNode.execute(inliningTarget, proto, stgdict.getfunc, self, index, size, readPointerNode.execute(inliningTarget, self.b_ptr).withOffset(offset));
        }

        @InliningCutoff
        private static void raiseNullPtr(Node inliningTarget, PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, ValueError, NULL_POINTER_ACCESS);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class PointerSubscriptNode extends MpSubscriptBuiltinNode {

        @Specialization
        static Object doInt(CDataObject self, int index,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Exclusive @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return PointerGetItemNode.Pointer_item(self, index, inliningTarget, pyCDataGetNode,
                            pyTypeStgDictNode, pyObjectStgDictNode, readPointerNode, raiseNode);
        }

        @Specialization(limit = "1")
        static Object doSubscript(VirtualFrame frame, CDataObject self, PSlice slice,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @CachedLibrary("self") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Exclusive @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Exclusive @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Exclusive @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            /*
             * Since pointers have no length, and we want to apply different semantics to negative
             * indices than normal slicing, we have to dissect the slice object ourselves.
             */
            int start, stop, step;
            if (slice.getStep() == PNone.NONE) {
                step = 1;
            } else {
                step = asSizeNode.executeExact(frame, inliningTarget, slice.getStep(), ValueError);
                if (step == 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, SLICE_STEP_CANNOT_BE_ZERO);
                }
            }
            if (slice.getStart() == PNone.NONE) {
                if (step < 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, SLICE_START_IS_REQUIRED_FOR_STEP_0);
                }
                start = 0;
            } else {
                start = asSizeNode.executeExact(frame, inliningTarget, slice.getStart(), ValueError);
            }
            if (slice.getStop() == PNone.NONE) {
                throw raiseNode.raise(inliningTarget, ValueError, SLICE_STOP_IS_REQUIRED);
            }
            stop = asSizeNode.executeExact(frame, inliningTarget, slice.getStop(), ValueError);
            int len;
            if ((step > 0 && start > stop) ||
                            (step < 0 && start < stop)) {
                len = 0;
            } else if (step > 0) {
                len = (stop - start - 1) / step + 1;
            } else {
                len = (stop - start + 1) / step + 1;
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for pointer instances";
            Object proto = stgdict.proto;
            assert proto != null;
            StgDictObject itemdict = pyTypeStgDictNode.execute(inliningTarget, proto);
            assert itemdict != null;
            if (itemdict.getfunc == FieldDesc.c.getfunc) {
                byte[] ptr = bufferLib.getInternalOrCopiedByteArray(self);

                if (len <= 0) {
                    return PFactory.createEmptyBytes(language);
                }
                if (start == 0 && step == 1) {
                    return PFactory.createBytes(language, ptr, len);
                }
                byte[] dest = new byte[len];
                for (int cur = start, i = 0; i < len; cur += step, i++) {
                    dest[i] = ptr[cur];
                }
                return PFactory.createBytes(language, dest);
            }
            if (itemdict.getfunc == FieldDesc.u.getfunc) { // CTYPES_UNICODE
                byte[] ptr = bufferLib.getInternalOrCopiedByteArray(self);

                if (len <= 0) {
                    return T_EMPTY_STRING;
                }
                if (step == 1) {
                    return switchEncodingNode.execute(fromByteArrayNode.execute(ptr, start, len, TruffleString.Encoding.UTF_8, true), TS_ENCODING);
                }
                byte[] dest = new byte[len];
                for (int cur = start, i = 0; i < len; cur += step, i++) {
                    dest[i] = ptr[cur];
                }
                return switchEncodingNode.execute(fromByteArrayNode.execute(dest, TruffleString.Encoding.UTF_8), TS_ENCODING);
            }

            Object[] np = new Object[len];

            for (int cur = start, i = 0; i < len; cur += step, i++) {
                np[i] = PointerGetItemNode.Pointer_item(self, cur, inliningTarget, pyCDataGetNode, pyTypeStgDictNode, pyObjectStgDictNode, readPointerNode, raiseNode);
            }
            return PFactory.createList(language, np);
        }

        @Specialization(guards = "!isPSlice(item)", replaces = "doInt")
        static Object doGeneric(VirtualFrame frame, CDataObject self, Object item,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Exclusive @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Exclusive @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (indexCheckNode.execute(inliningTarget, item)) {
                int i = asSizeNode.executeExact(frame, inliningTarget, item, IndexError);
                return PointerGetItemNode.Pointer_item(self, i, inliningTarget, pyCDataGetNode, pyTypeStgDictNode, pyObjectStgDictNode, readPointerNode, raiseNode);
            }
            throw raiseNode.raise(inliningTarget, TypeError, POINTER_INDICES_MUST_BE_INTEGER);
        }
    }
}
