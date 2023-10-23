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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArray;
import static com.oracle.graal.python.nodes.ErrorMessages.ARRAY_DOES_NOT_SUPPORT_ITEM_DELETION;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_ONLY_ASSIGN_SEQUENCE_OF_SAME_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.INDICES_MUST_BE_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.INDICES_MUST_BE_INTEGERS;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_INDEX;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataGetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataSetNode;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.AdjustIndices;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.SliceUnpack;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PyCArray)
public final class PyCArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCArrayBuiltinsFactory.getFactories();
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

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyCArraySetItemNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "!isPNone(value)")
        static Object Array_ass_item(VirtualFrame frame, CDataObject self, int index, Object value,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Shared @Cached PyCDataSetNode pyCDataSetNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for array object instances";
            if (index < 0 || index >= stgdict.length) {
                throw raiseNode.get(inliningTarget).raise(IndexError, INVALID_INDEX);
            }
            int size = stgdict.size / stgdict.length;
            // self.b_ptr.createStorage(stgdict.ffi_type_pointer, stgdict.size, value);
            int offset = index * size;

            pyCDataSetNode.execute(frame, self, stgdict.proto, stgdict.setfunc, value, index, size, self.b_ptr.withOffset(offset));
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPNone(value)", "!isPSlice(item)"})
        static Object Array_ass_subscript(VirtualFrame frame, CDataObject self, Object item, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSint,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Shared @Cached PyCDataSetNode pyCDataSetNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (indexCheckNode.execute(inliningTarget, item)) {
                int i = asSint.executeExact(frame, inliningTarget, item, IndexError);
                if (i < 0) {
                    i += self.b_length;
                }
                Array_ass_item(frame, self, i, value, inliningTarget,
                                pyObjectStgDictNode,
                                pyCDataSetNode,
                                raiseNode);
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, INDICES_MUST_BE_INTEGER);
            }
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isPNone(value)")
        static Object Array_ass_subscript(VirtualFrame frame, CDataObject self, PSlice slice, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode pySequenceLength,
                        @Cached PyObjectGetItem pySequenceGetItem,
                        @Cached SliceUnpack sliceUnpack,
                        @Cached AdjustIndices adjustIndices,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Shared @Cached PyCDataSetNode pyCDataSetNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            PSlice.SliceInfo sliceInfo = adjustIndices.execute(inliningTarget, self.b_length, sliceUnpack.execute(inliningTarget, slice));
            int start = sliceInfo.start, stop = sliceInfo.stop, step = sliceInfo.step;
            int slicelen = sliceInfo.sliceLength;
            // if ((step < 0 && start < stop) || (step > 0 && start > stop))
            // stop = start;

            int otherlen = pySequenceLength.execute(frame, inliningTarget, value);
            if (otherlen != slicelen) {
                throw raiseNode.get(inliningTarget).raise(ValueError, CAN_ONLY_ASSIGN_SEQUENCE_OF_SAME_SIZE);
            }
            for (int cur = start, i = 0; i < otherlen; cur += step, i++) {
                Array_ass_item(frame, self, cur, pySequenceGetItem.execute(frame, inliningTarget, value, i), inliningTarget,
                                pyObjectStgDictNode,
                                pyCDataSetNode,
                                raiseNode);
            }
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        static Object error(CDataObject self, Object item, PNone value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ARRAY_DOES_NOT_SUPPORT_ITEM_DELETION);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyCArrayGetItemNode extends PythonBinaryBuiltinNode {

        protected static boolean isInvalid(CDataObject self, int index) {
            return index < 0 || index >= self.b_length;
        }

        @Specialization(guards = "!isInvalid(self, index)")
        static Object Array_item(CDataObject self, int index,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            StgDictObject stgdict = pyObjectStgDictNode.execute(inliningTarget, self);
            assert stgdict != null : "Cannot be NULL for array object instances";
            int size = stgdict.size / stgdict.length;
            int offset = index * size;

            return pyCDataGetNode.execute(inliningTarget, stgdict.proto, stgdict.getfunc, self, index, size, self.b_ptr.withOffset(offset));
        }

        @Specialization(limit = "1")
        static Object Array_subscript(CDataObject self, PSlice slice,
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
                    return factory.createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
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
                np[i] = Array_item(self, cur, inliningTarget, pyCDataGetNode, pyObjectStgDictNode);
            }
            return factory.createList(np);
        }

        @Specialization(guards = "!isPSlice(item)")
        static Object Array_item(VirtualFrame frame, CDataObject self, Object item,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Exclusive @Cached PyCDataGetNode pyCDataGetNode,
                        @Exclusive @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!indexCheckNode.execute(inliningTarget, item)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, INDICES_MUST_BE_INTEGERS);
            }
            Object idx = indexNode.execute(frame, inliningTarget, item);
            int index = asSizeNode.executeExact(frame, inliningTarget, idx);
            if (index < 0) {
                index += self.b_length;
            }
            if (isInvalid(self, index)) {
                throw raiseNode.get(inliningTarget).raise(IndexError, INVALID_INDEX);
            }

            return Array_item(self, index, inliningTarget, pyCDataGetNode, pyObjectStgDictNode);
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
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
