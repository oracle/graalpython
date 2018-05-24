/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

@NodeChildren({@NodeChild(value = "object", type = PNode.class), @NodeChild(value = "key", type = PNode.class), @NodeChild(value = "value", type = PNode.class)})
public abstract class WriteAttributeToObjectNode extends PNode {
    public abstract boolean execute(Object primary, Object key, Object value);

    public static WriteAttributeToObjectNode create() {
        return WriteAttributeToObjectNodeGen.create(null, null, null);
    }

    protected Location getLocationOrNull(Property prop) {
        return prop == null ? null : prop.getLocation();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "object.getStorage().getShape() == cachedShape",
                    "!layoutAssumption.isValid()"
    })
    protected Object updateShapeAndWrite(PythonObject object, Object key, Object value,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("create()") WriteAttributeToObjectNode nextNode) {
        object.getStorage().updateShape();
        return nextNode.execute(object, key, value);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", //
                    guards = {
                                    "object.getStorage().getShape() == cachedShape",
                                    "cachedKey.equals(key)",
                                    "loc != null",
                                    "loc.canSet(value)"
                    }, //
                    assumptions = {
                                    "layoutAssumption"

                    })
    protected boolean doDirect(PythonObject object, Object key, Object value,
                    @Cached("key") Object cachedKey,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(key))") Location loc) {
        try {
            loc.set(object.getStorage(), value);
        } catch (IncompatibleLocationException | FinalLocationException e) {
            CompilerDirectives.transferToInterpreter();
            // cannot happen due to guard
            throw new RuntimeException("Location.canSet is inconsistent with Location.set");
        }
        return true;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", //
                    guards = {
                                    "object.getStorage().getShape() == cachedShape",
                                    "cachedKey.equals(key)",
                                    "loc == null || !loc.canSet(value)",
                                    "newLoc.canSet(value)"
                    }, //
                    assumptions = {
                                    "layoutAssumption",
                                    "newLayoutAssumption"
                    })
    protected boolean defineDirect(PythonObject object, Object key, Object value,
                    @Cached("key") Object cachedKey,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("getLocationOrNull(cachedShape.getProperty(key))") Location loc,
                    @Cached("cachedShape.defineProperty(key, value, 0)") Shape newShape,
                    @Cached("newShape.getValidAssumption()") Assumption newLayoutAssumption,
                    @Cached("getLocationOrNull(newShape.getProperty(key))") Location newLoc) {
        try {
            newLoc.set(object.getStorage(), value, cachedShape, newShape);
        } catch (IncompatibleLocationException e) {
            CompilerDirectives.transferToInterpreter();
            // cannot happen due to guard
            throw new RuntimeException("Location.canSet is inconsistent with Location.set");
        }
        return true;
    }

    @TruffleBoundary
    @Specialization(replaces = {"doDirect", "defineDirect"}, guards = {"object.getStorage().getShape().isValid()"})
    protected boolean doIndirect(PythonObject object, Object key, Object value) {
        object.setAttribute(key, value);
        return true;
    }

    @TruffleBoundary
    @Specialization(guards = "!object.getStorage().getShape().isValid()")
    protected boolean defineDirect(PythonObject object, Object key, Object value) {
        object.getStorage().updateShape();
        return doIndirect(object, key, value);
    }
}
