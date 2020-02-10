/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.NativeMemberNames;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNodeGen.ReadAttributeFromObjectNotTypeNodeGen;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNodeGen.ReadAttributeFromObjectTpDictNodeGen;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@ImportStatic({PGuards.class, PythonOptions.class, NativeMemberNames.class})
public abstract class ReadAttributeFromObjectNode extends ObjectAttributeNode {
    public static ReadAttributeFromObjectNode create() {
        return ReadAttributeFromObjectNotTypeNodeGen.create();
    }

    public static ReadAttributeFromObjectNode createForceType() {
        return ReadAttributeFromObjectTpDictNodeGen.create();
    }

    public static ReadAttributeFromObjectNode getUncached() {
        return ReadAttributeFromObjectNotTypeNodeGen.getUncached();
    }

    public static ReadAttributeFromObjectNode getUncachedForceType() {
        return ReadAttributeFromObjectTpDictNodeGen.getUncached();
    }

    public abstract Object execute(Object object, Object key);

    // read from the DynamicObject store
    @Specialization(guards = {
                    "!lib.hasDict(object) || isHiddenKey(key)"
    }, limit = "1")
    protected Object readFromDynamicStorage(PythonObject object, Object key,
                    @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary lib,
                    @Cached("create()") ReadAttributeFromDynamicObjectNode readAttributeFromDynamicObjectNode) {
        return readAttributeFromDynamicObjectNode.execute(object.getStorage(), key);
    }

    protected static boolean isBuiltinDict(PHashingCollection globals, IsBuiltinClassProfile profile) {
        return globals instanceof PDict && profile.profileObject(globals, PythonBuiltinClassType.PDict);
    }

    /**
     * @param module Non-cached parameter to help the DSL produce a guard, not an assertion
     */
    protected static HashingStorage getStorage(Object module, Object cachedGlobals) {
        return ((PDict) cachedGlobals).getDictStorage();
    }

