/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.attributes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetObjectDictNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.NativeMemberNames;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectNotTypeNodeGen;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectNotTypeUncachedNodeGen;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNodeGen.WriteAttributeToObjectTpDictNodeGen;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic({PythonOptions.class, NativeMemberNames.class})
public abstract class WriteAttributeToObjectNode extends ObjectAttributeNode {

    public abstract boolean execute(Object primary, Object key, Object value);

    public static WriteAttributeToObjectNode create() {
        return WriteAttributeToObjectNotTypeNodeGen.create();
    }

    public static WriteAttributeToObjectNode createForceType() {
        return WriteAttributeToObjectTpDictNodeGen.create();
    }

    public static WriteAttributeToObjectNode getUncached() {
        return WriteAttributeToObjectNotTypeUncachedNodeGen.getUncached();
    }

    protected static boolean isAttrWritable(IsBuiltinClassProfile exactBuiltinInstanceProfile, PythonObject self, Object key) {
        if (isHiddenKey(key) || self instanceof PythonManagedClass || self instanceof PFunction || self instanceof PMethod || self instanceof PythonModule || self instanceof PBaseException) {
            return true;
        }
        return !exactBuiltinInstanceProfile.profileIsAnyBuiltinObject(self);
    }

    private static void handlePythonClass(ConditionProfile isClassProfile, PythonObject object, Object key) {
        if (isClassProfile.profile(object instanceof PythonManagedClass)) {
            ((PythonManagedClass) object).invalidateFinalAttribute(key);
        }
    }

    // write to the DynamicObject
    @Specialization(guards = {
                    "isAttrWritable(exactBuiltinInstanceProfile, object, key)",
                    "isHiddenKey(key) || !lib.hasDict(object)"
    }, limit = "1")
    protected boolean writeToDynamicStorage(PythonObject object, Object key, Object value,
                    @CachedLibrary("object") @SuppressWarnings("unused") PythonObjectLibrary lib,
                    @Cached("create()") WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile isClassProfile,
                    @Exclusive @Cached @SuppressWarnings("unused") IsBuiltinClassProfile exactBuiltinInstanceProfile) {
        handlePythonClass(isClassProfile, object, key);
        return writeAttributeToDynamicObjectNode.execute(object.getStorage(), key, value);
    }

    // write to the dict
    @Specialization(guards = {
                    "!isHiddenKey(key)",
                    "lib.hasDict(object)"
    }, limit = "1")
    protected boolean writeToDict(PythonObject object, Object key, Object value,
                    @CachedLibrary("object") PythonObjectLibrary lib,
                    @Cached BranchProfile updateStorage,
                    @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                    @Cached HashingStorageNodes.SetItemNode setItemNode,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile isClassProfile) {
        handlePythonClass(isClassProfile, object, key);
        PHashingCollection dict = lib.getDict(object);
        HashingStorage dictStorage = getDictStorage.execute(dict);
        HashingStorage hashingStorage = setItemNode.execute(null, dictStorage, key, value);
        if (dictStorage != hashingStorage) {
            updateStorage.enter();
            dict.setDictStorage(hashingStorage);
        }
        return true;
    }

