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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointer;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.GenericPyCDataNew;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.GetKeepedObjects;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCData_FromBaseObj;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CREATE_INSTANCE_HAS_NO_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_INSTEAD_OF_S;
import static com.oracle.graal.python.nodes.ErrorMessages.NULL_POINTER_ACCESS;
import static com.oracle.graal.python.nodes.ErrorMessages.POINTER_DOES_NOT_SUPPORT_ITEM_DELETION;
import static com.oracle.graal.python.nodes.ErrorMessages.POINTER_INDICES_MUST_BE_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.SLICE_START_IS_REQUIRED_FOR_STEP_0;
import static com.oracle.graal.python.nodes.ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO;
import static com.oracle.graal.python.nodes.ErrorMessages.SLICE_STOP_IS_REQUIRED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
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
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.KeepRefNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataGetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataSetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PyCPointer)
public class PyCPointerBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCPointerBuiltinsFactory.getFactories();
    }

    static void Pointer_set_contents(VirtualFrame frame, CDataObject self, Object value,
                    PyTypeCheck pyTypeCheck,
                    PRaiseNode raiseNode,
                    PyObjectStgDictNode pyObjectStgDictNode,
                    GetNameNode getNameNode,
                    IsInstanceNode isInstanceNode,
                    GetClassNode getClassNode,
                    KeepRefNode keepRefNode,
                    PythonObjectFactory factory) {
        if (value == null) {
            throw raiseNode.raise(TypeError, POINTER_DOES_NOT_SUPPORT_ITEM_DELETION);
        }
        StgDictObject stgdict = pyObjectStgDictNode.execute(self);
        assert stgdict != null : "Cannot be NULL for pointer instances";
        assert stgdict.proto != null;
        if (!pyTypeCheck.isCDataObject(value)) {
            boolean res = isInstanceNode.executeWith(frame, value, stgdict.proto);
            if (!res) {
                raiseNode.raise(TypeError, EXPECTED_S_INSTEAD_OF_S,
                                getNameNode.execute(stgdict.proto),
                                getNameNode.execute(getClassNode.execute(value)));
            }
        }

        CDataObject dst = (CDataObject) value;
        self.b_ptr = dst.b_ptr;

        /*
         * A Pointer instance must keep the value it points to alive. So, a pointer instance has
         * b_length set to 2 instead of 1, and we set 'value' itself as the second item of the
         * b_objects list, additionally.
         */
        keepRefNode.execute(frame, self, 1, value);

        Object keep = GetKeepedObjects(dst, factory);
        keepRefNode.execute(frame, self, 0, keep);
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        protected Object Pointer_new(Object type, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            if (dict.proto == null) {
                throw raise(TypeError, CANNOT_CREATE_INSTANCE_HAS_NO_TYPE);
            }
            return GenericPyCDataNew(dict, factory().createCDataObject(type));
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class InitNode extends PythonBuiltinNode {

        @Specialization
        Object Pointer_init(VirtualFrame frame, CDataObject self, Object[] args, @SuppressWarnings("unused") PKeyword[] kwds,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached KeepRefNode keepRefNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached GetNameNode getNameNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached GetClassNode getClassNode) {
            if (args.length > 0) {
                Pointer_set_contents(frame, self, args[0],
                                pyTypeCheck, getRaiseNode(), pyObjectStgDictNode, getNameNode, isInstanceNode,
                                getClassNode, keepRefNode, factory());
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "contents", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "the object this pointer points to (read-write)")
    @GenerateNodeFactory
    protected abstract static class PointerContentNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object get_contents(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            if (PtrValue.isNull(self.b_ptr)) {
                throw raise(ValueError, NULL_POINTER_ACCESS);
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(self);
            assert stgdict != null : "Cannot be NULL for pointer instances";
            return PyCData_FromBaseObj(stgdict.proto,
                            self, 0, self.b_ptr, factory(), getRaiseNode(), pyTypeStgDictNode);
        }

        @Specialization(guards = "!isNoValue(value)")
        Object set_contents(VirtualFrame frame, CDataObject self, Object value,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached GetNameNode getNameNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached GetClassNode getClassNode,
                        @Cached KeepRefNode keepRefNode) {
            Pointer_set_contents(frame, self, value, pyTypeCheck, getRaiseNode(), pyObjectStgDictNode,
                            getNameNode, isInstanceNode, getClassNode, keepRefNode, factory());
            return PNone.NONE;
        }
    }

    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class PointerBoolNode extends PythonUnaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        boolean Pointer_bool(CDataObject self) {
            return !PtrValue.isNull(self.b_ptr);
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PointerSetItemNode extends PythonTernaryBuiltinNode {

        @Specialization
        Object Pointer_ass_item(VirtualFrame frame, CDataObject self, int index, Object value,
                        @Cached PyCDataSetNode pyCDataSetNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            if (value == PNone.NO_VALUE) {
                throw raise(TypeError, POINTER_DOES_NOT_SUPPORT_ITEM_DELETION);
            }

            if (PtrValue.isNull(self.b_ptr)) {
                throw raise(ValueError, NULL_POINTER_ACCESS);
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(self);
            assert stgdict != null : "Cannot be NULL for pointer instances";

            Object proto = stgdict.proto;
            assert proto != null;

            StgDictObject itemdict = pyTypeStgDictNode.execute(proto);
            assert itemdict != null : "Cannot be NULL because the itemtype of a pointer is always a ctypes type";

            int size = itemdict.size;
            int offset = index * itemdict.size;

            pyCDataSetNode.execute(frame, self, proto, stgdict.setfunc, value, index, size, self.b_ptr.ref(offset));
            return PNone.NONE;
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PointerGetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object Pointer_item(CDataObject self, int index,
                        @Shared @Cached PyCDataGetNode pyCDataGetNode,
                        @Shared @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Shared @Cached PyObjectStgDictNode pyObjectStgDictNode) {
            if (PtrValue.isNull(self.b_ptr)) {
                throw raise(ValueError, NULL_POINTER_ACCESS);
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(self);
            assert stgdict != null : "Cannot be NULL for pointer object instances";

            Object proto = stgdict.proto;
            assert proto != null;
            StgDictObject itemdict = pyTypeStgDictNode.execute(proto);
            assert itemdict != null : "proto is the item type of the pointer, a ctypes type, so this cannot be NULL";

            int size = itemdict.size;
            int offset = index * itemdict.size;

            return pyCDataGetNode.execute(proto, stgdict.getfunc, self, index, size, self.b_ptr.ref(offset));
        }

        @Specialization(limit = "1")
        Object Pointer_subscriptSlice(VirtualFrame frame, CDataObject self, PSlice slice,
                        @CachedLibrary("self") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PyCDataGetNode pyCDataGetNode,
                        @Shared @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Shared @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            /*
             * Since pointers have no length, and we want to apply different semantics to negative
             * indices than normal slicing, we have to dissect the slice object ourselves.
             */
            int start, stop, step;
            if (slice.getStep() == PNone.NONE) {
                step = 1;
            } else {
                step = asSizeNode.executeExact(frame, slice.getStep(), ValueError);
                if (step == 0) {
                    throw raise(ValueError, SLICE_STEP_CANNOT_BE_ZERO);
                }
            }
            if (slice.getStart() == PNone.NONE) {
                if (step < 0) {
                    throw raise(ValueError, SLICE_START_IS_REQUIRED_FOR_STEP_0);
                }
                start = 0;
            } else {
                start = asSizeNode.executeExact(frame, slice.getStart(), ValueError);
            }
            if (slice.getStop() == PNone.NONE) {
                throw raise(ValueError, SLICE_STOP_IS_REQUIRED);
            }
            stop = asSizeNode.executeExact(frame, slice.getStop(), ValueError);
            int len;
            if ((step > 0 && start > stop) ||
                            (step < 0 && start < stop)) {
                len = 0;
            } else if (step > 0) {
                len = (stop - start - 1) / step + 1;
            } else {
                len = (stop - start + 1) / step + 1;
            }

            StgDictObject stgdict = pyObjectStgDictNode.execute(self);
            assert stgdict != null : "Cannot be NULL for pointer instances";
            Object proto = stgdict.proto;
            assert proto != null;
            StgDictObject itemdict = pyTypeStgDictNode.execute(proto);
            assert itemdict != null;
            if (itemdict.getfunc == FieldDesc.c.getfunc) {
                byte[] ptr = bufferLib.getInternalOrCopiedByteArray(self);

                if (len <= 0) {
                    return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
                }
                if (step == 1) {
                    return factory().createBytes(ptr, start, len);
                }
                byte[] dest = new byte[len];
                for (int cur = start, i = 0; i < len; cur += step, i++) {
                    dest[i] = ptr[cur];
                }
                return factory().createBytes(dest);
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
                np[i] = Pointer_item(self, cur, pyCDataGetNode, pyTypeStgDictNode, pyObjectStgDictNode);
            }
            return factory().createList(np);
        }

        @Specialization(guards = "!isPSlice(item)")
        Object Pointer_subscript(VirtualFrame frame, CDataObject self, Object item,
                        @Shared @Cached PyCDataGetNode pyCDataGetNode,
                        @Shared @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Shared @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyIndexCheckNode indexCheckNode) {
            if (indexCheckNode.execute(item)) {
                int i = asSizeNode.executeExact(frame, item, IndexError);
                return Pointer_item(self, i, pyCDataGetNode, pyTypeStgDictNode, pyObjectStgDictNode);
            }
            throw raise(TypeError, POINTER_INDICES_MUST_BE_INTEGER);
        }
    }
}
