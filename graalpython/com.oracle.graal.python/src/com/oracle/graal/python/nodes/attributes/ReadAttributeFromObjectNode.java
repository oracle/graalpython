/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNodeGen.ReadAttributeFromObjectNotTypeNodeGen;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNodeGen.ReadAttributeFromObjectTpDictNodeGen;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic({PGuards.class, PythonOptions.class})
@ReportPolymorphism
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

    static final int MAX_DICT_TYPES = 2;

    // read from the DynamicObject store
    @Specialization(guards = "isHiddenKey(key)")
    protected static Object readFromDynamicStorageHidden(PythonObject object, Object key,
                    @Shared("readDynamic") @Cached ReadAttributeFromDynamicObjectNode readAttributeFromDynamicObjectNode) {
        return readAttributeFromDynamicObjectNode.execute(object.getStorage(), key);
    }

    @Specialization(guards = "getDict.execute(object) == null", limit = "1")
    protected static Object readFromDynamicStorage(PythonObject object, Object key,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Shared("readDynamic") @Cached ReadAttributeFromDynamicObjectNode readAttributeFromDynamicObjectNode) {
        return readAttributeFromDynamicObjectNode.execute(object.getStorage(), key);
    }

    /**
     * @param module Non-cached parameter to help the DSL produce a guard, not an assertion
     */
    protected static HashingStorage getStorage(Object module, PHashingCollection cachedGlobals) {
        return cachedGlobals.getDictStorage();
    }

    protected static PDict getDict(Object object) {
        return GetDictIfExistsNode.getUncached().execute(object);
    }

    // special case for the very common module read
    @Specialization(guards = {"isSingleContext()",
                    "cachedObject == object",
                    // no need to check the cachedDict for equality, module.__dict__ is read-only
                    "getStorage(object, cachedDict) == cachedStorage"
    }, limit = "1")
    @SuppressWarnings("unused")
    protected static Object readFromBuiltinModuleDict(PythonModule object, TruffleString key,
                    @Cached(value = "object", weak = true) PythonModule cachedObject,
                    @Cached(value = "getDict(object)", weak = true) PHashingCollection cachedDict,
                    @Cached(value = "getStorage(object, getDict(object))", weak = true) HashingStorage cachedStorage,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem) {
        // note that we don't need to pass the state here - string keys are hashable by definition
        Object value = getItem.execute(cachedStorage, key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    // read from the Dict
    @Specialization(guards = {
                    "!isHiddenKey(key)",
                    "dict != null"
    }, replaces = "readFromBuiltinModuleDict", limit = "1")
    protected static Object readFromDict(@SuppressWarnings("unused") PythonObject object, Object key,
                    @Shared("getDict") @SuppressWarnings("unused") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(object)") PDict dict,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem) {
        if (dict == null) {
            return PNone.NO_VALUE;
        }
        // Note: we should pass the frame. In theory a subclass of a string may override __hash__ or
        // __eq__ and run some side effects in there.
        Object value = getItem.execute(null, dict.getDictStorage(), key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    // foreign Object
    @Specialization(guards = "isForeignObjectNode.execute(object)", limit = "1")
    protected static Object readForeign(Object object, Object key,
                    @SuppressWarnings("unused") @Shared("isForeign") @Cached IsForeignObjectNode isForeignObjectNode,
                    @Cached ReadAttributeFromForeign read) {
        return read.execute(object, key);
    }

    // not a Python or Foreign Object
    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPythonObject(object)", "!isNativeObject(object)", "!isForeignObjectNode.execute(object)"}, limit = "1")
    protected static PNone readUnboxed(Object object, Object key,
                    @SuppressWarnings("unused") @Shared("isForeign") @Cached IsForeignObjectNode isForeignObjectNode
    // We want to share "hlib" with subclasses, this is to make Truffle shut up
    // about not being able to share it in the base class
    ) {
        return PNone.NO_VALUE;
    }

    // native objects. We distinguish reading at the objects dictoffset or the tp_dict
    // these are also the two nodes that generate uncached versions, because they encode
    // the boolean flag forceType for the fallback in their type

    @GenerateUncached
    protected abstract static class ReadAttributeFromObjectNotTypeNode extends ReadAttributeFromObjectNode {
        @Specialization(insertBefore = "readForeign")
        protected static Object readNativeObject(PythonAbstractNativeObject object, Object key,
                        @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                        @Shared("getItem") @Cached HashingStorageGetItem getItem) {
            return readNative(key, getDict.execute(object), getItem);
        }
    }

    @GenerateUncached
    protected abstract static class ReadAttributeFromObjectTpDictNode extends ReadAttributeFromObjectNode {
        @Specialization(insertBefore = "readForeign")
        protected static Object readNativeClass(PythonAbstractNativeObject object, Object key,
                        @Cached GetTypeMemberNode getNativeDict,
                        @Shared("getItem") @Cached HashingStorageGetItem getItem) {
            return readNative(key, getNativeDict.execute(object, NativeMember.TP_DICT), getItem);
        }
    }

    private static Object readNative(Object key, Object dict, HashingStorageGetItem getItem) {
        if (dict instanceof PHashingCollection) {
            Object result = getItem.execute(null, ((PHashingCollection) dict).getDictStorage(), key);
            if (result != null) {
                return result;
            }
        }
        return PNone.NO_VALUE;
    }

    @ImportStatic(PythonOptions.class)
    @GenerateUncached
    protected abstract static class ReadAttributeFromForeign extends PNodeWithContext {
        public abstract Object execute(Object object, Object key);

        @Specialization
        static Object read(Object object, Object key,
                        @Cached CastToJavaStringNode castNode,
                        @Cached PForeignToPTypeNode fromForeign,
                        @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") InteropLibrary read) {
            try {
                String member = castNode.execute(key);
                if (read.isMemberReadable(object, member)) {
                    return fromForeign.executeConvert(read.readMember(object, member));
                }
            } catch (CannotCastException | UnknownIdentifierException | UnsupportedMessageException ignored) {
            }
            return PNone.NO_VALUE;
        }
    }
}
