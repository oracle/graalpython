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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

@ImportStatic({PGuards.class, PythonOptions.class})
public abstract class ReadAttributeFromObjectNode extends PNodeWithContext {
    public static ReadAttributeFromObjectNode create() {
        return ReadAttributeFromObjectNodeGen.create();
    }

    public abstract Object execute(Object object, Object key);

    protected static Location getLocationOrNull(Property prop) {
        return prop == null ? null : prop.getLocation();
    }

    protected static boolean isNull(Object value) {
        return value == null;
    }

    protected static Object readFinalValue(PythonObject object, Location location) {
        Object value = location.get(object.getStorage());
        return value == null ? PNone.NO_VALUE : value;
    }

    /*
     * Includes "object" as a parameter so that Truffle DSL sees this as a dynamic check.
     */
    protected static boolean checkShape(@SuppressWarnings("unused") PythonObject object, PythonObject cachedObject, Shape cachedShape) {
        return cachedObject.getStorage().getShape() == cachedShape;
    }

    protected Object attrKey(Object key) {
        if (key instanceof PString) {
            return ((PString) key).getValue();
        } else {
            return key;
        }
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "1", //
                    guards = {
                                    "object == cachedObject",
                                    "checkShape(object, cachedObject, cachedShape)",
                                    "key == cachedKey",
                                    "!isNull(loc)",
                                    "loc.isAssumedFinal()",
                    }, //
                    assumptions = {
                                    "layoutAssumption",
                                    "finalAssumption"
                    })
    protected Object readDirectFinal(PythonObject object, Object key,
                    @Cached("object") PythonObject cachedObject,
                    @Cached("key") Object cachedKey,
                    @Cached("attrKey(key)") Object attrKey,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(attrKey))") Location loc,
                    @Cached("loc.getFinalAssumption()") Assumption finalAssumption,
                    @Cached("readFinalValue(object, loc)") Object cachedValue) {
        assert assertFinal(object, attrKey, cachedValue);
        return cachedValue;
    }

    private static boolean assertFinal(PythonObject object, Object key, Object cachedValue) {
        Object other = object.getStorage().get(key) == null ? PNone.NO_VALUE : object.getStorage().get(key);
        return cachedValue == other || cachedValue instanceof Number && other instanceof Number && ((Number) cachedValue).doubleValue() == ((Number) other).doubleValue();
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", //
                    guards = {
                                    "object.getStorage().getShape() == cachedShape",
                                    "key == cachedKey",
                                    "isNull(loc) || !loc.isAssumedFinal()",
                    }, //
                    assumptions = "layoutAssumption")
    protected Object readDirect(PythonObject object, Object key,
                    @Cached("key") Object cachedKey,
                    @Cached("attrKey(cachedKey)") Object attrKey,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(attrKey))") Location loc) {
        if (loc == null) {
            return PNone.NO_VALUE;
        } else {
            return loc.get(object.getStorage());
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "object.getStorage().getShape() == cachedShape",
                    "!layoutAssumption.isValid()"
    })
    protected Object updateShapeAndRead(PythonObject object, Object key,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("create()") ReadAttributeFromObjectNode nextNode) {
        CompilerDirectives.transferToInterpreter();
        object.getStorage().updateShape();
        return nextNode.execute(object, key);
    }

    @Specialization(replaces = "readDirect")
    protected Object readIndirect(PythonObject object, Object key) {
        Object value = object.getStorage().get(attrKey(key));
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    @Specialization(guards = "isForeignObject(object)")
    protected Object readForeign(TruffleObject object, Object key,
                    @Cached("create()") PForeignToPTypeNode fromForeign,
                    @Cached("createReadNode()") Node readNode) {
        try {
            return fromForeign.executeConvert(ForeignAccess.sendRead(readNode, object, attrKey(key)));
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            return PNone.NO_VALUE;
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isPythonObject(object)", "!isForeignObject(object)"})
    protected PNone readUnboxed(Object object, Object key) {
        return PNone.NO_VALUE;
    }

    protected Node createReadNode() {
        return Message.READ.createNode();
    }
}
