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
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_S_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.FrozenSetNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetBuiltins.ClearNode;
import com.oracle.graal.python.builtins.objects.set.SetNodes.ConstructSetNode;
import com.oracle.graal.python.builtins.objects.set.SetNodes.DiscardNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextSetBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextSetBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PySet_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySetNewNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"!isNone(iterable)", "!isNoValue(iterable)"})
        public Object newSet(VirtualFrame frame, Object iterable,
                        @Cached ConstructSetNode constructSetNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return constructSetNode.executeWith(frame, iterable);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object newSet(PNone iterable) {
            return factory().createSet();
        }
    }

    @Builtin(name = "PySet_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySetContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static int contains(VirtualFrame frame, PSet anyset, Object item,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                HashingStorage storage = anyset.getDictStorage();
                return PInt.intValue(getItem.hasKey(frame, storage, item));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization
        public static int contains(VirtualFrame frame, PFrozenSet anyset, Object item,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                HashingStorage storage = anyset.getDictStorage();
                return PInt.intValue(getItem.hasKey(frame, storage, item));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "isSetSubtype(frame, anyset, getClassNode, isSubtypeNode)", limit = "1")
        public static Object containsNative(VirtualFrame frame, @SuppressWarnings("unused") Object anyset, @SuppressWarnings("unused") Object item,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Shared("raiseNative") @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "set");
        }

        @Specialization(guards = {"!isPSet(anyset)", "!isPFrozenSet(anyset)", "!isSetSubtype(frame, anyset, getClassNode, isSubtypeNode)"}, limit = "1")
        public static Object contains(VirtualFrame frame, Object anyset, @SuppressWarnings("unused") Object item,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Shared("raiseNative") @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, anyset), anyset);
        }

        protected boolean isSetSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PSet) || isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PFrozenSet);
        }
    }

    @Builtin(name = "PySet_NextEntry", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    public abstract static class PySetNextEntryNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "pos < size(frame, set, sizeNode)", limit = "3")
        Object nextEntry(VirtualFrame frame, PSet set, long pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @CachedLibrary("set.getDictStorage()") HashingStorageLibrary lib,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached PyObjectHashNode hashNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return next(frame, (int) pos, set.getDictStorage(), lib, loopProfile, hashNode, transformExceptionToNativeNode, factory());
        }

        @Specialization(guards = "pos < size(frame, set, sizeNode)", limit = "3")
        Object nextEntry(VirtualFrame frame, PFrozenSet set, long pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @CachedLibrary("set.getDictStorage()") HashingStorageLibrary lib,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached PyObjectHashNode hashNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            return next(frame, (int) pos, set.getDictStorage(), lib, loopProfile, hashNode, transformExceptionToNativeNode, factory());
        }

        @Specialization(guards = {"isPSet(set) || isPFrozenSet(set)", "pos >= size(frame, set, sizeNode)"}, limit = "1")
        Object nextEntry(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object set, @SuppressWarnings("unused") long pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode) {
            return getContext().getNativeNull();
        }

        @Specialization(guards = {"!isPSet(anyset)", "!isPFrozenSet(anyset)", "isSetSubtype(frame, anyset, getClassNode, isSubtypeNode)"})
        public Object nextNative(VirtualFrame frame, @SuppressWarnings("unused") Object anyset, @SuppressWarnings("unused") Object pos,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "set");
        }

        @Specialization(guards = {"!isPSet(anyset)", "!isPFrozenSet(anyset)", "!isSetSubtype(frame, anyset, getClassNode, isSubtypeNode)"})
        public Object nextEntry(VirtualFrame frame, Object anyset, @SuppressWarnings("unused") Object pos,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, anyset), anyset);
        }

        protected boolean isSetSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PSet) || isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PFrozenSet);
        }

        protected int size(VirtualFrame frame, Object set, PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, set);
        }

        private Object next(VirtualFrame frame, int pos, HashingStorage storage, HashingStorageLibrary lib, LoopConditionProfile loopProfile, PyObjectHashNode hashNode,
                        TransformExceptionToNativeNode transformExceptionToNativeNode, PythonObjectFactory factory) {
            try {
                HashingStorageIterator<DictEntry> it = lib.entries(storage).iterator();
                DictEntry e = null;
                loopProfile.profileCounted(pos);
                for (int i = 0; loopProfile.inject(i <= pos); i++) {
                    e = it.next();
                }
                return factory.createTuple(new Object[]{e.key, hashNode.execute(frame, e.key)});
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PySet_Pop", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySetPopNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object pop(VirtualFrame frame, PSet set,
                        @Cached com.oracle.graal.python.builtins.objects.set.SetBuiltins.PopNode popNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return popNode.execute(frame, set);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isPSet(set)", "isSetSubtype(frame, set, getClassNode, isSubtypeNode)"})
        public Object popNative(VirtualFrame frame, @SuppressWarnings("unused") Object set,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "set");
        }

        @Specialization(guards = {"!isPSet(set)", "!isSetSubtype(frame, set, getClassNode, isSubtypeNode)"})
        public Object pop(VirtualFrame frame, Object set,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, set), set);
        }

        protected boolean isSetSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PSet);
        }
    }

    @Builtin(name = "PyFrozenSet_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyFrozenSetNewNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"!isNone(iterable)", "!isNoValue(iterable)"})
        public Object newFrozenSet(VirtualFrame frame, Object iterable,
                        @Cached FrozenSetNode frozenSetNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return frozenSetNode.execute(frame, PythonBuiltinClassType.PFrozenSet, iterable);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object newFrozenSet(PNone iterable) {
            return factory().createFrozenSet(PythonBuiltinClassType.PFrozenSet);
        }
    }

    @Builtin(name = "PySet_Discard", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySetDiscardNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!isNone(s)", "!isNoValue(s)"})
        public static Object discard(VirtualFrame frame, PSet s, Object key,
                        @Cached DiscardNode discardNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return discardNode.execute(frame, s, key) ? 1 : 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isPSet(set)", "isSetSubtype(frame, set, getClassNode, isSubtypeNode)"})
        public static Object popNative(VirtualFrame frame, @SuppressWarnings("unused") Object set, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "set");
        }

        @Specialization(guards = {"!isPSet(set)", "!isSetSubtype(frame, set, getClassNode, isSubtypeNode)"})
        public static Object discard(VirtualFrame frame, Object set, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, set), set);
        }

        protected boolean isSetSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PSet);
        }
    }

    @Builtin(name = "PySet_Clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySetClearNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!isNone(s)", "!isNoValue(s)"})
        public static Object clear(VirtualFrame frame, PSet s,
                        @Cached ClearNode clearNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                clearNode.execute(frame, s);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isPSet(set)", "isSetSubtype(frame, set, getClassNode, isSubtypeNode)"})
        public static Object clearNative(VirtualFrame frame, @SuppressWarnings("unused") Object set,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "set");
        }

        @Specialization(guards = {"!isPSet(set)", "!isSetSubtype(frame, set, getClassNode, isSubtypeNode)"})
        public static Object clear(VirtualFrame frame, Object set,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, set), set);
        }

        protected boolean isSetSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PSet);
        }
    }

    @Builtin(name = "PySet_Add", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySetAdd extends PythonBinaryBuiltinNode {

        @Specialization
        static int add(VirtualFrame frame, PBaseSet self, Object o,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, self, o, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
            return 0;
        }

        @Specialization(guards = "!isAnySet(self)")
        static int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, EXPECTED_S_NOT_P, "a set object", self);
        }
    }

    @Builtin(name = "PySet_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PySetSize extends PythonUnaryBuiltinNode {
        @Specialization
        static int doSet(PSet type,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(type.getDictStorage());
        }
    }
}
