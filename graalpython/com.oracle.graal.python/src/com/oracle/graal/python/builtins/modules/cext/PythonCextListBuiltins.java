/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_S;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.LIST_INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListExtendNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListInsertNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListSortNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.builtins.ListNodes.AppendNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes.ConstructTupleNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextListBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextListBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    ///////////// list /////////////
    @Builtin(name = "PyList_New", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PyListNewNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "size < 0")
        public Object newList(VirtualFrame frame, long size,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_S, size);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "size == 0")
        public Object newEmptyList(long size) {
            return factory().createList(PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        @Specialization(guards = "size > 0")
        public Object newList(long size) {
            return factory().createList(array(size));
        }

        private static Object[] array(long size) {
            Object[] a = new Object[(int) size];
            Arrays.fill(a, PNone.NO_VALUE);
            return a;
        }
    }

    @Builtin(name = "PyList_GetItem", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyListGetItemNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization(guards = "pos < 0")
        Object getItem(VirtualFrame frame, @SuppressWarnings("unused") Object list, @SuppressWarnings("unused") long pos,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.IndexError, LIST_INDEX_OUT_OF_RANGE);
        }

        @Specialization(guards = "pos >= 0")
        Object getItem(VirtualFrame frame, PList list, long pos,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.GetItemNode getItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return getItemNode.execute(frame, list, pos);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object pos,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object pos,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_Append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyListAppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object append(PList list, Object newItem,
                        @Cached AppendNode appendNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                appendNode.execute(list, newItem);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object appendNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object newItem,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object append(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object newItem,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_AsTuple", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyListAsTupleNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object append(VirtualFrame frame, PList list,
                        @Cached ConstructTupleNode constructTupleNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return constructTupleNode.execute(frame, list);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object asTupleNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object asTuple(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_GetSlice", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyListGetSliceNode extends PythonTernaryBuiltinNode {

        @Specialization
        Object getSlice(VirtualFrame frame, PList list, long iLow, long iHigh,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.GetItemNode getItemNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached BranchProfile isIntRangeProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (PInt.isIntRange(iLow) && PInt.isIntRange(iHigh)) {
                    isIntRangeProfile.enter();
                    return getItemNode.execute(frame, list, sliceNode.execute(frame, (int) iLow, (int) iHigh, PNone.NONE));
                }
                return getItemNode.execute(frame, list, sliceNode.execute(frame, iLow, iHigh, PNone.NONE));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization
        Object getSlice(VirtualFrame frame, PList list, Object iLow, Object iHigh,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.GetItemNode getItemNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return getItemNode.execute(frame, list, sliceNode.execute(frame, iLow, iHigh, PNone.NONE));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getSliceNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getSlice(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_SetSlice", minNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyListSetSliceNode extends PythonQuaternaryBuiltinNode {

        @Specialization
        static Object getSlice(VirtualFrame frame, PList list, long iLow, long iHigh, Object s,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.SetItemNode setItemNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached BranchProfile isIntRangeProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (PInt.isIntRange(iLow) && PInt.isIntRange(iHigh)) {
                    isIntRangeProfile.enter();
                    setItemNode.execute(frame, list, sliceNode.execute(frame, (int) iLow, (int) iHigh, PNone.NONE), s);
                }
                setItemNode.execute(frame, list, sliceNode.execute(frame, iLow, iHigh, PNone.NONE), s);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization
        static Object getSlice(VirtualFrame frame, PList list, Object iLow, Object iHigh, Object s,
                        @Cached com.oracle.graal.python.builtins.objects.list.ListBuiltins.SetItemNode setItemNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, list, sliceNode.execute(frame, iLow, iHigh, PNone.NONE), s);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object getSliceNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh,
                        @SuppressWarnings("unused") Object s,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object getSlice(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object iLow, @SuppressWarnings("unused") Object iHigh, @SuppressWarnings("unused") Object s,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_Extend", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyListExtendItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object getItem(VirtualFrame frame, PList list, Object iterable,
                        @Cached ListExtendNode extendNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                extendNode.execute(frame, list, iterable);
                return PNone.NONE;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object iterable,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object iterable,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyListSizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object append(PList list,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return lenNode.execute(list.getSequenceStorage());
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asTupleNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asTuple(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_Sort", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyListSortNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object append(VirtualFrame frame, PList list,
                        @Cached ListSortNode sortNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                sortNode.execute(frame, list);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asTupleNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Specialization(guards = {"!isList(obj)", "!isListSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object asTuple(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_Insert", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyListInsertNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object insert(VirtualFrame frame, PList list, Object i, Object item,
                        @Cached ListInsertNode insertNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                insertNode.execute(frame, list, i, item);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isList(obj)", "isListSubtype(frame, obj, getClassNode, isSubtypeNode)"}, limit = "1")
        public static Object insertNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") long i, @SuppressWarnings("unused") Object item,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "list");
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object error(VirtualFrame frame, Object obj, Object i, Object item,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isListSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PList);
        }
    }

    @Builtin(name = "PyList_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyListSetItem extends PythonTernaryBuiltinNode {
        @Specialization
        int doManaged(VirtualFrame frame, PythonNativeWrapper listWrapper, Object position, Object elementWrapper,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectStealingNode elementAsPythonObjectNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                if (!PGuards.isList(delegate)) {
                    throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, delegate, delegate);
                }
                PList list = (PList) delegate;
                Object element = elementAsPythonObjectNode.execute(elementWrapper);
                setItemNode.execute(frame, list.getSequenceStorage(), position, element);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forListAssign(), "invalid item for assignment");
        }
    }

    @Builtin(name = "PyList_Reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyListReverse extends PythonUnaryBuiltinNode {
        @Specialization
        static int reverse(VirtualFrame frame, PList self,
                        @Cached ListBuiltins.ListReverseNode reverseNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                reverseNode.execute(frame, self);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }
}
