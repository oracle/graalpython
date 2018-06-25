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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
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

@NodeChildren({@NodeChild(value = "klass", type = PNode.class), @NodeChild(value = "key", type = PNode.class)})
public abstract class ReadAttributeFromObjectNode extends PNode {
    public static ReadAttributeFromObjectNode create() {
        return ReadAttributeFromObjectNodeGen.create(null, null);
    }

    public abstract Object execute(Object object, Object key);

    protected Location getLocationOrNull(Property prop) {
        return prop == null ? null : prop.getLocation();
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "getIntOption(getContext(), AttributeAccessInlineCacheMaxDepth)", //
                    guards = {
                                    "object.getStorage().getShape() == cachedShape",
                                    "key == cachedKey"
                    }, //
                    assumptions = "layoutAssumption")
    protected Object readDirect(PythonObject object, Object key,
                    @Cached("key") Object cachedKey,
                    @Cached("object.getStorage().getShape()") Shape cachedShape,
                    @Cached("cachedShape.getValidAssumption()") Assumption layoutAssumption,
                    @Cached("cachedShape.getProperty(key)") Property prop,
                    @Cached("getLocationOrNull(prop)") Location loc) {
        if (prop == null) {
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
        Object value = object.getStorage().get(key);
        if (value == null) {
            return PNone.NO_VALUE;
        } else {
            return value;
        }
    }

    @Specialization(guards = "isForeignObject(object)")
    protected Object readForeign(TruffleObject object, Object key,
                    @Cached("createReadNode()") Node readNode) {
        try {
            return ForeignAccess.sendRead(readNode, object, key);
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