    private static boolean writeNativeGeneric(PythonAbstractNativeObject object, Object key, Object value, Object d, HashingCollectionNodes.SetItemNode setItemNode, PRaiseNode raiseNode) {
        if (d instanceof PHashingCollection) {
            setItemNode.execute(null, ((PHashingCollection) d), key, value);
            return true;
        } else {
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, "'%p' object has no attribute '%s'", object, key);
        }
    }

    @Specialization(guards = "isErrorCase(exactBuiltinInstanceProfile, lib, object, key)")
    protected static boolean doError(Object object, Object key, @SuppressWarnings("unused") Object value,
                    @CachedLibrary(limit = "1") @SuppressWarnings("unused") PythonObjectLibrary lib,
                    @Exclusive @Cached @SuppressWarnings("unused") IsBuiltinClassProfile exactBuiltinInstanceProfile,
                    @Exclusive @Cached PRaiseNode raiseNode) {
        throw raiseNode.raise(PythonBuiltinClassType.AttributeError, "'%p' object has no attribute '%s'", object, key);
    }

    protected static boolean isErrorCase(IsBuiltinClassProfile exactBuiltinInstanceProfile, PythonObjectLibrary lib, Object object, Object key) {
        if (object instanceof PythonObject) {
            PythonObject self = (PythonObject) object;
            if (isAttrWritable(exactBuiltinInstanceProfile, self, key) && (isHiddenKey(key) || !lib.hasDict(self))) {
                return false;
            }
            if (!isHiddenKey(key) && lib.hasDict(self)) {
                return false;
            }
        }
        if (object instanceof PythonAbstractNativeObject && !isHiddenKey(key)) {
            return false;
        }
        return true;
    }

    protected abstract static class WriteAttributeToObjectNotTypeNode extends WriteAttributeToObjectNode {
        @Specialization(guards = {"!isHiddenKey(key)"})
        static boolean writeNativeObject(PythonAbstractNativeObject object, Object key, Object value,
                        @Cached GetObjectDictNode getNativeDict,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached PRaiseNode raiseNode) {
            return writeNativeGeneric(object, key, value, getNativeDict.execute(object), setItemNode, raiseNode);
        }
    }

    @GenerateUncached
    protected abstract static class WriteAttributeToObjectNotTypeUncachedNode extends WriteAttributeToObjectNode {
        @Specialization(guards = {
                        "!isHiddenKey(key)",
                        "lib.hasDict(object)"
        }, replaces = {"writeToDict"}, limit = "1")
        protected boolean writeToDictUncached(PythonObject object, Object key, Object value,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Cached LookupInheritedAttributeNode.Dynamic getSetItem,
                        @Cached CallNode callSetItem,
                        @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile isClassProfile) {
            handlePythonClass(isClassProfile, object, key);
            PHashingCollection dict = lib.getDict(object);
            return writeToDictUncached(object, key, value, getSetItem, callSetItem, raiseNode, dict);
        }

        @Specialization(guards = {"!isHiddenKey(key)"})
        static boolean writeNativeObject(PythonAbstractNativeObject object, Object key, Object value,
                        @Cached GetObjectDictNode getNativeDict,
                        @Cached LookupInheritedAttributeNode.Dynamic getSetItem,
                        @Cached CallNode callSetItem,
                        @Cached PRaiseNode raiseNode) {
            Object nativeDict = getNativeDict.execute(object);
            return writeToDictUncached(object, key, value, getSetItem, callSetItem, raiseNode, nativeDict);
        }

        private static boolean writeToDictUncached(Object object, Object key, Object value, LookupInheritedAttributeNode.Dynamic getSetItem, CallNode callSetItem, PRaiseNode raiseNode,
                        Object dict) {
            Object setItemCallable = getSetItem.execute(dict, SpecialMethodNames.__SETITEM__);
            if (setItemCallable == PNone.NO_VALUE) {
                throw raiseNode.raise(PythonBuiltinClassType.AttributeError, "'%p' dict of '%p' object has no attribute '__setitem__'", dict, object);
            } else {
                callSetItem.execute(null, setItemCallable, object, key, value);
                return true;
            }
        }
    }

    protected abstract static class WriteAttributeToObjectTpDictNode extends WriteAttributeToObjectNode {
        @Specialization(guards = "!isHiddenKey(key)")
        static boolean writeNativeClass(PythonAbstractNativeObject object, Object key, Object value,
                        @Cached GetTypeMemberNode getNativeDict,
                        @Cached HashingCollectionNodes.SetItemNode setItemNode,
                        @Cached PRaiseNode raiseNode) {
            return writeNativeGeneric(object, key, value, getNativeDict.execute(object, NativeMemberNames.TP_DICT), setItemNode, raiseNode);
        }
    }

}
