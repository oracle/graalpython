/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.ManagedPythonClass;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic({PythonOptions.class, NativeMemberNames.class})
public abstract class WriteAttributeToObjectNode extends ObjectAttributeNode {

    private final ConditionProfile isClassProfile = ConditionProfile.createBinaryProfile();
    private final IsBuiltinClassProfile exactBuiltinInstanceProfile = IsBuiltinClassProfile.create();

    protected final boolean forceType;

    public WriteAttributeToObjectNode(boolean forceType) {
        this.forceType = forceType;
    }

    public abstract boolean execute(Object primary, Object key, Object value);

    public abstract boolean execute(Object primary, String key, Object value);

    public static WriteAttributeToObjectNode create() {
        return WriteAttributeToObjectNodeGen.create(false);
    }

    public static WriteAttributeToObjectNode createForceType() {
        return WriteAttributeToObjectNodeGen.create(true);
    }

    protected boolean isAttrWritable(PythonObject self, Object key) {
        if (isHiddenKey(key) || self instanceof ManagedPythonClass || self instanceof PFunction || self instanceof PMethod || self instanceof PythonModule || self instanceof PBaseException) {
            return true;
        }
        return !exactBuiltinInstanceProfile.profileIsAnyBuiltinObject(self);
    }

    private void handlePythonClass(PythonObject object, Object key) {
        if (isClassProfile.profile(object instanceof ManagedPythonClass)) {
            if (key instanceof String) {
                ((ManagedPythonClass) object).invalidateAttributeInMROFinalAssumptions((String) key);
            }
        }
    }

    // write to the DynamicObject
    @Specialization(guards = {
                    "object == cachedObject",
                    "isAttrWritable(object, key)"
    }, assumptions = {
                    "singleContextAssumption",
                    "dictUnsetOrSameAsStorageAssumption"
    })
    protected boolean writeToDynamicStorageCached(PythonObject object, Object key, Object value,
                    @SuppressWarnings("unused") @Cached("object") PythonObject cachedObject,
                    @SuppressWarnings("unused") @Cached("singleContextAssumption()") Assumption singleContextAssumption,
                    @SuppressWarnings("unused") @Cached("cachedObject.getDictUnsetOrSameAsStorageAssumption()") Assumption dictUnsetOrSameAsStorageAssumption,
                    @Cached("create()") WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode) {
        handlePythonClass(object, key);
        return writeAttributeToDynamicObjectNode.execute(object.getStorage(), key, value);
    }

    @Specialization(guards = {
                    "isAttrWritable(object, key)",
                    "isHiddenKey(key) || isDictUnsetOrSameAsStorage(object)"
    }, replaces = "writeToDynamicStorageCached")
    protected boolean writeToDynamicStorage(PythonObject object, Object key, Object value,
                    @Cached("create()") WriteAttributeToDynamicObjectNode writeAttributeToDynamicObjectNode) {
        handlePythonClass(object, key);
        return writeAttributeToDynamicObjectNode.execute(object.getStorage(), key, value);
    }

    // write to the dict
    @Specialization(guards = {
                    "object == cachedObject",
                    "!isHiddenKey(key)",
                    "!dictUnsetOrSameAsStorageAssumption.isValid()",
    }, assumptions = {
                    "singleContextAssumption"
    })
    protected boolean writeToDictCached(PythonObject object, Object key, Object value,
                    @SuppressWarnings("unused") @Cached("object") PythonObject cachedObject,
                    @SuppressWarnings("unused") @Cached("singleContextAssumption()") Assumption singleContextAssumption,
                    @SuppressWarnings("unused") @Cached("cachedObject.getDictUnsetOrSameAsStorageAssumption()") Assumption dictUnsetOrSameAsStorageAssumption,
                    @Cached("create()") BranchProfile updateStorage,
                    @Cached("create()") HashingStorageNodes.SetItemNode setItemNode) {
        handlePythonClass(object, key);
        PHashingCollection dict = object.getDict();
        HashingStorage dictStorage = getDictStorage(dict);
        HashingStorage hashingStorage = setItemNode.execute(dictStorage, key, value);
        if (dictStorage != hashingStorage) {
            updateStorage.enter();
            dict.setDictStorage(hashingStorage);
        }
        return true;
    }

    @Specialization(guards = {
                    "!isHiddenKey(key)",
                    "!isDictUnsetOrSameAsStorage(object)"
    }, replaces = "writeToDictCached")
    protected boolean writeToDict(PythonObject object, Object key, Object value,
                    @Cached("create()") BranchProfile updateStorage,
                    @Cached("create()") HashingStorageNodes.SetItemNode setItemNode) {
        handlePythonClass(object, key);
        PHashingCollection dict = object.getDict();
        HashingStorage dictStorage = getDictStorage(dict);
        HashingStorage hashingStorage = setItemNode.execute(dictStorage, key, value);
        if (dictStorage != hashingStorage) {
            updateStorage.enter();
            dict.setDictStorage(hashingStorage);
        }
        return true;
    }

    @Specialization(guards = {"!forceType", "!isHiddenKey(key)"})
    protected boolean writeNativeObject(PythonAbstractNativeObject object, Object key, Object value,
                    @Cached("create()") GetObjectDictNode getNativeDict,
                    @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode) {
        return writeNativeGeneric(object, key, value, getNativeDict.execute(object), setItemNode);
    }

    @Specialization(guards = {"forceType", "!isHiddenKey(key)"})
    protected boolean writeNativeClass(PythonAbstractNativeObject object, Object key, Object value,
                    @Cached("create(TP_DICT)") GetTypeMemberNode getNativeDict,
                    @Cached("create()") HashingCollectionNodes.SetItemNode setItemNode) {
        return writeNativeGeneric(object, key, value, getNativeDict.execute(object), setItemNode);
    }

    private boolean writeNativeGeneric(PythonAbstractNativeObject object, Object key, Object value, Object d, HashingCollectionNodes.SetItemNode setItemNode) {
        if (d instanceof PHashingCollection) {
            setItemNode.execute(((PHashingCollection) d), key, value);
            return true;
        } else {
            return raise(object, key, value);
        }
    }

    @Fallback
    protected boolean raise(Object object, Object key, @SuppressWarnings("unused") Object value) {
        throw raise(PythonBuiltinClassType.AttributeError, "'%p' object has no attribute '%s'", object, key);
    }
}
