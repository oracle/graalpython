/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.exception.AttributeErrorBuiltins;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked1Node;
import com.oracle.graal.python.builtins.objects.thread.ThreadLocalBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.CallSlotGetAttrNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A node merging the logic of {@link ObjectBuiltins.GetAttributeNode},
 * {@link TypeBuiltins.GetattributeNode} and {@link ModuleBuiltins.ModuleGetattributeNode} to reduce
 * code size by about 3x for host inlining
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class MergedObjectTypeModuleGetAttributeNode extends PNodeWithContext {

    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object object, Object keyObj);

    @Specialization
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object object, Object keyObj,
                    @Cached CastToTruffleStringChecked1Node castToString,
                    @Cached GetClassNode getClassNode,
                    @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Cached MergedObjectTypeModuleGetAttributeInnerNode innerNode) {
        TruffleString key = castToString.cast(inliningTarget, keyObj, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
        Object type = getClassNode.execute(inliningTarget, object);
        TpSlots slots = getSlotsNode.execute(inliningTarget, type);
        return innerNode.execute(frame, inliningTarget, object, key, type, slots);
    }
}

@GenerateUncached
@GenerateInline
@GenerateCached(false)
abstract class MergedObjectTypeModuleGetAttributeInnerNode extends PNodeWithContext {

    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object object, TruffleString key, Object type, TpSlots slots);

    /**
     * Keep in sync with {@link ObjectBuiltins.GetAttributeNode} and
     * {@link TypeBuiltins.GetattributeNode} and {@link ThreadLocalBuiltins.GetAttributeNode} and
     * {@link ModuleBuiltins.ModuleGetattributeNode}
     */
    @Specialization(guards = {"slots.tp_getattro() == cachedSlot", "isObjectTypeModuleGetAttribute(cachedSlot)"}, limit = "1")
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object object, TruffleString key, Object type, @SuppressWarnings("unused") TpSlots slots,
                    @Cached("slots.tp_getattro()") TpSlot cachedSlot,
                    // Common
                    @Cached GetObjectSlotsNode getDescrSlotsNode,
                    @Cached LookupAttributeInMRONode.Dynamic lookup,
                    @Cached InlinedConditionProfile hasDescProfile,
                    @Cached InlinedConditionProfile hasDescrGetProfile,
                    @Cached InlinedConditionProfile hasValueProfile,
                    @Cached PRaiseNode raiseNode,
                    @Cached CallSlotDescrGet.Lazy callSlotDescrGet,
                    // Specific to a given tp_getattro, some should probably be lazy
                    @Cached ReadAttributeFromObjectNode readAttributeOfObjectNode,
                    @Cached LookupAttributeInMRONode.Dynamic readAttributeOfClassNode,
                    @Cached GetObjectSlotsNode getValueSlotsNode,
                    @Cached InlinedBranchProfile hasNonDescriptorValueProfile,
                    @Cached CallSlotDescrGet.Lazy callSlotValueGet,
                    @Cached ModuleBuiltins.LazyHandleGetattrExceptionNode handleException) {
        assert hasNoGetAttr(object);
        try {
            Object descr = lookup.execute(type, key);
            boolean hasDescr = hasDescProfile.profile(inliningTarget, descr != PNone.NO_VALUE);

            TpSlot get = null;
            boolean hasDescrGet = false;
            if (hasDescr) {
                var descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
                get = descrSlots.tp_descr_get();
                hasDescrGet = hasDescrGetProfile.profile(inliningTarget, get != null);
                if (hasDescrGet && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    return callSlotDescrGet.get(inliningTarget).executeCached(frame, get, descr, object, type);
                }
            }

            // The main difference between all 3 nodes
            Object value;
            if (cachedSlot != TypeBuiltins.SLOTS.tp_getattro()) {
                // ObjectBuiltins.SLOTS.tp_getattro() || ModuleBuiltins.SLOTS.tp_getattro()
                value = readAttributeOfObjectNode.execute(object, key);
                if (hasValueProfile.profile(inliningTarget, value != PNone.NO_VALUE)) {
                    return value;
                }
            } else {
                // TypeBuiltins.SLOTS.tp_getattro()
                value = readAttributeOfClassNode.execute(object, key);
                if (hasValueProfile.profile(inliningTarget, value != PNone.NO_VALUE)) {
                    var valueGet = getValueSlotsNode.execute(inliningTarget, value).tp_descr_get();
                    if (valueGet == null) {
                        hasNonDescriptorValueProfile.enter(inliningTarget);
                        return value;
                    } else {
                        return callSlotValueGet.get(inliningTarget).executeCached(frame, valueGet, value, PNone.NO_VALUE, object);
                    }
                }
            }

            if (hasDescr) {
                if (!hasDescrGet) {
                    return descr;
                } else {
                    return callSlotDescrGet.get(inliningTarget).executeCached(frame, get, descr, object, type);
                }
            }

            throw raiseNode.raiseAttributeError(inliningTarget, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        } catch (PException e) {
            // Extra behavior for module.__getattribute__
            if (cachedSlot == ModuleBuiltins.SLOTS.tp_getattro()) {
                return handleException.get(inliningTarget).execute(frame, (PythonModule) object, key, e);
            } else {
                throw e;
            }
        }
    }

    @InliningCutoff
    @Specialization(replaces = "doIt")
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object object, TruffleString key, @SuppressWarnings("unused") Object type, TpSlots slots,
                    @Cached CallSlotGetAttrNode callGetAttrNode,
                    @Cached AttributeErrorBuiltins.SetAttributeErrorContext setContext) {
        try {
            return callGetAttrNode.execute(frame, inliningTarget, slots, object, key);
        } catch (PException e) {
            throw setContext.execute(inliningTarget, e, object, key);
        }
    }

    @Idempotent
    static boolean isObjectTypeModuleGetAttribute(TpSlot slot) {
        return slot == ObjectBuiltins.SLOTS.tp_getattro() || slot == TypeBuiltins.SLOTS.tp_getattro() || slot == ModuleBuiltins.SLOTS.tp_getattro();
    }

    static boolean hasNoGetAttr(Object obj) {
        CompilerAsserts.neverPartOfCompilation("only used in asserts");
        return LookupAttributeInMRONode.Dynamic.getUncached().execute(GetClassNode.executeUncached(obj), T___GETATTR__) == PNone.NO_VALUE;
    }
}
