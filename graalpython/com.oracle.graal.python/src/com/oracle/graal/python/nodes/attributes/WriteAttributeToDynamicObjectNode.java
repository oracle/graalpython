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

import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;

@ImportStatic(PythonOptions.class)
@ReportPolymorphism
@GenerateUncached
public abstract class WriteAttributeToDynamicObjectNode extends ObjectAttributeNode {

    public abstract boolean execute(Object primary, Object key, Object value);

    public abstract boolean execute(Object primary, String key, Object value);

    public static WriteAttributeToDynamicObjectNode create() {
        return WriteAttributeToDynamicObjectNodeGen.create();
    }

    public static WriteAttributeToDynamicObjectNode getUncached() {
        return WriteAttributeToDynamicObjectNodeGen.getUncached();
    }

    protected static boolean compareKey(Object cachedKey, Object key) {
        return cachedKey == key;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "dynamicObject.getShape() == cachedShape",
                    "!layoutAssumption.isValid()"
    })
    protected boolean updateShapeAndWrite(DynamicObject dynamicObject, Object key, Object value,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("create()") WriteAttributeToDynamicObjectNode nextNode) {
        dynamicObject.updateShape();
        return nextNode.execute(dynamicObject, key, value);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    guards = {
                                    "dynamicObject.getShape() == cachedShape",
                                    "compareKey(cachedKey, key)",
                                    "loc != null",
                                    "loc.canSet(value)"
                    }, //
                    assumptions = {
                                    "layoutAssumption"

                    })
    protected boolean doDirect(DynamicObject dynamicObject, Object key, Object value,
                    @Cached("key") Object cachedKey,
                    @Cached("attrKey(cachedKey)") Object attrKey,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(attrKey))") Location loc) {
        try {
            loc.set(dynamicObject, value);
        } catch (IncompatibleLocationException | FinalLocationException e) {
            CompilerDirectives.transferToInterpreter();
            // cannot happen due to guard
            throw new RuntimeException("Location.canSet is inconsistent with Location.set");
        }
        return true;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getAttributeAccessInlineCacheMaxDepth()", //
                    guards = {
                                    "dynamicObject.getShape() == cachedShape",
                                    "compareKey(cachedKey, key)",
                                    "loc == null || !loc.canSet(value)",
                                    "newLoc.canSet(value)"
                    }, //
                    assumptions = {
                                    "layoutAssumption",
                                    "newLayoutAssumption"
                    })
    protected boolean defineDirect(DynamicObject dynamicObject, Object key, Object value,
                    @Cached("key") Object cachedKey,
                    @Cached("attrKey(key)") Object attrKey,
                    @Cached("dynamicObject.getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(attrKey))") Location loc,
                    @Cached("cachedShape.defineProperty(attrKey, value, 0)") Shape newShape,
                    @Cached("newShape.getValidAssumption()") Assumption newLayoutAssumption,
                    @Cached("getLocationOrNull(newShape.getProperty(attrKey))") Location newLoc) {
        try {
            newLoc.set(dynamicObject, value, cachedShape, newShape);
        } catch (IncompatibleLocationException e) {
            CompilerDirectives.transferToInterpreter();
            // cannot happen due to guard
            throw new RuntimeException("Location.canSet is inconsistent with Location.set");
        }
        return true;
    }

    @TruffleBoundary
    @Specialization(replaces = {"doDirect", "defineDirect", "updateShapeAndWrite"})
    protected static boolean doIndirect(DynamicObject dynamicObject, Object key, Object value) {
        if (!dynamicObject.getShape().isValid()) {
            dynamicObject.updateShape();
        }
        assert dynamicObject.getShape().isValid();
        Object attrKey = attrKey(key);
        dynamicObject.define(attrKey, value);
        return true;
    }
}
