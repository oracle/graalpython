/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.CallSlotGetAttrNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent PyObject_GetAttr*. Like Python, this method raises when the attribute doesn't exist.
 *
 * @see PyObjectGetAttrO
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectGetAttr extends Node {
    public static Object executeUncached(Object receiver, TruffleString name) {
        return PyObjectGetAttr.getUncached().execute(null, null, receiver, name);
    }

    public final Object executeCached(Frame frame, Object receiver, TruffleString name) {
        return execute(frame, this, receiver, name);
    }

    public abstract Object execute(Frame frame, Node inliningTarget, Object receiver, TruffleString name);

    public final Object execute(Node inliningTarget, Object receiver, TruffleString name) {
        return execute(null, inliningTarget, receiver, name);
    }

    @Specialization(guards = "name == cachedName", limit = "1")
    static Object getFixedAttr(VirtualFrame frame, Object receiver, @SuppressWarnings("unused") TruffleString name,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Cached("create(name)") GetFixedAttributeNode getAttrNode) {
        return getAttrNode.execute(frame, receiver);
    }

    @InliningCutoff
    @Specialization(replaces = "getFixedAttr")
    static Object getDynamicAttr(Frame frame, Node inliningTarget, Object receiver, TruffleString name,
                    @Cached GetClassNode getClass,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Cached CallSlotGetAttrNode callGetAttrNode,
                    @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                    @Cached(inline = false) TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        Object type = getClass.execute(inliningTarget, receiver);
        var slots = getSlotsNode.execute(inliningTarget, receiver);
        if (!codePointLengthNode.isAdoptable()) {
            // It pays to try this in the uncached case, avoiding a full call to __getattribute__
            Object result = PyObjectLookupAttr.readAttributeQuickly(type, slots, receiver, name, codePointLengthNode, codePointAtIndexNode);
            if (result != null) {
                if (result == PNone.NO_VALUE) {
                    throw PRaiseNode.getUncached().raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, receiver, name);
                }
                return result;
            }
        }
        return callGetAttrNode.execute((VirtualFrame) frame, inliningTarget, slots, receiver, name);
    }

    @NeverDefault
    public static PyObjectGetAttr create() {
        return PyObjectGetAttrNodeGen.create();
    }

    public static PyObjectGetAttr getUncached() {
        return PyObjectGetAttrNodeGen.getUncached();
    }
}
