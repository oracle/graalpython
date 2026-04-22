/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.attributes.MergedObjectTypeModuleGetFixedAttributeNode.hasNoGetAttr;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateInline
@GenerateCached
public abstract class GetFixedObjectAttributeNode extends PNodeWithContext {

    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object object, TruffleString key, Object type);

    /**
     * @see com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.GetAttributeNode
     */
    @Specialization
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object object, TruffleString key, Object type,
                    @Cached(value = "create(key)", inline = false) LookupAttributeInMRONode lookup,
                    @Cached GetObjectSlotsNode getDescrSlotsNode,
                    @Cached ReadAttributeFromObjectNode readAttributeOfObjectNode,
                    @Cached InlinedConditionProfile hasDescrProfile,
                    @Cached InlinedConditionProfile hasDescrGetProfile,
                    @Cached InlinedConditionProfile hasValueProfile,
                    @Cached CallSlotDescrGet.Lazy callSlotDescrGet,
                    @Cached PRaiseNode raiseNode) {
        assert hasNoGetAttr(type);

        Object descr = lookup.execute(type);
        boolean hasDescr = hasDescrProfile.profile(inliningTarget, descr != PNone.NO_VALUE);

        TpSlot get = null;
        boolean hasDescrGet = false;
        boolean getValue = true;
        if (hasDescr) {
            var descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
            get = descrSlots.tp_descr_get();
            hasDescrGet = hasDescrGetProfile.profile(inliningTarget, get != null);
            if (hasDescrGet && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                // fall through to callSlotDescrGet below to avoid duplicating the call site
                getValue = false;
            }
        }

        if (getValue) {
            Object value = readAttributeOfObjectNode.execute(object, key);
            if (hasValueProfile.profile(inliningTarget, value != PNone.NO_VALUE)) {
                return value;
            }
        }

        if (hasDescr) {
            if (hasDescrGet) {
                return callSlotDescrGet.get(inliningTarget).execute(frame, inliningTarget, get, descr, object, type);
            } else {
                return descr;
            }
        }

        throw raiseNode.raiseAttributeError(inliningTarget, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
    }
}
