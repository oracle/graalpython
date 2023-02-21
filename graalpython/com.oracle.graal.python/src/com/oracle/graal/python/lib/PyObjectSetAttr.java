/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_NO_ATTRS_S_TO_ASSIGN;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_NO_ATTRS_S_TO_DELETE;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_RO_ATTRS_S_TO_ASSIGN;
import static com.oracle.graal.python.nodes.ErrorMessages.P_HAS_RO_ATTRS_S_TO_DELETE;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent PyObject_SetAttr*. Like Python, this method raises when the attribute doesn't exist.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectSetAttr extends PNodeWithContext {
    public abstract void execute(Frame frame, Object receiver, Object name, Object value);

    public final void execute(Object receiver, Object name, Object value) {
        execute(null, receiver, name, value);
    }

    public final void delete(Frame frame, Object receiver, Object name) {
        execute(frame, receiver, name, null);
    }

    @Specialization(guards = {"name == cachedName", "value != null"}, limit = "1")
    static void setFixedAttr(Frame frame, Object self, @SuppressWarnings("unused") TruffleString name, Object value,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Shared("getClass") @Cached GetClassNode getClass,
                    @Shared("lookup") @Cached(parameters = "SetAttr") LookupSpecialMethodSlotNode lookupSetattr,
                    @Shared("lookupGet") @Cached(parameters = "GetAttribute") LookupSpecialMethodSlotNode lookupGetattr,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Shared("call") @Cached CallTernaryMethodNode callSetattr) {
        Object type = getClass.execute(self);
        Object setattr = lookupSetattr.execute(frame, type, self);
        if (setattr == PNone.NO_VALUE) {
            if (lookupGetattr.execute(frame, type, self) == PNone.NO_VALUE) {
                throw raise.raise(TypeError, P_HAS_NO_ATTRS_S_TO_ASSIGN, self, name);
            } else {
                throw raise.raise(TypeError, P_HAS_RO_ATTRS_S_TO_ASSIGN, self, name);
            }
        }
        callSetattr.execute(frame, setattr, self, name, value);
    }

    @Specialization(guards = {"name == cachedName", "value == null"}, limit = "1")
    static void delFixedAttr(Frame frame, Object self, @SuppressWarnings("unused") TruffleString name, @SuppressWarnings("unused") Object value,
                    @SuppressWarnings("unused") @Cached("name") TruffleString cachedName,
                    @Shared("getClass") @Cached GetClassNode getClass,
                    @Shared("lookupDel") @Cached(parameters = "DelAttr") LookupSpecialMethodSlotNode lookupDelattr,
                    @Shared("lookupGet") @Cached(parameters = "GetAttribute") LookupSpecialMethodSlotNode lookupGetattr,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Shared("callDel") @Cached CallBinaryMethodNode callDelattr) {
        Object type = getClass.execute(self);
        Object delattr = lookupDelattr.execute(frame, type, self);
        if (delattr == PNone.NO_VALUE) {
            if (lookupGetattr.execute(frame, type, self) == PNone.NO_VALUE) {
                throw raise.raise(TypeError, P_HAS_NO_ATTRS_S_TO_DELETE, self, name);
            } else {
                throw raise.raise(TypeError, P_HAS_RO_ATTRS_S_TO_DELETE, self, name);
            }
        }
        callDelattr.executeObject(frame, delattr, self, name);
    }

    @Specialization(replaces = {"setFixedAttr", "delFixedAttr"})
    static void doDynamicAttr(Frame frame, Object self, TruffleString name, Object value,
                    @Shared("getClass") @Cached GetClassNode getClass,
                    @Shared("lookup") @Cached(parameters = "SetAttr") LookupSpecialMethodSlotNode lookupSetattr,
                    @Shared("lookupDel") @Cached(parameters = "DelAttr") LookupSpecialMethodSlotNode lookupDelattr,
                    @Shared("lookupGet") @Cached(parameters = "GetAttribute") LookupSpecialMethodSlotNode lookupGetattr,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Shared("call") @Cached CallTernaryMethodNode callSetattr,
                    @Shared("callDel") @Cached CallBinaryMethodNode callDelattr) {
        if (value == null) {
            delFixedAttr(frame, self, name, value, name, getClass, lookupDelattr, lookupGetattr, raise, callDelattr);
        } else {
            setFixedAttr(frame, self, name, value, name, getClass, lookupSetattr, lookupGetattr, raise, callSetattr);
        }
    }

    @Specialization(guards = "!isString(name)")
    @SuppressWarnings("unused")
    static void nameMustBeString(Object self, Object name, Object value,
                    @Shared("raise") @Cached PRaiseNode raise) {
        throw raise.raise(TypeError, ATTR_NAME_MUST_BE_STRING, name);
    }

    @NeverDefault
    public static PyObjectSetAttr create() {
        return PyObjectSetAttrNodeGen.create();
    }

    public static PyObjectSetAttr getUncached() {
        return PyObjectSetAttrNodeGen.getUncached();
    }
}
