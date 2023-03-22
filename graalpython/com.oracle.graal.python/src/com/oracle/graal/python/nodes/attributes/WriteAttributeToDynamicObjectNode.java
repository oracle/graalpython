/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Writes attribute directly to the underlying {@link DynamicObject} regardless of whether the
 * object has dict, also bypasses any other additional logic in
 * {@link WriteAttributeToDynamicObjectNode}. The only functionality this node provides on top of
 * {@link DynamicObjectLibrary} is casting of the key to {@code TruffleString}.
 */
@ImportStatic(PythonOptions.class)
@GenerateUncached
@GenerateInline(false) // Should be reconsidered during anticipated refactoring from DOM library
public abstract class WriteAttributeToDynamicObjectNode extends ObjectAttributeNode {

    public abstract boolean execute(Object primary, HiddenKey key, Object value);

    public abstract boolean execute(Object primary, TruffleString key, Object value);

    public abstract boolean execute(Object primary, Object key, Object value);

    @NeverDefault
    public static WriteAttributeToDynamicObjectNode create() {
        return WriteAttributeToDynamicObjectNodeGen.create();
    }

    public static WriteAttributeToDynamicObjectNode getUncached() {
        return WriteAttributeToDynamicObjectNodeGen.getUncached();
    }

    @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()")
    static boolean writeDirect(DynamicObject dynamicObject, TruffleString key, Object value,
                    @CachedLibrary("dynamicObject") DynamicObjectLibrary dylib) {
        dylib.put(dynamicObject, key, value);
        return true;
    }

    @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()")
    static boolean writeDirectHidden(DynamicObject dynamicObject, HiddenKey key, Object value,
                    @CachedLibrary("dynamicObject") DynamicObjectLibrary dylib) {
        dylib.put(dynamicObject, key, value);
        return true;
    }

    @Specialization(guards = "!isHiddenKey(key)", replaces = "writeDirect", limit = "getAttributeAccessInlineCacheMaxDepth()")
    static boolean write(DynamicObject dynamicObject, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @Cached CastToTruffleStringNode castNode,
                    @CachedLibrary("dynamicObject") DynamicObjectLibrary dylib) {
        dylib.put(dynamicObject, attrKey(inliningTarget, key, castNode), value);
        return true;
    }
}
