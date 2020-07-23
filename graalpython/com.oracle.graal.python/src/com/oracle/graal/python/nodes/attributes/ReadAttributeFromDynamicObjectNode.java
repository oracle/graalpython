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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;

@ImportStatic({PGuards.class, PythonOptions.class})
@ReportPolymorphism
@GenerateUncached
public abstract class ReadAttributeFromDynamicObjectNode extends ObjectAttributeNode {
    public static ReadAttributeFromDynamicObjectNode create() {
        return ReadAttributeFromDynamicObjectNodeGen.create();
    }

    public static ReadAttributeFromDynamicObjectNode getUncached() {
        return ReadAttributeFromDynamicObjectNodeGen.getUncached();
    }

    public abstract Object execute(Object object, Object key);

    protected static Object getAttribute(DynamicObject object, String key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(object, key, PNone.NO_VALUE);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "1", //
                    guards = {"dynamicObject == cachedObject", "key == cachedKey"}, //
                    assumptions = {"singleContextAssumption()", "propertyAssumption"})
    protected Object readFinal(DynamicObject dynamicObject, String key,
                    @Cached("key") String cachedKey,
                    @Cached(value = "dynamicObject", weak = true) DynamicObject cachedObject,
                    @Cached("dynamicObject.getShape().getPropertyAssumption(key)") Assumption propertyAssumption,
                    @Cached(value = "getAttribute(dynamicObject, key)", weak = true) Object value) {
        return value;
    }

    @Specialization(guards = "key == cachedKey", limit = "getAttributeAccessInlineCacheMaxDepth()", replaces = "readFinal")
    protected Object readDirect(DynamicObject dynamicObject, @SuppressWarnings("unused") String key,
                    @Cached("key") String cachedKey,
                    @CachedLibrary("dynamicObject") DynamicObjectLibrary dylib) {
        return dylib.getOrDefault(dynamicObject, cachedKey, PNone.NO_VALUE);
    }

    @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", replaces = {"readDirect", "readFinal"})
    protected Object read(DynamicObject dynamicObject, Object key,
                    @Cached CastToJavaStringNode castNode,
                    @CachedLibrary("dynamicObject") DynamicObjectLibrary dylib) {
        return dylib.getOrDefault(dynamicObject, attrKey(key, castNode), PNone.NO_VALUE);
    }
}
