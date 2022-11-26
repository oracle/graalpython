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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.HASH_MISMATCH;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_UPDATE;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.DelItemNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.PopNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextDictBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextDictBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyDict_New", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class PyDictNewNode extends PythonVarargsBuiltinNode {

        @Override
        public final Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return execute(frame, self, arguments, keywords);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object run(Object self, Object[] arguments, PKeyword[] keywords) {
            return factory().createDict();
        }
    }

    @Builtin(name = "PyDict_Next", minNumOfPositionalArgs = 2, parameterNames = {"dictObj", "pos"})
    @ArgumentClinic(name = "pos", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    public abstract static class PyDictNextNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PythonCextDictBuiltinsClinicProviders.PyDictNextNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "pos < size(frame, dict, sizeNode)", limit = "1")
        Object run(@SuppressWarnings("unused") VirtualFrame frame, PDict dict, int pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorValue itValue,
                        @Cached HashingStorageIteratorKeyHash itKeyHash,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                HashingStorage storage = dict.getDictStorage();
                HashingStorageNodes.HashingStorageIterator it = getIterator.execute(storage);
                loopProfile.profileCounted(pos);
                for (int i = 0; loopProfile.inject(i <= pos); i++) {
                    if (!itNext.execute(storage, it)) {
                        return getContext().getNativeNull();
                    }
                }
                Object key = itKey.execute(storage, it);
                Object value = itValue.execute(storage, it);
                return factory().createTuple(new Object[]{key, value, itKeyHash.execute(storage, it)});
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "isGreaterPosOrNative(frame, pos, dict, sizeNode, getClassNode, isSubtypeNode)", limit = "1")
        Object run(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") int pos,
                        @SuppressWarnings("unused") @Cached PyObjectSizeNode sizeNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return getContext().getNativeNull();
        }

        protected boolean isGreaterPosOrNative(VirtualFrame frame, int pos, Object obj, PyObjectSizeNode sizeNode, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return (isDict(obj) && pos >= size(frame, obj, sizeNode)) || (!isDict(obj) && !isDictSubtype(frame, obj, getClassNode, isSubtypeNode));
        }

        protected boolean isDict(Object obj) {
            return obj instanceof PDict;
        }

        protected int size(VirtualFrame frame, Object dict, PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, dict);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Pop", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictPopNode extends PythonTernaryBuiltinNode {
        @Specialization()
        public Object pop(VirtualFrame frame, PDict dict, Object key, Object defaultValue,
                        @Cached PopNode popNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return popNode.execute(frame, dict, key, defaultValue);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object popNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object defaultValue,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object pop(VirtualFrame frame, Object obj, Object key, Object defaultValue,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }

    }

    @Builtin(name = "PyDict_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDictSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static int size(PDict dict,
                        @Cached HashingStorageLen lenNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return lenNode.execute(dict.getDictStorage());
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public static Object sizeNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object size(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDictCopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object copy(PDict dict,
                        @Cached HashingStorageCopy copyNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return factory().createDict(copyNode.execute(dict.getDictStorage()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object copyNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object copy(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object getItem(VirtualFrame frame, PDict dict, Object key,
                        @Cached HashingStorageGetItem getItem,
                        @Cached BranchProfile noResultProfile) {
            try {
                Object res = getItem.execute(frame, dict.getDictStorage(), key);
                if (res == null) {
                    noResultProfile.enter();
                    return getContext().getNativeNull();
                }
                return res;
            } catch (PException e) {
                // PyDict_GetItem suppresses all exceptions for historical reasons
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object getItem(VirtualFrame frame, Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDict(Object obj) {
            return obj instanceof PDict;
        }
    }

    @Builtin(name = "PyDict_GetItemWithError", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictGetItemWithErrorNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object getItem(PDict dict, Object key,
                        @Cached HashingStorageGetItem getItem,
                        @Cached BranchProfile noResultProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object res = getItem.execute(null, dict.getDictStorage(), key);
                if (res == null) {
                    noResultProfile.enter();
                    return getContext().getNativeNull();
                }
                return res;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object getItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object getItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictSetItemNode extends PythonTernaryBuiltinNode {
        @Specialization
        public static Object setItem(VirtualFrame frame, PDict dict, Object key, Object value,
                        @Cached SetItemNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setItemNode.execute(frame, dict, key, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public static Object setItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_SetItem_KnownHash", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class PyDictSetItemKnownHashNode extends PythonQuaternaryBuiltinNode {
        @Specialization
        public Object setItem(VirtualFrame frame, PDict dict, Object key, Object value, Object givenHash,
                        @Cached PyObjectHashNode hashNode,
                        @Cached CastToJavaLongExactNode castToLong,
                        @Cached SetItemNode setItemNode,
                        @Cached BranchProfile wrongHashProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (hashNode.execute(frame, key) != castToLong.execute(givenHash)) {
                    wrongHashProfile.enter();
                    throw raise(PythonBuiltinClassType.AssertionError, HASH_MISMATCH);
                }
                setItemNode.execute(frame, dict, key, value);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public static Object setItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value, @SuppressWarnings("unused") Object givenHash,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_SetDefault", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictSetDefaultNode extends PythonTernaryBuiltinNode {
        @Specialization
        public Object setItem(VirtualFrame frame, PDict dict, Object key, Object value,
                        @Cached DictBuiltins.SetDefaultNode setItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return setItemNode.execute(frame, dict, key, value);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public Object setItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_DelItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictDelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static Object delItem(VirtualFrame frame, PDict dict, Object key,
                        @Cached DelItemNode delItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                delItemNode.execute(frame, dict, key);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(dict)", "isDictSubtype(frame, dict, getClassNode, isSubtypeNode)"})
        public static Object delItemNative(VirtualFrame frame, @SuppressWarnings("unused") Object dict, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object delItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "dict", obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyDictContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static int contains(VirtualFrame frame, PDict dict, Object key,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return PInt.intValue(getItem.hasKey(frame, dict.getDictStorage(), key));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(obj)", "isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object containsNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object contains(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Keys", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDictKeysNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object keys(VirtualFrame frame, PDict dict,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return listNode.execute(frame, factory().createDictKeysView(dict));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isDict(obj)", "isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object keysNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object keys(VirtualFrame frame, Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Values", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyDictValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, PDict dict,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return listNode.execute(frame, factory().createDictValuesView(dict));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isDict(obj)", "isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object valuesNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(obj)", "!isDictSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public Object values(VirtualFrame frame, Object obj,
                        @Cached StrNode strNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = "PyDict_Merge", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PyDictMergeNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = {"override != 0"})
        public static Object merge(VirtualFrame frame, PDict a, Object b, @SuppressWarnings("unused") int override,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object updateCallable = lookupAttr.execute(frame, a, T_UPDATE);
                callNode.execute(updateCallable, new Object[]{b});
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "override == 0")
        public static Object merge(VirtualFrame frame, PDict a, PDict b, @SuppressWarnings("unused") int override,
                        @Cached HashingStorageGetIterator getBIter,
                        @Cached HashingStorageIteratorNext itBNext,
                        @Cached HashingStorageIteratorKey itBKey,
                        @Cached HashingStorageIteratorValue itBValue,
                        @Cached HashingStorageIteratorKeyHash itBKeyHash,
                        @Cached HashingStorageGetItemWithHash getAItem,
                        @Cached HashingStorageSetItemWithHash setAItem,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                HashingStorage bStorage = b.getDictStorage();
                HashingStorageNodes.HashingStorageIterator bIt = getBIter.execute(bStorage);
                HashingStorage aStorage = a.getDictStorage();
                while (loopProfile.profile(itBNext.execute(bStorage, bIt))) {
                    Object key = itBKey.execute(bStorage, bIt);
                    long hash = itBKeyHash.execute(bStorage, bIt);
                    if (getAItem.execute(frame, aStorage, key, hash) != null) {
                        setAItem.execute(frame, aStorage, key, hash, itBValue.execute(bStorage, bIt));
                    }
                }
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"override == 0", "!isDict(b)"})
        public static Object merge(VirtualFrame frame, PDict a, Object b, @SuppressWarnings("unused") int override,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached GetItemNode getKeyNode,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getValueNode,
                        @Cached HashingStorageGetItem getItemA,
                        @Cached HashingStorageSetItem setItemA,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached BranchProfile noKeyProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object attr = getAttrNode.execute(frame, a, T_KEYS);
                PList keys = listNode.execute(frame, callNode.execute(frame, attr));

                SequenceStorage keysStorage = keys.getSequenceStorage();
                HashingStorage aStorage = a.getDictStorage();
                int size = keysStorage.length();
                loopProfile.profileCounted(size);
                for (int i = 0; loopProfile.inject(i < size); i++) {
                    Object key = getKeyNode.execute(keysStorage, i);
                    if (!getItemA.hasKey(frame, aStorage, key)) {
                        noKeyProfile.enter();
                        Object value = getValueNode.execute(frame, b, key);
                        aStorage = setItemA.execute(frame, aStorage, key, value);
                    }
                }
                a.setDictStorage(aStorage);
                return 0;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = {"!isDict(a)", "isDictSubtype(frame, a, getClassNode, isSubtypeNode)"})
        public static Object mergeNative(VirtualFrame frame, @SuppressWarnings("unused") Object a, @SuppressWarnings("unused") Object b, @SuppressWarnings("unused") int override,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "dict");
        }

        @Specialization(guards = {"!isDict(a)", "!isDictSubtype(frame, a, getClassNode, isSubtypeNode)"})
        public static Object merge(VirtualFrame frame, Object a, @SuppressWarnings("unused") Object b, @SuppressWarnings("unused") int override,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, a), a);
        }

        protected boolean isDictSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }
}
