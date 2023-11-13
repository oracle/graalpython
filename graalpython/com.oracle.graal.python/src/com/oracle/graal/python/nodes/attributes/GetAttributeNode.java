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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.CallSlotGetAttrNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.GetAttributeNodeFactory.GetFixedAttributeNodeGen;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class GetAttributeNode extends PNodeWithContext {

    @Child private GetFixedAttributeNode getFixedAttributeNode;

    public Object executeObject(VirtualFrame frame, Object object) {
        return getFixedAttributeNode.executeObject(frame, object);
    }

    protected GetAttributeNode(TruffleString key) {
        getFixedAttributeNode = GetFixedAttributeNode.create(key);
    }

    @NeverDefault
    public static GetAttributeNode create(TruffleString key) {
        return new GetAttributeNode(key);
    }

    @SuppressWarnings("truffle-static-method")
    public abstract static class GetFixedAttributeNode extends PNodeWithContext {
        private final TruffleString key;
        @Child private GetObjectSlotsNode getSlotsNode = GetObjectSlotsNode.create();

        public GetFixedAttributeNode(TruffleString key) {
            this.key = key;
        }

        public final TruffleString getKey() {
            return key;
        }

        public final Object executeObject(VirtualFrame frame, Object object) {
            return execute(frame, object);
        }

        public final Object execute(VirtualFrame frame, Object object) {
            return executeImpl(frame, object, getSlotsNode.executeCached(object));
        }

        abstract Object executeImpl(VirtualFrame frame, Object object, TpSlots slots);

        protected static boolean hasNoGetAttr(Object obj) {
            // only used in asserts
            return LookupAttributeInMRONode.Dynamic.getUncached().execute(GetClassNode.executeUncached(obj), T___GETATTR__) == PNone.NO_VALUE;
        }

        protected static boolean isObjectGetAttribute(TpSlots slots) {
            return slots.tp_get_attro() == ObjectBuiltins.SLOTS.tp_get_attro();
        }

        protected static boolean isModuleGetAttribute(TpSlots slots) {
            return slots.tp_get_attro() == ModuleBuiltins.SLOTS.tp_get_attro();
        }

        protected static boolean isTypeGetAttribute(TpSlots slots) {
            return slots.tp_get_attro() == TypeBuiltins.SLOTS.tp_get_attro();
        }

        @Specialization(guards = "isObjectGetAttribute(slots)")
        final Object doBuiltinObject(VirtualFrame frame, Object object, @SuppressWarnings("unused") TpSlots slots,
                        @Cached ObjectBuiltins.GetAttributeNode getAttributeNode) {
            assert hasNoGetAttr(object);
            return getAttributeNode.execute(frame, object, key);
        }

        @Specialization(guards = "isTypeGetAttribute(slots)")
        final Object doBuiltinType(VirtualFrame frame, Object object, @SuppressWarnings("unused") TpSlots slots,
                        @Cached TypeBuiltins.GetattributeNode getAttributeNode) {
            assert hasNoGetAttr(object);
            return getAttributeNode.execute(frame, object, key);
        }

        @Specialization(guards = "isModuleGetAttribute(slots)")
        final Object doBuiltinModule(VirtualFrame frame, Object object, @SuppressWarnings("unused") TpSlots slots,
                        @Cached ModuleBuiltins.ModuleGetattritbuteNode getAttributeNode) {
            assert hasNoGetAttr(object);
            return getAttributeNode.execute(frame, object, key);
        }

        @Specialization(replaces = {"doBuiltinObject", "doBuiltinType", "doBuiltinModule"})
        final Object doGeneric(VirtualFrame frame, Object object, TpSlots slots,
                        @Bind("this") Node inliningTarget,
                        @Cached CallSlotGetAttrNode callGetAttrNode) {
            return callGetAttrNode.execute(frame, inliningTarget, slots, object, key);
        }

        @NeverDefault
        public static GetFixedAttributeNode create(TruffleString key) {
            return GetFixedAttributeNodeGen.create(key);
        }
    }
}
