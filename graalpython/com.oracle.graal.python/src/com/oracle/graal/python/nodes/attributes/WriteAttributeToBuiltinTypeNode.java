/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Variant of {@link WriteAttributeToObjectNode} that allows to set attributes of builtin types
 * after initialization has finished. This node does not call
 * {@link PythonManagedClass#onAttributeUpdate(Object, Object)} and should be used with caution,
 * because it does not invalidate any assumptions or caches related to MRO lookups.
 */
@GenerateUncached
@GenerateInline(false) // footprint reduction 36 -> 17
public abstract class WriteAttributeToBuiltinTypeNode extends PNodeWithContext {

    public abstract void execute(Object primary, TruffleString key, Object value);

    protected static boolean isAttrWritable(PythonBuiltinClass self) {
        return (self.getShape().getFlags() & PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG) == 0;
    }

    @Specialization(guards = {"isAttrWritable(klass)", "getDict.execute(klass) == null"})
    static void writeToDynamicStorage(PythonBuiltinClass klass, TruffleString key, Object value,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") DynamicObjectLibrary dylib) {
        dylib.put(klass, key, value);
    }

    @Specialization(guards = "dict != null")
    static void writeToDictNoType(@SuppressWarnings("unused") PythonBuiltinClass klass, TruffleString key, Object value,
                    @Bind("this") Node inliningTarget,
                    @SuppressWarnings("unused") @Shared("getDict") @Cached GetDictIfExistsNode getDict,
                    @Bind("getDict.execute(klass)") PDict dict,
                    @Cached InlinedBranchProfile updateStorage,
                    @Cached HashingStorageSetItem setHashingStorageItem) {
        WriteAttributeToObjectNode.writeToDict(dict, key, value, inliningTarget, updateStorage, setHashingStorageItem);
    }

    @Specialization
    static void doPBCT(PythonBuiltinClassType object, TruffleString key, Object value,
                    @Cached WriteAttributeToBuiltinTypeNode recursive) {
        recursive.execute(PythonContext.get(recursive).lookupType(object), key, value);
    }
}