    // special case for the very common module attribute read from an unchanging PDict
    @Specialization(guards = {
                    "cachedObject == object",
                    "lib.getDict(cachedObject) == cachedDict",
                    "getStorage(object, cachedDict) == cachedStorage",
                    "isBuiltinDict(cachedDict, isBuiltinDict)",
    }, assumptions = "singleContextAssumption", limit = "1")
    protected Object readFromBuiltinModuleDictUnchanged(@SuppressWarnings("unused") PythonModule object, String key,
                    @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary lib,
                    @SuppressWarnings("unused") @Cached("object") PythonModule cachedObject,
                    @SuppressWarnings("unused") @Cached("lib.getDict(cachedObject)") PHashingCollection cachedDict,
                    @SuppressWarnings("unused") @Cached("singleContextAssumption()") Assumption singleContextAssumption,
                    @Cached("getStorage(object, cachedDict)") HashingStorage cachedStorage,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinDict,
                    @CachedLibrary(limit = "1") HashingStorageLibrary hlib) {
        // note that we don't need to pass the state here - string keys are hashable by definition
        Object value = hlib.getItem(cachedStorage, key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    // special case for the very common module attribute read
    @Specialization(guards = {
                    "cachedObject == object",
                    "lib.getDict(cachedObject) == cachedDict",
                    "hasBuiltinDict(cachedObject, lib, isBuiltinDict, isBuiltinMappingproxy)",
    }, assumptions = "singleContextAssumption", limit = "1", replaces = "readFromBuiltinModuleDictUnchanged")
    protected Object readFromBuiltinModuleDict(@SuppressWarnings("unused") PythonModule object, String key,
                    @SuppressWarnings("unused") @CachedLibrary("object") PythonObjectLibrary lib,
                    @SuppressWarnings("unused") @Cached("object") PythonModule cachedObject,
                    @SuppressWarnings("unused") @Cached("lib.getDict(cachedObject)") PHashingCollection cachedDict,
                    @SuppressWarnings("unused") @Cached("singleContextAssumption()") Assumption singleContextAssumption,
                    @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinDict,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinMappingproxy,
                    @CachedLibrary(limit = "1") HashingStorageLibrary hlib) {
        Object value = hlib.getItem(getDictStorage.execute(cachedDict), key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    protected HashingStorage getDictStorage(PythonObject object, PythonObjectLibrary lib) {
        return lib.getDict(object) != null ? lib.getDict(object).getDictStorage() : null;
    }

    // read from a builtin dict
    @Specialization(guards = {"!isHiddenKey(key)", "hasBuiltinDict(object, lib, isBuiltinDict, isBuiltinMappingproxy)"}, limit = "1")
    protected Object readFromBuiltinDict(PythonObject object, String key,
                    @CachedLibrary("object") PythonObjectLibrary lib,
                    @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinDict,
                    @SuppressWarnings("unused") @Cached IsBuiltinClassProfile isBuiltinMappingproxy,
                    @CachedLibrary("getDictStorage(object, lib)") HashingStorageLibrary hlib) {
        Object value = hlib.getItem(getDictStorage.execute(lib.getDict(object)), key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    // read from the Dict
    @Specialization(guards = {
                    "!isHiddenKey(key)",
                    "lib.hasDict(object)"
    }, replaces = {"readFromBuiltinDict", "readFromBuiltinModuleDict"}, limit = "1")
    protected Object readFromDict(PythonObject object, Object key,
                    @CachedLibrary("object") PythonObjectLibrary lib,
                    @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                    @Cached("create()") HashingStorageNodes.GetItemInteropNode getItemNode) {
        Object value = getItemNode.executeWithGlobalState(getDictStorage.execute(lib.getDict(object)), key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    // foreign Object
    @Specialization(guards = "isForeignObject(object)")
    protected Object readForeign(TruffleObject object, Object key,
                    @Cached PForeignToPTypeNode fromForeign,
                    @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") InteropLibrary read) {
        try {
            String member = (String) attrKey(key);
            if (read.isMemberReadable(object, member)) {
                return fromForeign.executeConvert(read.readMember(object, member));
            }
        } catch (UnknownIdentifierException | UnsupportedMessageException ignored) {
        }
        return PNone.NO_VALUE;
    }

    // not a Python or Foreign Object
    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPythonObject(object)", "!isNativeObject(object)", "!isForeignObject(object)"})
    protected PNone readUnboxed(Object object, Object key) {
        return PNone.NO_VALUE;
    }

    // native objects. We distinguish reading at the objects dictoffset or the tp_dict
    // these are also the two nodes that generate uncached versions, because they encode
    // the boolean flag forceType for the fallback in their type

    @GenerateUncached
    protected abstract static class ReadAttributeFromObjectNotTypeNode extends ReadAttributeFromObjectNode {
        @Specialization(guards = {"!isHiddenKey(key)"}, insertBefore = "readForeign", limit = "1")
        protected Object readNativeObject(PythonAbstractNativeObject object, Object key,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                        @Cached("create()") HashingStorageNodes.GetItemInteropNode getItemNode) {
            return readNative(key, lib.getDict(object), getItemNode, getDictStorage);
        }

        @Fallback
        protected Object fallback(Object object, Object key) {
            return readAttributeUncached(object, key, false);
        }
    }

    @GenerateUncached
    protected abstract static class ReadAttributeFromObjectTpDictNode extends ReadAttributeFromObjectNode {
        @Specialization(guards = {"!isHiddenKey(key)"}, insertBefore = "readForeign")
        protected Object readNativeClass(PythonNativeClass object, Object key,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorage,
                        @Cached GetTypeMemberNode getNativeDict,
                        @Cached("create()") HashingStorageNodes.GetItemInteropNode getItemNode) {
            return readNative(key, getNativeDict.execute(object, NativeMemberNames.TP_DICT), getItemNode, getDictStorage);
        }

        @Fallback
        protected Object fallback(Object object, Object key) {
            return readAttributeUncached(object, key, true);
        }
    }

    private static Object readNative(Object key, Object dict, HashingStorageNodes.GetItemInteropNode getItemNode, HashingCollectionNodes.GetDictStorageNode getDictStorage) {
        if (dict instanceof PHashingCollection) {
            Object result = getItemNode.executeWithGlobalState(getDictStorage.execute((PHashingCollection) dict), key);
            if (result != null) {
                return result;
            }
        }
        return PNone.NO_VALUE;
    }

    private static Object readAttributeUncached(Object object, Object key, boolean forceType) {
        if (object instanceof PythonObject) {
            PythonObject po = (PythonObject) object;
            if (!PythonObjectLibrary.getUncached().hasDict(po)) {
                return ReadAttributeFromDynamicObjectNode.getUncached().execute(po.getStorage(), key);
            } else {
                HashingStorage dictStorage = PythonObjectLibrary.getUncached().getDict(po).getDictStorage();
                HashingStorageLibrary lib = HashingStorageLibrary.getFactory().getUncached(dictStorage);
                Object value = lib.getItem(dictStorage, key);
                if (value == null) {
                    return PNone.NO_VALUE;
                } else {
                    return value;
                }
            }
        } else if (object instanceof PythonAbstractNativeObject) {
            Object d = null;
            if (forceType) {
                d = GetTypeMemberNode.getUncached().execute(object, NativeMemberNames.TP_DICT);
            } else {
                d = PythonObjectLibrary.getUncached().getDict(object);
            }
            Object value = null;
            if (d instanceof PHashingCollection) {
                HashingStorage dictStorage = ((PHashingCollection) d).getDictStorage();
                HashingStorageLibrary lib = HashingStorageLibrary.getFactory().getUncached(dictStorage);
                value = lib.getItem(dictStorage, key);
            }
            if (value == null) {
                return PNone.NO_VALUE;
            } else {
                return value;
            }

        }
        return PNone.NO_VALUE;
    }
}
