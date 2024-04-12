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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_NO_ATTRS_S_TO_ASSIGN;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_NO_ATTRS_S_TO_DELETE;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_RO_ATTRS_S_TO_ASSIGN;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_RO_ATTRS_S_TO_DELETE;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent PyObject_SetAttr*. Like Python, this method raises when the attribute doesn't exist.
 *
 * @see PyObjectSetAttrO
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectSetAttr extends PNodeWithContext {
    public static void executeUncached(Object receiver, TruffleString name, Object value) {
        PyObjectSetAttr.getUncached().execute(null, null, receiver, name, value);
    }

    public final void executeCached(Frame frame, Object receiver, TruffleString name, Object value) {
        execute(frame, this, receiver, name, value);
    }

    public abstract void execute(Frame frame, Node inliningTarget, Object receiver, TruffleString name, Object value);

    public final void execute(Node inliningTarget, Object receiver, TruffleString name, Object value) {
        execute(null, inliningTarget, receiver, name, value);
    }

    public final void deleteCached(Frame frame, Object receiver, TruffleString name) {
        execute(frame, null, receiver, name, null);
    }

    @Specialization(guards = {"name == cachedName", "value != null"}, limit = "1")
    static void setFixedAttr(Frame frame, Node inliningTarget, Object self, @SuppressWarnings("unused") TruffleString name, Object value,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Shared("getClass") @Cached GetClassNode getClass,
                    @Shared @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Shared("lookup") @Cached(parameters = "SetAttr", inline = false) LookupSpecialMethodSlotNode lookupSetattr,
                    @Shared("raise") @Cached PRaiseNode.Lazy raise,
                    @Shared("call") @Cached(inline = false) CallTernaryMethodNode callSetattr) {
        Object type = getClass.execute(inliningTarget, self);
        Object setattr = lookupSetattr.execute(frame, type, self);
        if (setattr == PNone.NO_VALUE) {
            TpSlots slots = getSlotsNode.execute(inliningTarget, type);
            if (slots.combined_tp_getattro_getattr() == null) {
                throw raise.get(inliningTarget).raise(TypeError, P_HAS_NO_ATTRS_S_TO_ASSIGN, self, name);
            } else {
                throw raise.get(inliningTarget).raise(TypeError, P_HAS_RO_ATTRS_S_TO_ASSIGN, self, name);
            }
        }
        callSetattr.execute(frame, setattr, self, name, value);
    }

    @Specialization(guards = {"name == cachedName", "value == null"}, limit = "1")
    static void delFixedAttr(Frame frame, Node inliningTarget, Object self, @SuppressWarnings("unused") TruffleString name, @SuppressWarnings("unused") Object value,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Shared("getClass") @Cached GetClassNode getClass,
                    @Shared @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Shared("lookupDel") @Cached(parameters = "DelAttr", inline = false) LookupSpecialMethodSlotNode lookupDelattr,
                    @Shared("raise") @Cached PRaiseNode.Lazy raise,
                    @Shared("callDel") @Cached(inline = false) CallBinaryMethodNode callDelattr) {
        Object type = getClass.execute(inliningTarget, self);
        Object delattr = lookupDelattr.execute(frame, type, self);
        if (delattr == PNone.NO_VALUE) {
            TpSlots slots = getSlotsNode.execute(inliningTarget, type);
            if (slots.combined_tp_getattro_getattr() == null) {
                throw raise.get(inliningTarget).raise(TypeError, P_HAS_NO_ATTRS_S_TO_DELETE, self, name);
            } else {
                throw raise.get(inliningTarget).raise(TypeError, P_HAS_RO_ATTRS_S_TO_DELETE, self, name);
            }
        }
        callDelattr.executeObject(frame, delattr, self, name);
    }

    @Specialization(replaces = {"setFixedAttr", "delFixedAttr"})
    static void doDynamicAttr(Frame frame, Node inliningTarget, Object self, TruffleString name, Object value,
                    @Shared("getClass") @Cached GetClassNode getClass,
                    @Shared @Cached GetCachedTpSlotsNode getSlotsNode,
                    @Shared("lookup") @Cached(parameters = "SetAttr", inline = false) LookupSpecialMethodSlotNode lookupSetattr,
                    @Shared("lookupDel") @Cached(parameters = "DelAttr", inline = false) LookupSpecialMethodSlotNode lookupDelattr,
                    @Shared("raise") @Cached PRaiseNode.Lazy raise,
                    @Shared("call") @Cached(inline = false) CallTernaryMethodNode callSetattr,
                    @Shared("callDel") @Cached(inline = false) CallBinaryMethodNode callDelattr) {
        if (value == null) {
            delFixedAttr(frame, inliningTarget, self, name, value, name, getClass, getSlotsNode, lookupDelattr, raise, callDelattr);
        } else {
            setFixedAttr(frame, inliningTarget, self, name, value, name, getClass, getSlotsNode, lookupSetattr, raise, callSetattr);
        }
    }

    @NeverDefault
    public static PyObjectSetAttr create() {
        return PyObjectSetAttrNodeGen.create();
    }

    public static PyObjectSetAttr getUncached() {
        return PyObjectSetAttrNodeGen.getUncached();
    }
}
