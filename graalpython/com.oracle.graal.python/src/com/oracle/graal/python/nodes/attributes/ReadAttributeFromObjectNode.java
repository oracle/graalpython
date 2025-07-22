/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_dict;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
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
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic({PGuards.class, PythonOptions.class})
@ReportPolymorphism
@GenerateInline(false) // footprint reduction 64 -> 47
public abstract class ReadAttributeFromObjectNode extends PNodeWithContext {
    @NeverDefault
    public static ReadAttributeFromObjectNode create() {
        return ReadAttributeFromObjectNotTypeNodeGen.create();
    }

    @NeverDefault
    public static ReadAttributeFromObjectNode createForceType() {
        return ReadAttributeFromObjectTpDictNodeGen.create();
    }

    public static ReadAttributeFromObjectNode getUncached() {
        return ReadAttributeFromObjectNotTypeNodeGen.getUncached();
    }

    public static ReadAttributeFromObjectNode getUncachedForceType() {
        return ReadAttributeFromObjectTpDictNodeGen.getUncached();
    }

    public abstract Object execute(Object object, TruffleString key);

    public abstract Object execute(PythonModule object, TruffleString key);

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
                    @Bind Node inliningTarget,
                    @Cached(value = "object", weak = true) PythonModule cachedObject,
                    @Cached(value = "getDict(object)", weak = true) PHashingCollection cachedDict,
                    @Cached(value = "getStorage(object, getDict(object))", weak = true) HashingStorage cachedStorage,
                    @Exclusive @Cached HashingStorageGetItem getItem) {
        // note that we don't need to pass the state here - string keys are hashable by definition
        Object value = getItem.execute(inliningTarget, cachedStorage, key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    // any python object attribute read
    @Specialization
    protected static Object readObjectAttribute(PythonObject object, TruffleString key,
                    @Bind Node inliningTarget,
                    @Cached InlinedConditionProfile profileHasDict,
                    @Exclusive @Cached GetDictIfExistsNode getDict,
                    @Cached ReadAttributeFromPythonObjectNode readAttributeFromPythonObjectNode,
                    @Exclusive @Cached HashingStorageGetItem getItem) {
        var dict = getDict.execute(object);
        if (profileHasDict.profile(inliningTarget, dict == null)) {
            return readAttributeFromPythonObjectNode.execute(object, key);
        } else {
            // Note: we should pass the frame. In theory a subclass of a string may override
            // __hash__ or __eq__ and run some side effects in there.
            Object value = getItem.execute(null, inliningTarget, dict.getDictStorage(), key);
            if (value == null) {
                return PNone.NO_VALUE;
            } else {
                return value;
            }
        }
    }

    // foreign object or primitive
    @InliningCutoff
    @Specialization(guards = {"!isPythonObject(object)", "!isNativeObject(object)"})
    protected static Object readForeignOrPrimitive(Object object, TruffleString key) {
        // Foreign members are tried after the regular attribute lookup, see
        // ForeignObjectBuiltins.GetAttributeNode. If we looked them up here
        // they would get precedence over attributes in the MRO.
        return PNone.NO_VALUE;
    }

    // native objects. We distinguish reading at the objects dictoffset or the tp_dict
    // these are also the two nodes that generate uncached versions, because they encode
    // the boolean flag forceType for the fallback in their type

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 64 -> 47
    protected abstract static class ReadAttributeFromObjectNotTypeNode extends ReadAttributeFromObjectNode {
        @Specialization(insertBefore = "readForeignOrPrimitive")
        protected static Object readNativeObject(PythonAbstractNativeObject object, TruffleString key,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Exclusive @Cached HashingStorageGetItem getItem) {
            return readNative(inliningTarget, key, getDict.execute(object), getItem);
        }
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 68 -> 51
    protected abstract static class ReadAttributeFromObjectTpDictNode extends ReadAttributeFromObjectNode {
        @Specialization(insertBefore = "readForeignOrPrimitive")
        protected static Object readNativeClass(PythonAbstractNativeObject object, TruffleString key,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode getNativeDict,
                        @Exclusive @Cached HashingStorageGetItem getItem) {
            return readNative(inliningTarget, key, getNativeDict.readFromObj(object, PyTypeObject__tp_dict), getItem);
        }
    }

    private static Object readNative(Node inliningTarget, TruffleString key, Object dict, HashingStorageGetItem getItem) {
        if (dict instanceof PHashingCollection) {
            Object result = getItem.execute(null, inliningTarget, ((PHashingCollection) dict).getDictStorage(), key);
            if (result != null) {
                return result;
            }
        }
        return PNone.NO_VALUE;
    }

}
