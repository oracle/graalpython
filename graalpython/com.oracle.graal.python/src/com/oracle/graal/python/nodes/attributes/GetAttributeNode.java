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

import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.Message;

@ImportStatic(Message.class)
@NodeChildren({@NodeChild(value = "object", type = PNode.class), @NodeChild(value = "key", type = PNode.class)})
public abstract class GetAttributeNode extends PNode implements ReadNode {

    public PNode makeWriteNode(PNode rhs) {
        return SetAttributeNode.create(getObject(), getKey(), rhs);
    }

    public static GetAttributeNode create() {
        return create(null, null);
    }

    public static GetAttributeNode create(PNode object, PNode key) {
        return GetAttributeNodeGen.create(object, key);
    }

    public abstract PNode getObject();

    public abstract PNode getKey();

    public abstract Object execute(Object object, Object key);

    public abstract Object executeWithObject(Object object);

    public Object executeWithKey(VirtualFrame frame, Object key) {
        return execute(getObject().execute(frame), key);
    }

    @Specialization
    protected Object doIt(PKeyword[] keywords, Object key,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode dispatchNode) {
        return dispatchNode.executeObject(factory().createDict(keywords), key);
    }

    @Specialization
    protected Object doIt(Object object, Object key,
                    @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode dispatchNode) {
        return dispatchNode.executeObject(object, key);
    }
}
